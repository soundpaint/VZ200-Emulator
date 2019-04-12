package emulator.z80;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import javax.swing.JPanel;

// TODO: Move management of color-related stuff into terminal model?
//
// TODO: Move model manipulation methods into terminal controller.
public class TerminalView extends JPanel
{
  private static final long serialVersionUID = -5805805101979871533L;

  private static final int DEFAULT_LINES_BUFFER_SIZE = 0x0800;
  private static final Color DEFAULT_BACKGROUND_COLOR = Color.BLACK;
  private static final Color DEFAULT_SELECTION_BACKGROUND_COLOR = Color.WHITE;
  private static final Color DEFAULT_TEXT_COLOR = Color.WHITE;
  private static final Color DEFAULT_INFO_COLOR = Color.GREEN;
  private static final Color DEFAULT_WARN_COLOR = Color.YELLOW;
  private static final Color DEFAULT_ERROR_COLOR = Color.RED;

  private final TerminalModel model;
  private final Font font;
  private final int paddingY;
  private final int charWidth;
  private final int charHeight;
  private char[] viewBuffer;
  private Color backgroundColor;
  private Color backgroundColorDisabled;
  private Color selectionBackgroundColor;
  private Color selectionBackgroundColorDisabled;
  private Color textColor;
  private Color textColorDisabled;
  private Color infoColor;
  private Color infoColorDisabled;
  private Color warnColor;
  private Color warnColorDisabled;
  private Color errorColor;
  private Color errorColorDisabled;

  private TerminalView()
  {
    throw new UnsupportedOperationException("unsupported constructor");
  }

  public TerminalView(final int bufferSize,
                      final int preferredColumns, final int preferredRows)
  {
    model =
      new TerminalModel(bufferSize, TerminalModel.DEFAULT_LINE_MAX_LENGTH);
    viewBuffer = new char[0];
    font = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    final FontRenderContext fontRenderContext =
      new FontRenderContext(new AffineTransform(), false, false);
    final Rectangle2D maxWidthBounds =
      font.getStringBounds(new char[] {'W'}, 0, 1, fontRenderContext);
    final Rectangle2D maxHeightBounds =
      font.getStringBounds(new char[] {'W', 'J', 'g', 'q'},
                           0, 1, fontRenderContext);
    charWidth = (int)maxWidthBounds.getWidth();
    charHeight = (int)maxHeightBounds.getHeight();
    paddingY = charHeight / 4;
    setBackgroundColor(DEFAULT_BACKGROUND_COLOR);
    setSelectionBackgroundColor(DEFAULT_SELECTION_BACKGROUND_COLOR);
    setTextColor(DEFAULT_TEXT_COLOR);
    setInfoColor(DEFAULT_INFO_COLOR);
    setWarnColor(DEFAULT_WARN_COLOR);
    setErrorColor(DEFAULT_ERROR_COLOR);
    setMinimumSize(new Dimension(charWidth, charHeight + paddingY));
    setPreferredSize(new Dimension(charWidth * preferredColumns,
                                   charHeight * preferredRows + paddingY));
  }

  public void
    addTerminalViewChangeListener(final TerminalViewChangeListener listener)
  {
    model.addChangeListener(listener);
  }

  public void hideCursor()
  {
    model.hideCursor();
  }

  public void showCursor(final int column)
  {
    model.showCursor(column);
  }

  public void setOutputLine(final Object eventSource, final String text)
  {
    model.setOutputLine(eventSource, text);
  }

  public void newOutputLine(final Object eventSource)
  {
    model.newOutputLine(eventSource);
  }

  private Color getDisabledColor(final Color color)
  {
    return color.darker();
  }

  public void setBackgroundColor(final Color backgroundColor)
  {
    this.backgroundColor = backgroundColor;
    backgroundColorDisabled = getDisabledColor(backgroundColor);
  }

  public void setSelectionBackgroundColor(final Color selectionBackgroundColor)
  {
    this.selectionBackgroundColor = selectionBackgroundColor;
    selectionBackgroundColorDisabled =
      getDisabledColor(selectionBackgroundColor);
  }

  public void setTextColor(final Color textColor)
  {
    this.textColor = textColor;
    textColorDisabled = getDisabledColor(textColor);
  }

  public void setInfoColor(final Color infoColor)
  {
    this.infoColor = infoColor;
    infoColorDisabled = getDisabledColor(infoColor);
  }

  public void setWarnColor(final Color warnColor)
  {
    this.warnColor = warnColor;
    warnColorDisabled = getDisabledColor(warnColor);
  }

  public void setErrorColor(final Color errorColor)
  {
    this.errorColor = errorColor;
    errorColorDisabled = getDisabledColor(errorColor);
  }

  /**
   * Upon startup and whenever the window is resized, the length of
   * the view buffer may need to be changed.
   */
  private void checkAndUpdateViewBufferSize(final int size)
  {
    if (viewBuffer.length != size) {
      viewBuffer = new char[size];
    }
  }

