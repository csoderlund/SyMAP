package backend;

import java.sql.SQLException;

public class FPCContig 
{
	int mIdx;
	int mNum;
	float mPos;
	int mSize;
	int mCCB;
	Group mGrp;
	Project mProj;
	String mGrpName;
	String mRmk;
	int mGrpIdx;
	int mProjIdx;
	
	// Constructor when called from anchors/synteny
	public FPCContig(int idx, int num, float anchor_pos, int size, int ccb, Group grp, Project proj)
	{
		mIdx = idx;
		mNum = num;
		mPos = anchor_pos;
		mSize = size;
		mCCB = ccb;
		mGrp = grp;
		mProj = proj;		
	}

	// Constructor when called from data load
	public FPCContig(int num,  String grpName, float anchor_pos,String rmk, int pidx)
	{
		mNum = num;
		mPos = anchor_pos;
		mGrpName = grpName;
		mRmk = rmk;
		mProjIdx = pidx;
	}
	
	public void doUpload(UpdatePool pool) throws SQLException
	{
		String st = "INSERT INTO contigs VALUES('0','" + mProjIdx + "','" + mNum + "','" + mGrpIdx + 
			"','" +  mPos + "','" + mSize + "','" + mCCB + "','" + mRmk +  "')"; 
		pool.executeUpdate(st);
		
		//mIdx = pool.lastID(); // mdb removed 7/1/09
		// mdb added 7/1/09
		st = "SELECT idx FROM contigs WHERE proj_idx=" + mProjIdx + " AND number=" + mNum + " AND grp_idx=" + mGrpIdx;
		mIdx = pool.getIdx(st);
	}
}
