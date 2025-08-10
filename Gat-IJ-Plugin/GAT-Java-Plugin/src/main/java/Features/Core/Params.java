package Features.Core;


public class Params {
    /** If null, uses current image in Fiji. */
    // Input/output
    public String imagePath = null;       // if null, uses current ImageJ active image
    public String outputDir = null;       // parent output dir (optional). We will create Analysis/<baseName> inside

    /** 1-based channel index for Hu segmentation. */
    public int huChannel = 3;


    /** StarDist 2D model (.zip) for neurons. */
    public String stardistModelZip;

    // Rescaling to model training resolution (faithful to macro)
    public boolean rescaleToTrainingPx = true;
    public double trainingPixelSizeUm = 0.568; // matches your settings
    public double trainingRescaleFactor = 1.0;                 // macro “Rescaling Factor” knob (default 1.0)

    // DeepImageJ model dir (folder)
    public String neuronDeepImageJModelDir; // e.g. <Fiji>/models/2D_enteric_neuron.bioimage.io.model

    /** If true and Z>1, use CLIJ2 EDF; else MIP. */
    public boolean useClij2EDF = false;


    /** Require microns/um calibration. */
    public boolean requireMicronUnits = true;

    // Post-processing knobs (macro names: prob_neuron, overlap_neuron)
    public double probThresh = 0.5;
    public double nmsThresh = 0.3;

    // Size filtering (pixel-area threshold derived from microns)
    public Double neuronSegLowerLimitUm = 70.0;   // from settings (in microns)

    /** Save flattened overlay image with ROIs. */
    public boolean saveFlattenedOverlay = true;

    /** For labels/CSVs. */
    public String cellTypeName = "Neuron";

    // Size filter (faithful to macro’s conversion: microns ÷ pixelWidth)
    public Double neuronSegMinMicron = 70.0; // “neuron_seg_lower_limit” in microns
}
