package props;

import java.util.Vector;

import java.util.Properties;
import java.sql.ResultSet;

import database.DBconn2;
import util.ErrorReport;

/******************************************************
 * Used by Explorer and one call in dotplot; it read proj_props, plus check for pairs 
 * dbProp contains everything in the proj_props DB table
 * Vector of pairs, Array of projects	
 */
public class PropsDB {
	private DatabaseProperties dbProjProp;
	private Vector <Pair> pairs;
	private Project[] projects;
	private DBconn2 dbc2;

	public PropsDB(DBconn2 dbc2) { // DrawingPanel and Dotplot.Data
		this.dbc2 =    dbc2;
		pairs = new Vector<Pair>();
		dbProjProp =   new DatabaseProperties();
		load();
	}
	
	private void load()  {
		try {
			pairs.clear();
			
		// Projects
			dbProjProp.loadAllProps(dbc2);

			Vector<Project> pvector = new Vector<Project>();

			ResultSet rs = null;
			int id, p1id, p2id;
			
			rs = dbc2.executeQuery("select idx,type,name from projects");
			while (rs.next()) {
				id = rs.getInt(1);
				pvector.add(new Project(id,rs.getString(3),rs.getString(2)));
			}
			rs.close();

			if (!pvector.isEmpty()) {
				projects = new Project[pvector.size()];
				projects = (Project[])pvector.toArray(projects);
			}
		// Pairs	
			rs = dbc2.executeQuery("SELECT idx,proj1_idx,proj2_idx FROM pairs");
			while (rs.next()) {
				p1id = rs.getInt(2);
				p2id = rs.getInt(3);
				pairs.add( new Pair(rs.getInt(1), p1id, p2id) );
			}
			
			for (Pair pp : pairs) {// for Sequence popup
				String x = dbc2.executeString("select value from pair_props where name='algo1' and pair_idx=" + pp.pid);
				pp.isAlgo1 = (x!=null) ? x.equals("1") : true; 
				x = dbc2.executeString("select value from pair_props where name='number_pseudo' and pair_idx=" + pp.pid);
				pp.hasPseudo = (x!=null) ? x.equals("1") : false;
			}
			rs.close();
		}
		catch (Exception e) {ErrorReport.print(e, "Project pool reset");}
	}
	
	public int getPairIdx(int pp1, int pp2) { // MapperPool to check version; CAS575
		if (hasPair(pp1, pp2)) return getPair(pp1, pp2).pid;
		if (hasPair(pp2, pp1)) return getPair(pp2, pp1).pid;
		return -1;
	}
	public boolean isAlgo1(int pp1, int pp2) {
		if (hasPair(pp1, pp2)) return getPair(pp1, pp2).isAlgo1;
		if (hasPair(pp2, pp1)) return getPair(pp2, pp1).isAlgo1;
		return false;
	}
	
	public String getProperty(int projectID, String property) { // Sequence
		return dbProjProp.getProperty((Object) projectID,property);
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

	public boolean hasPair(int p1, int p2) { // MapperPool
		for (Pair pp : pairs) {
			if (pp.p1 == p1 && pp.p2 == p2)
				return true;
		}
		return false;
	}
	public boolean hasPseudo(int pp1, int pp2) { // MapperPool
		if (hasPair(pp1, pp2)) return getPair(pp1, pp2).hasPseudo;
		if (hasPair(pp2, pp1)) return getPair(pp2, pp1).hasPseudo;
		return false;
	}
	
	public boolean isSwapped(int pX, int pY) { // Data
		Pair pair = getPair(pX, pY);
		return (pair == null || pX == pair.getP1());
	}
	private Pair getPair(int p1, int p2) {
		for (Pair pp : pairs) {
			if ((pp.getP1() == p1 && pp.getP2() == p2) || (pp.getP1() == p2 && pp.getP2() == p1))
				return pp;
		}
		return null;
	}
	/////////////////////////////////////////////////////////////////////
	protected class Project {
		protected int id;
		protected String name;
		protected String type;
		
		protected Project(int id, String name, String type) {
			this.id = id;
			if (name == null) this.name = "";
			else this.name = name;
			this.type = type.intern();
		}

		public boolean equals(Object obj) {
			return obj instanceof Project && ((Project)obj).id == id;
		}
	}
	private class Pair {
		private int p1, p2;
		private int pid;
		private boolean isAlgo1=false;
		private boolean hasPseudo=false;	// for MapperPool to know if need to set annot_hits annot_idx to 0

		protected Pair(int pid, int p1, int p2) {
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
	 */
	@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
	private class DatabaseProperties extends Properties {
		public static final String SEPARATOR = ":##:";

		private DatabaseProperties() {super();}
		
		private void loadAllProps(DBconn2 dbc2) throws Exception {
			ResultSet rs = null;
			try {
				rs = dbc2.executeQuery("SELECT proj_idx, name, value from proj_props");
				while (rs.next()) {
					setProperty(rs.getObject(1),rs.getString(2),rs.getString(3));
				}
			}
			finally {
				if (rs != null) {
					try { rs.close();} catch (Exception e) { }
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
