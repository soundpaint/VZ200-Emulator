package emulator.z80;

import java.awt.Color;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.AdjustmentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelEvent;
import java.io.IOException;
import javax.swing.JFrame;
import javax.swing.JScrollBar;
import javax.swing.SwingUtilities;

// TODO: Rename into "TerminalFrame".
public class GraphicalTerminal extends JFrame
  implements Terminal, TerminalViewChangeListener
{
  private static final long serialVersionUID = 3771448844674425458L;
  private static final boolean DEBUG = false;

  private final TerminalView outputArea;
  private final JScrollBar scrollBar;
  private final CPUControlAPI cpuControl;
  private final LineEditor lineEditor;

  private boolean connected;
  private boolean inputEnabled;

  private class KeyListener extends KeyAdapter
  {
    @Override
    public void keyTyped(final KeyEvent e)
    {
      logDebug("key typed: code=" + e.getKeyCode());
      logDebug("key typed: char=" + e.getKeyChar());
      logDebug("key typed: location=" + e.getKeyLocation());
    }

    @Override
    public void keyPressed(final KeyEvent e)
    {
      final int code = e.getKeyCode();
      switch (code) {
      case KeyEvent.VK_PAGE_UP:
      case KeyEvent.VK_PAGE_DOWN:
        final int maxValue = scrollBar.getMaximum();
        final int oldValue = scrollBar.getValue();
        final int direction = code == KeyEvent.VK_PAGE_UP ? -1 : +1;
        final int increment = e.isShiftDown() ?
          scrollBar.getBlockIncrement(direction) :
          scrollBar.getUnitIncrement(direction);
        final int newValue = oldValue + direction * increment;
        logDebug("setting value " + newValue);
        scrollBar.setValue(newValue);
        break;
      default:
        // do nothing;
      }
    }

    @Override
    public void keyReleased(final KeyEvent e)
    {
      logDebug("key released: code=" + e.getKeyCode());
      logDebug("key released: char=" + e.getKeyChar());
      logDebug("key released: location=" + e.getKeyLocation());
    }
  }

  @Override
  public void setPrompt(final String prompt)
  {
    lineEditor.setPrompt(prompt);
  }

  @Override
  public String getPrompt()
  {
    return lineEditor.getPrompt();
  }

  private final StringBuffer printLineBuffer;

  @Override
  public void print(final String text)
  {
    printLineBuffer.append(text);
    outputArea.setOutputLine(null, printLineBuffer.toString());
  }

  @Override
  public void println()
  {
    printLineBuffer.setLength(0);
    outputArea.newOutputLine(null);
  }

  @Override
  public void println(final String text)
  {
    print(text);
    println();
  }

  @Override
  public void flush()
  {
    // nothing to do, as output is currently not buffered
  }

  @Override
  public void logDebug(final String message)
  {
    if (DEBUG) {
      final Thread currentThread = Thread.currentThread();
      System.out.println(String.format("%s: %s", currentThread, message));
    }
  }

  @Override
  public void logInfo(final String message)
  {
    println(message);
  }

  @Override
  public void logWarn(final String message)
  {
    println(message);
  }

  @Override
  public void logError(final String message)
  {
    println(message);
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
    if (source == scrollBar) {
      throw new RuntimeException("did not expected scroll bar as event source");
    }
    final String threadName = Thread.currentThread().getName();
    if (!threadName.startsWith("AWT-EventQueue-")) {
      throw new RuntimeException("unexpected thread: " + threadName);
    }
    final int scrollLowerBound = 0;
    final int scrollUpperBound =
      Math.max(0, totalLines + totalWraps - viewHeight);
    final int scrollBarValue =
      outputArea.getOffsetY(viewportTopLineIndex) +
      (viewportTopLineOffsetX / viewWidth);
    if (scrollBarValue < scrollLowerBound) {
      throw new RuntimeException("scroll bar value out of range: " +
                                 scrollBarValue + " < " + scrollLowerBound);
    }
    if (scrollBarValue > scrollUpperBound) {
      throw new RuntimeException("scroll bar value out of range: " +
                                 scrollBarValue + " > " + scrollUpperBound);
    }
    final int scrollBarExtent = viewHeight / (1 + totalLines + totalWraps);
    scrollBar.setValues(scrollBarValue, scrollBarExtent,
                        scrollLowerBound, scrollUpperBound);
  }

  @Override
  public void viewSelectionChanged(final Object source)
  {
    if (source != scrollBar) {
      throw new RuntimeException("expected scroll bar as event source");
    }
    final String threadName = Thread.currentThread().getName();
    if (!threadName.startsWith("AWT-EventQueue-")) {
      throw new RuntimeException("unexpected thread: " + threadName);
    }
    outputArea.repaint();
  }

  @Override
  public void viewContentsChanged(final Object source)
  {
    if (source == scrollBar) {
      throw new RuntimeException("did not expected scroll bar as event source");
    }
    final String threadName = Thread.currentThread().getName();
    if (!threadName.startsWith("AWT-EventQueue-")) {
      throw new RuntimeException("unexpected thread: " + threadName);
    }
    outputArea.repaint();
  }

  private void setConnected(final boolean connected)
  {
    this.connected = connected;
  }

  // TODO: Set inputEnabled to false while script is available and not
  // yet exhausted.
  private void setInputEnabled(final boolean inputEnabled)
  {
    this.inputEnabled = inputEnabled;
  }

  private GraphicalTerminal()
  {
    throw new UnsupportedOperationException("unsupported empty constructor");
  }

  private static String createTitle(final String customTitle)
  {
    return customTitle != null ? customTitle : "GraphicalTerminal";
  }

  public GraphicalTerminal(final String customTitle,
                           final int preferredColumns,
                           final int preferredRows,
                           final CPUControlAPI cpuControl)
  {
    super(createTitle(customTitle));
    if (cpuControl == null) {
      throw new NullPointerException("cpuControl");
    }
    printLineBuffer = new StringBuffer();
    outputArea = new TerminalView(1000, preferredColumns, preferredRows);
    outputArea.setEnabled(false);
    lineEditor = new LineEditor(preferredColumns, preferredRows, cpuControl);
    scrollBar = new JScrollBar(JScrollBar.VERTICAL);
    scrollBar.setBackground(Color.BLACK);
    scrollBar.addAdjustmentListener((final AdjustmentEvent e) -> {
        logDebug("adjust=" + e.getValue());
        adjustView(e);
      });
    this.cpuControl = cpuControl;
    //cpuControl.addStateChangeListener(lineEditor);
    setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    addWindowListener(ApplicationExitListener.defaultInstance);
    setLayout(new BorderLayout());
    add(outputArea, BorderLayout.CENTER);
    add(lineEditor.getComponent(), BorderLayout.SOUTH);
    add(scrollBar, BorderLayout.EAST);
    addKeyListener(new KeyListener());
    addKeyListener(lineEditor);
    addMouseWheelListener((final MouseWheelEvent e) -> {
        adjustView(e);
      });
    outputArea.addTerminalViewChangeListener(this);
    pack();
    setVisible(true);
  }

  private void adjustView(final MouseWheelEvent e)
  {
    final int scrollAmount = e.getScrollAmount();
    final int unitsToScroll = e.getUnitsToScroll();
    final int totalScrollAmount;
    switch (e.getScrollType()) {
    case MouseWheelEvent.WHEEL_UNIT_SCROLL:
      final int unitIncrement = scrollBar.getUnitIncrement();
      totalScrollAmount = unitsToScroll * unitIncrement;
      break;
    case MouseWheelEvent.WHEEL_BLOCK_SCROLL:
      final int blockIncrement = scrollBar.getBlockIncrement();
      totalScrollAmount = unitsToScroll * blockIncrement;
      break;
    default:
      totalScrollAmount = 0;
    }
    if (totalScrollAmount != 0) {
      final int oldValue = scrollBar.getValue();
      final int newValue = oldValue + totalScrollAmount;
      logDebug("setting value " + newValue);
      scrollBar.setValue(newValue);
    }
  }

  private void adjustView(final AdjustmentEvent e)
  {
    final int value = e.getValue();
    if (value >= 0) {
      outputArea.setViewOffsetY(e.getSource(), value);
    } else {
      throw new RuntimeException("invalid adjust value: " + value +
                                 ", source=" + e.getSource());
    }
  }

  @Override
  public void setScript(final String script)
  {
    lineEditor.setScript(script);
  }

  /*
  @Override
  public void abortScript()
  {
    lineEditor.abortScript();
  }
  */

  /*
  @Override
  public boolean inputSeen() throws IOException
  {
    return lineEditor.inputSeen();
  }
  */

  @Override
  public String readLine() throws IOException
  {
    final String cmdLine = lineEditor.readLine();
    final String prompt = lineEditor.getPrompt();
    println(prompt + cmdLine);
    return cmdLine;
  }

  @Override
  public void stateChanged(final CPUControlAutomaton.State state)
  {
    switch (state) {
    case RUNNING:
      setConnected(true);
      break;
    case STOPPED:
      setConnected(false);
      break;
    default:
      // not relevant for us
      break;
    }
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
