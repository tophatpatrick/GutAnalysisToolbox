package UI.panes.Tools;

import Analysis.ChartGenerator;
import Analysis.ParameterGenerator;
import UI.Handlers.Navigator;
import Analysis.SingleCellTypeAnalysis;
import Analysis.TwoCellTypeAnalysis;

import javax.swing.*;
import java.awt.*;
import java.io.File;


public class SpatialAnalysisPane extends JPanel {

    public static final String Name = "Spatial Analysis";

    // UI Components for Single Celltype tab
    private JTextField singleMaxProjPath;
    private JTextField singleRoiCellsPath;
    private JTextField singleRoiGangliaPath;
    private JTextField singleOutputPath;
    private JTextField singleCellTypeName;
    private JSpinner singleExpansionSpinner;
    private JCheckBox singleSaveParametricImage;

    // UI Components for Two Celltype tab
    private JTextField twoMaxProjPath;
    private JTextField twoRoi1Path;
    private JTextField twoRoi2Path;
    private JTextField twoRoiGangliaPath;
    private JTextField twoOutputPath;
    private JTextField twoCellType1Name;
    private JTextField twoCellType2Name;
    private JCheckBox twoAssignPanNeuronal;
    private JRadioButton twoCell1Radio;
    private JRadioButton twoCell2Radio;
    private JSpinner twoExpansionSpinner;
    private JCheckBox twoSaveParametricImage;

    // Store last analysis info for chart generation
    private String lastAnalysisType = null;

    public SpatialAnalysisPane(Navigator navigator, Window owner) {
        super(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Set preferred size for this pane
        setPreferredSize(new Dimension(900, 700));

        // Create tabbed pane
        JTabbedPane tabbedPane = new JTabbedPane();

        // Add Single and Two Celltype analysis tabs
        tabbedPane.addTab("Number of neighbours (One celltype)", createSingleCelltypeTab());
        tabbedPane.addTab("Number of neighbours (Two celltype)", createTwoCelltypeTab());

        add(tabbedPane, BorderLayout.CENTER);

        // Run button
        JButton run = new JButton("Run");
        run.addActionListener(e -> {
            int selectedTab = tabbedPane.getSelectedIndex();
            runAnalysis(selectedTab, owner);
        });

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.add(run);
        add(btnPanel, BorderLayout.SOUTH);
    }

    private void runAnalysis(int tabIndex, Window owner) {
        JDialog progress = new JDialog(owner, "Processing Spatial Analysis…", Dialog.ModalityType.APPLICATION_MODAL);
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        progress.add(bar, BorderLayout.CENTER);
        progress.setSize(400, 80);
        progress.setLocationRelativeTo(owner);

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                if (tabIndex == 0) {
                    // Single celltype analysis
                    runSingleCelltypeAnalysis();
                } else {
                    // Two celltype analysis
                    runTwoCelltypeAnalysis();
                }
                return null;
            }

            @Override
            protected void done() {
                progress.dispose();
                try {
                    get(); // This will throw any exception that occurred
                    // Close ImageJ windows before showing dialog
                    closeImageJWindows();
                    showSuccessDialogWithChartOption(owner);
                    // JOptionPane.showMessageDialog(owner, "Spatial analysis complete!", "Success", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(owner, "Error during analysis: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                }
            }
        };

        worker.execute();
        progress.setVisible(true);
    }

    private void showSuccessDialogWithChartOption(Window owner) {
        JLabel messageLabel = new JLabel("<html>Spatial analysis complete!</html>");

        Object[] options = {"Generate Chart", "OK"};
        JOptionPane pane = new JOptionPane(
                messageLabel,
                JOptionPane.INFORMATION_MESSAGE,
                JOptionPane.YES_NO_OPTION,
                null,
                options,
                options[1]
        );

        JDialog dialog = pane.createDialog(owner, "Success");
        dialog.setModal(true);
        dialog.setResizable(false);

        dialog.setVisible(true);
        Object selectedValue = pane.getValue();

        if ("Generate Chart".equals(selectedValue)) {
            generateCharts(owner);
            // Update label to include chart confirmation
            messageLabel.setText("<html>Spatial analysis complete!<br><span style='color:green;'>Chart Generated!</span></html>");

            // Replace options so only OK remains
            pane.setOptions(new Object[]{"OK"});
            pane.setValue(JOptionPane.UNINITIALIZED_VALUE);

            // Reopen dialog with updated content
            dialog.setVisible(true);
        }

        dialog.dispose();

//        Object[] options = {"Generate Chart", "OK"};
//        int result = JOptionPane.showOptionDialog(
//                owner,
//                "Spatial analysis complete!",
//                "Success",
//                JOptionPane.YES_NO_OPTION,
//                JOptionPane.INFORMATION_MESSAGE,
//                null,
//                options,
//                options[1]
//        );
//
//        if (result == 0) { // Generate Charts clicked
//            generateCharts(owner);
//        }
    }

