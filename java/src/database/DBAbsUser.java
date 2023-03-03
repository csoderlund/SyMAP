package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;
import backend.Constants;
import util.Utilities;
import util.ErrorReport;

/**
 * Class DatabaseUser holds the constants for the database acquired from the SyMAP.DATABASE_PROPS file 
 * and methods that can be used by children for accessing the database. 
 * Also handles the connection object for the class so a new connection is only created when needed.
 * 
 * CAS504 changed from reading Schema from file to calling new Schema
 * CAS506 removed all dead code for reading schema, and reorganized to try to make sense...
 * CAS511 removed more dead code
 * CAS534 all pools and new TmpUser extend DBuser
 */
public abstract class DBAbsUser {
	private static final int QUERY_TIMEOUT=0;

	// CAS534 moved from database.properties to here; removed getURL(url,altHost)
	private static final String DEFAULT_DATABASE_URL 	= "JAVA_DB_STR";
	private static final String DEFAULT_DATABASE_USER 	= "DB_CLIENT_USER";
	private static final String DATABASE_DRIVER 		= "com.mysql.jdbc.Driver";

	private DBconn dbc; 
	private Connection connection = null;

	protected DBAbsUser(DBconn dbc) {
		this.dbc = dbc;
	}
	protected DBconn getDBconn() {
		return dbc;
	}
	
	protected void close() {
		if (connection != null) connection = null;
	}
	
	/** executeQuery **/  
    public ResultSet executeQuery(String strQuery) throws SQLException  {
        Statement stmt = null;
        ResultSet rs = null;
        SQLException savedException = null;
        int retryCount = 3;

        do {
            try {
            	// Get connection if one doesn't already exist and do query.
                getConnection();
                stmt = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
                rs = stmt.executeQuery(strQuery);
                return rs;
            } catch (SQLException sqlEx) {
            	savedException = sqlEx;
                
            	// The two SQL states that are 'retry-able' are 08S01 for a communications error, and 40001 for deadlock.
                // Only retry if the error was due to a stale connection, communications problem or deadlock
                String sqlState = sqlEx.getSQLState();
                if ("08S01".equals(sqlState) || "40001".equals(sqlState)) {
	                	try {
	                		if (stmt != null) stmt.close(); // also does rs.close()
		                	stmt = null;
		                	if (connection != null) connection.close(); // cause new connection on retry
		                	connection = null;
		                    retryCount--;
	                	}
	                	catch (SQLException sqlEx2) {
	                		throw sqlEx2; // things are really bad, pass error up the stack
	                	}
                }
                else
                    break;
            }
        } while (retryCount > 0);
        System.out.println(strQuery); // CAS42 1/6/18
        throw savedException; // too many retries, pass error up the stack
    }
    
    /** executeUpdate **/
    public void executeUpdate(String strSQL) throws SQLException  {
        Statement stmt = null;
        SQLException savedException = null;
        int retryCount = 3;

        do {
            try {
                getConnection();
                stmt = connection.createStatement();
                stmt.executeUpdate(strSQL);
                return;
            } 
            catch (SQLException sqlEx) {
            	savedException = sqlEx;
                // The two SQL states that are 'retry-able' are 08S01 for a communications error, and 40001 for deadlock.
                String sqlState = sqlEx.getSQLState();
                if ("08S01".equals(sqlState) || "40001".equals(sqlState))
                		retryCount--;
                else {
                	System.out.println("Could not execute: " + strSQL); // CAS506
                    break;
                }
            }
            catch (Exception e) {
            	System.out.println(strSQL); // CAS42 1/6/18
    			ErrorReport.print(e, "SQL request");
            }
            finally {
	            	try {
	                	if (stmt != null) stmt.close();
	                	stmt = null;
	                	if (connection != null) connection.close(); // cause new connection on retry
	                	connection = null;
	            	}
	            	catch (SQLException sqlEx2) {
	            		throw sqlEx2; // things are really bad, pass error up the stack
	            	}
            }
        } while (retryCount > 0);
        
        throw savedException; // too many retries, pass error up the stack
    }
   
