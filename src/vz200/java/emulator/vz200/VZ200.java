package emulator.vz200;

import java.io.DataInputStream;
import java.io.IOException;

import emulator.z80.CPU;
import emulator.z80.ChainedMemory;
import emulator.z80.Monitor;
import emulator.z80.RAMMemory;
import emulator.z80.ROMMemory;
import emulator.z80.Z80;

public class VZ200 {
  private final static int RAM_START = 0x7000;
  private final static int RAM_LENGTH = 0x2000;
  private final static String OS_RESOURCENAME = "os.rom";
  private final static int OS_START = 0x0000;
  private final static int OS_LENGTH = 0x4000;

  private CPU z80;

  public VZ200() throws IOException {
    IO io = new IO();
    Video video = io.getVideo();
    ChainedMemory portMemory = new ChainedMemory();
    portMemory.prependMemory(new RAMMemory(0x00, 0x100));
    ChainedMemory mainMemory = new ChainedMemory();
    mainMemory.prependMemory(new RAMMemory(RAM_START, RAM_LENGTH));
    mainMemory.prependMemory(new ROMMemory(this.getClass(), OS_RESOURCENAME,
					   OS_START, OS_LENGTH));
    mainMemory.prependMemory(io);
    mainMemory.prependMemory(video);
    z80 = new Z80(mainMemory, portMemory);
    new Thread(new Timer6847(z80)).start();
  }

  public static void main(String argv[]) throws IOException {
    VZ200 vz200 = new VZ200();
    new Monitor(vz200.z80).run("g0");
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
