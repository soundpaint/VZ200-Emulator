// TODO: move this class into new package 'emulator.monitor'.
package emulator.z80;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PushbackInputStream;
import java.io.IOException;

public class Monitor {
  private final static boolean DEBUG = false;

  private CPU cpu;
  private CPU.Memory memory;
  private CPU.Memory io;
  private CPU.Register[] registers;
  private CPU.Register regPC, regSP;
  private int address;
  private PushbackInputStream stdin;
  private PrintStream stdout, stderr;

  private static class ParseError extends Exception {
    public ParseError(String s) { super(s); }
  }

  // monitor status
  private int startaddr, endaddr;

  // command line
  private History history;
  private String cmdLine;
  private int pos; // parse position
  private char command;

  private class Number {
    boolean parsed;
    int value;
  }

  private class Id {
    boolean parsed;
    String value;
  }

  private static boolean isWhiteSpace(char ch) {
    return (ch == ' ') || (ch == '\t') || (ch == '\r') || (ch == '\n');
  }

  private static boolean isPrintableChar(char ch) {
    return ((ch >= ' ') && (ch <= '~'));
  }

  private void skip() {
    boolean stop = false;
    while ((pos < cmdLine.length()) && !stop) {
      if (isWhiteSpace(cmdLine.charAt(pos)))
	pos++;
      else
	stop = true;
    }
  }

  private boolean eof() {
    skip();
    return pos >= cmdLine.length();
  }

  private void parseNumber(Number num) throws ParseError {
    skip();
    int value = 0;
    boolean stop = false;
    boolean parsed = false;
    while ((pos < cmdLine.length()) && !stop) {
      int digit;
      if ((digit = Util.hexValue(cmdLine.charAt(pos))) >= 0) {
	value = (value << 4) | digit;
	parsed = true;
	pos++;
      }	else {
	stop = true;
      }
    }
    if (!parsed)
      throw new ParseError("number expected");
    num.value = value;
    num.parsed = true;
  }

  private void parseId(Id id) throws ParseError {
    skip();
    int startpos = pos;
    boolean stop = false;
    boolean parsed = false;
    while ((pos < cmdLine.length()) && !stop) {
      char ch = cmdLine.charAt(pos);
      if (isPrintableChar(ch) && !isWhiteSpace(ch)) {
	parsed = true;
	pos++;
      }	else {
	stop = true;
      }
    }
    if (!parsed)
      throw new ParseError("id expected");
    id.value = cmdLine.substring(startpos, pos);
    id.parsed = true;
  }

  private char lineBuffer[];

  private String readLine(PushbackInputStream in) throws IOException {
    char buf[] = lineBuffer;
    if (buf == null)
      buf = lineBuffer = new char[128];
    int room = buf.length;
    int offset = 0;
    int c;

  loop:
    while (true) {
      switch (c = in.read()) {
        case java.awt.Event.UP:
	  return history.getPrecursor(1);
	case -1:
	case '\n':
	  break loop;
	case '\r':
	  int c2 = in.read();
	  if ((c2 != '\n') && (c2 != -1))
	    in.unread(c2);
	  break loop;

	default:
	  if (--room < 0) {
	    buf = new char[offset + 128];
	    room = buf.length - offset - 1;
	    System.arraycopy(lineBuffer, 0, buf, 0, offset);
	    lineBuffer = buf;
	  }
	  buf[offset++] = (char) c;
	  break;
      }
    }
    if ((c == -1) && (offset == 0))
      return null;
    return String.copyValueOf(buf, 0, offset);
  }

  private Number num1, num2;
  private Id id;

  private String enterCommand() {
    try {
      stdout.println();
      stdout.print("#"); stdout.flush();
      return readLine(stdin);
    } catch (IOException e) {
      throw new InternalError(e.getMessage());
    }
  }

