package emulator.vz200;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import emulator.z80.UserPreferences;
import emulator.z80.WallClockProvider;

/**
 * Reconstructs a single-channel audio stream from a VZ file.
 */
public class VZFileSampler implements CassetteInputSampler
{
  private static final int LEAD_IN_0X80_COUNT = 255;
  private static final int LEAD_IN_0X80_TRIMMED_COUNT = 10;
  private static final int LEAD_IN_0XFE_COUNT = 5;
  private static final long DEFAULT_HALF_SHORT_CYCLE = 287103; // [ns]
  private static final long DEFAULT_GAP_TIME_SPAN = 3065000; // [ns]
  private final File file;
  private final boolean trimLeadIn;
  private final WallClockProvider wallClockProvider;
  private final long startWallClockTime;
  private final VZFile vzFile;
  private final long halfShortCycle; // [ns]
  private final long bitTimeSpan; // [ns]
  private final long byteTimeSpan; // [ns]
  private final long gapTimeSpan; // [ns]
  private final long leadIn0x80StartTime;
  private final long leadIn0xfeStartTime;
  private final long fileTypeStartTime;
  private final long fileNameStartTime;
  private final long gapStartTime;
  private final long startAddrLoStartTime;
  private final long startAddrHiStartTime;
  private final long endAddrLoStartTime;
  private final long endAddrHiStartTime;
  private final long contentStartTime;
  private final long chkSumLoStartTime;
  private final long chkSumHiStartTime;
  private final long leadOutStartTime;
  private final long eofStartTime;
  private byte lastValue;
  private boolean stopped;

  private VZFileSampler()
  {
    throw new UnsupportedOperationException("unsupported empty constructor");
  }

  public VZFileSampler(final File file,
                       final double speed,
                       final boolean trimLeadIn,
                       final WallClockProvider wallClockProvider,
                       final long wallClockTime)
    throws IOException
  {
    this.file = file;
    this.trimLeadIn = trimLeadIn;
    this.wallClockProvider = wallClockProvider;
    startWallClockTime = wallClockTime;
    vzFile = VZFile.fromFile(file);
    final long timePerClockCycle = wallClockProvider.getTimePerClockCycle();
    final double designedFrequency = UserPreferences.PREFS_DEFAULT_FREQUENCY;
    /*
     * FIXME: Due to the fact that timePerClockCycle is a long value
     * with nanoseconds resolution only rather than a double value,
     * the rounding error here is finally up to around 1% (assuming a
     * CPU speed up to 10MHz).
     */
    final double cycleScale =
      designedFrequency * timePerClockCycle / 1000000000.0 / speed;
    halfShortCycle = Math.round(DEFAULT_HALF_SHORT_CYCLE * cycleScale);
    bitTimeSpan = 6 * halfShortCycle;
    byteTimeSpan = 8 * bitTimeSpan;
    gapTimeSpan = Math.round(DEFAULT_GAP_TIME_SPAN * cycleScale);
    leadIn0x80StartTime = wallClockTime;
    final int leadIn0x80Count =
      trimLeadIn ? LEAD_IN_0X80_TRIMMED_COUNT : LEAD_IN_0X80_COUNT;
    leadIn0xfeStartTime = leadIn0x80StartTime + leadIn0x80Count * byteTimeSpan;
    fileTypeStartTime = leadIn0xfeStartTime + LEAD_IN_0XFE_COUNT * byteTimeSpan;
    fileNameStartTime = fileTypeStartTime + 1 * byteTimeSpan;
    gapStartTime = fileNameStartTime +
      (vzFile.getFileName().length() + 1) * byteTimeSpan;
    startAddrLoStartTime = gapStartTime + gapTimeSpan;
    startAddrHiStartTime = startAddrLoStartTime + 1 * byteTimeSpan;
    endAddrLoStartTime = startAddrHiStartTime + 1 * byteTimeSpan;
    endAddrHiStartTime = endAddrLoStartTime + 1 * byteTimeSpan;
    contentStartTime = endAddrHiStartTime + 1 * byteTimeSpan;
    chkSumLoStartTime =
      contentStartTime + vzFile.getContentSize() * byteTimeSpan;
    chkSumHiStartTime = chkSumLoStartTime + 1 * byteTimeSpan;
    leadOutStartTime = chkSumHiStartTime + 1 * byteTimeSpan;
    eofStartTime = leadOutStartTime + 20 * byteTimeSpan;
    stopped = false;
    System.out.printf("%s: start playing %s%n", file.getName(), vzFile);
  }

