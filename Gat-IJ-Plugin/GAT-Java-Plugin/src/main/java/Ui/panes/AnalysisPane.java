package Ui.panes;

import javax.swing.*;
import java.awt.*;

public class AnalysisPane extends JPanel {
    public static final String Name = "Analysis";

    public AnalysisPane(){
        setLayout(new BorderLayout());
        add(new JLabel("Welcome to the Analyse Pane", SwingConstants.CENTER),BorderLayout.CENTER);
    }
}
