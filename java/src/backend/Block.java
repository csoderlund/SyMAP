package backend;

import java.util.Vector;
import java.util.TreeSet;
import java.sql.SQLException;

import util.Logger;

enum BCase {A,B,C,D,Reject};
enum BlockType {CtgPseudo, GrpPseudo, PseudoPseudo};

public class Block 
{
	int mS1, mE1, mS2, mE2;
	Vector<SyHit> mHits;
	TreeSet<Integer> mHitIdxSet;
	float mCorr1, mCorr2;
	BCase mCase = BCase.Reject;
	String mCtgs1 = "";
	int mGrpIdx1;
	int mGrpIdx2;
	BlockType mType;
	int mNum=0;
	int mIdx;
	Vector<CtgHit> mCtgHits;
	int mPairIdx = 0;
	Project mProj1;
	Project mProj2;
	
	public Block(int pidx, Project proj1, Project proj2)
	{
		mHits = new Vector<SyHit>();
		mHitIdxSet = new TreeSet<Integer>();
		mPairIdx = pidx;
		mProj1 = proj1;
		mProj2 = proj2;
	}
	
	public Block(int s1, int e1, int s2, int e2, int gidx1, int gidx2,
				int pidx, Project proj1, Project proj2, int bnum, String ctgstr)
	{
		mS1 = s1;
		mS2 = s2;
		mE1 = e1;
		mE2 = e2;
		mGrpIdx1 = gidx1;
		mGrpIdx2 = gidx2;
		mHits = new Vector<SyHit>();
		mHitIdxSet = new TreeSet<Integer>();
		mPairIdx = pidx;
		mProj1 = proj1;
		mProj2 = proj2;	
		mNum = bnum;
		mCtgs1 = ctgstr;
	}

	public void clear()
	{
		mS1 = 0;
		mS2 = 0;
		mE1 = 0;
		mE2 = 0;
		mGrpIdx1 = 0;
		mGrpIdx2 = 0;
		if (mHits != null) mHits.clear();		
	}
	
	public void print(Logger log, String prefix)
	{
		log.msg(prefix + " score:" + mHits.size() + " corr:" + mCorr1 + " corr2:" + mCorr2 + " s1:" + mS1 + " e1:" + mE1 + " s2:" + mS2 + " e2:" + mE2);
	}
	
	public void addHit(SyHit h)
	{
		if (!mHitIdxSet.contains(h.mIdx))
		{
			mHitIdxSet.add(h.mIdx);
			mHits.add(h);
		}
	}
	
	public void mergeWith(Block b)
	{
		assert mGrpIdx1 == b.mGrpIdx1;
		assert mGrpIdx2 == b.mGrpIdx2;
		assert mType == b.mType;
	
		mS1 = Math.min(mS1,b.mS1);
		mS2 = Math.min(mS2,b.mS2);
		mE1 = Math.max(mE1,b.mE1);
		mE2 = Math.max(mE2,b.mE2);

		mCorr1 = (mHits.size() >= b.mHits.size() ? mCorr1 : b.mCorr1);
		mCorr2 = (mHits.size() >= b.mHits.size() ? mCorr2 : b.mCorr2);
		
		for (SyHit h : b.mHits)
		{
			addHit(h);
		}
	}
	
	public double avgPctID()
	{
		double avg = 0;
		for (SyHit h : mHits)
		{
			avg += h.mPctID;
		}
		avg /= mHits.size();
		avg = (Math.round(10*avg));
		return avg/10;
	}
	
	public void doUpload(UpdatePool pool) throws SQLException
	{
		Vector<String> vals = new Vector<String>();
		
		vals.add("0");
		vals.add(String.valueOf(mPairIdx));
		vals.add(String.valueOf(mNum));
		
		vals.add(String.valueOf(mProj1.getIdx()));
		vals.add(String.valueOf(mGrpIdx1));
		vals.add(String.valueOf(mS1));
		vals.add(String.valueOf(mE1));
		vals.add(mCtgs1);

		vals.add(String.valueOf(mProj2.getIdx()));
		vals.add(String.valueOf(mGrpIdx2));
		vals.add(String.valueOf(mS2));
		vals.add(String.valueOf(mE2));
		vals.add("");
		
		vals.add("0");
		vals.add("0");
		double avgPctID = avgPctID();
		vals.add("Avg %ID:" + String.valueOf(avgPctID));
		vals.add(String.valueOf(mCorr1));
		vals.add(String.valueOf(mHits.size()));
		vals.add("0"); // start gene fraction entries
		vals.add("0");
		vals.add("0");
		vals.add("0"); // end gene fraction entries
		pool.singleInsert("blocks", vals);
		
		//mIdx = pool.lastID(); // mdb removed 7/1/09
		// mdb added 7/1/09
		String st = "SELECT idx FROM blocks WHERE pair_idx=" + mPairIdx +
						" AND blocknum=" + mNum +
						" AND proj1_idx=" + mProj1.getIdx() +
						" AND grp1_idx=" + mGrpIdx1 +
						" AND proj2_idx=" + mProj2.getIdx() +
						" AND grp2_idx=" + mGrpIdx2 +
						" AND start1=" + mS1 +
						" AND end1=" + mE1 +
						" AND start2=" + mS2 +
						" AND end2=" + mE2 +
						" AND ctgs1='" + mCtgs1 + "'";
		mIdx = pool.getIdx(st);
	}
}
