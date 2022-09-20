package symapMultiAlign;

/***********************************
 * SyMAP Query - align selected set using muscle
 */
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

import javax.swing.JTextField;

import symapQuery.Q;
import util.ErrorReport;
import util.Utilities;
import backend.Constants;

public class MultiAlignmentData {
	private final static String DEFAULT_SOURCE_FILE_NAME = "muscle/Source.fa"; // CAS516 put in directory and do not delete
	private final static String DEFAULT_TARGET_FILE_NAME = "muscle/Align.fa";
	private final static String DEFAULT_TARGET_DIRECTORY = "muscle";
	private final static String MUSCLE_DIR = Constants.extDir + "muscle";
	private final static int SEQUENCE_LINE_LENGTH = 80;
	private JTextField progressField; // CAS505
	
	public MultiAlignmentData(String name, JTextField progressField) {
		sequenceNames = new Vector<String> ();
		sequences = new Vector<String> ();
		strAlignmentName = name;
		this.progressField = progressField;
	}
	
	public boolean hasFilename() { return strAlignmentName != null; }
	
	public void addSequence(String name, String AASequence) {
		sequenceNames.add(name);
		sequences.add(AASequence);
	}
	
	public void alignSequences() {
		try {
			String path  = MUSCLE_DIR + Constants.getPlatformPath() + DEFAULT_TARGET_DIRECTORY;
			
			progressField.setText("Writing sequences to file");
			Utilities.checkCreateDir("muscle", true);
			writeFASTA(DEFAULT_SOURCE_FILE_NAME);
			
			progressField.setText("Running MUSCLE");
			String cmd = path + " -in " + DEFAULT_SOURCE_FILE_NAME + " -out " + DEFAULT_TARGET_FILE_NAME;
			if (Q.TEST_TRACE) System.out.println(cmd);
			
			Process pr = Runtime.getRuntime().exec(cmd);
			pr.waitFor();
			
			progressField.setText("Reading results");
			if (readFASTA(DEFAULT_TARGET_FILE_NAME, true, true)) { // CAS505 check for success
				//deleteFile(DEFAULT_SOURCE_FILE_NAME); // CAS516 quit deleting
				//deleteFile(DEFAULT_TARGET_FILE_NAME);
			}
			progressField.setText("Displaying results");
		} catch(Exception e) {
			ErrorReport.print(e, "Align sequences with muscle");
		}
	}
	
	public void writeMuscleCache(String filename) {
		String targetDirectory = DEFAULT_TARGET_DIRECTORY + "/";
		File targetDir = new File(targetDirectory);
		if(!targetDir.exists())
			targetDir.mkdir();
		writeFASTA(targetDirectory + filename);
		System.out.println(targetDirectory + filename + " written");
	}
	
	private void writeFASTA(String fname) {
		try {
			if (Q.TEST_TRACE) System.out.println("Write sequences to " + fname);
			PrintWriter out = new PrintWriter(new FileWriter(fname));
			
			Iterator<String> nameIter = sequenceNames.iterator();
			Iterator<String> seqIter = sequences.iterator();
			
			while(nameIter.hasNext()) {
				out.println(">" + nameIter.next());
				int position = 0;
				String theSequence = seqIter.next();
				for(;(position + SEQUENCE_LINE_LENGTH) < theSequence.length(); position += SEQUENCE_LINE_LENGTH) {
					out.println(theSequence.substring(position, position+SEQUENCE_LINE_LENGTH));
				}
				out.println(theSequence.substring(position));
			}
			out.close();
		} catch(Exception e) {
			ErrorReport.print(e, "Write FASTA file " + fname);
		}
	}
	
	private void deleteFile(String fileName) {
		try {
			File f = new File(fileName);
			if(!f.delete()) {
				System.out.println("Muscle Alignment failed");
			}
		} catch(Exception e) {
			ErrorReport.print(e, "Delete file " + fileName);
		}
	}
	
	private boolean readFASTA(String fname, boolean replaceSequences, boolean createConsensus) {
		try {
			BufferedReader in = new BufferedReader(new FileReader(fname));
			
			if(replaceSequences) {
				sequenceNames.clear();
				sequences.clear();
			}
			
			String theSequence = "", theSequenceName = "", line;
			while((line = in.readLine()) != null) {
				if(line.startsWith(">")) {
					if(theSequenceName.length() > 0 && theSequence.length() > 0) {
						sequenceNames.add(theSequenceName);
						sequences.add(theSequence.replace('-', '.'));
					}
					theSequenceName =  line.substring(1);
					theSequence = "";
				} else {
					theSequence += line;
				}
			}
			if(theSequenceName.length() > 0 && theSequence.length() > 0) {
				sequenceNames.add(theSequenceName);
				sequences.add(theSequence.replace('-', '.'));
			}
			
			//If wanted, create consensus at position 0
			if (createConsensus)
				setConsensus();
			
			in.close();	
			return true;
			
		} catch(Exception e) {
			ErrorReport.print(e, "No MUSCLE results " + fname); return false;
		}
	}
	
