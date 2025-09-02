package backend;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.TreeMap;

import backend.anchor1.AnchorMain1;
import backend.anchor2.AnchorMain2;
import database.DBconn2;
import symap.Globals;
import symap.manager.Mpair;
import symap.manager.Mproject;
import util.Cancelled;
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
	public boolean doAlgo1=false; 
	
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
		if (mp!=null) {						// Null for Mproject.removeProjectFromDB;
			this.pairIdx = mp.getPairIdx(); 
			doAlgo1 = (mp.isAlgo1(Mpair.FILE));
		}
	}
	
	protected boolean run(Mproject pj1, Mproject pj2) throws Exception {
		try {
		/** setup **/
			long startTime = Utils.getTimeMem();
			
			String proj1Name = pj1.getDBName(), proj2Name = pj2.getDBName();
			if (proj1Name.equals(proj2Name)) plog.msg("\nStart calculating cluster hits for " + proj1Name + " self synteny"); // CAS572 add
			else plog.msg("\nStart calculating cluster hits for " + proj1Name + " and " + proj2Name);
			
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
			if (doAlgo1) {
				AnchorMain1 an = new AnchorMain1(this);
				boolean b = an.run(pj1, pj2, dh); if (!b) return false;
			}
			else {
				AnchorMain2 an = new AnchorMain2(this);
				boolean b = an.run(pj1, pj2, dh); if (!b) return false;
			}
			if (Cancelled.isCancelled()) {dbc2.close(); return false; } 
			
			saveHitNum();
			saveAnnoHitCnt();
			
			/** Run before pseudo **/
			if (pj1.hasGenes() && pj2.hasGenes()) { 
				AnchorPost collinear = new AnchorPost(pairIdx, pj1, pj2, dbc2, plog);
				collinear.collinearSets();	
			}
			if (Cancelled.isCancelled()) {dbc2.close(); return false; } 
			
			// Numbers pseudo genes; must be after collinear;
			if (mp.isNumPseudo(Mpair.FILE)) new Pseudo().addPseudo(); 
			
		/** finish **/	
			long modDirDate = new File(resultDir).lastModified(); // add for Pair Summary with 'use existing files'
			mp.setPairProp("pair_align_date", Utils.getDateStr(modDirDate));
			if (!Constants.VERBOSE) Globals.rclear();
			Utils.prtMsgTimeDone(plog,  "Finish clustering hits", startTime); 
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, "Run load anchors"); return false;}		
	}
	public void interrupt() {bInterrupt = true;	}
	
	/***************************************************************
	 * Assign hitnum, with them ordered by start1 ASC, len DESC
	 */
	private boolean saveHitNum() { 
	try {
		Globals.rprt("Compute and save hit#"); 
		TreeMap <Integer, String> grpMap1 = mp.mProj1.getGrpIdxMap();
		TreeMap <Integer, String> grpMap2 = mp.mProj2.getGrpIdxMap();
		
		for (int g1 : grpMap1.keySet()) {
			for (int g2 : grpMap2.keySet()) {
				int nhits = dbc2.executeCount("select count(*) from pseudo_hits "
						+ " where grp1_idx="+ g1 + " and grp2_idx=" + g2); 
				if (nhits==0) continue;										// mysql error if 0 in next query
				
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
		Globals.rclear();
		return true;
	}
	catch (Exception e) {ErrorReport.print(e, "compute gene hit cnts"); return false;}
	}
	/********************************************************************
	 * Count number of hits per gene; these include all pairwise projects except self; 
	 */
	public void saveAnnoHitCnt() { // public for Mpair.removePairFromDB
		Globals.rprt("Compute and save gene numHits"); 
		
		TreeMap <Integer, String> idxList1 = mp.mProj1.getGrpIdxMap();
		TreeMap <Integer, String> idxList2 = mp.mProj2.getGrpIdxMap();
		
		for (int idx : idxList1.keySet()) if (!saveAnnotHitCnt(idx, idxList1.get(idx))) return;
		for (int idx : idxList2.keySet()) if (!saveAnnotHitCnt(idx, idxList2.get(idx))) return;
		
		Globals.rclear();
	}
	public boolean saveAnnotHitCnt(int grpIdx, String grpName) { // public for Mproject.removeProjectFromDB
		try {
			dbc2.executeUpdate("update pseudo_annot set numhits=0 "
					+ " where grp_idx=" + grpIdx + " and type='gene'"); // numhits is used by pseudo too; 
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

			PreparedStatement ps = dbc2.prepareStatement("update pseudo_annot set numhits=? where idx=?");
			for (int idx : geneCntMap.keySet()) {
				int num = geneCntMap.get(idx);
				if (num>255) num=255; // tinyint unsigned max is 255
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
	// Called in Clear Pair as the pseudo are pair A&S specific
	// numhits is used for the pairIdx for pseudo, otherwise it is the number of hits in the DB to the gene
	public boolean removePseudo() { 
	try {
		String where = "where type='" + Globals.pseudoType + "' and numhits=" + pairIdx;
		
		int before = dbc2.executeCount("select count(*) from pseudo_annot " + where);
		dbc2.executeUpdate("delete from pseudo_annot "    + where);
		int after  = dbc2.executeCount("select count(*) from pseudo_annot " + where);
		
		Globals.tprt(String.format("Remove pseudo %,d annotation (%,d->%,d)",(before-after), before, after));
		
		return true;
	}
	catch (Exception e) {ErrorReport.print(e, "remove pseudo from pseudo_annot"); return false;}
	}
	/** For release v5.6.5 flag -pseudo **/
	protected boolean addPseudoFromFlag() {
	try {
		// This cascades to remove pseudo_hit_annot rows also
		int cnt = dbc2.executeCount("select count(*) from pseudo_annot "
				+ "where type='" + Globals.pseudoType + "' and numhits=" + pairIdx);
		if (cnt>0) { // wipes out ALL species-species pseudo's; only do when testing
			Globals.tprt("Existing pseudo to remove: " + cnt);
			
			removePseudo();
			
			if (!doAlgo1) {
				dbc2.executeUpdate("update pseudo_hits set annot1_idx=0, annot2_idx=0 "
						+ "where pair_idx= " + pairIdx + " and htype='nn'" );
				dbc2.executeUpdate("update pseudo_hits set annot1_idx=0 "
						+ "where pair_idx= " + pairIdx + " and (htype='nE' or htype='nI')" );
				dbc2.executeUpdate("update pseudo_hits set annot2_idx=0 "
						+ "where pair_idx= " + pairIdx + " and (htype='En' or htype='In')" );
			}
			else {// cannot easily zero out annot1_idx and annot2_idx for algo1 g1 hits; but it is only way to know which gene is missing
				Globals.prt("Warning: -pseudo is being run on algo1, this will only work the first time.");
				Globals.prt("You may need to re-run A&S for the complete synteny analysis");
				return false;
			}
		}
		return new Pseudo().addPseudo();
	}
	catch (Exception e) {ErrorReport.print(e, "Add pseudo from flag"); return false;}
	}
	/*******************************************************
	 * Add so can be used in Cluster and Report as regular genes;
	 * Assigns sequentially along chromosome regardless of what the target chromosome is
	 * 	which allows all numbers to be unique; but it may lead to gaps for a give chrQ-chrT
	 */
	private class Pseudo {
		private Mproject mProj1, mProj2;
		private int [] cntPseudo = {0,0};
		private String [] annoKey = {"desc","desc"};
		private String chrPair="";
		
		private Pseudo() {
			this.mProj1 = mp.mProj1;
			this.mProj2 = mp.mProj2;
			
			try { // Give pseudo desc=Not annotated; Use same Desc/Product as used by the project .gff file
				for (int i=0; i<2; i++) {
					int idx = (i==0) ? mProj1.getID() : mProj2.getID();
					ResultSet rs =	dbc2.executeQuery("SELECT keyname, count FROM annot_key " +
							" WHERE  proj_idx = " + idx);
					while(rs.next()) {
						String key = rs.getString(1);
						int cnt = rs.getInt(2);
						String keylc = key.toLowerCase();
						
						if (keylc.equals("desc") || keylc.equals("product") || keylc.equals("description")) {
							annoKey[i] = key;
							if (cnt>50) break;
						}
					}
				}
			}
			catch (Exception e) {ErrorReport.print(e, "Could not get annotation keys");}
		}
		/*****************************************************/
		private boolean addPseudo() {
		try {
			plog.msg("   Assign pseudo genes");
			
			TreeMap <Integer, String> grpMap1 = mp.mProj1.getGrpIdxMap();
			String grpList1 = "";
			for (int g1 : grpMap1.keySet()) {
				if (grpList1.equals("")) grpList1 += g1; 
				else                     grpList1 += "," + g1; 
			}
			TreeMap <Integer, String> grpMap2 = mp.mProj2.getGrpIdxMap();
			String grpList2 = "";
			for (int g2 : grpMap2.keySet()) {
				if (grpList2.equals("")) grpList2 += g2; 
				else                     grpList2 += "," + g2; 
			}
			
			int cnt=1;
			for (int g1 : grpMap1.keySet()) {
				chrPair = mProj1.getDisplayName() + " " + mProj1.getGrpNameFromIdx(g1);
				
				String where = " where grp1_idx= " + g1 + " and grp2_idx IN (" + grpList2 + ") and annot1_idx=0 "; 
				
				if (!addPseudoGrpGrp(g1, where, "annot1_idx", 0, cnt++, grpMap1.size())) return false;
			}
			cnt=1;
			for (int g2 : grpMap2.keySet()) {
				chrPair = mProj2.getDisplayName() + " " + mProj2.getGrpNameFromIdx(g2);
				
				String where = " where grp1_idx IN (" + grpList1  + ") and grp2_idx=" + g2 + " and annot2_idx=0 "; 
				
				if (!addPseudoGrpGrp(g2, where, "annot2_idx", 1, cnt++, grpMap2.size())) return false;
			}
			
			Utils.prtNumMsg(plog, cntPseudo[0], 
					String.format("Pseudo %s     %,d Pseudo %s ", mProj1.getDisplayName(), cntPseudo[1], mProj2.getDisplayName()));
			return true;
		}
		catch (Exception e) {Globals.prt(""); ErrorReport.print(e, "add pseudos"); return false;}
		}
		/*******************************************************
		 * Assign one side at a time; find all that have annotN_idx=0 and update
		 */
		private boolean addPseudoGrpGrp(int grpIdx, String where, String annoStr, int i, int cntGrp, int maxGrp) {
		try {
			String type = Globals.pseudoType;
			
			// Must count pseudo too so as to not repeat pseudo numbers
			int genes = dbc2.executeCount("select max(genenum) from pseudo_annot where grp_idx="+ grpIdx);
			int geneStart = genes; 
			if (genes==0) geneStart = 1;
			else {
				int rem = genes%100;
				int add = 100-rem;
				geneStart += add;
			}
			int genenum = geneStart;
			
		// find pseudo
			String select = (i==0) ? " idx, start1, end1, strand" : " idx, start2, end2, strand";
			String order  = (i==0) ? " order by start1"           : " order by start2";
		
			HashMap <Integer, Hit> hitGeneMap = new HashMap <Integer, Hit> (); // geneNum, hitIdx
			String sql = "select " + select + " from pseudo_hits " + where + order; 		
			
			ResultSet rs = dbc2.executeQuery(sql);
			while (rs.next()) {
				int hitIdx = rs.getInt(1);
				int start  = rs.getInt(2);
				int end    = rs.getInt(3);
				String strand = rs.getString(4);
				String [] tok = strand.split("/");
				if (tok.length==2) strand = tok[i];
				
				Hit ht = new Hit(hitIdx, start, end, strand);
				hitGeneMap.put(genenum, ht);
				genenum++;
			}
			rs.close();
			
			String dn =  (i==0) ? mProj1.getDisplayName() : mProj2.getDisplayName();
			String chr = (i==0) ? mProj1.getGrpNameFromIdx(grpIdx) : mProj2.getGrpNameFromIdx(grpIdx);
			String msg = String.format("Pseudo %-20s   Genes %,6d   Start %,6d ", (dn+" "+chr), genes, geneStart); 
			/* too much output CAS572
			if (Constants.VERBOSE && cntGrp==1) Utils.prtIndentNumMsgFile(plog, 1, hitGeneMap.size(), msg);  
			if (Constants.VERBOSE && cntGrp==2) {
				if (maxGrp>2) msg += "...";
				Utils.prtIndentNumMsgFile(plog, 1, hitGeneMap.size(), msg);  
			}
			else */
			Globals.rprt(String.format("%,5d %s", hitGeneMap.size(),msg));
			
			if (hitGeneMap.size()==0) return true;
			
			cntPseudo[i] += hitGeneMap.size();
			
		// pseudo_annot
			String name= "ID=pseudo-" + mProj1.getdbAbbrev()+"-"+mProj2.getdbAbbrev() +
					";" + annoKey[i] + "=Not annotated; ";
			
			int countBatch=0, cnt=0;
			PreparedStatement ps = dbc2.prepareStatement("insert into pseudo_annot " +
					"(grp_idx, type, genenum, tag, numhits, name, strand, start, end ) "
					+ "values (?,?,?,?,?,?,?,?,?)"); 	
			for (int gn : hitGeneMap.keySet()) {
				Hit ht = hitGeneMap.get(gn);
				
				ps.setInt(1, grpIdx);
				ps.setString(2, type);
				ps.setInt(3, gn);
				ps.setString(4, gn + "." + Globals.pseudoChar); // tag gn.~
				ps.setInt(5, pairIdx); 			// numhits=pairIdx so it can be remove on Pair Remove
				ps.setString(6, name);	
				ps.setString(7, ht.strand);
				ps.setInt(8, ht.start);
				ps.setInt(9, ht.end);
				ps.addBatch(); 
				
				countBatch++; cnt++;
				if (countBatch==1000) {
					countBatch=0;
					ps.executeBatch();
					Globals.rprt(cnt + " added pseudo " + chrPair); 
				}
			}
			if (countBatch>0) ps.executeBatch();
			ps.close();
			
		// pseudo_hit; first, get newly created idx
			rs = dbc2.executeQuery("select idx, genenum from pseudo_annot "
					+ "where type='" + type + "' and grp_idx=" + grpIdx);
			while (rs.next()) {
				int annoIdx = rs.getInt(1);
				int gn = rs.getInt(2);
				if (hitGeneMap.containsKey(gn)) // could be from a previous grp pair
					hitGeneMap.get(gn).annotIdx = annoIdx;
			}
			rs.close();
			
			countBatch=cnt=0;
			ps = dbc2.prepareStatement("update pseudo_hits set " + annoStr + "=? where idx=?");
			for (Hit ht: hitGeneMap.values()) {
				ps.setInt(1, ht.annotIdx);
				ps.setInt(2, ht.hitIdx);
				ps.addBatch(); 
				
				countBatch++; cnt++;
				if (countBatch==1000) {
					countBatch = 0;
					ps.executeBatch();
					Globals.rprt(cnt + " added pseudo to hit " + chrPair); 
				}
			}
			if (countBatch>0) ps.executeBatch();
			ps.close();
		
		// pseudo_hit_annot; olap, exlap, annot2_idx are all 0
			countBatch=cnt=0;
			ps = dbc2.prepareStatement("insert ignore into pseudo_hits_annot "
					+ "(hit_idx, annot_idx, olap, exlap, annot2_idx) values (?,?,0,0,0)");
			for (Hit ht: hitGeneMap.values()) {
				ps.setInt(1, ht.hitIdx);
				ps.setInt(2, ht.annotIdx);
				ps.addBatch(); 
				
				countBatch++; cnt++;
				if (countBatch==1000) {
					countBatch=0;
					ps.executeBatch();
					Globals.rprt(cnt + " added pseudo-hit table " + chrPair); 
				}
			}
			if (countBatch>0) ps.executeBatch();
			ps.close();
			Globals.rclear();
			return true;
		}
		catch (Exception e) {Globals.prt(""); ErrorReport.print(e, "add pseudos"); return false;}
		}
		private class Hit {
			int hitIdx, start, end, annotIdx;
			String strand;
			
			private Hit (int hitIdx, int start, int end, String strand) {
				this.hitIdx = hitIdx;
				this.start = start;
				this.end = end;
				this.strand = strand;
			}
		}
	} // end Pseudo class
}
