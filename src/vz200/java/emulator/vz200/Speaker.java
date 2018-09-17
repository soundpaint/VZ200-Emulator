package emulator.vz200;

public class Speaker {
  private static final short DEFAULT_AMPLITUDE = 5000;

  private final short[] ELONGATION = new short[3];

  private SignalEventQueue eventQueue;
  private boolean active;
  private AudioRenderer audioRenderer;

  public Speaker(long currentWallClockTime) {
    eventQueue = new SignalEventQueue("speaker", currentWallClockTime);
    try {
      active = true;
      audioRenderer = new AudioRenderer(this);
      audioRenderer.start();
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
    ELONGATION[0] = (short)-amplitude;
    ELONGATION[1] = 0;
    ELONGATION[2] = amplitude;
  }

  public void putEvent(int plusPinValue, int minusPinValue,
                       long wallClockTime) {
    short elongation = ELONGATION[plusPinValue - minusPinValue + 1];
    if (active) {
      eventQueue.put(elongation, wallClockTime);
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
