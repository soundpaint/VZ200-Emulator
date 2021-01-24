package emulator.vz200;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Control;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

/**
 * Renders a single-channel audio stream to an audio file.  Encoding
 * is 1 channel, 16 bits, signed PCM, little endian, 44100 Hz.
 */
public class FileStreamRenderer implements Runnable, TargetDataLine
{
  private static final int DEFAULT_BUFFER_FRAMES = 0xc00;
  private static final Control[] EMPTY_CONTROLS = new Control[0];
  private static final Line.Info LINE_INFO =
    new Line.Info(FileStreamRenderer.class);

  private final File file;
  private final SignalEventSource eventSource;
  private final int bufferFrames;
  private final int bytesPerFrame;
  private final float sampleRate;
  private final double nanoSampleRate;
  private final float inverseSampleRate;
  private final byte[] buffer;
  private final FileOutputStream out;
  private final List<LineListener> listeners;
  private long framePosition;
  private boolean running;
  private boolean closed;

  private FileStreamRenderer()
  {
    throw new UnsupportedOperationException("unsupported empty constructor");
  }

  public FileStreamRenderer(final File file,
                            final SignalEventSource eventSource)
    throws IOException
  {
    if (!AudioSystem.isFileTypeSupported(CassetteFileChooser.DEFAULT_FILE_TYPE))
      throw new IOException("no system support for .WAV files");
    this.file = file;
    this.eventSource = eventSource;
    bufferFrames = DEFAULT_BUFFER_FRAMES;
    bytesPerFrame = CassetteFileChooser.DEFAULT_BYTES_PER_FRAME;
    sampleRate = CassetteFileChooser.DEFAULT_SAMPLE_RATE;
    nanoSampleRate = ((double)sampleRate) * 0.000000001;
    inverseSampleRate = 1.0f / sampleRate;
    buffer = new byte[bytesPerFrame * bufferFrames];
    try {
      out = new FileOutputStream(file);
    } catch (final IOException e) {
      throw new IOException("failed opening file " + file.getPath(), e);
    }
    listeners = new ArrayList<LineListener>();
    running = false;
    closed = false;
    framePosition = 0;
  }

  @Override
  public void addLineListener(final LineListener listener)
  {
    listeners.add(listener);
  }

  @Override
  public void removeLineListener(final LineListener listener)
  {
    listeners.remove(listener);
  }

  private void emitLineEvent(final LineEvent.Type type)
  {
    final LineEvent event = new LineEvent(this, type, framePosition);
    for (final LineListener listener : listeners) {
      listener.update(event);
    }
  }

  @Override
  public Control getControl(final Control.Type control)
  {
    // currently, no controls supported
    throw new IllegalArgumentException("no controls supported");
  }

  @Override
  public Control[] getControls()
  {
    // currently, no controls supported
    return EMPTY_CONTROLS;
  }

  @Override
  public boolean isControlSupported(final Control.Type control)
  {
    // currently, no controls supported
    return false;
  }

  @Override
  public Line.Info getLineInfo()
  {
    return LINE_INFO;
  }

  @Override
  public boolean isOpen()
  {
    return !closed;
  }

  private int min(final int a, final int b)
  {
    return a < b ? a : b;
  }

  private int max(final int a, final int b)
  {
    return a > b ? a : b;
  }

  @Override
  public int available()
  {
    return
      max(0,
          min(getBufferSize(),
              bytesPerFrame *
              (int)(eventSource.getAvailableNanoSeconds() * 0.000000001 *
                    sampleRate)));
  }

