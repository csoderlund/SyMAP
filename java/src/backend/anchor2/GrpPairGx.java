package backend.anchor2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.HashMap;

import backend.Utils;
import util.ErrorReport;
import util.ProgressDialog;

/******************************************************
 * Computes bins for g2, g1, g0 for this GrpPair
 */
public class GrpPairGx {
	private static final int T=Arg.T, Q=Arg.Q, TQ=Arg.TQ;

	private int nBin=1; 

	// from GrpPair; 
	private GrpPair grpPairObj=null;
	private TreeMap <Integer, Gene> tGeneMap = null, qGeneMap = null;  // key: geneIdx
	private ArrayList <Hit> qGrpHitList = null; 
	private ProgressDialog plog;
	private String chrPair;
	
	// The order is very important as bins are assigned according to order
	// Order; 1 add G2 sorted on score, 2 add G1 sorted on score, 3 add G0 sorted on xMaxLen, 
	//        4 assign bin, 5 add split all minor with no order 
	// GrpPairs: create clusters from major, saveAnnoHits G2/G1 major and minor
	private ArrayList <HitPair> gxPairList = new ArrayList <HitPair> ();	
	
	private boolean bSuccess=true;  // used by all
	
	public GrpPairGx(GrpPair grpPairObj) { // GrpPair.buildClusters; uses gxPairList to finish and save 
		this.grpPairObj = grpPairObj;
		plog = grpPairObj.plog;
		
		tGeneMap = grpPairObj.tGeneMap;
		qGeneMap = grpPairObj.qGeneMap;
		for (Gene gn : tGeneMap.values()) {gn.sortHits(T);}
		for (Gene gn : qGeneMap.values()) {gn.sortHits(T);}
		
		qGrpHitList = grpPairObj.grpHitList;
		Hit.sortBySignByX(Q, qGrpHitList);
		for (Hit ht : qGrpHitList) ht.setType();
		
		chrPair = grpPairObj.tChr + "," + grpPairObj.qChr;
	}
	/*****************************************************
	 * All results are put in gxPairList
	 */
	protected boolean run() {
	try {
		new G2Pairs();				if (!bSuccess) return false;// G2: Genes are already assigned to genes
		
		new G1Pairs(); 				if (!bSuccess) return false;// G1: Make T or Q pseudo-genes
														
		new G0Pairs();				if (!bSuccess) return false;// G0: Make T and Q pseudo-genes
		
	/* Finish */
		Arg.tprt("Finish pairs");
		Arg.tprt(String.format("%,10d gxPairList Initial", gxPairList.size()));
		
		assignBin();				if (!bSuccess) return false;		
			
		splitBinPairs();			if (!bSuccess) return false;

		filterMulti();				if (!bSuccess) return false;
		
		prtHitPairs();
		
		return true;
	}
	catch (Exception e) {ErrorReport.print(e, "GrpPairGx run"); return false; }
	}
	protected ArrayList <HitPair> getPairList() {return gxPairList;}
	
	
	/**********************************************************/
	private void assignBin() {
		try {
			for (HitPair hpr : gxPairList) { // sorted in runG2, runG1, runG0
				if (hpr.flag==Arg.FILTER) continue;
				
				boolean found=false;
				for (Hit ht: hpr.hitList) {		// 2. Loop through GP hits assigning bin
					if (ht.bFilter) continue;
					
					if (ht.bin==0) { 				
						if (hpr.bin==0) {
							hpr.flag = Arg.MAJOR;
							hpr.bin = nBin;
							found = true;
						}
						ht.bin = nBin;			// ASSIGN BIN
					}
					else hpr.hasBin=true; // has a hit that already is assigned
				}
				if (found) nBin++;
			}
			Arg.tprt(String.format("%,10d assigned bins", nBin));
		}
		catch (Exception e) {ErrorReport.print(e,"Compute gene pair"); bSuccess=false;}	
	}
	
