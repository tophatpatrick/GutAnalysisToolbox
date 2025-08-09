package Features.Tools;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.plugin.frame.RoiManager;
import ij.io.FileInfo;

import java.io.*;

public final class OutputIO {
    private OutputIO(){}

    public static File prepareOutputDir(String explicitParent, ImagePlus imp, String baseName) {
        // 1) Resolve parent dir
        File parent;
        if (explicitParent != null && !explicitParent.trim().isEmpty()) {
            parent = new File(explicitParent);
        } else {
            FileInfo fi = imp.getOriginalFileInfo();
            if (fi != null && fi.directory != null && !fi.directory.trim().isEmpty()) {
                parent = new File(fi.directory);
            } else {
                String fallback = IJ.getDirectory("image");
                parent = new File(fallback != null ? fallback : System.getProperty("user.home"));
            }
        }

        // 2) Analysis/<baseName> with mkdirs() checks
        File analysis = new File(parent, "Analysis");
        if (!analysis.exists() && !analysis.mkdirs()) {
            throw new IllegalStateException("Failed to create dir: " + analysis.getAbsolutePath());
        }

        File out = uniqueDir(new File(analysis, baseName));
        if (!out.exists() && !out.mkdirs()) {
            throw new IllegalStateException("Failed to create dir: " + out.getAbsolutePath());
        }

        return out;
    }

    private static File uniqueDir(File target) {
        if (!target.exists()) return target;
        int k = 1;
        while (true) {
            File t = new File(target.getParentFile(), target.getName() + "_" + k);
            if (!t.exists()) return t;
            k++;
        }
    }

    public static void saveRois(RoiManager rm, File zip) {
        rm.runCommand("Save", zip.getAbsolutePath());
    }

    public static void saveTiff(ImagePlus imp, File out) {
        IJ.saveAsTiff(imp.duplicate(), out.getAbsolutePath());
    }

    public static void saveFlattenedOverlay(ImagePlus base, RoiManager rm, File out) {
        ImagePlus dup = base.duplicate();
        rm.runCommand(dup, "Show All with labels");
        Overlay ov = dup.getOverlay();
        if (ov != null) dup.setOverlay(ov);
        IJ.run(dup, "Flatten", "");
        IJ.saveAsTiff(dup, out.getAbsolutePath());
        dup.close();
    }

    public static void writeCountsCsv(File csv, String baseName, String cellType, int count) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(csv))) {
            pw.println("File name,Total " + cellType);
            pw.println(baseName + "," + count);
        } catch (IOException e) {
            IJ.log("Failed writing CSV: " + e.getMessage());
        }
    }
}
