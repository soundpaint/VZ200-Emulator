package emulator.vz200;

import emulator.z80.WallClockProvider;

/**
 * Cyclic first-in first-out ring buffer for signal value change
 * events.
 *
 * Invariants:
 *
 * • The buffer is cyclic.  That is, when an index into the buffer
 *   (specifically, the producerIndex and the consumerIndex) points to
 *   the maximum index of the buffer, increasing it by one means
 *   setting it to the minimum index of the buffer.  In other words,
 *   handling of indices is always modulo the size of the buffer.
 *
 * • There is always at least one event in the queue, namely the
 *   latest or "head" event, i.e. the event that has been inserted
 *   most recently.  Upon startup, the initial head event is that of
 *   resetting the signal to a neutral value at zero wall clock time.
 *
 * • Variable producerIndex always points to the "head" event
 *   (provided the queue is not just in the act of undergoing an
 *   update).
 *
 * • When the event producer inserts a new event, it is put behind the
 *   "head" event (i.e. at the position with the next upper index).
 *   This newly inserted event then becomes the new "head" event, that
 *   is, the value of producerIndex increases by one.  The new head
 *   event's delta time field is initialized to 0.
 *
 * • If the new event to be inserted would not effectively change the
 *   current signal value, then no new event is inserted into the
 *   queue.  Instead, for confirming that the signal keeps its value
 *   for longer, the head event is updated to cover the timespan from
 *   its original creation time to the new current time.
 *
 * • The producer ignores any buffer overflow.  That is, it will
 *   overwrite any pending old data that has not yet been consumed.
 *   However, in the case of a buffer overflow, the consumerIndex will
 *   be updated accordingly.  That is, it must be increased by one,
 *   just as the producerIndex.
 *
 * • The event to insert comes along with the absolute wall clock time
 *   at which the event happens.  The difference between this time and
 *   the time at which the previous event happened, is stored into the
 *   previous head event's delta time field.
 *
 * • The head event's delta time field is unused.  However, for safety
 *   reasons, it always should be set to 0.
 *
 * • Variable consumerIndex always points to the event that would be
 *   consumed next (provided the queue is not just in the act of
 *   undergoing an update).  If there is only the "head" event in the
 *   queue, then consumerIndex points to the "head" event.  In that
 *   case, consumerIndex and producerIndex consequently have the same
 *   value.
 *
 * • When consuming an event, the consumer indicates the maximum time
 *   span that the event to be consumed may cover.  This means, that
 *   if an event spans a larger amount of time, this event will stay
 *   in the buffer, but only its remaining span time will be reduced
 *   by the maximum time.  Only, if the maximum time exceeds the
 *   event's span time, this event will be marked as exhausted and,
 *   consequently, the consumerIndex increased to point to the next
 *   event.
 *
 * • If there is only the head event in the queue, its span time will
 *   be reduced, but no event dropped from the queue.  That is, the
 *   queue is not changed (except for the head event's updated span
 *   time), but only data from the head event is returned.
 *
 * • Note that the previous point means that in the case of a buffer
 *   underrun, the span time of the head event may (temporily) drop
 *   below zero.
 */
public class SignalEventQueue
{
  public static class Event
  {
    /**
     * Signal value that is announced with this event.
     */
    public short value;

    /**
     * Duration in nanoseconds for that the value is known to have not
     * changed.
     */
    public long timeSpan;

    /**
     * If true, supress buffer underflow warnings.  Used for ordinary,
     * planned signal gaps, e.g. when halting the CPU while working
     * with the CPU monitor.
     */
    public boolean plannedGap;

    /**
     * Pretty print time span in milliseconds.
     */
    public String prettyTimeSpanString()
    {
      return String.format("%fms", 0.000001 * timeSpan);
    }

    public String toString()
    {
      return String.format("SignalEventQueue.Event{value=%d, timeSpan=%s}",
                           value, prettyTimeSpanString());
    }
  }

  private static final int BUFFER_SIZE = 0x2800;

  /**
   * On the long-term average, the simulated CPU will run with
   * constant speed: If it runs too fast, a few milliseconds of delay
   * will be inserted; if it is behind time, it will run as fast as
   * possible to catch up with the actual time (as observed by the
   * user), resulting in observable jitter in the range of a few
   * milliseconds.  This jitter is relevant when e.g. rendering a
   * simulated speaker to e.g. the host's soundcard, which runs with
   * its own clock.  The jitter, as observed when comparing these two
   * clocks, leads to fluctuations between the distance of the
   * producerIndex and consumerIndex.  Therefore, some headroom is
   * required to tolerate these fluctuations.  We create this headroom
   * by slightly delaying audio (or whatever else) signal output.
   *
   * Increasing the delay value will reduce the likelyhood of signal
   * glitches caused by buffer underruns.  However, at the same time,
   * it increases the likelyhood of buffer overflows.  That is, when
   * increasing this value, the BUFFER_SIZE value should also be
   * increased.  Moreover, overly increased latency will eventually
   * result in perceptible delay, which can be perceived as
   * uncomfortable.
   *
   * 100ms seems to be a good trade-off, but a good choice for this
   * value may depend on the hardware and operating system that this
   * emulation is running on.  For example, when using a kernel with
   * realtime patches, you may want to decrease this value to reduce
   * perceivable audio delay.  On the contrary, if you still hear
   * audio glitches, you may want to increase this value, if you can
   * bear higher audio delay.
   */
  private static final long INITIAL_DELAY = 100000000; // [ns]

