package util;

import java.util.List;
import java.util.Collection;
import java.util.LinkedList;
import java.util.ArrayList;

public class Queue {
	private static final boolean DEBUG = false;

	private String name;

	private boolean pause = false;
	private boolean stop  = false;
	private boolean drain = false;

	private LinkedList list;

	public Queue() {
		this("Queue");
	}

	public Queue(String name) {
		list = new LinkedList();
		this.name = name;
	}

	public synchronized void drain() {
		drain = true;
		notifyAll();
	}

	public synchronized void pause() {
		pause = true;
	}

	public synchronized void resume() {
		pause = false;
		notifyAll();
	}

	public synchronized List<?> contents() {
		return new ArrayList(list);
	}

	public synchronized void stop() {
		stop = true;
		notifyAll();
	}

	public synchronized List<?> clear() {
		List l = new ArrayList(list);
		list.clear();
		return l;
	}

	public synchronized int size() {
		return list.size();
	}

	public synchronized boolean isEmpty() {
		return list.isEmpty();
	}

	public synchronized void put(Object[] objects) {
		for (int i = 0; i < objects.length; i++) list.add(objects[i]);
		notifyAll();
	}

	public synchronized void put(Collection<?> collection) {
		list.addAll(collection);
		notifyAll();
	}

	public synchronized void put(Object object) {
		list.add(object);
		notifyAll();
	}

	public synchronized boolean putFirst(Object object) {
		boolean c = list.remove(object);
		list.addFirst(object);
		notifyAll();
		return c;
	}

	public synchronized Object remove(Object obj) {
		int ind = list.indexOf(obj);
		if (ind < 0) return null;
		Object o = list.get(ind);
		notifyAll();
		return o;
	}

	public synchronized Object get() {
		while (!stop && (list.isEmpty() || pause)) {
			if (drain && list.isEmpty()) return null;
			printDebug("Waiting");
			try {
				wait(60000);
			} catch (InterruptedException e) { }
		}
		return stop ? null : list.removeFirst();
	}

	private void printDebug(String message) {
		if (DEBUG) System.out.println(name+": "+message);
	}
}
