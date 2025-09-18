package Analysis;

import ij.*;
import ij.process.ImageProcessor;
import ij.measure.ResultsTable;
import net.haesleinhuepf.clij2.CLIJ2;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import ij.plugin.frame.RoiManager;

import java.io.File;
import java.util.Arrays;

public class SpatialTwoCellType {

    private static final String FILE_SEPARATOR = File.separator;

    public static void execute(String cellType1, String cellImage1, String cellType2, String cellImage2,
                               String gangliaBinary, String savePath, double labelDilation,
                               boolean saveParametricImage, double pixelWidth, String roi1Path, String roi2Path) throws Exception {

        CLIJ2 clij2 = CLIJ2.getInstance();
        int labelDilationPixels = (int) Math.round(labelDilation / pixelWidth);

        String spatialSavePath = savePath + FILE_SEPARATOR + "spatial_analysis" + FILE_SEPARATOR;
        new File(spatialSavePath).mkdirs();

        // Get cell images
        ImagePlus cellImg1 = WindowManager.getImage(cellImage1);
        ImagePlus cellImg2 = WindowManager.getImage(cellImage2);

        if (cellImg1 == null) {
            IJ.error("Cell image 1 not found: " + cellImage1);
            return;
        }
        if (cellImg2 == null) {
            IJ.error("Cell image 2 not found: " + cellImage2);
            return;
        }

        int width = cellImg1.getWidth();
        int height = cellImg1.getHeight();

        // Get max labels for each cell type
        ImageProcessor labelIp1 = cellImg1.getProcessor();
        ImageProcessor labelIp2 = cellImg2.getProcessor();
        int maxLabel1 = (int) labelIp1.getStatistics().max;
        int maxLabel2 = (int) labelIp2.getStatistics().max;

        // Count cell2 neighbors around cell1
        int[] countsCell2AroundCell1 = countNeighboursAroundRef(clij2, cellImg1, cellImg2, labelDilationPixels, gangliaBinary, width, height);

        // Count cell1 neighbors around cell2
        int[] countsCell1AroundCell2 = countNeighboursAroundRef(clij2, cellImg2, cellImg1, labelDilationPixels, gangliaBinary, width, height);

        // Get ROI labels
        String[] cell1Names = getRoiLabels(roi1Path, cellImage1);
        String[] cell2Names = getRoiLabels(roi2Path, cellImage2);

        // Create results table
        ResultsTable outTable = new ResultsTable();

        // Add data ensuring arrays match expected lengths
        int maxRows = Math.max(Math.max(cell1Names.length, cell2Names.length),
                Math.max(countsCell2AroundCell1.length - 1, countsCell1AroundCell2.length - 1));

        for (int i = 0; i < maxRows; i++) {
            outTable.incrementCounter();

            // Cell 1 data
            if (i < cell1Names.length) {
                outTable.addValue(cellType1 + "_id", cell1Names[i]);
            } else {
                outTable.addValue(cellType1 + "_id", "");
            }

            if (i + 1 < countsCell2AroundCell1.length) {
                outTable.addValue("No of " + cellType2 + " around " + cellType1, countsCell2AroundCell1[i + 1]);
            } else {
                outTable.addValue("No of " + cellType2 + " around " + cellType1, 0);
            }

            // Cell 2 data
            if (i < cell2Names.length) {
                outTable.addValue(cellType2 + "_id", cell2Names[i]);
            } else {
                outTable.addValue(cellType2 + "_id", "");
            }

            if (i + 1 < countsCell1AroundCell2.length) {
                outTable.addValue("No of " + cellType1 + " around " + cellType2, countsCell1AroundCell2[i + 1]);
            } else {
                outTable.addValue("No of " + cellType1 + " around " + cellType2, 0);
            }
        }

        // Save CSV
        String csvPath = spatialSavePath + "Neighbour_count_" + cellType1 + "_" + cellType2 + ".csv";
        outTable.save(csvPath);

        // Save parametric images if requested
        if (saveParametricImage) {
            // Create parametric image for cell1 with cell2 counts
            createParametricImage(clij2, cellImg1, countsCell2AroundCell1, cellType2 + "_around_" + cellType1, spatialSavePath);

            // Create parametric image for cell2 with cell1 counts
            createParametricImage(clij2, cellImg2, countsCell1AroundCell2, cellType1 + "_around_" + cellType2, spatialSavePath);
        }

        IJ.log("Saved neighbor counts CSV and parametric images for " + cellType1 + " and " + cellType2);
    }

