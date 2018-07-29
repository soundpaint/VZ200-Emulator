
// TODO: move this class into new package 'emulator.cpu'.
package emulator.z80;

public interface CPU {
  public interface Memory {
    public boolean isValidAddr(int address);
    public int readByte(int address);
    public int readShort(int address);
    public void writeByte(int address, int value);
    public void writeShort(int address, int value);
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
    public ConcreteOpCode getConcreteOpCode();
    public void execute();
    public int getClockPeriods();
  }

  public int doPOP();
  public void doPUSH(int op);

  public Memory getMemory();
  public Memory getIO();
  public Register[] getAllRegisters();
  public Register getProgramCounter();
  public Register getStackPointer();
  public ConcreteOperation fetchNextOperation() throws MismatchException;

  public void requestIRQ();
  public void requestNMI();
  public long getTimePerClockPeriod();

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
