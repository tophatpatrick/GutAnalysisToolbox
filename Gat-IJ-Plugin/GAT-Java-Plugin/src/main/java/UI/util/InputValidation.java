package UI.util;

import ij.IJ;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Locale;

public final class InputValidation {

    private InputValidation() {}

    // Allowed image types (edit here once for all panes)
    private static final String[] IMAGE_EXTS = {
            "tif","tiff","ome.tif","czi","lif","nd2","lsm"
    };

    public static boolean isPlaceholderPath(String s) {
        if (s == null) return true;
        String t = s.trim();
        if (t.isEmpty()) return true;
        // catches “/path/to/image”, “…/path/to/…”, etc.
        return t.contains("path/to");
    }

    public static boolean hasImageExtension(File f) {
        String name = f.getName().toLowerCase(Locale.ROOT);
        for (String ext : IMAGE_EXTS) {
            if (name.endsWith("." + ext) || name.endsWith(ext)) return true;
        }
        // allow plain ".tif" check to also hit when ext list contains "ome.tif"
        return name.endsWith(".tif") || name.endsWith(".tiff");
    }

    /** Return true if OK; otherwise shows an error dialog and returns false. */
    public static boolean validateImageOrShow(Component parent, String path) {
        if (isPlaceholderPath(path)) {
            JOptionPane.showMessageDialog(parent, "Please select an input image file.",
                    "Missing image", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        File f = new File(path);
        if (!f.isFile()) {
            JOptionPane.showMessageDialog(parent, "Input image does not exist:\n" + f.getAbsolutePath(),
                    "Invalid image", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        if (!hasImageExtension(f)) {
            JOptionPane.showMessageDialog(parent,
                    "Unsupported image type. Allowed: .tif/.tiff/.ome.tif, .czi, .lif, .nd2, .lsm",
                    "Invalid image type", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }

    /** Model must be a .zip on disk. */
    public static boolean validateZipOrShow(Component parent, String path, String label) {
        if (path == null || path.trim().isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Please choose " + label + " (.zip).",
                    "Missing file", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        File f = new File(path);
        if (!f.isFile() || !f.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            JOptionPane.showMessageDialog(parent, label + " must be a .zip:\n" + f.getAbsolutePath(),
                    "Invalid file", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }

    /** Output dir is optional; create it if provided and missing. */
    public static boolean validateOutputDirOrShow(Component parent, String path) {
        if (path == null || path.trim().isEmpty()) return true; // optional
        File dir = new File(path);
        if (dir.exists()) {
            if (!dir.isDirectory()) {
                JOptionPane.showMessageDialog(parent, "Output path exists but is not a directory:\n" + dir.getAbsolutePath(),
                        "Invalid output", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            if (!dir.canWrite()) {
                JOptionPane.showMessageDialog(parent, "Output directory is not writable:\n" + dir.getAbsolutePath(),
                        "Invalid output", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            return true;
        }
        if (!dir.mkdirs()) {
            JOptionPane.showMessageDialog(parent, "Could not create output directory:\n" + dir.getAbsolutePath(),
                    "Invalid output", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }

    /** Folder name under <Fiji>/models (used by your ganglia/deepImageJ config). */
    public static boolean validateModelsFolderOrShow(Component parent, String folderName) {
        if (folderName == null || folderName.trim().isEmpty()) {
            JOptionPane.showMessageDialog(parent,
                    "Please choose a Ganglia model folder (under <Fiji>/models).",
                    "Missing ganglia model", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        File models = new File(IJ.getDirectory("imagej"), "models");
        File target = new File(models, folderName);
        if (!target.isDirectory()) {
            JOptionPane.showMessageDialog(parent,
                    "Ganglia model folder not found under <Fiji>/models:\n" + target.getAbsolutePath(),
                    "Invalid ganglia model folder", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }
}
