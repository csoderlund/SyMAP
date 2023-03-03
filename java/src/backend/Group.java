package backend;

import java.util.Set;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.Vector;
import java.sql.ResultSet;

import util.ErrorReport;

/**
 * Called in SyProj to load all groups; used by AnnotLoadMain, AnchorsMain, SyntenyMain; mostly AnchorMain
 * loads all the group annotation from DB (group is chromosome, linkage group, etc)
 * 
 * CAS500 removed lots of dead code including annotInclude, annotExclude, keepFamilies, AnnotAction 
 */

public class Group {
	private static final int max_cluster_gap = 1000;   // CAS534 was set in SyProps
	private static final int max_cluster_size = 50000; // clusters are being made way over this! e.g. 1M
	
	private static final int annotBinSize  = 30000;  // bp size of annotation bin; CAS534 was setting props
	private static final int hitBinSize    = 10000;  // # hits per hit bin;     CAS534 was props
	
	public int idx;
	public String fullname;
	private String chrName;  // prefix removed
	private QueryType mQT;   // AnchorsMain { Query, Target, Either };
	
	private boolean mPreClust = false;
	
	// Created during scan1 of align files (cluster)
	private TreeMap <Integer, AnnoSet> 	annoSetMap;  // list of AnnotElem per b; clusters
	private HashSet <AnnotElem> 		annoElemSet;  // list of AnnotElem (Gene, non-gene, n/a)
	
	// Created during scan2 of align files
	private TreeMap<Integer, Vector<HitBin>> hitBinMap; // list of HitBin per b
	private Vector<HitBin> 					 hitBins;   // list of HitBin
	
	private int topN = 0;
	private int maxGeneLen, avgGeneLen;
	
	public Group(int topN, String name, String fullname, int idx, QueryType qt) {
		this.topN = topN;
		this.chrName = name;
		this.fullname = fullname;
		this.idx = idx;
		this.mQT = qt;
		
		annoElemSet = 	new HashSet <AnnotElem> ();
		annoSetMap = 	new TreeMap<Integer,AnnoSet>();	
		hitBins = 		new Vector<HitBin>();
		hitBinMap = 	new TreeMap<Integer, Vector<HitBin>>();
	}
	
	public String getName() { return chrName; } 		// AnnoLoadPost
	public String getFullName() { return fullname; } 	// CAS512 add for AnnotLoadMain terminal output
	public int getIdx() { return idx; } 				// SyntenyMain
	public String idStr(){ return "" + idx + "(" + chrName + ")";	} // AnchorMain
	
	public String debugInfo() {
		int cnt=0;
		for (HitBin h : hitBins) if (h.getnHits()>0) cnt++;
		String x = String.format("%3s %6s AnnotMap %5d  hitBin %5d/%5d hitBinMap %5d ",
				chrName, mQT.toString(), 
				annoSetMap.size(),    cnt, hitBins.size(),     hitBinMap.size());
		return x;
	}
	
	/************************************************************
	 * ZZZ add exons to can break in reasonable places
	 * Load annotation and create HitBins [loadAnnotation]
	 */
	public int createAnnoHitBins(UpdatePool pool) {
	try {
		String stmtStr = "SELECT start,end,idx FROM pseudo_annot " +
						 "WHERE grp_idx=" + idx + " and type ='gene'"; 
		ResultSet rs = pool.executeQuery(stmtStr);
		
		avgGeneLen = maxGeneLen = 0;
		int totalGenes = 0; 
		
		while (rs.next()){
			int s = rs.getInt(1);
			int e = rs.getInt(2);
			int annotIdx = rs.getInt(3);
			
			AnnotElem ae = new AnnotElem(s, e, GeneType.Gene, annotIdx ); // CAS500 was checking type
			
			totalGenes++;
			int length = (e-s+1);
			avgGeneLen += length;
			maxGeneLen = Math.max(maxGeneLen, length);		
			
			updateAnnotBins(ae);
		}
		rs.close();
		
		if (totalGenes > 0)   avgGeneLen /= totalGenes;
		if (avgGeneLen==0)    avgGeneLen = max_cluster_gap;
		if (maxGeneLen == 0)  maxGeneLen = max_cluster_size;// CAS535 was setGeneParams
		
		return totalGenes;
	}
	catch (Exception e) {ErrorReport.print(e, "load genes and bin them"); return 0;}
	}

	private void updateAnnotBins(AnnotElem a){
		int bs = a.start/annotBinSize; // bs usua
		int be = a.end/annotBinSize;

		annoElemSet.add(a);
		
		for (int b = bs; b <= be; b++) {
			AnnoSet al;
			if (!annoSetMap.containsKey(b) ) {
				al = new AnnoSet();
				annoSetMap.put(b,al);
			}
			else al = annoSetMap.get(b);
			
			al.add(a);
		}
	}
	
