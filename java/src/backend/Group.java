package backend;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;
import java.util.Collection;
import java.sql.ResultSet;
import java.sql.SQLException;

import util.Utilities;

/*
 * This group class is heavy weight, e.g. loads all the group annotation
 */
public class Group 
{
	public String name;// prefix removed
	public String fullname; 
	public int idx;
	public HashSet<AnnotElem> allAnnot;
	private Map<AnnotAction,AnnotHash> annotByAction;	
	private TreeMap<Integer,Vector<HitBin>> hitBinMap;
	private Vector<HitBin> hitBins;
	private int hitBinSize;
	private int annotBinSize;
	private int maxWindow;
	private int topN = 0;
	//private int topNGene = 0; // mdb removed 12/13/09
	private QueryType qType;
	private int avgGeneLength; // mdb added 7/31/09 #167
	private int minGeneLength; // mdb added 7/31/09 #167
	private int maxGeneLength; // mdb added 7/31/09 #167
	private int maxGap;
	public static int annotIdx = 0; // unique id for the nongene clusters
	public boolean mPreClust = false;
	
	public Group(SyProps props, String name, String fullname, int idx, QueryType qt)
	{
		this.name = name;
		this.fullname = fullname;
		this.idx = idx;
		this.qType = qt;
		annotByAction = new TreeMap<AnnotAction,AnnotHash>();
		annotByAction.put(AnnotAction.Include, new AnnotHash());
		annotByAction.put(AnnotAction.Exclude, new AnnotHash());
		annotByAction.put(AnnotAction.Mark, new AnnotHash());
		hitBins = new Vector<HitBin>();
		hitBinMap = new TreeMap<Integer,Vector<HitBin>>();
		allAnnot = new HashSet<AnnotElem>();
		
		if (qt != QueryType.Either)
		{
			hitBinSize = props.getInt("hit_bin");
			annotBinSize = props.getInt("annot_bin"); // mdb added 8/3/09
			maxWindow = props.getInt("topn_maxwindow");
			
// mdb removed 12/13/09			
//			mTopN = (qt == QueryType.Query ? mProps.getInt("query_topn") : 
//											 mProps.getInt("target_topn") );
//			mTopNGene = (qt == QueryType.Query ?
//								mProps.getInt("query_topn_gene") : 
//								mProps.getInt("target_topn_gene") );

			// mdb added 12/13/09
			topN = props.getInt("topn");
		}
	}
	
	// mdb added 8/3/09
	public String getName() { return name; }
	public int getAvgGeneLength() { return avgGeneLength; }
	public int getMinGeneLength() { return minGeneLength; }
	public int getMaxGeneLength() { return maxGeneLength; }
	public int getIdx() { return idx; }
	
