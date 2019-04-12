package emulator.z80;

import java.awt.Point;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;

public class TerminalModel
{
  public static final int DEFAULT_LINE_MAX_LENGTH = 1024;

  private final int lineMaxLength;
  private final int maxLines;
  private final String[] lines;
  private final int[] lineWraps;
  private final List<TerminalViewChangeListener> listeners;
  private int topLineIndex;
  private int viewportTopLineIndex;
  private int viewportTopLineOffsetX;
  private int outputLineIndex;
  private int viewWidth, viewHeight;
  private int totalWraps;
  private int totalLines;
  private int selectionStartLineIndex;
  private int selectionStartLineOffsetX;
  private int selectionEndLineIndex;
  private int selectionEndLineOffsetX;

  private TerminalModel()
  {
    throw new UnsupportedOperationException("unsupported constructor");
  }

  public TerminalModel(final int lineMaxLength, final int maxLines)
  {
    if (lineMaxLength < 1) {
      throw new IllegalArgumentException("lineMaxLength < 1: " +
                                         lineMaxLength);
    }
    this.lineMaxLength = lineMaxLength;
    if (maxLines < 1) {
      throw new IllegalArgumentException("maxLines < 1: " + maxLines);
    }
    lines = new String[maxLines];
    lineWraps = new int[maxLines];
    for (int i = 0; i < maxLines; i++) {
      lines[i] = null;
      lineWraps[i] = 0;
    }
    this.maxLines = maxLines;
    topLineIndex = 0;
    outputLineIndex = 0;
    viewportTopLineIndex = 0;
    viewportTopLineOffsetX = 0;
    viewWidth = 1;
    viewHeight = 1;
    totalWraps = 0;
    totalLines = 0;
    selectionStartLineIndex = 0;
    selectionStartLineOffsetX = -1;
    selectionEndLineIndex = 0;
    selectionEndLineOffsetX = -1;
    listeners = new ArrayList<TerminalViewChangeListener>();
  }

  public void addChangeListener(final TerminalViewChangeListener listener)
  {
    listeners.add(listener);
    SwingUtilities.invokeLater(() -> {
        listener.viewPaneChanged(listener,
                                 totalLines, totalWraps,
                                 viewportTopLineIndex,
                                 viewportTopLineOffsetX,
                                 viewWidth, viewHeight);
      });
  }

  private void viewPaneChanged(final Object eventSource)
  {
    SwingUtilities.invokeLater(() -> {
        for (final TerminalViewChangeListener listener : listeners) {
          listener.viewPaneChanged(eventSource,
                                   totalLines, totalWraps,
                                   viewportTopLineIndex,
                                   viewportTopLineOffsetX,
                                   viewWidth, viewHeight);
        }
      });
  }

  private void viewContentsChanged(final Object eventSource)
  {
    SwingUtilities.invokeLater(() -> {
        for (final TerminalViewChangeListener listener : listeners) {
          listener.viewContentsChanged(eventSource);
        }
      });
  }

  private void viewSelectionChanged(final Object eventSource)
  {
    SwingUtilities.invokeLater(() -> {
        for (final TerminalViewChangeListener listener : listeners) {
          listener.viewSelectionChanged(eventSource);
        }
      });
  }

  public void setViewWidth(final Object eventSource, final int viewWidth)
  {
    if (viewWidth < 1) {
      throw new IllegalArgumentException("viewWidth < 1: " + viewWidth);
    }
    if (this.viewWidth != viewWidth) {
      this.viewWidth = viewWidth;
      updateLineWraps();
      updateViewportTopLine();
      viewPaneChanged(eventSource);
    }
  }

  public int getViewWidth()
  {
    return viewWidth;
  }

  public void setViewHeight(final Object eventSource, final int viewHeight)
  {
    if (viewHeight < 1) {
      throw new IllegalArgumentException("viewHeight < 1: " + viewHeight);
    }
    if (this.viewHeight != viewHeight) {
      this.viewHeight = viewHeight;
      updateLineWraps();
      updateViewportTopLine();
      viewPaneChanged(eventSource);
    }
  }

  public int getViewHeight()
  {
    return viewHeight;
  }

