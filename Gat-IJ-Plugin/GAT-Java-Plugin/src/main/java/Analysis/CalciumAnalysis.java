package Analysis;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.frame.RoiManager;
import ij.plugin.ZProjector;
import ij.plugin.ImageCalculator;
import java.io.File;
import java.awt.GridLayout;


import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import Features.Core.Params;

public class CalciumAnalysis {

    private final Params p;
    private ImagePlus rawStack;
    public ImagePlus maxProj;
    public ImagePlus normStack;
    private RoiManager rm;

    public CalciumAnalysis(Params params) {
        this.p = params;
    }

    // Step 1: Open the image
    public void openImage() {
        File imgFile = new File(p.imagePath);
        if (!imgFile.exists()) {
            IJ.error("File not found: " + p.imagePath);
            return;
        }
        rawStack = IJ.openImage(p.imagePath);
        this.maxProj = rawStack;
        rawStack.show();
        IJ.selectWindow(rawStack.getTitle());
        IJ.log("Step 1: Image opened. Inspect for blank frames if needed.");
    }

    // Step 2: Max intensity projection
    public void createMaxProjection() {
        if (maxProj.getStackSize() <= 1) {
            IJ.showMessage("Error", "Image must be a stack with multiple slices for Max Projection.");
            return;
        }
        int[] frames = promptForFrames(maxProj.getStackSize());
        if (frames == null) return; // cancelled

        int start = frames[0];
        int end = frames[1];
        IJ.run(rawStack, "Z Project...", "start=" + start + " stop=" + end + " projection=[Max Intensity]");
        maxProj = IJ.getImage();
        maxProj.setTitle("MAX_" + new File(p.imagePath).getName());
        maxProj.show();
        IJ.log("Step 2: Max projection created.");
    }

    // Step 3: F/F0 normalization
    public void normalizeStack() {
        if (!p.useFF0) {
            normStack = rawStack;
            return;
        }

        int[] frames = promptForBaseline(rawStack.getStackSize());
        if (frames == null) return; // cancelled

        int start = frames[0];
        int end = frames[1];
        IJ.run(rawStack, "Z Project...", "start=" + start + " stop=" + end + " projection=[Average Intensity]");
        ImagePlus f0 = IJ.getImage();
        IJ.run("Image Calculator...", "image1=[" + rawStack.getTitle() + "] operation=Divide image2=[" + f0.getTitle() + "] create 32-bit stack");
        normStack = IJ.getImage();
        normStack.setTitle("F_F0_" + new File(p.imagePath).getName());
        normStack.show();
        IJ.log("Step 3: F/F0 normalization complete.");
    }

    // Step 4: Setup ROI Manager
    public void setupROIManager() {
        rm = RoiManager.getInstance();
        if (rm == null) rm = new RoiManager();
        rm.reset();
        IJ.log("Step 4: ROI Manager initialized.");
    }

    // Step 5: Draw or import ROIs
    public void handleCellType(int i) {
        String cellName = (p.cellNames != null && p.cellNames.size() > i)
                ? p.cellNames.get(i)
                : "CellType" + (i + 1);

        boolean imported = false;

        // --- 1. Try importing ROIs if a path is provided ---
        if (p.roiPath != null && !p.roiPath.isEmpty()) {
            File roiFile = new File(p.roiPath);
            if (roiFile.exists()) {
                try {
                    rm.runCommand("Open", roiFile.getAbsolutePath());
                    IJ.showMessage("ROIs Imported",
                            "Successfully imported ROIs from:\n" + roiFile.getName());
                    imported = true;
                } catch (Exception ex) {
                    IJ.showMessage("Error", "Failed to import ROIs:\n" + ex.getMessage());
                }
            } else {
                IJ.showMessage("ROI File Missing",
                        "The specified ROI file does not exist:\n" + roiFile.getAbsolutePath());
            }
        }

        // --- 2. If not imported, optionally use StarDist or manual drawing ---
        if (!imported) {
            if (p.useStarDist) {
                try {
                    runStarDist(maxProj, new File(p.imagePath).getParentFile());
                    IJ.showMessage("StarDist Segmentation Completed",
                            "ROIs generated automatically for " + cellName + ".");
                    imported = true;
                } catch (Exception ex) {
                    IJ.showMessage("StarDist Error",
                            "StarDist segmentation failed: " + ex.getMessage());
                }
            }
        }

        // --- 3. If still no ROIs, prompt user to draw manually ---
        if (!imported) {
            IJ.selectWindow(maxProj.getTitle());
            IJ.setTool("oval");
            IJ.showMessage("Draw ROIs",
                    "No ROI file provided.\nPlease manually draw ROIs for " + cellName +
                    " using the Oval or Freehand tools.\n.");
        }
    }

    // Step 6: Rename ROIs
    public void renameROIs() {
        int roiCount = rm.getCount();
        for (int r = 0; r < roiCount; r++) {
            String name = (p.cellNames != null && !p.cellNames.isEmpty())
                    ? p.cellNames.get(r % p.cellNames.size()) + "_" + (r + 1)
                    : "Cell_" + (r + 1);
            IJ.runMacro("roiManager(\"Select\", " + r + ");");
            IJ.runMacro("roiManager(\"Rename\", \"" + name + "\");");
        }
    }

