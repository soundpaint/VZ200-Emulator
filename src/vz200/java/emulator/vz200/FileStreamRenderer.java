package emulator.vz200;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Renders a single-channel audio stream to a raw audio file.
 * Encoding is 1 channel, 16 bits, signed PCM, little endian, 44100
 * Hz.
 */
public class FileStreamRenderer extends Thread implements AutoCloseable {

  private static final int BUFFER_FRAMES = 0xc00;
  private static final int BYTES_PER_FRAME = 2;
  private static final float SAMPLE_RATE = 44100.0f;

  private final File file;
  private final byte[] buffer;
  private FileOutputStream out;
  private SignalEventSource eventSource;

  private FileStreamRenderer()
  {
    throw new UnsupportedOperationException("unsupported empty constructor");
  }

  public FileStreamRenderer(final File file)
    throws IOException
  {
    super("FileStreamRenderer for file " + file);
    this.file = file;
    buffer = new byte[BYTES_PER_FRAME * BUFFER_FRAMES];
    try {
      out = new FileOutputStream(file);
    } catch (final IOException e) {
      throw new IOException("failed opening file " + file.getPath(), e);
    }
  }

  public void setEventSource(SignalEventSource eventSource)
  {
    this.eventSource = eventSource;
  }

  public String getFileName()
  {
    return file.getName();
  }

  private void printMessage(final String message)
  {
    System.out.printf("FileStreamRenderer (%s): %s%n", file, message);
  }

  private int renderEvent(final short sample, final int bufferStartIndex,
                          final int frameIndex, final int nextFrameIndex)
  {
    final byte sampleHi = (byte)(sample >>> 8);
    final byte sampleLo = (byte)(sample & 0xff);
    int bufferIndex = bufferStartIndex;
    for (int i = frameIndex; i < nextFrameIndex; i++) {
      buffer[bufferIndex++] = sampleLo;
      buffer[bufferIndex++] = sampleHi;
    }
    return bufferIndex;
  }

  private void renderChannel(final SignalEventSource eventSource,
                             final int bufferOffset,
                             final SignalEventQueue.Event event,
                             final int fullBufferTime,
                             final double bufferFramesPerTime)
  {
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

  private void renderLoop(final int fullBufferTime,
                          final double bufferFramesPerTime)
  {
    final SignalEventQueue.Event event = new SignalEventQueue.Event();
    while (true) {
      while (eventSource.getAvailableNanoSeconds() < fullBufferTime) {
        try {
          Thread.sleep(10);
        } catch (final InterruptedException e) {
          // ignore for now
        }
      }
      renderChannel(eventSource, 0, event, fullBufferTime, bufferFramesPerTime);
      try {
        out.write(buffer, 0, buffer.length);
      } catch (final IOException e) {
        printMessage(String.format("file stream buffer overflow: %s%n", e));
      }
    }
  }

  public void run()
  {
    final double nanoSampleRate = ((double)SAMPLE_RATE) * 0.000000001;
    final double inv_fullBufferTime = nanoSampleRate / BUFFER_FRAMES;
    final int fullBufferTime = (int)(1.0 / inv_fullBufferTime + 0.5);
    final double bufferFramesPerTime = BUFFER_FRAMES * inv_fullBufferTime;
    printMessage(String.format("fullBufferTime=%d", fullBufferTime));
    printMessage(String.format("writing to file %s", file.getName()));
    renderLoop(fullBufferTime, bufferFramesPerTime);
  }

  @Override
  public void close()
  {
    if (out != null) {
      printMessage(String.format("closing file %s", file.getName()));
      try {
        out.close();
      } catch (final Throwable t) {
        printMessage(String.format("failed closing file %s: %s",
                                   file.getName(), t));
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