  @Override
  public void drain()
  {
    if (closed) return;
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public void flush()
  {
    eventSource.resync();
  }

  @Override
  public int getBufferSize()
  {
    return bufferFrames * bytesPerFrame;
  }

  @Override
  public int getFramePosition()
  {
    return ((int)getLongFramePosition()) & 0x7fffffff;
  }

  @Override
  public long getLongFramePosition()
  {
    return framePosition;
  }

  @Override
  public long getMicrosecondPosition()
  {
    return (long)(inverseSampleRate * framePosition * 1000000);
  }

  @Override
  public float getLevel()
  {
    return 1.0f;
  }

  @Override
  public synchronized boolean isActive()
  {
    return running & (eventSource != null);
  }

  @Override
  public synchronized boolean isRunning()
  {
    return running;
  }

  @Override
  public synchronized void start()
  {
    if (!running) {
      running = true;
      emitLineEvent(LineEvent.Type.START);
    }
  }

  @Override
  public synchronized void stop()
  {
    if (running) {
      running = false;
      emitLineEvent(LineEvent.Type.STOP);
    }
  }

  @Override
  public AudioFormat getFormat()
  {
    return CassetteFileChooser.TOGGLE_BIT_AUDIO;
  }

  @Override
  public void open()
    throws LineUnavailableException
  {
    open(CassetteFileChooser.TOGGLE_BIT_AUDIO);
  }

  @Override
  public void open(final AudioFormat format)
    throws LineUnavailableException
  {
    open(format, bufferFrames * bytesPerFrame);
  }

  @Override
  public void open(final AudioFormat format, final int bufferSize)
    throws LineUnavailableException
  {
    if (format != CassetteFileChooser.TOGGLE_BIT_AUDIO) {
      throw new LineUnavailableException("expected toggle bit audio format, " +
                                         "but got: " + format);
    }
    framePosition = 0;
  }

  private int renderSample(final byte[] buffer,
                           final short sample,
                           final int bufferStartIndex,
                           final int sampleFrames)
  {
    final byte sampleHi = (byte)(sample >>> 8);
    final byte sampleLo = (byte)(sample & 0xff);
    int bufferIndex = bufferStartIndex;
    for (int i = 0; i < sampleFrames; i++) {
      buffer[bufferIndex++] = sampleLo;
      buffer[bufferIndex++] = sampleHi;
    }
    return bufferIndex;
  }

  private int read(final byte[] buffer,
                   final int offset,
                   final int length,
                   final SignalEventSource eventSource)
  {
    final SignalEventQueue.Event event = new SignalEventQueue.Event();
    final int maxFramesToRender = length / bytesPerFrame;
    final double invLength = nanoSampleRate / maxFramesToRender;
    final int lengthAsTimeSpan = (int)(1.0 / invLength + 0.5);
    final double bufferFramesPerTimeSpan = nanoSampleRate;
    int bufferIndex = offset;

    if (eventSource == null) {
      bufferIndex = renderSample(buffer, (short)0, bufferIndex,
                                 length / bytesPerFrame);
      return bufferIndex - offset;
    }

    int renderedTimeSpan = 0;
    int renderedFrames = 0;
    while (renderedFrames + bytesPerFrame <= maxFramesToRender) {
      eventSource.getEvent(event, lengthAsTimeSpan - renderedTimeSpan);
      renderedTimeSpan += event.timeSpan;
      int nextRenderedFrames =
        (int)(bufferFramesPerTimeSpan * renderedTimeSpan + 0.5);
      if (nextRenderedFrames > maxFramesToRender) {
        printMessage("WARNING: rounding error: nextRenderedFrames off by " +
                     (maxFramesToRender - nextRenderedFrames));
        nextRenderedFrames = maxFramesToRender;
      }
      bufferIndex =
        renderSample(buffer, event.value, bufferIndex,
                     nextRenderedFrames - renderedFrames);
      renderedFrames = nextRenderedFrames;
    }
    return bufferIndex - offset;
  }

  @Override
  public synchronized int read(final byte[] buffer,
                               final int offset,
                               final int length)
  {
    while ((available() < length) && !closed) {
      try {
        wait(100);
      } catch (final InterruptedException e) {
        // ignore
      }
    }
    if (closed) {
      return 0;
    }
    final int bytesRead = read(buffer, offset, length, eventSource);
    framePosition += bytesRead / bytesPerFrame;
    return bytesRead;
  }

  public String getFileName()
  {
    return file.getName();
  }

  private void printMessage(final String message)
  {
    System.out.printf("FileStreamRenderer (%s): %s%n", file, message);
  }

  @Override
  public void run()
  {
    final double nanoSampleRate = ((double)sampleRate) * 0.000000001;
    final double inv_fullBufferTime = nanoSampleRate / bufferFrames;
    final int fullBufferTime = (int)(1.0 / inv_fullBufferTime + 0.5);
    final double bufferFramesPerTime = bufferFrames * inv_fullBufferTime;
    printMessage(String.format("fullBufferTime=%d", fullBufferTime));
    printMessage(String.format("writing to file %s", file.getName()));
    try {
      AudioSystem.write(new AudioInputStream(this),
                        CassetteFileChooser.DEFAULT_FILE_TYPE, file);
    } catch (final IOException e) {
      printMessage(String.format("Warning: %s: %s", file.getName(),
                                 e.getMessage()));
    }
    stop();
    close();
  }

  @Override
  public synchronized void close()
  {
    closed = true;
    emitLineEvent(LineEvent.Type.CLOSE);
  }

  @Override
  public String toString()
  {
    return "FileStreamRenderer for file " + file;
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
