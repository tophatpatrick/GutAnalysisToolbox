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
        if (saveParametricImage) {
            IJ.saveAs(cellImg, "Tiff", spatialSavePath + "cell_labels.tif");
            }

        // old parametric image (attempt trying to get fire lut to work)
//        if (saveParametricImage) {
//            float[] neighborArray = new float[maxLabel];
//            for (int i = 0; i < maxLabel; i++) neighborArray[i] = (float) outTable.getValueAsDouble(i, 1);
//
//            ClearCLBuffer vectorNeighbours = clij2.pushArray(neighborArray, neighborArray.length, 1, 1);
//            ClearCLBuffer paramImg = clij2.create(cellBuffer);
//            clij2.replaceIntensities(cellBuffer, vectorNeighbours, paramImg);
//
//            ImagePlus paramResult = clij2.pull(paramImg);
//
//            // Show parametric image only if requested
//            paramResult.setTitle(cellType + "_parametric");
//            paramResult.show();
//            IJ.run(paramResult, "Fire", "");
//
//
//            // Save parametric image
//            IJ.saveAs(paramResult, "Tiff", spatialSavePath + "cell_labels_parametric.tif");
//
//            vectorNeighbours.close();
//            paramImg.close();
//        }

        // Cleanup GPU buffers
        cellBuffer.close();
        dilated.close();
        neighborMap.close();
        neighborImg.close(); // safe to close, never showed

    }
}

