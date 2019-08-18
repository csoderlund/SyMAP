package util;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

public class CheckoutQueue {

	private List<Integer> counts;
	private List<Object> objects;

	private boolean stop;

	public CheckoutQueue() {
		counts  = new ArrayList<Integer>();
		objects = new ArrayList<Object>();
		stop    = false;
	}

	public CheckoutQueue(Object[] objs) {
		this();
		set(objs);
	}

	public CheckoutQueue(List<Object> contents) {
		this();
		set(contents);
	}

	private void set(Object[] objs) {
		set(Arrays.asList(objs));
	}

	private void set(List<Object> contents) {
		objects.clear();
		counts.clear();
		objects.addAll(contents);
		while (counts.size() < objects.size()) counts.add(new Integer(0));
	}

	public synchronized void stop() {
		stop = true;
	}

	public synchronized void clear() {
		counts.clear();
		objects.clear();
		notifyAll();
	}

	public synchronized void put(Object obj) {
		int ind = objects.indexOf(obj);
		if (ind < 0) {
			objects.add(obj);
			counts.add(new Integer(0));
		}
		else {
			counts.set(ind,decrease(counts.get(ind)));
			notifyAll();
		}	    
	}

	public synchronized Object get() {
		if (objects.isEmpty() || stop) return null;
		int min = ((Integer)counts.get(0)).intValue();
		int minIndex = 0;
		int c;
		for (int i = 1; i < counts.size(); i++) {
			c = ((Integer)counts.get(i)).intValue();
			if (c < min) {
				min = c;
				minIndex = i;
			}
		}
		counts.set(minIndex,increase(counts.get(minIndex)));
		return objects.get(minIndex);
	}

	public synchronized Object[] getContents() {
		return objects.toArray(new Object[0]);
	}

	public synchronized int[] getCounts() {
		return Utilities.getIntArray(counts);
	}

	public synchronized int getCount(Object obj) {
		int ind = objects.indexOf(obj);
		if (ind >= 0) ind = ((Integer)counts.get(ind)).intValue();
		return ind;
	}

	public synchronized boolean isCheckedOut(Object obj) {
		int ind = objects.indexOf(obj);
		if (ind >= 0) ind = ((Integer)counts.get(ind)).intValue();
		return ind > 0;
	}

	public synchronized boolean isCheckedOut() {
		for (int i = 0; i < counts.size(); i++)
			if (((Integer)counts.get(i)).intValue() > 0)
				return true;
		return false;
	}

	public void stopAndWait() throws InterruptedException {
		stop();
		waitTillFree();
	}

	public void waitTillFree() throws InterruptedException {
		while (isCheckedOut()) {
			synchronized (this) {
				wait(60000);
			}
		}
	}

	private Integer decrease(Object obj) {
		int i = ((Integer)obj).intValue();
		return new Integer(i > 0 ? i-1 : 0);
	}

	private Integer increase(Object obj) {
		return new Integer( ((Integer)obj).intValue() + 1 );
	}
}
