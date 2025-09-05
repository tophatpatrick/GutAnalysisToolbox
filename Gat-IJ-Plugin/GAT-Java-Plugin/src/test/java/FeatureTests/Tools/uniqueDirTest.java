package FeatureTests.Tools;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import Features.Tools.OutputIO;

import java.io.File;

public class uniqueDirTest {
    @Test
    public void testUniqueDir() {
        File baseDir = new File("C:/Users/jrsha/Software Projects/Monash/GAT/_Analyse Neuron (Multi-channel - No Hu).ijm");
        File uniqueDir1 = OutputIO.uniqueDir(baseDir);
//        File uniqueDir2 = OutputIO.uniqueDir(baseDir);
        assertEquals("_Analyse Neuron (Multi-channel - No Hu)_1", uniqueDir1.getName(), "unique directory should be" + uniqueDir1.getName());
    }
}
