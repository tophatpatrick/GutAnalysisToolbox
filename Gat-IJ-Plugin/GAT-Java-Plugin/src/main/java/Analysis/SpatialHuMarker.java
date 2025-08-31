package Analysis;

import ij.*;
import ij.measure.ResultsTable;
import ij.plugin.frame.RoiManager;
import java.io.File;

public class SpatialHuMarker {

    public static void execute(String cellType1, String cell1, String cellType2, String cell2,
                               String gangliaBinaryOrig, String savePath, double labelDilation,
                               boolean saveParametricImage, double pixelWidth, String roiLocationCell1) {

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

        // Count ref around marker (Hu around specific marker)
        double[] noNeighboursRefMarker = countRefAroundMarker(cell1, cell2, labelDilationPixels, gangliaBinary, cellType1, cellType2);
        double[] countsRefMarker = removeBackgroundIndex(noNeighboursRefMarker);

        // Count marker around ref (specific marker around Hu)
        double[] noNeighboursMarkerRef = countMarkerAroundRef(cell1, cell2, labelDilationPixels, gangliaBinary);
        double[] countsMarkerRef = removeBackgroundIndex(noNeighboursMarkerRef);

        roiManager.reset();
        IJ.run("Clear Results");

        String tableName = "Neighbour_count_" + cellType1 + "_" + cellType2;
        String tablePath = savePath + fs + tableName + ".csv";

        String[] cell1Names = getRoiLabels(roiLocationCell1, cell1);
        String[] cell2Names = getRoiLabels(roiLocationCell1, cell2); // Note: using cell1 ROI location for both as per original macro

        // Create and save results table
        ResultsTable neighbourTable = new ResultsTable();
        neighbourTable.setDefaultHeadings();

        // Add data for both cell types
        int maxRows = Math.max(cell1Names.length, cell2Names.length);
        for (int i = 0; i < maxRows; i++) {
            neighbourTable.incrementCounter();

            if (i < cell2Names.length) {
                neighbourTable.addValue(cellType2 + "_id", cell2Names[i]);
            }
            if (i < countsRefMarker.length) {
                neighbourTable.addValue("No of " + cellType1 + " around " + cellType2, countsRefMarker[i]);
            }
            if (i < cell1Names.length) {
                neighbourTable.addValue(cellType1 + "_id", cell1Names[i]);
            }
            if (i < countsMarkerRef.length) {
                neighbourTable.addValue("No of " + cellType2 + " around " + cellType1, countsMarkerRef[i]);
            }
        }

        try {
            neighbourTable.save(tablePath);
        } catch (Exception e) {
            IJ.error("Could not save table: " + e.getMessage());
        }

        if (saveParametricImage) {
            String overlapCell1 = getParametricImg(noNeighboursRefMarker, cell2, cellType1, cellType2);
            String overlapCell2 = getParametricImg(noNeighboursMarkerRef, cell1, cellType2, cellType1);

            ImagePlus paramImg1 = WindowManager.getImage(overlapCell1);
            if (paramImg1 != null) {
                IJ.saveAsTiff(paramImg1, savePath + fs + overlapCell1);
                paramImg1.close();
            }

            ImagePlus paramImg2 = WindowManager.getImage(overlapCell2);
            if (paramImg2 != null) {
                IJ.saveAsTiff(paramImg2, savePath + fs + overlapCell2);
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

    // Ref_img is Hu and should label all cells, marker_img should be a subset
    private static double[] countRefAroundMarker(String refImg, String markerImg, int dilateRadius, String gangliaBinary, String cellType1, String cellType2) {
        IJ.run("Clear Results");
        IJ.run("CLIJ2 Macro Extensions", "cl_device=");

        // Push images to GPU
        IJ.run("CLIJ2 push [" + refImg + "]");
        IJ.run("CLIJ2 push [" + markerImg + "]");

        // Dilate cells in ref_img
        IJ.run("CLIJ2 dilateLabels", "input=" + refImg + " destination=ref_dilate radius=" + dilateRadius);

        if (!gangliaBinary.equals("NA")) {
            IJ.run("CLIJ2 push [" + gangliaBinary + "]");
            IJ.run("CLIJ2 multiplyImages", "factor1=ref_dilate factor2=" + gangliaBinary + " destination=ref_dilate_ganglia_restrict");
            IJ.run("CLIJ2 touchingNeighborCountMap", "input=ref_dilate_ganglia_restrict destination=ref_neighbour_count");
        } else {
            IJ.run("CLIJ2 touchingNeighborCountMap", "input=ref_dilate destination=ref_neighbour_count");
        }

        IJ.run("CLIJ2 reduceLabelsToCentroids", "input=" + markerImg + " destination=marker_img_centroid");
        IJ.run("CLIJ2 statisticsOfBackgroundAndLabelledPixels", "input=ref_neighbour_count labelmap=marker_img_centroid");

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
        IJ.run("CLIJ2 release [marker_img_centroid]");
        if (!gangliaBinary.equals("NA")) {
            IJ.run("CLIJ2 release [" + gangliaBinary + "]");
        }

        return noNeighbours;
    }

    // How many cells in marker_img around ref
    private static double[] countMarkerAroundRef(String refImg, String markerImg, int dilateRadius, String gangliaBinary) {
        IJ.run("CLIJ2 Macro Extensions", "cl_device=");

        // Push images to GPU
        IJ.run("CLIJ2 push [" + refImg + "]");
        IJ.run("CLIJ2 push [" + markerImg + "]");

        // Dilate cells in ref_img
        IJ.run("CLIJ2 dilateLabels", "input=" + refImg + " destination=ref_dilate_init radius=" + dilateRadius);

        String refDilate;
        if (!gangliaBinary.equals("NA")) {
            IJ.run("CLIJ2 push [" + gangliaBinary + "]");
            IJ.run("CLIJ2 multiplyImages", "factor1=ref_dilate_init factor2=" + gangliaBinary + " destination=ref_dilate");
            refDilate = "ref_dilate";
        } else {
            refDilate = "ref_dilate_init";
        }

        IJ.run("Clear Results");

        // Get statistics of marker corresponding to ref_img
        IJ.run("CLIJ2 statisticsOfLabelledPixels", "input=" + refDilate + " labelmap=" + markerImg);

        ResultsTable rt = ResultsTable.getResultsTable();
        double[] markerLabelRefIds = new double[rt.getCounter()];
        for (int i = 0; i < rt.getCounter(); i++) {
            markerLabelRefIds[i] = rt.getValue("MAXIMUM_INTENSITY", i);
        }

        IJ.run("Clear Results");

        // Get IDs of all cells in ref_img
        IJ.run("CLIJ2 statisticsOfBackgroundAndLabelledPixels", "input=" + refDilate + " labelmap=" + refDilate);

        rt = ResultsTable.getResultsTable();
        double[] refIds = new double[rt.getCounter()];
        for (int i = 0; i < rt.getCounter(); i++) {
            refIds[i] = rt.getValue("IDENTIFIER", i);
        }

        IJ.run("Clear Results");
        IJ.run("CLIJ2 pull [" + refDilate + "]");

        ImagePlus refDilateImg = WindowManager.getImage(refDilate);
        if (refDilateImg != null) {
            IJ.run(refDilateImg, "Region Adjacency Graph", "  image=" + refDilate);

            String ragTableName = refDilate + "-RAG";
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            ImagePlus ragWindow = WindowManager.getImage(ragTableName);
            if (ragWindow != null) {
                ResultsTable ragTable = ResultsTable.getResultsTable(ragTableName);
                if (ragTable != null) {
                    double[] label1 = getColumnFromTable(ragTable, "Label 1");
                    double[] label2 = getColumnFromTable(ragTable, "Label 2");

                    // Array of zeros with length ref_ids
                    double[] counts = new double[refIds.length];

                    // Process label adjacencies
                    for (int i = 0; i < label1.length; i++) {
                        int neighbourMarker = valueInArray(markerLabelRefIds, label2[i]);
                        if (neighbourMarker == 1) {
                            int idx = (int) label1[i];
                            if (idx < counts.length) {
                                counts[idx] += 1;
                            }
                        }
                    }

                    for (int i = 0; i < label2.length; i++) {
                        int neighbourMarker = valueInArray(markerLabelRefIds, label1[i]);
                        if (neighbourMarker == 1) {
                            int idx = (int) label2[i];
                            if (idx < counts.length) {
                                counts[idx] += 1;
                            }
                        }
                    }

                    IJ.run("Clear Results");
                    refDilateImg.close();

                    ImagePlus ragImg = WindowManager.getImage(ragTableName);
                    if (ragImg != null) {
                        ragImg.close();
                    }

                    return counts;
                }
            }
        }

        return new double[0];
    }

    private static double[] getColumnFromTable(ResultsTable table, String columnName) {
        double[] column = new double[table.getCounter()];
        for (int i = 0; i < table.getCounter(); i++) {
            column[i] = table.getValue(columnName, i);
        }
        return column;
    }

    private static int valueInArray(double[] array, double value) {
        for (double v : array) {
            if (v == value) {
                return 1;
            }
        }
        return 0;
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

    private static boolean isClij2Available() {
        try {
            IJ.run("CLIJ2 Macro Extensions", "cl_device=");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
