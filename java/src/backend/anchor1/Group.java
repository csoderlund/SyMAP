package backend.anchor1;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.Vector;

import backend.Constants;

import java.sql.ResultSet;

import database.DBconn2;
import symap.Globals;
import util.ErrorReport;

/**
 * Called in Proj to load all groups 
 * Mainly used by AnchorMain - creates non-genes from hits that do not overlap genes
 * CAS575 removed a lot of old trace code
 */
public class Group {
	public  static boolean bSplitGene=false;// split hit/gene if true;  -sg will split
	
	protected  static final int FSplitLen  	= 50000; 
	private static final int FAnnotBinSize  = 30000;  // annoSetMap
	private static final int FHitBinSize    = 10000;  // hitBinMap
	private static final int FMaxHitAnno 	= 100000;	 
	
	protected int avgGeneLen =  1000, maxGeneLen = 50000; // use these defaults if no anno for project
				  	
	protected int idx;
	protected String fullname;	 // displayName + chrName with prefix
	private String chrName;  // prefix removed
	private QueryType mQT;   // AnchorsMain { Query, Target, Either };
	
	// Created during scan1 to create annotSetMap; then finish with create hitBinMap from annos 
	private TreeMap <Integer, AnnotSet> annoSetMap;   // list of AnnoSet (of AnnotElems) per b; fast lookup
	private HashSet <AnnotElem> 		annoElemSet;  // list of AnnotElem (Gene, non-gene, n/a)
	
	// Created during scan2 to create cluster hits 
	private TreeMap<Integer, Vector<HitBin>> hitBinMap; // list of HitBin containing Hits per b
	private Vector<HitBin> 					 hitBins;   // list of HitBin containing Hits
	
	private int topN = 0;
	
	protected Group(int topN, String name, String fullname, int idx, int qType) {
		this.topN = topN;
		this.chrName = name;
		this.fullname = fullname;
		this.idx = idx;
		
		if (qType==Constants.QUERY)       	this.mQT = QueryType.Query; 
		else if (qType==Constants.TARGET) 	this.mQT = QueryType.Target;
		else 								this.mQT = QueryType.Either;
		
		annoElemSet = 	new HashSet <AnnotElem> ();
		annoSetMap = 	new TreeMap<Integer,AnnotSet>();	
		hitBins = 		new Vector<HitBin>();
		hitBinMap = 	new TreeMap<Integer, Vector<HitBin>>();
	}
	
	protected String idStr()		{ return "" + idx + "(" + chrName + ")";} // AnchorMain1.clusterHits2
	
