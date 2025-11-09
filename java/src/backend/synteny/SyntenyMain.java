package backend.synteny;

import java.io.File;
import java.util.Vector;
import java.util.HashMap;
import java.util.Collections;

import backend.Constants;
import backend.Utils;
import database.DBconn2;
import symap.Globals;
import symap.manager.Mpair;
import symap.manager.Mproject;
import util.Cancelled;
import util.ErrorCount;
import util.ErrorReport;
import util.ProgressDialog;
import util.Utilities;

/**************************************************
 * Computes synteny
 * Strict: uses original algorithm but:
 * 		uses stricter gaps and larger PCC (see setCutoffs)
 * 		creates blocks with bOrient first, then remove hit-wires that cross may others
 * 		makes blocks out of CoSets that are not in blocks (cosetBlocks), and tries to merge
 * 		Merge has some special logic for Strict since CoSets can be merged
 * Self: 
 */
public class SyntenyMain {
	public  static boolean bTrace = false; 		  // -bt trace; set at command line 
	protected static boolean bTraceBlks = bTrace; // local set
	
	private boolean bVerbose = Constants.VERBOSE; 
	
	protected static final String orientSame = "==";	// must be ++ or --;  
	protected static final String orientDiff = "!=";	// must be +- or -+
	protected static final String orientMix  = "";		// can be mixed

	private ProgressDialog mLog;
	private DBconn2 dbc2, tdbc2;	
	private int mPairIdx;
	
	// Interface parameters saved 
	protected int pMindots=7;	// the only user integer parameters 
	private boolean bStrict  = false; 
	private boolean bOrient = false; // to conform to standard definition, must be same orientation
	private String pMerge  = "0"; // 0 - always do contain; 1 - overlap; 2 - close (which is also overlap)
	
	private boolean bOrSt   = false; // Orient || Strict  
	private boolean isSelf = false, bOrderAgainst=false;
	
	private Mproject mProj1, mProj2;
	private Mpair mp;
	private String resultDir;
	
	// Main data structure for a chr-chr pair
	private Vector <SyBlock> blockVec;  // blocks chr-chr pair; recreated on each new chr-chr; 
	
	// Cutoffs
	final private int fMindots_keepbest = 3; // to be merged into one >= pMinDots
	final private int fMingap = 1000; 		 // minimum gap param considered in binary search

	private int mMindotsA, mMindotsB, mMindotsC, mMindotsD; // Set using pMinHits; used with isGoodCorr
	private float mCorr1A=0.8f;								// reduced for strict
	private final float mCorr1B=0.98f, mCorr1C=0.96f, mCorr1D=0.94f; // For chain
	private final float mCorr2A=0.7f, mCorr2B=0.9f,  mCorr2C=0.8f,  mCorr2D=0.7f;  // For all hits in surrounding box
	
	private int mMaxgap1, mMaxgap2;  						  // mProj1.getLength()/(Math.sqrt(nHits)));
	private float mAvg1A, mAvg2A;							  // mMapgap/15
	private int ngaps;										  // Depends on genome length && number hits
	private Vector<Integer> gap1list = new Vector<Integer>(); // Set the decreasing gap search points 
	private Vector<Integer> gap2list = new Vector<Integer>(); 
	
	private final double fDiag = 0.4, fGeneDiag=0.8;					  // isSelf, aligns to close to diagonal; CAS576

	// final stats
	private boolean bSuccess = true;
	private long startTime;
	private int totalBlocks=0, cntMerge=0, cntSame=0, cntDiff=0;
	private int cntGrpMerge=0, cntGrpSame=0, cntGrpDiff=0, cntSetNB=0, cntSetNBhits=0; 			// Verbose
	private int cntTrIn=0, cntTrOlap=0, cntTrCoset=0, cntTrSkip=0, cntTrCsChg=0, cntTrRmDiag=0; // bTrace
	