  private void parseCommand() throws ParseError {
    pos = 0;
    if (eof())
      throw new ParseError("enter command ('h' for help)");
    command = cmdLine.charAt(pos);
    pos = 1;
    num1.parsed = false;
    num2.parsed = false;
    id.parsed = false;
    switch (command) {
      case 'g' :
      case 'c' :
      case 'b' :
      case 't' :
      case 'e' :
      case 'a' :
	if (!eof())
	  parseNumber(num1);
	break;
      case 'u' :
      case 'd' :
	if (!eof()) {
	  parseNumber(num1);
	  if (!eof())
	    parseNumber(num2);
	}
	break;
      case 's' :
	parseNumber(num1);
	parseNumber(num2);
	parseId(id);
	break;
      case 'l' :
	parseNumber(num1);
	parseId(id);
	break;
      case 'p' :
	parseNumber(num1);
	if (!eof())
	  parseNumber(num2);
	break;
      case 'r' :
	if (!eof()) {
	  parseId(id);
	  if (!eof())
	    parseNumber(num1);
	}
	break;
      case 'h' :
      case 'q' :
	break;
      default :
	pos = 0;
	throw new ParseError("invalid command");
    }
    if (!eof())
      throw new ParseError("end of line expected");
  }

  /**
   * The time (as returned by java.lang.System.nanoTime()) when the next
   * CPU cycle starts.
   */
  private long time;

  private void resetTiming() {
    time = System.nanoTime();
  }

  private long getLag() {
    return System.nanoTime() - time;
  }

  private final static long CHECK_KBD_PERIOD = 50000000; // [ns]
  private final static long FS_UP_PERIOD =     19000000; // [ns]
  private final static long FS_DOWN_PERIOD =    1000000; // [ns]

  private static enum
    EVENT_TYPE {CPU_CYCLE, KBD_CHECK, FS_ACTIVATE, FS_DEACTIVATE};

  private static class EventQueue {
    private final static int MAX_LENGTH = 10;

    private long[] time;
    private EVENT_TYPE[] type;
    private int size;

    public EventQueue() {
      time = new long[MAX_LENGTH];
      type = new EVENT_TYPE[MAX_LENGTH];
      size = 0;
    }

    public boolean isDue() {
      return
	size > 0 &&
	System.nanoTime() >= time[size - 1];
    }

    public void add(long time_, EVENT_TYPE type_) {
      //System.out.println("add " + time_ + " / " + type_);
      assert size < MAX_LENGTH : "queue overflow";
      time[size] = time_;
      type[size] = type_;
      for (int i = size - 1; i >= 0; i--) {
	if (time[i + 1] > time[i]) {
	  time_ = time[i]; time[i] = time[i + 1]; time[i + 1] = time_;
	  type_ = type[i]; type[i] = type[i + 1]; type[i + 1] = type_;
	} else {
	  break;
	}
      }
      size++;
    }

    public EVENT_TYPE nextEvent() {
      //System.out.println("remove " + time[size - 1] + " / " + type[size - 1]);
      assert size > 0 : "queue underflow";
      return type[--size];
    }

    public void clear() {
      size = 0;
    }
  }

  private void go(boolean callSub) {
    int savedRegSP = 0;
    stdout.println("press <enter> to stop");
    if (callSub) {
      stdout.println("[call sub: stopping as soon as " + regSP + "]");
      savedRegSP = regSP.getValue();
      cpu.doPUSH(regPC.getValue());
    }
    if (num1.parsed) {
      startaddr = num1.value;
      regPC.setValue(startaddr);
    } else {
      // continue whereever regPC currently points to
    }
    boolean stop = false;
    long lag = 0;
    EventQueue eventQueue = new EventQueue();
    try {
      try {
	CPU.ConcreteOperation op;
	eventQueue.clear();
	resetTiming();
	long cpuTime = time;
	long kbdCheckTime = time;
	long fsTime = time;
	eventQueue.add(cpuTime, EVENT_TYPE.CPU_CYCLE);
	eventQueue.add(kbdCheckTime, EVENT_TYPE.KBD_CHECK);
	eventQueue.add(fsTime, EVENT_TYPE.FS_ACTIVATE);
	while (!stop) {
	  if (eventQueue.isDue()) {
	    switch (eventQueue.nextEvent()) {
	      case CPU_CYCLE:
		op = cpu.fetchNextOperation();
		op.execute();
		if (!callSub || (regSP.getValue() != savedRegSP)) {
		  cpuTime += op.getClockPeriods() * cpu.getTimePerClockPeriod();
		  eventQueue.add(cpuTime, EVENT_TYPE.CPU_CYCLE);
		} else {
		  lag = getLag();
		  stop = true;
		}
		break;
	      case KBD_CHECK:
		if (stdin.available() > 0) {
		  lag = getLag();
		  stop = true;
		} else {
		  kbdCheckTime += CHECK_KBD_PERIOD;
		  eventQueue.add(kbdCheckTime, EVENT_TYPE.KBD_CHECK);
		}
		break;
	      case FS_ACTIVATE:
		fsTime += FS_UP_PERIOD;
		eventQueue.add(fsTime, EVENT_TYPE.FS_DEACTIVATE);
		break;
	      case FS_DEACTIVATE:
		fsTime += FS_DOWN_PERIOD;
		eventQueue.add(fsTime, EVENT_TYPE.FS_ACTIVATE);
		break;
	    }
	  } else {
	    /*
	    try {
	      Thread.currentThread().sleep(1);
	    } catch (InterruptedException e) {
	      // ignore
	    }
	    //Thread.currentThread().yield();
	    */
	  }
	}
      } catch (CPU.MismatchException e) {
	System.err.println(e);
      }
      if (stdin.available() > 0)
	readLine(stdin);
    } catch (IOException e) {
      throw new InternalError(e.getMessage());
    }
    stdout.println("[stopped]");
    stdout.println("[lag = " + lag + "ns]");
    stdout.println();
    id.parsed = false;
    num1.parsed = false;
    num2.parsed = false;
    registeraccess();
    startaddr = regPC.getValue();
  }

