// Features/AnalyseWorkflows/NeuronsMultiNoHuPipeline.java
package Features.AnalyseWorkflows;

import Features.Core.Params;
import Features.Core.PluginCalls;
import Features.Tools.ImageOps;
import Features.Tools.OutputIO;
import Features.Tools.ProgressUI;
import UI.panes.Tools.ReviewUI;
import ij.IJ;
import ij.ImagePlus;
import ij.plugin.frame.RoiManager;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static Features.Tools.RoiManagerHelper.*;

public class NeuronsMultiNoHuPipeline {

    // ------- spec & params -------
    public static final class MarkerSpec {
        public final String name;
        public final int channel;     // 1-based in MAX composite
        public Double prob;           // optional StarDist override
        public Double nms;            // optional StarDist override
        public File customRoisZip;


        public MarkerSpec(String name, int channel) { this.name = name; this.channel = channel; }
        public MarkerSpec withThresh(Double prob, Double nms) { this.prob = prob; this.nms = nms; return this; }
        public MarkerSpec withCustomRois(File zip) { this.customRoisZip = zip; return this; }
    }



    public static final class MultiParams {
        public Params base;               // projection / rescale / ganglia options reused
        public String subtypeModelZip;    // StarDist model (ZIP) for subtype channels
        public double multiProb = 0.50;
        public double multiNms  = 0.30;
        public double overlapFrac = 0.40; // kept for parity; combos are hard AND here
        public final List<MarkerSpec> markers = new ArrayList<>();
    }

    public static int estimateSteps(MultiParams mp){

        int n = mp != null ? mp.markers.size() : 0;
        int nCombos = (n * (n - 1)) / 2;

        // Base: open + projection + rescale math
        int base = 3;

        // Ganglia (No-Hu path): run model, label/export ROIs, compute areas, cleanup
        if (mp != null && mp.base != null && mp.base.cellCountsPerGanglia) {
            base += 4;
        }

        // Per-marker: prep, segment, post/resize, review, save  (5 each)
        int perMarker = 5 * n;

        // Combos: build AND + save (2 each)
        int combos = 2 * nCombos;

        // Finalize: write CSV + save MAX/cleanup
        int tail = 2;

        return base + perMarker + combos + tail;
    }

    // ------- output / result for the No-Hu multi UI -------
    public static final class NoHuResult {
        public final File outDir;
        public final String baseName;
        public final ImagePlus max;                 // for thumbnails
        public final LinkedHashMap<String,Integer> totals;       // marker or combo -> total cells
        public final LinkedHashMap<String,int[]>   perGanglia;   // marker or combo -> counts per ganglion (1..G)
        public final Integer nGanglia;                              // null if ganglia not run
        public final double[] gangliaAreaUm2;                       // null if ganglia not run

        public NoHuResult(File outDir, String baseName, ImagePlus max,
                          LinkedHashMap<String,Integer> totals,
                          LinkedHashMap<String,int[]> perGanglia,
                          Integer nGanglia, double[] gangliaAreaUm2) {
            this.outDir = outDir;
            this.baseName = baseName;
            this.max = max;
            this.totals = totals;
            this.perGanglia = perGanglia;
            this.nGanglia = nGanglia;
            this.gangliaAreaUm2 = gangliaAreaUm2;
        }
    }



