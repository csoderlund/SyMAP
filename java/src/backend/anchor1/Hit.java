package backend.anchor1;

import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

import backend.Utils;
import symap.Globals;
import util.ErrorReport;

/*****************************************************
 * Used for AnchorsMain: a hit may be a MUMmer hit or a clustered hit SubHit>0.
 */
public class Hit implements Comparable <Hit> {
	protected static final boolean FcheckRev = true; // Only cluster hits of the same orientation (--,++) and (+-,+-)
	protected static final int FhaveNSubs=2, FhaveLen1=300, FhaveLen2=100;   // >= heuristics; used in Hit/HitBin for filtering 
	
	protected int hitLen=0;
	protected int matchLen, pctid, pctsim, idx; // bestLen(T,Q), pctid=%ID, pctsim=%sim from MUMmer per subhit
	protected String strand;
	protected int annotIdx1 = 0, annotIdx2 = 0;
	protected int nSubHits=0;
	
	protected SubHit queryHits, targetHits;
	protected HitType mHT = null;					    // AnchorsMain1 {GeneGene, GeneNonGene, NonGene}
	protected int htype=0;							
	
	protected HitStatus status = HitStatus.Undecided;	// AnchorsMain1 {In, Out, Undecided };
	
	protected int origHits = 0; 
	protected int binsize1=0, binsize2=0; // store topN bin sizes for stats
	
	protected boolean isRev=false;
	
	// Called in AnchorMain
	protected Hit() {
		this(0); // single hit, i.e. not clustered
	}
	
