package backend;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Vector;

import backend.anchor1.AnchorMain1;
import backend.anchor2.AnchorMain2;
import database.DBconn2;
import symap.Globals;
import symap.manager.Mpair;
import symap.manager.Mproject;
import util.ErrorCount;
import util.ErrorReport;
import util.ProgressDialog;
import util.Utilities;

/**************************************************************
 * AnchorMain:
 * Performs the set up computations for computing clustered hits.
 * It then calls the anchor1 or anchor2 code. It finishes by computing the gene NumHits
 * 
 * Filename: e.g. arab_cc.cabb_f1.mum
 * Mproj1 (arab) is query and Mproj2 (cabb) is target; target coords (cabb) comes before query (arab)
 */
public class AnchorMain {
	public boolean doAnchor1=false; // CAS546 new parameter
	
	// variables may be accessed directly by AnchorMain1 or AnchorMain2
	public DBconn2 dbc2;
	public ProgressDialog plog=null;
	public Mpair mp;
	public int pairIdx;
	public boolean bInterrupt;
	
	public AnchorMain(DBconn2 dbc2, ProgressDialog log,  Mpair mp) {	
		this.dbc2 = dbc2;
		this.plog = log;
		this.mp = mp;
		if (mp!=null) {						// Null for Mproject.removeProjectFromDB; CAS561
			this.pairIdx = mp.getPairIdx(); 
			doAnchor1 = (mp.isAlgo1(Mpair.FILE));
		}
	}
	
