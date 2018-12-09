package emulator.vz200;

public class Speaker implements SignalEventSource, SpeakerControlListener
{
  private final SignalEventQueue eventQueue;
  private final short[] elongation;
  private boolean muted;

  private Speaker()
  {
    throw new UnsupportedOperationException("unsupported constructor");
  }

  public Speaker(final long currentWallClockTime)
  {
    elongation = new short[3];
    eventQueue = new SignalEventQueue("speaker", currentWallClockTime);
    setVolume(SpeakerControl.VOLUME_DEFAULT);
  }

  public void resync()
  {
    eventQueue.resync();
  }

  public void setVolume(final double volume)
  {
    final int amplitude =
      (int)Math.min(Math.max(volume * 32767.0, 0.0), 32767.0);
    /*
    elongation[0] = (short)-amplitude;
    elongation[1] = 0;
    elongation[2] = (short)amplitude;
    */
    elongation[0] = 0;
    elongation[1] = (short)amplitude;
    elongation[2] = (short)(2 * amplitude + 1);
  }

  public void setMuted(final boolean muted)
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
