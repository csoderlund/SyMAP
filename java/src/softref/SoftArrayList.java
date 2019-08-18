package softref;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;
import java.util.Collection;
import java.util.Iterator;

public class SoftArrayList implements SoftList {

	private ArrayList list;
	private ReferenceQueue refQ;
	private ReferenceRemover refR;

	public SoftArrayList() {
		list = new ArrayList();
		refQ = new ReferenceQueue();
		refR = new RefRemover(this);
		new Thread(refR).start();
		check();
	}

	public SoftArrayList(SoftCollection sc) {
		list = new ArrayList();
		this.refQ = sc.getReferenceQueue();
		this.refR = new RefRemover(sc);
		new Thread(refR).start();
		check();
	}

	public SoftArrayList(ReferenceRemover refR, ReferenceQueue refQ) {
		list = new ArrayList();
		this.refQ = refQ;
		this.refR = refR;
		check();
	}

	public SoftArrayList(int initialCapacity) {
		list = new ArrayList(initialCapacity);
		refQ = new ReferenceQueue();
		refR = new RefRemover(this);
		new Thread(refR).start();
		check();
	}

	public SoftArrayList(int initialCapacity, SoftCollection sc) {
		list = new ArrayList(initialCapacity);
		this.refQ = sc.getReferenceQueue();
		this.refR = new RefRemover(sc);
		new Thread(refR).start();
		check();
	}

	public SoftArrayList(int initialCapacity, ReferenceRemover refR, ReferenceQueue refQ) {
		list = new ArrayList(initialCapacity);
		this.refQ = refQ;
		this.refR = refR;
		check();
	}

	private void check() {
		if (refR == null) throw new IllegalArgumentException("The Reference Remover must not be null.");
		if (refQ == null) throw new IllegalArgumentException("The Reference Queue must not be null.");
	}

	public Object getFirst() {
		Object obj = ((Reference)list.get(0)).get();
		while (obj == null) {
			refR.addRemovedReference(list.remove(0));
			obj = ((Reference)list.get(0)).get();
		}
		return obj;
	}

	public Object getLast() {
		Object obj = ((Reference)list.get(list.size()-1)).get();
		while (obj == null) {
			refR.addRemovedReference(list.remove(list.size()-1));
			obj = ((Reference)list.get(list.size()-1)).get();
		}
		return obj;
	}

	public Object get(Object obj) {
		if (obj == null) throw new IllegalArgumentException("Null values not allowed.");
		Iterator iter = list.iterator();
		Reference ref;
		Object t;
		while (iter.hasNext()) {
			ref = (Reference)iter.next();
			t = ref.get();
			if (t == null) {
				iter.remove();
				refR.addRemovedReference(ref);
			}
			else if (obj.equals(t)) {
				iter.remove();
				refR.addRemovedReference(ref);
				return t;
			}
		}
		return null;	
	}

	public Object removeFirst() {
		Reference ref = (Reference)list.remove(0);
		Object obj = ref.get();
		while (obj == null) {
			refR.addRemovedReference(ref);
			ref = (Reference)list.remove(0);
			obj = ref.get();
		}
		return obj;
	}

	public Object removeLast() {
		Reference ref = (Reference)list.remove(list.size()-1);
		Object obj = ref.get();
		while (obj == null) {
			refR.addRemovedReference(ref);
			ref = (Reference)list.remove(list.size()-1);
			obj = ref.get();
		}
		return obj;
	}

	public void addFirst(Object obj) {
		if (obj == null) throw new IllegalArgumentException("Null values not allowed.");
		list.add(0,new SoftReference(obj,refQ));
	}

	public void addLast(Object obj) {
		if (obj == null) throw new IllegalArgumentException("Null values not allowed.");
		list.add(new SoftReference(obj,refQ));
	}

	public void addAll(Collection c) {
		Iterator iter = c.iterator();
		while (iter.hasNext()) {
			addLast(iter.next());
		}
	}

	public Object remove(Object obj) {
		if (obj == null) throw new IllegalArgumentException("Null values not allowed.");
		Iterator iter = list.iterator();
		Reference ref;
		Object t;
		while (iter.hasNext()) {
			ref = (Reference)iter.next();
			t = ref.get();
			if (t == null) {
				iter.remove();
				refR.addRemovedReference(ref);
			}
			else if (obj.equals(t)) {
				iter.remove();
				refR.addRemovedReference(ref);
				return t;
			}
		}
		return null;
	}

	public Object[] toArray() {
		return getList().toArray();
	}

	public Object[] toArray(Object a[]) {
		return getList().toArray(a);
	}

	public List getList() {
		ArrayList l = new ArrayList(list.size());
		Iterator iter = list.iterator();
		Object t;
		Reference ref;
		while (iter.hasNext()) {
			ref = (Reference)iter.next();
			t = ref.get();
			if (t == null) {
				iter.remove();
				refR.addRemovedReference(ref);
			}
			else l.add(t);
		}
		l.trimToSize();
		return l;
	}

	public Iterator iterator() {
		return new SoftListIterator(list.iterator());
	}

	public boolean contains(Object obj) {
		if (obj == null) throw new IllegalArgumentException("Null values not allowed.");
		Iterator iter = list.iterator();
		Reference ref;
		Object t;
		while (iter.hasNext()) {
			ref = (Reference)iter.next();
			t = ref.get();
			if (t == null) {
				iter.remove();
				refR.addRemovedReference(ref);
			}
			else if (obj.equals(t)) return true;
		}
		return false;
	}

	public int size() {
		return list.size();
	}

	public void clear() {
		list.clear();
	}

	public void trimToSize() {
		list.trimToSize();
	}

	public ReferenceQueue getReferenceQueue() {
		return refQ;
	}

	public ReferenceRemover getReferenceRemover() {
		return refR;
	}

	public void removeReference(Reference ref) {
		if (ref != null) {
			Reference sr;
			Iterator iter = list.iterator();
			while (iter.hasNext()) {
				sr = (Reference)iter.next();
				if (ref == sr) {
					iter.remove();
				}
				else if (sr.get() == null) {
					iter.remove();
					refR.addRemovedReference(sr);
				}
			}
		}
	}

	protected class SoftListIterator implements Iterator {
		Iterator iter;
		Object next = null;
		Reference ref = null;

		protected SoftListIterator(Iterator iter) {
			this.iter = iter;
		}

		public boolean hasNext() {
			while (next == null && iter.hasNext()) {
				ref = (Reference)iter.next();
				next = ref.get();
				if (next == null) {
					iter.remove();
					refR.addRemovedReference(ref);
				}
			}
			return iter.hasNext();
		}

		public Object next() {
			if (next == null) hasNext();
			Object t = next;
			next = null;
			return t;
		}

		public void remove() {
			iter.remove();
			if (ref != null) refR.addRemovedReference(ref);
			next = null;
		}
	}
}
