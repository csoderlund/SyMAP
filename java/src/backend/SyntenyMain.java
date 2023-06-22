package backend;

import java.io.File;
import java.io.FileWriter;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.TreeMap;
import java.util.Vector;
import java.util.Comparator;
import java.util.Collections;
import java.util.TreeSet;
import java.util.HashSet;

import database.DBconn2;
import symap.manager.Mpair;
import symap.manager.Mproject;
import util.Cancelled;
import util.ErrorCount;
import util.ErrorReport;
import util.ProgressDialog;
import util.Utilities;

/**************************************************
 * CAS500 1/2020 change all MySQl Gets to digits.
 * CAS522 removed FPC
 * CAS533 overlapping blocks was not working right; rearranged some
 * CAS534 the parameters from SyProps that never changed are hard-coded here now
 */

public class SyntenyMain {
	private boolean bNewOrder = Constants.NEW_ORDER;
	private boolean bNewBlockCoords = Constants.NEW_BLOCK_COORDS;
	
	private ProgressDialog mLog;
	private DBconn2 dbc2, tdbc2;	
	private SyProj syProj1, syProj2;
	private Mpair mp;
	private int mPairIdx;
	private String resultDir;
	
	private boolean mSelf = false;
	private int mMaxgap1, mMaxgap2, mMingap1, mMingap2;
	private float mAvg1A, mAvg2A;
	private int mMindots,  mMindots_keepbest, mMindotsA, mMindotsB, mMindotsC, mMindotsD; 

	private float mCorr1A, mCorr1B, mCorr1C, mCorr1D; 
	private float mCorr2A, mCorr2B, mCorr2C, mCorr2D; 
	
	private TreeMap<BCase,Integer> mBlksByCase;
	
	private long startTime;
	private boolean bInterrupt = false;

	public SyntenyMain(DBconn2 dbc2, ProgressDialog log, Mpair mp) {
		this.dbc2 = dbc2;
		this.mLog = log;
		this.mp = mp;
	}
	/******************************************************************/
	public boolean run(Mproject pj1, Mproject pj2) {
	try {
		String proj1Name = pj1.getDBName(), proj2Name = pj2.getDBName();
		
		startTime = Utils.getTime();
		mLog.msg("Finding synteny for " + proj1Name + " and " + proj2Name + mp.getChangedSynteny());
		
		mBlksByCase = new TreeMap<BCase,Integer>(); // used by cullChains
		
		for (BCase bt : BCase.values())
			mBlksByCase.put(bt,0);
		
		resultDir = Constants.getNameResultsDir(proj1Name, proj2Name);	
		if (!(new File(resultDir)).exists()) {
			mLog.msg("Cannot find result directory " + resultDir);
			ErrorCount.inc();
			return false;
		}
		int topN = mp.getTopN(Mpair.FILE); 
		
		tdbc2 = new DBconn2("Synteny-" + DBconn2.getNumConn(), dbc2);
		
		syProj1 = new SyProj(tdbc2, mLog, pj1, proj1Name, topN, QueryType.Query);
		syProj2 = new SyProj(tdbc2, mLog, pj2, proj2Name, topN, QueryType.Target);
		if (syProj1.hasOrderAgainst()) mLog.msg("Order against " + syProj1.getOrderAgainst());
		else if (syProj2.hasOrderAgainst()) mLog.msg("Order against " + syProj2.getOrderAgainst());
		
		setBlockTestProps();
		if (Cancelled.isCancelled()) {tdbc2.close(); return false;} // CAS535 there was no checks
			
		mSelf = (syProj1.getIdx() == syProj2.getIdx());

		mPairIdx = Utils.getPairIdx(syProj1.getIdx(), syProj2.getIdx(), tdbc2);
		if (mPairIdx == 0) {
			mLog.msg("Cannot find project pair in database for " + syProj1.getName() + "," + syProj2.getName());
			ErrorCount.inc(); tdbc2.close();
			return false;
		}
				
		//CAS535 deleted in MF: pool.executeUpdate("delete from blocks where pair_idx='" + mPairIdx + "'");
		//pool.resetIdx("idx", "blocks"); //CAS533 add

		setGapProperties();
		clearBlocks();
		
		/*********** Main loop **********************/
		int nGrpGrp = syProj1.getGroups().size() * syProj2.getGroups().size();
		Utils.prtNumMsg(mLog, nGrpGrp, "group-x-group pairs to analyze");
		
		for (Group grp1 : syProj1.getGroups()) {
			for (Group grp2 : syProj2.getGroups()) {
				if (!mSelf) {
					doSeqGrpGrpSynteny(grp1,grp2);
					if (Cancelled.isCancelled() || bInterrupt) return false;
				}
				else if (grp1.getIdx() <= grp2.getIdx()) {
					doSeqGrpGrpSynteny(grp1,grp2);
					if (Cancelled.isCancelled() || bInterrupt) return false;
				}
				nGrpGrp--;
				String t = Utilities.getDurationString(Utils.getTime()-startTime);
				System.err.print(nGrpGrp + " pairs remaining... (" + t + ")   \r"); // CAS541 add time
			}
		}
		System.err.print("                                          \r"); 
		Utils.timeDoneMsg(mLog, "Synteny", startTime); // CAS520 add time
		
		if (Cancelled.isCancelled() || bInterrupt) {tdbc2.close();return false;}
		/****************************************************/
		// CAS520 let self do collinear && p1.idx != p2.idx 
		if (syProj1.hasAnnot() && syProj2.hasAnnot()) { // CAS540 add check; hit# moved to AnchorMain 
			AnchorsPost collinear = new AnchorsPost(mPairIdx, syProj1, syProj2, tdbc2, mLog);
			collinear.collinearSets();
			
			if (Cancelled.isCancelled() || bInterrupt) {tdbc2.close();return false;}
		}
		if (mSelf) symmetrizeBlocks();	
		
		processStats();
		
		if (Cancelled.isCancelled() || bInterrupt) {tdbc2.close();return false;}
		
		/*********************************************************/ 
		if (syProj1.hasOrderAgainst()) {
			String orderBy = syProj1.getOrderAgainst();
			if (orderBy != null && orderBy.equals(syProj2.getName())) {
				OrderAgainst obj = new OrderAgainst(syProj1.mProj, syProj2.mProj, mLog, tdbc2); // CAS505 in new file
				if (bNewOrder) obj.orderGroupsV2(false);	
				else obj.orderGroups(false);	
			}	
		}
		else if (syProj2.hasOrderAgainst()){
			String orderBy = syProj2.getOrderAgainst();
			if (orderBy != null && orderBy.equals(syProj1.getName())) {
				OrderAgainst obj = new OrderAgainst(syProj1.mProj, syProj2.mProj, mLog, tdbc2);
				if (bNewOrder) obj.orderGroupsV2(true);
				else obj.orderGroups(true);
			}	
		}	
		if (Cancelled.isCancelled() || bInterrupt) {tdbc2.close(); return false;}
		
		writeResultsToFile();
		tdbc2.close();
		
		// CAS521 has a finished elsewhere Utils.timeMsg(mLog, startTime, "Synteny");
		return true;
	}
	catch (Exception e) {ErrorReport.print(e, "Computing Synteny"); return false; }
	}
	
