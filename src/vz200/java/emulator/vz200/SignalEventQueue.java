package emulator.vz200;

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
  private static final long INITIAL_AUDIO_DELAY = 100000000; // [ns]

  private final String label;
  private final Event[] events;
  private short currentValue;
  private long currentValueSince;
  private int consumerIndex;
  private int producerIndex;
  private long availableTimeSpan;

  public SignalEventQueue(final String label, final long currentWallClockTime)
  {
    this.label = label;
    events = new Event[BUFFER_SIZE];
    for (int i = 0; i < BUFFER_SIZE; i++) {
      events[i] = new Event();
    }
    reset(currentWallClockTime);
  }

  /**
   * Amount of signal data available.
   */
  public long getAvailableNanoSeconds()
  {
    return availableTimeSpan;
  }

  public synchronized void reset(final long currentWallClockTime)
  {
    currentValue = 0;
    currentValueSince = currentWallClockTime;
    consumerIndex = 0;
    producerIndex = 0;
    availableTimeSpan = 0;
  }

  public synchronized void resync()
  {
    events[consumerIndex].plannedGap = true;
  }

  public synchronized void put(final short value, final long wallClockTime)
  {
    if (value != currentValue) {
      final Event lastEvent = events[producerIndex];
      final long timeSpan = wallClockTime - currentValueSince;
      lastEvent.timeSpan += timeSpan;
      availableTimeSpan += timeSpan;
      producerIndex = (producerIndex + 1) % BUFFER_SIZE;
      if (producerIndex == consumerIndex) {
        System.err.printf("Warning: %s event queue overflow%n", label);
        consumerIndex = (consumerIndex + 1) % BUFFER_SIZE;
      }
      final Event event = events[producerIndex];
      event.value = value;
      event.timeSpan = 0;
      event.plannedGap = false;
      currentValueSince = wallClockTime;
      currentValue = value;
    }
  }

  public synchronized void get(final Event result, final long maxTimeSpan)
  {
    do {
      final Event event = events[consumerIndex];
      if (consumerIndex == producerIndex) {
        result.value = currentValue;
        result.timeSpan = maxTimeSpan;
        event.timeSpan -= maxTimeSpan;
        availableTimeSpan -= maxTimeSpan;
      } else {
        result.value = event.value;
        if (event.timeSpan > maxTimeSpan) {
          result.timeSpan = maxTimeSpan;
          event.timeSpan -= maxTimeSpan;
          availableTimeSpan -= maxTimeSpan;
        } else {
          result.timeSpan = event.timeSpan;
          availableTimeSpan -= event.timeSpan;
          if (result.timeSpan >= 0) {
            consumerIndex = (consumerIndex + 1) % BUFFER_SIZE;
          } else {
            event.timeSpan = INITIAL_AUDIO_DELAY;
            availableTimeSpan += INITIAL_AUDIO_DELAY;
            if (!event.plannedGap) {
              System.out.printf("Warning: %s buffer underflow, gap=%s%n",
                                label, result.prettyTimeSpanString());
            }
          }
        }
      }
    } while (result.timeSpan <= 0);
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
