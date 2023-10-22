package symap.sequence;

import java.sql.ResultSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;
import java.util.HashMap;

import database.DBconn2;
import number.GenomicsNumber;
import symap.Globals;
import symap.mapper.HitData;
import symap.mapper.SeqHits;
import util.ErrorReport;

/**
 * The SeqPool load data for Sequence Track and run a few of its algorithm.
 * CAS531 removed dead cache; CAS541 removed DBAbsUser
 * CAS543 moved stuff immediately to Annotation; by-pass AnnotationData and PseudoData; CAS544 add seqObj
 * CAS545 moved two computation into this file;  (SeqPool is a bit of a miss-name now, but there wasn't much left there)
 */
public class SeqPool {	
	private DBconn2 dbc2;
	
	public SeqPool(DBconn2 dbc2) { this.dbc2 = dbc2;}

	public synchronized void close() {}

	public int getNumAllocs(int grpIdx) { // CAS545 added so can exactly alloc correct size for Sequence.allAnnoVec
		try {
			return dbc2.executeCount("select count(*) from pseudo_annot where grp_idx=" + grpIdx);
		}
		catch (Exception e) {ErrorReport.print(e, "Getting number of annotations for " + grpIdx); return 0;}
	}
	/**
	 * Called by Sequence object in init
	 * gnsize - enter setBPsize; Annotation Vector = enter annotation objects 
	 */
	public synchronized String loadSeqData(Sequence seqObj, GenomicsNumber gnsize, Vector<Annotation> annoVec) {
		int grpIdx =   seqObj.getGroup();		
		
		String name=null, type, desc;
		int start, end; 
		String strand, tag; // CAS512 new variables
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
			gnsize.setBPValue(rs.getLong(1));
			name = rs.getString(2);
			
			HashMap <Integer, Annotation> geneMap = new HashMap <Integer, Annotation>  ();
			
			String annot_query =		// CAS520 add numhits CAS545 remove " ORDER BY type DESC";
					"SELECT idx, type,name,start,end,strand, genenum, gene_idx, tag, numhits FROM pseudo_annot " 
					+ " WHERE grp_idx=" + grpIdx + " and ";  
			
			for (int i=0; i<2; i++) {
				String sql = (i==0) ? " type='gene' " : " type!='gene' ";
				rs = dbc2.executeQuery(annot_query + sql + " order by idx");
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
					if (type.equals("gene")) itype = Annotation.GENE_INT;
					else if (type.equals("exon")) itype = Annotation.EXON_INT;
					else if (type.equals("gap")) itype = Annotation.GAP_INT;
					else if (type.equals("centromere")) itype = Annotation.CENTROMERE_INT;
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
								return a1.getGeneNum()-a2.getGeneNum(); // CAS517 sort on gene#
						}	
						return (a1.getType() - a2.getType()); 			  
					}
				}
			);
			
		} catch (Exception sql) {ErrorReport.print(sql, "SQL exception acquiring sequence data.");}
		
		return name;
	}
	/*******************************************************
	 * CAS518 added: Called during init(); used in build()
	 */
	private final int OVERLAP_OFFSET=12;			// CAS517/518 for overlap and yellow text
	public void buildOlap(HashMap <Integer, Integer> olapMap, Vector <Annotation> geneVec) {
		int lastGeneNum=-1;
		Vector <Annotation> numList = new Vector <Annotation> ();
		
		 for (Annotation annot : geneVec) {
		    	//if (!annot.isGene()) continue; CAS545 change to geneVec
		    	//if (annot.isExon()) break; // at end
		    	
				if (annot.getGeneNum() == lastGeneNum) {
					numList.add(annot);
					continue;
				}
				
				buildPlace(olapMap, numList);
				numList.clear();
				
				lastGeneNum = annot.getGeneNum();
				numList.add(annot);
		 }
		 buildPlace(olapMap, numList); // CAS519 missing last one
		 
		 if (olapMap.size()==0) olapMap.put(-1,-1); // need at least one value so will not compute again
	}
	/*******************************************************
	 * Compute how to display overlapping genes based on genenum
	 * CAS519 simplified algo; uses genenum to find set of overlapping, 
	 * but does not use genenum suffix (uses same overlap check as in AnnoLoadPost.computeGeneNum)
	 */
	private void buildPlace(HashMap <Integer, Integer> olapMap, Vector <Annotation> numList) {
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
		
		// Determine contained and overlap relations AND assign initial level	
			for (int i=0; i<gdVec.size()-1; i++) {
				GeneData gdi = gdVec.get(i);
				for (int j=i+1; j<gdVec.size(); j++) {
					GeneData gdj = gdVec.get(j);
					if (!gdi.isContain(gdj))
						 gdi.isOverlap(gdj);
				}
			}
			for (GeneData gd : gdVec) {
				for (GeneData o : gd.olVec) {
					if (gd.level==o.level)  {
						o.level = gd.level+1;
						if (o.level==3) o.level=0;
					}
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
	private class GeneData {
		Annotation annot;
		int start, end, len;
		int level=0;
		Vector <GeneData> olVec = new Vector <GeneData> ();
		
		public GeneData(Annotation annot) {
			this.annot=annot;
			start = annot.getStart();
			end = annot.getEnd();
			len = Math.abs(end-start)+1;
		}
		boolean isContain(GeneData gd) {
			if (gd.start >= start && gd.end <= end) {
				olVec.add(gd);
				return true;
			}
			return false;
		}
		private boolean isOverlap(GeneData gd) {
			int gap = Math.min(end,gd.end) - Math.max(start,gd.start);
			if (gap > 0) {
				olVec.add(gd);
				return true;
			}
			return false;
		}
	}
	/********************************************************************************
	 * SeqFilter Conserved Genes; add CAS545 These methods are called from the reference track
	 ****************************************************************************/
	/**
	 *  This finds gene->many and gene1/gene2->gene<-gene1/gene2 
	 *  On a back History (i.e. forceConserved), the hits have been updated but not the Sequence tracks
	 *  	this works because it just uses the hits (there could still be a sync problem...)
	 ***/
	public void flagConserved(SeqHits leftHitsObj, SeqHits rightHitsObj, Sequence refSeq) {
	try {	
		Vector <GeneHitGene> dualSet1 = createConservedPair(leftHitsObj);
		if (dualSet1.size()==0) return;
		
		if (rightHitsObj==null) {
			for (GeneHitGene g : dualSet1) g.setHigh();
			return;
		}
		
	/*  3 tracks */
		Vector <GeneHitGene> dualSet2 = createConservedPair(rightHitsObj);
		if (dualSet2.size()==0) return;
		
		// Find conserved: get Ref genes; mark found left, mark found right
		boolean isRefSeq1L = (refSeq==leftHitsObj.getSeqObj1()); 
		Vector <Integer> idxRef = (isRefSeq1L) ? 
								leftHitsObj.getSeqObj1().getGeneIdx() : 
								leftHitsObj.getSeqObj2().getGeneIdx();
		
		HashMap <Integer, Mark> geneMap = new HashMap <Integer, Mark> ();
		for (int idx : idxRef) geneMap.put(idx, new Mark());
		
		int cntNoGene=0;
		for (GeneHitGene cg : dualSet1) {
			int idx = (isRefSeq1L) ? cg.annot1_idx : cg.annot2_idx;
			if (geneMap.containsKey(idx)) geneMap.get(idx).mark1=true;
			else {
				if (Globals.DEBUG && cntNoGene<2) System.out.println("No geneIdx " + idx + " for hit " + cg.hitObj.getHitNum()); 
				cntNoGene++; continue; 
			}
		}
		boolean isRefSeq1R = (refSeq==rightHitsObj.getSeqObj1()); // may be different for right
		for (GeneHitGene cg : dualSet2) {
			int idx = (isRefSeq1R) ? cg.annot1_idx : cg.annot2_idx;
			if (geneMap.containsKey(idx)) geneMap.get(idx).mark2=true;
			else {
				if (Globals.DEBUG && cntNoGene<2) System.out.println("No geneIdx " + idx + " for hit " + cg.hitObj.getHitNum()); 
				cntNoGene++; continue; 
			}
		}
		if (cntNoGene>0) // the real problem is if the SeqHits is previous in history
			System.out.println("Possible sync problem - genes not found: " + cntNoGene);
		
		// highlight if left and right are marked
		for (GeneHitGene cg : dualSet1) {
			int idx = (isRefSeq1L) ? cg.annot1_idx : cg.annot2_idx;
			if (geneMap.containsKey(idx) && geneMap.get(idx).isConserved()) cg.setHigh();
		}
		for (GeneHitGene cg : dualSet2) {
			int idx = (isRefSeq1R) ? cg.annot1_idx : cg.annot2_idx;
			if (geneMap.containsKey(idx) && geneMap.get(idx).isConserved()) cg.setHigh();
		}
	}
	catch (Exception e) {ErrorReport.print(e, "Finding conserved");}
	}
	/**** Make the set of conserved genes for hit set ***/
	private Vector <GeneHitGene>  createConservedPair(SeqHits hitsObj) {
		Vector <GeneHitGene> dualSet = new Vector <GeneHitGene> ();
		try {	
			Vector <HitData> hitList = hitsObj.getVisGene2Hits();// only visible, unfiltered hits with genes at both ends
			
			for (HitData hd: hitList) { 
				GeneHitGene gn = new GeneHitGene(hd);
				dualSet.add(gn);
			}
			return dualSet;
		}
		catch (Exception e) {ErrorReport.print(e, "Finding dualGenes"); return dualSet;}
	}
	
	// Class
	private class GeneHitGene {
		private GeneHitGene(HitData h) {
			hitObj=h;
			annot1_idx = h.getAnnot1();
			annot2_idx = h.getAnnot2();
		}
		private void setHigh() {
			hitObj.setIsConserved(true);
		}
		private HitData hitObj=null;
		private int annot1_idx, annot2_idx;
	}
	private class Mark { // can have N->1 or 1->N, just need to know if marked on both sides
		boolean mark1=false, mark2=false;
		private boolean isConserved() {return mark1 && mark2;}
	}
}
