package emulator.vz200;

import java.awt.Component;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import javax.swing.JOptionPane;

public class ApplicationExitListener implements WindowListener
{
  private static final String MESSAGE = "Exit application?";
  private static final String TITLE = "Confirm Application Exit";

  public static final ApplicationExitListener defaultInstance =
    new ApplicationExitListener();

  @Override
  public void windowActivated(final WindowEvent e) {}

  @Override
  public void windowClosed(final WindowEvent e) {}

  @Override
  public void windowClosing(final WindowEvent e) {
    final int result =
      JOptionPane.showConfirmDialog((Component)e.getSource(),
                                    MESSAGE,
                                    TITLE,
                                    JOptionPane.OK_CANCEL_OPTION,
                                    JOptionPane.QUESTION_MESSAGE);
    if (result == JOptionPane.OK_OPTION) {
      System.exit(0);
    }
  }

  @Override
  public void windowDeactivated(final WindowEvent e) {}

  @Override
  public void windowDeiconified(final WindowEvent e) {}

  @Override
  public void windowIconified(final WindowEvent e) {}

  @Override
  public void windowOpened(final WindowEvent e) {}
}

/*
 * Local Variables:
 *   coding:utf-8
 *   mode:Java
 * End:
 */
