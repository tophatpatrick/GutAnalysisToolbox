package Features.AnalyseWorkflows;

import Analysis.SpatialSingleCellType;
import UI.panes.Results.ResultsUI;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.measure.Calibration;
import ij.plugin.filter.EDM;
import ij.plugin.frame.RoiManager;

import Features.Core.Params;
import Features.Core.PluginCalls;
import Features.Tools.ImageOps;
import Features.Tools.OutputIO;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;
import Features.Tools.ProgressUI;

import javax.swing.*;
import java.io.File;
import static Features.Tools.RoiManagerHelper.*;

public class NeuronsHuPipeline {

    public static final class HuResult {
        public final File outDir;
        public final String baseName;
        public final ImagePlus max;             // MAX_* image (shown/saved)
        public final ImagePlus neuronLabels;    // label map at MAX size
        public final int totalNeuronCount;

        // ganglia (null/empty if disabled)
        public final ImagePlus gangliaLabels;
        public final int[] neuronsPerGanglion;  // 1..G
        public final double[] gangliaAreaUm2;   // 1..G
        public final Integer nGanglia;

        public HuResult(File outDir, String baseName, ImagePlus max, ImagePlus neuronLabels,
                        int totalNeuronCount, ImagePlus gangliaLabels, int[] neuronsPerGanglion,
                        double[] gangliaAreaUm2, Integer nGanglia) {
            this.outDir = outDir;
            this.baseName = baseName;
            this.max = max;
            this.neuronLabels = neuronLabels;
            this.totalNeuronCount = totalNeuronCount;
            this.gangliaLabels = gangliaLabels;
            this.neuronsPerGanglion = neuronsPerGanglion;
            this.gangliaAreaUm2 = gangliaAreaUm2;
            this.nGanglia = nGanglia;
        }
    }

    public HuResult run(Params p, Boolean huReturn) {
        return run(p, huReturn, null);
    }

    public static int estimateSteps(Params p) {
        // keep this in sync with your step() calls
        return 12 + (p.cellCountsPerGanglia ? 7 : 0);
    }

