package Features.Tools;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.plugin.ZProjector;
import ij.plugin.HyperStackConverter;

public final class ImageOps {
    private ImageOps() {}

    /** Max Intensity Projection for Z-stacks. */
    public static ImagePlus mip(ImagePlus src) {
        ZProjector zp = new ZProjector(src);
        zp.setMethod(ZProjector.MAX_METHOD);
        zp.setStartSlice(1);
        zp.setStopSlice(src.getNSlices());
        zp.doProjection();
        ImagePlus out = zp.getProjection();
        out.setCalibration(src.getCalibration());
        out.setTitle("MAX_" + src.getTitle());
        return out;
    }

    /** Extract 1-based channel to single-channel image. */
    public static ImagePlus extractChannel(ImagePlus imp, int c1) {
        if (imp.getNChannels() == 1) return imp.duplicate();
        IJ.run(imp, "Duplicate...", "title=ch" + c1 + " duplicate channels=" + c1);
        ImagePlus dup = IJ.getImage();
        if (dup.getNChannels() > 1) {
            dup = HyperStackConverter.toHyperStack(dup, 1, dup.getNSlices(), dup.getNFrames(), "default", "Color");
        }
        dup.setCalibration(imp.getCalibration());
        return dup;
    }

    /** Resize by factor (no interpolation), update calibration accordingly. */
    public static ImagePlus resizeBy(ImagePlus src, double factor) {
        int newW = (int) Math.round(src.getWidth() * factor);
        int newH = (int) Math.round(src.getHeight() * factor);
        return resizeTo(src, newW, newH);
    }

    /** Resize to width/height (no interpolation), update calibration. */
    public static ImagePlus resizeTo(ImagePlus src, int newW, int newH) {
        IJ.run(src, "Scale...", "x=- y=- width=" + newW + " height=" + newH + " interpolation=None create");
        ImagePlus out = IJ.getImage();
        Calibration cal = src.getCalibration().copy();
        cal.pixelWidth  = cal.pixelWidth  * src.getWidth()  / out.getWidth();
        cal.pixelHeight = cal.pixelHeight * src.getHeight() / out.getHeight();
        out.setCalibration(cal);
        return out;
    }
}
