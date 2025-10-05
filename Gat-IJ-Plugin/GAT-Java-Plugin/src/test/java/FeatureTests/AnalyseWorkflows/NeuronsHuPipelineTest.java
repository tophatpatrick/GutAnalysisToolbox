package FeatureTests.AnalyseWorkflows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import Features.AnalyseWorkflows.NeuronsHuPipeline;
import Features.Core.Params;
import Features.Core.PluginCalls;
import Features.Tools.ImageOps;
import Features.Tools.OutputIO;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.measure.Calibration;
import ij.plugin.frame.RoiManager;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NeuronsHuPipelineTest {

//    @Test
//     void testRun_withValidParams_huReturn() {
//        // Arrange: Create mock Params with minimal valid values
//        Params params = new Params();
//        params.stardistModelZip = "src\\test\\resources\\sampleFiles\\2D_enteric_neuron_v4_1.zip";
//        params.huChannel = 1;
//        params.cellTypeName = "Neuron";
//        params.trainingRescaleFactor = 1.0;
//        params.trainingPixelSizeUm = 1.0;
//        params.rescaleToTrainingPx = false;
//        params.probThresh = 0.5;
//        params.nmsThresh = 0.5;
//        params.neuronSegLowerLimitUm = 1.0;
//        params.outputDir = "output";
//        params.requireMicronUnits = false;
//        params.useClij2EDF = false;
//        params.saveFlattenedOverlay = false;
//        params.cellCountsPerGanglia = false;
//
//        ImagePlus imp = mock(ImagePlus.class);
//        File outDir = mock(File.class);
//        ImagePlus hu = mock(ImagePlus.class);
//        ImagePlus labels = mock(ImagePlus.class);
//        RoiManager rm = mock(RoiManager.class);
//        ImagePlus correctedBinary = mock(ImagePlus.class);
//        ImagePlus cur = mock(ImagePlus.class);
//
//        Calibration calibration = new Calibration();
//        calibration.pixelWidth = 1.0;
//
//        when(imp.getTitle()).thenReturn("baseName.exe");
//        when(imp.getCalibration()).thenReturn(calibration);
//        when(imp.getNSlices()).thenReturn(1);
//        when(imp.duplicate()).thenReturn(imp);
//        when(hu.getCalibration()).thenReturn(calibration);
//        doNothing().when(rm).reset();
//        when(hu.duplicate()).thenReturn(hu);
//        doNothing().when(hu).show();
//        doNothing().when(rm).setVisible(true);
//        when(rm.runCommand(eq(hu), anyString())).thenReturn(true);
//        doNothing().when(mock(ij.gui.WaitForUserDialog.class)).show();
//        doNothing().when(hu).close();
//        doNothing().when(rm).close();
//        doNothing().when(cur).hide();
//
//        try(MockedStatic<IJ> ijMock = mockStatic(IJ.class);
//            MockedStatic<OutputIO> outputIOMock = mockStatic(OutputIO.class);
//            MockedStatic<ImageOps> imageOpsMock = mockStatic(ImageOps.class);
//            MockedStatic<PluginCalls> pluginCallsMock = mockStatic(PluginCalls.class);
//            MockedStatic<RoiManager> roiManagerMock = mockStatic(RoiManager.class);
//            MockedStatic<WindowManager> windowManagerMock = mockStatic(WindowManager.class);
//            MockedStatic<NeuronsHuPipeline> neuronsHuPipelineMock = mockStatic(NeuronsHuPipeline.class);) {
//            // Mock static methods
//            ijMock.when(IJ::getImage).thenReturn(imp);
//            outputIOMock.when(() -> OutputIO.prepareOutputDir(anyString(), eq(imp), any())).thenReturn(outDir);
//            imageOpsMock.when(() -> ImageOps.extractChannel(eq(imp), eq(params.huChannel))).thenReturn(hu);
//            pluginCallsMock.when(() -> PluginCalls.runStarDist2DLabel(eq(hu), eq(params.stardistModelZip), eq(params.probThresh), eq(params.nmsThresh))).thenReturn(labels);
//            pluginCallsMock.when(() -> PluginCalls.removeBorderLabels(eq(labels))).thenReturn(labels);
//            pluginCallsMock.when(() -> PluginCalls.labelMinSizeFilterPx(eq(labels), anyInt())).thenReturn(labels);
//            imageOpsMock.when(() -> ImageOps.resizeTo(eq(labels), anyInt(), anyInt())).thenReturn(labels);
//            roiManagerMock.when(RoiManager::getInstance2).thenReturn(rm);
//            pluginCallsMock.when(() -> PluginCalls.roisToBinary(eq(hu), eq(rm))).thenReturn(correctedBinary);
//            pluginCallsMock.when(() -> PluginCalls.binaryToLabels(eq(correctedBinary))).thenReturn(correctedBinary);
//            outputIOMock.when(() -> OutputIO.saveRois(eq(rm), any(File.class))).thenAnswer(invocation -> null);
//            outputIOMock.when(() -> OutputIO.saveFlattenedOverlay(any(ImagePlus.class), eq(rm), any(File.class))).thenAnswer(invocation -> null);
//            outputIOMock.when(() -> OutputIO.saveTiff(any(ImagePlus.class), any(File.class))).thenAnswer(invocation -> null);
//            outputIOMock.when(() -> OutputIO.writeCountsCsv(any(File.class), anyString(), anyString(), anyInt())).thenAnswer(invocation -> null);
//            windowManagerMock.when(WindowManager::getCurrentImage).thenReturn(cur);
//            neuronsHuPipelineMock.when(() -> NeuronsHuPipeline.applyWatershedInPlace(any(ImagePlus.class))).thenAnswer(invocation -> null);
//
//            // Act: Run the pipeline
//            NeuronsHuPipeline pipeline = new NeuronsHuPipeline();
//            NeuronsHuPipeline.HuResult result = pipeline.run(params, true);
//
//            // Assert: Check that result is not null and has expected fields
//            assertNotNull(result, "HuResult should not be null");
//            assertNotNull(result.max, "Max projection image should not be null");
//            assertNotNull(result.neuronLabels, "Neuron labels image should not be null");
//            assertTrue(result.totalNeuronCount >= 0, "Neuron count should be non-negative");
//            assertNotNull(result.outDir, "Output directory should not be null");
//            assertNotNull(result.baseName, "Base name should not be null");
//        }
//    }

    @Test
    void testRun_happyPath() {
        // Arrange
        Params params = new Params();
        params.stardistModelZip = "src\\test\\resources\\sampleFiles\\2D_enteric_neuron_v4_1.zip";
        params.huChannel = 1;
        params.cellTypeName = "Neuron";
        params.trainingRescaleFactor = 1.0;
        params.trainingPixelSizeUm = 1.0;
        params.rescaleToTrainingPx = false;
        params.probThresh = 0.5;
        params.nmsThresh = 0.5;
        params.neuronSegLowerLimitUm = 1.0;
        params.outputDir = "output";
        params.requireMicronUnits = false;
        params.useClij2EDF = false;
        params.saveFlattenedOverlay = false;
        params.cellCountsPerGanglia = false;

        ImagePlus imp = mock(ImagePlus.class);
        ImagePlus max = mock(ImagePlus.class);
        ImagePlus hu = mock(ImagePlus.class);
        ImagePlus labels = mock(ImagePlus.class);
        ImagePlus correctedBinary = mock(ImagePlus.class);
        ImagePlus labelsEdited = mock(ImagePlus.class);
        RoiManager rm = mock(RoiManager.class);
        File outDir = mock(File.class);
        Calibration calibration = new Calibration();
        calibration.pixelWidth = 1.0;
        calibration.setUnit("pixel");

        when(imp.getTitle()).thenReturn("test.tif");
        when(imp.getCalibration()).thenReturn(calibration);
        when(imp.getNSlices()).thenReturn(1);
        when(imp.duplicate()).thenReturn(max);
        when(max.getCalibration()).thenReturn(calibration);
        when(max.getWidth()).thenReturn(100);
        when(max.getHeight()).thenReturn(100);
        when(hu.getWidth()).thenReturn(100);
        when(hu.getHeight()).thenReturn(100);
        when(hu.duplicate()).thenReturn(hu);
        when(hu.getCalibration()).thenReturn(calibration);
        when(labels.getWidth()).thenReturn(100);
        when(labels.getHeight()).thenReturn(100);
        when(labelsEdited.getCalibration()).thenReturn(calibration);

        when(rm.getCount()).thenReturn(5);

        try (
                MockedStatic<IJ> ijMock = mockStatic(IJ.class);
                MockedStatic<OutputIO> outputIOMock = mockStatic(OutputIO.class);
                MockedStatic<ImageOps> imageOpsMock = mockStatic(ImageOps.class);
                MockedStatic<PluginCalls> pluginCallsMock = mockStatic(PluginCalls.class);
                MockedStatic<RoiManager> roiManagerMock = mockStatic(RoiManager.class);
                MockedStatic<WindowManager> windowManagerMock = mockStatic(WindowManager.class);
                MockedStatic<NeuronsHuPipeline> neuronsHuPipelineMock = mockStatic(NeuronsHuPipeline.class);
        ) {
            ijMock.when(IJ::getImage).thenReturn(imp);
            ijMock.when(() -> IJ.run(any(ImagePlus.class), anyString(), anyString())).thenAnswer(invocation -> null);
            ijMock.when(() -> IJ.resetMinAndMax(any(ImagePlus.class))).thenAnswer(invocation -> null);
            ijMock.when(() -> IJ.setTool(anyString())).thenReturn(true);

            outputIOMock.when(() -> OutputIO.prepareOutputDir(anyString(), any(ImagePlus.class), anyString())).thenReturn(outDir);
            outputIOMock.when(() -> OutputIO.saveRois(any(RoiManager.class), any(File.class))).thenAnswer(invocation -> null);
            outputIOMock.when(() -> OutputIO.saveTiff(any(ImagePlus.class), any(File.class))).thenAnswer(invocation -> null);
            outputIOMock.when(() -> OutputIO.writeCountsCsv(any(File.class), anyString(), anyString(), anyInt())).thenAnswer(invocation -> null);

            imageOpsMock.when(() -> ImageOps.extractChannel(any(ImagePlus.class), anyInt())).thenReturn(hu);

            pluginCallsMock.when(() -> PluginCalls.runStarDist2DLabel(any(ImagePlus.class), anyString(), anyDouble(), anyDouble())).thenReturn(labels);
            pluginCallsMock.when(() -> PluginCalls.removeBorderLabels(any(ImagePlus.class))).thenReturn(labels);
            pluginCallsMock.when(() -> PluginCalls.labelMinSizeFilterPx(any(ImagePlus.class), anyInt())).thenReturn(labels);
            pluginCallsMock.when(() -> PluginCalls.labelsToRois(any(ImagePlus.class))).thenAnswer(invocation -> null);
            pluginCallsMock.when(() -> PluginCalls.roisToBinary(any(ImagePlus.class), any(RoiManager.class))).thenReturn(correctedBinary);
            pluginCallsMock.when(() -> PluginCalls.binaryToLabels(any(ImagePlus.class))).thenReturn(labelsEdited);

            roiManagerMock.when(RoiManager::getInstance2).thenReturn(rm);

            neuronsHuPipelineMock.when(() -> NeuronsHuPipeline.applyWatershedInPlace(any(ImagePlus.class))).thenAnswer(invocation -> null);

            // Mock WaitForUserDialog
            try (MockedConstruction<ij.gui.WaitForUserDialog> waitForUserDialogMock = Mockito.mockConstruction(ij.gui.WaitForUserDialog.class, (mock, context) -> {
                doNothing().when(mock).show();
            })) {
                // Act
                NeuronsHuPipeline pipeline = new NeuronsHuPipeline();
                NeuronsHuPipeline.HuResult result = pipeline.run(params, true);

                // Assert
                assertNotNull(result);
                assertEquals(max, result.max);
                assertEquals(labelsEdited, result.neuronLabels);
                assertEquals(5, result.totalNeuronCount);
                assertEquals(outDir, result.outDir);
                assertEquals("test", result.baseName);
            }
        }
    }


     void testRun_IllegalArgument() {
        // Run the pipeline with null params
        NeuronsHuPipeline pipeline = new NeuronsHuPipeline();
        assertThrows(IllegalArgumentException.class, () -> pipeline.run(null, true), "Expected IllegalArgumentException for null params");
        assertThrows(IllegalArgumentException.class, () -> pipeline.run(null, false), "Expected IllegalArgumentException for null params");

        // Run the pipeline with missing required fields
        // Missing:
        // stardistModelZip
        //
        //
        Params incompleteParams = new Params();
        assertThrows(IllegalArgumentException.class, () -> pipeline.run(incompleteParams, true));
    }
}
