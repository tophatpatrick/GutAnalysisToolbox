package Features.Tools;

import ij.*;
import ij.gui.*;
import ij.io.*;
import ij.plugin.*;

import java.io.File;
import java.util.ArrayList;

/**
 * ImageJ Plugin to run Linear Stack Alignment with SIFT and/or Template matching on TIFF files
 * 
 * This plugin reads TIFF stack files with at least 10 frames and runs a combination of 
 * registration plugins on each TIFF file. The aligned stack is saved with "_aligned" suffix.
 * 
 * Note: If more than one channel per TIFF file, alignment will only run on the channel chosen by user.
 * Alignment runs in batch mode for faster processing.
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

public class AlignStackBatchDeprecated implements PlugIn {
    
     private static final String PLUGIN_INSTALLATION_URL = 
        "https://sites.google.com/site/qingzongtseng/template-matching-ij-plugin#install";
    
    private String inputDirectory;
    private boolean useSift = true;
    private String alignmentChoice = "Template Matching";
    private int referenceFrame = 1;
    private boolean useDefaultSettings = false;
    private String fileExtension = ".tif";
    
    // Current file processing variables
    private int currentSizeX, currentSizeY, currentSizeC, currentSizeT, currentSizeZ;

    @Override
    public void run(String arg) {
        // Clear results window
        IJ.log("\\Clear");
        IJ.log("Please install template plugin if you haven't already: " + PLUGIN_INSTALLATION_URL);
        
        try {
            // Step 1: Select input directory
            if (!selectInputDirectory()) {
                return;
            }
            
            // Step 2: Show options dialog
            if (!showOptionsDialog()) {
                return;
            }
            
            // Step 3: Initialize Bio-Formats macro extensions
            initializeBioFormats();
            
            // Step 4: Process all files in directory
            processBatchFiles();
            
        } catch (Exception e) {
            IJ.error("Error during batch alignment", e.getMessage());
            e.printStackTrace();
        } finally {
            // Cleanup
            closeBioFormats();
            System.gc();
        }
        
        IJ.showMessage("Batch Alignment Complete", "All files have been processed successfully!");
    }
    
    private boolean selectInputDirectory() {
        DirectoryChooser dc = new DirectoryChooser("Choose Input Directory with images");
        inputDirectory = dc.getDirectory();
        
        if (inputDirectory == null) {
            return false; // User cancelled
        }
        
        IJ.log("Selected directory: " + inputDirectory);
        return true;
    }
    
    private boolean showOptionsDialog() {
        String[] alignmentOptions = {"Template Matching", "StackReg"};
        
        GenericDialog gd = new GenericDialog("Batch Alignment Options");
        
        gd.addCheckbox("Linear Alignment with SIFT", useSift);
        gd.addRadioButtonGroup("Alignment in XY (no warping). Choose one:", 
                              alignmentOptions, 1, alignmentOptions.length, alignmentOptions[0]);
        
        gd.addMessage("Use Linear Alignment with SIFT if your\n" +
                     "images have warping and lots of deformation. If you only have movement in the\n" +
                     "XY direction (sideways) and no warping, use either Template Matching or StackReg");
        
        gd.addNumericField("Choose reference slice for aligning stacks", referenceFrame, 0);
        gd.addMessage("Alignment plugins need a reference image/frame to align the rest of\n" +
                     "the images. Set the frame here or first frame will be used as reference");
        
        gd.addMessage("If alignment is not satisfactory, try ticking this box.");
        gd.addCheckbox("Default settings", useDefaultSettings);
        
        gd.addStringField("File extension: ", fileExtension);
        gd.addMessage("If the files are Leica .lif files, each series within the file will be aligned");
        
        gd.addHelp(PLUGIN_INSTALLATION_URL);
        gd.showDialog();
        
        if (gd.wasCanceled()) {
            return false;
        }
        
        useSift = gd.getNextBoolean();
        alignmentChoice = gd.getNextRadioButton();
        referenceFrame = (int) gd.getNextNumber();
        useDefaultSettings = gd.getNextBoolean();
        fileExtension = gd.getNextString();
        
        return true;
    }
    
    private void initializeBioFormats() {
        // Initialize Bio-Formats
        IJ.run("Bio-Formats Macro Extensions");
    }
    
    private void closeBioFormats() {
        IJ.runMacro("Ext.close();");
    }
    
    private void processBatchFiles() {
        File inputDir = new File(inputDirectory);
        File[] files = inputDir.listFiles();
        
        if (files == null || files.length == 0) {
            IJ.error("No files found in directory: " + inputDirectory);
            return;
        }
        
        IJ.log("Files in folder:");
        ArrayList<String> validFiles = new ArrayList<>();
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(fileExtension)) {
                validFiles.add(file.getName());
                IJ.log("  " + file.getName());
            }
        }
        
        if (validFiles.isEmpty()) {
            IJ.error("No files with extension '" + fileExtension + "' found in directory");
            return;
        }
        
        // Process each file
        for (String fileName : validFiles) {
            String filePath = inputDirectory + File.separator + fileName;
            IJ.log("Processing file: " + filePath);
            
            try {
                if (fileExtension.equals(".lif")) {
                    processLifFile(filePath);
                } else {
                    processTiffFile(filePath);
                }
            } catch (Exception e) {
                IJ.log("Error processing file " + fileName + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    private void processLifFile(String filePath) {
        try {
            // Set file ID
            IJ.runMacro("Ext.setId(\"" + filePath + "\");");
            
            // Get series count
            String seriesCountStr = IJ.runMacro("Ext.getSeriesCount(seriesCount); return seriesCount;");
            int seriesCount = Integer.parseInt(seriesCountStr.trim());
            IJ.log("Series count: " + seriesCount);
            
            for (int s = 1; s <= seriesCount; s++) {
                // Import series
                String importCommand = "open=[" + filePath + "] " +
                                     "color_mode=Default " +
                                     "rois_import=[ROI manager] " +
                                     "view=Hyperstack " +
                                     "stack_order=XYCZT " +
                                     "series_" + s;
                
                IJ.run("Bio-Formats Importer", importCommand);
                
                ImagePlus imp = IJ.getImage();
                if (imp != null) {
                    String imageName = imp.getTitle();
                    IJ.log("Processing series " + s + ": " + imageName);
                    IJ.runMacro("Ext.setSeries(" + (s - 1) + ");");
                    
                    processFileAlignment(imp, imageName);
                }
            }
            
        } catch (Exception e) {
            IJ.log("Error processing LIF file: " + e.getMessage());
        }
    }
    
    private void processTiffFile(String filePath) {
        try {
            // Set file ID
            IJ.runMacro("Ext.setId(\"" + filePath + "\");");
            
            String importCommand = "open=[" + filePath + "] " +
                                 "autoscale " +
                                 "color_mode=Default " +
                                 "rois_import=[ROI manager] " +
                                 "view=Hyperstack " +
                                 "stack_order=XYCZT";
            
            IJ.run("Bio-Formats Importer", importCommand);
            
            ImagePlus imp = IJ.getImage();
            if (imp != null) {
                String imageName = new File(filePath).getName();
                if (imageName.contains(".")) {
                    imageName = imageName.substring(0, imageName.lastIndexOf('.'));
                }
                
                imp.setTitle(imageName);
                processFileAlignment(imp, imageName);
            }
            
        } catch (Exception e) {
            IJ.log("Error processing TIFF file: " + e.getMessage());
        }
    }
    
    private void processFileAlignment(ImagePlus imp, String imageName) {
        if (imp == null) {
            IJ.log("No image to process for: " + imageName);
            return;
        }
        
        imp.show();
        imp.setSlice(referenceFrame);
        
        // Get dimensions
        extractImageDimensions();
        
        // Handle slice/frame confusion
        if (currentSizeZ > currentSizeT) {
            currentSizeT = currentSizeZ;
            IJ.log("Swapping slices with frames");
        }
        
        IJ.log("No of frames: " + currentSizeT);
        
        // Only process if more than 10 frames
        if (currentSizeT <= 10) {
            IJ.log("Not a time series - skipping " + imageName);
            imp.close();
            return;
        }
        
        IJ.log("Processing: " + imageName);
        
        try {
            String alignedTitle;
            
            if (currentSizeC > 1) {
                // Multi-channel: work only on channel 1
                IJ.log("Multichannel image, only aligning channel 1");
                IJ.run(imp, "Split Channels", "");
                IJ.wait(500);
                
                // Close channel 2 and keep channel 1
                ImagePlus channel2 = WindowManager.getImage("C2-" + imageName);
                if (channel2 != null) {
                    channel2.close();
                }
                
                // Close any additional channels
                for (int c = 3; c <= currentSizeC; c++) {
                    ImagePlus channelImp = WindowManager.getImage("C" + c + "-" + imageName);
                    if (channelImp != null) {
                        channelImp.close();
                    }
                }
                
                ImagePlus channel1 = WindowManager.getImage("C1-" + imageName);
                if (channel1 != null) {
                    channel1.setSlice(referenceFrame);
                    alignedTitle = alignImages(channel1, "C1-" + imageName);
                    
                    ImagePlus aligned = WindowManager.getImage(alignedTitle);
                    if (aligned != null) {
                        saveAlignedImage(aligned, imageName);
                    }
                }
            } else {
                // Single channel
                alignedTitle = alignImages(imp, imageName);
                
                ImagePlus aligned = WindowManager.getImage(alignedTitle);
                if (aligned != null) {
                    saveAlignedImage(aligned, imageName);
                }
            }
            
        } catch (Exception e) {
            IJ.log("Error processing " + imageName + ": " + e.getMessage());
        } finally {
            // Cleanup
            System.gc();
        }
    }
    
    private void extractImageDimensions() {
        try {
            // get dimensions
            String sizeT = IJ.runMacro("Ext.getSizeT(sizeT); return sizeT;");
            String sizeZ = IJ.runMacro("Ext.getSizeZ(sizeZ); return sizeZ;");
            String sizeC = IJ.runMacro("Ext.getSizeC(sizeC); return sizeC;");
            String sizeX = IJ.runMacro("Ext.getSizeX(sizeX); return sizeX;");
            String sizeY = IJ.runMacro("Ext.getSizeY(sizeY); return sizeY;");
            
            currentSizeT = Integer.parseInt(sizeT.trim());
            currentSizeZ = Integer.parseInt(sizeZ.trim());
            currentSizeC = Integer.parseInt(sizeC.trim());
            currentSizeX = Integer.parseInt(sizeX.trim());
            currentSizeY = Integer.parseInt(sizeY.trim());
            
        } catch (Exception e) {
            IJ.log("Could not extract dimensions via Bio-Formats, using ImagePlus dimensions");
            // Fallback to ImagePlus dimensions
            ImagePlus currentImp = IJ.getImage();
            if (currentImp != null) {
                currentSizeX = currentImp.getWidth();
                currentSizeY = currentImp.getHeight();
                currentSizeC = currentImp.getNChannels();
                currentSizeT = currentImp.getNFrames();
                currentSizeZ = currentImp.getNSlices();
            }
        }
    }
    
    private String alignImages(ImagePlus imp, String imageName) {
        if (imp == null) {
            IJ.log("No image to align: " + imageName);
            return imageName;
        }
        
        WindowManager.setCurrentWindow(imp.getWindow());
        
        // Enable batch mode
        IJ.run("Set Batch Mode", "true");
        
        String alignedTitle = imageName;
        
        try {
            // Run SIFT alignment if selected
            if (useSift) {
                IJ.log("Running SIFT on " + imageName);
                imp.setSlice(referenceFrame);
                runSiftAlignment(imp);
                IJ.wait(100);
                
                imp.close();
                alignedTitle = "Aligned " + currentSizeT + " of " + currentSizeT;
                
                ImagePlus aligned = WindowManager.getImage(alignedTitle);
                if (aligned != null) {
                    imp = aligned;
                    alignedTitle = aligned.getTitle();
                } else {
                    IJ.log("Warning: SIFT alignment may have failed for " + imageName);
                    return imageName;
                }
            }
            
            // Run secondary alignment
            if (alignmentChoice.equals("Template Matching")) {
                imp.setSlice(referenceFrame);
                runTemplateMatching(imp);
                IJ.wait(10);
            } else if (alignmentChoice.equals("StackReg")) {
                imp.setSlice(referenceFrame);
                IJ.run(imp, "StackReg", "transformation=[Rigid Body]");
            }
            
            alignedTitle = imp.getTitle();
            
        } catch (Exception e) {
            IJ.log("Error during alignment of " + imageName + ": " + e.getMessage());
        } finally {
            // Exit batch mode
            IJ.run("Set Batch Mode", "exit and display");
        }
        
        return alignedTitle;
    }
    
    private void runSiftAlignment(ImagePlus imp) {
        int size = Math.min(currentSizeX, currentSizeY);
        int maximalAlignmentError = (int) Math.ceil(0.1 * size);
        
        String siftParams;
        
        if (!useDefaultSettings) {
            double inlierRatio = 0.7;
            int featureDescSize = 4;
            
            if (size < 500) {
                // For smaller images
                featureDescSize = 8;
                maximalAlignmentError = 5;
                inlierRatio = 0.9;
            }
            
            siftParams = "initial_gaussian_blur=1.60 " +
                        "steps_per_scale_octave=4 " +
                        "minimum_image_size=64 " +
                        "maximum_image_size=" + size + " " +
                        "feature_descriptor_size=" + featureDescSize + " " +
                        "feature_descriptor_orientation_bins=8 " +
                        "closest/next_closest_ratio=0.92 " +
                        "maximal_alignment_error=" + maximalAlignmentError + " " +
                        "inlier_ratio=" + inlierRatio + " " +
                        "expected_transformation=Affine";
        } else {
            siftParams = "initial_gaussian_blur=1.60 " +
                        "steps_per_scale_octave=3 " +
                        "minimum_image_size=64 " +
                        "maximum_image_size=" + size + " " +
                        "feature_descriptor_size=4 " +
                        "feature_descriptor_orientation_bins=8 " +
                        "closest/next_closest_ratio=0.92 " +
                        "maximal_alignment_error=" + maximalAlignmentError + " " +
                        "inlier_ratio=0.05 " +
                        "expected_transformation=Affine";
        }
        
        IJ.run(imp, "Linear Stack Alignment with SIFT", siftParams);
    }
    
    private void runTemplateMatching(ImagePlus imp) {
        int xSize = (int) Math.floor(currentSizeX * 0.7);
        int ySize = (int) Math.floor(currentSizeY * 0.7);
        int x0 = (int) Math.floor(currentSizeX / 6.0);
        int y0 = (int) Math.floor(currentSizeY / 6.0);
        
        String templateParams = "method=5 " +
                               "windowsizex=" + xSize + " " +
                               "windowsizey=" + ySize + " " +
                               "x0=" + x0 + " " +
                               "y0=" + y0 + " " +
                               "swindow=0 " +
                               "subpixel=false " +
                               "itpmethod=0 " +
                               "ref.slice=" + referenceFrame + 
                               "show=true";
        
        IJ.run(imp, "Align slices in stack...", templateParams);
    }
    
    private void saveAlignedImage(ImagePlus alignedImp, String originalName) {
        String outputPath = inputDirectory + File.separator + originalName + "_aligned.tif";
        IJ.log("Saving: " + outputPath);
        
        try {
            FileSaver fs = new FileSaver(alignedImp);
            if (fs.saveAsTiff(outputPath)) {
                IJ.log("Successfully saved: " + outputPath);
            } else {
                IJ.log("Failed to save: " + outputPath);
            }
            
            // Close the aligned image
            String closeTitle = originalName + "_aligned.tif";
            ImagePlus toClose = WindowManager.getImage(closeTitle);
            if (toClose != null) {
                toClose.close();
            } else {
                // Try closing with current title
                alignedImp.close();
            }
            
        } catch (Exception e) {
            IJ.log("Error saving " + outputPath + ": " + e.getMessage());
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
        
        AlignStackBatchDeprecated workflow = new AlignStackBatchDeprecated();
        workflow.run("");
    }
}