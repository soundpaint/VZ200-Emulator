package emulator.vz200;

import emulator.z80.WallClockProvider;

public class CassetteFileOut implements SignalEventSource
{
  private final SignalEventQueue eventQueue;
  private final short[] elongation;

  private CassetteFileOut()
  {
    throw new UnsupportedOperationException("unsupported empty constructor");
  }

  public CassetteFileOut(final WallClockProvider wallClockProvider)
  {
    elongation = new short[] {-32768, -30720, 0, +32767};
    eventQueue =
      new SignalEventQueue("cassette file out", wallClockProvider, (short)0);
  }

  private void printMessage(final String message)
  {
    System.out.printf("CassetteFileOut: %s%n", message);
  }

  public void resync()
  {
    eventQueue.resync();
  }

  @Override
  public long getAvailableNanoSeconds()
  {
    return eventQueue.getAvailableNanoSeconds();
  }

  public void putEvent(final int dataValue, final long wallClockTime)
  {
    final short signalValue = elongation[dataValue];
    eventQueue.put(signalValue, wallClockTime);
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
