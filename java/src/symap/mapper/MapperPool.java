package symap.mapper;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.ListIterator;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import symap.SyMAP;
import symap.SyMAPConstants;
import symap.pool.DatabaseUser;
import symap.pool.ProjectProperties;
import symap.pool.ProjectPair;
import util.ListCache;
import util.DatabaseReader;
import symap.track.Track;
import symap.sequence.Sequence;
import symap.marker.MarkerTrack;

/**
 * The pool of Mapper hits.
 * 
 * The pool utilizes a ListCache.
 * 
 * @author Austin Shoemaker
 */
public class MapperPool extends DatabaseUser implements SyMAPConstants {
	//private static final boolean TIME_TRACE = false;
	
	private static final String C1_INSERT = "C1_INSERT";
	private static final String C2_INSERT = "C2_INSERT";
	private static final String C1_RVALUE = "c1.number";
	private static final String C2_RVALUE = "c2.number";

	private static final String PSEUDO_MRK_BLOCK_APPEND         = "(mb.hit_idx IS NOT NULL AND mb.hit_idx > 0)";
	private static final String PSEUDO_MRK_NON_BLOCK_APPEND     = "(mb.hit_idx IS NULL OR mb.hit_idx = 0)";
	private static final String PSEUDO_MRK_NONREPETITIVE_APPEND = // also gets markers that are themselves repetitive
		"((mf.filter_code IS NULL OR mf.filter_code = 0) AND (pf.filter_code IS NULL OR pf.filter_code = 0))";
	private static final String PSEUDO_MRK_REPETITIVE_APPEND    = // doesn't get all non repetitive, but inverse from above
		"((mf.filter_code IS NOT NULL AND mf.filter_code > 0) OR (pf.filter_code IS NOT NULL AND pf.filter_code > 0))";   

	private static final String PSEUDO_BES_BLOCK_APPEND         = "(b.ctghit_idx IS NOT NULL AND b.ctghit_idx > 0)";
	private static final String PSEUDO_BES_NON_BLOCK_APPEND     = "(b.ctghit_idx IS NULL OR b.ctghit_idx = 0)";
	private static final String PSEUDO_BES_NONREPETITIVE_APPEND = 
		"((bf.filter_code IS NULL OR bf.filter_code = 0) AND (pf.filter_code IS NULL OR pf.filter_code = 0))";
	private static final String PSEUDO_BES_REPETITIVE_APPEND    = 
		"((bf.filter_code IS NOT NULL AND bf.filter_code > 0) OR (pf.filter_code IS NOT NULL AND pf.filter_code > 0))";  

	private static final String PSEUDO_MRKHITS_QUERY =
		"SELECT h.idx,h.marker,mf.filter_code,pf.filter_code,mb.hit_idx,"+
		"       h.evalue,h.pctid,h.start2,h.end2,h.strand,"+
		"		h.query_seq,h.target_seq,h.gene_overlap "+ // mdb added query_seq/target_seq 8/22/07 #126 
		"FROM contigs AS c "+
		"INNER JOIN mrk_ctg        AS mc ON (mc.ctg_idx=c.idx) "+
		"INNER JOIN markers        AS m  ON (m.idx=mc.mrk_idx) "+
		"INNER JOIN mrk_hits       AS h  ON (h.proj1_idx=? AND h.grp2_idx=? AND h.marker=m.name) "+
		"LEFT  JOIN mrk_filter     AS mf ON (mf.mrk_idx=m.idx AND mf.proj2_idx=?) "+
		"LEFT  JOIN pseudo_filter  AS pf ON (pf.proj1_idx=? AND pf.grp2_idx=? AND "+
		"                                              (h.end2+h.start2)>>1 >= pf.start AND (h.end2+h.start2)>>1 <= pf.end) "+
		"LEFT  JOIN mrk_block_hits AS mb ON (mb.hit_idx=h.idx AND "+
		"                                              mb.ctghit_idx IN (SELECT ch.idx "+
		"                                                                FROM ctghits AS ch "+
		"                                                                WHERE ch.ctg1_idx=c.idx AND ch.grp2_idx=?)) "+
		"WHERE c.proj_idx=? AND c.number=?";

	private static final String PSEUDO_BESHITS_QUERY = 
		"SELECT h.idx,h.clone,h.bes_type,bf.filter_code,pf.filter_code,b.ctghit_idx,"+
		"       h.evalue,h.pctid,h.start2,h.end2,cl.cb1,cl.cb2,cl.bes1,cl.bes2,h.strand,"+
		"		h.query_seq,h.target_seq,h.gene_overlap "+ // mdb added query_seq/target_seq 8/22/07 #126 
		"FROM contigs AS co "+
		"INNER JOIN clones AS cl ON cl.ctg_idx=co.idx "+
		"INNER JOIN bes_hits AS h  ON (h.proj1_idx=? AND h.proj2_idx=? AND h.grp2_idx=? AND h.clone=cl.name) "+
		"LEFT  JOIN bes_block_hits AS b ON (b.hit_idx=h.idx) "+
		"LEFT  JOIN bes_filter AS bf ON (bf.clone_idx=cl.idx AND bf.proj2_idx=? AND bf.bes_type=h.bes_type) "+
		"LEFT  JOIN pseudo_filter AS pf ON (pf.proj1_idx=? AND pf.grp2_idx=? AND "+
		"                                            (h.start2+h.end2)>>1 >= pf.start AND (h.start2+h.end2)>>1 <= pf.end) "+
		"WHERE co.proj_idx=? AND co.number=?";

