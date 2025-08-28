package Features.Tools;

import Features.Core.PluginCalls;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

public final class LabelOps {
    private LabelOps(){}

    /**
     * For every Hu label ID in huLabels, compute fraction of its pixels that
     * overlap ANY >0 pixel in markerLabels. Return boolean[ maxHuId+1 ],
     * where keep[huId] = true if fraction >= fracThresh.
     */
    public static boolean[] neuronsPositiveByOverlap(ImagePlus huLabels, ImagePlus markerLabels, double fracThresh) {
        ImageProcessor hu = huLabels.getProcessor();
        ImageProcessor mk = markerLabels.getProcessor();
        int w = hu.getWidth(), h = hu.getHeight();

        // find max Hu ID
        int maxId = 0;
        for (int y=0; y<h; y++) {
            for (int x=0; x<w; x++) {
                int id = hu.get(x,y) & 0xFFFF;
                if (id > maxId) maxId = id;
            }
        }
        long[] total = new long[maxId + 1];
        long[] hits  = new long[maxId + 1];

        for (int y=0; y<h; y++) {
            for (int x=0; x<w; x++) {
                int id = hu.get(x,y) & 0xFFFF;
                if (id == 0) continue;
                total[id]++;
                if ((mk.get(x,y) & 0xFFFF) > 0) hits[id]++;
            }
        }

        boolean[] keep = new boolean[maxId + 1];
        for (int id=1; id<=maxId; id++) {
            if (total[id] == 0) { keep[id] = false; continue; }
            double frac = (double)hits[id] / (double)total[id];
            keep[id] = frac >= fracThresh;
        }
        return keep;
    }



    /**
     * Keep only Hu labels whose keep[id] is true. Returns a NEW 16-bit label map
     * by converting the kept pixels to binary and re-labeling to 1..K (contiguous).
     */
    public static ImagePlus keepHuLabels(ImagePlus huLabels, boolean[] keep) {
        int w = huLabels.getWidth(), h = huLabels.getHeight();
        byte[] bin = new byte[w*h];

        int i=0;
        for (int y=0; y<h; y++) {
            for (int x=0; x<w; x++, i++) {
                int id = huLabels.getProcessor().get(x,y) & 0xFFFF;
                bin[i] = (byte)((id>0 && id < keep.length && keep[id]) ? 255 : 0);
            }
        }
        ImagePlus binary = new ImagePlus("keep_bin", new ij.process.ByteProcessor(w,h,bin,null));
        ImagePlus relabeled = PluginCalls.binaryToLabels(binary);
        // adopt calibration
        relabeled.setCalibration(huLabels.getCalibration());
        binary.close();
        return relabeled;
    }

    /** Directional keep: keep labels from ref whose area-overlap with test>=minFrac. */
    public static ImagePlus filterLabelsByOverlap(ImagePlus refLabels, ImagePlus testLabelsOrBinary, double minFrac) {
        int w = refLabels.getWidth(), h = refLabels.getHeight();
        short[] ref = (short[]) refLabels.getProcessor().getPixels();
        short[] tst = (short[]) testLabelsOrBinary.getProcessor().getPixels();

        // count areas
        int maxRef = 0;
        for (short v : ref) { int u = v & 0xFFFF; if (u > maxRef) maxRef = u; }
        long[] area = new long[maxRef + 1];
        long[] overlap = new long[maxRef + 1];

        for (int i = 0, n = w*h; i < n; i++) {
            int r = ref[i] & 0xFFFF;
            if (r == 0) continue;
            area[r]++;
            if ((tst[i] & 0xFFFF) != 0) overlap[r]++;
        }

        // build keep map
        boolean[] keep = new boolean[maxRef + 1];
        for (int id = 1; id <= maxRef; id++) {
            if (area[id] == 0) continue;
            keep[id] = (overlap[id] >= Math.ceil(minFrac * area[id]));
        }

        // paint kept labels contiguously 1..K
        ShortProcessor out = new ShortProcessor(w, h);
        int next = 1;
        int[] map = new int[maxRef + 1]; // old->new id
        for (int i = 0, n = w*h; i < n; i++) {
            int r = ref[i] & 0xFFFF;
            if (r == 0 || !keep[r]) continue;
            int tgt = map[r];
            if (tgt == 0) { tgt = map[r] = next++; }
            out.set(i % w, i / w, tgt);
        }
        ImagePlus outIp = new ImagePlus("filtered_labels", out);
        outIp.setCalibration(refLabels.getCalibration());
        return outIp;
    }

    public static ImagePlus andLabels(ImagePlus a, ImagePlus b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight())
            throw new IllegalArgumentException("Label maps must have the same size.");

        int w = a.getWidth(), h = a.getHeight();
        short[] ap = (short[]) a.getProcessor().getPixels();
        short[] bp = (short[]) b.getProcessor().getPixels();

        byte[] bin = new byte[w * h];
        for (int i = 0, n = w * h; i < n; i++) {
            int av = ap[i] & 0xFFFF;
            int bv = bp[i] & 0xFFFF;
            bin[i] = (byte) ((av != 0 && bv != 0) ? 255 : 0);
        }

        ImagePlus binary = new ImagePlus("and_bin", new ij.process.ByteProcessor(w, h, bin, null));
        binary.setCalibration(a.getCalibration());

        ImagePlus lab = PluginCalls.binaryToLabels(binary);
        lab.setCalibration(a.getCalibration());

        binary.close();
        return lab;
    }



}
