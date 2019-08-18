package backend;

import java.util.Vector;
import java.sql.SQLException;

// Represents one alignment of contig to pseudomolecule ("contig hit window")
// These are either extracted from group/group blocks, or computed for
// individual unanchored contigs

public class CtgHit 
{
	int mCtgIdx1;
	int mGrpIdx2;
	int mS1=Integer.MAX_VALUE, mS2 = Integer.MAX_VALUE;
	int mE1=0,mE2=0;
	int mScore=0;
	String mCorr;
	Vector<SyHit> mHits;
	int mPairIdx;
	int mBlockNum;
	int mIdx = 0;
	int mCtgSize = 0;
	
	// This one is used for anchored blocks
	public CtgHit(int cidx1, int gidx2, int pidx)
	{		
		mCtgIdx1 = cidx1;
		mGrpIdx2 = gidx2;
		mPairIdx = pidx;
		mHits = new Vector<SyHit>();
		assert(mGrpIdx2 > 0);

	}
	
	// This one is used for making the unanchored blocks
	public CtgHit(int cidx1, int s2, int e2, int score, int csize)
	{		
		mCtgIdx1 = cidx1;
		mS2 = s2;
		mE2 = e2;
		mScore = score;
		mCtgSize = csize;
	}	
	public void addHit(SyHit h)
	{
		mHits.add(h);
		mS1 = Math.min(mS1, h.mPos1);
		mE1 = Math.max(mE1, h.mPos1);
		mS2 = Math.min(mS2, h.mPos2);
		mE2 = Math.max(mE2, h.mPos2);
		
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
		
		vals.add(String.valueOf(mCtgIdx1));
		vals.add(String.valueOf(mS1));
		vals.add(String.valueOf(mE1));

		vals.add(String.valueOf(mGrpIdx2));
		vals.add("0");
		vals.add(String.valueOf(mS2));
		vals.add(String.valueOf(mE2));
		
		vals.add(String.valueOf(mHits.size()));
		
		vals.add(mCorr);
		vals.add(String.valueOf(mBlockNum));
		double avgPctID = avgPctID();
		vals.add("Avg %ID:" + String.valueOf(avgPctID));

		pool.singleInsert("ctghits", vals);
		
		//mIdx = pool.lastID(); // mdb removed 7/1/09
		// mdb added 7/1/09
		String st = "SELECT idx FROM ctghits WHERE pair_idx=" + mPairIdx +
						" AND ctg1_idx=" + mCtgIdx1 +
						" AND grp2_idx=" + mGrpIdx2 +
						" AND block_num=" + mBlockNum +
						" AND start1=" + mS1 +
						" AND end1=" + mE1 +
						" AND start2=" + mS2 +
						" AND end2=" + mE2;
		mIdx = pool.getIdx(st);
		
		// Now we have to update the block hits tables
		for (SyHit h : mHits)
		{
			Vector<String> vlist = new Vector<String>();
			vlist.add(String.valueOf(h.mIdx));
			vlist.add(String.valueOf(mIdx));
			
			String key = "";
			switch (h.mType)
			{
				case Bes:
					key = "bes_block_hits";
					break;
				case Mrk:
					key = "mrk_block_hits";
					break;
				default:
					assert(false);
			}
			pool.bulkInsertAdd(key, vlist);
		}
		pool.finishBulkInserts();
	}
}
