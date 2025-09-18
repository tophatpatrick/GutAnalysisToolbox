package Analysis;

import ij.*;
import ij.io.Opener;
import ij.plugin.frame.RoiManager;

import java.io.File;

public class TwoCellTypeAnalysis {

    private String maxProjPath;
    private String cellType1;
    private String roi1Path;
    private String cellType2;
    private String roi2Path;
    private String roiGangliaPath;
    private String savePath;
    private double labelDilation;
    private boolean saveParametricImage;

    public TwoCellTypeAnalysis(String maxProjPath, String cellType1, String roi1Path,
                               String cellType2, String roi2Path, String roiGangliaPath,
                               String savePath, double labelDilation, boolean saveParametricImage) {
        this.maxProjPath = maxProjPath;
        this.cellType1 = cellType1;
        this.roi1Path = roi1Path;
        this.cellType2 = cellType2;
        this.roi2Path = roi2Path;
        this.roiGangliaPath = roiGangliaPath;
        this.savePath = savePath;
        this.labelDilation = labelDilation;
        this.saveParametricImage = saveParametricImage;
    }

    public void execute() throws Exception {
        // Clear previous results
        IJ.run("Clear Results");
        IJ.log("\\Clear");
        IJ.run("Close All");

        // Validate cell type names are different
        if (cellType1.equals(cellType2)) {
            throw new Exception("Cell names are the same for both celltypes");
        }

        // Open the maximum projection image
        Opener opener = new Opener();
        ImagePlus maxProjImage = opener.openImage(maxProjPath);
        if (maxProjImage == null) {
            throw new Exception("Could not open maximum projection image: " + maxProjPath);
        }
        maxProjImage.show();

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

            // Convert ROIs to label map using Java class
            ConvertROIToLabels.execute();

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

        // Process cell 1 ROIs
        roiManager.runCommand("Open", roi1Path);
        ConvertROIToLabels.execute();
        IJ.getImage().setTitle(cellType1 + "_labels");
        Thread.sleep(10);
        IJ.run("Select None");
        String labelCell1Img = IJ.getImage().getTitle();

        roiManager.reset();

        // Process cell 2 ROIs
        roiManager.runCommand("Open", roi2Path);
        ConvertROIToLabels.execute();
        IJ.getImage().setTitle(cellType2 + "_labels");
        Thread.sleep(10);
        IJ.run("Select None");
        String labelCell2Img = IJ.getImage().getTitle();

        // Run spatial analysis using Java class
        SpatialTwoCellType.execute(cellType1, labelCell1Img, cellType2, labelCell2Img,
                gangliaBinary, savePath, labelDilation, saveParametricImage,
                pixelWidth, roi1Path, roi2Path);

        Thread.sleep(5);
        IJ.log("Files saved at " + savePath);

        // Close all images
        IJ.run("Close All");

        IJ.log("Two celltype neighbour analysis complete");
    }
}

