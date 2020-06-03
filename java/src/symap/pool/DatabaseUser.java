package symap.pool;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import backend.Constants;

import java.util.LinkedHashSet;

//import com.mysql.management.driverlaunched.ServerLauncherSocketFactory; CAS506 Java 14 not found

import symap.SyMAP;
import symap.SyMAPConstants;

import util.DatabaseReader;
import util.PropertiesReader;
import util.Utilities;
import util.ErrorReport;

/**
 * Class DatabaseUser holds the constants for the database acquired from the SyMAP.DATABASE_PROPS file 
 * and methods that can be used by children for accessing the database. 
 * Also handles the connection object for the class so a new connection is only created when needed.
 * 
 * CAS504 changed from reading Schema from file to calling new Schema
 * CAS506 removed all dead code for reading schema, and reorganized to try to make sense...
 * 
 * if (ProjectManagerFrameCommon.CHECK_SQLVAR) checkVariables(conn);
 */
public abstract class DatabaseUser implements SyMAPConstants {
	private static final int QUERY_TIMEOUT;

	private static final String DATABASE_URL_PROP = "database";
	private static final String DATABASE_USER_PROP = "databaseUser";
	private static final String DATABASE_PASSWORD_PROP = "databasePassword";
	private static final String DATABASE_DRIVER_PROP = "databaseDriver";
	private static final String DEFAULT_DATABASE_URL[], DEFAULT_DATABASE_USER,
			DEFAULT_DATABASE_PASSWORD, DATABASE_DRIVER;

	static {
		PropertiesReader props = new PropertiesReader(SyMAP.class.getResource("/properties/database.properties"));

		DEFAULT_DATABASE_URL = props.getStrings(DATABASE_URL_PROP);
		DEFAULT_DATABASE_USER = props.getString(DATABASE_USER_PROP);
		DEFAULT_DATABASE_PASSWORD = props.getString(DATABASE_PASSWORD_PROP);
		DATABASE_DRIVER = props.getString(DATABASE_DRIVER_PROP);
		QUERY_TIMEOUT = props.getInt("queryTimeout"); 
	}

	private DatabaseReader dr;

	private Connection connection = null;

	protected DatabaseUser(DatabaseReader dr) {
		this.dr = dr;
	}

	protected DatabaseReader getDatabaseReader() {
		return dr;
	}

	protected PreparedStatement prepareStatement(String sql)
			throws SQLException {
		PreparedStatement r = getConnection().prepareStatement(sql);
		r.setQueryTimeout(QUERY_TIMEOUT);
		return r;
	}