	/***********************************************************
	 * scanFile1: [testHitForAnnotOverlapAndUpdate2] removed [testHitForAnnotOverlap]
	 * If it hits a gene, skip it.
	 * If hits a non-gene, either grow that annotation, break it up, or new annotation.
	 */
	public void createNonAnnoHitBins(int start, int end) {
		int newStart = start;
		int newEnd = end;

		int bs = Math.max(0, (newStart - avgGeneLen)/annotBinSize);
		int be = (newEnd + avgGeneLen)/annotBinSize;
						
		TreeSet<AnnotElem> ngOlaps = new TreeSet<AnnotElem>();

		for (int b = bs; b <= be; b++) {
			for (AnnotElem ae : getBinList(b)) {
				int olap = Utils.intervalsOverlap(start,end,ae.start,ae.end);
				if (ae.isGene()){
					if (olap > 0) return; // Hits a gene - no need to inquire further
				}
				else {
					if (olap > -avgGeneLen) {
						ngOlaps.add(ae);	
						newStart = Math.min(newStart,ae.start);
						newEnd =   Math.max(newEnd,ae.end);
					}
				}
			}
		}

		// if it already covers the whole hit then do nothing
		if (ngOlaps.size() == 1 ){
			if (ngOlaps.first().start <= start && ngOlaps.first().end >= end) return;
		}

		// remove all the affected annots
		for (AnnotElem ae : ngOlaps){
			removeAnnotation(ae);
		}
		
		// add one or more new ones spanning the region
		for (int s = newStart; s < newEnd; s += maxGeneLen){
			int e = Math.min(newEnd, s + maxGeneLen - 1);
			AnnotElem ae = new AnnotElem(s,e,GeneType.NonGene,0);
			updateAnnotBins(ae);
		}
	}	
	
	// removed during testHitForAnnotOverlapAndUpdate2
	private void removeAnnotation(AnnotElem a){
		int bs = a.start/annotBinSize;
		int be = a.end/annotBinSize;
		
		annoElemSet.remove(a);
		
		for (int b = bs; b <= be; b++) {
			if (annoSetMap.containsKey(b) ) {
				AnnoSet l = annoSetMap.get(b);
				l.remove(a);
			}
		}
	}	
	/***************************************************************
	 * called after scanFile1 [collectPGInfo]; CAS535 was via SyProj, now direct
	 */
	public void collectHitBinInfo(String proj){
		int gene = 0, ng = 0;
		
		for (AnnotElem ae : annoElemSet){
			if (!ae.isGene()) ng++;	
			else              gene++;	
			
			HitBin hb = new HitBin(ae.start, ae.end, ae.mGT, topN, mQT);
			hitBins.add(hb);
			
			int bs = ae.start/hitBinSize;
			int be = ae.end/hitBinSize;
			
			for (int b = bs; b <= be; b++){
				if (!hitBinMap.containsKey(b)) hitBinMap.put(b, new Vector<HitBin>());
				hitBinMap.get(b).add(hb);
			}		
		}
		BinStats.incStat("AnnotatedGenes_" + proj, gene);
		BinStats.incStat("PredictedGenes_" + proj, ng);
		
		mPreClust = true;
	}
	/*********************************************************
	 * called from AnchorMain while clustering
	 */
	public AnnotElem getMostOverlappingAnnot(int start, int end){
		HashMap<AnnotElem,Integer> annots = getAllOverlappingAnnots(start, end);
		AnnotElem best = null;

		for (AnnotElem cur : annots.keySet()) {
			if (best == null )	{ // first time
				best = cur;
				continue;
			}
			
			if (!cur.isGene()) {
				if (!best.isGene() ){
					if (annots.get(cur) > annots.get(best))  {
						best = cur;
					}
					else if (annots.get(cur) == annots.get(best) ){
						if (cur.getLength() > best.getLength())
							best = cur;
						else if (cur.getLength() == best.getLength()){
							if (cur.start < best.start)
								best = cur;
						}
					}
				}
				continue;
			}
		
			if (best.isGene()) {
				int olapnew = annots.get(cur);
				int olapold = annots.get(best);
				
				if (olapnew > olapold ) {
					best = cur;
				}
				else if (olapnew == olapold ) {
					if (cur.idx < best.idx)
						best = cur;	
				}
				continue;
			}
			
			best = cur;	
		}

		return best;
	}
	
	/***********************************************************
	 * CAS535 two ways of loading, see AnchorsMain
	 */
	public void addAnnotHitOverlaps(Hit h, SubHit sh, UpdatePool conn, HashMap<String,Integer> counts)
	{
	try {
		// Only count one per type so that we get the number of hits hitting 
		// each type, not the total number of annotations which are overlapped. 
		TreeSet<GeneType> usedTypes = new TreeSet<GeneType>(); 
		
		for (AnnotElem ae : getAllOverlappingAnnots(sh.start, sh.end).keySet())
		{
			if (ae.idx == 0) continue;
			
			Vector<String> vals = new Vector<String>();
			vals.add(String.valueOf(h.idx));
			vals.add(String.valueOf(ae.idx));
			vals.add(String.valueOf(Utils.intervalsOverlap(ae.start,ae.end,sh.start,sh.end)));
			
			conn.bulkInsertAdd("pseudo_hits_annot", vals);
			
			if (!usedTypes.contains(ae.mGT)) {
				if (counts.containsKey(ae.mGT.toString()))
					counts.put(ae.mGT.toString(), counts.get(ae.mGT.toString()) + 1);
				else
					counts.put(ae.mGT.toString(),1);
				usedTypes.add(ae.mGT);
			}
		}
	} catch (Exception e) {ErrorReport.print(e, "addAnnotHitOVerlaps"); }
	}
	
