package finder;

public class FinderParam {

    private String name;
    private String displayName;
    private String desc;
    private ParamAcceptor pa;
    private Object def;

    public FinderParam(String name, String displayName, String description, long def, long min, long max) {
	set(name,displayName,description,new Long(def));
	pa = new LongParamAcceptor(min,max);
    }

    public FinderParam(String name, String displayName, String description, int def, int min, int max) {
	set(name,displayName,description,new Integer(def));
	pa = new IntegerParamAcceptor(min,max);
    }

    public FinderParam(String name, String displayName, String description, float def, float min, float max) {
	set(name,displayName,description,new Float(def));
	pa = new FloatParamAcceptor(min,max);
    }

    public FinderParam(String name, String displayName, String description, double def, double min, double max) {
	set(name,displayName,description,new Double(def));
	pa = new DoubleParamAcceptor(min,max);
    }

    public FinderParam(String name, String displayName, String description, Number def, Number min, Number max) {
	set(name,displayName,description,def);
	setParam(def,min,max);
    }

    public FinderParam(String name, String displayName, String description, Object def) {
	set(name,displayName,description,def);
	setParam(def);
    }

    public FinderParam(String name, String displayName, String description, Object def, ParamAcceptor paramAcceptor) {
	set(name,displayName,description,def);
	this.pa = paramAcceptor;
    }

    private void set(String name, String displayName, String desc, Object def) {
	this.name = name;
	this.displayName = displayName;
	this.desc = desc;
	this.def = def;
    }

    private void setParam(Number def, Number min, Number max) {
	if      (def instanceof Integer) pa = new IntegerParamAcceptor(min.intValue(),max.intValue());
	else if (def instanceof Long)    pa = new LongParamAcceptor(min.longValue(),max.longValue());
	else if (def instanceof Float)   pa = new FloatParamAcceptor(min.floatValue(),max.floatValue());
	else                             pa = new DoubleParamAcceptor(min.doubleValue(),max.doubleValue());
    }

    private void setParam(Object def) {
	if      (def instanceof Integer) pa = new IntegerParamAcceptor();
	else if (def instanceof Long)    pa = new LongParamAcceptor();
	else if (def instanceof Float)   pa = new FloatParamAcceptor();
	else if (def instanceof Number)  pa = new DoubleParamAcceptor();
	else if (def instanceof Boolean) pa = new BooleanParamAcceptor();
	else                             pa = new DefaultParamAcceptor();
    }

    public String getName() {
	return name;
    }

    public String getDisplayName() {
	return displayName;
    }

    public String getDescription() {
	return desc;
    }

    public Object getDefault() {
	return def;
    }

    public Class getParamClass() {
	return def.getClass();
    }

    public boolean isNumber() {
	return def instanceof Number;
    }
    
    public boolean isBoolean() {
	return def instanceof Boolean;
    }

    public String toString() {
	return name;
    }

    public ParamAcceptor getParamAcceptor() {
	return pa;
    }

    public boolean isValid(Object obj) {
	return pa == null ? obj != null : pa.isValid(obj);
    }

    public static class BooleanParamAcceptor implements ParamAcceptor {

	public BooleanParamAcceptor() { }

	public boolean isValid(Object obj) {
	    if (obj instanceof Boolean) return true;
	    if (obj instanceof String) {
		return obj.toString().equalsIgnoreCase("true") || obj.toString().equalsIgnoreCase("false");
	    }
	    else if (obj instanceof Number) {
		return ((Number)obj).intValue() == 0 || ((Number)obj).intValue() == 1;
	    }
	    return false;
	}
    }

    public static class IntegerParamAcceptor implements ParamAcceptor {
	private int min = Integer.MIN_VALUE;
	private int max = Integer.MAX_VALUE;

	public IntegerParamAcceptor() { }

	public IntegerParamAcceptor(int min, int max) {
	    this.min = min; this.max = max;
	}

	public boolean isValid(Object obj) {
	    if (obj == null) return false;
	    if (obj instanceof Number)
		return ((Number)obj).intValue() >= min && ((Number)obj).intValue() <= max;
	    else if (obj instanceof String) {
		try {
		    int i = Integer.parseInt((String)obj);
		    return i >= min && i <= max;
		} catch (Exception e) { }
	    }
	    return false;
	}

