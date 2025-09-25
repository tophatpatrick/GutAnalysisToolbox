package services.multiplex.config;

import java.io.File;

/**
 * Immutable configuration for multiplex registration.
 * Mirrors the macro parameters one-to-one.
 */
public final class MultiplexConfig {

    private final File imageFolder;         // Folder with all images
    private final String commonMarker;      // e.g., "Hu" or "DAPI"
    private final int multiplexRounds;      // e.g., 3
    private final String layerKeyword;      // e.g., "layer" or "round" (case-insensitive match)
    private final File saveFolder;          // Where to write outputs (Results subfolder will be created)
    private final boolean fineTuneParams;   // Whether to override defaults
    private final double minimalInlierRatio;// SIFT param
    private final int stepsPerScaleOctave;  // SIFT param

    private MultiplexConfig(Builder b) {
        this.imageFolder = b.imageFolder;
        this.commonMarker = b.commonMarker.toLowerCase().trim();
        this.multiplexRounds = b.multiplexRounds;
        this.layerKeyword = b.layerKeyword.toLowerCase().replace(" ", "");
        this.saveFolder = (b.saveFolder != null) ? b.saveFolder : b.imageFolder;
        this.fineTuneParams = b.fineTuneParams;
        this.minimalInlierRatio = b.minimalInlierRatio;
        this.stepsPerScaleOctave = b.stepsPerScaleOctave;
    }

    public File imageFolder() { return imageFolder; }
    public String commonMarker() { return commonMarker; }
    public int multiplexRounds() { return multiplexRounds; }
    public String layerKeyword() { return layerKeyword; }
    public File saveFolder() { return saveFolder; }
    public boolean fineTuneParams() { return fineTuneParams; }
    public double minimalInlierRatio() { return minimalInlierRatio; }
    public int stepsPerScaleOctave() { return stepsPerScaleOctave; }

    public static class Builder {
        private File imageFolder;
        private String commonMarker = "hu";
        private int multiplexRounds = 2;
        private String layerKeyword = "layer";
        private File saveFolder;
        private boolean fineTuneParams = false;
        private double minimalInlierRatio = 0.50;
        private int stepsPerScaleOctave = 3;

        public Builder imageFolder(File f) { this.imageFolder = f; return this; }
        public Builder commonMarker(String s) { this.commonMarker = s; return this; }
        public Builder multiplexRounds(int n) { this.multiplexRounds = n; return this; }
        public Builder layerKeyword(String s) { this.layerKeyword = s; return this; }
        public Builder saveFolder(File f) { this.saveFolder = f; return this; }
        public Builder fineTuneParams(boolean b) { this.fineTuneParams = b; return this; }
        public Builder minimalInlierRatio(double d) { this.minimalInlierRatio = d; return this; }
        public Builder stepsPerScaleOctave(int i) { this.stepsPerScaleOctave = i; return this; }

        public MultiplexConfig build() {
            if (imageFolder == null || !imageFolder.isDirectory())
                throw new IllegalArgumentException("imageFolder must be a directory");
            if (multiplexRounds < 1)
                throw new IllegalArgumentException("multiplexRounds must be >= 1");
            return new MultiplexConfig(this);
        }
    }
}
