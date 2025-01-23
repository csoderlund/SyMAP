package backend.synteny;

import java.util.Vector;
import java.util.TreeSet;

/**************************************
 * Used by SyntenyMain: represent blocks
 */

public class SyBlock  {
	static protected int cntBlk=1; // temporary to figure out how many blocks are create/disposed
	protected int nBlk=0;
	
	protected int mS1, mE1, mS2, mE2; // mid-point, until ready to save - than exact 
	protected float mCorr1, mCorr2;
	protected String mCase = "R";
	
	protected int mGrpIdx1, mGrpIdx2, mNum=0; // Finalize
	protected int mIdx;						  // Save
	
	protected Vector<SyHit> mHits;
	private TreeSet<Integer> mHitIdxSet; // quick search for mHits
	
	protected SyBlock() {
		mHits = new Vector<SyHit>();
		mHitIdxSet = new TreeSet<Integer>();
		nBlk = cntBlk++;
	}
	protected void clear() { // CAS560 add so can reuse blk 
		mHits.clear();
		mHitIdxSet.clear();
		mS1=mE1=mS2=mE2=0;
		mCorr1=mCorr2=0;
	}
	protected void setEnds(int s1, int e1, int s2, int e2) { // CAS533 move assignments from SyntenyMain
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
			if (!mHitIdxSet.contains(h.mIdx)) { // CAS533 was separate method
				mHitIdxSet.add(h.mIdx);
				mHits.add(h);
			}
		}
	}
	protected boolean isContained(int pos1, int pos2) {// CAS533 move check from SyntenyMain
		return (pos1 >= mS1 && pos1 <= mE1 &&
				pos2 >= mS2 && pos2 <= mE2);
	}
	// CAS533 was using mPos1 and mPos2 for block coordinates; call right before saving to DB
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
}
