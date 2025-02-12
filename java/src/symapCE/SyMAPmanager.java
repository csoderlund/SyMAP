package symapCE;

import java.awt.Component;

import backend.Constants;
import backend.anchor1.Group;
import symap.Globals;
import symap.manager.ManagerFrame;
import util.ErrorReport;

/*********************************************
 * Called by symap and viewSymap scripts
 * ManagerFrame displays interface, SyMAPFrame calls showExplorer
 * CAS541 showExplorer to ManagerFrame
 */

public class SyMAPmanager extends ManagerFrame {
	private static final long serialVersionUID = 1L;
	
	public static void main(String args[])  {	
		if (!checkJavaSupported(null)) return;
		
		if (equalOption(args, "-h") || equalOption(args, "-help") || equalOption(args, "--h")) {
			prtParams(args); // see ManagerFrame for all variable stuff
			System.exit(0);
		}
		setParams(args);
		printVersion();
		
		SyMAPmanager frame = new SyMAPmanager(args);
		frame.setVisible(true);
	}
	
	SyMAPmanager(String args[]) {
		super(); // Creates ManagerFrame; 
	}
	
	/******************************************************************
	 * CAS511 the perl script was removed, so the parameters need to be written here
	 * CAS505 moved parse args to ManagerFrame
	 * CAS534 moved back as the 3D was removed, which was causing duplicate args
	 */
	public static void prtParams(String args[]) {
		// Do not start new parameters with -c or -p; see startsWith below vs equals
		if (equalOption(args, "-r")) {
			System.out.println("Usage:  ./viewSymap [options]");
			System.out.println("  -c string : filename of config file (to use instead of symap.config)");
			System.out.println("  -sql      : check MySQL for important settings");
			System.out.println("  -q        : show gene overlap instead of exon for Cluster Algo2");
			System.out.println("  -a        : do not trim 2D alignments");
			System.out.println("  -h        : show help to terminal and exit");
		}
		else {
			System.out.println("Usage:  ./symap [options]");
			System.out.println("  -c string : filename of config file (to use instead of symap.config)");
			System.out.println("  -sql      : check MySQL for important settings");
			System.out.println("  -q        : show gene overlap instead of exon for Cluster Algo2");
			System.out.println("  -a        : do not trim 2D alignments (Explorer only)");
			System.out.println("  -s        : recreate Summary on view (only needed on new release when the summary has changed)");
			System.out.println("  -h        : show help to terminal and exit");
	
			System.out.println("\nSynteny&Alignment:");
			System.out.println("  -p N      : number of CPUs to use");
			System.out.println("  -v        : A&S verbose output");
			System.out.println("  -mum      : do not remove any mummer files");
			System.out.println("  -wsp      : for g2, print MUMmer hits that differ from gene strand (v5.4.8 Algo2, v5.6.0 update)");
			System.out.println("  -sg       : split genes on cluster hit creation (Cluster Algo1)");
			System.out.println("  -acs      : On A&S, ONLY execute the collinear sets computation");
		}
	}
	// these are listed to terminal in the 'symap' perl script.
	private static void setParams(String args[]) { 
		if (args.length > 0) {
			if (equalOption(args, "-r")) {// used by viewSymap
				inReadOnlyMode = true; // no message to terminal
			}
			if (equalOption(args, "-sql")) {// check MySQL for important settings; CAS561 was -v
				Globals.bMySQL = true; 
				System.out.println("-sql  check MySQL settings ");
			}
			if (startsWithOption(args, "-p")) { // #CPU
				String x = getCommandLineOption(args, "-p"); //CAS500
				try {
					maxCPUs = Integer.parseInt(x);
					System.out.println("-p  Max CPUs " + maxCPUs);
				}
				catch (Exception e){ System.err.println(x + " is not an integer. Ignoring.");}
			}
			if (startsWithOption(args, "-c")) {// CAS501
				Globals.MAIN_PARAMS = getCommandLineOption(args, "-c");
				System.out.println("-c  Configuration file " + Globals.MAIN_PARAMS);
			}
			if (equalOption(args, "-q")) { // CAS531 change
				Globals.bQueryOlap=true;
				System.out.println("-q  Show gene overlap instead of exon for Algo2");
			}
			if (equalOption(args, "-a")) { // CAS531 change
				Globals.bTrim=false;
				System.out.println("-a  Do not trim 2D alignments");
			}
			if (equalOption(args, "-s")) {
				System.out.println("-s  Regenerate summary");
				Globals.bRedoSum = true;
			}
			// A&S
			if (equalOption(args, "-v")) {// verbose A&S output; CAS561 new option
				Constants.VERBOSE = true; 
				if (equalOption(args, "-r")) System.out.println("Warning: -v  Use flag '-sql' to check MySQL settings ");
				else                         System.out.println("-v A&S verbose output");
			}
			if (equalOption(args, "-mum")) { // CAS559 add (remove obsolete -oo for old ordering of groups)
				System.out.println("-mum  Do not remove any mummer result files");
				Constants.MUM_NO_RM = true;
			}
			if (equalOption(args, "-wsp")) { // CAS548
				System.out.println("-wsp  Print g2 hits where the hit strands differ from the genes (Algo2)");
				Constants.WRONG_STRAND_PRT = true;
			}
			if (equalOption(args, "-acs")) { // CAS556
				System.out.println("-acs  On A&S, ONLY execute the collinear sets computation");
				Constants.CoSET_ONLY = true;
			}
			if (equalOption(args, "-sg")) { // CAS540
				System.out.println("-sg  Split genes (Algo1)");
				Group.bSplitGene= true;
			}
			// CAS560 remove -z; System.out.println("-z  Reload Annotation will only run the Gene# assignment algorithm");
			// CAS560 remove    System.out.println("-wse  Exclude g2 hits where the hit strands differ from the genes (Algo2)");
			/*************************************************************************/
			/** not shown in -h help - hence, the double character so user does not use by mistake **/
			if (equalOption(args, "-ii")) {
				System.out.println("-ii Extra info ");// CAS560 - extra info on popups, query overlap
				Globals.INFO = true;
			}
			if (equalOption(args, "-tt")) {
				System.out.println("-tt Trace output (developer only)");
				Globals.TRACE = true;   	// CAS560 changed to print all trace 
				Constants.PRT_STATS = true; // CAS560; got rid of the command line -s except for summary
			}
			if (equalOption(args, "-dd")) {
				System.out.println("-dd Debug (developer only)");// CAS533 changed to -dd, potential errors
				Globals.DEBUG = true;
			}
			if (equalOption(args, "-dbd")) {
				System.out.println("-dbd Database (developer only)");
				Globals.DBDEBUG = true;
			}
			// old tests - not shown in -h help
			if (equalOption(args, "-aa")) { // CAS531 change
				System.out.println("-aa Align largest project to smallest");
				lgProj1st = true;
			}
			if (equalOption(args, "-bb")) { // CAS533 change; orig used midpoints
				System.out.println("-bb Original block coordinates");
				Constants.NEW_BLOCK_COORDS = false;
			}
		}
	}
	
