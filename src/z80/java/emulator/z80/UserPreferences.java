package emulator.z80;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public class UserPreferences
{
  private static final UserPreferences instance =
    new UserPreferences();

  private final Preferences cpuPreferences;

  private static final String PREFS_PATH_CPU = "/cpu";
  private static final String PREFS_NAME_FREQUENCY = "frequency";

  // FIXME: Default frequency is VZ200 specific.  There is no default
  // Z80 frequency per se.  Actually, this class should completely be
  // merged into VZ200 UserPreferences class.  Also, the listeners
  // interface is flawed since it regards very view selected
  // preference values only, and in most cases, there is only a single
  // listener for each value, but mostly only exact one listener
  // implementation per value.  Therefore, the listening calls should
  // be replaced by direct calls in the code, since this is mostly a
  // 1:1 communication relation rather than a subscription model of
  // communication.
  public static final int PREFS_DEFAULT_FREQUENCY = 3579545; // [Hz]

  private static final String PREFS_NAME_STATISTICS_ENABLED =
    "statistics-enabled";
  private static final boolean PREFS_DEFAULT_STATISTICS_ENABLED = false;
  private static final String PREFS_NAME_BUSY_WAIT = "busy-wait";
  private static final boolean PREFS_DEFAULT_BUSY_WAIT = false;

  private final List<PreferencesChangeListener> listeners;

  private UserPreferences()
  {
    cpuPreferences = Preferences.userRoot().node(PREFS_PATH_CPU);
    listeners = new ArrayList<PreferencesChangeListener>();
  }

  public static UserPreferences getInstance()
  {
    return instance;
  }

  public void addListener(final PreferencesChangeListener listener)
  {
    listeners.add(listener);
    listener.speedChanged(getFrequency());
    listener.statisticsEnabledChanged(getStatisticsEnabled());
    listener.busyWaitChanged(getBusyWait());
  }

  public void setFrequency(final int frequency)
  {
    cpuPreferences.putInt(PREFS_NAME_FREQUENCY, frequency);
    for (final PreferencesChangeListener listener : listeners) {
      listener.speedChanged(frequency);
    }
  }

  public int getFrequency()
  {
    int frequency =
      cpuPreferences.getInt(PREFS_NAME_FREQUENCY, PREFS_DEFAULT_FREQUENCY);
    if ((frequency < 1) || (frequency > 1000000000)) {
      System.out.println("error: CPU frequency [Hz]: " + frequency +
                         ", resetting to default (" +
                         PREFS_DEFAULT_FREQUENCY + ")");
      frequency = PREFS_DEFAULT_FREQUENCY;
      setFrequency(frequency);
    }
    return frequency;
  }

  public void setStatisticsEnabled(final boolean statisticsEnabled)
  {
    cpuPreferences.putBoolean(PREFS_NAME_STATISTICS_ENABLED, statisticsEnabled);
    for (final PreferencesChangeListener listener : listeners) {
      listener.statisticsEnabledChanged(statisticsEnabled);
    }
  }

  public boolean getStatisticsEnabled()
  {
    return
      cpuPreferences.getBoolean(PREFS_NAME_STATISTICS_ENABLED,
                                PREFS_DEFAULT_STATISTICS_ENABLED);
  }

  public void setBusyWait(final boolean busyWait)
  {
    cpuPreferences.putBoolean(PREFS_NAME_BUSY_WAIT, busyWait);
    for (final PreferencesChangeListener listener : listeners) {
      listener.busyWaitChanged(busyWait);
    }
  }

  public boolean getBusyWait()
  {
    return
      cpuPreferences.getBoolean(PREFS_NAME_BUSY_WAIT, PREFS_DEFAULT_BUSY_WAIT);
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