  /**
   * Sets the display offset, counting viewport lines (not input
   * lines) relative to the top line of the buffer.
   */
  public void setOffsetY(final Object eventSource, final int offsetY)
  {
    int lineIndex = topLineIndex;
    String line = lines[lineIndex];
    int lineOffsetX = 0;

    // TODO: Possibly can speed up this loop by caching previously
    // computed values of f(offsetY) = (lineIndex, lineOffsetX).
    // However, need to invalidate and / or update the cache whenever
    // the buffer contents or the viewport dimensions change.
    for (int y = 0; y < offsetY; y++) {
      lineOffsetX += viewWidth;
      if ((line == null) || (lineOffsetX >= line.length())) {
        lineIndex = incrementIndex(lineIndex);
        line = lines[lineIndex];
        lineOffsetX = 0;
      }
    }
    viewportTopLineIndex = lineIndex;
    viewportTopLineOffsetX = lineOffsetX;
    viewSelectionChanged(eventSource);
  }

  public int getOffsetY(final int lineIndex)
  {
    //updateLineWraps();
    int offsetY = 0;

    // TODO: Possibly can speed up this loop by caching previously
    // computed values of f(lineIndex) = offsetY.
    // However, need to invalidate and / or update the cache whenever
    // the buffer contents or the viewport dimensions change.
    for (int i = 0; i < lineIndex; i++) {
      final String line = lines[i];
      if (line != null) {
        offsetY += lineWraps[i] + 1;
      } else {
        offsetY++; // even null lines take space
      }
    }
    return offsetY;
  }

  private void updateLineWraps()
  {
    int totalWraps = 0;
    for (int i = 0; i < maxLines; i++) {
      final String line = lines[i];
      final int length = line != null ? line.length() : 0;
      final int wraps = Math.max(0, (length - 1) / viewWidth);
      lineWraps[i] = wraps;
      totalWraps += wraps;
    }
    this.totalWraps = totalWraps;
  }

  private void updateViewportTopLine()
  {
    viewportTopLineIndex = outputLineIndex;
    int height = viewHeight;
    if (height > 0) {
      height -= (lineWraps[viewportTopLineIndex] + 1);
      while ((height > 0) && (viewportTopLineIndex != topLineIndex)) {
        viewportTopLineIndex = decrementIndex(viewportTopLineIndex);
        height -= (lineWraps[viewportTopLineIndex] + 1);
      }
    }

    // if there are less displayable lines than the number of lines
    // the viewport has, then leave empty lines at the bottom of the
    // view
    viewportTopLineOffsetX = viewWidth * Math.max(0, -height);
  }

  public int getMaxLines()
  {
    return maxLines;
  }

  private int decrementIndex(final int index)
  {
    return index > 0 ? index - 1 : maxLines - 1;
  }

  private int incrementIndex(final int index)
  {
    return index < maxLines - 1 ? index + 1 : 0;
  }

  public void setOutputLine(final Object eventSource, final String line)
  {
    // System.out.println("setOutputLine(): " + line); // DEBUG
    if (line == null) {
      throw new NullPointerException("line");
    }
    final boolean totalLinesChanged;
    if (lines[outputLineIndex] == null) {
      totalLines++;
      totalLinesChanged = true;
    } else {
      totalLinesChanged = false;
    }
    lines[outputLineIndex] = line;
    final int prevWraps = lineWraps[outputLineIndex];
    final int newWraps = (line.length() - 1) / viewWidth;
    lineWraps[outputLineIndex] = newWraps;
    final int deltaWraps = newWraps - prevWraps;
    totalWraps += deltaWraps;
    updateViewportTopLine();
    if (totalLinesChanged || deltaWraps != 0) {
      // need to re-compute scroll properties
      viewPaneChanged(eventSource);
    }
    viewContentsChanged(eventSource);
  }

  public void newOutputLine(final Object eventSource)
  {
    outputLineIndex = incrementIndex(outputLineIndex);
    if (outputLineIndex == topLineIndex) {
      topLineIndex = incrementIndex(topLineIndex);
    }
    setOutputLine(eventSource, "");
  }

  public int getTopLineIndex()
  {
    return topLineIndex;
  }

  public void setTopLineIndex(final int topLineIndex)
  {
    if (topLineIndex < 0) {
      throw new IllegalArgumentException("topLineIndex < 0: " + topLineIndex);
    }
    if (topLineIndex > lines.length) {
      throw new IllegalArgumentException("topLineIndex > lines.length: " +
                                         topLineIndex + " > " + lines.length);
    }
    this.topLineIndex = topLineIndex;
  }

