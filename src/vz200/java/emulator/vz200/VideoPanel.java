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

public class VideoPanel extends JPanel
{
  private static final long serialVersionUID = 1223323375329148324L;

  private static final int MEMORY_SIZE = 0x0800;
  private static final String CHARSET_RESOURCENAME = "charset.rom";
  private static final int CHARSET_LENGTH = 3072;
  public static final boolean COLOR_MODE_GREEN = false;
  public static final boolean COLOR_MODE_RED = true;
  public static final boolean DISPLAY_MODE_TEXT = false;
  public static final boolean DISPLAY_MODE_GRAPHICS = true;


  private static final Color GREEN_FRAME_COLOR =
    Color.green.darker().darker().darker();

  private static final Color RED_FRAME_COLOR =
    Color.red.darker().darker();

  private static final Color GREEN_FG_COLOR =
    Color.green.darker().darker();

  private static final Color RED_FG_COLOR =
    Color.red.darker();

  private static final Color[] GREEN_TEXT_COLOR_TABLE = {
    GREEN_FG_COLOR, GREEN_FG_COLOR, GREEN_FG_COLOR, GREEN_FG_COLOR,
    GREEN_FG_COLOR, GREEN_FG_COLOR, GREEN_FG_COLOR, GREEN_FG_COLOR,
    Color.green, Color.yellow, Color.blue, Color.red,
    Color.orange.darker(), Color.cyan, Color.magenta, Color.orange
  };

  private static final Color[] RED_TEXT_COLOR_TABLE = {
    RED_FG_COLOR, RED_FG_COLOR, RED_FG_COLOR, RED_FG_COLOR,
    RED_FG_COLOR, RED_FG_COLOR, RED_FG_COLOR, RED_FG_COLOR,
    Color.green, Color.yellow, Color.blue, Color.red,
    Color.orange.darker(), Color.cyan, Color.magenta, Color.orange
  };

  private static final Color[] GREEN_GRAPHICS_COLOR_TABLE = {
    Color.green, Color.yellow, Color.blue, Color.red
  };

  private final static Color[] RED_GRAPHICS_COLOR_TABLE = {
    Color.orange.darker(), Color.cyan, Color.magenta, Color.orange
  };

  private final int baseAddress;
  private final RAMMemory videoRAM;
  private final int[] directVideoRAM;
  private final int[] charset; // use int rather than signed byte to save casts
  private int zoomFactor;
  private Dimension preferredSize;
  private Color frameColor;
  private Color[] textColorTable, graphicsColorTable;
  private boolean displayMode;
  private boolean colorMode;

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
  private final Invalidator invalidator;

  private VideoPanel()
  {
    throw new UnsupportedOperationException("unsupported constructor");
  }

  public VideoPanel(final int baseAddress) throws IOException
  {
    this.baseAddress = baseAddress;
    charset =
      new ROMMemory((Class<? extends Object>)VideoPanel.class,
                    CHARSET_RESOURCENAME,
		    0x0000, CHARSET_LENGTH).getByteArray();
    videoRAM = new RAMMemory(baseAddress, 0x0800);
    directVideoRAM = videoRAM.getByteArray();
    invalidator = new Invalidator();
    setZoomFactor(UserPreferences.getInstance().getVideoZoomFactor());
    colorMode = COLOR_MODE_RED; // force initial update
    setColorMode(COLOR_MODE_GREEN);
    setDisplayMode(DISPLAY_MODE_TEXT);
  }

  public RAMMemory getVideoRAM()
  {
    return videoRAM;
  }

  public void setZoomFactor(final int zoomFactor)
  {
    if (zoomFactor < 1) {
      throw new IllegalArgumentException("zoomFactor < 1");
    }
    if (zoomFactor > 3) {
      throw new IllegalArgumentException("zoomFactor > 3");
    }
    this.zoomFactor = zoomFactor;

    // complete screen
    sxleft = 0;
    sxright = 320 * zoomFactor - 1;
    sytop = 0;
    sybottom = 256 * zoomFactor - 1;

    // active window
    frameWidth = 32 * zoomFactor;
    axleft = sxleft + 32 * zoomFactor;
    axright = sxright - 32 * zoomFactor;
    aytop = sytop + 32 * zoomFactor;
    aybottom = sybottom - 32 * zoomFactor;

    preferredSize = new Dimension(320 * zoomFactor, 256 * zoomFactor);
    invalidateAll();
  }

  public Dimension getPreferredSize()
  {
    return preferredSize;
  }

