package backend;

import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

enum RF {R,F};

public class Hit implements Comparable
{
	public int matchLen;
	public int pctid;
	public String strand;
	public String clone = "";
	public RF rf;
	public HitStatus status = HitStatus.Undecided;
	public int idx;
	public SubHit query;
	public SubHit target;
	public int origHits = 0;
	public HitType mBT = null;
	public int annotIdx1 = 0, annotIdx2 = 0;
	public int binsize1=0, binsize2=0; // store topN bin sizes for stats
	public Vector<Integer> annot1List, annot2List;
	
	// COPY CONSTRUCTOR - MAY NEED UPDATE IF MEMBER VARIABLES ARE CHANGED
	public Hit(Hit h)
	{ 
		matchLen = h.matchLen;
		pctid = h.pctid;
		strand = h.strand;
		clone = h.clone;
		rf = h.rf;
		status = h.status;
		idx = h.idx;
		origHits = h.origHits;
		mBT = h.mBT;
		annotIdx1 = h.annotIdx1;
		annotIdx2 = h.annotIdx2;
		binsize1 = h.binsize1;
		binsize2 = h.binsize2;
		
		query = new SubHit(h.query);
		target = new SubHit(h.target);

	}
	public Hit(int numSubBlocks)
	{
		query = new SubHit(numSubBlocks);
		target = new SubHit(numSubBlocks);
		
		// mdb added 8/5/09 #167 - set defaults for merging
		query.start  = Integer.MAX_VALUE;
		query.end    = 0;
		target.start = Integer.MAX_VALUE;
		target.end   = 0;
		matchLen     = 0;
		pctid        = 0;
	}
	
	public Hit() {
		this(0);
	}
	public boolean fullAnnot()
	{
		return (annotIdx1 > 0 && annotIdx2 > 0);
	}
	// mdb added 7/24/09 - for debug
	public String toString() {
		return "idx=" + idx + " status=" + status + " matchLen=" + matchLen + " pctid=" + pctid 
					+ " strand=" + strand 
					+ " qs=" + query.start + " qe=" + query.end + " ts=" + target.start + " te=" + target.end;
					//+ " query.grpIdx=" + query.grpIdx + " target.grpIdx=" + target.grpIdx
					//+ " query=" + query.toString() + " target=" + target.toString();
	}
	
	// mdb added 8/3/09 #167
	public static void sortByQuery(Vector<Hit> hits) {
		Collections.sort(hits, 
			new Comparator<Hit>() {
				public int compare(Hit h1, Hit h2) {
					return h1.query.start - h2.query.start;
				}
			}
		);
	}
	
	// mdb added 8/3/09 #167
	public static void sortByTarget(Vector<Hit> hits) {
		Collections.sort(hits, 
			new Comparator<Hit>() {
				public int compare(Hit h1, Hit h2) {
					return h1.target.start - h2.target.start;
				}
			}
		);
	}
	
	// mdb added 8/3/09 #167
	public boolean isOverlapping(Hit h) {
		return (this.query.isOverlapping(h.query) && this.target.isOverlapping(h.target));
	}
	
	// mdb added 8/18/09 #167
	public boolean sameStrand(Hit h) {
		return (strand == null && h.strand == null) || (strand.equals(h.strand));
	}
	
	// mdb added 8/3/09 #167
	public void merge(Hit h) {
		this.query.start     = Math.min(h.query.start,  this.query.start);
		this.query.end       = Math.max(h.query.end,    this.query.end);
		this.target.start    = Math.min(h.target.start, this.target.start);
		this.target.end      = Math.max(h.target.end,   this.target.end );
		this.query.fileType  = h.query.fileType;
		this.query.grpIdx    = h.query.grpIdx;
		this.target.fileType = h.target.fileType;
		this.target.grpIdx   = h.target.grpIdx;
		this.origHits += h.origHits;
		
		this.matchLen = this.query.end - this.query.start; // WN fixed (this.matchLen == 0 ? h.matchLen : (this.matchLen + h.matchLen) / 2);
		this.pctid = (this.pctid == 0 ? h.pctid : (this.pctid + h.pctid) / 2);
	}
	
