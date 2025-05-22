package backend.synteny;

import java.util.Vector;

import symap.Globals;

import java.util.TreeSet;

/**************************************
 * Block class used for the synteny computations
 */
public class SyBlock  implements Comparable <SyBlock>{
	static protected int cntBlk=1;  
	protected int nBlk=0;		   // temporary unique
	
	protected String orient="";		  // "", "==", "!=", needed so not merged; CAS567
	protected int mS1, mE1, mS2, mE2; // mid-point, until ready to save - than exact 
	protected float mCorr1, mCorr2;	  // chain hits, all hits in block
	protected String mCase = "R";	  // R=fail, anything else pass
	
	protected int mGrpIdx1, mGrpIdx2, mNum=0; // Finalize
	protected int mIdx;						  // Save
	
	protected Vector<SyHit> mHits;		 // Block hits
	private TreeSet<Integer> mHitIdxSet; // quick search for mHits in Merge

	protected SyBlock(String orient) { 
		mHits = new Vector<SyHit>();
		mHitIdxSet = new TreeSet<Integer>();
		this.orient = orient;
		nBlk = cntBlk++;
	}
	protected void clear() { 
		mHits.clear();
		mHitIdxSet.clear();
		mS1=mE1=mS2=mE2=0;
		mCorr1=mCorr2=0;
	}
	protected void setEnds(int s1, int e1, int s2, int e2) { 
		mS1 = s1; mE1 = e1;
		mS2 = s2; mE2 = e2;
	}
	protected void addHit(SyHit h) {mHits.add(h);}
	
	protected void mergeWith(SyBlock b) {
		mS1 = Math.min(mS1, b.mS1);
		mS2 = Math.min(mS2, b.mS2);
		mE1 = Math.max(mE1, b.mE1);
		mE2 = Math.max(mE2, b.mE2);

		mCorr1 = (mHits.size() >= b.mHits.size() ? mCorr1 : b.mCorr1);
		mCorr2 = (mHits.size() >= b.mHits.size() ? mCorr2 : b.mCorr2);
		
		for (SyHit h : b.mHits) {
			if (!mHitIdxSet.contains(h.mIdx)) { 
				mHitIdxSet.add(h.mIdx);
				mHits.add(h);
			}
		}
	}
	protected boolean isContained(int pos1, int pos2) {
		return (pos1 >= mS1 && pos1 <= mE1 &&
				pos2 >= mS2 && pos2 <= mE2);
	}
	
	protected void updateCoords() { 
		for (SyHit h : mHits) {
			if (h.s1<mS1) mS1=h.s1;
			if (h.e1>mE1) mE1=h.e1;
			if (h.s2<mS2) mS2=h.s2;
			if (h.e2>mE2) mE2=h.e2;
		}
	}
	protected double avgPctID() { // to save in DB
		double avg = 0;
		for (SyHit h : mHits) avg += h.mPctID;
		avg /= mHits.size();
		avg = (Math.round(10*avg));
		return avg/10;
	}
	protected int getNum() {
		int m = (mNum==0) ? nBlk : mNum;
		return m;
	}
	protected void tprt() { // CAS567 add
		int n=mHits.size();
		int m = (mNum==0) ? nBlk : mNum;
		String msg = String.format("Block #%-3d %2s %s  Hits %-4d  %6.3f %6.3f  Avg %,8d  %,8d   Len %,10d   %,10d", 
				m, orient, mCase, n, mCorr1, mCorr2, 
				(int)((mE1-mS1)/n),(int)((mE2-mS2)/n), (mE1-mS1), (mE2-mS2));
		Globals.prt(msg);
	}
	public int compareTo (SyBlock b2) {// CAS567 add for orient
		if (mS1 != b2.mS1) 	return mS1 - b2.mS1;
		else				return mE1 - b2.mE1;
	}
}
