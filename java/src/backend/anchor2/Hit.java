package backend.anchor2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;

import backend.Utils;

/*******************************************************************
 * Hit created when MUMmer read; overlap with genes are calculated at the same time
 * A hit is either GG, Gn, nG, or nn; a hit can be to multiple T and/or Q overlapping genes
 * A second Hit constructor is for merging hits in HitPair
 */
public class Hit {
	private static final int T=Arg.T, Q=Arg.Q;
	protected int htype = Arg.typeUnk;
	
	// Mummer
	protected int hitNum;					  // order found in Mummer; trace
	protected int id=0, sim=0, maxLen;
	protected int [] start=null, end=null, len, frame; // frames do not seem to coincide with hits along a gene
	protected String sign;					 // +/+, +/-, etc
	protected boolean isStEQ=true;			 // same strand (T) diff strand (F)
	
	// set during read mummer
	private ArrayList <Gene>    tGeneList = null, qGeneList = null; // a hit can align to overlapping genes
	private ArrayList <Integer> tExonList = null, qExonList = null; // 1-to-1 with tGeneList; T if hit aligns to ANY exon in this gene

	// set in GrpPairGx.mkGenePair
	protected HashSet <String> gxPair = new HashSet <String> ();
	
	protected int bin=0;	// used as temporary for GrpPairGx.g0/g1; then used to assign bin in GrpPairGx.assignBin
	
    // For clusters
	protected int clNum=0; 				
	protected boolean bFilter=false; 	// single filtered or HitPair outlier
	
	/*************************************************
	 * AnchorMain2.runReadMummer; this is the main hit used everywhere
	 */
	protected Hit(int hitCnt, int id, int sim, int [] start, int [] end, int [] len, 
			int [] grpIdx, String sign, int [] frame) {
		this.hitNum=hitCnt;
		this.id = id;
		this.sim=sim;
		this.start = start;
		this.end = end;
		this.len = len;
		this.maxLen = Math.max(len[T],len[Q]);
		this.sign = sign;
		isStEQ = Arg.isEqual(sign);
	}
	protected void addGene(int X, Gene gn, int cov) { 
		if (X==T && tGeneList==null) {
			tGeneList = new ArrayList <Gene> ();
			tExonList = new ArrayList <Integer> ();
		}
		if (X==Q && qGeneList==null) {
			qGeneList = new ArrayList <Gene> ();
			qExonList = new ArrayList <Integer> ();
		}
		ArrayList <Gene>     geneList = (X==T) ? tGeneList : qGeneList;
		ArrayList  <Integer> exonList = (X==T) ? tExonList : qExonList;
		
		geneList.add(gn);
		exonList.add(cov);
	}

	protected void setType() { // Called when GrpPair is created for this chr-chr (right after MUMMer finishes)
		if (bNoGene()) htype=Arg.type0;
		else if (bTgeneOnly()) htype=Arg.type1t;
		else if (bQgeneOnly()) htype=Arg.type1q;
		else htype=Arg.type2;
	}
	protected void mkGenePairs() throws Exception { // GrpPairGx.g2PairHits; only G2 pairs at this point
		if (tGeneList==null || qGeneList==null) return;
		if (gxPair.size()>0) return; // already made for EQ, now use for NE
		
		for (Gene tgn : tGeneList) {
			for (Gene qgn : qGeneList) {
				gxPair.add(tgn.geneIdx + ":" + qgn.geneIdx);
			}
		}
	}
	protected int getExonCov(int X, Gene gn) { // toResults
		ArrayList <Gene>    geneList = (X==T) ? tGeneList : qGeneList;
		ArrayList <Integer> exonList = (X==T) ? tExonList : qExonList;
		for (int i=0; i<geneList.size(); i++)
			if (geneList.get(i)==gn) return exonList.get(i);
		return 0;
	}
	// G2/G1 singles: the hit overlapping a exon may just be a little...
	protected boolean bPassCutoff(int X, Gene gn){
		if (gn==null) return true;
		ArrayList <Gene>    geneList = (X==T) ? tGeneList : qGeneList;
		ArrayList <Integer> exonList = (X==T) ? tExonList : qExonList;

		double  bases = len[X]*(id/100.0);	
		
		for (int i=0; i<geneList.size(); i++)  {
			if (gn==geneList.get(i)) {
				int exonCov = exonList.get(i);
				return (exonCov>0) ? (bases>Arg.gnMinExon) : (bases>Arg.gnMinIntron);
			}
		}
		
		Utils.die("Fatal error: " + toResults() + "\n" + gn.toResults());
		return false;
	}
	// G0 
	protected boolean bPassFilter() {
		double score = Arg.baseScore(this);
		
		return (score > Arg.g0MinBases);
	}
	
