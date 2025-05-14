package Ui.panes.Tools;

import Ui.Handlers.Navigator;

import javax.swing.*;
import java.awt.*;

public class SpatialAnalysisPane extends JPanel {
    public static final String Name = "Spatial Analysis";

    public SpatialAnalysisPane(Navigator navigator){
        setLayout(new BorderLayout());
        add(new JLabel("Welcome to the Spatial Analysis Pane", SwingConstants.CENTER),BorderLayout.CENTER);
    }
}