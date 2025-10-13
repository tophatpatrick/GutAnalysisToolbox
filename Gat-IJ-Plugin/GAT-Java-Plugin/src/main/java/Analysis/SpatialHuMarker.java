package Analysis;

import ij.*;
import ij.process.ImageProcessor;
import ij.measure.ResultsTable;
import net.haesleinhuepf.clij2.CLIJ2;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import ij.plugin.frame.RoiManager;

import java.io.File;

public class SpatialHuMarker {

    private static final String FILE_SEPARATOR = File.separator;

    public static void execute(String huCellType, String huCellImage, String markerCellType, String markerCellImage,
                               String gangliaBinary, String savePath, double labelDilation,
                               boolean saveParametricImage, double pixelWidth, String huRoiPath, String markerRoiPath) throws Exception {

        CLIJ2 clij2 = CLIJ2.getInstance();
        int labelDilationPixels = (int) Math.round(labelDilation / pixelWidth);

        String spatialSavePath = savePath + FILE_SEPARATOR + "spatial_analysis" + FILE_SEPARATOR;
        new File(spatialSavePath).mkdirs();

        // Get cell images
        ImagePlus huImg = WindowManager.getImage(huCellImage);
        ImagePlus markerImg = WindowManager.getImage(markerCellImage);

        if (huImg == null) {
            IJ.error("Hu cell image not found: " + huCellImage);
            return;
        }
        if (markerImg == null) {
            IJ.error("Marker cell image not found: " + markerCellImage);
            return;
        }

        int width = huImg.getWidth();
        int height = huImg.getHeight();

        // Count Hu neighbors around marker cells (ref around marker)
        int[] countsHuAroundMarker = countRefAroundMarker(clij2, huImg, markerImg, labelDilationPixels, gangliaBinary);

        // Count marker neighbors around Hu cells (marker around ref)
        int[] countsMarkerAroundHu = countMarkerAroundRef(clij2, huImg, markerImg, labelDilationPixels, gangliaBinary, width, height);

        // Get ROI labels
        String[] huNames = getRoiLabels(huRoiPath, huCellImage);
        String[] markerNames = getRoiLabels(markerRoiPath, markerCellImage);

        // Create results table
        ResultsTable outTable = new ResultsTable();

        // Add data ensuring arrays match expected lengths
        int maxRows = Math.max(Math.max(huNames.length, markerNames.length),
                Math.max(countsHuAroundMarker.length - 1, countsMarkerAroundHu.length - 1));

        for (int i = 0; i < maxRows; i++) {
            outTable.incrementCounter();

            // Marker cell data (receiving Hu neighbors)
            if (i < markerNames.length) {
                outTable.addValue(markerCellType + "_id", markerNames[i]);
            } else {
                outTable.addValue(markerCellType + "_id", "");
            }

            if (i + 1 < countsHuAroundMarker.length) {
                outTable.addValue("No of " + huCellType + " around " + markerCellType, countsHuAroundMarker[i + 1]);
            } else {
                outTable.addValue("No of " + huCellType + " around " + markerCellType, 0);
            }

            // Hu cell data (receiving marker neighbors)
            if (i < huNames.length) {
                outTable.addValue(huCellType + "_id", huNames[i]);
            } else {
                outTable.addValue(huCellType + "_id", "");
            }

            if (i + 1 < countsMarkerAroundHu.length) {
                outTable.addValue("No of " + markerCellType + " around " + huCellType, countsMarkerAroundHu[i + 1]);
            } else {
                outTable.addValue("No of " + markerCellType + " around " + huCellType, 0);
            }
        }

        // Save CSV
        String csvPath = spatialSavePath + "Neighbour_count_" + huCellType + "_" + markerCellType + ".csv";
        outTable.save(csvPath);

        // Save parametric images if requested
        if (saveParametricImage) {
            // Create parametric image for marker cells with Hu counts
            createParametricImage(clij2, markerImg, countsHuAroundMarker, huCellType + "_around_" + markerCellType, spatialSavePath);

            // Create parametric image for Hu cells with marker counts
            createParametricImage(clij2, huImg, countsMarkerAroundHu, markerCellType + "_around_" + huCellType, spatialSavePath);
        }

        IJ.log("Saved neighbor counts CSV and parametric images for " + huCellType + " and " + markerCellType);
    }

