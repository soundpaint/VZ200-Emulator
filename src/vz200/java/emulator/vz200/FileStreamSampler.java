package emulator.vz200;

import java.io.FileInputStream;
import java.io.IOException;

public class FileStreamSampler {
  private static final int BYTES_PER_FRAME = 2;
  private static final float SAMPLE_RATE = 44100.0f;
  private static final double INPUT_FILTER_ALPHA = 0.9;
  private static final long MAX_SIGNIFICANT_SAMPLES = 10;

  private SimpleIIRFilter inputFilter;
  private long significantSamples;
  private String filePath;
  private long holdTime;
  private long timeOffs;
  private FileInputStream in;
  private long filePos;
  private double volume;
  private double framesPerNanoSecond;
  private short previousValue;

  private FileStreamSampler() {
    throw new UnsupportedOperationException("unsupported constructor");
  }

  public FileStreamSampler(String filePath, long holdTime) throws IOException {
    this.filePath = filePath;
    this.holdTime = holdTime;
    timeOffs = 20000000000L; // 20s delay
    in = new FileInputStream(filePath);
    filePos = 0;
    volume = 1.0;
    framesPerNanoSecond = ((double)SAMPLE_RATE) * 0.000000001;
    previousValue = 0;
    inputFilter = new SimpleIIRFilter(INPUT_FILTER_ALPHA, Short.MAX_VALUE, 0.0);
    significantSamples = inputFilter.getSignificantSamples();
    System.out.printf("%s: hold time [#samples]: %d%n",
                      filePath, significantSamples);
    if (significantSamples > MAX_SIGNIFICANT_SAMPLES) {
      throw new IllegalArgumentException("number of max significant samples too high; " +
                                         "please choose higher value for alpha");
    }
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
        filePos += in.skip(bytesToSkip);
      }
      if (nextFilterFilePos > filePos) {
        // This happens when the end of the input stream has been
        // reached.  In this case, perform a trivial extrapolation by
        // keeping the previous value.
        return;
      }
      int sCount = 0;
      for (; (filePos < nextFilePos) && (in.available() >= 2); filePos += 2) {
        short value = (short)((in.read() & 0xff) | ((in.read() << 8) & 0xff00));
        inputFilter.addInputValue(value);
        sCount++;
      }
      if (filePos < nextFilePos) {
        System.out.println("WARNING: buffer underrun");
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
