package backend.anchor2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.HashMap;

import backend.Utils;
import symap.Globals;
import util.ErrorReport;
import util.ProgressDialog;

/******************************************************
 * Computes bins for g2, g1, g0 for this GrpPair
 * CAS560 all filters are in Arg. 
 * It was the case that any subhit could only be part of one cluster, and it was assigned to minor genes if more than one.
 * Now it is the case that a subhit can be assigned to multiple clusters overlapping multiple genes,
 * but can only be assigned once to a single gene. This gets rid of the need for the 'split'.
 * 
 * gxPairList holds the final list
 *   Each hpr has a bin and flag=MAJOR,MINOR or FILTER(trace only). 
 *   An hpr can have the same bin number as another with flag=MINOR
 *   All major have annot_idx1 and annot_idx2 set to the major gene indices (if G2, G1)
 *   All major and minor go into pseudo_hits_annot, where the hit_idx can be duplicated but the (hit_idx,annot_idx) is unique
 *   Final cluster number assigned in GrpPair after GrpPairPile
 */
public class GrpPairGx {
	private static boolean TRC = Arg.TRACE;
	private static final int T=Arg.T, Q=Arg.Q, TQ=Arg.TQ;
	private final double pCutoff5 = 0.5, pCutoff8 = 0.8; // to call a major for goodEnough or minor
	private int  intronLen4xG2rm, intronLen2xG1, intronLenRm, intronLenG0; 
	
	private int nBin=1; 

	// from GrpPair; 
	private GrpPair grpPairObj=null;
	private TreeMap <Integer, Gene> tGeneMap = null, qGeneMap = null;   // key: geneIdx
	private ArrayList <Hit> grpHitList = null; 							// all hits for chrPair
	
	private ProgressDialog plog;
	private String chrPair;

	// GrpPairs: create clusters from major, saveAnnoHits G2/G1 major and minor
	private ArrayList <HitPair> gxFinalList = new ArrayList <HitPair> ();	// all pairs for chrPair
	
	private boolean bSuccess=true;  // used by all
	
	public GrpPairGx(GrpPair grpPairObj) { // GrpPair.buildClusters; uses gxPairList to finish and save 
		this.grpPairObj = grpPairObj;
		plog = grpPairObj.plog;
		
		tGeneMap   = grpPairObj.tGeneMap;
		qGeneMap   = grpPairObj.qGeneMap;
		grpHitList = grpPairObj.grpHitList;
		
		chrPair = grpPairObj.tChr + "," + grpPairObj.qChr;
		
		intronLen2xG1 = Arg.iGmIntronLen2x;	
		intronLen4xG2rm = Arg.iGmIntronLen4x;	
		intronLenRm = Arg.iGmIntronLenRm;
		intronLenG0 = (intronLen4xG2rm>8000) ? 8000 : intronLen4xG2rm;
	}
	/*****************************************************
	 * All results are put in gxPairList
	 */
	protected boolean run() {
	try {
		new G2Pairs();	if (!bSuccess) return false;// G2: Genes are already assigned to genes
		
		new G1Pairs(); 	if (!bSuccess) return false;// G1: Make T or Q pseudo-genes
														
		new G0Pairs();	if (!bSuccess) return false;// G0: Make T and Q pseudo-genes
		
	/* Finish */

		if (TRC) {
			int cntEE=0, cntEI=0, cntIE=0, cntII=0, cntEn=0, cntnE=0, cntIn=0, cntnI=0, cntnn=0;
			for (HitPair hpr : gxFinalList) {
				if (hpr.flag!=Arg.MAJOR && hpr.flag!=Arg.MINOR) continue;
				String t = hpr.htype;
				if      (t.equals(Arg.sEE)) cntEE++; else if (t.equals(Arg.sIE)) cntIE++; else if (t.equals(Arg.sEI)) cntEI++;
				else if (t.equals(Arg.sII)) cntII++; else if (t.equals(Arg.sEn)) cntEn++; else if (t.equals(Arg.snE)) cntnE++;
				else if (t.equals(Arg.sIn)) cntIn++; else if (t.equals(Arg.snI)) cntnI++; else if (t.equals(Arg.snn)) cntnn++;
			}
			Globals.tprt(String.format("   EE %,d   IE %,d   EI %,d   II %,d   En %,d   nE %,d   In %,d   nI %,d   nn %,d",
					cntEE, cntIE, cntEI, cntII, cntEn, cntnE, cntIn, cntnI, cntnn));
			Globals.tprt(String.format("Finish pairs: %,d gxPairList Initial %s", gxFinalList.size(), chrPair));
		}
		return true;
	}
	catch (Exception e) {ErrorReport.print(e, "GrpPairGx run"); return false; }
	}
	
	/** GrpPair.buildClusters **/
	protected ArrayList <HitPair> getPairList() {return gxFinalList;} 
	
	protected void clear() {// cleared in GrpPair: gpHitList (and hits), tGeneMap, qGeneMap (and genes) 
		for (HitPair hp : gxFinalList) hp.clear();
		gxFinalList.clear();
	}
	
	/*****************************************************************
	 * G2: create GenePairList; a hit can go into multiple gene pairs, e.g. hit aligns to 1.a & 1.b -> 2.
	 * use gxPairList directly since it is first to use it
	 * Results: all Major/Minor Hprs will be assigned a bin along with their hits
	 ******************************************************************/
	private class G2Pairs {
		private ArrayList <HitPair> g2HprList  = new ArrayList <HitPair> (); // Final list, to be  added to gxPairList
		private ArrayList <HitPair> wsPairList = new ArrayList <HitPair> (); // wrong strand - for -wsp
		private int numPairsOrig=0, numFilter=0;
		
