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
        // MorphoLibJ "Label Map to ROIs" options to suppress the dialog
        String opts = "Connectivity=C4 Vertex Location=Corners Name Pattern=r%03d";

        IJ.run(labels, "Label Map to ROIs", opts);
    }

    public static ImagePlus removeBorderLabels(ImagePlus labels) {
        ImagePlus lab2d = ensure2DLabel(labels);
        lab2d.show();
        IJ.run(lab2d, "Remove Border Labels", "left right top bottom");
        ImagePlus out = IJ.getImage();
        out.setCalibration(lab2d.getCalibration());
        if (out != lab2d) lab2d.close();
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

    /** Heuristic tiling, same thresholds as the macro (after potential rescale) */
    public static int suggestTiles(int w, int h) {
        int n = 4;
        if (w > 2000 || h > 2000) n = 5;
        if (w > 4500 || h > 4500) n = 8;
        if (w > 9000 || h > 9000) n = 16;
        if (w > 15000 || h > 15000) n = 24;
        return n;
    }


    /**
     * StarDist 2D (ZIP model) -> Label Image.
     * Mirrors the macro’s call (normalize 1–99.8, user prob/nms, output=Label Image).
     */
    public static ImagePlus runStarDist2DLabel(ImagePlus input, String modelZip, double prob, double nms) {
        if (modelZip == null || !new File(modelZip).isFile())
            throw new IllegalArgumentException("StarDist ZIP not found: " + modelZip);

        input.show();
        IJ.selectWindow(input.getID());
        int nTiles = suggestTiles(input.getWidth(), input.getHeight());

        String title = input.getTitle().replace("'", ""); // be safe with quotes
        String modelEsc = modelZip.replace("\\", "\\\\"); // Windows escaping

        String args =
                "command=[de.csbdresden.stardist.StarDist2D],"
                        + "args=['input':'" + title + "',"
                        + " 'modelChoice':'Model (.zip) from File',"
                        + " 'normalizeInput':'true', 'percentileBottom':'1.0', 'percentileTop':'99.8',"
                        + " 'probThresh':'" + DF.format(prob) + "', 'nmsThresh':'" + DF.format(nms) + "',"
                        + " 'outputType':'Label Image'," // or 'Both' if you want ROIs too
                        + " 'modelFile':'" + modelEsc + "', 'nTiles':'" + nTiles + "',"
                        + " 'excludeBoundary':'2', 'roiPosition':'Automatic',"
                        + " 'verbose':'false','showCsbdeepProgress':'false','showProbAndDist':'false'],"
                        + " process=[false]";

        IJ.run("Command From Macro", args);


        ImagePlus label = IJ.getImage();
        label.setCalibration(input.getCalibration());
        return label;
    }

    private static ImagePlus ensure2DLabel(ImagePlus src) {
        ImagePlus lab2d;
        if (src.getStackSize() > 1) {
            lab2d = new ImagePlus("labels2d", src.getStack().getProcessor(1).duplicate());
        } else {
            lab2d = src.duplicate();
        }
        lab2d.setCalibration(src.getCalibration());
        if (lab2d.getType() != ImagePlus.GRAY16) {
            IJ.run(lab2d, "16-bit", ""); // MorphoLibJ label ops are happiest with 16-bit labels
        }
        return lab2d;
    }


}