    // Step 7: Measure ROIs
    public void measureROIs() {
        IJ.selectWindow(normStack.getTitle());
        IJ.run("Set Measurements...", "mean redirect=None decimal=2");
        rm.runCommand("Multi Measure");
        IJ.wait(50);
    }

    // Step 8: Save results
    public File saveResults() {
        File imgFile = new File(p.imagePath);
        File resultsDir = new File(imgFile.getParentFile(),
                "RESULTS" + File.separator + imgFile.getName().replace(".tif", ""));
        if (!resultsDir.exists()) resultsDir.mkdirs();

        File csvFile = new File(resultsDir, "RESULTS_" + imgFile.getName() + ".csv");
        IJ.saveAs("Results", csvFile.getAbsolutePath());

        if (p.useFF0) {
            IJ.selectWindow(normStack.getTitle());
            IJ.run("Select None");
            rm.deselect();
            IJ.saveAs("Tiff", new File(resultsDir, normStack.getTitle() + ".tif").getAbsolutePath());
            IJ.run("Close");
        }

        rm.deselect();
        rm.runCommand("Save", new File(resultsDir, "ROIS_" + imgFile.getName() + "_CELLS.zip").getAbsolutePath());
        IJ.log("Step 8: All results saved in " + resultsDir.getAbsolutePath());

        return csvFile;
    }

    private void runStarDist(ImagePlus img, File resultsDir) {
        if (img == null) {
            IJ.showMessage("Error", "No image available for StarDist segmentation.");
            return;
        }

        if (1 == 1) {
            IJ.showMessage("Error", "Stardist is currently broken.");
            return;
        }

        String fijiDir = IJ.getDirectory("imagej");
        String modelsDir = fijiDir + "models" + File.separator;
        String modelFile = modelsDir + "2D_enteric_neuron_v4_1.zip";

        double probability = 0.5;
        double overlap = 0.3;
        int nTiles = 4;

        IJ.log("Running StarDist 2D on " + img.getTitle());

        // Prepare escaped path (Windows safe)
        modelFile = modelFile.replace("\\", "\\\\\\\\");

        // Run the StarDist command
        IJ.run("Command From Macro",
            "command=[de.csbdresden.stardist.StarDist2D], " +
            "args=['input':'" + img.getTitle() +
            "', 'modelChoice':'Model (.zip) from File', " +
            "'normalizeInput':'true', " +
            "'percentileBottom':'1.0', 'percentileTop':'99.8', " +
            "'probThresh':'" + probability + "', " +
            "'nmsThresh':'" + overlap + "', " +
            "'outputType':'Both', " +
            "'modelFile':'" + modelFile + "', " +
            "'nTiles':'" + nTiles + "', " +
            "'excludeBoundary':'2', " +
            "'roiPosition':'Automatic', " +
            "'verbose':'false', " +
            "'showCsbdeepProgress':'false', " +
            "'showProbAndDist':'false'], " +
            "process=[false]"
        );

        // Basic cleanup and ROI preparation
        IJ.run("Remove Overlay");
        IJ.run("Remove Border Labels", "left right top bottom");

        IJ.log("StarDist segmentation completed. ROIs can now be measured.");
    }

    private int[] promptForFrames(int stackSize) {
        JPanel panel = new JPanel(new GridLayout(2, 2, 4, 4));

        SpinnerNumberModel startModel = new SpinnerNumberModel(1, 1, stackSize, 1);
        SpinnerNumberModel endModel = new SpinnerNumberModel(stackSize, 1, stackSize, 1);

        JSpinner startSpinner = new JSpinner(startModel);
        JSpinner endSpinner = new JSpinner(endModel);

        panel.add(new JLabel("Start frame:"));
        panel.add(startSpinner);
        panel.add(new JLabel("End frame:"));
        panel.add(endSpinner);

        int option = JOptionPane.showConfirmDialog(
                null, panel, "Select Max Projection Frames", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (option == JOptionPane.OK_OPTION) {
            int start = (Integer) startSpinner.getValue();
            int end = (Integer) endSpinner.getValue();
            if (start > end) {
                IJ.showMessage("Error", "Start frame must be ≤ end frame.");
                return null;
            }
            return new int[]{start, end};
        }
        return null; // user cancelled
    }

    private int[] promptForBaseline(int stackSize) {
        JPanel panel = new JPanel(new GridLayout(2, 2, 4, 4));

        SpinnerNumberModel startModel = new SpinnerNumberModel(1, 1, stackSize, 1);
        SpinnerNumberModel endModel = new SpinnerNumberModel(stackSize, 1, stackSize, 1);

        JSpinner startSpinner = new JSpinner(startModel);
        JSpinner endSpinner = new JSpinner(endModel);

        panel.add(new JLabel("Start frame:"));
        panel.add(startSpinner);
        panel.add(new JLabel("End frame:"));
        panel.add(endSpinner);

        int option = JOptionPane.showConfirmDialog(
                null, panel, "Select Baseline Frames", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (option == JOptionPane.OK_OPTION) {
            int start = (Integer) startSpinner.getValue();
            int end = (Integer) endSpinner.getValue();
            if (start > end) {
                IJ.showMessage("Error", "Start frame must be ≤ end frame.");
                return null;
            }
            return new int[]{start, end};
        }
        return null; // user cancelled
    }
}
