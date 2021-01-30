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

public class CommonFileSampleControl extends Box
{
  private static final long serialVersionUID = -6805482426722168542L;

  private static final String TOOL_TIP_SPEED =
    "<html>\n" +
    "  If you fail loading in data from a file via the<br />\n" +
    "  VZ200's cassette in interface, you may try to adjust<br />\n" +
    "  this slider.  By default (for a high-quality audio<br />\n" +
    "  signal), this should be set to 0.0%.  However, for<br />\n" +
    "  a degenerated or badly recorded signal, you may<br />\n" +
    "  need to fine-adjust playback speed for loading in<br />\n" +
    "  data to get working.\n" +
    "</html>\n";

  private static final String TOOL_TIP_LINK_SPEED_WITH_CPU_FREQ =
    "<html>\n" +
    "  If checked, playback speed of files is linked to the<br />\n" +
    "  effective speed of the emulated CPU, such that the<br />\n" +
    "  VZ200's built-in functions for reading from cassette<br />\n" +
    "  input will still work as expected.<br />\n" +
    "  If not checked, playback will be performed at the<br />\n" +
    "  standard speed, that is assuming the designed speed<br />\n" +
    "  of 3.57MHz.<br />\n" +
    "  The above speed adjustment option for fine-tuning the<br />\n" +
    "  playback speed is unaffected by the state of this<br />\n" +
    "  option and will still independently work.\n" +
    "</html>\n";

  private static final int SLIDER_SPEED_MIN = -100;
  private static final int SLIDER_SPEED_MAX = +100;
  private static final int SLIDER_SPEED_MAJOR_TICK_SPACING = 100;
  private static final int SLIDER_SPEED_MINOR_TICK_SPACING = 20;

  private final JSlider slSpeed;
  private final JLabel lbSpeedValue;
  private final JCheckBox cbLinkSpeedWithCpuFreq;

  public CommonFileSampleControl()
  {
    super(BoxLayout.Y_AXIS);
    final UserPreferences userPreferences = UserPreferences.getInstance();
    setBorder(BorderFactory.createTitledBorder("Common"));

    lbSpeedValue = new JLabel("");
    slSpeed = createAndAddSpeedSlider();
    speedChanged();

    cbLinkSpeedWithCpuFreq = createAndAddLinkSpeedWithCpuFreqCheckBox();
    linkSpeedWithCpuFreqChanged();

    add(Box.createVerticalGlue());
  }

  private JSlider createAndAddSpeedSlider()
  {
    final Box boxLabelSpeed = new Box(BoxLayout.X_AXIS);
    add(boxLabelSpeed);
    final JLabel lbSpeed = new JLabel("Speed Adjustment [%]");
    lbSpeed.setDisplayedMnemonic(KeyEvent.VK_S);
    boxLabelSpeed.add(lbSpeed);
    boxLabelSpeed.add(Box.createHorizontalGlue());

    final UserPreferences userPreferences = UserPreferences.getInstance();
    final Box boxSpeed = new Box(BoxLayout.X_AXIS);
    add(boxSpeed);
    final int slSpeedInitialValue =
      (int)(Math.round(1000.0 * (userPreferences.getCassetteInSpeed() - 1.0)));

    final JSlider slSpeed =
      new JSlider(SwingConstants.HORIZONTAL,
                  SLIDER_SPEED_MIN, SLIDER_SPEED_MAX,
                  slSpeedInitialValue);
    slSpeed.setToolTipText(TOOL_TIP_SPEED);
    slSpeed.setMajorTickSpacing(SLIDER_SPEED_MAJOR_TICK_SPACING);
    slSpeed.setMinorTickSpacing(SLIDER_SPEED_MINOR_TICK_SPACING);
    slSpeed.setPaintTicks(true);
    slSpeed.setPaintLabels(true);

    final Hashtable<Integer, JLabel> slSpeedLabels =
      new Hashtable<Integer, JLabel>();
    slSpeedLabels.put(-100, new JLabel("-10.0"));
    slSpeedLabels.put(0, new JLabel("0.0"));
    slSpeedLabels.put(100, new JLabel("+10.0"));
    // Java 9+:
    /*
    final var slSpeedLabels =
      new Hashtable<Integer, JLabel>(Map.of(-100, new JLabel("-10.0"),
                                            0, new JLabel("0.0"),
                                            100, new JLabel("+10.0")));
    */
    slSpeed.setLabelTable(slSpeedLabels);
    slSpeed.addChangeListener((final ChangeEvent event) -> {
        speedChanged();
      });
    slSpeed.setMinimumSize(slSpeed.getPreferredSize());
    slSpeed.setMaximumSize(slSpeed.getPreferredSize());
    boxSpeed.add(slSpeed);
    boxSpeed.add(lbSpeedValue);
    boxSpeed.add(Box.createHorizontalGlue());
    add(Box.createVerticalGlue());

    return slSpeed;
  }

  private JCheckBox createAndAddLinkSpeedWithCpuFreqCheckBox()
  {
    final Box boxLinkSpeedWithCpuFreq = new Box(BoxLayout.X_AXIS);
    add(boxLinkSpeedWithCpuFreq);
    final UserPreferences userPreferences = UserPreferences.getInstance();
    final JCheckBox cbLinkSpeedWithCpuFreq =
      new JCheckBox("Link Speed With CPU Frequency");
    cbLinkSpeedWithCpuFreq.setToolTipText(TOOL_TIP_LINK_SPEED_WITH_CPU_FREQ);
    cbLinkSpeedWithCpuFreq.
      setSelected(userPreferences.getCassetteInLinkSpeedWithCpuFreq());
    cbLinkSpeedWithCpuFreq.setMnemonic(KeyEvent.VK_F);
    cbLinkSpeedWithCpuFreq.addChangeListener((final ChangeEvent event) -> {
        linkSpeedWithCpuFreqChanged();
      });
    boxLinkSpeedWithCpuFreq.add(cbLinkSpeedWithCpuFreq);
    boxLinkSpeedWithCpuFreq.add(Box.createHorizontalGlue());
    return cbLinkSpeedWithCpuFreq;
  }

  private void speedChanged()
  {
    final UserPreferences userPreferences = UserPreferences.getInstance();
    lbSpeedValue.setText(String.format("%+2.1f%%", 0.1 * slSpeed.getValue()));
    userPreferences.setCassetteInSpeed(1.0 + slSpeed.getValue() * 0.001);
  }

  private void linkSpeedWithCpuFreqChanged()
  {
    final UserPreferences userPreferences = UserPreferences.getInstance();
    userPreferences.
      setCassetteInLinkSpeedWithCpuFreq(cbLinkSpeedWithCpuFreq.isSelected());
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
