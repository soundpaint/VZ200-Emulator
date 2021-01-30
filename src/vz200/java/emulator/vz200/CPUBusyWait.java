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

  private static final String TOOL_TIP_BUSY_WAIT =
    "<html>\n" +
    "  Synchronization of the simulation with the emulator host's<br />\n" +
    "  clock is by default done by emulating CPU instructions<br />\n" +
    "  until the emulator CPU's wall clock is up with or slightly<br />\n" +
    "  ahead of the host's clock, and then doing a sleep of a<br />\n" +
    "  few milliseconds, and then again resuming emulation.<br />\n" +
    "  This approach leads to jitter at least in the magnitude of<br />\n" +
    "  the sleeping time.<br />\n" +
    "  To reduce jitter, instead of doing a sleep, the emulator<br />\n" +
    "  can alternatively perform busy wait, until time is ready<br />\n" +
    "  for performing the next CPU instruction.  This alternative<br />\n" +
    "  approach reduces jitter, but at the same time increases<br />\n" +
    "  the CPU load to at least 1.0, since there is no more<br />\n" +
    "  sleep, but the CPU emulation process running all the<br />\n" +
    "  time.\n" +
    "</html>\n";

  private final JCheckBox cbBusyWait;
  private boolean busyWait;

  public CPUBusyWait()
  {
    super(BoxLayout.Y_AXIS);
    setBorder(BorderFactory.createTitledBorder("CPU Busy Wait"));

    final Box bxBusyWait = new Box(BoxLayout.X_AXIS);
    add(bxBusyWait);
    cbBusyWait = new JCheckBox("Busy Wait");
    cbBusyWait.setToolTipText(TOOL_TIP_BUSY_WAIT);
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
