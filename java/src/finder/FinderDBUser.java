package finder;

import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import util.DatabaseProperties;
import util.DatabaseReader;
import util.PropertiesReader;
import util.Utilities;
import dotplot.*;
import symap.pool.ProjectPair;
import symap.pool.ProjectProperties;
import symap.mapper.MapperPool;

public class FinderDBUser extends DotPlotDBUser {
	public static final boolean STORE_BLOCKS = false;

	//public static final String DB_PROPS_FILE = "/properties/wdb.props"; // mdb removed 4/9/08
	public static final String DB_PROPS_FILE = "/properties/database.properties"; // mdb added 4/9/08

	private static final String GET_FPC_GENOME_LENGTH = /* pid */
		"SELECT SUM(size) FROM contigs WHERE proj_idx=?";

	private static final String GET_PSEUDO_GENOME_LENGTH = /* pid */
		//"SELECT SUM(p.length) FROM "+GROUPS_TABLE+" AS g, "+PSEUDOS_TABLE+" AS p WHERE g.proj_idx=? AND g.idx=p.grp_idx"; // mdb removed 5/16/07 #110
		"SELECT SUM(p.length) FROM groups AS g JOIN pseudos AS p WHERE g.proj_idx=? AND g.idx=p.grp_idx"; // mdb added 5/16/07 #110
	private static final String GET_FPC_NUM_FP_HITS = /* p2,p1,p2,p1 */
		"SELECT COUNT(*) "+
		"FROM fp_hits AS fh "+
		"LEFT JOIN clones AS c           ON (fh.clone1=c.name) "+
		"LEFT OUTER JOIN fp_filter AS ff ON (c.idx=ff.clone1_idx AND ff.proj2_idx=?) "+
		"WHERE fh.proj1_idx=? AND fh.proj2_idx=? AND c.proj_idx=? AND ff.clone1_idx IS NULL ";

	private static final String GET_FPC_NUM_MRK_HITS = /* p1, p2, min_mrk_clones_hit, min_mrk_clones_hit */
		"SELECT m1.idx, m2.idx "+
		//"FROM   "+MARKERS_TABLE+" AS m1, "+MARKERS_TABLE+" AS m2, "+MRK_CTG_TABLE+" AS mc1, "+MRK_CTG_TABLE+" AS mc2 "+ // mdb removed 5/16/07 #110
		"FROM   markers AS m1 JOIN markers AS m2 JOIN mrk_ctg AS mc1 JOIN mrk_ctg AS mc2 "+ // mdb added 5/16/07 #110
		"WHERE  m1.proj_idx=? AND m2.proj_idx=? AND m1.name=m2.name AND m1.idx=mc1.mrk_idx AND mc1.nhits >= ? AND "+
		"       m2.idx=mc2.mrk_idx AND mc2.nhits >= ? AND mc1.ctg_idx != mc2.ctg_idx ";

	private static final String GET_FPC_NUM_CTGS =
		//"SELECT m.idx FROM "+MARKERS_TABLE+" AS m, "+MRK_CTG_TABLE+" AS mc WHERE m.proj_idx=? AND "+ // mdb removed 5/16/07 #110
		"SELECT m.idx FROM markers AS m JOIN mrk_ctg AS mc WHERE m.proj_idx=? AND "+ // mdb added 5/16/07 #110
		"       (SELECT COUNT(*) FROM mrk_ctg AS mc WHERE mc.mrk_idx=m.idx) > ? "+
		"ORDER BY m.idx";

	private static final String GET_PSEUDO_NUM_BES_HITS = /* p1,p2,p1 */
		"SELECT COUNT(*) "+
		//"FROM "+BES_HITS_TABLE+" AS b, "+CLONES_TABLE+" as c "+ // mdb removed 5/16/07 #110
		"FROM bes_hits AS b JOIN clones as c "+ // mdb added 5/16/07 #110
		"WHERE b.proj1_idx=? AND b.proj2_idx=? AND c.proj_idx=? AND c.name=b.clone AND c.ctg_idx > 0 ";

