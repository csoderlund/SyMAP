package dotplot;

import java.applet.Applet;

import java.util.List;
import java.util.Vector;
import java.util.ArrayList;

import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;

import util.PropertiesReader;
import util.DatabaseReader;
import util.Utilities;
import util.ListCache;
import softref.SoftListCache;
import symap.pool.DatabaseUser;
import symap.pool.ProjectPair;
import symap.mapper.MapperPool;
import symap.mapper.RepetitiveMarkerFilterData;
import symap.SyMAPApplet;
import symap.SyMAPConstants;
import finder.FBlock;
import symap.pool.ProjectProperties;

public class DotPlotDBUser extends DatabaseUser implements DotPlotConstants, SyMAPConstants {
	//public static final String DB_PROPS_FILE = "/properties/rdb.props"; // mdb removed 4/9/08
	public static final String DB_PROPS_FILE = "/properties/database.properties"; // mdb added 4/9/08

	private static final String REPETITIVE_MRK_FILTER_QUERY = MapperPool.REPETITIVE_MRK_FILTER_QUERY;
	private static final String RMFQ_SINGLE_PROJECT         = MapperPool.RMFQ_SINGLE_PROJECT;

	private static final String MAX_ALT_BLOCKS_RID =
		"SELECT rid "+
		"FROM alt_blocks_runs "+
		"WHERE rname=? AND proj1_idx=? AND proj2_idx=? HAVING MAX(rnum)";

	private MapperPool mapperPool;
	private ListCache rmfdCache    = new SoftListCache(1,3);
	private ListCache projectCache = new SoftListCache(1,1);

	public DotPlotDBUser(DatabaseReader dr) {
		super(dr);
	}

	public DotPlotDBUser(DatabaseReader dr, MapperPool mp) {
		this(dr);
		this.mapperPool = mp;
	}

// mdb unused 2/2/10	
//	public DotPlotDBUser() {
//		this(getDatabaseReader(SyMAPConstants.DB_CONNECTION_DOTPLOT,null,null,null,null));
//	}

	public DotPlotDBUser(Applet applet) {
		this(applet,null);
	}

	/**
	 * Creates a new <code>DotPlotDBUser</code> instance.
	 *
	 * @param applet an <code>Applet</code> value used to get attributes set by 
	 * the applet for creating a DatabaseReader (optional)
	 * @param databaseReaderName a <code>String</code> value DEFAULT_DBREADER_NAME is used if null
	 */
	public DotPlotDBUser(Applet applet, String databaseReaderName) {
		this(getDatabaseReader(
				databaseReaderName != null ? databaseReaderName : SyMAPConstants.DB_CONNECTION_DOTPLOT,
				applet != null ? applet.getParameter(SyMAPApplet.DATABASE_URL) : null,
				applet != null ? applet.getParameter(SyMAPApplet.USERNAME)     : null,
				applet != null ? applet.getParameter(SyMAPApplet.PASSWORD)     : null,
				/*applet != null ? Utilities.getHost(applet)                     :*/ null)); // mdb removed alt host 4/9/08
	}

	public void setMapperPool(MapperPool mp) {
		this.mapperPool = mp;
	}

	public DotPlotDBUser copy(String drName) {
		DotPlotDBUser db = new DotPlotDBUser(DatabaseReader.getInstance(drName,getDatabaseReader()),mapperPool);
		db.rmfdCache = rmfdCache;
		db.projectCache = projectCache;
		return db;
	}

	protected void copyFrom(DotPlotDBUser dbUser) {
		mapperPool   = dbUser.mapperPool;
		rmfdCache    = dbUser.rmfdCache;
		projectCache = dbUser.projectCache;
	}

	public void clear() {
		rmfdCache.clear();
		projectCache.clear();
	}

	public DatabaseReader getDatabaseReader() {
		return super.getDatabaseReader();
	}

