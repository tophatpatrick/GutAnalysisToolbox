package Analysis;

import ij.*;
import ij.process.ImageProcessor;
import ij.measure.ResultsTable;
import net.haesleinhuepf.clij2.CLIJ2;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;

import java.io.File;

public class SpatialSingleCellType {

    private static final String FILE_SEPARATOR = File.separator;

    public static void execute(String cellType, String cellImage, String gangliaBinary,
                               String savePath, double labelDilation, boolean saveParametricImage,
                               double pixelWidth, String roiPath) throws Exception {

        CLIJ2 clij2 = CLIJ2.getInstance();
        int labelDilationPixels = (int) Math.round(labelDilation / pixelWidth);

        String spatialSavePath = savePath + FILE_SEPARATOR + "spatial_analysis" + FILE_SEPARATOR;
        new File(spatialSavePath).mkdirs();

        ImagePlus cellImg = WindowManager.getImage(cellImage);
        if (cellImg == null) {
            IJ.error("Cell image not found: " + cellImage);
            return;
        }

        int width = cellImg.getWidth();
        int height = cellImg.getHeight();
        ImageProcessor labelIp = cellImg.getProcessor();
        int maxLabel = (int) labelIp.getStatistics().max;

        // Push label image to GPU
        ClearCLBuffer cellBuffer = clij2.push(cellImg);

        // Dilate labels
        ClearCLBuffer dilated = clij2.create(cellBuffer);
        clij2.dilateLabels(cellBuffer, dilated, labelDilationPixels);

        // Compute touching neighbor map
        ClearCLBuffer neighborMap = clij2.create(cellBuffer);
        clij2.touchingNeighborCountMap(dilated, neighborMap);

        // Pull neighbor map back to ImageJ (no .show(), so no window)
        ImagePlus neighborImg = clij2.pull(neighborMap);
        ImageProcessor neighborIp = neighborImg.getProcessor();

        // Prepare CSV
        ResultsTable outTable = new ResultsTable();
        for (int label = 1; label <= maxLabel; label++) {
            int neighborCount = 0;
            outer:
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if ((int) labelIp.getPixel(x, y) == label) {
                        neighborCount = (int) neighborIp.getPixel(x, y);
                        break outer;
                    }
                }
            }
            outTable.incrementCounter();
            outTable.addLabel(String.valueOf(label));
            outTable.addValue("No of cells around " + cellType, neighborCount);
        }

        // Save CSV
        String csvPath = spatialSavePath + "Neighbour_count_" + cellType + ".csv";
        outTable.save(csvPath);

        // Save labeled cell image (hidden)
        IJ.saveAs(cellImg, "Tiff", spatialSavePath + "cell_labels.tif");

        // Optional parametric image
        if (saveParametricImage) {
            float[] neighborArray = new float[maxLabel];
            for (int i = 0; i < maxLabel; i++) neighborArray[i] = (float) outTable.getValueAsDouble(i, 1);

            ClearCLBuffer vectorNeighbours = clij2.pushArray(neighborArray, neighborArray.length, 1, 1);
            ClearCLBuffer paramImg = clij2.create(cellBuffer);
            clij2.replaceIntensities(cellBuffer, vectorNeighbours, paramImg);

            ImagePlus paramResult = clij2.pull(paramImg);

            // Show parametric image only if requested
            paramResult.setTitle(cellType + "_parametric");
            paramResult.show();
            IJ.run(paramResult, "Fire", "");


            // Save parametric image
            IJ.saveAs(paramResult, "Tiff", spatialSavePath + "cell_labels_parametric.tif");

            vectorNeighbours.close();
            paramImg.close();
        }

        // Cleanup GPU buffers
        cellBuffer.close();
        dilated.close();
        neighborMap.close();
        neighborImg.close(); // safe to close, never showed

        IJ.log("Saved cell_labels.tif and neighbor counts CSV for " + cellType);
    }
}



