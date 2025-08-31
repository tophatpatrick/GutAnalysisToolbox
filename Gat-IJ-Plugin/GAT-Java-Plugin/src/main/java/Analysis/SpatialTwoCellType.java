package Analysis;

import ij.*;
import ij.measure.ResultsTable;
import ij.plugin.frame.RoiManager;
import java.io.File;

public class SpatialTwoCellType {

    public static void execute(String cellType1, String cell1, String cellType2, String cell2,
                               String gangliaBinaryOrig, String savePath, double labelDilation,
                               boolean saveParametricImage, double pixelWidth, String roiLocationCell1,
                               String roiLocationCell2) {

        IJ.run("Clear Results");

        // Check if CLIJ is installed
        if (!isClij2Available()) {
            IJ.error("CLIJ not installed. Check Log for installation details");
            IJ.log("CLIJ installation link: Please install CLIJ using from: https://clij.github.io/clij2-docs/installationInFiji");
            return;
        }

        if (cellType1.equals(cellType2)) {
            IJ.error("Cell names or ROI managers are the same for both celltypes");
            return;
        }

        RoiManager roiManager = RoiManager.getInstance();
        if (roiManager == null) {
            roiManager = new RoiManager();
        }
        roiManager.reset();

        IJ.run("CLIJ2 Macro Extensions", "cl_device=");

        IJ.log("Running spatial analysis on " + cellType1 + " and " + cellType2);

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

        // Cell 2 neighbors around cell 1
        double[] noNeighboursCell2Around1 = countNeighborAroundRefImg(cell1, cell2, labelDilationPixels, gangliaBinary);
        double[] countsCell2Around1 = removeBackgroundIndex(noNeighboursCell2Around1);

        // Cell 1 neighbors around cell 2
        double[] noNeighboursCell1Around2 = countNeighborAroundRefImg(cell2, cell1, labelDilationPixels, gangliaBinary);
        double[] countsCell1Around2 = removeBackgroundIndex(noNeighboursCell1Around2);

        // Clip values > 500 to zero
        countsCell2Around1 = clipArrayToZero(countsCell2Around1, 500);
        countsCell1Around2 = clipArrayToZero(countsCell1Around2, 500);

        roiManager.reset();
        IJ.run("Clear Results");

        String tableName = "Neighbour_count_" + cellType1 + "_" + cellType2;
        String tablePath = savePath + fs + tableName + ".csv";

        String[] cell1Names = getRoiLabels(roiLocationCell1, cell1);
        String[] cell2Names = getRoiLabels(roiLocationCell2, cell2);

        // Create and save results table
        ResultsTable neighbourTable = new ResultsTable();
        neighbourTable.setDefaultHeadings();

        // Add cell 1 data
        for (int i = 0; i < cell1Names.length && i < countsCell2Around1.length; i++) {
            neighbourTable.incrementCounter();
            neighbourTable.addLabel(cell1Names[i]);
            neighbourTable.addValue(cellType1 + "_id", cell1Names[i]);
            neighbourTable.addValue("No of " + cellType2 + " around " + cellType1, countsCell2Around1[i]);
        }

        // Add cell 2 data
        for (int i = 0; i < cell2Names.length && i < countsCell1Around2.length; i++) {
            if (i >= neighbourTable.getCounter()) {
                neighbourTable.incrementCounter();
            }
            neighbourTable.addValue(cellType2 + "_id", cell2Names[i]);
            neighbourTable.addValue("No of " + cellType1 + " around " + cellType2, countsCell1Around2[i]);
        }

        try {
            neighbourTable.save(tablePath);
        } catch (Exception e) {
            IJ.error("Could not save table: " + e.getMessage());
        }

        if (saveParametricImage) {
            String overlap1 = getParametricImg(noNeighboursCell2Around1, cell1, cellType2, cellType1);
            String overlap2 = getParametricImg(noNeighboursCell1Around2, cell2, cellType1, cellType2);

            ImagePlus paramImg1 = WindowManager.getImage(overlap1);
            if (paramImg1 != null) {
                IJ.saveAsTiff(paramImg1, savePath + fs + overlap1);
                paramImg1.close();
            }

            ImagePlus paramImg2 = WindowManager.getImage(overlap2);
            if (paramImg2 != null) {
                IJ.saveAsTiff(paramImg2, savePath + fs + overlap2);
                paramImg2.close();
            }
        }

        if (!gangliaBinary.equals("NA")) {
            ImagePlus gangliaImg = WindowManager.getImage(gangliaBinary);
            if (gangliaImg != null) {
                gangliaImg.close();
            }
        }

        IJ.log("Spatial analysis done for " + cellType1 + " and " + cellType2);
    }

