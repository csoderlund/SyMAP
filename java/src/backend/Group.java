package backend;
/*
 * loads all the group annotation from DB (group is chromosome, linkage group, etc)
 * does binning for type 'seq' 
 * 
 * CAS500 removed lots of dead code including 
 * 	annotInclude, annotExclude, keepFamilies, AnnotAction 
 */

import java.util.Set;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.Vector;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Group 
{
	public int idx;
	public String fullname;
	private String name;// prefix removed
	private QueryType qType;
	
	private boolean mPreClust = false;
	
	// Created during scan1 of align files (cluster)
	private TreeMap<Integer,AnnotList> 		annotMap;  // list of AnnotElem per b; clusters
	private HashSet<AnnotElem> 				allAnnot;  // list of AnnotElem (Gene, non-gene, n/a)
	
	// Created during scan2 of align files
	private TreeMap<Integer,Vector<HitBin>> 	hitBinMap; // list of HitBin per b
	private Vector<HitBin> 					hitBins;   // list of HitBin
	
	private int hitBinSize, annotBinSize, topN = 0;
	private int avgGeneLength, minGeneLength, maxGeneLength, maxGap;
	
	public Group(SyProps props, String name, String fullname, int idx, QueryType qt)
	{
		this.name = name;
		this.fullname = fullname;
		this.idx = idx;
		this.qType = qt;
		
		allAnnot = 	new HashSet <AnnotElem> ();
		annotMap = 	new TreeMap<Integer,AnnotList>();	
		hitBins = 	new Vector<HitBin>();
		hitBinMap = 	new TreeMap<Integer,Vector<HitBin>>();
		
		if (qt != QueryType.Either)
		{
			hitBinSize = 	props.getInt("hit_bin");  	// 10000 - SyProps
			annotBinSize = 	props.getInt("annot_bin"); 	// 30000 - SyProps
			topN = 			props.getInt("topn"); 		// default 2; change in interface
		}
	}
	
	public String getName() { return name; }
	public int getAvgGeneLength() { return avgGeneLength; }
	public int getMinGeneLength() { return minGeneLength; }
	public int getMaxGeneLength() { return maxGeneLength; }
	public int getIdx() { return idx; }
	
	public int getnClusters() { return annotMap.size();}
	public String info() {
		int cnt=0;
		for (HitBin h : hitBins) if (h.getnHits()>0) cnt++;
		String x = String.format("%3s %6s AnnotMap %5d  hitBin %5d/%5d hitBinMap %5d",
				name, qType.toString(), annotMap.size(), cnt, hitBins.size(), hitBinMap.size());
		return x;
	}
	// created during loadAnnotation and added to during testHitForAnnotOverlapAndUpdate2
	private void updateAnnotBins(AnnotElem a)
	{
		int bs = a.start/annotBinSize;
		int be = a.end/annotBinSize;
		
		allAnnot.add(a);
		
		for (int b = bs; b <= be; b++) 
		{
			AnnotList l;
			if (!annotMap.containsKey(b) ) {
				l = new AnnotList();
				annotMap.put(b,l);
			}
			else l = annotMap.get(b);
			
			l.add(a);
		}
	}
	// removed during testHitForAnnotOverlapAndUpdate2
	private void removeAnnotation(AnnotElem a)
	{
		int bs = a.start/annotBinSize;
		int be = a.end/annotBinSize;
		
		allAnnot.remove(a);
		
		for (int b = bs; b <= be; b++) 
		{
			if (annotMap.containsKey(b) ) {
				AnnotList l = annotMap.get(b);
				l.remove(a);
			}
		}
	}	
	
	public void collectPGInfo(String proj)
	{
		int gene = 0, ng = 0;
		
		for (AnnotElem ae : allAnnot)
		{
			if (!ae.isGene()) ng++;	
			else gene++;	
			
			HitBin hb = new HitBin(ae.start, ae.end, ae.mGT, topN, qType);
			hitBins.add(hb);
			
			int bs = ae.start/hitBinSize;
			int be = ae.end/hitBinSize;
			
			for (int b = bs; b <= be; b++)
			{
				if (!hitBinMap.containsKey(b))
					hitBinMap.put(b, new Vector<HitBin>());
				hitBinMap.get(b).add(hb);
			}		
		}
		Utils.incStat("AnnotatedGenes_" + proj, gene);
		Utils.incStat("PredictedGenes_" + proj, ng);
		
		mPreClust = true;
	}
	
	public int loadAnnotation(UpdatePool pool,
					TreeMap<Integer,Integer> annotIdx2GrpIdx,
					TreeMap<Integer,AnnotElem> annotIdx2Elem
				) throws SQLException
	{
		String stmtStr = "SELECT start,end,idx FROM pseudo_annot " +
						 "WHERE grp_idx=" + idx + " and type ='gene'"; // we don't use anything else
		ResultSet rs = pool.executeQuery(stmtStr);
		
		avgGeneLength = maxGeneLength = 0;
		minGeneLength  = Integer.MAX_VALUE;
		int totalGenes = 0; 
		
		while (rs.next())
		{
			int s = rs.getInt(1);
			int e = rs.getInt(2);
			int annotIdx = rs.getInt(3);
			
			AnnotElem ae = new AnnotElem(s,e, GeneType.Gene,annotIdx ); // CAS500 was checking type
			annotIdx2GrpIdx.put(annotIdx,idx);
			annotIdx2Elem.put(annotIdx,ae);
					
			totalGenes++;
			int length = (e-s+1);
			avgGeneLength += length;
			minGeneLength = Math.min(minGeneLength, length);
			maxGeneLength = Math.max(maxGeneLength, length);		
			
			updateAnnotBins(ae);
		}
		rs.close();
		
		if (totalGenes > 0) 
			avgGeneLength /= totalGenes;
		if (minGeneLength == Integer.MAX_VALUE)
			minGeneLength = 0;
		return totalGenes;
	}
	
	
	private HashMap<AnnotElem,Integer> getAllOverlappingAnnots(int start, int end)
	{
		int bs = start/annotBinSize;
		int be = 1 + end/annotBinSize;
		
		HashMap<AnnotElem,Integer> ret = new HashMap<AnnotElem,Integer>();
		
		for (int b = bs; b <= be; b++) 
		{
			for (AnnotElem ae : getBinList(b)) 
			{
				int overlap = Utils.intervalsOverlap(start,end,ae.start,ae.end);
				if (overlap >= 0) 
				{
					if (!ret.containsKey(ae) || overlap > ret.get(ae))
					{
						ret.put(ae, overlap);
					}
				}
			}
		}
		
		return ret;
	}
	public String idStr()
	{
		return "" + idx + "(" + name + ")";	
	}
	//called from AnchorMain while clustering
	public AnnotElem getMostOverlappingAnnot(int start, int end)
	{
		HashMap<AnnotElem,Integer> annots = getAllOverlappingAnnots(start, end);
		AnnotElem ret = null;

		for (AnnotElem a : annots.keySet()) 
		{
			// prefer merging with real genes
			if (ret == null )					
				ret = a;
			else 
			{
				if (!a.isGene()) 
				{
					if (!ret.isGene() )
					{
						if (annots.get(a) > annots.get(ret))
							ret = a;
						else if (annots.get(a) == annots.get(ret) )
						{
							if (a.getLength() > ret.getLength())
								ret = a;
							else if (a.getLength() == ret.getLength())
							{
								if (a.start < ret.start)
									ret = a;
							}
						}
					}
				}
				else // we're looking at a real gene
				{
					if (ret.isGene()) 
					{
						int olapnew = annots.get(a);
						int olapold = annots.get(ret);
						
						if (olapnew > olapold )
							ret = a;
						else if (olapnew == olapold ) {
							if (a.idx < ret.idx)
								ret = a;	
						}
					}
					else {
						ret = a;	
					}
				}
			}
		}

		return ret;
	}
	
	public boolean testHitForAnnotOverlap(SubHit sh) {
		return testHitForAnnotOverlap(sh.start, sh.end);
	}
	
	private boolean testHitForAnnotOverlap(int start, int end) {
		int bs = start/annotBinSize;
		int be = 1 + end/annotBinSize;
		
		for (int b = bs; b <= be; b++) {
			for (AnnotElem ae : getBinList(b)) {
				if (Utils.intervalsTouch(start,end,ae.start,ae.end))
					return true;
			}
		}
		
		return false;
	}
	
	// If it hits a gene, skip it. 
	// If hits a non-gene, either grow that annotation or break it up.
	// Otherwise, make a new annotation.
	public void testHitForAnnotOverlapAndUpdate2(int start, int end) 
	{
		int newStart = start;
		int newEnd = end;

		int bs = Math.max(0,(newStart - maxGap)/annotBinSize);
		int be = (newEnd + maxGap)/annotBinSize;
						
		TreeSet<AnnotElem> ngOlaps = new TreeSet<AnnotElem>();

		for (int b = bs; b <= be; b++) 
		{
			for (AnnotElem ae : getBinList(b)) 
			{
				int olap = Utils.intervalsOverlap(start,end,ae.start,ae.end);
				if (ae.isGene())
				{
					if (olap > 0)
						return; // Hits a gene - no need to inquire further
				}
				else
				{
					if (olap > -maxGap)
					{
						ngOlaps.add(ae);	
						newStart = Math.min(newStart,ae.start);
						newEnd = Math.max(newEnd,ae.end);
					}
				}
			}
		}

		// if it already covers the whole hit then do nothing
		if (ngOlaps.size() == 1 )
		{
			if (ngOlaps.first().start <= start && ngOlaps.first().end >= end) return;
		}

		// remove all the affected annots
		for (AnnotElem ae : ngOlaps)
		{
			removeAnnotation(ae);
		}
		
		// add one or more new ones spanning the region
		for (int s = newStart; s < newEnd; s += maxGeneLength)
		{
			int e = Math.min(newEnd, s + maxGeneLength - 1);
			AnnotElem ae = new AnnotElem(s,e,GeneType.NonGene,0);
			updateAnnotBins(ae);
		}
	}	
	
	public void addAnnotHitOverlaps(Hit h, SubHit sh, UpdatePool conn, HashMap<String,Integer> counts) throws SQLException
	{
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
			if (!usedTypes.contains(ae.mGT))
			{
				if (counts.containsKey(ae.mGT.toString()))
					counts.put(ae.mGT.toString(), counts.get(ae.mGT.toString()) + 1);
				else
					counts.put(ae.mGT.toString(),1);
				usedTypes.add(ae.mGT);
			}
		}
	}
	
	// PreFilter hits
	// Called from Project; add to hitBinMap for prefilter step
	// Add to existing hit bin, if not already ruled out by TopN; or, start a new bin
	public void checkAddToHitBin2(Hit hit, SubHit sh) throws Exception
	{
		int bs = sh.start/hitBinSize;
		int be = sh.end/hitBinSize;

		HitBin hb = null;
		int bestOlap = 0;
		for (int b = bs; b <= be; b++) 
		{
			if (!hitBinMap.containsKey(b)) continue;
			
			for (HitBin hbcheck : hitBinMap.get(b)) 
			{
				int olap = Utils.intervalsOverlap(hbcheck.start, hbcheck.end, sh.start, sh.end);
				if (olap <= 0) continue;
				if (olap > bestOlap)
				{
					hb = hbcheck;
					bestOlap = olap;
				}
				else if (olap == bestOlap)
				{
					if (hbcheck.start < hb.start)
					{
						hb = hbcheck;
						bestOlap = olap;						
					}
					else if (hbcheck.start == hb.start)
					{
						if (hbcheck.end > hb.end)
						{
							hb = hbcheck;
							bestOlap = olap;														
						}
					}
				}
			}
		}
		if (hb == null)
		{
			if (mPreClust) // seq-seq; bins are initialized with annotation
				// If the bins came from annotation, we expect each hit to find at least one
				throw(new Exception("No bin for hit!"));	
		}
		else
		{
			hb.checkAddHit2(hit, sh); // XXX
			return;
		}
		if (mPreClust) throw(new Exception(""));	
		
		hb = new HitBin(hit,sh,topN,qType);
		
		hitBins.add(hb);
		for (int b = bs; b <= be; b++)
		{
			if (!hitBinMap.containsKey(b))
				hitBinMap.put(b, new Vector<HitBin>());
			hitBinMap.get(b).add(hb);
		}
	}
	public void setGeneParams(int maxGapDef, int maxGeneDef)
	{
		if (avgGeneLength > 0) maxGap = avgGeneLength;
		else maxGap = maxGapDef;
		
		if (maxGeneLength == 0) maxGeneLength = maxGeneDef;
	}
	public void collectBinStats(BinStats bs)
	{
		bs.mNBins += hitBins.size();
		for (HitBin hb : hitBins)
		{
			bs.mTotalSize += Math.abs(hb.end - hb.start);
			bs.mTotalHits += hb.mHitsConsidered;
			bs.mHitsRm += hb.nHitsRemoved;
		}
	}
	
	// Called from Project for SEQ
	public void filterHits(Set<Hit> hits) throws Exception // CAS500 Set -> HashSet
	{
		for (HitBin hb : hitBins)
		{
			if (hb.mGT != null) // null for fpc->seq, !null for seq->seq
			{
				int n = hb.mHitsConsidered;
				if (qType == QueryType.Query)
				{
					Utils.incHist("TopNHist1" + hb.mGT.toString(), n, n);
					Utils.incHist("TopNHistTotal1", n, n);
				}
				else
				{
					Utils.incHist("TopNHist2"  + hb.mGT.toString(), n, n);
					Utils.incHist("TopNHistTotal2", n, n);
				}
			}				
			hb.filterHits(hits);
		}
	}
	/** annotMap methods **/
	
	private HashSet<AnnotElem> getBinList(Integer bin) {
		if (annotMap.containsKey(bin))  return annotMap.get(bin).mList;
		return new AnnotList().mList;
	}
	
	private class AnnotList { // CAS500 was a separate class
		HashSet<AnnotElem> mList;
		
		public AnnotList() 				{mList = new HashSet<AnnotElem>();}
		public void add(AnnotElem ae) 	{mList.add(ae);}
		public void remove(AnnotElem ae) {mList.remove(ae);}	
	}
}
