package backend.anchor2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;

/*******************************************************************
 * Hit created when MUMmer read; overlap with genes are calculated at the same time
 * A hit is either GG, Gn, nG, or nn; a hit can be to multiple T and/or Q overlapping genes
 * A second Hit constructor is for merging hits in HitPair
 * CAS560 moved coverage filters to Arg
 */
public class Hit {
	private static final int T=Arg.T, Q=Arg.Q;
	
	protected int bin=0; 	// set in G2,G1,G0;
	protected int bin2=0;	// G2 when multiple genes overlap, can have a HPR where no ht.bin=hpr.bin, which messes up setMinor
						    // G1,G0 temporary use
	// Mummer
	protected int hitNum;					  // order found in Mummer; used in the final check of sort
	protected int id=0, sim=0, maxLen;
	protected int [] hStart, hEnd, hLen; // frames not used; does not seem to coincide with hits along a gene
	protected String sign;					 // +/+, +/-, etc
	protected boolean isStEQ=true;			 // same strand (T) diff strand (F)
	
	// set during read mummer
	protected HashMap <Integer, HitGene> tGeneMap = null, qGeneMap=null; // CAS560 was two arrays
	
	// set in GrpPairGx.mkGenePair
	protected HashSet <String> gxPair = new HashSet <String> ();
	protected int cntGene=0;
	
	/*************************************************
	 * AnchorMain2.runReadMummer; this is the main hit used everywhere
	 */
	protected Hit(int hitCnt, int id, int sim, int [] start, int [] end, int [] len, 
			int [] grpIdx, String sign, int [] frame) {
		this.hitNum=hitCnt;
		this.id = id;
		this.sim=sim;
		this.hStart = start;
		this.hEnd = end;
		this.hLen = len;
		this.maxLen = Math.max(len[T],len[Q]);
		this.sign = sign;
		isStEQ = Arg.isEqual(sign);
	}
	protected void addGene(int X, Gene gn, int exonCov) { 
		if (X==T && tGeneMap==null) tGeneMap = new HashMap <Integer, HitGene> ();
		if (X==Q && qGeneMap==null) qGeneMap = new HashMap <Integer, HitGene> ();
		
		HashMap <Integer, HitGene> geneMap = (X==T) ? tGeneMap : qGeneMap;
		
		HitGene hg = new HitGene(gn, exonCov);
		geneMap.put(gn.geneIdx, hg);
	}
	protected void mkGenePairs() throws Exception { // GrpPairGx.g2PairHits; 
		if (tGeneMap==null || qGeneMap==null) return;
		if (gxPair.size()>0) return; 				// already made for EQ, now use for NE
		
		for (Integer tgn : tGeneMap.keySet()) {
			for (Integer qgn : qGeneMap.keySet()) {
				gxPair.add(tgn + ":" + qgn);
			}
		}
	}
	protected void setBin(int hbin) {
		if (bin>0) bin2= hbin;
		else       bin = hbin;
	}
	protected void addGeneHit(Gene tgene, Gene qgene) {
		cntGene++;
		
		HitGene thg = tGeneMap.get(tgene.geneIdx);
		thg.cnt++;
		HitGene qhg = qGeneMap.get(qgene.geneIdx);
		qhg.cnt++;
	}
	protected int getExonCov(int X, Gene gn) { // rmEndHits
		HashMap <Integer, HitGene> geneMap = (X==T) ? tGeneMap : qGeneMap;
		if (geneMap==null) return 0;
		
		if (geneMap.containsKey(gn.geneIdx)) return geneMap.get(gn.geneIdx).exonCov;
		
		return 0;
	}
	// for G1, ygene is null,
	protected void rmGeneFromHit(int X, Gene xgene, Gene ygene) { // do not remove from geneMap because may be used by another
		cntGene--;
		
		HashMap <Integer, HitGene> geneMap = (X==T) ? tGeneMap : qGeneMap;
		if (geneMap!=null) {
			if (geneMap.containsKey(xgene.geneIdx)) {
				HitGene hg = geneMap.get(xgene.geneIdx);
				hg.cnt--;
			}
			else symap.Globals.tprt("No gene " + xgene.geneTag + " for hit #" + hitNum);
		}
		if (ygene!=null) {
			geneMap = (X!=T) ? tGeneMap : qGeneMap;
			if (geneMap.containsKey(ygene.geneIdx)) {
				HitGene hg = geneMap.get(ygene.geneIdx);
				hg.cnt--;
			}
			else symap.Globals.tprt("No gene " + xgene.geneTag + " for hit #" + hitNum);
		}
	}
	
	protected boolean bHitsAnotherExon(int X, Gene xgene) { // rmEndHits
		if (cntGene==1) return false;		      // even if only hits intron, its not overlapping another
		HashMap <Integer, HitGene> geneMap = (X==T) ? tGeneMap : qGeneMap;
		
		if (!geneMap.containsKey(xgene.geneIdx)) {
			symap.Globals.tprt("****No " + xgene.geneTag);
			return false;
		}
		
		HitGene xhg = geneMap.get(xgene.geneIdx);
 		if (xhg.exonCov==0) return true;		      // this one falls in intron, let other gene use it
		
		int bestCov=0;
		for (HitGene hg : geneMap.values()) {
			if (hg!=xhg && hg.exonCov>0 && hg.cnt>0) bestCov = Math.max(bestCov, hg.exonCov);
		}
		if (bestCov==0) return false;
		return (xhg.exonCov < bestCov); 	          // this<best=T remove hit, let other gene use it
	}
	protected boolean bEitherGene()	 		{return tGeneMap!=null || qGeneMap!=null;}
	protected boolean bBothGene()  	 		{return tGeneMap!=null && qGeneMap!=null;}
	protected boolean bNoGene()  	 		{return tGeneMap==null && qGeneMap==null;}
	protected boolean bTgeneOnly()			{return tGeneMap!=null && qGeneMap==null;}
	protected boolean bQgeneOnly()	 		{return qGeneMap!=null && tGeneMap==null;}
	protected boolean bHasTgene(Gene gn)    {return tGeneMap!=null && tGeneMap.containsKey(gn.geneIdx);}
	protected boolean bHasQgene(Gene gn) 	{return qGeneMap!=null && qGeneMap.containsKey(gn.geneIdx);}
	
