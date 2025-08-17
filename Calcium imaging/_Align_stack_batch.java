import ij.*;
import ij.plugin.*;
import ij.process.*;
import ij.gui.*;
import ij.io.*;
import loci.plugins.BF;
import loci.plugins.in.ImporterOptions;
import loci.formats.*;
import loci.formats.meta.IMetadata;
import ome.xml.model.primitives.PositiveInteger;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageReader;

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

public class _Align_stack_batch implements PlugIn {
    
    // Plugin parameters
    private boolean useSift = true;
    private String alignmentChoice = "Template Matching";
    private int referenceFrame = 1;
    private boolean useDefaultSettings = false;
    private String fileExtension = ".tif";
    private String inputDirectory;
    
    // Alignment options
    private static final String[] ALIGNMENT_OPTIONS = {"Template Matching", "StackReg"};
    
    @Override
    public void run(String arg) {
        // User needs to install the template matching plugin
        IJ.log("Please install this template plugin if you haven't already: " +
               "https://sites.google.com/site/qingzongtseng/template-matching-ij-plugin#install");
        
        // Clear the Results window
        IJ.log("\\Clear");
        
        // Get input directory
        DirectoryChooser dc = new DirectoryChooser("Choose Input Directory with images");
        inputDirectory = dc.getDirectory();
        if (inputDirectory == null) return; // User cancelled
        
        // Show options dialog
        if (!showOptionsDialog()) return;
        
        // Get list of files in directory
        File dir = new File(inputDirectory);
        File[] files = dir.listFiles();
        if (files == null) {
            IJ.error("Could not read directory, or no files in directory: " + inputDirectory);
            return;
        }
        
        // Print files in folder
        IJ.log("Files in folder:");
        for (File file : files) {
            IJ.log(file.getName());
        }
        
        // Process each file
        processFilesInDirectory(files);
        
        IJ.log("Alignment Finished");
    }
    
    /**
     * Shows the options dialog for alignment parameters
     * @return true if user clicked OK, false if cancelled
     */
    private boolean showOptionsDialog() {
        String html = "<html>" +
                     "<font size=+1>" +
                     "<br>" +
                     "If selecting Template matching, install plugin from website: " +
                     "<a href=https://sites.google.com/site/qingzongtseng/template-matching-ij-plugin#install>Installation</a><br>" +
                     "</font>";
        
        GenericDialog gd = new GenericDialog("Alignment Options");
        gd.addCheckbox("Linear Alignment with SIFT", useSift);
        gd.addRadioButtonGroup("Alignment in XY (no warping). Choose one", 
                              ALIGNMENT_OPTIONS, 1, ALIGNMENT_OPTIONS.length, ALIGNMENT_OPTIONS[0]);
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
        gd.addHelp(html);
        
        gd.showDialog();
        if (gd.wasCanceled()) return false;
        
        useSift = gd.getNextBoolean();
        alignmentChoice = gd.getNextRadioButton();
        referenceFrame = (int) gd.getNextNumber();
        useDefaultSettings = gd.getNextBoolean();
        fileExtension = gd.getNextString();
        
        return true;
    }
    
    /**
     * Process all files in the directory
     */
    private void processFilesInDirectory(File[] files) {
        for (File file : files) {
            String filePath = file.getAbsolutePath();
            
            if (file.getName().toLowerCase().endsWith(fileExtension.toLowerCase())) {
                if (fileExtension.equalsIgnoreCase(".lif")) {
                    processLifFile(filePath);
                } else {
                    processSingleFile(filePath);
                }
            } else {
                IJ.log("Skipping " + filePath + " as it does not match the LIF type needed: " + fileExtension);
            }
        }
    }
    