	/* determine minor for overlapping genes */
	private void splitBinPairs() {
	try {
		ArrayList <HitPair> splitPairs = new ArrayList <HitPair> ();
		int cntSplit=0;
		
		for (HitPair hpr : gxPairList) {
			if (hpr.gtype==Arg.type0 || !hpr.hasBin) continue;
		
			int cntHasBin=0, cntDiff=0, saveBin=0;									
			for (Hit ht: hpr.hitList) {
				if (ht.bin>0) {
					cntHasBin++;
					if (saveBin==0) saveBin=ht.bin;
					else if (saveBin!=ht.bin) cntDiff++;
				}
			}
		
			if (hpr.bin==0 && cntHasBin==hpr.hitList.size() && cntDiff==0) { // already scored 
				hpr.flag = Arg.MINOR;				  
				hpr.bin = hpr.hitList.get(0).bin;	// inherit major CLnum in GrpPair.createClusters
				hpr.hasBin = false;
				continue; 		
			}
			if (cntDiff==0) continue; 
		
		// Split into multiple hitpairs for different bins in original
			TreeMap <Integer, HitPair> binPairs = new TreeMap <Integer, HitPair> ();
			for (Hit ht: hpr.hitList) {
				HitPair nhp;
				if (!binPairs.containsKey(ht.bin)) {	// new hitpair
					nhp = new HitPair(hpr.gtype, hpr.tGene, hpr.qGene, hpr.isStEQ);
					nhp.bin = ht.bin;
					if (hpr.flag==Arg.MAJOR && hpr.bin==ht.bin) nhp.flag = Arg.MAJOR;
					else 										nhp.flag = Arg.MINOR;
					
					binPairs.put(ht.bin, nhp);
					nhp.isBnSpl = true;			// for toResults
				}	
				else nhp = binPairs.get(ht.bin); 
				
				if (ht.bin==0) ht.bin = nhp.bin;
				nhp.addHit(ht); 
			} // end loop through hits for hitPair
			
			cntSplit++;
			hpr.flag = Arg.SPLIT;		// ignore this one; made a new one with ph.bin=ht.bin only
			for (HitPair hp : binPairs.values()) splitPairs.add(hp); 
		} // end HitPair loop
		
		// Add new pairs to main list
		for (HitPair hpr : splitPairs) {
			hpr.setScores();			// split score
			
			if (hpr.bBothGene && !hpr.bPassCovG2()) hpr.flag=Arg.FILTER;
			else if (hpr.bEitherGene && !hpr.bPassCovG1()) hpr.flag=Arg.FILTER;
			
			if (hpr.flag!=Arg.FILTER || Arg.TRACE) gxPairList.add(hpr); 	
		}
		splitPairs.clear();
		
		Arg.tprt(String.format("%,10d gxPairList SplitBin (Splits %d)", gxPairList.size(), cntSplit));
	}
	catch (Exception e) {ErrorReport.print(e,"assign bin"); bSuccess=false;}	
	}	
	/********************************************************
	 * Filtered after split as could fail before split...
	 */
	protected void filterMulti() {
	try {
		int cntF0=0, cntF1=0, cntF2=0, cntGood=0;
		
		for (HitPair hpr : gxPairList) 	{
			boolean bPassWS =  !hpr.isRst && // !isRst = not right sign
							(hpr.htype.equals("EE") || hpr.htype.equals("EI")) &&
					 		hpr.xMaxCov>1000 && hpr.flag==Arg.MAJOR;
			
			if (hpr.hitList.size()==1) {// have already passed cutoff filters
				if (bPassWS) {
					grpPairObj.cntWSSingle++;
					if (Arg.WRONG_STRAND_EXC) hpr.flag=Arg.FILTER;
					if (Arg.WRONG_STRAND_PRT) Arg.prt(hpr.toWSResults(chrPair));	
				}
			}
			if (hpr.flag==Arg.SPLIT || hpr.hitList.size()==1) continue;
					
			if (hpr.bBothGene) {
				if (!hpr.bPassCutoff(T) || !hpr.bPassCutoff(Q)) { 
					hpr.flag = Arg.FILTER;
					cntF2++;
				}
				else if (bPassWS) {
					grpPairObj.cntWSMulti++;
					if (Arg.WRONG_STRAND_EXC) hpr.flag=Arg.FILTER;
					if (Arg.WRONG_STRAND_PRT) Arg.prt(hpr.toWSResults(chrPair));	
				}
			}
			else if (hpr.bEitherGene) {
				if (!hpr.bPassCutoff(T) || !hpr.bPassCutoff(Q)) {// intron check for g0
					hpr.flag = Arg.FILTER;
					cntF1++;
				}
			}
			else { // no gene
				if (!hpr.bPassCutoff()) {
					hpr.flag = Arg.FILTER;
					cntF0++;
				}
			}
			if (hpr.flag==Arg.MINOR || hpr.flag==Arg.MAJOR) cntGood++;
		}
		Arg.tprt(String.format("%,10d gxPairList Final  Multi-Filter %,d ", cntGood, (cntF0+cntF1+cntF2)));
		
		grpPairObj.cntG0Filt += cntF0;
		grpPairObj.cntG1Fil += cntF1;
		grpPairObj.cntG2Fil += cntF2;
	}
	catch (Exception e) {ErrorReport.print(e,"filter multi"); bSuccess=false;}
	}
	///////////////////////////////////////////////////////
	protected void clear() {// cleared in GrpPair: gpHitList (and hits), tGeneMap, qGeneMap (and genes) 
		for (HitPair hp : gxPairList) hp.clear();
		gxPairList.clear();
	}

