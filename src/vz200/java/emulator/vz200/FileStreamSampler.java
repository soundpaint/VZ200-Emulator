package emulator.vz200;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FileStreamSampler
{
  private static final int BYTES_PER_FRAME = 2;
  private static final float SAMPLE_RATE = 44100.0f;
  private static final double INPUT_FILTER_ALPHA = 0.9;
  private static final long MAX_FEED_LENGTH = 10;

  private static final String MSG_FEED_LENGTH_OUT_OF_RANGE =
    "number of max feed length too high; " +
    "please choose higher value for alpha";

  private final long startWallClockTime;
  private final File file;
  private final long holdTime;
  private final double framesPerNanoSecond;
  private final List<CassetteTransportListener> listeners;
  private final FileInputStream inputStream;
  private final SimpleIIRFilter inputFilter;
  private final long feedLength;
  private long filePos;
  private double volume;
  private boolean stopped;

  private FileStreamSampler()
  {
    throw new UnsupportedOperationException("unsupported constructor");
  }

  public FileStreamSampler(final long wallClockTime,
                           final File file, final long holdTime)
    throws IOException
  {
    this.file = file;
    this.holdTime = holdTime;
    startWallClockTime = wallClockTime;
    framesPerNanoSecond = ((double)SAMPLE_RATE) * 0.000000001;
    listeners = new ArrayList<CassetteTransportListener>();
    inputStream = new FileInputStream(file);
    inputFilter = new SimpleIIRFilter(INPUT_FILTER_ALPHA, Short.MAX_VALUE, 0.0);
    feedLength = inputFilter.getFeedLength();
    if (feedLength > MAX_FEED_LENGTH) {
      throw new IllegalArgumentException(MSG_FEED_LENGTH_OUT_OF_RANGE);
    }
    filePos = 0;
    volume = 1.0;
    stopped = false;
    System.out.printf("%s: start playing with hold time of #%d feed samples%n",
                      getFileName(), feedLength);
  }

  public String getFileName()
  {
    return file.getName();
  }

  public void addTransportListener(final CassetteTransportListener listener)
  {
    listeners.add(listener);
  }

  private void stop()
  {
    System.out.printf("%s: end of file reached%n", getFileName());
    stopped = true;
    for (final CassetteTransportListener listener : listeners) {
      listener.cassetteStop();
    }
    inputFilter.reset();
  }

  public void setVolume(final double volume)
  {
    if (volume < 0.0) {
      throw new IllegalArgumentException("volume < 0.0");
    }
    if (volume > 1.0) {
      throw new IllegalArgumentException("volume > 1.0");
    }
    this.volume = volume;
  }

  public short getValue(final long wallClockTime)
  {
    seek(wallClockTime);
    final double value = inputFilter.getOutputValue();
    if (value <= -32768.0) return -32768;
    if (value >= 32767.0) return 32767;
    return (short)value;
  }

  /**
   * Wind to the file location that represents the specified point of
   * time.
   *
   * Note: Only seek forward!  Trying to seek backward may result in
   * unexpected behavior.
   */
  private void seek(final long wallClockTime)
  {
    if (stopped) {
      // no more data available => keep previous value
      return;
    }
    try {
      final long time = wallClockTime - startWallClockTime;
      if (time < 0) {
        // stream not yet started => keep initial value
        return;
      }
      final long nextFilePos =
        ((long)(framesPerNanoSecond * time + 0.5)) * BYTES_PER_FRAME;
      if (filePos > nextFilePos) {
        // rounding error
        System.out.printf("WARNING: rounding error: (%d > %d)%n",
                          filePos, nextFilePos);
      }
      if (filePos == nextFilePos) {
        // Data is more frequently read than updatable from the stream
        // => Assume that the previously returned value ist still
        // valid.
        return;
      }
      final long nextFilterFilePos =
        nextFilePos - feedLength * BYTES_PER_FRAME;
      final long bytesToSkip = nextFilterFilePos - filePos;
      if (bytesToSkip > 0) {
        filePos += inputStream.skip(bytesToSkip);
      }
      if (nextFilterFilePos > filePos) {
        stop();
        return;
      }
      boolean eof = false;
      for (; (filePos < nextFilePos); filePos += 2) {
        if (inputStream.available() < 2) {
          eof = true;
          break;
        }
        // 16 bit little endian, mono
        final short value =
          (short)((inputStream.read() & 0xff) |
                  ((inputStream.read() << 8) & 0xff00));
        inputFilter.addInputValue(value);
      }
      if (eof) {
        for (; (filePos < nextFilePos); filePos += 2) {
          inputFilter.addInputValue(0);
        }
        stop();
      }
    } catch (final IOException e) {
      System.out.printf("WARNING: io exception: %s%n", e);
    }
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
