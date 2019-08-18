package backend;

import java.util.HashSet;
import java.util.Set;
import java.util.Vector;
import java.util.Comparator;
import java.util.Collections;

// Represents a group of binned hits along a pseudomolecule.
// Or the set of hits of a particular query (e.g., marker, bes, gene).

enum HitType {GeneGene, GeneNonGene, NonGene}
enum GeneType {Gene, NonGene, NA}

public class HitBin 
{
	int start, end;
	Vector<Hit> mHits;
	int mHitsConsidered = 0;
	int mMinMatch = 0;
	int mTopN = 0;
	QueryType mQT;
	GeneType mGT = GeneType.NA;
	static HashSet<HitType> mKeepTypes = null;
	//static HashSet<BinType> mExcludeTypes = null;
	
	public HitBin(AnnotElem ae, int topn, QueryType qt)
	{
		start = ae.start;
		end = ae.end;
		mGT = ae.mGT;
		mQT = qt;
		mTopN = topn;
		mHits = new Vector<Hit>(topn,2); // mdb added vector capacity/increment 8/13/09
	}
	public HitBin(Hit h, SubHit sh, int topn, QueryType qt)
	{
		start = sh.start;
		end = sh.end;
		mTopN = topn;
		mHits = new Vector<Hit>(topn,2); // mdb added vector capacity/increment 8/13/09
		mHits.add(h);
		mMinMatch = h.matchLen;
		mQT = qt;
		mHitsConsidered = 1;
	}
	public static void initKeepTypes()
	{
		if (mKeepTypes == null)
		{
			mKeepTypes = new HashSet<HitType>();	
		}
	}
	public static void addKeepTypes(HitType bt)
	{
		mKeepTypes.add(bt);
	}	
	public void checkAddHit(Hit hit, SubHit sh)
	{
		// Add hit to the bin,if not already excluded by TopN
		//Log.msg("Check add hit match " + hit.mMatch + " to binsize " + mHits.size() + " match " + mMinMatch);
		
		mHitsConsidered++;
		boolean keep = (mKeepTypes.contains(hit.mBT));
		if (keep || mHits.size() <= mTopN || mMinMatch < hit.matchLen || mTopN == 0) 
		{
			mHits.add(hit);
			start = Math.min(start, sh.start);
			end = Math.max(end, sh.end);
			Collections.sort(mHits, new CompareByMatch());
			if (mTopN > 0 )
			{		
				// Now drop the lowest hit, unless we're forcing it to be kept
				// Or unless its score is 80% of the topN score.
				if (mHits.size() >= mTopN + 2)
				{
					int lastPos = mHits.size() - 1;
					int topNscore = mHits.get(mTopN-1).matchLen;
					int curScore = mHits.get(lastPos).matchLen;
					if (!mKeepTypes.contains(mHits.get(lastPos).mBT))
					{
						Hit h = mHits.get(lastPos);
						if (curScore < .8*topNscore || !h.fullAnnot()) // WMNTOPN
						{
							mHits.remove(lastPos);
						}
					}
				}
			}
			mMinMatch = mHits.get(mHits.size()-1).matchLen;
		}
	}
	public boolean addHit(Hit hit, SubHit sh)
	{
		// Add hit to the bin for later filtering
		
		mHitsConsidered++;
		mHits.add(hit);
		//System.out.println("HB ADD " + sh.start + "_" + sh.end + " " + start + "_" + end + " " + mGT.toString());
		//start = Math.min(start, sh.start);
		//end = Math.max(end, sh.end);
		
		return true;
	}	
	// Keep TopN hits, subject to being at least 25% above the background (=TopN+1 hit)
	// WMN - dropping the 25% thing, 9/27/10
	// WMN - adding possibility of keep hits within 80% score of the topN hit, for gene families
	public void filterHits(Set<Hit> hits, boolean keepFamilies)
	{
		Collections.sort(mHits, new CompareByMatch());

		int thresh = 0;
		if (mTopN > 0 && mHits.size() >= mTopN)
		{
			thresh = mHits.get(mTopN-1).matchLen;
			if (keepFamilies) thresh *= .8;
		}
		//if (mBT == BinType.GeneGene) thresh = 0;
		for (int i = 0; i < mHits.size(); i++)
		{
			Hit h = mHits.get(i);
			if (h.mBT == null || !mKeepTypes.contains(h.mBT))
			{
				if (mTopN > 0 && i >= mTopN) 
				{
					if (h.matchLen < thresh || !h.fullAnnot()) // WMNTOPN
					{
						continue;
					}
				}
				//WMN if (mHits.get(i).matchLen < thresh) continue;
			}
			switch (mQT)
			{
				case Query:
					h.query.status = HitStatus.In;
					hits.add(h);
					Utils.incStat("TopNQueryKeep" + mGT.toString(), 1);
					h.binsize1 = mHitsConsidered;
					break;
				case Target:
					h.target.status = HitStatus.In;
					hits.add(h);
					Utils.incStat("TopNTargetKeep" + mGT.toString(), 1);
					h.binsize2 = mHitsConsidered;
					break;
				default:
					break;
			}			
		}
		mHits.clear();
		mHits = null;
	}

	private static class CompareByMatch implements Comparator<Hit>
	{
		public int compare(Hit h1, Hit h2)
		{	
			if (h2.matchLen != h1.matchLen)
			{
				return h2.matchLen - h1.matchLen;	
			}
			else return h1.compareTo(h2); 
		}
	}
}
