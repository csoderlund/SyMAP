package backend.synteny;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Vector;

import database.DBconn2;
import symap.Globals;
import util.ErrorReport;

/*********************************************************
 * This contains the major read/write DB methods
 */
public class RWdb {
	private final String orientSame = SyntenyMain.orientSame;
	private final String orientDiff = SyntenyMain.orientDiff;
	
	private DBconn2 tdbc2;
	private int mPairIdx, mProj1Idx, mProj2Idx;
	
	protected RWdb (int mPairIdx, int mProj1Idx, int mProj2Idx, DBconn2 tdbc2) {
		this.mPairIdx = mPairIdx;
		this.mProj1Idx = mProj1Idx;
		this.mProj2Idx = mProj2Idx;
		this.tdbc2 = tdbc2;
	}
	/***************************************************** 
	 * Load hits for chromosome pair 
	 * ***************************************************/
	protected Vector <SyHit> loadHitsForGrpPair(int grpIdx1, int grpIdx2, String orient) {
	try {
		Vector<SyHit> hits = new Vector<SyHit>();
		
      	String st = "SELECT h.idx, h.hitnum, h.start1, h.end1, h.start2, h.end2, h.pctid, h.runnum, h.gene_overlap" + 
            " FROM pseudo_hits as h " +  
            " WHERE h.proj1_idx=" + mProj1Idx  + " AND h.proj2_idx=" +  mProj2Idx +
            " AND h.grp1_idx=" + grpIdx1 + " AND h.grp2_idx=" + grpIdx2 ;
      	
      	if (orient.equals(orientSame))      st += " AND (h.strand = '+/+' or h.strand = '-/-')";
      	else if (orient.equals(orientDiff)) st += " AND (h.strand = '+/-' or h.strand = '-/+')";
      	
      	if (grpIdx1==grpIdx2) st += " AND h.start1 > h.start2 "; // DIR_SELF (old <) these have refIdx=0; refIdx>0 is used to mirror blocks
  	
		ResultSet rs = tdbc2.executeQuery(st);
		while (rs.next()) {
			int i=1;
			int id = rs.getInt(i++);
			int hitnum = rs.getInt(i++);
			int start1 = rs.getInt(i++);
			int end1 = rs.getInt(i++);

			int start2 = rs.getInt(i++);
			int end2 = rs.getInt(i++);
			int pctid = rs.getInt(i++);
			int runnum = rs.getInt(i++);
			int genes = rs.getInt(i++);
			
			hits.add(new SyHit(start1, end1, start2, end2,id, pctid, runnum, hitnum, genes));
		}
		rs.close();

		return hits;
	}
	catch (Exception e) {ErrorReport.print(e, "Write results to DB"); return null;}
	}
	
