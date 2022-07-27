package util;

import java.util.Map;
import java.util.HashMap;
import java.lang.ref.WeakReference;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * The DatabaseReader handles the establishment of connections to the database.
 * See symap.pools.DatabaseUser.java 
 *
 * There is only one database reader for every name, url, user, password, and driver and
 * only one connection for that database reader.
 * 
 * CAS506 rearranged into logical order - no changes (removed kill())
 * CAS511 removed deadcode
 */
public class DatabaseReader {
	private static Map<DatabaseReader,WeakReference<Connection>> 
		databaseReaders = new HashMap<DatabaseReader,WeakReference<Connection>>();

	private String name, url, user, password, driver;

	/**
	 * returns a database reader instance with passed in properties.
	 */
	public static DatabaseReader getInstance(String name, String url, String user, String password, String driver) 
	throws ClassNotFoundException {
		synchronized (databaseReaders) {
			
			DatabaseReader dr = new DatabaseReader(name,url,user,password,driver);
			for (DatabaseReader temp : databaseReaders.keySet())
				if (dr.equals(temp)) return temp;
			
			databaseReaders.put(dr,new WeakReference<Connection>(null));
			return dr;
		}
	}

	 // Calls the above getInstance with the dr properties
	public static DatabaseReader getInstance(String name, DatabaseReader dr) {
		DatabaseReader r = null;
		try {
			r = getInstance(name,dr.url,dr.user,dr.password,dr.driver);
		} catch (ClassNotFoundException e) { }
		return r;
	}

	private DatabaseReader(String name, String url, String user, String password, String driver) throws ClassNotFoundException {
		this.name = name;
		this.url = url;
		this.user = user;
		if (password != null && password.length() == 0) password = null;
		this.password = password;
		this.driver = driver;
		Class.forName(driver);
	}
	/********************************************
	 * Get existing connection
	 */
	public Connection getConnection() throws SQLException {
		return getConnection(this);
	}

	private static Connection getConnection(DatabaseReader dr) throws SQLException {
		Connection con = null;
		synchronized (databaseReaders) {
			WeakReference<Connection> wr = (WeakReference<Connection>) databaseReaders.get(dr);
			if (wr != null) {
				con = (Connection)wr.get();
				if (con == null || con.isClosed()) {
					wr.clear();
					try {
						con = DriverManager.getConnection(dr.url,dr.user,dr.password);
					} catch (SQLException e) {
						String msg = "SQLException while establishing a connection to "+dr.url+" Error Code: "+e.getErrorCode();
						ErrorReport.print(e, msg);
						throw e;
					}
					databaseReaders.put(dr,new WeakReference<Connection>(con));
				}
			}
			else throw new SQLException("Connection for "+dr+" has been killed!");
		}
		return con;
	}

	/**
	 * closes the connection corresponding to this database reader if available.
	 */
	public void close() {
		close(this,false);
	}
	private static void close(DatabaseReader dr, boolean forGood) {
		synchronized (databaseReaders) {
			WeakReference<Connection> wr = (WeakReference<Connection>)databaseReaders.get(dr);
			if (wr != null) {
				Connection con = (Connection)wr.get();		
				if (con != null) 
					try { con.close(); } catch (Exception e) { }
					con = null;
					wr.clear();
					if (forGood)
						databaseReaders.remove(dr);
			}
		}
	}


	/**
	 * Method returns true if obj is an instance of DatabaseReader and has the same name, url, user, password, and driver.
	 */
	public boolean equals(Object obj) {
		if (obj instanceof DatabaseReader) {
			DatabaseReader dr = (DatabaseReader)obj;
			return url.equals(dr.url) && 
			(name == null ? dr.name == null : name.equals(dr.name)) &&
			(user == null ? dr.user == null : user.equals(dr.user)) &&
			(password == null ? dr.password == null : password.equals(dr.password)) &&
			(driver == null ? dr.driver == null : driver.equals(dr.driver));
		}
		return false;
	}

	public String getName() {
		return name;
	}
	public String toString() {
		return "["+name+" Url = "+url+"]";
	}
	public int hashCode() {
		return (name+" "+url+" "+user+" "+password+" "+driver).hashCode();
	}
	public String getURL() {
		return url;
	}
}
