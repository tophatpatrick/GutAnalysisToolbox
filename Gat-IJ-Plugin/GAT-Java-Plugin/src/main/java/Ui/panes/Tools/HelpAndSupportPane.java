package Ui.panes.Tools;

import Ui.Handlers.Navigator;

import javax.swing.*;
import java.awt.*;
public class HelpAndSupportPane extends JPanel {
    public static final String Name = "HelpAndSupport";

    public HelpAndSupportPane(Navigator navigator){
        setLayout(new BorderLayout());
        add(new JLabel("Welcome to Help and Support", SwingConstants.CENTER),BorderLayout.CENTER);
    }
}
