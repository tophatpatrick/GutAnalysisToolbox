package Ui.panes.SettingPanes;

import Ui.Handlers.Navigator;

import javax.swing.*;
import java.awt.*;

public class NeuronWorkflowPane extends JPanel{

    public static final String Name = "Neuron Work Flow";

    public NeuronWorkflowPane(Navigator navigator){
        setLayout(new BorderLayout());
        add(new JLabel("Neuron Work flow settings", SwingConstants.CENTER),BorderLayout.CENTER);
    }
}