  private final String label;
  private final WallClockProvider wallClockProvider;
  private final Event[] events;
  private short latestValue;
  private long latestWallClockTime;
  private int consumerIndex;
  private int producerIndex;
  private long availableTimeSpan;

  public SignalEventQueue(final String label,
                          final WallClockProvider wallClockProvider,
                          final short initialValue)
  {
    this.label = label;
    this.wallClockProvider = wallClockProvider;
    events = new Event[BUFFER_SIZE];
    for (int i = 0; i < BUFFER_SIZE; i++) {
      events[i] = new Event();
    }
    reset(wallClockProvider.getWallClockTime(), initialValue);
  }

  /**
   * Amount of signal data available.
   */
  public long getAvailableNanoSeconds()
  {
    updateLatestEventForTime(wallClockProvider.getWallClockTime());
    return availableTimeSpan;
  }

  public synchronized void reset(final long wallClockTime, final short value)
  {
    latestWallClockTime = wallClockTime - INITIAL_DELAY;
    latestValue = value;
    consumerIndex = 0;
    producerIndex = 0;
    final Event event = events[producerIndex];
    event.value = value;
    event.timeSpan = INITIAL_DELAY;
    event.plannedGap = false;
    availableTimeSpan = INITIAL_DELAY;
    put(value, wallClockTime);
  }

  public synchronized void resync()
  {
    reset(wallClockProvider.getWallClockTime(), events[producerIndex].value);
    events[consumerIndex].plannedGap = true;
  }

  private Event updateLatestEventForTime(final long wallClockTime)
  {
    final Event latestEvent = events[producerIndex];
    final long timeSpan = wallClockTime - latestWallClockTime;
    latestEvent.timeSpan += timeSpan;
    availableTimeSpan += timeSpan;
    latestWallClockTime = wallClockTime;
    return latestEvent;
  }

  public synchronized void put(final short value, final long wallClockTime)
  {
    if (wallClockTime < latestWallClockTime) {
      System.err.printf("Warning: %s: ignoring out-of-order event%n", label);
      return;
    }
    final Event latestEvent = updateLatestEventForTime(wallClockTime);
    if (value != latestValue) {
      producerIndex = (producerIndex + 1) % BUFFER_SIZE;
      if (producerIndex == consumerIndex) {
        // drop oldest event
        System.err.printf("Warning: %s: event queue overflow%n", label);
        availableTimeSpan -= events[consumerIndex].timeSpan;
        consumerIndex = (consumerIndex + 1) % BUFFER_SIZE;
      }
      final Event event = events[producerIndex];
      event.value = value;
      if (latestEvent.timeSpan < 0) {
        // transfer borrowed time, if any, to latest event
        event.timeSpan = latestEvent.timeSpan;
        latestEvent.timeSpan = 0;
      } else {
        event.timeSpan = 0;
      }
      event.plannedGap = false;
      latestWallClockTime = wallClockTime;
      latestValue = value;
    }
  }

  public synchronized void get(final Event result, final long maxTimeSpan)
  {
    updateLatestEventForTime(wallClockProvider.getWallClockTime());
    final Event event = events[consumerIndex];
    if (consumerIndex != producerIndex) {
      result.value = event.value;
      if (event.timeSpan > maxTimeSpan) {
        result.timeSpan = maxTimeSpan;
        event.timeSpan -= maxTimeSpan;
        availableTimeSpan -= maxTimeSpan;
      } else {
        result.timeSpan = event.timeSpan;
        availableTimeSpan -= event.timeSpan;
        consumerIndex = (consumerIndex + 1) % BUFFER_SIZE;
      }
    } else {
      result.value = latestValue;
      result.timeSpan = maxTimeSpan;
      event.timeSpan -= maxTimeSpan;
      availableTimeSpan -= maxTimeSpan;
      if ((event.timeSpan < 0) && !event.plannedGap) {
        System.out.printf("Warning: %s: buffer underflow, gap=%s, " +
                          "availableTimeSpan=%s%n",
                          label, event.prettyTimeSpanString(),
                          availableTimeSpan);
      }
    }
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
