package history;

import java.util.Vector;


/**
 * Class <code>History</code> stores a history list
 * with a current pointer.
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 */
public class History {

	private Vector<HistoryObject> history;
	private int index;
	private int size;

	/**
	 * Creates a new <code>History</code> instance.
	 *
	 * @param capacity an <code>int</code> value of the maximum size the history can become.
	 */
	public History(int capacity) {
		size = capacity;
		index = -1;
		history = new Vector<HistoryObject>();
	}

	/**
	 * Method <code>clear</code> clears the history.
	 *
	 */
	public synchronized void clear() {
		index = -1;
		history.clear();
	}

	/**
	 * Method <code>goHome</code> updates the history to the first position and returns
	 * the object at that point.
	 *
	 * @return an <code>Object</code> value of the object at the new point or null if not applicable
	 */
	public synchronized Object goHome() {
		if (isHome()) {
			index = 0;
			return ((HistoryObject)history.get(index)).getObject();
		}
		return null;
	}

	/**
	 * Method <code>goReset</code> updates the history to the most recent reset point going back and
	 * returns the object at that point. The first position is considered a reset point if none are found.
	 *
	 * @return an <code>Object</code> value of the object at the new point or null if isReset() is true
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
	 * Method <code>goDBack</code> updates the history going to the point before the most recent reset point
	 * going back and returns the object at that point. 
	 *
	 * @return an <code>Object</code> value of object at the new point or null if not applicable
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

	/**
	 * Method <code>goBack</code> updates the history going back one position in history
	 *
	 * @return an <code>Object</code> value of object at the new point or null if not applicable
	 */
	public synchronized Object goBack() {
		if (isBack()) {
			--index;
			return ((HistoryObject)history.get(index)).getObject();
		}
		return null;
	}

	/**
	 * Method <code>goForward</code> updates the history going forward one position in history
	 *
	 * @return an <code>Object</code> value of object at the new point or null if not applicable
	 */
	public synchronized Object goForward() {
		if (isForward()) {
			++index;
			return ((HistoryObject)history.get(index)).getObject();
		}
		return null;
	}

	/**
	 * Method <code>isHome</code> returns true if there is a home point that isn't the current point
	 *
	 * @return a <code>boolean</code> value
	 */
	public synchronized boolean isHome() {
		return index > 0;
	}

	/**
	 * Method <code>isReset</code> returns true if the current point is not a reset point
	 * or the first position.
	 *
	 * @return a <code>boolean</code> value
	 */
	public synchronized boolean isReset() {
		return index > 0 && !((HistoryObject)history.get(index)).isResetPoint();
	}

	/**
	 * Method <code>isDBack</code> returns true if there is a point before the most recent reset point
	 * going back.
	 *
	 * @return a <code>boolean</code> value
	 */
	public synchronized boolean isDBack() {
		int i;
		for (i = index; i >= 0; i--)
			if ( ((HistoryObject)history.get(i)).isResetPoint() ) break;
		return i > 0;
	}

	/**
	 * Method <code>isBack</code> returns true if the current position is not the first
	 * position in the history.
	 *
	 * @return a <code>boolean</code> value
	 */
	public synchronized boolean isBack() {
		return index > 0;
	}

	/**
	 * Method <code>isForward</code> returns true if the current position is not the last
	 * position in the history.
	 *
	 * @return a <code>boolean</code> value
	 */
	public synchronized boolean isForward() {
		return history.size() > index+1;
	}

	/**
	 * Method <code>isEmpty</code> returns true if the history is empty (no Objects have been added).
	 *
	 * @return a <code>boolean</code> value
	 */
	public synchronized boolean isEmpty() {
		return history.isEmpty();
	}

	/**
	 * Method <code>add</code> adds the object to the history setting it to be
	 * a reset point if reset is true. The first position is removed from
	 * history if the addition of obj makes the history larger than the capacity
	 * set at instantiation.
	 * 
	 * @param obj		an <code>Object</code> value
	 * @param reset		a <code>boolean</code> value
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

	/**
	 * Method <code>get</code> returns the object at the current point or null
	 * if there are no history objects.
	 *
	 * @return an <code>Object</code> value
	 */
	public synchronized Object get() {
		if (index < 0) return null;
		return ((HistoryObject)history.get(index)).getObject();
	}
}
