package emulator.vz200;

import java.awt.BorderLayout;
import java.awt.event.KeyListener;
import java.io.IOException;
import javax.swing.JPanel;
import javax.swing.JFrame;

import emulator.z80.CPU;
import emulator.z80.MemoryBus;
import emulator.z80.Util;

public class Keyboard extends JFrame implements MemoryBus.Writer {
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
      result = MemoryBus.Writer.BYTE_UNDEFINED;
    return result;
  }

  public int readShort(int address, long wallClockTime) {
    int resultLSB, resultMSB;
    int addressOffset = (address - baseAddress) & 0xffff;
    if (addressOffset < MEMORY_SIZE)
      resultLSB = matrix.read(addressOffset);
    else
      resultLSB = MemoryBus.Writer.BYTE_UNDEFINED;
    addressOffset = (addressOffset + 1) & 0xffff;
    if (addressOffset < MEMORY_SIZE)
      resultMSB = matrix.read(addressOffset);
    else
      resultMSB = MemoryBus.Writer.BYTE_UNDEFINED;
    return (resultMSB << 8) | resultLSB;
  }

  private Keyboard() {}

  public Keyboard(int baseAddress) throws IOException {
    super("VZ200 Keyboard");
    this.baseAddress = baseAddress;
    matrix = new KeyboardMatrix();
    getContentPane().setLayout(new BorderLayout());
    panel = new KeyboardPanel(matrix);
    getContentPane().add(panel, BorderLayout.CENTER);
    pack();
    setVisible(true);
  }

  public KeyListener getKeyListener() {
    return panel.getKeyListener();
  }

  public String toString()
  {
    return "Keyboard[baseAddress=" + Util.hexShortStr(baseAddress) +
      ", size=" + Util.hexShortStr(MEMORY_SIZE) + "]";
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
