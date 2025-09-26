package Analysis;

import ij.*;
import ij.gui.*;
import ij.io.*;
import ij.measure.Calibration;
import ij.plugin.*;
import ij.plugin.frame.RoiManager;
import java.io.File;

/**
 * ImageJ Plugin for Calcium Imaging Analysis.
 * * Processes an aligned calcium imaging stack, offering options for F/F0 normalization, 
 * ROI management (manual or loading from file), and automated cell segmentation using StarDist.
 * It saves intensity measurements, a maximum projection, the normalized stack (if used), and ROIs.
 * * @author Pradeep Rajasekhar (Original macro), Converted to Java by Gemini
 * @version 1.0
 * @since September 2025
 * * License: BSD3
 * * Copyright 2021 Pradeep Rajasekhar, INM Lab, Monash Institute of Pharmaceutical Sciences
 * * Redistribution and use in source and binary forms, with or without modification, are permitted 
 * provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions 
 * and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions 
 * and the following disclaimer in the documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse 
 * or promote products derived from this software without specific prior written permission.
 * * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED 
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A 
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR 
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT 
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, 
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN 
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

public class CalciumAnalysis implements PlugIn {

    // Global img references
    private ImagePlus originalStack;
    private ImagePlus calciumMax;
    private ImagePlus calciumNormalise; //  stack used for measurement (original or F/F0)
    private RoiManager rm;

    // Image metadata
    private String imageName;
    private String fileDir;
    private String resultsPath;
    private String outputFolder;
    private double pixelWidth;
    private int sizeX, sizeY, sizeC, slices, frames;

    // User options
    private boolean useFF0 = true;
    private boolean useStarDist = false;
    private int cellTypes = 1;
    
    // StarDist params 
    private double scale = 1.0;
    private double probability = 0.5;
    private double overlap = 0.3;
    private int nTiles = 4;
    private double neuronSegLowerLimit = 10.0;
    
    // Internal constants
    private static final double STAR_DIST_TRAINING_PX_SIZE = 0.568; // Default training pixel size for models

    @Override
    public void run(String arg) {
        IJ.log("\\Clear");
        IJ.log("Starting Calcium Imaging Analysis...");

        try {
            // Step 1: Initial Setup and File Open
            if (!showInitialSetupDialog()) return;

            // Step 2: Validate and Prepare Image Stack
            if (!validateAndPrepareStack()) return;

            // Step 3: Create Max Projection
            if (!createMaxProjection()) return;

            // Step 4: Normalization
            if (useFF0) {
                calciumNormalise = calculateFF0(originalStack.getTitle());
            } else {
                calciumNormalise = originalStack;
            }

            // Step 5: ROI Analysis
            processRois();

            // Step 6: Measurement and Saving
            saveResultsAndCleanup();

        } catch (Exception e) {
            IJ.error("Calcium Analysis Error", e.getMessage());
            e.printStackTrace();
        } finally {
            System.gc();
        }

        IJ.showMessage("Calcium Analysis Complete", "Processing finished successfully. Results saved to:\n" + outputFolder);
    }

    // --- Step 1: Initial Setup Dialog ---
    private boolean showInitialSetupDialog() {
        OpenDialog od = new OpenDialog("Open the aligned calcium imaging stack");
        String directory = od.getDirectory();
        String fileName = od.getFileName();
        
        if (fileName == null) return false;

        originalStack = IJ.openImage(directory + fileName);
        if (originalStack == null) {
            IJ.error("Could not open image: " + fileName);
            return false;
        }

        imageName = originalStack.getTitle();
        fileDir = directory;
        originalStack.show();

        // Dialogue for options
        GenericDialog gd = new GenericDialog("Calcium Analysis Options");
        gd.addMessage("F/F0 is the stack normalised to baseline.");
        gd.addCheckbox("Use F/F0 normalization", useFF0);
        gd.addCheckbox("Use Stardist model for segmenting neurons", useStarDist);
        gd.addMessage("If you are not sure, leave 'Use F/F0' checked.");
        
        gd.showDialog();

        if (gd.wasCanceled()) {
            originalStack.close();
            return false;
        }
        
        useFF0 = gd.getNextBoolean();
        useStarDist = gd.getNextBoolean();
        
        return true;
    }

    // --- Step 2: Validate and Prepare Image Stack ---
    private boolean validateAndPrepareStack() {
        sizeX = originalStack.getWidth();
        sizeY = originalStack.getHeight();
        sizeC = originalStack.getNChannels();
        slices = originalStack.getNSlices();
        frames = originalStack.getNFrames();
        
        // Handle dimension swap (slices > 10 and frames == 1)
        if (slices > 10 && frames == 1) {
            IJ.log("Swapping slices with frames.");
            frames = slices;
            slices = 1;
            originalStack.setDimensions(sizeC, slices, frames);
        }

        // Check for min frames
        if (frames <= 10) {
            IJ.error("ERROR", "The image is less than 10 frames/slices. Please choose the right stack (Current frames: " + frames + ")");
            originalStack.close();
            return false;
        }

        // Get pixel size and handle StarDist calibration requirement
        Calibration cal = originalStack.getCalibration();
        pixelWidth = cal.pixelWidth;
        String unit = cal.getUnits();
        
        if (useStarDist && !isUnitMicron(unit)) {
            IJ.showMessage("Calibration Required", "Image is not calibrated in microns. This is required for accurate segmentation using StarDist.");
            
            // Get pixel size from user
            GenericDialog gd = new GenericDialog("Enter Pixel Size");
            gd.addNumericField("Enter pixelsize in microns", STAR_DIST_TRAINING_PX_SIZE, 3);
            gd.showDialog();
            
            if (gd.wasCanceled()) {
                originalStack.close();
                return false;
            }
            pixelWidth = gd.getNextNumber();
            
            // Apply calibration temporarily
            cal.pixelWidth = pixelWidth;
            cal.pixelHeight = pixelWidth;
            cal.setUnit("um");
            originalStack.setCalibration(cal);
        }

        // Setup output directories
        String baseName = imageName.contains(".") ? imageName.substring(0, imageName.lastIndexOf('.')) : imageName;
        fileDir = new File(originalStack.getOriginalFileInfo().directory).getAbsolutePath() + File.separator;
        resultsPath = fileDir + "RESULTS" + File.separator;
        outputFolder = resultsPath + baseName + File.separator;

        File resultsDir = new File(resultsPath);
        File outputDir = new File(outputFolder);
        if (!resultsDir.exists()) resultsDir.mkdir();
        if (!outputDir.exists()) outputDir.mkdir();
        
        IJ.log("Processing: " + baseName);
        
        return true;
    }
    
    private boolean isUnitMicron(String unit) {
        if (unit == null) return false;
        String u = unit.trim().toLowerCase();
        return u.equals("microns") || u.equals("micron") || u.equals("um");
    }

    // --- Step 3: Create Max Projection ---
    private boolean createMaxProjection() {
        // Macro's way of deleting the last slice is weird.
        // It relies on the slice dimension, which is typically 1 for a time series (T-stack).
        // Instead use user interaction?
        
        // User is prompted to delete artifacts and set projection range
        IJ.showMessage("Delete Artifacts", "Delete any drug addition artefacts or blank frames. Click OK after you are done.");
        
        // Get Max Projection frames
        GenericDialog gdMax = new GenericDialog("Max Projection Frames");
        gdMax.addNumericField("Starting frame:", 1, 0);
        gdMax.addNumericField("End frame:", frames, 0);
        gdMax.showDialog();
        if (gdMax.wasCanceled()) {
             originalStack.close();
             return false;
        }
        int startFrame = (int) gdMax.getNextNumber();
        int endFrame = (int) gdMax.getNextNumber();

        // Run Z Projection (Max Intensity)
        IJ.run(originalStack, "Z Project...", "start=" + startFrame + " stop=" + endFrame + " projection=[Max Intensity]");
        
        calciumMax = WindowManager.getCurrentImage();
        if (calciumMax == null) {
            IJ.error("Max Projection Failed", "Could not create Max Projection.");
            return false;
        }
        
        // Convert and Save Max Projection
        IJ.run(calciumMax, "RGB Color", "");
        IJ.saveAs(calciumMax, "Tiff", outputFolder + "MAX_" + imageName);
        
        return true;
    }

    // --- Step 4: Normalization (F/F0) ---
    private ImagePlus calculateFF0(String originalName) {
        ImagePlus tempStack = WindowManager.getImage(originalName);
        
        // Get F0 (Baseline) frames
        GenericDialog gdFF0 = new GenericDialog("Calculate F/F0 Frames");
        gdFF0.addNumericField("Starting baseline frame:", 1, 0);
        gdFF0.addNumericField("End baseline frame:", 50, 0);
        gdFF0.showDialog();
        
        if (gdFF0.wasCanceled()) {
             IJ.error("F/F0 Cancelled", "Using original stack for measurement.");
             return originalStack;
        }
        
        int start = (int) gdFF0.getNextNumber();
        int end = (int) gdFF0.getNextNumber();
        
        // 1. Create F0 (Average Intensity Projection)
        IJ.run(tempStack, "Z Project...", "start=" + start + " stop=" + end + " projection=[Average Intensity]");
        ImagePlus f0 = WindowManager.getCurrentImage();
        
        // 2. Calculate F/F0
        IJ.run("Image Calculator...", "operation=[Divide] image1=" + originalName + " image2=" + f0.getTitle() + " create 32-bit stack");
        ImagePlus ff0Stack = WindowManager.getCurrentImage();
        
        // 3. Cleanup and Enhancement
        f0.close(); // Close the temporary F0 projection
        ff0Stack.setTitle("F_F0_32bit_" + imageName);
        IJ.selectWindow(ff0Stack.getTitle());
        IJ.run("mpl-viridis"); 
        IJ.run("Enhance Contrast", "saturated=0.35");
        
        ff0Stack.show(); // Display F/F0 stack
        
        return ff0Stack;
    }

    // --- Step 5: ROI Analysis Loop ---
    private void processRois() {
        rm = RoiManager.getInstance();
        if (rm == null) rm = new RoiManager(true); // Create a non-visible manager if none exists
        rm.reset();
        
        // Get number of cell types
        GenericDialog gdCell = new GenericDialog("Cell Type Analysis");
        gdCell.addNumericField("Enter the number of celltypes that will be analysed", cellTypes, 0);
        gdCell.showDialog();
        if (gdCell.wasCanceled()) return; // Stop processing ROIs
        cellTypes = (int) gdCell.getNextNumber();
        
        int roiStart = 0;
        
        for (int i = 0; i < cellTypes; i++) {
            
            // Check if loading ROI file or drawing manually
            GenericDialog gdRoiSource = new GenericDialog("ROI Source for Cell Type " + (i + 1));
            gdRoiSource.addCheckbox("Do you have an ROI Manager file?", false);
            gdRoiSource.showDialog();
            
            if (gdRoiSource.wasCanceled()) break;
            boolean roiFile = gdRoiSource.getNextBoolean();
            
            String cellName = "CellType_" + (i + 1);
            
            if (roiFile) {
                // Load ROIs from file
                OpenDialog odRoi = new OpenDialog("Choose ROI Manager file (.zip)");
                String roiPath = odRoi.getPath();
                if (roiPath != null) {
                    rm.open(roiPath);
                    IJ.selectWindow(calciumMax.getTitle());
                    IJ.run(calciumMax, "Show All", "");
                    IJ.showMessage("Verify ROIs", "Verify ROIs on the Max Projection. Press OK when done.");
                    
                    GenericDialog gdName = new GenericDialog("Enter Cell Name");
                    gdName.addStringField("Enter name of celltype", "Neuron");
                    gdName.showDialog();
                    if (gdName.wasCanceled()) break;
                    cellName = gdName.getNextString();
                }
            } else {
                // Manual/StarDist ROIs
                GenericDialog gdName = new GenericDialog("Enter Cell Name");
                gdName.addStringField("Enter name of celltype", "LEC");
                gdName.showDialog();
                if (gdName.wasCanceled()) break;
                cellName = gdName.getNextString();

                if (useStarDist) {
                    runStarDistSegmentation(calciumMax, rm);
                }
                
                // Prompt for manual drawing (used for both StarDist verification and manual draw)
                IJ.selectWindow(calciumMax.getTitle());
                IJ.setTool(Toolbar.OVAL); // Set tool to oval
                IJ.showMessage("Draw ROIs", "Draw ROIs for " + cellName + ". Press OK when done.");
            }
            
            // Rename new ROIs
            int cellCount = 0;
            for (int cell = roiStart; cell < rm.getCount(); cell++) {
                cellCount++;
                rm.select(cell);
                rm.rename(cell, cellName + "_" + cellCount);
            }
            roiStart = rm.getCount();
        }

        // Deselect all ROIs
        rm.deselect();
    }
    
    // --- StarDist Segmentation Logic ---
    private void runStarDistSegmentation(ImagePlus img, RoiManager rm) {

        // StarDist Parameter Dialog 
        GenericDialog gdStar = new GenericDialog("StarDist Segmentation Options");
        String[] modelChoices = new String[]{"GAT Model", "Stardist_Barth"};
        gdStar.addChoice("Choose StarDist model", modelChoices, modelChoices[0]);
        gdStar.addNumericField("Rescaling Factor", scale, 3, 8, "");
        gdStar.addSlider("Probability threshold", 0, 1, probability);
        gdStar.addSlider("Overlap threshold", 0, 1, overlap);
        gdStar.addNumericField("Minimum cell area", neuronSegLowerLimit, 0);
        gdStar.showDialog();
        
        if (gdStar.wasCanceled()) return;

        String choice = gdStar.getNextChoice();
        scale = gdStar.getNextNumber();
        probability = gdStar.getNextNumber();
        overlap = gdStar.getNextNumber();
        neuronSegLowerLimit = gdStar.getNextNumber();

        //Determine model path 
        String fijiDir = IJ.getDirectory("imagej");
        String modelsDir = fijiDir + "models" + File.separator;
        String modelFile = choice.equals("GAT Model") ? 
            modelsDir + "2D_enteric_neuron_v4_1.zip" : 
            modelsDir + "Barth_2D_StarDist.zip";

        File mf = new File(modelFile);
        if (!mf.exists()) {
            IJ.error("StarDist Model not found", "Cannot find model file: " + modelFile);
            return;
        }

        double targetPixelSize = STAR_DIST_TRAINING_PX_SIZE / scale;
        double scaleFactor = pixelWidth / targetPixelSize;

        //Run StarDist 
        String args = String.format(
            "input='%s', modelChoice='Model (.zip) from File', normalizeInput='true', percentileBottom='1.0', " +
            "percentileTop='99.8', probThresh='%.3f', nmsThresh='%.3f', outputType='Both', modelFile='%s', " +
            "nTiles='%d', excludeBoundary='2', roiPosition='Automatic', verbose='false', showCsbdeepProgress='false', showProbAndDist='false'",
            img.getTitle(), probability, overlap, modelFile.replace("\\", "/"), nTiles
        );

        IJ.run("Command From Macro", "command=[de.csbdresden.stardist.StarDist2D],args=[" + args + "], process=[false]");
        IJ.wait(100); // ensure the macro finishes

        // Check label image 
        ImagePlus labelImage = WindowManager.getImage("Label Image");
        if (labelImage == null) {
            IJ.error("StarDist Failed", "No Label Image produced. Check model and probability settings.");
            return;
        }

        // Remove border labels 
        IJ.run(labelImage, "Remove Border Labels", "left right top bottom");
        labelImage.setTitle("Label-killBorders");

        //  Rescale back if needed 
        ImagePlus finalLabel;
        if (Math.abs(scaleFactor - 1.0) > 0.001) {
            IJ.run(labelImage, "Scale...", "x=- y=- width=" + sizeX + " height=" + sizeY + " interpolation=None create title=label_original");
            finalLabel = WindowManager.getCurrentImage();
            labelImage.close();
        } else {
            finalLabel = labelImage;
            finalLabel.setTitle("label_original");
        }

        //Size filtering
        IJ.run(finalLabel, "Label Size Filtering", "operation=Greater_Than_Or_Equal size=" + neuronSegLowerLimit);
        ImagePlus labelFilter = WindowManager.getCurrentImage();
        labelFilter.getProcessor().resetMinAndMax();
        labelFilter.updateAndDraw();

        // Convert labels to ROIs
        IJ.run(labelFilter, "Label to ROI", "");
        labelFilter.close();

        rm.runCommand(calciumMax, "Show All"); // overlays all ROIs on the given image
        IJ.log("StarDist Segmentation completed: " + rm.getCount() + " ROIs detected.");
    }

    // --- Step 6: Measurement and Saving ---
    private void saveResultsAndCleanup() {

        if (calciumNormalise == null || rm.getCount() == 0) {
            IJ.log("No stack to measure or no ROIs. Skipping measurement and saving.");
            IJ.run("Close All", "");
            return;
        }

        calciumNormalise.show();
        IJ.run("Set Measurements...", "mean redirect=None decimal=2");

        // Multi-measure ROIs
        rm.runCommand(calciumNormalise, "Multi Measure");
        IJ.wait(50);

        // Save results as CSV
        String baseName = imageName.contains(".") ? imageName.substring(0, imageName.lastIndexOf('.')) : imageName;
        String resultsTitle = "Results";

        ImagePlus resultsImp = WindowManager.getImage(resultsTitle);
        if (resultsImp != null) {
            IJ.selectWindow(resultsImp.getTitle());
            IJ.saveAs(resultsImp, "Results", outputFolder + "RESULTS_" + baseName + ".csv");
            resultsImp.close();
        }

        // Save F/F0 stack if applicable
        if (useFF0 && calciumNormalise != originalStack) {
            IJ.log("Saving F/F0 stack");
            IJ.saveAs(calciumNormalise, "Tiff", outputFolder + "F_F0_" + imageName);
        }

        // Save ROIs 
        IJ.log("Saving ROIs");
        rm.runCommand("Save", outputFolder + "ROIS_" + imageName + "_CELLS.zip");

        //  Close all windows 
        IJ.run("Close All", "");
    }

    // --- Alternative Main for Standalone Testing ---
    public static void main(String[] args) {
        if (IJ.getInstance() == null) {
            new ImageJ();
        }
        new CalciumAnalysis().run("");
    }
}