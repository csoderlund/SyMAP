package symap.closeup;

import java.awt.Graphics;
import java.awt.FontMetrics;
import java.util.Arrays;
import java.util.Collections;
import java.util.Vector;

import util.ErrorReport;

public class SeqData implements Comparable <SeqData> { 
	public static final byte DASH = AlignData.gapCh;
	
	private byte[] alignSeq;
	private int realLength;
	private char strand;
	
	private String seqHeader;
	private String seqStr; // not aligned
	private int start=0;

	// graphic and text align
	public SeqData(String seq, String alignSeq, char strand) {
		this.alignSeq = (alignSeq == null) ? new byte[0] : alignSeq.getBytes();
		this.seqStr = seq;
		this.strand = strand;
		realLength = getRL(this.alignSeq,0,this.alignSeq.length);
	}
	// reverse align
	public SeqData(String seq, char strand) {
		this.alignSeq = (seq == null) ? new byte[0] : seq.getBytes();
		this.strand = strand;
		realLength = getRL(this.alignSeq,0,this.alignSeq.length);
	}
	
	// show seq
	public SeqData (String seq, int start, char strand, String header) {
		this.seqStr = seq;
		this.start = start;
		this.strand = strand;
		this.seqHeader = header;
	}
	public SeqData (String seq, char strand, String header) {
		this.seqStr = seq;
		this.strand = strand;
		this.seqHeader = header;
	}
	
	public boolean equals(Object obj) {
		return obj instanceof SeqData && Arrays.equals(alignSeq,((SeqData)obj).alignSeq);
	}
	public int compareTo(SeqData a){
		return start - a.start;
	}
	public String toString() {return new String(alignSeq);}	
	public int getSeqLen() {return alignSeq.toString().length();}	
	public char getStrand() {return strand;}
	public boolean isPlus() {return strand == '+';}
	public int length() {return alignSeq.length;}
	public void draw(Graphics g, int x, int y) {g.drawBytes(alignSeq,0,alignSeq.length,x,y);}
	public int getWidth(FontMetrics fm) {return fm.bytesWidth(alignSeq,0,alignSeq.length);}
	
	public String getHeader() {return seqHeader;}
	public String getSeq() {return seqStr;}
	
