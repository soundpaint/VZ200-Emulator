package emulator.vz200;

import java.awt.event.KeyEvent;
import java.util.Hashtable;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;

public class AudioFileSampleControl extends Box
  implements LineControlListener
{
  private static final long serialVersionUID = 1200435071825275940L;

  private static final String TOOL_TIP_INVERT_PHASE =
    "<html>\n" +
    "  If you fail loading in data from a file via the<br />\n" +
    "  VZ200's cassette in interface, you may try to toggle<br />\n" +
    "  this checkbox.  It will invert the phase of the audio<br />\n" +
    "  signal, which may help in some cases.\n" +
    "</html>\n";

  private static final String TOOL_TIP_DC_OFFSET =
    "<html>\n" +
    "  If you fail loading in data from a file via the<br />\n" +
    "  VZ200's cassette in interface, you may try to adjust<br />\n" +
    "  this slider.  By default (for a high-quality audio<br />\n" +
    "  signal), this should be set to 0.0%.  However, for<br />\n" +
    "  a degenerated or badly recorded signal, you may<br />\n" +
    "  need to adjust the DC offset for loading in data<br />\n" +
    "  to get working.\n" +
    "</html>\n";

  private static final int SLIDER_DC_OFFS_MIN = -300;
  private static final int SLIDER_DC_OFFS_MAX = +300;
  private static final int SLIDER_DC_OFFS_MAJOR_TICK_SPACING = 100;
  private static final int SLIDER_DC_OFFS_MINOR_TICK_SPACING = 50;

  private final JCheckBox cbInvertPhase;
  private final JSlider slDcOffset;
  private final JLabel lbDcOffsetValue;
  private final VolumeControl volumeControl;

  public AudioFileSampleControl()
  {
    super(BoxLayout.Y_AXIS);
    setBorder(BorderFactory.createTitledBorder("Audio Files"));

    cbInvertPhase = createAndAddInvertPhaseCheckBox();
    invertPhaseChanged();

    lbDcOffsetValue = new JLabel("");
    slDcOffset = createAndAddDcOffsetSlider();
    dcOffsetChanged();

    final UserPreferences userPreferences = UserPreferences.getInstance();
    volumeControl = new VolumeControl("audio file sample volume",
                                      null,
                                      userPreferences.getCassetteInVolume(),
                                      "Signal Volume Adjustment [dB]",
                                      false, false, null);
    volumeControl.addListener(this);
    add(volumeControl);
  }

  private JCheckBox createAndAddInvertPhaseCheckBox()
  {
    final Box boxInvertPhase = new Box(BoxLayout.X_AXIS);
    add(boxInvertPhase);
    final UserPreferences userPreferences = UserPreferences.getInstance();
    final JCheckBox cbInvertPhase = new JCheckBox("Signal Invert Phase");
    cbInvertPhase.setToolTipText(TOOL_TIP_INVERT_PHASE);
    cbInvertPhase.setSelected(userPreferences.getCassetteInInvertPhase());
    cbInvertPhase.setMnemonic(KeyEvent.VK_P);
    cbInvertPhase.addChangeListener((final ChangeEvent event) -> {
        invertPhaseChanged();
      });
    boxInvertPhase.add(cbInvertPhase);
    boxInvertPhase.add(Box.createHorizontalGlue());
    return cbInvertPhase;
  }

  private JSlider createAndAddDcOffsetSlider()
  {
    final Box boxLabelDcOffset = new Box(BoxLayout.X_AXIS);
    add(boxLabelDcOffset);
    final JLabel lbDcOffset = new JLabel("Signal DC Offset Adjustment [%]");
    lbDcOffset.setDisplayedMnemonic(KeyEvent.VK_O);
    boxLabelDcOffset.add(lbDcOffset);
    boxLabelDcOffset.add(Box.createHorizontalGlue());

    final UserPreferences userPreferences = UserPreferences.getInstance();
    final Box boxDcOffset = new Box(BoxLayout.X_AXIS);
    add(boxDcOffset);
    final int slDcOffsetInitialValue =
      (int)(Math.round(1000.0 *
                       (userPreferences.getCassetteInDcOffset() - 1.0)));

    final JSlider slDcOffset = new JSlider(SwingConstants.HORIZONTAL,
                                           SLIDER_DC_OFFS_MIN,
                                           SLIDER_DC_OFFS_MAX,
                                           slDcOffsetInitialValue);
    slDcOffset.setToolTipText(TOOL_TIP_DC_OFFSET);
    slDcOffset.setMajorTickSpacing(SLIDER_DC_OFFS_MAJOR_TICK_SPACING);
    slDcOffset.setMinorTickSpacing(SLIDER_DC_OFFS_MINOR_TICK_SPACING);
    slDcOffset.setPaintTicks(true);
    slDcOffset.setPaintLabels(true);

    final Hashtable<Integer, JLabel> slDcOffsetLabels =
      new Hashtable<Integer, JLabel>();
    slDcOffsetLabels.put(-300, new JLabel("-30.0"));
    slDcOffsetLabels.put(0, new JLabel("0.0"));
    slDcOffsetLabels.put(300, new JLabel("+30.0"));
    // Java 9+:
    /*
    final var slDcOffsetLabels =
      new Hashtable<Integer, JLabel>(Map.of(-300, new JLabel("-30.0"),
                                            0, new JLabel("0.0"),
                                            300, new JLabel("+30.0")));
    */
    slDcOffset.setLabelTable(slDcOffsetLabels);
    slDcOffset.addChangeListener((final ChangeEvent event) -> {
        dcOffsetChanged();
      });
    slDcOffset.setMinimumSize(slDcOffset.getPreferredSize());
    slDcOffset.setMaximumSize(slDcOffset.getPreferredSize());
    boxDcOffset.add(slDcOffset);
    boxDcOffset.add(lbDcOffsetValue);
    boxDcOffset.add(Box.createHorizontalGlue());
    add(Box.createVerticalGlue());

    return slDcOffset;
  }

  private void invertPhaseChanged()
  {
    final UserPreferences userPreferences = UserPreferences.getInstance();
    userPreferences.setCassetteInInvertPhase(cbInvertPhase.isSelected());
  }

  private void dcOffsetChanged()
  {
    final UserPreferences userPreferences = UserPreferences.getInstance();
    lbDcOffsetValue.setText(String.format("%+2.1f%%", 0.1 * slDcOffset.getValue()));
    userPreferences.setCassetteInDcOffset(1.0 + slDcOffset.getValue() * 0.001);
  }

  @Override
  public void volumeChanged(final double volume)
  {
    final UserPreferences userPreferences = UserPreferences.getInstance();
    userPreferences.setCassetteInVolume(volume);
  }

  @Override
  public void mutedChanged(final boolean muted)
  {
    // we do not have such a control
  }

  @Override
  public void lineChanged(final SourceDataLineChangeEvent event)
  {
    // we do not have such a control
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
