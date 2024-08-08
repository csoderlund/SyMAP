package backend.anchor2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import util.ErrorReport;

/*******************************************************
 * Hold a gene and hits that align to it
 */
public class Gene implements Comparable <Gene> {
	private static final int T=Arg.T, Q=Arg.Q;
	
	// Gene input; set at start
	protected int ix=0; 				// 0 is target, 1 is query
	protected int gStart, gEnd, gLen;
	protected int geneIdx=0;
	protected String chr="";
	protected String suffix=".", strand="", geneTag="";
	protected ArrayList <Exon> exonList = new ArrayList <Exon> ();
	private int exonLen=0;
	
	// read mummer finds overlaps; Pair-specific - clone only
	protected ArrayList <Hit> gnHitList = new ArrayList <Hit> (); // some will be filtered, or used in two diff clHits
	
	private int hitsStart=Integer.MAX_VALUE, hitsEnd=0, cntExonHit=0; // trace
	
	protected Gene(int ix, int geneIdx, String chr, int gstart, int gend, String strand, String geneTag) {
		this.ix=ix;
		this.geneIdx = geneIdx;			// eventually saved with HitCluster
		this.chr = chr;					// trace
		this.gStart = gstart;			// for calculations
		this.gEnd = gend;				// for calculations
		this.strand = strand;			// used to insure strands are consistent with hit
		this.geneTag = geneTag.trim();	// trace
		
		gLen = (gend-gstart)+1;
	}
	protected void addExon(int estart, int eend)  {
		Exon ex = new Exon(estart, eend);
		exonList.add(ex);
		exonLen += (eend-estart)+1;
	}
	// All genes objects used from GrpPair.addGene are copies so they are chrT-chrQ specific
	private Gene() {}
	public Object copy() {
		Gene gn = new Gene();
		gn.ix = ix;
		gn.geneIdx = geneIdx;
		gn.chr = chr;
		gn.gStart = gStart;
		gn.gEnd = gEnd;
		gn.strand = strand;
		gn.geneTag = geneTag.trim();
		gn.gLen = gLen;
		for (Exon ex : exonList) gn.exonList.add(ex);
		gn.exonLen = exonLen;
		return gn;
	}
	
	//////////////////////////////////////////////////
	protected int addHit(Hit ht) throws Exception { // AnchorMain2.runReadMummer
	try {
		hitsStart = Math.min(ht.start[ix], hitsStart);
		hitsEnd   = Math.max(ht.end[ix], hitsEnd);
		gnHitList.add(ht);
		
		int olap=0, allOlap=0;
		for (Exon ex : exonList) {
			 olap = Arg.olapOnly(ht.start[ix], ht.end[ix], ex.estart, ex.eend);	
			 if (olap>0) {
				 allOlap+=olap;
				 cntExonHit++;
			 }
		}
		return allOlap;
	}
	catch (Exception e) {ErrorReport.print(e, "Add hit"); return 0;}
	}
	
	/********************************************************************
	 * Scores exon and genes
	 * mergeSet has overlapping hits merged
	 */
	protected double [] scoreExons(ArrayList <Hit> mergeSet) throws Exception {
		double [] scores = {-1,-1};
		try {
			int exonCov=0;			// %exon with hit overlap = score[0]
			for (Exon ex : exonList) {
				for (Hit mh : mergeSet) {
					exonCov+= Arg.olapOnly(mh.start[ix], mh.end[ix], ex.estart, ex.eend);
				}
			}
			scores[0] = ((double) exonCov/ (double) exonLen) * 100.0; // %exons with hit overlap		
		
			int geneCov = 0; 		// %gene with hit overlap = score[1]
			for (Hit mh : mergeSet) 
				geneCov += Arg.olapOnly(mh.start[ix], mh.end[ix], gStart, gEnd);
			scores[1] = ((double) geneCov/ (double) gLen) * 100.0; 
			
			return scores;
		}
		catch (Exception e) {ErrorReport.print(e, "Score exons");  throw e;}
	}
	
	////////////////////////////////////////////////////////////
	protected ArrayList <Hit> get0BinEither(int X, boolean isEQ) throws Exception { // GrpPairGene.createG1
	try {
		if (gnHitList.size()==0) return null;
		
		ArrayList <Hit> binList = new ArrayList <Hit> ();
		for (Hit ht : gnHitList) 
			if (ht.bin==0 && !ht.bBothGene() && ht.isStEQ==isEQ) {
				
				if      (X==Q && ht.bQueryGene(this))  binList.add(ht);
				else if (X==T && ht.bTargetGene(this)) binList.add(ht);
			}
		
		if (binList.size()==0) return null;
		return binList;
	}
	catch (Exception e) {ErrorReport.print(e, "Find remaining hits"); return null;}
	}
	
	 /////////////////////////////////////////////////
	public int compareTo(Gene gn) {
		if (this.gStart<gn.gStart) return -1;
		else if (this.gStart>gn.gStart) return 1;
		else if (this.gEnd>gn.gEnd) return -1;
		else if (this.gEnd<gn.gEnd) return -1;
		else return 0;
	}
	protected void sortExons() { // AnchorMain.loadAnno; assign Hits to Exon in Read Mummer
		Collections.sort(exonList, 
			new Comparator<Exon>() {
				public int compare(Exon g1, Exon g2) {
					if      (g1.estart < g2.estart) return -1;
					else if (g1.estart > g2.estart) return 1;
					else if (g1.eend > g2.eend) return -1;
					else if (g1.eend < g2.eend) return 1;  // CAS557 added for violates its general contract!
					else return 0;
				}
			}
		);
	}
	protected void sortHits(int X) { // GrpPairGx sort hits for genes at beginning 
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
				int be = ht.getExonCov(ix, this);
				String   e = (be>0) ? " E " : " I ";
				hits += e + ht.toDiff(last) + "\n";
				last = ht;
			}
			hits = "\n" + hits;
		}
		else return "";
		
		return toBrief()  +  hits;
	}
	protected String toBrief() {
		String gCoords = String.format("Gene [%s%,d-%,d %5s] #Ex%d ",strand, gStart, gEnd, Arg.int2Str(gEnd-gStart+1), exonList.size());
		String hCoords = (gnHitList.size()>0) ?
				String.format("Hits [%,d-%,d %5s] #Ht%d ", hitsStart,hitsEnd, Arg.int2Str(hitsEnd-hitsStart+1), gnHitList.size())
				: " No hits ";
		String exon = " To exon: " + cntExonHit;
		return toName() + gCoords + hCoords + exon;
	}
	
	protected String toName() {return  Arg.side[ix]+ "#" + geneTag + " ("  + geneIdx + ") " + chr + " ";}
	
	public String toString() {return toResults();}

	public void clear() {
		gnHitList.clear();
		exonList.clear();
		
		hitsStart=Integer.MAX_VALUE; 
		hitsEnd=0;
		cntExonHit=0;
	}
	//////////////////////////////////////////////////////////////////
	protected class Exon {
		protected Exon(int h1, int h2) {this.estart=h1; this.eend=h2;}
		protected int estart, eend;
	}
}
