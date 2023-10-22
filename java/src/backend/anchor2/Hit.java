package backend.anchor2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;

import backend.Utils;

/*******************************************************************
 * single hit data; set in AnchorMain.runReadMummer; used in GrpPairs and HitCluster
 * A hit can be to multiple T and/or Q overlapping genes
 */
public class Hit implements Comparable <Hit> {
	private static final int T=Arg.T, Q=Arg.Q;
	
	// Mummer
	protected int hitNum, id=0, sim=0, blen;
	protected int [] start, end, grpIdx, len, frame; // frames do not seem to coincide with hits along a gene
	protected String sign;
	protected boolean isHitEQ=true;
	
	// set during read mummer
	protected ArrayList <Gene> tGeneList = null;    // a hit can align to overlapping genes
	protected ArrayList <Gene> qGeneList = null;	  // ditto
	private ArrayList <Boolean> tExonList = null; // file output only
	private ArrayList <Boolean> qExonList = null; // file output only
	private boolean bHitTexon=false, bHitQexon=false;
	
	// set in GrpPairGene 
	protected HashSet <String> genePair = null;
	
    // For clusters
	protected int bin = 0, clNum=0; 				
	protected boolean bDead=false; 	// single filtered
	
	protected Hit() {
		start = new int [2];
		end = new int [2];
		grpIdx = new int [2];
	}
	
	protected Hit(int hitNum, int id, int sim, int [] start, int [] end, int [] len, 
			int [] grpIdx, String sign, int [] frame) {
		this.hitNum=hitNum;
		this.id = id;
		this.sim=sim;
		this.start = start;
		this.end = end;
		this.len = len;
		this.grpIdx = grpIdx;
		this.blen = Math.max(len[T],len[Q]);
		this.sign = sign;
		isHitEQ = (sign.equals("+/+") || sign.equals("-/-"));
	}
	
	protected void addGene(int ix, Gene gn, boolean b) { // AnchorMain2.runReadMummer
		if (ix==T) {
			if (tGeneList==null) {
				tGeneList = new ArrayList <Gene> ();
				tExonList = new ArrayList <Boolean> ();
			}
			tGeneList.add(gn);
			tExonList.add(b);
			if (b) bHitTexon=true;
		}
		else   {
			if (qGeneList==null) {
				qGeneList = new ArrayList <Gene> ();
				qExonList = new ArrayList <Boolean> ();
			}
			qGeneList.add(gn);
			qExonList.add(b);
			if (b) bHitQexon=true;
		}
	}
	protected void createGenePairs() throws Exception { // GrpPairGene.initWorkDS
		if (tGeneList==null || qGeneList==null) return;
		genePair = new HashSet <String> ();
		
		for (Gene tgn : tGeneList) {
			for (Gene qgn : qGeneList) {
				genePair.add(tgn.geneIdx + ":" + qgn.geneIdx);
			}
		}
	}
	
	protected boolean bHitsExon(int X, Gene gn){
		if (X==T) {
			for (int i=0; i<tGeneList.size(); i++)  
				if (gn==tGeneList.get(i)) return tExonList.get(i); 
		}
		else {
			for (int i=0; i<qGeneList.size(); i++)  
				if (gn==qGeneList.get(i)) return qExonList.get(i); 
		}
		Utils.die("Fatal error: " + toResults() + "\n" + gn.toResults());
		return false;
	}
	
	protected boolean bEitherExon()  {return (bHitTexon || bHitQexon);}
	protected boolean bBothExon()  	 {return (bHitTexon && bHitQexon);}
	
	protected boolean bEitherGene()	 {return (tGeneList!=null || qGeneList!=null);}
	protected boolean bBothGene()  	 {return (tGeneList!=null && qGeneList!=null);}
	
	protected boolean bTargetGene()  {return tGeneList!=null;}
	protected boolean bQueryGene() 	 {return qGeneList!=null;}
	
	protected boolean bTargetExon()  {return bHitTexon;}
	protected boolean bQueryExon() 	 {return bHitQexon;}
	
	/////////////////////////////////////////////////////////////
	public int compareTo(Hit ht) {
		if (this.start[T] < ht.start[T]) return -1;
		if (this.start[T] > ht.start[T]) return 1;
		if (this.end[T]   > ht.end[T]) return -1;
		if (this.end[T]   < ht.end[T]) return 1;
		return 0;
	}
	
