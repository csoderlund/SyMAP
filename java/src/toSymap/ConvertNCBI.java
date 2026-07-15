package toSymap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.zip.GZIPInputStream;

import util.ErrorReport;

import java.util.TreeMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*************************************************************
 * ConvertNCBI: NCBI genome files formatted for SyMAP
 * See https://csoderlund.github.io/SyMAP/input/ncbi.html
 * 
 * Called from xToSymap, but can be used stand-alone (see /scripts directory).
 * 
 * This assumes input of the project directory, which has either
 * 1. The ncbi_dataset.zip unzipped, .e.g 
 *    /data/seq/Arab/ncbi_dataset/data/GCF_000001735.4
 * or the .fna and .gff files directly under the project directory
 * It only expects one of each file since NCBI does not supply the files by chromosome 
 *        
 * Note: I am no expert on NCBI GFF format, so I cannot guarantee this is 100% correct mapping for all input.
 * Your sequences/annotations may have other variations.
 * Hence, you may want to edit this to customize it for your needs -- its simply written so easy to modify.
 * See https://csoderlund.github.io/SyMAP/input/ncbi.html#edit
 * 
 * From NCBI book and confirm by https://ftp.ncbi.nlm.nih.gov/refseq/release/release-notes/RefSeq-release225.txt
 * AC_	Genomic	Complete genomic molecule, usually alternate assembly (or linkage)
 * NC_	Genomic	Complete genomic molecule, usually reference assembly (or linkage) - use sequence unless Prefix only disqualifies
 * NG_	Genomic	Incomplete genomic region
 * NT_	Genomic	Contig or scaffold, clone-based or WGS			- use sequence if scaffold
 * NW_	Genomic	Contig or scaffold, primarily WGS  				- use sequence if scaffold
 * NZ_	Genomic	Complete genomes and unfinished WGS data
 * 
   Gene attributes written: ID=geneID; Name=(if not contained in geneID); 
   			rnaID= longest mRNA ID (cnt of number of mRNAs)
   			desc=gene description or longest mRNA product
   			
   CAS586: add Version on header; create classes; output longest mRNA; include All seqs; remove Protein-id
 */

public class ConvertNCBI {
	static private boolean isToSymap=true; // false if standalone
	static private boolean bTrace=xToSymap.isDebug; // -d
	static private int defGapLen=30000;
	
	private final String header = "### Written by " + symap.Globals.toNCBI // SeqLoadMain && Split expects this to be 1st line
			+" " +  symap.Globals.VERSION + " on " + util.Utilities.getDateOnly(); 
	
	private String logFileName = "/xConvertNCBI.log";
	private String diffFileName = "/xDiffNCBI.log";
	
	// downloaded via NCBI Dataset link
	private final String ncbi_dataset 	= "ncbi_dataset"; 
	private final String ncbi_data 		= "/data"; 
	private final String faSuffix 		= ".fna";
	private final String gffSuffix 		= ".gff";
	private String subDir=null;
		
	//output - the projDir gets appended to the front
	private String seqDir 			= "sequence";		// Default sequence directory for symap
	private String annoDir 			= "annotation";   // Default annotation directory for symap
	private final String outFaFile 	= "/genomic.fna"; // Split expects this name
	private final String outGffFile = "/anno.gff";	  // Split expects this name
	private final String outGapFile = "/gap.gff";
			
	// args (can be set from command line)
	private boolean INCLUDEAll  = false;
	private boolean INCLUDESCAF = false;
	private boolean INCLUDEMtPt = false; 
	private boolean MASKED 		= false;
	private boolean VERBOSE 	= false;
					
	// args  (if running standalone, change in main)
	private int gapMinLen;          // Print to gap.gff if #N's is greater than this
	private String prefixOnly=null; // e.g. NC
	private int traceNum=5;	// if verbose, change to 20
	
	// Changed for specific input; if unknown and prefixOnly, no suffix
	private String chrPrefix="Chr", chrPrefix2="C", chrType="chromosome";	// These are checked in Split
	private String scafPrefix="s",  scafType="scaffold";  	// single letter is used because the "Chr" is usually removed to save space
	private String unkPrefix="Seq", unkType="unknown";		// CAS586 changed Unk to Seq
	
	// input 
	private String projDir = null;
	private String inFaFile = null;
	private String inGffFile = null;
	
	private PrintWriter fhOut, ghOut;
	private PrintWriter logFile=null, diffFile=null;
	
	// Other global variables
	private TreeMap <String, String> chr2id = new TreeMap <String, String> ();
	private TreeMap <String, String> id2chr = new TreeMap <String, String> ();
	private TreeMap <String, String> id2scaf = new TreeMap <String, String> ();
	private TreeMap <String, Integer> cntChrGene = 	new TreeMap <String, Integer> ();
	private TreeMap <String, Integer> cntScafGene = new TreeMap <String, Integer> ();
	
	private FASTA faObj;
	private GFF gffObj;
	private Init initObj;
				  	
	private boolean bSuccess=true;
	private final int PRT = 10000;
	
	/********************************************************************/
	// main: stand-alone; gaplen needs to be changed below; args are -s (scaffold) -t (MT) -v (verbose) -m (hard mask)
	public static void main(String[] args) { 
		isToSymap=false;
		new ConvertNCBI(args, defGapLen, null);
	}
	protected ConvertNCBI(String [] args, int gapLen, String prefix) {
		initObj = new Init();
		initObj.printArgs(args); // standalone only; exits if none or -h
		
		projDir = args[0];
		gapMinLen = gapLen;
		prefixOnly = prefix;
		
		prt("\n------ ConvertNCBI " + projDir +  " on " + util.Utilities.getDateOnly() + " ------");
		
		if (!initObj.checkInitFiles() || !bSuccess) return;
		initObj.createLog(args); if (!bSuccess) return;
		initObj.checkArgs(args); if (!bSuccess) return; // writes args to log
		
		faObj = new FASTA();	if (!bSuccess) return;
		
		gffObj = new GFF();		if (!bSuccess) return;
		
		printSuggestions();
		
		prt("------ Finish ConvertNCBI " + projDir + " ------");
		if (logFile!=null) logFile.close();
	}
	/***********************************************************************/
	private void printSuggestions() {
		prt("");
		boolean isChr = true;
		if (chr2id.size()>0) {
			for (String c : chr2id.keySet()) {
				if (!c.startsWith(chrPrefix)) {
					isChr=false;
					break;
				}
			}
			if (isChr && !INCLUDESCAF) prt("Suggestion: Set SyMAP project parameter 'Group prefix' to 'Chr'.");
		}
		
		if (faObj.cntOutSeq==0) prt("Are these NCBI files? Should scaffolds be included? Should 'Prefix only' be set?");
		else 
			if (faObj.cntOutSeq>30)  prt( "Suggestion: There are " + String.format("%,d",faObj.cntOutSeq) + " sequences. "
				+ "Set SyMAP project parameter 'Minimum length' to reduce number loaded.");
	}
	
