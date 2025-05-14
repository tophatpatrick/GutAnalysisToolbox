package Ui.panes;

import javax.swing.*;
import java.awt.*;

public class AnalyseNeuronsPane extends JPanel {
    public static final String Name = "AnalyseNeurons";

    public AnalyseNeuronsPane(){
        setLayout(new BorderLayout());
        add(new JLabel("Welcome to Analysing Neurons", SwingConstants.CENTER),BorderLayout.CENTER);
    }
}
