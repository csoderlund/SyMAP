package symap;

import java.io.File;

import util.ErrorReport;
import util.Jhtml;

/*****************************************************
 * This file checks what architecture and what /ext subdirectories to use.
 * extSubDir is set at startup, and cannot be changed; hence, it is okay these variables are static
 * CAS566 moved from backend.Constants
 * extDir + dirX + archDir 
 */
public class Ext {
	private static final String extDir = "ext/"; // directory of external programs
	private static final String dirMummer="mummer/", dirMuscle="muscle/", dirMafft="mafft/";
	private static String archDir;				 // set on startup (linux64, mac, macM4); user can set in symap.config; CAS566 
	
	private static String mummer4Path=null; 	 // can put full mummer4 path in symap.config; 
	
	public  static final String exNucmer="nucmer", exPromer="promer", exCoords="show-coords"; // accessed in AlignMain
	private static final String exMuscle="muscle", exMafft="mafft.bat";
			 	
	public static String getPlatform() {
		return  System.getProperty("os.name") + ":" + System.getProperty("os.arch");
	}
	/**************************************************************
	 *  SyMAPmanager reading config; can physically set; otherwise, will calculate
	 */
	public static boolean setArch(String userArch) {
		if (userArch!=null) {
			archDir = userArch;
			if (!archDir.endsWith("/")) archDir = archDir + "/";
			return checkMUMmer();
		}
		String plat =  System.getProperty("os.name").toLowerCase();
		
		if (plat.contains("linux")) {
			archDir = "lintel64/";	// CAS568 was linux64 from v567 when cleaning some code
			return true;
		}
		if (!plat.contains("mac")) { 
			System.err.println("The os.name is not lintel64 or Mac OS X");
			System.err.println("There are no executables for your machine");
			System.err.println("See " + Jhtml.SYS_GUIDE_URL + Jhtml.ext);
			return false;
		}
		// Mac OS X
		String arch =  System.getProperty("os.arch").toLowerCase();
		if (arch.equals("aarch64")) {
			archDir = "macM4/";		// checked in isMacM4
			return true;
		}
		if (arch.equals("x86_64")) {
			archDir = "mac/";
			return true;
		}
		System.err.println("The Mac OS X architecture is not x86_64 or aarch64");
		System.err.println("There are no executables for your machine");
		System.err.println("See " + Jhtml.SYS_GUIDE_URL + Jhtml.ext);
		return false;
	}
	/**************************************************************
	 *  SyMAPmanager reading config; add mummer4 stuff, which can be set in config file; 
	 */
	public static void setMummer4Path(String mummer) {
		mummer4Path=mummer;
		// /m4  /m4/  /m4/bin  /m4/bin/
		if (!mummer4Path.endsWith("/bin")) mummer4Path += "/bin";
		if (!mummer4Path.endsWith("/")) mummer4Path += "/";
		
		checkDir(mummer4Path);
		checkFile(mummer4Path+ exNucmer);
		checkFile(mummer4Path+ exPromer);
		checkFile(mummer4Path+ exCoords);
	}
	/***********************************************************************************/
	public static String getMuscleCmd() {
		String cmd = extDir + dirMuscle + archDir + exMuscle;
		if (!checkFile(cmd)) return null;
		return cmd;
	}
	public static String getMafftCmd()  {
		String cmd = extDir + dirMafft + archDir + exMafft;
		if (!checkFile(cmd)) return null;
		return cmd;
	}
	
	public static boolean isMummer4Path() {return mummer4Path!=null;}
	public static String getMummerPath() { // mummer 
		if (mummer4Path!=null) return mummer4Path;
		
		String path = extDir + dirMummer + archDir;
		if (!checkDir(path)) return "";
		
		return path; 
	}
	/***********************************************************************************/
	public static boolean isMac() {
		return System.getProperty("os.name").toLowerCase().contains("mac");
	}
	public static boolean isMacM4() {
		return archDir.equals("macM4/");
	}
	/**************************************************************
	 * Check ext directory when database is created or opened
	 * .. not checking blat since that is probably obsolete
	 */
	static public void checkExt() { // only called with database is created
		try {
			if (mummer4Path!=null) return; // already checked
			
			System.err.println("Check external programs in " + extDir + " for " + archDir);
			
			if (!checkDir(extDir)) {
				System.err.println("No " + extDir + " directory.  Will not be able to run any external programs.");
				return;
			}
			checkMUMmer();
			
			if (getMafftCmd()==null) {
				System.err.println("   Will not be able to run MAFFT from SyMAP Queries");
				return;
			}
			
			if (getMuscleCmd()==null) {
				System.err.println("   Will not be able to run MUSCLE from SyMAP Queries");
				return;
			}
			
			if (isMac()) { // CAS559 add this message
				String m = 	"  On Mac, MUMmer executables needs to be verified for 1st time use; \n    see ";
				String u = Jhtml.TROUBLE_GUIDE_URL + Jhtml.macVerify;
				System.out.println(m+u);
			}
			System.out.println("Check complete\n");
		}
		catch (Exception e) {ErrorReport.print(e, "Checking executables");	}
	}
	// more mummer hard-coded in AlignRun.cleanup
	static private boolean checkMUMmer() {
		String mpath = getMummerPath();
		if (!checkDir(mpath)) {
			System.err.println("   Will not be able to run MUMmer from SyMAP");
			return false;
		}
		checkFile(mpath+ "mummer");
		checkFile(mpath+ exNucmer);
		checkFile(mpath+ exPromer);
		checkFile(mpath+ exCoords);
		
		if (mummer4Path==null) { // using supplied mummer v3; v4 already checked, but to be sure...
			checkFile(mpath+ "mgaps");
			checkDir(mpath+ "aux_bin");
			checkFile(mpath+ "aux_bin/prepro"); 
			checkFile(mpath+ "aux_bin/postpro"); // CAS559 only prepro was being checked
			checkFile(mpath+ "aux_bin/prenuc");
			checkFile(mpath+ "aux_bin/postnuc");
			checkDir(mpath+"scripts");
		}
	
		return true;
	}
	/**********************************************************/
	static private boolean checkFile(String fileName) {
		try {
			File file = new File(fileName);

			if (!file.exists()) {
				System.err.println("*** file does not exists: " + fileName);
				return false;
			}
			if (!file.isFile()) {
				System.err.println("*** is not a file: " + fileName);
				return false;
			}
			if (!file.canExecute()) {
				System.err.println("*** file is not executable: " + fileName);
				return false;
			}
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, "Checking file " + fileName);	return false;}
	}
	static private boolean checkDir(String dirName) {
		try {
			File dir = new File(dirName);

			if (!dir.exists()) {
				System.err.println("*** directory does not exists: " + dirName);
				return false;
			}
			if (!dir.isDirectory()) {
				System.err.println("*** is not a directory: " + dirName);
				return false;
			}
			if (!dir.canRead()) {
				System.err.println("*** directory is not readable: " + dirName);
				return false;
			}
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, "Checking directory " + dirName);	return false;}
	}
}
