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
	protected int ix=0; 				
	protected int gStart, gEnd, gLen;
	protected int geneIdx=0;
	protected String chr="";
	protected String strand="", geneTag="";  // geneTag, e.g. 136. or 136.b
	protected ArrayList <Exon> exonList = new ArrayList <Exon> ();
	protected int exonSumLen=0; 
	
	protected boolean isOlapGene=false;
	
	// read mummer finds overlaps and assign to gene for chr-chr; Pair-specific - clone only
	protected ArrayList <Hit> gHitList = new ArrayList <Hit> ();          // some will be filtered, or used in two diff clHits
	protected ArrayList <HitPair> gHprList = new ArrayList <HitPair> ();  // CAS560 add 
	
	private int hitsStart=Integer.MAX_VALUE, hitsEnd=0; // trace
	
	protected Gene(int ix, int geneIdx, String chr, int gstart, int gend, String strand, String geneTag) {
		this.ix=ix;						// 0 is target, 1 is query
		this.geneIdx = geneIdx;			// eventually saved with HitPair
		this.chr = chr;					// trace
		this.gStart = gstart;			// for calculations
		this.gEnd = gend;				// for calculations
		this.strand = strand;			// used to insure strands are consistent with hit
		this.geneTag = geneTag.trim();	// trace; up to '('
		isOlapGene = !this.geneTag.endsWith(".");
		
		gLen = (gend-gstart)+1;
	}
	protected void addExon(int estart, int eend)  {
		Exon ex = new Exon(estart, eend);
		exonList.add(ex);
		exonSumLen += (eend-estart)+1;
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
		gn.exonSumLen = exonSumLen;
		
		gn.isOlapGene = isOlapGene;
		
		return gn;
	}
	
	//////////////////////////////////////////////////
	protected int addHit(Hit ht) throws Exception { // AnchorMain2.runReadMummer
	try {
		hitsStart = Math.min(ht.hStart[ix], hitsStart);
		hitsEnd   = Math.max(ht.hEnd[ix], hitsEnd);
		gHitList.add(ht);
		
		int allOlap=0;
		for (int i=0; i<exonList.size(); i++) {
			 Exon ex = exonList.get(i);
			 allOlap += Arg.pOlapOnly(ht.hStart[ix], ht.hEnd[ix], ex.estart, ex.eend);	
		}
		return allOlap;
	}
	catch (Exception e) {ErrorReport.print(e, "Add hit"); return 0;}
	}
	
	/********************************************************************
	 * Scores exon and genes; called from HitPair.setScores
	 * mergeSet has overlapping hits merged
	 * Return:
	 * 	%exons with a hit = score[0]
	 *  %exon with merge hit overlap = score[1]
	 *  %gene with hit overlap = score[2]
	 */
	protected double [] scoreExonsGene(ArrayList <Hit> mergeSet) throws Exception {
		double [] scores = {-1,-1, -1, -1};
		try {
			int nExons = exonList.size();	
			int exonCov=0;
			double exonFrac=0;
			
			for (int i=0; i<nExons; i++) {
				Exon ex = exonList.get(i);
				
				for (Hit mh : mergeSet) {
					int olap = Arg.pOlapOnly(mh.hStart[ix], mh.hEnd[ix], ex.estart, ex.eend);
					if (olap!=0) {
						exonCov += olap;
						exonFrac += (double) olap/(double)(ex.eend-ex.estart+1);
					}
				}
			}
			int geneCov = 0, hitStart=Integer.MAX_VALUE, hitEnd=0; 							
			for (Hit mh : mergeSet) {
				hitStart = Math.min(hitStart, mh.hStart[ix]);
				hitEnd   = Math.max(hitEnd, mh.hEnd[ix]);
				geneCov += Arg.pOlapOnly(mh.hStart[ix], mh.hEnd[ix], gStart, gEnd);
			}
			// exonFrac is often ~exonCov except when the cover is mainly over one exon and the rest are not covered
			scores[0] = (exonFrac>0 && nExons>0) ?    (exonFrac*100.0/(double)nExons)  : 0.0;
			scores[1] = (exonCov>0 && exonSumLen>0) ? (((double) exonCov/ (double) exonSumLen) * 100.0) : 0.0; 
			scores[2] = (geneCov>0 && gLen>0) ?       ((double) geneCov/ (double) gLen) * 100.0 : 0.0; 
			scores[3] = exonCov;
			return scores;
		}
		catch (Exception e) {ErrorReport.print(e, "Score exons");  throw e;}
	}
	
	// No paired Y gene and bin=0; will be sorted by Y in calling routine
	protected ArrayList <Hit> getHitsForXgene(int X, int Y, boolean isEQ) throws Exception { // GrpPairGx.runG1
	try {
		if (X!=ix) symap.Globals.dtprt("anchor2.Gene Wrong T/Q ");
		if (gHitList.size()==0) return null;
		
		ArrayList <Hit> binList = new ArrayList <Hit> ();
		for (Hit ht : gHitList) {
			if (ht.bin==0 && ht.isStEQ==isEQ && !ht.bBothGene()) {
				if      (X==Q && ht.bHasQgene(this))  binList.add(ht);
				else if (X==T && ht.bHasTgene(this)) binList.add(ht);
				else symap.Globals.dtprt(geneTag + " has hit that does not have supposed genes");
			}
		}
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
	
	/////////////////////////////////////////////////////////////////
	protected String toResults() {	
		if (gHitList.size()==0) return "";  
		
		int iy = (ix==Arg.T) ? Arg.Q : Arg.T;
		Hit.sortBySignByX(iy, gHitList);
		
		String hits="";
		Hit last=null;
		for (Hit ht : gHitList) {
			int be = ht.getExonCov(ix, this);
			String   e = (be>0) ? " E " : " I ";
			hits +=  e + ht.toDiff(last) + "\n";
			last = ht;
		}
		hits = "\n" + hits;
		
		String hprList= " HPR:" + gHprList.size(); // may be clear from prtToFile (unless not crossRefClear run)
		for (HitPair hpr : gHprList) hprList += "\n" + hpr.toResultsGene() + " " + hpr.note;
		
		return toBrief() + hprList + hits + "\n";
	}
	protected String toBrief() {
		String gCoords = String.format("Gene [%s%,d-%,d %5s] #Ex%d ",strand, gStart, gEnd, Arg.int2Str(gEnd-gStart+1), exonList.size());
		String hCoords = (gHitList.size()>0) ?
				String.format("Hits [%,d-%,d %5s] #Ht%d ", hitsStart,hitsEnd, Arg.int2Str(hitsEnd-hitsStart+1), gHitList.size())
				: " No hits ";
		return toName() + gCoords + hCoords;
	}
	
	protected String toName() {return  Arg.side[ix]+ "#" + geneTag + " ("  + geneIdx + ") " + chr + " ";}
	
	public String toString() {return toResults();}

	public void clear() {
		gHitList.clear();
		exonList.clear();
		gHprList.clear();
		
		hitsStart=Integer.MAX_VALUE; 
		hitsEnd=0;
	}
	//////////////////////////////////////////////////////////////////
	protected class Exon {
		protected Exon(int h1, int h2) {this.estart=h1; this.eend=h2;}
		protected int estart, eend;
	}
}
