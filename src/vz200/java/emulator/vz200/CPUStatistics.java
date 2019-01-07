package emulator.vz200;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;

import emulator.z80.CPU;
import emulator.z80.PreferencesChangeListener;

public class CPUStatistics extends Box
{
  private static final long serialVersionUID = -8929542516657150731L;

  private final List<PreferencesChangeListener> listeners;
  private final CPU cpu;
  private final JCheckBox cbEnable;
  private final JPanel pnStatistics;
  private final JLabel lbAvgSpeedLabel;
  private final JLabel lbAvgThreadLoadLabel;
  private final JLabel lbAvgJitterLabel;
  private final JLabel lbAvgSpeedValue;
  private final JLabel lbAvgThreadLoadValue;
  private final JLabel lbAvgJitterValue;
  private boolean enabled;

  public CPUStatistics(final CPU cpu,
                       final boolean initiallyEnabled)
  {
    super(BoxLayout.Y_AXIS);
    if (cpu == null) {
      throw new NullPointerException("cpu");
    }
    this.cpu = cpu;
    this.enabled = initiallyEnabled;
    listeners = new ArrayList<PreferencesChangeListener>();
    setBorder(BorderFactory.createTitledBorder("CPU Statistics"));

    lbAvgSpeedLabel = new JLabel("Actual avg. speed [Hz]:");
    lbAvgSpeedValue = new JLabel();
    lbAvgThreadLoadLabel = new JLabel("Avg. thread load [%]:");
    lbAvgThreadLoadValue = new JLabel();
    lbAvgJitterLabel = new JLabel("Avg. jitter [µs]:");
    lbAvgJitterValue = new JLabel();
    pnStatistics = new JPanel();

    final Box bxEnable = new Box(BoxLayout.X_AXIS);
    add(bxEnable);
    cbEnable = new JCheckBox("Enable statistics");
    bxEnable.add(cbEnable);
    cbEnable.setMnemonic(KeyEvent.VK_E);
    cbEnable.addChangeListener((final ChangeEvent event) -> {
        enableChanged();
      });
    cbEnable.setSelected(enabled);
    enableChanged();
    bxEnable.add(Box.createHorizontalGlue());
    add(Box.createVerticalStrut(5));

    pnStatistics.setLayout(new BoxLayout(pnStatistics, BoxLayout.X_AXIS));
    add(pnStatistics);

    final Box bxLabels = new Box(BoxLayout.Y_AXIS);
    pnStatistics.add(bxLabels);
    bxLabels.add(lbAvgSpeedLabel);
    bxLabels.add(lbAvgThreadLoadLabel);
    bxLabels.add(lbAvgJitterLabel);
    bxLabels.add(Box.createHorizontalStrut(5));
    pnStatistics.add(Box.createHorizontalStrut(5));

    final Box bxValues = new Box(BoxLayout.Y_AXIS);
    pnStatistics.add(bxValues);
    bxValues.add(lbAvgSpeedValue);
    bxValues.add(lbAvgThreadLoadValue);
    bxValues.add(lbAvgJitterValue);

    bxValues.add(Box.createHorizontalStrut(5));
    pnStatistics.add(Box.createHorizontalStrut(5));
    pnStatistics.add(Box.createHorizontalGlue());
    add(Box.createVerticalGlue());
    new Thread(new Runnable() {
        public void run() {
          while (true) {
            try {
              Thread.sleep(1000);
            } catch (final InterruptedException e) {
              // ignore
            }
            if (enabled) updateValues();
          }
        }
      }).start();
  }

  public void addListener(final PreferencesChangeListener listener)
  {
    listeners.add(listener);
    listener.statisticsEnabled(enabled);
  }

  public void removeListener(final PreferencesChangeListener listener)
  {
    listeners.remove(listener);
  }

  private void updateValues()
  {
    lbAvgSpeedValue.setText("" + cpu.getAvgSpeed());
    lbAvgThreadLoadValue.setText("" + cpu.getAvgThreadLoad());
    final double jitterMicroSeconds = cpu.getAvgJitter() * 0.001;
    lbAvgJitterValue.setText("" + jitterMicroSeconds);
  }

  private void enableChanged()
  {
    enabled = cbEnable.isSelected();
    pnStatistics.setEnabled(enabled);
    lbAvgSpeedLabel.setEnabled(enabled);
    lbAvgSpeedValue.setEnabled(enabled);
    lbAvgThreadLoadLabel.setEnabled(enabled);
    lbAvgThreadLoadValue.setEnabled(enabled);
    lbAvgJitterLabel.setEnabled(enabled);
    lbAvgJitterValue.setEnabled(enabled);
    for (final PreferencesChangeListener listener : listeners) {
      listener.statisticsEnabled(enabled);
    }
  }
}

/*
 * Local Variables:
 *   coding:utf-8
 *   mode:Java
 * End:
 */
