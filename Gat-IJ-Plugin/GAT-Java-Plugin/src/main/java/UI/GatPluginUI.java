package UI;

import Features.AnalyseWorkflows.NeuronsHuPipeline;
import Features.Core.Params;
import UI.panes.SettingPanes.*;
import UI.panes.Tools.*;
import UI.panes.WorkflowDashboards.AnalyseNeuronDashboard;
import ij.IJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import mdlaf.MaterialLookAndFeel;
import mdlaf.themes.MaterialOceanicTheme;

import UI.panes.*;
import UI.Handlers.*;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public class GatPluginUI implements PlugIn {

    private CardLayout cards = new CardLayout();
    private JPanel cardPanel = new JPanel(cards);
    Navigator navigator = name -> cards.show(cardPanel,name);


    static {
        // Install Material UI L&F on the EDT
        try {
            UIManager.setLookAndFeel(
                    new MaterialLookAndFeel(new MaterialOceanicTheme())
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run(String arg){
        SwingUtilities.invokeLater(this::buildAndShow);


        Params p = new Params();
        p.imagePath = "/Users/miles/Desktop/UNI/Year5/SEM2/FIT4002/Testing/ms_28_wk_colon_DAPI_nNOS_Hu_10X.tif";
        p.huChannel = 3;
        p.stardistModelZip = new File(new File(IJ.getDirectory("imagej"), "models"), "2D_enteric_neuron_v4_1.zip").getAbsolutePath();
        p.trainingPixelSizeUm = 0.568;
        p.probThresh = 0.5;
        p.nmsThresh = 0.3;
        p.neuronSegMinMicron = 70.0;
        p.saveFlattenedOverlay = true;
        p.rescaleToTrainingPx = true;
        p.useClij2EDF = false;
        p.cellCountsPerGanglia = true;
        p.gangliaMode = Params.GangliaMode.DEFINE_FROM_HU;
        p.huDilationMicron = 12.0;

        new NeuronsHuPipeline().run(p);
    }

    private void buildAndShow(){

        //Our main window which will host the plugin
        JDialog dialog = new JDialog(
                IJ.getInstance(),
                "GAT Plugin",
                false
        );
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(8,8));
//        dialog.setPreferredSize(new Dimension(900,550));
        Dimension fixedSize = new Dimension(900, 550);
        dialog.setPreferredSize(fixedSize);
        dialog.setMinimumSize(fixedSize);
        dialog.setResizable(true);        // Lock size

        // Our left toolbar with buttons
        JPanel leftBar = new JPanel();
        leftBar.setLayout(new BoxLayout(leftBar,BoxLayout.Y_AXIS));
        leftBar.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        leftBar.add(Box.createVerticalGlue());

        //Register other panels
        cardPanel.add(new HelpAndSupportPane(navigator),HelpAndSupportPane.Name);
        cardPanel.add(new NeuronWorkflowPane(navigator,dialog),NeuronWorkflowPane.Name);
        cardPanel.add(new MultiChannelNoHuPane(navigator),MultiChannelNoHuPane.Name);
        cardPanel.add(new MultichannelPane(navigator),MultichannelPane.Name);

        // register your dashboard pane




        //Register each of our panes in the card panel
        Map<String, JPanel> panes = new LinkedHashMap<>();
        panes.put(HomePane.Name, new HomePane(navigator));
        panes.put(AnalysisPane.Name,        new AnalysisPane(navigator));
        panes.put(AnalyseNeuronsPane.Name,  new AnalyseNeuronsPane(navigator));
        panes.put(CalciumImagingPane.Name,  new CalciumImagingPane(navigator));
        panes.put(MultiplexPane.Name,       new MultiplexPane(navigator));
        panes.put(ToolsPane.Name,           new ToolsPane(navigator));
        panes.put(SpatialAnalysisPane.Name, new SpatialAnalysisPane(navigator));



        //Register the panes in the card panel and create the button
        for (Map.Entry<String, JPanel> e: panes.entrySet()){
            String name = e.getKey();
            JPanel pane = e.getValue();

            //add to the CardLayout
            cardPanel.add(pane,name);


            JButton btn = new JButton(name);
            btn.setAlignmentX(Component.CENTER_ALIGNMENT);
            btn.setMaximumSize(new Dimension(160,36));
            btn.addActionListener(ae -> cards.show(cardPanel,name));

            leftBar.add(btn);
            leftBar.add(Box.createVerticalStrut(6));

        }

        leftBar.add(Box.createVerticalGlue());

        dialog.add(leftBar, BorderLayout.WEST);
        dialog.add(cardPanel, BorderLayout.CENTER);



        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
        navigator.show(HomePane.Name);

    }

}
