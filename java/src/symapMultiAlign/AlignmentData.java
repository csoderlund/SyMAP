package symapMultiAlign;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.Vector;

public class AlignmentData implements Comparable<AlignmentData> {	
	public int compareTo(AlignmentData obj) {
		Float OLP = theAlignment.getOLPScorePercentage();
		return -1 * OLP.compareTo(obj.theAlignment.getOLPScorePercentage());
	}
	
	public String getSequence1() { return theAlignment.getSequence1(); }
	public String getSequence2() { return theAlignment.getSequence2(); }
	public String getSequenceName1() { return theAlignment.getLabelForSequence1(); }
	public String getSequenceName2() { return theAlignment.getLabelForSequence2(); }
	public Vector<String> getDescription() { return theAlignment.getDescription(); }

	//Might be needed in the future
//	private static int getFramePosition(int frame) {
//		if(frame < 0) return (frame * -1) + 2;
//		return frame - 1;
//	}
//	
//	private static String convertNTtoAA(int frame, String NTSequence) {
//		String AASequence = "";
//		
//		for (int i = frame-1; i < NTSequence.length() - 3; i+=3) {
//			char c = getAminoAcidFor(NTSequence.charAt(i), NTSequence.charAt(i+1),NTSequence.charAt(i+2));
//			AASequence += c;
//		}
//		return AASequence;
//	}
//	
//	static private String getSequenceReverseComplement(String seqIn) {
//		String compSeq = "";
//		for (int i = seqIn.length() - 1; i >= 0; --i) {
//			compSeq += getBaseComplement(seqIn.charAt(i));
//		}
//		return compSeq;
//	}
	
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

