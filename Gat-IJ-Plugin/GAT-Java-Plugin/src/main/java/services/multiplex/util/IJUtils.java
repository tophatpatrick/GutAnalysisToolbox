package services.multiplex.util;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.frame.RoiManager;

/** Minimal ImageJ helpers. */
public final class IJUtils {
    private IJUtils() {}

    public static void ensureRoiManagerReset() {
        RoiManager rm = RoiManager.getInstance2();
        if (rm != null) rm.reset();
        else new RoiManager().reset();
    }

    public static void closeAllNonImageWindows() {
        IJ.run("Close All");
    }

    public static void selectWindow(String title) {
        ImagePlus imp = WindowManager.getImage(title);
        if (imp != null) WindowManager.setCurrentWindow(imp.getWindow());
    }

    public static void copyActiveToClipboard() {
        IJ.run("Select All");
        IJ.run("Copy");
        IJ.run("Select None");
    }
}