    private void generateCharts(Window owner) {
        try {
            if (lastAnalysisType.equals("single")) {
                String spatialPath = singleOutputPath.getText().trim() + File.separator + "spatial_analysis" + File.separator;
//                String csvPath = spatialPath + "Neighbour_count_" + lastCellTypeName + ".csv";
                String csvPath = spatialPath + "Neighbour_count_" + singleCellTypeName.getText().trim() + ".csv";

                ChartGenerator.createSingleCellTypeChart(csvPath, singleCellTypeName.getText().trim());
//                JOptionPane.showMessageDialog(owner,
//                        "Chart generated successfully!\nSaved to: " + spatialPath,
//                        "Success", JOptionPane.INFORMATION_MESSAGE);
            } else {
                String spatialPath = twoOutputPath.getText().trim() + File.separator + "spatial_analysis" + File.separator;
                String csvPath = spatialPath + "Neighbour_count_" + twoCellType1Name.getText().trim() + "_"
                        + twoCellType2Name.getText().trim() + ".csv";

                ChartGenerator.createTwoCellTypeChart(csvPath, twoCellType1Name.getText().trim(), twoCellType2Name.getText().trim());
//                JOptionPane.showMessageDialog(owner,
//                        "Chart generated successfully!\nSaved to: " + spatialPath,
//                        "Success", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(owner,
                    "Error generating charts: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void runSingleCelltypeAnalysis() throws Exception {
        // Validate inputs
        if (singleMaxProjPath.getText().trim().isEmpty()) {
            throw new Exception("Please select a maximum projection image");
        }
        if (singleRoiCellsPath.getText().trim().isEmpty()) {
            throw new Exception("Please select ROI manager for cells");
        }
        if (singleOutputPath.getText().trim().isEmpty()) {
            throw new Exception("Please select an output folder");
        }

        System.out.println("singleMaxProjPath = " + singleMaxProjPath.getText().trim());
        System.out.println("singleRoiCellsPath = " + singleRoiCellsPath.getText().trim());
        System.out.println("singleRoiGangliaPath = " + singleRoiGangliaPath.getText().trim());
        System.out.println("singleOutputPath = " + singleOutputPath.getText().trim());
        System.out.println("singleCellTypeName = " + singleCellTypeName.getText().trim());

        // Get parameters
        String maxProj = singleMaxProjPath.getText().trim();
        String roiCells = singleRoiCellsPath.getText().trim();
        String roiGanglia = singleRoiGangliaPath.getText().trim();
        String output = singleOutputPath.getText().trim();
        String cellType = singleCellTypeName.getText().trim();
        double expansion = (Double) singleExpansionSpinner.getValue();
        boolean saveParametric = singleSaveParametricImage.isSelected();

        ParameterGenerator.saveParametersToCSVSingleCellType(output, cellType, expansion, saveParametric, maxProj, roiCells, roiGanglia);

        // Store for chart generation
        lastAnalysisType = "single";

        // Create and execute analysis
        SingleCellTypeAnalysis analysis = new SingleCellTypeAnalysis(
                maxProj, roiCells, roiGanglia, output, cellType, expansion, saveParametric
        );
        analysis.execute();
    }

    private void runTwoCelltypeAnalysis() throws Exception {
        // Validate inputs
        if (twoMaxProjPath.getText().trim().isEmpty()) {
            throw new Exception("Please select a maximum projection image");
        }
        if (twoRoi1Path.getText().trim().isEmpty()) {
            throw new Exception("Please select ROI manager for cell 1");
        }
        if (twoRoi2Path.getText().trim().isEmpty()) {
            throw new Exception("Please select ROI manager for cell 2");
        }
        if (twoOutputPath.getText().trim().isEmpty()) {
            throw new Exception("Please select an output folder");
        }

        System.out.println("twoMaxProjPath = " + twoMaxProjPath.getText().trim());
        System.out.println("twoRoi1Path = " + twoRoi1Path.getText().trim());
        System.out.println("twoRoi2Path = " + twoRoi2Path.getText().trim());
        System.out.println("twoRoiGangliaPath = " + twoRoiGangliaPath.getText().trim());
        System.out.println("twoOutputPath = " + twoOutputPath.getText().trim());
        System.out.println("twoCellType1Name = " + twoCellType1Name.getText().trim());
        System.out.println("twoCellType2Name = " + twoCellType2Name.getText().trim());

        // Get parameters
        String maxProj = twoMaxProjPath.getText().trim();
        String cellType1 = twoCellType1Name.getText().trim();
        String roi1 = twoRoi1Path.getText().trim();
        String cellType2 = twoCellType2Name.getText().trim();
        String roi2 = twoRoi2Path.getText().trim();
        String roiGanglia = twoRoiGangliaPath.getText().trim();
        String output = twoOutputPath.getText().trim();
        double expansion = (Double) twoExpansionSpinner.getValue();
        boolean saveParametric = twoSaveParametricImage.isSelected();

        ParameterGenerator.saveParametersToCSVTwoCellType(output, cellType1, cellType2, expansion, saveParametric, maxProj, roi1, roi2, roiGanglia);

        // Store for chart generation
        lastAnalysisType = "two";

        // Create and execute analysis
        TwoCellTypeAnalysis analysis = new TwoCellTypeAnalysis(
                maxProj, cellType1, roi1, cellType2, roi2, roiGanglia, output,
                expansion, saveParametric
        );

        analysis.execute();
    }

    private JPanel createSingleCelltypeTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // Image selection section
        panel.add(Box.createVerticalStrut(10));

        JPanel labelWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JLabel imageLabel = new JLabel("Image and ROI Selection");
        imageLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        labelWrapper.add(imageLabel);
        panel.add(labelWrapper);

        // Maximum projection image selection
        JPanel maxProjRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        maxProjRow.add(new JLabel("Select the maximum projection image:"));
        singleMaxProjPath = new JTextField(25);
        JButton browseProjBtn = new JButton("Browse");

        browseProjBtn.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            int result = fileChooser.showOpenDialog(panel);
            if (result == JFileChooser.APPROVE_OPTION) {
                singleMaxProjPath.setText(fileChooser.getSelectedFile().getAbsolutePath());
            }
        });