	public SyntenyMain(DBconn2 dbc2, ProgressDialog log, Mpair mp) {
		this.dbc2 = dbc2;
		this.mLog = log;
		this.mp = mp;
		mPairIdx = mp.getPairIdx();
		mProj1 = mp.mProj1;
		mProj2 = mp.mProj2;
		
		bStrict = mp.isStrict(Mpair.FILE);
		bOrient = mp.isOrient(Mpair.FILE);
		pMerge  = mp.getMergeIndex(Mpair.FILE);
		bOrSt = bOrient || bStrict;  // Strict does orient but then mix merges, whereas bOrient does not mix merge
		
		bOrderAgainst = (mp.isOrder1(Mpair.FILE) || mp.isOrder2(Mpair.FILE));
		
		isSelf = (mProj1.getIdx() == mProj2.getIdx());
	}
	/******************************************************************/
	public boolean run(Mproject pj1, Mproject pj2) {
	try {
	/** Setup ***************************************/
		String proj1Name = mProj1.getDBName(), proj2Name = mProj2.getDBName();
		int mProj1Idx    = mProj1.getIdx(),    mProj2Idx = mProj2.getIdx(); 
		
		startTime = Utils.getTime();
		if (proj1Name.equals(proj2Name)) mLog.msg("Finding self-synteny for " + proj1Name); 
		else                             mLog.msg("Finding synteny for " + proj1Name + " and " + proj2Name); 
		
		resultDir = Constants.getNameResultsDir(proj1Name, proj2Name);	
		if (!(new File(resultDir)).exists()) {
			mLog.msg("Cannot find result directory " + resultDir);
			ErrorCount.inc();
			return false;
		}
		
		tdbc2 = new DBconn2("Synteny-" + DBconn2.getNumConn(), dbc2);
		tdbc2.executeUpdate("DELETE FROM blocks WHERE pair_idx=" + mPairIdx);
		RWdb rwObj            = new RWdb(mPairIdx, mProj1Idx, mProj2Idx, tdbc2);
		BuildBlock buildObj   = new BuildBlock(rwObj);
		FinishBlock finishObj = new FinishBlock(rwObj);
		
		setCutoffs(); 	// Set cutoffs for all grp-grp pairs
		if (Cancelled.isCancelled() || !bSuccess) {tdbc2.close(); return false;} 
		
	/** Main loop through chromosome pairs ***************************************/
		int nGrpGrp = mProj1.getGrpSize() * mProj2.getGrpSize();
		int maxGrp = nGrpGrp;
		
		if (bVerbose) 	Utils.prtNumMsg(mLog, nGrpGrp, "group-x-group pairs to analyze");
		else 			Globals.rprt("group-x-group pairs to analyze");
		String grpMsg = nGrpGrp + " of " + maxGrp;
		
		for (int grpIdx1 : mProj1.getGrpIdxMap().keySet()) {
			for (int grpIdx2 : mProj2.getGrpIdxMap().keySet()) {
				nGrpGrp--;
				if (isSelf && grpIdx1<grpIdx2) continue; // DIR_SELF (old >) must match direction of loadHitsForGrpPair; mirror later
				
				SyBlock.cntBlk = 1;
				cntGrpMerge=cntGrpSame=cntGrpDiff=cntTrRmDiag=0;
				blockVec = new Vector<SyBlock>(); // create for both orients so can be number sequentially
				
				if (!bOrSt) buildObj.doChrChrSynteny(grpIdx1,grpIdx2, orientMix, grpMsg); 
				else { 									// restrict to same orientation; 
					buildObj.doChrChrSynteny(grpIdx1,grpIdx2, orientSame, grpMsg); 
					buildObj.doChrChrSynteny(grpIdx1,grpIdx2, orientDiff, grpMsg);
				}
				if (Cancelled.isCancelled() || !bSuccess) {tdbc2.close();return false;}
				
				finishObj.doChrChrBlkFinish(grpIdx1,grpIdx2); 			// save to DB; need to be combined bOrient; 
				
				cntSame += cntGrpSame; cntDiff += cntGrpDiff; cntMerge += cntGrpMerge;
				if ((bTrace || bVerbose) && blockVec.size()>0) { 	
					Globals.rclear();
					String msg = String.format("Blocks %-12s", mProj1.getGrpFullNameFromIdx(grpIdx1) + "-" + mProj2.getGrpFullNameFromIdx(grpIdx2));
					if (bOrSt)                         msg += String.format("  Orient:  Same %3d   Diff %3d ",cntGrpSame, cntGrpDiff);
					if (!pMerge.equals("0") || bTrace) msg += String.format("  Merged %3d ", cntGrpMerge);
					if (isSelf && cntTrRmDiag>0 && bTrace) msg += String.format("  Remove close to diagonal %3d ", cntTrRmDiag);
					Utils.prtIndentNumMsgFile(mLog, 1, blockVec.size(), msg);	
				}
				String t = Utilities.getDurationString(Utils.getTime()-startTime);
				
				grpMsg = " (" + nGrpGrp + " of " + maxGrp + " pairs (" + t + "))  ";
				Globals.rprt(nGrpGrp + " of " + maxGrp + " pairs remaining (" + t + ")"); 
				blockVec.clear();
			}
		}	
		Globals.rclear();
		
		if (Cancelled.isCancelled() || !bSuccess) {tdbc2.close();return false;}
		
		// Final number for output to terminal and log
		String msg = "Blocks";
		if (!pMerge.equals("0")) msg += String.format("   %,d Merged ", cntMerge);
		if (bOrient)             msg += String.format("   Orient: %,d Same   %,d Diff", cntSame, cntDiff);
		Utils.prtNumMsg(mLog, totalBlocks, msg);
		
		if (bVerbose) {
			msg = String.format("Collinear Sets not in blocks (%,d total hits)", cntSetNBhits);	
			Utils.prtIndentNumMsgFile(mLog, 1, cntSetNB, msg);
		}
		if (bTrace) {
			Utils.prtIndentMsgFile(mLog, 3, String.format("Merge: In %,d    Olap %,d", cntTrIn, cntTrOlap));
			Utils.prtIndentMsgFile(mLog, 3, String.format("Coset Blk %,d    Skip %,d    Add hits to existing blk %,d", cntTrCoset, cntTrSkip, cntTrCsChg));
		}
		Utils.prtMsgTimeDone(mLog, "Finish Synteny", startTime);

	/** Order *******************************************************/ 
		if (mp.isOrder1(Mpair.FILE)) { 
			OrderAgainst obj = new OrderAgainst(mp, mLog, tdbc2); 
			obj.orderGroupsV2(false);				
		}
		else if (mp.isOrder2(Mpair.FILE)) {
			OrderAgainst obj = new OrderAgainst(mp, mLog, tdbc2);
			obj.orderGroupsV2(true);
		}	
		if (Cancelled.isCancelled() || !bSuccess) {tdbc2.close(); return false;}
		
	/** Finish **********************************************************/
		tdbc2.close();
		return bSuccess; 								// Full time printed in calling routine
	}
	catch (Exception e) {ErrorReport.print(e, "Computing Synteny"); return false; }
	}
	
//  Pearson correlation coefficient (PCC) of midpoints; used for BuildBlock and for saving final to DB
	private float hitCorrelation(Vector<SyHit> hits) {
		if (hits.size() <= 2) return 0;
		
		double N = (float)hits.size();
		double xav = 0, xxav = 0, yav = 0, yyav = 0, xyav = 0;
		
		for (SyHit h : hits) {
			double x = (double)h.midG1;
			double y = (double)h.midG2;
			xav  += x;   yav  += y;
			xxav += x*x; yyav += y*y; xyav 	+= x*y;
		}	
		xav  /= N; yav 	/= N;
		xxav /= N; yyav /= N; xyav /= N;
		
		double sdx = Math.sqrt(Math.abs(xxav - xav*xav));
		double sdy = Math.sqrt(Math.abs(yyav - yav*yav));
		double sdprod = sdx*sdy;
		
		double corr = (sdprod > 0) ? corr = ((xyav - xav*yav)/sdprod) : 0;
		if (Math.abs(corr) > 1) corr = (corr > 0) ? 1.0 : -1.0; // cannot be <1 or >1
		
		return (float)corr;
	}
	
