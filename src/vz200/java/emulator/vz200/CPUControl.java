package emulator.vz200;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;

import emulator.z80.CPU;
import emulator.z80.CPUControlAPI;

public class CPUControl extends JPanel
{
  private static final long serialVersionUID = 5541292559819662284L;

  public CPUControl(final CPUControlAPI cpuControl, final CPU cpu,
                    final JFrame owner)
  {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    add(new CPURunningStatusControl(cpuControl));
    add(new CPUSpeedControl(owner));
    add(new CPUBusyWait());
    add(new CPUStatistics(cpu));
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
