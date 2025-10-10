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

        // Close all ImageJ windows
        closeImageJWindows();

        IJ.log("Two celltype neighbour analysis complete");
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