package backend.anchor2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import util.ErrorReport;

/*************************************************************
 * Set of joined hits representing g2, g1, or g0
 * CAS560 moved coverage filters to Arg
 */
public class HitPair {
	private static final int T=Arg.T, Q=Arg.Q, TQ=Arg.TQ;
	private static int CNT_HPR=1;
	
	protected int  nHpr = 1; 			 // CAS560 for sorting identical hitpairs of overlapping genes
	protected int  gtype = Arg.typeUnk;  // "Unk","G0", "G1t", "G1q", "G2"
	protected char flag = Arg.UNK;	     // Major, Minor, Filter, Dup, Ign, Pile
	protected int  bin = 0;			     // GrpPairGx set for hpr, then reset in GrpPair
	
	///////
	protected int [] hpStart = {Integer.MAX_VALUE, Integer.MAX_VALUE};
	protected int [] hpEnd   = {0,0};
	
	protected ArrayList <Hit> hitList = new ArrayList <Hit> (); 
	protected int nHits = 0;
	protected boolean isStEQ = true; 	// same strand (T) diff strand (F)
	protected boolean isRst =  true;	// g2 genes correspond to hit strand, g2 are on +/- but hit is +/+
	protected boolean isOrder = true;
	
	protected Gene tGene=null, qGene=null;
	protected double [] pExonHitFrac  = {0.0, 0.0, 0.0};  // % fraction of each exon hit; CAS560 add
	protected double [] pExonHitCov   = {0.0, 0.0, 0.0};  // % exons covered by hits; T,Q,TQ
	protected double [] pGeneHitCov   = {0.0, 0.0, 0.0};  // % gene  covered by hits; T,Q,TQ
	protected double [] pGeneHitOlap  = {0.0, 0.0, 0.0};  // % hitLen/gLen; T,Q,TQ -- G0 for non-gene, sum of hits
	protected double [] pHitGapCov    = {0.0, 0.0, 0.0};  // % hitCov/hitLen;T,Q,TQ
	
	protected int    [] geneIdx       = {0, 0};           // geneIdx 
	protected double lenRatio=0;					      // g2 geneLen/geneLen; g1,g0 hprLen/hprLen
	protected int xSumId=0, xSumSim=0, xMaxCov=0;		  // Saved in DB; xMaxCov is used for filters		
	
	// Cluster
	protected int cHitNum=0; 	    // after all processing including pile; assigned in GrpPair.createCluster
	protected boolean bEE=false, bEI=false, bII=false, bEn=false, bIn=false, bnI=false, bnn=false; // for GrpPairPile filter on piles
	protected boolean bBothGene=false, bOneGene=false, bNoGene=false; // CAS560 changed bEitherGene to bOneGene, add bNoGene
	protected String sign="";							// */-, etc set in setScores
	protected String targetSubHits="", querySubHits=""; // concatenated hits for DB
	protected String htype=Arg.snn;							// EE, EI, etc
	
	// GrpPairPile
	protected int [] pile = {0,0};  // closely overlapping clusters; see GrpPairPile
	
	// Used for trace
	protected String note="";
	
	protected HitPair(int type, Gene tGene, Gene qGene, boolean isEQ) {
		this.gtype = type; 		// see Arg.type; G2, G1t, G1q, G0
		this.tGene = tGene;
		this.qGene = qGene;
		this.geneIdx[T] = (tGene!=null) ? tGene.geneIdx : 0;	
		this.geneIdx[Q] = (qGene!=null) ? qGene.geneIdx : 0;		
		this.isStEQ = isEQ;
	
		bBothGene   = (tGene!=null && qGene!=null);
		bOneGene    = (tGene!=null && qGene==null) || (tGene==null && qGene!=null); 
		bNoGene     = (tGene==null && qGene==null);
		nHpr=CNT_HPR++;
	}
	protected HitPair(HitPair cpHpr) {
		this.gtype = cpHpr.gtype;
		this.tGene = cpHpr.tGene;
		
		this.qGene = cpHpr.qGene;
		this.geneIdx[T] = cpHpr.geneIdx[T];	
		this.geneIdx[Q] = cpHpr.geneIdx[Q];		
		this.isStEQ = cpHpr.isStEQ;
		
		this.bBothGene = cpHpr.bBothGene;
		this.bOneGene  = cpHpr.bOneGene; 
		this.bNoGene   = cpHpr.bNoGene;
		this.nHpr = cpHpr.nHpr;
	}
	protected void addHit(Hit ht) {
		hitList.add(ht);	
		for (int x=0; x<2; x++) {
			if (hpStart[x] > ht.hStart[x]) hpStart[x] = ht.hStart[x];
			if (hpEnd[x]   < ht.hEnd[x])   hpEnd[x] = ht.hEnd[x];
		}
	}
	protected void rmHit(int X, Gene xgene, Gene ygene, int i) {
	try {
		Hit htrm = hitList.get(i);
		
		if (xgene!=null || ygene!=null) htrm.rmGeneFromHit(X, xgene, ygene);
		hitList.remove(i);
		
		nHits = hitList.size();
		if (nHits==0) {
			flag = Arg.FILTER;
			return;
		}
		
		for (int x=0; x<2; x++) {
			hpStart[x]=Integer.MAX_VALUE; 
			hpEnd[x]=0;
			for (Hit ht : hitList) {
				if (hpStart[x] > ht.hStart[x]) hpStart[x] = ht.hStart[x];
				if (hpEnd[x]   < ht.hEnd[x])   hpEnd[x] = ht.hEnd[x];
			}
		}
		setScoresMini();
	}
	catch (Exception e) {ErrorReport.print(e, "rmHit");}
	}
	