	/*************************************************************
	* Save blocks to DB
	* if bSwap is true, enter the synmetric block fo self-synteny
	 *************************************************************/
	protected void saveBlocksToDB(Vector<SyBlock> blocks, boolean bMirror)  {
	try {
		if (blocks.size()==0) return;
		
		if (!tdbc2.tableColumnExists("blocks", "avgGap1")) {// this is not a Version update; it is only for report;
			Globals.prt("Updating the database schema for the block table");
			tdbc2.tableCheckAddColumn("blocks", "avgGap1", "integer default 0", null);
			tdbc2.tableCheckAddColumn("blocks", "avgGap2", "integer default 0", null);
		}
		
		// Create blocks
		String sql= "insert into blocks (pair_idx, blocknum,";
		if (!bMirror) sql +=  "proj1_idx, grp1_idx, start1, end1, proj2_idx, grp2_idx, start2, end2, avgGap1, avgGap2, ";
		else        sql +=  "proj2_idx, grp2_idx, start2, end2, proj1_idx, grp1_idx, start1, end1, avgGap2, avgGap1, ";	
	
		sql += "score, corr, comment, ngene1, ngene2, genef1, genef2) "
				+ "values (?,?, ?,?,?,?, ?,?,?,?, ?,?,  ?,?,?,  0, 0, 0.0, 0.0)";
		
		PreparedStatement ps = tdbc2.prepareStatement(sql);
		
		for (SyBlock b : blocks) { 
			int i=1;
			ps.setInt(i++, mPairIdx);
			ps.setInt(i++, b.mBlkNum);
			
			ps.setInt(i++, mProj1Idx);
			ps.setInt(i++, b.mGrpIdx1);
			ps.setInt(i++, b.mS1);
			ps.setInt(i++, b.mE1);
			
			ps.setInt(i++, mProj2Idx);
			ps.setInt(i++, b.mGrpIdx2);
			ps.setInt(i++, b.mS2);
			ps.setInt(i++, b.mE2);
			
			if (bMirror) {ps.setInt(i++, 0);ps.setInt(i++, 0);} // avgGap=0 indicates mirrored; used in Report
			else {
				ps.setInt(i++, (int) b.avgGap1);
				ps.setInt(i++, (int) b.avgGap2);
			}
			
			ps.setInt(i++,    b.nHits);
			ps.setFloat(i++,  b.mCorr1);
			ps.setString(i++, ("Avg %ID:" + b.avgPctID())); // tinytext (256char)
		
			ps.executeUpdate();
		}
		ps.close();
		
		// Create pseudo_block_hits; first, get block idx (if bSwap, swapped above so can use start1...)
		for (SyBlock b : blocks) { 
			String st = "SELECT idx FROM blocks WHERE pair_idx=" + mPairIdx +
					" AND blocknum=" + b.mBlkNum + 
					" AND proj1_idx=" + mProj1Idx + " AND grp1_idx=" + b.mGrpIdx1 +
					" AND proj2_idx=" + mProj2Idx + " AND grp2_idx=" + b.mGrpIdx2 +
					" AND start1=" + b.mS1 + " AND end1=" + b.mE1 +
					" AND start2=" + b.mS2 + " AND end2=" + b.mE2;   
			b.mIdx = tdbc2.executeInteger(st);
			if (b.mIdx==-1) ErrorReport.die("SyMAP error, cannot get block idx \n" + st);
		}
	
		if (bMirror) { // blocks were created with hits with refidx=0, update reverse hits with refidx>0
			for (SyBlock b : blocks) {
				for (SyHit ht : b.hitVec) { // find  
					if (ht.mIdx>0) {
						int s = ht.mIdx;
						ht.mIdx = tdbc2.executeInteger("select idx from pseudo_hits where refidx=" + ht.mIdx);
						if (ht.mIdx<=0 && SyntenyMain.bTrace) 
							Globals.prt(String.format("%6d no refidx: %s   ", s, ht));
					}
				}
			}
		}
		PreparedStatement ps2 = tdbc2.prepareStatement(
				"insert into pseudo_block_hits (hit_idx, block_idx) values (?,?)");
		for (SyBlock bk : blocks) {
			for (SyHit ht : bk.hitVec) {
				if (ht.mIdx>0) {
					ps2.setInt(1, ht.mIdx); 
					ps2.setInt(2, bk.mIdx);
					ps2.executeUpdate(); 
				}
			}
		}
		ps2.close();
		
		/** compute final block fields **/
		if (bMirror) return; // do not need for mirrored
		
		// count genes1 with start1 and end2 of block
		tdbc2.executeUpdate("update blocks set "
				+ "ngene1 = (select count(*) from pseudo_annot as pa " +
				  "where pa.grp_idx=grp1_idx and greatest(pa.start,start1) < least(pa.end,end1) " +
				"  and pa.type='gene') where pair_idx=" + mPairIdx);
		
		tdbc2.executeUpdate("update blocks set "
				+ "ngene2 = (select count(*) from pseudo_annot as pa " +
				" where pa.grp_idx=grp2_idx  and greatest(pa.start,start2) < least(pa.end,end2) " +
				" and pa.type='gene') where pair_idx=" + mPairIdx);

		// compute gene1 in grp1 that have hit, and divide by ngene1
		tdbc2.executeUpdate("update blocks set "
				+ "genef1=(select count(distinct annot_idx) from  " +
				" pseudo_hits_annot      as pha " +
				" join pseudo_block_hits as pbh on pbh.hit_idx=pha.hit_idx " +
				" join pseudo_annot      as pa  on pa.idx=pha.annot_idx " +
				" where pbh.block_idx=blocks.idx and pa.grp_idx=blocks.grp1_idx)/ngene1 " +
				" where ngene1 > 0 and pair_idx=" + mPairIdx);
		
		tdbc2.executeUpdate("update blocks set "
				+ "genef2=(select count(distinct annot_idx) from  " +
				" pseudo_hits_annot as pha " +
				" join pseudo_block_hits as pbh on pbh.hit_idx=pha.hit_idx " +
				" join pseudo_annot as pa on pa.idx=pha.annot_idx " +
				" where pbh.block_idx=blocks.idx and pa.grp_idx=blocks.grp2_idx)/ngene2 " +
				" where ngene2 > 0 and pair_idx=" + mPairIdx);
				
	} catch (Exception e) {ErrorReport.die(e, "Writing blocks to DB"); }
	}
	
	 /** CAS575 replace with above: symmetrizeBlocks() {} Self-synteny Add the mirror reflected blocks for self alignments **/ 
}
