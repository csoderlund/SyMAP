package backend.synteny;

import java.util.Vector;
import java.util.TreeSet;

import symap.Globals;

/**************************************
 * Block class used for the synteny computations
 */
public class SyBlock  implements Comparable <SyBlock>{
	static protected int cntBlk=1;  
	static final double z_cutoff = 3.0;  // Used in bTrace
	
	protected int nBlk=0;		   // temporary unique
	
	protected String orient="";		  // "", "==", "!=", needed so not merged; 
	protected int mS1, mE1, mS2, mE2; // mid-point, until ready to save - than exact 
	protected float mCorr1, mCorr2;	  // chain hits, all hits in block
	protected String mCase = "R";	  // R=fail, A,B,C,D in isGoodCorr, N in coset, X is delete, M merged
	
	protected int mGrpIdx1, mGrpIdx2, mBlkNum=0; // Finalize
	protected int mIdx;						     // get from database to enter pseudo_blocks_hits
	protected double avgGap1=0, avgGap2=0, stdDev1=0, stdDev2=0; // CAS572 add
	protected int cntZ=0;					  // CAS572 add
	
	protected Vector<SyHit> mHits;		 // Block hits
	protected int n=0;					 // number hits
	
	private TreeSet<Integer> mHitIdxSet; // quick search for mHits in Merge
	protected boolean hasChg=false;	     // MergeWith or Strict; CAS572 add

	protected SyBlock(String orient) { 
		mHits = new Vector<SyHit>();
		mHitIdxSet = new TreeSet<Integer>();
		this.orient = orient;
		nBlk = cntBlk++;
		
		mS1 = mS2 = Integer.MAX_VALUE; mE1 = mE2 = 0;
	}
	protected void clear() { // gets reused
		mHits.clear();
		mHitIdxSet.clear();
		mCorr1 = mCorr2 = 0;
		mS1 = mS2 = Integer.MAX_VALUE; mE1 = mE2 = 0;
	}
	protected void addHit(SyHit ht) {
		mHits.add(ht);
		
		mS1 = Math.min(mS1, ht.midG1); // CAS572 moved them here from SyntenyMain
		mE1 = Math.max(mE1, ht.midG1); 
		mS2 = Math.min(mS2, ht.midG2);
		mE2 = Math.max(mE2, ht.midG2);
		n   = mHits.size();
	}
	protected void setActualCoords() { // finalize, used actual start/end
		for (SyHit h : mHits) {
			if (h.s1<mS1) mS1=h.s1;
			if (h.e1>mE1) mE1=h.e1;
			if (h.s2<mS2) mS2=h.s2;
			if (h.e2>mE2) mE2=h.e2;
		}
	}

	// Merge this block (mCorr1 was using the best of the 2 blocks, now it is recalculated in SyntenyMain CAS572)
	protected void mergeWith(SyBlock b) {
		hasChg = true;
		
		mS1 = Math.min(mS1, b.mS1);
		mS2 = Math.min(mS2, b.mS2);
		mE1 = Math.max(mE1, b.mE1);
		mE2 = Math.max(mE2, b.mE2);

		for (SyHit ht : b.mHits) {
			if (!mHitIdxSet.contains(ht.mIdx)) { 
				mHitIdxSet.add(ht.mIdx);
				addHit(ht);
			}
			else if (SyntenyMain.bTrace) Globals.prt("duplicate hit for merge: " + ht.mI2);
		}
		mCase = "M";
	}
	// Block finalize: Compute avgGap, stdDev; CAS572 add
	protected void stdDev(boolean is1) { // gets called twice in a row, once is1=F, then is1=T
		if (mHits.size() <= 2) return;
		int n = mHits.size();
		int d = n-1;
		double avgGap=0, stddev=0;
		
		if (is1) SyHit.sortListSide1(mHits); // last sort before database
		else     SyHit.sortListSide2(mHits); 
		
		for (int i=0; i<n-1; i++) {
			SyHit htA = mHits.get(i);
			SyHit htB = mHits.get(i+1);
			if (is1) {
				if (!isOlap(htA.s1, htA.e1, htB.s1, htB.e1)) {
					htA.gap1 = Math.abs(htB.s1 - htA.e1);
					avgGap += htA.gap1 ;
				}
			}
			else {
				if (!isOlap(htA.s2, htA.e2, htB.s2, htB.e2)) {
					htA.gap2 = Math.abs(htB.s2 - htA.e2); 
					avgGap += htA.gap2 ;
				}
			}
		}
		avgGap /= d;
		double var=0.0;
		for (int i=0; i<n-1; i++) {
			SyHit ht = mHits.get(i);
			int gap = (is1) ? ht.gap1 : ht.gap2;
			var += Math.pow((double) gap-avgGap, 2);
		}
		var /= (d-1);
		stddev = Math.sqrt(var);
		
		if (is1) {avgGap1 = avgGap; stdDev1 = stddev;} // save for DB
		else     {avgGap2 = avgGap; stdDev2 = stddev;}
		
		if (SyntenyMain.bTrace && !is1) {
			for (int i=0; i<n-1; i++) { // Z-score
				SyHit ht = mHits.get(i);
				double z1 =  (ht.gap1-avgGap)/stddev;
				double z2 =  (ht.gap2-avgGap)/stddev;
				if (z1>=z_cutoff || z2>=z_cutoff ) cntZ++;
			}
		}
	}
	
	protected boolean isContained(int pos1, int pos2) {
		return (pos1 >= mS1 && pos1 <= mE1 &&
				pos2 >= mS2 && pos2 <= mE2);
	}
	private boolean isOlap(int s1,int e1, int s2, int e2) {
		int gap = Math.max(s1,s2) - Math.min(e1,e2);
		return (gap <= 0);
	}
	protected double avgPctID() { // to save in DB
		if (mHits.size()==0) return -1.0; // for self-synteny - see RWdb
		
		double avg = 0;
		for (SyHit h : mHits) avg += h.mPctID;
		avg /= mHits.size();
		avg = (Math.round(10*avg));
		return avg/10;
	}
	
	protected void tprt(String msg) { 
		int m = (mBlkNum==0) ? nBlk : mBlkNum;
		String x = String.format("%-10s Block #%-3d %2s %s  Hits %-4d  %6.3f %6.3f", msg, m, orient, mCase, n, mCorr1, mCorr2);
		String y;
		if (avgGap1==0) y = String.format("  Avg %,8d  %,8d  Len %,10d   %,10d", 
							(int)((mE1-mS1)/n),(int)((mE2-mS2)/n), (mE1-mS1), (mE2-mS2));
		else            y = String.format("  Avg %,8d  %,8d  StdDev %,7d %,7d  Z %,2d  Len %,10d   %,10d", 
							(int)avgGap1, (int)avgGap2, (int)stdDev1, (int)stdDev2, cntZ, (mE1-mS1), (mE2-mS2));
		Globals.prt(x + y);
	}
	public int compareTo (SyBlock b2) {
		if (mS1 != b2.mS1) 	return mS1 - b2.mS1;
		else				return mE1 - b2.mE1;
	}
}
