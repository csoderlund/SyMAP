package symap.block;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Iterator;
import java.util.Collection;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import number.GenomicsNumber;
import symap.SyMAP;
import symap.marker.MarkerData;
import symap.pool.DatabaseUser;
import symap.pool.ProjectProperties;
import symap.pool.ProjectPair;
import util.DatabaseReader;
import util.ListCache;

/**
 * The ContigPool handles the cache for the Block track.
 * 
 * @author Austin Shoemaker
 * @see ListCache
 */
public class ContigPool extends DatabaseUser {	
	protected static final String CONTIGS_QUERY = 
		"SELECT idx,size FROM contigs "+
		"WHERE proj_idx=? AND number=?";
	
	// mdb added query 3/19/07 #105
	protected static final String MULT_CTGHITS_QUERY = 
		"SELECT DISTINCT g.name from "+
    	"pairs AS p INNER JOIN "+
    	"ctghits AS ch ON (p.proj1_idx=? AND p.proj2_idx=? AND ch.pair_idx=p.idx) INNER JOIN "+
    	"contigs AS c ON (c.idx=ch.ctg1_idx) INNER JOIN "+
    	"groups AS g ON (g.idx=ch.grp2_idx) "+
    	"WHERE c.number=?";// AND ch.grp2_idx!=?";

	protected static final String MARKER_QUERY =
		"SELECT m.name,m.type,mc.pos "+
		"FROM markers as m, mrk_ctg as mc "+
		"WHERE m.proj_idx=? AND mc.ctg_idx=? AND m.idx=mc.mrk_idx ORDER BY mc.pos; ";

	//protected static final String BLOCKS_QUERY = 
	//"SELECT c.number "+
	//"FROM "+GROUPS_TABLE+" AS g1,"+BLOCKS_TABLE+" AS b,"+GROUPS_TABLE+" AS g2, "+CONTIGS_TABLE+" AS c "+
	//"WHERE g1.proj_idx=? AND g1.name=? AND g1.idx=b.grp1_idx AND b.blocknum=? AND b.proj2_idx=? AND b.grp2_idx=g2.idx AND g2.name=?"+
	//"      AND c.proj_idx=? AND c.grp_idx=g?.idx AND FIND_IN_SET(c.idx,b.ctgs?)>0 "+
	//"ORDER BY FIND_IN_SET(c.idx,b.ctgs?) ";

	// ASD modified to handle a bug June 2, 2006
	protected static String makeBlocksQuery(ProjectPair pair, int project, int otherProject, String g1, String g2, int bn) {
		boolean isProj1 = pair.getP1() == project;
		/*
	boolean isGroup1 = !g1.equals("0");
	boolean isGroup2 = !g2.equals("0");

	StringBuffer query = new StringBuffer("SELECT c.number FROM ");
	if (isGroup1) query.append(GROUPS_TABLE).append(" AS g1,");
	query.append(BLOCKS_TABLE).append(" AS b,");
	if (isGroup2) query.append(GROUPS_TABLE).append(" AS g2,");
	query.append(CONTIGS_TABLE).append(" AS c WHERE ");
	if (isGroup1) 
	    query.append("g1.proj_idx=").append(pair.getP1()).append(" AND g1.name='").append(g1).append("' AND g1.idx=b.grp1_idx ");
	else          
	   query.append("b.proj1_idx=").append(pair.getP1()).append(" AND b.grp1_idx=0 ");
	query.append(" AND b.blocknum=").append(bn).append(" AND b.proj2_idx=").append(pair.getP2()).append(" AND ");
	if (isGroup2) query.append(" b.grp2_idx=g2.idx AND g2.name='").append(g2).append("' ");
	else          query.append(" b.grp2_idx=0 ");
	query.append(" AND c.proj_idx=").append(project).append(" AND ");
	if (isProj1) {
	    if (!isGroup1) query.append(" c.grp_idx=0 ");
	    else           query.append(" c.grp_idx=g1.idx ");
	}
	else {
	    if (!isGroup2) query.append(" c.grp_idx=0 ");
	    else           query.append(" c.grp_idx=g2.idx ");
	}
	query.append(" AND FIND_IN_SET(c.idx,b.ctgs").append(isProj1 ? "1" : "2").append(")>0 ");
	query.append("ORDER BY FIND_IN_SET(c.idx,b.ctgs").append(isProj1 ? "1" : "2").append(") ");
		 */
		StringBuffer query = new StringBuffer("SELECT c.number FROM ");
		query.append("groups").append(" AS g1,");
		query.append("blocks").append(" AS b,");
		query.append("groups").append(" AS g2,");
		query.append("contigs").append(" AS c WHERE ");
		query.append("g1.proj_idx=").append(pair.getP1()).append(" AND g1.name='").append(g1).append("' AND g1.idx=b.grp1_idx ");
		query.append(" AND b.blocknum=").append(bn).append(" AND b.proj2_idx=").append(pair.getP2()).append(" AND ");
		query.append(" b.grp2_idx=g2.idx AND g2.name='").append(g2).append("' ");
		query.append(" AND c.proj_idx=").append(project).append(" AND ");
		if (isProj1) {
			query.append(" c.grp_idx=g1.idx ");
		}
		else {
			query.append(" c.grp_idx=g2.idx ");
		}
		query.append(" AND FIND_IN_SET(c.idx,b.ctgs").append(isProj1 ? "1" : "2").append(")>0 ");
		query.append("ORDER BY FIND_IN_SET(c.idx,b.ctgs").append(isProj1 ? "1" : "2").append(") ");
		return query.toString();
	}

