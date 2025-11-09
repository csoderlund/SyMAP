package toSymap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.TreeMap;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import backend.Constants;
import backend.Utils;
import util.ErrorReport;
import util.Utilities;
import util.FileDir;

/***************************************
 * Check for existence FASTA and GFF
 * 1. top directory
 * 2. ncbi_dataset/data/<subdir>
 * 3. sequence and annotation directory
 * 
 * Fasta:
 * 	 provide list of prefixes
 * GFF 
 * 	 is there ID and Parent and do they abide by the rules
 * 	 is description in gene or mRNA
 * 
 * NCBI vs Ensembl: 
 * 	NCBI has "NCBI" in header lines whereas Ensembl has no indication in header
 *  NCBI uses "gene-biotype" whereas Ensembl used "biotype"
 *  NCBI has mRNA "products" whereas I haven't seen that in Ensembl
 */
public class Summary {
	private final String seqSubDir =     Constants.seqSeqDataDir;
	private final String annoSubDir =    Constants.seqAnnoDataDir;
	private final String [] fastaFile =  Constants.fastaFile;
	private String logFileName = "xSummary.log";
	private boolean bVerbose=false, bConverted=false; // args from xToSymap
	private int CNTHEAD=1, CNTPREFIX=5, CNTLEN=5; // #header, #unique prefix, #sequence len; changed if Verbose
	private int MAXBASE=Integer.MAX_VALUE;	  // if verbose, count bases for this many 
	
	private final String chrPrefix="Chr";
	private final String sChrPrefix="C";
	private final String scafPrefix="s";
	private final String geneType = "gene";
	private final String mrnaType = "mRNA";
	private final String exonType = "exon";
	private final String cdsType  = "CDS";
	
	private final String idAttrKey 		 = "ID"; 			 // Gene and mRNA
	private final String parentAttrKey 	 = "Parent"; 		 // mRNA and Exon
	private final String nBiotypeAttrKey = "gene_biotype";   // NCBI Gene 
	private final String eBiotypeAttrKey = "biotype"; 		 // Ensemble Gene
	private final String proteinCoding 	 = "protein_coding"; // biotype=protein_coding
	private final String proteinID       = "protein_id";	 // CDS attribute, NCBI and Ensembl
	
	private final String prodAttrKey 	= "product"; 		// NCBI
	private final String [] descAttrKeyArr	= {"description", "desc", "Desc"}; 	// Ensembl and NCBI

	private PrintWriter logFile=null;
	private boolean bSuccess=true;
	private String typeFile="";
	
	private Vector <File> seqFiles = new Vector <File>  ();
	private Vector <File> annoFiles = new Vector <File>  ();
	
	private TreeMap <String, Integer> chrMap = new TreeMap <String, Integer>  (); // Id, length
	private TreeMap <Character, Integer> cntBase = new TreeMap <Character, Integer> ();
	
	private String projDir ="", seqDir=null, annoDir = null;
	private int cntErr=0;
	
