package UI.panes.WorkflowDashboards;

import ij.ImagePlus;
import ij.process.ImageProcessor;

import javax.swing.*;
import java.awt.*;

public class TemporalColourDashboardPane extends JPanel {

    public static final String Name = "Temporal Color Dashboard";

    private final Window owner;
    private ImagePlus coloredStack;
    private ImagePlus colorScale;

    private JPanel stackPanel;
    private JPanel scalePanel;

    public TemporalColourDashboardPane(Window owner) {
        super(new BorderLayout(10,10));
        this.owner = owner;
        initUI();
    }

    private void initUI() {
        setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5,5,5,5);
        c.gridx = 0; c.gridy = 0;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1.0; c.weighty = 1.0;

        stackPanel = new JPanel(new BorderLayout());
        stackPanel.setBorder(BorderFactory.createTitledBorder("Colored Stack"));
        add(stackPanel, c);

        c.gridy++;
        scalePanel = new JPanel(new BorderLayout());
        scalePanel.setBorder(BorderFactory.createTitledBorder("Time Color Scale"));
        c.weighty = 0.2; // smaller height for scale
        add(scalePanel, c);
    }

    /**
     * Set the outputs to display in the dashboard
     */
    public void setOutputs(ImagePlus coloredStack, ImagePlus colorScale) {
        this.coloredStack = coloredStack;
        this.colorScale = colorScale;

        stackPanel.removeAll();
        scalePanel.removeAll();

        if (coloredStack != null) {
            // Convert first slice/frame into an ImageIcon for display
            ImageProcessor ip = coloredStack.getProcessor();
            Image img = ip.getBufferedImage();
            JLabel lblStack = new JLabel(new ImageIcon(img));
            JScrollPane scrollStack = new JScrollPane(lblStack);
            stackPanel.add(scrollStack, BorderLayout.CENTER);
        }

        if (colorScale != null) {
            ImageProcessor ipScale = colorScale.getProcessor();
            Image imgScale = ipScale.getBufferedImage();
            JLabel lblScale = new JLabel(new ImageIcon(imgScale));
            scalePanel.add(lblScale, BorderLayout.CENTER);
        }

        revalidate();
        repaint();
    }
}
