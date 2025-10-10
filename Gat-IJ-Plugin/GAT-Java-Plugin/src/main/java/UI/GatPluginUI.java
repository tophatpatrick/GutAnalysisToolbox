package UI;

import UI.panes.SettingPanes.*;
import UI.panes.Tools.*;
import ij.IJ;
import ij.plugin.PlugIn;
import mdlaf.MaterialLookAndFeel;
import mdlaf.themes.MaterialOceanicTheme;

import UI.panes.*;
import UI.Handlers.*;
import javax.swing.*;
import java.awt.*;
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
        String expectedNeuronModel  = "2D_enteric_neuron_V4_1.zip"; // e.g.
        String expectedSubtypeModel = "2D_enteric_neuron_subtype_V4.zip" ;

        // Run preflight; bail out if anything critical is missing.
        if (!UI.Preflight.runAll(expectedNeuronModel, expectedSubtypeModel)) {
            return;
        }

        SwingUtilities.invokeLater(this::buildAndShow);

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
        Dimension fixedSize = new Dimension(950, 650);
        dialog.setPreferredSize(fixedSize);
        dialog.setMinimumSize(fixedSize);
        dialog.setResizable(false);        // Lock size

        // Our left toolbar with buttons
        JPanel leftBar = new JPanel();
        leftBar.setLayout(new BoxLayout(leftBar,BoxLayout.Y_AXIS));
        leftBar.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        leftBar.add(Box.createVerticalGlue());

        //Register other panels
        cardPanel.add(new HelpAndSupportPane(navigator),HelpAndSupportPane.Name);
        cardPanel.add(new NeuronWorkflowPane(navigator,dialog),NeuronWorkflowPane.Name);
        cardPanel.add(new MultiChannelNoHuPane(navigator),MultiChannelNoHuPane.Name);
        cardPanel.add(new MultichannelPane(dialog),MultichannelPane.Name);

        // register your dashboard pane
        cardPanel.add(new alignStackPane(navigator, dialog), alignStackPane.Name);
        cardPanel.add(new calciumImagingAnalysisPane(navigator, dialog), calciumImagingAnalysisPane.Name);
        cardPanel.add(new TemporalColorPane(navigator, dialog), TemporalColorPane.Name);



        //Register each of our panes in the card panel
        Map<String, JPanel> panes = new LinkedHashMap<>();
        panes.put(HomePane.Name, new HomePane(navigator));
        panes.put(AnalysisPane.Name,        new AnalysisPane(navigator));
        panes.put(AnalyseNeuronsPane.Name,  new AnalyseNeuronsPane(navigator));
        panes.put(CalciumImagingPane.Name,  new CalciumImagingPane(navigator));        panes.put(MultiplexPane.Name,       new MultiplexPane(navigator));
        panes.put(ToolsPane.Name,           new ToolsPane(navigator));
        panes.put(SpatialAnalysisPane.Name, new SpatialAnalysisPane(navigator, dialog));



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
