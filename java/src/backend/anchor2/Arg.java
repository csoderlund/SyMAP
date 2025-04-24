package backend.anchor2;

import backend.Constants;
import symap.Globals;

/***************************************************************
 * Constants and input Parameters for backend.anchor2 only
 * CAS560 some filters were here, moved the rest of filters to here as static methods from HitPair and Hit
 *        set some values from input gene annotations; changed some defaults
 *        These rules were manually trained by observing results and fitting to what looks good
 */
public class Arg {
	// Command line; all default false
	protected static boolean VB = (Globals.TRACE || Constants.VERBOSE);
	protected static boolean TRACE = Globals.TRACE; 						// for developers
	protected static boolean WRONG_STRAND_PRT = Constants.WRONG_STRAND_PRT; // Print wrong strand
	
	// Useful constants
	protected static char UNK='?', MAJOR='+', MINOR='*', FILTER='F', IGN='I', PILE='P'; // HitPair flags; MAJOR and MINOR saved
	protected static String FLAG_DESC = "? unknown; + major; * minor; F filter; I Ignore; P pile"; 
		
	protected static final int NONE = -1;
	protected static final int T=0, Q=1, TQ=2;
	protected static final String [] side = {"T", "Q", "TQ"};
	
	protected static final int typeUnk=0, type0=1, type1t=2, type1q=3, type2=4;
	protected static final String [] strType = {"Unk","G0", "G1t", "G1q", "G2"};
	protected static final String sEE="EE", sEI="EI", sIE="IE", sII="II", sEn="En", snE="nE", sIn="In", snI="nI", snn="nn"; // HitPair.htype
	
	// CAS560 defaults from AI Chat Bot; used in setLenFromGenes
	protected static final int plantIntronLen =  500, plantGeneLen =  3500;  // plants (introns 150-500, genes 2k-5k)
	protected static final int mamIntronLen   =  4500, mamGeneLen   = 25000; // mammalians (introns 3k-5.4k, genes 20k-30k)
	
	//  G2 RBH, !RBH - the base values for the parameter scales
	private static double pExonScale=1.0, pGeneScale=1.0, pG0LenScale=1.0, pLenScale=1.0;
	private static final double [] fLowExonCov2  = {20,25}; // exon coverage and percent fraction of each exon
	private static final double [] fLowGeneCov2  = {25,35, 50}; // hit-gene overlap/genL
	private static final int    [] fMinLen    = {300, 1000, 5000, 10000}; // 300 is documented as smallest non-perfect 
	
	protected static final double  dLenRatio=0.5;				  // g1 and g0;
	private static final double [] dMinCov = {60.0, 80.0, 97.0};  // used directly with iPpMinMaxCov in g2 and g1 for gene&exon
	
	/***** Constants/Heuristics in code ****/
	// Set in setFromParams using above final values and parameter scale values
	private static double [] dPpLowExonCov2={0,0}, dPpLowGeneCov2={0,0,0}; // scale * fLow...
	private static int   []  iPpMinLen= {0,0,0,0}; 						 // fMinLen * lenScale; usually for xMaxLen, but g1 exon
	private static int  iPpMinG0Len= 1000; 
	private static double dMinG0Cov = 40.0;
	
	// Set from gene models: see setLenFromGenes
	protected static int iGmIntronLen2x = (plantIntronLen+mamIntronLen)/2;           // G0 max intron; G1 special case; default for no anno 
	protected static int iGmIntronLen4x = (int) ((plantIntronLen+mamIntronLen)/1.5); // G1 max intron
	protected static int iGmIntronLenRm = 1000;
	protected static int iGmGeneLen =     (plantGeneLen+mamGeneLen)/2;  	         // not used
	protected static int [] iGmLowHitGapCov2 = {20,40,60}; // set in setLen; hitScore = (summed-merged-hits/clustered-hit-length)%

	// Other
	protected static final double minEE=0.001; // HitPair: ExonScore great enough to be called exon, e.g. EE type
	
	// GrpPairPile 
	protected static boolean pEE=true, pEI=true, pEn=false, pII=false, pIn=false;	// piles to keep; set directly in AnchorMain
	protected static int topN = 2;													// set from AnchorMain2 from Pair Parameters
	protected static final double bigOlap = 0.4; 
	/*******************************************
	 * Functions
	 ******************************************/
	protected static void setVB() {VB = Globals.TRACE || Constants.VERBOSE;} 
	
