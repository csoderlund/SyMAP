package symap.sequence;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;
import java.util.HashMap;
import java.util.TreeMap;

import database.DBconn2;
import symap.Globals;
import symap.mapper.HitData;
import symap.mapper.SeqHits;
import util.ErrorReport;

/**
 * Load data for Sequence Track 
 * Gene overlap placement algorithm
 * Conserved genes (g2xN for Sfilter)
 */
public class SeqPool {	
	private DBconn2 dbc2;
	
	protected SeqPool(DBconn2 dbc2) { this.dbc2 = dbc2;} // called from Sequence when it is created

	/** XXX database methods ***/
	protected int loadChrSize(Sequence seqObj) {
	try {
		int grpIdx =   seqObj.getGroup();
		String size_query = 
				"SELECT (SELECT length FROM pseudos WHERE grp_idx=" + grpIdx + ") as size, "+
				"       (SELECT name   FROM xgroups WHERE idx="     + grpIdx + ") as name; ";
		
		int size = dbc2.executeInteger(size_query);
		return size;
	}
	catch (Exception e) {ErrorReport.print(e, "Cannot get genome size"); return 0;}
	}
	/**
	 * Called by Sequence object in init
	 * Annotation Vector = enter annotation objects 
	 */
	protected synchronized String loadSeqData(Sequence seqObj,  Vector<Annotation> annoVec) {
		int grpIdx =   seqObj.getGroup();		
		
		String name=null, type, desc;
		int start, end; 
		String strand, tag; 
		int annot_idx, genenum, gene_idx, numhits, itype;

		ResultSet rs = null;
		try {
			String size_query = 
					"SELECT (SELECT length FROM pseudos WHERE grp_idx=" + grpIdx + ") as size, "+
					"       (SELECT name   FROM xgroups WHERE idx="     + grpIdx + ") as name; ";
			
			rs = dbc2.executeQuery(size_query);
			rs.next(); // a row no matter what
			
			if (rs.wasNull()) {
				System.err.println("No information in db found for Sequence with group id "+grpIdx);
				return null;
			}
			name = rs.getString(2);
			
			HashMap <Integer, Annotation> geneMap = new HashMap <Integer, Annotation>  ();
			
			String annot_query =		
					"SELECT idx, type,name,start,end,strand, genenum, gene_idx, tag, numhits FROM pseudo_annot " 
					+ " WHERE grp_idx=" + grpIdx + " and ";  
			
			for (int i=0; i<2; i++) {
				String sql = (i==0) ? " type='" + Globals.geneType + "'" 
				: " (type!='" + Globals.geneType + "' && type!='" + Globals.pseudoType + "')"; // CAS565 add pseudo
				rs = dbc2.executeQuery(annot_query + sql + " order by start ASC"); // CAS560 add order to be sure
				while (rs.next()) {
					annot_idx = rs.getInt(1);		
					type = rs.getString(2);	
					desc = rs.getString(3);	
					start = rs.getInt(4);		
					end = rs.getInt(5);		
					strand = rs.getString(6);	
					genenum = rs.getInt(7); 	
					gene_idx = rs.getInt(8);		
					tag = rs.getString(9); 		
					numhits = rs.getInt(10);
						
					tag = (tag==null || tag.contentEquals("")) ? type : tag; 
					if (type.equals(Globals.geneType)) 		itype = Annotation.GENE_INT;
					else if (type.equals(Globals.exonType)) itype = Annotation.EXON_INT;
					else if (type.equals(Globals.gapType)) 	itype = Annotation.GAP_INT;
					else if (type.equals(Globals.centType)) itype = Annotation.CENTROMERE_INT;
					else {
						itype=Annotation.numTypes+1;
						System.out.println(tag + " unknown type: " + type);
					}
					
					Annotation aObj = new Annotation(seqObj, desc,itype,start,end,strand, tag, gene_idx, annot_idx, genenum, numhits);
					annoVec.add(aObj);
					
					if (i==0) geneMap.put(annot_idx, aObj);
					else if (type.equals("exon")) {
						Annotation gObj = geneMap.get(gene_idx);
						if (gObj!=null) gObj.addExon(aObj);
					}
				}
			}
			rs.close();
			geneMap.clear();
			
			// Sort the annotations by type (ascending order) so that 'exons' are drawn on top of 'genes'.
			Collections.sort(annoVec,
				new Comparator<Annotation>() {
					public int compare(Annotation a1, Annotation a2) { 
						if (a1.isGene() && a2.isGene()) {
							if (a1.getGeneNum()==a2.getGeneNum()) return a1.getStart()-a2.getStart();
								return a1.getGeneNum()-a2.getGeneNum(); // sort on gene#
						}	
						return (a1.getType() - a2.getType()); 			  
					}
				}
			);
			
		} catch (Exception sql) {ErrorReport.print(sql, "SQL exception acquiring sequence data.");}
		
		return name;
	}
	protected synchronized void close() {}

