package symapCE;

import java.awt.Component;

import backend.Constants;
import backend.anchor1.Group;
import symap.Globals;
import symap.manager.ManagerFrame;

/*********************************************
 * Called by symap and viewSymap scripts
 * ManagerFrame displays interface, SyMAPFrame calls showExplorer
 * CAS541 showExplorer to ManagerFrame
 */

public class SyMAPmanager extends ManagerFrame {
	private static final long serialVersionUID = 1L;
	
	public static void main(String args[])  {	
		if (!checkJavaSupported(null)) return;
		
		if (equalOption(args, "-h") || equalOption(args, "-help") 
			|| equalOption(args, "--h")) {
			
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
			System.out.println("  -a        : do not trim 2D alignments");
			System.out.println("  -v        : check MySQL for important settings");
			System.out.println("  -h        : show help to terminal and exit");
		}
		else {
			System.out.println("Usage:  ./symap [options]");
			System.out.println("  -c string : filename of config file (to use instead of symap.config)");
			System.out.println("  -a        : do not trim 2D alignments (Explorer only)");
			System.out.println("  -v        : check MySQL for important settings");
			System.out.println("  -h        : show help to terminal and exit");
	
			System.out.println("\nSynteny&Alignment:");
			System.out.println("  -p N      : number of CPUs to use");
			System.out.println("  -s        : print stats for A&S, or recreate the Summary on display");
			System.out.println("  -wse      : for g2, exclude MUMmer hits that differ from gene strand (v5.4.8 Algo2)");
			System.out.println("  -wsp      : for g2, print MUMmer hits that differ from gene strand (v5.4.8 Algo2)");
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
			if (equalOption(args, "-v")) {// check version
				bCheckVersion = true; // no message to terminal
				System.out.println("-v Check version ");
			}
			if (startsWithOption(args, "-p")) { // #CPU
				String x = getCommandLineOption(args, "-p"); //CAS500
				try {
					maxCPUs = Integer.parseInt(x);
					System.out.println("-p Max CPUs " + maxCPUs);
				}
				catch (Exception e){ System.err.println(x + " is not an integer. Ignoring.");}
			}
			
			if (startsWithOption(args, "-c")) {// CAS501
				Globals.MAIN_PARAMS = getCommandLineOption(args, "-c");
				System.out.println("-c Configuration file " + Globals.MAIN_PARAMS);
			}
			if (equalOption(args, "-a")) { // CAS531 change
				Globals.bTrim=false;
				System.out.println("-a Do not trim 2D alignments");
			}
			
			if (equalOption(args, "-sg")) { // CAS540
				System.out.println("-sg split genes (Algo1)");
				Group.bSplitGene= true;
			}
			if (equalOption(args, "-z")) {// CAS519b
				Globals.GENEN_ONLY=true;
				System.out.println("-z Reload Annotation will only run the Gene# assignment algorithm");
			}
			
			if (equalOption(args, "-s")) {
				System.out.println("-s Print Stats");
				Constants.PRT_STATS = true;
			}
			if (equalOption(args, "-wsp")) { // CAS548
				System.out.println("-wsp print g2 hits where the hit strands differ from the genes (Algo2)");
				Constants.WRONG_STRAND_PRT = true;
			}
			if (equalOption(args, "-wse")) { // CAS548
				System.out.println("-wse exclude g2 hits where the hit strands differ from the genes (Algo2)");
				Constants.WRONG_STRAND_EXC = true;
			}
			if (equalOption(args, "-acs")) { // CAS556
				System.out.println("-acs On A&S, ONLY execute the collinear sets computation");
				Constants.CoSET_ONLY = true;
			}
			/*************************************************************************/
			/** not shown in -h help - hence, the double character so user does not use by mistake **/
			if (equalOption(args, "-dd")) {
				System.out.println("-dd Debug (developer only)");// CAS533 changed to -dd 
				Globals.DEBUG = true;
				//Q.TEST_TRACE = true; // for query
			}
			if (equalOption(args, "-dbd")) {
				System.out.println("-dbd Database (developer only)");
				Globals.DBDEBUG = true;
			}
			if (equalOption(args, "-tt")) {
				System.out.println("-tt Trace output");
				Constants.TRACE = true; // in backend
				Globals.TRACE = true;   // CAS517; used to add info
			}
			
			// old tests - not shown in -h help
			if (equalOption(args, "-oo")) {
				System.out.println("-oo Use the original version of draft ordering");
				Constants.NEW_ORDER = false;
			}
			if (equalOption(args, "-aa")) { // CAS531 change
				System.out.println("-aa Align largest project to smallest");
				lgProj1st = true;
			}
			if (equalOption(args, "-bb")) { // CAS533 change; orig used midpoints
				System.out.println("-bb Original block coordinates");
				Constants.NEW_BLOCK_COORDS = false;
			}
			if (equalOption(args, "-y")) {// CAS519b,  no longer shown
				Globals.HITCNT_ONLY=true;
				System.out.println("-y the gene hit count will immediately be updated");
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
	public static void printVersion() {
		String url = util.Jhtml.BASE_HELP_URL;
		String base = url.substring(0, url.length()-1);
		//String d = (Runtime.getRuntime().maxMemory() / (1024*1024)) + "m";
		System.out.println("\nSyMAP " + Globals.VERSION + Globals.DATE + "  " + base
			+ "\nJava v" + System.getProperty("java.version")); // CAS548 add 'Compiled', remove mem; CAS550 was Run, no compile 
	}
	
	public static boolean checkJavaSupported(Component frame) {	
		/** CAS42 10/16/17 - did not work for Java SE 9 **/
		int rMajor=1, rMinor=8;
		String rVersion = rMajor + "." + rMinor;
		String version = System.getProperty("java.version");
		
		if (version != null) {
			String[] subs = version.split("\\.");
			int major = 0;
			int minor = 0;

			if (subs.length > 0) major  = Integer.valueOf(subs[0]);
			if (subs.length > 1) minor  = Integer.valueOf(subs[1]);
			
			// Java version not less than 1.8
			if (major == rMajor && minor < rMinor) {
				System.err.println("Java version " + rVersion + " or later is required.");
				
				javax.swing.JOptionPane.showMessageDialog(null,
					    "The installed Java version is " + version + ".  \n"
					    + "SyMAP requires version " + rVersion + " or later.  \n"
					    + "Please visit http://java.com/download/ to upgrade.", "Java Incompatibility",
					    javax.swing.JOptionPane.ERROR_MESSAGE);
				return false;
			}
			else
				return true;
		}
		else 
			System.err.println("Could not determine Java version.");
		return true;
	}
}