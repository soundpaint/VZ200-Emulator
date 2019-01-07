package emulator.vz200;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;

public class SourceDataLineSelectionDialog extends JDialog
{
  private static final long serialVersionUID = -4889480144477715660L;

  private static interface ToolTipProvider
  {
    String getToolTip();
  }

  /**
   * Wrapper class for Mixer.Info with toString() method made
   * suitable for JComboBox display.
   */
  private static class MixerInfo implements ToolTipProvider
  {
    private final Mixer.Info info;

    private MixerInfo() {
      throw new UnsupportedOperationException("unsupported empty constructor");
    }

    public MixerInfo(final Mixer.Info info)
    {
      if (info == null) {
        throw new NullPointerException("info");
      }
      this.info = info;
    }

    public Mixer.Info getInfo() { return info; }

    public String getToolTip()
    {
      return info.getDescription();
    }

    public String toString()
    {
      return info.getName();
    }
  }

  /**
   * Wrapper class for Line.Info with toString() method made
   * suitable for JComboBox display.
   */
  private static class LineInfo implements ToolTipProvider
  {
    private final Line.Info info;

    private LineInfo() {
      throw new UnsupportedOperationException("unsupported empty constructor");
    }

    public LineInfo(final Line.Info info)
    {
      if (info == null) {
        throw new NullPointerException("info");
      }
      this.info = info;
    }

    public Line.Info getInfo() { return info; }

    public String getToolTip()
    {
      return info.toString();
    }

    public String toString()
    {
      final Class<?> clazz = info.getLineClass();
      return clazz.getSimpleName();
    }
  }

  private static class ToolTipRenderer extends DefaultListCellRenderer
  {
    private static final long serialVersionUID = 2518437083704630409L;

    @Override
    public Component getListCellRendererComponent(final JList<?> list,
                                                  final Object value,
                                                  final int index,
                                                  final boolean isSelected,
                                                  final boolean cellHasFocus)
    {
      if (value instanceof ToolTipProvider) {
        final ToolTipProvider toolTipProvider = (ToolTipProvider)value;
        list.setToolTipText(toolTipProvider.getToolTip());
      } else {
        list.setToolTipText(null);
      }
      return
        super.getListCellRendererComponent(list, value, index, isSelected,
                                           cellHasFocus);
    }
  }

  private final String id;
  private final JComboBox<MixerInfo> cbMixerSelector;
  private final JComboBox<LineInfo> cbLineSelector;
  private final JScrollPane spLineDescription;
  private final JOptionPane opSelection;
  private final JButton btPaneOptionSelect;
  private final JButton btPaneOptionCancel;
  private String preferredMixerId;
  private String preferredLineId;

  public SourceDataLineSelectionDialog(final String id,
                                       final String preferredMixerId,
                                       final String preferredLineId,
                                       final Frame owner)
  {
    super(owner, "Select Output Line for " + id, true);
    if (id == null) {
      throw new NullPointerException("id");
    }
    this.id = id;
    this.preferredMixerId = preferredMixerId;
    this.preferredLineId = preferredLineId;
    final JButton[] paneOptions = {
      btPaneOptionSelect =
      createPaneOption("Select", KeyEvent.VK_S,
                       "Apply selected line and close dialog."),
      btPaneOptionCancel =
      createPaneOption("Cancel", KeyEvent.VK_C,
                       "Close dialog without applying selected line.")
    };
    spLineDescription = createLineDescription();
    final ToolTipRenderer selectorRenderer = new ToolTipRenderer();
    cbMixerSelector = createMixerSelector(selectorRenderer);
    cbLineSelector = createLineSelector(selectorRenderer);
    opSelection = createContentPane(paneOptions, spLineDescription);
    setContentPane(opSelection);
    rebuildMixerSelector(); // pop-up warning if no mixer available
  }

  private JButton createPaneOption(final String label,
                                   final int mnemonic,
                                   final String toolTipText)
  {
    final JButton button = new JButton(label);
    button.setToolTipText(toolTipText);
    if (mnemonic != 0) {
      button.setMnemonic(mnemonic);
    }
    button.addActionListener((final ActionEvent event) ->
                             {
                               opSelection.setValue(button);
                               setVisible(false);
                             });
    return button;
  }