	// mdb added PSEUDO_HITS_QUERY 7/11/07 #121
	private static final String PSEUDO_HITS_QUERY = 
		"SELECT h.idx,h.evalue,h.pctid,h.start1,h.end1,h.start2,h.end2,h.strand,bh.block_idx," +
		"h.gene_overlap," + // mdb added 2/19/08 #150
		"h.query_seq,h.target_seq " + // mdb added 4/17/09 #126
		"FROM pseudo_hits AS h "+
		"LEFT JOIN pseudo_block_hits AS bh ON (bh.hit_idx=h.idx) "+
		"WHERE h.grp1_idx=? AND h.grp2_idx=?";
	
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

	private static final String SHARED_MRK_BLOCK_QUERY =
		"SELECT c1.number,c2.number,m.name "+
		"FROM shared_mrk_block_hits AS b,markers AS m,ctghits AS ch,"+
		"     contigs AS c1,contigs AS c2 "+
		"WHERE c1.proj_idx=? AND "+C1_INSERT+" AND c1.idx=ch.ctg1_idx AND c2.proj_idx=? AND "+C2_INSERT+" AND c2.idx=ch.ctg2_idx AND "+
		"      ch.idx=b.ctghit_idx AND m.idx=b.mrk_idx AND m.proj_idx=? "+
		"ORDER BY c1.number,c2.number";

	private static final String FPC_QUERY_POST_APPEND = " ORDER BY c1.number,c2.number ";

	private static final String FPC_FP_BLOCK_APPEND         = "(b.ctghit_idx IS NOT NULL AND b.ctghit_idx > 0)";
	private static final String FPC_FP_NON_BLOCK_APPEND     = "(b.ctghit_idx IS NULL OR b.ctghit_idx = 0)";
	private static final String FPC_FP_NONREPETITIVE_APPEND = 
		"((f1.filter IS NULL OR f1.filter = 0) AND (f2.filter IS NULL OR f2.filter = 0))";
	private static final String FPC_FP_REPETITIVE_APPEND    = 
		"((f1.filter IS NOT NULL AND f1.filter > 0) OR (f2.filter IS NOT NULL AND f2.filter > 0))"; 

	private static final String FP_HITS_QUERY =
		"SELECT c1.number,c2.number,cl1.name,cl2.name,(cl1.cb1+cl1.cb2)>>1 AS pos1,(cl2.cb1+cl2.cb2)>>1 AS pos2,"+
		"       h.score,f1.filter,f2.filter,b.ctghit_idx "+
		//"FROM "+CONTIGS_TABLE+" AS c1,"+CLONES_TABLE+" AS cl1,"+FP_HITS_TABLE+" AS h,"+CONTIGS_TABLE+" AS c2,"+CLONES_TABLE+" AS cl2 "+ // mdb removed 3/27/07 #110
		"FROM contigs AS c1 JOIN clones AS cl1 JOIN fp_hits AS h JOIN contigs AS c2 JOIN clones AS cl2 "+ // mdb added 3/27/07 #110
		"LEFT  JOIN fp_block_hits AS b ON (b.hit_idx=h.idx) "+
		"LEFT JOIN fp_filter AS f1 ON (f1.clone1_idx=cl1.idx AND f1.proj2_idx=?) "+
		"LEFT JOIN fp_filter AS f2 ON (f2.clone1_idx=cl2.idx AND f2.proj2_idx=?) "+
		"WHERE h.proj1_idx=? AND h.proj2_idx=? AND "+
		"      c1.proj_idx=? AND "+C1_INSERT+" AND cl1.proj_idx=? AND cl1.ctg_idx=c1.idx AND h.clone1=cl1.name AND "+
		"      c2.proj_idx=? AND "+C2_INSERT+" AND cl2.proj_idx=? AND cl2.ctg_idx=c2.idx AND h.clone2=cl2.name ";

	private ProjectProperties projectProperties;
	private ListCache fpcPseudoCache, repetitiveMarkerFilterCache, fpcFpcCache;
	private ListCache pseudoPseudoCache; // mdb added 7/23/07 #121

	public MapperPool(DatabaseReader dr, ProjectProperties pp, 
			ListCache fpcPseudoCache, ListCache repetitiveMarkerFilterCache, 
			ListCache fpcFpcCache, ListCache pseudoPseudoCache) { // mdb added pseudoPseudoCache 7/23/07 #121 
		super(dr);
		this.projectProperties = pp;
		this.fpcPseudoCache = fpcPseudoCache;
		this.repetitiveMarkerFilterCache = repetitiveMarkerFilterCache;
		this.fpcFpcCache = fpcFpcCache;
		this.pseudoPseudoCache = pseudoPseudoCache; // mdb added 7/23/07 #121
	}