	private void setConsensus() {
		String theConsensus = "";
		
		int nSeqLength = sequences.get(0).length(); //All sequences are aligned.. they are all the same length
		
		//Get counts of each symbol
		Hashtable<Character, Integer> counts = new Hashtable<Character, Integer>();
		for(int x=0; x<nSeqLength; x++) {
			counts.clear();
			Iterator<String> seqIter = sequences.iterator();
			while(seqIter.hasNext()) {
				char c= seqIter.next().charAt(x);
				if(counts.containsKey(c))
					counts.put(c, counts.get(c) + 1);
				else
					counts.put(c, 1);
			}

			//Convert counts to a list
			Vector<SymbolCount> theCountList = new Vector<SymbolCount> ();
			for(Map.Entry<Character, Integer> val:counts.entrySet())
				theCountList.add(new SymbolCount(val.getKey(), val.getValue()));
		
			//Map no longer needed at this point
			counts.clear();
			Collections.sort(theCountList);
			
			//Test if the there is one symbol most frequent
			if( getTotalCount(theCountList) <= 1)
				theConsensus += '.';
			else if( theCountList.size() == 1 || (theCountList.get(0).theCount > theCountList.get(1).theCount && theCountList.get(0).theSymbol != '.'))
				theConsensus += theCountList.get(0).theSymbol;
			else if ( theCountList.get(0).theSymbol == '.' && (theCountList.size() == 2 || theCountList.get(1).theCount > theCountList.get(2).theCount))
				theConsensus += theCountList.get(1).theSymbol;
			else
				theConsensus += getMostCommonRelated(theCountList);
		}
		sequenceNames.insertElementAt("Consensus", 0);
		sequences.insertElementAt(theConsensus, 0);
	}
	
	private static Character getMostCommonRelated(Vector<SymbolCount> theSymbols) {
		//Special case: need at least 2 non-gap values to have consensus
		if(getTotalCount(theSymbols) == 1)
			return '.';
		
		//Create/initialize counters
		int [] relateCounts = new int[theSymbols.size()];
		for(int x=0; x<relateCounts.length; x++)
			relateCounts[x] = 0;
		
		//Going with the assumption that relationships are not mutually inclusive
		for(int x=0; x<relateCounts.length; x++) {
			for(int y=x+1; y<relateCounts.length; y++) {
				if(isCommonAcidSub(theSymbols.get(x).theSymbol, theSymbols.get(y).theSymbol)) {
					relateCounts[x]++;
					relateCounts[y]++;
				}
			}
		}

		//Find highest value
		int maxPos = 0;
		
		for(int x=1; x<relateCounts.length; x++) {
			if( (relateCounts[x]) > (relateCounts[maxPos]) )
				maxPos = x;
		}

		return theSymbols.get(maxPos).theSymbol;
	}
	
	static public boolean isCommonAcidSub(char chAcid1, char chAcid2) {
		// Return true anytime the BLOSUM62 matrix value is >= 1. This seems to
		// be how blast places '+' for a likely substitution in it's alignment.
		if (chAcid1 == chAcid2)
			return true;

		switch (chAcid1) {
		case AMINO_Glx:
			return (chAcid2 == AMINO_Asp || chAcid2 == AMINO_Gln
					|| chAcid2 == AMINO_Glu || chAcid2 == AMINO_Lys
					|| chAcid2 == AMINO_Asx);
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
					|| chAcid2 == AMINO_Lys || chAcid2 == AMINO_Asx
					|| chAcid2 == AMINO_Glx;
		case AMINO_His:
			return chAcid2 == AMINO_Asn || chAcid2 == AMINO_Tyr;
		case AMINO_Ile:
			return chAcid2 == AMINO_Leu || chAcid2 == AMINO_Met
					|| chAcid2 == AMINO_Val;
		case AMINO_Leu:
			return chAcid2 == AMINO_Ile || chAcid2 == AMINO_Met
					|| chAcid2 == AMINO_Val;
		case AMINO_Lys:
			return chAcid2 == AMINO_Arg || chAcid2 == AMINO_Gln
					|| chAcid2 == AMINO_Glu || chAcid2 == AMINO_Glx;
		case AMINO_Met:
			return chAcid2 == AMINO_Ile || chAcid2 == AMINO_Leu
					|| chAcid2 == AMINO_Val;
		case AMINO_Phe:
			return chAcid2 == AMINO_Trp || chAcid2 == AMINO_Tyr;
		case AMINO_Ser:
			return chAcid2 == AMINO_Ala || chAcid2 == AMINO_Asn
					|| chAcid2 == AMINO_Thr;
		case AMINO_Thr:
			return chAcid2 == AMINO_Ser;
		case AMINO_Trp:
			return chAcid2 == AMINO_Phe || chAcid2 == AMINO_Tyr;
		case AMINO_Tyr:
			return chAcid2 == AMINO_His || chAcid2 == AMINO_Phe
					|| chAcid2 == AMINO_Trp;
		case AMINO_Val:
			return chAcid2 == AMINO_Ile || chAcid2 == AMINO_Leu
					|| chAcid2 == AMINO_Met;
		default:
			return false;
		}
	}


	private static int getTotalCount(Vector<SymbolCount> theCounts) {
		int retVal = 0;
		
		Iterator<SymbolCount> iter = theCounts.iterator();
		while(iter.hasNext()) {
			SymbolCount temp = iter.next();
			if(temp.theSymbol != '.')
				retVal += temp.theCount;
		}
		
		return retVal;
	}

	//Data structure for sorting/retrieving counts
	private class SymbolCount implements Comparable<SymbolCount> {
		public SymbolCount(Character symbol, Integer count) {
			theSymbol = symbol;
			theCount = count;
		}
		
		public int compareTo(SymbolCount arg) {
			return -1 * theCount.compareTo(arg.theCount);
		}
		
		public Character theSymbol;
		public Integer theCount;
	}
	
	public int getNumSequences() { return sequences.size(); }
	
	public String [] getSequenceNames() { return sequenceNames.toArray(new String[sequences.size()]); }
	public String [] getSequences() { return sequences.toArray(new String[sequences.size()]); }
	
	private Vector<String> sequenceNames = null;
	private Vector<String> sequences = null;
	
	private String strAlignmentName = null;
	
	// Constants for the letters representing amino acids
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
