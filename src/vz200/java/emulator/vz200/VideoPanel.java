package emulator.vz200;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.io.DataInputStream;
import java.io.IOException;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import emulator.z80.CPU;
import emulator.z80.RAMMemory;
import emulator.z80.ROMMemory;

public class VideoPanel extends JPanel {

  private final static Color GREEN_FRAME_COLOR =
    Color.green.darker().darker().darker();

  private final static Color RED_FRAME_COLOR =
    Color.red.darker().darker();

  private final static Color GREEN_FG_COLOR =
    Color.green.darker().darker();

  private final static Color RED_FG_COLOR =
    Color.red.darker();

  private final static Color[] GREEN_TEXT_COLOR_TABLE = {
    GREEN_FG_COLOR, GREEN_FG_COLOR, GREEN_FG_COLOR, GREEN_FG_COLOR,
    GREEN_FG_COLOR, GREEN_FG_COLOR, GREEN_FG_COLOR, GREEN_FG_COLOR,
    Color.green, Color.yellow, Color.blue, Color.red,
    Color.orange.darker(), Color.cyan, Color.magenta, Color.orange
  };

  private final static Color[] RED_TEXT_COLOR_TABLE = {
    RED_FG_COLOR, RED_FG_COLOR, RED_FG_COLOR, RED_FG_COLOR,
    RED_FG_COLOR, RED_FG_COLOR, RED_FG_COLOR, RED_FG_COLOR,
    Color.green, Color.yellow, Color.blue, Color.red,
    Color.orange.darker(), Color.cyan, Color.magenta, Color.orange
  };

  private final static Color[] GREEN_GRAPHICS_COLOR_TABLE = {
    Color.green, Color.yellow, Color.blue, Color.red
  };

  private final static Color[] RED_GRAPHICS_COLOR_TABLE = {
    Color.orange.darker(), Color.cyan, Color.magenta, Color.orange
  };

  private RAMMemory videoRAM;
  private int[] directVideoRAM;
  private int[] charset; // use int rather than signed byte to save casts
  private Color frameColor;
  private Color[] textColorTable, graphicsColorTable;
  private Dimension preferredSize;
  private int zoom;
  private boolean displayMode;

  // active window
  private int axleft;
  private int axright;
  private int aybottom;
  private int aytop;

  // full screen window
  private int frameWidth;
  private int sxleft;
  private int sxright;
  private int sytop;
  private int sybottom;

  // invalidation
  private Invalidator invalidator;

  private final static String CHARSET_RESOURCENAME = "charset.rom";
  private final static int CHARSET_LENGTH = 3072;

  public VideoPanel(int baseAddress) throws IOException {
    charset =
      new ROMMemory(this.getClass(), CHARSET_RESOURCENAME,
		    0x0000, CHARSET_LENGTH).getByteArray();
    videoRAM = new RAMMemory(baseAddress, 0x0800);
    directVideoRAM = videoRAM.getByteArray();

    // debug: init video ram with pattern
    //for (int i = 0x0000; i < 0x0800; i++)
    //  directVideoRAM[i] = i & 0xff;

    invalidator = new Invalidator();
    setZoom(1);
    setColorMode(COLOR_MODE_GREEN);
    setDisplayMode(DISPLAY_MODE_TEXT);
  }

  public CPU.Memory getVideoRAM() { return videoRAM; }

  public void setZoom(int zoom) {
    this.zoom = zoom;

    // complete screen
    sxleft = 0;
    sxright = 320 * zoom - 1;
    sytop = 0;
    sybottom = 256 * zoom - 1;

    // active window
    frameWidth = 32 * zoom;
    axleft = sxleft + 32 * zoom;
    axright = sxright - 32 * zoom;
    aytop = sytop + 32 * zoom;
    aybottom = sybottom - 32 * zoom;

    preferredSize = new Dimension(320 * zoom, 256 * zoom);
  }

  public static boolean COLOR_MODE_GREEN = false;
  public static boolean COLOR_MODE_RED = true;

  public void setColorMode(boolean colorMode) {
    if (colorMode == COLOR_MODE_GREEN) {
      frameColor = GREEN_FRAME_COLOR;
      textColorTable = GREEN_TEXT_COLOR_TABLE;
      graphicsColorTable = GREEN_GRAPHICS_COLOR_TABLE;
    } else { // (colorMode == COLOR_MODE_RED)
      frameColor = RED_FRAME_COLOR;
      textColorTable = RED_TEXT_COLOR_TABLE;
      graphicsColorTable = RED_GRAPHICS_COLOR_TABLE;
    }
    invalidateAll();
  }

  public static boolean DISPLAY_MODE_TEXT = false;
  public static boolean DISPLAY_MODE_GRAPHICS = true;

  public void setDisplayMode(boolean displayMode) {
    if (this.displayMode != displayMode) {
      this.displayMode = displayMode;
      invalidateAll();
    }
  }

  public boolean getDisplayMode() {
    return displayMode;
  }

  public void paintComponent(Graphics g) {
    // current area to redraw
    int cxleft = sxleft;
    int cxright = sxright;
    int cytop = sytop;
    int cybottom = sybottom;

    g.setColor(frameColor);
    if (cytop < aytop) {
      g.fillRect(cxleft, cytop, cxright - cxleft + 1, aytop - cytop);
      cytop = aytop;
    }
    if (cxleft < axleft) {
      g.fillRect(cxleft, cytop, axleft - cxleft, cybottom - cytop + 1);
      cxleft = axleft;
    }
    if (cybottom > aybottom) {
      g.fillRect(cxleft, aybottom + 1,
		 cxright - cxleft + 1, cybottom - aybottom);
      cybottom = aybottom;
    }
    if (cxright > axright) {
      g.fillRect(axright + 1, cytop,
		 cxright - axright, cybottom - cytop + 1);
      cxright = axright;
    }

    if (displayMode == DISPLAY_MODE_TEXT)
      paintTextMode(g, cxleft, cxright, cytop, cybottom);
    else // (displayMode == DISPLAY_MODE_GRAPHICS)
      paintGraphicsMode(g, cxleft, cxright, cytop, cybottom);
  }

