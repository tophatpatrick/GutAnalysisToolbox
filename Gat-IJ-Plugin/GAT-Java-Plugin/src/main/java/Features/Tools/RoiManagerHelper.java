package Features.Tools;

import ij.plugin.frame.RoiManager;

/** Utilities for managing the global ImageJ RoiManager across workflows. */
public final class RoiManagerHelper {
    private RoiManagerHelper() {}

    // Handle that remembers whether we created the RM (so we can close it at the end).
    public static final class RmHandle {
        public final RoiManager rm;
        public final boolean weOpened;
        public RmHandle(RoiManager rm, boolean weOpened) { this.rm = rm; this.weOpened = weOpened; }
    }

    /** Ensure there's a global RoiManager; hide it by default. */
    public static RmHandle ensureGlobalRM() {
        RoiManager rm = RoiManager.getInstance2();
        boolean weOpened = false;
        if (rm == null) { rm = new RoiManager(); weOpened = true; } // becomes the singleton
        rm.setVisible(false); // keep hidden; let your UI show it as needed
        return new RmHandle(rm, weOpened);
    }

    /**
     * After any call that might (re)create the singleton (e.g., some plugins),
     * refresh your local reference by passing it in a one-element array:
     *   RoiManager rm = rmh.rm;
     *   RoiManagerHelpers.syncToSingleton(new RoiManager[]{ rm });
     */
    public static void syncToSingleton(RoiManager[] ref) {
        RoiManager s = RoiManager.getInstance2();
        if (s != null) ref[0] = s;
    }

    /** Close the RoiManager only if we created it in this run; otherwise leave it alone. */
    public static void maybeCloseRM(RmHandle h) {
        if (h != null && h.weOpened && h.rm != null) {
            try { h.rm.reset(); } catch (Throwable ignore) {}
            try { h.rm.setVisible(false); } catch (Throwable ignore) {}
            try { h.rm.close(); } catch (Throwable ignore) {}   // IJ API close
            try { h.rm.dispose(); } catch (Throwable ignore) {} // AWT Frame disposal
        }
    }
}
