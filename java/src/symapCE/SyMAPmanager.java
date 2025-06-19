package symapCE;

import java.awt.Component;
import java.io.File;

import backend.Constants;
import backend.anchor1.Group;
import props.PropertiesReader;
import symap.Ext;
import symap.Globals;
import symap.manager.ManagerFrame;
import util.ErrorReport;
import util.Utilities;

/*********************************************
 * Called by symap and viewSymap scripts
 * ManagerFrame displays interface, SyMAPFrame calls showExplorer
 */

public class SyMAPmanager extends ManagerFrame {
	private static final long serialVersionUID = 1L;
	
	public static void main(String args[])  {	
		// CAS569 user may have compiled, etc. if (!checkJavaSupported(null)) return;
		
		if (equalOption(args, "-h") || equalOption(args, "-help") || equalOption(args, "--h")) {
			prtParams(args); // see ManagerFrame for all variable stuff
			System.exit(0);
		}
		printVersion();
		setParams(args);
		if (!setArch()) return; // must go after setParams; reads configuration file
		
		SyMAPmanager frame = new SyMAPmanager(args);
		frame.setVisible(true);
	}
	
	SyMAPmanager(String args[]) {
		super(); // Creates ManagerFrame; 
	}

	private static void printVersion() {
		String url = util.Jhtml.BASE_HELP_URL;
		String base = url.substring(0, url.length()-1);
		System.out.println("\nSyMAP " + Globals.VERSION + Globals.DATE + "  " + base);	
		System.out.println("Running on " + Ext.getPlatform() + " with Java v" + System.getProperty("java.version")); 
	}
	/******************************************************************
	 * Command line parameters
	 * CAS511 the perl script was removed, so the parameters need to be written here
	 * CAS505 moved parse args to ManagerFrame
	 * CAS534 moved back as the 3D was removed, which was causing duplicate args
	 */
	public static void prtParams(String args[]) {
		// Do not start new parameters with -c or -p; see startsWith below vs equals
		if (equalOption(args, "-r")) System.out.println("Usage:  ./viewSymap [options]");
		else 						 System.out.println("Usage:  ./symap [options]");
		
		System.out.println("  -c string : filename of config file (to use instead of symap.config)");
		System.out.println("  -sql      : check MySQL for important settings and external programs");
		System.out.println("  -a        : do not trim 2D alignments");
		System.out.println("  -q        : Queries: show gene overlap instead of exon for Cluster Algo2");
		System.out.println("  -g        : Queries: run old PgeneF algorithm instead of the new Cluster algorithm");
		System.out.println("  -h        : show help to terminal and exit");
			
		if (!equalOption(args, "-r")) { // viewSymap - treated as read-only
			System.out.println("\nSynteny&Alignment:");
			System.out.println("  -p N      : number of CPUs to use");
			System.out.println("  -v        : A&S verbose output");
			System.out.println("  -mum      : do not remove any mummer files");
			System.out.println("  -wsp      : for g2, print MUMmer hits that differ from gene strand (v5.4.8 Algo2, v5.6.0 update)");
			System.out.println("  -pseudo   : On A&S, ONLY compute pseudo (v5.6.5 or later)"); // CAS565
		}
	}
	
	private static void setParams(String args[]) { 
		if (args.length ==0) return;
		
		if (equalOption(args, "-r")) {// used by viewSymap
			inReadOnlyMode = true; // no message to terminal
		}
		
		if (startsWithOption(args, "-c")) {
			Globals.MAIN_PARAMS = getCommandLineOption(args, "-c");
			if (Globals.MAIN_PARAMS==null) {
				System.err.println("-c must be followed by the name of a configuration file");
				System.exit(-1);
			}
			// CAS568 says it next line; System.out.println("-c  Configuration file " + Globals.MAIN_PARAMS);
		}
		if (startsWithOption(args, "-p")) { // #CPU; CAS500
			String x = getCommandLineOption(args, "-p"); 
			try {
				maxCPU = Integer.parseInt(x);	
				System.out.println("-p  Max CPUs " + x);
			}
			catch (Exception e){ System.err.println(x + " is not an integer. Ignoring.");}
		}
		
		if (equalOption(args, "-sql")) {// check MySQL for important settings; CAS561 was -v
			Globals.bMySQL = true; 
			System.out.println("-sql  check MySQL settings ");
		}
		
		if (equalOption(args, "-q")) { 
			Globals.bQueryOlap=true;
			System.out.println("-q  Show gene overlap instead of exon for Algo2");
		}
		if (equalOption(args, "-g")) { // CAS563 add
			Globals.bQueryPgeneF=true;
			System.out.println("-g  Run PgeneF algorithm in place of Cluster algorithm");
		}
		if (equalOption(args, "-a")) { 
			Globals.bTrim=false;
			System.out.println("-a  Do not trim 2D alignments");
		}
		if (equalOption(args, "-s")) { // not in -h
			System.out.println("-s  Regenerate summary");
			Globals.bRedoSum = true;
		}
		// A&S
		if (equalOption(args, "-v")) {// also on ManagerFrame; verbose A&S output; CAS561 new option
			Constants.VERBOSE = true;		
			System.out.println("-v A&S verbose output");
		}
		if (equalOption(args, "-mum")) { // CAS559 add (remove obsolete -oo for old ordering of groups)
			System.out.println("-mum  Do not remove any mummer result files");
			Constants.MUM_NO_RM = true;
		}
		if (equalOption(args, "-wsp")) { // CAS548
			System.out.println("-wsp  Print g2 hits where the hit strands differ from the genes (Algo2)");
			Constants.WRONG_STRAND_PRT = true;
		}
		
		if (equalOption(args, "-pseudo")) { // CAS565 Apr25
			System.out.println("-pseudo  On A&S, ONLY compute pseudo (v5.6.5 or later)");
			Constants.PSEUDO_ONLY = true;
		}
		// CAS565 not shown on -h
		if (equalOption(args, "-acs")) { // CAS556 July24; leave for possible updates
			System.out.println("-acs  On A&S, ONLY execute the collinear sets computation");
			Constants.CoSET_ONLY = true;
		}
		if (equalOption(args, "-sg")) { // CAS540
			System.out.println("-sg  Split genes (Algo1)");
			Group.bSplitGene= true;
		}
		
		/*************************************************************************/
		/** not shown in -h help - hence, the double character so user does not use by mistake **/
		if (equalOption(args, "-ii")) {
			System.out.println("-ii Extra info ");
			Globals.INFO = true;
		}
		if (equalOption(args, "-tt")) {
			System.out.println("-tt Trace output (developer only)");
			Globals.TRACE = true;   	
			Constants.PRT_STATS = true; 
		}
		if (equalOption(args, "-dd")) {
			System.out.println("-dd Debug (developer only)");
			Globals.DEBUG = true;
		}
		if (equalOption(args, "-dbd")) {
			System.out.println("-dbd Database (developer only)");
			Globals.DBDEBUG = true;
		}
		
		// experimental synteny tests - not shown in -h help; -brm is above
		if (equalOption(args, "-bf")) {  // CAS567 experiment
			System.out.println("-bf Tidy up blocks");
			backend.synteny.SyntenyMain.bTrim = true;
		}
		if (equalOption(args, "-bt")) {  
			System.out.println("-bt Synteny trace");
			backend.synteny.SyntenyMain.bTrace = true;
		}
	}
	