	//Might be needed in the future
//	static private char getBaseComplement(char chBase) {
//		switch (chBase) {
//		case 'a': return 't';
//		case 'A': return 'T';
//		case 'c': return 'g';
//		case 'C': return 'G';
//		case 'g': return 'c';
//		case 'G': return 'C';
//		case 't': return 'a';
//		case 'T': return 'A';
//		case 'R': return 'Y';
//		case 'Y': return 'R';
//		case 'M': return 'K';
//		case 'K': return 'M';
//		case 'H': return 'D';
//		case 'B': return 'V';
//		case 'V': return 'B';
//		case 'D': return 'H';
//		case 'W':
//		case 'S':
//		case 'N':
//		case 'n':
//		case '.':
//			return chBase;
//		default:
//			System.err.println("Found an unexpected base letter " + chBase
//					+ ", in SequenceData.getBaseComplement.");
//			return '.';
//		}
//	}
//
//	static private char getAminoAcidFor(char chA, char chB, char chC) {
//		return codonToAminoAcid(getBaseConversionKey(chA) * 100
//				+ getBaseConversionKey(chB) * 10 + getBaseConversionKey(chC));
//	}
//	
//	static private char codonToAminoAcid(int nCodonKey) {
//		switch (nCodonKey) {
//		// T = 0;  C = 1;  A = 2  G = 3
//
//		case 200:// 200 ATT Ile
//		case 201:// 201 ATC
//		case 202:// 202 ATA
//			return AMINO_Ile; // I
//			
//		case 100:	// 100 CTT 
//		case 101:	// 101 CTC
//		case 102:	// 102 CTA
//		case 103:	// 103 CTG
//		case 2:		// 002 TTA 
//		case 3:		// 003 TTG
//			return AMINO_Leu;	// L
//						
//		case 300:// 300 GTT Val
//		case 301:// 301 GTC
//		case 302:// 302 GTA
//		case 303:// 303 GTG
//			return AMINO_Val; // V
//			
//		case 0: 	// 000 TTT 
//		case 1:		// 001 TTC
//			return AMINO_Phe; // F
//			
//		case 203:// 203 ATG Met
//			return AMINO_Met; // M Start
//			
//		case 30:	// 030 TGT 
//		case 31:	// 031 TGC
//			return AMINO_Cys; // C
//			
//		case 310:// 310 GCT Ala
//		case 311:// 311 GCC
//		case 312:// 312 GCA
//		case 313:// 313 GCG
//			return AMINO_Ala; // A
//			
//		case 330:// 320 GGT Gly
//		case 331:// 321 GGC
//		case 332:// 322 GGA
//		case 333:// 322 GGG
//			return AMINO_Gly; // G
//			
//		case 110:// 110 CCT Pro
//		case 111:// 111 CCC
//		case 112:// 112 CCA
//		case 113:// 113 CCG
//			return AMINO_Pro; //P
//			
//		case 210:// 210 ACT Thr
//		case 211:// 211 ACC
//		case 212:// 212 ACA
//		case 213:// 213 ACG
//			return AMINO_Thr; // T
//			
//		case 10:	// 010 TCT 
//		case 11:	// 011 TCC
//		case 12:	// 012 TCA
//		case 13:	// 013 TCG
//		case 230:	// 230 AGT 
//		case 231:	// 231 AGC
//			return AMINO_Ser;	// S
//			
//		case 20:	// 020 TAT Tyr
//		case 21:	// 021 TAC
//			return AMINO_Tyr; // Y
//			
//		case 33:	// 033 TGG 
//			return AMINO_Trp; // W	
//
//		case 122:// 122 CAA Gln
//		case 123:// 123 CAG
//			return AMINO_Gln; // Q	
//						
//		case 220:// 220 AAT Asn
//		case 221:// 221 AAC
//			return AMINO_Asn; // N
//			
//		case 120:// 120 CAT His
//		case 121:// 121 CAC
//			return AMINO_His; // H
//			
//		case 322:// 322 GAA Glu
//		case 323:// 322 GAG
//			return AMINO_Glu; // E
//			
//		case 320:// 320 GAT Asp
//		case 321:// 321 GAC
//			return AMINO_Asp; // D (Asparagine)
//			
//		case 222:// 222 AAA Lys
//		case 223:// 223 AAG
//			return AMINO_Lys; // K
//			
//		case 130:// 130 CGT Arg
//		case 131:// 131 CGC
//		case 132:// 132 CGA
//		case 133:// 133 CGG
//		case 232:// 232 AGA 
//		case 233:// 233 AGG
//			return AMINO_Arg; // R
//				
//		case 22:	// 022 TAA 
//		case 23:	// 023 TAG
//		case 32:	// 032 TGA 
//			return AMINO_Stop;	// * Stop
//					
//		default:
//			return AMINO_Amiguous;
//		}
//	}
//
//	static private int getBaseConversionKey(char chBase) {
//		switch (Character.toUpperCase(chBase)) {
//		case 'T':
//			return AMINO_T_Value;
//		case 'C':
//			return AMINO_C_Value;
//		case 'A':
//			return AMINO_A_Value;
//		case 'G':
//			return AMINO_G_Value;
//		case 'R': // # 3.2. Purine (adenine or guanine): R
//		case 'Y': // # 3.3. Pyrimidine (thymine or cytosine): Y
//		case 'W': // # 3.4. Adenine or thymine: W
//		case 'S': // # 3.5. Guanine or cytosine: S
//		case 'M': // # 3.6. Adenine or cytosine: M
//		case 'K': // # 3.7. Guanine or thymine: K
//		case 'H': // # 3.8. Adenine or thymine or cytosine: H
//		case 'B': // # 3.9. Guanine or cytosine or thymine: B
//		case 'V': // # 3.10. Guanine or adenine or cytosine: V
//		case 'D': // # 3.11. Guanine or adenine or thymine: D
//		case 'N': // # 3.12. Guanine or adenine or thymine or cytosine: N
//		case '.':
//		default:
//			return 0;
//		}
//	}

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
//	static private final char AMINO_Amiguous = 'X';
	static public final char AMINO_Glx = 'Z';
	static public final char AMINO_Asx = 'B';

//	static private final int AMINO_T_Value = 0;
//	static private final int AMINO_C_Value = 1;
//	static private final int AMINO_A_Value = 2;
//	static private final int AMINO_G_Value = 3;

//	private AlignData [] theAlignments = null;
	private AlignData theAlignment = null;
	
	private class AlignData {
		public AlignData(String label1, String seq1, String label2, String seq2, boolean hasAASeq) {
//			nFrame = frame;
			strLabel1 = label1;
			strSeq1 = seq1;
			strLabel2 = label2;
			strSeq2 = seq2;
			bHasAASequence = hasAASeq;
		}
		
		public String getLabelForSequence1() { return strLabel1; }
		public String getLabelForSequence2() { return strLabel2; }
		
		public String getSequence1() { return strSeq1; }
		public String getSequence2() { return strSeq2; }
		