    private static int[] countNeighboursAroundRef(CLIJ2 clij2, ImagePlus refImg, ImagePlus markerImg,
                                                  int dilationPixels, String gangliaBinary, int width, int height) {

        // Push images to GPU
        ClearCLBuffer refBuffer = clij2.push(refImg);
        ClearCLBuffer markerBuffer = clij2.push(markerImg);

        // Dilate reference cells
        ClearCLBuffer refDilated = clij2.create(refBuffer);
        clij2.dilateLabels(refBuffer, refDilated, dilationPixels);

        ClearCLBuffer refDilatedFinal = refDilated;

        // Apply ganglia restriction if available
        if (!gangliaBinary.equals("NA") && WindowManager.getImage(gangliaBinary) != null) {
            ImagePlus gangliaImg = WindowManager.getImage(gangliaBinary);
            ClearCLBuffer gangliaBuffer = clij2.push(gangliaImg);
            ClearCLBuffer refDilatedRestricted = clij2.create(refBuffer);
            clij2.multiplyImages(refDilated, gangliaBuffer, refDilatedRestricted);
            refDilatedFinal = refDilatedRestricted;
            gangliaBuffer.close();
        }

        // Count label overlaps (number of marker pixels per reference label)
        ClearCLBuffer labelOverlapCount = clij2.create(refBuffer);
        clij2.labelOverlapCountMap(refDilatedFinal, markerBuffer, labelOverlapCount);

        // Pull overlap map back to CPU
        ImagePlus overlapImg = clij2.pull(labelOverlapCount);
        ImageProcessor overlapIp = overlapImg.getProcessor();

        // Get max label in reference image
        ImageProcessor refIp = refImg.getProcessor();
        int maxLabel = (int) refIp.getStatistics().max;

        int[] counts = new int[maxLabel + 1]; // counts[0] is background
        for (int label = 1; label <= maxLabel; label++) {
            // Find first pixel of this label and read count from overlap map
            outer:
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if ((int) refIp.getPixel(x, y) == label) {
                        counts[label] = (int) overlapIp.getPixel(x, y);
                        break outer;
                    }
                }
            }
        }

        // Cleanup GPU buffers
        refBuffer.close();
        markerBuffer.close();
        refDilated.close();
        if (refDilatedFinal != refDilated) refDilatedFinal.close();
        labelOverlapCount.close();

        // Close pulled image
        overlapImg.close();

        return counts;
    }

