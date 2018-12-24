package emulator.vz200;

import java.io.File;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class CassetteStatusLine extends JPanel
{
  private static final long serialVersionUID = 4748619637893425690L;
  private static final String STATUS_TEXT_STOPPED = "Stopped";
  private static final String STATUS_TEXT_PLAYING = "Playing";
  private static final String STATUS_TEXT_RECORDING = "Recording";

  private final JLabel statusLine;

  public CassetteStatusLine()
  {
    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
    statusLine = new JLabel();
    add(statusLine);
    stop();
    add(Box.createHorizontalGlue());
    statusLine.setMaximumSize(statusLine.getPreferredSize());
  }

  private void showFileAction(final File file, final String action)
  {
    statusLine.setText(action + " " + file.getName());
    statusLine.setToolTipText(file.toString());
  }

  public void play(final File file)
  {
    showFileAction(file, STATUS_TEXT_PLAYING);
  }

  public void record(final File file)
  {
    showFileAction(file, STATUS_TEXT_RECORDING);
  }

  public void stop()
  {
    statusLine.setText(STATUS_TEXT_STOPPED);
  }
}

/*
 * Local Variables:
 *   coding:utf-8
 *   mode:java
 * End:
 */
