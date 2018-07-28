package emulator.z80;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Iterator;
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

  private interface CodeFetcher {
    public int fetchNextByte();

    /**
     * Clear all fetched data.
     */
    public void reset();

    /**
     * Restart delivering all data that has already been fetched.
     */
    public void restart();
  }

  private class MemoryCodeFetcher implements CodeFetcher {
    private final static int CACHE_SIZE = 4;
    private CPU.Memory memory;
    private Reg16 regPC;
    private int pos, size;
    private int[] cache;

    private MemoryCodeFetcher() {}

    public MemoryCodeFetcher(CPU.Memory memory, Reg16 regPC) {
      this.memory = memory;
      this.regPC = regPC;
      cache = new int[CACHE_SIZE];
      size = 0;
      pos = 0;
    }

    public int fetchNextByte() {
      if (pos < size)
	return cache[pos++];
      int result = memory.readByte((regPC.getValue() + pos) & 0xffff) & 0xff;
      cache[pos++] = result;
      size = pos;
      return result;
    }

    public void restart() {
      pos = 0;
    }

    public void reset() {
      pos = 0;
      size = 0;
    }

    public String toString() {
      StringBuffer sb = new StringBuffer();
      sb.append(Util.hexShortStr(regPC.getValue()));
      sb.append("-   ");
      for (int i = 0; i < size; i++) {
	sb.append(" " + Util.hexByteStr(cache[i]));
      }
      return sb.toString();
    }
  }

  /**
   * Single-byte code fetcher.  Needed when serving mode 0 interrupt
   * requests.
   */
  private class IntrBusDataFetcher implements CodeFetcher {
    private int count;
    private int intr_bus_data;

    public IntrBusDataFetcher() {
      count = 0;
    }

    public void setIntrBusData(int intr_bus_data) {
      this.intr_bus_data = intr_bus_data;
    }

    public int fetchNextByte() {
      int result = count == 0 ? intr_bus_data : 0;
      count++;
      return result;
    }

    public int getFetchedByte(int index) {
      assert index < count : "not that many bytes fetched";
      return index == 0 ? intr_bus_data : 0;
    }

    public void restart() {
      count = 0;
    }

    public void reset() {
      count = 0;
    }

    public String toString() {
      StringBuffer sb = new StringBuffer();
      sb.append(Util.hexShortStr(regPC.getValue()));
      sb.append("-   ");
      sb.append(" " + Util.hexByteStr(intr_bus_data));
      for (int i = 0; i < 3; i++) {
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

    public int getValue() { return memory.readByte(reg16.getValue()); }

    public void setValue(int value) {
      memory.writeByte(reg16.getValue(), value);
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
    private int disp8 = 0x00;

    public IndirectReg8Disp8(CPU.Memory memory, Reg16 reg16) {
      super(memory, reg16);
    }

    public void setDisp8(int disp8) { this.disp8 = disp8; }

    public int getDisp8() { return disp8; }

    private String disp8ToString(int disp8) {
      disp8 &= 0xff;
      boolean neg = disp8 > 0x7f;
      if (neg)
	disp8 = 0x100 - disp8;
      return
	((neg) ? "-" : "+") +
	(char)('0' + (disp8 >>> 4)) +
	(char)('0' + (disp8 & 0x0f));
    }

    public String getName() {
      return "(" + reg16.getName() + disp8ToString(disp8) + ")";
    }

    public int getValue() {
      return this.memory.readByte((reg16.getValue() + disp8) & 0xffff);
    }

    public void setValue(int value) {
      this.memory.writeByte((reg16.getValue() + disp8) & 0xffff, value);
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
      return (regHi.getValue() << 8) | regLo.getValue();
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

  private Reg8 regA, regF, regB, regC, regD, regE, regH, regL;
  private RegPair regAF, regBC, regDE, regHL;
  private Reg16 regIX, regIY;
  private Reg8 regIV, regR;
  private Reg16 regSP, regPC;
  private Reg16 regAF_, regBC_, regDE_, regHL_;
  private Reg8 regIM;

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
    regF = new GenericReg8("F");
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
    regIV = new GenericReg8("IV");
    regR = new GenericReg8("R");
    regSP = new GenericReg16("SP");
    regPC = new GenericReg16("PC");
    regAF_ = new GenericReg16("AF'");
    regBC_ = new GenericReg16("BC'");
    regDE_ = new GenericReg16("DE'");
    regHL_ = new GenericReg16("HL'");
    regIM = new GenericReg8("IM");
    indirectRegHL = new IndirectReg8(memory, regHL);
    indirectIXDisp8 = new IndirectReg8Disp8(memory, regIX);
    indirectIYDisp8 = new IndirectReg8Disp8(memory, regIY);
    REGISTER_SET = new CPU.Register[] {
      regF, regA, regBC, regDE, regHL, regIX, regIY,
      regAF_, regBC_, regDE_, regHL_,
      regSP, regPC, regIV, regR, regIM
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

  // *** CPU FLAGS DIRECT ACCESS **********************************************

  // flags mask constants for processor register F
  private final static int FLAG_C = 0x01;
  private final static int FLAG_N = 0x02;
  private final static int FLAG_PV = 0x04;
  private final static int FLAG_H = 0x10;
  private final static int FLAG_Z = 0x40;
  private final static int FLAG_S = 0x80;

  private class Flag {
    private String name;
    private int andMask;
    private int reverseAndMask;
    private Reg8 regF;

    public Flag(String name, int andMask, Reg8 regF) {
      this.name = name;
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

    public String toString() { return name + "=" + (get() ? '1' : '0'); }
  }

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

  private /*final*/ Flag flagC;
  private /*final*/ Flag flagN;
  private /*final*/ Flag flagPV;
  private /*final*/ Flag flagH;
  private /*final*/ Flag flagZ;
  private /*final*/ Flag flagS;

  private void createFlags() {
    flagC = new Flag("C", FLAG_C, regF);
    flagN = new Flag("N", FLAG_N, regF);
    flagPV = new Flag("PV", FLAG_PV, regF);
    flagH = new Flag("H", FLAG_H, regF);
    flagZ = new Flag("Z", FLAG_Z, regF);
    flagS = new Flag("S", FLAG_S, regF);
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
		" " + regIV + " " + regR + " IM=" + regIM);
  }

  // *** INTERRUPT HANDLING ***************************************************

  private final static int INTR_MODE_0 = 0;
  private final static int INTR_MODE_1 = 1;
  private final static int INTR_MODE_2 = 2;

  private int intr_bus_data;
  private boolean irq_requested;
  private boolean irq_serving;
  private boolean irq_enabled;
  private boolean nmi_requested;
  private boolean nmi_serving;

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

  private final static int DEFAULT_CPU_FREQUENCY = 3579545; // [Hz]

  private long timePerClockPeriod; // [ns]

  public long getTimePerClockPeriod() {
    return timePerClockPeriod;
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

  private class Displacement implements Function {
    private String name;
    private int digits;

    private Displacement() {}

    Displacement(String name, int digits) {
      this.name = name;
      this.digits = digits;
    }

    public String getName() { return name; }

    public String evaluate(int arg) {
      int sign;
      if (arg < 0) {
	arg = -arg;
	sign = -1;
      } else {
	sign = +1;
      }
      String hex = Util.hexByteStr(arg);
      while (hex.length() < digits)
	hex = "0" + hex; // TODO: This is very slow and inefficient!
      return
	((sign == +1) ? "+" : "-") + hex.substring(hex.length() - digits);
    }
  }

  private class Rel8 implements Function {
    private String name;
    private Reg16 reg16;

    private Rel8() {}

    Rel8(String name, Reg16 reg16) {
      this.name = name;
      this.reg16 = reg16;
    }

    public String getName() { return name; }

    public String evaluate(int arg) {
      int address = reg16.getValue();
      address += (byte)arg; // signed byte
      return Util.hexShortStr(address);
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
      new Displacement("DISP8", 2),
      new Rel8("REL8", regPC)
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

    public void reset() {
      for (int index = 0; index < ARGS_LENGTH; index++) {
	args[index] = 0;
      }
    }
  }

  public class ConcreteOperation implements CPU.ConcreteOperation {
    private Arguments args;
    private ConcreteOpCode concreteOpCode;
    private String concreteMnemonic;
    private GenericOperation genericOperation;

    public ConcreteOperation() {
      args = new Arguments();
      concreteOpCode = new ConcreteOpCode();
    }

    public Arguments getArguments() { return args; }

    public String getConcreteMnemonic() {
      return genericOperation.createConcreteMnemonic(args);
    }

    public ConcreteOpCode getConcreteOpCode() {
      return concreteOpCode;
    }

    public void execute() {
      genericOperation.execute(args);
    }

    public int getClockPeriods() {
      return
	args.useDefaultClockPeriods ?
	genericOperation.defaultClockPeriods :
	genericOperation.altClockPeriods;
    }

    public void reset() {
      args.reset();
      concreteOpCode.reset();
    }

    /**
     * Tries to match the concrete code as delivered from the code
     * fetcher with this operation's generic opcode pattern.  The
     * arguments that result from matching the generic code pattern
     * with the concrete code are stored and can be accessed via the
     * <code>getArg()</code> method.
     *
     * @return A concrete operation or null, if the code from the code
     *    fetcher does not match this operation's opcode.
     *
     * @see #getArg
     */
    public void instantiate(GenericOperation genericOperation,
			    CodeFetcher codeFetcher)
      throws CPU.MismatchException
    {
      reset();
      int concreteOpCodeByte = 0x00;
      this.genericOperation = genericOperation;
      codeFetcher.restart();

      // compute concrete op-code and args
      for (int i = 0; i < genericOperation.genericOpCodeBits.length; i++) {
	if ((i & 0x7) == 0) {
	  concreteOpCodeByte = codeFetcher.fetchNextByte();
	  concreteOpCode.addByte(concreteOpCodeByte);
	}
	int concreteOpCodeBit = concreteOpCodeByte & SET_MASK[7 - (i & 0x7)];
	int genericOpCodeBit;
	switch (genericOpCodeBit = genericOperation.genericOpCodeBits[i]) {
	  case 0:
	    if (concreteOpCodeBit != 0)
	      throw new CPU.MismatchException();
	    break;
	  case 1:
	    if (concreteOpCodeBit == 0)
	      throw new CPU.MismatchException();
	    break;
	  default:
	    int argValue = args.getArg(genericOpCodeBit - 2);
	    argValue <<= 1;
	    if (concreteOpCodeBit != 0)
	      argValue |= 0x1;
	    args.setArg(genericOpCodeBit - 2, argValue);
	    break;
	}
      }
      genericOperation.swapEndiansOnAllArgs(args);
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
	  MutableInteger mutableInteger = (MutableInteger)vars.get(key);
	  if (mutableInteger == null)
	    vars.put(key, new MutableInteger(1));
	  else
	    mutableInteger.value++;
	} else
	  throw new InternalError("bad generic opcode: " + genericOpCode);
      }
      varIndex = new int[vars.size()];
      varSize = new int[vars.size()];
      Iterator indexIterator = vars.keySet().iterator();
      for (int i = 0; i < vars.size(); i++) {
	Integer index = (Integer)(indexIterator.next());
	varIndex[i] = index.intValue();
	varSize[i] = ((MutableInteger)vars.get(index)).value;
      }
      this.genericOpCode = genericOpCode;
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
	((value & 0x00ff) << 8) | ((value & 0xff00) >>>  8);
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

    /**
     * This is used during initialization to compute the decode tables
     * for fast operation lookup.
     *
     * @param code The starting byte(s) of some op-code.
     */
    public boolean mightMatch(int[] code) {
      Arguments args = new Arguments();
      int codeIndex = 0;
      int concreteOpCodeByte = 0;

      for (int i = 0; i < genericOpCodeBits.length; i++) {
	if ((i & 0x7) == 0)
	  if (codeIndex < code.length)
	    concreteOpCodeByte = code[codeIndex++];
	  else // no more bits in code to verify
	    return true; // TODO: check: resolution != null
        int genericOpCodeBit = genericOpCodeBits[i];
	int concreteOpCodeBit = concreteOpCodeByte & SET_MASK[7 - (i & 0x7)];
	switch (genericOpCodeBit) {
	  case 0: // '0'
	    if (concreteOpCodeBit != 0) {
	      return false;
	    }
	    break;
	  case 1: // '1'
	    if (concreteOpCodeBit == 0) {
	      return false;
	    }
	    break;
	  default: // 'a'..'z'
	    int argValue = args.getArg(genericOpCodeBit - 2);
	    argValue <<= 1;
	    if (concreteOpCodeBit != 0)
	      argValue |= 0x1;
	    args.setArg(genericOpCodeBit - 2, argValue);
	    // continue
	    break;
	}
      }
      
      // Now that we have all args, check that they are not out of
      // range.
      for (int i = 0; i < varIndex.length; i++) {
	int argValue = args.getArg(varIndex[i]);
	if (createConcreteMnemonic(args) == null) {
	  return false;
	}
      }

      // no more bits in genericOpCodeBits to verify
      return codeIndex++ == code.length;
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
	regIY.setValue(doADD16(regIY.getValue(),
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
	regA.setValue(doAND(regA.getValue(), getArg(args, 'd')));
      }
    },
    new GenericOperation() {
      public void init() {
	init("AND (HL)",
	     "10100110",
	     7, 0);
      }
      public void execute0(Arguments args) {
	regA.setValue(doAND(regA.getValue(), indirectRegHL.getValue()));
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
	regA.setValue(doAND(regA.getValue(), indirectIXDisp8.getValue()));
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
	regA.setValue(doAND(regA.getValue(), indirectIYDisp8.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("AND \\DREG8[x]",
	     "10100xxx",
	     4, 0);
      }
      public void execute0(Arguments args) {
	regA.setValue(doADD8(regA.getValue(), REG8[getArg(args, 'x')].
			     getValue()));
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
	init("CALL \\VAL16[x]",
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
	init("CALL \\COND[c],\\VAL16[x]",
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
	init("CP (IY+\\DISP8[d])",
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
	  regPC.setValue((regPC.getValue() - 0x0002) & 0xffff);
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
	  regPC.setValue((regPC.getValue() - 0x0002) & 0xffff);
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
	regA.setValue(doCPL(regA.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("DAA",
	     "00100111",
	     4, 0);
      }
      public void execute0(Arguments args) {
	regA.setValue(doDAA(regA.getValue()));
      }
    },
/*
    new GenericOperation() {
      public void init() {
	init("DEC (HL)",
	     "00110101",
	     11, 0);
      }
      public void execute0(Arguments args) {
	indirectRegHL.setValue(doDEC8(indirectRegHL.getValue()));
      }
    },
*/
    new GenericOperation() {
      public void init() {
	init("DEC IX",
	     "1101110100101011",
	     10, 0);
      }
      public void execute0(Arguments args) {
	regIX.setValue(doDEC16(regIX.getValue()));
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
	indirectIXDisp8.setValue(doDEC8(indirectIXDisp8.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("DEC IY",
	     "1111110100101011",
	     10, 0);
      }
      public void execute0(Arguments args) {
	regIY.setValue(doDEC16(regIY.getValue()));
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
	indirectIYDisp8.setValue(doDEC8(indirectIYDisp8.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("DEC \\REG16[x]",
	     "00xx1011",
	     6, 0);
      }
      public void execute0(Arguments args) {
	REG16[getArg(args, 'x')].setValue(doDEC16(REG16[getArg(args, 'x')].
						 getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("DEC \\REG8[x]",
	     "00xxx101",
	     4, 0);
      }
      public void execute0(Arguments args) {
	REG8[getArg(args, 'x')].setValue(doDEC8(REG8[getArg(args, 'x')].
					       getValue()));
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
	irq_enabled = true;
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
	int value = memory.readShort(regSP.getValue());
	memory.writeShort(regSP.getValue(), regHL.getValue());
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
	int value = memory.readShort(regSP.getValue());
	memory.writeShort(regSP.getValue(), regIX.getValue());
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
	int value = memory.readShort(regSP.getValue());
	memory.writeShort(regSP.getValue(), regIY.getValue());
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
	regA.setValue(io.readByte(getArg(args, 'p')));
      }
    },
    new GenericOperation() {
      public void init() {
	init("IN (C)",
	     "1110110101110000",
	     11, 0);
      }
      public void execute0(Arguments args) {
	doIN(regC.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("IN \\DREG8[x],(C)",
	     "1110110101xxx000",
	     11, 0);
      }
      public void execute0(Arguments args) {
	DREG8[getArg(args, 'x')].setValue(doIN(regC.getValue()));
      }
    },
/*
    new GenericOperation() {
      public void init() {
	init("INC (HL)",
	     "00110100",
	     11, 0);
      }
      public void execute0(Arguments args) {
	indirectRegHL.setValue(doINC8(indirectRegHL.getValue()));
      }
    },
*/
    new GenericOperation() {
      public void init() {
	init("INC IX",
	     "1101110100100011",
	     10, 0);
      }
      public void execute0(Arguments args) {
	regIX.setValue(doINC16(regIX.getValue()));
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
	indirectIXDisp8.setValue(doINC8(indirectIXDisp8.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("INC IY",
	     "1111110100100011",
	     10, 0);
      }
      public void execute0(Arguments args) {
	regIY.setValue(doINC16(regIY.getValue()));
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
	indirectIYDisp8.setValue(doINC8(indirectIYDisp8.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("INC \\REG16[x]",
	     "00xx0011",
	     6, 0);
      }
      public void execute0(Arguments args) {
	REG16[getArg(args, 'x')].setValue(doINC16(REG16[getArg(args, 'x')].
						 getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("INC \\REG8[x]",
	     "00xxx100",
	     4, 0);
      }
      public void execute0(Arguments args) {
	REG8[getArg(args, 'x')].setValue(doINC8(REG8[getArg(args, 'x')].
					       getValue()));
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
	  regPC.setValue((regPC.getValue() - 0x0002) & 0xffff);
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
	  regPC.setValue((regPC.getValue() - 0x0002) & 0xffff);
	else
	  args.useDefaultClockPeriods = false;
      }
    },
    new GenericOperation() {
      public void init() {
	init("JP \\VAL16[x]",
	     "11000011xxxxxxxxxxxxxxxx",
	     10, 0);
      }
      public void execute0(Arguments args) {
	regPC.setValue(getArg(args, 'x'));
      }
    },
    new GenericOperation() {
      public void init() {
	init("JP \\COND[c],\\VAL16[x]",
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
	regPC.setValue(indirectRegHL.getValue());
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
	init("LD A,(\\VAL16[x])",
	     "00111010xxxxxxxxxxxxxxxx",
	     13, 0);
      }
      public void execute0(Arguments args) {
	regA.setValue(memory.readByte(getArg(args, 'x')));
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD A,(BC)",
	     "00001010",
	     7, 0);
      }
      public void execute0(Arguments args) {
	regA.setValue(memory.readByte(regBC.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD A,(DE)",
	     "00011010",
	     7, 0);
      }
      public void execute0(Arguments args) {
	regA.setValue(memory.readByte(regDE.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD A,IV",
	     "1110110101010111",
	     9, 0);
      }
      public void execute0(Arguments args) {
	doLDAIV();
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
	init("LD (\\VAL16[x]),A",
	     "00110010xxxxxxxxxxxxxxxx",
	     13, 0);
      }
      public void execute0(Arguments args) {
	memory.writeByte(getArg(args, 'x'), regA.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD (\\VAL16[x]),BC",
	     "1110110101000011xxxxxxxxxxxxxxxx",
	     20, 0);
      }
      public void execute0(Arguments args) {
	memory.writeShort(getArg(args, 'x'), regBC.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD (\\VAL16[x]),DE",
	     "1110110101010011xxxxxxxxxxxxxxxx",
	     20, 0);
      }
      public void execute0(Arguments args) {
	memory.writeShort(getArg(args, 'x'), regDE.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD (\\VAL16[x]),HL",
	     "00100010xxxxxxxxxxxxxxxx",
	     16, 0);
      }
      public void execute0(Arguments args) {
	memory.writeShort(getArg(args, 'x'), regHL.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD (\\VAL16[x]),IX",
	     "1101110100100010xxxxxxxxxxxxxxxx",
	     20, 0);
      }
      public void execute0(Arguments args) {
	memory.writeShort(getArg(args, 'x'), regIX.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD (\\VAL16[x]),IY",
	     "1111110100100010xxxxxxxxxxxxxxxx",
	     20, 0);
      }
      public void execute0(Arguments args) {
	memory.writeShort(getArg(args, 'x'), regIY.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD (\\VAL16[x]),SP",
	     "1110110101110011xxxxxxxxxxxxxxxx",
	     20, 0);
      }
      public void execute0(Arguments args) {
	memory.writeShort(getArg(args, 'x'), regSP.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD (BC),A",
	     "00000010",
	     7, 0);
      }
      public void execute0(Arguments args) {
	memory.writeByte(regBC.getValue(), regA.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD (DE),A",
	     "00010010",
	     7, 0);
      }
      public void execute0(Arguments args) {
	memory.writeByte(regDE.getValue(), regA.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD HL,(\\VAL16[x])",
	     "00101010xxxxxxxxxxxxxxxx",
	     16, 0);
      }
      public void execute0(Arguments args) {
	regHL.setValue(memory.readShort(getArg(args, 'x')));
      }
    },
/*
    new GenericOperation() {
      public void init() {
	init("LD (HL),\\VAL8[d]",
	     "00110110dddddddd",
	     10, 0);
      }
      public void execute0(Arguments args) {
	indirectRegHL.setValue(getArg(args, 'd'));
      }
    },
*/
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
	init("LD IV,A",
	     "1110110101000111",
	     9, 0);
      }
      public void execute0(Arguments args) {
	regIV.setValue(regA.getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("LD IX,(\\VAL16[x])",
	     "1101110100101010xxxxxxxxxxxxxxxx",
	     20, 0);
      }
      public void execute0(Arguments args) {
	regIX.setValue(memory.readShort(getArg(args, 'x')));
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
	init("LD (IX\\DISP8[d]),q",
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
	init("LD IY,(\\VAL16[x])",
	     "1111110100101010xxxxxxxxxxxxxxxx",
	     20, 0);
      }
      public void execute0(Arguments args) {
	regIY.setValue(memory.readShort(getArg(args, 'x')));
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
	init("LD (IY\\DISP8[d]),q",
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
	init("LD \\REG16[x],(\\VAL16[y])",
	     "1110110101xx1011yyyyyyyyyyyyyyyy",
	     20, 0);
      }
      public void execute0(Arguments args) {
	REG16[getArg(args, 'x')].setValue(memory.readShort(getArg(args, 'y')));
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
	  regPC.setValue((regPC.getValue() - 0x0002) & 0xffff);
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
	  regPC.setValue((regPC.getValue() - 0x0002) & 0xffff);
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
	regA.setValue(doNEG(regA.getValue()));
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
	regA.setValue(doOR(regA.getValue(), getArg(args, 'd')));
      }
    },
    new GenericOperation() {
      public void init() {
	init("OR (HL)",
	     "10110110",
	     7, 0);
      }
      public void execute0(Arguments args) {
	regA.setValue(doOR(regA.getValue(), indirectRegHL.getValue()));
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
	regA.setValue(doOR(regA.getValue(), indirectIXDisp8.getValue()));
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
	regA.setValue(doOR(regA.getValue(), indirectIYDisp8.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("OR \\DREG8[x]",
	     "10110xxx",
	     4, 0);
      }
      public void execute0(Arguments args) {
	regA.setValue(doOR(regA.getValue(), REG8[getArg(args, 'x')].
			   getValue()));
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
	  regPC.setValue((regPC.getValue() - 0x0002) & 0xffff);
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
	  regPC.setValue((regPC.getValue() - 0x0002) & 0xffff);
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
	io.writeByte(regC.getValue(), REG8[getArg(args, 'x')].getValue());
      }
    },
    new GenericOperation() {
      public void init() {
	init("OUT \\VAL8[p],A",
	     "11010011pppppppp",
	     11, 0);
      }
      public void execute0(Arguments args) {
	io.writeByte(getArg(args, 'p'), regA.getValue());
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
	doRES(indirectRegHL.getValue(), getArg(args, 'b'));
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
	doRES(indirectIXDisp8.getValue(), getArg(args, 'b'));
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
	doRES(indirectIYDisp8.getValue(), getArg(args, 'b'));
      }
    },
    new GenericOperation() {
      public void init() {
	init("RES \\VAL3[b],\\DREG8[x]",
	     "1100101110bbbxxx",
	     8, 0);
      }
      public void execute0(Arguments args) {
	doRES(REG8[getArg(args, 'x')].getValue(), getArg(args, 'b'));
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
	irq_serving = false;
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
	nmi_serving = false;
      }
    },
    new GenericOperation() {
      public void init() {
	init("RL (HL)",
	     "1100101100010110",
	     15, 0);
      }
      public void execute0(Arguments args) {
	indirectRegHL.setValue(doRL(indirectRegHL.getValue()));
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
	indirectIXDisp8.setValue(doRL(indirectIXDisp8.getValue()));
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
	indirectIYDisp8.setValue(doRL(indirectIYDisp8.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("RL \\DREG8[x]",
	     "1100101100010xxx",
	     8, 0);
      }
      public void execute0(Arguments args) {
	REG8[getArg(args, 'x')].setValue(doRL(REG8[getArg(args, 'x')].
					     getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("RLA",
	     "00010111",
	     4, 0);
      }
      public void execute0(Arguments args) {
	regA.setValue(doRLA(regA.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("RLC (HL)",
	     "1100101100000110",
	     15, 0);
      }
      public void execute0(Arguments args) {
	indirectRegHL.setValue(doRLC(indirectRegHL.getValue()));
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
	indirectIXDisp8.setValue(doRLC(indirectIXDisp8.getValue()));
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
	indirectIYDisp8.setValue(doRLC(indirectIYDisp8.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("RLC \\DREG8[x]",
	     "1100101100000xxx",
	     8, 0);
      }
      public void execute0(Arguments args) {
	REG8[getArg(args, 'x')].setValue(doRLC(REG8[getArg(args, 'x')].
					      getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("RLCA",
	     "00000111",
	     4, 0);
      }
      public void execute0(Arguments args) {
	regA.setValue(doRLCA(regA.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("RLD",
	     "1110110101101111",
	     18, 0);
      }
      public void execute0(Arguments args) {
	indirectRegHL.setValue(doRLD(indirectRegHL.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("RR (HL)",
	     "1100101100011110",
	     15, 0);
      }
      public void execute0(Arguments args) {
	indirectRegHL.setValue(doRR(indirectRegHL.getValue()));
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
	indirectIXDisp8.setValue(doRR(indirectIXDisp8.getValue()));
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
	indirectIYDisp8.setValue(doRR(indirectIYDisp8.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("RR \\DREG8[x]",
	     "1100101100011xxx",
	     8, 0);
      }
      public void execute0(Arguments args) {
	REG8[getArg(args, 'x')].setValue(doRR(REG8[getArg(args, 'x')].getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("RRA",
	     "00011111",
	     4, 0);
      }
      public void execute0(Arguments args) {
	regA.setValue(doRRA(regA.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("RRC (HL)",
	     "1100101100001110",
	     15, 0);
      }
      public void execute0(Arguments args) {
	indirectRegHL.setValue(doRRC(indirectRegHL.getValue()));
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
	indirectIXDisp8.setValue(doRRC(indirectIXDisp8.getValue()));
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
	indirectIYDisp8.setValue(doRRC(indirectIYDisp8.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("RRC \\DREG8[x]",
	     "1100101100001xxx",
	     8, 0);
      }
      public void execute0(Arguments args) {
	REG8[getArg(args, 'x')].setValue(doRRC(REG8[getArg(args, 'x')].
					      getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("RRCA",
	     "00001111",
	     4, 0);
      }
      public void execute0(Arguments args) {
	regA.setValue(doRRCA(regA.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("RRD",
	     "1110110101100111",
	     18, 0);
      }
      public void execute0(Arguments args) {
	indirectRegHL.setValue(doRRD(indirectRegHL.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("RST \\VAL3[n]",
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
	doSET(indirectRegHL.getValue(), getArg(args, 'b'));
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
	doSET(indirectIXDisp8.getValue(), getArg(args, 'b'));
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
	doSET(indirectIYDisp8.getValue(), getArg(args, 'b'));
      }
    },
    new GenericOperation() {
      public void init() {
	init("SET \\VAL3[b],\\DREG8[x]",
	     "1100101111bbbxxx",
	     8, 0);
      }
      public void execute0(Arguments args) {
	doSET(REG8[getArg(args, 'x')].getValue(), getArg(args, 'b'));
      }
    },
    new GenericOperation() {
      public void init() {
	init("SLA (HL)",
	     "1100101100100110",
	     15, 0);
      }
      public void execute0(Arguments args) {
	indirectRegHL.setValue(doSLA(indirectRegHL.getValue()));
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
	indirectIXDisp8.setValue(doSLA(indirectIXDisp8.getValue()));
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
	indirectIYDisp8.setValue(doSLA(indirectIYDisp8.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("SLA \\DREG8[x]",
	     "1100101100100xxx",
	     8, 0);
      }
      public void execute0(Arguments args) {
	REG8[getArg(args, 'x')].setValue(doSLA(REG8[getArg(args, 'x')].
					      getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("SRA (HL)",
	     "1100101100101110",
	     15, 0);
      }
      public void execute0(Arguments args) {
	indirectRegHL.setValue(doSRA(indirectRegHL.getValue()));
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
	indirectIXDisp8.setValue(doSRA(indirectIXDisp8.getValue()));
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
	indirectIYDisp8.setValue(doSRA(indirectIYDisp8.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("SRA \\DREG8[x]",
	     "1100101100101xxx",
	     8, 0);
      }
      public void execute0(Arguments args) {
	REG8[getArg(args, 'x')].setValue(doSRA(REG8[getArg(args, 'x')].
					      getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("SRL (HL)",
	     "1100101100111110",
	     15, 0);
      }
      public void execute0(Arguments args) {
	indirectRegHL.setValue(doSRL(indirectRegHL.getValue()));
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
	indirectIXDisp8.setValue(doSRL(indirectIXDisp8.getValue()));
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
	indirectIYDisp8.setValue(doSRL(indirectIYDisp8.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("SRL \\DREG8[x]",
	     "1100101100111xxx",
	     8, 0);
      }
      public void execute0(Arguments args) {
	REG8[getArg(args, 'x')].setValue(doSRL(REG8[getArg(args, 'x')].
					      getValue()));
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
	regA.setValue(doXOR(regA.getValue(), getArg(args, 'd')));
      }
    },
    new GenericOperation() {
      public void init() {
	init("XOR (HL)",
	     "10101110",
	     7, 0);
      }
      public void execute0(Arguments args) {
	regA.setValue(doXOR(regA.getValue(), indirectRegHL.getValue()));
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
	regA.setValue(doXOR(regA.getValue(), indirectIXDisp8.getValue()));
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
	regA.setValue(doXOR(regA.getValue(), indirectIYDisp8.getValue()));
      }
    },
    new GenericOperation() {
      public void init() {
	init("XOR \\DREG8[x]",
	     "10101xxx",
	     4, 0);
      }
      public void execute0(Arguments args) {
	regA.setValue(doXOR(regA.getValue(), REG8[getArg(args, 'x')].
			    getValue()));
      }
    }
  };

  private class DecodeTable {
    /**
     * For each op-code byte 0..255, this table contains the according
     * GenericOperation or, if there is no unique GenericOperation for
     * the op-code byte, a nested DecodeTable object that also
     * encounters the subsequent op-code byte.
     */
    private Object[] operations;

    public DecodeTable(GenericOperation[] availableOperations,
		       int[] codePrefix)
    {
      int[] code = new int[codePrefix.length + 1];
      for (int i = 0; i < codePrefix.length; i++)
	code[i] = codePrefix[i];
      operations = new Object[256];
      Vector<GenericOperation> matchingSet = new Vector<GenericOperation>();
      for (int i = 0; i < 256; i++) {
	matchingSet.setSize(0);
	code[code.length - 1] = i;
	for (int j = 0; j < availableOperations.length; j++) {
	  if (availableOperations[j].mightMatch(code))
	    matchingSet.addElement(availableOperations[j]);
	}
	if (matchingSet.size() == 0) {
	  // no matching mnemonic found => this is an invalid opcode
	  operations[i] = null;
	  //for (int j = 0; j < code.length; j++)
	  //  System.out.print(Util.hexByteStr(code[j]) + " ");
	  //System.out.println("no matching mnemonic found");
	} else if (matchingSet.size() == 1) {
	  // exactly one matching mnemonic found => gotcha!
	  operations[i] = matchingSet.elementAt(0);
	  //for (int j = 0; j < code.length; j++)
	  //  System.out.print(Util.hexByteStr(code[j]) + " ");
	  //System.out.println(operations[i]);
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
	    operations[i] = matchingSet.elementAt(0);
	    for (int j = 0; j < code.length; j++)
	      System.out.print(Util.hexByteStr(code[j]) + " ");
	    System.out.println("WARNING: Multiple matches: {");
	    for (int j = 0; j < matchingSetArray.length; j++)
	      System.out.println(matchingSetArray[j]);
	    System.out.println("}");
	    System.out.println("Preferring " + matchingSetArray[0]);
	  } else {
	    // need to examine another byte of code differentiate
	    // between remaining mnemonics
	    operations[i] = new DecodeTable(matchingSetArray, code);
	  }
	}
      }
    }

    public GenericOperation findGenericOperation(CodeFetcher codeFetcher) {
      int codeByte = codeFetcher.fetchNextByte();
      Object operationOrTable = operations[codeByte];
      if (operationOrTable instanceof GenericOperation)
	return (GenericOperation)operationOrTable;
      if (operationOrTable == null)
	return null; /* invalid opcode */
      DecodeTable decodeTable = (DecodeTable)operationOrTable;
      return decodeTable.findGenericOperation(codeFetcher);
    }
  }

  private DecodeTable decodeTable;

  /**
   * Fills in concreteOperation for code that is delivered from the
   * code fetcher.
   */
  private void decode(ConcreteOperation concreteOperation,
		      CodeFetcher codeFetcher)
    throws CPU.MismatchException
  {
    codeFetcher.reset();
    GenericOperation genericOperation =
      decodeTable.findGenericOperation(codeFetcher);
    if (genericOperation != null)
      concreteOperation.instantiate(genericOperation, codeFetcher);
    else {
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
    regSP.reset();
    regPC.reset();
    regAF_.reset();
    regBC_.reset();
    regDE_.reset();
    regHL_.reset();
    regIM.reset();
    intr_bus_data = 0x00;
    irq_requested = false;
    irq_serving = false;
    irq_enabled = false;
    nmi_requested = false;
    nmi_serving = false;
  }

  private int doADC8(int op1, int op2) {
    int msb_op1 = op1 & 0x80;
    int msb_op2 = op2 & 0x80;
    int sum = (op1 & 0xff) + (op2 & 0xff);
    boolean new_flag_h =
      ((sum & 0xf) < (op1 & 0xf)) ||
      (((sum & 0xf) == 0xf) && flagC.get());
    if (flagC.get()) sum++;
    boolean new_flag_c = (sum & 0x100) != 0;
    int msb_sum = sum & 0x80;
    boolean new_flag_v = (msb_op1 == msb_op2) && (msb_op1 != msb_sum);
    sum &= 0xff;
    flagC.set(new_flag_c);
    flagN.set(false);
    flagPV.set(new_flag_v);
    flagH.set(new_flag_h);
    flagZ.set(sum == 0x00);
    flagS.set(sum >= 0x80);
    return sum;
  }

  private int doADC16(int op1, int op2) {
    int msb_op1 = op1 & 0x8000;
    int msb_op2 = op2 & 0x8000;
    int sum = (op1 & 0xffff) + (op2 & 0xffff);
    boolean new_flag_h =
      ((sum & 0xff) < (op1 & 0xff)) ||
      (((sum & 0xff) == 0xff) && flagC.get());
    if (flagC.get()) sum++;
    boolean new_flag_c = (sum & 0x10000) != 0;
    int msb_sum = sum & 0x8000;
    boolean new_flag_v = (msb_op1 == msb_op2) && (msb_op1 != msb_sum);
    sum &= 0xffff;
    flagC.set(new_flag_c);
    flagN.set(false);
    flagPV.set(new_flag_v);
    flagH.set(new_flag_h);
    flagZ.set(sum == 0x0000);
    flagS.set(sum >= 0x8000);
    return sum;
  }

  private int doADD8(int op1, int op2) {
    int msb_op1 = op1 & 0x80;
    int msb_op2 = op2 & 0x80;
    int sum = (op1 & 0xff) + (op2 & 0xff);
    boolean new_flag_h = (sum & 0xf) < (op1 & 0xf);
    boolean new_flag_c = (sum & 0x100) != 0;
    int msb_sum = sum & 0x80;
    boolean new_flag_v = (msb_op1 == msb_op2) && (msb_op1 != msb_sum);
    sum &= 0xff;
    flagC.set(new_flag_c);
    flagN.set(false);
    flagPV.set(new_flag_v);
    flagH.set(new_flag_h);
    flagZ.set(sum == 0x00);
    flagS.set(sum >= 0x80);
    return sum;
  }

  private int doADD16(int op1, int op2) {
    int sum = (op1 & 0xffff) + (op2 & 0xffff);
    boolean new_flag_h = (sum & 0xff) < (op1 & 0xff);
    boolean new_flag_c = (sum & 0x10000) != 0;
    sum &= 0xffff;
    flagC.set(new_flag_c);
    flagN.set(false);
    // flagPV unmodified
    flagH.set(new_flag_h);
    // flagZ unmodified
    // flagS unmodified
    return sum;
  }

  private int doAND(int op1, int op2) {
    int result = (op1 & op2) & 0xff;
    flagC.set(false);
    flagN.set(false);
    flagPV.set(PARITY[result]);
    flagH.set(true);
    flagZ.set(result == 0x00);
    flagS.set(result >= 0x80);
    return result;
  }

  private void doBIT(int op1, int op2) {
    // flagC unmodified
    flagN.set(false);
    // flagPV unmodified ("unknown")
    flagH.set(true);
    flagZ.set((op1 & SET_MASK[op2 & 0x7]) == 0x00);
    // flagS unmodified ("unknown")
  }

  private void doCCF() {
    flagC.set(!flagC.get());
    flagN.set(false);
    // flagPV unmodified
    flagH.set(!flagC.get());
    // flagZ unmodified
    // flagS unmodified
  }

  private int doCPL(int op) {
    // flagC unmodified
    flagN.set(true);
    // flagPV unmodified
    flagH.set(true);
    // flagZ unmodified
    // flagS unmodified
    return op ^ 0xff;
  }

  private void doCP(int op1, int op2) {
    doSUB8(op1, op2);
  }

  private void doCPD() {
    doCP(regA.getValue(), indirectRegHL.getValue());
    regHL.decrement();
    regBC.decrement();
    flagPV.set(flagZ.get());
  }

  private void doCPI() {
    doCP(regA.getValue(), indirectRegHL.getValue());
    regHL.increment();
    regBC.decrement();
    flagPV.set(flagZ.get());
  }

  private int doDAA(int op) {
    if ((op & 0xf) > 0x9) {
      op = ((op & 0xf0) + 0x10) | ((op & 0xf - 0xa));
      flagC.set((op & 0xf0) == 0x00);
      flagH.set(true);
    } else {
      flagH.set(false);
    }
    // flagN unmodified
    flagPV.set(PARITY[op]);
    flagZ.set(op == 0x00);
    flagS.set(op >= 0x80);
    return op;
  }

  private int doDEC16(int op) {
    op--; op &= 0xffff;
    // flagC unmodified
    // flagN unmodified
    // flagPV unmodified
    // flagH unmodified
    // flagZ unmodified
    // flagS unmodified
    return op;
  }

  private int doDEC8(int op) {
    op--; op &= 0xff;
    // flagC unmodified
    flagN.set(true);
    flagPV.set(op == 0xff);
    flagH.set((op & 0xf) == 0xf);
    flagZ.set(op == 0x00);
    flagS.set(op >= 0x80);
    return op;
  }

  private int doINC16(int op) {
    op++; op &= 0xffff;
    // flagC unmodified
    // flagN unmodified
    // flagPV unmodified
    // flagH unmodified
    // flagZ unmodified
    // flagS unmodified
    return op;
  }

  private int doINC8(int op) {
    op++; op &= 0xff;
    // flagC unmodified
    flagN.set(false);
    flagPV.set(op == 0xff);
    flagH.set((op & 0xf) == 0x0);
    flagZ.set(op == 0x00);
    flagS.set(op >= 0x80);
    return op;
  }

  private int doIN(int op1) {
    int result = io.readByte(op1);
    // flagC unmodified
    flagN.set(false);
    flagPV.set(PARITY[result]);
    flagH.set((result & 0xf) == 0x0);
    flagZ.set(result == 0x00);
    flagS.set(result >= 0x80);
    return result;
  }

  private void doIND() {
    indirectRegHL.setValue(io.readByte(regC.getValue()));
    regB.decrement();
    regHL.decrement();
    // flagC unmodified
    flagN.set(true);
    // flagPV unmodified
    flagH.set((regB.getValue() & 0xf) == 0x0);
    flagZ.set(regB.getValue() == 0x00);
    flagS.set(regB.getValue() >= 0x80);
  }

  private void doINI() {
    indirectRegHL.setValue(io.readByte(regC.getValue()));
    regB.decrement();
    regHL.increment();
    // flagC unmodified
    flagN.set(true);
    // flagPV unmodified
    flagH.set((regB.getValue() & 0xf) == 0x0);
    flagZ.set(regB.getValue() == 0x00);
    flagS.set(regB.getValue() >= 0x80);
  }

  private void doLDAIV() {
    regA.setValue(regIV.getValue());
    // flagC unmodified
    flagN.set(false);
    flagPV.set(irq_enabled);
    flagH.set(false);
    flagZ.set(regA.getValue() == 0x00);
    flagS.set(regA.getValue() >= 0x80);
  }

  private void doLDAR() {
    regA.setValue(regR.getValue());
    // flagC unmodified
    flagN.set(false);
    flagPV.set(irq_enabled);
    flagH.set(false);
    flagZ.set(regA.getValue() == 0x00);
    flagS.set(regA.getValue() >= 0x80);
  }

  private void doLDD() {
    memory.writeByte(regDE.getValue(), indirectRegHL.getValue());
    regDE.decrement();
    regHL.decrement();
    regBC.decrement();
    // flagC unmodified
    flagN.set(false);
    flagPV.set(regBC.getValue() != 0x0000);
    flagH.set(false);
    // flagZ unmodified
    // flagS unmodified
  }

  private void doLDI() {
    memory.writeByte(regDE.getValue(), indirectRegHL.getValue());
    regDE.increment();
    regHL.increment();
    regBC.decrement();
    // flagC unmodified
    flagN.set(false);
    flagPV.set(regBC.getValue() != 0x0000);
    flagH.set(false);
    // flagZ unmodified
    // flagS unmodified
  }

  private int doNEG(int op) {
    return doSUB8(0, op);
  }

  private void doNOP() {
    // flagC unmodified
    // flagN unmodified
    // flagPV unmodified
    // flagH unmodified
    // flagZ unmodified
    // flagS unmodified
  }

  private int doOR(int op1, int op2) {
    int result = (op1 | op2) & 0xff;
    flagC.set(false);
    flagN.set(false);
    flagPV.set(PARITY[result]);
    flagH.set(false);
    flagZ.set(result == 0x00);
    flagS.set(result >= 0x80);
    return result;
  }

  private void doOUTD() {
    io.writeByte(regC.getValue(), indirectRegHL.getValue());
    regB.decrement();
    regHL.decrement();
    // flagC unmodified
    flagN.set(true);
    flagPV.set(false); // ?
    flagH.set((regB.getValue() & 0xf) == 0x0);
    flagZ.set(regB.getValue() == 0x00);
    flagS.set(regB.getValue() >= 0x80);
  }

  private void doOUTI() {
    io.writeByte(regC.getValue(), indirectRegHL.getValue());
    regB.decrement();
    regHL.increment();
    // flagC unmodified
    flagN.set(true);
    flagPV.set(false); // ?
    flagH.set((regB.getValue() & 0xf) == 0x0);
    flagZ.set(regB.getValue() == 0x00);
    flagS.set(regB.getValue() >= 0x80);
  }

  public int doPOP() {
    int value = memory.readShort(regSP.getValue());
    regSP.setValue((regSP.getValue() + 0x0002) & 0xffff);
    return value;
  }

  public void doPUSH(int op) {
    regSP.setValue((regSP.getValue() - 0x0002) & 0xffff);
    memory.writeShort(regSP.getValue(), op);
  }

  private int doRES(int op1, int op2) {
    // flagC unmodified
    // flagN unmodified
    // flagPV unmodified
    // flagH unmodified
    // flagZ unmodified
    // flagS unmodified
    return op1 & RES_MASK[op2 & 0x7];
  }

  private int doRL(int op) {
    boolean new_flag_c = (op >= 0x80);
    op <<= 1; op &= 0xff;
    if (flagC.get()) op++;
    flagC.set(new_flag_c);
    flagN.set(false);
    flagPV.set(PARITY[op]);
    flagH.set(false);
    flagZ.set(op == 0x00);
    flagS.set(op >= 0x80);
    return op;
  }

  private int doRLA(int op) {
    boolean new_flag_c = (op >= 0x80);
    op <<= 1; op &= 0xff;
    if (flagC.get()) op++;
    flagC.set(new_flag_c);
    flagN.set(false);
    // flagPV unmodified
    flagH.set(false);
    // flagZ unmodified
    // flagS unmodified
    return op;
  }

  private int doRLC(int op) {
    flagC.set(op >= 0x80);
    op <<= 1; op &= 0xff;
    if (flagC.get()) op++;
    flagN.set(false);
    flagPV.set(PARITY[op]);
    flagH.set(false);
    flagZ.set(op == 0x00);
    flagS.set(op >= 0x80);
    return op;
  }

  private int doRLCA(int op) {
    flagC.set(op >= 0x80);
    op <<= 1; op &= 0xff;
    if (flagC.get()) op++;
    flagN.set(false);
    // flagPV unmodified
    flagH.set(false);
    // flagZ unmodified
    // flagS unmodified
    return op;
  }

  private int doRLD(int op) {
    int shifted = (op << 4) | (regA.getValue() & 0x0f);
    regA.setValue((regA.getValue() & 0xf0) | (op >>> 4));
    // flagC unmodified
    flagN.set(false);
    flagPV.set(PARITY[op]);
    flagH.set(false);
    flagZ.set(op == 0x00);
    flagS.set(op >= 0x80);
    return shifted;
  }

  private int doRR(int op) {
    boolean new_flag_c = (op & 0x01) == 1;
    op >>>= 1;
    if (flagC.get()) op |= 0x80;
    flagC.set(new_flag_c);
    flagN.set(false);
    flagPV.set(PARITY[op]);
    flagH.set(false);
    flagZ.set(op == 0x00);
    flagS.set(op >= 0x80);
    return op;
  }

  private int doRRA(int op) {
    boolean new_flag_c = (op & 0x01) == 1;
    op >>>= 1;
    if (flagC.get()) op |= 0x80;
    flagC.set(new_flag_c);
    flagN.set(false);
    // flagPV unmodified
    flagH.set(false);
    // flagZ unmodified
    // flagS unmodified
    return op;
  }

  private int doRRC(int op) {
    flagC.set((op & 0x01) == 1);
    op >>>= 1;
    if (flagC.get()) op |= 0x80;
    flagN.set(false);
    flagPV.set(PARITY[op]);
    flagH.set(false);
    flagZ.set(op == 0x00);
    flagS.set(op >= 0x80);
    return op;
  }

  private int doRRCA(int op) {
    flagC.set((op & 0x01) == 1);
    op >>>= 1;
    if (flagC.get()) op |= 0x80;
    flagN.set(false);
    // flagPV unmodified
    flagH.set(false);
    // flagZ unmodified
    // flagS unmodified
    return op;
  }

  private int doRRD(int op) {
    int shifted = op | (regA.getValue() << 8);
    regA.setValue((regA.getValue() & 0xf0) | (op & 0x0f));
    // flagC unmodified
    flagN.set(false);
    flagPV.set(PARITY[op]);
    flagH.set(false);
    flagZ.set(op == 0x00);
    flagS.set(op >= 0x80);
    return shifted >>> 4;
  }

  private int doSBC8(int op1, int op2) {
    int result = doADC8(op1 ^ 0xff, op2);
    flagN.set(true);
    return result;
  }

  private int doSBC16(int op1, int op2) {
    int result = doADC16(op1 ^ 0xffff, op2);
    flagN.set(true);
    return result;
  }

  private void doSCF() {
    flagC.set(true);
    flagN.set(false);
    // flagPV unmodified
    flagH.set(false);
    // flagZ unmodified
    // flagS unmodified
  }

  private int doSET(int op1, int op2) {
    // flagC unmodified
    // flagN unmodified
    // flagPV unmodified
    // flagH unmodified
    // flagZ unmodified
    // flagS unmodified
    return op1 | SET_MASK[op2 & 0x7];
  }

  private int doSLA(int op) {
    flagC.set(op >= 0x80);
    op <<= 1; op &= 0xff;
    flagN.set(false);
    flagPV.set(PARITY[op]);
    flagH.set(false);
    flagZ.set(op == 0x00);
    flagS.set(op >= 0x80);
    return op;
  }

  private int doSRA(int op) {
    flagC.set((op & 0x01) == 1);
    op >>>= 1;
    if (flagC.get()) op |= 0x80;
    flagN.set(false);
    flagPV.set(PARITY[op]);
    flagH.set(false);
    flagZ.set(op == 0x00);
    flagS.set(op >= 0x80);
    return op;
  }

  private int doSRL(int op) {
    flagC.set((op & 0x01) == 1);
    op >>>= 1;
    flagN.set(false);
    flagPV.set(PARITY[op]);
    flagH.set(false);
    flagZ.set(op == 0x00);
    flagS.set(op >= 0x80);
    return op;
  }

  private int doSUB8(int op1, int op2) {
    flagC.set(true);
    int result = doADD8(op1 ^ 0xff, op2);
    flagN.set(true);
    return result;
  }

  private int doSUB16(int op1, int op2) {
    flagC.set(true);
    int result = doADD16(op1 ^ 0xffff, op2);
    flagN.set(true);
    return result;
  }

  private int doXOR(int op1, int op2) {
    int result = (op1 ^ op2) & 0xff;
    flagC.set(false);
    flagN.set(false);
    flagPV.set(PARITY[result]);
    flagH.set(false);
    flagZ.set(result == 0x00);
    flagS.set(result >= 0x80);
    return result;
  }

  // *** INSTRUCTION FETCH/DECODE/EXECUTION UNIT ******************************

  private ConcreteOperation concreteOperation;

  public ConcreteOperation fetchNextOperation() throws CPU.MismatchException {
    boolean workPending = false;
    do {
      if (nmi_requested /* && !nmi_serving */) {
	nmi_serving = true;
	nmi_requested = false;
	doPUSH(regPC.getValue());
	regPC.setValue(0x0066);
	workPending = true;
      } else if (irq_requested && irq_enabled /* && !irq_serving */) {
	irq_serving = true;
	irq_requested = false;
	doPUSH(regPC.getValue());
	switch (regIM.getValue()) {
	  case INTR_MODE_0 :
	    intrBusDataFetcher.setIntrBusData(intr_bus_data);
	    decode(concreteOperation, intrBusDataFetcher);
	    break;
	  case INTR_MODE_1 :
	    regPC.setValue(0x0038);
	    workPending = true;
	    break;
	  case INTR_MODE_2 :
	    int vectorTableAddr =
	      (regIV.getValue() << 8) | (intr_bus_data & 0xfe);
	    regPC.setValue(memory.readShort(vectorTableAddr));
	    workPending = true;
	    break;
	  default :
	    throw new InternalError("illegal interrupt mode");
	}
      } else {
	decode(concreteOperation, memoryCodeFetcher);
	int opCodeLength =
	  concreteOperation.getConcreteOpCode().getLength();
	regPC.setValue((regPC.getValue() + opCodeLength) & 0xffff);
      }
    } while (workPending);
    return concreteOperation;
  }

  private CPU.Memory memory, io;

  public CPU.Memory getMemory() { return memory; }

  public CPU.Memory getIO() { return io; }

  public String getProgramCounterName() { return regPC.getName(); }

  public Z80() {
    this(DEFAULT_CPU_FREQUENCY);
  }

  public Z80(int cpuFrequency) {
    this(new RAMMemory(0, 65536), new RAMMemory(0, 256), cpuFrequency);
  }

  public Z80(CPU.Memory memory, CPU.Memory io) {
    this(memory, io, DEFAULT_CPU_FREQUENCY);
  }

  public Z80(CPU.Memory memory, CPU.Memory io, int cpuFrequency) {
    timePerClockPeriod = 1000000000 / cpuFrequency;
    System.out.println("initializing Z80:");
    this.memory = memory;
    this.io = io;
    System.out.println("setting up registers...");
    createRegisters();
    System.out.println("setting up processor flags...");
    createFlags();
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
    concreteOperation = new ConcreteOperation();
    System.out.println("resetting processor status...");
    reset();
    System.out.println("Z80 initialized.");
  }

  public static void main(String argv[]) {
    CPU z80 = new Z80();
    new Monitor(z80).run(null);
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
