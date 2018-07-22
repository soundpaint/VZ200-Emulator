package emulator.vz200;

import java.io.IOException;
import javax.swing.JPanel;
import javax.swing.JFrame;

import emulator.z80.CPU;

public class Video extends JFrame implements CPU.Memory {
  private final static int DEFAULT_BASE_ADDRESS = 0x7000;
  private VideoPanel panel;
  private CPU.Memory videoRAM;
  private int baseAddress;
  private long startTime;

  public boolean isValidAddr(int address) {
    return videoRAM.isValidAddr(address);
  }

  public int readByte(int address) {
    return videoRAM.readByte(address);
  }

  public int readShort(int address) {
    return videoRAM.readShort(address);
  }

  public int readInt(int address) {
    return videoRAM.readInt(address);
  }

  public void writeByte(int address, int value) {
    videoRAM.writeByte(address, value);
    address -= baseAddress;
    panel.invalidate(address);
  }

  public void writeShort(int address, int value) {
    videoRAM.writeShort(address, value);
    address -= baseAddress;
    panel.invalidate(address++);
    panel.invalidate(address++);
  }

  public void writeInt(int address, int value) {
    videoRAM.writeInt(address, value);
    address -= baseAddress;
    panel.invalidate(address++);
    panel.invalidate(address++);
    panel.invalidate(address++);
    panel.invalidate(address++);
  }

  public boolean fs() {
    long time = (System.nanoTime() - startTime) % 20000000;
    return time < 1000000;
  }

  public void setColorMode(boolean colorMode) {
    panel.setColorMode(colorMode);
  }

  public void setDisplayMode(boolean displayMode) {
    panel.setDisplayMode(displayMode);
  }

  public Video() throws IOException {
    this(DEFAULT_BASE_ADDRESS);
  }

  public Video(int baseAddress) throws IOException {
    super("VZ200 Video Screen");
    this.baseAddress = baseAddress;
    startTime = System.nanoTime();
    panel = new VideoPanel(baseAddress);
    getContentPane().add(panel);
    videoRAM = panel.getVideoRAM();
    pack();
    setVisible(true);
  }

  /**
   * This method is for testing and debugging only.
   */
  public static void main(String argv[]) throws IOException {
    new Video();
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
