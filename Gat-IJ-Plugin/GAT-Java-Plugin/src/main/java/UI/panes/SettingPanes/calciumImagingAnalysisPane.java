package UI.panes.SettingPanes;

import Features.Core.Params;
import UI.Handlers.Navigator;
import UI.util.InputValidation;
import UI.panes.WorkflowDashboards.CalciumImagingAnalysisDashboard;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;

public class calciumImagingAnalysisPane extends JPanel {
    public static final String Name = "Calcium Imaging Analysis";

    private final Window owner;
    private JTextField imagePathField;
    private JCheckBox useFF0Box;
    private JCheckBox useStarDistBox;
    private JSpinner cellTypesSpinner;
    private JTextField roiPathField;
    private JTextField cellNamesField;
    private JButton browseButton;
    private JButton runButton;

    private JTabbedPane tabs; 
    private CalciumImagingAnalysisDashboard calciumDashboard; 

    public calciumImagingAnalysisPane(Navigator navigator, Window owner) {
        super(new BorderLayout(10, 10));
        this.owner = owner;
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // --- Tabs ---
        tabs = new JTabbedPane();

        // Settings panel
        JPanel settingsPanel = buildSettingsPanel();
        JScrollPane settingsScroll = new JScrollPane(settingsPanel);
        settingsScroll.getVerticalScrollBar().setUnitIncrement(16);
        tabs.addTab("Settings", settingsScroll);

        add(tabs, BorderLayout.CENTER);

        // --- Run button ---
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        runButton = new JButton("Run Analysis Stepwise");
        runButton.addActionListener(e -> onRun());
        actions.add(runButton);
        add(actions, BorderLayout.SOUTH);
    }

    private JPanel buildSettingsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Calcium Imaging Analysis Settings");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(12));

        // Image path
        imagePathField = new JTextField(30);
        browseButton = new JButton("Browse…");
        browseButton.addActionListener(e -> chooseImageFile());
        panel.add(box("Input Stack", row(new JLabel("Aligned Image Stack:"), imagePathField, browseButton)));

        // F/F0 normalization
        useFF0Box = new JCheckBox("Use F/F₀ Normalisation", true);
        panel.add(box("Normalization", useFF0Box));

        // StarDist segmentation
        useStarDistBox = new JCheckBox("Use StarDist Segmentation", false);
        panel.add(box("Segmentation", useStarDistBox));

        // Cell types
        cellTypesSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 10, 1));
        panel.add(box("Cell Type Settings", row(new JLabel("Number of Cell Types:"), cellTypesSpinner)));

        // Cell names
        cellNamesField = new JTextField(30);
        panel.add(box("Cell Names", row(new JLabel("Cell Names (comma-separated):"), cellNamesField)));

        // ROI path
        roiPathField = new JTextField(30);
        JButton roiBrowse = new JButton("Browse…");
        roiBrowse.addActionListener(e -> chooseROIFile());
        panel.add(box("ROI Input", row(new JLabel("ROI Manager File (optional):"), roiPathField, roiBrowse)));

        return panel;
    }

    private void chooseImageFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select aligned calcium imaging stack");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            imagePathField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void chooseROIFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select ROI Manager ZIP file");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            roiPathField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void onRun() {
        runButton.setEnabled(false);

        if (!InputValidation.validateImageOrShow(this, imagePathField.getText())) {
            runButton.setEnabled(true);
            return;
        }

        Params p = getParams();

        // --- Create dashboard with params ---
        calciumDashboard = new CalciumImagingAnalysisDashboard(p);
        tabs.addTab("Analysis Dashboard", calciumDashboard);
        tabs.setSelectedComponent(calciumDashboard);

        // --- Stepwise execution ---
        // The dashboard should now handle its own buttons for each step
        // User can click each "Step 1", "Step 2", etc., to execute stepwise.
    }

    public Params getParams() {
        Params p = new Params();
        p.imagePath = imagePathField.getText();
        p.useFF0 = useFF0Box.isSelected();
        p.useStarDist = useStarDistBox.isSelected();
        p.cellTypes = (int) cellTypesSpinner.getValue();
        p.roiPath = roiPathField.getText().isEmpty() ? null : roiPathField.getText();
        p.uiAnchor = SwingUtilities.getWindowAncestor(this);

        String namesText = cellNamesField.getText().trim();
        if (!namesText.isEmpty()) {
            String[] names = namesText.split("\\s*,\\s*");
            p.cellNames = new ArrayList<>();
            for (String name : names) p.cellNames.add(name);
        }
        return p;
    }

    // --- UI Helpers ---
    private static JPanel box(String title, JComponent inner) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(inner, BorderLayout.CENTER);
        return panel;
    }

    private static JPanel row(JComponent... comps) {
        JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        for (JComponent c : comps) r.add(c);
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        return r;
    }
}