	private void prtHitPairs() { // state that Piles will see
	try {
		if (!Arg.TRACE) return;
		if (grpPairObj.mainObj.fhOutGP==null) return;
		
		grpPairObj.mainObj.fhOutGP.println(">>>>Exon Score; Pair for " +grpPairObj.traceHeader);
		grpPairObj.mainObj.fhOutGP.println("   " + Arg.FLAG_DESC);
			
		grpPairObj.mainObj.fhOutGP.println("After Filter&Split - assign bin order");
			
		for (HitPair ph : gxPairList) 
			grpPairObj.mainObj.fhOutGP.println(ph.toResults("", true)+"\n");
	}
	catch (Exception e) {ErrorReport.print(e,"prt hit pairs"); bSuccess=false;}
	}
	/*****************************************************************
	 * G2: create GenePairList; a hit can go into multiple gene pairs, e.g. hit aligns to 1.a & 1.b -> 2.
	 * use gxPairList directly since it is first to use it
	 *****************************************************************8*/
	private class G2Pairs {
		int cntG2Single=0, cntG2Multi=0, cntG2Filter=0;
		
		private G2Pairs() {
			if (tGeneMap.size()==0 || qGeneMap.size()==0) return;
			Arg.tprt("G2 GrpPairGx ");
			
			runG2(true);	if (!bSuccess) return;
			runG2(false);	if (!bSuccess) return;
			
			grpPairObj.cntG2Fil += cntG2Filter;
			
			if (Arg.PRT_STATS) {
				String msg = String.format("%,10d g2   %,6d multi   %,6d single   %,6d filter single", 
						cntG2Multi+cntG2Single, cntG2Multi, cntG2Single, cntG2Filter);
				Utils.prtIndentMsgFile(plog, 1, msg);
			}
		}
		// create by finding gene-pairs of hits
		private void runG2(boolean isStEQ) {
		try {
			if (tGeneMap.size()==0 || qGeneMap.size()==0) return;
				
			HashMap <String, HitPair> genePairMap = new HashMap <String, HitPair> (); // tgn.geneIdx:qgn.geneIdx -> hits
			
		// Build g2 multi-pairs
			for (Hit ht: qGrpHitList) { 			// Loop by hit assigning hpr to T-Q gene pairs		
				if (ht.isStEQ!=isStEQ) continue;
				
				ht.mkGenePairs(); 					// makes pairs; tgn.geneIdx + ":" + qgn.geneIdx   
				if (ht.gxPair.size()==0) continue;	
				
				for (String idxPair : ht.gxPair) { 	// Loop through pairs
					HitPair nhp;
					if (!genePairMap.containsKey(idxPair)) {
						String [] tok = idxPair.split(":");
						Gene tGene = tGeneMap.get(Arg.getInt(tok[0]));
						Gene qGene = qGeneMap.get(Arg.getInt(tok[1]));
						nhp = new HitPair(Arg.type2, tGene, qGene, isStEQ);
						genePairMap.put(idxPair, nhp);
					}
					else nhp = genePairMap.get(idxPair);
					
					nhp.addHit(ht); 
				}
			} 
			Arg.tprt(String.format("%,10d EQ=%s gene pairs", genePairMap.size(), Arg.charTF(isStEQ)));
			
		// g2 multi list of filtered and singled list of unfiltered
			ArrayList <HitPair> multiList = new ArrayList <HitPair> ();
			ArrayList <HitPair> singleList = new ArrayList <HitPair> ();
			
			for (HitPair hpr : genePairMap.values()) {
				hpr.setScores();				// g2 score multi
				
				if (hpr.hitList.size()==1) {
					singleList.add(hpr);
				}
				else {     // CAS555 if g2, just merge them; long genes were not getting hits merged
					cntG2Multi++;
					multiList.add(hpr);	
				}
				/**
				else if (hpr.bPassCovG2()) {     // check exon and hit coverage
					cntG2Multi++;
					multiList.add(hpr);	
				}
				else { // break into singles
					for (Hit ht : hpr.hitList) { // could check for adjacent...
						HitPair nhp = new HitPair(Arg.type2, hpr.tGene, hpr.qGene, isStEQ);
						singleList.add(nhp);
						nhp.addHit(ht);
						nhp.isMfSpl=true;
					}
				}
				**/
			}
			genePairMap.clear();
			
			sortForAssign(TQ,multiList);		// sort by score
			for (HitPair hpr : multiList) gxPairList.add(hpr);
			multiList.clear();
		
		// g2 singles all filtered here; do not need coverage check as that span of hits along cluster
			sortForAssign(TQ, singleList);
			for (HitPair hpr : singleList) {
				hpr.setScores();  			// g2 score single
				Hit ht = hpr.hitList.get(0);
				
				if (ht.bPassCutoff(T, hpr.tGene) && ht.bPassCutoff(Q, hpr.qGene)) {
					cntG2Single++;
					gxPairList.add(hpr);
				}
				else {
					cntG2Filter++;
					if (Arg.TRACE) {
						hpr.flag = Arg.FILTER;
						gxPairList.add(hpr);
					}
				}
			}
			singleList.clear();
		}
		catch (Exception e) {ErrorReport.print(e,"Compute gene DS"); bSuccess=false;}	
		}	
 	}
	/*****************************************************************
	// G1 create opposite side pseudogenes and match with T/Q genes
	 * Uses genes to gather hits without mate
	 ******************************************************************/
	private class G1Pairs {
		private int tBin=1;			
		private int cntSingle= 0, cntMulti= 0, cntFilter= 0;
		
		
		private G1Pairs() {
			if (qGeneMap.size()!=0) {	// G1: Make T pseudo-genes and use Q genes
				runG1(Q, T, qGeneMap);	if (!bSuccess) return;
			}
			if (tGeneMap.size()!=0) {	// G1: Make Q pseudo-genes and use T genes
				runG1(T, Q, tGeneMap);	if (!bSuccess) return;
			}
		}
		
