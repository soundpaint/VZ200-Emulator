package emulator.vz200;

import java.io.IOException;

import emulator.z80.MemoryBus;
import emulator.z80.Util;

public class IO implements MemoryBus.BusReader, MemoryBus.BusWriter {
  private final static int DEFAULT_BASE_ADDRESS = 0x6800;
  private final static int MEMORY_SIZE = 0x0800;

  private int baseAddress;
  private Video video;
  private Keyboard keyboard;
  private Speaker speaker;
  private CassetteOut cassetteOut;
  private AudioStreamRenderer audioStreamRenderer;
  private FileStreamRenderer fileStreamRenderer;
  private FileStreamSampler fileStreamSampler;

  private IO() { throw new UnsupportedOperationException(); }

  public IO(long currentWallClockTime) throws IOException {
    this(currentWallClockTime, DEFAULT_BASE_ADDRESS);
  }

  public IO(long currentWallClockTime, int baseAddress) throws IOException {
    this.baseAddress = baseAddress;
    keyboard = new Keyboard(baseAddress);
    video = new Video();
    video.addKeyListener(keyboard.getKeyListener());
    try {
      audioStreamRenderer = new AudioStreamRenderer();
    } catch (Throwable t) {
      System.err.println("WARNING: IO: failed opening audio stream.  " +
                         "No audio output will be produced.");
    }
    try {
      fileStreamRenderer = new FileStreamRenderer("cassette.out.raw");
    } catch (Throwable t) {
      System.err.println("WARNING: IO: failed opening file stream.  " +
                         "No audio output will be saved.");
    }
    try {
      fileStreamSampler = new FileStreamSampler("cassette.in.raw", 0);
    } catch (Throwable t) {
      System.err.println("WARNING: IO: failed opening file stream.  " +
                         "No cassette input will be recognized.");
    }
    if (audioStreamRenderer != null) {
      speaker = new Speaker(currentWallClockTime);
      cassetteOut = new CassetteOut(currentWallClockTime);
      audioStreamRenderer.setLeftChannelSource(speaker);
      //audioStreamRenderer.setRightChannelSource(cassetteOut);
      audioStreamRenderer.start();
      fileStreamRenderer.setEventSource(cassetteOut);
      fileStreamRenderer.start();
    }
  }

  public void resync(long wallClockTime) {
    if (speaker != null) {
      speaker.resync();
    }
    if (cassetteOut != null) {
      cassetteOut.resync();
    }
  }

  public Video getVideo() { return video; }

  private boolean cassetteInputActive(long wallClockTime) {
    if (fileStreamSampler != null) {
      short value = fileStreamSampler.getValue(wallClockTime);
      boolean active = value > 0;
      return active;
    } else {
      return false;
    }
  }

  public int readByte(int address, long wallClockTime) {
    int addressOffset = (address - baseAddress) & 0xffff;
    int data;
    if (addressOffset < MEMORY_SIZE) {
      data = keyboard.readByte(address, wallClockTime);
      if (cassetteInputActive(wallClockTime))
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
      if (speaker != null) {
        speaker.putEvent((value >> 5) & 0x1, value  & 0x1, wallClockTime);
      }
      if (cassetteOut != null) {
        cassetteOut.putEvent((value >> 1) & 0x3, wallClockTime);
      }
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