	protected boolean run(Mproject pj1, Mproject pj2) throws Exception {
		try {
		/** setup **/
			long startTime = Utils.getTimeMem();
			
			String proj1Name = pj1.getDBName(), proj2Name = pj2.getDBName();
			plog.msg("Start calculating cluster hits for " + proj1Name + " and " + proj2Name);
			
			String resultDir = Constants.getNameResultsDir(proj1Name, proj2Name); // e.g. data/seq_results/p1_to_p2
			if ( !Utilities.pathExists(resultDir) ) {
				plog.msg("Cannot find pair directory " + resultDir);
				ErrorCount.inc();
				return false;
			}
			String alignDir = resultDir + Constants.alignDir;
			
			File dh = new File(alignDir);
			if (!dh.exists() || !dh.isDirectory()) return Constants.rtError("/align directory does not exist " + alignDir);
			plog.msg("   Alignment files in " + alignDir);
			
		/** execute **/
			if (doAnchor1) {
				AnchorMain1 an = new AnchorMain1(this);
				boolean b = an.run(pj1, pj2, dh); if (!b) return false;
			}
			else {
				AnchorMain2 an = new AnchorMain2(this);
				boolean b = an.run(pj1, pj2, dh); if (!b) return false;
			}
			
			saveHitNum();
			saveAnnoHitCnt();
			
		/** finish **/	
			long modDirDate = new File(resultDir).lastModified(); // CAS532 add for Pair Summary with 'use existing files'
			mp.setPairProp("pair_align_date", Utils.getDateStr(modDirDate));
			if (!Constants.VERBOSE) Globals.rclear();
			Utils.prtTimeMemUsage(plog,  "Finish load hits", startTime);
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, "Run load anchors"); return false;}		
	}
	public void interrupt() {bInterrupt = true;	}
	
	/***************************************************************
	 * Assign hitnum, with them ordered by start1 ASC, len DESC
	 * CAS520 creates a hitnum to use for display, which is sequential and does not change on reload
	 * CAS540x moved from AnchorsPost; 
	 */
	private boolean saveHitNum() { 
	try {
		if (Constants.VERBOSE) plog.msg("   Compute and save hit#                                         "); 
		else                   Globals.rprt("Compute and save hit#");
		TreeMap <Integer, String> grpMap1 = mp.mProj1.getGrpIdxMap();
		TreeMap <Integer, String> grpMap2 = mp.mProj2.getGrpIdxMap();
		
		for (int g1 : grpMap1.keySet()) {
			for (int g2 : grpMap2.keySet()) {
				int nhits = dbc2.executeCount("select count(*) from pseudo_hits "
						+ " where grp1_idx="+ g1 + " and grp2_idx=" + g2); 
				if (nhits==0) continue;										// CAS560 mysql error if 0 in next query
				
				dbc2.executeUpdate("update pseudo_hits set hitnum=0 where grp1_idx=" + g1 + " and grp2_idx=" + g2);
				
				HashMap <Integer, Integer> hitNumMap = new HashMap <Integer, Integer> ();
			// assign
				int hitcnt=1;
				ResultSet rs = dbc2.executeQuery("select idx, (end1-start1) as len from pseudo_hits "
						+ " where grp1_idx= " + g1 + " and grp2_idx=" + g2
						+ " order by start1 ASC, len DESC");
				while (rs.next()) {
					int hidx = rs.getInt(1);
					hitNumMap.put(hidx, hitcnt);
					hitcnt++;
				}
				rs.close();
				Globals.rprt(grpMap1.get(g1) + ":" + grpMap2.get(g2) + " " + hitcnt);
				
			// save	
				PreparedStatement ps = dbc2.prepareStatement("update pseudo_hits set hitnum=? where idx=?");
				for (int idx : hitNumMap.keySet()) {
					int num = hitNumMap.get(idx);
					ps.setInt(1, num);
					ps.setInt(2, idx);
					ps.addBatch();
				}
				ps.executeBatch();
			}
		}
		return true;
	}
	catch (Exception e) {ErrorReport.print(e, "compute gene hit cnts"); return false;}
	}
	/********************************************************************
	 * Count number of hits per gene; these include all pairwise projects except self; CAS541 Moved from AnchorsPost; 
	 */
	
	public void saveAnnoHitCnt() { // CAS561 make public for Mpair.removePairFromDB
		if (Constants.VERBOSE && plog!=null) plog.msg("   Compute and save gene numHits"); else Globals.rprt("Compute and save gene numHits");
		TreeMap <Integer, String> idxList1 = mp.mProj1.getGrpIdxMap(); // CAS546 was using SyProj.Group
		TreeMap <Integer, String> idxList2 = mp.mProj2.getGrpIdxMap();
		for (int idx : idxList1.keySet()) if (!saveAnnotHitCnt(idx, idxList1.get(idx))) return;
		for (int idx : idxList2.keySet()) if (!saveAnnotHitCnt(idx, idxList2.get(idx))) return;
		if (!Constants.VERBOSE) Globals.rclear();
	}
	public boolean saveAnnotHitCnt(int grpIdx, String grpName) { // CAS561 make public for Mproject.removeProjectFromDB
		try {
			dbc2.executeUpdate("update pseudo_annot set numhits=0 where grp_idx=" + grpIdx);
			ResultSet rs = dbc2.executeQuery("select count(*) from pseudo_annot where grp_idx=" + grpIdx);
			int cnt = (rs.next()) ? rs.getInt(1) : 0;
			if (cnt==0) return true; 							// this is not a failure 
			
			HashMap <Integer, Integer> geneCntMap = new HashMap <Integer, Integer> ();
			
			rs = dbc2.executeQuery("select pa.idx "
					+ "from pseudo_annot           as pa "
					+ "join pseudo_hits_annot      as pha   on pha.annot_idx  = pa.idx "
					+ "join pseudo_hits			   as ph	on pha.hit_idx    = ph.idx "
					+ "where pa.type='gene' and ph.proj1_idx != ph.proj2_idx and pa.grp_idx=" + grpIdx);
			
			while (rs.next()) {
				int aidx = rs.getInt(1);
				if (geneCntMap.containsKey(aidx)) geneCntMap.put(aidx,geneCntMap.get(aidx)+1);
				else                              geneCntMap.put(aidx, 1);
			}
			rs.close();

			Globals.rprt("Process " + grpName + " Genes " + geneCntMap.size());
			
			PreparedStatement ps = dbc2.prepareStatement("update pseudo_annot set numhits=? where idx=?");
			for (int idx : geneCntMap.keySet()) {
				int num = geneCntMap.get(idx);
				if (num>255) num=255; // CAS543 tinyint unsigned max is 255
				ps.setInt(1, num);
				ps.setInt(2, idx);
				ps.addBatch();
			}
			ps.executeBatch();
			ps.close();
			
			Globals.rclear();
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, "compute gene hit cnts"); return false;}
	}
}
