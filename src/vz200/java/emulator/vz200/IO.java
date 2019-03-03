package emulator.vz200;

import java.io.IOException;
import java.io.File;

import emulator.z80.CPUControlAPI;
import emulator.z80.MemoryBus;
import emulator.z80.Util;
import emulator.z80.WallClockProvider;

public class IO implements
                  WallClockProvider,
                  MemoryBus.BusReader, MemoryBus.BusWriter,
                  CassetteTransportListener
{
  private final static int DEFAULT_BASE_ADDRESS = 0x6800;
  private final static int MEMORY_SIZE = 0x0800;

  private final int baseAddress;
  private final Video video;
  private final Keyboard keyboard;
  private final SettingsGUI settingsGUI;
  private final Speaker speaker;
  private final CassetteOut cassetteOut;
  private final MonoAudioStreamRenderer speakerRenderer;
  private final MonoAudioStreamRenderer cassetteOutRenderer;
  private FileStreamRenderer fileStreamRenderer;
  private FileStreamSampler fileStreamSampler;
  private long timePerClockCycle;
  private long wallClockCycles;
  private long wallClockTime;

  private IO()
  {
    throw new UnsupportedOperationException("unsupported empty constructor");
  }

  public IO(final CPUControlAPI cpuControl, final long currentWallClockTime)
    throws IOException
  {
    this.baseAddress = DEFAULT_BASE_ADDRESS;
    keyboard = new Keyboard(baseAddress);
    video = new Video();
    video.addKeyListener(keyboard.getKeyListener());
    speakerRenderer = new MonoAudioStreamRenderer("speaker renderer");
    speaker = new Speaker(currentWallClockTime);
    speakerRenderer.setSignalEventSource(speaker);
    speakerRenderer.start();
    cassetteOutRenderer = new MonoAudioStreamRenderer("cassette out renderer");
    cassetteOut = new CassetteOut(currentWallClockTime);
    cassetteOutRenderer.setSignalEventSource(cassetteOut);
    cassetteOutRenderer.start();
    settingsGUI = new SettingsGUI(cpuControl, speaker, speakerRenderer,
                                  cassetteOut, cassetteOutRenderer,
                                  this);
    settingsGUI.addTransportListener(this);
  }

  public void resync(final long wallClockTime)
  {
    speaker.resync();
    cassetteOut.resync();
  }

  public Video getVideo()
  {
    return video;
  }

  private boolean cassetteInputActive(final long wallClockTime)
  {
    if (fileStreamSampler != null) {
      final short value = fileStreamSampler.getValue(wallClockTime);
      final boolean active = value > 0;
      return active;
    } else {
      return false;
    }
  }

  public int readByte(final int address, final long wallClockTime)
  {
    final int addressOffset = (address - baseAddress) & 0xffff;
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

  public int readShort(int address, final long wallClockTime)
  {
    return
      readByte(address++, wallClockTime) |
      (readByte(address, wallClockTime) << 8);
  }

  public void writeByte(final int address, final int value,
                        final long wallClockTime)
  {
    final int addressOffset = (address - baseAddress) & 0xffff;
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

  public void writeShort(int address, final int value,
                         final long wallClockTime)
  {
    writeByte(address++, value & 0xff, wallClockTime);
    writeByte(address, (value >> 8) & 0xff, wallClockTime);
  }

  public boolean updateWallClock(final long timePerClockCycle,
                                 final long wallClockCycles,
                                 final long wallClockTime)
  {
    this.timePerClockCycle = timePerClockCycle;
    this.wallClockCycles = wallClockCycles;
    this.wallClockTime = wallClockTime;
    return video.updateWallClock(wallClockCycles, wallClockTime);
  }

  public long getTimePerClockCycle()
  {
    return timePerClockCycle;
  }

  public long getWallClockCycles()
  {
    return wallClockCycles;
  }

  public long getWallClockTime()
  {
    return wallClockTime;
  }

  public void startPlaying(final File file) throws IOException
  {
    try {
      fileStreamSampler = new FileStreamSampler(wallClockTime, file, 0);

      // FIXME: Introduce central event dispatcher rather than
      // chaining listeners.
      fileStreamSampler.addTransportListener(settingsGUI);
    } catch (final Throwable t) {
      throw new IOException("WARNING: I/O: failed opening file stream: " +
                            t.getMessage() +
                            ".  No cassette input will be recognized.", t);
    }
  }

  public void startRecording(final File file) throws IOException
  {
    try {
      fileStreamRenderer = new FileStreamRenderer(file);
    } catch (final Throwable t) {
      throw new IOException("WARNING: I/O: failed opening file stream: " +
                            t.getMessage() +
                            ".  No audio output will be saved.", t);
    }
    fileStreamRenderer.setEventSource(cassetteOut);
    fileStreamRenderer.start();
  }

  public void stop()
  {
    if (fileStreamSampler != null) {
      System.out.printf("%s: aborted%n", fileStreamSampler.getFileName());
      fileStreamSampler = null;
    }
    if (fileStreamRenderer != null) {
      fileStreamRenderer = null;
    }
  }

  public String toString()
  {
    return "IO Memory[baseAddress=" + Util.hexShortStr(baseAddress) +
      ", size=" + Util.hexShortStr(MEMORY_SIZE) + "]";
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
