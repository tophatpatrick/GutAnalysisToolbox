package Analysis;

import Features.Core.Params;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.plugin.ZProjector;

/**
 * TemporalColorCoder
 * Converts a grayscale time-lapse stack to an RGB stack with temporal color coding.
 */
public class TemporalColorCoder {

    public static class TemporalColorOutput {
        public final ImagePlus rgbStack;
        public final ImagePlus colorScale;

        public TemporalColorOutput(ImagePlus rgbStack, ImagePlus colorScale) {
            this.rgbStack = rgbStack;
            this.colorScale = colorScale;
        }
    }

    /**
     * Run the temporal color coding workflow.
     * @param imp Input ImagePlus stack (single-channel)
     * @param p Params object containing UI selections
     * @return TemporalColorOutput containing RGB stack and optional color scale
     * @throws Exception if input is invalid
     */
    public static TemporalColorOutput run(ImagePlus imp, Params p) throws Exception {
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

        // Generate RGB LUT arrays
        int[][] rgbLUT = generateRGBLUT(p.lutName);
        int[] rLUT = rgbLUT[0];
        int[] gLUT = rgbLUT[1];
        int[] bLUT = rgbLUT[2];
        int lutSize = rLUT.length;

        // Main loop: frames then slices
        for (int t = startFrame; t <= endFrame; t++) {
            for (int z = 1; z <= slices; z++) {
                imp.setPosition(1, z, t);
                ImageProcessor ip = imp.getProcessor();

                ColorProcessor cp = new ColorProcessor(width, height);
                int colorIndex = (int) Math.floor((lutSize / (double) totalFrames) * (t - startFrame));
                colorIndex = Math.min(colorIndex, lutSize - 1);

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
        ImagePlus scaleImp = null;
        if (p.createColorScale) {
            scaleImp = createColorScale(rLUT, gLUT, bLUT, startFrame, endFrame);
        }

        // Show output if not batch mode
        if (!p.batchMode) rgbImp.show();
        if (!p.batchMode && scaleImp != null) scaleImp.show();

        return new TemporalColorOutput(rgbImp, scaleImp);
    }

    /**
     * Generates RGB LUT arrays from a LUT name
     */
    private static int[][] generateRGBLUT(String lutName) {
        int size = 256;
        int[] r = new int[size];
        int[] g = new int[size];
        int[] b = new int[size];

        for (int i = 0; i < size; i++) {
            float t = i / (float)(size - 1);
            switch (lutName != null ? lutName : "Fire") {
                case "Fire":
                    r[i] = Math.min(255, (int)(255 * t));
                    g[i] = Math.min(255, (int)(255 * t * 0.5));
                    b[i] = 0;
                    break;
                case "Ice":
                    r[i] = 0;
                    g[i] = Math.min(255, (int)(255 * t * 0.5));
                    b[i] = Math.min(255, (int)(255 * t));
                    break;
                case "Green":
                    r[i] = 0;
                    g[i] = Math.min(255, (int)(255 * t));
                    b[i] = 0;
                    break;
                case "Red":
                    r[i] = Math.min(255, (int)(255 * t));
                    g[i] = 0;
                    b[i] = 0;
                    break;
                default:
                    r[i] = Math.min(255, (int)(255 * t));
                    g[i] = Math.min(255, (int)(255 * t * 0.5));
                    b[i] = 0;
            }
        }

        return new int[][] { r, g, b };
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
        int lutSize = rLUT.length;

        for (int x = 0; x < width; x++) {
            int frameIndex = (int) Math.floor((lutSize / (double) totalFrames) * x);
            frameIndex = Math.min(frameIndex, lutSize - 1);
            int r = rLUT[frameIndex];
            int g = gLUT[frameIndex];
            int b = bLUT[frameIndex];
            int rgb = (r << 16) | (g << 8) | b;
            for (int y = 0; y < height; y++) ip.set(x, y, rgb);
        }

        return scale;
    }
}
