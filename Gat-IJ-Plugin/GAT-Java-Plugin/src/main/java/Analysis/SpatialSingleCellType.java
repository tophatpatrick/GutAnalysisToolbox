package Analysis;

import ij.*;
import ij.measure.ResultsTable;
import ij.plugin.frame.RoiManager;
import java.io.File;

public class SpatialSingleCellType {

    public static void execute(String cellType1, String cell1, String gangliaBinaryOrig,
                               String savePath, double labelDilation, boolean saveParametricImage,
                               double pixelWidth, String roiLocationCell) {

        IJ.run("Clear Results");

        // Check if CLIJ is installed
        if (!isClij2Available()) {
            IJ.error("CLIJ not installed. Check Log for installation details");
            IJ.log("CLIJ installation link: Please install CLIJ using from: https://clij.github.io/clij2-docs/installationInFiji");
            return;
        }

        RoiManager roiManager = RoiManager.getInstance();
        if (roiManager == null) {
            roiManager = new RoiManager();
        }
        roiManager.reset();

        IJ.run("CLIJ2 Macro Extensions", "cl_device=");

        IJ.log("Getting number of neighbours for " + cellType1);

        // Convert to pixels
        int labelDilationPixels = (int) Math.round(labelDilation / pixelWidth);

        String fs = File.separator;
        savePath = savePath + fs + "spatial_analysis" + fs;
        File saveDir = new File(savePath);
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }

        // Binary image for ganglia
        IJ.run("Options...", "iterations=1 count=1 black");
        String gangliaBinary = "NA";

        if (gangliaBinaryOrig != null && !gangliaBinaryOrig.equals("NA")) {
            ImagePlus gangliaImg = WindowManager.getImage(gangliaBinaryOrig);
            if (gangliaImg != null) {
                IJ.run(gangliaImg, "Select None", "");
                IJ.run(gangliaImg, "Duplicate...", "title=ganglia_binary_dup");
                ImagePlus gangliaDup = WindowManager.getImage("ganglia_binary_dup");
                if (gangliaDup != null) {
                    IJ.setThreshold(gangliaDup, 0.5, 65535);
                    IJ.run(gangliaDup, "Convert to Mask", "");
                    gangliaDup.setTitle("Ganglia_outline");
                    gangliaBinary = gangliaDup.getTitle();
                    IJ.run(gangliaDup, "Divide...", "value=255");
                    gangliaDup.resetDisplayRange();
                    IJ.run(gangliaDup, "Set...", "value=0 value=1");
                }
            }
        }

        double[] neighbourCount = nearestNeighbourSingleCell(cell1, labelDilationPixels, gangliaBinary, cellType1);

        // Remove background (index 0)
        double[] neighbourCountNoBackground = new double[neighbourCount.length - 1];
        System.arraycopy(neighbourCount, 1, neighbourCountNoBackground, 0, neighbourCount.length - 1);

        String tableName = "Neighbour_count_" + cellType1;
        String tablePath = savePath + fs + tableName + ".csv";

        // Load ROIs and get cell names
        roiManager.runCommand("Open", roiLocationCell);
        ImagePlus cellImage = WindowManager.getImage(cell1);
        if (cellImage != null) {
            IJ.run("Set Measurements...", "centroid display redirect=None decimal=3");
            roiManager.deselect();
            roiManager.runCommand("Measure");
            renameRoiNameResultTable();

            ResultsTable rt = ResultsTable.getResultsTable();
            String[] cellNames = new String[rt.getCounter()];
            for (int i = 0; i < rt.getCounter(); i++) {
                cellNames[i] = rt.getLabel(i);
            }
            IJ.run("Clear Results");

            // Create and save results table
            ResultsTable neighbourTable = new ResultsTable();
            for (int i = 0; i < cellNames.length && i < neighbourCountNoBackground.length; i++) {
                neighbourTable.incrementCounter();
                neighbourTable.addLabel(cellNames[i]);
                neighbourTable.addValue("Neuron_id", cellNames[i]);
                neighbourTable.addValue("No of cells around " + cellType1, neighbourCountNoBackground[i]);
            }

            try {
                neighbourTable.save(tablePath);
            } catch (Exception e) {
                IJ.error("Could not save table: " + e.getMessage());
            }
        }

