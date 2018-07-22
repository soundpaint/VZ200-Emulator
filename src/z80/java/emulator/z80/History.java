// TODO: move this class into new package 'emulator.monitor'.
package emulator.z80;

public class History {
  public final static int DEFAULT_SIZE = 100;

  private String history[];
  private int size, oldest, newest;

  public History() {
    this(DEFAULT_SIZE);
  }

  public void addEntry(String entry) {
    history[newest++] = entry;
    if (newest == history.length)
      newest = 0;
  }

  public String getPrecursor(int distance) {
    if (distance < 0)
      throw new IllegalArgumentException("distance < 0");
    int index = newest - (distance % size);
    if (index < 0)
      index += size;
    if (index >= size)
      index -= size;
    return history[index];
  }

  public History(int size) {
    this.size = size;
    history = new String[size];
    oldest = 0;
    newest = 0;
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