	// mdb added 7/31/09 #167
	public void addAnnotations(AnnotAction action, Vector<AnnotElem> newAnnots) 
	{
		for (AnnotElem a : newAnnots) 
		{
			updateAnnotBins(a, action);

		}
	}
	public void updateAnnotBins(AnnotElem a, AnnotAction action)
	{
		int bs = a.start/annotBinSize;
		int be = a.end/annotBinSize;
		
		allAnnot.add(a);
		
		for (int b = bs; b <= be; b++) 
		{
			if (!annotByAction.get(action).containsKey(b) )
			{
				annotByAction.get(action).initializeKey(b);
			}
			annotByAction.get(action).add(b,a);
		}
		
	}
	public void removeAnnotation(AnnotElem a, AnnotAction action)
	{
		int bs = a.start/annotBinSize;
		int be = a.end/annotBinSize;
		
		allAnnot.remove(a);
		
		for (int b = bs; b <= be; b++) 
		{
			if (annotByAction.get(action).containsKey(b) )
			{
				annotByAction.get(action).get(b).remove(a);
			}
		}
		
	}	
	// collect, and/or print, the real gene/predicted gene counts
	public void collectPGInfo(String proj)
	{
		int gene = 0;
		int ng = 0;
		Vector<String> output = new Vector<String>();
		for (AnnotElem a : allAnnot)
		{
			if (!a.isGene())
			{
				ng++;	
			}
			else
			{
				gene++;	
			}
//			if (!a.isGene())
//			{
//				output.add(idStr() + " nongeneclust " + a.start + ":" + a.end);
//			}
//			else
//			{
//				output.add(idStr() + " geneclust " + a.start + ":" + a.end);	
//			}
		}
//		Collections.sort(output);
//		for (String s : output)
//		{
//			System.out.println(s);
//		}

//		System.out.println(idStr() + " clusters gene:" + gene + " ng:" + ng);

		Utils.incStat("AnnotatedGenes_" + proj, gene);
		Utils.incStat("PredictedGenes_" + proj, ng);
		
		mPreClust = true;
		hitBinsFromAnnotation();
	}

	
	public void loadAnnotation(UpdatePool pool,
					TreeMap<String,AnnotAction> annotSpec, 
					TreeMap<String,Integer> annotData, 
					TreeMap<Integer,Integer> annotIdx2GrpIdx,
					TreeMap<Integer,AnnotElem> annotIdx2Elem
					//TreeMap<String,Integer> geneName2AnnotIdx // mdb removed 8/12/09 - unused
				) throws SQLException
	{
		String stmtStr = "SELECT type,start,end,idx " +
						 "FROM pseudo_annot " +
						 "WHERE grp_idx=" + idx + " and type ='gene'"; // we don't use anything else
		ResultSet rs = pool.executeQuery(stmtStr);
		
		// mdb added 7/31/09 #167
		avgGeneLength  = 0;
		minGeneLength  = Integer.MAX_VALUE;
		maxGeneLength  = 0;
		int totalGenes = 0; 
		
		while (rs.next())
		{
			String type = rs.getString("type").intern(); // mdb added "intern" 8/14/09 - reduce memory usage
			int s = rs.getInt("start");
			int e = rs.getInt("end");
			int annotIdx = rs.getInt("idx");
			AnnotElem ae = new AnnotElem(s,e,(type.equals("gene") ? GeneType.Gene : GeneType.NonGene),annotIdx ); 
			annotIdx2GrpIdx.put(annotIdx,idx);
			annotIdx2Elem.put(annotIdx,ae);
			
			if (type.equals("gene"))
			{  				
				totalGenes++;
				int length = (e-s+1);
				avgGeneLength += length;
				minGeneLength = Math.min(minGeneLength, length);
				maxGeneLength = Math.max(maxGeneLength, length);
			}
			
			
			if (annotSpec.containsKey(type))
			{
				incAnnotDataCount(annotData,type); 
				
				AnnotAction atype = annotSpec.get(type);
				updateAnnotBins(ae, atype);

			}
			// This is set up to mark all hits overlapping any kind of annotation.
			// However, this is pretty slow.
			// TODO: revisit what to do about this in 4.0. 
			// For the moment all we use is the gene_olap field anyway.
// mdb removed 7/29/09 #167	- now using "annot_mark1/2" in props
//			if (true) {
//				for (int b = bs; b <= be; b++) {
//					if (!mAnnotByType.get(AnnotAction.Mark).containsKey(b) )
//						mAnnotByType.get(AnnotAction.Mark).initializeKey(b);
//					mAnnotByType.get(AnnotAction.Mark).add(b,ae);
//				}
//			}
		}
		rs.close();
		
		if (totalGenes > 0) 
			avgGeneLength /= totalGenes;
		if (minGeneLength == Integer.MAX_VALUE)
			minGeneLength = 0;
	}
	
	private void incAnnotDataCount(TreeMap<String,Integer> AnnotData, String type)
	{
		if (!AnnotData.containsKey(type))
			AnnotData.put(type, 0);
		AnnotData.put(type,AnnotData.get(type) + 1);
	}
	
	public boolean checkAnnotHash(AnnotAction aa, int bin)
	{
		if (annotByAction.containsKey(aa))
			return annotByAction.get(aa).checkBin(bin);
		return false;
	}
	
	public HashSet<AnnotElem> getBinList(AnnotAction aa, int bin)
	{
		return annotByAction.get(aa).getBinList(bin).mList;
	}
	
