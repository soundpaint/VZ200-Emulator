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

  public void setSpeakerVolume(final double volume)
  {
    vz200Preferences.putDouble(PREFS_NAME_SPEAKER_VOLUME, volume);
  }

  public double getSpeakerVolume()
  {
    double speakerVolume =
      vz200Preferences.getDouble(PREFS_NAME_SPEAKER_VOLUME,
                                 PREFS_DEFAULT_SPEAKER_VOLUME);
    if ((speakerVolume < 0.0) ||
        (speakerVolume > 1.0)) {
      System.out.println("error: unexpected speaker volume: " + speakerVolume +
                         ", resetting to " +
                         SpeakerControl.VOLUME_DEFAULT);
      speakerVolume = SpeakerControl.VOLUME_DEFAULT;
      setSpeakerVolume(speakerVolume);
    }
    return speakerVolume;
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
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
