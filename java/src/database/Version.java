package database;

/*****************************************************
 * Schema update: select value from props where name='DBVER'
 * Pair update:   select idx, syVer from pairs
 * 
 * CAS506 created to provide an organized why for database updates.
 * MySQL v8 'groups' is a new special keywords, so the ` is necessary for that particular rename.
 * CAS546 removed table methods and use DBconn2
 */
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.HashSet;

import backend.Utils;
import backend.AnchorMain;
import symap.Globals;
import util.ErrorReport;
import util.Utilities;

public class Version {
	public int dbVer = Globals.DBVER; 
	public String strVer = Globals.DBVERSTR; // db7
	public boolean dbdebug = Globals.DBDEBUG;  // -dbd
	
	private DBconn2 dbc2=null;
	
	public Version (DBconn2 dbc2) {
		this.dbc2 = dbc2;
		checkForSchemaUpdate();
		checkForContentUpdate();
		
		if (dbdebug) 	updateDEBUG();
		else 			removeDEBUG();
	}
	
	// if first run from viewSymap, updates anyway (so if no write permission, crashes)
	private void checkForSchemaUpdate() {
		try {
			String strDBver = "db1";
			ResultSet rs = dbc2.executeQuery("select value from props where name='DBVER'");
			if (rs.next()) strDBver=rs.getString(1);
			
			int idb = 1;
			if (strDBver!=null && strDBver.startsWith("db")) {
				String n = strDBver.substring(2);
				try {
					idb = Integer.parseInt(n);
				}
				catch (Exception e) {ErrorReport.print(e, "Cannot parse DBVER " + strDBver);};
			}
			if (idb==dbVer) return; 
			
			if (idb>dbVer) { // CAS546 add
				Utilities.showWarningMessage("This database schema is " + strDBver + "; this SyMAP version uses " + strVer +
						"\nThis may not be a problem.....");
				return;
			}
			
			if (!Utilities.showYesNo("DB update", 					// CAS544 change from continue
					"Database schema needs updating from " + strDBver + " to " + strVer +"\nProceed with update?")) return;
			
			System.out.println("Updating schema from " + strDBver + " to " + strVer);
			
			if (idb==1) {
				updateVer1();
				idb=2;
			}
			if (idb==2) {
				updateVer2();
				idb=3;
			}
			if (idb==3) {
				updateVer3();
				idb=4;
			}
			if (idb==4) {
				updateVer4();
				idb=5;
			}
			if (idb==5) {
				updateVer5();
				idb=6;
			}
			if (idb==6) {
				updateVer6();
				idb=7;
			}
		}
		catch (Exception e) {ErrorReport.print(e, "Error checking database version");}
	}
	private void updateVer1() {
		try {
			if (dbc2.tableRename("groups", "xgroups"))
				updateProps();
		}
		catch (Exception e) {ErrorReport.print(e, "Could not update database");}
	}
	// CAS512 gather all columns added with 'alter'
	private void updateVer2() {
		try {
			dbc2.tableCheckDropColumn("pseudo_annot", "text"); // lose 512
			dbc2.tableCheckAddColumn("pseudo_annot", "genenum", "INTEGER default 0", null);  // added in SyntenyMain
			dbc2.tableCheckAddColumn("pseudo_annot", "gene_idx", "INTEGER default 0", null); // new 512
			dbc2.tableCheckAddColumn("pseudo_annot", "tag", "VARCHAR(30)", null); 			// new 512
			dbc2.tableCheckAddColumn("pseudo_hits", "runsize", "INTEGER default 0", null);   // checked in SyntenyMain
			dbc2.tableCheckAddColumn("pairs", "params", "VARCHAR(128)", null);				// added 511
			dbc2.tableCheckAddColumn("blocks", "corr", "float default 0", null);				// SyMAPExp was checking for
			
			updateProps();
		}
		catch (Exception e) {ErrorReport.print(e, "Could not update database");}
	}
	// v5.20.0
	private void updateVer3() {
		try {
			dbc2.executeUpdate("alter table pseudo_hits modify countpct integer");  // Remove from AnchorsMain, 169
			dbc2.tableCheckAddColumn("pseudo_hits",  "runnum",  "integer default 0", null);
			dbc2.tableCheckAddColumn("pseudo_hits",  "hitnum",  "integer default 0", null);
			dbc2.tableCheckAddColumn("pseudo_annot", "numhits", "tinyint unsigned default 0", null);
			
			dbc2.tableCheckAddColumn("pairs",    "params", "tinytext", null); // added in earlier version, but never checked
			dbc2.tableCheckAddColumn("pairs",    "syver", "tinytext", null);
			dbc2.tableCheckAddColumn("projects", "syver", "tinytext", null);
			
			updateProps();
			
			System.err.println("For pre-v519 databases, reload annotation ");
			System.err.println("For pre-v520 databases, recompute synteny ");
		}
		catch (Exception e) {ErrorReport.print(e, "Could not update database");}
	}
	// v5.3.0 (code has v522, but major change)
	private void updateVer4() {
		try {
			dbc2.tableDrop("mrk_clone");
			dbc2.tableDrop("mrk_ctg"); 
			dbc2.tableDrop("clone_remarks_byctg"); 
			dbc2.tableDrop("bes_block_hits");
			dbc2.tableDrop("mrk_block_hits"); 
			dbc2.tableDrop("fp_block_hits");
			dbc2.tableDrop("shared_mrk_block_hits");
			dbc2.tableDrop("shared_mrk_filter");
			
			dbc2.tableDrop("clones"); 
			dbc2.tableDrop("markers");  
			dbc2.tableDrop("bes_seq"); 
			dbc2.tableDrop("mrk_seq");
			dbc2.tableDrop("clone_remarks"); 
			dbc2.tableDrop("bes_hits"); 
			dbc2.tableDrop("mrk_hits");
			dbc2.tableDrop("fp_hits"); 
			dbc2.tableDrop("ctghits");
			dbc2.tableDrop("contigs"); 
			
			dbc2.tableDrop("mrk_filter"); 
			dbc2.tableDrop("bes_filter");
			dbc2.tableDrop("fp_filter"); 
			dbc2.tableDrop("pseudo_filter"); // never used
			
			dbc2.tableCheckDropColumn("blocks", "level");
			dbc2.tableCheckDropColumn("blocks", "contained");
			dbc2.tableCheckDropColumn("blocks", "ctgs1");
			dbc2.tableCheckDropColumn("blocks", "ctgs2");
				
			updateProps();
			System.err.println("FPC tables removed from Schema. No user action necessary.\n"
					+ "   Older verion SyMAP will not work with this database.");
		}
		catch (Exception e) {ErrorReport.print(e, "Could not update database");}
	}
	// v5.4.3; however, these updates will be used in v5.4.4
	private void updateVer5() {
		try {
			dbc2.tableCheckAddColumn("pseudo_hits", "htype", "tinyint unsigned default 0", "evalue");// has to take evalue place
			dbc2.tableCheckDropColumn("pseudo_hits",  "evalue");
			dbc2.tableCheckAddColumn("pseudo_hits_annot", "htype", "tinyint unsigned default 0", null);
			
			updateProps();
			
			System.err.println("To use the v5.4.3 hit-gene assignment upgrade, recompute synteny ");
		}
		catch (Exception e) {ErrorReport.print(e, "Could not update database");}
	}
	// v5.4.6; htype was not being used; it is now with anchor2, but used as text
	private void updateVer6() {
		try {
			dbc2.tableCheckModifyColumn("pseudo_hits", "htype", "tinytext");
			dbc2.tableCheckAddColumn("pseudo_hits_annot", "exlap", "tinyint default 0", null);
			dbc2.tableCheckAddColumn("pseudo_hits_annot", "annot2_idx", "integer default 0", null);
			
			updateProps();
		}
		catch (Exception e) {ErrorReport.print(e, "Could not update database");}
	}
	