    // ------- run -------
    public void run(MultiParams mp) {
        if (mp == null || mp.base == null) throw new IllegalArgumentException("MultiParams/base cannot be null.");
        if (mp.subtypeModelZip == null || !new File(mp.subtypeModelZip).isFile())
            throw new IllegalArgumentException("Subtype StarDist model not found: " + mp.subtypeModelZip);
        if (mp.markers.isEmpty()) throw new IllegalArgumentException("Add at least one marker.");

        ij.macro.Interpreter.batchMode = true;
        ProgressUI progress = new ProgressUI("No-Hu multi-channel");
        progress.start(estimateSteps(mp));

        // 1) Open image & make MAX
        progress.step("Open image");
        ImagePlus imp = (mp.base.imagePath == null || mp.base.imagePath.isEmpty())
                ? IJ.getImage()
                : PluginCalls.openWithBioFormats(mp.base.imagePath);
        if (imp == null) throw new IllegalStateException("No image available to analyze.");

        final String baseName = stripExt(imp.getTitle());
        final File outDir = OutputIO.prepareOutputDir(mp.base.outputDir, imp, baseName);

        progress.step("Create projection");
        ImagePlus max = (imp.getNSlices() > 1)
                ? (mp.base.useClij2EDF ? PluginCalls.clij2EdfVariance(imp) : ImageOps.mip(imp))
                : imp.duplicate();
        max.setTitle("MAX_" + baseName);

        //create our global roi manager
        RmHandle rmh = ensureGlobalRM();

        // 2) Rescale math
        progress.step("Rescale math");
        final double pxUm = (max.getCalibration() != null && max.getCalibration().pixelWidth > 0)
                ? max.getCalibration().pixelWidth : 1.0;
        final double scale = (mp.base.trainingRescaleFactor > 0) ? mp.base.trainingRescaleFactor : 1.0;
        final double targetPxUm = mp.base.trainingPixelSizeUm / scale;
        double scaleFactor = (mp.base.rescaleToTrainingPx ? (pxUm / targetPxUm) : 1.0);
        if (Math.abs(scaleFactor - 1.0) < 1e-3) scaleFactor = 1.0;

        int minPx = 0; // min size in pixels at segmentation scale
        if (mp.base.neuronSegMinMicron != null && pxUm > 0) {
            double eff = (scaleFactor == 1.0) ? pxUm : targetPxUm;
            minPx = (int)Math.max(1, Math.round(mp.base.neuronSegMinMicron / eff));
        }



        // 2.5) Ganglia (once)
        ImagePlus gangliaLabels = null;
        double[] gangliaAreaUm2 = null;
        int  nGanglia = 0;

        if (mp.base.cellCountsPerGanglia) {
            progress.pulse("Ganglia: segment (" + mp.base.gangliaMode + ")");
            // No Hu labels in this pipeline → pass null for neuronLabels
            ImagePlus gangliaOut = GangliaOps.segment(mp.base, max, /*neuronLabels=*/null,progress);
            progress.stopPulse("Ganglia: segmentation done");

            progress.step("Ganglia: label/export/areas");
            // If segment() returned binary, convert; if it returned labels, this is quick no-op
            // Ensure we end with a label map either way
            ImagePlus glabels = (gangliaOut.getBitDepth() == 8)
                    ? PluginCalls.binaryToLabels(gangliaOut)
                    : gangliaOut;
            glabels.setCalibration(max.getCalibration());
            gangliaLabels = glabels;

            RoiManager rmG = rmh.rm;
            rmG.reset(); rmG.setVisible(false);
            PluginCalls.labelsToRois(gangliaLabels);
            syncToSingleton(new RoiManager[]{ rmG });
            nGanglia = rmG.getCount();

            if (nGanglia > 0) {
                OutputIO.saveRois(rmG, new File(outDir, "Ganglia_ROIs_" + baseName + ".zip"));
                if (mp.base.saveFlattenedOverlay)
                    OutputIO.saveFlattenedOverlay(max, rmG,
                            new File(outDir, "MAX_" + baseName + "_ganglia_overlay.tif"));
            }
            rmG.reset(); rmG.setVisible(false);

            gangliaAreaUm2 = GangliaOps.areaPerGanglionUm2(gangliaLabels);

            // tidy original
            if (gangliaOut != gangliaLabels) {
                gangliaOut.changes = false; gangliaOut.close();
            }
        }



        // 3) Results stores
        LinkedHashMap<String,Integer> totals = new LinkedHashMap<>();
        LinkedHashMap<String,int[]>   perGanglia = new LinkedHashMap<>();
        Map<String, ImagePlus>        labelsByMarker = new LinkedHashMap<>();

        // 4) Per-marker: segment → review → save
        for (MarkerSpec m : mp.markers) {
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

                RmHandle rmh2 = ensureGlobalRM();
                RoiManager tmp = rmh2.rm;
                tmp.reset();
                tmp.setVisible(false);
                tmp.runCommand("Open", m.customRoisZip.getAbsolutePath());
                if (tmp.getCount() == 0) {
                    throw new IllegalArgumentException("ROI zip '" + m.customRoisZip.getName() + "' contains no ROIs.");
                }

                // 2) ROI Manager macro commands need batch mode OFF so the mask has a canvas
                boolean prevBatch = ij.macro.Interpreter.batchMode;
                ij.macro.Interpreter.batchMode = false;
                try {
                    // Paint ROIs -> binary -> labels (your original helpers)
                    ImagePlus bin = Features.Core.PluginCalls.roisToBinary(max, tmp);
                    ImagePlus lab = Features.Core.PluginCalls.binaryToLabels(bin);
                    lab.setCalibration(max.getCalibration());

                    // tidy
                    bin.changes = false; bin.close();
                    markerLabels = lab;
                } finally {
                    ij.macro.Interpreter.batchMode = prevBatch;
                    tmp.reset();
                    tmp.setVisible(false);
                }}else {
                double prob = (m.prob != null) ? m.prob : mp.multiProb;
                double nms  = (m.nms  != null) ? m.nms  : mp.multiNms;
                markerLabels = PluginCalls.runStarDist2DLabel(segInput, mp.subtypeModelZip, prob, nms);
                markerLabels = PluginCalls.removeBorderLabels(markerLabels);
                if (minPx > 0) markerLabels = PluginCalls.labelMinSizeFilterPx(markerLabels, minPx);
                if (markerLabels.getWidth() != max.getWidth() || markerLabels.getHeight() != max.getHeight()) {
                    markerLabels = ImageOps.resizeTo(markerLabels, max.getWidth(), max.getHeight());
                }
            }
            progress.stopPulse("Segment done: " + m.name);

            progress.step("Review: " + m.name);
            // ---- Review (seed RM, pass fallback) ----
            RoiManager rmRev = rmh.rm;
            rmRev.reset();
            PluginCalls.labelsToRois(markerLabels);        // seed with current call
            syncToSingleton(new RoiManager[]{ rmRev });
            ImagePlus fallback = markerLabels.duplicate();
            ij.macro.Interpreter.batchMode = false;
            ImagePlus reviewed = ReviewUI.reviewAndRebuildLabels(
                    ch, rmRev, m.name + " (review)", max.getCalibration(), fallback);
            ij.macro.Interpreter.batchMode = true;
            rmRev.reset();
            rmRev.setVisible(false);
            fallback.close();

            if (gangliaLabels != null) {
                GangliaOps.Result r = GangliaOps.countPerGanglion(reviewed, gangliaLabels);
                perGanglia.put(m.name, r.countsPerGanglion);

                // area is the same for all markers; keep it once
                if (gangliaAreaUm2 == null) gangliaAreaUm2 = r.areaUm2;
            }

            progress.step("Save: " + m.name);
            // Count & save
            int n = countLabels(reviewed);
            totals.put(m.name, n);

            RoiManager rmSave = rmh.rm;
            rmSave.reset();
            PluginCalls.labelsToRois(reviewed);
            syncToSingleton(new RoiManager[]{ rmSave });
            if (rmSave.getCount() > 0) {
                OutputIO.saveRois(rmSave, new File(outDir, m.name + "_ROIs_" + baseName + ".zip"));
                if (mp.base.saveFlattenedOverlay)
                    OutputIO.saveFlattenedOverlay(max, rmSave, new File(outDir, "MAX_" + baseName + "_" + m.name + "_overlay.tif"));
            }
            rmSave.reset();
            rmSave.setVisible(false);

            labelsByMarker.put(m.name, reviewed); // keep for combos
            ch.close();
            if (segInput != ch) segInput.close();
            markerLabels.close();
        }

