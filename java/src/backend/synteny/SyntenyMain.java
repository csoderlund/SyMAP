package backend.synteny;

import java.io.File;
import java.util.Vector;
import java.util.Collections;
import java.util.TreeSet;
import java.util.HashSet;

import backend.Constants;
import backend.Utils;
import database.DBconn2;
import symap.manager.Mpair;
import symap.manager.Mproject;
import util.Cancelled;
import util.ErrorCount;
import util.ErrorReport;
import util.ProgressDialog;
import util.Utilities;

/**************************************************
 * Computes synteny
 * CAS500 1/2020 change all MySQl Gets to digits.
 * CAS522 removed FPC
 * CAS533 overlapping blocks was not working right; rearranged some
 * CAS534 the parameters from SyProps that never changed are hard-coded here now
 * CAS560 fix hits that only have closeness on one side; tidy up; move save/write to new RWdb
 */

public class SyntenyMain {
	private boolean bTrace = false; // symap.Globals.TRACE
	private boolean bNewBlockCoords = Constants.NEW_BLOCK_COORDS;
	private boolean bStrictOrient = false; // CAS560 - to conform to standard definition, must be same orientation
	
	private ProgressDialog mLog;
	private DBconn2 dbc2, tdbc2;	
	private Mproject mProj1, mProj2;
	private Mpair mp;
	private int mPairIdx;
	private String resultDir;
	private boolean bIsSelf = false;
	
	// Constants set in setProperties
	final private int fMindots_keepbest = 3; // to be merged into one >= pMinDots
	final private int fMingap = 1000; 		// minimum gap param considered in binary search
	
	private int pMindots;
	private int mMindotsA, mMindotsB, mMindotsC, mMindotsD; // For different correlations 
	private int mMaxgap1, mMaxgap2;  						// mProj1.getLength()/(Math.sqrt(nHits)));
	private float mCorr1A, mCorr1B, mCorr1C, mCorr1D; 
	private float mCorr2A, mCorr2B, mCorr2C, mCorr2D; 
	private float mAvg1A, mAvg2A;							
	private int ngaps;
	private Vector<Integer> gap1list = new Vector<Integer>(); // Set the decreasing gap search points
	private Vector<Integer> gap2list = new Vector<Integer>();
	
	// Global DS
	private Vector <SyHit>   hitVec;    // chr-chr hits; populated in RWdb for each new chr-chr; CAS560 make global
	private Vector <SyBlock> blockVec;  // blocks chr-chr pair; recreated on each new chr-chr; CAS560 make global
	private RWdb rwObj;					// CAS560 moved RWdb methods to RWdb class	
	private boolean bSuccess = true;
	
	private long startTime;
	private int totalBlocks=0; // CAS560 final
	
	private int tcntMerge=0; // CAS560 bTrace variables
	// CAS560 dead code mBlksByCase.put(blk.mCase, mBlksByCase.get(blk.mCase)+1);
		
