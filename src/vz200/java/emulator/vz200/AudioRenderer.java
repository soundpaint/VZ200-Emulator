package emulator.vz200;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

public class AudioRenderer extends Thread {

  private static final int BUFFER_FRAMES = 0x1000;
  private static final int BYTES_PER_FRAME = 4;
  private static final float SAMPLE_RATE = 44100.0f;

  private Speaker speaker;
  private Mixer.Info[] mixerInfo;
  private Mixer mixer;
  private Line.Info[] sourceLineInfo;
  private SourceDataLine sourceDataLine;
  private byte[] buffer;

  private AudioRenderer() {}

  public AudioRenderer(Speaker speaker) throws LineUnavailableException {
    this.speaker = speaker;
    buffer = new byte[BYTES_PER_FRAME * BUFFER_FRAMES];
    selectSourceDataLine();
  }

  private void selectSourceDataLine() throws LineUnavailableException {
    mixerInfo = AudioSystem.getMixerInfo();
    if (mixerInfo.length == 0) {
      throw new RuntimeException("no mixer found");
    }
    for (int i = 0; i < mixerInfo.length; i++) {
      System.out.println("found mixer: " + mixerInfo[i]);
    }
    mixer = AudioSystem.getMixer(mixerInfo[0]);
    System.out.println("using mixer: " + mixer);
    sourceLineInfo = mixer.getSourceLineInfo();
    if (sourceLineInfo.length == 0) {
      throw new RuntimeException("no source line found in mixer " + mixer);
    }
    for (int i = 0; i < sourceLineInfo.length; i++) {
      System.out.println("found source line info: " + sourceLineInfo[i]);
    }
    sourceDataLine = (SourceDataLine)mixer.getLine(sourceLineInfo[0]);
    System.out.println("using source data line: " + sourceDataLine.getClass());
    sourceDataLine.open(new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                                        SAMPLE_RATE, 16, 2, BYTES_PER_FRAME,
                                        SAMPLE_RATE, true));
  }

  public void run() {
    System.out.println("AudioRenderer: using audio format " +
                       sourceDataLine.getFormat());
    double nanoSampleRate = ((double)SAMPLE_RATE) * 0.000000001;
    double inv_fullBufferTime = nanoSampleRate / BUFFER_FRAMES;
    int fullBufferTime = (int)(1.0 / inv_fullBufferTime + 0.5);
    System.out.println("AudioRenderer: fullBufferTime=" + fullBufferTime);
    Speaker.Event event = new Speaker.Event();
    sourceDataLine.start();
    double bufferFramesPerTime = BUFFER_FRAMES * inv_fullBufferTime;
    while (true) {
      int bufferTime = 0;
      int frameIndex = 0;
      int bufferIndex = 0;
      while (frameIndex < BUFFER_FRAMES - 1) {
        speaker.getEvent(event, fullBufferTime - bufferTime);
        bufferTime += event.timeSpan;
        int nextFrameIndex = (int)(bufferFramesPerTime * bufferTime + 0.5);
        if (nextFrameIndex > BUFFER_FRAMES) {
          System.out.println("WARNING: rounding error: nextFrameIndex off by " +
                             (BUFFER_FRAMES - nextFrameIndex));
          nextFrameIndex = BUFFER_FRAMES;
        }
        short sample = event.elongation;
        byte sampleHi = (byte)(sample >> 8);
        byte sampleLo = (byte)(sample - sampleHi << 8);
        for (int i = frameIndex; i < nextFrameIndex; i++) {
          // left channel
          buffer[bufferIndex++] = sampleLo;
          buffer[bufferIndex++] = sampleHi;
          // right channel
          buffer[bufferIndex++] = sampleLo;
          buffer[bufferIndex++] = sampleHi;
        }
        frameIndex = nextFrameIndex;
      }
      sourceDataLine.write(buffer, 0, buffer.length);
    }
  }

  protected void finalize() {
    if (sourceDataLine != null) {
      try {
        sourceDataLine.drain();
      } catch (Throwable t) {
        System.out.println("AudioRenderer: Failed draining audio: " + t);
      }
      try {
        sourceDataLine.stop();
      } catch (Throwable t) {
        System.out.println("AudioRenderer: Failed stopping audio: " + t);
      }
      try {
        sourceDataLine.close();
      } catch (Throwable t) {
        System.out.println("AudioRenderer: Failed closing audio: " + t);
      }
      sourceDataLine = null;
    }
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