	/*******************************************************/
	 private BufferedReader openGZIP(String file) {
			try {
				if (!file.endsWith(".gz")) {
					File f = new File (file);
					if (f.exists()) return new BufferedReader (new FileReader (f));
					else fail("Cannot open file " + file);
				}
				else if (file.endsWith(".gz")) {
					FileInputStream fin = new FileInputStream(file);
					GZIPInputStream gzis = new GZIPInputStream(fin);
					InputStreamReader xover = new InputStreamReader(gzis);
					return new BufferedReader(xover);
				}
				else fail("Do not recognize file suffix: " + file);
			}
			catch (Exception e) {fail(e, "Cannot open file " + file);}
			return null;
	 }
	 private boolean fail(Exception e, String msg) {
		System.err.println("Fatal error -- " + msg);
		e.printStackTrace();
		if (!isToSymap) System.exit(-1);
		bSuccess = false;
		return false;
	}
	 private boolean fail(String msg) {
		System.err.println("Fatal error -- " + msg);
		if (!isToSymap) System.exit(-1);
		bSuccess = false;
		return false;
	}
	 private void prt(String msg) {
		System.out.println(msg);
		if (logFile!=null) logFile.println(msg);
	}
	
	/***********************************************************/
	 private class Init {
		 private Init() {}
		 
		 private void printArgs(String [] args) {
			 if (args.length==0 || args[0].equals("-h") || args[0].equals("-help") || args[0].equals("help")) { // for stand alone
				prt("\nConvertNCBI <project directory> [options] 				");
				prt("   the project directory must contain the FASTA file and the GFF is optional: " +
						        "\n       FASTA file ending with .fna or .fna.gz" +
						        "\n       GFF   file ending with .gff or .gff.gz" +  
						        "\n       Alternatively, the directory can contain the ncbi_dataset directory." +
						        "\nOptions:" +
						        "\n-m  assuming a soft-masked genome file, convert it to hard-masked." +
						        "\n-a  include all sequences'." +
						        "\n-s  include any sequence with NT_ or NW_ prefix'." +
						        "\n-t  include Mt and Pt chromosomes." +
						        "\n-v  write extra information." +
						        "\nNote: change gap length and prefix in main()\n" +
								"\n\nSee https://csoderlund.github.io/SyMAP/input for details.");
				System.exit(0);
			} 
		 }
		 private void checkArgs(String [] args) {
			prt("Parameters:");
			prt("   Project directory: " + projDir);
			
			if (gapMinLen!=defGapLen) prt("   Gap minimum size: " + gapMinLen); // set from Main or ToFrame
			if (prefixOnly!=null)     prt("   Prefix Only: " + prefixOnly); 
			
			if (args.length>1) { // flags set at command line or in ToFrame
				for (int i=1; i< args.length; i++)
					if (args[i].equals("-s")) {
						INCLUDESCAF=true;
						chrPrefix = chrPrefix2;
					}
					else if (args[i].equals("-a")) INCLUDEAll=true;
					else if (args[i].equals("-t")) INCLUDEMtPt=true;
					else if (args[i].equals("-v")) VERBOSE=true;
					else if (args[i].equals("-m")) MASKED=true;
			}
			if (INCLUDEAll) prt("   Include all sequences");
			if (INCLUDESCAF) {
				prt("   Include scaffold sequences ('NT_' or 'NW_' prefix)");
				prt("      Uses prefixes Chr '" + chrPrefix2 + "' and Scaffold '" + scafPrefix + "'");
			}
			if (INCLUDEMtPt)  	prt("   Include Mt and Pt chromosomes");
			if (MASKED)  		prt("   Hard mask sequence");
			if (VERBOSE) 		{traceNum=20; prt("   Verbose");}
		}
		private void deleteFilesInDir(boolean isSeq, File dir) { 
			 if (!dir.isDirectory()) return;
			 
		     String[] files = dir.list();
		        
		     if (files==null) return;
		     
		     for (String fn : files) {
		    	 if (isSeq) {
		    		 if (fn.endsWith(".fna") || fn.endsWith(".fa")) {
		    			 new File(dir, fn).delete();
		    		 }
		    	 }
		    	 else {
		    		 if (fn.endsWith(".gff") || fn.endsWith(".gff3")) {
		    			 new File(dir, fn).delete();
		    		 }
		    	 } 
		     }
	    }
		/**************************************************************************
		  * Find .fna and .gff in top project directory or in projDir/ncbi_dataset/data/<dir>
		  */
		 private boolean checkInitFiles() {
			try {
				if (projDir==null || projDir.trim().equals("")) {
					fail("No project directory");
					return false;
				}
				File[] files;
				String dsDirName=null;
				
				File dir = new File(projDir);
				if (!dir.isDirectory()) 
					return fail(projDir + " is not a directory.");
				
				// for check projDir/ncbi_dataset/data/<dir>  
				files = dir.listFiles();
				boolean isDS=false;
				for (File f : files) {
					if (f.isDirectory()) {
						if (f.getName().contentEquals(ncbi_dataset)) {
							isDS = true;
							break;
						}
					}
				}
				if (isDS) {
					if (!projDir.endsWith("/")) projDir += "/";
					dsDirName = projDir + ncbi_dataset + ncbi_data;
						
					dir = new File(dsDirName);
					if (!dir.isDirectory()) 
						return fail("ncbi_dataset directory is incorrect " + dsDirName);
					
					files = dir.listFiles();
					for (File f : files) {
						if (f.isDirectory()) {
							subDir = f.getName();
							break;
						}
					}
					if (subDir==null) return fail(dsDirName + " missing sub-directory");
					
					dsDirName += "/" + subDir;
				}
				else {
					dsDirName = projDir;
				}
				
				// find .fna and .gff files in dsDirName
				File dsDir = new File(dsDirName);
				if (!dsDir.isDirectory()) 
					return fail(dsDir + " is not a directory.");
				
				files = dsDir.listFiles();
				for (File f : files) {
					if (!f.isFile() || f.isHidden()) continue;
					
			       String fname = f.getName();
			       
			       if (fname.endsWith(faSuffix + ".gz") || fname.endsWith(faSuffix)) 		{
			    	   if (inFaFile!=null) {
			    		   prt("Multiple fasta files - using " + inFaFile);
			    		   break;
			    	   }
			    	   inFaFile = dsDirName + "/" + fname;
			       }
			       else if (fname.endsWith(gffSuffix + ".gz") || fname.endsWith(gffSuffix))	{
			    	   if (inGffFile!=null) {
			    		   prt("Multiple fasta files - using " + inFaFile);
			    		   break;
			    	   }
			    	   inGffFile= dsDirName + "/" +fname; 		
			       } 
				}
		
				if (inFaFile == null)  
					return fail("Project directory " + projDir + ": no file ending with " + faSuffix +  " or "+ faSuffix + ".gz (i.e. NCBI files)");
				if (inGffFile == null) prt("Project directory " + projDir + ": no file ending with " + gffSuffix + " or " + gffSuffix + ".gz");
				
				// Create sequence and annotation directories
				if (!projDir.endsWith("/")) projDir += "/";
				seqDir = projDir + seqDir;
				checkDir(true, seqDir);  if (!bSuccess) return false;
				
				annoDir = projDir + annoDir;
				checkDir(false, annoDir); if (!bSuccess) return false;
				
				return true;
			}
			catch (Exception e) { return fail(e, "Checking " + projDir); }
		}
		/*****************************************************************/
		 private void createLog(String [] args) {
			if (args.length==0) return;
			
			logFileName = args[0] + logFileName;
			prt("Log file to  " + logFileName);	
			
			try {
				logFile = new PrintWriter(new FileOutputStream(logFileName, false)); 
				logFile.println(header);
			}
			catch (Exception e) {fail("Cannot open " + logFileName); logFile=null;}
			
			if (!bTrace) return;
			
			diffFileName = args[0] + diffFileName;
			prt("CDS/Exon difference " + diffFileName);
			try {
				diffFile = new PrintWriter(new FileOutputStream(diffFileName, false)); 
			}
			catch (Exception e) {fail("Cannot open " + diffFileName); diffFile=null;}	
		}
		 private boolean checkDir(boolean isSeq, String dir) {
			File nDir = new File(dir);
			if (nDir.exists()) {
				String x = (isSeq) ? " .fna and .fa "  : " .gff and .gff3";
				prt(dir + " exists - remove existing " + x + " files");
				deleteFilesInDir(isSeq, nDir);
				return true;
			}
			else {
				if (!nDir.mkdir()) 
					return fail("*** Failed to create directory '" + nDir.getAbsolutePath() + "'.");
			}	
			return true;
		}
	 } // End Init Class
	 /***************************************************************************
	 * * Fasta file example:
	 * >NC_016131.2 Brachypodium distachyon strain Bd21 chromosome 1, Brachypodium_distachyon_v2.0, whole genome shotgun sequence
	 * >NW_014576703.1 Brachypodium distachyon strain Bd21 unplaced genomic scaffold, Brachypodium_distachyon_v2.0 super_11, whole genome shotgun sequence *
	 * NOTE: NCBI files are soft-masked, which is used to compute the gaps.
	 *       If its then hard-masked (soft->N's), gaps and hard-masked appear the same 
	 */
	private class FASTA {
		private final int scafMaxLenLog=10000;	// All scaffold are printed to fasta file, but only summarized if >scafMax
		