	public void setFBlocks(int rid, Tile block, int altNum) throws SQLException {
		try {
			String qry = 
				"SELECT blocknum,start2,end2,ctgs2,start1,end1,ctgs1,score "+
				"FROM alt_blocks "+
				"WHERE rid="+rid+" AND grp1_idx="+block.getGroup(Y)+" AND grp2_idx="+block.getGroup(X);
			Statement stat = createStatement();
			ResultSet rs = stat.executeQuery(qry);
			Vector<ABlock> list = new Vector<ABlock>();
			while (rs.next())
				list.add(
					new FBlock(block,
						rid,
						rs.getInt(1),
						rs.getInt(2),
						rs.getInt(3),
						Utilities.getIntArray(rs.getString(4)),
						rs.getInt(5),
						rs.getInt(6),
						Utilities.getIntArray(rs.getString(7)),
						rs.getDouble(8)));
			block.setAltBlocksRun(altNum,new AltBlocksRun(rid,list));

			closeResultSet(rs);
			closeStatement(stat);
		}
		catch (SQLException e) {
			close();
			throw e;
		}
	}

	public int getMaxAltBlockRID(String rname, ProjectPair pp) throws SQLException {
		Statement stat = createStatement();
		String qry = setInt(setInt(setString(MAX_ALT_BLOCKS_RID,rname),pp.getP1()),pp.getP2());
		ResultSet rs = stat.executeQuery(qry);
		int rid = -1;
		if (rs.next()) rid = rs.getInt(1);
		closeResultSet(rs);
		closeStatement(stat);
		return rid;
	}

	public void setIBlocks(Tile tile,
			Project[] projects, // mdb added 1/20/10 #205
			boolean swapped) 	// mdb added 12/18/09 #206
	throws SQLException 
	{
		try {
			// mdb added 12/18/09 #206 - swap projects to match database
			Group gX = (swapped ? tile.getGroup(Y) : tile.getGroup(X));
			Group gY = (swapped ? tile.getGroup(X) : tile.getGroup(Y));
			
			// mdb added 1/20/10 #205
			Project pX = (swapped ? projects[Y] : projects[X]);
			Project pY = (swapped ? projects[X] : projects[Y]);
			
			Statement stat = createStatement();
			String qry = 
				"SELECT blocknum,start2,end2,ctgs2,start1,end1,ctgs1 "+
				"FROM blocks WHERE grp1_idx="+gY+" AND grp2_idx="+gX;
			ResultSet rs = stat.executeQuery(qry);
			tile.clearIBlocks();
			while (rs.next()) {
				int start2 = rs.getInt(2);
				int end2   = rs.getInt(3);
				int start1 = rs.getInt(5);
				int end1   = rs.getInt(6);
				
				// mdb added 1/20/10 #205
				if (pX.isFPC()) {
					start2 *= pX.getScale();
					end2   *= pX.getScale();
				}
				if (pY.isFPC()) {
					start1 *= pY.getScale();
					end1   *= pY.getScale();
				}
					
				tile.addIBlock(
						rs.getInt(1), 				/* int number */
						swapped ? start1 : start2, 	/* int sX */ // mdb added swap 12/18/09 #206
						swapped ? end1 : end2,		/* int eX */ // mdb added swap 12/18/09 #206
						Utilities.getIntArray(rs.getString(4)), /* int[] ctgX */
						swapped ? start2 : start1, 	/* int sY */ // mdb added swap 12/18/09 #206
						swapped ? end2 : end1, 		/* int eY */ // mdb added swap 12/18/09 #206
						Utilities.getIntArray(rs.getString(7))); /* int[] ctgY */
			}
			closeResultSet(rs);
			closeStatement(stat);
		}
		catch (SQLException e) {
			close();
			throw e;
		}
	}

