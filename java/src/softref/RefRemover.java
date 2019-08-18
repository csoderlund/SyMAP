package softref;

import java.util.LinkedList;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;

public class RefRemover extends Thread implements ReferenceRemover {

    private SoftCollection sc;
    private LinkedList deadReferences;

    public RefRemover(SoftCollection s) {
	super();
	setDaemon(true);
	this.sc = s;
	deadReferences = new LinkedList();
    }

    public void addRemovedReference(Object ref) {
	synchronized (deadReferences) {
	    deadReferences.addLast(ref);
	}
    }

    private boolean removeDead(Reference ref) {
	synchronized (deadReferences) {
	    return deadReferences.remove(ref);
	}
    }

    public void run() {
	Reference ref;
	ReferenceQueue refQ = sc.getReferenceQueue();
	while (true) {
	    try {
		ref = refQ.remove();
		if (!removeDead(ref)) sc.removeReference(ref);
	    } catch (InterruptedException e) { }
	}
    }
}
