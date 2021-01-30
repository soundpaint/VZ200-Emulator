package emulator.vz200;

import java.awt.event.KeyEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.event.ChangeEvent;

public class VZFileSampleControl extends Box
{
  private static final long serialVersionUID = 3464216705257155313L;

  private static final String TOOL_TIP_LEAD_IN =
    "<html>\n" +
    "  Reduces the lead-in sequence from 255 to 10 bytes.<br />\n" +
    "  The lead-in sequence is used for enhancing reliability<br />\n" +
    "  of audio material with degraded quality, which is<br />\n" +
    "  somewhat pointless for perfect quality input created<br />\n" +
    "  on-the-fly, unless for higher authenticity of the<br />\n" +
    "  emulation.<br />\n" +
    "  If the CLOAD / CRUN command has already been issued when<br />\n" +
    "  starting replay, you can safely minimize lead-in, and<br />\n" +
    "  thus speeding up the process of reading in all data by<br />\n" +
    "  almost 4 seconds.\n" +
    "</html>\n";

  private final JCheckBox cbTrimLeadIn;

  public VZFileSampleControl()
  {
    super(BoxLayout.Y_AXIS);
    setBorder(BorderFactory.createTitledBorder("VZ Files"));

    cbTrimLeadIn = createAndAddTrimLeadInCheckBox();
    trimLeadInChanged();

    add(Box.createVerticalGlue());
  }

  private JCheckBox createAndAddTrimLeadInCheckBox()
  {
    final Box boxTrimLeadIn = new Box(BoxLayout.X_AXIS);
    add(boxTrimLeadIn);
    final UserPreferences userPreferences = UserPreferences.getInstance();
    final JCheckBox cbTrimLeadIn = new JCheckBox("Minimize Lead-In");
    cbTrimLeadIn.setToolTipText(TOOL_TIP_LEAD_IN);
    cbTrimLeadIn.setSelected(userPreferences.getCassetteInVzTrimLeadIn());
    cbTrimLeadIn.setMnemonic(KeyEvent.VK_L);
    cbTrimLeadIn.addChangeListener((final ChangeEvent event) -> {
        trimLeadInChanged();
      });
    boxTrimLeadIn.add(cbTrimLeadIn);
    boxTrimLeadIn.add(Box.createHorizontalGlue());
    return cbTrimLeadIn;
  }

  private void trimLeadInChanged()
  {
    final UserPreferences userPreferences = UserPreferences.getInstance();
    userPreferences.setCassetteInVzTrimLeadIn(cbTrimLeadIn.isSelected());
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
