package Ui.panes.SettingPanes;

import Ui.Handlers.Navigator;

import javax.swing.*;
import java.awt.*;

public class MultichannelPane extends JPanel{

    public static final String Name = "Multi Channel Pane";

    public MultichannelPane(Navigator navigator){
        setLayout(new BorderLayout());
        add(new JLabel("Multi Channel Pane settings", SwingConstants.CENTER),BorderLayout.CENTER);
    }
}
