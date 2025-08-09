package Features.AnalyseWorkflows;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.frame.RoiManager;
import ij.measure.Calibration;

import Features.Core.Params;
import Features.Core.PluginCalls;
import Features.Tools.ImageOps;
import Features.Tools.OutputIO;

import java.io.File;

public class NeuronsHuPipeline {

    public void run(Params p) {
        if (p.stardistModelZip == null || !(new File(p.stardistModelZip).isFile())) {
            throw new IllegalArgumentException("StarDist model not found: " + p.stardistModelZip);
        }

        // 1) Open image (Bio-Formats if path provided).
        ImagePlus imp = (p.imagePath == null) ? IJ.getImage() : PluginCalls.openWithBioFormats(p.imagePath);
        if (imp == null) throw new IllegalStateException("No image available.");

        String baseName = stripExt(imp.getTitle());
        File outDir = OutputIO.prepareOutputDir(p.outputDir, imp, baseName);

        // 2) Calibration check.
        Calibration cal = imp.getCalibration();
        if (p.requireMicronUnits && !PluginCalls.isMicronUnit(cal.getUnit()))
            throw new IllegalStateException("Image must be calibrated in microns. Unit: " + cal.getUnit());
        double pxUm = cal.pixelWidth;

        // 3) Projection.
        ImagePlus max = (imp.getNSlices() > 1)
                ? (p.useClij2EDF ? PluginCalls.clij2EdfVariance(imp) : ImageOps.mip(imp))
                : imp.duplicate();
        max.setTitle("MAX_" + baseName);

        // 4) Extract Hu channel.
        ImagePlus hu = ImageOps.extractChannel(max, p.huChannel);
        hu.setTitle(p.cellTypeName + "_segmentation");

        // 5) Optional rescale to model resolution.
        double scaleFactor = 1.0;
        if (p.rescaleToTrainingPx && pxUm > 0) {
            scaleFactor = pxUm / p.trainingPixelSizeUm;
            if (Math.abs(scaleFactor - 1.0) < 1e-3) scaleFactor = 1.0;
        }
        ImagePlus segInput = (scaleFactor == 1.0)
                ? hu
                : ImageOps.resizeToIntensity(hu, (int)Math.round(hu.getWidth()*scaleFactor), (int)Math.round(hu.getHeight()*scaleFactor));

        // 6) StarDist 2D -> label image.
        ImagePlus labels = PluginCalls.runStarDist2DLabel(segInput, p.stardistModelZip, p.probThresh, p.nmsThresh);

        // 7) Remove border labels + size filtering (area in px²).
        double effPxUm = segInput.getCalibration().pixelWidth;
        Double minPx2 = (p.minNeuronAreaUm2 != null) ? PluginCalls.um2ToPx2(p.minNeuronAreaUm2, effPxUm) : null;
        Double maxPx2 = (p.maxNeuronAreaUm2 != null) ? PluginCalls.um2ToPx2(p.maxNeuronAreaUm2, effPxUm) : null;

        labels = PluginCalls.removeBorderLabels(labels);
        labels = PluginCalls.labelSizeFilter(labels, minPx2, maxPx2);

        // 8) Scale labels back to MAX size if we scaled.
        if (labels.getWidth() != max.getWidth() || labels.getHeight() != max.getHeight()) {
            labels = ImageOps.resizeTo(labels, max.getWidth(), max.getHeight());
        }

        // 9) Labels -> ROIs (fills ROI Manager).
        RoiManager rm = RoiManager.getInstance2();
        rm.reset();
        PluginCalls.labelsToRois(labels);
        int nHu = rm.getCount();

        // 10) Save outputs.
        OutputIO.saveRois(rm, new File(outDir, p.cellTypeName + "_unmodified_ROIs_" + baseName + ".zip"));
        OutputIO.saveRois(rm, new File(outDir, p.cellTypeName + "_ROIs_" + baseName + ".zip"));
        labels.setTitle(p.cellTypeName + "_label_MAX_" + baseName);
        OutputIO.saveTiff(labels, new File(outDir, labels.getTitle() + ".tif"));
        OutputIO.saveTiff(max, new File(outDir, "MAX_" + baseName + ".tif"));
        if (p.saveFlattenedOverlay && nHu > 0) {
            OutputIO.saveFlattenedOverlay(max, rm, new File(outDir, "MAX_" + baseName + "_overlay.tif"));
        }
        OutputIO.writeCountsCsv(new File(outDir, "Analysis_" + p.cellTypeName + "_" + baseName + "_cell_counts.csv"),
                baseName, p.cellTypeName, nHu);

        IJ.log("Analyse Neurons (Hu) complete: " + outDir.getAbsolutePath());
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
