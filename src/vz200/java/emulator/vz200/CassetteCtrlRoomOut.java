package emulator.vz200;

import emulator.z80.WallClockProvider;

public class CassetteCtrlRoomOut
  implements SignalEventSource, LineControlListener
{
  private final SignalEventQueue eventQueue;
  private final short[] elongation;
  private boolean muted;

  private CassetteCtrlRoomOut()
  {
    throw new UnsupportedOperationException("unsupported empty constructor");
  }

  public CassetteCtrlRoomOut(final WallClockProvider wallClockProvider)
  {
    elongation = new short[4];
    eventQueue =
      new SignalEventQueue("cassette ctrl room out",
                           wallClockProvider, (short)0);
  }

  private void printMessage(final String message)
  {
    System.out.printf("CassetteCtrlRoomOut: %s%n", message);
  }

  @Override
  public void lineChanged(final SourceDataLineChangeEvent event)
  {
    eventQueue.reset(event.getCurrentWallClockTime(), (short)0);
  }

  @Override
  public void resync()
  {
    eventQueue.resync();
  }

  @Override
  public void volumeChanged(final double volume)
  {
    final int amplitude =
      (int)Math.min(Math.max(volume * 32767.0, 0.0), 32767.0);
    elongation[0] = (short)(amplitude >>> 8);
    elongation[1] = (short)(amplitude >>> 4);
    elongation[2] = (short)amplitude;
    elongation[3] = (short)(-(amplitude >>> 8) - 1);
    printMessage("amplitude changed: " + amplitude);
  }

  @Override
  public void mutedChanged(final boolean muted)
  {
    this.muted = muted;
  }

  @Override
  public long getAvailableNanoSeconds()
  {
    return eventQueue.getAvailableNanoSeconds();
  }

  public void putEvent(final int dataValue, final long wallClockTime)
  {
    if (!muted) {
      final short signalValue = elongation[dataValue];
      eventQueue.put(signalValue, wallClockTime);
    }
  }

  @Override
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