	protected void clear() {
		if (tGeneMap!=null) {tGeneMap.clear();}
		if (qGeneMap!=null) {qGeneMap.clear();}
		gxPair.clear();
	}
	
	/****** classes ******************/
	/** HitPair.calcMergeHits - merges one side at time */
	protected Hit(int X, int s, int e) {
		hStart = new int[2];
		hEnd = new int[2];
		hStart[X] = s;
		hEnd[X] = e;
	}
	private class HitGene {
		private HitGene(Gene xgene, int cov) {this.xgene=xgene; this.exonCov = cov; }
		Gene xgene;
		int exonCov;
		int cnt=0;
	}
	/***  Sorts **********************/
	protected static void sortBySignByX(int X, ArrayList<Hit> hits) { // Gene.gHitList for toResults
		Collections.sort(hits, 
			new Comparator<Hit>() {
				public int compare(Hit h1, Hit h2) {
					if (h1.isStEQ && !h2.isStEQ) return -1;
					if (!h1.isStEQ && h2.isStEQ) return 1;
					
					if (h1.hStart[X] < h2.hStart[X])  return -1;
					if (h1.hStart[X] > h2.hStart[X])  return  1;
					if (h1.hEnd[X] > h2.hEnd[X])  return -1;
					if (h1.hEnd[X] < h2.hEnd[X])  return  1;
					return 0;
				}
			}
		);
	}
	// HitPair.hitList: HitPair.setScores, setSubs; GrpPairGx rmEndsHitsG1/G2, runG0
	protected static void sortXbyStart(int X, ArrayList<Hit> hits) throws Exception { 
		Collections.sort(hits, 
			new Comparator<Hit>() {
				public int compare(Hit h1, Hit h2) {
					if (h1.hStart[X] < h2.hStart[X]) return -1;
					if (h1.hStart[X] > h2.hStart[X]) return 1;
					if (h1.hEnd[X] > h2.hEnd[X])  return -1;
					if (h1.hEnd[X] < h2.hEnd[X])  return  1;
					
					int Y = (X==Arg.Q) ? Arg.T : Arg.Q;
					if (h1.hStart[Y] < h2.hStart[Y]) return -1;
					if (h1.hStart[Y] > h2.hStart[Y]) return 1;
					if (h1.hEnd[Y] > h2.hEnd[Y])  return -1;
					if (h1.hEnd[Y] < h2.hEnd[Y])  return  1;
					
					return 0;
				}
			}
		);
	}
	
	/******** For file output on -tt ******************/
	protected String toDiff(Hit last) {
		int maxGap = Arg.iGmIntronLenRm; 
		String sdiff1="", sdiff2="", td="", qd="";
		if (last!=null) {
			int olap = Arg.pGap_nOlap(last.hStart[T], last.hEnd[T], hStart[T], hEnd[T]); // Negative is gap; make Olap neg
			sdiff1 =   Arg.int2Str(olap);                       // returns string prefixed with - or blank
			
			if (Math.abs(olap)>maxGap) td = " T" + olap;
		
			olap =    Arg.pGap_nOlap(last.hStart[Q], last.hEnd[Q], hStart[Q], hEnd[Q]);
			sdiff2 =  Arg.int2Str(olap);
			if (Math.abs(olap)>maxGap) qd = " Q" + olap;
		}
		
		String pre = "n";
		if (bBothGene()) pre="g";
		else if (bEitherGene()) pre="e";
		String sbin = String.format(" %7s ", (pre + bin));
		if (bin2>0) sbin += "gg" + bin2 + " ";
		String e = isStEQ ? " EQ"  : " NE";
		e += sign;
		String ext = sbin + toGeneStr() + e + " ";
		
		String coords = String.format("#%-6d [T %,10d %,10d %,5d %5s] [Q %,10d %,10d %,5d %5s] [ID %3d]", 
				 hitNum, hStart[T], hEnd[T], hLen[T], sdiff1, hStart[Q], hEnd[Q], hLen[Q], sdiff2, id);
		
		return coords + ext + td + qd;
	}
	protected String toGeneStr() {
		String tmsg="", qmsg="";
		if (tGeneMap!=null  && tGeneMap.size()>0) {
			for (HitGene hg : tGeneMap.values()) {
				if (hg.exonCov>0) tmsg += " Et";
				else          tmsg += " It";
				tmsg += hg.xgene.geneTag + hg.xgene.strand + hg.cnt;
			}
		}
		if (tmsg=="") tmsg="t--";
		
		if (qGeneMap!=null && qGeneMap.size()>0) {
			for (HitGene hg : qGeneMap.values()) {
				if (hg.exonCov>0) qmsg += " Eq";
				else              qmsg += " Iq";
				qmsg += hg.xgene.geneTag + hg.xgene.strand + hg.cnt;
			}
		}		
		if (qmsg=="") qmsg="q--";
		
		return "[" + tmsg + "][" + qmsg + "]" + cntGene + " ";
	}
}