	/* Set from AnchorMain2 Parameters */
	protected static String setFromParams(double exonScale, double geneScale,  double g0Scale, double lenScale) {
		pExonScale = exonScale; pGeneScale = geneScale; pG0LenScale = g0Scale; pLenScale = lenScale;
		
		for (int i=0; i<fLowExonCov2.length; i++) dPpLowExonCov2[i] = fLowExonCov2[i]  * pExonScale; 
		
		for (int i=0; i<fLowGeneCov2.length; i++) dPpLowGeneCov2[i] = fLowGeneCov2[i]  * pGeneScale;
		
		for (int i=0; i<fMinLen.length; i++)   iPpMinLen[i]   = (int) (fMinLen[i] * pLenScale);
		
		iPpMinG0Len = (int) (fMinLen[1] * pG0LenScale);
		
		String m2 = String.format("Cutoffs: Exon %.1f %.1f  Gene %.1f %.1f  MinLen %,d %,d %,d    ",
			dPpLowExonCov2[0], dPpLowExonCov2[1], dPpLowGeneCov2[0], dPpLowGeneCov2[1], iPpMinLen[0], iPpMinLen[1], iPpMinLen[2]);
		String m3 = String.format("Scales: exon %.1f  gene %.1f  Len %.1f  G0 %.1f ", exonScale, geneScale, lenScale, g0Scale);	
		String m4 = String.format("Static: minCov %.0f %.0f %.0f  LenRatio %.1f ", 
				dMinCov[0],dMinCov[1], dMinCov[2], dLenRatio);
		
		return "#" + m2+m3 + "\n#" + m4 + "\n"; // remark for trace files and stdout
	}
	/* Set from AnchorMain2 loadGenes from run() */
	protected static String setLenFromGenes(int [] intronAvg, int [] geneAvg, double [] exonGeneCov) { 
		iGmIntronLen4x = (intronAvg[T]+intronAvg[Q]) * 2 ; // mammals 2x; plants average <500, but often get >1500
		if (iGmIntronLen4x<plantIntronLen*3) iGmIntronLen4x=plantIntronLen*3;
		if (iGmIntronLen4x>15000) iGmIntronLen4x = 15000; 
		
		iGmIntronLen2x = intronAvg[T]+intronAvg[Q]; 			 			
		if (iGmIntronLen2x<plantIntronLen*2) iGmIntronLen2x = plantIntronLen*2; // if too small, will not cluster much
		if (iGmIntronLen2x>mamIntronLen*2) iGmIntronLen2x = mamIntronLen*2;     // rather have multiple clusters than too long gaps
		
		iGmIntronLenRm = (int) (iGmIntronLen2x * 0.5);
		if (iGmIntronLenRm<1000) iGmIntronLenRm = 1000; // even for plants, dangling <1000 is okay
		if (iGmIntronLenRm>2500) iGmIntronLenRm = 2500; // mammal 
		
		iGmLowHitGapCov2[0] = (int) Math.round((exonGeneCov[T]+exonGeneCov[Q])/2);    // 7% for hsa/pan; 60% cabb/arab
		if (iGmLowHitGapCov2[0]>20) iGmLowHitGapCov2[0]=20;
		
		iGmLowHitGapCov2[1] = iGmLowHitGapCov2[0]*2;
		iGmLowHitGapCov2[2] = iGmLowHitGapCov2[0]*3; // for non-gene
		
		iGmGeneLen = Math.min(geneAvg[T],geneAvg[Q]);
		
		String m1 = String.format("Gene models: intron 4x %,d  2x %,d  rm %,d    hitGapCov %,d %,d %,d from gene-exon cover", 
		iGmIntronLen4x, iGmIntronLen2x, iGmIntronLenRm, iGmLowHitGapCov2[0], iGmLowHitGapCov2[1],  iGmLowHitGapCov2[2]);
				
		return "#" + m1+ "\n"; // remark for trace files and stdout 
	}
	
	/***** functions for scoring *****/
	/* G2 */
	