	protected int getNumAllocs(int grpIdx) { // exactly alloc correct size for Sequence.allAnnoVec
		try {
			return dbc2.executeCount("select count(*) from pseudo_annot where grp_idx=" + grpIdx);
		}
		catch (Exception e) {ErrorReport.print(e, "Getting number of annotations for " + grpIdx); return 0;}
	}
	// for popup
	protected TreeMap<Integer, String> getGeneHits(int geneIdx, int grpIdx, boolean isAlgo1) {
		TreeMap <Integer, String> hitMap = new TreeMap <Integer, String> ();
		try {
			ResultSet rs = dbc2.executeQuery("select pha.hit_idx, pha.olap, pha.exlap "
					+ " from pseudo_hits_annot as pha "
					+ " join pseudo_annot as pa on pa.idx=pha.annot_idx "
					+ " where pha.annot_idx=" + geneIdx + " and pa.grp_idx=" + grpIdx);
			while (rs.next()) {
				int hitIdx = rs.getInt(1);
				int olap = rs.getInt(2);
				int exlap = rs.getInt(3);
				String scores = "Gene " + olap + "%";
				if (!isAlgo1) scores += " Exon " + exlap + "%"; 
				hitMap.put(hitIdx, scores);
			}
			return hitMap;
		}
		catch (Exception e) {ErrorReport.print(e, "Getting number of annotations for " + geneIdx); return hitMap;}
	}
	/*******************************************************
	 * XXX Gene placement algorithm: Called during init(); used in build(); adjusts x coord for overlapping genes
	 */
	private final int OVERLAP_OFFSET=12;			
	protected void buildOlap(HashMap <Integer, Integer> olapMap, Vector <Annotation> geneVec) {
		int lastGeneNum=-1;
		Vector <Annotation> numList = new Vector <Annotation> ();
		
		 for (Annotation annot : geneVec) { 
			if (annot.getGeneNum() == lastGeneNum) {
				numList.add(annot);
				continue;
			}
			
			buildPlace(olapMap, numList, lastGeneNum);
			numList.clear();
			
			lastGeneNum = annot.getGeneNum();
			numList.add(annot);
		 }
		 buildPlace(olapMap, numList, lastGeneNum); // CAS519 missing last one
		 
		 if (olapMap.size()==0) olapMap.put(-1,-1); // need at least one value so will not compute again
	}
	/*******************************************************
	 * Compute how to display overlapping genes based on genenum
	 * uses genenum to find set of overlapping, 
	 * but does not use genenum suffix (uses same overlap check as in AnnoLoadPost.computeGeneNum)
	 */
	private void buildPlace(HashMap <Integer, Integer> olapMap, Vector <Annotation> numList, int lastGeneNum) {
		try {
			if (numList.size()==1) return;
			if (numList.size()==2) { // get(0) has no offset
				olapMap.put(numList.get(1).getAnnoIdx(), OVERLAP_OFFSET);
				return;
			}
		// Create vector ordered by length
			Vector <GeneData> gdVec = new Vector <GeneData> ();
			for (Annotation annot : numList) gdVec.add(new GeneData(annot));
			
			Collections.sort(gdVec,
				new Comparator<GeneData>() {
					public int compare(GeneData a1, GeneData a2) { 
						if (a1.start!=a2.start) return (a1.start - a2.start); // it does not always catch =start
						return (a2.len - a1.len);
					}
				});
			// first is level 0; 
			for (int i=1; i<gdVec.size(); i++) {
				GeneData gdi = gdVec.get(i);
				int [] lev = {0, 0, 0};
				
				for (int j=0; j<i; j++) {
					GeneData gdj = gdVec.get(j);
					int olap = Math.min(gdi.end, gdj.end) - Math.max(gdi.start, gdj.start);
					if (olap >= 0) lev[gdj.level]++;		
				}
				
				if (lev[0]==0)      gdi.level = 0;
				else if (lev[1]==0) gdi.level = 1;
				else if (lev[2]==0) gdi.level = 2;
				else {
					if (lev[0]<lev[1] && lev[0]<lev[2]) gdi.level = 0;
					else if (lev[1]<lev[2]) gdi.level = 1;
					else gdi.level = 2;
				}
			}
			
			for (GeneData gd : gdVec) {
				if (gd.level==0) continue;
				
				int offset = gd.level* OVERLAP_OFFSET;
				olapMap.put(gd.annot.getAnnoIdx(), offset);
			}
		}
		catch (Exception e) {ErrorReport.print(e, "Place overlapping genes");}
	}
	private class GeneData { // for buildPlace
		Annotation annot;
		int start, end, len;
		int level=0;
	
