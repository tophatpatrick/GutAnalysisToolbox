package Analysis;
import ij.*;
import ij.plugin.frame.RoiManager;

public class ConvertLabelToROIs {

    public static void execute(String labelImage, boolean isGanglia) {
        RoiManager roiManager = RoiManager.getInstance();
        if (roiManager == null) {
            roiManager = new RoiManager();
        }
        roiManager.reset();

        if (labelImage == null || labelImage.isEmpty()) {
            IJ.error("No label image specified");
            return;
        }

        // Select the label image window
        ImagePlus imp = WindowManager.getImage(labelImage);
        if (imp == null) {
            IJ.error("Cannot find image: " + labelImage);
            return;
        }

        imp.show();
        IJ.run(imp, "Select None", "");

        if (!isGanglia) {
            IJ.run("Label image to ROIs");
        } else {
            IJ.run("Label image to composite ROIs");
        }

        roiManager.runCommand("Remove Channel Info");
        roiManager.runCommand("Remove Slice Info");
        roiManager.runCommand("Remove Frame Info");

        if (roiManager.getCount() == 0) {
            IJ.log("No labels or cells detected in image");
        }
    }

    // Overloaded method for backward compatibility
    public static void execute(String labelImage) {
        execute(labelImage, false);
    }
}
