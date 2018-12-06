package emulator.z80;

import java.util.prefs.Preferences;

public class UserPreferences
{
  private static final UserPreferences instance =
    new UserPreferences();

  private final Preferences cpuPreferences;

  private static final String PREFS_PATH_CPU = "/cpu";
  private static final String PREFS_NAME_FREQUENCY = "frequency";
  private static final int PREFS_DEFAULT_FREQUENCY = 3579545; // [Hz]

  private UserPreferences()
  {
    cpuPreferences = Preferences.userRoot().node(PREFS_PATH_CPU);
  }

  public static UserPreferences getInstance()
  {
    return instance;
  }

  public void setFrequency(int frequency)
  {
    cpuPreferences.putInt(PREFS_NAME_FREQUENCY, frequency);
  }

  public int getFrequency()
  {
    return
      cpuPreferences.getInt(PREFS_NAME_FREQUENCY, PREFS_DEFAULT_FREQUENCY);
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
