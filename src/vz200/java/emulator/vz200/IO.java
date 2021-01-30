package emulator.vz200;

import java.io.IOException;
import java.io.File;

import emulator.z80.CPU;
import emulator.z80.CPUControl;
import emulator.z80.MemoryBus;
import emulator.z80.Util;
import emulator.z80.WallClockProvider;

public class IO implements
                  CPU.WallClockListener,
                  WallClockProvider,
                  MemoryBus.BusReader, MemoryBus.BusWriter,
                  CassetteTransportListener
{
  private final static int DEFAULT_BASE_ADDRESS = 0x6800;
  private final static int MEMORY_SIZE = 0x0800;

  private final static CassetteInputSampler CLOSED_INPUT_SAMPLER =
    new CassetteInputSampler() {
      public float getProgress() { return 2.0f; }
      public short getValue(final long wallClockTime)
      {
        System.out.printf("WARNING: %s: EOF%n", this);
        return VALUE_LO;
      }
      public void stop()
      {
        System.out.printf("WARNING: %s: already stopped%n", this);
      }
      public boolean isStopped() { return true; }
      public File getFile() { return null; }
    };

  private final CPUControl cpuControl;
  private final int baseAddress;
  private final Video video;
  private final Keyboard keyboard;
  private final SettingsGUI settingsGUI;
  private final Speaker speaker;
  private final CassetteCtrlRoomOut cassetteCtrlRoomOut;
  private final CassetteFileOut cassetteFileOut;
  private final MonoAudioStreamRenderer speakerRenderer;
  private final MonoAudioStreamRenderer cassetteCtrlRoomOutRenderer;
  private FileStreamRenderer fileStreamRenderer;
  private CassetteInputSampler cassetteInputSampler;
  private long timePerClockCycle;
  private long wallClockCycles;
  private long wallClockTime;

  private IO()
  {
    throw new UnsupportedOperationException("unsupported empty constructor");
  }

  public IO(final CPUControl cpuControl, final CPU cpu,
            final long currentWallClockTime)
    throws IOException
  {
    this.cpuControl = cpuControl;
    this.baseAddress = DEFAULT_BASE_ADDRESS;
    keyboard = new Keyboard(baseAddress);
    video = new Video();
    video.addKeyListener(keyboard.getKeyListener());
    speakerRenderer = new MonoAudioStreamRenderer("speaker renderer");
    speaker = new Speaker(this);
    speakerRenderer.setSignalEventSource(speaker);
    speakerRenderer.start();
    cassetteCtrlRoomOutRenderer =
      new MonoAudioStreamRenderer("cassette out renderer");
    cassetteCtrlRoomOut = new CassetteCtrlRoomOut(this);
    cassetteCtrlRoomOutRenderer.setSignalEventSource(cassetteCtrlRoomOut);
    cassetteCtrlRoomOutRenderer.start();
    cassetteFileOut = new CassetteFileOut(this);
    settingsGUI = new SettingsGUI(cpuControl, cpu, speaker, speakerRenderer,
                                  cassetteCtrlRoomOut,
                                  cassetteCtrlRoomOutRenderer,
                                  this);
    settingsGUI.addTransportListener(this);
    cassetteInputSampler = CLOSED_INPUT_SAMPLER;
  }

  public void resync(final long wallClockTime)
  {
    speaker.resync();
    if (cassetteCtrlRoomOut != null) {
      cassetteCtrlRoomOut.resync();
    }
    if (cassetteFileOut != null) {
      cassetteFileOut.resync();
    }
  }

  public Video getVideo()
  {
    return video;
  }

  private long lastWallClockTime = 0;

  private boolean isCassInHigh(final long wallClockTime)
  {
    /*
     * Note: There is a race condition between this method (called by
     * the CPU processor thread) and other methods of this class such
     * as cassetteStartPlaying() (called via the AWT event thread),
     * since these methods concurrently access variable
     * cassetteInputSampler.  That is, this method (and all other
     * concurrent methods) should be declared "synchronized" on a
     * mutually exclusive lock.  However, since this method is called
     * extremely often (potentially almost every other microsecond),
     * using a mutual exclusive lock is a bad idea for performance.
     * Instead, we introduce a default pseudo cassetteInputSampler
     * "CLOSED_INPUT_SAMPLER", representing a null sampling source,
     * such that switching between the pseudo cassetteInputSampler and
     * a regular cassetteInputSampler basically is an atomic operation
     * (namely, changing the value of a 64 bit pointer).
     *
     * In the following code, in the worst case, cassetteInputSampler
     * changes from a regular cassetteInputSampler to
     * CLOSED_INPUT_SAMPLER while this method is executed.  In this
     * case, we will inadvertently call
     * CLOSED_INPUT_SAMPLER.getValue(), resulting in a pseudo default
     * signal value, which should be still ok.
     */
    if (cassetteInputSampler == CLOSED_INPUT_SAMPLER)
      return false;
    if ((wallClockTime - lastWallClockTime) > 0x04000000) {
      lastWallClockTime = wallClockTime;
    }
    final short value = cassetteInputSampler.getValue(wallClockTime);
    if (cassetteCtrlRoomOut != null) {
      cassetteCtrlRoomOut.putEvent(value <= 0 ? 3 : 0, wallClockTime);
    }
    if (cassetteInputSampler.isStopped()) {
      settingsGUI.cassetteStop();
      cassetteInputSampler = CLOSED_INPUT_SAMPLER;
    }
    return value <= 0;
  }

  public int readByte(final int address, final long wallClockTime)
  {
    final int addressOffset = (address - baseAddress) & 0xffff;
    int data;
    if (addressOffset < MEMORY_SIZE) {
      data = keyboard.readByte(address, wallClockTime) & 0x3f;
      if (isCassInHigh(wallClockTime))
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
      final int cassetteOutValue = (value >> 1) & 0x3;
      if (cassetteCtrlRoomOut != null) {
        cassetteCtrlRoomOut.putEvent(cassetteOutValue, wallClockTime);
      }
      if (cassetteFileOut != null) {
        cassetteFileOut.putEvent(cassetteOutValue, wallClockTime);
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

  public void wallClockChanged(final long timePerClockCycle,
                               final long wallClockCycles,
                               final long wallClockTime)
  {
    this.timePerClockCycle = timePerClockCycle;
    this.wallClockCycles = wallClockCycles;
    this.wallClockTime = wallClockTime;
    if (video.updateWallClock(wallClockCycles, wallClockTime)) {
      cpuControl.requestIRQ();
    }
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

  @Override
  public void cassetteStartPlaying(final CassetteInputSampler cassetteInputSampler)
  {
    if (cassetteInputSampler == null) {
      throw new NullPointerException("cassetteInputSampler");
    }
    this.cassetteInputSampler = cassetteInputSampler;
  }

  @Override
  public void cassetteStartRecording(final File file) throws IOException
  {
    try {
      cassetteFileOut.resync();
      fileStreamRenderer = new FileStreamRenderer(file, cassetteFileOut);
    } catch (final Throwable t) {
      throw new IOException("WARNING: I/O: failed opening file stream: " +
                            t.getMessage() +
                            ".  No audio output will be saved.", t);
    }
    new Thread(fileStreamRenderer).start();
  }

  @Override
  public void cassetteStop()
  {
    if (cassetteInputSampler != CLOSED_INPUT_SAMPLER) {
      System.out.printf("%s: aborted%n",
                        cassetteInputSampler.getFile().getName());
      cassetteInputSampler.stop();
      cassetteInputSampler = CLOSED_INPUT_SAMPLER;
    }
    if (fileStreamRenderer != null) {
      System.out.printf("%s: stopping renderer...%n",
                        fileStreamRenderer.getFileName());
      fileStreamRenderer.stop();
      System.out.printf("%s: stopped%n",
                        fileStreamRenderer.getFileName());
      System.out.printf("%s: closing renderer...%n",
                        fileStreamRenderer.getFileName());
      fileStreamRenderer.close();
      System.out.printf("%s: closed%n",
                        fileStreamRenderer.getFileName());
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
