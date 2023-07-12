package backend;

import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

import util.ErrorReport;

/*****************************************************
 * Used for AnchorsMain: a hit may be a MUMmer hit or a clustered hit SubHit>0.
 */
public class Hit implements Comparable <Hit> {// CAS500 added <Hit>
	public static final boolean bRev = true; // Only cluster hits of the same orientation (--,++) and (+-,+-)
	public static final int haveNSubs=2, haveLen1=300, haveLen2=100;   // >= heuristics; used in HitBin for filtering 
	
	public int hitLen=0;
	public int matchLen, pctid, pctsim, idx; // pctid=%ID, pctsim=%sim from MUMmer per subhit
	public String strand;
	public int annotIdx1 = 0, annotIdx2 = 0;
	public int nSubHits=0;
	
	public SubHit queryHits, targetHits;
	public HitType mHT = null;					    // AnchorsMain {GeneGene, GeneNonGene, NonGene}
	public int htype=0;								// CAS543 numeric type
	
	public HitStatus status = HitStatus.Undecided;	// AnchorsMain {In, Out, Undecided };
	
	public int origHits = 0; 
	public int binsize1=0, binsize2=0; // store topN bin sizes for stats
	
	public boolean isRev=false;
	
	// Called in AnchorMain
	public Hit() {
		this(0); // single hit, i.e. not clustered
	}
	
	// Called in AnchorMain.scanNextMummerHit CAS540x was setting values in anchorMain
	public void setHit(String query, String target, int qstart, int qend, int tstart, int tend, int match,
			int pctid, int pctsim, String strand) { 
		this.queryHits.name  = query.intern();	
		this.queryHits.start = qstart;
		this.queryHits.end = qend;
		
		this.targetHits.name = target.intern();
		this.targetHits.start = tstart;
		this.targetHits.end = tend;
		
		this.matchLen = match;
		this.pctid = pctid;
		this.pctsim = pctsim;
		this.strand = strand.intern(); // reduce hit memory footprint
		
		// CAS540x was waiting to reverse until after checking in splitMUMmer; now just set flag and reverse
		isRev = strand.contains("-") && strand.contains("+");
		
		int s = queryHits.start; int e = queryHits.end; 
		queryHits.start  = (s <= e ? s : e); 
		queryHits.end    = (s <= e ? e : s); 
		
		s = targetHits.start; e = targetHits.end;
		targetHits.start  = (s <= e ? s : e); 
		targetHits.end    = (s <= e ? e : s); 
	}
	
	private Hit(int nSubHits) { // set defaults for merging
		queryHits =  new SubHit(nSubHits); // if zero nSubHits, not used for clustered hit
		targetHits = new SubHit(nSubHits);
			
		queryHits.start  = Integer.MAX_VALUE;
		queryHits.end    = 0;
		targetHits.start = Integer.MAX_VALUE;
		targetHits.end   = 0;
		matchLen     = 0;
		pctid        = 0;
		pctsim		 = 0;
	}
	
	// Used by splitMummerHit 
	private Hit(Hit h, int qstart, int qend, int tstart, int tend, int tmp_idx) { 
		matchLen = 	h.matchLen;
		pctid = 	h.pctid;
		pctsim = 	h.pctsim; // CAS515 add
		strand = 	h.strand;
		status = 	h.status;
		idx 	= 	h.idx;
		origHits = 	h.origHits;
		mHT = 		h.mHT;
		annotIdx1 = h.annotIdx1;
		annotIdx2 = h.annotIdx2;
		binsize1 = 	h.binsize1;
		binsize2 = 	h.binsize2;
		
		queryHits =  new SubHit(h.queryHits);
		targetHits = new SubHit(h.targetHits);
		queryHits.start = qstart;
		queryHits.end = qend;
		targetHits.start = tstart;
		targetHits.end = tend;
		idx = tmp_idx; 
	}
	public int compareTo(Hit h2) {// CAS500 changed Object to Hit; scan2 Collections.sort(clustHits);
		if      (queryHits.start < h2.queryHits.start) 	return 1;	
		else if (queryHits.start > h2.queryHits.start)	return -1;
	
		if      (targetHits.start < h2.targetHits.start)return 1;
		else if (targetHits.start > h2.targetHits.start)return -1;
	
		return 0;
	}
	// called by HitBin on filtering clustered hit; if false, they may still be used if topN
	public boolean useAnnot() { 
		if (annotIdx1 > 0 && annotIdx2 > 0) {
			if (matchLen>haveLen2) return true;
		}
		if (annotIdx1 > 0 || annotIdx2 > 0) {
			if (nSubHits>=haveNSubs || matchLen>=haveLen1) return true; 
		}
		return false;
	}
	public boolean useAnnot2() { // CAS543 keep if both genes!
		if (annotIdx1 > 0 && annotIdx2 > 0) {
			if (matchLen>haveLen2) return true;
		}
		return false;
	}
	public boolean useAnnot1() { 
		if (annotIdx1 > 0 || annotIdx2 > 0) {
			if (nSubHits>=haveNSubs || matchLen>=haveLen1) return true; 
		}
		return false;
	}
	
