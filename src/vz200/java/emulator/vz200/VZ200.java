package emulator.vz200;

import java.io.DataInputStream;
import java.io.IOException;

import emulator.z80.CPU;
import emulator.z80.MemoryBus;
import emulator.z80.Monitor;
import emulator.z80.RAMMemory;
import emulator.z80.ROMMemory;
import emulator.z80.Z80;

public class VZ200 implements CPU.WallClockListener {
  private final static int RAM_START = 0x7800;
  private final static int RAM_LENGTH = 0x2800;
  private final static String OS_RESOURCENAME = "os.rom";
  private final static int OS_START = 0x0000;
  private final static int OS_LENGTH = 0x4000;

  private IO io;
  private CPU z80;
  private Monitor monitor;

  public VZ200() throws IOException {
    ROMMemory rom = new ROMMemory((Class<? extends Object>)VZ200.class,
                                  OS_RESOURCENAME,
                                  OS_START, OS_LENGTH);
    MemoryBus portMemoryBus = new MemoryBus();
    MemoryBus mainMemoryBus = new MemoryBus();
    z80 = new Z80(mainMemoryBus, portMemoryBus);
    z80.addWallClockListener(this);
    RAMMemory ram = new RAMMemory(RAM_START, RAM_LENGTH);
    io = new IO(z80.getWallClockTime());
    Video video = io.getVideo();
    mainMemoryBus.addReader(ram);
    mainMemoryBus.addWriter(ram);
    mainMemoryBus.addWriter(rom);
    mainMemoryBus.addReader(io);
    mainMemoryBus.addWriter(io);
    mainMemoryBus.addReader(video);
    mainMemoryBus.addWriter(video);
    monitor = new Monitor(z80);
    monitor.addResourceLocation(VZ200.class);
  }

  public void wallClockChanged(long wallClockCycles, long wallClockTime) {
    if (io.updateWallClock(wallClockCycles, wallClockTime)) {
      z80.requestIRQ();
    }
  }

  private void run() {
    monitor.run("n+=annotations.xml\n" +
                "g0");
  }

  public static void main(String argv[]) throws IOException {
    new VZ200().run();
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
