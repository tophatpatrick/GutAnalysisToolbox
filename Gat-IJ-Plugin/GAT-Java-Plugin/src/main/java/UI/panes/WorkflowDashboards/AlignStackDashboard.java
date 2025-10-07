package UI.panes.WorkflowDashboards;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.StackWindow;
import ij.measure.ResultsTable;

import javax.swing.*;
import java.awt.*;
import Features.Core.Params;

public class AlignStackDashboard extends JPanel {
    private final JTabbedPane tabs = new JTabbedPane();
    private int runCount = 0;

    public AlignStackDashboard() {
        super(new BorderLayout());
        add(tabs, BorderLayout.CENTER);
    }

    public void addRun(Params p) {
        runCount++;

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            private ImagePlus alignedImp;
            private String resultsText = "";

            @Override
            protected Void doInBackground() {
                // 1) Open image stack
                alignedImp = IJ.openImage(p.imagePath);
                if (alignedImp == null) {
                    JOptionPane.showMessageDialog(AlignStackDashboard.this,
                            "Could not open image:\n" + p.imagePath,
                            "Error", JOptionPane.ERROR_MESSAGE);
                    return null;
                }

                // 2) Validate reference frame
                if (p.referenceFrame < 1 || p.referenceFrame > alignedImp.getStackSize()) {
                    JOptionPane.showMessageDialog(AlignStackDashboard.this,
                            "Reference frame out of bounds. Image stack has " + alignedImp.getStackSize() + " slices.",
                            "Error", JOptionPane.ERROR_MESSAGE);
                    return null;
                }

                // 3) Create ImageWindow so plugin can render
                ImageWindow win = new ImageWindow(alignedImp);
                win.setVisible(false); // hide native ImageJ window

                // 4) Run alignment plugin silently
                String options = "method=5 windowsizex=32 windowsizey=32 x0=0 y0=0 " +
                        "swindow=0 subpixel=" + p.subpixel +
                        " itpmethod=0 ref.slice=" + p.referenceFrame +
                        (p.useSIFT ? " sift=true" : "") +
                        " show=true";
                IJ.run(alignedImp, "Align slices in stack...", options);

                // 5) Capture results
                ResultsTable rt = ResultsTable.getResultsTable();
                StringBuilder sb = new StringBuilder();

                for (int i = 0; i < rt.getCounter(); i++) {
                    sb.append("Slice: ").append((int)rt.getValue("Slice", i))
                    .append(" X displacement: ").append(rt.getValue("X Displacement", i))
                    .append(" Y displacement: ").append(rt.getValue("Y Displacement", i))
                    .append("\n");
                }

                resultsText = sb.toString();
                return null;
            }

            @Override
            protected void done() {
                // --- Image panel ---
                ImageCanvas canvas = new ImageCanvas(alignedImp);
                JScrollPane scroll = new JScrollPane(canvas);
                scroll.setPreferredSize(new Dimension(900, 600));
                scroll.setBorder(BorderFactory.createTitledBorder("Aligned Stack Run " + runCount));

                // --- Slice slider ---
                JSlider sliceSlider = new JSlider(1, alignedImp.getStackSize(), 1);
                sliceSlider.addChangeListener(e -> {
                    alignedImp.setSlice(sliceSlider.getValue());
                    canvas.repaint();
                });

                JPanel panel = new JPanel(new BorderLayout());
                panel.add(scroll, BorderLayout.CENTER);
                panel.add(sliceSlider, BorderLayout.SOUTH);

                tabs.addTab("Aligned Run " + runCount, panel);
                tabs.setSelectedIndex(tabs.getTabCount() - 1);

                revalidate();
                repaint();
            }
        };

        worker.execute();
    }
}
