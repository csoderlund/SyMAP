package util;

public interface MapCache {
    public int size();
    public void clear();
    public boolean put(Object key, Object value);
    public Object get(Object key);
}
