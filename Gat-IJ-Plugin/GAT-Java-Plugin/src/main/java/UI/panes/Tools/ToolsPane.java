package UI.panes.Tools;

import UI.Handlers.Navigator;

import javax.swing.*;
import java.awt.*;

public class ToolsPane extends JPanel {
    public static final String Name = "Tools";

    public ToolsPane(Navigator navigator){
        setLayout(new BorderLayout());
        add(new JLabel("Welcome to the Tools Pane", SwingConstants.CENTER),BorderLayout.CENTER);
    }
}
