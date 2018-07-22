package emulator.vz200;

import java.io.IOException;

import emulator.z80.CPU;

public class IO implements CPU.Memory {
  private final static int DEFAULT_BASE_ADDRESS = 0x6800;

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
  }

  public Video getVideo() { return video; }

  private void setCassetteOutput(boolean active) {
    // TODO
  }

  private void setSpeakerOutput(int value) {
    // TODO
  }

  private boolean cassetteInputActive() {
    // TODO
    return false;
  }

  public boolean isValidAddr(int address) {
    return
      (address >= baseAddress) &&
      (address < baseAddress + 0x800);
  }

  public int readByte(int address) {
    int data = keyboard.readByte(address);
    if (cassetteInputActive())
      data |= 0x40;
    if (video.fs())
      data |= 0x80;
    return data;
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

  public void writeByte(int address, int value) {
    setCassetteOutput((value & 0x4) != 0x0);
    setSpeakerOutput(((value >> 5) & 0x1) - (value  & 0x1));
    video.setColorMode((value & 0x10) != 0x0);
    video.setDisplayMode((value & 0x20) != 0x0);
  }

  public void writeShort(int address, int value) {
    writeByte(address++, value & 0xff);
    writeByte(address, (value >> 8) & 0xff);
  }

  public void writeInt(int address, int value) {
    writeByte(address++, value & 0xff);
    writeByte(address++, (value >> 8) & 0xff);
    writeByte(address++, (value >> 16) & 0xff);
    writeByte(address, (value >> 24) & 0xff);
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
