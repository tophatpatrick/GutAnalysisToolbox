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

import java.io.File;
import java.util.*;

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
            // fibres channel required; default to 1 if user left it 0
            int fibresCh  = (mp.base.gangliaChannel > 0) ? mp.base.gangliaChannel : 1;
            // cell-body (“most cells”) channel optional; default to first marker channel, else fibres
            int cellBodyCh = (mp.base.gangliaCellChannel > 0)
                    ? mp.base.gangliaCellChannel
                    : (!mp.markers.isEmpty() ? mp.markers.get(0).channel : fibresCh);

            progress.pulse("Ganglia: run model");
            ij.macro.Interpreter.batchMode = false;
            ImagePlus gangliaBinary = PluginCalls.runDeepImageJForGanglia(
                    max,
                    fibresCh,                         // C1 (green) in the DIJ input
                    cellBodyCh,                       // C2 (magenta) in the DIJ input
                    mp.base.gangliaModelFolder,
                    (mp.base.gangliaMinAreaUm2 != null ? mp.base.gangliaMinAreaUm2 : 200.0),
                    mp.base
            );
            ij.macro.Interpreter.batchMode = true;

            progress.stopPulse("Ganglia: model done");

            progress.step("Ganglia: label + export ROIs");
            // Label the binary and export ROIs like the macro
            gangliaLabels = PluginCalls.binaryToLabels(gangliaBinary);
            RoiManager rmG = new RoiManager(false);
            rmG.reset();
            PluginCalls.labelsToRois(gangliaLabels);

            nGanglia = rmG.getCount();
            if (nGanglia > 0) {
                OutputIO.saveRois(rmG, new File(outDir, "Ganglia_ROIs_" + baseName + ".zip"));
                if (mp.base.saveFlattenedOverlay)
                    OutputIO.saveFlattenedOverlay(max, rmG, new File(outDir, "MAX_" + baseName + "_ganglia_overlay.tif"));
            }
            rmG.reset(); rmG.close();
            progress.step("Ganglia: compute areas");

            gangliaAreaUm2 = GangliaOps.areaPerGanglionUm2(gangliaLabels);

            progress.step("Ganglia: cleanup");
            gangliaBinary.close();
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
                RoiManager tmp = new RoiManager(false);
                tmp.reset();
                tmp.runCommand("Open", m.customRoisZip.getAbsolutePath());
                ImagePlus bin = PluginCalls.roisToBinary(max, tmp);
                markerLabels = PluginCalls.binaryToLabels(bin);
                tmp.reset(); tmp.close(); bin.close();
            } else {
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
            RoiManager rmRev = new RoiManager(false);
            rmRev.reset();
            PluginCalls.labelsToRois(markerLabels);        // seed with current call
            ImagePlus fallback = markerLabels.duplicate();
            ij.macro.Interpreter.batchMode = false;
            ImagePlus reviewed = ReviewUI.reviewAndRebuildLabels(
                    ch, rmRev, m.name + " (review)", max.getCalibration(), fallback);
            ij.macro.Interpreter.batchMode = true;
            rmRev.reset(); rmRev.close();
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

            RoiManager rmSave = new RoiManager(false);
            rmSave.reset();
            PluginCalls.labelsToRois(reviewed);
            if (rmSave.getCount() > 0) {
                OutputIO.saveRois(rmSave, new File(outDir, m.name + "_ROIs_" + baseName + ".zip"));
                if (mp.base.saveFlattenedOverlay)
                    OutputIO.saveFlattenedOverlay(max, rmSave, new File(outDir, "MAX_" + baseName + "_" + m.name + "_overlay.tif"));
            }
            rmSave.reset(); rmSave.close();


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

                RoiManager rm = new RoiManager(false);
                rm.reset();
                PluginCalls.labelsToRois(c);
                if (rm.getCount() > 0) {
                    OutputIO.saveRois(rm, new File(outDir, combo + "_ROIs_" + baseName + ".zip"));
                    if (mp.base.saveFlattenedOverlay)
                        OutputIO.saveFlattenedOverlay(max, rm, new File(outDir, "MAX_" + baseName + "_" + combo + "_overlay.tif"));
                }
                rm.reset(); rm.close();
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

}
