package emulator.z80;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

public class Z80 implements CPU {
  // *** CODE FETCHING UNIT ***************************************************

  private static class ConcreteOpCode implements CPU.ConcreteOpCode {
    private int[] bytes;
    private int length;

    public ConcreteOpCode() {
      bytes = new int[4];
    }

    public void reset() {
      length = 0;
    }

    public void addByte(int codeByte) {
      bytes[length++] = codeByte;
    }

    public int getLength() { return length; }

    public int getByte(int index) {
      if ((index < 0) || (index >= length))
	throw new IndexOutOfBoundsException("" + index);
      else
	return bytes[index];
    }

    public String toString() {
      StringBuffer buf = new StringBuffer();
      if (length > 0)
	buf.append(Util.hexByteStr(bytes[0]));
      for (int i = 1; i < length; i++)
	buf.append(" " + Util.hexByteStr(bytes[i]));
      while (buf.length() < 16)
	buf.append(' ');
      return buf.toString();
    }
  }

  private interface CodeFetcher
  {
    int getBaseAddress();

    int fetchNextByte();

    int fetchByte(final int index);

    /**
     * Clear all fetched data.
     */
    void reset();

    /**
     * Restart delivering all data that has already been fetched.
     */
    void restart();
  }

  private class MemoryCodeFetcher implements CodeFetcher
  {
    private final static int CACHE_SIZE = 4;
    private final CPU.Memory memory;
    private final Reg16 regPC;
    private int offset, size;
    private int[] cache;

    private MemoryCodeFetcher()
    {
      throw new UnsupportedOperationException("unsupported empty constructor");
    }

    public MemoryCodeFetcher(final CPU.Memory memory, final Reg16 regPC)
    {
      this.memory = memory;
      this.regPC = regPC;
      cache = new int[CACHE_SIZE];
      size = 0;
      offset = 0;
    }

    public int getBaseAddress()
    {
      return regPC.getValue();
    }

    public int fetchNextByte()
    {
      if (offset < size)
	return cache[offset++];
      final int result =
        memory.readByte((regPC.getValue() + offset) & 0xffff,
                        wallClockTime) & 0xff;
      cache[offset++] = result;
      size = offset;
      return result;
    }

    public int fetchByte(final int offset)
    {
      if (offset >= size) {
        this.offset = size;
        while (this.offset <= offset) {
          fetchNextByte();
        }
      }
      this.offset = offset;
      return cache[this.offset++];
    }

    public void restart()
    {
      offset = 0;
    }

    public void reset()
    {
      offset = 0;
      size = 0;
    }

    public String toString()
    {
      final StringBuffer sb = new StringBuffer();
      sb.append(Util.hexShortStr(regPC.getValue()));
      sb.append("-   ");
      for (int offset = 0; offset < size; offset++) {
	sb.append(" " + Util.hexByteStr(cache[offset]));
      }
      return sb.toString();
    }
  }

  /**
   * Single-byte code fetcher.  Needed when serving mode 0 interrupt
   * requests.
   */
  private class IntrBusDataFetcher implements CodeFetcher
  {
    private int count;
    private int intr_bus_data;

    public IntrBusDataFetcher()
    {
      count = 0;
    }

    public int getBaseAddress()
    {
      return regPC.getValue();
    }

    public void setIntrBusData(final int intr_bus_data)
    {
      this.intr_bus_data = intr_bus_data;
    }

    public int fetchNextByte()
    {
      final int result = count == 0 ? intr_bus_data : 0;
      count++;
      return result;
    }

    public int fetchByte(final int index)
    {
      return index == 0 ? intr_bus_data : 0;
    }

    public void restart()
    {
      count = 0;
    }

    public void reset()
    {
      count = 0;
    }

    public String toString()
    {
      final StringBuffer sb = new StringBuffer();
      sb.append(Util.hexShortStr(regPC.getValue()));
      sb.append("-   ");
      sb.append(" " + Util.hexByteStr(intr_bus_data));
      for (int offset = 0; offset < 3; offset++) {
	sb.append(" ??");
      }
      return sb.toString();
    }
  }

  private MemoryCodeFetcher memoryCodeFetcher;
  private IntrBusDataFetcher intrBusDataFetcher;

  // *** CPU REGISTERS ********************************************************

  public interface Reg8 extends CPU.Register {}

  class GenericReg8 implements Reg8 {
    private int value;
    private String name;
    private GenericReg8() {}
    public GenericReg8(String name) { this.name = name; }
    public String getName() { return name; }
    public int getValue() { return value; }

    public void setValue(int value) { this.value = value; }

    public boolean increment() {
      value++;
      value &= 0xff;
      return value == 0x00;
    }
    public boolean decrement() {
      value--;
      value &= 0xff;
      return value == 0xff;
    }
    public void reset() { setValue(0); }
    public String toString() { return name + "=" + Util.hexByteStr(value); }
  }

  class IndirectReg8 implements Reg8 {
    protected CPU.Memory memory;
    protected Reg16 reg16;

    private IndirectReg8() {}

    public IndirectReg8(CPU.Memory memory, Reg16 reg16) {
      this.memory = memory;
      this.reg16 = reg16;
    }

    public String getName() { return "(" + reg16.getName() + ")"; }

    public int getValue() {
      return memory.readByte(reg16.getValue(), wallClockTime) & 0xff;
    }

    public void setValue(int value) {
      memory.writeByte(reg16.getValue(), value, wallClockTime);
    }

    public boolean increment() {
      int value = (getValue() + 1) & 0xff;
      setValue(value);
      return (value == 0x00);
    }

    public boolean decrement() {
      int value = (getValue() - 1) & 0xff;
      setValue(value);
      return (value == 0xff);
    }

    public void reset() { setValue(0); }
  }

  class IndirectReg8Disp8 extends IndirectReg8 {
    private byte disp8 = 0x00;

    public IndirectReg8Disp8(CPU.Memory memory, Reg16 reg16) {
      super(memory, reg16);
    }

    public void setDisp8(int disp8) { this.disp8 = (byte)disp8; }

    public int getDisp8() { return disp8; }

    private String disp8ToString(byte disp8) {
      if (disp8 >= 0)
        return String.format("+%02x", disp8);
      else
        return String.format("-%02x", -disp8);
    }

    public String getName() {
      return "(" + reg16.getName() + disp8ToString(disp8) + ")";
    }

    public int getValue() {
      return this.memory.readByte((reg16.getValue() + disp8) & 0xffff,
                                  wallClockTime) & 0xff;
    }

    public void setValue(int value) {
      this.memory.writeByte((reg16.getValue() + disp8) & 0xffff, value,
                            wallClockTime);
    }
  }

  // *** CPU FLAGS DIRECT ACCESS **********************************************

  // flags mask constants for processor register F
  private final static int FLAG_S = 0x80;
  private final static int FLAG_Z = 0x40;
  private final static int FLAG_X1 = 0x20;
  private final static int FLAG_H = 0x10;
  private final static int FLAG_X2 = 0x08;
  private final static int FLAG_PV = 0x04;
  private final static int FLAG_N = 0x02;
  private final static int FLAG_C = 0x01;

  private class Flag {
    private String name, nameTrue, nameFalse;
    private int andMask;
    private int reverseAndMask;
    private Reg8 regF;

    public Flag(String name, String nameTrue, String nameFalse,
                int andMask, Reg8 regF) {
      this.name = name;
      this.nameTrue = nameTrue;
      this.nameFalse = nameFalse;
      this.andMask = andMask;
      reverseAndMask = andMask ^ 0xff;
      this.regF = regF;
    }

    public boolean get() {
      return (regF.getValue() & andMask) != 0x00;
    }

    public void set(boolean value) {
      if (value)
	regF.setValue(regF.getValue() | andMask);
      else
	regF.setValue(regF.getValue() & reverseAndMask);
    }

    public String getName() {
      return name;
    }

    public String getValueName() {
      return get() ? nameTrue : nameFalse;
    }

    public String toString() {
      return getName() + "=" + getValueName();
    }
  }

  private /*final*/ Flag flagS;
  private /*final*/ Flag flagZ;
  private /*final*/ Flag flagX1;
  private /*final*/ Flag flagH;
  private /*final*/ Flag flagX2;
  private /*final*/ Flag flagPV;
  private /*final*/ Flag flagN;
  private /*final*/ Flag flagC;

  class Flags extends GenericReg8
  {
    private Flag[] flags;

    public Flags(String name) {
      super(name);
      System.out.println("setting up processor flags...");
      flags = new Flag[] {
        flagS = new Flag("S", "M", "P", FLAG_S, this),
        flagZ = new Flag("Z", "Z", "NZ", FLAG_Z, this),
        flagX1 = new Flag("X", "1", "0", FLAG_X1, this),
        flagH = new Flag("H", "H", "NH", FLAG_H, this),
        flagX2 = new Flag("X", "1", "0", FLAG_X2, this),
        flagPV = new Flag("PV", "PE", "PO", FLAG_PV, this),
        flagN = new Flag("N", "N", "NN", FLAG_N, this),
        flagC = new Flag("C", "C", "NC", FLAG_C, this)
      };
    }

    public String toString() {
      StringBuffer s = new StringBuffer();
      for (Flag flag : flags) {
        if (s.length() > 0) s.append(" ");
        s.append(flag.getValueName());
      }
      return
        getName() + "=" + Util.hexByteStr(getValue()) + "(" + s + ")";
    }
  }

  public interface Reg16 extends CPU.Register {}

  class GenericReg16 implements Reg16 {
    private int value;
    private String name;
    private GenericReg16() {}
    public GenericReg16(String name) { this.name = name; }
    public String getName() { return name; }
    public int getValue() { return value; }
    public void setValue(int value) { this.value = value; }
    public boolean increment() {
      value++;
      value &= 0xffff;
      return value == 0x0000;
    }
    public boolean decrement() {
      value--;
      value &= 0xffff;
      return value == 0xffff;
    }
    public void reset() { setValue(0); }
    public String toString() { return name + "=" + Util.hexShortStr(value); }
  }

  class RegPair implements Reg16 {
    private Reg8 regHi, regLo;

    private RegPair() {}

    public RegPair(Reg8 regHi, Reg8 regLo) {
      this.regHi = regHi;
      this.regLo = regLo;
    }

    public String getName() { return regHi.getName() + regLo.getName(); }

    public int getValue() {
      return (regHi.getValue() << 8) | (regLo.getValue() & 0xff);
    }

    public void setValue(int value) {
      regHi.setValue(value >>> 8);
      regLo.setValue(value & 0xff);
    }

    public boolean increment() {
      return regLo.increment() ? regHi.increment() : false;
    }

    public boolean decrement() {
      return regLo.decrement() ? regHi.decrement() : false;
    }

    public void reset() { setValue(0); }

    public String toString() {
      return getName() + "=" + Util.hexShortStr(getValue());
    }
  }

  class RegIM extends GenericReg8 {
    public RegIM() {
      super("IM");
    }

    public void setValue(int value) {
      if ((value < 0) || (value > 0x2)) {
        throw new IllegalArgumentException(String.format("value=%02Xh", value));
      }
      super.setValue(value);
    }

    public boolean increment() {
      throw new UnsupportedOperationException("can not increment value of IM");
    }

    public boolean decrement() {
      throw new UnsupportedOperationException("can not decrement value of IM");
    }
  }

  private Reg8 regA, regF, regB, regC, regD, regE, regH, regL;
  private RegPair regAF, regBC, regDE, regHL;
  private Reg16 regIX, regIY;
  private Reg8 regI, regR;
  private Reg16 regSP, regPC;
  private Reg16 regAF_, regBC_, regDE_, regHL_;
  private RegIM regIM;

  /** pseudo register (HL) */
  private Reg8 indirectRegHL;

  /** pseudo register (IX+disp8) */
  private IndirectReg8Disp8 indirectIXDisp8;

  /** pseudo register (IY+disp8) */
  private IndirectReg8Disp8 indirectIYDisp8;

  /** complete register set */
  private /*final*/ CPU.Register[] REGISTER_SET;

  /** register pairs for LD and arithmetic operations */
  private /*final*/ Reg16[] REG16;

  /** register pairs for PUSH/POP operations */
  private /*final*/ Reg16[] PREG16;

  /** 8 bit registers including indirect (HL) addressing */
  private /*final*/ Reg8[] REG8;

  /** 8 bit registers excluding indirect (HL) addressing */
  private /*final*/ Reg8[] DREG8;

  /** registers for "ADD IX,reg16" operation */
  private /*final*/ Reg16[] QREG16;

  /** registers for "ADD IY,reg16" operation */
  private /*final*/ Reg16[] RREG16;

  private void createRegisters() {
    regA = new GenericReg8("A");
    regF = new Flags("F");
    regB = new GenericReg8("B");
    regC = new GenericReg8("C");
    regD = new GenericReg8("D");
    regE = new GenericReg8("E");
    regH = new GenericReg8("H");
    regL = new GenericReg8("L");
    regAF = new RegPair(regA, regF);
    regBC = new RegPair(regB, regC);
    regDE = new RegPair(regD, regE);
    regHL = new RegPair(regH, regL);
    regIX = new GenericReg16("IX");
    regIY = new GenericReg16("IY");
    regI = new GenericReg8("I");
    regR = new GenericReg8("R");
    regSP = new GenericReg16("SP");
    regPC = new GenericReg16("PC");
    regAF_ = new GenericReg16("AF'");
    regBC_ = new GenericReg16("BC'");
    regDE_ = new GenericReg16("DE'");
    regHL_ = new GenericReg16("HL'");
    regIM = new RegIM();
    indirectRegHL = new IndirectReg8(memory, regHL);
    indirectIXDisp8 = new IndirectReg8Disp8(memory, regIX);
    indirectIYDisp8 = new IndirectReg8Disp8(memory, regIY);
    REGISTER_SET = new CPU.Register[] {
      regF, regA, regBC, regDE, regHL, regIX, regIY,
      regAF_, regBC_, regDE_, regHL_,
      regSP, regPC, regI, regR, regIM
    };
    REG16 = new Reg16[] {
      regBC, regDE, regHL, regSP
    };
    PREG16 = new Reg16[] {
      regBC, regDE, regHL, regAF
    };
    REG8 = new Reg8[] {
      regB, regC, regD, regE, regH, regL, indirectRegHL, regA
    };
    DREG8 = new Reg8[] {
      regB, regC, regD, regE, regH, regL, null, regA
    };
    QREG16 = new Reg16[] {
      regBC, regDE, regIX, regSP
    };
    RREG16 = new Reg16[] {
      regBC, regDE, regIY, regSP
    };
  }

  public CPU.Register[] getAllRegisters() { return REGISTER_SET; }

  public CPU.Register getProgramCounter() { return regPC; }

  public CPU.Register getStackPointer() { return regSP; }

  private class Cond implements CPU.NamedObject {
    private String name;
    private int andMask;
    private int trueValue;
    private Reg8 regF;

    private Cond() {}

    private Cond(String name, int andMask, boolean value, Reg8 regF) {
      this.name = name;
      this.andMask = andMask;
      trueValue = (value) ? andMask : 0x00;
      this.regF = regF;
    }

    public String getName() { return name; }

    public boolean isTrue() {
      return (regF.getValue() & andMask) == trueValue;
    }
  }

  private /*final*/ Cond condNZ;
  private /*final*/ Cond condZ;
  private /*final*/ Cond condNC;
  private /*final*/ Cond condC;
  private /*final*/ Cond condPO;
  private /*final*/ Cond condPE;
  private /*final*/ Cond condP;
  private /*final*/ Cond condM;

  /** conditions CALL/JP/RET */
  private /*final*/ Cond[] COND;

