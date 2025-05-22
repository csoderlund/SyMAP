package backend.synteny;

/*****************************************************
 * Hit class used for the synteny computations
 */
public class SyHit implements Comparable <SyHit> {
	protected int s1, e1, s2, e2; // input coords; used for final block coords and finding midpoint
	protected int mIdx;		// index of the hit in the database table
	protected int mPctID;   // MUMmer %id; create average of chain to save in DB
	
	protected int midG1;	// (h.start1+h.end1)/2	used for synteny computations
	protected int midG2;	// (h.start2+h.end2)/2
	
	protected int mI; 		// index of the hit in the sorted list
	
	// parameters used in finding long chain
	protected int mScore;		// score of longest chain terminating at this hit
	protected int mPrevI;		// predecessor in longest chain
	protected int mNDots;		// number of hits in longest chain
	
	// indicates if the hit has been returned in a chain in the current iteration
	protected boolean mDPUsed = false;
	
	// indicates if the hit has been put into a block and should not be used in any future iterations
	protected boolean mDPUsedFinal = false;
	
	protected SyHit(int s1, int e1, int s2, int e2, int idx, int pctid){
		this.s1 = s1;
		this.e1 = e1;
		this.s2 = s2;
		this.e2 = e2;
		this.mIdx = idx;
		this.mPctID = pctid;
		
		midG1 = (s1+e1)/2;
		midG2 = (s2+e2)/2;	
	}
	public int compareTo(SyHit h2) { 
		if (midG2 != h2.midG2) 	return midG2 - h2.midG2;
		else					return midG1 - h2.midG1;
	}
}
