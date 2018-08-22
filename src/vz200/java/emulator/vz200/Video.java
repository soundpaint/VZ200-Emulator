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

  private static final int DEFAULT_BASE_ADDRESS = 0x7000;

  // horizontal sync
  private static final long HS_CYCLE = 64000; // [ns]
  private static final long HS_CYCLE_LOW = 4800; // [ns]

  // field sync
  // FS_CYCLE must be a multiple of HS_CYCLE to keep FS and HS sync'd
  private static final long FS_CYCLE = 40960000; // [ns]
  private static final long FS_CYCLE_LOW = 2480000; // [ns]

  private VideoPanel panel;
  private RAMMemory videoRAM;
  private int baseAddress;
  private long wallClockTime;
  private long prevHsCycleLowStart;
  private long prevFsCycleLowStart;

  public int readByte(int address, long wallClockTime) {
    return videoRAM.readByte(address, wallClockTime);
  }

  public int readShort(int address, long wallClockTime) {
    return videoRAM.readShort(address, wallClockTime);
  }

  public void writeByte(int address, int value, long wallClockTime) {
    videoRAM.writeByte(address, value, wallClockTime);
    panel.invalidate(address);
  }

  public void writeShort(int address, int value, long wallClockTime) {
    videoRAM.writeShort(address, value, wallClockTime);
    panel.invalidate(address++);
    panel.invalidate(address);
  }

  public boolean hs() {
    return wallClockTime - prevHsCycleLowStart >= HS_CYCLE_LOW;
  }

  public boolean fs() {
    return wallClockTime - prevFsCycleLowStart >= FS_CYCLE_LOW;
  }

  public boolean updateWallClock(long wallClockCycles, long wallClockTime) {
    this.wallClockTime = wallClockTime;
    if (wallClockTime - prevHsCycleLowStart >= HS_CYCLE) {
      prevHsCycleLowStart += HS_CYCLE;
    }
    boolean doIrq;
    if (wallClockTime - prevFsCycleLowStart >= FS_CYCLE) {
      prevFsCycleLowStart += FS_CYCLE;
      doIrq = true;
    } else {
      doIrq = false;
    }
    return doIrq;
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
    wallClockTime = 0;
    prevHsCycleLowStart = 0;
    prevFsCycleLowStart = 0;
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
