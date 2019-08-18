package util;

import java.util.Vector;
import java.util.Collection;

/**
 * Class <code>ClearList</code> provides a list that will
 * call the clear() method in the object being removed.
 * All objects added must implements ClearList.Clearer.
 *
 * The methods implemented here work the same as in the Vector class only
 * calling the clear() method anytime an object is removed or overwritten
 * by an inserted object.
 *
 * Like the Vector class, ClearList is synchronized.
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class ClearList extends Vector {
	public ClearList(int initialCapacity, int capacityIncrement) {
		super(initialCapacity,capacityIncrement);
	}

	public ClearList(int initialCapacity) {
		this(initialCapacity,0);
	}

	public ClearList() {
		this(10);
	}

	public ClearList(Collection c) {
		super(c);
	}

	public synchronized void setSize(int newSize) {
		for (int remove = size() - newSize; remove > 0; remove--) {
			((Clearer)remove(size()-1)).clear();
		}
		super.setSize(newSize);
	}

	public synchronized void setElementAt(Object obj, int index) {
		set(index,obj);
	}

	public synchronized void removeElementAt(int index) {
		remove(index);
	}

	public synchronized void removeAllElements() {
		while (!isEmpty())
			remove(size()-1);
	}

	public void clear() {
		removeAllElements();
	}

	public synchronized Object set(int index, Object element) {
		Clearer c = (Clearer)super.set(index,element);
		if (c != null) c.clear();
		return c;
	}

	public synchronized boolean remove(Object o) {
		int ind = indexOf(o);
		if (ind >= 0 && get(ind) != null)
			((Clearer)get(ind)).clear();
		return super.remove(o);
	}

	public synchronized Object remove(int index) {
		Clearer c = (Clearer)super.remove(index);
		if (c != null) c.clear();
		return c;
	}

	protected void removeRange(int fromIndex, int toIndex) {
		for (int i = fromIndex; i < toIndex; i++) ((Clearer)get(i)).clear();
		super.removeRange(fromIndex,toIndex);
	}

	public static interface Clearer {
		public void clear();
	}
}
