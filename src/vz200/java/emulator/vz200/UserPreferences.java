package emulator.vz200;

import java.util.prefs.Preferences;

public class UserPreferences
{
  private static final UserPreferences instance =
    new UserPreferences();

  private final Preferences vz200Preferences;

  private static final String PREFS_PATH_VZ200 = "/vz200";
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
  private static final double PREFS_DEFAULT_CASSETTE_OUT_VOLUME = 0.8;
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
  private static final double PREFS_DEFAULT_SPEAKER_VOLUME = 0.8;
  private static final String PREFS_NAME_SPEAKER_MUTED =
    "speaker/muted";
  private static final boolean PREFS_DEFAULT_SPEAKER_MUTED = false;

  private UserPreferences()
  {
    vz200Preferences = Preferences.userRoot().node(PREFS_PATH_VZ200);
  }

  public static UserPreferences getInstance()
  {
    return instance;
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
                         ", resetting to 1");
      zoomFactor = 1;
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
                         ", resetting to " + LineControl.VOLUME_DEFAULT);
      volume = LineControl.VOLUME_DEFAULT;
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
                         ", resetting to " + LineControl.VOLUME_DEFAULT);
      volume = LineControl.VOLUME_DEFAULT;
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
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
