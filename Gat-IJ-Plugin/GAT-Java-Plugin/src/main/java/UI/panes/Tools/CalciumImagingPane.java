package UI.panes.Tools;

import UI.Handlers.Navigator;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CalciumImagingPane extends JPanel {
    public static final String Name = "Calcium Imaging";

    public CalciumImagingPane(Navigator navigator) {
        setLayout(new BorderLayout(10,10));
        setBorder(new EmptyBorder(10,10,10,10));

        // 1) Pane title
        JLabel paneTitle = new JLabel("Calcium Imaging Analysis", SwingConstants.CENTER);
        paneTitle.setFont(paneTitle.getFont().deriveFont(Font.BOLD, 18f));
        add(paneTitle, BorderLayout.NORTH);

        // 2) Main content panel
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        // Buttons for each analysis step
        JButton loadButton = new JButton("Align Stack");
        loadButton.addActionListener(e -> alignStack());

        JButton processButton = new JButton("Align Stack Batch");
        processButton.addActionListener(e -> alignStackBatch());

        JButton plotButton = new JButton("Calcium Image Analysis");
        plotButton.addActionListener(e -> calciumImagingAnalysis());

        content.add(loadButton);
        content.add(Box.createVerticalStrut(10));
        content.add(processButton);
        content.add(Box.createVerticalStrut(10));
        content.add(plotButton);

        add(content, BorderLayout.CENTER);
    }
    private void alignStack() {
        // Figure out out to integrate new Java files
        JOptionPane.showMessageDialog(this, "Load Images functionality not implemented yet.");
    }
    private void alignStackBatch() {
        // Figure out out to integrate new Java files
        JOptionPane.showMessageDialog(this, "Process Calcium Signals functionality not implemented yet.");
    }
    private void calciumImagingAnalysis() {
        // Figure out out to integrate new Java files
        JOptionPane.showMessageDialog(this, "Generate Plots functionality not implemented yet.");
    }
}