	// Called in AnchorMain1.scanNextMummerHit 
	protected void setHit(String query, String target, int qstart, int qend, int tstart, int tend, int match,
			int pctid, int pctsim, String strand) { 
		this.queryHits.name  = query.intern();	
		this.queryHits.start = qstart;
		this.queryHits.end = qend;
		
		this.targetHits.name = target.intern();
		this.targetHits.start = tstart;
		this.targetHits.end = tend;
		
		this.matchLen = match; // best of T and Q mummer reported length
		this.pctid = pctid;
		this.pctsim = pctsim;
		this.strand = strand.intern(); // reduce hit memory footprint
		
		isRev = strand.contains("-") && strand.contains("+");
		
		int s = queryHits.start; int e = queryHits.end; 
		queryHits.start  = (s < e ? s : e); 
		queryHits.end    = (s < e ? e : s); 
		
		s = targetHits.start; e = targetHits.end;
		targetHits.start  = (s < e ? s : e); 
		targetHits.end    = (s < e ? e : s); 
	}
	protected void swapCoords() {// isSelf - if sequences are in different files, grp1_idx<grp2_idx can happen; CAS575 add
		int start 		= queryHits.start;
		int end 		= queryHits.end;
		int grpIdx 		= queryHits.grpIdx;
		String name 	= queryHits.name;
		int [] subHits 	= queryHits.subHits ;
		
		queryHits.start 	= targetHits.start; 
		queryHits.end 		= targetHits.end; 
		queryHits.grpIdx 	= targetHits.grpIdx; 
		queryHits.name 		= targetHits.name; 
		queryHits.subHits 	= targetHits.subHits; 
		
		targetHits.start 	= start;
		targetHits.end	 	= end;
		targetHits.grpIdx	= grpIdx;
		targetHits.name	 	= name;
		targetHits.subHits	= subHits;
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
		pctsim = 	h.pctsim; 
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
	public int compareTo(Hit h2) {// scan2 Collections.sort(clustHits);
		if      (queryHits.start < h2.queryHits.start) 	return 1;	
		else if (queryHits.start > h2.queryHits.start)	return -1;
	
		if      (targetHits.start < h2.targetHits.start)return 1;
		else if (targetHits.start > h2.targetHits.start)return -1;
	
		return 0;
	}
	// called by HitBin on filtering clustered hit; if false, they may still be used if topN
	protected boolean useAnnot() { 
		if (annotIdx1 > 0 && annotIdx2 > 0) {
			if (matchLen>FhaveLen2) return true;
		}
		if (annotIdx1 > 0 || annotIdx2 > 0) {
			if (nSubHits>=FhaveNSubs || matchLen>=FhaveLen1) return true; 
		}
		return false;
	}
	protected boolean useAnnot2() { 
		if (annotIdx1 > 0 && annotIdx2 > 0) {
			if (matchLen>FhaveLen2) return true;
		}
		return false;
	}
	protected boolean useAnnot1() { 
		if (annotIdx1 > 0 || annotIdx2 > 0) {
			if (nSubHits>=FhaveNSubs || matchLen>=FhaveLen1) return true; 
		}
		return false;
	}
	
	protected int maxLength() {
		return Math.max(queryHits.length(), targetHits.length());
	}
	protected boolean isDiagHit() { 
		return queryHits.grpIdx == targetHits.grpIdx && queryHits.start == targetHits.start && queryHits.end == targetHits.end;
	}
	// for debug
	protected String getInfo() {
		return String.format("GrpIdx %2d %2d   Query %,10d %,10d   Target %,10d %,10d ", 
				queryHits.grpIdx, targetHits.grpIdx, queryHits.start, queryHits.end, targetHits.start, targetHits.end);	
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
		this.pctsim = (this.pctsim == 0 ? h.pctsim : (this.pctsim + h.pctsim) / 2); 
	}
	private boolean isOverlapping(Hit h) { 
		return (this.queryHits.isOverlapping(h.queryHits) && 
				this.targetHits.isOverlapping(h.targetHits));
	}

	private boolean sameStrand(Hit h) {
		return (strand == null && h.strand == null) || (strand.equals(h.strand));
	}
	
	/*******************************************************************
	 * XXX STATIC - manipulation of hits
	 ********************************************************************/
	/*******************************************************************
	 * Break the hit into pieces, taking care to not leave a tiny leftover hit, and for
	 * cases where query and target are different lengths (maybe not possible with mummer).
	 * Note, hits have been previously fixed so start < end. [AnchorMain.breakHit]
	 */
	static protected Vector<Hit> splitMUMmerHit(Hit hit) {
	try {
		int fSplitLen = Group.FSplitLen;
		
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
	 * to merge hit lists; CAS575 still merges, but does not save
	 * called in AnchorsMain.preFilterHits2
	 * not a complete merge b/c doesn't ever merge two clusters
	 */
	protected static void mergeOlapDiagHits(Vector<Hit> hits) {
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
	*/
	protected static int totalWS=0;
	protected static Vector <Hit> clusterHits2(Vector<Hit> hitVec, HitType htype, AnnotElem qAnno, AnnotElem tAnno)  throws Exception {
		Vector <Hit> retHits = new Vector <Hit> ();
		if (hitVec.size()==0) return retHits;
	
		Vector <Hit> rHits = new Vector <Hit> ();
		Vector <Hit> sHits = new Vector <Hit> ();
		for (Hit ht : hitVec) {
			if (FcheckRev) {		
				if (ht.isRev) rHits.add(ht);
				else          sHits.add(ht);
			}
			else rHits.add(ht);
		}
		// heuristic: if same/diff orient, only make clusters of both if the both have >2 subhits
		int r=rHits.size(), s=sHits.size();
		if (r>=s || r>2) retHits.addAll(clusterCreate(rHits, htype, qAnno, tAnno)); 
		if (s>=r || s>2) retHits.addAll(clusterCreate(sHits, htype, qAnno, tAnno));
		
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
			setStrand(h, qAnno, tAnno, 1);
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
		setStrand(clHit, qAnno, tAnno, numHits);
		
		clHit.matchLen = clHit.origHits = 0;
		int i = 0;
		double sumPctID = 0, sumPctSim = 0, sumMatch=0; 
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
			
			//  Compute subhit length for query
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
		
		//  Compute subhit length for target
		sortByTarget(hitVec);
		for (Hit sh : hitVec) {
			int tM = (sh.targetHits.end - sh.targetHits.start + 1);
			
			int tolap =  Utils.intervalsOverlap(sh.targetHits.start, sh.targetHits.end, tSlast, tElast);
			if (tolap<0) tMatch += tM;
			else         tMatch += (tM-tolap);
			tSlast = sh.targetHits.start; tElast=sh.targetHits.end;
		}
		clHit.matchLen = Math.max(tMatch, qMatch); // take best
		
		retHits.add(clHit);
		return retHits;
	}
	/*****************************************************
	 *  make hit strands correspond to genes
	 *  ht.isRev is +/- or -/+;  anno.isRev is -
	 */
	private static void setStrand(Hit ht, AnnotElem qAnno, AnnotElem tAnno, int numHits) {
		ht.isRev =    ht.strand.contains("+") && ht.strand.contains("-");
		if (!qAnno.isGene() && !tAnno.isGene()) return;
		
		boolean bHtEQ = !ht.isRev;
		
		if (qAnno.isGene() && tAnno.isGene()) {
			String ts = (tAnno.isRev) ? "-" : "+";
			String qs = (qAnno.isRev) ? "-" : "+";
			boolean isGnEQ = ts.equals(qs);
			
			if (bHtEQ!=isGnEQ) {// this is still processed
				if (Globals.TRACE && numHits>3 && totalWS<1) {
					String msg = String.format("%2d subhits cluster (%s) for Q gene %6d (%s) and T %6d (%s)",
						numHits, ht.strand, qAnno.genenum, qs, tAnno.genenum, ts);
					System.out.println(msg);
				}
				if (numHits>3) totalWS++;
			}
			else ht.strand = qs + "/" + ts;
		}
		else if (qAnno.isGene()) {
			String qs = (qAnno.isRev) ? "-" : "+";
			if (!bHtEQ) 			 ht.strand = qs + "/" + qs;
			else if (qs.equals("+")) ht.strand = "+/-";
			else                     ht.strand = "-/+";
		}
		else if (tAnno.isGene()) {
			String ts = (tAnno.isRev) ? "-" : "+";
			if (!bHtEQ) 			 ht.strand = ts + "/" + ts;
			else if (ts.equals("+")) ht.strand = "-/+";
			else                     ht.strand = "+/-";
		}
	}
	/*********************************************************/
	protected static void sortByTarget(Vector<Hit> hits) { // AnchorMain saveResults
		Collections.sort(hits, 
			new Comparator<Hit>() {
				public int compare(Hit h1, Hit h2) {
					return h1.targetHits.start - h2.targetHits.start;
				}
			}
		);
	}
	protected static void sortByQuery(Vector<Hit> hits) { // clusterHits2
		Collections.sort(hits, 
			new Comparator<Hit>() {
				public int compare(Hit h1, Hit h2) {
					return h1.queryHits.start - h2.queryHits.start;
				}
			}
		);
	}
	/***************************************************************
	 ***************************************************************/
	protected class SubHit  {
		protected int start, end, score, grpIdx = 0;		
		protected String name; 		
		protected int[] subHits;       // set in clusterHits; array of sub-hits start/end coordinates; 
		protected HitStatus status = HitStatus.Undecided; // AnchorsMain {In, Out, Undecided };
		
		protected SubHit(SubHit h) {
			end =      h.end;
			start =    h.start;
			grpIdx =   h.grpIdx;
			name = 	   h.name;
			status =   h.status;
			subHits =   new int[h.subHits.length];
			for (int i = 0; i < subHits.length; i++) subHits[i] = h.subHits[i];
		}
		
		protected SubHit(int numSubs) {
			subHits = new int[numSubs];
		}
		
		public String toString() {
			String s = "";
			for (int i = 0;  i < subHits.length - 1;  i+=2)
				s += subHits[i] + ":" + subHits[i+1] + ",";
			return s;
		}
		
		protected int length() { return (Math.abs(end-start)+1); }
		
		protected boolean isOverlapping(SubHit h) {
			return Utils.intervalsTouch(this.start, this.end, h.start, h.end);
		}
	}
}