	public void setHits(Project[] projects, ProjectPair pair, Tile tile, 
			boolean filtered, ScoreBounds sb,
			boolean swapped) // mdb added 12/18/09 #206
	throws SQLException 
	{
		if (tile.isLoaded(filtered ? Tile.SOME_LOADED : Tile.FULLY_LOADED)) return ;
		try {
			RepetitiveMarkerFilterData rmfd = getRepetitiveMarkerFilterData(pair);
			List<Hit> hits = new ArrayList<Hit>();
			Project pX = projects[X], pY = projects[Y];
			Group gX = tile.getGroup(X), gY = tile.getGroup(Y);
			Statement stat = createStatement();
			ResultSet rs;
			String qry;
			
			// mdb added 12/18/09 #206 - swap projects to match database
			if (swapped) {
				pX = projects[Y];
				pY = projects[X];
				gX = tile.getGroup(Y);
				gY = tile.getGroup(X);
			}
			
			// mdb added pseudo-to-pseudo 7/10/07 #121
			if (pX.isPseudo() && pY.isPseudo()) { // PSEUDO to PSEUDO //////////
				qry =
					"SELECT (h.start2+h.end2)>>1 as posX,(h.start1+h.end1)>>1 as posY,h.evalue,h.pctid,b.block_idx,(h.end2-h.start2) as length,h.strand "+
					"FROM pseudo_hits AS h "+
					"LEFT JOIN pseudo_block_hits AS b ON (h.idx=b.hit_idx) "+
					"WHERE (h.proj1_idx="+pY.getID()+" AND h.proj2_idx="+pX.getID()+
					"       AND h.grp1_idx="+gY.getID()+" AND h.grp2_idx="+gX.getID()+")";
				rs = stat.executeQuery(qry);
				while (rs.next()) {
					// mdb added 8/18/09 - fix rendering of reversed hits for #149
					int length = rs.getInt(6);
					String strand = rs.getString(7);
					if (strand.equals("+/-") || strand.equals("-/+"))
						length = -length;
					int posX = rs.getInt(1);
					int posY = rs.getInt(2);
					
					Hit hit = new DPHit(
									swapped ? posY : posX,	/* int posX */ // mdb added swap 12/18/09 #206
									swapped ? posX : posY,	/* int posY */ // mdb added swap 12/18/09 #206
									rs.getDouble(3),		/* double evalue */
									rs.getDouble(4),		/* double pctid	*/
									BES,					/* int type	*/
									false,					/* boolean repetitive */
									rs.getLong(5)!=0,		/* boolean block */
									length);				/* int length */ // mdb added length 12/17/09 #149
					hits.add(hit);
					if (sb != null) sb.condSetBounds(hit);
				}
				closeResultSet(rs);
			}
			else { // FPC to PSEUDO or FPC to FPC //////////////////////////////
				if (pX.isPseudo()) { 
					qry =
						"SELECT (h.start2+h.end2)>>1 as posX, co.number, cl.cb1, cl.cb2, cl.bes1, cl.bes2, h.bes_type,"+
						"       h.evalue,h.pctid,bf.filter_code,pf.filter_code,b.ctghit_idx IS NOT NULL AND b.ctghit_idx>0 "+
						"FROM bes_hits AS h "+
						"INNER JOIN clones AS cl ON (cl.proj_idx="+pY.getID()+" AND cl.name=h.clone) "+
						"INNER JOIN contigs AS co ON (co.idx=cl.ctg_idx AND co.grp_idx="+gY.getID()+") "+
						"LEFT  JOIN bes_block_hits AS b ON (h.idx=b.hit_idx) "+
						"LEFT  JOIN bes_filter AS bf ON (bf.clone_idx=cl.idx AND bf.proj2_idx="+pX.getID()+
						"                                                    AND bf.bes_type=h.bes_type) "+
						"LEFT  JOIN pseudo_filter AS pf ON (pf.proj1_idx="+pY.getID()+" AND "+
						"                                                    pf.grp2_idx="+gX.getID()+" AND "+
						"                                       (h.start2+h.end2)>>1 >= pf.start AND (h.start2+h.end2)>>1 <= pf.end) "+
						"WHERE h.proj1_idx="+pY.getID()+" AND h.proj2_idx="+pX.getID()+" AND h.grp2_idx="+gX.getID();
					if (filtered) {
						qry += 
							" AND ((b.ctghit_idx IS NOT NULL AND b.ctghit_idx > 0) OR "+
							"      ((bf.filter_code IS NULL OR bf.filter_code = 0) AND "+
							"       (pf.filter_code IS NULL OR pf.filter_code = 0)) ) ";
					}
				}
				else {
					qry = 
						"SELECT c2.number,c1.number,(cl2.cb1+cl2.cb2)>>1 as posX,(cl1.cb1+cl1.cb2)>>1 AS posY, "+
						"       h.score,f1.filter,f2.filter,b.ctghit_idx IS NOT NULL AND b.ctghit_idx > 0 "+
						"FROM contigs AS c1 JOIN clones AS cl1 JOIN fp_hits AS h JOIN "+
						"     clones AS cl2 JOIN contigs AS c2 "+
						"LEFT JOIN fp_block_hits AS b ON (h.idx=b.hit_idx) "+
						"LEFT JOIN fp_filter AS f1 ON (f1.clone1_idx=cl1.idx AND f1.proj2_idx="+pX.getID()+") "+
						"LEFT JOIN fp_filter AS f2 ON (f2.clone1_idx=cl2.idx AND f2.proj2_idx="+pY.getID()+") "+
						"WHERE c1.proj_idx="+pY.getID()+" AND c1.grp_idx="+gY.getID()+" AND cl1.ctg_idx=c1.idx AND "+
						"      h.proj1_idx="+pY.getID()+" AND h.proj2_idx="+pX.getID()+" AND h.clone1=cl1.name AND "+
						"      c1.proj_idx="+pY.getID()+" AND c1.grp_idx="+gY.getID()+" AND c1.idx=cl1.ctg_idx AND "+
						"      cl2.proj_idx="+pX.getID()+" AND cl2.name=h.clone2 AND cl2.ctg_idx=c2.idx AND "+
						"      c2.grp_idx="+gX.getID();
					if (filtered) {
						qry += 
							" AND ((b.ctghit_idx IS NOT NULL AND b.ctghit_idx > 0) OR "+
							"      ((f1.filter IS NULL OR f1.filter = 0) AND "+
							"       (f2.filter IS NULL OR f2.filter = 0)) )";
					} 
				}
				rs = stat.executeQuery(qry);
				if (pX.isPseudo()) {
					while (rs.next()) {
						int posX = rs.getInt(1);
						int posY = gY.getContigPosition(rs.getInt(2)) +
									getClonePosition(rs.getInt(3), 
											rs.getInt(4),		
											rs.getString(5),	
											rs.getString(6),	
											rs.getString(7))*pY.getScale(); // mdb added scale factor 1/20/10 #205
						
						Hit hit = new DPHit(
										swapped ? posY : posX,			/* int posX */ // mdb added swap 12/18/09 #206
										swapped ? posX : posY,			/* int posY */ // mdb added swap 12/18/09 #206
										rs.getDouble(8),				/* double evalue */
										rs.getDouble(9),				/* double pctid	*/
										BES,							/* int type	*/
										rs.getInt(10)+rs.getInt(11)>0,	/* boolean repetitive	*/
										rs.getBoolean(12),  			/* boolean block */
										0); 
						hits.add(hit);
						if (sb != null) sb.condSetBounds(hit);
					}
				}
				else {
					while (rs.next()) {
						int posX = gX.getContigPosition(rs.getInt(1))+rs.getInt(3)*pX.getScale();// mdb added scale factor 1/20/10 #205
						int posY = gY.getContigPosition(rs.getInt(2))+rs.getInt(4)*pY.getScale();// mdb added scale factor 1/20/10 #205
						
						Hit hit = new DPHit(
										swapped ? posY : posX,		/* int posX */ // mdb added swap 12/18/09 #206
										swapped ? posX : posY,		/* int posY */ // mdb added swap 12/18/09 #206
										rs.getDouble(5),			/* double evalue */
										ANY_PCTID,					/* double pctid	*/
										FP,							/* int type	*/
										rs.getInt(6)+rs.getInt(7)>0,/* boolean repetitive */
										rs.getBoolean(8), 			/* boolean block */
										0); // mdb FIXME - added 12/17/07 #149
						hits.add(hit);
						if (sb != null) sb.condSetBounds(hit);
					}
				}
				closeResultSet(rs);
		
				if (pX.isPseudo()) { // fpc-to-pseudo
					qry = 
						"SELECT (h.end2+h.start2)>>1 AS posX,c.number,mc.pos AS posY,h.evalue,h.pctid,"+
						"                mf.filter_code,pf.filter_code,mb.hit_idx IS NOT NULL, h.marker AS marker "+
						"FROM contigs AS c  "+
						"INNER JOIN mrk_ctg AS mc ON (mc.ctg_idx=c.idx) "+
						"INNER JOIN markers AS m  ON (m.idx=mc.mrk_idx) "+
						"INNER JOIN mrk_hits AS h  ON (h.proj1_idx="+pY.getID()+" AND h.grp2_idx="+gX.getID()+
						"                                                     AND h.marker=m.name) "+
						"LEFT  JOIN mrk_filter AS mf ON (mf.mrk_idx=m.idx AND mf.proj2_idx="+pX.getID()+") "+
						"LEFT  JOIN pseudo_filter  AS pf ON (pf.proj1_idx="+pY.getID()+" AND pf.grp2_idx="+gX.getID()+
						"                                                     AND (h.end2+h.start2)>>1 >= pf.start AND "+
						"                                                         (h.end2+h.start2)>>1 <= pf.end) "+
						"LEFT  JOIN mrk_block_hits AS mb ON (mb.hit_idx=h.idx AND "+
						"                                                     mb.ctghit_idx IN (SELECT ch.idx "+
						"                                                         FROM ctghits AS ch "+
						"                                                         WHERE ch.ctg1_idx=c.idx AND ch.grp2_idx="+gX+")) "+
						"WHERE c.proj_idx="+pY.getID()+" AND c.grp_idx="+gY.getID();
					if (filtered) {
						qry += " AND ( (mb.hit_idx IS NOT NULL AND mb.hit_idx > 0) OR "+
						"  ((mf.filter_code IS NULL OR mf.filter_code = 0) AND "+
						"   (pf.filter_code IS NULL OR pf.filter_code = 0)) )";
					}
				}
				else { // fpc-to-fpc
					qry =
						"SELECT c2.number,mc2.pos AS posX, c1.number,mc1.pos AS posY, "+
						"                (b.ctghit_idx IS NOT NULL) AS block, m1.name AS marker "+
						"FROM contigs AS c1 JOIN mrk_ctg AS mc1 JOIN markers AS m1 JOIN "+
						"     markers AS m2 JOIN mrk_ctg AS mc2 JOIN contigs AS c2 "+
						"LEFT JOIN ctghits AS ch ON (ch.ctg1_idx=c1.idx AND c2.idx=ch.ctg2_idx) "+
						"LEFT JOIN shared_mrk_block_hits AS b ON (b.ctghit_idx=ch.idx AND b.mrk_idx=m1.idx) "+
						"WHERE c1.proj_idx="+pY.getID()+" AND c1.grp_idx="+gY.getID()+" AND c1.idx=mc1.ctg_idx AND mc1.mrk_idx=m1.idx "+
						" AND m2.proj_idx="+pX.getID()+" AND m1.name=m2.name AND m2.idx=mc2.mrk_idx AND mc2.ctg_idx=c2.idx "+
						" AND c2.grp_idx="+gX.getID();
				}
				rs = stat.executeQuery(qry);
				if (pX.isPseudo()) { // fpc-to-pseudo
					boolean rhit, bhit;
					while (rs.next()) {
						bhit = rs.getBoolean(8);
						rhit = ((rs.getInt(6)+rs.getInt(7)) > 0) || rmfd.isRepetitive(rs.getString(9)) && !bhit;
						if (!filtered || !rhit || bhit) {
							int posX = rs.getInt(1);
							int posY = gY.getContigPosition(rs.getInt(2))+rs.getInt(3)*pY.getScale(); // mdb added scale factor 1/20/10 #205
								
							Hit hit = new DPHit(
									swapped ? posY : posX,	/* int posX */ // mdb added swap 12/18/09 #206
									swapped ? posX : posY,	/* int posY */ // mdb added swap 12/18/09 #206
									rs.getDouble(4),		/* double evalue */
									rs.getDouble(5),		/* double pctid */
									MRK,					/* int type	*/
									rhit,					/* boolean repetitive */
									bhit,					/* boolean block */
									0); // mdb FIXME - added 12/17/07 #149
							hits.add(hit);
							if (sb != null) sb.condSetBounds(hit);
						}
					}		    
				}
				else { // fpc-to-fpc
					boolean rhit, bhit;
					while (rs.next()) {
						bhit = rs.getBoolean(5);
						rhit = (rmfd == null ? false : rmfd.isRepetitive(rs.getString(6)) && !bhit);
						if (!filtered || !rhit || bhit) {
							int posX = gX.getContigPosition(rs.getInt(1))+rs.getInt(2)*pX.getScale(); // mdb added scale factor 1/20/10 #205
							int posY = gY.getContigPosition(rs.getInt(3))+rs.getInt(4)*pY.getScale(); // mdb added scale factor 1/20/10 #205
								
							Hit hit = new DPHit(
									swapped ? posY : posX, /* int posX */ // mdb added swap 12/18/09 #206
									swapped ? posX : posY, /* int posY */ // mdb added swap 12/18/09 #206
									NO_EVALUE,	/* double evalue */
									NO_PCTID,	/* double pctid */
									MRK,		/* int type	*/
									rhit,		/* boolean repetitive */
									bhit,		/* boolean block */
									0); // mdb FIXME - added 12/17/07 #149
							hits.add(hit);
						}
					}
				}
			}
			closeResultSet(rs);
			closeStatement(stat);

			tile.setHits(hits.toArray(new Hit[0]), filtered ? Tile.SOME_LOADED : Tile.FULLY_LOADED);
			hits.clear();
		}
		catch (SQLException e) {
			close();
			throw e;
		}
		catch (NullPointerException n) {
			close();
			throw n;
		}
	}

