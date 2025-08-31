
        package UI.panes.Tools;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.measure.Calibration;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

public final class ReviewUI {
    private ReviewUI(){}

    /**
     * Shows backdrop + current ROIs for interactive review, then rebuilds a label map from the final ROIs.
     * If the user deletes everything (or nothing is present), we fall back to 'fallbackLabels'.
     */
    public static ImagePlus reviewAndRebuildLabels(ImagePlus backdrop,
                                                   RoiManager rm,
                                                   String title,
                                                   Calibration cal,
                                                   ImagePlus fallbackLabels) {
        // display
        ImagePlus show = backdrop.duplicate();
        show.setTitle(title);
        show.show();

        rm.setVisible(true);
        // make sure overlay is bound to THIS window and labels are shown
        rm.runCommand(show, "Show None");
        rm.runCommand(show, "Show All with labels");

        IJ.setTool("polygon");
        new WaitForUserDialog(
                "Review: " + title,
                "• Draw a new ROI and press 'T' to add\n" +
                        "• Select a ROI and press Delete to remove\n" +
                        "• Drag vertices to tweak shapes\n" +
                        "Click OK when done."
        ).show();

        rm.runCommand(show, "Show All without labels");

        // rebuild labels from ROIs
        ImagePlus labels = labelsFromRois(show.getWidth(), show.getHeight(), cal, rm);

        // fallback if user ended up with 0 ROIs / 0 labels
        if (countLabels(labels) == 0 && fallbackLabels != null) {
            labels.close();
            labels = fallbackLabels.duplicate(); // preserve original result
            labels.setTitle("labels_from_review_fallback");
        }

        show.changes = false; show.close();
        return labels;
    }

    private static ImagePlus labelsFromRois(int w, int h, Calibration cal, RoiManager rm) {
        ShortProcessor sp = new ShortProcessor(w, h);
        ImageProcessor ip = sp;
        Roi[] rois = rm.getRoisAsArray();
        int id = 1;
        for (Roi r : rois) {
            if (r == null || !r.isArea()) continue; // skip points/lines
            ip.setRoi(r);
            ip.setValue(id & 0xFFFF);
            ip.fill();                 // <-- KEY FIX: fill at ROI’s true position
            id++;
        }
        ImagePlus out = new ImagePlus("labels_from_review", sp);
        out.setCalibration(cal);
        return out;
    }

    private static int countLabels(ImagePlus labels16) {
        short[] px = (short[]) labels16.getProcessor().getPixels();
        int max = 0;
        for (short v : px) {
            int u = v & 0xFFFF;
            if (u > max) max = u;
        }
        return max;
    }
}