		private int cntMask=0;
		private TreeMap <Character, Integer> cntBase = new TreeMap <Character, Integer> ();
		
		private int nChr=0, nScaf=0, nMt=0, nUnk=0, nGap=0; // for numbering output seqid and totals 
		private int cntChr=0, cntScaf=0, cntMtPt=0, cntUnk=0, cntOutSeq=0, cntNoOutSeq=0; 
		private long vChrLen=0, vScafLen=0, vMtptLen=0, vUnkLen=0, totalLen=0;
		private int cntScafSmall=0;
		char [] base = {'A', 'C', 'G', 'T', 'N', 'a', 'c', 'g', 't', 'n'};
		
		// Hard-coded values in rwFasta
		
		private FASTA () {
		try {
			fhOut = new PrintWriter(new FileOutputStream(seqDir + outFaFile, false));
			fhOut.println(header); 
			ghOut = new PrintWriter(new FileOutputStream(annoDir + outGapFile, false));
			ghOut.println(header); 
			
			for (char b : base) cntBase.put(b, 0);
			
			// Process file
			rwFasta(inFaFile); if (!bSuccess) return;
		
			fhOut.close(); ghOut.close();
			
			printSummary();
		}
		catch (Exception e) {e.printStackTrace(); fail("rwFasta");}
		}
		