	private ProjectProperties pp;
	private ListCache bpData;
	private Map<BlockObj,String> blocks;

	public ContigPool(DatabaseReader dr, ProjectProperties pp, ListCache cache) {
		super(dr);
		this.pp = pp;
		bpData = cache;
		blocks = new HashMap<BlockObj,String>();
	}

	public synchronized void close() {
		super.close();
	}

	public synchronized void clear() {
		super.close();
		if (bpData != null) bpData.clear();
		blocks.clear();
	}

	public synchronized String getContigs(int project, int otherProject, String block) throws SQLException {
		String query = null;
		String contigs = (String)blocks.get(new BlockObj(project,otherProject,block));
		if (contigs == null) {
			String s[] = block.split("\\.");
			String g1 = s[0];
			String g2 = s[1];
			int bn = Integer.parseInt(s[2]);
			Statement stat = null;
			ResultSet rs = null;
			try {
				//ProjectPair pair = pp.getProjectPair(project,otherProject);
				//String projectNumber = pair.getP1() == project ? "1" : "2";
				//query = setString(setInt(setInt(setString(setInt(BLOCKS_QUERY,pair.getP1()),g1),bn),pair.getP2()),g2);
				//query = setConstant(setConstant(setConstant(setInt(query,project),projectNumber),projectNumber),projectNumber);
				query = makeBlocksQuery(pp.getProjectPair(project,otherProject),project,otherProject,g1,g2,bn);
				stat = createStatement();
				rs = stat.executeQuery(query);
				List<Integer> clist = new ArrayList<Integer>();
				while (rs.next())
					clist.add(new Integer(rs.getInt(1)));
				contigs = Block.getContigs(clist);
				clist.clear();
				closeResultSet(rs);
				closeStatement(stat);
			} catch (SQLException e) {
				closeResultSet(rs);
				closeStatement(stat);
				close();
				throw e;
			}
			blocks.put(new BlockObj(project,otherProject,block),contigs);
		}
		if (SyMAP.DEBUG) System.out.println("Block: "+block+" has contigs: "+contigs+" ON Query: "+query);

		return contigs;
	}

