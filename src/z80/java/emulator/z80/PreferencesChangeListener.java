package emulator.z80;

public interface PreferencesChangeListener
{
  /**
   * Change speed of CPU.
   * @param frequency New frequency in [Hz].  Must be greater than 0.
   * Otherwise, a runtime exception should be thrown.
   */
  void speedChanged(final int frequency);

  /**
   * Turn on / off additional code for measuring and
   * processing CPU statistics.
   * @param enabled If true, statistics code is turned on, otherwise
   * off.
   */
  void statisticsEnabledChanged(final boolean statisticsEnabled);

  /**
   * Turn on / off busy waiting when synchronizing wall clock with
   * system time.  Busy waiting yields higher timing precision on
   * instruction level granularity, but raises system CPU load to
   * 100%.  Long-term timing precision should be unaffected by this
   * switch.
   * @param busyWait If true, busy wait is turned on, otherwise
   * off.
   */
  void busyWaitChanged(final boolean busyWait);
}

/*
 * Local Variables:
 *   coding:utf-8
 *   mode:Java
 * End:
 */
