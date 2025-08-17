package Analysis;

import ij.*;
import ij.io.Opener;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import java.io.File;

public class SingleCellTypeAnalysis {

    private String maxProjPath;
    private String roiPath;
    private String roiGangliaPath;
    private String savePath;
    private String cellType;
    private double labelDilation;
    private boolean saveParametricImage;

    public SingleCellTypeAnalysis(String maxProjPath, String roiPath, String roiGangliaPath,
                                  String savePath, String cellType, double labelDilation,
                                  boolean saveParametricImage) {
        this.maxProjPath = maxProjPath;
        this.roiPath = roiPath;
        this.roiGangliaPath = roiGangliaPath;
        this.savePath = savePath;
        this.cellType = cellType;
        this.labelDilation = labelDilation;
        this.saveParametricImage = saveParametricImage;
    }

    public void execute() throws Exception {
        // Clear previous results
        IJ.run("Clear Results");
        IJ.log("\\Clear");
        IJ.run("Close All");

        String fs = File.separator;
        String fijiDir = IJ.getDirectory("imagej");

        // Remove unwanted segments from path
        String unwanted = "Gat-IJ-Plugin" + fs + "GAT-Java-Plugin" + fs + "target" + fs;
        if (fijiDir.contains(unwanted)) {
            fijiDir = fijiDir.replace(unwanted, "");
        }

        String gatDir = fijiDir + fs + "Tools" + fs + "commands";

//        String fs = File.separator;
//        String fijiDir = IJ.getDirectory("imagej");
//        String gatDir = fijiDir + fs + "Tools" + fs + "commands";
//        String gatDir = fijiDir + "scripts" + fs + "GAT" + fs + "Tools" + fs + "commands";

        // Check if required macro files exist
        String labelToRoiPath = gatDir + fs + "Convert_Label_to_ROIs.ijm";
        if (!new File(labelToRoiPath).exists()) {
            throw new Exception("Cannot find label to roi script. Path: " + labelToRoiPath);
        }

        String roiToLabelPath = gatDir + fs + "Convert_ROI_to_Labels.ijm";
        if (!new File(roiToLabelPath).exists()) {
            throw new Exception("Cannot find roi to label script. Path: " + roiToLabelPath);
        }

        String spatialSingleCellTypePath = gatDir + fs + "spatial_single_celltype.ijm";
        if (!new File(spatialSingleCellTypePath).exists()) {
            throw new Exception("Cannot find single cell spatial analysis script. Path: " + spatialSingleCellTypePath);
        }

        // Open the maximum projection image
        Opener opener = new Opener();
        ImagePlus maxProjImage = opener.openImage(maxProjPath);
        if (maxProjImage == null) {
            throw new Exception("Could not open maximum projection image: " + maxProjPath);
        }
        maxProjImage.show();

        // Get file name and restrict length if necessary
        String fileName = new File(maxProjPath).getName();
        fileName = fileName.substring(0, fileName.lastIndexOf('.'));
        if (fileName.length() > 50) {
            fileName = fileName.substring(0, 39);
        }

        // Get pixel size
        double pixelWidth = maxProjImage.getCalibration().pixelWidth;
        String unit = maxProjImage.getCalibration().getUnit();
        if (!unit.equals("microns")) {
            IJ.log("Image is not calibrated in microns. Output may be in pixels");
        }

        // Reset ROI Manager
        RoiManager roiManager = RoiManager.getInstance();
        if (roiManager == null) {
            roiManager = new RoiManager();
        }
        roiManager.reset();

        // Process ganglia ROI if provided
        String gangliaBinary = "";
        IJ.run("Options...", "iterations=1 count=1 black");

        if (roiGangliaPath != null && !roiGangliaPath.equals("NA") && new File(roiGangliaPath).exists()) {
            roiManager.runCommand("Open", roiGangliaPath);

            // Convert ROIs to label map
            IJ.runMacro(new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get(roiToLabelPath))));

            Thread.sleep(10);
            IJ.run("Select None");
            IJ.run("Remove Overlay");

            // Create binary mask for ganglia
            IJ.setThreshold(0.5, 65535);
            IJ.run("Convert to Mask");
            IJ.getImage().setTitle("Ganglia_outline");
            gangliaBinary = IJ.getImage().getTitle();
            IJ.run("Divide...", "value=255");
            IJ.setMinAndMax(0, 1);

            roiManager.reset();
        } else {
            gangliaBinary = "NA";
        }

        // Process cell ROIs
        roiManager.runCommand("Open", roiPath);
        IJ.runMacro(new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(roiToLabelPath))));
        IJ.getImage().setTitle("Cell_labels");
        Thread.sleep(10);
        IJ.run("Select None");
        String labelCellImg = IJ.getImage().getTitle();

        // Run spatial analysis macro
        String args = cellType + "," + labelCellImg + "," + gangliaBinary + "," +
                savePath + "," + labelDilation + "," + saveParametricImage + "," +
                pixelWidth + "," + roiPath;

        IJ.runMacro(new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(spatialSingleCellTypePath))), args);

        Thread.sleep(5);
        IJ.log("Files saved at " + savePath);

        // Close all images
        IJ.run("Close All");

        IJ.log("Neighbour Analysis complete");
    }
}
