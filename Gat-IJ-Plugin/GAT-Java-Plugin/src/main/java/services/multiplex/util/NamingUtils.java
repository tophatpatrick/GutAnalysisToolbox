package services.multiplex.util;

import java.io.File;
import java.util.Locale;

/** Helper for case-insensitive filename matching and cleaning. */
public final class NamingUtils {
    private NamingUtils() {}

    public static String baseNameNoExt(File f) {
        String name = f.getName();
        int idx = name.lastIndexOf('.');
        return (idx >= 0) ? name.substring(0, idx) : name;
    }

    public static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).trim().replace(" ", "");
    }

    public static boolean hasTifExt(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        return n.endsWith(".tif") || n.endsWith(".tiff");
    }
}
