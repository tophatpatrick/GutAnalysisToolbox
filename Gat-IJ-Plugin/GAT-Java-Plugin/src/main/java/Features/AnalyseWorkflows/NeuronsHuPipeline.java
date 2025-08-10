package Features.AnalyseWorkflows;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.plugin.frame.RoiManager;

import Features.Core.Params;
import Features.Core.PluginCalls;
import Features.Tools.ImageOps;
import Features.Tools.OutputIO;

import java.io.File;

public class NeuronsHuPipeline {

    public void run(Params p) {
        // 0) Basic validation
        if (p == null) throw new IllegalArgumentException("Params cannot be null.");
        if (p.stardistModelZip == null || !new File(p.stardistModelZip).isFile()) {
            throw new IllegalArgumentException("StarDist model not found: " + p.stardistModelZip);
        }

        // 1) Open image (Bio-Formats if path provided)
        ImagePlus imp = (p.imagePath == null || p.imagePath.isEmpty())
                ? IJ.getImage()
                : PluginCalls.openWithBioFormats(p.imagePath);


        if (imp == null)
            throw new IllegalStateException("No image available. Open an image or set Params.imagePath before running.");

        String baseName = stripExt(imp.getTitle());
        File outDir = OutputIO.prepareOutputDir(p.outputDir, imp, baseName);

        // 2) Calibration check
        Calibration cal = imp.getCalibration();
        if (p.requireMicronUnits && !PluginCalls.isMicronUnit(cal.getUnit()))
            throw new IllegalStateException("Image must be calibrated in microns. Unit: " + cal.getUnit());
        double pxUm = cal.pixelWidth; // the amount of microns per pixel

        // 3) Projection
        ImagePlus max = (imp.getNSlices() > 1)
                ? (p.useClij2EDF ? PluginCalls.clij2EdfVariance(imp) : ImageOps.mip(imp))
                : imp.duplicate();
        max.setTitle("MAX_" + baseName);

        // 4) Extract Hu channel (1-based)
        ImagePlus hu = ImageOps.extractChannel(max, p.huChannel);
        hu.setTitle(p.cellTypeName + "_segmentation");

        // ==== 5) Optional rescale to training pixel size (faithful to macro) ====
        // Macro: target_pixel_size = training_pixel_size / scale; scale_factor = pixelWidth / target_pixel_size
        double scale = (p.trainingRescaleFactor > 0) ? p.trainingRescaleFactor : 1.0; // 'scale' from Advanced dialog in macro
        double targetPxUm = p.trainingPixelSizeUm / scale;
        double scaleFactor = p.rescaleToTrainingPx && (pxUm > 0) ? (pxUm / targetPxUm) : 1.0;
        if (Math.abs(scaleFactor - 1.0) < 1e-3) scaleFactor = 1.0;

        ImagePlus segInput = (scaleFactor == 1.0)
                ? hu
                : ImageOps.resizeToIntensity(hu,
                (int)Math.round(hu.getWidth() * scaleFactor),
                (int)Math.round(hu.getHeight() * scaleFactor));

        // ==== 6) StarDist 2D -> Label Image (ZIP) ====
        ImagePlus labels = PluginCalls.runStarDist2DLabel(segInput, p.stardistModelZip, p.probThresh, p.nmsThresh);

        // ==== 7) Remove border labels + size filtering ====
        // IMPORTANT: the macro passes a pixel COUNT threshold: neuron_seg_lower_limit_um / pixelWidth
        int minPixelArea = 0;
        if (p.neuronSegLowerLimitUm != null && pxUm > 0) {
            double effPxUm = segInput.getCalibration().pixelWidth; // in case segInput was scaled
            minPixelArea = (int)Math.max(1, Math.round(p.neuronSegLowerLimitUm / effPxUm));
        }
        labels = PluginCalls.removeBorderLabels(labels);
        if (minPixelArea > 0) {
            labels = PluginCalls.labelMinSizeFilterPx(labels, minPixelArea);
        }

        // ==== 8) Scale labels back to MAX size if we scaled ====
        if (labels.getWidth() != max.getWidth() || labels.getHeight() != max.getHeight()) {
            labels = ImageOps.resizeTo(labels, max.getWidth(), max.getHeight());
        }

        // ==== 9) Labels -> ROIs ====
        RoiManager rm = RoiManager.getInstance2();
        if (rm == null) rm = new RoiManager();
        rm.reset();
        PluginCalls.labelsToRois(labels);
        int nHu = rm.getCount();

        // --- Ganglia (optional) ---
        if (p.cellCountsPerGanglia) {
            // A) Segment ganglia labels (method chosen in Params)
            ImagePlus gangliaLabels = GangliaOps.segment(p, max, labels);

            // B) Clean borders (safe 2D)
            gangliaLabels = PluginCalls.removeBorderLabels(gangliaLabels);

            // C) Save ganglia label image
            gangliaLabels.setTitle("Ganglia_label_MAX_" + baseName);
            OutputIO.saveTiff(gangliaLabels, new File(outDir, gangliaLabels.getTitle() + ".tif"));

            // D) Convert to ROIs and save
            RoiManager rmG = RoiManager.getInstance2();
            rmG.reset();
            PluginCalls.labelsToRois(gangliaLabels);
            OutputIO.saveRois(rmG, new File(outDir, "Ganglia_ROIs_" + baseName + ".zip"));
            if (p.saveFlattenedOverlay && rmG.getCount() > 0) {
                OutputIO.saveFlattenedOverlay(max, rmG, new File(outDir, "MAX_" + baseName + "_ganglia_overlay.tif"));
            }

            // E) Counts per ganglion (centroid-in-label sampling)
            GangliaOps.Result r = GangliaOps.countPerGanglion(labels, gangliaLabels);
            OutputIO.writeGangliaCsv(new File(outDir, "Analysis_Ganglia_" + baseName + "_counts.csv"),
                    r.countsPerGanglion, r.areaUm2);

            IJ.log("Ganglia analysis complete.");
        }


        // ==== 10) Save outputs (faithful names/locations) ====
        OutputIO.saveRois(rm, new File(outDir, p.cellTypeName + "_unmodified_ROIs_" + baseName + ".zip"));
        OutputIO.saveRois(rm, new File(outDir, p.cellTypeName + "_ROIs_" + baseName + ".zip"));

        labels.setTitle("Neuron_label_MAX_" + baseName);
        OutputIO.saveTiff(labels, new File(outDir, labels.getTitle() + ".tif"));
        OutputIO.saveTiff(max, new File(outDir, "MAX_" + baseName + ".tif"));

        if (p.saveFlattenedOverlay && nHu > 0) {
            OutputIO.saveFlattenedOverlay(max, rm, new File(outDir, "MAX_" + baseName + "_overlay.tif"));
        }

        // Minimal CSV with counts (same idea as macro’s table export)
        OutputIO.writeCountsCsv(
                new File(outDir, "Analysis_" + p.cellTypeName + "_" + baseName + "_cell_counts.csv"),
                baseName, p.cellTypeName, nHu
        );

        IJ.log("Analyse Neurons (Hu) complete: " + outDir.getAbsolutePath());
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
