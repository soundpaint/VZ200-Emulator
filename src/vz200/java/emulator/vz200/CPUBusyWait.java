package emulator.vz200;

import java.awt.event.KeyEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;

import emulator.z80.CPU;
import emulator.z80.PreferencesChangeListener;
import emulator.z80.UserPreferences;

public class CPUBusyWait extends Box implements PreferencesChangeListener
{
  private static final long serialVersionUID = 7823489712923369946L;

  private final JCheckBox cbBusyWait;
  private boolean busyWait;

  public CPUBusyWait()
  {
    super(BoxLayout.Y_AXIS);
    setBorder(BorderFactory.createTitledBorder("CPU Busy Wait"));

    final Box bxBusyWait = new Box(BoxLayout.X_AXIS);
    add(bxBusyWait);
    cbBusyWait = new JCheckBox("Busy Wait");
    bxBusyWait.add(cbBusyWait);
    cbBusyWait.setMnemonic(KeyEvent.VK_W);
    cbBusyWait.addChangeListener((final ChangeEvent event) -> {
        busyWaitChanged();
      });
    bxBusyWait.add(Box.createHorizontalGlue());
    add(Box.createVerticalGlue());
    final UserPreferences userPreferences = UserPreferences.getInstance();
    userPreferences.addListener(this);
  }

  private void busyWaitChanged()
  {
    final boolean busyWait = cbBusyWait.isSelected();
    final UserPreferences userPreferences = UserPreferences.getInstance();
    userPreferences.setBusyWait(busyWait);
  }

  @Override
  public void speedChanged(final int frequency)
  {
    // This callback is handled elsewhere.
    // Hence, do nothing here.
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
    this.busyWait = busyWait;
    cbBusyWait.setSelected(busyWait);
  }
}

/*
 * Local Variables:
 *   coding:utf-8
 *   mode:Java
 * End:
 */
