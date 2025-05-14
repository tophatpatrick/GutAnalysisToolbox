package Ui.panes;

import javax.swing.*;
import java.awt.*;

public class MultiplexPane  extends JPanel {
    public static final String Name = "Multiplex";

    public MultiplexPane(){
        setLayout(new BorderLayout());
        add(new JLabel("Welcome to the Multiplex Pane", SwingConstants.CENTER),BorderLayout.CENTER);
    }
}