    // Hu (ref) is pan-neuronal and should label all cells, marker is subset
    private static int[] countRefAroundMarker(CLIJ2 clij2, ImagePlus refImg, ImagePlus markerImg,
                                              int dilationPixels, String gangliaBinary) {

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

            // Get neighbor count for each cell in ref_img
            ClearCLBuffer refNeighbourCount = clij2.create(refBuffer);
            clij2.touchingNeighborCountMap(refDilatedFinal, refNeighbourCount);

            gangliaBuffer.close();

            // Get centroids of marker image
            ClearCLBuffer markerCentroids = clij2.create(refBuffer);
            clij2.reduceLabelsToCentroids(markerBuffer, markerCentroids);

            // Get statistics
            clij2.statisticsOfBackgroundAndLabelledPixels(refNeighbourCount, markerCentroids);

            refNeighbourCount.close();
            markerCentroids.close();
        } else {
            // Get neighbor count for each cell in ref_img
            ClearCLBuffer refNeighbourCount = clij2.create(refBuffer);
            clij2.touchingNeighborCountMap(refDilated, refNeighbourCount);

            // Get centroids of marker image
            ClearCLBuffer markerCentroids = clij2.create(refBuffer);
            clij2.reduceLabelsToCentroids(markerBuffer, markerCentroids);

            // Get statistics
            clij2.statisticsOfBackgroundAndLabelledPixels(refNeighbourCount, markerCentroids);

            refNeighbourCount.close();
            markerCentroids.close();
        }

        ResultsTable results = ResultsTable.getResultsTable();

        String[] minStr = results.getColumnAsStrings("MINIMUM_INTENSITY");
        double[] minIntensities = new double[minStr.length];
        for (int i = 0; i < minStr.length; i++) {
            minIntensities[i] = Double.parseDouble(minStr[i]);
        }

        // Convert to int array and set background to 0
        int[] counts = new int[minIntensities.length];
        for (int i = 0; i < minIntensities.length; i++) {
            counts[i] = (int) minIntensities[i];
        }
        counts[0] = 0; // Background

        // Cleanup
        refBuffer.close();
        markerBuffer.close();
        refDilated.close();
        if (refDilatedFinal != refDilated) refDilatedFinal.close();

        IJ.run("Clear Results");