	/***************************************************************
	 * All values are final 
	 ****************************************************/
	private void setCutoffs() { 
	try {
		pMindots   = mp.getMinDots(Mpair.FILE);   // default 7
		
		mMindotsA = mMindotsB = pMindots; // default 7
		mMindotsC = 3*pMindots/2; 		  // default 10 if pMindots=7
		mMindotsD = 2*pMindots; 		  // default 14 if pMindots=7

		int nHits = tdbc2.executeCount("select count(*) as nhits from pseudo_hits "
				+ " where proj1_idx='" + mProj1.getIdx() + "' and proj2_idx='" + mProj2.getIdx() + "'");;
		if (bVerbose) 	Utils.prtNumMsg(mLog, nHits, "Total hits"); 
		else 			Globals.rprt(String.format("%,10d Total Hits", nHits));
		
		mMaxgap1 = (int)( ((double)mProj1.getLength())/(Math.sqrt(nHits))); // can be >3M, e.g. humans, don't limit
		mMaxgap2 = (int)( ((double)mProj2.getLength())/(Math.sqrt(nHits)));
		
		mAvg1A = ((float)mMaxgap1)/15;		
		mAvg2A = ((float)mMaxgap2)/15;		
		
		gap1list.add(mMaxgap1/2);
		gap2list.add(mMaxgap2/2);
		
		int minGap=2*fMingap; // fMingap=1000
		ngaps = 1; 
		while (gap1list.get(ngaps-1) >= minGap && gap2list.get(ngaps-1) >= minGap){
			gap1list.add(gap1list.get(ngaps-1)/2);
			gap2list.add(gap2list.get(ngaps-1)/2);	
			ngaps++;
		}
		if (bStrict) {				
			if (ngaps>2) {
				gap1list.remove(0);
				gap2list.remove(0);
				ngaps--;
			}
			mCorr1A = 0.9f;
		}
		if (bTrace) {
			Globals.prt(String.format("A Hits %4d  corr1 %6.3f  corr2 %6.3f  avg1 %,d  avg2 %,d", 
					                   mMindotsA, mCorr1A, mCorr2A, (int)mAvg1A, (int)mAvg2A));
			Globals.prt(String.format("B Hits %4d  corr1 %6.3f  corr2 %6.3f", mMindotsB, mCorr1B, mCorr2B));
			Globals.prt(String.format("C Hits %4d  corr1 %6.3f  corr2 %6.3f", mMindotsC, mCorr1C, mCorr2C));
			Globals.prt(String.format("D Hits %4d  corr1 %6.3f  corr2 %6.3f", mMindotsD, mCorr1D, mCorr2D));
			
			double sr = Math.sqrt(nHits);
			Globals.prt(String.format("MaxGap1 %,d (%,d/%.1f)  MaxGap2 %,d (%,d/%.1f) ", 
					mMaxgap1, mProj1.getLength(), sr, mMaxgap2, mProj2.getLength(), sr));
			String m="Gap1: "; for (int g : gap1list) m += String.format("%,d  ",g); Globals.prt(m);
			       m="Gap2: "; for (int g : gap2list) m += String.format("%,d  ",g); Globals.prt(m); Globals.prt("");
		}
	}
	catch (Exception e) {ErrorReport.print(e, "Set properties for synteny"); bSuccess=false;}
	}
	
