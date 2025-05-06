package database;

/***********************************************************
 * CAS541 All database operations are performed via this method for SyMAP (copied from TCW)
 * Its called DBconn2 since it replaces DBconn plus a bunch of other files (e.g. Abstract User)
 */
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.HashMap;

import symap.Ext;		// CAS566 moved checkExt from backend.Constants
import util.ErrorReport;

// WARNING: Does not work for nested queries (because it uses one Statement for all queries). 
// To do a query within the read loop of a previous query, use a second DBConn object.

public class DBconn2 {
	private final int maxTries = 10;
	private final boolean TRACE = symap.Globals.DEBUG && symap.Globals.TRACE; // CAS560 add trace
	
	static private String chrSQL = "characterEncoding=utf8"; // utf8mb4
    static private String driver = "com.mysql.cj.jdbc.Driver"; // CAS560 add .cj for new driver
    
    // CAS541 
    // 1. Connections are associated with unique panels or view from CE (where dp, circle, 2d are reused). 
    // 2. Manager is the only one that stays open. The rest close when the window closes.
    // 3. Multiple circles, etc can be opened; they each have there own connection, 
    //			and will have their own suffix number.
    // 4. connMap is for debugging to make sure connections are closed when done with.
    //   The following are the existing labels; they are only meaningful for debugging
    //	    Process			Label				alt			label
    //		ManagerFrame 	Manager
    //		Block Whole		BlocksG-num			Chr			BlocksC-num	(separate window)
    //		DotPlot 		DotplotG-num		from CE		DotplotE-num
    //		Circle			CircleG-num			from CE		CircleE-num
    //		SyMAP2d	CE		SyMAP2dE-num		popup		SyMAP2dP-num
    
    static private HashMap <String, Connection> connMap = new HashMap <String, Connection> ();
    static private int numConn=0;
    static public int getNumConn() { 
    	if (connMap.size()<=1) numConn=0;
    	return numConn++;
    }
    
	private String mUser, mPass, mUrl, mHost, mDBname, pName;
    
	private Connection mConn = null;
	private Statement mStmt = null;
	
	// used for manager dbc2
    public DBconn2(String name, String host, String dbname,  String user, String pass)  {
        pName = name;
    	mHost = host;
        mDBname = dbname;
        mUser = user;
        mPass = pass;
        
        mUrl = createDBstr(host, dbname);  
        mUrl += "&useServerPrepStmts=false&rewriteBatchedStatements=true"; //TCW used, but symap was not
        
        if (renew()) {
        	connMap.put(pName, mConn);
        	prtNumConn();
        }
    }
    // others copy from manager dbc2
 	public DBconn2(String name, DBconn2 db)  { // pname is process
 		pName = name;
 		mUser =   	db.getUserid();
 		mPass =   	db.getPassword();
 		mHost = 	db.getHost();
 		mDBname = 	db.getDBname();
 		mUrl =    	db.getURL(); 	// contains host and database name
 		
 		if (renew()) {
        	connMap.put(pName, mConn);
        	prtNumConn();
        }
 	}
	public void close() {
		try {
			if (mStmt!=null) mStmt.close(); 
			mStmt = null;
			
			connMap.remove(pName, connMap.get(pName));
			
			if (mConn!=null && !mConn.isClosed()) mConn.close();
			mConn = null;
			
			if (TRACE) prt("Close " + pName);
		}
		catch (Exception e) {ErrorReport.print(e, "Closing connection");}
	}
	public void shutdown() {
		try {
			for (String n : connMap.keySet()) {
				Connection c = connMap.get(n);
				if (!c.isClosed()) {
					if (TRACE) prt("Close " + n);
					c.close();
				}
			}
			if (TRACE) {
				Runtime rt = Runtime.getRuntime();
				long total_mem = rt.totalMemory();
				long free_mem =  rt.freeMemory();
				long used_mem = total_mem - free_mem;
				String mem = String.format("%,dk", (int) Math.round(used_mem/1000));
				
				prt(String.format("Memory %-20s\n\n", mem));
			}
		}
		catch (Exception e) {ErrorReport.print("Closing connections");};
	}
	public static void shutConns() { // called from ErrorReport - not tested
		try {
			for (String n : connMap.keySet()) {
				Connection c = connMap.get(n);
				if (!c.isClosed()) {
					prt("Close " + n);
					c.close();
				}
			}
		}
		catch (Exception e) {ErrorReport.print("Closing connections");};
	}
	public void prtNumConn() {
		try {
			if (connMap.size()==1) numConn=0; // Manager only, which has no number
			if (connMap.size()>100) 
				System.err.println("Warning: Too many MySQL connections are open. To be safe, restart SyMAP.");
		
			if (!TRACE) return;
			
			ResultSet rs = executeQuery("show status where variable_name='threads_connected'");
			while (rs.next()) prt(rs.getString(1) + " " + rs.getInt(2));
			rs.close();
			
			for (String n : connMap.keySet()) prt("  Open " + n);
		}
		catch (Exception e) {ErrorReport.print(e, "Closing connection");}
	}
	public String getURL() 			{return  mUrl;}
	public String getUserid() 		{ return mUser; }
	public String getPassword() 	{ return mPass; }
	public String getHost() 		{ return mHost; }
	public String getDBname() 	    { return mDBname; }
	public Connection getDBconn() 	{ return mConn;}
	
