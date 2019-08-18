package util;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.util.Properties;

/**
 * Class <code>DatabaseProperties</code> can load properties from a database for
 * quick access.
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
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
	 * Creates a new <code>DatabaseProperties</code> instance loading in the properties from the database.
	 *
	 * If the load fails (i.e. load throws an SQLException), than a stack trace is printed out but the
	 * exception is not thrown.
	 *
	 * @param dr a <code>DatabaseReader</code> value
	 * @param table a <code>String</code> value
	 * @param idColumns a <code>String[]</code> value
	 * @param nameColumn a <code>String</code> value
	 * @param valueColumn a <code>String</code> value
	 * @see #load(DatabaseReader,String,String[],String,String)
	 */
	public DatabaseProperties(DatabaseReader dr, String table, String[] idColumns, String nameColumn, String valueColumn) {
		try {
			load(dr,table,idColumns,nameColumn,valueColumn);
		}
		catch (SQLException e) {
			e.printStackTrace();
		}
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
	 * Method <code>store</code> attempts to store the data into the database.
	 * The DatabaseReader must have insert and delete privledges.  Also,
	 * id columns are treated as integers if they can successfully be parsed.
	 * Otherwise they are treated as strings.
	 *
	 * @param dr a <code>DatabaseReader</code> value
	 * @param table a <code>String</code> value
	 * @param idColumns a <code>String[]</code> value
	 * @param nameColumn a <code>String</code> value, default 'name' used if null
	 * @param valueColumn a <code>String</code> value, default 'value' used if null
	 * @exception SQLException if an error occurs
	 */
	public void store(DatabaseReader dr, String table, String[] idColumns, String nameColumn, String valueColumn) throws SQLException {
		java.util.Enumeration enumNames = propertyNames(); // mdb changed 3/23/07 #109
		if (!enumNames.hasMoreElements()) return ; // mdb changed 3/23/07 #109

		if (nameColumn == null) nameColumn   = DEFAULT_NAME_COLUMN;
		if (valueColumn == null) valueColumn = DEFAULT_VALUE_COLUMN;

		Connection con = null;
		PreparedStatement stat = null;
		ResultSet rs = null;

		String query = "REPLACE "+table+" (";
		if (idColumns.length > 0) {
			query += idColumns[0];
			for (int i = 1; i < idColumns.length; ++i) query += ","+idColumns[i];
			query += ",";
		}
		query += nameColumn+","+valueColumn+") VALUES (";
		if (idColumns.length > 0) {
			query += "?";
			for (int i = 1; i < idColumns.length; ++i) query += ",?";
			query += ",";
		}
		query += "?,?)";

		try {
			con = dr.getConnection();
			stat = con.prepareStatement(query);

			while (enumNames.hasMoreElements()) { // mdb changed 3/23/07 #109
				String key = (String)enumNames.nextElement(); // mdb changed 3/23/07 #109
				String[] vals = key.split(SEPARATOR);
				for (int i = 0; i < vals.length-1; i++) {
					try {
						int j = Integer.parseInt(vals[i]);
						stat.setInt(i+1,j);
					}
					catch (NumberFormatException n) {
						stat.setString(i+1,vals[i]);
					}
				}
				stat.setString(vals.length,vals[vals.length-1]);
				stat.setString(vals.length+1,getProperty(key));
				stat.addBatch();
			}
			stat.executeBatch();
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
	 * Method <code>setProperty</code> is a convenience method for single id property tables
	 *
	 * Will call setProperty(Object[],String,String) if id is an instance of an Object[]
	 *
	 * @param id an <code>Object</code> value
	 * @param name a <code>String</code> value
	 * @param value a <code>Strin</code> value
	 * @return an <code>Object</code> value
	 */
	public Object setProperty(Object id, String name, String value) {
		if (id instanceof Object[]) return setProperty((Object[])id,name,value);
		return setProperty(new Object[] {id},name,value);
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
	 * Method <code>getProperty</code>
	 *
	 * @param ids an <code>Object[]</code> value
	 * @param name a <code>String</code> value
	 * @param defaultValue a <code>String</code> value
	 * @return a <code>String</code> value
	 * @see #getKey(Object[],String)
	 * @see Properties#getProperty(String,String)
	 */
	public String getProperty(Object[] ids, String name, String defaultValue) {
		return getProperty(getKey(ids,name),defaultValue);
	}

	/**
	 * Method <code>getIntProperty</code> is a convenience method for converting
	 * a property into an integer.  If the property doesn't exist or can't be
	 * converted to an integer, <code>defaultValue</code> is used.
	 *
	 * @param ids an <code>Object[]</code> value 
	 * @param name a <code>String</code> value 
	 * @param defaultValue an <code>int</code> value
	 * @return an <code>int</code> value
	 * @see #getProperty(Object[],String)
	 * @see Integer#parseInt(String)
	 */
	public int getIntProperty(Object[] ids, String name, int defaultValue) {
		String prop = getProperty(ids,name);
		if (prop != null && prop.trim().length() > 0) { 
			try {
				defaultValue = Integer.parseInt(prop);
			} catch (Exception e) { }
		}
		return defaultValue;
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
	 * Method <code>getBooleanProperty</code> is a convenience method for a single id properties table.
	 *
	 * First sees if it's an integer by attempting to parse it.  If successfull, any non-zero value is
	 * determined to be true.  Otherwise, returns true if the value is equal to "true" ignoring case.
	 *
	 * @param id an <code>Object</code> value
	 * @param name a <code>String</code> value
	 * @param defaultValue an <code>boolean</code> value
	 * @return an <code>boolean</code> value
	 * @see #getKey(Object,String)
	 * @see Properties#getProperty(java.lang.String)
	 * @see Boolean#valueOf(java.lang.String)
	 */
	public boolean getBooleanProperty(Object id, String name, boolean defaultValue) {
		String prop = getProperty(getKey(id,name));
		if (prop != null && prop.trim().length() > 0) { 
			try {
				int i = Integer.parseInt(prop);
				defaultValue = i != 0;
			} catch (Exception e) { 
				defaultValue = Boolean.valueOf(prop).booleanValue();
			}
		}
		return defaultValue;
	}

	/**
	 * Method <code>getBooleanProperty</code> is a convenience method for converting
	 * a property into an boolean.  If the property doesn't exist or can't be
	 * converted to an integer, <code>defaultValue</code> is used.
	 *
	 * First sees if it's an integer by attempting to parse it.  If successfull, any non-zero value is
	 * determined to be true.  Otherwise, returns true if the value is equal to "true" ignoring case.
	 *
	 * @param ids an <code>Object[]</code> value 
	 * @param name a <code>String</code> value 
	 * @param defaultValue an <code>boolean</code> value
	 * @return an <code>boolean</code> value
	 * @see #getProperty(Object[],String)
	 * @see Integer#parseInt(String)
	 * @see Boolean#valueOf(java.lang.String)
	 */
	public boolean getBooleanProperty(Object[] ids, String name, boolean defaultValue) {
		String prop = getProperty(ids,name);
		if (prop != null && prop.trim().length() > 0) { 
			try {
				int i = Integer.parseInt(prop);
				defaultValue = i != 0;
			} catch (Exception e) { 
				defaultValue = Boolean.valueOf(prop).booleanValue();
			}
		}
		return defaultValue;
	}

	public Number getNumberProperty(Object[] ids, String name, Number defaultValue) {
		String prop = getProperty(ids,name);
		if (prop != null && prop.trim().length() > 0) {
			try {
				Class c = defaultValue.getClass();
				java.lang.reflect.Constructor con = c.getConstructor(new Class[] {String.class});
				Number n = (Number)con.newInstance(new Object[] {prop});
				defaultValue = n;

			} catch (Exception e) { }
		}
		return defaultValue;
	}

	public Number getNumberProperty(Object id, String name, Number defaultValue) {
		return getNumberProperty(new Object[] {id},name,defaultValue);
	}

	/**
	 * Method <code>getKey</code> returns the key used that corrisponds to ids and name
	 *
	 * @param ids an <code>Object[]</code> value
	 * @param name a <code>String</code> value
	 * @return a <code>String</code> value
	 */
	public static String getKey(Object[] ids, String name) {
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
	public static String getKey(Object id, String name) {
		return new StringBuffer().append(id).append(SEPARATOR).append(name).toString();
	}
}