        return counts;
    }

    // Count how many cells in marker_img around ref (using Region Adjacency Graph approach)
    private static int[] countMarkerAroundRef(CLIJ2 clij2, ImagePlus refImg, ImagePlus markerImg,
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

        // Get marker values corresponding to ref image positions
        clij2.statisticsOfLabelledPixels(refDilatedFinal, markerBuffer);

        ResultsTable results = ResultsTable.getResultsTable();;

        String[] maxStr = results.getColumnAsStrings("MINIMUM_INTENSITY");
        double[] markerLabelRefIds = new double[maxStr.length];
        for (int i = 0; i < maxStr.length; i++) {
            markerLabelRefIds[i] = Double.parseDouble(maxStr[i]);
        }



        IJ.run("Clear Results");

        // Get IDs of all cells in ref image
        clij2.statisticsOfBackgroundAndLabelledPixels(refDilatedFinal, refDilatedFinal);
        results = ResultsTable.getResultsTable();
        String[] idsStr = results.getColumnAsStrings("IDENTIFIER");
        int[] refIds = new int[idsStr.length];
        for (int i = 0; i < idsStr.length; i++) {
            refIds[i] = Integer.parseInt(idsStr[i]);
        }
        IJ.run("Clear Results");

        // Pull dilated reference image back to ImageJ for RAG analysis
        ImagePlus refDilatedImg = clij2.pull(refDilatedFinal);
        refDilatedImg.show();

        // Run Region Adjacency Graph analysis
        IJ.run(refDilatedImg, "Region Adjacency Graph", "");
        String ragTableName = refDilatedImg.getTitle() + "-RAG";

        // Wait for processing
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Get RAG table results
        ResultsTable ragTable = ResultsTable.getResultsTable(ragTableName);
        if (ragTable == null) {
            // Fallback if table naming is different
            ragTable = ResultsTable.getResultsTable();
        }

        String[] lbl1Str = ragTable.getColumnAsStrings("Label 1");
        int[] label1 = new int[lbl1Str.length];
        for (int i = 0; i < lbl1Str.length; i++) {
            label1[i] = Integer.parseInt(lbl1Str[i]);
        }

        String[] lbl2Str = ragTable.getColumnAsStrings("Label 1");
        int[] label2 = new int[lbl1Str.length];
        for (int i = 0; i < lbl1Str.length; i++) {
            label2[i] = Integer.parseInt(lbl2Str[i]);
        }

        // Initialize counts array
        int[] counts = new int[refIds.length];

        // Count neighbors based on RAG
        for (int i = 0; i < label1.length; i++) {
            int idx1 = (int) label1[i];
            int idx2 = (int) label2[i];

            // Check if label2 corresponds to a marker cell
            if (idx2 < markerLabelRefIds.length && markerLabelRefIds[idx2] > 0) {
                if (idx1 < counts.length) {
                    counts[idx1]++;
                }
            }
        }

        for (int i = 0; i < label2.length; i++) {
            int idx1 = (int) label1[i];
            int idx2 = (int) label2[i];

            // Check if label1 corresponds to a marker cell
            if (idx1 < markerLabelRefIds.length && markerLabelRefIds[idx1] > 0) {
                if (idx2 < counts.length) {
                    counts[idx2]++;
                }
            }
        }

        // Cleanup
        refBuffer.close();
        markerBuffer.close();
        refDilated.close();
        if (refDilatedFinal != refDilated) refDilatedFinal.close();

        refDilatedImg.close();
        IJ.run("Clear Results");

        // Close RAG table if it exists
        if (WindowManager.getWindow(ragTableName) != null) {
            IJ.selectWindow(ragTableName);
            IJ.run("Close");
        }

        return counts;
    }

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
//import java.util.HashSet;
//import java.util.Set;
//
//public class SpatialHuMarker {
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
//        double[] noNeighboursRefMarker = countRefAroundMarker(cell1, cell2, labelDilationPixels, gangliaBinary, cellType1, cellType2, clij2);
//        double[] countsRefMarker = removeFirstElement(noNeighboursRefMarker);
//
//        double[] noNeighboursMarkerRef = countMarkerAroundRef(cell1, cell2, labelDilationPixels, gangliaBinary, clij2);
//        double[] countsMarkerRef = removeFirstElement(noNeighboursMarkerRef);
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
//            if (i < cell2Names.length) {
//                resultsTable.addLabel(cell2Names[i]);
//                resultsTable.addValue(cellType2 + "_id", cell2Names[i]);
//                if (i < countsRefMarker.length) {
//                    resultsTable.addValue("No of " + cellType1 + " around " + cellType2, countsRefMarker[i]);
//                } else {
//                    resultsTable.addValue("No of " + cellType1 + " around " + cellType2, 0);
//                }
//            } else {
//                resultsTable.addLabel("");
//                resultsTable.addValue(cellType2 + "_id", "");
//                resultsTable.addValue("No of " + cellType1 + " around " + cellType2, 0);
//            }
//
//            if (i < cell1Names.length) {
//                resultsTable.addValue(cellType1 + "_id", cell1Names[i]);
//                if (i < countsMarkerRef.length) {
//                    resultsTable.addValue("No of " + cellType2 + " around " + cellType1, countsMarkerRef[i]);
//                } else {
//                    resultsTable.addValue("No of " + cellType2 + " around " + cellType1, 0);
//                }
//            } else {
//                resultsTable.addValue(cellType1 + "_id", "");
//                resultsTable.addValue("No of " + cellType2 + " around " + cellType1, 0);
//            }
//        }
//
//        String tablePath = spatialSavePath + "Neighbour_count_" + cellType1 + "_" + cellType2 + ".csv";
//        resultsTable.save(tablePath);
//
//        // Save parametric images if requested
//        if (saveParametricImage) {
//            ImagePlus overlapCell1 = getParametricImage(noNeighboursRefMarker, cell2, cellType1, cellType2, clij2);
//            IJ.saveAs(overlapCell1, "Tiff", spatialSavePath + overlapCell1.getTitle());
//            overlapCell1.close();
//
//            ImagePlus overlapCell2 = getParametricImage(noNeighboursMarkerRef, cell1, cellType2, cellType1, clij2);
//            IJ.saveAs(overlapCell2, "Tiff", spatialSavePath + overlapCell2.getTitle());
//            overlapCell2.close();
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
//    // ref_img is Hu and should label all cells, marker_img should be a subset
//    private static double[] countRefAroundMarker(String refImg, String markerImg, int dilateRadius,
//                                                 String gangliaBinary, String cellType1, String cellType2, CLIJ2 clij2) {
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
//        // Dilate cells in ref_img
//        ClearCLBuffer refDilate = clij2.create(refBuffer);
//        clij2.dilateLabels(refBuffer, refDilate, dilateRadius);
//
//        ClearCLBuffer refNeighbourCount = clij2.create(refBuffer);
//
//        if (!gangliaBinary.equals("NA")) {
//            ImagePlus gangliaImg = WindowManager.getImage(gangliaBinary);
//            if (gangliaImg != null) {
//                ClearCLBuffer gangliaBuffer = clij2.push(gangliaImg);
//                ClearCLBuffer refDilateRestricted = clij2.create(refBuffer);
//                clij2.multiplyImages(refDilate, gangliaBuffer, refDilateRestricted);
//
//                // Get neighbor count for each cell in ref_img
//                clij2.touchingNeighborCountMap(refDilateRestricted, refNeighbourCount);
//
//                gangliaBuffer.close();
//                refDilateRestricted.close();
//            } else {
//                clij2.touchingNeighborCountMap(refDilate, refNeighbourCount);
//            }
//        } else {
//            clij2.touchingNeighborCountMap(refDilate, refNeighbourCount);
//        }
//
//        ClearCLBuffer markerCentroid = clij2.create(markerBuffer);
//        clij2.reduceLabelsToCentroids(markerBuffer, markerCentroid);
//
//        clij2.statisticsOfBackgroundAndLabelledPixels(refNeighbourCount, markerCentroid);
//
//        ResultsTable results = ResultsTable.getResultsTable();
//        double[] noNeighbours = results.getColumn("MINIMUM_INTENSITY");
//        if (noNeighbours.length > 0) {
//            noNeighbours[0] = 0;
//        }
//
//        // Cleanup
//        refBuffer.close();
//        markerBuffer.close();
//        refDilate.close();
//        refNeighbourCount.close();
//        markerCentroid.close();
//
//        IJ.run("Clear Results");
//        return noNeighbours;
//    }
//
//    // How many cells in marker_img around ref
//    private static double[] countMarkerAroundRef(String refImg, String markerImg, int dilateRadius,
//                                                 String gangliaBinary, CLIJ2 clij2) {
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
//        // Dilate cells in ref_img
//        ClearCLBuffer refDilateInit = clij2.create(refBuffer);
//        clij2.dilateLabels(refBuffer, refDilateInit, dilateRadius);
//
//        ClearCLBuffer refDilate;
//
//        if (!gangliaBinary.equals("NA")) {
//            ImagePlus gangliaImg = WindowManager.getImage(gangliaBinary);
//            if (gangliaImg != null) {
//                ClearCLBuffer gangliaBuffer = clij2.push(gangliaImg);
//                refDilate = clij2.create(refBuffer);
//                clij2.multiplyImages(refDilateInit, gangliaBuffer, refDilate);
//                gangliaBuffer.close();
//            } else {
//                refDilate = refDilateInit;
//            }
//        } else {
//            refDilate = refDilateInit;
//        }
//
//        IJ.run("Clear Results");
//
//        // Get value of marker corresponding to ref_img
//        clij2.statisticsOfLabelledPixels(refDilate, markerBuffer);
//        ResultsTable results = ResultsTable.getResultsTable();
//        double[] markerLabelRefIds = results.getColumn("MAXIMUM_INTENSITY");
//
//        IJ.run("Clear Results");
//
//        // Get ids of all cells in ref_img
//        clij2.statisticsOfBackgroundAndLabelledPixels(refDilate, refDilate);
//        results = ResultsTable.getResultsTable();
//        double[] refIds = results.getColumn("IDENTIFIER");
//
//        IJ.run("Clear Results");
//
//        // Pull dilated image to ImageJ for Region Adjacency Graph
//        ImagePlus dilatedImg = clij2.pull(refDilate);
//        dilatedImg.show();
//        String dilatedTitle = dilatedImg.getTitle();
//
//        IJ.run(dilatedImg, "Region Adjacency Graph", "");
//        String ragTableName = dilatedTitle + "-RAG";
//
//        // Wait for the table to be created
//        try {
//            Thread.sleep(5);
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//        }
//
//        // Process the RAG table
//        ResultsTable ragTable = ResultsTable.getResultsTable(ragTableName);
//        if (ragTable == null) {
//            // If the named table doesn't exist, try the default Results table
//            ragTable = ResultsTable.getResultsTable();
//        }
//
//        double[] counts = new double[refIds.length];
//
//        if (ragTable != null && ragTable.getCounter() > 0) {
//            double[] label1 = ragTable.getColumn("Label 1");
//            double[] label2 = ragTable.getColumn("Label 2");
//
//            // Count neighbors for each label
//            for (int i = 0; i < label1.length; i++) {
//                int neighborMarker = valueInArray(markerLabelRefIds, label2[i]);
//                if (neighborMarker == 1) {
//                    int idx = (int) label1[i];
//                    if (idx < counts.length) {
//                        counts[idx] += 1;
//                    }
//                }
//            }
//
//            for (int i = 0; i < label2.length; i++) {
//                int neighborMarker = valueInArray(markerLabelRefIds, label1[i]);
//                if (neighborMarker == 1) {
//                    int idx = (int) label2[i];
//                    if (idx < counts.length) {
//                        counts[idx] += 1;
//                    }
//                }
//            }
//        }
//
//        IJ.run("Clear Results");
//
//        // Close the dilated image and RAG table
//        dilatedImg.close();
//        if (WindowManager.getWindow(ragTableName) != null) {
//            IJ.selectWindow(ragTableName);
//            IJ.run("Close");
//        }
//
//        // Cleanup GPU buffers
//        refBuffer.close();
//        markerBuffer.close();
//        if (refDilateInit != refDilate) {
//            refDilateInit.close();
//        }
//        refDilate.close();
//
//        return counts;
//    }
//
//    // Check if value is in array, return 1 if found, 0 otherwise
//    private static int valueInArray(double[] arr, double val) {
//        for (double value : arr) {
//            if (value == val) {
//                return 1;
//            }
//        }
//        return 0;
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
//    private static double[] removeFirstElement(double[] array) {
//        if (array.length <= 1) {
//            return new double[0];
//        }
//        double[] result = new double[array.length - 1];
//        System.arraycopy(array, 1, result, 0, array.length - 1);
//        return result;
//    }
//}






