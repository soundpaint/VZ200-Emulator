package emulator.vz200;

import java.awt.BorderLayout;
import java.awt.Color;
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
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JToolBar;

import emulator.z80.WallClockProvider;

public class CassetteTransportControl extends Box
{
  private static final long serialVersionUID = 5283444125008636282L;

  private final WallClockProvider wallClockProvider;
  private final List<CassetteTransportListener> listeners;
  private final CassetteFileChooser recordFileChooser, playFileChooser;
  private final CassetteStatusLine statusLine;
  private final JButton btnPlay, btnRecord, btnStop;

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

  public CassetteTransportControl(final WallClockProvider wallClockProvider)
  {
    super(BoxLayout.Y_AXIS);
    this.wallClockProvider = wallClockProvider;
    setBorder(BorderFactory.
              createTitledBorder("VZ200 Cassette I/O ↔ Host File"));
    listeners = new ArrayList<CassetteTransportListener>();

    playFileChooser =
      new CassetteFileChooser("Receive Cassette Input from Host File",
                              "Start Playing", false, true, true);
    final JLabel lbWarn =
      new JLabel("<html>Ensure to run a command " +
                 "like “CLOAD” or “CRUN” before starting playback!</html>");
    lbWarn.setForeground(new Color(0xffffdd));
    lbWarn.setBackground(new Color(0x000022));
    lbWarn.setOpaque(true);
    final JComponent chooserInnerComponent = (JComponent)
      ((BorderLayout)playFileChooser.getLayout()).
      getLayoutComponent(BorderLayout.NORTH);
    chooserInnerComponent.add(lbWarn, BorderLayout.NORTH);
    recordFileChooser =
      new CassetteFileChooser("Record Cassette Output to Host File",
                              "Start Recording", false, true, false);
    final JToolBar tbTransportControl = new JToolBar("Cassette Tape");
    add(tbTransportControl);
    btnPlay =
      createToolButton("play32x32.png",
                       "Start receiving cassette input from " +
                       "host file.");
    btnPlay.addActionListener((final ActionEvent event) -> { play(); });
    btnPlay.setEnabled(true);
    tbTransportControl.add(btnPlay);

    btnRecord =
      createToolButton("record32x32.png",
                       "Start recording cassette output to " +
                       "host file.");
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

  private void play(final CassetteInputSampler cassetteInputSampler,
                    final boolean notifyListeners)
  {
    boolean havePlayer = false;
    if (notifyListeners) {
      for (final CassetteTransportListener listener : listeners) {
        listener.cassetteStartPlaying(cassetteInputSampler);
        havePlayer = true;
      }
    }
    if (!notifyListeners || havePlayer) {
      btnPlay.setEnabled(false);
      btnRecord.setEnabled(false);
      btnStop.setEnabled(true);
      statusLine.play(cassetteInputSampler);
    }
  }

  private void play()
  {
    final int option = playFileChooser.showDialog(this, null);
    switch (option) {
    case JFileChooser.APPROVE_OPTION:
      final File file = playFileChooser.getSelectedFile();
      final CassetteInputSampler cassetteInputSampler;
      try {
        final UserPreferences userPreferences = UserPreferences.getInstance();
        final long startWallClockTime = wallClockProvider.getWallClockTime();
        final double speed = userPreferences.getCassetteInSpeed();
        if (file.getName().toLowerCase().endsWith(".vz")) {
          final boolean trimLeadIn =
            userPreferences.getCassetteInVzTrimLeadIn();
          cassetteInputSampler =
            new VZFileSampler(file, speed, trimLeadIn,
                              wallClockProvider, startWallClockTime);
        } else {
          final boolean invertPhase =
            userPreferences.getCassetteInInvertPhase();
          final double volume = userPreferences.getCassetteInVolume();
          final double dcOffset = userPreferences.getCassetteInDcOffset();
          cassetteInputSampler =
            new AudioFileSampler(file, invertPhase ? -volume : volume,
                                 speed, dcOffset,
                                 wallClockProvider, startWallClockTime);
        }
        play(cassetteInputSampler, true);
      } catch (final IOException e) {
        final String title = "Cassette Input Error";
        final String message =
          "I/O error: failed opening cassette input stream:\n" +
          e.getMessage() + ".\n" +
          "No cassette input will be recognized.";
        JOptionPane.showMessageDialog(this, message, title,
                                      JOptionPane.WARNING_MESSAGE);
      }
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
      final File file = recordFileChooser.getSelectedFile();
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
    if (notifyListeners) {
      for (final CassetteTransportListener listener : listeners) {
        listener.cassetteStop();
      }
    }
    statusLine.stop();
  }

  public void startPlaying(final CassetteInputSampler cassetteInputSampler)
  {
    play(cassetteInputSampler, false);
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
