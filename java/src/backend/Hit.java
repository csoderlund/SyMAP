package backend;

import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

/*****************************************************
 * Used for AnchorsMain clustering
 */
public class Hit implements Comparable <Hit> {// CAS500 added <Hit>
	public int matchLen, pctid, pctsim, idx; // pctid=%ID, pctsim=%sim
	public String strand;
	public HitStatus status = HitStatus.Undecided;	// AnchorsMain {In, Out, Undecided };
	public SubHit query, target;
	public HitType mHT = null;				// AnchorsMain {GeneGene, GeneNonGene, NonGene}
	public int annotIdx1 = 0, annotIdx2 = 0;
	public int origHits = 0; 
	public int binsize1=0, binsize2=0; // store topN bin sizes for stats
	
	// Called in AnchorMain, then used with merge()
	public Hit() {this(0);}
	
	public Hit(int subblocks) {
		query =  new SubHit(subblocks);
		target = new SubHit(subblocks);
		
		// set defaults for merging
		query.start  = Integer.MAX_VALUE;
		query.end    = 0;
		target.start = Integer.MAX_VALUE;
		target.end   = 0;
		matchLen     = 0;
		pctid        = 0;
		pctsim		 = 0;
	}
	// Used by Break hit 
	public Hit(Hit h, int qstart, int qend, int tstart, int tend, int tmp_idx) { 
		matchLen = h.matchLen;
		pctid = h.pctid;
		pctsim = h.pctsim; // CAS515 add
		strand = h.strand;
		status = h.status;
		idx 	= h.idx;
		origHits = h.origHits;
		mHT = h.mHT;
		annotIdx1 = h.annotIdx1;
		annotIdx2 = h.annotIdx2;
		binsize1 = h.binsize1;
		binsize2 = h.binsize2;
		
		query =  new SubHit(h.query);
		target = new SubHit(h.target);
		query.start = qstart;
		query.end = qend;
		target.start = tstart;
		target.end = tend;
		idx = tmp_idx; 
	}
	
	public boolean fullAnnot() {
		return (annotIdx1 > 0 && annotIdx2 > 0);
	}
	public boolean reversed() {
		return (((query.end < query.start) && (target.end > target.start)) ||
				((query.end > query.start) && (target.end < target.start)) );
	}
	public void orderEnds() {
		int s = query.start; int e = query.end;
		query.start  = (s <= e ? s : e); 
		query.end    = (s <= e ? e : s); 
		
		s = target.start; e = target.end;
		target.start  = (s <= e ? s : e); 
		target.end    = (s <= e ? e : s); 
	}
	public int compareTo(Hit h2) {// CAS500 changed Object to Hit
		if      (query.start < h2.query.start) 	return 1;	
		else if (query.start > h2.query.start)	return -1;
	
		if      (target.start < h2.target.start)	return 1;
		else if (target.start > h2.target.start)	return -1;
	
		return 0;
	}
	public int maxLength() {
		return Math.max(query.length(), target.length());
	}
	public boolean isDiagHit() { // CAS535 add: was doing full check in multiple places
		return query.grpIdx == target.grpIdx && query.start == target.start && query.end == target.end;
	}
	// for debug
	public String toString() {
		return "idx=" + idx + " status=" + status + " maxMatch=" + matchLen + " pctid=" + pctid 
				+ "pctsim=" + pctsim + " strand=" + strand 
				+ " qs=" + query.start + " qe=" + query.end + " ts=" + target.start + " te=" + target.end;
	}
	
	public static void sortByQuery(Vector<Hit> hits) {
		Collections.sort(hits, 
			new Comparator<Hit>() {
				public int compare(Hit h1, Hit h2) {
					return h1.query.start - h2.query.start;
				}
			}
		);
	}
	
	public static void sortByTarget(Vector<Hit> hits) {
		Collections.sort(hits, 
			new Comparator<Hit>() {
				public int compare(Hit h1, Hit h2) {
					return h1.target.start - h2.target.start;
				}
			}
		);
	}
	
	/**************************************************************/
	private boolean isOverlapping(Hit h) {
		return (this.query.isOverlapping(h.query) && 
				this.target.isOverlapping(h.target));
	}
	
	private boolean sameStrand(Hit h) {
		return (strand == null && h.strand == null) || (strand.equals(h.strand));
	}
	
	private void merge(Hit h) {
		this.query.start     = Math.min(h.query.start,  this.query.start);
		this.query.end       = Math.max(h.query.end,    this.query.end);
		this.target.start    = Math.min(h.target.start, this.target.start);
		this.target.end      = Math.max(h.target.end,   this.target.end );
		this.query.grpIdx    = h.query.grpIdx;
		this.target.grpIdx   = h.target.grpIdx;
		this.origHits += h.origHits;
		
		this.matchLen = this.query.end - this.query.start; 
		this.pctid =  (this.pctid == 0 ? h.pctid : (this.pctid + h.pctid) / 2);
		this.pctsim = (this.pctsim == 0 ? h.pctsim : (this.pctsim + h.pctsim) / 2); // CAS515 add
	}
	
	/******************************************************************
	 * STATIC - to merge hit lists
	 * called in AnchorsMain.clusterGeneHits2 and preFilterHits2
	 * not a complete merge b/c doesn't ever merge two clusters
	 */
	// ZZZ add length limit
	public static void mergeOverlappingHits(Vector<Hit> hits) {
		if (hits.size() <= 1) return;
		
		Vector<Hit> mergedHits = new Vector<Hit>();
		for (Hit h1 : hits) {
			boolean overlap = false;
			
			for (Hit h2 : mergedHits) {
				// sameStrand test b/c otherwise we can't label the orientation of the hits.
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
	// called in AnchorsMain.clusterGeneHits2
	public static Hit clusterHits(Vector<Hit> hits, HitType ht)  throws Exception {
		int numHits = hits.size();
		
		if (numHits==0) return null;
		
		if (numHits == 1) {
			hits.firstElement().mHT = ht;
			if (hits.firstElement().origHits != 1) System.err.println("SyMAP error clusterhits 4 ");
			return hits.firstElement();
		}
		
		Hit h2 = new Hit( numHits*2 );
		h2.origHits = 0;
		
		for (Hit h : hits) {
			if (h.origHits > 1) System.err.println("SyMAP error clusterhits1 ");
			h2.merge(h);
		}
		if (h2.origHits > numHits)System.err.println("SyMAP error clusterhits2 ");
					
		h2.matchLen = 0;
		h2.mHT = ht;
		int i = 0;
		long avgPctID = 0;
		h2.origHits = 0;
		
		// average the pctid, weighted by matchlen
		for (Hit h : hits) {
			h2.query.subHits[i]  = h.query.start;
			h2.target.subHits[i] = h.target.start;
			i++;
			
			h2.query.subHits[i]  = h.query.end;
			h2.target.subHits[i] = h.target.end;
			i++;
			
			h2.matchLen += h.matchLen;
			avgPctID += h.pctid*h.matchLen;
			h2.origHits += h.origHits;
		}
		
		h2.pctid = (int)(avgPctID/h2.matchLen);
		h2.strand = hits.get(0).strand; 
		
		if (h2.origHits != numHits) System.err.println("SyMAP error clusterhits 3 ");
		
		return h2;
	}
}
