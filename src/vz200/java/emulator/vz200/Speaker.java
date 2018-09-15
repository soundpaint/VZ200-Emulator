package emulator.vz200;

import emulator.z80.CPU;

public class Speaker {
  public static class Event {
    public short value;

    // duration for that the value holds
    public long deltaWallClockTime;
  }

  /**
   * Cyclic ring buffer for speaker status change events.
   *
   * Invariants:
   *
   * • There is always at least one event in the queue, namely the
   *   "head" event, i.e. the event that has been inserted most
   *   recently.  Upon startup, the initial head event is that of
   *   pulling the speaker's membrane into a neutral position (0) at
   *   zero wall clock time.
   *
   * • Variable producerIndex always points to the "head" event
   *   (provided the queue is not just in the act of undergoing an
   *   update).
   *
   * • When the event producer inserts a new event, it is put behind
   *   the current "head" event and then becomes the new "head" event,
   *   that is, the value of producerIndex increases by one.  The new
   *   head event's delta time field is initialized to 0.
   *
   * • The producer ignores any buffer overflow.  That is, it will
   *   overwrite any pending old data that has not yet been consumed.
   *   However, the consumerIndex must be updated accordingly.  That
   *   is, it must be increased by one, just as the producerIndex.
   *
   * • The event to insert comes along with the absolute wall clock
   *   time at which the event happens.  The difference between this
   *   time and the time at which the previous event happened, is
   *   stored into the previous head event's delta time field.
   *
   * • The head event's delta time field is unused.  However, for
   *   safety reasons, it always should be set to 0.
   *
   * • If an event to be inserted effectively would not change the
   *   current status of the speaker (except for confirming that the
   *   speaker keeps its status for longer), then the event is
   *   ignored.
   *
   * • The buffer is cyclic, that is, when producerIndex points to the
   *   maximum index of the buffer, increasing it by one means setting
   *   it to the minimum index of the buffer.
   *
   * • Variable consumerIndex always points to the event that would be
   *   consumed next (provided the queue is not just in the act of
   *   undergoing an update).  If there is only the "head" event in
   *   the queue, then consumerIndex points to the "head" event.  In
   *   that case, consumerIndex and producerIndex consequently have
   *   the same value.
   *
   * • The buffer is cyclic, that is, when consumerIndex points to the
   *   maximum index of the buffer, increasing it by one means setting
   *   it to the minimum index of the buffer.
   *
   * • When consuming an event, the consumer indicates the maximum
   *   time span that the event to be consumed may cover.
   *
   * • If the actual time span of the next event to consume is greater
   *   than the maximum desired span, than the event keeps in the
   *   queue, but only its delta time is reduced by the maximum value.
   *
   * • If there is only the head event in the queue, a pseudo event is
   *   created on-the-fly.  That is, the queue is not changed, but
   *   only data from the head event is returned.
   */
  private class EventFiFo {
    private static final int BUFFER_SIZE = 0x2800;

    private Event[] events;
    private int consumerIndex;
    private int producerIndex;

    public EventFiFo() {
      events = new Event[BUFFER_SIZE];
      for (int i = 0; i < BUFFER_SIZE; i++) {
        events[i] = new Event();
      }
      consumerIndex = 0;
      producerIndex = 0;
    }

    public synchronized void put(short value, long wallClockTime) {
      Event lastEvent = events[producerIndex];
      lastEvent.deltaWallClockTime =
        wallClockTime - currentValueSinceWallClockTime;
      producerIndex = (producerIndex + 1) % BUFFER_SIZE;
      if (producerIndex == consumerIndex) {
        System.err.println("Warning: Speaker event queue overflow");
        consumerIndex = (consumerIndex + 1) % BUFFER_SIZE;
      }
      Event event = events[producerIndex];
      event.value = value;
      event.deltaWallClockTime = 0;
      currentValueSinceWallClockTime = wallClockTime;
      currentValue = value;
    }

    private synchronized void get(Event event, long maxTimeSpan) {
      if (consumerIndex == producerIndex) {
        long wallClockTime = cpu.getWallClockTime();
        currentValueSinceWallClockTime = cpu.getWallClockTime();
        event.value = currentValue;
        event.deltaWallClockTime = maxTimeSpan;
      } else {
        Event ev = events[consumerIndex];
        event.value = ev.value;
        if (ev.deltaWallClockTime > maxTimeSpan) {
          event.deltaWallClockTime = maxTimeSpan;
          ev.deltaWallClockTime -= maxTimeSpan;
        } else {
          event.deltaWallClockTime = ev.deltaWallClockTime;
          consumerIndex = (consumerIndex + 1) % BUFFER_SIZE;
        }
      }
    }

    private synchronized int size() {
      return (BUFFER_SIZE + producerIndex - consumerIndex) % BUFFER_SIZE;
    }
  }

  private EventFiFo eventFiFo;
  private CPU cpu;
  private long currentValueSinceWallClockTime;
  private short currentValue;
  private AudioRenderer audioRenderer;
  private boolean active;

  public Speaker(CPU cpu) {
    this.eventFiFo = new EventFiFo();
    this.cpu = cpu;
    currentValueSinceWallClockTime = cpu.getWallClockTime();
    try {
      active = true;
      audioRenderer = new AudioRenderer(this);
      audioRenderer.start();
    } catch (Throwable t) {
      active = false;
      System.err.println("WARNING: Failed opening audio stream.  " +
                         "No audio output will be produced.");
    }
  }

  public void putEvent(short value, long wallClockTime) {
    if (active && (value != currentValue)) {
      eventFiFo.put(value, wallClockTime);
    }
  }

  public void getEvent(Event event, long maxTimeSpan) {
    eventFiFo.get(event, maxTimeSpan);
  }

  public int queueSize() {
    return eventFiFo.size();
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
