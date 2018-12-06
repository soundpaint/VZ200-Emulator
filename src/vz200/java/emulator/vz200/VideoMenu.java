package emulator.vz200;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import javax.swing.KeyStroke;
import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;

public class VideoMenu extends JMenuBar
{
  private static final long serialVersionUID = -4170952420939834952L;

  private Video video;
  private JMenuItem zoomFactor1;
  private JMenuItem zoomFactor2;
  private JMenuItem zoomFactor3;
  private UserPreferences preferences;

  private VideoMenu()
  {
    throw new RuntimeException("unsupported constructor");
  }

  public VideoMenu(final Video video)
  {
    if (video == null) {
      throw new NullPointerException("video");
    }
    this.video = video;
    setBackground(Color.black);
    setBorderPainted(false);
    add(createZoomMenu());
    preferences = UserPreferences.getInstance();
    final int zoomFactor = preferences.getVideoZoomFactor();
    System.out.println("restoring zoom factor " + zoomFactor);
    switch (zoomFactor) {
    case 1:
      zoomFactor1.setSelected(zoomFactor == 1);
      break;
    case 2:
      zoomFactor2.setSelected(zoomFactor == 2);
      break;
    case 3:
      zoomFactor3.setSelected(zoomFactor == 3);
      break;
    default:
      throw new InternalError("unexpected zoom factor: " + zoomFactor);
    }
  }

  private JMenu createZoomMenu()
  {
    final JMenu zoom = new JMenu("Zoom");
    zoom.setForeground(Color.gray);
    zoom.setMnemonic(KeyEvent.VK_Z);

    ButtonGroup zoomFactors = new ButtonGroup();
    zoomFactor1 = new JRadioButtonMenuItem("× 1");
    zoomFactor1.setMnemonic(KeyEvent.VK_1);
    zoomFactor1.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_1,
                                                      ActionEvent.ALT_MASK));
    zoomFactor1.getAccessibleContext().
      setAccessibleDescription("Set zoom factor 1");
    zoomFactor1.addActionListener((final ActionEvent event) -> {
        video.setZoomFactor(1);
        preferences.setVideoZoomFactor(1);
      });
    zoomFactors.add(zoomFactor1);
    zoom.add(zoomFactor1);

    zoomFactor2 = new JRadioButtonMenuItem("× 2");
    zoomFactor2.setMnemonic(KeyEvent.VK_2);
    zoomFactor2.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_2,
                                                      ActionEvent.ALT_MASK));
    zoomFactor2.getAccessibleContext().
      setAccessibleDescription("Set zoom factor 2");
    zoomFactor2.addActionListener((final ActionEvent event) -> {
        video.setZoomFactor(2);
        preferences.setVideoZoomFactor(2);
      });
    zoomFactors.add(zoomFactor2);
    zoom.add(zoomFactor2);

    zoomFactor3 = new JRadioButtonMenuItem("× 3");
    zoomFactor3.setMnemonic(KeyEvent.VK_3);
    zoomFactor3.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_3,
                                                      ActionEvent.ALT_MASK));
    zoomFactor3.getAccessibleContext().
      setAccessibleDescription("Set zoom factor 3");
    zoomFactor3.addActionListener((final ActionEvent event) -> {
        video.setZoomFactor(3);
        preferences.setVideoZoomFactor(3);
      });
    zoomFactors.add(zoomFactor3);
    zoom.add(zoomFactor3);

    return zoom;
  }
}

/*
 * Local Variables:
 *   coding:utf-8
 *   mode:java
 * End:
 */
