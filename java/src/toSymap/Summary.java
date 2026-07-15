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
 *  NCBI has mRNA "products" whereas Ensembl uses gene "description"
 *  
 *  Verbose: prints out length of all chromosomes instead of 1st 3
 *           counts up to 2G bases instead of 500k
 *           outputs one of each GFF salient type
 */
public class Summary {
	private final String seqSubDir =     Constants.seqSeqDataDir;
	private final String annoSubDir =    Constants.seqAnnoDataDir;
	private final String [] fastaFile =  Constants.fastaFile;
	private String logFileName = "xSummary.log";
	
	private boolean VERBOSE=false, CONVERT=false; 	// args from xToSymap
	
	private final int CNTHEAD=1, CNTLEN=3; 		// #header, #unique prefix, #sequence len;
	private final int CNTPREFIX=5; 				// # of header lines to print with unique prefix of this type; plus, # to print out
	private final int maxBaseVer=Integer.MAX_VALUE;	  			// 2**31 in Java 2,147,483,648 verbose
	private final int maxBaseNotV= 500000000;					// not verbose
	private final String strMaxBaseVer = "2.14G", strMaxBaseNotV="500M";
	
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
	
	private final String prodAttrKey 	= "product"; 		// NCBI
	private final String [] descAttrKeyArr	= {"description", "desc", "Desc"}; 	// Ensembl and NCBI

	private PrintWriter sumLogFile=null;
	private boolean bSuccess=true;
	private String typeFileMsg="", outFileMsg="";
	
	private Vector <File> seqFiles = new Vector <File>  ();
	private Vector <File> annoFiles = new Vector <File>  ();
	
	private TreeMap <String, Integer> seqLenMap = new TreeMap <String, Integer>  (); // Id, length; used in both FASTA and GFF
	
	
	private String projDir ="", seqDir=null, annoDir = null;
	private int cntErr=0, cntOutSeq=0;
	private boolean isNCBI=false, isEns=false;
	
	/**************************************************************/
	protected Summary(String dirName, boolean bVerbose, boolean bConvert) {
		this.projDir = dirName;
		this.CONVERT = bConvert;
		this.VERBOSE = bVerbose;
		
		new Init();			if (!bSuccess) return;
		
		new FASTA();		if (!bSuccess) return;
		
		new GFF();
		
		// finish
		if (!CONVERT) {
			prt("                                           ");
			if (typeFileMsg.isEmpty()) 			typeFileMsg = "File type unknown";
			if (typeFileMsg.startsWith("; ")) 	typeFileMsg = typeFileMsg.substring(2);
			else if (typeFileMsg.startsWith(";")) typeFileMsg = typeFileMsg.substring(1);

			prt("Input type: " + typeFileMsg + outFileMsg);
		}
		if (cntErr>0) prt("Warnings: " + cntErr);
		
		prt("------ Finish summary for " + projDir + " ------");
		if (sumLogFile!=null) sumLogFile.close();
 	}
	/*************************************************************/
	 private boolean fail(Exception e, String msg) {
		ErrorReport.print(e, msg);	
		bSuccess = false;
		return false;
	 }
	 private boolean fail(String msg) {
		System.err.println("Fatal error -- " + msg);
		bSuccess = false;
		return false;
	 }
	 private void prt(String msg) {
		System.out.println(msg);
		if (sumLogFile!=null) sumLogFile.println(msg);
	 }
	 private void prt(int sz, int num, String msg) {
		 String f = " %," + sz + "d %s";
		 String x = String.format(f, num, msg);
		 System.out.println(x);
		 if (sumLogFile!=null) sumLogFile.println(x);
	 }
	 private void prtNZ(int sz, int num, String msg) {
		 if (num==0) return;
		 String f = " %," + sz + "d %s";
		 String x = String.format(f, num, msg);
		 System.out.println(x);
		 if (sumLogFile!=null) sumLogFile.println(x);
	 }
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
	 /****************************************************************************/
	 private class FASTA {
		private TreeMap <String, Integer> prefixMap = new TreeMap <String, Integer> ();
		private int [] lenCutoff = {10000, 100000, 1000000, 10000000, 50000000, 100000000, 200000000, 
				300000000, 400000000, 500000000,0};
		private int [] lenCnts = {0,0,0,0,0, 0, 0, 0, 0, 0, 0, 0};
		private final String NUM_PREFIX = "Number, X, Y, Roman";
		
