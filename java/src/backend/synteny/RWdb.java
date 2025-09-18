package backend.synteny;

import java.io.File;
import java.io.FileWriter;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Vector;

import database.DBconn2;
import symap.Globals;
import backend.Constants;
import backend.Utils;
import util.ErrorReport;
import util.ProgressDialog;

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
	protected Vector <SyHit> loadHits(int grpIdx1, int grpIdx2, boolean isSelf, String orient) {
	try {
		Vector<SyHit> hits = new Vector<SyHit>();
		
      	String st = "SELECT h.idx, h.hitnum, h.start1, h.end1, h.start2, h.end2, h.pctid, h.runnum" + // CAS572 add runnum
            " FROM pseudo_hits as h " +  
            " WHERE h.proj1_idx=" + mProj1Idx  + " AND h.proj2_idx=" +  mProj2Idx +
            " AND h.grp1_idx=" + grpIdx1 + " AND h.grp2_idx=" + grpIdx2 ;
      	
      	if (orient.equals(orientSame)) { 
      		st += " AND (h.strand = '+/+' or h.strand = '-/-')";
      	}
      	else if (orient.equals(orientDiff)) {
      		st += " AND (h.strand = '+/-' or h.strand = '-/+')";
      	}
      	if (isSelf) {
      		st += " AND h.start1 < h.start2 "; // do blocks in lower diagonal, mirror later
      	}
  	
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
			
			// Ignore diagonal hits for self-alignments; This doesn't really work though because tandem gene families create many near-diagonal hits. 
			if (isSelf) {
				int pos1 = (start1 + end1)/2;
				int pos2 = (start2 + end2)/2;
				
				if (pos1 >= pos2) continue; // find only upper triangle blocks, and reflect them later.
				if (Utils.intervalsOverlap(start1, end1, start2, end2, 0)) continue;
			}	
			
			hits.add(new SyHit(start1, end1, start2, end2,id, pctid, runnum, hitnum));
		}
		rs.close();

		return hits;
	}
	catch (Exception e) {ErrorReport.print(e, "Write results to DB"); return null;}
	}
	
	/*************************************************************
	* Save blocks to DB
	 *************************************************************/
	protected void saveBlocksToDB(Vector<SyBlock> blocks)  {
	try {
		if (blocks.size()==0) return;
		if (!tdbc2.tableColumnExists("blocks", "avgGap1")) {// this is not a Version update; it is only for report; CAS572 
			Globals.prt("Updating the database schema for the block table");
			tdbc2.tableCheckAddColumn("blocks", "avgGap1", "integer default 0", null);
			tdbc2.tableCheckAddColumn("blocks", "avgGap2", "integer default 0", null);
			tdbc2.tableCheckAddColumn("blocks", "stdDev1", "integer default 0", null);
			tdbc2.tableCheckAddColumn("blocks", "stdDev2", "integer default 0", null);
		}
		PreparedStatement ps = tdbc2.prepareStatement("insert into blocks "
			+ "(pair_idx, blocknum, proj1_idx, grp1_idx, start1, end1, "
			+ "proj2_idx, grp2_idx, start2, end2, score, corr,  "
			+ "avgGap1, avgGap2, stdDev1, stdDev2, comment, "	// add 4 new fields (not in order) CAS572
			+ "ngene1, ngene2, genef1, genef2) "
			+ "values (?,?,?,?,?,?,  ?,?,?,?,?,?,  ?,?,?,?,?, "
			+ "        0, 0, 0.0, 0.0)");
		
		for (SyBlock b : blocks) { 
			ps.setInt(1, mPairIdx);
			ps.setInt(2, b.mBlkNum);
			
			ps.setInt(3, mProj1Idx);
			ps.setInt(4, b.mGrpIdx1);
			ps.setInt(5, b.mS1);
			ps.setInt(6, b.mE1);
			
			ps.setInt(7, mProj2Idx);
			ps.setInt(8, b.mGrpIdx2);
			ps.setInt(9, b.mS2);
			ps.setInt(10, b.mE2);
			
			ps.setInt(11,    b.n);
			ps.setFloat(12,  b.mCorr1);
			ps.setInt(13, (int) b.avgGap1);
			ps.setInt(14, (int) b.avgGap2);
			ps.setInt(15, (int) b.stdDev1);
			ps.setInt(16, (int) b.stdDev2);
			ps.setString(17, ("Avg %ID:" + b.avgPctID())); // tinytext (256char)
		
			ps.executeUpdate();
		}
		ps.close();
		
		ResultSet rs;
		for (SyBlock b : blocks) { // get block idx
			String st = "SELECT idx FROM blocks WHERE pair_idx=" + mPairIdx +
					" AND blocknum=" + b.mBlkNum + 
					" AND proj1_idx=" + mProj1Idx + " AND grp1_idx=" + b.mGrpIdx1 +
					" AND proj2_idx=" + mProj2Idx + " AND grp2_idx=" + b.mGrpIdx2 +
					" AND start1=" + b.mS1 + " AND end1=" + b.mE1 +
					" AND start2=" + b.mS2 + " AND end2=" + b.mE2;   
			
			rs = tdbc2.executeQuery(st);
			
			if (rs.next()) b.mIdx = rs.getInt(1);
			else ErrorReport.die("SyMAP error, cannot get block idx \n" + st);
		}
		
		PreparedStatement ps2 = tdbc2.prepareStatement("insert into pseudo_block_hits"
				+ "(hit_idx, block_idx) values (?,?)");
		for (SyBlock b : blocks) {
			for (SyHit h : b.mHits){
				ps2.setInt(1, h.mIdx);
				ps2.setInt(2, b.mIdx);
				ps2.executeUpdate(); 
			}
		}
		ps2.close();
		
		/** compute final block fields **/
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
				
	} catch (Exception e) {ErrorReport.print(e, "Writing blocks to DB"); }
	}
	
	/********************************************************************
	 * Self-synteny Add the mirror reflected blocks for self alignments 
	 * New fields added to pre-v572 databases on first addition of blocks
	 **/ 
	protected boolean symmetrizeBlocks()  {
		try {
			Vector<Integer> idxlist = new Vector<Integer>();
			ResultSet rs = tdbc2.executeQuery("select idx from blocks where pair_idx=" + mPairIdx + " and grp1_idx != grp2_idx ");
			while (rs.next()){
				idxlist.add(rs.getInt(1));
			}
			rs.close();
		
			for (int idx : idxlist) {
				tdbc2.executeUpdate("insert into blocks "
						+ "(select 0,pair_idx,blocknum,proj2_idx,grp2_idx,start2,end2,proj1_idx,grp1_idx,start1,end1," 
						+   "comment,corr,score,0,0,0,0,0,0,0,0 from blocks where idx=" + idx + ")"); // add 4 0's CAS572
				
				int newIdx = tdbc2.executeCount("select max(idx) as maxidx from blocks");
				
				tdbc2.executeUpdate("insert into pseudo_block_hits "
						+ "(select pseudo_hits.refidx," + newIdx + " from pseudo_block_hits " 
						+ " join pseudo_hits on pseudo_hits.idx=pseudo_block_hits.hit_idx where block_idx=" + idx + ")");
			}
			
			Vector<Integer> grps = new Vector<Integer>();
			rs = tdbc2.executeQuery("select idx from xgroups where proj_idx=" + mProj1Idx);
			while (rs.next()){
				grps.add(rs.getInt(1));
			}
			
			for (int gidx : grps){
				// The self-self blocks
				int maxBlkNum  = tdbc2.executeCount("select max(blocknum) as bnum from blocks "
						+ " where grp1_idx=" + gidx + " and grp1_idx=grp2_idx");
				int newMaxBlkNum = 2*maxBlkNum + 1;
				
				// For each old block, make a new block with reflected coordinates, and blocknum computed as in the query
				tdbc2.executeUpdate("insert into blocks "
						+ "(select 0,pair_idx," + newMaxBlkNum + "-blocknum,"
						+ " proj2_idx,grp2_idx,start2,end2,proj1_idx,grp1_idx,start1,end1," 
						+ " comment,corr,score,0,0,0,0, 0,0,0,0 from blocks "		// add 4 0's CAS572
						+ " where grp1_idx=grp2_idx and grp1_idx=" + gidx + " and blocknum <= " + maxBlkNum + ")"); // last clause prob not needed
				
				// Lastly add the reflected block hits. For each original block hit, get its original block, get the new block (known
				// by its blocknum), get the reflected hit (from refidx), and add the reflected hit as a block hit for the new block.
				tdbc2.executeUpdate("insert into pseudo_block_hits "
						+ "(select ph.refidx,b.idx from pseudo_block_hits as pbh " 
						+ " join blocks as b1 on b1.idx = pbh.block_idx " 
						+ " join blocks as b on b.grp1_idx=" + gidx + " and b.grp2_idx=" + gidx 
						+ "      and b.blocknum=" + newMaxBlkNum + "-b1.blocknum " 
						+ " join pseudo_hits as ph on ph.idx=pbh.hit_idx "
						+ " where b1.blocknum<=" + maxBlkNum +" and ph.grp1_idx=" + gidx + " and ph.grp2_idx=" + gidx + ")");
			}
			return true;
		}
		catch (Exception e) {
			Globals.prt("Self-synteny causes this error sometimes - you will have to rebuild the database");
			ErrorReport.print(e, "Write results to DB"); 
			return false;
		}
	}
	
	/******************************************************************
	 * CAS572 Write blocks and anchors to file; this is deleted since it is easy to get the same thing from report.
	 * writeResultsToFile(ProgressDialog mLog, String resultDir)
	 */
	/******************************************************************
	 * Temporary write hits to file for isSelf until I get self-synteny rewritten
	 */
	protected void writeResultsToFile(ProgressDialog mLog, String resultDir)  {
	try {
		mLog.msg("Write final cluster hits to file for self-synteny");
		ResultSet rs = null;
		
		String idField1 = "ID", idField2 = "ID";
		
		File resDir = new File(resultDir, Constants.finalDir);
		if (!resDir.exists()) resDir.mkdir();	
	
		File anchFile = new File(resDir, "clusterHits.txt");
		if (anchFile.exists()) anchFile.delete();	
		
		// Write anchors
		FileWriter fw = new FileWriter(anchFile);
		fw.write("block\tstart1\tend1\tstart2\tend2\tannot1\tannot2\n");
		rs = tdbc2.executeQuery("select blocknum, grp1.name, grp2.name, " +
			" ph.start1, ph.end1, ph.start2, ph.end2, a1.name, a2.name " +
			" from blocks " +
			" join xgroups            as grp1 on grp1.idx=blocks.grp1_idx " + 
			" join xgroups            as grp2 on grp2.idx=blocks.grp2_idx " +
			" join pseudo_block_hits as pbh  on pbh.block_idx=blocks.idx " +
			" join pseudo_hits       as ph   on ph.idx=pbh.hit_idx " + 
			" left join pseudo_annot as a1   on a1.idx=ph.annot1_idx " +
			" left join pseudo_annot as a2   on a2.idx=ph.annot2_idx " +
			" where blocks.pair_idx=" + mPairIdx + 
			" order by grp1.sort_order asc,grp2.sort_order asc,blocknum asc, ph.start1 asc");
		while (rs.next()) {
			int bidx = rs.getInt(1);
			String grp1 = rs.getString(2);	
			String grp2 = rs.getString(3);	
			int s1 = rs.getInt(4);
			int e1 = rs.getInt(5);
			int s2 = rs.getInt(6);
			int e2 = rs.getInt(7);
			String a1 = rs.getString(8);	
			String a2 = rs.getString(9);
			
			a1 = parseAnnotID(a1,idField1);
			a2 = parseAnnotID(a2,idField2);
			
			fw.write(grp1 + "." + grp2 + "." + bidx + "\t" + s1 + "\t" + e1 + "\t" + s2 + "\t" + e2 +
							"\t" + a1 + "\t" + a2 + "\n");
		}
		fw.flush();
		fw.close();
		mLog.msg("   Wrote " + anchFile);
	}
	catch (Exception e) {ErrorReport.print(e, "Write results to file"); }
	}
	private String parseAnnotID(String in, String fieldName) {
		if (in != null){
			for (String field : in.split(";")){
				if (field.trim().startsWith(fieldName)) {
					field = field.substring(1 + fieldName.length()).trim();
					if (field.startsWith("=")) {
						field = field.substring(2).trim();
					}
					return field;
				}
			}
		}		
		return in;
	}
}