//package Analysis;
//
//import ij.*;
//import ij.io.Opener;
//import ij.plugin.frame.RoiManager;
//
//import java.io.File;
//
//public class TwoCellTypeAnalysis {
//
//    private String maxProjPath;
//    private String cellType1;
//    private String roiPath1;
//    private String cellType2;
//    private String roiPath2;
//    private String savePath;
//    private String roiGangliaPath;
//    private double labelDilation;
//    private boolean saveParametricImage;
//
//    public TwoCellTypeAnalysis(String maxProjPath, String cellType1, String roiPath1,
//                               String cellType2, String roiPath2, String savePath,
//                               String roiGangliaPath, double labelDilation,
//                               boolean saveParametricImage) {
//        this.maxProjPath = maxProjPath;
//        this.cellType1 = cellType1;
//        this.roiPath1 = roiPath1;
//        this.cellType2 = cellType2;
//        this.roiPath2 = roiPath2;
//        this.savePath = savePath;
//        this.roiGangliaPath = roiGangliaPath;
//        this.labelDilation = labelDilation;
//        this.saveParametricImage = saveParametricImage;
//    }
//
//    public void execute() throws Exception {
//        // Clear previous results
//        IJ.run("Clear Results");
//        IJ.log("\\Clear");
//        IJ.run("Close All");
//
//        // Reset ROI Manager
//        RoiManager roiManager = RoiManager.getInstance();
//        if (roiManager == null) {
//            roiManager = new RoiManager();
//        }
//        roiManager.reset();
//
//        // Validate inputs
//        if (cellType1.equals(cellType2) || roiPath1.equals(roiPath2)) {
//            throw new Exception("Cell names or ROI managers are the same for both celltypes");
//        }
//
//        // Validate file paths exist
//        if (!new File(maxProjPath).exists()) {
//            throw new Exception("Maximum projection image not found: " + maxProjPath);
//        }
//        if (!new File(roiPath1).exists()) {
//            throw new Exception("ROI file 1 not found: " + roiPath1);
//        }
//        if (!new File(roiPath2).exists()) {
//            throw new Exception("ROI file 2 not found: " + roiPath2);
//        }
//
//        // Validate and create save directory
//        File saveDir = new File(savePath);
//        if (!saveDir.exists()) {
//            if (!saveDir.mkdirs()) {
//                throw new Exception("Could not create save directory: " + savePath);
//            }
//        }
//        if (!saveDir.canWrite()) {
//            throw new Exception("No write permission for save directory: " + savePath);
//        }
//
//        // Open the maximum projection image
//        Opener opener = new Opener();
//        ImagePlus maxProjImage = opener.openImage(maxProjPath);
//        if (maxProjImage == null) {
//            throw new Exception("Could not open maximum projection image: " + maxProjPath);
//        }
//        maxProjImage.show();
//
//        // Get file name and restrict length if necessary
//        String fileName = new File(maxProjPath).getName();
//        fileName = fileName.substring(0, fileName.lastIndexOf('.'));
//        if (fileName.length() > 50) {
//            fileName = fileName.substring(0, 39);
//        }
//
//        // Get pixel size
//        double pixelWidth = maxProjImage.getCalibration().pixelWidth;
//        String unit = maxProjImage.getCalibration().getUnit();
//        if (!unit.equals("microns")) {
//            IJ.log("Image is not calibrated in microns. Output may be in pixels");
//        }
//
//        // Process ganglia ROI if provided
//        String gangliaBinary = "NA";
//        IJ.run("Options...", "iterations=1 count=1 black");
//
//        if (roiGangliaPath != null && !roiGangliaPath.equals("NA") && new File(roiGangliaPath).exists()) {
//            try {
//                roiManager.runCommand("Open", roiGangliaPath);
//                ConvertROIToLabels.execute();
//                Thread.sleep(10);
//                IJ.run("Select None");
//                IJ.run("Remove Overlay");
//
//                // Create binary mask for ganglia
//                IJ.setThreshold(0.5, 65535);
//                IJ.run("Convert to Mask");
//                IJ.getImage().setTitle("Ganglia_outline");
//                gangliaBinary = IJ.getImage().getTitle();
//                IJ.run("Divide...", "value=255");
//                IJ.setMinAndMax(0, 1);
//
//                roiManager.reset();
//            } catch (Exception e) {
//                IJ.log("Warning: Could not process ganglia ROIs: " + e.getMessage());
//                gangliaBinary = "NA";
//            }
//        }
//
//        // Process cell type 1 ROIs
//        try {
//            roiManager.runCommand("Open", roiPath1);
//            ConvertROIToLabels.execute();
//            Thread.sleep(10);
//            IJ.getImage().setTitle(cellType1);
//            String cell1 = IJ.getImage().getTitle();
//            roiManager.runCommand("Show None");
//            IJ.run("Select None");
//            IJ.run("Remove Overlay");
//            roiManager.reset();
//
//            // Process cell type 2 ROIs
//            roiManager.runCommand("Open", roiPath2);
//            ConvertROIToLabels.execute();
//            Thread.sleep(10);
//            IJ.getImage().setTitle(cellType2);
//            String cell2 = IJ.getImage().getTitle();
//            roiManager.runCommand("Show None");
//            roiManager.reset();
//            IJ.run("Select None");
//            IJ.run("Remove Overlay");
//
////            // Run appropriate spatial analysis based on pan-neuronal setting
////            if (assignAsPanNeuronal) {
////                IJ.log("Pan-neuronal spatial analysis enabled");
////
////                String huName, huLabelImg, otherCellType, otherCell;
////
////                if (panNeuronalChoice.equals("Cell 1")) {
////                    huName = cellType1;
////                    huLabelImg = cell1;
////                    otherCellType = cellType2;
////                    otherCell = cell2;
////                } else {
////                    huName = cellType2;
////                    huLabelImg = cell2;
////                    otherCellType = cellType1;
////                    otherCell = cell1;
////                }
////
////                SpatialHuMarker.execute(huName, huLabelImg, otherCellType, otherCell,
////                        gangliaBinary, savePath, labelDilation, saveParametricImage,
////                        pixelWidth, roiPath1, roiPath2);
////            } else {
////                IJ.log("Spatial analysis for two celltypes");
////                SpatialTwoCellType.execute(cellType1, cell1, cellType2, cell2,
////                        gangliaBinary, savePath, labelDilation, saveParametricImage,
////                        pixelWidth, roiPath1, roiPath2);
////            }
//
//            Thread.sleep(5);
//            IJ.log("Neighbour Analysis complete (Two celltypes)");
//
//        } catch (Exception e) {
//            throw new Exception("Error processing ROI files: " + e.getMessage());
//        }
//    }
//}






