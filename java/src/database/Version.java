package database;

/*****************************************************
 * CAS506 created to provide an organized why for database updates.
 */
import java.sql.ResultSet;
import java.util.HashMap;

import symap.Globals;
import util.ErrorReport;
import util.Utilities;

public class Version {
	public int dbVer = Globals.DBVER; 
	public String strVer = Globals.DBVERSTR; // db4
	public boolean debug = Globals.DEBUG;
	
	public Version (DBuser pool) {
		this.pool = pool;
		checkForUpdate();
		if (debug) 	updateDEBUG();
		else 		removeDEBUG();
	}
	
	// if first run from viewSymap, updates anyway (so if no write permission, crashes)
	private void checkForUpdate() {
		try {
			String strDBver = "db1";
			ResultSet rs = pool.executeQuery("select value from props where name='DBVER'");
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
			
			if (!Utilities.showContinue("DB update", 
					"Database schema needs updating from " + strDBver + " to " + strVer)) return;
			
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
			pool.executeUpdate("alter table pseudo_hits modify countpct integer");  // Remove from AnchorsMain, 169
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
	/****************************************************
	 * 
	 */
	private void updateDEBUG() {
	try {
		System.out.println("Update DB debug");
// projs.(idx,name)	
		HashMap <Integer, String> projs = new HashMap <Integer, String> ();
		ResultSet rs = pool.executeQuery("select idx, name from projects");
		while (rs.next()) projs.put(rs.getInt(1), rs.getString(2));
// grps.(idx,name)
		HashMap <Integer, String> grps = new HashMap <Integer, String> ();
		rs = pool.executeQuery("select idx, name from xgroups");
		while (rs.next()) grps.put(rs.getInt(1), rs.getString(2));
// pairs.(idx,proj1_idx, proj2_idx)	
		HashMap <Integer, String> pairs = new HashMap <Integer, String> ();
		rs = pool.executeQuery("select idx, proj1_idx, proj2_idx from pairs");
		while (rs.next()) {
			int idx1 = rs.getInt(2);
			int idx2 = rs.getInt(3);
			String key = projs.get(idx1) + ":" + projs.get(idx2);
			pairs.put(rs.getInt(1), key);
		}
		
   // pairs.proj_names	
		if (!tableColumnExists("pairs", "proj_names")) {
			tableCheckAddColumn("pairs",  "proj_names",  "tinytext", "proj2_idx");
			for (int pidx : pairs.keySet()) {
				pool.executeUpdate("update pairs set proj_names='" + pairs.get(pidx) 
					+ "' where idx=" + pidx);
			}
			System.out.println("Update pairs");
		}
	// xgroups.proj_name	
		if (!tableColumnExists("xgroups", "proj_name")) {
			tableCheckAddColumn("xgroups",  "proj_name",  "tinytext", "fullname");
			for (int pidx : projs.keySet()) {
				pool.executeUpdate("update xgroups set proj_name='" + projs.get(pidx) 
					+ "' where proj_idx=" + pidx);
			}
			System.out.println("Update xgroups");
		}
	// blocks.proj_names
		if (!tableColumnExists("blocks", "proj_names")) {
			tableCheckAddColumn("blocks",  "proj_names", "tinytext", "pair_idx");
			for (int pidx : pairs.keySet()) {
				pool.executeUpdate("update blocks set proj_names='" + pairs.get(pidx) 
				+ "' where pair_idx=" + pidx);
			}
			System.out.println("Update blocks.pairs");
		}
		if (!tableColumnExists("blocks", "grp1")) {
			tableCheckAddColumn("blocks",  "grp1",  "tinytext", "proj_names");	
			tableCheckAddColumn("blocks",  "grp2",  "tinytext", "grp1");	
			for (int gidx : grps.keySet()) {
				pool.executeUpdate("update blocks set grp1='" + grps.get(gidx) 
					+ "' where grp1_idx=" +gidx);
				pool.executeUpdate("update blocks set grp2='" + grps.get(gidx) 
					+ "' where grp2_idx=" +gidx);
			}
			System.out.println("Update blocks.grps");
		}
		
	}catch (Exception e) {ErrorReport.print(e, "Could not update database for debug");}
	}
	private void removeDEBUG() {
		try {
			if (!tableColumnExists("blocks", "proj_names")) return;
			tableCheckDropColumn("blocks", "proj_names");
			tableCheckDropColumn("blocks", "grp1");
			tableCheckDropColumn("blocks", "grp2");
			System.out.println("Remove blocks.grps");
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
			replaceProps("VERSION",Globals.VERSION);
			
			replaceProps("UPDATE", Utilities.getDateOnly());
			
			replaceProps("DBVER", Globals.DBVERSTR);
			System.err.println("Complete schema update to " +  Globals.DBVERSTR + " for SyMAP " + Globals.VERSION );
		}
		catch (Exception e) {ErrorReport.print(e, "Error checking database version");}
	}
	
	/****************************************************************************/
	/***************************************************************************
	 * Methods to modify the MySQL database.
	 ******************************************************************/

	private void replaceProps(String name, String value) {
		String sql="";
		try {
			ResultSet rs = pool.executeQuery("select value from props where name='" + name + "'");
			if (rs.next()) {
				sql = "UPDATE props set value='" + value + "' where name='" + name + "'; ";
			}
			else {
				sql = "INSERT INTO props (name,value) VALUES " + "('" + name + "','" +  value + "'); ";	
			}
			pool.executeUpdate(sql);
		}
		catch (Exception e) {ErrorReport.print(e, "Replace props: " + sql);}
	}
	// MySQL v8 groups is a new special keywords, so the ` is necessary for that particular rename.

	private boolean tableRename(String oldTable, String newTable) {
		String sql = "RENAME TABLE `" + oldTable + "` TO " + newTable;
		
		try {
			if (tableExists(oldTable))
				pool.executeUpdate(sql);
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, sql); return false;}
	}
	private boolean tableCheckAddColumn(String table, String col, String type, String aft) throws Exception {
		String cmd = "alter table " + table + " add " + col + " " + type ;
		try {
			if (!tableColumnExists(table,col)){
				if (aft!=null && !aft.trim().equals("")) cmd += " after " + aft;
				pool.executeUpdate(cmd);
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
			pool.executeUpdate(cmd);
		}
	}
	private boolean tableDrop(String table) {
		String sql = "Drop table " + table;
		
		try {
			if (tableExists(table))
				pool.executeUpdate(sql);
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, sql); return false;}
	}
	
	private boolean tableExists(String name) throws Exception {
		ResultSet rs = pool.executeQuery("show tables");
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
		ResultSet rs = pool.executeQuery("show columns from " + table);
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
	// class variable
	private DBuser pool=null;
}
