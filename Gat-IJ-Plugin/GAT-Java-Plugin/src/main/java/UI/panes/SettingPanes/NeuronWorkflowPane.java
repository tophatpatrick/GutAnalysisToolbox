package UI.panes.SettingPanes;

import UI.Handlers.Navigator;
import UI.panes.WorkflowDashboards.AnalyseNeuronDashboard;

import javax.swing.*;
import java.awt.*;
import ij.IJ;

/**
 * On “Run Analysis”:
 * 1) shows a modal progress bar
 * 2) lazy-creates a single popup JDialog containing one
 *    AnalyseNeuronDashboard instance
 * 3) calls addRun(...) to add a new tab
 * 4) repacks & shows the popup
 */
public class NeuronWorkflowPane extends JPanel {
    public static final String Name = "Neuron Workflow";

    private final Navigator navigator;
    private final Window    owner;
    private JDialog         popup;
    private AnalyseNeuronDashboard dash;

    public NeuronWorkflowPane(Navigator navigator, Window owner) {
        super(new BorderLayout(10,10));
        this.navigator = navigator;
        this.owner     = owner;
        setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
        IJ.run("DeepImageJ Run","");

        JButton run = new JButton("Run Analysis");
        run.addActionListener(e -> {
            // 1) show progress dialog
            JDialog dlg = new JDialog(
                    owner instanceof Dialog ? (Dialog)owner : null,
                    "Processing…",
                    Dialog.ModalityType.APPLICATION_MODAL
            );
            JProgressBar bar = new JProgressBar();
            bar.setIndeterminate(true);
            dlg.getContentPane().add(bar);
            dlg.setSize(300,80);
            dlg.setLocationRelativeTo(owner);

            // 2) background work
            new SwingWorker<Void,Void>() {
                @Override protected Void doInBackground() throws Exception {
                    Thread.sleep(2000);
                    return null;
                }
                @Override protected void done() {
                    dlg.dispose();

                    // lazy-create dashboard
                    if (dash==null) {
                        dash = new AnalyseNeuronDashboard();
                    }

                    // 3) add a run before packing
                    dash.addRun(
                            "/Users/miles/Desktop/UNI/Year5/SEM1/FIT4002/Project/Gat-IJ-Plugin/GAT-Java-Plugin/target/classes/MAX_ms_28_wk_colon_DAPI__2.tif",
                            "/Users/miles/Desktop/UNI/Year5/SEM1/FIT4002/Project/Gat-IJ-Plugin/GAT-Java-Plugin/target/classes/Neuron_ROIs_ms_28_wk_colon_DAPI__2.zip"
                    );

                    // 4) lazy-create popup
                    if (popup==null) {
                        Dialog ownerDlg =
                                owner instanceof Dialog ? (Dialog)owner : null;
                        popup = new JDialog(ownerDlg, "Analysis Results", false);
                        popup.getContentPane().add(dash);
                    }

                    // 5) re-pack & show
                    popup.pack();
                    popup.setLocationRelativeTo(owner);
                    popup.setVisible(true);
                }
            }.execute();

            dlg.setVisible(true);
        });

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnRow.add(run);
        add(btnRow, BorderLayout.SOUTH);
    }

    // … insert createBasicTab() / createAdvancedTab() if you need them …
}