	/*************************************************************************
	 * Synteny algorithm
	 * All methods that use hitVec are in this class
	 * The hitVec contains the hits from grp1 to grp2 for orient ==,!= or both
	 *************************************************************************/
	private class BuildBlock {
		private String orient;
		private RWdb rwObj;					// all read/write db methods
		private Vector <SyHit>   hitVec;    // chr-chr hits; populated in RWdb for each new chr-chr; 
		private int grpIdx1, grpIdx2;
		private String title, grpMsg;
		
		private BuildBlock(RWdb rwObj) {
			this.rwObj = rwObj;
		}
		/************************************************************************
		 * Main synteny method; orient may be "==", "!=" or ""
		 *******************************************************************/
		private void doChrChrSynteny(int grpIdx1, int grpIdx2, String o, String grpMsg)  {
		try {
			this.grpIdx1 = grpIdx1;
			this.grpIdx2 = grpIdx2;
			orient = o;	// "", ==, !=
			this.grpMsg = grpMsg;
			title = mProj1.getGrpFullNameFromIdx(grpIdx1)+ "-" + mProj2.getGrpFullNameFromIdx(grpIdx2);
			
		/* Read hits */
			hitVec = rwObj.loadHitsForGrpPair(grpIdx1, grpIdx2, orient);
			if (hitVec==null) {bSuccess=false; return;}
			
			if (hitVec.size() < pMindots) return; // not error
			
			Collections.sort(hitVec); 									   // Sort by G2 hit midpoints
			for (int i = 0; i < hitVec.size(); i++) hitVec.get(i).mI2 = i; // index of the hit in the sorted list
			
		/** Create blocks **/
			createBlockVec();
			
			hitVec.clear();
		}
		catch (Exception e) {ErrorReport.print(e, "Synteny main"); bSuccess = false;}
		}
		
		/** This performs a "binary search" of the gap parameter space **/
		private void createBlockVec() { 
			for (int i = 0; i < ngaps; i++) {
				int low1 = gap1list.get(i);						// gaps are from large to small
				int low2 = gap2list.get(i);
				int high1 = (i > 0 ? gap1list.get(i-1) : low1);
				int high2 = (i > 0 ? gap2list.get(i-1) : low2);

				while (hasGoodChain(low1, low2)) 				// if there is at least one good chain
					binarySearch(low1, high1, low2, high2);		// search them all
			}
			cosetBlocks(); 	   // Use HitVec, if bStrict, make cosets not in blocks into blocks; otherwise, just count for output
		}
		