    public HuResult run(Params p, Boolean huReturn, ProgressUI progress) {


        boolean ownProgress = (progress == null);
        if (ownProgress) {
            progress = new ProgressUI("Neuron/Hu pipeline");
            progress.start(estimateSteps(p));
        }

        boolean prevBatch = ij.macro.Interpreter.batchMode;
        ij.macro.Interpreter.batchMode = true;


        // 0) Basic validation
        if (p.stardistModelZip == null || !new File(p.stardistModelZip).isFile()) {
            throw new IllegalArgumentException("StarDist model not found: " + p.stardistModelZip);
        }


        progress.step("Opening image");
        // 1) Open image (Bio-Formats if path provided)
        ImagePlus imp = (p.imagePath == null || p.imagePath.isEmpty())
                ? IJ.getImage()
                : PluginCalls.openWithBioFormats(p.imagePath);


        if (imp == null)
            throw new IllegalStateException("No image available. Open an image or set Params.imagePath before running.");

        String baseName = stripExt(imp.getTitle());
        File outDir = OutputIO.prepareOutputDir(p.outputDir, imp, baseName);

        progress.step("Checking calibration");
        // 2) Calibration check
        Calibration cal = imp.getCalibration();
        if (p.requireMicronUnits && !PluginCalls.isMicronUnit(cal.getUnit()))
            throw new IllegalStateException("Image must be calibrated in microns. Unit: " + cal.getUnit());
        double pxUm = cal.pixelWidth; // the amount of microns per pixel

        progress.step("Creating Projection");
        // 3) Projection
        ImagePlus max = (imp.getNSlices() > 1)
                ? (p.useClij2EDF ? PluginCalls.clij2EdfVariance(imp) : ImageOps.mip(imp))
                : imp.duplicate();
        max.setTitle("MAX_" + baseName);

        //create our global roi manager
        RmHandle rmh = ensureGlobalRM();

        progress.step("Extracting Hu Channel");
        // 4) Extract Hu channel (1-based)
        ImagePlus hu = ImageOps.extractChannel(max, p.huChannel);
        hu.setTitle(p.cellTypeName + "_segmentation");

        progress.step("Rescaling to the training pixel size");
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

        progress.step("Segmenting with Stardist");
        // ==== 6) StarDist 2D -> Label Image (ZIP) ====
        ImagePlus labels = PluginCalls.runStarDist2DLabel(
                segInput, p.stardistModelZip, p.probThresh, p.nmsThresh);

        progress.step("Removing border labels and size filtering");
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

        progress.step("Scaling Labels");
        // ==== 8) Scale labels back to MAX size if we scaled ====
        if (labels.getWidth() != max.getWidth() || labels.getHeight() != max.getHeight()) {
            labels = ImageOps.resizeTo(labels, max.getWidth(), max.getHeight());
        }
        progress.step("Converting Labels to ROI's");
        // ==== 9) Labels -> ROIs ====
        RoiManager rm = rmh.rm;
        rm.reset();

// push labels into RM (silent)
        PluginCalls.labelsToRois(labels);
        syncToSingleton(new RoiManager[]{ rm });
        ij.macro.Interpreter.batchMode = false;

        progress.step("Show Hu review");

// show Hu (at MAX size) + colored LUT so cells are clear
        ImagePlus huReview = hu.duplicate();         // 'hu' is at MAX_* size already
        huReview.setTitle("Hu (review) - " + baseName);
        IJ.run(huReview, "Magenta", "");             // make Hu clearly visible
        IJ.resetMinAndMax(huReview);

// show RM overlay on top of Hu
        huReview.show();
        rm.setVisible(true);
        rm.runCommand(huReview, "Show All with labels");

// let user edit: draw new ROIs (Polygon/Freehand) + press 'T' to add; select + Delete to remove
        IJ.setTool("polygon");
        new ij.gui.WaitForUserDialog(
                "Neuron ROIs review",
                "Review Hu + ROIs.\n" +
                        "• Draw a new ROI and press 'T' to add\n" +
                        "• Select a ROI and press Delete to remove\n" +
                        "• Drag vertices to tweak shapes\n" +
                        "Click OK when done."
        ).show();

        progress.step("Rebuilding labels from edited ROIs");
// remove overlay and rebuild labels from whatever is in RM now
        rm.runCommand(huReview, "Show All without labels");

        ij.macro.Interpreter.batchMode = true;


// paint ROIs → binary → labels, at MAX size
        ImagePlus correctedBinary = PluginCalls.roisToBinary(huReview, rm);
        applyWatershedInPlace(correctedBinary);
        ImagePlus labelsEdited    = PluginCalls.binaryToLabels(correctedBinary);
        labelsEdited.setCalibration(max.getCalibration());

// cleanup temps and replace 'labels' going forward
        if (correctedBinary != labelsEdited) { correctedBinary.changes = false; correctedBinary.close(); }
        if (labels != labelsEdited)          { labels.changes = false; labels.close(); }
        huReview.changes = false; huReview.close();
        labels = labelsEdited;

        progress.step("Counting ROIs + saving");
        int nHu = rm.getCount();

        //save our stuff here

        //Save outputs (faithful names/locations) ====
        OutputIO.saveRois(rm, new File(outDir, p.cellTypeName + "_unmodified_ROIs_" + baseName + ".zip"));
        OutputIO.saveRois(rm, new File(outDir, p.cellTypeName + "_ROIs_" + baseName + ".zip"));

        if (p.saveFlattenedOverlay && nHu > 0) {
            OutputIO.saveFlattenedOverlay(max, rm, new File(outDir, "MAX_" + baseName + "_overlay.tif"));
        }

        try {
            ImagePlus rgbBase = PluginCalls.buildGangliaRgbForOverlay(max, p.gangliaChannel, p.huChannel);
            OutputIO.saveFlattenedOverlay(rgbBase, rm,
                    new File(outDir, "RGB_" + baseName + "_neurons_overlay.tif"));
            rgbBase.changes = false; rgbBase.close();

        } catch (Throwable t) {
            IJ.log("RGB neuron overlay save skipped: " + t.getMessage());
        }




        rm.reset();
        rm.setVisible(false);

        labels.setTitle("Neuron_label_MAX_" + baseName);
        OutputIO.saveTiff(labels, new File(outDir, labels.getTitle() + ".tif"));
        OutputIO.saveTiff(max,    new File(outDir, "MAX_" + baseName + ".tif"));

        // Minimal CSV with counts (same idea as macro’s table export)
        OutputIO.writeCountsCsv(
                new File(outDir, "Analysis_" + p.cellTypeName + "_" + baseName + "_cell_counts.csv"),
                baseName, p.cellTypeName, nHu
        );




        //hide images
        ImagePlus cur = ij.WindowManager.getCurrentImage();
        if (cur!=null) cur.hide();


        // --- Ganglia (optional) ---
        if (p.cellCountsPerGanglia) {
            progress.step("Segmenting Ganglia");
            // A) Segment ganglia (raw labels from chosen method)
            ImagePlus gangliaLabelsRaw = GangliaOps.segment(p, max, labels);
            gangliaLabelsRaw.setCalibration(max.getCalibration());

            progress.step("Ganglia: pre-count");
            // B) Count neurons per RAW ganglion (to know which have ≥1 neuron)
            GangliaOps.Result rAll = GangliaOps.countPerGanglion(labels, gangliaLabelsRaw);

            progress.step("Ganglia: keep ≥1 neuron");
            // C) Keep only ganglia that contain at least one neuron -> BINARY mask
            //    (macro equivalence: label_overlap >= 1 → Convert to Mask → "ganglia_binary")
            ImagePlus gangliaBinary = GangliaOps.keepGangliaWithAtLeast(gangliaLabelsRaw, rAll.countsPerGanglion, 1);
            gangliaBinary.setCalibration(max.getCalibration());
            gangliaBinary.setTitle("ganglia_binary_MAX_" + baseName);
            OutputIO.saveTiff(gangliaBinary, new File(outDir, gangliaBinary.getTitle() + ".tif"));

            progress.step("Filtering Ganglia Projections");
            // D) Convert the filtered BINARY back to labels for ROI export / area calc
            ij.IJ.run(gangliaBinary, "Fill Holes", "");
            ImagePlus gangliaLabels = PluginCalls.binaryToLabels(gangliaBinary);
            gangliaLabels.setCalibration(max.getCalibration());
            gangliaLabels.setTitle("Ganglia_label_MAX_" + baseName);
            OutputIO.saveTiff(gangliaLabels, new File(outDir, gangliaLabels.getTitle() + ".tif"));

            progress.step("Converting Ganglia to ROI's");
            // E) Convert to ROIs and save (matches macro’s ROI export stage)
            RoiManager rmG = rmh.rm;
            rmG.reset();
            PluginCalls.labelsToRois(gangliaLabels);
            syncToSingleton(new RoiManager[]{ rmG });
            int nG = rmG.getCount();

            //save ganglia roi's
            if (nG > 0) {
                OutputIO.saveRois(rmG, new File(outDir, "Ganglia_ROIs_" + baseName + ".zip"));
            }

            progress.step("Saving Image Overlay's");
            if (p.saveFlattenedOverlay && rmG.getCount() > 0) {
                OutputIO.saveFlattenedOverlay(max, rmG, new File(outDir, "MAX_" + baseName + "_ganglia_overlay.tif"));
            }

            progress.step("Final Ganglia Counting");
            // F) Re-count using the FILTERED labels (parity with post-threshold macro state)
            GangliaOps.Result r = GangliaOps.countPerGanglion(labels, gangliaLabels);

            try {
                ImagePlus rgbBase2 = PluginCalls.buildGangliaRgbForOverlay(max, p.gangliaChannel, p.huChannel);
                OutputIO.saveFlattenedOverlay(rgbBase2, rmG,
                        new File(outDir, "RGB_" + baseName + "_ganglia_overlay.tif"));
                rgbBase2.changes = false; rgbBase2.close();
            } catch (Throwable t) {
                IJ.log("RGB ganglia overlay save skipped: " + t.getMessage());
            }

            rmG.setVisible(false);
            rmG.reset();

            if (huReturn){
                return new HuResult(outDir, baseName, max, labels, nHu, gangliaLabels, r.countsPerGanglion, r.areaUm2, nG);
            }else {

                progress.close();
                OutputIO.writeGangliaCsv(
                        new File(outDir, "Analysis_Ganglia_" + baseName + "_counts.csv"),
                        r.countsPerGanglion, r.areaUm2
                );
                HuResult result = new HuResult(
                        outDir, baseName, max, labels, nHu,
                        gangliaLabels, r.countsPerGanglion, r.areaUm2, nG
                );

                if (p.doSpatialAnalysis) {
                    runSpatialFromHu(result, p);
                }

                SwingUtilities.invokeLater(() -> ResultsUI.promptAndMaybeShow(result));

                return null;
            }



        }



        ij.macro.Interpreter.batchMode = prevBatch;
        if (ownProgress) progress.close();


        maybeCloseRM(rmh);
        if (huReturn){
            return new HuResult(outDir, baseName, max, labels, nHu, null,null,null,null);
        }else {
            HuResult result = new HuResult(
                    outDir, baseName, max, labels, nHu,
                    null,null,null,null
            );
            if (p.doSpatialAnalysis) {
                runSpatialFromHu(result, p);
            }
            maybeCloseRM(rmh);
            SwingUtilities.invokeLater(() -> ResultsUI.promptAndMaybeShow(result));
            return null;
        }


    }




    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }


    private void runSpatialFromHu(HuResult hu, Params p) {
        // 1) Ensure the Hu label map is an open ImageJ window with a known title
        ImagePlus huLabels = hu.neuronLabels.duplicate();
        huLabels.setTitle("Cell_labels"); // matches Single pane convention
        huLabels.show();                  // SpatialSingleCellType looks up by WindowManager title


        // 5) Fire your existing spatial code (no ROI zips needed here)
        try {
            SpatialSingleCellType.execute(
                    p.spatialCellTypeName != null ? p.spatialCellTypeName : "Hu",
                    huLabels.getTitle(),   // title of the shown Hu label image
                    "NA",                  // gangliaBinary not used
                    hu.outDir.getAbsolutePath(),
                    p.spatialExpansionUm != null ? p.spatialExpansionUm : 6.5,
                    Boolean.TRUE.equals(p.spatialSaveParametric),
                    (hu.max.getCalibration() != null && hu.max.getCalibration().pixelWidth > 0)
                            ? hu.max.getCalibration().pixelWidth : 1.0,
                    "NA"                   // roiPath not used
            );
        } catch (Exception ex) {
            IJ.log("Spatial analysis failed: " + ex.getMessage());
        } finally {
            // tidy the transient windows we created
            ImagePlus c = WindowManager.getImage(huLabels.getTitle());
            if (c != null) { c.changes = false; c.close(); }
        }
    }

    public static void applyWatershedInPlace(ImagePlus bin) {
        // Must be an 8-bit binary mask where background=0 and objects=255
        if (bin.getBitDepth() != 8) new ImageConverter(bin).convertToGray8();

        ImageProcessor ip = bin.getProcessor();

        new EDM().toWatershed(ip);
        bin.updateAndDraw();
    }

}