		private G2Pairs() {
			if (tGeneMap.size()==0 || qGeneMap.size()==0) return;
			
			if (TRC) Utils.prtIndentMsgFile(plog, 0, ">>G2: Remove hit use len: " + intronLen4xG2rm);
			
			runG2(true);	if (!bSuccess) return;	// A gene can have a same strand and a diff strand RBH, though rare
			runG2(false);	if (!bSuccess) return;
			
			processWS(); 
			grpPairObj.mainObj.cntG2Fil+=numFilter;
		}
		private void runG2(boolean isStEQ) {
			if (tGeneMap.size()==0 || qGeneMap.size()==0) return;
			
			Globals.rprt("Find G2 hits for " + Arg.isEQstr(isStEQ));
			
			step1MkPairAndFilter(isStEQ); if (!bSuccess) return; // create g2HprList and filter
			
			step2SetBinAllG2();  if (!bSuccess) return; // set Major from RBH and GoodEnough, and set minor
			
			// Finalize and cleanup; sorted by start in last of step2
			for (HitPair hpr : g2HprList) gxFinalList.add(hpr);  // will have flag=IGNs (or FILTER if TRACE), but will not be saved in DB
			
			prtTrace(isStEQ);
			
			for (Hit ht : grpHitList) ht.bin2=0; 
			if (!TRC) 
				for (HitPair hpr : g2HprList) hpr.crossRefClear(); 
			g2HprList.clear();	
		}
		/*******************************************
		 * create by finding gene-pairs of hits and filter: (1) EQ (2) NE
		 *******************************************/
		private void step1MkPairAndFilter(boolean isStEQ) {
		try {
			HashMap <String, HitPair> hprGeneMap = new HashMap <String, HitPair> (); // tgn.geneIdx:qgn.geneIdx -> hits
			
		// Build g2 multi and single hit pairs from genes with shared hits
			for (Hit ht: grpHitList) { 			// Loop by hit assigning hpr to T-Q gene pairs		
				if (ht.isStEQ!=isStEQ) continue;
				
				ht.mkGenePairs(); 					// makes pairs; tgn.geneIdx + ":" + qgn.geneIdx   
				if (ht.gxPair.size()==0) continue;	
				
				for (String idxPair : ht.gxPair) { 	// Loop through pairs
					HitPair nhp;
					String [] tok = idxPair.split(":");
					Gene tGene = tGeneMap.get(Arg.getInt(tok[0]));
					Gene qGene = qGeneMap.get(Arg.getInt(tok[1]));
					
					if (!hprGeneMap.containsKey(idxPair)) {
						nhp = new HitPair(Arg.type2, tGene, qGene, isStEQ);
						hprGeneMap.put(idxPair, nhp);
					}
					else nhp = hprGeneMap.get(idxPair);
					
					nhp.addHit(ht);
					ht.addGeneHit(tGene, qGene);
				}
			}
			numPairsOrig = hprGeneMap.size();
			
		// Filter wrong strand  - put rest on allHprList
			ArrayList <HitPair> allHprList = new ArrayList <HitPair> ();
			
			for (HitPair hpr : hprGeneMap.values()) {		
				hpr.setSign();  		
				if (hpr.isRst) allHprList.add(hpr); // transfer to allHprList
				else { 			     				// CAS560 do not allow wrong strand; many singletons; 
					if (Arg.WRONG_STRAND_PRT) wsPairList.add(hpr);
					else hpr.clear();
					numFilter++;
				}
			}
			hprGeneMap.clear();
		
		// Filter bad ends
			sortForAssignGx(TQ, TQ, allHprList);
			for (int i=allHprList.size()-1; i>=0; i--) { // remove extend ends from least good first
				HitPair hpr = allHprList.get(i);
				hpr.setScoresMini();
				
				while (hpr.nHits>1 && rmEndHitsG2(hpr));  // if rm,  if nHits=0, filter is set.
			}
			
		// Filter main
			for (HitPair hpr : allHprList) {		
				if (hpr.nHits==0) {numFilter++; hpr.clear(); continue;} // all hits removed
				
				hpr.setScores();
				
				if (!Arg.bG2isOkay(hpr)) {numFilter++; hpr.clear(); continue;}	// Removes many!
				
				if (Arg.bG2passCov(0, hpr)) {
					g2HprList.add(hpr);
					continue;
				}
				
				hpr.flag=Arg.FILTER; 
				numFilter++;
				
				if (hpr.nHits>1) {
					for (Hit ht : hpr.hitList) { 
						HitPair nhp = new HitPair(Arg.type2, hpr.tGene, hpr.qGene, isStEQ);
						nhp.addHit(ht);
						nhp.setScores();
						if (Arg.bG2passCov(0, nhp))	g2HprList.add(nhp); 
					}
				}
				if (TRC) g2HprList.add(hpr); // will have orig single/multi; will not have multi->single
				else hpr.clear();
			} 
			allHprList.clear();
		}
		catch (Exception e) {ErrorReport.print(e,"Compute g2 genes"); bSuccess=false;}	
		}	
		/********************************************************
		 * Remove end hits if gap is intronLen (helps a lot) or large extends and is in another hit
		 * Return true if should may be able to remove another
		 * 20Dec24 Tried removing hits where the olap>len on both sides - only removed a few and did not reduce disorder
		 */
		private boolean rmEndHitsG2(HitPair hpr) {
		try {
			if (hpr.nHits==1) {
				if (hpr.pExonHitCov[T]>=100.0 && hpr.pExonHitCov[Q]>=100.0
				 && hpr.pGeneHitCov[T]>=100.0 && hpr.pGeneHitCov[Q]>=100.0) {hpr.note += " s100"; return false; }
			}
			int gap=0, extend=0;
			boolean bRm=false, bDidRm=false;

			for (int X=0; X<2; X++) { 										// X=T,Q; need to check hit=1 for the (2) test
				Hit.sortXbyStart(X, hpr.hitList);
				Gene xgene = (X==Q) ? hpr.qGene : hpr.tGene;
				Gene ygene = (X==Q) ? hpr.tGene : hpr.qGene;
				int Y =      (X==Q) ? T : Q;
				int olap =  (int) Math.round((double)Arg.pOlapOnly(xgene.gStart, xgene.gEnd, hpr.hpStart[X], hpr.hpEnd[X])/2.0); 
				
			/*  Check 1st hit */
				bRm=false;
				Hit ht0 = hpr.hitList.get(0);
				
				if (hpr.nHits>1) {											 // (1) start has large gap and hits intron
					Hit ht1 = hpr.hitList.get(1);
					gap = Math.abs(ht1.hStart[X] - ht0.hEnd[X])+1;
					bRm = (gap>intronLen4xG2rm && ht0.getExonCov(X, xgene)==0); 
					
					if (bRm && TRC) hpr.note += " rm1"+Arg.side[X];
				} 
				if (!bRm) { 											      // (2) extends passed start and is in another hpr
					extend = (ht0.hStart[X] < xgene.gStart) ? xgene.gStart-ht0.hStart[X] : 0;
					if (extend>100) {	
						if (extend > olap || ht0.getExonCov(X, xgene)==0) {		// see if overlaps another
							bRm = ht0.bHitsAnotherExon(X, xgene) && ht0.bHitsAnotherExon(Y, ygene);
							if (bRm && TRC) hpr.note += " rm1x"+Arg.side[X]+"-"+ht0.hitNum;
						}
					}
				}
				if (bRm) { 									// Remove 1st hit
					hpr.rmHit(X, xgene, ygene, 0);			// remove hit and genes from hit, sets flag=FILTER if nHits==0
					if (hpr.nHits==0) return false;
					bDidRm=true;
				}
				
			/* Check end hit */
				bRm=false;
				int sz = hpr.hitList.size()-1;
				
				ht0 = hpr.hitList.get(sz);
				if (hpr.nHits>1) {											// (1) end with gap and hits intron
					Hit ht1 = hpr.hitList.get(sz-1);
					gap = Math.abs(ht0.hStart[X] - ht1.hEnd[X]);
					bRm = (gap>intronLen4xG2rm && ht0.getExonCov(X, xgene)==0); 
					if (bRm && TRC) hpr.note += " rmN"+Arg.side[X];
				} 
				if (!bRm) { 											    // (2) extends passed end and is in another hpr
					extend = (ht0.hEnd[X] > xgene.gEnd) ? ht0.hEnd[X]-xgene.gEnd : 0;
					if (extend>100) {
						if (extend>olap  || ht0.getExonCov(X, xgene)==0) {	
							bRm = ht0.bHitsAnotherExon(X, xgene) && ht0.bHitsAnotherExon(Y, ygene);
							if (bRm && TRC) hpr.note += " rmNx"+Arg.side[X]+"-"+ht0.hitNum;
						}
					}
				}
				if (bRm) {													// Remove last hit
					hpr.rmHit(X, xgene, ygene, sz);
					if (hpr.nHits==0) return false;	
					bDidRm=true;
				}
			} // end X=TQ loop
			
			return bDidRm;
		}
		catch (Exception e) {ErrorReport.print(e,"Compute g2 genes"); bSuccess=false; return false;}	
		}
		/*********************************************************
		 * Assign major, minor and assign bin to multi and single
		 * g2HprList - complete set
		 ********************************************************/
		private void step2SetBinAllG2() { 
		try {
		// Make list of HPRs for each gene
			for (HitPair hpr : g2HprList) hpr.crossRef();
			
			for (Gene gn : qGeneMap.values()) {
				if (gn.gHprList.size()>1)
					sortForAssignGx(Q, T, gn.gHprList); // gn.gHprList will have best first 
			}
			for (Gene gn : tGeneMap.values()) {
				if (gn.gHprList.size()>1)
					sortForAssignGx(T, Q, gn.gHprList); // gn.gHprList will have best first 
			}
			
			sortForAssignGx(TQ,TQ, g2HprList);										 // sort by score - multi and single
			HashMap <Integer, HitPair> majorMap = new HashMap <Integer, HitPair> (); // bin, HitPair; used in setMinor for lookup
			    	
		// Loop Major RBH: find best major using RBH genes; these have already passed cov test idx=0
			for (HitPair hpr : g2HprList) {  			
				if (hpr.flag!=Arg.UNK) continue;				// Not Arg.FILTER
				
				if (hpr != hpr.qGene.gHprList.get(0) || 
					hpr != hpr.tGene.gHprList.get(0))  continue; // not RBH
				
				for (Hit ht: hpr.hitList) ht.setBin(nBin);
				hpr.flag = Arg.MAJOR; 
				hpr.bin = nBin++;
				majorMap.put(hpr.bin, hpr);
				
				if (TRC) hpr.note += " best";
			}
			
		// Loop Major: Good Enough 
			for (HitPair hpr : g2HprList) {  
				if (hpr.flag!=Arg.UNK) continue;				// Not Arg.FILTER or Arg.MAJOR
				
				int minorBin = setMinor(1, hpr, majorMap); 		// if can be minor, assign on next loop when all majors are done 
				
				if (minorBin==0 && Arg.bG2passCov(1, hpr)) {   	// 1 is more stringent (already passed 0)
					hpr.flag = Arg.MAJOR; 
					hpr.bin = nBin++;
					for (Hit ht: hpr.hitList) ht.setBin(hpr.bin);
					majorMap.put(hpr.bin, hpr);
					
					if (TRC) hpr.note += " good+";
				}	
			}
			
		// Loop Minor or Ignore
			for (HitPair hpr : g2HprList) {  
				if (hpr.flag!=Arg.UNK) continue;				
				
				int minorBin = setMinor(2, hpr, majorMap); 		// sufficient shared hits
				if (minorBin>0) { 
					hpr.flag = Arg.MINOR; 
					hpr.bin = minorBin;
				}
				else {numFilter++; hpr.flag = Arg.IGN; }
			}
			majorMap.clear();
			
		// Loop Remove exact coords; do last since may flip major to minor
			HitPair last=null;
			HitPair.sortByXstart(T, g2HprList);
			for (HitPair hpr : g2HprList) {  
				if (hpr.flag!=Arg.MAJOR) continue;	
				if (last!=null) {
					if (Arg.bHprOlap(last, hpr)) {
						ArrayList <HitPair> dups = new ArrayList <HitPair> (2);
						dups.add(last);
						dups.add(hpr);
						sortForAssignGx(TQ, TQ, dups);
						
						HitPair dhp = dups.get(1);
						dhp.flag = Arg.MINOR;
						dhp.bin = dups.get(0).bin;
						dhp.note = "Dup-"+dups.get(0).bin;
					}
				}
				last = hpr;
			}
		}
		catch (Exception e) {ErrorReport.print(e,"g2 set major"); bSuccess=false;}
		}
		/*********************************************************
		 * hpr: is minor? Called from setBinAllG2 
		 * majorMap: <bin, hitCnt>
		 * step: 1 check if there is a definite minor; 2 assign minor if possible
		 * The benefit of Minor is to give every gene a 'hit' if possible, as then it shows in Query Every* 
		 * Return: 0 not minor, >0 bin to set for minor
		 */
		private int setMinor(int step, HitPair hpr, HashMap <Integer, HitPair> majorMap) {
		try {
			if (!hpr.tGene.isOlapGene && !hpr.qGene.isOlapGene) return 0;
			String tGeneTag = hpr.tGene.geneTag, qGeneTag=hpr.qGene.geneTag;
	
			HashMap <Integer, Integer> binMap = new HashMap <Integer, Integer> (); // bin, cntShared
			int  cntBin0=0;
	
		// Count how may hits this hpr has in each hit bin
			for (Hit ht: hpr.hitList) {	
				if (ht.bin==0) cntBin0++;
				else {
					if (!binMap.containsKey(ht.bin)) binMap.put(ht.bin, 1);
					else binMap.put(ht.bin, binMap.get(ht.bin)+1);
					
					if (ht.bin2>0) { 		// hit can be in multiple HitPairs
						if (!binMap.containsKey(ht.bin2)) binMap.put(ht.bin2, 1);
						else binMap.put(ht.bin2, binMap.get(ht.bin2)+1);
					}
				}
			}
			if (cntBin0==hpr.nHits) return 0; // all unique
			
		// Find the best and check contained
			int bestBin=0, bestCnt=0;
			
			for (int bin : binMap.keySet()) {
				int cnt = binMap.get(bin);
				if (cnt>bestCnt) {
					HitPair majHpr = majorMap.get(bin);
					if (majHpr.tGene.geneTag.equals(tGeneTag) || majHpr.qGene.geneTag.equals(qGeneTag)) {
						bestBin = bin;
						bestCnt = cnt;
					}
				}
			}	
			if (bestCnt==hpr.nHits) return bestBin;	// Contained - does not matter if both already major

		// Not contained, then if already major do not make minor 
			int cntMajT=0, cntMajQ=0;
			for (HitPair thp : hpr.tGene.gHprList) if (thp.flag==Arg.MAJOR) cntMajT++;
			for (HitPair qhp : hpr.qGene.gHprList) if (qhp.flag==Arg.MAJOR) cntMajQ++;
			if (cntMajT>0 && cntMajQ>0) return 0;
			
		// for all major, check if bestBin or any other qualify as minor
			// 1. bestCnt will not include hits shared with overlapping majors, 
			// 2. or there may be HPRs with tied binMap counts, but different nHits
			int shareBin=0, shareCnt=0;
			for (int i=0; i<2; i++) {
				for (int bin : binMap.keySet()) {
					if (i==0 && bin!=bestBin) continue;  // give bestBin 1st chance
					if (i==1 && bin==bestBin) continue;
					
					HitPair majHpr = majorMap.get(bin);
					int cnt=0;
					for (Hit ht1: majHpr.hitList) {
						for (Hit ht2 : hpr.hitList) {
							if (ht1==ht2) {
								cnt++;
								break;
							}
						}
					}
					if (cnt==hpr.nHits) return bin;	// could still happen...
					
					double p1 = ((double) cnt/(double)hpr.nHits); 
					double p2 = ((double) cnt/(double)majHpr.nHits);
					if (p1>=pCutoff5 && p2>=pCutoff8) {
						if (step==2 || hpr.lenRatio<majHpr.lenRatio) return bin;
					}
					if (cnt>shareCnt) {
						shareCnt = cnt;
						shareBin = bin;
					}
				}
			}
			if (step==1) return 0;
			
			// one more try for step2
			HitPair majHpr = majorMap.get(shareBin);
			double p1 = ((double) shareCnt/(double)hpr.nHits); 
			double p2 = ((double) shareCnt/(double)majHpr.nHits);
			if (p1>=pCutoff5     || p2>=pCutoff8)     return shareBin; 
			if (p1>=pCutoff5-0.1 && p2>=pCutoff8-0.1) return shareBin; 
			return 0;
		}
		catch (Exception e) {ErrorReport.print(e,"g2 getMinor"); bSuccess=false; return 0;}
		}
	
