package props;

import java.util.Vector;

import java.util.List;
import java.util.Properties;
import java.sql.ResultSet;

import database.DBconn2;
import util.ErrorReport;

/******************************************************
 * Used by Explorer and one call in dotplot; it read proj_props 
 * its in props because no good place for it; needs to be merged with Mproject
 */
public class ProjectPool {
	private DatabaseProperties dbProp;
	private List<ProjectPair> projectPairs;
	private ProjectObj[] projects;
	private DBconn2 dbc2;

	public ProjectPool(DBconn2 dbc2) {
		this.dbc2 =    dbc2;
		projectPairs = new Vector<ProjectPair>();
		dbProp =       new DatabaseProperties();
		reset();
	}
	public synchronized void close() {}
	
	public void reset()  {
		try {
			projectPairs.clear();
			dbProp.load(dbc2,"proj_props",new String[] {"proj_idx"},"name","value");

			Vector<ProjectObj> pvector = new Vector<ProjectObj>();

			ResultSet rs = null;
			int id, p1id, p2id;
			
			rs = dbc2.executeQuery("select idx,type,name from projects");
			while (rs.next()) {
				id = rs.getInt(1);
				pvector.add(new ProjectObj(id,rs.getString(3),rs.getString(2)));
			}
			rs.close();

			if (!pvector.isEmpty()) {
				projects = new ProjectObj[pvector.size()];
				projects = (ProjectObj[])pvector.toArray(projects);
			}
			
			rs = dbc2.executeQuery("SELECT idx,proj1_idx,proj2_idx FROM pairs");
			while (rs.next()) {
				p1id = rs.getInt(2);
				p2id = rs.getInt(3);
				projectPairs.add( new ProjectPair(rs.getInt(1), p1id, p2id, 1) );
			}
			rs.close();
		}
		catch (Exception e) {ErrorReport.print(e, "Project pool reset");}
	}

	public String getProperty(int projectID, String property) {
		return dbProp.getProperty(new Object[]{projectID},property);
	}

	public String getStringProperty(int projectID, String property, String defaultValue) {
		String r = getProperty(projectID,property);
		if (r == null) return defaultValue;
		return r;
	}
	
	public int getIntProperty(int projectID, String property, int defaultValue) {
		return dbProp.getIntProperty(projectID,property,defaultValue);
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
		public void load(DBconn2 dbc2, String table, String[] idColumns, String nameColumn, String valueColumn) throws Exception {
			if (nameColumn == null) nameColumn = DEFAULT_NAME_COLUMN;
			if (valueColumn == null) valueColumn = DEFAULT_VALUE_COLUMN;

			StringBuffer query = new StringBuffer("SELECT ");
			for (int i = 0; i < idColumns.length; i++) query.append(idColumns[i]).append(",");
			query.append(nameColumn).append(",").append(valueColumn);
			query.append(" FROM ").append(table);

			Object[] ids = new Object[idColumns.length];

			ResultSet rs = null;
			try {
				rs = dbc2.executeQuery(query.toString());
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
