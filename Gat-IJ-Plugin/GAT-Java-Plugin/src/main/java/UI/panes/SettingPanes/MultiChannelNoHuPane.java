package UI.panes.SettingPanes;

import UI.Handlers.Navigator;

import javax.swing.*;
import java.awt.*;

public class MultiChannelNoHuPane extends JPanel {

    public static final String Name = "Multi-Channel No Hu";

    public MultiChannelNoHuPane(Navigator navigator){
        setLayout(new BorderLayout());
        add(new JLabel("Multi Channel No Hu settings", SwingConstants.CENTER),BorderLayout.CENTER);
    }
}
