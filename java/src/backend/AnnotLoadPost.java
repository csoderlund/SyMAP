package backend;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Vector;
import java.util.HashMap;
import java.util.TreeMap;

import symap.manager.Mproject;
import database.DBconn2;
import util.Cancelled;
import util.ErrorReport;
import util.ProgressDialog;
import util.Utilities;

/******************************************************
 * CAS512 added for post-processing of annotation
v5.1.2 	Exons pseudo_annot.gene_idx and tag to map exon to gene in AnnotLoadMain (was doing here too, but removed 518)
		Genenum was computed in AnchorsPost, then AnnotLoadMain
v5.1.4	Gene# Replaced the result table #Gene column: it was computed per query, whereas the replacement is stored in the database. The replacement is the order number of the gene along the chromosomes, which stays constant across all queries and is shown on the Chromosome Explorer Annotation and the hover of the gene.
v5.1.7 	Gene# Overlapping genes have been numbered with the same gene number; now they are given a suffix, e.g. Gene #1011a, Gene #1011b.
v5.1.8 	Gene# Overlapping genes: the algorithm has been redone so that all overlapping and contained genes have the same number with suffixes a-z; and if there are more than 26 overlapping genes, then it starts over with numbers following the a-z; e.g. Gene #100a, #100z, #100a1, #100b1, ...
		No longer support having exons in separate file.
v5.1.9	Gene # This computes the Gene#, and was allowing a gap of 3 without calling them overlap; this has been changed to 0.

AnchorsPost uses genenum for collinear computation 
The 2D gene placement algorithm uses the pseudo_annot.genenum but not the suffix from the pseudo_annot.tag 
 */

public class AnnotLoadPost {
	private final int MAX_GAP_FOR_OLAP=0; // CAS519 changed from 3
	private ProgressDialog plog;
	private DBconn2 dbc2;
	private Mproject mProj;      // CAS546 was syProj
	
	private HashMap <Integer, GeneData> geneMap = new HashMap <Integer, GeneData> (); 
	private Vector <Integer> geneOrder = new Vector <Integer> ();
	private int genenum;
	private boolean isSuccess=true;
	private int totexonUpdate=0, totgeneUpdate=0, totOverlap=0, totContained=0, totGeneNum=0;
	