	private int getClonePosition(int cb1, int cb2, String bes1, String bes2, String hitBES) {
		if (R_VALUE_STR.equals(hitBES)) {
			return R_VALUE_STR.equals(bes1) || F_VALUE_STR.equals(bes2) ? cb1 : cb2;
		}
		else {
			return F_VALUE_STR.equals(bes1) || R_VALUE_STR.equals(bes2) ? cb1 : cb2;
		}
	}

	public void setProjects(Project[] projects, 
			int[] xGroups, int[] yGroups, 	// mdb added 1/14/09 #207
			ProjectProperties pp) 			// mdb added 12/30/09 #206
	throws SQLException {
		try {
			ProjectHolder th = new ProjectHolder(projects);
			ProjectHolder ph = (ProjectHolder)projectCache.get(th);
			if (ph != null)
				ph.copyInto(projects);
			else
				projectCache.add(th);
				
			// mdb moved out of else above 1/15/10 #207
			setGroups(projects, xGroups, yGroups, pp);
			//printProjects(projects);
			setContigs(projects, xGroups, yGroups);
		}
		catch (SQLException e) {
			close();
			throw e;
		}
	}
	
	private static void printProjects(Project[] projects) { // for debug
		for (Project p : projects) { 
			System.out.println(p.getDisplayName());
			for (Group g : p.getGroups())
				System.out.println(g.getName());
		}
	}