	public void interrupt() {bInterrupt = true;}// never called
	
	/************************************************************************
	 * Main synteny method
	 *******************************************************************/
	private void doSeqGrpGrpSynteny(Group grp1, Group grp2) throws Exception {
		Vector<SyHit> hits = new Vector<SyHit>();
		
		boolean isSelf = (grp1.getIdx() == grp2.getIdx());
		
      	String st = "SELECT h.idx, h.start1, h.end1, h.start2, h.end2, h.pctid,h.gene_overlap" +
            " FROM pseudo_hits as h " +  
            " WHERE h.proj1_idx='" + syProj1.getIdx() + "'" + 
            " AND h.proj2_idx='" +  syProj2.getIdx() + "'" +  
            " AND h.grp1_idx='" + grp1.getIdx() + "'" + 
            " AND h.grp2_idx='" + grp2.getIdx() + "'" ;
      	
      	if (isSelf) {
      		st += " AND h.start1 < h.start2 "; // do blocks in one triangle, mirror later
      	}
		ResultSet rs = tdbc2.executeQuery(st);
		while (rs.next()) {
			int id = rs.getInt(1);
			int start1 = rs.getInt(2);
			int end1 = rs.getInt(3);

			int start2 = rs.getInt(4);
			int end2 = rs.getInt(5);
			int pctid = rs.getInt(6);
			int gene = rs.getInt(7);
			
			// Ignore diagonal hits for self-alignments
			// This doesn't really work though because tandem gene families create many near-diagonal hits. 
			if (isSelf ) {
				int pos1 = (start1 + end1)/2;
				int pos2 = (start2 + end2)/2;
				if (pos1 >= pos2) continue; // find only upper triangle blocks, and reflect them later.
				if (Utils.intervalsOverlap(start1, end1, start2, end2, 0)) continue;
			}	
			hits.add(new SyHit(start1, end1, start2, end2,id,pctid,gene));
			
			BinStats.incStat("SyntenyHitsTotal", 1);
			if (gene == 0)		BinStats.incStat("RawSyHits" + HitType.NonGene.toString(), 1);
			else if (gene == 1)	BinStats.incStat("RawSyHits" + HitType.GeneNonGene.toString(), 1);
			if (gene == 2)		BinStats.incStat("RawSyHits" + HitType.GeneGene.toString(), 1);
		}
		rs.close();
		
		Vector<Block> blocks = new Vector<Block>();
		
		chainFinder(hits,blocks,null);
		if (isSelf) blocks = removeDiagonalBlocks(blocks);
		
		blocks = mergeBlocks(blocks);
		setBlockGrps(blocks, grp1.getIdx(), grp2.getIdx());
		
		int num = 1;			
		for (Block b : blocks) {
			b.mNum = num++;
			if (bNewBlockCoords) b.updateCoords(); // CAS533 add
		}
		saveBlocksToDB(blocks);
	}
	
