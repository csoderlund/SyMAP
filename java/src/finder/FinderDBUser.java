package finder;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;
import util.DatabaseReader;
import dotplot.*;
import symap.pool.ProjectPair;
import symap.pool.ProjectProperties;

public class FinderDBUser extends DotPlotDBUser {
	public static final String DB_PROPS_FILE = "/properties/database.properties"; 

	private static final String GET_FPC_GENOME_LENGTH = /* pid */
		"SELECT SUM(size) FROM contigs WHERE proj_idx=?";

	private static final String GET_PSEUDO_GENOME_LENGTH = /* pid */
		"SELECT SUM(p.length) FROM xgroups AS g JOIN pseudos AS p WHERE g.proj_idx=? AND g.idx=p.grp_idx"; 
	private static final String GET_FPC_NUM_FP_HITS = /* p2,p1,p2,p1 */
		"SELECT COUNT(*) "+
		"FROM fp_hits AS fh "+
		"LEFT JOIN clones AS c           ON (fh.clone1=c.name) "+
		"LEFT OUTER JOIN fp_filter AS ff ON (c.idx=ff.clone1_idx AND ff.proj2_idx=?) "+
		"WHERE fh.proj1_idx=? AND fh.proj2_idx=? AND c.proj_idx=? AND ff.clone1_idx IS NULL ";

	private static final String GET_FPC_NUM_MRK_HITS = /* p1, p2, min_mrk_clones_hit, min_mrk_clones_hit */
		"SELECT m1.idx, m2.idx "+
		"FROM   markers AS m1 JOIN markers AS m2 JOIN mrk_ctg AS mc1 JOIN mrk_ctg AS mc2 "+ 
		"WHERE  m1.proj_idx=? AND m2.proj_idx=? AND m1.name=m2.name AND m1.idx=mc1.mrk_idx AND mc1.nhits >= ? AND "+
		"       m2.idx=mc2.mrk_idx AND mc2.nhits >= ? AND mc1.ctg_idx != mc2.ctg_idx ";

	private static final String GET_FPC_NUM_CTGS =
		"SELECT m.idx FROM markers AS m JOIN mrk_ctg AS mc WHERE m.proj_idx=? AND "+ 
		"       (SELECT COUNT(*) FROM mrk_ctg AS mc WHERE mc.mrk_idx=m.idx) > ? "+
		"ORDER BY m.idx";

	private static final String GET_PSEUDO_NUM_BES_HITS = /* p1,p2,p1 */
		"SELECT COUNT(*) "+
		"FROM bes_hits AS b JOIN clones as c "+ 
		"WHERE b.proj1_idx=? AND b.proj2_idx=? AND c.proj_idx=? AND c.name=b.clone AND c.ctg_idx > 0 ";

	private static final String GET_PSEUDO_NUM_MRK_HITS = /* p1,p2,p1,min_mrk_clones_hit */
		"SELECT COUNT(*) "+
		"FROM mrk_hits AS h JOIN xgroups AS g JOIN markers AS m JOIN mrk_ctg AS mc "+ 
		"WHERE h.proj1_idx=? AND g.proj_idx=? AND h.grp2_idx=g.idx AND "+
		"      m.proj_idx=? AND m.name=h.marker AND mc.mrk_idx=m.idx AND mc.nhits >= ?";

	/* this won't take up hardly any memory so not worrying about caching them */
	private Map<ProjectPair,Integer> numAnchorsMap = new HashMap<ProjectPair,Integer>(); // keys are ProjectPair, values are total number of anchors
	private Map<Integer,Integer> genomeLengthMap   = new HashMap<Integer,Integer>();     // keys are project id, values are total genome length

	public FinderDBUser(DatabaseReader dr) {
		super(dr);
	}

	public static FinderDBUser newInstance(String drName, DotPlotDBUser dbUser) {
		FinderDBUser db = new FinderDBUser(DatabaseReader.getInstance(drName,dbUser.getDatabaseReader()));
		db.copyFrom(dbUser);
		return db;
	}

	protected void copyFrom(DotPlotDBUser dbUser) {
		super.copyFrom(dbUser);
	}

	protected int getGenomeLength(ProjectProperties pp, int project) throws SQLException {
		int r = 0;
		synchronized (genomeLengthMap) {
			Integer mv = (Integer)genomeLengthMap.get(project);
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

				genomeLengthMap.put(project, r);
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
						nctgs1.add(rs.getInt(1));
					closeResultSet(rs);
					if (pp.getP1() == pp.getP2()) nctgs2 = nctgs1;
					else {
						rs = stat.executeQuery(setInt(setInt(GET_FPC_NUM_CTGS,pp.getP2()),pp.getMaxMarkerHits()));
						while (rs.next())
							nctgs2.add(rs.getInt(1));
					}
					closeResultSet(rs);

					query = setInt(setInt(setInt(setInt(GET_FPC_NUM_MRK_HITS,pp.getP1()),pp.getP2()),pp.getMinMrkClonesHit()),pp.getMinMrkClonesHit());

					rs  = stat.executeQuery(query);
					while (rs.next()) {
						if (!nctgs1.contains(rs.getInt(1)) && !nctgs2.contains(rs.getInt(2)))
							r++;
					}
					nctgs1.clear();
					nctgs2.clear();
				}
				closeStatement(stat);
				numAnchorsMap.put(pp, r);
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

	public void writeFBlocks(int rid, Tile block, FBlock[] ablocks) throws SQLException {
		return ;
	}
}