//    private static int[] countNeighboursAroundRef(CLIJ2 clij2, ImagePlus refImg, ImagePlus markerImg,
//                                                  int dilationPixels, String gangliaBinary, int width, int height) {
//
//        // Push images to GPU
//        ClearCLBuffer refBuffer = clij2.push(refImg);
//        ClearCLBuffer markerBuffer = clij2.push(markerImg);
//
//        // Dilate reference cells
//        ClearCLBuffer refDilated = clij2.create(refBuffer);
//        clij2.dilateLabels(refBuffer, refDilated, dilationPixels);
//
//        ClearCLBuffer refDilatedFinal = refDilated;
//
//        // Apply ganglia restriction if available
//        if (!gangliaBinary.equals("NA") && WindowManager.getImage(gangliaBinary) != null) {
//            ImagePlus gangliaImg = WindowManager.getImage(gangliaBinary);
//            ClearCLBuffer gangliaBuffer = clij2.push(gangliaImg);
//            ClearCLBuffer refDilatedRestricted = clij2.create(refBuffer);
//            clij2.multiplyImages(refDilated, gangliaBuffer, refDilatedRestricted);
//            refDilatedFinal = refDilatedRestricted;
//            gangliaBuffer.close();
//        }
//
//        // Count label overlaps
//        ClearCLBuffer labelOverlapCount = clij2.create(refBuffer);
//        clij2.labelOverlapCountMap(refDilatedFinal, markerBuffer, labelOverlapCount);
//
//        // Create binary markers
//        ClearCLBuffer markerBinary = clij2.create(refBuffer);
//        clij2.greaterOrEqualConstant(markerBuffer, markerBinary, 1.0f);
//
//        // Subtract marker binary to correct self-counting
//        ClearCLBuffer correctedOverlap = clij2.create(refBuffer);
//        clij2.subtractImages(labelOverlapCount, markerBinary, correctedOverlap);
//
//        // Get centroids of reference image
//        ClearCLBuffer refCentroids = clij2.create(refBuffer);
//        clij2.reduceLabelsToCentroids(refBuffer, refCentroids);
//
//        // Get statistics
//        clij2.statisticsOfBackgroundAndLabelledPixels(correctedOverlap, refCentroids);
//
//        ResultsTable results = ResultsTable.getResultsTable();
//        System.out.println(Arrays.toString(results.getHeadings()));
//        String[] minStr = results.getColumnAsStrings("MIN_INTENSITY");
//        double[] minIntensities = new double[minStr.length];
//        for (int i = 0; i < minStr.length; i++) {
//            minIntensities[i] = Double.parseDouble(minStr[i]);
//        }
//
//        // Convert to int array and set background to 0
//        int[] counts = new int[minIntensities.length];
//        for (int i = 0; i < minIntensities.length; i++) {
//            counts[i] = (int) minIntensities[i];
//            if (counts[i] > 500) counts[i] = 0; // Clip high values as in original macro
//        }
//        if (counts.length > 0) counts[0] = 0; // Background
//
//        // Cleanup
//        refBuffer.close();
//        markerBuffer.close();
//        refDilated.close();
//        if (refDilatedFinal != refDilated) refDilatedFinal.close();
//        labelOverlapCount.close();
//        markerBinary.close();
//        correctedOverlap.close();
//        refCentroids.close();
//
//        IJ.run("Clear Results");
//
//        return counts;
//    }

    private static void createParametricImage(CLIJ2 clij2, ImagePlus cellImg, int[] counts,
                                              String imageName, String savePath) {

        // Convert int array to float array
        float[] floatCounts = new float[counts.length];
        for (int i = 0; i < counts.length; i++) {
            floatCounts[i] = (float) counts[i];
        }

        ClearCLBuffer cellBuffer = clij2.push(cellImg);
        ClearCLBuffer vectorNeighbours = clij2.pushArray(floatCounts, floatCounts.length, 1, 1);
        ClearCLBuffer paramImg = clij2.create(cellBuffer);

        clij2.replaceIntensities(cellBuffer, vectorNeighbours, paramImg);

        ImagePlus paramResult = clij2.pull(paramImg);
        paramResult.setTitle(imageName);
        IJ.run(paramResult, "Fire", "");

        // Save parametric image
        IJ.saveAs(paramResult, "Tiff", savePath + imageName + ".tif");

        // Close without showing
        paramResult.close();

        // Cleanup
        cellBuffer.close();
        vectorNeighbours.close();
        paramImg.close();
    }

    private static String[] getRoiLabels(String roiPath, String cellImage) {
        RoiManager roiManager = RoiManager.getInstance();
        if (roiManager == null) {
            roiManager = new RoiManager();
        }
        roiManager.reset();

        roiManager.runCommand("Open", roiPath);

        ImagePlus img = WindowManager.getImage(cellImage);
        if (img != null) {
            img.show();
            IJ.run("Set Measurements...", "centroid display redirect=None decimal=3");
            roiManager.runCommand("Deselect");
            roiManager.runCommand("Measure");

            // Process ROI names
            ResultsTable results = ResultsTable.getResultsTable();
            String[] labels = new String[results.size()];

            for (int i = 0; i < results.size(); i++) {
                String label = results.getLabel(i);
                if (label != null && label.contains(":")) {
                    int colonIndex = label.indexOf(":");
                    labels[i] = label.substring(colonIndex + 1);
                } else {
                    labels[i] = String.valueOf(i + 1);
                }
            }

            IJ.run("Clear Results");
            return labels;
        }

        return new String[0];
    }
}

