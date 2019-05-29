package emulator.z80;

import java.util.ArrayList;
import java.util.List;

public class CPUControlAutomaton
{
  public static enum State
  {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING
  }

  private State state;

  private static class StateChangeError extends InternalError
  {
    private static final long serialVersionUID = 7052051547796325979L;

    private StateChangeError()
    {
      throw new UnsupportedOperationException("unsupported empty constructor");
    }

    public StateChangeError(final State source, final State target)
    {
      super(createMessage(source, target));
    }

    private static String createMessage(final State source, final State target)
    {
      return String.format("illegal state change from state %s to state %s",
                           source, target);
    }
  }

  private final List<CPUControlAutomatonListener> listeners;

  private final Object listenerLock = new Object();
  private final Object stateLock = new Object();

  public void addListener(final CPUControlAutomatonListener listener)
  {
    synchronized(listenerLock) {
      synchronized(stateLock) {
        listeners.add(listener);
        listener.stateChanged(state);
      }
    }
  }

  public boolean removeListener(final CPUControlAutomatonListener listener)
  {
    synchronized(listenerLock) {
      synchronized(stateLock) {
        return listeners.remove(listener);
      }
    }
  }

  public CPUControlAutomaton()
  {
    listeners = new ArrayList<CPUControlAutomatonListener>();
    state = State.STOPPED;
  }

  private static final boolean DEBUG = false;

  private void printMessage(final String message)
  {
    if (DEBUG) {
      final Thread currentThread = Thread.currentThread();
      System.out.printf("CPUControlAutomaton: %s: %s%n",
                        currentThread, message);
    }
  }

  private StateChangeError stateChangeError(final State target,
                                            final boolean doTry)
  {
    final StateChangeError error = new StateChangeError(state, target);
    if (doTry) {
      return error;
    }
    throw error;
  }

  private StateChangeError checkIsValidTargetState(final State targetState,
                                                   final boolean doTry)
  {
    switch (targetState) {
    case STOPPED:
      if (state != State.STOPPING) {
        return stateChangeError(targetState, doTry);
      }
      break;
    case STARTING:
      if (state != State.STOPPED) {
        return stateChangeError(targetState, doTry);
      }
      break;
    case RUNNING:
      if (state != State.STARTING) {
        return stateChangeError(targetState, doTry);
      }
      break;
    case STOPPING:
      if (state != State.RUNNING) {
        return stateChangeError(targetState, doTry);
      }
      break;
    default:
      throw new InternalError("unexpected state: " + targetState);
    }
    return null;
  }

  public StateChangeError setState(final State targetState,
                                   final boolean doTry)
  {
    return setState(targetState, doTry, null);
  }

  /**
   * @return If the state transition is valid, this method returns
   * <code>null</code>.  If the new state is not a valid successor of
   * the current state and doTry is true, then, rather than throwing
   * an exception, the exception is returned.
   * @exception StateChangeError, if the new state is not a valid
   * successor of the current state and doTry is <code>false</code>.
   */
  public StateChangeError setState(final State targetState,
                                   final boolean doTry,
                                   final Runnable postWork)
  {
    printMessage("entering setState(), state=" + targetState);
    final StateChangeError error;
    synchronized(stateLock) {
      error = checkIsValidTargetState(targetState, doTry);
      if (error == null) {
        printMessage(String.format("state transition %s -> %s",
                                   state, targetState));
        state = targetState;
        if (postWork != null) {
          postWork.run();
        }
        for (final CPUControlAutomatonListener listener : listeners) {
          printMessage("setState(): notifying " + listener);
          listener.stateChanged(state);
        }
        stateLock.notifyAll();
      }
    }
    printMessage("leaving setState(), state=" + targetState);
    return error;
  }

  public State getState()
  {
    return state;
  }

  public void awaitState(final State targetState)
  {
    printMessage("entering awaitState(), state=" + targetState);
    synchronized(stateLock) {
      while (true) {
        if (targetState == state) {
          break;
        }
        try {
          stateLock.wait();
        } catch (final InterruptedException e) {
          // ignore
        }
      }
    }
    printMessage("leaving awaitState(), state=" + targetState);
  }

  public void runSynchronized(final Runnable criticalSection)
  {
    synchronized(stateLock) {
      criticalSection.run();
    }
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
