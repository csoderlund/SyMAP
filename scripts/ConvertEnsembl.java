
/*************************************************************
 * ConvertEnsembl: Ensembl genome files formatted for SyMAP
 * See www.agcol.arizona.edu/software/symap/doc/convert
 */
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

public class ConvertEnsembl {
	static boolean VERBOSE = false;
	static String chrPrefix="Chr";
	
	// input
	static String projDir = null; // command line input
	static String fastaFile=null; // found in projDir
	static String annoFile= null; // found in projDir
	
	//output - the projDir gets appended to the front
	static String seqDir =  "sequence";
	static String annoDir = "annotation";
	
	// Other global variables - all for trace output
	static TreeMap <String, Integer> chrCnt = new TreeMap <String, Integer> ();
	static TreeMap <String, Integer> typeCnt = new TreeMap <String, Integer> ();
	static TreeMap <String, Integer> bioTypeCnt = new TreeMap <String, Integer> ();
	static TreeMap <String, Integer> srcCnt = new TreeMap <String, Integer> ();
	
	static int cntGene=0, cntGeneChr=0, cntGeneNotOnSeq=0, cntNoDesc=0;
	static int nChr=0, nOther=0;
	static long chrLen=0, otherLen=0;
	
	public static void main(String[] args) {
		System.out.println(">>>> ConvertEnsembl <<<<");
		parseArgs(args);
		checkFiles();
		
		System.out.println("Processing " + fastaFile);
		rwFasta();
		System.out.println("Processing " + annoFile);
		rwAnno();
		printTrace();
	}
	/**
	 * * Fasta file example:
	 * >7 dna_rm:chromosome chromosome:SolTub_3.0:7:1:56760843:1 REF
	 */
	static private void rwFasta() {
		try {
			BufferedReader fhIn = openGZIP(fastaFile);
			PrintWriter fhOut = new PrintWriter(new FileOutputStream(seqDir + "/genomic.fna", false)); 
			String line="";
			int cnt=0, len=0;
		
			String num="", chrN="";
			boolean bPrt=false, isChr=false;
			
			while ((line = fhIn.readLine()) != null) {
	    			if (line.startsWith(">")) {
	    				
	    				if (len>0) {
	    					addTrace(isChr, len, chrN);
	    					len=0;
	    				}
	    				String line1 = line.substring(1);
	    				String [] tok = line1.split("\\s+");
	    				num = tok[0];
	    				
	    				isChr = line.contains("chromosome");
	    				if (isChr) {
		    				try {
		    					Integer.parseInt(num);
		    				}
		    				catch (Exception e){
		    					if (!isRoman(num)) isChr=false; 
		    				}
	    				}
	    				if (isChr) {
	    					nChr++;
	    					chrN = chrPrefix + num;
		    				cnt++;
		    				bPrt=true;
		    				chrCnt.put(num, 0);
	    					fhOut.println(">" + chrN);
	    				}
	    				else {
	    					if (VERBOSE) System.out.println("Ignore = " + line);
	    					bPrt=false;
	    					nOther++;
	    				}
	    			}
	    			else {
	    				len += line.length()-1;
	    				if (bPrt) fhOut.println(line);
	    			}
	    		}
			if (len>0) addTrace(isChr, len, chrN);
    			fhIn.close(); fhOut.close(); 
    			System.out.println("Write fasta " + cnt);
		}
		catch (Exception e) {die(e, "rwFasta");}
	}
	static private boolean isRoman(String num) {
		char [] let = num.toCharArray();
		for (char c : let) 
			if (c!='X' && c!='V' && c!='I') return false;
		return true;
	}
	static private void addTrace(boolean isChr, int len, String seqPrefix) {
		if (isChr) {
			chrLen+=len;
			System.out.format("%-10s %,d\n", seqPrefix, len);
		}
		else {
			otherLen+=len;
		}
	}
	/**
	 * GFF file example:
	1       Potato Genome Sequencing Consortium     chromosome      1       88663952        .       .       .       
				ID=chromosome:1;Alias=ST4.02ch01
	1       pgsc    gene    152322  153489  .       -       .       
				ID=gene:PGSC0003DMG400015133;biotype=protein_coding;description=Defensin [Source:PGSC_GENE%3BAcc:PGSC0003DMG400015133];gene_id=PGSC0003DMG400015133;logic_name=pgsc
	1       pgsc    mRNA    152322  153489  .       -       .       
				ID=transcript:PGSC0003DMT400039136;Parent=gene:PGSC0003DMG400015133;Name=PGSC0003DMG400015133;biotype=protein_coding;transcript_id=PGSC0003DMT400039136
	1       pgsc    three_prime_UTR 152322  152417  .       -       .       
				Parent=transcript:PGSC0003DMT400039136
	1       pgsc    exon    152322  152593  .       -       .       
				Parent=transcript:PGSC0003DMT400039136;Name=PGSC0003DMT400039136.exon2;constitutive=1;ensembl_end_phase=-1;ensembl_phase=-1;exon_id=PGSC0003DMT400039136.exon2;rank=2
	 */
	static private void rwAnno() {
		try {
			BufferedReader fhIn = openGZIP(annoFile);
			
			PrintWriter fhOutGene = new PrintWriter(new FileOutputStream(annoDir + "/gene.gff", false));
			PrintWriter fhOutExon = new PrintWriter(new FileOutputStream(annoDir + "/exon.gff", false)); 
			
			int skipLine=0, goodLine=0;
			String line="", chr="", type="", src="", chrN="";
			
			while ((line = fhIn.readLine()) != null) {
				if (line.startsWith("#")) continue;
				
				String [] tok = line.split("\\t");
				if (tok.length!=9) {
					skipLine++;
					if (skipLine>10000) die("Goodlines: " + goodLine + " >100 skipped lines too may ...");
					continue;
				}
				goodLine++;
				
				chr  = tok[0];
				if (!chrCnt.containsKey(chr)) continue;
				
				src  = tok[1];
				type = tok[2];
				boolean isGene = type.equals("gene");
				
				if (isGene) { // count regardless of source
					if (!srcCnt.containsKey(src)) srcCnt.put(src,1);
					else srcCnt.put(src, srcCnt.get(src)+1);
					
					chrCnt.put(chr, chrCnt.get(chr)+1);
				}
				
				if (!typeCnt.containsKey(type))typeCnt.put(type,1);
				else typeCnt.put(type, typeCnt.get(type)+1);
				
				if (chrCnt.containsKey(chr))  {
					if (isGene) cntGeneChr++;
					chrN =  chrPrefix + chr;
				}
				else {
					if (isGene)  cntGeneNotOnSeq++; 
					continue;
				}
				
				if (type.equals("gene")) {
					String nLine = createGeneLine(chrN, line);
					fhOutGene.println(nLine);
					
					cntGene++;
					if (cntGene%5000==0)
						System.out.print("Wrote " + cntGene + " genes...\r");
				}
				else if (type.equals("exon")) {
					line = chrPrefix + line;
					fhOutExon.println(line);
				}
			}
			
			fhIn.close(); fhOutGene.close(); fhOutExon.close();
		}
		catch (Exception e) {die(e, "rwAnno                         ");}
	}
	
