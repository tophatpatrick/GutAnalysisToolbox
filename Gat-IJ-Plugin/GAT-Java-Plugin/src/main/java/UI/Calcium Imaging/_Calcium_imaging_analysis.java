import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.OvalRoi;
import ij.measure.ResultsTable;
import ij.plugin.ImageCalculator;
import ij.plugin.ZProjector;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;

import java.io.File;

public class CalciumImagingAnalysis {

    public static void main(String[] args) {

        String calciumPath = IJ.getFilePath("Open the aligned calcium imaging stack");
        if (calciumPath == null) return;

        // Open image
        ImagePlus imp = IJ.openImage(calciumPath);
        imp.show();

        int width = imp.getWidth();
        int height = imp.getHeight();
        int slices = imp.getNSlices();
        int frames = imp.getNFrames();

        // Ask for baseline frames if using F/F0
        GenericDialog gd = new GenericDialog("Calculate F/F0");
        gd.addNumericField("Starting frame:", 1, 0); // Added default values
        gd.addNumericField("Ending frame:", 50, 0); // Same (both suggested from walkthrough)
        gd.showDialog();
        if (gd.wasCanceled()) return;
        int startFrame = (int) gd.getNextNumber();
        int endFrame = (int) gd.getNextNumber();

        // Z-Projection for baseline
        ZProjector zp = new ZProjector(imp);
        zp.setStartSlice(startFrame);
        zp.setStopSlice(endFrame);
        zp.setMethod(ZProjector.AVG_METHOD);
        zp.doProjection();
        ImagePlus f0 = zp.getProjection();

        // Divide original stack by baseline
        ImageCalculator ic = new ImageCalculator();
        ImagePlus fOverF0 = ic.run("Divide create 32-bit stack", imp, f0);
        fOverF0.show();

        // Enhance contrast
        IJ.run(fOverF0, "Enhance Contrast", "saturated=0.35");

        // ROI Manager
        RoiManager roiManager = RoiManager.getRoiManager();
        roiManager.reset();

        // Example: add an Oval ROI
        OvalRoi roi = new OvalRoi(50, 50, 30, 30); // x, y, width, height
        fOverF0.setRoi(roi);
        roiManager.addRoi(roi);

        // Measure intensity
        ResultsTable rt = new ResultsTable();
        IJ.run(fOverF0, "Set Measurements...", "mean redirect=None decimal=2");
        roiManager.runCommand(fOverF0, "Measure");

        // Save CSV
        String outputDir = new File(calciumPath).getParent() + File.separator + "RESULTS";
        new File(outputDir).mkdirs();
        rt.save(outputDir + File.separator + "RESULTS.csv");

        System.out.println("Analysis complete. Results saved to " + outputDir);
    }
}