  private JScrollPane createLineDescription()
  {
    final JTextPane tpLineDescription = new JTextPane();
    tpLineDescription.setEditable(false);
    final JScrollPane spLineDescription = new JScrollPane(tpLineDescription);
    spLineDescription.setBorder(BorderFactory.
                                createTitledBorder("Line to be Selected"));
    spLineDescription.setVerticalScrollBarPolicy(JScrollPane.
                                                 VERTICAL_SCROLLBAR_ALWAYS);
    spLineDescription.setMinimumSize(new Dimension(200, 80));
    spLineDescription.setPreferredSize(new Dimension(400, 80));
    return spLineDescription;
  }

  private JComboBox<MixerInfo>
    createMixerSelector(final ToolTipRenderer selectorRenderer)
  {
    final JComboBox<MixerInfo> cbMixerSelector = new JComboBox<MixerInfo>();
    cbMixerSelector.setRenderer(selectorRenderer);
    cbMixerSelector.setToolTipText("Select a mixer to choose a line from.");
    cbMixerSelector.addActionListener((final ActionEvent event) ->
                                      { mixerSelectionChanged(); });
    return cbMixerSelector;
  }

  private JComboBox<LineInfo>
    createLineSelector(final ToolTipRenderer selectorRenderer)
  {
    final JComboBox<LineInfo> cbLineSelector = new JComboBox<LineInfo>();
    cbLineSelector.setRenderer(selectorRenderer);
    cbLineSelector.setToolTipText("Choose a line from the selected mixer.");
    cbLineSelector.addActionListener((final ActionEvent event) ->
                                     { lineSelectionChanged(); });
    return cbLineSelector;
  }

  private Box createMixerSelection()
  {
    final Box bxMixerSelection = new Box(BoxLayout.Y_AXIS);
    final Box bxSelectMixerLabel = new Box(BoxLayout.X_AXIS);
    final JLabel lbSelectMixer = new JLabel("Select Mixer");
    lbSelectMixer.setDisplayedMnemonic(KeyEvent.VK_M);
    lbSelectMixer.setLabelFor(cbMixerSelector);
    bxSelectMixerLabel.add(lbSelectMixer);
    bxSelectMixerLabel.add(Box.createHorizontalGlue());
    bxMixerSelection.add(bxSelectMixerLabel);
    bxMixerSelection.add(cbMixerSelector);
    return bxMixerSelection;
  }

  private Box createLineSelection()
  {
    final Box bxLineSelection = new Box(BoxLayout.Y_AXIS);
    final Box bxSelectLineLabel = new Box(BoxLayout.X_AXIS);
    final JLabel lbSelectLine = new JLabel("Select Line of Mixer");
    lbSelectLine.setDisplayedMnemonic(KeyEvent.VK_L);
    lbSelectLine.setDisplayedMnemonicIndex(7);
    lbSelectLine.setLabelFor(cbLineSelector);
    bxSelectLineLabel.add(lbSelectLine);
    bxSelectLineLabel.add(Box.createHorizontalGlue());
    bxLineSelection.add(bxSelectLineLabel);
    bxLineSelection.add(cbLineSelector);
    return bxLineSelection;
  }

  private Box createSelection()
  {
    final Box bxSelection = new Box(BoxLayout.X_AXIS);
    bxSelection.setBorder(BorderFactory.
                          createTitledBorder("Select Line from a Mixer"));

    bxSelection.add(Box.createHorizontalStrut(5));
    bxSelection.add(createMixerSelection());
    bxSelection.add(Box.createHorizontalStrut(5));
    bxSelection.add(createLineSelection());

    bxSelection.add(Box.createHorizontalStrut(5));
    final Dimension selectionMaximumSize =
      new Dimension((int)bxSelection.getMaximumSize().getWidth(),
                    (int)bxSelection.getPreferredSize().getHeight());
    bxSelection.setMaximumSize(selectionMaximumSize);
    return bxSelection;
  }

