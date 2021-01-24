package emulator.vz200;

public interface SignalEventSource
{
  long getAvailableNanoSeconds();
  void getEvent(SignalEventQueue.Event event, long maxTimeSpan);
  void resync();
}
