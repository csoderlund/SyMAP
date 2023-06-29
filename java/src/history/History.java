package history;

import java.util.Vector;

/**
 * Class History stores a history list with a current pointer.
 * If it goes Home or back, then a new one is added, all remaining are lost but not freed.
 */
public class History {
	private static final int HISTORY_SIZE = 50; // CAS542 was 10, moved constant from SyMAP2d
	
	private Vector<HistoryObject> history;
	private int index;
	private int size = HISTORY_SIZE;

	public History() { 
		index = -1;
		history = new Vector<HistoryObject>();
	}

	public synchronized void clear() {
		index = -1;
		history.clear();
	}

	public synchronized Object goHome() { 
		if (isHome()) {
			index = 0;
			return ((HistoryObject)history.get(index)).getObject();
		}
		return null;
	}
	public synchronized Object goBack() {
		if (isBack()) {
			--index;
			return ((HistoryObject)history.get(index)).getObject();
		}
		return null;
	}

	public synchronized Object goForward() {
		if (isForward()) {
			++index;
			return ((HistoryObject)history.get(index)).getObject();
		}
		return null;
	}
	/**
	 * Method goReset updates the history to the most recent reset point going back and
	 * returns the object at that point. The first position is considered a reset point if none are found.
	 */
	public synchronized Object goReset() {
		if (isReset()) {
			for (int i = index-1; i >= 0; i--) {
				HistoryObject ho = (HistoryObject)history.get(i);
				if ( ho.isResetPoint() ) {
					index = i;
					return ho.getObject();
				}
			}
			return goHome();
		}
		return null;
	}

	/**
	 * Method goDBack updates the history going to the point before the most recent reset point
	 * going back and returns the object at that point. Never used?
	 */
	public synchronized Object goDBack() { 
		int i;
		for (i = index; i >= 0; i--)
			if ( ((HistoryObject)history.get(i)).isResetPoint() ) break;
		if (i > 0) {
			index = i-1;
			return ((HistoryObject)history.get(index)).getObject();
		}
		return null;
	}

	public synchronized boolean isHome() {return index > 0;}

	 // Method isReset returns true if the current point is not a reset point or the first position.	 
	public synchronized boolean isReset() {
		return index > 0 && !((HistoryObject)history.get(index)).isResetPoint();
	}

	// Method isDBack returns true if there is a point before the most recent reset point going back.
	public synchronized boolean isDBack() {
		int i;
		for (i = index; i >= 0; i--)
			if ( ((HistoryObject)history.get(i)).isResetPoint() ) break;
		return i > 0;
	}

	public synchronized boolean isBack() {return index > 0;}

	public synchronized boolean isForward() {return history.size() > index+1;}

	public synchronized boolean isEmpty() {return history.isEmpty();}

	/**
	 * Method adds the object to the history setting it to be
	 * a reset point if reset is true. The first position is removed from
	 * history if the addition of obj makes the history larger than the capacity
	 * set at instantiation.
	 */
	public synchronized void add(Object obj, boolean reset) {
		HistoryObject ho = new HistoryObject(obj,reset);
		history.setSize(++index);
		history.add(ho);
		if (history.size() > size) {
			history.remove(0);
			index--;
		}
	}

	// Method get returns the object at the current point or null if there are no history objects.
	public synchronized Object get() {
		if (index < 0) return null;
		return ((HistoryObject)history.get(index)).getObject();
	}
}