	public synchronized void close() {
		super.close();
	}

	public synchronized void clear() {
		super.close();
		if (fpcPseudoCache != null) 			 fpcPseudoCache.clear();
		if (repetitiveMarkerFilterCache != null) repetitiveMarkerFilterCache.clear();
		if (fpcFpcCache != null) 				 fpcFpcCache.clear();
		if (pseudoPseudoCache != null) 			 pseudoPseudoCache.clear(); // mdb added 7/23/07 #121
	}
	
	// mdb added 4/2/09 #160
	public boolean hasPair(Track t1, Track t2) {
		return projectProperties.hasProjectPair(t1.getProject(),t2.getProject());
	}

	public synchronized boolean setData(Mapper mapper, Track t1, Track t2,
			MapInfo mapinfo, HitFilter hf, List hits) throws SQLException 
	{
		MapInfo newMapInfo = new MapInfo(t1,t2,hf.getBlock(),hf.getNonRepetitive());
		if (newMapInfo.equalIfUpgradeHitContent(mapinfo)) {
			if (SyMAP.DEBUG) System.out.println("New Map Info equal with upgrade. New Hit Content = "+newMapInfo.getHitContent()+
					" Old Hit Content = "+mapinfo.getHitContent());
			mapinfo.setHitContent(newMapInfo.getHitContent());
			return false;
		}

		if (mapinfo.getMapperType() != newMapInfo.getMapperType()) hits.clear();

		/* mdb removed 7/23/07 #121
		if (newMapInfo.getMapperType() == Mapper.FPC2PSEUDO) {
			//repetitiveMarkerFilterCache.clear();
			fpcFpcCache.clear();
		}
		else {
			fpcPseudoCache.clear();
		}*/
		
		// mdb clear all caches, added 7/23/07 #121
		if (fpcFpcCache != null) 	   fpcFpcCache.clear();
		if (fpcPseudoCache != null)    fpcPseudoCache.clear();
		if (pseudoPseudoCache != null) pseudoPseudoCache.clear();
		
		hits.clear(); // mdb tempfix added to prevent hit accumulation on pseudo-pseudo 8/17/07 #121
		
		// mdb added pseudo-to-pseudo case 7/11/07 #121
		if (t1 instanceof Sequence && t2 instanceof Sequence) { // PSEUDO to PSEUDO
			// mdb added 4/1/09 #160 - fix pseudo-pseudo query/target ordering problem
			if (hasPair(t1,t2))
				setPseudoPseudoData(mapper,(Sequence)t1,(Sequence)t2,hits,mapinfo,newMapInfo);
			else // swap projects
				setPseudoPseudoData(mapper,(Sequence)t2,(Sequence)t1,hits,mapinfo,newMapInfo);
		}
		else if (t1 instanceof Sequence || t2 instanceof Sequence) { // FPC to PSEUDO
			if (t1 instanceof Sequence)
				setFPCPseudoData(mapper,(MarkerTrack)t2,(Sequence)t1,hits,mapinfo,newMapInfo);
			else
				setFPCPseudoData(mapper,(MarkerTrack)t1,(Sequence)t2,hits,mapinfo,newMapInfo);
		}
		else { // FPC to FPC
			setFPCFPCData(mapper,(MarkerTrack)t1,(MarkerTrack)t2,hits,mapinfo,newMapInfo);
		}

		mapinfo.set(newMapInfo);

		return true;
	}

