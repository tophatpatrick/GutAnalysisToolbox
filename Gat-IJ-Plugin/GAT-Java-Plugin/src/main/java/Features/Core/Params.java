package Features.Core;


public class Params {
    /** If null, uses current image in Fiji. */
    public String imagePath = null;

    /** 1-based channel index for Hu segmentation. */
    public int huChannel = 1;

    /** StarDist 2D model (.zip) for neurons. */
    public String stardistModelZip;

    /** Model training pixel size (um/pixel). */
    public double trainingPixelSizeUm = 0.568;

    /** StarDist thresholds. */
    public double probThresh = 0.50;
    public double nmsThresh  = 0.40;

    /** Min/Max neuron area (um^2) for label filtering. */
    public Double minNeuronAreaUm2 = 90.0;
    public Double maxNeuronAreaUm2 = 1500.0;

    /** If true and Z>1, use CLIJ2 EDF; else MIP. */
    public boolean useClij2EDF = false;

    /** Rescale Hu channel to training pixel size before running the model. */
    public boolean rescaleToTrainingPx = true;

    /** Require microns/um calibration. */
    public boolean requireMicronUnits = true;

    /** Output directory; null => Analysis/<basename> next to image. */
    public String outputDir = null;

    /** Save flattened overlay image with ROIs. */
    public boolean saveFlattenedOverlay = true;

    /** For labels/CSVs. */
    public String cellTypeName = "Hu";
}