		/********************************************************************/
		private void processWS() { // all needed info is in wsPairList
			if (!Arg.WRONG_STRAND_PRT) return;
			
			final int cutoff=2;
			TreeMap <String, Integer> tGeneMap = new TreeMap <String, Integer> ();
			TreeMap <String, Integer> qGeneMap = new TreeMap <String, Integer> ();
			HitPair.sortByXstart(Q, wsPairList);
			
			for (HitPair hpr : wsPairList) {
				if (!hpr.htype.equals("EE")) continue;
				
				String tTag = hpr.tGene.geneTag, qTag = hpr.tGene.geneTag;
				if (!tTag.endsWith(".") && !qTag.endsWith(".")) continue; // probably share hits with isRst overlapped genes
				
				if (tGeneMap.containsKey(tTag)) tGeneMap.put(tTag, tGeneMap.get(tTag)+hpr.nHits);
				else tGeneMap.put(tTag, hpr.nHits);
				
				if (qGeneMap.containsKey(qTag)) qGeneMap.put(qTag, qGeneMap.get(qTag)+hpr.nHits);
				else qGeneMap.put(qTag, hpr.nHits);
			}
			int cnt=0;
			for (int n : tGeneMap.values()) if (n>cutoff) cnt++;
			for (int n : qGeneMap.values()) if (n>cutoff) cnt++;
			if (cnt==0) {
				 Globals.tprt(">> No cluster hits -- must have wrong strand orientation");
				 return;
			}
			
			cnt=0;
			Globals.prt(String.format("#Hits  %-10s %-10s   [Exon   Gene   Length]      [Exon   Gene   Length]      [EE Gene Hit]", 
					grpPairObj.mainObj.proj1Name, grpPairObj.mainObj.proj2Name));
			
			for (HitPair hpr : wsPairList) {
				if (!hpr.htype.equals("EE")) continue;
				
				String tTag = hpr.tGene.geneTag, qTag = hpr.tGene.geneTag;
				if (!tTag.endsWith(".") && !qTag.endsWith(".")) continue;
				
				if (tGeneMap.get(tTag)>cutoff || qGeneMap.get(qTag)>cutoff) { // don't want a lot of little hits
					Globals.prt(hpr.toWSResults(chrPair));
					cnt++;
				}
			}
			if (cnt==0) Globals.tprt(">> No cluster hits - must have wrong strand orientation");
		}
		/**************************************************************
		 * Counts content of notes from this file, booleans, and single/mult
		 */
		private void prtTrace(boolean isStEQ) {
			if (!TRC) return;
			
			int cntM=0, cntDis=0, cntRm1=0, cntRmN=0, cntS=0;
			int cntMajM=0, cntMajS=0, cntMinM=0, cntMinS=0, cntGoodM=0, cntGoodS=0;
			int cntfM=0, cntfS=0,  cntIgnM=0, cntIgnS=0, cntFlip=0;
			
			sortForAssignGx(TQ, TQ, g2HprList);
			for (HitPair hpr : g2HprList) {
				if (!hpr.isOrder) cntDis++;
				if (hpr.note.contains("rm1")) cntRm1++;
				if (hpr.note.contains("rmN")) cntRmN++;
				if (hpr.note.contains("Dup")) cntFlip++;
				if (hpr.nHits==1) cntS++; else cntM++;
				
				if (hpr.flag==Arg.MAJOR) {
					boolean bGood = hpr.note.contains("good");
					if (hpr.nHits==1) {
						cntMajS++; 
						if (bGood) cntGoodS++;
					}
					else {
						cntMajM++;
						if (bGood) cntGoodM++;
					}
				}
				else if (hpr.flag==Arg.MINOR) {
					if (hpr.nHits==1) cntMinS++; else cntMinM++;
				}
				else if (hpr.flag==Arg.FILTER) {
					if (hpr.nHits==1) cntfS++; 	else cntfM++;
				}
				else if (hpr.flag==Arg.IGN) {
					if (hpr.nHits==1) cntIgnS++; else cntIgnM++;
				}
			}
			String msg =  String.format("  %s %,d (%,d) g2: M %,d (DO %,d, Rm %,d %,d) S %s", 
					Arg.isEQstr(isStEQ), g2HprList.size(), numPairsOrig, cntM, cntDis, cntRm1, cntRmN, Arg.int2Str(cntS));
			
			String msgP = String.format("  Maj(G): M %,d (%,d)  S %,d (%,d)   Min: M %,d S %,d",
					 cntMajM, cntGoodM, cntMajS, cntGoodS, cntMinM, cntMinS);
			
			String msgF = String.format("  f: M %,d  S %,d Ign: M %,d  S %,d  Exact: %,d", 
					cntfM, cntfS, cntIgnM, cntIgnS, cntFlip);
			
			Utils.prtIndentMsgFile(plog, 1, String.format("%-40s %-40s %s", msg, msgP, msgF));
			
			if (grpPairObj.mainObj.fhOutHPR[2]!=null) {
				grpPairObj.mainObj.fhOutHPR[2].println("## " + chrPair + " " + Arg.isEQstr(isStEQ) + " HPRs: " + g2HprList.size());
				for (HitPair ph : g2HprList) 
					grpPairObj.mainObj.fhOutHPR[2].println(ph.toResults(chrPair, true)+"\n");
			}
		}
		
	} // End G2 class
	/*****************************************************************
	// G1 create opposite side pseudogenes and match with T/Q genes
	 * Uses genes to gather hits without mate
	 * All hits with a bin>0 are in a G2; G1 hits get a temporary tbin 
	 ******************************************************************/
	private class G1Pairs {
		private ArrayList <HitPair> g1HprList  = new ArrayList <HitPair> (); // Final list, to be  added to gxPairList 
		