	/************************************************
	 *  Static calls
	 */
	private static int getRL(byte[] seq, int i, int len) {// real length
		int r;
		for (r = 0; i < len; ++i)
			if (seq[i] != DASH) ++r;
		return r;
	}
	// these two are used by all methods that display coords
	public static String coordsStr(int start, int end) {
		return String.format("%,d - %,d %dbp", start, end, (end-start+1)) ;
	}
	public static String coordsStr(char o, int start, int end) {
		return String.format("%c(%,d - %,d) %dbp", o, start, end, (end-start+1)) ;
	}
	public static String coordsStr(boolean isStrandPos, int start, int end) {
		String o = (isStrandPos) ? "+" : "-";
		return String.format("%s(%,d - %,d) %dbp", o, start, end, (end-start+1)) ;
	}
	  // CAS517 - format exon list (e.g. Exon #1:20:50,Exon #1:20:50); CAS531 moved from Utilities
    static public String formatExon(String exonList) {	
    	int rm = "Exon #".length();
    	String list="";
    	String [] tokc = exonList.split(",");
    	int dist=0, last=0;
    	
    	String [] fields = {"#", "Coords" ,"Len", "Intron"};
		int [] justify =   {1,    0,    0,     0};
		int nRow = tokc.length;
	    int nCol=  fields.length;
	    String [][] rows = new String[nRow][nCol];
	    int r=0, c=0;
	    
	    int x1, x2;
    	for (String x : tokc) {
    		String [] y = x.split(":");
    		if (y.length!=3) {System.err.println("Parsing y: " + exonList); return exonList;}
    		
    		if (!y[0].startsWith("Exon #")) System.err.println(y[0]);
    		String n = y[0].substring(rm);
    		try {
    			x1 = Integer.parseInt(y[1]);
    			x2 = Integer.parseInt(y[2]);
    		}
    		catch (Exception e) {System.err.println("Parsing int: " + exonList); return exonList;}
    		
			if (x1>x2) System.err.println("ERROR start>end " + x1 + "," + x2);
		
			rows[r][c++] = n; 
			rows[r][c++] =  String.format("%,d - %,d",x1, x2);
			rows[r][c++] =  String.format("%,d",(x2-x1+1));
			
			if (last>0) {
				dist = Math.abs(last-x1)+1; // CAS531 this was x2-x1+1?
				rows[r][c] = String.format("%,d",dist); 
			}
			else rows[r][c] = "-";
			last = x2;
			
    		r++; c=0;
    	}
    	list = util.Utilities.makeTable(nCol, nRow, fields, justify, rows);
    	return list;
    }
    // CAS517 - format a merged hit (e.g. 100:200,300:400); CAS531 moved from Utilities
    // CAS531 sorted by start so easier to read and determine overlap or gap
    static public String formatHit(String hList) {
    	String [] tok = hList.split("\n"); // name\nlist
    	if (tok.length!=2) return hList;
    	String name = tok[0];
    	String hitListStr = tok[1];
    			
    	String list="";
    	String [] tokc = hitListStr.split(",");
    	int dist=0, lastx2=0,  x1=0, x2=0, nOrder=0;
    	
    	Vector <Hit> hitSort = new Vector <Hit> (tokc.length);
    	for (String coords : tokc) {
    		String [] y = coords.split(":");
    		if (y.length!=2) {System.err.println("Parsing y: " + hitListStr); return "error";}
    		try {
    			x1 = Integer.parseInt(y[0]);
    			x2 = Integer.parseInt(y[1]);
    		}
    		catch (Exception e) {System.err.println("Parsing int: " + coords); return "error";}
			if (x1>x2) {System.err.println("ERROR start>end " + x1 + "," + x2); return "error";}
			
			nOrder++;
			Hit h = new Hit(nOrder, x1, x2);
			hitSort.add(h);
    	}
    	Collections.sort(hitSort);
    	
    	String [] fields = {"#", "Coords" ,"Len", "Gap"};
		int [] justify =   {1,    0,    0,     0};
		int nRow = tokc.length;
	    int nCol=  fields.length;
	    String [][] rows = new String[nRow][nCol];
	    int r=0, c=0, o=0;
	    
    	for (Hit h : hitSort) {
    		o = h.order;
    		x1 = h.start;
    		x2 = h.end;
    		
			rows[r][c++] =  o+""; 
			rows[r][c++] =  String.format("%,d - %,d", x1, x2);
			rows[r][c++] =  String.format("%,d", Math.abs(x2-x1+1)); // CAS535 add abs
			
			if (lastx2>0) {
				dist = x1-lastx2; 
				if (dist<0 && lastx2<x1) dist = -dist;
				rows[r][c] = String.format("%,d",dist);
			}
			else rows[r][c] = "-";
			
			lastx2 = x2;
			
    		r++; c=0;
    	}
    	list = util.Utilities.makeTable(nCol, nRow, fields, justify, rows);
    	return name + "\n" + list;
    }
    static private class Hit implements Comparable <Hit>{
    	int start, end, order;
    	
    	public Hit (int order, int start, int end) {
    		this.order=order; this.start=start; this.end=end;
    	}
    	public int compareTo(Hit a){
    		return start - a.start; 
    	}
    }
	// these 3 methods are used to closeup.HitAlignment
	public static double[] getQueryMisses(SeqData qs, SeqData ts) { 
	try {
		Vector <Double> miss = new Vector<Double>();
		double size = (double)ts.realLength;
		int pos = 0, prev = -1, i;

		for (i = 0; i < qs.alignSeq.length; ++i) {
			byte b1 = toUpperCase(qs.alignSeq[i]);
			byte b2 = toUpperCase(ts.alignSeq[i]);
			if (b1 != b2 && qs.alignSeq[i] != DASH && ts.alignSeq[i] != DASH) {
				if (pos != prev) {
					miss.add((double)pos/(double)size); // CAS520 new Double
					prev = pos;
				}
			}
			if (ts.alignSeq[i] != DASH) ++pos;
		}
		double[] ret = new double[miss.size()];
		for (i = 0; i < ret.length; ++i) ret[i] = miss.get(i);
			
		return ret;
	}
	catch (Exception e) {
		ErrorReport.die(e, "getQueryMisses "+ qs.alignSeq.toString() + "\n" +  ts.alignSeq.toString());
		return null;
	}
	}
	// CAS531 alignSeq was upperCase, but stopped doing that
	 private static final byte UPPER_CASE_OFFSET = 'A' - 'a';
	 private static byte toUpperCase(byte b) {
        if (b < 'a' || b > 'z') return b;
        return (byte) (b + UPPER_CASE_OFFSET);
    }
			 

