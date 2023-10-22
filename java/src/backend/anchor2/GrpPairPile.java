package backend.anchor2;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.TreeMap;

import backend.Utils;
import util.ErrorReport;
import util.ProgressDialog;

/*************************************************************
 * After creating gene clusters, this processes non-gene hits to remove pile of hits
 */
public class GrpPairPile {
	static private final int T=Arg.T, Q=Arg.Q;
	static private final double bigOlap = 0.4; 
	static private final double fudgeScore = 70.0;
	static private final int    fudgeLen = 5000; 
	
	private ArrayList <HitCluster> clHitList = null; 
	private int clArrLen;
	
	private GrpPair grpPairObj;
	private ProgressDialog plog;
	
	// Working - discarded pile hits are marked bDead
	private ArrayList <PileClHits> pileList = new ArrayList <PileClHits> (); 
	
	private HashSet <Integer> htNumSet = new HashSet <Integer> ();	// key: hitNum
	private boolean bSuccess=true;
	
	protected GrpPairPile (GrpPair grpPairObj) {
		this.grpPairObj = grpPairObj;
		plog = grpPairObj.plog;
		
		clHitList = grpPairObj.gpClHitList;
	
		clArrLen = clHitList.size();
	}
	protected boolean run() {
		identifyPiles();	if (!bSuccess) return false;
		createPiles();		if (!bSuccess) return false;
		filterPiles();		if (!bSuccess) return false;
			
		prtToFile();
		return bSuccess;
	}
	
	///////////////////////////////////////////////////////////////
	private void identifyPiles() {
	try {
		// set hitcluster pileNum 
		int [] cPile = {0,0};
		
		for (int X=1; X>=0; X--) { 				// Q piles, then T piles
			HitCluster.sortByX(X, clHitList); 	// sorts by T/Q; eq/ne mixed
			
			for (int r=0; r<clArrLen-1; r++) {
				HitCluster ht0 = clHitList.get(r);
				if (ht0.bBaseFilter) continue;	   
				
				for (int r1=r+1; r1<clArrLen; r1++) {
					HitCluster ht1 = clHitList.get(r1);
					if (ht1.bBaseFilter) continue;	
					
					if (ht0.cend[X]<ht1.cstart[X]) break;
					
					if (bBigOlap(ht0.cstart[X], ht0.cend[X], ht1.cstart[X], ht1.cend[X])) {
						if (ht0.pile[X]==0) ht0.pile[X] = ++cPile[X];  // first of group
						ht1.pile[X] = ht0.pile[X];
					}
				}
			}
		}
	}
	catch (Exception e) {ErrorReport.print(e, "Creating piles"); bSuccess=false;}		
	}
	
	private boolean bBigOlap(int start0, int end0, int start1, int end1) {
		if (start1>=start0 && end1<=end0) return true;
		
		int olap = Math.min(end1,end0) - Math.max(start1, start0); 
		int len1 = end1-start1;
		int len0 = end0-start0;
		
		double x1 = (double) olap/(double)len1;
		if (x1>bigOlap) return true;
		
		double x2 = (double) olap/(double)len0;
		if (x2>bigOlap) return true;
		
		return false;
	}
	/////////////////////////////////////////////////////////////////////////////////
	private void createPiles() {
	try {
		// create cluster pile sets; pile# is Key
		TreeMap <Integer, PileClHits> tPileMap = new TreeMap <Integer, PileClHits> (); // key: pile[T]
		TreeMap <Integer, PileClHits> qPileMap = new TreeMap <Integer, PileClHits> (); // key: pile[Q]
		
		PileClHits pHt;
		for (HitCluster ht : clHitList) {
			if (ht.pile[T]>0) {
				if (!tPileMap.containsKey(ht.pile[T])) {
					pHt = new PileClHits(T, ht.pile[T]);
					tPileMap.put(ht.pile[T], pHt);
				}
				else pHt = tPileMap.get(ht.pile[T]);
				pHt.addHit(ht);
			}
			if (ht.pile[Q]>0) {
				if (!qPileMap.containsKey(ht.pile[Q])) {
					pHt = new PileClHits(Q, ht.pile[Q]);
					qPileMap.put(ht.pile[Q], pHt);
				}
				else pHt = qPileMap.get(ht.pile[Q]);
				pHt.addHit(ht);
			}
			if (ht.pile[T]>0 && ht.pile[Q]>0) htNumSet.add(ht.cHitNum);
		}
		
		// finalize
		for (PileClHits ph : tPileMap.values()) {
			pileList.add(ph);
			ph.setGene();  // for sortByGene
		}
		for (PileClHits ph : qPileMap.values()) {
			pileList.add(ph);
			ph.setGene();
		}
		sortByGene(pileList);
		
		tPileMap.clear(); qPileMap.clear();
	}
	catch (Exception e) {ErrorReport.print(e, "Creating piles"); bSuccess=false;}		
	}
		
