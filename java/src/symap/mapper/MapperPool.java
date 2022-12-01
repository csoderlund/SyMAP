package symap.mapper;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;

import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import symap.SyMAPConstants;
import symap.pool.DatabaseUser;
import symap.pool.ProjectProperties;
import symap.pool.ProjectPair;
import util.ErrorReport;
import util.ListCache;
import util.DatabaseReader;
import symap.track.Track;
import symap.sequence.Sequence;
import symap.SyMAP;

/**
 * The pool of Mapper hits.
 * The pool utilizes a ListCache.
 * CAS520 FPC is partially removed; FPC521 more FPC removed
 */
public class MapperPool extends DatabaseUser implements SyMAPConstants {
	private ProjectProperties projectProperties;
	private ListCache repetitiveMarkerFilterCache;
	private ListCache pseudoPseudoCache; 
	private boolean TRACE=SyMAP.DEBUG;

	public MapperPool(DatabaseReader dr, ProjectProperties pp, 
			ListCache fpcPseudoCache, ListCache repetitiveMarkerFilterCache, 
			ListCache fpcFpcCache, ListCache pseudoPseudoCache) {  
		super(dr);
		this.projectProperties = pp;
		this.pseudoPseudoCache = pseudoPseudoCache; 
	}

	public synchronized void close() {
		super.close();
	}

	public synchronized void clear() {
		super.close();
		if (pseudoPseudoCache != null) 			 pseudoPseudoCache.clear(); 
	}
	
	public boolean hasPair(Track t1, Track t2) {
		return projectProperties.hasProjectPair(t1.getProject(),t2.getProject());
	}

	public synchronized boolean setData(Mapper mapper, Track t1, Track t2,
			MapInfo mapinfo, HitFilter hf, List hits) throws SQLException 
	{
		MapInfo newMapInfo = new MapInfo(t1,t2,hf.isBlock(),hf.getNonRepetitive());
		
		if (newMapInfo.equalIfUpgradeHitContent(mapinfo)) {
			mapinfo.setHitContent(newMapInfo.getHitContent());
			return false;
		}
	
		if (mapinfo.getMapperType() != newMapInfo.getMapperType()) hits.clear();
		if (pseudoPseudoCache != null) pseudoPseudoCache.clear();
		
		hits.clear(); // prevent hit accumulation on pseudo-pseudo 
		
		if (t1 instanceof Sequence && t2 instanceof Sequence) { // PSEUDO to PSEUDO
			if (hasPair(t1,t2))
				setPseudoPseudoData(mapper,(Sequence)t1,(Sequence)t2,hits,mapinfo,newMapInfo);
			else // swap projects
				setPseudoPseudoData(mapper,(Sequence)t2,(Sequence)t1,hits,mapinfo,newMapInfo);
		}
		
		mapinfo.set(newMapInfo);

		return true;
	}

