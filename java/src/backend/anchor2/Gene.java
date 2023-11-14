package backend.anchor2;

import java.util.ArrayList;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Collections;
import java.util.Comparator;

import util.ErrorReport;

/*******************************************************
 */
public class Gene implements Comparable <Gene> {
	private static final int T=Arg.T, Q=Arg.Q;
	
	// Gene input; set at start
	protected int ix=0; 				// 0 is target, 1 is query
	protected int gstart, gend, glen;
	protected int geneIdx=0;
	protected String chr="";
	protected String suffix=".", strand="", geneTag="";
	protected boolean isPos=true;
	protected ArrayList <Exon> exonList = new ArrayList <Exon> ();
	private int exonLen=0;
	
	// read mummer finds overlaps; Pair-specific - clone only
	private ArrayList <Hit> gnHitList = new ArrayList <Hit> ();
	private HashMap <Integer, Integer> hitExonOlapMap = new HashMap <Integer, Integer> (); // key: hitNum, value overlap; which exon does not matter
	private int hitsStart=Integer.MAX_VALUE, hitsEnd=0, cntExonHit=0;
	
	// GenePairGene.initWorkDS; Pair-specific - clone only; was used for TRACE
	private TreeMap <Integer, Gene> geneHomoList = null; 
			
	protected Gene(int ix, int geneIdx, String chr, int gstart, int gend, String strand, String geneTag) {
		this.ix=ix;
		this.geneIdx = geneIdx;
		this.chr = chr;
		this.gstart = gstart;
		this.gend = gend;
		this.strand = strand;
		this.geneTag = geneTag.trim();
		
		isPos = strand.equals("+");
		glen = (gend-gstart)+1;
	}
	protected void addExon(int estart, int eend)  {
		Exon ex = new Exon(estart, eend);
		exonList.add(ex);
		exonLen += (eend-estart)+1;
	}
	// All genes objects used from GrPair are copies so they are chrT-chrQ specific
	private Gene() {}
	public Object copy() {
		Gene gn = new Gene();
		gn.ix = ix;
		gn.geneIdx = geneIdx;
		gn.chr = chr;
		gn.gstart = gstart;
		gn.gend = gend;
		gn.strand = strand;
		gn.geneTag = geneTag.trim();
		gn.isPos = isPos;
		gn.glen = glen;
		for (Exon ex : exonList) gn.exonList.add(ex);
		gn.exonLen = exonLen;
		return gn;
	}
	
	///////////////////////////////////////////////////////
	protected void addHomoGene(Gene gn) { // -tt only; GrpPairGene.initWorkDS; gene on opposite end of hit
		if (geneHomoList==null) geneHomoList = new TreeMap <Integer, Gene> ();
		if (!geneHomoList.containsKey(gn.geneIdx)) geneHomoList.put(gn.geneIdx, gn);
	} 
	
	//////////////////////////////////////////////////
	protected boolean addHit(Hit ht) throws Exception { // AnchorMain2.runReadMummer
	try {
		hitsStart = Math.min(ht.start[ix], hitsStart);
		hitsEnd   = Math.max(ht.end[ix], hitsEnd);
		gnHitList.add(ht);
		
		int idx = itvBinSearch(ht.start[ix], ht.end[ix]); // does it overlap an exon, if so, its inc
		Boolean bHitExon = (idx>=0);
		if (bHitExon) cntExonHit++;
		
		if (idx>=0) {
			int olap = Math.min(ht.end[ix], exonList.get(idx).eend) -  Math.max(ht.start[ix], exonList.get(idx).estart);
			hitExonOlapMap.put(ht.hitCnt, olap);
		}
		else hitExonOlapMap.put(ht.hitCnt, 0);
		
		return bHitExon;
	}
	catch (Exception e) {ErrorReport.print(e, "Add hit"); return false;}
	}
	// non-overlapping exons, only hit one
	private int itvBinSearch(int start, int end) {
		int low=0, high=exonList.size();
		
		while (low <= high) {
	        int mid = low  + ((high - low) / 2);
	        if (mid>=exonList.size()) return -1;
	        
	        Exon exon = exonList.get(mid);
	        int olap = Math.min(end, exon.eend) - Math.max(start, exon.estart);
	        
	        if (olap>1)  return mid;
	        
	        if (exon.estart < start) low = mid + 1;
	        else                     high = mid - 1;
	    }
	    return -1;
	}
	
	/////////////////////////////////////////////////////////
	protected double scoreExons(HashSet <Integer> hitSet) throws Exception { // GrpPairGene.initWorkDS and HitCluster.finishSubs for DB
	try {
		double score=0.0;
		for (Hit ht : gnHitList) {
			if (hitSet.contains(ht.hitCnt)) 
				score += hitExonOlapMap.get(ht.hitCnt); // YYY remove overlap between hit
		}
		score = (score/exonLen)*100.0;
		return score;
	}
	catch (Exception e) {ErrorReport.print(e, "Score exons");  return -1.0;}
	}
	protected double scoreGene(HashSet <Integer> hitSet) throws Exception {// GrpPairGene.initWorkDS 
		try {
			double score=0.0;
			for (Hit ht : gnHitList) {
				if (hitSet.contains(ht.hitCnt)) 
					score += ht.len[ix];  // YYY remove overlap between hit
			}
			score = (score/glen)*100.0; 
			return score;
		}
		catch (Exception e) {ErrorReport.print(e, "Score gene"); return -1.0;}
	} 
	
