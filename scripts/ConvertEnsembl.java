/*************************************************************
 * ConvertEnsembl: Ensembl genome files formatted for SyMAP
 * See https://csoderlund.github.io/SyMAP/convert
 * 
 * Written by CAS Oct 2019
 * This assumes input of:
 * 1. ftp://ftp.ensemblgenomes.org/pub/plants/release-45/fasta/<species>/dna/<species>.dna_rm.toplevel.fa.gz
 *    Note: the _rm indicates its been masked, this probably works with unmasked (.dna.toplevel)
 *    It only extracts 'chromosomes'
 * 2. ftp://ftp.ensemblgenomes.org/pub/plants/release-45/gff3/<species>/<species>.chr.gff3.gz
 * Note: either hard-masked (rm) where repeats are replaced with Ns, or soft-masked (sm) where repeats are in lower-case text.
 * 
 * Update by CAS 14-Aug-2022 
 * 		write protein genes, first mRNA and exons of first mRNA to anno.gff
 * 		add computing gaps and output (Note Ensembl has hard-masked sequence, so that option is not necessary)
 * 		add scaffold option and improve summary output
 * To determine chromosome vs scaffold, the following rule is used:
 * 		if the ">id" id is numeric, X, Y, Mt, Pt then its chromosome; else its scaffold.
 * CAS 11-Oct-2022 add -u option
 * CAS 25-Jan-2023 read multiple fasta and gff files
 */
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.TreeMap;
import java.util.Vector;
import java.util.zip.GZIPInputStream;

public class ConvertEnsembl {
	// args
	private boolean VERBOSE = false; 
	private boolean INCLUDESCAF = false;
	private boolean INCLUDEMtPt = false; // not option
	private boolean USENAMES = false;
	
	private final int maxName=5;
	private final int gapLen=30000;     // Print to gap.gff if #N's is greater than this
	private final int scafMaxNum=20;	// print first 20
	private final int scafMaxLen=10000;	// All scaffold are printed to fasta file, but only summarized if >scafMax
	
	private String chrPrefix="Chr";
	private String scafPrefix="s";
		
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
		
	// input
	private String projDir = null; // command line input
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
	private int nChr=0, nScaf=0, nMtPt=0, nOther=0, nGap=0, cntChrGeneAll=0, cntScafGeneAll=0;
	private long chrLen=0, scafLen=0, mtptLen=0, otherLen=0;
	
