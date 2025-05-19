package UI.panes.WorkflowDashboards;

import UI.Handlers.Navigator;
import UI.panes.SettingPanes.NeuronWorkflowPane;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.gui.OvalRoi;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;

import javax.swing.*;
import java.awt.*;

public class AnalyseNeuronDashboard extends JPanel {
    public static final String Name = "Analyse Dashboard";
    public AnalyseNeuronDashboard(Navigator navigator){
        super(new BorderLayout(5,5));
        setBorder(BorderFactory.createEmptyBorder(5,5,5,5));


        //The image view, dummy for now
        ImagePlus imp = IJ.createImage("Result","8-bit ramp",256,256,1);
        ImageCanvas canvas = imp.getCanvas();
        JScrollPane imgScroll = new JScrollPane(canvas);
        imgScroll.setBorder(BorderFactory.createTitledBorder("Image"));

        //Recreate ROI panel in here
        RoiManager rm = RoiManager.getInstance();
        if (rm == null) rm = new RoiManager();


        // 1) Rectangular ROI
        Roi r1 = new Roi(20, 30, 80, 50);
        imp.setRoi(r1);
        rm.add(imp, r1, 0);

        // 2) Oval ROI
        OvalRoi r2 = new OvalRoi(150, 100, 60, 40);
        imp.setRoi(r2);
        rm.add(imp, r2, 0);

        rm.setVisible(false);

        // Grab the AWT List directly
        java.awt.List awtList = rm.getList();
        // style it for Material L&F
        awtList.setBackground(new Color(48,48,48));
        awtList.setForeground(Color.WHITE);
        awtList.setMinimumSize(new Dimension(200,150));
        awtList.setPreferredSize(new Dimension(200,150));

        // Grab the toolbar & checkbox panels
        java.awt.Panel toolbar = null, checkboxPanel = null;
        for (Component c : rm.getComponents()) {
            if (c instanceof java.awt.Panel) {
                java.awt.Panel p = (java.awt.Panel)c;
                if (p.getLayout() instanceof FlowLayout) toolbar = p;
                else                                   checkboxPanel = p;
            }
        }
        // style them
        Color bg = new Color(48,48,48), fg = Color.WHITE;
        if (toolbar != null) {
            toolbar.setBackground(bg);
            for (Component b : toolbar.getComponents()) {
                b.setBackground(bg); b.setForeground(fg);
            }
        }
        if (checkboxPanel != null) {
            checkboxPanel.setBackground(bg);
            for (Component cb : checkboxPanel.getComponents()) {
                cb.setBackground(bg); cb.setForeground(fg);
            }
        }

        // wrap them exactly as the real ROI Manager does:
        JPanel roiWrapper = new JPanel(new BorderLayout());
        roiWrapper.setBorder(BorderFactory.createTitledBorder("ROI Manager"));
        if (toolbar  != null) roiWrapper.add(toolbar,  BorderLayout.NORTH);
        if (awtList       != null) roiWrapper.add(awtList,       BorderLayout.CENTER);
        if (checkboxPanel != null) roiWrapper.add(checkboxPanel, BorderLayout.SOUTH);

        //Create a dummy log
        JTextArea logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.append("Analysis started ...\n");
        logArea.append("Detected x and y cells\n");
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Log"));

        //set the preferred sizes so they sit correctly
        imgScroll .setMinimumSize(new Dimension(200,200));
        imgScroll .setPreferredSize(new Dimension(400,300));
        logScroll .setMinimumSize(new Dimension(200,100));
        logScroll .setPreferredSize(new Dimension(400,150));


        // Split ROI / Log
                JSplitPane rightSplit = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT, roiWrapper, logScroll
        );
        rightSplit.setResizeWeight(0.5);

        //  Main horizontal split: Image vs (ROI+Log)
        JSplitPane mainSplit = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, imgScroll, rightSplit
        );
        mainSplit.setResizeWeight(0.6);
        mainSplit.setDividerLocation(0.6);
        add(mainSplit, BorderLayout.CENTER);

        //Button to go back
        JButton backButton = new JButton("Back");
        backButton.addActionListener(e -> navigator.show(NeuronWorkflowPane.Name));
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.add(backButton);
        add(bottom, BorderLayout.SOUTH);



    }

}
