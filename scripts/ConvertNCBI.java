
/*************************************************************
 * ConvertNCBI: NCBI genome files formatted for SyMAP
 * See https://csoderlund.github.io/SyMAP/convert/ncbi.html
 * 
 * Written by CAS 16/Jan/18; 
 *         update 20/Feb/20 add hard-masked option; check parent and allow genes, mRNA and exons to be in any order
 *   	   update 21/Mar/20 add gap file
 *   	   update 31/Jan/22 add ncbi_dataset format; convert hex to char
 *   	   update 14/Aug/22 v512 
 *   						produce one gff file, write the gene entry followed by the first mRNA's exons
 *   						better summary output
 *         
 * Note: I am no expert on NCBI GFF format, so I cannot guarantee this is 100% correct mapping.
 * Your sequences/annotations may have other variations.
 * Hence, you may want to edit this to customize it for your needs -- its simply written so easy to modify.
 * Also, the ncbi_dataset format seems to have irregular gff files; if that is the only option, you
 * definitely may need to edit this.
 * 
 * The 'product' description must be in the mRNA product attribute!!
   Annotation description: 	ID=gene-;Name=(if unique) and ID=rna-;product= and Parent=
   Types used:   gene, mRNA, exon
   Attributes:   ID, gene_biotype, parent, product, gene
   Only process: gene-biotype='protein_coding'
   For gene product, first mRNA product description and appends remaining mRNA variants
		e.g. product=phosphate transporter PHO1-1-like%2C transcript variant X1,X2
 */
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.zip.GZIPInputStream;

public class ConvertNCBI {
	// flags set by user
	private boolean LINKAGE = false;
	private boolean INCLUDESCAF = false;
	private boolean MASKED = false;
	private boolean VERBOSE = false;
	private boolean ALLEXON = false; // not documented
	
	// Changed for specific input
	private String chrPrefix="Chr";	// changed to "c" if scaffolds too
	private String scafPrefix="s"; // single letter is used because the "Chr" is usually removed to save space
	
	private final int gapLen=30000;     // Print to gap.gff if #N's is greater than this
	private final int scafMaxNum=20;	// print first 20
	private final int scafMaxLen=10000;	// All scaffold are printed to fasta file, but only summarized if >scafMax
	
	// search in .fna files
	private final String chrKey		= "chromosome";
	private final String lgKey 		= "linkage";
	private final String scaf1Key 	= "scaffold";  
	private final String scaf2Key 	= "contig"; // ncbi_dataset
		
	// types
	private final String geneType = "gene";
	private final String mrnaType = "mRNA";
	private final String exonType = "exon";
	
	// attribute keywords
	private final String idAttrKey 		= "ID";
	private final String nameAttrKey 	= "Name"; 
	private final String biotypeAttrKey	= "gene_biotype";
	private final String biotypeAttr 	= "protein_coding";
	private final String parentAttrKey 	= "Parent";
	private final String productAttrKey = "product";  
	private final String exonGeneAttrKey = "gene";
	
	private HashMap <String, String> hexMap = new HashMap <String, String> ();
	
	// input 
	private String projDir = null;
	private String inFaFile=null;
	private String inGffFile= null;
	
	// downloaded via NCBI Dataset link
	private String ncbi_dataset="ncbi_dataset"; 
	private String ncbi_data=	"/data"; 
	private String subDir=null;
	private TreeSet <String> inFaFiles = new TreeSet <String> ();
	 
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
	private int nScaf=0, nChr=0, nOther=0, nGap=0, cntChr=0;
	private long chrLen=0, scafLen=0, otherLen=0;
	private final int PRT = 10000;
	
