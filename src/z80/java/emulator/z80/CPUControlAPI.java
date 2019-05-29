// TODO: move this class into new package 'emulator.monitor'.
package emulator.z80;

import java.io.InputStream;
import java.io.IOException;
import java.net.URL;

public interface CPUControlAPI extends WallClockProvider
{
  static interface LogListener
  {
    void announceCPUStarted();
    void announceCPUStopped();
    void reportInvalidOp(final String message);
    void logOperation(final CPU.ConcreteOperation op);
    void logStatistics();
    void updateStatistics(final double avgSpeed,
                          final boolean busyWait,
                          final double jitter,
                          final double avgLoad);
    void cpuStopped();
  }

  /**
   * Return an array with all registers of the CPU.
   */
  CPU.Register[] getAllRegisters();

  /**
   * Return value of CPU program counter.
   */
  int getPCValue();

  /**
   * Set value of CPU program counter.
   */
  void setPCValue(final int value);

  /**
   * Returns the annotations to retro-fit when displaying concrete CPU
   * instructions.
   */
  Annotations getAnnotations();

  /**
   * Write byte to memory via CPU bus.
   */
  void writeByteToMemory(final int address, final int dataByte);

  /**
   * Read byte from memory via CPU bus.
   */
  int readByteFromMemory(final int address);

  /**
   * Write byte to I/O port via CPU bus.
   */
  void writeByteToPort(final int port, final int dataByte);

  /**
   * Read byte from I/O port via CPU bus.
   */
  int readByteFromPort(final int port);

  /**
   * Add listener that retrieves events for logging purposes.
   * @param listener The listener to add.
   */
  void addLogListener(final LogListener listener);

  /**
   * Add listener that gets informed about changes of the
   * CPUControl's running state.
   * @param listener The listener to add.
   */
  void addStateChangeListener(final CPUControlAutomatonListener listener);

  /**
   * Try removing state change listener that has been previously
   * added.
   * @param listener The listener to remove.
   * @return <code>true</code> If the list of listeners contained the
   * specified listener.
   */
  boolean
    removeStateChangeListener(final CPUControlAutomatonListener listener);

  /**
   * Add the given resource's location path to the list of resource
   * location paths to search when loading data into to CPU's memory.
   * @param resource The resource whose location path to add.
   */
  void addResourceLocation(final Class<?> resource);

  /**
   * Resolve a path into a URL, based on the resource locations that
   * have been previously added.  The first match (in the order of
   * resource locations previously added) is returned, or
   * <code>null</code>, if the resource is not found.
   * @param path The path of resource to be resolved, relative to the
   * resource locations.
   * @return A URL pointing to the resource or <code>null</code> if
   * the resource could not be resolved.
   */
  URL resolveLocation(final String path);

  /**
   * Returns an input stream of the specified resource, resolving the
   * path according to the resource locations that have been
   * previously added.  An input stream associated with the first
   * match (in the order of resource locations previously added) is
   * returned, or <code>null</code>, if the resource is not found.
   * @param path The path of resource to be resolved, relative to the
   * resource locations.
   * @return An input stream providing read access to the resource or
   * <code>null</code> if the resource could not be resolved.
   */
  InputStream resolveStream(final String path) throws IOException;

  /**
   * Set a break point, causing the CPU to stop running if the
   * program counter advances to exactly the specified address.
   * Setting to null will clear the break point.
   *
   * @param breakPoint The address where to stop or null to
   * unset any break point.
   */
  void setBreakPoint(final Integer breakPoint);

  /**
   * Non-blocking, asynchronous request for starting execution of code
   * on the CPU.  Returns immediately after the start request has been
   * submitted.  Execution of this method is guarded by the locking
   * mechanism.
   *
   * @param singleStep True, if single step is to be activated.
   * False, if single step is to be deactivated.  If single step is
   * activated, starting the CPU will cause it to run only a single
   * instruction and after that immediately being stopped.
   *
   * @param trace True, if trace is to be activated.  False, if trace
   * is to be deactivated.  If trace is activated, starting the CPU
   * will cause it to run further actions like logging the current
   * processor registers after each instruction.
   * @return <code>Error</code>, if the CPU is already stopped.
   */
  Error start(final boolean singleStep, final boolean trace,
              final boolean doTry);

  /**
   * Waits until the CPU has been stopped.
   * Execution of this method is guarded by the locking mechanism.
   */
  void awaitStop();

  /**
   * Non-blocking, asynchronous request for stopping the CPU.  Returns
   * immediately, even when the CPU has not yet stopped.
   * Execution of this method is guarded by the locking mechanism.
   * @return <code>Error</code>, if the CPU is already stopped.
   */
  Error stop(final boolean doTry);

  /**
   * Run the given Runnable as critical section, guarded
   * by the CPUControlAutomaton's internal lock.
   * @param criticalSection The code to run as critical section
   * guarded by the CPUControlAutomaton's internal lock.
   */
  void runSynchronized(final Runnable criticalSection);
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
