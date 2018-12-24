package emulator.vz200;

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;

import emulator.z80.WallClockProvider;

public class SourceDataLineControl extends Box
{
  private static final long serialVersionUID = -5069573981818146253L;

  private final String id;
  private final WallClockProvider wallClockProvider;
  private final List<LineControlListener> lineControlListeners;
  private final JLabel lbMixerId;
  private final JLabel lbLineId;
  private final JButton btChangeLine;
  private final SourceDataLineSelectionDialog dlLineSelection;

  public SourceDataLineControl(final String id,
                               final WallClockProvider wallClockProvider,
                               final String preferredMixerId,
                               final String preferredLineId,
                               final Frame owner)
  {
    super(BoxLayout.X_AXIS);
    if (id == null) {
      throw new NullPointerException("id");
    }
    this.id = id;
    if (wallClockProvider == null) {
      throw new NullPointerException("wallClockProvider");
    }
    this.wallClockProvider = wallClockProvider;
    lineControlListeners = new ArrayList<LineControlListener>();
    lbMixerId = new JLabel();
    lbLineId = new JLabel();
    btChangeLine = new JButton("Change");
    dlLineSelection =
      new SourceDataLineSelectionDialog(id,
                                        preferredMixerId, preferredLineId,
                                        owner);

    final Box bxSelectionLabels = new Box(BoxLayout.Y_AXIS);
    add(bxSelectionLabels);
    bxSelectionLabels.add(new JLabel("Mixer:"));
    bxSelectionLabels.add(new JLabel("Line:"));
    bxSelectionLabels.add(Box.createHorizontalStrut(5));
    add(Box.createHorizontalStrut(5));

    final Box bxSelectionValues = new Box(BoxLayout.Y_AXIS);
    add(bxSelectionValues);
    bxSelectionValues.add(lbMixerId);
    bxSelectionValues.add(lbLineId);
    bxSelectionValues.add(Box.createHorizontalStrut(5));
    add(Box.createHorizontalStrut(5));

    add(Box.createHorizontalGlue());

    final Box bxSelectionButtons = new Box(BoxLayout.Y_AXIS);
    add(bxSelectionButtons);
    btChangeLine.setMnemonic(KeyEvent.VK_C);
    btChangeLine.addActionListener((final ActionEvent event) ->
                                   { lineChanged(true); });
    bxSelectionButtons.add(btChangeLine);
    bxSelectionButtons.add(Box.createHorizontalStrut(5));
    add(Box.createHorizontalStrut(5));
  }

  public void addListener(final LineControlListener listener)
  {
    lineControlListeners.add(listener);
    listener.lineChanged(updateLabelsAndCreateLineChangeEvent());
  }

  public void removeListener(final LineControlListener listener)
  {
    lineControlListeners.remove(listener);
  }

  private void printMessage(final String message)
  {
    System.out.printf("%s: %s%n", id, message);
  }

  private void updateMixerLabel(final Mixer.Info mixerInfo)
  {
    final String mixerLabel;
    final String mixerToolTip;
    if (mixerInfo != null) {
      mixerLabel = mixerInfo.getName();
      mixerToolTip = mixerInfo.getDescription();
    } else {
      mixerLabel = "n/a";
      mixerToolTip = null;
    }
    lbMixerId.setText(mixerLabel);
    lbMixerId.setToolTipText(mixerToolTip);
  }

  private void updateLineLabel(final Line.Info lineInfo)
  {
    final String lineLabel;
    final String lineToolTip;
    if (lineInfo != null) {
      final Class<?> clazz = lineInfo.getLineClass();
      lineLabel = clazz.getSimpleName();
      lineToolTip = lineInfo.toString();
    } else {
      lineLabel = "n/a";
      lineToolTip = null;
    }
    lbLineId.setText(lineLabel);
    lbLineId.setToolTipText(lineToolTip);
  }

  private SourceDataLineChangeEvent
    createLineChangeEvent(final Mixer.Info mixerInfo,
                          final Line.Info lineInfo,
                          final long currentWallClockTime)
  {
    printMessage("using mixer: " + mixerInfo);
    printMessage("using line: " + lineInfo);
    final SourceDataLineChangeEvent event =
      new SourceDataLineChangeEvent(this, mixerInfo, lineInfo,
                                    currentWallClockTime);
    return event;
  }

  private SourceDataLineChangeEvent updateLabelsAndCreateLineChangeEvent()
  {
    final long currentWallClockTime = wallClockProvider.getWallClockTime();
    Mixer.Info mixerInfo = dlLineSelection.getSelectedMixer();
    Line.Info lineInfo = dlLineSelection.getSelectedLine();
    if (mixerInfo == null) {
      lineInfo = null;
    } else if (lineInfo == null) {
      mixerInfo = null;
    }
    updateMixerLabel(mixerInfo);
    updateLineLabel(lineInfo);
    final SourceDataLineChangeEvent event =
      createLineChangeEvent(mixerInfo, lineInfo, currentWallClockTime);
    return event;
  }

  private void lineChanged(final boolean execDialog)
  {
    printMessage("execDialog: " + execDialog);
    if (!execDialog || dlLineSelection.execute()) {
      final SourceDataLineChangeEvent event =
        updateLabelsAndCreateLineChangeEvent();
      for (final LineControlListener listener : lineControlListeners) {
        listener.lineChanged(event);
      }
    } else {
      // dialog aborted => nothing to do
    }
  }
}

/*
 * Local Variables:
 *   coding:utf-8
 *   mode:Java
 * End:
 */
