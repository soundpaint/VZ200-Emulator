package emulator.vz200;

import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;

import emulator.z80.CPU;

public class KeyboardMatrix {

  public static class Key {
    private String keyLabel, shiftLabel, topLabel, bottomLabel;
    private int xPos, yPos;
    private float width;
    private boolean haveGraphicChar;
    private int keyCode;
    private int row, column;

    public Key(String keyLabel,
	       String shiftLabel,
	       String topLabel,
	       String bottomLabel,
	       int xPos,
	       int yPos,
	       float width,
	       boolean haveGraphicChar,
	       int keyCode) {
      this.keyLabel = keyLabel;
      this.shiftLabel = shiftLabel;
      this.topLabel = topLabel;
      this.bottomLabel = bottomLabel;
      this.xPos = xPos;
      this.yPos = yPos;
      this.width = width;
      this.haveGraphicChar = haveGraphicChar;
      this.keyCode = keyCode;
    }

    public int getRow() {
      return row;
    }

    public int getColumn() {
      return column;
    }

    public String getKeyLabel() {
      return keyLabel;
    }

    public String getShiftLabel() {
      return shiftLabel;
    }

    public String getTopLabel() {
      return topLabel;
    }

    public String getBottomLabel() {
      return bottomLabel;
    }

    public int getXPos() {
      return xPos;
    }

    public int getYPos() {
      return yPos;
    }

    public float getWidth() {
      return width;
    }

    public boolean haveGraphicChar() {
      return haveGraphicChar;
    }

    public int getKeyCode() {
      return keyCode;
    }
  }

  private final static Key[][] KEYS = {
    {
      new Key("T", "▀", "THEN", "MID $ (",
	      19, 1, 1.0f, true, KeyEvent.VK_T),
      new Key("W", "▜", "TO", "VAL (",
	      7, 1, 1.0f, true, KeyEvent.VK_W),
      null,
      new Key("E", "▙", "NEXT", "LEN",
	      11, 1, 1.0f, true, KeyEvent.VK_E),
      new Key("Q", "▛", "FOR", "CHR $ (",
	      3, 1, 1.0f, true, KeyEvent.VK_Q),
      new Key("R", "▟", "LEFT $ (", "RETURN",
	      15, 1, 1.0f, true, KeyEvent.VK_R)
    }, {
      new Key("G", "▚", "GOTO", "STOP",
	      20, 2, 1.0f, true, KeyEvent.VK_G),
      new Key("S", "▖", "STEP", "STR$ (",
	      8, 2, 1.0f, true, KeyEvent.VK_S),
      new Key("CTRL", null, "", "",
	      0, 2, 1.0f, false, KeyEvent.VK_CONTROL),
      new Key("D", "▝", "DIM", "RESTORE",
	      12, 2, 1.0f, true, KeyEvent.VK_D),
      new Key("A", "▗", "MODE (", "ASC (",
	      4, 2, 1.0f, true, KeyEvent.VK_A),
      new Key("F", "▘", "GOSUB", "RND (",
	      16, 2, 1.0f, true, KeyEvent.VK_F)
    }, {
      new Key("B", "", "LLIST", "SOUND",
	      22, 3, 1.0f, false, KeyEvent.VK_B),
      new Key("X", "", "POKE", "OUT",
	      10, 3, 1.0f, false, KeyEvent.VK_X),
      new Key("SHIFT", null, "", "",
	      0, 3, 2.0f, false, KeyEvent.VK_SHIFT),
      new Key("C", "", "CONT", "COPY",
	      14, 3, 1.0f, false, KeyEvent.VK_C),
      new Key("Z", "", "PEEK (", "INP",
	      6, 3, 1.0f, false, KeyEvent.VK_Z),
      new Key("V", "", "LPRINT", "USR",
	      18, 3, 1.0f, false, KeyEvent.VK_V)
    }, {
      new Key("5", "%", "LIST", "LOG (",
	      17, 0, 1.0f, false, KeyEvent.VK_5),
      new Key("2", "\"", "C LOAD", "COS (",
	      5, 0, 1.0f, false, KeyEvent.VK_2),
      null,
      new Key("3", "#", "C RUN", "TAN (",
	      9, 0, 1.0f, false, KeyEvent.VK_3),
      new Key("1", "!", "C SAVE", "SIN (",
	      1, 0, 1.0f, false, KeyEvent.VK_1),
      new Key("4", "$", "VERIFY", "ATN (",
              13, 0, 1.0f, false, KeyEvent.VK_4)
    }, {
      new Key("N", "^", "COLOR", "USING",
	      26, 3, 1.0f, false, KeyEvent.VK_N),
      new Key(".", ">", "_⬆", "",
	      38, 3, 1.0f, false, KeyEvent.VK_PERIOD),
      null,
      new Key(",", "<", "_➡", "",
	      34, 3, 1.0f, false, KeyEvent.VK_COMMA),
      new Key("SPACE", null, "_⬇", "",
	      42, 3, 2.0f, false, KeyEvent.VK_SPACE),
      new Key("M", "\\", "_⬅", "",
	      30, 3, 1.0f, false, KeyEvent.VK_M)
    }, {
      new Key("6", "&", "RUN", "EXP (",
	      21, 0, 1.0f, false, KeyEvent.VK_6),
      new Key("9", ")", "READ", "ABS (",
	      33, 0, 1.0f, false, KeyEvent.VK_9),
      new Key("-", "=", "_ BREAK ", "",
	      41, 0, 1.0f, false, KeyEvent.VK_MINUS),
      new Key("8", "(", "NEW", "SQR (",
	      29, 0, 1.0f, false, KeyEvent.VK_8),
      new Key("0", "@", "DATA", "INT (",
	      37, 0, 1.0f, false, KeyEvent.VK_0),
      new Key("7", "'", "END", "SGN (",
	      25, 0, 1.0f, false, KeyEvent.VK_7)
    }, {
      new Key("Y", "▄", "ELSE", "RIGHT$ (",
	      23, 1, 1.0f, true, KeyEvent.VK_Y),
      new Key("O", "[", "LET", "OR",
	      35, 1, 1.0f, false, KeyEvent.VK_O),
      new Key("RETURN", null, "_ FUNCTION ", "",
	      43, 1, 1.5f, false, KeyEvent.VK_ENTER),
      new Key("I", "▐", "INPUT", "AND",
	      31, 1, 1.0f, true, KeyEvent.VK_I),
      new Key("P", "]", "PRINT", "NOT",
	      39, 1, 1.0f, false, KeyEvent.VK_P),
      new Key("U", "▌", "IF", "INKEY $",
	      27, 1, 1.0f, true, KeyEvent.VK_U)
    }, {
      new Key("H", "▞", "CLS", "SET",
	      24, 2, 1.0f, true, KeyEvent.VK_H),
      new Key("L", "?", "_ INSERT ", "",
	      36, 2, 1.0f, false, KeyEvent.VK_L),
      new Key(":", "*", "_ INVERSE ", "",
	      44, 2, 1.0f, false, KeyEvent.VK_COLON),
      new Key("K", "/", "TAB (", "POINT",
	      32, 2, 1.0f, false, KeyEvent.VK_K),
      new Key(";", "+", "_ RUBOUT ", "",
	      40, 2, 1.0f, false, KeyEvent.VK_SEMICOLON),
      new Key("J", "█", "REM", "RESET",
	      28, 2, 1.0f, true, KeyEvent.VK_J)
    }
  };

