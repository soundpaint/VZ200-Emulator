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
  private CPU cpu;
  private CPU.Memory memory;
  private CPU.Memory io;
  private CPU.Register[] registers;
  private CPU.Register regPC, regSP;
  private int address;
  private PushbackInputStream stdin;
  private PrintStream stdout, stderr;

  private static class ParseError extends Exception {
    private static final long serialVersionUID = 6821628755472075263L;

    public ParseError(String s) { super(s); }
  }

  // monitor status
  private int startaddr;

  // command line
  private History history;
  private String cmdLine;
  private int pos; // parse position
  private char command;

  private class Number {
    private boolean parsed;
    private int value;
  }

  private class Text {
    private boolean parsed;
    private String value;
  }

  private static boolean isWhiteSpace(char ch) {
    return (ch == ' ') || (ch == '\t') || (ch == '\r') || (ch == '\n');
  }

  private static boolean isPrintableChar(char ch) {
    return (ch >= ' ') && (ch <= '~');
  }

  private static boolean isAsciiLetter(char ch) {
    return
      ((ch >= 'A') && (ch <= 'Z')) ||
      ((ch >= 'a') && (ch <= 'z'));
  }

  private static boolean isDigit(char ch) {
    return (ch >= '0') && (ch <= '9');
  }

  private static boolean isFileNameChar(char ch) {
    return
      isAsciiLetter(ch) ||
      isDigit(ch) ||
      (ch == '_') ||
      (ch == '-') ||
      (ch == '.');
  }

  private static boolean isRegNameChar(char ch) {
    return
      isAsciiLetter(ch) ||
      isDigit(ch) ||
      (ch == '\'');
  }

  private void skipWhiteSpace() {
    boolean stop = false;
    while ((pos < cmdLine.length()) && !stop) {
      if (isWhiteSpace(cmdLine.charAt(pos)))
	pos++;
      else
	stop = true;
    }
  }

  private boolean eof() {
    skipWhiteSpace();
    return pos >= cmdLine.length();
  }

  private void parseEof() throws ParseError {
    if (!eof())
      throw new ParseError("end of line expected");
  }

  private static final String SYMBOL_ASSIGN = "=";
  private static final String SYMBOL_TO = "-";

  private void parseSymbol(String symbol) throws ParseError {
    skipWhiteSpace();
    if (symbol.isEmpty()) {
      return;
    }
    int symbolPos;
    for (symbolPos = 0; symbolPos < symbol.length(); symbolPos++) {
      if (pos >= cmdLine.length()) break;
      if (symbol.charAt(symbolPos) != cmdLine.charAt(pos)) break;
      pos++;
    }
    if (symbolPos < symbol.length())
      throw new ParseError("symbol '" + symbol + "' expected");
  }

  private boolean tryParseNumber(Number num) {
    skipWhiteSpace();
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
    if (parsed) {
      num.value = value;
      num.parsed = true;
    }
    return parsed;
  }

  private void parseNumber(Number num) throws ParseError {
    if (!tryParseNumber(num))
      throw new ParseError("number expected");
  }

  private void parseRegName(Text regName) throws ParseError {
    skipWhiteSpace();
    int startpos = pos;
    boolean stop = false;
    boolean parsed = false;
    while ((pos < cmdLine.length()) && !stop) {
      char ch = cmdLine.charAt(pos);
      if (isRegNameChar(ch) && !isWhiteSpace(ch)) {
	parsed = true;
	pos++;
      }	else {
	stop = true;
      }
    }
    if (!parsed)
      throw new ParseError("register name expected");
    regName.value = cmdLine.substring(startpos, pos);
    regName.parsed = true;
  }

  private void parseFileName(Text fileName) throws ParseError {
    skipWhiteSpace();
    int startpos = pos;
    boolean stop = false;
    boolean parsed = false;
    while ((pos < cmdLine.length()) && !stop) {
      char ch = cmdLine.charAt(pos);
      if (isFileNameChar(ch) && !isWhiteSpace(ch)) {
	parsed = true;
	pos++;
      }	else {
	stop = true;
      }
    }
    if (!parsed)
      throw new ParseError("file name expected");
    fileName.value = cmdLine.substring(startpos, pos);
    fileName.parsed = true;
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
  private Text fileName, regName;

  private String[] script;
  private int scriptLineIndex;

  private String readLine() throws IOException {
    String cmdLine;
    if ((script != null) && (scriptLineIndex < script.length)) {
      cmdLine = script[scriptLineIndex++];
      stdout.println("[" + cmdLine + "]");
    } else {
      cmdLine = readLine(stdin);
    }
    return cmdLine;
  }

  private int inputAvailable() throws IOException {
    if ((script != null) && (scriptLineIndex < script.length))
      return script[scriptLineIndex].length();
    return stdin.available();
  }

  private String enterCommand() {
    try {
      stdout.println();
      stdout.print("#"); stdout.flush();
      return readLine();
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
    fileName.parsed = false;
    regName.parsed = false;
    switch (command) {
      case 'g' :
      case 't' :
      case 'd' :
      case 'u' :
	if (!eof()) {
	  tryParseNumber(num1);
	  if (!eof()) {
            parseSymbol(SYMBOL_TO);
            parseNumber(num2);
          }
	}
        parseEof();
	break;
      case 'i' :
      case 'o' :
      case 'e' :
      case 'a' :
	if (!eof()) parseNumber(num1);
        parseEof();
	break;
      case 's' :
	parseFileName(fileName);
        parseSymbol(SYMBOL_ASSIGN);
	parseNumber(num1);
        parseSymbol(SYMBOL_TO);
	parseNumber(num2);
        parseEof();
	break;
      case 'l' :
	parseNumber(num1);
        parseSymbol(SYMBOL_ASSIGN);
	parseFileName(fileName);
        parseEof();
	break;
      case 'p' :
	parseNumber(num1);
	if (!eof()) {
          parseSymbol(SYMBOL_ASSIGN);
	  parseNumber(num2);
        }
        parseEof();
	break;
      case 'r' :
	if (!eof()) {
	  parseRegName(regName);
	  if (!eof()) {
            parseSymbol(SYMBOL_ASSIGN);
            parseNumber(num1);
          }
	}
        parseEof();
	break;
      case 'h' :
      case 'q' :
	break;
      default :
	pos = 0;
	throw new ParseError("invalid command");
    }
    parseEof();
  }

  private static final int KBD_CHECK_COUNT_LIMIT = 1024;

  /**
   * Turn off, if you want to get less CPU load.  Turn on, if you
   * require high precision in the point of time of CPU instruction
   * execution.
   */
  private static final boolean BUSY_WAIT = true;

  /**
   * If BUSY_WAIT is turned on, use BUSY_WAIT_TIME to fine-control
   * timing precision.  The lower the value, the higher the CPU load,
   * but the better timing precision.  If BUSY_WAIT is turned off,
   * this variable is unused.  Note that the precision of the idle
   * statistics, that is printed after executing code, also increases
   * with increasing the busy wait time value.
   */
  private static final long BUSY_WAIT_TIME = 1000;

  private void go(boolean trace, boolean singleStep, boolean stepOver) {
    if (!singleStep) {
      stdout.println("press <enter> to pause");
    }

    if (num1.parsed) {
      startaddr = num1.value;
      regPC.setValue(startaddr);
    } else {
      // continue whereever regPC currently points to
    }

    boolean haveBreakPoint;
    int breakPoint;
    if (num2.parsed) {
      haveBreakPoint = true;
      breakPoint = num2.value;
    } else if (singleStep) {
      haveBreakPoint = true;
      breakPoint = 0; // will be set later
    } else {
      haveBreakPoint = false;
      breakPoint = 0; // unused
    }

    boolean done = false;
    int kbdCheckCount = 0;
    long systemStartTime = 0;
    long systemStopTime = 0;
    long startCycle = cpu.getWallClockCycles();
    long idleTime = 0;
    long busyTime = 0;
    long jitter = 0;
    try {
      try {
	CPU.ConcreteOperation op = null;
        systemStartTime = System.nanoTime();
        long cpuStartTime = cpu.getWallClockTime();
        long deltaStartTime = cpuStartTime - systemStartTime;
	while (!done) {
          long systemTime = System.nanoTime();
          long cpuTime = cpu.getWallClockTime();
          jitter = systemTime - cpuTime + deltaStartTime;
	  if (jitter > 0) {
            op = cpu.fetchNextOperation();
            if (singleStep) {
              if (stepOver) {
                breakPoint = regPC.getValue();
                op.execute();
              } else {
                op.execute();
                breakPoint = regPC.getValue();
              }
            } else {
              op.execute();
            }
            if (trace) {
              printOperation(op, 0);
              registeraccess();
            }
            if (haveBreakPoint && (regPC.getValue() == breakPoint)) {
              done = true;
            }
            if (kbdCheckCount++ >= KBD_CHECK_COUNT_LIMIT) {
              done |= inputAvailable() > 0;
              kbdCheckCount = 0;
            }
            busyTime += System.nanoTime() - systemTime;
	  } else {
            // busy wait
            if (BUSY_WAIT) {
              while (System.nanoTime() - systemTime < BUSY_WAIT_TIME);
            } else {
              try {
                Thread.sleep(1);
              } catch (InterruptedException e) {
                // ignore
              }
            }
            idleTime += System.nanoTime() - systemTime;
	  }
	}
        systemStopTime = System.nanoTime();
        if (haveBreakPoint && !trace) {
          printOperation(op, 0);
          registeraccess();
        }
      } catch (CPU.MismatchException e) {
	System.err.println(e);
      }
      if (inputAvailable() > 0)
	readLine();
    } catch (IOException e) {
      throw new InternalError(e.getMessage());
    }
    if (!singleStep && !trace) {
      long stopCycle = cpu.getWallClockCycles();
      stdout.printf("[paused]%n");
      stdout.printf("[avg_speed = %.3fMhz]%n",
                    1000.0 * (stopCycle - startCycle) /
                    (systemStopTime - systemStartTime));
      stdout.printf("[busy_wait = %b]%n", BUSY_WAIT);
      if (BUSY_WAIT) {
        stdout.printf("[busy_wait_time = %.2fµs]%n", 0.001 * BUSY_WAIT_TIME);
      }
      stdout.printf("[latest_jitter = %.2fµs]%n", 0.001 * jitter);
      stdout.printf("[avg_thread_load = %3.2f%%]%n",
                    100 * (busyTime / ((float)idleTime + busyTime)));
      registeraccess();
    }
    startaddr = regPC.getValue();
  }

  private void save() {
    try {
      OutputStream os =
	new BufferedOutputStream(new FileOutputStream(fileName.value));
      int addr = num1.value;
      do {
	int value = memory.readByte(addr);
	os.write(value);
	addr++;
      } while (addr != num2.value);
      os.close();
      stdout.printf("wrote %s bytes to file %s%n",
                    Util.hexShortStr((num2.value - num1.value) & 0xffff),
                    fileName.value);
    } catch (IOException e) {
      stderr.println(e.getMessage());
    }
  }

  private void load() {
    try {
      InputStream is =
	new BufferedInputStream(new FileInputStream(fileName.value));
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
      stdout.printf("loaded %s bytes from file %s%n",
                    Util.hexShortStr((addr - num1.value) & 0xffff),
                    fileName.value);
    } catch (IOException e) {
      stderr.println(e.getMessage());
    }
  }

  private int printOperation(CPU.ConcreteOperation op, int fallbackAddress) {
    // TODO: have to know that regPC is a short
    if (op.isSynthesizedCode()) {
      stdout.print("INTR:");
    } else {
      stdout.print(Util.hexShortStr(op.getAddress()) + "-");
    }
    stdout.print("  ");
    int length;
    if (op != null) {
      CPU.ConcreteOpCode opCode = op.createOpCode();
      length = opCode.getLength();
      for (int i = 0; i < length; i++) {
        stdout.print(" " + Util.hexByteStr(opCode.getByte(i)));
      }
      for (int i = 0; i < (6 - length); i++) {
        stdout.print("   ");
      }
      stdout.println(op.getConcreteMnemonic());
    } else {
      stdout.println(" " + Util.hexByteStr(memory.readByte(fallbackAddress)) +
                     "               ???");
      length = 1;
    }
    return length;
  }

  private static final int DEFAULT_UNASSEMBLE_LINES = 16;

  private void unassemble() {
    if (num1.parsed)
      startaddr = num1.value;
    int endAddr = 0;
    if (num2.parsed)
      endAddr = num2.value;
    int regPCValue = regPC.getValue();
    int currentaddr = startaddr;
    CPU.ConcreteOperation op;
    int lineCount = 0;
    do {
      regPC.setValue(currentaddr);
      try {
	op = cpu.fetchNextOperationNoInterrupts();
      } catch (CPU.MismatchException e) {
        op = null;
      }
      currentaddr += printOperation(op, currentaddr);
      currentaddr &= 0xffff; // TODO: 0xffff is z80 specific
    } while ((num2.parsed && (currentaddr <= endAddr)) ||
             (++lineCount < DEFAULT_UNASSEMBLE_LINES));
    startaddr = currentaddr;
    regPC.setValue(regPCValue);
  }

  private void assemble() {
    stderr.println("'a[<addr>]': not yet implemented");
  }

  private final static String SPACE =
  "                                                ";

  private static boolean isPrintableChar(int ch) {
    return (ch >= 0x20) && (ch != 0x7f) && (ch <= 0x80);
  }

  private static char renderDataByteAsChar(int dataByte) {
    if (isPrintableChar(dataByte))
      return (char)dataByte;
    dataByte -= 0x80;
    if (isPrintableChar(dataByte))
      return (char)dataByte;
    return '?';
  }

  private static final int DEFAULT_DUMP_BYTES = 0x100;

  private void dump() {
    if (num1.parsed)
      startaddr = num1.value;
    int stopAddr;
    if (num2.parsed)
      stopAddr = num2.value;
    else
      stopAddr = (startaddr + DEFAULT_DUMP_BYTES) &
        ~0xf & 0xffff; // TODO: 0xffff is z80 specific
    int currentaddr = startaddr;
    stdout.print(Util.hexShortStr(currentaddr) + "-  ");
    StringBuffer sbNumeric =
      new StringBuffer(SPACE.substring(0, (currentaddr & 0xf) * 3));
    StringBuffer sbText =
      new StringBuffer(SPACE.substring(0, currentaddr & 0xf));
    do {
      int dataByte = memory.readByte(currentaddr++);
      currentaddr &= 0xffff;
      sbNumeric.append(" " + Util.hexByteStr(dataByte));
      sbText.append(renderDataByteAsChar(dataByte));
      if ((currentaddr & 0xf) == 0) {
	stdout.println(sbNumeric + "   " + sbText);
	sbNumeric.setLength(0);
	sbText.setLength(0);
	if (currentaddr != stopAddr)
	  stdout.print(Util.hexShortStr(currentaddr) + "-  ");
      }
    } while (currentaddr != stopAddr);
    if ((currentaddr & 0xf) != 0) {
      sbNumeric.append(SPACE.substring(0, (0x10 - currentaddr & 0xf) * 3));
      sbText.append(SPACE.substring(0, 0x10 - currentaddr & 0xf));
      stdout.println(sbNumeric + "   " + sbText);
    }
    startaddr = stopAddr;
  }

  private void enter() throws ParseError {
    if (num1.parsed)
      startaddr = num1.value;
    do {
      stdout.print(Util.hexShortStr(startaddr) + "-   (" +
		   Util.hexByteStr(memory.readByte(startaddr)) + ") ");
      try {
	cmdLine = readLine();
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
      stdout.print(" " + register);
    }
    stdout.println();
  }

  private void registeraccess() {
    if (regName.parsed) {
      CPU.Register matchedRegister = null;
      for (CPU.Register register : registers) {
        if (register.getName().equalsIgnoreCase(regName.value)) {
          matchedRegister = register;
          break;
        }
      }
      if (matchedRegister == null) {
	stderr.printf("no such register: '%s'%n", regName.value);
        return;
      }
      if (num1.parsed) {
	int data = num1.value;
        try {
          matchedRegister.setValue(num1.value);
        } catch (Exception e) {
          stderr.printf("failed setting register %s: %s%n",
                        regName.value, e.getMessage());
        }
      }
      stdout.printf("     %s%n", matchedRegister);
    } else {
      printRegisters();
    }
  }

  private void help() {
    stdout.println("Commands");
    stdout.println("========");
    stdout.println();
    stdout.println("Code Execution");
    stdout.println("  g[<startaddr>][-<stopaddr>]      go [until]");
    stdout.println("  t[<startaddr>][-<stopaddr>]      trace [until]");
    stdout.println("  i[<addr>]                        single step into");
    stdout.println("  o[<addr>]                        step over");
    stdout.println();
    stdout.println("Code / Data Listing");
    stdout.println("  u[<startaddr>][-<stopaddr>]      unassemble");
    stdout.println("  d[<startaddr>][-<stopaddr>]      dump data");
    stdout.println();
    stdout.println("Code / Data Entry");
    stdout.println("  a[<addr>]                        assemble");
    stdout.println("  e[<addr>]                        enter data");
    stdout.println();
    stdout.println("I/O");
    stdout.println("  p<addr>[=<data>]                 port access");
    stdout.println();
    stdout.println("CPU Status");
    stdout.println("  r[<name>[=<data>]]               register access");
    stdout.println();
    stdout.println("Load / Save");
    stdout.println("  s<name>=<startaddr>-<stopaddr>   save data to disk");
    stdout.println("  l<startaddr>=<name>              load data from disk");
    stdout.println();
    stdout.println("Miscelleanous");
    stdout.println("  h                                help (this page)");
    stdout.println("  q                                quit");
  }

  private void executeCommand() throws ParseError {
    switch (command) {
      case 'g' :
	go(false, false, false);
	break;
      case 't' :
	go(true, false, false);
	break;
      case 'i' :
	go(false, true, false);
	break;
      case 'o' :
	// go(false, true, true); // does not yet work correctly
        stderr.println("'o[<addr>]': not yet implemented");
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

  public void run(String script) {
    if ((script != null) && (!script.isEmpty())) {
      this.script = script.split("\r?\n");
      scriptLineIndex = 0;
    }
    run();
  }

  private void run() {
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
    welcome();
    num1 = new Number();
    num2 = new Number();
    fileName = new Text();
    regName = new Text();
    num1.parsed = false;
    num2.parsed = false;
    fileName.parsed = false;
    regName.parsed = false;
    registeraccess();
    do {
      try {
        cmdLine = enterCommand();
        if (!cmdLine.isEmpty()) {
          parseCommand();
          history.addEntry(cmdLine);
          executeCommand();
        }
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
      new Monitor(cpu).run();
    }
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
