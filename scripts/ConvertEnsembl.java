
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;
import java.util.zip.GZIPInputStream;

/*************************************************************
 * ConvertEnsembl: Ensembl genome files formatted for SyMAP
 * See https://csoderlund.github.io/SyMAP/input/ensembl.html
 * This script is the same as used by xToSymap Convert Ensembl
 * 
 * This assumes input of, e.g:
 * 1. ftp://ftp.ensemblgenomes.org/pub/plants/release-45/fasta/<species>/dna/<species>.dna_sm.toplevel.fa.gz
 *    Note: either hard-masked (rm) where repeats are replaced with Ns, or soft-masked (sm) where repeats are in lower-case text.
 * 2. ftp://ftp.ensemblgenomes.org/pub/plants/release-45/gff3/<species>/<species>.chr.gff3.gz
 *
 * Note: I am no expert on Ensembl GFF format, so I cannot guarantee this is 100% correct mapping for all input.
 * Your sequences/annotations may have other variations.
 * Hence, you may want to edit this to customize it for your needs -- its simply written so easy to modify.
 * See https://csoderlund.github.io/SyMAP/input/ensembl.html#edit
 * 
 * CAS586 include All sequences; remove Protein-id
 * 		output mRNA with 'tag=Ensembl_canonical'; if none exist, output largest
 * 		if no desc, use 'uncharacterized protein' (same as NCBI); output Version; add classes
 */

public class ConvertEnsembl {
	static private boolean isToSymap=false; 
	static private boolean bTrace=false;
	static private final int defGapLen=30000;
	
	private final String header = "### Written by SyMAP ConvertEnsembl"; 
	
	private String logFileName = "/xConvertENS.log";
	private String diffFileName = "/xDiffENS.log";
	private final String source = "[Source"; // remove this from description
	
	//input - project directory
	private final String faSuffix = ".fa";
	private final String gffSuffix = ".gff3";
	
	//output - the projDir gets appended to the front
	private String seqDir 		=  "sequence";		// Default sequence directory for symap
	private String annoDir 		=  "annotation";	// Default sequence directory for symap
	
	private final String outFaFile 	=  "/genomic.fna";	// Split expects this name
	private final String outGffFile =  "/anno.gff";		// Split expects this name
	private final String outGapFile =  "/gap.gff";
		
	// args (can be set from command line)
	private boolean VERBOSE 	= false; 
	private boolean INCLUDEAll  = false; 
	private boolean INCLUDESCAF = false;
	private boolean INCLUDEMtPt = false; 
	
	// args  (if running standalone, change in main)
	private int gapMinLen=defGapLen; // Print to gap.gff if #N's is greater than this
	private String prefixOnly=null;  // e.g. NC
	private boolean bNumOnly=false;  // Only number, X, Y or roman numeral
	private int traceNum=5;			 // if verbose, change to 20
	
	private String chrPrefix="Chr", chrPrefix2="C", chrType="chromosome"; // these values are checked in Split
	private String scafPrefix="s",  scafType="scaffold";
	private String unkPrefix="Seq", unkType="unknown";	// CAS586 changed Unk to Seq
		
	// input -- Ensembl allows individual chromosomes to be downloaded, hence, the vector
	private String projDir = null; // command line input
	private Vector <String> inFaFileVec = new Vector <String> (); // must be in projDir, end with fastaSuffix
	private Vector <String> inGffFileVec = new Vector <String> (); // must be in projDir, end with annoSuffix
	
	// All chromosome and scaffold names are mapped to ChrN and ScafN
	private TreeMap <String, Integer> cntChrGene = 	new TreeMap <String, Integer> ();
	private TreeMap <String, Integer> cntScafGene = new TreeMap <String, Integer> ();
	private TreeMap <String, String>  id2scaf = 	new TreeMap <String, String> ();
	private TreeMap <String, String>  id2chr = 		new TreeMap <String, String> ();
	private TreeMap <String, String>  chr2id = 		new TreeMap <String, String> ();
	
	private PrintWriter fhOut=null, ghOut=null; // FASTA and gap
	private PrintWriter fhOutGff=null;			// annotation
	private PrintWriter logFile=null, diffFile=null;
	
	private boolean bSuccess=true;
	
	private FASTA faObj;
	private GFF gffObj;
	private Init initObj;
	