  private static final int ROW_COUNT = KEYS.length;
  private static final int COLUMN_COUNT = KEYS[0].length;

  public static int getRowCount() { return ROW_COUNT; }

  public static int getColumnCount() { return COLUMN_COUNT; }

  private static class KeyIterator implements Iterator<Key> {
    private int row, column;

    private KeyIterator() {
      row = 0;
      column = 0;
    }
    public boolean hasNext() {
      return column >= 0;
    }

    private void forward() {
      assert column >= 0 : "illegal state of indices";
      column++;
      if (column >= COLUMN_COUNT) {
	column = 0;
	row++;
	if (row >= ROW_COUNT) {
	  column = -1;
	}
      }
    }

    private Key current() {
      return KEYS[row][column];
    }

    public Key next() {
      if (!hasNext())
	throw new NoSuchElementException();
      Key key = current();
      forward();
      while (hasNext() && (current() == null))
	forward();
      return key;
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  public static Iterator<Key> getKeyIterator() {
    return new KeyIterator();
  }

  private final static HashMap<Integer, Key> keyCode2Key;

  static {
    keyCode2Key = new HashMap<Integer, Key>();
    for (int row = 0; row < ROW_COUNT; row++) {
      for (int column = 0; column < COLUMN_COUNT; column++) {
	Key key = KEYS[row][column];
	if (key != null) {
	  key.row = row;
	  key.column = column;
	  keyCode2Key.put(key.keyCode, key);
	}
      }
    }
  }

  public static Key getKeyByKeyCode(int keyCode) {
    return keyCode2Key.get(keyCode);
  }

  private int rows[];

  public void setSelected(Key key, boolean selected) {
    if (selected) {
      rows[key.row] &= ~(1 << key.column);
    } else {
      rows[key.row] |= 1 << key.column;
    }
  }

  public int read(int address) {
    int data = ~0x0;
    for (int row = 0; row < ROW_COUNT; row++) {
      if (((address >> row) & 0x1) == 0x0)
	data &= rows[row];
    }
    return data;
  }

  public KeyboardMatrix() {
    rows = new int[ROW_COUNT];
    Arrays.fill(rows, 0xff);
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