	/* G2 de-weed: This weeds out low scores, where there are many in hsa-pan */
	protected static boolean bG2isOkay(HitPair hpr) {
		double e=0.1, g=2.0;
		if (hpr.pExonHitCov[T]<e && hpr.pExonHitFrac[T]<e &&
			hpr.pExonHitCov[Q]<e && hpr.pExonHitFrac[Q]<e &&
			hpr.pGeneHitCov[T]<g && hpr.pGeneHitOlap[Q]<g &&
			hpr.pGeneHitCov[Q]<g && hpr.pGeneHitOlap[Q]<g) return false;
		
		if (hpr.htype.equals("II") && hpr.xMaxCov<iPpMinLen[1]) return false; // 1000
		if (hpr.htype.equals("nn")) return false;
		
		return true;
	}
	/* G2 filter */
	protected static boolean bG2passCov(int idx, HitPair hpr) { // idx=0 candidate RBH, idx=1 good selected
		if (!hpr.isRst) return false;
		
	// Perfect
		if (bGxisPerfect(idx, T, hpr) || bGxisPerfect(idx, Q, hpr)) return true;
		
	// Scaled Min Length tests; require 60% or 80% * scale pass 
		// Accept
		boolean bMinQ = bGxPcov(Q, hpr), bMinT = bGxPcov(T, hpr);
		
		if (hpr.xMaxCov>iPpMinLen[1] && (bMinQ && bMinT)) return bRet(hpr, " Plen1", true); //1000
		if (hpr.xMaxCov>iPpMinLen[2] && (bMinQ || bMinT)) return bRet(hpr, " Plen2", true); //5000
		if (hpr.xMaxCov>iPpMinLen[3] && hpr.lenRatio>dLenRatio && 
				hpr.pExonHitFrac[T]>dMinCov[0] && hpr.pExonHitFrac[Q]>dMinCov[0]) return bRet(hpr, " Plen3", true); // 10000
		//Reject
		if (hpr.xMaxCov<iPpMinLen[0]) return bRet(hpr, " Fmin1", false); // 300 Required test if not very good
				
	// If both have major and not so good, fail; else use cnt to determine if neither has
		int cntT=0, cntQ=0;
		for (HitPair thp : hpr.tGene.gHprList) if (thp.flag==MAJOR) cntT++;
		for (HitPair qhp : hpr.qGene.gHprList) if (qhp.flag==MAJOR) cntQ++;
		if (cntT>0 && cntQ>0) {
			if ((!bMinQ && !bMinT) || !hpr.htype.equals(sEE))  return bRet(hpr, " MAJ"+(cntT+cntQ), false);	
		}

	// Scaled exon, gene, hit tests -- these are fairly loose to try to get hits to genes that do not have one
		if (idx==1 && cntT==0 && cntQ==0) idx=0; // neither has major, so be lenient
		
		cntT=cntQ=0;
		String note=" ";
		
		double eOlap =  dPpLowExonCov2[idx];
		if (hpr.pExonHitCov[T] > eOlap || hpr.pExonHitFrac[T] > eOlap) cntT++; else note += " Et";
		if (hpr.pExonHitCov[Q] > eOlap || hpr.pExonHitFrac[Q] > eOlap) cntQ++; else note += " Eq";
		
		// Capture EI or II with these 2; test Gene Olap so accept long introns; 
		double gOlap = (!hpr.htype.equals(sEE)) ?  (dPpLowGeneCov2[idx]*2.0) : dPpLowGeneCov2[idx];
		if (hpr.pGeneHitCov[T] > gOlap || hpr.pGeneHitOlap[T] > gOlap) cntT++;  else note += " Gt"; 
		if (hpr.pGeneHitCov[Q] > gOlap || hpr.pGeneHitOlap[Q] > gOlap) cntQ++;  else note += " Gq"; 
		
		if (hpr.nHits > 1) {
			if (hpr.pHitGapCov[T] > iGmLowHitGapCov2[idx]) cntT++; else note += " Ht";
			if (hpr.pHitGapCov[Q] > iGmLowHitGapCov2[idx]) cntQ++; else note += " Hq";
		}
		if (cntT==0 || cntQ==0) return bRet(hpr, " Ff0" + note, false); 
		if (cntT==1 && cntQ==1) return bRet(hpr, " Ff1" + note, false); 
		 
		return bRet(hpr, " Ps" + note, true);
	}
	