		// Counts used for both header lines and sequence length output
		private long sumChrLen=0, sumScafLen=0, sumMtLen=0, sumPtLen=0, sumUnkLen=0;
		private int cntAll=0, cntChr=0, cntScaf=0, cntMt=0, cntPt=0, cntUnk=0;
		private int cntUniPre=0, cntLine=0;
		private long totalLen = 0;
		private String lenline="";
		
		private TreeMap <Character, Integer> cntBase = new TreeMap <Character, Integer> ();
		private long totalBase=0;
		private char [] base = {'A', 'C', 'G', 'T', 'N', 'a', 'c', 'g', 't', 'n'};
		private Vector <String> headLines = new Vector <String> ();
			
		/******************/
		private FASTA() {
		try {
			// inits for al files
			lenline = String.format(" %11s  %s \n", "Length  ", "SeqId");
			for (char b : base) cntBase.put(b, 0);
			
			prt("");
			prt("### FASTA sequence file(s)");
			
			for (File file : seqFiles) {
				String fileName = file.getName();
				String f = (seqDir.endsWith("/")) ? seqDir + fileName : seqDir + "/" + fileName;
				BufferedReader fhIn = Utils.openGZIP(f); 
				if (fhIn==null) return;
				
				sumFile(fhIn);
			}
			sumOut();
		}
		catch (Exception e) { fail(e, "Check FASTA"); }
		}
		/***********************************************************/
		private void sumFile(BufferedReader fhIn) {
		try {
			int seqLen=0; 
			String line;
			String idCol1="";
			boolean isChr=false, isScaf=false, isMt=false, isPt=false, isUnk=false;
			long maxBaseCnt = (VERBOSE) ? maxBaseVer : maxBaseNotV;
			
			while ((line = fhIn.readLine()) != null) {
				if (line.startsWith("!") || line.startsWith("#") || line.trim().isEmpty()) continue;
				
				if (line.startsWith(">")) {
					if (seqLen>0) { // process last sequence
						totalLen += seqLen;
						System.out.print("  #" + cntAll +"  " + idCol1 + " " + totalLen + " bases...  \r");
						
						for (int i=0; i < lenCutoff.length; i++) {
							if (seqLen<lenCutoff[i] || lenCutoff[i]==0) {
								lenCnts[i]++;
								break;
							}
						}
						seqLenMap.put(idCol1,seqLen);
						
						boolean bAdd=(cntAll<=CNTLEN);
						if (VERBOSE) {
							bAdd = isChr || (isScaf && cntScaf<CNTLEN) ||  (isScaf && cntScaf<CNTLEN)
								|| (isMt && cntMt<CNTLEN) ||(isPt && cntPt<CNTLEN) || (isUnk && cntUnk<CNTLEN);
						}	
						if (bAdd) {lenline += String.format(" %,11d  %s \n", seqLen, idCol1); cntLine++;}
						seqLen=0;
					}
					cntAll++;
					
					// eval prefix and line for type
					String linex = line.substring(1);
					String [] tok = linex.split("\\s+");
					idCol1 = tok[0].trim();
					
					boolean bUniPre;
					String preCol1 = getPrefix(idCol1);
					if (isChrNum(idCol1)) bUniPre = incMap(NUM_PREFIX, prefixMap); // Number, X, Y , Roman Numeral
					else 				  bUniPre = incMap(preCol1, prefixMap);    // anything else
					if (bUniPre) cntUniPre++;
								
					isScaf=isChr=isMt=isPt=isUnk=false;
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
					isUnk = !isChr && !isScaf && !isMt && !isPt;
					
					// Counts of types and show headers for the first CNTPREFIX
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
					int len = line.length();
					seqLen += len;
					if (isMt) 			sumMtLen += len; // isChr
					else if (isPt) 		sumPtLen += len;
					else if (isChr) 	sumChrLen += len;
					else if (isScaf) 	sumScafLen += len;
					else 				sumUnkLen += len;
					
					if (totalBase < maxBaseCnt) {
						char [] bases = line.toCharArray();
						for (char b : bases) {			
							if (cntBase.containsKey(b)) cntBase.put(b, cntBase.get(b)+1); 
	    					else 						cntBase.put(b, 1);
							
							if (totalBase > maxBaseCnt) break;
							totalBase++; // for all files, sequence
						}
					}
				}
			} // end loop through file
			
			if (seqLen>0) { // finish last one
				totalLen += seqLen;
				for (int i=0; i < lenCutoff.length; i++) {
					if (seqLen<lenCutoff[i] || lenCutoff[i]==0) {
						lenCnts[i]++;
						break;
					}
				}
				seqLenMap.put(idCol1,seqLen);
				boolean bAdd=(cntAll<=CNTLEN);
				if (VERBOSE) {
					bAdd = isChr || (isScaf && cntScaf<CNTLEN) ||  (isScaf && cntScaf<CNTLEN)
						|| (isMt && cntMt<CNTLEN) ||(isPt && cntPt<CNTLEN) || (isUnk && cntUnk<CNTLEN);
				}	
				if (bAdd) {lenline += String.format(" %,11d  %s \n", seqLen, idCol1); cntLine++;}
			}
		}catch (Exception e) { fail(e, "Summarize FASTA "); }
		}
		/************************************/
		private void sumOut() {
		try {
			if (prefixMap.containsKey("NC_") && prefixMap.get("NC_")>0) 
				typeFileMsg += "; NCBI NC_ chromosome prefix";
			else if (prefixMap.containsKey("NW_") && prefixMap.get("NW_")>0) 
				typeFileMsg += "; NCBI NW_ scaffold prefix";
			else if (prefixMap.containsKey(NUM_PREFIX) && prefixMap.get(NUM_PREFIX)>0) 
				typeFileMsg += "; Ensembl chr '" + NUM_PREFIX + "'";
			
			prt("Example header lines                                                         ");
			for (String l : headLines) prt(l); 
					
			///////// Prefix
			prt("");
			prt("Count prefixes");
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
					if (cnt<CNTPREFIX) prt(6, p.count, p.word);
					else if (cnt==CNTPREFIX) prt("Suppressing further unique prefixes");
				}
			}
			if (cnt>=5) prtNZ(6, cnt, "Unique prefixes");
			
