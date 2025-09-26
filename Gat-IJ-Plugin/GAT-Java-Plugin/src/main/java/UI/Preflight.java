package UI;

import ij.IJ;
import ij.Menus;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;

import ij.WindowManager;
import net.haesleinhuepf.clij2.CLIJ2;

public final class Preflight {
    private Preflight(){}

    /**
     * Run everything. Pass expected model filenames (just the names inside Fiji/models), or null to only list what’s found.
     * Returns true if it’s safe to continue launching the UI.
     */
    public static boolean runAll(String expectedNeuronModel, String expectedSubtypeModel) {
        boolean logWasOpen = isLogOpen();
        logHeader();


        // 1) First-run sentinel + DeepImageJ engines check
        if (!firstRunAndDeepImageJ()) return false;

        // 2) System / GPU / RAM report (best-effort)
        reportSystem();

        // 3) Check models in <Fiji>/models (no macros or IJM tables)
        if (!checkModels(expectedNeuronModel, expectedSubtypeModel)) return false;

        // 4) Check required commands/plugins are present
        if (!checkPlugins()) return false;

        IJ.log("****** DONE – environment looks good. ******");

        if (!logWasOpen) {
            closeLogWindowIfOpen();
        }
        return true;
    }

    // ---------- 1) First-run + DeepImageJ engines ----------

