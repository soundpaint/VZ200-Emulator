package emulator.vz200;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.URL;
import javax.swing.ImageIcon;

import emulator.z80.CPU;
import emulator.z80.CPUControl;
import emulator.z80.MemoryBus;
import emulator.z80.Monitor;
import emulator.z80.RAMMemory;
import emulator.z80.ROMMemory;
import emulator.z80.Z80;

public class VZ200
{
  private static final String IMAGES_ROOT_PATH = ".";
  private static final int RAM_START = 0x7800;
  private static final int RAM_LENGTH = 0x8800;
  private static final String OS_RESOURCENAME = "os.rom";
  private static final int OS_START = 0x0000;
  private static final int OS_LENGTH = 0x4000;

  private final CPUControl cpuControl;
  private final MemoryBus portMemoryBus;
  private final MemoryBus mainMemoryBus;
  private final IO io;
  private final Monitor monitor;

  public static ImageIcon createIcon(final String imageFileName,
                                     final String altText)
  {
    final String imagePath = IMAGES_ROOT_PATH + "/" + imageFileName;
    final URL imageURL = VZ200.class.getResource(imagePath);
    if (imageURL != null) {
      return new ImageIcon(imageURL, altText);
    }
    return null;
  }

  public VZ200() throws IOException
  {
    final ROMMemory rom = new ROMMemory((Class<? extends Object>)VZ200.class,
                                        OS_RESOURCENAME,
                                        OS_START, OS_LENGTH);
    portMemoryBus = new MemoryBus();
    mainMemoryBus = new MemoryBus();
    final Z80 z80 = new Z80(mainMemoryBus, portMemoryBus);
    cpuControl = new CPUControl(z80);
    cpuControl.addResourceLocation(VZ200.class);
    final RAMMemory ram = new RAMMemory(RAM_START, RAM_LENGTH);
    io = new IO(cpuControl, z80, z80.getWallClockTime());
    z80.addWallClockListener(io);
    final Video video = io.getVideo();
    mainMemoryBus.addReader(ram);
    mainMemoryBus.addWriter(ram);
    mainMemoryBus.addWriter(rom);
    mainMemoryBus.addReader(io);
    mainMemoryBus.addWriter(io);
    mainMemoryBus.addReader(video);
    mainMemoryBus.addWriter(video);
    monitor = new Monitor(cpuControl);
  }

  private void run()
  {
    monitor.run("n+=annotations.xml\n" +
                "n+=annotations-math.xml\n" +
                "n+=annotations-rt.xml\n" +
                "g0");
  }

  public static void main(final String argv[]) throws IOException
  {
    new VZ200().run();
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
