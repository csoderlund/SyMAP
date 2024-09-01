package toSymap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Vector;
import java.util.zip.GZIPInputStream;

/*************************************************************
 * ConvertEnsembl: Ensembl genome files formatted for SyMAP
 * See https://csoderlund.github.io/SyMAP/input/ensembl.html
 * 
 * Called from toSymap, but can be used stand-alone.
 * 
 * Written by CAS Oct2019; made part of toSymap and other changes for CAS557; altered in CAS558
 * This assumes input of, e.g:
 * 1. ftp://ftp.ensemblgenomes.org/pub/plants/release-45/fasta/<species>/dna/<species>.dna_sm.toplevel.fa.gz
 *    Note: either hard-masked (rm) where repeats are replaced with Ns, or soft-masked (sm) where repeats are in lower-case text.
 * 2. ftp://ftp.ensemblgenomes.org/pub/plants/release-45/gff3/<species>/<species>.chr.gff3.gz
 *
 * Note: I am no expert on Ensembl GFF format, so I cannot guarantee this is 100% correct mapping for all input.
 * Your sequences/annotations may have other variations.
 * Hence, you may want to edit this to customize it for your needs -- its simply written so easy to modify.
 * See https://csoderlund.github.io/SyMAP/input/ensembl.html#edit
 */

public class ConvertEnsembl {
	static private boolean isToSymap=true; // set to false in main if standalone
	static private int defGapLen=30000;
	
	private final String header = "### Written by SyMAP ConvertEnsembl"; // Split expects this to be 1st line
	private String logFileName = "/xConvertENS.log";
	private final String star = "*", plus="+";
	private final String source = "[Source"; // remove this from description
	
	private final int PRT = 10000;
	private final int scafMaxLen=10000;	// counted if less than this
	private int traceNum=5;				// if verbose, change to 20
	private final int traceNumGenes=3;  // Only print sequence if at least this many genes
	
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
	private boolean INCLUDESCAF = false;
	private boolean INCLUDEMtPt = false; 
	private boolean ATTRPROT 	= false;
	
	// args  (if running standalone, change in main)
	private int gapMinLen=defGapLen; // Print to gap.gff if #N's is greater than this
	private String prefixOnly=null;  // e.g. NC
	private boolean bNumOnly=false;  // Only number, X, Y or roman numeral
	
	private String chrPrefix="Chr", chrPrefix2="C", chrType="chromosome"; // these values are checked in Split
	private String scafPrefix="s",  scafType="scaffold";
	private String unkPrefix="Unk", unkType="unknown";
		
	// types
	private final String geneType = "gene";
	private final String mrnaType = "mRNA";
	private final String exonType = "exon";
	private final String cdsType  = "CDS";
	
	// attribute keywords
	private final String idAttrKey 		= "ID"; 			// Gene and mRNA
	private final String nameAttrKey 	= "Name"; 			// All
	private final String descAttrKey	= "description"; 	// Gene
	private final String biotypeAttrKey = "biotype"; 		// Gene
	private final String biotypeAttr 	= "protein_coding"; // Gene
	private final String parentAttrKey 	= "Parent"; 		// mRNA and Exon
	private final String cdsProteinAttrKey = "protein_id";

	private final String PROTEINID  = "proteinID=";  // CAS558 new keywords for gene attributes
	private final String MRNAID     = "rnaID="; 	// ditto
	private final String DESC	    = "desc=";		// ditto
	private final String RMGENE    = "gene:";
	private final String RMTRAN    = "transcript:";
		
	// input
	private String projDir = null; // command line input
	// Ensembl allows individual chromosomes to be downloaded, hence, the vector
	private Vector <String> inFaFileVec = new Vector <String> (); // must be in projDir, end with fastaSuffix
	private Vector <String> inGffFileVec = new Vector <String> (); // must be in projDir, end with annoSuffix
	
	// All chromosome and scaffold names are mapped to ChrN and ScafN
	private TreeMap <String, Integer> cntChrGene = 	new TreeMap <String, Integer> ();
	private TreeMap <String, Integer> cntScafGene = new TreeMap <String, Integer> ();
	private TreeMap <String, String>  id2scaf = 	new TreeMap <String, String> ();
	private TreeMap <String, String>  id2chr = 		new TreeMap <String, String> ();
	private TreeMap <String, String>  chr2id = 		new TreeMap <String, String> ();
	