	private static boolean equalOption(String[] args, String name) {
		for (int i = 0;  i < args.length;  i++)
			if (args[i].equals(name)) 
				return true;
		return false;
	}
	private static boolean startsWithOption(String[] args, String name) {
		for (int i = 0;  i < args.length;  i++)
			if (args[i].equals(name)) 
				return true;
		return false;
	}
	private static String getCommandLineOption(String[] args, String name){
		for (int i = 0;  i < args.length;  i++){
			if (args[i].startsWith(name) && !args[i].equals(name)){
				String ret = args[i];
				ret = ret.replaceFirst(name,"");
				return ret;
			}
			else if (args[i].equals(name) && i+1 < args.length) {
				return args[i+1];
			}
		}
		return null;
	} 	
	
	/*************************************************
	 * Sets architecture (Available linux64, mac, macM4)
	 * Reads from symap.config
	 */
	private static boolean setArch() {
	try {
		String paramsfile = Globals.MAIN_PARAMS;
		
		if (Utilities.fileExists(paramsfile)) System.out.println("Configuration file " + paramsfile);
		else ErrorReport.die("Configuration file not available: " + paramsfile);		
		
		PropertiesReader dbProps  = new PropertiesReader(new File(paramsfile));
		
		// architecture
		String userArch = dbProps.getProperty("arch");
		if (userArch!=null) System.out.println("   Architecture: " + userArch); 
		Ext.setArch(userArch); // if null, will determine it; prints errors if any found
		
		// mummer path
		String mummer 	= dbProps.getProperty("mummer_path"); 
		if (mummer!=null && mummer!="") {
			System.out.println("   MUMmer path: " + mummer);
			Ext.setMummer4Path(mummer);
		}
		return true;
	} catch (Exception e) {ErrorReport.print(e, "Setting architecture"); return false;}
	}
	
	private static boolean checkJavaSupported(Component frame) {	// CAS559 update; CAS42 10/16/17 - did not work for Java SE 9
	try {
		String userV = System.getProperty("java.version");
		if (userV==null) {
			System.err.println("+++ Could not determine Java version - try to continue....");
			return true;
		}
		String [] ua = userV.split("\\.");
		int u0=0, u1=0, u2=0; 	// this is suppose to have 3 numbers, but 22 just has 1; CAS569
		if (ua.length>=0) u0 = Integer.valueOf(ua[0]);
		if (ua.length>=1) u1 = Integer.valueOf(ua[1]);
		if (ua.length>=2) u2 = Integer.valueOf(ua[2]);
		
		String relV = Globals.JARVERION; // "17.0.11";
		String [] rx = relV.split("\\.");
		int r0 = Integer.valueOf(rx[0]), r1 = Integer.valueOf(rx[1]), r2 = Integer.valueOf(rx[2]);
		
		// u0<r0 will fail before executions starts
		if (u0 == r0 && (u1 < r1 || u2 < r2))  { // versions seem to all have u1 as '0' except for v1.8 
			System.err.println("+++ Java version " + relV + " or later is required. The installed Java version is " + userV + "." );
			System.err.println("    This will run, but may have issues. Suggest updating your Java version.");
			return true;
		}
		
		return true;
	} catch (Exception e) {ErrorReport.print(e, "Getting Java version - try to continue...."); return true;}
	}
}