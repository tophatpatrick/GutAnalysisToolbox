package Ui.panes.Tools;

import Ui.Handlers.Navigator;

import javax.swing.*;
import java.awt.*;

public class CalciumImagingPane extends JPanel {
    public static final String Name = "Calcium Imaging";

    public CalciumImagingPane(Navigator navigator){
        setLayout(new BorderLayout());
        add(new JLabel("Welcome to Calcium Imaging Pane", SwingConstants.CENTER),BorderLayout.CENTER);
    }
}