	// mdb added 7/11/07 #121
	private void setPseudoPseudoData(Mapper mapper, Sequence st1, Sequence st2, 
			List hits, MapInfo mi, MapInfo nmi) throws SQLException 
	{
		//long start = System.currentTimeMillis();
		int i;
		PseudoPseudoData data/*, tempData*/;
//		PseudoPseudoHits h;
		int stProject1, stProject2;
		int group1, group2;	
		boolean reorder = false;
		
		// Reorder projects to match order in database so that grp1_idx/grp2_idx
		// and start1/end1/start2/end2 fields are correct.

// mdb removed 4/1/09 #160 - fix pseudo-pseudo query/target ordering
//		if ((st1.getPosition() % 2) == 0) {	// is even?		
//			reorder = true;
//			stProject1 = st2.getProject();
//			stProject2 = st1.getProject();
//			group1 = st2.getGroup();
//			group2 = st1.getGroup();	
//		}
//		else {
//			reorder = false;
			stProject1 = st1.getProject();
			stProject2 = st2.getProject();
			group1 = st1.getGroup();
			group2 = st2.getGroup();
//		}
		if (SyMAP.DEBUG) System.out.println("pos1="+st1.getPosition()+" pos2="+st2.getPosition()/*+" Reorder projects = "+reorder*/);
		
// FIXME: finish this pseudo-pseudo hit caching code
//		List neededDataList = PseudoPseudoData.getPseudoPseudoData(stProject1,stProject2,group1,group2);
//		hits.retainAll(neededDataList);
//		ListIterator iter = neededDataList.listIterator();
//		while (iter.hasNext()) {
//			tempData = (PseudoPseudoData)iter.next();
//			i = hits.indexOf(tempData);
//			if (i >= 0) {
//				h = (PseudoPseudoHits)hits.get(i);
//				h.set(mt,st);
//				if (h.hasHitContent(nmi.getHitContent())) iter.remove();
//				else if (pseudoPseudoCache != null) {
//					data = (PseudoPseudoData)pseudoPseudoCache.get(tempData);
//					if (data != null) {
//						if (!h.setHits(data)) data.setHitData(h);
//						if (h.hasHitContent(nmi.getHitContent())) iter.remove();
//						else iter.set(data);
//					}
//				}
//			}
//			else if (pseudoPseudoCache != null) {
//				data = (PseudoPseudoData)pseudoPseudoCache.get(tempData);
//				if (data != null) {
//					if (data.hasHitContent(nmi.getHitContent())) {
//						iter.remove();
//						hits.add(new PseudoPseudoHits(mapper,mt,st,data));
//					}
//					else iter.set(data);
//				}
//			}
//		}
//
//		if (!neededDataList.isEmpty()) {
			ProjectPair pp = projectProperties.getProjectPair(stProject1,stProject2);
//			RepetitiveMarkerFilterData rmfd = getRepetitiveMarkerFilterData(pp);

			if (SyMAP.DEBUG) System.out.println("Looking for the Hits for Pseudos: p1="+stProject1+" p2="+stProject2+" pair="+pp.getPair()+" g1="+group1+" g2="+group2);
			List<HitData> hitList = new LinkedList<HitData>();
			Statement statement;
			ResultSet rs;
			String query;
			try {
				statement = createStatement();
				//for (iter = neededDataList.listIterator(); iter.hasNext(); ) {
					//data = (PseudoPseudoData)iter.next();
					data = new PseudoPseudoData(stProject1,group1,stProject2,
							group2,nmi.getHitContent(),hitList,reorder);
				
					query = PSEUDO_HITS_QUERY;
					//query = setInt(query,pp.getPair());
					query = setInt(query,group1);	
					query = setInt(query,group2);
					
					//long cStart = System.currentTimeMillis();
					rs = statement.executeQuery(query);
					//if (TIME_TRACE) System.out.println("MapperPool: pseudo hits query time = "+(System.currentTimeMillis()-cStart)+" ms");

					int start1, end1, start2, end2;
					
					while (rs.next()) {
// mdb removed 4/1/09 #160						
//						if (reorder) {
//							start1 = rs.getInt(6);
//							end1   = rs.getInt(7);
//							start2 = rs.getInt(4);
//							end2   = rs.getInt(5);
//						}
//						else {
							start1 = rs.getInt(4);
							end1   = rs.getInt(5);
							start2 = rs.getInt(6);
							end2   = rs.getInt(7);
//						}
							HitData temp = 							PseudoPseudoData.getHitData(
									rs.getLong(1),		/* long id 		*/
									null,				/* String name 	*/
									rs.getString(8),	/* String strand*/
									0,					/* int repetitive*/
									rs.getInt(9),		/* int block	*/
									rs.getDouble(2),	/* double evalue*/
									rs.getDouble(3),	/* double pctid	*/
									start1,				/* int start1	*/
									end1,				/* int end1		*/
									start2,				/* int start2	*/
									end2,				/* int end2		*/
									rs.getInt(10),		/* int overlap  */ 		// mdb added 2/19/08 #150
									rs.getString(11),	/* String query_seq */	// mdb added 4/17/09 #126
									rs.getString(12));	/* String target_seq */	// mdb added 4/17/09 #126

							
						hitList.add(temp);
					}
					closeResultSet(rs);

					if (SyMAP.DEBUG) System.out.println("MapperPool.setPseudoPseudoData hitList.size="+hitList.size());

// mdb removed 8/4/09 - unused					
//					// mdb added 2/1/08 --------------------------------- BEGIN
//					java.util.Collections.sort(hitList, 
//							new Comparator() {
//								public int compare(Object o1, Object o2) {
//									return (int)(((HitData)o1).getStart1() - ((HitData)o2).getStart1());
//								}
//					});
//					HitData lastHit = null;
//					for (HitData hit : hitList) {
//						if (lastHit != null) {
//							int diff = hit.getStart1() - lastHit.getEnd1();
//							hit.setPrevDist1(diff);
//							lastHit.setNextDist1(diff);
//						}
//						lastHit = hit;
//					}
//					java.util.Collections.sort(hitList, 
//							new Comparator() {
//								public int compare(Object o1, Object o2) {
//									return (int)(((HitData)o1).getStart2() - ((HitData)o2).getStart2());
//								}
//					});
//					lastHit = null;
//					for (HitData hit : hitList) {
//						if (lastHit != null) {
//							int diff = hit.getStart2() - lastHit.getEnd2();
//							hit.setPrevDist2(diff);
//							lastHit.setNextDist2(diff);
//						}
//						lastHit = hit;
//					}
//					// mdb added 2/1/08 ----------------------------------- END
					
					data.addHitData(nmi.getHitContent(),hitList);
					
					i = hits.indexOf(data);
					if (i < 0) hits.add(new PseudoPseudoHits(mapper,st1,st2,data,reorder));
					else       ((PseudoPseudoHits)hits.get(i)).addHits(nmi.getHitContent(),hitList);
					
					hitList.clear();
					if (pseudoPseudoCache != null) pseudoPseudoCache.add(data);
				//}
				closeStatement(statement);
			} catch (SQLException e) {
				close();
				throw e;
			}
//		}
		//if (TIME_TRACE) System.out.println("MapperPool: setPseudoPseudoData() time = "+(System.currentTimeMillis()-start)+" ms");
	}