	private TreeMap <String, Integer> allTypeCnt = 			new TreeMap <String, Integer> ();
	private TreeMap <String, Integer> allGeneBiotypeCnt = 	new TreeMap <String, Integer> ();
	private TreeMap <String, Integer> allGeneSrcCnt = 		new TreeMap <String, Integer> ();
	
	
	private int cntChrGeneAll=0, cntScafGeneAll=0, cntOutSeq=0, cntNoOutSeq=0, cntScafSmall=0;
	private int nChr=0, nScaf=0, nMt=0, nUnk=0, nGap=0; // for numbering output seqid and totals 
	private int cntChr=0, cntScaf=0, cntMtPt=0, cntUnk=0; // for counting regardless of write to file
	private long chrLen=0, scafLen=0, mtptLen=0, unkLen=0, totalLen=0;
	
	private HashMap <String, String> hexMap = new HashMap <String, String> (); // CAS548 add
	
	private TreeMap <Character, Integer> cntBase = new TreeMap <Character, Integer> ();
	private PrintWriter fhOut, ghOut;
	private PrintWriter logFile=null;
	
	private boolean bSuccess=true;
	
	/************************************************************************/
	public static void main(String[] args) {
		isToSymap=false;
		new ConvertEnsembl(args, defGapLen, null, false);
	}
	protected ConvertEnsembl(String[] args, int gapLen, String prefix, boolean bNum) { // CAS557 make accessible from ConvertFrame
		if (args.length==0) { // command line
			checkArgs(args);
			return;
		}
		gapMinLen = gapLen;
		prefixOnly = prefix;
		bNumOnly = bNum;
		
		projDir = args[0];
		prt("\n------ ConvertEnsembl " + projDir + " ------");
		checkInitFiles();	if (!bSuccess) return;
		
		//hexMap.put("%09", " "); // tab break on this
		hexMap.put("%3D", "-"); // = but cannot have these in attr, so use dash
		hexMap.put("%0A", " "); // newline
		hexMap.put("%25", "%"); // %
		hexMap.put("%2C", ","); // ,
		hexMap.put("%3B", ","); // ; but cannot have these in attr, so use comma
		hexMap.put("%26", "&"); // &
		
		createLog(args); 	if (!bSuccess) return;
		checkArgs(args);	if (!bSuccess) return;
		
		rwFasta();
		
		rwAnno();			if (!bSuccess) return;
		
		printSummary();
		
		prt("");
		printSuggestions();
		prt("------ Finish ConvertEnsembl " + projDir + " -------");
		if (logFile!=null) logFile.close();
	}
	private void printSuggestions() {
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
		if (cntOutSeq==0) prt("Are these Ensembl files? Should scaffolds be included? Should 'Prefix only' be set?");
		else if (cntOutSeq>30)  prt( "Suggestion: There are " + String.format("%,d",cntOutSeq) + " sequences. "
				+ "Set SyMAP project parameter 'Minimum length' to reduce number loaded.");
	}
	/**
	 * * Fasta file example:
	 * >1 dna_sm:chromosome chromosome:IRGSP-1.0:1:1:43270923:1 REF
	 * >Mt dna_sm:chromosome chromosome:IRGSP-1.0:Mt:1:490520:1 REF
	 * >Syng_TIGR_043 dna_sm:scaffold scaffold:IRGSP-1.0:Syng_TIGR_043:1:4236:1 REF
	 */
	private void rwFasta() {
		try {
			cntBase = new TreeMap <Character, Integer> ();
			char [] base = {'A', 'C', 'G', 'T', 'N', 'a', 'c', 'g', 't', 'n'};
			for (char b : base) cntBase.put(b, 0);
		
			fhOut = new PrintWriter(new FileOutputStream(seqDir+outFaFile, false)); 
			fhOut.println(header); 
			
			ghOut = new PrintWriter(new FileOutputStream(annoDir + outGapFile, false));
			ghOut.println(header); 
			
			for (String file : inFaFileVec) {
				rwFasta(file);
			}
			fhOut.close(); ghOut.close();
    		System.err.print("                                            \r");
    		String xx = (VERBOSE) ? "(" + star + ")" : "";
    		prt(String.format("Sequences not output: %,d  %s", cntNoOutSeq, xx));
    		prt("Finish writing " + seqDir + outFaFile + "                          ");
    		
    		prt("");
    		prt( String.format("A %,11d  a %,11d", cntBase.get('A'), cntBase.get('a')) );
			prt( String.format("T %,11d  t %,11d", cntBase.get('T'), cntBase.get('t')) );
			prt( String.format("C %,11d  c %,11d", cntBase.get('C'), cntBase.get('c')) );
			prt( String.format("G %,11d  g %,11d", cntBase.get('G'), cntBase.get('g')) );
			prt( String.format("N %,11d  n %,11d", cntBase.get('N'), cntBase.get('n') ));
			String other=""; 
			for (char b : cntBase.keySet()) {
				boolean found = false;
				for (char x : base) if (x==b) {found=true; break;}
				if (!found) other += String.format("%c %,d  ", b, cntBase.get(b));
			}
			if (other!="") prt(other);
			
			cntBase.clear();
			prt(String.format("Total %,d", totalLen));
		
			prt("                                                        ");
			prt(String.format("Gaps >= %,d: %,d (using N and n)", gapMinLen, nGap));
			prt("Finish writing " + annoDir + outGapFile + "                     ");
		}
		catch (Exception e) {die(e, "rwFasta");}
	}
	private void rwFasta(String fileName) {
	try {
		prt("Processing " + fileName);
		
		String prtName="", idCol1="",  line;
		boolean bPrt=false, isChr=false, isScaf=false, isMtPt=false;
		boolean bMt=false, bPt=false;
		int baseLoc=0, gapStart=0, gapCnt=1, len=0, lineN=0;
		
		BufferedReader fhIn = openGZIP(fileName); if (!bSuccess) return;
		
		while ((line = fhIn.readLine()) != null) {
			lineN++;
			if (line.startsWith("!") || line.startsWith("#") || line.trim().equals("")) continue;
			
			if (line.startsWith(">")) {
				if (len>0) {
					totalLen += len;
					printTrace(bPrt, isChr, isScaf, isMtPt, len, idCol1, prtName);
					len=0;
				}
				bPrt=false;
				String line1 = line.substring(1).trim();
				String [] tok = line1.split("\\s+");
				if (tok.length==0) {
					die("Header line is blank: line #" + lineN);
					return;
				}
				
				idCol1 = tok[0].trim();	
				
				isMtPt = idCol1.toLowerCase().startsWith("mt") // CAS557 MT or mtDNA... and add line check
						|| idCol1.toLowerCase().startsWith("pt") 
				        || line.contains("mitochondrion")
				        || line.contains("plastid")
				        || line.contains("mitochondrial")
				        || line.contains("chloroplast");
				
				isChr  = isChrNum(idCol1) || line.contains("chromosome"); // CAS557 start checking for word
				
				isScaf = line.contains("scaffold"); 
				
				if (isChr && !isMtPt) cntChr++;
				else if (isMtPt) cntMtPt++;
				else if (isScaf) cntScaf++;
				else cntUnk++;
				
				if (prefixOnly!=null) { // CAS557 new parameter
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
					bPrt=true; 
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
					bPrt=true;
				}
				else if (isScaf && INCLUDESCAF) {
					nScaf++; 
					prtName = scafPrefix + padNum(nScaf+""); // scaf use name
					
    				id2scaf.put(idCol1, prtName);
    				cntScafGene.put(idCol1, 0);
    				gapStart=1; gapCnt=0; baseLoc=0;
    				
					fhOut.println(">" + prtName+ "  " + idCol1 + " " + scafType);
					bPrt=true; 
				}
				else {
					if (isScaf) 		{prtName="Scaf";}
					else if (isMtPt) 	{prtName="Mt/Pt";}
					else 				{prtName="Unk";}
					
					if (prefixOnly!=null) {// use chr, don't know what it is
						nUnk++;
						prtName = unkPrefix + padNum(nUnk+""); // but no prefix
						id2chr.put(idCol1, prtName); 
	    				cntChrGene.put(idCol1, 0);
	    				
	    				bPrt=true;
    					fhOut.println(">" + prtName+ "  " + idCol1 + " " + unkType);
					}
				}
				if (bPrt) cntOutSeq++; else cntNoOutSeq++;
			}
			//////////////////////////////////////////////////////////////////
			else {
				String aline = line.trim();
				if (bPrt) {
    				char [] bases = aline.toCharArray();
    				
    				for (int i =0 ; i<bases.length; i++) {
    					char b = bases[i];

    					if (cntBase.containsKey(b)) {
    						int cnt = cntBase.get(b);
    						if (cnt<Integer.MAX_VALUE) cntBase.put(b, cnt+1);
    					}
    					else 						cntBase.put(b, 1);
    					
    					baseLoc++;
    					if (b=='N' || b=='n') { 
    						if (gapStart==0) 	gapStart = baseLoc;
    						else 				gapCnt++;
    					}
    					else if (gapStart>0) {						// gaps 
    						if (gapCnt>gapMinLen) {
    							nGap++;
    							String x = createGap(prtName, gapStart, gapCnt);
    							if (bPrt) ghOut.println(x);
    						}
    						gapStart=0; gapCnt=1;
    					}
    				}
				}
				len += aline.length();
				if (bPrt) fhOut.println(aline);
			}
		}
		if (len>0) {
			totalLen += len;
			printTrace(bPrt, isChr, isScaf, isMtPt, len, idCol1, prtName);
		}
		fhIn.close();
	}
	catch (Exception e) {die(e, "rwFasta");}
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
			if (x.length()==1) return "0" + x;
			else return x;
		}
		catch (Exception e) {
			return x;
		} // could be X or Y
	}
	
	private void printTrace(boolean bPrt, boolean isChr, boolean isScaf, boolean isMtPt, int len, String id, String prtname) {
		String x = (bPrt) ? "" : star;
		String msg = String.format("%-7s %-15s %,11d %s", prtname, id, len,x);
		String msg2 = String.format("   %s %,d %s ....                       ", prtname, len,x);
		if (isChr && !isMtPt) {
			chrLen+=len;
			if (bPrt || VERBOSE) {
				if (cntChr<=traceNum) prt(msg);
				else if (cntChr==traceNum+1) prt("Suppressing further chromosome logs");
				else System.out.print(msg2 + "\r");
			}
			else System.out.print(msg2 + "\r");
		}
		else if (isMtPt) {
			mtptLen+=len;
			if (bPrt || VERBOSE) {
				if (cntMtPt<=traceNum) prt(msg);
				else if (cntMtPt==traceNum+1) prt("Suppressing further Mt/Pt logs");
				else System.out.print(msg2 + "\r");
			}
		}
		else if (isScaf) {
			scafLen+=len;
			if (len<scafMaxLen) cntScafSmall++;
			if (bPrt || VERBOSE) {
				if (cntScaf<=traceNum) 	prt(msg);
				else if (cntScaf==traceNum+1) prt("Suppressing further scaffold logs");
				else System.out.print(msg2 + "\r");
			}
			else if (len>1000000) System.out.print(msg2 + "\r");
		}
		else {
			unkLen += len;
			if (bPrt || VERBOSE) {
				if (cntUnk<=traceNum) prt(msg);
				else if (cntUnk==traceNum+1) 	  prt("Suppressing further unknown logs");
				else System.out.print(msg2 + "\r");
			}
			else if (len>1000000) System.out.print(msg2 + "\r");
		}
	}
	
	/********************************************************************************
	 * XXX GFF file, e.g.
1  araport11   gene    3631    5899    .       +       .       ID=gene:AT1G01010;Name=NAC001;biotype=protein_coding;description=NAC domain containing protein 1 [Source:NCBI gene (formerly Entrezgene)%3BAcc:839580];gene_id=AT1G01010;logic_name=araport11
1  araport11   mRNA    3631    5899    .       +       .       ID=transcript:AT1G01010.1;Parent=gene:AT1G01010;Name=NAC001-201;biotype=protein_coding;transcript_id=AT1G01010.1
1  araport11   five_prime_UTR  3631    3759    .       +       .       Parent=transcript:AT1G01010.1
1  araport11   exon    3631    3913    .       +       .       Parent=transcript:AT1G01010.1;Name=AT1G01010.1.exon1;constitutive=1;ensembl_end_phase=1;ensembl_phase=-1;exon_id=AT1G01010.1.exon1;rank=1
	 */
	private int cntGene=0, cntExon=0, cntMRNA=0, cntGeneNotOnSeq=0, cntNoDesc=0;
	private int skipLine=0;
	private PrintWriter fhOutGff=null;
	private int cntReadGene=0, cntReadMRNA=0, cntReadExon=0;
	
	// per gene
	private String geneID="",geneLine="", gchr="",  gproteinAt="", gmrnaAt="";
	private String mrnaLine="", mrnaID="";
	private int cntThisGeneMRNA=0;
	private Vector <String> exonVec = new Vector <String> ();	 
	
	private void rwAnno() {
		if (inGffFileVec==null || inGffFileVec.size()==0) return; 
	try {
		fhOutGff = new PrintWriter(new FileOutputStream(annoDir + outGffFile, false));
		fhOutGff.println(header);
		
		for (String file : inGffFileVec) {
			rwAnno(file);
		}
		fhOutGff.close();
		
		prt(String.format("   Use Gene %,d from %,d                ", cntGene, cntReadGene));
		prt(String.format("   Use mRNA %,d from %,d                ", cntMRNA, cntReadMRNA));
		prt(String.format("   Use Exon %,d from %,d                ", cntExon, cntReadExon));
		prt("Finish writing " + annoDir + outGffFile + "                     ");
	}
	catch (Exception e) {die(e, "rwAnno");}	
	}
	private void rwAnno(String file) {
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
				else die("too many errors");
				continue;
			}
			
			// Count everything here for summary
			type =  tok[2].trim();  		// gene, mRNA, exon...
			if (!allTypeCnt.containsKey(type))  allTypeCnt.put(type,1);
			else 								allTypeCnt.put(type, allTypeCnt.get(type)+1);
			
			if (type.equals(geneType)) {
				rwAnnoOut(fhOutGff);
				rwAnnoGene(tok, line);
				cntReadGene++;
			}
			else if (type.equals(mrnaType)) {
				rwAnnoMRNA(tok, line);
				cntReadMRNA++; cntThisGeneMRNA++;
			}
			else if (type.equals(cdsType)) {
				rwAnnoCDS(tok, line);
			}
			else if (type.equals(exonType)) {
				rwAnnoExon(tok, line);
				cntReadExon++;
			}	
		 }
		fhIn.close(); 
	}
	catch (Exception e) {die(e, "rwAnnoGene");}
	}
	
	private void rwAnnoGene(String [] tok, String line) { 
		try {	
			cntGene++;
			if (cntGene%PRT==0) System.err.print("   Process " + cntGene + " genes...\r");
			
			// counts for gene
			String [] typeAttrs = tok[8].trim().split(";"); 
			String biotype = getVal(biotypeAttrKey, typeAttrs); 
			if (!allGeneBiotypeCnt.containsKey(biotype)) allGeneBiotypeCnt.put(biotype,1);
			else 										 allGeneBiotypeCnt.put(biotype, allGeneBiotypeCnt.get(biotype)+1);
			
			String src  =  tok[1].trim();  // RefSeq, Gnomon..
			
			if (!allGeneSrcCnt.containsKey(src)) 	allGeneSrcCnt.put(src,1);
			else 									allGeneSrcCnt.put(src, allGeneSrcCnt.get(src)+1);
			
			if (!biotype.equals(biotypeAttr)) return; // protein-coding
			
			String idcol1 =   tok[0];  // chromosome, scaffold, linkage group...
			if (id2chr.containsKey(idcol1))  { 
				cntChrGeneAll++;
				gchr = id2chr.get(idcol1);
				cntChrGene.put(idcol1, cntChrGene.get(idcol1)+1);
			}
			else if (id2scaf.containsKey(idcol1)) {
				cntScafGeneAll++; 
				gchr = id2scaf.get(idcol1);
				cntScafGene.put(idcol1, cntScafGene.get(idcol1)+1);
			}
			else {
				cntGeneNotOnSeq++; 
				return;
			}
			geneID =  getVal(idAttrKey, typeAttrs);
			String gid = geneID.replace(RMGENE,"");
			String attr = idAttrKey + "=" + gid + ";" + getKeyVal(nameAttrKey, typeAttrs);
			
			String desc = getVal(descAttrKey, typeAttrs).trim();
			if (desc==null || desc.equals("")) { 
				cntNoDesc++;
			}
			else {
				if (desc.contains(source)) desc = desc.substring(0, desc.lastIndexOf(source)).trim(); // CAS558 change to last
				
				if (desc.contains("%")) { // CAS548 add
					for (String hex : hexMap.keySet()) {
						if (desc.contains(hex)) desc = desc.replace(hex, hexMap.get(hex));
					}
				}
				attr += ";" + DESC + desc;
			}
			
			geneLine =  "###\n" + gchr + "\t" + tok[1] + "\t" + tok[2] + "\t" + tok[3] + "\t" + tok[4]
			+ "\t" + tok[5] + "\t" + tok[6] + "\t" + tok[7] + "\t" + attr;
		 }
		 catch (Exception e) {die(e, "rwAnnoGene");}
	 }
	 // mRNA is not loaded into SyMAP, but needed for exon parentID
	 private void rwAnnoMRNA(String [] tok, String line) { 
		 try {
			if (!mrnaID.equals("")) return;
			
			String [] attrs = tok[8].split(";"); 
			String pid = getVal(parentAttrKey, attrs);
				
			if (!geneID.equals(pid)) return;
			
			////////
			mrnaID = getVal(idAttrKey, attrs);
			gmrnaAt = ";" + MRNAID + mrnaID.replace(RMTRAN,"");
			
			String mid = idAttrKey + "=" + mrnaID.replace(RMTRAN,"");
			String gid = parentAttrKey + "=" + geneID.replace(RMGENE,"");
			String newAttrs = mid + ";" + gid;
			mrnaLine = gchr + "\t" +  tok[1] + "\t" + tok[2] + "\t" + tok[3] + "\t" + 
					tok[4] + "\t" + tok[5] + "\t" + tok[6] + "\t" + tok[7] + "\t" + newAttrs;
		
			cntMRNA++;
		 }
		 catch (Exception e) {die(e, "rwAnnoMRNA");}
	 }
	 private void rwAnnoCDS(String [] tok, String line) { 
		 try {
			if (!ATTRPROT || mrnaID.equals("")) return;
			
			String [] attrs = tok[8].split(";"); 
			String pid = getVal(parentAttrKey, attrs);
				
			if (mrnaID.equals(pid)) {
				gproteinAt = PROTEINID + getVal(cdsProteinAttrKey, attrs);
			}
		 }
		 catch (Exception e) {die(e, "rwAnnoMRNA");}
	 }
	 /** Write genes and exons to file**/
	 private void rwAnnoExon(String [] tok, String line) {
		 try {
			if (mrnaID.equals("")) return;
				
			String [] attrs = tok[8].split(";"); 
			String pid = getVal(parentAttrKey, attrs);
			if (!mrnaID.equals(pid)) return;
			
			//////////////////
			String newAttrs = parentAttrKey + "=" + mrnaID.replace(RMTRAN,"");
			
			String nLine = gchr + "\t" + tok[1] + "\t" + tok[2] + "\t" + tok[3] + "\t" + tok[4]
						+ "\t" + tok[5] + "\t" + tok[6] + "\t" + tok[7] + "\t" + newAttrs;
			exonVec.add(nLine);
			cntExon++;
		 }
		 catch (Exception e) {die(e, "rwAnnoExon");} 
	 }
	 private void rwAnnoOut( PrintWriter fhOutGff) {
	 try {
		if (geneID.equals("")) return;
		if (mrnaID.equals("")) return;
		
		gmrnaAt = gmrnaAt.replace(RMTRAN,"");
		gmrnaAt += " (" + cntThisGeneMRNA + ");";
		String line = geneLine + gmrnaAt + gproteinAt;
		fhOutGff.println(line);
		
		fhOutGff.println(mrnaLine); 
		
		for (String eline : exonVec) fhOutGff.println(eline);
		
		cntThisGeneMRNA=0;
		geneID=geneLine=gchr=gproteinAt=gmrnaAt=mrnaID=mrnaLine="";
		exonVec.clear();
	 }
	 catch (Exception e) {die(e, "rwAnnoOut");} 
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
	
	/*****************************************************************
	 * Summary
	 */
	private void printSummary() {
		prt("                                         ");
		if (VERBOSE) {
			prt(">>Sequences ");
			if (cntChr>0) {
				prt(String.format("  %,6d Output %-,6d %-11s  %,15d", cntChr, nChr, "Chromosomes", chrLen));
			}
			if (cntMtPt>0) {
				prt(String.format("  %,6d Output %-,6d %-11s  %,15d", cntMtPt, nMt, "Mt/Pt", mtptLen));
			}
			if (cntScaf>0) {
				prt(String.format("  %,6d Output %-,6d %-11s  %,15d (%,d < %,dbp)", cntScaf,nScaf, "Scaffolds", scafLen, cntScafSmall, scafMaxLen));
			}
			if (cntUnk>0) {
				prt(String.format("  %,6d Output %-,6d %-11s  %,15d", cntUnk, nUnk, "Unknown", unkLen));
			}
			if (inGffFileVec==null) return;
			
			prt("                                         ");
			prt(">>All Types  (col 3)  (" + plus + " are processed keywords)");
			for (String key : allTypeCnt.keySet()) {
				boolean bKey = ATTRPROT && key.equals(cdsType);
				String x = (key.equals(geneType) || key.equals(mrnaType) || key.equals(exonType)) || bKey ? plus : "";
				prt(String.format("   %-22s %,8d %s", key, allTypeCnt.get(key), x));
			}
			
			prt(">>All Gene Source (col 2)");
			for (String key :allGeneSrcCnt.keySet()) {
				prt(String.format("   %-22s %,8d", key, allGeneSrcCnt.get(key)));
			}
			
			prt(">>All gene biotype= (col 8)                    ");
			for (String key : allGeneBiotypeCnt.keySet()) {
				String x = (key.equals(biotypeAttr)) ? plus : "";
				prt(String.format("   %-22s %,8d %s", key, allGeneBiotypeCnt.get(key), x));
			}
			
			if (cntNoDesc>0) {
				prt(">>Description");
				prt(String.format("   %-22s %,8d", "None", cntNoDesc));
			}
		}
		prt(String.format(">>Chromosome gene count %,d", cntChrGeneAll));
		
		for (String prt : chr2id.keySet()) {
			String id = chr2id.get(prt); // sorted on prtName
			int cnt   = cntChrGene.get(id);
			prt(String.format("   %-10s %-20s %,8d", prt, id, cnt));
		}
		
		if (INCLUDESCAF) {
			prt(String.format(">>Scaffold gene count %,d (list scaffolds with #genes>%d)", cntScafGeneAll, traceNumGenes));
			int cntOne=0;
			for (String key : cntScafGene.keySet()) { // sorted on idCol1, which is not scaffold order
				int cnt = cntScafGene.get(key);
				if (cnt>traceNumGenes) prt(String.format("   %-7s %-15s %,8d", id2scaf.get(key), key, cnt));
				else cntOne++;
			}
			prt(String.format("   Scaffolds with <=%d gene (not listed) %,8d", traceNumGenes, cntOne));
			prt(String.format("   Genes not included %,8d",  cntGeneNotOnSeq));
		}
		else prt(String.format("   Genes not on Chromosome %,8d",  cntGeneNotOnSeq));
	}
	/************************************************************
	 * File stuff
	 */
	private boolean checkInitFiles() {
		// find fasta and gff files
		try {
			if (projDir==null || projDir.trim().equals("")) 
				return die("No project directory");
			
			File dir = new File(projDir);
			if (!dir.isDirectory()) 
				return die("The argument must be a directory. " + projDir + " is not a directory.");
				
			File[] files = dir.listFiles();
			for (File f : files) {
				if (f.isFile() && !f.isHidden()) { 
			       String fname = f.getName();
			     
			       if (fname.endsWith(faSuffix + ".gz") || fname.endsWith(faSuffix)) inFaFileVec.add(projDir + "/" + fname);
			       else if (fname.endsWith(gffSuffix + ".gz") || fname.endsWith(gffSuffix)) inGffFileVec.add(projDir + "/" +fname); 	
				} 
			}
		}
		catch (Exception e) {die(e, "Checking files");}
		
		if (inFaFileVec.size()==0)  
			return die("Project directory " + projDir + ": no file ending with " + faSuffix + " or "+ faSuffix + ".gz (i.e. Ensembl files)");
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
		}
		catch (Exception e) {die("Cannot open " + logFileName); logFile=null;}
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
				die("*** Failed to create directory '" + nDir.getAbsolutePath() + "'.");
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
	private BufferedReader openGZIP(String file) {
		try {
			if (!file.endsWith(".gz")) {
				File f = new File (file);
				if (f.exists()) return new BufferedReader ( new FileReader (f));
				else die("Cannot open file " + file);
			}
			else if (file.endsWith(".gz")) {
				FileInputStream fin = new FileInputStream(file);
				GZIPInputStream gzis = new GZIPInputStream(fin);
				InputStreamReader xover = new InputStreamReader(gzis);
				return new BufferedReader(xover);
			}
			else die("Do not recognize file suffix: " + file);
		}
		catch (Exception e) {die(e, "Cannot open file " + file);}
		return null;
	}
	
	/******************************************************************
	 * Args print and process
	 */
	private void checkArgs(String [] args) {
		if (args.length==0 || args[0].equals("-h") || args[0].equals("-help") || args[0].equals("help")) {
			System.out.println("\nConvertEnsembl <project directory> [-r] [-c] [-v] 				");
			System.out.println(
					"   the project directory must contain the FASTA file and the GFF is optional:  \n" +
					"       FASTA file ending with .fa   or .fa.gz   e.g. Oryza_sativa.IRGSP-1.0.dna_sm.toplevel.fa.gz\n" +
					"       GFF   file ending with .gff3 or .gff3.gz e.g. Oryza_sativa.IRGSP-1.0.45.chr.gff3.gz\n" +  
					"   Options:\n" +
					"   -s  include any sequence with the header contains 'scaffold'.\n" +
					"   -t  include Mt and Pt chromosomes.\n" + 
					"   -p  include the protein name (1st CDS) in the attribute field.\n" +
					"   -v  write header lines of ignored sequences [default false].\n" +
					"\nSee https://csoderlund.github.io/SyMAP/convert for details.");
			System.exit(0);
		}
		
		prt("Parameters:");
		prt("   Project Directory: " + args[0]);
		projDir = args[0];
		
		if (gapMinLen!=defGapLen) prt("   Gap size: " + gapMinLen); // argument to Convert
		if (prefixOnly!=null)     prt("   Prefix Only: " + prefixOnly); // set in main
		if (bNumOnly)			  prt("   Only number, X, Y, Roman"); // set in main
		
		if (args.length>1) {
			for (int i=1; i< args.length; i++) {
				if (args[i].equals("-v")) {
					VERBOSE=true;
					prt("   Verbose ");
					traceNum=20;
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
				else if (args[i].equals("-p")) {
					ATTRPROT=true; 
					prt("   Include protein-id in attributes");
				}
			}
		}   
	}
	private boolean die(Exception e, String msg) {
		System.err.println("Fatal error -- " + msg);
		e.printStackTrace();
		if (!isToSymap) System.exit(-1);
		bSuccess = false;
		return false;
	}
	 private boolean die(String msg) {
		System.err.println("Fatal error -- " + msg);
		if (!isToSymap) System.exit(-1);
		bSuccess = false;
		return false;
	}
	private void prt(String msg) {
		System.out.println(msg);
		if (logFile!=null) logFile.println(msg);
	}
}
