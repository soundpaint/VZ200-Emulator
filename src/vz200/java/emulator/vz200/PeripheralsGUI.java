package emulator.vz200;

import java.awt.BorderLayout;
import java.io.File;
import java.io.IOException;
import javax.swing.JFrame;

public class PeripheralsGUI extends JFrame
  implements CassetteTransportListener,
             SpeakerControlListener // FIXME: do not chain listeners
{
  private static final long serialVersionUID = 2686785121065338684L;

  private final CassetteTransportControl transportControl;
  private final CassetteStatusLine statusLine;
  private final SpeakerControl speakerControl;
  private final Speaker speaker;

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

  public PeripheralsGUI(final Speaker speaker)
  {
    super("Peripherals");
    this.speaker = speaker;
    setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    statusLine = new CassetteStatusLine();
    transportControl = new CassetteTransportControl(statusLine);
    add(transportControl, BorderLayout.PAGE_START);
    add(statusLine, BorderLayout.PAGE_END);
    speakerControl = new SpeakerControl();
    speakerControl.addListener(this);
    add(speakerControl, BorderLayout.PAGE_END);
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

  public void setVolume(final double volume)
  {
    speaker.setVolume(volume);
  }

  public void setMuted(final boolean muted)
  {
    speaker.setMuted(muted);
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
