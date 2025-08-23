package UI.panes.Tools;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.WaitForUserDialog;
import ij.measure.Calibration;
import ij.plugin.frame.RoiManager;

import Features.Core.PluginCalls;

public final class ReviewUI {
    private ReviewUI(){}

    /**
     * Shows backdrop + current ROIs for interactive review, then rebuilds a label map from the final ROIs.
     * Returns the label map (calibration preserved). Backdrop is closed before returning.
     */
    public static ImagePlus reviewAndRebuildLabels(ImagePlus backdrop,
                                                   RoiManager rm,
                                                   String title,
                                                   Calibration cal) {
        ImagePlus show = backdrop.duplicate();
        show.setTitle(title);
        show.show();

        rm.setVisible(true);
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

        ImagePlus bin = PluginCalls.roisToBinary(show, rm);
        ImagePlus labels = PluginCalls.binaryToLabels(bin);
        labels.setCalibration(cal);

        bin.changes = false; bin.close();
        show.changes = false; show.close();

        return labels;
    }
}
