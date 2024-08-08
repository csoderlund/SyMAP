package toSymap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.HashSet;
import java.util.zip.GZIPInputStream;

/*************************************************************
 * ConvertNCBI: NCBI genome files formatted for SyMAP
 * See https://csoderlund.github.io/SyMAP/toSymap/ncbi.html
 * 
 * Called from xToSymap, but can be used stand-alone.
 * 
 * Written by CAS 16/Jan/18; made part of toSymap and other changes for CAS557
 * This assumes input of the project directory, which has either
 * 1. The ncbi_dataset.zip unzipped, .e.g 
 *    /data/seq/Arab/ncbi_dataset/data/GCF_000001735.4
 * or the .fna and .gff files directly under the project directory
 *        
 * Note: I am no expert on NCBI GFF format, so I cannot guarantee this is 100% correct mapping for all input.
 * Your sequences/annotations may have other variations.
 * Hence, you may want to edit this to customize it for your needs -- its simply written so easy to modify.
 * See https://csoderlund.github.io/SyMAP/toSymap/ncbi.html#edit
 * 
 * From NCBI book and confirm by https://ftp.ncbi.nlm.nih.gov/refseq/release/release-notes/RefSeq-release225.txt
 * AC_	Genomic	Complete genomic molecule, usually alternate assembly (or linkage)
 * NC_	Genomic	Complete genomic molecule, usually reference assembly (or linkage) - check this one
 * NG_	Genomic	Incomplete genomic region
 * NT_	Genomic	Contig or scaffold, clone-based or WGS			- check this one
 * NW_	Genomic	Contig or scaffold, primarily WGS  				- check this one
 * NZ_	Genomic	Complete genomes and unfinished WGS data
 * 
 * The 'product' description must be in the mRNA product attribute!!
   Annotation description: 	ID=gene-;Name=(if unique) and ID=rna-;product= and Parent=
   Types used:   gene, mRNA, exon
   Attributes:   ID, gene_biotype, parent, product, gene
   Only process: gene-biotype='protein_coding'
   For gene product, first mRNA product description and appends remaining mRNA variants
		e.g. product=phosphate transporter PHO1-1-like%2C transcript variant X1,X2
 */

public class ConvertNCBI {
	static private boolean isToSymap=true;
	static private int defGapLen=30000;
	
	private String logFileName = "/xConvertNCBI.log";
	private PrintWriter logFile=null;
	
	// flags set by user
	//private boolean LINKAGE = false; CAS557 remove - RefSeq does not have Accession prefix for it
	private boolean INCLUDESCAF = false;
	private boolean INCLUDEMtPt = false; 
	private boolean MASKED = false;
	private boolean ATTRPROT = false;
	private boolean ATTRPROTALL = false;
	private boolean VERBOSE = false;
	private boolean ALLEXON = false; // not documented
	
	private int gapMinLen;          // Set from main; Print to gap.gff if #N's is greater than this
	private String prefixOnly=null; // Set from main; e.g. NC
	
	// Changed for specific input; if unknown and prefixOnly, no suffix
	private String chrPrefix="Chr";	// changed to "c" if scaffolds too
	private String scafPrefix="s";  // single letter is used because the "Chr" is usually removed to save space
	private String unkPrefix="Unk";
	
	private int traceChrNum=20;
	private int traceNum=5;	// if verbose, change to 20
	private final int scafMaxLen=10000;	// All scaffold are printed to fasta file, but only summarized if >scafMax
	
	// search in .fna files CAS557 using prefixes instead
	//private final String chrKey		= "chromosome"; 
	//private final String scafKey	= "scaffold";
		
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
	private final String exonGeneAttrKey = "gene";
	private final String star = "*";
	
	private HashMap <String, String> hexMap = new HashMap <String, String> ();
	
	// input 
	private String projDir = null;
	private String inFaFile = null;
	private String inGffFile = null;
	
	// downloaded via NCBI Dataset link
	private String ncbi_dataset="ncbi_dataset"; 
	private String ncbi_data=	"/data"; 
	private String subDir=null;
	 
	//output - the projDir gets appended to the front
	private String seqDir =   	"sequence";
	private String annoDir =  	"annotation";
	private String outFaFile =  "/genomic.fna";
	private String outGffFile = "/anno.gff";
	private String outGapFile = "/gap.gff";
	