	/******************* ANNO BINS ******************************/
	/************************************************************
	 * Called from AnchorsMain1.loadAnnoBins [loadAnnotation]
	 */
	protected int createAnnoFromGenes(DBconn2 dbc2) {
	try {
		HashSet <Integer> idxSplit = new HashSet <Integer> ();
		ResultSet rs;
		
		if (bSplitGene) {
			rs = dbc2.executeQuery("SELECT idx, start, end, genenum, (end-start) as len FROM pseudo_annot "
					+ " WHERE grp_idx=" + idx + " and type ='gene' order by genenum, len DESC");
			int lastIdx=0, lastStart=0, lastEnd=0, lastGN=-1, lastCnt=0;
			while (rs.next()) {
				int idx = rs.getInt(1);
				int s = rs.getInt(2);
				int e = rs.getInt(3);
				int g = rs.getInt(4);
				if (lastGN!=g) {
					if (lastCnt>1) idxSplit.add(lastIdx);
					lastIdx=idx; lastStart=s; lastEnd=e; lastGN=g; lastCnt=0;
				}
				else if (lastGN==g) {
					if (isContained(lastStart, lastEnd, s, e)) lastCnt++;
				}
			}
		}
		avgGeneLen = maxGeneLen = 0;
		int totalGenes = 0; 
		
		rs = dbc2.executeQuery("SELECT idx, start, end, strand, genenum FROM pseudo_annot "
				+ " WHERE grp_idx=" + idx + " and type ='gene'");
		while (rs.next()){
			int annotIdx = rs.getInt(1);
			int s = rs.getInt(2);
			int e = rs.getInt(3);
			boolean isRev = rs.getString(4).contentEquals("-");
			int gn = rs.getInt(5);
			
			int len = (e-s+1);
			if (idxSplit.contains(annotIdx)) { 
				int minLeftover = FSplitLen/10;
				int left = len%FSplitLen;
				int parts = (left >= minLeftover ?  (len/FSplitLen +1) : (len/FSplitLen));
				
				for (int i = 1; i < parts; i++) {
					int start = s + FSplitLen*(i-1);
					int end = 	start + FSplitLen - 1;
					AnnotElem ae = new AnnotElem(annotIdx, start, end, isRev, gn, GeneType.Gene); 
					updateAnnotBins(ae);
				}
				int start = s + FSplitLen * (parts-1);
				AnnotElem ae = new AnnotElem(annotIdx, start, e, isRev, gn, GeneType.Gene); 
				updateAnnotBins(ae);
			}
			else {
				AnnotElem ae = new AnnotElem(annotIdx, s, e, isRev, gn, GeneType.Gene); 
				updateAnnotBins(ae);
			}
			
			int length = (e-s+1);
			avgGeneLen += length;
			maxGeneLen = Math.max(maxGeneLen, length);		
			totalGenes++;
		}
		rs.close();
		if (totalGenes > 0)  {
			avgGeneLen /= totalGenes;
			maxGeneLen = Math.min(maxGeneLen, FMaxHitAnno); // still need a limit on this; use 100k
		}
		if (maxGeneLen==0) maxGeneLen=50000;			
		if (avgGeneLen==0) avgGeneLen=1000;
		if (Globals.TRACE) System.out.println("avg: " + avgGeneLen + " max: " + maxGeneLen + " " + fullname);
		return totalGenes;
	}
	catch (Exception e) {ErrorReport.print(e, "load genes and bin them"); return 0;}
	}
	
	/***********************************************************
	 * AnchorMain1.scanFile1
	 */
	protected void createAnnoFromHits1(int hstart, int hend, boolean isRev) {
		int bs = Math.max((hstart - avgGeneLen)/FAnnotBinSize, 0);
		int be =          (hend + avgGeneLen)/FAnnotBinSize;
						
		int newStart = hstart, newEnd = hend;
		TreeSet<AnnotElem> ngOlaps = new TreeSet<AnnotElem>();

		for (int b = bs; b <= be; b++) {
			HashSet<AnnotElem> list = getAnnoBinList(b);
			if (list==null) continue;
		
			for (AnnotElem ae : list) {
				int olap = getOlap(hstart, hend, ae.start, ae.end);
				if (ae.isGene()){
					if (olap > 0) return; // Have an anno already from a gene
				}
				else {
					if (olap > -avgGeneLen) { // create a bin to catch overlapping and close hits
						int tryStart = Math.min(newStart, ae.start);
						int tryEnd =   Math.max(newEnd, ae.end);
						if (!overlapGene(tryStart, tryEnd)) { 
							ngOlaps.add(ae);	
							newStart = tryStart;
							newEnd =   tryEnd;
						}
					}
				}
			}
		}

		if (ngOlaps.size() == 1 ){
			if (ngOlaps.first().start <= hstart && ngOlaps.first().end >= hend) return;
		}

		// remove all hits that are covered by newStart, newEnd
		for (AnnotElem ae : ngOlaps) removeAnnotation(ae);
		
		// add one or more new ones spanning the region
		for (int s = newStart; s < newEnd; s += maxGeneLen){
			int e = Math.min(newEnd, s + maxGeneLen - 1);
			AnnotElem ae = new AnnotElem(0, s, e, isRev, 0, GeneType.NonGene);
			updateAnnotBins(ae);
		}
	}
	private boolean overlapGene(int hstart, int hend){
		int bs = hstart/FAnnotBinSize;
		int be = hend/FAnnotBinSize +1;
		
		for (int b = bs; b <= be; b++) {
			HashSet<AnnotElem> list = getAnnoBinList(b);
			if (list==null) continue;
			
			for (AnnotElem ae : list) {
				if (!ae.isGene()) continue;
				
				int overlap = getOlap(hstart, hend, ae.start, ae.end);
				if (overlap>=0) return true;
			}
		}
		return false;
	}
	private void removeAnnotation(AnnotElem ae){
		annoElemSet.remove(ae);
		
		int bs = ae.start/FAnnotBinSize;
		int be = ae.end/FAnnotBinSize;
		
		for (int b = bs; b <= be; b++) {
			if (annoSetMap.containsKey(b) ) {
				AnnotSet as = annoSetMap.get(b);
				as.remove(ae);
			}
		}
	}	
	// used by both createAnnoFromGenes and createAnnoFromHits
	private void updateAnnotBins(AnnotElem a) {
		annoElemSet.add(a);
		
		int bs = a.start/FAnnotBinSize; // since 30k, start and end usually the same
		int be = a.end/FAnnotBinSize;
		for (int b = bs; b <= be; b++) {
			AnnotSet as;
			if (!annoSetMap.containsKey(b) ) {
				as = new AnnotSet();
				annoSetMap.put(b, as);
			}
			else as = annoSetMap.get(b);
			
			as.add(a);
		}
	}
	// For getBestOlapAnno for clustering and getAnnoHitOlapForSave
	private HashMap<AnnotElem,Integer> getAllOlapAnnos(int hstart, int hend){
		int bs = hstart/FAnnotBinSize;
		int be = hend/FAnnotBinSize +1;
		
		HashMap<AnnotElem,Integer> retMap = new HashMap<AnnotElem,Integer>();
		
		for (int b = bs; b <= be; b++) {
			HashSet<AnnotElem> list = getAnnoBinList(b);
			if (list==null) continue;
			
			for (AnnotElem ae : list) {
				
				int overlap = getOlap(hstart, hend, ae.start, ae.end);
				
				if (overlap >= 0) {
					if (!retMap.containsKey(ae) || overlap > retMap.get(ae))
						retMap.put(ae, overlap);
				}
			}
		}
		return retMap;
	}
	