	/****************************************************
	 * -dbd
	 */
	private void updateDEBUG() {
	try {
		if (dbc2.tableColumnExists("pairs", "proj_names")) return;
		
		long time = Utils.getTime();
		System.out.println("Update DB debug");
		
// projs <idx, proj_name>	
		HashMap <Integer, String> projIdxName = new HashMap <Integer, String> ();
		ResultSet rs = dbc2.executeQuery("select idx, name from projects");
		while (rs.next()) projIdxName.put(rs.getInt(1), rs.getString(2));
		
// grps <idx, grpname>   <idx, proj_idx>
		HashMap <Integer, String> grpIdxName = new HashMap <Integer, String> ();
		rs = dbc2.executeQuery("select idx, name, proj_idx from xgroups");
		while (rs.next()) grpIdxName.put(rs.getInt(1), rs.getString(2));

// pairs <idx, proj1_idx:proj2_idx)	
		HashMap <Integer, String> pairsProjs = new HashMap <Integer, String> ();
		rs = dbc2.executeQuery("select idx, proj1_idx, proj2_idx from pairs");
		while (rs.next()) {
			int idx1 = rs.getInt(2);
			int idx2 = rs.getInt(3);
			String key = projIdxName.get(idx1) + ":" + projIdxName.get(idx2);
			pairsProjs.put(rs.getInt(1), key);
		}
		
   // add pairs.proj_names	
		if (!dbc2.tableColumnExists("pairs", "proj_names")) {
			dbc2.tableCheckAddColumn("pairs",  "proj_names",  "tinytext", "proj2_idx");
			for (int pidx : pairsProjs.keySet()) {
				dbc2.executeUpdate("update pairs set proj_names='" + pairsProjs.get(pidx) + "' where idx=" + pidx);
			}
			System.out.println("Update pairs");
		}
	// add xgroups.proj_name	
		if (!dbc2.tableColumnExists("xgroups", "proj_name")) {
			dbc2.tableCheckAddColumn("xgroups",  "proj_name",  "tinytext", "fullname");
			for (int pidx : projIdxName.keySet()) {
				dbc2.executeUpdate("update xgroups set proj_name='" + projIdxName.get(pidx) + "' where proj_idx=" + pidx);
			}
			System.out.println("Update xgroups");
		}
	// add blocks.proj_names, grp1name, grp2name
		if (!dbc2.tableColumnExists("blocks", "proj_names")) {
			dbc2.tableCheckAddColumn("blocks",  "proj_names", "tinytext", "pair_idx");
			for (int pidx : pairsProjs.keySet()) {
				dbc2.executeUpdate("update blocks set proj_names='" + pairsProjs.get(pidx) + "' where pair_idx=" + pidx);
			}
			System.out.println("Update blocks.pairs");
		}
		if (!dbc2.tableColumnExists("blocks", "grp1")) {
			dbc2.tableCheckAddColumn("blocks",  "grp1",  "tinytext", "proj_names");	
			dbc2.tableCheckAddColumn("blocks",  "grp2",  "tinytext", "grp1");	
			for (int gidx : grpIdxName.keySet()) {
				dbc2.executeUpdate("update blocks set grp1='" + grpIdxName.get(gidx) + "' where grp1_idx=" +gidx);
				dbc2.executeUpdate("update blocks set grp2='" + grpIdxName.get(gidx) + "' where grp2_idx=" +gidx);
			}
			System.out.println("Update blocks.grps");
		}
		
	// CAS540x -dbd add pseudo_annot_hit.pair_idx	THIS IS SLOW
		HashSet <Integer> annotSet = new HashSet <Integer> ();
		HashSet <Integer> hitSet = new HashSet <Integer> ();
		rs = dbc2.executeQuery("select hit_idx, annot_idx from pseudo_hits_annot");
		while (rs.next()) {
			hitSet.add(rs.getInt(1));
			annotSet.add(rs.getInt(2));
		}
		System.out.println("hits to genes: " + hitSet.size() + " " + annotSet.size());
		
		HashMap <Integer, Integer> hitPairMap = new HashMap <Integer, Integer> ();
		rs = dbc2.executeQuery("select idx, pair_idx from pseudo_hits");
		while (rs.next()) {
			int idx = rs.getInt(1);
			if (hitSet.contains(idx))
				hitPairMap.put(idx, rs.getInt(2));
		}
		System.out.println("Hits to process for xpair_idx: " + hitPairMap.size());
		
		dbc2.tableCheckAddColumn("pseudo_hits_annot",  "xpair_idx",  "integer", null);
		int cnt=0;
		for (int hidx : hitPairMap.keySet()) {
			cnt++;
			if (cnt%5000==0) System.out.print("   added " + cnt + " ....");
			dbc2.executeUpdate("update pseudo_hits_annot set xpair_idx=" + hitPairMap.get(hidx) + " where hit_idx=" +hidx);
		}
		hitPairMap.clear(); hitSet.clear();
		System.out.println("Update pseudo_hits_annot.pair_idx                \r");
		
	// CAS540x -dbd add pseudo_annot_hit.grp_idx and proj_idx
	
		HashMap <Integer, Integer> annotGrpMap = new HashMap <Integer, Integer> (); // annot_idx, grp_idx
		
		rs = dbc2.executeQuery("select idx, grp_idx from pseudo_annot");
		while (rs.next()) {
			int idx = rs.getInt(1);
			if (annotSet.contains(idx))
				annotGrpMap.put(idx, rs.getInt(2));
		}
		System.out.println("Annot to process for xgrp_idx: " + annotGrpMap.size());
		
		dbc2.tableCheckAddColumn("pseudo_hits_annot",  "xgrp_idx",  "integer", null);	
		
		cnt=0;
		for (int aidx : annotGrpMap.keySet()) {
			cnt++;
			if (cnt%5000==0) System.out.print("   added " + cnt + " ....\r");
			int gidx = annotGrpMap.get(aidx);
			dbc2.executeUpdate("update pseudo_hits_annot set xgrp_idx=" + gidx + " where annot_idx=" +aidx);
		}
		annotGrpMap.clear();
		
		rs.close();
		System.out.println("Update pseudo_hits_annot.grp_idx");
		Utils.timeDoneMsg(null, "Update ", time );
		
	}catch (Exception e) {ErrorReport.print(e, "Could not update database for debug");}
	}
	private void removeDEBUG() {
		try {
			if (!dbc2.tableColumnExists("blocks", "proj_names")) return;
			
			dbc2.tableCheckDropColumn("pairs",  "proj_names");
			dbc2.tableCheckDropColumn("xgroups","proj_name");			
			dbc2.tableCheckDropColumn("blocks", "proj_names");
			dbc2.tableCheckDropColumn("blocks", "grp1");
			dbc2.tableCheckDropColumn("blocks", "grp2");
			dbc2.tableCheckDropColumn("pseudo_hits_annot", "xpair_idx");
			dbc2.tableCheckDropColumn("pseudo_hits_annot", "xgrp_idx");
			System.out.println("Remove blocks.grp1/2, and blocks/pairs/xgroups proj_names, and pseudo_hits_annot proj/pair_idx");
		}catch (Exception e) {ErrorReport.print(e, "Could not remove debug");}
	}
	
