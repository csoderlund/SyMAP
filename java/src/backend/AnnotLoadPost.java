package backend;

/******************************************************
 * CAS512 added for post-processing of annotation
 *  geneNumber = assigns consecutive gene numbers along the chromosome
 *  genesMapExon = assigns exons to genes IF it was not done in AnnotLoadMain
 *  genesCntExon = if it is done, it assigns tag only
 */
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Vector;
import java.util.HashMap;

import util.ErrorReport;
import util.Logger;

public class AnnotLoadPost {
	private Logger log;
	private UpdatePool pool;
	private Project project;
	
	public AnnotLoadPost(Project project, UpdatePool pool, Logger log) {
		this.project = project;
		this.pool = pool;
		this.log = log;
	}
	public boolean run(int assigned) {
		try {
			genesNumber(); if (!isSuccess) return isSuccess;	// CAS512 moved from SyntenyMain
			
			if (assigned>0) genesCntExons(); // Genes+Exons in same file
			else 			genesMapExons(); 
			
			return isSuccess;				
		}
		catch (Exception e) {ErrorReport.print(e, "Post process annotation"); return false;}
	}
	/*********************************************************/
	private void genesNumber() {
		try {
			// First set the order number for the genes, using the same number for ones which overlap
			log.msg("  Compute gene order ");
			
			pool.executeUpdate("update pseudo_annot, xgroups set pseudo_annot.genenum=0 "
					+ "where pseudo_annot.grp_idx=xgroups.idx and xgroups.proj_idx=" + project.idx);
			
			int totalGeneUpdate=0;
			for (Group g : project.getGroups())
			{
				int genenum = 0;
				PreparedStatement ps = pool.prepareStatement( "update pseudo_annot set genenum=? where idx=?");
				ResultSet rs = pool.executeQuery(
					"select idx,start,end from pseudo_annot where grp_idx=" + g.idx + 
					" and type='gene' order by start asc");
				int sprev=-1, eprev=-1;
				while (rs.next())
				{
					int idx = rs.getInt(1);
					int s = rs.getInt(2);
					int e = rs.getInt(3);
					
					if (sprev == -1 || !Utils.intervalsOverlap(sprev, eprev, s, e, 0 /* max_gap*/)) {
						genenum++;
					}
					ps.setInt(1, genenum);
					ps.setInt(2, idx);
					ps.addBatch();
					sprev=s; eprev=e;
				}
				ps.executeBatch();
				totalGeneUpdate+=genenum;
				System.err.print("   " + g.getFullName() + " " + genenum + " genes        \r");
			}
			Utils.prtNumMsg(log, totalGeneUpdate, "Non-overlapping genes        ");
		}
		catch (Exception e) {ErrorReport.print(e, "Compute gene order"); isSuccess=false;}
	}
	
