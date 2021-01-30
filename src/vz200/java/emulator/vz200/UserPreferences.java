package emulator.vz200;

import java.util.prefs.Preferences;

public class UserPreferences
{
  private static final UserPreferences instance =
    new UserPreferences();

  private final Preferences vz200Preferences;

  private static final String PREFS_PATH_VZ200 = "/vz200";

  private static final String PREFS_NAME_CPU_STATISTICS_ENABLED =
    "cpu/statistics-enabled";
  private static final boolean PREFS_DEFAULT_CPU_STATISTICS_ENABLED = false;

  private static final String PREFS_NAME_VIDEO_ZOOM_FACTOR =
    "video/zoom-factor";
  private static final int PREFS_DEFAULT_VIDEO_ZOOM_FACTOR = 1;

  private static final String PREFS_NAME_KBD_ICONIFIED =
    "keyboard/iconified";
  private static final boolean PREFS_DEFAULT_KBD_ICONIFIED = false;

  private static final String PREFS_NAME_CASSETTE_OUT_MIXER =
    "cassette-out/mixer";
  private static final String PREFS_DEFAULT_CASSETTE_OUT_MIXER = null;

  private static final String PREFS_NAME_CASSETTE_OUT_LINE =
    "cassette-out/line";
  private static final String PREFS_DEFAULT_CASSETTE_OUT_LINE = null;

  private static final String PREFS_NAME_CASSETTE_OUT_VOLUME =
    "cassette-out/volume";
  private static final double PREFS_DEFAULT_CASSETTE_OUT_VOLUME = 1.0;

  private static final String PREFS_NAME_CASSETTE_OUT_MUTED =
    "cassette-out/muted";
  private static final boolean PREFS_DEFAULT_CASSETTE_OUT_MUTED = false;

  private static final String PREFS_NAME_SPEAKER_MIXER =
    "speaker/mixer";
  private static final String PREFS_DEFAULT_SPEAKER_MIXER = null;

  private static final String PREFS_NAME_SPEAKER_LINE =
    "speaker/line";
  private static final String PREFS_DEFAULT_SPEAKER_LINE = null;

  private static final String PREFS_NAME_SPEAKER_VOLUME =
    "speaker/volume";
  private static final double PREFS_DEFAULT_SPEAKER_VOLUME = 1.0;

  private static final String PREFS_NAME_SPEAKER_MUTED =
    "speaker/muted";
  private static final boolean PREFS_DEFAULT_SPEAKER_MUTED = false;

  private static final String PREFS_NAME_CASSETTE_IN_INVERT_PHASE =
    "cassette-in/invert-phase";
  private static final boolean PREFS_DEFAULT_CASSETTE_IN_INVERT_PHASE = false;

  private static final String PREFS_NAME_CASSETTE_IN_VOLUME =
    "cassette-in/volume";
  private static final double PREFS_DEFAULT_CASSETTE_IN_VOLUME = 1.0;

  private static final String PREFS_NAME_CASSETTE_IN_SPEED =
    "cassette-in/speed";
  private static final double PREFS_DEFAULT_CASSETTE_IN_SPEED = 1.0;

  private static final String PREFS_NAME_CASSETTE_IN_LINK_SPEED_WITH_CPU_FREQ =
    "cassette-in/link-speed-with-cpu-freq";
  private static final boolean
    PREFS_DEFAULT_CASSETTE_IN_LINK_SPEED_WITH_CPU_FREQ = true;

  private static final String PREFS_NAME_CASSETTE_IN_DC_OFFSET =
    "cassette-in/dc-offset";
  private static final double PREFS_DEFAULT_CASSETTE_IN_DC_OFFSET = 1.0;

  private static final String PREFS_NAME_CASSETTE_IN_VZ_TRIM_LEAD_IN =
    "cassette-in/vz-trim-lead-in";
  private static final boolean PREFS_DEFAULT_CASSETTE_IN_VZ_TRIM_LEAD_IN =
    false;

  private UserPreferences()
  {
    vz200Preferences = Preferences.userRoot().node(PREFS_PATH_VZ200);
  }

  public static UserPreferences getInstance()
  {
    return instance;
  }

  public void setCPUStatisticsEnabled(final boolean enabled)
  {
    vz200Preferences.putBoolean(PREFS_NAME_CPU_STATISTICS_ENABLED, enabled);
  }

  public boolean getCPUStatisticsEnabled()
  {
    final boolean enabled =
      vz200Preferences.getBoolean(PREFS_NAME_CPU_STATISTICS_ENABLED,
                                  PREFS_DEFAULT_CPU_STATISTICS_ENABLED);
    return enabled;
  }

