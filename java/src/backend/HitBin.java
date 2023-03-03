package backend;

import java.util.Set;
import java.util.HashSet;
import java.util.Vector;
import java.util.Comparator;
import java.util.Collections;

/*****************************************************************
// Represents a group of binned hits along a group.
// Or the set of hits of a particular query (e.g. gene).
**********************************************************/

public class HitBin  {
	public int start, end;  
	public  GeneType  mGT = GeneType.NA;// AnchorsMain {Gene, NonGene, NA}
	private QueryType mQT;				// AnchorsMain {Query, Target, Either };
	private int mTopN=0;
	
	public int mHitsConsidered = 0, nHitsRemoved = 0;
	
	private Vector<Hit> mHits;
	private int mMinMatch = 0;
	
	private static HashSet<HitType> mKeepTypes = new HashSet<HitType>(); // always empty, but fails without it
	
	// Group created from annotation
	public HitBin(int start, int end, GeneType mGT, int topn, QueryType qt) {
		this.start = start;
		this.end = end;
		this.mGT = mGT;
		this.mQT = qt;
		this.mTopN = topn;
		
		mHits = new Vector<Hit>(topn,2); 
	}
	// Group
	public HitBin(Hit h, SubHit sh, int topn, QueryType qt) {
		start = sh.start;
		end = sh.end;
		mTopN = topn;
		mHits = new Vector<Hit>(topn,2); 
		mHits.add(h);
		mMinMatch = h.matchLen;
		mQT = qt;
		mHitsConsidered = 1;
	}
	/****************************************************************
	// Called from Group; Add hit to the bin, unless excluded by TopN
	 ************************************************************/
	public void checkAddHit2(Hit hit, SubHit sh) {
		mHitsConsidered++;
		boolean keep = (mKeepTypes.contains(hit.mHT)); // this is always false, but when remove, does not work
		if (keep || mHits.size() <= mTopN || mMinMatch < hit.matchLen || mTopN == 0) {
			mHits.add(hit); 
			
			start = Math.min(start, sh.start);
			end =   Math.max(end, sh.end);
			Collections.sort(mHits, new CompareByMatch());
			
			if (mTopN > 0 ) {		
				// Now drop the lowest hit, unless we're forcing it to be kept
				// Or unless its score is 80% of the topN score.
				if (mHits.size() >= mTopN + 2) {
					int lastPos = mHits.size() - 1;
					int topNscore = mHits.get(mTopN-1).matchLen;
					int curScore = mHits.get(lastPos).matchLen;
					if (!mKeepTypes.contains(mHits.get(lastPos).mHT)) { // always true 
						Hit h = mHits.get(lastPos);
						if (curScore < Constants.perBin*topNscore || !h.fullAnnot()) {
							mHits.remove(lastPos);
							nHitsRemoved++; //
						}
					}
				}
			}
			mMinMatch = mHits.get(mHits.size()-1).matchLen;
		}
	}
	/******************************************************************
	// Keep TopN hits 
	// called by Group
	// keep hits within 80% score of the topN hit, for gene families
	 ****************************************************************/
	public void filterHits(Set<Hit> hits) {
		if (mHits.size()==0) return; // CAS500 
		
		Collections.sort(mHits, new CompareByMatch());
		
		int thresh = 0;
		if (mTopN > 0 && mHits.size() >= mTopN) {
			thresh = mHits.get(mTopN-1).matchLen;
			thresh *= Constants.perBin;
		}
	
		for (int i = 0; i < mHits.size(); i++) {
			Hit h = mHits.get(i);
			if (h.mHT == null || !mKeepTypes.contains(h.mHT)) {// BT HitType genegene, genenongene, nongene
				if (mTopN > 0 && i >= mTopN) {
					if (h.matchLen < thresh || !h.fullAnnot()) // TOPN
						continue;
				}
			}
			switch (mQT){
				case Query:
					h.query.status = HitStatus.In;
					h.binsize1 = mHitsConsidered;
					hits.add(h);
					BinStats.incStat("TopNQueryKeep" + mGT.toString(), 1);
					break;
				case Target:
					h.target.status = HitStatus.In;
					h.binsize2 = mHitsConsidered;
					hits.add(h);
					BinStats.incStat("TopNTargetKeep" + mGT.toString(), 1);
					break;
				default:
					break;
			}			
		}
		
		mHits.clear();
		mHits = null;
	}
	public int getnHits() { return mHits.size();}
	
	private static class CompareByMatch implements Comparator<Hit> {
		public int compare(Hit h1, Hit h2) {	
			if (h2.matchLen != h1.matchLen) {
				return h2.matchLen - h1.matchLen;	
			}
			else return h1.compareTo(h2); 
		}
	}
}