	public static void main(String[] args) {
		new ConvertNCBI(args);
	}
	private ConvertNCBI(String [] args) {
		prt(">>>> ConvertNCBI <<<<");
		
		hexMap.put("%2C", ",");
		hexMap.put("%25", "%");
		hexMap.put("%3B", ";");
		
		checkArgs(args);
		checkInitFiles();
		
		rwFasta();
		
		rwAnno();
		
		printSummary();
		prt("ConvertNCBI completed");
	}
	/***************************************************************************
	 * * Fasta file example:
	 * >NC_016131.2 Brachypodium distachyon strain Bd21 chromosome 1, Brachypodium_distachyon_v2.0, whole genome shotgun sequence
	 * >NW_014576703.1 Brachypodium distachyon strain Bd21 unplaced genomic scaffold, Brachypodium_distachyon_v2.0 super_11, whole genome shotgun sequence *
	 * NOTE: NCBI files are soft-masked, which is used to compute the gaps.
	 *       If its then hard-masked (soft->N's), gaps and hard-masked appear the same 
	 */
	PrintWriter fhOut, ghOut;
	int cntMask=0;
	TreeMap <Character, Integer> cntBase = new TreeMap <Character, Integer> ();
	private void rwFasta() {
		try {
			fhOut = new PrintWriter(new FileOutputStream(seqDir + outFaFile, false));
			fhOut.println("### Written by SyMAP ConvertNCBI");
			ghOut = new PrintWriter(new FileOutputStream(annoDir + outGapFile, false));
			ghOut.println("### Written by SyMAP ConvertNCBI");
			
			char [] base = {'A', 'C', 'G', 'T', 'N', 'a', 'c', 'g', 't', 'n'};
			for (char b : base) cntBase.put(b, 0);
			
			if (subDir==null) { // single FASTA
				prt("\nProcessing " + inFaFile);
				rwFasta(inFaFile);
			}
			else {				// ncbi_dataset
				prt("\nProcessing fasta files");
				for (String file : inFaFiles) rwFasta(file);
			}
			fhOut.close(); ghOut.close();
			prt("Finish writing " + outFaFile + "                     ");
			
			prt( String.format("A %,11d  a %,11d", cntBase.get('A'), cntBase.get('a')) );
			prt( String.format("T %,11d  t %,11d", cntBase.get('T'), cntBase.get('t')) );
			prt( String.format("C %,11d  c %,11d", cntBase.get('C'), cntBase.get('c')) );
			prt( String.format("G %,11d  g %,11d", cntBase.get('G'), cntBase.get('g')) );
			prt( String.format("N %,11d  n %,11d", cntBase.get('N'), cntBase.get('n') ));
			cntBase.clear();
			
			if (MASKED) prt(String.format("Hard masked: %,d lower case changed to N", cntMask));
			
			prt(String.format("Gaps >= %d: %d", gapLen, nGap));
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
			BufferedReader fhIn = openGZIP(fastaFile);
		
			String line="";
			int len=0;
			
			String idcol1="", prtName="";
			boolean bPrt=false, isChr=false, isScaf=false;
			int baseLoc=0, gapStart=0, gapCnt=1;
			
			while ((line = fhIn.readLine()) != null) {
    			if (line.startsWith(">")) { // id line
    				if (len>0) {
    					printTrace(isChr, isScaf, len, idcol1, prtName);
    					len=0;
    				}
    				String line1 = line.substring(1);
    				String [] tok = line1.split("\\s+");
    				idcol1 = tok[0];
    				
    				isChr = (LINKAGE) ? line.contains(lgKey) : line.contains(chrKey);
    				isScaf = line.contains(scaf1Key) || line.contains(scaf2Key);
    		
    				bPrt=false; // CAS508 
    				if (isChr && !isScaf) { // can have chromosome and scaffold on same > line
    					cntChr++;
    					prtName = createChrPrtName(line);
	    				id2chr.put(idcol1, prtName);
	    				chr2id.put(prtName, idcol1);
	    				cntChrGene.put(idcol1, 0);
	    				gapStart=1; gapCnt=0; baseLoc=0;
	    				
	    				bPrt=true;
    					fhOut.println(">" + prtName + " " + idcol1);
    				}
    				else if (isScaf && INCLUDESCAF) {
    					nScaf++;
	    				prtName = scafPrefix + padNum(nScaf+"");
	    				id2scaf.put(idcol1, prtName);
	    				cntScafGene.put(idcol1, 0);
	    				gapStart=1; gapCnt=0; baseLoc=0;
	    				
    					bPrt=true;
    					fhOut.println(">" + prtName+ "  " + idcol1);
    				}
    				else {
    					if (isScaf) nScaf++;
    					else 		nOther++;
    					prtName="Ignore";
    				}
    			}
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
	    						if (gapCnt>gapLen) {
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
    			}
    		}
			if (len>0) printTrace(isChr, isScaf, len, idcol1, prtName);
			System.err.print("                                            \r");
			fhIn.close(); 	
		}
		catch (Exception e) {die(e, "rwFasta: " + fastaFile);}
	}
	// >NC_015438.3 Solanum lycopersicum cultivar Heinz 1706 chromosome 12, SL3.0, whole genome";
	// >NC_003279.8 Caenorhabditis elegans chromosome III
	// >NC_003070.9 Arabidopsis thaliana chromosome X sequence
	private String createChrPrtName(String line) {
		try {
			String name=null;
			Pattern patFA1 = Pattern.compile("(.*)\\schromosome\\s+([0-9IVXY]*),\\s(.+$)");
			Matcher m = patFA1.matcher(line);
			if (m.matches()) name = m.group(2);
			else {
				Pattern patFA2 = Pattern.compile("(.*)\\schromosome\\s+([0-9IVXY]*)$");
				m = patFA2.matcher(line);
				if (m.matches()) name = m.group(2);
				else {
					Pattern patFA3 = Pattern.compile("(.*)\\schromosome\\s+([0-9IVXY]*)\\s(.+$)");
					m = patFA3.matcher(line);
					if (m.matches()) name = m.group(2);
				}
			}
			if (name==null) {
				name = nChr+""; 
				nChr++;
			}
			
			name = chrPrefix + padNum(name);
			
			if (chr2id.containsKey(name)) {
				prt(name + " not unique: line " + line);
				while (chr2id.containsKey(name) && nChr<50) {
					name = chrPrefix + padNum(nChr+"");
					nChr++;
				}
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
	// prtName is 'Ignore' if not write to fa file
	private void printTrace(boolean isChr, boolean isScaf, int len, String id, String prtName) {
		if (isChr && !isScaf) {
			chrLen+=len;
			prt(String.format("%-10s  %-20s %,11d", prtName, id, len));
		}
		else if (isScaf) {
			scafLen+=len;
			if (len<scafMaxLen) cntScafSmall++;
			
			if (INCLUDESCAF) {
				if (nScaf<=scafMaxNum || VERBOSE) 	prt(String.format("%-7s %-15s %,11d", prtName, id, len));
				else if (nScaf==scafMaxNum+1) 		prt("Suppressing further scaffold outputs");
				else if (nScaf%100==0) 				System.err.print(String.format("     Scaffold #%,d Len=%,d ...", nScaf, len) + "\r");
			}
			else {
				if (VERBOSE) 						prt(String.format("%-7s %-15s %,11d Scaffold", prtName, id, len));
				else if (nScaf%100==0) 				System.err.print(String.format("     Scaffold #%,d Len=%,d ...", nScaf, len) + "\r");
			}
		}
		else {
			otherLen+=len;
			if (VERBOSE)  							prt(String.format("%-7s %-15s %,11d Other", prtName, id, len));
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
			prt("\nProcessing " + inGffFile);
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
			 BufferedReader fhIn = openGZIP(inGffFile);
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
			 prt(String.format("   Use Genes %,d from %,d        ", geneMap.size(), cntGene));
		 }
		 catch (Exception e) {die(e, "rwAnnoGene");}
	 }
	 /** add product to gene attributes **/
	 private void rwAnnoMRNA() { 
		 try {
			 prt("Process mRNA....");
			 BufferedReader fhIn = openGZIP(inGffFile);
			 String line="";
			 int cntReadRNA=0;
			 
			 while ((line = fhIn.readLine()) != null) {
				line = line.trim();
				if (line.startsWith("#") || line.length()==0) continue;
				
				String [] tok = line.split("\\t");
				if (tok.length!=9) continue;
				
				String type =  tok[2];  // gene, mRNA, exon...
				if (!type.equals(mrnaType)) continue;
				cntReadRNA++;
				if (cntReadRNA%PRT==0) System.err.print("   Process " + cntReadRNA + " mRNA...\r");
				
				String [] typeAttrs = tok[8].split(";"); 
				String pid = getVal(parentAttrKey, typeAttrs);
				if (!geneMap.containsKey(pid)) continue;	// parent gene was excluded
				
				String idKey =      getKeyVal(idAttrKey, typeAttrs);
				String productKey = getKeyVal(productAttrKey, typeAttrs);
				
				Gene g = geneMap.get(pid);
				boolean bIs1st = g.bNoProduct();
				if (bIs1st)	g.setProduct(idKey, productKey);
				else		g.mergeProduct(idKey, productKey);
	
				if (bIs1st || ALLEXON) {
					String mID =  getVal(idAttrKey, typeAttrs);
					mrnaMap.put(mID, g);	
				}
			 }
			 fhIn.close();
			 prt(String.format("   First mRNAs %,d from %,d           ", mrnaMap.size(), cntReadRNA));		 
		 }
		 catch (Exception e) {die(e, "rwAnnoMRNA");}
	 }
	 /** Write genes and exons to file**/
	 private void rwAnnoExon() {
		 try {
			 prt("Process exons....");
			 PrintWriter fhOutGff = new PrintWriter(new FileOutputStream(annoDir + outGffFile, false)); 
			 fhOutGff.println("### Written by SyMAP ConvertNCBI");
			 
			 BufferedReader fhIn = openGZIP(inGffFile);
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
			 prt(String.format("   Write Exons %,d  from %,d            ", cntExon, cntReadExon));
			 prt("Finish writing " + outGffFile + "                          ");
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
		prt("                                         ");
		prt(">>Sequence                      ");
		String nw = "-- not written to file";
			System.out.format("   %,5d %-11s of %,15d total length\n", cntChr, "Chromosomes", chrLen);
	
		if (nScaf>0) {
			String x = (INCLUDESCAF) ? "" : nw;
			System.out.format("   %,5d %-11s of %,15d total length (%,d<%,d) %s\n", nScaf, "Scaffolds", scafLen, cntScafSmall, scafMaxLen, x);
		}
		if (nOther>0)
			System.out.format("   %,5d %-11s of %,15d total length %s\n", nOther, "Other", otherLen, nw);
		
		prt("                                         ");
		prt(">>All Types  (col 3)                       ");
		for (String key : allTypeCnt.keySet()) {
			String x = (key.equals(geneType) || key.equals(mrnaType) || key.equals(exonType)) ? "*" : "";
			prt(String.format("   %-22s %,8d %s", key, allTypeCnt.get(key), x));
		}
		prt(">>All Gene Source (col 2)");
		for (String key :allGeneSrcCnt.keySet()) {
			prt(String.format("   %-22s %,8d *", key, allGeneSrcCnt.get(key)));
		}
		prt(">>All gene_biotype=                       ");
		for (String key : allGeneBiotypeCnt.keySet()) {
			String x = (key.equals(biotypeAttr)) ? "*" : "";
			prt(String.format("   %-22s %,8d %s", key, allGeneBiotypeCnt.get(key), x));
		}

		prt(">>Gene product=");
		prt(String.format("   %-22s %,8d", "Unique", cntUniqueProduct));
		if (cntMultUniqueProduct>0) prt(String.format("   %-22s %,8d", "Multiple Unique", cntMultUniqueProduct));
		if (cntDupProduct>0) prt(String.format("   %-22s %,8d", "Substring (ignored)", cntDupProduct));
		prt(String.format("   %-22s %,8d", "Variants", cntXProduct));
		
		prt(">>Written to file ");
		prt(String.format("   %-22s %,8d", "Gene", geneMap.size()));
		prt(String.format("   %-22s %,8d", "mRNA", mrnaMap.size()));
		prt(String.format("   %-22s %,8d", "Exon", cntExon));
		
		prt(String.format(">>Chromosome gene count %,d                ", cntChrGeneAll));
		for (String prt : chr2id.keySet()) {
			String id = chr2id.get(prt);
			prt(String.format("   %-10s %-20s %,8d", prt, id, cntChrGene.get(id)));
		}
		if (INCLUDESCAF) {
			prt(String.format(">>Scaffold gene count %,d (list scaffolds with #genes>0) ", cntScafGeneAll));
			for (String id : cntScafGene.keySet()) 
				if (cntScafGene.get(id)>0)
					prt(String.format("   %-10s %-20s %,8d", id2scaf.get(id), id, cntScafGene.get(id)));
		}
		
		if (INCLUDESCAF) System.out.format("   %s %,8d\n", "Genes not on Chr/Scaf", cntGeneNotOnSeq);
		else 			 System.out.format("   %s %,8d\n", "Genes not on Chromosome", cntGeneNotOnSeq);
	 }
	 /**************************************************************************/
	 private void checkInitFiles() {
		seqDir = projDir + "/" + seqDir;
		checkDir(seqDir);
		annoDir = projDir + "/" + annoDir;
		checkDir(annoDir);
		
		boolean isDS=false;
		try {
			File dir = new File(projDir);
			if (!dir.isDirectory()) 
				die("The argument must be a directory. " + projDir + " is not a directory.");
			
			File[] files = dir.listFiles();
			for (File f : files) {
				if (f.isFile()) {
			       String fname = f.getName();
			       if (fname.endsWith(".fna.gz") || fname.endsWith(".fna")) 		inFaFile= projDir + "/" + fname;
			       else if (fname.endsWith(".gff.gz") || fname.endsWith(".gff"))	inGffFile= projDir + "/" +fname; 		
				} 
				else if (f.isDirectory()) {
					if (f.getName().contentEquals(ncbi_dataset)) {
						isDS = true;
						break;
					}
				}
			}
		}
		catch (Exception e) {die(e, "Checking files");}
		
		if (!isDS) {// fna and gff in projDir
			if (inFaFile==null) die("Project directory does not have a file ending with .fna or .fna.gz");
			if (inGffFile==null)die("Project directory does not have a file ending with .gff or .gff.gz");
			return;
		}
		
	/** check projDir/ncbi_dataset/data/<dir>  **/
		if (!projDir.endsWith("/")) projDir += "/";
		String dsDir = projDir + ncbi_dataset + ncbi_data;
			
		// find subdirectory
		try {
			File dir = new File(dsDir);
			if (!dir.isDirectory()) 
				die("ncbi_dataset directory is incorrect. " + dsDir);
			
			File[] files = dir.listFiles();
			for (File f : files) {
				if (f.isDirectory()) {
					subDir = f.getName();
					break;
				}
			}
		}
		catch (Exception e) {die(e, "Checking " + ncbi_dataset);}
		
		if (subDir==null) die(dsDir + " missing sub-directory");
		
		// find .fna and .gff files
		dsDir += "/" + subDir;
		try {
			File dir = new File(dsDir);
			if (!dir.isDirectory()) 
				die("Sub directory is incorrect. " + dsDir);
			
			File[] files = dir.listFiles();
			for (File f : files) {
				String fname = f.getName();
				if (fname.endsWith(".fna.gz") || fname.endsWith(".fna")) 		inFaFiles.add(dsDir + "/" + fname);
				else if (fname.endsWith(".gff.gz") || fname.endsWith(".gff"))	inGffFile= dsDir + "/" +fname; 
			}
		}
		catch (Exception e) {die(e, "Checking " + dsDir);}
		
		if (inGffFile==null)  	die(dsDir + " does not have a file ending with .gff or .gff.gz");
		if (inFaFiles.size()==0) die(dsDir + " has no files ending in .fna or .fna.gz");
		return;
	}
	 private boolean checkDir(String dir) {
		File nDir = new File(dir);
		if (nDir.exists()) {
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
	private BufferedReader openGZIP(String file) {
		try {
			if (!file.endsWith(".gz")) {
				File f = new File (file);
				if (f.exists())
	    				return new BufferedReader ( new FileReader (f));
				else {
					f = new File (file + ".gz");
					if (f.exists()) file = file + ".gz";
					else die("Cannot open file " + file);
				}
			}
			else if (file.endsWith(".gz")) {
				FileInputStream fin = new FileInputStream(file);
				GZIPInputStream gzis = new GZIPInputStream(fin);
				InputStreamReader xover = new InputStreamReader(gzis);
				return new BufferedReader(xover);
			}
			else die("Do not recognize file suffix: " + file);
		}
		catch (Exception e) {die(e, "Cannot open gzipped file " + file);}
		return null;
	}
	 private void checkArgs(String [] args) {
		if (args.length==0 || args[0].equals("-h") || args[0].equals("-help") || args[0].equals("help")) {
			prt("\nConvertNCBI <project directory> [options] 				");
			prt("   the project directory must contain:  " +
					        "\n       FASTA file ending with .fna or .fna.gz" +
					        "\n       GFF   file ending with .gff or .gff.gz" +  
					        "\nOptions:" +
					        "\n-m  assuming a soft-masked genome file, convert it to hard-masked." +
					        "\n-s  include " + scaf1Key + " and " + scaf2Key + "." +

					        "\n-l  use linkage instead of chromosome." +
					        "\n-v  write header lines of ignored sequences." +
							"\n\nSee https://csoderlund.github.io/SyMAP/convert for details.");
			System.exit(0);
		}
		
		projDir = args[0];
		if (args.length>1) {
			for (int i=1; i< args.length; i++)
				if (args[i].equals("-s")) {
					INCLUDESCAF=true;
					chrPrefix = "C";
				}
				else if (args[i].equals("-l")) LINKAGE=true;
				else if (args[i].equals("-v")) VERBOSE=true;
				else if (args[i].equals("-m")) MASKED=true;
				else if (args[i].equals("-e")) ALLEXON=true;
		}
		prt("Parameters:");
		if (ALLEXON) prt("   Write all exons for each gene to gff file");
		if (INCLUDESCAF) {
			prt("   Include "+ scaf1Key + " and " + scaf2Key);
			prt("      Uses prefixes Chr '" + chrPrefix + "' and Scaffold '" + scafPrefix);
			prt("      IMPORTANT: SyMAP Project Parameters - set grp_prefix to blank ");
		}
				
		if (LINKAGE) {
			prt("   Use Linkage groups");
			chrPrefix="Lg";
		}
		if (MASKED)  prt("   Hard mask sequence");
		if (VERBOSE) prt("   Verbose - print ignored sequences");
		prt("Project directory: " + projDir);
	}
	 private void die(Exception e, String msg) {
		System.err.println("Die -- " + msg);
		System.err.println("   Run 'convertNCBI -h'  to see help");
		e.printStackTrace();
		System.exit(-1);
	}
	 private void die(String msg) {
		System.err.println("Die -- " + msg);
		System.err.println("   Run 'convertNCBI -h'  to see help");
		System.exit(-1);
	}
	 private void prt(String msg) {
		System.out.println(msg);
	}
	private class Gene {
		private String chr="", line="", products="";
		
		Gene(String chr, String line) {
			this.chr = chr;
			this.line = line;
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
			
			if (line.contains("%")) {
				for (String hex : hexMap.keySet()) {
					if (line.contains(hex)) line = line.replace(hex, hexMap.get(hex));
				}
			}
			return line;
		}
		private String getChr() { return chr;}
	}
}
