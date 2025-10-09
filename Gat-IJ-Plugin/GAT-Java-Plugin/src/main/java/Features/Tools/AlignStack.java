package Features.Tools;

import ij.*;
import ij.gui.*;
import ij.io.*;
import ij.plugin.*;

/**
 * Batch ImageJ Plugin to run Linear Stack Alignment with SIFT and/or Template matching on TIFF/LIF files in a folder
 * 
 * This plugin reads files in a TIFF or LIF file (acquired from Leica microscope), extracts timeseries files 
 * which have at least more than 10 frames, runs a combination of registration plugins on each of the files, 
 * and saves the aligned files with "_aligned" suffix.
 * 
 * Note: If more than one channel per TIFF file, it will only run alignment on the first channel.
 * Runs in batch mode so images won't be displayed during processing.
 * 
 * @author Pradeep Rajasekhar (Original macro), Converted to Java by Edward Griffith
 * @version 1.0
 * @since August 2025
 * 
 * License: BSD3
 * 
 * Copyright 2021 Pradeep Rajasekhar, INM Lab, Monash Institute of Pharmaceutical Sciences
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted 
 * provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions 
 *    and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions 
 *    and the following disclaimer in the documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse 
 *    or promote products derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED 
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A 
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR 
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT 
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, 
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN 
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

public class AlignStack implements PlugIn {

    private static final String PLUGIN_INSTALLATION_URL = 
        "https://sites.google.com/site/qingzongtseng/template-matching-ij-plugin#install";
    
    private ImagePlus selectedImage;
    private String imageName;
    private String inputDirectory;
    private int sizeX, sizeY, sizeC, slices, sizeT;
    
    // User options
    private boolean useSift = true;
    private boolean useTemplateMatching = true;
    private int referenceFrame = 1;
    private boolean useDefaultSettings = false;
    private int selectedChannel = 0;

    @Override
    public void run(String arg) {
        // Clear results window
        IJ.log("\\Clear");
        IJ.log("Please install template plugin if you get errors: " + PLUGIN_INSTALLATION_URL);
        
        try {
            // Step 1: Open file dialog
            if (!openImageFile()) {
                return;
            }
            
            // Step 2: Get image dimensions and validate
            if (!validateImageStack()) {
                return;
            }
            
            // Step 3: Show options dialog
            if (!showOptionsDialog()) {
                return;
            }
            
            // Step 4: Process the image
            processImage();
            
        } catch (Exception e) {
            IJ.error("Error during alignment", e.getMessage());
            e.printStackTrace();
        } finally {
            // Cleanup
            System.gc();
        }
        
        IJ.showMessage("Alignment Complete", "Processing finished successfully!");
    }
    
    private boolean openImageFile() {
        OpenDialog od = new OpenDialog("Choose the file to be aligned");
        String directory = od.getDirectory();
        String fileName = od.getFileName();
        
        if (fileName == null) {
            return false; // User cancelled
        }
        
        String filePath = directory + fileName;
        selectedImage = IJ.openImage(filePath);
        
        if (selectedImage == null) {
            IJ.error("Could not open image: " + filePath);
            return false;
        }
        
        imageName = selectedImage.getTitle();
        if (imageName.contains(".")) {
            imageName = imageName.substring(0, imageName.lastIndexOf('.'));
        }
        
        selectedImage.setTitle(imageName);
        inputDirectory = directory;
        selectedImage.show();
        
        return true;
    }
    
    private boolean validateImageStack() {
        sizeX = selectedImage.getWidth();
        sizeY = selectedImage.getHeight();
        sizeC = selectedImage.getNChannels();
        slices = selectedImage.getNSlices();
        sizeT = selectedImage.getNFrames();
        
        // If slices > frames, assume metadata is switched
        if (slices > sizeT) {
            IJ.log("Swapping slices with frames");
            sizeT = slices;
            // if you want the ImagePlus metadata to match: 
            // selectedImage.setDimensions(sizeC, 1, sizeT); // C, Z, T
            // But be careful only do this if that matches your data layout.
        }
        
        // Only process stacks with at least 10 frames
        if (sizeT <= 10) {
            IJ.error("Stack must have more than 10 frames for alignment. Current frames: " + sizeT);
            selectedImage.close();
            return false;
        }
        
        return true;
    }
    
    private boolean showOptionsDialog() {
        GenericDialog gd = new GenericDialog("Alignment Options");
        
        gd.addCheckbox("Linear Alignment with SIFT", useSift);
        gd.addCheckbox("Template Matching", useTemplateMatching);
        
        gd.addMessage("At least one option is required. Use first and second option if your\n" +
                     "images have warping and lots of deformation. If you only have movement in the\n" +
                     "XY direction (sideways) and no warping, use only the second option");
        
        gd.addMessage("If using Template Matching, the plugin can be installed from:\n" + 
                     PLUGIN_INSTALLATION_URL);
        
        gd.addMessage("Alignment plugins need a reference image/frame to align the rest of\n" +
                     "the images. Set the frame here or first frame will be used as reference");
        
        gd.addNumericField("Choose reference slice for aligning stacks", referenceFrame, 0);
        
        gd.addMessage("If alignment is not satisfactory, try ticking this box.");
        gd.addCheckbox("Default settings", useDefaultSettings);
        
        gd.addMessage("If there are empty slices, it may affect alignment");
        
        gd.addHelp(PLUGIN_INSTALLATION_URL);
        gd.showDialog();
        
        if (gd.wasCanceled()) {
            selectedImage.close();
            return false;
        }
        
        useSift = gd.getNextBoolean();
        useTemplateMatching = gd.getNextBoolean();
        referenceFrame = (int) gd.getNextNumber();
        useDefaultSettings = gd.getNextBoolean();
        
        if (!useSift && !useTemplateMatching) {
            IJ.error("Choose at least one alignment option");
            return false;
        }
        
        return true;
    }
    
    private void processImage() {
        IJ.log("Processing: " + imageName);
        
        // Handle multi-channel images
        if (sizeC > 1) {
            if (!handleMultiChannelImage()) {
                return;
            }
        }
        
        // Set reference frame
        selectReferenceFrame(selectedImage, referenceFrame);
        
        // Run SIFT alignment if selected
        if (useSift) {
            runSiftAlignment();
        }
        
        // Run template matching if selected
        if (useTemplateMatching) {
            runTemplateMatching();
        }
        
        // Save the aligned image
        saveAlignedImage();
    }
    
    private boolean handleMultiChannelImage() {
        // Create channel options
        String[] channelOptions = new String[sizeC];
        for (int i = 0; i < sizeC; i++) {
            channelOptions[i] = "Channel " + (i + 1);
        }
        
        // Show channel selection dialog
        GenericDialog channelDialog = new GenericDialog("Choose Channel to Align");
        channelDialog.addMessage("Multiple channels detected. Please verify the channel to be aligned");
        channelDialog.addChoice("Channel", channelOptions, channelOptions[0]);
        channelDialog.showDialog();
        
        if (channelDialog.wasCanceled()) {
            selectedImage.close();
            return false;
        }
        
        selectedChannel = channelDialog.getNextChoiceIndex() + 1;
        
        // Split channels
        IJ.run(selectedImage, "Split Channels", "");
        
        // Select the chosen channel
        String channelWindowTitle = "C" + selectedChannel + "-" + imageName;
        selectedImage = WindowManager.getImage(channelWindowTitle);
        
        if (selectedImage == null) {
            IJ.error("Could not find channel: " + channelWindowTitle);
            return false;
        }
        
        selectedImage.setTitle(imageName);
        
        // Close other channels
        String[] windowTitles = WindowManager.getImageTitles();
        for (String title : windowTitles) {
            if (title.startsWith("C") && title.contains("-" + imageName) && !title.equals(channelWindowTitle)) {
                ImagePlus img = WindowManager.getImage(title);
                if (img != null) {
                    img.close();
                }
            }
        }
        
        IJ.wait(10);
        return true;
    }
    
    private void runSiftAlignment() {
        IJ.log("Running SIFT alignment...");

        IJ.run("Set Batch Mode...", "true");
        selectedImage.show();
        selectedImage.getWindow().toFront();
        WindowManager.setCurrentWindow(selectedImage.getWindow());

        int size = Math.min(sizeX, sizeY);
        int maximalAlignmentError = (int) Math.ceil(0.1 * size);
        double inlierRatio = (size < 500) ? 0.9 : 0.7;
        int featureDescSize = (size < 500) ? 8 : 4;

        String siftParams = 
            "initial_gaussian_blur=1.60 steps_per_scale_octave=4 minimum_image_size=64 " +
            "maximum_image_size=" + size + " feature_descriptor_size=" + featureDescSize + 
            " feature_descriptor_orientation_bins=8 closest/next_closest_ratio=0.92 " +
            "maximal_alignment_error=" + maximalAlignmentError + " inlier_ratio=" + inlierRatio +
            " transformation=Affine";

        selectedImage.setSlice(referenceFrame);
        IJ.run(selectedImage, "Linear Stack Alignment with SIFT", siftParams);
        IJ.wait(100);

        for (int i = 1; i <= WindowManager.getImageCount(); i++) {
            ImagePlus imp = WindowManager.getImage(i);
            if (imp.getTitle().toLowerCase().contains("aligned")) {
                selectedImage.close();
                selectedImage = imp;
                break;
            }
        }

        IJ.run("Set Batch Mode...", "false");
    }

    
    private void runTemplateMatching() {
        IJ.log("Running template matching...");

        // Ensure reference frame is properly selected in the right dimension
        selectReferenceFrame(selectedImage, referenceFrame);

        int xSize = (int) Math.floor(sizeX * 0.7);
        int ySize = (int) Math.floor(sizeY * 0.7);
        int x0 = (int) Math.floor(sizeX / 6.0);
        int y0 = (int) Math.floor(sizeY / 6.0);

        // Build template params, ensure spaces are inserted correctly
        String templateParams = "method=5 " +
                "windowsizex=" + xSize + " " +
                "windowsizey=" + ySize + " " +
                "x0=" + x0 + " " +
                "y0=" + y0 + " " +
                "swindow=0 " +
                "subpixel=false " +
                "itpmethod=0 " +
                "ref.slice=" + referenceFrame + " " +
                "show=true";

        IJ.run(selectedImage, "Align slices in stack...", templateParams);
        selectedImage.show();
        selectedImage.getWindow().toFront();
        WindowManager.setCurrentWindow(selectedImage.getWindow());
        IJ.wait(10);
    }
    private void saveAlignedImage() {
        String outputPath = inputDirectory + imageName + "_aligned.tif";
        IJ.log("Saving: " + outputPath);
        
        FileSaver fs = new FileSaver(selectedImage);
        if (fs.saveAsTiff(outputPath)) {
            IJ.log("Successfully saved aligned image: " + outputPath);
        } else {
            IJ.error("Failed to save aligned image: " + outputPath);
        }
    }

    /** 
     * Pick the appropriate position for the reference frame based on the image dimensions.
     * For hyperstacks use setPosition(channel, z, frame). For classic stacks use setSlice(slice).
     */
    private void selectReferenceFrame(ImagePlus imp, int refFrame1based) {
        int nC = imp.getNChannels();
        int nZ = imp.getNSlices();
        int nT = imp.getNFrames();

        // If we detect a time series (T>1 and Z==1), set the time (T)
        if (nT > 1 && nZ <= 1) {
            // Set channel to 1, z to 1, t to refFrame
            int c = 1;
            int z = 1;
            int t = Math.max(1, Math.min(refFrame1based, nT));
            imp.setPosition(c, z, t);
            IJ.log("setPosition(c,z,t) -> " + c + "," + z + "," + t);
        }
        // If it's a Z-stack (Z>1 and T==1), use setSlice
        else if (nZ > 1 && nT <= 1) {
            int slice = Math.max(1, Math.min(refFrame1based, nZ));
            imp.setSlice(slice);
            IJ.log("setSlice -> " + slice);
        }
        // If hyperstack with both Z and T, choose to interpret refFrame as TIME (T) like the macro
        else if (nZ > 1 && nT > 1) {
            // interpret referenceFrame as time (to match original macro behaviour)
            int c = 1;
            int z = 1;
            int t = Math.max(1, Math.min(refFrame1based, nT));
            imp.setPosition(c, z, t);
            IJ.log("Hyperstack: setPosition(c,z,t) -> " + c + "," + z + "," + t);
        } else {
            // fallback
            int slice = Math.max(1, Math.min(refFrame1based, imp.getStackSize()));
            imp.setSlice(slice);
            IJ.log("Fallback setSlice -> " + slice);
        }
    }
    
    /**
     * Alternative main method for testing
     */
    public static void main(String[] args) {
        // Initialize ImageJ if running standalone
        if (IJ.getInstance() == null) {
            new ImageJ();
        }
        
        AlignStack workflow = new AlignStack();
        workflow.run("");
    }
}