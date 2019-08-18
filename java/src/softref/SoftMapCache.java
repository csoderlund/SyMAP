package softref;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.Reference;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import util.MapCache;

/**
 * A SoftMapCache is a map implementation that only implements the get, put, and size methods.
 * 
 * A SoftMapCache uses SoftReferences and a deamon thread to clear references and remove them from the map
 * when the garbage collector deams it necessary.
 * 
 * The SoftMapCache is synchronized.
 * 
 * @author Austin Shoemaker
 */
public class SoftMapCache implements MapCache, SoftCollection {
    //private static final boolean DEBUG = false;
	
    private final Map<Object,Object> map;
   
    private final Vector<HardEntry> hardVector;
    private final int minSize, maxSize;
    private final ReferenceQueue queue;

    /**
     * Creates a new empty SoftMapCache.
     * 
     * The minimum size is used to hold the last <code>minSize</code> object from being garbage collected.
     * 
     * The maximum size is used to keep the map from getting too large.  Once the maximum size is reached,
     * the map is reduced down to just the minimum size (the entries that where last entered up to minimum size).
     * 
     * @param minSize The number of references that are not allowed to be cleared
     * @param maxSize The maximum size of the SoftMapCache
     */
    public SoftMapCache(int minSize, int maxSize) {
		this.minSize = minSize;
		this.maxSize = maxSize;
		map = new HashMap<Object,Object>();
		hardVector = new Vector<HardEntry>();
		queue = new ReferenceQueue();
		new RefRemover(this).start();
    }

    public ReferenceQueue getReferenceQueue() {
    	return queue;
    }
    
    public void removeReference(Reference ref) {
    	
    }
    
    /**
     * Method <code>size</code> returns the size of the map.
     *
     * @return an <code>int</code> value
     */
    public int size() {
		synchronized (map) {
		    return map.size();
		}
    }
    
    public void clear() {
    	synchronized (map) {
    		map.clear();
    	}
    }

    /**
     * Returns the value Object associated to key or null if it doesn't exist.
     * 
     * @param key The key for the value wanted
     * @return The value associated with key or null if not found
     */
    public Object get(Object key) {
    	synchronized (map) {
		    Object result = null;
		    Reference ref = (Reference)map.get(key);
		    if (ref != null) {
				result = ref.get();
				if (result == null) {
				    map.remove(key);
				}
		    }
		    return result;
		}
    }

    /**
     * Adds <code>value</code> to the SoftMapCache with key <code>key</code>.
     * 
     * If there is already an entry with the associated key <code>key</code> then no change occurs to 
     * the SoftMapCache and false is returned.
     * 
     * @param key
     * @param value
     * @return true is returned on entry, false is returned if the entry is already in the map
     */
    public boolean put(Object key, Object value) {
	synchronized (map) {
		    if (map.containsKey(key)) return false;
		    hardVector.add(0,new HardEntry(key,value));
		    if (hardVector.size() > minSize) hardVector.setSize(minSize);
		    map.put(key, new SoftEntry(value, key, queue));
		    if (map.size() > maxSize) {
				map.clear();
				for (HardEntry hv : hardVector)
				    map.put(key, new SoftEntry(hv.getValue(),hv.getKey(),queue));
		    }
		    return true;
		}
    }
}
