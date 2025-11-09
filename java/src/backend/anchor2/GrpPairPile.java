package backend.anchor2;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.TreeMap;

import backend.Utils;
import symap.Globals;
import util.ErrorReport;
import util.ProgressDialog;

/*************************************************************
 * Find overlapping HitPairs; mark Pile
 */
public class GrpPairPile {
	static private final int T=Arg.T, Q=Arg.Q;
	static private final double FperBin=backend.Constants.FperBin; // 0.8*matchLen top piles to save
	
	// from GrpPair
	private GrpPair grpPairObj;
	private ProgressDialog plog;
	private ArrayList <HitPair> clustList = null; 
	private int clArrLen;
	
	// Working - discarded pile hits are Pile
	private ArrayList <Pile> pileList = new ArrayList <Pile> (); 
	
	private boolean bSuccess=true;
	
	protected GrpPairPile (GrpPair grpPairObj) {
		this.grpPairObj = grpPairObj;
		plog = grpPairObj.plog;
		
		clustList = grpPairObj.gxPairList;
	
		clArrLen = clustList.size();
	}
	protected boolean run() {
		Globals.tprt("Find piles from " + clArrLen);
		
		identifyPiles();	if (!bSuccess) return false;
		createPiles();		if (!bSuccess) return false;
		filterPiles();		if (!bSuccess) return false;
		return bSuccess;
	}
	
	////////////////////////////////////////////////////////////////////////////
	private void identifyPiles() {
	try {
		for (int X=0; X<2; X++) { 			// T piles, then Q piles (correct for saveDB)
			HitPair.sortByXstart(X, clustList); 	// sorts by T/Q start; eq/ne mixed
			
			int cBin=1;
			
			for (int r1=0; r1<clArrLen-1; r1++) {
				HitPair hpr1 = clustList.get(r1);
				if (hpr1.flag!=Arg.MAJOR || hpr1.bin==0) continue;	   
				
				for (int r2=r1+1; r2<clArrLen; r2++) {
					HitPair hpr2 = clustList.get(r2);
					if (hpr1.hpEnd[X]<hpr2.hpStart[X]) break; 
					
					if (hpr2.pile[X]!=0 ||hpr2.flag!=Arg.MAJOR || hpr2.bin==0) continue;	
					
					if (bBigOlap(hpr1.hpStart[X], hpr1.hpEnd[X], hpr2.hpStart[X], hpr2.hpEnd[X])) {
						if (hpr1.pile[X]==0) {
							hpr1.pile[X] = cBin++;  // first of pile
						}
						hpr2.pile[X] = hpr1.pile[X];
					}
				}
			}
		}
	}
	catch (Exception e) {ErrorReport.print(e, "Creating piles"); bSuccess=false;}		
	}
	
	private boolean bBigOlap(int start0, int end0, int start1, int end1) {
		int olap = Arg.pGap_nOlap(start0, end0, start1, end1);	
		if (olap>0) return false;	// Gap
		
		olap = -olap;
		int len0 = end0-start0+1;
		int len1 = end1-start1+1;
		
		double x1 = (double) olap/(double)len0;
		if (x1>Arg.bigOlap) return true;
		
		double x2 = (double) olap/(double)len1;
		if (x2>Arg.bigOlap) return true;
		
		return false;
	}
	
	/////////////////////////////////////////////////////////////////////////////////
	private void createPiles() {
	try {
		// create cluster pile sets; pile# is Key
		TreeMap <Integer, Pile> tPileMap = new TreeMap <Integer, Pile> (); // key: pile[T]
		TreeMap <Integer, Pile> qPileMap = new TreeMap <Integer, Pile> (); // key: pile[Q]
		
		Pile pHt;
		for (HitPair hpr : clustList) {
			if (hpr.pile[T]>0) {
				if (!tPileMap.containsKey(hpr.pile[T])) {
					pHt = new Pile(T, hpr.pile[T]);
					tPileMap.put(hpr.pile[T], pHt);
				}
				else pHt = tPileMap.get(hpr.pile[T]);
				pHt.addHit(hpr);
			}
			if (hpr.pile[Q]>0) {
				if (!qPileMap.containsKey(hpr.pile[Q])) {
					pHt = new Pile(Q, hpr.pile[Q]);
					qPileMap.put(hpr.pile[Q], pHt);
				}
				else pHt = qPileMap.get(hpr.pile[Q]);
				pHt.addHit(hpr);
			}
		}
		
		// finalize
		for (Pile pile : tPileMap.values()) pileList.add(pile);
	
		for (Pile pile : qPileMap.values()) pileList.add(pile);
	
		Globals.tprt(tPileMap.size(), "Piles for T");
		Globals.tprt(qPileMap.size(), "Piles for Q");
		tPileMap.clear(); qPileMap.clear();
	}
	catch (Exception e) {ErrorReport.print(e, "Creating piles"); bSuccess=false;}		
	}
		