  public void hideCursor()
  {
    // System.out.println("hide cursor"); // DEBUG
    setSelectionStart(0, 0);
    setSelectionEnd(0, -1);
  }

  public void showCursor(final int column)
  {
    // System.out.println("show cursor"); // DEBUG
    // System.out.println("outputLineIndex=" + outputLineIndex); // DEBUG
    setSelectionStart(outputLineIndex, column);
    setSelectionEnd(outputLineIndex, column + 1);
  }

  private void setSelectionStart(final int lineIndex, final int offsetX)
  {
    // TODO: Range check?  Reset when line contents changes?
    selectionStartLineIndex = lineIndex;
    selectionStartLineOffsetX = offsetX;
  }

  private void setSelectionEnd(final int lineIndex, final int offsetX)
  {
    // TODO: Range check?  Reset when line contents changes?
    selectionEndLineIndex = lineIndex;
    selectionEndLineOffsetX = offsetX;
  }

  private void emptySelection(final Point selectionStart,
                              final Point selectionEnd)
  {
    selectionStart.x = 0;
    selectionStart.y = -1;
    selectionEnd.x = 0;
    selectionEnd.y = -1;
    return;
  }

  public void getSelection(final Point selectionStart,
                           final Point selectionEnd)
  {
    int lineIndex = viewportTopLineIndex;
    if ((selectionEndLineIndex < selectionStartLineIndex) ||
        (lineIndex > selectionEndLineIndex)) {
      // selection is beyond visible top line
      emptySelection(selectionStart, selectionEnd);
      return;
    }
    String line = lines[lineIndex];
    int lineLength = line != null ? line.length() : 0;
    int lineOffsetX = viewportTopLineOffsetX;
    boolean startPassed = false;
    for (int y = 0; y < viewHeight; y++) {
      final int nextLineOffsetX = lineOffsetX + viewWidth;
      if (!startPassed) {
        if (lineIndex == selectionStartLineIndex) {
          if (nextLineOffsetX > selectionStartLineOffsetX) {
            selectionStart.x =
              Math.max(selectionStartLineOffsetX - lineOffsetX, 0);
            selectionStart.y = y;
            startPassed = true;
          }
        }
      }
      if (lineIndex == selectionEndLineIndex) {
        if (nextLineOffsetX >= selectionEndLineOffsetX) {
          selectionEnd.x = Math.max(selectionEndLineOffsetX - lineOffsetX, 0);
          selectionEnd.y = y;
          return;
        }
      }
      lineOffsetX = nextLineOffsetX;
      if ((line == null) || (lineOffsetX >= line.length())) {
        lineIndex = incrementIndex(lineIndex);
        line = lines[lineIndex];
        lineLength = line != null ? line.length() : 0;
        lineOffsetX = 0;
      }
    }
    selectionEnd.x = Math.max(selectionEndLineOffsetX - lineOffsetX, 0);
    selectionEnd.y = Math.max(viewHeight - 1, 0);
  }

  public String getLine(final int index)
  {
    return lines[(topLineIndex + index) % maxLines];
  }

  private int min(final int x, final int y)
  {
    return x < y ? x : y;
  }

  private static final char[] EMPTY_CHAR_ARRAY = new char[0];

  private char[] getLineChars(final String line)
  {
    final char[] lineChars;
    if (line != null) {
      lineChars = new char[line.length()];
      line.getChars(0, lineChars.length, lineChars, 0);
    } else {
      lineChars = EMPTY_CHAR_ARRAY;
    }
    return lineChars;
  }

  public void renderViewPort(final char[] viewPort)
  {
    int lineIndex = viewportTopLineIndex;
    String line = lines[lineIndex];
    int lineOffsetX = viewportTopLineOffsetX;
    int destPos = 0;
    char[] lineChars = getLineChars(line);
    for (int y = 0; y < viewHeight; y++) {
      final int length = min(lineChars.length - lineOffsetX, viewWidth);
      final int nextDestPos = destPos + viewWidth;
      System.arraycopy(lineChars, lineOffsetX, viewPort, destPos, length);
      Arrays.fill(viewPort, destPos + length, nextDestPos, ' ');
      destPos = nextDestPos;
      lineOffsetX += viewWidth;
      if ((line == null) || (lineOffsetX >= line.length())) {
        lineIndex = incrementIndex(lineIndex);
        line = lines[lineIndex];
        lineChars = getLineChars(line);
        lineOffsetX = 0;
      }
    }
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