        // 5) Pairwise combos (AND)
        List<String> names = new ArrayList<>(labelsByMarker.keySet());
        for (int i = 0; i < names.size(); i++) {
            for (int j = i + 1; j < names.size(); j++) {
                String aName = names.get(i), bName = names.get(j);
                String combo = aName + "+" + bName;

                progress.step("Combo: " + combo);
                ImagePlus a = labelsByMarker.get(aName);
                ImagePlus b = labelsByMarker.get(bName);
                ImagePlus c = andLabels(a, b);                 // pixelwise AND -> relabel

                int n = countLabels(c);
                totals.put(combo, n);

                if (gangliaLabels != null) {
                    GangliaOps.Result rc = GangliaOps.countPerGanglion(c, gangliaLabels);
                    perGanglia.put(combo, rc.countsPerGanglion);
                }

                progress.step("Save combo: " + combo);

                RoiManager rm = rmh.rm;
                rm.reset();
                PluginCalls.labelsToRois(c);
                syncToSingleton(new RoiManager[]{ rm});
                if (rm.getCount() > 0) {
                    OutputIO.saveRois(rm, new File(outDir, combo + "_ROIs_" + baseName + ".zip"));
                    if (mp.base.saveFlattenedOverlay)
                        OutputIO.saveFlattenedOverlay(max, rm, new File(outDir, "MAX_" + baseName + "_" + combo + "_overlay.tif"));
                }
                rm.reset();
                rm.setVisible(false);
                c.close();
            }
        }


