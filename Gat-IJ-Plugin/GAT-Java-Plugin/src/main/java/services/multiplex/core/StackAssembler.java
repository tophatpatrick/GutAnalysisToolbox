package services.multiplex.core;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.RGBStackMerge;
import ij.process.ImageProcessor;

import static services.multiplex.util.IJUtils.copyActiveToClipboard;
import static services.multiplex.util.IJUtils.selectWindow;

/**
 * Utility to build stacks like the macro does:
 * - A stack for the common marker across rounds
 * - The final aligned multi-slice stack (later converted to composite)
 */
public final class StackAssembler {

    private StackAssembler() {}

    /** Append current active image (by title) into a stack as a new slice by Copy+Paste (exactly like macro). */
    public static void appendByCopyPaste(String srcTitle, String dstTitle, boolean addSliceIfNeeded) {
        selectWindow(srcTitle);
        copyActiveToClipboard();
        selectWindow(dstTitle);
        if (addSliceIfNeeded) IJ.run("Add Slice");
        IJ.run("Paste");
    }

    /** Convert N-slice single-Z stack to composite for visualization and save. */
    public static void saveAsComposite(ImagePlus stackImp, String outPath) {
        // Make composite in grayscale display like macro
        int nSlices = stackImp.getStackSize();
        IJ.run(stackImp, "Stack to Hyperstack...", "order=xyczt channels=" + nSlices + " slices=1 frames=1 display=Grayscale");
        IJ.saveAs(stackImp, "Tiff", outPath);
    }
}