		private G1Pairs() {
			if (TRC) Utils.prtIndentMsgFile(plog, 0, ">>G1: Intron 2x " + intronLen2xG1 
					+ " Sm " + intronLenRm + "   hits " + grpHitList.size());
			
			if (qGeneMap.size()!=0) {	// Make T pseudo-genes and use Q genes; put results in global gxPairList
				runG1(Q, T, qGeneMap, true);  if (!bSuccess) return;	// isStEQ
				runG1(Q, T, qGeneMap, false); if (!bSuccess) return;	// !isStEQ
			}
			if (tGeneMap.size()!=0) {	// Make Q pseudo-genes and use T genes
				runG1(T, Q, tGeneMap, true);  if (!bSuccess) return;
				runG1(T, Q, tGeneMap, false); if (!bSuccess) return;
			}
			for (Hit ht : grpHitList) ht.bin2=0;
		}
		private void runG1(int X, int Y, TreeMap <Integer, Gene> xGeneMap, boolean isStEQ) {
			try {
				Globals.rprt("Find G1 hits for " + Arg.isEQstr(isStEQ) + " side " + Arg.side[X]);
				
			// Process
				stepMkPairs(X, Y, xGeneMap, isStEQ);
			
			// Bins - no minor assigned
				sortForAssignGx(X, Y, g1HprList);
				
				for (HitPair hpr : g1HprList) {
					if (hpr.flag==Arg.UNK) { // could be FILTER
						hpr.flag = Arg.MAJOR; 
						hpr.bin = nBin++;
						for (Hit ht: hpr.hitList) if (ht.bin==0) ht.bin = hpr.bin;
					}
				}
				
			// Finish 
				for (HitPair hpr : g1HprList) gxFinalList.add(hpr);
				
				prtTrace(X, isStEQ);
				
				g1HprList.clear();
			}
			catch (Exception e ) {ErrorReport.print(e, "runG1");
			}
		}
		private void stepMkPairs(int X, int Y, TreeMap <Integer, Gene> xGeneMap, boolean isStEQ) {
			try {	
				double lenRatioCut = Arg.dLenRatio +0.2;
				int cntG1Mf=0, cntG1Sf=0;
				
				int type = (X==Q) ? Arg.type1q : Arg.type1t;
				int tBin2 = 1;					// temporary bin to show its in a multiMap or been used
				
			/* g1 loop by gene 	*/
				for (Gene xgene : xGeneMap.values()) {
					ArrayList <Hit> gnHitList = xgene.getHitsForXgene(X, Y, isStEQ); // bin=0, no Y gene for xgene
					if (gnHitList==null || gnHitList.size()==0) continue;
					
					Hit.sortBySignByX(Y, gnHitList); // sort by intergenic
					Gene tGene = (X==T) ? xgene : null;
					Gene qGene = (X==Q) ? xgene : null;
					
					HashMap <Integer, HitPair> hprMultiMap = new HashMap <Integer, HitPair> (); // bin, object
					int nHits = gnHitList.size();
					
				// For gene loop by hit: create multi-hpr; a gene can have multiple hprs 
					for (int r1=0; r1<nHits-1; r1++) {
						Hit ht1 = gnHitList.get(r1); 				   // if in a bin, extend it
						if (ht1.bin2>0 &&  !hprMultiMap.containsKey(ht1.bin2)) continue; // already taken for this Q,isStEq
						
						for (int r2=r1+1; r2<nHits; r2++) {
							Hit ht2 = gnHitList.get(r2);
							if (ht2.bin2>0) continue;
		
							int yDiff = Math.abs(ht2.hStart[Y]-ht1.hEnd[Y]); 
							if (yDiff> intronLen2xG1) continue; 		// CAS560 changed from glen to Arg.iGmIntronLenG0
							
							HitPair hpr=null;
							if (ht1.bin2==0) {						// make a hitPair if at least two hits within distance				
								hpr = new HitPair(type, tGene, qGene, isStEQ);
								ht1.bin2 = tBin2++; 				// set bin so it will not be made a single hpr
								hpr.addHit(ht1);
								
								hprMultiMap.put(ht1.bin2, hpr);
							}
							else hpr = hprMultiMap.get(ht1.bin2);
									
							ht2.bin2 = ht1.bin2;
							hpr.addHit(ht2);
						}
					}	
				// For multi in gene's hprMultiMap
					for (HitPair hpr : hprMultiMap.values()) {
						
					// prune ends from gene side, then non-gene
						hpr.setScoresMini(); 
						while (rmEndHitsG1(X, hpr, true)); 
						while (hpr.lenRatio<lenRatioCut && rmEndHitsG1(Y, hpr, false));
						if (hpr.nHits==0) {
							if (TRC) {g1HprList.add(hpr);hpr.flag = Arg.FILTER;} 
							else hpr.clear(); 
							continue;
						}
						
					// score and filter
						hpr.setScores();
		
						if (hpr.nHits>1 && Arg.bG1passCov(X, hpr)) { 
							g1HprList.add(hpr);
						}
						else { // break back into singles
							cntG1Mf++; 
							for (Hit ht : hpr.hitList) ht.bin2 = 0; // identify singles in gnHitList
							
							if (TRC) {
								hpr.flag = Arg.FILTER;
								g1HprList.add(hpr);
							}
							else hpr.clear();
						}
					}
					hprMultiMap.clear();
					
				// For singles in gene's gnHitList
					for (Hit ht : gnHitList) { 
						if (ht.bin2>0) continue; 
						
						HitPair hpr = new HitPair(type, tGene, qGene, isStEQ);
						hpr.addHit(ht);
						
						hpr.setScores();	 			
						
						if (Arg.bG1passCov(X, hpr)) { 
							g1HprList.add(hpr);
							ht.bin2 = tBin2++;
						}
						else {	
							cntG1Sf++; 
							if (TRC) {
								hpr.flag = Arg.FILTER;
								g1HprList.add(hpr);
							}
							else hpr.clear();
						}
					}
					gnHitList.clear(); 
				} // finish g1 Loop by gene
				
				grpPairObj.mainObj.cntG1Fil+= (cntG1Sf+cntG1Mf);	
			}
			catch (Exception e) {ErrorReport.print(e,"Compute G1 clusters"); bSuccess=false;}	
		}
		/********************************************************
		 * Remove end hits if gap is intronLen
		 * Without the other gene constraint, the gene side have the big gap at beginning or end
		 * Return True: run again, False: do not run again
		 */
		private boolean rmEndHitsG1(int X, HitPair hpr, boolean isGene) {
		try {
			if (hpr.nHits<=1) return false;

			Hit.sortXbyStart(X, hpr.hitList); 
			
			Gene xygene;
			if (X==Q) if (isGene) xygene = hpr.qGene; else xygene=hpr.tGene;
			else      if (isGene) xygene = hpr.tGene; else xygene=hpr.qGene;
			
			boolean bDidRm=false, bTest;
			Hit ht0, ht1;
			double olap;
			int iRm;
			
			for (int i=0; i<2; i++) { // i=0 remove 1st, i=1 remove end
				if (i==0) {
					ht0 = hpr.hitList.get(0);
					ht1 = hpr.hitList.get(1);
					iRm=0;
				}
				else {
					ht0 = hpr.hitList.get(hpr.nHits-1);
					ht1 = hpr.hitList.get(hpr.nHits-2);	
					iRm = hpr.nHits-1;
				}
				olap = Arg.pGap_nOlap(ht0.hStart[X], ht0.hEnd[X], ht1.hStart[X], ht1.hEnd[X]);
			
				bTest = isGene && ((olap > intronLen2xG1) || (olap>intronLenRm && ht0.getExonCov(X, xygene)==0));
				if (!bTest) bTest = !isGene && (olap > intronLenRm); // it may cover an exon on gene side, but would need to check amount olap on that side

				if (bTest) {
					hpr.rmHit(X, xygene, null, iRm); // setScoreMini after change
					ht0.bin2 = 0;				     // Hit is in gene list, can try for single
					bDidRm = true;
					if (TRC) {
						String x = (i==0) ? "0" : "n";
						hpr.note += " rm" +  Arg.toTF(isGene) + x + Arg.side[X]  + "#" + ht0.hitNum;
					}
				}
				if (hpr.nHits<=1) return false;
			}
			
			return bDidRm;
		}
		catch (Exception e) {ErrorReport.print(e,"g1 rm end hits "); bSuccess=false; return false;}	
		}
		