        progress.step("Write CSV");
        // 6) CSV
        OutputIO.writeMultiCsvNoHu(
                new File(outDir, "Analysis_NoHu_" + baseName + "_cell_counts_multi.csv"),
                baseName,
                totals,
                perGanglia,
                gangliaAreaUm2
        );

        progress.step("Save MAX & cleanup");
        // 7) Save MAX and clean up
        OutputIO.saveTiff(max, new File(outDir, "MAX_" + baseName + ".tif"));
        for (ImagePlus keep : labelsByMarker.values()) keep.close();
        if (gangliaLabels != null) { gangliaLabels.changes = false; gangliaLabels.close(); }

        //close the progress bar
        progress.close();

        NoHuResult result = new NoHuResult(
                outDir,
                baseName,
                max,
                totals,
                perGanglia,
                (gangliaLabels != null ? Integer.valueOf(nGanglia) : null),
                gangliaAreaUm2
        );
        if (mp.base.doSpatialAnalysis) {
            runSingleSpatialPerMarker(result, mp);
        }
        maybeCloseRM(rmh);
        SwingUtilities.invokeLater(() ->
                UI.panes.Results.ResultsMultiNoHuUI.promptAndMaybeShow(result)
        );
    }

    // ------- helpers -------
    private static int countLabels(ImagePlus labels16) {
        short[] px = (short[]) labels16.getProcessor().getPixels();
        int max = 0;
        for (short v : px) { int u = v & 0xFFFF; if (u > max) max = u; }
        return max;
    }

    private static String stripExt(String name) {
        int dot = (name != null) ? name.lastIndexOf('.') : -1;
        return (dot > 0) ? name.substring(0, dot) : name;
    }

    /** pixelwise AND of two 16-bit label maps -> contiguous relabeled map */
    private static ImagePlus andLabels(ImagePlus a, ImagePlus b) {
        int w = a.getWidth(), h = a.getHeight();
        short[] pa = (short[]) a.getProcessor().getPixels();
        short[] pb = (short[]) b.getProcessor().getPixels();
        byte[] bin = new byte[w * h];

        for (int i = 0, n = bin.length; i < n; i++) {
            int va = pa[i] & 0xFFFF;
            int vb = pb[i] & 0xFFFF;
            bin[i] = (byte) ((va > 0 && vb > 0) ? 255 : 0);
        }
        ImagePlus binary = new ImagePlus("and_bin", new ij.process.ByteProcessor(w, h, bin, null));
        binary.setCalibration(a.getCalibration());
        ImagePlus relabeled = PluginCalls.binaryToLabels(binary);
        binary.close();
        relabeled.setCalibration(a.getCalibration());
        return relabeled;
    }

    private void runSingleSpatialPerMarker(NoHuResult mr, MultiParams p) {
        if (mr == null || p == null) return;

        String maxPath = new File(mr.outDir, "MAX_" + mr.baseName + ".tif").getAbsolutePath();
        String gangliaZip = (mr.nGanglia != null && mr.nGanglia > 0)
                ? new File(mr.outDir, "Ganglia_ROIs_" + mr.baseName + ".zip").getAbsolutePath()
                : "NA";
        String outDir = mr.outDir.getAbsolutePath();

        double expansionUm = (p.base.spatialExpansionUm != null) ? p.base.spatialExpansionUm : 6.5;
        boolean saveParametric = (p.base.spatialSaveParametric != null) && p.base.spatialSaveParametric;

        // include Hu + all subtype markers
        java.util.List<String> names = new java.util.ArrayList<>();
        for (MarkerSpec m : p.markers) names.add(m.name);

        for (String name : names) {
            // Hu uses the pre-existing Neuron_ROIs_<baseName>.zip
            File roiZipFile = new File(mr.outDir, name + "_ROIs_" + mr.baseName + ".zip");

            // fallback if someone saved Hu under a different scheme
            if (!roiZipFile.isFile() && name.equals("Hu")) {
                File alt = new File(mr.outDir, "Hu_ROIs_" + mr.baseName + ".zip");
                if (alt.isFile()) roiZipFile = alt;
            }

            if (!roiZipFile.isFile()) {
                IJ.log("Spatial single: missing ROI zip for " + name + " (" + roiZipFile.getName() + ")");
                continue;
            }

            try {
                new Analysis.SingleCellTypeAnalysis(
                        maxPath,
                        roiZipFile.getAbsolutePath(),
                        null,
                        outDir,
                        name,
                        expansionUm,
                        saveParametric
                ).execute();
            } catch (Exception ex) {
                IJ.log("Spatial single (" + name + ") failed: " + ex.getMessage());
            }
        }


    }
}
