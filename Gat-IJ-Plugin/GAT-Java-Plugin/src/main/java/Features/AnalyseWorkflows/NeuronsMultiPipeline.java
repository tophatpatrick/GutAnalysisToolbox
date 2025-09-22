package Features.AnalyseWorkflows;

import Features.Core.Params;
import Features.Tools.*;
import UI.panes.Tools.ReviewUI;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import static Features.Tools.RoiManagerHelper.*;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NeuronsMultiPipeline {

    // ----- Input spec ---------------------------------------------------------
    public static final class MarkerSpec {
        public final String name;
        public final int channel;      // 1-based
        public Double prob;            // optional
        public Double nms;             // optional

        // ADD ↓↓↓
        public File customRoisZip;     // optional: user-supplied ROI zip

        public MarkerSpec(String name, int channel) {
            this.name = name;
            this.channel = channel;
        }
        public MarkerSpec withThresh(Double prob, Double nms) {
            this.prob = prob; this.nms = nms; return this;
        }
        // ADD ↓↓↓
        public MarkerSpec withCustomRois(File zip) {
            this.customRoisZip = zip;
            return this;
        }
    }

    public static final class MultiParams {
        public Params base;                          // your existing Params (Hu + ganglia config)
        public String subtypeModelZip;               // StarDist model zip for subtype channels
        public double multiProb   = 0.50;            // default StarDist thresholds for subtype
        public double multiNms    = 0.30;
        public double overlapFrac = 0.40;            // Hu label must be >= this fraction covered by marker

        public final List<MarkerSpec> markers = new ArrayList<>();
    }

    // ----- Output / result for the multi-stage UI -----------------------------
    public static final class MultiResult {
        public final File outDir;
        public final String baseName;
        public final ImagePlus max;                 // preview / thumbnails
        public final int totalHu;                   // total Hu neurons
        public final Integer nGanglia;              // may be null
        public final double[] gangliaAreaUm2;       // may be null
        public final ImagePlus gangliaLabels;       // may be null

        // marker or combo name -> total Hu-gated neuron count
        public final LinkedHashMap<String,Integer> totals;

        // marker or combo name -> neurons-per-ganglion array (1..G)
        public final LinkedHashMap<String,int[]> perGanglia;

        public MultiResult(File outDir,
                           String baseName,
                           ImagePlus max,
                           int totalHu,
                           Integer nGanglia,
                           double[] gangliaAreaUm2,
                           ImagePlus gangliaLabels,
                           LinkedHashMap<String,Integer> totals,
                           LinkedHashMap<String,int[]> perGanglia) {
            this.outDir = outDir;
            this.baseName = baseName;
            this.max = max;
            this.totalHu = totalHu;
            this.nGanglia = nGanglia;
            this.gangliaAreaUm2 = gangliaAreaUm2;
            this.gangliaLabels = gangliaLabels;
            this.totals = totals;
            this.perGanglia = perGanglia;
        }
    }


    // ----- Run ----------------------------------------------------------------
    public void run(MultiParams mp) {
        if (mp == null || mp.base == null) throw new IllegalArgumentException("MultiParams/base cannot be null");
        if (mp.subtypeModelZip == null || !new File(mp.subtypeModelZip).isFile())
            throw new IllegalArgumentException("Subtype StarDist model not found: " + mp.subtypeModelZip);
        if (mp.markers.isEmpty()) throw new IllegalArgumentException("No markers provided.");



        final int perMarkerSteps = 4;
        final int comboSteps     = 1;
        final int nm              = mp.markers.size();
        final int nCombos        = (nm * (nm - 1)) / 2;

        // total = Hu + per-marker + combos + 1(final CSV)
        int totalSteps = NeuronsHuPipeline.estimateSteps(mp.base)
                + (perMarkerSteps * nm)
                + (comboSteps * nCombos)
                + 1;

        ProgressUI progress = new ProgressUI("Hu + Multi-channel");
        progress.start(totalSteps);

        // 1) Run Hu once (returns MAX, Hu labels, ganglia info)
        NeuronsHuPipeline.HuResult hu = new NeuronsHuPipeline().run(mp.base, /*huReturn=*/true, progress);
        ImagePlus max    = hu.max;
        ImagePlus huLab  = hu.neuronLabels;
        int       totalHu = hu.totalNeuronCount;


        // 2) Common bits for rescale math
        double pxUm = max.getCalibration().pixelWidth;
        double scale = (mp.base.trainingRescaleFactor > 0) ? mp.base.trainingRescaleFactor : 1.0;
        double targetPxUm = mp.base.trainingPixelSizeUm / scale;
        double scaleFactor = (mp.base.rescaleToTrainingPx && pxUm > 0) ? (pxUm / targetPxUm) : 1.0;
        if (Math.abs(scaleFactor - 1.0) < 1e-3) scaleFactor = 1.0;

        // min size for subtype sanity (macro neuron_lower_limit in microns → pixels in segInput scale)
        int subtypeMinPx = 0;
        if (mp.base.neuronSegMinMicron != null && pxUm > 0) {
            double eff = (scaleFactor == 1.0) ? pxUm : (targetPxUm); // segInput calibration after rescale
            subtypeMinPx = (int)Math.max(1, Math.round(mp.base.neuronSegMinMicron / eff));
        }

        // 3) Collect results
        File outDir = hu.outDir; String baseName = hu.baseName;
        LinkedHashMap<String,Integer> totals = new LinkedHashMap<>();
        LinkedHashMap<String,int[]>   perGanglia = new LinkedHashMap<>();

        double[] gangliaArea = hu.gangliaAreaUm2;       // may be null
        Integer  nGanglia    = hu.nGanglia;             // may be null

        // For combos later
        Map<String, boolean[]> keepMaskByMarker = new LinkedHashMap<>();

        RmHandle rmh = ensureGlobalRM();
        RoiManager rm = rmh.rm;
        rm.setVisible(false);

        // 4) Loop each marker

        for (MarkerSpec m : mp.markers) {
            ij.macro.Interpreter.batchMode = true;
            progress.step("Prep: " + m.name);
            ImagePlus ch = ImageOps.extractChannel(max, m.channel);
            ImagePlus segInput = (scaleFactor == 1.0)
                    ? ch
                    : ImageOps.resizeToIntensity(ch,
                    (int)Math.round(ch.getWidth() * scaleFactor),
                    (int)Math.round(ch.getHeight() * scaleFactor));

            progress.pulse("Segment: " + m.name);
            ImagePlus markerLabels;
            if (m.customRoisZip != null && m.customRoisZip.isFile()) {
                // ---- Use user-supplied ROI ZIP instead of StarDist ----
                RoiManager tmp = rmh.rm;
                tmp.reset();
                tmp.runCommand("Open", m.customRoisZip.getAbsolutePath());

                // If you have helpers:
                //   bin = Features.Core.PluginCalls.roisToBinary(max, tmp);
                //   markerLabels = Features.Core.PluginCalls.binaryToLabels(bin);

                // Minimal fallback (if you don't have roisToBinary):
                ij.process.ByteProcessor bp = new ij.process.ByteProcessor(max.getWidth(), max.getHeight());
                bp.setValue(255);
                for (ij.gui.Roi r : tmp.getRoisAsArray()) {
                    bp.setRoi(r);
                    bp.fill(); // fill ROI area
                }
                ImagePlus bin = new ImagePlus("bin", bp);
                markerLabels = Features.Core.PluginCalls.binaryToLabels(bin);
                bin.close();

                tmp.reset(); tmp.close();

            } else {
                // ---- StarDist path (your original code) ----
                double prob = (m.prob != null) ? m.prob : mp.multiProb;
                double nms  = (m.nms  != null) ? m.nms  : mp.multiNms;

                markerLabels = Features.Core.PluginCalls.runStarDist2DLabel(segInput, mp.subtypeModelZip, prob, nms);
                markerLabels = Features.Core.PluginCalls.removeBorderLabels(markerLabels);
                if (subtypeMinPx > 0) markerLabels = Features.Core.PluginCalls.labelMinSizeFilterPx(markerLabels, subtypeMinPx);
            }


            if (subtypeMinPx > 0) markerLabels = Features.Core.PluginCalls.labelMinSizeFilterPx(markerLabels, subtypeMinPx);
            progress.stopPulse("Segment done: " + m.name);
            progress.step("Postprocess/resize: " + m.name);
            if (markerLabels.getWidth() != max.getWidth() || markerLabels.getHeight() != max.getHeight()) {
                markerLabels = ImageOps.resizeTo(markerLabels, max.getWidth(), max.getHeight());
            }

            // Determine which Hu labels are positive for this marker (fractional overlap >= overlapFrac)
            boolean[] keep = Features.Tools.LabelOps.neuronsPositiveByOverlap(huLab, markerLabels, mp.overlapFrac);
            keepMaskByMarker.put(m.name, keep);

            // Build filtered Hu label map for this marker (for ROI export / ganglia counts)
            ImagePlus filteredLabels = Features.Tools.LabelOps.keepHuLabels(huLab, keep);


            // Seed RM with current Hu-gated labels
            rm.reset();
            Features.Core.PluginCalls.labelsToRois(filteredLabels);
            syncToSingleton(new RoiManager[]{ rm });

            //Build our backdrop
            ImagePlus backdrop = ch.duplicate();
            IJ.run(backdrop, "Green", "");
            IJ.resetMinAndMax(backdrop);

            progress.step("Review: " + m.name);
            // Launch review and rebuild labels from edited ROIs
            ij.macro.Interpreter.batchMode = false;
            ImagePlus reviewed = ReviewUI.reviewAndRebuildLabels(
                    backdrop,
                    rm,
                    m.name + " (Hu-gated)",
                    max.getCalibration(),
                    filteredLabels
            );
            ij.macro.Interpreter.batchMode = true;

            progress.step("Save: " + m.name);
            // Count + save ROIs
            int markerTotal = countLabels(reviewed);
            totals.put(m.name, markerTotal);



            rm.reset();
            Features.Core.PluginCalls.labelsToRois(reviewed);
            syncToSingleton(new RoiManager[]{ rm });
            if (rm.getCount() > 0) {
                OutputIO.saveRois(rm, new File(outDir, m.name + "_ROIs_" + baseName + ".zip"));
                if (mp.base.saveFlattenedOverlay)
                    OutputIO.saveFlattenedOverlay(max, rm, new File(outDir, "MAX_" + baseName + "_" + m.name + "_overlay.tif"));
            }
            rm.reset();
            rm.setVisible(false);

            // Per-ganglion counts if available
            if (hu.gangliaLabels != null) {
                GangliaOps.Result rM = GangliaOps.countPerGanglion(reviewed, hu.gangliaLabels);
                perGanglia.put(m.name, rM.countsPerGanglion);
            }

            // cleanup
            ch.close(); segInput.close(); markerLabels.close(); reviewed.close();
            filteredLabels.close();
        }

        // 5) Build combos (AND of keep arrays)
        List<String> names = new ArrayList<>(keepMaskByMarker.keySet());
        for (int i = 0; i < names.size(); i++) {
            for (int j = i+1; j < names.size(); j++) {
                String comboName = names.get(i) + "+" + names.get(j);
                boolean[] a = keepMaskByMarker.get(names.get(i));
                boolean[] b = keepMaskByMarker.get(names.get(j));
                boolean[] and = andMasks(a, b);
                ImagePlus lab = LabelOps.keepHuLabels(huLab, and);
                int n = countLabels(lab);
                totals.put(comboName, n);

                if (hu.gangliaLabels != null) {
                    GangliaOps.Result rc = GangliaOps.countPerGanglion(lab, hu.gangliaLabels);
                    perGanglia.put(comboName, rc.countsPerGanglion);
                }
                progress.step("Save combo: " + comboName);
                rm.reset();
                Features.Core.PluginCalls.labelsToRois(lab);
                syncToSingleton(new RoiManager[]{ rm });
                if (rm.getCount() > 0) {
                    OutputIO.saveRois(rm, new File(outDir, comboName + "_ROIs_" + baseName + ".zip"));
                    if (mp.base.saveFlattenedOverlay)
                        OutputIO.saveFlattenedOverlay(max, rm, new File(outDir, "MAX_" + baseName + "_" + comboName + "_overlay.tif"));
                }
                rm.reset();
                rm.setVisible(false);
                lab.close();
            }
        }

        // 6) Write the macro-style multi CSV
        OutputIO.writeMultiCsv(
                new File(outDir, "Analysis_Hu_" + baseName + "_cell_counts_multi.csv"),
                baseName,
                totalHu,
                nGanglia,
                totals,
                perGanglia,
                gangliaArea
        );

        progress.close();

        MultiResult mr = new MultiResult(
                outDir, baseName, max, totalHu,
                nGanglia, gangliaArea, hu.gangliaLabels,
                totals, perGanglia
        );

        RoiManager rmRev = rmh.rm;
        rmRev.setVisible(false);
        rmRev.reset();
        maybeCloseRM(rmh);


        SwingUtilities.invokeLater(() -> UI.panes.Results.ResultsMultiUI.promptAndMaybeShow(mr));
    }

    private static boolean[] andMasks(boolean[] a, boolean[] b) {
        int n = Math.max(a.length, b.length);
        boolean[] out = new boolean[n];
        for (int i = 0; i < n; i++) {
            boolean ai = (i < a.length) && a[i];
            boolean bi = (i < b.length) && b[i];
            out[i] = ai && bi;
        }
        return out;
    }





    private static int countLabels(ImagePlus labels16) {
        // Label map with values 0..K; just find max label ID (they’re contiguous after binary re-label)
        short[] px = (short[]) labels16.getProcessor().getPixels();
        int max = 0;
        for (short v : px) {
            int u = v & 0xFFFF;
            if (u > max) max = u;
        }
        return max;
    }
}
