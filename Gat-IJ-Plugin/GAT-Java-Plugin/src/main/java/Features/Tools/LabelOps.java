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




}
