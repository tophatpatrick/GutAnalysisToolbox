package UI.panes.SettingPanes;

import Features.AnalyseWorkflows.NeuronsMultiPipeline;
import Features.Core.Params;
import UI.util.InputValidation;
import ij.IJ;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MultichannelPane extends JPanel {
    public static final String Name = "Multiplex Workflow";

    private final Window owner;

    // Reuse Hu basics
    private JTextField tfImagePath, tfOutputDir;
    private JButton btnBrowseImage, btnBrowseOutput;
    private JButton btnPreviewImage;

    private JSpinner spHuChannel;
    private JTextField tfHuModelZip;


    private JTextField tfGangliaRoiZip;
    private JButton btnBrowseGangliaRoi;

    private JPanel pnlGangliaModelRow;
    private JTextField tfGangliaModelFolder;
    private JButton btnBrowseGangliaModel;

    // Subtype model + overlap
    private JTextField tfSubtypeModelZip;
    private JSpinner spOverlapFrac, spSubtypeProb, spSubtypeNms;

    // Ganglia + rescale etc. (subset for brevity)
    private JCheckBox cbGangliaAnalysis;
    private JComboBox<Params.GangliaMode> cbGangliaMode;
    private JSpinner spGangliaChannel;
    private JPanel pnlCustomRoiBox;

    //Build marker params
    private JPanel  markersPanel;
    private JButton btnAddMarker, btnRemoveMarker;
    private final java.util.List<MarkerRow> markerRows = new java.util.ArrayList<>();

    private JCheckBox cbRescaleToTrainingPx;
    private JSpinner spTrainingPxUm, spTrainingScale;
    private JCheckBox cbSaveOverlay;
    private JCheckBox cbRequireMicronUnits;
    private JSpinner spNeuronSegLowerUm, spNeuronSegMinUm;

    public MultichannelPane(Window owner) {
        super(new BorderLayout(10,10));
        this.owner = owner;
        setBorder(BorderFactory.createEmptyBorder(16,16,16,16));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Basic", buildBasic());
        tabs.add("Markers",buildMarkersTab());
        tabs.addTab("Advanced", buildAdvanced());

        add(tabs, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton runBtn = new JButton("Run Multiplex Analysis");
        runBtn.addActionListener(e -> onRun(runBtn));
        actions.add(runBtn);
        add(actions, BorderLayout.SOUTH);

        loadDefaults();
    }

    private JPanel buildMarkersTab() {
        JPanel outer = new JPanel(new BorderLayout(8,8));

        markersPanel = new JPanel();
        markersPanel.setLayout(new BoxLayout(markersPanel, BoxLayout.Y_AXIS));
        markersPanel.setBorder(BorderFactory.createEmptyBorder(4,4,4,4));

        JScrollPane scroll = new JScrollPane(markersPanel);
        scroll.setPreferredSize(new Dimension(640, 260));
        outer.add(scroll, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnAddMarker = new JButton("Add marker");
        btnRemoveMarker = new JButton("Remove selected");
        btnAddMarker.addActionListener(e -> addMarkerRow(null, 1,  false, null));
        btnRemoveMarker.addActionListener(e -> removeSelectedMarkerRow());
        controls.add(btnAddMarker);
        controls.add(btnRemoveMarker);

        outer.add(controls, BorderLayout.SOUTH);
        outer.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Markers",
                TitledBorder.LEFT,
                TitledBorder.TOP
        ));
        return outer;
    }

    private void addMarkerRow(String name, int channel, boolean custom, File roiZip) {
        MarkerRow row = new MarkerRow(name, channel, custom, roiZip);
        markerRows.add(row);
        markersPanel.add(row.panel);
        markersPanel.revalidate();
        markersPanel.repaint();
    }

    private void removeSelectedMarkerRow() {
        boolean removedAny = false;

        // walk from bottom to top so indices stay valid while removing
        for (int i = markerRows.size() - 1; i >= 0; i--) {
            MarkerRow row = markerRows.get(i);
            if (row.cbSelect.isSelected()) {
                markersPanel.remove(row.panel);
                markerRows.remove(i);
                removedAny = true;
            }
        }

        // if nothing was selected, remove the last row as a fallback
        if (!removedAny && !markerRows.isEmpty()) {
            int last = markerRows.size() - 1;
            markersPanel.remove(markerRows.get(last).panel);
            markerRows.remove(last);
        }

        // layout update once
        markersPanel.revalidate();
        markersPanel.repaint();
    }

    // ------- MarkerRow -------
    private static final class MarkerRow {
        final JPanel panel = new JPanel(new GridBagLayout());
        final JCheckBox cbSelect = new JCheckBox();
        final JTextField tfName  = new JTextField(12);
        final JSpinner  spChannel = new JSpinner(new SpinnerNumberModel(1, 1, 64, 1));
        final JCheckBox cbCustom = new JCheckBox("Use custom ROI .zip");
        final JTextField tfRoiZip = new JTextField(18);
        final JButton   btnBrowseRoi = new JButton("…");

        MarkerRow(String name, int channel, boolean custom, File roiZip) {
            // card-like border
            panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(230,230,230)),
                    BorderFactory.createEmptyBorder(6,6,6,6)
            ));

            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(4,4,4,4);
            c.anchor = GridBagConstraints.WEST;

            // Row 0: select | Name: [.....] | Channel: [#]
            c.gridy = 0; c.gridx = 0;                           panel.add(cbSelect, c);

            c.gridx = 1;                                        panel.add(new JLabel("Name:"), c);
            c.gridx = 2; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
            tfName.setText(name != null ? name : "marker");
            tfName.setMaximumSize(new Dimension(Integer.MAX_VALUE, tfName.getPreferredSize().height));
            panel.add(tfName, c);

            c.gridx = 3; c.weightx = 0; c.fill = GridBagConstraints.NONE;
            panel.add(new JLabel("Channel:"), c);
            c.gridx = 4;                                        spChannel.setValue(channel); panel.add(spChannel, c);

            // Row 1: Custom ROI zip: [x]  [path...............]  […]
            c.gridy = 1; c.gridx = 1;                           panel.add(new JLabel("Custom ROI zip:"), c);
            c.gridx = 2;                                        cbCustom.setSelected(custom); panel.add(cbCustom, c);

            c.gridx = 3; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
            tfRoiZip.setEnabled(custom);
            tfRoiZip.setText(roiZip != null ? roiZip.getAbsolutePath() : "");
            tfRoiZip.setMaximumSize(new Dimension(Integer.MAX_VALUE, tfRoiZip.getPreferredSize().height));
            panel.add(tfRoiZip, c);

            c.gridx = 4; c.weightx = 0; c.fill = GridBagConstraints.NONE;
            btnBrowseRoi.setEnabled(custom);
            btnBrowseRoi.addActionListener(e -> chooseFile(tfRoiZip, JFileChooser.FILES_ONLY));
            panel.add(btnBrowseRoi, c);

            cbCustom.addActionListener(e -> {
                boolean on = cbCustom.isSelected();
                tfRoiZip.setEnabled(on);
                btnBrowseRoi.setEnabled(on);
            });

            // Make the card width follow the container (prevents horizontal scroll)
            panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
            panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        // Convert row -> pipeline spec (no per-marker prob/nms)
        NeuronsMultiPipeline.MarkerSpec toSpec() {
            String nm = tfName.getText().trim();
            if (nm.isEmpty()) throw new IllegalArgumentException("Marker name cannot be empty.");
            int ch = ((Number) spChannel.getValue()).intValue();

            NeuronsMultiPipeline.MarkerSpec spec = new NeuronsMultiPipeline.MarkerSpec(nm, ch);

            if (cbCustom.isSelected()) {
                String z = tfRoiZip.getText().trim();
                if (z.isEmpty()) throw new IllegalArgumentException(
                        "Custom ROI selected for '"+nm+"' but no zip chosen.");
                File f = new File(z);
                if (!f.isFile() || !z.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
                    throw new IllegalArgumentException("Custom ROI for '"+nm+"' must be a .zip on disk.");
                }
                spec.withCustomRois(f);
            }
            return spec;
        }
    }





    private JPanel buildBasic() {

        JPanel outer = new JPanel(new BorderLayout());


        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        tfImagePath = new JTextField(36);
        btnBrowseImage = new JButton("Browse…");
        btnBrowseImage.addActionListener(e -> chooseFile(tfImagePath, JFileChooser.FILES_ONLY));
        btnPreviewImage = new JButton("Preview");
        btnPreviewImage.addActionListener(e -> previewImage());
        p.add(box("Input image (.tif, .lif, etc.)", row(tfImagePath, btnBrowseImage,btnPreviewImage)));

        spHuChannel = new JSpinner(new SpinnerNumberModel(3,1,32,1));

        p.add(box("Hu channel & model", grid2(
                new JLabel("Hu channel:"), spHuChannel
        )));



        tfOutputDir = new JTextField(36);
        btnBrowseOutput = new JButton("Browse…");
        btnBrowseOutput.addActionListener(e -> chooseFile(tfOutputDir, JFileChooser.DIRECTORIES_ONLY));
        cbSaveOverlay = new JCheckBox("Save flattened overlays");
        p.add(box("Output", column(row(tfOutputDir, btnBrowseOutput), cbSaveOverlay)));

        cbGangliaAnalysis = new JCheckBox("Run ganglia analysis");
        cbGangliaMode = new JComboBox<>(Params.GangliaMode.values());
        cbGangliaAnalysis.addActionListener(e -> updateGangliaVisibility());
        cbGangliaMode.addActionListener(e -> updateGangliaVisibility());
        spGangliaChannel = new JSpinner(new SpinnerNumberModel(2,1,32,1));
        p.add(box("Ganglia", column(
                cbGangliaAnalysis,
                row(new JLabel("Mode:"), cbGangliaMode),
                row(new JLabel("Ganglia channel:"), spGangliaChannel)
        )));

        p.add(Box.createVerticalGlue());

        tfGangliaRoiZip = new JTextField(28);
        btnBrowseGangliaRoi = new JButton("Browse…");
        btnBrowseGangliaRoi.addActionListener(e -> chooseFile(tfGangliaRoiZip, JFileChooser.FILES_ONLY));


        pnlCustomRoiBox = box("Import ganglia ROIs (.zip)",
                row(new JLabel("Zip file:"), tfGangliaRoiZip, btnBrowseGangliaRoi));
        p.add(pnlCustomRoiBox);

        JScrollPane scroll = new JScrollPane(
                p,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        );
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        outer.add(scroll, BorderLayout.CENTER);
        return outer;
    }

    private JPanel buildAdvanced() {
        JPanel outer = new JPanel(new BorderLayout());
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        cbRescaleToTrainingPx = new JCheckBox("Rescale to training pixel size");
        spTrainingPxUm = new JSpinner(new SpinnerNumberModel(0.568, 0.01, 100.0, 0.001));
        spTrainingScale= new JSpinner(new SpinnerNumberModel(1.0,   0.01, 100.0, 0.01));
        p.add(box("Rescaling", grid2(
                new JLabel("Training pixel size (µm):"), spTrainingPxUm,
                new JLabel("Training rescale factor:"),  spTrainingScale,
                new JLabel(""), cbRescaleToTrainingPx
        )));

        cbRequireMicronUnits = new JCheckBox("Require microns calibration");
        spNeuronSegLowerUm = new JSpinner(new SpinnerNumberModel(70.0, 0.0, 10000.0, 1.0));
        spNeuronSegMinUm   = new JSpinner(new SpinnerNumberModel(70.0, 0.0, 10000.0, 1.0));
        p.add(box("Size filtering", grid2(
                new JLabel("Hu seg lower limit (µm):"), spNeuronSegLowerUm,
                new JLabel("Subtype min size (µm):"),   spNeuronSegMinUm,
                new JLabel(""), cbRequireMicronUnits
        )));

        tfGangliaModelFolder = new JTextField(28);
        btnBrowseGangliaModel = new JButton("Pick folder…");
        btnBrowseGangliaModel.addActionListener(e -> chooseFolderName(tfGangliaModelFolder));
        pnlGangliaModelRow = box("Ganglia model (DeepImageJ)",
                row(new JLabel("Folder (under <Fiji>/models):"), tfGangliaModelFolder, btnBrowseGangliaModel)
        );
        p.add(pnlGangliaModelRow);

        spSubtypeProb = new JSpinner(new SpinnerNumberModel(0.50, 0.0, 1.0, 0.05));
        spSubtypeNms  = new JSpinner(new SpinnerNumberModel(0.30, 0.0, 1.0, 0.05));
        spOverlapFrac = new JSpinner(new SpinnerNumberModel(0.40, 0.0, 1.0, 0.05));
        tfHuModelZip = new JTextField(28);
        JButton btnBrowseHu = new JButton("Browse…");
        btnBrowseHu.addActionListener(e -> chooseFile(tfHuModelZip, JFileChooser.FILES_ONLY));

        tfSubtypeModelZip = new JTextField(28);
        JButton btnBrowseSubtype = new JButton("Browse…");
        btnBrowseSubtype.addActionListener(e -> chooseFile(tfSubtypeModelZip, JFileChooser.FILES_ONLY));

// (optional) allow shrinking a bit below preferred width
        tfHuModelZip.setMinimumSize(new Dimension(120, tfHuModelZip.getPreferredSize().height));
        tfSubtypeModelZip.setMinimumSize(new Dimension(120, tfSubtypeModelZip.getPreferredSize().height));

        p.add(box("Subtype model & overlap", grid2(
                new JLabel("Hu StarDist model (.zip):"),       growRow(tfHuModelZip, btnBrowseHu),
                new JLabel("Subtype StarDist model (.zip):"),  growRow(tfSubtypeModelZip, btnBrowseSubtype),
                new JLabel("Subtype prob:"),                   spSubtypeProb,
                new JLabel("Subtype NMS:"),                    spSubtypeNms,
                new JLabel("Hu/marker overlap fraction:"),     spOverlapFrac
        )));
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

    private static JPanel growRow(JTextField tf, JButton btn) {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(0, 0, 0, 0);

        // text field grows
        c.gridx = 0; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        p.add(tf, c);

        // button stays compact
        c.gridx = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE; c.insets = new Insets(0,8,0,0);
        p.add(btn, c);

        return p;
    }

    private void onRun(JButton runBtn) {
        runBtn.setEnabled(false);

        if (markerRows.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please add at least one marker in the Markers tab.",
                    "No markers", JOptionPane.ERROR_MESSAGE);
            runBtn.setEnabled(true);
            return;
        }

        // Base validations
        if (!InputValidation.validateImageOrShow(this, tfImagePath.getText()) ||
                !InputValidation.validateZipOrShow(this, tfHuModelZip.getText(), "Hu StarDist model") ||
                !InputValidation.validateZipOrShow(this, tfSubtypeModelZip.getText(), "Subtype StarDist model") ||
                !InputValidation.validateOutputDirOrShow(this, tfOutputDir.getText())) {
            runBtn.setEnabled(true);
            return;
        }

        // Mode-aware ganglia validations
        if (cbGangliaAnalysis.isSelected()) {
            Params.GangliaMode mode = (Params.GangliaMode) cbGangliaMode.getSelectedItem();
            switch (Objects.requireNonNull(mode)) {
                case DEEPIMAGEJ:
                    if (!InputValidation.validateModelsFolderOrShow(this, tfGangliaModelFolder.getText())) {
                        runBtn.setEnabled(true);
                        return;
                    }
                    break;
                case IMPORT_ROI:
                    if (!InputValidation.validateZipOrShow(this, tfGangliaRoiZip.getText(), "Ganglia ROI zip")) {
                        runBtn.setEnabled(true);
                        return;
                    }
                    break;
                default:
                    break;
            }
        }


        SwingWorker<Void,Void> w = new SwingWorker() {
            @Override protected Void doInBackground() {
                try {
                    Params base = buildBaseParams();
                    NeuronsMultiPipeline.MultiParams mp = buildMultiParams(base);
                    new NeuronsMultiPipeline().run(mp);
                } catch (Throwable ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                            owner,
                            "Multiplex failed:\n" + ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE));
                }
                return null;
            }
            @Override protected void done() { runBtn.setEnabled(true); }
        };
        w.execute();
    }

    private Params buildBaseParams() {
        Params p = new Params();
        p.imagePath = emptyToNull(tfImagePath.getText());
        p.outputDir = emptyToNull(tfOutputDir.getText());

        p.huChannel = ((Number)spHuChannel.getValue()).intValue();
        p.stardistModelZip = tfHuModelZip.getText();

        p.rescaleToTrainingPx   = cbRescaleToTrainingPx.isSelected();
        p.trainingPixelSizeUm   = ((Number)spTrainingPxUm.getValue()).doubleValue();
        p.trainingRescaleFactor = ((Number)spTrainingScale.getValue()).doubleValue();

        p.saveFlattenedOverlay = cbSaveOverlay.isSelected();

        p.requireMicronUnits     = cbRequireMicronUnits.isSelected();
        p.neuronSegLowerLimitUm  = ((Number)spNeuronSegLowerUm.getValue()).doubleValue();
        p.neuronSegMinMicron     = ((Number)spNeuronSegMinUm.getValue()).doubleValue();

        p.cellCountsPerGanglia = cbGangliaAnalysis.isSelected();
        p.gangliaMode          = (Params.GangliaMode) cbGangliaMode.getSelectedItem();
        p.gangliaChannel       = ((Number)spGangliaChannel.getValue()).intValue();

        if (p.cellCountsPerGanglia) {
            if (p.gangliaMode == Params.GangliaMode.DEEPIMAGEJ) {
                p.gangliaModelFolder = emptyToNull(tfGangliaModelFolder.getText()); // folder name under <Fiji>/models
                p.customGangliaRoiZip = null;
            } else if (p.gangliaMode == Params.GangliaMode.IMPORT_ROI) {
                p.customGangliaRoiZip = emptyToNull(tfGangliaRoiZip.getText());
                p.gangliaModelFolder = null;
            } else {
                p.gangliaModelFolder = null;
                p.customGangliaRoiZip = null;
            }
        } else {
            p.gangliaModelFolder = null;
            p.customGangliaRoiZip = null;
        }

        return p;
    }

    private NeuronsMultiPipeline.MultiParams buildMultiParams(Params base) {
        NeuronsMultiPipeline.MultiParams mp = new NeuronsMultiPipeline.MultiParams();
        mp.base = base;
        mp.subtypeModelZip = tfSubtypeModelZip.getText();
        mp.multiProb   = ((Number)spSubtypeProb.getValue()).doubleValue();
        mp.multiNms    = ((Number)spSubtypeNms.getValue()).doubleValue();
        mp.overlapFrac = ((Number)spOverlapFrac.getValue()).doubleValue();

        if (markerRows.isEmpty()) {
            throw new IllegalArgumentException("Add at least one marker.");
        }
        for (MarkerRow r : markerRows) {
            mp.markers.add(r.toSpec());
        }
        return mp;
    }

    // ---- helpers ----
    private static String[] splitCsv(String s) {
        if (s == null || s.trim().isEmpty()) return new String[0];
        String[] toks = s.split(",");
        List<String> out = new ArrayList<>();
        for (String t : toks) {
            String u = t.trim();
            if (!u.isEmpty()) out.add(u);
        }
        return out.toArray(new String[0]);
    }
    private static int[] parseIntCsv(String s) {
        String[] toks = splitCsv(s);
        int[] out = new int[toks.length];
        for (int i=0; i<toks.length; i++) out[i] = Integer.parseInt(toks[i]);
        return out;
    }

    private void loadDefaults() {
        tfImagePath = ensure(tfImagePath);
        tfImagePath.setText("/path/to/your/composite.tif");
        tfOutputDir.setText("");

        spHuChannel.setValue(3);
        tfHuModelZip.setText(new File(new File(IJ.getDirectory("imagej"), "models"), "2D_enteric_neuron_v4_1.zip").getAbsolutePath());

        tfSubtypeModelZip.setText(new File(new File(IJ.getDirectory("imagej"), "models"), "2D_enteric_neuron_subtype_v4.zip").getAbsolutePath());
        spSubtypeProb.setValue(0.50); spSubtypeNms.setValue(0.30); spOverlapFrac.setValue(0.40);

        addMarkerRow("nNOS", 2, false, null);
        addMarkerRow("ChAT", 1,  false, null);

        // Defaults for new ganglia UI
        if (tfGangliaRoiZip == null) tfGangliaRoiZip = new JTextField(28);
        tfGangliaRoiZip.setText("");                        // empty by default

        if (tfGangliaModelFolder == null) tfGangliaModelFolder = new JTextField(28);
        tfGangliaModelFolder.setText("2D_Ganglia_RGB_v3.bioimage.io.model");

        // Make the visibility correct on first render
        updateGangliaVisibility();

        cbSaveOverlay.setSelected(true);
        cbRescaleToTrainingPx.setSelected(true);
        spTrainingPxUm.setValue(0.568);
        spTrainingScale.setValue(1.0);

        cbRequireMicronUnits.setSelected(true);
        spNeuronSegLowerUm.setValue(70.0);
        spNeuronSegMinUm.setValue(70.0);

        cbGangliaAnalysis.setSelected(true);
        cbGangliaMode.setSelectedItem(Params.GangliaMode.DEEPIMAGEJ);
        spGangliaChannel.setValue(4);
    }

    // small UI helpers
    private static JPanel box(String title, Component c) {
        JPanel b = new JPanel(new BorderLayout());
        b.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), title, TitledBorder.LEFT, TitledBorder.TOP));
        b.add(c, BorderLayout.CENTER);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        return b;
    }
    private static JPanel row(JComponent... comps) {
        JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 8,4));
        for (JComponent c: comps) r.add(c);
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        return r;
    }
    private static JPanel column(JComponent... comps) {
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        for (JComponent c: comps) { c.setAlignmentX(Component.LEFT_ALIGNMENT); col.add(c); col.add(Box.createVerticalStrut(6)); }
        return col;
    }
    private static JPanel grid2(Component... kv) {
        JPanel g = new JPanel(new GridBagLayout());
        GridBagConstraints lc = new GridBagConstraints(), rc = new GridBagConstraints();
        lc.gridx=0; lc.gridy=0; lc.anchor=GridBagConstraints.WEST; lc.insets = new Insets(3,3,3,3);
        rc.gridx=1; rc.gridy=0; rc.weightx=1; rc.fill=GridBagConstraints.HORIZONTAL; rc.insets = new Insets(3,3,3,3);
        for (int i=0; i<kv.length; i+=2) { g.add(kv[i], lc); g.add(kv[i+1], rc); lc.gridy++; rc.gridy++; }
        g.setAlignmentX(Component.LEFT_ALIGNMENT);
        return g;
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

    private void chooseFolderName(JTextField target) {
        File models = new File(IJ.getDirectory("imagej"), "models");
        JFileChooser ch = new JFileChooser(models);
        ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int rv = ch.showOpenDialog(this);
        if (rv == JFileChooser.APPROVE_OPTION) {
            target.setText(ch.getSelectedFile().getName()); // store only folder name
        }
    }

    private void updateGangliaVisibility() {
        boolean runGanglia = cbGangliaAnalysis.isSelected();
        Params.GangliaMode mode = (Params.GangliaMode) cbGangliaMode.getSelectedItem();

        boolean showCustom = runGanglia && mode == Params.GangliaMode.IMPORT_ROI;
        boolean showDIJ    = runGanglia && mode == Params.GangliaMode.DEEPIMAGEJ;

        if (pnlCustomRoiBox != null) {
            pnlCustomRoiBox.setVisible(showCustom);
            if (!showCustom && tfGangliaRoiZip != null) tfGangliaRoiZip.setText("");
        }
        if (pnlGangliaModelRow != null) {
            pnlGangliaModelRow.setVisible(showDIJ);
            if (!showDIJ && tfGangliaModelFolder != null) tfGangliaModelFolder.setText("");
        }
        revalidate();
        repaint();
    }








    private static void chooseFile(JTextField target, int mode) {
        JFileChooser ch = new JFileChooser();
        ch.setFileSelectionMode(mode);
        if (target.getText() != null && !target.getText().isEmpty()) {
            File start = new File(target.getText());
            ch.setCurrentDirectory(start.isDirectory()? start : start.getParentFile());
        }
        if (ch.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            target.setText(ch.getSelectedFile().getAbsolutePath());
        }
    }
    private static JTextField ensure(JTextField tf) { return tf==null? new JTextField(36) : tf; }
    private static String emptyToNull(String s) { if (s==null) return null; s=s.trim(); return s.isEmpty()? null : s; }
}
