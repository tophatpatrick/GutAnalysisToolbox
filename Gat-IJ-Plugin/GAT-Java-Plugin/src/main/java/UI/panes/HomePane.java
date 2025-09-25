package UI.panes;

import UI.Handlers.Navigator;
import UI.panes.Tools.HelpAndSupportPane;
import UI.panes.SettingPanes.NeuronWorkflowPane;
import UI.panes.SettingPanes.MultichannelPane;
import UI.panes.SettingPanes.MultiChannelNoHuPane;
import ij.IJ;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.dnd.*;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.prefs.Preferences;

public class HomePane extends JPanel {

    public static final String Name = "Home";

    private final Navigator navigator;

    private final DateTimeFormatter clockFmt = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy  HH:mm:ss");
    private final JLabel clockLabel   = new JLabel();
    private final JLabel greetingLabel= new JLabel();
    private final JLabel titleLabel   = new JLabel("Gut Analysis Toolbox", SwingConstants.CENTER);
    private final Timer  clockTimer;

    // Recents
    private final DefaultListModel<String> recentModel = new DefaultListModel<>();
    private final JList<String> recentList = new JList<>(recentModel);
    private final Preferences prefs = Preferences.userNodeForPackage(HomePane.class);
    private static final String RECENTS_KEY = "recentImagesV1";
    private static final int RECENTS_MAX = 4;

    // Expected model names (under <Fiji>/models)
    private static final String HU_ZIP_PRIMARY      = "2D_enteric_neuron_v4_1.zip";
    private static final String HU_ZIP_FALLBACK     = "2D_enteric_neuron_v4.zip";
    private static final String SUBTYPE_ZIP         = "2D_enteric_neuron_subtype_v4.zip";
    private static final String GANGLIA_DIJ_FOLDER  = "2D_Ganglia_RGB_v3.bioimage.io.model";

