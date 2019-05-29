package emulator.vz200;

import java.awt.event.KeyEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;

import emulator.z80.CPU;
import emulator.z80.CPUControlAPI;
import emulator.z80.PreferencesChangeListener;
import emulator.z80.UserPreferences;

public class CPUStatistics extends Box
  implements PreferencesChangeListener, CPUControlAPI.LogListener
{
  private static final long serialVersionUID = -8929542516657150731L;

  private final CPUControlAPI cpuControl;
  private final JCheckBox cbEnable;
  private final JPanel pnStatistics;
  private final JLabel lbAvgSpeedLabel;
  private final JLabel lbAvgThreadLoadLabel;
  private final JLabel lbAvgJitterLabel;
  private final JLabel lbAvgSpeedValue;
  private final JLabel lbAvgThreadLoadValue;
  private final JLabel lbAvgJitterValue;
  private boolean statisticsEnabled;

  public CPUStatistics(final CPUControlAPI cpuControl)
  {
    super(BoxLayout.Y_AXIS);
    if (cpuControl == null) {
      throw new NullPointerException("cpuControl");
    }
    this.cpuControl = cpuControl;
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
    cpuControl.addLogListener(this);
    final UserPreferences userPreferences = UserPreferences.getInstance();
    userPreferences.addListener(this);
  }

  @Override
  public void announceCPUStarted()
  {
    // not relevant for us
  }

  @Override
  public void announceCPUStopped()
  {
    // not relevant for us
  }

  @Override
  public void reportInvalidOp(final String message)
  {
    // not relevant for us
  }

  @Override
  public void logOperation(final CPU.ConcreteOperation op)
  {
    // not relevant for us
  }

  @Override
  public void logStatistics()
  {
    // this view logs statistics immediately upon updating => nothing
    // to do here
  }

  @Override
  public void updateStatistics(final double avgSpeed,
                               final boolean busyWait,
                               final double jitter,
                               final double avgLoad)
  {
    if (statisticsEnabled) {
      lbAvgSpeedValue.setText("" + avgSpeed);
      lbAvgThreadLoadValue.setText("" + avgLoad);
      final double jitterMicroSeconds = jitter * 0.001;
      lbAvgJitterValue.setText("" + jitterMicroSeconds);
    }
  }

  @Override
  public void cpuStopped()
  {
    // not relevant for us
  }

  private void enableChanged()
  {
    final boolean statisticsEnabled = cbEnable.isSelected();
    final UserPreferences userPreferences = UserPreferences.getInstance();
    userPreferences.setStatisticsEnabled(statisticsEnabled);
  }

  @Override
  public void speedChanged(final int frequency)
  {
    // This callback is handled elsewhere.
    // Hence, do nothing here.
  }

  @Override
  public void statisticsEnabledChanged(final boolean statisticsEnabled)
  {
    this.statisticsEnabled = statisticsEnabled;
    cbEnable.setSelected(statisticsEnabled);
    pnStatistics.setEnabled(statisticsEnabled);
    lbAvgSpeedLabel.setEnabled(statisticsEnabled);
    lbAvgSpeedValue.setEnabled(statisticsEnabled);
    lbAvgThreadLoadLabel.setEnabled(statisticsEnabled);
    lbAvgThreadLoadValue.setEnabled(statisticsEnabled);
    lbAvgJitterLabel.setEnabled(statisticsEnabled);
    lbAvgJitterValue.setEnabled(statisticsEnabled);
  }

  @Override
  public void busyWaitChanged(final boolean busyWait)
  {
    // This callback is handled elsewhere.
    // Hence, do nothing here.
  }
}

/*
 * Local Variables:
 *   coding:utf-8
 *   mode:Java
 * End:
 */
