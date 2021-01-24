package emulator.vz200;

import emulator.z80.WallClockProvider;

public class Speaker implements SignalEventSource, LineControlListener
{
  private final SignalEventQueue eventQueue;
  private final short[] elongation;
  private boolean muted;

  private Speaker()
  {
    throw new UnsupportedOperationException("unsupported empty constructor");
  }

  public Speaker(final WallClockProvider wallClockProvider)
  {
    elongation = new short[3];
    eventQueue =
      new SignalEventQueue("speaker", wallClockProvider, (short)0);
  }

  public void lineChanged(final SourceDataLineChangeEvent event)
  {
    eventQueue.reset(event.getCurrentWallClockTime(), (short)0);
  }

  @Override
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
    elongation[2] = (short)(2 * amplitude + 1);
  }

  public void mutedChanged(final boolean muted)
  {
    this.muted = muted;
  }

  public long getAvailableNanoSeconds()
  {
    return eventQueue.getAvailableNanoSeconds();
  }

  public void putEvent(final int plusPinValue, final int minusPinValue,
                       final long wallClockTime)
  {
    if (!muted) {
      final short currentElongation =
        elongation[plusPinValue - minusPinValue + 1];
      eventQueue.put(currentElongation, wallClockTime);
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