	private void setFPCPseudoData(Mapper mapper, MarkerTrack mt, Sequence st, List hits, MapInfo mi, MapInfo nmi) throws SQLException {
		int i;
		FPCPseudoData data, tempData;
		FPCPseudoHits h;
		int mtProject = mt.getProject();
		int stProject = st.getProject();
		int group = st.getGroup();
		List neededDataList = FPCPseudoData.getFPCPseudoData(mtProject,stProject,group,nmi.getContigList());

		hits.retainAll(neededDataList);

		ListIterator iter = neededDataList.listIterator();
		while (iter.hasNext()) {
			tempData = (FPCPseudoData)iter.next();
			i = hits.indexOf(tempData);
			if (i >= 0) {
				h = (FPCPseudoHits)hits.get(i);
				h.set(mt,st);
				if (h.hasHitContent(nmi.getHitContent())) iter.remove();
				else if (fpcPseudoCache != null) {
					data = (FPCPseudoData)fpcPseudoCache.get(tempData);
					if (data != null) {
						if (!h.setHits(data)) data.setHitData(h);
						if (h.hasHitContent(nmi.getHitContent())) iter.remove();
						else iter.set(data);
					}
				}
			}
			else if (fpcPseudoCache != null) {
				data = (FPCPseudoData)fpcPseudoCache.get(tempData);
				if (data != null) {
					if (data.hasHitContent(nmi.getHitContent())) {
						iter.remove();
						hits.add(new FPCPseudoHits(mapper,mt,st,data));
					}
					else iter.set(data);
				}
			}
		}

		if (!neededDataList.isEmpty()) {
			ProjectPair pp = projectProperties.getProjectPair(mtProject,stProject);
			RepetitiveMarkerFilterData rmfd = getRepetitiveMarkerFilterData(pp);

			if (SyMAP.DEBUG) System.out.println("Looking for the Hits for Contigs: "+neededDataList);
			List mrkHitList = new LinkedList();
			List besHitList = new LinkedList();
			Statement statement;
			ResultSet rs;
			String query;
			try {
				statement = createStatement();
				for (iter = neededDataList.listIterator(); iter.hasNext(); ) {
					data = (FPCPseudoData)iter.next();

					query = getPseudoMarkerQuery(data.getHitContent(),nmi.getHitContent());
					query = setInt(query,mtProject);
					query = setInt(query,group);
					query = setInt(query,stProject);
					query = setInt(query,mtProject);
					query = setInt(query,group);
					query = setInt(query,group);
					query = setInt(query,mtProject);
					//query = setInt(query,data.getContig()); // mdb removed 7/25/07 #134
					query = setInt(query,data.getContent1()); // mdb added 7/25/07 #134

					rs = statement.executeQuery(query);
					while (rs.next()) {
						mrkHitList.add(
								data.getMarkerData(
										rs.getLong(1), 				/* long id		*/
										rs.getString(2),			/* String name	*/
										rs.getString(10), 			/* String strand*/
										rs.getInt(3) + rs.getInt(4),/* int repetitive*/ 
										rmfd, 						/* RepetitiveMarkerFilterData rmfd*/
										rs.getLong(5), 				/* long block	*/
										rs.getDouble(6), 			/* double evalue*/
										rs.getDouble(7), 			/* double pctid	*/
										rs.getInt(8),				/* int start2	*/
										rs.getInt(9),				/* int end2		*/
										rs.getString(11),			/* String query_seq  - mdb added 8/22/07 #126 */
										rs.getString(12),			/* String query_seq  - mdb added 8/22/07 #126 */
										rs.getInt(13)
										)
									);
					}
					closeResultSet(rs);

					query = getPseudoBESQuery(data.getHitContent(),nmi.getHitContent());
					query = setInt(query,mtProject);
					query = setInt(query,stProject);
					query = setInt(query,group);
					query = setInt(query,stProject);
					query = setInt(query,mtProject);
					query = setInt(query,group);
					query = setInt(query,mtProject);
					//query = setInt(query,data.getContig()); // mdb removed 7/25/07 #134
					query = setInt(query,data.getContent1()); // mdb added 7/25/07 #134

					rs = statement.executeQuery(query);
					while (rs.next()) {
						besHitList.add(
								data.getBESData(
										rs.getLong(1),					/* long id 		*/
										rs.getString(2),				/* String name 	*/
										getBESValue(rs.getString(3)),	/* byte bes		*/
										rs.getString(15),				/* String strand*/
										rs.getInt(4)+rs.getInt(5),		/* int repetitive*/
										rs.getInt(6),					/* int block	*/
										rs.getDouble(7),				/* double evalue*/
										rs.getDouble(8),				/* double pctid	*/
										rs.getInt(9),					/* int start2	*/
										rs.getInt(10),					/* int end2		*/
										rs.getInt(11),					/* int cb1		*/
										rs.getInt(12),					/* int cb2		*/
										getBESValue(rs.getString(13)),	/* byte bes1	*/
										getBESValue(rs.getString(14)),	/* byte bes2	*/
										rs.getString(16),				/* String query_seq  - mdb added 8/22/07 #126 */
										rs.getString(17),				/* String target_seq - mdb added 8/22/07 #126 */
										rs.getInt(18)));
					}
					closeResultSet(rs);

					data.addHitData(nmi.getHitContent(),mrkHitList,besHitList);

					i = hits.indexOf(data);
					if (i < 0) hits.add(new FPCPseudoHits(mapper,mt,st,data));
					else       ((FPCPseudoHits)hits.get(i)).addHits(nmi.getHitContent(),mrkHitList,besHitList);

					mrkHitList.clear();
					besHitList.clear();

					if (fpcPseudoCache != null) fpcPseudoCache.add(data);
				}
				closeStatement(statement);
			} catch (SQLException e) {
				close();
				throw e;
			}
		}
	}

