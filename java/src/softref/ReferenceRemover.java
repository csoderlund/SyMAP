package softref;

public interface ReferenceRemover extends Runnable {
    public void addRemovedReference(Object ref);
}