	// Other global variables
	private TreeMap <String, String> chr2id = new TreeMap <String, String> ();
	private TreeMap <String, String> id2chr = new TreeMap <String, String> ();
	private TreeMap <String, String> id2scaf = new TreeMap <String, String> ();
	private TreeMap <String, Integer> cntChrGene = 	new TreeMap <String, Integer> ();
	private TreeMap <String, Integer> cntScafGene = new TreeMap <String, Integer> ();
	
	private HashMap <String, Gene> geneMap = new HashMap <String, Gene> ();	
	private HashMap <String, Gene> mrnaMap = new HashMap <String, Gene> ();
	private int cntExon=0;
	 
	// Summary
	private TreeMap <String, Integer> allTypeCnt = new TreeMap <String, Integer> ();
	private TreeMap <String, Integer> allGeneBiotypeCnt = new TreeMap <String, Integer> ();
	private TreeMap <String, Integer> allGeneSrcCnt = new TreeMap <String, Integer> ();
	private int cntDupProduct=0, cntXProduct=0, cntUniqueProduct=0, cntMultUniqueProduct=0;
	private int cntChrGeneAll=0, cntScafGeneAll=0, cntGeneNotOnSeq=0, cntScafSmall=0;
	
	private int nChr=0, nScaf=0, nMt=0, nUnk=0, nGap=0; // for numbering output seqid and totals 
	private int cntChr=0, cntScaf=0, cntMtPt=0, cntUnk=0, cntOutSeq=0, cntNoOutSeq=0; 
	private long chrLen=0, scafLen=0, mtptLen=0, unkLen=0;
	  
	private int maxProt=0;
	private String maxGene="";
	
	private PrintWriter fhOut, ghOut;
	private int cntMask=0;
	private TreeMap <Character, Integer> cntBase = new TreeMap <Character, Integer> ();
	
	private boolean bSuccess=true;
	private final int PRT = 10000;
	