		/********************************************************
		 * >NC_010460.4 Sus scrofa isolate TJ Tabasco breed Duroc chromosome 18, Sscrofa11.1, whole genome shotgun sequence
		 * >NC_010461.5 Sus scrofa isolate TJ Tabasco breed Duroc chromosome X, Sscrofa11.1, whole genome shotgun sequence
		 * >NW_018084777.1 Sus scrofa isolate TJ Tabasco breed Duroc chromosome Y unlocalized genomic scaffold,
		 */
		private void rwFasta(String fastaFile) {
		try {
			prt("\nProcessing " + fastaFile);
			BufferedReader fhIn = openGZIP(fastaFile); if (!bSuccess) return;
			
			String line="";
			int len=0, lineN=0;
			
			String idcol1="", prtName="", seqType="";
			boolean bPrtSeq=false, isChr=false, isScaf=false, isMT=false;
			boolean bMt=false, bPt=false;
			int baseLoc=0, gapStart=0, gapCnt=1, cntDup=0;
			
			while ((line = fhIn.readLine()) != null) {
				lineN++;
				if (line.startsWith("!") || line.startsWith("#") || line.trim().equals("")) continue;
				
    			if (line.startsWith(">")) { // id line
    				if (len>0) { // end last sequence
    					totalLen += len;
    					printSeqInfo(bPrtSeq, isChr, isScaf, isMT, len, idcol1, prtName);
    					len=0;
    				}
    				String line1 = line.substring(1).trim();
    				String [] tok = line1.split("\\s+");
    				if (tok.length==0) {
    					fail("Header line is blank: line #" + lineN);
    					return;
    				}
    				idcol1 = tok[0];
    				
    				isChr=isScaf=isMT=false; 
    				if (idcol1.startsWith("NC_") || line.contains(chrType))    isChr=true; 
    				if (idcol1.startsWith("NW_") || idcol1.startsWith("NT_") ) isScaf=true;
    				if (isChr && isScaf) isChr=false; //CAS586 >NW_018394658.1 Danio rerio strain Tuebingen chromosome 6 genomic scaffold,
    				
    				if (isChr) {
	    				if  (line.contains("mitochondrion") || line.contains("mitochondrial")
	    					 || line.contains("plastid") || line.contains("chloroplast")) isMT=true; // Mt/Pt, etc are NC_, but not 'chromosome'
    				}
    				if (isChr && !isMT) cntChr++;
    				else if (isScaf) 	cntScaf++;
    				else if (isMT) 		cntMtPt++;
    				else 				cntUnk++;
    				
    				if (prefixOnly!=null) { 
    					if (!idcol1.startsWith(prefixOnly)) {
    						cntNoOutSeq++;
    						continue;
    					}
    				}
    				
    				gapStart=1; gapCnt=0; baseLoc=0;
    				bPrtSeq=false; 
    				if (isChr && !isMT) { 
    					nChr++; 
    					seqType=chrType;
    					prtName = createChrPrtName(line);
    					
    					if (chr2id.containsKey(prtName)) { // CAS586 happens with zebrafish
    						if (cntDup<=traceNum) prt("Duplicate " + prtName + ":" + line);
    						else if (cntDup==traceNum+1) prt("Suppressing further chromosome logs");
    						cntDup++;
    					}
    					else {
		    				id2chr.put(idcol1, prtName);
		    				chr2id.put(prtName, idcol1);
		    				cntChrGene.put(idcol1, 0);
		    				bPrtSeq=true;
    					}
    				}
    				else if (isMT && INCLUDEMtPt) { 
    					nChr++; 
    					if (line.contains("mitochondrion") || line.contains("mitochondrial")) {
    						prtName= chrPrefix + "Mt";
    						if (!bMt) {bMt=true; nMt++;}
    						else {
    							cntNoOutSeq++;
    							if (VERBOSE) prt("Ignore Mt: " + line);
    							continue;
    						}
    					}
    					else {
    						prtName = chrPrefix + "Pt";
    						if (!bPt) {bPt=true; nMt++;}
    						else {
    							cntNoOutSeq++;
    							if (VERBOSE) prt("Ignore Mt: " + line);
    							continue;
    						}
    					}
	    				id2chr.put(idcol1, prtName);
	    				chr2id.put(prtName, idcol1);
	    				cntChrGene.put(idcol1, 0);
	    				
	    				seqType=chrType;
	    				bPrtSeq=true;	
    				}
    				else if (isScaf && INCLUDESCAF) {
    					nScaf++;  
	    				prtName = scafPrefix + padNum(nScaf+"");
	    				id2scaf.put(idcol1, prtName);
	    				cntScafGene.put(idcol1, 0);
	    				
	    				seqType=scafType;
    					bPrtSeq=true;
    				}
    				else if (prefixOnly!=null || INCLUDEAll) {// CAS586 add INCLUDEAll
						nUnk++;
						prtName = unkPrefix + padNum(nUnk+"");
						id2chr.put(idcol1, prtName); 
	    				cntChrGene.put(idcol1, 0);
	    				seqType=unkType;
	    				bPrtSeq=true;
    				}
    				if (bPrtSeq) {
    					fhOut.println(">" + prtName + " " + idcol1 + " " + seqType);
    					cntOutSeq++;
    				}
    				else cntNoOutSeq++;
    				continue;
    			} // finish header line
    			
    			String seqLine = line.trim();
    			
    			// for verbose
    			if (isChr && !isMT) vChrLen  += seqLine.length();
    			else if (isMT) 		vMtptLen += seqLine.length();
    			else if (isScaf) 	vScafLen += seqLine.length();
    			else 				vUnkLen  += seqLine.length();
    			
    			if (!bPrtSeq) continue;
    			
    			//////////////////////////////////////////////////////////////////////
    			// sequence line
    			
				char [] bases = seqLine.toCharArray();
				
				//eval for gaps, mask and count bases
				for (int i =0 ; i<bases.length; i++) {
					char b = bases[i];
					if (cntBase.containsKey(b)) {
						int cnt = cntBase.get(b);
						if (cnt<Integer.MAX_VALUE) cntBase.put(b, cnt+1);
					}
					else  cntBase.put(b, 1);
					
					baseLoc++;
					if (b=='N' || b=='n') { 
						if (gapStart==0) 	gapStart = baseLoc;
						else 				gapCnt++;
					}
					else if (gapStart>0) {						// gaps 
						if (gapCnt>gapMinLen) {
							nGap++;
							String x = createGap(prtName, gapStart, gapCnt);
							ghOut.println(x);
						}
						gapStart=0; gapCnt=1;
					}
					if (MASKED) { // set lc to masked
						if (b=='a' || b=='c' || b=='t' || b=='g' || b=='n') {
							bases[i]='N';
							cntMask++;
						}
					}
				}
				if (MASKED) seqLine = new String(bases); // make masked bases new output line
				
				len += seqLine.length();
				fhOut.println(seqLine);
    		}// finish loop through file
			if (len>0) {
				totalLen += len;
				printSeqInfo(bPrtSeq, isChr, isScaf, isMT, len, idcol1, prtName);
			}
			System.err.print("                                            \r");
			fhIn.close(); 	
		}
		catch (Exception e) {fail(e, "rwFasta: " + fastaFile);}
		}
		// >NC_015438.3 Solanum lycopersicum cultivar Heinz 1706 chromosome 12, SL3.0, whole genome";
		// >NC_003279.8 Caenorhabditis elegans chromosome III
		// >NC_003070.9 Arabidopsis thaliana chromosome X sequence
		// >NC_027748.1 Brassica oleracea TO1000 chromosome C1, BOL, whole genome shotgun sequence
		private String createChrPrtName(String line) {
		try {
			String name=null;
			String [] words = line.split("\\s+"); 
			for (int i=0; i<words.length; i++) {
				if (words[i].equals(chrType)) {
					if (i+1 < words.length) name = words[i+1];
					break;
				}
			}
			if (name!=null) {
				if (name.endsWith(",")) name = name.substring(0, name.length()-1);
				try {
					Integer.parseInt(name);
					name = chrPrefix + padNum(name);
				}
				catch (Exception e) {
					if (name.matches("^[XYIV]*$")) name = "Chr" + name;;
				}	
			}
			if (name == null) {
				Pattern patFA1 =         Pattern.compile("(.*)\\schromosome\\s+([0-9IVXY]*),\\s(.+$)");
				Matcher m = patFA1.matcher(line);
				if (m.matches()) name = m.group(2);
				else {
					Pattern patFA2 =     Pattern.compile("(.*)\\schromosome\\s+([0-9IVXY]*)$");
					m = patFA2.matcher(line);
					if (m.matches()) name = m.group(2);
					else {
						Pattern patFA3 = Pattern.compile("(.*)\\schromosome\\s+([0-9IVXY]*)\\s(.+$)");
						m = patFA3.matcher(line);
						if (m.matches()) name = m.group(2);
					}
				}
				if (name!=null) 
					name = chrPrefix + padNum(name);
			}
			
			if (name==null) { 
				nUnk++;
				name = "???" + padNum(nUnk+""); 
				prt("Unknown: " + line);
			}
			return name;
		}
		catch (Exception e) {}
		return null;
		}
		// The zero is added so that the sorting will be integer based
		private String padNum(String x) {
		try {
			Integer.parseInt(x);
			if (x.length()==1) return "0" + x;
			else return x;
		}
		catch (Exception e) {return x;} // could be X or Y
		}
		