  private void createConditions() {
    condNZ = new Cond("NZ", FLAG_Z, false, regF);
    condZ  = new Cond("Z",  FLAG_Z, true,  regF);
    condNC = new Cond("NC", FLAG_C, false, regF);
    condC  = new Cond("C",  FLAG_C, true,  regF);
    condPO = new Cond("PO", FLAG_PV, false, regF);
    condPE = new Cond("PE", FLAG_PV, true,  regF);
    condP  = new Cond("P",  FLAG_S, false, regF);
    condM  = new Cond("M",  FLAG_S, true,  regF);
    COND = new Cond[]
    { condNZ, condZ, condNC, condC, condPO, condPE, condP, condM };
  }

  public void printRegs(PrintStream out) {
    out.println("  FLAGS " + flagC + " " + flagN + " " + flagPV +
		" " + flagH + " " + flagZ + " " + flagS);
    out.println("  " + regA + " " + regBC + " " + regDE + " " + regHL +
		" " + regIX + " " + regIY);
    out.println("  " + regAF_ + " " + regBC_ + " " + regDE_ + " " + regHL_);
    out.println("  " + regSP + " " + regPC +
		" " + regI + " " + regR + " IM=" + regIM);
  }

  // *** INTERRUPT HANDLING ***************************************************

  private final static int INTR_MODE_0 = 0;
  private final static int INTR_MODE_1 = 1;
  private final static int INTR_MODE_2 = 2;

  private int intr_bus_data;
  private boolean irq_requested;
  private boolean irq_to_be_enabled;
  private boolean irq_enabled;
  private boolean nmi_requested;

  public void setInterruptResponseVector(int intr_bus_data) {
    this.intr_bus_data = intr_bus_data;
  }

  public void requestIRQ() {
    irq_requested = true;
  }

  public void requestNMI() {
    nmi_requested = true;
  }

  // *** CPU TIMING ***********************************************************

  private long wallClockCycles = 0; // number of CPU cycles since startup
  private long wallClockTime = 0; // [ns since startup]
  private long timePerClockCycle; // [ns]
  private boolean statisticsEnabled;

  public void addWallClockListener(WallClockListener listener) {
    wallClockListeners.add(listener);
  }

  private void notifyWallClockListeners() {
    for (WallClockListener listener : wallClockListeners) {
      listener.wallClockChanged(timePerClockCycle,
                                wallClockCycles, wallClockTime);
    }
  }

  public long getTimePerClockCycle() {
    return timePerClockCycle;
  }

  /**
   * Returns the total number of instruction cycles of all
   * instructions performed since CPU start.
   */
  public long getWallClockCycles() {
    return wallClockCycles;
  }

  /**
   * Returns the total number of time in ns of all instructions
   * performed since CPU start.
   */
  public long getWallClockTime() {
    return wallClockTime;
  }

  private void updateWallClock(int cycles) {
    wallClockCycles += cycles;
    wallClockTime += cycles * timePerClockCycle;
    notifyWallClockListeners();
  }

  // *** OPCODE HELPERS *******************************************************

  private interface Function extends CPU.NamedObject {
    public String getName();
    public String evaluate(int arg);
  }

  private class Enumeration implements Function {
    private String name;
    private CPU.NamedObject[] values;

    private Enumeration() {}

    Enumeration(String name, CPU.NamedObject[] values) {
      this.name = name;
      this.values = values;
    }

    public String getName() { return name; }

    public String evaluate(int arg) {
      CPU.NamedObject value = values[arg];
      return value != null ? value.getName() : null;
    }
  }

  private class Identity implements Function {
    private String name;
    private int digits;

    private Identity() {}

    Identity(String name, int digits) {
      this.name = name;
      this.digits = digits;
    }

    public String getName() { return name; }

    public String evaluate(int arg) {
      String hex = Util.hexIntStr(arg);
      while (hex.length() < digits)
	hex = "0" + hex; // TODO: This is very slow and inefficient!
      return hex.substring(hex.length() - digits);
    }
  }

  private class Address implements Function {
    private String name;
    private int digits;

    private Address() {}

    Address(String name, int digits) {
      this.name = name;
      this.digits = digits;
    }

    public String getName() { return name; }

    public String evaluate(int address) {
      String label = annotations.getLabel(address);
      if (label != null) {
        return label;
      }
      String hex = Util.hexIntStr(address);
      while (hex.length() < digits)
	hex = "0" + hex; // TODO: This is very slow and inefficient!
      return hex.substring(hex.length() - digits);
    }
  }

  private class Disp8 implements Function {
    private String name;

    private Disp8() {}

    Disp8(String name) {
      this.name = name;
    }

    public String getName() { return name; }

    public String evaluate(int arg) {
      byte disp8 = (byte)arg;
      if (disp8 >= 0)
        return String.format("+%02x", disp8);
      else
        return String.format("-%02x", -disp8);
    }
  }

  private class Rel8 implements Function {
    private String name;
    private ConcreteOperation concreteOperation;

    private Rel8() {}

    Rel8(String name, ConcreteOperation concreteOperation) {
      this.name = name;
      this.concreteOperation = concreteOperation;
    }

    public String getName() { return name; }

    public String evaluate(int arg) {
      int address = concreteOperation.getNextAddress();
      address += (byte)arg; // signed byte
      String label = annotations.getLabel(address);
      if (label != null) {
        return label;
      }
      return Util.hexShortStr(address);
    }
  }

  private class Rst implements Function {
    private String name;

    private Rst() {}

    Rst(String name) {
      this.name = name;
    }

    public String getName() { return name; }

    public String evaluate(int arg) {
      if ((arg < 0) || (arg > 7)) {
        throw new IndexOutOfBoundsException("arg=" + arg);
      }
      int address = 8 * arg;
      String label = annotations.getLabel(address);
      if (label != null) {
        return label;
      }
      String hex = Util.hexIntStr(address);
      while (hex.length() < 2)
	hex = "0" + hex; // TODO: This is very slow and inefficient!
      return hex.substring(hex.length() - 2);
    }
  }

  private /*final*/ Function[] FUNCTIONS;

  private void createFunctions() {
    FUNCTIONS = new Function[] {
      new Enumeration("REG16", REG16),
      new Enumeration("PREG16", PREG16),
      new Enumeration("REG8", REG8),
      new Enumeration("DREG8", DREG8),
      new Enumeration("QREG16", QREG16),
      new Enumeration("RREG16", RREG16),
      new Enumeration("COND", COND),
      new Identity("VAL3", 1),
      new Identity("VAL8", 2),
      new Identity("VAL16", 4),
      new Address("ADR16", 4),
      new Disp8("DISP8"),
      new Rel8("REL8", concreteOperation),
      new Rst("RST")
    };
  }

  // *** CPU OPERATION CODES **************************************************

  private class Arguments {

    private final static int ARGS_LENGTH = 26;

    /**
     * <PRE>
     * arg[0] holds value for generic variable 'a'.
     * arg[1] holds value for generic variable 'b'.
     * ...
     * arg[25] holds value for generic variable 'z'.
     * <PRE>
     */
    private int[] args;
    private boolean useDefaultClockPeriods;

    public Arguments() {
      args = new int[ARGS_LENGTH]; /* mnemonic variables 'a' .. 'z' */
    }

    /**
     * @param index 0..25 (for generic variables 'a'..'z').
     */
    public int getArg(int index) {
      if ((index < 0) || (index >= ARGS_LENGTH))
	throw new IndexOutOfBoundsException("index");
      return args[index];
    }

    /**
     * @param index 0..25 (for generic variables 'a'..'z').
     */
    public void setArg(int index, int value) {
      if ((index < 0) || (index > ARGS_LENGTH))
	throw new IndexOutOfBoundsException("index");
      args[index] = value;
    }

    public String toString() {
      StringBuffer sb = new StringBuffer();
      for (int index = 0; index < ARGS_LENGTH; index++) {
        if (sb.length() > 0) {
          sb.append(", ");
        }
        sb.append((char)(index + 'a'));
        sb.append("=");
        sb.append(args[index]);
      }
      return "Arguments{" + sb + "}";
    }
  }

  public class ConcreteOperation implements CPU.ConcreteOperation {
    private Arguments args;
    private String concreteMnemonic;
    private GenericOperation genericOperation;
    private CodeFetcher codeFetcher;
    private int address;
    private boolean isSynthesizedCode;
    private long systemNanoTime;

    public ConcreteOperation() {
      args = new Arguments();
    }

    public Arguments getArguments() { return args; }

    public String getConcreteMnemonic() {
      return genericOperation.createConcreteMnemonic(args);
    }

    public int getAddress() {
      return address;
    }

    public boolean isSynthesizedCode() {
      return isSynthesizedCode;
    }

    public ConcreteOpCode createOpCode() {
      codeFetcher.restart();
      ConcreteOpCode concreteOpCode = new ConcreteOpCode();
      for (int i = 0; i < genericOperation.byteLength; i++) {
        int concreteOpCodeByte = codeFetcher.fetchNextByte();
        concreteOpCode.addByte(concreteOpCodeByte);
      }
      return concreteOpCode;
    }

    public int getByteLength() {
      return genericOperation.byteLength;
    }

    public int getNextAddress() {
      return (address + genericOperation.byteLength) & 0xffff;
    }

    public void execute() {
      genericOperation.execute(args);
      updateWallClock(getClockPeriods());
      if (statisticsEnabled) {
        updateInstructionLevelStatistics(getClockPeriods(),
                                         getSystemNanoTime());
      }
    }

    public int getClockPeriods() {
      return
	args.useDefaultClockPeriods ?
	genericOperation.defaultClockPeriods :
	genericOperation.altClockPeriods;
    }

    /**
     * Tries to match the concrete code as delivered from the code
     * fetcher with this operation's generic opcode pattern.  The
     * arguments that result from matching the generic code pattern
     * with the concrete code are stored and can be accessed via the
     * <code>getArg()</code> method.
     *
     * @return The length of the matched operation in bytes.
     *
     * @see #getArg
     */
    public int instantiate(PrecompiledGenericOperation precompiledGenericOperation,
                           CodeFetcher codeFetcher)
      throws CPU.MismatchException
    {
      this.codeFetcher = codeFetcher;
      precompiledGenericOperation.fillArgs(codeFetcher, args);
      genericOperation = precompiledGenericOperation.getGenericOperation();
      return genericOperation.byteLength;
    }

    public void setSystemNanoTime(final long systemNanoTime)
    {
      this.systemNanoTime = systemNanoTime;
    }

    public long getSystemNanoTime()
    {
      return systemNanoTime;
    }
  }

  private abstract class GenericOperation {
    private String genericMnemonic, genericOpCode;
    private int defaultClockPeriods, altClockPeriods;

    /**
     * Possible values for each bit of the op-code:
     * <PRE>
     * 0: constant op-code bit '0'
     * 1: constant op-code bit '1'
     * 2: variable op-code bit for generic variable 'a'
     * 3: variable op-code bit for generic variable 'b'
     * ...
     * 27: variable op-code bit for generic variable 'z'
     * </PRE>
     */
    private int[] genericOpCodeBits;

    /**
     * Length of this operation's underlying op code
     * as number of bytes.
     */
    private int byteLength;

    /**
     * Lists all generic variables (by means of their character code
     * (0..25)) that are used by this generic operation.
     */
    private int[] varIndex;

    /**
     * Lists the bit sizes of all generic variables that are used by
     * this generic operation (in the same order of variables as in
     * varIndex).
     */
    private int[] varSize;

    public GenericOperation() {}

    public void init(String genericMnemonic,
		     String genericOpCode,
		     int defaultClockPeriods,
		     int altClockPeriods) {
      setGenericMnemonic(genericMnemonic);
      setGenericOpCode(genericOpCode);
      setDefaultClockPeriods(defaultClockPeriods);
      setAltClockPeriods(altClockPeriods);
    }

    public abstract void init();

    public void setGenericMnemonic(String genericMnemonic) {
      if (genericMnemonic == null)
	throw new NullPointerException("genericMnemonic");
      this.genericMnemonic = genericMnemonic;
    }

    public String getGenericMnemonic() {
      return genericMnemonic;
    }

    /**
     * This is an arbitrary upper limit for the opcode's length in bits,
     * just to avoid accidental allocation of lots of memory.
     */
    private final static int MAX_OPCODE_LENGTH = 64;

    private class MutableInteger {
      int value;

      public MutableInteger() { this(0); }

      public MutableInteger(int value) { this.value = value; }

      public boolean equals(Object obj) {
	return
	  (obj instanceof MutableInteger) &&
	  (((MutableInteger)obj).value == value);
      }

      public int hashCode() {
        return Integer.hashCode(value);
      }
    }

    public void setGenericOpCode(String genericOpCode) {
      if (genericOpCode == null)
	throw new NullPointerException("genericOpCode");
      genericOpCodeBits = new int[genericOpCode.length()];
      HashMap<Integer,MutableInteger> vars = new HashMap<Integer,MutableInteger>();
      // vars.get((char code of some variable) minus code('a')) maps
      // to bit size of that variable.
      for (int i = 0; i < genericOpCode.length(); i++) {
	Character varName = new Character(genericOpCode.charAt(i));
	if (varName.charValue() == '0')
	  genericOpCodeBits[i] = 0;
	else if (varName.charValue() == '1')
	  genericOpCodeBits[i] = 1;
	else if ((varName.charValue() >= 'a') &&
		 (varName.charValue() <= 'z')) {
	  genericOpCodeBits[i] = varName.charValue() - (int)'a' + 2;
	  Integer key = new Integer(varName.charValue() - (int)'a');
	  MutableInteger mutableInteger = vars.get(key);
	  if (mutableInteger == null)
	    vars.put(key, new MutableInteger(1));
	  else
	    mutableInteger.value++;
	} else
	  throw new InternalError("bad generic opcode: " + genericOpCode);
      }
      varIndex = new int[vars.size()];
      varSize = new int[vars.size()];
      Iterator<Integer> indexIterator = vars.keySet().iterator();
      for (int i = 0; i < vars.size(); i++) {
	Integer index = indexIterator.next();
	varIndex[i] = index.intValue();
	varSize[i] = (vars.get(index)).value;
      }
      this.genericOpCode = genericOpCode;
      byteLength = genericOpCode.length() >>> 3;
    }

    public void setDefaultClockPeriods(int defaultClockPeriods) {
      if (defaultClockPeriods < 0)
	throw new InternalError("bad clock periods: " + defaultClockPeriods);
      this.defaultClockPeriods = defaultClockPeriods;
    }

    public void setAltClockPeriods(int altClockPeriods) {
      if (altClockPeriods < 0)
	throw new InternalError("bad clock periods: " + altClockPeriods);
      this.altClockPeriods = altClockPeriods;
    }

    private int swapEndianShort(int value) {
      return
	((value & 0xff) << 8) | ((value >>>  8) & 0xff);
    }

    private int swapEndianInt(int value) {
      return
	((value & 0x000000ff) << 24) |
	((value & 0x0000ff00) << 8) |
	((value & 0x00ff0000) >>> 8) |
	((value & 0xff000000) >>> 24);
    }

    private void swapEndiansOnAllArgs(Arguments args) {
      for (int i = 0; i < varIndex.length; i++) {
	int size = varSize[i];
	if (size > 8) {
	  int index = varIndex[i];
	  int value = args.getArg(index);
	  int swappedValue =
	    (size > 16) ? swapEndianInt(value) : swapEndianShort(value);
	  args.setArg(index, swappedValue);
	}
      }
    }

    private void addActionsForArgs(DecodeCompletionActions actions,
                                   HashMap<Integer, Integer> args) {
      for (Integer varIndex : args.keySet()) {
        actions.addConstant(varIndex, args.get(varIndex));
      }
    }

