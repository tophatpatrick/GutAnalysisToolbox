package services.multiplex.ui;

import services.multiplex.config.MultiplexConfig;
import services.multiplex.core.MultiplexRegistrationService;

import javax.swing.*;
import java.io.File;

/** Tiny helper to run the core without ImageJ's plugin menu (useful during development). */
public class MinimalRunner {
    public static void main(String[] args) {
        JFileChooser c = new JFileChooser();
        c.setDialogTitle("Select folder with immuno files");
        c.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (c.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;

        MultiplexConfig cfg = new MultiplexConfig.Builder()
                .imageFolder(c.getSelectedFile())
                .commonMarker("Hu")
                .multiplexRounds(3)
                .layerKeyword("Layer")
                .build();

        new MultiplexRegistrationService(cfg).run();
    }
}
