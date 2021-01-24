package emulator.vz200;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * Reconstructs a single-channel audio stream from an audio file.
 * Encoding is 1 channel, 16 bits, signed PCM, little endian, 44100
 * Hz.
 */
public class FileStreamSampler
{
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
  private final AudioInputStream inputStream;
  private final SimpleIIRFilter inputFilter;
  private final long feedLength;
  private final byte[] buffer;
  private long filePos;
  private double volume;
  private boolean stopped;

  private FileStreamSampler()
  {
    throw new UnsupportedOperationException("unsupported constructor");
  }

  private static boolean checkAudioFileFormat(final AudioFileFormat fileFormat)
  {
    final AudioFormat format = fileFormat.getFormat();
    return format.matches(CassetteFileChooser.TOGGLE_BIT_AUDIO);
  }

  public FileStreamSampler(final long wallClockTime,
                           final File file, final long holdTime)
    throws IOException
  {
    this.file = file;
    this.holdTime = holdTime;
    startWallClockTime = wallClockTime;
    framesPerNanoSecond =
      ((double)CassetteFileChooser.DEFAULT_SAMPLE_RATE) * 0.000000001;
    listeners = new ArrayList<CassetteTransportListener>();
    inputFilter = new SimpleIIRFilter(INPUT_FILTER_ALPHA, Short.MAX_VALUE, 0.0);
    feedLength = inputFilter.getFeedLength();
    if (feedLength > MAX_FEED_LENGTH) {
      throw new IllegalArgumentException(MSG_FEED_LENGTH_OUT_OF_RANGE);
    }
    buffer = new byte[2];
    try {
      if (!checkAudioFileFormat(AudioSystem.getAudioFileFormat(file))) {
        throw new IOException("unsupported audio file format");
      }
      inputStream = AudioSystem.getAudioInputStream(file);
    } catch (final UnsupportedAudioFileException e) {
      throw new IOException("unsupported audio file format", e);
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
        ((long)(framesPerNanoSecond * time + 0.5)) *
        CassetteFileChooser.DEFAULT_BYTES_PER_FRAME;
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
        nextFilePos - feedLength * CassetteFileChooser.DEFAULT_BYTES_PER_FRAME;
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
        inputStream.read(buffer, 0, 2);
        // 16 bit little endian, mono
        final short value =
          (short)((buffer[0] & 0xff) |
                  ((buffer[1] << 8) & 0xff00));
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