	// mdb added 8/3/09 #167
	// WMN not a complete merge b/c doesn't ever merge two clusters
	public static void mergeOverlappingHits(Vector<Hit> hits) {
		if (hits.size() > 1) {
			Vector<Hit> mergedHits = new Vector<Hit>();
			for (Hit h1 : hits) {
				boolean overlap = false;
				for (Hit h2 : mergedHits) {
					// WMN put back the sameStrand test b/c otherwise we can't
					// label the orientation of the hits.
					// show-coords -k should take care of most of the wrong-strand problem
					if ( h1.sameStrand(h2) && h1.isOverlapping(h2) ) { 
						overlap = true;
						h2.merge(h1); 
						break; // WMN what if it hits two?
					}
				}
				if (!overlap)
					mergedHits.add(h1);
			}
			
			if (mergedHits.size() < hits.size()) {
				hits.clear();
				hits.addAll(mergedHits);
			}
		}
	}
	
	// mdb added 8/3/09 #167
	public static Hit clusterHits(Vector<Hit> hits, HitType bt)  throws Exception
	{
		int numHits = hits.size();
		
		if (numHits > 1) {
			Hit h2 = new Hit( numHits*2 );
			h2.origHits = 0;
			
			for (Hit h : hits)
			{
				if (h.origHits > 1)
				{
					//throw(new Exception("" + h.origHits));	
				}
				h2.merge(h);
			}
			
			if (h2.origHits > numHits)
			{
				//throw(new Exception(h2.origHits + " " + numHits));	
			}
						
			h2.matchLen = 0;
			h2.mBT = bt;
			int i = 0;
			long avgPctID = 0;
			h2.origHits = 0;
			// average the pctid, weighted by matchlen
			for (Hit h : hits) {
				h2.query.blocks[i]  = h.query.start;
				h2.target.blocks[i] = h.target.start;
				i++;
				h2.query.blocks[i]  = h.query.end;
				h2.target.blocks[i] = h.target.end;
				i++;
				h2.matchLen += h.matchLen;
				avgPctID += h.pctid*h.matchLen;
				h2.origHits += h.origHits;
			}
			
			h2.pctid = (int)(avgPctID/h2.matchLen);
			h2.strand = hits.get(0).strand; 
			
			if (h2.origHits != numHits)
			{
				//throw(new Exception(h2.origHits + " " + numHits));	
			}
			
			return h2;
		}
		else if (numHits == 1)
		{
			hits.firstElement().mBT = bt;
			if (hits.firstElement().origHits != 1)
			{
				//throw(new Exception("" + hits.firstElement().origHits ));	
			}
			return hits.firstElement();
		}
		
		return null;
	}
	public boolean reversed()
	{
		return ( ((query.end < query.start) && (target.end > target.start)) ||
				((query.end > query.start) && (target.end < target.start)) );
	}
	public void orderEnds()
	{
		int s = query.start; int e = query.end;
		query.start  = (s <= e ? s : e); 
		query.end    = (s <= e ? e : s); 
		s = target.start; e = target.end;
		target.start  = (s <= e ? s : e); 
		target.end    = (s <= e ? e : s); 
	}
	public int compareTo(Object _h2)
	{
		Hit h2 = (Hit)_h2;	
		if (query.start < h2.query.start)
		{
			return 1;	
		}
		else if (query.start > h2.query.start)
		{
			return -1;	
		}
		if (target.start < h2.target.start)
		{
			return 1;	
		}
		else if (target.start > h2.target.start)
		{
			return -1;	
		}	
		return 0;
	}
	public int maxLength()
	{
		return Math.max(query.length(), target.length());
	}
}