	public SyntenyMain(DBconn2 dbc2, ProgressDialog log, Mpair mp) {
		this.dbc2 = dbc2;
		this.mLog = log;
		this.mp = mp;
	}
	/******************************************************************/
	public boolean run(Mproject pj1, Mproject pj2) {
	try {
	/** Setup ***************************************/
		mProj1 = pj1;
		mProj2 = pj2;
		String proj1Name = pj1.getDBName(), proj2Name = pj2.getDBName();
		int mProj1Idx    = pj1.getIdx(),    mProj2Idx = pj2.getIdx(); 
		
		startTime = Utils.getTime();
		mLog.msg("Finding synteny for " + proj1Name + " and " + proj2Name); 
		tprt("Trace on");
		
		resultDir = Constants.getNameResultsDir(proj1Name, proj2Name);	
		if (!(new File(resultDir)).exists()) {
			mLog.msg("Cannot find result directory " + resultDir);
			ErrorCount.inc();
			return false;
		}
		
		if (mProj1.hasOrderAgainst()) mLog.msg("Order against " + mProj1.getOrderAgainst());
		else if (mProj2.hasOrderAgainst()) mLog.msg("Order against " + mProj2.getOrderAgainst());
		
		bIsSelf = (mProj1.getIdx() == mProj2.getIdx());
		
		tdbc2 = new DBconn2("Synteny-" + DBconn2.getNumConn(), dbc2);
		
		mPairIdx = tdbc2.executeCount("SELECT idx FROM pairs WHERE proj1_idx='" + mProj1Idx + "' AND proj2_idx='" + mProj2Idx +"'");
		if (mPairIdx == 0) {
			mLog.msg("Cannot find project pair in database for " + mProj1.getDisplayName() + "," + mProj2.getDisplayName());
			ErrorCount.inc(); tdbc2.close();
			return false;
		}
		tdbc2.executeUpdate("DELETE FROM blocks WHERE pair_idx=" + mPairIdx);
		
		rwObj = new RWdb(mPairIdx, mProj1Idx, mProj2Idx, tdbc2);
		
		setProperties(); 
		if (Cancelled.isCancelled() || !bSuccess) {tdbc2.close(); return false;} // CAS535 there was no checks
		
	/** Main loop ***************************************/
		int nGrpGrp = mProj1.getGrpSize() * mProj2.getGrpSize();
		Utils.prtNumMsg(mLog, nGrpGrp, "group-x-group pairs to analyze");
		
		for (int grpIdx1 : mProj1.getGrpIdxMap().keySet()) {
			for (int grpIdx2 : mProj2.getGrpIdxMap().keySet()) {
				if (!bIsSelf || (bIsSelf && grpIdx1 <= grpIdx2)) { // CAS560 combine 2 ifs, remove bInterrupt
					
					if (!bStrictOrient) doChrChrSynteny(grpIdx1,grpIdx2, "");
					else { 				// CAS560 add orient - not working, just thinking
						doChrChrSynteny(grpIdx1,grpIdx2, "+/+"); 
						doChrChrSynteny(grpIdx1,grpIdx2, "+/-");
					}
					
					if (Cancelled.isCancelled() || !bSuccess) {tdbc2.close();return false;}
				}
				nGrpGrp--;
				String t = Utilities.getDurationString(Utils.getTime()-startTime);
				System.err.print(nGrpGrp + " pairs remaining... (" + t + ")   \r"); 
			}
		}
		System.err.print("                                                   \r"); 
		
		tprt(tcntMerge, "Merged");
		
		Utils.prtNumMsg(mLog, totalBlocks, "Blocks");
		Utils.timeDoneMsg(mLog, "Synteny", startTime); 
		
	/** Self **************************************************/
		if (bIsSelf) rwObj.symmetrizeBlocks();	
		if (Cancelled.isCancelled() || !bSuccess) {tdbc2.close();return false;}
		
	/** Order *******************************************************/ 
		if (mProj1.hasOrderAgainst()) {
			String orderBy = mProj1.getOrderAgainst();
			if (orderBy != null && orderBy.equals(mProj2.getDBName())) {
				OrderAgainst obj = new OrderAgainst(mProj1, mProj2, mLog, tdbc2); // CAS505 in new file
				obj.orderGroupsV2(false);	// CAS559 remove the old orderGroups	
			}	
		}
		else if (mProj2.hasOrderAgainst()){
			String orderBy = mProj2.getOrderAgainst();
			if (orderBy != null && orderBy.equals(mProj1.getDBName())) {
				OrderAgainst obj = new OrderAgainst(mProj1, mProj2, mLog, tdbc2);
				obj.orderGroupsV2(true);
			}	
		}	
		if (Cancelled.isCancelled() || !bSuccess) {tdbc2.close(); return false;}
		
	/** Finish **********************************************************/
		rwObj.writeResultsToFile(mLog, resultDir);
		tdbc2.close();
		
		return bSuccess; // Full time in calling routine
	}
	catch (Exception e) {ErrorReport.print(e, "Computing Synteny"); return false; }
	}
	
	/************************************************************************
	 * Main synteny method
	 *******************************************************************/
	private void doChrChrSynteny(int grpIdx1, int grpIdx2, String orient)  {
	try {
		boolean isGrpSelf = (grpIdx1 == grpIdx2);
		
		// Read hits
		hitVec = rwObj.loadHits(grpIdx1, grpIdx2, isGrpSelf, orient);
		if (hitVec==null) {bSuccess=false; return;}
		if (hitVec.size() < pMindots) return; // not error
		
		Collections.sort(hitVec); // CAS560 moved from chainFinder
		for (int i = 0; i < hitVec.size(); i++) hitVec.get(i).mI = i;
		
		tprt("Groups " + mProj1.getGrpNameFromIdx(grpIdx1) + " " + 
						 mProj2.getGrpNameFromIdx(grpIdx2) + " Hits " + hitVec.size());
		
		// Create blocks
		blockVec = new Vector<SyBlock>();
		chainFinder();	
		
		// Finalize
		if (isGrpSelf) blockVec = removeDiagonalBlocks();
	
		blockVec = mergeBlocks();
		
		int num = 1;	
		for (SyBlock b : blockVec) { // CAS560 merge two loops	
			b.mGrpIdx1 = grpIdx1;
			b.mGrpIdx2 = grpIdx2;
			
			b.mNum = num++;
			tprtBlk(b);
			if (bNewBlockCoords) b.updateCoords(); // CAS533 add, was using mid-point which put dot-plot hit over boundary
		}
		
		// Save
		rwObj.saveBlocksToDB(blockVec);
		
		totalBlocks += blockVec.size();
	}
	catch (Exception e) {ErrorReport.print(e, "Synteny main"); bSuccess = false;}
	}
	