	/*****************************************************************************
	 * called from AnchorMain.clusterHits2 while clustering hits into subhits
	 */
	protected AnnotElem getBestOlapAnno(int hStart, int hEnd) {
		AnnotElem best = null;
		int bestOlap=0, curOlap=0;

		HashMap<AnnotElem, Integer> annoMap = getAllOlapAnnos(hStart, hEnd); 
		
		for (AnnotElem cur : annoMap.keySet()) {
			curOlap = annoMap.get(cur);
				
			if (best == null )	{ // first time
				best = cur;
				bestOlap = curOlap;
				continue;
			}
			
			if (!cur.isGene() && best.isGene()) continue;
			
			if (cur.isGene() && !best.isGene()) {
				best = cur;
				bestOlap = curOlap;
				continue;
			}
		
			// either both are genes or both are not
			if (curOlap > bestOlap)  {
				best = cur;
				bestOlap = curOlap;
			}
			else if (curOlap == bestOlap) { 
				int curExtra  = Math.abs(hStart-cur.start) + Math.abs(hEnd-cur.end);
				int bestExtra = Math.abs(hStart-best.start) + Math.abs(hEnd-best.end);
				if (curExtra<bestExtra) {
					best = cur;
					bestOlap = curOlap;
				}
			}		
		}
		return best;
	}
	
