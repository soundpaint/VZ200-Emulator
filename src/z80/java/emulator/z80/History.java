// TODO: move this class into new package 'emulator.monitor'.
package emulator.z80;

public class History
{
  public static final int DEFAULT_MAX_SIZE = 100;

  private final String history[];
  private final int maxSize;
  private int headIndex, size;
  private int preCursor;

  public History()
  {
    this(DEFAULT_MAX_SIZE);
  }

  public History(final int maxSize)
  {
    this.maxSize = maxSize;
    history = new String[maxSize];
    size = 0;
    headIndex = -1;
    resetPreCursor();
  }

  public int getSize()
  {
    return size;
  }

  public void resetPreCursor()
  {
    preCursor = 0;
  }

  public boolean incrementPreCursor()
  {
    if (preCursor < size - 1) {
      preCursor++;
      return true;
    }
    return false;
  }

  public boolean decrementPreCursor()
  {
    if (preCursor > 0) {
      preCursor--;
      return true;
    }
    return false;
  }

  private int getIndexForPreCursor()
  {
    int index = headIndex - preCursor;
    if (index < 0)
      index += maxSize;
    if (index >= maxSize)
      index -= maxSize;
    return index;
  }

  public String getPreCursorEntry()
  {
    final int index = getIndexForPreCursor();
    return history[index];
  }

  public void setEntry(final String entry)
  {
    if (size == 0) {
      throw new IllegalStateException("must add entry before setting it");
    }
    final int index = getIndexForPreCursor();
    history[index] = entry;
    //printHistory(); // DEBUG
  }

  public void newEntry()
  {
    if (headIndex >= 0) {
      history[headIndex] = getPreCursorEntry();
    }
    headIndex++;
    if (headIndex == maxSize) {
      headIndex = 0;
    }
    if (size < maxSize) {
      size++;
    }
    resetPreCursor();
    setEntry("");
  }

  // DEBUG
  public void printHistory()
  {
    for (int i = 0; i < history.length; i++) {
      System.out.println("history[" + i + "]=" + history[i]);
    }
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
