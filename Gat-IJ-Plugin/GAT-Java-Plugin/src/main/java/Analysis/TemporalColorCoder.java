package Analysis;

import Features.Core.Params;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.plugin.LutLoader;
import ij.plugin.ZProjector;

import java.awt.image.IndexColorModel;

/**
 * TemporalColorCoder
 * Converts a grayscale time-lapse stack to an RGB stack with temporal color coding.
 * Supports single-channel 8-bit or 16-bit stacks.
 */
public class TemporalColorCoder {

    /**
     * Run the temporal color coding workflow.
     * @param imp Input ImagePlus stack (single-channel)
     * @param p Params object containing UI selections:
     *          - referenceFrame / referenceFrameEnd (frame range)
     *          - lutName (LUT string, e.g., "Fire")
     *          - projectionMethod (Z projection method)
     *          - createColorScale (boolean)
     *          - batchMode (boolean)
     * @return RGB ImagePlus stack after temporal color coding
     * @throws Exception if input is invalid
     */
    public static ImagePlus run(ImagePlus imp, Params p) throws Exception {
        if (imp == null) throw new IllegalArgumentException("Input stack cannot be null.");
        if (imp.getNChannels() > 1) throw new IllegalArgumentException("Multi-channel stacks not supported.");

        int width = imp.getWidth();
        int height = imp.getHeight();
        int slices = imp.getNSlices();
        int frames = imp.getNFrames();

        // Swap slices/frames if macro logic requires
        if (slices > 1 && frames == 1) {
            int tmp = slices;
            slices = frames;
            frames = tmp;
            imp.setDimensions(1, slices, frames);
        }

        // Frame range
        int startFrame = Math.max(1, p.referenceFrame);
        int endFrame = Math.min(frames, p.referenceFrameEnd > 0 ? p.referenceFrameEnd : frames);
        int totalFrames = endFrame - startFrame + 1;

        ImageStack rgbStack = new ImageStack(width, height);

        // Load LUT
        LUT lut;
        try {
            lut = LutLoader.openLut(IJ.getDirectory("luts") + p.lutName + ".lut");
        } catch (Exception e) {
            IJ.log("Failed to load LUT " + p.lutName + ", using Fire LUT as default.");
            lut = LutLoader.openLut(IJ.getDirectory("luts") + "Fire.lut");
        }

        // Extract LUT RGB arrays
        IndexColorModel icm = (IndexColorModel) lut.getColorModel();
        int mapSize = icm.getMapSize();
        byte[] rBytes = new byte[mapSize];
        byte[] gBytes = new byte[mapSize];
        byte[] bBytes = new byte[mapSize];
        icm.getReds(rBytes);
        icm.getGreens(gBytes);
        icm.getBlues(bBytes);

        int[] rLUT = new int[mapSize];
        int[] gLUT = new int[mapSize];
        int[] bLUT = new int[mapSize];
        for (int i = 0; i < mapSize; i++) {
            rLUT[i] = rBytes[i] & 0xFF;
            gLUT[i] = gBytes[i] & 0xFF;
            bLUT[i] = bBytes[i] & 0xFF;
        }

        // Main loop: frames then slices
        for (int t = startFrame; t <= endFrame; t++) {
            for (int z = 1; z <= slices; z++) {
                imp.setPosition(1, z, t);
                ImageProcessor ip = imp.getProcessor();

                ColorProcessor cp = new ColorProcessor(width, height);
                int colorIndex = (int) Math.floor((mapSize / (double) totalFrames) * (t - startFrame));

                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int gray = ip.getPixel(x, y);
                        double factor = gray / 255.0;
                        int r = (int) Math.round(rLUT[colorIndex] * factor);
                        int g = (int) Math.round(gLUT[colorIndex] * factor);
                        int b = (int) Math.round(bLUT[colorIndex] * factor);
                        cp.putPixel(x, y, (r << 16) | (g << 8) | b);
                    }
                }

                rgbStack.addSlice("Z" + z + "-T" + t, cp);
            }
        }

        ImagePlus rgbImp = new ImagePlus("TemporalColor_" + imp.getTitle(), rgbStack);

        // Apply Z-projection if requested
        if (p.projectionMethod != null && !p.projectionMethod.isEmpty()) {
            ZProjector zp = new ZProjector(rgbImp);
            switch (p.projectionMethod) {
                case "Max Intensity": zp.setMethod(ZProjector.MAX_METHOD); break;
                case "Average Intensity": zp.setMethod(ZProjector.AVG_METHOD); break;
                case "Min Intensity": zp.setMethod(ZProjector.MIN_METHOD); break;
                default: zp.setMethod(ZProjector.MAX_METHOD); break;
            }
            zp.doProjection();
            rgbImp = zp.getProjection();
            rgbImp.setTitle("TemporalColor_" + imp.getTitle() + "_Proj");
        }

        // Optional color scale
        if (p.createColorScale) {
            ImagePlus scale = createColorScale(rLUT, gLUT, bLUT, startFrame, endFrame);
            scale.show();
        }

        if (!p.batchMode) rgbImp.show();
        return rgbImp;
    }

    /**
     * Creates a small RGB color scale bar
     */
    private static ImagePlus createColorScale(int[] rLUT, int[] gLUT, int[] bLUT, int startFrame, int endFrame) {
        int width = 256;
        int height = 32;
        ImagePlus scale = IJ.createImage("TimeColorScale", "RGB", width, height, 1);
        ImageProcessor ip = scale.getProcessor();
        int totalFrames = endFrame - startFrame + 1;
        int mapSize = rLUT.length;

        for (int x = 0; x < width; x++) {
            int frameIndex = (int) Math.floor((mapSize / (double) totalFrames) * x);
            int r = rLUT[frameIndex];
            int g = gLUT[frameIndex];
            int b = bLUT[frameIndex];
            int rgb = (r << 16) | (g << 8) | b;
            for (int y = 0; y < height; y++) ip.set(x, y, rgb);
        }

        return scale;
    }
}
