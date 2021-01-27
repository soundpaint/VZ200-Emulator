package emulator.vz200;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JToolBar;

public class CassetteTransportControl extends Box
{
  private static final long serialVersionUID = 5283444125008636282L;

  private final List<CassetteTransportListener> listeners;
  private final CassetteFileChooser recordFileChooser, playFileChooser;
  private final CassetteStatusLine statusLine;
  private final JButton btnPlay, btnRecord, btnStop;
  private File file;

  public void addListener(final CassetteTransportListener listener)
  {
    listeners.add(listener);
  }

  public void removeListener(final CassetteTransportListener listener)
  {
    listeners.remove(listener);
  }

  private static JButton createToolButton(final String imageFileName,
                                          final String altText)
  {
    final ImageIcon icon = VZ200.createIcon(imageFileName, altText);
    final JButton button = new JButton();
    if (icon != null) {
      button.setIcon(icon);
    } else {
      button.setText(altText);
      System.err.printf("Warning: Resource not found: %s%n", imageFileName);
    }
    button.setToolTipText(altText);
    return button;
  }

  public CassetteTransportControl()
  {
    super(BoxLayout.Y_AXIS);
    setBorder(BorderFactory.createTitledBorder("Cassette Audio File Binding"));
    listeners = new ArrayList<CassetteTransportListener>();

    playFileChooser =
      new CassetteFileChooser("Receive Cassette Input from External Audio File",
                              "Start Playing", false, true, true);
    recordFileChooser =
      new CassetteFileChooser("Record Cassette Output to External Audio File",
                              "Start Recording", false, true, false);
    final JToolBar tbTransportControl = new JToolBar("Cassette Tape");
    add(tbTransportControl);
    btnPlay =
      createToolButton("play32x32.png",
                       "Start receiving cassette input from " +
                       "external audio file.");
    btnPlay.addActionListener((final ActionEvent event) -> { play(); });
    btnPlay.setEnabled(true);
    tbTransportControl.add(btnPlay);

    btnRecord =
      createToolButton("record32x32.png",
                       "Start recording cassette output to " +
                       "external audio file.");
    btnRecord.addActionListener((final ActionEvent event) -> { record(); });
    btnRecord.setEnabled(true);
    tbTransportControl.add(btnRecord);

    btnStop = createToolButton("stop32x32.png",
                               "Stop receiving from or recording to " +
                               "external audio file.");
    btnStop.addActionListener((final ActionEvent event) -> { stop(true); });
    btnStop.setEnabled(false);
    tbTransportControl.add(btnStop);
    tbTransportControl.add(new JToolBar.Separator(new Dimension(5, 32)));
    tbTransportControl.add(Box.createHorizontalGlue());

    statusLine = new CassetteStatusLine();
    add(statusLine);
  }

  private void play(final File file, final boolean notifyListeners)
  {
    boolean havePlayer = false;
    if (notifyListeners) {
      for (final CassetteTransportListener listener : listeners) {
        try {
          listener.cassetteStartPlaying(file);
          havePlayer = true;
        } catch (final IOException e) {
          JOptionPane.showMessageDialog(this, e.getMessage(), "IO Error",
                                        JOptionPane.WARNING_MESSAGE);
        }
      }
    }
    if (!notifyListeners || havePlayer) {
      btnPlay.setEnabled(false);
      btnRecord.setEnabled(false);
      btnStop.setEnabled(true);
      statusLine.play(file);
    }
  }

  private void play()
  {
    final int option = playFileChooser.showDialog(this, null);
    switch (option) {
    case JFileChooser.APPROVE_OPTION:
      file = playFileChooser.getSelectedFile();
      play(file, true);
      break;
    case JFileChooser.CANCEL_OPTION:
      break;
    case JFileChooser.ERROR_OPTION:
      break;
    default:
      break;
    }
  }

  private void record(final File file, final boolean notifyListeners)
  {
    boolean haveRecorder = false;
    if (notifyListeners) {
      for (CassetteTransportListener listener : listeners) {
        try {
          listener.cassetteStartRecording(file);
          haveRecorder = true;
        } catch (final IOException e) {
          JOptionPane.showMessageDialog(this, e.getMessage(), "IO Error",
                                        JOptionPane.WARNING_MESSAGE);
        }
      }
    }
    if (!notifyListeners || haveRecorder) {
      btnPlay.setEnabled(false);
      btnRecord.setEnabled(false);
      btnStop.setEnabled(true);
      statusLine.record(file);
    }
  }

  private void record()
  {
    final int option = recordFileChooser.showDialog(this, null);
    switch (option) {
    case JFileChooser.APPROVE_OPTION:
      file = recordFileChooser.getSelectedFile();
      if (file.exists()) {
        final int choice =
          JOptionPane.showConfirmDialog(this,
                                        "Overwrite " + file.getName() + "?",
                                        "Confirm Overwrite",
                                        JOptionPane.YES_NO_OPTION,
                                        JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
          return;
        }
      }
      record(file, true);
      break;
    case JFileChooser.CANCEL_OPTION:
      break;
    case JFileChooser.ERROR_OPTION:
      break;
    default:
      break;
    }
  }

  private void stop(final boolean notifyListeners)
  {
    btnPlay.setEnabled(true);
    btnRecord.setEnabled(true);
    btnStop.setEnabled(false);
    file = null;
    if (notifyListeners) {
      for (final CassetteTransportListener listener : listeners) {
        listener.cassetteStop();
      }
    }
    statusLine.stop();
  }

  public void startPlaying(final File file) throws IOException
  {
    play(file, false);
  }

  public void startRecording(final File file) throws IOException
  {
    record(file, false);
  }

  public void stop()
  {
    stop(false);
  }
}

/*
 * Local Variables:
 *   coding:utf-8
 *   mode:Java
 * End:
 */