	protected Summary(String dirName) {
		this.projDir = dirName;
	}
	/*************************************************************/
	protected void runCheck(boolean bVerbose, boolean bConvert) {
		if (!isDir(projDir)) {
			prt("Not a directory: " + projDir);
			return;
		}
		this.bVerbose = bVerbose;
		if (bVerbose) {CNTPREFIX=10;}
		this.bConverted = bConvert;
		
		prt("");
		prt("------ Summary for " + projDir + " ------");
		createLog();
		if (bVerbose) prt("Verbose output ");
		if (bConverted) prt("Converted");
		prtConvertLog();
		
		if (bConverted) {
			setConvertDir(); 
		}
		else {
			setTopDir();
			if (seqDir==null) setNcbiDir();
			if (seqDir==null) setConvertDir();
		}
		if (seqDir==null) {
			prt("Cannot find FASTA or GFF files in project directory: " + projDir);
			return;
		}
		// print files
		prt("");
		if (seqDir.endsWith("/")) seqDir = seqDir.substring(0, seqDir.length()-1);
		prt("Sequence directory: "   + seqDir + "      " + FileDir.fileDate(seqDir));
		for (File f : seqFiles) prt("   " + f.getName());
		
		if (annoDir!=null) {
			if (annoDir.endsWith("/")) annoDir = annoDir.substring(0, annoDir.length()-1);
			prt("Annotation directory: " + annoDir);
			for (File f : annoFiles) prt("   " + f.getName());
		}
		
		// compute
		rFasta();		if (!bSuccess) return;
		
		rAnno();
		
		// finish
		if (!bConverted) {
			prt("                                           ");
			if (typeFile.isEmpty()) typeFile = "No hints";
			if (typeFile.startsWith(";")) typeFile = typeFile.substring(1);
			prt("Input type: " + typeFile);
		}
		if (cntErr>0) prt("Warnings: " + cntErr);
		
		prt("------ Finish summary for " + projDir + " ------");
		if (logFile!=null) logFile.close();
 	}
	/***********************************************************
	 * 
	 */
	private void rFasta() {
		prt("");
		prt("FASTA sequence file(s)");
		String fileName="";
	try {
		char [] base = {'A', 'C', 'G', 'T', 'N', 'a', 'c', 'g', 't', 'n'};
		for (char b : base) cntBase.put(b, 0);
		
		TreeMap <String, Integer> prefixMap = new TreeMap <String, Integer> ();
		int [] lenCutoff = {10000, 100000, 1000000, 10000000, 50000000, 100000000, 200000000, 0};
		int [] lenCnts = {0,0,0,0,0, 0, 0, 0, 0};
		final String NUM_PREFIX = "Number, X, Y, Roman";
		
		String idCol1="", line;
		boolean isChr=false, isScaf=false, isMt=false, isPt=false, isLowerCase=false;
		int cntAll=0, cntChr=0, cntChrScaf=0, cntScaf=0, cntMt=0, cntPt=0, cntUnk=0, cntN=0, cntLine=0, len=0;
		int cntUniPre=0;
		long totalLen = 0;
		String baseMsg="";
		
		Vector <String> headLines = new Vector <String> ();
		
		String lenline="";
		for (File file : seqFiles) {
			fileName = file.getName();
			String f = (seqDir.endsWith("/")) ? seqDir + fileName : seqDir + "/" + fileName;
			BufferedReader fhIn = Utils.openGZIP(f); 
			if (fhIn==null) return;
			len=0;
			
			while ((line = fhIn.readLine()) != null) {
				if (line.startsWith("!") || line.startsWith("#") || line.trim().isEmpty()) continue;
				
				if (line.startsWith(">")) {
					if (len>0) { // process last sequence
						totalLen += len;
						System.out.print("  #" + cntAll +"  process ID " + idCol1 + " " + totalLen + " bases...  \r");
						
						for (int i=0; i < lenCutoff.length; i++) {
							if (len<lenCutoff[i] || lenCutoff[i]==0) {
								lenCnts[i]++;
								break;
							}
						}
						chrMap.put(idCol1,len);
						if (cntAll<=CNTLEN) lenline += String.format(" %-12s %,11d\n", idCol1, len);
						len=0;
					}
					cntAll++;
					
					// eval prefix and line for type
					String linex = line.substring(1);
					String [] tok = linex.split("\\s+");
					idCol1 = tok[0].trim();
					
					boolean bUniPre;
					String preCol1 = getPrefix(idCol1);
					if (isChrNum(idCol1)) bUniPre = incMap(NUM_PREFIX, prefixMap);
					else 				  bUniPre = incMap(preCol1, prefixMap);
					if (bUniPre) cntUniPre++;
								
					isScaf=isChr=isMt=isPt=false;
					if (preCol1.length()==3 && preCol1.charAt(2)=='_') { // NCBI
						if (preCol1.equals("NC_"))      isChr=true;
						else if (preCol1.equals("NT_")) isScaf=true;
						else if (preCol1.equals("NW_")) isScaf=true;
					}
					else if (isChrNum(idCol1)) isChr=true; // Ensembl
					else {
						isChr = line.contains("chromosome") || line.contains("Chromosome"); // Ensembl
						isScaf = line.contains("scaffold") || line.contains("Scaffold");    // Ensembl
						if (isChr && isScaf) isScaf=false;
						else if (!isChr && !isScaf) { // Converted
							 if (preCol1.toLowerCase().equals(chrPrefix) || preCol1.equals(sChrPrefix)) isChr=true; 
							 else if (preCol1.equals(scafPrefix)) isScaf=true; 
						}
					}		
					isMt =   idCol1.toLowerCase().startsWith("mt") ||  // Ens uses MtDNA or MT or Mt
							 line.contains("mitochondrion") || line.contains("mitochondrial"); // NCBI
					isPt =   idCol1.toLowerCase().startsWith("pt") ||  // Ens uses MtDNA or MT or Mt
							 line.contains("plastid") || line.contains("chloroplast");  // NCBI
					
					// Counts of type
					if (isMt) {  // isChr true
						cntMt++;
						if (cntMt<=CNTHEAD || (bUniPre && cntUniPre<=CNTPREFIX))   headLines.add("   Mt:   " + line);
					}
					else if (isPt) { // isChr true
						cntPt++;
						if (cntPt<=CNTHEAD || (bUniPre && cntUniPre<=CNTPREFIX))   headLines.add("   Pt:   " + line);
					}
					else if (isChr) {
						cntChr++;
						if (cntChr<=CNTHEAD || (bUniPre && cntUniPre<=CNTPREFIX)) headLines.add("   Chr:  " + line);
					}	
					else if (isScaf) {
						cntScaf++;
						if (cntScaf<=CNTHEAD || (bUniPre && cntUniPre<=CNTPREFIX)) headLines.add("   Scaf: " + line);
					}
					else {
						cntUnk++;
						if (cntUnk<=CNTHEAD || (bUniPre && cntUnk<=CNTPREFIX)) headLines.add("   Unk:  " + line);
					}
				}
				else { // eval sequence
					len += line.length();
					
					if (bVerbose && baseMsg.isEmpty()) {
						if ((totalLen+len) >= MAXBASE) baseMsg = String.format(" for 1st %,d bases", totalLen);
					}
					if (bVerbose && baseMsg.isEmpty()) {
						char [] bases = line.toCharArray();
						for (char b : bases) {
							if (cntBase.containsKey(b)) cntBase.put(b, cntBase.get(b)+1); // counts
	    					else 						cntBase.put(b, 1);
						}
					}
					else if (!isLowerCase) {
						isLowerCase = line.contains("a") || line.contains("c") || line.contains("t") || line.contains("g");
						if (!isLowerCase) {
							if (line.contains("N")) cntN++;
							cntLine++;
						}
					}
				}
			}
			if (len>0) { // finish last one
				totalLen += len;
				for (int i=0; i < lenCutoff.length; i++) {
					if (len<lenCutoff[i] || lenCutoff[i]==0) {
						lenCnts[i]++;
						break;
					}
				}
				chrMap.put(idCol1,len);
				if (cntAll<=CNTLEN) lenline += String.format(" %-12s %,11d\n", idCol1, len);
			}
		}
		/////////////////////////////////////////
		prt("Example header lines:                                                         ");
		for (String l : headLines) prt(l); 
		prt("");
		
		prt("Count Totals:            ");
		prtNZ(6, cntChr, "Chromosomes");
		prtNZ(6, cntChrScaf, "Chromosome scaffolds");
		prtNZ(6, cntScaf, "Scaffolds");
		prtNZ(6, cntMt+cntPt, "Mt/Pt");
		prtNZ(6, cntUnk, "Unknown");
		if (cntAll!=cntChr) prt(6, cntAll, "Total sequences");
		prt("");
		prt("Count Prefixes:");
		if (prefixMap.containsKey("NC_") && prefixMap.get("NC_")>0) 
			typeFile = "NCBI chr NC_ prefix";
		else if (prefixMap.containsKey("NW_") && prefixMap.get("NW_")>0) 
			typeFile = "NCBI scaf NW_ prefix";
		else if (prefixMap.containsKey(NUM_PREFIX) && prefixMap.get(NUM_PREFIX)>0) 
			typeFile = "Ensembl chr '" + NUM_PREFIX + "'";
		
		Vector <Count> pVec = new Vector <Count> ();
		for (String key : prefixMap.keySet()) {
			Count p = new Count();
			p.word = key;
			p.count = prefixMap.get(key);
			pVec.add(p);
		}
		Collections.sort(pVec);
		
		int cnt=0;
		for (Count p : pVec) {
			if (p.count>1) prt(6, p.count, p.word);
			else if (p.count==1) {
				cnt++;
				if (cnt<5) prt(6, p.count, p.word);
				else if (cnt==5) prt("Suppressing further unique prefixes");
			}
		}
		if (cnt>=5) prtNZ(6, cnt, "Unique prefixes");
		
		prt("");
		prt("Counts of Length Ranges:");
		String outline="";
		int end=0;
		for (int i=0; i < lenCutoff.length; i++) if ( lenCnts[i]!=0) end=i;
		for (int i=0; i <= end; i++) {
			String xxx = lenCnts[i]==0 ? "" : String.format("%,d", lenCnts[i]);
			if (lenCutoff[i]==0) 
				outline += String.format("   %s>=%s",xxx,  Utilities.kMText(lenCutoff[i-1]));
			else 
				outline += String.format("   %s<%s",xxx,  Utilities.kMText(lenCutoff[i]));
		}
		if (!outline.isEmpty()) prt(outline);
		prt(String.format("   %,d Total length", totalLen));
		
		if (bVerbose) {
			prt("");
			int d = (chrMap.size()<CNTLEN) ? chrMap.size() : CNTLEN;
			prt("Lengths of first " + d + " sequences:");
			prt(lenline);
			
			prt("Base counts" + baseMsg);
			prt( String.format("   A %,11d  a %,11d", cntBase.get('A'), cntBase.get('a')) );
			prt( String.format("   T %,11d  t %,11d", cntBase.get('T'), cntBase.get('t')) );
			prt( String.format("   C %,11d  c %,11d", cntBase.get('C'), cntBase.get('c')) );
			prt( String.format("   G %,11d  g %,11d", cntBase.get('G'), cntBase.get('g')) );
			prt( String.format("   N %,11d  n %,11d", cntBase.get('N'), cntBase.get('n') ));
			String other=""; 
			for (char b : cntBase.keySet()) {
				boolean found = false;
				for (char x : base) if (x==b) {found=true; break;}
				if (!found) other += String.format("%c %,d  ", b, cntBase.get(b));
			}
			if (other!="") prt("   " + other);
			cntBase.clear();
		}
		else {
			if (!isLowerCase) {
				double p = ((double)cntN/(double)cntLine)*100.0;
				String pp = String.format("%.1f%s", p, "%");
				prt("");
				prt("Possible hard mask: No sequence line has lowercase actg ");
				prt("                    " + pp + " lines have at least one N");
			}
		}
	}
	catch (Exception e) { die(e, "Check file " + fileName); }
	}
	
