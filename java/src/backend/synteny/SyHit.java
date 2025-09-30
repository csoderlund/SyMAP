package backend.synteny;

import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

import symap.Globals;

/*****************************************************
 * Hit class used for the synteny computations
 */
public class SyHit implements Comparable <SyHit> {
	protected int s1, e1, s2, e2; // input coords; used for final block coords and finding midpoint
	protected int mIdx;		// index of the hit in the database table
	protected int mPctID;   // MUMmer %id; create average of chain to save in DB; never used
	protected int coset;	// collinear set; 				 for bStrict to include cosets
	protected int hitNum;   // hitnum computed down G1 side after clustering
	
	protected int midG1;	// (h.start1+h.end1)/2	used for synteny computations
	protected int midG2;	// (h.start2+h.end2)/2
	
	protected int mI2; 		// index of the hit in the sorted list sorted on midG2 - set before for binarySearch
	
	// parameters used in finding long chain
	protected int mScore;		// score of longest chain terminating at this hit
	protected int mPrevI;		// predecessor in longest chain; finalBlock used for previous hitNum
	protected int mNDots;		// number of hits in longest chain
	
	protected int gap1=0, gap2=0;  
	
	// indicates if the hit has been returned in a chain in the current iteration
	protected boolean mDPUsed = false;
	
	// indicates if the hit has been put into a block and should not be used in any future iterations
	protected boolean mDPUsedFinal = false;
	protected int nBlk = 0;		// Used in SyntenyMain.cosetBlock
	
	protected SyHit(int s1, int e1, int s2, int e2, int idx, int pctid, int coset, int hitnum){
		this.s1 = s1;
		this.e1 = e1;
		this.s2 = s2;
		this.e2 = e2;
		this.mIdx = idx;
		this.mPctID = pctid;
		this.coset = coset;
		this.hitNum = hitnum;
		
		midG1 = (s1+e1)/2;
		midG2 = (s2+e2)/2;	
	}
	protected void tprt(String msg) {
		Globals.prt(String.format("  %s   Hit# %,6d  Gap: %,8d  %,8d  S/E: %,10d %,10d %,10d %,10d  CS %d", 
				msg, hitNum, gap1, gap2, s1, e1, s2, e2, coset));
	}
	/////////////////////////////////////////////////////////////////////
	public int compareTo(SyHit h2) { // For binarySearch
		if (midG2 != h2.midG2) 	return midG2 - h2.midG2;
		else					return midG1 - h2.midG1;
	}
	
	//SyHit.sortListSide1(hitVec);
	protected static void sortListSide1(Vector<SyHit> mHits) {// sorted on side1 in ascending order
		Collections.sort(mHits,new Comparator<SyHit>() {
			public int compare(SyHit a, SyHit b) { 
				if (a.s1 != b.s1) 	return a.s1 - b.s1;
				else				return a.e1 - b.e1;		  
		}});
	}
}