		//chr3    consensus       gap     4845507 4895508 .       +       .       Name    "chr03_0"
		private String createGap(String chr, int start, int len) {
			String id = "Gap_" + nGap + "_" + len;
			return chr + "\tsymap\tgap\t" + start + "\t" + (start+len) + "\t.\t+\t.\tID=\"" + id + "\"";
		}
		
		private void printSeqInfo(boolean bPrt, boolean isChr, boolean isScaf, boolean isMtPt,
				int len, String id, String prtname) {
			
			String msg = String.format("   %-7s %-15s %,11d", prtname, id, len);
			String msg2 = String.format("  %s %,d  ....                       ", prtname, len);
			
			if (isChr && !isMtPt) {
				if (bPrt || VERBOSE) {
					if (cntChr<=traceNum) prt(msg);
					else if (cntChr==traceNum+1) prt("   Suppressing further chromosome logs");
					else System.out.print(msg2 + "\r");
				}
				else System.out.print(msg2 + "\r");
			}
			else if (isMtPt) {
				if (bPrt || VERBOSE) {
					if (cntMtPt<=traceNum) prt(msg);
					else if (cntMtPt==traceNum+1) prt("   Suppressing further Mt/Pt logs");
					else System.out.print(msg2 + "\r");
				}
			}
			else if (isScaf) {
				if (len<scafMaxLenLog) cntScafSmall++;
				if (bPrt || VERBOSE) {
					if (cntScaf<=traceNum) 	prt(msg);
					else if (cntScaf==traceNum+1) prt("   Suppressing further scaffold logs");
					else System.out.print(msg2 + "\r");
				}
				else if (len>1000000) System.out.print(msg2 + "\r");
			}
			else {
				if (bPrt || VERBOSE) {
					if (cntUnk<=traceNum) prt(msg);
					else if (cntUnk==traceNum+1) prt("   Suppressing further unknown logs");
					else System.out.print(msg2 + "\r");
				} 
				else if (len>1000000) System.out.print(msg2 + "\r");
			}
		}
		private void printSummary() {
			prt(String.format("   Sequences not output:  %,d", cntNoOutSeq));
			if (cntOutSeq==0) {
				fail("No sequences were output. Execute Summarize for prefixes.");
				return;
			}
			if (INCLUDESCAF) prt(String.format("   Scaffolds < %,dbp:  %,d", scafMaxLenLog, cntScafSmall));
			
			prt("");
			prt( String.format("   A %,11d  a %,11d", cntBase.get('A'), cntBase.get('a')) );
			prt( String.format("   T %,11d  t %,11d", cntBase.get('T'), cntBase.get('t')) );
			prt( String.format("   C %,11d  c %,11d", cntBase.get('C'), cntBase.get('c')) );
			prt( String.format("   G %,11d  g %,11d", cntBase.get('G'), cntBase.get('g')) );
			prt( String.format("   N %,11d  n %,11d", cntBase.get('N'), cntBase.get('n') ));
			
			String other=""; 
			for (char b : cntBase.keySet()) {
				boolean found = false;
				for (char x : base) if (x==b) {found=true; break;}
				if (!found) other += String.format("   %c %,d  ", b, cntBase.get(b));
			}
			if (other!="") prt(other);
			cntBase.clear();
			prt(String.format("   Total written: %,d", totalLen));
			
			if (MASKED) prt(String.format("Hard masked: %,d lower case changed to N", cntMask));
			prt("Finish writing " + seqDir + outFaFile + "                     ");
			
			prt("");
			prt(String.format("Gaps >= %,d: %,d", gapMinLen, nGap));
			prt("Finish writing " + annoDir + outGapFile + "                     ");
			
			if (!VERBOSE) return;
			
			prt("");
			prt("All sequences");
			prt(String.format("  %,6d %-11s  Output %-,6d  %,13d", cntChr, "Chromosomes", nChr,  vChrLen));
			prt(String.format("  %,6d %-11s  Output %-,6d  %,13d", cntMtPt, "Mt/Pt", nMt, vMtptLen));
			prt(String.format("  %,6d %-11s  Output %-,6d  %,13d", cntScaf, "Scaffolds", nScaf,  vScafLen));
			if (cntUnk>0) 
				prt(String.format("  %,6d %-11s  Output %-,6d  %,15d", cntUnk, "Unknown", nUnk,  vUnkLen));
		}
	} // End FASTA class
	/***************************************************************************
	 * XXX GFF file example:
	 * NC_016131.2     Gnomon  gene    18481   21742   .       +       .       ID=gene3;Dbxref=GeneID:100827252;Name=LOC100827252;gbkey=Gene;gene=LOC100827252;gene_biotype=protein_coding
	   NC_016131.2     Gnomon  mRNA    18481   21742   .       +       .       ID=rna2;Parent=gene3;Dbxref=GeneID:100827252,Genbank:XM_003563157.3;Name=XM_003563157.3;gbkey=mRNA;gene=LOC100827252;model_evidence=Supporting evidence includes similarity to: 3 Proteins%2C and 78%25 coverage of the annotated genomic feature by RNAseq alignments;product=angio-associated migratory cell protein-like;transcript_id=XM_003563157.3
	   NC_016131.2     Gnomon  exon    18481   18628   .       +       .       ID=id15;Parent=rna2;Dbxref=GeneID:100827252,Genbank:XM_003563157.3;gbkey=mRNA;gene=LOC100827252;product=angio-associated migratory cell protein-like;transcript_id=XM_003563157.3
	 	Gene gets new attribute of product=mRNA product and rnaID=mrnaID
	 */
	private class GFF {
		// types
		private final String geneType = "gene", mrnaType = "mRNA";
		private final String exonType = "exon", cdsType  = "CDS";  
		
