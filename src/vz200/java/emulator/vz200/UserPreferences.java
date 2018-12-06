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

  private UserPreferences()
  {
    vz200Preferences = Preferences.userRoot().node(PREFS_PATH_VZ200);
  }

  public static UserPreferences getInstance()
  {
    return instance;
  }

  public void setVideoZoomFactor(int zoomFactor)
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
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
