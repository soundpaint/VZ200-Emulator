package emulator.vz200;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JTextPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.KeyStroke;

import emulator.z80.PreferencesChangeListener;

public class CPUSpeedSelectionDialog extends JDialog
{
  private static final long serialVersionUID = -5452242694848944892L;

  private final JSpinner snSpeed;
  private final SpinnerNumberModel speedModel;
  private final JOptionPane opSelection;
  private final JButton btPaneOptionSelect;
  private final JButton btPaneOptionCancel;
  private int frequency;

  public CPUSpeedSelectionDialog(final Frame owner)
  {
    super(owner, "Select CPU Speed ", true);
    final JButton[] paneOptions = {
      btPaneOptionSelect =
      createPaneOption("Select", KeyEvent.VK_S,
                       "Apply selected speed and close dialog."),
      btPaneOptionCancel =
      createPaneOption("Cancel", KeyEvent.VK_C,
                       "Close dialog without applying selected speed.")
    };
    speedModel = new SpinnerNumberModel(1, 1, 1000000000, 1);
    snSpeed = new JSpinner(speedModel);
    final JComponent bxSpeedSelector = new JLabel();
    opSelection = createContentPane(paneOptions, bxSpeedSelector);
    setContentPane(opSelection);
    pack();
  }

  private JButton createPaneOption(final String label,
                                   final int mnemonic,
                                   final String toolTipText)
  {
    final JButton button = new JButton(label);
    button.setToolTipText(toolTipText);
    if (mnemonic != 0) {
      button.setMnemonic(mnemonic);
    }
    button.addActionListener((final ActionEvent event) ->
                             {
                               opSelection.setValue(button);
                               setVisible(false);
                             });
    return button;
  }

  private JScrollPane createLineDescription()
  {
    final JTextPane tpLineDescription = new JTextPane();
    tpLineDescription.setEditable(false);
    final JScrollPane spLineDescription = new JScrollPane(tpLineDescription);
    spLineDescription.setBorder(BorderFactory.
                                createTitledBorder("Line to be Selected"));
    spLineDescription.setVerticalScrollBarPolicy(JScrollPane.
                                                 VERTICAL_SCROLLBAR_ALWAYS);
    spLineDescription.setMinimumSize(new Dimension(200, 80));
    spLineDescription.setPreferredSize(new Dimension(400, 80));
    return spLineDescription;
  }

  private Box createSpeedSelector()
  {
    final Box bxSpeedSelector = new Box(BoxLayout.Y_AXIS);
    final Box bxSelectSpeedLabel = new Box(BoxLayout.X_AXIS);
    final JLabel lbSelectSpeed = new JLabel("Select Speed [Hz]");
    lbSelectSpeed.setDisplayedMnemonic(KeyEvent.VK_P);
    lbSelectSpeed.setLabelFor(snSpeed);
    bxSelectSpeedLabel.add(lbSelectSpeed);
    bxSelectSpeedLabel.add(Box.createHorizontalGlue());
    bxSpeedSelector.add(bxSelectSpeedLabel);
    bxSpeedSelector.add(snSpeed);
    return bxSpeedSelector;
  }

  private Box createSelection()
  {
    final Box bxSelection = new Box(BoxLayout.X_AXIS);
    bxSelection.setBorder(BorderFactory.
                          createTitledBorder("Select CPU Speed"));
    bxSelection.add(Box.createHorizontalStrut(5));
    bxSelection.add(createSpeedSelector());
    bxSelection.add(Box.createHorizontalStrut(5));
    final Dimension selectionMaximumSize =
      new Dimension((int)bxSelection.getMaximumSize().getWidth(),
                    (int)bxSelection.getPreferredSize().getHeight());
    bxSelection.setMaximumSize(selectionMaximumSize);
    return bxSelection;
  }

  private JOptionPane createContentPane(final JButton[] paneOptions,
                                        final JComponent paneContent)
  {
    final Box bxContent = new Box(BoxLayout.Y_AXIS);
    bxContent.add(createSelection());

    bxContent.add(Box.createVerticalStrut(5));
    bxContent.add(paneContent);
    bxContent.add(Box.createVerticalGlue());
    final JOptionPane opSelection =
      new JOptionPane(bxContent,
                      JOptionPane.PLAIN_MESSAGE,
                      JOptionPane.YES_NO_OPTION,
                      null,
                      paneOptions, paneOptions[0]);
    opSelection.registerKeyboardAction((final ActionEvent event) -> {
        CPUSpeedSelectionDialog.this.setVisible(false);
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
      JComponent.WHEN_IN_FOCUSED_WINDOW);
    return opSelection;
  }

  private void printMessage(final String message)
  {
    System.out.printf("%s%n", message);
  }

  public int getSelectedFrequency()
  {
    return frequency;
  }

  /**
   * @return True, if and only if the user closed the dialog by
   * clicking the select button.  In this case, the caller should
   * subsequently call methods <code>#getSelectedMixer()</code> and
   * <code>#getSelectedLine()</code> and apply the selected mixer and
   * line.
   */
  public boolean execute(final int preselectedFrequency)
  {
    opSelection.setValue(null);
    speedModel.setValue(preselectedFrequency);
    setVisible(true);
    final Object value = opSelection.getValue();
    final boolean result;
    if ((value == null) || (value == JOptionPane.UNINITIALIZED_VALUE)) {
      // window closed w/o option button pressed
      result = false;
    } else if (value == btPaneOptionSelect) {
      result = true;
      frequency = speedModel.getNumber().intValue();
    } else if (value == btPaneOptionCancel) {
      result = false;
    } else {
      throw new InternalError("unexpected option pane selection: " + value);
    }
    return result;
  }
}

/*
 * Local Variables:
 *   coding:utf-8
 *   mode:Java
 * End:
 */
