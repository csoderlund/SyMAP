package dotplot;

import java.util.List;
import java.util.Vector;
import java.util.ArrayList;

import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;

import util.DatabaseReader;
import util.Utilities;
import symap.pool.DatabaseUser;
import symap.pool.ProjectPair;
import symap.SyMAPConstants;
import symap.pool.ProjectProperties;

public class DotPlotDBUser extends DatabaseUser implements DotPlotConstants, SyMAPConstants {
	public static final String DB_PROPS_FILE = "/properties/database.properties"; 

	//private ListCache projectCache = new SoftListCache(1,1);

	public DotPlotDBUser(DatabaseReader dr) {
		super(dr);
	}
	public void clear() {
		//projectCache.clear();
	}
	// Data
	public DatabaseReader getDatabaseReader() {
		return super.getDatabaseReader();
	}
	// Loader
	public void setIBlocks(Tile tile, Project[] projects, boolean swapped) 	throws SQLException 
	{
		try {
			Group gX = (swapped ? tile.getGroup(Y) : tile.getGroup(X));
			Group gY = (swapped ? tile.getGroup(X) : tile.getGroup(Y));
			
			Statement stat = createStatement();
			String qry =  "SELECT blocknum, start2, end2, start1, end1 "+
				"FROM blocks WHERE grp1_idx="+gY+" AND grp2_idx="+gX;
			ResultSet rs = stat.executeQuery(qry);
			tile.clearIBlocks();
			while (rs.next()) {
				int start2 = rs.getInt(2);
				int end2   = rs.getInt(3);
				int start1 = rs.getInt(4);
				int end1   = rs.getInt(5);
					
				tile.addIBlock(
						rs.getInt(1), 				/* blocknum */
						swapped ? start1 : start2, 	/* int sX */ 
						swapped ? end1 : end2,		/* int eX */ 
						swapped ? start2 : start1, 	/* int sY */ 
						swapped ? end2 : end1);		/* int eY */
			}
			closeResultSet(rs);
			closeStatement(stat);
		}
		catch (SQLException e) {
			close();
			throw e;
		}
	}
	// Loader
	public void setHits(Project[] projects, ProjectPair pair, Tile tile, 
			boolean filtered, FilterData fd, boolean swapped) throws SQLException 
	{
		if (tile.isLoaded(filtered ? Tile.SOME_LOADED : Tile.FULLY_LOADED)) return ;
		try {
			List<DPHit> hits = new ArrayList<DPHit>();
			Project pX = projects[X], pY = projects[Y];
			Group gX = tile.getGroup(X), gY = tile.getGroup(Y);
			Statement stat = createStatement();
			if (swapped) {
				pX = projects[Y];
				pY = projects[X];
				gX = tile.getGroup(Y);
				gY = tile.getGroup(X);
			}
			double min=100, max=0;
			String qry =
				"SELECT (h.start2+h.end2)>>1 as posX, (h.start1+h.end1)>>1 as posY,"
				+ "h.pctid, b.block_idx, (h.end2-h.start2) as length, h.strand "+
				"FROM pseudo_hits AS h "+
				"LEFT JOIN pseudo_block_hits AS b ON (h.idx=b.hit_idx) "+
				"WHERE (h.proj1_idx="+pY.getID()+" AND h.proj2_idx="+pX.getID()+
				"       AND h.grp1_idx="+gY.getID()+" AND h.grp2_idx="+gX.getID()+")";
			ResultSet rs = stat.executeQuery(qry);
			while (rs.next()) {
				int posX = 		rs.getInt(1);
				int posY = 		rs.getInt(2);
				double pctid = 	rs.getDouble(3);
				long blockidx = rs.getLong(4);
				int length = 	rs.getInt(5);
				String strand = rs.getString(6);
				if (strand.equals("+/-") || strand.equals("-/+")) length = -length;
				
				DPHit hit = new DPHit(
								swapped ? posY : posX,	
								swapped ? posX : posY,	 
								pctid,		
								blockidx!=0,			
								length);				
				hits.add(hit);
				min = Math.min(min, pctid);
				max = Math.min(max, pctid);
			}
			closeResultSet(rs);
			closeStatement(stat);
			
			if (fd != null) fd.setBounds(min, max); 
			tile.setHits(hits.toArray(new DPHit[0]), filtered ? Tile.SOME_LOADED : Tile.FULLY_LOADED);
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
	// Data
	public void setProjects(Project[] projects, int[] xGroups, int[] yGroups, ProjectProperties pp) 			throws SQLException {
		try {
			/** CAS531 dead code
			ProjectHolder th = new ProjectHolder(projects);
			ProjectHolder ph = (ProjectHolder)projectCache.get(th);
			if (ph != null) ph.copyInto(projects);
			else projectCache.add(th);
			**/	
			setGroups(projects, xGroups, yGroups, pp);
		}
		catch (SQLException e) {
			close();
			throw e;
		}
	}

	
	private void setGroups(Project[] projects, 
			int[] xGroupIDs, int[] yGroupIDs, 	
			ProjectProperties pp) 	throws SQLException 
	{
		Vector<Group> list = new Vector<Group>();
		Statement stat = createStatement();
		String qry;
		ResultSet rs;
		String groupList = null;
		
		for (Project p : projects) {
			if (xGroupIDs != null && yGroupIDs != null) {
				groupList = "(";
				if (p == projects[0])
					groupList += Utilities.getCommaSepString(xGroupIDs);
				else
					groupList += Utilities.getCommaSepString(yGroupIDs);
				groupList += ")";
			}
			
			qry = "SELECT g.idx,g.sort_order,g.name,p.length " +
					"FROM xgroups AS g JOIN pseudos AS p ON (p.grp_idx=g.idx) " +
					"WHERE g.proj_idx=" + p.getID() +
					(groupList == null ? "" : " AND g.idx IN " + groupList) + 
					" AND g.sort_order > 0 ORDER BY g.sort_order";
			
			rs = stat.executeQuery(qry);
			while (rs.next()) {
				int idx = rs.getInt(1);
				Group g = p.getGroupByID(idx);
				if (g == null)
					g = new Group(idx,
							  rs.getInt(2),
							  rs.getString(3),
							  rs.getInt(4),
							  p.getID());
				list.add(g);
			}
			p.setGroups( list.toArray(new Group[0]) );
			list.clear();
			closeResultSet(rs);
		}
		
		if (xGroupIDs != null && yGroupIDs != null)
			groupList = "(" + Utilities.getCommaSepString(xGroupIDs) + ", " +
							Utilities.getCommaSepString(yGroupIDs) + ")";
		
		
		Project pX = projects[X];
		for (int i = 0;  i < projects.length;  i++) { // start at 0 (project X) to include self-alignments
			Project pY = projects[i];
			boolean swapped = pp.isSwapped(pX.getID(), pY.getID());
			
			qry = "SELECT b.grp1_idx,b.grp2_idx " +
				  "FROM blocks AS b " +
				  "JOIN xgroups AS g ON (g.idx=b.grp1_idx) " +
			      "WHERE (b.proj1_idx=" + (swapped ? pX.getID() : pY.getID()) +
			      " AND b.proj2_idx=" + (swapped ? pY.getID() : pX.getID()) + 
			      (groupList == null ? "" : " AND g.idx IN " + groupList) + 
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
/** CAS531 dead code 
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
**/
}
