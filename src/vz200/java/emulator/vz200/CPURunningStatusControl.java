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

import emulator.z80.CPUControlAPI;
import emulator.z80.CPUControlAutomaton;
import emulator.z80.CPUControlAutomatonListener;

public class CPURunningStatusControl extends Box
  implements CPUControlAutomatonListener
{
  private static final long serialVersionUID = 5526690686086587536L;

  private final CPUControlAPI cpuControl;
  private final JButton btnStart, btnStop;

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

  public CPURunningStatusControl(final CPUControlAPI cpuControl)
  {
    super(BoxLayout.Y_AXIS);
    if (cpuControl == null) {
      throw new NullPointerException("cpuControl");
    }
    this.cpuControl = cpuControl;
    setBorder(BorderFactory.createTitledBorder("CPU Running Status Control"));
    final JToolBar tbControl = new JToolBar("CPU");
    add(tbControl);
    btnStart = createToolButton("play32x32.png", "Start CPU.");
    btnStart.addActionListener((final ActionEvent event) -> { start(); });
    btnStart.setEnabled(false);
    tbControl.add(btnStart);
    btnStop = createToolButton("stop32x32.png", "Stop CPU.");
    btnStop.addActionListener((final ActionEvent event) -> { stop(); });
    btnStop.setEnabled(false);
    tbControl.add(btnStop);
    tbControl.add(new JToolBar.Separator(new Dimension(5, 32)));
    tbControl.add(Box.createHorizontalGlue());
    cpuControl.addStateChangeListener(this);
  }

  @Override
  public void stateChanged(final CPUControlAutomaton.State state)
  {
    switch (state) {
    case STARTING:
      btnStart.setEnabled(false);
      break;
    case RUNNING:
      btnStop.setEnabled(true);
      break;
    case STOPPING:
      btnStop.setEnabled(false);
      break;
    case STOPPED:
      btnStart.setEnabled(true);
      break;
    default:
      throw new InternalError("unexpected case fall-through: state=" + state);
    }
  }

  private void start()
  {
    final Error error = cpuControl.start(false, false, true);
    if (error != null) {
      JOptionPane.showMessageDialog(this, error.getMessage());
    }
  }

  private void stop()
  {
    final Error error = cpuControl.stop(true);
    if (error != null) {
      JOptionPane.showMessageDialog(this, error.getMessage());
    }
  }
}

/*
 * Local Variables:
 *   coding:utf-8
 *   mode:Java
 * End:
 */
