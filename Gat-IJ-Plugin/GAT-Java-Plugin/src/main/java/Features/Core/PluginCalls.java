package Features.Core;

import Features.Tools.ProgressUI;
import Features.Tools.SilentRun;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.frame.RoiManager;

import javax.swing.*;
import java.awt.*;
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

    //Build the ganglia rgb image so that we can display it in the results page
    public static ImagePlus buildGangliaRgbForOverlay(ImagePlus maxProj, int gangliaCh1, int huCh1) {
        ImagePlus g  = Features.Tools.ImageOps.extractChannel(maxProj, gangliaCh1);
        ImagePlus hu = Features.Tools.ImageOps.extractChannel(maxProj, huCh1);
        IJ.resetMinAndMax(g);  IJ.resetMinAndMax(hu);

        ij.process.ByteProcessor r8 = (ij.process.ByteProcessor) hu.getProcessor().convertToByte(true);
        ij.process.ByteProcessor g8 = (ij.process.ByteProcessor) g .getProcessor().convertToByte(true);
        ij.process.ByteProcessor b8 = (ij.process.ByteProcessor) hu.getProcessor().convertToByte(true);

        ij.process.ColorProcessor cp = new ij.process.ColorProcessor(maxProj.getWidth(), maxProj.getHeight());
        cp.setRGB((byte[]) r8.getPixels(), (byte[]) g8.getPixels(), (byte[]) b8.getPixels());

        ImagePlus rgb = new ImagePlus("ganglia_rgb_base", cp);
        rgb.setCalibration(maxProj.getCalibration());
        rgb.hide();

        g.changes=false; g.close();
        hu.changes=false; hu.close();
        return rgb;
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


    public static final class GangliaPrep {
        public final ImagePlus dijInput3C;   // 3-channel, 32-bit hyperstack (C=3,Z=1,T=1), 0..1
        public final ImagePlus rgbForOverlay; // RGB Color image for painting overlay
        GangliaPrep(ImagePlus d, ImagePlus r) { dijInput3C=d; rgbForOverlay=r; }
    }

    /** Prepare both inputs with no IJ.run converters/dialogs. */
    public static GangliaPrep prepareGangliaInputs(ImagePlus maxProj, int gangliaCh1, int huCh1) {
        // 1) Extract the two source channels (grayscale, no UI)
        ImagePlus g  = Features.Tools.ImageOps.extractChannel(maxProj, gangliaCh1); // ganglia marker
        ImagePlus hu = Features.Tools.ImageOps.extractChannel(maxProj, huCh1);      // Hu (cells)
        IJ.resetMinAndMax(g);  IJ.resetMinAndMax(hu);  // define display ranges

        final int w = maxProj.getWidth(), h = maxProj.getHeight();

        // 2) Build the review RGB image: R=Hu, G=Ganglia, B=Hu  → Hu appears magenta, ganglia green
        //    Use convertToByte(true) so display range is respected (matches macro look).
        ij.process.ByteProcessor r8 = (ij.process.ByteProcessor) hu.getProcessor().convertToByte(true);
        ij.process.ByteProcessor g8 = (ij.process.ByteProcessor) g .getProcessor().convertToByte(true);
        ij.process.ByteProcessor b8 = (ij.process.ByteProcessor) hu.getProcessor().convertToByte(true);

        ij.process.ColorProcessor cp = new ij.process.ColorProcessor(w, h);
        cp.setRGB((byte[]) r8.getPixels(), (byte[]) g8.getPixels(), (byte[]) b8.getPixels());

        ImagePlus rgb = new ImagePlus("ganglia_rgb", cp);
        rgb.setCalibration(maxProj.getCalibration());
        rgb.hide();

        // Optional: keep a hidden copy with the old helper name if any code still expects it.
        ImagePlus rgb2 = new ImagePlus("ganglia_rgb_2", (ij.process.ColorProcessor) cp.duplicate());
        rgb2.setCalibration(maxProj.getCalibration());
        rgb2.hide();

        // 3) Build DeepImageJ input: 3 slices of float 0..1, exposed as C=3 hyperstack
        ij.process.FloatProcessor rf = r8.convertToFloatProcessor(); rf.multiply(1.0/255.0);
        ij.process.FloatProcessor gf = g8.convertToFloatProcessor(); gf.multiply(1.0/255.0);
        ij.process.FloatProcessor bf = b8.convertToFloatProcessor(); bf.multiply(1.0/255.0);

        ImageStack st = new ImageStack(w, h);
        st.addSlice("R", rf); st.addSlice("G", gf); st.addSlice("B", bf);

        ImagePlus dij = new ImagePlus("ganglia_rgb", st);   // title kept stable for DIJ
        dij.setDimensions(3, 1, 1);                         // C=3
        dij.setOpenAsHyperStack(true);
        dij.setCalibration(maxProj.getCalibration());
        dij.hide();

        // Tidy temps
        g.changes = false;  g.close();
        hu.changes = false; hu.close();

        return new GangliaPrep(dij, rgb);
    }






    // full macro-faithful path for ganglia (RGB + im_preprocessing + DIJ + post)
    public static ImagePlus runDeepImageJForGanglia(
            ImagePlus maxProj, int gangliaCh1, int huCh1,
            String modelFolderName, double minAreaUm2, Params p, ProgressUI progress) {

        GangliaPrep prep = prepareGangliaInputs(maxProj, gangliaCh1, huCh1);
        ImagePlus in3C = prep.dijInput3C;        // feed DIJ
        ImagePlus rgbColor = prep.rgbForOverlay;

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
        SilentRun.runAndGrab(out, "Size Opening 2D/3D", "min=" + Math.max(1, minAreaPx));

        // Optional interactive review
        // --- Interactive review ---
        if (p != null && p.gangliaInteractiveReview) {
            ij.macro.Interpreter.batchMode = false;
            progress.setVisible(false);

            out.setTitle("ganglia_mask");

            // colored overlay
            ij.gui.ImageRoi ir = new ij.gui.ImageRoi(0, 0, rgbColor.getProcessor().duplicate());
            ir.setOpacity(0.60);
            out.setOverlay(new ij.gui.Overlay(ir));

            // make sure *this* window has focus
            out.show();
            IJ.selectWindow(out.getID());
            if (out.getWindow() != null) {
                out.getWindow().toFront();
                if (out.getCanvas() != null) out.getCanvas().requestFocusInWindow();
            }

            // set brush tool robustly (Toolbar API + string fallback)

            IJ.setTool("Paintbrush Tool");

            // ensure FG/BG are correct for painting; X will toggle them
            IJ.setForegroundColor(255, 255, 255);   // WHITE = add
            IJ.setBackgroundColor(0, 0, 0);         // BLACK = remove

            // the caption you liked before
            showPaintPalette(
                    out.getWindow(),                       // owner; use GatWindows.owner() if you prefer
                    "Ganglia overlay",
                    "Paint on 'ganglia_mask'. WHITE adds, BLACK removes."
            );

            // clean up + hide the review window so it doesn't reappear later
            IJ.run(out, "Select None", "");
            out.setOverlay(null);
            if (out.getWindow() != null) out.hide();

            ij.macro.Interpreter.batchMode = true;
            progress.setVisible(true);

            // optional tidy
            rgbColor.changes = false; rgbColor.close();
        }



        // Second Size Opening pass
        out = SilentRun.runAndGrab(out, "Size Opening 2D/3D", "min=" + Math.max(1, minAreaPx));
        out = SilentRun.runAndGrab(out, "Size Opening 2D/3D", "min=" + Math.max(1, minAreaPx));
        // Cleanup temps
        if (rgbColor != in3C) { rgbColor.changes = false; rgbColor.close(); }
        if (in3C != out)       { in3C.changes = false; in3C.close(); }
        ImagePlus rgb2 = ij.WindowManager.getImage("ganglia_rgb_2");
        if (rgb2 != null) { rgb2.changes = false; rgb2.close(); }

        // Return final binary (macro keeps binary here)
        if (out.getWindow() != null) out.hide();
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
        labels.hide();
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

    // --- keep these tiny helpers (or add them if you don't have them) ---
    private static void setAddMode() { ij.IJ.setForegroundColor(255,255,255); ij.IJ.setBackgroundColor(0,0,0); }
    private static void setEraseMode(){ ij.IJ.setForegroundColor(0,0,0);     ij.IJ.setBackgroundColor(255,255,255); }

    /** Modeless palette: user can paint on the image while it's open.
     *  Blocks the calling (pipeline) thread until "Done" is pressed (uses a latch).
     */
    public static void showPaintPalette(java.awt.Window owner, String title, String helpLine) {
        // start in ADD mode by default
        setAddMode();

        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

        final JDialog dlg = new JDialog(owner, title, Dialog.ModalityType.MODELESS);
        dlg.setAlwaysOnTop(true);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(8,8));
        root.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        if (helpLine != null && !helpLine.isEmpty()) {
            JLabel tip = new JLabel("<html>" + helpLine + "<br/>Tip: toggle paint on/off with button.</html>");
            root.add(tip, BorderLayout.NORTH);
        }

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JToggleButton mode = new JToggleButton("Add (white)");
        JButton done = new JButton("Done");

        mode.addActionListener(e -> {
            if (mode.isSelected()) { setEraseMode(); mode.setText("Erase (black)"); }
            else                   { setAddMode();   mode.setText("Add (white)");   }
        });
        done.addActionListener(e -> { dlg.dispose(); latch.countDown(); });

        row.add(mode); row.add(done);
        root.add(row, BorderLayout.CENTER);

        dlg.setContentPane(root);
        dlg.pack();
        dlg.setResizable(false);
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);

        // Give focus back to the image window/canvas right after showing the palette
        if (owner != null) {
            SwingUtilities.invokeLater(() -> {
                owner.toFront();
                owner.requestFocus();
            });
        }

        // Block ONLY the calling thread (not the EDT). Your pipeline should be off the EDT.
        if (!SwingUtilities.isEventDispatchThread()) {
            try { latch.await(); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        }
    }







}