	public static void main(String[] args) { 
		isToSymap=false;
		new ConvertNCBI(args, defGapLen, null);
	}
	protected ConvertNCBI(String [] args, int gapLen, String prefix) { // CAS557 make accessible from ConvertFrame
		if (args.length==0) { // command line
			checkArgs(args);
			return;
		}
		projDir = args[0];
		if (projDir==null || projDir.isEmpty()) {
			System.err.println("No project directory");
			return;
		}
		prt("\n------ ConvertNCBI " + projDir + " ------");
		gapMinLen = gapLen;
		prefixOnly = prefix;
		
		hexMap.put("%09", " "); // tab
		hexMap.put("%0A", " "); // newline
		hexMap.put("%25", "%"); // %
		hexMap.put("%2C", ","); // ,
		hexMap.put("%3B", ";"); // ;
		hexMap.put("%3D", "="); // =
		hexMap.put("%26", "="); // &
		
		createLog(args); if (!bSuccess) return;
		checkArgs(args); if (!bSuccess) return;
		checkInitFiles();if (!bSuccess) return;
		
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
			if (cntOutSeq>30)  prt( "Suggestion: There are " + cntOutSeq + " sequence. "
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
			fhOut.println("### Written by SyMAP ConvertNCBI");
			ghOut = new PrintWriter(new FileOutputStream(annoDir + outGapFile, false));
			ghOut.println("### Written by SyMAP ConvertNCBI");
			
			char [] base = {'A', 'C', 'G', 'T', 'N', 'a', 'c', 'g', 't', 'n'};
			for (char b : base) cntBase.put(b, 0);
			
			// Process file
			rwFasta(inFaFile); if (!bSuccess) return;
		
			fhOut.close(); ghOut.close();
			prt(String.format("Sequences not output: %,d", cntNoOutSeq));
			prt("Finish writing " + seqDir + outFaFile + "                     ");
			
			prt("");
			prt( String.format("A %,11d  a %,11d", cntBase.get('A'), cntBase.get('a')) );
			prt( String.format("T %,11d  t %,11d", cntBase.get('T'), cntBase.get('t')) );
			prt( String.format("C %,11d  c %,11d", cntBase.get('C'), cntBase.get('c')) );
			prt( String.format("G %,11d  g %,11d", cntBase.get('G'), cntBase.get('g')) );
			prt( String.format("N %,11d  n %,11d", cntBase.get('N'), cntBase.get('n') ));
			String other=""; // CAS513
			for (char b : cntBase.keySet()) {
				boolean found = false;
				for (char x : base) if (x==b) {found=true; break;}
				if (!found) other += String.format("%c %,d  ", b, cntBase.get(b));
			}
			if (other!="") prt(other);
			cntBase.clear();
			
			if (MASKED) prt(String.format("Hard masked: %,d lower case changed to N", cntMask));
			
			prt("");
			prt(String.format("Gaps >= %d: %d", gapMinLen, nGap));
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
			
			String idcol1="", prtName="";
			boolean bPrt=false, isChr=false, isScaf=false, isMT=false;
			boolean bMt=false, bPt=false;
			int baseLoc=0, gapStart=0, gapCnt=1;
			
			while ((line = fhIn.readLine()) != null) {
				lineN++;
				if (line.startsWith("!!") || line.startsWith("#") || line.isEmpty()) continue;
				
    			if (line.startsWith(">")) { // id line
    				if (len>0) {
    					printTrace(bPrt, isChr, isScaf, isMT, len, idcol1, prtName);
    					len=0;
    				}
    				String line1 = line.substring(1);
    				String [] tok = line1.split("\\s+");
    				if (tok.length==0) {
    					die("Header line is blank: line #" + lineN);
    					return;
    				}
    				idcol1 = tok[0];
    				
    				isChr=isScaf=isMT=false; // CAS557 start check prefix (was only checking for contains)
    				
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
    				
    				if (prefixOnly!=null) { // CAS557 new parameter
    					if (!idcol1.startsWith(prefixOnly)) {
    						cntNoOutSeq++;
    						continue;
    					}
    				}
    				
    				gapStart=1; gapCnt=0; baseLoc=0;
    				bPrt=false; 
    				if (isChr && !isMT) { 
    					nChr++; 
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
	    				
	    				bPrt=true;	
    				}
    				else if (isScaf && INCLUDESCAF) {
    					nScaf++;  
	    				prtName = scafPrefix + padNum(nScaf+"");
	    				id2scaf.put(idcol1, prtName);
	    				cntScafGene.put(idcol1, 0);
	    				
    					bPrt=true;
    				}
    				else {
    					if (isScaf) {prtName=scafPrefix;}
    					else 		{prtName=unkPrefix;}
    					
    					if (prefixOnly!=null) {// use chr, don't know what it is
    						nUnk++;
    						prtName = unkPrefix + padNum(nUnk+"");
    						id2chr.put(idcol1, prtName); 
    	    				cntChrGene.put(idcol1, 0);
    	    				
    	    				bPrt=true;
    					}
    				}
    				if (bPrt) {
    					fhOut.println(">" + prtName + " " + idcol1);
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
	    					
	    					if (cntBase.containsKey(b)) cntBase.put(b, cntBase.get(b)+1); // counts
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
			if (len>0) printTrace(bPrt, isChr, isScaf, isMT, len, idcol1, prtName);
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
			String [] words = line.split("\\s+"); // CAS557 this gets them all I think...
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
		String x = (bPrt) ? "" : "*";
		if (isChr && !isMtPt) {
			chrLen+=len;
			if (bPrt || VERBOSE) {
				if (cntChr<traceChrNum) prt(String.format("%-7s %-15s %,11d %s", prtname, id, len,x));
				else if (cntChr==traceChrNum) prt("Suppressing further chromosome outputs");
			}
		}
		else if (isMtPt) {
			mtptLen+=len;
			if (bPrt || VERBOSE) {
				if (cntMtPt<traceNum) prt(String.format("%-7s %-15s %,11d %s", prtname, id, len, x));
				else if (cntMtPt==traceNum+1) prt("Suppressing further Mt/Pt outputs");
			}
		}
		else if (isScaf) {
			scafLen+=len;
			if (len<scafMaxLen) cntScafSmall++;
			if (bPrt || VERBOSE) {
				if (cntScaf<=traceNum) 	prt(String.format("%-7s %-15s %,11d  %s", prtname, id, len, x));
				else if (cntScaf==traceNum+1) prt("Suppressing further scaffold outputs");
			}
		}
		else {
			unkLen += len;
			if (bPrt || VERBOSE) {
				if (cntUnk<traceNum) prt(String.format("%-7s %-15s %,11d  %s", prtname, id, len, x));
				else if (cntUnk==traceNum+1) 	  prt("Suppressing further unknown outputs");
			} 
		}
	}
	/***************************************************************************
	 * GFF file example:
	 * NC_016131.2     Gnomon  gene    18481   21742   .       +       .       ID=gene3;Dbxref=GeneID:100827252;Name=LOC100827252;gbkey=Gene;gene=LOC100827252;gene_biotype=protein_coding
	   NC_016131.2     Gnomon  mRNA    18481   21742   .       +       .       ID=rna2;Parent=gene3;Dbxref=GeneID:100827252,Genbank:XM_003563157.3;Name=XM_003563157.3;gbkey=mRNA;gene=LOC100827252;model_evidence=Supporting evidence includes similarity to: 3 Proteins%2C and 78%25 coverage of the annotated genomic feature by RNAseq alignments;product=angio-associated migratory cell protein-like;transcript_id=XM_003563157.3
	   NC_016131.2     Gnomon  exon    18481   18628   .       +       .       ID=id15;Parent=rna2;Dbxref=GeneID:100827252,Genbank:XM_003563157.3;gbkey=mRNA;gene=LOC100827252;product=angio-associated migratory cell protein-like;transcript_id=XM_003563157.3
	 */
	 private void rwAnno() { 
		 try {
			if (inGffFile==null) return; // CAS513 
			prt("");
			prt("Processing " + inGffFile);
			rwAnnoGene();	// put genes in geneMap
			rwAnnoMRNA();   // add product to gene product
			rwAnnoExon();	// write gene followed by its exons
		 }
		 catch (Exception e ) {} 
	 }
	/** 
	 * Reads genes into geneMap, computes summary stats, and verifies input
	 * The entries are saved so that the mRNA product= can be appended to the gene's
	 ***/
	private void rwAnnoGene() { 
		try {		
			 BufferedReader fhIn = openGZIP(inGffFile); if (!bSuccess) return;
			 
			 String line="";
			 int skipLine=0, cntGene=0;
			 
			 while ((line = fhIn.readLine()) != null) {
				line = line.trim();
				if (line.startsWith("#") || line.length()==0) continue;
				
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
				
				if (!type.equals(geneType)) continue; 
				
				cntGene++;
				if (cntGene%PRT==0) System.err.print("   Process " + cntGene + " genes...\r");
				
				// counts for gene
				String [] typeAttrs = tok[8].trim().split(";"); 
				String biotype = getVal(biotypeAttrKey, typeAttrs);
				if (!allGeneBiotypeCnt.containsKey(biotype)) allGeneBiotypeCnt.put(biotype,1);
				else 										 allGeneBiotypeCnt.put(biotype, allGeneBiotypeCnt.get(biotype)+1);
				
				String src  =  tok[1].trim();  // RefSeq, Gnomon..
				if (src.contains("%2C")) 		src = src.replace("%2C", ","); 
				
				if (!allGeneSrcCnt.containsKey(src)) 	allGeneSrcCnt.put(src,1);
				else 									allGeneSrcCnt.put(src, allGeneSrcCnt.get(src)+1);
				
				if (!biotype.equals(biotypeAttr)) continue; // protein-coding
				
				String prtName="";
				String idcol1 =   tok[0];  // chromosome, scaffold, linkage group...
				if (id2chr.containsKey(idcol1))  { 
					cntChrGeneAll++;
					prtName = id2chr.get(idcol1);
					cntChrGene.put(idcol1, cntChrGene.get(idcol1)+1);
				}
				else if (id2scaf.containsKey(idcol1)) {
					cntScafGeneAll++; 
					prtName = id2scaf.get(idcol1);
					cntScafGene.put(idcol1, cntScafGene.get(idcol1)+1);
				}
				else {
					cntGeneNotOnSeq++; 
					continue;
				}
				String id =  getVal(idAttrKey, typeAttrs);
				Gene g = new Gene(prtName, line);
				geneMap.put(id, g);
			 }
			 fhIn.close();
			 prt(String.format("   Use Genes %,d from %,d                ", geneMap.size(), cntGene));
		 }
		 catch (Exception e) {die(e, "rwAnnoGene");}
	 }
	 /** add product to gene attributes **/
	 private void rwAnnoMRNA() { 
		 try {
			 BufferedReader fhIn = openGZIP(inGffFile); if (!bSuccess) return;
			 
			 String line="", lastGene="";
			 int cntReadRNA=0;
			 HashMap <String, Gene> gMrnaMap = new HashMap <String, Gene> (); // mRNA for current gene; to detect all proteins
			 boolean bPROT = (ATTRPROT || ATTRPROTALL);
			 
			 while ((line = fhIn.readLine()) != null) {
				line = line.trim();
				if (line.startsWith("#") || line.length()==0) continue;
				
				String [] tok = line.split("\\t");
				if (tok.length!=9) continue;
				
				String type =  tok[2];  // gene, mRNA, exon...
				if (! (type.equals(mrnaType) || (bPROT && type.equals(cdsType))) ) continue;
				
				String [] typeAttrs = tok[8].split(";"); 
				String pid =   getVal(parentAttrKey, typeAttrs);
				
				if (type.contentEquals(cdsType)) { 
					if (gMrnaMap.containsKey(pid)) {
						String idKey =  getVal(idAttrKey, typeAttrs);
						Gene g = gMrnaMap.get(pid);
						g.mergeProtein(idKey);
					}
					continue;
				}
				
				Gene gObj = geneMap.get(pid);
				if (!geneMap.containsKey(pid)) continue;	// parent gene was excluded
				
				if (!lastGene.contentEquals(pid)) {
					gMrnaMap.clear();
					lastGene=pid;
				}
				cntReadRNA++;
				if (cntReadRNA%PRT==0) System.err.print("   Process " + cntReadRNA + " mRNA...\r");
				
				String productKey = getKeyVal(productAttrKey, typeAttrs);
				
				String idKey =  getKeyVal(idAttrKey, typeAttrs);
				boolean bIs1st = gObj.bNoProduct();
				if (bIs1st)	     gObj.setProduct(idKey, productKey);
				else		     gObj.mergeProduct(idKey, productKey);
	
				String mID =  getVal(idAttrKey, typeAttrs);
				if (bIs1st || ALLEXON) mrnaMap.put(mID, gObj);	
				
				if (ATTRPROTALL || (ATTRPROT && bIs1st)) gMrnaMap.put(mID, gObj);
			 }
			 fhIn.close();
			 prt(String.format("   Use mRNAs %,d from %,d           ", mrnaMap.size(), cntReadRNA));	
		 }
		 catch (Exception e) {die(e, "rwAnnoMRNA");}
	 }
	 /** Write genes and exons to file**/
	 private void rwAnnoExon() {
		 try {
			 PrintWriter fhOutGff = new PrintWriter(new FileOutputStream(annoDir + outGffFile, false)); 
			 fhOutGff.println("### Written by SyMAP ConvertNCBI");
			 
			 BufferedReader fhIn = openGZIP(inGffFile); if (!bSuccess) return;
			 
			 String line="";
			 String curGeneID=null;
			 int cntReadExon=0;
			 
			 while ((line = fhIn.readLine()) != null) {
				line = line.trim();
				if (line.startsWith("#") || line.length()==0) continue;
				
				String [] tok = line.split("\\t");
				if (tok.length!=9) continue;
				
				String type =  tok[2];  // gene, mRNA, exon...
				if (!type.equals(geneType) && !type.equals(exonType) && !type.equals(mrnaType)) continue;
				
				if (type.equals(exonType)) { // Count all exons before start filtering
					cntReadExon++;
					if (cntReadExon%PRT==0)
						System.err.print("   Process " + cntReadExon + " exons...\r");
				}
				
				String [] typeAttrs = tok[8].split(";"); 
				
				// gene
				if (type.equals(geneType)) {		// if Gene, if valid, set curGeneID and continue
					String id =  getVal(idAttrKey, typeAttrs);
					if (geneMap.containsKey(id)) {
						curGeneID =  id;
						Gene g = geneMap.get(curGeneID);
						String nLine = g.createLine(curGeneID);
						fhOutGff.println(nLine); 
					}
					else curGeneID=null;
					continue;
				}
				if (curGeneID==null) continue; 		// skip all until valid gene
				
				if (type.equals(mrnaType)) {		// this will be ignored by SyMAP, but for correct file..
					String mID =  getVal(idAttrKey, typeAttrs);
						
					if (mrnaMap.containsKey(mID)) {
						String chrName = mrnaMap.get(mID).getChr();
						String idStr =   idAttrKey + "=" + getVal(idAttrKey, typeAttrs);	// create shortened attribute list
						String parStr =  parentAttrKey + "=" + getVal(parentAttrKey, typeAttrs);
						String prodStr = productAttrKey + "=" + getVal(productAttrKey, typeAttrs);
						String newAttrs = idStr + ";" + parStr + ";" + prodStr;
						String nline = chrName + "\t" +  tok[1] + "\t" + tok[2] + "\t" + tok[3] + "\t" + 
								tok[4] + "\t" + tok[5] + "\t" + tok[6] + "\t" + tok[7] + "\t" + newAttrs;
						fhOutGff.println(nline); 
					}
					continue;
				}
				
				// exon
				String parID = getVal(parentAttrKey, typeAttrs);
				if (!mrnaMap.containsKey(parID)) continue;	// only write exons if mrna is in map
				
				String chrName = mrnaMap.get(parID).getChr(); // the exons chr is same as gene
				
				String idStr =   idAttrKey + "=" + getVal(idAttrKey, typeAttrs);	// create shortened attribute list
				String parStr =  parentAttrKey + "=" + parID;
				String geneStr = exonGeneAttrKey + "=" + getVal(exonGeneAttrKey, typeAttrs);
				String newAttrs = idStr + ";" + parStr + ";" + geneStr;
				String nLine = chrName + "\t" + tok[1] + "\t" + tok[2] + "\t" + tok[3] + "\t" + tok[4]
							+ "\t" + tok[5] + "\t" + tok[6] + "\t" + tok[7] + "\t" + newAttrs;
				
				fhOutGff.println(nLine);
				cntExon++;	
			 }
			 fhIn.close(); fhOutGff.close();
			 prt(String.format("   Use Exons %,d from %,d            ", cntExon, cntReadExon));
			 prt("Finish writing " + annoDir + outGffFile + "                          ");
		 }
		 catch (Exception e) {die(e, "rwAnnoExon");} 
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
						cntScaf,nScaf, "Scaffolds", scafLen, cntScafSmall, scafMaxLen));
			}
			if (cntUnk>0) {
				prt(String.format("  %,6d  Output %-,6d %-11s  %,15d", cntUnk, nUnk, "Unknown", unkLen));
			}
			
			if (inGffFile==null) return;
			
			prt("                                         ");
		
			prt(">>All Types  (col 3)  (" + star + " are processed keywords)");
			for (String key : allTypeCnt.keySet()) {
				boolean bKey = (ATTRPROT || ATTRPROTALL) && key.equals(cdsType);
				String x = (key.equals(geneType) || key.equals(mrnaType) || key.equals(exonType)) || bKey ? star : "";
				prt(String.format("   %-22s %,8d %s", key, allTypeCnt.get(key), x));
			}
		
			prt(">>All Gene Source (col 2)");
			for (String key :allGeneSrcCnt.keySet()) {
				prt(String.format("   %-22s %,8d", key, allGeneSrcCnt.get(key)));
			}
			prt(">>All gene_biotype= (col 8)                    ");
			for (String key : allGeneBiotypeCnt.keySet()) {
				String x = (key.equals(biotypeAttr)) ? "*" : "";
				prt(String.format("   %-22s %,8d %s", key, allGeneBiotypeCnt.get(key), x));
			}
	
			prt(">>Gene product= (col 8)");
			prt(String.format("   %-22s %,8d %s", "Unique", cntUniqueProduct, star));
			if (cntMultUniqueProduct>0) prt(String.format("   %-22s %,8d", "Multiple Unique", cntMultUniqueProduct));
			if (cntDupProduct>0)        prt(String.format("   %-22s %,8d", "Substring (ignored)", cntDupProduct));
			prt(String.format("   %-22s %,8d", "Variants", cntXProduct));
			
			prt(">>Written to file ");
			prt(String.format("   %-22s %,8d", "Gene", geneMap.size()));
			prt(String.format("   %-22s %,8d", "mRNA", mrnaMap.size()));
			prt(String.format("   %-22s %,8d", "Exon", cntExon));
		}
		prt(String.format(">>Chromosome gene count %,d                ", cntChrGeneAll));
		for (String prt : chr2id.keySet()) {
			String id = chr2id.get(prt);
			prt(String.format("   %-10s %-20s %,8d", prt, id, cntChrGene.get(id)));
		}
		if (INCLUDESCAF) {
			prt(String.format(">>Scaffold gene count %,d (list scaffolds with #genes>1) ", cntScafGeneAll));
			for (String id : cntScafGene.keySet()) 
				if (cntScafGene.get(id)>1)
					prt(String.format("   %-10s %-20s %,8d", id2scaf.get(id), id, cntScafGene.get(id)));
		}
		
		if (INCLUDESCAF) prt(String.format("   %s %,8d", "Genes not on Chr/Scaf", cntGeneNotOnSeq));
		else 			 prt(String.format("   %s %,8d", "Genes not on Chromosome", cntGeneNotOnSeq));
		
		if (ATTRPROTALL) prt(String.format("   %s %,8d", "Maximum # of proteins for " + maxGene, maxProt));
	 }
	