		private void runG1(int X, int Y, TreeMap <Integer, Gene> xGeneMap) {
			Arg.tprt(String.format("G1%s GrpPairGx ", Arg.side[X]));
			
			runG1(X,Y,xGeneMap, true);
			runG1(X,Y,xGeneMap, false);
			
			grpPairObj.cntG1Fil+=cntFilter;
			
			if (Arg.PRT_STATS  || Arg.TRACE) {
				String msg = String.format("%,10d g1%s  %,6d multi   %,6d single   %,6d filter single", 
					cntMulti+cntSingle, Arg.side[X], cntMulti, cntSingle, cntFilter);
				Utils.prtIndentMsgFile(plog, 1, msg);
			}
			
			cntSingle=cntMulti=cntFilter=0;
		}
		// create by Gene
		private void runG1(int X, int Y, TreeMap <Integer, Gene> xGeneMap, boolean isStEQ) {
		try {
			int type = (X==Q) ? Arg.type1q : Arg.type1t;
			
			HashMap <Integer, HitPair> multiMap = new HashMap <Integer, HitPair> (); 
			ArrayList <HitPair> multiList = new ArrayList <HitPair> ();
			ArrayList <HitPair> singleList = new ArrayList <HitPair> ();
			
		/* g1 Loop by gene	*/
			for (Gene xgene : xGeneMap.values()) { 	
				ArrayList <Hit> gnHitList = xgene.get0BinEither(X, isStEQ); // bin=0 and does not have paired gene
				if (gnHitList==null || gnHitList.size()==0) continue;
				
				Gene tGene = (X==T) ? xgene : null;
				Gene qGene = (X==Q) ? xgene : null;
				int glen = (tGene==null) ? qGene.gLen : tGene.gLen;
				
				Hit.sortBySignByX(Y, gnHitList); // SORT: assume X is proper distance, check Y
				
				// g1 Loop gene hits; create multi pairs
				for (int r1=0; r1<gnHitList.size()-1; r1++) {	
					Hit ht1 = gnHitList.get(r1);
					
					for (int r2=r1+1; r2<gnHitList.size(); r2++) {
						Hit ht2 = gnHitList.get(r2);
						if (ht2.bin>0) continue;
	
						int yDiff = Math.abs(ht2.start[Y]-ht1.end[Y]);
						if (yDiff>glen) continue; // CAS555 changed from intronLen
						
						HitPair nhp;
						if (ht1.bin==0) {					
							nhp = new HitPair(type, tGene, qGene, isStEQ);
							multiMap.put(tBin, nhp);
							ht1.bin = tBin++;
							nhp.addHit(ht1);
							cntMulti++;
						}
						else nhp = multiMap.get(ht1.bin);
								
						ht2.bin = ht1.bin;
						nhp.addHit(ht2);
					}
				}	
				// g1 Loop gene hits: create single pairs
				for (Hit ht : gnHitList) { 
					if (ht.bin==0) {
						HitPair hpr = new HitPair(type, tGene, qGene, isStEQ);
						hpr.addHit(ht);
						singleList.add(hpr);
					}
					else ht.bin=0;
				}
				gnHitList.clear();
			
				// g1 finish multi - add to single pairs
				for (HitPair hpr : multiMap.values()) { // Loop pairMap to transfer to singleList and multiList
					hpr.setScores();	 	//  g1 score multi here for next check
					if (hpr.bPassCovG1()) { // only happen for X side since Y is constrained
						cntMulti++;
						multiList.add(hpr);
					}
					else {
						for (Hit ht : hpr.hitList) { // creates singles
							HitPair nhp = new HitPair(type, hpr.tGene, hpr.qGene, isStEQ);
							nhp.addHit(ht);
							nhp.isMfSpl=true;
							singleList.add(nhp);
						}
					}
				}
				multiMap.clear();
			} // end loop1 through genes
			
		/* finish */
			// Multi: Do not filter before split - add to main list 
			sortForAssign(X, multiList);
			for (HitPair hpr : multiList) gxPairList.add(hpr); 
			multiList.clear();
			
			// Singles: filter - add to main list - scored in main loop
			for (HitPair hpr : singleList) hpr.setScores();
			sortForAssign(X, singleList);
			
			for (HitPair hpr : singleList) {
				Hit ht = hpr.hitList.get(0);
				
				//boolean bPassFil = ht.bPassCutoff(T, hpr.tGene) && ht.bPassCutoff(Q, hpr.qGene);
				if (ht.bPassFilter()) { // intergenic is stricter; this is done for subhit=1 only
					cntSingle++;
					gxPairList.add(hpr);
				}
				else {
					cntFilter++;
					if (Arg.TRACE) {
						hpr.flag = Arg.FILTER;
						gxPairList.add(hpr);
					}
				}
			}
			singleList.clear();
		}
		catch (Exception e) {ErrorReport.print(e,"Compute G1 clusters"); bSuccess=false;}	
		}	
	}
	/***************************************************************
	 * G0: Create pairs for G0, where both sides are bound by intron length
	 */
	private class G0Pairs {
		int cntMulti=0, cntSingle=0, cntFilter=0;	
		