	public boolean renew() {
	try {
		Class.forName(driver);
		// mStmt = null;
		//if (mConn != null && !mConn.isClosed()) mConn.close(); CAS541 why closing?
		if (mConn != null && !mConn.isClosed())  return true;
		
		if (mStmt!=null && !mStmt.isClosed()) mStmt.close();
		mStmt = null;
		
		for (int i = 0; i <= maxTries; i++) {
			try {
				mConn = DriverManager.getConnection(mUrl, mUser,mPass);
				break;
			} 
			catch (SQLException e) {
				if (i == maxTries) 
					ErrorReport.die(e, "Unable to connect to " 	+ mUrl + "\nJava Exception: " + e.getMessage());
			}
			Thread.sleep(100);
		}
		return true;
	} catch (Exception e) {ErrorReport.print(e, "Renew connection"); return false;}
	}
	/******************************************
	 * Main query code
	 */
	
	public Statement createStatement() throws Exception {
		return mConn.createStatement();
	}
	public  PreparedStatement prepareStatement(String st) throws SQLException {
		return mConn.prepareStatement(st);
	}
	
	private Statement getStatement() throws Exception {
		if (mStmt == null) mStmt = mConn.createStatement();
		return mStmt;
	}

	public int executeUpdate(String sql) throws Exception {
		if (mConn == null || mConn.isClosed()) renew();
		Statement stmt = getStatement();
		int ret = 0;
		try {
			ret = stmt.executeUpdate(sql);
		}
		catch (Exception e) {
			System.err.println("Query failed, retrying:" + sql);
			mStmt.close();
			mStmt = null;
			renew();
			stmt = getStatement();
			ret = stmt.executeUpdate(sql);
		}
		return ret;
	}
	
	public ResultSet executeQuery(String sql) throws Exception {
		if (mConn == null || mConn.isClosed()) renew();
		
		Statement stmt = getStatement();
		ResultSet rs = null;
		try {
			rs = stmt.executeQuery(sql);
		}
		catch (Exception e) {
			try {
				mStmt.close();
				mStmt = null;
				renew();
				stmt = getStatement();
				rs = stmt.executeQuery(sql);
			}
			catch (SQLException ee) {
				System.err.println("Query failed: " + sql);
				System.err.println(ee.getMessage());
			}
		}
		return rs;
	}
	public int executeCount(String sql)  {
        try {
            ResultSet rs = executeQuery(sql);
            int n = (rs.next()) ? rs.getInt(1) : -1; 
            rs.close();
            return n;
        }
        catch (Exception e) {ErrorReport.print(e, "Getting counts"); return -1;}
	}
	public int executeInteger(String sql)  {return executeCount(sql);}
	public int getIdx(String sql)  {return executeCount(sql);}
	
	public boolean executeBoolean(String sql) throws Exception {
        try {
           int cnt = executeCount(sql); // CAS310 was (sql + "limit 1")
           if (cnt==0) return false;
           else return true;
        }
        catch (Exception e) {ErrorReport.print(e, "Getting boolean"); return false;}
	}
	
	public long executeLong(String sql) throws Exception {
        try {
            ResultSet rs = executeQuery(sql);
            rs.next();
            long n = rs.getLong(1);
            rs.close();
            return n;
        }
        catch (Exception e) {ErrorReport.print(e, "Getting long"); return -1;}
	}
	public float executeFloat(String sql) throws Exception{
        try {
            ResultSet rs = executeQuery(sql);
            rs.next();
            float n = rs.getFloat(1);
            rs.close();
            return n;
        }
        catch (Exception e) {ErrorReport.print(e, "Getting float"); return -1;}
	}
	public String executeString(String sql) throws Exception {
        try {
            ResultSet rs = executeQuery(sql);
            if (!rs.next()) return null;
            String n = rs.getString(1);
            rs.close();
            return n;
        }
        catch (Exception e) {ErrorReport.print(e, "Getting string");return null;}
	}
	
