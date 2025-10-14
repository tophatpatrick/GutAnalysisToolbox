package Features.Tools;

import Features.Core.Params;
import Features.AnalyseWorkflows.NeuronsMultiPipeline;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

import static Features.Tools.RoiManagerHelper.*;

/**
 * Shared, minimal segmentation helpers for the Tuning tools.
 * - Hu single-run (with rescale / prob override)
 * - Subtype single-run (uses MultiParams thresholds)
 * - Quick overlay PNG writers
 * - Simple ganglia-from-Hu expansion for preview (binary dilate → CC label)
 */
public final class SegCommon {

    // ----------------------- Result wrapper -----------------------

    public static final class SegResult {
        public final RoiManager rm;
        public final ImagePlus labels16;   // label map aligned to MAX (may be null for subtype)
        public final int count;

        public SegResult(RoiManager rm, ImagePlus labels16, int count) {
            this.rm = rm;
            this.labels16 = labels16;
            this.count = count;
        }
        public void dispose() {
            try {
                if (labels16 != null) { labels16.changes = false; labels16.close(); }
                if (rm != null) { rm.reset(); rm.setVisible(false); }
            } catch (Throwable ignore) {}
        }
    }

    // ----------------------- HU (single run) ----------------------

    /**
     * Segment Hu on one channel image, applying rescale + size filter + border removal.
     * @param ch   single-channel ImagePlus (extracted from MAX)
     * @param max  original MAX (for calibration + output alignment)
     * @param p    Params (uses trainingPixelSizeUm, trainingRescaleFactor, rescaleToTrainingPx, neuronSegLowerLimitUm, stardistModelZip)
     * @param probOverride if non-null, overrides p.probThresh
     */
    public static SegResult segmentHuOne(ImagePlus ch, ImagePlus max, Params p, Double probOverride) {
        if (ch == null || ch.getProcessor() == null) throw new IllegalArgumentException("channel image is null");
        if (max == null) throw new IllegalArgumentException("max image is null");
        if (p == null)  throw new IllegalArgumentException("Params cannot be null");
        if (p.stardistModelZip == null) throw new IllegalArgumentException("Params.stardistModelZip is null");

        // --- compute scale factor like your pipelines ---
        double pxUm = max.getCalibration() != null ? max.getCalibration().pixelWidth : 0.0;
        double scale = (p.trainingRescaleFactor > 0) ? p.trainingRescaleFactor : 1.0;
        double targetPxUm = (p.trainingPixelSizeUm > 0) ? (p.trainingPixelSizeUm / scale) : 0.0;
        double scaleFactor = (p.rescaleToTrainingPx && pxUm > 0 && targetPxUm > 0) ? (pxUm / targetPxUm) : 1.0;
        if (Math.abs(scaleFactor - 1.0) < 1e-3) scaleFactor = 1.0;

        ImagePlus segInput = (scaleFactor == 1.0)
                ? ch.duplicate()
                : ImageOps.resizeToIntensity(ch, (int)Math.round(ch.getWidth()*scaleFactor), (int)Math.round(ch.getHeight()*scaleFactor));

        try {
            double prob = (probOverride != null) ? probOverride
                    : (p.probThresh > 0 ? p.probThresh : 0.50);
            double nms  = (p.nmsThresh  > 0 ? p.nmsThresh  : 0.30);

            // --- StarDist label map on segInput scale ---
            ImagePlus labels = Features.Core.PluginCalls.runStarDist2DLabel(segInput, p.stardistModelZip, prob, nms);
            labels = Features.Core.PluginCalls.removeBorderLabels(labels);

            // --- size filter in pixels, using effective pixel size at segInput scale ---
            Double minMicron = (p.neuronSegLowerLimitUm != null) ? p.neuronSegLowerLimitUm : p.neuronSegMinMicron;
            int minPx = 0;
            if (minMicron != null && pxUm > 0) {
                double effUm = (scaleFactor == 1.0 || targetPxUm == 0.0) ? pxUm : targetPxUm;
                minPx = (int)Math.max(1, Math.round(minMicron / effUm));
            }
            if (minPx > 0) {
                labels = Features.Core.PluginCalls.labelMinSizeFilterPx(labels, minPx);
            }

            // --- back to MAX size if needed ---
            if (labels.getWidth() != max.getWidth() || labels.getHeight() != max.getHeight()) {
                labels = ImageOps.resizeTo(labels, max.getWidth(), max.getHeight());
                labels.setCalibration(max.getCalibration());
            }

            // --- labels → ROI Manager ---
            RmHandle rmh = ensureGlobalRM();
            RoiManager rm = rmh.rm;
            rm.reset(); rm.setVisible(false);
            Features.Core.PluginCalls.labelsToRois(labels);
            syncToSingleton(new RoiManager[]{rm});

            int count = countLabels(labels);
            return new SegResult(rm, labels, count);

        } finally {
            segInput.changes = false;
            segInput.close();
        }
    }

