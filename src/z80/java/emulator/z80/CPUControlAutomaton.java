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

  public interface Listener
  {
    void stateChanged(final State state);
  }

  private final List<Listener> listeners;

  private final Object listenerLock = new Object();

  public void addListener(final Listener listener)
  {
    synchronized(listenerLock) {
      synchronized(setStateLock) {
        listeners.add(listener);
        listener.stateChanged(state);
      }
    }
  }

  public void removeListener(final Listener listener)
  {
    synchronized(listenerLock) {
      synchronized(setStateLock) {
        listeners.remove(listener);
      }
    }
  }

  public CPUControlAutomaton()
  {
    listeners = new ArrayList<Listener>();
    state = State.STOPPED;
  }

  private static final boolean DEBUG = false;

  private void printMessage(final String message)
  {
    if (DEBUG) {
      System.out.printf("CPUControlAutomaton: %s%n", message);
    }
  }

  private void stateChangeError(final State target)
  {
    throw new StateChangeError(state, target);
  }

  private final Object setStateLock = new Object();

  private void doSetState(final State state)
  {
    printMessage(String.format("state transition %s -> %s",
                               this.state, state));
    this.state = state;
    for (final Listener listener : listeners) {
      listener.stateChanged(state);
    }
  }

  public void setState(final State state)
  {
    synchronized(setStateLock) {
      switch (state) {
      case STOPPED:
        if (this.state != State.STOPPING) {
          stateChangeError(state);
        }
        break;
      case STARTING:
        if (this.state != State.STOPPED) {
          stateChangeError(state);
        }
        break;
      case RUNNING:
        if (this.state != State.STARTING) {
          stateChangeError(state);
        }
        break;
      case STOPPING:
        if (this.state != State.RUNNING) {
          stateChangeError(state);
        }
        break;
      default:
        throw new InternalError("unexpected state: " + state);
      }
      doSetState(state);
    }
  }

  public State getState()
  {
    return state;
  }

  private static class Awaiter implements Listener
  {
    private final State awaitState;
    private State listenedState;
    private boolean dirty;

    private Awaiter()
    {
      throw new UnsupportedOperationException("no empty constructor supported");
    }

    public Awaiter(final State awaitState)
    {
      this.awaitState = awaitState;
      listenedState = null;
      dirty = false;
    }

    public synchronized void stateChanged(final State listenedState)
    {
      this.listenedState = listenedState;
      notify();
    }

    public synchronized void await() {
      if (dirty) {
        throw new InternalError("to ensure unique notify/listening matching, AwaitThread can not be re-used");
      }
      dirty = true;
      while (true) {
        try {
          wait();
        } catch (final InterruptedException e) {
          // ignore
        }
        if (listenedState == awaitState) {
          break;
        }
      }
    }
  }

  public void awaitState(final State state)
  {
    final Awaiter awaiter = new Awaiter(state);
    addListener(awaiter);
    awaiter.await();
    removeListener(awaiter);
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
