package backend.anchor2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;

import util.ErrorReport;

/***************************************************************
 * A Hit Cluster could align to multiple overlapping genes, but is only assigned to one pair
 */
public class HitCluster implements Comparable <HitCluster> {
	private static final int T=Arg.T, Q=Arg.Q, X=Arg.X;
	private final boolean PRT_HITS=false;
	private final double fudge=0.001;
	
	// from file
	protected int [] cstart = {Integer.MAX_VALUE, Integer.MAX_VALUE};
	protected int [] cend =   {-1, -1};
	protected int pId=0, pSim=0, bLen=0, nHit=0;
	protected String sign;
	protected int [] sumId  = {-1, -1}; // do not need sumSim; only need this for Arg.baseScore
	protected int [] sumLen = {-1, -1};
	
	// from pseudo_anno and file
	protected int [] geneIdx;
	protected String [] geneTag = {"",""};
	
	// from bin
	protected int bin=0;
	protected ArrayList <Hit> clHitList = null;
	protected String targetSubHits="", querySubHits="";
	
	// working
	protected int cHitNum=0;
	protected String htype="";
	
	protected boolean bEE=false, bEI=false, bII=false, bEn=false, bIn=false, bnn=false; // for GrpPairPrune filter on piles
	protected boolean bBothGene=false, bEitherGene=false; // for GrpPair Cluster filter on good bases
	protected double [] exonScore = {0.0, 0.0, 0.0}; 	// T, Q, X (X (Q+T) used for sorting)
	
	protected int [] pile = {0,0};               		// closely overlapping clusters; see GrpPairPrune
	protected boolean bBaseFilter=false, bPile=false; 	// No written to DB if any T               
	
	protected HitCluster() {}
	
	protected HitCluster(int bin, int [] geneIdx) {
		this.bin = bin;
		this.geneIdx = geneIdx;
	
		clHitList = new ArrayList <Hit> ();
	}
	protected void addSubHit(Hit ht) {
		clHitList.add(ht);
		
		for (int i=0; i<2; i++) {
			if (cstart[i]>ht.start[i]) cstart[i] = ht.start[i];
			if (cend[i]<ht.end[i])     cend[i] = ht.end[i];
		}
	}
	// All processing done at this point except Piles
	protected void finishSubs(Gene tgene, Gene qgene) throws Exception {
	try {
		// create subhits for DB
		nHit = clHitList.size();
		targetSubHits=querySubHits="";
		int [] sumSim= {0,0};
		
		Hit.sortByX(Q, clHitList); // This is the order anchor1 does it - must be the same
		for (Hit ht : clHitList) {
			targetSubHits += ht.start[T] + ":" + ht.end[T] + ",";
			querySubHits  += ht.start[Q] + ":" + ht.end[Q] + ",";
			
			sumSim[T] += ht.sim * ht.len[T]; 
			sumId[T]  += ht.id  * ht.len[T];
			sumLen[T] += ht.len[T];
			
			sumSim[Q] += ht.sim * ht.len[T]; 
			sumId[Q]  += ht.id  * ht.len[T];
			sumLen[Q] += ht.len[Q];
		}
		targetSubHits.substring(0, targetSubHits.length()-1);
		querySubHits.substring(0, querySubHits.length()-1);
		
		// merge hit values
		sign = clHitList.get(0).sign; 
		int ix = (sumLen[T]>sumLen[Q]) ? T : Q;
		pId  = sumId[ix]/sumLen[ix];
		pSim = sumSim[ix]/sumLen[ix];
		bLen = sumLen[ix];
		
		if (tgene==null && qgene==null) {
			htype="nn";
			bnn=true;
			return;
		}
		
		// set hit type and score - already scored in GrpPairGene, but this updates with correct hits....
		bBothGene   = (tgene!=null && qgene!=null);
		bEitherGene = (tgene!=null || qgene!=null);
		
		HashSet <Integer> hitSet = new HashSet <Integer> ();
		for (Hit ht : clHitList) hitSet.add(ht.hitCnt);  // this hit list to get score
		
		if (tgene!=null) {
			exonScore[T] = tgene.scoreExons(hitSet); 
			geneTag[T] =   tgene.geneTag;
		}
		if (qgene!=null) {
			exonScore[Q] = qgene.scoreExons(hitSet);
			geneTag[Q] 	 = qgene.geneTag;
		}
		
		if (exonScore[T]>fudge && exonScore[Q]>fudge) 	{htype="EE"; bEE=true;}
		else if (exonScore[T]>fudge && geneIdx[Q]>0)  	{htype="IE"; bEI=true;}
		else if (exonScore[Q]>fudge && geneIdx[T]>0)  	{htype="EI"; bEI=true;}
		else if (exonScore[T]>fudge && geneIdx[Q]==0) 	{htype="nE"; bEn=true;}
		else if (exonScore[Q]>fudge && geneIdx[T]==0)   {htype="En"; bEn=true;}
		else if (geneIdx[T]>0 && geneIdx[Q]>0)			{htype="II"; bII=true;}
		else if (geneIdx[T]>0) 							{htype="nI"; bIn=true;}
		else if (geneIdx[Q]>0)							{htype="In"; bIn=true;}
		else 											htype="??";
		
		exonScore[X] = exonScore[T] + exonScore[Q];
	}
	catch (Exception e) {ErrorReport.print(e, "finish subs"); }
	}
	
