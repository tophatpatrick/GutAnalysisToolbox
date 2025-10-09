// UI/panes/Tools/ToolsPane.java
package UI.panes.Tools;

import UI.Handlers.Navigator;
import UI.util.GatSettings;
import Features.Core.Params;
import Features.AnalyseWorkflows.NeuronsMultiPipeline;
import ij.IJ;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;

/**
 * Tuning Tools pane (runs off the EDT to avoid UI freeze).
 * - Fresh defaults each click
 * - Prompts for missing model ZIPs (per run only)
 * - Lets user choose an output folder (defaults to ~/Analysis/Tuning)
 * - Long work runs in a SwingWorker; UI stays responsive
 */
public class ToolsPane extends JPanel {

    public static final String Name = "Tools";

    private final GatSettings settings;
    private final JButton btnRescale = new JButton("Test Rescaling (Hu)");
    private final JButton btnHuProb  = new JButton("Test Probability (Hu)");
    private final JButton btnSubProb = new JButton("Test Probability (Subtype)");
    private final JButton btnGanglia = new JButton("Test Ganglia expansion (µm)");
    private final JLabel  status     = new JLabel(" ");

    public ToolsPane(Navigator navigator) {
        this.settings = GatSettings.loadOrDefaults();

        setLayout(new GridLayout(0, 1, 8, 8));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel title = new JLabel("Tuning Tools (fresh defaults each run)", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        add(title);

        add(btnRescale);
        add(btnHuProb);
        add(btnSubProb);
        add(btnGanglia);

        status.setHorizontalAlignment(SwingConstants.CENTER);
        add(status);

        // Wire actions (each runs async)
        btnRescale.addActionListener(e -> startRescaleHu());
        btnHuProb.addActionListener(e -> startHuProb());
        btnSubProb.addActionListener(e -> startSubtypeProb());
        btnGanglia.addActionListener(e -> startGangliaSweep());
    }

    // -------------------- Actions (async) --------------------

    private void startRescaleHu() {
        Params base = defaultBaseParams();
        if (!ensureNeuronModelZip(base)) return;
        File outDir = ensureTuningDir(chooseOutDirOrDefault(null));
        runAsync("Rescaling (Hu)", () -> TuningTools.runRescaleSweep(base, outDir, settings));
    }

    private void startHuProb() {
        Params base = defaultBaseParams();
        if (!ensureNeuronModelZip(base)) return;
        File outDir = ensureTuningDir(chooseOutDirOrDefault(null));
        runAsync("Probability (Hu)", () -> TuningTools.runProbSweepHu(base, outDir, settings));
    }

    private void startSubtypeProb() {
        Params base = defaultBaseParams();
        if (!ensureNeuronModelZip(base)) return; // Hu context still needed
        NeuronsMultiPipeline.MultiParams mp = defaultMultiParams(base);
        if (!ensureSubtypeModelZip(mp)) return;
        File outDir = ensureTuningDir(chooseOutDirOrDefault(null));
        runAsync("Probability (Subtype)", () -> TuningTools.runProbSweepSubtype(mp, outDir, settings));
    }

    private void startGangliaSweep() {
        Params base = defaultBaseParams();
        if (!ensureNeuronModelZip(base)) return; // segments Hu first
        File outDir = ensureTuningDir(chooseOutDirOrDefault(null));
        runAsync("Ganglia expansion", () -> TuningTools.runGangliaExpansionSweep(base, outDir, settings));
    }

    // -------------------- Async runner -----------------------

    private void runAsync(String label, Runnable task) {
        setBusy(true, "Running: " + label + " …");
        SwingWorker<Void, Void> w = new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                try {
                    task.run();
                } catch (Throwable t) {
                    // show error on EDT
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                            ToolsPane.this,
                            "Error during " + label + ":\n" + t.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE));
                }
                return null;
            }
            @Override protected void done() {
                setBusy(false, "Done: " + label);
            }
        };
        w.execute();
    }

    private void setBusy(boolean busy, String msg) {
        setCursor(Cursor.getPredefinedCursor(busy ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
        btnRescale.setEnabled(!busy);
        btnHuProb.setEnabled(!busy);
        btnSubProb.setEnabled(!busy);
        btnGanglia.setEnabled(!busy);
        status.setText(msg != null ? msg : " ");
        revalidate();
        repaint();
    }

    // -------------------- Defaults per click -----------------

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

        // optional ganglia/spatial defaults
        p.gangliaInteractiveReview = true;
        p.gangliaOpenIterations = 3;
        p.gangliaMinAreaUm2 = 200.0;
        p.spatialExpansionUm = 6.5;
        p.spatialSaveParametric = false;
        return p;
    }

    private static NeuronsMultiPipeline.MultiParams defaultMultiParams(Params base) {
        NeuronsMultiPipeline.MultiParams mp = new NeuronsMultiPipeline.MultiParams();
        mp.base = base;
        mp.multiProb = 0.50;
        mp.multiNms  = 0.30;
        mp.overlapFrac = 0.40;
        return mp;
    }

    // -------------------- Model prompts ----------------------

    private boolean ensureNeuronModelZip(Params p) {;
        p.stardistModelZip =  new File(new File(IJ.getDirectory("imagej"), "models"), "2D_enteric_neuron_subtype_v4.zip").getAbsolutePath();
        return true;
    }

    private boolean ensureSubtypeModelZip(NeuronsMultiPipeline.MultiParams mp) {
        mp.subtypeModelZip  = new File(new File(IJ.getDirectory("imagej"), "models"), "2D_enteric_neuron_subtype_v4.zip").getAbsolutePath();
        return true;
    }

    private static String pickZipFile(String title) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(title);
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setMultiSelectionEnabled(false);
        fc.setFileFilter(new FileNameExtensionFilter("StarDist model (*.zip)", "zip"));
        int ret = fc.showOpenDialog(null);
        return (ret == JFileChooser.APPROVE_OPTION) ? fc.getSelectedFile().getAbsolutePath() : null;
    }

    // -------------------- Output folder ----------------------

    private static File chooseOutDirOrDefault(File preselect) {
        JFileChooser fc = new JFileChooser(preselect);
        fc.setDialogTitle("Choose output folder (optional)");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setMultiSelectionEnabled(false);
        int ret = fc.showOpenDialog(null);
        if (ret == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            if (f != null) return f;
        }
        return new File(System.getProperty("user.home"), "Analysis");
    }

    private static File ensureTuningDir(File parent) {
        File base = (parent != null) ? parent : new File(System.getProperty("user.home"), "Analysis");
        File tuning = new File(base, "Tuning");
        if (!tuning.isDirectory()) tuning.mkdirs();
        return tuning;
    }
}
