package UI.panes.Tools;

import Features.Core.Params;
import Features.AnalyseWorkflows.NeuronsMultiPipeline;
import Features.Tools.SegOne;
import UI.util.GatSettings;
import ij.IJ;
import ij.ImagePlus;

import javax.swing.*;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class TuningTools {

    public static final class Row {
        public final double x;
        public final int    count;
        public final File   preview;
        public Row(double x, int count, File preview){ this.x=x; this.count=count; this.preview=preview; }
        @Override public String toString(){ return String.format(java.util.Locale.US, "%.3f  —  %d", x, count); }
    }

    private static ImagePlus requireMax() {
        ImagePlus imp = IJ.getImage();
        if (imp == null) throw new IllegalStateException("Open or activate the MAX image first.");
        return imp;
    }
    private static File tuningDir(File outDir){
        File t = new File(outDir, "Tuning");
        if (!t.exists()) t.mkdirs();
        return t;
    }

    // ---- Hu rescale sweep ----
    public static void runRescaleSweep(Params base, File outDir, GatSettings s){
        ImagePlus max = requireMax();
        int huCh = base.huChannel;
        double[] grid = new double[]{0.50, 0.75, 1.00, 1.25};
        List<Row> rows = new ArrayList<>();
        for (double g : grid) rows.add(SegOne.runHuAtScale(max, huCh, base, g, tuningDir(outDir)));

        Row pick = pick("Pick best Hu rescale", rows);
        if (pick != null) {
            s.setHuTrainingRescale(pick.x);
            saveConfigDialog(s, outDir.toPath(), "tuning_hu_rescale.properties");
        }
    }

    // ---- Hu probability sweep ----
    public static void runProbSweepHu(Params base, File outDir, GatSettings s){
        ImagePlus max = requireMax();
        int huCh = base.huChannel;
        double tr = (s.huTrainingRescale != null) ? s.huTrainingRescale :
                (base.trainingRescaleFactor > 0 ? base.trainingRescaleFactor : 1.0);
        double[] grid = new double[]{0.35, 0.45, 0.50, 0.60};
        List<Row> rows = new ArrayList<>();
        for (double p : grid) rows.add(SegOne.runHuAtProb(max, huCh, base, tr, p, tuningDir(outDir)));

        Row pick = pick("Pick best Hu probability", rows);
        if (pick != null) {
            s.setHuProb(pick.x);
            saveConfigDialog(s, outDir.toPath(), "tuning_hu_prob.properties");
        }
    }

    // ---- Subtype probability sweep ----
    public static void runProbSweepSubtype(NeuronsMultiPipeline.MultiParams mp, File outDir, GatSettings s){
        ImagePlus max = requireMax();
        if (mp == null || mp.markers.isEmpty()) {
            JOptionPane.showMessageDialog(null, "No subtype markers configured.");
            return;
        }
        int ch = mp.markers.get(0).channel;
        double[] grid = new double[]{0.35, 0.45, 0.50, 0.60};
        List<Row> rows = new ArrayList<>();
        for (double p : grid) rows.add(SegOne.runSubtypeAtProb(max, ch, mp, p, tuningDir(outDir)));

        Row pick = pick("Pick best Subtype probability (default)", rows);
        if (pick != null) {
            s.subtypeProb = pick.x;
            saveConfigDialog(s, outDir.toPath(), "tuning_subtype_prob.properties");
        }
    }

    // ---- Ganglia expansion (µm) ----
    public static void runGangliaExpansionSweep(Params base, File outDir, GatSettings s){
        ImagePlus max = requireMax();
        int huCh = base.huChannel;
        double[] grid = new double[]{6.5, 10.0, 12.0, 15.0};
        List<Row> rows = new ArrayList<>();
        for (double um : grid) rows.add(SegOne.runGangliaFromHuExpansion(max, huCh, base, um, tuningDir(outDir)));

        Row pick = pick("Pick ganglia expansion (µm)", rows);
        if (pick != null) {
            s.setGangliaExpandUm(pick.x);
            saveConfigDialog(s, outDir.toPath(), "tuning_ganglia.properties");
        }
    }

    // ------- helpers -------
    private static Row pick(String title, List<Row> rows){
        Row[] arr = rows.toArray(new Row[0]);
        return (Row) JOptionPane.showInputDialog(
                null, "Choose the setting that looks best (previews saved in Tuning/).",
                title, JOptionPane.QUESTION_MESSAGE, null, arr, arr[Math.max(0, arr.length/2)]);
    }

    private static void saveConfigDialog(GatSettings s, Path defaultDir, String suggestedName){
        JFileChooser fc = new JFileChooser(defaultDir.toFile());
        fc.setSelectedFile(new File(defaultDir.toFile(), suggestedName));
        int ret = fc.showSaveDialog(null);
        if (ret == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try {
                s.saveTo(f.toPath());
                JOptionPane.showMessageDialog(null,
                        "Saved config:\n" + f.getAbsolutePath() + "\n\nUse your existing 'Load Config' to apply.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null,
                        "Failed to save config:\n" + ex.getMessage(),
                        "Save error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private TuningTools(){}
}
