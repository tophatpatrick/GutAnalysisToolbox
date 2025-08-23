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


    public static void writeMultiCsv(
            File csv,
            String baseName,
            String cellType,
            int totalHu,
            Integer noOfGanglia,                       // null if ganglia not computed
            int[] huCountsPerGanglion,                 // may be null; 1-based (idx 1..G) like macro
            double[] gangliaAreaUm2,                   // may be null; 1-based
            java.util.LinkedHashMap<String,Integer> markerTotals,             // keep insertion order
            java.util.LinkedHashMap<String,int[]> markerCountsPerGanglion     // may be empty; 1-based arrays
    ) {
        // determine number of rows we need (max ganglion index present)
        int G = 0;
        G = Math.max(G, lastNonZeroIndex(huCountsPerGanglion));
        G = Math.max(G, lastNonZeroIndex(gangliaAreaUm2));
        if (markerCountsPerGanglion != null) {
            for (int[] arr : markerCountsPerGanglion.values()) {
                G = Math.max(G, lastNonZeroIndex(arr));
            }
        }
        if (G == 0) G = 1; // no ganglia → still emit a single summary row

        try (PrintWriter pw = new PrintWriter(new FileWriter(csv))) {
            // Header
            StringBuilder hdr = new StringBuilder();
            appendCsv(hdr, "File name");
            appendCsv(hdr, "Total " + cellType);
            if (noOfGanglia != null) {
                appendCsv(hdr, "No of ganglia");
                appendCsv(hdr, "Neuron counts per ganglia");
                appendCsv(hdr, "Area_per_ganglia_um2");
            }
            if (markerTotals != null) {
                for (String m : markerTotals.keySet()) {
                    appendCsv(hdr, m);
                }
            }
            if (noOfGanglia != null && markerCountsPerGanglion != null) {
                for (String m : markerCountsPerGanglion.keySet()) {
                    appendCsv(hdr, m + " counts per ganglia");
                }
            }
            pw.println(hdr.toString());

            // Rows (one per ganglion index; row 1 also carries totals & filename)
            java.util.Locale loc = java.util.Locale.US;
            for (int gi = 1; gi <= G; gi++) {
                StringBuilder row = new StringBuilder();

                // Summary only on first row
                appendCsv(row, gi == 1 ? baseName : "");
                appendCsv(row, gi == 1 ? Integer.toString(totalHu) : "");
                if (noOfGanglia != null) {
                    appendCsv(row, gi == 1 ? Integer.toString(noOfGanglia) : "");
                    appendCsv(row, valAt(huCountsPerGanglion, gi));
                    appendCsv(row, dblAt(loc, gangliaAreaUm2, gi));
                }

                // Marker totals only on first row
                if (markerTotals != null) {
                    for (Integer tot : markerTotals.values()) {
                        appendCsv(row, gi == 1 ? String.valueOf(tot) : "");
                    }
                }

                // Per-ganglion counts for each marker/combination
                if (noOfGanglia != null && markerCountsPerGanglion != null) {
                    for (int[] arr : markerCountsPerGanglion.values()) {
                        appendCsv(row, valAt(arr, gi));
                    }
                }

                pw.println(row.toString());
            }
        } catch (IOException e) {
            IJ.log("Failed writing multi CSV: " + e.getMessage());
        }
    }

    private static int lastNonZeroIndex(int[] a) {
        if (a == null) return 0;
        for (int i = a.length - 1; i >= 1; i--) {
            if (a[i] != 0) return i;
        }
        // if array exists but could be all zeros; still return (length-1) if length>1
        return Math.max(0, a.length - 1);
    }

    private static int lastNonZeroIndex(double[] a) {
        if (a == null) return 0;
        for (int i = a.length - 1; i >= 1; i--) {
            if (a[i] != 0.0) return i;
        }
        return Math.max(0, a.length - 1);
    }

    private static String valAt(int[] a, int idx) {
        if (a == null || idx >= a.length) return "";
        int v = a[idx];
        return (v == 0) ? "" : Integer.toString(v);
    }

    private static String dblAt(java.util.Locale loc, double[] a, int idx) {
        if (a == null || idx >= a.length) return "";
        double v = a[idx];
        if (v == 0.0) return "";
        return String.format(loc, "%.6f", v);
    }

    private static void appendCsv(StringBuilder sb, String cell) {
        if (sb.length() > 0) sb.append(',');
        sb.append(csvEscape(cell));
    }

    private static String csvEscape(String s) {
        if (s == null) return "";
        boolean needQuote = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        if (!needQuote) return s;
        // escape quotes by doubling them
        String esc = s.replace("\"", "\"\"");
        return "\"" + esc + "\"";
    }

}