	static private String createGeneLine(String chr, String l) {
		String [] tok = l.split("\\t");
		if (tok.length!=9) die(tok.length + " " + l);
		
		String [] attrs = tok[8].split(";");
		String desc=null, type=null;
		for (String s : attrs) {
			if (s.startsWith("description=")) 	desc = s.split("=")[1];
			if (s.startsWith("biotype=")) 	  	type = s.split("=")[1];
		}
		String attr = attrs[0];
		if (desc!=null) {
			if (desc.contains("[")) 
				desc = desc.substring(0, desc.indexOf("["));
			attr += ";desc=" + desc;
		}
		else cntNoDesc++;
		
		if (!bioTypeCnt.containsKey(type)) bioTypeCnt.put(type,1);
		else bioTypeCnt.put(type, bioTypeCnt.get(type)+1);
		
		return chr + "\t" + tok[1] + "\t" + tok[2] + "\t" + tok[3] + "\t" + tok[4]
		+ "\t" + tok[5] + "\t" + tok[6] + "\t" + tok[7] + "\t" + attr;
	}

	static private void printTrace() {
		System.out.println("                                         ");
		System.out.println(">>Chromosome gene count:                       ");
		for (String key : chrCnt.keySet()) 
			System.out.format("   %-20s %,8d\n", key, chrCnt.get(key));
		
		System.out.println(">>Type count:                       ");
		for (String key : typeCnt.keySet()) 
			System.out.format("   %-20s %,8d\n", key, typeCnt.get(key));
		
		System.out.println(">>BioType count:                       ");
		for (String key : bioTypeCnt.keySet()) 
			System.out.format("   %-20s %,8d\n", key, bioTypeCnt.get(key));
		
		System.out.println(">>Gene Source count:");
		for (String key :srcCnt.keySet()) 
			System.out.format("   %-20s %,8d\n", key, srcCnt.get(key));

		System.out.println(">>Description:");
		System.out.format("   %-20s %,8d\n", "None", cntNoDesc);
		
		System.out.println(">>Genes: ");
		System.out.format("   %-20s %,8d\n", "Written to file", cntGene);
		if (cntGeneChr>0)      System.out.format("   %-20s %,8d\n", "On Chromosomes", cntGeneChr);
		if (cntGeneNotOnSeq>0) System.out.format("   %-20s %,8d\n", "Not on Chr/Scaf", cntGeneNotOnSeq);
		
		System.out.println(">>Sequences: ");
		System.out.format("   %4d chromosomes of %,12d total length\n", nChr, chrLen);
		if (nOther>0)
			 System.out.format("   %4d other       of %,12d total length  -- not written to file\n", nOther, otherLen);
	}
	static private void checkFiles() {
		try {
			System.out.println("Project directory: " + projDir);
			
			File dir = new File(projDir);
			if (!dir.isDirectory()) 
				die("The argument must be a directory. " + projDir + " is not a directory.");
			
			File[] files = dir.listFiles();
			for (File f : files) {
				if (f.isFile()) {
			       String fname = f.getName();
			       if (fname.endsWith(".fa") || fname.endsWith(".fa.gz"))
			    	   		fastaFile= projDir + "/" + fname;
			       else if (fname.endsWith(".gff3") || fname.endsWith(".gff3.gz"))
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
		createDir(seqDir);
		annoDir = projDir + "/" + annoDir;
		createDir(annoDir);
	}
	static private boolean createDir(String dir) {
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
	static private BufferedReader openGZIP(String file) {
		try {
			if (!file.endsWith(".gz")) {
				File f = new File (file);
				if (f.exists())
	    				return new BufferedReader ( new FileReader (f));
			}
			else {
				FileInputStream fin = new FileInputStream(file);
				GZIPInputStream gzis = new GZIPInputStream(fin);
				InputStreamReader xover = new InputStreamReader(gzis);
				return new BufferedReader(xover);
			}
		}
		catch (Exception e) {die(e, "Cannot open gzipped file " + file);}
		return null;
	}
	static private void parseArgs(String [] args) {
		if (args.length==0 || args[0].equals("-h") || args[0].equals("-help") || args[0].equals("help")) {
			System.out.println("\nConvertEnsembl <project directory> [-r] [-c] [-v] 				");
			System.out.println("   the project directory must contain:  " +
					        "\n       a fasta file ending with .fa.gz" +
					        "\n       a gff3  file ending with .gff3.gz" +  
					        "\n-v  write header lines of ignored sequences [default false]." +
							"\n\nSee https://csoderlund.github.io/SyMAP/convert for details.");
			System.exit(0);
		}
		
		projDir = args[0];
		if (args.length>1) {
			for (int i=1; i< args.length; i++)
				if (args[i].equals("-v")) VERBOSE=true;
		}
		if (VERBOSE) System.out.println("Verbose - print ignored sequences");
	}
	static private void die(Exception e, String msg) {
		System.err.println("Die -- " + msg);
		System.err.println("   Run 'convertEnsembl -h'  to see help");
		e.printStackTrace();
		System.exit(-1);
	}
	static private void die(String msg) {
		System.err.println("Die -- " + msg);
		System.err.println("   Run 'convertEnsembl -h'  to see help");
		System.exit(-1);
	}
}
