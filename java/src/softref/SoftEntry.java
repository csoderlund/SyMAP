package softref;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Map;

class SoftEntry extends SoftReference implements Map.Entry {
    private Object key;
    private int hash;
    public SoftEntry(Object value, Object key, ReferenceQueue q) {
	super(value,q);
	this.key = key;
	hash = ((key == null) ? 0 : key.hashCode()) ^ ((value == null) ? 0 : value.hashCode());
    }

    public Object getKey() {
	return key;
    }

    public Object getValue() {
	return get();
    }

    public Object setValue(Object obj) {
    	throw new UnsupportedOperationException("SetValue not supported");
    }
    
    public int hashCode() {
	return hash;
    }

    public boolean equals(Object obj) {
	if (obj instanceof Map.Entry) {
	    Map.Entry e = (Map.Entry)obj;
	    return eq(key,e.getKey()) && eq(get(),e.getValue());
	}
	return false;
    }

    public String toString() {
	return key+"="+get();
    }

    private static boolean eq(Object o1, Object o2) {
	return (o1 == null ? o2 == null : o1.equals(o2));
    }
}
