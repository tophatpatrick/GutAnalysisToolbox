package UI.panes.SettingPanes;

import Features.Core.Params;
import Analysis.TemporalColorCoder;
import ij.ImagePlus;
import ij.IJ;
import UI.Handlers.Navigator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class TemporalColorPane extends JPanel {

    public static final String Name = "Temporal Color Coder";

    private final Window owner;

    private JTextField tfStartFrame;
    
    private JTextField tfEndFrame;
    private JComboBox<String> cbLUT;
    private JComboBox<String> cbProjection;
    private JCheckBox cbColorScale;
    private JCheckBox cbBatchMode;
    private JButton runBtn;

    private Params params;

    public TemporalColorPane(Navigator navigator, Window owner) {
    super(new BorderLayout(10,10));
    this.owner = owner;
    initUI();
}

    private void initUI() {
        setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.gridx = 0; c.gridy = 0; c.anchor = GridBagConstraints.LINE_END;

        add(new JLabel("Start Frame:"), c);
        c.gridx = 1; c.anchor = GridBagConstraints.LINE_START;
        tfStartFrame = new JTextField("1", 5);
        add(tfStartFrame, c);

        c.gridx = 0; c.gridy++;
        c.anchor = GridBagConstraints.LINE_END;
        add(new JLabel("End Frame:"), c);
        c.gridx = 1; c.anchor = GridBagConstraints.LINE_START;
        tfEndFrame = new JTextField("10", 5);
        add(tfEndFrame, c);

        c.gridx = 0; c.gridy++;
        c.anchor = GridBagConstraints.LINE_END;
        add(new JLabel("LUT:"), c);
        c.gridx = 1; c.anchor = GridBagConstraints.LINE_START;
        cbLUT = new JComboBox<>(new String[]{"Fire", "Ice", "Green", "Red"});
        add(cbLUT, c);

        c.gridx = 0; c.gridy++;
        c.anchor = GridBagConstraints.LINE_END;
        add(new JLabel("Projection:"), c);
        c.gridx = 1; c.anchor = GridBagConstraints.LINE_START;
        cbProjection = new JComboBox<>(new String[]{"Max Intensity", "Average Intensity", "Min Intensity"});
        add(cbProjection, c);

        c.gridx = 0; c.gridy++;
        c.anchor = GridBagConstraints.LINE_END;
        add(new JLabel("Color Scale:"), c);
        c.gridx = 1; c.anchor = GridBagConstraints.LINE_START;
        cbColorScale = new JCheckBox();
        cbColorScale.setSelected(true);
        add(cbColorScale, c);

        c.gridx = 0; c.gridy++;
        c.anchor = GridBagConstraints.LINE_END;
        add(new JLabel("Batch Mode:"), c);
        c.gridx = 1; c.anchor = GridBagConstraints.LINE_START;
        cbBatchMode = new JCheckBox();
        add(cbBatchMode, c);

        c.gridx = 0; c.gridy++;
        c.gridwidth = 2; c.anchor = GridBagConstraints.CENTER;
        runBtn = new JButton("Run Temporal Color Coding");
        add(runBtn, c);

        runBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runWorkflow();
            }
        });
    }

    private void runWorkflow() {
        try {
            if (params == null)
                params = new Params();

            params.referenceFrame = Integer.parseInt(tfStartFrame.getText());
            params.referenceFrameEnd = Integer.parseInt(tfEndFrame.getText());
            params.lutName = (String) cbLUT.getSelectedItem();
            params.projectionMethod = (String) cbProjection.getSelectedItem();
            params.createColorScale = cbColorScale.isSelected();
            params.batchMode = cbBatchMode.isSelected();

            ImagePlus imp = IJ.getImage();
            if (imp != null) {
                TemporalColorCoder.run(imp, params);
            } else {
                JOptionPane.showMessageDialog(owner, "Please select an active image in ImageJ.", "No Image", JOptionPane.WARNING_MESSAGE);
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(owner, "Start/End frames must be integers.", "Input Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(owner, "Error running workflow: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

}