	public static double[] getQueryInserts(SeqData qs, SeqData ts) { 
	try {
		Vector <Double> ins = new Vector <Double>();
		double size = (double)ts.realLength;
		int pos = 0, prev = -1, i;

		for (i = 0; i < qs.alignSeq.length; ++i) {
			if (ts.alignSeq[i] == DASH) {
				if (pos != prev) {
					ins.add((double)pos/(double)size); //CAS520 double
					prev = pos;
				}
			}
			else ++pos;
		}
		double[] ret = new double[ins.size()];
		for (i = 0; i < ret.length; ++i) ret[i] = ins.get(i);
		return ret;
	}
	catch (Exception e) {
		ErrorReport.die(e, "getQueryInserts\n" + qs.alignSeq.toString() + "\n" +  ts.alignSeq.toString());
		return null;
	}
	}

	public static double[] getQueryDeletes(SeqData qs, SeqData ts) { 
	try {
		Vector <Double> dels = new Vector<Double>();
		double size = (double)ts.realLength;
		int pos = 0, prev = -1, i;

		for (i = 0; i < qs.alignSeq.length; ++i) {
			if (qs.alignSeq[i] == DASH) {
				if (pos != prev) {
					dels.add((double)pos/(double)size); //CAS520 double
					prev = pos;
				}
			}
			if (ts.alignSeq[i] != DASH) ++pos;
		}
		double[] ret = new double[dels.size()];
		for (i = 0; i < ret.length; ++i) ret[i] = dels.get(i);
		
		return ret;
	}
	catch (Exception e) {
		ErrorReport.die(e, "getQueryDeletes\n" + qs.alignSeq.toString() + "\n" +  ts.alignSeq.toString());
		return null;
	}
	}
	/*****************************************************
	 * XXX DNA string; CAS531 make case-sensitive result
	 */
	// handle reverse complement of sequences for database changes
	public static String revComplement(String seq) {
		if (seq == null) return null;
		
		StringBuffer retStr = new StringBuffer(seq.length());
		int i;
		for (i = 0; i < seq.length(); i++) {
			char c = seq.charAt(i);
			switch (c) {
			case 'A':	retStr.append("T");	break;
			case 'a':	retStr.append("t");	break;
			case 'T':	retStr.append("A");	break;
			case 't':	retStr.append("a");	break;
			case 'G':	retStr.append("C");	break;
			case 'g':	retStr.append("c");	break;
			case 'C':	retStr.append("G");	break;
			case 'c':	retStr.append("g");	break;
			default:	retStr.append("N"); // anything nonstandard gets an N
			}
		}
		retStr = retStr.reverse();
		return retStr.toString();
	}
	
	/****************************************************************/
	public static String translate(String seq) {
	try {
		String aaSeq="";
		for (int i=0; i<seq.length()-2; i+=3) {
			String codon = seq.substring(i, i+3).toLowerCase();
			aaSeq += codonToAA(codon);
		}
		return aaSeq;
	}catch (Exception e) {
		ErrorReport.die(e, "translate");
		return "";
	}
	}
	