	 /**************************************************************************
	  * Find .fna and .gff in top project directory or in projDir/ncbi_dataset/data/<dir>
	  */
	 private boolean checkInitFiles() {
		try {
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
				if (!f.isFile()) continue;
				
		       String fname = f.getName();
		       if (fname.endsWith(".fna.gz") || fname.endsWith(".fna")) 		{
		    	   if (inFaFile!=null) {
		    		   prt("Multiple fasta files - using " + inFaFile);
		    		   break;
		    	   }
		    	   inFaFile = dsDirName + "/" + fname;
		       }
		       else if (fname.endsWith(".gff.gz") || fname.endsWith(".gff"))	{
		    	   if (inGffFile!=null) {
		    		   prt("Multiple fasta files - using " + inFaFile);
		    		   break;
		    	   }
		    	   inGffFile= dsDirName + "/" +fname; 		
		       } 
			}
	
			if (inFaFile==null)   return die(dsDirName + ": no file ending in .fna or .fna.gz (i.e. NCBI files)");
			if (inGffFile==null)  prt(dsDirName + " does not have a file ending with .gff or .gff.gz");
		
			// Create sequence and annotation directories
			seqDir = projDir + "/" + seqDir;
			checkDir(seqDir);  if (!bSuccess) return false;
			
			annoDir = projDir + "/" + annoDir;
			checkDir(annoDir); if (!bSuccess) return false;
			
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
	 private boolean checkDir(String dir) {
		File nDir = new File(dir);
		if (nDir.exists()) {
			return true;
		}
		else {
			if (!nDir.mkdir()) 
				return die("*** Failed to create directory '" + nDir.getAbsolutePath() + "'.");
		}	
		return true;
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
					        "\n-p  include the 1st protein name (1st mRNA) in the attribute field." +
					        "\n-pa  include all protein names in the attribute field." +
					        "\n-v  write header lines of ignored sequences." +
							"\n\nSee https://csoderlund.github.io/SyMAP/convert for details.");
			System.exit(0);
		}
		