	////////////////////////////////////////////////////////////
	protected ArrayList <Hit> get0Bin(int X) throws Exception { // GrpPairGene.createSingle
	try {
		if (gnHitList.size()==0) return null;
		
		ArrayList <Hit> binList = new ArrayList <Hit> ();
		for (Hit ht : gnHitList) 
			if (ht.bin==0 && !ht.bBothGene()) {
				if (X==Q && ht.bQueryGene()) binList.add(ht);
				else if (X==T && ht.bTargetGene()) binList.add(ht);
			}
		
		if (binList.size()==0) return null;
		return binList;
	}
	catch (Exception e) {ErrorReport.print(e, "Find remaining hits"); return null;}
	}
	protected Hit getBestHit() throws Exception {// GrpPairGene.createSingle
		Hit bestHit=null;
		double bscore = 0.0;
		
		for (Hit ht : gnHitList) {
			if (ht.bin>0 || ht.bDead) continue;
			
			if (bestHit==null) bestHit = ht;
			else if (hitExonOlapMap.get(ht.hitCnt)>bscore) {
				bestHit = ht; 
				bscore = hitExonOlapMap.get(ht.hitCnt);
			}
			else if (bscore==0.0 && ht.blen>bestHit.blen) bestHit = ht;
		}
		return bestHit;
	}
	protected HashSet <Integer> getHitSet(HashSet <Integer> clSet) {
		HashSet <Integer> set = new HashSet <Integer> ();
		for (Hit ht : gnHitList) {
			if (clSet.contains(ht.hitCnt)) set.add(ht.hitCnt);
		}
		return set;
	}
	 /////////////////////////////////////////////////
	public int compareTo(Gene gn) {
		if (this.gstart<gn.gstart) return -1;
		else if (this.gstart>gn.gstart) return 1;
		else if (this.gend>gn.gend) return -1;
		else if (this.gend<gn.gend) return -1;
		else return 0;
	}
	protected void sortExons() { // AnchorMain.runAnnoFinish; assign Hits to Exon in Read Mummer
		Collections.sort(exonList, 
			new Comparator<Exon>() {
				public int compare(Exon g1, Exon g2) {
					if (g1.estart < g2.estart) return -1;
					else if (g1.estart > g2.estart) return 1;
					else if (g1.eend > g2.eend) return -1;
					else return 1;
				}
			}
		);
	}
	protected void sortHits(int X) { // GrpPairGene.initWorkDS 
		Collections.sort(gnHitList, 
			new Comparator<Hit>() {
				public int compare(Hit g1, Hit g2) {
					if      (g1.start[X] < g2.start[X]) return -1;
					else if (g1.start[X] > g2.start[X]) return 1;
					else if (g1.end[X] > g2.end[X]) return -1;
					else if (g1.end[X] > g2.end[X]) return 1;
					else return 0;
				}
			}
		);
	}
	
	/////////////////////////////////////////////////////////////////
	protected String toResults() {
		String hits="";
		if (gnHitList.size()>0) { 
			Hit last=null;
			for (Hit ht : gnHitList) {
				Boolean be = hitExonOlapMap.get(ht.hitCnt)>0;
				String   e = (be) ? " E " : " I ";
				hits += e + ht.toDiff(last) + "\n";
				last = ht;
			}
			hits = "\n" + hits;
		}
		else return "";
		
		String shared="";
		if (geneHomoList!=null) {
			shared = "\nShared" + geneHomoList.size() + ": ";
			for (Gene gn : geneHomoList.values()) {
				shared += gn.toName() + "; ";
			}
		}
		return toBrief()   + shared +  hits;
	}
	protected String toBrief() {
		String gCoords = " [" + gstart + ":" + gend + " " + (gend-gstart) +  "]; #Exons " + exonList.size() +";";
		String hCoords = (gnHitList.size()>0) ?
				" Hits [" + hitsStart + "," + hitsEnd +  " " + (hitsEnd-hitsStart) +"] #Hits " + gnHitList.size(): " No hits ";
		String exon = "; To exon: " + cntExonHit;
		return toName() + gCoords + hCoords + exon;
	}
	
	protected String toName() {return chr + "#" + geneTag + "(" + Arg.side[ix] + geneIdx + ") ";}
	
	public String toString() {return toResults();}

	public void clear() {
		if (geneHomoList!=null) geneHomoList.clear();
		geneHomoList=null;
		
		gnHitList.clear();
		hitExonOlapMap.clear();
		exonList.clear();
		
		hitsStart=Integer.MAX_VALUE; 
		hitsEnd=0;
		cntExonHit=0;
	}
	//////////////////////////////////////////////////////////////////
	public class Exon {
		private Exon(int h1, int h2) {this.estart=h1; this.eend=h2;}
		public int estart, eend;
	}
}
