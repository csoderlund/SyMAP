package backend;

import util.Utilities;

public class Constants {
////////////////////////////////////////////////////////
// CAS500 v5 moved hard-coded constants for build to here
public static final boolean TRACE = false;

public static final String cfgFile =    "symap.config";

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
public static final String syntenyLog = "symap.log"; 		// suffix for align log (p1_to_p2.log)

// default directories of data ('/' in front indicates after project name
public static final String dataDir =    		"data/"; // top level
public static final String fpcDataDir = 		"data/fpc/";
public static final String fpcFileSuffix = 	".fpc";
public static final String fpcBesDataDir =	"/bes/"; 
public static final String fpcMrkDataDir =	"/mrk/"; 

public static final String seqDataDir = 		 "data/seq/";
public static final String seqSeqDataDir = 	"/sequence/"; 
public static final String seqAnnoDataDir = 	"/annotation/"; 
public static final String paramsFile = 		"/params"; // in both fpcDataDir and seqDataDir

// directories for results
public static final String fpcRunDir =  		"data/fpc_results/"; 
public static final String seqRunDir =  		"data/seq_results/";

public static final String alignDir = 		"/align/";
public static final String mumSuffix = 		".mum";
public static final String blatSuffix = 		".blat";
public static final String doneSuffix = 		".done";
public static final String selfPrefix = 		"self.";
public static final String finalDir = 		"/final/";
public static final String anchorFile = 		"anchors.txt";
public static final String blockFile = 		"block.txt";

public static final String projTo = 			"_to_";
public static final String faFile =         ".fa";

// directory of temporary files written for mummer/blat
//under runDir/<p1>_to_<p2>/tmp/<px>/ file names <p>_bes,<p>_mrk,<p>_ff or <p>_f1...
//files in <p1> are aligned with files in <p2>. 
public static final String tmpRunDir = 		 "/tmp/"; 

// anchor draft
public static final String anchorSuffix 	=  "_anchored";
public static final String anchorCSVFile	=  "/anchoring.csv";

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
	 *  External programs (programs are hardcoded in .java)
	 */

	// sub-directories
	public static String getPlatformPath(String program, String plat) 
	{
		if (Utilities.isLinux()) 
		{
			if (program.equals("blat"))		return "/lintel/"; // no 64b blat version
			
			if (plat.equals("i386"))        	return "/lintel/";
			else if (plat.equals("x86_64")) 	return "/lintel64/";	
			else if (Utilities.is64Bit() )  	return "/lintel64/";
			else								return "/lintel/";
		}
		else if (Utilities.isMac())
		{
			return "/mac/"; 
		}
		else 
		{
			System.err.println("Unknown platform! Trying /lintel64/");
			return "/lintel64/";
		}
	}
	public static String getPlatformPath() {
		if (Utilities.isLinux()) 
		{
			if(Utilities.is64Bit()) 	return "/lintel64/";
			else 					return "/lintel/";
		}
		else if (Utilities.isMac())
		{
									return "/mac/";
		}
		else {
			System.err.println("Unknown platform! Trying /lintel64/");
			return "/lintel64/";
		}
	}
}
