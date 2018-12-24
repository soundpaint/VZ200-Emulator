package emulator.vz200;

import java.io.IOException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

public class AudioStreamRenderer extends Thread
{
  private static final int BUFFER_FRAMES = 0xc00;
  private static final float SAMPLE_RATE = 44100.0f;
  private static final int SAMPLE_SIZE_IN_BITS = 16;
  private static final int CHANNELS = 2;
  private static final int FRAME_SIZE =
    SAMPLE_SIZE_IN_BITS * CHANNELS / 8; // bytes per frame
  private static final float FRAME_RATE = SAMPLE_RATE;
  private static final boolean BIG_ENDIAN = true;

  private final byte[] buffer;
  private final Mixer.Info[] mixerInfo;
  private final Mixer mixer;
  private final Line.Info[] sourceLineInfo;
  private final SourceDataLine sourceDataLine;
  private SignalEventSource leftChannelSource, rightChannelSource;

  public AudioStreamRenderer() throws IOException
  {
    buffer = new byte[FRAME_SIZE * BUFFER_FRAMES];
    try {
      mixerInfo = AudioSystem.getMixerInfo();
      if (mixerInfo.length == 0) {
        throw new RuntimeException("no mixer found");
      }
      for (int i = 0; i < mixerInfo.length; i++) {
        printMessage(String.format("found mixer: %s", mixerInfo[i]));
      }
      mixer = AudioSystem.getMixer(mixerInfo[0]);
      printMessage("using mixer: " + mixer);
      sourceLineInfo = mixer.getSourceLineInfo();
      if (sourceLineInfo.length == 0) {
        throw new RuntimeException("no source line found in mixer " + mixer);
      }
      for (int i = 0; i < sourceLineInfo.length; i++) {
        printMessage("found source line info: " + sourceLineInfo[i]);
      }
      sourceDataLine = (SourceDataLine)mixer.getLine(sourceLineInfo[0]);
      printMessage("using source data line: " + sourceDataLine.getClass());
      sourceDataLine.open(new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                                          SAMPLE_RATE, SAMPLE_SIZE_IN_BITS,
                                          CHANNELS, FRAME_SIZE,
                                          FRAME_RATE, BIG_ENDIAN));
    } catch (final LineUnavailableException e) {
      throw new IOException("failed opening audio stream", e);
    }
  }

  public void setLeftChannelSource(final SignalEventSource eventSource)
  {
    this.leftChannelSource = eventSource;
  }

  public void setRightChannelSource(final SignalEventSource eventSource)
  {
    this.rightChannelSource = eventSource;
  }

  private void printMessage(final String message)
  {
    System.out.printf("AudioStreamRenderer: %s%n", message);
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
      bufferIndex += 2; // skip other channel
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
      renderChannel(leftChannelSource, 0, event,
                    fullBufferTime, bufferFramesPerTime);
      renderChannel(rightChannelSource, 2, event,
                    fullBufferTime, bufferFramesPerTime);
      sourceDataLine.write(buffer, 0, buffer.length);
    }
  }

  public void run()
  {
    final double nanoSampleRate = ((double)SAMPLE_RATE) * 0.000000001;
    final double inv_fullBufferTime = nanoSampleRate / BUFFER_FRAMES;
    final int fullBufferTime = (int)(1.0 / inv_fullBufferTime + 0.5);
    final double bufferFramesPerTime = BUFFER_FRAMES * inv_fullBufferTime;
    printMessage("fullBufferTime=" + fullBufferTime);
    printMessage("using audio format " + sourceDataLine.getFormat());
    sourceDataLine.start();
    renderLoop(fullBufferTime, bufferFramesPerTime);
  }

  protected void finalize()
  {
    if (sourceDataLine != null) {
      try {
        sourceDataLine.drain();
      } catch (final Throwable t) {
        printMessage("failed draining audio: " + t);
      }
      try {
        sourceDataLine.stop();
      } catch (final Throwable t) {
        printMessage("failed stopping audio: " + t);
      }
      try {
        sourceDataLine.close();
      } catch (final Throwable t) {
        printMessage("failed closing audio: " + t);
      }
    }
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
