package UI.panes.SettingPanes;

import Features.AnalyseWorkflows.NeuronsHuPipeline;
import Features.Core.Params;
import UI.Handlers.Navigator;
import UI.util.InputValidation;
import ij.IJ;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.Locale;
import java.util.Objects;

/**
 * Neuron Workflow pane with Basic & Advanced tabs.
 * - No modal progress window
 * - Builds Params directly from the UI
 * - Runs NeuronsHuPipeline on a SwingWorker
 */
public class NeuronWorkflowPane extends JPanel {
    public static final String Name = "Neuron Workflow";

    private final Window owner;

    // Basic tab fields
    private JTextField tfImagePath;
    private JButton    btnBrowseImage;
    private JButton    btnPreviewImage;

    private JSpinner   spHuChannel;

    private JTextField tfModelZip;
    private JButton    btnBrowseModel;

    private JPanel pnlGangliaModel;
    private JPanel pnlCustomZipBasic;

    private JTextField tfCustomGangliaZip;
    private JButton btnBrowseCustomRoiZip;

    private JButton runBtn;

    private JCheckBox  cbRescaleToTrainingPx;
    private JSpinner   spTrainingPixelSizeUm;   // double
    private JSpinner   spProbThresh;            // 0..1
    private JSpinner   spNmsThresh;             // 0..1
    private JCheckBox  cbSaveFlattenedOverlay;
    private JTextField tfOutputDir;
    private JButton    btnBrowseOutput;

    private JCheckBox  cbGangliaAnalysis;       // enable/disable ganglia block
    private JComboBox<Params.GangliaMode> cbGangliaMode;

    // Advanced tab fields
    private JCheckBox  cbRequireMicronUnits;
    private JSpinner   spNeuronSegLowerLimitUm; // double
    private JSpinner   spNeuronSegMinMicron;    // double (kept for parity)

    private JSpinner   spTrainingRescaleFactor; // double

    private JSpinner   spGangliaChannel;        // int (1-based)
    private JTextField tfGangliaModelFolder;    // model folder name (under <Fiji>/models)
    private JButton    btnBrowseGangliaModelFolder;

    private JSpinner   spHuDilationMicron;      // double
    private JSpinner   spGangliaProbThresh01;   // double 0..1
    private JSpinner   spGangliaMinAreaUm2;     // double
    private JSpinner   spGangliaOpenIterations; // int
    private JCheckBox  cbGangliaInteractiveReview;

    public NeuronWorkflowPane(Navigator navigator, Window owner) {
        super(new BorderLayout(10,10));
        this.owner     = owner;
        setBorder(BorderFactory.createEmptyBorder(16,16,16,16));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Basic", buildBasicTab());
        tabs.addTab("Advanced", buildAdvancedTab());
        add(tabs, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton runBtn = new JButton("Run Analysis");
        runBtn.addActionListener(e -> onRun(runBtn));
        actions.add(runBtn);
        add(actions, BorderLayout.SOUTH);

        loadDefaults();


        cbGangliaMode.addActionListener(e -> { updateGangliaUI(); updateRunButtonEnabled(); });
        cbGangliaAnalysis.addActionListener(e -> { updateGangliaUI(); updateRunButtonEnabled(); });

        if (tfCustomGangliaZip != null) {
            tfCustomGangliaZip.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { updateRunButtonEnabled(); }
                @Override public void removeUpdate(DocumentEvent e) { updateRunButtonEnabled(); }
                @Override public void changedUpdate(DocumentEvent e) { updateRunButtonEnabled(); }
            });
        }

