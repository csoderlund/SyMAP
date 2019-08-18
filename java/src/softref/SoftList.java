package softref;

import java.util.List;
import java.util.Collection;
import java.util.Iterator;

public interface SoftList extends SoftCollection {
    public Object getFirst();
    public Object getLast();
    public Object get(Object obj);
    public Object removeFirst();
    public Object removeLast();
    public void addFirst(Object obj);
    public void addLast(Object obj);
    public Object remove(Object obj);
    public void addAll(Collection c);
    public Object[] toArray();
    public Object[] toArray(Object a[]);
    public List getList();
    public Iterator iterator();
    public boolean contains(Object obj);
    public int size();
    public void clear();
}