		/*************************************************
		 * Write summary and to file
		 */
		private void prtTrace(int X, boolean isStEQ) {
		try {
			if (!TRC) return;
			
			int cntM=0, cntDis=0, cntS=0, cntRm1T=0, cntRmNT=0 , cntRm1F=0, cntRmNF=0;
			int cntMajM=0, cntMajS=0, cntfM=0, cntfS=0;
			
			for (HitPair hpr : g1HprList) {
				if (!hpr.isOrder) cntDis++;
				if (hpr.note.contains("rmT0")) cntRm1T++;
				else if (hpr.note.contains("rmF0")) cntRm1F++;
				else if (hpr.note.contains("rmTn")) cntRmNT++;
				else if (hpr.note.contains("rmFn")) cntRmNF++;
				if (hpr.nHits==1) cntS++; else cntM++;
				
				if (hpr.flag==Arg.MAJOR) {
					if (hpr.nHits==1) cntMajS++;  else cntMajM++;	
				}
				else if (hpr.flag==Arg.FILTER) {
					if (hpr.nHits==1) cntfS++; 	else cntfM++;
				}
			}
			String msg =  String.format("  %s %s g1: M %s (DO %,d, Rm T %,d %,d  F %,d %,d) S %s", 
					Arg.isEQstr(isStEQ), Arg.int2Str(g1HprList.size()), Arg.int2Str(cntM), cntDis, cntRm1T, cntRmNT, cntRm1F, cntRmNF, Arg.int2Str(cntS));
			
			String msgP = String.format("  Maj : M %,5d  S %,d", cntMajM, cntMajS);
			
			String msgF = String.format("  f: M %,d  S %,d", cntfM, cntfS);
			
			Utils.prtIndentMsgFile(plog, 1, String.format("%-50s %-25s %s", msg, msgP, msgF));

			// To file
			if (grpPairObj.mainObj.fhOutHPR[1]!=null) {
				grpPairObj.mainObj.fhOutHPR[1].println("##" +  Arg.side[X] + " " + Arg.isEQstr(isStEQ) + " HPRs: " + g1HprList.size());
				for (HitPair ph : g1HprList) 
					grpPairObj.mainObj.fhOutHPR[1].println(ph.toResults("", true)+"\n");
			}
		}
		catch (Exception e) {ErrorReport.print(e,"g1 prt trace"); bSuccess=false;}	
		}
	} // End G1
	/***************************************************************
	 * G0: Create pairs for G0, where both sides are bound by intron length
	 * CAS560 changes:
	 *  Was using constants 2500 for plants and 5000 for mammalian; now using (intron[0]+intron[1])/2 (see Arg.setLen)
	 *  In filterMulti, the HitPair.bPassCovG0() now uses 2x the cutoff for hitscore and both T and Q must pass
	 *  The first loop uses Math.abs for qDiff
	 */
	private class G0Pairs {
		int cntG0Mf=0, cntG0Sf=0;	
		
