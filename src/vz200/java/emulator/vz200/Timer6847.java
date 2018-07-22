package emulator.vz200;

import emulator.z80.CPU;

/**
 * In the VZ200, the 6847 CRT Controller's FS output is connected to
 * the Z80's INT input, such that, effectively, the Z80 performs a
 * maskable interrupt each 20ms.  This class produces such an
 * interrupt in regular intervals.
 */
public class Timer6847 implements Runnable {
  public final static int CYCLE_TIME = 200; // [ms]

  private CPU cpu;

  private Timer6847() {}

  public Timer6847(CPU cpu) {
    this.cpu = cpu;
  }

  public void run() {
    while (true) {
      try {
	Thread.currentThread().sleep(CYCLE_TIME);
      } catch (InterruptedException e) {
	// For the moment, ignored (TODO).
      }
      //System.out.println(cpu.getProgramCounter());
      //cpu.requestIRQ();
    }
  }
}