	public RepetitiveMarkerFilterData getRepetitiveMarkerFilterData(ProjectPair pp) throws SQLException {
		if (pp == null) return null; // mdb added 7/23/07
			
		RepetitiveMarkerFilterData rmfd = null;

		synchronized (rmfdCache) {
			rmfd = (RepetitiveMarkerFilterData)rmfdCache.get(new RepetitiveMarkerFilterData(pp.getP1(),pp.getP2()));

			if (rmfd == null && mapperPool != null) {
				rmfd = mapperPool.getRepetitiveMarkerFilterData(pp);
				rmfdCache.add(rmfd);
			}

			if (rmfd == null) {
				ArrayList<String> fd = new ArrayList<String>();
				Statement stat = null;
				ResultSet rs = null;
				try {
					stat = createStatement();
					if (pp.getP1() == pp.getP2()) 
						rs = stat.executeQuery(setInt(setInt(RMFQ_SINGLE_PROJECT,pp.getP1()),pp.getMaxMarkerHits()));
					else
						rs = stat.executeQuery(setInt(setInt(setInt(setInt(REPETITIVE_MRK_FILTER_QUERY,pp.getP1()),
								pp.getMaxMarkerHits()),pp.getP2()),pp.getMaxMarkerHits()));
					while (rs.next())
						fd.add(rs.getString(1));
					rmfd = new RepetitiveMarkerFilterData(pp.getP1(),pp.getP2(),fd);
					rmfdCache.add(rmfd);
				}
				finally {
					closeStatement(stat);
					closeResultSet(rs);
					stat = null;
					rs = null;
					fd = null;
				}
			}
		}
		return rmfd;
	}
	