    /**
     * Process a single LIF file with multiple series
     */
    private void processLifFile(String filePath) {
        try {
            IJ.log("Processing LIF: " + filePath);
            
            // Use Bio-Formats to get series count
            ImageReader reader = new ImageReader();
            reader.setId(filePath);
            int seriesCount = reader.getSeriesCount();
            IJ.log("Series count = " + seriesCount);
            
            // Process each series
            for (int s = 0; s < seriesCount; s++) {
                try {
                    // Import using Bio-Formats
                    ImporterOptions options = new ImporterOptions();
                    options.setId(filePath);
                    options.setSeriesOn(s, true);
                    options.setColorMode(ImporterOptions.COLOR_MODE_DEFAULT);
                    options.setStackOrder(ImporterOptions.ORDER_XYCZT);
                    
                    ImagePlus[] imps = BF.openImagePlus(options);
                    if (imps.length > 0) {
                        ImagePlus imp = imps[0];
                        String seriesName = "Series_" + (s + 1);
                        imp.setTitle(seriesName);
                        imp.show();
                        
                        reader.setSeries(s);
                        processFileAlignment(imp, seriesName, reader);
                    }
                } catch (Exception e) {
                    IJ.log("Error processing " + (s + 1) + ": " + e.getMessage());
                }
            }
            
            reader.close();
        } catch (Exception e) {
            IJ.log("Error processing LIF " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Process a single file (non-LIF)
     */
    private void processSingleFile(String filePath) {
        try {
            // Use Bio-Formats to open the file
            ImporterOptions options = new ImporterOptions();
            options.setId(filePath);
            options.setAutoscale(true);
            options.setColorMode(ImporterOptions.COLOR_MODE_DEFAULT);
            options.setStackOrder(ImporterOptions.ORDER_XYCZT);
            
            ImagePlus[] imps = BF.openImagePlus(options);
            if (imps.length > 0) {
                ImagePlus imp = imps[0];
                String imgName = new File(filePath).getName();
                int dotIndex = imgName.lastIndexOf('.');
                if (dotIndex > 0) {
                    imgName = imgName.substring(0, dotIndex);
                }
                imp.setTitle(imgName);
                
                ImageReader reader = new ImageReader();
                reader.setId(filePath);
                processFileAlignment(imp, imgName, reader);
                reader.close();
            }
        } catch (Exception e) {
            IJ.log("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Process alignment for a single file/series
     */
    private void processFileAlignment(ImagePlus imp, String name, ImageReader reader) {
        try {
            imp.setT(referenceFrame);
            
            // Get image dimensions using Bio-Formats
            int sizeT = reader.getSizeT();
            int sizeZ = reader.getSizeZ();
            int sizeC = reader.getSizeC();
            int sizeX = reader.getSizeX();
            int sizeY = reader.getSizeY();
            
            // If slices > frames, swap them
            if (sizeZ > sizeT) {
                sizeT = sizeZ;
                IJ.log("Swapping slices with frames");
            }
            
            IJ.log("No of frames: " + sizeT);
            
            if (sizeT > 10) { // Only process if more than 10 frames
                IJ.log("Processing: " + name);
                
                if (sizeC > 1) {
                    // Multi-channel image - only align channel 1
                    IJ.log("Multichannel image, only aligning channel 1");
                    IJ.run(imp, "Split Channels", "");
                    IJ.wait(500);
                    
                    // Close other channels and keep C1
                    closeOtherChannels(name, sizeC);
                    ImagePlus c1Imp = WindowManager.getImage("C1-" + name);
                    if (c1Imp != null) {
                        c1Imp.setT(referenceFrame);
                        String aligned = alignImages("C1-" + name, sizeX, sizeY, sizeT);
                        saveAlignedImage(aligned, name);
                    }
                } else {
                    // Single channel
                    String aligned = alignImages(name, sizeX, sizeY, sizeT);
                    saveAlignedImage(aligned, name);
                }
            } else {
                IJ.log("Not a time series");
                imp.close();
            }
            
        } catch (Exception e) {
            IJ.log("Error in file alignment: " + e.getMessage());
        }
        
        // Garbage collection
        System.gc();
    }
    
    /**
     * Close other channels except C1
     */
    private void closeOtherChannels(String baseName, int totalChannels) {
        for (int i = 2; i <= totalChannels; i++) {
            ImagePlus toClose = WindowManager.getImage("C" + i + "-" + baseName);
            if (toClose != null) {
                toClose.close();
            }
        }
    }
    
    /**
     * Run alignment on the specified image
     */
    private String alignImages(String imageName, int sizeX, int sizeY, int sizeT) {
        ImagePlus imp = WindowManager.getImage(imageName);
        if (imp == null) {
            IJ.error("Could not find image: " + imageName);
            return null;
        }
        
        imp.setT(referenceFrame);
        ImageProcessor.setUseBicubic(true);
        
        // Set batch mode for faster processing
        boolean wasBatchMode = Interpreter.isBatchMode();
        if (!wasBatchMode) {
            Interpreter.batchMode = true;
        }
        
        try {
            // Run SIFT alignment if selected
            if (useSift) {
                IJ.log("Running SIFT");
                imp.setT(referenceFrame);
                alignWithSift(imp, sizeX, sizeY);
                IJ.wait(100);
                imp.close();
                imp = WindowManager.getImage("Aligned " + sizeT + " of " + sizeT);
                if (imp == null) {
                    IJ.error("SIFT alignment failed");
                    return null;
                }
            }
            
            // Run secondary alignment based on choice
            if (alignmentChoice.equals("Template Matching")) {
                imp.setT(referenceFrame);
                alignWithTemplateMatching(imp, sizeX, sizeY);
                IJ.wait(10);
            } else if (alignmentChoice.equals("StackReg")) {
                imp.setT(referenceFrame);
                IJ.run(imp, "StackReg", "transformation=[Rigid Body]");
            }
            
            return imp.getTitle();
            
        } finally {
            // Restore batch mode state
            if (!wasBatchMode) {
                Interpreter.batchMode = false;
            }
        }
    }
    
    /**
     * Run SIFT alignment with adaptive parameters
     */
    private void alignWithSift(ImagePlus imp, int sizeX, int sizeY) {
        int size = Math.min(sizeX, sizeY);
        int maximalAlignmentError = (int) Math.ceil(0.1 * size);
        
        String siftParams;
        
        if (!useDefaultSettings) {
            double inlierRatio = 0.7;
            int featureDescSize = 4;
            
            if (size < 500) {
                // For smaller images these parameters work well
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
            maximalAlignmentError = (int) Math.ceil(0.1 * size);
            
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
    
    /**
     * Run template matching alignment
     */
    private void alignWithTemplateMatching(ImagePlus imp, int sizeX, int sizeY) {
        int xSize = (int) Math.floor(sizeX * 0.7);
        int ySize = (int) Math.floor(sizeY * 0.7);
        int x0 = (int) Math.floor(sizeX / 6.0);
        int y0 = (int) Math.floor(sizeY / 6.0);
        
        String templateParams = "method=5 " +
                               "windowsizex=" + xSize + " " +
                               "windowsizey=" + ySize + " " +
                               "x0=" + x0 + " " +
                               "y0=" + y0 + " " +
                               "swindow=0 " +
                               "subpixel=false " +
                               "itpmethod=0 " +
                               "ref.slice=3 " +
                               "show=true";
        
        IJ.run(imp, "Align slices in stack...", templateParams);
    }
    
    /**
     * Save the aligned image
     */
    private void saveAlignedImage(String alignedTitle, String originalName) {
        if (alignedTitle == null) return;
        
        ImagePlus alignedImp = WindowManager.getImage(alignedTitle);
        if (alignedImp != null) {
            String outputPath = inputDirectory + originalName + "_aligned.tif";
            IJ.log("Saving: " + outputPath);
            IJ.saveAs(alignedImp, "Tiff", outputPath);
            alignedImp.close();
        }
    }
}
