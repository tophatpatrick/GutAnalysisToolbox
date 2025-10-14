// UI/panes/Tools/ToolsPane.java
package UI.panes.Tools;

import UI.Handlers.Navigator;
import UI.panes.Tools.dialogs.RescaleHuDialog;
import UI.util.GatSettings;
import Features.Core.Params;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;

public class ToolsPane extends JPanel {

    public static final String Name = "Tools";

    private final GatSettings settings;
    private final JButton btnRescale = new JButton("Test Rescaling");
    private final JButton btnProb    = new JButton("Test Probability");
    private final JButton btnGanglia = new JButton("Test Ganglia expansion (µm)");
    private final JLabel  status     = new JLabel(" ");

    public ToolsPane(Navigator navigator) {
        this.settings = GatSettings.loadOrDefaults();

        setLayout(new GridLayout(0, 1, 8, 8));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel title = new JLabel("Tuning Tools", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        add(title);

        add(btnRescale);
        add(btnProb);
        add(btnGanglia);

        status.setHorizontalAlignment(SwingConstants.CENTER);
        add(status);

        btnRescale.addActionListener(e -> startRescale());
        btnProb.addActionListener(e -> startProbability());
        btnGanglia.addActionListener(e -> startGanglia());
    }

    // ---------- Actions ----------
    private void startRescale() {
        Params base = defaultBaseParams();

        RescaleHuDialog dlg = new RescaleHuDialog(SwingUtilities.getWindowAncestor(this));
        RescaleHuDialog.Config cfg = dlg.showAndGet();
        if (cfg == null) return;

        // supply model (Hu) if you keep an ensure method
        ensureNeuronModelZip(base);

        base.probThresh = cfg.prob;
        base.nmsThresh  = cfg.overlap;
        base.huChannel  = cfg.channel;

        File outDir = ensureDir(cfg.outDir);

        runAsync("Rescaling", () ->
                TuningTools.runRescaleSweep(base, outDir, settings, cfg));
    }

    private static File ensureDir(File dir) {
        if (dir == null) {
            File def = new File(new File(System.getProperty("user.home"), "Analysis"), "Tuning");
            if (!def.isDirectory()) def.mkdirs();
            return def;
        }
        if (!dir.isDirectory()) dir.mkdirs();
        return dir;
    }

    private void startProbability() {
        Params base = defaultBaseParams();

        // open dialog
        UI.panes.Tools.dialogs.ProbabilityDialog dlg =
                new UI.panes.Tools.dialogs.ProbabilityDialog(SwingUtilities.getWindowAncestor(this));
        UI.panes.Tools.dialogs.ProbabilityDialog.Config cfg = dlg.showAndGet();
        if (cfg == null) return;

        // For NEURON probability we still need the neuron model
        if (cfg.mode == UI.panes.Tools.dialogs.ProbabilityDialog.Mode.NEURON) {
            if (!ensureNeuronModelZip(base)) return;
        }
        // For SUBTYPE probability, the dialog gives us modelZip in cfg; runner handles it.

        runAsync("Probability sweep", () ->
                TuningTools.runProbSweep(base, null, settings, cfg));
    }

    private void startGanglia() {
        Params base = defaultBaseParams();
        if (!ensureNeuronModelZip(base)) return; // Hu segmentation required

        UI.panes.Tools.dialogs.GangliaExpansionDialog dlg =
                new UI.panes.Tools.dialogs.GangliaExpansionDialog(SwingUtilities.getWindowAncestor(this));
        UI.panes.Tools.dialogs.GangliaExpansionDialog.Config cfg = dlg.showAndGet();
        if (cfg == null) return;

        runAsync("Ganglia expansion sweep", () ->
                TuningTools.runGangliaExpansionSweep(base, null, settings, cfg));
    }

    // ---------- Async plumbing ----------
    private void runAsync(String label, Runnable task) {
        setBusy(true, "Running: " + label + " … (this might take a while)");
        SwingWorker<Void, Void> w = new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                try { task.run(); } catch (Throwable t) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                            ToolsPane.this, "Error during " + label + ":\n" + t.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE));
                }
                return null;
            }
            @Override protected void done() { setBusy(false, "Done: " + label); }
        };
        w.execute();
    }
    private void setBusy(boolean b, String msg) {
        setCursor(Cursor.getPredefinedCursor(b ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
        btnRescale.setEnabled(!b);
        btnProb.setEnabled(!b);
        btnGanglia.setEnabled(!b);
        status.setText(msg != null ? msg : " ");
    }

    // ---------- Defaults ----------
    private static Params defaultBaseParams() {
        Params p = new Params();
        p.huChannel = 3;
        p.rescaleToTrainingPx = true;
        p.trainingPixelSizeUm = 0.568;
        p.trainingRescaleFactor = 1.0;
        p.probThresh = 0.50;
        p.nmsThresh  = 0.30;
        p.neuronSegMinMicron = 70.0;
        p.saveFlattenedOverlay = true;
        p.cellTypeName = "Neuron";
        p.gangliaInteractiveReview = true;
        p.gangliaOpenIterations = 3;
        p.gangliaMinAreaUm2 = 200.0;
        p.spatialExpansionUm = 6.5;
        p.spatialSaveParametric = false;
        return p;
    }

    private static boolean ensureNeuronModelZip(Params p) {
        // Point this at your Hu (neuron) StarDist model zip.
        // If your Hu model is "2D_enteric_neuron_V4_1.zip", use that.
        File modelsDir = new File(ij.IJ.getDirectory("imagej"), "models");
        File model = new File(modelsDir, "2D_enteric_neuron_V4_1.zip");
        if (!model.isFile()) {
            // fallback to subtype name if that's what you actually ship
            model = new File(modelsDir, "2D_enteric_neuron_subtype_v4.zip");
        }
        p.stardistModelZip = model.getAbsolutePath();
        return model.isFile();  // <— return true if we found a model
    }

    private static File chooseOutDirOrDefault(File preselect) {
        JFileChooser fc = new JFileChooser(preselect);
        fc.setDialogTitle("Choose output folder (optional)");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setMultiSelectionEnabled(false);
        int ret = fc.showOpenDialog(null);
        if (ret == JFileChooser.APPROVE_OPTION && fc.getSelectedFile() != null) return fc.getSelectedFile();
        return new File(System.getProperty("user.home"), "Analysis");
    }
    private static File ensureTuningDir(File parent) {
        File base = (parent != null) ? parent : new File(System.getProperty("user.home"), "Analysis");
        File tuning = new File(base, "Tuning");
        if (!tuning.isDirectory()) tuning.mkdirs();
        return tuning;
    }
}