	protected void crossRef() {
		//for (Hit ht : hitList) ht.hHprList.add(this);
		if (tGene!=null) tGene.gHprList.add(this);
		if (qGene!=null) qGene.gHprList.add(this);
	}
	protected void crossRefClear() {
		if (tGene!=null) tGene.gHprList.clear();
		if (qGene!=null) qGene.gHprList.clear();
	}
	
	/*******************************************************
	 * Scores: hit, exon, gene, id & len
	 * Everything here is needed for filtering, sorting...; finishSubs has the rest
	 */
	protected void setScores() throws Exception {
	try {
		nHits = hitList.size();
		boolean isG0 = (tGene==null && qGene==null);
		int [] totHprLen = {0,0};
		int [] sumMergeHits = {0,0};
		ArrayList <Hit> mergedHits ;
		
		// TQ p scores
		for (int X=0; X<2; X++) {
			Hit.sortXbyStart(X, hitList); // SORT 
			
			if (X==T) { // only need to check for one side after sort
				for (int i=0; i<nHits-1 && isOrder; i++) { 
					Hit ht1 = hitList.get(i);
					for (int j=i+1; j<nHits; j++) 
						isOrder = (isStEQ) ? (ht1.hStart[Q] < hitList.get(j).hStart[Q]) :
											 (ht1.hStart[Q] > hitList.get(j).hStart[Q]);
				}
			}
			
			mergedHits = calcMergeHits(X);
		
			totHprLen[X] = hpEnd[X]-hpStart[X]+1;
			for (Hit ht : mergedHits) 
				sumMergeHits[X] += (ht.hEnd[X]-ht.hStart[X]+1);
			pHitGapCov[X] = ((double)sumMergeHits[X]/(double)totHprLen[X]) * 100.0; // %coverage
			
			Gene xGene = (X==T) ? tGene : qGene;
			if (xGene!=null) {
				int hs = mergedHits.get(0).hStart[X];
				int he = mergedHits.get(mergedHits.size()-1).hEnd[X];
				pGeneHitOlap[X] = ((double) Arg.pOlapOnly(hs, he, xGene.gStart, xGene.gEnd)/ (double)xGene.gLen)*100.0;
				
				double [] scores = xGene.scoreExonsGene(mergedHits); 
				pExonHitFrac[X]	 = scores[0]; // % fraction cover of each exon; CAS560 add
				pExonHitCov[X]   = scores[1]; // % total summed exons covered in hits - no regard for individual exons
				pGeneHitCov[X]   = scores[2]; // % hit/gene; was percent hit coverage of genes, but bad for long introns
			} 
			else if (isG0) pGeneHitOlap[X] = sumMergeHits[X];
		} // end X loop
		
		// Sum values for each hit
		double [] sumHitSim = {0,0};
		double [] sumHitId  = {0,0}; 
		int [] sumHitLen    = {0,0};
	
		for (int X=0; X<2; X++) {
			double sumId=0, sumSim=0;
			for (Hit ht : hitList) {
				sumSim       += ht.sim * ht.hLen[X]; 
				sumId        += ht.id  * ht.hLen[X];
				sumHitLen[X] += ht.hLen[X];
			}
			sumHitId[X]   = (double)sumId /(double)sumHitLen[X];		
			sumHitSim[X]  = (double)sumSim/(double)sumHitLen[X];
		}
		// Saved in DB - only xMaxCov is used in Piles for threshold
		int ix = (sumMergeHits[T] > sumMergeHits[Q]) ? T : Q;
		xMaxCov = sumMergeHits[ix];
		xSumId  = (int) Math.round(sumHitId[ix]);		
		xSumSim = (int) Math.round(sumHitSim[ix]);
		
		setSign(); // CAS548 make sign agree with gene strands; 

		if (tGene!=null && qGene!=null) { // nHitOlap can be the same for overlapping genes; so use length
			if (tGene.gLen < qGene.gLen) lenRatio = (double)tGene.gLen/(double)qGene.gLen;
			else                         lenRatio = (double)qGene.gLen/(double)tGene.gLen;
		}
		else {
			if (totHprLen[0] < totHprLen[1]) lenRatio = (double)totHprLen[0]/(double)totHprLen[1];
			else                             lenRatio = (double)totHprLen[1]/(double)totHprLen[0];
		}
			
		// TQ
		pExonHitFrac[TQ]= pExonHitFrac[T]+ pExonHitFrac[Q];
		pExonHitCov[TQ] = pExonHitCov[T] + pExonHitCov[Q];
		pGeneHitCov[TQ] = pGeneHitCov[T] + pGeneHitCov[Q];
		pGeneHitOlap[TQ]= pGeneHitOlap[T]+ pGeneHitOlap[Q];
		pHitGapCov[TQ]  = pHitGapCov[T]  + pHitGapCov[Q];
		
		if (tGene==null && qGene==null) {
			htype="nn";
			bnn=true;
			return;
		}
		double p = Arg.minEE;
		if (pExonHitCov[T]>p && pExonHitCov[Q]>p)   {htype=Arg.sEE; bEE=true;}
		else if (pExonHitCov[T]>p && geneIdx[Q]>0)  {htype=Arg.sIE; bEI=true;}
		else if (pExonHitCov[Q]>p && geneIdx[T]>0)  {htype=Arg.sEI; bEI=true;}
		else if (pExonHitCov[T]>p && geneIdx[Q]==0) {htype=Arg.snE; bEn=true;}
		else if (pExonHitCov[Q]>p && geneIdx[T]==0) {htype=Arg.sEn; bEn=true;}
		else if (geneIdx[T]>0 && geneIdx[Q]>0)		{htype=Arg.sII; bII=true;}
		else if (geneIdx[T]>0) 						{htype=Arg.snI; bIn=true;}
		else if (geneIdx[Q]>0) 						{htype=Arg.sIn; bnI=true;}
	}
	catch (Exception e) {ErrorReport.print(e, "Setting scores"); throw e;}
	}

