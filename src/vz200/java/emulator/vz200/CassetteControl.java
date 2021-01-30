package emulator.vz200;

import javax.sound.sampled.Mixer;
import javax.sound.sampled.Line;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import emulator.z80.WallClockProvider;

public class CassetteControl extends JPanel
{
  private static final long serialVersionUID = 7805962032130043825L;

  private static class CassetteOutPreferencesChangeListener
    implements LineControlListener
  {
    public void lineChanged(final SourceDataLineChangeEvent event)
    {
      final UserPreferences userPreferences = UserPreferences.getInstance();
      final Mixer.Info mixerInfo = event.getMixerInfo();
      if (mixerInfo != null) {
        userPreferences.setCassetteOutMixer(mixerInfo.toString());
      }
      final Line.Info lineInfo = event.getLineInfo();
      if (lineInfo != null) {
        userPreferences.setCassetteOutLine(lineInfo.toString());
      }
    }

    public void volumeChanged(final double volume)
    {
      UserPreferences.getInstance().setCassetteOutVolume(volume);
    }

    public void mutedChanged(final boolean muted)
    {
      UserPreferences.getInstance().setCassetteOutMuted(muted);
    }
  }

  private final CassetteTransportControl transportControl;

  public CassetteControl(final LineControlListener cassetteOut,
                         final MonoAudioStreamRenderer cassetteOutRenderer,
                         final WallClockProvider wallClockProvider,
                         final JFrame owner)
  {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    final UserPreferences userPreferences = UserPreferences.getInstance();

    final JTabbedPane tpSettings = new JTabbedPane();
    add(tpSettings);

    final JPanel cassetteInControl = new JPanel();
    cassetteInControl.setLayout(new BoxLayout(cassetteInControl,
                                              BoxLayout.Y_AXIS));
    tpSettings.addTab("Host File → Cass In", null,
                      cassetteInControl, "Configure Cassette Input");
    final CommonFileSampleControl commonFileSampleControl =
      new CommonFileSampleControl();
    cassetteInControl.add(commonFileSampleControl);

    final AudioFileSampleControl audioFileSampleControl =
      new AudioFileSampleControl();
    cassetteInControl.add(audioFileSampleControl);

    final VZFileSampleControl vzFileSampleControl =
      new VZFileSampleControl();
    cassetteInControl.add(vzFileSampleControl);


    final JPanel cassetteOutControl = new JPanel();
    cassetteOutControl.setLayout(new BoxLayout(cassetteOutControl,
                                               BoxLayout.Y_AXIS));
    tpSettings.addTab("Cass Out → Host File", null,
                      cassetteOutControl, "Configure Cassette Output");

    final String cassOutId = "Cassette Out";
    final String cassOutBorderTitle = "Common";
    final String cassOutMixerInfoId = userPreferences.getCassetteOutMixer();
    final String cassOutLineInfoId = userPreferences.getCassetteOutLine();
    final double cassOutInitialVolume = userPreferences.getCassetteOutVolume();
    final boolean cassOutInitiallyMuted = userPreferences.getCassetteOutMuted();
    final LineControl cassOutLineControl =
      LineControl.create(cassOutId, cassOutBorderTitle,
                         cassOutMixerInfoId, cassOutLineInfoId,
                         cassOutInitialVolume, cassOutInitiallyMuted,
                         wallClockProvider,
                         new CassetteOutPreferencesChangeListener(),
                         owner);
    cassOutLineControl.addListener(cassetteOut);
    cassOutLineControl.addListener(cassetteOutRenderer);
    cassetteOutControl.add(cassOutLineControl);

    transportControl = new CassetteTransportControl(wallClockProvider);
    add(transportControl);
  }

  public CassetteTransportControl getTransportControl()
  {
    return transportControl;
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