	/**********************************************
	 * Keep all Arg.isGoodPileHit and the 1st topN !Arg.isGoodPileHit && hpr.xMaxCov>thresh
	 * Heuristic using anchor1 Top N and 0.8
	 */
	private void filterPiles() {
	try {
		int cntInPile=0, cntFil=0;
		
		sortPiles(pileList); // by maxCov
		
		for (Pile pile : pileList) { 
			cntInPile += pile.hprList.size();
		
			sortForPileTopN(pile.hprList);	// sort by xMaxCov
						
			int thresh = pile.hprList.get(0).xMaxCov;
			thresh *= FperBin; 				// 0.8 so keep 80% of mTopN matchLen
			
			int tn=0;
			for (HitPair hpr : pile.hprList) {
				if (hpr.flag==Arg.PILE || Arg.isGoodPileHit(hpr)) continue;
				
				if (hpr.xMaxCov<thresh || tn>=Arg.topN) {
					hpr.flag = Arg.PILE;
					cntFil++;
				}
				tn++;						// accept TopN that are !Arg.isGoodPileHit(hpr)
			}
		}	
		grpPairObj.mainObj.cntPileFil += cntFil;
		if (Arg.TRACE) {
			Utils.prtIndentMsgFile(plog, 1, String.format("%,10d Piles    Hits in piles %,d   Filtered pile hits %d", 
				pileList.size(), cntInPile, cntFil));
		}
	}
	catch (Exception e) {ErrorReport.print(e, "Prune clusters"); bSuccess=false;}	
	}
	////////////////////////////////////////////////////////////////
	protected void prtToFile(PrintWriter fhOutRep, String chrs) {
	try {
		fhOutRep.println("Repetitive clusters for " + chrs);
		
		for (Pile ph : pileList) { 
			fhOutRep.println("\n" + ph.toResults());
			
			for (HitPair cht : ph.hprList) 
				fhOutRep.println(" " + cht.toPileResults());
		}
	}
	catch (Exception e) {ErrorReport.print(e, "Write reps to file"); bSuccess=false;}	
	}
	protected static void sortForPileTopN(ArrayList <HitPair> gpList) { // GrpPairPile heuristic topN
		Collections.sort(gpList, 
			new Comparator<HitPair>() {
				public int compare(HitPair h1, HitPair h2) {
					if (h1.xMaxCov>h2.xMaxCov) return -1; 	
					if (h1.xMaxCov<h2.xMaxCov) return  1; 
					
					if ( h1.bBothGene && !h2.bBothGene) return -1; 
					if (!h1.bBothGene &&  h2.bBothGene) return  1;
					
					if (h1.bOneGene && h2.bNoGene) return -1; 
					if (h1.bNoGene  && h2.bOneGene) return  1;
					
					if (h1.nHits>h2.nHits) return -1; 
					if (h1.nHits<h2.nHits) return 1;
					
					return 0;	
				}
			}
		);
	}
	protected static void sortPiles(ArrayList <Pile> gpList) { // GrpPairPile heuristic topN
		Collections.sort(gpList, 
			new Comparator<Pile>() {
				public int compare(Pile h1, Pile h2) {
					if (h1.xMaxCov>h2.xMaxCov) return -1;
					if (h1.xMaxCov<h2.xMaxCov) return  1;
					if (h1.hprList.size()>h2.hprList.size()) return -1;
					if (h1.hprList.size()<h2.hprList.size()) return 1;
					return 0;
				}
		});
	}
	/////////////////////////////////
	private class Pile {
		private int X, pnum;
		
		private int tmin=Integer.MAX_VALUE, tmax=0, qmin=Integer.MAX_VALUE, qmax=0; // for trace
		private int xMaxCov=0;
		private ArrayList <HitPair> hprList = new ArrayList <HitPair> ();
		
		private Pile(int X, int pnum) {this.X = X; this.pnum=pnum;}
		
		private void addHit(HitPair ht) {
			hprList.add(ht);
			tmin = Math.min(ht.hpStart[T], tmin);
			qmin = Math.min(ht.hpStart[Q], qmin);
			tmax = Math.max(ht.hpEnd[T], tmax);
			qmax = Math.max(ht.hpEnd[Q], qmax);
			xMaxCov = Math.max(ht.xMaxCov, xMaxCov);
		}
	
		private String toResults() {
			String msg = String.format("%s%-5d #%3d T[%,10d %,10d %,6d] Q[%,10d %,10d %,d]  >>>>>>>>>>>>>>>>>>>>>>>>",
					Arg.side[X], pnum, hprList.size(),  tmin, tmax, (tmax-tmin), qmin, qmax, (qmax-qmin));
			return msg;
		}
	}
}
