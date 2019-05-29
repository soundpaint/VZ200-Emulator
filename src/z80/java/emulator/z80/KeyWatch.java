package emulator.z80;

import java.io.IOException;
import java.io.PushbackInputStream;

public class KeyWatch extends Thread
{
  private static final boolean DEBUG = false;

  private final PushbackInputStream stdin;
  private final CPUControlAPI cpuControl;
  private boolean stopping;
  private boolean inputSeen;

  public KeyWatch(final PushbackInputStream stdin,
                  final CPUControlAPI cpuControl)
  {
    super("GraphicalTerminal KeyWatch Thread");
    if (stdin == null) {
      throw new NullPointerException("stdin");
    }
    if (cpuControl == null) {
      throw new NullPointerException("cpuControl");
    }
    this.stdin = stdin;
    this.cpuControl = cpuControl;
  }

  private void log(final String message)
  {
    final Thread currentThread = Thread.currentThread();
    System.out.println(String.format("%s: %s", currentThread, message));
  }

  private void logDebug(final String message)
  {
    if (DEBUG) {
      log(message);
    }
  }

  private void logError(final String message)
  {
    log(message);
  }

  public boolean inputSeen() throws IOException
  {
    inputSeen |= stdin.available() > 0;
    logDebug("KeyWatch: inputSeen=" + inputSeen);
    return inputSeen;
  }

  public void run()
  {
    boolean haveException = false;
    inputSeen = false;
    try {
      synchronized(this) {
        while (!stopping && !inputSeen()) {
          try {
            wait(1000);
          } catch (final InterruptedException e) {
            // ignore
          }
        }
      }
    } catch (final IOException e) {
      haveException = true;
      /**
       * if checking for kbd input throws an exception, there is a
       * fundamental problem.  In this case, we log this error and
       * prematurely stop watching for keys.
       */
      logError("failed watching for keyboard actions, " +
               "stopping key watch thread: " + e.getMessage());
    }
    if (!stopping) {
      final Error error = cpuControl.stop(true);
      if (error != null) {
        logError(error.getMessage());
      }
    }
  }

  public void stateChanged(final CPUControlAutomaton.State state)
  {
    synchronized(this) {
      logDebug("KeyWatch: state=" + state);
      if (state == CPUControlAutomaton.State.STOPPING) {
        stopping = true;
      }
      notify();
    }
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