		/************************************************************/
		private void binarySearch(int low1, int high1, int low2, int high2){
			int gap1 = (high1 + low1)/2;
			int gap2 = (high2 + low2)/2;
			
			while ( high1-low1 > fMingap && high2-low2 > fMingap){ // 1000
				if (hasGoodChain(gap1,gap2)){
					low1 = gap1;
					low2 = gap2;
					gap1 = (gap1 + high1)/2;
					gap2 = (gap2 + high2)/2;	
				}
				else {
					high1 = gap1;
					high2 = gap2;
					gap1 = (low1 + gap1)/2;
					gap2 = (low2 + gap2)/2;
				}
			}
			// Final blocks
			SyBlock blk = new SyBlock(orient, grpIdx1, grpIdx2);
			while (longestChain(2, low1, low2, blk)){  
				if (isGoodCorr(blk)){
					blockVec.add(blk);				   // final: add new block to blockVec
					finalBlock1(blk);
					
					blk = new SyBlock(orient, grpIdx1, grpIdx2); 
					Globals.rclear();
					Globals.rprt(blockVec.size() + " blocks for " + title + grpMsg); 
				}
				else blk.clear();
			}
			resetDPUsed();
		}
		/************************************************************/
		private boolean hasGoodChain(int gap1, int gap2){ // createBlockVec, binarySearch
			SyBlock blk = new SyBlock(orient, grpIdx1, grpIdx2); 
			while (longestChain(1, gap1, gap2, blk)){ 
				if (isGoodCorr(blk)) {
					resetDPUsed();
					return true;			 
				}
				blk.clear();
			}
			resetDPUsed();
			return false;
		}
		
		private void resetDPUsed(){
			for (SyHit h : hitVec) h.mDPUsed = h.mDPUsedFinal;
		}
		/************************************************************
		 * Uses !ht.mDPUsed from hitVec to find longest chain
		 ***********************************************************/
		private boolean longestChain(int who, int gap1, int gap2, SyBlock rtBlk) {// hasGoodChain, binarySearch
			for (SyHit hit : hitVec) {
				hit.mNDots 	= 1;
				hit.mScore  = 1;
				hit.mPrevI 	= -1;
			}
			
			SyHit savHt1 = null;					    // first of final chain
			Vector <SyHit> links = new Vector<SyHit>(); // hits within range of ht1
			
			for (int i = 0; i < hitVec.size(); i++) {
				SyHit ht1 = hitVec.get(i);			// try this dot for possible first
				if (ht1.mDPUsed) continue;
				
				links.clear(); 					 	// get all dots within gaps range of ht1 to be used as possible bestPrev
				for (int j = i-1; j >= 0; j--) { 	// links are from current position searching previous
					SyHit ht2 = hitVec.get(j);
					
					if (ht2.mDPUsed) continue;
					
					if (ht1.midG2 - ht2.midG2 > mMaxgap2) break; // crucial optimization from sorting on midG2
					
					if (Math.abs(ht1.midG1 - ht2.midG1) > mMaxgap1) continue;	
					
					links.add(ht2);			
				}
				if (links.size() == 0) continue; 
			
				int maxscore = 0;
				SyHit bestPrev = null; 				  
				
				for (SyHit htbN : links) { 			        // Find bestNext for ht1 by minimal gap on both sides
					double d1 = Math.abs((double)htbN.midG1 - (double)ht1.midG1)/(double)gap1; 
					double d2 = Math.abs((double)htbN.midG2 - (double)ht1.midG2)/(double)gap2;
					int gap_penalty = (int)(d1 + d2);
					
					int score = 1 + htbN.mScore - gap_penalty; 
					
					if (score > maxscore) { // score can be <0
						maxscore = score;
						bestPrev = htbN;
					}
				}
				if (maxscore > 0) {
					ht1.mScore = maxscore;
					ht1.mPrevI = bestPrev.mI2;
					ht1.mNDots += bestPrev.mNDots;
					
					if (savHt1 == null) 					savHt1 = ht1;
					else if (ht1.mScore > savHt1.mScore) 	savHt1 = ht1;
					else if (ht1.mScore >= savHt1.mScore && ht1.mNDots > savHt1.mNDots) savHt1 = ht1;
				}
				else {
					ht1.mNDots = 1;
					ht1.mScore = 1;
					ht1.mPrevI = -1;
				}
			} // end hit loop

			if (savHt1==null || savHt1.mNDots < fMindots_keepbest) return false; // fMindots_keepBest=3
			
		/* Need to build block for isGoodBlock even if it is then discarded */
			SyHit ht = savHt1; 			// build chain from first dot
			
			while (true) {
				ht.mDPUsed = true;
				
				rtBlk.addHit(ht); // add to block hits (rtBlk is parameter); 
				
				if (ht.mPrevI >= 0) ht = hitVec.get(ht.mPrevI);
				else break;
			}
			return true;
		}
		/****************************************************/
		private boolean isGoodCorr(SyBlock blk){ // hasGoodChain, binary search;  evaluate block
			int nDots = blk.nHits; //blk.mHits.size();
			
			if (nDots < mMindotsA) return false;
			
			blockCorrelations(blk);
			
			if (!bOrderAgainst && bOrSt) {	// does not work well for small draft
				if (orient.equals(orientSame) && blk.mCorr1<0) return false;
				if (orient.equals(orientDiff) && blk.mCorr1>0) return false;
			}
			
			float avg1 = (blk.mE1 - blk.mS1)/nDots;
			float avg2 = (blk.mE2 - blk.mS2)/nDots;
			float c1 = Math.abs(blk.mCorr1), c2 = Math.abs(blk.mCorr2);
			
			// mCase='R' is reject; mCase also used in Merge.removeBlocks
			if (nDots >= mMindotsA && avg1 <= mAvg1A && avg2 <= mAvg2A && c1 >= mCorr1A && c2 >= mCorr2A) blk.mCase = "A";
			else if (nDots >= mMindotsB	&& c1 >= mCorr1B && c2 >= mCorr2B) blk.mCase = "B";
			else if (nDots >= mMindotsC	&& c1 >= mCorr1C && c2 >= mCorr2C) blk.mCase = "C";
			else if (nDots >= mMindotsD	&& c1 >= mCorr1D && c2 >= mCorr2D) blk.mCase = "D";
			
			return (!blk.mCase.equals("R"));
		}
		
