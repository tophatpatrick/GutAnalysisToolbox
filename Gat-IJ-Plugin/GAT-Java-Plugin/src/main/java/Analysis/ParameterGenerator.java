package Analysis;

import ij.IJ;
import ij.measure.ResultsTable;
import java.io.File;


public class ParameterGenerator {

    private static final String FILE_SEPARATOR = File.separator;

    public static void saveParametersToCSVSingleCellType(String savePath, String cellType, double labelDilation,
                                            boolean saveParametricImage, String maxProjPath,
                                            String roiPath, String roiGangliaPath) {
        try {
            String spatialSavePath = savePath + FILE_SEPARATOR + "spatial_analysis" + FILE_SEPARATOR;
            String paramPath = spatialSavePath + "parameters_single_celltype.csv";

            ResultsTable paramTable = new ResultsTable();
            paramTable.incrementCounter();
            paramTable.addValue("Parameter", "Cell Type Name");
            paramTable.addValue("Value", cellType);

            paramTable.incrementCounter();
            paramTable.addValue("Parameter", "Label Dilation (microns)");
            paramTable.addValue("Value", labelDilation);

            paramTable.incrementCounter();
            paramTable.addValue("Parameter", "Save Parametric Image");
            paramTable.addValue("Value", saveParametricImage ? "Yes" : "No");

            paramTable.incrementCounter();
            paramTable.addValue("Parameter", "Maximum Projection Path");
            paramTable.addValue("Value", maxProjPath);

            paramTable.incrementCounter();
            paramTable.addValue("Parameter", "ROI Cells Path");
            paramTable.addValue("Value", roiPath);

            paramTable.incrementCounter();
            paramTable.addValue("Parameter", "ROI Ganglia Path");
            paramTable.addValue("Value", roiGangliaPath);

            paramTable.save(paramPath);
            IJ.log("Parameters saved to: " + paramPath);

        } catch (Exception e) {
            IJ.log("Warning: Could not save parameters - " + e.getMessage());
        }
    }

    public static void saveParametersToCSVTwoCellType(String savePath, String cellType1, String cellType2,
                                            double labelDilation, boolean saveParametricImage,
                                            String maxProjPath, String roi1Path, String roi2Path,
                                            String roiGangliaPath) {
        try {
            String spatialSavePath = savePath + FILE_SEPARATOR + "spatial_analysis" + FILE_SEPARATOR;
            String paramPath = spatialSavePath + "parameters_two_celltype.csv";

            ResultsTable paramTable = new ResultsTable();
            paramTable.incrementCounter();
            paramTable.addValue("Parameter", "Cell Type 1 Name");
            paramTable.addValue("Value", cellType1);

            paramTable.incrementCounter();
            paramTable.addValue("Parameter", "Cell Type 2 Name");
            paramTable.addValue("Value", cellType2);

            paramTable.incrementCounter();
            paramTable.addValue("Parameter", "Label Dilation (microns)");
            paramTable.addValue("Value", labelDilation);

            paramTable.incrementCounter();
            paramTable.addValue("Parameter", "Save Parametric Image");
            paramTable.addValue("Value", saveParametricImage ? "Yes" : "No");

            paramTable.incrementCounter();
            paramTable.addValue("Parameter", "Maximum Projection Path");
            paramTable.addValue("Value", maxProjPath);

            paramTable.incrementCounter();
            paramTable.addValue("Parameter", "ROI Cell 1 Path");
            paramTable.addValue("Value", roi1Path);

            paramTable.incrementCounter();
            paramTable.addValue("Parameter", "ROI Cell 2 Path");
            paramTable.addValue("Value", roi2Path);

            paramTable.incrementCounter();
            paramTable.addValue("Parameter", "ROI Ganglia Path");
            paramTable.addValue("Value", roiGangliaPath);

            paramTable.save(paramPath);
            IJ.log("Parameters saved to: " + paramPath);

        } catch (Exception e) {
            IJ.log("Warning: Could not save parameters - " + e.getMessage());
        }
    }
}