	protected void setSign() {
	try {
		sign = hitList.get(0).sign;
		boolean isEQ = Arg.isEqual(sign); 
		if (gtype==Arg.type0 || gtype==Arg.type2) { // get best order (g2 used for printout)
			int same=0,diff=0;
			for (Hit ht :hitList) {
				if (ht.sign.equals(sign)) same++;
				else diff++;
			}
			if (diff>same) sign = sign.charAt(2) + "/" + sign.charAt(0);
		}
		// sign - checked in GrpPairGx for consistency
		if (tGene!=null && qGene!=null) {
			boolean isGnEQ = tGene.strand.equals(qGene.strand);
			isRst = (isGnEQ==isEQ);			
			sign = (isRst) ? qGene.strand + "/" + tGene.strand : sign;
		}
		else if (qGene!=null) {
			if (isEQ) sign = qGene.strand + "/" + qGene.strand;
			else if (qGene.strand.equals("+")) sign = "+/-";
			else sign =  "-/+";
		}
		else if (tGene!=null) {
			if (isEQ) sign = tGene.strand + "/" + tGene.strand;
			else if (tGene.strand.equals("+")) sign = "-/+";
			else sign =  "+/-";
		}
	}
	catch (Exception e) {ErrorReport.print(e, "Setting sign"); throw e;}
	}
	// For G1 because do not need all scores yet
	protected void setScoresMini() {
		nHits = hitList.size();
		int [] totHPRlen = {0,0};
		totHPRlen[Q] = hpEnd[Q]-hpStart[Q]+1;
		totHPRlen[T] = hpEnd[T]-hpStart[T]+1;
		if (totHPRlen[0] < totHPRlen[1]) lenRatio = (double)totHPRlen[0]/(double)totHPRlen[1];
		else                             lenRatio = (double)totHPRlen[1]/(double)totHPRlen[0];
	}
	/* For setScores: The hits overlap, so merge the overlapping ones. 
	 * There is a similar method in mapper.HitData and closeup.SeqHitsbut with different data structures  */
	protected ArrayList<Hit> calcMergeHits(int X) { 
	try {
		if (nHits==1) return hitList;
		
		ArrayList <Hit> subSet = new ArrayList <Hit> ();
		
		for (Hit ht : hitList) { // sorted on X
			boolean found=false;
			
			for (Hit sh : subSet) {
				int olap = Arg.pGap_nOlap(sh.hStart[X], sh.hEnd[X], ht.hStart[X], ht.hEnd[X]);
				if (olap>0) continue; // gap
				
				found=true;
				if (ht.hEnd[X]   > sh.hEnd[X])   sh.hEnd[X]   = ht.hEnd[X];
				if (ht.hStart[X] < sh.hStart[X]) sh.hStart[X] = ht.hStart[X];
				
				break;
			}
			if (!found) {
				Hit sh = new Hit(X, ht.hStart[X], ht.hEnd[X]);
				subSet.add(sh);
			}
		}
		return subSet;
	}
	catch (Exception e) {ErrorReport.print(e, "merge hits"); return null;}	
	}
	///////////////////////////////////////////////////////////////
	// All processing done at this point except Piles and final filter; values to save to DB
	protected void setSubs() throws Exception {
	try {
		targetSubHits=querySubHits="";
		
		Hit.sortXbyStart(Q, hitList); // This is the order anchor1 does it - must be the same
		for (Hit ht : hitList) {
			targetSubHits += ht.hStart[T] + ":" + ht.hEnd[T] + ",";
			querySubHits  += ht.hStart[Q] + ":" + ht.hEnd[Q] + ",";
		}
		targetSubHits.substring(0, targetSubHits.length()-1);
		querySubHits.substring(0, querySubHits.length()-1);
	}
	catch (Exception e) {ErrorReport.print(e, "finish subs"); }
	}
	protected int getGeneOverlap() {
		if (geneIdx[T]>0 && geneIdx[Q]>0) return 2;
		if (geneIdx[T]>0 || geneIdx[Q]>0) return 1;
		return 0;
	}
	protected double getGeneCov(int X) {
		if (X==Arg.T && tGene!=null) {
			int cov = Arg.pOlapOnly(hpStart[T], hpEnd[T], tGene.gStart, tGene.gEnd);
			return (double) cov/(double)tGene.gLen;
		}
		if (X==Arg.Q && qGene!=null) {
			int cov = Arg.pOlapOnly(hpStart[Q], hpEnd[Q], tGene.gStart, tGene.gEnd);
			return (double) cov/(double)qGene.gLen;
		}
		return 0.0;
	}
	/***************************************************************************/
	protected static void sortByXstart(int X, ArrayList<HitPair> hpr) { // GrpPair.saveClusterHits, GrpPairPile.identifyPiles
		Collections.sort(hpr, 
			new Comparator<HitPair>() {
				public int compare(HitPair h1, HitPair h2) {
					if (h1.hpStart[X] < h2.hpStart[X]) return -1;
					if (h1.hpStart[X] > h2.hpStart[X]) return 1;
					if (h1.hpEnd[X]   > h2.hpEnd[X]) return -1;
					if (h1.hpEnd[X]   < h2.hpEnd[X]) return 1;
					return 0;
				}
			}
		);
	}
	protected void clear() {
		hitList.clear();
	}
	// symap -wsp outputs this; documented in SystemHelp under Clusters; see GrpPairGx.runG2.processWS
	protected String toWSResults(String chrPair) { 
		String [] chr = chrPair.split(",");
		String eq = isStEQ ? " =" : "!=";
		eq = qGene.strand + "/" + tGene.strand + "  " + eq;
	
		String locq = String.format("[%5.1f  %5.1f  %,7dbp]", pExonHitCov[Q], pGeneHitCov[Q], (hpEnd[Q]-hpStart[Q]+1));
		String loct = String.format("[%5.1f  %5.1f  %,7dbp]", pExonHitCov[T], pGeneHitCov[T], (hpEnd[T]-hpStart[T]+1));
		
		return String.format("%-5d  %-10s %-10s   %s   %s   [%s %s] %s %s", 
				 nHits, qGene.geneTag, tGene.geneTag, locq, loct,  htype, eq, chr[Q], chr[T]);
	}
	/********* Trace output *************************************************/
	protected String toResultsGene() {
		String n1 = (tGene!=null) ? tGene.geneTag : "None"; n1 = String.format("T#%-6s", n1);
		String n2 = (qGene!=null) ? qGene.geneTag : "None"; n2 = String.format("Q#%-6s", n2);
		
		String cb = (cHitNum==0) ?  String.format("[b%-5d]", bin) : String.format("[CL%-5d b%-5d]", cHitNum, bin);
		
		String mb = String.format("%s %s %s %s", flag, cb, n1, n2);
		String me = String.format("[E %5.1f %5.1f | %5.1f %5.1f]", pExonHitCov[T], pExonHitCov[Q], pExonHitFrac[T], pExonHitFrac[Q]);
		String mg = String.format("[G %5.1f %5.1f | %5.1f %5.1f]", pGeneHitCov[T], pGeneHitCov[Q], pGeneHitOlap[T], pGeneHitOlap[Q]);
		String mh = String.format("[H %5.1f %5.1f]", pHitGapCov[T], pHitGapCov[Q]);
		String m4 = String.format("[M %,6d %,6d %,6d %.2f]", xMaxCov, Arg.hprLen(T,this),  Arg.hprLen(Q,this), lenRatio);
		
		String eq = (isStEQ) ? "EQ" : "NE";
		String ws =  (!isRst && tGene!=null && qGene!=null)  ? "WS" +sign : " " + sign;   // hit sign does not agree with gene
		String m5 = String.format("%s #%3d %s", eq, nHits, ws);

		return String.format("%s %s %s %s %s %s", mb, me, mg, mh, m4, m5);
	}
	protected String toResults(String chrs, boolean bHits) { 	                                 
		String pil = (pile[T]>0 || pile[Q]>0) ? String.format("[P%3d P%3d]", pile[T], pile[Q]) : ""; 
		String id = String.format("[id %2d]", xSumId);
		String o  = (!isOrder) ? "DO" : "  ";
	
		String msg;
		if (tGene==null && qGene==null) {
			String cb = (cHitNum==0) ?  String.format("[b%-5d]", bin) : String.format("[CL%-5d b%-5d]", cHitNum, bin);
			String ms = String.format("[S %,6d %,6d]", (int) hpStart[T], (int)hpStart[Q]);
			String mo = String.format("[C %,6d %,6d]", (int) pGeneHitOlap[T], (int)pGeneHitOlap[Q]);
			String mh = String.format("[H %5.1f %5.1f]", pHitGapCov[T], pHitGapCov[Q]);
			String ml = String.format("[%.2f]", lenRatio);
			String m3 = String.format("%s #%d %s %-12s %s %s", pil, nHits, o, note, id, chrs);
			msg = String.format(">%-3s:%c %s TQ%d %s %s %s %s %s", Arg.strType[gtype], flag, cb, nHpr, ms, mo, mh, ml, m3);
		}
		else {
			String m1 = String.format(">%s:", htype);
			String m3 = String.format("%s %s %-12s %s %s", pil, o, note, id, chrs);
			msg = String.format("%s%s %s", m1, toResultsGene(),  m3);
		}
		
		if (bHits) {
			Hit last=null;
			for (Hit ht: hitList) {
				String m="";
				if (last!=null) {
					if (ht.bin!=last.bin && last.bin!=0 && ht.bin!=0) m=" DIFF";	
					if (ht.isStEQ!=last.isStEQ) m+="Q";
				}
				msg += "\n" + ht.toDiff(last) + m;
				last = ht;
			}
		}
		return msg;
	}
	protected String toPileResults() {
		String loc = String.format("[T %,10d %,10d][Q %,10d %,10d]", hpStart[T],hpEnd[T], hpStart[Q], hpEnd[Q]);
		String s =  (note.contains("Split")) ? "BinSpl " : "";  // created from bin split
		String n1 = (tGene!=null) ? tGene.geneTag : "None";
		String n2 = (qGene!=null) ? qGene.geneTag : "None";
		
		return String.format(
			">%-3s:%c %s %,7d T#%-6s Q#%-6s [P %5d %5d] %s #%d %s %s", 
			Arg.strType[gtype], flag, htype, xMaxCov, n1, n2, pile[T],pile[Q],  loc, nHits, sign, s);
	}
}