	private static final String GET_PSEUDO_NUM_MRK_HITS = /* p1,p2,p1,min_mrk_clones_hit */
		"SELECT COUNT(*) "+
		//"FROM "+MRK_HITS_TABLE+" AS h, "+GROUPS_TABLE+" AS g, "+MARKERS_TABLE+" AS m, "+MRK_CTG_TABLE+" AS mc "+ // mdb removed 5/16/07 #110
		"FROM mrk_hits AS h JOIN groups AS g JOIN markers AS m JOIN mrk_ctg AS mc "+ // mdb added 5/16/07 #110
		"WHERE h.proj1_idx=? AND g.proj_idx=? AND h.grp2_idx=g.idx AND "+
		"      m.proj_idx=? AND m.name=h.marker AND mc.mrk_idx=m.idx AND mc.nhits >= ?";

	private static final String INSERT_ALT_BLOCK =
		"INSERT INTO alt_blocks" +
		" (rid,blocknum,grp1_idx,start1,end1,ctgs1,grp2_idx,start2,end2,ctgs2,score) VALUES "+
		" (?,?,?,?,?,?,?,?,?,?,?)";

	private static final String CREATE_ALT_BLOCK_TABLE = 
		"CREATE TABLE IF NOT EXISTS alt_blocks ( "+
		" rid         INTEGER NOT NULL,"+
		" grp1_idx    INTEGER NOT NULL,"+
		" grp2_idx    INTEGER NOT NULL,"+
		" blocknum    INTEGER NOT NULL,"+
		" start1      INTEGER NOT NULL,"+
		" end1        INTEGER NOT NULL,"+
		" ctgs1       TEXT,"+
		" start2      INTEGER NOT NULL,"+
		" end2        INTEGER NOT NULL,"+
		" ctgs2       TEXT,"+
		" score       DOUBLE,"+
		" PRIMARY KEY(rid,grp1_idx,grp2_idx,blocknum),"+
		" FOREIGN KEY (rid) REFERENCES alt_blocks_runs (rid) ON DELETE CASCADE"+
		") ENGINE = InnoDB";

	private static final String CREATE_RUNS_TABLE =
		"CREATE TABLE IF NOT EXISTS alt_blocks_runs ( "+
		" rid         INTEGER NOT NULL PRIMARY KEY AUTO_INCREMENT,"+
		" rname       VARCHAR(100) NOT NULL,"+
		" rnum        INTEGER NOT NULL,"+
		" pair_idx    INTEGER NOT NULL,"+
		" comment     TEXT,"+
		" UNIQUE (rname,rnum,pair_idx),"+
		" FOREIGN KEY (pair_idx) REFERENCES pairs (idx) ON DELETE CASCADE"+
		") ENGINE = InnoDB";

	private static final String CREATE_RUNS_PROP_TABLE =
		"CREATE TABLE IF NOT EXISTS alt_runs_props ( "+
		" rid         INTEGER NOT NULL,"+
		" name        VARCHAR(50) NOT NULL,"+
		" value       VARCHAR(50) NOT NULL,"+
		" PRIMARY KEY (rid,name),"+
		" FOREIGN KEY (rid) REFERENCES alt_blocks_runs (rid) ON DELETE CASCADE"+
		") ENGINE = InnoDB";

	private static final String GET_RUN_ID_QUERY =
		//"SELECT rid FROM "+ALT_BLOCKS_RUNS_TABLE+" as a, "+PROJECT_PAIRS_TABLE+" as p "+ // mdb removed 5/16/07 #110
		"SELECT rid FROM alt_blocks_runs as a JOIN pairs as p "+ // mdb added 5/16/07 #110
		"WHERE a.rname=? AND a.rnum=? AND p.proj1_idx=? AND p.proj2_idx=? AND a.pair_idx=p.idx";

