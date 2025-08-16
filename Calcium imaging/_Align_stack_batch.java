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
