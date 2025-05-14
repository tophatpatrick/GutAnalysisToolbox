package Ui.panes;

import javax.swing.*;
import java.awt.*;

public class CalciumImagingPane extends JPanel {
    public static final String Name = "CalciumImaging";

    public CalciumImagingPane(){
        setLayout(new BorderLayout());
        add(new JLabel("Welcome to Calcium Imaging Pane", SwingConstants.CENTER),BorderLayout.CENTER);
    }
}