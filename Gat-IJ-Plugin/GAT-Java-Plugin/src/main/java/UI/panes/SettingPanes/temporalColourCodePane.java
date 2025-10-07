package UI.panes.SettingPanes;

import Features.Tools.AlignStack;
import Features.Core.Params;
import UI.Handlers.Navigator;
import UI.util.InputValidation;
import ij.IJ;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import Features.Tools.AlignStack;

import static UI.util.FormUI.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.Locale;
import java.util.Objects;

/**
 * Align Stack pane
 * Lets user configure reference frame, save options, and input image.
 */
public class temporalColourCodePane extends JPanel {
    public static final String Name = "Temporal Colour Code";
}