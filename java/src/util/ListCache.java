package util;

import java.util.List;

public interface ListCache {
    public void clear();
    public boolean contains(Object obj);
    public void add(Object obj);
    public boolean replace(Object obj);
    public Object get(Object obj);
    public Object peek(Object obj);
    public Object[] toArray();
    public Object[] toArray(Object a[]);
    public List getList();
}