		private G0Pairs() {
			Arg.tprt("G0 use Intron " + Arg.useIntronLen);
		
			for (int eq=0; eq<2; eq++) {
				runG0(eq==0); 		if (!bSuccess) return;
			}
			
			grpPairObj.cntG0Filt+=cntFilter;
			if (Arg.PRT_STATS) {
				String msg = String.format("%,10d g0   %,6d multi   %,6d single   %,6d filter single", 
						cntMulti+cntSingle, cntMulti, cntSingle, cntFilter);
				Utils.prtIndentMsgFile(plog, 1, msg);
			}	
		}
		/* create by finding runs of hits with intron length */
		private void runG0(boolean isStEQ) {
		try {
			ArrayList <Hit> hitList = new ArrayList <Hit> ();	// Find isStEQ hits with no genes
			for (Hit ht : qGrpHitList) {
				if (ht.isStEQ==isStEQ && ht.bNoGene()) hitList.add(ht); // remains sorted by Q
			}
			Arg.tprt(hitList.size(), "EQ-" + Arg.charTF(isStEQ) + " with no genes");
	
			int tBin=1;										// Loop thru g0 hits for multi-hits; bin is key below
			HashMap <Integer, HitPair> pairMap = new HashMap <Integer, HitPair> ();
			
			for (int r=0; r<hitList.size()-1; r++) {			
				Hit ht1 = hitList.get(r);
				
				for (int r1=r+1; r1<hitList.size(); r1++) {
					Hit ht2 = hitList.get(r1);
					if (ht2.bin>0) continue;

					int qDiff = ht2.start[Q]-ht1.end[Q];
					if (qDiff>Arg.useIntronLen) break;
					
					int tDiff = Arg.getDiff(T, isStEQ, ht1, ht2);
					
					if (tDiff<Arg.useIntronLen) {
						HitPair hpr;
						if (ht1.bin==0) {
							hpr = new HitPair(Arg.type0, null, null, isStEQ);
							pairMap.put(tBin, hpr);
							
							hpr.addHit(ht1);
							ht1.bin = tBin++;
							cntMulti++;
						}
						else hpr = pairMap.get(ht1.bin);
						
						ht2.bin = ht1.bin;
						hpr.addHit(ht2);
					}
				}
			}
			
			ArrayList <HitPair> pairList = new ArrayList <HitPair> ();// Add good to pairList
			for (HitPair hpr : pairMap.values()) {
				hpr.setScores();			// g0 score multi
				if (hpr.bPassCovG0()) {
					pairList.add(hpr);	
				}
				else { // not likely
					for (Hit ht : hpr.hitList) ht.bin=0;
				}
			}
			pairMap.clear();
			Arg.tprt(pairList.size(),"g0 Mult-hits");
			
			for (Hit ht : hitList) { 					// Create singles & Filter
				if (ht.bin==0) {
					if (ht.bPassFilter()) { 
						HitPair nhp = new HitPair(Arg.type0, null, null, isStEQ);
						nhp.addHit(ht);
						nhp.setScores(); // g0 score single
						pairList.add(nhp);	
						cntSingle++;
					}
					else {
						cntFilter++;
						ht.bFilter = true;
					}
				}
				else ht.bin = 0;	// set back to zero for main assignment
			}
			hitList.clear();
			
			sortForAssign(pairList);				// Add sorted g0PairList to main list
			for (HitPair ph : pairList) gxPairList.add(ph);
			pairList.clear();
		}
		catch (Exception e) {ErrorReport.print(e,"Compute G0 clusters"); bSuccess=false;}	
		}
	} // End class G0	
	