//package Analysis;
//
//import ij.*;
//import ij.measure.ResultsTable;
//import ij.plugin.frame.RoiManager;
//import java.io.File;
//
//public class SpatialHuMarker {
//
//    public static void execute(String cellType1, String cell1, String cellType2, String cell2,
//                               String gangliaBinaryOrig, String savePath, double labelDilation,
//                               boolean saveParametricImage, double pixelWidth, String roiLocationCell1) {
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
//        // Count ref around marker (Hu around specific marker)
//        double[] noNeighboursRefMarker = countRefAroundMarker(cell1, cell2, labelDilationPixels, gangliaBinary, cellType1, cellType2);
//        double[] countsRefMarker = removeBackgroundIndex(noNeighboursRefMarker);
//
//        // Count marker around ref (specific marker around Hu)
//        double[] noNeighboursMarkerRef = countMarkerAroundRef(cell1, cell2, labelDilationPixels, gangliaBinary);
//        double[] countsMarkerRef = removeBackgroundIndex(noNeighboursMarkerRef);
//
//        roiManager.reset();
//        IJ.run("Clear Results");
//
//        String tableName = "Neighbour_count_" + cellType1 + "_" + cellType2;
//        String tablePath = savePath + fs + tableName + ".csv";
//
//        String[] cell1Names = getRoiLabels(roiLocationCell1, cell1);
//        String[] cell2Names = getRoiLabels(roiLocationCell1, cell2); // Note: using cell1 ROI location for both as per original macro
//
//        // Create and save results table
//        ResultsTable neighbourTable = new ResultsTable();
//        neighbourTable.setDefaultHeadings();
//
//        // Add data for both cell types
//        int maxRows = Math.max(cell1Names.length, cell2Names.length);
//        for (int i = 0; i < maxRows; i++) {
//            neighbourTable.incrementCounter();
//
//            if (i < cell2Names.length) {
//                neighbourTable.addValue(cellType2 + "_id", cell2Names[i]);
//            }
//            if (i < countsRefMarker.length) {
//                neighbourTable.addValue("No of " + cellType1 + " around " + cellType2, countsRefMarker[i]);
//            }
//            if (i < cell1Names.length) {
//                neighbourTable.addValue(cellType1 + "_id", cell1Names[i]);
//            }
//            if (i < countsMarkerRef.length) {
//                neighbourTable.addValue("No of " + cellType2 + " around " + cellType1, countsMarkerRef[i]);
//            }
//        }
//
//        try {
//            neighbourTable.save(tablePath);
//        } catch (Exception e) {
//            IJ.error("Could not save table: " + e.getMessage());
//        }
//
//        if (saveParametricImage) {
//            String overlapCell1 = getParametricImg(noNeighboursRefMarker, cell2, cellType1, cellType2);
//            String overlapCell2 = getParametricImg(noNeighboursMarkerRef, cell1, cellType2, cellType1);
//
//            ImagePlus paramImg1 = WindowManager.getImage(overlapCell1);
//            if (paramImg1 != null) {
//                IJ.saveAsTiff(paramImg1, savePath + fs + overlapCell1);
//                paramImg1.close();
//            }
//
//            ImagePlus paramImg2 = WindowManager.getImage(overlapCell2);
//            if (paramImg2 != null) {
//                IJ.saveAsTiff(paramImg2, savePath + fs + overlapCell2);
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
//    // Ref_img is Hu and should label all cells, marker_img should be a subset
//    private static double[] countRefAroundMarker(String refImg, String markerImg, int dilateRadius, String gangliaBinary, String cellType1, String cellType2) {
//        IJ.run("Clear Results");
//        IJ.run("CLIJ2 Macro Extensions", "cl_device=");
//
//        // Push images to GPU
//        IJ.run("CLIJ2 push [" + refImg + "]");
//        IJ.run("CLIJ2 push [" + markerImg + "]");
//
//        // Dilate cells in ref_img
//        IJ.run("CLIJ2 dilateLabels", "input=" + refImg + " destination=ref_dilate radius=" + dilateRadius);
//
//        if (!gangliaBinary.equals("NA")) {
//            IJ.run("CLIJ2 push [" + gangliaBinary + "]");
//            IJ.run("CLIJ2 multiplyImages", "factor1=ref_dilate factor2=" + gangliaBinary + " destination=ref_dilate_ganglia_restrict");
//            IJ.run("CLIJ2 touchingNeighborCountMap", "input=ref_dilate_ganglia_restrict destination=ref_neighbour_count");
//        } else {
//            IJ.run("CLIJ2 touchingNeighborCountMap", "input=ref_dilate destination=ref_neighbour_count");
//        }
//
//        IJ.run("CLIJ2 reduceLabelsToCentroids", "input=" + markerImg + " destination=marker_img_centroid");
//        IJ.run("CLIJ2 statisticsOfBackgroundAndLabelledPixels", "input=ref_neighbour_count labelmap=marker_img_centroid");
//
//        ResultsTable rt = ResultsTable.getResultsTable();
//        double[] noNeighbours = new double[rt.getCounter()];
//
//        for (int i = 0; i < rt.getCounter(); i++) {
//            noNeighbours[i] = rt.getValue("MINIMUM_INTENSITY", i);
//        }
//
//        IJ.run("Clear Results");
//
//        if (noNeighbours.length > 0) {
//            noNeighbours[0] = 0;
//        }
//
//        // Release GPU memory
//        IJ.run("CLIJ2 release [ref_neighbour_count]");
//        IJ.run("CLIJ2 release [marker_img_centroid]");
//        if (!gangliaBinary.equals("NA")) {
//            IJ.run("CLIJ2 release [" + gangliaBinary + "]");
//        }
//
//        return noNeighbours;
//    }
//
//    // How many cells in marker_img around ref
//    private static double[] countMarkerAroundRef(String refImg, String markerImg, int dilateRadius, String gangliaBinary) {
//        IJ.run("CLIJ2 Macro Extensions", "cl_device=");
//
//        // Push images to GPU
//        IJ.run("CLIJ2 push [" + refImg + "]");
//        IJ.run("CLIJ2 push [" + markerImg + "]");
//
//        // Dilate cells in ref_img
//        IJ.run("CLIJ2 dilateLabels", "input=" + refImg + " destination=ref_dilate_init radius=" + dilateRadius);
//
//        String refDilate;
//        if (!gangliaBinary.equals("NA")) {
//            IJ.run("CLIJ2 push [" + gangliaBinary + "]");
//            IJ.run("CLIJ2 multiplyImages", "factor1=ref_dilate_init factor2=" + gangliaBinary + " destination=ref_dilate");
//            refDilate = "ref_dilate";
//        } else {
//            refDilate = "ref_dilate_init";
//        }
//
//        IJ.run("Clear Results");
//
//        // Get statistics of marker corresponding to ref_img
//        IJ.run("CLIJ2 statisticsOfLabelledPixels", "input=" + refDilate + " labelmap=" + markerImg);
//
//        ResultsTable rt = ResultsTable.getResultsTable();
//        double[] markerLabelRefIds = new double[rt.getCounter()];
//        for (int i = 0; i < rt.getCounter(); i++) {
//            markerLabelRefIds[i] = rt.getValue("MAXIMUM_INTENSITY", i);
//        }
//
//        IJ.run("Clear Results");
//
//        // Get IDs of all cells in ref_img
//        IJ.run("CLIJ2 statisticsOfBackgroundAndLabelledPixels", "input=" + refDilate + " labelmap=" + refDilate);
//
//        rt = ResultsTable.getResultsTable();
//        double[] refIds = new double[rt.getCounter()];
//        for (int i = 0; i < rt.getCounter(); i++) {
//            refIds[i] = rt.getValue("IDENTIFIER", i);
//        }
//
//        IJ.run("Clear Results");
//        IJ.run("CLIJ2 pull [" + refDilate + "]");
//
//        ImagePlus refDilateImg = WindowManager.getImage(refDilate);
//        if (refDilateImg != null) {
//            IJ.run(refDilateImg, "Region Adjacency Graph", "  image=" + refDilate);
//
//            String ragTableName = refDilate + "-RAG";
//            try {
//                Thread.sleep(5);
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//            }
//
//            ImagePlus ragWindow = WindowManager.getImage(ragTableName);
//            if (ragWindow != null) {
//                ResultsTable ragTable = ResultsTable.getResultsTable(ragTableName);
//                if (ragTable != null) {
//                    double[] label1 = getColumnFromTable(ragTable, "Label 1");
//                    double[] label2 = getColumnFromTable(ragTable, "Label 2");
//
//                    // Array of zeros with length ref_ids
//                    double[] counts = new double[refIds.length];
//
//                    // Process label adjacencies
//                    for (int i = 0; i < label1.length; i++) {
//                        int neighbourMarker = valueInArray(markerLabelRefIds, label2[i]);
//                        if (neighbourMarker == 1) {
//                            int idx = (int) label1[i];
//                            if (idx < counts.length) {
//                                counts[idx] += 1;
//                            }
//                        }
//                    }
//
//                    for (int i = 0; i < label2.length; i++) {
//                        int neighbourMarker = valueInArray(markerLabelRefIds, label1[i]);
//                        if (neighbourMarker == 1) {
//                            int idx = (int) label2[i];
//                            if (idx < counts.length) {
//                                counts[idx] += 1;
//                            }
//                        }
//                    }
//
//                    IJ.run("Clear Results");
//                    refDilateImg.close();
//
//                    ImagePlus ragImg = WindowManager.getImage(ragTableName);
//                    if (ragImg != null) {
//                        ragImg.close();
//                    }
//
//                    return counts;
//                }
//            }
//        }
//
//        return new double[0];
//    }
//
//    private static double[] getColumnFromTable(ResultsTable table, String columnName) {
//        double[] column = new double[table.getCounter()];
//        for (int i = 0; i < table.getCounter(); i++) {
//            column[i] = table.getValue(columnName, i);
//        }
//        return column;
//    }
//
//    private static int valueInArray(double[] array, double value) {
//        for (double v : array) {
//            if (v == value) {
//                return 1;
//            }
//        }
//        return 0;
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
//    private static boolean isClij2Available() {
//        try {
//            IJ.run("CLIJ2 Macro Extensions", "cl_device=");
//            return true;
//        } catch (Exception e) {
//            return false;
//        }
//    }
//}
