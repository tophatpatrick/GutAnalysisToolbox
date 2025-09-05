package Features.Tools;

import javax.swing.*;
import java.awt.*;

public final class ProgressUI implements AutoCloseable {
    private final JDialog dialog;
    private final JProgressBar bar;
    private final JLabel label;
    private int total = 100;
    private int current = 0;

    public ProgressUI(String title) {
        dialog = new JDialog((Frame) null, title, false);
        Dimension fixedSize = new Dimension(300, 100);
        dialog.setPreferredSize(fixedSize);
        bar = new JProgressBar(0, 100);
        bar.setStringPainted(true);
        label = new JLabel("Starting...");
        JPanel p = new JPanel(new BorderLayout(10,10));
        p.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        p.add(label, BorderLayout.NORTH);
        p.add(bar, BorderLayout.CENTER);
        dialog.setContentPane(p);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setAlwaysOnTop(true);
        dialog.setVisible(true);
    }

    public void start(int totalSteps) {
        this.total = Math.max(1, totalSteps);
        set(0, "Starting...");
    }

    public void step(String msg) {
        set(current + 1, msg);
    }

    public void set(int step, String msg) {
        this.current = Math.max(0, Math.min(step, total));
        int pct = (int)Math.round(100.0 * current / total);
        SwingUtilities.invokeLater(() -> {
            label.setText(msg);
            bar.setValue(pct);
            bar.setString(pct + "%");
        });
        // Also mirror to ImageJ status bar:
        ij.IJ.showStatus(msg);
        ij.IJ.showProgress(current, total);
    }

    /** For long unknown-duration tasks, call periodically. */
    public void pulse(String msg) {
        SwingUtilities.invokeLater(() -> {
            label.setText(msg);
            bar.setIndeterminate(true);
        });
        ij.IJ.showStatus(msg);
    }

    public void stopPulse(String msg) {
        SwingUtilities.invokeLater(() -> {
            label.setText(msg);
            bar.setIndeterminate(false);
        });
        ij.IJ.showStatus(msg);
    }

    @Override public void close() {
        SwingUtilities.invokeLater(() -> dialog.dispose());
        ij.IJ.showProgress(1.0);
        ij.IJ.showStatus("");
    }
}
