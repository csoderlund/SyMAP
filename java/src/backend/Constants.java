package backend;

import java.io.File;

import backend.anchor1.Proj;
import util.ErrorReport;
import util.Jhtml;

public class Constants {
/***********************************************************
// CAS500 v5 moved hard-coded constants for build to here
// CAS522 removed FPC
// AnchorsMain: enum HitStatus, QueryType, HitType, GeneType
// CAS557 some constants are used by toSymap
*****************************************************/
	
/**********************************************
 * Command line arguments
 */
// Set in SymapCE.SyMAPmanager
public static boolean TRACE =     false; 		// -tt not shown in help
public static boolean NEW_BLOCK_COORDS = true; 	// -b save hit ends for block coords
public static boolean PRT_STATS=  false; 		// -s
public static boolean WRONG_STRAND_EXC = false; // -wse  exclude wrong strand hits for algo2 
public static boolean WRONG_STRAND_PRT = false; // -wsp  print wrong strand hits for algo2 
public static boolean CoSET_ONLY = false;		// -scs on A&S ONLY execute AnchorPosts
public static boolean MUM_NO_RM = false;		// -mum on A&S ONLY do not remove any mummer files; CAS559 add

// Anchor1 constants; Anchor2 constants are in Anchor2.Arg
// CAS546 when anchor1 classes were moved to backend.anchor1, enums broke so make static here
public static final int TARGET=0, QUERY=1, EITHER=2;
public static final String GeneGene="GeneGene";
public static final String GeneNonGene = "GeneNonGene";
public static final String NonGene = "NonGene";

public static final double FperBin=0.8; // HitBin.filter piles FperBin*matchLen
/*************************************************************/

// getPlatformPath() gets subdirectory under /ext
// ProgSpec.java doAlignments() gets next subdirectory, with mummer and blat stuff hardcoded
public static final String extDir = 		"ext/";		// directory of external programs

// types
public static final String seqType = 	"seq";    	// directory name
public static final String dbSeqType = 	"pseudo"; 	// name in database
public static final String geneType = 	"gene";	  	// is this used?

public static final int CHUNK_SIZE = 1000000;

// Write to logs:  	ProgSpec for program alignment output
//					ProjectManagerFrameCommon.buildLog for SyMAP alignment output
public static final String logDir =     "logs/";     // in symap directory
public static final String loadLog =    "load.log";  // suffix for load log  (symap_load.log)
public static final String syntenyLog = "symap.log"; // suffix for align log (p1_to_p2.log)

// default directories of data ('/' in front indicates after project name
// these are repeated as constants in xToSymap - don't change
public static final String dataDir =    	"data/"; // top level
public static final String seqDataDir = 	"data/seq/";
public static final String seqSeqDataDir = 	"/sequence/"; 
public static final String seqAnnoDataDir = "/annotation/"; 

public static final String paramsFile = 	"/params"; // in both seq/proj and seq_results/proj1_to_proj2

//These file types, denoted by the .fas extension, are used by most large curated databases. 
//Specific extensions exist for nucleic acids (.fna), nucleotide coding regions (.ffn), amino acids (.faa), 
//and non-coding RNAs (.frn).
public static final String [] fastaFile = {".fa", ".fna", ".fas", ".fasta",  ".seq"}; // CAS557 add
public static final String fastaList =    ".fas, .fa, .fna, .fasta, .seq (options .gz)";
public static final String [] gffFile = {".gff", ".gff3"};
public static final String gffList  = ".gff, .gff3";

// directories for results
public static final String seqRunDir =  	"data/seq_results/";

public static final String alignDir = 		"/align/";
public static final String mumSuffix = 		".mum";
public static final String doneSuffix = 	".done";
public static final String selfPrefix = 	"self.";
public static final String finalDir = 		"/final/";
public static final String anchorFile = 	"anchors.txt";
public static final String blockFile = 		"block.txt";

public static final String projTo = 		"_to_";
public static final String faFile =         ".fa";

// directory of temporary files written for mummer
//under runDir/<p1>_to_<p2>/tmp/<px>/ file names.
//files in <p1> are aligned with files in <p2>. 
public static final String tmpRunDir = 		 "/tmp/"; 

//CAS508 can put mummer4 in symap.config
private static String mummer4Path=null; 

/*************************************************************************/
	public static boolean rtError(String msg) {
		System.out.println(msg);
		return false;
	}
	// default data directory
	public  static String getNameDataDir(Proj p1) {
		String name = p1.getName();
		return seqDataDir + name;
	}
	public  static  String getNameResultsDir() {
		return seqRunDir;
	}

	// Results NOTES: XXX 
	//   1. Cannot use "Project p" as argument as two Project.java
	//   2  see ProjectManagerFrameCommon.orderProjects
	public  static String getNameResultsDir(String n1, String n2) {
		return seqRunDir +  n1 + projTo + n2;
	}
	
	public  static  String getNameAlignDir(String n1, String n2) {
		return getNameResultsDir(n1,  n2) + alignDir;
	}
	
	public  static String getNameAlignFile(String n1, String n2) {
		String tmp1 = n1.substring(0, n1.indexOf(Constants.faFile));
		String tmp2 = n2.substring(0, n2.indexOf(Constants.faFile));
		if (tmp1.equals(tmp2)) return selfPrefix + tmp1;
		return tmp1 + "." + tmp2;
	}
	// tmp preprocessed results; created in AlignName.java
	public  static  String getNameTmpDir(String n1,  String n2) {
		return getNameResultsDir(n1,  n2) + tmpRunDir;
	}
	public  static String getNameTmpPreDir(String n1,  String n2, String n) {
		return getNameResultsDir(n1, n2) + tmpRunDir + n;
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
	public static boolean isMac() {
		return System.getProperty("os.name").toLowerCase().contains("mac");
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
			
			System.err.println("Check external programs " + Constants.getPlatform());
			
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
		
		if (mummer4Path==null) { // using supplied mummer v3; v4 already checked, but to be sure...
			checkFile(mpath+ "mgaps");
			checkDir(mpath+ "aux_bin");
			checkFile(mpath+ "aux_bin/prepro"); 
			checkFile(mpath+ "aux_bin/postpro"); // CAS559 only prepro was being checked
			checkFile(mpath+ "aux_bin/prenuc");
			checkFile(mpath+ "aux_bin/postnuc");
			checkDir(mpath+"scripts");
		}
		if (isMac()) { // CAS559 add this message
			String m = 	"  On Mac, MUMmer executables needs to be verified for 1st time use; \n    see ";
			String u = Jhtml.TROUBLE_GUIDE_URL + Jhtml.macVerify;
			System.out.println(m+u);
		}
		return true;
	}
	static private boolean checkDir(String dirName) {
		try {
			//System.out.println("   Check " + dirName);
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
	static private boolean checkFile(String fileName) {
		try {
			if (symap.Globals.DEBUG) System.out.println("Check " + fileName);
			
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
	/**********************************************************
	 * CAS534 added for Params
	 ******************************************************/
	static public File getProjParamsFile(String projName) {
		return new File(seqDataDir+projName+paramsFile);
	}
	static public String getProjParamsName(String projName) {
		return seqDataDir+projName+paramsFile;
	}
}