	private static final String PSEUDO_HITS_QUERY = 
			"SELECT h.idx, h.hitnum, h.pctid, h.start1, h.end1, h.start2, h.end2, h.strand," + // CAS520 change evalue to hitnum
			"h.gene_overlap, h.query_seq, h.target_seq, " +
			"h.cvgpct, h.countpct, " +    	// CAS515 add cvgpct, countpct,
			"h.runsize, h.runnum, " +		// CAS520 add runnum
			"b.corr, b.blocknum " +  	  	// CAS516 add corr; CAS504 bh.block_idx -> b.blocknum
			"FROM pseudo_hits AS h "+
			"LEFT JOIN pseudo_block_hits AS bh ON (bh.hit_idx=h.idx) "+
			"LEFT JOIN blocks as b on (b.idx=bh.block_idx) " + // CAS505 added for blocknum
			"WHERE h.grp1_idx=? AND h.grp2_idx=? order by h.start1"; // CAS520 add order by
	/*************************************************************************
	 * Update HitData
	 */
	private void setPseudoPseudoData(Mapper mapper, Sequence st1, Sequence st2, 
			List hits, MapInfo mi, MapInfo nmi) throws SQLException 
	{
		int i;
		PseudoPseudoData data/*, tempData*/;
		boolean reorder = false;
		
		int stProject1 = st1.getProject();
		int stProject2 = st2.getProject();
		int group1 = st1.getGroup();
		int group2 = st2.getGroup();
		String chr1="?", chr2="?";

		ProjectPair pp = projectProperties.getProjectPair(stProject1,stProject2);

		if (TRACE) System.out.println("Looking for the Hits for Pseudos: p1="+stProject1+" p2="+stProject2+" pair="+pp.getPair()+" g1="+group1+" g2="+group2);
		List<HitData> hitList = new LinkedList<HitData>();
		Statement statement;
		ResultSet rs;
		String query, tag;
		try {
			statement = createStatement();
			
			rs = statement.executeQuery("select name from xgroups where idx=" + group1); // CAS517 add chrname for display
			if (rs.next()) chr1=rs.getString(1);
			rs = statement.executeQuery("select name from xgroups where idx=" + group2);
			if (rs.next()) chr2=rs.getString(1);
			
			data = new PseudoPseudoData(stProject1,group1,stProject2,
					group2,nmi.getHitContent(),hitList,reorder);
		
			query = PSEUDO_HITS_QUERY;
			query = setInt(query,group1);	
			query = setInt(query,group2);
			
			rs = statement.executeQuery(query);
			/*
		  	1 h.idx, 2 h.hitnum, 3 h.pctid, 4 h.start1, 5 h.end1, 6 h.start2, 7 h.end2, 8 h.strand,
		 	9 h.gene_overlap, 10 h.query_seq, 11 h.target_seq, 12 h.cvgpct, 13 h.countpct, 
			14 h.runsize, 15 h.runnum, 16 b.corr, 17 b.blocknum 
			 */
			while (rs.next()) {
				tag    = "g" + rs.getInt(9); // CAS516 gene_overlap, runsize; 
				int runsize = rs.getInt(14);
				int runnum  = rs.getInt(15);
				if (runsize>0 && runnum>0) tag += " c" + runsize + "." + runnum; // CAS520 add runnum
				else if (runsize>0)        tag += " c" + runsize;			    // parsed in Utilities.isCollinear
		
				HitData temp = 		PseudoPseudoData.getHitData(
						rs.getLong(1),		/* long id 		*/
						rs.getInt(2),		/* int hitnum 	*/
						rs.getString(8),	/* String strand*/
						0,					/* int repetitive*/
						rs.getInt(17),		/* int block	*/
						rs.getDouble(3),	/* double pctid	*/
						rs.getInt(4),		/* int start1	*/
						rs.getInt(5),		/* int end1		*/
						rs.getInt(6),		/* int start2	*/
						rs.getInt(7),		/* int end2		*/
						rs.getInt(9),		/* int gene_overlap  */ 		
						rs.getString(10),	/* String query_seq */	
						rs.getString(11),	/* String target_seq */	
						rs.getInt(12),		/* int cvgpct->avg %sim */
						rs.getInt(13),      /* int countpct -> nMergedHits */
						rs.getDouble(16),	/* CAS516 b.corr */
						tag, chr1, chr2		/* CAS517 add chr1, chr2 */
						);		
				hitList.add(temp);
			}
			closeResultSet(rs);
			
			data.addHitData(nmi.getHitContent(),hitList);
			
			i = hits.indexOf(data);
			if (i < 0) hits.add(new PseudoPseudoHits(mapper,st1,st2,data,reorder));
			else       ((PseudoPseudoHits)hits.get(i)).addHits(nmi.getHitContent(),hitList);
			
			hitList.clear();
			if (pseudoPseudoCache != null) pseudoPseudoCache.add(data);
		
			closeStatement(statement);
		} catch (SQLException e) {
			close();
			ErrorReport.print(e, "Get hit data");
			throw e;
		}
	}

	public static final String REPETITIVE_MRK_FILTER_QUERY =
		"SELECT DISTINCT m.name "+
		"FROM markers as m "+
		"WHERE (SELECT COUNT(*) "+
		"       FROM mrk_ctg as mc1 "+
		"       WHERE m.proj_idx=? AND mc1.mrk_idx=m.idx) > ? OR "+
		"      (SELECT COUNT(*) "+
		"       FROM mrk_ctg as mc2 "+
		"       WHERE m.proj_idx=? AND mc2.mrk_idx=m.idx) > ?";

	public static final String RMFQ_SINGLE_PROJECT =
		"SELECT m.name "+
		"FROM markers AS m "+
		"WHERE m.proj_idx=? AND (SELECT COUNT(*) FROM mrk_ctg AS mc WHERE mc.mrk_idx=m.idx) > ? ";

	
	public RepetitiveMarkerFilterData getRepetitiveMarkerFilterData(ProjectPair pp) throws SQLException {
		RepetitiveMarkerFilterData p = null;
		
		if (repetitiveMarkerFilterCache != null)
			p = (RepetitiveMarkerFilterData) repetitiveMarkerFilterCache.get(new RepetitiveMarkerFilterData(pp.getP1(), pp.getP2()));

		if (p != null) return p;

		ArrayList<String> fd = new ArrayList<String>();
		Statement stat = null;
		ResultSet rs = null;
		try {
			stat = createStatement();

			if (pp.getP1() == pp.getP2()) rs = stat.executeQuery(setInt(setInt(RMFQ_SINGLE_PROJECT,pp.getP1()),pp.getMaxMarkerHits()));
			else
				rs = stat.executeQuery(setInt(setInt(setInt(setInt(REPETITIVE_MRK_FILTER_QUERY,pp.getP1()),
						pp.getMaxMarkerHits()),pp.getP2()),pp.getMaxMarkerHits()));

			while (rs.next())
				fd.add(rs.getString(1));

			p = new RepetitiveMarkerFilterData(pp.getP1(),pp.getP2(),fd);
			if (repetitiveMarkerFilterCache != null) repetitiveMarkerFilterCache.add(p);
		}
		finally {
			closeStatement(stat);
			closeResultSet(rs);
			stat = null;
			rs = null;
			fd = null;
		}
		return p;
	}

}
