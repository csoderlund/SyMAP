package symap.pool;

import java.util.List;

import util.ListCache;
import softref.SoftListCache;
import symap.block.ContigData;
import symap.contig.ContigCloneData;

/**
 * Class <code>BlockContigCache</code> handles the median between the contig and contig clone pools
 * and the actual caches which contain the data.
 *
 * It is important to note that the get(Object) method may return an object of a differenct type than
 * was asked for.  This issue only becomes important when the argument is a ContigCloneData object in which case a ContigData
 * object may be returned if no ContigCloneData object exists in the cache.
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 * @see ListCache
 */
public class BlockContigCache implements ListCache {

	private ListCache bp;
	private ListCache cp;

	public BlockContigCache(int minContig, int maxContig, int minContigClone, int maxContigClone) {
		bp = new SoftListCache(minContig,maxContig);
		cp = new SoftListCache(minContigClone,maxContigClone);
	}

	public synchronized void clear() {
		bp.clear();
		cp.clear();
	}

	public synchronized boolean contains(Object obj) {
		return bp.contains(obj) || cp.contains(obj);
	}

	public synchronized void add(Object obj) {
		if (obj instanceof ContigCloneData) {
			bp.replace(obj);
			cp.add(obj);
		}
		else if (obj instanceof ContigData) {
			Object o = cp.peek(obj);
			if (o != null) bp.add(o);
			else bp.add(obj);
		}
		else {
			throw new IllegalArgumentException("Argument of type "+obj.getClass().getName()+" not allowed.");
		}
	}

	public synchronized Object peek(Object obj) {
		Object o = cp.peek(obj);
		if (o != null) o = bp.peek(obj);
		return o;
	}

	public synchronized boolean replace(Object obj) {
		if (obj instanceof ContigCloneData) {
			bp.replace(obj);
			return cp.replace(obj);
		}
		else if (obj instanceof ContigData) {
			Object o = cp.peek(obj);
			if (o != null) return bp.replace(o);
			else return bp.replace(obj);
		}
		throw new IllegalArgumentException("Argument of type "+obj.getClass().getName()+" not allowed.");
	}

	public synchronized Object get(Object obj) {
		if (obj instanceof ContigCloneData) {
			Object o = cp.get(obj);
			if (o == null) o = bp.peek(obj);
			return o;
		}
		if (obj instanceof ContigData) {
			Object o = bp.get(obj);
			if (o == null) {
				o = cp.peek(obj);
				if (o != null) bp.add(o);
			}
			return o;
		}
		throw new IllegalArgumentException("Argument of type "+obj.getClass().getName()+" not allowed.");
	}

	public List getList() {
		List ret = bp.getList();
		ret.addAll(cp.getList());
		return ret;
	}

	public Object[] toArray() {
		return getList().toArray();
	}

	public Object[] toArray(Object[] a) {
		return getList().toArray(a);
	}
}