	public Statement createStatement() throws SQLException {
		Statement r = getConnection().createStatement();
		boolean passed = false;
		for (int i = 1; i <= 10; i++) {
			if (!testStatement(r)) {
				try {
					Thread.sleep(1000);
				}
				catch(Exception e) {}
				r = getConnection().createStatement();
			}
			else {
				passed = true;
				break;
			}
		}
		if (!passed) {
			System.out.println("Database connection lost, unable to re-connect. Please re-start SyMAP.");
			System.exit(0);
		}
		r.setQueryTimeout(QUERY_TIMEOUT);
		return r;
	}
	protected PreparedStatement prepareStatement(String sql) throws SQLException {
		PreparedStatement r = getConnection().prepareStatement(sql);
		r.setQueryTimeout(QUERY_TIMEOUT);
		return r;
	}
	protected void closeResultSet(ResultSet rs) {
		if (rs != null) {
			try { rs.close(); }
			catch (Exception e) { }
		}
	}
	protected void closeStatement(Statement st) {
		if (st != null) {
			try { st.close(); }
			catch (Exception e) { }
		}
	}
	/*******************************************************************8
	 * Helper methods
	 */
	public int getIdx(String strQuery) throws SQLException   {
    	int idx = -1;
		ResultSet rs = executeQuery(strQuery);
		if (rs.next())
			idx = rs.getInt(1);
		rs.close();
		return idx;
    }
	public int getInt(String strQuery) { // CAS512 add, same as above
		try {
			int idx = -1;
			ResultSet rs = executeQuery(strQuery);
			if (rs.next())
				idx = rs.getInt(1);
			rs.close();
			return idx;
		}
		catch (Exception e) {ErrorReport.print(e, "Get Int " + strQuery); return 0;}
	}
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
	/*******************************************************************8
	 * CAS511 add for updating schema
	 */
	public boolean tableCheckAddColumn(String table, String col, String type, String aft) throws Exception {
		String cmd = "alter table " + table + " add " + col + " " + type ;
		try {
			if (!tableColumnExists(table,col)){
				if (aft!=null && !aft.trim().equals("")) cmd += " after " + aft;
				executeUpdate(cmd);
				return true;
			}
			return false;
		}
		catch(Exception e) {ErrorReport.print(e, "MySQL error: " + cmd);}
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
	
	/************ Private methods **********************/
	private Connection getConnection() throws SQLException {
		boolean closed = true;
		try {
			if (connection != null)
				closed = connection.isClosed();
		} 
		catch (Exception e) { }
		
		if (closed)
			connection = dbc.getConnection();
		return connection;
	}
	private boolean testStatement(Statement s) {
		boolean passed = true;
		try {
			ResultSet r = s.executeQuery("select 1");
			r.close();
		}
		catch(Exception e) {
			passed = false;
		}
		return passed;
	}

	/*************** XXX Static public methods  ************************/
	/**
	 * Method getDBuser is used to acquire a DBconn using the driver, pool size, and props
	 * table set in this class. The default value is used for arguments passed in that are null.
	 *
	 * appName is used for distinguishing between two different DBconn in order
	 * to make more then one connection to the same database.
	 */
	public static DBconn getDBconn(String appName, String url, String user, String password) {
		if (url==null) 			url = DEFAULT_DATABASE_URL;
		if (user == null)		user = DEFAULT_DATABASE_USER;
		if (password == null)	password = "";
		
		DBconn dbc = null;
		
		if (url.equals("JAVA_DB_STR") ||  user.equals("DB_CLIENT_USER") || password.equals("DB_CLIENT_PASSWD")) {
			Utilities.showErrorMessage("One or more of the database parameters "+
					"are not set.\nSee the installation instructions for how to set the parameters.");
			Utilities.exit(-1);
		}
		else {
			try {
				dbc = DBconn.getInstance(appName, url, user, password, DATABASE_DRIVER);
			} catch (ClassNotFoundException e) {
				ErrorReport.print(e,"open database");
			}
		}	
		return dbc;
	}

	/***********************************************
	 * Creates string for accessing the MySQL database
	 */
	// CAS506 private static final String MXJ_PATH = "mysql"; // path relative to symap top-level directory
	// CAS506 private static final String MXJ_PORT = "44505"; // prevent conflict with existing daemon
	public static String getDatabaseURL(String hostname, String dbname) {
		boolean useMXJ = false;
		if (Utilities.isEmpty(hostname)) {
			useMXJ = true;
			hostname = "localhost";
		}
		if (!Utilities.isEmpty(dbname) && !dbname.matches("^[\\w\\d_]+$")) {
			System.out.println("Invalid database name " + dbname + ": may contain letters, numbers, and underscore\n");
			System.exit(0);
		}
		
		if (useMXJ) return "jdbc:mysql:mxj//" + hostname + ":44505/" + (dbname == null ? "" : dbname) +
				"?server.basedir=mysql&server.initialize-user=true&server.innodb_buffer_pool_size=100M&characterEncoding=utf8";
		
		// CAS506 add characterEncoding
		if (dbname == null) return "jdbc:mysql://" + hostname + "?characterEncoding=utf8";
		else                return "jdbc:mysql://" + hostname + "/" + dbname + "?characterEncoding=utf8";
	
		/**
		return "jdbc:mysql:" + (useMXJ ? "mxj:" : "") 
					+ "//" + hostname + (useMXJ ? ":" + MXJ_PORT : "") 
					+ "/" + (dbname == null ? "" : dbname) 
					+ (useMXJ ? "?server.basedir=" + MXJ_PATH + "&server.initialize-user=true&server.innodb_buffer_pool_size=100M" : ""); 	// for mxj module
		**/
	}
	
	/**********************************************
	 * CAS534 changed, was for old built-in database
	 */
	public static void checkDBRunning(String hostname){
		if (!Utilities.isRunning("mysqld")) {	
			System.err.println("**** There is no 'mysqld' running on  " + hostname);		
		}
	}
	/***********************************************************
	 * Database exists for the read only viewSymap CAS511 add
	 */
	public static boolean existDatabase(String hostname, String dbname, String username, String password) {
		if (!checkHost(hostname,username,password))
			return false;
		try {
			String hosturl = DBAbsUser.getDatabaseURL(hostname, "");
			Connection conn = DriverManager.getConnection(hosturl, username,  password);
			
			Statement stmt = conn.createStatement();
	    	ResultSet rs = stmt.executeQuery("SHOW DATABASES LIKE '" + dbname + "'");
	    	boolean b = (rs.next()) ? true : false;
	    	stmt.close();
	    	conn.close();
	    	return b;
		}
		catch (Exception e) {ErrorReport.print(e, "Checking for database " + dbname); return false;}
		
	}
	/*************************************************************
	 * Create database; CAS504 stopped using scripts/symap.sql and added Schema.java
	 */
	public static boolean createDatabase(String hostname, String dbname, String username, String password) {
		if (!checkHost(hostname,username,password))
			return false;	
		
		Connection conn;
		boolean success = true;
		String dburl =   DBAbsUser.getDatabaseURL(hostname, dbname);
		String hosturl = DBAbsUser.getDatabaseURL(hostname, "");
		
		// In case database exists without tables, i.e. failed earlier
		try { 
			conn = DriverManager.getConnection(dburl,username,  password);
			if (!dbHasTables(conn)) {// database exists without schema loaded
				System.out.println("Create schema '" + dbname + "' (" + dburl + ").");
				//DatabaseUser.loadSQLFile(conn, sqlFile); was loading from /scripts/symap.sql
				new Schema(conn);
				checkVariables(conn, false);
			}
			conn.close();
		}
		catch (SQLException e) { // Create database
			try {
				System.out.println("Creating database '" + dbname + "' (" + dburl + ").");
				conn = DriverManager.getConnection(hosturl, username, password);
				
				Statement stmt = conn.createStatement();
	        	stmt.executeUpdate("CREATE DATABASE " + dbname);
	        	stmt.close();
	        	conn.close();
	        		
	        	conn = DriverManager.getConnection(dburl, username, password);
				new Schema(conn);
				
				checkVariables(conn, false);
				conn.close();
			}
			catch (SQLException e2) {
				ErrorReport.print(e,"Error creating database '" + dbname + "'.");
				success = false;
			}
		}
		return success;
	}
	
	protected static String setInt(String sql, int value) {
		// CAS506 depreciated return sql.replaceFirst("[?]", new Integer(value).toString());
		return sql.replaceFirst("[?]", Integer.toString(value));
	}

	protected static String setString(String sql, String value) {
		return sql.replaceFirst("[?]", "'" + value + "'");
	}

	/**************** Private Static *****************************/
	
	private static boolean dbHasTables(Connection conn)  {
		boolean has = true;
		try {
      		Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery("show tables");
			has = rs.first();
			stmt.close();
		}
		catch (Exception e) {}
		
		return has;
	}
	
	private static boolean checkHost(String hostname, String username, String password) {
		Connection conn;
		boolean success = true;
		
		if (!hostname.equals("")) {
			try {
				conn = DriverManager.getConnection(
						DBAbsUser.getDatabaseURL(hostname, ""), username, password);
				success = hasInnodb(conn);
				conn.close();
			}
			catch(Exception e) { 
				System.err.println("Unable to connect to the mysql database on " + hostname	+ "; are username and password correct?");		
				ErrorReport.print(e, "Cannot connect to mysql");
				success = false;
			}
		}
		if (!success) {
			System.exit(0);
		}
		return success;
	}
	
	private static boolean hasInnodb(Connection conn) throws Exception {
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
		}
		return has;
	}	
	/*********** Extraneous methods **************/
	 public static void shutdown() {
		 	
	    	/** CAS506 - not found error with Java 14
		    	File f = new File(MXJ_PATH + "/data/MysqldResource.pid");
		    	if (f.exists()) // server is running
		    		ServerLauncherSocketFactory.shutdown(new File(MXJ_PATH), null);
		    **/
	 }
	/****************************************************************************
	 * Check database settings when mysql database is created
	 */
	public static void checkVariables(Connection con, boolean prt) { // CAS501, update CAS505
		try{	
			System.err.println("\nCheck MySQL variables");
			int cntFlag=0;
			Statement st = con.createStatement();
			
			ResultSet rs = st.executeQuery("show variables like 'max_allowed_packet'");
			if (rs.next()) {
				long packet = rs.getLong(2);
				
				if (prt) System.err.println("   max_allowed_packet=" + packet);
				if (packet<4194304) {
					cntFlag++;
					System.err.println("   Suggest: SET GLOBAL max_allowed_packet=1073741824;");
				}
			}
			
			rs = st.executeQuery("show variables like 'innodb_buffer_pool_size'");
			if (rs.next()) {
				long packet = rs.getLong(2);
				
				if (prt) System.err.println("   innodb_buffer_pool_size=" + packet);
				if (packet< 134217728) {
					cntFlag++;
					System.err.println("   Suggest: set GLOBAL innodb_buffer_pool_size=1073741824;");
				}
			}
			
			rs = st.executeQuery("show variables like 'innodb_flush_log_at_trx_commit'");
			if (rs.next()) {
				int b = rs.getInt(2);
				if (prt) System.err.println("   innodb_flush_log_at_trx_commit=" + b);
				if (b==1) {
					cntFlag++;
					System.err.println("   Suggest: set GLOBAL innodb_flush_log_at_trx_commit=0");
				}
			}
			
			if (cntFlag>0) {
				System.err.println("For details: see " + util.Jhtml.TROUBLE_GUIDE_URL);
			}
			else System.err.println("  MySQL variables are okay ");
			
			Constants.checkExt(); // CAS508 moved checkExt from this file to Constants
        }
        catch (Exception e) {ErrorReport.print(e, "Getting system variables");	}
	}
}
