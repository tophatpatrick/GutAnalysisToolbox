package UI.panes.WorkflowDashboards;

import Features.Core.Params;
import ij.ImagePlus;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class TemporalColourDashboardPane extends JPanel {

    private JPanel imagePanel;
    private JTextArea paramInfo;
    private JPanel intensityPlotPanel;

    public TemporalColourDashboardPane(Window owner) {
        super(new BorderLayout(6,6));

        // Parameter info panel
        paramInfo = new JTextArea();
        paramInfo.setEditable(false);
        paramInfo.setBackground(getBackground());
        add(paramInfo, BorderLayout.WEST);

        // Image panel
        imagePanel = new JPanel(new BorderLayout());
        add(imagePanel, BorderLayout.CENTER);

        // Optional intensity plot at bottom
        intensityPlotPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // Draw dummy plot as example
                g.setColor(Color.BLUE);
                int w = getWidth(), h = getHeight();
                for (int i = 0; i < w; i+=5) g.drawLine(i, h, i, h - (i%h));
            }
        };
        intensityPlotPanel.setPreferredSize(new Dimension(400,100));
        add(intensityPlotPanel, BorderLayout.SOUTH);
    }

    public void setOutputs(ImagePlus rgbStack, ImagePlus colorScale, Params p) {
        // Image display
        BufferedImage bi = rgbStack.getBufferedImage();
        imagePanel.removeAll();
        imagePanel.add(new JLabel(new ImageIcon(bi)), BorderLayout.CENTER);

        // Parameter display
        paramInfo.setText(String.format(
                "Start Frame: %d\nEnd Frame: %d\nLUT: %s\nProjection: %s\nColor Scale: %s\nBatch Mode: %s",
                p.referenceFrame, p.referenceFrameEnd, p.lutName, p.projectionMethod,
                p.createColorScale, p.batchMode
        ));

        revalidate();
        repaint();
    }
}
