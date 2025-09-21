package UI.panes.Results;

import Features.AnalyseWorkflows.NeuronsHuPipeline.HuResult;
import ij.IJ;
import ij.ImagePlus;
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
            center.add(sectionTitle("Neurons per ganglion (box plot)"));
            BufferedImage boxImg = makeGangliaBoxPlot(r);
            JLabel chart = new JLabel(new ImageIcon(boxImg));
            JPanel chartWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            chart.setBorder(new EmptyBorder(6,0,8,0));
            chartWrap.add(chart);

// Save button (saves chart(s) into the output folder)
            JButton saveBtn = new JButton("Save plots…");
            saveBtn.addActionListener(e -> {
                try {
                    File outPng = new File(r.outDir, "Plot_neurons_per_ganglion_box.png");
                    saveImage(boxImg, outPng);
                    JOptionPane.showMessageDialog(chartWrap,
                            "Saved:\n" + outPng.getAbsolutePath(),
                            "Saved", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    IJ.handleException(ex);
                }
            });

            JPanel chartWithBtn = new JPanel();
            chartWithBtn.setLayout(new BoxLayout(chartWithBtn, BoxLayout.Y_AXIS));
            chartWithBtn.add(chartWrap);
            JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            btnRow.add(saveBtn);
            btnRow.setBorder(new EmptyBorder(0,0,8,0));
            chartWithBtn.add(btnRow);

            center.add(chartWithBtn);

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
        p.setBorder(new EmptyBorder(2, 0, 6, 0)); // was (2,0,2,0)
        return p;
    }

    private static BufferedImage makeGangliaBoxPlot(HuResult r) {
        if (r.neuronsPerGanglion == null || r.neuronsPerGanglion.length <= 1) {
            return placeholderImage(420, 240, "No ganglia data");
        }
        java.util.List<Integer> vals = new java.util.ArrayList<>();
        for (int i = 1; i < r.neuronsPerGanglion.length; i++) {
            int v = r.neuronsPerGanglion[i];
            if (v > 0) vals.add(v);
        }
        if (vals.isEmpty()) return placeholderImage(420, 240, "No ganglia data");

        double[] q = fiveNumberSummary(vals); // {min, q1, median, q3, max}

        // extra top padding so nothing bumps the header
        int W = 560, H = 300, padL = 60, padR = 20, padT = 36, padB = 40;
        BufferedImage bi = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bi.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // background
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);

        // NO internal chart title (we use the Swing section header)

        // Frame
        int x0 = padL, x1 = W - padR, y0 = H - padB, y1 = padT;
        g.setColor(new Color(220,220,220));
        g.drawRect(x0, y1, (x1 - x0), (y0 - y1));

        // y ticks/grid
        int ymax = (int)Math.ceil(q[4] * 1.10);
        ymax = Math.max(ymax, 1);
        g.setFont(g.getFont().deriveFont(12f));
        for (int i = 0; i <= 5; i++) {
            double yv = i * (ymax / 5.0);
            int y = y0 - (int)Math.round((yv / ymax) * (y0 - y1));
            g.setColor(new Color(235,235,235)); g.drawLine(x0, y, x1, y);
            g.setColor(new Color(90,90,90));
            String lab = String.valueOf((int)Math.round(yv));
            int tw = g.getFontMetrics().stringWidth(lab);
            g.drawString(lab, x0 - tw - 6, y + 4);
        }

        // x-axis label (bottom)
        String xlab = "Neuron count";
        int xw = g.getFontMetrics().stringWidth(xlab);
        g.setColor(new Color(80,80,80));
        g.drawString(xlab, x0 + ((x1 - x0) - xw) / 2, H - 10);

        // value→y mapper
        int finalYmax = ymax;
        java.util.function.DoubleFunction<Integer> Y = v -> y0 - (int)Math.round((v / finalYmax) * (y0 - y1));

        // box at center
        int cx = (x0 + x1) / 2;
        int boxW = 80;

        // whiskers
        g.setColor(new Color(70,70,70));
        g.drawLine(cx, Y.apply(q[0]), cx, Y.apply(q[1]));
        g.drawLine(cx, Y.apply(q[3]), cx, Y.apply(q[4]));
        g.drawLine(cx - 15, Y.apply(q[0]), cx + 15, Y.apply(q[0]));
        g.drawLine(cx - 15, Y.apply(q[4]), cx + 15, Y.apply(q[4]));

        // box (Q1–Q3)
        int top = Y.apply(q[3]), bot = Y.apply(q[1]);
        g.setColor(new Color(180,205,255));
        g.fillRect(cx - boxW/2, top, boxW, bot - top);
        g.setColor(new Color(60,90,160));
        g.drawRect(cx - boxW/2, top, boxW, bot - top);

        // median
        int my = Y.apply(q[2]);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(cx - boxW/2, my, cx + boxW/2, my);

        // stats INSIDE the plot area (top-left), not above it
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(60,60,60));
        String stats = String.format(java.util.Locale.US,
                "min=%.0f  Q1=%.0f  median=%.0f  Q3=%.0f  max=%.0f   (n=%d)",
                q[0], q[1], q[2], q[3], q[4], vals.size());
        int ascent = g.getFontMetrics().getAscent();
        g.drawString(stats, x0 + 6, y1 + ascent + 2);

        g.dispose();
        return bi;
    }


    private static double[] fiveNumberSummary(java.util.List<Integer> vals) {
        java.util.Collections.sort(vals);
        int n = vals.size();
        java.util.function.IntFunction<Double> at = i -> vals.get(Math.max(0, Math.min(n-1, i))).doubleValue();
        // median helpers
        java.util.function.BiFunction<Integer,Integer,Double> median = (lo, hi) -> {
            int len = hi - lo + 1;
            if (len <= 0) return Double.NaN;
            int mid = lo + len/2;
            if ((len & 1) == 1) return at.apply(mid);
            return (at.apply(mid-1) + at.apply(mid)) / 2.0;
        };
        double med = median.apply(0, n-1);
        // lower half: up to (but excluding) median when n odd
        int hiL = (n%2==0) ? (n/2 - 1) : (n/2 - 1);
        double q1 = median.apply(0, Math.max(0, hiL));
        // upper half: from (n/2 + (n%2)) .. n-1
        int loU = (n%2==0) ? (n/2) : (n/2 + 1);
        double q3 = median.apply(loU, n-1);
        return new double[]{ vals.get(0), q1, med, q3, vals.get(n-1) };
    }

    private static BufferedImage placeholderImage(int w, int h, String msg) {
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bi.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0,0,w,h);
        g.setColor(Color.DARK_GRAY); g.drawString(msg, Math.max(10, w/2-60), h/2);
        g.dispose();
        return bi;
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




    private static void saveImage(BufferedImage img, File out) throws IOException {
        out.getParentFile().mkdirs();
        javax.imageio.ImageIO.write(img, "PNG", out);
    }

}