  public void paintTextMode(Graphics g,
			    int cxleft, int cxright,
			    int cytop, int cybottom) {
    int x0 = (cxleft - frameWidth) / zoom / 8;
    int x1 = (cxright - frameWidth) / zoom / 8 + 1;
    int y0 = (cytop - frameWidth) / zoom / 12;
    int y1 = (cybottom - frameWidth) / zoom / 12 + 1;
    int zoom8 = 8 * zoom;
    int zoom12 = 12 * zoom;
    int sy0 = y0 * zoom12 + frameWidth;
    for (int y = y0; y < y1; y++) {
      int sx0 = x0 * zoom8 + frameWidth;
      for (int x = x0; x < x1; x++) {
	int charCode = directVideoRAM[(y << 5) + x];
	Color fgColor = textColorTable[charCode >> 4];
	int sy = sy0;
	int charsetIndex = charCode * 12;
	for (int yline = 0; yline < 12; yline++) {
	  int charline = charset[charsetIndex++];
	  int sx = sx0;
	  int mask = 1;
	  for (int xline = 0; xline < 8; xline++) {
	    if ((charline & mask) == 0)
	      g.setColor(Color.black);
	    else
	      g.setColor(fgColor);
	    g.fillRect(sx, sy, zoom, zoom);
	    mask <<= 1;
	    sx += zoom;
	  }
	  sy += zoom;
	}
	sx0 += zoom8;
      }
      sy0 += zoom12;
    }
  }

  public void paintGraphicsMode(Graphics g,
				int cxleft, int cxright,
				int cytop, int cybottom) {
    int x0 = (cxleft - frameWidth) / zoom / 8;
    int x1 = (cxright - frameWidth) / zoom / 8 + 1;
    int y0 = (cytop - frameWidth) / zoom / 3;
    int y1 = (cybottom - frameWidth) / zoom / 3 + 1;

    int zoom2 = 2 * zoom;
    int zoom3 = 3 * zoom;
    int zoom8 = 8 * zoom;

    int sy0 = y0 * zoom3 + frameWidth;
    for (int y = y0; y < y1; y++) {
      int sx0 = x0 * zoom8 + frameWidth;
      for (int x = x0; x < x1; x++) {
	int charCode = directVideoRAM[(y << 5) + x];
	int sx = sx0;
	for (int xline = 0; xline < 4; xline++) {
	  g.setColor(graphicsColorTable[charCode & 0x3]);
	  g.fillRect(sx, sy0, zoom2, zoom3);
	  charCode >>= 2;
	  sx += zoom2;
	}
	sx0 += zoom8;
      }
      sy0 += zoom3;
    }
  }

  public Dimension getPreferredSize() { return preferredSize; }

  public void repaintTextMode(boolean[] dirty) {
    int dx = zoom * 8;
    int dy = zoom * 12;
    int index = 0;
    int y = frameWidth;
    for (int cy = 0; cy < 16; cy++) {
      int x = frameWidth;
      for (int cx = 0; cx < 32; cx++) {
	if (dirty[index++])
	  repaint(0, x, y, dx, dy);
	x += dx;
      }
      y += dy;
    }
  }

  public void repaintGraphicsMode(boolean[] dirty) {
    int dx = zoom * 2;
    int dy = zoom * 3;
    int index = 0;
    int y = frameWidth;
    for (int cy = 0; cy < 64; cy++) {
      int x = frameWidth;
      for (int cx = 0; cx < 128; cx++) {
	if (dirty[index])
	  repaint(0, x, y, dx, dy);
	x += dx;
        if ((cx & 0x3) == 0x3) index++;
      }
      y += dy;
    }
  }

  public void repaint(boolean[] dirty) {
    if (displayMode == DISPLAY_MODE_TEXT)
      repaintTextMode(dirty);
    else
      repaintGraphicsMode(dirty);
  }

  public void invalidate(int address) {
    invalidator.invalidate(address);
  }

  public void invalidateAll() {
    invalidator.invalidateAll();
  }

  private class Invalidator implements Runnable {
    private boolean scheduled;
    private boolean[] dirtyCollect, dirtyHandle;
    private boolean allDirtyCollect, allDirtyHandle;

    public Invalidator() {
      scheduled = false;
      dirtyCollect = new boolean[2048];
      dirtyHandle = new boolean[2048];
    }

    public synchronized void invalidate(int address) {
      dirtyCollect[address] = true;
      if (!scheduled) {
	SwingUtilities.invokeLater(this);
	scheduled = true;
      }
    }

    /**
     * Call this when switching graphics or color mode.
     */
    public synchronized void invalidateAll() {
      allDirtyCollect = true;
      if (!scheduled) {
	SwingUtilities.invokeLater(this);
	scheduled = true;
      }
    }

    private synchronized void updateDirtyData() {
      boolean[] dirty = dirtyCollect;
      dirtyCollect = dirtyHandle;
      dirtyHandle = dirty;
      boolean allDirty = allDirtyCollect;
      allDirtyCollect = allDirtyHandle;
      allDirtyHandle = allDirty;
      scheduled = false;
    }

    public void run() {
      updateDirtyData();
      if (allDirtyHandle)
	repaint();
      else
	repaint(dirtyHandle);
      for (int i = 0; i < 2048; i++)
	dirtyHandle[i] = false;
      allDirtyHandle = false;
    }
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
