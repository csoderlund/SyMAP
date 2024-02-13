package backend.anchor2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import util.ErrorReport;

/*************************************************************
 * Set of joined hits representing g2, g1, or g0
 */
public class HitPair {
	private static final int T=Arg.T, Q=Arg.Q, TQ=Arg.TQ;
	
	protected int  gtype=Arg.typeUnk;	
	protected char flag=Arg.UNK;	// Major, Minor, Split, Filter 
	protected boolean hasBin=false; // Marked during GrpPairGx.assignBin for GrpPairGx.splitBin
	protected int bin=0;			// assigned in GrpPairGx.assignBin
	
	///////
	protected int [] start = {Integer.MAX_VALUE, Integer.MAX_VALUE};
	protected int [] end   = {0,0};
	
	protected ArrayList <Hit>  hitList = new ArrayList <Hit> (); 
	protected boolean isStEQ=true; 	// same strand (T) diff strand (F)
	protected boolean isRst=true;	// g2 genes correspond to hit strand
	
	protected Gene tGene=null, qGene=null;
	protected double []  exonScore = {0.0, 0.0, 0.0};
	protected double []  geneScore = {0.0, 0.0, 0.0};
	protected double []  hitScore = {0.0, 0.0, 0.0};
	protected int [] geneIdx = {0,0}; // geneIdx 
	
	protected int xSumId=0, xSumSim=0, xMaxCov=0;				
	protected int [] sumId  = {0, 0}; // Arg.baseScore
	protected int [] sumCov = {0, 0}; // Arg.baseScore, sortByPile. Summed merged hits
	
	// Cluster
	protected int cHitNum=0; 	    // after filter; assigned in GrpPair.createCluster
	protected boolean bEE=false, bEI=false, bII=false, bEn=false, bIn=false, bnn=false; // for GrpPairPile filter on piles
	protected boolean bBothGene=false, bEitherGene=false; // for GrpPair Cluster filter on good bases
	protected String sign="";							// */-, etc set in setScores
	protected String targetSubHits="", querySubHits=""; // concatenated hits for DB
	protected String htype="";							// EE, EI, etc
	
	// GrpPairPile
	protected int [] pile = {0,0};  // closely overlapping clusters; see GrpPairPile
	
	// Used for trace
	protected boolean isBnSpl=false, isMfSpl=false; 
	
	protected HitPair(int type, Gene tGene, Gene qGene, boolean isEQ) {
		this.gtype = type; 		// see Arg.type; G2, G1t, G1q, G0
		this.tGene = tGene;
		this.qGene = qGene;
		this.geneIdx[T] = (tGene!=null) ? tGene.geneIdx : 0;	
		this.geneIdx[Q] = (qGene!=null) ? qGene.geneIdx : 0;		
		this.isStEQ = isEQ;
		
		bBothGene = (tGene!=null && qGene!=null);
		bEitherGene = (tGene!=null || qGene!=null);
	}
	