		// attribute keywords
		private final String idAttrKey 		= "ID";		// all types
		private final String nameAttrKey 	= "Name"; 			// gene
		private final String biotypeAttrKey	= "gene_biotype";	// gene - just for recording for terminal output
		private final String proteinAttr 	= "protein_coding"; // gene - only genes saved
		private final String parentAttrKey 	= "Parent";			// mRNA, CDS, Exon
		private final String productAttrKey = "product"; 		// mRNA 
		private final String descAttrKey 	= "description"; 	// gene - usually does not exist, use longest mRNA product

		// new gene attribute
		private final String MRNAID    = "rnaID="; 	   // longest mRNA 
		private final String DESC	   = "desc=";	   // either gene description or mRNA product
		
		private final String RMGENE	   = "gene-";
		private final String RMRNA	   = "rna-";
		
		// Summary
		private TreeMap <String, Integer> geneAttrCnt = new TreeMap <String, Integer> ();
		private TreeMap <String, Integer> geneSrcCnt = new TreeMap <String, Integer> ();
		
		private int cntReadGene=0, cntReadMRNA=0, cntReadExon=0, cntReadCDS=0;
		private int cntChrGeneAll=0, cntScafGeneAll=0, cntGeneNotOnSeq=0;
		private int cntUseGene=0, cntUseExon=0, cntUseCDS=0, cntReplace=0, cntNoDesc=0, cntNoMrna=0;
	
		// anno per gene
		private String geneLine="";						
		private String geneID="", gChr="";
		private int cntGeneMRNA=0;	// number of mRNAs for gene
		
		private int mrnaLen=0;
		private String mrnaLine="", mrnaID="", mrnaProduct="";
		private Vector <String> exonVec = new Vector <String> ();
		private Vector <String> cdsVec = new Vector <String> ();	
		
		private int nextLen=0;		// find longest mRNA
		private String nextLine="", nextID="", nextProduct="";
		private Vector <String> nextExonVec = new Vector <String> ();	
		private Vector <String> nextCdsVec = new Vector <String> ();
		
		private HashMap <String, String> hexMap = new HashMap <String, String> ();
		
