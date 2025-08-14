package Features.Tools;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;

public final class SilentRun {
    private SilentRun() {}

    /** Run a command bound to `imp` without showing any windows. */
    public static void on(ImagePlus imp, String command, String options) {
        WindowManager.setTempCurrentImage(imp);
        try {
            IJ.run(imp, command, options);  // bound execution
        } finally {
            WindowManager.setTempCurrentImage(null);
        }
    }
}