package emulator.vz200;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;

import emulator.z80.WallClockProvider;

public class LineControl extends Box
{
  private static final long serialVersionUID = 4259806640992812397L;

  public static final double VOLUME_DEFAULT = 1.0;
  private static final double LOG_20 = 20.0 / Math.log(10.0);
  private static final double INV_LOG_20 = 1.0 / LOG_20;
  private static final int SLIDER_VOLUME_MIN = -96;
  private static final int SLIDER_VOLUME_MAX = 0;
  private static final int SLIDER_MAJOR_TICK_SPACING = 24;
  private static final int SLIDER_MINOR_TICK_SPACING = 6;

  private final String id;
  private final WallClockProvider wallClockProvider;
  private final List<LineControlListener> lineControlListeners;
  private final SourceDataLineControl sourceDataLineControl;
  private final JSlider slVolume;
  private final JLabel lbDB;
  private final JCheckBox cbMute;
  private final JLabel lbMuted;
  private double volume;
  private boolean muted;

  public LineControl(final String id,
                     final String borderTitle,
                     final String mixerInfoId,
                     final String lineInfoId,
                     final double initialVolume,
                     final boolean initiallyMuted,
                     final WallClockProvider wallClockProvider,
                     final LineControlListener userPreferencesChangeListener,
                     final Frame owner)
  {
    super(BoxLayout.Y_AXIS);
    if (id == null) {
      throw new NullPointerException("id");
    }
    this.id = id;
    if (wallClockProvider == null) {
      throw new NullPointerException("wallClockProvider");
    }
    this.volume = initialVolume;
    this.muted = initiallyMuted;
    this.wallClockProvider = wallClockProvider;
    lineControlListeners = new ArrayList<LineControlListener>();
    setBorder(BorderFactory.createTitledBorder(borderTitle));
    final int dbValue = (int)Math.round(Math.log(volume) * LOG_20);
    slVolume = new JSlider(SwingConstants.HORIZONTAL,
                           SLIDER_VOLUME_MIN, SLIDER_VOLUME_MAX,
                           dbValue);
    lbDB = new JLabel("" + SLIDER_VOLUME_MIN, SwingConstants.RIGHT);
    lbMuted = new JLabel();
    cbMute = new JCheckBox("Mute");
    sourceDataLineControl =
      new SourceDataLineControl(id, wallClockProvider,
                                mixerInfoId, lineInfoId,
                                owner);
    sourceDataLineControl.addListener(userPreferencesChangeListener);
    add(sourceDataLineControl);
    add(Box.createVerticalStrut(5));
    add(Box.createVerticalGlue());
    add(createVolumeLabel());
    add(Box.createVerticalStrut(5));
    add(createVolumeControl());
    addListener(userPreferencesChangeListener);
    lbDB.setText("" + dbValue);
  }

  private Box createVolumeLabel()
  {
    final Box bxVolumeLabel = new Box(BoxLayout.X_AXIS);
    final JLabel lbVolume = new JLabel("Volume");
    bxVolumeLabel.add(lbVolume);
    lbVolume.setDisplayedMnemonic(KeyEvent.VK_V);
    lbVolume.setLabelFor(slVolume);
    bxVolumeLabel.add(Box.createHorizontalGlue());
    return bxVolumeLabel;
  }

  private Box createVolumeControl()
  {
    final Box bxVolumeControl = new Box(BoxLayout.X_AXIS);
    slVolume.setMajorTickSpacing(SLIDER_MAJOR_TICK_SPACING);
    slVolume.setMinorTickSpacing(SLIDER_MINOR_TICK_SPACING);
    slVolume.setPaintTicks(true);
    slVolume.setPaintLabels(true);
    slVolume.addChangeListener((final ChangeEvent event) -> {
        volumeChanged();
      });
    slVolume.setMinimumSize(slVolume.getPreferredSize());
    slVolume.setMaximumSize(slVolume.getPreferredSize());
    bxVolumeControl.add(slVolume);
    lbDB.setPreferredSize(lbDB.getMinimumSize());
    lbDB.setMinimumSize(lbDB.getPreferredSize());
    lbDB.setMaximumSize(lbDB.getPreferredSize());
    bxVolumeControl.add(lbDB);
    bxVolumeControl.add(new JLabel("dB"));
    bxVolumeControl.add(Box.createHorizontalStrut(5));
    lbMuted.setAlignmentX(1.0f);
    cbMute.setMnemonic(KeyEvent.VK_M);
    cbMute.addChangeListener((final ChangeEvent event) -> {
        muteChanged();
      });
    cbMute.setSelected(muted);
    muteChanged();
    bxVolumeControl.add(cbMute);
    bxVolumeControl.add(Box.createHorizontalGlue());
    bxVolumeControl.add(lbMuted);
    bxVolumeControl.add(Box.createHorizontalStrut(5));
    final int maxHeight = (int)bxVolumeControl.getPreferredSize().getHeight();
    final Dimension maximumSize = new Dimension(Integer.MAX_VALUE, maxHeight);
    bxVolumeControl.setMaximumSize(maximumSize);
    return bxVolumeControl;
  }

  public void addListener(final LineControlListener listener)
  {
    printMessage("adding listener " + listener);
    sourceDataLineControl.addListener(listener);
    lineControlListeners.add(listener);
    listener.volumeChanged(volume);
    listener.mutedChanged(muted);
  }

  public void removeListener(final LineControlListener listener)
  {
    sourceDataLineControl.removeListener(listener);
    lineControlListeners.remove(listener);
  }

  private void printMessage(final String message)
  {
    System.out.printf("LineControl '%s': %s%n", id, message);
  }

  private void volumeChanged()
  {
    final int dbValue = slVolume.getValue();
    lbDB.setText("" + dbValue);
    lbDB.setPreferredSize(lbDB.getMinimumSize());
    volume = Math.exp(dbValue * INV_LOG_20);
    for (final LineControlListener listener : lineControlListeners) {
      listener.volumeChanged(volume);
    }
  }

  private void muteChanged()
  {
    muted = cbMute.isSelected();
    lbMuted.setIcon(muted ? Icons.LINE_MUTED : Icons.LINE_UNMUTED);
    for (final LineControlListener listener : lineControlListeners) {
      listener.mutedChanged(muted);
    }
  }
}

/*
 * Local Variables:
 *   coding:utf-8
 *   mode:Java
 * End:
 */
