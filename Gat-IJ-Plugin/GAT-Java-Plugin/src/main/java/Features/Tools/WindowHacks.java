// Features/Tools/WindowHacks.java
package Features.Tools;

import ij.ImagePlus;
import ij.gui.ImageWindow;

import java.awt.*;

public final class WindowHacks {
    private WindowHacks(){}

    /** Park an ImageJ window behind your UI window on the same screen. */
    public static void parkBehind(Window anchor, ImagePlus imp) {
        if (imp == null) return;
        ImageWindow w = imp.getWindow();
        if (w == null) { imp.show(); w = imp.getWindow(); }
        if (w == null) return;

        // Same screen as anchor
        if (anchor != null && anchor.getGraphicsConfiguration() != null) {
            w.setLocation(anchor.getX() + 5, anchor.getY() + anchor.getHeight() - 6);
        }

        // Make tiny + non-intrusive
        try { w.setOpacity(0f); } catch (Throwable ignore) {}   // if supported, fully transparent
        w.setFocusableWindowState(false);
        w.setResizable(false);
        w.setAlwaysOnTop(false);
        w.setType(Window.Type.UTILITY);                         // avoids Dock/taskbar noise on many setups
        w.setSize(1, 1);
        w.toBack();
    }

    /** Park any AWT/Swing Window (e.g., results, progress) the same way. */
    public static void parkBehind(Window anchor, Window w) {
        if (w == null) return;
        if (anchor != null) w.setLocation(anchor.getX() + 5, anchor.getY() + anchor.getHeight() - 6);
        try { w.setOpacity(0f); } catch (Throwable ignore) {}
        w.setFocusableWindowState(false);
        w.setAlwaysOnTop(false);
        w.setType(Window.Type.UTILITY);
        w.setSize(1, 1);
        w.toBack();
    }
}