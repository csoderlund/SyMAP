package symap.pool;

/*****************************************************
 * CAS506 created to provide an organized why for database updates.
 */
import java.sql.ResultSet;

import backend.UpdatePool;
import symap.SyMAP;
import util.ErrorReport;
import util.Utilities;

public class Version {
	int dbVer = SyMAP.DBVER; // 2
	String strVer = SyMAP.DBVERSTR; // db2
	
	public Version (UpdatePool pool) {
		this.pool = pool;
		checkForUpdate();
	}
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
		}
		catch (Exception e) {ErrorReport.print(e, "Error checking database version");}
	}
	private void updateVer1() {
		try {
			if (tableRename("groups", "xgroups"))
				updateProps();
		}
		catch (Exception e) {ErrorReport.print(e, "Error checking database version");}
	}
	/***************************************************************
	 * Run after every version update.
	 * The props table values of DBVER and UPDATE are hardcoded in Schema.
	 * 
	 * if screw up, force update: update props set value=1 where name="DBVER" 
	 */
	private void updateProps() {
		try {
			ResultSet rs = pool.executeQuery("select value from props where name='VERSION'");
			if (rs.next()) {
				String v = rs.getString(1);
				if (v.contentEquals("3.0")) // value before v5.0.6
					replaceProps("VERSION",SyMAP.VERSION);
			}
			replaceProps("UPDATE", Utilities.getDateOnly());
			
			replaceProps("DBVER", SyMAP.DBVERSTR);
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
	/* Below are not - yet **/
	private boolean tableCheckAddColumn(String table, String col, String type, String aft) throws Exception
	{
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
	private void tableCheckDropColumn(String table, String col) throws Exception
	{
		if (tableColumnExists(table,col)) {
			String cmd = "alter table " + table + " drop " + col;
			pool.executeUpdate(cmd);
		}
	}
	
	private void tableCheckRenameColumn(String table, String oldCol, String newCol, String type) throws Exception
	{
		if (tableColumnExists(table,oldCol)) {
			String cmd = "alter table " + table + " change column `" + oldCol + "` " + newCol + " " +  type ;
			pool.executeUpdate(cmd);
		}
	}
	
	private void tableCheckModifyColumn(String table, String col, String type) throws Exception
	{
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
	private boolean tableExists(String name) throws Exception
	{
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
	private boolean tableColumnExists(String table, String column) throws Exception
	{
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
	private String tableGetColDesc(String tbl, String col)
	{
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
	// class variable
	private UpdatePool pool=null;
}
