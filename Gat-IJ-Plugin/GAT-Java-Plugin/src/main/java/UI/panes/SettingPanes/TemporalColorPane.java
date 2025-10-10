package UI.panes.SettingPanes;

import Features.Core.Params;
import Analysis.TemporalColorCoder;
import Analysis.TemporalColorCoder.TemporalColorOutput;
import ij.ImagePlus;
import ij.IJ;
import UI.Handlers.Navigator;
import UI.panes.WorkflowDashboards.TemporalColourDashboardPane;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TemporalColorPane extends JPanel {

    public static final String Name = "Temporal Color Coder";

    private final Window owner;

    private JTextField tfStartFrame, tfEndFrame, tfImagePath;
    private JComboBox<String> cbLUT, cbProjection;
    private JCheckBox cbColorScale, cbBatchMode;
    private JButton runBtn, selectImageBtn;

    private JTabbedPane dashboardTabs;

    private ImagePlus selectedImage;

    public TemporalColorPane(Navigator navigator, Window owner) {
        super(new BorderLayout(10,10));
        this.owner = owner;
        dashboardTabs = new JTabbedPane();
        initUI();
    }

    private void initUI() {
        // ===== Info Panel (Top) =====
        JTextArea infoArea = new JTextArea(
            "Temporal Color Coding visualizes temporal dynamics in a stack. " +
            "Bright colors represent later frames; darker colors represent earlier frames. " +
            "Use this to see movement, calcium signals, or other time-lapse dynamics."
        );
        infoArea.setEditable(false);
        infoArea.setBackground(getBackground());
        infoArea.setLineWrap(true);
        infoArea.setWrapStyleWord(true);
        infoArea.setFont(infoArea.getFont().deriveFont(Font.PLAIN, 13f));
        infoArea.setBorder(BorderFactory.createEmptyBorder(4,4,4,4));
        add(infoArea, BorderLayout.NORTH);

        // ===== Settings Panel (Left) =====
        JPanel settingsPanel = new JPanel(new GridBagLayout());
        settingsPanel.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4,4,4,4);
        c.anchor = GridBagConstraints.LINE_END;

        int row = 0;

        // Image selection
        c.gridx=0; c.gridy=row; settingsPanel.add(new JLabel("Select Image:"), c);
        c.gridx=1; c.anchor = GridBagConstraints.LINE_START;
        tfImagePath = new JTextField(20); tfImagePath.setEditable(false);
        settingsPanel.add(tfImagePath, c);
        c.gridx=2; selectImageBtn = new JButton("Browse...");
        settingsPanel.add(selectImageBtn, c);
        selectImageBtn.addActionListener(e -> selectImageFile());
        row++;

        // Frame range
        c.gridx=0; c.gridy=row; c.anchor = GridBagConstraints.LINE_END;
        settingsPanel.add(new JLabel("Start Frame:"), c);
        c.gridx=1; c.anchor = GridBagConstraints.LINE_START;
        tfStartFrame = new JTextField("1",5); settingsPanel.add(tfStartFrame, c);
        row++;

        c.gridx=0; c.gridy=row; c.anchor = GridBagConstraints.LINE_END;
        settingsPanel.add(new JLabel("End Frame:"), c);
        c.gridx=1; c.anchor = GridBagConstraints.LINE_START;
        tfEndFrame = new JTextField("10",5); settingsPanel.add(tfEndFrame, c);
        row++;

        // LUT
        c.gridx=0; c.gridy=row; c.anchor = GridBagConstraints.LINE_END;
        settingsPanel.add(new JLabel("LUT:"), c);
        c.gridx=1; c.anchor = GridBagConstraints.LINE_START;
        cbLUT = new JComboBox<>(new String[]{"Fire","Ice","Green","Red"}); settingsPanel.add(cbLUT, c);
        row++;

        // Projection
        c.gridx=0; c.gridy=row; c.anchor = GridBagConstraints.LINE_END;
        settingsPanel.add(new JLabel("Projection:"), c);
        c.gridx=1; c.anchor = GridBagConstraints.LINE_START;
        cbProjection = new JComboBox<>(new String[]{"Max Intensity","Average Intensity","Min Intensity"});
        settingsPanel.add(cbProjection, c);
        row++;

        // Color Scale & Batch
        c.gridx=0; c.gridy=row; c.anchor = GridBagConstraints.LINE_END;
        settingsPanel.add(new JLabel("Color Scale:"), c);
        c.gridx=1; c.anchor = GridBagConstraints.LINE_START;
        cbColorScale = new JCheckBox(); cbColorScale.setSelected(true); settingsPanel.add(cbColorScale, c);
        row++;

        c.gridx=0; c.gridy=row; c.anchor = GridBagConstraints.LINE_END;
        settingsPanel.add(new JLabel("Batch Mode:"), c);
        c.gridx=1; c.anchor = GridBagConstraints.LINE_START;
        cbBatchMode = new JCheckBox(); settingsPanel.add(cbBatchMode, c);
        row++;

        // Run button
        c.gridx=0; c.gridy=row; c.gridwidth=3; c.anchor = GridBagConstraints.CENTER;
        runBtn = new JButton("Run Temporal Color Coding");
        settingsPanel.add(runBtn, c);
        runBtn.addActionListener(e -> runWorkflow());

        add(settingsPanel, BorderLayout.WEST);

        // ===== Dashboard Tabs (Center) =====
        add(dashboardTabs, BorderLayout.CENTER);
    }

    private void selectImageFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            tfImagePath.setText(f.getAbsolutePath());
            selectedImage = IJ.openImage(f.getAbsolutePath());
            if (selectedImage != null) selectedImage.show();
        }
    }

    private void runWorkflow() {
        if (selectedImage == null) { IJ.error("No image selected"); return; }

        try {
            Params p = new Params();
            p.referenceFrame = Integer.parseInt(tfStartFrame.getText());
            p.referenceFrameEnd = Integer.parseInt(tfEndFrame.getText());
            p.lutName = (String) cbLUT.getSelectedItem();
            p.projectionMethod = (String) cbProjection.getSelectedItem();
            p.createColorScale = cbColorScale.isSelected();
            p.batchMode = cbBatchMode.isSelected();

            int nFrames = selectedImage.getStackSize();
            int start = Integer.parseInt(tfStartFrame.getText());
            int end = Integer.parseInt(tfEndFrame.getText());
            if (start < 1 || end > nFrames || start > end) {
                IJ.error("Frame range invalid. Stack has " + nFrames + " frames.");
                return;
            }

            TemporalColorOutput output = TemporalColorCoder.run(selectedImage, p);

            // --- Dashboard for this run ---
            TemporalColourDashboardPane dashboard = new TemporalColourDashboardPane(owner);
            dashboard.setOutputs(output.rgbStack, output.colorScale, p);

            // --- Add live color strip at top ---
            ImagePlus scaleImg = output.colorScale; // ImagePlus
            int W = scaleImg.getWidth();
            Color[] colours = new Color[W];
            for (int x = 0; x < W; x++) {
                int rgb = scaleImg.getProcessor().getPixel(x, 0);
                colours[x] = new Color(rgb);
            }
            JPanel colorStrip = createColorScalePanel(300, 20, colours);
            dashboard.add(colorStrip, BorderLayout.NORTH);

            // --- Timestamped tab ---
            String tabName = String.format("Temporal: %s",
                    new SimpleDateFormat("HH:mm:ss").format(new Date()));
            dashboardTabs.addTab(tabName, dashboard);
            dashboardTabs.setSelectedComponent(dashboard);

            // --- Start live rotation of highlighted frame ---
            if (output.rgbStack.getStackSize() > 1) {
                Timer timer = new Timer(150, e -> {
                    int idx = output.rgbStack.getCurrentSlice();
                    idx = (idx % output.rgbStack.getStackSize()) + 1;
                    output.rgbStack.setSlice(idx);
                    colorStrip.repaint(); // update highlight on strip
                });
                timer.start();
            }

        } catch (Exception ex) {
            IJ.handleException(ex);
        }
    }

    /**
     * Creates a horizontal time-color bar representing all frames.
     * Highlights current frame in live rotation.
     */
    private JPanel createColorScalePanel(int width, int height, Color[] colorScale) {
        return new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                int n = colorScale.length;
                for (int i = 0; i < n; i++) {
                    int x0 = i * width / n;
                    int x1 = (i+1) * width / n;
                    g.setColor(colorScale[i]);
                    g.fillRect(x0, 0, x1-x0, height);
                }

                // Optional: highlight current slice
                if (selectedImage != null) {
                    int cur = selectedImage.getCurrentSlice() - 1;
                    if (cur >= 0 && cur < n) {
                        g.setColor(Color.WHITE);
                        int x = cur * width / n;
                        g.drawLine(x, 0, x, height);
                    }
                }
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(width, height);
            }
        };
    }

}