	private void setGroups(Project[] projects, 
			int[] xGroupIDs, int[] yGroupIDs, 	// mdb added 1/14/10 #207
			ProjectProperties pp) 				// mdb added 12/30/09 #206
	throws SQLException {
		Vector<Group> list = new Vector<Group>();
		Statement stat = createStatement();
		String qry;
		ResultSet rs;
		String groupList = null;
		
		for (Project p : projects) {
			// mdb added 1/14/10 #207
			if (xGroupIDs != null && yGroupIDs != null) {
				groupList = "(";
				if (p == projects[0])
					groupList += Utilities.getCommaSepString(xGroupIDs);
				else
					groupList += Utilities.getCommaSepString(yGroupIDs);
				groupList += ")";
			}
			
			if (p.isPseudo())
				qry = 
					"SELECT g.idx,g.sort_order,g.name,p.length " +
					"FROM groups AS g JOIN pseudos AS p ON (p.grp_idx=g.idx) " +
					"WHERE g.proj_idx=" + p.getID() +
					(groupList == null ? "" : " AND g.idx IN " + groupList) + // mdb added 1/14/10 #207
					" AND g.sort_order > 0 " +
					"ORDER BY g.sort_order";
			else // FPC
				qry = 
					"SELECT g.idx,g.sort_order,g.name,SUM(c.size) " +
					"FROM contigs AS c JOIN groups AS g ON (c.grp_idx=g.idx) " +
					"WHERE c.proj_idx=" + p.getID() +
					(groupList == null ? "" : " AND g.idx IN " + groupList) + // mdb added 1/14/10 #207
					" AND g.sort_order > 0 " +
					"GROUP BY g.idx ORDER BY g.sort_order";
			rs = stat.executeQuery(qry);
			while (rs.next()) {
				// mdb rewritten 1/15/10 #207 - reuse existing groups
				int idx = rs.getInt(1);
				Group g = p.getGroupByID(idx);
				if (g == null)
					g = new Group(idx,
							  rs.getInt(2),
							  rs.getString(3),
							  (p.isPseudo() ? rs.getInt(4) : rs.getInt(4) * p.getScale()),
							  p.getID());
				list.add(g);
			}
			p.setGroups( list.toArray(new Group[0]) );
			list.clear();
			closeResultSet(rs);
		}
		
		// mdb added 1/14/10 #207
		if (xGroupIDs != null && yGroupIDs != null)
			groupList = "(" + Utilities.getCommaSepString(xGroupIDs) + ", " +
							Utilities.getCommaSepString(yGroupIDs) + ")";
		
		// mdb added 12/14/09 #203 - identify groups with no blocks
		Project pX = projects[X];
		for (int i = 0;  i < projects.length;  i++) { // start at 0 (project X) to include self-alignments
			Project pY = projects[i];
			boolean swapped = pp.isSwapped(pX.getID(), pY.getID());
			
			qry = "SELECT b.grp1_idx,b.grp2_idx " +
				  "FROM blocks AS b " +
				  "JOIN groups AS g ON (g.idx=b.grp1_idx) " +
			      "WHERE (b.proj1_idx=" + (swapped ? pX.getID() : pY.getID()) +
			      " AND b.proj2_idx=" + (swapped ? pY.getID() : pX.getID()) + 
			      (groupList == null ? "" : " AND g.idx IN " + groupList) + // mdb added 1/14/10 #207
			      " AND g.sort_order > 0) " +
			      "GROUP BY b.grp1_idx,b.grp2_idx";
			rs = stat.executeQuery(qry);
			while (rs.next()) {
				int grp1_idx = rs.getInt(swapped ? 2 : 1);
				int grp2_idx = rs.getInt(swapped ? 1 : 2);
				Group g1 = pY.getGroupByID(grp1_idx);
				Group g2 = pX.getGroupByID(grp2_idx);
				if (g1 != null && g2 != null) {
					g1.setHasBlocks(g2);
					g2.setHasBlocks(g1);
				}
			}
			closeResultSet(rs);
		}
		
		closeStatement(stat);
	}

