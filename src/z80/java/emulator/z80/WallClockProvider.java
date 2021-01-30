package emulator.z80;

public interface WallClockProvider
{
  /**
   * Returns the total number of time in ns per CPU T cycle
   * (i.e. the reciprocal of the CPU speed in MHz).
   */
  long getTimePerClockCycle();

  /**
   * Returns the total number of instruction cycles of all
   * instructions performed since CPU start.
   */
  long getWallClockCycles();

  /**
   * Returns the total number of time in ns of all instructions
   * performed since CPU start.
   */
  long getWallClockTime();
}

/*
 * Local Variables:
 *   coding:utf-8
 *   mode:Java
 * End:
 */
