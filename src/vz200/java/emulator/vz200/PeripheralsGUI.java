package emulator.vz200;

import java.awt.BorderLayout;
import java.io.File;
import java.io.IOException;
import javax.swing.JFrame;

public class PeripheralsGUI extends JFrame
  implements CassetteTransportListener // FIXME: do not chain
                                       // listeners
{
  private static final long serialVersionUID = 2686785121065338684L;

  private CassetteTransportControl transportControl;
  private CassetteStatusLine statusLine;

  public void addTransportListener(CassetteTransportListener listener) {
    transportControl.addListener(listener);
  }

  public void removeTransportListener(CassetteTransportListener listener) {
    transportControl.removeListener(listener);
  }

  public PeripheralsGUI() {
    super("Peripherals");
    setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    statusLine = new CassetteStatusLine();
    transportControl = new CassetteTransportControl(statusLine);
    add(transportControl, BorderLayout.PAGE_START);
    add(statusLine, BorderLayout.PAGE_END);
    pack();
    setVisible(true);
  }

  public void startPlaying(File file) throws IOException {
    transportControl.startPlaying(file);
  }

  public void startRecording(File file) throws IOException {
    transportControl.startRecording(file);
  }

  public void stop() {
    transportControl.stop();
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
