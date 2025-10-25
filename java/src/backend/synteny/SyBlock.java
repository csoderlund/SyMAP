package backend.synteny;

import java.util.Vector;
import java.util.Collections;
import java.util.Comparator;
import java.util.TreeSet;

import symap.Globals;
import util.ErrorReport;

/**************************************
 * Block class used for the synteny computations
 */
public class SyBlock  implements Comparable <SyBlock>{
	static protected int cntBlk=1;  
	
	protected int tBlk=0;		   // temporary unique
	
	protected String orient="";		  // "", "==", "!=", needed so not merged; 
	protected int mS1, mE1, mS2, mE2; // mid-point, until ready to save - than exact 
	protected float mCorr1, mCorr2;	  // chain hits, all hits in block
	protected String mCase = "R";	  // R=fail, A,B,C,D in isGoodCorr, N in coset, X is delete, M merged
	
	protected int mGrpIdx1, mGrpIdx2, mBlkNum=0; // Finalize
	protected int mIdx;						     // get from database to enter pseudo_blocks_hits
	protected double avgGap1=0, avgGap2=0; 
	
	protected Vector<SyHit> hitVec;		 // Block hits
	protected int nHits=0;				 // number hits
	
	private TreeSet<Integer> mHitIdxSet; // quick search for hitVec in Merge
	protected boolean hasChg=false;	     // MergeWith or Strict

	protected SyBlock(String orient) { 
		hitVec = new Vector<SyHit>();
		mHitIdxSet = new TreeSet<Integer>();
		this.orient = orient;
		tBlk = cntBlk++;
		
		mS1 = mS2 = Integer.MAX_VALUE; mE1 = mE2 = 0;
	}
	protected void clear() { // gets reused
		hitVec.clear();
		mHitIdxSet.clear();
		mCorr1 = mCorr2 = 0;
		mS1 = mS2 = Integer.MAX_VALUE; mE1 = mE2 = 0;
	}
	protected void addHit(SyHit ht) {
		hitVec.add(ht);
		nHits = hitVec.size();
		
		mS1 = Math.min(mS1, ht.midG1); 
		mE1 = Math.max(mE1, ht.midG1); 
		mS2 = Math.min(mS2, ht.midG2);
		mE2 = Math.max(mE2, ht.midG2);
	}
	protected void setActualCoords() { // finalize, used actual start/end
		for (SyHit h : hitVec) {
			if (h.s1<mS1) mS1=h.s1;
			if (h.e1>mE1) mE1=h.e1;
			if (h.s2<mS2) mS2=h.s2;
			if (h.e2>mE2) mE2=h.e2;
		}
	}

	// Merge this block
	// Orient not recalculated, but it is the mCorr1 that is save in database for orient
	protected void mergeWith(SyBlock b) {
		hasChg = true;
		
		mS1 = Math.min(mS1, b.mS1);
		mS2 = Math.min(mS2, b.mS2);
		mE1 = Math.max(mE1, b.mE1);
		mE2 = Math.max(mE2, b.mE2);

		for (SyHit ht : b.hitVec) {
			if (!mHitIdxSet.contains(ht.mIdx)) { 
				mHitIdxSet.add(ht.mIdx);
				addHit(ht);
			}
			else if (SyntenyMain.bTrace) Globals.prt("Merge dup hit#" + ht.mI2 + " " + tBlk + " -> " + b.tBlk);
		}
		nHits = hitVec.size();
		mCase = "M";
	}
	
