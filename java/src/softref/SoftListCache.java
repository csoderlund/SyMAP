package softref;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.Reference;
import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import util.ListCache;

/**
 * A ListCache uses a list of hard links a and a list of soft links to maintain a synchronized cache.
 * 
 * @author Austin Shoemaker
 * @see SoftList
 */
public class SoftListCache implements ListCache {
    private static final boolean DEBUG = false;
	
    private LinkedList hardList;
    private SoftList softList;
    private int maxHard, maxSoft;

    /**
     * Creates a new empty SoftListCache instance.
     * 
     * The minimum size is used to hold the last <code>minSize</code> object accessed from being garbage collected.
     * 
     * The maximum size is used to keep the SoftListCache from getting too large.  Once the maximimum size is reached,
     * the oldest entry (least recently accessed object) is removed.
     * 
     * Accessed meaning that the object was returned from get or passed as the argument to add.
     * 
     * @param minSize The number of references that are not allowed to be cleared
     * @param maxSize The maximum number of references.
     */
    public SoftListCache(int minSize, int maxSize) {
	maxHard = minSize;
	maxSoft = maxSize-minSize;
	softList = new SoftLinkedList(new SynchSoftCollection(new ReferenceQueue()));
	hardList = new LinkedList();
    }

    public synchronized void clear() {
	if (DEBUG) System.out.println("Clearing the SoftListCache.");
	softList.clear();
	hardList.clear();
    }

    public synchronized boolean contains(Object obj) {
	return hardList.contains(obj) || softList.contains(obj);
    }
    
    /**
     * Adds obj to the SoftListCache make it the lowest priority for garbage collection.
     * Until x where x is the minimum size of the SoftListCache objects, are accessed, obj
     * will not be garbage collected.
     *
     * If the object is already in the lists, the old object is removed.
     *
     * @param obj Object to add
     */
    public synchronized void add(Object obj) {
	if (obj == null) throw new IllegalArgumentException("Null Values not supported");

	hardList.remove(obj);
	softList.remove(obj);

	Object sobj = null;
	if (maxHard > 0) {
	    if (hardList.size() >= maxHard) sobj = hardList.removeLast();
	    hardList.addFirst(obj);
	}
	else sobj = obj;

	if (maxSoft > 0 && sobj != null) {
	    if (softList.size() >= maxSoft) softList.removeLast();
	    softList.addFirst(sobj);
	}
    }

    /**
     * Returns the Object that equals <code>obj</code> determinded by the equals method.
     * If the object is in the list, the object is given the lowest priority to be garbage
     * collected.  It will not be garbage collected till the minimum size objects are accessed.
     * 
     * @param obj
     * @return The object in the list or null if it's not found
     */
    public synchronized Object get(Object obj) {
	if (obj == null) return null;

	Object ret = null, temp;
	Iterator iter = hardList.iterator();
	while (iter.hasNext()) {
	    temp = iter.next();
	    if (obj.equals(temp)) {
		iter.remove();
		ret = temp;
		break;
	    }
	}
	if (ret == null) ret = softList.remove(obj);
	if (ret != null) add(ret);
	return ret;
    }

    public synchronized Object peek(Object obj) {
	if (obj == null) return null;

	Object ret = null, temp;
	Iterator iter = hardList.iterator();
	while (iter.hasNext()) {
	    if (obj.equals( (temp = iter.next()) )) {
		ret = temp;
		break;
	    }
	}
	if (ret == null) ret = softList.get(obj);
	return ret;	
    }

    public synchronized Object[] toArray() {
	Object[] sa = softList.toArray();
	Object[] ha = hardList.toArray();
	Object[] ra = new Object[ha.length+sa.length];
	System.arraycopy(ha,0,ra,0,ha.length);
	System.arraycopy(sa,0,ra,ha.length,sa.length);
	return ra;
    }
    public synchronized Object[] toArray(Object ra[]) {
	Object[] sa = softList.toArray((Object[])java.lang.reflect.Array.newInstance(ra.getClass().getComponentType(),0));
	Object[] ha = hardList.toArray((Object[])java.lang.reflect.Array.newInstance(ra.getClass().getComponentType(),0));
	if (ra.length < sa.length + ha.length)
	    ra = (Object[])java.lang.reflect.Array.newInstance(ra.getClass().getComponentType(),sa.length+ha.length);
	System.arraycopy(ha,0,ra,0,ha.length);
	System.arraycopy(sa,0,ra,ha.length,sa.length);
	return ra;
    }

    public synchronized List getList() {
	List list = softList.getList();
	List ret = new ArrayList(hardList.size()+list.size());
	ret.addAll(hardList);
	ret.addAll(list);
	return ret;
    }

    public synchronized boolean replace(Object obj) {
	int ind = hardList.indexOf(obj);
	if (ind >= 0) hardList.set(ind,obj);
	else if (softList.remove(obj) != null) softList.addFirst(obj);
	else return false;
	return true;
    }

    private synchronized void removeRef(Reference ref) {
	if (DEBUG) System.out.println("Removing from SoftListCache "+ref);
	softList.removeReference(ref);
    }

    private class SynchSoftCollection implements SoftCollection {
	
	private ReferenceQueue refQ;

	SynchSoftCollection(ReferenceQueue refQ) {
	    this.refQ = refQ;
	}

	public ReferenceQueue getReferenceQueue() {
	    return refQ;
	}

	public void removeReference(Reference ref) {
	    removeRef(ref);
	}
    }
}
