package emulator.vz200;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Iterator;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import emulator.z80.RAMMemory;

public class KeyboardPanel extends JPanel {
  private static final long serialVersionUID = 7317835440086946160L;

  private Button buttons[][];
  private KeyboardMatrix matrix;
  private KeyListener keyListener;

  private static class Button extends JButton {
    private KeyboardMatrix.Key key;

    public Button(ImageIcon icon, KeyboardMatrix.Key key) {
      super(icon);
      this.key = key;
    }

    public KeyboardMatrix.Key getKey() {
      return key;
    }
  }

  private class KeyListener extends KeyAdapter {
    public void keyPressed(KeyEvent e) {
      KeyboardMatrix.Key key = KeyboardMatrix.getKeyByKeyCode(e.getKeyCode());
      if (key != null) {
	matrix.setSelected(key, true);
	JButton button = buttons[key.getRow()][key.getColumn()];
	button.setSelected(true);
      }
    }

    public void keyReleased(KeyEvent e) {
      KeyboardMatrix.Key key = KeyboardMatrix.getKeyByKeyCode(e.getKeyCode());
      if (key != null) {
	matrix.setSelected(key, false);
	JButton button = buttons[key.getRow()][key.getColumn()];
	button.setSelected(false);
      }
    }
  }

  private class MouseListener extends MouseAdapter {
    public void mousePressed(MouseEvent e) {
      if (e.getButton() == MouseEvent.BUTTON1) {
        Component component = e.getComponent();
        if (component instanceof Button) {
          Button button = (Button)component;
          KeyboardMatrix.Key key = button.getKey();
          matrix.setSelected(key, true);
          button.setSelected(true);
        }
      }
    }

    public void mouseReleased(MouseEvent e) {
      if (e.getButton() == MouseEvent.BUTTON1) {
        Component component = e.getComponent();
        if (component instanceof Button) {
          Button button = (Button)component;
          KeyboardMatrix.Key key = button.getKey();
          matrix.setSelected(key, false);
          button.setSelected(false);
        }
      }
    }
  }

  private final static Color PANEL_BG_COLOR =
    Color.red.darker().darker().darker().darker().darker();

  private final static Color KEY_BG_COLOR =
    Color.orange.darker().darker();

  private final static Color LABEL_FG_COLOR =
    Color.white;

  private final static Color BLOCK_FG_COLOR =
    Color.black;

  private final static Color[] TEXT_COLORS = {
    Color.green, Color.yellow, Color.blue, Color.red,
    Color.orange.darker(), Color.cyan, Color.magenta, Color.orange
  };

  private final static String[] TEXT_COLOR_NAMES = {
    "GREEN", "YELLOW", "BLUE", "RED", "BUFF", "CYAN", "MAGENTA", "ORANGE"
  };

  private Dimension preferredSize;
  private int zoom;

  public KeyAdapter getKeyListener() {
    return keyListener;
  }

