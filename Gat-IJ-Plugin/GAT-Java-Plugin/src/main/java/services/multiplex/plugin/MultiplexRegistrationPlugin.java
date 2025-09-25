package services.multiplex.plugin;

import ij.IJ;
import ij.plugin.PlugIn;
import services.multiplex.config.MultiplexConfig;
import services.multiplex.core.MultiplexRegistrationService;

import javax.swing.*;
import java.io.File;

/**
 * Minimal ImageJ PlugIn entry point.
 * Use this if you want to run from ImageJ/Fiji Plugins menu.
 *
 * In your existing Swing app, you can skip this and call the Service directly.
 */
public class MultiplexRegistrationPlugin implements PlugIn {

    @Override
    public void run(String arg) {
        try {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select folder with immuno files");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;
            File imageFolder = chooser.getSelectedFile();

            String common = JOptionPane.showInputDialog(null, "Enter name of common marker (e.g. Hu/DAPI)", "Hu");
            if (common == null || common.isEmpty()) return;

            String roundsStr = JOptionPane.showInputDialog(null, "Enter number of rounds of multiplexing", "3");
            int rounds = Integer.parseInt(roundsStr);

            String layerKey = JOptionPane.showInputDialog(null, "Keyword that distinguishes each batch (Layer/Round)", "Layer");
            if (layerKey == null || layerKey.isEmpty()) layerKey = "Layer";

            int answer = JOptionPane.showConfirmDialog(null, "Choose custom Save Folder?", "Save", JOptionPane.YES_NO_OPTION);
            File saveFolder = imageFolder;
            if (answer == JOptionPane.YES_OPTION) {
                JFileChooser chooser2 = new JFileChooser();
                chooser2.setDialogTitle("Select save folder");
                chooser2.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                if (chooser2.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;
                saveFolder = chooser2.getSelectedFile();
            }

            MultiplexConfig cfg = new MultiplexConfig.Builder()
                    .imageFolder(imageFolder)
                    .commonMarker(common)
                    .multiplexRounds(rounds)
                    .layerKeyword(layerKey)
                    .saveFolder(saveFolder)
                    .build();

            new MultiplexRegistrationService(cfg).run();

        } catch (Exception ex) {
            IJ.handleException(ex);
        }
    }
}