  private void traceUntil() {
    if (num1.parsed) {
      endaddr = num1.value;
    }
    CPU.ConcreteOperation op;
    try {
      while (startaddr != endaddr) {
	// TODO: have to know that regPC is a short
	stdout.print(Util.hexShortStr(startaddr) + "-  ");
	op = cpu.fetchNextOperation();
	printOperation(op);
	op.execute();
	registeraccess();
	startaddr = regPC.getValue();
        if (stdin.available() > 0) {
          stdout.println("[stopped]");
          break;
        }
      }
    } catch (CPU.MismatchException e) {
      System.err.println(e);
    } catch (IOException e) {
      throw new InternalError(e.getMessage());
    }
    id.parsed = false;
    num1.parsed = false;
    num2.parsed = false;
  }

  private void trace() {
    if (num1.parsed) {
      startaddr = num1.value;
      regPC.setValue(startaddr);
    } else {
      // continue whereever regPC currently points to
    }
    CPU.ConcreteOperation op;
    try {
      // TODO: have to know that regPC is a short
      stdout.print(Util.hexShortStr(regPC.getValue()) + "-  ");
      op = cpu.fetchNextOperation();
      printOperation(op);
      op.execute();
    } catch (CPU.MismatchException e) {
      System.err.println(e);
    }
    id.parsed = false;
    num1.parsed = false;
    num2.parsed = false;
    registeraccess();
    startaddr = regPC.getValue();
  }

  private void save() {
    try {
      OutputStream os =
	new BufferedOutputStream(new FileOutputStream(id.value));
      int addr = num1.value;
      do {
	int value = memory.readByte(addr);
	os.write(value);
	addr++;
      } while (addr != num2.value);
      os.close();
    } catch (IOException e) {
      stderr.println(e.getMessage());
    }
  }

  private void load() {
    try {
      InputStream is =
	new BufferedInputStream(new FileInputStream(id.value));
      startaddr = num1.value;
      int addr = startaddr;
      int value;
      do {
	value = is.read();
	if (value >= 0) {
	  memory.writeByte(addr, value);
	  addr++;
	}
      }
      while (value >= 0);
      is.close();
    } catch (IOException e) {
      stderr.println(e.getMessage());
    }
  }

  private void printOperation(CPU.ConcreteOperation op) {
    CPU.ConcreteOpCode opCode = op.getConcreteOpCode();
    for (int i = 0; i < opCode.getLength(); i++) {
      stdout.print(" " + Util.hexByteStr(opCode.getByte(i)));
    }
    for (int i = 0; i < (6 - opCode.getLength()); i++) {
      stdout.print("   ");
    }
    stdout.println(op.getConcreteMnemonic());
  }