	protected void close() {
		if (connection != null) {
			connection = null;
		}
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
	
	/** executeQuery **/  
    // Based on http://dev.mysql.com/doc/refman/5.1/en/connector-j-usagenotes-troubleshooting.html#qandaitem-21-4-5-3-1-4
    public ResultSet executeQuery(String strQuery) throws SQLException 
    {
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
    // Based on http://dev.mysql.com/doc/refman/5.1/en/connector-j-usagenotes-troubleshooting.html#qandaitem-21-4-5-3-1-4
    public void executeUpdate(String strSQL) throws SQLException 
    {
        Statement stmt = null;
        SQLException savedException = null;
        int retryCount = 3;

        do {
            try {
            	// Get connection if one doesn't already exist and do query.
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
            catch (Exception e)
            {
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
		for (int i = 1; i <= 10; i++)
		{
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
	
	public int getIdx(String strQuery) throws SQLException 
    {
    	int idx = -1;
		ResultSet rs = executeQuery(strQuery);
		if (rs.next())
			idx = rs.getInt("idx");
		rs.close();
		return idx;
    }
	/***************** Extraneous public class methods *******************/
	public boolean isContigs(int projectID, Collection<Integer> contigs) {
		if (contigs == null || contigs.isEmpty())
			return false;
		String nc = " number=";
		StringBuffer query = new StringBuffer("SELECT COUNT(*) FROM ").append("contigs");
		query.append(" WHERE proj_idx=").append(projectID);

		Iterator<Integer> iter = contigs.iterator();

		query.append(" AND (").append(nc).append(iter.next());

		while (iter.hasNext())
			query.append(" OR").append(nc).append(iter.next());

		query.append(")");

		Statement stat = null;
		ResultSet rs = null;
		int count = 0;
		try {
			stat = createStatement();
			rs = stat.executeQuery(query.toString());
			rs.next();
			count = rs.getInt(1);
			closeResultSet(rs);
			closeStatement(stat);
		} catch (SQLException e) {
			closeResultSet(rs);
			rs = null;
			closeStatement(stat);
			stat = null;
		}
		return count > 0;
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
			connection = dr.getConnection();
		return connection;
	}
	private boolean testStatement(Statement s)
	{
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
	 * Method getDatabaseReader is used to acquire a database reader using the driver, pool size, and props
	 * table set in this class. The default value is used for arguments passed in that are null.
	 *
	 * appName is used for distinguishing between two different database readers in order
	 * to make more then one connection to the same database.
	 */
	public static DatabaseReader getDatabaseReader(String appName, String url,
			String user, String password, String altHost) {
		
		url = getURL(url, altHost);
		if (user == null)		user = DEFAULT_DATABASE_USER;
		if (password == null)	password = "";
		
		DatabaseReader d = null;
		
		if (SyMAP.DEBUG) System.out.println("user="+user+" password="+password);
		
		if (url.equals("JAVA_DB_STR") ||  user.equals("DB_CLIENT_USER") || password.equals("DB_CLIENT_PASSWD"))
		{
			Utilities.showErrorMessage("One or more of the database parameters "+
									   "are not set.\nSee the installation "+
									   "instructions for how to set the parameters.");
			Utilities.exit(-1);
		}
		else {
			try {
				d = DatabaseReader.getInstance(appName, url, user, password, DATABASE_DRIVER);
			} catch (ClassNotFoundException e) {
				ErrorReport.print(e,"open database");
			}
		}	
		return d;
	}


	public static DatabaseReader getDatabaseReader(String appName, PropertiesReader props) {
		String url = null, user = null, password = null, driver = null;
		if (props != null) {
			url = props.getString(DATABASE_URL_PROP);
			user = props.getString(DATABASE_USER_PROP);
			password = props.getString(DATABASE_PASSWORD_PROP);
			driver = props.getString(DATABASE_DRIVER_PROP);
		}
		url = getURL(url, null);
		if (user == null)		user = DEFAULT_DATABASE_USER;
		if (password == null)	password = DEFAULT_DATABASE_PASSWORD;
		if (driver == null)		driver = DATABASE_DRIVER;
		
		DatabaseReader d = null;
		try {
			d = DatabaseReader.getInstance(appName, url, user, password, driver);
		} catch (ClassNotFoundException e) {
			ErrorReport.print(e,"open database");
		}
		return d;
	}

	/***********************************************
	 * Creates string for accessing the MySQL database
	 */
	// for MXJ support 
	// CAS506 private static final String MXJ_PATH = "mysql"; // path relative to symap top-level directory
	// CAS506 private static final String MXJ_PORT = "44505"; // prevent conflict with existing daemon
	public static String getDatabaseURL(String hostname, String dbname) {
		boolean useMXJ = false;
		if (Utilities.isStringEmpty(hostname)) {
			useMXJ = true;
			hostname = "localhost";
		}
		if (!Utilities.isStringEmpty(dbname) && !dbname.matches("^[\\w\\d_]+$"))
		{
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
	 * For built-in MySQL - maybe remove; called from DatabaseReader
	 */
	public static void checkDBRunning(String hostname)
	{
		boolean useMXJ = false;
		if (Utilities.isStringEmpty(hostname)) {
			useMXJ = true;
		}
		if (useMXJ && Utilities.isRunning("mysqld"))
		{	
			System.err.println("The built-in MySQL server cannot run because a MySQL server is already running on this computer. " +
						"\nPlease stop that server, or else specify a MySQL host name in the symap.config file.");
			System.exit(0);
		}
	}
	
	/*************************************************************
	 * Create database
	 * CAS504 stopped using scripts/symap.sql and added Schema.java
	 */
	public static boolean createDatabase(String hostname, String dbname, 
			String username, String password) 
	{
		if (!checkHost(hostname,username,password))
			return false;	
		
		Connection conn;
		boolean success = true;
		String dburl =   DatabaseUser.getDatabaseURL(hostname, dbname);
		String hosturl = DatabaseUser.getDatabaseURL(hostname, "");
		
		// In case database exists without tables, i.e. failed earlier
		try { 
			conn = DriverManager.getConnection(dburl,username,  password);
			if (!dbHasTables(conn)) // database exists without schema loaded
			{
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
	
	protected static String setInsert(String sql, String insertString,
			String lvalue, int[] rvalue) {
		return sql.replaceFirst(insertString, toSQL(lvalue, rvalue));
	}

	protected static String setInsert(String sql, String insertString,
			String lvalue, String[] rvalue) {
		return sql.replaceFirst(insertString, toSQL(lvalue, rvalue));
	}

	protected static String setInsert(String sql, String is, String lvalue1,
			String[] rvalue1, String lvalue2, String[] rvalue2) {
		return sql.replaceFirst(is, toSQL(lvalue1, rvalue1, lvalue2, rvalue2));
	}

	protected static String setInt(String sql, int value) {
		// CAS506 depreciated return sql.replaceFirst("[?]", new Integer(value).toString());
		return sql.replaceFirst("[?]", Integer.toString(value));
	}

	protected static String setLong(String sql, long value) {
		// CAS506 depreciated return sql.replaceFirst("[?]", new Long(value).toString());
		return sql.replaceFirst("[?]", Long.toString(value));
	}

	protected static String setString(String sql, String value) {
		return sql.replaceFirst("[?]", "'" + value + "'");
	}

	protected static String setConstant(String sql, String value) {
		return sql.replaceFirst("[?]", value);
	}
	/**************** Private Static *****************************/
	private static String getURL(String url, String altHost) {
		if (url == null) {
			if (altHost == null)
				url = DEFAULT_DATABASE_URL[0];
			else
				try {
					String[] urls = (String[]) DEFAULT_DATABASE_URL.clone();
					for (int i = 0; i < urls.length; i++) { 
						URI uri = new URI(urls[i].substring(5)); // take off "jdbc:"
						
						if (uri.getHost().equals(altHost)) {
							url = urls[i];
							break;
						}
					}
					if (url == null)
						url = getURI(DEFAULT_DATABASE_URL[0], altHost);
				} catch (URISyntaxException e) {
					ErrorReport.print(e,"get URL");
				}
		}
		return url;
	}

	private static String getURI(String uriStr, String altHost) throws URISyntaxException {
		if (uriStr.startsWith("jdbc:")) {
			URI u = new URI(uriStr.substring(5));
			return "jdbc:"+(new URI(u.getScheme(),u.getUserInfo(),altHost,u.getPort(),u.getPath(),u.getQuery(),u.getFragment()));
		}
		URI u = new URI(uriStr);
		return new URI(u.getScheme(),u.getUserInfo(),altHost,u.getPort(),u.getPath(),u.getQuery(),u.getFragment()).toString();
	}
	
	private static boolean dbHasTables(Connection conn) 
	{
		boolean has = true;
		try
		{
      		Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery("show tables");
			has = rs.first();
			stmt.close();
		}
		catch (Exception e) {}
		
		return has;
	}
	
	private static boolean checkHost(String hostname, String username, String password)
	{
		Connection conn;
		boolean success = true;
		
		if (!hostname.equals(""))
		{
			try {
				conn = DriverManager.getConnection(
						DatabaseUser.getDatabaseURL(hostname, ""), username, password);
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
	
	
	
	private static String toSQL(String lvalue, int[] rvalue) {
		if (rvalue == null || rvalue.length == 0)
			return "(0 = 1)";

		rvalue = Utilities.sortCopy(rvalue);

		StringBuffer buf = new StringBuffer("(");
		int last = rvalue[0], i;
		for (i = 1; i < rvalue.length; i++) {
			if (rvalue[i - 1] + 1 < rvalue[i]) {
				if (last == rvalue[i - 1]) {
					buf.append(lvalue).append("=").append(last).append(" OR ");
				} else if (last + 1 == rvalue[i - 1]) {
					buf.append(lvalue).append("=").append(last).append(" OR ")
							.append(lvalue).append("=").append(rvalue[i - 1])
							.append(" OR ");
				} else {
					buf.append("(").append(lvalue).append(">=").append(last)
							.append(" AND ").append(lvalue).append("<=")
							.append(rvalue[i - 1]).append(") OR ");
				}
				last = rvalue[i];
			}
		}
		if (last == rvalue[i - 1]) {
			buf.append(lvalue).append("=").append(last).append(")");
		} else if (last + 1 == rvalue[i - 1]) {
			buf.append(lvalue).append("=").append(last).append(" OR ").append(
					lvalue).append("=").append(rvalue[i - 1]).append(")");
		} else {
			buf.append("(").append(lvalue).append(">=").append(last).append(
					" AND ").append(lvalue).append("<=").append(rvalue[i - 1])
					.append("))");
		}

		return buf.toString();
	}

	private static String toSQL(String lvalue, String[] rvalue) {
		if (rvalue == null || rvalue.length == 0)
			return "(0 = 1)";
		Set<String> rvalues = new LinkedHashSet<String>(rvalue.length);
		for (int i = 0; i < rvalue.length; i++)
			rvalues.add(rvalue[i]);
		Iterator<String> iter = rvalues.iterator();
		lvalue = lvalue + "='";
		StringBuffer buf = new StringBuffer("(").append(lvalue).append(
				iter.next());
		lvalue = "' OR " + lvalue;
		while (iter.hasNext())
			buf.append(lvalue).append(iter.next());
		buf.append("')");
		return buf.toString();
	}

	private static String toSQL(String lvalue1, String[] rvalue1,
			String lvalue2, String[] rvalue2) {
		if (rvalue1 == null || rvalue2 == null || rvalue1.length == 0)
			return "(0 = 1)";
		StringBuffer ret = new StringBuffer("(");
		int i, j;
		for (i = 0; i < rvalue1.length; i++) {
			for (j = 0; j < i; j++)
				if (rvalue1[j].equals(rvalue1[i]))
					break;
			if (j >= i) {
				ret.append("(").append(lvalue1).append("='").append(rvalue1[i])
						.append("' AND ").append(lvalue2).append("='").append(
								rvalue2[i]).append("')");
				if (i + 1 < rvalue1.length)
					ret.append(" OR ");
			}
		}
		return ret.toString();
	}

	private static boolean hasInnodb(Connection conn) throws Exception
	{
		boolean has = false;
		Statement st = conn.createStatement();
		ResultSet rs = st.executeQuery("show engines");
		while (rs.next())
		{
			String engine = rs.getString(1);
			String status = rs.getString(2);
			
			if (engine.equalsIgnoreCase("innodb") && 
					(status.equalsIgnoreCase("yes") || status.equalsIgnoreCase("default")) )
			{
				has = true;
				break;
			}
		}
		if (!has)
		{
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
	 
	 public static byte getBESValue(String str) {
			if (str != null) {
				if (str.equals(R_VALUE_STR))	return R_VALUE;
				if (str.equals(F_VALUE_STR))	return F_VALUE;
			}
			return NORF_VALUE;
	 }

	public static String getBESValueStr(byte bes) {
		if (bes == R_VALUE)	return R_VALUE_STR;
		if (bes == F_VALUE)	return F_VALUE_STR;
		return NORF_VALUE_STR;
	}
	
	/****************************************************************************
	 * Check database settings when mysql database is created
	 */
	private static void checkVariables(Connection con, boolean prt) { // CAS501, update CAS505
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
				System.err.println("For details: see www.agcol.arizona.edu/symap/doc/TroubleShooting.html");
			}
			else System.err.println("  MySQL variables are okay ");
			
			checkExt();
        }
        catch (Exception e) {ErrorReport.print(e, "Getting system variables");	}
	}
	/**************************************************************
	 * Check ext directory when database is created
	 */
	static private void checkExt() {
		try {
			System.err.println("\nCheck external programs " + Constants.getPlatform());
			
			String exDir = Constants.extDir;
			if (!checkDir(exDir)) {
				System.err.println("       Will not be able to run mummer or blat from SyMAP");
				return;
			}
			
			String platform = Constants.getPlatformPath();
			
			String mpath = exDir + "mummer" + platform;
			if (!checkDir(mpath)) {
				System.err.println("   Will not be able to run MUMmer from SyMAP");
				return;
			}
			checkFile(mpath+ "mummer");
			checkFile(mpath+ "mgaps");
			checkFile(mpath+ "nucmer");
			checkFile(mpath+ "promer");
			checkFile(mpath+ "show-coords");
			checkDir(mpath+"aux_bin");
			checkDir(mpath+"scripts");
			
			String bpath = exDir + "blat" + platform;
			if (!checkDir(bpath)) {
				System.err.println("   Will not be able to run blat from SyMAP");
				return;
			}
			checkFile(bpath+ "blat");
			
			String apath = exDir + "muscle" + platform;
			if (!checkDir(apath)) {
				System.err.println("   Will not be able to run Muscle from SyMAP Queries");
				return;
			}
			checkFile(apath+ "muscle");
			
			System.out.println("Check complete\n");
			
		}
		 catch (Exception e) {ErrorReport.print(e, "Checking executables");	}
	}
	static private boolean checkDir(String dirName) {
		try {
			//System.out.println("   Check " + dirName);
			File dir = new File(dirName);

			if (!dir.exists()) {
				System.err.println("***Error - directory does not exists: " + dirName);
				return false;
			}
			if (!dir.isDirectory()) {
				System.err.println("***Error - is not a directory: " + dirName);
				return false;
			}
			if (!dir.canRead()) {
				System.err.println("***Error - directory is not readable: " + dirName);
				return false;
			}
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, "Checking directory " + dirName);	return false;}
	}
	static private boolean checkFile(String fileName) {
		try {
			File file = new File(fileName);

			if (!file.exists()) {
				System.err.println("***Error - file does not exists: " + fileName);
				return false;
			}
			if (!file.isFile()) {
				System.err.println("***Error - is not a file: " + fileName);
				return false;
			}
			if (!file.canExecute()) {
				System.err.println("***Error - file is not executable: " + fileName);
				return false;
			}
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, "Checking file " + fileName);	return false;}
	}
}
