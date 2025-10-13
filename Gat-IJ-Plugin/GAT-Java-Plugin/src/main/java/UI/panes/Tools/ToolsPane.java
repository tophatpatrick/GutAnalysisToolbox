package UI.panes.Tools;

import UI.Handlers.Navigator;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

public class ToolsPane extends JPanel {
    public static final String Name = "Tools";

    public ToolsPane(Navigator navigator){
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(16,16,16,16));

        // Title
        JLabel title = new JLabel("Tools", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        add(title, BorderLayout.NORTH);

        // Left-aligned vertical menu
        JPanel menu = new JPanel();
        menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS));
        menu.setBorder(BorderFactory.createEmptyBorder(12, 0, 12, 24));

        // Plain actions
        menu.add(menuButton("QC cell count GT predictions", () ->
                notImplemented("QC cell count GT predictions")));
        menu.add(space());

        menu.add(menuButton("Save max projection", () ->
                notImplemented("Save max projection")));
        menu.add(space());

        menu.add(menuButton("Test neuron probability", () ->
                notImplemented("Test neuron probability")));
        menu.add(space());

        menu.add(menuButton("Test neuron rescaling", () ->
                notImplemented("Test neuron rescaling")));
        menu.add(space());

        menu.add(menuButton("Test neuron rescaling advanced", () ->
                notImplemented("Test neuron rescaling advanced")));
        menu.add(space());

        menu.add(menuButton("^Test ganglia segmentation Hu", () ->
                notImplemented("^Test ganglia segmentation Hu")));
        menu.add(space());

        // Submenus: Data Curation ▸ and commands ▸
        menu.add(submenuButton("Data Curation", new String[]{
                "Annotate cells 2D", "Annotate ganglia 2D", "Rescale image masks pixel size", "Rescale image masks resolution", "Verify images Masks"
        }));

        menu.add(space());

        menu.add(submenuButton("commands", new String[]{
                "Calculate Neurons per Ganglia", "Calculate Neurons per Ganglia label", "....."
        }));

        // Push everything left, leave center area free
        JPanel leftWrap = new JPanel(new BorderLayout());
        leftWrap.add(menu, BorderLayout.NORTH); // keep compact at top
        add(leftWrap, BorderLayout.WEST);
        
    }

    // ---- UI helpers ----

    private static Component space() {
        return Box.createVerticalStrut(8);
    }

    private static JButton menuButton(String text, Runnable action){
        JButton b = new JButton(text);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(320, 36));
        b.addActionListener(e -> action.run());
        return b;
    }

    private JButton submenuButton(String title, String[] items){
        // Button with a right-pointing triangle to indicate submenu
        JButton b = new JButton(title + "  ▸");
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(320, 36));

        // Build popup
        JPopupMenu popup = new JPopupMenu();
        Arrays.stream(items).forEach(label -> {
            JMenuItem mi = new JMenuItem(label);
            mi.addActionListener(e -> notImplemented(title + " → " + label));
            popup.add(mi);
        });

        b.addActionListener(e -> {
            // Show popup just to the right of the button
            popup.show(b, b.getWidth() - 8, b.getHeight()/2);
        });
        return b;
    }

    private void notImplemented(String what){
        JOptionPane.showMessageDialog(
                this,
                what + " (not implemented yet)",
                "Info",
                JOptionPane.INFORMATION_MESSAGE
        );
    }
}