	/************ 
	 * table operations 
	 *******************************************************/
	public boolean tablesExist() throws Exception {
		boolean ret = false;
		ResultSet rs = executeQuery("show tables");
		if (rs!=null && rs.next()) ret = true; // CAS560 was first()
		if (rs!=null) rs.close();
		return ret;
	}
	public boolean tableExists(String name) throws Exception {
		ResultSet rs = executeQuery("show tables");
		while (rs.next()) {
			if (rs.getString(1).equals(name)) {
				rs.close();
				return true;
			}
		}
		if (rs!=null) rs.close();
		return false;
	}
	public boolean tableColumnExists(String table, String column) throws Exception {
		ResultSet rs = executeQuery("show columns from " + table);
		while (rs.next()) {
			if (rs.getString(1).equals(column)) {
				rs.close();
				return true;
			}
		}
		if (rs!=null) rs.close();
		return false;
	}
	public boolean tableDrop(String table) {
		try {
			if (tableExists(table)) {
				executeUpdate ("DROP TABLE " + table);
				return true;
			}
			else return false;
		}
		catch (Exception e) {ErrorReport.print(e, "Cannot drop table " + table); return false;}
	}
	public boolean tableRename(String otab, String ntab) { 
		try {
			if (tableExists(otab)) {
				executeUpdate ("RENAME TABLE " + otab + " to " + ntab);
				return true;
			}
			else return false;
		}
		catch (Exception e) {ErrorReport.print(e, "Cannot rename table " + otab); return false;}
	}
	public void tableDelete(String table) {
	   	try { // finding 'status' fails when 'show tables' succeeds (incorrectly)
	   	   ResultSet rs = executeQuery("show table status like '" + table + "'");
	   	   if (!rs.next()) {
	   		   rs.close();
	   		   return;
	   	   }
	   	   executeUpdate ("TRUNCATE TABLE " + table); // this resets auto-increment
	    }
	    catch(Exception e) {
	    	System.err.println("*** Database is probably corrupted");
	     	System.err.println("*** MySQL finds table but then cannot delete from it.");
		    ErrorReport.die(e,"Fatal error deleting table " + table);
		    System.exit(-1);
	     }
   }
	public boolean tableCheckAddColumn(String table, String col, String type, String aft) throws Exception {
		String cmd = "alter table " + table + " add " + col + " " + type ;
		try {
			if (!tableColumnExists(table,col)) {
				if (aft!=null && !aft.trim().equals(""))
					cmd += " after " + aft;
				executeUpdate(cmd);
				return true;
			}
			return false;
		}
		catch(Exception e){ErrorReport.print(e, "MySQL error: " + cmd);}
		return false;
	}
	public void tableCheckDropColumn(String table, String col) throws Exception {
		if (tableColumnExists(table,col)){
			String cmd = "alter table " + table + " drop " + col;
			executeUpdate(cmd);
		}
	}

	public void tableCheckRenameColumn(String table, String oldCol, String newCol, String type) throws Exception{
		if (tableColumnExists(table,oldCol)){
			String cmd = "alter table " + table + " change column `" + oldCol + "` " + newCol + " " +  type ;
			executeUpdate(cmd);
		}
	}
	
	public void tableCheckModifyColumn(String table, String col, String type) throws Exception{
		if (tableColumnExists(table,col)){
			String curDesc = tableGetColDesc(table,col);
			if (!curDesc.equalsIgnoreCase(type)){
				String cmd = "alter table " + table + " modify " + col + " " + type ;
				executeUpdate(cmd);
			}
		}
		else {PrtWarn("Tried to change column " + table + "." + col + ", which does not exist");}
	}
	
