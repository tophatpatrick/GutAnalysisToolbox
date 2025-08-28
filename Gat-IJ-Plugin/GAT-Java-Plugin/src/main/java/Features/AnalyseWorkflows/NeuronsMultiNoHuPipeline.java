package Features.AnalyseWorkflows;

import Features.Core.Params;
import Features.Core.PluginCalls;
import Features.Tools.ImageOps;
import Features.Tools.LabelOps;
import Features.Tools.OutputIO;
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
        public final int channel;     // 1-based
        public Double prob;           // optional override
        public Double nms;            // optional override
        public File customRoisZip;    // optional: use user-provided ROIs for this marker

        public MarkerSpec(String name, int channel) {
            this.name = name; this.channel = channel;
        }
        public MarkerSpec withThresh(Double prob, Double nms) { this.prob = prob; this.nms = nms; return this; }
        public MarkerSpec withCustomRois(File zip) { this.customRoisZip = zip; return this; }
    }

    public static final class MultiParams {
        public Params base;               // projection / rescale / ganglia options reused
        public String subtypeModelZip;    // StarDist model for subtypes
        public double multiProb = 0.50;
        public double multiNms  = 0.30;
        public double overlapFrac = 0.40; // used for combos (pixelwise AND anyway, kept for parity)
        public final List<MarkerSpec> markers = new ArrayList<>();
    }

    // ------- run -------
    public void run(MultiParams mp) {
        if (mp == null || mp.base == null) throw new IllegalArgumentException("MultiParams/base cannot be null");
        if (mp.subtypeModelZip == null || !new File(mp.subtypeModelZip).isFile())
            throw new IllegalArgumentException("Subtype StarDist model not found: " + mp.subtypeModelZip);
        if (mp.markers.isEmpty()) throw new IllegalArgumentException("Add at least one marker.");

        // 1) Open image and prepare MAX projection (no Hu gating here)
        ImagePlus imp = (mp.base.imagePath == null || mp.base.imagePath.isEmpty())
                ? IJ.getImage()
                : PluginCalls.openWithBioFormats(mp.base.imagePath);
        if (imp == null) throw new IllegalStateException("No image available to analyze.");

        final String baseName = stripExt(imp.getTitle());
        final File outDir = OutputIO.prepareOutputDir(mp.base.outputDir, imp, baseName);

        ImagePlus max = (imp.getNSlices() > 1)
                ? (mp.base.useClij2EDF ? PluginCalls.clij2EdfVariance(imp) : ImageOps.mip(imp))
                : imp.duplicate();
        max.setTitle("MAX_" + baseName);

        // 2) Rescale math
        final double pxUm = max.getCalibration().pixelWidth > 0 ? max.getCalibration().pixelWidth : 1.0;
        final double scale = (mp.base.trainingRescaleFactor > 0) ? mp.base.trainingRescaleFactor : 1.0;
        final double targetPxUm = mp.base.trainingPixelSizeUm / scale;
        double scaleFactor = (mp.base.rescaleToTrainingPx ? (pxUm / targetPxUm) : 1.0);
        if (Math.abs(scaleFactor - 1.0) < 1e-3) scaleFactor = 1.0;

        // min size in pixels at segmentation scale
        int minPx = 0;
        if (mp.base.neuronSegMinMicron != null && pxUm > 0) {
            double eff = (scaleFactor == 1.0) ? pxUm : targetPxUm;
            minPx = (int)Math.max(1, Math.round(mp.base.neuronSegMinMicron / eff));
        }

        // 3) results stores
        LinkedHashMap<String,Integer> totals = new LinkedHashMap<>();
        LinkedHashMap<String,int[]>   perGanglia = new LinkedHashMap<>();
        Map<String, ImagePlus>        labelsByMarker = new LinkedHashMap<>();

        // 4) per-marker: segment → optional review → save
        for (MarkerSpec m : mp.markers) {
            IJ.log("[NoHu] Marker: " + m.name + " (ch " + m.channel + ")");
            ImagePlus ch = ImageOps.extractChannel(max, m.channel);
            ImagePlus segInput = (scaleFactor == 1.0)
                    ? ch
                    : ImageOps.resizeToIntensity(ch,
                    (int)Math.round(ch.getWidth() * scaleFactor),
                    (int)Math.round(ch.getHeight() * scaleFactor));

            ImagePlus markerLabels;
            if (m.customRoisZip != null && m.customRoisZip.isFile()) {
                // use user-provided ROIs → to labels (at MAX size)
                RoiManager tmp = new RoiManager(false);
                tmp.reset();
                tmp.runCommand("Open", m.customRoisZip.getAbsolutePath());
                ImagePlus bin = PluginCalls.roisToBinary(max, tmp);
                markerLabels = PluginCalls.binaryToLabels(bin);
                tmp.reset(); tmp.close();
                bin.close();
            } else {
                // StarDist on segInput
                double prob = (m.prob != null) ? m.prob : mp.multiProb;
                double nms  = (m.nms  != null) ? m.nms  : mp.multiNms;
                markerLabels = PluginCalls.runStarDist2DLabel(segInput, mp.subtypeModelZip, prob, nms);
                markerLabels = PluginCalls.removeBorderLabels(markerLabels);
                if (minPx > 0) markerLabels = PluginCalls.labelMinSizeFilterPx(markerLabels, minPx);

                // back to MAX size
                if (markerLabels.getWidth() != max.getWidth() || markerLabels.getHeight() != max.getHeight()) {
                    markerLabels = ImageOps.resizeTo(markerLabels, max.getWidth(), max.getHeight());
                }
            }

            // ----- REVIEW (seed RM with current labels; pass fallback) -----
            RoiManager rmRev = new RoiManager(false);
            rmRev.reset();
            PluginCalls.labelsToRois(markerLabels);                  // seed
            // bind overlay to review window *inside* ReviewUI
            ImagePlus fallback = markerLabels.duplicate();
            ImagePlus reviewed = ReviewUI.reviewAndRebuildLabels(
                    ch /*backdrop*/, rmRev, m.name + " review", max.getCalibration(), fallback);
            rmRev.reset(); rmRev.close();
            fallback.close();

            // count + save
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

            labelsByMarker.put(m.name, reviewed); // keep for combos (don’t close yet)
            ch.close();
            if (segInput != ch) segInput.close();
            markerLabels.close();
        }

        // 5) combos (pairwise AND in label-space)
        List<String> names = new ArrayList<>(labelsByMarker.keySet());
        for (int i = 0; i < names.size(); i++) {
            for (int j = i + 1; j < names.size(); j++) {
                String aName = names.get(i), bName = names.get(j);
                String combo = aName + "+" + bName;

                ImagePlus a = labelsByMarker.get(aName);
                ImagePlus b = labelsByMarker.get(bName);
                ImagePlus c = LabelOps.andLabels(a, b);      // keep IDs where overlap >0 (produces fresh label map)
                int n = countLabels(c);
                totals.put(combo, n);

                // optional per-ganglia
                // if (gangliaLabels != null) { ... perGanglia.put(combo, ...); }

                // save ROIs/overlay for combos
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

        // 6) CSV (no Hu totals in this workflow → pass 0)
        OutputIO.writeMultiCsv(
                new File(outDir, "Analysis_NoHu_" + baseName + "_cell_counts_multi.csv"),
                baseName,
                /*totalHu*/ 0,
                /*nGanglia*/ null,
                totals,
                perGanglia,
                /*gangliaArea*/ null
        );

        // 7) save MAX and cleanup
        OutputIO.saveTiff(max, new File(outDir, "MAX_" + baseName + ".tif"));
        for (ImagePlus keep : labelsByMarker.values()) keep.close();
        IJ.log("[NoHu] Complete: " + outDir.getAbsolutePath());
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
}
