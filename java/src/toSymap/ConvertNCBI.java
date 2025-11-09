package toSymap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.zip.GZIPInputStream;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*************************************************************
 * ConvertNCBI: NCBI genome files formatted for SyMAP
 * See https://csoderlund.github.io/SyMAP/input/ncbi.html
 * 
 * Called from xToSymap, but can be used stand-alone.
 * 
 * This assumes input of the project directory, which has either
 * 1. The ncbi_dataset.zip unzipped, .e.g 
 *    /data/seq/Arab/ncbi_dataset/data/GCF_000001735.4
 * or the .fna and .gff files directly under the project directory
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
   			rnaID= first mRNA ID (cnt of number of mRNAs)
   			desc=gene description or 1st mRNA product
   			protein-ID=1st CDS for 1st mRNA 
 */

public class ConvertNCBI {
	static private boolean isToSymap=true;
	static private int defGapLen=30000;
	
	private final String header = "### Written by SyMAP ConvertNCBI"; // Split expects this to be 1st line
	private String logFileName = "/xConvertNCBI.log";
	private final String plus ="+", star="*"; 
	
	//output - the projDir gets appended to the front
	private String seqDir =   	"sequence";		// Default sequence directory for symap
	private String annoDir =  	"annotation";   // Default annotation directory for symap
	private final String outFaFile =  "/genomic.fna"; // Split expects this name
	private final String outGffFile = "/anno.gff";	  // Split expects this name
	private final String outGapFile = "/gap.gff";
			
	// downloaded via NCBI Dataset link
	private final String ncbi_dataset="ncbi_dataset"; 
	private final String ncbi_data=	"/data"; 
	private final String faSuffix = ".fna";
	private final String gffSuffix = ".gff";
	private String subDir=null;
	
	// args (can be set from command line)
	private boolean INCLUDESCAF = false;
	private boolean INCLUDEMtPt = false; 
	private boolean MASKED = false;
	private boolean ATTRPROT = false;
	private boolean VERBOSE = false;
	
	// args  (if running standalone, change in main)
	private int gapMinLen;          // Print to gap.gff if #N's is greater than this
	private String prefixOnly=null; // e.g. NC
	
	// Changed for specific input; if unknown and prefixOnly, no suffix
	private String chrPrefix="Chr", chrPrefix2="C", chrType="chromosome";	// These are checked in Split
	private String scafPrefix="s",  scafType="scaffold";  // single letter is used because the "Chr" is usually removed to save space
	private String unkPrefix="Unk", unkType="unknown";
	
	private int traceNum=5;	// if verbose, change to 20
	private final int traceScafMaxLen=10000;	// All scaffold are printed to fasta file, but only summarized if >scafMax
	private final int traceNumGenes=3;  // Only print sequence if at least this many genes
	
	// types
	private final String geneType = "gene";
	private final String mrnaType = "mRNA";
	private final String exonType = "exon";
	private final String cdsType  = "CDS";  
	
	// attribute keywords
	private final String idAttrKey 		= "ID";
	private final String nameAttrKey 	= "Name"; 
	private final String biotypeAttrKey	= "gene_biotype";
	private final String biotypeAttr 	= "protein_coding";
	private final String parentAttrKey 	= "Parent";
	private final String productAttrKey = "product"; 
	private final String descAttrKey 	= "description"; 
	private final String exonGeneAttrKey = "gene";
	private final String cdsProteinAttrKey = "protein_id";

	private final String PROTEINID = "proteinID="; // keywords for gene attributes
	private final String MRNAID    = "rnaID="; 	   // ditto
	private final String DESC	   = "desc=";	   // ditto
	private final String RMGENE	   = "gene-";
	private final String RMRNA	   = "rna-";
	
	// input 
	private String projDir = null;
	private String inFaFile = null;
	private String inGffFile = null;
	
	// Other global variables
	private TreeMap <String, String> chr2id = new TreeMap <String, String> ();
	private TreeMap <String, String> id2chr = new TreeMap <String, String> ();
	private TreeMap <String, String> id2scaf = new TreeMap <String, String> ();
	private TreeMap <String, Integer> cntChrGene = 	new TreeMap <String, Integer> ();
	private TreeMap <String, Integer> cntScafGene = new TreeMap <String, Integer> ();
	