	private static char codonToAA(String codon) {
		char c='X';
		if (codon.contains("-")) return c;
		if (codon.contains("n")) return c;
		
		if (codon.equals("ttt") || codon.equals("ttc")) return AMINO_Phe; 
		
		if (codon.equals("tta") || codon.equals("ttg")) return AMINO_Leu; 
		if (codon.equals("ctt") || codon.equals("ctc")) return AMINO_Leu; 
		if (codon.equals("cta") || codon.equals("ctg")) return AMINO_Leu;
		
		if (codon.equals("att") || codon.equals("atc")) return AMINO_Ile; 
		if (codon.equals("ata")) 					   return AMINO_Ile;
		
		if (codon.equals("atg"))                        return AMINO_Met;
		
		if (codon.equals("gtt") || codon.equals("gtc")) return AMINO_Val; 
		if (codon.equals("gta") || codon.equals("gtg")) return AMINO_Val;
		
		if (codon.equals("tct") || codon.equals("tcc")) return AMINO_Ser; 
		if (codon.equals("tca") || codon.equals("tcg")) return AMINO_Ser;
		
		if (codon.equals("cct") || codon.equals("ccc")) return AMINO_Pro; 
		if (codon.equals("cca") || codon.equals("ccg")) return AMINO_Pro;
		
		if (codon.equals("act") || codon.equals("acc")) return AMINO_Thr; 
		if (codon.equals("aca") || codon.equals("acg")) return AMINO_Thr;
		
		if (codon.equals("gct") || codon.equals("gcc")) return AMINO_Ala; 
		if (codon.equals("gca") || codon.equals("gcg")) return AMINO_Ala;
		
		if (codon.equals("tat") || codon.equals("tac")) return AMINO_Tyr; 
		
		if (codon.equals("taa") || codon.equals("tag")) return AMINO_Stop;
		
		if (codon.equals("cat") || codon.equals("cac")) return AMINO_His; 
		
		if (codon.equals("caa") || codon.equals("cag")) return AMINO_Gln; 
		
		if (codon.equals("aat") || codon.equals("aac")) return AMINO_Asn; 
		
		if (codon.equals("aaa") || codon.equals("aag")) return AMINO_Lys; 
		
		if (codon.equals("gat") || codon.equals("gac")) return AMINO_Asp; 
		
		if (codon.equals("gaa") || codon.equals("gag")) return AMINO_Glu; 
		
		if (codon.equals("tgt") || codon.equals("tgc")) return AMINO_Cys; 
		if (codon.equals("tga")) 					   return AMINO_Stop;
		
		if (codon.equals("tgg"))                        return AMINO_Trp;
		
		if (codon.equals("cgt") || codon.equals("cgc")) return AMINO_Arg; 
		if (codon.equals("cga") || codon.equals("cgg")) return AMINO_Arg;
		
		if (codon.equals("agt") || codon.equals("agc")) return AMINO_Ser; 
		
		if (codon.equals("aga") || codon.equals("agg")) return AMINO_Arg; 
		
		if (codon.equals("ggt") || codon.equals("ggc")) return AMINO_Gly; 
		if (codon.equals("gga") || codon.equals("ggg")) return AMINO_Gly;
		
		System.out.println("Warning: Codon not recognized " + codon);
		return c;
	}
// Constants for the letters representing amino acids
	private static final char AMINO_Phe = 'F';
	private static final char AMINO_Ile = 'I';
	private static final char AMINO_Met = 'M';
	private static final char AMINO_Leu = 'L';
	private static final char AMINO_Val = 'V';
	private static final char AMINO_Pro = 'P';
	private static final char AMINO_Ser = 'S';
	private static final char AMINO_Thr = 'T';
	private static final char AMINO_Ala = 'A';
	private static final char AMINO_Tyr = 'Y';
	private static final char AMINO_Stop = '*';
	private static final char AMINO_Gln = 'Q';
	private static final char AMINO_Lys = 'K';
	private static final char AMINO_Glu = 'E';
	private static final char AMINO_Trp = 'W';
	private static final char AMINO_Gly = 'G';
	private static final char AMINO_His = 'H';
	private static final char AMINO_Asn = 'N';
	private static final char AMINO_Asp = 'D';
	private static final char AMINO_Cys = 'C';
	private static final char AMINO_Arg = 'R';
}
