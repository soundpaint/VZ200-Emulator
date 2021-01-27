package emulator.vz200;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reconstructs a single-channel audio stream from a VZ file.
 */
public class VZFileSampler implements CassetteInputSampler
{
  private static final short VALUE_LO = -32768;
  private static final short VALUE_HI = 32767;
  private static final int leadIn1Bytes = 255;
  private static final int leadIn2Bytes = 5;
  private static final long halfShortCycle = 287103; // [ns]
  private static final long bitTimeSpan = 6 * halfShortCycle; // [ns]
  private static final long byteTimeSpan = 8 * bitTimeSpan; // [ns]
  private static final long gapTimeSpan = 3065000; // [ns]
  private final long startWallClockTime;
  private final File file;
  private final VZFile vzFile;
  private final long leadIn1StartTime;
  private final long leadIn2StartTime;
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
  private long filePos;
  private byte lastValue;
  private boolean stopped;

  private VZFileSampler()
  {
    throw new UnsupportedOperationException("unsupported constructor");
  }

  public VZFileSampler(final long wallClockTime, final File file)
    throws IOException
  {
    this.file = file;
    this.vzFile = VZFile.fromFile(file);
    leadIn1StartTime = wallClockTime;
    leadIn2StartTime = leadIn1StartTime + leadIn1Bytes * byteTimeSpan;
    fileTypeStartTime = leadIn2StartTime + leadIn2Bytes * byteTimeSpan;
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
    startWallClockTime = wallClockTime;
    stopped = false;
    System.out.printf("%s: start playing %s%n", getFileName(), vzFile);
  }

  @Override
  public boolean isStopped()
  {
    return stopped;
  }

  @Override
  public String getFileName()
  {
    return file.getName();
  }

  private void stop()
  {
    System.out.println();
    System.out.printf("%s: end of file reached%n", getFileName());
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
    if (stopped)
      throw new IllegalStateException("VZFileSampler: EOF");
    if (wallClockTime < leadIn2StartTime) {
      return getValueOfByte((byte)0x80, wallClockTime - leadIn1StartTime);
    }
    if (wallClockTime < fileTypeStartTime) {
      return getValueOfByte((byte)0xfe, wallClockTime - leadIn2StartTime);
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