	protected static void sortBySignByX(int X, ArrayList<Hit> hits) { 
		Collections.sort(hits, 
			new Comparator<Hit>() {
				public int compare(Hit h1, Hit h2) {
					if (h1.isHitEQ && !h2.isHitEQ) return -1;
					if (!h1.isHitEQ && h2.isHitEQ) return 1;
					
					if (h1.start[X] < h2.start[X])  return -1;
					if (h1.start[X] > h2.start[X])  return  1;
					if (h1.end[X] > h2.end[X])  return -1;
					if (h1.end[X] < h2.end[X])  return  1;
					return 0;
				}
			}
		);
	}
	protected static void sortByX(int X, ArrayList<Hit> hits) { 
		Collections.sort(hits, 
			new Comparator<Hit>() {
				public int compare(Hit h1, Hit h2) {
					if (h1.start[X] < h2.start[X]) return -1;
					if (h1.start[X] > h2.start[X]) return 1;
					if (h1.end[X] > h2.end[X])  return -1;
					if (h1.end[X] < h2.end[X])  return  1;
					return 0;
				}
			}
		);
	}
	
	protected static void sortByBin(ArrayList<Hit> hits) { 
		Collections.sort(hits, 
			new Comparator<Hit>() {
				public int compare(Hit h1, Hit h2) {
					return h1.bin - h2.bin;
				}
			}
		);
	}
	////////////////////////////////////////////////////////////////////
	// For file output on -dd
	protected String toDiff(Hit last) {
		String sdiff1="", sdiff2="";
		if (last!=null) {
			int diff1 =  last.start[T]-end[T];
			int diff2 =  last.start[Q]-end[Q]; 
			
			sdiff1 = (Math.abs(diff1)>=1000) ? (diff1/1000) + "k" : diff1+"";
			sdiff2 = (Math.abs(diff2)>=1000) ? (diff2/1000) + "k" : diff2+"";
			
			sdiff1 = (Math.abs(diff1)>=1000000) ? (diff1/1000000) + "M" : sdiff1;
			sdiff2 = (Math.abs(diff2)>=1000000) ? (diff2/1000000) + "M" : sdiff2;
		}
		String coords = String.format("[%,10d %,10d %,5d %6s] [%,10d %,10d %,5d %6s] ", 
				 start[T], end[T], len[T], sdiff1, 
				 start[Q], end[Q], len[Q], sdiff2);
		
		String pre = "b";
		if (bBothGene()) pre="g";
		else if (bEitherGene()) pre="e";
		String sbin = pre + bin;
		String b = String.format("%5s ", sbin);
		
		String p = String.format("CL%-5d ", clNum);
		String e = isHitEQ ? " EQ "  : " NE ";
		String d = (bDead) ? " DEAD " : " ";
		
		return coords + b + p  + geneStr() + e + d;
	}
	
	protected String toResults() {
		String pre = "b";
		if (bBothGene()) pre="g";
		else if (bEitherGene()) pre="e";
		String sbin = pre + bin;
		String b = String.format(" %7s ", sbin);
		
		String r = String.format("CL%-5d ", clNum);
		String c = String.format("%6d T[%,10d %,10d  %,6d] Q[%,10d %,10d  %,6d] [ID %3d %3d]",
				 hitNum, start[T], end[T], end[T]-start[T], start[Q], end[Q], end[Q]-start[Q], id, sim);
		String e = isHitEQ ? " EQ "  : " NE ";
		String d = (bDead) ? " SFIL " : " ";
		
		return c + b + r + geneStr() + e + d;
	}
	
	private String geneStr() {
		String tmsg="", qmsg="";
		if (tGeneList!=null) {
			for (int i=0; i< tGeneList.size(); i++) {
				if (tExonList.get(i)) tmsg += " E";
				else                  tmsg += " I";
				tmsg += tGeneList.get(i).geneTag + tGeneList.get(i).strand;
			}
		}
		if (tmsg=="") tmsg="--";
		
		if (qGeneList!=null) {
			for (int i=0; i< qGeneList.size(); i++) {
				if (qExonList.get(i)) qmsg += " E";
				else                  qmsg += " I";
				qmsg += qGeneList.get(i).geneTag + qGeneList.get(i).strand;
			}
		}
		if (qmsg=="") qmsg="--";
		
		return "[" + tmsg + "][" + qmsg + "]";
	}
	
	public String toString() {return toResults();}
}
