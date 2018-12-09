package emulator.vz200;

import java.awt.Dimension;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;

public class SpeakerControl extends Box
{
  private static final long serialVersionUID = 4259806640992812397L;

  public static final double VOLUME_DEFAULT = 0.8;
  private static final int SLIDER_VOLUME_MIN = -96;
  private static final int SLIDER_VOLUME_MAX = 0;
  private static final int SLIDER_MAJOR_TICK_SPACING = 24;
  private static final int SLIDER_MINOR_TICK_SPACING = 6;

  private static final ImageIcon ICON_SPEAKER =
    VZ200.createIcon("speaker32x32.png", null);
  private static final Dimension FILLER_DIM = new Dimension(5, 32);

  private final List<SpeakerControlListener> listeners;
  private final JSlider sliderVolume;
  private final JLabel lblDB;
  private final JCheckBox cbMute;
  private double volume;
  private boolean muted;

  public SpeakerControl()
  {
    super(BoxLayout.X_AXIS);
    setBorder(BorderFactory.createTitledBorder("Speaker"));
    listeners = new ArrayList<SpeakerControlListener>();

    volume = UserPreferences.getInstance().getSpeakerVolume();
    final int dbValue =
      (int)Math.round(Math.log(volume) / Math.log(10.0) * 20.0);
    sliderVolume = new JSlider(SwingConstants.HORIZONTAL,
                               SLIDER_VOLUME_MIN, SLIDER_VOLUME_MAX,
                               dbValue);
    sliderVolume.setMajorTickSpacing(SLIDER_MAJOR_TICK_SPACING);
    sliderVolume.setMinorTickSpacing(SLIDER_MINOR_TICK_SPACING);
    sliderVolume.setPaintTicks(true);
    sliderVolume.setPaintLabels(true);
    sliderVolume.addChangeListener((final ChangeEvent event) -> {
        volumeChanged();
      });
    sliderVolume.doLayout();
    sliderVolume.setMinimumSize(sliderVolume.getPreferredSize());
    sliderVolume.setMaximumSize(sliderVolume.getPreferredSize());
    add(sliderVolume);
    lblDB = new JLabel("" + SLIDER_VOLUME_MIN, SwingConstants.RIGHT);
    lblDB.doLayout();
    lblDB.setMinimumSize(lblDB.getPreferredSize());
    lblDB.setMaximumSize(lblDB.getPreferredSize());
    add(lblDB);
    lblDB.setText("" + dbValue);
    lblDB.setPreferredSize(lblDB.getMinimumSize());
    add(new JLabel("dB"));

    add(new Filler(FILLER_DIM, FILLER_DIM, FILLER_DIM));

    muted = UserPreferences.getInstance().getSpeakerMuted();
    cbMute = new JCheckBox("Mute");
    cbMute.setSelected(muted);
    cbMute.addChangeListener((final ChangeEvent event) -> {
        muteChanged();
      });
    add(cbMute);

    add(Box.createHorizontalGlue());

    JLabel lblSpeaker = new JLabel(ICON_SPEAKER);
    lblSpeaker.setAlignmentX(1.0f);
    add(lblSpeaker);

    add(new Filler(FILLER_DIM, FILLER_DIM, FILLER_DIM));
  }

  public void addListener(final SpeakerControlListener listener)
  {
    listeners.add(listener);
    listener.setVolume(volume);
    listener.setMuted(muted);
  }

  public void removeListener(final SpeakerControlListener listener)
  {
    listeners.remove(listener);
  }

  private void volumeChanged()
  {
    final int dbValue = sliderVolume.getValue();
    lblDB.setText("" + dbValue);
    lblDB.setPreferredSize(lblDB.getMinimumSize());
    volume = Math.exp(dbValue / 20.0 * Math.log(10.0));
    UserPreferences.getInstance().setSpeakerVolume(volume);
    for (final SpeakerControlListener listener : listeners) {
      listener.setVolume(volume);
    }
  }

  private void muteChanged()
  {
    muted = cbMute.isSelected();
    UserPreferences.getInstance().setSpeakerMuted(muted);
    for (final SpeakerControlListener listener : listeners) {
      listener.setMuted(muted);
    }
  }
}

/*
 * Local Variables:
 *   coding:utf-8
 *   mode:Java
 * End:
 */