	private static final String GET_MAX_RUN_QUERY =
		//"SELECT MAX(rnum) FROM "+ALT_BLOCKS_RUNS_TABLE+" as a, "+PROJECT_PAIRS_TABLE+" as p "+ // mdb removed 5/16/07 #110
		"SELECT MAX(rnum) FROM alt_blocks_runs as a JOIN pairs as p "+ // mdb added 5/16/07 #110
		"WHERE a.rname=? AND p.proj1_idx=? AND p.proj2_idx=? AND a.pair_idx=p.idx";

	private static final String INSERT_RUN_ID_QUERY =
		"INSERT INTO alt_blocks_runs (rname,rnum,pair_idx) VALUE (?,?,"+
		" (SELECT idx FROM pairs AS p WHERE p.proj1_idx=? AND p.proj2_idx=?))";

	private static final String GET_RUN_NAMES_QUERY =
		//"SELECT DISTINCT rname FROM "+ALT_BLOCKS_RUNS_TABLE+" as a, "+PROJECT_PAIRS_TABLE+" as p "+ // mdb removed 5/16/07 #110
		"SELECT DISTINCT rname FROM alt_blocks_runs as a JOIN pairs as p "+ // mdb added 5/16/07 #110
		"WHERE p.proj1_idx=? AND p.proj2_idx=? AND p.idx=a.pair_idx";

	private static final String GET_RUN_NUMBERS_QUERY =
		//"SELECT rnum FROM "+ALT_BLOCKS_RUNS_TABLE+" as a, "+PROJECT_PAIRS_TABLE+" as p "+ // mdb removed 5/16/07 #110
		"SELECT rnum FROM alt_blocks_runs as a JOIN pairs as p "+ // mdb added 5/16/07 #110
		"WHERE a.rname=? AND p.proj1_idx=? AND p.proj2_idx=? AND a.pair_idx=p.idx ORDER BY rnum";

	private static final String GET_RUN_COMMENT_QUERY =
		"SELECT comment FROM alt_blocks_runs WHERE rid=?";

	private static final String SET_RUN_COMMENT_QUERY =
		"UPDATE alt_blocks_runs SET comment=? WHERE rid=?";

	private DBPropsHolder dbProps = new DBPropsHolder();

	/* this won't take up hardly any memory so not worying about caching them */
	private Map<ProjectPair,Integer> numAnchorsMap = new HashMap<ProjectPair,Integer>(); // keys are ProjectPair, values are total number of anchors
	private Map<Integer,Integer> genomeLengthMap   = new HashMap<Integer,Integer>();     // keys are project id, values are total genome length

	public FinderDBUser(DatabaseReader dr) {
		super(dr);
	}

	public FinderDBUser(DatabaseReader dr, MapperPool mp) {
		super(dr,mp);
	}

	public FinderDBUser() {
		super(getDatabaseReader("FinderDBUser"));
	}

	public FinderDBUser(String dbReaderName) {
		super(getDatabaseReader(dbReaderName));
	}

	public static FinderDBUser newInstance(String drName, DotPlotDBUser dbUser) {
		FinderDBUser db = new FinderDBUser(DatabaseReader.getInstance(drName,dbUser.getDatabaseReader()));
		db.copyFrom(dbUser);
		return db;
	}

	public DotPlotDBUser copy(String drName) {
		FinderDBUser db = new FinderDBUser(DatabaseReader.getInstance(drName,getDatabaseReader()));
		db.copyFrom(this);
		return db;
	}

	protected void copyFrom(DotPlotDBUser dbUser) {
		super.copyFrom(dbUser);
		if (dbUser instanceof FinderDBUser) {
			dbProps       = ((FinderDBUser)dbUser).dbProps;
		}
	}

	public DatabaseProperties getDatabaseProperties() throws SQLException {
		synchronized (dbProps) {
			if (!dbProps.isSet()) {
				createTables();
				dbProps.setProps(new DatabaseProperties());
				dbProps.getProps().load(getDatabaseReader(),"alt_runs_props",new String[]{"rid"},"name","value");
			}
			return dbProps.getProps();
		}
	}

