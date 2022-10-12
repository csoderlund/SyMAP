package symap.pool;

import java.util.Vector;
import java.util.List;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;
import util.DatabaseProperties;
import util.DatabaseReader;
import symap.mapper.Mapper;

public class ProjectProperties extends DatabaseUser {
	private DatabaseProperties dp;
	private List<ProjectPair> projectPairs;
	private ProjectObj[] projects;

	public ProjectProperties(DatabaseReader dr) throws SQLException {
		super(dr);
		projectPairs = new Vector<ProjectPair>();
		dp = new DatabaseProperties();
		reset();
	}

	public void reset() throws SQLException {
		projectPairs.clear();
		dp.load(getDatabaseReader(),"proj_props",new String[] {"proj_idx"},"name","value");

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
				projectPairs.add( new ProjectPair(rs.getInt(1), p1id, p2id, getMapType(p1id,p2id), getP1Scale(p1id,p2id)) );
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

	public String getProperty(String project, String property) {
		return dp.getProperty(new Object[]{getID(project)},property);
	}

	public String getStringProperty(int projectID, String property, String defaultValue) {
		String r = getProperty(projectID,property);
		if (r == null) return defaultValue;
		return r;
	}

	public int getIntProperty(int projectID, String property, int defaultValue) {
		return dp.getIntProperty(projectID,property,defaultValue);
	}

	private int getID(String project) {
		for (int i = 0; i < projects.length; i++) {
			if (projects[i].name.equals(project)) return projects[i].id;
		}
		return -1;
	}

	public String getName(int projectID) {
		for (int i = 0; i < projects.length; i++) {
			if (projects[i].id == projectID) return projects[i].name;
		}
		return null;	
	}

	public String getType(int projectID) {
		for (int i = 0; i < projects.length; i++) {
			if (projects[i].id == projectID) return projects[i].type;
		}
		return null;
	}

	public int getMapType(int p1ID, int p2ID) {
		if (isPseudo(p1ID) && isPseudo(p2ID)) return Mapper.PSEUDO2PSEUDO; 
		if (isPseudo(p1ID) || isPseudo(p2ID)) return Mapper.FPC2PSEUDO; 
		return Mapper.FPC2FPC; 
	}

	public boolean isFPCPseudo(int p1ID, int p2ID) {
		return getType(p1ID) == "pseudo" || getType(p2ID) == "pseudo";
	}

	public boolean isPseudo(int pID) {
		return getType(pID) == "pseudo";
	}
	
	public boolean isFPC(int pID) {
		return getType(pID) == "fpc";
	}

	public String getType(String project) {
		for (int i = 0; i < projects.length; i++) {
			if (projects[i].name.equals(project)) return projects[i].type;
		}
		return null;
	}

	public String getDisplayName(int projectID) {
		return getProperty(projectID,"display_name");
	}

	public String getDisplayName(String project) {
		return getProperty(project,"display_name");
	}

	private double getP1Scale(int p1, int p2) {
		if (isFPC(p1) && isPseudo(p2))
			return getIntProperty(p1,"cbsize",1);
		else if (isFPC(p1) && isFPC(p2))		
			return getIntProperty(p1,"cbsize",1) / (double)getIntProperty(p2,"cbsize",1);
		
		return 1;
	}

	public ProjectPair[] getProjectPairs() {
		return (ProjectPair[])projectPairs.toArray(new ProjectPair[projectPairs.size()]);
	}

	public NamedProjectPair[] getNamedProjectPairs() {
		NamedProjectPair npp[] = new NamedProjectPair[projectPairs.size()];
		int i = 0;
		for (ProjectPair p : projectPairs)
			npp[i++] = new NamedProjectPair(p,getName(p.getP1()),getName(p.getP2()));
		return npp;
	}

	public ProjectPair getProjectPair(String p1, String p2) {
		return getProjectPair(getID(p1),getID(p2));
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

	protected DatabaseProperties getDatabaseProperties() {
		return dp;
	}

	protected ProjectObj[] getProjectObjects() {
		return projects;
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
}
