package Ui;

import Ui.panes.SettingPanes.*;
import Ui.panes.Tools.*;
import ij.IJ;
import ij.plugin.PlugIn;
import mdlaf.MaterialLookAndFeel;
import mdlaf.themes.MaterialOceanicTheme;

import Ui.panes.*;
import Ui.Handlers.*;
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
        dialog.setPreferredSize(new Dimension(900,550));

        // Our left toolbar with buttons
        JPanel leftBar = new JPanel();
        leftBar.setLayout(new BoxLayout(leftBar,BoxLayout.Y_AXIS));
        leftBar.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        leftBar.add(Box.createVerticalGlue());

        //Register other panels
        cardPanel.add(new HelpAndSupportPane(navigator),HelpAndSupportPane.Name);
        cardPanel.add(new NeuronWorkflowPane(navigator),NeuronWorkflowPane.Name);
        cardPanel.add(new MultiChannelNoHuPane(navigator),MultiChannelNoHuPane.Name);
        cardPanel.add(new MultichannelPane(navigator),MultichannelPane.Name);




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
