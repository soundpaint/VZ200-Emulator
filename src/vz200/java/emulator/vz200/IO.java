package emulator.vz200;

import java.io.IOException;

import emulator.z80.CPU;
import emulator.z80.MemoryBus;
import emulator.z80.Util;

public class IO implements MemoryBus.Reader, MemoryBus.Writer {
  private final static int DEFAULT_BASE_ADDRESS = 0x6800;
  private final static int MEMORY_SIZE = 0x0800;

  private int baseAddress;
  private Video video;
  private Keyboard keyboard;

  public IO() throws IOException {
    this(DEFAULT_BASE_ADDRESS);
  }

  public IO(int baseAddress) throws IOException {
    this.baseAddress = baseAddress;
    keyboard = new Keyboard(baseAddress);
    video = new Video();
    video.addKeyListener(keyboard.getKeyListener());
  }

  public Video getVideo() { return video; }

  private void setCassetteOutput(int value) {
    // TODO
  }

  private void setSpeakerOutput(int value) {
    // TODO
  }

  private boolean cassetteInputActive() {
    // TODO
    return false;
  }

  public int readByte(int address, long wallClockTime) {
    int addressOffset = (address - baseAddress) & 0xffff;
    int data;
    if (addressOffset < MEMORY_SIZE) {
      data = keyboard.readByte(address, wallClockTime);
      if (cassetteInputActive())
        data |= 0x40;
      if (video.hs())
        data |= 0x80;
    } else {
      data = BYTE_UNDEFINED;
    }
    return data;
  }

  public int readShort(int address, long wallClockTime) {
    return
      readByte(address++, wallClockTime) |
      (readByte(address, wallClockTime) << 8);
  }

  public void writeByte(int address, int value, long wallClockTime) {
    int addressOffset = (address - baseAddress) & 0xffff;
    if (addressOffset < MEMORY_SIZE) {
      setCassetteOutput((value >> 1) & 0x3);
      setSpeakerOutput(((value >> 5) & 0x1) - (value  & 0x1));
      video.setDisplayMode((value & 0x08) != 0x0);
      video.setColorMode((value & 0x10) != 0x0);
    }
  }

  public void writeShort(int address, int value, long wallClockTime) {
    writeByte(address++, value & 0xff, wallClockTime);
    writeByte(address, (value >> 8) & 0xff, wallClockTime);
  }

  public boolean updateWallClock(long wallClockCycles, long wallClockTime) {
    return video.updateWallClock(wallClockCycles, wallClockTime);
  }

  public String toString()
  {
    return "IO Memory[baseAddress=" + Util.hexShortStr(baseAddress) +
      ", size=" + Util.hexShortStr(MEMORY_SIZE) + "]";
  }

  /**
   * This method is for testing and debugging only.
   */
  public static void main(String argv[]) throws IOException {
    new IO();
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
