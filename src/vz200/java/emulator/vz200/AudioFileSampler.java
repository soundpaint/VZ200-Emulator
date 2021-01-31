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

import emulator.z80.WallClockProvider;

/**
 * Reconstructs a single-channel audio stream from an audio file.
 * Encoding is 1 channel, 16 bits, signed PCM, little endian, 44100
 * Hz.
 */
public class AudioFileSampler implements CassetteInputSampler
{
  private static final long MAX_ACCEPTED_FEED_LENGTH = 10;
  private static final double INPUT_FILTER_ALPHA = 0.9;

  private final File file;
  private final WallClockProvider wallClockProvider;
  private final long startWallClockTime;
  private final double framesPerNanoSecond;
  private final double nanoSecondsPerFrame;
  private final AudioInputStream inputStream;
  private final SimpleIIRFilter inputFilter;
  private final long feedLength;
  private final byte[] buffer;
  private final long size;
  private final double totalNanoSeconds;
  private long filePos;
  private double volume;
  private double dcOffset;
  private boolean stopped;

  private AudioFileSampler()
  {
    throw new UnsupportedOperationException("unsupported empty constructor");
  }

  private static boolean checkAudioFileFormat(final AudioFileFormat fileFormat)
  {
    final AudioFormat format = fileFormat.getFormat();
    return format.matches(CassetteFileChooser.TOGGLE_BIT_AUDIO);
  }

  public AudioFileSampler(final File file,
                          final double volume,
                          final double speed,
                          final double dcOffset,
                          final WallClockProvider wallClockProvider,
                          final long startWallClockTime)
    throws IOException
  {
    this.file = file;
    this.volume = volume < -1.0 ? -1.0 : (volume > 1.0 ? 1.0 : volume);
    this.dcOffset = (VALUE_HI - VALUE_LO) *
      ((dcOffset < 0.7 ? 0.7 : (dcOffset > 1.3 ? 1.3 : dcOffset)) - 1.0);
    this.wallClockProvider = wallClockProvider;
    this.startWallClockTime = startWallClockTime;
    framesPerNanoSecond =
      ((double)CassetteFileChooser.DEFAULT_SAMPLE_RATE) * 0.000000001 *
      (speed < 0.9 ? 0.9 : (speed > 1.1 ? 1.1 : speed));
    nanoSecondsPerFrame = 1.0 / framesPerNanoSecond;
    final long resolution = Short.MAX_VALUE;
    inputFilter =
      new SimpleIIRFilter(INPUT_FILTER_ALPHA,
                          resolution, MAX_ACCEPTED_FEED_LENGTH, 0.0);
    feedLength = inputFilter.getFeedLength();
    buffer = new byte[2];
    try {
      if (!checkAudioFileFormat(AudioSystem.getAudioFileFormat(file))) {
        throw new IOException("unsupported audio file format");
      }
      inputStream = AudioSystem.getAudioInputStream(file);
    } catch (final UnsupportedAudioFileException e) {
      throw new IOException("unsupported audio file format", e);
    }
    size = inputStream.available();
    totalNanoSeconds =
      size * nanoSecondsPerFrame / CassetteFileChooser.DEFAULT_BYTES_PER_FRAME;
    filePos = 0;
    stopped = false;
    System.out.printf("%s: start playing with hold time of #%d feed samples%n",
                      file.getName(), feedLength);
  }

  @Override
  public File getFile()
  {
    return file;
  }

  @Override
  public boolean isStopped()
  {
    return stopped;
  }

  @Override
  public void stop()
  {
    System.out.println();
    System.out.printf("%s: end of file reached%n", file.getName());
    stopped = true;
    inputFilter.reset();
  }

  @Override
  public short getValue(final long wallClockTime)
  {
    if (stopped) {
      System.out.printf("WARNING: %s: EOF%n", file.getName());
    }
    seek(wallClockTime);
    final double value = inputFilter.getOutputValue() * volume + dcOffset;
    if (value <= VALUE_LO) return VALUE_LO;
    if (value >= VALUE_HI) return VALUE_HI;
    return (short)value;
  }

  @Override
  public float getProgress()
  {
    if (stopped) return 2.0f;
    final long wallClockTime = wallClockProvider.getWallClockTime();
    final float t1 = wallClockTime - startWallClockTime;
    final float t2 = (float)totalNanoSeconds;
    return t1 / t2;
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