//
//    /**
//     * Calculate nearest neighbors for single cell type using CLIJ2
//     */
//    private static double[] nearestNeighbourSingleCell(String refImg, int dilateRadius,
//                                                       String gangliaBinary, String cellType,
//                                                       CLIJ2 clij2) {
//        IJ.run("Clear Results");
//
//        ImagePlus refImage = WindowManager.getImage(refImg);
//        if (refImage == null) {
//            IJ.error("Cannot find reference image: " + refImg);
//            return new double[0];
//        }
//
//        // Push reference image to GPU
//        ClearCLBuffer refBuffer = clij2.push(refImage);
//
//        // Dilate labels
//        ClearCLBuffer refDilate = clij2.create(refBuffer);
//        clij2.dilateLabels(refBuffer, refDilate, dilateRadius);
//
//        ClearCLBuffer neighbourCount;
//
//        if (!gangliaBinary.equals("NA")) {
//            ImagePlus gangliaImg = WindowManager.getImage(gangliaBinary);
//            if (gangliaImg != null) {
//                ClearCLBuffer gangliaBuffer = clij2.push(gangliaImg);
//                ClearCLBuffer refDilateGangliaRestrict = clij2.create(refBuffer);
//
//                // Multiply with ganglia mask to restrict to ganglia regions
//                clij2.multiplyImages(refDilate, gangliaBuffer, refDilateGangliaRestrict);
//
//                // Get neighbor count
//                neighbourCount = clij2.create(refBuffer);
//                clij2.touchingNeighborCountMap(refDilateGangliaRestrict, neighbourCount);
//
//                // Clean up intermediate buffers
//                gangliaBuffer.close();
//                refDilateGangliaRestrict.close();
//            } else {
//                neighbourCount = clij2.create(refBuffer);
//                clij2.touchingNeighborCountMap(refDilate, neighbourCount);
//            }
//        } else {
//            neighbourCount = clij2.create(refBuffer);
//            clij2.touchingNeighborCountMap(refDilate, neighbourCount);
//        }
//
//        // Reduce labels to centroids to avoid edge artifacts
//        ClearCLBuffer refImgCentroid = clij2.create(refBuffer);
//        clij2.reduceLabelsToCentroids(refBuffer, refImgCentroid);
//
//        // Get statistics
//        clij2.statisticsOfBackgroundAndLabelledPixels(neighbourCount, refImgCentroid);
//
//        // Read results
//        ResultsTable rt = ResultsTable.getResultsTable();
//        double[] noNeighbours = new double[rt.getCounter()];
//
//        for (int i = 0; i < rt.getCounter(); i++) {
//            noNeighbours[i] = rt.getValue("MIN", i);
//        }
//
//        IJ.run("Clear Results");
//
//        // Set background to 0
//        if (noNeighbours.length > 0) {
//            noNeighbours[0] = 0;
//        }
//
//        // Clean up GPU memory
//        refBuffer.close();
//        refDilate.close();
//        neighbourCount.close();
//        refImgCentroid.close();
//
//        return noNeighbours;
//    }
//
//    /**
//     * Generate parametric image by replacing label values with neighbor counts
//     */
//    private static String getParametricImage(double[] noNeighbours, String cellLabelImg,
//                                             String cellType, CLIJ2 clij2) {
//
//        ImagePlus cellImg = WindowManager.getImage(cellLabelImg);
//        if (cellImg == null) {
//            return null;
//        }
//
//        // Convert double array to float array for CLIJ2
//        float[] neighboursFloat = new float[noNeighbours.length];
//        for (int i = 0; i < noNeighbours.length; i++) {
//            neighboursFloat[i] = (float) noNeighbours[i];
//        }
//
//        // Push arrays and images to GPU
//        ClearCLBuffer cellBuffer = clij2.push(cellImg);
//        ClearCLBuffer vectorNeighbours = clij2.pushArray(neighboursFloat, neighboursFloat.length, 1, 1);
//        ClearCLBuffer parametricImg = clij2.create(cellBuffer);
//
//        // Replace intensities
//        clij2.replaceIntensities(cellBuffer, vectorNeighbours, parametricImg);
//
//        // Pull result back to ImageJ
//        ImagePlus result = clij2.pull(parametricImg);
//        String newName = cellType + "_neighbours";
//        result.setTitle(newName);
//        result.show();
//
//        // Apply Fire LUT
//        IJ.run("Fire");
//
//        // Clean up GPU memory
//        cellBuffer.close();
//        vectorNeighbours.close();
//        parametricImg.close();
//
//        return newName;
//    }
//
//    /**
//     * Rename ROI names in results table (equivalent to macro function)
//     */
//    private static void renameRoiNameResultTable() {
//        RoiManager roiManager = RoiManager.getInstance();
//        if (roiManager != null) {
//            roiManager.runCommand("UseNames", "true");
//        }
//
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
//                if (delimiter != -1) {
//                    String newLabel = oldLabel.substring(delimiter + 1);
//                    rt.setLabel(newLabel, i);
//                }
//            }
//        }
//    }
//}