	// Change column to new definition, if it doesn't already match.
	// Note, the definition must be given exactly as seen in the "show table" listing,
	// e.g. mediumint(8) and not just mediumint.
	// If defs don't match, it will re-change the column, wasting time. 
	public void tableCheckChangeColumn(String table, String col, String type) throws Exception {
		if (tableColumnExists(table,col)){
			String curDesc = tableGetColDesc(table,col);
			if (!curDesc.equalsIgnoreCase(type)){
				String cmd = "alter table " + table + " change " + col + " " + col + " " + type ;
				executeUpdate(cmd);
			}
		}
		else {PrtWarn("Tried to change column " + table + "." + col + ", which does not exist");}
	}
	public String tableGetColDesc(String tbl, String col){
		String ret = "";
		try {
			ResultSet rs = executeQuery("describe " + tbl);
			while (rs.next()) {
				String fld = rs.getString("Field");
				String desc = rs.getString("Type");
				if (fld.equalsIgnoreCase(col)){
					ret = desc;
					break;
				}
			}
		}
		catch(Exception e){ErrorReport.print(e, "checking column description for " + tbl + "." + col);
		}
		return ret;
	}
	
	// NOT THREAD SAFE unless each thread is using its own DB connection.
	public Integer lastID() throws Exception {
		String st = "select last_insert_id() as id";
		ResultSet rs = executeQuery(st);
		int i = 0;
		if (rs.next()) i = rs.getInt("id");
		rs.close();
		return i;
	}	
	
    /***************************************************************************/
    public void resetAllIdx() { // CAS535 add, this is easier
		try {
			resetIdx("idx", "annot_key");
			resetIdx("idx", "blocks");
			resetIdx("idx", "pairs"); 
			resetIdx("idx", "projects"); 
			resetIdx("idx", "pseudo_annot");   
			resetIdx("idx", "pseudo_hits");
	        resetIdx("idx", "xgroups");    
		}
		catch (Exception e) {ErrorReport.print(e, "Reset auto-crement for all tables");}
	}
	public void resetIdx(String idx, String table) { // CAS511 add, CAS512 add max
		try {
			int cnt = getIdx("select count(*) from " + table);
			if (cnt==0) {
				executeUpdate("ALTER TABLE " + table + " AUTO_INCREMENT = 1");
			}
			else {
				int max = getIdx("select max(" + idx + ") from " + table);
				executeUpdate("alter table " + table + " AUTO_INCREMENT=" + max);
			}
		}
		catch (Exception e) {ErrorReport.print(e, "Reset auto-crement for " + table);}
	}
    /****************************************************************************
   	 * Check database settings when mysql database is created
   	 */
   	public void checkVariables(boolean prt) { // CAS501, update CAS505; CAS560 GLOBAL->global uc does not work on mac
   		try{	
   			System.err.println("\nCheck MySQL variables");
   			int cntFlag=0;
   			
   			ResultSet rs = executeQuery("show variables like 'max_allowed_packet'");
   			if (rs.next()) {
   				long packet = rs.getLong(2);
   				
   				if (prt) System.err.println("   max_allowed_packet=" + packet);
   				if (packet<4194304) {
   					cntFlag++;
   					System.err.println("   Suggest: set global max_allowed_packet=4194304;  # or greater"); // CAS559 chg msg
   				}
   			}
   			
   			rs = executeQuery("show variables like 'innodb_buffer_pool_size'");
   			if (rs.next()) {
   				long packet = rs.getLong(2);
   				
   				if (prt) System.err.println("   innodb_buffer_pool_size=" + packet);
   				if (packet< 134217728) {
   					cntFlag++;
   					System.err.println("   Suggest: set global innodb_buffer_pool_size=134217728; # or greater");// CAS559 chg msg
   				}
   			}
   			
   			rs = executeQuery("show variables like 'innodb_flush_log_at_trx_commit'");
   			if (rs.next()) {
   				int b = rs.getInt(2);
   				if (prt) System.err.println("   innodb_flush_log_at_trx_commit=" + b);
   				if (b==1) {
   					cntFlag++;
   					System.err.println("   Suggest: set global innodb_flush_log_at_trx_commit=0");
   				}
   			}
   			
   			if (cntFlag>0) {
   				System.err.println("For details: see " + util.Jhtml.TROUBLE_GUIDE_URL);
   			}
   			else System.err.println("  MySQL variables are okay ");
   			
   			Ext.checkExt(); // CAS508 moved checkExt from this file to Constants
   		}
        catch (Exception e) {ErrorReport.print(e, "Getting system variables");	}
   	}
    /************************************************************** 
     * Static methods 
     * ******************************************/
  
