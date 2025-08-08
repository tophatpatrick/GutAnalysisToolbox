package UI.panes.WorkflowDashboards;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;

/**
 * Holds a JTabbedPane.  Each addRun() loads one image+ROI set
 * into a new tab with a JSplitPane (image on left, ROI manager on right).
 */
public class AnalyseNeuronDashboard extends JPanel {
    private final JTabbedPane tabs = new JTabbedPane();
    private int runCount = 0;

    public AnalyseNeuronDashboard() {
        super(new BorderLayout());
        add(tabs, BorderLayout.CENTER);
    }

    /**
     * Load the given TIFF + ROI zip, hide IJ’s own windows,
     * harvest the RoiManager UI panels, build a split pane,
     * and add it as “Run N” to the tabbed pane.
     */
    public void addRun(String imagePath, String roiZipPath) {
        runCount++;
        // 1) open the image
        ImagePlus imp = IJ.openImage(imagePath);
        if (imp == null) {
            JOptionPane.showMessageDialog(this,
                    "Could not open image:\n" + imagePath,
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        imp.show();
        Frame ijWin = imp.getWindow();
        if (ijWin != null) ijWin.setVisible(false);

        // 2) embed the ImageCanvas in a scroll pane (with Ctrl+wheel zoom)
        ImageCanvas canvas = imp.getCanvas();
        JScrollPane imgScroll = new JScrollPane(canvas);
        imgScroll.setBorder(BorderFactory.createTitledBorder("Image"));
        canvas.addMouseWheelListener(e -> {
            if (e.isControlDown()) {
                double f = e.getWheelRotation() < 0 ? 1.1 : 0.9;
                canvas.setMagnification(canvas.getMagnification() * f);
                imp.updateAndDraw();
                canvas.repaint();
                imgScroll.revalidate();
                e.consume();
            }
        });

        // 3) prepare / reset the global RoiManager
        RoiManager rm = RoiManager.getInstance();
        if (rm == null) rm = new RoiManager();
        rm.reset();
        rm.runCommand("Open", roiZipPath);
        rm.setVisible(false);

        // 4) harvest its toolbar & checkbox panels + the AWT List
        Panel toolbar = null, checkboxPanel = null;
        for (Component c : rm.getComponents()) {
            if (c instanceof Panel) {
                Panel p = (Panel)c;
                boolean hasBtn = false, hasChk = false;
                for (Component cc : p.getComponents()) {
                    if (cc instanceof Button)            hasBtn = true;
                    if (cc instanceof java.awt.Checkbox) hasChk = true;
                }
                if (hasBtn   && toolbar       == null) toolbar       = p;
                if (hasChk   && checkboxPanel == null) checkboxPanel = p;
            }
        }
        java.awt.List awtList = rm.getList();

        // 5) dark-theme styling
        stylePanel(toolbar);
        stylePanel(checkboxPanel);
        awtList.setBackground(new Color(48,48,48));
        awtList.setForeground(Color.WHITE);

        // 6) draw initial “nothing selected” overlay
        rebuildOverlay(imp, canvas, rm, -1);

        // 7) two-way sync: list → overlay
        RoiManager finalRm = rm;
        awtList.addItemListener(ev -> {
            if (ev.getStateChange()==ItemEvent.SELECTED) {
                int i = awtList.getSelectedIndex();
                finalRm.select(i);
                rebuildOverlay(imp, canvas, finalRm, i);
            }
        });

        // 8) two-way sync: click on image → list
        awtList.addItemListener(ev -> {
            if (ev.getStateChange()==ItemEvent.SELECTED) {
                // handled above
            }
        });
        RoiManager finalRm1 = rm;
        canvas.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                double m = canvas.getMagnification();
                int x = (int)(canvas.screenX(e.getX())/m),
                        y = (int)(canvas.screenY(e.getY())/m);
                for (int i = 0; i< finalRm1.getCount(); i++) {
                    Roi r = finalRm1.getRoi(i);
                    if (r.contains(x,y)) {
                        awtList.select(i);
                        finalRm1.select(i);
                        rebuildOverlay(imp, canvas, finalRm1, i);
                        break;
                    }
                }
            }
        });

        // 9) sidebar + split
        JPanel sidebar = new JPanel(new BorderLayout(5,5));
        sidebar.setBorder(BorderFactory.createTitledBorder("ROI Manager"));
        if (checkboxPanel != null) sidebar.add(checkboxPanel, BorderLayout.NORTH);
        sidebar.add(awtList,                    BorderLayout.CENTER);
        if (toolbar       != null) sidebar.add(toolbar,       BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, imgScroll, sidebar
        );
        split.setResizeWeight(0.7);
        split.setDividerLocation(0.7);

        // 10) tab it
        tabs.addTab("Run " + runCount, split);
        tabs.setSelectedIndex(tabs.getTabCount()-1);
        revalidate();
        repaint();
    }

    private void rebuildOverlay(ImagePlus imp, ImageCanvas canvas,
                                RoiManager rm, int selIndex) {
        Overlay ov = new Overlay();
        for (int i=0; i<rm.getCount(); i++) {
            if (i!=selIndex) {
                Roi r = rm.getRoi(i);
                r.setStrokeColor(Color.YELLOW);
                ov.add(r);
            }
        }
        imp.setOverlay(ov);
        if (selIndex>=0) {
            Roi s = rm.getRoi(selIndex);
            s.setStrokeColor(Color.BLUE);
            imp.setRoi(s);
        } else {
            imp.killRoi();
        }
        canvas.repaint();
    }

    private void stylePanel(Panel p) {
        if (p==null) return;
        p.setBackground(new Color(48,48,48));
        for (Component c : p.getComponents()) {
            c.setBackground(Color.DARK_GRAY);
            c.setForeground(Color.WHITE);
        }
    }
}
