package finder;

import util.PropertiesReader;

/**
 * Class ParamHolder lays out the foundation for a ParamHolder such as a BlockFinder.
 *
 * All of the non final methods may need to be overrided for new params, also calling the super
 * method since ParamHolder takes care of the debug parameter.
 */
public abstract class ParamHolder {
    private static final boolean DEFAULT_DEBUG = false;

    protected final PropertiesReader props;
    protected boolean debug;
    private FinderParam[] params;

    /**
     * Creates a new ParamHolder instance. with the given props file.
     * Note that setDefaultParams() is not called here.
     */
    protected ParamHolder(PropertiesReader props) {
    		this.props = props;
    		debug  = DEFAULT_DEBUG;
    		params = null;
    }

    /**
     * Method setDefaultParams sets the debug variable based on what the value
     * in the PropertiesReader or false otherwise.
     *
     * This method should be overrided in extending classes that have configurable paramaters.
     * These overriding methods should always call super.setDefaultParams().
     *
     */
    public void setDefaultParams() {
		try {
		    debug = (props == null ? DEFAULT_DEBUG : props.getBoolean("debug"));
		} catch (Exception e) {
		    debug = false;
		}
    }

    /**
     * Method setParam sets the value of the configurable param with the name corrisponding
     * to param.getName() to value.  Returns true if value is a valid
     * value for the param, false otherwise.
     *
     * This method should be overrided in extending classes that have configurable paramaters.
     * These overriding methods should always call super.setParam(FinderParam,Object).
     */
    public boolean setParam(FinderParam param, Object value) {
    		if (param.isValid(value)) {
    			try {
    				if (param.getName().equals("debug")) debug = ((Boolean)value).booleanValue();
    				return true;
    			} catch (Exception e) { e.printStackTrace(); }
    		}
    		return false;
    }

    /**
     * Method numberOfParams returns the number of params for this block finder.
     *
     * This method should be overrided in extending classes that have configurable paramaters.
     * These overriding methods should return super.numberOfParams() + N. Where N is
     * the number of params being added. N could be negative, but getParam(int) must be implemented
     * accordingly.
     */
    protected int numberOfParams() {
    		return 1;
    }

 
    protected FinderParam getParam(int i) {
    	boolean b = (props == null) ? DEFAULT_DEBUG : props.getBoolean("debug"); // CAS520 new Boolean
    	if (i == 0) return new FinderParam("debug","Debug","Print debug information",b);
    	return null;
    }

    public final FinderParam[] getParams() {
		if (params == null) {
		    params = new FinderParam[numberOfParams()];
		    for (int i = 0; i < params.length; i++)
			params[i] = getParam(i);
		}
		return params;
    }

 
    public final void setParams(FinderParam[] params, Object[] values) {
		if (params != null && values != null && params.length == values.length) {
		    for (int i = 0; i < params.length; i++)
			setParam(params[i],values[i]);
		}
    }

    public final void setParams(Object[] values) {
    		setParams(getParams(),values);
    }

    protected void printDebug(String message) {
    		if (debug)
    			System.out.println("DEBUG: "+message);
    }
}
