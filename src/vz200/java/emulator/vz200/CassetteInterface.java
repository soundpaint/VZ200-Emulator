package emulator.vz200;

public class CassetteInterface implements SignalEventSource {
  private static final short DEFAULT_AMPLITUDE = 5000;

  private final short[] VALUE = new short[4];

  private SignalEventQueue eventQueue;
  private boolean active;

  public CassetteInterface(long currentWallClockTime) {
    eventQueue = new SignalEventQueue("cassette", currentWallClockTime);
    try {
      active = true;
    } catch (Throwable t) {
      active = false;
      System.err.println("WARNING: Failed opening audio stream.  " +
                         "No audio output will be produced.");
    }
    setAmplitude(DEFAULT_AMPLITUDE);
  }

  public void resync() {
    eventQueue.resync();
  }

  public void setAmplitude(short amplitude) {
    VALUE[0] = (short)-amplitude;
    VALUE[1] = 0;
    VALUE[2] = 0;
    VALUE[3] = amplitude;
  }

  public void putEvent(int dataValue, long wallClockTime) {
    short signalValue = VALUE[dataValue];
    if (active) {
      eventQueue.put(signalValue, wallClockTime);
    }
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
