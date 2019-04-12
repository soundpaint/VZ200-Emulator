package emulator.z80;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.util.concurrent.LinkedBlockingQueue;
import javax.swing.JComponent;

public class LineEditor extends KeyAdapter
  implements TerminalViewChangeListener, CPUControlAutomatonListener
{
  private static final String DEFAULT_PROMPT = "$ ";
  private static final String SPACE = " ";
  private static final boolean DEBUG = true;
  private static final int LINE_INPUT_CAPACITY = 1000;
  private static final int KEY_INPUT_CAPACITY = 1000;
  private static final int ASCII_CAPITAL_LETTERS_BASE = '@';

  private final TerminalView inputArea;
  private final CPUControlAPI cpuControl;
  //private final PushbackInputStream stdin;
  //private final KeyWatch keyWatch;
  private final History history;
  private StringBuffer lineBuffer;
  private int cursorPosition;
  private String clipBoard;
  private boolean deleteInProgress;
  private String prompt;
  private LinkedBlockingQueue<String> lineInputBuffer;
  private LinkedBlockingQueue<Character> keyInputBuffer;
  private boolean active;

  public LineEditor(final int preferredColumns, final int preferredRows,
                    final CPUControlAPI cpuControl)
  {
    this.inputArea = createInputArea(preferredColumns, preferredRows);
    if (cpuControl == null) {
      throw new NullPointerException("cpuControl");
    }
    this.cpuControl = cpuControl;
    active = false;
    lineInputBuffer = new LinkedBlockingQueue<String>(LINE_INPUT_CAPACITY);
    keyInputBuffer = new LinkedBlockingQueue<Character>(KEY_INPUT_CAPACITY);
    history = new History();
    clipBoard = "";
    deleteInProgress = false;
    prompt = DEFAULT_PROMPT;
    lineBuffer = new StringBuffer();
    cursorPosition = 0;
    history.newEntry();
    inputArea.addTerminalViewChangeListener(this);
    cpuControl.addStateChangeListener(this);
  }

  @Override
  public void viewPaneChanged(final Object source,
                              final int totalLines,
                              final int totalWraps,
                              final int viewportTopLineIndex,
                              final int viewportTopLineOffsetX,
                              final int viewWidth,
                              final int viewHeight)
  {
    // we have no scrollbar => nothing to do
  }

  @Override
  public void viewSelectionChanged(final Object source)
  {
    throw new RuntimeException("did not expect selection change event on input area");
  }

  @Override
  public void viewContentsChanged(final Object source)
  {
    inputArea.repaint();
  }

  @Override
  public void stateChanged(final CPUControlAutomaton.State state)
  {
    switch (state) {
    case STARTING:
      inputArea.setEnabled(false);
      break;
    case RUNNING:
      inputArea.setEnabled(false);
      break;
    case STOPPING:
      inputArea.setEnabled(false);
      break;
    case STOPPED:
      inputArea.setEnabled(true);
      break;
    default:
      throw new InternalError("unexpected case fall-through: state=" + state);
    }
  }

  public JComponent getComponent()
  {
    return inputArea;
  }

  public void setPrompt(String prompt)
  {
    this.prompt = prompt;
  }

  public String getPrompt()
  {
    return prompt;
  }

  private TerminalView createInputArea(final int preferredColumns,
                                        final int preferredRows)
  {
    final TerminalView inputArea =
      new TerminalView(1, preferredColumns, preferredRows);
    inputArea.setPreferredSize(inputArea.getMinimumSize());
    return inputArea;
  }

  private void clearInputLine()
  {
    inputArea.setOutputLine(this, "");
    inputArea.hideCursor();
  }

  private void updateInputLine()
  {
    final String line = lineBuffer.toString();
    inputArea.setOutputLine(this, prompt + line + SPACE);
    final int promptLength = prompt.length();
    inputArea.showCursor(promptLength + cursorPosition);
    history.setEntry(line);
  }

  private void setLine(final String contents)
  {
    lineBuffer.setLength(0);
    lineBuffer.append(contents);
    cursorPosition = contents.length();
    updateInputLine();
  }

  private void handleInsertChar(final char ch)
  {
    lineBuffer.insert(cursorPosition++, ch);
    updateInputLine();
    deleteInProgress = false;
  }

  private void handleCursorLeft()
  {
    if (cursorPosition > 0) {
      cursorPosition--;
      updateInputLine();
    }
    deleteInProgress = false;
  }

  private void handleCursorRight()
  {
    if (cursorPosition < lineBuffer.length()) {
      cursorPosition++;
      updateInputLine();
    }
    deleteInProgress = false;
  }

  private boolean isAlphaNumeric(final int offset)
  {
    final int charPosition = cursorPosition + offset;
    if (charPosition < 0) {
      return true;
    }
    if (charPosition >= lineBuffer.length()) {
      return true;
    }
    final int ch = lineBuffer.charAt(charPosition);
    switch (Character.getType(ch)) {
    case Character.UPPERCASE_LETTER:
    case Character.LOWERCASE_LETTER:
    case Character.TITLECASE_LETTER:
    case Character.MODIFIER_LETTER:
    case Character.OTHER_LETTER:
    case Character.DECIMAL_DIGIT_NUMBER:
    case Character.LETTER_NUMBER:
    case Character.OTHER_NUMBER:
      return true;
    default:
      return false;
    }
  }

  private void handleToggle()
  {
    if (cursorPosition > 0) {
      final char swap = lineBuffer.charAt(cursorPosition - 1);
      lineBuffer.setCharAt(cursorPosition - 1,
                            lineBuffer.charAt(cursorPosition));
      lineBuffer.setCharAt(cursorPosition, swap);
      if (cursorPosition < lineBuffer.length()) {
        cursorPosition++;
      }
      updateInputLine();
    }
    deleteInProgress = false;
  }

  private void clipBoardAppend(final int from, final int to)
  {
    if (!deleteInProgress) {
      clipBoard = "";
    }
    clipBoard += lineBuffer.substring(from, to);
  }

  private void clipBoardPrepend(final int from, final int to)
  {
    if (!deleteInProgress) {
      clipBoard = "";
    }
    clipBoard = lineBuffer.substring(from, to) + clipBoard;
  }

  private void handleCursorLeftWord()
  {
    final int originalCursorPosition = cursorPosition;
    while ((cursorPosition > 0) && !isAlphaNumeric(-1)) {
      cursorPosition--;
    }
    while ((cursorPosition > 0) && isAlphaNumeric(-1)) {
      cursorPosition--;
    }
    if (cursorPosition != originalCursorPosition) {
      updateInputLine();
    }
    deleteInProgress = false;
  }

  private void handleCursorRightWord()
  {
    final int originalCursorPosition = cursorPosition;
    while ((cursorPosition < lineBuffer.length()) && !isAlphaNumeric(0)) {
      cursorPosition++;
    }
    while ((cursorPosition < lineBuffer.length()) && isAlphaNumeric(0)) {
      cursorPosition++;
    }
    if (cursorPosition != originalCursorPosition) {
      updateInputLine();
    }
    deleteInProgress = false;
  }

  private void handleDeleteLeftWord()
  {
    final int originalCursorPosition = cursorPosition;
    while ((cursorPosition > 0) && !isAlphaNumeric(-1)) {
      cursorPosition--;
    }
    while ((cursorPosition > 0) && isAlphaNumeric(-1)) {
      cursorPosition--;
    }
    if (cursorPosition != originalCursorPosition) {
      clipBoardPrepend(cursorPosition, originalCursorPosition);
      lineBuffer.delete(cursorPosition, originalCursorPosition);
      updateInputLine();
    }
    deleteInProgress = true;
  }

  private void handleDeleteRightWord()
  {
    final int originalCursorPosition = cursorPosition;
    while ((cursorPosition < lineBuffer.length()) && !isAlphaNumeric(0)) {
      cursorPosition++;
    }
    while ((cursorPosition < lineBuffer.length()) && isAlphaNumeric(0)) {
      cursorPosition++;
    }
    if (cursorPosition != originalCursorPosition) {
      clipBoardAppend(originalCursorPosition, cursorPosition);
      lineBuffer.delete(originalCursorPosition, cursorPosition);
      updateInputLine();
    }
    deleteInProgress = true;
  }

  private void handleCursorHome()
  {
    if (cursorPosition > 0) {
      cursorPosition = 0;
      updateInputLine();
    }
    deleteInProgress = false;
  }

  private void handleCursorEnd()
  {
    if (cursorPosition < lineBuffer.length()) {
      cursorPosition = lineBuffer.length();
      updateInputLine();
    }
    deleteInProgress = false;
  }

  private void handleDeleteBackwards()
  {
    if (cursorPosition > 0) {
      cursorPosition--;
      lineBuffer.delete(cursorPosition, cursorPosition + 1);
      updateInputLine();
    }
    deleteInProgress = false;
  }

  private void handleDelete()
  {
    if (cursorPosition < lineBuffer.length()) {
      lineBuffer.delete(cursorPosition, cursorPosition + 1);
      updateInputLine();
    }
    deleteInProgress = false;
  }

  private void handleCutToBegin()
  {
    if (cursorPosition > 0) {
      clipBoardPrepend(0, cursorPosition);
      lineBuffer.delete(0, cursorPosition);
      cursorPosition = 0;
      updateInputLine();
    }
    deleteInProgress = true;
  }

  private void handleCutToEnd()
  {
    if (cursorPosition < lineBuffer.length()) {
      clipBoardAppend(cursorPosition, lineBuffer.length());
      lineBuffer.delete(cursorPosition, lineBuffer.length());
      updateInputLine();
    }
    deleteInProgress = true;
  }

  private void handlePasteDeletedWord()
  {
    if (clipBoard != null) {
      if (clipBoard.length() > 0) {
        lineBuffer.insert(cursorPosition, clipBoard);
        cursorPosition += clipBoard.length();
        updateInputLine();
      }
    }
    deleteInProgress = false;
  }

  private void handleHistoryNext()
  {
    if (history.decrementPreCursor()) {
      setLine(history.getPreCursorEntry());
    }
    deleteInProgress = false;
  }

  private void handleHistoryPrevious()
  {
    if (history.incrementPreCursor()) {
      setLine(history.getPreCursorEntry());
    }
    deleteInProgress = false;
  }

  private void handleReverseISearch()
  {
    // TODO
    System.out.println("TODO: handle reverse i-search");
    deleteInProgress = false;
  }

  private void handleClearLine()
  {
    if (lineBuffer.length() > 0) {
      cursorPosition = 0;
      lineBuffer.setLength(0);
      updateInputLine();
    }
    deleteInProgress = false;
  }

  private void handleClearScreen()
  {
    // TODO
    System.out.println("TODO: clear screen");
    deleteInProgress = false;
  }

  private void handleStop()
  {
    // TODO
    System.out.println("TODO: handle stop");
    deleteInProgress = false;
  }

  private void handleQuit()
  {
    // TODO
    System.out.println("TODO: handle quit from stop");
    deleteInProgress = false;
  }

  private void updateToolTipText()
  {
    final StringBuffer toolTipTextBuffer = new StringBuffer();
    for (final String line : lineInputBuffer) {
      toolTipTextBuffer.append(line);
      toolTipTextBuffer.append("\r\n");
    }
    final String toolTipText =
      toolTipTextBuffer.length() > 0 ? toolTipTextBuffer.toString() : null;
    inputArea.setToolTipText(toolTipText);
  }

  private String takeLine()
  {
    String line;
    do {
      try {
        line = lineInputBuffer.take();
        updateToolTipText();
      } catch (final InterruptedException ex) {
        line = null;
      }
    } while (line == null);
    return line;
  }

  private void putLine(final String line)
  {
    boolean interrupted;
    do {
      try {
        lineInputBuffer.put(line);
        updateToolTipText();
        interrupted = false;
      } catch (final InterruptedException ex) {
        interrupted = true;
      }
    } while (interrupted);
  }

  private void handleEnter()
  {
    final String line = lineBuffer.toString();
    putLine(line);
    history.setEntry(line);
    history.newEntry();
    setLine("");
    deleteInProgress = false;
  }

  private void handleRedo()
  {
    final String line = lineBuffer.toString();
    handleEnter();
    lineBuffer.append(line);
    updateInputLine();
    deleteInProgress = false;
  }

  private void handleVerbatim()
  {
    // TODO
    System.out.println("TODO: handle verbatim");
    deleteInProgress = false;
  }

  @Override
  public void keyTyped(final KeyEvent e)
  {
    final char ch = e.getKeyChar();
    if (active) {
      handleTypedChar(ch);
    } else {
      // if key input buffer is full, ignore input
      keyInputBuffer.offer(ch);
    }
  }

  private void handleTypedChar(final char ch)
  {
    if (ch < ' ') {
      handleTypedControlChar(ch);
    } else if (ch == KeyEvent.VK_DELETE) {
      // already handled by method unmodifiedKeyPressed()
    } else if (ch == 0) {
      // ignore
    } else if (ch == KeyEvent.VK_ENTER) {
      handleEnter();
    } else if (ch >= ' ') {
      handleInsertChar(ch);
    }
  }

  private void handleTypedControlChar(final char ch)
  {
    if (ch == KeyEvent.VK_BACK_SPACE) {
      // already handled by method unmodifiedKeyPressed()
    } else if (ch == KeyEvent.VK_A - ASCII_CAPITAL_LETTERS_BASE) {
      handleCursorHome();
    } else if (ch == KeyEvent.VK_B - ASCII_CAPITAL_LETTERS_BASE) {
      handleCursorLeft();
    } else if (ch == KeyEvent.VK_C - ASCII_CAPITAL_LETTERS_BASE) {
      handleClearLine();
    } else if (ch == KeyEvent.VK_D - ASCII_CAPITAL_LETTERS_BASE) {
      handleDelete();
    } else if (ch == KeyEvent.VK_E - ASCII_CAPITAL_LETTERS_BASE) {
      handleCursorEnd();
    } else if (ch == KeyEvent.VK_F - ASCII_CAPITAL_LETTERS_BASE) {
      handleCursorRight();
    } else if (ch == KeyEvent.VK_H - ASCII_CAPITAL_LETTERS_BASE) {
      handleDeleteBackwards();
    } else if (ch == KeyEvent.VK_J - ASCII_CAPITAL_LETTERS_BASE) {
      handleEnter();
    } else if (ch == KeyEvent.VK_K - ASCII_CAPITAL_LETTERS_BASE) {
      handleCutToEnd();
    } else if (ch == KeyEvent.VK_L - ASCII_CAPITAL_LETTERS_BASE) {
      handleClearScreen();
    } else if (ch == KeyEvent.VK_M - ASCII_CAPITAL_LETTERS_BASE) {
      handleEnter();
    } else if (ch == KeyEvent.VK_N - ASCII_CAPITAL_LETTERS_BASE) {
      handleHistoryNext();
    } else if (ch == KeyEvent.VK_O - ASCII_CAPITAL_LETTERS_BASE) {
      handleRedo();
    } else if (ch == KeyEvent.VK_P - ASCII_CAPITAL_LETTERS_BASE) {
      handleHistoryPrevious();
    } else if (ch == KeyEvent.VK_Q - ASCII_CAPITAL_LETTERS_BASE) {
      handleQuit();
    } else if (ch == KeyEvent.VK_R - ASCII_CAPITAL_LETTERS_BASE) {
      handleReverseISearch();
    } else if (ch == KeyEvent.VK_S - ASCII_CAPITAL_LETTERS_BASE) {
      handleStop();
    } else if (ch == KeyEvent.VK_T - ASCII_CAPITAL_LETTERS_BASE) {
      handleToggle();
    } else if (ch == KeyEvent.VK_U - ASCII_CAPITAL_LETTERS_BASE) {
      handleCutToBegin();
    } else if (ch == KeyEvent.VK_V - ASCII_CAPITAL_LETTERS_BASE) {
      handleVerbatim();
    } else if (ch == KeyEvent.VK_W - ASCII_CAPITAL_LETTERS_BASE) {
      handleDeleteLeftWord();
    } else if (ch == KeyEvent.VK_Y - ASCII_CAPITAL_LETTERS_BASE) {
      handlePasteDeletedWord();
    } else {
      // ignore any other control keys
    }
  }

  @Override
  public void keyPressed(final KeyEvent e)
  {
    if (active) {
      if (!e.isAltDown() && !e.isAltGraphDown() &&
          !e.isControlDown() && !e.isMetaDown() && !e.isShiftDown()) {
        unmodifiedKeyPressed(e);
      } else if (!e.isAltDown() && !e.isAltGraphDown() &&
                 e.isControlDown() && !e.isMetaDown() && !e.isShiftDown()) {
        controlKeyPressed(e);
      }
    } else {
      handleNonEditorKeys(e);
    }
  }

  /**
   * When line editor is inactive, CTRL-Z will be recognized as
   * request for interrupting the current process and gaining back
   * process control by activating the line editor.
   */
  private void handleNonEditorKeys(final KeyEvent e)
  {
    final char ch = e.getKeyChar();
    if (ch == KeyEvent.VK_Z - ASCII_CAPITAL_LETTERS_BASE) {
      cpuControl.stop();
    }
  }

  private void unmodifiedKeyPressed(final KeyEvent e)
  {
    if (!active) {
      // ignore special keys when editor not active
    }
    final int code = e.getKeyCode();
    switch (code) {
    case KeyEvent.VK_HOME:
      handleCursorHome();
      break;
    case KeyEvent.VK_END:
      handleCursorEnd();
      break;
    case KeyEvent.VK_BACK_SPACE:
      handleDeleteBackwards();
      break;
    case KeyEvent.VK_DELETE:
      handleDelete();
      break;
    case KeyEvent.VK_LEFT:
    case KeyEvent.VK_KP_LEFT:
      handleCursorLeft();
      break;
    case KeyEvent.VK_RIGHT:
    case KeyEvent.VK_KP_RIGHT:
      handleCursorRight();
      break;
    case KeyEvent.VK_UP:
    case KeyEvent.VK_KP_UP:
      handleHistoryPrevious();
      break;
    case KeyEvent.VK_DOWN:
    case KeyEvent.VK_KP_DOWN:
      handleHistoryNext();
      break;
    default:
      // ignore other keys
      break;
    }
  }

  private void controlKeyPressed(final KeyEvent e)
  {
    final int code = e.getKeyCode();
    switch (code) {
    case KeyEvent.VK_LEFT:
    case KeyEvent.VK_KP_LEFT:
      handleCursorLeftWord();
      break;
    case KeyEvent.VK_RIGHT:
    case KeyEvent.VK_KP_RIGHT:
      handleCursorRightWord();
      break;
    default:
      // ignore other keys
      break;
    }
  }

  private void log(final String message)
  {
    final Thread currentThread = Thread.currentThread();
    System.out.println(String.format("%s: %s", currentThread, message));
  }

  private void logDebug(final String message)
  {
    if (DEBUG) {
      log(message);
    }
  }

  private void logInfo(final String message)
  {
    log(message);
  }

  public void setScript(final String script)
  {
    if ((script != null) && (!script.isEmpty())) {
      final String[] lines = script.split("\r?\n");
      for (final String line : lines) {
        putLine(line);
      }
    }
  }

  public String readLine() throws IOException
  {
    updateInputLine();
    active = true;
    final String line = takeLine();
    active = false;
    clearInputLine();
    history.setEntry(line);
    history.newEntry();
    return line;
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
