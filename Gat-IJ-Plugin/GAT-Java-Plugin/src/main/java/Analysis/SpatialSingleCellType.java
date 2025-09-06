package Analysis;

import ij.*;
import ij.measure.ResultsTable;
import ij.plugin.ImageCalculator;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class SpatialSingleCellType {

    // Add this debugging version to your SpatialSingleCellType.execute method

    public static void execute(String cellType1, String cell1, String gangliaBinaryOrig,
                               String savePath, double labelDilation, boolean saveParametricImage,
                               double pixelWidth, String roiLocationCell) {

        IJ.run("Clear Results");

        // Debug: Print all input parameters
        IJ.log("=== DEBUG: Input Parameters ===");
        IJ.log("cellType1: " + cellType1);
        IJ.log("cell1: " + cell1);
        IJ.log("gangliaBinaryOrig: " + gangliaBinaryOrig);
        IJ.log("savePath: " + savePath);
        IJ.log("labelDilation: " + labelDilation);
        IJ.log("saveParametricImage: " + saveParametricImage);
        IJ.log("pixelWidth: " + pixelWidth);
        IJ.log("roiLocationCell: " + roiLocationCell);

        // remove after testing
        testFileWriting(savePath);

        // TEMPORARILY BYPASS CLIJ2 CHECK FOR DEBUGGING
        boolean bypassCheck = true;

        if (!bypassCheck && !isClij2Available()) {
            IJ.error("CLIJ not installed. Check Log for installation details");
            return;
        }

        IJ.log("Bypassing CLIJ2 check - attempting to run anyway...");

        RoiManager roiManager = RoiManager.getInstance();
        if (roiManager == null) {
            roiManager = new RoiManager();
        }
        roiManager.reset();

        IJ.run("CLIJ2 Macro Extensions", "cl_device=");
        IJ.log("Getting number of neighbours for " + cellType1);

        // Convert to pixels
        int labelDilationPixels = (int) Math.round(labelDilation / pixelWidth);
        IJ.log("Label dilation in pixels: " + labelDilationPixels);

        String fs = File.separator;
        String fullSavePath = savePath + fs + "spatial_analysis" + fs;

        // Debug: Check save path
        IJ.log("=== DEBUG: File Paths ===");
        IJ.log("Original savePath: " + savePath);
        IJ.log("Full save path: " + fullSavePath);

        File saveDir = new File(fullSavePath);
        if (!saveDir.exists()) {
            boolean created = saveDir.mkdirs();
            IJ.log("Created directory: " + created + " at " + saveDir.getAbsolutePath());
        } else {
            IJ.log("Directory already exists: " + saveDir.getAbsolutePath());
        }

        // Binary image for ganglia
        IJ.run("Options...", "iterations=1 count=1 black");
        String gangliaBinary = "NA";

        if (gangliaBinaryOrig != null && !gangliaBinaryOrig.equals("NA")) {
            ImagePlus gangliaImg = WindowManager.getImage(gangliaBinaryOrig);
            IJ.log("Ganglia image found: " + (gangliaImg != null));
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
                    IJ.log("Processed ganglia binary: " + gangliaBinary);
                }
            }
        } else {
            IJ.log("No ganglia image provided");
        }

        // Debug: Check if cell image exists
        ImagePlus cellImage = WindowManager.getImage(cell1);
        IJ.log("Cell image found: " + (cellImage != null));
        if (cellImage != null) {
            IJ.log("Cell image dimensions: " + cellImage.getWidth() + "x" + cellImage.getHeight());
        }

        double[] neighbourCount = nearestNeighbourSingleCell(cell1, labelDilationPixels, gangliaBinary, cellType1);

        // Debug: Check neighbor count results
        IJ.log("=== DEBUG: Neighbor Count Results ===");
        IJ.log("neighbourCount array length: " + neighbourCount.length);
        if (neighbourCount.length > 0) {
            IJ.log("First few values: ");
            for (int i = 0; i < Math.min(5, neighbourCount.length); i++) {
                IJ.log("  [" + i + "]: " + neighbourCount[i]);
            }
        }

        // Remove background (index 0)
        double[] neighbourCountNoBackground = new double[neighbourCount.length - 1];
        System.arraycopy(neighbourCount, 1, neighbourCountNoBackground, 0, neighbourCount.length - 1);
        IJ.log("neighbourCountNoBackground length: " + neighbourCountNoBackground.length);

        String tableName = "Neighbour_count_" + cellType1;
        String tablePath = fullSavePath + fs + tableName + ".csv";

        IJ.log("=== DEBUG: CSV File Saving ===");
        IJ.log("Table name: " + tableName);
        IJ.log("Full table path: " + tablePath);

        // Load ROIs and get cell names
        try {
            roiManager.runCommand("Open", roiLocationCell);
            IJ.log("ROIs loaded successfully. Count: " + roiManager.getCount());

            if (cellImage != null) {
                IJ.run("Set Measurements...", "centroid display redirect=None decimal=3");
                roiManager.deselect();
                roiManager.runCommand("Measure");
                renameRoiNameResultTable();

                ResultsTable rt = ResultsTable.getResultsTable();
                IJ.log("Results table entries: " + rt.getCounter());

                String[] cellNames = new String[rt.getCounter()];
                for (int i = 0; i < rt.getCounter(); i++) {
                    cellNames[i] = rt.getLabel(i);
                }
                IJ.run("Clear Results");

                // Create and save results table
                ResultsTable neighbourTable = new ResultsTable();
                int rowsToProcess = Math.min(cellNames.length, neighbourCountNoBackground.length);
                IJ.log("Creating table with " + rowsToProcess + " rows");

                for (int i = 0; i < rowsToProcess; i++) {
                    neighbourTable.incrementCounter();
                    neighbourTable.addLabel(cellNames[i]);
                    neighbourTable.addValue("Neuron_id", cellNames[i]);
                    neighbourTable.addValue("No of cells around " + cellType1, neighbourCountNoBackground[i]);
                }

                try {
                    neighbourTable.save(tablePath);
                    IJ.log("CSV SAVED SUCCESSFULLY to: " + tablePath);

                    // Verify file exists
                    File csvFile = new File(tablePath);
                    IJ.log("File exists after save: " + csvFile.exists());
                    if (csvFile.exists()) {
                        IJ.log("File size: " + csvFile.length() + " bytes");
                    }
                } catch (Exception e) {
                    IJ.error("Could not save CSV table: " + e.getMessage());
                    IJ.log("Save error details: " + e.toString());
                }
            } else {
                IJ.log("ERROR: Cell image is null, cannot process ROIs");
            }
        } catch (Exception e) {
            IJ.log("Error loading ROIs or processing: " + e.getMessage());
        }

        // Debug parametric image saving
        if (saveParametricImage) {
            IJ.log("=== DEBUG: Parametric Image Saving ===");
            try {
                String neighbourCell1 = getParametricImg(neighbourCount, cell1, cellType1);
                IJ.log("Parametric image created: " + neighbourCell1);

                ImagePlus paramImg = WindowManager.getImage(neighbourCell1);
                if (paramImg != null) {
                    String paramPath = fullSavePath + fs + neighbourCell1 + ".tif";
                    IJ.log("Saving parametric image to: " + paramPath);

                    IJ.saveAsTiff(paramImg, paramPath);
                    paramImg.close();

                    // Verify file exists
                    File tifFile = new File(paramPath);
                    IJ.log("TIF file exists after save: " + tifFile.exists());
                    if (tifFile.exists()) {
                        IJ.log("TIF file size: " + tifFile.length() + " bytes");
                    }
                } else {
                    IJ.log("ERROR: Parametric image is null");
                }
            } catch (Exception e) {
                IJ.log("Error creating/saving parametric image: " + e.getMessage());
            }
        } else {
            IJ.log("Parametric image saving disabled (saveParametricImage = false)");
        }

        // Cleanup
        if (!gangliaBinary.equals("NA")) {
            ImagePlus gangliaImg = WindowManager.getImage(gangliaBinary);
            if (gangliaImg != null) {
                gangliaImg.close();
            }
        }

        IJ.log("=== DEBUG: Final Status ===");
        IJ.log("Analysis completed for " + cellType1);
        IJ.log("Check directory: " + fullSavePath);
        IJ.log("Expected files:");
        IJ.log("  - CSV: " + tablePath);
        if (saveParametricImage) {
            IJ.log("  - TIF: " + fullSavePath + cellType1 + "_neighbours.tif");
        }

        roiManager.reset();
    }

    private static double[] nearestNeighbourSingleCell(String refImg, int dilateRadius, String gangliaBinary, String cellType1) {
        IJ.log("=== DEBUG: nearestNeighbourSingleCell ===");
        IJ.log("refImg: " + refImg);
        IJ.log("dilateRadius: " + dilateRadius);
        IJ.log("gangliaBinary: " + gangliaBinary);
        IJ.log("cellType1: " + cellType1);

        IJ.run("Clear Results");

        try {
            IJ.run("CLIJ2 Macro Extensions", "cl_device=");
            IJ.log("CLIJ2 initialized for neighbor analysis");
        } catch (Exception e) {
            IJ.log("CLIJ2 initialization failed: " + e.getMessage());
            return new double[0];
        }

        try {
            // Push image to GPU
            IJ.log("Pushing image to GPU: " + refImg);
            IJ.run("CLIJ2 push [" + refImg + "]");

            // Dilate labels
            IJ.log("Dilating labels with radius: " + dilateRadius);
            IJ.run("CLIJ2 dilateLabels", "input=" + refImg + " destination=ref_dilate radius=" + dilateRadius);

            if (!gangliaBinary.equals("NA")) {
                IJ.log("Processing with ganglia restriction");
                IJ.run("CLIJ2 push [" + gangliaBinary + "]");
                IJ.run("CLIJ2 multiplyImages", "factor1=ref_dilate factor2=" + gangliaBinary + " destination=ref_dilate_ganglia_restrict");
                IJ.run("CLIJ2 touchingNeighborCountMap", "input=ref_dilate_ganglia_restrict destination=ref_neighbour_count");
                IJ.run("CLIJ2 release [" + gangliaBinary + "]");
            } else {
                IJ.log("Processing without ganglia restriction");
                IJ.run("CLIJ2 touchingNeighborCountMap", "input=ref_dilate destination=ref_neighbour_count");
            }

            // Reduce labels to centroids
            IJ.log("Reducing labels to centroids");
            IJ.run("CLIJ2 reduceLabelsToCentroids", "input=" + refImg + " destination=ref_img_centroid");

            IJ.log("Computing statistics");
            IJ.run("CLIJ2 statisticsOfBackgroundAndLabelledPixels", "input=ref_neighbour_count labelmap=ref_img_centroid");

            ResultsTable rt = ResultsTable.getResultsTable();
            IJ.log("Statistics results table entries: " + rt.getCounter());

            if (rt.getCounter() == 0) {
                IJ.log("WARNING: No results in statistics table!");
                return new double[0];
            }

            double[] noNeighbours = new double[rt.getCounter()];

            for (int i = 0; i < rt.getCounter(); i++) {
                noNeighbours[i] = rt.getValue("MINIMUM_INTENSITY", i);
                if (i < 5) { // Log first few values
                    IJ.log("Neighbor count [" + i + "]: " + noNeighbours[i]);
                }
            }

            IJ.run("Clear Results");

            if (noNeighbours.length > 0) {
                noNeighbours[0] = 0;
            }

            // Release GPU memory
            IJ.log("Releasing GPU memory");
            IJ.run("CLIJ2 release [ref_neighbour_count]");
            IJ.run("CLIJ2 release [ref_img_centroid]");
            IJ.run("CLIJ2 release [ref_dilate]");

            if (!gangliaBinary.equals("NA")) {
                IJ.run("CLIJ2 release [ref_dilate_ganglia_restrict]");
            }

            IJ.log("nearestNeighbourSingleCell completed. Returning " + noNeighbours.length + " values");
            return noNeighbours;

        } catch (Exception e) {
            IJ.log("ERROR in nearestNeighbourSingleCell: " + e.getMessage());
            IJ.log("Stack trace: ");
            e.printStackTrace();
            return new double[0];
        }
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

    private static void testFileWriting(String savePath) {
        try {
            String fs = File.separator;
            String testDir = savePath + fs + "spatial_analysis" + fs;
            File saveDir = new File(testDir);

            if (!saveDir.exists()) {
                boolean created = saveDir.mkdirs();
                IJ.log("Test: Directory created: " + created);
            }

            // Test writing a simple text file
            String testFile = testDir + "test.txt";
            java.io.FileWriter writer = new java.io.FileWriter(testFile);
            writer.write("Test file - if you see this, file writing works!");
            writer.close();

            File testF = new File(testFile);
            if (testF.exists()) {
                IJ.log("SUCCESS: Test file created at " + testFile);
                IJ.log("File size: " + testF.length() + " bytes");
                // Clean up
                testF.delete();
            } else {
                IJ.log("ERROR: Test file was not created");
            }

        } catch (Exception e) {
            IJ.log("ERROR: Cannot write to directory: " + e.getMessage());
        }
    }
}