//package Analysis;
//
//import ij.*;
//import ij.process.ImageProcessor;
//import ij.measure.ResultsTable;
//import ij.plugin.frame.RoiManager;
//import net.haesleinhuepf.clij2.CLIJ2;
//import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
//
//import java.io.File;
//import java.util.ArrayList;
//import java.util.List;
//
//public class SpatialTwoCellType {
//
//    private static final String FILE_SEPARATOR = File.separator;
//
//    public static void execute(String cellType1, String cell1, String cellType2, String cell2,
//                               String gangliaBinaryOrig, String savePath, double labelDilation,
//                               boolean saveParametricImage, double pixelWidth,
//                               String roiLocationCell1, String roiLocationCell2) throws Exception {
//
//        if (cellType1.equals(cellType2)) {
//            throw new Exception("Cell names are the same for both celltypes");
//        }
//
//        CLIJ2 clij2 = CLIJ2.getInstance();
//
//        // Convert dilation to pixels
//        int labelDilationPixels = (int) Math.round(labelDilation / pixelWidth);
//
//        String spatialSavePath = savePath + FILE_SEPARATOR + "spatial_analysis" + FILE_SEPARATOR;
//        new File(spatialSavePath).mkdirs();
//
//        // Process ganglia binary if provided
//        String gangliaBinary = "NA";
//        if (!gangliaBinaryOrig.equals("NA")) {
//            ImagePlus gangliaImg = WindowManager.getImage(gangliaBinaryOrig);
//            if (gangliaImg != null) {
//                IJ.run("Select None");
//                IJ.run(gangliaImg, "Duplicate...", "title=ganglia_binary_dup");
//                ImagePlus dupImg = WindowManager.getImage("ganglia_binary_dup");
//                dupImg.show();
//                IJ.setThreshold(0.5, 65535);
//                IJ.run("Convert to Mask");
//                dupImg.setTitle("Ganglia_outline");
//                gangliaBinary = dupImg.getTitle();
//                IJ.run("Divide...", "value=255");
//                IJ.setMinAndMax(0, 1);
//            }
//        }
//
//        // Get neighbor counts
//        double[] countsCell2Around1 = countNeighborAroundRef(cell1, cell2, labelDilationPixels, gangliaBinary, clij2);
//        double[] countsCell1Around2 = countNeighborAroundRef(cell2, cell1, labelDilationPixels, gangliaBinary, clij2);
//
//        // Clip values > 500 to zero
//        countsCell2Around1 = clipArrayToZero(countsCell2Around1, 500);
//        countsCell1Around2 = clipArrayToZero(countsCell1Around2, 500);
//
//        // Get ROI names
//        String[] cell1Names = getRoiLabels(roiLocationCell1, cell1);
//        String[] cell2Names = getRoiLabels(roiLocationCell2, cell2);
//
//        // Create and save results table
//        ResultsTable resultsTable = new ResultsTable();
//
//        int maxRows = Math.max(cell1Names.length, cell2Names.length);
//
//        for (int i = 0; i < maxRows; i++) {
//            resultsTable.incrementCounter();
//
//            if (i < cell1Names.length) {
//                resultsTable.addLabel(cell1Names[i]);
//                resultsTable.addValue(cellType1 + "_id", cell1Names[i]);
//                if (i < countsCell2Around1.length) {
//                    resultsTable.addValue("No of " + cellType2 + " around " + cellType1, countsCell2Around1[i]);
//                } else {
//                    resultsTable.addValue("No of " + cellType2 + " around " + cellType1, 0);
//                }
//            } else {
//                resultsTable.addLabel("");
//                resultsTable.addValue(cellType1 + "_id", "");
//                resultsTable.addValue("No of " + cellType2 + " around " + cellType1, 0);
//            }
//
//            if (i < cell2Names.length) {
//                resultsTable.addValue(cellType2 + "_id", cell2Names[i]);
//                if (i < countsCell1Around2.length) {
//                    resultsTable.addValue("No of " + cellType1 + " around " + cellType2, countsCell1Around2[i]);
//                } else {
//                    resultsTable.addValue("No of " + cellType1 + " around " + cellType2, 0);
//                }
//            } else {
//                resultsTable.addValue(cellType2 + "_id", "");
//                resultsTable.addValue("No of " + cellType1 + " around " + cellType2, 0);
//            }
//        }
//
//        String tablePath = spatialSavePath + "Neighbour_count_" + cellType1 + "_" + cellType2 + ".csv";
//        resultsTable.save(tablePath);
//
//        // Save parametric images if requested
//        if (saveParametricImage) {
//            ImagePlus overlap1 = getParametricImage(countsCell2Around1, cell1, cellType2, cellType1, clij2);
//            IJ.saveAs(overlap1, "Tiff", spatialSavePath + overlap1.getTitle());
//            overlap1.close();
//
//            ImagePlus overlap2 = getParametricImage(countsCell1Around2, cell2, cellType1, cellType2, clij2);
//            IJ.saveAs(overlap2, "Tiff", spatialSavePath + overlap2.getTitle());
//            overlap2.close();
//        }
//
//        // Close ganglia binary if it was created
//        if (!gangliaBinary.equals("NA")) {
//            ImagePlus gangliaImg = WindowManager.getImage(gangliaBinary);
//            if (gangliaImg != null) {
//                gangliaImg.close();
//            }
//        }
//
//        IJ.log("Spatial analysis done for " + cellType1 + " and " + cellType2);
//    }
//
//    private static double[] countNeighborAroundRef(String refImg, String markerImg, int dilateRadius,
//                                                   String gangliaBinary, CLIJ2 clij2) {
//        ImagePlus refImage = WindowManager.getImage(refImg);
//        ImagePlus markerImage = WindowManager.getImage(markerImg);
//
//        if (refImage == null || markerImage == null) {
//            return new double[0];
//        }
//
//        ClearCLBuffer refBuffer = clij2.push(refImage);
//        ClearCLBuffer markerBuffer = clij2.push(markerImage);
//
//        ClearCLBuffer refDilate = clij2.create(refBuffer);
//        clij2.dilateLabels(refBuffer, refDilate, dilateRadius);
//
//        ClearCLBuffer labelOverlapCount;
//        ClearCLBuffer refImgCentroid;
//
//        if (!gangliaBinary.equals("NA")) {
//            ImagePlus gangliaImg = WindowManager.getImage(gangliaBinary);
//            if (gangliaImg != null) {
//                ClearCLBuffer gangliaBuffer = clij2.push(gangliaImg);
//                ClearCLBuffer refDilateRestricted = clij2.create(refBuffer);
//                clij2.multiplyImages(refDilate, gangliaBuffer, refDilateRestricted);
//
//                labelOverlapCount = clij2.create(refBuffer);
//                clij2.labelOverlapCountMap(refDilateRestricted, markerBuffer, labelOverlapCount);
//
//                refImgCentroid = clij2.create(refBuffer);
//                clij2.reduceLabelsToCentroids(refDilateRestricted, refImgCentroid);
//
//                gangliaBuffer.close();
//                refDilateRestricted.close();
//            } else {
//                labelOverlapCount = clij2.create(refBuffer);
//                clij2.labelOverlapCountMap(refDilate, markerBuffer, labelOverlapCount);
//
//                refImgCentroid = clij2.create(refBuffer);
//                clij2.reduceLabelsToCentroids(refDilate, refImgCentroid);
//            }
//        } else {
//            labelOverlapCount = clij2.create(refBuffer);
//            clij2.labelOverlapCountMap(refDilate, markerBuffer, labelOverlapCount);
//
//            refImgCentroid = clij2.create(refBuffer);
//            clij2.reduceLabelsToCentroids(refDilate, refImgCentroid);
//        }
//
//        // Create binary images
//        ClearCLBuffer markerBinary = clij2.create(markerBuffer);
//        ClearCLBuffer refBinary = clij2.create(refBuffer);
//        clij2.greaterOrEqualConstant(markerBuffer, markerBinary, 1.0f);
//        clij2.greaterOrEqualConstant(refBuffer, refBinary, 1.0f);
//
//        // Subtract to get corrected count
//        ClearCLBuffer labelOverlapCorrected = clij2.create(refBuffer);
//        clij2.subtractImages(labelOverlapCount, markerBinary, labelOverlapCorrected);
//
//        // Get statistics
//        clij2.statisticsOfBackgroundAndLabelledPixels(labelOverlapCorrected, refImgCentroid);
//
//        ResultsTable results = ResultsTable.getResultsTable();
//        double[] overlapCount = results.getColumn("MINIMUM_INTENSITY");
//        if (overlapCount.length > 0) {
//            overlapCount[0] = 0; // background is zero
//        }
//
//        // Cleanup
//        refBuffer.close();
//        markerBuffer.close();
//        refDilate.close();
//        labelOverlapCount.close();
//        refImgCentroid.close();
//        markerBinary.close();
//        refBinary.close();
//        labelOverlapCorrected.close();
//
//        IJ.run("Clear Results");
//        return overlapCount;
//    }
//
//    private static ImagePlus getParametricImage(double[] noNeighbours, String cellLabelImg,
//                                                String cellType1, String cellType2, CLIJ2 clij2) {
//        ImagePlus cellImg = WindowManager.getImage(cellLabelImg);
//        if (cellImg == null) return null;
//
//        float[] neighboursFloat = new float[noNeighbours.length];
//        for (int i = 0; i < noNeighbours.length; i++) {
//            neighboursFloat[i] = (float) noNeighbours[i];
//        }
//
//        ClearCLBuffer cellBuffer = clij2.push(cellImg);
//        ClearCLBuffer vectorNeighbours = clij2.pushArray(neighboursFloat, neighboursFloat.length, 1, 1);
//        ClearCLBuffer parametricImg = clij2.create(cellBuffer);
//
//        clij2.replaceIntensities(cellBuffer, vectorNeighbours, parametricImg);
//
//        ImagePlus result = clij2.pull(parametricImg);
//        String newName = cellType1 + "_around_" + cellType2;
//        result.setTitle(newName);
//        result.show();
//        IJ.run(result, "Fire", "");
//
//        // Cleanup
//        cellBuffer.close();
//        vectorNeighbours.close();
//        parametricImg.close();
//
//        return result;
//    }
//
//    private static String[] getRoiLabels(String roiLocation, String cellImage) {
//        RoiManager roiManager = RoiManager.getInstance();
//        if (roiManager == null) {
//            roiManager = new RoiManager();
//        }
//        roiManager.reset();
//
//        roiManager.runCommand("Open", roiLocation);
//        ImagePlus cellImg = WindowManager.getImage(cellImage);
//        if (cellImg != null) {
//            cellImg.show();
//            IJ.run("Set Measurements...", "centroid display redirect=None decimal=3");
//            roiManager.runCommand("Deselect");
//            roiManager.runCommand("Measure");
//
//            // Rename ROI labels
//            renameRoiNameResultTable();
//
//            ResultsTable results = ResultsTable.getResultsTable();
//            String[] cellNames = results.getColumnAsStrings("Label");
//            IJ.run("Clear Results");
//
//            return cellNames != null ? cellNames : new String[0];
//        }
//        return new String[0];
//    }
//
//    private static void renameRoiNameResultTable() {
//        ResultsTable results = ResultsTable.getResultsTable();
//        if (results.getCounter() == 0) {
//            IJ.log("No rois in results table for spatial analysis");
//            return;
//        }
//
//        for (int i = 0; i < results.getCounter(); i++) {
//            String oldLabel = results.getLabel(i);
//            if (oldLabel != null) {
//                int delimiter = oldLabel.indexOf(":");
//                if (delimiter > 0) {
//                    String newLabel = oldLabel.substring(delimiter + 1);
//                    results.setLabel(newLabel, i);
//                }
//            }
//        }
//    }
//
//    private static double[] clipArrayToZero(double[] arr, double thresh) {
//        double[] clipped = new double[arr.length];
//        for (int i = 0; i < arr.length; i++) {
//            clipped[i] = arr[i] > thresh ? 0 : arr[i];
//        }
//        return clipped;
//    }
//}