   	/*************************************************************
	 * Create database; CAS504 stopped using scripts/symap.sql and added Schema.java
	 */
	public static int createDatabase(String hostname, String dbname, String username, String password) {
		checkHost(hostname,username,password);
		
		int rc=0; // 0 create, -1 fail, 1 exist CAS559 calling routine needs these 3 states
		Connection conn;
		String dburl =   createDBstr(hostname, dbname);
		String hosturl = createDBstr(hostname, "");
		
		try { // In case database exists without tables, i.e. failed earlier
			conn = DriverManager.getConnection(dburl, username,  password);
			
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery("show tables");
			if (rs==null) {
				System.out.println("Create schema '" + dbname + "' (" + dburl + ").");
				new Schema(conn);
			}
			stmt.close(); rs.close(); conn.close(); 
			rc=1;
		}
		catch (SQLException e) { // Create database
			try {
				System.out.println("Creating database '" + dbname + "' (" + dburl + ").");
				conn = DriverManager.getConnection(hosturl, username, password);
				
				Statement stmt = conn.createStatement();
	        	stmt.executeUpdate("CREATE DATABASE " + dbname);
	        	stmt.close(); conn.close();
	        		
	        	conn = DriverManager.getConnection(dburl, username, password);
				new Schema(conn);
				conn.close();	
				rc = 0;
			}
			catch (SQLException e2) {
				ErrorReport.print(e,"Error creating database '" + dbname + "'.");
				rc = -1;
			}
		}
		return rc;
	}
	/***********************************************************
	 * Database exists for the read only viewSymap CAS511 add
	 */
	public static boolean existDatabase(String hostname, String dbname, String username, String password) {
		checkHost(hostname,username,password);
		
		try {	
			String hosturl =  createDBstr(hostname, "");
			Connection conn = DriverManager.getConnection(hosturl, username,  password);
			
			Statement stmt = conn.createStatement();
	    	ResultSet rs = stmt.executeQuery("SHOW DATABASES LIKE '" + dbname + "'");
	    	boolean b = (rs.next()) ? true : false;
	    	stmt.close(); conn.close();
	    	return b;
	    	
	    	/** alternative way - but creates exception
			String dbstr = createDBstr(hostname, dbname);
			Connection con = DriverManager.getConnection(dbstr, username, password);
			con.close();
			return true;
			**/
		}
		catch (Exception e) {ErrorReport.print(e, "Checking for database " + dbname); return false;}
		
	}
	private static void checkHost(String hostname, String username, String password) {
        try {
            Class.forName(driver);
        }
        catch(Exception e) { ErrorReport.die("Unable to find MySQL driver: " + driver);}
	        
		try {
			String hosturl =  createDBstr(hostname, "");
			Connection conn = DriverManager.getConnection(hosturl, username, password);
			
			SQLWarning warn = conn.getWarnings();
            while (warn != null) {
                System.out.println("SQLState: " + warn.getSQLState());
                System.out.println("Message:  " + warn.getMessage());
                System.out.println("Error:   "  + warn.getErrorCode());
                System.out.println("");
                warn = warn.getNextWarning();
            }
			
			hasInnodb(conn);
			conn.close();
		}
		catch(Exception e) { 
			System.err.println("Unable to connect to the mysql database on " + hostname	+ "; are username and password correct?");		
			System.err.println("   Host: " + hostname + "  Username: " + username + "  Password: " + password);
			ErrorReport.die(e, "Cannot connect to mysql");
		}
		
	}
	private static void hasInnodb(Connection conn) throws Exception {
		boolean has = false;
		Statement st = conn.createStatement();
		ResultSet rs = st.executeQuery("show engines");
		while (rs.next()) {
			String engine = rs.getString(1);
			String status = rs.getString(2);
			
			if (engine.equalsIgnoreCase("innodb") && 
					(status.equalsIgnoreCase("yes") || status.equalsIgnoreCase("default")) ) {
				has = true;
				break;
			}
		}
		if (!has) {
			System.err.println("The database does not support Innodb tables. Check the mysql error log problems.");
			System.exit(-1);
		}
		return;
	}	
	
    private static String createDBstr(String host, String db) {
    	if (db==null) {
    		String h = host.replace(";", ""); // in case a ';' is at end of localhost in HOSTs.cfg
    		return "jdbc:mysql://" + h + "?" + chrSQL; 
    	}
    	else {
    		String h = host.replace(";", "");
    		return "jdbc:mysql://" + h + "/" + db + "?" + chrSQL;
    	}
    }
   
    static void PrtWarn(String msg) {System.out.println("Warning: " + msg);}
    static void prt(String msg) {System.out.println("+++ " + msg);}
}
