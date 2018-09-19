package emulator.vz200;

public interface SignalEventSource {
  public long getAvailableNanoSeconds();
  public void getEvent(SignalEventQueue.Event event, long maxTimeSpan);
}