    // -------------------- Subtype (single run) --------------------

    /**
     * Segment one subtype channel using MultiParams thresholds (prob/nms) and base rescaling.
     * Returns ROIs + count; label map is resized back to MAX dims.
     */
    public static SegResult segmentSubtypeOne(ImagePlus ch, ImagePlus max, NeuronsMultiPipeline.MultiParams mp) {
        if (ch == null || ch.getProcessor() == null) throw new IllegalArgumentException("channel image is null");
        if (max == null) throw new IllegalArgumentException("max image is null");
        if (mp == null || mp.base == null) throw new IllegalArgumentException("MultiParams/base cannot be null");
        if (mp.subtypeModelZip == null) throw new IllegalArgumentException("MultiParams.subtypeModelZip is null");

        // scale like in your multi pipeline
        double pxUm = max.getCalibration() != null ? max.getCalibration().pixelWidth : 0.0;
        double scale = (mp.base.trainingRescaleFactor > 0) ? mp.base.trainingRescaleFactor : 1.0;
        double targetPxUm = (mp.base.trainingPixelSizeUm > 0) ? (mp.base.trainingPixelSizeUm / scale) : 0.0;
        double scaleFactor = (mp.base.rescaleToTrainingPx && pxUm > 0 && targetPxUm > 0) ? (pxUm / targetPxUm) : 1.0;
        if (Math.abs(scaleFactor - 1.0) < 1e-3) scaleFactor = 1.0;

        ImagePlus segInput = (scaleFactor == 1.0)
                ? ch.duplicate()
                : ImageOps.resizeToIntensity(ch, (int)Math.round(ch.getWidth()*scaleFactor), (int)Math.round(ch.getHeight()*scaleFactor));

        try {
            double prob = (mp.multiProb > 0) ? mp.multiProb : 0.50;
            double nms  = (mp.multiNms  > 0) ? mp.multiNms  : 0.30;

            ImagePlus labels = Features.Core.PluginCalls.runStarDist2DLabel(segInput, mp.subtypeModelZip, prob, nms);
            labels = Features.Core.PluginCalls.removeBorderLabels(labels);

            // optional min-size using same logic as in your multi pipeline
            Double minMicron = (mp.base.neuronSegLowerLimitUm != null)
                    ? mp.base.neuronSegLowerLimitUm
                    : mp.base.neuronSegMinMicron;
            int subtypeMinPx = 0;
            if (minMicron != null && pxUm > 0) {
                double effUm = (scaleFactor == 1.0 || targetPxUm == 0.0) ? pxUm : targetPxUm;
                subtypeMinPx = (int)Math.max(1, Math.round(minMicron / effUm));
            }
            if (subtypeMinPx > 0) {
                labels = Features.Core.PluginCalls.labelMinSizeFilterPx(labels, subtypeMinPx);
            }

            if (labels.getWidth() != max.getWidth() || labels.getHeight() != max.getHeight()) {
                labels = ImageOps.resizeTo(labels, max.getWidth(), max.getHeight());
                labels.setCalibration(max.getCalibration());
            }

            RmHandle rmh = ensureGlobalRM();
            RoiManager rm = rmh.rm;
            rm.reset(); rm.setVisible(false);
            Features.Core.PluginCalls.labelsToRois(labels);
            syncToSingleton(new RoiManager[]{rm});

            int count = countLabels(labels);
            return new SegResult(rm, labels, count);

        } finally {
            segInput.changes = false;
            segInput.close();
        }
    }

