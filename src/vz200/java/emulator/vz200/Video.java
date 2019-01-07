package emulator.vz200;

import java.io.IOException;
import javax.swing.JPanel;
import javax.swing.JFrame;

import emulator.z80.CPU;
import emulator.z80.MemoryBus;
import emulator.z80.RAMMemory;
import emulator.z80.Util;

public class Video extends JFrame
  implements MemoryBus.BusReader, MemoryBus.BusWriter
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

  private final VideoPanel panel;
  private final RAMMemory videoRAM;
  private final int baseAddress;
  private long wallClockTime;
  private long prevHsCycleLowStart;
  private long prevFsCycleLowStart;

  @Override
  public int readByte(final int address, final long wallClockTime)
  {
    return videoRAM.readByte(address, wallClockTime);
  }

  @Override
  public int readShort(final int address, final long wallClockTime)
  {
    return videoRAM.readShort(address, wallClockTime);
  }

  @Override
  public void writeByte(final int address, final int value,
                        final long wallClockTime)
  {
    final int previousValue = videoRAM.readByte(address, wallClockTime);
    videoRAM.writeByte(address, value, wallClockTime);
    if (value != previousValue) {
      panel.invalidate(address);
    }
  }

  @Override
  public void writeShort(final int address, final int value,
                         final long wallClockTime)
  {
    final int previousValue = videoRAM.readShort(address, wallClockTime);
    videoRAM.writeShort(address, value, wallClockTime);
    if (value != previousValue) {
      panel.invalidate(address);
      panel.invalidate(address + 1);
    }
  }

  @Override
  public void resync(final long wallClockTime) {}

  public boolean hs()
  {
    return wallClockTime - prevHsCycleLowStart >= HS_CYCLE_LOW;
  }

  public boolean fs()
  {
    return wallClockTime - prevFsCycleLowStart >= FS_CYCLE_LOW;
  }

  public boolean updateWallClock(final long wallClockCycles,
                                 final long wallClockTime)
  {
    this.wallClockTime = wallClockTime;
    if (wallClockTime - prevHsCycleLowStart >= HS_CYCLE) {
      prevHsCycleLowStart += HS_CYCLE;
    }
    final boolean doIrq;
    if (wallClockTime - prevFsCycleLowStart >= FS_CYCLE) {
      prevFsCycleLowStart += FS_CYCLE;
      doIrq = true;
    } else {
      doIrq = false;
    }
    return doIrq;
  }

  public void setColorMode(final boolean colorMode)
  {
    panel.setColorMode(colorMode);
  }

  public void setDisplayMode(final boolean displayMode)
  {
    panel.setDisplayMode(displayMode);
  }

  public void setZoomFactor(final int zoomFactor)
  {
    panel.setZoomFactor(zoomFactor);
    pack();
  }

  public Video() throws IOException
  {
    this(DEFAULT_BASE_ADDRESS);
  }

  public Video(final int baseAddress) throws IOException
  {
    super("VZ200 Video Screen");
    this.baseAddress = baseAddress;
    wallClockTime = 0;
    prevHsCycleLowStart = 0;
    prevFsCycleLowStart = 0;
    setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    addWindowListener(ApplicationExitListener.defaultInstance);
    setJMenuBar(new VideoMenu(this));
    panel = new VideoPanel(baseAddress);
    getContentPane().add(panel);
    videoRAM = panel.getVideoRAM();
    pack();
    setVisible(true);
  }

  public String toString()
  {
    return String.format("Video[baseAddress=%04xh, videoRAM=%s]",
                         Util.hexShortStr(baseAddress), videoRAM);
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