	public int maxLength() {
		return Math.max(queryHits.length(), targetHits.length());
	}
	public boolean isDiagHit() { // CAS535 add: was doing full check in multiple places
		return queryHits.grpIdx == targetHits.grpIdx && queryHits.start == targetHits.start && queryHits.end == targetHits.end;
	}
	// for debug
	public String getInfo() {
		String x = (mHT==null) ? "No HT" : mHT.toString();
		String state = (idx>0) ? "idx=" + idx + " " + status : " ";
		
		int cnt = 0;
		if (annotIdx1 > 0 && annotIdx2 > 0) cnt=2;
		else if (annotIdx1 > 0 || annotIdx2 > 0) cnt=1;
		String q = "q: " + queryHits.start + "/" + queryHits.end;
		String t = "t: " + targetHits.start + "/" + targetHits.end;
		String p = String.format("len=%,5d id=%d", matchLen, pctid);
		
		return String.format("Gene %d %s %-30s %-30s %s %s %s", cnt, p, q, t,  strand, state, x);	
	}
	/****************************************************************
	 * mergeOlapDiagHits methods
	 */
	private void merge(Hit h) { 
		this.queryHits.grpIdx    = h.queryHits.grpIdx;
		this.queryHits.start     = Math.min(h.queryHits.start,  this.queryHits.start);
		this.queryHits.end       = Math.max(h.queryHits.end,    this.queryHits.end);
		this.targetHits.grpIdx   = h.targetHits.grpIdx;
		this.targetHits.start    = Math.min(h.targetHits.start, this.targetHits.start);
		this.targetHits.end      = Math.max(h.targetHits.end,   this.targetHits.end );
		
		if (h.origHits>1) System.out.println("Orig " + h.origHits);
		this.origHits += h.origHits;
		
		this.matchLen = this.queryHits.end - this.queryHits.start; 
		this.pctid =  (this.pctid == 0 ?  h.pctid :  (this.pctid + h.pctid) / 2);
		this.pctsim = (this.pctsim == 0 ? h.pctsim : (this.pctsim + h.pctsim) / 2); // CAS515 add
	}
	private boolean isOverlapping(Hit h) { 
		return (this.queryHits.isOverlapping(h.queryHits) && 
				this.targetHits.isOverlapping(h.targetHits));
	}

	private boolean sameStrand(Hit h) {
		return (strand == null && h.strand == null) || (strand.equals(h.strand));
	}
	
