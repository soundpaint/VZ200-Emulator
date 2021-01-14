// TODO: move this class into new package 'emulator.monitor'.
package emulator.z80;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PushbackInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class Monitor implements CPUControlAPI.LogListener
{
  private CPUControl cpuControl;
  private CPU.Register[] registers;
  private Annotations annotations;
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

  private class FileName extends Text {
    public URL getResource() {
      File resolvedFile;
      String text = getValue();
      resolvedFile = new File(text);
      if (resolvedFile.exists()) {
        try {
          return resolvedFile.toURI().toURL();
        } catch (MalformedURLException e) {
          throw new InternalError("unexpected URL conversion error");
        }
      }
      return cpuControl.resolveLocation(text);
    }

    public InputStream getInputStream() throws IOException {
      File resolvedFile;
      String text = getValue();
      resolvedFile = new File(text);
      if (resolvedFile.exists()) {
        return new FileInputStream(resolvedFile);
      }
      return cpuControl.resolveStream(text);
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
  private static final String SYMBOL_ADD = "+";
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

  private boolean tryParseFileName(FileName fileName) {
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

  private void parseFileName(FileName fileName) throws ParseError {
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
        case java.awt.event.KeyEvent.VK_UP:
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
  private FileName fileName;
  private Text regName;

  private String[] script;
  private int scriptLineIndex;

  private static final boolean DEBUG = false;

  public void logDebug(final String message)
  {
    if (DEBUG) {
      stdout.println(message);
    }
  }

  public void logInfo(final String message)
  {
    stdout.println(message);
  }

  public void logWarn(final String message)
  {
    stderr.println(message);
  }

  public void logError(final String message)
  {
    stderr.println(message);
  }

  public void logOperation(final CPU.ConcreteOperation op)
  {
    printOperation(op, 0);
    printRegisters();
  }

  public void logStatistics(final double avgSpeed,
                            final boolean busyWait,
                            final double jitter,
                            final double avgLoad)
  {
    logInfo(String.format("[avg speed = %.3fMHz]", avgSpeed));
    logInfo(String.format("[busy wait = %b]", busyWait));
    logInfo(String.format("[latest jitter = %.2fµs]", 0.001 * jitter));
    logInfo(String.format("[avg thread load = %3.2f%%]", 100 * avgLoad));
    logInfo("");
    printRegisters();
  }

  public void cpuStopped()
  {
    codeStartAddr = cpuControl.getPCValue();
  }

  private String readLine() throws IOException {
    String cmdLine;
    if ((script != null) && (scriptLineIndex < script.length)) {
      cmdLine = script[scriptLineIndex++];
      logInfo("[" + cmdLine + "]");
    } else {
      cmdLine = readLine(stdin);
    }
    return cmdLine;
  }

  private void abortScript() {
    if (scriptLineIndex < script.length) {
      logInfo("[abort script:");
      while (scriptLineIndex < script.length) {
        logInfo("  script: " + script[scriptLineIndex++]);
      }
      logInfo("]");
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

  private enum AnnotationAction {
    REPLACE_LOAD, APPEND_LOAD, SAVE;
  }

  private AnnotationAction annotationAction;

  private void parseAnnotationCommand() throws ParseError {
    if (tryParseSymbol(SYMBOL_ADD)) {
      parseSymbol(SYMBOL_ASSIGN);
      parseFileName(fileName);
      annotationAction = AnnotationAction.APPEND_LOAD;
    } else if (tryParseSymbol(SYMBOL_ASSIGN)) {
      parseFileName(fileName);
      annotationAction = AnnotationAction.REPLACE_LOAD;
    } else if (tryParseFileName(fileName)) {
      annotationAction = AnnotationAction.SAVE;
    } else {
      throw new ParseError("'+', '=' or <filename> expected", pos);
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
	break;
      case 's' :
	parseFileName(fileName);
        parseSymbol(SYMBOL_ASSIGN);
	parseNumber(num1);
        parseSymbol(SYMBOL_TO);
	parseNumber(num2);
	break;
      case 'l' :
	parseNumber(num1);
        parseSymbol(SYMBOL_ASSIGN);
	parseFileName(fileName);
	break;
      case 'n' :
        parseAnnotationCommand();
	break;
      case 'p' :
	parseNumber(num1);
	if (!eof()) {
          parseSymbol(SYMBOL_ASSIGN);
	  parseNumber(num2);
        }
	break;
      case 'r' :
	if (!eof()) {
	  parseRegName(regName);
	  if (!eof()) {
            parseSymbol(SYMBOL_ASSIGN);
            parseNumber(num1);
          }
	}
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

  private void save() {
    try {
      OutputStream os =
	new BufferedOutputStream(new FileOutputStream(fileName.getValue()));
      int addr = num1.getValue();
      do {
	int value = cpuControl.readByteFromMemory(addr);
	os.write(value);
	addr++;
      } while (addr != num2.getValue());
      os.close();
      logInfo(String.format("wrote %s bytes to file %s",
                            Util.hexShortStr((num2.getValue() - num1.getValue()) &
                                             0xffff),
                            fileName.getValue()));
    } catch (IOException e) {
      logError(e.getMessage());
    }
  }

  private void load() {
    try {
      InputStream is = new BufferedInputStream(fileName.getInputStream());
      dataStartAddr = num1.getValue();
      int addr = dataStartAddr;
      int value;
      do {
	value = is.read();
	if (value >= 0) {
	  cpuControl.writeByteToMemory(addr, value);
	  addr++;
	}
      }
      while (value >= 0);
      is.close();
      logInfo(String.format("loaded %s bytes from file %s",
                            Util.hexShortStr((addr - num1.getValue()) & 0xffff),
                            fileName.getValue()));
    } catch (IOException e) {
      logError(e.getMessage());
    }
  }

  private void loadAnnotations(FileName fileName) {
    URL resource = fileName.getResource();
    try {
      annotations.loadFromResource(resource);
    } catch (ParseException e) {
      logWarn("warning: failed loading annotations from resource " +
              resource + ": " + e.getMessage());
    }
  }

  private void annotate() {
    switch (annotationAction) {
    case REPLACE_LOAD:
      annotations.clear();
      loadAnnotations(fileName);
      break;
    case APPEND_LOAD:
      loadAnnotations(fileName);
      break;
    case SAVE:
      throw new InternalError("not yet implemented");
    default:
      throw new InternalError("unexpected case fall-through");
    }
  }

  private static String fill(char ch, int length) {
    StringBuffer s = new StringBuffer(length);
    for (int i = 0; i < length; i++) {
      s.append(ch);
    }
    return s.toString();
  }

  private static final int MAX_LABEL_LENGTH = 16;
  private static final String LABEL_FORMAT_STRING =
    "%" + MAX_LABEL_LENGTH + "s";
  private static final String EMPTY_LABEL = fill(' ', MAX_LABEL_LENGTH);
  private static final int MAX_DATA_BYTES = 5;
  private static final int COMMENT_POS = 56;

  private int printOperation(CPU.ConcreteOperation op, int fallbackAddress) {
    List<String> lines = new ArrayList<String>();
    StringBuffer lineBuffer = new StringBuffer();
    // TODO: have to know that regPC is a short
    int address;
    String strAddress;
    String label;
    List<String> header, footer, comment;
    boolean containsInnerAnnotation = false;
    boolean containsDataByte = false;
    boolean isDataByte = false;
    if (op != null && op.isSynthesizedCode()) {
      address = 0;
      strAddress = "INTR:";
      label = null;
      header = null;
      footer = null;
      comment = null;
    } else {
      if (op == null) {
        address = fallbackAddress;
      } else {
        address = op.getAddress();
      }
      strAddress = Util.hexShortStr(address) + "-";
      String fullLabel = annotations.getLabel(address);
      label =
        fullLabel != null ?
        (fullLabel.length() > MAX_LABEL_LENGTH ?
         fullLabel.substring(0, MAX_LABEL_LENGTH) :
         fullLabel) :
        null;
      header = annotations.getHeader(address);
      comment = annotations.getComment(address);
      int opCodeLength = op != null ? op.getByteLength() : 1;
      for (int i = 0; i < opCodeLength; i++) {
        // TODO: 0xffff is z80 specific
        int opCodeByteAddress = (address + i) & 0xffff;
        boolean opCodeByteIsDataByte =
          annotations.isDataByte(opCodeByteAddress);
        if (i == 0) {
          isDataByte = opCodeByteIsDataByte;
        } else {
          containsInnerAnnotation |=
            annotations.getLabel(opCodeByteAddress) != null;
          containsInnerAnnotation |=
            annotations.getHeader(opCodeByteAddress) != null;
          containsInnerAnnotation |=
            annotations.getComment(opCodeByteAddress) != null;
        }
        if (i < opCodeLength - 1) {
          containsInnerAnnotation |=
            annotations.getFooter(opCodeByteAddress) != null;
        }
        containsDataByte |= opCodeByteIsDataByte;
      }
    }
    if (header != null) {
      lines.add("");
      for (String line : header) {
        lines.add("      ;" + line);
      }
    }
    lineBuffer.append(strAddress);
    lineBuffer.append("  ");
    lineBuffer.append(String.format(LABEL_FORMAT_STRING,
                                    label != null ? label : EMPTY_LABEL));
    lineBuffer.append(" ");
    int length;
    if (op == null || containsDataByte || containsInnerAnnotation) {
      int dataBytesLength;
      if (isDataByte) {
        dataBytesLength = annotations.getRemainingDataBytes(address);
      } else {
        dataBytesLength = 1;
      }
      lineBuffer.append(" ");
      StringBuffer strDataBytes = new StringBuffer();
      for (int i = 0; i < MAX_DATA_BYTES; i++) {
        if (i < dataBytesLength) {
          int dataByte = cpuControl.readByteFromMemory(fallbackAddress + i);
          String strDataByte = Util.hexByteStr(dataByte);
          lineBuffer.append(strDataByte);
          strDataBytes.append(strDataByte);
        } else {
          lineBuffer.append("  ");
        }
        lineBuffer.append(" ");
      }
      lineBuffer.append("  ");
      if (isDataByte || containsInnerAnnotation) {
        String mnemonic = annotations.getDataBytesMnemonic(address);
        if (mnemonic != null) {
          lineBuffer.append(mnemonic);
        } else {
          lineBuffer.append("DB " + strDataBytes);
        }
      } else {
        lineBuffer.append("???");
      }
      length = dataBytesLength < MAX_DATA_BYTES ?
        dataBytesLength : MAX_DATA_BYTES;
    } else  {
      CPU.ConcreteOpCode opCode = op.createOpCode();
      length = opCode.getLength();
      for (int i = 0; i < length; i++) {
        lineBuffer.append(" " + Util.hexByteStr(opCode.getByte(i)));
      }
      for (int i = 0; i < (6 - length); i++) {
        lineBuffer.append("   ");
      }
      lineBuffer.append(op.getConcreteMnemonic());
    }
    if (comment != null) {
      for (String commentLine : comment) {
        int prevLength = lineBuffer.length();
        if (COMMENT_POS < prevLength) {
          lines.add(lineBuffer.toString());
          prevLength = 0;
        }
        lineBuffer.setLength(COMMENT_POS);
        for (int i = prevLength; i < lineBuffer.length(); i++) {
          lineBuffer.setCharAt(i, ' ');
        }
        lineBuffer.append(";");
        lineBuffer.append(commentLine);
        lines.add(lineBuffer.toString());
        lineBuffer.setLength(0);
      }
    }
    if (lineBuffer.length() > 0) {
      lines.add(lineBuffer.toString());
    }
    footer = annotations.getFooter((address + length - 1) & 0xffff);
    if (footer != null) {
      for (String line : footer) {
        lines.add("      ;" + line);
      }
      lines.add("");
    }
    for (String line : lines) {
      logInfo(line);
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
    int regPCValue = cpuControl.getPCValue();
    int currentAddr = codeStartAddr;
    CPU.ConcreteOperation op;
    int lineCount = 0;
    do {
      cpuControl.setPCValue(currentAddr);
      try {
	op = cpuControl.fetchNextOperationNoInterrupts();
      } catch (CPU.MismatchException e) {
        op = null;
      }
      currentAddr += printOperation(op, currentAddr);
      currentAddr &= 0xffff; // TODO: 0xffff is z80 specific
    } while ((num2.parsed() && (currentAddr <= endAddr)) ||
             (++lineCount < DEFAULT_UNASSEMBLE_LINES));
    codeStartAddr = currentAddr;
    cpuControl.setPCValue(regPCValue);
  }

  private void assemble() {
    logError("'a[<addr>]': not yet implemented");
  }

  private final static String SPACE =
  "                                                ";

  private static boolean isPrintableChar(int ch) {
    return (ch >= 0x20) && (ch < 0x7f);
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
      int dataByte = cpuControl.readByteFromMemory(currentAddr++);
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
      int dataByte = cpuControl.readByteFromMemory(dataStartAddr);
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
	  cpuControl.writeByteToMemory(dataStartAddr, num1.getValue());
	  dataStartAddr++;
	}
      }
    } while (true);
  }

  private void portaccess() {
    int port = num1.getValue();
    if (num2.parsed()) {
      int dataByte = num2.getValue();
      cpuControl.writeByteToPort(port, dataByte);
    } else {
      int dataByte = cpuControl.readByteFromPort(port);
      logInfo(Util.hexByteStr(dataByte));
    }
  }

  private void printRegisters() {
    final StringBuffer s = new StringBuffer();
    s.append("    ");
    for (int i = 0; i < registers.length; i++) {
      if (i == 7) {
        logInfo(s.toString());
        s.setLength(0);
	s.append("    ");
      }
      CPU.Register register = registers[i];
      s.append(" " + register);
    }
    logInfo(s.toString());
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
      logInfo(String.format("     %s", matchedRegister));
    } else {
      printRegisters();
    }
  }

  private void help() {
    logInfo("Commands");
    logInfo("========");
    logInfo("");
    logInfo("Code Execution");
    logInfo("  g[<startaddr>][-<stopaddr>]      go [until]");
    logInfo("  t[<startaddr>][-<stopaddr>]      trace [until]");
    logInfo("  i[<addr>]                        single step into");
    logInfo("  o[<addr>]                        step over <not yet implemented>");
    logInfo("");
    logInfo("Code / Data Listing");
    logInfo("  u[<startaddr>][-<stopaddr>]      unassemble");
    logInfo("  d[<startaddr>][-<stopaddr>]      dump data");
    logInfo("");
    logInfo("Code / Data Entry");
    logInfo("  a[<addr>]                        assemble <not yet implemented>");
    logInfo("  e[<addr>]                        enter data");
    logInfo("");
    logInfo("I/O");
    logInfo("  p<addr>[=<data>]                 port access");
    logInfo("");
    logInfo("CPU Status");
    logInfo("  r[<regname>[=<data>]]            register access");
    logInfo("");
    logInfo("Load / Save");
    logInfo("  s<name>=<startaddr>-<stopaddr>   save data to disk");
    logInfo("  l<startaddr>=<name>              load data from disk");
    logInfo("");
    logInfo("Annotations");
    logInfo("  n=<filename>                     replace load");
    logInfo("  n+=<filename>                    append load");
    logInfo("");
    logInfo("Miscelleanous");
    logInfo("  h                                help (this page)");
    logInfo("  q                                quit");
  }

  private class KeyWatch extends Thread implements CPUControlAutomaton.Listener
  {
    private boolean stopping;
    private boolean inputSeen;

    public boolean inputSeen() throws IOException
    {
      inputSeen |= stdin.available() > 0;
      return inputSeen;
    }

    public void run()
    {
      boolean haveException = false;
      inputSeen = false;
      try {
        while (!stopping && !inputSeen()) {
          try {
            Thread.sleep(100);
          } catch (final InterruptedException e) {
            // ignore
          }
        }
      } catch (final IOException e) {
        haveException = true;
        /**
         * if checking for kbd input throws an exception, there is a
         * fundamental problem.  In this case, we log this error and
         * prematurely stop watching for keys.
         */
        logError("failed watching for keyboard actions, " +
                 "stopping key watch thread: " + e.getMessage());
      }
      // if CPU not already stopped by itself, try stopping it
      cpuControl.stop();
    }

    public void requestStop()
    {
      logDebug("KeyWatch: requestStop()");
      final Exception e = new Exception();
      stopping = true;
    }

    public void stateChanged(final CPUControlAutomaton.State state)
    {
      logDebug("KeyWatch: state=" + state);
      if (state == CPUControlAutomaton.State.STOPPING) {
        stopping = true;
      }
    }
  }

  private void runCPU()
  {
    final KeyWatch keyWatch = new KeyWatch();
    try {
      synchronized(cpuControl) {
        if ((command == 'g') || (command == 't')) {
          logInfo("press <enter> to pause");
        }
        cpuControl.setSingleStep(command == 'i');
        cpuControl.setTrace(command == 't');
        if (num1.parsed()) {
          codeStartAddr = num1.getValue();
          cpuControl.setPCValue(codeStartAddr);
        } else {
          // continue whereever regPC currently points to
        }
        cpuControl.addStateChangeListener(keyWatch);
        keyWatch.start();
        final Integer breakPoint = num2.parsed() ? num2.getValue() : null;
        cpuControl.setBreakPoint(breakPoint);
        cpuControl.execute();
        cpuControl.awaitStop();
        try {
          if (keyWatch.inputSeen()) {
            abortScript();
            readLine();
          }
        } catch (final IOException e) {
          throw new InternalError(e.getMessage());
        }
      }
    } finally {
      cpuControl.removeStateChangeListener(keyWatch);
    }
  }

  private void executeCommand() throws ParseError {
    switch (command) {
      case 'g' :
      case 't' :
      case 'i' :
        runCPU();
	break;
      case 'o' :
        logError("'o[<addr>]': not yet implemented");
	break;
      case 's' :
	save();
	break;
      case 'l' :
	load();
	break;
      case 'n' :
	annotate();
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
    logInfo("");
    logInfo("****************************************");
    logInfo("* Monitor V/0.1                        *");
    logInfo("* (C) 2001, 2010 Jürgen Reuter,        *");
    logInfo("* Karlsruhe                            *");
    logInfo("****************************************");
    logInfo("");
    logInfo("Enter 'h' for help.");
    logInfo("");
  }

  /**
   * Executes a sequence of monitor commands.  Adjacent commands are
   * separated by line breaks (either CR+LF characters, or LF
   * character only).
   * @param script The script to execute.
   */
  public void run(String script) {
    if ((script != null) && (!script.isEmpty())) {
      this.script = script.split("\r?\n");
      scriptLineIndex = 0;
    }
    run();
  }

  private void run() {
    stdout = System.out;
    logInfo("starting monitor...");
    stderr = System.err;
    stdin = new PushbackInputStream(System.in);
    registers = cpuControl.getAllRegisters();
    codeStartAddr = 0x0000;
    dataStartAddr = 0x0000;
    welcome();
    num1 = new Number();
    num2 = new Number();
    fileName = new FileName();
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
        logError(e.prettyPrint());
      }
    }
    System.exit(0);
  }

  private Monitor() {
    throw new UnsupportedOperationException("unsupported empty constructor");
  }

  public Monitor(CPUControl cpuControl) {
    this.cpuControl = cpuControl;
    cpuControl.addLogListener(this);
    annotations = cpuControl.getAnnotations();
    history = new History();
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
	cpu = (CPU)cpuClass.getDeclaredConstructor().newInstance();
      } catch (Exception e) {
	System.err.println("Could not instantiate CPU class '" +
                           cpuClassName + "': " + e);
	System.exit(-1);
      }
      final CPUControl cpuControl = new CPUControl(cpu);
      new Monitor(cpuControl).run();
    }
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
