package Ui.panes.WorkflowDashboards;

import Ui.Handlers.Navigator;
import Ui.panes.SettingPanes.NeuronWorkflowPane;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.gui.OvalRoi;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

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
        // 1) Rectangular ROI
        Roi r1 = new Roi(20, 30, 80, 50);
        imp.setRoi(r1);
        rm.add(imp, r1, 0);   // slice-index 0 for single-slice images

        // 2) Oval ROI
        OvalRoi r2 = new OvalRoi(150, 100, 60, 40);
        imp.setRoi(r2);
        rm.add(imp, r2, 0);

        if (rm == null) rm = new RoiManager();
        rm.setVisible(false);
        java.awt.Panel awtPanel = null;
        for (Component c : rm.getComponents())
            if (c instanceof java.awt.Panel) {
                awtPanel = (java.awt.Panel)c;
                break;
            }
        if (awtPanel != null) {
            awtPanel.setBackground(new Color(48,48,48));
            for (Component c : awtPanel.getComponents()) {
                c.setBackground(new Color(48,48,48));
                c.setForeground(Color.WHITE);
                if (c instanceof java.awt.Checkbox) {
                    ((java.awt.Checkbox)c).setForeground(Color.WHITE);
                }
            }
            awtPanel.setMinimumSize(new Dimension(200,200));
        }

        // wrap it
        JPanel roiWrapper = new JPanel(new BorderLayout());
        roiWrapper.setBorder(BorderFactory.createTitledBorder("ROI Manager"));
        if (awtPanel != null) roiWrapper.add(awtPanel, BorderLayout.CENTER);

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
        mainSplit.setResizeWeight(0.5);
        mainSplit.setDividerLocation(0.5);
        add(mainSplit, BorderLayout.CENTER);

        //Button to go back
        JButton backButton = new JButton("Back");
        backButton.addActionListener(e -> navigator.show(NeuronWorkflowPane.Name));
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.add(backButton);
        add(bottom, BorderLayout.SOUTH);



    }

    private JTable buildRoiTable(List<Roi> rois) {
        String[] cols = { "Name","Bounds" };
        DefaultTableModel model = new DefaultTableModel(cols, 0);
        for (int i = 0; i < rois.size(); i++) {
            Roi r = rois.get(i);
            Rectangle b = r.getBounds();
            model.addRow(new Object[]{
                    "ROI " + (i+1),
                    String.format("x=%d,y=%d,w=%d,h=%d", b.x,b.y,b.width,b.height)
            });
        }
        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        // selecting a row will draw that ROI on the image
        table.getSelectionModel().addListSelectionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                ImagePlus imp = IJ.getImage();
                imp.setRoi(rois.get(row));
            }
        });
        return table;
    }
}