	/**
	 * Method <code>setBlock</code> sets the list of inner blocks using the 
	 * ones in the list as needed, removing the ones not needed and adding 
	 * the needed ones from the contigList.  After returning, contigList may 
	 * have fewer elements if the maximum number of blocks 
	 * (Block.MAX_BLOCKS_ALLOWED) is reached.
	 *
	 * @param block a <code>Block</code> value
	 * @param contigList a <code>Collection</code> value
	 * @param innerBlocks a <code>Collection</code> value
	 * @param size a <code>GenomicsNumber</code> value
	 * @exception SQLException if an error occurs
	 */
	public synchronized void setBlock(Block block, 
			Collection<Integer> contigList, Collection<Block.InnerBlock> innerBlocks, 
			GenomicsNumber size) throws SQLException
	{
		int project = block.getProject();
		int project2 = block.getOtherProject();
		LinkedList<ContigData> blockList = new LinkedList<ContigData>();
		LinkedList<Integer> neededBlocks = new LinkedList<Integer>();

		// Create a list of contig numbers already cached in innerBlocks
		ArrayList<Integer> cachedBlocks = new ArrayList<Integer>(innerBlocks.size());
		for (Block.InnerBlock ib : innerBlocks)
			cachedBlocks.add(new Integer(ib.getContig()));

		// Create a list of contig numbers (neededBlocks) that aren't cached yet
		Iterator<Integer> iter = contigList.iterator();
		while (iter.hasNext()) {
			Integer integer = iter.next();
			if (!cachedBlocks.contains(integer)) {
				ContigData data = null;
				if (bpData != null)
					data = (ContigData)(bpData.get(new ContigData(project,integer.intValue(),project2)));
				if (data == null) neededBlocks.add(integer);
				else {
					blockList.add(data);
					if (blockList.size() >= Block.MAX_BLOCKS_ALLOWED) {
						System.err.println("Maximum blocks exceeded ("+Block.MAX_BLOCKS_ALLOWED+")");
						while (iter.hasNext()) {
							iter.next();
							iter.remove();
						}
						contigList.removeAll(neededBlocks);
						neededBlocks.clear();
						break;
					}
				}
			}
		}
		cachedBlocks.clear();
		cachedBlocks = null;

		if (!neededBlocks.isEmpty()) {
			if (SyMAP.DEBUG) System.out.println("Looking for block data in data base for " + neededBlocks);

			PreparedStatement statement = null;
			ResultSet rs = null;
			LinkedList<ContigData> tempBlockDataList = new LinkedList<ContigData>();
			LinkedList<MarkerData> markerData = new LinkedList<MarkerData>();
			int contig;
			try {
				statement = prepareStatement(CONTIGS_QUERY);
				iter = neededBlocks.iterator();
				while (iter.hasNext()) {
					contig = ((Integer)iter.next()).intValue();
					statement.setInt(1,project);
					statement.setInt(2,contig);
					rs = statement.executeQuery();
					if (rs.next()) {		
						// mdb added 3/19/07 #105 -- BEGIN	
						PreparedStatement s = prepareStatement(MULT_CTGHITS_QUERY);
						ResultSet r;
						String groupList = "";
						s.setInt(1,project);
						s.setInt(2,project2);
						s.setInt(3,contig);
						r = s.executeQuery();	
						while (r.next()) 
							groupList += ""+r.getString(1)+(r.isLast() ? "" : ", ");
						// mdb added 3/19/07 #105 -- END
						
						ContigData data = new ContigData(project,contig,project2,rs.getInt(1),rs.getLong(2),new String(groupList));
						tempBlockDataList.add(data);
					}
					iter.remove();
					closeResultSet(rs);
					if (tempBlockDataList.size()+blockList.size() >= Block.MAX_BLOCKS_ALLOWED) {
						System.err.println("Maximum blocks exceeded ("+Block.MAX_BLOCKS_ALLOWED+")");
						contigList.removeAll(neededBlocks);
						neededBlocks.clear();
						break;
					}
				}
				if (!tempBlockDataList.isEmpty()) {
					statement = prepareStatement(MARKER_QUERY);
					for (ContigData data : tempBlockDataList) {
						statement.setInt(1,project);
						statement.setInt(2,data.getID());
						rs = statement.executeQuery();
						while (rs.next())
							markerData.add(ContigData.getMarkerData(rs.getString(1),rs.getString(2),rs.getLong(3)));
						data.setMarkerData((MarkerData[])markerData.toArray(new MarkerData[markerData.size()]));
						blockList.add(data);
						if (bpData != null) bpData.add(data);
						markerData.clear();
						closeResultSet(rs);
					}
				}
			} catch (SQLException e) {
				close();
				throw e;
			}
			finally {
				markerData.clear();
				tempBlockDataList.clear();
			}
		}
		ContigData.setBlock(block,contigList,innerBlocks,blockList,size);
	}

	private static class BlockObj {
		private int p, op;
		private String b;

		private BlockObj(int project, int otherProject, String block) {
			this.p = project;
			this.op = otherProject;
			this.b = block;
		}

		public boolean equals(Object obj) {
			if (obj instanceof Block) {
				BlockObj o = (BlockObj)obj;
				return p == o.p && op == o.op && b.equals(o.b);
			}
			return false;
		}

		public int hashCode() {
			return new StringBuffer(p).append(" ").append(op).append(" ").append(b).toString().hashCode();
		}
	} 
}

