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