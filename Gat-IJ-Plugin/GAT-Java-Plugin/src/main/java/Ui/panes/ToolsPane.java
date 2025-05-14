package Ui.panes;

import javax.swing.*;
import java.awt.*;

public class ToolsPane extends JPanel {
    public static final String Name = "ToolsPane";

    public ToolsPane(){
        setLayout(new BorderLayout());
        add(new JLabel("Welcome to the Tools Pane", SwingConstants.CENTER),BorderLayout.CENTER);
    }
}
