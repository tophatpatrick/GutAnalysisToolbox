package Ui.panes.SettingPanes;

import Ui.Handlers.Navigator;

import javax.swing.*;
import java.awt.*;
import Ui.panes.WorkflowDashboards.*;

public class NeuronWorkflowPane extends JPanel{

    public static final String Name = "Neuron Workflow";

    public NeuronWorkflowPane(Navigator navigator, Window owner){
        super(new BorderLayout(10,10));
        setBorder(BorderFactory.createEmptyBorder(20,20,20,20));


        JLabel lbl = new JLabel(
                "<html><h2>Neuron Analysis</h2>"
                        + "<p>Configure parameters here…</p></html>",
                SwingConstants.CENTER
        );
        add(lbl, BorderLayout.CENTER);

        JButton run = new JButton("Run Analysis");

        run.addActionListener(e -> {
            // 1) build modal progress dialog
            JDialog progress = new JDialog(owner, "Processing…", Dialog.ModalityType.APPLICATION_MODAL);
            JProgressBar bar = new JProgressBar();
            bar.setIndeterminate(true);
            progress.add(bar, BorderLayout.CENTER);
            progress.setSize(300,80);
            progress.setLocationRelativeTo(owner);

            // 2) SwingWorker to simulate the analysis
            SwingWorker<Void,Void> worker = new SwingWorker<Void,Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    Thread.sleep(2000);  // simulate 2 s work
                    return null;
                }
                @Override
                protected void done() {
                    progress.dispose();
                    // switch to your dashboard pane
                    navigator.show(AnalyseNeuronDashboard.Name);
                }
            };
            worker.execute();
            progress.setVisible(true);
        });

        JPanel btnPanel = new JPanel();
        btnPanel.add(run);
        add(btnPanel, BorderLayout.SOUTH);


    }
}
