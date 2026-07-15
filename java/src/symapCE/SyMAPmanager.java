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
	private static boolean isReadOnly;

	public static void main(String args[])  {	
		printVersion(); 
		
		isReadOnly = equalOption(args, "-r");
		
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
	private SyMAPmanager(String args[]) {super(); }		// Creates ManagerFrame; 

	/** Args **/
	private static String [] rArgVec = {"-r", "-c", "-sql",  "-p", "-a", "-go", "-pg", "-ac"};	// symap or viewSymap
	private static String [] wArgVec = {"-mum", "-wsp","-v", "-nh", "-cds"};					  		// symap
	private static String [] hArgVec = { "-ii","-tt", "-dd", "-dbd", "-bt", "-s", "-cs", "-ncat"}; 	    // hidden
	
	private static void argPrint(String args[]) {
		if (isReadOnly) System.out.println("Usage:  ./viewSymap [options]");
		else 			System.out.println("Usage:  ./symap [options]");
		
		System.out.println("  -c string : Filename of config file (to use instead of symap.config)");
		System.out.println("  -sql      : MySQL: for important settings and external programs");
		System.out.println("  -p N      : Number of CPUs to use (same as setting CPUs on SyMAPmanager)"); // used in Query MSA
		System.out.println("  -h        : Show help to terminal and exit");
		
		if (!isReadOnly) { // viewSymap is started with -r arg
			System.out.println("\nLoad Project:");
			System.out.println("  -cds      : Use CDSs instead of exons (CDSs must be in .gff file)");
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
		System.out.println("  -ii  Extra info on 2d popups, extra Query, check Case-insensitive database ");
		System.out.println("  -tt  Trace output, and query zTest files ");
		System.out.println("  -dd  Debug - mysql trace, etc");
		System.out.println("  -dbd Database - saves special tables to db");
		System.out.println("  -bt  Synteny trace");
		System.out.println("  -s   Regenerate summary");
		System.out.println("  -cs  Collinear sets: recompute for v5.7.7");
		System.out.println("  -ncat Do not concat any sequence files for v5.8.1");
	}
	private static void argSet(String args[]) { 
		if (args.length ==0) return;
		
		if (isReadOnly) {			// used by viewSymap; set in ManagerFrame
			inReadOnlyMode = true; 
		}
		if (equalOption(args, "-c")) {
			Globals.MAIN_PARAMS = getOption(args, "-c");
			if (Globals.MAIN_PARAMS==null) {
				System.err.println("-c must be followed by the name of a configuration file");
				System.exit(-1);
			}
		}
		if (equalOption(args, "-p")) { // #CPU;
			String x = getOption(args, "-p"); 
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
	/** Display viewSymap and symap **/
		if (equalOption(args, "-a")) { 
			Globals.bTrim=false;
			System.out.println("-a  2D: Do not trim alignments");
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
	/** Load Project symap **/
		if (equalOption(args, "-cds")) {// CAS586
			System.out.println("-cds  Load CDSs instead of Exons");
			backend.AnnotLoadMain.bUseCDS = true;
		}
	/** A&S symap **/
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
		if (equalOption(args, "-nh")) {// CAS579c add - not on help anymore
			System.out.println("-nh  numHits: recompute numHits for v5.7.9c");
			Constants.NUMHITS_ONLY = true;
		}
		
	/** -hhh viewSymap and symap **/
		if (equalOption(args, "-ii")) {
			System.out.println("-ii Extra info");
			Globals.INFO = true;
		}
		if (equalOption(args, "-tt")) {
			System.out.println("-tt Trace output and mysql to file (and -ii)");
			Globals.TRACE = Globals.INFO = true;   	
		}
		if (equalOption(args, "-dd")) {
			System.out.println("-dd Debug (and -ii)");
			Globals.DEBUG = Globals.INFO = true;
		}
		if (equalOption(args, "-dbd")) {
			System.out.println("-dbd Database - add tables to DB");
			Globals.DBDEBUG = true;
		}
		if (equalOption(args, "-bt")) {  
			System.out.println("-bt Synteny trace");
			backend.synteny.SyntenyMain.bTrace = true;
		}
		if (equalOption(args, "-ncat")) {  // CAS581
			System.out.println("-ncat No concatenation");
			backend.AlignMain.bNoCAT = true;
		}
		if (equalOption(args, "-cs")) { // shown on -h for v5.7.7
			System.out.println("-cs  On A&S, ONLY execute the collinear sets computation");
			Constants.CoSET_ONLY = true;
		}
		if (equalOption(args, "-s")) { 
			System.out.println("-s  Regenerate summary");
			Globals.bRedoSum = true;
		}
		// not shown on -h; leave for possible updates
		if (equalOption(args, "-sg")) { 
			System.out.println("-sg  Split genes (Algo1)");
			Group.bSplitGene= true;
		}		
	}
	/******************************************************************
	 * Command line parameters
	 */
	private static boolean argFail(String args[]) {// return true
		if (args.length==0) return false;
		
		if (args.length==1 && isReadOnly) return false;
		
		HashSet <String> argSet = new HashSet <String> ();
		for (String x : rArgVec) argSet.add(x);
		for (String x : hArgVec) argSet.add(x);	
		if (!isReadOnly) 
			for (String x : wArgVec) argSet.add(x);
		
		for (int i = 0;  i < args.length;  i++) {
			String a = args[i];
			
			if (a.startsWith("-") && !argSet.contains(a)) {
				System.err.println("*** Illegal argument: " + a);
				argPrint(args);
				return true;
			}
		}
		if (isReadOnly && args.length==2 && !args[1].startsWith("-")) { // viewSymap xxx  (-r comes first)
			System.err.println("*** Illegal argument: " + args[1]);
			argPrint(args);
			return true;
		}
		if (!isReadOnly && args.length==1 && !args[0].startsWith("-")) {// symap xxx  (check 1st arg)
			System.err.println("*** Illegal argument: " + args[0]);
			argPrint(args);
			return true;
		}
		return false;
	}
	private static boolean equalOption(String[] args, String name) {
		for (int i = 0;  i < args.length;  i++)
			if (args[i].equals(name)) 
				return true;
		return false;
	}
	private static String getOption(String[] args, String name){
		for (int i = 0;  i < args.length;  i++){
			/* CAS586 remove
			if (args[i].startsWith(name) && !args[i].equals(name)){
				String ret = args[i];
				ret = ret.replaceFirst(name,"");
				return ret;
			}
			else */ 
			if (args[i].equals(name) && i+1 < args.length) {
				return args[i+1];
			}
		}
		return null;
	} 	
	/*************************************************
	 * Sets architecture (Available linux64, mac, macM4); Reads from symap.config
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
		if (mummer!=null && mummer!="" && !mummer.isBlank()) {
			System.out.println("   MUMmer path: " + mummer);
			Ext.setMummer4Path(mummer);
		}
		return true;
	} catch (Exception e) {ErrorReport.print(e, "Setting architecture"); return false;}
	}
	/** Banner**/
	private static void printVersion() {
		System.out.println("\nSyMAP " + Globals.VERDATE + "  " + Globals.WEB);	
		System.out.println("Running on " + Ext.getPlatform() + " with Java v" + System.getProperty("java.version")); 
	}
	private static boolean checkJavaSupported(Component frame) {
	try {
		String userV = System.getProperty("java.version");
		if (userV==null) {
			System.err.println("+++ Could not determine Java version - try to continue....");
			return true;
		}
		String [] ua = userV.split("\\.");
		int u0=0, u1=0, u2=0; 	// this is suppose to have 3 numbers, but 22 just has 1; 
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