	/* G1 - this rejects many ones that could be in a block, but it tries to just pass the 'interesting' ones;
	 *      otherwise, there are way too many due to the unconstrained non-gene side */
	protected static boolean bG1passCov(int X, HitPair hpr) { 
		if (hpr.flag==FILTER || hpr.nHits==0) return false;
		int Y = (hpr.tGene!=null) ? Q : T; 
		
		// 1. Reject clearly bad: (1) unequal hit coverage (2) low exon/gene coverage; E<25, G<1; can hit one tiny exon, so use frac
		if (hpr.pHitGapCov[Y]*3 < hpr.pHitGapCov[X] || hpr.lenRatio<(dLenRatio/2.0)) return bRet(hpr, " Fgp", false);
		
		if (hpr.pExonHitFrac[X]< dPpLowExonCov2[1] && 
			hpr.pGeneHitCov[X]<1.0  && hpr.pGeneHitOlap[X]<1.0) return bRet(hpr, " Fgn", false);  	
		
		// 2. Accept perfect and near perfect
		if (bGxisPerfect(1, X, hpr)) return true;					   // accept perfect
		
		if (hpr.xMaxCov>iPpMinLen[2] && bGxPcov(X, hpr)) return true;  // accept 5000*scale and Good
		
		// 3. Reject intron only: (1) <3000, (2) GeneCov&Olap<50*scale (3) Cov X&Y < 60  (4) lenRatio<0.5
		if (!hpr.htype.contains("E")) { 
			if (hpr.xMaxCov<iPpMinLen[1]*3) return bRet(hpr, " Fi1", false);
			
			if (hpr.pGeneHitCov[X] < dPpLowGeneCov2[2] && hpr.pGeneHitOlap[X] < dPpLowGeneCov2[2]) return bRet(hpr, " Fi2",  false);
			
			if (hpr.pHitGapCov[X] < dMinCov[0] && hpr.pHitGapCov[Y] < dMinCov[0]) return bRet(hpr, " Fi3",  false);
			
			if (hpr.lenRatio<dLenRatio) return bRet(hpr, " Fi4",  false);
			
			return true;
		}
		
		// 4. Reject length
		if (hpr.xMaxCov<iPpMinLen[0]) return bRet(hpr, " Fl", false);  // 300*scale; Required if not perfect
		
		double [] re = {40.0, 15.0, 10.0, 5.0, 0.0};					// shorter length; more stringent exon fraction
		double [] rg = {55.0, 45.0, 35.0, 25.0,0.0};					// shorter length; more stringent gene overlap
		for (int i=0; i<re.length && i<iPpMinLen.length; i++) {
			if (hpr.xMaxCov < iPpMinLen[1]*(i+1)) { 				         //     1000, 2000, 3000, 4000, 5000
				double ex = dPpLowExonCov2[0]+re[i];
				if (hpr.pExonHitFrac[X] < ex && hpr.pExonHitCov[X] < ex) {   // 20+ = 60, 35,   30,   25,   20
					double gn = dPpLowGeneCov2[1]+rg[i];
					if (hpr.pGeneHitOlap[X] < gn && hpr.pGeneHitCov[X] < gn) // 35+ = 90, 80,   70,   60,   35
						return bRet(hpr, " Fl" + i, false);
				}
			}
		}
		// 5. Special cases; <10000, ExonCov<30, GeneCov&Olap < 25; do not reject one big exon, so use cov
		if (hpr.xMaxCov<iPpMinLen[2] 
				&& hpr.pExonHitCov[X]< dPpLowExonCov2[1]+5.0 
				&& hpr.pGeneHitCov[X]< dPpLowGeneCov2[0] && hpr.pGeneHitOlap[X]<dPpLowGeneCov2[0]) 
					return bRet(hpr, " FlX", false); // ExonCov<30, GeneCov&Olap<25; 
		
		// Two hits and a big gap that can pass other rules 
		if (hpr.nHits==2 
			&& hpr.pGeneHitCov[X]<dPpLowGeneCov2[0] 
			&& hpr.pHitGapCov[X]<40.0 && hpr.pHitGapCov[Y]<40.0 ) return bRet(hpr, "Fh2", false); 
		
		int cnt=0;
		String note = "";
		
		// 6. Pass 2 out of 4: Need good gene, exon and/or hit coverage
		int idx = (hpr.pGeneHitOlap[X] > iPpMinLen[1]*2) ? 0 : 1; // >2000 less stringent (20,25) else (25,35)
		
		if (hpr.pExonHitCov[X] > dPpLowExonCov2[idx] || hpr.pExonHitFrac[X] > dPpLowExonCov2[idx]) cnt++;  else note += " Fe";
		if (hpr.pGeneHitCov[X] > dPpLowGeneCov2[idx] || hpr.pGeneHitOlap[X] > dPpLowGeneCov2[idx]) cnt++;  else note += " Fg";
		
		if (hpr.nHits>1) {// If mostly intron, want few gaps; however, the exon/gene can fail but have long hit to intron
			int cov =  (hpr.pExonHitCov[X] > dPpLowExonCov2[idx]) ? (int) dMinCov[0] : (int) dMinCov[1]; // 20,25; 60,80
			if (hpr.pHitGapCov[X] > cov && hpr.pHitGapCov[Y] > cov) cnt++; else note += " Fih"; // gap>60|80
			if (hpr.xMaxCov>iPpMinLen[3] && hpr.lenRatio>dLenRatio) cnt++; else note += " Fil"; // len>10000
		}
		if (cnt<=1) return bRet(hpr, " Ff" + cnt + note, false);
			
		return bRet(hpr, " Ps" + cnt + note, true);
	}
	
