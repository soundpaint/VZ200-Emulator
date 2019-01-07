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

  private final int baseAddress;
  private final KeyboardPanel panel;
  private final KeyboardMatrix matrix;

  @Override
  public int readByte(final int address, final long wallClockTime)
  {
    final int result;
    final int addressOffset = (address - baseAddress) & 0xffff;
    if (addressOffset < MEMORY_SIZE)
      result = matrix.read(addressOffset);
    else
      result = BYTE_UNDEFINED;
    return result;
  }

  @Override
  public int readShort(final int address, final long wallClockTime)
  {
    final int resultLSB, resultMSB;
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

  @Override
  public void resync(final long wallClockTime) {}

  private Keyboard() {
    throw new UnsupportedOperationException("unsupported empty constructor");
  }

  public Keyboard(final int baseAddress) throws IOException
  {
    super("VZ200 Keyboard");
    this.baseAddress = baseAddress;
    setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    addWindowListener(ApplicationExitListener.defaultInstance);
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

  public KeyListener getKeyListener()
  {
    return panel.getKeyListener();
  }

  @Override
  public void windowOpened(final WindowEvent event)
  {
    // nothing
  }

  @Override
  public void windowClosing(final WindowEvent event)
  {
    // nothing
  }

  @Override
  public void windowClosed(final WindowEvent event)
  {
    // nothing
  }

  @Override
  public void windowDeactivated(final WindowEvent event)
  {
    // nothing
  }

  @Override
  public void windowActivated(final WindowEvent event)
  {
    // nothing
  }

  @Override
  public void windowDeiconified(final WindowEvent event)
  {
    UserPreferences.getInstance().setKeyboardIconified(false);
  }

  @Override
  public void windowIconified(final WindowEvent event)
  {
    UserPreferences.getInstance().setKeyboardIconified(true);
  }

  public String toString()
  {
    return String.format("Keyboard[baseAddress=0x%04x, size=0x%04x]",
                         baseAddress, MEMORY_SIZE);
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