		public Vector<String> getDescription() {
			Vector<String> retVal = new Vector<String> ();
			int s1Start, s2Start, s1Stop, s2Stop;
			int matchStart, matchStop;
			String line = "";
			
			s1Start = getSequenceStart(strSeq1);
			s1Stop = getSequenceStop(strSeq1);
			s2Start = getSequenceStart(strSeq2);
			s2Stop = getSequenceStop(strSeq2);
			
			matchStart = Math.max(s1Start, s2Start);
			matchStop = Math.min(s1Stop, s2Stop);
			
			line += getFormattedStringBig("Sequence 1", strLabel1);
			line += getFormattedStringBig("Sequence 2", strLabel2);
			retVal.add(line);
			line = "";
			retVal.add(line);
			if(bHasAASequence) {
				line += getFormattedStringHeader("HSR", "");
				line += getFormattedStringSmall("Identity", (((int)((((float)nHSRScore)/nHSRLen) * 100))) + "%");
				line += getFormattedStringSmall("Length", nHSRLen);
				retVal.add(line);
				line = "";
				line += getFormattedStringHeader("OLP", "");
				line += getFormattedStringSmall("Identity", (((int)((((float)nOLPScore)/nOLPLen) * 100))) + "%");
				line += getFormattedStringSmall("Overlap", ((int) (getPercentOverlap(matchStop-matchStart) * 100)) + "%");
//				line += getFormattedStringSmall("Frame", nFrame);
				retVal.add(line);
			} else {
				line += "No hit sequence data available for " + strLabel1;
				retVal.add(line);
			}

			return retVal;
		}
		
		public void setHSRResults(int HSRScore, int HSRLen) {
			nHSRScore = HSRScore;
			nHSRLen = HSRLen;
		}
		
		public void setOLPResults(int OLPScore, int OLPLen, int OLPMaxGap) {
			nOLPScore = OLPScore;
			nOLPLen = OLPLen;
		}
		//TODO
		public float getOLPScorePercentage() { return (((float)nOLPScore)/nOLPLen); }
		
		//Might be needed in the future
//		public boolean excludeByHeuristics() {
//			boolean retVal = false;
//			int matchStart = Math.max(getSequenceStart(strSeq1), getSequenceStart(strSeq2));
//			int matchStop = Math.min(getSequenceStop(strSeq1), getSequenceStop(strSeq2));
//			
//			//Filter by percent overlap
//			retVal = getPercentOverlap(matchStop-matchStart) <= PERCENT_OVERLAP_LIMIT;
//			
//			return retVal;
//		}
		
		private int getSequenceStart(String sequence) {
			int x;
			for(x=0; x<sequence.length() && sequence.charAt(x) == '.'; x++);
			return x;
		}
		
		private int getSequenceStop(String sequence) {
			int x;
			for(x=sequence.length()-1; x>=0 && sequence.charAt(x) == '.'; x--);
			return x;
		}
		
		private float getPercentOverlap(int overlapSize) {
			int size = Math.max(strSeq1.length(), strSeq2.length());
			return ((float)overlapSize)/size;
		}
		
		private String getFormattedStringHeader(String header, String subHeader) { return String.format(formatStringHeader, header, subHeader); } 
		private String getFormattedStringSmall(String label, int value) { return String.format(formatStringIntSmall, label, value); }
		private String getFormattedStringSmall(String label, String value) { return String.format(formatStringStringSmall, label, value); }
//		private String getFormattedStringBig(String label, int value) { return String.format(formatStringIntBig, label, value); }
		private String getFormattedStringBig(String label, String value) { return String.format(formatStringStringBig, label, value); }

		//Constants for output formatting
		private final static int LABELSIZESMALL = 7;
		private final static int VALUESIZESMALL = 5;
		private final static int LABELSIZEBIG = 15;
		private final static int VALUESIZEBIG = 20;
		private final static String formatStringHeader = "%-" + LABELSIZESMALL + "s  %-" + VALUESIZESMALL + "s";
		private final static String formatStringIntSmall = "%-" + LABELSIZESMALL + "s : %-" + VALUESIZESMALL + "d";
		private final static String formatStringStringSmall = "%-" + LABELSIZESMALL + "s : %-" + VALUESIZESMALL + "s";
//		private final static String formatStringIntBig = "%-" + LABELSIZEBIG + "s : %-" + VALUESIZEBIG + "d";
		private final static String formatStringStringBig = "%-" + LABELSIZEBIG + "s : %-" + VALUESIZEBIG + "s";
		
		//Constants for heuristics
//		private final static float PERCENT_OVERLAP_LIMIT = 0.05f;
		
		private String strSeq1 = "";
		private String strSeq2 = "";
		private String strLabel1 = "";
		private String strLabel2 = "";
		private boolean bHasAASequence = true;
//		private int nFrame = 0;
	    private int nHSRScore = -1;
	    private int nHSRLen = -1;
	    
	    private int nOLPScore = -1;
	    private int nOLPLen = -1;
	}
}
