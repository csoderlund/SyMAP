
/*************************************************************
 * ConvertNCBI: NCBI genome files formatted for SyMAP
 * See www.agcol.arizona.edu/software/symap/doc/covert
 * 
 * Written by CAS 1/16/18; 
 *         update 2/20/20 add hard-masked option; check parent and allow genes, mRNA and exons to be in any order
 *   	   update 3/21/20 add gap file
 *         
 * Note: I am no expert on NCBI GFF format, so I cannot guarantee this is 100% correct mapping.
 * I used this for the www.agcol.arizona.edu/symapdb 2020 synteny. 
 * 
 * Your sequences/annotations may have other variations.
 * Hence, you may want to edit this to customize it for your needs -- its simply written so easy to modify.
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.Vector;
import java.util.zip.GZIPInputStream;

public class ConvertNCBI {
	// flags set by user
	private boolean REFSEQ = false;
	private boolean GNOMON = false;
	private boolean ANYSRC = true;
	private boolean LINKAGE = false;
	private boolean INCLUDESCAF = false;
	private boolean VERBOSE = false;
	private boolean MASKED = false;
	
	// Changed for specific input
	private String chrPrefix="Chr";
	private String scafPrefix="s";
	private String cPrefix="c";
	private int gapLen=30000;     // Print to gap.gff if #N's is greater than this
	
	// keywords 
	private final String geneType = "gene";
	private final String mrnaType = "mRNA";
	private final String exonType = "exon";
	
	private final String idAttrKey = "ID";
	private final String nameAttrKey = "Name"; 
	private final String biotypeAttrKey="gene_biotype";
	private final String biotypeAttr = "protein_coding";
	private final String parentAttrKey = "Parent";
	private final String productAttrKey = "product";  
	private final String exonGeneAttrKey = "gene";
		
	private final int PRT = 5000;
	
	// input
	private String projDir = null;
	private String fastaFile=null;
	private String annoFile= null;
	
	//output - the projDir gets appended to the front
	private String seqDir =   "sequence";
	private String annoDir =  "annotation";
	private String seqFile =   "/genomic.fna";
	private String geneFile = "/gene.gff";
	private String exonFile = "/exon.gff";
	private String gapFile = "/gap.gff";
	
	// Other global variables
	private TreeMap <String, String> gb2chr = new TreeMap <String, String> ();
	private TreeMap <String, String> gb2scaf = new TreeMap <String, String> ();
	
	private Vector <String> geneID = new Vector <String> (); 					// keep input order; easier to verify
	private HashMap <String, Gene> geneMap = new HashMap <String, Gene> ();	
	private HashMap <String, Gene> mrnaMap = new HashMap <String, Gene> ();
	
	// Summary
	private TreeMap <String, Integer> typeCnt = new TreeMap <String, Integer> ();
	private TreeMap <String, Integer> biotypeCnt = new TreeMap <String, Integer> ();
	private TreeMap <String, Integer> srcCnt = new TreeMap <String, Integer> ();
	private int cntDupProduct=0, cntXProduct=0, cntUniqueProduct=0, cntMultUniqueProduct=0;
	private int cntGene=0, cntGeneChr=0, cntGeneScaf=0, cntGeneNotOnSeq=0;
	private int nScaf=0, nChr=0, nOther=0, nGap=0;
	private long chrLen=0, scafLen=0, otherLen=0;
	private String exLine="";
	
	public static void main(String[] args) {
		new ConvertNCBI(args);
	}
	private ConvertNCBI(String [] args) {
		prt(">>>> ConvertNCBI <<<<");
		prt("  The 'product' description must be in the mRNA product attribute!!");
		prt("  Annotation description: ID=gene-;Name=(if unique);ID=rna-;product=");
		prt("  Types used:   gene, mRNA, exon");
		prt("  Attributes:   ID, gene_biotype, parent, product, gene");
		prt("  Only process: gene-biotype='protein_coding'");
		prt("  Uses first mRNA but appends all mRNA products for gene");
		checkArgs(args);
		checkFiles();
		
		prt("\nProcessing " + fastaFile);
		rwFasta();
		
		prt("\nProcessing " + annoFile);
		rwAnnoGene();
		rwAnnoMRNA();
		rwAnnoExon();
		
		printSummary();
		prt("Done");
	}
	/**
	 * * Fasta file example:
	 * >NC_016131.2 Brachypodium distachyon strain Bd21 chromosome 1, Brachypodium_distachyon_v2.0, whole genome shotgun sequence
	 * >NW_014576703.1 Brachypodium distachyon strain Bd21 unplaced genomic scaffold, Brachypodium_distachyon_v2.0 super_11, whole genome shotgun sequence *
	 * NOTE: NCBI files are soft-masked. The gaps are computed on this, 
	 *       even if its then hard-masked, which makes gaps and repeats appear the same
	 */
	private void rwFasta() {
		try {
			BufferedReader fhIn = openGZIP(fastaFile);
			PrintWriter fhOut = new PrintWriter(new FileOutputStream(seqDir + seqFile, false)); 
			PrintWriter ghOut = new PrintWriter(new FileOutputStream(annoDir + gapFile, false)); 
			
			String line="";
			int len=0, cntMask=0;
			char [] base = {'A', 'C', 'G', 'T', 'N', 'a', 'c', 'g', 't', 'n'};
			TreeMap <Character, Integer> cntBase = new TreeMap <Character, Integer> ();
		
			for (char b : base) 
				cntBase.put(b, 0);
				
			String name="", seqPrefix="";
			boolean bPrt=false, isChr=false, isScaf=false;
			int baseLoc=0, gapStart=0, gapCnt=1;
			
			while ((line = fhIn.readLine()) != null) {
	    			if (line.startsWith(">")) { // chromosome
	    				if (len>0) {
	    					printTrace(isChr, isScaf, len, seqPrefix);
	    					len=0;
	    				}
	    				String line1 = line.substring(1);
	    				String [] tok = line1.split("\\s+");
	    				name = tok[0];
	    				
	    				isChr = (LINKAGE) ? line.contains("linkage") : line.contains("chromosome");
	    				isScaf = line.contains("scaffold");
	    				
	    				if (isChr && !isScaf) {
	    					nChr++;
	    					seqPrefix = chrPrefix + nChr;
		    				bPrt=true;
		    				gb2chr.put(name, seqPrefix);
	    					fhOut.println(">" + seqPrefix + "  " + name);
	    					gapStart=1; gapCnt=0; baseLoc=0;
	    				}
	    				else if (isScaf) {
	    					nScaf++;
		    				seqPrefix = scafPrefix + nScaf;
		    				gb2scaf.put(name, seqPrefix);
		    				
		    				if (INCLUDESCAF) {
		    					bPrt=true;
		    					fhOut.println(">" + seqPrefix + "  " + name);
		    				}
		    				else if (VERBOSE) prt("Ignore = " + line);
	    				}
	    				else {
	    					if (VERBOSE) prt("Ignore = " + line);
	    					bPrt=false;
	    					nOther++;
	    				}
	    			}
	    			else {
	    				String aline = line.trim();
	    				char [] bases = aline.toCharArray();
	    				for (int i =0 ; i<bases.length; i++) {
	    					char b = bases[i];
	    					baseLoc++;
	    					if (b=='N') { // at least for O.sativa, there are no 'n' in soft-masked
	    						if (gapStart==0) gapStart = baseLoc;
	    						else gapCnt++;
	    					}
	    					else if (gapStart>0) {
	    						if (gapCnt>gapLen) {
	    							nGap++;
	    							String x = createGap(seqPrefix, gapStart, gapCnt);
	    							ghOut.println(x);
	    						}
	    						gapStart=0; gapCnt=1;
	    					}
	    					
	    					if (cntBase.containsKey(b)) {
	    						cntBase.put(b, cntBase.get(b)+1);
	    						if (b=='a' || b=='c' || b=='t' || b=='g') {
	    							if (MASKED) bases[i]='N';
	    							cntMask++;
	    						}
	    					}
	    					else {
	    						cntBase.put(b, 1);
	    					}
	    				}
	    				aline = new String(bases);
	    				len += aline.length();
	    				if (bPrt) fhOut.println(aline);
	    			}
	    		}
			if (len>0) printTrace(isChr, isScaf, len, seqPrefix);
    			fhIn.close(); fhOut.close(); ghOut.close();
    			
    			prt("Finish writing " + seqFile + "                 ");
    			 
    			prt( String.format("A %,10d  a %,10d", cntBase.get('A'), cntBase.get('a')) );
    			prt( String.format("T %,10d  t %,10d", cntBase.get('T'), cntBase.get('t')) );
    			prt( String.format("C %,10d  c %,10d", cntBase.get('C'), cntBase.get('c')) );
    			prt( String.format("G %,10d  g %,10d", cntBase.get('G'), cntBase.get('g')) );
    			prt( String.format("N %,10d", cntBase.get('N') ));
    			
    			if (MASKED) 
    				prt(String.format("Hard masked: %,d lower case changed to N", cntMask));
    			else
    				prt(String.format("Soft masked: %,d lower case", cntMask));
    			cntBase.clear();
    			prt(String.format("Gaps >= %d: %d", gapLen, nGap));
		}
		catch (Exception e) {die(e, "rwFasta");}
	}
	 private void printTrace(boolean isChr, boolean isScaf, int len, String chr) {
		String gb="";
		
		if (isChr && !isScaf) {
			for (String name : gb2chr.keySet()) {
				String val = gb2chr.get(name);
				if (val.equals(chr)) gb=name;
			}
			String x = chr + " " + gb;
			chrLen+=len;
			prt(String.format("%-20s   %,d", x, len));
		}
		else if (isScaf) {
			for (String name : gb2scaf.keySet()) {
				String val = gb2scaf.get(name);
				if (val.equals(chr)) gb=name;
			}
			String x = chr + " " + gb;
			scafLen+=len;
			if (INCLUDESCAF)  prt(String.format("%-20s   %,d", x, len));
			else if (VERBOSE) prt(String.format("Ignore %-20s   %,d", x, len));
		}
		else {
			otherLen+=len;
		}
	}
	/**
	 * GFF file example:
	 * NC_016131.2     Gnomon  gene    18481   21742   .       +       .       ID=gene3;Dbxref=GeneID:100827252;Name=LOC100827252;gbkey=Gene;gene=LOC100827252;gene_biotype=protein_coding
	   NC_016131.2     Gnomon  mRNA    18481   21742   .       +       .       ID=rna2;Parent=gene3;Dbxref=GeneID:100827252,Genbank:XM_003563157.3;Name=XM_003563157.3;gbkey=mRNA;gene=LOC100827252;model_evidence=Supporting evidence includes similarity to: 3 Proteins%2C and 78%25 coverage of the annotated genomic feature by RNAseq alignments;product=angio-associated migratory cell protein-like;transcript_id=XM_003563157.3
	   NC_016131.2     Gnomon  exon    18481   18628   .       +       .       ID=id15;Parent=rna2;Dbxref=GeneID:100827252,Genbank:XM_003563157.3;gbkey=mRNA;gene=LOC100827252;product=angio-associated migratory cell protein-like;transcript_id=XM_003563157.3
	 */
	 
	 /** reads genes into geneMap, plus get summary stats and verify input **/
	 private void rwAnnoGene() { 
		 try {
			 prt("Process genes....");
			 BufferedReader fhIn = openGZIP(annoFile);
			 String line="", chrName="";
			 int skipLine=0;
			 
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
					
					String src  =  tok[1];  // RefSeq, Gnomon..
					if (!srcCnt.containsKey(src)) srcCnt.put(src,1);
					else srcCnt.put(src, srcCnt.get(src)+1);
					
					String type =  tok[2];  // gene, mRNA, exon...
					if (!typeCnt.containsKey(type)) typeCnt.put(type,1);
					else typeCnt.put(type, typeCnt.get(type)+1);
					
					if (!ANYSRC) {
						boolean r = (REFSEQ && src.equals("RefSeq"));
						boolean g = (GNOMON && src.equals("Gnomon"));
						if (r==false && g==false) continue;
					}
					
					if (!type.equals(geneType)) continue;
					
					String [] typeAttrs = tok[8].split(";"); 
						
					String biotype = getVal(biotypeAttrKey, typeAttrs);
					if (!biotypeCnt.containsKey(biotype)) biotypeCnt.put(biotype,1);
					else biotypeCnt.put(biotype, biotypeCnt.get(biotype)+1);
					
					if (!biotype.equals(biotypeAttr)) continue;
					
					// chr
					String chr =   tok[0];  // chromosome, scaffold, linkage group...
					if (gb2chr.containsKey(chr))  { 
						cntGeneChr++;
						chrName = gb2chr.get(chr);
					}
					else if (gb2scaf.containsKey(chr)) {
						cntGeneScaf++; 
						if (INCLUDESCAF) chrName = gb2scaf.get(chr);
						else continue;
					}
					else {
						cntGeneNotOnSeq++; 
						continue;
					}
						
					String id =  getVal(idAttrKey, typeAttrs);
					Gene g = new Gene(chrName, line);
					geneMap.put(id, g);
					geneID.add(id);
					
					cntGene++;
					if (cntGene%PRT==0)
						System.out.print("   Process " + cntGene + " genes...\r");
			 }
			 fhIn.close();
			 prt("   Number of Genes " + geneMap.size() + "                 ");
		 }
		 catch (Exception e) {die(e, "rwAnnoGene");}
	 }
	 /** read mRNA into mrnaMap and write genes.gff **/
	 private void rwAnnoMRNA() { 
		 try {
			 prt("Process mRNA....");
			 BufferedReader fhIn = openGZIP(annoFile);
			 String line="";
			 int cntNoProduct=0, cnt=0;
			 
			 while ((line = fhIn.readLine()) != null) {
					line = line.trim();
					if (line.startsWith("#") || line.length()==0) continue;
					
					String [] tok = line.split("\\t");
					if (tok.length!=9) continue;
					
					String type =  tok[2];  // gene, mRNA, exon...
					if (!type.equals(mrnaType)) continue;
					
					String [] typeAttrs = tok[8].split(";"); 
					String pid = getVal(parentAttrKey, typeAttrs);
					if (!geneMap.containsKey(pid)) continue;
					
					Gene g = geneMap.get(pid);
					String midKey =     getKeyVal(idAttrKey, typeAttrs);
					String productKey = getKeyVal(productAttrKey, typeAttrs);
					if (productKey.equals("")) cntNoProduct++;
					
					if (g.hasProduct()) {
						g.mergeProduct(midKey, productKey);
					}
					else {
						g.setProduct(midKey, productKey);
						
						String mid =     getVal(idAttrKey, typeAttrs);
						mrnaMap.put(mid, g);	
					}
					cnt++;
					if (cnt%PRT==0)
						System.out.print("   Process " + cnt + " mRNA...\r");
			 }
			 fhIn.close();
			 prt("   Number of mMRA " + mrnaMap.size() + "                 ");
			 
			 boolean prtFirst=true;
			 PrintWriter fhOutGene = new PrintWriter(new FileOutputStream(annoDir + geneFile, false));
			 fhOutGene.println("### Generated by CovertNCBI for SyMAP");
			 
			 for (String id : geneID) {
				Gene g = geneMap.get(id);
				String nLine = g.createLine(id);
				fhOutGene.println(nLine); 
				
				if (prtFirst) {
					prtFirst=false;
					exLine = nLine;
				}
			 }
			 fhOutGene.close();
			 geneMap.clear(); geneID.clear();
			 if (cntNoProduct>0)
				 prt("WARNING: No product keyword for mRNA of gene: " + cntNoProduct);
			 prt("Finish writing " + geneFile + "                 ");
		 }
		 catch (Exception e) {die(e, "rwAnnoMRNA");}
	 }
	 /** write exons to exon.gff **/
	 private void rwAnnoExon() {
		 try {
			 prt("Process exons....");
			 PrintWriter fhOutExon = new PrintWriter(new FileOutputStream(annoDir + exonFile, false)); 
			 
			 BufferedReader fhIn = openGZIP(annoFile);
			 String line="";
			 int cnt=0;
			 
			 while ((line = fhIn.readLine()) != null) {
					line = line.trim();
					if (line.startsWith("#") || line.length()==0) continue;
					
					String [] tok = line.split("\\t");
					if (tok.length!=9) continue;
					
					String type =  tok[2];  // gene, mRNA, exon...
					if (!type.equals(exonType)) continue;
					
					String [] typeAttrs = tok[8].split(";"); 
					String pid = getVal(parentAttrKey, typeAttrs);
					if (!mrnaMap.containsKey(pid)) continue;
					
					String chrName = mrnaMap.get(pid).getChr(); // this assume the exons chr is same as gene
					
					// the attributes are not used by SyMAP, but needed for verification
					String idStr =   idAttrKey + "=" + getVal(idAttrKey, typeAttrs);
					String geneStr = exonGeneAttrKey + "=" + getVal(exonGeneAttrKey, typeAttrs);
					String newAttrs = idStr + ";" + geneStr;
					
					String nLine = chrName + "\t" + tok[1] + "\t" + tok[2] + "\t" + tok[3] + "\t" + tok[4]
								+ "\t" + tok[5] + "\t" + tok[6] + "\t" + tok[7] + "\t" + newAttrs;
					
					fhOutExon.println(nLine);
					cnt++;
					if (cnt%PRT==0)
						System.out.print("   Process " + cnt + " exons...\r");
			 }
			 fhIn.close(); fhOutExon.close();
			 prt("   Number of Exons " + cnt + "                 ");
			 prt("Finish writing exon.gff                   ");
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

	 private void printSummary() {
		prt("                                         ");
		
		prt(">>Type count:                       ");
		for (String key : typeCnt.keySet()) {
			String x = (key.equals(geneType) || key.equals(mrnaType) || key.equals(exonType)) ? "*" : "";
			prt(String.format("   %-20s %,8d %s", key, typeCnt.get(key), x));
		}
		prt(">>Biotype count:                       ");
		for (String key : biotypeCnt.keySet()) {
			String x = (key.equals(biotypeAttr)) ? "*" : "";
			prt(String.format("   %-20s %,8d %s", key, biotypeCnt.get(key), x));
		}
		
		prt(">>Gene Source count:");
		for (String key :srcCnt.keySet()) {
			prt(String.format("   %-20s %,8d", key, srcCnt.get(key)));
		}
		prt(">>Products:");
		prt(String.format("   %-20s %,8d", "Unique", cntUniqueProduct));
		if (cntMultUniqueProduct>0) prt(String.format("   %-20s %,8d", "Multiple Unique", cntMultUniqueProduct));
		if (cntDupProduct>0) prt(String.format("   %-20s %,8d", "Substring (ignored)", cntDupProduct));
		prt(String.format("   %-20s %,8d", "Variants", cntXProduct));
		
		prt(">>Genes: ");
		prt(String.format("   %-20s %,8d", "Written to file", cntGene));
		if (cntGeneChr>0)      prt(String.format("   %-20s %,8d", "On Chromosomes", cntGeneChr));
		if (cntGeneScaf>0)     prt(String.format("   %-20s %,8d", "On Scaffold", cntGeneScaf));
		if (cntGeneNotOnSeq>0) prt(String.format("   %-20s %,8d", "Not on Chr/Scaf", cntGeneNotOnSeq));
		
		prt(">>Sequences: ");
		prt(String.format("   %4d chromosomes of %,12d total length", nChr, chrLen));
		if (INCLUDESCAF) prt(String.format("   %4d scaffold    of %,12d total length", nScaf, scafLen));
		else prt(String.format("   %4d scaffold    of %,12d total length  -- not written to file", nScaf, scafLen));
		if (nOther>0)
			 prt(String.format("   %4d other       of %,12d total length  -- not written to file", nOther, otherLen));
		
		prt(">>1st output in " + geneFile + ":");
		prt(exLine);
	 }
	 private void checkFiles() {
		try {
			File dir = new File(projDir);
			if (!dir.isDirectory()) 
				die("The argument must be a directory. " + projDir + " is not a directory.");
			
			File[] files = dir.listFiles();
			for (File f : files) {
				if (f.isFile()) {
			       String fname = f.getName();
			       if (fname.endsWith(".fna.gz") || fname.endsWith(".fna"))
			    	   		fastaFile= projDir + "/" + fname;
			       else if (fname.endsWith(".gff.gz") || fname.endsWith(".gff"))
			    	   		annoFile= projDir + "/" +fname; 		
				} 
			}
		}
		catch (Exception e) {die(e, "Checking files");}
		
		if (fastaFile==null) 
			die("Project directory does not have a file ending with .fna or .fna.gz");
		if (annoFile==null) 
			die("Project directory does not have a file ending with .gff or .gff.gz");
		
		seqDir = projDir + "/" + seqDir;
		checkDir(seqDir);
		annoDir = projDir + "/" + annoDir;
		checkDir(annoDir);
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
			prt("\nConvertNCBI <project directory> [-r] [-c] [-v] 				");
			prt("   the project directory must contain:  " +
					        "\n       a fasta file ending with .fna or .fna.gz" +
					        "\n       a gff   file ending with .gff or .gff.gz" +  
					        "\n" +
					        "\n-m  assuming a soft-masked genome file, convert it to hard-masked." +
					        "\n-r  only use source RefSeq." +
					        "\n-g  only use source Gonome." +
					        "\n-s  include scaffolds." +
					        "\n-l  use linkage instead of chromosome." +
					        "\n-v  write header lines of ignored sequences." +
							"\n\nSee www.agcol.arizona.edu/software/symap/doc/convert/ncbi for details.");
			System.exit(0);
		}
		
		projDir = args[0];
		if (args.length>1) {
			for (int i=1; i< args.length; i++)
				if (args[i].equals("-s")) {
					INCLUDESCAF=true;
					chrPrefix = cPrefix;
				}
				else if (args[i].equals("-l")) LINKAGE=true;
				else if (args[i].equals("-v")) VERBOSE=true;
				else if (args[i].equals("-r")) REFSEQ=true;
				else if (args[i].equals("-g")) GNOMON=true;
				else if (args[i].equals("-m")) MASKED=true;
		}
		prt("Parameters:");
		prt("   Project directory: " + projDir);
		if (INCLUDESCAF) {
			prt("   Include Scaffolds (use prefixes Chr '" + chrPrefix + "' and Scaffold '" + scafPrefix + "')");
			prt("   IMPORTANT: SyMAP Project Parameters - set grp_prefix to blank ");
		}
				
		if (LINKAGE) {
			prt("   Use Linkage groups");
			chrPrefix="Lg";
		}
		if (MASKED)  prt("   Hard mask sequence");
		if (VERBOSE) prt("   Verbose - print ignored sequences");
		if (REFSEQ || GNOMON) {
			ANYSRC=false;
			if (REFSEQ && GNOMON) prt("   Use source REFSEQ && GNOMON ");
			else if (REFSEQ) prt("   Use source REFSEQ");
			else prt("   Use source GNOMON ");
		}
	}
	 private void die(Exception e, String msg) {
		System.err.println("Die -- " + msg);
		System.err.println("   Run 'convertGB -h'  to see help");
		e.printStackTrace();
		System.exit(-1);
	}
	 private void die(String msg) {
		System.err.println("Die -- " + msg);
		System.err.println("   Run 'convertGB -h'  to see help");
		System.exit(-1);
	}
	 private void prt(String msg) {
		System.out.println(msg);
	}
	private class Gene {
		Gene(String chr, String line) {
			this.chr = chr;
			this.line = line;
		}
		private boolean hasProduct() {return !products.equals("");}
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
			
			return chr + "\t" + tok[1] + "\t" + tok[2] + "\t" + tok[3] + "\t" + tok[4]
			+ "\t" + tok[5] + "\t" + tok[6] + "\t" + tok[7] + "\t" + newAttrs;
		}
		private String getChr() { return chr;}
		
		private String chr="", line="", products="";
	}
	//chr3    consensus       gap     4845507 4895508 .       +       .       Name    "chr03_0"
	private String createGap(String chr, int start, int len) {
		String id = "Gap_" + nGap + "_" + len;
		return chr + "\tsymap\tgap\t" + start + "\t" + (start+len) + "\t.\t+\t.\tID=\"" + id + "\"";
	}
}