	protected int getGenomeLength(ProjectProperties pp, int project) throws SQLException {
		int r = 0;
		synchronized (genomeLengthMap) {
			Integer mv = (Integer)genomeLengthMap.get(new Integer(project));
			if (mv != null) r = mv.intValue();
			else {
				String query = GET_FPC_GENOME_LENGTH;
				if (pp.isPseudo(project)) query = GET_PSEUDO_GENOME_LENGTH;

				Statement stat = createStatement();
				ResultSet rs = stat.executeQuery(setInt(query,project));
				if (rs.next())
					r = rs.getInt(1);
				closeResultSet(rs);
				closeStatement(stat);

				genomeLengthMap.put(new Integer(project),new Integer(r));
			}
		}
		return r;
	}

	protected int getGenomeAnchors(ProjectPair pp) throws SQLException {
		int r = 0;
		synchronized (numAnchorsMap) {
			Integer mv = (Integer)numAnchorsMap.get(pp);
			if (mv != null) r = mv.intValue();
			else {
				Statement stat = null;
				String query;

				stat = createStatement();
				if (pp.isFPCPseudo()) {
					query = setInt(setInt(setInt(GET_PSEUDO_NUM_BES_HITS,pp.getP1()),pp.getP2()),pp.getP1());
					r += getSingleInt(stat,query);

					query = setInt(setInt(setInt(setInt(GET_PSEUDO_NUM_MRK_HITS,pp.getP1()),pp.getP2()),pp.getP1()),
							pp.getMinMrkClonesHit());//pp.getMaxMarkerHits());
					r += getSingleInt(stat,query);
				}
				else {
					query = setInt(setInt(setInt(setInt(GET_FPC_NUM_FP_HITS,pp.getP2()),pp.getP1()),pp.getP2()),pp.getP1());
					r += getSingleInt(stat,query);
					closeStatement(stat);
					stat = createStatement();

					Set<Integer> nctgs1 = new TreeSet<Integer>();
					Set<Integer> nctgs2 = new TreeSet<Integer>();

					ResultSet rs = stat.executeQuery(setInt(setInt(GET_FPC_NUM_CTGS,pp.getP1()),pp.getMaxMarkerHits()));
					while (rs.next())
						nctgs1.add(new Integer(rs.getInt(1)));
					closeResultSet(rs);
					if (pp.getP1() == pp.getP2()) nctgs2 = nctgs1;
					else {
						rs = stat.executeQuery(setInt(setInt(GET_FPC_NUM_CTGS,pp.getP2()),pp.getMaxMarkerHits()));
						while (rs.next())
							nctgs2.add(new Integer(rs.getInt(1)));
					}
					closeResultSet(rs);

					query = setInt(setInt(setInt(setInt(GET_FPC_NUM_MRK_HITS,pp.getP1()),pp.getP2()),pp.getMinMrkClonesHit()),pp.getMinMrkClonesHit());

					rs  = stat.executeQuery(query);
					while (rs.next()) {
						if (!nctgs1.contains(new Integer(rs.getInt(1))) && !nctgs2.contains(new Integer(rs.getInt(2))))
							r++;
					}
					nctgs1.clear();
					nctgs2.clear();
				}
				closeStatement(stat);
				numAnchorsMap.put(pp,new Integer(r));
			}
		}
		return r;
	}

	private int getSingleInt(Statement stat, String query) throws SQLException {
		int r = 0;
		ResultSet rs = stat.executeQuery(query);
		if (rs.next()) r = rs.getInt(1);
		closeResultSet(rs);
		return r;
	}

	public void storeProperties() throws SQLException {
		getDatabaseProperties().store(getDatabaseReader(),"alt_runs_props",new String[]{"rid"},"name","value");
	}