		public GeneData(Annotation annot) {
			this.annot = annot;
			start = annot.getStart();
			end =   annot.getEnd();
			len =   Math.abs(end-start)+1;
		}	
	}
	/********************************************************************************
	 * XXX SeqFilter Conserved Genes; These methods are called from the reference track from Sequence
	 ****************************************************************************/
	/**
	 *  CAS570 stop using intermediate class, better naming of variables, fix problem of filtered G2 showing one side only
	 *  Confusing: Left/right tracks; Hits have left/right possible genes.
	 *  Green: high&anno, visible g2/g1
	 *  Pink:  high&force, non-visible but completes g2: green/pink, g1: brown/pink
	 *  Red:   force, visible g1 that could be possible g2: brown/red, g1: green/red
	 *  Need to set for L, Ref, R:
	 *  	SeqHits.isG2x2, for each hit conditional HitData.isHighG2xN and isHighForce 
	 *  	Sequence.isG2x2 for ref called from Sfilte
	 ***/
	protected void computeG2xN(boolean isG2x2,  SeqHits hitsObjL, SeqHits hitsObjR, Sequence seqObjRef) {
	try {	
		if (seqObjRef.allAnnoVec.size()==0) return;
		
		// Get Ref genes from 1st track (same for 3rd) and create Hash map
		boolean isRefSeq1L = (seqObjRef==hitsObjL.getSeqObj1()); 
		boolean isRefSeq1R = (seqObjRef==hitsObjR.getSeqObj1()); // may be different for right
		
		Vector <Integer> gidxRefVec = (isRefSeq1L) ? hitsObjL.getSeqObj1().getGeneIdxVec() : 
										             hitsObjL.getSeqObj2().getGeneIdxVec();
		
		HashMap <Integer, Mark> gnMarkMap = new HashMap <Integer, Mark> (); //geneIdx, mark class
		for (int idx : gidxRefVec) gnMarkMap.put(idx, new Mark());
		gidxRefVec.clear();
	
	/* Mark */
		// Visible g2 - add hd and vis
		int cntNoGene=0;
		Vector <HitData> visHitSetL = hitsObjL.getVisG2Hits(); 
		
		for (HitData hd : visHitSetL) {
			int idx = (isRefSeq1L) ? hd.getAnnot1() : hd.getAnnot2();
			if (gnMarkMap.containsKey(idx)) {
				Mark mk = gnMarkMap.get(idx);
				mk.addL(hd, true);
			}
			else if (cntNoGene++<2) Globals.dprt("No geneIdx " + idx + " for hit " + hd.getHitNum()); 
		}
		visHitSetL.clear(); 
		
		Vector <HitData> visHitSetR = hitsObjR.getVisG2Hits();
		for (HitData hd : visHitSetR) {
			int idx = (isRefSeq1R) ? hd.getAnnot1() : hd.getAnnot2();
			if (gnMarkMap.containsKey(idx)) {
				Mark mk = gnMarkMap.get(idx);
				mk.addR(hd, true);
			}
			else if (cntNoGene++<2) Globals.dprt("No geneIdx " + idx + " for hit " + hd.getHitNum()); 
		}
		visHitSetR.clear();
		
		if (cntNoGene>0) {										// should not happen...
			System.out.println("Possible sync problem - genes not found: " + cntNoGene);
			return;
		}
		 
		// Filtered g2 - complete g2 if no existing hit-wire, only add one
		Vector <HitData> invHitSetL = hitsObjL.getInVisG2Hits(); 
		for (HitData hd : invHitSetL) {
			int idx = (isRefSeq1L) ? hd.getAnnot1(): hd.getAnnot2();
			Mark mk = gnMarkMap.get(idx);
			if (mk.oHitArrL==null && mk.oHitArrR!=null) mk.addL(hd, false);
		}
		invHitSetL.clear();
		
		Vector <HitData> invHitSetR = hitsObjR.getInVisG2Hits(); 
		for (HitData hd : invHitSetR) {
			int idx = (isRefSeq1R) ? hd.getAnnot1(): hd.getAnnot2();
			Mark mk = gnMarkMap.get(idx);
			if (mk.oHitArrL!=null && mk.oHitArrR==null) mk.addR(hd, false);
		}
		invHitSetR.clear();
	
	/* Highlight */
		// Green: Highlight visible g2/g1 
		for (Mark mk : gnMarkMap.values()) { 
			if (mk.oHitArrL!=null) {
				for (int i=0; i<mk.oHitArrL.size(); i++) {	// mark all left hit ref
					if (mk.isG2(isG2x2)) {					
						HitData hd  = mk.oHitArrL.get(i);
						hd.setHighAnnoG2xN(true);			// green hit, blue anno on both sides
						mk.doneL=true;
					}
				}
			}
			if (mk.oHitArrR!=null) {
				for (int i=0; i<mk.oHitArrR.size(); i++) { // mark all right hit ref
					if (mk.isG2(isG2x2)) {
						HitData hd = mk.oHitArrR.get(i);
						hd.setHighAnnoG2xN(true);		 // green hit, blue anno on both sides  (2x ref gets done 2x)	
						mk.doneR=true;
					}
				}
			}
		}
		// Pink: g2 where one is invisible; only do one to show it is not g1
		for (Mark mk : gnMarkMap.values()) {
			if (mk.isUseFilt(true)) { // both sides have hits but L filt
				HitData hd = mk.oHitArrL.get(0);
				if (isG2x2) hd.setHighAnnoG2xN(true); // blue anno because real g2
				hd.setHighForceG2xN(true);			  // only force pink
				mk.doneL=true;
			}
			if (mk.isUseFilt(false)) {// both sides have hits but R filt
				HitData hd = mk.oHitArrR.get(0);
				if (isG2x2) hd.setHighAnnoG2xN(true);
				hd.setHighForceG2xN(true);
				mk.doneR=true;
			}
		}
		
		// Red: g1 visible: use to complete g2 
		int side0 = (isRefSeq1L) ? 2 : 1;			// not necessary as ref-no-gene not in gnMarkMap, but reduces hits
		Vector <HitData> g1HitSetL = hitsObjL.getVisG1Hits(side0); 
		for (HitData hd : g1HitSetL) {
			int idx = (isRefSeq1L) ? hd.getAnnot1(): hd.getAnnot2();
			if (!gnMarkMap.containsKey(idx)) continue;
			
			Mark mk = gnMarkMap.get(idx);
			if (!mk.doneL && mk.oHitArrR!=null) hd.setForceG2xN(true);
		}
		g1HitSetL.clear();
		
		side0 = (isRefSeq1R) ? 2 : 1;
		Vector <HitData> g1HitSetR = hitsObjR.getVisG1Hits(side0); 
		for (HitData hd : g1HitSetR) {
			int idx = (isRefSeq1R) ? hd.getAnnot1(): hd.getAnnot2();
			if (!gnMarkMap.containsKey(idx)) continue;
			
			Mark mk = gnMarkMap.get(idx);
			if (!mk.doneR && mk.oHitArrL!=null) hd.setForceG2xN(true);
		}
		g1HitSetR.clear();
		
		gnMarkMap.clear();
	}
	catch (Exception e) {ErrorReport.print(e, "Finding g2xN genes");}
	}
	