		if (args.length>1) {
			for (int i=1; i< args.length; i++)
				if (args[i].equals("-s")) {
					INCLUDESCAF=true;
					chrPrefix = "C";
				}
				else if (args[i].equals("-t")) INCLUDEMtPt=true;
				else if (args[i].equals("-v")) VERBOSE=true;
				else if (args[i].equals("-m")) MASKED=true;
				else if (args[i].equals("-e")) ALLEXON=true;
				else if (args[i].equals("-p")) ATTRPROT=true;
				else if (args[i].equals("-pa")) ATTRPROTALL=true;
		}
		prt("Parameters:");
		prt("   Project directory: " + projDir);
		
		if (gapMinLen!=defGapLen) prt("   Gap minium size: " + gapMinLen); // set in main
		if (prefixOnly!=null)     prt("   Prefix Only: " + prefixOnly); // set in main
		
		if (ALLEXON) prt("   Write all exons for each gene to gff file");
		if (INCLUDESCAF) {
			prt("   Include any sequence with the 'NT_' or 'NW_' prefix");
			prt("      Uses prefixes Chr '" + chrPrefix + "' and Scaffold '" + scafPrefix + "'");
		}
		if (INCLUDEMtPt)  	prt("   Include Mt and Pt chromosomes");
		if (MASKED)  		prt("   Hard mask sequence");
		if (ATTRPROT)  		prt("   Include 1st protein name in attributes");
		if (ATTRPROTALL)  	prt("   Include all protein names in attributes");
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
	
