// UI/util/GatWindows.java
package UI.util;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class GatWindows {
    private GatWindows() {}

    private static Window MAIN;
    // remember which windows we already nudged (avoid multiple toggles)
    private static final Set<Window> NUDGED = Collections.newSetFromMap(new WeakHashMap<>());

    /** Call once from GatPluginUI after you create the main dialog. */
    public static void install(Window main) {
        MAIN = main;
        if (MAIN != null) MAIN.setAutoRequestFocus(true);
    }

    /** Owner for popups you create (JOptionPane, custom dialogs, etc.). */
    public static Window owner() { return MAIN; }

    /**
     * One-shot “bring-to-front” nudge for any Window (IJ ImageWindow, ROI Manager, dialogs).
     * It briefly sets always-on-top, brings front, requests focus, then reverts.
     */
    public static void nudgeFront(Window w) {
        if (w == null || NUDGED.contains(w)) return;
        NUDGED.add(w);

        SwingUtilities.invokeLater(() -> {
            try { w.setAlwaysOnTop(true); } catch (Throwable ignore) {}
            w.toFront();
            w.requestFocus();
            Timer t = new Timer(250, e -> {
                try { w.setAlwaysOnTop(false); } catch (Throwable ignore) {}
            });
            t.setRepeats(false);
            t.start();
        });
    }
}
