package backend;

/******************************************************
 * CAS512 added for post-processing of annotation
 *  geneNumber = assigns consecutive gene numbers along the chromosome
 *  genesMapExon = assigns exons to genes IF it was not done in AnnotLoadMain
 *  genesCntExon = if it is done, it assigns tag only
 *  
 * CAS518 no longer support having exons in separate file. Remove method genesMapExon.
 *        update assigning suffixes
 */
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Vector;
import java.util.HashMap;

import util.ErrorReport;
import util.Logger;

public class AnnotLoadPost {
	private final int MAX_GAP=0; // CAS519 changed from 3
	private Logger log;
	private UpdatePool pool;
	private Project project;
	
	private HashMap <Integer, GeneData> geneMap = new HashMap <Integer, GeneData> (); 
	private Vector <Integer> geneOrder = new Vector <Integer> ();
	private int genenum;
	private boolean isSuccess=true;
	private int totexonUpdate=0, totgeneUpdate=0, totOverlap=0, totContained=0, totGeneNum=0;
	
	public AnnotLoadPost(Project project, UpdatePool pool, Logger log) {
		this.project = project;
		this.pool = pool;
		this.log = log;
	}
	public boolean run(int assigned) {
		try {
			if (assigned==0) {
				log.msg("  No assignment of #exons and tags to genes");
				return true;
			}
			log.msg("  Assign #exons and tags to genes");
			
			pool.executeUpdate("update pseudo_annot, xgroups set pseudo_annot.genenum=0 "
					+ "where pseudo_annot.grp_idx=xgroups.idx and xgroups.proj_idx=" + project.idx);
			
			
			for (Group g : project.getGroups()){
				System.err.print("Process " + g.getName() + "                \r");
				computeGeneNum(g); if (!isSuccess) return isSuccess;	// CAS512 moved from SyntenyMain
				computeTags(g);    if (!isSuccess) return isSuccess;	
			}
			System.err.print("                                                 \r");
			Utils.prtNumMsg(log, totGeneNum,      "Unique gene numbers");
			Utils.prtNumMsg(log, totOverlap,   "Overlapping genes");
			Utils.prtNumMsg(log, totContained, "Contained genes");
			
			Utils.prtNumMsg(log, totgeneUpdate, "Gene update");
			Utils.prtNumMsg(log, totexonUpdate, "Exon update (avg " +  
						String.format("%.3f", (float) totexonUpdate/(float)totgeneUpdate) + ")");
			
			return true;				
		}
		catch (Exception e) {ErrorReport.print(e, "Post process annotation"); return false;}
	}
	/*********************************************************
	 * This does not work for long genes with many small embedded genes
	 * Find longs ones with embedded - they are z. The placement algorithm puts it in the middle
	 * and the others on either size.
	 */
	private void computeGeneNum(Group g) {
		try {	
			ResultSet rs = pool.executeQuery(
				"select idx,start,end from pseudo_annot where grp_idx=" + g.idx + 
				" and type='gene' order by start asc");
			
			while (rs.next()){
				GeneData gd = new GeneData ();
				gd.idx = rs.getInt(1);
				gd.start = rs.getInt(2);
				gd.end = rs.getInt(3);
				geneMap.put(gd.idx, gd);
				geneOrder.add(gd.idx);
			}
			
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
			
			for (GeneData gd : geneMap.values()) gd.tag = "Gene #" + gd.genenum + gd.suffix; 
			
			System.err.print("   " + g.getFullName() + " " + genenum + " genes        \r");
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
	private void computeTags(Group grp) {
		try {	
		// Create exon list per gene; provides exon cnt for gene, and exonIdx for tag update
			ResultSet rs = pool.executeQuery("select idx, gene_idx, start, end from pseudo_annot "
				+ "where grp_idx=" + grp.idx + " and type='exon'"); 
			
			while (rs.next()) {
				int idx = 		rs.getInt(1);
				int gene_idx = 	rs.getInt(2);
				
				if (geneMap.containsKey(gene_idx)) {
					GeneData gd = geneMap.get(gene_idx);
					gd.exonIdx.add(idx);
					gd.exonLen += (rs.getInt(4)-rs.getInt(3)+1);
				}
				else {
					System.err.println("Internal error: no gene " + gene_idx + " for exon " + idx);
				}
			}
			rs.close();
				
		// WRITE write to db; names parsed in Annotation.java getLongDescription
			PreparedStatement ps = pool.prepareStatement(
					"update pseudo_annot set genenum=?, tag=? where idx=?");
			
			int exonUpdate=0, geneUpdate=0, cntBatch=0;
			
			for (int geneidx : geneOrder) {
				GeneData gene = geneMap.get(geneidx);
				
				/** Annotation.java and Sequence.java depends on this format! Gene #dn (d, dbp) **/
				String elen = String.format(" %,d", gene.exonLen);
				String tag = gene.tag + " (" + gene.exonIdx.size() + elen + "bp)"; 

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
					tag = "Exon #" + nExon;
					nExon++;
					
				    ps.setInt(1, 0);
					ps.setString(2, tag);  // newly created tag
					ps.setInt(3,  exonIdx);   // update exon
					ps.addBatch();
					cntBatch++; exonUpdate++;
				}
				if (cntBatch>5000) {
					ps.executeBatch();
					cntBatch=0;
					System.err.print("   " + grp.getFullName() + " assign " + exonUpdate + " exons to "  + geneUpdate + " genes    \r");
				}
			}
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
			if (gap <= MAX_GAP) return false;
			cntOverlap++;
			gd.cntOverlap++;
			totOverlap++;
			return true;
		}
		int cntHasIn=0, cntIsIn=0, cntOverlap=0;
	}	
}