  private Image createKeyImage(KeyboardMatrix.Key key,
			       Dimension size, boolean selected) {
    Color keyFgColor = selected ? KEY_BG_COLOR : LABEL_FG_COLOR;
    Color keyBgColor = selected ? LABEL_FG_COLOR : KEY_BG_COLOR;
    BufferedImage image =
      new BufferedImage((int)size.getWidth(),
			(int)size.getHeight(),
			BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    graphics.setPaint(keyFgColor);
    graphics.setBackground(keyBgColor);
    graphics.clearRect(0, 0, image.getWidth(), image.getHeight());
    if (key.getShiftLabel() != null) {
      // align text to lower left corner
      Font normalFont = graphics.getFont();
      float bigFontSize = normalFont.getSize2D() * 2.0f;
      Font bigFont = normalFont.deriveFont(bigFontSize);
      graphics.setFont(bigFont);
      graphics.drawString(key.getKeyLabel(), 4.0f, 42.0f);
      graphics.setFont(normalFont);
      if (key.haveGraphicChar()) {
	graphics.setPaint(BLOCK_FG_COLOR);
	graphics.drawString("█", 34.0f, 14.0f);
	graphics.drawString("█", 36.0f, 14.0f);
	graphics.drawString("█", 34.0f, 16.0f);
	graphics.drawString("█", 36.0f, 16.0f);
	graphics.setPaint(keyBgColor);
	graphics.drawString("█", 35.0f, 15.0f);
	graphics.setPaint(BLOCK_FG_COLOR);
      }
      graphics.drawString(key.getShiftLabel(), 35.0f, 15.0f);
    } else {
      // align text centered
      graphics.drawString(key.getKeyLabel(), 15.0f, 35.0f);
    }
    return image;
  }

  private void addColorLabels() {
    for (int i = 0; i < 8; i++) {
      JLabel colorLabel;
      colorLabel = new JLabel(TEXT_COLOR_NAMES[i]);
      colorLabel.setForeground(LABEL_FG_COLOR);
      colorLabel.setBackground(TEXT_COLORS[i]);
      colorLabel.setOpaque(true);
      Font defaultFont = colorLabel.getFont();
      float smallFontSize = defaultFont.getSize2D() * 0.7f;
      Font smallFont = defaultFont.deriveFont(smallFontSize);
      colorLabel.setFont(smallFont);
      JPanel colorPanel = new JPanel();
      colorPanel.setBackground(PANEL_BG_COLOR);
      colorPanel.setAlignmentX(0.10f + (1.0f + i * 4.0f) / 47.0f);
      colorPanel.setAlignmentY(0.05f);
      colorPanel.add(colorLabel);
      colorPanel.setPreferredSize(new Dimension(50, 50));
      add(colorPanel);
    }
  }

  private void addKey(KeyboardMatrix.Key key, MouseListener mouseListener) {
    if (key != null) {
      Dimension size = new Dimension(Math.round(50 * key.getWidth()), 50);
      Image defaultImage = createKeyImage(key, size, false);
      Image selectedImage = createKeyImage(key, size, true);
      Button button = new Button(new ImageIcon(defaultImage), key);
      button.setSelectedIcon(new ImageIcon(selectedImage));
      buttons[key.getRow()][key.getColumn()] = button;
      button.setForeground(LABEL_FG_COLOR);
      button.setBackground(KEY_BG_COLOR);
      button.setAlignmentX(0.10f + (float)key.getXPos() / 47.0f);
      button.setAlignmentY(0.13f + (float)key.getYPos() / 5.0f);
      button.setPreferredSize(size);
      button.setFocusable(false);
      button.addMouseListener(mouseListener);
      add(button);

      JLabel topLabel;
      if (key.getTopLabel() == null) {
	topLabel = new JLabel(" ");
	topLabel.setForeground(LABEL_FG_COLOR);
      } else if (!key.getTopLabel().startsWith("_")) {
	topLabel = new JLabel(key.getTopLabel());
	topLabel.setForeground(LABEL_FG_COLOR);
      } else {
	topLabel = new JLabel(key.getTopLabel().substring(1));
	topLabel.setForeground(PANEL_BG_COLOR);
	topLabel.setBackground(LABEL_FG_COLOR);
	topLabel.setOpaque(true);
      }
      Font defaultFont = topLabel.getFont();
      float smallFontSize = defaultFont.getSize2D() * 0.7f;
      Font smallFont = defaultFont.deriveFont(smallFontSize);
      topLabel.setFont(smallFont);
      JPanel panelTop = new JPanel();
      panelTop.setBackground(PANEL_BG_COLOR);
      panelTop.setAlignmentX(0.10f + (float)key.getXPos() / 47.0f);
      panelTop.setAlignmentY(0.10f + (float)key.getYPos() / 5.0f);
      panelTop.add(topLabel);
      panelTop.setPreferredSize(size);
      add(panelTop);

      JLabel bottomLabel = new JLabel(key.getBottomLabel() != null ? 
				      key.getBottomLabel() : " ");
      bottomLabel.setForeground(LABEL_FG_COLOR);
      bottomLabel.setFont(smallFont);
      JPanel panelBottom = new JPanel();
      panelBottom.setBackground(PANEL_BG_COLOR);
      panelBottom.setAlignmentX(0.10f + (float)key.getXPos() / 47.0f);
      panelBottom.setAlignmentY(0.20f + (float)key.getYPos() / 5.0f);
      panelBottom.add(bottomLabel);
      panelBottom.setPreferredSize(size);
      add(panelBottom);
    }
  }

  public KeyboardPanel(KeyboardMatrix matrix) throws IOException {
    this.matrix = matrix;
    keyListener = new KeyListener();
    int rowCount = KeyboardMatrix.getRowCount();
    int columnCount = KeyboardMatrix.getColumnCount();
    buttons = new Button[rowCount][columnCount];
    addColorLabels();
    MouseListener mouseListener = new MouseListener();
    Iterator<KeyboardMatrix.Key> keys = KeyboardMatrix.getKeyIterator();
    while (keys.hasNext()) {
      addKey(keys.next(), mouseListener);
    }
    setBackground(PANEL_BG_COLOR);
    ProportionalLayout layout = new ProportionalLayout(this);
    setLayout(layout);
    setPreferredSize(new Dimension(1200, 900));
    addKeyListener(keyListener);
    setFocusable(true);
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
