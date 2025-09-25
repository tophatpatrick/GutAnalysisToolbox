package services.multiplex.core;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.frame.RoiManager;

import static services.multiplex.util.IJUtils.selectWindow;

/**
 * Encapsulates feature matching strategies used in the macro:
 * - SIFT
 * - MOPS (fallback)
 * - Block Matching (final fallback)
 *
 * It writes found correspondences (ROIs) into the ROI Manager:
 *   - <common>_<k>_ref on the reference image
 *   - <common>_<k>_target on the target image
 */
public final class FeatureMatching {

    private FeatureMatching() {}

    /**
     * Try SIFT → MOPS → BlockMatching to create landmark correspondences.
     * @param ref reference ImagePlus (first round common marker)
     * @param target target ImagePlus (current round common marker)
     * @param commonMarker normalized e.g. "hu"
     * @param pairIndex 1-based index for naming
     * @param minimalInlierRatio SIFT param
     * @param stepsPerScaleOctave SIFT param (will be incremented on retries)
     * @return true if correspondences found; otherwise false
     */
    public static boolean matchWithFallbacks(ImagePlus ref,
                                             ImagePlus target,
                                             String commonMarker,
                                             int pairIndex,
                                             double minimalInlierRatio,
                                             int stepsPerScaleOctave) {

        // SIFT: attempt with increasing stepsPerScaleOctave up to 30
        int steps = stepsPerScaleOctave;
        boolean found = false;
        while (!found && steps <= 30) {
            String args = String.format(
                    "source_image=[%s] target_image=[%s] initial_gaussian_blur=1.60 steps_per_scale_octave=%d " +
                            "minimum_image_size=32 maximum_image_size=%d feature_descriptor_size=4 " +
                            "feature_descriptor_orientation_bins=8 closest/next_closest_ratio=0.92 " +
                            "filter maximal_alignment_error=25 minimal_inlier_ratio=%.3f " +
                            "minimal_number_of_inliers=7 expected_transformation=Affine",
                    ref.getTitle(), target.getTitle(), steps, Math.max(ref.getWidth(), ref.getHeight()), minimalInlierRatio);

            IJ.run("Extract SIFT Correspondences", args);
            found = hasSelectionOnBoth(ref, target);
            steps += 3;
        }

        // MOPS fallback
        if (!found) {
            String args = String.format(
                    "source_image=[%s] target_image=[%s] initial_gaussian_blur=1.60 steps_per_scale_octave=%d " +
                            "minimum_image_size=64 maximum_image_size=%d feature_descriptor_size=16 " +
                            "closest/next_closest_ratio=0.92 maximal_alignment_error=25 inlier_ratio=0.50 " +
                            "expected_transformation=Affine",
                    ref.getTitle(), target.getTitle(), steps, Math.max(ref.getWidth(), ref.getHeight()));
            IJ.run("Extract MOPS Correspondences", args);
            found = hasSelectionOnBoth(ref, target);
        }

        // Block matching fallback
        if (!found) {
            String args = String.format(
                    "source_image=[%s] target_image=[%s] layer_scale=1 search_radius=50 block_radius=50 " +
                            "resolution=24 minimal_pmcc_r=0.10 maximal_curvature_ratio=1000 " +
                            "maximal_second_best_r/best_r=1 use_local_smoothness_filter " +
                            "approximate_local_transformation=Affine local_region_sigma=65 " +
                            "maximal_local_displacement=12 maximal_local_displacement_0=3 export",
                    ref.getTitle(), target.getTitle());
            IJ.run("Extract Block Matching Correspondences", args);
            found = hasSelectionOnBoth(ref, target);
        }

        if (!found) return false;

        // Save the two landmark ROI sets to ROI Manager with consistent names
        RoiManager rm = RoiManager.getInstance2();
        if (rm == null) rm = new RoiManager();

        selectWindow(ref.getTitle());
        rm.addRoi(ref.getRoi());
        rm.select(rm.getCount() - 1);
        rm.rename(rm.getCount() - 1, commonMarker + "_" + (pairIndex) + "_ref");
        IJ.run(ref, "Remove Overlay", "");
        IJ.run(ref, "Select None", "");

        selectWindow(target.getTitle());
        rm.addRoi(target.getRoi());
        rm.select(rm.getCount() - 1);
        rm.rename(rm.getCount() - 1, commonMarker + "_" + (pairIndex) + "_target");
        IJ.run(target, "Select None", "");

        return true;
    }

    /** Checks that a selection (correspondences) exists on both images. */
    private static boolean hasSelectionOnBoth(ImagePlus ref, ImagePlus target) {
        return ref.getRoi() != null && target.getRoi() != null;
    }
}
