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

    private int location;

    public ParseError(String s, int location) {
      super(s);
      this.location = location;
    }

    public String prettyPrint() {
      StringBuffer s = new StringBuffer();
      s.append(" ");
      for (int i = 0; i < location; i++)
        s.append(" ");
      s.append("^ >>> ");
      s.append(getMessage());
      s.append(" (enter 'h' for help)");
      return s.toString();
    }
  }

  // monitor status
  private int codeStartAddr;
  private int dataStartAddr;

  // command line
  private History history;
  private String cmdLine;
  private int pos; // parse position
  private char command;

  private class Number {
    private int location;
    private int value;

    public Number() { reset(); }

    public void reset() {
      location = -1;
      value = 0;
    }

    public void set(int location, int value) {
      if (location < 0) {
        throw new IllegalArgumentException("location < 0: " + location);
      }
      this.location = location;
      this.value = value;
    }

    public boolean parsed() {
      return location >= 0;
    }

    public int getLocation() {
      if (!parsed()) { throw new IllegalStateException("unparsed value"); }
      return location;
    }

    public int getValue() {
      if (!parsed()) { throw new IllegalStateException("unparsed value"); }
      return value;
    }
  }

  private class Text {
    private int location;
    private String value;

    public Text() { reset(); }

    public void reset() {
      location = -1;
      value = "";
    }

    public void set(int location, String value) {
      if (location < 0) {
        throw new IllegalArgumentException("location < 0: " + location);
      }
      if (value == null) {
        throw new NullPointerException("value");
      }
      this.location = location;
      this.value = value;
    }

    public boolean parsed() {
      return location >= 0;
    }

    public int getLocation() {
      if (!parsed()) { throw new IllegalStateException("unparsed value"); }
      return location;
    }

    public String getValue() {
      if (!parsed()) { throw new IllegalStateException("unparsed value"); }
      return value;
    }
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
      throw new ParseError("end of line expected", pos);
  }

  private static final String SYMBOL_ASSIGN = "=";
  private static final String SYMBOL_TO = "-";

  private boolean tryParseSymbol(String symbol) {
    skipWhiteSpace();
    int location = pos;
    int symbolPos;
    for (symbolPos = 0; symbolPos < symbol.length(); symbolPos++) {
      if (pos >= cmdLine.length()) break;
      if (symbol.charAt(symbolPos) != cmdLine.charAt(pos)) break;
      pos++;
    }
    if (symbolPos >= symbol.length()) {
      return true;
    }
    pos = location;
    return false;
  }

  private void parseSymbol(String symbol) throws ParseError {
    int location = pos;
    if (!tryParseSymbol(symbol))
      throw new ParseError("symbol '" + symbol + "' expected", location);
  }

  private boolean tryParseNumber(Number num) {
    skipWhiteSpace();
    int value = 0;
    boolean stop = false;
    int location = pos;
    while ((pos < cmdLine.length()) && !stop) {
      int digit;
      if ((digit = Util.hexValue(cmdLine.charAt(pos))) >= 0) {
	value = (value << 4) | digit;
	pos++;
      }	else {
	stop = true;
      }
    }
    if (pos > location) {
      num.set(location, value);
      return true;
    }
    return false;
  }

  private void parseNumber(Number num) throws ParseError {
    if (!tryParseNumber(num))
      throw new ParseError("number expected", pos);
  }

  private boolean tryParseRegName(Text regName) {
    skipWhiteSpace();
    boolean stop = false;
    int location = pos;
    while ((pos < cmdLine.length()) && !stop) {
      char ch = cmdLine.charAt(pos);
      if (isRegNameChar(ch) && !isWhiteSpace(ch)) {
	pos++;
      }	else {
	stop = true;
      }
    }
    if (pos > location) {
      regName.set(location, cmdLine.substring(location, pos));
      return true;
    }
    return false;
  }

  private void parseRegName(Text regName) throws ParseError {
    if (!tryParseRegName(regName))
      throw new ParseError("register name expected", pos);
  }

  private boolean tryParseFileName(Text fileName) {
    skipWhiteSpace();
    boolean stop = false;
    int location = pos;
    while ((pos < cmdLine.length()) && !stop) {
      char ch = cmdLine.charAt(pos);
      if (isFileNameChar(ch) && !isWhiteSpace(ch)) {
	pos++;
      }	else {
	stop = true;
      }
    }
    if (pos > location) {
      fileName.set(location, cmdLine.substring(location, pos));
      return true;
    }
    return false;
  }

  private void parseFileName(Text fileName) throws ParseError {
    if (!tryParseFileName(fileName))
      throw new ParseError("file name expected", pos);
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

  private void abortScript() {
    if (scriptLineIndex < script.length) {
      stdout.println("[abort script:");
      while (scriptLineIndex < script.length) {
        stdout.println("  script: " + script[scriptLineIndex++]);
      }
      stdout.println("]");
    }
  }

  private String enterCommand() {
    try {
      stdout.println();
      stdout.print("#");
      stdout.flush();
      return readLine();
    } catch (IOException e) {
      throw new InternalError(e.getMessage());
    }
  }

  private void parseCommand() throws ParseError {
    pos = 0;
    if (eof())
      throw new ParseError("enter command ('h' for help)", pos);
    command = cmdLine.charAt(pos);
    pos = 1;
    num1.reset();
    num2.reset();
    fileName.reset();
    regName.reset();
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
	throw new ParseError("invalid command", pos);
    }
    parseEof();
  }

  // TODO: Specify monitor keyboard check interval by emulation CPU
  // wall clock time (or host CPU time?) rather than as number of
  // emulation CPU instructions.
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

    if (num1.parsed()) {
      codeStartAddr = num1.getValue();
      regPC.setValue(codeStartAddr);
    } else {
      // continue whereever regPC currently points to
    }

    boolean haveBreakPoint;
    int breakPoint;
    if (num2.parsed()) {
      haveBreakPoint = true;
      breakPoint = num2.getValue();
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
              printRegisters();
            }
            if (haveBreakPoint && (regPC.getValue() == breakPoint)) {
              done = true;
            }
            if (kbdCheckCount++ >= KBD_CHECK_COUNT_LIMIT) {
              done |= stdin.available() > 0;
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
          printRegisters();
        }
      } catch (CPU.MismatchException | RuntimeException e) {
        e.printStackTrace(stderr);
      }
      if (stdin.available() > 0) {
        abortScript();
        readLine();
      }
    } catch (IOException e) {
      throw new InternalError(e.getMessage());
    }
    if (!singleStep && !trace) {
      long stopCycle = cpu.getWallClockCycles();
      stdout.printf("[paused]%n");
      stdout.printf("[avg_speed = %.3fMHz]%n",
                    1000.0 * (stopCycle - startCycle) /
                    (systemStopTime - systemStartTime));
      stdout.printf("[busy_wait = %b]%n", BUSY_WAIT);
      if (BUSY_WAIT) {
        stdout.printf("[busy_wait_time = %.2fµs]%n", 0.001 * BUSY_WAIT_TIME);
      }
      stdout.printf("[latest_jitter = %.2fµs]%n", 0.001 * jitter);
      stdout.printf("[avg_thread_load = %3.2f%%]%n",
                    100 * (busyTime / ((float)idleTime + busyTime)));
      stdout.println();
      printRegisters();
    }
    codeStartAddr = regPC.getValue();
  }

  private void save() {
    try {
      OutputStream os =
	new BufferedOutputStream(new FileOutputStream(fileName.getValue()));
      int addr = num1.getValue();
      do {
	int value = memory.readByte(addr, cpu.getWallClockCycles());
	os.write(value);
	addr++;
      } while (addr != num2.getValue());
      os.close();
      stdout.printf("wrote %s bytes to file %s%n",
                    Util.hexShortStr((num2.getValue() - num1.getValue()) &
                                     0xffff),
                    fileName.getValue());
    } catch (IOException e) {
      stderr.println(e.getMessage());
    }
  }

  private void load() {
    try {
      InputStream is =
	new BufferedInputStream(new FileInputStream(fileName.getValue()));
      dataStartAddr = num1.getValue();
      int addr = dataStartAddr;
      int value;
      do {
	value = is.read();
	if (value >= 0) {
	  memory.writeByte(addr, value, cpu.getWallClockCycles());
	  addr++;
	}
      }
      while (value >= 0);
      is.close();
      stdout.printf("loaded %s bytes from file %s%n",
                    Util.hexShortStr((addr - num1.getValue()) & 0xffff),
                    fileName.getValue());
    } catch (IOException e) {
      stderr.println(e.getMessage());
    }
  }

  private int printOperation(CPU.ConcreteOperation op, int fallbackAddress) {
    // TODO: have to know that regPC is a short
    String address;
    if (op == null) {
      address = Util.hexShortStr(fallbackAddress) + "-";
    } else if (!op.isSynthesizedCode()) {
      address = Util.hexShortStr(op.getAddress()) + "-";
    } else {
      address = "INTR:";
    }
    stdout.print(address);
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
      int dataByte = memory.readByte(fallbackAddress, cpu.getWallClockCycles());
      stdout.println(" " + Util.hexByteStr(dataByte) +
                     "               ???");
      length = 1;
    }
    return length;
  }

  private static final int DEFAULT_UNASSEMBLE_LINES = 16;

  private void unassemble() {
    if (num1.parsed())
      codeStartAddr = num1.getValue();
    int endAddr = 0;
    if (num2.parsed())
      endAddr = num2.getValue();
    int regPCValue = regPC.getValue();
    int currentAddr = codeStartAddr;
    CPU.ConcreteOperation op;
    int lineCount = 0;
    do {
      regPC.setValue(currentAddr);
      try {
	op = cpu.fetchNextOperationNoInterrupts();
      } catch (CPU.MismatchException e) {
        op = null;
      }
      currentAddr += printOperation(op, currentAddr);
      currentAddr &= 0xffff; // TODO: 0xffff is z80 specific
    } while ((num2.parsed() && (currentAddr <= endAddr)) ||
             (++lineCount < DEFAULT_UNASSEMBLE_LINES));
    codeStartAddr = currentAddr;
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
    if (num1.parsed())
      dataStartAddr = num1.getValue();
    int stopAddr;
    if (num2.parsed())
      stopAddr = num2.getValue();
    else
      stopAddr = (dataStartAddr + DEFAULT_DUMP_BYTES) &
        ~0xf & 0xffff; // TODO: 0xffff is z80 specific
    int currentAddr = dataStartAddr;
    stdout.print(Util.hexShortStr(currentAddr) + "-  ");
    StringBuffer sbNumeric =
      new StringBuffer(SPACE.substring(0, (currentAddr & 0xf) * 3));
    StringBuffer sbText =
      new StringBuffer(SPACE.substring(0, currentAddr & 0xf));
    do {
      int dataByte = memory.readByte(currentAddr++, cpu.getWallClockCycles());
      currentAddr &= 0xffff;
      sbNumeric.append(" " + Util.hexByteStr(dataByte));
      sbText.append(renderDataByteAsChar(dataByte));
      if ((currentAddr & 0xf) == 0) {
	stdout.println(sbNumeric + "   " + sbText);
	sbNumeric.setLength(0);
	sbText.setLength(0);
	if (currentAddr != stopAddr)
	  stdout.print(Util.hexShortStr(currentAddr) + "-  ");
      }
    } while (currentAddr != stopAddr);
    if ((currentAddr & 0xf) != 0) {
      sbNumeric.append(SPACE.substring(0, (0x10 - currentAddr & 0xf) * 3));
      sbText.append(SPACE.substring(0, 0x10 - currentAddr & 0xf));
      stdout.println(sbNumeric + "   " + sbText);
    }
    dataStartAddr = stopAddr;
  }

  private void enter() throws ParseError {
    if (num1.parsed())
      dataStartAddr = num1.getValue();
    do {
      int dataByte = memory.readByte(dataStartAddr, cpu.getWallClockCycles());
      stdout.print(Util.hexShortStr(dataStartAddr) + "-   (" +
		   Util.hexByteStr(dataByte) + ") ");
      try {
	cmdLine = readLine();
	pos = 0;
      } catch (IOException e) {
	throw new InternalError(e.getMessage());
      }
      if (cmdLine.toUpperCase().equals("Q"))
	break;
      if (eof())
	dataStartAddr++;
      else {
	while (!eof()) {
	  parseNumber(num1);
	  memory.writeByte(dataStartAddr, num1.getValue(),
                           cpu.getWallClockCycles());
	  dataStartAddr++;
	}
      }
    } while (true);
  }

  private void portaccess() {
    int port = num1.getValue();
    if (num2.parsed()) {
      int dataByte = num2.getValue();
      io.writeByte(port, dataByte, cpu.getWallClockCycles());
    } else {
      int dataByte = io.readByte(port, cpu.getWallClockCycles());
      stdout.println(Util.hexByteStr(dataByte));
    }
  }

  private void printRegisters() {
    stdout.print("    ");
    for (int i = 0; i < registers.length; i++) {
      if (i == 7) {
	stdout.println();
	stdout.print("    ");
      }
      CPU.Register register = registers[i];
      stdout.print(" " + register);
    }
    stdout.println();
  }

  private void registerAccess() throws ParseError {
    if (regName.parsed()) {
      CPU.Register matchedRegister = null;
      for (CPU.Register register : registers) {
        if (register.getName().equalsIgnoreCase(regName.getValue())) {
          matchedRegister = register;
          break;
        }
      }
      if (matchedRegister == null) {
        throw new ParseError("no such register: " + regName.getValue(),
                             regName.getLocation());
      }
      if (num1.parsed()) {
        try {
          matchedRegister.setValue(num1.getValue());
        } catch (Exception e) {
          throw new ParseError("failed setting register " +
                               regName.getValue() + ": " + e.getMessage(),
                               regName.getLocation());
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
	registerAccess();
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
    codeStartAddr = 0x0000;
    dataStartAddr = 0x0000;
    welcome();
    num1 = new Number();
    num2 = new Number();
    fileName = new Text();
    regName = new Text();
    num1.reset();
    num2.reset();
    fileName.reset();
    regName.reset();
    printRegisters();
    boolean quit = false;
    while (!quit) {
      try {
        cmdLine = enterCommand();
        if (!cmdLine.isEmpty()) {
          parseCommand();
          history.addEntry(cmdLine);
          executeCommand();
        }
        if (command == 'q') {
          quit = true;
        }
      } catch (ParseError e) {
        stdout.print(e.prettyPrint());
      }
    }
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
	Class<?> cpuClass = Class.forName(cpuClassName);
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
