package Ui.panes;

import Ui.Handlers.Navigator;

import javax.swing.*;
import java.awt.*;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;



public class HomePane extends JPanel{

    public static final String Name = "Home";

    private final DateTimeFormatter fmt  =DateTimeFormatter.ofPattern("dd-MM-yyyy  HH:mm:ss");
    private final Timer clockTimer;
    private final JLabel clockLabel;

    public HomePane(Navigator navigator){

        setLayout(new BorderLayout(6,6));
        setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        //Creating our info Row (clock | title | help button)
        JPanel infoRow = new JPanel(new BorderLayout(4,4));

        clockLabel = new JLabel(fmt.format(LocalDateTime.now()));
        clockLabel.setFont(clockLabel.getFont().deriveFont(Font.BOLD,14f));
        infoRow.add(clockLabel,BorderLayout.WEST);

        JLabel titleLabel = new JLabel("Gut Analysis Toolbox", SwingConstants.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(16f));
        infoRow.add(titleLabel, BorderLayout.CENTER);

        JButton helpBtn = new JButton("Help & Support");
        infoRow.add(helpBtn, BorderLayout.EAST);

        add(infoRow, BorderLayout.NORTH);

        //Enable the "Clock timer" so that we can display the current day and time
        clockTimer = new Timer(1000,e->
                clockLabel.setText(fmt.format(LocalDateTime.now())));
        clockTimer.setInitialDelay(0);
        clockTimer.start();

        JLabel dashLabel = new JLabel("Dashboard Placeholder", SwingConstants.CENTER);
        dashLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY,2));
        dashLabel.setPreferredSize(new Dimension(550,450));

        add(dashLabel,BorderLayout.CENTER);

    }

}
