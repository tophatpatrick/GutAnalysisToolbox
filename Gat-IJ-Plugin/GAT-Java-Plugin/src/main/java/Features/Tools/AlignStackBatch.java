package Features.Tools;

import Features.Core.Params;
import Features.Tools.AlignStack;
import ij.IJ;
import ij.ImagePlus;
import ij.io.Opener;

import java.io.File;

/**
 * AlignStackBatch plugin
 * Runs Linear Stack Alignment with SIFT, Template Matching, or StackReg on multiple stacks.
 * Supports .tif and .lif files (multi-series), batch mode.
 */
public class AlignStackBatch {

    public static void runBatch(Params p) throws Exception {
        // --- Validate input ---
        if (p.inputDir == null || p.inputDir.trim().isEmpty())
            throw new IllegalArgumentException("Input directory not specified");
        File folder = new File(p.inputDir.trim());
        if (!folder.exists() || !folder.isDirectory())
            throw new IllegalArgumentException("Invalid input directory: " + p.inputDir);

        String ext = (p.fileExt == null || p.fileExt.trim().isEmpty()) ? ".tif" : p.fileExt.trim().toLowerCase();
        String[] files = folder.list((dir, name) -> name.toLowerCase().endsWith(ext));
        if (files == null || files.length == 0) {
            IJ.log("No files found with extension " + ext + " in " + folder.getAbsolutePath());
            return;
        }

        IJ.log("Files found: " + files.length);

        // --- Process each file ---
        for (String fileName : files) {
            File file = new File(folder, fileName);
            IJ.log("Processing file: " + file.getName());

            ImagePlus imp = IJ.openImage(file.getAbsolutePath());
            if (imp != null) processFile(imp, p);
        }

        IJ.log("Batch alignment finished.");
    }

    private static ImagePlus openBioFormatsImage(File file, int series) {
        try {
            // Use Bio-Formats Importer
            String cmd = (series > 0)
                    ? "open=[" + file.getAbsolutePath() + "] color_mode=Default rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT series_" + series
                    : "open=[" + file.getAbsolutePath() + "] autoscale color_mode=Default rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT";

            IJ.run("Bio-Formats Importer", cmd);

            // Retrieve current ImagePlus
            ImagePlus imp = IJ.getImage();
            if (imp == null) IJ.log("Failed to open " + file.getName());
            return imp;

        } catch (Exception ex) {
            IJ.log("Error opening " + file.getName() + ": " + ex.getMessage());
            return null;
        }
    }

    private static void processFile(ImagePlus imp, Params p) {
        if (imp == null) return;

        int sizeT = imp.getNFrames();
        if (sizeT <= 10) {
            IJ.log("Skipping " + imp.getTitle() + " (<=10 frames)");
            imp.close();
            return;
        }

        // Multi-channel: keep only first channel
        int sizeC = imp.getNChannels();
        if (sizeC > 1) {
            IJ.run(imp, "Split Channels", "");
            ImagePlus firstChannel = IJ.getImage();
            IJ.selectWindow("C1-" + imp.getTitle());
            for (int c = 2; c <= sizeC; c++) IJ.run("Close");
            imp = firstChannel;
        }

        IJ.log("Running alignment on " + imp.getTitle());

        if (p.useSIFT) AlignStack.alignSIFT(imp, true);
        if (p.useTemplateMatching) AlignStack.alignTemplateMatching(imp, p.referenceFrame);
        if (p.useStackReg) {
        throw new UnsupportedOperationException(
            "StackReg alignment is not implemented in batch mode at this time"
        );
    }

        String outputDir = (p.outputDir != null && !p.outputDir.trim().isEmpty())
                ? p.outputDir.trim()
                : new File(".").getAbsolutePath();
        String outFile = outputDir + File.separator + imp.getTitle() + "_aligned.tif";

        IJ.saveAsTiff(imp, outFile);
        IJ.log("Saved aligned stack: " + outFile);

        AlignStack.saveAlignmentResultsCSV(imp, outputDir);

        imp.close();
        System.gc();
    }

}
