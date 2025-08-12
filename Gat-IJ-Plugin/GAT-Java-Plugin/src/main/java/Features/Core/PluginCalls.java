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
        // MorphoLibJ "Label Map to ROIs"
        String opts = "Connectivity=C8 Vertex Location=Corners Name Pattern=r%03d";

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

    /** Build 3-channel composite for DIJ: C1=ganglia marker, C2=Hu, C3=blank */
    public static ImagePlus buildGangliaRGB(ImagePlus maxProj, int gangliaCh1, int huCh1) {
        IJ.run(maxProj, "Select None", "");
        IJ.run(maxProj, "Duplicate...", "title=ganglia_ch duplicate channels=" + gangliaCh1);
        ImagePlus g = IJ.getImage();
        IJ.resetMinAndMax(g);            // <-- important
        IJ.run(g, "Green", "");

        IJ.run(maxProj, "Duplicate...", "title=cells_ch duplicate channels=" + huCh1);
        ImagePlus h = IJ.getImage();
        IJ.resetMinAndMax(h);            // <-- important
        IJ.run(h, "Magenta", "");

        IJ.run("Merge Channels...", "c1=[ganglia_ch] c2=[cells_ch] create");
        ImagePlus comp = IJ.getImage();

        IJ.run(comp, "RGB Color", "");
        ImagePlus rgb = IJ.getImage();
        rgb.setTitle("ganglia_rgb");
        rgb.setCalibration(maxProj.getCalibration());

        IJ.run("Duplicate...", "title=ganglia_rgb_2");
        comp.changes = false; comp.close();
        g.changes = false; g.close();
        h.changes = false; h.close();
        return rgb;
    }

    /** Macro-faithful preprocessing:
     *  RGB Color -> RGB Stack (3 slices) -> 32-bit -> divide by 255 (each slice)
     *  -> promote to a 3-channel hyperstack (C=3, Z=1, T=1) so DIJ sees "Channel".
     */
    private static ImagePlus preprocessGangliaLikeMacro(ImagePlus rgb) {
        rgb.show(); IJ.selectWindow(rgb.getID());
        IJ.run(rgb, "RGB Stack", "");          // 3 slices: R, G, B
        ImagePlus st = IJ.getImage();
        IJ.run(st, "32-bit", "");

        int n = st.getStackSize();
        for (int s = 1; s <= n; s++) {
            st.setSlice(s);
            IJ.run(st, "Divide...", "value=255 slice");   // 0..1
        }
        // No "Make Composite" here — let DIJ read 3 slices as 3 channels.
        st.setTitle("ganglia_rgb");                        // keep title stable
        st.setCalibration(rgb.getCalibration());
        return st;
    }


    // full macro-faithful path for ganglia (RGB + im_preprocessing + DIJ + post)
    public static ImagePlus runDeepImageJForGanglia(
            ImagePlus maxProj, int gangliaCh1, int huCh1,
            String modelFolderName, double minAreaUm2, Params p) {

        // Build exactly like the macro: C1=ganglia marker, C2=Hu, then RGB Color
        ImagePlus rgbColor = buildGangliaRGB(maxProj, gangliaCh1, huCh1);

        // Inline preprocessing (macro-equivalent)
        ImagePlus in3C = preprocessGangliaLikeMacro(rgbColor);
        IJ.log("[GAT] DIJ input C/Z/T=" + in3C.getNChannels() + "/" + in3C.getNSlices() + "/" + in3C.getNFrames()
                + " bitDepth=" + in3C.getBitDepth());

        // DIJ model folder
        File fiji = new File(IJ.getDirectory("imagej"));
        File modelDir = new File(new File(fiji, "models"), modelFolderName);
        if (!modelDir.isDirectory())
            throw new IllegalArgumentException("DeepImageJ model folder not found: " + modelDir);

        // Run DeepImageJ
        String args = "model_path=[" + modelDir.getAbsolutePath() + "] input_path=null output_folder=null display_output=all";
        IJ.run(in3C, "DeepImageJ Run", args);
        ImagePlus out = IJ.getImage();
        out.setCalibration(maxProj.getCalibration());

        if (p != null && p.gangliaProbThresh01 != null) {
            out = probToBinary(out, p.gangliaProbThresh01);
        }

        // Binary Open (same as macro "Options..." with do=Open)
        int it = (p != null ? Math.max(0, p.gangliaOpenIterations) : 3);
        IJ.run(out, "Options...", "iterations=" + it + " count=2 black do=Open");

        // Size Opening in µm² -> px using MAX calibration
        double px = (maxProj.getCalibration() != null && maxProj.getCalibration().pixelWidth > 0)
                ? maxProj.getCalibration().pixelWidth : 1.0;
        double areaUm2 = (minAreaUm2 > 0 ? minAreaUm2
                : (p != null && p.gangliaMinAreaUm2 != null ? p.gangliaMinAreaUm2 : 200.0));
        int minAreaPx = (int)Math.ceil(areaUm2 / (px * px));
        IJ.run(out, "Size Opening 2D/3D", "min=" + Math.max(1, minAreaPx));

        // Optional interactive review (matches macro UX)
        if (p != null && p.gangliaInteractiveReview) {
            ImagePlus rgb2 = ij.WindowManager.getImage("ganglia_rgb_2");
            if (rgb2 != null) {
                IJ.run(out, "Image to Selection...", "image=[" + rgb2.getTitle() + "] opacity=60");
            }
            IJ.setTool("brush");
            new ij.gui.WaitForUserDialog(
                    "Ganglia overlay",
                    "Use the Brush tool to add (white) or remove (black) ganglia.\nClick OK when done."
            ).show();
            IJ.run(out, "Select None", "");
        }

        // Second Size Opening pass (macro does this unconditionally)
        IJ.run(out, "Size Opening 2D/3D", "min=" + Math.max(1, minAreaPx));
        IJ.run(out, "Size Opening 2D/3D", "min=" + Math.max(1, minAreaPx));

        // Cleanup temps
        if (rgbColor != in3C) { rgbColor.changes = false; rgbColor.close(); }
        if (in3C != out)       { in3C.changes = false; in3C.close(); }
        ImagePlus rgb2 = ij.WindowManager.getImage("ganglia_rgb_2");
        if (rgb2 != null) { rgb2.changes = false; rgb2.close(); }

        // Return final binary (macro keeps binary here)
        return out;
    }

    public static ImagePlus probToBinary(ImagePlus prob, double thresh01) {
        prob.show();
        if (prob.getBitDepth() != 8) IJ.run(prob, "8-bit", ""); // scales 0..255
        int t = (int)Math.round(Math.max(0, Math.min(255, thresh01 * 255.0)));
        IJ.setThreshold(prob, t, 255);
        IJ.run(prob, "Convert to Mask", "");
        return prob; // now an 8-bit mask
    }



    /** From a BINARY mask -> label image (connected components). */
    public static ImagePlus binaryToLabels(ImagePlus binary) {
        // ensure 8-bit mask
        binary.show();
        IJ.run(binary, "Convert to Mask", "");
        // 2D connected components → label image
        IJ.run(binary, "Connected Components Labeling", "connectivity=8");
        ImagePlus labels = IJ.getImage();
        labels.setCalibration(binary.getCalibration());
        return labels;
    }

    /** Fill ROIs into a blank mask with same size as ref. */
    public static ImagePlus roisToBinary(ImagePlus ref, RoiManager rm) {
        ImagePlus mask = IJ.createImage("ganglia_binary", "8-bit black",
                ref.getWidth(), ref.getHeight(), 1);
        mask.setCalibration(ref.getCalibration());
        mask.show();
        IJ.run(mask, "Select None", "");
        rm.runCommand(mask, "Show All without labels");
        rm.runCommand(mask, "Deselect");
        rm.runCommand(mask, "Measure"); // no-op, just ensure manager is bound
        rm.runCommand(mask, "Fill");
        IJ.run(mask, "Select None", "");
        return mask;
    }





}