	/************************************************************************
	 * CAS548 if only DB content needs updating per project, do it here
	 */
	private void checkForContentUpdate() {
	try {
		// can only check version by making it intege
		HashMap <Integer, Integer> pairVer = new HashMap <Integer, Integer> ();
		ResultSet rs = dbc2.executeQuery("select idx, syVer from pairs");
		while (rs.next()) {
			int idx = rs.getInt(1);
			String ver = rs.getString(2);
			if (ver!=null) { // v5.5.2c
				String x= ver.replaceAll("([a-z])", ""); // CAS552c remove any chars 
				x = x.replaceAll("\\.", ""); 
				int y = Utilities.getInt(x);
				pairVer.put(idx, y);
			}
		}
		
		int chg=0;
		for (int idx : pairVer.keySet()) {
			int ver = pairVer.get(idx);
			if (ver<548) { 			// Update for v548
				int algo1 = dbc2.executeCount("select value from pair_props where name='algo1' and pair_idx=" + idx);
				
				if (algo1==1) {
					chg++;// gene_overlap% and pseudo_hits_annot.annot2_idx (for multi query)
					System.err.println(getProjNames(idx) + ": Synteny for  needs to be rerun for v548 Algo1 new features");
				}
				else    {
					chg++;// gene and exon overlap, plus improved assignments	
					System.err.println(getProjNames(idx) + ": Synteny for needs to be rerun for v548 Algo2 new features");
				}
			}
			else if (ver<556) {	// Update for v556 (added in v557)
				chg++;
				System.err.println(getProjNames(idx) + ": Collinear sets for  needs to be rerun for v556 improvements");
			}
		}
		
		if (chg>0) {
			System.err.println(" See https://csoderlund.github.io/SyMAP/SystemGuide.html#update for how to update.");
		}
	}
	catch (Exception e) {ErrorReport.print(e, "Check For Content update");}
	}
	private String getProjNames(int pairIdx) {
	try {
		int p1 =  dbc2.executeCount("select proj1_idx from pairs where idx=" + pairIdx);
		int p2 =  dbc2.executeCount("select proj2_idx from pairs where idx=" + pairIdx);
		String n1 = dbc2.executeString("select name from projects where idx=" + p1);
		String n2 = dbc2.executeString("select name from projects where idx=" + p2);
		return n1 + " vs " + n2;
	}
	catch (Exception e) {ErrorReport.print(e, "Get project name"); return "error";}
	}
	
