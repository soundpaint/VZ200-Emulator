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
  void statisticsEnabled(final boolean enabled);
}

/*
 * Local Variables:
 *   coding:utf-8
 *   mode:Java
 * End:
 */
