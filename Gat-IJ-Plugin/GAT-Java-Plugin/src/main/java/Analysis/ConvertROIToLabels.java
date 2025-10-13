package Analysis;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.frame.RoiManager;

// add this import to use your existing helpers
import Features.Core.PluginCalls;

public class ConvertROIToLabels {

    // Creates (or replaces) a window called "label_mapss" with a label image
    // built from the ROIs currently loaded in ROI Manager, using the active image
    // as the canvas. No overlay/macro UI needed.
    public static void execute() {
        ImagePlus canvas = IJ.getImage();
        if (canvas == null) {
            IJ.error("No image open");
            return;
        }

        RoiManager rm = RoiManager.getInstance();
        if (rm == null) rm = new RoiManager();
        if (rm.getCount() == 0) {
            IJ.log("No ROIs in ROI Manager; creating blank label image.");
            // ensure we still produce the expected window
            ImagePlus blank = IJ.createImage("label_mapss", "16-bit black",
                    canvas.getWidth(), canvas.getHeight(), 1);
            blank.setCalibration(canvas.getCalibration());
            blank.show();
            return;
        }

        // Remove any stale output from prior runs
        ImagePlus old = WindowManager.getImage("label_mapss");
        if (old != null) { old.changes = false; old.close(); }

        // Deterministic conversion: ROIs -> binary -> labels
        // (these helpers already exist in your codebase and do not depend on overlay)
        ImagePlus bin = PluginCalls.roisToBinary(canvas, rm);   // same size as canvas
        ImagePlus lab = PluginCalls.binaryToLabels(bin);        // 16-bit label map

        // Match calibration and publish under the fixed name that upstream code expects
        lab.setCalibration(canvas.getCalibration());
        lab.setTitle("label_mapss");
        lab.show();

        // Tidy
        bin.changes = false;
        bin.close();
        rm.reset(); // optional
    }
}
