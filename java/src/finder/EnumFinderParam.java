package finder;

public class EnumFinderParam extends FinderParam {

    private Object[] values;
    private int def;

    public EnumFinderParam(String name, String displayName, String description, Object[] values, int def) {
	super(name,displayName,description,values[def],new AllParamAcceptor(values));
	this.values = values;
	this.def    = def;
    }

    public Object[] getOptions() {
	return values;
    }

    public int getDefaultOption() {
	return def;
    }

    public int getSelectedOption(Object sel) {
	for (int i = 0; i < values.length; i++)
	    if (values[i] == sel) return i;
	return -1;
    }

    private static class AllParamAcceptor implements ParamAcceptor {
	//private Object[] opts; // mdb removed 6/29/07 #118

	public AllParamAcceptor(Object[] values) {
	    //this.opts = values; // mdb removed 6/29/07 #118
	}

	public boolean isValid(Object obj) {
	    return true;
	    /*
	      for (int i = 0; i < opts.length; i++)
	      if (obj == opts[i]) return true;
	      return false;
	    */
	}
    }
}
