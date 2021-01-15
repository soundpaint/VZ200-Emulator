package emulator.vz200;

import java.io.File;
import java.io.IOException;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JTabbedPane;
import javax.swing.JFrame;

import emulator.z80.CPU;
import emulator.z80.CPUControlAPI;
import emulator.z80.WallClockProvider;

public class SettingsGUI extends JFrame
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

  public SettingsGUI()
  {
    throw new UnsupportedOperationException("unsupported constructor");
  }

  public SettingsGUI(final CPUControlAPI cpuControl,
                     final CPU cpu,
                     final LineControlListener speaker,
                     final MonoAudioStreamRenderer speakerRenderer,
                     final LineControlListener cassetteOut,
                     final MonoAudioStreamRenderer cassetteOutRenderer,
                     final WallClockProvider wallClockProvider)
  {
    super("Settings");
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
    addWindowListener(ApplicationExitListener.defaultInstance);

    final JTabbedPane tpSettings = new JTabbedPane();
    add(tpSettings);
    final SpeakerControl speakerControl =
      new SpeakerControl(speaker, speakerRenderer, wallClockProvider, this);
    tpSettings.addTab(null, Icons.LINE_UNMUTED,
                      speakerControl, "Configure Speaker Ouput");
    final CassetteControl cassetteControl =
      new CassetteControl(cassetteOut, cassetteOutRenderer, wallClockProvider,
                          this);
    transportControl = cassetteControl.getTransportControl();
    tpSettings.addTab(null, Icons.TAPE,
                      cassetteControl, "Configure Cassette I/O");
    final CPUControl cpuControlGUI = new CPUControl(cpuControl, cpu, this);
    tpSettings.addTab(null, Icons.CPU,
                      cpuControlGUI, "Configure CPU Settings");
    pack();
    setVisible(true);
  }

  @Override
  public void cassetteStartPlaying(final File file) throws IOException
  {
    transportControl.startPlaying(file);
  }

  @Override
  public void cassetteStartRecording(final File file) throws IOException
  {
    transportControl.startRecording(file);
  }

  @Override
  public void cassetteStop()
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
