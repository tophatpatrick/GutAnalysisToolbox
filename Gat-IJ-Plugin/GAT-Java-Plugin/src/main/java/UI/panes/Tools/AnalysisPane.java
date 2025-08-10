package UI.panes.Tools;

import UI.Handlers.Navigator;
import services.merge.MergeSwingTest; // <-- make sure this package matches your project

import javax.swing.*;
import java.awt.*;

public class AnalysisPane extends JPanel {
    public static final String Name = "Analysis";

    public AnalysisPane(Navigator navigator){
        setLayout(new BorderLayout(12, 12));

        JLabel title = new JLabel("Merge Analysis Tool", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        add(title, BorderLayout.NORTH);

        JButton runButton = new JButton("Run Merge Analysis");
        runButton.setFont(runButton.getFont().deriveFont(Font.BOLD, 16f));
        runButton.setPreferredSize(new Dimension(260, 56));

        JPanel center = new JPanel(new GridBagLayout());
        center.add(runButton);
        add(center, BorderLayout.CENTER);

        runButton.addActionListener(e -> {
            // run on the EDT because it opens Swing dialogs
            SwingUtilities.invokeLater(() -> {
                try {
                    MergeSwingTest.main(new String[0]);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(
                            AnalysisPane.this,
                            "Error running merge tool:\n" + ex.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            });
        });
    }
}
