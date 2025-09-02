package backend.synteny;

import java.io.File;
import java.util.Vector;
import java.util.HashMap;
import java.util.TreeMap;
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
 * CAS572 new Strict; 
 */
public class SyntenyMain {
	public static boolean bTrace = false; // -bt trace; set at command line 
	private boolean bVerbose = Constants.VERBOSE;
	
	protected static final int strictCoset=2;
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
	
	private boolean bOrSt   = false; // Orient || Strict  CAS572
	private boolean bIsSelf = false, bOrderAgainst=false;
	
	private Mproject mProj1, mProj2;
	private Mpair mp;
	private String resultDir;
	
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
	
	// Global DS
	private RWdb rwObj;					// all read/write db methods
	private Vector <SyHit>   hitVec;    // chr-chr hits; populated in RWdb for each new chr-chr; 
	private Vector <SyBlock> blockVec;  // blocks chr-chr pair; recreated on each new chr-chr; 
	private String orient="";			// block is assigned its orient; 
	
	private boolean bSuccess = true;
	private long startTime;
	private int totalBlocks=0, cntMerge=0, cntSame=0, cntDiff=0;
	private int cntGrpMerge=0, cntGrpSame=0, cntGrpDiff=0; 
	private int cntVerOrient=0;					// if Verbose && bStrict or bOrient, count how many would pass if orient was not checked
	private TreeMap <Integer, Integer> cosetNBmap = new TreeMap <Integer, Integer>  ();
	private int cntTrIn=0, cntTrOlap=0, cntTrCsBlk=0, cntTrCsChg=0; 
	
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
		