	/************************************************************
	 * Works for all genes/exons in the same file. 
	 */
	private void genesCntExons() {
		try {
			log.msg("  Assign #exons and tags to genes");
			
			PreparedStatement ps = pool.prepareStatement(
					"update pseudo_annot set tag=? where idx=?");
			
			int totexonUpdate=0, totgeneUpdate=0, totOverlap=0;
			char [] suffix = {'a','b','c','d','e','f','g','h', 'i','j','k','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z'};
			int isuf=0;
			
			for (Group grp : project.getGroups()) {
					// GENES all genes for this group/strand
					HashMap <Integer, GeneData> geneMap = new HashMap <Integer, GeneData> (); 
					Vector <Integer> geneOrder = new Vector <Integer> ();
					GeneData lastgd = null;
					
					ResultSet rs = pool.executeQuery(
						"select idx, start, genenum from pseudo_annot "
						+ " where grp_idx=" + grp.idx + " and type='gene' "
						+ " order by genenum, start"); 
					while (rs.next()) {
						GeneData gd = new GeneData ();
						gd.idx = rs.getInt(1);
						gd.start = rs.getInt(2);
						gd.genenum = rs.getInt(3);
						gd.tag = "Gene #" + gd.genenum;
						
						if (lastgd!=null && lastgd.genenum==gd.genenum) {
							totOverlap++;
							if (isuf==0) lastgd.tag += suffix[isuf++];
							gd.tag += suffix[isuf++];
							if (isuf>=suffix.length-2) {
								System.err.println("Too many overlapping genes for #" + lastgd.genenum);
								isuf=0;
							}
						}
						else isuf=0;
						
						lastgd = gd;
						geneMap.put(gd.idx, gd);
						geneOrder.add(gd.idx);
					}
					rs.close();
			    //---------------------------------------------------------------
				// EXONS all exons for this group/strand - assign to Gene curGD
					
					rs = pool.executeQuery(
						"select idx, gene_idx from pseudo_annot "
						+ "where grp_idx=" + grp.idx + " and type='exon'"); 
					while (rs.next()) {
						int idx = 		rs.getInt(1);
						int gene_idx = 	rs.getInt(2);
						
						if (geneMap.containsKey(gene_idx)) {
							GeneData gd = geneMap.get(gene_idx);
							gd.exonIdx.add(idx);
						}
						else {
							System.err.println("Internal error: no gene " + gene_idx + " for exon " + idx);
						}
					}
					rs.close();
				
					//---------------------------------------------------------------
					// WRITE write to db; names parsed in Annotation.java getLongDescription
					int exonUpdate=0, geneUpdate=0, cntBatch=0;
					
					for (int geneidx : geneOrder) {
						GeneData gene = geneMap.get(geneidx);
						String tag = gene.tag + " (" + gene.exonIdx.size() + ")"; /** Annotation.java depends on this format!**/
	
						ps.setString(1, tag);  // name
						ps.setInt(2,    gene.idx); // update gene
						ps.addBatch();
						cntBatch++; geneUpdate++;
						
						int nExon=1;
						for (int i=0; i<gene.exonIdx.size(); i++) {
							int exonIdx = gene.exonIdx.get(i);
							tag = "Exon #" + nExon;
							nExon++;
							
							ps.setString(1, tag);  // newly created tag
							ps.setInt(2,   exonIdx);   // update exon
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
			}
			ps.close(); 
			
			if (totOverlap>0) Utils.prtNumMsg(log, totOverlap, "Overlapping genes");
			Utils.prtNumMsg(log, totexonUpdate, "Exon update (avg " +  
						String.format("%.3f", (float) totexonUpdate/(float)totgeneUpdate) + ")");
		}
		catch (Exception e) {ErrorReport.print(e, "Assign tags"); isSuccess=false;}
	}
	/*****************************************************
	 * Assign gene idx to its exons
	 * This does not take into account embedded genes; this could be improved, but I think
	 * separate gene.gff and exon.gff files are obsolete
	 */
	private void genesMapExons() {
		try {
			log.msg("  Assign exons to genes");
			
			PreparedStatement ps = pool.prepareStatement(
					"update pseudo_annot set gene_idx=?, tag=? where idx=?");
			
			int totexonUpdate=0, totgeneUpdate=0, cntZero=0;
			String [] strand = {"+", "-"};
			
			for (Group grp : project.getGroups()) {
				for (String st : strand) {
					
					// GENES all genes for this group/strand
					Vector <GeneData> geneSet = new Vector<GeneData> (); 
					ResultSet rs = pool.executeQuery(
						"select idx,  start, end from pseudo_annot where grp_idx=" + grp.idx + 
						  " and strand='" + st + "' and type='gene' order by start, end"); // order doesn't matter
					while (rs.next()) {
						GeneData gd = new GeneData ();
						gd.idx = rs.getInt(1);
						gd.start = rs.getInt(2);
						gd.end = rs.getInt(3);
						geneSet.add(gd);
					}
					rs.close();
					if (geneSet.size()==0) { // CAS513 check added
						System.err.println("No genes loaded");
						isSuccess=false;
						return;
					}
			    //---------------------------------------------------------------
				// EXONS all exons for this group/strand - assign to Gene curGD
					GeneData curGD = geneSet.get(0);
					int ix=1;
					rs = pool.executeQuery(
						"select idx, start, end from pseudo_annot where grp_idx=" + grp.idx + 
						" and strand='" + st + "' and type='exon' order by start, end"); 
					while (rs.next()) {
						int idx = 		rs.getInt(1);
						int start = 	rs.getInt(2);
						int end = 		rs.getInt(3);
						
						while (!(start >= curGD.start && end <= curGD.end)) { // find parent gene
							if (ix >= geneSet.size())  {
								System.err.println("Internal error processing: " + idx);
								return;
							}
							curGD = geneSet.get(ix);
							ix++;	
						}
						if (curGD!=null) {
							curGD.exonIdx.add(idx);
						}
					}
					rs.close();
					curGD=null;
					//---------------------------------------------------------------
					// WRITE write to db; names parsed in Annotation.java getLongDescription
					
					int exonUpdate=0, geneUpdate=0, cntBatch=0;
					for (GeneData gene : geneSet) {
						String tag = "Gene (" + gene.exonIdx.size() + ")";
						if (gene.exonIdx.size()==0) cntZero++;
						ps.setInt(1,    0); 		// gene_idx
						ps.setString(2, tag);  // name
						ps.setInt(3,    gene.idx); // update gene
						ps.addBatch();
						cntBatch++; geneUpdate++;
						
						int nExon=1;
						for (int i=0; i<gene.exonIdx.size(); i++) {
							int exonIdx = gene.exonIdx.get(i);
							tag = "Exon #" + nExon;
							nExon++;
							
							ps.setInt(1,    gene.idx); // gene_idx
							ps.setString(2, tag);  // newly created tag
							ps.setInt(3,    exonIdx);   // update exon
							ps.addBatch();
							cntBatch++; exonUpdate++;
						}
						if (cntBatch>5000) {
							ps.executeBatch();
							cntBatch=0;
							System.err.print("   " + grp.getFullName() + " assign " + exonUpdate + " exons to "  + geneUpdate + " genes     \r");
						}
					}
					if (cntBatch>0) ps.executeBatch();
					totexonUpdate += exonUpdate;
					totgeneUpdate += geneUpdate;
				}
			}
			ps.close(); 
			
			Utils.prtNumMsg(log, totexonUpdate, "Exon update (avg " +  
						String.format("%.3f", (float) totexonUpdate/(float)totgeneUpdate) + ")                   ");
			if (cntZero>0) {
				Utils.prtNumMsg(log, cntZero, "Genes with no assigned exons");
				System.out.println("   This happens when there are nexted genes and separate exon and gene gff files");
			}
		}
		catch (Exception e) {ErrorReport.print(e, "Exons 2 genes"); isSuccess=false;}
	}	
	/************************************************************/
	private class GeneData {
		int idx, start, end, genenum;
		String tag;
		Vector <Integer> exonIdx = new Vector <Integer> ();
	}
	private boolean isSuccess=true;
}
