package emulator.vz200;

import java.io.File;
import java.io.IOException;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JTabbedPane;
import javax.swing.JFrame;

import emulator.z80.WallClockProvider;

public class PeripheralsGUI extends JFrame
  implements CassetteTransportListener
{
  private static final long serialVersionUID = 2686785121065338684L;

  private final CassetteTransportControl transportControl;

  public void addTransportListener(final CassetteTransportListener listener)
  {
    transportControl.addListener(listener);
  }

  public void removeTransportListener(final CassetteTransportListener listener)
  {
    transportControl.removeListener(listener);
  }

  public PeripheralsGUI()
  {
    throw new UnsupportedOperationException("unsupported constructor");
  }

  public PeripheralsGUI(final LineControlListener speaker,
                        final MonoAudioStreamRenderer speakerRenderer,
                        final LineControlListener cassetteOut,
                        final MonoAudioStreamRenderer cassetteOutRenderer,
                        final WallClockProvider wallClockProvider)
  {
    super("Peripherals");
    if (speaker == null) {
      throw new NullPointerException("speaker");
    }
    if (speakerRenderer == null) {
      throw new NullPointerException("speakerRenderer");
    }
    if (cassetteOut == null) {
      throw new NullPointerException("cassetteOut");
    }
    if (cassetteOutRenderer == null) {
      throw new NullPointerException("cassetteOutRenderer");
    }

    setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

    final JTabbedPane tpPeripherals = new JTabbedPane();
    add(tpPeripherals);
    final SpeakerControl speakerControl =
      new SpeakerControl(speaker, speakerRenderer, wallClockProvider, this);
    tpPeripherals.addTab(null, Icons.LINE_UNMUTED,
                         speakerControl, "Configure Speaker Ouput");
    final CassetteControl cassetteControl =
      new CassetteControl(cassetteOut, cassetteOutRenderer, wallClockProvider,
                          this);
    transportControl = cassetteControl.getTransportControl();
    tpPeripherals.addTab(null, Icons.TAPE,
                         cassetteControl, "Configure Cassette I/O");
    pack();
    setVisible(true);
  }

  public void startPlaying(final File file) throws IOException
  {
    transportControl.startPlaying(file);
  }

  public void startRecording(final File file) throws IOException
  {
    transportControl.startRecording(file);
  }

  public void stop()
  {
    transportControl.stop();
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
