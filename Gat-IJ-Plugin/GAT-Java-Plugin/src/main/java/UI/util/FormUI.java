package UI.util;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Small UI utilities for consistent section boxes, info-badge tooltips,
 * and compact form layouts across panels.
 */
public final class FormUI {
    private FormUI() {}

    // ------------------ Section boxes ------------------

    /** Plain titled box; fills available width. */
    public static JPanel box(String title, Component content) {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), title,
                TitledBorder.LEFT, TitledBorder.TOP));
        outer.add(content, BorderLayout.CENTER);

        // Keep consistent width across all sections
        normalizeSectionWidth(outer);
        return outer;
    }

    /**
     * Titled box with a small info badge. The badge lives in the same row as
     * the content (top-right) so it doesn’t add extra vertical space.
     */
    public static JPanel boxWithHelp(String title, JComponent content, String helpHtml) {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), title,
                TitledBorder.LEFT, TitledBorder.TOP));

        JPanel inner = new JPanel(new BorderLayout());
        inner.setOpaque(false);
        inner.add(content, BorderLayout.CENTER);

        // Badge docked to the top-right
        JLabel info = createInfoBadge(helpHtml);
        JPanel east = new JPanel(new GridBagLayout());
        east.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.gridy = 0;
        gc.anchor = GridBagConstraints.NORTH;   // pin to top
        gc.insets = new Insets(2, 6, 0, 0);     // slight nudge down; small left gap
        east.add(info, gc);

        inner.add(east, BorderLayout.EAST);
        outer.add(inner, BorderLayout.CENTER);

        // Compact padding inside the titled border
        outer.setBorder(BorderFactory.createCompoundBorder(
                outer.getBorder(),
                BorderFactory.createEmptyBorder(8, 10, 10, 10)
        ));

        normalizeSectionWidth(outer);
        return outer;
    }

    /** Make a section expand horizontally but keep its preferred height. */
    public static void normalizeSectionWidth(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension pref = c.getPreferredSize();
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));
    }

    // ------------------ Layout helpers ------------------

    /** Horizontal row with gentle gaps; left-aligned. */
    public static JPanel row(JComponent... comps) {
        JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        for (JComponent c : comps) r.add(c);
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        return r;
    }

    /** Vertical column with a small vertical gap between children. */
    public static JPanel column(JComponent... comps) {
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        for (JComponent c : comps) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            col.add(c);
            col.add(Box.createVerticalStrut(6));
        }
        return col;
    }

    /**
     * Two-column grid for label/value rows.
     * Right column grows (good for text fields).
     */
    public static JPanel grid2(Component... kvPairs) {
        JPanel g = new JPanel(new GridBagLayout());
        GridBagConstraints lc = new GridBagConstraints();
        GridBagConstraints rc = new GridBagConstraints();
        lc.gridx = 0; lc.gridy = 0; lc.anchor = GridBagConstraints.WEST; lc.insets = new Insets(3,3,3,3);
        rc.gridx = 1; rc.gridy = 0; rc.weightx = 1; rc.fill = GridBagConstraints.HORIZONTAL; rc.insets = new Insets(3,3,3,3);
        for (int i = 0; i < kvPairs.length; i += 2) {
            g.add(kvPairs[i], lc);
            g.add(kvPairs[i+1], rc);
            lc.gridy++; rc.gridy++;
        }
        g.setAlignmentX(Component.LEFT_ALIGNMENT);
        return g;
    }

    /**
     * Compact two-column grid where the right column does NOT stretch.
     * Great for keeping spinners or small combos tight to their labels.
     */
    public static JPanel grid2Compact(Component... kvPairs) {
        JPanel g = new JPanel(new GridBagLayout());
        GridBagConstraints l = new GridBagConstraints();
        GridBagConstraints r = new GridBagConstraints();
        l.gridx = 0; l.gridy = 0; l.anchor = GridBagConstraints.WEST; l.insets = new Insets(3,3,3,6);
        r.gridx = 1; r.gridy = 0; r.anchor = GridBagConstraints.WEST; r.insets = new Insets(3,0,3,3);
        r.weightx = 0; r.fill = GridBagConstraints.NONE;  // <- no stretch
        for (int i = 0; i < kvPairs.length; i += 2) {
            g.add(kvPairs[i], l); g.add(kvPairs[i+1], r);
            l.gridy++; r.gridy++;
        }
        g.setAlignmentX(Component.LEFT_ALIGNMENT);
        return g;
    }

    /** Keep a control left-aligned even inside a wider BoxLayout container. */
    public static JComponent leftWrap(JComponent c) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.add(c);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    /**
     * Hard-limit the width of a control (e.g., spinner to 56px, combo to 180px).
     * Returns a tiny wrapper so BoxLayout respects the exact size.
     */
    public static JComponent limitWidth(JComponent c, int width) {
        Dimension d = c.getPreferredSize();
        d = new Dimension(Math.min(d.width, width), d.height);
        c.setPreferredSize(d); c.setMinimumSize(d); c.setMaximumSize(d);
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.add(c);
        return p;
    }

    // ------------------ Info badge + tooltip ------------------

    /** Small cyan “i” icon with a wrapped tooltip. */
    public static JLabel createInfoBadge(String helpHtml) {
        JLabel b = new JLabel(getInfoIcon(14)); // 14px icon
        b.setText(null);
        b.setOpaque(false);
        b.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setToolTipText(wrapTooltip(helpHtml, 360)); // wrapped tooltip
        b.getAccessibleContext().setAccessibleName("More info");
        return b;
    }

    /** Wrap HTML so Swing tooltips line-wrap instead of one long line. */
    public static String wrapTooltip(String innerHtml, int widthPx) {
        return "<html><body style='width:" + widthPx + "px; padding:6px;'>" + innerHtml + "</body></html>";
    }

    /** Get a scaled info icon (falls back to a vector draw if LAF icon missing). */
    public static Icon getInfoIcon(int sizePx) {
        Icon ui = UIManager.getIcon("OptionPane.informationIcon");
        if (ui instanceof ImageIcon) {
            Image img = ((ImageIcon) ui).getImage();
            Image scaled = img.getScaledInstance(sizePx, sizePx, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        } else if (ui != null) {
            BufferedImage bi = new BufferedImage(ui.getIconWidth(), ui.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = bi.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            ui.paintIcon(null, g2, 0, 0);
            g2.dispose();
            Image scaled = bi.getScaledInstance(sizePx, sizePx, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        }
        return new MiniInfoIcon(sizePx); // fallback vector
    }

    /** Minimal vector “i in a circle” for when the LAF icon isn't available. */
    static final class MiniInfoIcon implements Icon {
        private final int sz;
        MiniInfoIcon(int size) { this.sz = size; }
        public int getIconWidth()  { return sz; }
        public int getIconHeight() { return sz; }
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fg = UIManager.getColor("Label.foreground");
            if (fg == null) fg = new Color(190, 200, 210);
            int d = sz - 1;
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 180));
            g2.drawOval(x, y, d, d);                    // circle
            int cx = x + sz / 2;
            g2.drawLine(cx, y + (int)(sz * 0.38),       // stem
                    cx, y + (int)(sz * 0.78));
            g2.fillOval(cx - 1, y + (int)(sz * 0.25), 2, 2); // dot
            g2.dispose();
        }
    }
}
