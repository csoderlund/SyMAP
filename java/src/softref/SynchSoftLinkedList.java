package softref;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.Reference;
import java.util.List;
import java.util.Collection;
import java.util.Iterator;

public class SynchSoftLinkedList implements SoftList {

    private SoftList list;
    private Object mutex;

    public SynchSoftLinkedList(SoftList list) {
	this.list = list;
	this.mutex = this;
    }

    public SynchSoftLinkedList(SoftList list, Object mutex) {
	this.list = list;
	this.mutex = mutex;
    }

    public Object getFirst() { 
	synchronized (mutex) {
	    return list.getFirst();
	}
    }

    public Object getLast() {
	synchronized (mutex) {
	    return list.getLast();
	}
    }

    public Object get(Object obj) {
	synchronized (mutex) {
	    return list.get(obj); 
	}
    }

    public Object removeFirst() {
	synchronized (mutex) {
	    return list.removeFirst();
	}
    }

    public Object removeLast() {
	synchronized (mutex) { 
	    return list.removeLast(); 
	}
    }

    public void addFirst(Object obj) {
	synchronized (mutex) {
	    list.addFirst(obj); 
	}
    }

    public void addLast(Object obj) {
	synchronized (mutex) {
	    list.addLast(obj);
	} 
    }

    public Object remove(Object obj) {
	synchronized (mutex) {
	    return list.remove(obj);
	} 
    }

    public void addAll(Collection c) {
	synchronized (mutex) {
	    list.addAll(c);
	}
    }

    public Object[] toArray() { 
	synchronized (mutex) { 
	    return list.toArray();
	} 
    }

    public Object[] toArray(Object a[]) {
	synchronized (mutex) {
	    return list.toArray(a);
	} 
    }

    public List getList() {
	synchronized (mutex) {
	    return list.getList();
	} 
    }

    public Iterator iterator() { 
	synchronized (mutex) {
	    return list.iterator();
	} 
    }

    public boolean contains(Object obj) {
	synchronized (mutex) {
	    return list.contains(obj);
	}
    }

    public int size() { 
	synchronized (mutex) { 
	    return list.size(); 
	} 
    }

    public void clear() {
	synchronized (mutex) {
	    list.clear();
	}
    }

    public void removeReference(Reference ref) {
	synchronized (mutex) {
	    list.removeReference(ref);
	}
    }

    public ReferenceQueue getReferenceQueue() {
	return list.getReferenceQueue();
    }
}
