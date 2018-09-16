
// TODO: move this class into new package 'emulator.cpu'.
package emulator.z80;

public interface CPU {
  public interface Memory {
    public int readByte(int address, long wallClockTime);
    public int readShort(int address, long wallClockTime);
    public void writeByte(int address, int value, long wallClockTime);
    public void writeShort(int address, int value, long wallClockTime);
    public void resync(long wallClockTime);
  }

  public interface NamedObject {
    public String getName();
  }

  public interface Register extends NamedObject {
    public int getValue();
    public void setValue(int value);
    public boolean increment(); // for performance efficiency
    public boolean decrement(); // for performance efficiency
    public void reset();
  }

  public interface ConcreteOpCode {
    public void addByte(int codeByte); // TODO: remove me from interface
    public int getLength();
    public int getByte(int index);
  }

  public interface ConcreteOperation {
    public String getConcreteMnemonic();
    public int getAddress();
    public boolean isSynthesizedCode();
    public void execute();
    public int getClockPeriods();
    public ConcreteOpCode createOpCode();
  }

  public interface WallClockListener {
    public void wallClockChanged(long wallClockCycles, long wallClockTime);
  }

  public int doPOP();
  public void doPUSH(int op);

  public Memory getMemory();
  public Memory getIO();
  public Register[] getAllRegisters();
  public Register getProgramCounter();
  public Register getStackPointer();
  public ConcreteOperation fetchNextOperation() throws MismatchException;
  public ConcreteOperation fetchNextOperationNoInterrupts()
    throws MismatchException;

  public void requestIRQ();
  public void requestNMI();

  public void addWallClockListener(WallClockListener listener);

  public long getTimePerClockCycle();

  /**
   * Returns the total number of instruction cycles of all
   * instructions performed since CPU start.
   */
  public long getWallClockCycles();

  /**
   * Returns the total number of time in ns of all instructions
   * performed since CPU start.
   */
  public long getWallClockTime();

  /**
   * When pausing and then continuing the CPU to run, the emulated
   * CPU's internal wall clock may need to be resync'd with real world
   * time.  Call this method to (re-)announce the CPU's wall clock to
   * all peripherals.
   */
  public void resyncPeripherals();

  public class MismatchException extends Exception {
    private static final long serialVersionUID = 3640134396555784341L;

    public MismatchException() { super(); }
    public MismatchException(String message) { super(message); }
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