	private static boolean equalOption(String[] args, String name) {// CAS534 moved from Utilites
		for (int i = 0;  i < args.length;  i++)
			if (args[i].equals(name)) 
				return true;
		return false;
	}
	private static boolean startsWithOption(String[] args, String name) {
		for (int i = 0;  i < args.length;  i++)
			if (args[i].equals(name)) // CAS556 was startsWith
				return true;
		return false;
	}
	private static String getCommandLineOption(String[] args, String name){// CAS534 moved from Utilites
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
	/********************************************************************/
	// moved from SyMAP2d as was not even used there; only called from Manager
	// String d = (Runtime.getRuntime().maxMemory() / (1024*1024)) + "m";
	// This is printed to log file in ProgressDialog
	public static void printVersion() {
		String url = util.Jhtml.BASE_HELP_URL;
		String base = url.substring(0, url.length()-1);
		System.out.println("\nSyMAP " + Globals.VERSION + Globals.DATE + "  " + base
			+ "\nRunning Java v" + System.getProperty("java.version")); // CAS548 add 'Compiled', remove mem; CAS550 was Run, no compile; CAS559 Running 
	}
	
	public static boolean checkJavaSupported(Component frame) {	// CAS559 update; CAS42 10/16/17 - did not work for Java SE 9
	try {
		String relV = Globals.JARVERION;
		
		String userV = System.getProperty("java.version");
		if (userV==null) {
			System.err.println("+++ Could not determine Java version - try to continue....");
			return true;
		}
		String [] ua = userV.split("\\.");
		if (ua.length<=1) {
			System.out.println("+++ Java version " + userV + "; incorrect format - try to continue....");
			return true;
		}
		
		String [] rx = relV.split("\\.");
		int r0 = Integer.valueOf(rx[0]), r1 = Integer.valueOf(rx[1]), r2 = Integer.valueOf(rx[2]);
		
		int u0 = Integer.valueOf(ua[0]), u1 = Integer.valueOf(ua[1]);
		int u2 = (ua.length>2)  ? Integer.valueOf(ua[2]) : -1;
		
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