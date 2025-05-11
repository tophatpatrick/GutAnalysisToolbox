package org.example;

import ij.plugin.PlugIn;
import ij.gui.GenericDialog;
import javax.swing.UIManager;
import mdlaf.MaterialLookAndFeel;
import mdlaf.themes.MaterialOceanicTheme;
public class ExamplePopup implements PlugIn{
    static{
        try{
            UIManager.setLookAndFeel(
                new MaterialLookAndFeel(new MaterialOceanicTheme())
            );
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void run(String arg){
        GenericDialog gd = new GenericDialog("Material-Styled Dialog");
        gd.addMessage("Gello from Material UI + Swing!");
        gd.showDialog();
    }
}