    // ------------------- Ganglia (Hu expansion) -------------------

    /**
     * Quick preview builder: take Hu ROIs → binary → dilate (µm) → connected components → labels.
     * Uses iterative "Dilate" as a simple morphology. For preview/tuning only.
     */
    public static ImagePlus gangliaByExpansionPreview(ImagePlus max, RoiManager huRm, double expansionUm) {
        if (max == null) throw new IllegalArgumentException("max is null");
        if (huRm == null || huRm.getCount() == 0) throw new IllegalArgumentException("Hu ROI manager is empty");

        double pxUm = max.getCalibration() != null ? max.getCalibration().pixelWidth : 0.0;
        int iters = (pxUm > 0) ? Math.max(1, (int)Math.round(expansionUm / pxUm)) : 6;

        // ROIs → binary mask (8-bit) same size as MAX
        ImagePlus bin = Features.Core.PluginCalls.roisToBinary(max, huRm);

        // simple pixel dilation for 'iters' steps
        for (int k = 0; k < iters; k++) {
            IJ.run(bin, "Dilate", "");
        }

        // mask → labels
        ImagePlus labels = Features.Core.PluginCalls.binaryToLabels(bin);
        labels.setCalibration(max.getCalibration());

        // tidy
        bin.changes = false; bin.close();
        return labels;
    }

    // ---------------------- Overlay savers (PNG) ------------------

    /** Save a flattened PNG of MAX with given ROIs overlaid. */
    public static File saveOverlay(ImagePlus max, RoiManager rm, File outDir, String fileName) {
        try {
            if (!outDir.exists()) outDir.mkdirs();
            File out = new File(outDir, fileName.endsWith(".png") ? fileName : (fileName + ".png"));

            ImagePlus dup = max.duplicate();
            Overlay ov = new Overlay();
            for (Roi r : rm.getRoisAsArray()) {
                if (r == null) continue;
                Roi c = (Roi) r.clone();
                c.setStrokeColor(new Color(241, 7, 7));
                c.setStrokeWidth(1.5);
                c.setFillColor(null);
                ov.add(c);
            }
            dup.setOverlay(ov);
            ImagePlus flat = dup.flatten();
            BufferedImage bi = flat.getBufferedImage();
            ImageIO.write(bi, "PNG", out);

            dup.changes = false; dup.close();
            flat.changes = false; flat.close();
            return out;
        } catch (Exception ex) {
            IJ.handleException(ex);
            return null;
        }
    }

    /** Save a flattened PNG of MAX with a label/binary painted as ROIs. */
    public static File saveMaskOverlay(ImagePlus max, ImagePlus labelsOrMask, File outDir, String fileName) {
        try {
            RmHandle rmh = ensureGlobalRM();
            RoiManager rm = rmh.rm;
            rm.reset(); rm.setVisible(false);

            Features.Core.PluginCalls.labelsToRois(labelsOrMask);
            syncToSingleton(new RoiManager[]{rm});

            File f = saveOverlay(max, rm, outDir, fileName);

            rm.reset(); rm.setVisible(false);
            return f;
        } catch (Exception ex) {
            IJ.handleException(ex);
            return null;
        }
    }

    // ------------------------- Utilities --------------------------

    /** Count labels in a 16-bit label map (max pixel value). */
    public static int countLabels(ImagePlus labels16) {
        if (labels16 == null || labels16.getProcessor() == null) return 0;
        ImageProcessor ip = labels16.getProcessor();
        int w = ip.getWidth(), h = ip.getHeight();
        int max = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = ip.get(x, y) & 0xFFFF;
                if (v > max) max = v;
            }
        }
        return max;
    }

    private SegCommon() {}
}
