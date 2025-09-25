package services.multiplex.core;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.NewImage;
import ij.plugin.frame.RoiManager;
import services.multiplex.config.MultiplexConfig;
import services.multiplex.util.NamingUtils;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static services.multiplex.util.IJUtils.ensureRoiManagerReset;

/**
 * Core engine: reproduces the macro logic in Java.
 *
 * Steps:
 * 1) Discover all files (.tif/.tiff).
 * 2) Collect common-marker images across rounds; choose the first as reference.
 * 3) Build common-marker stack and store landmark ROIs (SIFT → MOPS → BlockMatching).
 * 4) For each round:
 *     - Round 1: stack as-is (ref first, then other markers)
 *     - Round 2..N: align each marker using "Landmark Correspondences" and stack
 * 5) Save outputs:
 *     - Results/<common>_stack.tif
 *     - Results/Aligned_Stack.tif (composite)
 *     - Results/landmark_correspondences.zip
 */
public class MultiplexRegistrationService {

    private final MultiplexConfig cfg;

    public MultiplexRegistrationService(MultiplexConfig cfg) {
        this.cfg = cfg;
    }

    public void run() {
        // Prepare Results folder
        File resultsDir = new File(cfg.saveFolder(), "Results");
        if (resultsDir.exists()) {
            throw new IllegalStateException("Remove Results folder in directory: " + resultsDir.getAbsolutePath());
        }
        resultsDir.mkdirs();

        // Reset ROI Manager
        ensureRoiManagerReset();

        // Discover TIF files
        List<File> allTifs = Arrays.stream(Objects.requireNonNull(cfg.imageFolder().listFiles()))
                .filter(NamingUtils::hasTifExt)
                .sorted(Comparator.comparing(File::getName))
                .collect(Collectors.toList());

        if (allTifs.isEmpty())
            throw new IllegalArgumentException("No .tif files found in: " + cfg.imageFolder());

        // Collect common-marker images (e.g., "*Hu*.tif")
        List<File> commonMarkerFiles = allTifs.stream()
                .filter(f -> NamingUtils.normalize(NamingUtils.baseNameNoExt(f)).contains(cfg.commonMarker()))
                .sorted(Comparator.comparing(File::getName))
                .collect(Collectors.toList());

        if (commonMarkerFiles.isEmpty())
            throw new IllegalArgumentException("No files matching common marker '" + cfg.commonMarker() + "'");

        // Open first common marker as reference
        ImagePlus ref = IJ.openImage(commonMarkerFiles.get(0).getAbsolutePath());
        if (ref == null) throw new IllegalStateException("Failed to open reference image");
        ref.show(); // keep visible for IJ.run targets

        final String refTitle = ref.getTitle();
        final int width = ref.getWidth();
        final int height = ref.getHeight();
        final int bitDepth = ref.getBitDepth();

        // Build <common>_stack.tif and collect landmark ROIs for each subsequent common marker
        ImagePlus commonStack = NewImage.createImage(
                cfg.commonMarker() + "_stack", width, height, 1, bitDepth, NewImage.FILL_BLACK);
        commonStack.show();

        // Paste reference into first slice
        IJ.run(ref, "Select All", "");
        IJ.run(ref, "Copy", "");
        IJ.run(ref, "Select None", "");
        IJ.selectWindow(commonStack.getTitle());
        IJ.run("Paste", "");
        // ✅ FIX: use slice label instead of "Set Metadata..."
        commonStack.getStack().setSliceLabel(refTitle, commonStack.getStackSize());

        // For each subsequent round common marker: find correspondences, add slice to common stack
        for (int i = 1; i < commonMarkerFiles.size(); i++) {
            ImagePlus target = IJ.openImage(commonMarkerFiles.get(i).getAbsolutePath());
            if (target == null) continue;
            target.show();

            boolean ok = FeatureMatching.matchWithFallbacks(
                    ref, target, cfg.commonMarker(), i, cfg.minimalInlierRatio(), cfg.stepsPerScaleOctave());

            if (!ok) {
                target.close();
                throw new IllegalStateException("Couldn't find matches for " + refTitle + " vs " + target.getTitle());
            }

            // Append target to common stack
            IJ.selectWindow(commonStack.getTitle());
            IJ.run("Add Slice", "");
            IJ.selectWindow(target.getTitle());
            IJ.run("Select All", ""); IJ.run("Copy", ""); IJ.run("Select None", "");
            IJ.selectWindow(commonStack.getTitle());
            IJ.run("Paste", "");
            // ✅ FIX: set slice label here too
            commonStack.getStack().setSliceLabel(target.getTitle(), commonStack.getStackSize());
            target.close();
        }

        // Save and close common stack
        IJ.saveAs(commonStack, "Tiff", new File(resultsDir,
                cfg.commonMarker() + "_stack.tif").getAbsolutePath());
        commonStack.close();

        // Start final stack with one blank slice
        ImagePlus finalStack = NewImage.createImage(
                "STACK", width, height, 1, bitDepth, NewImage.FILL_BLACK);
        finalStack.show();

        // Iterate rounds 1..N
        int roiPairOffset = 0; // after each round>1, we advance by +2 ROI entries (ref + target)
        for (int round = 1; round <= cfg.multiplexRounds(); round++) {
            final String layerName = cfg.layerKeyword() + round; // e.g., "layer1", "layer2"

            // All files in this round EXCEPT the common marker
            List<File> roundFiles = allTifs.stream()
                    .filter(f -> {
                        String base = NamingUtils.normalize(NamingUtils.baseNameNoExt(f));
                        return base.contains(layerName) && !base.contains(cfg.commonMarker());
                    })
                    .sorted(Comparator.comparing(File::getName))
                    .collect(Collectors.toList());

            if (round == 1) {
                // Put common ref first
                IJ.selectWindow(refTitle);
                IJ.run("Select All", ""); IJ.run("Copy", ""); IJ.run("Select None", "");
                IJ.selectWindow(finalStack.getTitle());
                IJ.run("Paste", "");
                // ✅ FIX: label this slice
                finalStack.getStack().setSliceLabel(refTitle, finalStack.getStackSize());

                // Then every other marker as-is
                for (File f : roundFiles) {
                    ImagePlus imp = IJ.openImage(f.getAbsolutePath());
                    if (imp == null) continue;
                    imp.show();
                    IJ.selectWindow(finalStack.getTitle());
                    IJ.run("Add Slice", "");
                    IJ.selectWindow(imp.getTitle());
                    IJ.run("Select All", ""); IJ.run("Copy", "");
                    IJ.selectWindow(finalStack.getTitle());
                    IJ.run("Paste", "");
                    // ✅ FIX: label slice
                    finalStack.getStack().setSliceLabel(imp.getTitle(), finalStack.getStackSize());
                    imp.close();
                }
            } else {
                // For subsequent rounds, align each marker using stored landmark correspondences
                RoiManager rm = RoiManager.getInstance2();
                if (rm == null || rm.getCount() < roiPairOffset + 2)
                    throw new IllegalStateException("ROI correspondences missing for round " + round);

                for (File f : roundFiles) {
                    ImagePlus imp = IJ.openImage(f.getAbsolutePath());
                    if (imp == null) continue;
                    imp.show();

                    // By convention: select the pair for this round
                    rm.select(roiPairOffset);     // ref
                    IJ.selectWindow(refTitle);
                    rm.select(roiPairOffset + 1); // target
                    IJ.selectWindow(imp.getTitle());

                    // Apply warp
                    String args = String.format(
                            "source_image=[%s] template_image=[%s] transformation_method=[Least Squares] alpha=1 " +
                                    "mesh_resolution=32 transformation_class=Affine interpolate",
                            imp.getTitle(), refTitle);
                    IJ.run("Landmark Correspondences", args);

                    // The plugin opens an aligned copy
                    ImagePlus aligned = WindowManager.getCurrentImage();
                    if (aligned == null) aligned = imp;

                    aligned.show();
                    // Append to final stack
                    IJ.selectWindow(finalStack.getTitle());
                    IJ.run("Add Slice", "");
                    IJ.selectWindow(aligned.getTitle());
                    IJ.run("Select All", ""); IJ.run("Copy", "");
                    IJ.selectWindow(finalStack.getTitle());
                    IJ.run("Paste", "");
                    // ✅ FIX: label slice
                    finalStack.getStack().setSliceLabel(imp.getTitle(), finalStack.getStackSize());

                    aligned.close();
                    imp.close();
                }
                roiPairOffset += 2; // advance for next round
            }
        }

        // Save final composite
        IJ.selectWindow(finalStack.getTitle());
        IJ.run("Stack to Hyperstack...", "order=xyczt channels=" +
                finalStack.getStackSize() + " slices=1 frames=1 display=Grayscale");
        IJ.saveAs(finalStack, "Tiff", new File(resultsDir, "Aligned_Stack.tif").getAbsolutePath());

        // Save ROI correspondences
        RoiManager rm = RoiManager.getInstance2();
        if (rm != null) {
            rm.deselect();
            rm.runCommand("Save",
                    new File(resultsDir, "landmark_correspondences.zip").getAbsolutePath());
        }

        // Cleanup
        finalStack.close();
        ref.close();
        IJ.showMessage("Multiplex Registration", "Done.\nSaved to: " + resultsDir.getAbsolutePath());
    }
}
