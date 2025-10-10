package Analysis;

import ij.*;
import ij.io.Opener;
import ij.plugin.frame.RoiManager;

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

        // Process cell ROIs
        roiManager.runCommand("Open", roiPath);
        ConvertROIToLabels.execute();
        IJ.getImage().setTitle("Cell_labels");
        Thread.sleep(10);
        IJ.run("Select None");
        String labelCellImg = IJ.getImage().getTitle();

        // Run spatial analysis using Java class
        SpatialSingleCellType.execute(cellType, labelCellImg, gangliaBinary, savePath,
                labelDilation, saveParametricImage, pixelWidth, roiPath);

        Thread.sleep(5);
        IJ.log("Files saved at " + savePath);

        // Close all images
        IJ.run("Close All");

        // Close all ImageJ windows
        closeImageJWindows();

        IJ.log("Neighbour Analysis complete");
    }

    private void closeImageJWindows() {
        // Close ROI Manager
        RoiManager roiManager = RoiManager.getInstance();
        if (roiManager != null) {
            roiManager.close();
        }

        // Close Results window
        IJ.run("Clear Results");
        ij.measure.ResultsTable rt = ij.measure.ResultsTable.getResultsTable();
        if (rt != null) {
            rt.reset();
        }

        // Close Log window
        ij.text.TextWindow logWindow = (ij.text.TextWindow) WindowManager.getWindow("Log");
        if (logWindow != null) {
            logWindow.close();
        }

        // Close Console if it exists
        ij.text.TextWindow consoleWindow = (ij.text.TextWindow) WindowManager.getWindow("Console");
        if (consoleWindow != null) {
            consoleWindow.close();
        }
    }
}
