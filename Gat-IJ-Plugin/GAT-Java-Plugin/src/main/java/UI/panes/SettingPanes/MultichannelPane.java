package UI.panes.SettingPanes;

import Features.AnalyseWorkflows.NeuronsMultiPipeline;
import Features.Core.Params;
import ij.IJ;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MultichannelPane extends JPanel {
    public static final String Name = "Multiplex Workflow";

    private final Window owner;

    // Reuse Hu basics
    private JTextField tfImagePath, tfOutputDir;
    private JButton btnBrowseImage, btnBrowseOutput;

    private JSpinner spHuChannel;
    private JTextField tfHuModelZip;

    // Subtype model + overlap
    private JTextField tfSubtypeModelZip;
    private JSpinner spOverlapFrac, spSubtypeProb, spSubtypeNms;

    // Markers (macro-style entry)
    private JTextField tfMarkerNames;    // e.g., "nNOS,ChAT,VIP"
    private JTextField tfMarkerChannels; // e.g., "2,1,4"  (1-based)

    // Ganglia + rescale etc. (subset for brevity)
    private JCheckBox cbGangliaAnalysis;
    private JComboBox<Params.GangliaMode> cbGangliaMode;
    private JSpinner spGangliaChannel;

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
        tabs.addTab("Advanced", buildAdvanced());
        add(tabs, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton runBtn = new JButton("Run Multiplex Analysis");
        runBtn.addActionListener(e -> onRun(runBtn));
        actions.add(runBtn);
        add(actions, BorderLayout.SOUTH);

        loadDefaults();
    }

    private JPanel buildBasic() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        tfImagePath = new JTextField(36);
        btnBrowseImage = new JButton("Browse…");
        btnBrowseImage.addActionListener(e -> chooseFile(tfImagePath, JFileChooser.FILES_ONLY));
        p.add(box("Input image", row(tfImagePath, btnBrowseImage)));

        spHuChannel = new JSpinner(new SpinnerNumberModel(3,1,32,1));
        tfHuModelZip = new JTextField(36);
        JButton btnBrowseHu = new JButton("Browse…");
        btnBrowseHu.addActionListener(e -> chooseFile(tfHuModelZip, JFileChooser.FILES_ONLY));
        p.add(box("Hu channel & model", grid2(
                new JLabel("Hu channel (1-based):"), spHuChannel,
                new JLabel("Hu StarDist model (.zip):"), row(tfHuModelZip, btnBrowseHu)
        )));

        tfSubtypeModelZip = new JTextField(36);
        JButton btnBrowseSubtype = new JButton("Browse…");
        btnBrowseSubtype.addActionListener(e -> chooseFile(tfSubtypeModelZip, JFileChooser.FILES_ONLY));
        spSubtypeProb = new JSpinner(new SpinnerNumberModel(0.50, 0.0, 1.0, 0.05));
        spSubtypeNms  = new JSpinner(new SpinnerNumberModel(0.30, 0.0, 1.0, 0.05));
        spOverlapFrac = new JSpinner(new SpinnerNumberModel(0.40, 0.0, 1.0, 0.05));
        p.add(box("Subtype model & overlap", grid2(
                new JLabel("Subtype StarDist model (.zip):"), row(tfSubtypeModelZip, btnBrowseSubtype),
                new JLabel("Subtype prob:"), spSubtypeProb,
                new JLabel("Subtype NMS:"),  spSubtypeNms,
                new JLabel("Hu/marker overlap fraction:"), spOverlapFrac
        )));

        tfMarkerNames    = new JTextField(36);  // "nNOS,ChAT"
        tfMarkerChannels = new JTextField(36);  // "2,1"
        p.add(box("Markers", grid2(
                new JLabel("Marker names (comma-separated):"), tfMarkerNames,
                new JLabel("Marker channels (comma-separated):"), tfMarkerChannels
        )));

        tfOutputDir = new JTextField(36);
        btnBrowseOutput = new JButton("Browse…");
        btnBrowseOutput.addActionListener(e -> chooseFile(tfOutputDir, JFileChooser.DIRECTORIES_ONLY));
        cbSaveOverlay = new JCheckBox("Save flattened overlays");
        p.add(box("Output", column(row(tfOutputDir, btnBrowseOutput), cbSaveOverlay)));

        cbGangliaAnalysis = new JCheckBox("Run ganglia analysis");
        cbGangliaMode = new JComboBox<>(Params.GangliaMode.values());
        spGangliaChannel = new JSpinner(new SpinnerNumberModel(2,1,32,1));
        p.add(box("Ganglia", column(
                cbGangliaAnalysis,
                row(new JLabel("Mode:"), cbGangliaMode),
                row(new JLabel("Ganglia channel (1-based):"), spGangliaChannel)
        )));

        p.add(Box.createVerticalGlue());
        return p;
    }

    private JPanel buildAdvanced() {
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

        p.add(Box.createVerticalGlue());
        return p;
    }

    private void onRun(JButton runBtn) {
        runBtn.setEnabled(false);
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
        p.gangliaModelFolder = "2D_Ganglia_RGB_v3.bioimage.io.model";

        return p;
    }

    private NeuronsMultiPipeline.MultiParams buildMultiParams(Params base) {
        NeuronsMultiPipeline.MultiParams mp = new NeuronsMultiPipeline.MultiParams();
        mp.base = base;
        mp.subtypeModelZip = tfSubtypeModelZip.getText();
        mp.multiProb   = ((Number)spSubtypeProb.getValue()).doubleValue();
        mp.multiNms    = ((Number)spSubtypeNms.getValue()).doubleValue();
        mp.overlapFrac = ((Number)spOverlapFrac.getValue()).doubleValue();

        String[] names = splitCsv(tfMarkerNames.getText());
        int[] chans    = parseIntCsv(tfMarkerChannels.getText());
        if (names.length != chans.length)
            throw new IllegalArgumentException("Markers: name count ("+names.length+") must match channel count ("+chans.length+").");

        for (int i=0; i<names.length; i++) {
            mp.markers.add(new NeuronsMultiPipeline.MarkerSpec(names[i], chans[i]));
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

        tfMarkerNames.setText("nNOS,ChAT");
        tfMarkerChannels.setText("2,1");

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
