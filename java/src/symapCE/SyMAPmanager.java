package symapCE;

import java.awt.Component;
import java.io.File;
import java.util.HashSet;

import backend.Constants;
import backend.anchor1.Group;
import props.PropertiesReader;
import symap.Ext;
import symap.Globals;
import symap.manager.ManagerFrame;
import util.ErrorReport;

/*********************************************
 * Called by symap and viewSymap scripts; print help; set options; starts ManagerFrame 
 */

public class SyMAPmanager extends ManagerFrame {
	private static final long serialVersionUID = 1L;
	
	public static void main(String args[])  {	
		printVersion(); 
		
		if (equalOption(args, "-hhh")) {// for me; put first
			argPrintHidden(args);
			System.exit(0);
		}
		if (equalOption(args, "-h") || equalOption(args, "-help") || equalOption(args, "--h")) {
			argPrint(args); 			
			System.exit(0);
		}
		if (argFail(args)) System.exit(0);	// prints args; CAS579 add

		argSet(args);
		if (!setArch()) return; // must go after setParams; reads configuration file
		
		SyMAPmanager frame = new SyMAPmanager(args);
		frame.setVisible(true);
	}
	
	private SyMAPmanager(String args[]) {
		super(); 		// Creates ManagerFrame; 
	}

	private static void printVersion() {
		String url = util.Jhtml.BASE_HELP_URL;
		String base = url.substring(0, url.length()-1);
		System.out.println("\nSyMAP " + Globals.VERSION + Globals.DATE + "  " + base);	
		System.out.println("Running on " + Ext.getPlatform() + " with Java v" + System.getProperty("java.version")); 
	}
	/******************************************************************
	 * Command line parameters
	 * Add to argCheck, argPrint, argSet
	 */
	private static boolean argFail(String args[]) {// return true
		if (args.length==0) return false;
		boolean ronly = equalOption(args, "-r");
		if (args.length==1 && ronly) return false;
		
		String [] rArgVec = {"-c", "-sql", "-a", "-go", "-pg", "-ac"};
		String [] wArgVec = {"-mum", "-wsp", "-p", "-v", };
		
		HashSet <String> argSet = new HashSet <String> ();
		for (String x : rArgVec) argSet.add(x);
		if (!ronly) for (String x : wArgVec) argSet.add(x);
		
		for (int i = 0;  i < args.length;  i++) {
			String a = args[i];
			if (equalOption(args, "-r")) continue;
			
			if (a.startsWith("-") && !argSet.contains(a)) {
				System.err.println("*** Illegal argument: " + a);
				argPrint(args);
				return true;
			}
		}
		if (ronly && args.length==2 && !args[1].startsWith("-")) { // viewSymap xxx  (-r comes first)
			System.err.println("*** Illegal argument: " + args[1]);
			argPrint(args);
			return true;
		}
		if (!ronly && args.length==1 && !args[0].startsWith("-")) {// symap xxx  (check 1st arg)
			System.err.println("*** Illegal argument: " + args[0]);
			argPrint(args);
			return true;
		}
		return false;
	}
	private static void argPrint(String args[]) {
		if (equalOption(args, "-r")) System.out.println("Usage:  ./viewSymap [options]");
		else 						 System.out.println("Usage:  ./symap [options]");
		
		System.out.println("  -c string : Filename of config file (to use instead of symap.config)");
		System.out.println("  -sql      : MySQL: for important settings and external programs");
		System.out.println("  -p N      : Number of CPUs to use (same as setting CPUs on SyMAPmanager)");
		System.out.println("  -h        : Show help to terminal and exit");
		
		if (!equalOption(args, "-r")) { // viewSymap is started with -r arg
			System.out.println("\nAlign&Synteny:");
			System.out.println("  -mum      : MUMmer: Do not remove any mummer files");
			System.out.println("  -wsp      : Algo2: print MUMmer hits that differ from gene strand");
			System.out.println("  -v        : A&S verbose output (same as checking Verbose on Manager)");
		}
		System.out.println("\nDisplay:");
		System.out.println("  -a        : 2D: do not trim alignments");
		System.out.println("  -go       : Queries: show gene overlap instead of exon for Cluster Algo2");
		System.out.println("  -pg       : Queries: for Cluster, run old PgeneF algorithm instead of the new Cluster algorithm");
		System.out.println("  -ac       : Queries: for Cluster, retain all clusters regardless of size");
	}
	private static void argPrintHidden(String args[]) {
		System.out.println("Developer only special flags ");
		System.out.println("  -ii  Extra info on 2d popups, Queries ");
		System.out.println("  -tt  Trace output, and query zTest files ");
		System.out.println("  -dd  Debug - mysql trace, etc");
		System.out.println("  -dbd Database - saves special tables to db");
		System.out.println("  -bt  Synteny trace");
		System.out.println("  -s   Regenerate summary");
		System.out.println("  -cs  Collinear sets: recompute for v5.7.7");
	}
	private static void argSet(String args[]) { 
		if (args.length ==0) return;
		
		if (equalOption(args, "-r")) {// used by viewSymap
			inReadOnlyMode = true; // no message to terminal
		}
		
		if (equalOption(args, "-c")) {
			Globals.MAIN_PARAMS = getCommandLineOption(args, "-c");
			if (Globals.MAIN_PARAMS==null) {
				System.err.println("-c must be followed by the name of a configuration file");
				System.exit(-1);
			}
		}
		if (equalOption(args, "-p")) { // #CPU;
			String x = getCommandLineOption(args, "-p"); 
			try {
				maxCPU = Integer.parseInt(x);	
				System.out.println("-p  Max CPUs " + x);
			}
			catch (Exception e){ System.err.println(x + " is not an integer. Ignoring.");}
		}
		
		if (equalOption(args, "-sql")) {// check MySQL for important settings; 
			Globals.bMySQL = true; 
			System.out.println("-sql MySQL: check settings ");
		}
	
		if (equalOption(args, "-go")) { 
			Globals.bQueryOlap=true;
			System.out.println("-go  Queries: show gene overlap instead of exon for Cluster Algo2");
		}
		if (equalOption(args, "-pg")) { // CAS563 add
			Globals.bQueryPgeneF=true;
			System.out.println("-pg  Queries: for Cluster, run old PgeneF algorithm instead of the new Cluster algorithm");
		}
		if (equalOption(args, "-ac")) { // CAS579 add
			Globals.bQuerySaveLgClust=true;
			System.out.println("-ac  Queries: retain all clusters regardless of size");
		}
		
		if (equalOption(args, "-a")) { 
			Globals.bTrim=false;
			System.out.println("-a  2D: Do not trim alignments");
		}
		if (equalOption(args, "-s")) { // not in -h
			System.out.println("-s  Regenerate summary");
			Globals.bRedoSum = true;
		}
		// A&S
		if (equalOption(args, "-v")) {// also on ManagerFrame; verbose A&S output
			Constants.VERBOSE = true;		
			System.out.println("-v A&S verbose output");
		}
		if (equalOption(args, "-mum")) { 
			System.out.println("-mum  MUMmer: Do not remove any mummer result files");
			Constants.MUM_NO_RM = true;
		}
		if (equalOption(args, "-wsp")) {
			System.out.println("-wsp  Algo2: print MUMmer hits that differ from gene strand");
			Constants.WRONG_STRAND_PRT = true;
		}
		
		// not shown on -h; leave for possible updates
		if (equalOption(args, "-sg")) { 
			System.out.println("-sg  Split genes (Algo1)");
			Group.bSplitGene= true;
		}
		
		/*************************************************************************/
		/** not shown in -h help - hence, the double character so user does not use by mistake **/
		if (equalOption(args, "-ii")) {
			System.out.println("-ii Extra info (e.g. idx on hit and gene info; warning messages");
			Globals.INFO = true;
		}
		if (equalOption(args, "-tt")) {
			System.out.println("-tt Trace output and mysql to file");
			Globals.TRACE = true;   	
		}
		if (equalOption(args, "-dd")) {
			System.out.println("-dd Debug");
			Globals.DEBUG = true;
		}
		if (equalOption(args, "-dbd")) {
			System.out.println("-dbd Database - add tables to DB");
			Globals.DBDEBUG = true;
		}
		if (equalOption(args, "-bt")) {  
			System.out.println("-bt Synteny trace");
			backend.synteny.SyntenyMain.bTrace = true;
		}
		if (equalOption(args, "-cs")) { // shown on -h for v5.7.7
			System.out.println("-cs  On A&S, ONLY execute the collinear sets computation");
			Constants.CoSET_ONLY = true;
		}
	}
	
	private static boolean equalOption(String[] args, String name) {
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
		
		if (util.FileDir.fileExists(paramsfile)) System.out.println("Configuration file " + paramsfile);
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