//package Analysis;
//
//import ij.*;
//import ij.io.Opener;
//import ij.plugin.frame.RoiManager;
//
//import java.io.File;
//
//public class TwoCellTypeAnalysis {
//
//    private String maxProjPath;
//    private String cellType1;
//    private String roiPath1;
//    private String cellType2;
//    private String roiPath2;
//    private String roiGangliaPath;
//    private String savePath;
//    private boolean assignAsPanNeuronal;
//    private String panNeuronalChoice;
//    private double labelDilation;
//    private boolean saveParametricImage;
//
//    public TwoCellTypeAnalysis(String maxProjPath, String cellType1, String roiPath1,
//                               String cellType2, String roiPath2, String roiGangliaPath,
//                               String savePath, boolean assignAsPanNeuronal,
//                               String panNeuronalChoice, double labelDilation,
//                               boolean saveParametricImage) {
//        this.maxProjPath = maxProjPath;
//        this.cellType1 = cellType1;
//        this.roiPath1 = roiPath1;
//        this.cellType2 = cellType2;
//        this.roiPath2 = roiPath2;
//        this.roiGangliaPath = roiGangliaPath;
//        this.savePath = savePath;
//        this.assignAsPanNeuronal = assignAsPanNeuronal;
//        this.panNeuronalChoice = panNeuronalChoice;
//        this.labelDilation = labelDilation;
//        this.saveParametricImage = saveParametricImage;
//    }
//
//    public void execute() throws Exception {
//        // Validate inputs
//        if (cellType1.equals(cellType2) || roiPath1.equals(roiPath2)) {
//            throw new Exception("Cell names or ROI managers are the same for both celltypes");
//        }
//
//        // Clear previous results
//        IJ.run("Clear Results");
//        IJ.log("\\Clear");
//        IJ.run("Close All");
//
//        String fs = File.separator;
//        String fijiDir = IJ.getDirectory("imagej");
//        String gatDir = fijiDir + fs + "Tools" + fs + "commands";
//
//        // Check if required macro files exist
//        String labelToRoiPath = gatDir + fs + "Convert_Label_to_ROIs.ijm";
//        if (!new File(labelToRoiPath).exists()) {
//            throw new Exception("Cannot find label to roi script. Path: " + labelToRoiPath);
//        }
//
//        String roiToLabelPath = gatDir + fs + "Convert_ROI_to_Labels.ijm";
//        if (!new File(roiToLabelPath).exists()) {
//            throw new Exception("Cannot find roi to label script. Path: " + roiToLabelPath);
//        }
//
//        String spatialTwoCellTypePath = gatDir + fs + "spatial_two_celltype.ijm";
//        if (!new File(spatialTwoCellTypePath).exists()) {
//            throw new Exception("Cannot find spatial analysis script. Path: " + spatialTwoCellTypePath);
//        }
//
//        String spatialHuMarkerPath = gatDir + fs + "spatial_hu_marker.ijm";
//        if (!new File(spatialHuMarkerPath).exists()) {
//            throw new Exception("Cannot find hu_marker spatial analysis script. Path: " + spatialHuMarkerPath);
//        }
//
//        // Open the maximum projection image
//        Opener opener = new Opener();
//        ImagePlus maxProjImage = opener.openImage(maxProjPath);
//        if (maxProjImage == null) {
//            throw new Exception("Could not open maximum projection image: " + maxProjPath);
//        }
//        maxProjImage.show();
//
//        // Get file name and restrict length if necessary
//        String fileName = new File(maxProjPath).getName();
//        fileName = fileName.substring(0, fileName.lastIndexOf('.'));
//        if (fileName.length() > 50) {
//            fileName = fileName.substring(0, 39);
//        }
//
//        // Get pixel size
//        double pixelWidth = maxProjImage.getCalibration().pixelWidth;
//        String unit = maxProjImage.getCalibration().getUnit();
//        if (!unit.equals("microns")) {
//            IJ.log("Image is not calibrated in microns. Output may be in pixels");
//        }
//
//        String img = maxProjImage.getTitle();
//
//        // Reset ROI Manager
//        RoiManager roiManager = RoiManager.getInstance();
//        if (roiManager == null) {
//            roiManager = new RoiManager();
//        }
//        roiManager.reset();
//
//        // Process ganglia ROI if provided
//        String gangliaBinary = "";
//        IJ.run("Options...", "iterations=1 count=1 black");
//
//        if (roiGangliaPath != null && !roiGangliaPath.equals("NA") && new File(roiGangliaPath).exists()) {
//            roiManager.runCommand("Open", roiGangliaPath);
//
//            // Convert ROIs to label map
//            IJ.runMacro(new String(java.nio.file.Files.readAllBytes(
//                    java.nio.file.Paths.get(roiToLabelPath))));
//
//            Thread.sleep(10);
//            IJ.run("Select None");
//            IJ.run("Remove Overlay");
//
//            // Create binary mask for ganglia
//            IJ.setThreshold(0.5, 65535);
//            IJ.run("Convert to Mask");
//            IJ.getImage().setTitle("Ganglia_outline");
//            gangliaBinary = IJ.getImage().getTitle();
//            IJ.run("Divide...", "value=255");
//            IJ.setMinAndMax(0, 1);
//
//            roiManager.reset();
//        } else {
//            gangliaBinary = "NA";
//        }
//
//        // Process cell type 1 ROIs
//        roiManager.reset();
//        roiManager.runCommand("Open", roiPath1);
//        IJ.runMacro(new String(java.nio.file.Files.readAllBytes(
//                java.nio.file.Paths.get(roiToLabelPath))));
//        Thread.sleep(10);
//        IJ.getImage().setTitle(cellType1);
//        String cell1 = IJ.getImage().getTitle();
//        roiManager.runCommand("Show None");
//        IJ.run("Select None");
//        IJ.run("Remove Overlay");
//        roiManager.reset();
//
//        // Process cell type 2 ROIs
//        roiManager.runCommand("Open", roiPath2);
//        IJ.runMacro(new String(java.nio.file.Files.readAllBytes(
//                java.nio.file.Paths.get(roiToLabelPath))));
//        Thread.sleep(10);
//        IJ.getImage().setTitle(cellType2);
//        String cell2 = IJ.getImage().getTitle();
//        roiManager.runCommand("Show None");
//        roiManager.reset();
//        IJ.run("Select None");
//        IJ.run("Remove Overlay");
//
//        // Run appropriate analysis based on pan-neuronal setting
//        if (assignAsPanNeuronal) {
//            IJ.log("Pan-neuronal spatial analysis enabled");
//
//            String huName, huLabelImg;
//            String adjustedCellType2, adjustedCell2;
//
//            if (panNeuronalChoice.equals("Cell 1")) {
//                huName = cellType1;
//                huLabelImg = cell1;
//                adjustedCellType2 = cellType2;
//                adjustedCell2 = cell2;
//            } else {
//                huName = cellType2;
//                huLabelImg = cell2;
//                adjustedCellType2 = cellType1;
//                adjustedCell2 = cell1;
//            }
//
//            String args = huName + "," + huLabelImg + "," + adjustedCellType2 + "," +
//                    adjustedCell2 + "," + gangliaBinary + "," + savePath + "," +
//                    labelDilation + "," + saveParametricImage + "," + pixelWidth + "," + roiPath1;
//
//            IJ.runMacro(new String(java.nio.file.Files.readAllBytes(
//                    java.nio.file.Paths.get(spatialHuMarkerPath))), args);
//            Thread.sleep(5);
//        } else {
//            IJ.log("Spatial analysis for two celltypes");
//
//            String args = cellType1 + "," + cell1 + "," + cellType2 + "," + cell2 + "," +
//                    gangliaBinary + "," + savePath + "," + labelDilation + "," +
//                    saveParametricImage + "," + pixelWidth + "," + roiPath1 + "," + roiPath2;
//
//            IJ.runMacro(new String(java.nio.file.Files.readAllBytes(
//                    java.nio.file.Paths.get(spatialTwoCellTypePath))), args);
//            Thread.sleep(5);
//        }
//
//        IJ.log("Neighbour Analysis complete (Two celltypes)");
//    }
//}
