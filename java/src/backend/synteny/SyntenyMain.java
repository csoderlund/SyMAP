package backend.synteny;

import java.io.File;
import java.util.Vector;
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
 * CAS560 fix hits that only have closeness on one side; tidy up; move save/write to new RWdb
 * CAS567 more removing misleading code; moved mergeBlocks to Merge (was SyGraph); add orient
 */
public class SyntenyMain {
	public static boolean bTrim = false;  // -bf  experimental; CAS567 todo fix ends and little close merges
	public static boolean bTrace = false; // -bt trace; set at command line 
	
	protected static final String orientSame = "==";	// must be ++ or --;  CAS567 
	protected static final String orientDiff = "!=";	// must be +- or -+
	protected static final String orientMix  = "";		// can be mixed

	private ProgressDialog mLog;
	private DBconn2 dbc2, tdbc2;	
	private int mPairIdx;
	private boolean bOrient = false; // to conform to standard definition, must be same orientation
	private boolean bMerge = false;  // merge blocks
	
	private Mproject mProj1, mProj2;
	private Mpair mp;
	
	private String resultDir;
	private boolean bIsSelf = false, bOrderAgainst=false;
	
	// Cutoffs
	final private int fMindots_keepbest = 3; // to be merged into one >= pMinDots
	final private int fMingap = 1000; 		 // minimum gap param considered in binary search
	
	protected int pMindots;					// the only user parameter
	private int mMindotsA, mMindotsB, mMindotsC, mMindotsD; // Set using pMinDots; used with mCorr 
	private final float mCorr1A=0.8f, mCorr1B=0.98f, mCorr1C=0.96f, mCorr1D=0.94f; // For chain
	private final float mCorr2A=0.7f, mCorr2B=0.9f,  mCorr2C=0.8f,  mCorr2D=0.7f; // For all hits in surrounding box
	
	private int mMaxgap1, mMaxgap2;  						  // mProj1.getLength()/(Math.sqrt(nHits)));
	private float mAvg1A, mAvg2A;							  // mMapgap/15
	private int ngaps;										  // Depends on genome length && number hits
	private Vector<Integer> gap1list = new Vector<Integer>(); // Set the decreasing gap search points 
	private Vector<Integer> gap2list = new Vector<Integer>(); 
	
	// Global DS
	private RWdb rwObj;					// all read/write db methods
	private Vector <SyHit>   hitVec;    // chr-chr hits; populated in RWdb for each new chr-chr; 
	private Vector <SyBlock> blockVec;  // blocks chr-chr pair; recreated on each new chr-chr; 
	private String orient="";			// block is assigned its orient; CAS567	
	
	private boolean bSuccess = true;
	private long startTime;
	private int totalBlocks=0, cntMerge=0, cntSame=0, cntDiff=0, cntGrpMerge=0, cntGrpSame=0, cntGrpDiff=0; 
	
