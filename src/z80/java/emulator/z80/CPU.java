
// TODO: move this class into new package 'emulator.cpu'.
package emulator.z80;

public interface CPU extends WallClockProvider
{
  public interface Memory
  {
    int readByte(int address, long wallClockTime);
    int readShort(int address, long wallClockTime);
    void writeByte(int address, int value, long wallClockTime);
    void writeShort(int address, int value, long wallClockTime);
    void resync(long wallClockTime);
  }

  public interface NamedObject
  {
    String getName();
  }

  public interface Register extends NamedObject
  {
    int getValue();
    void setValue(int value);
    boolean increment(); // for performance efficiency
    boolean decrement(); // for performance efficiency
    void reset();
  }

  public interface ConcreteOpCode
  {
    void addByte(int codeByte); // TODO: remove me from interface
    int getLength();
    int getByte(int index);
  }

  public interface ConcreteOperation
  {
    int getByteLength();
    String getConcreteMnemonic();
    int getAddress();
    boolean isSynthesizedCode();
    void execute();
    int getClockPeriods();
    ConcreteOpCode createOpCode();
  }

  public interface WallClockListener
  {
    void wallClockChanged(long timePerClockCycle,
                          long wallClockCycles, long wallClockTime);
  }

  int doPOP();
  void doPUSH(int op);

  Annotations getAnnotations();
  Memory getMemory();
  Memory getIO();
  Register[] getAllRegisters();
  Register getProgramCounter();
  Register getStackPointer();
  ConcreteOperation fetchNextOperation() throws MismatchException;
  ConcreteOperation fetchNextOperationNoInterrupts() throws MismatchException;

  void requestIRQ();
  void requestNMI();

  void addWallClockListener(WallClockListener listener);

  /**
   * When pausing and then continuing the CPU to run, the emulated
   * CPU's internal wall clock may need to be resync'd with real world
   * time.  Call this method to (re-)announce the CPU's wall clock to
   * all peripherals.
   */
  void resyncPeripherals();

  public class MismatchException extends Exception
  {
    private static final long serialVersionUID = 3640134396555784341L;

    MismatchException()
    {
      super();
    }

    MismatchException(String message)
    {
      super(message);
    }
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
