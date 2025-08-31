package Features.Core;

import Features.Tools.SilentRun;
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
        ImagePlus imp = IJ.getImage();
        if (imp != null) imp.hide();     // <-- keep it open/active, but not visible
        return imp;
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
        out.hide();
        return out;
    }

    public static boolean isMicronUnit(String unit) {
        if (unit == null) return false;
        String u = unit.trim().toLowerCase(java.util.Locale.ROOT);
        return u.equals("µm") || u.equals("um") || u.equals("micron") || u.equals("microns");
    }

    /** MorphoLibJ: Label Image -> ROIs */
    public static void labelsToRois(ImagePlus labels) {
        // MorphoLibJ "Label Map to ROIs"
        String opts = "Connectivity=C8 Vertex Location=Corners Name Pattern=r%03d";
        SilentRun.on(labels, "Label Map to ROIs", opts);
    }

    public static ImagePlus removeBorderLabels(ImagePlus labels) {
        ImagePlus lab2d = ensure2DLabel(labels);
        int[] before = ij.WindowManager.getIDList();
        SilentRun.on(lab2d, "Remove Border Labels", "left right top bottom");
        ImagePlus out = findNewImageSince(before);
        if (out == null) out = IJ.getImage();
        if (out == null) throw new IllegalStateException("Remove Border Labels produced no output.");

        out.setCalibration(lab2d.getCalibration());
        if (out != lab2d) lab2d.close();
        if (out != labels) labels.close();
        out.hide();
        return out;
    }

    /** MorphoLibJ: Label Size Filtering (>= threshold in pixels; faithful to macro) */
    public static ImagePlus labelMinSizeFilterPx(ImagePlus labels, int minPx) {
        int[] before = ij.WindowManager.getIDList();
        SilentRun.on(labels, "Label Size Filtering",
                "operation=Greater_Than_Or_Equal size=" + Math.max(1, minPx));
        ImagePlus out = findNewImageSince(before);
        if (out == null) out = IJ.getImage();
        if (out == null) throw new IllegalStateException("Label Size Filtering produced no output.");
        out.hide();
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
// in PluginCalls
    // --- Add this helper near the top of PluginCalls ---
    private static void showHidden(ImagePlus imp) {
        if (imp == null) return;
        // Ensure the image is registered for lookup by title,
        // but don't let the window be visible.
        if (imp.getWindow() == null) imp.show();
        if (imp.getWindow() != null) imp.getWindow().setVisible(false);
    }

    // --- Replace your runStarDist2DLabel with this version ---
    public static ImagePlus runStarDist2DLabel(ImagePlus input, String modelZip, double prob, double nms) {
        if (modelZip == null || !new File(modelZip).isFile())
            throw new IllegalArgumentException("StarDist ZIP not found: " + modelZip);

        // Stable, safe title for macro binding
        String uniq = input.getTitle();
        if (uniq == null || uniq.isEmpty() || uniq.contains(".")) {
            uniq = "SDIN_" + System.nanoTime();
            input.setTitle(uniq);
        }
        uniq = uniq.replace("'", "");

        // Register, but keep the window invisible
        showHidden(input);

        // Snapshot open images so we can detect StarDist's output deterministically
        int[] before = ij.WindowManager.getIDList();

        int nTiles = suggestTiles(input.getWidth(), input.getHeight());
        String modelEsc = modelZip.replace("\\", "\\\\");
        String args =
                "command=[de.csbdresden.stardist.StarDist2D],"
                        + "args=['input':'" + uniq + "',"
                        + " 'modelChoice':'Model (.zip) from File',"
                        + " 'normalizeInput':'true','percentileBottom':'1.0','percentileTop':'99.8',"
                        + " 'probThresh':'" + DF.format(prob) + "','nmsThresh':'" + DF.format(nms) + "',"
                        + " 'outputType':'Label Image',"
                        + " 'modelFile':'" + modelEsc + "', 'nTiles':'" + nTiles + "',"
                        + " 'excludeBoundary':'2','roiPosition':'Automatic',"
                        + " 'verbose':'false','showCsbdeepProgress':'false','showProbAndDist':'false'],"
                        + " process=[false]";

        // Run the command; StarDist finds the input by its title
        IJ.run("Command From Macro", args);

        // Locate the label image that StarDist created
        ImagePlus label = findNewImageSince(before);
        if (label == null) {
            // Fallback for some builds that name it literally "Label Image" or "(V)"
            ImagePlus byName = ij.WindowManager.getImage("Label Image");
            if (byName == null) byName = ij.WindowManager.getImage("Label Image (V)");
            label = (byName != null) ? byName : IJ.getImage();
        }
        if (label == null) throw new IllegalStateException("StarDist did not return a label image.");

        // Keep silent and propagate calibration
        label.setCalibration(input.getCalibration());
        label.hide(); // window remains registered but not visible
        return label;
    }


    /** Return the newly-created ImagePlus since the 'before' snapshot, or null if none. */
    public static ImagePlus findNewImageSince(int[] beforeIds) {
        java.util.HashSet<Integer> prev = new java.util.HashSet<>();
        if (beforeIds != null) for (int id : beforeIds) prev.add(id);

        int[] after = ij.WindowManager.getIDList();
        if (after == null) return null;

        for (int id : after) {
            if (!prev.contains(id)) {
                return ij.WindowManager.getImage(id);
            }
        }
        return null;
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
        ImagePlus rgb2 = IJ.getImage();
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

        // DIJ model folder
        File fiji = new File(IJ.getDirectory("imagej"));
        File modelDir = new File(new File(fiji, "models"), modelFolderName);
        if (!modelDir.isDirectory())
            throw new IllegalArgumentException("DeepImageJ model folder not found: " + modelDir);

        int[] before = ij.WindowManager.getIDList();
        IJ.run(in3C, "DeepImageJ Run", "model_path=[" + modelDir.getAbsolutePath() + "] input_path=null output_folder=null display_output=all");
        ImagePlus out = Features.Core.PluginCalls.findNewImageSince(before);
        if (out == null) out = IJ.getImage();
        if (out == null) throw new IllegalStateException("DeepImageJ produced no output.");
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

        // Optional interactive review
        if (p != null && p.gangliaInteractiveReview) {
            out.setTitle("ganglia_mask");                  // clear title
            ImagePlus rgb2 = ij.WindowManager.getImage("ganglia_rgb_2");

            // Put the color image on top of the mask as a translucent overlay
            if (rgb2 != null) {
                ij.gui.ImageRoi ir = new ij.gui.ImageRoi(0, 0, rgb2.getProcessor().duplicate());
                ir.setOpacity(0.60);                       // 0..1
                ij.gui.Overlay ov = new ij.gui.Overlay(ir);
                out.setOverlay(ov);
            }

            // Make sure we're painting the MASK
            out.show();
            if (out.getWindow() != null) out.getWindow().toFront();

            // Brush semantics: white = add, black = remove (X toggles fg/bg in ImageJ)
            IJ.setTool("brush");
            IJ.setForegroundColor(255, 255, 255);
            IJ.setBackgroundColor(0,   0,   0);

            new ij.gui.WaitForUserDialog(
                    "Ganglia overlay",
                    "Paint on the window titled 'ganglia_mask'.\n" +
                            "WHITE adds ganglia, BLACK removes (press 'X' to toggle).\n" +
                            "Click OK when done."
            ).show();

            IJ.run(out, "Select None", "");
            out.setOverlay(null);                          // don't keep overlay in saved TIFFs
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
    public static void clearThreshold(ImagePlus imp) {
        if (imp != null && imp.getProcessor() != null) {
            imp.getProcessor().resetThreshold();  // clears the red overlay
            imp.updateAndDraw();
        }
    }


    public static ImagePlus probToBinary(ImagePlus prob, double thresh01) {
        if (prob.getBitDepth() != 8) IJ.run(prob, "8-bit", ""); // scales 0..255
        int t = (int)Math.round(Math.max(0, Math.min(255, thresh01 * 255.0)));
        IJ.setThreshold(prob, t, 255);
        SilentRun.on(prob, "Convert to Mask", "");
        clearThreshold(prob);
        prob.hide();
        return prob; // now an 8-bit mask
    }



    /** From a BINARY mask -> label image (connected components). */
    public static ImagePlus binaryToLabels(ImagePlus binary) {
        SilentRun.on(binary, "Convert to Mask", "");
        int[] before = ij.WindowManager.getIDList();
        SilentRun.on(binary, "Connected Components Labeling", "connectivity=8");
        ImagePlus labels = findNewImageSince(before);
        if (labels == null) labels = IJ.getImage();
        if (labels == null) throw new IllegalStateException("Connected Components produced no output.");

        labels.setCalibration(binary.getCalibration());
        clearThreshold(labels);
        return labels;
    }

    /** Fill ROIs into a blank mask with same size as ref. */
    public static ImagePlus roisToBinary(ImagePlus ref, RoiManager rm) {
        ImagePlus mask = IJ.createImage("ganglia_binary", "8-bit black", ref.getWidth(), ref.getHeight(), 1);
        mask.setCalibration(ref.getCalibration());

        // rm bindings can target an ImagePlus without showing:
        rm.runCommand("Associate", "true");
        rm.runCommand(mask, "Show All without labels");
        rm.runCommand(mask, "Deselect");
        rm.runCommand(mask, "Fill");
        return mask;
    }





}