	protected void addHit(Hit ht) {
		hitList.add(ht);	
		for (int x=0; x<2; x++) {
			if (start[x] > ht.start[x]) start[x] = ht.start[x];
			if (end[x]   < ht.end[x])   end[x] = ht.end[x];
		}
	}
	/*******************************************************
	 * Scores: hit, exon, gene, id & len
	 * Everything here is needed for filtering, sorting...; finishSubs has the rest
	 */
	protected void setScores() throws Exception {
	try {
		sumCov[T]=sumCov[Q]=0;
		
		// T
		Hit.sortBySignByX(T, hitList); // SORT 
		ArrayList <Hit> mergedHits = calcMergeHits(T);
	
		int totlen=end[T]-start[T]+1;
		for (Hit ht : mergedHits) sumCov[T] += (ht.end[T]-ht.start[T]+1); // %hit length with actual hit; i.e. no gap
		hitScore[T] = ((double)sumCov[T]/(double)totlen)*100.0;
	
		if (tGene!=null) {
			double [] tscores = tGene.scoreExons(mergedHits); 
			exonScore[T] = tscores[0]; 
			geneScore[T] = tscores[1];
		}
		// Q
		Hit.sortBySignByX(Q, hitList); // SORT - leave in Q order
		mergedHits = calcMergeHits(Q);
		
		totlen=end[Q]-start[Q]+1;
		for (Hit ht : mergedHits) sumCov[Q] += (ht.end[Q]-ht.start[Q]+1); // %hit length with actual hit; i.e. no gap
		hitScore[Q] = ((double)sumCov[Q]/(double)totlen)*100.0;

		if (qGene!=null) {
			double [] qscores = qGene.scoreExons(mergedHits); 
			exonScore[Q] = qscores[0]; 
			geneScore[Q] = qscores[1];
		}
		// TQ
		exonScore[TQ]  = exonScore[T] + exonScore[Q];
		geneScore[TQ]  = geneScore[T] + geneScore[Q];
		hitScore[TQ]  = hitScore[T] + hitScore[Q];
		
		// Sim/Id
		int [] sumSim= {0,0};
		sumId[T]=sumId[Q]=0;	
		int [] sumLen = {0,0};;
		for (Hit ht : hitList) {
			sumSim[T] += ht.sim * ht.len[T]; 
			sumId[T]  += ht.id  * ht.len[T];
			sumLen[T] += ht.len[T];
			
			sumSim[Q] += ht.sim * ht.len[T]; 
			sumId[Q]  += ht.id  * ht.len[T];
			sumLen[Q] += ht.len[Q];
		}
		int ix = (sumCov[T]>sumCov[Q]) ? T : Q;
		xSumId  = sumId[ix]/sumLen[ix];		// use len since id*len
		xSumSim = sumSim[ix]/sumLen[ix];
		xMaxCov = sumCov[ix]; // DB Coverage, sort

		setSign(); // CAS548 make sign agree with gene strands
					
		if (tGene==null && qGene==null) {
			htype="nn";
			bnn=true;
			return;
		}
		
		if (exonScore[T]>Arg.fudge && exonScore[Q]>Arg.fudge) 	{htype="EE"; bEE=true;}
		else if (exonScore[T]>Arg.fudge && geneIdx[Q]>0)  		{htype="IE"; bEI=true;}
		else if (exonScore[Q]>Arg.fudge && geneIdx[T]>0)  		{htype="EI"; bEI=true;}
		else if (exonScore[T]>Arg.fudge && geneIdx[Q]==0) 		{htype="nE"; bEn=true;}
		else if (exonScore[Q]>Arg.fudge && geneIdx[T]==0)   	{htype="En"; bEn=true;}
		else if (geneIdx[T]>0 && geneIdx[Q]>0)					{htype="II"; bII=true;}
		else if (geneIdx[T]>0) 									{htype="nI"; bIn=true;}
		else if (geneIdx[Q]>0)									{htype="In"; bIn=true;}
	}
	catch (Exception e) {ErrorReport.print(e, "Setting scores"); throw e;}
	}
	private void setSign() {
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
	/* The hits overlap, so merge the overlapping ones */
	protected ArrayList<Hit> calcMergeHits(int X) {
	try {
		ArrayList <Hit> subSet = new ArrayList <Hit> ();
		
		for (Hit ht : hitList) {
			boolean found=false;
			for (Hit sh : subSet) {
				int olap = Arg.olapOrGap(sh.start[X], sh.end[X], ht.start[X], ht.end[X]);
				if (olap<0) continue;
				
				found=true;
				if (ht.end[X]>sh.end[X])     sh.end[X] = ht.end[X];
				if (ht.start[X]<sh.start[X]) sh.start[X] = ht.start[X];
				break;
			}
			if (!found) {
				Hit sh = new Hit(X, ht.start[X], ht.end[X]);
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
		
		Hit.sortByX(Q, hitList); // This is the order anchor1 does it - must be the same
		for (Hit ht : hitList) {
			targetSubHits += ht.start[T] + ":" + ht.end[T] + ",";
			querySubHits  += ht.start[Q] + ":" + ht.end[Q] + ",";
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
	////////////////////////////////////////////////////
	protected boolean bPassCovG2() {
		if (exonScore[T]<Arg.lowExon && hitScore[T]<Arg.perHitOfLenWithLow) return false; // 5.0 and 5.0
		if (exonScore[Q]<Arg.lowExon && hitScore[Q]<Arg.perHitOfLenWithLow) return false;
		if (hitScore[T]<Arg.perHitOfLenWithExon) return false;							  // 1.5
		if (hitScore[Q]<Arg.perHitOfLenWithExon) return false;
		return true;
	}
	protected boolean bPassCovG1() {
		int X = (tGene!=null) ? T : Q;
		int Y = (tGene!=null) ? Q : T;
		if (exonScore[X]<Arg.lowExon && hitScore[X]<Arg.perHitOfLenWithLow) return false; 
		if (hitScore[X]<Arg.perHitOfLenWithExon) return false;
		if (hitScore[Y]<Arg.perHitOfLenWithLow) return false;
		return true;
	}
	
	protected boolean bPassCovG0() {
		return hitScore[T]>Arg.perHitOfLenWithLow || hitScore[Q]>Arg.perHitOfLenWithLow;
	}
	// these use user supplied cutoffs
	protected boolean bPassCutoff(int X){
		double  bases = Arg.baseScore(X, this);
		boolean bexon = (exonScore[X]>Arg.fudge);

		return (bexon) ? (bases>=Arg.gnMinExon) : (bases>=Arg.gnMinIntron);
	}
	protected boolean bPassCutoff() {
		double  bases = Arg.baseScore(this);
		return (bases >= Arg.g0MinBases);
	}
	/***************************************************************************/
	protected void clear() {
		hitList.clear();
	}
	protected String toResults(String chrs, boolean bHits) { // chrs="" is hitPair, else clusters
		String loc = chrs.equals("") ? String.format("[L %,6d %2d %.2f]", xMaxCov, xSumId, Arg.baseScore(this)) : // Pair
									   String.format("[P %d %d]", pile[T], pile[Q]); // final cluster
		String cb = (chrs.equals("")) ?  String.format("[b%-5d]", bin) : 
			                             String.format("[CL%-5d b%-5d]", cHitNum, bin);
		
		String n1 = (tGene!=null) ? tGene.geneTag : "None";
		String n2 = (qGene!=null) ? qGene.geneTag : "None";
		String eq = (isStEQ) ? "EQ" : "NE";
		
		String f =  (isMfSpl) ? "MfSpl " : "";   // created from G1/G2 MF split
		String s =  (isBnSpl) ? "BinSpl " : "";  // created from bin split
		String w =  (!isRst)  ? "WS" +sign : " " + sign;   // hit sign does not agree with gene
		String d =  (hasBin)  ? "WasSpl " : "";  // was bin split into others
		String x = w+" " + f+s+d;
		
		String msg = String.format(
			">%-3s:%c%s  T#%-6s Q#%-6s [E T%.2f Q%.2f X%.2f][G T%.2f Q%.2f X%.2f][H %.2f %.2f]%s #%-3d %s %s", 
				Arg.strType[gtype], flag, cb, n1, n2, 
				exonScore[T], exonScore[Q], exonScore[TQ], geneScore[T], geneScore[Q], geneScore[TQ],
				hitScore[T], hitScore[Q], loc, hitList.size(), eq, x);
		
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
	protected String toWSResults(String chrs) { // symap -wsp outputs this; documented in SystemHelp
		String [] chr = chrs.split(",");
		String eq = isStEQ ? " =" : "!=";
		eq = qGene.strand + "/" + tGene.strand + " " + eq;
		
		String locq = String.format("[%5s %,10d %,10d %,6dbp]", chr[Q], start[Q], end[Q], (end[Q]-start[Q]+1));
		String loct = String.format("[%5s %,10d %,10d %,6dbp]", chr[T], start[T], end[T], (end[T]-start[T]+1));
		
		return String.format("%s #%-3d %-8s %-8s %s %s %s", htype, hitList.size(), 
				qGene.geneTag, tGene.geneTag, eq, locq, loct);
	}
	
	protected String toPileResults() {
		String loc = String.format("[T %,10d %,10d][Q %,10d %,10d]", start[T],end[T], start[Q], end[Q]);
		String s =  (isBnSpl) ? "BinSpl " : "";  // created from bin split
		String n1 = (tGene!=null) ? tGene.geneTag : "None";
		String n2 = (qGene!=null) ? qGene.geneTag : "None";
		
		return String.format(
			">%-3s:%c %s %,7d T#%-6s Q#%-6s [P %5d %5d] %s #%d %s %s", 
			Arg.strType[gtype], flag, htype, xMaxCov, n1, n2, pile[T],pile[Q],  loc,hitList.size(), sign, s);
	}
	//////////////////////////////////////////////////////////////////////////////
	protected static void sortByX(int X, ArrayList<HitPair> hpr) { // GrpPair.saveClusterHits, GrpPairPile.identifyPiles
		Collections.sort(hpr, 
			new Comparator<HitPair>() {
				public int compare(HitPair h1, HitPair h2) {
					if (h1.start[X] < h2.start[X]) return -1;
					if (h1.start[X] > h2.start[X]) return 1;
					if (h1.end[X]   > h2.end[X]) return -1;
					if (h1.end[X]   < h2.end[X]) return 1;
					
					return 0;
				}
			}
		);
	}
}