//package Analysis;
//
//import ij.*;
//import ij.measure.ResultsTable;
//import ij.plugin.ImageCalculator;
//import ij.plugin.frame.RoiManager;
//import ij.process.ImageProcessor;
//import net.haesleinhuepf.clij2.CLIJ2;
//import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
//
//import java.io.File;
//import java.util.HashMap;
//import java.util.Map;
//
//public class SpatialSingleCellType {
//
//    public static void execute(String cellType1, String cell1, String gangliaBinaryOrig,
//                               String savePath, double labelDilation, boolean saveParametricImage,
//                               double pixelWidth, String roiLocationCell) {
//
//        IJ.run("Clear Results");
//
//        // Debug: Print all input parameters
//        IJ.log("=== DEBUG: Input Parameters ===");
//        IJ.log("cellType1: " + cellType1);
//        IJ.log("cell1: " + cell1);
//        IJ.log("gangliaBinaryOrig: " + gangliaBinaryOrig);
//        IJ.log("savePath: " + savePath);
//        IJ.log("labelDilation: " + labelDilation);
//        IJ.log("saveParametricImage: " + saveParametricImage);
//        IJ.log("pixelWidth: " + pixelWidth);
//        IJ.log("roiLocationCell: " + roiLocationCell);
//
//        // remove after testing
//        testFileWriting(savePath);
//
//        if (!isClij2Available()) {
//            IJ.error("CLIJ2 not available. Check Log for installation details");
//            return;
//        }
//
//        RoiManager roiManager = RoiManager.getInstance();
//        if (roiManager == null) {
//            roiManager = new RoiManager();
//        }
//        roiManager.reset();
//
//        IJ.log("Getting number of neighbours for " + cellType1);
//
//        // Convert to pixels
//        int labelDilationPixels = (int) Math.round(labelDilation / pixelWidth);
//        IJ.log("Label dilation in pixels: " + labelDilationPixels);
//
//        String fs = File.separator;
//        String fullSavePath = savePath + fs + "spatial_analysis" + fs;
//
//        // Debug: Check save path
//        IJ.log("=== DEBUG: File Paths ===");
//        IJ.log("Original savePath: " + savePath);
//        IJ.log("Full save path: " + fullSavePath);
//
//        File saveDir = new File(fullSavePath);
//        if (!saveDir.exists()) {
//            boolean created = saveDir.mkdirs();
//            IJ.log("Created directory: " + created + " at " + saveDir.getAbsolutePath());
//        } else {
//            IJ.log("Directory already exists: " + saveDir.getAbsolutePath());
//        }
//
//        ImagePlus gangliaDup = null;   // declare here, in method scope
//
//        // Binary image for ganglia
////        IJ.run("Options...", "iterations=1 count=1 black");
//        String gangliaBinary = "NA";
//
//        if (gangliaBinaryOrig != null && !gangliaBinaryOrig.equals("NA")) {
//            ImagePlus gangliaImg = WindowManager.getImage(gangliaBinaryOrig);
//            IJ.log("Ganglia image found: " + (gangliaImg != null));
//            if (gangliaImg != null) {
//                IJ.run(gangliaImg, "Select None", "");
//                IJ.run(gangliaImg, "Duplicate...", "title=ganglia_binary_dup");
//                gangliaDup = WindowManager.getImage("ganglia_binary_dup");
//                if (gangliaDup != null) {
//                    IJ.setThreshold(gangliaDup, 0.5, 65535);
//                    IJ.run(gangliaDup, "Convert to Mask", "");
//                    gangliaDup.setTitle("Ganglia_outline");
//                    gangliaBinary = gangliaDup.getTitle();
//                    IJ.run(gangliaDup, "Divide...", "value=255");
//                    gangliaDup.resetDisplayRange();
//                    IJ.run(gangliaDup, "Set...", "value=0 value=1");
//                    IJ.log("Processed ganglia binary: " + gangliaBinary);
//                }
//            }
//        } else {
//            IJ.log("No ganglia image provided");
//        }
//
//        // Debug: Check if cell image exists
//        ImagePlus cellImage = WindowManager.getImage(cell1);
//        IJ.log("Cell image found: " + (cellImage != null));
//        if (cellImage != null) {
//            IJ.log("Cell image dimensions: " + cellImage.getWidth() + "x" + cellImage.getHeight());
//        }
//
//        double[] neighbourCount = nearestNeighbourSingleCell(cell1, labelDilationPixels, gangliaBinary, cellType1);
//
//        // Debug: Check neighbor count results
//        IJ.log("=== DEBUG: Neighbor Count Results ===");
//        IJ.log("neighbourCount array length: " + neighbourCount.length);
//        if (neighbourCount.length > 0) {
//            IJ.log("First few values: ");
//            for (int i = 0; i < Math.min(5, neighbourCount.length); i++) {
//                IJ.log("  [" + i + "]: " + neighbourCount[i]);
//            }
//        }
//
//        // Remove background (index 0)
//        double[] neighbourCountNoBackground = new double[neighbourCount.length - 1];
//        System.arraycopy(neighbourCount, 1, neighbourCountNoBackground, 0, neighbourCount.length - 1);
//        IJ.log("neighbourCountNoBackground length: " + neighbourCountNoBackground.length);
//
//        String tableName = "Neighbour_count_" + cellType1;
//        String tablePath = fullSavePath + fs + tableName + ".csv";
//
//        IJ.log("=== DEBUG: CSV File Saving ===");
//        IJ.log("Table name: " + tableName);
//        IJ.log("Full table path: " + tablePath);
//
//        // Load ROIs and get cell names
//        try {
//            roiManager.runCommand("Open", roiLocationCell);
//            IJ.log("ROIs loaded successfully. Count: " + roiManager.getCount());
//
//            if (cellImage != null) {
//                IJ.run("Set Measurements...", "centroid display redirect=None decimal=3");
//                roiManager.deselect();
//                roiManager.runCommand("Measure");
//                renameRoiNameResultTable();
//
//                ResultsTable rt = ResultsTable.getResultsTable();
//                IJ.log("Results table entries: " + rt.getCounter());
//
//                String[] cellNames = new String[rt.getCounter()];
//                for (int i = 0; i < rt.getCounter(); i++) {
//                    cellNames[i] = rt.getLabel(i);
//                }
//                IJ.run("Clear Results");
//
//                // Create and save results table
//                ResultsTable neighbourTable = new ResultsTable();
//                int rowsToProcess = Math.min(cellNames.length, neighbourCountNoBackground.length);
//                IJ.log("Creating table with " + rowsToProcess + " rows");
//
//                for (int i = 0; i < rowsToProcess; i++) {
//                    neighbourTable.incrementCounter();
//                    neighbourTable.addLabel(cellNames[i]);
//                    neighbourTable.addValue("Neuron_id", cellNames[i]);
//                    neighbourTable.addValue("No of cells around " + cellType1, neighbourCountNoBackground[i]);
//                }
//
//                try {
//                    neighbourTable.save(tablePath);
//                    IJ.log("CSV SAVED SUCCESSFULLY to: " + tablePath);
//
//                    // Verify file exists
//                    File csvFile = new File(tablePath);
//                    IJ.log("File exists after save: " + csvFile.exists());
//                    if (csvFile.exists()) {
//                        IJ.log("File size: " + csvFile.length() + " bytes");
//                    }
//                } catch (Exception e) {
//                    IJ.error("Could not save CSV table: " + e.getMessage());
//                    IJ.log("Save error details: " + e.toString());
//                }
//            } else {
//                IJ.log("ERROR: Cell image is null, cannot process ROIs");
//            }
//        } catch (Exception e) {
//            IJ.log("Error loading ROIs or processing: " + e.getMessage());
//        }
//
//        // Debug parametric image saving
//        if (saveParametricImage) {
//            IJ.log("=== DEBUG: Parametric Image Saving ===");
//            try {
//                String neighbourCell1 = getParametricImg(neighbourCount, cell1, cellType1);
//                IJ.log("Parametric image created: " + neighbourCell1);
//
//                ImagePlus paramImg = WindowManager.getImage(neighbourCell1);
//                if (paramImg != null) {
//                    String paramPath = fullSavePath + fs + neighbourCell1 + ".tif";
//                    IJ.log("Saving parametric image to: " + paramPath);
//
//                    IJ.saveAsTiff(paramImg, paramPath);
//                    paramImg.close();
//
//                    // Verify file exists
//                    File tifFile = new File(paramPath);
//                    IJ.log("TIF file exists after save: " + tifFile.exists());
//                    if (tifFile.exists()) {
//                        IJ.log("TIF file size: " + tifFile.length() + " bytes");
//                    }
//                } else {
//                    IJ.log("ERROR: Parametric image is null");
//                }
//            } catch (Exception e) {
//                IJ.log("Error creating/saving parametric image: " + e.getMessage());
//            }
//        } else {
//            IJ.log("Parametric image saving disabled (saveParametricImage = false)");
//        }
//
//        // Cleanup
//        if (!gangliaBinary.equals("NA")) {
//            ImagePlus gangliaImg = WindowManager.getImage(gangliaBinary);
//            if (gangliaImg != null) {
//                gangliaImg.close();
//            }
//        }
//
//        IJ.log("=== DEBUG: Final Status ===");
//        IJ.log("Analysis completed for " + cellType1);
//        IJ.log("Check directory: " + fullSavePath);
//        IJ.log("Expected files:");
//        IJ.log("  - CSV: " + tablePath);
//        if (saveParametricImage) {
//            IJ.log("  - TIF: " + fullSavePath + cellType1 + "_neighbours.tif");
//        }
//
//        roiManager.reset();
//    }
//
//    private static double[] nearestNeighbourSingleCell(String refImg, int dilateRadius, String gangliaBinary, String cellType1) {
//        IJ.log("=== DEBUG: nearestNeighbourSingleCell ===");
//        IJ.log("refImg: " + refImg);
//        IJ.log("dilateRadius: " + dilateRadius);
//        IJ.log("gangliaBinary: " + gangliaBinary);
//        IJ.log("cellType1: " + cellType1);
//
//        IJ.run("Clear Results");
//
//        CLIJ2 clij2 = null;
//        ClearCLBuffer ref = null;
//        ClearCLBuffer refDilate = null;
//        ClearCLBuffer refDilateGangliaRestrict = null;
//        ClearCLBuffer refNeighbourCount = null;
//        ClearCLBuffer refImgCentroid = null;
//        ClearCLBuffer gangliaBuf = null;
//
//        try {
//            // Get CLIJ2 instance
//            clij2 = CLIJ2.getInstance();
//            if (clij2 == null) {
//                IJ.log("ERROR: Could not get CLIJ2 instance.");
//                return new double[0];
//            }
//            IJ.log("CLIJ2 initialized for neighbor analysis");
//
//            // Get the reference image
//            ImagePlus refImp = WindowManager.getImage(refImg);
//            if (refImp == null) {
//                IJ.log("ERROR: Reference image not found: " + refImg);
//                return new double[0];
//            }
//
//            // Push reference labels to GPU
//            IJ.log("Pushing image to GPU: " + refImg);
//            ref = clij2.push(refImp);
//
//            // Dilate labels
//            IJ.log("Dilating labels with radius: " + dilateRadius);
//            refDilate = clij2.create(ref);
//            clij2.dilateLabels(ref, refDilate, dilateRadius);
//
//            ClearCLBuffer finalDilated = refDilate;
//
//            if (!gangliaBinary.equals("NA")) {
//                IJ.log("Processing with ganglia restriction");
//                ImagePlus gangliaImg = WindowManager.getImage(gangliaBinary);
//                if (gangliaImg != null) {
//                    gangliaBuf = clij2.push(gangliaImg);
//                    refDilateGangliaRestrict = clij2.create(ref);
//                    clij2.multiplyImages(refDilate, gangliaBuf, refDilateGangliaRestrict);
//                    finalDilated = refDilateGangliaRestrict;
//                }
//            } else {
//                IJ.log("Processing without ganglia restriction");
//            }
//
//            // Count touching neighbors
//            refNeighbourCount = clij2.create(ref);
//            clij2.touchingNeighborCountMap(finalDilated, refNeighbourCount);
//
//            // Reduce labels to centroids
//            IJ.log("Reducing labels to centroids");
//            refImgCentroid = clij2.create(ref);
//            clij2.reduceLabelsToCentroids(ref, refImgCentroid);
//
//            // Compute statistics
//            IJ.log("Computing statistics");
//            double[][] stats = clij2.statisticsOfBackgroundAndLabelledPixels(refNeighbourCount, refImgCentroid);
//
//            if (stats == null || stats.length == 0) {
//                IJ.log("WARNING: No statistics returned!");
//                return new double[0];
//            }
//
//            // Extract minimum intensity values (neighbor counts)
//            // The statistics array is 2D: stats[label_index][statistic_index]
//            // statistic_index: 0=min, 1=max, 2=mean, 3=stdDev
//            // We want the minimum values (index 0) for each label
//            int numLabels = stats.length;
//            double[] noNeighbours = new double[numLabels];
//
//            for (int i = 0; i < numLabels; i++) {
//                if (stats[i].length > 0) {
//                    noNeighbours[i] = stats[i][0]; // minimum intensity for each label
//                    if (i <= 5) { // Log first few values
//                        IJ.log("Neighbor count [" + i + "]: " + noNeighbours[i]);
//                    }
//                } else {
//                    noNeighbours[i] = 0;
//                }
//            }
//
//            // Set background to 0
//            if (noNeighbours.length > 0) {
//                noNeighbours[0] = 0;
//            }
//
//            IJ.log("nearestNeighbourSingleCell completed. Returning " + noNeighbours.length + " values");
//            return noNeighbours;
//
//        } catch (Exception e) {
//            IJ.log("ERROR in nearestNeighbourSingleCell: " + e.getMessage());
//            IJ.log("Stack trace: ");
//            e.printStackTrace();
//            return new double[0];
//        } finally {
//            // Release GPU memory
//            if (clij2 != null) {
//                IJ.log("Releasing GPU memory");
//                if (ref != null) ref.close();
//                if (refDilate != null) refDilate.close();
//                if (refDilateGangliaRestrict != null) refDilateGangliaRestrict.close();
//                if (refNeighbourCount != null) refNeighbourCount.close();
//                if (refImgCentroid != null) refImgCentroid.close();
//                if (gangliaBuf != null) gangliaBuf.close();
//            }
//        }
//    }
//
//    private static String getParametricImg(double[] noNeighbours, String cellLabelImg, String cellType1) {
//        CLIJ2 clij2 = null;
//        ClearCLBuffer vectorNeighbours = null;
//        ClearCLBuffer cellLabels = null;
//        ClearCLBuffer parametricImg = null;
//
//        try {
//            // Get CLIJ2 instance
//            clij2 = CLIJ2.getInstance();
//            if (clij2 == null) {
//                IJ.log("ERROR: Could not get CLIJ2 instance for parametric image.");
//                return null;
//            }
//
//            // Get the cell label image
//            ImagePlus cellLabelImp = WindowManager.getImage(cellLabelImg);
//            if (cellLabelImp == null) {
//                IJ.log("ERROR: Cell label image not found: " + cellLabelImg);
//                return null;
//            }
//
//            // Push cell labels to GPU
//            cellLabels = clij2.push(cellLabelImp);
//
//            // Create vector with neighbor counts
//            // Convert double array to float array for CLIJ2
//            float[] neighboursFloat = new float[noNeighbours.length];
//            for (int i = 0; i < noNeighbours.length; i++) {
//                neighboursFloat[i] = (float) noNeighbours[i];
//            }
//
//            // Push array as vector
//            vectorNeighbours = clij2.pushArray(neighboursFloat, neighboursFloat.length, 1, 1);
//
//            // Replace intensities
//            parametricImg = clij2.create(cellLabels);
//            clij2.replaceIntensities(cellLabels, vectorNeighbours, parametricImg);
//
//            // Pull result back to ImageJ
//            ImagePlus result = clij2.pull(parametricImg);
//
//            String newName = cellType1 + "_neighbours";
//            result.setTitle(newName);
//            IJ.run(result, "Fire", "");
//
//            return newName;
//
//        } catch (Exception e) {
//            IJ.log("ERROR in getParametricImg: " + e.getMessage());
//            return null;
//        } finally {
//            // Release GPU memory
//            if (clij2 != null) {
//                if (vectorNeighbours != null) vectorNeighbours.close();
//                if (cellLabels != null) cellLabels.close();
//                if (parametricImg != null) parametricImg.close();
//            }
//        }
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
//    private static boolean isClij2Available() {
//        try {
//            CLIJ2 clij2 = CLIJ2.getInstance();
//            return clij2 != null;
//        } catch (Exception e) {
//            IJ.log("CLIJ2 not available: " + e.getMessage());
//            return false;
//        }
//    }
//
//    private static void testFileWriting(String savePath) {
//        try {
//            String fs = File.separator;
//            String testDir = savePath + fs + "spatial_analysis" + fs;
//            File saveDir = new File(testDir);
//
//            if (!saveDir.exists()) {
//                boolean created = saveDir.mkdirs();
//                IJ.log("Test: Directory created: " + created);
//            }
//
//            // Test writing a simple text file
//            String testFile = testDir + "test.txt";
//            java.io.FileWriter writer = new java.io.FileWriter(testFile);
//            writer.write("Test file - if you see this, file writing works!");
//            writer.close();
//
//            File testF = new File(testFile);
//            if (testF.exists()) {
//                IJ.log("SUCCESS: Test file created at " + testFile);
//                IJ.log("File size: " + testF.length() + " bytes");
//                // Clean up
//                testF.delete();
//            } else {
//                IJ.log("ERROR: Test file was not created");
//            }
//
//        } catch (Exception e) {
//            IJ.log("ERROR: Cannot write to directory: " + e.getMessage());
//        }
//    }
//}