	private Vector<Block> removeDiagonalBlocks(Vector<Block> in){
		Vector<Block> out = new Vector<Block>();
		for (Block b : in){
			if (!Utils.intervalsOverlap(b.mS1, b.mE1, b.mS2, b.mE2, 0)){
				out.add(b);
			}
		}
		return out;
	}
	private void setBlockGrps(Vector<Block> blocks, int gidx1, int gidx2) {
		for (Block b : blocks) {
			b.mGrpIdx1 = gidx1;
			b.mGrpIdx2 = gidx2;
		}
	}
	
	/*************************************************************************
	// This routine organizes the "binary search" of the gap parameter space
	//
	// How it works:
	// 1. Progressively smaller initial gap values are examined, maxgap, maxgap/2, maxgap/4, ...
	// 2. If a good block is found, then the range [gap, 2*gap] is searched with a binary
	//		search to find the largest gap giving a good block. 
	// 3. The highest-scoring chain at the maximum gap param is put into the "best" array
	// 		to provide at least one result for every query
	 *************************************************************************/
	private void chainFinder(Vector<SyHit> hits, Vector<Block> blocks, Block bestblk) {
		Collections.sort(hits,new SyntenyMain.SyHitComp());
		
		for (int i = 0; i < hits.size(); i++)
			hits.get(i).mI = i;

		if (bestblk != null){
			longestChain(mMaxgap1/2,mMaxgap2/2,mMindots_keepbest,hits, bestblk);
			resetDPUsed(hits);
		}

		// Set the decreasing gap search points
		Vector<Integer> gap1list = new Vector<Integer>();
		Vector<Integer> gap2list = new Vector<Integer>();
						
		gap1list.add(mMaxgap1/2);
		gap2list.add(mMaxgap2/2);
		
		int ngaps = 1;
		while (gap1list.get(ngaps-1) >= 2*mMingap1 && gap2list.get(ngaps-1) >= 2*mMingap2){
			gap1list.add(gap1list.get(ngaps-1)/2);
			gap2list.add(gap2list.get(ngaps-1)/2);	
			ngaps++;
		}

		for (int i = 0; i < ngaps; i++){
			int low1 = gap1list.get(i);
			int low2 = gap2list.get(i);
			int high1 = (i > 0 ? gap1list.get(i-1) : low1);
			int high2 = (i > 0 ? gap2list.get(i-1) : low2);

			while (goodChainsExist(low1,low2,hits))
				binarySearch(low1,high1,low2,high2,mMingap1, mMingap2, hits,blocks);				
		}
	}
	
	private void resetDPUsed(Vector<SyHit> hits){
		for (SyHit h : hits)
			h.mDPUsed = h.mDPUsedFinal;
	}
	