	/*************************************************************************
	 * Called from doChrChrSynteny
	 * Move 
	// This routine organizes the "binary search" of the gap parameter space
	// chainFinder
	 * 		if goodChainsExist
	 * 			longestChain
	 * 				goodBlock
	 * 		then 
	 * 			binarySearch through progressively lower gaps
	 * 				cullchain
	 * 			  		longestChain
	 * 						goodBlock
	 *************************************************************************/
	private void chainFinder() { // CAS560 remove arg bestblk, always null
		for (int i = 0; i < ngaps; i++) {
			int low1 = gap1list.get(i);
			int low2 = gap2list.get(i);
			int high1 = (i > 0 ? gap1list.get(i-1) : low1);
			int high2 = (i > 0 ? gap2list.get(i-1) : low2);

			while (goodChainsExist(low1, low2)) 
				binarySearch(low1, high1, low2, high2);	
		}
	}
	
	private void binarySearch(int low1, int high1, int low2, int high2){
		int gap1 = (high1 + low1)/2;
		int gap2 = (high2 + low2)/2;
		
		int cnt=0;
		while ( high1-low1 > fMingap && high2-low2 > fMingap){ // 1000
			cnt++;
			if (goodChainsExist(gap1,gap2)){
				tprt(String.format("Good %3d LH1 %,6d %,6d  LH2 %,6d %,6d   Gap %,6d %,6d", 
						cnt, low1, high1, low2, high2, gap1, gap2));
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
		tprt(String.format("Cull  %3d LH1 %,6d %,6d  LH2 %,6d %,6d   Gap %,6d %,6d", 
				cnt, low1, high1, low2, high2, gap1, gap2));
		cullChains(low1,low2); // low1,2 had good blocks
		tprt(blockVec.size(), "Blocks");
	}
	/************************************************************/
	private boolean goodChainsExist(int gap1,int gap2){
		SyBlock blk = new SyBlock();
		while (longestChain(gap1, gap2, blk, 1)){ // add hits to blk
			if (goodBlock(blk)){				 // discarded after this
				resetDPUsed();
				return true;
			}
			blk.clear();
		}
		resetDPUsed();
		return false;
	}
	private void cullChains(int gap1,int gap2){
		SyBlock blk = new SyBlock();
		while (longestChain(gap1, gap2, blk, 2)){  // add hits to blk 
			if (goodBlock(blk)){
				blockVec.add(blk);				   // add new block to blockVec
				for (SyHit h : blk.mHits) 
					h.mDPUsedFinal = true;
				blk = new SyBlock();  // CAS560 move inside if, and clear if fail
			}
			else blk.clear();
		}
		resetDPUsed();
	}
	private void resetDPUsed(){
		for (SyHit h : hitVec) h.mDPUsed = h.mDPUsedFinal;
	}
	/************************************************************
	 * Uses !mDPUsed from hitVec to find longest chain; called by cullChain and goodChainExists
	 * goodChainExists discards good block, cullchain keeps it
	 ***********************************************************/
	private boolean longestChain(int gap1, int gap2, SyBlock blk, int from) {
		for (SyHit hit : hitVec) {
			hit.mNDots 	  = 1;
			hit.mDPScore  = 1;
			hit.mPrevI 	  = -1;
		}
		
		SyHit saveH1 = hitVec.get(0);
		Vector<SyHit> links = new Vector<SyHit>();
		
		for (int i = 0; i < hitVec.size(); i++) { 
			SyHit h1 = hitVec.get(i);
			if (h1.mDPUsed) continue;
			
			links.clear(); 
			for (int j = i-1; j >= 0; j--) {
				SyHit h2 = hitVec.get(j);
				
				if (h2.mDPUsed) continue;
				
				if (h1.mPos2 - h2.mPos2 > mMaxgap2) break; // crucial optimization from sorting on pos2
				
				if (Math.abs(h1.mPos1 - h2.mPos1) > mMaxgap1) continue;	
				
				links.add(h2);			
			}
			if (links.size() < 0) continue; // CAS560 was just checking 0; check fMindots_keepbest
		
			// Score from h1...
			int maxscore = 0;
			SyHit bestNext = null;
			
			for (SyHit h : links) {
				float d1 = Math.abs(((float)h.mPos1 - (float)h1.mPos1)/(float)gap1);
				float d2 = Math.abs(((float)h.mPos2 - (float)h1.mPos2)/(float)gap2);
				int gap_penalty = (int)(d1 + d2);
				
				int dpscore = 1 + h.mDPScore - gap_penalty;
				
				if (dpscore > maxscore) {
					maxscore = dpscore;
					bestNext = h;
							
					String x = String.format("%d Blk %d", from, blk.nBlk);
					tprt(String.format("%-10s Links %,d (h1 %3d %,10d %,10d) MaxScore %,4d  (bestNext %3d %,10d %,10d  dots %,d)", 
							 x, links.size(),  h1.nHit, h1.mPos1, h1.mPos2, maxscore,  bestNext.nHit, bestNext.mPos1, bestNext.mPos2, bestNext.mNDots));
				}
			}
			if (maxscore > 0) {
				h1.mDPScore = maxscore;
				h1.mPrevI   = bestNext.mI;
				h1.mNDots  += bestNext.mNDots;
				
				if (saveH1 == null) saveH1 = h1;
				else if (h1.mDPScore > saveH1.mDPScore) saveH1 = h1;
				else if (h1.mDPScore >= saveH1.mDPScore && h1.mNDots > saveH1.mNDots) saveH1 = h1;			
			}
			else {
				h1.mNDots 	= 1;
				h1.mDPScore = 1;
				h1.mPrevI 	= -1;
			}
		} // end hit loop
		
		if (saveH1==null || saveH1.mNDots < fMindots_keepbest) return false;
		
		SyHit h = saveH1;
		
		int s1 = h.mPos1;
		int e1 = h.mPos1;
		int s2 = h.mPos2;
		int e2 = h.mPos2;
				
		while (true) {
			h.mDPUsed = true;
			
			blk.addHit(h); // add to block hits
			
			s1 = Math.min(s1, h.mPos1);
			s2 = Math.min(s2, h.mPos2);
			e1 = Math.max(e1, h.mPos1);
			e2 = Math.max(e2, h.mPos2);
			
			if (h.mPrevI >= 0) h = hitVec.get(h.mPrevI);
			else break;
		}
		blk.setEnds(s1, e1, s2, e2);
		
		return true;
	}
	private boolean goodBlock(SyBlock blk){
		int nDots = blk.mHits.size();
		
		if (nDots < mMindotsA) return false;
		
		blockCorrelations(blk);
		
		float avg1 = (blk.mE1 - blk.mS1)/nDots;
		float avg2 = (blk.mE2 - blk.mS2)/nDots;
		
		// The mCase does not mean anything except not to reject
		if (nDots >= mMindotsA && avg1 <= mAvg1A && avg2 <= mAvg2A
				&& Math.abs(blk.mCorr1) >= mCorr1A && Math.abs(blk.mCorr2) >= mCorr2A) blk.mCase = "A";
		else if (nDots >= mMindotsB	
				&& Math.abs(blk.mCorr1) >= mCorr1B && Math.abs(blk.mCorr2) >= mCorr2B) blk.mCase = "B";
		else if (nDots >= mMindotsC	
				&& Math.abs(blk.mCorr1) >= mCorr1C && Math.abs(blk.mCorr2) >= mCorr2C) blk.mCase = "C";
		else if (nDots >= mMindotsD	
				&& Math.abs(blk.mCorr1) >= mCorr1D && Math.abs(blk.mCorr2) >= mCorr2D) blk.mCase = "D";
		
		
		// ttt
		if (blk.nBlk==1 || !blk.mCase.equals("R")) {
			tprtBlk(blk);
			for (SyHit h : blk.mHits)
			   tprt(String.format("Hit %3d prev %3d  %,6d  %,3d", h.nHit, h.mPrevI, h.mDPScore, h.mNDots));
		}
		else tprt(String.format("badBlock %d %s  Corr %6.3f %6.3f  Dots %,d", 
				blk.nBlk, blk.mCase, blk.mCorr1, blk.mCorr2, nDots));
				
		
		return (!blk.mCase.equals("R"));
	}
	
	/***************************************************
	 * from 2006 publication
	 *  mCorr1 is the approximate linearity of the anchors in the chain as measured by the PCC; 
	 *  mCorr2 is the correlation of all the anchors in the chain’s bounding rectangle 
	 */
	private void blockCorrelations(SyBlock blk) {
		
		blk.mCorr1 = hitCorrelation(blk.mHits); // chain hits 
		
		Vector<SyHit> hits2 = new Vector<SyHit>();
		for (SyHit h : hitVec) {
			if (blk.isContained(h.mPos1, h.mPos2)) hits2.add(h);
		}
		blk.mCorr2 = hitCorrelation(hits2); // all hits in surrounding block
	}
	private float hitCorrelation(Vector<SyHit> hits) {//Pearson correlation coefficient (PCC) of midpoints
		if (hits.size() <= 2) return 0;
		
		double N = (double)hits.size();
		double xav = 0, xxav = 0, yav = 0, yyav = 0, xyav = 0;
		
		for (SyHit h : hits) {
			double x = (double)h.mPos1;
			double y = (double)h.mPos2;
			xav  += x;   yav  += y;
			xxav += x*x; yyav += y*y; xyav 	+= x*y;
		}	
		xav  /= N; yav 	/= N;
		xxav /= N; yyav /= N; xyav /= N;
		
		double sdx = Math.sqrt(Math.abs(xxav - xav*xav));
		double sdy = Math.sqrt(Math.abs(yyav - yav*yav));
		double sdprod = sdx*sdy;
		
		double corr = 0;
		if (sdprod > 0) corr = ((xyav - xav*yav)/sdprod);
		if (Math.abs(corr) > 1) corr = (corr > 0) ? 1.0 : -1.0;
		
		return (float)corr;
	}
	/**************************************************
	 * bIfSelf
	 */
	private Vector<SyBlock> removeDiagonalBlocks(){
		Vector<SyBlock> out = new Vector<SyBlock>();
		for (SyBlock b : blockVec){
			if (!intervalsOverlap(b.mS1, b.mE1, b.mS2, b.mE2, 0)){
				out.add(b);
			}
		}
		return out;
	}
	
	/***************************************************
	 * Merge blocks
	 */
	private Vector<SyBlock> mergeBlocks() {
		int nprev = blockVec.size() + 1;

		while (nprev > blockVec.size()){
			nprev = blockVec.size();
			blockVec = mergeBlocksSingleRound();
		}
		return blockVec;
	}
	
	private Vector<SyBlock> mergeBlocksSingleRound() {
		if (blockVec.size() <= 1) return blockVec;
		
		boolean doMerge = mp.isMerge(Mpair.FILE);
		int maxjoin1 = 0, maxjoin2 = 0;
		float joinfact = 0;
		
		if (doMerge) {		
			maxjoin1 = 1000000000;
			maxjoin2 = 1000000000;
			joinfact = 0.25f;
		}
		
		SyGraph graph = new SyGraph(blockVec.size());
		
		for (int i = 0; i < blockVec.size(); i++) {
			SyBlock b1 = blockVec.get(i);
			
			int w11 = b1.mE1 - b1.mS1;
			int w12 = b1.mE2 - b1.mS2;				
			
			for (int j = i + 1; j < blockVec.size(); j++){
				SyBlock b2 = blockVec.get(j);

				int w21 = b2.mE1 - b2.mS1;
				int w22 = b2.mE2 - b2.mS2;

				int gap1 = Math.min(maxjoin1,(int)(joinfact*Math.max(w11,w21)));
				int gap2 = Math.min(maxjoin2,(int)(joinfact*Math.max(w12,w22)));
				
				if (doMerge) {
					if (intervalsOverlap(b1.mS1,b1.mE1,b2.mS1,b2.mE1,gap1) && 
						intervalsOverlap(b1.mS2,b1.mE2,b2.mS2,b2.mE2,gap2) )
					{
						graph.addNode(i,j);
						tcntMerge++;
					}
				}
				else {// only merge if contained	
					if (intervalContained(b1.mS1,b1.mE1,b2.mS1,b2.mE1) && 
						intervalContained(b1.mS2,b1.mE2,b2.mS2,b2.mE2) )
					{
						graph.addNode(i,j);
						tcntMerge++;
					}					
				}
			}
		}
		
		HashSet<TreeSet<Integer>> blockSets = graph.transitiveClosure();
		
		Vector<SyBlock> mergedBlocks = new Vector<SyBlock>();
		
		for (TreeSet<Integer> s : blockSets) {
			SyBlock bnew = null;
			for (Integer i : s) {
				if (bnew == null) 
					bnew = blockVec.get(i);
				else
					bnew.mergeWith(blockVec.get(i));
			}
			mergedBlocks.add(bnew);
		}
		return mergedBlocks;
	}
	
	/***************************************************************
	 * All values are final 
	 ****************************************************/
	private void setProperties() { // CAS534 move constants from SyProps
	try {
		pMindots = mp.getMinDots(Mpair.FILE);
		if (pMindots <= 1) {
			symap.Globals.eprt("Min dots parameter(s) out of range (<=1)");
			bSuccess=false;
			return;
		}
		
		mMindotsA = mMindotsB = pMindots; // default 7
		mMindotsC = 3*pMindots/2; 		  // default 10
		mMindotsD = 2*pMindots; 		  // default 14

		mCorr1A = 0.8f;  // For chain
		mCorr1B = 0.98f; 
		mCorr1C = 0.96f; 
		mCorr1D = 0.94f; 

		mCorr2A = 0.7f; // For all hits in surrounding box
		mCorr2B = 0.9f; 
		mCorr2C = 0.8f; 
		mCorr2D = 0.7f; 
		
		// CAS560 merge method with above; CAS534 was getting defaults of 0; then writing back to mProps.setProperty; 
		int nHits = tdbc2.executeCount("select count(*) as nhits from pseudo_hits "
				+ " where proj1_idx='" + mProj1.getIdx() + "' and proj2_idx='" + mProj2.getIdx() + "'");;
		Utils.prtNumMsg(mLog, nHits, "Total hits");
		
		mMaxgap1 = (int)( ((float)mProj1.getLength())/(Math.sqrt(nHits)));
		mMaxgap2 = (int)( ((float)mProj2.getLength())/(Math.sqrt(nHits)));
		
		mAvg1A = ((float)mMaxgap1)/15;
		mAvg2A = ((float)mMaxgap2)/15;
		
		gap1list.add(mMaxgap1/2);
		gap2list.add(mMaxgap2/2);
		
		// CAS560 moved from chain finder - does not have to be computed for every chr-chr since uses genLen
		ngaps = 1; 
		while (gap1list.get(ngaps-1) >= 2*fMingap && gap2list.get(ngaps-1) >= 2*fMingap){
			gap1list.add(gap1list.get(ngaps-1)/2);
			gap2list.add(gap2list.get(ngaps-1)/2);	
			ngaps++;
		}
		// ttt
		tprt(mMaxgap1, "Max Gap1");
		tprt(mMaxgap2, "Max Gap2");
		tprt((int)mAvg1A, "mAvg1A");
		tprt((int)mAvg2A, "mAvg2A");
		
		tprt(ngaps, "Gaps");
		String m="Gap1: ";
		for (int g : gap1list) m += String.format("%,d",g) + " ";
		tprt(m);
		m="Gap2: ";
		for (int g : gap2list) m += String.format("%,d",g) + " ";
		tprt(m);
	}
	catch (Exception e) {ErrorReport.print(e, "Set properties for synteny"); bSuccess=false;}
	}
	
	private boolean intervalsOverlap(int s1,int e1, int s2, int e2, int max_gap) {
		int gap = Math.max(s1,s2) - Math.min(e1,e2);
		return (gap <= max_gap);
	}
	private boolean intervalContained(int s1,int e1, int s2, int e2){
		return ( (s1 >= s2 && e1 <= e2) || (s2 >= s1 && e2 <= e1));
	}
	
	// TRACE
	private void tprtBlk(SyBlock b) {
		String msg = String.format("Block %3d (Tmp %5d) %s #Hits %4d  %6.3f %6.3f  %,10d %,10d    %,10d %,10d ", 
				b.mNum, b.nBlk, b.mCase, b.mHits.size(), b.mCorr1, b.mCorr2, b.mS1, b.mE1, b.mS2, b.mE2);
		tprt(msg);
	}
	private void tprt(String msg) {
		if (bTrace) System.out.println(msg + "                ");}
	private void tprt(int num, String msg) {
		if (bTrace && num>0) System.out.format("%,10d %s          \n", num, msg);}
}