	public int [][] getAnnoHitOverlap(Hit h, SubHit sh) throws Exception {
	try {
		// integer value =  Utils.intervalsOverlap(start,end,ae.start,ae.end);
		HashMap<AnnotElem,Integer> vals = getAllOverlappingAnnots(sh.start, sh.end);
		int [][] retVals = new int [vals.size()][3];
		int i=0;
		for (AnnotElem ae : vals.keySet()) {
			retVals[i][0] = h.idx;
			retVals[i][1] = ae.idx;
			retVals[i][2] = vals.get(ae);
			i++;
		}
		return retVals;
	}
	catch (Exception e) {ErrorReport.print(e, "getting annotation overlaps"); throw e; }
	}
	
	private HashMap<AnnotElem,Integer> getAllOverlappingAnnots(int hstart, int hend){
		int bs =   hstart/annotBinSize;
		int be = 1 + hend/annotBinSize;
		
		HashMap<AnnotElem,Integer> retMap = new HashMap<AnnotElem,Integer>();
		
		for (int b = bs; b <= be; b++) {
			for (AnnotElem ae : getBinList(b)) {
				int overlap = Utils.intervalsOverlap(hstart, hend,ae.start,ae.end);
				if (overlap >= 0) {
					if (!retMap.containsKey(ae) || overlap > retMap.get(ae)){
						retMap.put(ae, overlap);
					}
				}
			}
		}
		return retMap;
	}
	// PreFilter hits
	// Called from Project; add to hitBinMap for prefilter step
	// Add to existing hit bin, if not already ruled out by TopN; or, start a new bin
	public void checkAddToHitBin2(Hit hit, SubHit sh) throws Exception {
		int bs = sh.start/hitBinSize;
		int be = sh.end/hitBinSize;

		HitBin hb = null;
		int bestOlap = 0;
		for (int b = bs; b <= be; b++) {
			if (!hitBinMap.containsKey(b)) continue;
			
			for (HitBin hbcheck : hitBinMap.get(b)) {
				int olap = Utils.intervalsOverlap(hbcheck.start, hbcheck.end, sh.start, sh.end);
				if (olap <= 0) continue;
				if (olap > bestOlap){
					hb = hbcheck;
					bestOlap = olap;
				}
				else if (olap == bestOlap){
					if (hbcheck.start < hb.start){
						hb = hbcheck;
						bestOlap = olap;						
					}
					else if (hbcheck.start == hb.start){
						if (hbcheck.end > hb.end){
							hb = hbcheck;
							bestOlap = olap;														
						}
					}
				}
			}
		}
		if (hb == null){
			if (mPreClust) // seq-seq; bins are initialized with annotation
				// If the bins came from annotation, we expect each hit to find at least one
				throw(new Exception("No bin for hit!"));	
		}
		else {
			hb.checkAddHit2(hit, sh); // XXX
			return;
		}
		if (mPreClust) throw(new Exception(""));	
		
		hb = new HitBin(hit,sh,topN,mQT);
		
		hitBins.add(hb);
		for (int b = bs; b <= be; b++){
			if (!hitBinMap.containsKey(b))
				hitBinMap.put(b, new Vector<HitBin>());
			hitBinMap.get(b).add(hb);
		}
	}
	
	public void collectBinStats(BinStats bs){
		bs.mNBins += hitBins.size();
		for (HitBin hb : hitBins){
			bs.mTotalSize += Math.abs(hb.end - hb.start);
			bs.mTotalHits += hb.mHitsConsidered;
			bs.mHitsRm += hb.nHitsRemoved;
		}
	}
	
	// Called from SyProj
	public void filterHits(Set<Hit> hits) throws Exception { // CAS500 Set -> HashSet
		for (HitBin hb : hitBins){
			if (hb.mGT != null) { // null for fpc->seq, !null for seq->seq
				int n = hb.mHitsConsidered;
				if (mQT == QueryType.Query){
					BinStats.incHist("TopNHist1" + hb.mGT.toString(), n, n);
					BinStats.incHist("TopNHistTotal1", n, n);
				}
				else{
					BinStats.incHist("TopNHist2"  + hb.mGT.toString(), n, n);
					BinStats.incHist("TopNHistTotal2", n, n);
				}
			}				
			hb.filterHits(hits);
		}
	}
	/** annotMap methods **/
	
	private HashSet<AnnotElem> getBinList(Integer bin) {
		if (annoSetMap.containsKey(bin))  return annoSetMap.get(bin).mList;
		return new AnnoSet().mList;
	}
	
	private class AnnoSet { // CAS500 was a separate class
		HashSet <AnnotElem> mList;
		
		public AnnoSet() 				 {mList = new HashSet<AnnotElem>();}
		public void add(AnnotElem ae) 	 {mList.add(ae);}
		public void remove(AnnotElem ae) {mList.remove(ae);}	
	}
}