    private static double[] countNeighborAroundRefImg(String refImg, String markerImg, int dilateRadius, String gangliaBinary) {
        IJ.run("Clear Results");
        IJ.run("CLIJ2 Macro Extensions", "cl_device=");

        // Push images to GPU
        IJ.run("CLIJ2 push [" + refImg + "]");
        IJ.run("CLIJ2 push [" + markerImg + "]");

        // Dilate labels
        IJ.run("CLIJ2 dilateLabels", "input=" + refImg + " destination=ref_dilate radius=" + dilateRadius);

        if (!gangliaBinary.equals("NA")) {
            IJ.run("CLIJ2 push [" + gangliaBinary + "]");
            IJ.run("CLIJ2 multiplyImages", "factor1=ref_dilate factor2=" + gangliaBinary + " destination=ref_dilate_ganglia_restrict");
            IJ.run("CLIJ2 labelOverlapCountMap", "input=ref_dilate_ganglia_restrict labels=" + markerImg + " destination=label_overlap_count");
            IJ.run("CLIJ2 reduceLabelsToCentroids", "input=ref_dilate_ganglia_restrict destination=ref_img_centroid");
        } else {
            IJ.run("CLIJ2 labelOverlapCountMap", "input=ref_dilate labels=" + markerImg + " destination=label_overlap_count");
            IJ.run("CLIJ2 reduceLabelsToCentroids", "input=ref_dilate destination=ref_img_centroid");
        }

        // Create binary masks
        IJ.run("CLIJ2 greaterOrEqualConstant", "input=" + markerImg + " destination=marker_img_binary constant=1.0");
        IJ.run("CLIJ2 greaterOrEqualConstant", "input=" + refImg + " destination=ref_img_binary constant=1.0");

        // Subtract to correct count
        IJ.run("CLIJ2 subtractImages", "subtrahend=marker_img_binary minuend=label_overlap_count destination=label_overlap_count_corrected");
        IJ.run("CLIJ2 statisticsOfBackgroundAndLabelledPixels", "input=label_overlap_count_corrected labelmap=ref_img_centroid");

        ResultsTable rt = ResultsTable.getResultsTable();
        double[] overlapCount = new double[rt.getCounter()];

        for (int i = 0; i < rt.getCounter(); i++) {
            overlapCount[i] = rt.getValue("MINIMUM_INTENSITY", i);
        }

        IJ.run("Clear Results");

        if (overlapCount.length > 0) {
            overlapCount[0] = 0;
        }

        // Release GPU memory
        IJ.run("CLIJ2 release [ref_img_centroid]");
        IJ.run("CLIJ2 release [ref_dilate]");
        if (!gangliaBinary.equals("NA")) {
            IJ.run("CLIJ2 release [" + gangliaBinary + "]");
        }

        return overlapCount;
    }

    private static String getParametricImg(double[] noNeighbours, String cellLabelImg, String cellType1, String cellType2) {
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

        String newName = cellType1 + "_around_" + cellType2;
        ImagePlus paramImg = WindowManager.getImage("parametric_img");
        if (paramImg != null) {
            paramImg.setTitle(newName);
            IJ.run(paramImg, "Fire", "");
        }

        IJ.run("CLIJ2 release [vector_neighbours]");

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

    private static String[] getRoiLabels(String roiLocation, String cellImage) {
        RoiManager roiManager = RoiManager.getInstance();
        if (roiManager == null) {
            roiManager = new RoiManager();
        }

        roiManager.reset();
        roiManager.runCommand("Open", roiLocation);

        ImagePlus cellImg = WindowManager.getImage(cellImage);
        if (cellImg != null) {
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
            return cellNames;
        }

        return new String[0];
    }

    private static double[] removeBackgroundIndex(double[] array) {
        if (array.length <= 1) return new double[0];

        double[] result = new double[array.length - 1];
        System.arraycopy(array, 1, result, 0, array.length - 1);
        return result;
    }

    private static double[] clipArrayToZero(double[] array, double threshold) {
        double[] result = new double[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = array[i] > threshold ? 0 : array[i];
        }
        return result;
    }

    private static boolean isClij2Available() {
        try {
            IJ.run("CLIJ2 Macro Extensions", "cl_device=");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

