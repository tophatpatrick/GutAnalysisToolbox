package UI.panes.WorkflowDashboards;

import UI.Handlers.Navigator;
import UI.panes.SettingPanes.NeuronWorkflowPane;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class AnalyseNeuronDashboard extends JPanel {
    public static final String Name = "Analyse Dashboard";

    private final RoiManager rm;
    private final ImagePlus   imp;
    private final ImageCanvas canvas;

    /**
     * @param navigator  callback to switch back to the workflow pane
     *                   **/
    public AnalyseNeuronDashboard(Navigator navigator) {
        super(new BorderLayout(5,5));
        RoiManager rm1;
        setBorder(BorderFactory.createEmptyBorder(5,5,5,5));

        // ── 1) Load & show the image ────────────────────────────
        this.imp = IJ.openImage("placeImageHere");
        imp.show();                         // registers it with WindowManager
        // now grab its real window and hide it
        Frame imgWin = imp.getWindow();
        imgWin.setVisible(false);

        // embed the real canvas
        this.canvas = imp.getCanvas();
        JScrollPane imgScroll = new JScrollPane(canvas);
        imgScroll.setBorder(BorderFactory.createTitledBorder("Image"));
        imgScroll.setPreferredSize(new Dimension(
                imp.getWidth(),
                imp.getHeight()
        ));
        canvas.setBackground(new Color(41, 58, 63, 255));


        // ── 2) Prepare hidden RoiManager & import ROIs ─────────
        rm1 = RoiManager.getInstance();
        if (rm1 == null) {
            rm1 = new RoiManager();
            rm1.setVisible(false);
        }
        rm = rm1;
        rm.reset();
        rm.runCommand("Open", "PlaceRoiziphere");
        rm.setVisible(false);


        // ── 3) Extract & style the AWT ROI-Manager UI ─────────────────────────────
        // 3a) get the list of ROI names
        java.awt.List awtList = rm.getList();

        // 3b) toolbar is always component(0)
        java.awt.Panel toolbarPanel = null;
        if (rm.getComponentCount() > 0 && rm.getComponent(0) instanceof java.awt.Panel) {
            toolbarPanel = (java.awt.Panel)rm.getComponent(0);
        }
        // 3c) the remaining Panel is the checkbox panel
        java.awt.Panel checkboxPanel = null;
        for (Component c : rm.getComponents()) {
            if (c instanceof java.awt.Panel && c != toolbarPanel) {
                checkboxPanel = (java.awt.Panel)c;
                break;
            }
        }


        // apply dark theme
        Color bg = new Color(48,48,48), fg = Color.WHITE;
        styleAwtPanel(toolbarPanel,       bg, fg);
        awtList .setBackground(bg);
        awtList .setForeground(fg);
        styleAwtPanel(checkboxPanel, bg, fg);


        rebuildOverlay(-1);


        //
        // 5) Two-way sync
        //
        // A) clicking in the ROI list → rebuildOverlay(selected)
        awtList.addItemListener(ev -> {
            if (ev.getStateChange() == ItemEvent.SELECTED) {
                int idx = awtList.getSelectedIndex();
                rm.select(idx);
                rebuildOverlay(idx);
            }
        });

        // B) clicking on the image → find which ROI contains the click
        ImageCanvas finalCanvas = canvas;
        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                double mag = finalCanvas.getMagnification();
                int x = (int)(finalCanvas.screenX(e.getX()) / mag);
                int y = (int)(finalCanvas.screenY(e.getY()) / mag);
                for (int i = 0; i < rm.getCount(); i++) {
                    Roi r = rm.getRoi(i);
                    if (r.contains(x, y)) {
                        awtList.select(i);
                        awtList.makeVisible(i);
                        rm.select(i);
                        rebuildOverlay(i);
                        break;
                    }
                }
            }
        });



        // wrap them in a Swing panel exactly like the floating window
        JPanel roiWrapper = new JPanel(new BorderLayout());
        roiWrapper.setBorder(BorderFactory.createTitledBorder("ROI Manager"));
        if (toolbarPanel       != null) roiWrapper.add(toolbarPanel,       BorderLayout.NORTH);
        roiWrapper.add(awtList,       BorderLayout.CENTER);
        if (checkboxPanel != null) roiWrapper.add(checkboxPanel, BorderLayout.SOUTH);

        // ── 4) A simple log area ────────────────────────────────
        JTextArea log = new JTextArea();
        log.setEditable(false);
        log.append("Loaded image: " + "\n");
        log.append("Loaded ROIs:  " + "\n");
        JScrollPane logScroll = new JScrollPane(log);
        logScroll.setBorder(BorderFactory.createTitledBorder("Log"));
        logScroll.setPreferredSize(new Dimension(0,150));

        // ── 5) Split panes: Image | (ROI Manager over Log) ─────
        JSplitPane rightSplit = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                roiWrapper,
                logScroll
        );
        rightSplit.setResizeWeight(0.6);

        JSplitPane mainSplit = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                imgScroll,
                rightSplit
        );
        mainSplit.setResizeWeight(0.6);
        mainSplit.setDividerLocation(0.6);

        add(mainSplit, BorderLayout.CENTER);

        // ── 6) Back button ─────────────────────────────────────
        JButton back = new JButton("← Back");
        back.addActionListener(e -> navigator.show(NeuronWorkflowPane.Name));
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.add(back);
        add(bottom, BorderLayout.SOUTH);
    }

    /** Utility to style an AWT Panel and its children for dark theme */
    private void styleAwtPanel(java.awt.Panel p, Color bg, Color fg) {
        if (p == null) return;
        p.setBackground(bg);
        for (Component c : p.getComponents()) {
            c.setBackground(bg);
            c.setForeground(fg);
        }
    }

    private void rebuildOverlay(int idxSelected) {
        // 1) build overlay of all *other* ROIs
        Overlay ov = new Overlay();
        for (int i = 0; i < rm.getCount(); i++) {
            if (i != idxSelected) {
                Roi r = rm.getRoi(i);
                r.setStrokeColor(Color.YELLOW);  // mute the others
                ov.add(r);
            }
        }
        imp.setOverlay(ov);

        // 2) set the selected ROI, so it’s drawn on top
        if (idxSelected >= 0) {
            Roi sel = rm.getRoi(idxSelected);
            sel.setStrokeColor(Color.BLUE);      // ← paint it blue
            imp.setRoi(sel);
        } else {
            imp.killRoi();
        }

        // 3) refresh the canvas
        imp.updateAndDraw();
        canvas.repaint();
    }
}
