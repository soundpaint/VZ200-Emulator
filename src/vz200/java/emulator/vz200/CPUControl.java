package emulator.vz200;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;

import emulator.z80.CPU;
import emulator.z80.PreferencesChangeListener;
import emulator.z80.WallClockProvider;

public class CPUControl extends JPanel
{
  private static final long serialVersionUID = 5541292559819662284L;

  private static class CPUControlListener
    implements PreferencesChangeListener
  {
    public void speedChanged(final int frequency)
    {
      final UserPreferences userPreferences = UserPreferences.getInstance();
      userPreferences.setCPUSpeed(frequency);
    }
    public void statisticsEnabled(final boolean enabled)
    {
      final UserPreferences userPreferences = UserPreferences.getInstance();
      userPreferences.setCPUStatisticsEnabled(enabled);
    }
  }

  public CPUControl(final CPU cpu, final JFrame owner)
  {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    final CPUControlListener cpuControlListener = new CPUControlListener();
    final UserPreferences userPreferences = UserPreferences.getInstance();
    final int frequency = userPreferences.getCPUSpeed();
    final CPUSpeedControl cpuSpeedControl =
      new CPUSpeedControl(frequency, owner);
    add(cpuSpeedControl);
    cpuSpeedControl.addListener(cpu);
    cpuSpeedControl.addListener(cpuControlListener);
    final boolean cpuStatisticsEnabled =
      userPreferences.getCPUStatisticsEnabled();
    final CPUStatistics cpuStatistics =
      new CPUStatistics(cpu, cpuStatisticsEnabled);
    add(cpuStatistics);
    cpuStatistics.addListener(cpu);
    cpuStatistics.addListener(cpuControlListener);
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
