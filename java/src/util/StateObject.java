package util;

import javax.swing.event.*;

public class StateObject implements State {
    private int state;
    private Object mutex;
    protected EventListenerList listenerList;

    public StateObject() {
	listenerList = new EventListenerList();
	state = NONE;
	mutex = new Object();
    }

    public void addChangeListener(ChangeListener listener) {
	listenerList.add(ChangeListener.class,listener);
    }

    public void removeChangeListener(ChangeListener listener) {
	listenerList.remove(ChangeListener.class,listener);
    }

    public int state() {
	return state;
    }

    public boolean hasState() {
	return state != NONE;
    }

    public boolean isStopped() {
	return state == STOPPED;
    }

    public boolean isStopping() {
	return state == STOPPING;
    }

    public boolean isPausing() {
	return state == PAUSING;
    }

    public boolean isPaused() {
	return state == PAUSED;
    }

    public boolean isActive() {
	return state == ACTIVE;
    }

    public boolean isDead() {
	return state == DEAD;
    }

    protected boolean setActive() {
	return state != DEAD ? set(ACTIVE) : false;
    }

    protected boolean setPaused(boolean pause) {
	if (pause)
	    return state == PAUSING ? set(PAUSED) : false;
	else
	    return state == PAUSED || state == PAUSING ? set(ACTIVE) : false;
    }

    protected boolean setStopped() {
	return state == STOPPING ? set(STOPPED) : false;
    }
    
    protected boolean setDone() {
	return state != DEAD ? set(STOPPED) : false;
    }

    protected boolean setDead() {
	return state != NONE ? set(DEAD) : false;
    }
    
    protected boolean setPausing() {
	return state == ACTIVE ? set(PAUSING) : false;
    }

    protected boolean setStopping() {
	return state == ACTIVE || state == PAUSED || state == PAUSING ? set(STOPPING) : false;
    }

    protected boolean set(int s) {
	if (mySetState(s)) {
	    fireStateChange();
	    return true;
	}
	return false;
    }

    private boolean mySetState(int s) {
	synchronized (mutex) {
	    if (state != s) {
		state = s;
		mutex.notifyAll();
		return true;
	    }
	    return false;
	}
    }

    protected void waitTillStopped() throws InterruptedException {
	synchronized (mutex) {
	    while (state != STOPPED && state != DEAD) {
		mutex.wait(60000);
	    }
	}
    }

    protected void waitTillPaused() throws InterruptedException {
	synchronized (mutex) {
	    while (state != PAUSED && state != STOPPED && state != DEAD) {
		mutex.wait(60000);
	    }
	}
    }

    protected void fireStateChange() {
	ChangeEvent ce = null;
	Object[] listeners = listenerList.getListenerList();
	for (int i = listeners.length-2; i>=0; i-=2) {
	    if (listeners[i]==ChangeListener.class) {
		if (ce == null)
		    ce = new ChangeEvent(this);
		if (isDead()) listenerList.remove(ChangeListener.class,(ChangeListener)listeners[i+1]);
		((ChangeListener)listeners[i+1]).stateChanged(ce);
	    }
	}
    }
}