	/*******************************************************************
	 * STATIC - manipulation of hits
	 ********************************************************************/
	/*******************************************************************
	 * Break the hit into pieces, taking care to not leave a tiny leftover hit, and for
	 * cases where query and target are different lengths (maybe not possible with mummer).
	 * Note, hits have been previously fixed so start < end. [AnchorMain.breakHit]
	 */
	static public Vector<Hit> splitMUMmerHit(Hit hit) {
	try {
		int fSplitLen = Group.fSplitLen;
		
		Vector<Hit> ret = new Vector<Hit>();
	
		int minLeftover = fSplitLen/10;
		
		int qlen = hit.queryHits.length();
		int tlen = hit.targetHits.length();

		int qleft = qlen%fSplitLen;
		int tleft = tlen%fSplitLen;		
		
		int qparts = (qleft >= minLeftover ?  (1 + qlen/fSplitLen) : (qlen/fSplitLen));
		int tparts = (tleft >= minLeftover ?  (1 + tlen/fSplitLen) : (tlen/fSplitLen));
		
		int parts = Math.min(qparts,tparts);
		int qhStart=hit.queryHits.start,  qhEnd=hit.queryHits.end;
		int thStart=hit.targetHits.start, thEnd=hit.targetHits.end;
		
		if (parts == 1) {
			hit.idx=6;
			ret.add(hit);
		}
		else if (!hit.isRev) { // build (parts-1) hits of fixed size, and put the rest into the final hit
			for (int i = 1; i < parts; i++) {
				int qstart = 	qhStart + fSplitLen * (i-1);
				int qend = 		qstart  + fSplitLen - 1;
				int tstart = 	thStart + fSplitLen * (i-1);
				int tend = 		tstart  + fSplitLen - 1;
				Hit h = new Hit(hit, qstart, qend, tstart, tend, 1);
				ret.add(h);
			}
			int qstart = 		qhStart + fSplitLen * (parts-1);
			int tstart = 		thStart + fSplitLen * (parts-1);
			
			Hit h = new Hit(hit, qstart, qhEnd, tstart, thEnd, 2);
			ret.add(h);
		}
		else if (hit.isRev) {//  forward through the query, backward through target
			for (int i = 1; i < parts; i++) {
				int qstart = 	qhStart + fSplitLen * (i-1);
				int qend =		qstart  + fSplitLen - 1;
				int tend = 		thEnd   - fSplitLen * (i-1);
				int tstart = 	tend    - fSplitLen + 1;	
				Hit h = new Hit(hit, qstart, qend, tstart, tend, 3);
				ret.add(h);
			}
			int qstart = 		qhStart + fSplitLen*(parts-1);
			int tend = 			thEnd - fSplitLen*(parts-1);

			Hit h = new Hit(hit, qstart, qhEnd, thStart, tend, 4);
			ret.add(h);		
		}
		return ret;
	}
	catch (Exception e) {ErrorReport.print(e, "Split MUMmer hit"); return null;}
	}
	/******************************************************************
	 * to merge hit lists [mergeOverlappingHits; was called by clusterSubHits2 on bighits, which never ran]
	 * called in AnchorsMain.preFilterHits2
	 * not a complete merge b/c doesn't ever merge two clusters
	 */
	public static void mergeOlapDiagHits(Vector<Hit> hits) {
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
	/***************************************************************
	* called in AnchorsMain.clusterHits2 
	* CAS540 check for isRev and !isRev, was mixing ++/-- with +-/-+
	*/
	public static Vector <Hit> clusterHits2(Vector<Hit> hitVec, HitType htype, AnnotElem qAnno, AnnotElem tAnno)  throws Exception {
		Vector <Hit> retHits = new Vector <Hit> ();
		if (hitVec.size()==0) return retHits;
		
		Vector <Hit> rHits = new Vector <Hit> ();
		Vector <Hit> sHits = new Vector <Hit> ();
		for (Hit ht : hitVec) {
			if (bRev) {
				boolean hRev = (ht.strand.contains("+") && ht.strand.contains("-"));		
				if (hRev) rHits.add(ht);
				else      sHits.add(ht);
			}
			else rHits.add(ht);
		}
		int r=rHits.size(), s=sHits.size();
		if (r>=s || r>2) retHits.addAll(clusterCreate(rHits, htype, qAnno, tAnno)); // heuristic
		if (s>=r || s>2) retHits.addAll(clusterCreate(sHits, htype, qAnno, tAnno));
		if (r>0 && s>0) BinStats.incStat("OppositeDir", 1); 
		
		return retHits;
	}
	private static Vector <Hit> clusterCreate(Vector<Hit> hitVec, HitType htype, AnnotElem qAnno, AnnotElem tAnno)  throws Exception {
		Vector <Hit> retHits = new Vector <Hit> ();
		int numHits = hitVec.size();
		
		if (numHits==0) return null;
		
		if (numHits == 1) {
			Hit h = hitVec.firstElement();
			h.mHT = htype;
			h.nSubHits = 1;
			if (qAnno.isGene()) h.annotIdx1 = qAnno.idx;	
			if (tAnno.isGene()) h.annotIdx2 = tAnno.idx;
			retHits.add(h);
			return retHits;
		}

		// Process list
		Hit clHit = new Hit( numHits*2 );
		clHit.mHT = htype;
		if (qAnno.isGene()) clHit.annotIdx1 = qAnno.idx;	
		if (tAnno.isGene()) clHit.annotIdx2 = tAnno.idx;
		
		clHit.nSubHits=numHits;
		clHit.queryHits.grpIdx  = hitVec.get(0).queryHits.grpIdx;
		clHit.targetHits.grpIdx = hitVec.get(0).targetHits.grpIdx;
		clHit.strand =   hitVec.get(0).strand; 
		clHit.isRev =    clHit.strand.contains("+") && clHit.strand.contains("-");
		clHit.matchLen = clHit.origHits = 0;
		
		int i = 0;
		double sumPctID = 0, sumPctSim = 0, sumMatch=0; // CAS540 was not computing sim, so using first
		int qSlast=0, qElast=0, tSlast=0, tElast=0, tMatch=0, qMatch=0;
		
		sortByQuery(hitVec);
		
		for (Hit sh : hitVec) {
			clHit.queryHits.subHits[i]  = sh.queryHits.start;
			clHit.targetHits.subHits[i] = sh.targetHits.start;
			i++;
			
			clHit.queryHits.subHits[i]  = sh.queryHits.end;
			clHit.targetHits.subHits[i] = sh.targetHits.end;
			i++;
			
			clHit.queryHits.start  = Math.min(sh.queryHits.start,  clHit.queryHits.start);
			clHit.queryHits.end    = Math.max(sh.queryHits.end,    clHit.queryHits.end);
			clHit.targetHits.start = Math.min(sh.targetHits.start, clHit.targetHits.start);
			clHit.targetHits.end   = Math.max(sh.targetHits.end,   clHit.targetHits.end );
			
			clHit.origHits += sh.origHits;
			
			sumPctSim += sh.pctsim * sh.matchLen; 
			sumPctID  += sh.pctid  * sh.matchLen;
			sumMatch  += sh.matchLen;
			
			//  CAS540 Compute subhit length for query; matchLen was just the sum of the query
			int qM = (sh.queryHits.end  - sh.queryHits.start + 1);
			if (qMatch>0) {
				int qolap =  Utils.intervalsOverlap(sh.queryHits.start, sh.queryHits.end, qSlast, qElast);
				if (qolap<0) qMatch += qM;
				else         qMatch += (qM-qolap);
			}
			else qMatch = qM;
			qSlast = sh.queryHits.start;  qElast=sh.queryHits.end;
		}
		clHit.pctid =    (int) Math.round(sumPctID/sumMatch); 
		clHit.pctsim =   (int) Math.round(sumPctSim/sumMatch);
		
		//  CAS540 Compute subhit length for target
		sortByTarget(hitVec);
		for (Hit sh : hitVec) {
			int tM = (sh.targetHits.end - sh.targetHits.start + 1);
			
			int tolap =  Utils.intervalsOverlap(sh.targetHits.start, sh.targetHits.end, tSlast, tElast);
			if (tolap<0) tMatch += tM;
			else         tMatch += (tM-tolap);
			tSlast = sh.targetHits.start; tElast=sh.targetHits.end;
		}
		clHit.matchLen = Math.max(tMatch, qMatch); // CAS540 add take best
		
		retHits.add(clHit);
		return retHits;
	}
	
	/*********************************************************/
	public static void sortByTarget(Vector<Hit> hits) { // AnchorMain saveResults
		Collections.sort(hits, 
			new Comparator<Hit>() {
				public int compare(Hit h1, Hit h2) {
					return h1.targetHits.start - h2.targetHits.start;
				}
			}
		);
	}
	public static void sortByQuery(Vector<Hit> hits) { // clusterHits2
		Collections.sort(hits, 
			new Comparator<Hit>() {
				public int compare(Hit h1, Hit h2) {
					return h1.queryHits.start - h2.queryHits.start;
				}
			}
		);
	}
	/***************************************************************
	 * CAS540 moved from separate file; changed from using subhit as an parameter to using start/end
	 ***************************************************************/
	public class SubHit  {
		public int start, end, score, grpIdx = 0;		
		public String name; 		
		public int[] subHits;       // set in clusterHits; array of sub-hits start/end coordinates; CAS535 [blocks]
		public HitStatus status = HitStatus.Undecided; // AnchorsMain {In, Out, Undecided };
		
		public SubHit(SubHit h) {
			end =      h.end;
			start =    h.start;
			grpIdx =   h.grpIdx;
			name = 	   h.name;
			status =   h.status;
			subHits =   new int[h.subHits.length];
			for (int i = 0; i < subHits.length; i++) subHits[i] = h.subHits[i];
		}
		
		public SubHit(int numSubs) {
			subHits = new int[numSubs];
		}
		
		public String toString() {
			String s = "";
			for (int i = 0;  i < subHits.length - 1;  i+=2)
				s += subHits[i] + ":" + subHits[i+1] + ",";
			return s;
		}
		
		public int length() { return (Math.abs(end-start)+1); }
		
		public boolean isOverlapping(SubHit h) {
			return Utils.intervalsTouch(this.start, this.end, h.start, h.end);
		}
	}
}