		/////// Len of sequences
			prt("");
			if (cntAll==cntLine) prt("Lengths of all sequences");
			else if (VERBOSE)    prt("Lengths of " + cntLine + " sequence (includes all chromosomes, plus a few others)");
			else                 prt("Lengths of first " + cntLine + " sequences");
			prt(lenline);
					
			///////
			//prt(""); lenline has \n at end
			prt("#Seqs<length && >=previous");
			
			int endL=0, endR=0;
			for (int i=0; i < lenCutoff.length && endL==0; i++) if (lenCnts[i]!=0) endL=i;
			for (int i=lenCutoff.length-1; i>0 && endR==0; i--) if (lenCnts[i]!=0) endR=i;
			if (endR==0) endR = lenCutoff.length-1;
			
			String outline = (endL>0) ? "   0<" + Utilities.kMText(lenCutoff[endL-1]) : "";
			
			for (int i=endL; i <= endR; i++) {
				String xxx = lenCnts[i]==0 ? "0" : String.format("%,d", lenCnts[i]);
				if (lenCutoff[i]==0) 
					outline += String.format("   %s>=%s",xxx,  Utilities.kMText(lenCutoff[i-1]));
				else 
					outline += String.format("   %s<%s",xxx,  Utilities.kMText(lenCutoff[i]));
			}
			if (!outline.isEmpty()) prt(outline);
			
		/////
			prt("");
			if (VERBOSE) {
				if (totalLen < maxBaseVer) 	prt("Bases count for all sequence");
				else 						prt("Base count for 1st " + strMaxBaseVer + " bases");
			}
			else  {
				if (totalLen < maxBaseNotV) prt("Bases count for all sequence");
				else 						prt("Base count for 1st " + strMaxBaseNotV + " bases");
			}
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
			
		///////
			prt("");
			int x = (cntAll<1000) ? 4 : 6;  
			prt("Summary   ");
			prtNZ(x, cntChr, 		String.format("%-11s  %,13d", "Chromosomes", sumChrLen));
			prtNZ(x, cntScaf, 		String.format("%-11s  %,13d", "Scaffolds",   sumScafLen));
			prtNZ(x, cntMt+cntPt, 	String.format("%-11s  %,13d", "Mt/Pt",      (sumMtLen+sumPtLen)));
			prtNZ(x, cntUnk, 		String.format("%-11s  %,13d", "Unknown",     sumUnkLen));
			
