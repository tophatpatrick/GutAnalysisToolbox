import ij.*;
import ij.plugin.*;
import ij.process.*;
import ij.gui.*;
import ij.io.*;
import java.awt.*;
import java.io.File;

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
public class Stack_Alignment_Plugin implements PlugIn {
    
    // Plugin params
    private boolean useSift = true;
    private boolean useTemplateMatching = true;
    private int referenceFrame = 1;
    private boolean useDefaultSettings = false;
    
    @Override // Plugin interface method
    public void run(String arg) {
        // User needs to install the template matching plugin
        IJ.log("Please install this template plugin if you are getting an error: " +
               "https://sites.google.com/site/qingzongtseng/template-matching-ij-plugin#install");
        
        // Clear the Results window
        IJ.log("\\Clear");
        
        // Use OpenDialog to choose file to align
        OpenDialog od = new OpenDialog("Choose the file to be aligned");
        String fileOpen = od.getFileName();
        if (fileOpen == null) return; // No file entered / user cancelled?
        
        String dir = od.getDirectory();
        String path = dir + fileOpen;
        
        // Open image
        ImagePlus img = IJ.openImage(path);
        if (img == null) {
            IJ.error("No image at: " + path);
            return;
        }
        
        // Get image name without extension
        String imgName = fileOpen.substring(0, fileOpen.lastIndexOf('.'));
        img.setTitle(imgName);
        img.show();
        
        // Get image dimensions
        int[] dims = img.getDimensions(); // [width, height, nChannels, nSlices, nFrames]
        int sizeX = dims[0];
        int sizeY = dims[1];
        int sizeC = dims[2];
        int slices = dims[3];
        int sizeT = dims[4];
        
        // If number of slices > frames, assume metadata is switched
        if (slices > sizeT) {
            IJ.log("Metadata switched: treating slices as frames");
            sizeT = slices;
        }
        
        // Show options dialog
        if (!showOptionsDialog()) return;
        
        // Validate option is selected
        if (!useSift && !useTemplateMatching) {
            IJ.error("Need an alignment option");
            return;
        }
        
        // Only process if stack has > 10 frames
        if (sizeT > 10) {
            IJ.log("Processing: " + imgName);
            
            if (sizeC > 1) {
                // Multiple channels
                processMultiChannelStack(img, imgName, sizeX, sizeY, sizeC, sizeT, dir);
            } else {
                // Single channel processing
                processSingleChannelStack(img, imgName, sizeX, sizeY, sizeT, dir);
            }
        } else {
            IJ.log("Insufficient frames. Series has " + sizeT + " frames");
            img.close();
        }
        
        //  garbage collector
        System.gc();
        IJ.log("Done");
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
        gd.addCheckbox("Template Matching", useTemplateMatching);
        gd.addMessage("At least one option is required. Use first and second option if your\n" +
                     "images have warping and lots of deformation. If you only have movement in the\n" +
                     "XY direction (sideways) and no warping, use only the second option");
        gd.addMessage("If using Template Matching, the plugin can be installed using the Help button");
        gd.addMessage("Alignment plugins need a reference image/frame to align the rest of\n" +
                     "the images. Set the frame here or first frame will be used as reference");
        gd.addNumericField("Choose reference slice for aligning stacks", referenceFrame, 0);
        gd.addMessage("If alignment is not satisfactory, try ticking this box.");
        gd.addCheckbox("Default settings", useDefaultSettings);
        gd.addMessage("If there are empty slices, it may affect alignment");
        gd.addHelp(html);
        
        gd.showDialog();
        if (gd.wasCanceled()) return false;
        
        useSift = gd.getNextBoolean();
        useTemplateMatching = gd.getNextBoolean();
        referenceFrame = (int) gd.getNextNumber();
        useDefaultSettings = gd.getNextBoolean();
        
        return true;
    }
    
    /**
     * Process stack with multiple channels
     */
    private void processMultiChannelStack(ImagePlus imp, String imgName, 
                                        int sizeX, int sizeY, int sizeC, int sizeT, String directory) {
        // Let user choose which channel to align
        IJ.showMessage("Multiple channels detected.");
        
        String[] channels = new String[sizeC];
        for (int i = 0; i < sizeC; i++) {
            channels[i] = String.valueOf(i + 1);
        }
        
        GenericDialog gd = new GenericDialog("Choose Channel");
        gd.addChoice("Channel", channels, "1");
        gd.showDialog();
        if (gd.wasCanceled()) return; // User cancelled
        
        int channelToAlign = Integer.parseInt(gd.getNextChoice());
        
        // Split channels and keep only the selected one
        IJ.run(imp, "Split Channels", "");
        IJ.wait(100); // Wait for channels to split (I believe this is sufficient runtime)

        // Select the chosen channel
        ImagePlus channelImp = WindowManager.getImage("C" + channelToAlign + "-" + imgName);
        if (channelImp == null) {
            IJ.error("Could not find channel: " + channelToAlign);
            return;
        }
        
        channelImp.setTitle(imgName);
        
        // Close other channels
        closeOtherChannels(imgName, channelToAlign, sizeC);
        
        // Process the selected channel
        processAlignment(channelImp, imgName, sizeX, sizeY, sizeT, directory);
    }
    
    