    private static boolean firstRunAndDeepImageJ() {
        String fijiDir = IJ.getDirectory("imagej"); // ends with separator on IJ1
        if (fijiDir == null) {
            JOptionPane.showMessageDialog(null, "Could not determine Fiji directory.", "GAT", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        File commandsDir = new File(new File(new File(new File(fijiDir, "scripts"), "GAT"), "Tools"), "commands");
        commandsDir.mkdirs();
        File sentinel = new File(commandsDir, "gat_init_deepimagej_check_file");

        if (!sentinel.exists()) {
            // First time
            JOptionPane.showMessageDialog(null,
                    "Thanks for installing GAT.\nWe're going to verify DeepImageJ and required components.",
                    "GAT – First time", JOptionPane.INFORMATION_MESSAGE);
            try {
                writeText(sentinel, "file_check_deepimagej\n");
            } catch (IOException e) {
                IJ.log("Could not write first-run sentinel: " + e.getMessage());
            }
        }

        // DeepImageJ initialized? (engines folder)
        File engines = new File(fijiDir, "engines");
        if (!engines.isDirectory()) {
            String msg =
                    "DeepImageJ needs to be initialized.\n\n" +
                            "Please run: Plugins -> DeepImageJ  \n -> DeepImageJ Run\n" +
                            "This will download the \n required engine files.\n\n" +
                            "When finished, start GAT again.";
            IJ.log(msg);
            JOptionPane.showMessageDialog(null, msg, "GAT – DeepImageJ not initialized", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        IJ.log("DeepImageJ already initialized.");
        return true;
    }

    // ---------- 2) System report ----------

    private static void reportSystem() {
        try {
            IJ.log("****** System Config ******");
            IJ.log("Date: " + new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date()));

            // RAM (JVM)
            long max = Runtime.getRuntime().maxMemory();
            long total = Runtime.getRuntime().totalMemory();
            long free = Runtime.getRuntime().freeMemory();
            IJ.log(String.format(java.util.Locale.US,
                    "JVM memory (GB): max=%.1f  total=%.1f  free=%.1f",
                    max / 1e9, total / 1e9, free / 1e9));
            if (max < 20L * 1024L * 1024L * 1024L) {
                IJ.log("Note: Fiji JVM has < ~20 GB max heap. For large images, consider 32 GB+.");
            }

            // GPU via CLIJ2 (best-effort)
            try {
                CLIJ2 clij2 = CLIJ2.getInstance();
                String gpuName = clij2.getGPUName();
                IJ.log("OpenCL Device: " + gpuName);
                clij2.clear();
            } catch (Throwable t) {
                IJ.log("CLIJ2 GPU info not available: " + t.getMessage());
            }
            IJ.log("***************************");
        } catch (Throwable t) {
            IJ.log("System report failed: " + t.getMessage());
        }
    }

    // ---------- 3) Model checks ----------

    private static boolean checkModels(String expectedNeuronModel, String expectedSubtypeModel) {
        String fijiDir = IJ.getDirectory("imagej");
        File modelsDir = new File(fijiDir, "models");
        if (!modelsDir.isDirectory()) {
            warnStop("Cannot find Fiji models folder:\n" + modelsDir.getAbsolutePath());
            return false;
        }

        IJ.log("****** Checking models in: " + modelsDir.getAbsolutePath() + " ******");

        // Collect available files (top-level only)
        String[] files = modelsDir.list();
        if (files == null) files = new String[0];
        Arrays.sort(files, String.CASE_INSENSITIVE_ORDER);

        // If expectations provided, verify presence
        boolean ok = true;
        if (expectedNeuronModel != null && !expectedNeuronModel.trim().isEmpty()) {
            File f = new File(modelsDir, expectedNeuronModel);
            if (!f.exists()) {
                IJ.log("Missing neuron model: " + expectedNeuronModel);
                ok = false;
            } else {
                IJ.log("Neuron model OK: " + expectedNeuronModel);
            }
        }
        if (expectedSubtypeModel != null && !expectedSubtypeModel.trim().isEmpty()) {
            File f = new File(modelsDir, expectedSubtypeModel);
            if (!f.exists()) {
                IJ.log("Missing subtype model: " + expectedSubtypeModel);
                ok = false;
            } else {
                IJ.log("Subtype model OK: " + expectedSubtypeModel);
            }
        }

        // If no expectation provided, list helpful candidates
        if ((expectedNeuronModel == null || expectedNeuronModel.isEmpty())
                || (expectedSubtypeModel == null || expectedSubtypeModel.isEmpty())) {
            IJ.log("Models found:");
            for (String name : files) {
                if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")
                        || name.toLowerCase(java.util.Locale.ROOT).contains("bioimage.io")) {
                    IJ.log(" - " + name);
                }
            }
        }

        if (!ok) {
            JOptionPane.showMessageDialog(null,
                    "Cannot find one or more model files in Fiji/models.\n" +
                            "See the Log window for details and available models.",
                    "GAT – Models missing", JOptionPane.ERROR_MESSAGE);
        }
        return ok;
    }

    // ---------- 4) Plugin/command checks ----------

    private static boolean checkPlugins() {
        IJ.log("****** Checking required plugins/commands ******");

        // command name -> guidance if missing
        LinkedHashMap<String,String> required = new LinkedHashMap<>();
        required.put("DeepImageJ Run", "Add the DeepImageJ update site: https://sites.imagej.net/DeepImageJ/");
        required.put("Command From Macro", "Enable StarDist + CSBDeep update sites.");
        // MorpholibJ: some installs expose 'Area Opening' or 'Size Opening 2D/3D'
        required.put("Area Opening", "Enable the IJPB-plugins update site (MorphoLibJ).");
        required.put("Size Opening 2D/3D", "Enable the IJPB-plugins update site (MorphoLibJ).");
        // CLIJ/CLIJ2
        required.put("CLIJ Macro Extensions", "Enable update sites for CLIJ and CLIJ2: https://clij.github.io/clij2-docs/installationInFiji");
        required.put("CLIJ2 Macro Extensions", "Enable update sites for CLIJ and CLIJ2: https://clij.github.io/clij2-docs/installationInFiji");
        // StackReg (BIG-EPFL)
        required.put("StackReg", "Enable the BIG-EPFL update site.");
        // PT-BIOP (optional but recommended)
        required.put("Label Map to ROIs", "Enable the PT-BIOP update site: https://biop.epfl.ch/Fiji-Update/");

        @SuppressWarnings("rawtypes")
        Map commands = Menus.getCommands(); // command -> class name
        Set<String> keys = new HashSet<>();
        for (Object k : commands.keySet()) keys.add(String.valueOf(k));

        boolean missingAny = false;

        // helper: test either exact key or “starts with” for quirky names
        for (Map.Entry<String,String> e : required.entrySet()) {
            String want = e.getKey();
            boolean present = hasCommand(keys, want);
            if (!present) {
                missingAny = true;
                IJ.log("Missing: " + want + "  → " + e.getValue());
            } else {
                IJ.log(want + " ... OK!");
            }
        }

        IJ.log("***********************************************");

        if (missingAny) {
            JOptionPane.showMessageDialog(null,
                    "Some required plugins are missing.\nSee the Log window for which ones and how to enable them.",
                    "GAT – Plugins missing", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }

    private static boolean hasCommand(Set<String> keys, String want) {
        if (keys.contains(want)) return true;
        // Try forgiving matches for names with/without spaces / suffixes
        String w = want.trim().toLowerCase(java.util.Locale.ROOT);
        for (String k : keys) {
            String kk = k.trim().toLowerCase(java.util.Locale.ROOT);
            if (kk.equals(w)) return true;
            if (kk.startsWith(w)) return true;
            if (kk.contains(w)) return true;
        }
        return false;
    }

    // ---------- utils ----------

    private static void logHeader() {
        IJ.log("===============================================");
        IJ.log("GAT – Environment check");
        IJ.log("===============================================");
    }

    private static void warnStop(String msg) {
        IJ.log(msg);
        JOptionPane.showMessageDialog(null, msg, "GAT – Check failed", JOptionPane.ERROR_MESSAGE);
    }

    private static void writeText(File f, String text) throws IOException {
        f.getParentFile().mkdirs();
        try (PrintWriter pw = new PrintWriter(f, "UTF-8")) {
            pw.print(text);
        }
    }

    private static boolean isLogOpen() {
        java.awt.Frame f = WindowManager.getFrame("Log");
        return f != null && f.isShowing();
    }

    private static void closeLogWindowIfOpen() {
        java.awt.Frame f = WindowManager.getFrame("Log");
        if (f != null) {
            // dispose() closes the TextWindow cleanly
            f.dispose();
        }
    }
}
