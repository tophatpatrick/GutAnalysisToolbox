package UI.panes.Results;

import Features.AnalyseWorkflows.NeuronsHuPipeline.HuResult;
import Features.Core.PluginCalls;
import Features.Tools.OutputIO;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Plot;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class ResultsUI {

    public static void promptAndMaybeShow(HuResult r) {
        String msg = "Results saved to:\n" + r.outDir.getAbsolutePath();
        String[] options = {"Preview results…", "Open folder", "End"};
        int choice = JOptionPane.showOptionDialog(
                null, msg, "Workflow finished",
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                null, options, options[0]
        );
        if (choice == 1) {
            openFolder(r.outDir);
        } else if (choice == 0) {
            showResultsFrame(r);
        }
    }

    private static void openFolder(File dir) {
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(dir);
            else IJ.showMessage("Folder: " + dir.getAbsolutePath());
        } catch (IOException e) {
            IJ.handleException(e);
        }
    }

    private static void showResultsFrame(HuResult r) {
        JFrame f = new JFrame("Results – " + r.baseName);
        f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        f.setLayout(new BorderLayout());

        // ---- header ----
        JPanel header = new JPanel(new GridLayout(0,1));
        header.setBorder(new EmptyBorder(6,12,2,12));
        header.add(new JLabel("Output folder: " + r.outDir.getAbsolutePath()));
        header.add(new JLabel("Total neurons: " + r.totalNeuronCount));
        if (r.nGanglia != null) header.add(new JLabel("Detected ganglia: " + r.nGanglia));
        f.add(header, BorderLayout.NORTH);

        // ---- center ----
        JPanel center = new JPanel();
        center.setBorder(new EmptyBorder(4,12,4,12));
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        // Ensure overlay images exist
        File neuronOverlay = new File(r.outDir, "RGB_" + r.baseName + "_neurons_overlay.tif");
        File gangliaOverlay = new File(r.outDir, "RGB_" + r.baseName + "_ganglia_overlay.tif");

        // IMAGES: only the two overlays
        center.add(sectionTitle("Images (overlays)"));
        JPanel thumbsRow = new JPanel(new GridLayout(0, 1, 0, 6));
        thumbsRow.add(makeThumbCard("Neurons overlay",
                loadForThumb(neuronOverlay, r.max), gangliaOverlay.exists() ? 400 : 600));
        if (gangliaOverlay.exists()) {
            thumbsRow.add(makeThumbCard("Ganglia overlay",
                    loadForThumb(gangliaOverlay, r.max), 400));
        }
        center.add(thumbsRow);

        // Ganglia table + centered chart (if available)
        if (r.neuronsPerGanglion != null && r.neuronsPerGanglion.length > 1) {
            center.add(Box.createVerticalStrut(10));
            center.add(sectionTitle("Ganglia results (table)"));

            JTable table = makeGangliaTable(r);
            JScrollPane tableScroll = new JScrollPane(table);
            tableScroll.setBorder(BorderFactory.createEmptyBorder());
            tableScroll.setPreferredSize(new Dimension(720, 220));
            center.add(tableScroll);

            center.add(Box.createVerticalStrut(10));
            center.add(sectionTitle("Neurons per ganglion (bar)"));
            JPanel chartWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            JLabel chart = new JLabel(new ImageIcon(makeGangliaBarPlot(r)));
            chart.setBorder(new EmptyBorder(6,0,8,0));
            chartWrap.add(chart);
            center.add(chartWrap);
        }

        JScrollPane scroller = new JScrollPane(center);
        scroller.setBorder(null);
        f.add(scroller, BorderLayout.CENTER);

        // footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.add(new JButton("Open folder"){{
            addActionListener(e -> openFolder(r.outDir));
        }});
        footer.add(new JButton("Close"){{
            addActionListener(e -> f.dispose());
        }});
        f.add(footer, BorderLayout.SOUTH);

        f.pack(); // size to preferred sizes
