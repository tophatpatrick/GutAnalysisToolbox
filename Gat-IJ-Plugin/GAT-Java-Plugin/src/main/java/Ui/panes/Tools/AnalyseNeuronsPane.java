package Ui.panes.Tools;

import Ui.Handlers.Navigator;
import Ui.panes.SettingPanes.MultiChannelNoHuPane;
import Ui.panes.SettingPanes.MultichannelPane;
import Ui.panes.SettingPanes.NeuronWorkflowPane;

import javax.swing.*;
import java.awt.*;

public class AnalyseNeuronsPane extends JPanel {
    public static final String Name = "Analyse Neurons";

    public AnalyseNeuronsPane(Navigator navigator){

        setLayout(new BorderLayout(10,10));
        setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        // Create our three toggle options
        JToggleButton btnNeurons = createOptionToggle(
                "Analyse Neurons",
                "Run the neuron analysis pipeline"
        );
        JToggleButton btnNoHu    = createOptionToggle(
                "Multichannel – No HU",
                "Process only multichannel images"
        );
        JToggleButton btnMulti   = createOptionToggle(
                "Multichannel",
                "Let us figure out the difference on this one"
        );

        ButtonGroup group = new ButtonGroup();
        group.add(btnNeurons);
        group.add(btnNoHu);
        group.add(btnMulti);
        btnNeurons.setSelected(true);


        JPanel choices = new JPanel(new GridLayout(1,3,10,10));
        choices.add(btnNeurons);
        choices.add(btnNoHu);
        choices.add(btnMulti);
        add(choices,BorderLayout.CENTER);


        JButton go = new JButton("Go");
        go.addActionListener(e -> {
            if      (btnNeurons.isSelected()) navigator.show(NeuronWorkflowPane.Name);
            else if (btnNoHu   .isSelected()) navigator.show(MultiChannelNoHuPane.Name);
            else if (btnMulti  .isSelected()) navigator.show(MultichannelPane.Name);
        });
        JPanel goPanel = new JPanel();
        goPanel.add(go);
        add(goPanel, BorderLayout.SOUTH);


    }



    //Function to build each box with a title and description

    private JToggleButton createOptionToggle(String title, String desc) {

        String html = "<html><div style='text-align:center;'>"
                + "<b>" + title + "</b><br/>"
                + desc
                + "</div></html>";
        JToggleButton t = new JToggleButton(html);
        t.setFont(t.getFont().deriveFont(14f));
        return t;
    }
}
