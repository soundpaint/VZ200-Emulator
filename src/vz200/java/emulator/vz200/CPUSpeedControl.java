package emulator.vz200;

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;

import emulator.z80.PreferencesChangeListener;
import emulator.z80.UserPreferences;

public class CPUSpeedControl extends Box implements PreferencesChangeListener
{
  private static final long serialVersionUID = -865874602941269179L;

  private final JLabel lbSpeed;
  private final JButton btChange;
  private final CPUSpeedSelectionDialog dlCPUSpeedSelection;
  private int frequency;

  public CPUSpeedControl(final Frame owner)
  {
    super(BoxLayout.X_AXIS);
    setBorder(BorderFactory.createTitledBorder("CPU Speed"));

    final Box bxLabel = new Box(BoxLayout.Y_AXIS);
    add(bxLabel);
    bxLabel.add(new JLabel("Target Clock Frequency [Hz]:"));
    bxLabel.add(Box.createHorizontalStrut(5));
    add(Box.createHorizontalStrut(5));

    final Box bxValue = new Box(BoxLayout.Y_AXIS);
    add(bxValue);
    lbSpeed = new JLabel();
    bxValue.add(lbSpeed);
    bxValue.add(Box.createHorizontalStrut(5));
    add(Box.createHorizontalStrut(5));
    add(Box.createHorizontalGlue());

    final Box bxChangeButton = new Box(BoxLayout.Y_AXIS);
    add(bxChangeButton);
    btChange = new JButton("Change");
    btChange.setMnemonic(KeyEvent.VK_C);
    btChange.addActionListener((final ActionEvent event) ->
                               { changeSpeed(); });
    bxChangeButton.add(btChange);
    bxChangeButton.add(Box.createHorizontalStrut(5));
    add(Box.createHorizontalStrut(5));
    dlCPUSpeedSelection = new CPUSpeedSelectionDialog(owner);
    final UserPreferences userPreferences = UserPreferences.getInstance();
    userPreferences.addListener(this);
  }

  private void changeSpeed()
  {
    if (dlCPUSpeedSelection.execute(frequency)) {
      final int frequency = dlCPUSpeedSelection.getSelectedFrequency();
      final UserPreferences userPreferences = UserPreferences.getInstance();
      userPreferences.setFrequency(frequency);
    } else {
      // dialog aborted => nothing to do
    }
  }

  @Override
  public void speedChanged(final int frequency)
  {
    this.frequency = frequency;
    lbSpeed.setText("" + frequency);
  }

  @Override
  public void statisticsEnabledChanged(final boolean statisticsEnabled)
  {
    // This callback is handled elsewhere.
    // Hence, do nothing here.
  }

  @Override
  public void busyWaitChanged(final boolean busyWait)
  {
    // This callback is handled elsewhere.
    // Hence, do nothing here.
  }
}

/*
 * Local Variables:
 *   coding:utf-8
 *   mode:Java
 * End:
 */
