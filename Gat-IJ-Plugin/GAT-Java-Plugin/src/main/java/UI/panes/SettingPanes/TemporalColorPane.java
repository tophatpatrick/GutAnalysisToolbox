package UI.panes.SettingPanes;

import Features.Core.Params;
import Analysis.TemporalColorCoder;
import ij.ImagePlus;
import ij.IJ;
import UI.Handlers.Navigator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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

    private Params params;
    private ImagePlus selectedImage;

    public TemporalColorPane(Navigator navigator, Window owner) {
        super(new BorderLayout(10,10));
        this.owner = owner;
        initUI();
    }

    private void initUI() {
        setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.gridx = 0; c.gridy = 0; c.anchor = GridBagConstraints.LINE_END;

        // --- Image Selection ---
        add(new JLabel("Select Image:"), c);
        c.gridx = 1; c.anchor = GridBagConstraints.LINE_START;
        tfImagePath = new JTextField(20);
        tfImagePath.setEditable(false);
        add(tfImagePath, c);

        c.gridx = 2;
        selectImageBtn = new JButton("Browse...");
        add(selectImageBtn, c);

        selectImageBtn.addActionListener(e -> selectImageFile());

        // --- Start Frame ---
        c.gridx = 0; c.gridy++;
        c.anchor = GridBagConstraints.LINE_END;
        add(new JLabel("Start Frame:"), c);
        c.gridx = 1; c.anchor = GridBagConstraints.LINE_START;
        tfStartFrame = new JTextField("1", 5);
        add(tfStartFrame, c);

        // --- End Frame ---
        c.gridx = 0; c.gridy++;
        c.anchor = GridBagConstraints.LINE_END;
        add(new JLabel("End Frame:"), c);
        c.gridx = 1; c.anchor = GridBagConstraints.LINE_START;
        tfEndFrame = new JTextField("10", 5);
        add(tfEndFrame, c);

        // --- LUT ---
        c.gridx = 0; c.gridy++;
        c.anchor = GridBagConstraints.LINE_END;
        add(new JLabel("LUT:"), c);
        c.gridx = 1; c.anchor = GridBagConstraints.LINE_START;
        cbLUT = new JComboBox<>(new String[]{"Fire", "Ice", "Green", "Red"});
        add(cbLUT, c);

        // --- Projection ---
        c.gridx = 0; c.gridy++;
        c.anchor = GridBagConstraints.LINE_END;
        add(new JLabel("Projection:"), c);
        c.gridx = 1; c.anchor = GridBagConstraints.LINE_START;
        cbProjection = new JComboBox<>(new String[]{
                "Max Intensity", "Average Intensity", "Min Intensity"
        });
        add(cbProjection, c);

        // --- Color Scale ---
        c.gridx = 0; c.gridy++;
        c.anchor = GridBagConstraints.LINE_END;
        add(new JLabel("Color Scale:"), c);
        c.gridx = 1; c.anchor = GridBagConstraints.LINE_START;
        cbColorScale = new JCheckBox();
        cbColorScale.setSelected(true);
        add(cbColorScale, c);

        // --- Batch Mode ---
        c.gridx = 0; c.gridy++;
        c.anchor = GridBagConstraints.LINE_END;
        add(new JLabel("Batch Mode:"), c);
        c.gridx = 1; c.anchor = GridBagConstraints.LINE_START;
        cbBatchMode = new JCheckBox();
        add(cbBatchMode, c);

        // --- Run Button ---
        c.gridx = 0; c.gridy++;
        c.gridwidth = 3; c.anchor = GridBagConstraints.CENTER;
        runBtn = new JButton("Run Temporal Color Coding");
        add(runBtn, c);

        runBtn.addActionListener(e -> runWorkflow());
    }

    private void selectImageFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select an Image File");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setAcceptAllFileFilterUsed(true);

        int result = chooser.showOpenDialog(owner);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();
            String path = selectedFile.getAbsolutePath();
            tfImagePath.setText(path);

            // --- Verify file exists before trying to open ---
            if (!selectedFile.exists()) {
                JOptionPane.showMessageDialog(owner,
                        "The selected file does not exist:\n" + path,
                        "File Not Found",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            try {
                selectedImage = IJ.openImage(path);
                if (selectedImage == null) {
                    JOptionPane.showMessageDialog(owner,
                            "Failed to open the selected file.\n\nSupported formats include:\n" +
                                    "TIF, PNG, JPG, BMP, AVI, or multi-frame stacks.",
                            "Unsupported Format",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // Optional: show preview in ImageJ
                selectedImage.show();

            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(owner,
                        "Error opening file:\n" + ex.getMessage(),
                        "Open Image Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
}


    private void runWorkflow() {
        try {
            ImagePlus imp = IJ.getImage();
            if (imp == null) {
                IJ.error("No image open. Please open an image before running the workflow.");
                return;
            }

            // === Safely get image file path using FileInfo ===
            String path = null;
            if (imp.getOriginalFileInfo() != null) {
                path = imp.getOriginalFileInfo().directory + imp.getOriginalFileInfo().fileName;
            }

            // If FileInfo is null, prompt user to select a file
            if (path == null) {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Select the image file associated with this stack");
                chooser.setCurrentDirectory(new java.io.File(System.getProperty("user.home")));
                int result = chooser.showOpenDialog(this);
                if (result == JFileChooser.APPROVE_OPTION) {
                    path = chooser.getSelectedFile().getAbsolutePath();
                } else {
                    IJ.error("No image file selected.");
                    return;
                }
            }

            // Log the selected image and recommended formats
            IJ.log("Selected image: " + path);
            IJ.log("Recommended formats: .tif, .tiff, .png, or .jpg for best compatibility.");

            // === Prepare Params from UI inputs ===
            Params p = new Params();
            p.referenceFrame = Integer.parseInt(tfStartFrame.getText());
            p.referenceFrameEnd = Integer.parseInt(tfEndFrame.getText());
            p.lutName = (String) cbLUT.getSelectedItem();
            p.projectionMethod = (String) cbProjection.getSelectedItem();
            p.createColorScale = cbColorScale.isSelected();
            p.batchMode = cbBatchMode.isSelected();

            // Run TemporalColorCoder
            Analysis.TemporalColorCoder.run(imp, p);

        } catch (NumberFormatException ex) {
            IJ.error("Start/End frames must be integers.");
        } catch (Exception ex) {
            IJ.handleException(ex);
        }
    }

}
