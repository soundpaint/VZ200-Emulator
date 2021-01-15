package emulator.vz200;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FileStreamSampler {
  private static final int BYTES_PER_FRAME = 2;
  private static final float SAMPLE_RATE = 44100.0f;
  private static final double INPUT_FILTER_ALPHA = 0.9;
  private static final long MAX_SIGNIFICANT_SAMPLES = 10;

  private boolean stopped;
  private List<CassetteTransportListener> listeners;
  private SimpleIIRFilter inputFilter;
  private long significantSamples;
  private File file;
  private long holdTime;
  private long timeOffs;
  private FileInputStream inputStream;
  private long filePos;
  private double volume;
  private double framesPerNanoSecond;
  private short previousValue;

  private FileStreamSampler() {
    throw new UnsupportedOperationException("unsupported constructor");
  }

  public FileStreamSampler(long wallClockTime,
                           File file, long holdTime) throws IOException {
    stopped = false;
    listeners = new ArrayList<CassetteTransportListener>();
    this.file = file;
    this.holdTime = holdTime;
    timeOffs = wallClockTime;
    inputStream = new FileInputStream(file);
    filePos = 0;
    volume = 1.0;
    framesPerNanoSecond = ((double)SAMPLE_RATE) * 0.000000001;
    previousValue = 0;
    inputFilter = new SimpleIIRFilter(INPUT_FILTER_ALPHA, Short.MAX_VALUE, 0.0);
    significantSamples = inputFilter.getSignificantSamples();
    System.out.printf("%s: start playing with #%d samples hold time%n",
                      getFileName(), significantSamples);
    if (significantSamples > MAX_SIGNIFICANT_SAMPLES) {
      throw new IllegalArgumentException("number of max significant samples too high; " +
                                         "please choose higher value for alpha");
    }
  }

  public String getFileName() {
    return file.getName();
  }

  public void addTransportListener(CassetteTransportListener listener) {
    listeners.add(listener);
  }

  private void stop() {
    stopped = true;
    for (CassetteTransportListener listener : listeners) {
      listener.cassetteStop();
    }
    inputFilter.resetInputValue(0.0);
  }

  public void setVolume(double volume) {
    if (volume < 0.0) {
      throw new IllegalArgumentException("volume < 0.0");
    }
    if (volume > 1.0) {
      throw new IllegalArgumentException("volume > 1.0");
    }
    this.volume = volume;
  }

  public short getValue(long wallClockTime) {
    computeValue(wallClockTime);
    long value = (long)inputFilter.getOutputValue();
    if (value < -32768) value = -32768;
    if (value > 32767) value = 32767;
    return (short)value;
  }

  private void computeValue(long wallClockTime) {
    if (stopped) {
      // no more data available => keep previous value
      return;
    }
    try {
      long time = wallClockTime - timeOffs;
      if (time < 0) {
        // Stream not yet started => keep initial value.
        return;
      }
      long nextFilePos =
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
      long nextFilterFilePos =
        nextFilePos - significantSamples * BYTES_PER_FRAME;
      long bytesToSkip = nextFilterFilePos - filePos;
      if (bytesToSkip > 0) {
        filePos += inputStream.skip(bytesToSkip);
      }
      if (nextFilterFilePos > filePos) {
        // This happens when the end of the input stream has been
        // reached.
        stop();
        return;
      }
      boolean eof = false;
      for (; (filePos < nextFilePos); filePos += 2) {
        if (inputStream.available() < 2) {
          eof = true;
          break;
        }
        short value = (short)((inputStream.read() & 0xff) |
                              ((inputStream.read() << 8) & 0xff00));
        inputFilter.addInputValue(value);
      }
      if (eof) {
        for (; (filePos < nextFilePos); filePos += 2) {
          inputFilter.addInputValue(0);
        }
        System.out.printf("%s: end of file reached%n", getFileName());
        stop();
      }
    } catch (IOException e) {
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
