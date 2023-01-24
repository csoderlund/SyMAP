package backend;

// Hit class used for the synteny computations
public class SyHit 
{
	protected int s1, e1, s2, e2; // CAS533 add
	protected int mPos1;	// (h.start1+h.end1)/2
	protected int mPos2;	// (h.start2+h.end2)/2
	protected int mIdx;		// index of the hit in the database table
	protected int mI; 		// index of the hit in the sorted list
	protected int mPctID;
	
	protected int mScore = 0;
	protected int mPos1_alt = 0;
	protected HitType mBT = HitType.NonGene;
	
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
	
	public SyHit(int s1, int e1, int s2, int e2, int idx, int pctid, int bt){
		this.s1=s1;
		this.e1=e1;
		this.s2=s2;
		this.e2=e2;
		mPos1 = (s1+e1)/2;
		mPos2 = (s2+e2)/2;
		mIdx = idx;
		
		mPctID = pctid;
		if (bt == 2) mBT = HitType.GeneGene;
		else if (bt == 1) mBT = HitType.GeneNonGene;
	}
}