	private boolean isChrNum(String col1) { // copied from ConvertEnsembl
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
	
	private String getPrefix(String tag) { 
		if (tag.contains("_")) {
			Pattern pat2 = Pattern.compile("([AN][A-Z])_(\\d*).*");	// NC_
			Matcher m = pat2.matcher(tag);
			if (m.matches()) return m.group(1) + "_";
		}
		
		Pattern pat1 = Pattern.compile("([a-zA-Z]+)(\\d+)([.]?).*"); // Scaf123 or Scaf123.3
		Matcher m = pat1.matcher(tag);
		if (m.matches()) return m.group(1);
		
		Pattern pat3 = Pattern.compile("([a-zA-Z]+)$"); // Alphabetic
		m = pat3.matcher(tag);
		if (m.matches()) return m.group(1);
		
		String [] x = tag.split("_"); // other, could be CTG123_
		if (x.length>0) return x[0]; 
		
		return tag;
	}
	
	/***********************************************************
	 * XXX Make sure there is ID and product or description
	 * Make sure that the Exon is linked to the first mRNA of the gene
	 */
	private void rAnno() {
		prt("                      ");
		if (annoFiles.size()==0) {
			prt("No GFF Annotation file(s)");
			return;
		}
		prt("GFF Annotation file(s)");
		String fileName="";
		
	try {
		int cntReadGenes=0, cntReadMRNAs=0, cntReadExons=0;
		int cntGene=0, cntExon=0, cntMRNA=0, cntmProdKey=0, cntgProdKey=0, cntgDescKey=0, cntProteinID=0;
		int errID=0, errParent=0, errChr=0, errLine=0;
		boolean isNCBI=false, isEns=false;
		
		TreeMap <String, Integer> typeMap = new TreeMap <String, Integer>  (); // column 3
		TreeMap <String, Integer> biotypeMap = new TreeMap <String, Integer>  (); // column 8
		TreeMap <String, Integer> attrMap = new TreeMap <String, Integer>  (); // column 8
		
		if (bVerbose) prt("Example lines:");
		for (File file : annoFiles) {
			fileName = file.getName();
			String f = (seqDir.endsWith("/")) ? (annoDir + fileName) : (annoDir + "/" + fileName);
			BufferedReader fhIn = Utils.openGZIP(f);
			if (fhIn==null) return;
			
			String line, geneID=null, mrnaID=null, cdsID=null;
			
			while ((line = fhIn.readLine()) != null) {
				if (line.startsWith("#")) {
					if (line.contains("processor NCBI")) {
						if (!typeFile.isEmpty()) typeFile += "; ";
						typeFile += "NCBI GFF header";
						isNCBI = true;
					}
					continue;
				}
				if (line.startsWith("!") || line.startsWith("#") ||line.isEmpty()) continue;
				
				String [] tok = line.split("\\t");
				if (tok.length!=9) {
					badLine(errLine++, "Wrong number columns (" + tok.length + "): ", line); if (!bSuccess) return;
					continue;
				}
				
				String chrCol = tok[0];  // chromosome, scaffold, ...
				String type   = tok[2];
				incMap(type, typeMap);		// count types
				
				boolean isGene = type.equals(geneType);
				boolean isMRNA = type.equals(mrnaType);
				boolean isExon = type.equals(exonType);
				boolean isCDS = type.equals(cdsType);
				if (!isGene && !isMRNA && !isExon && !isCDS) continue;
				
				String [] attrs = tok[8].split(";");
				if (attrs.length==0) {
					badLine(errLine++, "No attributes", line); if (!bSuccess) return;
					continue;
				}
				if (!chrMap.containsKey(chrCol)) {
					badLine(errChr++, "No '" + chrCol + "' sequence name in FASTA: ", line); if (!bSuccess) return;
					continue;
				}
				
				if (isGene) { 
					cntReadGenes++;
					if (cntReadGenes%5000==0)	System.err.print("   Read " + cntReadGenes + " genes...\r");
					
					geneID=mrnaID=cdsID=null;
					
					String ebiotype = getVal(eBiotypeAttrKey, attrs); // biotype=
					String nbiotype = getVal(nBiotypeAttrKey, attrs); // gene-biotype=
					if (!ebiotype.isEmpty()) {
						isEns=true;
						incMap(ebiotype, biotypeMap);
						if (!ebiotype.contentEquals(proteinCoding)) continue; // not protein-coding
					}
					else if (!nbiotype.isEmpty()) {
						isNCBI=true;
						incMap(nbiotype, biotypeMap);
						if (!nbiotype.contentEquals(proteinCoding)) continue; // not protein-coding
					}
				
					geneID = getVal(idAttrKey, attrs);
					if (geneID.contentEquals("")) {
						geneID = null;
						badLine(errID++, "No ID keyword: ", line); if (!bSuccess) return;
						continue;
					}
					
					cntGene++;
					if (cntGene==1 && bVerbose) prt(line);
					
					for (String a : attrs) {
						String [] tok2 = a.split("=");
						if (tok2.length==2) incMap(tok2[0], attrMap);
					}
					
					for (String a : descAttrKeyArr) {
						String v = getVal(a, attrs);
						if (!v.isEmpty()) {
							cntgDescKey++;
							break;
						}
					}
					String product = getVal(prodAttrKey, attrs);
					if (!product.isEmpty()) cntgProdKey++;
				}
				else if (isMRNA) {
					cntReadMRNAs++;
					
					if (geneID==null) 					continue;
					if (mrnaID!=null) 					continue;
					
					String parent = getVal(parentAttrKey, attrs);
					if (parent.contentEquals("")) {
						badLine(errParent++, "No parent keyword: ", line); if (!bSuccess) return;
						continue;
					}
					if (!geneID.startsWith("gene-")) 
						parent = parent.replace("gene-", "");
					if (!parent.contentEquals(geneID)) 	{
						geneID=mrnaID=null; // pseudogene, etc
						continue;
					}
					
					mrnaID = getVal(idAttrKey, attrs);
					if (mrnaID.contentEquals("")) {
						mrnaID=null;
						badLine(errID++, "No ID keyword: ", line); if (!bSuccess) return;
						continue;
					}
					cntMRNA++;
					if (cntMRNA==1  && bVerbose) prt(line);
					
					String product = getVal(prodAttrKey, attrs);
					if (!product.isEmpty()) cntmProdKey++;
				}
				else if (isExon) {
					cntReadExons++;
					if (mrnaID==null) 					continue;
					
					String parent = getVal(parentAttrKey, attrs);
					if (parent.contentEquals("")) {
						badLine(errParent++, "No parent keyword: ", line); if (!bSuccess) return;
						continue;
					}
					if (!parent.contentEquals(mrnaID)) 	continue; 
					
					cntExon++;
					if (cntExon==1  && bVerbose) prt(line);
				}
				else if (isCDS) {
					if (mrnaID==null) 					continue;
					if (cdsID!=null) 					continue;
					
					String parent = getVal(parentAttrKey, attrs);
					if (parent.contentEquals("") || !parent.contentEquals(mrnaID)) continue;
					
					cdsID = "yes"; 
					String pid = getVal(proteinID, attrs); 
					if (!pid.isEmpty()) cntProteinID++;
				}
				if (!bSuccess) return;
			}
			fhIn.close();
		}// Loop through files
		//////////////////////////////////////////////
		if (bVerbose) prt("                                                               ");
		prt("Summary:                                                     ");
		String pc = (biotypeMap.size()>0) ? "   (use protein_coding only)" : "";
		prt(7, cntGene, String.format("Genes from %,d %s", cntReadGenes, pc));
		pc = (cntProteinID>0) ? String.format("   (%,d has protein_id)", cntProteinID) : "";
		prt(7, cntMRNA, String.format("mRNAs from %,d %s", cntReadMRNAs, pc));
		prt(7, cntExon, String.format("Exons from %,d ", cntReadExons));
		
		if (bVerbose) {
			prt("");
			prt("Types: " + typeMap.size());
			String oline="";
			int cnt=0;
			for (String key : typeMap.keySet()) {
				if (typeMap.get(key)>1) {
					oline += String.format("%,7d %-25s ",typeMap.get(key), key);
					cnt++;
					if (cnt>2) {
						prt(oline);
						cnt=0;
						oline="";
					}
				}
			}
			if (!oline.isEmpty()) prt(oline);
			if (biotypeMap.size()>0) {// Converted do not have biotype
				prt("");
				prt("Gene Attributes biotype: " + biotypeMap.size());
				oline="";
				cnt=0;
				for (String key : biotypeMap.keySet()) {
					if (biotypeMap.get(key)>1) {
						oline += String.format("%,7d %-25s ",biotypeMap.get(key), key);
						cnt++;
						if (cnt>2) {
							prt(oline);
							cnt=0;
							oline="";
						}
					}
				}
				if (!oline.isEmpty()) prt(oline);
			}
		} // end Verbose
	
		prt("");
		prt("Gene Attribute: " + attrMap.size()); // print because if Load into SyMAP, this is the Query columns
		String oline="";
		int cnt=0;
		for (String a : attrMap.keySet()) {
			oline += String.format(" %,7d %-25s", attrMap.get(a), a);
			cnt++;
			if (cnt==2) {
				prt(oline);
				cnt=0;
				oline="";
			}
		}
		if (!oline.isEmpty()) prt(oline);
		
		if (!bConverted) {
			if (cntmProdKey>0) {
				prt("mRNA Attribute:");
				prt(7, cntmProdKey, "product");
			}		
			
			if (!typeFile.isEmpty() && (isNCBI || isEns)) 
				typeFile += "\n            "; // separate from FASTA remarks
			
			if (isNCBI) {
				typeFile += "NCBI 'gene_biotype' keyword";
				
				if (cntmProdKey>0) typeFile += "; NCBI mRNA 'product' keyword";
				if (cntgProdKey>0) typeFile += "; gene 'product' keyword";
				if (cntgDescKey>0) typeFile += "; gene 'description' keyword";
				if (cntmProdKey==0 && cntgDescKey==0) prt("No mRNA product or gene description");
			}
			if (isEns)  {
				typeFile += "Ensembl 'biotype' keyword";
				
				if (cntgDescKey>0) typeFile += "; gene 'description' keyword";
				else prt("No gene description");
			}
		}
	}
	catch (Exception e) { die(e, "Checking GFF file " + fileName); }
	}
	private String getVal(String key, String [] attrs) {
		for (String s : attrs) {
			String [] x = s.split("=");
			if (x.length==2 && x[0].equals(key)) return x[1];
		}
		return "";
	}
	
	private void badLine(int cnt, String msg, String line) {
		cntErr++;
		String s = (line.length()>100) ? line.substring(100) + "..." : line;
		if (cnt<3) 	prt("+++" + msg + "\n   " + s);
		if (cntErr>20) {
			die("Too many bad lines: " + cntErr);
			bSuccess=false;
		}
	}
	/*************************************************
	 * Are the files directly in the projDir
	 */
	private void setTopDir() {
	try {
		seqDir = projDir;
		annoDir = projDir;
		getFiles();
	}
	catch (Exception e) { die(e, "Checking project directory for files" + projDir); }
	}
	
	/**************************************************************************
	  * Are the files in projDir/ncbi_dataset/data/<dir>  
	  */
	 private void setNcbiDir() {
		try {
			String ncbi_dataset="ncbi_dataset"; 
			String ncbi_data=	"/data"; 
			String subDir=null;
			
			String dsDirName=null;
			File dir = new File(projDir);
			
			File[]  files = dir.listFiles();
			boolean isDS=false;
			for (File f : files) {
				if (f.isDirectory()) {
					if (f.getName().contentEquals(ncbi_dataset)) {
						isDS = true;
						break;
					}
				}
			}
			if (!isDS) return;
			
			/////////////////////////////////
			dsDirName = FileDir.fileNormalizePath(projDir, ncbi_dataset + ncbi_data);
					
			dir = new File(dsDirName);
			if (!dir.isDirectory()) {
				die("ncbi_dataset directory is incorrect " + dsDirName);
				return;
			}	
			files = dir.listFiles();
			for (File f : files) {
				if (f.isDirectory()) {
					subDir = f.getName();
					break;
				}
			}
			if (subDir==null) {
				die(dsDirName + " missing sub-directory");
				return;
			}
			seqDir = FileDir.fileNormalizePath(dsDirName, subDir);
			annoDir = seqDir;
			
			getFiles();
		}
		catch (Exception e) { die(e, "Checking " + projDir); }
	}
	private void setConvertDir() {
		seqDir  = FileDir.fileNormalizePath(projDir, seqSubDir);
		annoDir = FileDir.fileNormalizePath(projDir, annoSubDir);
		getFiles();
	}
	/**************************************************************************
	 * Are the files in /sequence and /annotation 
	 */
	private void getFiles() {
	try {
		File sdf = new File(seqDir);
		if (sdf.exists() && sdf.isDirectory()) {
			for (File f2 : sdf.listFiles()) {
				String name = f2.getAbsolutePath();
				for (String suf : fastaFile) { 
					if (!f2.isFile() || f2.isHidden()) continue; 
					
					if (name.endsWith(suf) || name.endsWith(suf+".gz")) {
						seqFiles.add(f2);
						break;
					}
				}
			}
		}
		if (seqFiles.size()==0) {
			seqDir=null;
			return;
		}
		sdf = new File(annoDir);
		if (sdf.exists() && sdf.isDirectory()) {
			for (File f2 : sdf.listFiles()) {
				if (!f2.isFile() || f2.isHidden()) continue; 
				
				String name = f2.getAbsolutePath();
				for (String suf : Constants.gffFile) { 
					if (name.endsWith(suf) || name.endsWith(suf+".gz")) {
						annoFiles.add(f2);
						break;
					}
				}
			}
		}
		if (annoFiles.size()==0) annoDir=null;
	}
	catch (Exception e) { die(e, "Getting sequence and annotation files from: " + projDir); }
	}
	
	//////////////////////////////////////////////////////////
	 private void createLog() {
		 if (!projDir.endsWith("/")) projDir += "/";
		logFileName = projDir + logFileName;
		prt("Log file to  " + logFileName);	
		
		try {
			logFile = new PrintWriter(new FileOutputStream(logFileName, false)); 
		}
		catch (Exception e) {die(e, "Cannot open " + logFileName); logFile=null;}
	 }
	 /*******************************************************
	  * Print out whether convert has been run
	  */
	 private void prtConvertLog() {
		try {	
			File sdf = new File(projDir);
			if (sdf.exists() && sdf.isDirectory()) {
				for (File f2 : sdf.listFiles()) {
					String name = f2.getName();
					if (name.startsWith("xConvert")) {
						prt("Convert log: " + name + "      " +  FileDir.fileDate(f2.getAbsolutePath()));
					}
				}
			}
		}
		catch (Exception e) {die(e, "Cannot search for convert log"); }
	 }
	 private boolean isDir(String dirName) {
			File dir = new File(dirName);
			if (!dir.isDirectory()) return false;
			else return true;
	 }
	 private boolean die(Exception e, String msg) {
		ErrorReport.print(e, msg);
		bSuccess = false;
		return false;
	 }
	 private boolean die(String msg) {
		System.err.println("Fatal error -- " + msg);
		bSuccess = false;
		return false;
	 }
	 private void prt(String msg) {
		System.out.println(msg);
		if (logFile!=null) logFile.println(msg);
	 }
	 private void prt(int sz, int num, String msg) {
		 String f = " %," + sz + "d %s";
		 String x = String.format(f, num, msg);
		 System.out.println(x);
		 if (logFile!=null) logFile.println(x);
	 }
	 private void prtNZ(int sz, int num, String msg) {
		 if (num==0) return;
		 String f = " %," + sz + "d %s";
		 String x = String.format(f, num, msg);
		 System.out.println(x);
		 if (logFile!=null) logFile.println(x);
	 }
	
	 // Count and sort map
	 private boolean incMap(String key, TreeMap <String, Integer> map) {
		 if (map.containsKey(key)) {
			 map.put(key, map.get(key)+1);
			 return false;
		 }
		 else {
			 map.put(key, 1);
			 return true;
		 }
	 } 
	 private class Count implements Comparable <Count>{
		String word="";
		int count=0;
		
		public int compareTo(Count a){
			return a.count - count;
		}
	}
}