  public void setColorMode(final boolean colorMode)
  {
    if (this.colorMode != colorMode) {
      this.colorMode = colorMode;
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
  }

  public void setDisplayMode(final boolean displayMode)
  {
    if (this.displayMode != displayMode) {
      this.displayMode = displayMode;
      invalidateAll();
    }
  }

  public boolean getDisplayMode()
  {
    return displayMode;
  }

  public void paintComponent(final Graphics g)
  {
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

  public void paintTextMode(final Graphics g,
			    final int cxleft, final int cxright,
			    final int cytop, final int cybottom)
  {
    final int x0 = (cxleft - frameWidth) / zoomFactor / 8;
    final int x1 = (cxright - frameWidth) / zoomFactor / 8 + 1;
    final int y0 = (cytop - frameWidth) / zoomFactor / 12;
    final int y1 = (cybottom - frameWidth) / zoomFactor / 12 + 1;
    final int zoomFactor8 = 8 * zoomFactor;
    final int zoomFactor12 = 12 * zoomFactor;
    int sy0 = y0 * zoomFactor12 + frameWidth;
    for (int y = y0; y < y1; y++) {
      int sx0 = x0 * zoomFactor8 + frameWidth;
      for (int x = x0; x < x1; x++) {
	final int charCode = directVideoRAM[(y << 5) + x];
	final Color fgColor = textColorTable[charCode >> 4];
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
	    g.fillRect(sx, sy, zoomFactor, zoomFactor);
	    mask <<= 1;
	    sx += zoomFactor;
	  }
	  sy += zoomFactor;
	}
	sx0 += zoomFactor8;
      }
      sy0 += zoomFactor12;
    }
  }

  public void paintGraphicsMode(final Graphics g,
				final int cxleft, final int cxright,
				final int cytop, final int cybottom)
  {
    final int x0 = (cxleft - frameWidth) / zoomFactor / 8;
    final int x1 = (cxright - frameWidth) / zoomFactor / 8 + 1;
    final int y0 = (cytop - frameWidth) / zoomFactor / 3;
    final int y1 = (cybottom - frameWidth) / zoomFactor / 3 + 1;

    final int zoomFactor2 = 2 * zoomFactor;
    final int zoomFactor3 = 3 * zoomFactor;
    final int zoomFactor8 = 8 * zoomFactor;
    final int _sx = x0 * zoomFactor8 + 6 * zoomFactor + frameWidth;

    int sy0 = y0 * zoomFactor3 + frameWidth;
    for (int y = y0; y < y1; y++) {
      int sx0 = _sx;
      for (int x = x0; x < x1; x++) {
	int charCode = directVideoRAM[(y << 5) + x];
	int sx = sx0;
	for (int xline = 0; xline < 4; xline++) {
	  g.setColor(graphicsColorTable[charCode & 0x3]);
	  g.fillRect(sx, sy0, zoomFactor2, zoomFactor3);
	  charCode >>= 2;
	  sx -= zoomFactor2;
	}
	sx0 += zoomFactor8;
      }
      sy0 += zoomFactor3;
    }
  }

  public void repaintTextMode(final boolean[] dirty)
  {
    final int dx = zoomFactor * 8;
    final int dy = zoomFactor * 12;
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

  public void repaintGraphicsMode(final boolean[] dirty)
  {
    final int dx = zoomFactor * 2;
    final int dy = zoomFactor * 3;
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

  public void repaint(final boolean[] dirty)
  {
    if (displayMode == DISPLAY_MODE_TEXT)
      repaintTextMode(dirty);
    else
      repaintGraphicsMode(dirty);
  }

  public void invalidate(final int address)
  {
    invalidator.invalidate(address);
  }

  public void invalidateAll()
  {
    invalidator.invalidateAll();
  }

  private class Invalidator implements Runnable
  {
    private boolean[] dirtyCollect, dirtyHandle;
    private boolean allDirtyCollect, allDirtyHandle;
    private boolean scheduled;

    public Invalidator()
    {
      dirtyCollect = new boolean[MEMORY_SIZE];
      dirtyHandle = new boolean[MEMORY_SIZE];
      scheduled = false;
    }

    public synchronized void invalidate(final int address)
    {
      final int addressOffset = (address - baseAddress) & 0xffff;
      if (addressOffset < MEMORY_SIZE) {
        dirtyCollect[addressOffset] = true;
        if (!scheduled) {
          SwingUtilities.invokeLater(this);
          scheduled = true;
        }
      }
    }

    /**
     * Call this when switching graphics or color mode.
     */
    public synchronized void invalidateAll()
    {
      allDirtyCollect = true;
      if (!scheduled) {
	SwingUtilities.invokeLater(this);
	scheduled = true;
      }
    }

    private synchronized void updateDirtyData()
    {
      final boolean[] dirty = dirtyCollect;
      dirtyCollect = dirtyHandle;
      dirtyHandle = dirty;
      final boolean allDirty = allDirtyCollect;
      allDirtyCollect = allDirtyHandle;
      allDirtyHandle = allDirty;
      scheduled = false;
    }

    public void run()
    {
      updateDirtyData();
      if (allDirtyHandle)
	repaint();
      else
	repaint(dirtyHandle);
      for (int i = 0; i < MEMORY_SIZE; i++)
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
