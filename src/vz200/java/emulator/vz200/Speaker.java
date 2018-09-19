package emulator.vz200;

public class Speaker implements SignalEventSource {
  private static final short DEFAULT_AMPLITUDE = 5000;

  private final short[] ELONGATION = new short[3];

  private SignalEventQueue eventQueue;

  public Speaker(long currentWallClockTime) {
    eventQueue = new SignalEventQueue("speaker", currentWallClockTime);
    setAmplitude(DEFAULT_AMPLITUDE);
  }

  public void resync() {
    eventQueue.resync();
  }

  public void setAmplitude(short amplitude) {
    ELONGATION[0] = (short)-amplitude;
    ELONGATION[1] = 0;
    ELONGATION[2] = amplitude;
  }

  public long getAvailableNanoSeconds() {
    return eventQueue.getAvailableNanoSeconds();
  }

  public void putEvent(int plusPinValue, int minusPinValue,
                       long wallClockTime) {
    short elongation = ELONGATION[plusPinValue - minusPinValue + 1];
    eventQueue.put(elongation, wallClockTime);
  }

  public void getEvent(SignalEventQueue.Event event, long maxTimeSpan) {
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
