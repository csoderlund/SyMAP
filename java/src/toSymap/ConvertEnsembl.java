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
 * See https://csoderlund.github.io/SyMAP/toSymap/ensembl.html
 * 
 * Called from toSymap, but can be used stand-alone.
 * 
 * Written by CAS Oct2019; made part of toSymap and other changes for CAS557
 * This assumes input of, e.g:
 * 1. ftp://ftp.ensemblgenomes.org/pub/plants/release-45/fasta/<species>/dna/<species>.dna_sm.toplevel.fa.gz
 *    Note: either hard-masked (rm) where repeats are replaced with Ns, or soft-masked (sm) where repeats are in lower-case text.
 * 2. ftp://ftp.ensemblgenomes.org/pub/plants/release-45/gff3/<species>/<species>.chr.gff3.gz
 *
 * Note: I am no expert on Ensembl GFF format, so I cannot guarantee this is 100% correct mapping for all input.
 * Your sequences/annotations may have other variations.
 * Hence, you may want to edit this to customize it for your needs -- its simply written so easy to modify.
 * See https://csoderlund.github.io/SyMAP/toSymap/ensembl.html#edit
 * 
 * Differences with NCBI GFF (at least for Arabidopsis): 
 * 	NCBI has a different 'product' description for each mRNA, whereas Ens only has a description for the gene.
 *  NCBI has a chromosome name such as NC_003070.9, whereas Ens will use the chromosome#, or other...
 *  NCBI provide an protein identifier with the mRNA, whereas I do not see that with Ens.
 */

public class ConvertEnsembl {
	private static boolean isToSymap=true;
	static private int defGapLen=30000;
	
	private String logFileName = "/xConvertENS.log";
	private PrintWriter logFile=null;
	
	// args
	private boolean VERBOSE = false; 
	private boolean INCLUDESCAF = false;
	private boolean INCLUDEMtPt = false; 
	
	private int gapMinLen;     // set in main, Print to gap.gff if #N's is greater than this
	private String prefixOnly=null; // Set from main; e.g. NC
	private boolean bNumOnly=false;
	
	private final int maxName=5;
	
	private int traceChrNum=20;
	private int traceNum=5;	// if verbose, change to 20
	private final int scafMaxLen=10000;	// counted if less than this
	
	private String chrPrefix="Chr";
	private String scafPrefix="s";
	private String unkPrefix="Unk";
		
	// types
	private final String geneType = "gene";
	private final String mrnaType = "mRNA";
	private final String exonType = "exon";
	
	// attribute keywords
	private final String idAttrKey 		= "ID"; 			// Gene and mRNA
	private final String nameAttrKey 	= "Name"; 			// All
	private final String descAttrKey	= "description"; 	// Gene
	private final String biotypeAttrKey = "biotype"; 		// Gene
	private final String biotypeAttr 	= "protein_coding"; // Gene
	private final String parentAttrKey 	= "Parent"; 		// mRNA and Exon
	private final String star = "*";
		
	// input
	private String projDir = null; // command line input
	// Ensembl allows individual chromosomes to be downloaded, hence, the vector
	private Vector <String> inFaFile = new Vector <String> (); // must be in projDir, end with fastaSuffix
	private Vector <String> inGffFile = new Vector <String> (); // must be in projDir, end with annoSuffix
	
	//output - the projDir gets appended to the front
	private String seqDir 		=  "sequence";
	private String outFaFile 	=  "/genomic.fa";
	private String annoDir 		=  "annotation";
	private String outGffFile 	=  "/anno.gff";
	private String outGapFile 	=  "/gap.gff";
	
	// All chromosome and scaffold names are mapped to ChrN and ScafN
	private TreeMap <String, Integer> cntChrGene = 	new TreeMap <String, Integer> ();
	private TreeMap <String, Integer> cntScafGene = 	new TreeMap <String, Integer> ();
	private TreeMap <String, String>  id2scaf = new TreeMap <String, String> ();
	private TreeMap <String, String>  id2chr = new TreeMap <String, String> ();
	private TreeMap <String, String>  chr2id = new TreeMap <String, String> ();
	
