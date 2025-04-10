package symap.closeup;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Vector;
import java.util.HashMap;
import javax.swing.JTextField;

import util.ErrorReport;
import util.Utilities;
import backend.Constants;
import symap.Globals;

/***********************************
 * SyMAP Query MSA - align selected set using muscle or mafft
 * 
 * Called from MsaMainPanel, which adds all sequences and their names, then calls alignMafft or alignMuscle
 * Write fasta, run align, read result
 * 
 * CAS563 add MAFFT; was named MultiAlignmentData. This was written for NT or AA, but only used for NT
 */
public class MsaRun {
	private final static String TARGET_DIR  = Globals.alignDir;	// CAS563 make global constant called /queryAlign
	private final static String SOURCE_FILE = Globals.alignDir + "/Source.fa"; // put in directory and do not delete
	private final static String TARGET_FILE = Globals.alignDir + "/Align.fa";
	private final static String MUSCLE_DIR  = Constants.extDir + "muscle"; 		// program to run
	private final static String MAFFT_DIR   = Constants.extDir + "mafft"; 		// program to run
	private final static int SEQ_LINE_LEN = 80;
	protected final static char gapIn='-', gapOut='.', unk='n';
	
	// input/output
	private String fileName = null, pgmStr="";	// input
	private JTextField progressField; 
	private Vector<String> seqNames = null;		// input/output
	private Vector<String> seqSeqs = null;		// input/output
	protected String finalStats= " ";  			// Used in MsaMainPanel;  CAS563 add
	private int maxSeq=0;
	private MsaMainPanel msaMain=null;			// to checked Stopped; CAS564 add
		
	protected MsaRun(MsaMainPanel msaMain, String name, JTextField progressField) {
		this.msaMain = msaMain;
		seqNames = new Vector<String> ();
		seqSeqs = new Vector<String> ();
		fileName = name;
		this.progressField = progressField;
		
		Utilities.deleteFile(TARGET_FILE); // previous file; if error, could read old one if not removed
	}
	protected boolean hasFilename() { return fileName != null; }
	
	// Add input
	protected void addSequence(String name, String seq) {
		seqNames.add(name);
		seqSeqs.add(seq);
	}
	// Get results
	protected int getNumSeqs() 		{return seqSeqs.size();}
	protected String [] getNames() 	{return seqNames.toArray(new String[seqSeqs.size()]);}
	protected String [] getSeqs() 	{return seqSeqs.toArray(new String[seqSeqs.size()]);}
	protected int getMaxSeqLen() 	{return maxSeq;}
	
