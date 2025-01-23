package backend.synteny;

// Hit class used for the synteny computations
public class SyHit implements Comparable <SyHit> {
	private static int cntHit=1;
	protected int nHit=0;
	
	protected int s1, e1, s2, e2; // CAS533 add for final boundaries of block
	protected int mPos1;	// (h.start1+h.end1)/2
	protected int mPos2;	// (h.start2+h.end2)/2
	protected int mIdx;		// index of the hit in the database table
	protected int mI; 		// index of the hit in the sorted list
	protected int mPctID;   // MUMmer %id; create average of chain to save in DB
	
	protected int mScore = 0;
	protected int mPos1_alt = 0;
	
	// parameters used during dynamic programming
	protected int mDPScore;		// DP score of longest chain terminating here
	protected int mPrevI;		// predecessor in longest chain
	protected int mNDots;		// number of hits in longest chain
	
	// indicates if the hit has been returned in a chain in the current iteration of the dynamic programming
	protected boolean mDPUsed 	= false;
	
	// indicates if the hit has been put into a block and should not be used in any future DP iterations
	protected boolean mDPUsedFinal = false;
	
	protected SyHit(int s1, int e1, int s2, int e2, int idx, int pctid, int bt){
		this.s1 = s1;
		this.e1 = e1;
		this.s2 = s2;
		this.e2 = e2;
		this.mIdx = idx;
		this.mPctID = pctid;
		
		mPos1 = (s1+e1)/2;
		mPos2 = (s2+e2)/2;	
		nHit = cntHit++;
	}
	public int compareTo(SyHit h2) { // CAS560 moved from SyntenyMain
		if (mPos2 != h2.mPos2) 	return mPos2 - h2.mPos2;
		else					return mPos1 - h2.mPos1;
	}
}