	public void storePairProperties(DatabaseProperties props) throws SQLException {
		props.store(getDatabaseReader(),"pair_props",new String[] {"pair_idx","proj1_idx","proj2_idx"},"name","value");
	}

	public void createTables() throws SQLException {
		if (!dbProps.createdTables()) {
			Statement stat = createStatement();
			stat.addBatch(CREATE_RUNS_TABLE);
			stat.executeBatch();
			closeStatement(stat);

			stat = createStatement();
			stat.addBatch(CREATE_RUNS_PROP_TABLE);
			if (STORE_BLOCKS) stat.addBatch(CREATE_ALT_BLOCK_TABLE);
			stat.executeBatch();
			closeStatement(stat);

			dbProps.setCreatedTables();
		}
	}

	public String getRunComment(int rid) throws SQLException {
		Statement stat = createStatement();
		ResultSet rs = stat.executeQuery(setInt(GET_RUN_COMMENT_QUERY,rid));
		String ret = null;
		if (rs.next()) ret = rs.getString(1);
		return ret == null ? "" : ret;
	}

	public void setRunComment(int rid, String comment) throws SQLException {
		if (comment == null) comment = "";
		PreparedStatement stat = prepareStatement(SET_RUN_COMMENT_QUERY);
		stat.setString(1,comment);
		stat.setInt(2,rid);
		stat.executeUpdate();
	}

	public List<String> getRunNames(ProjectPair pp) throws SQLException {
		createTables();
		List<String> runNames = new LinkedList<String>();
		Statement stat = createStatement();
		ResultSet rs = stat.executeQuery(setInt(setInt(GET_RUN_NAMES_QUERY,pp.getP1()),pp.getP2()));
		while (rs.next())
			runNames.add(rs.getString(1));
		closeResultSet(rs);
		closeStatement(stat);
		return runNames;
	}

	public List<Integer> getRunNumbers(ProjectPair pp, String rname) throws SQLException {
		createTables();
		List<Integer> runNums = new LinkedList<Integer>();
		Statement stat = createStatement();
		ResultSet rs = stat.executeQuery(setInt(setInt(setString(GET_RUN_NUMBERS_QUERY,rname),pp.getP1()),pp.getP2()));
		while (rs.next())
			runNums.add(new Integer(rs.getInt(1)));
		closeResultSet(rs);
		closeStatement(stat);
		return runNums;
	}

	/**
	 * Method <code>getRunID</code> looks for the run id in the database and returns it if found.
	 * Otherwise a new run id is added. 
	 *
	 * If rnum is less than zero the maximum rnum for the given run 
	 * is used.  If no run exists in this case an rnum of 1 is used.
	 *
	 * If rnum is equal to 0 than a new run is inserted with a run number of one greater than
	 * the current max run number or 1 if no runs exist.
	 *
	 * @param rname a <code>String</code> value
	 * @param rnum a <code>int</code> value of the run number
	 * @param pp a <code>ProjectPair</code> value
	 * @return an <code>int</code> value
	 * @exception SQLException if an error occurs
	 */
	public int getRunID(String rname, int rnum, ProjectPair pp) throws SQLException {
		int rid = -1;
		if (rnum < 0) {
			rid = getMaxAltBlockRID(rname,pp);
			if (rid >= 0) return rid;
		}
		Statement stat = createStatement();
		ResultSet rs = null;
		if (rnum > 0) {
			rs = stat.executeQuery(setInt(setInt(setInt(setString(GET_RUN_ID_QUERY,rname),rnum),pp.getP1()),pp.getP2()));
			if (rs.next())
				rid = rs.getInt(1);
		}
		else if (rnum == 0) {
			rs = stat.executeQuery(setInt(setInt(setString(GET_MAX_RUN_QUERY,rname),pp.getP1()),pp.getP2()));
			if (rs.next()) rnum = rs.getInt(1)+1;
			if (rnum <= 0) rnum = 1;
		}
		if (rid < 0) {
			closeResultSet(rs);
			if (rnum <= 0) rnum = 1;
			if (stat.executeUpdate(setInt(setInt(setInt(setString(INSERT_RUN_ID_QUERY,rname),rnum),pp.getP1()),pp.getP2())) > 0) {
				rs = stat.executeQuery("SELECT LAST_INSERT_ID()");
				if (rs.next()) rid = rs.getInt(1);
			}
		}

		closeResultSet(rs);
		closeStatement(stat);

		return rid;
	}

