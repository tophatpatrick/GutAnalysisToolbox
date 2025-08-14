package Features.Tools;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.io.FileSaver;
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
        new FileSaver(imp).saveAsTiff(out.getAbsolutePath());
    }

    public static void saveFlattenedOverlay(ImagePlus base, RoiManager rm, File out) {
        ImagePlus dup = base.duplicate();
        dup.hide();
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

    public static void writeGangliaCsv(File out, int[] counts, double[] areaUm2) {
        try (java.io.PrintWriter pw = new java.io.PrintWriter(out)) {
            pw.println("ganglion_id,neuron_count,area_um2");
            int n = Math.max(counts.length, areaUm2.length);
            for (int gid = 1; gid < n; gid++) {
                int c = (gid < counts.length) ? counts[gid] : 0;
                double a = (gid < areaUm2.length) ? areaUm2[gid] : 0.0;
                // skip empty ganglia if you want
                if (c == 0 && a == 0) continue;
                pw.printf(java.util.Locale.US, "%d,%d,%.6f%n", gid, c, a);
            }
        } catch (Exception e) {
            ij.IJ.handleException(e);
        }
    }
}
