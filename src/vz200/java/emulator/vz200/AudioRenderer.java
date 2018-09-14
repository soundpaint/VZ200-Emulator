package emulator.vz200;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

public class AudioRenderer extends Thread {

  private static final int BUFFER_FRAMES = 0x800;
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
    buffer = new byte[4 * BUFFER_FRAMES];
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
                                        SAMPLE_RATE, 16, 2, 4,
                                        SAMPLE_RATE, true));
  }

  private static boolean alwaysTrue() { return true; }

  public void run() {
    System.out.println("AudioRenderer: using audio format " +
                       sourceDataLine.getFormat());
    double nanoSampleRate = SAMPLE_RATE / 1000000000;
    Speaker.Event event = new Speaker.Event();
    sourceDataLine.start();
    while (alwaysTrue()) {
      int maxTime = (int)(BUFFER_FRAMES / nanoSampleRate + 0.5);
      int bIndex = 0;
      while (maxTime > 0) {
        speaker.getEvent(event, maxTime);
        int bNextIndex =
          bIndex + (int)(event.deltaWallClockTime * nanoSampleRate + 0.5);
        if (bNextIndex > BUFFER_FRAMES) {
          bNextIndex = BUFFER_FRAMES;
          // TODO: Avoid rounding errors
        }
        int address = bIndex << 2;
        for (int i = bIndex; i < bNextIndex; i++) {
          short sample = (short)(event.value * 10000);
          byte sampleHi = (byte)(sample >> 8);
          byte sampleLo = (byte)(sample - sampleHi << 8);
          // left channel
          buffer[address++] = sampleLo;
          buffer[address++] = sampleHi;
          // right channel
          buffer[address++] = sampleLo;
          buffer[address++] = sampleHi;
        }
        maxTime -= event.deltaWallClockTime;
        bIndex = bNextIndex;
      }
      sourceDataLine.write(buffer, 0, buffer.length);
    }
    sourceDataLine.drain();
    sourceDataLine.stop();
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