		/**************************************************/
		private GFF() {
		try {	
			if (inGffFile==null) return; 
			prt("");
			prt("Processing " + inGffFile);
			
			//hexMap.put("%09", " "); // tab - break on this
			hexMap.put("%3D", "-"); // = but cannot have these in attr, so use dash
			hexMap.put("%0A", " "); // newline
			hexMap.put("%25", "%"); // %
			hexMap.put("%2C", ","); // ,
			hexMap.put("%3B", ","); // ; but cannot have these in attr, so use comma
			hexMap.put("%26", "&"); // &
		
			 BufferedReader fhIn = openGZIP(inGffFile); if (!bSuccess) return;
			 PrintWriter fhOutGff = new PrintWriter(new FileOutputStream(annoDir + outGffFile, false)); 
			 fhOutGff.println(header);
			 
			 String line="";
			 int skipLine=0;
			 
			 /* read old GFF and write new GFF */
			 while ((line = fhIn.readLine()) != null) {
				line = line.trim();
				if (line.startsWith("#") || line.startsWith("!") || line.trim().length()==0) continue;
				
				String [] tok = line.split("\\t");
				if (tok.length!=9) {
					skipLine++;
					if (skipLine<10) prt("Bad line: " + line);
					else fail("too many errors");
					continue;
				}
				
				// Count everything here for summary
				String type =  tok[2].trim();  		// gene, mRNA, exon...
				
				if (type.equals(geneType)) {
					replaceMRNA();					// in case there is only one
					writeGene(fhOutGff);			// Write last record
					
					typeGene(tok, line);			// Start new
					cntReadGene++; 
					if (cntReadGene%PRT==0) System.err.print("   Process " + cntReadGene + " genes...\r");
				}
				else if (type.equals(mrnaType)) {
					typeMRNA(tok, line);
					cntReadMRNA++;
				}
				else if (type.equals(cdsType)) {
					typeCDS(tok, line);
					cntReadCDS++;
				}
				else if (type.equals(exonType)) {
					typeExon(tok, line);
					cntReadExon++;
				}	
			 }
			 fhIn.close(); fhOutGff.close();
			
			 
			 printSummary();
		 }
		 catch (Exception e) {fail(e, "rwAnnoGene");}
		}
		// Gene
		private void typeGene(String [] tok, String line) { 
		try {	
			// counts for gene
			String [] typeAttrs = tok[8].trim().split(";"); 
			String biotype = getVal(biotypeAttrKey, typeAttrs);
			if (!biotype.equals(proteinAttr)) return; // protein-coding
			
			String idcol1 =   tok[0];  // chromosome, scaffold, linkage group...
			if (id2chr.containsKey(idcol1))  { 
				cntChrGeneAll++;
				gChr = id2chr.get(idcol1);
				cntMap(cntChrGene, idcol1);
			}
			else if (id2scaf.containsKey(idcol1)) {
				cntScafGeneAll++; 
				gChr = id2scaf.get(idcol1);
				cntMap(cntScafGene, idcol1);
			}
			else {
				cntGeneNotOnSeq++; 
				return;
			}
			/// accepted
			geneID =  getVal(idAttrKey, typeAttrs);
			geneLine = line;
			cntUseGene++;
			
			// stats 
			String src  =  tok[1].trim();  // RefSeq, Gnomon..
			if (src.contains("%2C")) src = src.replace("%2C", ","); 
			cntMap(geneSrcCnt, src);
			
			for (String a : typeAttrs) {
				String [] tok2 = a.split("=");
				if (tok2.length==2) cntMap(geneAttrCnt, tok2[0]);
			}
		 }
		 catch (Exception e) {fail(e, "typeGene");}
		 }
		 // mRNA is not loaded into SyMAP, but needed for exon parentID
		 private void typeMRNA(String [] tok, String line) { 
		 try {
			String [] attrs = tok[8].split(";"); 
			String pid = getVal(parentAttrKey, attrs);
			if (!geneID.equals(pid)) return; 
			
			cntGeneMRNA++;
			if (!nextID.equals("")) replaceMRNA();
			
			nextID = getVal(idAttrKey, attrs);
	
			nextProduct = getVal(productAttrKey, attrs); // for gene attribute
			
			String idStr 	= idAttrKey     + "=" + nextID.replace(RMRNA,"");	
			String parStr 	= parentAttrKey + "=" + geneID.replace(RMGENE,"");
			String newAttrs = idStr + ";" + parStr;
			
			nextLine = gChr + "\t" + tok[1] + "\t" + tok[2] + "\t" + tok[3] + "\t" + 
					 tok[4] + "\t" + tok[5] + "\t" + tok[6] + "\t" + tok[7] + "\t" + newAttrs;
			
			int start = Integer.parseInt(tok[3]);
			int end   = Integer.parseInt(tok[4]);
			nextLen = end-start+1;
		 }
		 catch (Exception e) {fail(e, "typeMRNA");}
		 }
		// Update MRNA with longest
		 private void replaceMRNA() {
			 if (nextLine.equals("")) return;
			 
			 if (mrnaLen!=0 &&  mrnaLen<nextLen) cntReplace++;
			 
			 if (mrnaLen==0 || mrnaLen<nextLen) {
				 mrnaLen  = nextLen;
				 mrnaLine = nextLine;
				 mrnaID   = nextID;
				 mrnaProduct = nextProduct;
				 
				 exonVec.clear(); cdsVec.clear();
				 for (String e : nextExonVec) exonVec.add(e);
				 for (String c : nextCdsVec)  cdsVec.add(c); 
			 }
			 nextLen=0;
			 nextLine = nextID = nextProduct = "";
			 nextExonVec.clear(); nextCdsVec.clear();
		 }
		 // CDS
		 private void typeCDS(String [] tok, String line) { 
		 try {
			if (nextID.equals("")) return;
			
			String [] attrs = tok[8].split(";"); 
			String pid = getVal(parentAttrKey, attrs);
			if (!nextID.equals(pid)) return;
			
			// save CDS
			String idStr =   idAttrKey       + "=" + getVal(idAttrKey, attrs);	// create shortened attribute list
			String parStr =  parentAttrKey   + "=" + nextID.replace(RMRNA,"");	
			String newAttrs = idStr + ";" + parStr;
			String nLine = gChr + "\t" + tok[1] + "\t" + tok[2] + "\t" + tok[3] + "\t" + tok[4]
						        + "\t" + tok[5] + "\t" + tok[6] + "\t" + tok[7] + "\t" + newAttrs;
			nextCdsVec.add(nLine);
		 }
		 catch (Exception e) {fail(e, "typeCDS");}
		 }
		 // Exon
		 private void typeExon(String [] tok, String line) {
		 try {
			if (nextID.equals("")) return;
				
			String [] attrs = tok[8].split(";"); 
			String pid = getVal(parentAttrKey, attrs);	
			if (!nextID.equals(pid)) return;
			
			// save exon
			String idStr =   idAttrKey       + "=" + getVal(idAttrKey, attrs);	// create shortened attribute list
			String parStr =  parentAttrKey   + "=" + nextID.replace(RMRNA,"");
			String newAttrs = idStr + ";" + parStr;
			String nLine = gChr + "\t" + tok[1] + "\t" + tok[2] + "\t" + tok[3] + "\t" + tok[4]
						        + "\t" + tok[5] + "\t" + tok[6] + "\t" + tok[7] + "\t" + newAttrs;
			nextExonVec.add(nLine);
		 }
		 catch (Exception e) {fail(e, "typeExon");} 
		 }
		 /******************************************************/
		 private void writeGene(PrintWriter fhOutGff) {
		 try {
			if (geneID.trim().equals("")) {cntNoMrna++; return;} 
			if (mrnaID.trim().equals("")) return; 
		
			String [] tok = geneLine.split("\\t"); // original geneLine
			if (tok.length!=9) fail("Gene: " + tok.length + " " + geneLine);
			
			if (geneID.startsWith(RMGENE)) geneID = geneID.replace(RMGENE,""); 
			String idAt = idAttrKey + "=" + geneID + ";";
			
			String [] attrs = tok[8].split(";");
			String val = getVal(nameAttrKey, attrs);
			String nameAt = (!geneID.contains(val)) ? (nameAttrKey + "=" + val + ";") : ""; // CHANGE here to alter what attributes are saved
			
			String desc = getVal(descAttrKey, attrs).trim();
			if (!desc.equals("")) desc = DESC + desc;
			else {
				if (mrnaProduct.equals("")) {desc=""; cntNoDesc++;}
				else desc = DESC + mrnaProduct;
			}
			
			String mrnaAt 	= MRNAID + mrnaID.replace(RMRNA,"") + " (" + cntGeneMRNA + ");";
			
			String allAttrs = idAt + nameAt + mrnaAt + desc;
			
			String line = gChr + "\t" + tok[1] + "\t" + tok[2] + "\t" + tok[3] + "\t" + tok[4]
			+ "\t" + tok[5] + "\t" + tok[6] + "\t" + tok[7] + "\t" + allAttrs;
			
			if (line.contains("%")) {
				for (String hex : hexMap.keySet()) {
					if (line.contains(hex)) line = line.replace(hex, hexMap.get(hex));
				}
			}
			fhOutGff.println("###\n" + line);
			
			fhOutGff.println(mrnaLine); 
			
			cntUseExon += exonVec.size();
			cntUseCDS  += cdsVec.size();
			
			for (String eline : exonVec) fhOutGff.println(eline);
			for (String eline : cdsVec)  fhOutGff.println(eline);
			if (bTrace) printDiff();
			
			cntGeneMRNA=0; mrnaLen=0;
			geneID=geneLine=gChr="";
			mrnaLine= mrnaID = mrnaProduct="";
			exonVec.clear(); cdsVec.clear();
			
			nextLen=0;
			nextLine = nextID = nextProduct = "";
			nextExonVec.clear(); nextCdsVec.clear();
		 }
		 catch (Exception e) {fail(e, "Write gene");} 
		}
		
