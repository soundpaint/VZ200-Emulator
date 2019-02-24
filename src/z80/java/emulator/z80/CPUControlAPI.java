// TODO: move this class into new package 'emulator.monitor'.
package emulator.z80;

import java.io.InputStream;
import java.io.IOException;
import java.net.URL;

public interface CPUControlAPI extends WallClockProvider
{
  static interface LogListener
  {
    void logInfo(final String message);
    void logWarn(final String message);
    void logError(final String message);
    void logOperation(final CPU.ConcreteOperation op);
    void logStatistics(final double avgSpeed,
                       final boolean busyWait,
                       final double jitter,
                       final double avgLoad);
    void cpuStopped();
  }

  static interface StateChangeListener
  {
    void stateChanged(final CPUControlAutomaton.State state);
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
   * Based on the current status of the CPU registers, fetch and
   * decode the next instruction and update the CPU's program counter
   * accordingly.  However, ignore any interrupts.
   *
   * This method is useful for displaying the next instruction but
   * without actually executing it.
   *
   * @return The decoded operation, ready for printing out in a
   * human-readable form.
   */
  CPU.ConcreteOperation fetchNextOperationNoInterrupts()
    throws CPU.MismatchException;

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
  void addStateChangeListener(final StateChangeListener listener);

  /**
   * Try removing state change listener that has been previously
   * added.
   * @param listener The listener to remove.
   * @return <code>true</code> If the list of listeners contained the
   * specified listener.
   */
  boolean removeStateChangeListener(final StateChangeListener listener);

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
   * Execute the specified code with a synchronization lock on the
   * CPUControl object instance.
   * @param runnable Implements the critical section.
   */
  void criticalSection(final Runnable runnable);

  /**
   * Get a new lock for starting a critical sequence of control
   * commands.  This method will block until there is no other thread
   * that holds any lock.  Once the new lock is returned, no other
   * thread will be able to perform any further control command until
   * the lock is released.
   * @TODO: Clarify: Can the same thread aquire two locks at the same
   * time?
   */
  Object aquireLock();

  /**
   * Release a lock that has been previously created by envoking the
   * #aquireLock() method.
   * @param lock The lock returned from a previous call to method
   * #aquireLock().
   * @exception IllegalArgumentException If \code{lock} is no lock
   * that has been previously created by envoking the #aquireLock()
   * method, or if this lock already has been released.
   */
  void releaseLock(final Object lock);

  /**
   * If single step is activated, starting the CPU will cause it to
   * run only a single instruction and after that immediately being
   * stopped.
   * @param singleStep True, if single step is to be activated.
   * False, if single step is to be deactivated.
   */
  void setSingleStep(final boolean singleStep);

  /**
   * If trace is activated, starting the CPU will cause it to
   * run further actions like logging the current processor
   * registers after each instruction.
   *
   * @TODO This method should be eliminated.  Instead, the monitor
   * should install a callback that is called by the CPU control after
   * each instruction execution, and within this callback, the monitor
   * itself should decide by itself if to print trace information, and
   * do it by itself if appropriate.
   *
   * @param trace True, if trace is to be activated.
   * False, if trace is to be deactivated.
   */
  void setTrace(final boolean trace);

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
   * Blocking, synchronous request for executing code on the CPU.
   * Returns only after the CPU has been stopped.  Execution of this
   * method is guarded by the locking mechanism.
   */
  void execute();

  /**
   * Waits until the CPU has been stopped.
   * Execution of this method is guarded by the locking mechanism.
   */
  void awaitStop();

  /**
   * Non-blocking, asynchronous request for stopping the CPU.  Returns
   * immediately, even when the CPU has not yet stopped.
   * Execution of this method is guarded by the locking mechanism.
   * @return <code>true</code>, if the CPU is already stopped.
   */
  boolean stop();
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
