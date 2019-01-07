package emulator.vz200;

import javax.sound.sampled.Mixer;
import javax.sound.sampled.Line;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;

import emulator.z80.WallClockProvider;

public class SpeakerControl extends JPanel
{
  private static final long serialVersionUID = -5660423523837741207L;

  private static class SpeakerPreferencesChangeListener
    implements LineControlListener
  {
    public void lineChanged(final SourceDataLineChangeEvent event)
    {
      final UserPreferences userPreferences = UserPreferences.getInstance();
      final Mixer.Info mixerInfo = event.getMixerInfo();
      if (mixerInfo != null) {
        userPreferences.setSpeakerMixer(mixerInfo.toString());
      }
      final Line.Info lineInfo = event.getLineInfo();
      if (lineInfo != null) {
        userPreferences.setSpeakerLine(lineInfo.toString());
      }
    }

    public void volumeChanged(final double volume)
    {
      UserPreferences.getInstance().setSpeakerVolume(volume);
    }

    public void mutedChanged(final boolean muted)
    {
      UserPreferences.getInstance().setSpeakerMuted(muted);
    }
  }

  public SpeakerControl(final LineControlListener speaker,
                        final MonoAudioStreamRenderer speakerRenderer,
                        final WallClockProvider wallClockProvider,
                        final JFrame owner)
  {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    final UserPreferences userPreferences = UserPreferences.getInstance();
    final String speakerId = "Speaker";
    final String speakerBorderTitle = "Speaker";
    final String speakerMixerInfoId = userPreferences.getSpeakerMixer();
    final String speakerLineInfoId = userPreferences.getSpeakerLine();
    final double speakerInitialVolume = userPreferences.getSpeakerVolume();
    final boolean speakerInitiallyMuted = userPreferences.getSpeakerMuted();
    final LineControl speakerLineControl =
      new LineControl(speakerId, speakerBorderTitle,
                      speakerMixerInfoId, speakerLineInfoId,
                      speakerInitialVolume, speakerInitiallyMuted,
                      wallClockProvider,
                      new SpeakerPreferencesChangeListener(),
                      owner);
    add(speakerLineControl);
    speakerLineControl.addListener(speaker);
    speakerLineControl.addListener(speakerRenderer);
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
