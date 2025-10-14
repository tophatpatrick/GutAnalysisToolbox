package UI.panes.Tools;

import Features.Core.Params;
import Features.AnalyseWorkflows.NeuronsMultiPipeline;
import Features.Tools.SegOne;
import UI.panes.Tools.dialogs.GangliaExpansionDialog;
import UI.panes.Tools.dialogs.ProbabilityDialog;
import UI.panes.Tools.dialogs.RescaleHuDialog;
import UI.util.GatSettings;
import ij.IJ;
import ij.ImagePlus;

import javax.swing.*;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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


    public static void runRescaleSweep(Params base,
                                       File outDir,
                                       GatSettings settings,
                                       RescaleHuDialog.Config cfg) {

        ImagePlus imp = null;
        boolean closeImp = false;

        try {
            // Open or use active image

            imp = Features.Core.PluginCalls.openWithBioFormats(cfg.imageFile.getAbsolutePath());
            closeImp = true;


            // Build MAX (or EDF if base.useClij2EDF is true)
            ImagePlus max = toMaxProjection(imp, base);

            // Use dialog’s prob/overlap for this run
            base.probThresh = cfg.prob;
            base.nmsThresh  = cfg.overlap;

            // Sweep rescale factors
            File sweepDir = ensureSweepDir(outDir);
            java.util.List<Row> rows = new java.util.ArrayList<Row>();
            for (double f = cfg.rescaleMin; f <= cfg.rescaleMax + 1e-9; f += cfg.rescaleStep) {
                rows.add(SegOne.runHuAtScale(max, cfg.channel, base, round3(f), sweepDir));
            }

            // Let user pick a winner and (optionally) save a config for later loading
            Row pick = pick("Pick best Hu rescale", rows);
            if (pick != null) {
                settings.setHuTrainingRescale(pick.x);
                saveConfigDialog(settings, new File(outDir, "Tuning").toPath(), "tuning_hu_rescale.properties");
            }

            // Always write a small CSV summary
            saveRowsCsv(rows, new File(sweepDir, "hu_rescale_sweep.csv"));

            // tidy
            if (max != null) { max.changes = false; max.close(); }

        } finally {
            if (closeImp && imp != null) { imp.changes = false; imp.close(); }
        }
    }

    private static File ensureSweepDir(File outDir) {
        File t = new File(outDir, "Tuning");
        if (!t.isDirectory()) t.mkdirs();
        return t;
    }

    private static double round3(double d){ return Math.round(d*1000.0)/1000.0; }

    private static ImagePlus toMaxProjection(ImagePlus src, Params base) {
        if (src == null) throw new IllegalArgumentException("Image is null");
        if (src.getNSlices() <= 1) return src.duplicate();
        // If you want to honor EDF toggle, call your CLIJ2 helper when base.useClij2EDF is true
        if (base.useClij2EDF) {
            return Features.Core.PluginCalls.clij2EdfVariance(src);
        } else {
            src.show();
            IJ.run("Z Project...", "projection=[Max Intensity]");
            ImagePlus out = IJ.getImage();
            out.hide();
            return out;
        }
    }

    private static void saveRowsCsv(List<Row> rows, File csv) {
        try {
            java.io.PrintWriter pw = new java.io.PrintWriter(csv, "UTF-8");
            try {
                pw.println("x,count,preview");
                for (Row r : rows) {
                    String path = (r.preview != null) ? r.preview.getAbsolutePath().replace('\\','/') : "";
                    pw.println(String.format(Locale.US, "%.3f,%d,%s", r.x, r.count, path));
                }
            } finally { pw.close(); }
        } catch (Exception ignore) { }
    }

    // in UI/panes/Tools/TuningTools.java  (additions only)



// ... keep existing class and methods ...

    public static void runProbSweep(Features.Core.Params base,
                                    java.io.File unusedOutDir,
                                    UI.util.GatSettings s,
                                    UI.panes.Tools.dialogs.ProbabilityDialog.Config cfg) {
        ij.ImagePlus imp =  Features.Core.PluginCalls.openWithBioFormats(cfg.imageFile.getAbsolutePath());
        if (imp == null) throw new IllegalStateException("No image available.");
        boolean closeImp = true;

        try {
            // fix base thresholds used during sweep
            base.rescaleToTrainingPx = true;
            base.trainingRescaleFactor = cfg.rescaleFactor;
            base.nmsThresh = cfg.overlap;

            ij.ImagePlus max = toMaxProjection(imp, base);

            java.util.List<Row> rows = new java.util.ArrayList<>();
            java.io.File tdir = ensureSweepDir(cfg.outDir);

            for (double p = cfg.probMin; p <= cfg.probMax + 1e-12; p += cfg.probStep) {
                double pp = round3(p);
                if (cfg.mode == UI.panes.Tools.dialogs.ProbabilityDialog.Mode.NEURON) {
                    rows.add(Features.Tools.SegOne.runHuAtProb(max, cfg.channel, base,
                            cfg.rescaleFactor, pp, tdir));
                } else {
                    Features.AnalyseWorkflows.NeuronsMultiPipeline.MultiParams mp =
                            new Features.AnalyseWorkflows.NeuronsMultiPipeline.MultiParams();
                    mp.base = base;
                    mp.multiProb = pp;            // sweeping this
                    mp.multiNms  = cfg.overlap;   // fixed
                    mp.subtypeModelZip = cfg.modelZip.getAbsolutePath();

                    rows.add(Features.Tools.SegOne.runSubtypeAtProb(max, cfg.channel, mp, pp, tdir));
                }
            }

            Row pick = pick("Pick best probability", rows);
            if (pick != null) {
                if (cfg.mode == UI.panes.Tools.dialogs.ProbabilityDialog.Mode.NEURON) s.setHuProb(pick.x);
                else s.subtypeProb = pick.x;
                saveConfigDialog(s, cfg.outDir.toPath(), "tuning_probability.properties");
            }
            saveRowsCsv(rows, new java.io.File(tdir, "prob_sweep.csv"));

        } finally {
            if (closeImp) { imp.changes=false; imp.close(); }
        }
    }

    // ---- Ganglia expansion sweep (uses Hu segmentation) ----
    public static void runGangliaExpansionSweep(Features.Core.Params base,
                                                java.io.File unusedOutDir,
                                                UI.util.GatSettings s,
                                                UI.panes.Tools.dialogs.GangliaExpansionDialog.Config cfg) {
        ij.ImagePlus imp = Features.Core.PluginCalls.openWithBioFormats(cfg.imageFile.getAbsolutePath());
        if (imp == null) throw new IllegalStateException("No image available.");
        boolean closeImp = true;

        try {
            ij.ImagePlus max = toMaxProjection(imp, base);
            java.util.List<Row> rows = new java.util.ArrayList<>();
            java.io.File tdir = ensureSweepDir(cfg.outDir);

            for (double um = cfg.minUm; um <= cfg.maxUm + 1e-12; um += cfg.stepUm) {
                double uu = round3(um);
                rows.add(Features.Tools.SegOne.runGangliaFromHuExpansion(max, base.huChannel, base, uu, tdir));
            }

            Row pick = pick("Pick ganglia expansion (µm)", rows);
            if (pick != null) {
                s.setGangliaExpandUm(pick.x);
                saveConfigDialog(s, cfg.outDir.toPath(), "tuning_ganglia.properties");
            }
            saveRowsCsv(rows, new java.io.File(tdir, "ganglia_expand_sweep.csv"));

        } finally {
            if (closeImp) { imp.changes=false; imp.close(); }
        }
    }




    private TuningTools(){}
}
