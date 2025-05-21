package UI.panes.SettingPanes;

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
                    navigator.show(AnalyseNeuronDashboard.Name);
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
        panel.add(Box.createVerticalStrut(10));

        panel.add(new JLabel("Image Selection"));
        panel.add(Box.createVerticalStrut(5));

        // Image path row with inline Browse and Preview buttons
        JPanel imageRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        imageRow.add(new JLabel("Choose the image to segment:"));

        JTextField imagePath = new JTextField(30);
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

        panel.add(Box.createVerticalStrut(10));

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
        panel.add(new JLabel("Determine Ganglia Outline"));

        JCheckBox cellCounts = new JCheckBox("Cell counts per ganglia");
        panel.add(cellCounts);

        panel.add(new JLabel("Ganglia detection"));

        ButtonGroup detectionGroup = new ButtonGroup();
        JRadioButton deepImageJ = new JRadioButton("DeepImageJ");
        JRadioButton defineHu = new JRadioButton("Define ganglia using Hu");
        JRadioButton manual = new JRadioButton("Manually Draw ganglia");

        detectionGroup.add(deepImageJ);
        detectionGroup.add(defineHu);
        detectionGroup.add(manual);

        panel.add(deepImageJ);
        panel.add(defineHu);
        panel.add(manual);

        JPanel channelRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        channelRow.add(new JLabel("Enter channel number for segmenting ganglia:"));
        JTextField channelInput = new JTextField("2", 3);
        channelRow.add(channelInput);
        panel.add(channelRow);

        JCheckBox spatialAnalysis = new JCheckBox("Perform Spatial Analysis");
        JCheckBox fineTune = new JCheckBox("Finetune Detection Parameters");
        JCheckBox contribute = new JCheckBox("Contribute to GAT");

        panel.add(spatialAnalysis);
        panel.add(fineTune);
        panel.add(contribute);

        return panel;
    }
}

//package UI.panes.SettingPanes;
//
//import UI.Handlers.Navigator;
//
//import javax.swing.*;
//import java.awt.*;
//import UI.panes.WorkflowDashboards.*;
//
//public class NeuronWorkflowPane extends JPanel{
//
//    public static final String Name = "Neuron Workflow";
//
//    public NeuronWorkflowPane(Navigator navigator, Window owner){
//        super(new BorderLayout(10,10));
//        setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
//
//
//        JLabel lbl = new JLabel(
//                "<html><h2>Neuron Analysis</h2>"
//                        + "<p>Configure parameters here…</p></html>",
//                SwingConstants.CENTER
//        );
//        add(lbl, BorderLayout.CENTER);
//
//        JButton run = new JButton("Run Analysis");
//
//        run.addActionListener(e -> {
//            // 1) build modal progress dialog
//            JDialog progress = new JDialog(owner, "Processing…", Dialog.ModalityType.APPLICATION_MODAL);
//            JProgressBar bar = new JProgressBar();
//            bar.setIndeterminate(true);
//            progress.add(bar, BorderLayout.CENTER);
//            progress.setSize(300,80);
//            progress.setLocationRelativeTo(owner);
//
//            // 2) SwingWorker to simulate the analysis
//            SwingWorker<Void,Void> worker = new SwingWorker<Void,Void>() {
//                @Override
//                protected Void doInBackground() throws Exception {
//                    Thread.sleep(2000);  // simulate 2 s work
//                    return null;
//                }
//                @Override
//                protected void done() {
//                    progress.dispose();
//                    // switch to your dashboard pane
//                    navigator.show(AnalyseNeuronDashboard.Name);
//                }
//            };
//            worker.execute();
//            progress.setVisible(true);
//        });
//
//        JPanel btnPanel = new JPanel();
//        btnPanel.add(run);
//        add(btnPanel, BorderLayout.SOUTH);
//
//
//    }
//}
