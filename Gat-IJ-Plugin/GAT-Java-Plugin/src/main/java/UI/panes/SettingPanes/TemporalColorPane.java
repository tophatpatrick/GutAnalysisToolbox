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

public class TemporalColorPane extends JPanel {

    public static final String Name = "Temporal Color Coder";

    private final Window owner;

    private JTextField tfStartFrame;
    private JTextField tfEndFrame;
    private JComboBox<String> cbLUT;
    private JComboBox<String> cbProjection;
    private JCheckBox cbColorScale;
    private JCheckBox cbBatchMode;
    private JButton runBtn;
    private JButton selectImageBtn;
    private JTextField tfImagePath;

    private JTabbedPane mainTabs;
    private TemporalColourDashboardPane dashboard;

    private ImagePlus selectedImage;

    public TemporalColorPane(Navigator navigator, Window owner) {
        super(new BorderLayout(10, 10));
        this.owner = owner;

        // Main JTabbedPane
        mainTabs = new JTabbedPane();
        this.add(mainTabs, BorderLayout.CENTER);

        // Add Settings Tab
        JPanel settingsTab = buildSettingsTab();
        mainTabs.addTab("Temporal Color Settings", settingsTab);
    }

    private JPanel buildSettingsTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.gridx = 0; c.gridy = 0; c.anchor = GridBagConstraints.LINE_END;

        // Image selection
        panel.add(new JLabel("Select Image:"), c);
        c.gridx = 1; c.anchor = GridBagConstraints.LINE_START;
        tfImagePath = new JTextField(20);
        tfImagePath.setEditable(false);
        panel.add(tfImagePath, c);

        c.gridx = 2;
        selectImageBtn = new JButton("Browse...");
        panel.add(selectImageBtn, c);
        selectImageBtn.addActionListener(e -> selectImageFile());

        // Start Frame
        c.gridx = 0; c.gridy++;
        c.anchor = GridBagConstraints.LINE_END;
        panel.add(new JLabel("Start Frame:"), c);
        c.gridx = 1; c.anchor = GridBagConstraints.LINE_START;
        tfStartFrame = new JTextField("1", 5);
        panel.add(tfStartFrame, c);

        // End Frame
        c.gridx = 0; c.gridy++;
        c.anchor = GridBagConstraints.LINE_END;
        panel.add(new JLabel("End Frame:"), c);
        c.gridx = 1; c.anchor = GridBagConstraints.LINE_START;
        tfEndFrame = new JTextField("10", 5);
        panel.add(tfEndFrame, c);

        // LUT
        c.gridx = 0; c.gridy++;
        c.anchor = GridBagConstraints.LINE_END;
        panel.add(new JLabel("LUT:"), c);
        c.gridx = 1; c.anchor = GridBagConstraints.LINE_START;
        cbLUT = new JComboBox<>(new String[]{"Fire", "Ice", "Green", "Red"});
        panel.add(cbLUT, c);

        // Projection
        c.gridx = 0; c.gridy++;
        c.anchor = GridBagConstraints.LINE_END;
        panel.add(new JLabel("Projection:"), c);
        c.gridx = 1; c.anchor = GridBagConstraints.LINE_START;
        cbProjection = new JComboBox<>(new String[]{
                "Max Intensity", "Average Intensity", "Min Intensity"
        });
        panel.add(cbProjection, c);

        // Color Scale
        c.gridx = 0; c.gridy++;
        c.anchor = GridBagConstraints.LINE_END;
        panel.add(new JLabel("Color Scale:"), c);
        c.gridx = 1; c.anchor = GridBagConstraints.LINE_START;
        cbColorScale = new JCheckBox();
        cbColorScale.setSelected(true);
        panel.add(cbColorScale, c);

        // Batch Mode
        c.gridx = 0; c.gridy++;
        c.anchor = GridBagConstraints.LINE_END;
        panel.add(new JLabel("Batch Mode:"), c);
        c.gridx = 1; c.anchor = GridBagConstraints.LINE_START;
        cbBatchMode = new JCheckBox();
        panel.add(cbBatchMode, c);

        // Run Button
        c.gridx = 0; c.gridy++;
        c.gridwidth = 3; c.anchor = GridBagConstraints.CENTER;
        runBtn = new JButton("Run Temporal Color Coding");
        panel.add(runBtn, c);

        runBtn.addActionListener(e -> runWorkflow());

        JScrollPane scroll = new JScrollPane(panel);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        JPanel outer = new JPanel(new BorderLayout());
        outer.add(scroll, BorderLayout.CENTER);
        return outer;
    }

    private void selectImageFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select an Image File");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        int result = chooser.showOpenDialog(owner);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            tfImagePath.setText(file.getAbsolutePath());
            selectedImage = IJ.openImage(file.getAbsolutePath());
            if (selectedImage != null) selectedImage.show();
        }
    }

    private void runWorkflow() {
        ImagePlus imp = IJ.getImage();
        if (imp == null) {
            IJ.error("No image open. Please open an image before running the workflow.");
            return;
        }

        try {
            Params p = new Params();
            p.referenceFrame = Integer.parseInt(tfStartFrame.getText());
            p.referenceFrameEnd = Integer.parseInt(tfEndFrame.getText());
            p.lutName = (String) cbLUT.getSelectedItem();
            p.projectionMethod = (String) cbProjection.getSelectedItem();
            p.createColorScale = cbColorScale.isSelected();
            p.batchMode = cbBatchMode.isSelected();

            TemporalColorOutput output = TemporalColorCoder.run(imp, p);

            dashboard = new TemporalColourDashboardPane(owner);
            dashboard.setOutputs(output.rgbStack, output.colorScale);

            // Add dashboard as a new tab if it doesn't already exist
            String tabName = "Temporal Color: " + imp.getTitle();

            // Add the dashboard if it doesn’t exist
            boolean tabExists = false;
            for (int i = 0; i < mainTabs.getTabCount(); i++) {
                if (mainTabs.getTitleAt(i).equals(tabName)) {
                    tabExists = true;
                    dashboard = (TemporalColourDashboardPane) mainTabs.getComponentAt(i);
                    break;
                }
            }

            if (!tabExists) {
                mainTabs.addTab(tabName, dashboard);
            }

            // Always select AFTER adding
            SwingUtilities.invokeLater(() -> mainTabs.setSelectedComponent(dashboard));

        } catch (NumberFormatException ex) {
            IJ.error("Start/End frames must be integers.");
        } catch (Exception ex) {
            IJ.handleException(ex);
        }
    }
}