    /**
     * This is used during initialization to compute the decode tables
     * for fast operation lookup.
     *
     * @param code The starting byte(s) of some op-code.
     */
    public DecodeCompletionActions tryMatch(int[] code) {
      DecodeCompletionActions actions = new DecodeCompletionActions();
      HashMap<Integer, Integer> args = new HashMap<Integer, Integer>();
      int codeIndex = 0;
      int concreteOpCodeByte = 0;

      for (int i = 0; i < genericOpCodeBits.length; i++) {
	if ((i & 0x7) == 0)
	  if (codeIndex < code.length)
	    concreteOpCodeByte = code[codeIndex++];
	  else { // no more bits in code to verify
            addActionsForArgs(actions, args);
	    return actions; // TODO: check: resolution != null
          }
        int genericOpCodeBit = genericOpCodeBits[i];
	int concreteOpCodeBit = concreteOpCodeByte & SET_MASK[7 - (i & 0x7)];
	switch (genericOpCodeBit) {
	  case 0: // '0'
	    if (concreteOpCodeBit != 0) {
	      return null;
	    }
	    break;
	  case 1: // '1'
	    if (concreteOpCodeBit == 0) {
	      return null;
	    }
	    break;
	  default: // 'a'..'z'
	    int argValue;
            if (args.containsKey(genericOpCodeBit - 2)) {
              argValue = args.get(genericOpCodeBit - 2);
            } else {
              argValue = 0;
            }
	    argValue <<= 1;
	    if (concreteOpCodeBit != 0)
	      argValue |= 0x1;
	    args.put(genericOpCodeBit - 2, argValue);
	    // continue
	    break;
	}
      }

      // Now that we have all args, check that they are not out of
      // range.
      for (int i = 0; i < varIndex.length; i++) {
        if (!args.containsKey(varIndex[i])) {
          return null;
        }
      }

      /* TODO: Assertion:
      if (createConcreteMnemonic(args) == null) {
        return null;
      }
      */

      // no more bits in genericOpCodeBits to verify
      boolean matchesLength = codeIndex++ == code.length;
      if (!matchesLength) {
        return null;
      }

      addActionsForArgs(actions, args);
      return actions; // TODO: check: resolution != null
    }

    public void compileDynArgsDecoding(DecodeCompletionActions actions,
                                       int precompiledBytes) {
      int genericOpCodeBytes = genericOpCodeBits.length / 8;
      for (int instructionByte = precompiledBytes;
           instructionByte < genericOpCodeBytes; instructionByte++) {
        compileDynArgsDecodingForByte(actions, instructionByte);
      }
    }

    private void compileDynArgsDecodingForByte(DecodeCompletionActions actions,
                                               int instructionByte) {
      int bitStart = instructionByte * 8;
      int bitEnd = bitStart + 8;
      int prevGenericOpCodeBit = 0;
      int contiguousArgBits = 0;
      for (int bit = bitStart; bit < bitEnd; bit++) {
        int genericOpCodeBit = genericOpCodeBits[bit];
	switch (genericOpCodeBit) {
	  case 0: // '0'
	  case 1: // '1'
            contiguousArgBits = 0;
            break; // not a dynamic argument bit => skip
	  default: // 'a'..'z'
            if (contiguousArgBits == 0) {
              contiguousArgBits++;
            } else if (prevGenericOpCodeBit == genericOpCodeBit) {
              contiguousArgBits++;
            } else {
              addDynArgsDecodingAction(actions, instructionByte,
                                       genericOpCodeBit - 2,
                                       bit & 0x7,
                                       contiguousArgBits);
              contiguousArgBits = 0;
            }
        }
        prevGenericOpCodeBit = genericOpCodeBit;
      }
      if (contiguousArgBits > 0) {
        addDynArgsDecodingAction(actions, instructionByte,
                                 prevGenericOpCodeBit - 2,
                                 0x7,
                                 contiguousArgBits);
      }
    }

    private void addDynArgsDecodingAction(DecodeCompletionActions actions,
                                          int opCodeByteIndex,
                                          int varIndex,
                                          int bitEnd, int bitSize) {
      int opCodeByteShiftRight = 7 - bitEnd;
      actions.appendBitsToVar(varIndex, bitSize, opCodeByteIndex,
                              opCodeByteShiftRight);
    }

    protected int getArg(Arguments args, char argName) {
      return args.getArg((int)argName - (int)'a');
    }

    private void execute(Arguments args) {
      args.useDefaultClockPeriods = true;
      execute0(args);
    }

    public abstract void execute0(Arguments args);

    private String resolve(String funcName, int argValue) {
      String result = null;
      for (int i = 0; (i < FUNCTIONS.length) && (result == null); i++)
	if (FUNCTIONS[i].getName().equals(funcName))
	  result = FUNCTIONS[i].evaluate(argValue);
      return result;
    }

    public String createConcreteMnemonic(Arguments args) {
      StringBuffer out = new StringBuffer();
      int pos = 0;
      while (pos < genericMnemonic.length()) {
	char ch = genericMnemonic.charAt(pos);
	if (ch != '\\') {
	  out.append(ch);
	  pos++;
	} else { // resolve expression
	  pos++;
	  int funcStart = pos;
	  int funcEnd = genericMnemonic.indexOf('[', pos);
	  int argStart, argEnd;
	  if (funcEnd < 0) {
	    pos = genericMnemonic.length();
	    funcEnd = pos;
	    argStart = 0;
	    argEnd = 0;
	  } else {
	    argStart = funcEnd + 1;
	    argEnd = genericMnemonic.indexOf(']', argStart);
	    if (argEnd < 0)
	      throw new InternalError("missing ']' in mnemonic " +
				      genericMnemonic);
	    pos = argEnd + 1;
	  }
	  String funcName = genericMnemonic.substring(funcStart, funcEnd);
	  String argName = genericMnemonic.substring(argStart, argEnd);
	  if (argName.length() != 1)
	    throw new InternalError("one-letter argument name expected");
	  int argValue = getArg(args, argName.charAt(0));
	  String resolved = resolve(funcName, argValue);
	  if (resolved == null) {
	    // error: argument out of range => bad opcode for this mnemonic
	    return null;
	  }
	  out.append(resolved);
	}
      }
      return out.toString();
    }

    public String toString() {
      return genericMnemonic;
    }
  }