		bIsSelf = (mProj1.getIdx() == mProj2.getIdx());
	}
	/******************************************************************/
	public boolean run(Mproject pj1, Mproject pj2) {
	try {
	/** Setup ***************************************/
		String proj1Name = mProj1.getDBName(), proj2Name = mProj2.getDBName();
		int mProj1Idx    = mProj1.getIdx(),    mProj2Idx = mProj2.getIdx(); 
		
		startTime = Utils.getTime();
		if (proj1Name.equals(proj2Name)) mLog.msg("Finding self-synteny for " + proj1Name); // CAS572 add
		else mLog.msg("Finding synteny for " + proj1Name + " and " + proj2Name); 
		Globals.tprt("Trace on");
		
		resultDir = Constants.getNameResultsDir(proj1Name, proj2Name);	
		if (!(new File(resultDir)).exists()) {
			mLog.msg("Cannot find result directory " + resultDir);
			ErrorCount.inc();
			return false;
		}
		
		tdbc2 = new DBconn2("Synteny-" + DBconn2.getNumConn(), dbc2);
		tdbc2.executeUpdate("DELETE FROM blocks WHERE pair_idx=" + mPairIdx);
		
		rwObj = new RWdb(mPairIdx, mProj1Idx, mProj2Idx, tdbc2);
		
		setCutoffs(); 
		if (Cancelled.isCancelled() || !bSuccess) {tdbc2.close(); return false;} 
		
	/** Main loop through chromosome pairs ***************************************/
		int nGrpGrp = mProj1.getGrpSize() * mProj2.getGrpSize();
		int maxGrp = nGrpGrp;
		
		if (bVerbose) 	Utils.prtNumMsg(mLog, nGrpGrp, "group-x-group pairs to analyze");
		else 			Globals.rprt("group-x-group pairs to analyze");
		
		for (int grpIdx1 : mProj1.getGrpIdxMap().keySet()) {
			for (int grpIdx2 : mProj2.getGrpIdxMap().keySet()) {
				nGrpGrp--;
				if (bIsSelf && grpIdx1>grpIdx2) continue; // was incorrectly >=; CAS572
				
				SyBlock.cntBlk = 1;
				cntGrpMerge=cntGrpSame=cntGrpDiff=0;
				blockVec = new Vector<SyBlock>(); // create for both orients so can be number sequentially
				
				if (!bOrSt) doChrChrSynteny(grpIdx1,grpIdx2, orientMix); 
				else { 									// restrict to same orientation; 
					doChrChrSynteny(grpIdx1,grpIdx2, orientSame); 
					doChrChrSynteny(grpIdx1,grpIdx2, orientDiff);
				}
				if (Cancelled.isCancelled() || !bSuccess) {tdbc2.close();return false;}
				
				doChrChrFinish(grpIdx1,grpIdx2); 			// save to DB; need to be separate for bOrient; 
				
				cntSame += cntGrpSame; cntDiff += cntGrpDiff; cntMerge += cntGrpMerge;
				if ((bVerbose || bTrace) && blockVec.size()>0) { 		
					String msg = String.format("Blocks %-12s", mProj1.getGrpNameFromIdx(grpIdx1) + "-" + mProj2.getGrpNameFromIdx(grpIdx2));
					if (bOrSt)                         msg += String.format("  Orient:  Same %3d   Diff %3d ",cntGrpSame, cntGrpDiff);
					if (!pMerge.equals("0") || bTrace) msg += String.format("  Merged %3d ", cntGrpMerge);
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
		
		// Final number for output to terminal and log
		String msg = "Blocks";
		if (!pMerge.equals("0")) msg += String.format("   %,d Merged ", cntMerge);
		if (bOrient)             msg += String.format("   Orient: %,d Same   %,d Diff", cntSame, cntDiff);
		Utils.prtNumMsg(mLog, totalBlocks, msg);
		
		if (bVerbose && bOrSt && cntVerOrient>0) 
			Utils.prtNumMsg(mLog, cntVerOrient, "Wrong correlation sign for good block");
		
		if (cosetNBmap.size()>0 && bVerbose) {
			msg = "  Collinear sets not in blocks (size: #sets):  ";
			for (int sz : cosetNBmap.keySet()) msg += sz + ": " + cosetNBmap.get(sz) + "  ";
			Utils.prtIndentMsgFile(mLog, 3, msg);
		}
		
		if (bTrace) {
			msg = String.format("Merge In %,d  Olap %,d   Coset Blk %,d     Add Coset hits to Blk %,d ", cntTrIn, cntTrOlap, cntTrCsBlk, cntTrCsChg);
			Utils.prtIndentMsgFile(mLog, 3, msg);
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
		// CAS572 no longer necessary due to Report; rwObj.writeResultsToFile(mLog, resultDir);
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
		
	/* Read hits */
		hitVec = rwObj.loadHits(grpIdx1, grpIdx2, isGrpSelf, orient);
		if (hitVec==null) {bSuccess=false; return;}
		
		if (bTrace) Globals.prt(String.format("\n>> %s %s  %s  Hits %,d                         ", 
				mProj1.getGrpNameFromIdx(grpIdx1), mProj2.getGrpNameFromIdx(grpIdx2), o, hitVec.size()));
		
		if (hitVec.size() < pMindots) return; // not error
		
		Collections.sort(hitVec); 									   // Sort by G2 hit midpoints
		for (int i = 0; i < hitVec.size(); i++) hitVec.get(i).mI2 = i; // index of the hit in the sorted list
		
	/** Create blocks **/
		createBlockVec();
		cosetBlocks(); 	   // if bStrict, make cosets not in blocks into blocks; otherwise, just count for output
	}
	catch (Exception e) {ErrorReport.print(e, "Synteny main"); bSuccess = false;}
	}
	/*************************************************************
	 * All blocks for grp-grp need to be numbered in order for bOrient
	 */
	private void doChrChrFinish(int grpIdx1, int grpIdx2) {
	try {
		if (grpIdx1 == grpIdx2) rmDiagBlocks(); 
		
		mergeBlocks(); 
		
		if (bStrict) strictBlocks(); // removes small coset that were not merged
		
		for (SyBlock blk : blockVec) {
			blk.setActualCoords(); 		
			blk.stdDev(false); 					    // side 2; saved in DB for report
			blk.stdDev(true);					    // side 1; last sort of hits for block
			blk.mCorr1 = hitCorrelation(blk.mHits); // Not needed if from Binary search, but all others are changed
		}
		
		Collections.sort(blockVec);	    // sort on start1 for final block#
		
		String msg="Old->New: ";
		int blkNum=1;					// final block numbering
		for (SyBlock b : blockVec) { 
			b.mGrpIdx1 = grpIdx1;
			b.mGrpIdx2 = grpIdx2;
			b.mBlkNum = blkNum++;
			
			if ((bOrient || bStrict) && b.orient.equals(orientSame)) cntGrpSame++; else cntGrpDiff++;
			if (bTrace) msg +=  b.nBlk + "->" + b.mBlkNum + "; "; 
		}
		if (bTrace) Globals.prt(msg);
		
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
	// this is called for every block put on blockVec, or changed when on blockVec
	private void finalizeBlock(SyBlock blk, String msg) {
		for (SyHit h : blk.mHits) {
			h.mDPUsedFinal = true;
			h.nBlk  = blk.nBlk;          // use for cosetBlocks; CAS572
		}
		if (bTrace) blk.tprt("Final ");
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
		SyBlock blk = new SyBlock(orient);
		while (longestChain(2, low1, low2, blk)){  
			if (isGoodCorr(blk)){
				blockVec.add(blk);				   // final: add new block to blockVec
				finalizeBlock(blk, "Final");
				
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
				ht1.mNDots 	= 1;
				ht1.mScore = 1;
				ht1.mPrevI 	= -1;
			}
		} // end hit loop
		
		if (savHt1==null || savHt1.mNDots < fMindots_keepbest) return false; // fMindots_keepBest=3
		
	/* Need to build block for isGoodBlock even if it is then discarded */
		SyHit ht = savHt1; 			// build chain from first dot
		
		while (true) {
			ht.mDPUsed = true;
			
			rtBlk.addHit(ht); // add to block hits (rtBlk is parameter); moved calculate block ends to addHit CAS572
			
			if (ht.mPrevI >= 0) ht = hitVec.get(ht.mPrevI);
			else break;
		}
		return true;
	}
	/****************************************************/
	private boolean isGoodCorr(SyBlock blk){ // hasGoodChain, binary search;  evaluate block
		int nDots = blk.n; //blk.mHits.size();
		
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
		blk.mCorr1 = hitCorrelation(blk.mHits); // chain hits 
		
		Vector<SyHit> hits2 = new Vector<SyHit>();
		for (SyHit ht : hitVec) {
			if (blk.isContained(ht.midG1, ht.midG2)) hits2.add(ht);
		}
		blk.mCorr2 = hitCorrelation(hits2); // all hits in surrounding block
	}
	//  Pearson correlation coefficient (PCC) of midpoints
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
	
	/*****************************************************************************
	 * Find cosets that are not in blocks and create them. They may then get merged. CAS572 add method
	 *   Run on chr-chr-orient, hence, hitsVec can be used. 
	 *   All options use this. It uses pCosetdots for cutoff. 
	 *****************************************************************************/
	private void cosetBlocks() {
	try {
	// gather Coset hits
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
	// For each coset
		for (CoSet cset : cosetMap.values()) {
			int nBlk=0, cntNo=0;
			
			// determine if in block or not
			for (SyHit ht : cset.setVec) {
				if (ht.coset!=0) {
					if (ht.nBlk!=0) {
						if (nBlk==0) nBlk = ht.nBlk; 	// assume collinear will always end up in the same block
						else if (nBlk!=ht.nBlk) 
							if (bTrace) Globals.prt("Warning: collinear set " + ht.coset + " in block " + nBlk + " and " + ht.nBlk);
					}
					else cntNo++;
				}
			}
			// Determine if create new or add any 
			if (nBlk==0) { 			 		// new block (coset=2 end up to randomly entered, would need 		
				if (bStrict && cntNo>=strictCoset) { 	// bStrict will later remove <pCosetdots if not merged
					SyBlock cblk = new SyBlock(orient);	// Assigned nBlk on creation
					for (SyHit ht : cset.setVec)  cblk.addHit(ht);
				
					cblk.mCase = "N";
					finalizeBlock(cblk, "CSnew");
					blockVec.add(cblk);
				}
				else {
					int sz = cset.setVec.size();
					if (cosetNBmap.containsKey(sz)) cosetNBmap.put(sz, cosetNBmap.get(sz)+1);
					else cosetNBmap.put(sz, 1);
				}
			}
			else if (cntNo>0) {			// add to existing block - this will even happen for original
				for (SyBlock cblk : blockVec) {
					if (cblk.nBlk != nBlk) continue;
					
					for (SyHit ht : cset.setVec) {
						if (ht.nBlk==0) {
							cblk.addHit(ht);
							ht.nBlk = nBlk;
							cntTrCsChg++;
						}
					}
					finalizeBlock(cblk, "CSchg");
					break;
				}
			}
		}
	}
	catch (Exception e) {ErrorReport.print(e, "trim block"); }
	}
	/**************************************************
	 * Remove blocks from chr-chr diagonal
	 * Run on final chr-chr before merge or strict
	 */
	private void rmDiagBlocks(){ // change to set blockVec like other methods; CAS572
		Vector<SyBlock> out = new Vector<SyBlock>();
		for (SyBlock b : blockVec){
			if (!intervalsOverlap(b.mS1, b.mE1, b.mS2, b.mE2, 0)){
				out.add(b);
			}
		}
		blockVec = out;
	}
	/*****************************************************
	 * Merge final blocks 
	 * Run on chr-chr pair (both orients) before strict (HitsVec cannot be used because maybe only one orient)
	 */
	private void mergeBlocks() {
		int nb = blockVec.size();
		
		// Always do for contained
		Merge mergeObj = new Merge(blockVec, false, bOrient); // F=contained
		blockVec = mergeObj.mergeBlocks(0, 0, 0); 			  // return merged contained blocks
		
		for (SyBlock blk : blockVec) {						
			if (blk.hasChg) {
				finalizeBlock(blk, "MgIn ");
				blk.hasChg = false;
				cntTrIn++;
			}
		}
		
		// Only do closely space merge if parameter set
		if (!pMerge.equals("0")) {
			mergeObj = new Merge(blockVec, true, bOrient); // T=overlap or close; send blocks; perform overlap
		
			if (pMerge.equals("1"))
				blockVec = mergeObj.mergeBlocks(0, 0, pMindots);  // combine small overlapping - which will be mixed
			else
				blockVec = mergeObj.mergeBlocks(gap1list.get(0), gap2list.get(0), pMindots); // return merged close blocks
						  
			for (SyBlock blk : blockVec) {						
				if (blk.hasChg) {
					finalizeBlock(blk, "MgOlap");
					blk.hasChg = false;
					cntTrOlap++;
				}
			}
		}	
		cntGrpMerge += nb-blockVec.size();
		
		if (bTrace) for (SyBlock blk : blockVec) if (blk.mCase=="N") cntTrCsBlk++;
	}
	
	/** Run on final chr-chr pair (both orients) after merge 
	 *  Was going to eval for poor small blocks, though they are looking pretty good as is...
	 ***/
	private void strictBlocks() {
	try {
		int cnt=0;
		for (SyBlock blk : blockVec) {
			if (blk.n<pMindots) {
				cnt++;
				blk.mCase = "X";
				// add to existing - created in cosetBlocks
				if (cosetNBmap.containsKey(blk.n)) cosetNBmap.put(blk.n, cosetNBmap.get(blk.n)+1);
				else cosetNBmap.put(blk.n, 1);
			}
		}
		if (cnt>0) { // rebuild blockVec
			Vector <SyBlock> xVec = new Vector <SyBlock> ();
			for (SyBlock blk : blockVec) 
				if (!blk.mCase.equals("X")) xVec.add(blk);
			
			blockVec = xVec;
			if (bTrace) Globals.prt("Final strict blocks " + cnt);
		}
	}
	catch (Exception e) {ErrorReport.print(e, "trim block"); }
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
		if (bStrict) {				// CAS572
			gap1list.remove(0);
			gap2list.remove(0);
			ngaps--;
			
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
	/**************************************************/
	private class CoSet {
		private Vector <SyHit> setVec = new Vector <SyHit> ();
		
		private void add(SyHit ht) {setVec.add(ht);}

		private CoSet(int num) {}
	}
}