	protected static void sortForAssign(int X, ArrayList <HitPair> gpList) { // GrpPairGx g1T, g1Q, g2TQ
		Collections.sort(gpList, 
			new Comparator<HitPair>() {
				public int compare(HitPair h1, HitPair h2) {
					if (h1.isRst && !h2.isRst) return -1; 
					if (!h1.isRst && h2.isRst) return  1;
					
					if (h1.exonScore[X]>h2.exonScore[X]) return -1; 
					if (h1.exonScore[X]<h2.exonScore[X]) return  1;
					
					if (h1.geneScore[X]>h2.geneScore[X]) return -1;
					if (h1.geneScore[X]<h2.geneScore[X]) return  1;
					
					if (h1.hitScore[X]>h2.hitScore[X]) return -1;
					if (h1.hitScore[X]<h2.hitScore[X]) return  1;
					
					if (h1.xMaxCov<h2.xMaxCov) return -1; 	// want shorter to be Major
					if (h1.xMaxCov>h2.xMaxCov) return  1; 
					
					if (h1.hitList.size()>h2.hitList.size()) return -1; 
					if (h1.hitList.size()<h2.hitList.size()) return 1;
					
					return 0;	
				}
			}
		);
	}
	protected static void sortForAssign(ArrayList <HitPair> gpList) { // GrpPairGx g0
		Collections.sort(gpList, 
			new Comparator<HitPair>() {
				public int compare(HitPair h1, HitPair h2) {
					int X = TQ;
					if (h1.hitScore[X]>h2.hitScore[X]) return -1;
					if (h1.hitScore[X]<h2.hitScore[X]) return  1;
					
					if (h1.xMaxCov>h2.xMaxCov) return -1; 	// want longer to be Major
					if (h1.xMaxCov<h2.xMaxCov) return  1; 
					
					if (h1.hitList.size()>h2.hitList.size()) return -1; 
					if (h1.hitList.size()<h2.hitList.size()) return 1;
					
					return 0;	
				}
			}
		);
	}
}
