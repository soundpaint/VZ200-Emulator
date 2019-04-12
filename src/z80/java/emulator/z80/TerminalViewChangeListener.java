package emulator.z80;

import java.util.Arrays;

public interface TerminalViewChangeListener
{
  /**
   * Invoked whenever the view port's width or height or the number of
   * display lines has changed, such that the scrollbar properties may
   * need to be recomputed.
   *
   * Calling this method may result in adjusting the scrollbar
   * properties, which in turn may result in a subsequent call to the
   * #viewSelectionChanged() method.
   */
  void viewPaneChanged(final Object source,
                       final int totalLines,
                       final int totalWraps,
                       final int viewportTopLineIndex,
                       final int viewportTopLineWrapOffset,
                       final int viewWidth,
                       final int viewHeight);

  /**
   * Invoked whenever the contents of the terminal buffer has changed,
   * such that a repaint is necessary.
   */
  void viewContentsChanged(final Object source);

  /**
   * Invoked whenever the viewable section of the terminal's buffer
   * has changed, e.g. by manual scrolling or when a new line is
   * appended at the bottom of the view, such that the view
   * automatically scrolls.
   */
  void viewSelectionChanged(final Object source);
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
