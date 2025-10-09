package UI.panes.SettingPanes;

import Features.Core.Params;
import Features.Tools.AlignStack;
import UI.Handlers.Navigator;
import UI.panes.WorkflowDashboards.AlignStackDashboard;
import UI.util.InputValidation;
import ij.IJ;
import ij.ImagePlus;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * Align Stack pane
 * Lets user configure reference frame, alignment method, save options, and input image.
 */
public class alignStackPane extends JPanel {
    public static final String Name = "Align Stack";

    private final Window owner;

    // --- UI components ---
    private JTextField tfImagePath;
    private JButton btnBrowseImage;
    private JButton btnPreviewImage;

    private JSpinner spReferenceFrame;
    private JCheckBox cbUseSIFT;
    private JCheckBox cbUseTemplateMatching;
    private JTextField tfOutputDir;
    private JButton btnBrowseOutput;
    private JCheckBox cbSaveAlignedStack;

    private JButton runBtn;

    // Dashboard
    private AlignStackDashboard alignStackDashboard;

    public alignStackPane(Navigator navigator, Window owner) {
        super(new BorderLayout(10,10));
        this.owner = owner;
        setBorder(BorderFactory.createEmptyBorder(16,16,16,16));

        // --- Tabs ---
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Settings", buildSettingsTab());

        alignStackDashboard = new AlignStackDashboard();
        tabs.addTab("Alignment Dashboard", alignStackDashboard);

        add(tabs, BorderLayout.CENTER);

        // --- Run button ---
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        runBtn = new JButton("Run Alignment");
        runBtn.addActionListener(e -> onRun(runBtn));
        actions.add(runBtn);
        add(actions, BorderLayout.SOUTH);

        loadDefaults();
    }

    private JPanel buildSettingsTab() {
        JPanel outer = new JPanel(new BorderLayout());
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        // Input stack
        tfImagePath = new JTextField(36);
        btnBrowseImage = new JButton("Browse…");
        btnBrowseImage.addActionListener(e -> chooseFile(tfImagePath, JFileChooser.FILES_ONLY));
        btnPreviewImage = new JButton("Preview");
        btnPreviewImage.addActionListener(e -> previewImage());
        p.add(box("Input stack (.tif)", row(tfImagePath, btnBrowseImage, btnPreviewImage)));

        // Reference frame
        spReferenceFrame = new JSpinner(new SpinnerNumberModel(1, 1, 1000, 1));
        p.add(box("Reference frame", spReferenceFrame));

        // Alignment method
        cbUseSIFT = new JCheckBox("Use SIFT alignment if tissue moves significantly");
        cbUseTemplateMatching = new JCheckBox("Use Template Matching for XY movement");
        p.add(box("Alignment method", column(cbUseSIFT, cbUseTemplateMatching)));

        // Output dir + save
        tfOutputDir = new JTextField(36);
        btnBrowseOutput = new JButton("Browse…");
        btnBrowseOutput.addActionListener(e -> chooseFile(tfOutputDir, JFileChooser.DIRECTORIES_ONLY));
        cbSaveAlignedStack = new JCheckBox("Save aligned stack (recommended)");
        p.add(box("Output location", column(row(tfOutputDir, btnBrowseOutput), cbSaveAlignedStack)));

        JScrollPane scroll = new JScrollPane(p);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        outer.add(scroll, BorderLayout.CENTER);
        return outer;
    }

    // --- Run logic (only sends params to dashboard) ---
    private void onRun(JButton runBtn) {
        runBtn.setEnabled(false);

        boolean ok = InputValidation.validateImageOrShow(this, tfImagePath.getText());
        if (!ok) {
            runBtn.setEnabled(true);
            return;
        }

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            private ImagePlus alignedImage;

            @Override
            protected Void doInBackground() {
                try {
                    Params params = buildParamsFromUI();

                    // Run alignment
                    AlignStack aligner = new AlignStack();
                    AlignStack.AlignResult result = aligner.run(params);

                    // Load the aligned image
                    String outFile = params.outputDir + File.separator +
                            new File(params.imagePath).getName().replace(".tif", "_aligned.tif");
                    alignedImage = IJ.openImage(outFile);

                    // --- Send aligned image and CSV to dashboard ---
                    SwingUtilities.invokeLater(() -> {
                        alignStackDashboard.addAlignedStackWithResults(
                            result.alignedStack,
                            result.resultCSV
                        );
                    });

                } catch (Throwable ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                            owner,
                            "Alignment failed:\n" + ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                    ));
                }
                return null;
            }
        };
        worker.execute();
    }

    private Params buildParamsFromUI() {
        Params p = new Params();
        p.imagePath = tfImagePath.getText();
        p.outputDir = tfOutputDir.getText();
        p.referenceFrame = ((Number) spReferenceFrame.getValue()).intValue();
        p.useSIFT = cbUseSIFT.isSelected();
        p.saveAlignedStack = cbSaveAlignedStack.isSelected();
        p.uiAnchor = SwingUtilities.getWindowAncestor(this);
        p.useSIFT = cbUseSIFT.isSelected();
        p.useTemplateMatching = cbUseTemplateMatching.isSelected();
        p.saveAlignedStack = cbSaveAlignedStack.isSelected();
        return p;
    }

    // --- Utilities for UI ---
    private static JPanel box(String title, JComponent inner) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(inner, BorderLayout.CENTER);
        return panel;
    }

    private static JPanel column(JComponent... comps) {
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        for (JComponent c : comps) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            col.add(c);
            col.add(Box.createVerticalStrut(6));
        }
        return col;
    }

    private static JPanel row(JComponent... comps) {
        JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        for (JComponent c : comps) r.add(c);
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        return r;
    }

    private void chooseFile(JTextField target, int mode) {
        JFileChooser ch = new JFileChooser();
        ch.setFileSelectionMode(mode);
        if (target.getText() != null && !target.getText().isEmpty()) {
            File start = new File(target.getText());
            ch.setCurrentDirectory(start.isDirectory() ? start : start.getParentFile());
        }
        int rv = ch.showOpenDialog(this);
        if (rv == JFileChooser.APPROVE_OPTION) {
            target.setText(ch.getSelectedFile().getAbsolutePath());
        }
    }

    private void previewImage() {
        final String path = (tfImagePath.getText() == null) ? "" : tfImagePath.getText().trim();
        btnPreviewImage.setEnabled(false);
        new SwingWorker<Void,Void>() {
            @Override protected Void doInBackground() {
                try {
                    if (path.isEmpty()) IJ.open(); else IJ.open(path);
                } catch (Throwable ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                            owner,
                            "Preview failed:\n" + ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                            "Preview error",
                            JOptionPane.ERROR_MESSAGE
                    ));
                }
                return null;
            }
            @Override protected void done() { btnPreviewImage.setEnabled(true); }
        }.execute();
    }

    private void loadDefaults() {
        tfImagePath.setText("/path/to/stack.tif");
        spReferenceFrame.setValue(1);
        cbUseSIFT.setSelected(false);
        cbSaveAlignedStack.setSelected(true);
        tfOutputDir.setText(System.getProperty("user.home"));
    }
}