	 /*************************************************************************
	  * Gene; keeps track of products to merge
	  */
	private class Gene {
		private String chr="", line="", products="", proteins="";
		private HashSet <String> protSet = new HashSet <String>();
		
		private Gene(String chr, String line) {
			this.chr = chr;
			this.line = line;
		}
		private void mergeProtein(String protein) {
			String p = protein.replace("cds-","");
			if (!protSet.contains(protein)) {
				if (proteins=="") proteins = p;
				else proteins += "," + p;
				protSet.add(protein);
			}
 		}
		private boolean bNoProduct() {return products.equals("");}
		private void setProduct(String idrna, String curProduct) {
			if (!curProduct.equals("")) cntUniqueProduct++;
			products = idrna + ";" + curProduct;
			return;
		}
		private void mergeProduct(String idrna, String newProd) {
			if (!products.equals("") && newProd.equals("")) return;
			
			// Multiple mRNA tend to be variants: 
			// product=probable glutamate carboxypeptidase 2%2C transcript variant X1;
			// product=probable glutamate carboxypeptidase 2%2C transcript variant X2
			// so just make subsequent ones be X2; etc
			int index = newProd.lastIndexOf(" ");
			String desc = (index>=0) ? newProd.substring(0, index) : newProd;
			String x =    (index>=0) ? newProd.substring(index) : "";
			
			// check this mRNA product with all existing products for gene
			String [] prevProds = products.split(";"); 
			
			for (int j=1; j<prevProds.length; j++) { // variant? X1, X2....
				if (index>0 && x.startsWith(" X")) {
					int index2 = prevProds[j].lastIndexOf(" ");
					if (index2>0) {
						String desc2 = prevProds[j].substring(0, index2);
		
						if (desc2.equals(desc)) { // is family
							prevProds[j] +=  "," + x.trim(); // get rid of leading blank
							
							// rebuild list
							String newProds=prevProds[0];
							for (int k=1; k<prevProds.length; k++) {
								newProds += ";" + prevProds[k];
							}
							cntXProduct++;
							products= newProds;
							return;
						}
					}
				}
				if (prevProds[j].startsWith(newProd) || newProd.startsWith(prevProds[j])) {
					cntDupProduct++;
					return;
				}
			}
			cntUniqueProduct++;
			cntMultUniqueProduct++;
			products += ";" + newProd;
		}
		private String createLine(String id) {
			String [] tok = line.split("\\t");
			if (tok.length!=9) die("Gene: " + tok.length + " " + line);
			
			String [] attrs = tok[8].split(";");
			String idAt = idAttrKey + "=" + id + ";";
			
			String name = getVal(nameAttrKey, attrs);
			String idAt2 = (!id.contains(name)) ? nameAttrKey + "=" + name + ";" : "";
			
			String newAttrs = idAt + idAt2 + products;
			
			String line = chr + "\t" + tok[1] + "\t" + tok[2] + "\t" + tok[3] + "\t" + tok[4]
			+ "\t" + tok[5] + "\t" + tok[6] + "\t" + tok[7] + "\t" + newAttrs;
			
			if ((ATTRPROT || ATTRPROTALL) && proteins!="") {
				line += ";protein=" + proteins; // CAS520 add proteins
			}
			if (line.contains("%")) {
				for (String hex : hexMap.keySet()) {
					if (line.contains(hex)) line = line.replace(hex, hexMap.get(hex));
				}
			}
			if (protSet.size()>maxProt) {
				maxProt = protSet.size();
				maxGene = newAttrs.substring(0, newAttrs.indexOf(";"));
			}
			protSet.clear();
			return line;
		}
		private String getChr() { return chr;}
	}
}