        updateGangliaUI();
        updateRunButtonEnabled();
    }

    // ---------------- UI builders ----------------

    private JPanel buildBasicTab() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        // Image
        tfImagePath = new JTextField(36);
        btnBrowseImage = new JButton("Browse…");
        btnBrowseImage.addActionListener(e -> chooseFile(tfImagePath, JFileChooser.FILES_ONLY));

        btnPreviewImage = new JButton("Preview");
        btnPreviewImage.addActionListener(e -> previewImage());

        p.add(boxWith("Input image (.tif, .lif, etc.)", row(tfImagePath, btnBrowseImage, btnPreviewImage)));


        // Hu channel
        spGangliaChannel     = new JSpinner(new SpinnerNumberModel(2, 1, 16, 1));
        spHuChannel = new JSpinner(new SpinnerNumberModel(3, 1, 16, 1));
        p.add(boxWith("Image Channels", grid2(
                new JLabel("Hu Channel:"),      spHuChannel,
                new JLabel("Ganglia Channel"),  spGangliaChannel
        )));

        // Output dir
        tfOutputDir = new JTextField(36);
        btnBrowseOutput = new JButton("Browse…");
        btnBrowseOutput.addActionListener(e -> chooseFile(tfOutputDir, JFileChooser.DIRECTORIES_ONLY));
        p.add(boxWith("Output directory (optional; default: Analysis/<basename>)", row(tfOutputDir, btnBrowseOutput)));


        // EDF / overlay / ganglia toggle
        cbSaveFlattenedOverlay = new JCheckBox("Save flattened overlay");
        cbGangliaAnalysis = new JCheckBox("Run ganglia analysis");
        cbGangliaMode = new JComboBox<>(Params.GangliaMode.values());
        p.add(boxWith("Options", column(
                cbSaveFlattenedOverlay,
                row(new JLabel("Ganglia mode:"), cbGangliaMode),
                cbGangliaAnalysis
        )));

        p.add(Box.createVerticalGlue());

        tfCustomGangliaZip = new JTextField(28);
        btnBrowseCustomRoiZip = new JButton("Browse…");
        btnBrowseCustomRoiZip.addActionListener(e -> chooseFile(tfCustomGangliaZip, JFileChooser.FILES_ONLY));

        // Keep a handle so we can show/hide from updateGangliaUI()
        pnlCustomZipBasic = boxWith("Import ganglia ROIs (.zip)",
                row(tfCustomGangliaZip, btnBrowseCustomRoiZip));
        p.add(pnlCustomZipBasic);
        return p;
    }

    private JPanel buildAdvancedTab() {

        JPanel outer = new JPanel(new BorderLayout());

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        cbRequireMicronUnits     = new JCheckBox("Require microns calibration");
        spNeuronSegLowerLimitUm  = new JSpinner(new SpinnerNumberModel(70.0, 0.0, 10000.0, 1.0));
        spNeuronSegMinMicron     = new JSpinner(new SpinnerNumberModel(70.0, 0.0, 10000.0, 1.0));
        spTrainingRescaleFactor  = new JSpinner(new SpinnerNumberModel(1.0, 0.01, 100.0, 0.01));

        p.add(boxWith("Calibration & size filtering", column(
                cbRequireMicronUnits,
                grid2(new JLabel("Neuron seg lower limit (µm):"), spNeuronSegLowerLimitUm,
                        new JLabel("Neuron seg min (µm)       :"),   spNeuronSegMinMicron,
                        new JLabel("Training rescale factor:"), spTrainingRescaleFactor)
        )));

        // StarDist model (.zip)
        tfModelZip = new JTextField(36);
        btnBrowseModel = new JButton("Browse…");
        btnBrowseModel.addActionListener(e -> chooseFile(tfModelZip, JFileChooser.FILES_ONLY));
        p.add(boxWith("StarDist model (.zip)", row(tfModelZip, btnBrowseModel)));

        // StarDist thresholds
        spProbThresh = new JSpinner(new SpinnerNumberModel(0.5, 0.0, 1.0, 0.05));
        spNmsThresh  = new JSpinner(new SpinnerNumberModel(0.3, 0.0, 1.0, 0.05));
        p.add(boxWith("StarDist thresholds", grid2(
                new JLabel("Probability:"), spProbThresh,
                new JLabel("NMS:"),         spNmsThresh
        )));


        // Rescale + training px size
        cbRescaleToTrainingPx = new JCheckBox("Rescale to training pixel size");
        spTrainingPixelSizeUm = new JSpinner(new SpinnerNumberModel(0.568, 0.01, 100.0, 0.001));
        p.add(boxWith("Rescaling", column(cbRescaleToTrainingPx, row(new JLabel("Training pixel size (µm):"), spTrainingPixelSizeUm))));


        tfGangliaModelFolder = new JTextField(28);
        btnBrowseGangliaModelFolder = new JButton("Browse…");
        btnBrowseGangliaModelFolder.addActionListener(e -> chooseFolderName(tfGangliaModelFolder));

        pnlGangliaModel = boxWith("Ganglia model",
                row(new JLabel(""), tfGangliaModelFolder, btnBrowseGangliaModelFolder));
        p.add(pnlGangliaModel);

        spHuDilationMicron       = new JSpinner(new SpinnerNumberModel(12.0, 0.0, 1000.0, 0.5));
        spGangliaProbThresh01    = new JSpinner(new SpinnerNumberModel(0.35, 0.0, 1.0, 0.01));
        spGangliaMinAreaUm2      = new JSpinner(new SpinnerNumberModel(200.0, 0.0, 100000.0, 5.0));
        spGangliaOpenIterations  = new JSpinner(new SpinnerNumberModel(3, 0, 50, 1));
        cbGangliaInteractiveReview = new JCheckBox("Interactive review overlay");

        p.add(boxWith("Ganglia post-processing", grid2(
                new JLabel("Hu dilation (µm):"),      spHuDilationMicron,
                new JLabel("Prob threshold (0–1):"),  spGangliaProbThresh01,
                new JLabel("Min area (µm²):"),        spGangliaMinAreaUm2,
                new JLabel("Open iterations:"),       spGangliaOpenIterations
        )));
        p.add(boxWith("Review", cbGangliaInteractiveReview));

        p.add(Box.createVerticalStrut(8));

        JScrollPane scroll = new JScrollPane(
                p,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        );
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        outer.add(scroll, BorderLayout.CENTER);
        return outer;
    }

    // ---------------- Actions ----------------

    private void onRun(JButton runBtn) {
        runBtn.setEnabled(false);

        Params.GangliaMode mode = (Params.GangliaMode) cbGangliaMode.getSelectedItem();

        boolean ok =
                InputValidation.validateImageOrShow(this, tfImagePath.getText()) &&
                        InputValidation.validateZipOrShow(this, tfModelZip.getText(), "StarDist model") &&
                        InputValidation.validateOutputDirOrShow(this, tfOutputDir.getText());

        if (ok && cbGangliaAnalysis.isSelected()) {
            switch (Objects.requireNonNull(mode)) {
                case DEEPIMAGEJ:
                    ok = InputValidation.validateModelsFolderOrShow(this, tfGangliaModelFolder.getText());
                    break;
                case IMPORT_ROI:
                    ok = InputValidation.validateZipOrShow(this, tfCustomGangliaZip.getText(), "Ganglia ROI zip");
                    break;
                case DEFINE_FROM_HU:
                case MANUAL:
                    break;
            }
        }

        if (!ok) {
            runBtn.setEnabled(true);
            return;
        }

        SwingWorker<Void,Void> worker = new SwingWorker() {
            @Override protected Void doInBackground() {
                try {
                    Params p = buildParamsFromUI();
                    if (p.gangliaMode == Params.GangliaMode.IMPORT_ROI) {
                        p.customGangliaRoiZip = emptyToNull(tfCustomGangliaZip.getText());
                    } else {
                        p.customGangliaRoiZip = null;
                    }
                    new NeuronsHuPipeline().run(p,false);
                } catch (Throwable ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                            owner,
                            "Analysis failed:\n" + ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                    ));
                }
                return null;
            }
            @Override protected void done() { runBtn.setEnabled(true); }
        };
        worker.execute();
    }

    private Params buildParamsFromUI() {
        Params p = new Params();

        p.imagePath = emptyToNull(tfImagePath.getText());
        p.outputDir = emptyToNull(tfOutputDir.getText());

        p.huChannel = (int) spHuChannel.getValue();

        p.stardistModelZip = tfModelZip.getText();

        p.rescaleToTrainingPx  = cbRescaleToTrainingPx.isSelected();
        p.trainingPixelSizeUm  = ((Number) spTrainingPixelSizeUm.getValue()).doubleValue();
        p.trainingRescaleFactor= ((Number) spTrainingRescaleFactor.getValue()).doubleValue();

        p.probThresh = ((Number) spProbThresh.getValue()).doubleValue();
        p.nmsThresh  = ((Number) spNmsThresh.getValue()).doubleValue();

        p.saveFlattenedOverlay = cbSaveFlattenedOverlay.isSelected();

        p.requireMicronUnits = cbRequireMicronUnits.isSelected();
        p.neuronSegLowerLimitUm = ((Number) spNeuronSegLowerLimitUm.getValue()).doubleValue();
        p.neuronSegMinMicron    = ((Number) spNeuronSegMinMicron.getValue()).doubleValue();

        p.cellCountsPerGanglia = cbGangliaAnalysis.isSelected();
        p.gangliaMode = (Params.GangliaMode) cbGangliaMode.getSelectedItem();
        p.gangliaChannel = ((Number) spGangliaChannel.getValue()).intValue();
        p.gangliaModelFolder = tfGangliaModelFolder.getText();
        p.huDilationMicron = ((Number) spHuDilationMicron.getValue()).doubleValue();
        p.gangliaProbThresh01 = ((Number) spGangliaProbThresh01.getValue()).doubleValue();
        p.gangliaMinAreaUm2   = ((Number) spGangliaMinAreaUm2.getValue()).doubleValue();
        p.gangliaOpenIterations = ((Number) spGangliaOpenIterations.getValue()).intValue();
        p.gangliaInteractiveReview = cbGangliaInteractiveReview.isSelected();
        p.uiAnchor = SwingUtilities.getWindowAncestor(this);

        return p;
    }

    private void previewImage() {
        final String path = (tfImagePath.getText() == null) ? "" : tfImagePath.getText().trim();
        btnPreviewImage.setEnabled(false);

        new SwingWorker<Void,Void>() {
            @Override protected Void doInBackground() {
                try {
                    if (path.isEmpty()) {
                        // Show the standard ImageJ file chooser (File ▸ Open…)
                        IJ.open();
                    } else {
                        // Open exactly the way ImageJ would from File ▸ Open…
                        IJ.open(path);
                    }
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
            @Override protected void done() {
                btnPreviewImage.setEnabled(true);
            }
        }.execute();
    }



    private void loadDefaults() {
        // Fill with your known-good defaults (same as your working run)
        if (tfCustomGangliaZip != null) tfCustomGangliaZip.setText("");
        tfImagePath.setText("/path/to/image");
        spHuChannel.setValue(3);

        String modelZip = new File(new File(IJ.getDirectory("imagej"), "models"), "2D_enteric_neuron_v4_1.zip").getAbsolutePath();
        tfModelZip.setText(modelZip);

        cbRescaleToTrainingPx.setSelected(true);
        spTrainingPixelSizeUm.setValue(0.568);
        spProbThresh.setValue(0.5);
        spNmsThresh.setValue(0.3);

        cbSaveFlattenedOverlay.setSelected(true);

        cbRequireMicronUnits.setSelected(true);
        spNeuronSegLowerLimitUm.setValue(70.0);
        spNeuronSegMinMicron.setValue(70.0);
        spTrainingRescaleFactor.setValue(1.0);

        cbGangliaAnalysis.setSelected(true);
        cbGangliaMode.setSelectedItem(Params.GangliaMode.DEEPIMAGEJ);
        spGangliaChannel.setValue(2);
        tfGangliaModelFolder.setText("2D_Ganglia_RGB_v3.bioimage.io.model");

        spHuDilationMicron.setValue(12.0);
        spGangliaProbThresh01.setValue(0.35);
        spGangliaMinAreaUm2.setValue(200.0);
        spGangliaOpenIterations.setValue(3);
        cbGangliaInteractiveReview.setSelected(true);
    }

    // ---------------- Small helpers ----------------

    private static String emptyToNull(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private static JPanel boxWith(String title, Component content) {
        JPanel box = new JPanel(new BorderLayout());
        box.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                title,
                TitledBorder.LEFT,
                TitledBorder.TOP
        ));
        box.add(content, BorderLayout.CENTER);
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        return box;
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

    private static JPanel grid2(Component... kvPairs) {
        JPanel g = new JPanel(new GridBagLayout());
        GridBagConstraints lc = new GridBagConstraints();
        GridBagConstraints rc = new GridBagConstraints();
        lc.gridx = 0; lc.gridy = 0; lc.anchor = GridBagConstraints.WEST; lc.insets = new Insets(3,3,3,3);
        rc.gridx = 1; rc.gridy = 0; rc.weightx = 1; rc.fill = GridBagConstraints.HORIZONTAL; rc.insets = new Insets(3,3,3,3);
        for (int i = 0; i < kvPairs.length; i += 2) {
            g.add(kvPairs[i], lc);
            g.add(kvPairs[i+1], rc);
            lc.gridy++; rc.gridy++;
        }
        g.setAlignmentX(Component.LEFT_ALIGNMENT);
        return g;
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

    /**
     * Lets you pick a folder name under <Fiji>/models (for DIJ model folder).
     * This writes only the folder name into the field, matching your Params usage.
     */
    private void chooseFolderName(JTextField target) {
        File models = new File(IJ.getDirectory("imagej"), "models");
        JFileChooser ch = new JFileChooser(models);
        ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int rv = ch.showOpenDialog(this);
        if (rv == JFileChooser.APPROVE_OPTION) {
            File sel = ch.getSelectedFile();
            // store only folder name relative to models dir (parity with your current Params)
            String name = sel.getName();
            target.setText(name);
        }
    }

    private void updateGangliaUI() {
        boolean gangliaOn = cbGangliaAnalysis.isSelected();
        Params.GangliaMode mode = (Params.GangliaMode) cbGangliaMode.getSelectedItem();

        if (pnlGangliaModel != null) {
            pnlGangliaModel.setVisible(gangliaOn && mode == Params.GangliaMode.DEEPIMAGEJ);
        }
        if (pnlCustomZipBasic != null) {
            pnlCustomZipBasic.setVisible(gangliaOn && mode == Params.GangliaMode.IMPORT_ROI);
        }
        revalidate();
        repaint();
    }

    private void updateRunButtonEnabled() {
        if (runBtn == null) return;

        boolean enable = true;

        if (cbGangliaAnalysis.isSelected() &&
                cbGangliaMode.getSelectedItem() == Params.GangliaMode.IMPORT_ROI) {

            String path = (tfCustomGangliaZip != null) ? tfCustomGangliaZip.getText().trim() : "";
            enable = isValidZipPath(path); // silent gating for the button
        }

        runBtn.setEnabled(enable);
    }

    private static boolean isValidZipPath(String path) {
        if (path == null || path.isEmpty()) return false;
        File f = new File(path);
        String name = f.getName().toLowerCase(Locale.ROOT);
        return f.isFile() && name.endsWith(".zip");
    }

}