		private G0Pairs() {
			if (TRC) Globals.tprt(">>G0 use Intron " + intronLenG0);
			
			for (int eq=0; eq<2; eq++) {
				runG0(eq==0); 		if (!bSuccess) return;
			}
			grpPairObj.mainObj.cntG0Fil+=(cntG0Mf+cntG0Sf);
		}
		/* create by finding runs of hits with intron length */
		private void runG0(boolean isStEQ) {
		try {
			ArrayList <Hit> g0HitList = new ArrayList <Hit> ();	// Find isStEQ hits with no genes
			for (Hit ht : grpHitList) {
				if (ht.isStEQ==isStEQ && ht.bNoGene()) g0HitList.add(ht); 
			}
			Hit.sortXbyStart(Q, g0HitList);
			Globals.rprt("G0 hits " + String.format("%,d", g0HitList.size()) + " for " + Arg.isEQstr(isStEQ));
			
		// Multi: Loop thru g0 hits to create multi hprs
			int tBin=1, nG0Hits=g0HitList.size();		
			HashMap <Integer, HitPair> multiHprMap = new HashMap <Integer, HitPair> ();
		
			for (int r0=0; r0<nG0Hits-1; r0++) {			
				Hit ht1 = g0HitList.get(r0);
				
				for (int r1=r0+1; r1<nG0Hits; r1++) {
					Hit ht2 = g0HitList.get(r1);			
					if (ht2.bin2>0) continue;

					int qDiff = ht2.hStart[Q]-ht1.hEnd[Q]; 
					if (qDiff>intronLenG0) break;           // CAS560 Arg.useIntronLen has changed; see Arg.setLen
					
					int tDiff = (isStEQ) ? Math.abs(ht2.hStart[T] - ht1.hEnd[T]) 
	                                     : Math.abs(ht1.hStart[T] - ht2.hEnd[T]); 
					if (tDiff>intronLenG0)  continue;
					
					HitPair hpr;
					if (ht1.bin2==0) {
						hpr = new HitPair(Arg.type0, null, null, isStEQ);
						multiHprMap.put(tBin, hpr);
						
						hpr.addHit(ht1);
						ht1.bin2 = tBin++;
					}
					else hpr = multiHprMap.get(ht1.bin2);
					
					ht2.bin2 = ht1.bin2;
					hpr.addHit(ht2);
				}
			}
			
		// Multi: Loop multiHpr and score
			ArrayList <HitPair> hprList = new ArrayList <HitPair> ();
			for (HitPair hpr : multiHprMap.values()) {
				
				hpr.setScoresMini();
				if (hpr.lenRatio<0.1) continue;
				
				int cntRm=0;
				while (cntRm<5 && rmEndHitsG0(hpr)) {cntRm++;} // setScoreMini, bin2=0 if removed
				if (hpr.nHits<=1) {
					if (TRC) {hpr.flag = Arg.FILTER; hprList.add(hpr);}
					else hpr.clear();
					continue;
				}			
				hpr.setScores();			
				if (Arg.bG0passCov(hpr)) {		
					hprList.add(hpr);
					hpr.flag = Arg.MAJOR;
				}
				else { 
					cntG0Mf++;
					for (Hit ht : hpr.hitList) ht.bin2=0; // for singles
					
					if (TRC) {hpr.flag = Arg.FILTER; hprList.add(hpr);}
					else hpr.clear();
				}
			}
			multiHprMap.clear();
			
		// Singles: Loop thru g0 hits to create and filter single hprs
			for (Hit ht : g0HitList) { 					
				if (ht.bin2>0) {ht.bin2=0; continue;}
				
				HitPair hpr = new HitPair(Arg.type0, null, null, isStEQ);
				hpr.addHit(ht);
				hpr.setScores(); 
				
				if (Arg.bG0passCov(hpr)) { 
					hprList.add(hpr);	
				}
				else {
					cntG0Sf++;
					
					if (Globals.TRACE) {hpr.flag = Arg.FILTER; hprList.add(hpr);}
					else hpr.clear();
				}
			}
			g0HitList.clear();
			
			sortForAssignG0(hprList);				// Add sorted g0PairList to main list
			for (HitPair hpr : hprList) {
				hpr.bin = nBin++;
				if (hpr.flag!=Arg.FILTER) hpr.flag = Arg.MAJOR;
				gxFinalList.add(hpr); 
			}
			
			prtTrace(isStEQ, hprList);
			hprList.clear();
		}
		catch (Exception e) {ErrorReport.print(e,"Compute G0 clusters"); bSuccess=false;}	
		}
		private boolean rmEndHitsG0(HitPair hpr) {
			try {
				if (hpr.nHits<=1) return false;

				boolean bDidRm=false;
				Hit ht0, ht1;
				double olap;
				int iRm;
				
				for (int X=0; X<2; X++) { // X=0 remove 1st, X=1 remove end
					Hit.sortXbyStart(X, hpr.hitList); 
					
					if (X==0) {
						ht0 = hpr.hitList.get(0);
						ht1 = hpr.hitList.get(1);
						iRm=0;
					}
					else {
						ht0 = hpr.hitList.get(hpr.nHits-1);
						ht1 = hpr.hitList.get(hpr.nHits-2);	
						iRm = hpr.nHits-1;
					}
					olap = Arg.pGap_nOlap(ht0.hStart[X], ht0.hEnd[X], ht1.hStart[X], ht1.hEnd[X]);
				
					if (olap > intronLenRm) {
						hpr.rmHit(X, null, null, iRm); // setScoreMini after change
						ht0.bin2 = 0;				   // Hit is in gene list, can try for single
						bDidRm = true;
						if (TRC) {
							String x = (X==0) ? "0" : "N";
							hpr.note += " rm"  + x + Arg.toTF(hpr.isStEQ) + Arg.side[X]  + "#" + ht0.hitNum;
						}
					}
					if (hpr.nHits<=1) return false;
				}
				return bDidRm;
			}
			catch (Exception e) {ErrorReport.print(e,"g1 rm end hits "); bSuccess=false; return false;}	
		}
		/*************************************************
		 * Write summary and to file
		 */
		private void prtTrace(boolean isStEQ, ArrayList <HitPair> hprList) {
		try {
			if (!TRC) return;
			int cntM=0, cntDis=0, cntS=0, cntRm1T=0, cntRm1F=0, cntRmNT=0, cntRmNF=0;
			
			for (HitPair hpr : hprList) {
				if (!hpr.isOrder) cntDis++;
				if (hpr.note.contains("rm0T")) cntRm1T++;
				if (hpr.note.contains("rm0F")) cntRm1F++;
				if (hpr.note.contains("rmNT")) cntRmNT++;
				if (hpr.note.contains("rmNF")) cntRmNF++;
				if (hpr.nHits==1) cntS++; else cntM++;
			}
			String msg =  String.format("  %s %s g0: M %s (DO %,d, Rm Eq %,d %,d Neq %,d %,d )   S %s", 
					Arg.isEQstr(isStEQ), Arg.int2Str(hprList.size()), Arg.int2Str(cntM), cntDis, 
					cntRm1T, cntRmNT,  cntRm1F, cntRmNF, Arg.int2Str(cntS));
			
			String msgF = String.format("  f: M %,d  S %,d", cntG0Mf, cntG0Sf);
			
			Utils.prtIndentMsgFile(plog, 1, String.format("%-50s %s", msg,  msgF));

			// To file
			if (grpPairObj.mainObj.fhOutHPR[0]!=null) {
				grpPairObj.mainObj.fhOutHPR[0].println("##"  + Arg.isEQstr(isStEQ) + " HPRs: " + hprList.size());
				for (HitPair ph : hprList) 
					grpPairObj.mainObj.fhOutHPR[0].println(ph.toResults("", true)+"\n");
			}
		}
		catch (Exception e) {ErrorReport.print(e,"g1 prt trace"); bSuccess=false;}	
		}
	} // End class G0
	
