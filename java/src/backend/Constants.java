package backend;

import java.io.File;

import util.ErrorReport;

public class Constants {
////////////////////////////////////////////////////////
// CAS500 v5 moved hard-coded constants for build to here

/**********************************************
 * Command line arguments
 */
public static boolean TRACE =     false;
public static boolean NEW_ORDER = true;
public static boolean PRT_STATS=  false; 

/*************************************************************/

// getPlatformPath() gets subdirectory under /ext
// ProgSpec.java doAlignments() gets next subdirectory, with mummer and blat stuff hardcoded
public static final String extDir = 		"ext/";		// directory of external programs

// types
public static final String fpcType = 	"fpc";
public static final String seqType = 	"seq";    	// directory name
public static final String dbSeqType = 	"pseudo"; 	// name in database
public static final String geneType = 	"gene";	  	// is this used?
public static final String besType = 	"bes";	  	// sub-type of fpc
public static final String mrkType = 	"mrk";		// sub-type of fpc

public static final int CHUNK_SIZE = 1000000;
public static final double perBin = 0.8; // keep hits within 80% score of the topN hit

// Write to logs:  	ProgSpec for program alignment output
//					ProjectManagerFrameCommon.buildLog for SyMAP alignment output
public static final String logDir =     "logs/";     // in symap directory
public static final String loadLog =    "LOAD.log";  // suffix for load log  (symap_load.log)
public static final String syntenyLog = "symap.log"; // suffix for align log (p1_to_p2.log)

// default directories of data ('/' in front indicates after project name
public static final String dataDir =    	"data/"; // top level
public static final String fpcDataDir = 	"data/fpc/";
public static final String fpcFileSuffix = 	".fpc";
public static final String fpcBesDataDir =	"/bes/"; 
public static final String fpcMrkDataDir =	"/mrk/"; 

public static final String seqDataDir = 	"data/seq/";
public static final String seqSeqDataDir = 	"/sequence/"; 
public static final String seqAnnoDataDir = "/annotation/"; 
public static final String paramsFile = 	"/params"; // in both fpcDataDir and seqDataDir

// directories for results
public static final String fpcRunDir =  	"data/fpc_results/"; 
public static final String seqRunDir =  	"data/seq_results/";

public static final String alignDir = 		"/align/";
public static final String mumSuffix = 		".mum";
public static final String blatSuffix = 	".blat";
public static final String doneSuffix = 	".done";
public static final String selfPrefix = 	"self.";
public static final String finalDir = 		"/final/";
public static final String anchorFile = 	"anchors.txt";
public static final String blockFile = 		"block.txt";

public static final String projTo = 		"_to_";
public static final String faFile =         ".fa";

// directory of temporary files written for mummer/blat
//under runDir/<p1>_to_<p2>/tmp/<px>/ file names <p>_bes,<p>_mrk,<p>_ff or <p>_f1...
//files in <p1> are aligned with files in <p2>. 
public static final String tmpRunDir = 		 "/tmp/"; 

//CAS508 can put mummer4 in symap.config
private static String mummer4Path=null; 

/*************************************************************************/
	// default data directory
	public  static String getNameDataDir(Project p1) {
		String name = p1.getName();
		String typeDir = (p1.isFPC()) ? fpcDataDir : seqDataDir;
		return typeDir + name;
	}
	public  static  String getNameResultsDir(boolean isFPC) {
		if (isFPC) return fpcRunDir;
		else return seqRunDir;
	}

	// Results NOTES: XXX 
	//   1. Cannot use "Project p" as argument as two Project.java
	//   2. If there is a FPC and Seq directory, p1=fpc and p2=seq; see ProjectManagerFrameCommon.orderProjects
	//   3. If FileType or ProjType are used, the Call Hierachy does not work
	public  static String getNameResultsDir(String n1, boolean isFPC, String n2) {
		String name = n1 + projTo + n2;
		String typeDir = (isFPC) ? fpcRunDir : seqRunDir;
		return typeDir + name;
	}
	
	public  static  String getNameAlignDir(String n1, boolean isFPC, String n2) {
		return getNameResultsDir(n1, isFPC, n2) + alignDir;
	}
	
