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
 */
public class Summary {
	private final String chrPrefix="Chr";
	private final String sChrPrefix="C";
	private final String scafPrefix="s";
	private final String geneType = "gene";
	private final String mrnaType = "mRNA";
	private final String exonType = "exon";
	
	private final String idAttrKey 		= "ID"; 			// Gene and mRNA
	private final String parentAttrKey 	= "Parent"; 		 // mRNA and Exon
	private final String nBiotypeAttrKey = "gene_biotype";   // NCBI Gene
	private final String eBiotypeAttrKey = "biotype"; 		 // Ensemble Gene
	private final String biotypeAttr 	= "protein_coding";  // biotype=protein_coding
	
	private final String prodAttrKey 	= "product"; 		// NCBI
	private final String descAttrKey	= "description"; 	// Ensembl
	private final String desc2AttrKey	= "desc"; 	        // converted Ensembl v557
	private final String desc3AttrKey	= "Desc"; 	        // converted Ensembl
	
	private String logFileName = "xSummary.log";
	private PrintWriter logFile=null;
	private boolean bSuccess=true, bVerbose=false;
	private String typeFile="";
	
	private Vector <File> seqFiles = new Vector <File>  ();
	private Vector <File> annoFiles = new Vector <File>  ();
	
	private TreeMap <String, Integer> chrMap = new TreeMap <String, Integer>  (); // Id, length
	
	private String projDir ="", seqDir=null, annoDir = null;
	private int cntErr=0;
	
