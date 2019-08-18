package util;

import java.util.Map;
import java.util.HashMap;
import java.lang.ref.WeakReference;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * The DatabaseReader handles the establishment of connections to the database.
 *
 * There is only one database reader for every name, url, user, password, and driver and
 * only one connnection for that database reader.
 *
 * @author Austin Shoemaker
 */
public class DatabaseReader {
	private static Map<DatabaseReader,WeakReference<Connection>> databaseReaders = new HashMap<DatabaseReader,WeakReference<Connection>>();

	/**
	 * Method <code>getInstance</code> returns a database reader instance with
	 * the following properties.
	 *
	 * @param name a <code>String</code> value of database reader name
	 * @param url a <code>String</code> value
	 * @param user a <code>String</code> value
	 * @param password a <code>String</code> value of password (optional)
	 * @param driver a <code>String</code> value
	 * @return a <code>DatabaseReader</code> value
	 * @exception ClassNotFoundException if the driver class is not found
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

	/**
	 * Method <code>getInstance</code> returns an instance of a database reader with the given name
	 * and the url, user, and password of <code>dr</code>
	 *
	 * @param name a <code>String</code> value
	 * @param dr a <code>DatabaseReader</code> value
	 * @return a <code>DatabaseReader</code> value
	 */
	public static DatabaseReader getInstance(String name, DatabaseReader dr) {
		DatabaseReader r = null;
		try {
			r = getInstance(name,dr.url,dr.user,dr.password,dr.driver);
		} catch (ClassNotFoundException e) { }
		return r;
	}

	private static Connection getConnection(DatabaseReader dr) throws SQLException {
		Connection con = null;
		synchronized (databaseReaders) {
			WeakReference<Connection> wr = (WeakReference<Connection>)databaseReaders.get(dr);
			if (wr != null) {
				con = (Connection)wr.get();
				if (con == null || con.isClosed()) {
					wr.clear();
					try {
						con = DriverManager.getConnection(dr.url,dr.user,dr.password);
					} catch (SQLException e) {
						System.err.println("SQLException while establishing a connection to "+dr.url+" Error Code: "+e.getErrorCode());
						e.printStackTrace();
						throw e;
					}
					databaseReaders.put(dr,new WeakReference<Connection>(con));
				}
			}
			else throw new SQLException("Connection for "+dr+" has been killed!");
		}
		return con;
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

	private String name, url, user, password, driver;

	private DatabaseReader(String name, String url, String user, String password, String driver) throws ClassNotFoundException {
		this.name = name;
		this.url = url;
		this.user = user;
		if (password != null && password.length() == 0) password = null;
		this.password = password;
		this.driver = driver;
		Class.forName(driver);
	}

	/**
	 * Method <code>getURL</code> returns the url used by this reader
	 *
	 * @return a <code>String</code> value
	 */
	public String getURL() {
		return url;
	}

	/**
	 * Method <code>equals</code> returns true if obj is an instance of DatabaseReader
	 * and has the same name, url, user, password, and driver.
	 *
	 * @param obj an <code>Object</code> value
	 * @return a <code>boolean</code> value
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

	/**
	 * Method <code>getConnection</code> returns a connection.
	 *
	 * @return a <code>Connection</code> value
	 * @exception SQLException if an error occurs
	 */
	public Connection getConnection() throws SQLException {
		return getConnection(this);
	}

	public String toString() {
		return "["+name+" Url = "+url+"]";
	}

	/**
	 * Method <code>kill</code> closes the database reader's connection and
	 * doesn't return a new connection for an equal database reader until 
	 * getInstance is called with the same params as this database reader.
	 *
	 */
	public void kill() {
		close(this,true);
	}

	/**
	 * Method <code>close</code> closes the connection corresponding to this
	 * database reader if available.
	 *
	 */
	public void close() {
		close(this,false);
	}

	public int hashCode() {
		return (name+" "+url+" "+user+" "+password+" "+driver).hashCode();
	}
}
