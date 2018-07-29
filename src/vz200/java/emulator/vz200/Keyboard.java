package emulator.vz200;

import java.awt.BorderLayout;
import java.io.IOException;
import javax.swing.JPanel;
import javax.swing.JFrame;

import emulator.z80.CPU;

public class Keyboard extends JFrame implements CPU.Memory {
  private static final long serialVersionUID = -6642328202936155082L;

  private int baseAddress;
  private KeyboardPanel panel;
  private KeyboardMatrix matrix;

  public boolean isValidAddr(int address) {
    return
      (address >= baseAddress) &&
      (address < baseAddress + 0x800);
  }

  public int readByte(int address) {
    address -= baseAddress;
    return matrix.read(address);
  }

  public int readShort(int address) {
    return
      readByte(address++) |
      (readByte(address) << 8);
  }

  public int readInt(int address) {
    return
      readByte(address++) |
      (readByte(address++) << 8) |
      (readByte(address++) << 16) |
      (readByte(address) << 24);
  }

  public void writeByte(int address, int value) {}
  public void writeShort(int address, int value) {}
  public void writeInt(int address, int value) {}

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