	private void binarySearch(int low1,int high1, int low2, int high2, 
			int min1, int min2,	Vector<SyHit> hits, Vector<Block> blocks){

		int gap1 = (high1 + low1)/2;
		int gap2 = (high2 + low2)/2;
		
		while ( high1-low1 > min1 && high2-low2 > min2){
			if (goodChainsExist(gap1,gap2,hits)){
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
		// low1,2 had good blocks
		cullChains(low1,low2,hits,blocks);
	}
	
	private void cullChains(int gap1,int gap2, Vector<SyHit> hits, Vector<Block> blocks){
		Block blk = new Block(mPairIdx,syProj1.mProj,syProj2.mProj);
		while(longestChain(gap1,gap2,mMindots_keepbest,hits,blk)){
			if (goodBlock(blk, hits)){
				blocks.add(blk);
				for (SyHit h : blk.mHits)
					h.mDPUsedFinal = true;
				mBlksByCase.put(blk.mCase,mBlksByCase.get(blk.mCase)+1);
			}
			blk = new Block(mPairIdx, syProj1.mProj, syProj2.mProj); 
		}
		resetDPUsed(hits);
	}
	
	private boolean goodChainsExist(int gap1,int gap2, Vector<SyHit> hits){
		Block blk = new Block(mPairIdx, syProj1.mProj, syProj2.mProj);
		while(longestChain(gap1,gap2,mMindots_keepbest,hits,blk)){
			if (goodBlock(blk, hits)){
				resetDPUsed(hits);
				return true;
			}
			blk = new Block(mPairIdx, syProj1.mProj, syProj2.mProj);
		}
		resetDPUsed(hits);
		return false;
	}
	
	private boolean goodBlock(Block blk, Vector<SyHit> hits){
		int score = blk.mHits.size();
		
		if (score < mMindotsA) return false;
		
		blockCorrelations(blk, hits);
		
		float avg1 = (blk.mE1 - blk.mS1)/score;
		float avg2 = (blk.mE2 - blk.mS2)/score;
		
		if (score >= mMindotsA && avg1 <= mAvg1A && avg2 <= mAvg2A
				&& Math.abs(blk.mCorr1) >= mCorr1A && Math.abs(blk.mCorr2) >= mCorr2A)
		{
			blk.mCase = BCase.A;
		}
		else if (score >= mMindotsB	
				&& Math.abs(blk.mCorr1) >= mCorr1B && Math.abs(blk.mCorr2) >= mCorr2B)
		{
			blk.mCase = BCase.B;
		}
		else if (score >= mMindotsC	
				&& Math.abs(blk.mCorr1) >= mCorr1C && Math.abs(blk.mCorr2) >= mCorr2C)
		{
			blk.mCase = BCase.C;
		}
		else if (score >= mMindotsD	
				&& Math.abs(blk.mCorr1) >= mCorr1D && Math.abs(blk.mCorr2) >= mCorr2D)
		{
			blk.mCase = BCase.D;
		}
		return (blk.mCase != BCase.Reject);
	}
	/***************************************************
	 * from 2006 publication
	 *  r1 is the approximate linearity of the anchors in the chain as measured by the PCC; 
	 *  r2 is the correlation of all the anchors in the chain’s bounding rectangle 
	 */
	private void blockCorrelations(Block blk, Vector<SyHit> hits) {
		blk.mCorr1 = hitCorrelation(blk.mHits); 
		
		Vector<SyHit> hits2 = new Vector<SyHit>();
		for (SyHit h : hits) {
			if (blk.isContained(h.mPos1, h.mPos2)) hits2.add(h);
		}
		blk.mCorr2 = hitCorrelation(hits2);
	}
	// Pearson correlation coefficient (PCC)
	private float hitCorrelation(Vector<SyHit> hits) {
		if (hits.size() <= 2) return 0;
		
		double N = (double)hits.size();
		double xav = 0, xxav = 0, yav = 0, yyav = 0, xyav = 0;
		
		for (SyHit h : hits) {
			double x = (double)h.mPos1;
			double y = (double)h.mPos2;
			xav 	+= x;
			yav 	+= y;
			xxav 	+= x*x;
			yyav 	+= y*y;
			xyav 	+= x*y;
		}	
		xav 	/= N;
		yav 	/= N;
		xxav 	/= N;
		yyav 	/= N;
		xyav	/= N;
		
		double sdx = Math.sqrt(Math.abs(xxav - xav*xav));
		double sdy = Math.sqrt(Math.abs(yyav - yav*yav));
		double sdprod = sdx*sdy;
		
		double corr = 0;
		if (sdprod > 0) corr = ((xyav - xav*yav)/sdprod);
		if (Math.abs(corr) > 1) corr = (corr > 0) ? 1.0 : -1.0;
		
		return (float)corr;
	}
	
	// The core dynamic programming routine
	// Return the longest chain, if any, with at least mindots dots
	// The DPUsed flags are set on the hits returned, so the routine can
	// be run repeatedly, pulling out the successively largest chains.
	// resetDPUsed should be called on the hit list before starting the process over.
	private boolean longestChain(int gap1,int gap2,int mindots, Vector<SyHit> hits, Block blk) {
		if (hits.size() < mindots) return false;
		
		for (SyHit hit : hits) {
			hit.mNDots 		= 1;
			hit.mDPScore 	= 1;
			hit.mPrevI 		= -1;
		}
		SyHit overall_best_hit = hits.get(0);
		for (int i = 0; i < hits.size(); i++) {
			SyHit h1 = hits.get(i);
			if (h1.mDPUsed) continue;
			
			Vector<SyHit> links = new Vector<SyHit>();
			
			for (int j = i-1; j >= 0; j--) {
				SyHit h2 = hits.get(j);
				if (h2.mDPUsed) continue;
				if (h1.mPos2 - h2.mPos2 > mMaxgap2) break; // crucial optimization from sorting on pos2
				if (Math.abs(h1.mPos1 - h2.mPos1) > mMaxgap1) continue;	
				
				links.add(h2);			
			}
			if (links.size() > 0) {
				int maxscore = 0;
				SyHit hbest = null;
				for (SyHit h : links) {
					float d1 = Math.abs(((float)h.mPos1 - (float)h1.mPos1)/(float)gap1);
					float d2 = Math.abs(((float)h.mPos2 - (float)h1.mPos2)/(float)gap2);
					int gap_penalty = (int)(d1 + d2);
					int dpscore = 1 + h.mDPScore - gap_penalty;
					if (dpscore > maxscore) {
						maxscore = dpscore;
						hbest = h;
					}
				}
				if (maxscore > 0){
					h1.mDPScore = maxscore;
					h1.mPrevI = hbest.mI;
					h1.mNDots += hbest.mNDots;
					
					if (h1.mDPScore > overall_best_hit.mDPScore) {
						overall_best_hit = h1;
					}
					else if (h1.mDPScore >= overall_best_hit.mDPScore && h1.mNDots > overall_best_hit.mNDots){
						overall_best_hit = h1;
					}					
				}
				else{
					h1.mDPScore 	= 1;
					h1.mPrevI 	= -1;
					h1.mNDots 	= 1;
				}
			}
		}
		if (overall_best_hit.mNDots < mindots)
			return false;

		SyHit h = overall_best_hit;
		
		int s1 = h.mPos1;
		int e1 = h.mPos1;
		int s2 = h.mPos2;
		int e2 = h.mPos2;
				
		while (true) {
			h.mDPUsed = true;
			
			blk.mHits.add(h);
			
			s1 = Math.min(s1, h.mPos1);
			s2 = Math.min(s2, h.mPos2);
			e1 = Math.max(e1, h.mPos1);
			e2 = Math.max(e2, h.mPos2);
			
			if (h.mPrevI >= 0)
				h = hits.get(h.mPrevI);
			else
				break;
		}
		blk.addHit(s1, e1, s2, e2);
		return true;
	}
		
	private void clearBlocks() {	
		try {
			String stmt = "DELETE FROM blocks WHERE pair_idx=" + mPairIdx;
			tdbc2.executeUpdate(stmt);
		} catch (Exception e) {ErrorReport.print(e, "clear blocks");}
	}
	
	private Vector<Block> mergeBlocks(Vector<Block> blocks){
		int nprev = blocks.size() + 1;

		while (nprev > blocks.size()){
			nprev = blocks.size();
			blocks = mergeBlocksSingleRound(blocks);
		}
		return blocks;
	}
	
	private Vector<Block> mergeBlocksSingleRound(Vector<Block> blocks) {
		if (blocks.size() <= 1) return blocks;
		boolean doMerge = mp.isMerge(Mpair.FILE);
		int maxjoin1 = 0, maxjoin2 = 0;
		float joinfact = 0;
		int cntMerge=0;
		
		if (doMerge) {// CAS534 was set in SyProps		
			maxjoin1 = 1000000000;
			maxjoin2 = 1000000000;
			joinfact = 0.25f;
		}
		
		SyGraph graph = new SyGraph(blocks.size());
		
		for (int i = 0; i < blocks.size(); i++) {
			Block b1 = blocks.get(i);
			
			int w11 = b1.mE1 - b1.mS1;
			int w12 = b1.mE2 - b1.mS2;				
			
			for (int j = i + 1; j < blocks.size(); j++){
				Block b2 = blocks.get(j);

				int w21 = b2.mE1 - b2.mS1;
				int w22 = b2.mE2 - b2.mS2;

				int gap1 = Math.min(maxjoin1,(int)(joinfact*Math.max(w11,w21)));
				int gap2 = Math.min(maxjoin2,(int)(joinfact*Math.max(w12,w22)));
				
				if (doMerge) {
					if (Utils.intervalsOverlap(b1.mS1,b1.mE1,b2.mS1,b2.mE1,gap1) && 
						Utils.intervalsOverlap(b1.mS2,b1.mE2,b2.mS2,b2.mE2,gap2) )
					{
						graph.addNode(i,j);
						cntMerge++;
					}
				}
				else {// only merge if contained	
					if (Utils.intervalContained(b1.mS1,b1.mE1,b2.mS1,b2.mE1) && 
						Utils.intervalContained(b1.mS2,b1.mE2,b2.mS2,b2.mE2) )
					{
						graph.addNode(i,j);
						cntMerge++;
					}					
				}
			}
		}
		BinStats.incStat("Merge Blocks", cntMerge);
		HashSet<TreeSet<Integer>> blockSets = graph.transitiveClosure();
		
		Vector<Block> mergedBlocks = new Vector<Block>();
		
		for (TreeSet<Integer> s : blockSets) {
			Block bnew = null;
			for (Integer i : s) {
				if (bnew == null) 
					bnew = blocks.get(i);
				else
					bnew.mergeWith(blocks.get(i));
			}
			mergedBlocks.add(bnew);
		}
		return mergedBlocks;
	}
	
	/** Add the mirror reflected blocks for self alignments */ 
	private void symmetrizeBlocks() throws Exception {
		Vector<Integer> idxlist = new Vector<Integer>();
		ResultSet rs = tdbc2.executeQuery("select idx from blocks where pair_idx=" + mPairIdx + " and grp1_idx != grp2_idx ");
		while (rs.next()){
			idxlist.add(rs.getInt(1));
		}
		rs.close();
		for (int idx : idxlist) {
			tdbc2.executeUpdate("insert into blocks (select 0,pair_idx,blocknum,proj2_idx,grp2_idx,start2,end2,proj1_idx,grp1_idx,start1,end1," +
					"comment,corr,score,0,0,0,0 from blocks where idx=" + idx + ")");
			
			rs = tdbc2.executeQuery("select max(idx) as maxidx from blocks");
			rs.first();
			int newIdx = rs.getInt("maxidx");
			tdbc2.executeUpdate("insert into pseudo_block_hits (select pseudo_hits.refidx," + newIdx + " from pseudo_block_hits " +
					" join pseudo_hits on pseudo_hits.idx=pseudo_block_hits.hit_idx where block_idx=" + idx + ")");
		}
		assert(syProj1.idx == syProj2.idx);
		Vector<Integer> grps = new Vector<Integer>();
		rs = tdbc2.executeQuery("select idx from xgroups where proj_idx=" + syProj1.idx);
		while (rs.next()){
			grps.add(rs.getInt(1));
		}
		for (int gidx : grps){
			// The self-self blocks
			int maxBlkNum = 0;
			rs = tdbc2.executeQuery("select max(blocknum) as bnum from blocks where grp1_idx=" + gidx + " and grp1_idx=grp2_idx");
			rs.first();
			maxBlkNum = rs.getInt(1);
			int newMaxBlkNum = 2*maxBlkNum + 1;
			
			// For each old block, make a new block with reflected coordinates, and blocknum computed as in the query
			tdbc2.executeUpdate("insert into blocks (select 0,pair_idx," + newMaxBlkNum + "-blocknum,proj2_idx,grp2_idx,start2,end2,proj1_idx,grp1_idx,start1,end1," +
					"comment,corr,score,0,0,0,0 from blocks where grp1_idx=grp2_idx and grp1_idx=" + gidx + " and blocknum <= " + maxBlkNum + ")"); // last clause prob not needed
			
			// Lastly add the reflected block hits. For each original block hit, get its original block, get the new block (known
			// by its blocknum), get the reflected hit (from refidx), and add the reflected hit as a block hit for the new block.
			tdbc2.executeUpdate("insert into pseudo_block_hits (select ph.refidx,b.idx from pseudo_block_hits as pbh " +
					" join blocks as b1 on b1.idx=pbh.block_idx " +
					" join blocks as b on b.grp1_idx=" + gidx + " and b.grp2_idx=" + gidx + " and b.blocknum=" + newMaxBlkNum + "-b1.blocknum " +
					" join pseudo_hits as ph on ph.idx=pbh.hit_idx where b1.blocknum<=" + maxBlkNum +" and ph.grp1_idx=" + gidx + " and ph.grp2_idx=" + gidx + ")");
		}
	}
	/******************************************************************/
	private void writeResultsToFile() throws Exception {
		mLog.msg("Write final results");
		ResultSet rs = null;
		
		String idField1 = "ID", idField2 = "ID";
		
		File resDir = new File(resultDir,Constants.finalDir);
		if (!resDir.exists()) resDir.mkdir();	
		
		File blockFile = new File(resDir,Constants.blockFile);
		if (blockFile.exists()) blockFile.delete();	

		File anchFile = new File(resDir,Constants.anchorFile);
		if (anchFile.exists()) anchFile.delete();	
		
		FileWriter fw = new FileWriter(blockFile);
		fw.write("grp1\tgrp2\tblock\tstart1\tend1\tstart2\tend2\t#hits\t#gene1\t#gene2\t%gene1\t%gene2\n");
		
		rs = tdbc2.executeQuery("select blocknum, grp1.name, grp2.name, " +
			" start1,end1,start2,end2,score,ngene1,ngene2,genef1,genef2 " +
			" from blocks " +
			" join xgroups as grp1 on grp1.idx=blocks.grp1_idx " + 
			" join xgroups as grp2 on grp2.idx=blocks.grp2_idx " +
			" where blocks.pair_idx=" + mPairIdx + 
			" order by grp1.sort_order asc,grp2.sort_order asc,blocknum asc");
		while (rs.next()) {
			int bidx = rs.getInt(1);
			String grp1 = rs.getString(2);	
			String grp2 = rs.getString(3);	
			int s1 = rs.getInt(4);
			int e1 = rs.getInt(5);
			int s2 = rs.getInt(6);
			int e2 = rs.getInt(7);
			int nhits = rs.getInt(8);
			int ngene1 = rs.getInt(9);
			int ngene2 = rs.getInt(10);
			float genef1 = rs.getFloat(11);
			float genef2 = rs.getFloat(12);
			genef1 = ((float)((int)(1000*genef1)))/10;
			genef2 = ((float)((int)(1000*genef2)))/10;
			fw.write(grp1 + "\t" + grp2 + "\t" + bidx + "\t" + s1 + "\t" + e1 + "\t" + s2 + "\t" + e2 + "\t" +
					nhits + "\t" + ngene1 + "\t" + ngene2 + "\t" + genef1 + "\t" + genef2 + "\n");
		}
		fw.flush();
		fw.close();
		mLog.msg("   Wrote " + blockFile);
		
		fw = new FileWriter(anchFile);
		fw.write("block\tstart1\tend1\tstart2\tend2\tannot1\tannot2\n");
		rs = tdbc2.executeQuery("select blocknum, grp1.name, grp2.name, " +
			" ph.start1, ph.end1, ph.start2, ph.end2, a1.name, a2.name " +
			" from blocks " +
			" join xgroups            as grp1 on grp1.idx=blocks.grp1_idx " + 
			" join xgroups            as grp2 on grp2.idx=blocks.grp2_idx " +
			" join pseudo_block_hits as pbh  on pbh.block_idx=blocks.idx " +
			" join pseudo_hits       as ph   on ph.idx=pbh.hit_idx " + 
			" left join pseudo_annot as a1   on a1.idx=ph.annot1_idx " +
			" left join pseudo_annot as a2   on a2.idx=ph.annot2_idx " +
			" where blocks.pair_idx=" + mPairIdx + 
			" order by grp1.sort_order asc,grp2.sort_order asc,blocknum asc, ph.start1 asc");
		while (rs.next()) {
			int bidx = rs.getInt(1);
			String grp1 = rs.getString(2);	
			String grp2 = rs.getString(3);	
			int s1 = rs.getInt(4);
			int e1 = rs.getInt(5);
			int s2 = rs.getInt(6);
			int e2 = rs.getInt(7);
			String a1 = rs.getString(8);	
			String a2 = rs.getString(9);
			
			a1 = parseAnnotID(a1,idField1);
			a2 = parseAnnotID(a2,idField2);
			
			fw.write(grp1 + "." + grp2 + "." + bidx + "\t" + s1 + "\t" + e1 + "\t" + s2 + "\t" + e2 +
							"\t" + a1 + "\t" + a2 + "\n");
		}
		fw.flush();
		fw.close();
		mLog.msg("   Wrote " + anchFile);
	}
	private String parseAnnotID(String in, String fieldName) {
		if (in != null){
			for (String field : in.split(";")){
				if (field.trim().startsWith(fieldName)) {
					field = field.substring(1 + fieldName.length()).trim();
					if (field.startsWith("=")) {
						field = field.substring(2).trim();
					}
					return field;
				}
			}
		}		
		return in;
	}
	/*************************************************************
	* Load data a chr-by-chr at a time
	// CAS533 rewrote to use prepareStatement and make more explicit
	// Gives SQL syntax error the ps.executeBatch() but not ps.addBatch() 
	 *************************************************************/
	private void saveBlocksToDB(Vector<Block> blocks) throws Exception {
		PreparedStatement ps = tdbc2.prepareStatement("insert into blocks "
			+ "(pair_idx, blocknum, proj1_idx, grp1_idx, start1, end1, "
			+ "proj2_idx, grp2_idx, start2, end2, comment, corr, score, "
			+ "ngene1, ngene2, genef1, genef2) "
			+ "values (?,?, ?,?,?,?, ?,?,?,?, ?,?,?, 0,0,0.0,0.0)");
		
		for (Block b : blocks) {
			ps.setInt(1, b.mPairIdx);
			ps.setInt(2, b.mNum);
			
			ps.setInt(3, b.mProj1.getIdx());
			ps.setInt(4, b.mGrpIdx1);
			ps.setInt(5, b.mS1);
			ps.setInt(6, b.mE1);
			
			ps.setInt(7, b.mProj2.getIdx());
			ps.setInt(8, b.mGrpIdx2);
			ps.setInt(9, b.mS2);
			ps.setInt(10, b.mE2);
			
			ps.setString(11, ("Avg %ID:" + b.avgPctID()));
			ps.setFloat(12,  b.mCorr1);
			ps.setInt(13,    b.mHits.size());
			
			ps.executeUpdate();
		}
		ps.close();
		
		ResultSet rs;
		for (Block b : blocks) {
			rs = tdbc2.executeQuery(b.getBlockSql());
			if (rs.next()) b.mIdx = rs.getInt(1);
			else ErrorReport.die("SyMAP error, cannot get block idx \n" + b.getBlockSql());
		}
		
		PreparedStatement ps2 = tdbc2.prepareStatement("insert into pseudo_block_hits"
				+ "(hit_idx, block_idx) values (?,?)");
		for (Block b : blocks) {
			for (SyHit h : b.mHits){
				ps2.setInt(1, h.mIdx);
				ps2.setInt(2, b.mIdx);
				ps2.executeUpdate(); 
			}
		}
		ps2.close();
		
		computeFinalBlockFields();
	}
	private void computeFinalBlockFields() throws Exception {// CAS533 moved from Util
		// count genes1 with start1 and end2 of block
		tdbc2.executeUpdate("update blocks set "
				+ "ngene1 = (select count(*) from pseudo_annot as pa " +
				  "where pa.grp_idx=grp1_idx and greatest(pa.start,start1) < least(pa.end,end1) " +
				"  and pa.type='gene') where pair_idx=" + mPairIdx);
		
		tdbc2.executeUpdate("update blocks set "
				+ "ngene2 = (select count(*) from pseudo_annot as pa " +
				" where pa.grp_idx=grp2_idx  and greatest(pa.start,start2) < least(pa.end,end2) " +
				" and pa.type='gene') where pair_idx=" + mPairIdx);

		// compute gene1 in grp1 that have hit, and divide by ngene1
		tdbc2.executeUpdate("update blocks set "
				+ "genef1=(select count(distinct annot_idx) from  " +
				" pseudo_hits_annot      as pha " +
				" join pseudo_block_hits as pbh on pbh.hit_idx=pha.hit_idx " +
				" join pseudo_annot      as pa  on pa.idx=pha.annot_idx " +
				" where pbh.block_idx=blocks.idx and pa.grp_idx=blocks.grp1_idx)/ngene1 " +
				" where ngene1 > 0 and pair_idx=" + mPairIdx);
		
		tdbc2.executeUpdate("update blocks set "
				+ "genef2=(select count(distinct annot_idx) from  " +
				" pseudo_hits_annot as pha " +
				" join pseudo_block_hits as pbh on pbh.hit_idx=pha.hit_idx " +
				" join pseudo_annot as pa on pa.idx=pha.annot_idx " +
				" where pbh.block_idx=blocks.idx and pa.grp_idx=blocks.grp2_idx)/ngene2 " +
				" where ngene2 > 0 and pair_idx=" + mPairIdx);
	}
	/***************************************************************/
	private void setBlockTestProps() throws Exception { // CAS534 move constants from SyProps
		mMindots = mp.getMinDots(Mpair.FILE);
		mMindots_keepbest = 3;
		mMingap1 = 1000; // minimum gap param considered in binary search
		mMingap2 = 1000;
		mMindotsA = mMindots;
		mMindotsB = mMindots;
		mMindotsC = 3*mMindots/2; 
		mMindotsD = 2*mMindots; 

		mCorr1A = 0.8f; 
		mCorr1B = 0.98f; 
		mCorr1C = 0.96f; 
		mCorr1D = 0.94f; 

		mCorr2A = 0.7f; 
		mCorr2B = 0.9f; 
		mCorr2C = 0.8f; 
		mCorr2D = 0.7f; 

		if (mMindots <= 0 || mMindotsA <= 0 || mMindotsB <= 0 
				|| mMindotsC <= 0 || mMindotsD <= 0 || mMindots_keepbest <= 0)
		{
			throw(new Exception("Min dots parameter(s) out of range (<=0)"));
		}
	}
	private void setGapProperties() throws Exception{
		// CAS500 removed checkgrp1 property checks and assignments to checkgrp1/2
		
		int nHits = 0;
		
		String st = "select count(*) as nhits from pseudo_hits where proj1_idx='" + syProj1.getIdx() + "'" + 
								" and proj2_idx='" + syProj2.getIdx() + "'";
		ResultSet rs = tdbc2.executeQuery(st);
		if (rs.next())
			nHits = rs.getInt("nhits");
		rs.close();
		Utils.prtNumMsg(mLog, nHits, "Total hits");
		
		if (nHits == 0)
			throw(new Exception("No anchors loaded!"));
		
		// CAS534 was getting defaults of 0; then writing back to mProps.setProperty
		mMaxgap1 = (int)( ((float)syProj1.getSizeBP())/(Math.sqrt(nHits)));
		mMaxgap2 = (int)( ((float)syProj2.getSizeBP())/(Math.sqrt(nHits)));
		mAvg1A = ((float)mMaxgap1)/15;
		mAvg2A = ((float)mMaxgap2)/15;
		
		/**
		mProps.setProperty("maxgap1", String.valueOf(mMaxgap1));
		mProps.setProperty("maxgap2", String.valueOf(mMaxgap2));
		mProps.setProperty("avg1_A", String.valueOf(mAvg1A));
		mProps.setProperty("avg2_A", String.valueOf(mAvg2A));
		**/
	}
	private void processStats()  throws Exception {
		for (HitType bt : HitType.values()) {
			String rlbl = "RawSyHits" + bt.toString();
			String blbl = "BlockHits" + bt.toString();
			
			if (BinStats.mStats != null && BinStats.mStats.containsKey(rlbl) && BinStats.mStats.containsKey(blbl)) {
				float rnum = BinStats.mStats.get(rlbl);
				float bnum = BinStats.mStats.get(blbl);
				float pct = (100*bnum)/rnum;
				BinStats.incStat("BlockPercent" + bt.toString(), pct);
			}
		}	
		
		if (Constants.PRT_STATS) {// CAS500
			BinStats.dumpHist(mLog);
			BinStats.dumpStats(mLog);
		}
	}
	
	/**  CLASSES */
	private static class SyHitComp implements Comparator<SyHit>  {
        public int compare(SyHit h1, SyHit h2){
        	if (h1.mPos2 != h2.mPos2)
                return h1.mPos2 - h2.mPos2;
        	else
        		return h1.mPos1 - h2.mPos1;
        }
    }
}