	public String toString() {
	    if (min == Integer.MIN_VALUE && max == Integer.MAX_VALUE) return "Property must be an integer";
	    if (min == Integer.MIN_VALUE) return "Property must be less than "+(max+1);
	    if (max == Integer.MAX_VALUE) return "Property must be greater than "+(min-1);
	    return "Property must be between "+(min-1)+" and "+(max+1);
	}
    }

    public static class LongParamAcceptor implements ParamAcceptor {
	private long min = Long.MIN_VALUE;
	private long max = Long.MAX_VALUE;

	public LongParamAcceptor() { }

	public LongParamAcceptor(long min, long max) {
	    this.min = min; this.max = max;
	}

	public boolean isValid(Object obj) {
	    if (obj == null) return false;
	    if (obj instanceof Number)
		return ((Number)obj).longValue() >= min && ((Number)obj).longValue() <= max;
	    else if (obj instanceof String) {
		try {
		    long i = Long.parseLong((String)obj);
		    return i >= min && i <= max;
		} catch (Exception e) { }
	    }
	    return false;
	}

	public String toString() {
	    if (min == Long.MIN_VALUE && max == Long.MAX_VALUE) return "Property must be a long";
	    if (min == Long.MIN_VALUE) return "Property must be less than "+(max+1);
	    if (max == Long.MAX_VALUE) return "Property must be greater than "+(min-1);
	    return "Property must be between "+(min-1)+" and "+(max+1);
	}
    }

    public static class FloatParamAcceptor implements ParamAcceptor {
	private float min = Float.NEGATIVE_INFINITY;
	private float max = Float.POSITIVE_INFINITY;

	public FloatParamAcceptor() { }

	public FloatParamAcceptor(float min, float max) {
	    this.min = min; this.max = max;
	}

	public boolean isValid(Object obj) {
	    if (obj == null) return false;
	    if (obj instanceof Number)
		return ((Number)obj).floatValue() >= min && ((Number)obj).floatValue() <= max;
	    else if (obj instanceof String) {
		try {
		    float i = Float.parseFloat((String)obj);
		    return i >= min && i <= max;
		} catch (Exception e) { }
	    }
	    return false;
	}

	public String toString() {
	    if (min == Float.NEGATIVE_INFINITY && max == Float.POSITIVE_INFINITY) return "Property must be a number";
	    if (min == Float.NEGATIVE_INFINITY) return "Property must be less than or equal to "+max;
	    if (max == Float.POSITIVE_INFINITY) return "Property must be greater than or equal to "+min;
	    return "Property must be between "+min+" and "+max;
	}
    }

    
    public static class DoubleParamAcceptor implements ParamAcceptor {
	private double min = Double.NEGATIVE_INFINITY;
	private double max = Double.POSITIVE_INFINITY;

	public DoubleParamAcceptor() { }

	public DoubleParamAcceptor(double min, double max) {
	    this.min = min; this.max = max;
	}

	public boolean isValid(Object obj) {
	    if (obj == null) return false;
	    if (obj instanceof Number)
		return ((Number)obj).doubleValue() >= min && ((Number)obj).doubleValue() <= max;
	    else if (obj instanceof String) {
		try {
		    double i = Double.parseDouble((String)obj);
		    return i >= min && i <= max;
		} catch (Exception e) { }
	    }
	    return false;
	}

	public String toString() {
	    if (min == Double.NEGATIVE_INFINITY && max == Double.POSITIVE_INFINITY) return "Property must be a number";
	    if (min == Double.NEGATIVE_INFINITY) return "Property must be less than or equal to "+max;
	    if (max == Double.POSITIVE_INFINITY) return "Property must be greater than or equal to "+min;
	    return "Property must be between "+min+" and "+max;
	}
    }

    private static class DefaultParamAcceptor implements ParamAcceptor {
	private DefaultParamAcceptor() { }
	
	public boolean isValid(Object obj) { 
	    return obj != null; 
	}
    }
}
