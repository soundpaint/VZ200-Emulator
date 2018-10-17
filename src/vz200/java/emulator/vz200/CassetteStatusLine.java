package emulator.vz200;

import java.awt.BorderLayout;
import java.io.File;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class CassetteStatusLine extends JPanel {
  private static final long serialVersionUID = 4748619637893425690L;
  private static final String STATUS_TEXT_STOPPED = "Stopped";
  private static final String STATUS_TEXT_PLAYING = "Playing";
  private static final String STATUS_TEXT_RECORDING = "Recording";

  private final JLabel statusLine;

  public CassetteStatusLine() {
    super(new BorderLayout());
    statusLine = new JLabel();
    add(statusLine, BorderLayout.WEST);
    stop();
  }

  private void showFileAction(File file, String action) {
    statusLine.setText(action + " " + file.getName());
    statusLine.setToolTipText(file.toString());
  }

  public void play(File file) {
    showFileAction(file, STATUS_TEXT_PLAYING);
  }

  public void record(File file) {
    showFileAction(file, STATUS_TEXT_RECORDING);
  }

  public void stop() {
    statusLine.setText(STATUS_TEXT_STOPPED);
  }
}

/*
 * Local Variables:
 *   coding:utf-8
 *   mode:java
 * End:
 */
