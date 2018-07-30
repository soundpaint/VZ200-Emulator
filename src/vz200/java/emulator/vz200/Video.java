package emulator.vz200;

import java.io.IOException;
import javax.swing.JPanel;
import javax.swing.JFrame;

import emulator.z80.CPU;
import emulator.z80.MemoryBus;
import emulator.z80.RAMMemory;
import emulator.z80.Util;

public class Video extends JFrame
  implements MemoryBus.Reader, MemoryBus.Writer
{
  private static final long serialVersionUID = 8771293905230414438L;

  private final static int DEFAULT_BASE_ADDRESS = 0x7000;
  private VideoPanel panel;
  private RAMMemory videoRAM;
  private int baseAddress;
  private long startTime;

  public int readByte(int address) {
    return videoRAM.readByte(address);
  }

  public int readShort(int address) {
    return videoRAM.readShort(address);
  }

  public void writeByte(int address, int value) {
    videoRAM.writeByte(address, value);
    panel.invalidate(address);
  }

  public void writeShort(int address, int value) {
    videoRAM.writeShort(address, value);
    panel.invalidate(address++);
    panel.invalidate(address);
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

  public String toString()
  {
    return "Video[baseAddress=" + Util.hexShortStr(baseAddress) +
      ", videoRAM=" + videoRAM + "]";
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
