package backend;

import java.util.Vector;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Collections;

/*****************************************************************
// Represents a group of binned cluster hits along a group.
// Or the set of hits of a particular query (e.g. gene).
 * CAS540 TopN is no longer allowed to be 0; Hit.useAnnot 
**********************************************************/

public class HitBin  {
	private final static double perBin=0.8;
	public int start, end;  
	public  GeneType  mGT = GeneType.NA;// AnchorsMain {Gene, NonGene, NA}
	private QueryType mQT;				// AnchorsMain {Query, Target, Either };
	private int mTopN=0;
	
	public int mHitsConsidered = 0, nHitsRemoved = 0;
	
	private Vector<Hit> mHits;
	private int mMinMatch = 0; // lowest matchLen of mHits; will not consider anything lower
	
	// Group.createHitBin1; place holders
	public HitBin(int start, int end, GeneType mGT, int topn, QueryType qt) {
		this.start = start;
		this.end   = end;
		this.mGT = mGT;
		this.mQT = qt;
		this.mTopN = topn;
		
		mHits = new Vector<Hit>(topn,2); 
	}
	// Group.filterAddToHitBin2
	public HitBin(Hit h, int start, int end, int topn, QueryType qt) {
		this.mMinMatch = h.matchLen;
		this.start = start;
		this.end   = end;
		this.mTopN = topn;
		this.mQT = qt;
		
		mHits = new Vector<Hit>(topn,2); 
		mHits.add(h);
		
		mHitsConsidered = 1;
	}
	/****************************************************************
	 * Called from Group [checkAddHit2]
	 ************************************************************/
	public void filterAddHit2(Hit hit, int shStart, int shEnd) {
		mHitsConsidered++;
		
		if (mHits.size() >= mTopN && hit.matchLen < mMinMatch && !hit.useAnnot()) return;
		
		mHits.add(hit); 
		start = Math.min(start, shStart);
		end =   Math.max(end,   shEnd);
		Collections.sort(mHits, new CompareByMatch()); // ascending matchLen

		if (mHits.size() >= mTopN+2) { 
		
			int topNmatch = mHits.get(mTopN-1).matchLen; // if Top2=2, then 2nd highest length
			topNmatch     = (int) Math.round(topNmatch * perBin); 
	
			for (int i = mHits.size()-1; i>=mTopN; i--) { // CAS540 was if; try to remove at least one
				Hit h = mHits.get(i);
				
				if (h.matchLen <= topNmatch || !h.useAnnot()) { // making this an && explodes the number of hits
					mHits.remove(i);
					nHitsRemoved++; 
					break;
				}
			}
		}
		mMinMatch = mHits.get(mHits.size()-1).matchLen; 
	}
	public int getnHits() { if (mHits==null) return -1; return mHits.size();}
	
	/******************************************************************
	 * AnchorMain.filterHitsFinal -> Group.filterAddHits -> add to HashSet hits
	 * The mHits were filtered above during 2nd scan; they are further filtered here with same filters
	 * Keep mTopN by length, fullAnnot regardless, 80% of length
	 ****************************************************************/
	public void filterFinalAddHits(HashSet<Hit> addToHits) {
		int nHits = mHits.size();
		if (nHits==0) return; // CAS500 
		
		Collections.sort(mHits, new CompareByMatch()); // ascending matchLen
		
		int thresh = 0;
		if (nHits >= mTopN) {
			thresh = mHits.get(mTopN-1).matchLen;
			thresh *= perBin; // 0.8 so keep 80% of mTopN matchLen
		}
	
		for (int i = 0; i < nHits; i++) {
			Hit h = mHits.get(i);
			
			if (!h.useAnnot()) {
				if (h.nSubHits==1 && h.matchLen<=Hit.haveLen2) continue; // CAS540 spurious hit
				if (i >= mTopN    && h.matchLen< thresh) continue;
			}
		
			switch (mQT){
				case Query:
					h.queryHits.status = HitStatus.In;
					h.binsize1 = mHitsConsidered;
					addToHits.add(h); 
					BinStats.incStat("TopNQueryKeep" + mGT.toString(), 1);
					break;
				case Target:
					h.targetHits.status = HitStatus.In;
					h.binsize2 = mHitsConsidered;
					addToHits.add(h);
					BinStats.incStat("TopNTargetKeep" + mGT.toString(), 1);
					break;
				default:
					break;
			}			
		}
		mHits.clear();
	}
	public String getInfo() {return String.format("Hit bin  %,10d %,10d  %4d", start, end, mHits.size());}
	
	private static class CompareByMatch implements Comparator<Hit> {
		public int compare(Hit h1, Hit h2) {	
			if (h2.matchLen != h1.matchLen) {
				return h2.matchLen - h1.matchLen;	
			}
			else return h1.compareTo(h2); 
		}
	}
}
