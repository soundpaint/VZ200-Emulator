package emulator.vz200;

import java.awt.AWTEvent;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.Line;
import javax.sound.sampled.SourceDataLine;

public class SourceDataLineChangeEvent extends AWTEvent
{
  private static final long serialVersionUID = 6878166238096107263L;
  private static final int ID = 4711;

  private final Mixer.Info mixerInfo;
  private final Line.Info lineInfo;
  private final long currentWallClockTime;

  public SourceDataLineChangeEvent(final Object source,
                                   final Mixer.Info mixerInfo,
                                   final Line.Info lineInfo,
                                   final long currentWallClockTime)
  {
    super(source, ID);
    if (mixerInfo == null) {
      throw new NullPointerException("mixerInfo");
    }
    if (lineInfo == null) {
      throw new NullPointerException("lineInfo");
    }
    this.mixerInfo = mixerInfo;
    this.lineInfo = lineInfo;
    this.currentWallClockTime = currentWallClockTime;
  }

  public Mixer.Info getMixerInfo()
  {
    return mixerInfo;
  }

  public Line.Info getLineInfo()
  {
    return lineInfo;
  }

  public long getCurrentWallClockTime()
  {
    return currentWallClockTime;
  }
}

/*
 * Local Variables:
 *   coding:utf-8
 *   mode:Java
 * End:
 */