	// AnchorsMain1 - get hits to save to pseudo_hits_anno; the hits were just read from the db
	protected int [][] getAnnoHitOlapForSave(Hit ht, int start, int end) throws Exception {
	try {
		HashMap<AnnotElem,Integer> vals = getAllOlapAnnos(start, end);
		int [][] retVals = new int [vals.size()][4];
		int i=0;
		for (AnnotElem ae : vals.keySet()) {
			retVals[i][0] = ht.idx;
			retVals[i][1] = ae.idx;
			
			double dolap = (double) vals.get(ae);
			dolap = (dolap/(double)(ae.end-ae.start)) *100.0; 
			retVals[i][2] = (int) Math.round(dolap);
				
			if (ht.annotIdx1==ae.idx) retVals[i][3]=ht.annotIdx2; 
			else if (ht.annotIdx2==ae.idx) retVals[i][3]=ht.annotIdx1;
			i++;
		}
		return retVals;
	}
	catch (Exception e) {ErrorReport.print(e, "getting annotation overlaps"); throw e; }
	}
	/********************* HIT BINS *******************************/
	/***************************************************************
	 * called after scanFile1 
	 * AnnotElem 1-to-1 with HitBin; then HitBin is put in hitBinMap, which has more entries than HitBin
	 */
	protected void createHitBinFromAnno2(String proj){
		for (AnnotElem ae : annoElemSet) {
			HitBin hb = new HitBin(ae.start, ae.end, ae.mGT, topN, mQT);
			hitBins.add(hb);
			
			int bs = ae.start/FHitBinSize;
			int be = ae.end/FHitBinSize;
			
			for (int b = bs; b <= be; b++){
				if (!hitBinMap.containsKey(b)) hitBinMap.put(b, new Vector<HitBin>());
				hitBinMap.get(b).add(hb);
			}
		}
	}
	
	/***********************************************************************
	 * AnchorMain.filterClusterHits2; add to HitBin with best overlap if pass filters [checkAddToHitBin2]
	 ***************************************************************/
	protected void filterAddToHitBin2(Hit hit, int start, int end) throws Exception { // query/target start end
		int bs = start/FHitBinSize;
		int be = end/FHitBinSize;
		HitBin hbBest = null;
		int bestOlap = 0;
		
		// find best HitBin; whether this gets filtered or not depends on what is already in HitBin
		// Multiple HitBins can have same AnnoHits
		for (int b = bs; b <= be; b++) {
			if (!hitBinMap.containsKey(b)) continue; // should not happen because all added earlier
			
			for (HitBin hbin : hitBinMap.get(b)) {
				int olap = getOlap(hbin.start, hbin.end, start, end);

				if (olap <= 0) continue; 
				
				if (olap > bestOlap) { // always true for 1st
					hbBest = hbin;
					bestOlap = olap;
				}
				else if (olap == bestOlap) {
					if (hbin.start < hbBest.start || 
					   (hbin.start== hbBest.start && hbin.end > hbBest.end)){ 
						hbBest = hbin;
						bestOlap = olap;						
					}
				}
			}
		}
		// Filter
		if (hbBest != null) 
			hbBest.filterAddHit2(hit, start, end);
		else 
			System.err.println("SyMAP error: filterAddtoHitBin " + start + " " + end);	 
	}
	
	// Called from AnchorMain1 to add this groups filtered hits to the main hashset; CAS575 remove BinStats
	protected void filterFinalAddHits(HashSet<Hit> finalHits) throws Exception { 
		for (HitBin hb : hitBins){
			hb.filterFinalAddHits(finalHits);
		}
	}
	private boolean isContained(int s1,int e1, int s2, int e2){
		return (s2 >= s1 && e2 <= e1);
	}
	private int getOlap(int s1,int e1, int s2, int e2) {
		int gap = Math.max(s1,s2) - Math.min(e1,e2);
		return -gap;
	}	
	
	/** annoSetMap methods **/
	private HashSet<AnnotElem> getAnnoBinList(int bin) {// called by createAnnoFromHits and getAllOlapAnnos
		if (annoSetMap.containsKey(bin))  return annoSetMap.get(bin).mList;
		return null; // this happens a lot
	}
	
	private class AnnotSet { 
		private HashSet <AnnotElem> mList;
		
		private AnnotSet() 				 {mList = new HashSet<AnnotElem>();}
		private void add(AnnotElem ae) 	 {mList.add(ae);}
		private void remove(AnnotElem ae){mList.remove(ae);}	
	}
	protected String getInfo() {
		return String.format("%5s AnnoSetMap %,5d ElemSet %,5d  hitBinMap %,5d  HitBins %,5d", 
				chrName, annoSetMap.size(), annoElemSet.size(), hitBinMap.size(), hitBins.size());
	}
}
