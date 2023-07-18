package database;

/*****************************************************
 * CAS506 created to provide an organized why for database updates.
 */
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.HashSet;

import backend.AnchorsMain;
import backend.Utils;
import symap.Globals;
import util.ErrorReport;
import util.Utilities;

public class Version {
	public int dbVer = Globals.DBVER; 
	public String strVer = Globals.DBVERSTR; // db4
	public boolean debug = Globals.DBDEBUG;  // -dbd
	
	private DBconn2 dbc2=null;
	
	public Version (DBconn2 dbc2) {
		this.dbc2 = dbc2;
		checkForUpdate();
		
		if (debug) 	updateDEBUG();
		else 		removeDEBUG();
		
		if (Globals.HITCNT_ONLY) updateHitCnt();
	}
	
	// if first run from viewSymap, updates anyway (so if no write permission, crashes)
	private void checkForUpdate() {
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
		}
		catch (Exception e) {ErrorReport.print(e, "Error checking database version");}
	}
	private void updateVer1() {
		try {
			if (tableRename("groups", "xgroups"))
				updateProps();
		}
		catch (Exception e) {ErrorReport.print(e, "Could not update database");}
	}
	// CAS512 gather all columns added with 'alter'
	private void updateVer2() {
		try {
			tableCheckDropColumn("pseudo_annot", "text"); // lose 512
			tableCheckAddColumn("pseudo_annot", "genenum", "INTEGER default 0", null);  // added in SyntenyMain
			tableCheckAddColumn("pseudo_annot", "gene_idx", "INTEGER default 0", null); // new 512
			tableCheckAddColumn("pseudo_annot", "tag", "VARCHAR(30)", null); 			// new 512
			tableCheckAddColumn("pseudo_hits", "runsize", "INTEGER default 0", null);   // checked in SyntenyMain
			tableCheckAddColumn("pairs", "params", "VARCHAR(128)", null);				// added 511
			tableCheckAddColumn("blocks", "corr", "float default 0", null);				// SyMAPExp was checking for
			
			updateProps();
		}
		catch (Exception e) {ErrorReport.print(e, "Could not update database");}
	}
	// v5.20.0
	private void updateVer3() {
		try {
			dbc2.executeUpdate("alter table pseudo_hits modify countpct integer");  // Remove from AnchorsMain, 169
			tableCheckAddColumn("pseudo_hits",  "runnum",  "integer default 0", null);
			tableCheckAddColumn("pseudo_hits",  "hitnum",  "integer default 0", null);
			tableCheckAddColumn("pseudo_annot", "numhits", "tinyint unsigned default 0", null);
			
			tableCheckAddColumn("pairs",    "params", "tinytext", null); // added in earlier version, but never checked
			tableCheckAddColumn("pairs",    "syver", "tinytext", null);
			tableCheckAddColumn("projects", "syver", "tinytext", null);
			
			updateProps();
			
			System.err.println("For pre-v519 databases, reload annotation ");
			System.err.println("For pre-v520 databases, recompute synteny ");
		}
		catch (Exception e) {ErrorReport.print(e, "Could not update database");}
	}
	// v5.3.0 (code has v522, but major change)
	private void updateVer4() {
		try {
			tableDrop("mrk_clone");
			tableDrop("mrk_ctg"); 
			tableDrop("clone_remarks_byctg"); 
			tableDrop("bes_block_hits");
			tableDrop("mrk_block_hits"); 
			tableDrop("fp_block_hits");
			tableDrop("shared_mrk_block_hits");
			tableDrop("shared_mrk_filter");
			
			tableDrop("clones"); 
			tableDrop("markers");  
			tableDrop("bes_seq"); 
			tableDrop("mrk_seq");
			tableDrop("clone_remarks"); 
			tableDrop("bes_hits"); 
			tableDrop("mrk_hits");
			tableDrop("fp_hits"); 
			tableDrop("ctghits");
			tableDrop("contigs"); 
			
			tableDrop("mrk_filter"); 
			tableDrop("bes_filter");
			tableDrop("fp_filter"); 
			tableDrop("pseudo_filter"); // never used
			
			tableCheckDropColumn("blocks", "level");
			tableCheckDropColumn("blocks", "contained");
			tableCheckDropColumn("blocks", "ctgs1");
			tableCheckDropColumn("blocks", "ctgs2");
				
			updateProps();
			System.err.println("FPC tables removed from Schema. No user action necessary.\n"
					+ "   Older verion SyMAP will not work with this database.");
		}
		catch (Exception e) {ErrorReport.print(e, "Could not update database");}
	}
	// v5.4.3; however, these updates will be used in v5.4.4
	private void updateVer5() {
		try {
			tableCheckAddColumn("pseudo_hits", "htype", "tinyint unsigned default 0", "evalue");// has to take evalue place
			tableCheckDropColumn("pseudo_hits",  "evalue");
			tableCheckAddColumn("pseudo_hits_annot", "htype", "tinyint unsigned default 0", null);
			
			updateProps();
			
			System.err.println("To use the v5.4.3 hit-gene assignment upgrade, recompute synteny ");
		}
		catch (Exception e) {ErrorReport.print(e, "Could not update database");}
	}
	/****************************************************
	 * -dbd
	 */
	private void updateDEBUG() {
	try {
		if (tableColumnExists("pairs", "proj_names")) return;
		
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
		if (!tableColumnExists("pairs", "proj_names")) {
			tableCheckAddColumn("pairs",  "proj_names",  "tinytext", "proj2_idx");
			for (int pidx : pairsProjs.keySet()) {
				dbc2.executeUpdate("update pairs set proj_names='" + pairsProjs.get(pidx) + "' where idx=" + pidx);
			}
			System.out.println("Update pairs");
		}
	// add xgroups.proj_name	
		if (!tableColumnExists("xgroups", "proj_name")) {
			tableCheckAddColumn("xgroups",  "proj_name",  "tinytext", "fullname");
			for (int pidx : projIdxName.keySet()) {
				dbc2.executeUpdate("update xgroups set proj_name='" + projIdxName.get(pidx) + "' where proj_idx=" + pidx);
			}
			System.out.println("Update xgroups");
		}
	// add blocks.proj_names, grp1name, grp2name
		if (!tableColumnExists("blocks", "proj_names")) {
			tableCheckAddColumn("blocks",  "proj_names", "tinytext", "pair_idx");
			for (int pidx : pairsProjs.keySet()) {
				dbc2.executeUpdate("update blocks set proj_names='" + pairsProjs.get(pidx) + "' where pair_idx=" + pidx);
			}
			System.out.println("Update blocks.pairs");
		}
		if (!tableColumnExists("blocks", "grp1")) {
			tableCheckAddColumn("blocks",  "grp1",  "tinytext", "proj_names");	
			tableCheckAddColumn("blocks",  "grp2",  "tinytext", "grp1");	
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
		
		tableCheckAddColumn("pseudo_hits_annot",  "xpair_idx",  "integer", null);
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
		
		tableCheckAddColumn("pseudo_hits_annot",  "xgrp_idx",  "integer", null);	
		
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
			if (!tableColumnExists("blocks", "proj_names")) return;
			
			tableCheckDropColumn("pairs",  "proj_names");
			tableCheckDropColumn("xgroups","proj_name");			
			tableCheckDropColumn("blocks", "proj_names");
			tableCheckDropColumn("blocks", "grp1");
			tableCheckDropColumn("blocks", "grp2");
			tableCheckDropColumn("pseudo_hits_annot", "xpair_idx");
			tableCheckDropColumn("pseudo_hits_annot", "xgrp_idx");
			System.out.println("Remove blocks.grp1/2, and blocks/pairs/xgroups proj_names, and pseudo_hits_annot proj/pair_idx");
		}catch (Exception e) {ErrorReport.print(e, "Could not remove debug");}
	}
	/***************************************************************
	 * Run after every version update.
	 * The props table values of DBVER and UPDATE are hardcoded in Schema.
	 * 
	 * if screw up, force update: update props set value=1 where name="DBVER" 
	 */
	private void updateProps() {
		try {
			/** CAS520 why check
			ResultSet rs = pool.executeQuery("select value from props where name='VERSION'");
			if (rs.next()) {
				String v = rs.getString(1);
				if (v.contentEquals("3.0")) // value before v5.0.6
					replaceProps("VERSION",SyMAP.VERSION);
			}
			**/
			replaceProps("UPDATE", Utilities.getDateOnly());
			
			replaceProps("VERSION",Globals.VERSION);
			
			replaceProps("DBVER", Globals.DBVERSTR);
			System.err.println("Complete schema update to " +  Globals.DBVERSTR + " for SyMAP " + Globals.VERSION );
		}
		catch (Exception e) {ErrorReport.print(e, "Error updating props");}
	}
	public void updateReplaceProp() { // CAS543 new Version(DBconn2 dbc).updateReplaceProp
		try {
			replaceProps("UPDATE", Utilities.getDateOnly());
			
			replaceProps("VERSION",Globals.VERSION);
		}
		catch (Exception e) {ErrorReport.print(e, "Error replacing props");}
	}
	private void updateHitCnt() {
		try {
			System.out.println("Starting update of gene hit counts");
			HashSet <Integer> grpIdx = new HashSet <Integer> ();
			ResultSet rs = dbc2.executeQuery("select idx from xgroups");
			while (rs.next()) grpIdx.add(rs.getInt(1));
			
			AnchorsMain am = new AnchorsMain(dbc2);
			int cnt=1;
			for (int idx : grpIdx)  {
				System.out.print("   Update " + cnt + " of " + grpIdx.size() + "\r");
				am.setAnnotHits(idx);
				cnt++;
			}
			dbc2.close();
			System.out.println("Complete update of gene hit counts");
			System.out.println("Restart symap without the -y");
			System.exit(0);
		}
		catch (Exception e) {ErrorReport.print(e, "Update hit count");}
	}
	/****************************************************************************/
	/***************************************************************************
	 * Methods to modify the MySQL database.
	 ******************************************************************/

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
	// MySQL v8 groups is a new special keywords, so the ` is necessary for that particular rename.

	private boolean tableRename(String oldTable, String newTable) {
		String sql = "RENAME TABLE `" + oldTable + "` TO " + newTable;
		
		try {
			if (tableExists(oldTable))
				dbc2.executeUpdate(sql);
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, sql); return false;}
	}
	private boolean tableCheckAddColumn(String table, String col, String type, String aft) throws Exception {
		String cmd = "alter table " + table + " add " + col + " " + type ;
		try {
			if (!tableColumnExists(table,col)){
				if (aft!=null && !aft.trim().equals("")) cmd += " after " + aft;
				dbc2.executeUpdate(cmd);
				return true;
			}
			return false;
		}
		catch(Exception e) {ErrorReport.print(e, "MySQL error: " + cmd);}
		return false;
	}
	
	private void tableCheckDropColumn(String table, String col) throws Exception {
		if (tableColumnExists(table,col)) {
			String cmd = "alter table " + table + " drop " + col;
			dbc2.executeUpdate(cmd);
		}
	}
	private boolean tableDrop(String table) {
		String sql = "Drop table " + table;
		
		try {
			if (tableExists(table))
				dbc2.executeUpdate(sql);
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, sql); return false;}
	}
	
	private boolean tableExists(String name) throws Exception {
		ResultSet rs = dbc2.executeQuery("show tables");
		while (rs.next()) {
			if (rs.getString(1).equals(name)) {
				rs.close();
				return true;
			}
		}
		if (rs!=null) rs.close();
		return false;
	}
	private boolean tableColumnExists(String table, String column) throws Exception {
		ResultSet rs = dbc2.executeQuery("show columns from " + table);
		while (rs.next()) {
			if (rs.getString(1).equals(column)) {
				rs.close();
				return true;
			}
		}
		if (rs!=null) rs.close();
		return false;
	}
	/* Below are not - yet 
	private void tableCheckRenameColumn(String table, String oldCol, String newCol, String type) throws Exception {
		if (tableColumnExists(table,oldCol)) {
			String cmd = "alter table " + table + " change column `" + oldCol + "` " + newCol + " " +  type ;
			pool.executeUpdate(cmd);
		}
	}
	private void tableCheckModifyColumn(String table, String col, String type) throws Exception {
		if (tableColumnExists(table,col))
		{
			String curDesc = tableGetColDesc(table,col);
			if (!curDesc.equalsIgnoreCase(type)) {
				String cmd = "alter table " + table + " modify " + col + " " + type ;
				pool.executeUpdate(cmd);
			}
		}
		else {
			System.err.println("Warning: tried to change column " + table + "." + col + ", which does not exist");
		}
	}
	private String tableGetColDesc(String tbl, String col) {
		String ret = "";
		try {
			ResultSet rs = pool.executeQuery("describe " + tbl);
			while (rs.next()) {
				String fld = rs.getString("Field");
				String desc = rs.getString("Type");
				if (fld.equalsIgnoreCase(col)) {
					ret = desc;
					break;
				}
			}
		}
		catch(Exception e) {ErrorReport.print(e, "checking column description for " + tbl + "." + col);}
		return ret;
	}
	*/
}
