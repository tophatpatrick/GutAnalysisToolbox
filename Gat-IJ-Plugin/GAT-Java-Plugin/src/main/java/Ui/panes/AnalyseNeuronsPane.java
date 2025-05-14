package Ui.panes;

import Ui.Handlers.Navigator;

import javax.swing.*;
import java.awt.*;

public class AnalyseNeuronsPane extends JPanel {
    public static final String Name = "Analyse Neurons";

    public AnalyseNeuronsPane(Navigator navigator){
        setLayout(new BorderLayout());
        add(new JLabel("Welcome to Analysing Neurons", SwingConstants.CENTER),BorderLayout.CENTER);
    }
}