	private static boolean bGxisPerfect(int idx, int X, HitPair hpr) {
		double p = dMinCov[idx+1]; // 80, 97
		if (hpr.pExonHitCov[X] < p) return false;
		if (hpr.pGeneHitOlap[X]< p) return false;
		if (hpr.pHitGapCov[X]  < p) return false;
		
		if (TRACE) hpr.note += " Perf" + side[X]+ idx;
		return true;
	}
	private static boolean bGxPcov(int X, HitPair hpr) {
		double e = dMinCov[0] * pExonScale, g  =  dMinCov[0] * pGeneScale;
		if (hpr.pExonHitCov[X] < e && hpr.pExonHitFrac[X] < e) return false;
		if (hpr.pGeneHitCov[X] < g && hpr.pGeneHitOlap[X] < g) return false;
		if (hpr.pHitGapCov[X]  < iGmLowHitGapCov2[1]) return false;
		
		if (TRACE) hpr.note += " Pmin" + side[X];
		return true;
	}
	/* G0 *******************/
	protected static boolean bG0passCov(HitPair hpr) { 
		if (hpr.lenRatio<dLenRatio) return false;
		
		for (int X=0; X<2; X++) {
			double  bases = hpr.pGeneHitOlap[X];   // The sum of merged hits
			if (bases < iPpMinG0Len) return false; // 1000 * G0 scale
			if (hpr.pHitGapCov[X] < dMinG0Cov) return false; // 50; always passes for nHit=1
		}
		return true;
	}

	/* ***** Piles **** */
	protected static boolean isPileSet() {
		return pEE || pEI || pEn || pII || pIn;
	}
	protected static boolean isGoodPileHit(HitPair cht) {
		if (cht.bBothGene)
			return (pEE && cht.bEE) || (pEI && cht.bEI) || (pII && cht.bII);
		if (cht.bOneGene) 
			return (pEn && cht.bEn) || (pIn && cht.bIn);
		return false;
	}
	
	private static boolean bRet(HitPair hpr, String msg, boolean rc) {
		if (TRACE) hpr.note += msg;
		return rc;
	}
	/////////////////////////////////////////////////////////
	// gap is positive, olap is negative; does not work for contained; CAS560 was using in places w/o -1
	protected static int pGap_nOlap(int s2, int e1) {return s2-e1-1;}
	
