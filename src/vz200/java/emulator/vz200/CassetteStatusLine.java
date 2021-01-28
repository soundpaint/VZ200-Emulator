package emulator.vz200;

import java.awt.Cursor;
import java.io.File;
import javax.swing.JProgressBar;

public class CassetteStatusLine extends JProgressBar
{
  private static final long serialVersionUID = 4748619637893425690L;
  private static final String STATUS_TEXT_STOPPED = "Stopped";
  private static final String STATUS_TEXT_PLAYING = "Playing";
  private static final String STATUS_TEXT_RECORDING = "Recording";

  public CassetteStatusLine()
  {
    super(0, 100);
    setStringPainted(true);
    stop();
    setProgress(0.0f);
  }

  private void showFileAction(final File file, final String action)
  {
    setString(action + " " + file.getName());
    setToolTipText(file.toString());
  }

  private void setProgress(final float progress)
  {
    final float clippedProgress =
      1.0f < progress ? 1.0f : (0.0f > progress ? 0.0f : progress);
    setValue((int)(100.0 * clippedProgress));
  }

  public void play(final CassetteInputSampler cassetteInputSampler)
  {
    showFileAction(cassetteInputSampler.getFile(), STATUS_TEXT_PLAYING);
    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    setProgress(0.0f);
    final Thread progressPoll = new Thread()
      {
        final ProgressProvider progressProvider = cassetteInputSampler;
        public void run()
        {
          while (true) {
            final float progress = progressProvider.getProgress();
            setProgress(progress);
            if (progress > 1.0) break;
            try {
              sleep(200);
            } catch (final InterruptedException e) {
              // ignore
            }
          }
          CassetteStatusLine.this.stop();
        }
      };
    progressPoll.start();
  }

  public void record(final File file)
  {
    showFileAction(file, STATUS_TEXT_RECORDING);
    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    setIndeterminate(true);
  }

  public void stop()
  {
    setString(STATUS_TEXT_STOPPED);
    setToolTipText(null);
    setCursor(null);
    setProgress(0.0f);
  }
}

/*
 * Local Variables:
 *   coding:utf-8
 *   mode:java
 * End:
 */
