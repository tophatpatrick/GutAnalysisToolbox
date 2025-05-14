package Ui.panes;

import javax.swing.*;
import java.awt.*;

public class SpatialAnalysisPane extends JPanel {
    public static final String Name = "SpatialAnalysis";

    public SpatialAnalysisPane(){
        setLayout(new BorderLayout());
        add(new JLabel("Welcome to the Spatial Analysis Pane", SwingConstants.CENTER),BorderLayout.CENTER);
    }
}