  @Override
  public boolean isStopped()
  {
    return stopped;
  }

  @Override
  public File getFile()
  {
    return file;
  }

  @Override
  public float getProgress()
  {
    if (stopped) return 2.0f;
    final long wallClockTime = wallClockProvider.getWallClockTime();
    final float t1 = (float)(wallClockTime - startWallClockTime);
    final float t2 = (float)(eofStartTime - startWallClockTime);
    return t1 / t2;
  }

  @Override
  public void stop()
  {
    System.out.println();
    System.out.printf("%s: end of file reached%n", getFile().getName());
    stopped = true;
  }

  private short getValueOfBit(final int bit, final long time)
  {
    if (time <= halfShortCycle)
      return VALUE_HI;
    if (time <= 2 * halfShortCycle)
      return VALUE_LO;
    if (time <= 3 * halfShortCycle)
      return VALUE_HI;
    if (time <= 4 * halfShortCycle)
      return bit == 1 ? VALUE_LO : VALUE_HI;
    if (time <= 5 * halfShortCycle)
      return bit == 1 ? VALUE_HI : VALUE_LO;
    return VALUE_LO;
  }

  private short getValueOfByte(final byte b, long time)
  {
    time %= byteTimeSpan;
    final int bit = (int)(time / bitTimeSpan);
    if ((bit < 0) || (bit > 7)) throw new RuntimeException("bit=" + bit);
    return getValueOfBit((b >>> (7 - bit)) & 0x1, time % bitTimeSpan);
  }

  @Override
  public short getValue(final long wallClockTime)
  {
    if (stopped) {
      System.out.printf("WARNING: %s: EOF (%s)%n", file.getName(), vzFile);
      return VALUE_LO;
    }
    if (wallClockTime < leadIn0xfeStartTime) {
      return getValueOfByte((byte)0x80, wallClockTime - leadIn0x80StartTime);
    }
    if (wallClockTime < fileTypeStartTime) {
      return getValueOfByte((byte)0xfe, wallClockTime - leadIn0xfeStartTime);
    }
    if (wallClockTime < fileNameStartTime) {
      final int b = vzFile.getFileType();
      return getValueOfByte((byte)b, wallClockTime - fileTypeStartTime);
    }
    if (wallClockTime < gapStartTime) {
      final String fileName = vzFile.getFileName();
      final int index =
        (int)((wallClockTime - fileNameStartTime) / byteTimeSpan);
      final int b = index < fileName.length() ? fileName.charAt(index) : 0;
      return getValueOfByte((byte)b, wallClockTime - fileNameStartTime);
    }
    if (wallClockTime < startAddrLoStartTime) {
      return VALUE_LO;
    }
    if (wallClockTime < startAddrHiStartTime) {
      final int startAddrLo = vzFile.getStartAddress() & 0xff;
      return
        getValueOfByte((byte)startAddrLo, wallClockTime - startAddrLoStartTime);
    }
    if (wallClockTime < endAddrLoStartTime) {
      final int startAddrHi = vzFile.getStartAddress() >>> 8;
      return
        getValueOfByte((byte)startAddrHi, wallClockTime - startAddrHiStartTime);
    }
    if (wallClockTime < endAddrHiStartTime) {
      final int endAddrLo = vzFile.getEndAddress() & 0xff;
      return
        getValueOfByte((byte)endAddrLo, wallClockTime - endAddrLoStartTime);
    }
    if (wallClockTime < contentStartTime) {
      final int endAddrHi = vzFile.getEndAddress() >>> 8;
      return
        getValueOfByte((byte)endAddrHi, wallClockTime - endAddrHiStartTime);
    }
    if (wallClockTime < chkSumLoStartTime) {
      final int index =
        (int)((wallClockTime - contentStartTime) / byteTimeSpan);
      return getValueOfByte(vzFile.getContentByte(index),
                            wallClockTime - contentStartTime);
    }
    if (wallClockTime < chkSumHiStartTime) {
      final int chkSumLo = vzFile.getCheckSum() & 0xff;
      return getValueOfByte((byte)chkSumLo, wallClockTime - chkSumLoStartTime);
    }
    if (wallClockTime < leadOutStartTime) {
      final int chkSumHi = vzFile.getCheckSum() >>> 8;
      return getValueOfByte((byte)chkSumHi, wallClockTime - chkSumHiStartTime);
    }
    if (wallClockTime < eofStartTime) {
      return getValueOfByte((byte)0x00, wallClockTime - leadOutStartTime);
    }
    stop();
    return VALUE_LO;
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