		/***************************************************
		 * from 2006 publication
		 *  mCorr1 is the approximate linearity of the anchors in the chain as measured by the PCC; 
		 *  mCorr2 is the correlation of all the anchors in the chain’s bounding rectangle 
		 */
		private void blockCorrelations(SyBlock blk) {
			blk.mCorr1 = hitCorrelation(blk.hitVec); // chain hits 
			
			Vector<SyHit> hits2 = new Vector<SyHit>();
			for (SyHit ht : hitVec) {
				if (blk.isContained(ht.midG1, ht.midG2)) hits2.add(ht);
			}
			blk.mCorr2 = hitCorrelation(hits2); // all hits in surrounding block
		}
		private void finalBlock1(SyBlock blk) {
			for (SyHit h : blk.hitVec) {
				h.mDPUsedFinal = true;		 
				h.nBlk  = blk.tBlk;          // use for cosetBlocks
			}
		}
		/*****************************************************************************
		 * Find cosets that are not in blocks and create them. They may then get merged.
		 *   Run on chr-chr-orient, hence, hitsVec can be used. 
		 *   Strict: make blocks from cosets; Original: count cosets not in blocks
		 *   
		 *   If coset is same strand, then will already be part of block if any good.
		 *   So this only merges opposite strand cosets that 'fit' well.
		 *****************************************************************************/
		private void cosetBlocks() {
		try {
			if (!bStrict && !bVerbose) return;
			if (bStrict && bOrient && !bVerbose) return; 
			
		// gather Coset hits; loop through hits
			HashMap <Integer, CoSet> cosetMap = new HashMap <Integer, CoSet> ();
			for (SyHit ht : hitVec) {
				if (ht.coset==0) continue;
				CoSet cs;
				if (cosetMap.containsKey(ht.coset)) cs = cosetMap.get(ht.coset);
				else {
					cs = new CoSet(ht.coset);
					cosetMap.put(ht.coset, cs);
				}
				cs.add(ht);
			}
		
		// loop through cosets
			Vector <SyBlock> cblkVec = new Vector <SyBlock> ();
			for (CoSet cset : cosetMap.values()) {
				int nBlk=0, cntNoBlk=0;		// loop through coset hits; determine if in block or not; 
				for (SyHit ht : cset.setVec) {  
					if (ht.nBlk!=0) {
						if (nBlk==0) nBlk = ht.nBlk; 	    // hits go in 1st block found for coset
						else if (nBlk!=ht.nBlk && bTrace) 	// coset in two different blocks
							 Globals.prt("Warning: collinear set " + ht.coset + " in block " + nBlk + " and " + ht.nBlk);
					}
					else cntNoBlk++;
				} // end hit loop
				
				if (!bStrict) {							    
					if (nBlk==0) {cntSetNB++; cntSetNBhits += cset.setVec.size(); }
					continue;
				}
				
				if (nBlk==0) { 			 	 		// new block            		
					SyBlock cblk = new SyBlock(orient, grpIdx1, grpIdx2);		// assigned nBlk on creation
					for (SyHit ht : cset.setVec)  
						if (ht.nBlk==0) cblk.addHit(ht);	
			
					cblk.mCase = "N"; 
					cblk.setActualCoords();					// better for possible merge
					cblkVec.add(cblk);	
					continue;
				}
				
				if (cntNoBlk>0) {			   		// at least one hit in block, add rest to the existing block; rare case 
					for (SyBlock cblk : blockVec) {
						if (cblk.tBlk == nBlk) {
							for (SyHit ht : cset.setVec) {
								if (ht.nBlk==0) {
									cblk.addHit(ht);
									cntTrCsChg++;
								}
							}
							break;
						}
					}
				}
				// else nBlk>0 and all are in a block
			} // end coset loop
			
			// If Cosets merge, they have a better change of merging into a bigger block
			if (cblkVec.size()>0) {
				Merge mergeObj = new Merge(Merge.COSET, cblkVec,  bOrient, pMindots, gap2list.get(0)); 
				cblkVec = mergeObj.mergeBlocks(gap1list.get(0), gap2list.get(0)); 
				
				for (SyBlock blk : cblkVec) blockVec.add(blk);
			}
		}
		catch (Exception e) {ErrorReport.print(e, "trim block"); }
		}
		/**************************************************/
		private class CoSet {
			private Vector <SyHit> setVec = new Vector <SyHit> ();
			
