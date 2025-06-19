package backend.synteny;

import java.io.File;
import java.io.FileWriter;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.TreeMap;
import java.util.Vector;

import database.DBconn2;
import backend.Constants;
import backend.Utils;
import symap.manager.Mpair;
import symap.manager.Mproject;
import util.ErrorReport;
import util.ProgressDialog;
import util.Utilities;

/****************************************************************
 * Called from SyntenyMain after blocks are created
 * v505 had major changes about when scaffolds are flipped
 */
public class OrderAgainst {
	
	private ProgressDialog mLog;
	private DBconn2 dbc2;	
	private Mpair mp;
	private Mproject mProj1, mProj2;
	
	public OrderAgainst(Mpair mp, ProgressDialog mLog, DBconn2 dbc2) {
		this.mp = mp;
		this.mProj1 = mp.getProj1();
		this.mProj2 = mp.getProj2();
		this.mLog = mLog;
		this.dbc2 = dbc2;
	}
	
	/**************************************************************
	// mProj1 is proj_idx1 and mProj2 is proj_idx2 in database.
	// if !switch, mProj1 is draft to be ordered against mProj2 the target
	// if  switch, mProj2 is draft to be ordered against mProj1 the target 
	****************************************************************/
	public void orderGroupsV2(boolean bSwitch)  {
	try {
		if (!bSwitch) {pDraft = mProj1; pTarget = mProj2; }
		else          {pDraft = mProj2; pTarget = mProj1; }
		
		String ordPfx =  pDraft.getGrpPrefix();
		String targPfx = pTarget.getGrpPrefix();
		String chr0 = targPfx + "UNK";
		int chr0Idx = 99999;
		
		long startTime = Utils.getTime();				// CAS568 add time
		mLog.msg("Ordering " + pDraft.getDBName() + " contigs against " + pTarget.getDBName());

		if (!s1LoadDataDB(bSwitch, chr0, chr0Idx)) return;
	
		if (!s2OrderFlipGrpDB(targPfx, ordPfx, chr0, chr0Idx)) return;
	
		s3WriteNewProj();
		
		Utils.prtMsgTimeDone(mLog, "Complete ordering", startTime);
	}
	catch (Exception e) {ErrorReport.print(e, "Ordering sequence"); }
	}
	/***********************************************************
	 * Load groups from both projects
	 */
	private boolean s1LoadDataDB(boolean bSwitch, String chr0, int chr0Idx) {
		try {
			/** Get Draft and Target groups **/
			ResultSet rs = dbc2.executeQuery("select idx, fullname, p.length " +
					" from xgroups as g " +
					" join pseudos as p on p.grp_idx=g.idx " +
					" where proj_idx=" + pDraft.getIdx());
			while (rs.next()) {
				Draft o = new Draft();
				o.gidx =     rs.getInt(1);
				o.ctgName =  rs.getString(2); 
				o.ctgLen =   rs.getInt(3);
				idxDraftMap.put(o.gidx, o);
			}
				
			rs = dbc2.executeQuery("select idx, fullname from xgroups " +
					" where proj_idx=" + pTarget.getIdx());
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
			
			rs = dbc2.executeQuery("select grp1_idx, grp2_idx, start1, start2, score, corr from blocks " +
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
			String fileName = Constants.seqDataDir + pDraft.getDBName() + getOrderFile(mp);
			File ordFile = new File(fileName);
			if (ordFile.exists()) ordFile.delete();
			FileWriter ordFileW = new FileWriter(ordFile);
			
			ordFileW.write(pDraft.getDBName() + "-" + ordPfx + "," + pTarget.getDBName() + "-" + targPfx + ",pos,F/R,#anchors," + pDraft.getDBName() + "-length\n");
			
			int nOrd=1; // new order of contigs in database; only place database changes for V2
			for (Draft d : orderedDraft) {
				int f = (d.bDoFlip) ? 1 : 0; // the flipped is not used anywhere
				dbc2.executeUpdate("update xgroups set sort_order=" + nOrd + ",flipped=" + f 
						+ " where idx=" + d.gidx);
				nOrd++;
				
				String rc = (d.bDoFlip) ? "R" : "F";
				if (d.tidx==chr0Idx)  d.chrName = chr0;
				ordFileW.write(d.ctgName  + "," + d.chrName  + "," + d.pos  + "," + rc  + "," +  d.score  + "," + d.ctgLen  + "\n");
			}
			ordFileW.close();
			mLog.msg("   Wrote order to " + fileName);
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, "Order Draft"); return false; }
	}
	
