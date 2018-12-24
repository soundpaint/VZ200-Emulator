package emulator.vz200;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

public class MonoAudioStreamRenderer extends Thread
  implements LineControlListener
{
  private static final int BUFFER_FRAMES = 0xc00;
  private static final float SAMPLE_RATE = 44100.0f;
  private static final int SAMPLE_SIZE_IN_BITS = 16;
  private static final int CHANNELS = 1;
  private static final int FRAME_SIZE =
    SAMPLE_SIZE_IN_BITS * CHANNELS / 8; // bytes per frame
  private static final float FRAME_RATE = SAMPLE_RATE;
  private static final boolean BIG_ENDIAN = true;

  private final String id;
  private final byte[] buffer;
  private SourceDataLine sourceDataLine;
  private SignalEventSource signalEventSource;

  private MonoAudioStreamRenderer()
  {
    throw new UnsupportedOperationException("unsupported empty constructor");
  }

  public MonoAudioStreamRenderer(final String id)
  {
    if (id == null) throw new NullPointerException("id");
    this.id = id;
    buffer = new byte[FRAME_SIZE * BUFFER_FRAMES];
  }

  public SignalEventSource getSignalEventSource()
  {
    return signalEventSource;
  }

  public void setSignalEventSource(final SignalEventSource eventSource)
  {
    this.signalEventSource = eventSource;
  }

  private void printMessage(final String message)
  {
    System.out.printf("%s: %s%n", id, message);
  }

  public void lineChanged(final SourceDataLineChangeEvent event)
  {
    releaseSourceDataLine();
    final Mixer.Info mixerInfo = event.getMixerInfo();
    final Line.Info lineInfo = event.getLineInfo();
    if ((mixerInfo != null) && (lineInfo != null)) {
      final Mixer mixer = AudioSystem.getMixer(mixerInfo);
      try {
        sourceDataLine = (SourceDataLine)mixer.getLine(lineInfo);
      } catch (final LineUnavailableException e) {
        // TODO: Popup error dialog and disconnect speaker
        sourceDataLine = null;
      }
    } else {
      sourceDataLine = null;
    }
    if (sourceDataLine != null) {
      try {
        printMessage("using source data line: " + sourceDataLine.getClass());
        sourceDataLine.open(new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                                            SAMPLE_RATE, SAMPLE_SIZE_IN_BITS,
                                            CHANNELS, FRAME_SIZE,
                                            FRAME_RATE, BIG_ENDIAN));
        printMessage("using audio format " + sourceDataLine.getFormat());
        sourceDataLine.start();
      } catch (final LineUnavailableException e) {
        // TODO:
        // (1) Pop-up error message dialog explaining the problem.
        // (2) Envoke SwingUtilities.invokeLater()
        //     on SpeakerControl.changeLine(null, null);
        //     (or whatever else source caused this error)
        //     to disconnect the line.  Deferred invocation is
        //     required as current invocation of event handling
        //     first must be finished before.
      }
    } else {
      printMessage("no source data line available");
    }
  }

  public void volumeChanged(final double volume)
  {
    // ignore
  }

  public void mutedChanged(final boolean muted)
  {
    // ignore
  }

  private int renderEvent(final short sample, final int bufferIndexStart,
                          final int frameIndex, final int nextFrameIndex)
  {
    final byte sampleHi = (byte)(sample >> 8);
    final byte sampleLo = (byte)(sample - sampleHi << 8);
    int bufferIndex = bufferIndexStart;
    for (int i = frameIndex; i < nextFrameIndex; i++) {
      buffer[bufferIndex++] = sampleLo;
      buffer[bufferIndex++] = sampleHi;
    }
    return bufferIndex;
  }

  private void renderChannel(final SignalEventSource eventSource,
                             final SignalEventQueue.Event event,
                             final int fullBufferTime,
                             final double bufferFramesPerTime)
  {
    int bufferIndex = 0;
    int frameIndex = 0;
    if (eventSource == null) {
      renderEvent((short)0, bufferIndex, frameIndex, BUFFER_FRAMES);
      return;
    }
    int bufferTime = 0;
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
      renderChannel(signalEventSource, event,
                    fullBufferTime, bufferFramesPerTime);
      if (sourceDataLine != null) {
        // FIXME: Potential race condition when changing source data
        // line.
        sourceDataLine.write(buffer, 0, buffer.length);
      } else {
        try {
          Thread.sleep(100);
        } catch (final InterruptedException e) {
          // ignore
        }
      }
    }
  }

  @Override
  public void run()
  {
    final double nanoSampleRate = ((double)SAMPLE_RATE) * 0.000000001;
    final double inv_fullBufferTime = nanoSampleRate / BUFFER_FRAMES;
    final int fullBufferTime = (int)(1.0 / inv_fullBufferTime + 0.5);
    final double bufferFramesPerTime = BUFFER_FRAMES * inv_fullBufferTime;
    printMessage("fullBufferTime=" + fullBufferTime);
    renderLoop(fullBufferTime, bufferFramesPerTime);
  }

  private void releaseSourceDataLine()
  {
    if (sourceDataLine != null) {
      try {
        sourceDataLine.close();
      } catch (final Throwable t) {
        printMessage("failed closing audio: " + t);
      }
      sourceDataLine = null;
    }
  }

  @Override
  protected void finalize()
  {
    releaseSourceDataLine();
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
