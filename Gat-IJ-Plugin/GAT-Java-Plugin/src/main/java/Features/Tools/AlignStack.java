package Features.Tools;

import Features.Core.Params;
import ij.IJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.measure.ResultsTable;
import java.util.List;
import java.util.ArrayList;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * AlignStack plugin
 * Runs Linear Stack Alignment with SIFT and/or Template Matching on a TIFF stack.
 * Converts the original macro logic to Java, using parameters from Params.
 */
public class AlignStack implements PlugIn {

    public static class AlignResult {
        public final ImagePlus alignedStack;
        public final File resultCSV;

        public AlignResult(ImagePlus alignedStack, File resultCSV) {
            this.alignedStack = alignedStack;
            this.resultCSV = resultCSV;
        }
    }

    private static final String PLUGIN_INSTALLATION_URL =
            "https://sites.google.com/site/qingzongtseng/template-matching-ij-plugin#install";

    @Override
    public void run(String arg) {
        IJ.log("Please install template plugin if needed: " + PLUGIN_INSTALLATION_URL);
        IJ.showMessage("Info", "Ensure Linear Stack Alignment with SIFT and Template Matching plugins are installed.\n" +
                "URL: " + PLUGIN_INSTALLATION_URL);
    }

    /**
     * Run alignment using params from the pane.
     */
    public AlignResult run(Params p) throws Exception {
        if (p.imagePath == null || p.imagePath.isEmpty()) {
            throw new IllegalArgumentException("No input image specified");
        }

        File file = new File(p.imagePath);
        if (!file.exists()) throw new IllegalArgumentException("File not found: " + p.imagePath);

        ImagePlus imp = IJ.openImage(p.imagePath);
        if (imp == null) throw new RuntimeException("Failed to open image: " + p.imagePath);

        int sizeX = imp.getWidth();
        int sizeY = imp.getHeight();
        int sizeC = imp.getNChannels();
        int sizeT = imp.getNFrames();

        if (sizeT < 10) {
            IJ.log("Stack has fewer than 10 frames. Skipping alignment: " + file.getName());
            throw new Exception("Stack has fewer than 10 frames. Skipping alignment.");
        }

        // Reference frame
        int refFrame = Math.min(Math.max(1, p.referenceFrame), sizeT);
        imp.setSlice(refFrame);

        IJ.showStatus("Starting alignment: " + file.getName());

        // --- Multi-channel handling ---
        if (sizeC > 1) {
            IJ.run(imp, "Split Channels", "");
            // Assume first channel only; close others
            ImagePlus firstChannel = IJ.getImage();
            firstChannel.setTitle(file.getName());
            IJ.selectWindow(file.getName());
            for (int c = 2; c <= sizeC; c++) {
                IJ.selectWindow("C" + c + "-" + file.getName());
                IJ.run("Close");
            }
            imp = firstChannel;
        }

        // --- SIFT alignment ---
        if (p.useSIFT) {
            alignSIFT(imp, false);
        }

        // --- Template Matching ---
        if (p.useTemplateMatching) {
            alignTemplateMatching(imp, refFrame);
        }

        // --- Save aligned stack ---
        if (p.saveAlignedStack) {
            String outputPath = p.outputDir;
            if (!outputPath.endsWith(File.separator)) outputPath += File.separator;
            String outFile = outputPath + file.getName().replace(".tif", "_aligned.tif");
            IJ.saveAsTiff(imp, outFile);
            IJ.log("Saved aligned stack to: " + outFile);
        }

        // --- save results CSV ---
        File resultCSV = new File(p.outputDir, imp.getTitle() + "_alignment.csv");
        List<Double> slices = new ArrayList<>();
        List<Double> dx = new ArrayList<>();
        List<Double> dy = new ArrayList<>();

        ResultsTable rt = ResultsTable.getResultsTable();
        if (rt != null && rt.getCounter() > 0) {
            int lastCol = rt.getLastColumn();
            int secondLastCol = lastCol - 1;

            try (PrintWriter pw = new PrintWriter(new FileWriter(resultCSV))) {
                pw.println("Slice,Dx,Dy");
                for (int i = 0; i < rt.getCounter(); i++) {
                    double valDx = rt.getValueAsDouble(secondLastCol, i);
                    double valDy = rt.getValueAsDouble(lastCol, i);
                    slices.add((double) (i + 1)); // Slice index
                    dx.add(valDx);
                    dy.add(valDy);
                    pw.printf("%d,%.3f,%.3f%n", i + 1, valDx, valDy);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        } else {
            IJ.log("Warning: ResultsTable is empty. No motion data found.");
        }

        
        // create CSV with columns: Slice,Dx,Dy (Nothing column removed)
        // (Use previous CSV cleaning logic here)

        // Clean up
        IJ.run("Collect Garbage");
        IJ.showStatus("Alignment done: " + file.getName());
        return new AlignResult(imp, resultCSV);
    }

    /** Align using SIFT plugin (Java version of macro align_sift function) */
    private void alignSIFT(ImagePlus imp, boolean defaultSettings) {
        int size = Math.min(imp.getWidth(), imp.getHeight());
        int maximalAlignmentError = (int) Math.ceil(0.1 * size);
        double inlierRatio = defaultSettings ? 0.05 : 0.7;
        int featureDescSize = defaultSettings ? 4 : (size < 500 ? 8 : 4);

        String args = String.format("initial_gaussian_blur=1.60 steps_per_scale_octave=%d minimum_image_size=64 " +
                        "maximum_image_size=%d feature_descriptor_size=%d feature_descriptor_orientation_bins=8 " +
                        "closest/next_closest_ratio=0.92 maximal_alignment_error=%d inlier_ratio=%f expected_transformation=Affine",
                defaultSettings ? 3 : 4, size, featureDescSize, maximalAlignmentError, inlierRatio);

        IJ.run(imp, "Linear Stack Alignment with SIFT", args);
        IJ.wait(100);
    }

    /** Align using Template Matching plugin (Java version of macro) */
    private void alignTemplateMatching(ImagePlus imp, int refFrame) {
        int xSize = (int) Math.floor(imp.getWidth() * 0.7);
        int ySize = (int) Math.floor(imp.getHeight() * 0.7);
        int x0 = (int) Math.floor(imp.getWidth() / 6.0);
        int y0 = (int) Math.floor(imp.getHeight() / 6.0);

        String args = String.format("method=5 windowsizex=%d windowsizey=%d x0=%d y0=%d swindow=0 subpixel=false itpmethod=0 ref.slice=%d show=true",
                xSize, ySize, x0, y0, refFrame);

        IJ.run(imp, "Align slices in stack...", args);
        IJ.wait(10);
    }
}
