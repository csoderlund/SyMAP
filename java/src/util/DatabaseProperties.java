package util;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Class <code>DatabaseProperties</code> can load properties from a database for
 * quick access.
 
 * @see Properties
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class DatabaseProperties extends Properties {
	public static final String DEFAULT_NAME_COLUMN = "name";
	public static final String DEFAULT_VALUE_COLUMN = "value";
	public static final String SEPARATOR = ":##:";

	/**
	 * Creates a new <code>DatabaseProperties</code> instance with no properties.
	 */
	public DatabaseProperties() {
		super();
	}

	/**
	 * Method <code>load</code> loads in the properties.
	 *
	 * @param dr a <code>DatabaseReader</code> value of the DatabaseReader to use to acquire a connection to the database
	 * @param table a <code>String</code> value of the name of the properties table
	 * @param idColumns a <code>String[]</code> value of the name of the id columns
	 * @param nameColumn a <code>String</code> value of the name of the name column or the default 'name' if null
	 * @param valueColumn a <code>String</code> value of the name of the value column or the default value of 'value' if null
	 * @exception SQLException if an error occurs
	 */
	// WMN 1/10/12 This method is drastically overcomplicated but the dotplot applet code uses it. 
	public void load(DatabaseReader dr, String table, String[] idColumns, String nameColumn, String valueColumn) throws SQLException {
		if (nameColumn == null) nameColumn = DEFAULT_NAME_COLUMN;
		if (valueColumn == null) valueColumn = DEFAULT_VALUE_COLUMN;

		StringBuffer query = new StringBuffer("SELECT ");
		for (int i = 0; i < idColumns.length; i++) query.append(idColumns[i]).append(",");
		query.append(nameColumn).append(",").append(valueColumn);
		query.append(" FROM ").append(table);

		Object[] ids = new Object[idColumns.length];

		Connection con = null;
		Statement stat = null;
		ResultSet rs = null;
		try {
			con = dr.getConnection();
			stat = con.createStatement();
			rs = stat.executeQuery(query.toString());
			while (rs.next()) {
				for (int i = 0; i < idColumns.length; i++) ids[i] = rs.getObject(idColumns[i]);
				setProperty(ids,rs.getString(nameColumn),rs.getString(valueColumn));
			}
		}
		finally {
			if (rs != null) {
				try {
					rs.close();
				} catch (Exception e) { }
				rs = null;
			}
			if (stat != null) {
				try {
					stat.close();
				} catch (Exception e) { }
				stat = null;
			}
			con = null;
		}
	}

	/**
	 * Method <code>setProperty</code> sets the property
	 *
	 * @param ids an <code>Object[]</code> value
	 * @param name a <code>String</code> value
	 * @param value a <code>String</code> value
	 * @return an <code>Object</code> value of the previous value of the resultant key or null if it didn't exist
	 * @see #getKey(Object[],String)
	 * @see Properties#setProperty(String,String)
	 */
	public Object setProperty(Object[] ids, String name, String value) {
		return setProperty(getKey(ids,name),value);
	}

	/**
	 * Method <code>getProperty</code> returns the property or null if it doesn't exist
	 *
	 * @param ids an <code>Object[]</code> value of the ids given in the same order as on loading
	 * @param name a <code>String</code> value of the name
	 * @return a <code>String</code> value
	 * @see #getKey(Object[],String)
	 * @see Properties#getProperty(String)
	 */
	public String getProperty(Object[] ids, String name) {
		return getProperty(getKey(ids,name));
	}


	/**
	 * Method <code>getProperty</code> is a convenience method for a single id properties table
	 *
	 * @param id an <code>Object</code> value
	 * @param name a <code>String</code> value
	 * @param defaultValue a <code>String</code> value
	 * @return a <code>String</code> value
	 * @see #getKey(Object,String)
	 * @see Properties#getProperty(String,String)
	 */
	public String getProperty(Object id, String name, String defaultValue) {
		return getProperty(getKey(id,name),defaultValue);
	}

	/**
	 * Method <code>getIntProperty</code> is a convenience method for a single id properties table
	 *
	 * @param id an <code>Object</code> value
	 * @param name a <code>String</code> value
	 * @param defaultValue an <code>int</code> value
	 * @return an <code>int</code> value
	 * @see #getKey(Object,String)
	 * @see Properties#getProperty(java.lang.String)
	 * @see Integer#parseInt(java.lang.String)
	 */
	 public int getIntProperty(Object id, String name, int defaultValue) {
		String prop = getProperty(getKey(id,name));
		if (prop != null && prop.trim().length() > 0) { 
			try {
				defaultValue = Integer.parseInt(prop);
			} catch (Exception e) { }
		}
		return defaultValue;
	}


	/**
	 * Method <code>getKey</code> returns the key used that corrisponds to ids and name
	 *
	 * @param ids an <code>Object[]</code> value
	 * @param name a <code>String</code> value
	 * @return a <code>String</code> value
	 */
	private static String getKey(Object[] ids, String name) {
		StringBuffer buf = new StringBuffer();
		for (int i = 0; i < ids.length; i++) buf.append(ids[i]).append(SEPARATOR);
		buf.append(name);
		return buf.toString();
	}

	/**
	 * Method <code>getKey</code> is a convenience method for single id column properties tables.
	 *
	 * @param id an <code>Object</code> value
	 * @param name a <code>String</code> value
	 * @return a <code>String</code> value
	 */
	private static String getKey(Object id, String name) {
		return new StringBuffer().append(id).append(SEPARATOR).append(name).toString();
	}
}