	// Summary
	private TreeMap <String, Integer> allTypeCnt = new TreeMap <String, Integer> ();
	private TreeMap <String, Integer> allGeneBiotypeCnt = new TreeMap <String, Integer> ();
	private TreeMap <String, Integer> allGeneSrcCnt = new TreeMap <String, Integer> ();
	private int cntChrGeneAll=0, cntScafGeneAll=0, cntGeneNotOnSeq=0, cntScafSmall=0;
	
	private int nChr=0, nScaf=0, nMt=0, nUnk=0, nGap=0; // for numbering output seqid and totals 
	private int cntChr=0, cntScaf=0, cntMtPt=0, cntUnk=0, cntOutSeq=0, cntNoOutSeq=0; 
	private long chrLen=0, scafLen=0, mtptLen=0, unkLen=0, totalLen=0;
	  
	private PrintWriter fhOut, ghOut;
	private PrintWriter logFile=null;
	
	private int cntMask=0;
	private TreeMap <Character, Integer> cntBase = new TreeMap <Character, Integer> ();
	private HashMap <String, String> hexMap = new HashMap <String, String> ();
	
	// anno per gene
	private String geneLine="";						
	private String geneID="", gchr="",  gproteinAt="", gmrnaAt="", gproductAt="";
	private String mrnaLine="", mrnaID="";
	private Vector <String> exonVec = new Vector <String> ();
	private int cntUseGene=0, cntUseMRNA=0, cntUseExon=0, cntThisGeneMRNA=0;
				  	
	private boolean bSuccess=true;
	private final int PRT = 10000;
	
	public static void main(String[] args) { 
		isToSymap=false;
		new ConvertNCBI(args, defGapLen, null);
	}
	protected ConvertNCBI(String [] args, int gapLen, String prefix) {
		if (args.length==0) { // command line
			checkArgs(args);
			return;
		}
		gapMinLen = gapLen;
		prefixOnly = prefix;
		
		projDir = args[0];
		prt("\n------ ConvertNCBI " + projDir + " ------");
		if (!checkInitFiles() || !bSuccess) return;
		
		//hexMap.put("%09", " "); // tab - break on this
		hexMap.put("%3D", "-"); // = but cannot have these in attr, so use dash
		hexMap.put("%0A", " "); // newline
		hexMap.put("%25", "%"); // %
		hexMap.put("%2C", ","); // ,
		hexMap.put("%3B", ","); // ; but cannot have these in attr, so use comma
		hexMap.put("%26", "&"); // &
		
		createLog(args); if (!bSuccess) return;
		checkArgs(args); if (!bSuccess) return;
		
		rwFasta();		 if (!bSuccess) return;
		
		rwAnno();		 if (!bSuccess) return;
		
		printSummary();
		
		prt("");
		printSuggestions();
		prt("------ Finish ConvertNCBI " + projDir + " ------");
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
		
		if (cntOutSeq==0) prt("Are these NCBI files? Should scaffolds be included? Should 'Prefix only' be set?");
		else 
			if (cntOutSeq>30)  prt( "Suggestion: There are " + String.format("%,d",cntOutSeq) + " sequences. "
				+ "Set SyMAP project parameter 'Minimum length' to reduce number loaded.");
	}
	
