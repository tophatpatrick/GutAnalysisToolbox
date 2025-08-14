package Features.Tools;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.plugin.ZProjector;
import ij.plugin.HyperStackConverter;

import static Features.Core.PluginCalls.findNewImageSince;

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
        if (imp.getNChannels()==1) return imp.duplicate();
        int ch = Math.max(1, Math.min(c1, imp.getNChannels()));
        ImagePlus dup = new ij.plugin.Duplicator().run(imp, ch, ch, 1, imp.getNSlices(), 1, imp.getNFrames());
        if (dup.getNChannels() > 1) {
            dup = ij.plugin.HyperStackConverter.toHyperStack(dup, 1, dup.getNSlices(), dup.getNFrames(), "default", "Color");
        }
        dup.setCalibration(imp.getCalibration());
        return dup;
    }

    /** Resize to W×H with interpolation=None (faithful to macro), and update calibration accordingly. */
    public static ImagePlus resizeTo(ImagePlus src, int newW, int newH) {
        int[] before = ij.WindowManager.getIDList();
        SilentRun.on(src, "Scale...", "x=- y=- width="+newW+" height="+newH+" interpolation=None create");
        ImagePlus out = findNewImageSince(before); if (out == null) out = IJ.getImage();
        ij.measure.Calibration cal = src.getCalibration().copy();
        cal.pixelWidth  = cal.pixelWidth  * src.getWidth()  / (double) out.getWidth();
        cal.pixelHeight = cal.pixelHeight * src.getHeight() / (double) out.getHeight();
        out.setCalibration(cal);
        out.hide();
        return out;
    }

    /** Resize to width/height (Bilinear for intensity images), update calibration. */
    public static ImagePlus resizeToIntensity(ImagePlus src, int newW, int newH) {
        int[] before = ij.WindowManager.getIDList();
        SilentRun.on(src, "Scale...", "x=- y=- width="+newW+" height="+newH+" interpolation=Bilinear create");
        ImagePlus out = findNewImageSince(before); if (out == null) out = IJ.getImage();

        //hide our output
        out.hide();

        if (out.getType() == ImagePlus.COLOR_RGB) IJ.run(out, "8-bit", "");
        IJ.run(out, "Grays", "");
        out.setDimensions(1,1,1);
        out.setOpenAsHyperStack(false);

        ij.measure.Calibration cal = src.getCalibration().copy();
        cal.pixelWidth  = cal.pixelWidth  * src.getWidth()  / (double) out.getWidth();
        cal.pixelHeight = cal.pixelHeight * src.getHeight() / (double) out.getHeight();
        out.setCalibration(cal);
        out.hide();
        return out;
    }


}
