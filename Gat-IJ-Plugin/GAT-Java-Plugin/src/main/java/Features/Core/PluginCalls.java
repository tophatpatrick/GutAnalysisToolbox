package Features.Core;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.frame.RoiManager;

import java.io.File;
import java.text.DecimalFormat;

public final class PluginCalls {
    private PluginCalls(){}

    private static final DecimalFormat DF = new DecimalFormat("0.######",
            java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US));

    /** Bio-Formats opener that leaves the image active */
    public static ImagePlus openWithBioFormats(String path) {
        IJ.run("Bio-Formats", "open=[" + path + "] color_mode=Composite rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT");
        return IJ.getImage();
    }

    /** CLIJ2 EDF (variance) projection, returns pulled image */
    public static ImagePlus clij2EdfVariance(ImagePlus src) {
        src.show();
        IJ.run("CLIJ2 Macro Extensions", "cl_device=");
        IJ.run("CLIJ2 Push Current Z Stack", "");
        IJ.run("CLIJ2 Extended Depth Of Focus (variance)", "radius_x=2 radius_y=2 sigma=10");
        String outTitle = "EDF_" + src.getTitle();
        IJ.run("CLIJ2 Pull", "destination=" + outTitle);
        IJ.run("CLIJ2 Clear", "");
        ImagePlus out = IJ.getImage();
        out.setTitle(outTitle);
        out.setCalibration(src.getCalibration());
        return out;
    }

    public static boolean isMicronUnit(String unit) {
        if (unit == null) return false;
        String u = unit.trim().toLowerCase(java.util.Locale.ROOT);
        return u.equals("µm") || u.equals("um") || u.equals("micron") || u.equals("microns");
    }

    /** MorphoLibJ: Label Image -> ROIs */
    public static void labelsToRois(ImagePlus labels) {
        labels.show();
        IJ.run(labels, "Label Image to ROIs", "");
    }

    /** MorphoLibJ: Remove labels touching borders */
    public static ImagePlus removeBorderLabels(ImagePlus labels) {
        labels.show();
        IJ.run(labels, "Remove Border Labels", "left right top bottom");
        ImagePlus out = IJ.getImage();
        out.setCalibration(labels.getCalibration());
        if (out != labels) labels.close();
        return out;
    }

    /** MorphoLibJ: Label Size Filtering (>= threshold in pixels; faithful to macro) */
    public static ImagePlus labelMinSizeFilterPx(ImagePlus labels, int minPx) {
        labels.show();
        IJ.run(labels, "Label Size Filtering", "operation=Greater_Than_Or_Equal size=" + Math.max(1, minPx));
        ImagePlus out = IJ.getImage();
        out.setCalibration(labels.getCalibration());
        if (out != labels) labels.close();
        return out;
    }

    /** DeepImageJ + model's stardist_postprocessing.ijm → returns label image */
    public static ImagePlus runDeepImageJNeuronLabel(ImagePlus input, File modelDir, double prob, double overlap) {
        if (modelDir == null || !modelDir.isDirectory())
            throw new IllegalArgumentException("DeepImageJ model folder not found: " + modelDir);

        File post = new File(modelDir, "stardist_postprocessing.ijm");
        if (!post.isFile())
            throw new IllegalStateException("Missing stardist_postprocessing.ijm in model folder: " + post);

        input.show();
        String args = "modelPath=[" + modelDir.getAbsolutePath() + "] inputPath=null outputFolder=null displayOutput=all";
        IJ.run("DeepImageJ Run", args);

        // The DIJ output should be the active image
        ImagePlus dijOut = IJ.getImage();

        // Run the model-provided post-processing macro (prob, overlap)
        String macroArgs = DF.format(prob) + "," + DF.format(overlap);
        IJ.runMacroFile(post.getAbsolutePath(), macroArgs);

        // Post-processing produces a new active image (label or intermediate)
        ImagePlus out = IJ.getImage();
        out.setCalibration(input.getCalibration());

        // Close the raw DIJ output if it’s different
        if (dijOut != out) dijOut.close();

        return out;
    }
}