//package Analysis;
//
//import ij.*;
//import ij.measure.ResultsTable;
//import ij.plugin.frame.RoiManager;
//import java.io.File;
//
//public class SpatialTwoCellType {
//
//    public static void execute(String cellType1, String cell1, String cellType2, String cell2,
//                               String gangliaBinaryOrig, String savePath, double labelDilation,
//                               boolean saveParametricImage, double pixelWidth, String roiLocationCell1,
//                               String roiLocationCell2) {
//
//        IJ.run("Clear Results");
//
//        // Check if CLIJ is installed
//        if (!isClij2Available()) {
//            IJ.error("CLIJ not installed. Check Log for installation details");
//            IJ.log("CLIJ installation link: Please install CLIJ using from: https://clij.github.io/clij2-docs/installationInFiji");
//            return;
//        }
//
//        if (cellType1.equals(cellType2)) {
//            IJ.error("Cell names or ROI managers are the same for both celltypes");
//            return;
//        }
//
//        RoiManager roiManager = RoiManager.getInstance();
//        if (roiManager == null) {
//            roiManager = new RoiManager();
//        }
//        roiManager.reset();
//
//        IJ.run("CLIJ2 Macro Extensions", "cl_device=");
//
//        IJ.log("Running spatial analysis on " + cellType1 + " and " + cellType2);
//
//        // Convert to pixels
//        int labelDilationPixels = (int) Math.round(labelDilation / pixelWidth);
//
//        String fs = File.separator;
//        savePath = savePath + fs + "spatial_analysis" + fs;
//        File saveDir = new File(savePath);
//        if (!saveDir.exists()) {
//            saveDir.mkdirs();
//        }
//
//        // Binary image for ganglia
//        IJ.run("Options...", "iterations=1 count=1 black");
//        String gangliaBinary = "NA";
//
//        if (gangliaBinaryOrig != null && !gangliaBinaryOrig.equals("NA")) {
//            ImagePlus gangliaImg = WindowManager.getImage(gangliaBinaryOrig);
//            if (gangliaImg != null) {
//                IJ.run(gangliaImg, "Select None", "");
//                IJ.run(gangliaImg, "Duplicate...", "title=ganglia_binary_dup");
//                ImagePlus gangliaDup = WindowManager.getImage("ganglia_binary_dup");
//                if (gangliaDup != null) {
//                    IJ.setThreshold(gangliaDup, 0.5, 65535);
//                    IJ.run(gangliaDup, "Convert to Mask", "");
//                    gangliaDup.setTitle("Ganglia_outline");
//                    gangliaBinary = gangliaDup.getTitle();
//                    IJ.run(gangliaDup, "Divide...", "value=255");
//                    gangliaDup.resetDisplayRange();
//                    IJ.run(gangliaDup, "Set...", "value=0 value=1");
//                }
//            }
//        }
//
//        // Cell 2 neighbors around cell 1
//        double[] noNeighboursCell2Around1 = countNeighborAroundRefImg(cell1, cell2, labelDilationPixels, gangliaBinary);
//        double[] countsCell2Around1 = removeBackgroundIndex(noNeighboursCell2Around1);
//
//        // Cell 1 neighbors around cell 2
//        double[] noNeighboursCell1Around2 = countNeighborAroundRefImg(cell2, cell1, labelDilationPixels, gangliaBinary);
//        double[] countsCell1Around2 = removeBackgroundIndex(noNeighboursCell1Around2);
//
//        // Clip values > 500 to zero
//        countsCell2Around1 = clipArrayToZero(countsCell2Around1, 500);
//        countsCell1Around2 = clipArrayToZero(countsCell1Around2, 500);
//
//        roiManager.reset();
//        IJ.run("Clear Results");
//
//        String tableName = "Neighbour_count_" + cellType1 + "_" + cellType2;
//        String tablePath = savePath + fs + tableName + ".csv";
//
//        String[] cell1Names = getRoiLabels(roiLocationCell1, cell1);
//        String[] cell2Names = getRoiLabels(roiLocationCell2, cell2);
//
//        // Create and save results table
//        ResultsTable neighbourTable = new ResultsTable();
//        neighbourTable.setDefaultHeadings();
//
//        // Add cell 1 data
//        for (int i = 0; i < cell1Names.length && i < countsCell2Around1.length; i++) {
//            neighbourTable.incrementCounter();
//            neighbourTable.addLabel(cell1Names[i]);
//            neighbourTable.addValue(cellType1 + "_id", cell1Names[i]);
//            neighbourTable.addValue("No of " + cellType2 + " around " + cellType1, countsCell2Around1[i]);
//        }
//
//        // Add cell 2 data
//        for (int i = 0; i < cell2Names.length && i < countsCell1Around2.length; i++) {
//            if (i >= neighbourTable.getCounter()) {
//                neighbourTable.incrementCounter();
//            }
//            neighbourTable.addValue(cellType2 + "_id", cell2Names[i]);
//            neighbourTable.addValue("No of " + cellType1 + " around " + cellType2, countsCell1Around2[i]);
//        }
//
//        try {
//            neighbourTable.save(tablePath);
//        } catch (Exception e) {
//            IJ.error("Could not save table: " + e.getMessage());
//        }
//
//        if (saveParametricImage) {
//            String overlap1 = getParametricImg(noNeighboursCell2Around1, cell1, cellType2, cellType1);
//            String overlap2 = getParametricImg(noNeighboursCell1Around2, cell2, cellType1, cellType2);
//
//            ImagePlus paramImg1 = WindowManager.getImage(overlap1);
//            if (paramImg1 != null) {
//                IJ.saveAsTiff(paramImg1, savePath + fs + overlap1);
//                paramImg1.close();
//            }
//
//            ImagePlus paramImg2 = WindowManager.getImage(overlap2);
//            if (paramImg2 != null) {
//                IJ.saveAsTiff(paramImg2, savePath + fs + overlap2);
//                paramImg2.close();
//            }
//        }
//
//        if (!gangliaBinary.equals("NA")) {
//            ImagePlus gangliaImg = WindowManager.getImage(gangliaBinary);
//            if (gangliaImg != null) {
//                gangliaImg.close();
//            }
//        }
//
//        IJ.log("Spatial analysis done for " + cellType1 + " and " + cellType2);
//    }
//
//    private static double[] countNeighborAroundRefImg(String refImg, String markerImg, int dilateRadius, String gangliaBinary) {
//        IJ.run("Clear Results");
//        IJ.run("CLIJ2 Macro Extensions", "cl_device=");
//
//        // Push images to GPU
//        IJ.run("CLIJ2 push [" + refImg + "]");
//        IJ.run("CLIJ2 push [" + markerImg + "]");
//
//        // Dilate labels
//        IJ.run("CLIJ2 dilateLabels", "input=" + refImg + " destination=ref_dilate radius=" + dilateRadius);
//
//        if (!gangliaBinary.equals("NA")) {
//            IJ.run("CLIJ2 push [" + gangliaBinary + "]");
//            IJ.run("CLIJ2 multiplyImages", "factor1=ref_dilate factor2=" + gangliaBinary + " destination=ref_dilate_ganglia_restrict");
//            IJ.run("CLIJ2 labelOverlapCountMap", "input=ref_dilate_ganglia_restrict labels=" + markerImg + " destination=label_overlap_count");
//            IJ.run("CLIJ2 reduceLabelsToCentroids", "input=ref_dilate_ganglia_restrict destination=ref_img_centroid");
//        } else {
//            IJ.run("CLIJ2 labelOverlapCountMap", "input=ref_dilate labels=" + markerImg + " destination=label_overlap_count");
//            IJ.run("CLIJ2 reduceLabelsToCentroids", "input=ref_dilate destination=ref_img_centroid");
//        }
//
//        // Create binary masks
//        IJ.run("CLIJ2 greaterOrEqualConstant", "input=" + markerImg + " destination=marker_img_binary constant=1.0");
//        IJ.run("CLIJ2 greaterOrEqualConstant", "input=" + refImg + " destination=ref_img_binary constant=1.0");
//
//        // Subtract to correct count
//        IJ.run("CLIJ2 subtractImages", "subtrahend=marker_img_binary minuend=label_overlap_count destination=label_overlap_count_corrected");
//        IJ.run("CLIJ2 statisticsOfBackgroundAndLabelledPixels", "input=label_overlap_count_corrected labelmap=ref_img_centroid");
//
//        ResultsTable rt = ResultsTable.getResultsTable();
//        double[] overlapCount = new double[rt.getCounter()];
//
//        for (int i = 0; i < rt.getCounter(); i++) {
//            overlapCount[i] = rt.getValue("MINIMUM_INTENSITY", i);
//        }
//
//        IJ.run("Clear Results");
//
//        if (overlapCount.length > 0) {
//            overlapCount[0] = 0;
//        }
//
//        // Release GPU memory
//        IJ.run("CLIJ2 release [ref_img_centroid]");
//        IJ.run("CLIJ2 release [ref_dilate]");
//        if (!gangliaBinary.equals("NA")) {
//            IJ.run("CLIJ2 release [" + gangliaBinary + "]");
//        }
//
//        return overlapCount;
//    }
//
//    private static String getParametricImg(double[] noNeighbours, String cellLabelImg, String cellType1, String cellType2) {
//        IJ.run("CLIJ2 Macro Extensions", "cl_device=");
//
//        // Convert double array to string for macro
//        StringBuilder sb = new StringBuilder();
//        for (int i = 0; i < noNeighbours.length; i++) {
//            sb.append(noNeighbours[i]);
//            if (i < noNeighbours.length - 1) {
//                sb.append(",");
//            }
//        }
//
//        IJ.run("CLIJ2 pushArray2D", "array=[" + sb.toString() + "] width=" + noNeighbours.length + " height=1 destination=vector_neighbours");
//        IJ.run("CLIJ2 replaceIntensities", "input=" + cellLabelImg + " new_values_vector=vector_neighbours destination=parametric_img");
//        IJ.run("CLIJ2 pull [parametric_img]");
//
//        String newName = cellType1 + "_around_" + cellType2;
//        ImagePlus paramImg = WindowManager.getImage("parametric_img");
//        if (paramImg != null) {
//            paramImg.setTitle(newName);
//            IJ.run(paramImg, "Fire", "");
//        }
//
//        IJ.run("CLIJ2 release [vector_neighbours]");
//
//        return newName;
//    }
//
//    private static void renameRoiNameResultTable() {
//        ResultsTable rt = ResultsTable.getResultsTable();
//        if (rt.getCounter() == 0) {
//            IJ.log("No rois in results table for spatial analysis");
//            return;
//        }
//
//        for (int i = 0; i < rt.getCounter(); i++) {
//            String oldLabel = rt.getLabel(i);
//            if (oldLabel != null) {
//                int delimiter = oldLabel.indexOf(":");
//                if (delimiter > -1) {
//                    String newLabel = oldLabel.substring(delimiter + 1);
//                    rt.setLabel(newLabel, i);
//                }
//            }
//        }
//    }
//
//    private static String[] getRoiLabels(String roiLocation, String cellImage) {
//        RoiManager roiManager = RoiManager.getInstance();
//        if (roiManager == null) {
//            roiManager = new RoiManager();
//        }
//
//        roiManager.reset();
//        roiManager.runCommand("Open", roiLocation);
//
//        ImagePlus cellImg = WindowManager.getImage(cellImage);
//        if (cellImg != null) {
//            IJ.run("Set Measurements...", "centroid display redirect=None decimal=3");
//            roiManager.deselect();
//            roiManager.runCommand("Measure");
//            renameRoiNameResultTable();
//
//            ResultsTable rt = ResultsTable.getResultsTable();
//            String[] cellNames = new String[rt.getCounter()];
//            for (int i = 0; i < rt.getCounter(); i++) {
//                cellNames[i] = rt.getLabel(i);
//            }
//            IJ.run("Clear Results");
//            return cellNames;
//        }
//
//        return new String[0];
//    }
//
//    private static double[] removeBackgroundIndex(double[] array) {
//        if (array.length <= 1) return new double[0];
//
//        double[] result = new double[array.length - 1];
//        System.arraycopy(array, 1, result, 0, array.length - 1);
//        return result;
//    }
//
//    private static double[] clipArrayToZero(double[] array, double threshold) {
//        double[] result = new double[array.length];
//        for (int i = 0; i < array.length; i++) {
//            result[i] = array[i] > threshold ? 0 : array[i];
//        }
//        return result;
//    }
//
//    private static boolean isClij2Available() {
//        try {
//            IJ.run("CLIJ2 Macro Extensions", "cl_device=");
//            return true;
//        } catch (Exception e) {
//            return false;
//        }
//    }
//}
//