			if (cntAll!=cntChr && cntUnk!=cntAll) prt(x, cntAll, String.format("%-11s  %,13d", "Total", totalLen));	
			if (cntChr==0) outFileMsg = "\n*** No chromosome sequences to output; see options";
		}
		catch (Exception e) { fail(e, "Sum Out FASTA "); }
		}
		/***********************************/
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
	 
		private class Count implements Comparable <Count>{
			String word="";
			int count=0;
			
			public int compareTo(Count a){
				return a.count - count;
			}
		}
	 }
	 /***********************************************************
	  * XXX Make sure there is ID and product or description
	  * Make sure that the Exon is linked to the first mRNA of the gene
	  */
	 private class GFF {
		private String warning="";
		private int cntReadGenes=0, cntReadMRNAs=0, cntReadExons=0, cntReadCDS=0, cntNoMrna=0;
		private int cntGene=0, cntExon=0, cntCDS=0, cntMRNA=0, cntmProdKey=0, cntgProdKey=0, cntgDescKey=0;
		private int errID=0, errParent=0, errChr=0, errLine=0;
		
		private TreeMap <String, Integer> typeMap = new TreeMap <String, Integer>  (); // column 3
		private TreeMap <String, Integer> biotypeMap = new TreeMap <String, Integer>  (); // column 8
		private TreeMap <String, Integer> attrMap = new TreeMap <String, Integer>  (); // column 8
		
		/****************************/
		private GFF() {
		try {
			prt("                      ");
			if (annoFiles.size()==0) {
				prt("No GFF Annotation file(s)");
				return;
			}
			prt("### GFF Annotation file(s)");
			String fileName="";
			
			if (VERBOSE) prt("Example lines");
			for (File file : annoFiles) {
				fileName = file.getName();
				String f = (seqDir.endsWith("/")) ? (annoDir + fileName) : (annoDir + "/" + fileName);
				BufferedReader fhIn = Utils.openGZIP(f);
				if (fhIn==null) return;
				
				sumFile(fhIn);
				
				fhIn.close();
			}
			
			sumOut();
		}
		catch (Exception e) { fail(e, "Checking GFF file "); }
		}
		/*****************************************************************/
		private void sumFile(BufferedReader fhIn ) {
		try {
			String line, geneID=null, mrnaID=null;
			
			while ((line = fhIn.readLine()) != null) {
				if (line.startsWith("!") || line.startsWith("#") || line.isEmpty()) continue;
				
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
				boolean isCDS  = type.equals(cdsType);
				if (!isGene && !isMRNA && !isExon && !isCDS) continue;
				
				String [] attrs = tok[8].split(";");
				if (attrs.length==0) {
					badLine(errLine++, "No attributes", line); if (!bSuccess) return;
					continue;
				}
				if (!seqLenMap.containsKey(chrCol)) {
					badLine(errChr++, "No '" + chrCol + "' sequence name in FASTA: ", line); if (!bSuccess) return;
					continue;
				}
				
				if (isGene) { 
					if (geneID!=null && mrnaID==null) {
						if (cntNoMrna==1) warning += "No mRNA for " + geneID;
						cntNoMrna++;
					}
					cntReadGenes++;
					if (cntReadGenes%5000==0) System.err.print("   Read " + cntReadGenes + " genes...\r");
					
					geneID=mrnaID=null;
					
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
					// protein coding
					cntGene++;
					if (cntGene==1 && VERBOSE) prt(line);
					
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
					
					if (geneID==null) continue;
					if (mrnaID!=null) continue;
					
					String parent = getVal(parentAttrKey, attrs);
					if (parent.contentEquals("")) {
						badLine(errParent++, "No parent keyword: ", line); if (!bSuccess) return;
						continue;
					}
					if (!geneID.startsWith("gene-")) parent = parent.replace("gene-", "");
					
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
					if (cntMRNA==1  && VERBOSE) prt(line);
					
					String product = getVal(prodAttrKey, attrs);
					if (!product.isEmpty()) cntmProdKey++;
				}
				else if (isExon) {
					cntReadExons++;
					if (mrnaID==null) continue;
					
					String parent = getVal(parentAttrKey, attrs);
					if (parent.contentEquals("")) {
						badLine(errParent++, "No parent keyword: ", line); if (!bSuccess) return;
						continue;
					}
					if (!parent.contentEquals(mrnaID)) continue; 
					
					cntExon++;
					if (cntExon==1  && VERBOSE) prt(line);
				}
				else if (isCDS) {
					cntReadCDS++;
					if (mrnaID==null) continue;
					
					String parent = getVal(parentAttrKey, attrs);
					if (parent.contentEquals("") || !parent.contentEquals(mrnaID)) continue;
					
					cntCDS++;
					if (cntCDS==1  && VERBOSE) prt(line);	
				}
				if (!bSuccess) return;
			}// end loop through file
		}
		catch (Exception e) { fail(e, "Summarize GFF file "); }
		}
		/*****************************************************************/
		private void sumOut() {
		try {
			// After ### GFF Annotation Files and verbose line output
			///
			if (VERBOSE) {
				prt("                                                                 ");
				prt("Types                                                                    ");
				String oline="";
				int cnt=0;
				for (String key : typeMap.keySet()) {
					String k = (key.length()>=21) ? key.substring(0,21) + "..." : key;
					oline += String.format("%,9d %-25s ",typeMap.get(key), k);
					cnt++;
					if (cnt>2) {
						prt(oline);
						cnt=0;
						oline="";
					}
				}
				if (!oline.isEmpty()) prt(oline);
				if (biotypeMap.size()>0) {// Converted do not have biotype
					prt("");
					prt("Gene attribute biotype=values ");
					oline="";
					cnt=0;
					for (String key : biotypeMap.keySet()) {
						oline += String.format("%,9d %-25s ",biotypeMap.get(key), key);
						cnt++;
						if (cnt>2) {
							prt(oline);
							cnt=0;
							oline="";
						}
					}
					if (!oline.isEmpty()) prt(oline);
				}
				prt("");
			}
			// print attributes because if Load into SyMAP, this is the Query columns
			prt("Gene attribute keywords                                                    "); 
			String oline="";
			int cnt=0;
			int v = (VERBOSE) ? 2 : 1;
			for (String a : attrMap.keySet()) {
				oline += String.format(" %,9d %-25s", attrMap.get(a), a);
				cnt++;
				if (cnt>v) {
					prt(oline);
					cnt=0;
					oline="";
				}
			}
			if (!oline.isEmpty()) prt(oline);
			
			if (!CONVERT) {
				if (cntmProdKey>0) {
					prt("");
					prt(String.format(" %,9d mRNA's with attribute keyword 'product'", cntmProdKey));
				}		
				
				if (!typeFileMsg.isEmpty() && (isNCBI || isEns)) 
					typeFileMsg += "\n            "; // separate from FASTA remarks
				
				String rem="";
				if (isNCBI) {
					rem = "NCBI 'gene_biotype' keyword; ";
					
					if (cntmProdKey>0) rem += "NCBI mRNA 'product' keyword; ";
					if (cntgProdKey>0) rem += "gene 'product' keyword; ";
					if (cntgDescKey>0) rem += "gene 'description' keyword; ";
					if (cntmProdKey==0 && cntgDescKey==0) prt("No mRNA product or gene description; ");
				}
				if (isEns)  {
					rem = "Ensembl 'biotype' keyword; ";
					
					if (cntgDescKey>0) rem += "gene 'description' keyword; ";
					else prt("No gene description");
				}
				if (!rem.isEmpty()) {
					rem = rem.substring(0, rem.length()-2);
					typeFileMsg += rem;
				}
			}
			if (VERBOSE && cntNoMrna>0) 
				prt(String.format("\nNo mRNA for protein coding genes: %,d\n e.g. %s", cntNoMrna, warning));
			
			prt("");
			prt("Summary                                                             ");
			if (!CONVERT) {
				prt(8, cntGene, String.format("Gene from %,9d (protein_coding)", cntReadGenes));
				prt(8, cntMRNA, String.format("mRNA from %,9d (1st mRNA)", cntReadMRNAs));
			}
			else {
				prt(8, cntGene, String.format("Gene from %,9d", cntReadGenes));
				prt(8, cntMRNA, String.format("mRNA from %,9d", cntReadMRNAs));
			}
			
			prt(8, cntExon, String.format("Exon from %,9d ", cntReadExons));
			prt(8, cntCDS,  String.format("CDS  from %,9d ", cntReadCDS));
			
		}
		catch (Exception e) { fail(e, "Sum Out GFF file "); }
		}
		/****************************/
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
				fail("Too many bad lines: " + cntErr);
				bSuccess=false;
			}
		}
	 } // end GFF class
	 /**********************************************************/
	 private class Init {
		 private Init() {
		 try {	
			 if (!isDir(projDir)) {
				prt("Not a directory: " + projDir);
				return;
			}
			
			prt("");
			prt("------ Summary for " + projDir + " on " + util.Utilities.getDateOnly() + " ------");
			createLog(); // prints args
			
			if (CONVERT) {
					setConvertDir(); 
			}
			else {
				setTopDir();
				if (seqDir==null) setNcbiDir();
				if (seqDir==null) setConvertDir();
			}
			if (seqDir==null) {
				prt("Cannot find FASTA or GFF files in project directory: " + projDir);
				bSuccess=false;
				return;
			}
			// print files
			prt("");
			if (seqDir.endsWith("/")) seqDir = seqDir.substring(0, seqDir.length()-1);
			prt("Sequence directory: "   + seqDir );
			for (File f : seqFiles) {
				prt(String.format("   %-60s         File date %s", f.getName(), FileDir.fileDate(f.getAbsolutePath())));
			}
			
			if (annoDir!=null) {
				if (annoDir.endsWith("/")) annoDir = annoDir.substring(0, annoDir.length()-1);
				prt("Annotation directory: " + annoDir);
				
				String header=null;
				for (File f : annoFiles) {
					prt(String.format("   %-60s         File date %s", f.getName(), FileDir.fileDate(f.getAbsolutePath())));
					if (VERBOSE && header==null) header = read1stGFFheader(f); 
				}
				if (header!=null && !header.isEmpty()) {
					header = header.substring(0, header.length()-1);
					prt(header);
				}
			}
			
		 } catch (Exception e) {fail(e, "Fail in Summary Init");}
		} 
		 /************************************************************
		 // NCBI 
		 //   #!genome-build TAIR10.1
		 // Ensembl: 
		 //   #!genome-build The Arabidopsis Information Resource TAIR10
		 //   #!genome-date 2008-04
		 //   #!genebuild-last-updated 2010-09
		 **/
		private String read1stGFFheader(File file) {
		try {
			String header="";
			
			String fileName = file.getName();
			String f = (seqDir.endsWith("/")) ? (annoDir + fileName) : (annoDir + "/" + fileName);
			BufferedReader fhIn = Utils.openGZIP(f);
			if (fhIn==null) return null;
			
			String line;
			while ((line = fhIn.readLine()) != null) {
				if (line.startsWith("#")) {
					if (line.contains("NCBI") && !typeFileMsg.contains("NCBI")) {//Ensembl is not in header
						if (!typeFileMsg.isEmpty()) typeFileMsg += "; ";
						typeFileMsg += "NCBI GFF header";
						isNCBI = true;
					}
					else if (line.startsWith("   #!genome-build ")) header += line + "\n";
					else if (line.startsWith("   #!genome-date "))  header += line + "\n";
					else if (line.startsWith("   #!genebuild-last-updated ")) header += line + "\n";
					continue;
				}
				else if (line.startsWith("!") || line.isEmpty()) continue;
				else break;
			}
			fhIn.close();
			if (!header.isEmpty()) header = "Header lines from 1st GFF file \n" + header;
			return header;
		} 
		catch (Exception e) {fail(e, "Fail reading 1st header"); return null;}
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
		catch (Exception e) { fail(e, "Checking project directory for files" + projDir); }
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
					fail("ncbi_dataset directory is incorrect " + dsDirName);
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
					fail(dsDirName + " missing sub-directory");
					return;
				}
				seqDir = FileDir.fileNormalizePath(dsDirName, subDir);
				annoDir = seqDir;
				
				getFiles();
			}
			catch (Exception e) { fail(e, "Checking " + projDir); }
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
		catch (Exception e) { fail(e, "Getting sequence and annotation files from: " + projDir); }
	}
			
	//////////////////////////////////////////////////////////
	 private void createLog() {
		 if (!projDir.endsWith("/")) projDir += "/";
		logFileName = projDir + logFileName;
		prt("Summary file: " + logFileName);	
		
		try {
			sumLogFile = new PrintWriter(new FileOutputStream(logFileName, false)); 
			sumLogFile.println("### xToSymap Summary " + symap.Globals.VERSION + " on " + util.Utilities.getDateOnly());
			if (VERBOSE) prt("  Verbose output ");
			if (CONVERT) prt("  Converted");
		}
		catch (Exception e) {fail(e, "Cannot open " + logFileName); sumLogFile=null;}
	 }
	
	 private boolean isDir(String dirName) {
			File dir = new File(dirName);
			if (!dir.isDirectory()) return false;
			else return true;
	 }
  }// end Init class
}
