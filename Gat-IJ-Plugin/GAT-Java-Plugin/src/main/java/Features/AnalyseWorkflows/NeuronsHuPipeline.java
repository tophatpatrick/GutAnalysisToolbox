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
        if (p.neuronDeepImageJModelDir == null || !(new File(p.neuronDeepImageJModelDir).isDirectory())) {
            throw new IllegalArgumentException("DeepImageJ neuron model folder not found: " + p.neuronDeepImageJModelDir);
        }

        // 1) Open image (Bio-Formats if path provided)
        ImagePlus imp = (p.imagePath == null) ? IJ.getImage() : PluginCalls.openWithBioFormats(p.imagePath);
        if (imp == null) throw new IllegalStateException("No image available.");

        String baseName = stripExt(imp.getTitle());
        File outDir = OutputIO.prepareOutputDir(p.outputDir, imp, baseName);

        // 2) Calibration check
        Calibration cal = imp.getCalibration();
        if (p.requireMicronUnits && !PluginCalls.isMicronUnit(cal.getUnit()))
            throw new IllegalStateException("Image must be calibrated in microns. Unit: " + cal.getUnit());
        double pxUm = cal.pixelWidth; // assume square pixels

        // 3) Projection
        ImagePlus max = (imp.getNSlices() > 1)
                ? (p.useClij2EDF ? PluginCalls.clij2EdfVariance(imp) : ImageOps.mip(imp))
                : imp.duplicate();
        max.setTitle("MAX_" + baseName);

        // 4) Extract Hu channel (1-based)
        ImagePlus hu = ImageOps.extractChannel(max, p.huChannel);
        hu.setTitle(p.cellTypeName + "_segmentation");

        // 5) Optional rescale to training resolution (faithful to macro: interpolation=None)
        double targetPx = p.trainingPixelSizeUm / Math.max(1e-9, p.scale); // macro uses training_pixel_size/scale
        double scaleFactor = (p.rescaleToTrainingPx && pxUm > 0) ? (pxUm / targetPx) : 1.0;
        if (Math.abs(scaleFactor - 1.0) < 1e-3) scaleFactor = 1.0;

        ImagePlus segInput = (scaleFactor == 1.0)
                ? hu
                : ImageOps.resizeTo(hu, (int)Math.round(hu.getWidth()*scaleFactor), (int)Math.round(hu.getHeight()*scaleFactor));

        // 6) DeepImageJ → model’s stardist_postprocessing.ijm (prob, overlap) → label image
        ImagePlus labels = PluginCalls.runDeepImageJNeuronLabel(segInput, new File(p.neuronDeepImageJModelDir), p.probThresh, p.nmsOverlap);

        // 7) Remove border labels
        labels = PluginCalls.removeBorderLabels(labels);

        // 8) Scale labels back to MAX size (macro does this before size filter)
        if (labels.getWidth() != max.getWidth() || labels.getHeight() != max.getHeight()) {
            labels = ImageOps.resizeTo(labels, max.getWidth(), max.getHeight());
        }

        // 9) Size filtering (faithful to macro): minSizePx = microns / pixelWidth (NOT µm²)
        int minSizePx = (p.neuronSegMinMicron != null && pxUm > 0) ? (int)Math.max(1, Math.round(p.neuronSegMinMicron / pxUm)) : 1;
        labels = PluginCalls.labelMinSizeFilterPx(labels, minSizePx);

        // 10) Labels -> ROIs
        RoiManager rm = RoiManager.getInstance2();
        rm.reset();
        PluginCalls.labelsToRois(labels);
        int nHu = rm.getCount();

        // 11) Save outputs
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
