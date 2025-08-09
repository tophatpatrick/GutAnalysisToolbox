package Features.Tools;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;

public final class OutputIO {
    private OutputIO() {}

    public static File prepareOutputDir(String outputDir, ImagePlus imp, String baseName) {
        File dir = (outputDir == null)
                ? new File(
                imp != null && imp.getOriginalFileInfo() != null && imp.getOriginalFileInfo().directory != null
                        ? imp.getOriginalFileInfo().directory
                        : System.getProperty("user.home"),
                "Analysis" + File.separator + baseName)
                : new File(outputDir);
        try { Files.createDirectories(dir.toPath()); } catch (Exception ignored) {}
        return dir;
    }

    public static void saveTiff(ImagePlus imp, File out) {
        IJ.saveAsTiff(imp, out.getAbsolutePath());
    }

    public static void saveRois(RoiManager rm, File outZip) {
        if (rm == null) return;
        rm.runCommand("Save", outZip.getAbsolutePath());
    }

    public static void saveFlattenedOverlay(ImagePlus base, RoiManager rm, File outTif) {
        if (base == null) return;

        Roi[] rois = getRoisSafe(rm);

        // Preserve any existing overlay
        Overlay old = base.getOverlay();
        try {
            Overlay overlay = null;
            if (rois.length > 0) {
                overlay = new Overlay();
                for (Roi r : rois) {
                    if (r != null) overlay.add((Roi) r.clone());
                }
            }
            base.setOverlay(overlay);
            ImagePlus flat = base.flatten();
            IJ.saveAs(flat, "Tiff", outTif.getAbsolutePath());
            flat.close();
        } finally {
            base.setOverlay(old);
        }
    }

    private static Roi[] getRoisSafe(RoiManager rm) {
        if (rm == null) return new Roi[0];
        try {
            // Correct method name/case
            return rm.getRoisAsArray();
        } catch (Throwable t) {
            // Back-compact fallback
            int n = rm.getCount();
            Roi[] arr = new Roi[n];
            for (int i = 0; i < n; i++) arr[i] = rm.getRoi(i);
            return arr;
        }
    }

    public static void writeCountsCsv(File outCsv, String baseName, String cellType, int total) {
        try (FileWriter fw = new FileWriter(outCsv)) {
            fw.write("File name,Total " + cellType + "\n");
            fw.write(baseName + "," + total + "\n");
        } catch (Exception e) {
            IJ.handleException(e);
        }
    }
}
