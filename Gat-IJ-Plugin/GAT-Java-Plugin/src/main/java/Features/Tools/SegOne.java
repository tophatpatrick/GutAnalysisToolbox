// Features/Tools/SegOne.java
package Features.Tools;

import Features.Core.Params;
import Features.AnalyseWorkflows.NeuronsMultiPipeline;
import UI.panes.Tools.TuningTools;
import ij.ImagePlus;

import java.io.File;
import java.util.Locale;

public final class SegOne {

    // Hu rescale sweep
    public static TuningTools.Row runHuAtScale(ImagePlus max, int huCh, Params base, double trainingRescale, File outDir){
        Params p = (base != null) ? base.copy() : new Params();
        p.rescaleToTrainingPx   = true;
        p.trainingRescaleFactor = trainingRescale;

        ImagePlus ch = ImageOps.extractChannel(max, huCh);
        SegCommon.SegResult r  = SegCommon.segmentHuOne(ch, max, p, /*probOverride*/ null);
        File png = SegCommon.saveOverlay(max, r.rm, outDir, "tune_rescale_"+fmt(trainingRescale)+".png");
        cleanup(ch, r);
        return new TuningTools.Row(trainingRescale, r.count, png);
    }

    // Hu prob sweep
    public static TuningTools.Row runHuAtProb(ImagePlus max, int huCh, Params base, double trainingRescale, double prob, File outDir){
        Params p = (base != null) ? base.copy() : new Params();
        p.rescaleToTrainingPx   = true;
        p.trainingRescaleFactor = trainingRescale;

        ImagePlus ch = ImageOps.extractChannel(max, huCh);
        SegCommon.SegResult r  = SegCommon.segmentHuOne(ch, max, p, /*probOverride*/ prob);
        File png = SegCommon.saveOverlay(max, r.rm, outDir, "tune_hu_prob_"+fmt(prob)+".png");
        cleanup(ch, r);
        return new TuningTools.Row(prob, r.count, png);
    }

    // Subtype prob sweep (your version, just ensure mp.copy() exists)
    public static TuningTools.Row runSubtypeAtProb(ImagePlus max, int subtypeCh,
                                                   NeuronsMultiPipeline.MultiParams mp, double prob, File outDir){
        NeuronsMultiPipeline.MultiParams mpc = (mp != null) ? mp.copy() : new NeuronsMultiPipeline.MultiParams();
        mpc.multiProb = prob;

        // guard: segmentSubtypeOne requires base != null
        if (mpc.base == null)
            throw new IllegalArgumentException("MultiParams.base cannot be null for subtype sweep.");

        ImagePlus ch = ImageOps.extractChannel(max, subtypeCh);
        SegCommon.SegResult r  = SegCommon.segmentSubtypeOne(ch, max, mpc);
        File png = SegCommon.saveOverlay(max, r.rm, outDir, "tune_subtype_prob_"+fmt(prob)+".png");
        cleanup(ch, r);
        return new TuningTools.Row(prob, r.count, png);
    }

    // Ganglia expansion sweep (Hu ROIs → dilate µm → CC labels)
    public static TuningTools.Row runGangliaFromHuExpansion(ImagePlus max, int huCh, Params base, double um, File outDir){
        Params p = (base != null) ? base.copy() : new Params();

        // 1) segment Hu once using base
        ImagePlus ch = ImageOps.extractChannel(max, huCh);
        SegCommon.SegResult r  = SegCommon.segmentHuOne(ch, max, p, null);

        // 2) Preview labels by expansion (µm) → CC
        ImagePlus ganglia = SegCommon.gangliaByExpansionPreview(max, r.rm, um);

        // 3) Save preview; count CC
        int cc = SegCommon.countLabels(ganglia);
        File png = SegCommon.saveMaskOverlay(max, ganglia, outDir, "tune_ganglia_"+fmt(um)+"um.png");

        cleanup(ch, r);
        ganglia.changes=false; ganglia.close();
        return new TuningTools.Row(um, cc, png);
    }

    // --- tiny utilities
    private static void cleanup(ImagePlus ch, SegCommon.SegResult r){ ch.close(); r.dispose(); }
    private static String fmt(double d){ return String.format(Locale.US,"%.3f", d); }

    private SegOne() {}
}