	///////////////////////////////////////////////////////////////////////////////////////
	// CAS558 added this because had a violates 
	protected void sortForAssignGx(int X, int Y, ArrayList <HitPair> gpList) {// G2, G1
		try {
			sortForAssignTry1(X, Y, gpList);
			return;
		}
		catch (Exception e) {}	
		Globals.dtprt("First sort did not work - try easier one...");
		
		try {
			sortForAssignTry2(X, gpList);
			return;
		}
		catch (Exception e) {}	
		
		Globals.dtprt("Second sort did not work - try an easier...");
		try {
			sortForAssignTry3(X, gpList);
			return;
		}
		catch (Exception e) {}	
		
		Globals.eprt("Algo2 will not work for this dataset - use Algo1");
		bSuccess=false;
	}
	// This can get a comparison Comparison method violates its general contract!
	
	// This can get a comparison Comparison method violates its general contract!
	protected void sortForAssignTry1(int X, int Y, ArrayList <HitPair> gpList) { // GrpPairGx g2TQ
		Collections.sort(gpList, 
			new Comparator<HitPair>() {
				public int compare(HitPair h1, HitPair h2) {
					if (h1.isRst && !h2.isRst) return -1; 
					if (!h1.isRst && h2.isRst) return  1;
					
					if (h1.flag==Arg.UNK && h2.flag!=Arg.UNK) return -1; // could be filter, dup
					if (h1.flag!=Arg.UNK && h2.flag==Arg.UNK) return  1; 
					
					if (h1.htype.equals(Arg.sEE) && !h2.htype.equals(Arg.sEE)) return -1;
					if (!h1.htype.equals(Arg.sEE) && h2.htype.equals(Arg.sEE)) return 1;
					
					if (h1.pExonHitCov[X]>h2.pExonHitCov[X]) return -1; 
					if (h1.pExonHitCov[X]<h2.pExonHitCov[X]) return  1;
					
					if (h1.pGeneHitOlap[X]>h2.pGeneHitOlap[X]) return -1;
					if (h1.pGeneHitOlap[X]<h2.pGeneHitOlap[X]) return  1;
					
					if (h1.pHitGapCov[X]>h2.pHitGapCov[X]) return -1;
					if (h1.pHitGapCov[X]<h2.pHitGapCov[X]) return  1;
					
					// in case one side as the same hits and ratio, check the other
					if (X!=Y) {
						if (h1.pExonHitCov[Y]>h2.pExonHitCov[Y]) return -1; 
						if (h1.pExonHitCov[Y]<h2.pExonHitCov[Y]) return  1;
						
						if (h1.pGeneHitOlap[Y]>h2.pGeneHitOlap[Y]) return -1;
						if (h1.pGeneHitOlap[Y]<h2.pGeneHitOlap[Y]) return  1;
						
						if (h1.pHitGapCov[Y]>h2.pHitGapCov[Y]) return -1;
						if (h1.pHitGapCov[Y]<h2.pHitGapCov[Y]) return  1;
					}
					if (h1.lenRatio>h2.lenRatio) return -1; 				
					if (h1.lenRatio<h2.lenRatio) return  1; 
					
					if (h1.nHits>h2.nHits) return -1; 
					if (h1.nHits<h2.nHits) return 1;
					
					if (h1.nHpr>h2.nHpr) return -1; // deterministic sort on identical hits; may have caused the violate
					if (h1.nHpr<h2.nHpr) return 1;
					
					return 0;	
				}
			}
		);
	}
	protected void sortForAssignTry2(int X, ArrayList <HitPair> gpList) { // GrpPairGx g1T, g1Q, g2TQ
		Collections.sort(gpList, 
			new Comparator<HitPair>() {
				public int compare(HitPair h1, HitPair h2) {
					if (h1.isRst && !h2.isRst) return -1; 
					if (!h1.isRst && h2.isRst) return  1;
					
					if (h1.flag==Arg.UNK && h2.flag!=Arg.UNK) return -1; // could be filter 
					if (h1.flag!=Arg.UNK && h2.flag==Arg.UNK) return  1; 
					
					if (h1.pExonHitCov[X]>h2.pExonHitCov[X]) return -1; 
					if (h1.pExonHitCov[X]<h2.pExonHitCov[X]) return  1;
					
					if (h1.nHits>h2.nHits) return -1; 
					if (h1.nHits<h2.nHits) return 1;
					
					if (h1.nHpr>h2.nHpr) return -1; 
					if (h1.nHpr<h2.nHpr) return 1;
					
					return 0;	
				}
			}
		);
	}
	protected void sortForAssignTry3(int X, ArrayList <HitPair> gpList) { // GrpPairGx g1T, g1Q, g2TQ
		Collections.sort(gpList, 
			new Comparator<HitPair>() {
				public int compare(HitPair h1, HitPair h2) {
					if (h1.flag==Arg.UNK && h2.flag!=Arg.UNK) return -1; // could be filter 
					if (h1.flag!=Arg.UNK && h2.flag==Arg.UNK) return  1; 
					
					if (h1.htype.equals(Arg.sEE) && !h2.htype.equals(Arg.sEE)) return -1;
					if (!h1.htype.equals(Arg.sEE) && h2.htype.equals(Arg.sEE)) return 1;
	
					if (h1.nHpr>h2.nHpr) return -1; 
					if (h1.nHpr<h2.nHpr) return 1;
					
					return 0;	
				}
			}
		);
	}
	
	protected void sortForAssignG0(ArrayList <HitPair> gpList) { // GrpPairGx g0
		Collections.sort(gpList, 
			new Comparator<HitPair>() {
				public int compare(HitPair h1, HitPair h2) {
					int X = TQ;
					if (h1.flag==Arg.MAJOR && h2.flag!=Arg.MAJOR) return -1; // could be filter 
					if (h1.flag!=Arg.MAJOR && h2.flag==Arg.MAJOR) return  1; 
					
					if (h1.pHitGapCov[X]>h2.pHitGapCov[X]) return -1;
					if (h1.pHitGapCov[X]<h2.pHitGapCov[X]) return  1;
					
					if (h1.xMaxCov>h2.xMaxCov) return -1; 	
					if (h1.xMaxCov<h2.xMaxCov) return  1; 
					
					if (h1.nHits>h2.nHits) return -1; 
					if (h1.nHits<h2.nHits) return 1;
					
					return 0;	
				}
			}
		);
	}
}