	/***************************************************************************
	 * * Fasta file example:
	 * >NC_016131.2 Brachypodium distachyon strain Bd21 chromosome 1, Brachypodium_distachyon_v2.0, whole genome shotgun sequence
	 * >NW_014576703.1 Brachypodium distachyon strain Bd21 unplaced genomic scaffold, Brachypodium_distachyon_v2.0 super_11, whole genome shotgun sequence *
	 * NOTE: NCBI files are soft-masked, which is used to compute the gaps.
	 *       If its then hard-masked (soft->N's), gaps and hard-masked appear the same 
	 */
	private void rwFasta() {
		try {
			fhOut = new PrintWriter(new FileOutputStream(seqDir + outFaFile, false));
			fhOut.println(header); 
			ghOut = new PrintWriter(new FileOutputStream(annoDir + outGapFile, false));
			ghOut.println(header); 
			
			char [] base = {'A', 'C', 'G', 'T', 'N', 'a', 'c', 'g', 't', 'n'};
			for (char b : base) cntBase.put(b, 0);
			
			// Process file
			rwFasta(inFaFile); if (!bSuccess) return;
		
			fhOut.close(); ghOut.close();
			
			// 1st part of summary
			String xx = (VERBOSE) ? "(" + star + ")" : "";
			prt(String.format("Sequences not output: %,d   %s", cntNoOutSeq, xx));
			prt("Finish writing " + seqDir + outFaFile + "                     ");
			
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
			
			if (MASKED) prt(String.format("Hard masked: %,d lower case changed to N", cntMask));
			
			prt("");
			prt(String.format("Gaps >= %,d: %,d", gapMinLen, nGap));
			prt("Finish writing " + annoDir + outGapFile + "                     ");
		}
		catch (Exception e) {e.printStackTrace(); die("rwFasta");}
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
			boolean bPrt=false, isChr=false, isScaf=false, isMT=false;
			boolean bMt=false, bPt=false;
			int baseLoc=0, gapStart=0, gapCnt=1;
			
			while ((line = fhIn.readLine()) != null) {
				lineN++;
				if (line.startsWith("!") || line.startsWith("#") || line.trim().equals("")) continue;
				
    			if (line.startsWith(">")) { // id line
    				if (len>0) {
    					totalLen += len;
    					printTrace(bPrt, isChr, isScaf, isMT, len, idcol1, prtName);
    					len=0;
    				}
    				String line1 = line.substring(1).trim();
    				String [] tok = line1.split("\\s+");
    				if (tok.length==0) {
    					die("Header line is blank: line #" + lineN);
    					return;
    				}
    				idcol1 = tok[0];
    				
    				isChr=isScaf=isMT=false; 
    				
    				if (idcol1.startsWith("NC_")) isChr=true; 
    				else if (idcol1.startsWith("NW_") || idcol1.startsWith("NT_") ) isScaf=true;
    				
    				if (isChr) {
	    				if  (line.contains("mitochondrion") || line.contains("mitochondrial")
	    					 || line.contains("plastid") || line.contains("chloroplast")) isMT=true; // Mt/Pt, etc are NC_, but not 'chromosome'
    				}
    				if (isChr) cntChr++;
    				else if (isScaf) cntScaf++;
    				else if (isMT) cntMtPt++;
    				else cntUnk++;
    				
    				if (prefixOnly!=null) { 
    					if (!idcol1.startsWith(prefixOnly)) {
    						cntNoOutSeq++;
    						continue;
    					}
    				}
    				
    				gapStart=1; gapCnt=0; baseLoc=0;
    				bPrt=false; 
    				if (isChr && !isMT) { 
    					nChr++; 
    					seqType=chrType;
    					prtName = createChrPrtName(line);
	    				id2chr.put(idcol1, prtName);
	    				chr2id.put(prtName, idcol1);
	    				cntChrGene.put(idcol1, 0);
	    				
	    				bPrt=true;	
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
	    				bPrt=true;	
    				}
    				else if (isScaf && INCLUDESCAF) {
    					nScaf++;  
	    				prtName = scafPrefix + padNum(nScaf+"");
	    				id2scaf.put(idcol1, prtName);
	    				cntScafGene.put(idcol1, 0);
	    				
	    				seqType=scafType;
    					bPrt=true;
    				}
    				else {
    					if (isScaf)    {prtName=scafPrefix;}
    					else if (isMT) {prtName="Mt/Pt";}
    					else 		   {prtName=unkPrefix;}
    					
    					if (prefixOnly!=null) {// use chr, don't know what it is
    						nUnk++;
    						prtName = unkPrefix + padNum(nUnk+"");
    						id2chr.put(idcol1, prtName); 
    	    				cntChrGene.put(idcol1, 0);
    	    				seqType=unkType;
    	    				bPrt=true;
    					}
    				}
    				if (bPrt) {
    					fhOut.println(">" + prtName + " " + idcol1 + " " + seqType);
    					cntOutSeq++;
    				}
    				else cntNoOutSeq++;
    			} // finish header line
    			//////////////////////////////////////////////////////////////////////
    			else  { // sequence line
    				String aline = line.trim();
    				if (bPrt) {
	    				char [] bases = aline.toCharArray();
	    				
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
	    					if (MASKED) {
	    						if (b=='a' || b=='c' || b=='t' || b=='g' || b=='n') {
	    							bases[i]='N';
	    							cntMask++;
	    						}
	    					}
	    				}
	    				if (MASKED) aline = new String(bases);
    				}
    				
    				len += aline.length();
    				if (bPrt) fhOut.println(aline);
    			} // finish seq line
    		}
			if (len>0) {
				totalLen += len;
				printTrace(bPrt, isChr, isScaf, isMT, len, idcol1, prtName);
			}
			System.err.print("                                            \r");
			fhIn.close(); 	
		}
		catch (Exception e) {die(e, "rwFasta: " + fastaFile);}
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
				if (words[i].equals("chromosome")) {
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
					if (name.endsWith("X") || name.endsWith("Y") || 
						name.endsWith("I") || name.endsWith("V")) name = "Chr" + name;
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
	// Write to file, or Verbose
	private void printTrace(boolean bPrt, boolean isChr, boolean isScaf, boolean isMtPt,
			int len, String id, String prtname) {
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
			if (len<traceScafMaxLen) cntScafSmall++;
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
	/***************************************************************************
	 * XXX GFF file example:
	 * NC_016131.2     Gnomon  gene    18481   21742   .       +       .       ID=gene3;Dbxref=GeneID:100827252;Name=LOC100827252;gbkey=Gene;gene=LOC100827252;gene_biotype=protein_coding
	   NC_016131.2     Gnomon  mRNA    18481   21742   .       +       .       ID=rna2;Parent=gene3;Dbxref=GeneID:100827252,Genbank:XM_003563157.3;Name=XM_003563157.3;gbkey=mRNA;gene=LOC100827252;model_evidence=Supporting evidence includes similarity to: 3 Proteins%2C and 78%25 coverage of the annotated genomic feature by RNAseq alignments;product=angio-associated migratory cell protein-like;transcript_id=XM_003563157.3
	   NC_016131.2     Gnomon  exon    18481   18628   .       +       .       ID=id15;Parent=rna2;Dbxref=GeneID:100827252,Genbank:XM_003563157.3;gbkey=mRNA;gene=LOC100827252;product=angio-associated migratory cell protein-like;transcript_id=XM_003563157.3
	 */
	
	 private void rwAnno() {
		try {	
			if (inGffFile==null) return; 
			prt("");
			prt("Processing " + inGffFile);
			
			 int cntReadGene=0, cntReadMRNA=0, cntReadExon=0;
			 BufferedReader fhIn = openGZIP(inGffFile); if (!bSuccess) return;
			 PrintWriter fhOutGff = new PrintWriter(new FileOutputStream(annoDir + outGffFile, false)); 
			 fhOutGff.println(header);
			 
			 String line="";
			 int skipLine=0;
			 
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
				String type =  tok[2].trim();  		// gene, mRNA, exon...
				if (!allTypeCnt.containsKey(type))  allTypeCnt.put(type,1);
				else 								allTypeCnt.put(type, allTypeCnt.get(type)+1);
				
				if (type.equals(geneType)) {
					rwAnnoOut(fhOutGff);			// Write last record
					rwAnnoGene(tok, line);			// Start new
					cntReadGene++; cntThisGeneMRNA++;
					if (cntReadGene%PRT==0) System.err.print("   Process " + cntReadGene + " genes...\r");
				}
				else if (type.equals(mrnaType)) {
					rwAnnoMRNA(tok, line);
					cntReadMRNA++;
				}
				else if (type.equals(cdsType)) {
					rwAnnoCDS(tok, line);
				}
				else if (type.equals(exonType)) {
					rwAnnoExon(tok, line);
					cntReadExon++;
				}	
			 }
			 fhIn.close(); fhOutGff.close();
			 prt(String.format("   Use Gene %,d from %,d                ", cntUseGene, cntReadGene));
			 prt(String.format("   Use mRNA %,d from %,d                ", cntUseMRNA, cntReadMRNA));
			 prt(String.format("   Use Exon %,d from %,d                ", cntUseExon, cntReadExon));
			 prt("Finish writing " + annoDir + outGffFile + "                     ");
		 }
		 catch (Exception e) {die(e, "rwAnnoGene");}
	}
	
	private void rwAnnoGene(String [] tok, String line) { 
		try {	
			// counts for gene
			String [] typeAttrs = tok[8].trim().split(";"); 
			String biotype = getVal(biotypeAttrKey, typeAttrs);
			if (!allGeneBiotypeCnt.containsKey(biotype)) allGeneBiotypeCnt.put(biotype,1);
			else 										 allGeneBiotypeCnt.put(biotype, allGeneBiotypeCnt.get(biotype)+1);
			
			String src  =  tok[1].trim();  // RefSeq, Gnomon..
			if (src.contains("%2C")) 		src = src.replace("%2C", ","); 
			
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
			////////////
			geneID =  getVal(idAttrKey, typeAttrs);
			geneLine = line;
			cntUseGene++;
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
			
			////////////////////////
			mrnaID = getVal(idAttrKey, attrs);
			
			String mid = mrnaID.replace(RMRNA,"");
			gmrnaAt = MRNAID + mid;
			gproductAt =  DESC + getVal(productAttrKey, attrs);
			
			String idStr =   idAttrKey + "=" + mrnaID.replace(RMRNA,"");	
			String parStr =  parentAttrKey + "=" + geneID.replace(RMGENE,"");
			String newAttrs = idStr + ";" + parStr + ";" + gproductAt;
			mrnaLine = gchr + "\t" +  tok[1] + "\t" + tok[2] + "\t" + tok[3] + "\t" + 
					tok[4] + "\t" + tok[5] + "\t" + tok[6] + "\t" + tok[7] + "\t" + newAttrs;
		
			cntUseMRNA++;
		 }
		 catch (Exception e) {die(e, "rwAnnoMRNA");}
	 }
	 private void rwAnnoCDS(String [] tok, String line) { 
		 try {
			if (!ATTRPROT || mrnaID.equals("")) return;
			
			String [] attrs = tok[8].split(";"); 
			String pid = getVal(parentAttrKey, attrs);
				
			if (mrnaID.equals(pid)) {
				gproteinAt = ";" + PROTEINID + getVal(cdsProteinAttrKey, attrs);
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
			
			//////////////
			String idStr =   idAttrKey + "=" + getVal(idAttrKey, attrs);	// create shortened attribute list
			String parStr =  parentAttrKey + "=" + mrnaID.replace(RMRNA,"");
			String geneStr = exonGeneAttrKey + "=" + getVal(exonGeneAttrKey, attrs);	
			String newAttrs = idStr + ";" + parStr + ";" + geneStr;
			String nLine = gchr + "\t" + tok[1] + "\t" + tok[2] + "\t" + tok[3] + "\t" + tok[4]
						+ "\t" + tok[5] + "\t" + tok[6] + "\t" + tok[7] + "\t" + newAttrs;
			exonVec.add(nLine);
			cntUseExon++;
		 }
		 catch (Exception e) {die(e, "rwAnnoExon");} 
	 }
	 private void rwAnnoOut( PrintWriter fhOutGff) {
	 try {
		if (geneID.trim().equals("")) return;
		if (mrnaID.trim().equals("")) return;
		
		String [] tok = geneLine.split("\\t");
		if (tok.length!=9) die("Gene: " + tok.length + " " + geneLine);
		
		if (geneID.startsWith(RMGENE)) geneID = geneID.replace(RMGENE,""); 
		String idAt = idAttrKey + "=" + geneID + ";";
		
		String [] attrs = tok[8].split(";");
		String val = getVal(nameAttrKey, attrs);
		String nameAt = (!geneID.contains(val)) ? (nameAttrKey + "=" + val + ";") : ""; // CHANGE here to alter what attributes are saved
		
		String desc = getVal(descAttrKey, attrs).trim();
		if (!desc.equals("")) gproductAt = DESC + desc;
		
		gmrnaAt += " (" + cntThisGeneMRNA + ");";
		
		String allAttrs = idAt + nameAt + gmrnaAt + gproductAt + gproteinAt;
		
		String line = gchr + "\t" + tok[1] + "\t" + tok[2] + "\t" + tok[3] + "\t" + tok[4]
		+ "\t" + tok[5] + "\t" + tok[6] + "\t" + tok[7] + "\t" + allAttrs;
		
		if (line.contains("%")) {
			for (String hex : hexMap.keySet()) {
				if (line.contains(hex)) line = line.replace(hex, hexMap.get(hex));
			}
		}
		fhOutGff.println("###\n" + line);
		
		fhOutGff.println(mrnaLine); 
		
		for (String eline : exonVec) fhOutGff.println(eline);
		
		cntThisGeneMRNA=0;
		geneID=geneLine=gchr=gproteinAt=gmrnaAt=gproductAt=mrnaID=mrnaLine="";
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
	
	 /***********************************************************************/
	 private void printSummary() {
		prt("                                                      ");
		if (VERBOSE) {
			prt(">>Sequence                      ");
			if (cntChr>0) {
				prt(String.format("  %,6d  Output %-,6d %-11s  %,15d", cntChr, nChr, "Chromosomes", chrLen));
			}
			if (cntMtPt>0) {
				prt(String.format("  %,6d  Output %-,6d %-11s  %,15d", cntMtPt, nMt, "Mt/Pt", mtptLen));
			}
			if (cntScaf>0) {
				prt(String.format("  %,6d  Output %-,6d %-11s  %,15d (%,d < %,dbp)", 
						cntScaf,nScaf, "Scaffolds", scafLen, cntScafSmall, traceScafMaxLen));
			}
			if (cntUnk>0) {
				prt(String.format("  %,6d  Output %-,6d %-11s  %,15d", cntUnk, nUnk, "Unknown", unkLen));
			}
			
			if (inGffFile==null) return;
			
			////////////////// anno ////////////////
			prt("                                                       ");
		
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
			prt(">>All gene_biotype= (col 8)                    ");
			for (String key : allGeneBiotypeCnt.keySet()) {
				String x = (key.equals(biotypeAttr)) ? plus : "";
				prt(String.format("   %-22s %,8d %s", key, allGeneBiotypeCnt.get(key), x));
			}
		}
		prt(String.format(">>Chromosome gene count %,d                ", cntChrGeneAll));
		for (String prt : chr2id.keySet()) {
			String id = chr2id.get(prt);
			prt(String.format("   %-10s %-20s %,8d", prt, id, cntChrGene.get(id)));
		}
		if (INCLUDESCAF) {
			int cntOne=0;
			prt(String.format(">>Scaffold gene count %,d (list scaffolds with #genes>%d) ", cntScafGeneAll, traceNumGenes));
			for (String id : cntScafGene.keySet()) {
				if (cntScafGene.get(id)>traceNumGenes)
					prt(String.format("   %-10s %-20s %,8d", id2scaf.get(id), id, cntScafGene.get(id)));
				else cntOne++;
			}
			prt(String.format("   Scaffolds with <=%d gene (not listed) %,8d", traceNumGenes, cntOne));
			prt(String.format("   Genes not included %,8d",  cntGeneNotOnSeq));
		}
		else 	prt(String.format("   %s %,8d", "Genes not on Chromosome", cntGeneNotOnSeq));
	 }
	
	 /**************************************************************************
	  * Find .fna and .gff in top project directory or in projDir/ncbi_dataset/data/<dir>
	  */
	 private boolean checkInitFiles() {
		try {
			if (projDir==null || projDir.trim().equals("")) {
				die("No project directory");
				return false;
			}
			File[] files;
			String dsDirName=null;
			
			File dir = new File(projDir);
			if (!dir.isDirectory()) 
				return die(projDir + " is not a directory.");
			
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
					return die("ncbi_dataset directory is incorrect " + dsDirName);
				
				files = dir.listFiles();
				for (File f : files) {
					if (f.isDirectory()) {
						subDir = f.getName();
						break;
					}
				}
				if (subDir==null) return die(dsDirName + " missing sub-directory");
				
				dsDirName += "/" + subDir;
			}
			else {
				dsDirName = projDir;
			}
			
			// find .fna and .gff files in dsDirName
			File dsDir = new File(dsDirName);
			if (!dsDir.isDirectory()) 
				return die(dsDir + " is not a directory.");
			
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
	
			if (inFaFile == null)  return die("Project directory " + projDir + ": no file ending with " + faSuffix +  " or "+ faSuffix + ".gz (i.e. Ensembl files)");
			if (inGffFile == null)        prt("Project directory " + projDir + ": no file ending with " + gffSuffix + " or " + gffSuffix + ".gz");
			
			// Create sequence and annotation directories
			if (!projDir.endsWith("/")) projDir += "/";
			seqDir = projDir + seqDir;
			checkDir(true, seqDir);  if (!bSuccess) return false;
			
			annoDir = projDir + annoDir;
			checkDir(false, annoDir); if (!bSuccess) return false;
			
			return true;
		}
		catch (Exception e) { return die(e, "Checking " + projDir); }
	}
	/*****************************************************************/
	 private void createLog(String [] args) {
		if (args.length==0) return;
		logFileName = args[0] + logFileName;
		prt("Log file to  " + logFileName);	
		
		try {
			logFile = new PrintWriter(new FileOutputStream(logFileName, false)); 
		}
		catch (Exception e) {die("Cannot open " + logFileName); logFile=null;}
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
				return die("*** Failed to create directory '" + nDir.getAbsolutePath() + "'.");
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
	/*******************************************************/
	 private void checkArgs(String [] args) {
		if (args.length==0 || args[0].equals("-h") || args[0].equals("-help") || args[0].equals("help")) { // for stand alone
			prt("\nConvertNCBI <project directory> [options] 				");
			prt("   the project directory must contain the FASTA file and the GFF is optional: " +
					        "\n       FASTA file ending with .fna or .fna.gz" +
					        "\n       GFF   file ending with .gff or .gff.gz" +  
					        "\n       Alternatively, the directory can contain the ncbi_dataset directory." +
					        "\nOptions:" +
					        "\n-m  assuming a soft-masked genome file, convert it to hard-masked." +
					        "\n-s  include any sequence with NT_ or NW_ prefix'." +
					        "\n-t  include Mt and Pt chromosomes." +
					        "\n-p  include the protein name (1st CDS) in the attribute field." +
					        "\n-v  write extra information." +
							"\n\nSee https://csoderlund.github.io/SyMAP/input for details.");
			System.exit(0);
		}
		
		if (args.length>1) {
			for (int i=1; i< args.length; i++)
				if (args[i].equals("-s")) {
					INCLUDESCAF=true;
					chrPrefix = chrPrefix2;
				}
				else if (args[i].equals("-t")) INCLUDEMtPt=true;
				else if (args[i].equals("-v")) VERBOSE=true;
				else if (args[i].equals("-m")) MASKED=true;
				else if (args[i].equals("-p")) ATTRPROT=true;
		}
		prt("Parameters:");
		prt("   Project directory: " + projDir);
		
		if (gapMinLen!=defGapLen) prt("   Gap minimum size: " + gapMinLen); // set in main
		if (prefixOnly!=null)     prt("   Prefix Only: " + prefixOnly); // set in main
		
		if (INCLUDESCAF) {
			prt("   Include scaffold sequences ('NT_' or 'NW_' prefix)");
			prt("      Uses prefixes Chr '" + chrPrefix2 + "' and Scaffold '" + scafPrefix + "'");
		}
		if (INCLUDEMtPt)  	prt("   Include Mt and Pt chromosomes");
		if (MASKED)  		prt("   Hard mask sequence");
		if (ATTRPROT)  		prt("   Include protein-id in attributes");
		if (VERBOSE) 		{traceNum=20; prt("   Verbose");}
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
