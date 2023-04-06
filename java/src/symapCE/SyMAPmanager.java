package symapCE;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import backend.Constants;
import backend.Group;
import backend.Hit;
import database.DBconn;
import symap.Globals;
import symap.frame.ChrExpInit;
import symap.manager.Mproject;
import symap.manager.ManagerFrame;
import util.Utilities;
import util.ErrorReport;

/*********************************************
 * Called by symap and viewSymap scripts
 * ManagerFrame displays interface, SyMAPFrame calls showExplorer
 */

public class SyMAPmanager extends ManagerFrame {
	private static final long serialVersionUID = 1L;
	
	public static void main(String args[])  {	
		if (!checkJavaSupported(null)) return;
		
		if (hasCommandLineOption(args, "-h") || hasCommandLineOption(args, "-help") 
			|| hasCommandLineOption(args, "--h")) {
			
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
		
		explorerListener = new ActionListener() { // explorerListener in ManagerFrame
			public void actionPerformed(ActionEvent e) { 
				showExplorer();
			}
		};
	}
	// Called when Chromosome Explorer clicked in Manager
	private void showExplorer() {
		Utilities.setCursorBusy(this, true);
		try {
			DBconn dr = DBconn.getInstance(Globals.DB_CONN_SYMAP, dbc);
			
			ChrExpInit symapExp = new ChrExpInit(dr);
			
			for (Mproject p : selectedProjVec) 
				symapExp.addProject( p.getDBName() );
			symapExp.build();
			symapExp.getFrame().build();
			symapExp.getFrame().setVisible(true); 
		}
		catch (Exception err) {ErrorReport.print(err, "Show SyMAP graphical window");}
		finally {
			Utilities.setCursorBusy(this, false);
		}
	}
	/******************************************************************
	 * CAS511 the perl script was removed, so the parameters need to be written here
	 * CAS505 moved parse args to ManagerFrame
	 * CAS534 moved back as the 3D was removed, which was causing duplicate args
	 */
	public static void prtParams(String args[]) {
		if (hasCommandLineOption(args, "-r")) {
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
	
			System.out.println("\nReload Annotation:");
			System.out.println("  -z        : Reload Annotation will only run the Gene# assignment algorithm");
			
			System.out.println("\nSynteny&Alignment:");
			System.out.println("  -p N      : number of CPUs to use");
			System.out.println("  -s        : print stats for A&S, or recreate the Summary on display");
			
			if (hasCommandLineOption(args, "-s")) {
				System.out.println("  -ug N      : 0 ignore annot, 1 either annot, 2 both annot");
				System.out.println("  -nsg       : do not split genes");
			}
		}
	}
	// these are listed to terminal in the 'symap' perl script.
	private static void setParams(String args[]) { 
		if (args.length > 0) {
			if (hasCommandLineOption(args, "-r")) {// used by viewSymap
				inReadOnlyMode = true; // no message to terminal
			}
			if (hasCommandLineOption(args, "-v")) {// check version
				bCheckVersion = true; // no message to terminal
				System.out.println("-v Check version ");
			}
			if (hasCommandLineOption(args, "-p")) { // #CPU
				String x = getCommandLineOption(args, "-p"); //CAS500
				try {
					maxCPUs = Integer.parseInt(x);
					System.out.println("-p Max CPUs " + maxCPUs);
				}
				catch (Exception e){ System.err.println(x + " is not an integer. Ignoring.");}
			}
			
			if (hasCommandLineOption(args, "-c")) {// CAS501
				Globals.MAIN_PARAMS = getCommandLineOption(args, "-c");
				System.out.println("-c Configuration file " + Globals.MAIN_PARAMS);
			}
			if (hasCommandLineOption(args, "-a")) { // CAS531 change
				Globals.bTrim=false;
				System.out.println("-a Do not trim 2D alignments");
				//System.out.println("-a Align largest project to smallest");
				//lgProj1st = true;
			}
			
			if (hasCommandLineOption(args, "-z")) {// CAS519b
				Globals.GENEN_ONLY=true;
				System.out.println("-z Reload Annotatin will only run the Gene# assignment algorithm");
			}
			if (hasCommandLineOption(args, "-s")) {
				System.out.println("-s Print Stats");
				Constants.PRT_STATS = true;
			}
			
			/** not shown in -h help - hence, the double character so user does not use by mistake **/
			if (hasCommandLineOption(args, "-dd")) {
				System.out.println("-dd Debug (developer only)");// CAS533 changed to -dd 
				Globals.DEBUG = true;
				//Q.TEST_TRACE = true; // for query
			}
			if (hasCommandLineOption(args, "-dbd")) {
				System.out.println("-dbd Database (developer only)");
				Globals.DBDEBUG = true;
			}
			if (hasCommandLineOption(args, "-tt")) {
				System.out.println("-tt Trace output");
				Constants.TRACE = true; // in backend
				Globals.TRACE = true;   // CAS517; used to add info
			}
			if (hasCommandLineOption(args, "-nsg")) { // CAS540
				System.out.println("-nsg split genes !" + Group.bSplitGene);
				Group.bSplitGene= !Group.bSplitGene;
			}
			
			// old tests
			if (hasCommandLineOption(args, "-oo")) {// CAS505 not shown in -h help
				System.out.println("-oo Use the original version of draft ordering");
				Constants.NEW_ORDER = false;
			}
			if (hasCommandLineOption(args, "-aa")) { // CAS531 change
				System.out.println("-aa Align largest project to smallest");
				lgProj1st = true;
			}
			if (hasCommandLineOption(args, "-bb")) { // CAS533 change; orig used midpoints
				System.out.println("-bb Original block coordinates");
				Constants.NEW_BLOCK_COORDS = false;
			}
		}
	}
	private static boolean hasCommandLineOption(String[] args, String name) {// CAS534 moved from Utilites
		for (int i = 0;  i < args.length;  i++)
			if (args[i].startsWith(name)) 
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
		String d = (Runtime.getRuntime().maxMemory() / (1024*1024)) + "m";
		System.out.println("\nSyMAP " + Globals.VERSION + Globals.DATE + "   " + base
			+ "   Java v" + System.getProperty("java.version") + " (" + d + ")");
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