	/************************************************************************/
	// main: stand-alone; gaplen or prefix need be changed below; args are -s (scaffold) -t (MT) -v (verbose)
	public static void main(String[] args) { 
		isToSymap=false;
		new ConvertEnsembl(args, defGapLen, null, false);
	}
	protected ConvertEnsembl(String[] args, int gapLen, String prefix, boolean bNum) { 
		initObj = new Init();
		initObj.printArgs(args); // Stand-alone; will quit if no args or -h or -help
		
		projDir = args[0];
		gapMinLen = gapLen;
		prefixOnly = prefix;
		bNumOnly = bNum;
		
		prt("\n------ ConvertEnsembl " + projDir + " ------");
		initObj.checkInitFiles();	if (!bSuccess) return;
		
		initObj.createLog(args); 	if (!bSuccess) return;
		initObj.checkArgs(args);	if (!bSuccess) return;
		
		faObj = new FASTA(); 		if (!bSuccess) return;
		
		gffObj = new GFF();			if (!bSuccess) return;
		
		printSuggestions();
		
		prt("------ Finish ConvertEnsembl " + projDir + " -------");
		if (logFile!=null) logFile.close();
	}
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
		if (faObj.cntOutSeq==0) prt("Are these Ensembl files? Should scaffolds be included? Should 'Prefix only' be set?");
		else if (faObj.cntOutSeq>30)  
				prt( "Suggestion: There are " + String.format("%,d",faObj.cntOutSeq) + " sequences. "
				   + "Set SyMAP project parameter 'Minimum length' to reduce number loaded.");
	}

	private BufferedReader openGZIP(String file) {
		try {
			if (!file.endsWith(".gz")) {
				File f = new File (file);
				if (f.exists()) return new BufferedReader ( new FileReader (f));
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
	private class Init {
		private void printArgs(String [] args) {
			if (args.length==0 || args[0].equals("-h") || args[0].equals("-help") || args[0].equals("help")) {
				System.out.println("\nConvertEnsembl <project directory> [options] 	");
				System.out.println(
						"   the project directory must contain the FASTA file and the GFF is optional:  \n" +
						"       FASTA file ending with .fa   or .fa.gz   e.g. Oryza_sativa.IRGSP-1.0.dna_sm.toplevel.fa.gz\n" +
						"       GFF   file ending with .gff3 or .gff3.gz e.g. Oryza_sativa.IRGSP-1.0.45.chr.gff3.gz\n" +  
						"   Options:\n" +
						"   -a  include all sequences'.\n" +
						"   -s  include any sequence with the header contains 'scaffold'.\n" +
						"   -t  include Mt and Pt chromosomes.\n" + 
						"   -v  write header lines of ignored sequences [default false].\n" +
						"Note: change gap length, prefix or #,X,Y in main()\n" +
						"\nSee https://csoderlund.github.io/SyMAP/input for details.");
				System.exit(0);
			}
		}
		private void checkArgs(String [] args) {	
			prt("Parameters:");
			prt("   Project Directory: " + args[0]);
			projDir = args[0];
			
			if (gapMinLen!=defGapLen) prt("   Gap size: " + gapMinLen);     
			if (prefixOnly!=null)     prt("   Prefix Only: " + prefixOnly); 
			if (bNumOnly)			  prt("   Only number, X, Y, Roman");   
			
			if (args.length>1) { // flags
				for (int i=1; i< args.length; i++) {
					if (args[i].equals("-v")) {
						VERBOSE=true;
						prt("   Verbose ");
						traceNum=20;
					}
					else if (args[i].equals("-a")) {
						INCLUDEAll=true;
						prt("   Include all sequences");
					}
					else if (args[i].equals("-s")) {
						INCLUDESCAF=true;
						chrPrefix = chrPrefix2;
						prt("   Include any sequence whose header line contains 'scaffold'");
						prt("      Uses prefixes Chr '" + chrPrefix + "' and Scaffold '" + scafPrefix +"'");
					}
					else if (args[i].equals("-t")) {
						INCLUDEMtPt=true;
						prt("   Include Mt and Pt chromosomes");
					}
				}
			}   
		}
		/************************************************************
		 * File stuff
		 */
		private boolean checkInitFiles() {
			// find fasta and gff files
			try {
				if (projDir==null || projDir.trim().equals("")) 
					return fail("No project directory");
				
				File dir = new File(projDir);
				if (!dir.isDirectory()) 
					return fail("The argument must be a directory. " + projDir + " is not a directory.");
					
				File[] files = dir.listFiles();
				for (File f : files) {
					if (f.isFile() && !f.isHidden()) { 
				       String fname = f.getName();
				     
				       if (fname.endsWith(faSuffix + ".gz") || fname.endsWith(faSuffix)) inFaFileVec.add(projDir + "/" + fname);
				       else if (fname.endsWith(gffSuffix + ".gz") || fname.endsWith(gffSuffix)) inGffFileVec.add(projDir + "/" +fname); 	
					} 
				}
			}
			catch (Exception e) {fail(e, "Checking files");}
			
			if (inFaFileVec.size()==0)  
				return fail("Project directory " + projDir + ": no file ending with " + faSuffix + " or "+ faSuffix + ".gz (i.e. Ensembl files)");
			if (inGffFileVec.size()==0)
				prt("Project directory " + projDir + ": no file ending with " + gffSuffix + " or " + gffSuffix + ".gz");
			
			// Create sequence and annotation directories
			if (!projDir.endsWith("/")) projDir += "/";
			seqDir = projDir +  seqDir;
			createDir(true, seqDir);		if (!bSuccess) return false;
			
			annoDir = projDir +  annoDir;
			createDir(false, annoDir);		if (!bSuccess) return false;
			
			return true;
		}
		 private void createLog(String [] args) {
			if (args.length==0) return;
			logFileName = args[0] + logFileName;
			prt("Log file to  " + logFileName);	
			
			try {
				logFile = new PrintWriter(new FileOutputStream(logFileName, false)); 
				logFile.println(header);
			}
			catch (Exception e) {fail("Cannot open " + logFileName); logFile=null;}
			
			if (bTrace) {
				diffFileName = args[0] + diffFileName;
				prt("CDS/Exon difference " + diffFileName);
				try {
					diffFile = new PrintWriter(new FileOutputStream(diffFileName, false)); 
				}
				catch (Exception e) {fail("Cannot open " + diffFileName); diffFile=null;}	
			}
		}
		
		private boolean createDir(boolean isSeq, String dir) {
			File nDir = new File(dir);
			if (nDir.exists()) {
				String x = (isSeq) ? " .fna and .fa "  : " .gff and .gff3";
				prt(dir + " exists - remove existing " + x + " files");
				deleteFilesInDir(isSeq, nDir);
				return true;
			}
			else {
				if (!nDir.mkdir()) {
					fail("*** Failed to create directory '" + nDir.getAbsolutePath() + "'.");
					return false;
				}
			}	
			return true;
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
	}
	/**
	 * * Fasta file example:
	 * >1 dna_sm:chromosome chromosome:IRGSP-1.0:1:1:43270923:1 REF
	 * >Mt dna_sm:chromosome chromosome:IRGSP-1.0:Mt:1:490520:1 REF
	 * >Syng_TIGR_043 dna_sm:scaffold scaffold:IRGSP-1.0:Syng_TIGR_043:1:4236:1 REF
	 */
	private class FASTA {
		private final int scafMaxLen=10000;	// counted if less than this
		
		private TreeMap <Character, Integer> cntBase = new TreeMap <Character, Integer> ();
		private char [] base = {'A', 'C', 'G', 'T', 'N', 'a', 'c', 'g', 't', 'n'};
		
		private int cntOutSeq=0, cntNoOutSeq=0;
		private int nChr=0, nScaf=0, nMt=0, nUnk=0, nGap=0; // for numbering output seqid and totals 
		private int cntChr=0, cntScaf=0, cntMtPt=0, cntUnk=0; // for counting regardless of write to file
		private long vChrLen=0, vScafLen=0, vMtptLen=0, unkLen=0, totalLen=0, cntScafSmall=0;
		
		// hard-coded values in rwFastaFile
		
		private FASTA() {
		try {
			for (char b : base) cntBase.put(b, 0);
		
			fhOut = new PrintWriter(new FileOutputStream(seqDir+outFaFile, false)); 
			fhOut.println(header); 
			
			ghOut = new PrintWriter(new FileOutputStream(annoDir + outGapFile, false));
			ghOut.println(header); 
			
			/** Loop through files **/
			for (String file : inFaFileVec) {
				rwFastaFile(file);
			}
			fhOut.close(); ghOut.close();
    		System.err.print("                                            \r");
    		
    		printSummary();
    		
		}
		catch (Exception e) {fail(e, "rwFasta");}
		}
		/*************************************************
		 * Process a fasta file
		 */
		private void rwFastaFile(String fileName) {
		try {
			prt("Processing " + fileName);
			
			String prtName="", idCol1="",  line;
			boolean bPrtSeq=false, isChr=false, isScaf=false, isMtPt=false;
			boolean bMt=false, bPt=false;
			int baseLoc=0, gapStart=0, gapCnt=1, len=0, lineN=0;
			
			BufferedReader fhIn = openGZIP(fileName); if (!bSuccess) return;
			
			while ((line = fhIn.readLine()) != null) {
				lineN++;
				if (line.startsWith("!") || line.startsWith("#") || line.trim().equals("")) continue;
				
				if (line.startsWith(">")) {
					if (len>0) {
						totalLen += len;
						printChrSum(bPrtSeq, isChr, isScaf, isMtPt, len, idCol1, prtName);
						len=0;
					}
					bPrtSeq=false;
					String line1 = line.substring(1).trim();
					String [] tok = line1.split("\\s+");
					if (tok.length==0) {
						fail("Header line is blank: line #" + lineN);
						return;
					}
					
					idCol1 = tok[0].trim();	
					
					isMtPt = idCol1.toLowerCase().startsWith("mt") //  MT or mtDNA...
							|| idCol1.toLowerCase().startsWith("pt") 
					        || line.contains("mitochondrion")
					        || line.contains("plastid")
					        || line.contains("mitochondrial")
					        || line.contains("chloroplast");
					
					isChr  = isChrNum(idCol1) || line.contains("chromosome"); 
					
					isScaf = line.contains("scaffold"); 
					
					if (isChr && !isMtPt) cntChr++;
					else if (isMtPt) cntMtPt++;
					else if (isScaf) cntScaf++;
					else cntUnk++;
					
					if (prefixOnly!=null) { 
						if (!idCol1.startsWith(prefixOnly)) {
							cntNoOutSeq++;
							continue;
						}
					}
					else if (bNumOnly) {
						if (!isChrNum(idCol1)) {
							cntNoOutSeq++;
							continue;
						}
					}
					
					if (isChr && !isMtPt) {
						nChr++;
						prtName = isChrNum(idCol1) ? chrPrefix + padNum(tok[0]) : idCol1; // tok[0], e.g. C1 for cabbage
						cntChrGene.put(idCol1, 0);
						id2chr.put(idCol1, prtName);
						chr2id.put(prtName, idCol1);
						
						fhOut.println(">" + prtName + " " + idCol1 + " " + chrType);
						bPrtSeq=true; 
					}
					else if (isMtPt && INCLUDEMtPt) { // just like for numeric chromosome
						prtName = chrPrefix + tok[0];
						
						if (idCol1.equalsIgnoreCase("Mt")) {
							if (bMt) {
								if (VERBOSE) prt("Ignore Mt: " + line);
								cntNoOutSeq++;
								continue;
							}
							else {bMt=true; nMt++;}
						}
						if (idCol1.equalsIgnoreCase("Pt")) {
							if (bPt) {
								if (VERBOSE) prt("Ignore Pt: " + line);
								cntNoOutSeq++;
								continue;
							}
							else {bPt=true; nMt++;}
						}
						cntChrGene.put(idCol1, 0);
						id2chr.put(idCol1, prtName);
						chr2id.put(prtName, idCol1);
						
						fhOut.println(">" + prtName + "  " + idCol1  + " " + chrType);
						bPrtSeq=true;
					}
					else if (isScaf && INCLUDESCAF) {
						nScaf++; 
						prtName = scafPrefix + padNum(nScaf+""); // scaf use name
						
	    				id2scaf.put(idCol1, prtName);
	    				cntScafGene.put(idCol1, 0);
	    				gapStart=1; gapCnt=0; baseLoc=0;
	    				
						fhOut.println(">" + prtName+ "  " + idCol1 + " " + scafType);
						bPrtSeq=true; 
					}
					else if (prefixOnly!=null || INCLUDEAll) {// CAS586 add INCLUDEAll
						nUnk++;
						prtName = unkPrefix + padNum(nUnk+""); 
						id2chr.put(idCol1, prtName); 
	    				cntChrGene.put(idCol1, 0);
	    				
	    				bPrtSeq=true;
    					fhOut.println(">" + prtName+ "  " + idCol1 + " " + unkType);
					}
					if (bPrtSeq) cntOutSeq++; else cntNoOutSeq++;
					continue;
				} // end header line
				//////////////////////////
				String seqLine = line.trim();
				
				if (isChr && !isMtPt) 	vChrLen+=seqLine.length();
				else if (isMtPt) 		vMtptLen+=seqLine.length();
				else if (isScaf) 		vScafLen+=seqLine.length();
				else 					unkLen += seqLine.length();
					
				if (!bPrtSeq) continue;
				
				//////////////////////////
				char [] bases = seqLine.toCharArray();
				
				for (int i =0 ; i<bases.length; i++) {
					char b = bases[i];

					if (cntBase.containsKey(b)) {
						int cnt = cntBase.get(b);
						if (cnt<Integer.MAX_VALUE) cntBase.put(b, cnt+1);
					}
					else cntBase.put(b, 1);
					
					baseLoc++;
					if (b=='N' || b=='n') { 
						if (gapStart==0) 	gapStart = baseLoc;
						else 				gapCnt++;
					}
					else if (gapStart>0) {						// gaps 
						if (gapCnt>gapMinLen) {
							nGap++;
							String x = createGap(prtName, gapStart, gapCnt);
							if (bPrtSeq) ghOut.println(x);
						}
						gapStart=0; gapCnt=1;
					}
				}
			
				len += seqLine.length();
				fhOut.println(seqLine);
			} // loop through file
			
			if (len>0) {
				totalLen += len;
				printChrSum(bPrtSeq, isChr, isScaf, isMtPt, len, idCol1, prtName);
			}
			fhIn.close();
		}
		catch (Exception e) {fail(e, "rwFastaFile");}
		}
		/************************************************/
		private void printSummary() {
			
    		prt(String.format("   Sequences not output: %,d", cntNoOutSeq));
    		if (cntOutSeq==0) {
    			fail("No sequences were output. Execute Summarize for prefixes.");
    			return;
    		}
    		
    		if (INCLUDESCAF) prt(String.format("   Scaffolds < %,dbp: %,d ", scafMaxLen, cntScafSmall));
    		
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
			prt("Finish writing " + seqDir + outFaFile + "                          ");
		
			prt("                                                        ");
			prt(String.format("Gaps >= %,d: %,d (using N and n)", gapMinLen, nGap));
			prt("Finish writing " + annoDir + outGapFile + "                     ");
			
			if (!VERBOSE) return;
			
			prt("");
			prt("All sequences");
			prt(String.format("  %,6d %-11s  Output %-,6d  %,13d", cntChr, "Chromosomes", nChr,  vChrLen));
			prt(String.format("  %,6d %-11s  Output %-,6d  %,13d", cntMtPt, "Mt/Pt", nMt, vMtptLen));
			prt(String.format("  %,6d %-11s  Output %-,6d  %,13d ", cntScaf, "Scaffolds", nScaf,  vScafLen));
			if (cntUnk>0) 
				prt(String.format("  %,6d %-11s  Output %-,6d  %,15d", cntUnk, "Unknown", nUnk,  unkLen));
		}
		private boolean isChrNum(String col1) {
			if (col1.contentEquals("X") || col1.contentEquals("Y")) return true;
			try {
				Integer.parseInt(col1);
				return true;
			}
			catch (Exception e) {
				if (col1.length()>3) return false;
				// roman
				char [] x = col1.toCharArray();
				for (char y : x) {
					if (y!='I' && y!='V' && y!='X') return false;
				}
				return true;
			}
		}
		//chr3    consensus       gap     4845507 4895508 .       +       .       Name    "chr03_0"
		private String createGap(String chr, int start, int len) {
			String id = "Gap_" + nGap + "_" + len;
			return chr + "\tsymap\tgap\t" + start + "\t" + (start+len) + "\t.\t+\t.\tID=\"" + id + "\"";
		}
		// The zero is added so that the sorting will be integer based
		private String padNum(String x) {
			try {
				Integer.parseInt(x);
				if (x.length()==1) return "0" + x; // assume <99 chrs
				else return x;
			}
			catch (Exception e) {
				return x;		// could be X or Y
			} 
		}
		
		private void printChrSum(boolean bPrt, boolean isChr, boolean isScaf, boolean isMtPt, int len, String id, String prtname) {
			String msg = String.format("   %-7s %-15s %,11d", prtname, id, len);
			String msg2 = String.format("   %s %,d....                       ", prtname, len);
			
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
				if (len<scafMaxLen) cntScafSmall++;
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
					else if (cntUnk==traceNum+1) 	  prt("   Suppressing further unknown logs");
					else System.out.print(msg2 + "\r");
				}
				else if (len>1000000) System.out.print(msg2 + "\r");
			}
		}
	}
	/********************************************************************************
	 * XXX GFF file, e.g.
	1  araport11   gene    3631    5899    .       +       .       ID=gene:AT1G01010;Name=NAC001;biotype=protein_coding;description=NAC domain containing protein 1 [Source:NCBI gene (formerly Entrezgene)%3BAcc:839580];gene_id=AT1G01010;logic_name=araport11
	1  araport11   mRNA    3631    5899    .       +       .       ID=transcript:AT1G01010.1;Parent=gene:AT1G01010;Name=NAC001-201;biotype=protein_coding;transcript_id=AT1G01010.1
	1  araport11   five_prime_UTR  3631    3759    .       +       .       Parent=transcript:AT1G01010.1
	1  araport11   exon    3631    3913    .       +       .       Parent=transcript:AT1G01010.1;Name=AT1G01010.1.exon1;constitutive=1;ensembl_end_phase=1;ensembl_phase=-1;exon_id=AT1G01010.1.exon1;rank=1
	New Gene attribute of the selected mRNA along with number of mRNAs for gene, ie rnaID=selected mrnaID (cnt)
	 */
	private class GFF {
		private final int PRT = 10000;
	
		// types
		private final String geneType = "gene", mrnaType = "mRNA";
		private final String exonType = "exon", cdsType  = "CDS";
		
		// attribute keywords
		private final String idAttrKey 		= "ID"; 			// Gene and mRNA
		private final String nameAttrKey 	= "Name"; 			// Gene
		private final String descAttrKey	= "description"; 	// Gene
		private final String biotypeAttrKey = "biotype"; 		// Gene
		private final String proteinAttr 	= "protein_coding"; // Gene
		private final String parentAttrKey 	= "Parent"; 		// mRNA and Exon
		private final String mrnaPrefAttr   = "tag=Ensembl_canonical";	// mRNA - Ensembl preferred mrna
		
		private final String RMGENE    	= "gene:";					// remove from Gene and mRNA
		private final String RMTRAN    	= "transcript:";			// remove from mRNA, Exon, CDS
		
		private final String MRNAID     = "rnaID="; 	// new gene attribute
		private final String DESC	    = "desc=";		// rename description to desc
		
		private final String NODESC	    = "uncharacterized protein"; 
		private TreeMap <String, Integer> geneAttrCnt = new TreeMap <String, Integer> ();
		private TreeMap <String, Integer> geneSrcCnt = new TreeMap <String, Integer> ();
		
		private HashMap <String, String> hexMap = new HashMap <String, String> (); 
		
		private int cntChrGeneAll=0, cntScafGeneAll=0;
		private int cntGene=0, cntExon=0, cntCDS=0, cntGeneNotOnSeq=0; 
		private int cntReadGene=0, cntReadMRNA=0, cntReadExon=0, cntReadCDS=0;
		private int skipLine=0, cntReplace=0;
		
		// per gene
		private String geneID="",geneLine="", gChr="";
		private int cntGeneMRNA=0;
		
		private int mrnaLen=0;
		private String mrnaLine="", mrnaID="";
		private Vector <String> exonVec = new Vector <String> ();	
		private Vector <String> cdsVec = new Vector <String> ();	
		
		private boolean bFoundTag=false;	// mRNA has tag=Ensembl_canonical attribute
		private int nextLen=0;				// use longest mRNA or canonical
		private String nextLine="", nextID="";
		private Vector <String> nextExonVec = new Vector <String> ();	
		private Vector <String> nextCdsVec = new Vector <String> ();	
		
		/************************************************/
		private GFF() {
		try {
			if (inGffFileVec==null || inGffFileVec.size()==0) return; 
			
			//hexMap.put("%09", " "); // tab break on this
			hexMap.put("%3D", "-"); // = but cannot have these in attr, so use dash
			hexMap.put("%0A", " "); // newline
			hexMap.put("%25", "%"); // %
			hexMap.put("%2C", ","); // ,
			hexMap.put("%3B", ","); // ; but cannot have these in attr, so use comma
			hexMap.put("%26", "&"); // &
			
			fhOutGff = new PrintWriter(new FileOutputStream(annoDir + outGffFile, false));
			fhOutGff.println(header);
			
			/** Loop through files **/
			for (String file : inGffFileVec) {
				rwAnnoFile(file);
			}
			fhOutGff.close();
			
			printSummary();
			
		}
		catch (Exception e) {fail(e, "rwAnno");}	
		}
		/**** Process each gff file ***********/
		private void rwAnnoFile(String file) {
		try {
			String line="", type="";
			
			prt("");
			prt("Processing " + file);
			BufferedReader fhIn = openGZIP(file); if (!bSuccess) return;
				
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
				type =  tok[2].trim();  		// gene, mRNA, exon...
				
				if (type.equals(geneType)) {
					replaceMRNA();			// if only one; otherwise, checked in typeMRNA on 2nd, etc...
					writeGene(fhOutGff);
					
					typeGene(tok, line);
					cntReadGene++;
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
			replaceMRNA();			// Last one
			writeGene(fhOutGff);
			fhIn.close(); 
		}
		catch (Exception e) {fail(e, "rwAnnoGene");}
		}
		// Gene 
		private void typeGene(String [] tok, String line) { 
		try {	
			if (cntGene%PRT==0) System.err.print("   Process " + cntGene + " genes...\r");
			
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
			/////// Valid gene ////////////
			geneID =  getVal(idAttrKey, typeAttrs);
			String gid = geneID.replace(RMGENE,"");
			String attr = idAttrKey + "=" + gid + ";" + getKeyVal(nameAttrKey, typeAttrs);
			
			String desc = getVal(descAttrKey, typeAttrs).trim();
			if (desc==null || desc.equals("")) { 
				desc = NODESC;
			}
			else {
				if (desc.contains(source)) // "[Source"
					desc = desc.substring(0, desc.lastIndexOf(source)).trim(); 
				
				if (desc.contains("%")) { 
					for (String hex : hexMap.keySet()) {
						if (desc.contains(hex)) desc = desc.replace(hex, hexMap.get(hex));
					}
				}
			}
			attr += ";" + DESC + desc;
			geneLine =  "###\n" + gChr + "\t" + tok[1] + "\t" + tok[2] + "\t" + tok[3] + "\t" + tok[4]
			                    + "\t" + tok[5] + "\t" + tok[6] + "\t" + tok[7] + "\t" + attr;
			
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
			
			cntGeneMRNA++;			// Number of mRNAs for current gene
			if (bFoundTag) return;
			
			if (!nextID.equals("")) replaceMRNA();
			
			nextID = getVal(idAttrKey, attrs);
			String mid = idAttrKey     + "=" + nextID.replace(RMTRAN,"");
			String gid = parentAttrKey + "=" + geneID.replace(RMGENE,"");
			String newAttrs = mid + ";" + gid;
			
			if (tok[8].contains(mrnaPrefAttr)) { 
				bFoundTag=true;					// will be canonical on next replaceMRNA
				newAttrs += ";" + mrnaPrefAttr;
			}
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
			 
			 if (mrnaLen==0 || mrnaLen<nextLen || bFoundTag) { // FoundTag set for nextID
				 mrnaLen  = nextLen;
				 mrnaLine = nextLine;
				 mrnaID   = nextID;
				
				 exonVec.clear(); cdsVec.clear();
				 for (String e : nextExonVec) exonVec.add(e);
				 for (String c : nextCdsVec)  cdsVec.add(c); 
				 cntReplace++;
			 }
			 nextExonVec.clear(); nextCdsVec.clear();
			 nextLen=0;
			 nextLine = nextID = "";
		 }
		 // CDS
		 private void typeCDS(String [] tok, String line) { 
		 try {
			if (nextID.equals("")) return;
			
			String [] attrs = tok[8].split(";"); 
			String pid = getVal(parentAttrKey, attrs);
				
			if (!nextID.equals(pid)) return;
			
			// save CDS
			String newAttrs = parentAttrKey + "=" + nextID.replace(RMTRAN,""); // "transcript:"
			
			String nLine = gChr + "\t" + tok[1] + "\t" + tok[2] + "\t" + tok[3] + "\t" + tok[4]
						+ "\t" + tok[5] + "\t" + tok[6] + "\t" + tok[7] + "\t" + newAttrs;
			nextCdsVec.add(nLine);
		 }
		 catch (Exception e) {fail(e, "typeMRNA");}
		 }
		 // Exon 
		 private void typeExon(String [] tok, String line) {
		 try {
			if (nextID.equals("")) return;
				
			String [] attrs = tok[8].split(";"); 
			String pid = getVal(parentAttrKey, attrs);
			if (!nextID.equals(pid)) return;
			
			// save exon
			String newAttrs = parentAttrKey + "=" + nextID.replace(RMTRAN,""); // "transcript:"
			
			String nLine = gChr + "\t" + tok[1] + "\t" + tok[2] + "\t" + tok[3] + "\t" + tok[4]
						        + "\t" + tok[5] + "\t" + tok[6] + "\t" + tok[7] + "\t" + newAttrs;
			nextExonVec.add(nLine);
		 }
		 catch (Exception e) {fail(e, "typeExon");} 
		 }
		
		 /*************************************************/
		 private void writeGene( PrintWriter fhOutGff) {
		 try {
			if (geneID.equals("") || mrnaID.equals("")) return;
			
			cntGene++;
			cntExon += exonVec.size();
			cntCDS  += cdsVec.size();
			
			String gmrnaID = ";" + MRNAID + mrnaID.replace(RMTRAN,"") + " (" + cntGeneMRNA + ");";
			fhOutGff.println(geneLine + gmrnaID);
			
			fhOutGff.println(mrnaLine); 
			
			for (String eline : exonVec) fhOutGff.println(eline);
			for (String eline : cdsVec)  fhOutGff.println(eline);
			
			if (bTrace) printDiff();
			cntGeneMRNA=0;
			geneID=geneLine=gChr=mrnaID=mrnaLine="";
			exonVec.clear(); cdsVec.clear();
			mrnaLen=0;
			
			nextExonVec.clear(); nextCdsVec.clear();
			nextLen=0;
			nextLine = nextID = "";
			bFoundTag=false;
		}
		catch (Exception e) {fail(e, "writeGene");} 
		}

		private String getVal(String key, String [] attrs) {
			for (String s : attrs) {
				String [] x = s.split("=");
				if (x[0].equals(key)) return x[1];
			}
			return "";
		}
		private String getKeyVal(String key, String [] attrs) {
			for (String s : attrs) {
				String [] x = s.split("=");
				if (x[0].equals(key)) return s;
			}
			return key + "=no value";
		}
		
		private void printSummary() {
			prt("");
			prt(String.format("   Chromosome gene count: %,d ", cntChrGeneAll));
			for (String prt : chr2id.keySet()) {
				String id = chr2id.get(prt); // sorted on prtName
				int cnt   = cntChrGene.get(id);
				prt(String.format("   %-7s %-15s %,8d", prt, id, cnt));
			}
			
			if (INCLUDESCAF) {
				prt("");
				int cutGene = (cntScafGene.size()>100) ? 10 : 5;
				prt(String.format("   Scaffold gene count: %,d", cntScafGeneAll));
				int cntOne=0;
				for (String key : cntScafGene.keySet()) { // sorted on idCol1, which is not scaffold order
					int cnt = cntScafGene.get(key);
					if (cnt>cutGene) prt(String.format("   %-7s %-15s %,8d", id2scaf.get(key), key, cnt));
					else cntOne++;
				}
				prt(String.format("   Scaffolds with <=%d genes: %,d (not listed)", cutGene, cntOne));
			}
			prt(String.format("   Genes not included in GFF file: %,d",  cntGeneNotOnSeq));
			
			prt("");
			prt(String.format(" %,8d Gene from %,d (protein coding)", cntGene, cntReadGene));
			prt(String.format(" %,8d mRNA from %,d (canonical or longest)", cntGene, cntReadMRNA));
			prt(String.format(" %,8d Exon from %,d                ", cntExon, cntReadExon));
			prt(String.format(" %,8d CDS  from %,d                ", cntCDS,  cntReadCDS));
		
			if (bTrace) prt(String.format("   Replace 1st mRNA with longest %,d", cntReplace));
			
			prt("Finish writing " + annoDir + outGffFile + "                     ");
			
			if (!VERBOSE) return;
			//////////////////////////////////////////
			prt("                                         ");
			prt("Gene Source ");
			for (String key :geneSrcCnt.keySet()) {
				prt(String.format("   %,8d %-22s", geneSrcCnt.get(key), key));
			}
			
			String oline="";
			int cnt=0;
			prt("");
			prt("Gene attribute keywords (not all keywords saved in converted GFF)                ");
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
	} // End GFF class
}