	/***************************************************************
	 * Run after every version update.
	 * The props table values of DBVER and UPDATE are hardcoded in Schema.
	 * 
	 * if screw up, force update: update props set value=1 where name="DBVER" 
	 */
	private void updateProps() {
		try {
			replaceProps("UPDATE", Utilities.getDateOnly());
			
			replaceProps("VERSION",Globals.VERSION);
			
			replaceProps("DBVER", Globals.DBVERSTR);
			System.err.println("Complete schema update to " +  Globals.DBVERSTR + " for SyMAP " + Globals.VERSION );
		}
		catch (Exception e) {ErrorReport.print(e, "Error updating props");}
	}
	/*************************************************************
	 * Any update from ManagerFrame calls this
	 */
	public void updateReplaceProp() { // CAS543 new Version(DBconn2 dbc).updateReplaceProp
		try {
			replaceProps("UPDATE", Utilities.getDateOnly());
			
			replaceProps("VERSION",Globals.VERSION);
		}
		catch (Exception e) {ErrorReport.print(e, "Error replacing props");}
	}
	private void replaceProps(String name, String value) {
		String sql="";
		try {
			ResultSet rs = dbc2.executeQuery("select value from props where name='" + name + "'");
			if (rs.next()) {
				sql = "UPDATE props set value='" + value + "' where name='" + name + "'; ";
			}
			else {
				sql = "INSERT INTO props (name,value) VALUES " + "('" + name + "','" +  value + "'); ";	
			}
			dbc2.executeUpdate(sql);
		}
		catch (Exception e) {ErrorReport.print(e, "Replace props: " + sql);}
	}
}