  public void setVideoZoomFactor(final int zoomFactor)
  {
    vz200Preferences.putInt(PREFS_NAME_VIDEO_ZOOM_FACTOR, zoomFactor);
  }

  public int getVideoZoomFactor()
  {
    int zoomFactor =
      vz200Preferences.getInt(PREFS_NAME_VIDEO_ZOOM_FACTOR,
                              PREFS_DEFAULT_VIDEO_ZOOM_FACTOR);
    if ((zoomFactor < 1) || (zoomFactor > 3)) {
      System.out.println("error: unexpected zoom factor: " + zoomFactor +
                         ", resetting to default (" +
                         PREFS_DEFAULT_VIDEO_ZOOM_FACTOR + ")");
      zoomFactor = PREFS_DEFAULT_VIDEO_ZOOM_FACTOR;
      setVideoZoomFactor(zoomFactor);
    }
    return zoomFactor;
  }

  public void setKeyboardIconified(final boolean iconified)
  {
    vz200Preferences.putBoolean(PREFS_NAME_KBD_ICONIFIED, iconified);
  }

  public boolean getKeyboardIconified()
  {
    return vz200Preferences.getBoolean(PREFS_NAME_KBD_ICONIFIED,
                                       PREFS_DEFAULT_KBD_ICONIFIED);
  }

  public void setCassetteOutVolume(final double volume)
  {
    vz200Preferences.putDouble(PREFS_NAME_CASSETTE_OUT_VOLUME, volume);
  }

  public double getCassetteOutVolume()
  {
    double volume =
      vz200Preferences.getDouble(PREFS_NAME_CASSETTE_OUT_VOLUME,
                                 PREFS_DEFAULT_CASSETTE_OUT_VOLUME);
    if ((volume < 0.0) ||
        (volume > 1.0)) {
      System.out.println("error: unexpected cassette out volume: " + volume +
                         ", resetting to " + VolumeControl.VOLUME_DEFAULT);
      volume = VolumeControl.VOLUME_DEFAULT;
      setCassetteOutVolume(volume);
    }
    return volume;
  }

  public void setCassetteOutMuted(final boolean muted)
  {
    vz200Preferences.putBoolean(PREFS_NAME_CASSETTE_OUT_MUTED, muted);
  }

  public boolean getCassetteOutMuted()
  {
    final boolean muted =
      vz200Preferences.getBoolean(PREFS_NAME_CASSETTE_OUT_MUTED,
                                  PREFS_DEFAULT_CASSETTE_OUT_MUTED);
    return muted;
  }

  public void setCassetteOutMixer(final String id)
  {
    vz200Preferences.put(PREFS_NAME_CASSETTE_OUT_MIXER, id);
  }

  public String getCassetteOutMixer()
  {
    return
      vz200Preferences.get(PREFS_NAME_CASSETTE_OUT_MIXER,
                           PREFS_DEFAULT_CASSETTE_OUT_MIXER);
  }

  public void setCassetteOutLine(final String id)
  {
    vz200Preferences.put(PREFS_NAME_CASSETTE_OUT_LINE, id);
  }

  public String getCassetteOutLine()
  {
    return
      vz200Preferences.get(PREFS_NAME_CASSETTE_OUT_LINE,
                           PREFS_DEFAULT_CASSETTE_OUT_LINE);
  }

  public void setSpeakerVolume(final double volume)
  {
    vz200Preferences.putDouble(PREFS_NAME_SPEAKER_VOLUME, volume);
  }

  public double getSpeakerVolume()
  {
    double volume =
      vz200Preferences.getDouble(PREFS_NAME_SPEAKER_VOLUME,
                                 PREFS_DEFAULT_SPEAKER_VOLUME);
    if ((volume < 0.0) ||
        (volume > 1.0)) {
      System.out.println("error: unexpected speaker volume: " + volume +
                         ", resetting to " + VolumeControl.VOLUME_DEFAULT);
      volume = VolumeControl.VOLUME_DEFAULT;
      setSpeakerVolume(volume);
    }
    return volume;
  }

  public void setSpeakerMuted(final boolean muted)
  {
    vz200Preferences.putBoolean(PREFS_NAME_SPEAKER_MUTED, muted);
  }

  public boolean getSpeakerMuted()
  {
    final boolean speakerMuted =
      vz200Preferences.getBoolean(PREFS_NAME_SPEAKER_MUTED,
                                  PREFS_DEFAULT_SPEAKER_MUTED);
    return speakerMuted;
  }

  public void setSpeakerMixer(final String id)
  {
    vz200Preferences.put(PREFS_NAME_SPEAKER_MIXER, id);
  }

