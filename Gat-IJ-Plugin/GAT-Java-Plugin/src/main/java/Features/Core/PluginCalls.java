package Features.Core;


import ij.IJ;
import ij.ImagePlus;
import ij.plugin.frame.RoiManager;

import java.text.DecimalFormat;
import java.util.Locale;

import static javax.print.attribute.standard.MediaTray.MANUAL;

/** Tiny wrappers for plugin calls via IJ.run(...) */
public final class PluginCalls {
    private static final DecimalFormat DF =
            new DecimalFormat("0.######",
                    java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US));


    private PluginCalls() {}

    /** PLUGIN: Bio-Formats (opens image and leaves it active). */
    public static ImagePlus openWithBioFormats(String path) {
        IJ.run("Bio-Formats", "open=[" + path + "] color_mode=Composite rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT");
        return IJ.getImage();
    }

    public static int suggestTiles(int w, int h, double scaleFactor) {
        int newW = (int)Math.round(w * scaleFactor);
        int newH = (int)Math.round(h * scaleFactor);
        int n = 4;
        if (newW > 2000 || newH > 2000) n = 5;
        if (newW > 4500 || newH > 4500) n = 8;
        if (newW > 9000 || newH > 9000) n = 16;
        if (newW > 15000 || newH > 15000) n = 24;
        return n;
    }

    public static ImagePlus runStarDist2DLabel(ImagePlus input, String modelZip, double prob, double nms) {
        input.show();
        int nTiles = suggestTiles(input.getWidth(), input.getHeight(), 1.0); // or scaleFactor used for segInput
        String args =
                "input=[" + input.getTitle() + "] " +
                        "modelChoice=[Model (.zip) from File] " +
                        "modelFile=[" + modelZip + "] " +
                        "normalizeInput=true percentileBottom=1.0 percentileTop=99.8 " +
                        "probThresh=" + DF.format(prob) + " nmsThresh=" + DF.format(nms) + " " +
                        "outputType=[Label Image] nTiles=" + nTiles + " excludeBoundary=2 roiPosition=Automatic " +
                        "verbose=false showCsbdeepProgress=false showProbAndDist=false";
        IJ.run("StarDist 2D", args);
        ImagePlus label = IJ.getImage();
        label.setCalibration(input.getCalibration());
        return label;
    }


    /** PLUGIN: MorphoLibJ Remove Border Labels. */
    public static ImagePlus removeBorderLabels(ImagePlus labels) {
        labels.show();
        IJ.run(labels, "Remove Border Labels", "left right top bottom");
        ImagePlus out = IJ.getImage();
        out.setCalibration(labels.getCalibration());
        if (out != labels) labels.close();
        return out;
    }

    /** PLUGIN: MorphoLibJ Label Size Filtering. */
    public static ImagePlus labelSizeFilter(ImagePlus labels, Double minPx2, Double maxPx2) {
        labels.show();
        if (minPx2 != null && maxPx2 != null) {
            IJ.run(labels, "Label Size Filtering", "operation=Within_Range min=" + Math.max(1, minPx2.intValue()) + " max=" + maxPx2.intValue());
        } else if (minPx2 != null) {
            IJ.run(labels, "Label Size Filtering", "operation=Greater_Than_Or_Equal size=" + Math.max(1, minPx2.intValue()));
        } else if (maxPx2 != null) {
            IJ.run(labels, "Label Size Filtering", "operation=Less_Than_Or_Equal size=" + Math.max(1, maxPx2.intValue()));
        }
        ImagePlus out = IJ.getImage();
        out.setCalibration(labels.getCalibration());
        if (out != labels) labels.close();
        return out;
    }

    /** PLUGIN: MorphoLibJ Label Image -> ROIs (fills ROI Manager). */
    public static void labelsToRois(ImagePlus labels) {
        labels.show();
        IJ.run(labels, "Label Image to ROIs", "");
    }

    /** PLUGIN: CLIJ2 EDF (variance). Returns the pulled image. */
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
        String u = unit.trim().toLowerCase(Locale.ROOT);
        return u.equals("µm") || u.equals("um") || u.equals("micron") || u.equals("microns");
    }

    public static double um2ToPx2(double um2, double pxUm) {
        return um2 / (pxUm * pxUm);
    }

    public static ImagePlus roisToLabels(ImagePlus ref, RoiManager rm) {
        ref.show();
        IJ.selectWindow(ref.getID());
        try {
            IJ.run(ref, "ROI Manager to Label Image", ""); // common name
        } catch (Throwable t1) {
            try {
                IJ.run(ref, "ROI Manager to Label Map", ""); // alt name on some builds
            } catch (Throwable t2) {
                IJ.log("[IJPB] ROI→Labels command not found. Enable IJPB-Plugins update site.");
                return null;
            }
        }
        ImagePlus out = IJ.getImage();
        out.setCalibration(ref.getCalibration());
        return out;
    }

    public static void loadRoiZip(RoiManager rm, String zipPath) {
        if (rm == null) return;
        rm.reset();
        rm.runCommand("Open", zipPath);
    }


}
