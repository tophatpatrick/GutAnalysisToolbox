package UI.panes.Tools;

import ij.*;
import ij.gui.*;
import ij.plugin.*;
import ij.process.*;
import java.awt.*;
import java.io.File;

/**
 * Temporal-Color Coder: A Fiji/ImageJ plugin to color-code the temporal changes
 * of a time-lapse stack into a single RGB image.
 *
 * This is a modified version of the original Temporal Color Code plugin.
 * Credits to Kota Miura and other contributors.
 *
 * @author Kota Miura (Original macro), Modified by Pradeep R. (Macro), Converted to Java by Gemini
 * @version 1.0
 * @since September 2025
 */

/**
public class TemporalColorCoder implements PlugIn {

    // --- Global User Options/Parameters ---
    private String lutName = "Fire";
    private int startFrame = 1;
    private int endFrame = 0; // Will be set to total frames
    private String projectionMethod = "Max Intensity";
    private boolean createColorScale = true;
    private boolean batchMode = false;

    // --- Image Metadata ---
    private ImagePlus originalStack;
    private int sizeX, sizeY, channels, slices, frames;

    // --- Constants for LUTs and Projections ---
    private static final String[] PROJECTION_METHODS = new String[]{
        "Average Intensity", "Max Intensity", "Min Intensity", "Sum Slices", "Standard Deviation", "Median"
    };


    @Override
    public void run(String arg) {
        originalStack = IJ.getImage();
        if (originalStack == null) {
            IJ.error("Temporal-Color Coder", "Please open a time-lapse stack (8-bit or 16-bit) first.");
            return;
        }

        // Step 1: Validate and set up dimensions
        if (!validateAndSetupDimensions()) return;

        // Step 2: Show options dialog
        if (!showDialog()) return;

        // Step 3: Run the core processing logic
        processStack();

        IJ.log("Temporal Color Coding Complete.");
    }

    /**
     * Handles dimension checking and swapping.
     */
/**
    private boolean validateAndSetupDimensions() {
        channels = originalStack.getNChannels();
        slices = originalStack.getNSlices();
        frames = originalStack.getNFrames();
        sizeX = originalStack.getWidth();
        sizeY = originalStack.getHeight();

        if (channels > 1) {
            IJ.error("Temporal-Color Coder", "Cannot color-code multi-channel images!");
            return false;
        }

        // Swap slices and frames if frames == 1 and slices > 1 (Assuming slices are time points)
        if (slices > 1 && frames == 1) {
            frames = slices;
            slices = 1;
            originalStack.setDimensions(1, slices, frames);
            IJ.log("Slices and frames swapped.");
        }
        
        // Set default end frame to total frames
        endFrame = frames;

        return true;
    }

    /**
     * Mimics the macro's showDialog() using GenericDialog.
     */
/**
    private boolean showDialog() {
        String[] lutList = LutLoader.getLutList();
        
        GenericDialog gd = new GenericDialog("Temporal Color Code Settings");
        gd.addChoice("LUT", lutList, lutName);
        gd.addNumericField("Start frame:", startFrame, 0);
        gd.addNumericField("End frame:", endFrame, 0);
        gd.addChoice("Projection Method", PROJECTION_METHODS, projectionMethod);
        gd.addCheckbox("Create Time Color Scale Bar", createColorScale);
        gd.addCheckbox("Batch mode? (no image output)", batchMode);
        
        gd.showDialog();

        if (gd.wasCanceled()) return false;

        lutName = gd.getNextChoice();
        startFrame = (int) gd.getNextNumber();
        endFrame = (int) gd.getNextNumber();
        projectionMethod = gd.getNextChoice();
        createColorScale = gd.getNextBoolean();
        batchMode = gd.getNextBoolean();

        // Validate frame range
        if (startFrame < 1) startFrame = 1;
        if (endFrame > frames) endFrame = frames;
        if (startFrame > endFrame) {
             IJ.error("Temporal-Color Coder", "Start frame must be less than or equal to end frame.");
             return false;
        }
        
        return true;
    }

    /**
     * Core logic for color coding and projection.
     */