    public HomePane(Navigator navigator) {
        super(new BorderLayout(10,10));
        this.navigator = navigator;

        setBorder(BorderFactory.createEmptyBorder(16,16,16,16));

        // ----- Top bar: greeting | title | clock+help -----
        JPanel top = new JPanel(new BorderLayout(8,8));
        top.setOpaque(false);

        greetingLabel.setFont(greetingLabel.getFont().deriveFont(Font.BOLD, 16f));
        top.add(greetingLabel, BorderLayout.WEST);

        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.PLAIN, 20f));
        top.add(titleLabel, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        clockLabel.setFont(clockLabel.getFont().deriveFont(13f));
        right.add(clockLabel);

        JButton helpBtn = new JButton("Help & Support");
        helpBtn.addActionListener(e -> navigator.show(HelpAndSupportPane.Name));
        right.add(helpBtn);

        top.add(right, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        updateClockAndGreeting();
        clockTimer = new Timer(1000, e -> updateClockAndGreeting());
        clockTimer.setInitialDelay(0);
        clockTimer.start();

        // ----- Main column -----
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setOpaque(false);

        col.add(section("Welcome", welcomeButtons()));
        col.add(section("Open or drop", dropZone()));
        col.add(section("Tip of the day", tipOfDay()));
        col.add(section("Models & assets", modelsAndAssets()));
        col.add(sectionFill("Recent images", recents()));

        JScrollPane scroll = new JScrollPane(
                col,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        );
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
    }

    // ========== Sections ==========

    private JComponent section(String title, JComponent content) {
        JPanel box = new JPanel(new BorderLayout());
        box.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                title,
                TitledBorder.LEFT,
                TitledBorder.TOP
        ));
        box.add(content, BorderLayout.CENTER);
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        normalizeSectionWidth(box);
        return box;
    }

    private JComponent welcomeButtons() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        row.setOpaque(false);

        JLabel lead = new JLabel("Let’s analyse some images.");
        lead.setBorder(new EmptyBorder(0,0,0,6));
        row.add(lead);

        JButton neurons = new JButton("Analyse Neurons");
        neurons.addActionListener(e -> navigator.show(NeuronWorkflowPane.Name));
        row.add(neurons);

        JButton multiplex = new JButton("Multichannel Workflow");
        multiplex.addActionListener(e -> navigator.show(MultichannelPane.Name));
        row.add(multiplex);

        JButton noHu = new JButton("Multi-Channel (No Hu)");
        noHu.addActionListener(e -> navigator.show(MultiChannelNoHuPane.Name));
        row.add(noHu);

        return row;
    }

    private JComponent dropZone() {
        JPanel drop = new JPanel(new BorderLayout());
        drop.setBorder(new DashBorder(UIManager.getColor("Label.disabledForeground")));
        drop.setBackground(UIManager.getColor("Panel.background"));
        drop.setOpaque(true);

        JLabel hint = new JLabel("Drop an image file here (.tif, .lif, .czi, etc.) to preview in ImageJ",
                SwingConstants.CENTER);
        hint.setBorder(new EmptyBorder(18, 8, 18, 8));
        drop.add(hint, BorderLayout.CENTER);

        // DnD
        new DropTarget(drop, new DropTargetAdapter() {
            @Override public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) dtde.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) openImage(files.get(0));
                } catch (Exception ignore) { }
            }
        });
        // Click to open
        drop.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { openFileDialogAndOpen(); }
        });

        return drop;
    }

    private JComponent tipOfDay() {
        String[] tips = new String[]{
                "Use <b>Preview</b> in Neuron workflow to verify channel order before a long run.",
                "Try <b>Ganglia ▸ DEEPIMAGEJ</b> first, then analyse further with <b>IMPORT ROI</b>.",
                "Enable <b>Require microns calibration</b> to avoid mis-scaled size filters.",
                "Keep models under Fiji/Models so every pane can find them."
        };
        int idx = java.time.LocalDate.now().getDayOfYear() % tips.length;
        JLabel tip = new JLabel("<html><body style='width:100%; padding:2px 0;'>" + tips[idx] + "</body></html>");
        tip.setBorder(new EmptyBorder(6,6,6,6));
        return tip;
    }

    private JComponent modelsAndAssets() {
        JPanel g = new JPanel(new GridBagLayout());
        g.setOpaque(false);
        GridBagConstraints l = new GridBagConstraints();
        GridBagConstraints r = new GridBagConstraints();
        l.gridx=0; l.gridy=0; l.anchor=GridBagConstraints.WEST; l.insets=new Insets(3,8,3,8);
        r.gridx=1; r.gridy=0; r.anchor=GridBagConstraints.WEST; r.insets=new Insets(3,8,3,8);

        // Where models live
        File modelsDir = new File(IJ.getDirectory("imagej"), "models");

        addModelRow(g, l, r, "Hu StarDist model",
                firstExisting(modelsDir, HU_ZIP_PRIMARY, HU_ZIP_FALLBACK));

        addModelRow(g, l, r, "Ganglia model (DeepImageJ folder)",
                new File(modelsDir, GANGLIA_DIJ_FOLDER).exists()
                        ? new File(modelsDir, GANGLIA_DIJ_FOLDER).getName()
                        : null);

        addModelRow(g, l, r, "Subtype StarDist model",
                new File(modelsDir, SUBTYPE_ZIP).isFile() ? SUBTYPE_ZIP : null);

        // Open models folder (handy)
        r.gridy++; r.gridwidth = 2;
        JButton openModels = new JButton("Open models folder…");
        openModels.addActionListener(e -> {
            try { Desktop.getDesktop().open(modelsDir); } catch (Throwable ignore) { }
        });
        g.add(openModels, r);

        return g;
    }

    private static void addModelRow(JPanel g, GridBagConstraints l, GridBagConstraints r,
                                    String label, String foundName) {
        JLabel left = new JLabel(label + ":");
        g.add(left, l);

        String text = (foundName != null)
                ? "Found — " + foundName
                : "Missing — check <Fiji>/models";
        JLabel right = new JLabel(text);
        g.add(right, r);

        l.gridy++; r.gridy++;
    }

    private static String firstExisting(File dir, String... names) {
        for (String n : names) {
            if (n == null) continue;
            File f = new File(dir, n);
            if (f.isFile()) return f.getName();
        }
        return null;
    }

    private JComponent recents() {
        loadRecents();

        JPanel p = new JPanel(new BorderLayout());
        Color bg = HomePane.this.getBackground();
        p.setOpaque(true);
        p.setBackground(bg);

        if (recentModel.isEmpty()) {
            JLabel empty = new JLabel("No recent images yet. Open or drop a file to see it here.");
            empty.setBorder(new EmptyBorder(6,6,6,6));
            p.add(empty, BorderLayout.NORTH);
            return p;
        }

        // List styling (no scroll pane)
        recentList.setOpaque(true);
        recentList.setBackground(bg);
        recentList.setForeground(UIManager.getColor("Label.foreground"));
        recentList.setSelectionBackground(UIManager.getColor("List.selectionBackground"));
        recentList.setSelectionForeground(UIManager.getColor("List.selectionForeground"));
        recentList.setVisibleRowCount(RECENTS_MAX);        // advertise height for BoxLayout

        // Ensure unselected rows use the same dark background
        recentList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (!isSelected) {
                    lbl.setOpaque(true);
                    lbl.setBackground(list.getBackground());
                    lbl.setForeground(list.getForeground());
                }
                return lbl;
            }
        });

        recentList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        recentList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && recentList.getSelectedValue() != null) {
                openImage(new File(recentList.getSelectedValue()));
            }
        });


        p.add(recentList, BorderLayout.CENTER);
        return p;
    }


    // ========== Helpers ==========

    private void updateClockAndGreeting() {
        LocalDateTime now = LocalDateTime.now();
        clockLabel.setText(clockFmt.format(now));
        int h = now.getHour();
        String part = (h < 5) ? "Good night"
                : (h < 12) ? "Good morning"
                : (h < 17) ? "Good afternoon"
                : "Good evening";
        greetingLabel.setText(part);
    }

    private void openFileDialogAndOpen() {
        JFileChooser ch = new JFileChooser();
        ch.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (ch.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            openImage(ch.getSelectedFile());
        }
    }

    private void openImage(File f) {
        if (f == null || !f.exists()) return;
        new SwingWorker<Void,Void>() {
            @Override protected Void doInBackground() {
                try { IJ.open(f.getAbsolutePath()); } catch (Throwable ignore) {}
                return null;
            }
        }.execute();
        rememberRecent(f);
    }

    private void rememberRecent(File f) {
        try {
            String existing = prefs.get(RECENTS_KEY, "");
            java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
            if (!existing.isEmpty()) for (String s : existing.split("\\|")) if (!s.isEmpty()) set.add(s);
            set.remove(f.getAbsolutePath()); // move to end
            set.add(f.getAbsolutePath());
            while (set.size() > RECENTS_MAX) set.remove(set.iterator().next());
            prefs.put(RECENTS_KEY, String.join("|", set));
            loadRecents();
        } catch (Throwable ignore) { }
    }

    private void loadRecents() {
        recentModel.clear();
        String existing = prefs.get(RECENTS_KEY, "");
        if (!existing.isEmpty()) {
            String[] items = existing.split("\\|");
            for (int i = items.length - 1; i >= 0; i--) { // newest first
                if (!items[i].isEmpty()) recentModel.addElement(items[i]);
            }
        }
    }

    // Add alongside your existing section(...) helper
    private JComponent sectionFill(String title, JComponent content) {
        JPanel box = new JPanel(new BorderLayout());
        box.setOpaque(true);
        box.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), title,
                TitledBorder.LEFT, TitledBorder.TOP));
        box.add(content, BorderLayout.CENTER);
        box.setAlignmentX(Component.LEFT_ALIGNMENT);

        // allow this section to grow vertically to take leftover space
        box.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return box;
    }






    private static void normalizeSectionWidth(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension pref = c.getPreferredSize();
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));
    }

    // simple dashed border to match the theme
    static class DashBorder extends LineBorder {
        public DashBorder(Color color) { super(color != null ? color : new Color(140,140,140), 1, true); }
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float[] dash = {6f, 6f};
            g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, dash, 0f));
            g2.setColor(lineColor);
            g2.drawRoundRect(x+2, y+2, w-5, h-5, 10, 10);
            g2.dispose();
        }
    }
}
