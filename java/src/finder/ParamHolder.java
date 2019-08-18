package finder;

import util.PropertiesReader;

/**
 * Class <code>ParamHolder</code> lays out the foundation for a ParamHolder such as a BlockFinder.
 *
 * All of the non final methods may need to be overrided for new params, also calling the super
 * method since ParamHolder takes care of the debug parameter.
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 */
public abstract class ParamHolder {
    private static final boolean DEFAULT_DEBUG = false;

    protected final PropertiesReader props;
    protected boolean debug;
    private FinderParam[] params;

    /**
     * Creates a new <code>ParamHolder</code> instance. with the given props file.
     * Note that setDefaultParams() is not called here.
     *
     * @param props a <code>PropertiesReader</code> value
     */
    protected ParamHolder(PropertiesReader props) {
	this.props = props;
	debug  = DEFAULT_DEBUG;
	params = null;
    }

    /**
     * Method <code>setDefaultParams</code> sets the debug variable based on what the value
     * in the PropertiesReader or false otherwise.
     *
     * This method should be overrided in extending classes that have configurable paramaters.
     * These overriding methods should always call <code>super.setDefaultParams()</code>.
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
     * Method <code>setParam</code> sets the value of the configurable param with the name corrisponding
     * to <code>param.getName()</code> to <code>value</code>.  Returns true if <code>value</code> is a valid
     * value for the param, false otherwise.
     *
     * This method should be overrided in extending classes that have configurable paramaters.
     * These overriding methods should always call <code>super.setParam(FinderParam,Object)</code>.
     *
     * @param param a <code>FinderParam</code> value
     * @param value an <code>Object</code> value
     * @return a <code>boolean</code> value
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
     * Method <code>numberOfParams</code> returns the number of params for this block finder.
     *
     * This method should be overrided in extending classes that have configurable paramaters.
     * These overriding methods should return <code>super.numberOfParams() + N</code>. Where N is
     * the number of params being added. N could be negative, but getParam(int) must be implemented
     * accordingly.
     *
     * @return an <code>int</code> value
     */
    protected int numberOfParams() {
	return 1;
    }

    /**
     * Method <code>getParam</code> returns the param with index value i.  If the index value
     * does not corrispond to a value in the class being implemented (i.e. <code>i &lt; super.numberOfParams()</code>)
     * this method should return <code>super.getParam(i)</code>.
     *
     * @param i an <code>int</code> value
     * @return a <code>FinderParam</code> value
     */
    protected FinderParam getParam(int i) {
	if (i == 0) return new FinderParam("debug","Debug","Print debug information",
					   new Boolean(props == null ? DEFAULT_DEBUG : props.getBoolean("debug")));
	return null;
    }


    /**
     * Method <code>getParams</code> returns the paramaters in an array using <code>getParam(int)</code>
     * to create the array.
     *
     * @return a <code>FinderParam[]</code> value
     */
    public final FinderParam[] getParams() {
	if (params == null) {
	    params = new FinderParam[numberOfParams()];
	    for (int i = 0; i < params.length; i++)
		params[i] = getParam(i);
	}
	return params;
    }

    /**
     * Method <code>setParams</code> sets the params from the arrays given.
     * FinderParam at index i of <code>params</code> corrisponds to the Object value at index i in <code>values</code>.
     *
     * @param params a <code>FinderParam[]</code> value
     * @param values an <code>Object[]</code> value
     */
    public final void setParams(FinderParam[] params, Object[] values) {
	if (params != null && values != null && params.length == values.length) {
	    for (int i = 0; i < params.length; i++)
		setParam(params[i],values[i]);
	}
    }

    /**
     * Method <code>setParams</code> calls <code>setParams(getParams(),values)</code>.
     *
     * @param values an <code>Object[]</code> value
     */
    public final void setParams(Object[] values) {
	setParams(getParams(),values);
    }


    /**
     * Method <code>printDebug</code> to be called when a debug message
     * is to be printed. Alternative implementations should check the debug
     * variable before printing.
     *
     * @param message a <code>String</code> value
     */
    protected void printDebug(String message) {
	if (debug)
	    System.out.println("DEBUG: "+message);
    }
}