/**
    private void processStack() {
        int totalFrames = endFrame - startFrame + 1;
        int calcSlices = slices * totalFrames;
        
        IJ.log("Processing frames " + startFrame + " to " + endFrame + " (Total frames: " + totalFrames + ")");

        // 0. Set batch mode if requested
        if (batchMode) IJ.setBatchMode(true);
        
        // 1. Prepare the LUT color data
        byte[][] lut = getLutColorData(lutName);
        byte[] rA = lut[0];
        byte[] gA = lut[1];
        byte[] bA = lut[2];

        // 2. Create the destination RGB stack
        ImagePlus newStack = NewImage.createRGBImage("colored", sizeX, sizeY, calcSlices, NewImage.FILL_BLACK);

        newStack.setDimensions(1, slices, totalFrames);
        
        // 3. Create a temporary 8-bit duplicate of the original stack (as required by the macro logic)
        ImagePlus tempOriginal = originalStack.duplicate();
        IJ.run(tempOriginal, "8-bit", ""); // Ensures 8-bit processing for correct LUT indexing

        // 4. Create temporary processor for colorizing a single slice
        ImageProcessor tempProcessor = new ByteProcessor(sizeX, sizeY);
        ImagePlus tempImage = new ImagePlus("temp", tempProcessor);

        // --- Frame-by-Frame Processing Loop ---
        for (int i = 0; i < totalFrames; i++) { // i is the index in the new stack (0 to totalFrames - 1)
            double colorscale = Math.floor((256.0 / totalFrames) * i);
            int frameIndex = i + startFrame; // Original frame number (1-based)
            
            // Generate the temporary, intensity-modulated LUT for this frame
            byte[] nrA = new byte[256];
            byte[] ngA = new byte[256];
            byte[] nbA = new byte[256];

            for (int j = 0; j < 256; j++) {
                double intensityFactor = j / 255.0;
                
                // Get the color component from the original LUT at the time-based index
                int baseR = rA[(int) colorscale] & 0xFF;
                int baseG = gA[(int) colorscale] & 0xFF;
                int baseB = bA[(int) colorscale] & 0xFF;

                // Scale color by original image intensity (j)
                nrA[j] = (byte) Math.round(baseR * intensityFactor);
                ngA[j] = (byte) Math.round(baseG * intensityFactor);
                nbA[j] = (byte) Math.round(baseB * intensityFactor);
            }

            for (int j = 0; j < slices; j++) { // j is the slice/Z index (0-based)
                int sliceIndex = j + 1; // 1-based slice index
                int newStackSlice = newStack.getStackIndex(1, sliceIndex, i + 1); // 1-based index in the new hyperstack
                
                // a. Get the original 8-bit slice
                ImageProcessor originalIp = tempOriginal.getStack().getProcessor(tempOriginal.getStackIndex(1, sliceIndex, frameIndex));
                
                // b. Apply the temporary LUT to the 8-bit slice
                tempProcessor.setPixels(originalIp.getPixels());
                IndexColorModel icm = new IndexColorModel(8, 256, nrA, ngA, nbA);
                tempProcessor.setColorModel(icm);
                
                // c. Convert the now-colored 8-bit processor to 24-bit RGB
                ImageProcessor rgbProcessor = tempProcessor.convertToRGB();

                // d. Paste the colored slice into the new RGB stack
                newStack.getStack().setProcessor(rgbProcessor, newStackSlice);
            }
        }
        
        // 5. Cleanup temporary images
        tempOriginal.close();
        tempImage.close();

        // 6. Project the RGB stack
        ImagePlus projectedImage = projectStack(newStack);
        projectedImage.setTitle(originalStack.getTitle() + "_" + projectionMethod + "_ColorTime");
        projectedImage.show();
        
        // 7. Cleanup the temporary RGB stack
        newStack.close();
        
        // 8. Create Scale Bar if requested
        if (createColorScale) {
            createScale(lutName, startFrame, endFrame);
        }
        
        // 9. Exit batch mode if applicable
        if (batchMode) IJ.setBatchMode(false);
    }
    
    /**
     * Projects the final RGB stack based on the user's choice.
     */
