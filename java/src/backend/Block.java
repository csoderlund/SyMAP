package backend;

import java.util.Vector;
import java.util.TreeSet;

import util.Logger;

/**************************************
 * Used by SyntenyMain: represent blocks
 */
enum BCase {A,B,C,D,Reject};

public class Block 
{
	protected int mS1, mE1, mS2, mE2;
	
	protected float mCorr1, mCorr2;
	protected BCase mCase = BCase.Reject;
	
	protected int mGrpIdx1, mGrpIdx2;
	protected int mPairIdx = 0;
	protected Project mProj1, mProj2;
	
	protected int mIdx, mNum=0;
	protected Vector<SyHit> mHits;
	
	private TreeSet<Integer> mHitIdxSet; // quick search for mHits
	
	public Block(int pidx, Project proj1, Project proj2) {
		mHits = new Vector<SyHit>();
		mHitIdxSet = new TreeSet<Integer>();
		mPairIdx = pidx;
		mProj1 = proj1;
		mProj2 = proj2;
	}
	
	public void clear() {
		mS1 = mS2 = 0;
		mE1 = mE2 = 0;
		mGrpIdx1 = mGrpIdx2 = 0;
		if (mHits != null) mHits.clear();		
	}
	public void addHit(int s1, int e1, int s2, int e2) { // CAS533 move assignments from SyntenyMain
		mS1 = s1;
		mE1 = e1;
		mS2 = s2;
		mE2 = e2;
	}
	
	public void mergeWith(Block b) {
		assert mGrpIdx1 == b.mGrpIdx1;
		assert mGrpIdx2 == b.mGrpIdx2;
	
		mS1 = Math.min(mS1,b.mS1);
		mS2 = Math.min(mS2,b.mS2);
		mE1 = Math.max(mE1,b.mE1);
		mE2 = Math.max(mE2,b.mE2);

		mCorr1 = (mHits.size() >= b.mHits.size() ? mCorr1 : b.mCorr1);
		mCorr2 = (mHits.size() >= b.mHits.size() ? mCorr2 : b.mCorr2);
		
		for (SyHit h : b.mHits) {
			if (!mHitIdxSet.contains(h.mIdx)) { // CAS533 was separate method
				mHitIdxSet.add(h.mIdx);
				mHits.add(h);
			}
		}
	}
	public boolean isContained(int pos1, int pos2) {// CAS533 move check from SyntenyMain
		return (pos1 >= mS1 && pos1 <= mE1 &&
				pos2 >= mS2 && pos2 <= mE2);
	}
	// CAS533 was using pos1 and pos2 for block coordinates; call right before saving to DB
	public void updateCoords() { 
		for (SyHit h : mHits) {
			if (h.s1<mS1) mS1=h.s1;
			if (h.e1>mE1) mE1=h.e1;
			if (h.s2<mS2) mS2=h.s2;
			if (h.e2>mE2) mE2=h.e2;
		}
	}
	public double avgPctID() {
		double avg = 0;
		for (SyHit h : mHits) avg += h.mPctID;
		avg /= mHits.size();
		avg = (Math.round(10*avg));
		return avg/10;
	}
	public String getBlockSql() {// CAS533 move most of method to SyntenyMain prepareStatements
		String st = "SELECT idx FROM blocks WHERE pair_idx=" + mPairIdx +
				" AND blocknum=" + mNum +
				" AND proj1_idx=" + mProj1.getIdx() +
				" AND grp1_idx=" + mGrpIdx1 +
				" AND proj2_idx=" + mProj2.getIdx() +
				" AND grp2_idx=" + mGrpIdx2 +
				" AND start1=" + mS1 +
				" AND end1=" + mE1 +
				" AND start2=" + mS2 +
				" AND end2=" + mE2;
		return st;
	}
	public void print(Logger log, String prefix){
		log.msg(prefix + " score:" + mHits.size() + " corr:" + mCorr1 + " corr2:" + mCorr2 + " s1:" + mS1 + " e1:" + mE1 + " s2:" + mS2 + " e2:" + mE2);
	}
}
