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

  private void stateChangeError(final State target)
  {
    throw new StateChangeError(state, target);
  }

  private void checkIsValidTargetState(final State targetState)
  {
    switch (targetState) {
    case STOPPED:
      if (state != State.STOPPING) {
        stateChangeError(targetState);
      }
      break;
    case STARTING:
      if (state != State.STOPPED) {
        stateChangeError(targetState);
      }
      break;
    case RUNNING:
      if (state != State.STARTING) {
        stateChangeError(targetState);
      }
      break;
    case STOPPING:
      if (state != State.RUNNING) {
        stateChangeError(targetState);
      }
      break;
    default:
      throw new InternalError("unexpected state: " + targetState);
    }
  }

  public void setState(final State targetState)
  {
    printMessage("entering setState(), state=" + targetState);
    synchronized(stateLock) {
      checkIsValidTargetState(targetState);
      printMessage(String.format("state transition %s -> %s",
                                 state, targetState));
      state = targetState;
      for (final CPUControlAutomatonListener listener : listeners) {
        printMessage("setState(): notifying " + listener);
        listener.stateChanged(state);
      }
      stateLock.notifyAll();
    }
    printMessage("leaving setState(), state=" + targetState);
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
