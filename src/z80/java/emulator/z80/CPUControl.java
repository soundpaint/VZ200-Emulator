package emulator.z80;

import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class CPUControl implements CPUControlAPI, PreferencesChangeListener
{
  private final CPU cpu;
  private final CPU.Register regPC, regSP;
  private final CPU.Memory memory;
  private final CPU.Memory io;
  private final List<LogListener> logListeners;
  private final List<CPUControlAutomatonListener> stateChangeListeners;
  private final List<Class<?>> resourceLocations;
  private final ControlThread controlThread;
  private final StatisticsLogger statisticsLogger;
  private CPUControlAutomaton automaton;
  private boolean singleStep;
  private boolean trace;
  private Integer breakPoint;
  private boolean statisticsEnabled;

  /**
   * Turn off, if you want to get less CPU load.  Turn on, if you
   * require high precision in the point of time of CPU instruction
   * execution.
   */
  private boolean busyWait;

  private static final boolean DEBUG = false;

  private void printMessage(final String message)
  {
    if (DEBUG) {
      final Thread currentThread = Thread.currentThread();
      System.out.printf("CPUControl: %s: %s%n", currentThread, message);
    }
  }

  private void announceCPUStarted()
  {
    for (final LogListener listener : logListeners) {
      listener.announceCPUStarted();
    }
  }

  private void announceCPUStopped()
  {
    for (final LogListener listener : logListeners) {
      listener.announceCPUStopped();
    }
  }

  private void reportInvalidOp(final String message)
  {
    for (final LogListener listener : logListeners) {
      listener.reportInvalidOp(message);
    }
  }

  private void logOperation(final CPU.ConcreteOperation op)
  {
    for (final LogListener listener : logListeners) {
      listener.logOperation(op);
    }
  }

  private void logStatistics(final double avgSpeed,
                             final boolean busyWait,
                             final double jitter,
                             final double avgLoad)
  {
    for (final LogListener listener : logListeners) {
      listener.logStatistics(avgSpeed, busyWait, jitter, avgLoad);
    }
  }

  private class StatisticsLogger extends Thread
  {
    public StatisticsLogger()
    {
      super("Statistics Logger Thread");
    }

    public void run() {
      while (true) {
        try {
          Thread.sleep(1000);
        } catch (final InterruptedException e) {
          // ignore
        }
        if (statisticsEnabled) {
          logStatistics(cpu.getAvgSpeed(),
                        busyWait,
                        cpu.getAvgJitter(),
                        cpu.getAvgThreadLoad());
        }
      }
    }
  }

  private void cpuStopped()
  {
    for (final LogListener listener : logListeners) {
      listener.cpuStopped();
    }
  }

  public long getTimePerClockCycle()
  {
    return cpu.getTimePerClockCycle();
  }

  public long getWallClockCycles()
  {
    return cpu.getWallClockCycles();
  }

  public long getWallClockTime()
  {
    return cpu.getWallClockTime();
  }

  public CPU.Register[] getAllRegisters()
  {
    return cpu.getAllRegisters();
  }

  public int getPCValue()
  {
    return regPC.getValue();
  }

  public void setPCValue(final int value)
  {
    regPC.setValue(value);
  }

  public CPU.ConcreteOperation fetchNextOperationNoInterrupts()
    throws CPU.MismatchException
  {
    return cpu.fetchNextOperationNoInterrupts();
  }

  public Annotations getAnnotations()
  {
    return cpu.getAnnotations();
  }

  public void writeByteToMemory(final int address, final int dataByte)
  {
    memory.writeByte(address, dataByte, cpu.getWallClockCycles());
  }

  public int readByteFromMemory(final int address)
  {
     return memory.readByte(address, cpu.getWallClockCycles());
  }

  public void writeByteToPort(final int port, final int dataByte)
  {
    io.writeByte(port, dataByte, cpu.getWallClockCycles());
  }

  public int readByteFromPort(final int port)
  {
     return io.readByte(port, cpu.getWallClockCycles());
  }

  public void speedChanged(final int frequency)
  {
    // This callback is handled by CPU class (or its implementor).
    // Hence, do nothing here.
  }

  public void statisticsEnabledChanged(final boolean enabled)
  {
    statisticsEnabled = enabled;
  }

  public void busyWaitChanged(final boolean busyWait)
  {
    this.busyWait = busyWait;
  }

  public void addLogListener(final LogListener listener)
  {
    logListeners.add(listener);
  }

  public void
    addStateChangeListener(final CPUControlAutomatonListener listener)
  {
    automaton.addListener(listener);
  }

  public boolean
    removeStateChangeListener(final CPUControlAutomatonListener listener)
  {
    return automaton.removeListener(listener);
  }

  public void addResourceLocation(final Class<?> clazz)
  {
    resourceLocations.add(clazz);
  }

  public URL resolveLocation(final String path)
  {
    for (final Class<?> clazz : resourceLocations) {
      final URL url = clazz.getResource(path);
      if (url != null)
        return url;
    }
    return null;
  }

  public InputStream resolveStream(final String path) throws IOException
  {
    for (final Class<?> clazz : resourceLocations) {
      final InputStream stream = clazz.getResourceAsStream(path);
      if (stream != null)
        return stream;
    }
    return null;
  }

  private void execute()
  {
    long systemStartTime = 0;
    long systemStopTime = 0;
    long startCycle = cpu.getWallClockCycles();
    long idleTime = 0;
    long busyTime = 0;
    long jitter = 0;
    try {
      CPU.ConcreteOperation op = null;
      systemStartTime = System.nanoTime();
      long cpuStartTime = cpu.getWallClockTime();
      long deltaStartTime = cpuStartTime - systemStartTime;
      cpu.resyncPeripherals();
      acknowledgeStartCompleted();
      printMessage("CPUControl: Now running.");
      while (automaton.getState() == CPUControlAutomaton.State.RUNNING) {
        long systemTime = System.nanoTime();
        long cpuTime = cpu.getWallClockTime();
        jitter = systemTime - cpuTime + deltaStartTime;
        if (jitter > 0) {
          try {
            op = cpu.fetchNextOperation();
            if (trace) {
              logOperation(op);
            }
            op.execute();
          } catch (final CPU.MismatchException e) {
            reportInvalidOp(e.getMessage());
            breakPoint = regPC.getValue(); // stop executing
          }
          if (singleStep ||
              ((breakPoint != null) && (regPC.getValue() == breakPoint))) {
            requestStop();
          }
          busyTime += System.nanoTime() - systemTime;
        } else {
          if (busyWait) {
            while (System.nanoTime() - systemTime < 0);
          } else {
            try {
              Thread.sleep(1);
            } catch (final InterruptedException e) {
              // ignore
            }
          }
          idleTime += System.nanoTime() - systemTime;
        }
      }
      printMessage("CPUControl: No more running.");
      systemStopTime = System.nanoTime();
      if (!trace && (singleStep || (breakPoint != null))) {
        logOperation(op);
      }
    } catch (final Throwable t) {
      // unexpected exceptions
      printMessage("unexepected error: " + t.toString());
      t.printStackTrace(System.out);
      // depending on where exactly the unexpected exception occurred,
      // the automaton status may be either "running" or "stopping" =>
      // ensure to have it set to "stopping"
      if (automaton.getState() == CPUControlAutomaton.State.RUNNING) {
        requestStop();
      }
      acknowledgeStopCompleted();
      throw new InternalError(t.toString());
    }
    printMessage("CPUControl: Stopping.");
    final long stopCycle = cpu.getWallClockCycles();
    final double avgSpeed =
      1000.0 * (stopCycle - startCycle) / (systemStopTime - systemStartTime);
    final double avgLoad = busyTime / ((float)idleTime + busyTime);
    logStatistics(avgSpeed, busyWait, jitter, avgLoad);
    acknowledgeStopCompleted();
    cpuStopped();
  }

  private void setSingleStep(final boolean singleStep)
  {
    this.singleStep = singleStep;
  }

  private void setTrace(final boolean trace)
  {
    this.trace = trace;
  }

  public void setBreakPoint(final Integer breakPoint)
  {
    if (breakPoint == null) {
      this.breakPoint = null;
    } else {
      this.breakPoint = breakPoint & 0xffff;
    }
  }

  private void acknowledgeStartCompleted()
  {
    printMessage("acknowledgeStartCompleted()");
    automaton.setState(CPUControlAutomaton.State.RUNNING);
  }

  private void requestStart()
  {
    printMessage("requestStart()");
    automaton.setState(CPUControlAutomaton.State.STARTING);
  }

  private void awaitStartRequest()
  {
    printMessage("awaitStartRequest()...");
    automaton.awaitState(CPUControlAutomaton.State.STARTING);
    printMessage("awaitStartRequest() done");
  }

  /**
   * Waits until the CPU has been started.
   * Execution of this method is guarded by the locking mechanism.
   */

  private void awaitStart()
  {
    printMessage("awaitStart()...");
    automaton.awaitState(CPUControlAutomaton.State.RUNNING);
    printMessage("awaitStart() done");
  }

  public void start(final boolean singleStep, final boolean trace)
  {
    printMessage("start()...");
    automaton.runSynchronized(() -> {
        setSingleStep(singleStep);
        setTrace(trace);
        if (automaton.getState() != CPUControlAutomaton.State.STOPPED) {
          throw new InternalError("trying to start Monitor while it is not stopped");
        }
        requestStart();
        awaitStart();
      });
    printMessage("start() done");
  }

  private void requestStop()
  {
    printMessage("requestStop()");
    automaton.setState(CPUControlAutomaton.State.STOPPING);
  }

  private void awaitStopRequest()
  {
    printMessage("awaitStopRequest()...");
    automaton.awaitState(CPUControlAutomaton.State.STOPPING);
    printMessage("awaitStopRequest() done");
  }

  public void awaitStop()
  {
    printMessage("awaitStop()...");
    automaton.awaitState(CPUControlAutomaton.State.STOPPED);
    printMessage("awaitStop() done");
  }

  public boolean stop()
  {
    printMessage("stop()...");
    automaton.runSynchronized(() -> {
        /*
        if (automaton.getState() == CPUControlAutomaton.State.STOPPED) {
          return true;
        }
        if (automaton.getState() != CPUControlAutomaton.State.RUNNING) {
          throw new InternalError("trying to stop Monitor while it is not running");
        }
        */
        requestStop();
        awaitStop();
      });
    printMessage("stop() done");
    return false;
  }

  private void acknowledgeStopCompleted()
  {
    printMessage("acknowledgeStopCompleted()");
    automaton.setState(CPUControlAutomaton.State.STOPPED);
  }

  public void requestIRQ()
  {
    cpu.requestIRQ();
  }

  private class ControlThread extends Thread
  {
    public ControlThread()
    {
      super("CPU Control Thread");
    }

    public void run()
    {
      printMessage("CPU control thread: started");
      while (true) {
        awaitStartRequest();
        if (!singleStep) {
          announceCPUStarted();
        }
        execute();
        if (!singleStep) {
          announceCPUStopped();
        }
      }
    }
  }

  public void runSynchronized(final Runnable criticalSection)
  {
    automaton.runSynchronized(criticalSection);
  }

  private CPUControl()
  {
    throw new UnsupportedOperationException("unsupported empty constructor");
  }

  public CPUControl(final CPU cpu)
  {
    automaton = new CPUControlAutomaton();
    this.cpu = cpu;
    regPC = cpu.getProgramCounter();
    regSP = cpu.getStackPointer();
    memory = cpu.getMemory();
    io = cpu.getIO();
    setSingleStep(false);
    setTrace(false);
    setBreakPoint(null);
    logListeners = new ArrayList<LogListener>();
    stateChangeListeners = new ArrayList<CPUControlAutomatonListener>();
    resourceLocations = new ArrayList<Class<?>>();
    addResourceLocation(CPUControl.class);
    UserPreferences.getInstance().addListener(this);
    printMessage("CPU control thread: starting");
    controlThread = new ControlThread();
    controlThread.start();
    statisticsLogger = new StatisticsLogger();
    statisticsLogger.start();
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
