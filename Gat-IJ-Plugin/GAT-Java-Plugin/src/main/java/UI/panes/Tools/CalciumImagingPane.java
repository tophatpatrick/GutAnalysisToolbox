package UI.panes.Tools;

import UI.Handlers.Navigator;

import javax.swing.*;
import java.awt.*;

public class CalciumImagingPane extends JPanel {
    public static final String Name = "Calcium imaging";

    public CalciumImagingPane(Navigator navigator) {
        setLayout(new BorderLayout(12,12));
        setBorder(BorderFactory.createEmptyBorder(16,16,16,16));

        JLabel title = new JLabel("Calcium imaging", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        add(title, BorderLayout.NORTH);

        JPanel menu = new JPanel();
        menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS));
        menu.setBorder(BorderFactory.createEmptyBorder(12,0,12,24));

        menu.add(actionBtn("Align stack", this::onAlignStack));
        menu.add(space());
        menu.add(actionBtn("Align stack batch", this::onAlignStackBatch));
        menu.add(space());
        menu.add(actionBtn("Calcium imaging analysis", this::onAnalysis));
        menu.add(space());
        menu.add(actionBtn("Temporal Colour Code (GAT)", this::onTemporalColourCode));

        JPanel leftWrap = new JPanel(new BorderLayout());
        leftWrap.add(menu, BorderLayout.NORTH);
        add(leftWrap, BorderLayout.WEST);

        add(new JLabel("Choose an action from the left", SwingConstants.CENTER), BorderLayout.CENTER);
    }

    private static Component space() { return Box.createVerticalStrut(8); }

    private static JButton actionBtn(String label, Runnable action) {
        JButton b = new JButton(label);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(320, 36));
        b.addActionListener(e -> action.run());
        return b;
    }

    // ----- hook these to your macros / pipelines -----
    private void onAlignStack() {
        // TODO: wire to ImageJ command or your Java pipeline
        JOptionPane.showMessageDialog(this, "Align stack (not implemented)", "Info", JOptionPane.INFORMATION_MESSAGE);
    }

    private void onAlignStackBatch() {
        JOptionPane.showMessageDialog(this, "Align stack batch (not implemented)", "Info", JOptionPane.INFORMATION_MESSAGE);
    }

    private void onAnalysis() {
        JOptionPane.showMessageDialog(this, "Calcium imaging analysis (not implemented)", "Info", JOptionPane.INFORMATION_MESSAGE);
    }

    private void onTemporalColourCode() {
        JOptionPane.showMessageDialog(this, "Temporal Colour Code (GAT) (not implemented)", "Info", JOptionPane.INFORMATION_MESSAGE);
    }
}