		/******************************************/
		private void printSummary() {
		try {
			prt("");
			prt(String.format("   Chromosome gene count: %,d                ", cntChrGeneAll));
			for (String prt : chr2id.keySet()) {
				String id = chr2id.get(prt);
				prt(String.format("   %-10s %-20s %,8d", prt, id, cntChrGene.get(id)));
			}
			if (INCLUDESCAF) {
				int cutGene = (cntScafGene.size()>100) ? 10 : 5;
				int cntOne=0;
				prt(String.format("   Scaffold gene count %,d (list scaffolds with #genes>%d) ", cntScafGeneAll, cutGene));
				for (String id : cntScafGene.keySet()) {
					if (cntScafGene.get(id)>cutGene)
						prt(String.format("   %-10s %-20s %,8d", id2scaf.get(id), id, cntScafGene.get(id)));
					else cntOne++;
				}
				prt(String.format("   Scaffolds with <=%d genes: %,d  (not listed)", cutGene, cntOne));
			}
			prt(String.format("   Genes not included in GFF file: %,d", cntGeneNotOnSeq));
			
			prt("");
			prt(String.format(" %,8d Gene from %,d (protein coding)", cntUseGene, cntReadGene));
			prt(String.format(" %,8d mRNA from %,d (longest mRNA)  ", cntUseGene, cntReadMRNA));
			prt(String.format(" %,8d Exon from %,d                 ", cntUseExon, cntReadExon));
			prt(String.format(" %,8d CDS  from %,d                 ", cntUseCDS,  cntReadCDS));
			
			prt("Finish writing " + annoDir + outGffFile + "                     ");
			
			if (!VERBOSE) return;
			
			prt("");
			prt("Gene Source ");
			for (String key :geneSrcCnt.keySet()) {
				prt(String.format("   %,8d %-22s", geneSrcCnt.get(key), key));
			}
			
			String oline="";
			int cnt=0;
			prt("");
			prt("Gene attribute keywords  (not all keywords saved in converted GFF)");
			for (String key : geneAttrCnt.keySet()) {
				oline += String.format("   %,8d %-22s", geneAttrCnt.get(key), key);
				cnt++;
				if (cnt>2) {
					prt(oline);
					cnt=0;
					oline="";
				}
			}
			if (!oline.isEmpty()) prt(oline);
			if (bTrace) { // -d
				prt("");
				if (cntNoDesc>0)  prt(String.format("No description %,d", cntNoDesc));
				if (cntNoMrna>0)  prt(String.format("Genes with no mRNAs %,d", cntNoMrna));
				if (cntReplace>0) prt(String.format("1st mRNA is not longest %,d", cntReplace));	
			}
		} 
		catch (Exception e) {ErrorReport.print(e, "GFF summary");}
		}
		
		private void printDiff() {
			if (diffFile==null) return;
			StringBuffer line = new StringBuffer();
			String [] exon = new String [exonVec.size()];
			String [] cds = new String [cdsVec.size()];
			
			int i=0;
			for (String x : exonVec) {
				String [] tok = x.split("\\t");
				exon[i++] = tok[3]+":"+tok[4];
			}
			i=0;
			for (String x : cdsVec) {
				String [] tok = x.split("\\t");
				cds[i++] = tok[3]+":"+tok[4];
			}
			
			int cntE=0, cntC=0;
			line.append("Exon");
			for (String ex : exon) {
				boolean found=false;
				for (String cd : cds) {
					if (ex.equals(cd)) {found=true; break;}
				}
				if (!found) {line.append(" " + ex); cntE++;}
			}
			line.append("\nCDS ");
			for (String cd : cds) {
				boolean found=false;
				for (String ex : exon) {
					if (ex.equals(cd)) {found=true; break;}
				}
				if (!found) {line.append(" " + cd); cntC++;}
			}
			diffFile.println("### " +geneID + " " + cntE + " " + cntC + " of " + exonVec.size() + " " + cdsVec.size());
			diffFile.println(line.toString()+"\n");
		}
		private void cntMap(Map <String, Integer> xmap, String val) {
			if (xmap.containsKey(val)) 	xmap.put(val, xmap.get(val)+1); 
			else 						xmap.put(val, 1);
		}
		
		private String getVal(String key, String [] attrs) {
			for (String s : attrs) {
				String [] x = s.split("=");
				if (x[0].equals(key)) return x[1];
			}
			return "";
		}
	} // End GFF class
}