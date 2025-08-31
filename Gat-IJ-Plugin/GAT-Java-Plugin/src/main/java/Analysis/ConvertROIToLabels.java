package Analysis;

import ij.*;
import ij.plugin.frame.RoiManager;

public class ConvertROIToLabels {

    public static void execute() {
        ImagePlus currentImage = IJ.getImage();
        if (currentImage == null) {
            IJ.error("No image open");
            return;
        }

        String imgTitle = currentImage.getTitle();
        int bitDepth = currentImage.getBitDepth();
        String imgBit;

        if (bitDepth == 8) {
            imgBit = "8-bit";
        } else if (bitDepth == 16) {
            imgBit = "16-bit";
        } else {
            imgBit = "32-bit";
        }

        int width = currentImage.getWidth();
        int height = currentImage.getHeight();
        int channels = currentImage.getNChannels();
        int slices = currentImage.getNSlices();
        int frames = currentImage.getNFrames();

        RoiManager roiManager = RoiManager.getInstance();
        if (roiManager == null || roiManager.getCount() == 0) {
            IJ.log("No ROIs");
            IJ.newImage("label_mapss", imgBit + " black", width, height, 1);
            return;
        }

        // Handle multichannel image input
        if (channels > 1) {
            IJ.run(currentImage, "Select None", "");
            IJ.run(currentImage, "Duplicate...", "title=temp channels=1");
            ImagePlus tempImage = WindowManager.getImage("temp");
            if (tempImage != null) {
                tempImage.show();
                roiManager.runCommand("Show All");
                IJ.run("Show Overlay");
                IJ.run("ROIs to Label image");
                tempImage.close();
            }
        } else {
            roiManager.runCommand("Show All");
            IJ.run("Show Overlay");
            IJ.run("ROIs to Label image");
        }

        // Wait for processing
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        ImagePlus labelImage = IJ.getImage();
        String tempTitle = labelImage.getTitle();

        if (tempTitle.equals(imgTitle)) {
            IJ.error("ROI conversion didn't work; error with plugin");
            return;
        }

        labelImage.show();
        int newChannels = labelImage.getNChannels();

        if (newChannels > 1) {
            IJ.run(labelImage, "Select None", "");
            IJ.run(labelImage, "Duplicate...", "title=label_mapss channels=1");
            labelImage.close();
        } else {
            labelImage.setTitle("label_mapss");
        }

        ImagePlus finalImage = WindowManager.getImage("label_mapss");
        if (finalImage != null) {
            IJ.run(finalImage, "Select None", "");
            finalImage.resetDisplayRange();
        }
    }
}