	////////////////////////////////////////////////////
	protected boolean bHitsExon(int X){
		if (exonScore[X]>fudge) return true;
		else return false;
	}
	///////////////////////////////////////////////////////
	// Save to pseudo_hits
	protected int getGeneOverlap() {
		if (geneIdx[T]>0 && geneIdx[Q]>0) return 2;
		if (geneIdx[T]>0) return 1;
		if (geneIdx[Q]>0) return 1;
		return 0;
	}
	
	///////////////////////////////////////////////////////////////////
	public int compareTo(HitCluster ht) {
		if (this.cstart[T] < ht.cstart[T]) return -1;
		if (this.cstart[T] > ht.cstart[T]) return 1;
		if (this.cend[T] < ht.cend[T]) return -1;
		if (this.cend[T] > ht.cend[T]) return 1;
		return 0;
	}
	
	protected static void sortByX(int X, ArrayList<HitCluster> hits) { // GrpPair saveCluster hits, GrpPairPile.identifyPiles
		Collections.sort(hits, 
				new Comparator<HitCluster>() {
					public int compare(HitCluster h1, HitCluster h2) {
						if (h1.cstart[X] < h2.cstart[X]) return -1;
						if (h1.cstart[X] > h2.cstart[X]) return 1;
						if (h1.cend[X]   > h2.cend[X]) return -1;
						if (h1.cend[X]   < h2.cend[X]) return 1;
						
						return 0;
					}
				}
			);
	}
	
	protected static void sortByGene(ArrayList<HitCluster> hits) { // GrpPairPile sort clusters in pile
		Collections.sort(hits, 										
			new Comparator<HitCluster>() {
				public int compare(HitCluster h1, HitCluster h2) {
					if (h1.exonScore[X]>h2.exonScore[X]) return -1; 
					if (h1.exonScore[X]<h2.exonScore[X]) return 1;
					
					if (h1.bBothGene && !h2.bBothGene) return -1;
					if (!h1.bBothGene && h2.bBothGene) return 1;
					
					if (h1.bEitherGene && !h2.bEitherGene) return -1;
					if (!h1.bEitherGene && h2.bEitherGene) return 1;
					
					if (h1.bLen>h2.bLen) return -1; 
					if (h1.bLen<h2.bLen) return  1; 
					
					return h1.cHitNum-h2.cHitNum;
				}
			}
		);
	}
	protected static void sortByPile(int X, ArrayList<HitCluster> hits) {  // GrpPairPile - sort for printing
		Collections.sort(hits, 
			new Comparator<HitCluster>() {
				public int compare(HitCluster h1, HitCluster h2) {
					if (h1.pile[X]<h2.pile[X]) return -1;
					if (h1.pile[X]>h2.pile[X]) return 1;
					if (h1.sumLen[X]>h2.sumLen[X]) return -1;
					if (h1.sumLen[X]<h2.sumLen[X]) return 1;
					return 0;
				}
			}
		);
	}
	//////////////////////////////////////////////////
	protected String toResults(String chrs) {
		String num = String.format("CL%-5d b%-6d", cHitNum, bin);
		
		String            		sl = "SHOW ";
		if (bPile)        		sl = "Pile ";
		else if (bBaseFilter) 	sl = "Filt " ;
		
		String coords = String.format(" h%-3d T[%,10d %,10d %,7d] Q[%,10d %,10d %,7d]  ", 
				nHit, cstart[T], cend[T], sumLen[T], cstart[Q], cend[Q], sumLen[Q]);
		
		String id = String.format(" "
				+ "P[%3d %3d] ", pId, pSim);
		String gn = String.format("[T#%-6s Q#%-6s] ", geneTag[T], geneTag[Q]);
		String sc = String.format("[%4.1f %4.1f] ", exonScore[T], exonScore[Q]);
		String rp = String.format("[T%-5d Q%-5d] ", pile[T], pile[Q]);
		String eq = (sign.equals("+/+") || sign.equals("-/-")) ? "EQ" : "NE";
		
		String hits="";
		if (PRT_HITS) {
			hits="\n";
			Hit last=null;
			for (Hit ht : clHitList) {
				hits += " " + ht.toDiff(last) + "\n";
				last = ht;
			}
		}
		return num + sl + htype + id  + coords +  rp +gn + sc + eq + chrs + hits;
	}
}