	private void setFPCFPCData(Mapper mapper, MarkerTrack t1, MarkerTrack t2, List hits, MapInfo mi, MapInfo nmi) throws SQLException {
		ProjectPair pp = projectProperties.getProjectPair(t1.getProject(),t2.getProject());
		RepetitiveMarkerFilterData smfd = null;
		if (pp.getP1() != t1.getProject()) {
			MarkerTrack temp = t1;
			t1 = t2;
			t2 = temp;
		}

		FPCFPCData data, tempData;
		FPCFPCHits h;
		int i,j;

		int contigs1[] = t1.getContigs();
		int contigs2[] = t2.getContigs();

		Arrays.sort(contigs1);
		Arrays.sort(contigs2);

		List neededDataList = new LinkedList();
		for (i = 0; i < contigs1.length; i++)
			for (j = 0; j < contigs2.length; j++)
				neededDataList.add(new FPCFPCData(pp.getP1(),contigs1[i],pp.getP2(),contigs2[j]));

		hits.retainAll(neededDataList);

		ListIterator iter = neededDataList.listIterator();
		while (iter.hasNext()) {
			tempData = (FPCFPCData)iter.next();
			i = hits.indexOf(tempData);
			if (i >= 0) {
				h = (FPCFPCHits)hits.get(i);
				h.set(t1,t2);
				if (h.hasHitContent(nmi.getHitContent())) iter.remove();
				else if (fpcFpcCache != null) {
					data = (FPCFPCData)fpcFpcCache.get(tempData);
					if (data != null) {
						if (!h.setHits(data,null)) data.setHitData(h);
						if (h.hasHitContent(nmi.getHitContent())) iter.remove();
						else iter.set(data);
					}
				}
			}
			else if (fpcFpcCache != null) {
				data = (FPCFPCData)fpcFpcCache.get(tempData);
				if (data != null) {
					if (data.hasHitContent(nmi.getHitContent())) {
						iter.remove();
						if (smfd == null && nmi.getHitContent() > MapInfo.ONLY_BLOCK_HITS) smfd = getRepetitiveMarkerFilterData(pp);
						hits.add(new FPCFPCHits(mapper,t1,t2,data,smfd));
					}
					else iter.set(data);
				}
			}
		}

		if (!neededDataList.isEmpty()) {
			if (SyMAP.DEBUG) System.out.println("Looking for "+neededDataList+" from database.");

			if (smfd == null && nmi.getHitContent() > MapInfo.ONLY_BLOCK_HITS) smfd = getRepetitiveMarkerFilterData(pp);

			setBlockHitMarkers(pp.getP1(),pp.getP2(),FPCFPCData.getNeededBlockDataList(neededDataList));

			List[] dataLists = FPCFPCData.splitOnHitContentAndClear(neededDataList);
			for (i = 0; i < dataLists.length; i++)
				setFPHits(pp.getP1(),pp.getP2(),mapper,t1,t2,dataLists[i],hits,smfd,i,nmi.getHitContent());
		}
	}

