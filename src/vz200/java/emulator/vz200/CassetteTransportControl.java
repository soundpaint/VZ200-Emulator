package emulator.vz200;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JToolBar;

public class CassetteTransportControl extends JToolBar {
  private static final long serialVersionUID = 5283444125008636282L;
  private static final String IMAGES_ROOT_PATH = ".";

  private List<CassetteTransportListener> listeners;
  private File file;
  private CassetteFileChooser recordFileChooser, playFileChooser;
  private CassetteStatusLine statusLine;
  private JButton btnPlay, btnRecord, btnStop;

  public void addListener(CassetteTransportListener listener) {
    listeners.add(listener);
  }

  public void removeListener(CassetteTransportListener listener) {
    listeners.remove(listener);
  }

  private static final ImageIcon ICON_ERROR =
    createIcon("record32x32.png", "Error");

  private static ImageIcon createIcon(final String imageFileName,
                                      final String altText) {
    final String imagePath = IMAGES_ROOT_PATH + "/" + imageFileName;
    final URL imageURL = VZ200.class.getResource(imagePath);
    if (imageURL != null) {
      return new ImageIcon(imageURL, altText);
    }
    return null;
  }

  private static JButton createToolButton(final String imageFileName,
                                          final String altText) {
    final ImageIcon icon = createIcon(imageFileName, altText);
    final JButton button = new JButton();
    if (icon != null) {
      button.setIcon(icon);
    } else {
      button.setText(altText);
      System.err.printf("Resource not found: %s%n", imageFileName);
    }
    button.setToolTipText(altText);
    return button;
  }

  private CassetteTransportControl() {
    throw new UnsupportedOperationException("unsupported constructor");
  }

  public CassetteTransportControl(CassetteStatusLine statusLine) {
    listeners = new ArrayList<CassetteTransportListener>();
    this.statusLine = statusLine;
    playFileChooser =
      new CassetteFileChooser("Provide Cassette Input from File",
                              "Start Playing");
    recordFileChooser =
      new CassetteFileChooser("Record Cassette Output to File",
                              "Start Recording");
    btnPlay =
      createToolButton("play32x32.png", "Start loading from cassette");
    btnPlay.addActionListener((final ActionEvent event) -> { play(); });
    btnPlay.setEnabled(true);
    add(btnPlay);

    btnRecord =
      createToolButton("record32x32.png", "Start saving to cassette");
    btnRecord.addActionListener((final ActionEvent event) -> { record(); });
    btnRecord.setEnabled(true);
    add(btnRecord);

    btnStop = createToolButton("stop32x32.png", "Stop loading or saving");
    btnStop.addActionListener((final ActionEvent event) -> { stop(true); });
    btnStop.setEnabled(false);
    add(btnStop);
  }

  private void play(File file, boolean notifyListeners) {
    boolean havePlayer = false;
    if (notifyListeners) {
      for (CassetteTransportListener listener : listeners) {
        try {
          listener.startPlaying(file);
          havePlayer = true;
        } catch (IOException e) {
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

  private void play() {
    int option = playFileChooser.showDialog(this, null);
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

  private void record(File file, boolean notifyListeners) {
    boolean haveRecorder = false;
    if (notifyListeners) {
      for (CassetteTransportListener listener : listeners) {
        try {
          listener.startRecording(file);
          haveRecorder = true;
        } catch (IOException e) {
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

  private void record() {
    int option = recordFileChooser.showDialog(this, null);
    switch (option) {
    case JFileChooser.APPROVE_OPTION:
      file = recordFileChooser.getSelectedFile();
      if (file.exists()) {
        int choice =
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

  private void stop(boolean notifyListeners) {
    btnPlay.setEnabled(true);
    btnRecord.setEnabled(true);
    btnStop.setEnabled(false);
    file = null;
    if (notifyListeners) {
      for (CassetteTransportListener listener : listeners) {
        listener.stop();
      }
    }
    statusLine.stop();
  }

  public void startPlaying(File file) throws IOException {
    play(file, false);
  }

  public void startRecording(File file) throws IOException {
    record(file, false);
  }

  public void stop() {
    stop(false);
  }
}

/*
 * Local Variables:
 *   coding:utf-8
 *   mode:Java
 * End:
 */