	protected Summary(String dirName) {
		this.projDir = dirName;
	}
	protected void runCheck(boolean bVerbose, boolean bConverted) {
		if (!isDir(projDir)) {
			prt("Not a directory: " + projDir);
			return;
		}
		this.bVerbose = bVerbose;
		
		prt("");
		prt("------ Summary for " + projDir + " ------");
		createLog();
		if (bVerbose) prt("Verbose output");
		prtConvertLog();
		
		if (bConverted) setSeqDir(); // user checks after Convert
		else {
			setTopDir();
			if (seqDir==null) setNcbiDir();
			if (seqDir==null) setSeqDir();
		}
		if (seqDir==null) {
			prt("Cannot find FASTA or GFF files in project directory: " + projDir);
			return;
		}
		// print files
		prt("");
		if (seqDir.endsWith("/")) seqDir = seqDir.substring(0, seqDir.length()-1);
		prt("Sequence directory: "   + seqDir + "      " + Utilities.fileDate(seqDir));
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
		prt("                                           ");
		if (typeFile.isEmpty()) typeFile = "No hints";
		if (typeFile.startsWith(";")) typeFile = typeFile.substring(1);
		prt("Input type: " + typeFile);
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
		TreeMap <String, Integer> prefixMap = new TreeMap <String, Integer> ();
		int [] lenCuts = {10000, 100000, 1000000, 10000000, 50000000, 100000000, 200000000, 0};
		int [] lenCnts = {0,0,0,0,0, 0, 0, 0, 0};
		final String NUM_PREFIX = "Number, X, Y, Roman";
		
		String idCol1="", line, sline="";
		boolean isChr=false, isScaf=false, isMt=false, isPt=false;
		int cntAll=0, cntChr=0, cntChrScaf=0, cntScaf=0, cntMt=0, cntPt=0, cntOther=0, len=0;
		long totalLen = 0;
		
		prt("Example header lines:");
		for (File file : seqFiles) {
			fileName = file.getName();
			String f = (seqDir.endsWith("/")) ? seqDir + fileName : seqDir + "/" + fileName;
			BufferedReader fhIn = backend.Utils.openGZIP(f); 
			if (fhIn==null) return;
			len=0;
			
			while ((line = fhIn.readLine()) != null) {
				if (line.startsWith("!!") || line.startsWith("#") || line.trim().isEmpty()) continue;
				
				if (line.startsWith(">")) {
					if (len>0) { // process last sequence
						totalLen += len;
						for (int i=0; i < lenCuts.length; i++) {
							if (len<lenCuts[i] || lenCuts[i]==0) {
								lenCnts[i]++;
								break;
							}
						}
						chrMap.put(idCol1,len);
						len=0;
					}
					sline = line;
					
					String line1 = line.substring(1);
					String [] tok = line1.split("\\s+");
					idCol1 = tok[0].trim();
					
					String preCol1 = getPrefix(idCol1);
					if (isChrNum(idCol1)) incMap(NUM_PREFIX, prefixMap);
					else                  incMap(preCol1, prefixMap);
				
					isChr  = isChrNum(idCol1) || 			          // often Ensembl
							 preCol1.equals("NC_") ||                 // NCBI
							 preCol1.toLowerCase().equals("chr") ||   // already converted or demo
							 preCol1.equals(sChrPrefix) || 			  // already converted w/scaffolds (or was chromosome Cn)
							 line.contains("chromosome");             // Ensembl
					
					isScaf = preCol1.equals("NW_") || preCol1.equals("NT_") || 	// NCBI
							 preCol1.equals(scafPrefix) || 			  			// already converted w/scaffolds
							 line.contains("scaffold"); 		    			// Ensembl 
					
					isMt =   idCol1.toLowerCase().startsWith("mt") ||  // Ens uses MtDNA or MT or Mt
							 line.contains("mitochondrion") || line.contains("mitochondrial"); // NCBI
					isPt =   idCol1.toLowerCase().startsWith("pt") ||  // Ens uses MtDNA or MT or Mt
							 line.contains("plastid") || line.contains("chloroplast");  // NCBI
					
					if (isMt) {  // isChr true
						cntMt++;
						if (cntMt==1 || (bVerbose && cntMt<6))           prt("   Mt:   " + sline);
					}
					else if (isPt) { // isChr true
						cntPt++;
						if (cntPt==1 || (bVerbose && cntPt<6))           prt("   Pt:   " + sline);
					}
					else if (isChr) {
						cntChr++;
						if (cntChr==1 || (bVerbose && cntChr<30))        prt("   Chr:  " + sline);
					}	
					else if (isScaf) {
						cntScaf++;
						if (cntScaf==1 || (bVerbose && cntScaf<6))       prt("   Scaf: " + sline);
					}
					else {
						cntOther++;
						if (cntOther==1 || (bVerbose && cntOther<6))     prt("   Unk:  " + sline);
					}
					cntAll++;
					if (cntAll%100 == 0) System.out.print("  " + cntAll + " sequences...\r");
				}
				else len += line.length();
			}
			if (len>0) { // finish last one
				totalLen += len;
				for (int i=0; i < lenCuts.length; i++) {
					if (len<lenCuts[i] || lenCuts[i]==0) {
						lenCnts[i]++;
						break;
					}
				}
				chrMap.put(idCol1,len);
			}
		}
		/////////////////////////////////////////
		prt("                                                           ");
		prt("Count Totals:            ");
		prtNZ(6, cntChr, "Chromosomes");
		prtNZ(6, cntChrScaf, "Chromosome scaffolds");
		prtNZ(6, cntScaf, "Scaffolds");
		prtNZ(6, cntMt+cntPt, "Mt/Pt");
		prtNZ(6, cntOther, "Unknown");
		if (cntAll!=cntChr) prt(6, cntAll, "Total sequences");
		prt("");
		prt("Count Prefixes:");
		if (prefixMap.containsKey("NC_") && prefixMap.get("NC_")>0) 
			typeFile = "NCBI chr NC_ prefix";
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
		
		int cnt1=0;
		for (Count p : pVec) {
			if (p.count>1) prt(6, p.count, p.word);
			else if (p.count==1) {
				cnt1++;
				if (cnt1<5) prt(6, p.count, p.word);
				else if (cnt1==5) prt("Surpressing further unique prefixes");
			}
		}
		if (cnt1>=5) prtNZ(6, cnt1, "Unique prefixes");
		prt("");
		prt("Count Length<=Cutoff:");
		for (int i=0; i < lenCuts.length; i++) {
			if (lenCuts[i]==0) prtNZ(6, lenCnts[i], String.format(">  %,d", lenCuts[i-1]));
			else               prtNZ(6, lenCnts[i], String.format("<= %,d", lenCuts[i]));
		}
		prt(String.format("   %,d Total length", totalLen));
		
		if (bVerbose) {
			prt("First 10 Lengths:");
			int cnt=0;
			for (String id : chrMap.keySet()) {
				prt(String.format("%10s %,d", id, chrMap.get(id)));
				cnt++;
				if (cnt >= 10) break;
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
		if (Utils.parseHasPrefix(tag, chrPrefix)) return chrPrefix;
		if (Utils.parseHasPrefix(tag, sChrPrefix)) return sChrPrefix;
		if (Utils.parseHasPrefix(tag, scafPrefix)) return scafPrefix;
		
		Pattern pat1 = Pattern.compile("([a-zA-Z]+)(\\d+)([.]?).*"); // Scaf123 or Scaf123.3
		Matcher m = pat1.matcher(tag);
		if (m.matches()) return m.group(1);
		
		Pattern pat2 = Pattern.compile("([a-zA-Z]+)_(.*)");	// NC_
		m = pat2.matcher(tag);
		if (m.matches()) return m.group(1) + "_";
		
		Pattern pat3 = Pattern.compile("([a-zA-Z]+)$"); // Alphabetic
		m = pat3.matcher(tag);
		if (m.matches()) return m.group(1);
		
		String [] x = tag.split("_"); // other, could be CTG123_
		if (x.length>0) return x[0];  // always has one
		
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
		int cntGene=0, cntExon=0, cntMRNA=0, cntProdKey=0, cntDescKey=0;
		int errID=0, errParent=0, errChr=0, errLine=0;
		boolean isNCBI=false, isEns=false;
		
		TreeMap <String, Integer> typeMap = new TreeMap <String, Integer>  ();
		TreeMap <String, Integer> biotypeMap = new TreeMap <String, Integer>  ();
		
		if (bVerbose) prt("Example lines:");
		for (File file : annoFiles) {
			fileName = file.getName();
			String f = (seqDir.endsWith("/")) ? (annoDir + fileName) : (annoDir + "/" + fileName);
			BufferedReader fhIn = backend.Utils.openGZIP(f);
			if (fhIn==null) return;
			
			String line, geneID=null, mrnaID=null;
			
			while ((line = fhIn.readLine()) != null) {
				if (line.startsWith("#")) {
					if (line.contains("processor NCBI")) {
						if (!typeFile.isEmpty()) typeFile += "; ";
						typeFile += "NCBI GFF header";
					}
					continue;
				}
				if (line.startsWith("!!") || line.isEmpty()) continue;
				
				String [] tok = line.split("\\t");
				if (tok.length!=9) {
					badLine(errLine++, "Wrong number columns (" + tok.length + "): ", line); if (!bSuccess) return;
					continue;
				}
				
				String chrCol = tok[0];  // chromosome, scaffold, ...
				String type   = tok[2];
				String [] attrs = tok[8].split(";");
				if (attrs.length==0) {
					badLine(errLine++, "No attributes", line); if (!bSuccess) return;
					continue;
				}
				
				incMap(type, typeMap);		// count types
				
				boolean isGene = type.equals(geneType);
				boolean isMRNA = type.equals(mrnaType);
				boolean isExon = type.equals(exonType);
				
				if (isGene) { 
					cntReadGenes++;
					if (cntReadGenes%5000==0)	System.err.print("   Read " + cntReadGenes + " genes...\r");
					
					geneID=mrnaID=null;
					
					String ebiotype = getVal(eBiotypeAttrKey, attrs);
					String nbiotype = getVal(nBiotypeAttrKey, attrs);
					if (!ebiotype.isEmpty()) {
						isEns=true;
						incMap(ebiotype, biotypeMap);
						if (!ebiotype.contentEquals(biotypeAttr)) continue; // not protein-coding
					}
					else if (!nbiotype.isEmpty()) {
						isNCBI=true;
						incMap(nbiotype, biotypeMap);
						if (!nbiotype.contentEquals(biotypeAttr)) continue; // not protein-coding
					}
					
				}
				else if (isMRNA) cntReadMRNAs++;
				else if (isExon) cntReadExons++;
				else continue;
				
				if (!chrMap.containsKey(chrCol)) {
					badLine(errChr++, "No '" + chrCol + "' sequence name in FASTA: ", line); if (!bSuccess) return;
					continue;
				}
				
				if (isGene) { 
					String id = getVal(idAttrKey, attrs);
					if (id.contentEquals("")) {
						badLine(errID++, "No ID keyword: ", line); if (!bSuccess) return;
						continue;
					}
					String desc = getVal(descAttrKey, attrs);
					if (!desc.isEmpty()) cntDescKey++;
					else {
						desc = getVal(desc2AttrKey, attrs);
						if (!desc.isEmpty()) cntDescKey++;
						else {
							desc = getVal(desc3AttrKey, attrs);
							if (!desc.isEmpty()) cntDescKey++;
						}
					}
					
					geneID=id;
					cntGene++;
					if (cntGene==1 && bVerbose) prt(line);
				}
				else if (isMRNA) {
					if (geneID==null) 					continue;
					if (mrnaID!=null) 					continue;
					
					String parent = getVal(parentAttrKey, attrs);
					if (parent.contentEquals("")) {
						badLine(errParent++, "No parent keyword: ", line); if (!bSuccess) return;
						continue;
					}
					if (!parent.contentEquals(geneID)) 	{
						geneID=mrnaID=null; // pseudogene, etc
						continue;
					}
					
					String id = getVal(idAttrKey, attrs);
					if (id.contentEquals("")) {
						badLine(errID++, "No ID keyword: ", line); if (!bSuccess) return;
						continue;
					}
					String product = getVal(prodAttrKey, attrs);
					if (!product.isEmpty()) cntProdKey++;
					
					mrnaID = id;
					cntMRNA++;
					if (cntMRNA==1  && bVerbose) prt(line);
				}
				else if (isExon) {
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
				if (!bSuccess) return;
			}
			fhIn.close();
		}// Loop through files
		//////////////////////////////////////////////
		if (bVerbose) prt("                                                               ");
		prt("Summary:                                                     ");
		prt(String.format("   Use Genes %,d from %,d    (use protein_coding only)          ", cntGene, cntReadGenes));
		prt(String.format("   Use mRNAs %,d from %,d ", cntMRNA, cntReadMRNAs));
		prt(String.format("   Use Exons %,d from %,d ", cntExon, cntReadExons));
		
		if (bVerbose) {
			prt("");
			prt("Types:");
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
			prt("");
			prt("Gene Attributes biotype:");
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
			prt("");
		}
		if (!typeFile.isEmpty()) typeFile += "\n            ";
		String d = "";
		if (cntProdKey>0) {
			prt("mRNA Attribute:");
			prtNZ(7, cntProdKey, "product");
			typeFile += "NCBI mRNA 'product' keyword";
			d="; ";
		}
		else if (cntDescKey>0) {
			prt("Gene Attribute:");
			prtNZ(7, cntDescKey, "description");
			typeFile += "Ensembl gene 'description' keyword";
			d="; ";
		}
		else if (cntProdKey==0 && cntDescKey==0) prt("+++ No attribute 'product' or 'description'!!!!");
		
		if (isNCBI) typeFile += d+ "NCBI 'gene_biotype' keyword";
		if (isEns)  typeFile += d+ "Ensembl 'biotype' keyword";
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
		getSeqFiles();
		if (seqFiles.size()==0) {
			seqDir=null;
			return;
		}
		annoDir = projDir;
		getAnnoFiles();
		if (annoFiles.size()==0) annoDir=null;
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
			dsDirName = Utilities.fileNormalizePath(projDir, ncbi_dataset + ncbi_data);
					
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
			seqDir = Utilities.fileNormalizePath(dsDirName, subDir);
			
			getSeqFiles();
			if (seqFiles.size()==0) {
				seqDir=null;
				return;
			}
			annoDir = seqDir;
			getAnnoFiles();
			if (annoFiles.size()==0) annoDir=null;
		}
		catch (Exception e) { die(e, "Checking " + projDir); }
	}
	/**************************************************************************
	 * Are the files in /sequence and /annotation 
	 */
	private void setSeqDir() {
		seqDir = Utilities.fileNormalizePath(projDir, backend.Constants.seqSeqDataDir);
		getSeqFiles();
		if (seqFiles.size()==0) {
			seqDir=null;
			return;
		}
		annoDir = Utilities.fileNormalizePath(projDir, backend.Constants.seqAnnoDataDir);
		getAnnoFiles();
		if (annoFiles.size()==0) annoDir=null;
	}
	
	////////////////////////////////////////////////
	protected String getSeqDir() { // prtLengths only
		try {
			seqDir = Utilities.fileNormalizePath(projDir, backend.Constants.seqSeqDataDir);
			if (!Utilities.pathExists(seqDir)) {
				Utilities.showWarningMessage("Path for sequence files does not exist: " + seqDir);
				return null;
			}	
			return seqDir;
		}
		catch (Exception e) {ErrorReport.print(e, "Cannot get sequence file"); return null;}
	}
	protected Vector <File> getSeqFiles() { // prtLengths and local
		try {	
			File sdf = new File(seqDir);
			if (sdf.exists() && sdf.isDirectory()) {
				for (File f2 : sdf.listFiles()) {
					String name = f2.getAbsolutePath();
					for (String suf : Constants.fastaFile) { 
						if (name.endsWith(suf) || name.endsWith(suf+".gz")) {
							seqFiles.add(f2);
							break;
						}
					}
				}
			}
			else {
				return null;
			}
		}
		catch (Exception e) {die(e, "Cannot get sequence files"); return null;}
		
		return seqFiles;
	}
	private void getAnnoFiles() { 
		try {	
			File sdf = new File(annoDir);
			if (sdf.exists() && sdf.isDirectory()) {
				for (File f2 : sdf.listFiles()) {
					String name = f2.getAbsolutePath();
					for (String suf : Constants.gffFile) { 
						if (name.endsWith(suf) || name.endsWith(suf+".gz")) {
							annoFiles.add(f2);
							break;
						}
					}
				}
			}
		}
		catch (Exception e) {die(e, "Cannot get annotation files"); }
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
						prt("Convert log: " + name + "      " +  Utilities.fileDate(f2.getAbsolutePath()));
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
	 private void incMap(String key, TreeMap <String, Integer> map) {
		 if (map.containsKey(key)) map.put(key, map.get(key)+1);
		 else map.put(key, 1);
	 } 
	 private class Count implements Comparable <Count>{
		String word="";
		int count=0;
		
		public int compareTo(Count a){
			return a.count - count;
		}
	}
}
