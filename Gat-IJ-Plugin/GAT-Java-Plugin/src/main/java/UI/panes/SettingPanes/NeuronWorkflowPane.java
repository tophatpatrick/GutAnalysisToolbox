package UI.panes.SettingPanes;

import UI.Handlers.AnalysisWindow;
import UI.Handlers.Navigator;
import UI.panes.WorkflowDashboards.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class NeuronWorkflowPane extends JPanel {

    public static final String Name = "Neuron Workflow";

    public NeuronWorkflowPane(Navigator navigator, Window owner) {
        super(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Create tabbed pane
        JTabbedPane tabbedPane = new JTabbedPane();

        // Add Basic and Advanced panels
        tabbedPane.addTab("Basic", createBasicTab());
        tabbedPane.addTab("Advanced", createAdvancedTab());

        add(tabbedPane, BorderLayout.CENTER);

        // Run button
        JButton run = new JButton("Run");
        run.addActionListener(e -> {
            JDialog progress = new JDialog(owner, "Processing…", Dialog.ModalityType.APPLICATION_MODAL);
            JProgressBar bar = new JProgressBar();
            bar.setIndeterminate(true);
            progress.add(bar, BorderLayout.CENTER);
            progress.setSize(300, 80);
            progress.setLocationRelativeTo(owner);

            SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    Thread.sleep(2000);
                    return null;
                }

                @Override
                protected void done() {
                    progress.dispose();


                    progress.dispose();

                    AnalyseNeuronDashboard dash = new AnalyseNeuronDashboard(navigator);

                    AnalysisWindow popup = AnalysisWindow.get(owner);

                    // 4. Add a new tab
                    popup.addTab(AnalyseNeuronDashboard.Name, dash, 1000, 1000);
                }
            };

            worker.execute();
            progress.setVisible(true);
        });

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.add(run);
        add(btnPanel, BorderLayout.SOUTH);
    }

    private JPanel createBasicTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
//        panel.add(Box.createVerticalStrut(1));

//        JLabel imageLabel = new JLabel("Image Selection");
//        imageLabel.setFont(new Font("SansSerif", Font.BOLD, 16));  // Bold, size 16
//        imageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
//        panel.add(imageLabel);
        JPanel labelWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JLabel imageLabel = new JLabel("Image Selection");
        imageLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        labelWrapper.add(imageLabel);
        panel.add(labelWrapper);
//        panel.add(Box.createVerticalStrut(1));

        // Image path row with inline Browse and Preview buttons
        JPanel imageRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        imageRow.add(new JLabel("Choose the image to segment:"));

        JTextField imagePath = new JTextField(25);
        imageRow.add(imagePath);

        JButton browse = new JButton("Browse");
        JButton preview = new JButton("Show Preview");
        preview.setEnabled(false); // Disabled until image is selected
        JCheckBox imageOpen = new JCheckBox("Image already open");

        imageRow.add(browse);
        imageRow.add(preview);
        imageRow.add(imageOpen);

        panel.add(imageRow);

        // Handle Browse click
        browse.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();

            // Set a custom file filter
            fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
                @Override
                public boolean accept(File f) {
                    return f.isDirectory() || f.getName().toLowerCase().endsWith(".tif") || f.getName().toLowerCase().endsWith(".tiff");
                }

                @Override
                public String getDescription() {
                    return "TIFF Images (*.tif, *.tiff)";
                }
            });


            int result = fileChooser.showOpenDialog(panel);
            if (result == JFileChooser.APPROVE_OPTION) {
                String selectedPath = fileChooser.getSelectedFile().getAbsolutePath();
                imagePath.setText(selectedPath);
                preview.setEnabled(true);
            }
        });

        // Handle Show Preview click
        preview.addActionListener(e -> {
            String path = imagePath.getText();
            if (!path.isEmpty()) {
                JFrame previewFrame = new JFrame("Image Preview");
                previewFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                previewFrame.setSize(600, 600);

                try {
                    BufferedImage original = ImageIO.read(new File(path));
                    int maxW = 550;
                    int maxH = 500;

                    // Calculate scaled dimensions
                    double widthRatio = (double) maxW / original.getWidth();
                    double heightRatio = (double) maxH / original.getHeight();
                    double scale = Math.min(widthRatio, heightRatio);

                    int newW = (int) (original.getWidth() * scale);
                    int newH = (int) (original.getHeight() * scale);

                    Image scaledImage = original.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
                    ImageIcon icon = new ImageIcon(scaledImage);

                    JLabel imgLabel = new JLabel(icon);
                    JScrollPane scrollPane = new JScrollPane(imgLabel);
                    previewFrame.add(scrollPane);

                    previewFrame.setLocationRelativeTo(null);
                    previewFrame.setVisible(true);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(panel, "Failed to load image.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

//        panel.add(Box.createVerticalStrut(1));

        // Channel hue selection
        JPanel hueRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        hueRow.add(new JLabel("Select Channel Hue"));
        JTextField hueField = new JTextField("3", 3);
        hueRow.add(hueField);
        panel.add(hueRow);

        return panel;
    }

    private JPanel createAdvancedTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        panel.add(Box.createVerticalStrut(10));

        JLabel titleLabel = new JLabel("Determine Ganglia Outline");
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(titleLabel);

        JCheckBox cellCounts = new JCheckBox("Cell counts per ganglia");
        cellCounts.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(cellCounts);

        JLabel detectionLabel = new JLabel("Ganglia detection");
        detectionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(detectionLabel);

        // Radio buttons in a single row
        ButtonGroup detectionGroup = new ButtonGroup();
        JRadioButton deepImageJ = new JRadioButton("DeepImageJ");
        JRadioButton defineHu = new JRadioButton("Define ganglia using Hu");
        JRadioButton manual = new JRadioButton("Manually Draw ganglia");

        detectionGroup.add(deepImageJ);
        detectionGroup.add(defineHu);
        detectionGroup.add(manual);

        JPanel radioRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        radioRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        radioRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        radioRow.add(deepImageJ);
        radioRow.add(defineHu);
        radioRow.add(manual);
        panel.add(radioRow);

        // Channel input row
        JPanel channelRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        channelRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        channelRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        channelRow.add(new JLabel("Enter channel number for segmenting ganglia:"));
        JTextField channelInput = new JTextField("2", 3);
        channelRow.add(channelInput);
        panel.add(channelRow);

        // Checkboxes
        JCheckBox spatialAnalysis = new JCheckBox("Perform Spatial Analysis");
        JCheckBox fineTune = new JCheckBox("Finetune Detection Parameters");
        JCheckBox contribute = new JCheckBox("Contribute to GAT");

        spatialAnalysis.setAlignmentX(Component.LEFT_ALIGNMENT);
        fineTune.setAlignmentX(Component.LEFT_ALIGNMENT);
        contribute.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(spatialAnalysis);
        panel.add(fineTune);
        panel.add(contribute);

        return panel;
    }
}
