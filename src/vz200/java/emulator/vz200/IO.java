package emulator.vz200;

import java.io.IOException;

import emulator.z80.CPU;
import emulator.z80.MemoryBus;

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

  public int readByte(int address) {
    int addressOffset = (address - baseAddress) & 0xffff;
    int data;
    if (addressOffset < MEMORY_SIZE) {
      data = keyboard.readByte(address);
      if (cassetteInputActive())
        data |= 0x40;
      if (video.fs())
        data |= 0x80;
    } else {
      data = BYTE_UNDEFINED;
    }
    return data;
  }

  public int readShort(int address) {
    return
      readByte(address++) |
      (readByte(address) << 8);
  }

  public void writeByte(int address, int value) {
    int addressOffset = (address - baseAddress) & 0xffff;
    if (addressOffset < MEMORY_SIZE) {
      setCassetteOutput((value >> 1) & 0x3);
      setSpeakerOutput(((value >> 5) & 0x1) - (value  & 0x1));
      video.setDisplayMode((value & 0x08) != 0x0);
      video.setColorMode((value & 0x10) != 0x0);
    }
  }

  public void writeShort(int address, int value) {
    writeByte(address++, value & 0xff);
    writeByte(address, (value >> 8) & 0xff);
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