			private void add(SyHit ht) {setVec.add(ht);}

			private CoSet(int num) {}
		}
	}
	/*************************************************************
	 * All blocks for grp-grp, both orient
	 */
	private class FinishBlock {
		private RWdb rwObj;
		
		private FinishBlock(RWdb rwObj) {
			this.rwObj = rwObj;
		}
		private void doChrChrBlkFinish(int grpIdx1, int grpIdx2) {
		try {
			for (SyBlock blk : blockVec) finalBlock2(blk);
				
			if (grpIdx1 == grpIdx2) rmDiagBlocks();
			
			mergeBlocks(); 					// always merge contained; conditionally merge olap/close
			
			if (bStrict) strictRemoveBlocks();
			
			// Finalize for DB save
			Collections.sort(blockVec);	    // Sort on start1 for final block# (sorted before Merge, but may have changed)
			
			String msg= String.format("%-6s %-6s:", mProj1.getGrpFullNameFromIdx(grpIdx1), mProj2.getGrpFullNameFromIdx(grpIdx2));
			int blkNum=1;					// final block numbering
			for (SyBlock b : blockVec) { 
				b.mGrpIdx1 = grpIdx1;
				b.mGrpIdx2 = grpIdx2;
				b.mBlkNum = blkNum++;
				
				if ((bOrient || bStrict) && b.orient.equals(orientSame)) cntGrpSame++; else cntGrpDiff++; // for bVerbose
				if (bTraceBlks) msg +=  b.tBlk + "->" + b.mBlkNum + "; "; // for trace
			}
			if (bTraceBlks && blockVec.size()>0) Globals.prt(msg);
			
			rwObj.saveBlocksToDB(blockVec, false);
			if (isSelf) rwObj.saveBlocksToDB(blockVec, true);
			
			totalBlocks += blockVec.size();
		}
		catch (Exception e) {ErrorReport.print(e, "Synteny main finish"); bSuccess = false;}
		}
		