	private TreeMap <String, Integer> allTypeCnt = 	new TreeMap <String, Integer> ();
	private TreeMap <String, Integer> allGeneBioTypeCnt = 	new TreeMap <String, Integer> ();
	private TreeMap <String, Integer> allGeneSrcCnt = 		new TreeMap <String, Integer> ();
	
	private int cntGene=0, cntExon=0, cntMRNA=0, cntGeneNotOnSeq=0, cntNoDesc=0, cntScafSmall=0;
	private int cntChrGeneAll=0, cntScafGeneAll=0, cntOutSeq=0, cntNoOutSeq=0;
	private int nChr=0, nScaf=0, nMt=0, nUnk=0, nGap=0; // for numbering output seqid and totals 
	private int cntChr=0, cntScaf=0, cntMtPt=0, cntUnk=0; // for counting regardless of write to file
	private long chrLen=0, scafLen=0, mtptLen=0, unkLen=0;
	
	private HashMap <String, String> hexMap = new HashMap <String, String> (); // CAS548 add
	
	TreeMap <Character, Integer> cntBase = new TreeMap <Character, Integer> ();
	private PrintWriter fhOut, ghOut;
	
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
		projDir = args[0];
		if (projDir==null || projDir.isEmpty()) {
			System.err.println("No project directory");
			return;
		}
		prt("\n------ ConvertEnsembl " + projDir + " ------");
		gapMinLen = gapLen;
		prefixOnly = prefix;
		bNumOnly = bNum;
		
		hexMap.put("%09", " "); // tab
		hexMap.put("%0A", " "); // newline
		hexMap.put("%25", "%"); // %
		hexMap.put("%2C", ","); // ,
		hexMap.put("%3B", ";"); // ;
		hexMap.put("%3D", "="); // =
		hexMap.put("%26", "&"); // &
		
