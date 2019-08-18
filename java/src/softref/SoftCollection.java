package softref;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;

public interface SoftCollection {
    public void removeReference(Reference ref);
    public ReferenceQueue getReferenceQueue();
}