	/*******************************************
	 * Checks to see whether the collinear block fit well in this block; called from Merge; CAS574 add
	 */
	protected boolean fitStrict(SyBlock coblk, int fullGap2, boolean bPrt) {// hits sorted on side1; 
		try {
			int cutoff = (int) (Math.round((double)coblk.nHits/2.0) * (double)fullGap2);
			cutoff = Math.max(cutoff, 300000);  // plants have small gaps
			cutoff = Math.min(cutoff, 4000000); // mammals have large gaps, e.g. human 3,389,849
			
			SyHit cHtTop = coblk.hitVec.get(0);
			SyHit cHtBot = coblk.hitVec.get(coblk.nHits-1);
			SyHit htTop=null, htBot=null;
			
			for (int i=1; i<nHits; i++) { // Find hit before and after coset using hitNum order
				SyHit htA = hitVec.get(i);
				if (htTop==null && htA.hitNum > cHtTop.hitNum) htTop = hitVec.get(i-1);
				
				if (htA.hitNum > cHtBot.hitNum) htBot = htA;
				
				if (htTop!=null && htBot!=null) break;
			}
			if (SyntenyMain.bTraceBlks) {
				
			}
			boolean isGood1 = true, isGood2 = true;
			int diff1 = 0, diff2 = 0;
			if (htTop!=null) {
				diff1 =  Math.abs(cHtTop.midG2 - htTop.midG2);
				isGood1 = diff1 < cutoff;
			}
			if (htBot!=null) {
				diff2 = Math.abs(cHtBot.midG2 - htBot.midG2);
				isGood2 = diff2 < cutoff;
			}
			
			if (bPrt && SyntenyMain.bTraceBlks) {
				cHtTop.tprt(String.format("C %-4d 1", cHtTop.coset));
				if (htTop!=null) htTop.tprt(String.format("B %-4d 1", tBlk)); else Globals.prt("At TOP");
				Globals.prt(String.format("%s%s D %,10d %,10d  [BK %-3d #%-4d  Top M2 %,10d] [bk %-3d #%-4d  top M2 %,10d] Gap %,d\n", 
				   Globals.toTF(isGood1),Globals.toTF(isGood2), diff1, diff2, coblk.tBlk, coblk.nHits, cHtTop.midG2,    tBlk, nHits, htTop.midG2, cutoff));
			}
				
			return isGood1 || isGood2; 
		}
		catch (Exception e) {ErrorReport.print(e,"strictTrim"); return false;}
	}
	/******************************************************************/
	// Block finalize: Compute avgGap, stdDev; CAS572 add; CAS574 do both averages in one call; CAS575 remove stddev
	// 
	protected void avgGap() { 
		int nGap = nHits-1; 
		avgGap1 = avgGap2 = 0;
		
		// side2 avgGap
		Collections.sort(hitVec,new Comparator<SyHit>() {
			public int compare(SyHit a, SyHit b) { 
				if (a.s2 != b.s2) 	return a.s2 - b.s2;
				else				return a.e2 - b.e2;		  
		}});
		
		for (int i=0; i<nHits-1; i++) { 
			SyHit htA = hitVec.get(i);
			SyHit htB = hitVec.get(i+1);
		
			avgGap2 += Math.abs(htB.s2 - htA.e2); //if (!isOlap(htA.s2, htA.e2, htB.s2, htB.e2)) { CAS574 remove
		}
		avgGap2 /= nGap;
		
		// side1 avgGap
		Collections.sort(hitVec,new Comparator<SyHit>() {
			public int compare(SyHit a, SyHit b) { 
				if (a.s1 != b.s1) 	return a.s1 - b.s1;
				else				return a.e1 - b.e1;		  
		}});
		 
		for (int i=0; i<nHits-1; i++) { 
			SyHit htA = hitVec.get(i);
			SyHit htB = hitVec.get(i+1);
			
			avgGap1 += Math.abs(htB.s1 - htA.e1);
		}
		avgGap1 /= nGap; 
	}
	
	protected boolean isContained(int pos1, int pos2) {
		return (pos1 >= mS1 && pos1 <= mE1 &&
				pos2 >= mS2 && pos2 <= mE2);
	}
	
	protected double avgPctID() { // to save in DB
		if (hitVec.size()==0) return -1.0; // for self-synteny - see RWdb
		
		double avg = 0;
		for (SyHit h : hitVec) avg += h.mPctID;
		avg /= hitVec.size();
		avg = (Math.round(10*avg));
		return avg/10;
	}
	protected void tprt(String msg) { 
		int m = (mBlkNum==0) ? tBlk : mBlkNum;
		String x = String.format("%-6s [Blk #%-3d %2s %s  #Hits %-4d  %6.3f %6.3f]", 
				  msg, m, orient, mCase, nHits, mCorr1, mCorr2);
		
		String y = String.format(" [AG %,7d  %,7d  S1 %,10d %,10d  Len %,10d %,10d]", 
							(int)avgGap1, (int)avgGap2, mS1, mS2,  (mE1-mS1), (mE2-mS2));
		
		String z = String.format(" [1st Hit# %4d  C# %d]",hitVec.get(0).hitNum, hitVec.get(0).coset);
		Globals.prt(x + y + z);
	}
	public String toString() {
		return "Block#" + mBlkNum + " Grps " + mGrpIdx1 + " " + mGrpIdx2;
	}
	public int compareTo (SyBlock b2) {
		if (mS1 != b2.mS1) 	return mS1 - b2.mS1;
		else				return mE1 - b2.mE1;
	}
}