  public void setViewOffsetY(final Object source, final int offsetY)
  {
    model.setOffsetY(source, offsetY);
  }

  public int getOffsetY(final int lineIndex)
  {
    return model.getOffsetY(lineIndex);
  }

  private void fillLineSegment(final Graphics g, final int y,
                               final int startX, final int endX)
  {
    g.fillRect(startX * charWidth, y * charHeight,
               (endX - startX) * charWidth, charHeight + paddingY);
  }

  private void fillSelectionBackground(final Graphics g,
                                       final int selectionStartX,
                                       final int selectionStartY,
                                       final int selectionEndX,
                                       final int selectionEndY)
  {
    final int width = getWidth();
    final int height = getHeight();
    g.setColor(isEnabled() ?
               selectionBackgroundColor :
               selectionBackgroundColorDisabled);
    if (selectionStartY <= selectionEndY) {
      if (selectionStartY < selectionEndY) {
        fillLineSegment(g, selectionStartY,
                        selectionStartX, width);
        if (selectionStartY + 1 < selectionEndY) {
          g.fillRect(0, (selectionStartY + 1) * charHeight, width,
                     (selectionEndY - selectionStartY - 1) * charHeight +
                     paddingY);
        }
        fillLineSegment(g, selectionEndY,
                        0, selectionEndX);
      } else {
        fillLineSegment(g, selectionStartY,
                        selectionStartX, selectionEndX);
      }
    }
  }

  @Override
  public void paintComponent(final Graphics g)
  {
    super.paintComponent(g); // not strictly necessary as long as we
                             // draw an opaque background
    final int width = getWidth();
    final int height = getHeight();
    final int lineSize = Math.max(1, width / charWidth);
    final int columnSize = Math.max(1, height / charHeight);
    checkAndUpdateViewBufferSize(lineSize * columnSize);
    model.setViewWidth(null, lineSize);
    model.setViewHeight(null, columnSize);
    model.renderViewPort(viewBuffer);
    final Point selectionStart = new Point();
    final Point selectionEnd = new Point();
    model.getSelection(selectionStart, selectionEnd);
    g.setFont(font);
    g.setColor(isEnabled() ? backgroundColor : backgroundColorDisabled);
    g.fillRect(0, 0, width, height);
    fillSelectionBackground(g, selectionStart.x, selectionStart.y,
                            selectionEnd.x, selectionEnd.y);
    g.setColor(isEnabled() ? textColor : textColorDisabled);
    final int x0 = 0;
    final int y0 = 0;
    final int x1 = lineSize;
    final int y1 = columnSize;
    final int sx0 = 0;
    int sy0 = charHeight;
    for (int y = y0; y < y1; y++) {
      if (y == selectionStart.y) {
        try {
          g.drawChars(viewBuffer,
                      y * lineSize,
                      selectionStart.x,
                      0,
                      sy0);
        } catch (final RuntimeException e) {
          System.out.println("y*lineSize=" + y * lineSize); // DEBUG
          System.out.println("0=" + 0); // DEBUG
          System.out.println("selectionStart.x=" + selectionStart.x); // DEBUG
          System.out.println("sy0=" + sy0); // DEBUG
          throw e;
        }
        g.setColor(isEnabled() ? backgroundColor : backgroundColorDisabled);
        if (y == selectionEnd.y) {
          try {
            g.drawChars(viewBuffer,
                        y * lineSize + selectionStart.x,
                        selectionEnd.x - selectionStart.x,
                        selectionStart.x * charWidth,
                        sy0);
          } catch (final RuntimeException e) {
            System.out.println("y*lineSize+selectionStart.x=" +
                               (y * lineSize + selectionStart.x)); // DEBUG
            System.out.println("selectionEnd.x-selectionStart.x=" +
                               (selectionEnd.x - selectionStart.x)); // DEBUG
            System.out.println("selectionStart.x*charWidth=" +
                               (selectionStart.x * charWidth)); // DEBUG
            System.out.println("sy0=" + sy0); // DEBUG
            throw e;
          }
          g.setColor(isEnabled() ? textColor : textColorDisabled);
          g.drawChars(viewBuffer,
                      y * lineSize + selectionEnd.x,
                      x1 - selectionEnd.x,
                      selectionEnd.x * charWidth,
                      sy0);
        } else {
          g.drawChars(viewBuffer,
                      y * lineSize + selectionStart.x,
                      x1 - selectionStart.x,
                      selectionStart.x * charWidth,
                      sy0);
        }
      } else if (y == selectionEnd.y) {
        g.drawChars(viewBuffer,
                    y * lineSize,
                    selectionEnd.x,
                    0,
                    sy0);
        g.setColor(isEnabled() ? textColor : textColorDisabled);
        g.drawChars(viewBuffer,
                    y * lineSize + selectionEnd.x,
                    x1 - selectionEnd.x,
                    selectionEnd.x * charWidth,
                    sy0);
      } else {
        g.drawChars(viewBuffer, y * lineSize, x1, sx0, sy0);
      }
      sy0 += charHeight;
    }
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