  private final GenericOperation[] OPERATION_SET =
  new GenericOperation[] {
    new GenericOperation() {
      public void init() {
	init("ADC \\VAL8[d]",
	     "11001110dddddddd",
	     7, 0);
      }
      public void execute0(Arguments args) {
	regA.setValue(doADC8(regA.getValue(), getArg(args, 'd')));
      }
    },
    new GenericOperation() {
      public void init() {
	init("ADC (HL)",
	     "10001110",
	     7, 0);
      }
      public void execute0(Arguments args) {
	regA.setValue(doADC8(regA.getValue(), indirectRegHL.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("ADC HL,\\REG16[x]",
	     "1110110101xx1010",
	     15, 0);
      }
      public void execute0(Arguments args) {
	regHL.setValue(doADC16(regHL.getValue(),
			       REG16[getArg(args, 'x')].getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("ADC (IX\\DISP8[d])",
	     "1101110110001110dddddddd",
	     19, 0);
      }
      public void execute0(Arguments args) {
	indirectIXDisp8.setDisp8(getArg(args, 'd'));
	regA.setValue(doADC8(regA.getValue(), indirectIXDisp8.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("ADC (IY\\DISP8[d])",
	     "1111110110001110dddddddd",
	     19, 0);
      }
      public void execute0(Arguments args) {
	indirectIYDisp8.setDisp8(getArg(args, 'd'));
	regA.setValue(doADC8(regA.getValue(), indirectIYDisp8.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("ADC \\DREG8[x]",
	     "10001xxx",
	     4, 0);
      }
      public void execute0(Arguments args) {
	regA.setValue(doADC8(regA.getValue(), REG8[getArg(args, 'x')].
			     getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("ADD \\VAL8[d]",
	     "11000110dddddddd",
	     7, 0);
      }
      public void execute0(Arguments args) {
	regA.setValue(doADD8(regA.getValue(), getArg(args, 'd')));
      }
    },
    new GenericOperation() {
      public void init() {
	init("ADD (HL)",
	     "10000110",
	     7, 0);
      }
      public void execute0(Arguments args) {
	regA.setValue(doADD8(regA.getValue(), indirectRegHL.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("ADD HL,\\REG16[x]",
	     "00xx1001",
	     11, 0);
      }
      public void execute0(Arguments args) {
	regHL.setValue(doADD16(regHL.getValue(),
			       REG16[getArg(args, 'x')].getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("ADD (IX\\DISP8[d])",
	     "1101110110000110dddddddd",
	     19, 0);
      }
      public void execute0(Arguments args) {
	indirectIXDisp8.setDisp8(getArg(args, 'd'));
	regA.setValue(doADD8(regA.getValue(), indirectIXDisp8.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("ADD IX,\\QREG16[x]",
	     "1101110100xx1001",
	     15, 0);
      }
      public void execute0(Arguments args) {
	regIX.setValue(doADD16(regIX.getValue(),
			       QREG16[getArg(args, 'x')].getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("ADD (IY\\DISP8[d])",
	     "1111110110000110dddddddd",
	     19, 0);
      }
      public void execute0(Arguments args) {
	indirectIYDisp8.setDisp8(getArg(args, 'd'));
	regA.setValue(doADD8(regA.getValue(), indirectIYDisp8.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("ADD IY,\\RREG16[x]",
	     "1111110100xx1001",
	     15, 0);
      }
      public void execute0(Arguments args) {
	regIY.setValue(doADD16(regIY.getValue(),
			       RREG16[getArg(args, 'x')].getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("ADD \\DREG8[x]",
	     "10000xxx",
	     4, 0);
      }
      public void execute0(Arguments args) {
	regA.setValue(doADD8(regA.getValue(), REG8[getArg(args, 'x')].
			     getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("AND \\VAL8[d]",
	     "11100110dddddddd",
	     7, 0);
      }
      public void execute0(Arguments args) {
	doAND(regA, getArg(args, 'd'));
      }
    },
    new GenericOperation() {
      public void init() {
	init("AND (HL)",
	     "10100110",
	     7, 0);
      }
      public void execute0(Arguments args) {
	doAND(regA, indirectRegHL.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("AND (IX\\DISP8[d])",
	     "1101110110100110dddddddd",
	     19, 0);
      }
      public void execute0(Arguments args) {
	indirectIXDisp8.setDisp8(getArg(args, 'd'));
	doAND(regA, indirectIXDisp8.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("AND (IY\\DISP8[d])",
	     "1111110110100110dddddddd",
	     19, 0);
      }
      public void execute0(Arguments args) {
	indirectIYDisp8.setDisp8(getArg(args, 'd'));
	doAND(regA, indirectIYDisp8.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("AND \\DREG8[x]",
	     "10100xxx",
	     4, 0);
      }
      public void execute0(Arguments args) {
	doAND(regA, REG8[getArg(args, 'x')].getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("BIT \\VAL3[b],(HL)",
	     "1100101101bbb110",
	     12, 0);
      }
      public void execute0(Arguments args) {
	doBIT(indirectRegHL.getValue(), getArg(args, 'b'));
      }
    },
    new GenericOperation() {
      public void init() {
	init("BIT \\VAL3[b],(IX\\DISP8[d])",
	     "1101110111001011dddddddd01bbb110",
	     20, 0);
      }
      public void execute0(Arguments args) {
	indirectIXDisp8.setDisp8(getArg(args, 'd'));
	doBIT(indirectIXDisp8.getValue(), getArg(args, 'b'));
      }
    },
    new GenericOperation() {
      public void init() {
	init("BIT \\VAL3[b],(IY\\DISP8[d])",
	     "1111110111001011dddddddd01bbb110",
	     20, 0);
      }
      public void execute0(Arguments args) {
	indirectIYDisp8.setDisp8(getArg(args, 'd'));
	doBIT(indirectIYDisp8.getValue(), getArg(args, 'b'));
      }
    },
    new GenericOperation() {
      public void init() {
	init("BIT \\VAL3[b],\\DREG8[x]",
	     "1100101101bbbxxx",
	     8, 0);
      }
      public void execute0(Arguments args) {
	doBIT(REG8[getArg(args, 'x')].getValue(), getArg(args, 'b'));
      }
    },
    new GenericOperation() {
      public void init() {
	init("CALL \\ADR16[x]",
	     "11001101xxxxxxxxxxxxxxxx",
	     17, 0);
      }
      public void execute0(Arguments args) {
	doPUSH(regPC.getValue());
	regPC.setValue(getArg(args, 'x'));
      }
    },
    new GenericOperation() {
      public void init() {
	init("CALL \\COND[c],\\ADR16[x]",
	     "11ccc100xxxxxxxxxxxxxxxx",
	     10, 17);
      }
      public void execute0(Arguments args) {
	if (COND[getArg(args, 'c')].isTrue())
	{
	  doPUSH(regPC.getValue());
	  regPC.setValue(getArg(args, 'x'));
	}
	else
	  args.useDefaultClockPeriods = false;
      }
    },
    new GenericOperation() {
      public void init() {
	init("CCF",
	     "00111111",
	     4, 0);
      }
      public void execute0(Arguments args) {
	doCCF();
      }
    },
    new GenericOperation() {
      public void init() {
	init("CP \\VAL8[d]",
	     "11111110dddddddd",
	     7, 0);
      }
      public void execute0(Arguments args) {
	doCP(regA.getValue(), getArg(args, 'd'));
      }
    },
    new GenericOperation() {
      public void init() {
	init("CP (HL)",
	     "10111110",
	     7, 0);
      }
      public void execute0(Arguments args) {
	doCP(regA.getValue(), indirectRegHL.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("CP (IX\\DISP8[d])",
	     "1101110110111110dddddddd",
	     19, 0);
      }
      public void execute0(Arguments args) {
	indirectIXDisp8.setDisp8(getArg(args, 'd'));
	doCP(regA.getValue(), indirectIXDisp8.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("CP (IY\\DISP8[d])",
	     "1111110110111110dddddddd",
	     19, 0);
      }
      public void execute0(Arguments args) {
	indirectIYDisp8.setDisp8(getArg(args, 'd'));
	doCP(regA.getValue(), indirectIYDisp8.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("CP \\DREG8[x]",
	     "10111xxx",
	     4, 0);
      }
      public void execute0(Arguments args) {
	doCP(regA.getValue(), REG8[getArg(args, 'x')].getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("CPD",
	     "1110110110101001",
	     16, 0);
      }
      public void execute0(Arguments args) {
	doCPD();
      }
    },
    new GenericOperation() {
      public void init() {
	init("CPDR",
	     "1110110110111001",
	     21, 16);
      }
      public void execute0(Arguments args) {
	doCPD();
	if (regBC.getValue() != 0x0000)
	  regPC.setValue((regPC.getValue() + 0xfffe) & 0xffff);
	else
	  args.useDefaultClockPeriods = false;
      }
    },
    new GenericOperation() {
      public void init() {
	init("CPI",
	     "1110110110100001",
	     16, 0);
      }
      public void execute0(Arguments args) {
	doCPI();
      }
    },
    new GenericOperation() {
      public void init() {
	init("CPIR",
	     "1110110110110001",
	     21, 16);
      }
      public void execute0(Arguments args) {
	doCPI();
	if (regBC.getValue() != 0x0000)
	  regPC.setValue((regPC.getValue() + 0xfffe) & 0xffff);
	else
	  args.useDefaultClockPeriods = false;
      }
    },
    new GenericOperation() {
      public void init() {
	init("CPL",
	     "00101111",
	     4, 0);
      }
      public void execute0(Arguments args) {
	doCPL(regA);
      }
    },
    new GenericOperation() {
      public void init() {
	init("DAA",
	     "00100111",
	     4, 0);
      }
      public void execute0(Arguments args) {
	doDAA(regA);
      }
    },
    new GenericOperation() {
      public void init() {
	init("DEC IX",
	     "1101110100101011",
	     10, 0);
      }
      public void execute0(Arguments args) {
	doDEC16(regIX);
      }
    },
    new GenericOperation() {
      public void init() {
	init("DEC (IX\\DISP8[d])",
	     "1101110100110101dddddddd",
	     23, 0);
      }
      public void execute0(Arguments args) {
	indirectIXDisp8.setDisp8(getArg(args, 'd'));
	doDEC8(indirectIXDisp8);
      }
    },
    new GenericOperation() {
      public void init() {
	init("DEC IY",
	     "1111110100101011",
	     10, 0);
      }
      public void execute0(Arguments args) {
	doDEC16(regIY);
      }
    },
    new GenericOperation() {
      public void init() {
	init("DEC (IY\\DISP8[d])",
	     "1111110100110101dddddddd",
	     23, 0);
      }
      public void execute0(Arguments args) {
	indirectIYDisp8.setDisp8(getArg(args, 'd'));
	doDEC8(indirectIYDisp8);
      }
    },
    new GenericOperation() {
      public void init() {
	init("DEC \\REG16[x]",
	     "00xx1011",
	     6, 0);
      }
      public void execute0(Arguments args) {
	doDEC16(REG16[getArg(args, 'x')]);
      }
    },
    new GenericOperation() {
      public void init() {
	init("DEC \\REG8[x]",
	     "00xxx101",
	     4, 0);
      }
      public void execute0(Arguments args) {
	doDEC8(REG8[getArg(args, 'x')]);
      }
    },
    new GenericOperation() {
      public void init() {
	init("DI",
	     "11110011",
	     4, 0);
      }
      public void execute0(Arguments args) {
	irq_enabled = false;
      }
    },
    new GenericOperation() {
      public void init() {
	init("DJNZ \\REL8[r]",
	     "00010000rrrrrrrr",
	     8, 13);
      }
      public void execute0(Arguments args) {
	regB.decrement();
	if (regB.getValue() != 0x00)
	  regPC.setValue((regPC.getValue() + (byte)getArg(args, 'r')) & 0xffff);
	else
	  args.useDefaultClockPeriods = false;
      }
    },
    new GenericOperation() {
      public void init() {
	init("EI",
	     "11111011",
	     4, 0);
      }
      public void execute0(Arguments args) {
	irq_to_be_enabled = true;
      }
    },
    new GenericOperation() {
      public void init() {
	init("EX AF,AF'",
	     "00001000",
	     4, 0);
      }
      public void execute0(Arguments args) {
	int value = regAF.getValue();
	regAF.setValue(regAF_.getValue());
	regAF_.setValue(value);
      }
    },
    new GenericOperation() {
      public void init() {
	init("EX DE,HL",
	     "11101011",
	     4, 0);
      }
      public void execute0(Arguments args) {
	int value = regDE.getValue();
	regDE.setValue(regHL.getValue());
	regHL.setValue(value);
      }
    },
    new GenericOperation() {
      public void init() {
	init("EX (SP),HL",
	     "11100011",
	     19, 0);
      }
      public void execute0(Arguments args) {
        int address = regSP.getValue();
	int value = memory.readShort(address, wallClockTime);
	memory.writeShort(address, regHL.getValue(), wallClockTime);
	regHL.setValue(value);
      }
    },
    new GenericOperation() {
      public void init() {
	init("EX (SP),IX",
	     "1101110111100011",
	     23, 0);
      }
      public void execute0(Arguments args) {
        int address = regSP.getValue();
	int value = memory.readShort(address, wallClockTime);
	memory.writeShort(address, regIX.getValue(), wallClockTime);
	regIX.setValue(value);
      }
    },
    new GenericOperation() {
      public void init() {
	init("EX (SP),IY",
	     "1111110111100011",
	     23, 0);
      }
      public void execute0(Arguments args) {
        int address = regSP.getValue();
	int value = memory.readShort(address, wallClockTime);
	memory.writeShort(address, regIY.getValue(), wallClockTime);
	regIY.setValue(value);
      }
    },
    new GenericOperation() {
      public void init() {
	init("EXX",
	     "11011001",
	     4, 0);
      }
      public void execute0(Arguments args) {
	int value;
	value = regBC.getValue();
	regBC.setValue(regBC_.getValue());
	regBC_.setValue(value);
	value = regDE.getValue();
	regDE.setValue(regDE_.getValue());
	regDE_.setValue(value);
	value = regHL.getValue();
	regHL.setValue(regHL_.getValue());
	regHL_.setValue(value);
      }
    },
    new GenericOperation() {
      public void init() {
	init("HALT",
	     "01110110",
	     4, 0);
      }
      public void execute0(Arguments args) {
	regPC.decrement();
      }
    },
    new GenericOperation() {
      public void init() {
	init("IM 0",
	     "1110110101000110",
	     8, 0);
      }
      public void execute0(Arguments args) {
	regIM.setValue(INTR_MODE_0);
      }
    },
    new GenericOperation() {
      public void init() {
	init("IM 1",
	     "1110110101010110",
	     8, 0);
      }
      public void execute0(Arguments args) {
	regIM.setValue(INTR_MODE_1);
      }
    },
    new GenericOperation() {
      public void init() {
	init("IM 2",
	     "1110110101011110",
	     8, 0);
      }
      public void execute0(Arguments args) {
	regIM.setValue(INTR_MODE_2);
      }
    },
    new GenericOperation() {
      public void init() {
	init("IN A,\\VAL8[p]",
	     "11011011pppppppp",
	     10, 0);
      }
      public void execute0(Arguments args) {
        int portAddress = (regA.getValue() << 8) | getArg(args, 'p');
	regA.setValue(io.readByte(portAddress, wallClockTime));
      }
    },
    new GenericOperation() {
      public void init() {
	init("IN (C)",
	     "1110110101110000",
	     11, 0);
      }
      public void execute0(Arguments args) {
	doIN(regBC.getValue()); // result of doIN() intentionally
                                // ignored
      }
    },
    new GenericOperation() {
      public void init() {
	init("IN \\DREG8[x],(C)",
	     "1110110101xxx000",
	     11, 0);
      }
      public void execute0(Arguments args) {
	DREG8[getArg(args, 'x')].setValue(doIN(regBC.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("INC IX",
	     "1101110100100011",
	     10, 0);
      }
      public void execute0(Arguments args) {
	doINC16(regIX);
      }
    },
    new GenericOperation() {
      public void init() {
	init("INC (IX\\DISP8[d])",
	     "1101110100110100dddddddd",
	     23, 0);
      }
      public void execute0(Arguments args) {
	indirectIXDisp8.setDisp8(getArg(args, 'd'));
	doINC8(indirectIXDisp8);
      }
    },
    new GenericOperation() {
      public void init() {
	init("INC IY",
	     "1111110100100011",
	     10, 0);
      }
      public void execute0(Arguments args) {
	doINC16(regIY);
      }
    },
    new GenericOperation() {
      public void init() {
	init("INC (IY\\DISP8[d])",
	     "1111110100110100dddddddd",
	     23, 0);
      }
      public void execute0(Arguments args) {
	indirectIYDisp8.setDisp8(getArg(args, 'd'));
	doINC8(indirectIYDisp8);
      }
    },
    new GenericOperation() {
      public void init() {
	init("INC \\REG16[x]",
	     "00xx0011",
	     6, 0);
      }
      public void execute0(Arguments args) {
	doINC16(REG16[getArg(args, 'x')]);
      }
    },
    new GenericOperation() {
      public void init() {
	init("INC \\REG8[x]",
	     "00xxx100",
	     4, 0);
      }
      public void execute0(Arguments args) {
	doINC8(REG8[getArg(args, 'x')]);
      }
    },
    new GenericOperation() {
      public void init() {
	init("IND",
	     "1110110110101010",
	     15, 0);
      }
      public void execute0(Arguments args) {
	doIND();
      }
    },
    new GenericOperation() {
      public void init() {
	init("INDR",
	     "1110110110111010",
	     20, 15);
      }
      public void execute0(Arguments args) {
	doIND();
	if (condNZ.isTrue())
	  regPC.setValue((regPC.getValue() + 0xfffe) & 0xffff);
	else
	  args.useDefaultClockPeriods = false;
      }
    },
    new GenericOperation() {
      public void init() {
	init("INI",
	     "1110110110100010",
	     15, 0);
      }
      public void execute0(Arguments args) {
	doINI();
      }
    },
    new GenericOperation() {
      public void init() {
	init("INIR",
	     "1110110110110010",
	     20, 15);
      }
      public void execute0(Arguments args) {
	doINI();
	if (condNZ.isTrue())
	  regPC.setValue((regPC.getValue() + 0xfffe) & 0xffff);
	else
	  args.useDefaultClockPeriods = false;
      }
    },
    new GenericOperation() {
      public void init() {
	init("JP \\ADR16[x]",
	     "11000011xxxxxxxxxxxxxxxx",
	     10, 0);
      }
      public void execute0(Arguments args) {
	regPC.setValue(getArg(args, 'x'));
      }
    },
    new GenericOperation() {
      public void init() {
	init("JP \\COND[c],\\ADR16[x]",
	     "11ccc010xxxxxxxxxxxxxxxx",
	     10, 10);
      }
      public void execute0(Arguments args) {
	if (COND[getArg(args, 'c')].isTrue())
	  regPC.setValue(getArg(args, 'x'));
	else
	  args.useDefaultClockPeriods = false;
      }
    },
    new GenericOperation() {
      public void init() {
	init("JP (HL)",
	     "11101001",
	     4, 0);
      }
      public void execute0(Arguments args) {
	regPC.setValue(regHL.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("JP (IX)",
	     "1101110111101001",
	     8, 0);
      }
      public void execute0(Arguments args) {
	regPC.setValue(regIX.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("JP (IY)",
	     "1111110111101001",
	     8, 0);
      }
      public void execute0(Arguments args) {
	regPC.setValue(regIY.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("JR \\REL8[r]",
	     "00011000rrrrrrrr",
	     12, 0);
      }
      public void execute0(Arguments args) {
	regPC.setValue((regPC.getValue() + (byte)getArg(args, 'r')) & 0xffff);
      }
    },
    new GenericOperation() {
      public void init() {
	init("JR NZ,\\REL8[r]",
	     "00100000rrrrrrrr",
	     7, 12);
      }
      public void execute0(Arguments args) {
	if (condNZ.isTrue())
	  regPC.setValue((regPC.getValue() + (byte)getArg(args, 'r')) & 0xffff);
	else
	  args.useDefaultClockPeriods = false;
      }
    },
    new GenericOperation() {
      public void init() {
	init("JR Z,\\REL8[r]",
	     "00101000rrrrrrrr",
	     7, 12);
      }
      public void execute0(Arguments args) {
	if (condZ.isTrue())
	  regPC.setValue((regPC.getValue() + (byte)getArg(args, 'r')) & 0xffff);
	else
	  args.useDefaultClockPeriods = false;
      }
    },
    new GenericOperation() {
      public void init() {
	init("JR NC,\\REL8[r]",
	     "00110000rrrrrrrr",
	     7, 12);
      }
      public void execute0(Arguments args) {
	if (condNC.isTrue())
	  regPC.setValue((regPC.getValue() + (byte)getArg(args, 'r')) & 0xffff);
	else
	  args.useDefaultClockPeriods = false;
      }
    },
    new GenericOperation() {
      public void init() {
	init("JR C,\\REL8[r]",
	     "00111000rrrrrrrr",
	     7, 12);
      }
      public void execute0(Arguments args) {
	if (condC.isTrue())
	  regPC.setValue((regPC.getValue() + (byte)getArg(args, 'r')) & 0xffff);
	else
	  args.useDefaultClockPeriods = false;
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD A,(\\ADR16[x])",
	     "00111010xxxxxxxxxxxxxxxx",
	     13, 0);
      }
      public void execute0(Arguments args) {
	regA.setValue(memory.readByte(getArg(args, 'x'), wallClockTime));
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD A,(BC)",
	     "00001010",
	     7, 0);
      }
      public void execute0(Arguments args) {
	regA.setValue(memory.readByte(regBC.getValue(), wallClockTime));
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD A,(DE)",
	     "00011010",
	     7, 0);
      }
      public void execute0(Arguments args) {
	regA.setValue(memory.readByte(regDE.getValue(), wallClockTime));
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD A,I",
	     "1110110101010111",
	     9, 0);
      }
      public void execute0(Arguments args) {
	doLDAI();
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD A,R",
	     "1110110101011111",
	     9, 0);
      }
      public void execute0(Arguments args) {
	doLDAR();
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD (\\ADR16[x]),A",
	     "00110010xxxxxxxxxxxxxxxx",
	     13, 0);
      }
      public void execute0(Arguments args) {
	memory.writeByte(getArg(args, 'x'), regA.getValue(), wallClockTime);
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD (\\ADR16[y]),\\REG16[x]",
	     "1110110101xx0011yyyyyyyyyyyyyyyy",
	     20, 0);
      }
      public void execute0(Arguments args) {
	memory.writeShort(getArg(args, 'y'),
                          REG16[getArg(args, 'x')].getValue(), wallClockTime);
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD (\\ADR16[x]),HL",
	     "00100010xxxxxxxxxxxxxxxx",
	     16, 0);
      }
      public void execute0(Arguments args) {
	memory.writeShort(getArg(args, 'x'), regHL.getValue(), wallClockTime);
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD (\\ADR16[x]),IX",
	     "1101110100100010xxxxxxxxxxxxxxxx",
	     20, 0);
      }
      public void execute0(Arguments args) {
	memory.writeShort(getArg(args, 'x'), regIX.getValue(), wallClockTime);
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD (\\ADR16[x]),IY",
	     "1111110100100010xxxxxxxxxxxxxxxx",
	     20, 0);
      }
      public void execute0(Arguments args) {
	memory.writeShort(getArg(args, 'x'), regIY.getValue(), wallClockTime);
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD (BC),A",
	     "00000010",
	     7, 0);
      }
      public void execute0(Arguments args) {
	memory.writeByte(regBC.getValue(), regA.getValue(), wallClockTime);
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD (DE),A",
	     "00010010",
	     7, 0);
      }
      public void execute0(Arguments args) {
	memory.writeByte(regDE.getValue(), regA.getValue(), wallClockTime);
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD HL,(\\ADR16[x])",
	     "00101010xxxxxxxxxxxxxxxx",
	     16, 0);
      }
      public void execute0(Arguments args) {
	regHL.setValue(memory.readShort(getArg(args, 'x'), wallClockTime));
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD (HL),\\DREG8[x]",
	     "01110xxx",
	     7, 0);
      }
      public void execute0(Arguments args) {
	indirectRegHL.setValue(DREG8[getArg(args, 'x')].getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD I,A",
	     "1110110101000111",
	     9, 0);
      }
      public void execute0(Arguments args) {
	regI.setValue(regA.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD IX,(\\ADR16[x])",
	     "1101110100101010xxxxxxxxxxxxxxxx",
	     20, 0);
      }
      public void execute0(Arguments args) {
	regIX.setValue(memory.readShort(getArg(args, 'x'), wallClockTime));
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD IX,\\VAL16[d]",
	     "1101110100100001dddddddddddddddd",
	     14, 0);
      }
      public void execute0(Arguments args) {
	regIX.setValue(getArg(args, 'd'));
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD (IX\\DISP8[d]),\\VAL8[q]",
	     "1101110100110110ddddddddqqqqqqqq",
	     19, 0);
      }
      public void execute0(Arguments args) {
	indirectIXDisp8.setDisp8(getArg(args, 'd'));
	indirectIXDisp8.setValue(getArg(args, 'q'));
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD (IX\\DISP8[d]),\\DREG8[x]",
	     "1101110101110xxxdddddddd",
	     19, 0);
      }
      public void execute0(Arguments args) {
	indirectIXDisp8.setDisp8(getArg(args, 'd'));
	indirectIXDisp8.setValue(DREG8[getArg(args, 'x')].getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD IY,(\\ADR16[x])",
	     "1111110100101010xxxxxxxxxxxxxxxx",
	     20, 0);
      }
      public void execute0(Arguments args) {
	regIY.setValue(memory.readShort(getArg(args, 'x'), wallClockTime));
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD IY,\\VAL16[d]",
	     "1111110100100001dddddddddddddddd",
	     14, 0);
      }
      public void execute0(Arguments args) {
	regIY.setValue(getArg(args, 'd'));
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD (IY\\DISP8[d]),\\VAL8[q]",
	     "1111110100110110ddddddddqqqqqqqq",
	     19, 0);
      }
      public void execute0(Arguments args) {
	indirectIYDisp8.setDisp8(getArg(args, 'd'));
	indirectIYDisp8.setValue(getArg(args, 'q'));
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD (IY\\DISP8[d]),\\DREG8[x]",
	     "1111110101110xxxdddddddd",
	     19, 0);
      }
      public void execute0(Arguments args) {
	indirectIYDisp8.setDisp8(getArg(args, 'd'));
	indirectIYDisp8.setValue(DREG8[getArg(args, 'x')].getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD R,A",
	     "1110110101001111",
	     9, 0);
      }
      public void execute0(Arguments args) {
	regR.setValue(regA.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD \\REG8[x],\\VAL8[d]",
	     "00xxx110dddddddd",
	     7, 0);
      }
      public void execute0(Arguments args) {
	REG8[getArg(args, 'x')].setValue(getArg(args, 'd'));
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD \\DREG8[x],(HL)",
	     "01xxx110",
	     7, 0);
      }
      public void execute0(Arguments args) {
	DREG8[getArg(args, 'x')].setValue(indirectRegHL.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD \\DREG8[x],(IX\\DISP8[d])",
	     "1101110101xxx110dddddddd",
	     19, 0);
      }
      public void execute0(Arguments args) {
	indirectIXDisp8.setDisp8(getArg(args, 'd'));
	DREG8[getArg(args, 'x')].setValue(indirectIXDisp8.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD \\DREG8[x],(IY\\DISP8[d])",
	     "1111110101xxx110dddddddd",
	     19, 0);
      }
      public void execute0(Arguments args) {
	indirectIYDisp8.setDisp8(getArg(args, 'd'));
	DREG8[getArg(args, 'x')].setValue(indirectIYDisp8.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD \\DREG8[x],\\DREG8[y]",
	     "01xxxyyy",
	     4, 0);
      }
      public void execute0(Arguments args) {
	DREG8[getArg(args, 'x')].setValue(DREG8[getArg(args, 'y')].getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD \\REG16[x],(\\ADR16[y])",
	     "1110110101xx1011yyyyyyyyyyyyyyyy",
	     20, 0);
      }
      public void execute0(Arguments args) {
	REG16[getArg(args, 'x')].setValue(memory.readShort(getArg(args, 'y'),
                                                           wallClockTime));
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD \\REG16[x],\\VAL16[d]",
	     "00xx0001dddddddddddddddd",
	     10, 0);
      }
      public void execute0(Arguments args) {
	REG16[getArg(args, 'x')].setValue(getArg(args, 'd'));
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD SP,HL",
	     "11111001",
	     6, 0);
      }
      public void execute0(Arguments args) {
	regSP.setValue(regHL.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD SP,IX",
	     "1101110111111001",
	     10, 0);
      }
      public void execute0(Arguments args) {
	regSP.setValue(regIX.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD SP,IY",
	     "1111110111111001",
	     10, 0);
      }
      public void execute0(Arguments args) {
	regSP.setValue(regIY.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("LDD",
	     "1110110110101000",
	     16, 0);
      }
      public void execute0(Arguments args) {
	doLDD();
      }
    },
    new GenericOperation() {
      public void init() {
	init("LDDR",
	     "1110110110111000",
	     21, 16);
      }
      public void execute0(Arguments args) {
	doLDD();
	if (condPE.isTrue())
	  regPC.setValue((regPC.getValue() + 0xfffe) & 0xffff);
	else
	  args.useDefaultClockPeriods = false;
      }
    },
    new GenericOperation() {
      public void init() {
	init("LDI",
	     "1110110110100000",
	     16, 0);
      }
      public void execute0(Arguments args) {
	doLDI();
      }
    },
    new GenericOperation() {
      public void init() {
	init("LDIR",
	     "1110110110110000",
	     21, 16);
      }
      public void execute0(Arguments args) {
	doLDI();
	if (condPE.isTrue())
	  regPC.setValue((regPC.getValue() + 0xfffe) & 0xffff);
	else
	  args.useDefaultClockPeriods = false;
      }
    },
    new GenericOperation() {
      public void init() {
	init("NEG",
	     "1110110101000100",
	     8, 0);
      }
      public void execute0(Arguments args) {
	doNEG(regA);
      }
    },
    new GenericOperation() {
      public void init() {
	init("NOP",
	     "00000000",
	     4, 0);
      }
      public void execute0(Arguments args) {
	doNOP();
      }
    },
    new GenericOperation() {
      public void init() {
	init("OR \\VAL8[d]",
	     "11110110dddddddd",
	     7, 0);
      }
      public void execute0(Arguments args) {
	doOR(regA, getArg(args, 'd'));
      }
    },
    new GenericOperation() {
      public void init() {
	init("OR (HL)",
	     "10110110",
	     7, 0);
      }
      public void execute0(Arguments args) {
	doOR(regA, indirectRegHL.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("OR (IX\\DISP8[d])",
	     "1101110110110110dddddddd",
	     19, 0);
      }
      public void execute0(Arguments args) {
	indirectIXDisp8.setDisp8(getArg(args, 'd'));
	doOR(regA, indirectIXDisp8.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("OR (IY\\DISP8[d])",
	     "1111110110110110dddddddd",
	     19, 0);
      }
      public void execute0(Arguments args) {
	indirectIYDisp8.setDisp8(getArg(args, 'd'));
	doOR(regA, indirectIYDisp8.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("OR \\DREG8[x]",
	     "10110xxx",
	     4, 0);
      }
      public void execute0(Arguments args) {
	doOR(regA, REG8[getArg(args, 'x')].getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("OTDR",
	     "1110110110111011",
	     20, 15);
      }
      public void execute0(Arguments args) {
	doOUTD();
	if (condNZ.isTrue())
	  regPC.setValue((regPC.getValue() + 0xfffe) & 0xffff);
	else
	  args.useDefaultClockPeriods = false;
      }
    },
    new GenericOperation() {
      public void init() {
	init("OTIR",
	     "1110110110110011",
	     20, 15);
      }
      public void execute0(Arguments args) {
	doOUTI();
	if (condNZ.isTrue())
	  regPC.setValue((regPC.getValue() + 0xfffe) & 0xffff);
	else
	  args.useDefaultClockPeriods = false;
      }
    },
    new GenericOperation() {
      public void init() {
	init("OUT (C),\\REG8[x]",
	     "1110110101xxx001",
	     12, 0);
      }
      public void execute0(Arguments args) {
	io.writeByte(regBC.getValue(), REG8[getArg(args, 'x')].getValue(),
                     wallClockTime);
      }
    },
    new GenericOperation() {
      public void init() {
	init("OUT \\VAL8[p],A",
	     "11010011pppppppp",
	     11, 0);
      }
      public void execute0(Arguments args) {
        int regAValue = regA.getValue();
        int portAddress = (regAValue << 8) | getArg(args, 'p');
	io.writeByte(portAddress, regAValue, wallClockTime);
      }
    },
    new GenericOperation() {
      public void init() {
	init("OUTD",
	     "1110110110101011",
	     15, 0);
      }
      public void execute0(Arguments args) {
	doOUTD();
      }
    },
    new GenericOperation() {
      public void init() {
	init("OUTI",
	     "1110110110100011",
	     15, 0);
      }
      public void execute0(Arguments args) {
	doOUTI();
      }
    },
    new GenericOperation() {
      public void init() {
	init("POP IX",
	     "1101110111100001",
	     14, 0);
      }
      public void execute0(Arguments args) {
	regIX.setValue(doPOP());
      }
    },
    new GenericOperation() {
      public void init() {
	init("POP IY",
	     "1111110111100001",
	     14, 0);
      }
      public void execute0(Arguments args) {
	regIY.setValue(doPOP());
      }
    },
    new GenericOperation() {
      public void init() {
	init("POP \\PREG16[x]",
	     "11xx0001",
	     10, 0);
      }
      public void execute0(Arguments args) {
	PREG16[getArg(args, 'x')].setValue(doPOP());
      }
    },
    new GenericOperation() {
      public void init() {
	init("PUSH IX",
	     "1101110111100101",
	     15, 0);
      }
      public void execute0(Arguments args) {
	doPUSH(regIX.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("PUSH IY",
	     "1111110111100101",
	     15, 0);
      }
      public void execute0(Arguments args) {
	doPUSH(regIY.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("PUSH \\PREG16[x]",
	     "11xx0101",
	     11, 0);
      }
      public void execute0(Arguments args) {
	doPUSH(PREG16[getArg(args, 'x')].getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("RES \\VAL3[b],(HL)",
	     "1100101110bbb110",
	     15, 0);
      }
      public void execute0(Arguments args) {
	doRES(indirectRegHL, getArg(args, 'b'));
      }
    },
    new GenericOperation() {
      public void init() {
	init("RES \\VAL3[b],(IX\\DISP8[d])",
	     "1101110111001011dddddddd10bbb110",
	     23, 0);
      }
      public void execute0(Arguments args) {
	indirectIXDisp8.setDisp8(getArg(args, 'd'));
	doRES(indirectIXDisp8, getArg(args, 'b'));
      }
    },
    new GenericOperation() {
      public void init() {
	init("RES \\VAL3[b],(IY\\DISP8[d])",
	     "1111110111001011dddddddd10bbb110",
	     23, 0);
      }
      public void execute0(Arguments args) {
	indirectIYDisp8.setDisp8(getArg(args, 'd'));
	doRES(indirectIYDisp8, getArg(args, 'b'));
      }
    },
    new GenericOperation() {
      public void init() {
	init("RES \\VAL3[b],\\DREG8[x]",
	     "1100101110bbbxxx",
	     8, 0);
      }
      public void execute0(Arguments args) {
        Reg8 reg = REG8[getArg(args, 'x')];
	doRES(reg, getArg(args, 'b'));
      }
    },
    new GenericOperation() {
      public void init() {
	init("RET",
	     "11001001",
	     10, 0);
      }
      public void execute0(Arguments args) {
	regPC.setValue(doPOP());
      }
    },
    new GenericOperation() {
      public void init() {
	init("RET \\COND[c]",
	     "11ccc000",
	     5, 11);
      }
      public void execute0(Arguments args) {
	if (COND[getArg(args, 'c')].isTrue())
	  regPC.setValue(doPOP());
	else
	  args.useDefaultClockPeriods = false;
      }
    },
    new GenericOperation() {
      public void init() {
	init("RETI",
	     "1110110101001101",
	     14, 0);
      }
      public void execute0(Arguments args) {
	regPC.setValue(doPOP());
	irq_requested = false;
      }
    },
    new GenericOperation() {
      public void init() {
	init("RETN",
	     "1110110101000101",
	     14, 0);
      }
      public void execute0(Arguments args) {
	regPC.setValue(doPOP());
      }
    },
    new GenericOperation() {
      public void init() {
	init("RL (HL)",
	     "1100101100010110",
	     15, 0);
      }
      public void execute0(Arguments args) {
	doRL(indirectRegHL);
      }
    },
    new GenericOperation() {
      public void init() {
	init("RL (IX\\DISP8[d])",
	     "1101110111001011dddddddd00010110",
	     23, 0);
      }
      public void execute0(Arguments args) {
	indirectIXDisp8.setDisp8(getArg(args, 'd'));
	doRL(indirectIXDisp8);
      }
    },
    new GenericOperation() {
      public void init() {
	init("RL (IY\\DISP8[d])",
	     "1111110111001011dddddddd00010110",
	     23, 0);
      }
      public void execute0(Arguments args) {
	indirectIYDisp8.setDisp8(getArg(args, 'd'));
	doRL(indirectIYDisp8);
      }
    },
    new GenericOperation() {
      public void init() {
	init("RL \\DREG8[x]",
	     "1100101100010xxx",
	     8, 0);
      }
      public void execute0(Arguments args) {
	doRL(REG8[getArg(args, 'x')]);
      }
    },
    new GenericOperation() {
      public void init() {
	init("RLA",
	     "00010111",
	     4, 0);
      }
      public void execute0(Arguments args) {
	doRLA(regA);
      }
    },
    new GenericOperation() {
      public void init() {
	init("RLC (HL)",
	     "1100101100000110",
	     15, 0);
      }
      public void execute0(Arguments args) {
	doRLC(indirectRegHL);
      }
    },
    new GenericOperation() {
      public void init() {
	init("RLC (IX\\DISP8[d])",
	     "1101110111001011dddddddd00000110",
	     23, 0);
      }
      public void execute0(Arguments args) {
	indirectIXDisp8.setDisp8(getArg(args, 'd'));
	doRLC(indirectIXDisp8);
      }
    },
    new GenericOperation() {
      public void init() {
	init("RLC (IY\\DISP8[d])",
	     "1111110111001011dddddddd00000110",
	     23, 0);
      }
      public void execute0(Arguments args) {
	indirectIYDisp8.setDisp8(getArg(args, 'd'));
	doRLC(indirectIYDisp8);
      }
    },
    new GenericOperation() {
      public void init() {
	init("RLC \\DREG8[x]",
	     "1100101100000xxx",
	     8, 0);
      }
      public void execute0(Arguments args) {
	doRLC(REG8[getArg(args, 'x')]);
      }
    },
    new GenericOperation() {
      public void init() {
	init("RLCA",
	     "00000111",
	     4, 0);
      }
      public void execute0(Arguments args) {
	doRLCA(regA);
      }
    },
    new GenericOperation() {
      public void init() {
	init("RLD",
	     "1110110101101111",
	     18, 0);
      }
      public void execute0(Arguments args) {
	doRLD(indirectRegHL);
      }
    },
    new GenericOperation() {
      public void init() {
	init("RR (HL)",
	     "1100101100011110",
	     15, 0);
      }
      public void execute0(Arguments args) {
	doRR(indirectRegHL);
      }
    },
    new GenericOperation() {
      public void init() {
	init("RR (IX\\DISP8[d])",
	     "1101110111001011dddddddd00011110",
	     23, 0);
      }
      public void execute0(Arguments args) {
	indirectIXDisp8.setDisp8(getArg(args, 'd'));
	doRR(indirectIXDisp8);
      }
    },
    new GenericOperation() {
      public void init() {
	init("RR (IY\\DISP8[d])",
	     "1111110111001011dddddddd00011110",
	     23, 0);
      }
      public void execute0(Arguments args) {
	indirectIYDisp8.setDisp8(getArg(args, 'd'));
	doRR(indirectIYDisp8);
      }
    },
    new GenericOperation() {
      public void init() {
	init("RR \\DREG8[x]",
	     "1100101100011xxx",
	     8, 0);
      }
      public void execute0(Arguments args) {
	doRR(REG8[getArg(args, 'x')]);
      }
    },
    new GenericOperation() {
      public void init() {
	init("RRA",
	     "00011111",
	     4, 0);
      }
      public void execute0(Arguments args) {
	doRRA(regA);
      }
    },
    new GenericOperation() {
      public void init() {
	init("RRC (HL)",
	     "1100101100001110",
	     15, 0);
      }
      public void execute0(Arguments args) {
	doRRC(indirectRegHL);
      }
    },
    new GenericOperation() {
      public void init() {
	init("RRC (IX\\DISP8[d])",
	     "1101110111001011dddddddd00001110",
	     23, 0);
      }
      public void execute0(Arguments args) {
	indirectIXDisp8.setDisp8(getArg(args, 'd'));
	doRRC(indirectIXDisp8);
      }
    },
    new GenericOperation() {
      public void init() {
	init("RRC (IY\\DISP8[d])",
	     "1111110111001011dddddddd00001110",
	     23, 0);
      }
      public void execute0(Arguments args) {
	indirectIYDisp8.setDisp8(getArg(args, 'd'));
	doRRC(indirectIYDisp8);
      }
    },
    new GenericOperation() {
      public void init() {
	init("RRC \\DREG8[x]",
	     "1100101100001xxx",
	     8, 0);
      }
      public void execute0(Arguments args) {
	doRRC(REG8[getArg(args, 'x')]);
      }
    },
    new GenericOperation() {
      public void init() {
	init("RRCA",
	     "00001111",
	     4, 0);
      }
      public void execute0(Arguments args) {
	doRRCA(regA);
      }
    },
    new GenericOperation() {
      public void init() {
	init("RRD",
	     "1110110101100111",
	     18, 0);
      }
      public void execute0(Arguments args) {
	doRRD(indirectRegHL);
      }
    },
    new GenericOperation() {
      public void init() {
	init("RST \\RST[n]",
	     "11nnn111",
	     11, 0);
      }
      public void execute0(Arguments args) {
	doPUSH(regPC.getValue());
	regPC.setValue(getArg(args, 'n') << 3);
      }
    },
    new GenericOperation() {
      public void init() {
	init("SBC \\VAL8[d]",
	     "11011110dddddddd",
	     7, 0);
      }
      public void execute0(Arguments args) {
	regA.setValue(doSBC8(regA.getValue(), getArg(args, 'd')));
      }
    },
    new GenericOperation() {
      public void init() {
	init("SBC (HL)",
	     "10011110",
	     7, 0);
      }
      public void execute0(Arguments args) {
	regA.setValue(doSBC8(regA.getValue(), indirectRegHL.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("SBC HL,\\REG16[x]",
	     "1110110101xx0010",
	     15, 0);
      }
      public void execute0(Arguments args) {
	regHL.setValue(doSBC16(regHL.getValue(),
			       REG16[getArg(args, 'x')].getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("SBC (IX\\DISP8[d])",
	     "1101110110011110dddddddd",
	     19, 0);
      }
      public void execute0(Arguments args) {
	indirectIXDisp8.setDisp8(getArg(args, 'd'));
	regA.setValue(doSBC8(regA.getValue(), indirectIXDisp8.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("SBC (IY\\DISP8[d])",
	     "1111110110011110dddddddd",
	     19, 0);
      }
      public void execute0(Arguments args) {
	indirectIYDisp8.setDisp8(getArg(args, 'd'));
	regA.setValue(doSBC8(regA.getValue(), indirectIYDisp8.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("SBC \\DREG8[x]",
	     "10011xxx",
	     4, 0);
      }
      public void execute0(Arguments args) {
	regA.setValue(doSBC8(regA.getValue(), REG8[getArg(args, 'x')].
			     getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("SCF",
	     "00110111",
	     4, 0);
      }
      public void execute0(Arguments args) {
	doSCF();
      }
    },
    new GenericOperation() {
      public void init() {
	init("SET \\VAL3[b],(HL)",
	     "1100101111bbb110",
	     15, 0);
      }
      public void execute0(Arguments args) {
	doSET(indirectRegHL, getArg(args, 'b'));
      }
    },
    new GenericOperation() {
      public void init() {
	init("SET \\VAL3[b],(IX\\DISP8[d])",
	     "1101110111001011dddddddd11bbb110",
	     23, 0);
      }
      public void execute0(Arguments args) {
	indirectIXDisp8.setDisp8(getArg(args, 'd'));
	doSET(indirectIXDisp8, getArg(args, 'b'));
      }
    },
    new GenericOperation() {
      public void init() {
	init("SET \\VAL3[b],(IY\\DISP8[d])",
	     "1111110111001011dddddddd11bbb110",
	     23, 0);
      }
      public void execute0(Arguments args) {
	indirectIYDisp8.setDisp8(getArg(args, 'd'));
	doSET(indirectIYDisp8, getArg(args, 'b'));
      }
    },
    new GenericOperation() {
      public void init() {
	init("SET \\VAL3[b],\\DREG8[x]",
	     "1100101111bbbxxx",
	     8, 0);
      }
      public void execute0(Arguments args) {
        Reg8 reg = REG8[getArg(args, 'x')];
	doSET(reg, getArg(args, 'b'));
      }
    },
    new GenericOperation() {
      public void init() {
	init("SLA (HL)",
	     "1100101100100110",
	     15, 0);
      }
      public void execute0(Arguments args) {
	doSLA(indirectRegHL);
      }
    },
    new GenericOperation() {
      public void init() {
	init("SLA (IX\\DISP8[d])",
	     "1101110111001011dddddddd00100110",
	     23, 0);
      }
      public void execute0(Arguments args) {
	indirectIXDisp8.setDisp8(getArg(args, 'd'));
	doSLA(indirectIXDisp8);
      }
    },
    new GenericOperation() {
      public void init() {
	init("SLA (IY\\DISP8[d])",
	     "1111110111001011dddddddd00100110",
	     23, 0);
      }
      public void execute0(Arguments args) {
	indirectIYDisp8.setDisp8(getArg(args, 'd'));
	doSLA(indirectIYDisp8);
      }
    },
    new GenericOperation() {
      public void init() {
	init("SLA \\DREG8[x]",
	     "1100101100100xxx",
	     8, 0);
      }
      public void execute0(Arguments args) {
	doSLA(REG8[getArg(args, 'x')]);
      }
    },
    new GenericOperation() {
      public void init() {
	init("SRA (HL)",
	     "1100101100101110",
	     15, 0);
      }
      public void execute0(Arguments args) {
	doSRA(indirectRegHL);
      }
    },
    new GenericOperation() {
      public void init() {
	init("SRA (IX\\DISP8[d])",
	     "1101110111001011dddddddd00101110",
	     23, 0);
      }
      public void execute0(Arguments args) {
	indirectIXDisp8.setDisp8(getArg(args, 'd'));
	doSRA(indirectIXDisp8);
      }
    },
    new GenericOperation() {
      public void init() {
	init("SRA (IY\\DISP8[d])",
	     "1111110111001011dddddddd00101110",
	     23, 0);
      }
      public void execute0(Arguments args) {
	indirectIYDisp8.setDisp8(getArg(args, 'd'));
	doSRA(indirectIYDisp8);
      }
    },
    new GenericOperation() {
      public void init() {
	init("SRA \\DREG8[x]",
	     "1100101100101xxx",
	     8, 0);
      }
      public void execute0(Arguments args) {
	doSRA(REG8[getArg(args, 'x')]);
      }
    },
    new GenericOperation() {
      public void init() {
	init("SRL (HL)",
	     "1100101100111110",
	     15, 0);
      }
      public void execute0(Arguments args) {
	doSRL(indirectRegHL);
      }
    },
    new GenericOperation() {
      public void init() {
	init("SRL (IX\\DISP8[d])",
	     "1101110111001011dddddddd00111110",
	     23, 0);
      }
      public void execute0(Arguments args) {
	indirectIXDisp8.setDisp8(getArg(args, 'd'));
	doSRL(indirectIXDisp8);
      }
    },
    new GenericOperation() {
      public void init() {
	init("SRL (IY\\DISP8[d])",
	     "1111110111001011dddddddd00111110",
	     23, 0);
      }
      public void execute0(Arguments args) {
	indirectIYDisp8.setDisp8(getArg(args, 'd'));
	doSRL(indirectIYDisp8);
      }
    },
    new GenericOperation() {
      public void init() {
	init("SRL \\DREG8[x]",
	     "1100101100111xxx",
	     8, 0);
      }
      public void execute0(Arguments args) {
	doSRL(REG8[getArg(args, 'x')]);
      }
    },
    new GenericOperation() {
      public void init() {
	init("SUB \\VAL8[d]",
	     "11010110dddddddd",
	     7, 0);
      }
      public void execute0(Arguments args) {
	regA.setValue(doSUB8(regA.getValue(), getArg(args, 'd')));
      }
    },
    new GenericOperation() {
      public void init() {
	init("SUB (HL)",
	     "10010110",
	     7, 0);
      }
      public void execute0(Arguments args) {
	regA.setValue(doSUB8(regA.getValue(), indirectRegHL.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("SUB (IX\\DISP8[d])",
	     "1101110110010110dddddddd",
	     19, 0);
      }
      public void execute0(Arguments args) {
	indirectIXDisp8.setDisp8(getArg(args, 'd'));
	regA.setValue(doSUB8(regA.getValue(), indirectIXDisp8.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("SUB (IY\\DISP8[d])",
	     "1111110110010110dddddddd",
	     19, 0);
      }
      public void execute0(Arguments args) {
	indirectIYDisp8.setDisp8(getArg(args, 'd'));
	regA.setValue(doSUB8(regA.getValue(), indirectIYDisp8.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("SUB \\DREG8[x]",
	     "10010xxx",
	     4, 0);
      }
      public void execute0(Arguments args) {
	regA.setValue(doSUB8(regA.getValue(), REG8[getArg(args, 'x')].
			     getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("XOR \\VAL8[d]",
	     "11101110dddddddd",
	     7, 0);
      }
      public void execute0(Arguments args) {
	doXOR(regA, getArg(args, 'd'));
      }
    },
    new GenericOperation() {
      public void init() {
	init("XOR (HL)",
	     "10101110",
	     7, 0);
      }
      public void execute0(Arguments args) {
	doXOR(regA, indirectRegHL.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("XOR (IX\\DISP8[d])",
	     "1101110110101110dddddddd",
	     19, 0);
      }
      public void execute0(Arguments args) {
	indirectIXDisp8.setDisp8(getArg(args, 'd'));
	doXOR(regA, indirectIXDisp8.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("XOR (IY\\DISP8[d])",
	     "1111110110101110dddddddd",
	     19, 0);
      }
      public void execute0(Arguments args) {
	indirectIYDisp8.setDisp8(getArg(args, 'd'));
	doXOR(regA, indirectIYDisp8.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("XOR \\DREG8[x]",
	     "10101xxx",
	     4, 0);
      }
      public void execute0(Arguments args) {
	doXOR(regA, REG8[getArg(args, 'x')].getValue());
      }
    }
  };

  private static class DecodeCompletionActions {
    private static interface Action {
      public void apply(CodeFetcher codeFetcher, Arguments args);
      public int getVarIndex();
    }

    private static class ConstantAction implements Action {
      private int varIndex;
      private int value;

      public ConstantAction(int varIndex, int value) {
        this.varIndex = varIndex;
        this.value = value;
      }

      public int getVarIndex() { return varIndex; }

      public void apply(CodeFetcher codeFetcher, Arguments args) {
        args.setArg(varIndex, value);
      }

      public String toString() {
        return "ConstantAction{var='" + (char)(varIndex + 'a') +
          "', value=" + value + "}";
      }
    }

    private static class AppendBitsToVarAction implements Action {
      private int varIndex;
      private int bitSize;
      private int opCodeByteIndex;
      private int opCodeByteShiftRight;

      private static int[] MASK = { 0x00, 0x01, 0x03, 0x07,
                                    0x0f, 0x1f, 0x3f, 0x7f, 0xff };

      public AppendBitsToVarAction(int varIndex, int bitSize,
                                   int opCodeByteIndex,
                                   int opCodeByteShiftRight) {
        this.varIndex = varIndex;
        this.bitSize = bitSize;
        this.opCodeByteIndex = opCodeByteIndex;
        this.opCodeByteShiftRight = opCodeByteShiftRight;
      }

      public int getVarIndex() { return varIndex; }

      public void apply(CodeFetcher codeFetcher, Arguments args) {
        int value = codeFetcher.fetchByte(opCodeByteIndex);
        int arg = args.getArg(varIndex);
        arg = arg <<= bitSize;
        arg |= (value >>>= opCodeByteShiftRight) & MASK[bitSize];
        args.setArg(varIndex, arg);
      }

      public String toString() {
        return "AppendBitsToVarAction{var='" + (char)(varIndex + 'a') +
          "', bitSize=" + bitSize + ", byteIndex=" + opCodeByteIndex +
          ", shift-right=" + opCodeByteShiftRight + "}";
      }
    }

    private List<Action> actions;

    private DecodeCompletionActions() {
      actions = new ArrayList<Action>();
    }

    private boolean haveActionForVar(int varIndex) {
      for (Action action : actions) {
        if (action.getVarIndex() == varIndex) {
          return true;
        }
      }
      return false;
    }

    public void addConstant(int varIndex, int value) {
      actions.add(new ConstantAction(varIndex, value));
    }

    public void appendBitsToVar(int varIndex, int bitCount,
                                int opCodeByteIndex,
                                int opCodeByteShiftRight)
    {
      if (!haveActionForVar(varIndex)) {
        // this is the first action for this varIndex
        // => clear value before starting to append bits
        actions.add(new ConstantAction(varIndex, 0));
      }
      actions.add(new AppendBitsToVarAction(varIndex, bitCount,
                                            opCodeByteIndex,
                                            opCodeByteShiftRight));
    }

    public void apply(CodeFetcher codeFetcher, Arguments args)
    {
      for (Action action : actions) {
        action.apply(codeFetcher, args);
      }
    }

    public String toString() {
      StringBuffer s = new StringBuffer();
      for (Action action : actions) {
        if (s.length() > 0) {
          s.append(", ");
        }
        s.append(action);
      }
      return "DecodeCompleteActions={" + s + "}";
    }
  }

  private static interface DecodeTableEntry {
    PrecompiledGenericOperation findGenericOperation(CodeFetcher codeFetcher);
  }

  private static class PrecompiledGenericOperation implements DecodeTableEntry {
    GenericOperation genericOperation;
    DecodeCompletionActions actions;
    int precompiledBytes;

    public PrecompiledGenericOperation(GenericOperation genericOperation,
                                       DecodeCompletionActions actions,
                                       int precompiledBytes) {
      this.genericOperation = genericOperation;
      this.actions = actions;
      this.precompiledBytes = precompiledBytes;
    }

    public GenericOperation getGenericOperation() {
      return genericOperation;
    }

    public void fillArgs(CodeFetcher codeFetcher, Arguments args)
    {
      actions.apply(codeFetcher, args);
      genericOperation.swapEndiansOnAllArgs(args);
    }

    public int getPrecompiledBytes() {
      return precompiledBytes;
    }

    public PrecompiledGenericOperation
      findGenericOperation(CodeFetcher codeFetcher)
    {
      return this;
    }
  }

  private static class DecodeTable implements DecodeTableEntry {
    /**
     * For each op-code byte 0..255, this table contains the according
     * GenericOperation or, if there is no unique GenericOperation for
     * the op-code byte, a nested DecodeTable object that also
     * encounters the subsequent op-code byte.
     */
    private DecodeTableEntry[] entries;

    private static final boolean DEBUG_OPERATIONS = false;

    private static String opCodeAsHexBytes(int[] code)
    {
      StringBuffer s = new StringBuffer();
      for (int j = 0; j < code.length; j++)
        s.append(Util.hexByteStr(code[j]) + " ");
      return String.format("%1$-16s", s);
    }

    public DecodeTable(GenericOperation[] availableOperations,
		       int[] codePrefix)
    {
      int[] code = new int[codePrefix.length + 1];
      for (int i = 0; i < codePrefix.length; i++)
	code[i] = codePrefix[i];
      entries = new DecodeTableEntry[256];
      Vector<GenericOperation> matchingSet = new Vector<GenericOperation>();
      for (int i = 0; i < 256; i++) {
	matchingSet.setSize(0);
	code[code.length - 1] = i;
        DecodeCompletionActions matchedActions = null;
	for (GenericOperation genericOperation : availableOperations) {
          DecodeCompletionActions actions = genericOperation.tryMatch(code);
	  if (actions != null) {
            if (matchedActions == null) matchedActions = actions;
	    matchingSet.addElement(genericOperation);
          }
	}
	if (matchingSet.size() == 0) {
	  // no matching mnemonic found => this is an unsupported opcode
	  entries[i] = null;
          if (DEBUG_OPERATIONS) {
            System.out.print("MATCHING OP: ");
            System.out.print(opCodeAsHexBytes(code));
            System.out.println("-- INFO: no matching mnemonic found");
          }
	} else if (matchingSet.size() == 1) {
	  // exactly one matching mnemonic found => gotcha!
          GenericOperation genericOperation = matchingSet.elementAt(0);
          genericOperation.compileDynArgsDecoding(matchedActions, code.length);
          PrecompiledGenericOperation entry =
            new PrecompiledGenericOperation(genericOperation,
                                            matchedActions, code.length);
	  entries[i] = entry;
          if (DEBUG_OPERATIONS) {
            System.out.print("MATCHING OP: ");
            System.out.print(opCodeAsHexBytes(code));
            System.out.println(entries[i]);
          }
	} else {
	  //multiple matching mnemonics found
	  GenericOperation[] matchingSetArray =
	    matchingSet.toArray(new GenericOperation[0]);
	  if (code.length * 8 == matchingSetArray[0].genericOpCodeBits.length) {
	    /*
	     * Two or more clashing generic mnemonics represent the
	     * same opcode.  This is usually an error in the modelling
	     * of mnemonics.  Still, we prefer the first of all
	     * matching mnemonics, assuming that it is more specific
	     * than the other ones, and thus it is the correct one.
	     * We print a warning.
	     */
            PrecompiledGenericOperation entry =
              new PrecompiledGenericOperation(matchingSet.elementAt(0),
                                              matchedActions, code.length);
	    entries[i] = entry;
            if (DEBUG_OPERATIONS) {
              System.out.print("MATCHING OP: ");
              System.out.print(opCodeAsHexBytes(code));
              System.out.println("-- ERROR: Multiple matches: {");
              for (GenericOperation genericOperation : matchingSetArray)
                System.out.println(genericOperation);
              System.out.println("}");
              System.out.println("Please the set of operation codes!");
              System.out.println("Preferring " + matchingSetArray[0]);
            }
	  } else {
	    // need to examine another byte of code differentiate
	    // between remaining mnemonics
	    entries[i] = new DecodeTable(matchingSetArray, code);
	  }
	}
      }
    }

    public PrecompiledGenericOperation
      findGenericOperation(CodeFetcher codeFetcher)
    {
      int codeByte = codeFetcher.fetchNextByte();
      DecodeTableEntry entry = entries[codeByte];
      if (entry == null) return null; /* invalid opcode */
      return entry.findGenericOperation(codeFetcher);
    }
  }

  private DecodeTable decodeTable;

  /**
   * Fills in concreteOperation for code that is delivered from the
   * code fetcher.
   * @return The length of the operation in bytes.
   */
  private int decode(ConcreteOperation concreteOperation,
                     CodeFetcher codeFetcher, boolean isSynthesizedCode)
    throws CPU.MismatchException
  {
    concreteOperation.address = codeFetcher.getBaseAddress();
    concreteOperation.isSynthesizedCode = isSynthesizedCode;
    codeFetcher.reset();
    PrecompiledGenericOperation precompiledGenericOperation =
      decodeTable.findGenericOperation(codeFetcher);
    if (precompiledGenericOperation != null) {
      return
        concreteOperation.instantiate(precompiledGenericOperation, codeFetcher);
    } else {
      /* invalid opcode */
      throw
	new CPU.MismatchException("no matching operation found [" +
				  codeFetcher + "]");
    }
  }

  // *** ALU ******************************************************************

  // or mask for SET command
  private final static int[] SET_MASK = new int[] {
    0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80
  };

  // and mask for RES command
  private final static int[] RES_MASK = new int[] {
    0xfe, 0xfd, 0xfb, 0xf7, 0xef, 0xdf, 0xbf, 0x7f
  };

  private final static boolean[] PARITY;

  static {
    PARITY = new boolean[256];
    for (int i = 0; i < 256; i++) {
      int bit = 0;
      int value = i;
      for (int j = 0; j < 8; j++) {
	bit ^= value & 0x1;
	value >>= 1;
      }
      PARITY[i] = bit == 0;
    }
  }

  private void reset() {
    regAF.reset();
    regBC.reset();
    regDE.reset();
    regHL.reset();
    regIX.reset();
    regIY.reset();
    regI.reset();
    regR.reset();
    regSP.reset();
    regPC.reset();
    regAF_.reset();
    regBC_.reset();
    regDE_.reset();
    regHL_.reset();
    regIM.reset();
    intr_bus_data = 0x00;
    irq_requested = false;
    irq_to_be_enabled = false;
    irq_enabled = false;
    nmi_requested = false;
  }

  private boolean halfCarry(int bitOp1, int bitOp2, int bitSum) {
    return
      ((bitOp1 & bitOp2) != 0x0) ||
      ((bitSum == 0x0) && ((bitOp1 | bitOp2) != 0x0));
  }

  private int doADC8OrSBC8(int op1, int op2, int carry, boolean isSubOp) {
    int msb_op1 = op1 & 0x80;
    int msb_op2 = op2 & 0x80;
    int sum = op1 + op2 + carry;
    boolean new_flag_h = halfCarry(op1 & 0x08, op2 & 0x08, sum & 0x08);
    boolean new_flag_c = (sum >= 0x100) ^ isSubOp;
    int msb_sum = sum & 0x80;
    boolean new_flag_v = (msb_op1 == msb_op2) && (msb_op1 != msb_sum);
    sum &= 0xff;
    flagC.set(new_flag_c);
    flagN.set(isSubOp);
    flagPV.set(new_flag_v);
    flagH.set(new_flag_h);
    flagZ.set(sum == 0x00);
    flagS.set(sum >= 0x80);
    return sum;
  }

  private int doADC8(int op1, int op2) {
    return doADC8OrSBC8(op1, op2, flagC.get() ? 1 : 0, false);
  }

  private int doADC16OrSBC16(int op1, int op2, int carry, boolean isSubOp) {
    int msb_op1 = op1 & 0x8000;
    int msb_op2 = op2 & 0x8000;
    int sum = op1 + op2 + carry;
    boolean new_flag_h = halfCarry(op1 & 0x0800, op2 & 0x0800, sum & 0x0800);
    boolean new_flag_c = (sum >= 0x10000) ^ isSubOp;
    int msb_sum = sum & 0x8000;
    boolean new_flag_v = (msb_op1 == msb_op2) && (msb_op1 != msb_sum);
    sum &= 0xffff;
    flagC.set(new_flag_c);
    flagN.set(isSubOp);
    flagPV.set(new_flag_v);
    flagH.set(new_flag_h);
    flagZ.set(sum == 0x0000);
    flagS.set(sum >= 0x8000);
    return sum;
  }

  private int doADD16OrSUB16(int op1, int op2, boolean carryIsBorrow) {
    int sum = (op1 & 0xffff) + (op2 & 0xffff);
    boolean new_flag_h = halfCarry(op1 & 0x0800, op2 & 0x0800, sum & 0x0800);
    boolean new_flag_c = (sum >= 0x10000) ^ carryIsBorrow;
    sum &= 0xffff;
    flagC.set(new_flag_c);
    flagN.set(carryIsBorrow);
    // flagPV not affected
    flagH.set(new_flag_h);
    // flagZ not affected
    // flagS not affected
    return sum;
  }

  private int doADC16(int op1, int op2) {
    return doADC16OrSBC16(op1, op2, flagC.get() ? 1 : 0, false);
  }

  private int doADD8(int op1, int op2) {
    return doADC8OrSBC8(op1, op2, 0, false);
  }

  private int doADD16(int op1, int op2) {
    return doADD16OrSUB16(op1, op2, false);
  }

  private void doAND(Reg8 reg, int op) {
    int result = (reg.getValue() & op) & 0xff;
    reg.setValue(result);
    flagC.set(false);
    flagN.set(false);
    flagPV.set(PARITY[result]);
    flagH.set(true);
    flagZ.set(result == 0x00);
    flagS.set(result >= 0x80);
  }

  private void doBIT(int op1, int op2) {
    // flagC not affected
    flagN.set(false);
    // flagPV unknown
    flagH.set(true);
    flagZ.set((op1 & SET_MASK[op2 & 0x7]) == 0x00);
    // flagS unknown
  }

  private void doCCF() {
    boolean savedFlagC = flagC.get();
    flagC.set(!savedFlagC);
    flagN.set(false);
    // flagPV not affected
    flagH.set(savedFlagC);
    // flagZ not affected
    // flagS not affected
  }

  private void doCPL(Reg8 reg) {
    reg.setValue(reg.getValue() ^ 0xff);
    // flagC not affected
    flagN.set(true);
    // flagPV not affected
    flagH.set(true);
    // flagZ not affected
    // flagS not affected
  }

  private void doCP(int op1, int op2) {
    doSUB8(op1, op2);
  }

  private void doCPD() {
    boolean savedFlagC = flagC.get();
    doCP(regA.getValue(), indirectRegHL.getValue());
    regHL.decrement();
    regBC.decrement();
    flagC.set(savedFlagC);
    // flag N set by doCP()
    flagPV.set(((regBC.getValue() + 0xffff) & 0xffff) != 0x0);
    // flag H set by doCP()
    // flag Z set by doCP()
    // flag S set by doCP()
  }

  private void doCPI() {
    boolean savedFlagC = flagC.get();
    doCP(regA.getValue(), indirectRegHL.getValue());
    regHL.increment();
    regBC.decrement();
    flagC.set(savedFlagC);
    // flag N set by doCP()
    flagPV.set(((regBC.getValue() + 0xffff) & 0xffff) != 0x0);
    // flag H set by doCP()
    // flag Z set by doCP()
    // flag S set by doCP()
  }

  private void doDAA(Reg8 reg) {
    // TODO: This is an 8080-style implementation of DAA.  The full
    // Z80-style implementation needs also considerating the processor
    // status "N" flag.
    int op = reg.getValue();
    boolean new_flag_h;
    if ((op & 0x0f) > 0x09) {
      op += 0x06;
      new_flag_h = true;
    } else if (flagH.get()) {
      op += 0x06;
      new_flag_h = false;
    } else {
      new_flag_h = false;
    }
    if (((op & 0xf0) > 0x90) || flagC.get())  {
      op += 0x60;
    }
    boolean new_flag_c = op >= 0x100;
    op &= 0xff;
    flagC.set(new_flag_c);
    // flagN not affected
    flagPV.set(PARITY[op]);
    flagH.set(new_flag_h);
    flagZ.set(op == 0x00);
    flagS.set(op >= 0x80);
    reg.setValue(op);
  }

  private void doDEC16(Reg16 reg) {
    reg.decrement();
    // flagC not affected
    // flagN not affected
    // flagPV not affected
    // flagH not affected
    // flagZ not affected
    // flagS not affected
  }

  private void doDEC8(Reg8 reg) {
    reg.decrement();
    int op = reg.getValue();
    // flagC not affected
    flagN.set(true);
    flagPV.set(op == 0x7f);
    flagH.set((op & 0xf) == 0xf);
    flagZ.set(op == 0x00);
    flagS.set(op >= 0x80);
  }

  private void doINC16(Reg16 reg) {
    reg.increment();
    // flagC not affected
    // flagN not affected
    // flagPV not affected
    // flagH not affected
    // flagZ not affected
    // flagS not affected
  }

  private void doINC8(Reg8 reg) {
    reg.increment();
    int op = reg.getValue();
    // flagC not affected
    flagN.set(false);
    flagPV.set(op == 0x80);
    flagH.set((op & 0xf) == 0x0);
    flagZ.set(op == 0x00);
    flagS.set(op >= 0x80);
  }

  private int doIN(int op) {
    int result = io.readByte(op, wallClockTime) & 0xff;
    // flagC not affected
    flagN.set(false);
    flagPV.set(PARITY[result]);
    flagH.set(false);
    flagZ.set(result == 0x00);
    flagS.set(result >= 0x80);
    return result;
  }

  private void doIND() {
    indirectRegHL.setValue(io.readByte(regBC.getValue(), wallClockTime));
    regB.decrement();
    regHL.decrement();
    // flagC not affected
    flagN.set(true);
    // flagPV unknown
    // flagH unknown
    flagZ.set(regB.getValue() == 0x00);
    // flagS unknown
  }

  private void doINI() {
    indirectRegHL.setValue(io.readByte(regBC.getValue(), wallClockTime));
    regB.decrement();
    regHL.increment();
    // flagC not affected
    flagN.set(true);
    // flagPV unknown
    // flagH unknown
    flagZ.set(regB.getValue() == 0x00);
    // flagS unknown
  }

  private void doLDAI() {
    int op = regI.getValue();
    regA.setValue(op);
    // flagC not affected
    flagN.set(false);
    flagPV.set(irq_enabled); // TODO: irq_enabled != IFF2
    flagH.set(false);
    flagZ.set(op == 0x00);
    flagS.set(op >= 0x80);
  }

  private void doLDAR() {
    int op = regR.getValue();
    regA.setValue(op);
    // flagC not affected
    flagN.set(false);
    flagPV.set(irq_enabled); // TODO: irq_enabled != IFF2
    flagH.set(false);
    flagZ.set(op == 0x00);
    flagS.set(op >= 0x80);
  }

  private void doLDD() {
    memory.writeByte(regDE.getValue(), indirectRegHL.getValue(),
                     wallClockTime);
    regDE.decrement();
    regHL.decrement();
    regBC.decrement();
    // flagC not affected
    flagN.set(false);
    flagPV.set(regBC.getValue() != 0x0000);
    flagH.set(false);
    // flagZ not affected
    // flagS not affected
  }

  private void doLDI() {
    memory.writeByte(regDE.getValue(), indirectRegHL.getValue(),
                     wallClockTime);
    regDE.increment();
    regHL.increment();
    regBC.decrement();
    // flagC not affected
    flagN.set(false);
    flagPV.set(regBC.getValue() != 0x0000);
    flagH.set(false);
    // flagZ not affected
    // flagS not affected
  }

  private void doNEG(Reg8 reg) {
    int op = reg.getValue();
    int neg = 0x100 - op;
    reg.setValue(neg);
    flagC.set(op == 0x00);
    flagN.set(true);
    flagPV.set(op == 0x80);
    flagH.set((op & 0x0f) != 0x00);
    flagZ.set(neg == 0x00);
    flagS.set(neg >= 0x80);
  }

  private void doNOP() {
    // flagC not affected
    // flagN not affected
    // flagPV not affected
    // flagH not affected
    // flagZ not affected
    // flagS not affected
  }

  private void doOR(Reg8 reg, int op) {
    int result = (reg.getValue() | op) & 0xff;
    reg.setValue(result);
    flagC.set(false);
    flagN.set(false);
    flagPV.set(PARITY[result]);
    flagH.set(false);
    flagZ.set(result == 0x00);
    flagS.set(result >= 0x80);
  }

  private void doOUTD() {
    int value = indirectRegHL.getValue();
    regB.decrement();
    io.writeByte(regBC.getValue(), value, wallClockTime);
    regHL.decrement();
    // flagC not affected
    flagN.set(true);
    // flagPV unknown
    // flagH unknown
    flagZ.set(regB.getValue() == 0x00);
    // flagS unknown
  }

  private void doOUTI() {
    int value = indirectRegHL.getValue();
    regB.decrement();
    io.writeByte(regBC.getValue(), value, wallClockTime);
    regHL.increment();
    // flagC not affected
    flagN.set(true);
    // flagPV unknown
    // flagH unknown
    flagZ.set(regB.getValue() == 0x00);
    // flagS unknown
  }

  public int doPOP() {
    int value = memory.readShort(regSP.getValue(), wallClockTime);
    regSP.setValue((regSP.getValue() + 0x0002) & 0xffff);
    return value;
  }

  public void doPUSH(int op) {
    regSP.setValue((regSP.getValue() + 0xfffe) & 0xffff);
    memory.writeShort(regSP.getValue(), op, wallClockTime);
  }

  private void doRES(Reg8 reg, int op) {
    reg.setValue(reg.getValue() & RES_MASK[op & 0x7]);
    // flagC not affected
    // flagN not affected
    // flagPV not affected
    // flagH not affected
    // flagZ not affected
    // flagS not affected
  }

  private void doRL(Reg8 reg) {
    int op = reg.getValue();
    boolean new_flag_c = (op >= 0x80);
    op <<= 1; op &= 0xff;
    if (flagC.get()) op |= 0x1;
    reg.setValue(op);
    flagC.set(new_flag_c);
    flagN.set(false);
    flagPV.set(PARITY[op]);
    flagH.set(false);
    flagZ.set(op == 0x00);
    flagS.set(op >= 0x80);
  }

  private void doRLA(Reg8 reg) {
    int op = reg.getValue();
    boolean new_flag_c = (op >= 0x80);
    op <<= 1; op &= 0xff;
    if (flagC.get()) op |= 0x1;
    reg.setValue(op);
    flagC.set(new_flag_c);
    flagN.set(false);
    // flagPV not affected
    flagH.set(false);
    // flagZ not affected
    // flagS not affected
  }

  private void doRLC(Reg8 reg) {
    int op = reg.getValue();
    boolean new_flag_c = op >= 0x80;
    op <<= 1; op &= 0xff;
    if (new_flag_c) op |= 0x1;
    reg.setValue(op);
    flagC.set(new_flag_c);
    flagN.set(false);
    flagPV.set(PARITY[op]);
    flagH.set(false);
    flagZ.set(op == 0x00);
    flagS.set(op >= 0x80);
  }

  private void doRLCA(Reg8 reg) {
    int op = reg.getValue();
    boolean new_flag_c = op >= 0x80;
    op <<= 1; op &= 0xff;
    if (new_flag_c) op |= 0x1;
    reg.setValue(op);
    flagC.set(new_flag_c);
    flagN.set(false);
    // flagPV not affected
    flagH.set(false);
    // flagZ not affected
    // flagS not affected
  }

  private void doRLD(Reg8 reg) {
    int regValue = reg.getValue();
    int regAValue = regA.getValue();
    reg.setValue(((regValue << 4) & 0xf0) | (regAValue & 0x0f));
    regAValue = (regAValue & 0xf0) | ((regValue >>> 4) & 0x0f);
    regA.setValue(regAValue);
    // flagC not affected
    flagN.set(false);
    flagPV.set(PARITY[regAValue]);
    flagH.set(false);
    flagZ.set(regAValue == 0x00);
    flagS.set(regAValue >= 0x80);
  }

  private void doRR(Reg8 reg) {
    int op = reg.getValue();
    boolean new_flag_c = (op & 0x01) == 1;
    op >>>= 1;
    if (flagC.get()) op |= 0x80;
    reg.setValue(op);
    flagC.set(new_flag_c);
    flagN.set(false);
    flagPV.set(PARITY[op]);
    flagH.set(false);
    flagZ.set(op == 0x00);
    flagS.set(op >= 0x80);
  }

  private void doRRA(Reg8 reg) {
    int op = reg.getValue();
    boolean new_flag_c = (op & 0x01) == 1;
    op >>>= 1;
    if (flagC.get()) op |= 0x80;
    reg.setValue(op);
    flagC.set(new_flag_c);
    flagN.set(false);
    // flagPV not affected
    flagH.set(false);
    // flagZ not affected
    // flagS not affected
  }

  private void doRRC(Reg8 reg) {
    int op = reg.getValue();
    boolean new_flag_c = (op & 0x01) == 1;
    op >>>= 1;
    if (new_flag_c) op |= 0x80;
    reg.setValue(op);
    flagC.set(new_flag_c);
    flagN.set(false);
    flagPV.set(PARITY[op]);
    flagH.set(false);
    flagZ.set(op == 0x00);
    flagS.set(op >= 0x80);
  }

  private void doRRCA(Reg8 reg) {
    int op = reg.getValue();
    boolean new_flag_c = (op & 0x01) == 1;
    op >>>= 1;
    if (new_flag_c) op |= 0x80;
    reg.setValue(op);
    flagC.set(new_flag_c);
    flagN.set(false);
    // flagPV not affected
    flagH.set(false);
    // flagZ not affected
    // flagS not affected
  }

  private void doRRD(Reg8 reg) {
    int regValue = reg.getValue();
    int regAValue = regA.getValue();
    reg.setValue(((regAValue << 4) & 0xf0) | ((regValue >>> 4) & 0x0f));
    regAValue = (regAValue & 0xf0) | (regValue & 0x0f);
    regA.setValue(regAValue);
    // flagC not affected
    flagN.set(false);
    flagPV.set(PARITY[regAValue]);
    flagH.set(false);
    flagZ.set(regAValue == 0x00);
    flagS.set(regAValue >= 0x80);
  }

  private int doSBC8(int op1, int op2) {
    return doADC8OrSBC8(op1, 0x100 - op2, flagC.get() ? -1 : 0, true);
  }

  private int doSBC16(int op1, int op2) {
    return doADC16OrSBC16(op1, 0x10000 - op2, flagC.get() ? -1 : 0, true);
  }

  private void doSCF() {
    flagC.set(true);
    flagN.set(false);
    // flagPV not affected
    flagH.set(false);
    // flagZ not affected
    // flagS not affected
  }

  private void doSET(Reg8 reg, int op) {
    reg.setValue(reg.getValue() | SET_MASK[op & 0x7]);
    // flagC not affected
    // flagN not affected
    // flagPV not affected
    // flagH not affected
    // flagZ not affected
    // flagS not affected
  }

  private void doSLA(Reg8 reg) {
    int op = reg.getValue();
    boolean new_flag_c = op >= 0x80;
    op <<= 1; op &= 0xff;
    reg.setValue(op);
    flagC.set(new_flag_c);
    flagN.set(false);
    flagPV.set(PARITY[op]);
    flagH.set(false);
    flagZ.set(op == 0x00);
    flagS.set(op >= 0x80);
  }

  private void doSRA(Reg8 reg) {
    int op = reg.getValue();
    boolean new_flag_c = (op & 0x01) == 1;
    op >>>= 1;
    if ((op & 0x40) == 0x1) op |= 0x80;
    reg.setValue(op);
    flagC.set(new_flag_c);
    flagN.set(false);
    flagPV.set(PARITY[op]);
    flagH.set(false);
    flagZ.set(op == 0x00);
    flagS.set(op >= 0x80);
  }

  private void doSRL(Reg8 reg) {
    int op = reg.getValue();
    boolean new_flag_c = (op & 0x01) == 1;
    op >>>= 1;
    reg.setValue(op);
    flagC.set(new_flag_c);
    flagN.set(false);
    flagPV.set(PARITY[op]);
    flagH.set(false);
    flagZ.set(op == 0x00);
    flagS.set(op >= 0x80);
  }

  private int doSUB8(int op1, int op2) {
    return doADC8OrSBC8(op1, 0x100 - op2, 0, true);
  }

  private int doSUB16(int op1, int op2) {
    // return doADD16OrSUB16(op1, 0x10000 - op2, true);
    return doADC16OrSBC16(op1, 0x10000 - op2, 0, true);
  }

  private void doXOR(Reg8 reg, int op) {
    int result = (reg.getValue() ^ op) & 0xff;
    reg.setValue(result);
    flagC.set(false);
    flagN.set(false);
    flagPV.set(PARITY[result]);
    flagH.set(false);
    flagZ.set(result == 0x00);
    flagS.set(result >= 0x80);
  }

  // *** INSTRUCTION FETCH/DECODE/EXECUTION UNIT ******************************

  private ConcreteOperation concreteOperation;

  public ConcreteOperation fetchNextOperation() throws CPU.MismatchException {
    // TODO: Emulate Z80's IFF1 and IFF2 flip-flops in order to
    // correctly handle NMIs.
    final long systemNanoTime = System.nanoTime();
    boolean workPending = false;
    do {
      if (nmi_requested) {
	nmi_requested = false;
	doPUSH(regPC.getValue());
	regPC.setValue(0x0066);
        if (irq_to_be_enabled) {
          irq_enabled = true;
          irq_to_be_enabled = false;
        }
	workPending = true;
      } else if (irq_requested && irq_enabled) {
        irq_enabled = false;
	switch (regIM.getValue()) {
	  case INTR_MODE_0 :
	    intrBusDataFetcher.setIntrBusData(intr_bus_data);
	    decode(concreteOperation, intrBusDataFetcher, true);
            // TODO: What about multi-byte operations on interrupt
            // bus?
	    workPending = false;
	    break;
	  case INTR_MODE_1 :
            doPUSH(regPC.getValue());
	    regPC.setValue(0x0038);
	    workPending = true;
	    break;
	  case INTR_MODE_2 :
            doPUSH(regPC.getValue());
	    int vectorTableAddr =
	      (regI.getValue() << 8) | (intr_bus_data & 0xfe);
	    regPC.setValue(memory.readShort(vectorTableAddr, wallClockTime));
	    workPending = true;
	    break;
	  default :
	    throw new InternalError("illegal interrupt mode");
	}
      } else {
	int opCodeLength = decode(concreteOperation, memoryCodeFetcher, false);
	regPC.setValue((regPC.getValue() + opCodeLength) & 0xffff);
        if (irq_to_be_enabled) {
          irq_enabled = true;
          irq_to_be_enabled = false;
        }
        workPending = false;
      }
    } while (workPending);
    concreteOperation.setSystemNanoTime(systemNanoTime);
    return concreteOperation;
  }

  @Override
  public ConcreteOperation unassembleInstructionAt(final int address)
    throws MismatchException
  {
    final Reg16 regPC = new GenericReg16("PC");
    regPC.setValue(address & 0xffff);
    final MemoryCodeFetcher memoryCodeFetcher =
      new MemoryCodeFetcher(memory, regPC);
    final ConcreteOperation concreteOperation = new ConcreteOperation();
    decode(concreteOperation, memoryCodeFetcher, false);
    return concreteOperation;
  }

  private CPU.Memory memory, io;
  private List<WallClockListener> wallClockListeners;
  private Annotations annotations;

  public Annotations getAnnotations() { return annotations; }

  public CPU.Memory getMemory() { return memory; }

  public CPU.Memory getIO() { return io; }

  public String getProgramCounterName() { return regPC.getName(); }

  public void resyncPeripherals() {
    io.resync(wallClockTime);
    memory.resync(wallClockTime);
  }

  // *** STATISTICS LOGGING ***************************************************

  private long prevSystemNanoTime;
  private long prevWallClockTime;
  private double avgJitterNanoTime;
  private double avgSpeed;
  private double avgThreadLoad;

  private void updateInstructionLevelStatistics(final int clockPeriods,
                                                final long fetchSystemNanoTime)
  {
    final long systemNanoTime = System.nanoTime();
    final long wallClockTime = getWallClockTime();
    final long systemInstructionCycleTime =
      systemNanoTime - prevSystemNanoTime;
    final long wallClockInstructionCycleTime =
      wallClockTime - prevWallClockTime;
    final long systemInstructionPerformanceTime =
      systemNanoTime - fetchSystemNanoTime;

    final long currentJitterNanoTime =
      wallClockInstructionCycleTime - systemInstructionCycleTime;
    avgJitterNanoTime =
      0.9 * avgJitterNanoTime +
      0.1 * currentJitterNanoTime;

    final double currentSpeed =
      1000000000.0 * clockPeriods / wallClockInstructionCycleTime;
    avgSpeed =
      0.9 * avgSpeed +
      0.1 * currentSpeed;

    final double currentThreadLoad =
      ((double)systemInstructionPerformanceTime) / systemInstructionCycleTime;
    avgThreadLoad =
      0.9 * avgThreadLoad +
      0.1 * currentThreadLoad;

    prevSystemNanoTime = systemNanoTime;
    prevWallClockTime = wallClockTime;
  }

  public double getAvgSpeed()
  {
    return avgSpeed;
  }

  public double getAvgThreadLoad()
  {
    return avgThreadLoad;
  }

  public double getAvgJitter()
  {
    return avgJitterNanoTime;
  }

  public void statisticsEnabledChanged(final boolean enabled)
  {
    statisticsEnabled = enabled;
  }

  // *** CPU CONTROL **********************************************************

  public void speedChanged(final int frequency)
  {
    timePerClockCycle = 1000000000 / frequency;
  }

  public void busyWaitChanged(final boolean busyWait)
  {
    // This callback is handled by Monitor class.
    // Hence, do nothing here.
  }

  public Z80() {
    this(MemoryBus.createRAMMemoryBus(0, 65536),
         MemoryBus.createRAMMemoryBus(0, 256));
  }

  public Z80(CPU.Memory memory, CPU.Memory io) {
    System.out.println("initializing Z80:");
    this.memory = memory;
    this.io = io;
    annotations = new Annotations();
    concreteOperation = new ConcreteOperation();
    System.out.println("setting up registers...");
    createRegisters();
    System.out.println("setting up branch conditions...");
    createConditions();
    System.out.println("setting up mnemonic functions...");
    createFunctions();
    System.out.println("setting up instruction set...");
    for (int i = 0; i < OPERATION_SET.length; i++) {
      OPERATION_SET[i].init();
    }
    System.out.println("setting up decode table...");
    decodeTable = new DecodeTable(OPERATION_SET, new int[0]);
    System.out.println("setting up processor interface...");
    memoryCodeFetcher = new MemoryCodeFetcher(memory, regPC);
    intrBusDataFetcher = new IntrBusDataFetcher();
    wallClockListeners = new ArrayList<WallClockListener>();
    System.out.println("resetting processor status...");
    reset();
    UserPreferences.getInstance().addListener(this);
    System.out.println("Z80 initialized.");
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
