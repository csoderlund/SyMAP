package props;

import java.util.Vector;

import java.util.Properties;
import java.sql.ResultSet;

import database.DBconn2;
import util.ErrorReport;

/******************************************************
 * Used by Explorer and one call in dotplot; it read proj_props, plus check for pairs 
 * CAS552 move ProjectPair class to here; removed lots of unused stuff; renamed from ProjectPool
 */
public class PropsDB {
	private DatabaseProperties dbProp;
	private Vector <ProjectPair> projectPairs;
	private ProjectObj[] projects;
	private DBconn2 dbc2;

	public PropsDB(DBconn2 dbc2) { // DrawingPanel and Dotplot.Data
		this.dbc2 =    dbc2;
		projectPairs = new Vector<ProjectPair>();
		dbProp =       new DatabaseProperties();
		load();
	}
	
	private void load()  {
		try {
			projectPairs.clear();
			dbProp.load(dbc2);

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
				projectPairs.add( new ProjectPair(rs.getInt(1), p1id, p2id) );
			}
			
			for (ProjectPair pp : projectPairs) {// CAS552 add for Sequence popup
				String x = dbc2.executeString("select value from pair_props where name='algo1' and pair_idx=" + pp.pid);
				pp.isAlgo1 = (x!=null) ? x.equals("1") : true; 
			}
			rs.close();
		}
		catch (Exception e) {ErrorReport.print(e, "Project pool reset");}
	}

	public boolean isAlgo1(int pp1, int pp2) {
		if (hasProjectPair(pp1, pp2)) return getProjectPair(pp1, pp2).isAlgo1;
		else return getProjectPair(pp2, pp1).isAlgo1;
	}
	public String getProperty(int projectID, String property) { // Sequence
		return dbProp.getProperty((Object) projectID,property);
	}

	public String getName(int projectID) { // Sequence
		for (int i = 0; i < projects.length; i++) {
			if (projects[i].id == projectID) return projects[i].name;
		}
		return null;	
	}

	public String getDisplayName(int projectID) { // Sequence
		return getProperty(projectID,"display_name");
	}

	public boolean hasProjectPair(int p1, int p2) { // MapperPool
		for (ProjectPair pp : projectPairs) {
			if (pp.p1 == p1 && pp.p2 == p2)
				return true;
		}
		return false;
	}
	
	public boolean isSwapped(int pX, int pY) { // Data
		ProjectPair pair = getProjectPair(pX, pY);
		return (pair == null || pX == pair.getP1());
	}
	private ProjectPair getProjectPair(int p1, int p2) {
		for (ProjectPair pp : projectPairs) {
			if ((pp.getP1() == p1 && pp.getP2() == p2)
					|| (pp.getP1() == p2 && pp.getP2() == p1))
				return pp;
		}
		return null;
	}
	/////////////////////////////////////////////////////////////////////
	protected class ProjectObj {
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
	private class ProjectPair {
		private int p1, p2;
		private int pid;
		private boolean isAlgo1=false;

		protected ProjectPair(int pid, int p1, int p2) {
			this.pid = pid;
			this.p1  = p1;
			this.p2  = p2;
		}

		protected int getP1() { return p1; }
		protected int getP2() { return p2; }

		public String toString() { return "[ProjectPair: "+pid+" ("+p1+","+p2+")]"; }
	}

	/**
	 * Class DatabaseProperties can load properties from a database for quick access.
	 * @see Properties CAS534 moved from a separate file in util; this needs cleaning...
	 */
	@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
	public class DatabaseProperties extends Properties {
		public static final String SEPARATOR = ":##:";

		private DatabaseProperties() {
			super();
		}
		private void load(DBconn2 dbc2) throws Exception {
			ResultSet rs = null;
			try {
				rs = dbc2.executeQuery("SELECT proj_idx, name, value from proj_props");
				while (rs.next()) {
					setProperty(rs.getObject(1),rs.getString(2),rs.getString(3));
				}
			}
			finally {
				if (rs != null) {
					try { rs.close();
					} catch (Exception e) { }
					rs = null;
				}
			}
		}

		protected String getProperty(Object id, String name) {
			return getProperty(getKey(id,name));
		}
		private Object setProperty(Object ids, String name, String value) {
			return setProperty(getKey(ids,name),value);
		}
		private String getKey(Object id, String name) {
			return new StringBuffer().append(id).append(SEPARATOR).append(name).toString();
		}
	}
}