	private void setFPHits(int p1, int p2, Mapper mapper, MarkerTrack t1,
			MarkerTrack t2, List dataList, List hits,
			RepetitiveMarkerFilterData smfd, int hitContent, int newHitContent)
			throws SQLException 
	{
		if (dataList == null || dataList.isEmpty())
			return;

		Iterator iter;
		FPCFPCData data;
		Statement stat = null;
		ResultSet rs = null;
		int c1,c2;
		List fpHits = new LinkedList();

		int contigs1[] = new int[dataList.size()];
		int contigs2[] = new int[dataList.size()];
		iter = dataList.iterator();
		for (int i = 0; i < contigs1.length; i++) {
			data = (FPCFPCData)iter.next();
			contigs1[i] = data.getContig1();
			contigs2[i] = data.getContig2();
		}

		try {
			stat = createStatement();
			String query = getFPCFPHitsQuery(hitContent,newHitContent);
			query = setInt(query,p2); 
			query = setInt(query,p1);
			query = setInt(query,p1);
			query = setInt(query,p2);
			query = setInt(query,p1);
			query = setInsert(query,C1_INSERT,C1_RVALUE,contigs1);
			query = setInt(query,p1);
			query = setInt(query,p2);
			query = setInsert(query,C2_INSERT,C2_RVALUE,contigs2);
			query = setInt(query,p2);

			rs = stat.executeQuery(query);
			iter = dataList.iterator();
			data = (FPCFPCData)iter.next();
			while (data != null && rs.next()) {
				c1 = rs.getInt(1);
				c2 = rs.getInt(2);

				if (c2 > data.getContig2() || c1 > data.getContig1()) {
					addToHits(mapper,t1,t2,hits,fpHits,data,smfd,newHitContent);
					fpHits.clear();
					if (iter.hasNext()) {
						for (data=(FPCFPCData)iter.next(); c2>data.getContig2()||c1>data.getContig1(); data=(FPCFPCData)iter.next()){
							addToHits(mapper,t1,t2,hits,fpHits,data,smfd,newHitContent);
							if (!iter.hasNext()) {
								data = null;
								break;
							}
						}
					}
					else data = null;
				}
				//if (data != null && c2 == data.getContig2() && c1 == data.getContig1()) // mdb removed 7/25/07 #134
				if (data != null && c2 == data.getContent2() && c1 == data.getContent1()) // mdb added 7/25/07 #134
					fpHits.add(FPCFPCData.getFPHitData(rs.getString(3),rs.getString(4),rs.getInt(5),rs.getInt(6),
							rs.getDouble(7),rs.getInt(8)+rs.getInt(9),rs.getInt(10)));
			}
			if (data != null) {
				addToHits(mapper,t1,t2,hits,fpHits,data,smfd,newHitContent);
				fpHits.clear();
				while (iter.hasNext()) 
					addToHits(mapper,t1,t2,hits,fpHits,(FPCFPCData)iter.next(),smfd,newHitContent);
			}
		}
		finally {
			closeStatement(stat);
			closeResultSet(rs);
			stat = null;
			rs = null;
			data = null;
			fpHits = null;
		}	
	}

	private void addToHits(Mapper mapper, MarkerTrack t1, MarkerTrack t2, List hits, List fpHits, 
			FPCFPCData data, RepetitiveMarkerFilterData smfd, int newHitContent) {
		data.addHitData(newHitContent,fpHits);
		int i = hits.indexOf(data);
		if (i < 0) hits.add(new FPCFPCHits(mapper,t1,t2,data,smfd));
		else ((FPCFPCHits)hits.get(i)).addHits(newHitContent,fpHits,data,smfd);

		if (fpcFpcCache != null) fpcFpcCache.add(data);
	}

	private void setBlockHitMarkers(int p1, int p2, List dataList) throws SQLException {
		if (dataList == null || dataList.isEmpty()) return ;

		Iterator iter;
		FPCFPCData data;
		Statement stat = null;
		ResultSet rs = null;
		int c1,c2;
		List<String> markers = new ArrayList<String>();

		int contigs1[] = new int[dataList.size()];
		int contigs2[] = new int[dataList.size()];
		iter = dataList.iterator();
		for (int i = 0; i < contigs1.length; i++) {
			data = (FPCFPCData)iter.next();
			//contigs1[i] = data.getContig1(); // mdb removed 7/25/07 #134
			//contigs2[i] = data.getContig2(); // mdb removed 7/25/07 #134
			contigs1[i] = data.getContent1(); // mdb added 7/25/07 #134
			contigs2[i] = data.getContent2(); // mdb added 7/25/07 #134
		}

		try {
			stat = createStatement();
			String query = setInt(SHARED_MRK_BLOCK_QUERY,p1);
			query = setInsert(query,C1_INSERT,C1_RVALUE,contigs1);
			query = setInt(query,p2);
			query = setInsert(query,C2_INSERT,C2_RVALUE,contigs2);
			query = setInt(query,p1);

			rs = stat.executeQuery(query);
			iter = dataList.iterator();
			data = (FPCFPCData)iter.next();
			while (data != null && rs.next()) {
				c1 = rs.getInt(1);
				c2 = rs.getInt(2);

				if (c2 > data.getContig2() || c1 > data.getContig1()) {
					data.setSharedMarkerBlockHits(markers);
					markers.clear();
					if (iter.hasNext()) {
						for (data=(FPCFPCData)iter.next(); c2>data.getContig2() || c1>data.getContig1(); data=(FPCFPCData)iter.next()) {
							data.setSharedMarkerBlockHits(null);
							if (!iter.hasNext()) {
								data = null;
								break;
							}
						}
					}
					else data = null;
				}
				//if (data != null && c2 == data.getContig2() && c1 == data.getContig1()) // mdb removed 7/25/07 #134
				if (data != null && c2 == data.getContent2() && c1 == data.getContent1()) // mdb added 7/25/07 #134
					markers.add(rs.getString(3));//.intern()); // mdb removed intern() 2/2/10 - can cause memory leaks in this case
			}
			if (data != null) {
				data.setSharedMarkerBlockHits(markers);
				markers.clear();
				while (iter.hasNext())
					((FPCFPCData)iter.next()).setSharedMarkerBlockHits(null);
			}
		}
		finally {
			closeStatement(stat);
			closeResultSet(rs);
			stat = null;
			rs = null;
			data = null;
			markers = null;
		}
	}

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