	public void removeRun(int rid) throws SQLException {
		Statement stat = createStatement();
		/*
	  stat.addBatch("DELETE FROM "+ALT_BLOCKS_TABLE+"      WHERE rid="+rid);
	  stat.addBatch("DELETE FROM "+ALT_RUNS_PROPS_TABLE+"  WHERE rid="+rid);
		 */
		stat.addBatch("DELETE FROM alt_blocks_runs WHERE rid="+rid);
		stat.executeBatch();
		closeStatement(stat);

		if (dbProps.isSet()) {
			DatabaseProperties props = dbProps.getProps();
			props.clear();
			props.load(getDatabaseReader(),"alt_runs_props",new String[]{"rid"},"name","value");
		}
	}

	/**
	 * Method <code>writeFBlocks</code>
	 *
	 * @param rid an <code>int</code> value
	 * @param block a <code>Block</code> value
	 * @param ablocks an <code>FBlock[]</code> value
	 * @exception SQLException if an error occurs
	 */
	public void writeFBlocks(int rid, Tile block, FBlock[] ablocks) throws SQLException {
		if (ablocks == null || ablocks.length == 0 || !STORE_BLOCKS) return ;

		Statement stat = createStatement();
		stat.executeUpdate("DELETE FROM alt_blocks WHERE rid="+rid+" AND "+
				"grp1_idx="+block.getGroup(Y)+" AND grp2_idx="+block.getGroup(X));
		closeStatement(stat);

		PreparedStatement ps = prepareStatement(INSERT_ALT_BLOCK);
		for (int i = 0; i < ablocks.length; ++i) {
			ps.setInt(1,rid);
			ps.setInt(2,ablocks[i].getNumber());
			ps.setInt(3,block.getGroup(Y).getID());
			ps.setInt(4,ablocks[i].getStart(Y));
			ps.setInt(5,ablocks[i].getEnd(Y));
			ps.setString(6,Utilities.getCommaSepString(ablocks[i].getContigNumbers(Y)));
			ps.setInt(7,block.getGroup(X).getID());
			ps.setInt(8,ablocks[i].getStart(X));
			ps.setInt(9,ablocks[i].getEnd(X));
			ps.setString(10,Utilities.getCommaSepString(ablocks[i].getContigNumbers(X)));
			ps.setDouble(11,ablocks[i].getScore());
			ps.addBatch();
		}
		ps.executeBatch();
		closeStatement(ps);
	}

	public static DatabaseReader getDatabaseReader(String appName) {
		DatabaseReader dr = null;
		try {
			dr = getDatabaseReader(appName,new PropertiesReader(FinderDBUser.class.getResource(DB_PROPS_FILE)));
		} catch (Exception e) {
			System.err.println("Problem loading default database properties file ["+DB_PROPS_FILE+"]");
			System.err.println("\t"+e.getMessage());
			dr = getDatabaseReader(appName,null,null,null,null);
		}
		return dr;
	}

	private static class DBPropsHolder {
		private DatabaseProperties dbProps;
		private boolean createdTables;

		public DBPropsHolder() {
			dbProps = null;
			createdTables = false;
		}

		public void setProps(DatabaseProperties dbProps) {
			this.dbProps = dbProps;
		}

		public DatabaseProperties getProps() {
			return dbProps;
		}

		public boolean isSet() {
			return dbProps != null;
		}

		public boolean createdTables() {
			return createdTables;
		}

		public void setCreatedTables() {
			createdTables = true;
		}
	}
}