//package Analysis;
//
//import ij.*;
//        import ij.measure.ResultsTable;
//import ij.plugin.ImageCalculator;
//import ij.plugin.frame.RoiManager;
//import ij.process.ImageProcessor;
//import ij.process.ImageStatistics;
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
//        IJ.log("=== Starting Spatial Analysis (No CLIJ2) ===");
//        IJ.log("cellType1: " + cellType1);
//        IJ.log("cell1: " + cell1);
//
//        RoiManager roiManager = RoiManager.getInstance();
//        if (roiManager == null) {
//            roiManager = new RoiManager();
//        }
//        roiManager.reset();
//
//        // Convert to pixels
//        int labelDilationPixels = (int) Math.round(labelDilation / pixelWidth);
//        IJ.log("Label dilation in pixels: " + labelDilationPixels);
//
//        String fs = File.separator;
//        String fullSavePath = savePath + fs + "spatial_analysis" + fs;
//
//        // Create save directory
//        File saveDir = new File(fullSavePath);
//        if (!saveDir.exists()) {
//            boolean created = saveDir.mkdirs();
//            if (!created) {
//                IJ.error("Could not create save directory: " + fullSavePath);
//                return;
//            }
//        }
//
//        // Process ganglia binary image
//        String gangliaBinary = processGangliaBinary(gangliaBinaryOrig);
//
//        // Verify cell image exists
//        ImagePlus cellImage = WindowManager.getImage(cell1);
//        if (cellImage == null) {
//            IJ.error("Cell image not found: " + cell1);
//            return;
//        }
//
//        // Run neighbor analysis without CLIJ2
//        double[] neighbourCount = nearestNeighbourSingleCellNoCLIJ(cellImage, labelDilationPixels, gangliaBinary, cellType1);
//
//        if (neighbourCount.length == 0) {
//            IJ.error("Neighbor analysis failed - no results returned");
//            return;
//        }
//
//        // Remove background (index 0)
//        double[] neighbourCountNoBackground = new double[neighbourCount.length - 1];
//        System.arraycopy(neighbourCount, 1, neighbourCountNoBackground, 0, neighbourCount.length - 1);
//
//        // Save results table
//        saveResultsTable(cellType1, fullSavePath, neighbourCountNoBackground, roiLocationCell, cellImage);
//
//        // Save parametric image if requested
//        if (saveParametricImage) {
//            saveParametricImageNoCLIJ(neighbourCount, cellImage, cellType1, fullSavePath);
//        }
//
//        // Cleanup
//        cleanup(gangliaBinary);
//
//        IJ.log("Analysis completed for " + cellType1);
//        roiManager.reset();
//    }
//
//    private static String processGangliaBinary(String gangliaBinaryOrig) {
//        IJ.run("Options...", "iterations=1 count=1 black");
//        String gangliaBinary = "NA";
//
//        if (gangliaBinaryOrig != null && !gangliaBinaryOrig.equals("NA")) {
//            ImagePlus gangliaImg = WindowManager.getImage(gangliaBinaryOrig);
//            if (gangliaImg != null) {
//                IJ.selectWindow(gangliaImg.getTitle());
//                IJ.run("Select None");
//                IJ.run("Duplicate...", "title=ganglia_binary_dup");
//
//                ImagePlus gangliaDup = WindowManager.getImage("ganglia_binary_dup");
//                if (gangliaDup != null) {
//                    IJ.selectWindow(gangliaDup.getTitle());
//                    IJ.setThreshold(gangliaDup, 0.5, 65535);
//                    IJ.run("Convert to Mask");
//                    gangliaDup.setTitle("Ganglia_outline");
//                    gangliaBinary = gangliaDup.getTitle();
//                    IJ.run("Divide...", "value=255");
//                    gangliaDup.resetDisplayRange();
//                }
//            }
//        }
//        return gangliaBinary;
//    }
//
//    private static double[] nearestNeighbourSingleCellNoCLIJ(ImagePlus cellImage, int dilateRadius, String gangliaBinary, String cellType1) {
//        IJ.log("Starting neighbor analysis (no CLIJ2) for: " + cellType1);
//
//        try {
//            // Duplicate the cell image for processing
//            IJ.selectWindow(cellImage.getTitle());
//            IJ.run("Duplicate...", "title=cell_for_dilation");
//            ImagePlus dilateImg = WindowManager.getImage("cell_for_dilation");
//
//            // Dilate labels using ImageJ's built-in morphology
//            for (int i = 0; i < dilateRadius; i++) {
//                IJ.selectWindow(dilateImg.getTitle());
//                IJ.run("Maximum...", "radius=1");
//            }
//
//            ImagePlus finalDilated = dilateImg;
//
//            // Apply ganglia restriction if available
//            if (!gangliaBinary.equals("NA")) {
//                ImagePlus gangliaImg = WindowManager.getImage(gangliaBinary);
//                if (gangliaImg != null) {
//                    // Multiply dilated image with ganglia mask
//                    ImageCalculator ic = new ImageCalculator();
//                    ImagePlus restricted = ic.run("Multiply create", finalDilated, gangliaImg);
//                    restricted.setTitle("dilated_restricted");
//                    finalDilated.close();
//                    finalDilated = restricted;
//                }
//            }
//
//            // Count neighbors manually
//            double[] neighborCounts = countNeighborsManually(cellImage, finalDilated);
//
//            // Cleanup
//            finalDilated.close();
//
//            IJ.log("Neighbor analysis completed. Found " + neighborCounts.length + " objects");
//            return neighborCounts;
//
//        } catch (Exception e) {
//            IJ.error("Error in neighbor analysis: " + e.getMessage());
//            return new double[0];
//        }
//    }
//
//    private static double[] countNeighborsManually(ImagePlus originalLabels, ImagePlus dilatedLabels) {
//        ImageProcessor originalIP = originalLabels.getProcessor();
//        ImageProcessor dilatedIP = dilatedLabels.getProcessor();
//
//        int width = originalIP.getWidth();
//        int height = originalIP.getHeight();
//
//        // Find all unique labels
//        Map<Integer, Integer> labelCounts = new HashMap<>();
//        int maxLabel = 0;
//
//        for (int y = 0; y < height; y++) {
//            for (int x = 0; x < width; x++) {
//                int label = (int) originalIP.getPixelValue(x, y);
//                if (label > 0) {
//                    maxLabel = Math.max(maxLabel, label);
//                    labelCounts.put(label, 0);
//                }
//            }
//        }
//
//        // Count neighbors for each label
//        for (int originalLabel : labelCounts.keySet()) {
//            int neighborCount = 0;
//
//            // For each pixel of the original label, check what labels are in the dilated version
//            for (int y = 0; y < height; y++) {
//                for (int x = 0; x < width; x++) {
//                    int origLabel = (int) originalIP.getPixelValue(x, y);
//                    if (origLabel == originalLabel) {
//                        // Check 8-connected neighborhood in dilated image
//                        for (int dy = -1; dy <= 1; dy++) {
//                            for (int dx = -1; dx <= 1; dx++) {
//                                int nx = x + dx;
//                                int ny = y + dy;
//                                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
//                                    int dilatedLabel = (int) dilatedIP.getPixelValue(nx, ny);
//                                    if (dilatedLabel > 0 && dilatedLabel != originalLabel) {
//                                        neighborCount++;
//                                    }
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//            labelCounts.put(originalLabel, neighborCount);
//        }
//
//        // Convert to array (note: this is a simplified version)
//        double[] result = new double[maxLabel + 1];
//        for (Map.Entry<Integer, Integer> entry : labelCounts.entrySet()) {
//            result[entry.getKey()] = entry.getValue();
//        }
//
//        return result;
//    }
//
//    private static void saveParametricImageNoCLIJ(double[] neighbourCount, ImagePlus cellImage, String cellType1, String fullSavePath) {
//        try {
//            // Create parametric image using ImageJ's built-in functions
//            IJ.selectWindow(cellImage.getTitle());
//            IJ.run("Duplicate...", "title=parametric_temp");
//            ImagePlus paramImg = WindowManager.getImage("parametric_temp");
//
//            ImageProcessor ip = paramImg.getProcessor();
//            int width = ip.getWidth();
//            int height = ip.getHeight();
//
//            // Replace label values with neighbor counts
//            for (int y = 0; y < height; y++) {
//                for (int x = 0; x < width; x++) {
//                    int label = (int) ip.getPixelValue(x, y);
//                    if (label < neighbourCount.length) {
//                        ip.putPixelValue(x, y, neighbourCount[label]);
//                    }
//                }
//            }
//
//            paramImg.resetDisplayRange();
//            IJ.selectWindow(paramImg.getTitle());
//            IJ.run("Fire");
//
//            String newName = cellType1 + "_neighbours";
//            paramImg.setTitle(newName);
//
//            String paramPath = fullSavePath + File.separator + newName + ".tif";
//            IJ.saveAsTiff(paramImg, paramPath);
//            paramImg.close();
//
//            IJ.log("Parametric image saved to: " + paramPath);
//        } catch (Exception e) {
//            IJ.log("Error creating/saving parametric image: " + e.getMessage());
//        }
//    }
//
//    private static void saveResultsTable(String cellType1, String fullSavePath, double[] neighbourCountNoBackground,
//                                         String roiLocationCell, ImagePlus cellImage) {
//        String tableName = "Neighbour_count_" + cellType1;
//        String tablePath = fullSavePath + File.separator + tableName + ".csv";
//
//        try {
//            RoiManager roiManager = RoiManager.getInstance();
//            roiManager.runCommand("Open", roiLocationCell);
//
//            IJ.selectWindow(cellImage.getTitle());
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
//
//            ResultsTable neighbourTable = new ResultsTable();
//            int rowsToProcess = Math.min(cellNames.length, neighbourCountNoBackground.length);
//
//            for (int i = 0; i < rowsToProcess; i++) {
//                neighbourTable.incrementCounter();
//                neighbourTable.addLabel(cellNames[i]);
//                neighbourTable.addValue("Neuron_id", cellNames[i]);
//                neighbourTable.addValue("No of cells around " + cellType1, neighbourCountNoBackground[i]);
//            }
//
//            neighbourTable.save(tablePath);
//            IJ.log("CSV saved successfully to: " + tablePath);
//
//        } catch (Exception e) {
//            IJ.error("Error saving results table: " + e.getMessage());
//        }
//    }
//
//    private static void renameRoiNameResultTable() {
//        ResultsTable rt = ResultsTable.getResultsTable();
//        if (rt.getCounter() == 0) return;
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
//    private static void cleanup(String gangliaBinary) {
//        if (!gangliaBinary.equals("NA")) {
//            ImagePlus gangliaImg = WindowManager.getImage(gangliaBinary);
//            if (gangliaImg != null) {
//                gangliaImg.close();
//            }
//        }
//
//        // Clear CLIJ2 cache
//        try {
//            IJ.run("CLIJ2 clear");
//        } catch (Exception e) { /* ignore */ }
//    }
//}