	/** 
	 * Write new project sequence/x.fa, annotation/gap.gff and params 
	 ***/
	private void s3WriteNewProj() {
		try {
			String ordProjName = Constants.getOrderDir(mp); // CAS568 new dir name	
			String ordDirName = Constants.seqDataDir + ordProjName;
			
			mLog.msg("   Creating new project " + ordDirName);
			
			File   ordDir = new File(ordDirName);
			if (ordDir.exists()) {
				mLog.msg("   Delete previous " + ordDirName);
				Utilities.clearAllDir(ordDir);
				ordDir.delete();
			}	
			Utilities.checkCreateDir(ordDirName, false); // CAS568 quite writing to stdout here; add ordDirName above
			
			File ordSeqDir = Utilities.checkCreateDir(ordDir + Constants.seqSeqDataDir, false);
			File ordFasta = Utilities.checkCreateFile(ordSeqDir, ordProjName + Constants.faFile, "SM fasta file");
			FileWriter ordFastaFH = 	new FileWriter(ordFasta);
			
			File ordAnnoDir = Utilities.checkCreateDir(ordDir + Constants.seqAnnoDataDir, false);
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
				ResultSet rs = dbc2.executeQuery("select ps.seq from pseudo_seq2 as ps " +
						" where ps.grp_idx =" + d.gidx + " order by ps.chunk asc");
				String seq="";
				while (rs.next()) 
					seq += rs.getString(1);
				rs.close();
				
				if (d.bDoFlip) { // v2 flips on output instead of in database for draft
					seq = reverseComplement(seq);
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
			
			File ordParam = Utils.getParamsFile(ordDir.getAbsolutePath(), Constants.paramsFile);// CAS569 to rename if exists
			ordParam =   Utilities.checkCreateFile(ordDir, Constants.paramsFile, "SM params"); // deletes if exists
			FileWriter ordParamsFH = new FileWriter(ordParam);
			ordParamsFH.write("category = " + pDraft.getdbCat() + "\n");
			ordParamsFH.write("abbrev_name = Draf\n");
			ordParamsFH.write("display_name=" + getOrderDisplay(mp) + "\n");
			ordParamsFH.write("description=" + getOrderDesc(mp) + "\n");
			ordParamsFH.write("grp_prefix=" + pTarget.getGrpPrefix() + "\n");
			ordParamsFH.close();	
		}
		catch (Exception e) {ErrorReport.print(e, "write chromosomes"); }
	}
	private String reverseComplement(String in){ // CAS569 moved from Utils 
		in = (new StringBuffer(in)).reverse().toString().toUpperCase();
		in = in.replace('A', 't');
		in = in.replace('G', 'c');
		in = in.replace('C', 'g');
		in = in.replace('T', 'a');
		return in.toLowerCase();
	}
	/***************************************************************************/
	private String getOrderFile(Mpair mp) { // CAS568 renamed proj_ordered to this
		if (mp.isOrder1(Mpair.FILE)) return "/" + mp.mProj2.getDBName() + Constants.orderSuffix;
		if (mp.isOrder2(Mpair.FILE)) return "/" + mp.mProj1.getDBName() + Constants.orderSuffix;
		return null;
	}
	
	private String getOrderDisplay(Mpair mp) { // CAS568 renamed proj_ordered to this
		if (mp.isOrder1(Mpair.FILE)) return mp.mProj1.getDisplayName() + "." + mp.mProj2.getDisplayName();
		if (mp.isOrder2(Mpair.FILE)) return mp.mProj2.getDisplayName() + "." + mp.mProj1.getDisplayName();
		return null;
	}
	private String getOrderDesc(Mpair mp) { // CAS568 used DBname instead of Display since it can change
		if (mp.isOrder1(Mpair.FILE)) return mp.mProj1.getDisplayName() + " ordered against " + mp.mProj2.getDisplayName();
		if (mp.isOrder2(Mpair.FILE)) return mp.mProj2.getDisplayName() + " ordered against " + mp.mProj1.getDisplayName();
		return null;
	}
	/***************************************************************************/
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
	private Mproject pDraft, pTarget;
	private TreeMap <Integer, Target> idxTargetMap = new TreeMap <Integer, Target> ();
	private TreeMap <Integer, Draft>  idxDraftMap = new TreeMap <Integer, Draft> ();
	private Vector <Draft> orderedDraft = new Vector <Draft> ();
}