		/***********************************************************
		 * This is called on all final blocks from BuildBlock, and any changed blocks from Merge
		 *********************************************************/
		private void finalBlock2(SyBlock blk) {
			for (SyHit h : blk.hitVec) h.mDPUsedFinal = h.mDPUsed = false;	
				
			blk.setActualCoords();				// Change from midpoint to actual coords
			blk.avgGap();						// saved to DB; sorted by hitVec
			
			blk.mCorr1 = hitCorrelation(blk.hitVec); 
			blk.hasChg = false;
		}
		
		/*****************************************************
		 * Merge final blocks 
		 * Run on chr-chr pair (both orients) before strict (HitsVec cannot be used because maybe only one orient)
		 */
		private void mergeBlocks() {
			cntGrpMerge = 0;
			Collections.sort(blockVec);  // Sort blocks on start coord
			
			// Always do for contained
			Merge mergeObj = new Merge(Merge.CONTAINED, blockVec,  bOrient, pMindots, gap2list.get(0)); 
			blockVec = mergeObj.mergeBlocks(0, 0); 	// returned merged contained blocks
			
			for (SyBlock blk : blockVec) {						
				if (blk.hasChg) {
					finalBlock2(blk);
					cntTrIn++;
				}
			}
			cntTrCoset += mergeObj.cntCoset; cntTrSkip += mergeObj.cntSkip;
			if (pMerge.equals("0")) return;		// Contain only
			
			// 0 Contain, 1 Overlap
			int nb = blockVec.size();
			
			mergeObj = new Merge(Merge.OLAP, blockVec, bOrient, pMindots, gap2list.get(0)); // T=overlap or close; send blocks; perform overlap
		
			if (pMerge.equals("1"))
				blockVec = mergeObj.mergeBlocks(0, 0);  // combine small overlapping 
			else
				blockVec = mergeObj.mergeBlocks(gap1list.get(0), gap2list.get(0)); // return merged close blocks
						  
			for (SyBlock blk : blockVec) {						
				if (blk.hasChg) {
					finalBlock2(blk);
					cntTrOlap++;
				}
			}
			cntGrpMerge += nb-blockVec.size();
		}
		
		/******************************************
		 * Run on final chr-chr pair (both orients) after merge 
		 * Coset blocks are not on final Merge sets unless they are merged with blocks>pMindots
		 * So just need to recalculate #Cosets not in blocks
		 *****************************************/
		private void strictRemoveBlocks() {
		try {
			int cntRm=0;
			for (SyBlock blk : blockVec) {
				if (blk.nHits<pMindots) {
					if (blk.mCase.equals("N")) {cntSetNB++; cntSetNBhits += blk.nHits;} // Could be trimmed that reduced it
					cntRm++;
					blk.mCase = "X";
				}
			}
			if (cntRm>0) { // rebuild blockVec
				Vector <SyBlock> xVec = new Vector <SyBlock> ();
				for (SyBlock blk : blockVec) {
					if (!blk.mCase.equals("X")) xVec.add(blk);
					else blk.clear();
				}
				blockVec = xVec;
			}
		}
		catch (Exception e) {ErrorReport.print(e, "trim block"); }
		}
		
		/**************************************************
		* Remove blocks that run along the diagonal with few genes; Hsa has many, Arab none
		* Note: even if a block is retained, it may be removed in strictRemove
		*/
		private void rmDiagBlocks(){ 
			Vector<SyBlock> out = new Vector<SyBlock>();
			for (SyBlock b : blockVec){
				int gap = Math.max(b.mS1,b.mS2) - Math.min(b.mE1,b.mE2); 
				if (gap>0) {
					out.add(b);
					continue;
				}
				gap = -gap;
				int len = b.mE2-b.mS2+1;
				double ratio = (double)gap/(double)len;
				if (bTrace) Globals.prt(String.format("Rm %s %,10d %,10d %.6f  ", Globals.toTF(ratio>=fDiag), gap, len, ratio) + b.toString());
				
				if (ratio<fDiag) { 			// 0.4
					out.add(b);
					continue;
				}
				if (ratio<fGeneDiag) { 		// 0.8
					int cntG=0, cntNG=0;
					for (SyHit ht : b.hitVec) {
						if (ht.nGenes==2) cntG++; 
						else 		      cntNG++; // g1 is not counted
					}
					if (bTrace) Globals.prt(String.format("Rm %s %d %d", Globals.toTF(cntG<=cntNG*2), cntG, cntNG));
					
					if (cntG > cntNG*2) {	// 2x genes vs non-gene
						out.add(b);
						continue;
					}
				}
				cntTrRmDiag++;
			}
			blockVec = out;
		}
	}
}