	private class Mark { 
		boolean doneL=false, doneR=false;
		ArrayList <HitData> oHitArrL=null, oHitArrR=null;
		ArrayList <Boolean> bVisArrL=null, bVisArrR=null;
		
		private void addL(HitData hd, boolean vis) {
			if (oHitArrL==null) {
				oHitArrL = new ArrayList <HitData> (3);
				bVisArrL= new ArrayList <Boolean> (3);
			}
			oHitArrL.add(hd);
			bVisArrL.add(vis);
		}
		private void addR(HitData hd, boolean vis) {
			if (oHitArrR == null) {
				oHitArrR = new ArrayList <HitData> (3);
				bVisArrR = new ArrayList <Boolean> (3);
			}
			oHitArrR.add(hd);
			bVisArrR.add(vis);
		}
		private boolean isUseFilt(boolean isL) { // both sides have hits, one filt one not
			if (oHitArrL==null || oHitArrR==null) return false;
			if ( isL && !bVisArrL.get(0) &&  bVisArrR.get(0)) return true; // force filtered L
			if (!isL &&  bVisArrL.get(0) && !bVisArrR.get(0)) return true; // force filtered R
			return false;
		}
		
		private boolean isG2(boolean isBoth) {
			if (isBoth) {
				return oHitArrL!=null && oHitArrR!=null;
			}
			return (oHitArrL==null && oHitArrR!=null) || (oHitArrL!=null && oHitArrR==null);
		}
	}
}
