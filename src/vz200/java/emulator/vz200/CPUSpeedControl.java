package emulator.vz200;

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;

import emulator.z80.PreferencesChangeListener;

public class CPUSpeedControl extends Box
{
  private static final long serialVersionUID = -865874602941269179L;
  public static final int DEFAULT_SPEED = 3579545; // [Hz]

  private final List<PreferencesChangeListener> listeners;
  private final JLabel lbSpeed;
  private final JButton btChange;
  private final CPUSpeedSelectionDialog dlCPUSpeedSelection;
  private int frequency;

  public CPUSpeedControl(final int initialFrequency, final Frame owner)
  {
    super(BoxLayout.X_AXIS);
    this.frequency = initialFrequency;
    listeners = new ArrayList<PreferencesChangeListener>();
    setBorder(BorderFactory.createTitledBorder("CPU Speed"));

    final Box bxLabel = new Box(BoxLayout.Y_AXIS);
    add(bxLabel);
    bxLabel.add(new JLabel("Target speed [Hz]:"));
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
    dlCPUSpeedSelection = new CPUSpeedSelectionDialog(frequency, owner);
    speedChanged();
  }

  public void addListener(final PreferencesChangeListener listener)
  {
    listeners.add(listener);
    listener.speedChanged(frequency);
  }

  public void removeListener(final PreferencesChangeListener listener)
  {
    listeners.remove(listener);
  }

  private void speedChanged()
  {
    lbSpeed.setText("" + frequency);
    for (final PreferencesChangeListener listener : listeners) {
      listener.speedChanged(frequency);
    }
  }

  private void changeSpeed()
  {
    if (dlCPUSpeedSelection.execute()) {
      frequency = dlCPUSpeedSelection.getSelectedFrequency();
      speedChanged();
    } else {
      // dialog aborted => nothing to do
    }
  }
}

/*
 * Local Variables:
 *   coding:utf-8
 *   mode:Java
 * End:
 */