/**
    private ImagePlus projectStack(ImagePlus hyperStack) {
        // The macro does two dimension rearrangements and then Z Projects.
        // We can simplify this by forcing Z-projection on the TIME (T) dimension.
        
        // 1. Rearrange to ZT ordering (slices=T, frames=Z) as required for Z Project
        hyperStack.setDimensions(1, totalFrames, slices);
        
        // 2. Run Z Projection
        ZProjector zp = new ZProjector(hyperStack);
        zp.setMethod(getZProjectorMethod(projectionMethod));
        zp.doProjection();
        
        return zp.getProjection();
    }
    
    /**
     * Helper to get the corresponding ZProjector constant.
     */
/**
    private int getZProjectorMethod(String method) {
        return switch (method) {
            case "Average Intensity" -> ZProjector.AVERAGE_INTENSITY;
            case "Min Intensity" -> ZProjector.MIN_INTENSITY;
            case "Sum Slices" -> ZProjector.SUM_SLICES;
            case "Standard Deviation" -> ZProjector.SD_INTENSITY;
            case "Median" -> ZProjector.MEDIAN_INTENSITY;
            case "Max Intensity", default -> ZProjector.MAX_INTENSITY;
        };
    }
    
    /**
     * Helper to load the color component arrays (R, G, B) from the chosen LUT.
     */
/**
    private byte[][] getLutColorData(String lutstr) {
        // Create a temporary 8-bit image to load the LUT
        ImagePlus temp = NewImage.createByteImage("lut_temp", 1, 1, 1, NewImage.FILL_WHITE);
        IJ.run(temp, lutstr, "");
        
        IndexColorModel icm = (IndexColorModel) temp.getProcessor().getColorModel();
        
        byte[] r = new byte[256];
        byte[] g = new byte[256];
        byte[] b = new byte[256];
        
        icm.getReds(r);
        icm.getGreens(g);
        icm.getBlues(b);
        
        temp.close();
        return new byte[][]{r, g, b};
    }

    /**
     * Creates the color scale bar.
     */
/**
    private void createScale(String lutstr, int beginf, int endf) {
        int ww = 256;
        int hh = 32;
        
        // Create 8-bit image with ramped intensity (0-255)
        ImageProcessor scaleIp = new ByteProcessor(ww, hh);
        byte[] pixels = (byte[]) scaleIp.getPixels();
        for (int j = 0; j < hh; j++) {
            for (int i = 0; i < ww; i++) {
                pixels[j * ww + i] = (byte) i;
            }
        }
        ImagePlus scaleBar = new ImagePlus("color time scale", scaleIp);

        // Apply LUT and convert to RGB
        IJ.run(scaleBar, lutstr, "");
        IJ.run(scaleBar, "RGB Color", "");

        // Add text labels
        scaleBar.getProcessor().setAntialiasedText(true);
        scaleBar.getProcessor().setFont(new Font("SansSerif", Font.PLAIN, 12));
        scaleBar.getProcessor().setColor(Color.WHITE);

        // Enlarge Canvas to fit text
        int newHeight = hh + 16;
        IJ.run(scaleBar, "Canvas Size...", "width=" + ww + " height=" + newHeight + " position=Top-Center zero");
        
        ImageProcessor ip = scaleBar.getProcessor();
        
        // Draw frame text
        ip.drawString("frame", (ww / 2) - 12, hh + 12);
        
        // Draw start frame
        String startStr = String.format("%03d", beginf);
        ip.drawString(startStr, 0, hh + 12);
        
        // Draw end frame
        String endStr = String.format("%03d", endf);
        ip.drawString(endStr, ww - ip.getStringWidth(endStr) - 1, hh + 12);
        
        scaleBar.show();
    }

    // --- Alternative Main for Standalone Testing ---
    public static void main(String[] args) {
        if (IJ.getInstance() == null) {
            new ImageJ();
        }
        // For testing, open a sample stack before running:
        // IJ.openImage("path/to/your/timeseries.tif").show();
        // new TemporalColorCoder().run("");
    }
}
**/