  private void unassemble() {
    if (num1.parsed)
      startaddr = num1.value;
    if (num2.parsed)
      endaddr = num2.value;
    else
      endaddr = (startaddr + 0x10) & 0xffff; // TODO: 0xffff is z80 specific
    if (endaddr < startaddr)
      endaddr += 0x10000; // TODO: 0x10000 is z80 specific
    int regPCValue = regPC.getValue();
    int currentaddr = startaddr;
    CPU.ConcreteOperation op;
    do {
      // TODO: have to know that regPC is a short
      stdout.print(Util.hexShortStr(currentaddr) + "-  ");

      regPC.setValue(currentaddr & 0xffff); // TODO: 0xffff is z80 specific
      try {
	op = cpu.fetchNextOperation();
	printOperation(op);
	currentaddr += op.getConcreteOpCode().getLength();
      } catch (CPU.MismatchException e) {
	stdout.println(" " + Util.hexByteStr(memory.readByte(currentaddr)) +
		       "               ???");
	currentaddr++;
      }
    } while (currentaddr <= endaddr);
    startaddr = currentaddr & 0xffff; // TODO: 0xffff is z80 specific
    endaddr = startaddr;
    regPC.setValue(regPCValue);
  }

  private void assemble() {
    stderr.println("'a[ <addr>]': not yet implemented");
  }

  private final static String SPACE =
  "                                                ";

  private static boolean isPrintableChar(int ch) {
    return (ch >= 0x20) && (ch != 0x7f) && (ch <= 0x80);
  }

  private void dump() {
    if (num1.parsed)
      startaddr = num1.value;
    if (num2.parsed)
      endaddr = num2.value;
    else
      endaddr = (startaddr + 0x3f) & 0xffff; // TODO: 0xffff is z80 specific
    if (endaddr < startaddr)
      endaddr += 0x10000; // TODO: 0x10000 is z80 specific
    int currentaddr = startaddr;
    stdout.print(Util.hexShortStr(currentaddr) + "-  ");
    StringBuffer sbNumeric = new StringBuffer(SPACE.substring(0, (currentaddr & 0xf) * 3));
    StringBuffer sbText = new StringBuffer(SPACE.substring(0, (currentaddr & 0xf) * 2));
    do {
      int codeByte = memory.readByte(currentaddr++);
      sbNumeric.append(" " + Util.hexByteStr(codeByte));
      if (isPrintableChar(codeByte))
	sbText.append(" " + (char)codeByte);
      else
	sbText.append(" ?");
      if ((currentaddr & 0xf) == 0) {
	stdout.println(sbNumeric + " " + sbText);
	sbNumeric.setLength(0);
	sbText.setLength(0);
	if (currentaddr <= endaddr)
	  stdout.print(Util.hexShortStr(currentaddr) + "-  ");
      }
    } while (currentaddr <= endaddr);
    if ((currentaddr & 0xf) != 0) {
      sbNumeric.append(SPACE.substring(0, (0x10 - currentaddr & 0xf) * 3));
      sbText.append(SPACE.substring(0, (0x10 - currentaddr & 0xf) * 2));
      stdout.println(sbNumeric + " " + sbText);
    }
    startaddr = (endaddr + 1) & 0xffff; // TODO: 0xffff is z80 specific
  }

  private void enter() throws ParseError {
    if (num1.parsed)
      startaddr = num1.value;
    do {
      stdout.print(Util.hexShortStr(startaddr) + "-   (" +
		   Util.hexByteStr(memory.readByte(startaddr)) + ") ");
      try {
	cmdLine = readLine(stdin);
	pos = 0;
      } catch (IOException e) {
	throw new InternalError(e.getMessage());
      }
      if (cmdLine.toUpperCase().equals("Q"))
	break;
      if (eof())
	startaddr++;
      else {
	while (!eof()) {
	  parseNumber(num1);
	  memory.writeByte(startaddr, num1.value);
	  startaddr++;
	}
      }
    } while (true);
  }

  private void portaccess() {
    int port = num1.value;
    if (num2.parsed) {
      int data = num2.value;
      io.writeByte(port, data);
    } else {
      stdout.println(Util.hexByteStr(io.readByte(port)));
    }
  }

  private void printRegisters() {
    for (int i = 0; i < registers.length; i++) {
      if ((i & 0x7) == 0) {
	stdout.println();
	stdout.print("    ");
      }
      CPU.Register register = registers[i];
      // TODO: have to know bit width of register
      stdout.print(" " + register.getName() + "=" +
		   Util.hexShortStr(register.getValue()));
    }
    stdout.println();
  }

