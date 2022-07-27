package backend;

/**************************************************
 * CAS505 
 * 	moved orderGroups from SyntenyMain, which has a bug where most ctgs go into chrUNK
 *  rewrote it, which is called orderGroupsV2
 *  the orderGroups can still be run with the -o option
 *  
 *  orderGroups:   flips contigs in database, which messes up if its run again. 
 *  orderGroupsV2: does not flip contigs in database, but writes the chromosome files with flipped contigs
 */
import java.io.File;
import java.io.FileWriter;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

import util.ErrorReport;
import util.Logger;
import util.Utilities;

public class OrderAgainst {
	
	private static final int CHUNK_SIZE = Constants.CHUNK_SIZE;
	
	private static final String anchorSuffix 	=  "_anchored"; 
	private static final String anchorCSVFile	=  "/anchored.csv";

	public static final String orderSuffix 		=  "_ordered"; // CAS505 for ordering V2
	private static final String orderCSVFile	=  "/ordered.csv";
	
	private Logger mLog;
	private UpdatePool pool;	
	private Project mProj1, mProj2;
	
	public OrderAgainst(Project mProj1, Project mProj2, Logger mLog, UpdatePool pool) {
		this.mProj1 = mProj1;
		this.mProj2 = mProj2;
		this.mLog = mLog;
		this.pool = pool;
	}
	/*****************************************************
	 * pre-v5.0.5 ordering
	 * - changes data in database, so messes up when run a 2nd time
	 * - flips the contigs in database for correct draft display
	 * - puts many contigs in chrUNK
	 * - uses term anchored
	 */
	public void orderGroups(boolean bSwitch) throws Exception {
	try {
		Project pDraft =  (bSwitch ? mProj2 : mProj1); 
		Project pTarget = (bSwitch ? mProj1 : mProj2); 
	
		File ordFile = new File(Constants.seqDataDir + pDraft.name + anchorCSVFile);
		if (ordFile.exists()) ordFile.delete();
		FileWriter ordFileW = new FileWriter(ordFile);
		
		mLog.msg("\nOrdering " + pDraft.getName() + " contigs against " + pTarget.getName());
		mLog.msg("   Original ordering algorithm - flips contigs in database.");
		
		ResultSet rs;
		TreeSet<Integer> alreadyFlipped = new TreeSet<Integer>();
	
		rs = pool.executeQuery("select idx, flipped from xgroups where proj_idx=" + pDraft.idx);
		while (rs.next()) {
			int idx = rs.getInt(1);
			boolean flipped = rs.getBoolean(2);
			if (flipped) alreadyFlipped.add(idx);
		}
		pool.executeUpdate("update xgroups set flipped=0, sort_order=idx where proj_idx=" + pDraft.idx);
				
		// Order the grp1 by finding the grp2 that they have the most synteny
		// hits with, and taking the start point of those hits.
		TreeSet<Integer>         grp1Seen = new TreeSet<Integer>();
		TreeMap<Integer,Integer> grp1score = new TreeMap<Integer,Integer>();
		TreeMap<Integer,Integer> grp1pos = new TreeMap<Integer,Integer>();
		TreeMap<Integer,Integer> grp1len = new TreeMap<Integer,Integer>();
		TreeMap<Integer,String>  grp1name = new TreeMap<Integer,String>();
		TreeSet<Integer>         grp1flip = new TreeSet<Integer>();
		
		TreeMap<Integer,String>  newGrp = new TreeMap<Integer,String>();
		
		TreeMap<Integer,Vector<Integer>> grpMaps = new TreeMap<Integer,Vector<Integer>>();
		
		Vector<Integer> grp2Order = new Vector<Integer>(); 
		rs = pool.executeQuery("select idx from xgroups where proj_idx=" + pTarget.getIdx() + 
						" order by sort_order asc");
		while (rs.next())
		{
			grp2Order.add(rs.getInt(1));
		}
		mLog.msg("   " + grp2Order.size() + " xgroups to order against ");
		
		// note avg(corr) is necessary because of the grouping, and the grouping is because we don't store the # of hits in the blocks table
		rs = pool.executeQuery("select grp1_idx, grp2_idx, " +
				"grp2.fullname, grp1.fullname, count(*) as score, " +
				"start1, start2, avg(corr) as corr, " + 
				" ps1.length as len1, ps2.length as len2  from blocks " + 
				" join pseudo_block_hits on pseudo_block_hits.block_idx=blocks.idx " +
				" join xgroups as grp2    on grp2.idx=grp2_idx " +
				" join xgroups as grp1    on grp1.idx=grp1_idx " +
				" join pseudos as ps1    on ps1.grp_idx=grp1_idx " +
				" join pseudos as ps2    on ps2.grp_idx=grp2_idx " +
				" where proj1_idx=" + mProj1.getIdx() + " and proj2_idx=" + mProj2.getIdx() + 
				" by score desc, grp1_idx, grp2_idx, blocknum" );
				// CAS506 " group by blocks.grp1_idx, blocks.grp2_idx, blocks.blocknum order by score desc" );
		while (rs.next())
		{
			int grp1Idx = (bSwitch ? rs.getInt("grp2_idx") : rs.getInt("grp1_idx"));
			int grp2Idx = (bSwitch ? rs.getInt("grp1_idx") : rs.getInt("grp2_idx"));
			int pos =     (bSwitch ? rs.getInt("start1")   : rs.getInt("start2"));
			int len1 =    (bSwitch ? rs.getInt("len2")     : rs.getInt("len1"));
			int score = rs.getInt("score");
			float corr = rs.getFloat("corr");
			String grp2Name = (bSwitch ? rs.getString("grp1.fullname") : rs.getString("grp2.fullname"));
			String grp1Name = (bSwitch ? rs.getString("grp2.fullname") : rs.getString("grp1.fullname"));
		
			if (grp1Seen.contains(grp1Idx))
			{
				continue; // if seen this group before then already got its best block	
			}
			if (!grpMaps.containsKey(grp2Idx))
			{
				grpMaps.put(grp2Idx, new Vector<Integer>());	
			}
			grpMaps.get(grp2Idx).add(grp1Idx);
			
			grp1Seen.add(grp1Idx);
			grp1pos.put(grp1Idx,  pos);
			grp1len.put(grp1Idx,  len1);
			grp1score.put(grp1Idx,score);
			grp1name.put(grp1Idx, grp1Name);
			
			newGrp.put(grp1Idx, grp2Name);
			if (corr < 0) {
				grp1flip.add(grp1Idx);
			}
		}
		rs.close();
		mLog.msg("   " + grp1Seen.size() + " groups to order ");
		
		// Order contigs. It seems like there should be an easier way to do this...
		int curOrd = 1;
		TreeMap<Integer,Integer> grp2ord = new TreeMap<Integer,Integer>();
		Vector<Integer> grp1_ordered = new Vector<Integer>();
		for (int grp2Idx : grp2Order)
		{
			if (!grpMaps.containsKey(grp2Idx)) continue;
			Integer[] grp1s = grpMaps.get(grp2Idx).toArray(new Integer[1]);
			Integer[] pos = new Integer[grp1s.length];
			for (int i = 0; i < grp1s.length; i++)
			{
				pos[i] = grp1pos.get(grp1s[i]);
			}
			// We have parallel arrays of grp and pos, so sort the pos and apply this sort to the grp
			Utils.HeapDoubleSort(pos, grp1s, new Utils.ObjCmp());
			for (int i = 0; i < grp1s.length; i++)
			{
				grp2ord.put(grp1s[i], curOrd);
				grp1_ordered.add(grp1s[i]);
				curOrd++;
			}
		}
		
		String ordPfx =  Utils.getProjProp(pDraft.idx,  "grp_prefix", pool);
		String targPfx = Utils.getProjProp(pTarget.idx, "grp_prefix", pool);

		// Chr0; assign the ones that didn't align anywhere
		Vector<Integer> allGrpIdx = new Vector<Integer>();
		rs = pool.executeQuery("select idx from xgroups where proj_idx=" + pDraft.idx);
		while (rs.next())
		{
			int idx = rs.getInt(1);
			allGrpIdx.add(idx);
			if (!grp2ord.containsKey(idx))
			{
				grp2ord.put(idx, curOrd);
				curOrd++;
			}	
		}
		ordFileW.write(pDraft.name + "-" + ordPfx + "," + pTarget.name + "-" + targPfx + ",pos,F/R,#anchors," + pDraft.name + "-length\n");
		
		for (int idx : grp1_ordered)
		{
			int ord = grp2ord.get(idx);
			int pos = grp1pos.get(idx);
			int len = grp1len.get(idx);
			String RC = "F";
			if (grp1flip.contains(idx)) { // XXX block.corr<0
				pool.executeUpdate("update xgroups set sort_order=" + ord + ",flipped=1 where idx=" + idx);
				RC = "R";
			}
			else {
				pool.executeUpdate("update xgroups set sort_order=" + ord + ",flipped=0 where idx=" + idx);
			}
			ordFileW.write(grp1name.get(idx) + "," + newGrp.get(idx) + "," + pos + "," + RC + "," + grp1score.get(idx) + "," + len + "\n"); 
		}
		ordFileW.close();
		
		// Now, update the newly flipped/unflipped xgroups and the anchors and synteny blocks containing them
		int cnt=0;
		mLog.msg("   Updating flipped contigs and anchors....");
		for (int idx : allGrpIdx)
		{
			if ( (grp1flip.contains(idx) && !alreadyFlipped.contains(idx)) || 
				(!grp1flip.contains(idx) &&  alreadyFlipped.contains(idx)))
			{
				cnt++;
				// First, reverse-conjugate the group sequence
				String seq = "";
				rs = pool.executeQuery("select seq from pseudo_seq2 where grp_idx=" + idx + " order by chunk asc");
				while (rs.next())
				{
					seq += rs.getString(1);
				}
				rs.close();
				pool.executeUpdate("delete from pseudo_seq2 where grp_idx=" + idx);
				String revSeq = Utils.reverseComplement(seq);
				// Finally, upload the sequence in chunks
				for (int chunk = 0; chunk*CHUNK_SIZE < revSeq.length(); chunk++)
				{
					int start = chunk*CHUNK_SIZE;
					int len = Math.min(CHUNK_SIZE, seq.length() - start );

					String cseq = revSeq.substring(start,start + len );
					String st = "INSERT INTO pseudo_seq2 VALUES('" + idx + "','" + chunk + "','" + cseq + "')";
					pool.executeUpdate(st);
				}
				// Now, flip coordinates of all anchors and blocks containing this group
				pool.executeUpdate("update pseudo_hits as ph, pseudos as p  " +
						" set ph.start1=p.length-ph.end1, ph.end1=p.length-ph.start1 " +
						"where ph.grp1_idx=" + idx + " and p.grp_idx=" + idx );
				pool.executeUpdate("update pseudo_hits as ph, pseudos as p  " +
						" set ph.start2=p.length-ph.end2, ph.end2=p.length-ph.start2 " +
						"where ph.grp2_idx=" + idx + " and p.grp_idx=" + idx );
				pool.executeUpdate("update bes_hits as h, pseudos as p  " +
						" set h.start2=p.length-h.end2, h.end2=p.length-h.start2 " +
						"where h.grp2_idx=" + idx + " and p.grp_idx=" + idx );
				pool.executeUpdate("update mrk_hits as h, pseudos as p  " +
						" set h.start2=p.length-h.end2, h.end2=p.length-h.start2 " +
						"where h.grp2_idx=" + idx + " and p.grp_idx=" + idx );
				
				pool.executeUpdate("update blocks as b, pseudos as p  " +
						" set b.start1=p.length-b.end1, b.end1=p.length-b.start1, b.corr=-b.corr " +
						"where b.grp1_idx=" + idx + " and p.grp_idx=" + idx );
				pool.executeUpdate("update blocks as b, pseudos as p  " +
						" set b.start2=p.length-b.end2, b.end2=p.length-b.start2, b.corr=-b.corr " +
						"where b.grp2_idx=" + idx + " and p.grp_idx=" + idx );
			}
		}
		if (cnt>0) mLog.msg("   Flipped " + cnt);
		
		// Lastly, print out the two ordered projects
		mLog.msg("Creating anchored project from " + pDraft.getName());
		mLog.msg("Wrote " + ordFile.getAbsolutePath());
	
		String groupedName = pDraft.getName() + anchorSuffix;	
		String groupFileName = Constants.seqDataDir + groupedName;
	
		File groupedDir = new File(groupFileName);
		if (groupedDir.exists())
		{
			mLog.msg("   Delete previous " + groupFileName);
			Utilities.clearAllDir(groupedDir);
			groupedDir.delete();
		}	
		mLog.msg("   Create " + groupFileName);
		Utilities.checkCreateDir(groupFileName, true /* bPrt */);
		
		File groupedSeqDir =Utilities.checkCreateDir(groupedDir + Constants.seqSeqDataDir, true);
		File groupedAnnoDir = Utilities.checkCreateDir(groupedDir + Constants.seqAnnoDataDir, true);
		
		File groupedFasta = Utilities.checkCreateFile(groupedSeqDir, groupedName + Constants.faFile, "SM fasta file");
		File groupedGFF =   Utilities.checkCreateFile(groupedAnnoDir, groupedName + ".gff", "SM gff");
		File grpParam =     Utilities.checkCreateFile(groupedDir, Constants.paramsFile, "SM params");

		FileWriter grpFastaW = 	new FileWriter(groupedFasta);
		FileWriter grpParamW = 	new FileWriter(grpParam);
		FileWriter groupedGFFW = new FileWriter(groupedGFF);
		
		String separator = "NNNNNNNNNN" + "NNNNNNNNNN" +"NNNNNNNNNN" +"NNNNNNNNNN" +"NNNNNNNNNN" +
				           "NNNNNNNNNN" + "NNNNNNNNNN" +"NNNNNNNNNN" +"NNNNNNNNNN" +"NNNNNNNNNN";
		
		Vector<String> grpOrder = new Vector<String>();
		rs = pool.executeQuery("select xgroups.fullname, xgroups.idx, pseudo_seq2.seq " +
				" from xgroups " +
				" join pseudo_seq2 on pseudo_seq2.grp_idx=xgroups.idx where xgroups.proj_idx=" + pDraft.idx + 
				" order by xgroups.sort_order asc, pseudo_seq2.chunk asc");
		int curGrpIdx = -1, groupedPos = 0, count=0;
		String curNewGrp = "", curNewGrpName = "", curGrpName = "", prevGrpName = "";
		long lenNewCtg = 0;
		String lenMsg="   Ordered Sequence Lengths:\n";
		
		boolean unanchStarted = false;
		while (rs.next())
		{
			String name = rs.getString(1);
			int grpIdx = rs.getInt(2);
			String seq = rs.getString(3);
			
			if (grpIdx != curGrpIdx)
			{
				prevGrpName = curGrpName;
				curGrpIdx = grpIdx;
				curGrpName = name;
				if (newGrp.containsKey(curGrpIdx))
				{
					if (!newGrp.get(curGrpIdx).equals(curNewGrp))
					{
						if (lenNewCtg>0) {
							lenMsg += String.format("     %-10s %,d\n", curNewGrpName, lenNewCtg);
							lenNewCtg=0;
						}
						curNewGrp = newGrp.get(curGrpIdx);
						curNewGrpName = curNewGrp;
						grpOrder.add(curNewGrpName);
						grpFastaW.write(">" + curNewGrpName + "\n");
						groupedPos = 0;
					}
					else
					{
						// still in the same anchor section, wrote a prior contig to it, hence need a separator
						lenNewCtg += separator.length();
						grpFastaW.write(separator); grpFastaW.write("\n");	
						groupedGFFW.write(curNewGrpName + "\tconsensus\tgap\t" + groupedPos + "\t" + (groupedPos+separator.length()) + 
								"\t.\t+\t.\tName \"GAP:" + prevGrpName + "-" + curGrpName + "\"\n");
						groupedPos += separator.length();
					}
				}
				else // unanchored section
				{
					if (!unanchStarted)
					{
						if (lenNewCtg>0) {
							lenMsg += String.format("     %-10s %,d\n", curNewGrpName, lenNewCtg);
							lenNewCtg=0;
						}
						curNewGrpName = targPfx + "UNK";
						grpOrder.add(curNewGrpName);
						grpFastaW.write(">" + curNewGrpName  + "\n");
						groupedPos = 0;	
						unanchStarted = true;
					}
					else
					{
						grpFastaW.write(separator);	 grpFastaW.write("\n");
						groupedGFFW.write(curNewGrpName + "\tconsensus\tgap\t" + groupedPos + "\t" + (groupedPos+separator.length()) + 
								"\t.\t+\t.\tName \"GAP:" + prevGrpName + "-" + curGrpName + "\"\n");
						groupedPos += separator.length();
					}
				}
			}
			for (int i = 0; i < seq.length(); i += 50)
			{
				int end = Math.min(i+50,seq.length()-1);
				String line = seq.substring(i, end);
				grpFastaW.write(line); grpFastaW.write("\n");
			}
			lenNewCtg += seq.length();
			groupedPos += seq.length();
			count++;
			if (count%1000==0) System.out.print("Processed " + count + "...\r");
		}
		
		if (lenNewCtg>0) 
			lenMsg += String.format("     %-10s %,d\n", curNewGrpName, lenNewCtg);
			
		mLog.msg(lenMsg);
		mLog.msg("   Wrote " + groupedFasta + "                               ");
		grpFastaW.close();
		groupedGFFW.close();
		String dispName;

		dispName = pDraft.displayName + anchorSuffix;
		grpParamW.write("category = " + pDraft.category + "\ndisplay_name=" + dispName + "\n");
		grpParamW.write("grp_prefix=" + pTarget.grpPrefix + "\n");
		grpParamW.close();
	}
	catch (Exception e) {ErrorReport.print(e, "Ordering sequence"); }
	}
	/**************************************************************
	// XXX mProj1 is proj_idx1 and mProj2 is proj_idx2 in database.
	// if !switch, mProj1 is draft to be ordered against mProj2 the target
	// if  switch, mProj2 is draft to be ordered against mProj1 the target 
	 * 
	 * v2 - SyMAP v5.0.5
	 * - the only change to database is the order of draft contigs for display
	 * - the contigs are not flipped for the draft display
	 * - the contigs are written correctly to the chromosome file, and flipped then
	 * - uses term 'ordered'
	****************************************************************/
	public void orderGroupsV2(boolean bSwitch)  {
	try {
		
		if (!bSwitch) {pDraft = mProj1; pTarget = mProj2; }
		else          {pDraft = mProj2; pTarget = mProj1; }
		
		String ordPfx =  Utils.getProjProp(pDraft.idx,  "grp_prefix", pool);
		String targPfx = Utils.getProjProp(pTarget.idx, "grp_prefix", pool);
		String chr0 = targPfx + "UNK";
		int chr0Idx = 99999;
		
		mLog.msg("\nOrdering " + pDraft.getName() + " contigs against " + pTarget.getName());

		if (!s1LoadDataDB(bSwitch, chr0, chr0Idx)) return;
	
		if (!s2OrderFlipGrpDB(targPfx, ordPfx, chr0, chr0Idx)) return;
	
		s3WriteNewProj();
		
		mLog.msg("Complete ordering");
	}
	catch (Exception e) {ErrorReport.print(e, "Ordering sequence"); }
	}
	/***********************************************************
	 * Load groups from both projects
	 */
	private boolean s1LoadDataDB(boolean bSwitch, String chr0, int chr0Idx) {
		try {
			/** Get Draft and Target groups **/
			ResultSet rs = pool.executeQuery("select idx, fullname, p.length " +
					" from xgroups as g " +
					" join pseudos as p on p.grp_idx=g.idx " +
					" where proj_idx=" + pDraft.idx);
			while (rs.next()) {
				Draft o = new Draft();
				o.gidx =     rs.getInt(1);
				o.ctgName =  rs.getString(2); 
				o.ctgLen =   rs.getInt(3);
				idxDraftMap.put(o.gidx, o);
			}
				
			rs = pool.executeQuery("select idx, fullname from xgroups " +
					" where proj_idx=" + pTarget.idx);
			while (rs.next()) {
				Target o = new Target();
				o.gidx = rs.getInt(1);
				o.chrName = rs.getString(2);
				idxTargetMap.put(o.gidx, o);
			}
			Target o = new Target();
			o.gidx = chr0Idx;
			o.chrName = chr0;
			idxTargetMap.put(chr0Idx, o); 
			
		/** Assign Target chromosome from blocks **/
			/** CAS506 does not work in mySQL v8: SELECT list is not in GROUP BY clause and contains nonaggregated 
			rs = pool.executeQuery("select grp1_idx, grp2_idx, start1, start2, score, corr from blocks " +
				" where proj1_idx=" + mProj1.getIdx() + " and proj2_idx=" + mProj2.getIdx() + 
				" group by grp1_idx, grp2_idx, blocknum order by score desc" );
		**/
			
			rs = pool.executeQuery("select grp1_idx, grp2_idx, start1, start2, score, corr from blocks " +
					" where proj1_idx=" + mProj1.getIdx() + " and proj2_idx=" + mProj2.getIdx() + 
					" order by score desc,  grp1_idx, grp2_idx, blocknum" );
			
			while (rs.next()) {
				int tgidx1 =  rs.getInt(1);
				int tgidx2 =  rs.getInt(2);
				int tStart1 = rs.getInt(3);
				int tStart2 = rs.getInt(4);
				int score =   rs.getInt(5);
				
				int gidx1, gidx2, pos;
				if (!bSwitch) {
					gidx1 = tgidx1;
					gidx2 = tgidx2;
					pos = tStart2;
				}
				else {
					gidx1 = tgidx2;
					gidx2 = tgidx1;
					pos = tStart1;
				}
				Draft d = idxDraftMap.get(gidx1);
				
				// if (d.pos>0) continue; CAS506
				if (d.score > score) continue; // a group (chr) can be split across multiple blocks; use first with biggest score
				
				d.pos = pos;
				d.score = rs.getInt(5);
				d.bDoFlip = (rs.getDouble(6)<0);  // Block containing contig is flipped
				
				Target t = idxTargetMap.get(gidx2);
				d.chrName = t.chrName;
				d.tidx = t.gidx;
			}
			rs.close();
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, "Load Data"); return false; }
	}
	/** 
	 * Assign chr0 to unassigned, create ordered ctgs, write ordered file, update DB order
	 ***/
	private boolean s2OrderFlipGrpDB(String targPfx, String ordPfx, String chr0, int chr0Idx) {
		try {
			
			for (Draft d : idxDraftMap.values()) {
				if (d.tidx==0) {
					d.chrName = targPfx + chr0Idx; // sort last; renamed after sort
					d.tidx =    chr0Idx;
				}
				orderedDraft.add(d);
			}
			Collections.sort(orderedDraft);
			
		/** Write Ordered file and update DB order **/
			
			File ordFile = new File(Constants.seqDataDir + pDraft.name + orderCSVFile);
			if (ordFile.exists()) ordFile.delete();
			FileWriter ordFileW = new FileWriter(ordFile);
			
			ordFileW.write(pDraft.name + "-" + ordPfx + "," + pTarget.name + "-" + targPfx + ",pos,F/R,#anchors," + pDraft.name + "-length\n");
			
			int nOrd=1; // new order of contigs in database; only place database changes for V2
			for (Draft d : orderedDraft) {
				int f = (d.bDoFlip) ? 1 : 0; // the flipped is not used anywhere
				pool.executeUpdate("update xgroups set sort_order=" + nOrd + ",flipped=" + f 
						+ " where idx=" + d.gidx);
				nOrd++;
				
				String rc = (d.bDoFlip) ? "R" : "F";
				if (d.tidx==chr0Idx)  d.chrName = chr0;
				ordFileW.write(d.ctgName  + "," + d.chrName  + "," + d.pos  + "," + rc  + "," +  d.score  + "," + d.ctgLen  + "\n");
			}
			ordFileW.close();
			mLog.msg("   Wrote order to " + ordFile.getCanonicalPath());
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, "Order Draft"); return false; }
	}
	
	/** 
	 * Write new project sequence/x.fa, annotation/gap.gff and params 
	 ***/
	private void s3WriteNewProj() {
		try {
			mLog.msg("   Creating new ordered project from " + pDraft.getName());
			
			String ordProjName = pDraft.getName() + orderSuffix;	
			String ordDirName = Constants.seqDataDir + ordProjName;
			File   ordDir = new File(ordDirName);
			if (ordDir.exists()) {
				mLog.msg("   Delete previous " + ordDirName);
				Utilities.clearAllDir(ordDir);
				ordDir.delete();
			}	
			Utilities.checkCreateDir(ordDirName, true);
			
			File ordSeqDir = Utilities.checkCreateDir(ordDir + Constants.seqSeqDataDir, true);
			File ordFasta = Utilities.checkCreateFile(ordSeqDir, ordProjName + Constants.faFile, "SM fasta file");
			FileWriter ordFastaFH = 	new FileWriter(ordFasta);
			
			File ordAnnoDir = Utilities.checkCreateDir(ordDir + Constants.seqAnnoDataDir, true);
			File ordGFF =   Utilities.checkCreateFile(ordAnnoDir, ordProjName + ".gff", "SM gff");
			FileWriter ordGffFH = new FileWriter(ordGFF);
			
			int sepLen=100;
			String separator="";
			for (int i=0; i<10; i++) separator += "NNNNNNNNNN";
		
			Target curTar=null;
			String lastCtgName = "";
			int cntFlip=0;
			
			for (Draft d : orderedDraft) {
				if (curTar==null || curTar.gidx != d.tidx) {
					curTar = idxTargetMap.get(d.tidx);
					ordFastaFH.write(">" + curTar.chrName + "\n");
				}
				else { // N's between subsequent contigs aligned to chrN
					ordFastaFH.write(separator + "\n"); 
					
					ordGffFH.write(curTar.chrName + "\tconsensus\tgap\t" 
							+ curTar.tLen + "\t" + (curTar.tLen+sepLen) + 
							"\t.\t+\t.\tName \"GAP:" + lastCtgName + "-" + d.ctgName + "\"\n");
					curTar.nLen += sepLen;
					curTar.tLen += sepLen;
				}
				ResultSet rs = pool.executeQuery("select ps.seq from pseudo_seq2 as ps " +
						" where ps.grp_idx =" + d.gidx + " order by ps.chunk asc");
				String seq="";
				while (rs.next()) 
					seq += rs.getString(1);
				rs.close();
				
				if (d.bDoFlip) { // v2 flips on output instead of in database for draft
					seq = Utils.reverseComplement(seq);
					cntFlip++;
				}
				
				for (int i = 0; i < seq.length(); i += 50)
				{
					int end = Math.min(i+50, seq.length()-1);
					String line = seq.substring(i, end);
					ordFastaFH.write(line + "\n"); 
				}
				curTar.tLen += seq.length();
				curTar.bLen += seq.length();
				lastCtgName  = d.ctgName;
			}
			mLog.msg("   Flipped contigs " + cntFlip);
			
			long blen=0,nlen=0,tlen=0;
			mLog.msg("   Ordered chromosome lengths:");
			mLog.msg(String.format("     %-10s  %12s  %12s  %12s", "ChrName", "nBases", "Added Ns", "Total"));
			for (Target t : idxTargetMap.values()) {
				mLog.msg(String.format("     %-10s  %,12d  %,12d  %,12d", 
						t.chrName, t.bLen, t.nLen, t.tLen));
				blen += t.bLen;
				nlen += t.nLen;
				tlen += t.tLen;
			}
			mLog.msg(String.format("     %-10s  %,12d  %,12d  %,12d", "Total", blen, nlen, tlen));
			
			mLog.msg("   Wrote sequence to " + ordFasta);
			mLog.msg("   Wrote gaps to " + ordGFF);
			ordFastaFH.close();
			ordGffFH.close();
			
			File ordParam =   Utilities.checkCreateFile(ordDir, Constants.paramsFile, "SM params");
			FileWriter ordParamsFH = 	new FileWriter(ordParam);
			ordParamsFH.write("category = " + pDraft.category + "\n");
			ordParamsFH.write("display_name=" + pDraft.displayName + orderSuffix + "\n");
			ordParamsFH.write("grp_prefix=" + pTarget.grpPrefix + "\n");
			ordParamsFH.close();
			
			ordParamsFH.close();
			
		}
		catch (Exception e) {ErrorReport.print(e, "write chromosomes"); }
	}
	
	private class Draft implements Comparable <Draft> {
		int gidx; 				// group.idx
		String ctgName=""; 		// group.fullname
		int ctgLen;				// pseudo.length

		int score=0; 			// block.score
		boolean bDoFlip=false;	// (block.corr<0)
		int pos=0;				// block.start to chromosome
		int tidx = 0;			// block.grpX_idx; target chromosome; if 0, not aligned to the target
		String chrName=""; 		// From idxTargetMap.get(tidx).chrName
		
		public int compareTo(Draft d) {
			if (chrName.equals(d.chrName)) {
				if (pos<d.pos) return -1;
				if (pos>d.pos) return 1;
				if (score<d.score) return -1; // doesn't matter, but makes consistent
				if (score>d.score) return 1;
				return 0;
			}
			return chrName.compareTo(d.chrName);
		}
	}
	private class Target {
		int gidx; 			    // group.idx, group.proj_idx
		String chrName;		    // group.fullname
		int bLen=0, nLen=0, tLen=0;
	}
	private Project pDraft, pTarget;
	private TreeMap <Integer, Target> idxTargetMap = new TreeMap <Integer, Target> ();
	private TreeMap <Integer, Draft>  idxDraftMap = new TreeMap <Integer, Draft> ();
	private Vector <Draft> orderedDraft = new Vector <Draft> ();
}