  public String getSpeakerMixer()
  {
    return
      vz200Preferences.get(PREFS_NAME_SPEAKER_MIXER,
                           PREFS_DEFAULT_SPEAKER_MIXER);
  }

  public void setSpeakerLine(final String id)
  {
    vz200Preferences.put(PREFS_NAME_SPEAKER_LINE, id);
  }

  public String getSpeakerLine()
  {
    return
      vz200Preferences.get(PREFS_NAME_SPEAKER_LINE,
                           PREFS_DEFAULT_SPEAKER_LINE);
  }

  public void setCassetteInInvertPhase(final boolean invertPhase)
  {
    vz200Preferences.putBoolean(PREFS_NAME_CASSETTE_IN_INVERT_PHASE,
                                invertPhase);
  }

  public boolean getCassetteInInvertPhase()
  {
    final boolean invertPhase =
      vz200Preferences.getBoolean(PREFS_NAME_CASSETTE_IN_INVERT_PHASE,
                                  PREFS_DEFAULT_CASSETTE_IN_INVERT_PHASE);
    return invertPhase;
  }

  public void setCassetteInVolume(final double volume)
  {
    vz200Preferences.putDouble(PREFS_NAME_CASSETTE_IN_VOLUME, volume);
  }

  public double getCassetteInVolume()
  {
    double volume =
      vz200Preferences.getDouble(PREFS_NAME_CASSETTE_IN_VOLUME,
                                 PREFS_DEFAULT_CASSETTE_IN_VOLUME);
    if ((volume < 0.0) ||
        (volume > 1.0)) {
      System.out.println("error: unexpected cassette in volume: " + volume +
                         ", resetting to " + VolumeControl.VOLUME_DEFAULT);
      volume = VolumeControl.VOLUME_DEFAULT;
      setCassetteInVolume(volume);
    }
    return volume;
  }

  public void setCassetteInSpeed(final double speed)
  {
    vz200Preferences.putDouble(PREFS_NAME_CASSETTE_IN_SPEED, speed);
  }

  public double getCassetteInSpeed()
  {
    double speed =
      vz200Preferences.getDouble(PREFS_NAME_CASSETTE_IN_SPEED,
                                 PREFS_DEFAULT_CASSETTE_IN_SPEED);
    if ((speed < 0.9) ||
        (speed > 1.1)) {
      System.out.println("error: unexpected cassette in speed: " + speed +
                         ", resetting to 1.0");
      speed = 1.0;
      setCassetteInSpeed(speed);
    }
    return speed;
  }

  public void setCassetteInLinkSpeedWithCpuFreq(final boolean linkSpeedWithCpuFreq)
  {
    vz200Preferences.putBoolean(PREFS_NAME_CASSETTE_IN_LINK_SPEED_WITH_CPU_FREQ,
                                linkSpeedWithCpuFreq);
  }

  public boolean getCassetteInLinkSpeedWithCpuFreq()
  {
    final boolean linkSpeedWithCpuFreq =
      vz200Preferences.getBoolean(PREFS_NAME_CASSETTE_IN_LINK_SPEED_WITH_CPU_FREQ,
                                  PREFS_DEFAULT_CASSETTE_IN_LINK_SPEED_WITH_CPU_FREQ);
    return linkSpeedWithCpuFreq;
  }

  public void setCassetteInDcOffset(final double dcOffset)
  {
    vz200Preferences.putDouble(PREFS_NAME_CASSETTE_IN_DC_OFFSET, dcOffset);
  }

  public double getCassetteInDcOffset()
  {
    double dcOffset =
      vz200Preferences.getDouble(PREFS_NAME_CASSETTE_IN_DC_OFFSET,
                                 PREFS_DEFAULT_CASSETTE_IN_DC_OFFSET);
    if ((dcOffset < 0.7) ||
        (dcOffset > 1.3)) {
      System.out.println("error: unexpected cassette in DC offset: " +
                         dcOffset +
                         ", resetting to 1.0");
      dcOffset = 1.0;
      setCassetteInDcOffset(dcOffset);
    }
    return dcOffset;
  }

  public void setCassetteInVzTrimLeadIn(final boolean trimLeadIn)
  {
    vz200Preferences.putBoolean(PREFS_NAME_CASSETTE_IN_VZ_TRIM_LEAD_IN,
                                trimLeadIn);
  }

  public boolean getCassetteInVzTrimLeadIn()
  {
    final boolean trimLeadIn =
      vz200Preferences.getBoolean(PREFS_NAME_CASSETTE_IN_VZ_TRIM_LEAD_IN,
                                  PREFS_DEFAULT_CASSETTE_IN_VZ_TRIM_LEAD_IN);
    return trimLeadIn;
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