	public AnnotLoadPost(Mproject project, DBconn2 dbc2, ProgressDialog log) {
		this.mProj = project;
		this.dbc2 = dbc2;
		this.plog = log;
	}
	public boolean run(boolean hasAnnot) {
		try {
			if (!hasAnnot) {
				plog.msg("  No assignment of #exons and tags to genes");
				return true;
			}
			plog.msg("  Assign #exons and tags to genes");
			
			dbc2.executeUpdate("update pseudo_annot, xgroups set pseudo_annot.genenum=0 "
					+ "where pseudo_annot.grp_idx=xgroups.idx and xgroups.proj_idx=" + mProj.getIdx());
			
			TreeMap <Integer, String> grpIdxList = mProj.getGrpIdxMap();
			for (int idx : grpIdxList.keySet()){
				//System.err.print("Process " + g.getName() + "                \r");
				computeGeneNum(idx, grpIdxList.get(idx)); if (!isSuccess) return isSuccess;	// CAS512 moved from SyntenyMain
				computeTags(idx, grpIdxList.get(idx));    if (!isSuccess) return isSuccess;	
			}
			System.err.print("                                                 \r");
			Utils.prtNumMsg(plog, totGeneNum,      "Unique gene numbers");
			Utils.prtNumMsg(plog, totOverlap,   "Overlapping genes");
			Utils.prtNumMsg(plog, totContained, "Contained genes");
			
			Utils.prtNumMsg(plog, totgeneUpdate, "Gene update");
			Utils.prtNumMsg(plog, totexonUpdate, "Exon update (avg " +  
						String.format("%.3f", (float) totexonUpdate/(float)totgeneUpdate) + ")");
			
			return true;				
		}
		catch (Exception e) {ErrorReport.print(e, "Post process annotation"); return false;}
	}
	/*********************************************************
	 * Assign Gene#
	 */
	private void computeGeneNum(int grpIdx, String grpName) {
		try {	
			ResultSet rs = dbc2.executeQuery(
				"select idx,start,end from pseudo_annot where grp_idx=" + grpIdx + 
				" and type='gene' order by start asc");
			
			while (rs.next()){
				GeneData gd = new GeneData ();
				gd.idx = rs.getInt(1);
				gd.start = rs.getInt(2);
				gd.end = rs.getInt(3);
				geneMap.put(gd.idx, gd);
				geneOrder.add(gd.idx);
			}
			if (Cancelled.isCancelled()) {isSuccess=false;return;}
			
			Vector <GeneData> numList = new Vector <GeneData> ();
			for (int idx : geneOrder) {
				GeneData gd = geneMap.get(idx);
				
				boolean bUseNum=false;
				
				// go through all because because isContained and isOverlap add counts
				for (GeneData gx : numList) { 
					if      (gx.isContained(gd)) bUseNum=true;
					else if (gx.isOverlap(gd))   bUseNum=true;
				}
				if (bUseNum) {
					gd.genenum = genenum;
					numList.add(gd);
				}
				else {
					assignSuf(genenum, numList);		// end current num
					numList.clear();
					
					genenum++;				// start new num
					gd.genenum = genenum;
					numList.add(gd);
				}
			}
			assignSuf(genenum, numList); // last one
			
			for (GeneData gd : geneMap.values()) gd.tag = gd.genenum + "." + gd.suffix; // CAS543 "Gene #" + gd.genenum + gd.suffix; 
			
			System.err.print("   " + grpName + " " + genenum + " genes                    \r");
			totGeneNum+= genenum;
			genenum=0;
		}
		catch (Exception e) {ErrorReport.print(e, "Compute gene order"); isSuccess=false;}
	}
	private void assignSuf(int genenum, Vector <GeneData> numList) {
		if (numList.size()<=1) return;
		
		char [] alpha = {'a','b','c','d','e','f','g','h', 'i','j','k','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z'};
		int ialpha=0, icnt=1;
		
		// assign gd.suf to each
		boolean isRepeat=false;
		for (GeneData gd : numList) {
			gd.suffix +=  alpha[ialpha++]+"";
			if (isRepeat)  gd.suffix += icnt+"";
			
			if (ialpha>=alpha.length) {
				if (!isRepeat) {
					isRepeat=true;
					if (Constants.TRACE) 
						System.out.println(numList.size() + " overlapping genes for gene #" + genenum);
				}
				ialpha=0;
				icnt++;
			}
			if (Constants.TRACE) {
				if (gd.cntHasIn>3 || gd.cntOverlap>3)
					System.out.println("Gene #" + gd.genenum + gd.suffix 
							+ " has contained "  + gd.cntHasIn + " overlap " + gd.cntOverlap);
				if (gd.cntIsIn>1) 
					System.out.println("Gene #" + gd.genenum + gd.suffix + " is contained in " + gd.cntIsIn + " genes");
			}
		}
	}
	/************************************************************
	 * Assign tags for Genes and Exons
	 */
	private void computeTags(int grpIdx, String grpName) {
		try {	
		// Create exon list per gene; provides exon cnt for gene, and exonIdx for tag update
			ResultSet rs = dbc2.executeQuery("select idx, gene_idx, start, end from pseudo_annot "
				+ "where grp_idx=" + grpIdx + " and type='exon'"); 
			
			int err=0;
			while (rs.next()) {
				int idx = 		rs.getInt(1);
				int gene_idx = 	rs.getInt(2);
				
				if (geneMap.containsKey(gene_idx)) {
					GeneData gd = geneMap.get(gene_idx);
					gd.exonIdx.add(idx);
					gd.exonLen += (rs.getInt(4)-rs.getInt(3)+1);
				}
				else {
					err++;
					if (err>10) break;
					System.err.println("Unexpected GFF input: no gene " + gene_idx + " for exon " + idx);
				}
			}
			rs.close();
			if (err>5 && symap.Globals.GENEN_ONLY) {
				Utilities.showWarningMessage("This SyMAP database was built with an old version.\n"
					+ "The -z will not work with it. You will need to reload.");
				isSuccess=false;
				return;
			}
			if (Cancelled.isCancelled()) {isSuccess=false;return;}
				
		// WRITE write to db; names parsed in Annotation.java getLongDescription
			PreparedStatement ps = dbc2.prepareStatement(
					"update pseudo_annot set genenum=?, tag=? where idx=?");
			
			int exonUpdate=0, geneUpdate=0, cntBatch=0;
			
			for (int geneidx : geneOrder) {
				GeneData gene = geneMap.get(geneidx);
				
				// Annotation.java and Sequence.java depends on this format! Gene #dn (d, dbp(
				String elen = String.format(" %,d", gene.exonLen);
				String tag = gene.tag + " (" + gene.exonIdx.size() + elen + ")"; // CAS543 " (" + gene.exonIdx.size() + elen + "bp)"

				// update gene
				ps.setInt(1, gene.genenum);
				ps.setString(2, tag);  
				ps.setInt(3,  gene.idx); 
				ps.addBatch();
				cntBatch++; geneUpdate++;
				
				// update gene's exons
				int nExon=1;
				for (int i=0; i<gene.exonIdx.size(); i++) {
					int exonIdx = gene.exonIdx.get(i);
					tag = ""+nExon;							// CAS543 was "Exon #" + nExon;
					nExon++;
					
				    ps.setInt(1, 0);
					ps.setString(2, tag);  // newly created tag
					ps.setInt(3,  exonIdx);   // update exon
					ps.addBatch();
					cntBatch++; exonUpdate++;
				}
				if (cntBatch>5000) {
					if (Cancelled.isCancelled()) {isSuccess=false;return;}
					ps.executeBatch();
					cntBatch=0;
					System.err.print("   " + grpName + " count " + exonUpdate + " exons for "  + geneUpdate + " genes        \r");
				}
			}
			System.err.print("                                                                         \r");
			if (cntBatch>0) ps.executeBatch();
			totexonUpdate += exonUpdate;
			totgeneUpdate += geneUpdate;

			geneMap.clear(); geneOrder.clear();
		}
		catch (Exception e) {ErrorReport.print(e, "Assign tags"); isSuccess=false;}
	}
	
	/************************************************************/
	private class GeneData {
		int idx, start, end, genenum;
		String tag;
		String suffix="";
		Vector <Integer> exonIdx = new Vector <Integer> ();
		int exonLen=0;
		
		boolean isContained(GeneData gd) {
			if (gd.start >= start && gd.end <= end) {
				cntHasIn++;
				gd.cntIsIn++;
				totContained++;
				return true;
			}
			return false;
		}
		private boolean isOverlap(GeneData gd) {
			int gap = Math.min(end,gd.end) - Math.max(start,gd.start);
			if (gap <= MAX_GAP_FOR_OLAP) return false;
			cntOverlap++;
			gd.cntOverlap++;
			totOverlap++;
			return true;
		}
		int cntHasIn=0, cntIsIn=0, cntOverlap=0;
	}	
}
