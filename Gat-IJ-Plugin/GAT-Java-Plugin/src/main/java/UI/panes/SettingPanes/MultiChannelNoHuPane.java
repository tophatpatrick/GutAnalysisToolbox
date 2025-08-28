package UI.panes.SettingPanes;

import Features.AnalyseWorkflows.NeuronsMultiNoHuPipeline;
import Features.Core.Params;
import UI.Handlers.Navigator;
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
        tabs.addTab("Ganglia", buildGangliaTab());

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
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        // Image
        tfImagePath = new JTextField(36);
        btnBrowseImage = new JButton("Browse…");
        btnBrowseImage.addActionListener(e -> chooseFile(tfImagePath, JFileChooser.FILES_ONLY));
        p.add(boxWith("Input image (.tif, .lif, .czi)", row(tfImagePath, btnBrowseImage)));

        // Subtype model (.zip)
        tfSubtypeModelZip = new JTextField(36);
        btnBrowseSubtypeModel = new JButton("Browse…");
        btnBrowseSubtypeModel.addActionListener(e -> chooseFile(tfSubtypeModelZip, JFileChooser.FILES_ONLY));
        p.add(boxWith("Subtype StarDist model (.zip)", row(tfSubtypeModelZip, btnBrowseSubtypeModel)));

        // Output dir
        tfOutputDir = new JTextField(36);
        btnBrowseOutput = new JButton("Browse…");
        btnBrowseOutput.addActionListener(e -> chooseFile(tfOutputDir, JFileChooser.DIRECTORIES_ONLY));
        p.add(boxWith("Output directory (optional; default: Analysis/<basename>)", row(tfOutputDir, btnBrowseOutput)));

        // Rescale group
        cbRescaleToTrainingPx = new JCheckBox("Rescale to training pixel size");
        spTrainingPixelSizeUm = new JSpinner(new SpinnerNumberModel(0.568, 0.01, 100.0, 0.001));
        spTrainingRescaleFactor = new JSpinner(new SpinnerNumberModel(1.0, 0.01, 100.0, 0.01));
        cbUseEdf = new JCheckBox("Use Extended Depth of Field (CLIJ2)");

        p.add(boxWith("Projection & Rescaling", column(
                cbUseEdf,
                row(new JLabel("Training pixel size (µm):"), spTrainingPixelSizeUm),
                row(new JLabel("Training rescale factor:"),   spTrainingRescaleFactor),
                cbRescaleToTrainingPx
        )));

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

        cbSaveFlattenedOverlay = new JCheckBox("Save flattened overlays");

        p.add(boxWith("Options", column(cbSaveFlattenedOverlay)));

        p.add(Box.createVerticalGlue());
        return p;
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

    private JPanel buildGangliaTab() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        cbGangliaAnalysis = new JCheckBox("Run ganglia analysis");
        cbGangliaMode = new JComboBox<>(Params.GangliaMode.values());
        spGangliaChannel = new JSpinner(new SpinnerNumberModel(2, 1, 16, 1));
        tfGangliaModelFolder = new JTextField(28);
        btnBrowseGangliaModelFolder = new JButton("Browse…");
        btnBrowseGangliaModelFolder.addActionListener(e -> chooseFolderName(tfGangliaModelFolder));

        p.add(boxWith("Ganglia", column(
                cbGangliaAnalysis,
                row(new JLabel("Ganglia mode:"), cbGangliaMode),
                row(new JLabel("Ganglia channel (1-based):"), spGangliaChannel),
                row(new JLabel("DeepImageJ model folder (under <Fiji>/models):"), tfGangliaModelFolder, btnBrowseGangliaModelFolder)
        )));

        p.add(Box.createVerticalGlue());
        return p;
    }

    // ---------------- Marker rows ----------------

    private void addMarkerRow(String name, int channel, Double prob, Double nms, boolean custom, File roiZip) {
        MarkerRow row = new MarkerRow(name, channel, prob, nms, custom, roiZip);
        markerRows.add(row);
        markersPanel.add(row.panel);
        markersPanel.revalidate();
        markersPanel.repaint();
    }

    private void removeSelectedMarkerRow() {
        // Remove the last row that has its "selected" checkbox ticked; if none, remove last row
        for (int i = markerRows.size() - 1; i >= 0; i--) {
            if (markerRows.get(i).cbSelect.isSelected()) {
                markersPanel.remove(markerRows.get(i).panel);
                markerRows.remove(i);
                markersPanel.revalidate();
                markersPanel.repaint();
                return;
            }
        }
        if (!markerRows.isEmpty()) {
            markersPanel.remove(markerRows.get(markerRows.size() - 1).panel);
            markerRows.remove(markerRows.size() - 1);
            markersPanel.revalidate();
            markersPanel.repaint();
        }
    }

    private final class MarkerRow {
        final JPanel panel = new JPanel(new GridBagLayout());
        final JCheckBox cbSelect = new JCheckBox();
        final JTextField tfName  = new JTextField(12);
        final JSpinner spChannel = new JSpinner(new SpinnerNumberModel(1, 1, 64, 1));
        final JSpinner spProb    = new JSpinner(new SpinnerNumberModel(-1.0, -1.0, 1.0, 0.05)); // -1 = use default
        final JSpinner spNms     = new JSpinner(new SpinnerNumberModel(-1.0, -1.0, 1.0, 0.05)); // -1 = use default
        final JCheckBox cbCustom = new JCheckBox("Custom ROI");
        final JTextField tfRoiZip = new JTextField(18);
        final JButton btnBrowseRoi = new JButton("…");

        MarkerRow(String name, int channel, Double prob, Double nms, boolean custom, File roiZip) {
            GridBagConstraints lc = new GridBagConstraints();
            lc.insets = new Insets(2,2,2,2);
            lc.anchor = GridBagConstraints.WEST;

            int col = 0;
            lc.gridx = col++; panel.add(cbSelect, lc);

            lc.gridx = col++; panel.add(new JLabel("Name:"), lc);
            lc.gridx = col++; tfName.setText(name != null ? name : "marker"); panel.add(tfName, lc);

            lc.gridx = col++; panel.add(new JLabel("Channel:"), lc);
            lc.gridx = col++; spChannel.setValue(channel); panel.add(spChannel, lc);

            lc.gridx = col++; panel.add(new JLabel("Prob (opt):"), lc);
            lc.gridx = col++; if (prob != null) spProb.setValue(prob); panel.add(spProb, lc);

            lc.gridx = col++; panel.add(new JLabel("NMS (opt):"), lc);
            lc.gridx = col++; if (nms != null) spNms.setValue(nms); panel.add(spNms, lc);

            lc.gridx = col++; cbCustom.setSelected(custom); panel.add(cbCustom, lc);
            lc.gridx = col++; tfRoiZip.setEnabled(custom); tfRoiZip.setText(roiZip != null ? roiZip.getAbsolutePath() : ""); panel.add(tfRoiZip, lc);
            lc.gridx = col;   btnBrowseRoi.addActionListener(e -> chooseFile(tfRoiZip, JFileChooser.FILES_ONLY));
            btnBrowseRoi.setEnabled(custom); panel.add(btnBrowseRoi, lc);

            cbCustom.addActionListener(e -> {
                boolean on = cbCustom.isSelected();
                tfRoiZip.setEnabled(on);
                btnBrowseRoi.setEnabled(on);
            });

            panel.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.setBorder(BorderFactory.createLineBorder(new Color(230,230,230)));
        }

        NeuronsMultiNoHuPipeline.MarkerSpec toSpec(double defProb, double defNms) {
            String nm = tfName.getText().trim();
            if (nm.isEmpty()) throw new IllegalArgumentException("Marker name cannot be empty.");
            int ch = ((Number) spChannel.getValue()).intValue();
            double p = ((Number) spProb.getValue()).doubleValue();
            double n = ((Number) spNms.getValue()).doubleValue();

            NeuronsMultiNoHuPipeline.MarkerSpec spec = new NeuronsMultiNoHuPipeline.MarkerSpec(nm, ch);
            if (p >= 0.0) spec.prob = p; // -1 means "use default"
            if (n >= 0.0) spec.nms  = n;

            if (cbCustom.isSelected()) {
                String z = tfRoiZip.getText().trim();
                if (z.isEmpty()) throw new IllegalArgumentException("Custom ROI selected for '"+nm+"' but no zip chosen.");
                spec.withCustomRois(new File(z));
            }
            return spec;
        }
    }

    // ---------------- Actions ----------------

    private void onRun(JButton runBtn) {
        runBtn.setEnabled(false);

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
        base.gangliaChannel = ((Number) spGangliaChannel.getValue()).intValue();
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
            mp.markers.add(r.toSpec(mp.multiProb, mp.multiNms));
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
        cbUseEdf.setSelected(false);

        spDefaultProb.setValue(0.50);
        spDefaultNms.setValue(0.30);
        spOverlapFrac.setValue(0.40);
        spMinMarkerSizeUm.setValue(160.0);

        cbSaveFlattenedOverlay.setSelected(true);

        cbGangliaAnalysis.setSelected(false);
        cbGangliaMode.setSelectedItem(Params.GangliaMode.DEEPIMAGEJ);
        spGangliaChannel.setValue(2);
        tfGangliaModelFolder.setText("2D_Ganglia_RGB_v3.bioimage.io.model");

        // one example row
        addMarkerRow("nNOS", 4, null, null, false, null);
        addMarkerRow("ChAT", 5, null, null, false, null);
    }
}
