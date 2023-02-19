package props;

import java.util.Vector;

import database.DBconn;
import database.DBAbsUser;

import java.util.List;
import java.util.Properties;
import java.sql.Statement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/******************************************************
 * Used by Explorer and one call in dotplot; it read proj_props 
 * its in props because no good place for it; needs to be merged with Mproject
 */
public class ProjectPool extends DBAbsUser {
	private DatabaseProperties dp;
	private List<ProjectPair> projectPairs;
	private ProjectObj[] projects;

	public ProjectPool(DBconn dr) throws SQLException {
		super(dr);
		projectPairs = new Vector<ProjectPair>();
		dp = new DatabaseProperties();
		reset();
	}
	public synchronized void close() {super.close();}
	
	public void reset() throws SQLException {
		projectPairs.clear();
		dp.load(getDBconn(),"proj_props",new String[] {"proj_idx"},"name","value");

		Vector<ProjectObj> pvector = new Vector<ProjectObj>();

		Statement stat = null;
		ResultSet rs = null;
		int id, p1id, p2id;
		try {
			stat = createStatement();
			rs = stat.executeQuery("select idx,type,name from projects");
			while (rs.next()) {
				id = rs.getInt(1);
				pvector.add(new ProjectObj(id,rs.getString(3),rs.getString(2)));
			}
			closeResultSet(rs);

			if (!pvector.isEmpty()) {
				projects = new ProjectObj[pvector.size()];
				projects = (ProjectObj[])pvector.toArray(projects);
			}
			
			rs = stat.executeQuery("SELECT idx,proj1_idx,proj2_idx FROM pairs");
			while (rs.next()) {
				p1id = rs.getInt(2);
				p2id = rs.getInt(3);
				projectPairs.add( new ProjectPair(rs.getInt(1), p1id, p2id, 1) );
			}
			closeResultSet(rs);
			closeStatement(stat);
		}
		catch (SQLException e) {
			closeResultSet(rs);
			closeStatement(stat);
			close();
			e.printStackTrace();
			throw e;
		}
	}

	public String getProperty(int projectID, String property) {
		return dp.getProperty(new Object[]{projectID},property);
	}

	public String getStringProperty(int projectID, String property, String defaultValue) {
		String r = getProperty(projectID,property);
		if (r == null) return defaultValue;
		return r;
	}
	
	public int getIntProperty(int projectID, String property, int defaultValue) {
		return dp.getIntProperty(projectID,property,defaultValue);
	}

	public String getName(int projectID) {
		for (int i = 0; i < projects.length; i++) {
			if (projects[i].id == projectID) return projects[i].name;
		}
		return null;	
	}

	public String getDisplayName(int projectID) {
		return getProperty(projectID,"display_name");
	}

	public ProjectPair getProjectPair(int p1, int p2) {
		return ProjectPair.getProjectPair(projectPairs,p1,p2);
	}
	
	public boolean hasProjectPair(int p1, int p2) {
		return ProjectPair.hasProjectPair(projectPairs,p1,p2);
	}
	
	public boolean isSwapped(int pX, int pY) {
		ProjectPair pair = getProjectPair(pX, pY);
		return (pair == null || pX == pair.getP1());
	}

	protected static class ProjectObj {
		protected int id;
		protected String name;
		protected String type;

		protected ProjectObj(int id, String name, String type) {
			this.id = id;
			if (name == null) this.name = "";
			else this.name = name;
			this.type = type.intern();
		}

		public boolean equals(Object obj) {
			return obj instanceof ProjectObj && ((ProjectObj)obj).id == id;
		}
	}
	/**
	 * Class DatabaseProperties can load properties from a database for quick access.
	 * @see Properties CAS534 moved from a separate file in util; this needs cleaning...
	 */
	@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
	public class DatabaseProperties extends Properties {
		public static final String DEFAULT_NAME_COLUMN = "name";
		public static final String DEFAULT_VALUE_COLUMN = "value";
		public static final String SEPARATOR = ":##:";

		public DatabaseProperties() {
			super();
		}

		// WMN 1/10/12 This method is drastically overcomplicated but the dotplot applet code uses it. 
		public void load(DBconn dr, String table, String[] idColumns, String nameColumn, String valueColumn) throws SQLException {
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

		public Object setProperty(Object[] ids, String name, String value) {
			return setProperty(getKey(ids,name),value);
		}

		public String getProperty(Object[] ids, String name) {
			return getProperty(getKey(ids,name));
		}

		 public int getIntProperty(Object id, String name, int defaultValue) {
			String prop = getProperty(getKey(id,name));
			if (prop != null && prop.trim().length() > 0) { 
				try {
					defaultValue = Integer.parseInt(prop);
				} catch (Exception e) { }
			}
			return defaultValue;
		}

		private String getKey(Object[] ids, String name) {
			StringBuffer buf = new StringBuffer();
			for (int i = 0; i < ids.length; i++) buf.append(ids[i]).append(SEPARATOR);
			buf.append(name);
			return buf.toString();
		}

		private String getKey(Object id, String name) {
			return new StringBuffer().append(id).append(SEPARATOR).append(name).toString();
		}
	}
}
