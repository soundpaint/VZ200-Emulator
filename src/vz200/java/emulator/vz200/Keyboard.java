package emulator.vz200;

import java.awt.BorderLayout;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import javax.swing.JFrame;

import emulator.z80.MemoryBus;
import emulator.z80.Util;

public class Keyboard extends JFrame
  implements WindowListener, MemoryBus.BusWriter
{
  private static final long serialVersionUID = -6642328202936155082L;

  private static final int MEMORY_SIZE = 0x0800;

  private int baseAddress;
  private KeyboardPanel panel;
  private KeyboardMatrix matrix;

  public int readByte(int address, long wallClockTime) {
    int result;
    int addressOffset = (address - baseAddress) & 0xffff;
    if (addressOffset < MEMORY_SIZE)
      result = matrix.read(addressOffset);
    else
      result = BYTE_UNDEFINED;
    return result;
  }

  public int readShort(int address, long wallClockTime) {
    int resultLSB, resultMSB;
    int addressOffset = (address - baseAddress) & 0xffff;
    if (addressOffset < MEMORY_SIZE)
      resultLSB = matrix.read(addressOffset);
    else
      resultLSB = BYTE_UNDEFINED;
    addressOffset = (addressOffset + 1) & 0xffff;
    if (addressOffset < MEMORY_SIZE)
      resultMSB = matrix.read(addressOffset);
    else
      resultMSB = BYTE_UNDEFINED;
    return (resultMSB << 8) | resultLSB;
  }

  public void resync(long wallClockTime) {}

  private Keyboard() {}

  public Keyboard(int baseAddress) throws IOException {
    super("VZ200 Keyboard");
    this.baseAddress = baseAddress;
    addWindowListener(this);
    matrix = new KeyboardMatrix();
    getContentPane().setLayout(new BorderLayout());
    panel = new KeyboardPanel(matrix);
    getContentPane().add(panel, BorderLayout.CENTER);
    pack();
    setVisible(true);
    if (UserPreferences.getInstance().getKeyboardIconified()) {
      setExtendedState(ICONIFIED);
    }
  }

  public KeyListener getKeyListener() {
    return panel.getKeyListener();
  }

  @Override
  public void windowOpened(WindowEvent event) {
    // nothing
  }

  @Override
  public void windowClosing(WindowEvent event) {
    // nothing
  }

  @Override
  public void windowClosed(WindowEvent event) {
    // nothing
  }

  @Override
  public void windowDeactivated(WindowEvent event) {
    // nothing
  }

  @Override
  public void windowActivated(WindowEvent event) {
    // nothing
  }

  @Override
  public void windowDeiconified(WindowEvent event) {
    UserPreferences.getInstance().setKeyboardIconified(false);
  }

  @Override
  public void windowIconified(WindowEvent event) {
    UserPreferences.getInstance().setKeyboardIconified(true);
  }

  public String toString()
  {
    return String.format("Keyboard[baseAddress=0x%04x, size=0x%04x]",
                         baseAddress, MEMORY_SIZE);
  }

  /**
   * This method is for testing and debugging only.
   */
  public static void main(String argv[]) throws IOException {
    new Keyboard();
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
