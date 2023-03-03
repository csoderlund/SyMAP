package database;

import java.util.HashMap;
import java.lang.ref.WeakReference;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import util.ErrorReport;

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
public class DBconn {
	private static HashMap<DBconn,WeakReference<Connection>> dbReaders = new HashMap<DBconn,WeakReference<Connection>>();

	private String name, url, user, password, driver;

	public static DBconn getInstance(String name, DBconn dr) {
		DBconn dbc = null;
		try {
			dbc = getInstance(name,dr.url,dr.user,dr.password,dr.driver);
		} catch (ClassNotFoundException e) { }
		return dbc;
	}
		
	public static DBconn getInstance(String name, String url, String user, String password, String driver) 
	throws ClassNotFoundException {
		synchronized (dbReaders) {
			DBconn dbc = new DBconn(name,url,user,password,driver);
			for (DBconn temp : dbReaders.keySet()) {
				if (dbc.equals(temp)) {
					dbc.close(); // CAS534
					return temp;
				}
			}
			dbReaders.put(dbc,new WeakReference<Connection>(null));
			return dbc;
		}
	}

	private DBconn(String name, String url, String user, String password, String driver) throws ClassNotFoundException {
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

	private static Connection getConnection(DBconn dbc) throws SQLException {
		Connection con = null;
		synchronized (dbReaders) {
			WeakReference<Connection> wr = (WeakReference<Connection>) dbReaders.get(dbc);
			if (wr != null) {
				con = (Connection)wr.get();
				if (con == null || con.isClosed()) {
					wr.clear();
					try {
						con = DriverManager.getConnection(dbc.url,dbc.user,dbc.password);
					} catch (SQLException e) {
						String msg = "SQLException while establishing a connection to "+
									dbc.url+"\nError Code: "+e.getErrorCode();
						ErrorReport.die(e, msg); // CAS511 change from print to die
						throw e;
					}
					dbReaders.put(dbc,new WeakReference<Connection>(con));
				}
			}
			else throw new SQLException("Connection for "+dbc+" has been killed!");
		}
		return con;
	}

	public void close() {
		close(this,false);
	}
	private static void close(DBconn dr, boolean forGood) {
		synchronized (dbReaders) {
			WeakReference<Connection> wr = (WeakReference<Connection>)dbReaders.get(dr);
			if (wr != null) {
				Connection con = (Connection)wr.get();		
				if (con != null) 
					try { con.close(); } catch (Exception e) { }
					con = null;
					wr.clear();
					if (forGood)
						dbReaders.remove(dr);
			}
		}
	}

	public boolean equals(Object obj) {
		if (obj instanceof DBconn) {
			DBconn dr = (DBconn)obj;
			return url.equals(dr.url) && 
			(name == null ? dr.name == null : name.equals(dr.name)) &&
			(user == null ? dr.user == null : user.equals(dr.user)) &&
			(password == null ? dr.password == null : password.equals(dr.password)) &&
			(driver == null ? dr.driver == null : driver.equals(dr.driver));
		}
		return false;
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
