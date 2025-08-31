package UI.panes.SettingPanes;

import Features.AnalyseWorkflows.NeuronsMultiNoHuPipeline;
import Features.AnalyseWorkflows.NeuronsMultiPipeline;
import Features.Core.Params;
import UI.Handlers.Navigator;
import UI.util.InputValidation;
import ij.IJ;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MultiChannelNoHuPane extends JPanel {

    public static final String Name = "Multi-Channel No Hu";

    private final Navigator navigator;
    private final Window owner;

    // --- Basic ---
    private JTextField tfImagePath;
    private JButton    btnBrowseImage;

    private JTextField tfSubtypeModelZip;
    private JButton    btnBrowseSubtypeModel;

    private JCheckBox  cbRescaleToTrainingPx;
    private JSpinner   spTrainingPixelSizeUm;   // double
    private JSpinner   spTrainingRescaleFactor; // double

    private JSpinner   spDefaultProb;           // 0..1
    private JSpinner   spDefaultNms;            // 0..1
    private JSpinner   spOverlapFrac;           // 0..1  (for combinations)

    private JSpinner   spMinMarkerSizeUm;       // double (like neuronSegMinMicron)
    private JCheckBox  cbUseEdf;                // use CLIJ2 EDF

    private JCheckBox  cbSaveFlattenedOverlay;

    private JTextField tfOutputDir;
    private JButton    btnBrowseOutput;

    // --- Ganglia ---
    private JCheckBox  cbGangliaAnalysis;
    private JComboBox<Params.GangliaMode> cbGangliaMode;
    private JSpinner   spGangliaChannel;
    private JTextField tfGangliaModelFolder;
    private JButton    btnBrowseGangliaModelFolder;
    private  JSpinner spCellBodyChannel;

    // --- Markers list ---
    private JPanel     markersPanel;            // holds rows
    private JButton    btnAddMarker;
    private JButton    btnRemoveMarker;

    private final List<MarkerRow> markerRows = new ArrayList<>();

    public MultiChannelNoHuPane(Navigator navigator) {
        super(new BorderLayout(10,10));
        this.navigator = navigator;
        this.owner = SwingUtilities.getWindowAncestor(this);

        setBorder(BorderFactory.createEmptyBorder(16,16,16,16));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Basic", buildBasicTab());
        tabs.addTab("Markers", buildMarkersTab());
        tabs.addTab("Advanced", buildAdvancedTab());

        add(tabs, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton runBtn = new JButton("Run Multi-Channel (No Hu)");
        runBtn.addActionListener(e -> onRun(runBtn));
        actions.add(runBtn);
        add(actions, BorderLayout.SOUTH);

        loadDefaults();
    }

    // ---------------- UI builders ----------------

    private JPanel buildBasicTab() {
        JPanel outer = new JPanel(new BorderLayout());
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        // Image
        tfImagePath = new JTextField(36);
        btnBrowseImage = new JButton("Browse…");
        btnBrowseImage.addActionListener(e -> chooseFile(tfImagePath, JFileChooser.FILES_ONLY));
        p.add(boxWith("Input image (.tif, .lif, .czi)", row(tfImagePath, btnBrowseImage)));



        // Output dir
        tfOutputDir = new JTextField(36);
        btnBrowseOutput = new JButton("Browse…");
        btnBrowseOutput.addActionListener(e -> chooseFile(tfOutputDir, JFileChooser.DIRECTORIES_ONLY));
        p.add(boxWith("Output directory (optional; default: Analysis/<basename>)", row(tfOutputDir, btnBrowseOutput)));


        cbGangliaAnalysis = new JCheckBox("Run ganglia analysis");
        cbGangliaMode = new JComboBox<>(Params.GangliaMode.values());
        spGangliaChannel   = new JSpinner(new SpinnerNumberModel(2, 1, 64, 1)); // fibres/neurites
        spCellBodyChannel  = new JSpinner(new SpinnerNumberModel(1, 1, 64, 1)); // NEW: cell-body
        tfGangliaModelFolder = new JTextField(28);

        p.add(boxWith("Ganglia", column(
                cbGangliaAnalysis,
                row(new JLabel("Fibres / neurites channel (1-based):"), spGangliaChannel),
                row(new JLabel("Cell-body (‘most cells’) channel (1-based):"), spCellBodyChannel),
                row(new JLabel("Ganglia mode:"), cbGangliaMode)
        )));

        cbSaveFlattenedOverlay = new JCheckBox("Save flattened overlays");

        p.add(boxWith("Options", column(cbSaveFlattenedOverlay)));

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
        btnAddMarker.addActionListener(e -> addMarkerRow(null, 1, null, null, false, null));
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

    private JPanel buildAdvancedTab() {
        JPanel outer = new JPanel(new BorderLayout());
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        // Subtype model (.zip)
        tfSubtypeModelZip = new JTextField(36);
        btnBrowseSubtypeModel = new JButton("Browse…");
        btnBrowseSubtypeModel.addActionListener(e -> chooseFile(tfSubtypeModelZip, JFileChooser.FILES_ONLY));
        p.add(boxWith("Subtype StarDist model (.zip)", row(tfSubtypeModelZip, btnBrowseSubtypeModel)));

        // Thresholds
        spDefaultProb = new JSpinner(new SpinnerNumberModel(0.50, 0.0, 1.0, 0.05));
        spDefaultNms  = new JSpinner(new SpinnerNumberModel(0.30, 0.0, 1.0, 0.05));
        spOverlapFrac = new JSpinner(new SpinnerNumberModel(0.40, 0.0, 1.0, 0.05));
        spMinMarkerSizeUm = new JSpinner(new SpinnerNumberModel(160.0, 0.0, 10000.0, 1.0));

        p.add(boxWith("Detection", grid2(
                new JLabel("Default probability:"), spDefaultProb,
                new JLabel("Default NMS:"),         spDefaultNms,
                new JLabel("Min marker size (µm):"), spMinMarkerSizeUm,
                new JLabel("Overlap (combos):"),    spOverlapFrac
        )));

        // Rescale group
        cbRescaleToTrainingPx = new JCheckBox("Rescale to training pixel size");
        spTrainingPixelSizeUm = new JSpinner(new SpinnerNumberModel(0.568, 0.01, 100.0, 0.001));
        spTrainingRescaleFactor = new JSpinner(new SpinnerNumberModel(1.0, 0.01, 100.0, 0.01));

        p.add(boxWith("Projection & Rescaling", column(
                row(new JLabel("Training pixel size (µm):"), spTrainingPixelSizeUm),
                row(new JLabel("Training rescale factor:"),   spTrainingRescaleFactor),
                cbRescaleToTrainingPx
        )));

        tfGangliaModelFolder = new JTextField(28);
        btnBrowseGangliaModelFolder = new JButton("Browse…");
        btnBrowseGangliaModelFolder.addActionListener(e -> chooseFolderName(tfGangliaModelFolder));

        p.add(boxWith("Ganglia model", column(
                row(new JLabel(""), tfGangliaModelFolder, btnBrowseGangliaModelFolder)
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

    // ---------------- Marker rows ----------------

    private void addMarkerRow(String name, int channel, Double prob, Double nms, boolean custom, File roiZip) {
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

    private final class MarkerRow {
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
        NeuronsMultiNoHuPipeline.MarkerSpec toSpec() {
            String nm = tfName.getText().trim();
            if (nm.isEmpty()) throw new IllegalArgumentException("Marker name cannot be empty.");
            int ch = ((Number) spChannel.getValue()).intValue();

            NeuronsMultiNoHuPipeline.MarkerSpec spec = new NeuronsMultiNoHuPipeline.MarkerSpec(nm, ch);

            if (cbCustom.isSelected()) {
                String z = tfRoiZip.getText().trim();
                if (z.isEmpty()) throw new IllegalArgumentException(
                        "Custom ROI selected for '"+nm+"' but no zip chosen.");
                // If your MarkerSpec supports it:
                // spec.withCustomRois(new File(z));
                // If not yet implemented in the pipeline, throw to avoid silent ignore:
                throw new IllegalStateException(
                        "Custom ROI zip selected, but NeuronsMultiPipeline.MarkerSpec has no withCustomRois(File). " +
                                "Add it (and handle it in run()), or disable 'Custom ROI zip'.");
            }
            return spec;
        }
    }

    // ---------------- Actions ----------------

    private void onRun(JButton runBtn) {
        runBtn.setEnabled(false);

        if (!InputValidation.validateImageOrShow(this, tfImagePath.getText()) ||
                !InputValidation.validateZipOrShow(this, tfSubtypeModelZip.getText(), "Subtype StarDist model") ||
                !InputValidation.validateOutputDirOrShow(this, tfOutputDir.getText()) ||
                (cbGangliaAnalysis.isSelected() &&
                        !InputValidation.validateModelsFolderOrShow(this, tfGangliaModelFolder.getText()))
        ) {
            runBtn.setEnabled(true);
            return;
        }

        SwingWorker<Void,Void> worker = new SwingWorker() {
            @Override protected Void doInBackground() {
                try {
                    // Build params and run
                    NeuronsMultiNoHuPipeline.MultiParams mp = buildMultiParamsFromUI();
                    new NeuronsMultiNoHuPipeline().run(mp);
                } catch (Throwable ex) {
                    JOptionPane.showMessageDialog(
                            SwingUtilities.getWindowAncestor(MultiChannelNoHuPane.this),
                            "Analysis failed:\n" + ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
                return null;
            }
            @Override protected void done() { runBtn.setEnabled(true); }
        };
        worker.execute();
    }

    private NeuronsMultiNoHuPipeline.MultiParams buildMultiParamsFromUI() {
        // base Params reused by all pipelines (projection, rescale, ganglia, etc.)
        Params base = new Params();
        base.imagePath = emptyToNull(tfImagePath.getText());
        base.outputDir = emptyToNull(tfOutputDir.getText());

        base.useClij2EDF = cbUseEdf.isSelected();
        base.rescaleToTrainingPx   = cbRescaleToTrainingPx.isSelected();
        base.trainingPixelSizeUm   = ((Number) spTrainingPixelSizeUm.getValue()).doubleValue();
        base.trainingRescaleFactor = ((Number) spTrainingRescaleFactor.getValue()).doubleValue();

        base.neuronSegMinMicron    = ((Number) spMinMarkerSizeUm.getValue()).doubleValue(); // reuse field name
        base.saveFlattenedOverlay  = cbSaveFlattenedOverlay.isSelected();

        // ganglia options (optional)
        base.cellCountsPerGanglia = cbGangliaAnalysis.isSelected();
        base.gangliaMode = (Params.GangliaMode) cbGangliaMode.getSelectedItem();
        base.gangliaChannel     = ((Number) spGangliaChannel.getValue()).intValue();   // fibres
        base.gangliaCellChannel = ((Number) spCellBodyChannel.getValue()).intValue();
        base.gangliaModelFolder = tfGangliaModelFolder.getText(); // folder under <Fiji>/models

        // build MultiParams
        NeuronsMultiNoHuPipeline.MultiParams mp = new NeuronsMultiNoHuPipeline.MultiParams();
        mp.base = base;
        mp.subtypeModelZip = tfSubtypeModelZip.getText();
        mp.multiProb = ((Number) spDefaultProb.getValue()).doubleValue();
        mp.multiNms  = ((Number) spDefaultNms.getValue()).doubleValue();
        mp.overlapFrac = ((Number) spOverlapFrac.getValue()).doubleValue();

        if (markerRows.isEmpty()) throw new IllegalStateException("Add at least one marker.");
        for (MarkerRow r : markerRows) {
            mp.markers.add(r.toSpec());
        }
        return mp;
    }

    // ---------------- Helpers ----------------

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

    private void chooseFolderName(JTextField target) {
        File models = new File(IJ.getDirectory("imagej"), "models");
        JFileChooser ch = new JFileChooser(models);
        ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int rv = ch.showOpenDialog(this);
        if (rv == JFileChooser.APPROVE_OPTION) {
            File sel = ch.getSelectedFile();
            target.setText(sel.getName()); // store folder name only (parity with your Params usage)
        }
    }

    private void loadDefaults() {
        // You can customize these defaults to your environment
        tfSubtypeModelZip.setText(new File(new File(IJ.getDirectory("imagej"), "models"), "2D_neuron_subtype.zip").getAbsolutePath());
        cbRescaleToTrainingPx.setSelected(true);
        spTrainingPixelSizeUm.setValue(0.568);
        spTrainingRescaleFactor.setValue(1.0);

        spDefaultProb.setValue(0.50);
        spDefaultNms.setValue(0.30);
        spOverlapFrac.setValue(0.40);
        spMinMarkerSizeUm.setValue(160.0);

        cbSaveFlattenedOverlay.setSelected(true);

        cbGangliaAnalysis.setSelected(false);
        cbGangliaMode.setSelectedItem(Params.GangliaMode.DEEPIMAGEJ);
        spGangliaChannel.setValue(2);
        spCellBodyChannel.setValue(!markerRows.isEmpty()
                ? ((Number) markerRows.get(0).spChannel.getValue()).intValue()
                : 1);
        tfGangliaModelFolder.setText("2D_Ganglia_RGB_v3.bioimage.io.model");

        // one example row
        addMarkerRow("nNOS", 4, null, null, false, null);
        addMarkerRow("ChAT", 5, null, null, false, null);
    }
}