	/************************************************************************
	 * MAFFT
	 * String cmd = ./ext/mafft/mac/mafft.bat Source.fa >Align.fa;			 // command line from /queryMSA
	 * cmd = cmd + " --auto --reorder --thread " + cpus + " " + SOURCE_FILE; // used by TCW	
	 **********************************************************************/
	protected boolean alignMafft(String status, boolean bTrim, boolean bAuto, int cpu) {// CAS563 add MAFFT
		pgmStr = "MAFFT";
		
		String cmd = MAFFT_DIR + Constants.getPlatformPath() + "mafft.bat";
		if (bAuto) cmd += " --auto";
		if (cpu>1) cmd += " --thread " + cpu;
		
		String trace = cmd + " " + SOURCE_FILE + " >" + TARGET_FILE;
		if (Globals.INFO) Globals.prt(trace);
		
		try {
			long time = Utilities.getNanoTime();
			
			progressField.setText("Writing sequences to file");
			Utilities.checkCreateDir(TARGET_DIR, true);
			writeFASTA(SOURCE_FILE);
			
			progressField.setText(status);
			
			String traceFile =  Globals.alignDir + "/maffta.log";
			
			int rc = runStdOut(cmd + " " + SOURCE_FILE, TARGET_FILE, traceFile);
			if (rc!=0) {
				if (msaMain.bStopped) return false;
				
				Globals.eprt("MAFFT failed with rc=" + rc);
				if (rc==1) Globals.prt("rc=1 may indicate not enought memory");
				Globals.prt(trace);		
				return false;
			}
			
			progressField.setText("Reading results");
			if (!readFASTA(TARGET_FILE)) {
				if (msaMain.bStopped) return false;
				
				Globals.eprt("MAFFT failed reading " + TARGET_FILE);
				Globals.prt(trace);	
				return false;
			}; 
			
			trimEnds(bTrim);
			if (Globals.INFO) finalStats += "     Time: " + Utilities.getNanoTimeStr(time);
			progressField.setText("Displaying results");
			return true;
		} 
		catch(Exception e) {
			if (!msaMain.bStopped) {	
				ErrorReport.print(e, "Exception occurred when running MAFTA"); 
				Globals.prt(trace);
			}
			return false;
		}
	}
	private int runStdOut(String cmd, String outFile, String errFile) {// CAS563 copied from TCW for using >outfile
		int exitVal=0;
    	try {
    		String[] args = cmd.split("\\s+");
    		ProcessBuilder pb = new ProcessBuilder(args);
    		pb.redirectOutput(new File(outFile));
    		pb.redirectError(new File(errFile));
    		
    		Process p = pb.start(); 
    		exitVal = p.waitFor();
    
    		return exitVal;
    	}
    	catch (MalformedURLException e) {
    		if (!msaMain.bStopped) ErrorReport.print(e, "MalformedURLException");
    		exitVal=-3;
    	}
	    catch (IOException e) {
	    	if (!msaMain.bStopped) ErrorReport.print(e, "IOException - check permissions");
    		exitVal=-2;
    	}
    	catch (Exception e) { 
    		if (!msaMain.bStopped) ErrorReport.print(e, "Command failed");
    		exitVal=-1;
    	}
    	return exitVal;
	}
	/*****************************************************************
	 * MUSCLE
	 * String cmd = path + " -in " + SOURCE_FILE + " -out " + TARGET_FILE;
	 ***************************************************************/
	protected boolean alignMuscle(String status, boolean bTrim) {// CAS563 set status in calling symapQuery.TableShow
		pgmStr = "MUSCLE";
		String cmd  = MUSCLE_DIR + Constants.getPlatformPath() + "muscle";
		
		String trace = cmd + " -in " + SOURCE_FILE + " -out " + TARGET_FILE;
		if (Globals.INFO) Globals.prt(trace);
		try {
			long time = Utilities.getNanoTime();
			
			progressField.setText("Writing sequences to file");
			Utilities.checkCreateDir(TARGET_DIR, true);
			writeFASTA(SOURCE_FILE);
			
			progressField.setText(status);
			
			String [] x = {cmd, "-in", SOURCE_FILE, "-out", TARGET_FILE}; 	// CAS557 single string depreciated 
			Process pr = Runtime.getRuntime().exec(x); 						// CAS547 single string depreciated
			pr.waitFor();
			
			if (pr.exitValue()!=0) {
				if (msaMain.bStopped) return false;
				
				Globals.eprt("MUSCLE failed with rc=" + pr.exitValue());
				Globals.prt(trace);
				return false;
			}
			progressField.setText("Reading results");
			if (!readFASTA(TARGET_FILE)) {
				if (msaMain.bStopped) return false;
				
				Globals.eprt("MUSCLE failed reading " + TARGET_FILE);
				Globals.prt(trace);
				return false;
			}; 	
			
			trimEnds(bTrim);
			if (Globals.INFO) finalStats += "     Time: " + Utilities.getNanoTimeStr(time);
			progressField.setText("Displaying results"); 
			return true;
		} 
		catch(Exception e) {
			if (!msaMain.bStopped) {
				ErrorReport.print(e, "Align sequences with muscle"); 
				Globals.prt(trace);
			}
			return false;
		}
	}
	/***************************************************************/
	private void trimEnds(boolean bTrim) {// called if Trim checkbox 
		if (!bTrim) return;
		try {
			String consen = seqSeqs.get(0);
			int len0=consen.length()-1;
			if (consen.charAt(0)!=gapOut && consen.charAt(len0)!=gapOut) {
				finalStats += "  Trim 0";
				return;
			}
			
			int s=0;
			while (consen.charAt(s)==gapOut && s<=len0) s++;
			if (s!=0) {
				Vector <String> tSeqs = new Vector <String> ();
				for (String seq : seqSeqs) tSeqs.add(seq.substring(s));
				seqSeqs.clear(); 
				seqSeqs = tSeqs;
				consen = seqSeqs.get(0);		// get modified one
				len0 = consen.length()-1;
			}
			int e=len0;
			while (consen.charAt(e)==gapOut && e>=0) e--;
			if (e!=len0) {
				Vector <String> tSeqs = new Vector <String> ();
				for (String seq : seqSeqs) tSeqs.add(seq.substring(0,e));
				seqSeqs.clear(); 
				seqSeqs = tSeqs;
			}
			finalStats += String.format("  Trim %,d", (s+(len0-e)));
		} 
		catch(Exception e) {ErrorReport.print(e, "Align trim sequences"); }
	}
	