	public SyntenyMain(DBconn2 dbc2, ProgressDialog log, Mpair mp) {
		this.dbc2 = dbc2;
		this.mLog = log;
		this.mp = mp;
		mPairIdx = mp.getPairIdx();
		bOrient = mp.isOrient(Mpair.FILE);
		bMerge  = mp.isMerge(Mpair.FILE);
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
		Globals.tprt("Trace on");
		
		resultDir = Constants.getNameResultsDir(proj1Name, proj2Name);	
		if (!(new File(resultDir)).exists()) {
			mLog.msg("Cannot find result directory " + resultDir);
			ErrorCount.inc();
			return false;
		}
		
		if (mp.isOrder1(Mpair.FILE) || mp.isOrder2(Mpair.FILE)) bOrderAgainst=true; // CAS568 do not need mlog.msg here
		
		bIsSelf = (mProj1.getIdx() == mProj2.getIdx());
		
		tdbc2 = new DBconn2("Synteny-" + DBconn2.getNumConn(), dbc2);
		tdbc2.executeUpdate("DELETE FROM blocks WHERE pair_idx=" + mPairIdx);
		
		rwObj = new RWdb(mPairIdx, mProj1Idx, mProj2Idx, tdbc2);
		
		setCutoffs(); 
		if (Cancelled.isCancelled() || !bSuccess) {tdbc2.close(); return false;} 
		
	/** Main loop through chromosome pairs ***************************************/
		int nGrpGrp = mProj1.getGrpSize() * mProj2.getGrpSize();
		int maxGrp = nGrpGrp;
		
		if (Constants.VERBOSE) 	Utils.prtNumMsg(mLog, nGrpGrp, "group-x-group pairs to analyze");
		else 					Globals.rprt("group-x-group pairs to analyze");
		
		for (int grpIdx1 : mProj1.getGrpIdxMap().keySet()) {
			for (int grpIdx2 : mProj2.getGrpIdxMap().keySet()) {
				nGrpGrp--;
				if (bIsSelf && grpIdx1>=grpIdx2) continue;
				
				SyBlock.cntBlk = 1;
				cntGrpMerge=cntGrpSame=cntGrpDiff=0;
				blockVec = new Vector<SyBlock>(); // create for both orients so can be number sequentially
				
				if (!bOrient) doChrChrSynteny(grpIdx1,grpIdx2, orientMix);
				else { 									// restrict to same orientation; CAS567  
					doChrChrSynteny(grpIdx1,grpIdx2, orientSame); 
					doChrChrSynteny(grpIdx1,grpIdx2, orientDiff);
				}
				if (Cancelled.isCancelled() || !bSuccess) {tdbc2.close();return false;}
				
				doChrChrFinish(grpIdx1,grpIdx2); 			// save to DB; need to be separate for bOrient; CAS567 
				
				cntSame += cntGrpSame; cntDiff += cntGrpDiff; cntMerge += cntGrpMerge;
				if (Constants.VERBOSE && blockVec.size()>0) { 		// CAS567 add verbose; CAS568 add size check 
					String msg = String.format("Blocks %-12s", mProj1.getGrpNameFromIdx(grpIdx1) + "-" + mProj2.getGrpNameFromIdx(grpIdx2));
					if (bOrient) msg += String.format("  Orient:  Same %3d   Diff %3d ",cntGrpSame, cntGrpDiff);
					if (bMerge)  msg += String.format("  Merged %3d ", cntGrpMerge);
					Utils.prtIndentNumMsgFile(mLog, 1, blockVec.size(), msg);	
				}
				else {
					String t = Utilities.getDurationString(Utils.getTime()-startTime);
					Globals.rprt(nGrpGrp + " of " + maxGrp + " pairs remaining (" + t + ")"); 
				}
			}
		}	
		Globals.rclear();
		
		if (bIsSelf) rwObj.symmetrizeBlocks();	
		if (Cancelled.isCancelled() || !bSuccess) {tdbc2.close();return false;}
		
		String msg = "Blocks";									// CAS568 move before order
		if (bMerge)  msg += String.format("   %,d Merged ", cntMerge);
		if (bOrient) msg += String.format("   Orient: %,d Same   %,d Diff", cntSame, cntDiff);
		Utils.prtNumMsg(mLog, totalBlocks, msg);
		
		Utils.prtMsgTimeDone(mLog, "Finish Synteny", startTime);

	/** Order *******************************************************/ 
		if (mp.isOrder1(Mpair.FILE)) { // CAS568 get from Mpairs instead of Mproject
			OrderAgainst obj = new OrderAgainst(mp, mLog, tdbc2); 
			obj.orderGroupsV2(false);				
		}
		else if (mp.isOrder2(Mpair.FILE)) {
			OrderAgainst obj = new OrderAgainst(mp, mLog, tdbc2);
			obj.orderGroupsV2(true);
		}	
		if (Cancelled.isCancelled() || !bSuccess) {tdbc2.close(); return false;}
		
	/** Finish **********************************************************/
		rwObj.writeResultsToFile(mLog, resultDir);
		tdbc2.close();
		
		return bSuccess; 								// Full time printed in calling routine
	}
	catch (Exception e) {ErrorReport.print(e, "Computing Synteny"); return false; }
	}
	
	/************************************************************************
	 * Main synteny method
	 *******************************************************************/
	private void doChrChrSynteny(int grpIdx1, int grpIdx2, String o)  {
	try {
		orient = o;	// "", ==, !=
		boolean isGrpSelf = (grpIdx1 == grpIdx2);
		
		// Read hits
		hitVec = rwObj.loadHits(grpIdx1, grpIdx2, isGrpSelf, orient);
		if (hitVec==null) {bSuccess=false; return;}
		if (hitVec.size() < pMindots) return; // not error
		
		Collections.sort(hitVec); 									  // Sort by G2 hit midpoints
		for (int i = 0; i < hitVec.size(); i++) hitVec.get(i).mI = i; // index of the hit in the sorted list

		/** Create blocks **/
		createBlockVec();
	}
	catch (Exception e) {ErrorReport.print(e, "Synteny main"); bSuccess = false;}
	}
	/*************************************************************
	 * All blocks for grp-grp need to be numbered in order for bOrient
	 */
	private void doChrChrFinish(int grpIdx1, int grpIdx2) {
	try {
		// self only
		if (grpIdx1 == grpIdx2) blockVec = removeDiagonalBlocks();
		
		// merge & remove 
		int s = blockVec.size();
		Merge mergeObj = new Merge(blockVec, bOrient, bMerge); // send blocks
		blockVec = mergeObj.mergeBlocks();							 // return merged blocks
		cntGrpMerge += s-blockVec.size();
		
		// finish
		for (SyBlock b : blockVec) b.updateCoords(); // using mid-point which put dot-plot hit over boundary
		Collections.sort(blockVec);					 // sort after merge but before numbering
		
		int blkNum=1;
		for (SyBlock b : blockVec) { 
			b.mGrpIdx1 = grpIdx1;
			b.mGrpIdx2 = grpIdx2;
			
			b.mNum = blkNum++;
			if (bOrient && b.orient.equals(orientSame)) cntGrpSame++; else cntGrpDiff++;
		}
		
		// Save
		rwObj.saveBlocksToDB(blockVec);
		
		totalBlocks += blockVec.size();
	}
	catch (Exception e) {ErrorReport.print(e, "Synteny main finish"); bSuccess = false;}
	}
	/*************************************************************************
	 * Called from doChrChrSynteny where the hitVec contains the hits from grp1 to grp2 
	 * This routine organizes the "binary search" of the gap parameter space
	 *************************************************************************/
	private void createBlockVec() { 
		for (int i = 0; i < ngaps; i++) {
			int low1 = gap1list.get(i);						// gaps are from large to small
			int low2 = gap2list.get(i);
			int high1 = (i > 0 ? gap1list.get(i-1) : low1);
			int high2 = (i > 0 ? gap2list.get(i-1) : low2);

			while (hasGoodChain(low1, low2)) 				// if there is at least one good chain
				binarySearch(low1, high1, low2, high2);		// search them all
		}
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
		
		// cull blocks
		SyBlock blk = new SyBlock(orient);
		while (longestChain(2, low1, low2, blk)){  // add block 
			if (isGoodCorr(blk)){
				blockVec.add(blk);				   // final: add new block to blockVec
				
				for (SyHit h : blk.mHits) h.mDPUsedFinal = true;
				
				blk = new SyBlock(orient); 
			}
			else blk.clear();
		}
		resetDPUsed();
	}
	/************************************************************/
	private boolean hasGoodChain(int gap1, int gap2){ // createBlockVec, binarySearch
		SyBlock blk = new SyBlock(orient); 
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
		
		SyHit savHt1 = null;					// first of final chain
		Vector <SyHit> links = new Vector<SyHit>();
		
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
			if (links.size() == 0) continue; // was <0 CAS567
		
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
				ht1.mPrevI = bestPrev.mI;
				ht1.mNDots += bestPrev.mNDots;
				
				if (savHt1 == null) 					savHt1 = ht1;
				else if (ht1.mScore > savHt1.mScore) 	savHt1 = ht1;
				else if (ht1.mScore >= savHt1.mScore && ht1.mNDots > savHt1.mNDots) savHt1 = ht1;	
			}
			else {
				ht1.mNDots 	= 1;
				ht1.mScore = 1;
				ht1.mPrevI 	= -1;
			}
		} // end hit loop
		
		if (savHt1==null || savHt1.mNDots < fMindots_keepbest) return false; // fMindots_keepBest=3
		
	/* Finalize blk - need to build block for isGoodBlock even if it is then discarded */
		SyHit ht = savHt1; 			// build chain from first dot
		
		int s1 = ht.midG1;	
		int e1 = ht.midG1;
		int s2 = ht.midG2;
		int e2 = ht.midG2;
				
		while (true) {
			ht.mDPUsed = true;
			
			rtBlk.addHit(ht); // add to block hits
			
			s1 = Math.min(s1, ht.midG1);
			s2 = Math.min(s2, ht.midG2);
			e1 = Math.max(e1, ht.midG1);
			e2 = Math.max(e2, ht.midG2);
			
			if (ht.mPrevI >= 0) ht = hitVec.get(ht.mPrevI);
			else break;
		}
		rtBlk.setEnds(s1, e1, s2, e2);
		
		return true;
	}
	/****************************************************/
	private boolean isGoodCorr(SyBlock blk){ // hasGoodChain, binary search;  evaluate block
		int nDots = blk.mHits.size();
		
		if (nDots < mMindotsA) return false;
		
		blockCorrelations(blk);
		
		if (!bOrderAgainst) {	// CAS567 - does not work well for small draft
			if (bOrient && orient.equals(orientSame) && blk.mCorr1<0) return false; 
			if (bOrient && orient.equals(orientDiff) && blk.mCorr1>0) return false;
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
		blk.mCorr1 = hitCorrelation(blk.mHits); // chain hits 
		
		Vector<SyHit> hits2 = new Vector<SyHit>();
		for (SyHit ht : hitVec) {
			if (blk.isContained(ht.midG1, ht.midG2)) hits2.add(ht);
		}
		blk.mCorr2 = hitCorrelation(hits2); // all hits in surrounding block
	}
	// returns -1, 0, or 1 
	private float hitCorrelation(Vector<SyHit> hits) {// Pearson correlation coefficient (PCC) of midpoints
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
	
	/***************************************************************
	 * All values are final 
	 ****************************************************/
	private void setCutoffs() { 
	try {
		pMindots = mp.getMinDots(Mpair.FILE);// default 7
		if (pMindots <= 1) {
			symap.Globals.eprt("Min dots parameter(s) out of range (<=1)");
			bSuccess=false;
			return;
		}
		
		mMindotsA = mMindotsB = pMindots; // default 7
		mMindotsC = 3*pMindots/2; 		  // default 10 if pMindots=7
		mMindotsD = 2*pMindots; 		  // default 14 if pMindots=7

		int nHits = tdbc2.executeCount("select count(*) as nhits from pseudo_hits "
				+ " where proj1_idx='" + mProj1.getIdx() + "' and proj2_idx='" + mProj2.getIdx() + "'");;
		if (Constants.VERBOSE) 	Utils.prtNumMsg(mLog, nHits, "Total hits"); 
		else 					Globals.rprt(String.format("%,10d Total Hits", nHits));
		
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
			       m="Gap2: "; for (int g : gap2list) m += String.format("%,d  ",g); Globals.prt(m);
			Globals.prt("");
		}
	}
	catch (Exception e) {ErrorReport.print(e, "Set properties for synteny"); bSuccess=false;}
	}
	
	private boolean intervalsOverlap(int s1,int e1, int s2, int e2, int max_gap) {
		int gap = Math.max(s1,s2) - Math.min(e1,e2);
		return (gap <= max_gap);
	}
}

