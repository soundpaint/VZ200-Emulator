package emulator.vz200;

import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Renders a single-channel audio stream to a raw audio file.
 * Encoding is 1 channel, 16 bits, signed PCM, little endian, 44100
 * Hz.
 */
public class FileStreamRenderer extends Thread {

  private static final int BUFFER_FRAMES = 0xc00;
  private static final int BYTES_PER_FRAME = 2;
  private static final float SAMPLE_RATE = 44100.0f;

  private String filePath;
  private byte[] buffer;
  private FileOutputStream out;
  private SignalEventSource eventSource;

  public FileStreamRenderer(String filePath) throws IOException {
    this.filePath = filePath;
    buffer = new byte[BYTES_PER_FRAME * BUFFER_FRAMES];
    try {
      out = new FileOutputStream(filePath);
    } catch (IOException e) {
      throw new IOException("failed opening file " + filePath, e);
    }
  }

  public void setEventSource(SignalEventSource eventSource) {
    this.eventSource = eventSource;
  }

  private void printMessage(String message) {
    System.out.printf("FileStreamRenderer: %s%n", message);
  }

  private int renderEvent(short sample, int bufferIndex,
                          int frameIndex, int nextFrameIndex) {
    byte sampleHi = (byte)(sample >> 8);
    byte sampleLo = (byte)(sample - sampleHi << 8);
    for (int i = frameIndex; i < nextFrameIndex; i++) {
      buffer[bufferIndex++] = sampleLo;
      buffer[bufferIndex++] = sampleHi;
    }
    return bufferIndex;
  }

  private void renderChannel(SignalEventSource eventSource,
                             int bufferOffset, SignalEventQueue.Event event,
                             int fullBufferTime, double bufferFramesPerTime) {
    if (eventSource == null) {
      renderEvent((short)0, bufferOffset, 0, BUFFER_FRAMES);
      return;
    }
    int bufferTime = 0;
    int frameIndex = 0;
    int bufferIndex = bufferOffset;
    while (frameIndex < BUFFER_FRAMES - 1) {
      eventSource.getEvent(event, fullBufferTime - bufferTime);
      bufferTime += event.timeSpan;
      int nextFrameIndex = (int)(bufferFramesPerTime * bufferTime + 0.5);
      if (nextFrameIndex > BUFFER_FRAMES) {
        printMessage("WARNING: rounding error: nextFrameIndex off by " +
                     (BUFFER_FRAMES - nextFrameIndex));
        nextFrameIndex = BUFFER_FRAMES;
      }
      bufferIndex =
        renderEvent(event.value, bufferIndex, frameIndex, nextFrameIndex);
      frameIndex = nextFrameIndex;
    }
  }

  private void render(int fullBufferTime, double bufferFramesPerTime) {
    SignalEventQueue.Event event = new SignalEventQueue.Event();
    while (true) {
      while (eventSource.getAvailableNanoSeconds() < fullBufferTime) {
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) {
          // ignore for now
        }
      }
      renderChannel(eventSource, 0, event, fullBufferTime, bufferFramesPerTime);
      try {
        out.write(buffer, 0, buffer.length);
      } catch (IOException e) {
        System.err.printf("file stream buffer overflow: %s%n", e);
      }
    }
  }

  public void run() {
    double nanoSampleRate = ((double)SAMPLE_RATE) * 0.000000001;
    double inv_fullBufferTime = nanoSampleRate / BUFFER_FRAMES;
    int fullBufferTime = (int)(1.0 / inv_fullBufferTime + 0.5);
    double bufferFramesPerTime = BUFFER_FRAMES * inv_fullBufferTime;
    printMessage("fullBufferTime=" + fullBufferTime);
    printMessage("writing to file " + filePath);
    render(fullBufferTime, bufferFramesPerTime);
  }

  protected void finalize() {
    if (out != null) {
      try {
        out.close();
      } catch (Throwable t) {
        printMessage("failed closing file: " + t);
      }
      out = null;
    }
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
