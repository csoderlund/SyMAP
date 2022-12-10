package backend;

// Hit class used for the synteny computations
public class SyHit 
{
	int mPos1;
	int mPos2;
	int mIdx;		// index of the hit in the database table
	int mI; 		// index of the hit in the sorted list
	int mPctID;
	
	int mScore = 0;
	int mPos1_alt = 0;
	HitType mBT = HitType.NonGene;
	
	// parameters used during dynamic programming
	int mDPScore;	// DP score of longest chain terminating here
	int mPrevI;		// predecessor in longest chain
	int mNDots;		// number of hits in longest chain
	
	// indicates that the hit has been returned in a chain in the current
	// iteration of the dynamic programming
	boolean mDPUsed 	= false;
	
	// indicates that the hit has been put into a block and should not
	// be used in any future DP iterations
	boolean mDPUsedFinal = false;
	
	public SyHit(int pos1, int pos2, int idx, int pctid, int bt)
	{
		mPos1 = pos1;
		mPos2 = pos2;
		mIdx = idx;
		
		mPctID = pctid;
		if (bt == 2)
		{
			mBT = HitType.GeneGene;
		}
		else if (bt == 1)
		{
			mBT = HitType.GeneNonGene;
		}		
	}
}