	public HashMap<AnnotElem,Integer> getAllOverlappingAnnots(int start, int end)
	{
		int bs = start/annotBinSize;
		int be = 1 + end/annotBinSize;
		
		HashMap<AnnotElem,Integer> ret = new HashMap<AnnotElem,Integer>();
		
		for (int b = bs; b <= be; b++) 
		{
			for (AnnotElem ae : getBinList(AnnotAction.Mark,b)) 
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
	public AnnotElem getMostOverlappingAnnot(int start, int end)
	{
		HashMap<AnnotElem,Integer> annots = getAllOverlappingAnnots(start, end);
		AnnotElem ret = null;

		for (AnnotElem a : annots.keySet()) 
		{
			// WMN changes to prefer merging with real genes
			if (ret == null )					
			{
				ret = a;
			}
			else 
			{
				if (!a.isGene()) 
				{
					if (!ret.isGene() )
					{
						if (annots.get(a) > annots.get(ret))
						{
							ret = a;
						}
						else if (annots.get(a) == annots.get(ret) )
						{
							if (a.getLength() > ret.getLength())
							{
								ret = a;
							}
							else if (a.getLength() == ret.getLength())
							{
								if (a.start < ret.start)
								{
									ret = a;
								}	
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
						{
							ret = a;
						}
						else if (olapnew == olapold )
						{
							if (a.idx < ret.idx)
							{
								ret = a;	
							}
//							if (a.getLength() > ret.getLength())
//							{
//								ret = a;
//							}
//							else if (a.getLength() == ret.getLength())
//							{
//								if (a.start < ret.start)	
//								{
//									ret = a;	
//								}
//							}
						}
					}
					else
					{
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
	
	public boolean testHitForAnnotOverlap(int start, int end) {
		int bs = start/annotBinSize;
		int be = 1 + end/annotBinSize;
		
		for (int b = bs; b <= be; b++) {
			for (AnnotElem ae : getBinList(AnnotAction.Mark,b)) {
				if (Utils.intervalsTouch(start,end,ae.start,ae.end))
					return true;
			}
		}
		
		return false;
	}
	// If it hits a gene, skip it. 
	// If hits a non-gene, either grow that annotation or break it up.
	// Otherwise, make a new annotation.
	public void testHitForAnnotOverlapAndUpdate(int start, int end, int maxLen, int maxGap) 
	{
		int newStart = start;
		int newEnd = end;

		int bs = Math.max(0,(newStart - maxGap)/annotBinSize);
		int be = (newEnd + maxGap)/annotBinSize;
		
				
		TreeSet<AnnotElem> ngOlaps = new TreeSet<AnnotElem>();

		for (int b = bs; b <= be; b++) 
		{
			for (AnnotElem ae : getBinList(AnnotAction.Mark,b)) 
			{
				int olap = Utils.intervalsOverlap(start,end,ae.start,ae.end);
				if (ae.isGene())
				{
					if (olap > 0)
					{
						return; // Hits a gene - no need to inquire further
					}
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

		//System.out.println(idStr() + ":add hit start:" + start + " end:" + end + " ngolaps:" + ngOlaps.size());

		// if it already covers the whole hit then do nothing
		if (ngOlaps.size() == 1 )
		{
			if (ngOlaps.first().start <= start && ngOlaps.first().end >= end) return;
		}

		// remove all the affected annots
		for (AnnotElem ae : ngOlaps)
		{
			//System.out.println(idStr() + ":remove annot " + ae.mID + " start:" + ae.start + " end:" + ae.end);
			removeAnnotation(ae,AnnotAction.Mark);
		}
		
		// add one or more new ones spanning the region

		for (int s = newStart; s < newEnd; s += maxLen)
		{
			int e = Math.min(newEnd, s + maxLen - 1);
			AnnotElem ae = new AnnotElem(s,e,GeneType.NonGene,0);
			//System.out.println(idStr() + ":new annot " + ae.mID + " start:" + ae.start + " end:" + ae.end);			
			updateAnnotBins(ae,AnnotAction.Mark);
		}
		
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
			for (AnnotElem ae : getBinList(AnnotAction.Mark,b)) 
			{
				int olap = Utils.intervalsOverlap(start,end,ae.start,ae.end);
				if (ae.isGene())
				{
					if (olap > 0)
					{
						return; // Hits a gene - no need to inquire further
					}
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

		//System.out.println(idStr() + ":add hit start:" + start + " end:" + end + " ngolaps:" + ngOlaps.size());

		// if it already covers the whole hit then do nothing
		if (ngOlaps.size() == 1 )
		{
			if (ngOlaps.first().start <= start && ngOlaps.first().end >= end) return;
		}

		// remove all the affected annots
		for (AnnotElem ae : ngOlaps)
		{
			//System.out.println(idStr() + ":remove annot " + ae.mID + " start:" + ae.start + " end:" + ae.end);
			removeAnnotation(ae,AnnotAction.Mark);
		}
		
		// add one or more new ones spanning the region

		for (int s = newStart; s < newEnd; s += maxGeneLength)
		{
			int e = Math.min(newEnd, s + maxGeneLength - 1);
			AnnotElem ae = new AnnotElem(s,e,GeneType.NonGene,0);
			//System.out.println(idStr() + ":new annot " + ae.mID + " start:" + ae.start + " end:" + ae.end);			
			updateAnnotBins(ae,AnnotAction.Mark);
		}
		
	}	
	// mdb added 8/3/09
	public boolean testHitForAnnotContainment(SubHit sh, int pad) {
		int bs = sh.start/annotBinSize;
		int be = sh.end/annotBinSize;
		
		for (int b = bs; b <= be; b++) {
			for (AnnotElem ae : getBinList(AnnotAction.Mark,b)) {
				if (Utilities.isContained(sh.start,sh.end,ae.start-pad,ae.end+pad))
					return true;
			}
		}
		
		return false;
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
	public void hitBinsFromAnnotation()
	{
		for (AnnotElem ae : allAnnot)
		{
			HitBin hb = new HitBin(ae, topN, qType);
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
		mPreClust = true;
	}
	public void checkAddToHitBin(Hit hit, SubHit sh) throws Exception
	{
		// Add to existing hit bin, if not already ruled out by TopN; or, start a new bin
		
		int bs = sh.start/hitBinSize;
		int be = sh.end/hitBinSize;

		HitBin hb = null;
		int bestOlap = 0;
		for (int b = bs; b <= be; b++) 
		{
			if (hitBinMap.containsKey(b)) 
			{
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
		}
		if (hb == null)
		{
			if (mPreClust)
			{
				// If the bins came from annotation, we expect each hit to find at least one
				throw(new Exception("No bin for hit!"));	
			}
		}
		else
		{
			hb.checkAddHit(hit, sh);
			//hb.addHit(hit, sh);
			return;
		}
		if (mPreClust) throw(new Exception(""));	
		

		hb = new HitBin(hit,sh,topN,qType);
		hb.mHitsConsidered = 1;
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
		}
	}
	
	public void filterHits(Set<Hit> hits, boolean keepFamilies) throws Exception
	{
		int totalHits = 0;
		for (HitBin hb : hitBins)
		{
			if (hb.mGT != null)
			{
				if (qType == QueryType.Query)
				{
					Utils.incHist("TopNHist1" + hb.mGT.toString(), hb.mHitsConsidered, hb.mHitsConsidered);
					Utils.incHist("TopNHistTotal1", hb.mHitsConsidered,hb.mHitsConsidered);
				}
				else
				{
					Utils.incHist("TopNHist2"  + hb.mGT.toString(), hb.mHitsConsidered, hb.mHitsConsidered);
					Utils.incHist("TopNHistTotal2", hb.mHitsConsidered,hb.mHitsConsidered);
				}
			}				
			hb.filterHits(hits, keepFamilies);
		}

	}
	
	/* 
	 * This class stores a map of sequence bins to annotation, enabling
	 * fast lookup of annotations which overlap hits. 
	 */
	private class AnnotHash
	{
		TreeMap<Integer,AnnotList> mAnnotMap;
		
		public AnnotHash()
		{
			mAnnotMap = new TreeMap<Integer,AnnotList>();	
		}
		
		public boolean containsKey(Integer key)
		{
			return mAnnotMap.containsKey(key);
		}
		
		public AnnotList get(Integer key)
		{
			return mAnnotMap.get(key);
		}
		
		public void initializeKey(Integer key)
		{
			AnnotList l = new AnnotList();
			mAnnotMap.put(key,l);
		}
		
		public void add(Integer key, AnnotElem ae)
		{
			get(key).add(ae);
		}
		
		public boolean checkBin(Integer b)
		{
			return mAnnotMap.containsKey(b);
		}
		
		public AnnotList getBinList(Integer b)
		{
			if (mAnnotMap.containsKey(b))
				return mAnnotMap.get(b);
			return new AnnotList();
		}
		public Set<Integer> keySet()
		{
			return mAnnotMap.keySet();	
		}
		
		// mdb added 9/3/09 #167
		public Collection<AnnotList> values() {
			return mAnnotMap.values();
		}
		
		public int size() { return mAnnotMap.size(); }
	}
}
