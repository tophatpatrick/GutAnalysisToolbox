package Ui;

import ij.IJ;
import ij.plugin.PlugIn;
import mdlaf.MaterialLookAndFeel;
import mdlaf.themes.MaterialOceanicTheme;

import javax.swing.*;
import java.awt.*;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class GatPluginUI implements PlugIn {

    static {
        // Install Material UI L&F on the EDT
        try {
            UIManager.setLookAndFeel(
                    new MaterialLookAndFeel(new MaterialOceanicTheme())
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Swing timer for live clock
    private final DateTimeFormatter fmt =
            DateTimeFormatter.ofPattern("dd-MM-yyyy  HH:mm:ss");
    private final Timer clockTimer = new Timer(1000, e -> {/* listener set below */});

    @Override
    public void run(String arg) {
        SwingUtilities.invokeLater(this::buildAndShow);
    }

    private void buildAndShow() {
        // --- top‐level window ---
        JDialog dialog = new JDialog(IJ.getInstance(),
                "GAT Plugin",
                false);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.setPreferredSize(new Dimension(900, 550));

        // --- 1) Left toolbar (Material buttons) ---
        JPanel leftBar = new JPanel();
        leftBar.setLayout(new BoxLayout(leftBar, BoxLayout.Y_AXIS));
        leftBar.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        leftBar.add(Box.createVerticalGlue());
        String[] tools = {
                "Analyse Neurons","Analysis",
                "Calcium Imaging","Multiplex","Spatial Analysis","Tools"
        };
        for (String name : tools) {
            JButton b = new JButton(name);
            b.setAlignmentX(Component.CENTER_ALIGNMENT);
            b.setMaximumSize(new Dimension(160, 36));
            leftBar.add(b);
            leftBar.add(Box.createVerticalStrut(6));
        }
        leftBar.add(Box.createVerticalGlue());
        dialog.add(leftBar, BorderLayout.WEST);

        // --- 2) Center info panel ---
        JPanel center = new JPanel(new BorderLayout(6,6));
        center.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Clock + Help row
        JPanel infoRow = new JPanel(new BorderLayout(4,4));
        JLabel clockLabel = new JLabel();
        clockLabel.setFont(clockLabel.getFont().deriveFont(Font.BOLD, 14f));
        // set up timer to update clockLabel
        clockTimer.addActionListener(e ->
                clockLabel.setText(fmt.format(LocalDateTime.now()))
        );
        clockTimer.setInitialDelay(0);
        clockTimer.start();

        infoRow.add(clockLabel, BorderLayout.WEST);

        JLabel titleLabel = new JLabel("Gut Analysis ToolBox");
        titleLabel.setFont(titleLabel.getFont().deriveFont(16f));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        infoRow.add(titleLabel, BorderLayout.CENTER);


        JButton help = new JButton("Help and Support");
        infoRow.add(help, BorderLayout.EAST);
        center.add(infoRow, BorderLayout.NORTH);

        // --- 3) Image placeholder ---
        // 2b) Image placeholder, loaded from resources
        JLabel imageLabel = new JLabel("", SwingConstants.CENTER);
        imageLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 2));
        imageLabel.setPreferredSize(new Dimension(550, 450));

        URL imgUrl = getClass().getResource("/org/example/images/myImage.png");
        if (imgUrl != null) {
            imageLabel.setIcon(new ImageIcon(imgUrl));
        } else {
            imageLabel.setText("Dashboard Placeholder");
        }
        center.add(imageLabel, BorderLayout.CENTER);

        dialog.add(center, BorderLayout.CENTER);

        // --- finalise & display ---
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }
}
