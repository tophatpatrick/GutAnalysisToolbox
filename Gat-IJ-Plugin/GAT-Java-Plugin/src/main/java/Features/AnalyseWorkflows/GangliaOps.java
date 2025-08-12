package Features.AnalyseWorkflows;

import Features.Core.Params;
import Features.Core.PluginCalls;
import ij.IJ;
import ij.ImagePlus;
import ij.plugin.frame.RoiManager;


public final class GangliaOps {
    private GangliaOps(){}

    /** Main entry: produce a ganglia LABEL image from the chosen method. */
    public static ImagePlus segment(Params p, ImagePlus maxProjection, ImagePlus neuronLabels) {
        switch (p.gangliaMode) {
            case DEFINE_FROM_HU:
                return defineFromHu(p, neuronLabels, maxProjection);
            case IMPORT_ROI:
                return importRoiToLabels(p, maxProjection);
            case MANUAL:
                return manualDrawToLabels(maxProjection);
            case DEEPIMAGEJ:
            default:
                return deepImageJ(p, maxProjection);
        }
    }

    /** Count neurons per ganglion via neuron-label centroids sampled in ganglia label map. */
    public static Result countPerGanglion(ImagePlus neuronLabels, ImagePlus gangliaLabels) {
        final int w = neuronLabels.getWidth(), h = neuronLabels.getHeight();
        final short[] nl = (short[]) neuronLabels.getProcessor().convertToShort(false).getPixels();
        final short[] gl = (short[]) gangliaLabels.getProcessor().convertToShort(false).getPixels();

        int maxN = 0, maxG = 0;
        for (short v : nl) if ((v & 0xffff) > maxN) maxN = (v & 0xffff);
        for (short v : gl) if ((v & 0xffff) > maxG) maxG = (v & 0xffff);

        if (maxN == 0 || maxG == 0) return new Result(new int[0], new double[0], 0);

        double[] sx = new double[maxN + 1], sy = new double[maxN + 1];
        int[] cnt = new int[maxN + 1];

        int idx = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++, idx++) {
                int id = nl[idx] & 0xffff;
                if (id > 0) { sx[id] += x; sy[id] += y; cnt[id]++; }
            }

        int[] perGanglion = new int[maxG + 1];
        for (int id = 1; id <= maxN; id++) {
            if (cnt[id] == 0) continue;
            int cx = (int)Math.round(sx[id] / cnt[id]);
            int cy = (int)Math.round(sy[id] / cnt[id]);
            if (cx < 0) cx = 0; if (cy < 0) cy = 0;
            if (cx >= w) cx = w - 1; if (cy >= h) cy = h - 1;
            int gid = gl[cy * w + cx] & 0xffff;
            if (gid > 0) perGanglion[gid]++;
        }

        // area in µm²
        int[] areaPx = new int[maxG + 1];
        for (short v : gl) {
            int gid = v & 0xffff;
            if (gid > 0) areaPx[gid]++;
        }
        double pxUm = gangliaLabels.getCalibration().pixelWidth > 0 ? gangliaLabels.getCalibration().pixelWidth : 1.0;
        double s = pxUm * pxUm;
        double[] areaUm2 = new double[maxG + 1];
        for (int g = 1; g <= maxG; g++) areaUm2[g] = areaPx[g] * s;

        return new Result(perGanglion, areaUm2, maxG);
    }

    // ---------- methods (reuse PluginCalls everywhere possible) ----------

    private static ImagePlus deepImageJ(Params p, ImagePlus maxProjection) {
        ImagePlus bin = PluginCalls.runDeepImageJForGanglia(
                maxProjection, p.gangliaChannel, p.huChannel, p.gangliaModelFolder, 200.0, p);

        // Do NOT remove border labels (macro doesn’t)
        // Do NOT fill holes (macro doesn’t)

        ImagePlus labels = PluginCalls.binaryToLabels(bin);
        labels.setCalibration(maxProjection.getCalibration());
        if (labels != bin) bin.close();
        return labels;
    }

    private static ImagePlus defineFromHu(Params p, ImagePlus neuronLabels, ImagePlus ref) {
        // labels -> binary (inline)
        ImagePlus bin = neuronLabels.duplicate();
        bin.show();
        IJ.run(bin, "Select None", "");
        IJ.setThreshold(bin, 1, 65535);
        IJ.run(bin, "Convert to Mask", "");

        double pxUm = (ref.getCalibration() != null) ? ref.getCalibration().pixelWidth : 0.0;
        if (pxUm <= 0) throw new IllegalStateException("Image must be calibrated in microns.");
        int iters = Math.max(0, (int) Math.round(p.huDilationMicron / pxUm));  // allow 0

        for (int i = 0; i < iters; i++) IJ.run(bin, "Dilate", "");

        ImagePlus labels = PluginCalls.binaryToLabels(bin);
        labels.setCalibration(ref.getCalibration());
        if (labels != bin) { bin.changes = false; bin.close(); }
        return labels;
    }

    private static ImagePlus importRoiToLabels(Params p, ImagePlus ref) {
        if (p.customGangliaRoiZip == null || p.customGangliaRoiZip.isEmpty())
            throw new IllegalArgumentException("Custom ROI zip path is empty.");
        RoiManager rm = RoiManager.getInstance2();
        rm.reset();
        rm.runCommand("Open", p.customGangliaRoiZip);
        ImagePlus bin = PluginCalls.roisToBinary(ref, rm);
        rm.reset();
        ImagePlus lab = PluginCalls.binaryToLabels(bin);
        lab.setCalibration(ref.getCalibration());
        if (lab != bin) bin.close();
        return lab;
    }

    private static ImagePlus manualDrawToLabels(ImagePlus ref) {
        ref.show();
        RoiManager rm = RoiManager.getInstance2();
        rm.reset();
        IJ.setTool("freehand");
        IJ.showMessage("Ganglia outline",
                "Draw each ganglion with the Freehand tool and press 'T' to add to ROI Manager.\nClick OK when done.");
        ImagePlus bin = PluginCalls.roisToBinary(ref, rm);
        rm.reset();
        ImagePlus lab = PluginCalls.binaryToLabels(bin);
        lab.setCalibration(ref.getCalibration());
        if (lab != bin) bin.close();
        return lab;
    }


    // ---------- result POJO ----------
    public static final class Result {
        public final int[] countsPerGanglion;  // index = ganglion id
        public final double[] areaUm2;         // index = ganglion id
        public final int maxGanglionId;
        public Result(int[] c, double[] a, int maxId) {
            this.countsPerGanglion = c; this.areaUm2 = a; this.maxGanglionId = maxId;
        }
    }


    // Keep only ganglia that contain at least `minCount` neurons.
// Returns an 8-bit binary mask named exactly like the macro: "ganglia_binary".
    public static ImagePlus keepGangliaWithAtLeast(ImagePlus gangliaLabels, int[] countsPerGanglion, int minCount) {
        final int w = gangliaLabels.getWidth(), h = gangliaLabels.getHeight();
        final short[] gl = (short[]) gangliaLabels.getProcessor().convertToShort(false).getPixels();

        ImagePlus bin = ij.IJ.createImage("ganglia_binary", "8-bit black", w, h, 1);
        byte[] bp = (byte[]) bin.getProcessor().getPixels();

        int idx = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++, idx++) {
                int gid = gl[idx] & 0xffff;
                if (gid > 0 && gid < countsPerGanglion.length && countsPerGanglion[gid] >= minCount) {
                    bp[idx] = (byte) 255;
                }
            }
        }
        bin.setCalibration(gangliaLabels.getCalibration());
        return bin;
    }

}