        if (saveParametricImage) {
            String neighbourCell1 = getParametricImg(neighbourCount, cell1, cellType1);
            ImagePlus paramImg = WindowManager.getImage(neighbourCell1);
            if (paramImg != null) {
                IJ.saveAsTiff(paramImg, savePath + fs + neighbourCell1);
                paramImg.close();
            }
        }

        if (!gangliaBinary.equals("NA")) {
            ImagePlus gangliaImg = WindowManager.getImage(gangliaBinary);
            if (gangliaImg != null) {
                gangliaImg.close();
            }
        }

        IJ.log("Spatial analysis done for " + cellType1);
        roiManager.reset();
    }

    private static double[] nearestNeighbourSingleCell(String refImg, int dilateRadius, String gangliaBinary, String cellType1) {
        IJ.run("Clear Results");
        IJ.run("CLIJ2 Macro Extensions", "cl_device=");

        // Push image to GPU
        IJ.run("CLIJ2 push [" + refImg + "]");

        // Dilate labels
        IJ.run("CLIJ2 dilateLabels", "input=" + refImg + " destination=ref_dilate radius=" + dilateRadius);

        if (!gangliaBinary.equals("NA")) {
            IJ.run("CLIJ2 push [" + gangliaBinary + "]");
            IJ.run("CLIJ2 multiplyImages", "factor1=ref_dilate factor2=" + gangliaBinary + " destination=ref_dilate_ganglia_restrict");
            IJ.run("CLIJ2 touchingNeighborCountMap", "input=ref_dilate_ganglia_restrict destination=ref_neighbour_count");
            IJ.run("CLIJ2 release [" + gangliaBinary + "]");
        } else {
            IJ.run("CLIJ2 touchingNeighborCountMap", "input=ref_dilate destination=ref_neighbour_count");
        }

        // Reduce labels to centroids
        IJ.run("CLIJ2 reduceLabelsToCentroids", "input=" + refImg + " destination=ref_img_centroid");
        IJ.run("CLIJ2 statisticsOfBackgroundAndLabelledPixels", "input=ref_neighbour_count labelmap=ref_img_centroid");

        ResultsTable rt = ResultsTable.getResultsTable();
        double[] noNeighbours = new double[rt.getCounter()];

        for (int i = 0; i < rt.getCounter(); i++) {
            noNeighbours[i] = rt.getValue("MINIMUM_INTENSITY", i);
        }

        IJ.run("Clear Results");

        if (noNeighbours.length > 0) {
            noNeighbours[0] = 0;
        }

        // Release GPU memory
        IJ.run("CLIJ2 release [ref_neighbour_count]");
        IJ.run("CLIJ2 release [ref_img_centroid]");

        return noNeighbours;
    }

    private static String getParametricImg(double[] noNeighbours, String cellLabelImg, String cellType1) {
        IJ.run("CLIJ2 Macro Extensions", "cl_device=");

        // Convert double array to string for macro
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < noNeighbours.length; i++) {
            sb.append(noNeighbours[i]);
            if (i < noNeighbours.length - 1) {
                sb.append(",");
            }
        }

        IJ.run("CLIJ2 pushArray2D", "array=[" + sb.toString() + "] width=" + noNeighbours.length + " height=1 destination=vector_neighbours");
        IJ.run("CLIJ2 replaceIntensities", "input=" + cellLabelImg + " new_values_vector=vector_neighbours destination=parametric_img");
        IJ.run("CLIJ2 pull [parametric_img]");

        String newName = cellType1 + "_neighbours";
        ImagePlus paramImg = WindowManager.getImage("parametric_img");
        if (paramImg != null) {
            paramImg.setTitle(newName);
            IJ.run(paramImg, "Fire", "");
        }

        return newName;
    }

    private static void renameRoiNameResultTable() {
        ResultsTable rt = ResultsTable.getResultsTable();
        if (rt.getCounter() == 0) {
            IJ.log("No rois in results table for spatial analysis");
            return;
        }

        for (int i = 0; i < rt.getCounter(); i++) {
            String oldLabel = rt.getLabel(i);
            if (oldLabel != null) {
                int delimiter = oldLabel.indexOf(":");
                if (delimiter > -1) {
                    String newLabel = oldLabel.substring(delimiter + 1);
                    rt.setLabel(newLabel, i);
                }
            }
        }
    }

    private static boolean isClij2Available() {
        // Check if CLIJ2 is available
        try {
            IJ.run("CLIJ2 Macro Extensions", "cl_device=");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

