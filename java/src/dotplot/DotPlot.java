package dotplot;

import util.PropertiesReader;

// CAS512 obsolete class - except for the following
public class DotPlot {
	public static final String DOTPLOT_PROPS = "/properties/dotplot.properties";

	// The total number of runs.  First one is the regular block hits.  
	// Next, there is the main Sytry run.  Then there are the comparison 
	// runs defined in the properties file DOTPLOT_PROPS.  The last one is 
	// an extra run for the sub chains.
	public static final int TOT_RUNS;

	public static final int SUB_CHAIN_RUN; // The sub-chain run (i.e., TOT_RUNS - 1)
	
	public static final boolean RUN_SUBCHAIN_FINDER;

	static {
		PropertiesReader props = new PropertiesReader(DotPlot.class.getResource(DOTPLOT_PROPS));
		int n = props.getInt("numCompRuns"); // =1
		if (n < 0) n = 0;
		TOT_RUNS      = n + 3;
		SUB_CHAIN_RUN = n + 2;
		RUN_SUBCHAIN_FINDER = props.getBoolean("runSubChainFinder");
	}
}