  private JOptionPane createContentPane(final JButton[] paneOptions,
                                        final JComponent paneContent)
  {
    final Box bxContent = new Box(BoxLayout.Y_AXIS);
    bxContent.add(createSelection());

    bxContent.add(Box.createVerticalStrut(5));
    bxContent.add(paneContent);
    bxContent.add(Box.createVerticalGlue());
    final JOptionPane opSelection =
      new JOptionPane(bxContent,
                      JOptionPane.PLAIN_MESSAGE,
                      JOptionPane.YES_NO_OPTION,
                      null,
                      paneOptions, paneOptions[0]);
    opSelection.registerKeyboardAction((final ActionEvent event) -> {
        SourceDataLineSelectionDialog.this.setVisible(false);
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
      JComponent.WHEN_IN_FOCUSED_WINDOW);
    return opSelection;
  }

  private void printMessage(final String message)
  {
    System.out.printf("%s: %s%n", id, message);
  }

  private Mixer.Info[] getMixerInfo()
  {
    final Mixer.Info[] mixerInfo = AudioSystem.getMixerInfo();
    if (mixerInfo.length == 0) {
      final String message =
        "There will be no audible output line for " + id + ".";
      final String title = "No Mixer Found for " + id;
      JOptionPane.showMessageDialog(this, message, title,
                                    JOptionPane.WARNING_MESSAGE);
    }
    for (int i = 0; i < mixerInfo.length; i++) {
      printMessage(String.format("found mixer: %s", mixerInfo[i]));
    }
    return mixerInfo;
  }

  private Line.Info[] getSourceLineInfo(final Mixer mixer)
  {
    final Line.Info[] sourceLineInfo = mixer.getSourceLineInfo();
    for (int i = 0; i < sourceLineInfo.length; i++) {
      printMessage("found source line info: " + sourceLineInfo[i]);
    }
    return sourceLineInfo;
  }

  private boolean rebuildMixerSelector()
  {
    final Mixer.Info[] mixerInfo = getMixerInfo();
    if (mixerInfo.length == 0) {
      return false;
    }
    cbMixerSelector.removeAllItems(); // clear only after successful
                                      // mixer info retrieval
    for (Mixer.Info info : mixerInfo) {
      final MixerInfo wrappedInfo = new MixerInfo(info);
      cbMixerSelector.addItem(wrappedInfo);
      if (info.toString().equals(preferredMixerId)) {
        cbMixerSelector.setSelectedItem(wrappedInfo);
      }
    }
    return true;
  }

  private void mixerSelectionChanged()
  {
    final MixerInfo mixerInfo = (MixerInfo)cbMixerSelector.getSelectedItem();
    if (mixerInfo != null) {
      final Mixer mixer = AudioSystem.getMixer(mixerInfo.getInfo());
      final Line.Info[] lineInfo = getSourceLineInfo(mixer);
      if (mixer != null) {
        // clear only after successful line info retrieval
        cbLineSelector.removeAllItems();
        for (Line.Info info : lineInfo) {
          /*final*/ Line line;
          try {
            line = mixer.getLine(info);
          } catch (final LineUnavailableException e) {
            // ignore this source data line
            line = null;
          }
          if (line instanceof SourceDataLine) {
            final LineInfo wrappedInfo = new LineInfo(info);
            cbLineSelector.addItem(wrappedInfo);
            if (info.toString().equals(preferredLineId)) {
              cbLineSelector.setSelectedItem(wrappedInfo);
            }
          } else {
            // ignore anything else (e.g. PortMixerPort)
          }
        }
        final int itemCount = cbLineSelector.getItemCount();
        cbLineSelector.setEnabled(itemCount > 1);
        btPaneOptionSelect.setEnabled(itemCount > 0);
      }
    } else {
      cbLineSelector.removeAllItems();
    }
  }

  private void lineSelectionChanged()
  {
    final Line.Info info = getSelectedLine();
    final String text = info != null ? info.toString() : "n/a";
    ((JTextPane)spLineDescription.getViewport().getView()).setText(text);
  }

  public Mixer.Info getSelectedMixer()
  {
    final MixerInfo info = (MixerInfo)cbMixerSelector.getSelectedItem();
    return info != null ? info.getInfo() : null;
  }

  public Line.Info getSelectedLine()
  {
    final LineInfo info = (LineInfo)cbLineSelector.getSelectedItem();
    return info != null ? info.getInfo() : null;
  }

  /**
   * @return True, if and only if the user closed the dialog by
   * clicking the select button.  In this case, the caller should
   * subsequently call methods <code>#getSelectedMixer()</code> and
   * <code>#getSelectedLine()</code> and apply the selected mixer and
   * line.
   */
  public boolean execute()
  {
    if (!rebuildMixerSelector()) {
      return false;
    }
    pack();
    opSelection.setValue(null);
    setVisible(true);
    final Object value = opSelection.getValue();
    final boolean result;
    if ((value == null) || (value == JOptionPane.UNINITIALIZED_VALUE)) {
      // window closed w/o option button pressed
      result = false;
    } else if (value == btPaneOptionSelect) {
      result = true;
      preferredMixerId = getSelectedMixer().toString();
      preferredLineId = getSelectedLine().toString();
    } else if (value == btPaneOptionCancel) {
      result = false;
    } else {
      throw new InternalError("unexpected option pane selection: " + value);
    }
    return result;
  }
}

/*
 * Local Variables:
 *   coding:utf-8
 *   mode:Java
 * End:
 */
