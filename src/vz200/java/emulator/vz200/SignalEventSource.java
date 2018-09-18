package emulator.vz200;

public interface SignalEventSource {
  public void getEvent(SignalEventQueue.Event event, long maxTimeSpan);
}
