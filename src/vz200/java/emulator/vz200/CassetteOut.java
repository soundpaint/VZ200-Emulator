package emulator.vz200;

import emulator.z80.WallClockProvider;

public class CassetteOut implements SignalEventSource, LineControlListener
{
  private final SignalEventQueue eventQueue;
  private final short[] elongation;
  private boolean muted;

  private CassetteOut()
  {
    throw new UnsupportedOperationException("unsupported empty constructor");
  }

  public CassetteOut(final WallClockProvider wallClockProvider)
  {
    elongation = new short[4];
    eventQueue =
      new SignalEventQueue("cassette out", wallClockProvider, (short)0);
  }

  private void printMessage(final String message)
  {
    System.out.printf("CassetteOut: %s%n", message);
  }

  public void lineChanged(final SourceDataLineChangeEvent event)
  {
    eventQueue.reset(event.getCurrentWallClockTime(), (short)0);
  }

  public void resync()
  {
    eventQueue.resync();
  }

  public void volumeChanged(final double volume)
  {
    final int amplitude =
      (int)Math.min(Math.max(volume * 32767.0, 0.0), 32767.0);
    elongation[0] = 0;
    elongation[1] = (short)amplitude;
    elongation[2] = (short)amplitude;
    elongation[3] = (short)(2 * amplitude + 1);
    printMessage("amplitude changed: " + amplitude);
  }

  public void mutedChanged(final boolean muted)
  {
    this.muted = muted;
  }

  public long getAvailableNanoSeconds()
  {
    return eventQueue.getAvailableNanoSeconds();
  }

  public void putEvent(final int dataValue, final long wallClockTime)
  {
    if (!muted) {
      short signalValue = elongation[dataValue];
      eventQueue.put(signalValue, wallClockTime);
    }
  }

  public void getEvent(final SignalEventQueue.Event event,
                       final long maxTimeSpan)
  {
    if (maxTimeSpan <= 0) {
      throw new IllegalArgumentException("maxTimeSpan <= 0");
    }
    eventQueue.get(event, maxTimeSpan);
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
