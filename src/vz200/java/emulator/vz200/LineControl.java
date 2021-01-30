package emulator.vz200;

import java.awt.Frame;

import emulator.z80.WallClockProvider;

public class LineControl extends VolumeControl
{
  private static final long serialVersionUID = 4259806640992812397L;

  private final SourceDataLineControl sourceDataLineControl;

  public static LineControl
    create(final String id,
           final String borderTitle,
           final String mixerInfoId,
           final String lineInfoId,
           final double initialVolume,
           final boolean initiallyMuted,
           final WallClockProvider wallClockProvider,
           final LineControlListener userPreferencesChangeListener,
           final Frame owner)
  {
    final SourceDataLineControl sourceDataLineControl =
      new SourceDataLineControl(id, wallClockProvider,
                                mixerInfoId, lineInfoId,
                                owner);
    return
      new LineControl(id, borderTitle, mixerInfoId, lineInfoId,
                      initialVolume, initiallyMuted, wallClockProvider,
                      userPreferencesChangeListener, sourceDataLineControl);
  }

  private LineControl(final String id,
                      final String borderTitle,
                      final String mixerInfoId,
                      final String lineInfoId,
                      final double initialVolume,
                      final boolean initiallyMuted,
                      final WallClockProvider wallClockProvider,
                      final LineControlListener userPreferencesChangeListener,
                      final SourceDataLineControl sourceDataLineControl)
  {
    super(id, borderTitle, initialVolume, null, true, initiallyMuted,
          sourceDataLineControl);
    this.sourceDataLineControl = sourceDataLineControl;
    add(sourceDataLineControl);
    sourceDataLineControl.addListener(userPreferencesChangeListener);
    addListener(userPreferencesChangeListener);
  }

  public void addListener(final LineControlListener listener)
  {
    super.addListener(listener);
    sourceDataLineControl.addListener(listener);
  }

  public void removeListener(final LineControlListener listener)
  {
    super.removeListener(listener);
    sourceDataLineControl.removeListener(listener);
  }
}

/*
 * Local Variables:
 *   coding:utf-8
 *   mode:Java
 * End:
 */