	private void setContigs(Project[] projects, 
			int[] xGroupIDs, int[] yGroupIDs) // mdb added 1/14/10 #207
	throws SQLException {
		Statement stat = createStatement();
		for (Project p : projects) {
			if (p.isFPC() && p.getNumGroups() > 0) { // FPC project
				// mdb added 1/14/10 #207
				String groupList = null;
				if (xGroupIDs != null && yGroupIDs != null) {
					groupList = "(";
					if (p == projects[0])
						groupList += Utilities.getCommaSepString(xGroupIDs);
					else
						groupList += Utilities.getCommaSepString(yGroupIDs);
					groupList += ")";
				}
				
				String qry = 
					"SELECT g.idx,c.idx,c.number,c.ccb,c.size "+
					"FROM groups AS g " +
					"JOIN contigs AS c ON (g.idx=c.grp_idx) "+
					"WHERE g.proj_idx=" + p.getID() +
					(groupList == null ? "" : " AND g.idx IN " + groupList) + // mdb added 1/14/10 #207
					" AND g.sort_order > 0 "+
					"ORDER BY c.number";
				ResultSet rs = stat.executeQuery(qry);
				while (rs.next())
					p.addContig(
							rs.getInt(1),
							rs.getInt(2),
							rs.getInt(3),
							rs.getInt(4) * p.getScale(), // mdb added scale factor 1/20/10 #205
							rs.getInt(5) * p.getScale());// mdb added scale factor 1/20/10 #205
				closeResultSet(rs);
			}
		}
		closeStatement(stat);
	}

	public static DatabaseReader getDatabaseReader(String appName) {
		DatabaseReader dr = null;
		try {
			dr = getDatabaseReader(appName,
					new PropertiesReader(DotPlotDBUser.class.getResource(DB_PROPS_FILE)));
		} catch (Exception e) {
			System.err.println("Problem loading default database properties file ["+DB_PROPS_FILE+"]");
			System.err.println("\t"+e.getMessage());
			dr = getDatabaseReader(appName,null,null,null,null);
		}
		return dr;
	}

	private static class ProjectHolder {
		private Project[] projects;

		public ProjectHolder(Project[] projects) {
			this.projects = projects;
		}

		public boolean equals(Object obj) {
			return obj instanceof ProjectHolder 
				&& java.util.Arrays.equals(projects,((ProjectHolder)obj).projects);
		}

		public String toString() {
			return "Projects ("+projects[0].getName()+","+projects[1].getName()+")";
		}

		public void copyInto(Project[] ps) {
			ps[0] = projects[0];
			ps[1] = projects[1];
		}
	}
}