	/************************************************************************/
	public static void main(String[] args) {
		new ConvertEnsembl(args);
	}
	private ConvertEnsembl(String[] args) {
		System.out.println(">>>> ConvertEnsembl <<<<");
		checkArgs(args);
		checkFiles();
		
		System.out.println("Processing " + inFaFile);
		rwFasta();
		System.out.println("Processing " + inGffFile);
		rwAnno();
		printSummary();
		prt("ConvertEnsembl completed");
	}
	/**
	 * * Fasta file example:
	 * >1 dna_sm:chromosome chromosome:IRGSP-1.0:1:1:43270923:1 REF
	 * >Mt dna_sm:chromosome chromosome:IRGSP-1.0:Mt:1:490520:1 REF
	 * >Syng_TIGR_043 dna_sm:scaffold scaffold:IRGSP-1.0:Syng_TIGR_043:1:4236:1 REF
	 */
	private void rwFasta() {
		try {
			TreeMap <Character, Integer> cntBase = new TreeMap <Character, Integer> ();
			char [] base = {'A', 'C', 'G', 'T', 'N', 'a', 'c', 'g', 't', 'n'};
			for (char b : base) cntBase.put(b, 0);
			
		
			PrintWriter fhOut = new PrintWriter(new FileOutputStream(seqDir+outFaFile, false)); 
			fhOut.println("### Written by SyMAP ConvertEnsembl");
			PrintWriter ghOut = new PrintWriter(new FileOutputStream(annoDir + outGapFile, false));
			ghOut.println("### Written by SyMAP ConvertEnsembl");
			
			String line="";
			int len=0;
			
			String prtName="", idCol1="";
			boolean bPrt=false, isChr=false, isScaf=false, isMtPt=false;
			int baseLoc=0, gapStart=0, gapCnt=1;
			
			for (String file : inFaFile) {
				BufferedReader fhIn = openGZIP(file);
				
				while ((line = fhIn.readLine()) != null) {
	    			if (line.startsWith(">")) {
	    				if (len>0) {
	    					printTrace(isChr, isScaf, isMtPt, len, idCol1, prtName);
	    					len=0;
	    				}
	    				bPrt=false;
	    				String line1 = line.substring(1);
	    				String [] tok = line1.split("\\s+");
	    				idCol1 = tok[0].trim();
	    				
	    				isMtPt = idCol1.equalsIgnoreCase("Mt") || idCol1.equalsIgnoreCase("Pt"); 
	    				isChr  = isChr(idCol1) || isMtPt; 
	    				isScaf = (!isChr) ? true : false;
	    				
	    				if (isChr && !isMtPt) {
	    					nChr++;
	    					prtName = chrPrefix + padNum(tok[0]); // chr use number except X,Y,Mt,Pt
	    					cntChrGene.put(idCol1, 0);
	    					id2chr.put(idCol1, prtName);
	    					chr2id.put(prtName, idCol1);
	    					
	    					fhOut.println(">" + prtName);
	    					bPrt=true;
	    				}
	    				else if (isMtPt && INCLUDEMtPt) { // just like for numeric chromosome
	    					nMtPt++;
	    					prtName = chrPrefix + tok[0];
	    					cntChrGene.put(idCol1, 0);
	    					id2chr.put(idCol1, prtName);
	    					chr2id.put(prtName, idCol1);
	    					
	    					fhOut.println(">" + prtName + "  " + idCol1);
	    					bPrt=true;
	    				}
	    				else if (isScaf && INCLUDESCAF) {
	    					nScaf++;
	    					if (USENAMES && idCol1.length()<=maxName) prtName = idCol1;
	    					else prtName = scafPrefix + padNum(nScaf+""); // scaf use name
	    					
		    				id2scaf.put(idCol1, prtName);
		    				cntScafGene.put(idCol1, 0);
		    				gapStart=1; gapCnt=0; baseLoc=0;
		    				
	    					bPrt=true;
	    					fhOut.println(">" + prtName+ "  " + idCol1);
	    				}
	    				else {
	    					prtName="Ignore";
	    					if (isScaf) 		nScaf++;
	    					else if (isMtPt) 	nMtPt++;
	    					else 				nOther++;
	    				}
	    			}
	    			//////////////////////////////////////////////////////////////////
	    			else {
	    				String aline = line.trim();
	    				if (bPrt) {
		    				char [] bases = aline.toCharArray();
		    				
		    				for (int i =0 ; i<bases.length; i++) {
		    					char b = bases[i];
		
		    					if (cntBase.containsKey(b)) cntBase.put(b, cntBase.get(b)+1);// CAS512 add counts
		    					else 						cntBase.put(b, 1);
		    					
		    					baseLoc++;
		    					if (b=='N' || b=='n') { // CAS512 eval for gaps; ensembl has >30k lower-case n's
		    						if (gapStart==0) 	gapStart = baseLoc;
		    						else 				gapCnt++;
		    					}
		    					else if (gapStart>0) {						// gaps 
		    						if (gapCnt>gapLen) {
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
				fhIn.close();
			}
			if (len>0) printTrace(isChr, isScaf, isMtPt, len, idCol1, prtName);
    		fhOut.close(); ghOut.close();
    		System.err.print("                                            \r");
    		prt("Finish writing " + outFaFile + "                          ");
    		
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
		
			prt(String.format("Gaps >= %d: %d (using N and n)", gapLen, nGap));
		}
		catch (Exception e) {die(e, "rwFasta");}
	}
	private boolean isChr(String col1) {
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
	// prtName=Ignore if not being printed
	private void printTrace(boolean isChr, boolean isScaf, boolean isMtPt, int len, String id, String prtname) {
		if (isChr && !isMtPt) {
			chrLen+=len;
			prt(String.format("%-7s %-15s %,11d", prtname, id, len));
		}
		else if (isMtPt) {
			mtptLen+=len;
			if (INCLUDEMtPt || VERBOSE) prt(String.format("%-7s %-15s %,11d", prtname, id, len));
		}
		else if (isScaf) {
			scafLen+=len;
			if (len<scafMaxLen) cntScafSmall++;
			
			if (INCLUDESCAF) {
				if (nScaf<=scafMaxNum || VERBOSE) 	prt(String.format("%-7s %-15s %,11d", prtname, id, len));
				else if (nScaf==scafMaxNum+1) 		prt("Suppressing further scaffold outputs");
				else if (nScaf%100==0) 				System.err.print(String.format("     Scaffold #%,d Len=%,d ...", nScaf, len) + "\r");
			}
			else {
				if (VERBOSE) 						prt(String.format("%-7s %-15s %,11d", prtname, id, len));
				else if (nScaf%100==0) 				System.err.print(String.format("     Scaffold #%,d Len=%,d ...", nScaf, len) + "\r");
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
			if (inGffFile==null) return; // CAS513
			
			
			PrintWriter fhOut = new PrintWriter(new FileOutputStream(annoDir + outGffFile, false));
			fhOut.println("### Written by SyMAP ConvertEnsembl");
			
			int cntReadGenes=0, cntReadExon=0, cntReadMRNA=0;
			String line="", type="";
			String geneID=null, mrnaID=null;
			
			for (String file : inGffFile) {
				BufferedReader fhIn = openGZIP(file);
			
				while ((line = fhIn.readLine()) != null) {
					if (line.startsWith("#")) continue;
					
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
					String idCol1 =   tok[0];  // chromosome, scaffold, ...
					if (id2chr.containsKey(idCol1))  { 
						prtName = id2chr.get(idCol1);
						isChr=true;
					}
					else if (id2scaf.containsKey(idCol1)) {
						prtName = id2scaf.get(idCol1);
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
							cntChrGene.put(idCol1, cntChrGene.get(idCol1)+1);
						}
						else {
							cntScafGeneAll++;
							cntScafGene.put(idCol1, cntScafGene.get(idCol1)+1);	
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
			attr += ";Desc=" + desc;
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
	/*****************************************************************
	 * Summary
	 */
	private void printSummary() {
		System.out.println("                                         ");
		
		System.out.println(">>Sequences ");
		String nw = "-- not written to file";
		System.out.format("  %,6d %-11s of %,15d total length\n", nChr, "Chromosomes", chrLen);
		if (nMtPt>0) {
			String x = (INCLUDEMtPt) ? "" : nw;
			System.out.format("  %,6d %-11s of %,15d total length %s\n", nMtPt, "Mt/Pt", mtptLen, x);
		}
		if (nScaf>0) {
			String x = (INCLUDESCAF) ? "" : nw;
			System.out.format("  %,6d %-11s of %,15d total length (%,d<%,dbp) %s\n", nScaf, "Scaffolds", scafLen, cntScafSmall, scafMaxLen, x);
		}
		if (nOther>0)
			System.out.format("  %,6d %-11s of %,15d total length %s\n", nOther, "Other", otherLen, nw);
		
		if (inGffFile==null) return;
		System.out.println("                                         ");
		System.out.println(">>All Type (col 3)                       ");
		for (String key : allTypeCnt.keySet())  {
			String x = (key.equals(geneType) || key.equals(mrnaType) || key.equals(exonType)) ? "*" : "";
			System.out.format("   %-22s %,8d %s\n", key, allTypeCnt.get(key), x);
		}
		System.out.println(">>All Gene Source (col 2)");
		for (String key :allGeneSrcCnt.keySet()) {
			System.out.format("   %-22s %,8d *\n", key, allGeneSrcCnt.get(key));
		}
		System.out.println(">>All gene biotype=                       ");
		for (String key : allGeneBioTypeCnt.keySet())  {
			String x = (key.equals(biotypeAttr)) ? "*" : "";
			System.out.format("   %-22s %,8d %s\n", key, allGeneBioTypeCnt.get(key), x);
		}
		if (cntNoDesc>0) {
			System.out.println(">>Description");
			System.out.format("   %-20s %,8d\n", "None", cntNoDesc);
		}
		System.out.println(">>Written to file ");
		System.out.format("   %-20s %,8d\n", "Genes ", cntGene);
		System.out.format("   %-20s %,8d\n", "mRNA ", cntMRNA);
		System.out.format("   %-20s %,8d\n", "Exons ", cntExon);
		
		prt(String.format(">>Chromosome gene count %s ", String.format("%,d", cntChrGeneAll)));
		for (String prt : chr2id.keySet()) {
			String id =   chr2id.get(prt); // sorted on prtName
			prt(String.format("   %-10s %-20s %,8d", prt, id, cntChrGene.get(id)));
		}
		if (INCLUDESCAF) {
			prt(String.format(">>Scaffold gene count %s  (>=0)        ", String.format("(%,d)", cntScafGeneAll)));
			for (String key : cntScafGene.keySet()) 
				if (cntScafGene.get(key)>0) // sorted on idCol1, which is not scaffold order
					System.out.format("   %-7s %-15s %,8d\n", id2scaf.get(key), key, cntScafGene.get(key));
			System.out.format("   %s %,8d\n", "Genes not included", cntGeneNotOnSeq);
		}
		else System.out.format("   %s %,8d\n", "Genes not on Chromosome", cntGeneNotOnSeq);
	}
	/************************************************************
	 * File stuff
	 */
	private void checkFiles() {
		try {
			System.out.println("Project directory: " + projDir);
			
			File dir = new File(projDir);
			if (!dir.isDirectory()) 
				die("The argument must be a directory. " + projDir + " is not a directory.");
			
			File[] files = dir.listFiles();
			for (File f : files) {
				if (f.isFile()) { // CAS512 allow file not be have .gz
			       String fname = f.getName();
			     
			       if (fname.endsWith(".fa.gz") || fname.endsWith(".fa")) 		inFaFile.add(projDir + "/" + fname);
			       else if (fname.endsWith(".gff3.gz") || fname.endsWith(".gff3"))	inGffFile.add(projDir + "/" +fname); 	
			       else if (fname.endsWith(".gff.gz") || fname.endsWith(".gff")) inGffFile.add(projDir + "/" +fname); 			
				} 
			}
		}
		catch (Exception e) {die(e, "Checking files");}
		
		if (inFaFile==null) die("Project directory does not have a file ending with .fna or .fna.gz");
		if (inGffFile==null) prt("Project directory does not have a file ending with .gff3 or .gff3.gz");
		
		seqDir = projDir + "/" + seqDir;
		createDir(seqDir);
		annoDir = projDir + "/" + annoDir;
		createDir(annoDir);
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
					"   -p [prefix] use this prefix in place of 'Chr'.\n" +
					"   -s  include any sequence not labeled 'chromosome'.\n" +
					"   -u  use sequence names from FASTA file when less than 5 characters (remaining will be scaffolds)\n" +
					"   -m  include Mt and Pt chromosomes.\n" +
					"   -v  write header lines of ignored sequences [default false].\n" +
					"\nSee https://csoderlund.github.io/SyMAP/convert for details.");
			System.exit(0);
		}
		
		prt("Parameters:");
		prt("Directory: " + args[0]);
		projDir = args[0];
		if (args.length>1) {
			for (int i=1; i< args.length; i++) {
				if (args[i].equals("-v")) {
					VERBOSE=true;
					prt("   Print ignored sequences");
				}
				else if (args[i].equals("-u")) {
					USENAMES=true;
					INCLUDESCAF=true;
					chrPrefix = "C";
					prt("   Use FASTA file labels if <=" + maxName);
					prt("      Others will be called Scaffold with prefix '" + scafPrefix + "'");
					prt("      IMPORTANT: SyMAP Project Parameters - set grp_prefix to blank!! (unless all sequences start with same prefix)");
				}
				else if (args[i].equals("-s")) {
					INCLUDESCAF=true;
					chrPrefix = "C";
					prt("   Include any sequence not labeled 'chromosome'");
					prt("      Uses prefixes Chr '" + chrPrefix + "' and Scaffold '" + scafPrefix );
					prt("      IMPORTANT: SyMAP Project Parameters - set grp_prefix to blank ");
				}
				else if (args[i].equals("-m")) {
					INCLUDEMtPt=true;
					prt("   Include Mt and Pt chromosomes");
				}
			}
		}   
	}
	private void die(Exception e, String msg) {
		System.err.println("Die -- " + msg);
		System.err.println("   Run 'convertEnsembl -h'  to see help");
		e.printStackTrace();
		System.exit(-1);
	}
	private void die(String msg) {
		System.err.println("Die -- " + msg);
		System.err.println("   Run 'convertEnsembl -h'  to see help");
		System.exit(-1);
	}
	private void prt(String msg) {System.out.println(msg);}
}