// (optional) cap to a reasonable maximum so it never gets too big)
        Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
        f.setSize(Math.min(f.getWidth(), 900), Math.min(f.getHeight(), 700));
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }

    // ---------- helpers ----------

    private static JPanel sectionTitle(String text) {
        JPanel p = new JPanel(new BorderLayout());
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        p.add(l, BorderLayout.CENTER);
        p.setBorder(new EmptyBorder(2,0,2,0));
        return p;
    }

    private static JPanel makeThumbCard(String title, ImagePlus impForThumb, int widthPx) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220,220,220)),
                new EmptyBorder(4,4,4,4)
        ));

        JLabel t = new JLabel(title);
        t.setFont(t.getFont().deriveFont(Font.BOLD));
        t.setBorder(new EmptyBorder(0,0,2,0));
        t.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(t, BorderLayout.NORTH);

        JLabel thumb = new JLabel(new ImageIcon(makeThumbnail(impForThumb, widthPx)));
        thumb.setBorder(new EmptyBorder(0,0,0,0));
        thumb.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(thumb, BorderLayout.CENTER);

        return card;
    }

    private static BufferedImage makeThumbnail(ImagePlus imp, int w) {
        if (imp == null || imp.getProcessor() == null) {
            BufferedImage bi = new BufferedImage(200, 120, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = bi.createGraphics();
            g.setColor(new Color(245,245,245)); g.fillRect(0,0,bi.getWidth(), bi.getHeight());
            g.setColor(Color.DARK_GRAY); g.drawString("No image", 70, 60);
            g.dispose();
            return bi;
        }
        double scale = w / (double) imp.getWidth();
        int h = Math.max(1, (int) Math.round(imp.getHeight() * scale));
        ImageProcessor ip = imp.getProcessor().resize(w, h, true);
        return ip.getBufferedImage();
    }

    private static ImagePlus loadForThumb(File f, ImagePlus fallback) {
        ImagePlus imp = (f != null && f.exists()) ? IJ.openImage(f.getAbsolutePath()) : null;
        return (imp != null) ? imp : fallback;
    }

    /**
     * Ensures an overlay TIFF exists; if not, derives ROIs from the label image,
     * draws them on a hidden duplicate of base, flattens, and saves.
     */
    private static File ensureOverlayFile(File outFile, ImagePlus base, ImagePlus labelMap) {
        if (outFile.exists()) return outFile;

        try {
            RoiManager rm = new RoiManager(false); // hidden
            rm.reset();
            // Push label ROIs into RM (PluginCalls utility from your codebase)
            PluginCalls.labelsToRois(labelMap);

            // Save flattened overlay
            OutputIO.saveFlattenedOverlay(base, rm, outFile);

            rm.reset();
            rm.close();
        } catch (Throwable t) {
            IJ.log("Could not generate overlay: " + outFile.getName() + " – " + t.getMessage());
        }
        return outFile;
    }

    private static JTable makeGangliaTable(HuResult r) {
        DefaultTableModel model = new DefaultTableModel(new Object[]{"ganglion_id","neuron_count","area_um2"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        int n = Math.max(
                r.neuronsPerGanglion != null ? r.neuronsPerGanglion.length : 0,
                r.gangliaAreaUm2    != null ? r.gangliaAreaUm2.length    : 0
        );
        for (int gid = 1; gid < n; gid++) {
            int count = (r.neuronsPerGanglion != null && gid < r.neuronsPerGanglion.length)
                    ? r.neuronsPerGanglion[gid] : 0;
            double area = (r.gangliaAreaUm2 != null && gid < r.gangliaAreaUm2.length)
                    ? r.gangliaAreaUm2[gid] : 0.0;
            if (count == 0 && area == 0.0) continue;
            model.addRow(new Object[]{gid, count, String.format(java.util.Locale.US, "%.2f", area)});
        }
        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setRowHeight(22);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setFont(table.getFont().deriveFont(12f));
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD, 12f));

        // trim column widths so everything fits nicely
        TableColumnModel cm = table.getColumnModel();
        if (cm.getColumnCount() >= 3) {
            cm.getColumn(0).setPreferredWidth(90);   // ganglion_id
            cm.getColumn(1).setPreferredWidth(120);  // neuron_count
            cm.getColumn(2).setPreferredWidth(140);  // area_um2
        }
        return table;
    }

    private static BufferedImage makeGangliaBarPlot(HuResult r) {
        int n = r.neuronsPerGanglion.length;
        if (n <= 1) {
            BufferedImage bi = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = bi.createGraphics();
            g.setColor(Color.WHITE); g.fillRect(0,0,bi.getWidth(),bi.getHeight());
            g.setColor(Color.DARK_GRAY); g.drawString("No ganglia data", 150, 100);
            g.dispose();
            return bi;
        }
        double[] x = new double[n - 1];
        double[] y = new double[n - 1];
        int max = 0;
        for (int gid = 1; gid < n; gid++) {
            int v = r.neuronsPerGanglion[gid];
            x[gid - 1] = gid;
            y[gid - 1] = v;
            if (v > max) max = v;
        }
        Plot plot = new Plot("Neurons per ganglion", "Ganglion #", "Count");
        plot.setLimits(0.5, (n - 1) + 0.5, 0, Math.max(1, (int) Math.ceil(max * 1.1)));
        plot.add("bar", x, y);
        return plot.getProcessor().getBufferedImage();
    }
}