  private void registeraccess() {
    if (id.parsed) {
      String regName = id.value;
      if (num1.parsed) {
	int data = num1.value;
	stderr.println("'r <name> <data>': not yet implemented");
      } else {
	stderr.println("'r <name>': not yet implemented");
      }
    } else {
      printRegisters();
    }
  }

  private void help() {
    stdout.println("Commands:");
    stdout.println("g[ <addr>]                   go");
    stdout.println("c[ <addr>]                   call");
    stdout.println("t[ <addr>]                   trace");
    stdout.println("b[ <addr>]                   trace until pc=addr");
    stdout.println("s <addr1> <addr2> <name>     save data to disk");
    stdout.println("l <addr> <name>              load data from disk");
    stdout.println("u[ <addr1>[ <addr2>]]        unassemble");
    stdout.println("a[ <addr>]                   assemble");
    stdout.println("d[ <addr1>[ <addr2>]]        dump data");
    stdout.println("e[ <addr>]                   enter data");
    stdout.println("p <addr>[ <data>]            port access");
    stdout.println("r[ <name>[ <data>]]          register access");
    stdout.println("h                            help (this page)");
    stdout.println("q                            quit");
  }

  private void executeCommand() throws ParseError {
    switch (command) {
      case 'g' :
	go(false);
	break;
      case 'c' :
	go(true);
	break;
      case 'b' :
	traceUntil();
	break;
      case 't' :
	trace();
	break;
      case 's' :
	save();
	break;
      case 'l' :
	load();
	break;
      case 'u' :
	unassemble();
	break;
      case 'a' :
	assemble();
	break;
      case 'd' :
	dump();
	break;
      case 'e' :
	enter();
	break;
      case 'p' :
	portaccess();
	break;
      case 'r' :
	registeraccess();
	break;
      case 'h' :
	help();
	break;
      case 'q' :
	break;
      default :
	throw new InternalError("invalid command: " + command);
    }
  }

  private void welcome() {
    stdout.println();
    stdout.println("****************************************");
    stdout.println("* Monitor V/0.1                        *");
    stdout.println("* (C) 2001, 2010 Jürgen Reuter,        *");
    stdout.println("* Karlsruhe                            *");
    stdout.println("****************************************");
    stdout.println();
    stdout.println("Enter 'h' for help.");
    stdout.println();
  }

  public void run(String preCmdLine) {
    System.out.println("starting monitor...");
    stdin = new PushbackInputStream(System.in);
    stdout = System.out;
    stderr = System.err;
    memory = cpu.getMemory();
    io = cpu.getIO();
    registers = cpu.getAllRegisters();
    regPC = cpu.getProgramCounter();
    regSP = cpu.getStackPointer();
    startaddr = 0x0000;
    endaddr = 0x0000;
    welcome();
    num1 = new Number();
    num2 = new Number();
    id = new Id();
    num1.parsed = false;
    num2.parsed = false;
    id.parsed = false;
    registeraccess();
    do {
      try {
	if (preCmdLine == null) {
	  cmdLine = enterCommand();
	} else {
	  cmdLine = preCmdLine;
	  preCmdLine = null;
	  stdout.println("[" + cmdLine + "]");
	}
	parseCommand();
	history.addEntry(cmdLine);
	executeCommand();
      } catch (ParseError e) {
	stdout.print(" ");
	for (int i = 0; i < pos; i++)
	  stdout.print(" ");
	stdout.println("^");
	stderr.println(">>> " + e.getMessage() + " (enter 'h' for help)");
      }
    } while (command != 'q');
    System.exit(0);
  }

  private Monitor() {}

  public Monitor(CPU cpu) {
    history = new History();
    this.cpu = cpu;
  }

  private static void usage() {
    System.out.println("Usage: emulator.z80.Monitor <classname of CPU>");
  }

  public static void main(String argv[]) {
    if (argv.length != 1) {
      usage();
      System.exit(-1);
    } else {
      String cpuClassName = null;
      CPU cpu = null;
      try {
	cpuClassName = argv[0];
	Class cpuClass = Class.forName(cpuClassName);
	cpu = (CPU)cpuClass.newInstance();
      } catch (Exception e) {
	System.out.println("Could not instantiate CPU class '" +
			   cpuClassName + "': " + e);
	System.exit(-1);
      }
      new Monitor(cpu).run(null);
    }
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