		createLog(args); 	if (!bSuccess) return;
		checkArgs(args);	if (!bSuccess) return;
		checkInitFiles();	if (!bSuccess) return;
		
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
		else if (cntOutSeq>30)  prt( "Suggestion: There are " + cntOutSeq + " sequence. "
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
			fhOut.println("### Written by SyMAP ConvertEnsembl");
			ghOut = new PrintWriter(new FileOutputStream(annoDir + outGapFile, false));
			ghOut.println("### Written by SyMAP ConvertEnsembl");
			
			for (String file : inFaFile) {
				rwFasta(file);
			}
			fhOut.close(); ghOut.close();
    		System.err.print("                                            \r");
    		prt(String.format("Sequences not output: %,d", cntNoOutSeq));
    		prt("Finish writing " + outFaFile + "                          ");
    		
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
		
			prt("");
			prt(String.format("Gaps >= %d: %d (using N and n)", gapMinLen, nGap));
			prt("Finish writing " + outGapFile + "                          ");
		}
		catch (Exception e) {die(e, "rwFasta");}
	}
	private void rwFasta(String fileName) {
	try {
		prt("Processing " + fileName);
		
		String prtName="", idCol1="", line;
		boolean bPrt=false, isChr=false, isScaf=false, isMtPt=false;
		boolean bMt=false, bPt=false;
		int baseLoc=0, gapStart=0, gapCnt=1, len=0, lineN=0;
		
		BufferedReader fhIn = openGZIP(fileName); if (!bSuccess) return;
		
		while ((line = fhIn.readLine()) != null) {
			lineN++;
			if (line.startsWith("!!") || line.startsWith("#") || line.isEmpty()) continue;
			
			if (line.startsWith(">")) {
				if (len>0) {
					printTrace(bPrt, isChr, isScaf, isMtPt, len, idCol1, prtName);
					len=0;
				}
				bPrt=false;
				String line1 = line.substring(1);
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
					prtName = isChrNum(idCol1) ? chrPrefix + padNum(tok[0]) : tok[0]; // tok[0], e.g. C1 for cabbage
					cntChrGene.put(idCol1, 0);
					id2chr.put(idCol1, prtName);
					chr2id.put(prtName, idCol1);
					
					fhOut.println(">" + prtName);
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
					
					fhOut.println(">" + prtName + "  " + idCol1);
					bPrt=true;
				}
				else if (isScaf && INCLUDESCAF) {
					nScaf++; 
					prtName = scafPrefix + padNum(nScaf+""); // scaf use name
					
    				id2scaf.put(idCol1, prtName);
    				cntScafGene.put(idCol1, 0);
    				gapStart=1; gapCnt=0; baseLoc=0;
    				
					fhOut.println(">" + prtName+ "  " + idCol1);
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
    					fhOut.println(">" + prtName+ "  " + idCol1);
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

    					if (cntBase.containsKey(b)) cntBase.put(b, cntBase.get(b)+1);
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
		if (len>0) printTrace(bPrt, isChr, isScaf, isMtPt, len, idCol1, prtName);
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
	
	/********************************************************************************
	 * GFF file, e.g.
1  araport11   gene    3631    5899    .       +       .       ID=gene:AT1G01010;Name=NAC001;biotype=protein_coding;description=NAC domain containing protein 1 [Source:NCBI gene (formerly Entrezgene)%3BAcc:839580];gene_id=AT1G01010;logic_name=araport11
1  araport11   mRNA    3631    5899    .       +       .       ID=transcript:AT1G01010.1;Parent=gene:AT1G01010;Name=NAC001-201;biotype=protein_coding;transcript_id=AT1G01010.1
1  araport11   five_prime_UTR  3631    3759    .       +       .       Parent=transcript:AT1G01010.1
1  araport11   exon    3631    3913    .       +       .       Parent=transcript:AT1G01010.1;Name=AT1G01010.1.exon1;constitutive=1;ensembl_end_phase=1;ensembl_phase=-1;exon_id=AT1G01010.1.exon1;rank=1
	 */
	private int skipLine=0;
	private void rwAnno() {
		try {
			if (inGffFile==null) return; 
			
			PrintWriter fhOut = new PrintWriter(new FileOutputStream(annoDir + outGffFile, false));
			fhOut.println("### Written by SyMAP ConvertEnsembl");
			
			int cntReadGenes=0, cntReadExon=0, cntReadMRNA=0;
			String line="", type="";
			String geneID=null, mrnaID=null;
			
			prt("");
			for (String file : inGffFile) {
				prt("Processing " + file);
				BufferedReader fhIn = openGZIP(file); if (!bSuccess) return;
			
				while ((line = fhIn.readLine()) != null) {
					if (line.startsWith("#") || line.startsWith("!!") || line.isEmpty()) continue;
					
					String [] tok = line.split("\\t");
					if (tok.length!=9) {
						badLine("Wrong columns: ", line);
						continue;
					}
					
					// filtered on type and attributes
					type = tok[2];
					String [] attrs = tok[8].split(";");
					
					if (!allTypeCnt.containsKey(type)) 	allTypeCnt.put(type,1);
					else 								allTypeCnt.put(type, allTypeCnt.get(type)+1);
					
					boolean isGene = type.equals(geneType);
					boolean isMRNA = type.equals(mrnaType);
					boolean isExon = type.equals(exonType);
					
					if (isGene) { 
						cntReadGenes++;
						if (cntReadGenes%5000==0)	System.err.print("Read " + cntReadGenes + " genes...\r");
						
						String src  = tok[1];
						if (!allGeneSrcCnt.containsKey(src))allGeneSrcCnt.put(src,1);
						else 								allGeneSrcCnt.put(src, allGeneSrcCnt.get(src)+1);
						
						String biotype = getVal(biotypeAttrKey, attrs);
						if (!allGeneBioTypeCnt.containsKey(biotype))allGeneBioTypeCnt.put(biotype,1);
						else 									    allGeneBioTypeCnt.put(biotype, allGeneBioTypeCnt.get(biotype)+1);
					
						geneID=mrnaID=null;
						
						biotype = getVal(biotypeAttrKey, attrs);
						if (!biotype.contentEquals(biotypeAttr)) continue; // not protein-coding
					}
					else if (isMRNA) cntReadMRNA++;
					else if (isExon) cntReadExon++;
					else continue;
					
					boolean isChr=false;
					String prtName="";
					String chrCol =   tok[0];  // chromosome, scaffold, ...
					if (id2chr.containsKey(chrCol))  { 
						prtName = id2chr.get(chrCol);
						isChr=true;
					}
					else if (id2scaf.containsKey(chrCol)) {
						prtName = id2scaf.get(chrCol);
					}
					else {
						if (isGene) cntGeneNotOnSeq++;
						continue;
					}
					
					/** ready to process **/
					if (isGene) { 
						String id = getVal(idAttrKey, attrs);
						if (id.contentEquals("")) {
							badLine("No ID: ", line);
							continue;
						}
						// accept gene 
						cntGene++;
						if (isChr) {
							cntChrGeneAll++; 
							cntChrGene.put(chrCol, cntChrGene.get(chrCol)+1);
						}
						else {
							cntScafGeneAll++;
							cntScafGene.put(chrCol, cntScafGene.get(chrCol)+1);	
						}
						geneID=id;
						String nLine = createGeneLine(prtName, line, tok, attrs);
						fhOut.println(nLine);
					}
					else if (isMRNA) {
						if (geneID==null) 					continue;
						if (mrnaID!=null) 					continue;
						
						String parent = getVal(parentAttrKey, attrs);
						if (!parent.contentEquals(geneID)) 	continue;
						
						String id = getVal(idAttrKey, attrs);
						if (id.contentEquals("")) {
							skipLine++;
							if (skipLine<3) prt("Bad line: " + line);
							continue;
						}
						// accept mRNA
						cntMRNA++;
						mrnaID = id;
						String nLine = createMRNALine(prtName, line, tok, attrs);
						fhOut.println(nLine);
					}
					else if (isExon) {
						if (mrnaID==null) 					continue;
						
						String parent = getVal(parentAttrKey, attrs);
						if (!parent.contentEquals(mrnaID)) 	continue;
						
						// accept Exon
						cntExon++;
						String nLine = createExonLine(prtName, line, tok, attrs);
						fhOut.println(nLine);
					}
				}
				fhIn.close();
			}
			fhOut.close();
			prt(String.format("   Use Genes %,d from %,d              ", cntGene, cntReadGenes));
			prt(String.format("   Use mRNAs %,d from %,d ", cntMRNA, cntReadMRNA));
			prt(String.format("   Use Exons %,d from %,d ", cntExon, cntReadExon));
			prt("Finish writing " + outGffFile);
		}
		catch (Exception e) {die(e, "rwAnno");}
	}
	private void badLine(String msg, String line) {
		skipLine++;
		if (skipLine<3) 	prt(msg + line);
		if (skipLine>10000) die("Bad Lines: " + skipLine+ " too many bad lines ...");
	}
	private String createGeneLine(String chr, String l, String [] tok, String [] attrs) {
		String attr = getKeyVal(idAttrKey, attrs) + ";" + getKeyVal(nameAttrKey, attrs);
		
		String desc = getVal(descAttrKey, attrs);
		if (desc!=null) {
			if (desc.contains("[")) desc = desc.substring(0, desc.indexOf("["));
			
			if (desc.contains("%")) { // CAS548 add
				for (String hex : hexMap.keySet()) {
					if (desc.contains(hex)) desc = desc.replace(hex, hexMap.get(hex));
				}
			}
			
			attr += ";desc=" + desc;
		}
		else cntNoDesc++;
		
		return "###\n" + chr + "\t" + tok[1] + "\t" + tok[2] + "\t" + tok[3] + "\t" + tok[4]
		+ "\t" + tok[5] + "\t" + tok[6] + "\t" + tok[7] + "\t" + attr;
	}
	private String createMRNALine(String chr, String l, String [] tok, String [] attrs) {
		String attr = getKeyVal(idAttrKey, attrs) + ";" + getKeyVal(parentAttrKey, attrs);
		
		return chr + "\t" + tok[1] + "\t" + tok[2] + "\t" + tok[3] + "\t" + tok[4]
				+ "\t" + tok[5] + "\t" + tok[6] + "\t" + tok[7] + "\t" + attr;
	}
	private String createExonLine(String chr, String l, String [] tok, String [] attrs) {
		String attr = getKeyVal(parentAttrKey, attrs) + ";" + getKeyVal(nameAttrKey, attrs);
		
		return chr + "\t" + tok[1] + "\t" + tok[2] + "\t" + tok[3] + "\t" + tok[4]
				+ "\t" + tok[5] + "\t" + tok[6] + "\t" + tok[7] + "\t" + attr;
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
				prt(String.format("  %,6d Output %-,6d %-11s  %,15d %s", cntUnk, nUnk, "Unknown", unkLen));
			}
			if (inGffFile==null) return;
			
			prt("                                         ");
			prt(">>All Type (col 3)");
			for (String key : allTypeCnt.keySet())  {
				prt(String.format("   %-22s %,8d", key, allTypeCnt.get(key)));
			}
			prt(">>All Gene Source (col 2)");
			for (String key :allGeneSrcCnt.keySet()) {
				prt(String.format("   %-22s %,8d", key, allGeneSrcCnt.get(key)));
			}
			prt(">>All gene biotype= (col 8)                    ");
			for (String key : allGeneBioTypeCnt.keySet())  {
				prt(String.format("   %-22s %,8d", key, allGeneBioTypeCnt.get(key)));
			}
			if (cntNoDesc>0) {
				prt(">>Description");
				prt(String.format("   %-22s %,8d", "None", cntNoDesc));
			}
			prt(">>Written to file ");
			prt(String.format("   %-22s %,8d", "Genes ", cntGene));
			prt(String.format("   %-22s %,8d", "mRNA ", cntMRNA));
			prt(String.format("   %-22s %,8d", "Exons ", cntExon));
		}
		prt(String.format(">>Chromosome gene count %,d", cntChrGeneAll));
		
		for (String prt : chr2id.keySet()) {
			String id = chr2id.get(prt); // sorted on prtName
			int cnt   = cntChrGene.get(id);
			prt(String.format("   %-10s %-20s %,8d", prt, id, cnt));
		}
		
		if (INCLUDESCAF) {
			prt(String.format(">>Scaffold gene count %,d (list scaffolds with #genes>1)", cntScafGeneAll));
			int cntOne=0;
			for (String key : cntScafGene.keySet()) { // sorted on idCol1, which is not scaffold order
				int cnt = cntScafGene.get(key);
				if (cnt>1) prt(String.format("   %-7s %-15s %,8d", id2scaf.get(key), key, cnt));
				else cntOne++;
			}
			prt(String.format("   Genes not included %,8d",  cntGeneNotOnSeq));
			prt(String.format("   Scaffolds with 1 gene (not listed) %,8d",  cntOne));
		}
		else prt(String.format("   Genes not on Chromosome %,8d",  cntGeneNotOnSeq));
	}
	/************************************************************
	 * File stuff
	 */
	private void checkInitFiles() {
		// find fasta and gff files
		try {
			File dir = new File(projDir);
			if (!dir.isDirectory()) 
				die("The argument must be a directory. " + projDir + " is not a directory.");
			
			File[] files = dir.listFiles();
			for (File f : files) {
				if (f.isFile()) { 
			       String fname = f.getName();
			     
			       if (fname.endsWith(".fa.gz") || fname.endsWith(".fa")) 		inFaFile.add(projDir + "/" + fname);
			       else if (fname.endsWith(".gff3.gz") || fname.endsWith(".gff3"))	inGffFile.add(projDir + "/" +fname); 	
			       else if (fname.endsWith(".gff.gz") || fname.endsWith(".gff")) inGffFile.add(projDir + "/" +fname); 			
				} 
			}
		}
		catch (Exception e) {die(e, "Checking files");}
		
		if (inFaFile.size()==0)  {
			die("Project directory " + projDir + ": no file ending with .fa or .fa.gz (i.e. Ensembl files)");
		}
		if (inGffFile.size()==0)
			prt("Project directory " + projDir + " does not have a file ending with .gff3 or .gff3.gz");
		
		// Create sequence and annotation directories
		seqDir = projDir + "/" + seqDir;
		createDir(seqDir);
		annoDir = projDir + "/" + annoDir;
		createDir(annoDir);
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
	
	private boolean createDir(String dir) {
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
				else if (args[i].equals("-u")) {
					INCLUDESCAF=true;
					chrPrefix = "C";
					prt("   Use FASTA file labels if <=" + maxName);
					prt("      Others will be called Scaffold with prefix '" + scafPrefix + "'");
				}
				else if (args[i].equals("-s")) {
					INCLUDESCAF=true;
					chrPrefix = "C";
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
