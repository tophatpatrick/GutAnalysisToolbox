package UI.panes.Tools;

import UI.Handlers.Navigator;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.Objects;

public class MultiplexPane extends JPanel {
    public static final String Name = "Multiplex";

    // Controls
    private final JTextField immunoFolderTf = new JTextField();
    private final JButton    browseImmuno   = new JButton("Browse");

    private final JTextField huTf           = new JTextField();
    private final JSpinner   roundsSp       = new JSpinner(new SpinnerNumberModel(2, 1, 99, 1));
    private final JTextField batchTf        = new JTextField();

    private final JCheckBox  chooseSaveCb   = new JCheckBox("Choose_Save_Folder");
    private final JTextField saveFolderTf   = new JTextField();
    private final JButton    browseSave     = new JButton("Browse");

    private final JCheckBox  finetuneCb     = new JCheckBox("Finetune_parameters");

    private final JButton    runBtn         = new JButton("Run");
    private final JButton    resetBtn       = new JButton("Reset");

    public MultiplexPane(Navigator navigator) {
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(16,16,16,16));

        // Title
        JLabel title = new JLabel("Multiplex", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        add(title, BorderLayout.NORTH);

        // Main form
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 24));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(10, 8, 10, 8);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.EAST;
        gc.weightx = 0;

        // 1) Select folder with immuno files
        immunoFolderTf.setEditable(false);
        JPanel immunoRow = rowWithBrowse(immunoFolderTf, browseImmuno);
        addLabeled(form, gc, 0, "Select folder with immuno files", immunoRow);

        // 2) Common marker (Hu)
        addLabeled(form, gc, 1, "Enter name of common marker (Hu)", huTf);

        // 3) Rounds
        addLabeled(form, gc, 2, "Enter number of rounds of multiplexing", roundsSp);

        // 4) Batch label
        addLabeled(form, gc, 3, "Enter name that distinguishes each batch (Layer/Round)", batchTf);

        // 5) Choose_Save_Folder + conditional save path
        addLabeled(form, gc, 4, "", chooseSaveCb);

        saveFolderTf.setEditable(false);
        JPanel saveRow = rowWithBrowse(saveFolderTf, browseSave);
        saveRow.setVisible(false);
        addLabeled(form, gc, 5, "", saveRow);

        // 6) Finetune
        addLabeled(form, gc, 6, "", finetuneCb);

        // Actions
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(resetBtn);
        actions.add(runBtn);

        JPanel leftWrap = new JPanel(new BorderLayout());
        leftWrap.add(form, BorderLayout.NORTH);
        leftWrap.add(actions, BorderLayout.SOUTH);
        add(leftWrap, BorderLayout.WEST);

        // Behavior
        browseImmuno.addActionListener(e -> chooseDirInto(immunoFolderTf));
        browseSave.addActionListener(e -> chooseDirInto(saveFolderTf));

        chooseSaveCb.addActionListener(e -> {
            saveRow.setVisible(chooseSaveCb.isSelected());
            revalidate();
        });

        resetBtn.addActionListener(e -> resetFields());
        runBtn.addActionListener(e -> onRun());
    }

    // ---- Actions ----

    private void onRun() {
        Path immuno = pathFromText(immunoFolderTf.getText());
        Path save   = chooseSaveCb.isSelected() ? pathFromText(saveFolderTf.getText()) : null;
        String hu   = huTf.getText().trim();
        int rounds  = (Integer) roundsSp.getValue();
        String batch= batchTf.getText().trim();
        boolean finetune = finetuneCb.isSelected();

        if (immuno == null) {
            warn("Please select the folder with immuno files.");
            return;
        }
        if (hu.isEmpty()) {
            warn("Please enter the common marker name (e.g., Hu).");
            return;
        }
        if (chooseSaveCb.isSelected() && save == null) {
            warn("Please choose a save folder, or untick 'Choose_Save_Folder'.");
            return;
        }

        runBtn.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        // TODO: replace with your real call (SwingWorker recommended if long-running)
        try {
            // MultiplexService.runRegistration(immuno, hu, rounds, batch, save, finetune);
            JOptionPane.showMessageDialog(this,
                    "Multiplex Registration started\n\n"
                            + "Immuno folder: " + immuno + "\n"
                            + "Marker (Hu): " + hu + "\n"
                            + "Rounds: " + rounds + "\n"
                            + "Batch label: " + batch + "\n"
                            + "Save folder: " + (save != null ? save : "(default)") + "\n"
                            + "Finetune: " + (finetune ? "Yes" : "No"),
                    "Started", JOptionPane.INFORMATION_MESSAGE);
        } finally {
            runBtn.setEnabled(true);
            setCursor(Cursor.getDefaultCursor());
        }
    }

    private void resetFields() {
        immunoFolderTf.setText("");
        huTf.setText("");
        roundsSp.setValue(2);
        batchTf.setText("");
        chooseSaveCb.setSelected(false);
        saveFolderTf.setText("");
        finetuneCb.setSelected(false);
    }

    // ---- UI helpers ----

    private static void addLabeled(JPanel panel, GridBagConstraints gc, int row, String label, JComponent field) {
        gc.gridx = 0; gc.gridy = row; gc.weightx = 0; gc.fill = GridBagConstraints.NONE; gc.anchor = GridBagConstraints.EAST;
        if (!Objects.equals(label, "")) panel.add(new JLabel(label), gc);
        else panel.add(Box.createHorizontalStrut(1), gc); // spacer for empty label rows

        gc.gridx = 1; gc.weightx = 1; gc.fill = GridBagConstraints.HORIZONTAL; gc.anchor = GridBagConstraints.WEST;
        panel.add(field, gc);
    }

    private static JPanel rowWithBrowse(JTextField tf, JButton browseBtn) {
        JPanel row = new JPanel(new GridBagLayout());
        GridBagConstraints rgc = new GridBagConstraints();
        rgc.insets = new Insets(0,0,0,6);
        rgc.gridx = 0; rgc.weightx = 1; rgc.fill = GridBagConstraints.HORIZONTAL;
        row.add(tf, rgc);
        rgc.gridx = 1; rgc.weightx = 0; rgc.fill = GridBagConstraints.NONE;
        row.add(browseBtn, rgc);
        return row;
    }

    private void chooseDirInto(JTextField tf) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setMultiSelectionEnabled(false);
        int ret = chooser.showOpenDialog(this);
        if (ret == JFileChooser.APPROVE_OPTION) {
            tf.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private static Path pathFromText(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        return new java.io.File(s.trim()).toPath();
    }

    private void warn(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Input required", JOptionPane.WARNING_MESSAGE);
    }
}