	/******************************************************/
	protected void exportConsensus(String filename) {
		String targetDirectory = TARGET_DIR + "/";
		File targetDir = new File(targetDirectory);
		if (!targetDir.exists()) targetDir.mkdir();
		
		writeFASTA(targetDirectory + filename); // Consensus is 1st sequence, followed by the rest
	}
	/******************************************************/
	private void writeFASTA(String fname) {
		try {
			PrintWriter out = new PrintWriter(new FileWriter(fname));
			
			Iterator<String> nameIter = seqNames.iterator();
			Iterator<String> seqIter = seqSeqs.iterator();
			
			while(nameIter.hasNext()) {
				int pos = 0;
				String theSeq = seqIter.next();
				maxSeq = Math.max(theSeq.length(), maxSeq);
				out.println(">" + nameIter.next());	
				
				for(;(pos + SEQ_LINE_LEN) < theSeq.length(); pos += SEQ_LINE_LEN) {
					out.println(theSeq.substring(pos, pos+SEQ_LINE_LEN));
				}
				out.println(theSeq.substring(pos));
			}
			out.close();
		} 
		catch(Exception e) {ErrorReport.print(e, "Write FASTA file " + fname);}
	}
	/******************************************************/
	private boolean readFASTA(String fname) {
		try {
			if (!Utilities.fileExists(fname)) { // CAS559 added check
				Utilities.showErrorMessage("No output files were created. Make sure " + pgmStr + " exists");
				return false;
			}
			// The sequences in the alignment file are not in original order; save order to put back; CAS563 add
			int nSeqs = seqNames.size(); 
			HashMap <String, Integer> seqPosMap = new HashMap <String, Integer> (nSeqs);
			for (int i=0; i<seqNames.size(); i++) seqPosMap.put(seqNames.get(i), i);
			
			seqNames.clear();
			seqSeqs.clear();
			
			boolean isMAFFT = (pgmStr.equals("MAFFT"));
			
			String theSeq = "", theName = "", line;
			BufferedReader in = new BufferedReader(new FileReader(fname));
			
			String [] names = new String [nSeqs];
			String [] seq = new String [nSeqs];
			for (int i=0; i<nSeqs; i++) names[i]=seq[i]="";
			
			while((line = in.readLine()) != null) {
				if (line.startsWith(">")) {
					if (theName.length() > 0 && theSeq.length() > 0) { // last sequence
						if (seqPosMap.containsKey(theName)) {
							int i = seqPosMap.get(theName);
							names[i] = theName;
							seq[i]   = theSeq.replace(gapIn, gapOut);
						}
						else Globals.eprt("Cannot read from input file: " + theName);
					}
					theName = line.substring(1);
					theSeq  = "";
				} else {
					theSeq += line;
				}
			}
			if (theName.length() > 0 && theSeq.length() > 0) { // last one
				int i    = seqPosMap.get(theName);
				names[i] = theName;
				seq[i]   = theSeq.replace(gapIn, gapOut);
			}
			in.close();	
			
			// Enter them in original order
			int cntGood=0;
			int len = seq[0].length();
			for (int i=0; i<nSeqs; i++) {
				if (seq[i].length()!=len || names[i].equals("")) {
					if (Globals.INFO) Globals.prt("Could not align sequence #" + i + " Sequence: " + seq[i]);
					if (names[i].equals("")) names[i] = "Error";
				}
				else cntGood++;
			
				if (isMAFFT) seq[i] = seq[i].toUpperCase(); // MUSCLE is upper; setConsensus expects it
				
				seqNames.add(names[i]);
				seqSeqs.add(seq[i]);
			}
			if (cntGood==0) {
				Utilities.showErrorMessage("No results were found. Make sure " + pgmStr + " works.");
				return false;
			}
			
			// Set consensus and put in first location of output vectors
			setConsensus();
			
			return true;	
		} 
		catch(Exception e) {ErrorReport.print(e, "No alignment results " + fname); return false;}
	}
	/******************************************************
	 * Create consensus at position 0
	 */
	private void setConsensus() {
	try {
		if (seqSeqs.size()==0) return;
		String theConsensus = "";
		int nSeqLen = seqSeqs.get(0).length(); //All sequences are aligned.. they are all the same length
		
		// Calculate consensus one base at a time
		int cntGap=0, cntMatch=0, cntMis=0;
		HashMap<Character, Integer> counts = new HashMap<Character, Integer>();
		
		for(int base=0; base<nSeqLen; base++) {
			counts.clear();
			
			for (String seq : seqSeqs) {// for each sequence, get base
				if (base<seq.length()) {
					char c = seq.charAt(base);
					if (counts.containsKey(c)) counts.put(c, counts.get(c) + 1);
					else counts.put(c, 1);
				}
			}
			int totalCount=0;
			Vector <SymbolCount> bCntVec = new Vector<SymbolCount> ();
			for (Character c : counts.keySet()) {
				int cnt = counts.get(c);
				bCntVec.add(new SymbolCount(c, cnt));
				if (c!=gapOut) totalCount += cnt;
			}
			counts.clear();
			Collections.sort(bCntVec);
			
			//Test if the there is one symbol most frequent
			if (totalCount==1) { 			// single value for column; rest are gaps
				theConsensus += gapOut;
				cntGap++;
			}
			else if (bCntVec.size()==1) {	// all syms the same; can't all be gap
				theConsensus += bCntVec.get(0).sym;
				cntMatch++;
			}
			else if (bCntVec.size()==2 && (bCntVec.get(0).sym==gapOut || bCntVec.get(1).sym==gapOut)) { // more than one match, rest gaps
				char c = (bCntVec.get(0).sym==gapOut) ?  bCntVec.get(1).sym :  bCntVec.get(0).sym;
				theConsensus += c;
				cntMatch++;
			}
			else {
				char cc = unk;							// if no best base
				for (int i=0; i<bCntVec.size(); i++) {     
					SymbolCount sc = bCntVec.get(i);
					if (sc.sym==gapOut) continue;
					
					if (i+1 < bCntVec.size()) {			
						SymbolCount sc2 = bCntVec.get(i+1);	// largest count is tie, consensus unknown
						if (sc2.cnt==sc.cnt && bCntVec.get(i+1).sym!=gapOut) break;
					}
					if (sc.cnt>1) {
						cc = Character.toLowerCase(sc.sym); // CAS563 check for lc in AlignPanel
						theConsensus += cc;
						cntMis++;
						break;
					}
				}
				if (cc==unk) {
					theConsensus += cc;
					cntMis++;
				}
			}
		}
		seqNames.insertElementAt("Consensus", 0);
		seqSeqs.insertElementAt(theConsensus, 0);
		
		double score =  ((double) cntMatch/(double) nSeqLen)*100.0;
		finalStats = String.format(" %sID %.1f  Len %,d  Match %,d  Mismatch %,d  Gap %,d", 
				"%", score, nSeqLen, cntMatch, cntMis,  cntGap);
	} 
	catch(Exception e) {ErrorReport.print(e, "Error building consensus "); return;}
	}
	private static int getTotalCount(Vector<SymbolCount> theCounts) {
		int retVal = 0;
		Iterator<SymbolCount> iter = theCounts.iterator();
		while(iter.hasNext()) {
			SymbolCount temp = iter.next();
			if(temp.sym != gapOut) retVal += temp.cnt;
		}
		return retVal;
	}
	//Data structure for sorting/retrieving counts
	private class SymbolCount implements Comparable<SymbolCount> {
		private SymbolCount(char symbol, int count) {
			sym = symbol;
			cnt = count;
		}
		public int compareTo(SymbolCount a) {
			return a.cnt - cnt;
		}
		private char sym;
		private int cnt;
	}
	/**********************************************************
	 * The below is currently not used because no AA
	 ********************************************************/
	private static Character getMostCommonRelated(Vector<SymbolCount> theSymbols) {
		//Special case: need at least 2 non-gap values to have consensus
		if (getTotalCount(theSymbols) == 1) return gapOut;
		
		int [] relateCounts = new int[theSymbols.size()];
		for (int x=0; x<relateCounts.length; x++) relateCounts[x] = 0;
		
		//Going with the assumption that relationships are not mutually inclusive
		for(int x=0; x<relateCounts.length; x++) {
			for(int y=x+1; y<relateCounts.length; y++) {
				if(isCommonAcidSub(theSymbols.get(x).sym, theSymbols.get(y).sym)) {
					relateCounts[x]++;
					relateCounts[y]++;
				}
			}
		}
		int maxPos = 0; // Find highest value
		for(int x=1; x<relateCounts.length; x++) {
			if( (relateCounts[x]) > (relateCounts[maxPos]) )
				maxPos = x;
		}
		return theSymbols.get(maxPos).sym;
	}
	// CAS563 used by MultiAlignPanel - but never really since not aligning AA
	// Return true anytime the BLOSUM62 matrix value is >= 1. This seems to
	// be how blast places '+' for a likely substitution in it's alignment.
	static protected boolean isCommonAcidSub(char chAcid1, char chAcid2) {
		if (chAcid1 == chAcid2) return true;

		switch (chAcid1) {
		case AMINO_Glx:
			return (chAcid2 == AMINO_Asp || chAcid2 == AMINO_Gln
					|| chAcid2 == AMINO_Glu || chAcid2 == AMINO_Lys || chAcid2 == AMINO_Asx);
		case AMINO_Asx:
			return  (chAcid2 == AMINO_Asn || chAcid2 == AMINO_Asp
					|| chAcid2 == AMINO_Glu || chAcid2 == AMINO_Glx);
		case AMINO_Ala:
			return (chAcid2 == AMINO_Ser);
		case AMINO_Arg:
			return (chAcid2 == AMINO_Gln || chAcid2 == AMINO_Lys);
		case AMINO_Asn:
			return chAcid2 == AMINO_Asp || chAcid2 == AMINO_His
					|| chAcid2 == AMINO_Ser || chAcid2 == AMINO_Asx;
		case AMINO_Asp:
			return chAcid2 == AMINO_Asn || chAcid2 == AMINO_Glu
					|| chAcid2 == AMINO_Asx || chAcid2 == AMINO_Glx;
		case AMINO_Gln:
			return chAcid2 == AMINO_Arg || chAcid2 == AMINO_Glu
					|| chAcid2 == AMINO_Lys || chAcid2 == AMINO_Glx;
		case AMINO_Glu:
			return chAcid2 == AMINO_Asp || chAcid2 == AMINO_Gln
					|| chAcid2 == AMINO_Lys || chAcid2 == AMINO_Asx || chAcid2 == AMINO_Glx;
		case AMINO_His:
			return chAcid2 == AMINO_Asn || chAcid2 == AMINO_Tyr;
		case AMINO_Ile:
			return chAcid2 == AMINO_Leu || chAcid2 == AMINO_Met || chAcid2 == AMINO_Val;
		case AMINO_Leu:
			return chAcid2 == AMINO_Ile || chAcid2 == AMINO_Met || chAcid2 == AMINO_Val;
		case AMINO_Lys:
			return chAcid2 == AMINO_Arg || chAcid2 == AMINO_Gln
					|| chAcid2 == AMINO_Glu || chAcid2 == AMINO_Glx;
		case AMINO_Met:
			return chAcid2 == AMINO_Ile || chAcid2 == AMINO_Leu || chAcid2 == AMINO_Val;
		case AMINO_Phe:
			return chAcid2 == AMINO_Trp || chAcid2 == AMINO_Tyr;
		case AMINO_Ser:
			return chAcid2 == AMINO_Ala || chAcid2 == AMINO_Asn || chAcid2 == AMINO_Thr;
		case AMINO_Thr:
			return chAcid2 == AMINO_Ser;
		case AMINO_Trp:
			return chAcid2 == AMINO_Phe || chAcid2 == AMINO_Tyr;
		case AMINO_Tyr:
			return chAcid2 == AMINO_His || chAcid2 == AMINO_Phe || chAcid2 == AMINO_Trp;
		case AMINO_Val:
			return chAcid2 == AMINO_Ile || chAcid2 == AMINO_Leu || chAcid2 == AMINO_Met;
		default:
			return false;
		}
	}
	static private final char AMINO_Phe = 'F';
	static private final char AMINO_Ile = 'I';
	static private final char AMINO_Met = 'M';
	static private final char AMINO_Leu = 'L';
	static private final char AMINO_Val = 'V';
//	static private final char AMINO_Pro = 'P';
	static private final char AMINO_Ser = 'S';
	static private final char AMINO_Thr = 'T';
	static private final char AMINO_Ala = 'A';
	static private final char AMINO_Tyr = 'Y';
//	static private final char AMINO_Stop = '*';
	static private final char AMINO_Gln = 'Q';
	static private final char AMINO_Lys = 'K';
	static private final char AMINO_Glu = 'E';
	static private final char AMINO_Trp = 'W';
//	static private final char AMINO_Gly = 'G';
	static private final char AMINO_His = 'H';
	static private final char AMINO_Asn = 'N';
	static private final char AMINO_Asp = 'D';
//	static private final char AMINO_Cys = 'C';
	static private final char AMINO_Arg = 'R';
	static public final char AMINO_Glx = 'Z';
	static public final char AMINO_Asx = 'B';
	static public final char AMINO_Amiguous = 'X';
}