	///////////////////////////////////////////////////////////////////////
	private void filterPiles() {
	try {
		int cntNoShow=0, cntInPile=0, cntFil1=0, cntFil2=0;
		for (PileClHits ph : pileList) { 
			
			cntInPile += ph.pHitList.size();
		
			// Check gene parameters
			boolean isOnly=false;
			for (HitCluster cht : ph.pHitList) {
				if (Arg.isGoodHit(cht)) {
					isOnly=true;
					break;
				}
			}
			if (isOnly) {
				for (HitCluster cht : ph.pHitList) {
	
					if (!Arg.isGoodHit(cht)) {
						cht.bPile = true; 
						cntNoShow++; cntFil1++;
					}
					else cht.bPile = false;
				}
				continue;
			}
			// Serious heuristics to find the best of the pile and only set from bPile=T to F is good
			HitCluster bestL=null, bestG=null;
			for (HitCluster cht : ph.pHitList) {
				if (bestL==null) 				bestL = cht;
				else if (cht.bLen > bestL.bLen) bestL = cht; 
				
				if (cht.exonScore[T]>fudgeScore || cht.exonScore[Q]>fudgeScore) { 
					if (bestG==null) 				bestG = cht;
					else if (cht.bLen > bestG.bLen) bestG = cht; 
				}
			}
			
			if (bestL!=null || bestG!=null) {
				if (bestL!=null && bestL.bPile) {
					if (bestL.pId>fudgeScore && Arg.baseScore(bestL)>fudgeLen) bestL.bPile = false; // if set T, only make F if good
				}
				if (bestG!=null && bestG.bPile) bestG.bPile = false;	// already checked for fudgeScore
				
				for (HitCluster cht : ph.pHitList) {
					if (cht!=bestL && cht!=bestG) {
						cht.bPile = true; 
						cntNoShow++; cntFil2++;
					}
				}
			}	
		}	
		grpPairObj.anchorObj.cntPileFilter += cntNoShow;
		if (Arg.PRT_STATS) {
			Utils.prtIndentMsgFile(plog, 1, String.format("%,10d Piles    Hits in piles %,d", 
				pileList.size(), cntInPile));
			Utils.prtIndentMsgFile(plog, 1, String.format("%,10d Filtered pile hits   Gene %,d  Length %,d", 
				cntNoShow, cntFil1, cntFil2));
		}
	}
	catch (Exception e) {ErrorReport.print(e, "Prune clusters"); bSuccess=false;}	
	}
	////////////////////////////////////////////////////////////////
	private void prtToFile() {
	try {
		PrintWriter fhOutRep = grpPairObj.anchorObj.fhOutRep;
		if (fhOutRep==null) return ;
		
		fhOutRep.println(grpPairObj.anchorObj.fileName);
		fhOutRep.println("Repetitive clusters for " + grpPairObj.qChr + " (Q) and " + grpPairObj.tChr + " (T)");
		fhOutRep.println(grpPairObj.mPair.getChangedSynteny());
		
		String chrs = grpPairObj.tChr+":"+grpPairObj.qChr;
		for (PileClHits ph : pileList) { 
			fhOutRep.println(ph.toResults());
			
			int X = (ph.X==T) ? Q : T;
			HitCluster.sortByPile(X, ph.pHitList);
			
			for (HitCluster cht : ph.pHitList) 
				fhOutRep.println(" " + cht.toResults(chrs));
		}
	}
	catch (Exception e) {ErrorReport.print(e, "Write reps to file"); bSuccess=false;}	
	}
	////////////////////////////////////////////////////////////////
	protected static void sortByGene(ArrayList<PileClHits> hits) { 
		Collections.sort(hits, 
			new Comparator<PileClHits>() {
				public int compare(PileClHits h1, PileClHits h2) {
					if (h1.cntGene==0 && h2.cntGene==0) 
						return  h2.pHitList.size()-h1.pHitList.size(); 
					
					if (h1.cntEE>h2.cntEE) return -1;
					if (h1.cntEE<h2.cntEE) return 1;
					
					if (h1.cntGene>h2.cntGene) return -1;
					if (h1.cntGene<h2.cntGene) return 1;
					
					return  h2.pHitList.size()-h1.pHitList.size();
				}
			}
		);
	}
	
	/////////////////////////////////
	private class PileClHits {
		private int X, pnum;
		private int cntGene=0, cntEE=0;
		private int tmin=Integer.MAX_VALUE, tmax=0;
		private int qmin=Integer.MAX_VALUE, qmax=0;
		private ArrayList <HitCluster> pHitList = new ArrayList <HitCluster> ();
		
		private PileClHits(int X, int pnum) {this.X = X; this.pnum=pnum;}
		
		private void addHit(HitCluster ht) {
			pHitList.add(ht);
			tmin = Math.min(ht.cstart[T], tmin);
			qmin = Math.min(ht.cstart[Q], qmin);
			tmax = Math.max(ht.cend[T], tmax);
			qmax = Math.max(ht.cend[Q], qmax);
			
		}
		private void setGene() { // cntPass and cntEE are used in sorting Piles, to process them first
			HitCluster.sortByGene(pHitList);
			
			for (HitCluster cht : pHitList) { 
				if (cht.bnn) continue;
				
				if (!Arg.isGoodHit(cht)) continue;
				
				cntGene++;
					
				if (cht.bEE) cntEE++;	
			}
		}
		
		private String toResults() {
			return String.format("%s%-5d >>>>>>>>>> h%d T[%,d] Q[%,d] Count: G[%,d]E[%,d] >>>>>>>>>>>>>>>>>>>>>>>>",
					Arg.side[X], pnum, pHitList.size(), (tmax-tmin), (qmax-qmin), cntGene, cntEE);
		}
	}
}
