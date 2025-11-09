package backend;

import backend.anchor1.Proj;
import symap.manager.Mpair;

public class Constants {
	
/**********************************************
 * Command line arguments
 */
// Set in SymapCE.SyMAPmanager
public static boolean WRONG_STRAND_PRT = false; // -wsp  print wrong strand hits for algo2 
public static boolean CoSET_ONLY = false;		// -scs on A&S ONLY execute AnchorPosts; not on -h, leave for possible updates
public static boolean MUM_NO_RM = false;		// -mum on A&S ONLY do not remove any mummer files; 
public static boolean NEW_BLOCK_COORDS = true; 	// -b save hit ends for block coords; not on -h, but still works

public static boolean VERBOSE = false; 			// -v on 

// Anchor1 constants; Anchor2 constants are in Anchor2.Arg
public static final int TARGET=0, QUERY=1, EITHER=2;
public static final String GeneGene="GeneGene";
public static final String GeneNonGene = "GeneNonGene";
public static final String NonGene = "NonGene";

public static final double FperBin=0.8; // HitBin.filter piles FperBin*matchLen
/*************************************************************/

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

// MacOS Sequoia will no longer easily open files if they do not end with .txt; checked in Utils.getParamFile
public static final String paramsFile = "/params.txt"; // in both seq/proj and seq_results/proj1_to_proj2
public static final String usedFile =	"/params_align_used.txt"; // seq_results 

//These file types, denoted by the .fas extension, are used by most large curated databases. 
//Specific extensions exist for nucleic acids (.fna), nucleotide coding regions (.ffn), amino acids (.faa), 
//and non-coding RNAs (.frn).
public static final String [] fastaFile = {".fa", ".fna", ".fas", ".fasta",  ".seq"}; 
public static final String fastaList =    ".fas, .fa, .fna, .fasta, .seq (options .gz)";
public static final String [] gffFile = {".gff", ".gff3"};
public static final String gffList  = ".gff, .gff3";

// directories for results
public static final String seqRunDir =  	"data/seq_results/";

public static final String alignDir = 		"/align/";
public static final String mumSuffix = 		".mum";
public static final String doneSuffix = 	".done";
public static final String selfPrefix = 	"self.";
public static final String finalDir = 		"/final/"; // has been discontinued; still exists in order to Remove

public static final String projTo = 		"_to_";
public static final String faFile =         ".fa";

public static final String orderSuffix =	"_ordered.csv";
public static final String orderDelim = 	"..";

// directory of temporary files written for mummer
//under runDir/<p1>_to_<p2>/tmp/<px>/ file names.
//files in <p1> are aligned with files in <p2>. 
public static final String tmpRunDir = 		 "/tmp/"; 

/*************************************************************************/
	public static boolean rtError(String msg) {
		System.out.println(msg);
		return false;
	}
	// default data directory
	public static String getNameDataDir(Proj p1) {
		String name = p1.getName();
		return seqDataDir + name;
	}
	public static  String getNameResultsDir() {
		return seqRunDir;
	}

	// Results NOTES: XXX 
	//   1. Cannot use "Project p" as argument as two Project.java
	//   2  see ProjectManagerFrameCommon.orderProjects
	public static String getNameResultsDir(String n1, String n2) {
		return seqRunDir +  n1 + projTo + n2;
	}
	
	public static  String getNameAlignDir(String n1, String n2) {
		return getNameResultsDir(n1,  n2) + alignDir;
	}
	
	public static String getNameAlignFile(String n1, String n2) {
		String tmp1 = n1.substring(0, n1.indexOf(Constants.faFile));
		String tmp2 = n2.substring(0, n2.indexOf(Constants.faFile));
		if (tmp1.equals(tmp2)) return selfPrefix + tmp1;
		return tmp1 + "." + tmp2;
	}
	// tmp preprocessed results; created in AlignName.java
	public static  String getNameTmpDir(String n1,  String n2) {
		return getNameResultsDir(n1,  n2) + tmpRunDir;
	}
	public static String getNameTmpPreDir(String n1,  String n2, String n) {
		return getNameResultsDir(n1, n2) + tmpRunDir + n;
	}	
	public static String getOrderDir(Mpair mp) { 
		if (mp.isOrder1(Mpair.FILE)) return mp.mProj1.getDBName() + Constants.orderDelim + mp.mProj2.getDBName();
		if (mp.isOrder2(Mpair.FILE)) return mp.mProj2.getDBName() + Constants.orderDelim + mp.mProj1.getDBName();
		return null;
	}
}