	public  static String getNameAlignFile(String n1, String n2) {
		String tmp1 = n1.substring(0, n1.indexOf(Constants.faFile));
		String tmp2 = n2.substring(0, n2.indexOf(Constants.faFile));
		if (tmp1.equals(tmp2)) return selfPrefix + tmp1;
		return tmp1 + "." + tmp2;
	}
	// tmp preprocessed results; created in AlignName.java
	public  static  String getNameTmpDir(String n1, boolean isFPC, String n2) {
		return getNameResultsDir(n1, isFPC, n2) + tmpRunDir;
	}
	public  static String getNameTmpPreDir(String n1, boolean isFPC, String n2, String n) {
		return getNameResultsDir(n1, isFPC, n2) + tmpRunDir + n;
	}	
	/**************************************************************
	 *  CAS508 gathered all paths methods here so in one place; add mummer4 stuff
	 */
	public static void setMummer4Path(String mummer) {
		mummer4Path=mummer;
		// /m4  /m4/  /m4/bin  /m4/bin/
		if (!mummer4Path.endsWith("/bin")) mummer4Path += "/bin";
		if (!mummer4Path.endsWith("/")) mummer4Path += "/";
		
		checkDir(mummer4Path);
		checkFile(mummer4Path+ "nucmer");
		checkFile(mummer4Path+ "promer");
		checkFile(mummer4Path+ "show-coords");
	}
	public static boolean isMummerPath() {return mummer4Path!=null;}
	
	public static String getProgramPath(String program) { // mummer or blat
		if (program.contentEquals("mummer") && mummer4Path!=null) { // either set in symap.config
			return mummer4Path;
		}
		String path = Constants.extDir + program;
		if (!checkDir(path)) return "";
		
		path += getPlatformPath();
		if (!checkDir(path)) return "";
		
		return path; // as absolute
	}
	
	public static String getPlatformPath() {
		String plat =  System.getProperty("os.name").toLowerCase();
		
		if (plat.contains("linux")) {
			if (is64()) return "/lintel64/";
			else 		return "/lintel/";
		}
		else if (plat.contains("mac")) {
			return "/mac/";
		}
		else {
			System.err.println("Unknown platform! Trying /lintel64/");
			return "/lintel64/";
		}
	}
	public static boolean is64() {
		return System.getProperty("os.arch").toLowerCase().contains("64");
	}
	public static String getPlatform() {
		return  System.getProperty("os.name") + ":" + System.getProperty("os.arch");
	}
	/**************************************************************
	 * Check ext directory when database is created or opened
	 * .. not checking blat since that is probably obsolete
	 */
	static public void checkExt() { // only called with database is created
		try {
			if (mummer4Path!=null) return; // already checked
			
			System.err.println("\nCheck external programs " + Constants.getPlatform());
			
			String exDir = Constants.extDir;
			if (!checkDir(exDir)) {
				System.err.println("       Will not be able to run MUMmer from SyMAP");
				return;
			}
			checkMUMmer();
			
			String apath = exDir + "muscle" + getPlatformPath();
			if (!checkDir(apath)) {
				System.err.println("   Will not be able to run MUSCLE from SyMAP Queries");
				return;
			}
			checkFile(apath+ "muscle");
			
			System.out.println("Check complete\n");
		}
		catch (Exception e) {ErrorReport.print(e, "Checking executables");	}
	}
	static private boolean checkMUMmer() {
		String mpath = getProgramPath("mummer");
		if (!checkDir(mpath)) {
			System.err.println("   Will not be able to run MUMmer from SyMAP");
			return false;
		}
		checkFile(mpath+ "mummer");
		checkFile(mpath+ "nucmer");
		checkFile(mpath+ "promer");
		checkFile(mpath+ "show-coords");
		if (mummer4Path==null) { // using supplied mummer v3
			checkFile(mpath+ "mgaps");
			checkDir(mpath+"aux_bin");
			checkDir(mpath+"scripts");
		}
		return true;
	}
	static private boolean checkDir(String dirName) {
		try {
			//System.out.println("   Check " + dirName);
			File dir = new File(dirName);

			if (!dir.exists()) {
				System.err.println("***Error - directory does not exists: " + dirName);
				return false;
			}
			if (!dir.isDirectory()) {
				System.err.println("***Error - is not a directory: " + dirName);
				return false;
			}
			if (!dir.canRead()) {
				System.err.println("***Error - directory is not readable: " + dirName);
				return false;
			}
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, "Checking directory " + dirName);	return false;}
	}
	static private boolean checkFile(String fileName) {
		try {
			File file = new File(fileName);

			if (!file.exists()) {
				System.err.println("***Error - file does not exists: " + fileName);
				return false;
			}
			if (!file.isFile()) {
				System.err.println("***Error - is not a file: " + fileName);
				return false;
			}
			if (!file.canExecute()) {
				System.err.println("***Error - file is not executable: " + fileName);
				return false;
			}
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, "Checking file " + fileName);	return false;}
	}
}