	// gap is positive; olap is negative; works for contained
	protected static int pGap_nOlap(int s1, int e1, int s2, int e2) {
		return -(Math.min(e1,e2) - Math.max(s1,s2) + 1); // CAS548 rm +1; CAS560 put back and change calling routine
	}
	// olap is positive
	protected static int pOlapOnly(int s1, int e1, int s2, int e2) {
		int olap = Math.min(e1,e2) - Math.max(s1,s2) + 1; // CAS560 add +1
		if (olap<0) return 0;
		return olap;
	}
	protected static int pOlapOnly(int X, HitPair h1, HitPair h2) {
		return pOlapOnly(h1.hpStart[X], h1.hpEnd[X],h2.hpStart[X], h2.hpEnd[X]);
	}
	protected static int hprLen(int X, HitPair hpr) {
		return hpr.hpEnd[X]-hpr.hpStart[X]+1;
	}
	
	protected static boolean bHprOlap(HitPair h1, HitPair h2) {
		// exact
		if (h1.hpStart[T]==h2.hpStart[T] && h1.hpStart[Q]==h2.hpStart[Q] &&
			h1.hpEnd[T]  ==h2.hpEnd[T]   && h1.hpEnd[Q]  ==h2.hpEnd[Q]) return true;
		
		String h1t = h1.tGene.geneTag, h1q = h1.qGene.geneTag;
		String h2t = h2.tGene.geneTag, h2q = h2.qGene.geneTag;
		
		if (!h1t.equals(h2t) && !h1q.equals(h2q)) return false;
		
		boolean bt=false, bq=false;
		if (!h1t.endsWith(".") && !h2t.endsWith(".")) {
			String pre1 = h1t.substring(0, h1t.indexOf("."));
			String pre2 = h2t.substring(0, h2t.indexOf("."));
			bt = pre1.equals(pre2);
		}
		if (!h1.qGene.geneTag.endsWith(".") && !h2.qGene.geneTag.endsWith(".")) {
			String pre1 = h1q.substring(0, h1q.indexOf("."));
			String pre2 = h2q.substring(0, h2q.indexOf("."));
			bq = pre1.equals(pre2);
		};
		if (!bt && !bq) return false;
		
		// one or neither overlap
		int tp = pOlapOnly(T, h1, h2);	if (tp==0) return false;
		int qp = pOlapOnly(Q, h1, h2);  if (qp==0) return false;
		
		// are either exact coords
		int t1 = hprLen(T, h1), q1 = hprLen(Q, h1);
		int t2 = hprLen(T, h2), q2 = hprLen(Q, h2);
		if (t1!=tp && q1!=qp && t2!=tp && q2!=qp) return false;
		
		return true;
	}
	protected static int getDiff(int ix, boolean lastIsEQ, Hit ht0, Hit ht1) {
		if (ht0==null) return 0;
		return (lastIsEQ) ? Math.abs(ht1.hStart[ix] - ht0.hEnd[ix]) 
                          : Math.abs(ht0.hStart[ix] - ht1.hEnd[ix]);
	}
	protected static boolean isEqual(String sign) {
		return sign.equals("+/+") || sign.equals("-/-");
	}
	protected static int getInt(String i) { 
		try {
			int x = Integer.parseInt(i);
			return x;
		}
		catch (Exception e) {return -1;}
	}
	/* For trace */
	protected static String toTF(boolean b) {
		return (b) ? "T" : "F";
	}
	protected static String isEQstr(boolean b) {
		return (b) ? "EQ" : "NE";
	}
	protected static String int2Str(int diff) {
		boolean isNeg = (diff<0);
		if (isNeg) diff = -diff;
		String sdiff = (Math.abs(diff)>=1000) ? (diff/1000) + "k" : diff+"";
		sdiff = (Math.abs(diff)>=1000000) ? (diff/1000000) + "M" : sdiff;
		
		if (isNeg) sdiff = "-" + sdiff;
		else       sdiff = " " + sdiff;
		return sdiff;
	}
	protected static String side(int ix) {
		if (ix<0) return "N";
		if (ix<=2) return side[ix];
		else return "?";
	}
	protected static String htType(Gene tg, Gene qg) {
		if (tg!=null && qg!=null) return strType[type2];
		if (tg==null && qg!=null) return strType[type1q];
		if (tg!=null && qg==null) return strType[type1t];
		return strType[type0];
	}
}