        maxProjRow.add(singleMaxProjPath);
        maxProjRow.add(browseProjBtn);
        panel.add(maxProjRow);

        // ROI manager for cells selection
        JPanel roiCellsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        roiCellsRow.add(new JLabel("Select roi manager for cells:"));
        singleRoiCellsPath = new JTextField(25);
        JButton browseRoiBtn = new JButton("Browse");

        browseRoiBtn.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            int result = fileChooser.showOpenDialog(panel);
            if (result == JFileChooser.APPROVE_OPTION) {
                singleRoiCellsPath.setText(fileChooser.getSelectedFile().getAbsolutePath());
            }
        });

        roiCellsRow.add(singleRoiCellsPath);
        roiCellsRow.add(browseRoiBtn);
        panel.add(roiCellsRow);

        // ROI manager for ganglia selection
        JPanel roiGangliaRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        roiGangliaRow.add(new JLabel("Select roi manager for ganglia (Enter NA if none):"));
        singleRoiGangliaPath = new JTextField("NA", 20);
        JButton browseGangliaBtn = new JButton("Browse");

        browseGangliaBtn.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            int result = fileChooser.showOpenDialog(panel);
            if (result == JFileChooser.APPROVE_OPTION) {
                singleRoiGangliaPath.setText(fileChooser.getSelectedFile().getAbsolutePath());
            }
        });

        roiGangliaRow.add(singleRoiGangliaPath);
        roiGangliaRow.add(browseGangliaBtn);
        panel.add(roiGangliaRow);

        // Output folder selection
        JPanel outputRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        outputRow.add(new JLabel("Select Output Folder:"));
        singleOutputPath = new JTextField(25);
        JButton browseOutputBtn = new JButton("Browse");

        browseOutputBtn.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int result = fileChooser.showOpenDialog(panel);
            if (result == JFileChooser.APPROVE_OPTION) {
                singleOutputPath.setText(fileChooser.getSelectedFile().getAbsolutePath());
            }
        });

        outputRow.add(singleOutputPath);
        outputRow.add(browseOutputBtn);
        panel.add(outputRow);

        panel.add(Box.createVerticalStrut(15));

        // Parameters section
        JPanel paramWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JLabel paramLabel = new JLabel("Analysis Parameters");
        paramLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        paramWrapper.add(paramLabel);
        panel.add(paramWrapper);

        // Cell type name
        JPanel cellTypeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        cellTypeRow.add(new JLabel("Name of celltype:"));
        singleCellTypeName = new JTextField("Hu", 10);
        cellTypeRow.add(singleCellTypeName);
        panel.add(cellTypeRow);

        // Cell expansion parameter
        JPanel expansionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        expansionRow.add(new JLabel("Cell expansion (microns):"));
        singleExpansionSpinner = new JSpinner(new SpinnerNumberModel(6.5, 1.0, 15.0, 0.5));
        expansionRow.add(singleExpansionSpinner);
        panel.add(expansionRow);

        // Add explanatory text
        JPanel hintRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JLabel hintLabel = new JLabel("<html>Expand cells by a certain distance so that they touch each other<br>and then count immediate neighbours (6.5 micron is default)</html>");
        hintLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
        hintLabel.setForeground(Color.GRAY);
        hintRow.add(hintLabel);
        panel.add(hintRow);

        // Save parametric image option
        singleSaveParametricImage = new JCheckBox("Save parametric image");
        singleSaveParametricImage.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(singleSaveParametricImage);

        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private JPanel createTwoCelltypeTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // Image selection section
        panel.add(Box.createVerticalStrut(10));

        JPanel labelWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JLabel imageLabel = new JLabel("Image and ROI Selection");
        imageLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        labelWrapper.add(imageLabel);
        panel.add(labelWrapper);

        // Maximum projection image selection
        JPanel maxProjRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        maxProjRow.add(new JLabel("Select the maximum projection image:"));
        twoMaxProjPath = new JTextField(25);
        JButton browseProjBtn = new JButton("Browse");

        browseProjBtn.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            int result = fileChooser.showOpenDialog(panel);
            if (result == JFileChooser.APPROVE_OPTION) {
                twoMaxProjPath.setText(fileChooser.getSelectedFile().getAbsolutePath());
            }
        });

        maxProjRow.add(twoMaxProjPath);
        maxProjRow.add(browseProjBtn);
        panel.add(maxProjRow);

        // Cell type 1 ROI selection
        JPanel cellType1Row = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        cellType1Row.add(new JLabel("Select roi manager for cell 1:"));
        twoRoi1Path = new JTextField(25);
        JButton browseRoi1Btn = new JButton("Browse");

        browseRoi1Btn.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            int result = fileChooser.showOpenDialog(panel);
            if (result == JFileChooser.APPROVE_OPTION) {
                twoRoi1Path.setText(fileChooser.getSelectedFile().getAbsolutePath());
            }
        });

        cellType1Row.add(twoRoi1Path);
        cellType1Row.add(browseRoi1Btn);
        panel.add(cellType1Row);

        // Cell type 2 ROI selection
        JPanel cellType2Row = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        cellType2Row.add(new JLabel("Select roi manager for cell 2:"));
        twoRoi2Path = new JTextField(25);
        JButton browseRoi2Btn = new JButton("Browse");

        browseRoi2Btn.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            int result = fileChooser.showOpenDialog(panel);
            if (result == JFileChooser.APPROVE_OPTION) {
                twoRoi2Path.setText(fileChooser.getSelectedFile().getAbsolutePath());
            }
        });

        cellType2Row.add(twoRoi2Path);
        cellType2Row.add(browseRoi2Btn);
        panel.add(cellType2Row);

        // ROI manager for ganglia selection
        JPanel roiGangliaRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        roiGangliaRow.add(new JLabel("Select roi manager for ganglia (Enter NA if none):"));
        twoRoiGangliaPath = new JTextField("NA", 20);
        JButton browseGangliaBtn = new JButton("Browse");

        browseGangliaBtn.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            int result = fileChooser.showOpenDialog(panel);
            if (result == JFileChooser.APPROVE_OPTION) {
                twoRoiGangliaPath.setText(fileChooser.getSelectedFile().getAbsolutePath());
            }
        });

        roiGangliaRow.add(twoRoiGangliaPath);
        roiGangliaRow.add(browseGangliaBtn);
        panel.add(roiGangliaRow);

        // Output folder selection
        JPanel outputRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        outputRow.add(new JLabel("Select Output Folder:"));
        twoOutputPath = new JTextField(25);
        JButton browseOutputBtn = new JButton("Browse");

        browseOutputBtn.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int result = fileChooser.showOpenDialog(panel);
            if (result == JFileChooser.APPROVE_OPTION) {
                twoOutputPath.setText(fileChooser.getSelectedFile().getAbsolutePath());
            }
        });

        outputRow.add(twoOutputPath);
        outputRow.add(browseOutputBtn);
        panel.add(outputRow);

        panel.add(Box.createVerticalStrut(15));

        // Parameters section
        JPanel paramWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JLabel paramLabel = new JLabel("Analysis Parameters");
        paramLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        paramWrapper.add(paramLabel);
        panel.add(paramWrapper);

        // Cell type names
        JPanel cellName1Row = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        cellName1Row.add(new JLabel("Name of celltype 1:"));
        twoCellType1Name = new JTextField("cell_1", 10);
        cellName1Row.add(twoCellType1Name);
        panel.add(cellName1Row);

        JPanel cellName2Row = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        cellName2Row.add(new JLabel("Name of celltype 2:"));
        twoCellType2Name = new JTextField("cell_2", 10);
        cellName2Row.add(twoCellType2Name);
        panel.add(cellName2Row);

        panel.add(Box.createVerticalStrut(10));

        // Cell expansion parameter
        JPanel expansionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        expansionRow.add(new JLabel("Cell expansion distance for cells (microns):"));
        twoExpansionSpinner = new JSpinner(new SpinnerNumberModel(6.5, 1.0, 15.0, 0.5));
        expansionRow.add(twoExpansionSpinner);
        panel.add(expansionRow);

        // Add explanatory text
        JPanel hintRow2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JLabel hintLabel2 = new JLabel("<html>Expand cells by a certain distance so that they touch each other<br>and then count immediate neighbours (6.5 micron is default)</html>");
        hintLabel2.setFont(new Font("SansSerif", Font.ITALIC, 12));
        hintLabel2.setForeground(Color.GRAY);
        hintRow2.add(hintLabel2);
        panel.add(hintRow2);

        // Save parametric image option
        twoSaveParametricImage = new JCheckBox("Save parametric image");
        twoSaveParametricImage.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(twoSaveParametricImage);

        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private void closeImageJWindows() {

        // Close ROI Manager
        ij.plugin.frame.RoiManager roiManager = ij.plugin.frame.RoiManager.getInstance();
        if (roiManager != null) {
            roiManager.close();
        }

        // Close Results window
//        ij.IJ.run("Clear Results");
//        ij.measure.ResultsTable rt = ij.measure.ResultsTable.getResultsTable();
//        if (rt != null) {
//            rt.reset();
//        }
        ij.text.TextWindow resultWindow =  (ij.text.TextWindow) ij.WindowManager.getWindow("Results");
        if (resultWindow != null) {
            resultWindow.close();
        }

        // Close Log window
        ij.text.TextWindow logWindow = (ij.text.TextWindow) ij.WindowManager.getWindow("Log");
        if (logWindow != null) {
            logWindow.close();
        }

        // Close Console if it exists
        ij.text.TextWindow consoleWindow = (ij.text.TextWindow) ij.WindowManager.getWindow("Console");
        if (consoleWindow != null) {
            consoleWindow.close();
        }
    }
}