	protected boolean bEitherGene()	 		{return (tGeneList!=null || qGeneList!=null);}
	protected boolean bBothGene()  	 		{return (tGeneList!=null && qGeneList!=null);}
	protected boolean bNoGene()  	 		{return (tGeneList==null && qGeneList==null);}
	protected boolean bTgeneOnly()			{return tGeneList!=null && qGeneList==null;}
	protected boolean bQgeneOnly()	 		{return qGeneList!=null && tGeneList==null;}
	protected boolean bTargetGene(Gene gn)  {return tGeneList!=null && tGeneList.contains(gn);}
	protected boolean bQueryGene(Gene gn) 	{return qGeneList!=null && qGeneList.contains(gn);}
	
	protected void clear() {
		if (tGeneList!=null) {tGeneList.clear(); tExonList.clear();}
		if (qGeneList!=null) {qGeneList.clear(); qExonList.clear();}
		gxPair.clear();
	}
	
	/*********************************************
	 * HitPair.calcMergeHits
	 */
	protected Hit(int X, int s, int e) {
		start = new int[2];
		end = new int[2];
		start[X] = s;
		end[X] = e;
	}
	
	/////////////////////////////////////////////////////////////
	protected static void sortBySignByX(int X, ArrayList<Hit> hits) { // qGrpPairList; G1 phr.hitLists; phr.setScores.hitLists
		Collections.sort(hits, 
			new Comparator<Hit>() {
				public int compare(Hit h1, Hit h2) {
					if (h1.isStEQ && !h2.isStEQ) return -1;
					if (!h1.isStEQ && h2.isStEQ) return 1;
					
					if (h1.start[X] < h2.start[X])  return -1;
					if (h1.start[X] > h2.start[X])  return  1;
					if (h1.end[X] > h2.end[X])  return -1;
					if (h1.end[X] < h2.end[X])  return  1;
					return 0;
				}
			}
		);
	}
	protected static void sortByX(int X, ArrayList<Hit> hits) { // finishSubs
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
	
	////////////////////////////////////////////////////////////////////
	// For file output on -dd
	protected String toDiff(Hit last) {
		int maxGap = Arg.maxBigGap;
		String sdiff1="", sdiff2="", td="", qd="";
		if (last!=null) {
			int olap = -Arg.olapOrGap(last.start[T], last.end[T], start[T], end[T]); // Gap -s neg
			sdiff1 =  Arg.int2Str(olap);
			if (-olap>maxGap) td = " MFT" + olap;
			else if (olap>maxGap) td = " MFT+" + olap;
			
			olap = -Arg.olapOrGap(last.start[Q], last.end[Q], start[Q], end[Q]);
			sdiff2 =  Arg.int2Str(olap);
			if (-olap>maxGap) qd = " MFQ" + olap;
			else if (olap>maxGap) qd = " MFQ+" + olap;
			
		}
		
		String coords = String.format("#%-6d [%,10d %,10d %5d %5s] [%,10d %,10d %5d %5s]", 
				 hitNum, start[T], end[T], len[T], sdiff1, 
				 start[Q], end[Q], len[Q], sdiff2);
		
		return coords + extraStr() + td + qd;
	}
	
	protected String toResults() {
		String coords = String.format("#%-6d %3s T[%,10d %,10d %5s] Q[%,10d %,10d %5s] [ID %3d %3d]",
				 hitNum, Arg.strType[htype], start[T], end[T], Arg.int2Str(end[T]-start[T]), 
				         start[Q], end[Q], Arg.int2Str(end[Q]-start[Q]), id, sim);
		
		return coords + extraStr();
	}
	private String extraStr() {
		String pre = "n";
		if (bBothGene()) pre="g";
		else if (bEitherGene()) pre="e";
		String sbin = String.format(" %7s ", (pre + bin));
		
		String e = isStEQ ? " EQ"  : " NE";
		e += sign;
		String d = (bFilter) ? " Filt " : "  ";
		
		return sbin + geneStr() + e + d + " ";
	}
	protected String geneStr() {
		String tmsg="", qmsg="";
		if (tGeneList!=null  && tGeneList.size()>0) {
			for (int i=0; i< tGeneList.size(); i++) {
				if (tExonList.get(i)>0) tmsg += " E";
				else                  tmsg += " I";
				tmsg += tGeneList.get(i).geneTag + tGeneList.get(i).strand;
			}
		}
		if (tmsg=="") tmsg="t--";
		
		if (qGeneList!=null && qGeneList.size()>0) {
			for (int i=0; i< qGeneList.size(); i++) {
				if (qExonList.get(i)>0) qmsg += " E";
				else                  qmsg += " I";
				qmsg += qGeneList.get(i).geneTag + qGeneList.get(i).strand;
			}
		}		
		if (qmsg=="") qmsg="q--";
		
		return "[" + tmsg + "][" + qmsg + "]";
	}
	
	public String toString() {return toResults();}
}
