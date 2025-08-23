package UI.panes.Tools;

import UI.Handlers.Navigator;
import services.merge.CsvMerger;
import services.merge.DefaultLabelStrategy;
import services.merge.MergeException;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;

public class AnalysisPane extends JPanel {
    public static final String Name = "Analysis";

    // UI controls
    private final JRadioButton singleRb   = new JRadioButton("Single file (Merge files matching this filename or substring)");
    private final JRadioButton multiRb    = new JRadioButton("Multiple CSVs (Merge all CSV types based on filenames found in the first subfolder)");
    private final JTextField   patternTf  = new JTextField("results.csv", 20);
    private final JPanel       singleRow  = new JPanel(new GridBagLayout()); // holds pattern field when Single selected

    private final JTextField   rootTf     = new JTextField(28);
    private final JButton      browseBtn  = new JButton("Choose Root...");
    private final JButton      runBtn     = new JButton("Run Merge");

    // internal state
    private Path selectedRoot = null;

    public AnalysisPane(Navigator navigator) {
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // Title
        JLabel title = new JLabel("Merge Analysis", SwingConstants.LEFT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        add(title, BorderLayout.NORTH);

        // Main form (left)
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 4, 6, 4);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill   = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;

        // Mode radios
        ButtonGroup group = new ButtonGroup();
        group.add(singleRb);
        group.add(multiRb);
        singleRb.setSelected(true);

        gc.gridx = 0; gc.gridy = 0;
        form.add(new JLabel("Mode:"), gc);
        gc.gridy++;
        form.add(singleRb, gc);
        gc.gridy++;
        form.add(multiRb, gc);

        // Single pattern row (shown only when Single selected)
        gc.gridy++;
        singleRow.setVisible(true);
        GridBagConstraints sgc = new GridBagConstraints();
        sgc.insets = new Insets(0, 0, 0, 4);
        sgc.anchor = GridBagConstraints.WEST;
        singleRow.add(new JLabel("Filename (e.g. Cell_counts.csv / results.csv) :"), sgc);
        sgc.gridx = 1; sgc.fill = GridBagConstraints.HORIZONTAL; sgc.weightx = 1.0;
        singleRow.add(patternTf, sgc);
        form.add(singleRow, gc);

        // Root picker
        JPanel rootRow = new JPanel(new GridBagLayout());
        GridBagConstraints rgc = new GridBagConstraints();
        rgc.insets = new Insets(0, 0, 0, 4);
        rgc.anchor = GridBagConstraints.WEST;
        rootTf.setEditable(false);
        rootTf.setToolTipText("Root folder where your dataset lives");
        rootRow.add(new JLabel("Root folder:"), rgc);
        rgc.gridx = 1; rgc.fill = GridBagConstraints.HORIZONTAL; rgc.weightx = 1.0;
        rootRow.add(rootTf, rgc);
        rgc.gridx = 2; rgc.fill = GridBagConstraints.NONE; rgc.weightx = 0;
        rootRow.add(browseBtn, rgc);

        gc.gridy++;
        form.add(rootRow, gc);

        // Run button
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(runBtn);
        gc.gridy++;
        form.add(actions, gc);

        add(form, BorderLayout.CENTER);

        // Behavior
        singleRb.addActionListener(e -> singleRow.setVisible(true));
        multiRb.addActionListener(e -> singleRow.setVisible(false));

        browseBtn.addActionListener(e -> chooseRootDirectory());

        runBtn.addActionListener(e -> {
            if (selectedRoot == null) {
                JOptionPane.showMessageDialog(this,
                        "Please choose a ROOT folder first.",
                        "Root folder required",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Run on background thread to keep UI responsive
            runBtn.setEnabled(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

            if (singleRb.isSelected()) {
                String pattern = patternTf.getText().trim();
                if (pattern.isEmpty()) {
                    JOptionPane.showMessageDialog(this,
                            "Please enter a filename or substring (e.g. Cell_counts).",
                            "Pattern required",
                            JOptionPane.WARNING_MESSAGE);
                    runBtn.setEnabled(true);
                    setCursor(Cursor.getDefaultCursor());
                    return;
                }
                runSingle(selectedRoot, pattern);
            } else {
                runMulti(selectedRoot);
            }
        });
    }

    private void chooseRootDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select the ROOT folder for merging");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setMultiSelectionEnabled(false);
        int ret = chooser.showOpenDialog(this);
        if (ret == JFileChooser.APPROVE_OPTION) {
            selectedRoot = chooser.getSelectedFile().toPath();
            rootTf.setText(selectedRoot.toString());
        }
    }

    private void runSingle(Path root, String pattern) {
        CsvMerger merger = new CsvMerger(new DefaultLabelStrategy());
        new SwingWorker<Path, Void>() {
            @Override protected Path doInBackground() throws Exception {
                return merger.mergeSinglePattern(root, pattern);
            }
            @Override protected void done() {
                finishRun();
                try {
                    Path out = get();
                    JOptionPane.showMessageDialog(AnalysisPane.this,
                            "Merged file created:\n" + out,
                            "Merge complete",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    showMergeError(ex);
                }
            }
        }.execute();
    }

    private void runMulti(Path root) {
        CsvMerger merger = new CsvMerger(new DefaultLabelStrategy());
        new SwingWorker<List<Path>, Void>() {
            @Override protected List<Path> doInBackground() throws Exception {
                return merger.mergeFromFirstSubfolderPatterns(root);
            }
            @Override protected void done() {
                finishRun();
                try {
                    List<Path> outs = get();
                    if (outs == null || outs.isEmpty()) {
                        JOptionPane.showMessageDialog(AnalysisPane.this,
                                "No output files were created.",
                                "No files", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    StringBuilder sb = new StringBuilder("Merged files:\n");
                    for (Path p : outs) sb.append(p).append("\n");
                    JOptionPane.showMessageDialog(AnalysisPane.this,
                            sb.toString(),
                            "Merge complete",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    showMergeError(ex);
                }
            }
        }.execute();
    }

    private void finishRun() {
        runBtn.setEnabled(true);
        setCursor(Cursor.getDefaultCursor());
    }

    private void showMergeError(Exception ex) {
        String msg;
        if (ex.getCause() instanceof MergeException || ex instanceof MergeException) {
            msg = (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
        } else {
            msg = String.valueOf(ex);
        }
        JOptionPane.showMessageDialog(this,
                "Merge error:\n" + msg,
                "Error",
                JOptionPane.ERROR_MESSAGE);
    }
}