	private static String getPseudoMarkerQuery(int hc, int dhc) {
		String q = PSEUDO_MRKHITS_QUERY;
		if (hc == MapInfo.NOT_SET) {
			if (dhc == MapInfo.ALL_HITS)         
				return q;
			if (dhc == MapInfo.ONLY_BLOCK_HITS)  
				return q+" AND "+PSEUDO_MRK_BLOCK_APPEND;
			if (dhc == MapInfo.ONLY_FILTERED_HITS)
				return q+" AND ("+PSEUDO_MRK_BLOCK_APPEND+" OR "+PSEUDO_MRK_NONREPETITIVE_APPEND+")";
		}
		else if (hc == MapInfo.ONLY_BLOCK_HITS) {
			if (dhc == MapInfo.ONLY_FILTERED_HITS) 
				return q+" AND "+PSEUDO_MRK_NON_BLOCK_APPEND+" AND "+PSEUDO_MRK_NONREPETITIVE_APPEND;
			if (dhc == MapInfo.ALL_HITS)        
				return q+" AND "+PSEUDO_MRK_NON_BLOCK_APPEND;
		}
		else if (hc == MapInfo.ONLY_FILTERED_HITS) {
			if (dhc == MapInfo.ALL_HITS) 
				return q+" AND "+PSEUDO_MRK_NON_BLOCK_APPEND+" AND "+PSEUDO_MRK_REPETITIVE_APPEND;
		}
		throw new IllegalStateException("Getting marker hits query; hc = "+hc+" and dhc = "+dhc);
	}

	private static String getPseudoBESQuery(int hc, int dhc) {
		String q = PSEUDO_BESHITS_QUERY;
		if (hc == MapInfo.NOT_SET) {
			if (dhc == MapInfo.ALL_HITS)         
				return q;
			if (dhc == MapInfo.ONLY_BLOCK_HITS)  
				return q+" AND "+PSEUDO_BES_BLOCK_APPEND;
			if (dhc == MapInfo.ONLY_FILTERED_HITS)
				return q+" AND ("+PSEUDO_BES_BLOCK_APPEND+" OR "+PSEUDO_BES_NONREPETITIVE_APPEND+")";
		}
		else if (hc == MapInfo.ONLY_BLOCK_HITS) {
			if (dhc == MapInfo.ONLY_FILTERED_HITS) 
				return q+" AND "+PSEUDO_BES_NON_BLOCK_APPEND+" AND "+PSEUDO_BES_NONREPETITIVE_APPEND;
			if (dhc == MapInfo.ALL_HITS)        
				return q+" AND "+PSEUDO_BES_NON_BLOCK_APPEND;
		}
		else if (hc == MapInfo.ONLY_FILTERED_HITS) {
			if (dhc == MapInfo.ALL_HITS) 
				return q+" AND "+PSEUDO_BES_NON_BLOCK_APPEND+" AND "+PSEUDO_BES_REPETITIVE_APPEND;
		}
		throw new IllegalStateException("Getting bes hits query; hc = "+hc+" and dhc = "+dhc);
	}	

	private static String getFPCFPHitsQuery(int hc, int dhc) {
		String q = FP_HITS_QUERY;
		if (hc == MapInfo.NOT_SET) {
			if (dhc == MapInfo.ALL_HITS)         
				return q+FPC_QUERY_POST_APPEND;
			if (dhc == MapInfo.ONLY_BLOCK_HITS)  
				return q+" AND "+FPC_FP_BLOCK_APPEND+FPC_QUERY_POST_APPEND;
			if (dhc == MapInfo.ONLY_FILTERED_HITS)
				return q+" AND ("+FPC_FP_BLOCK_APPEND+" OR "+FPC_FP_NONREPETITIVE_APPEND+")"+FPC_QUERY_POST_APPEND;
		}
		else if (hc == MapInfo.ONLY_BLOCK_HITS) {
			if (dhc == MapInfo.ONLY_FILTERED_HITS) 
				return q+" AND "+FPC_FP_NON_BLOCK_APPEND+" AND "+FPC_FP_NONREPETITIVE_APPEND+FPC_QUERY_POST_APPEND;
			if (dhc == MapInfo.ALL_HITS)        
				return q+" AND "+FPC_FP_NON_BLOCK_APPEND+FPC_QUERY_POST_APPEND;
		}
		else if (hc == MapInfo.ONLY_FILTERED_HITS) {
			if (dhc == MapInfo.ALL_HITS) 
				return q+" AND "+FPC_FP_NON_BLOCK_APPEND+" AND "+FPC_FP_REPETITIVE_APPEND+FPC_QUERY_POST_APPEND;
		}
		throw new IllegalStateException("Getting fp hits query; hc = "+hc+" and dhc = "+dhc);
	}
}
