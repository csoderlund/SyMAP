package dotplot;

import java.util.List;
import java.util.Vector;

import database.DBconn;
import database.DBAbsUser;
import props.ProjectPool;

import java.util.ArrayList;

import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;

import util.ErrorReport;
import util.Utilities;

/****************************************************
 * Loads data for the plot
 * CAS531 removed cache and dead code; CAS533 removed some Loader stuff, dead code, a little rewrite
 */
public class DotPlotDBUser extends DBAbsUser {
	private final int X = Data.X, Y = Data.Y;
	private double minPctid=100;
	
	public DotPlotDBUser(DBconn dr) {
		super(dr);
	}
	public void clear() { minPctid=100;}
	public double getMinPctID() { return minPctid;}
	public DBconn getDBconn() {return super.getDBconn();}
	
	/***************************************************
	 *  For all projects; add groups to projects, and blocks to groups
	 *  CAS533 renamed from setGroups
	 */
	public void setGrpPerProj(Project[] projects, int[] xGroupIDs, int[] yGroupIDs, ProjectPool pp) {
	try {
		Vector<Group> list = new Vector<Group>();
		Statement stat = createStatement();
		String qry;
		ResultSet rs;
		String groupList = null;
		
	// for each project, add groups
		for (Project prj : projects) {
			if (xGroupIDs != null && yGroupIDs != null) { // chromosomes only (from CE)
				groupList = "(";
				if (prj == projects[0]) groupList += Utilities.getCommaSepString(xGroupIDs);
				else					groupList += Utilities.getCommaSepString(yGroupIDs);
				groupList += ")";
			}
			String inGrp =  (groupList == null) ? "" : " AND g.idx IN " + groupList;
			
			qry = "SELECT g.idx, g.sort_order, g.name, p.length " +
				  " FROM xgroups AS g JOIN pseudos AS p ON (p.grp_idx=g.idx) " +
				  " WHERE g.proj_idx=" + prj.getID() +  inGrp + 
				  " AND g.sort_order > 0 ORDER BY g.sort_order";
			
			rs = stat.executeQuery(qry);
			while (rs.next()) {
				int idx = rs.getInt(1);
				Group g = prj.getGroupByID(idx);
				if (g == null)
					g = new Group(idx, rs.getInt(2), rs.getString(3), rs.getInt(4),  prj.getID());
				list.add(g);
			}
			prj.setGroups( list.toArray(new Group[0]) );
			list.clear();
			closeResultSet(rs);
		}
		
		if (xGroupIDs != null && yGroupIDs != null)
			groupList = "(" + Utilities.getCommaSepString(xGroupIDs) + ", " +
							  Utilities.getCommaSepString(yGroupIDs) + ")";
		
	// does the reference groupN have block with projectI groupM
		Project pX = projects[X];
		for (int i = 0;  i < projects.length;  i++) { // start at 0 to include self-alignments
			Project pY = projects[i];
			boolean swapped = pp.isSwapped(pX.getID(), pY.getID());
			String inGrp =  (groupList == null) ? "" : " AND g.idx IN " + groupList;
			int p1 =  (swapped) ? pX.getID() : pY.getID();
			int p2 =  (swapped) ? pY.getID() : pX.getID();
			
			qry = "SELECT b.grp1_idx, b.grp2_idx " +
				  " FROM blocks AS b " +
				  " JOIN xgroups AS g ON (g.idx=b.grp1_idx) " +
			      " WHERE (b.proj1_idx=" + p1 + " AND b.proj2_idx=" + p2 + inGrp + 
			      " AND g.sort_order > 0) " +
			      " GROUP BY b.grp1_idx,b.grp2_idx";
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
	catch (SQLException e) {ErrorReport.print(e, "Loading projects and groups");}
	}
	
	// For this Tile: Add blocks to a tile (cell)
	public void setBlocks(Tile tile,  boolean swapped) {
	try {
		Group gX = (swapped ? tile.getGroup(Y) : tile.getGroup(X));
		Group gY = (swapped ? tile.getGroup(X) : tile.getGroup(Y));
		
		Statement stat = createStatement();
		String qry =  "SELECT blocknum, start2, end2, start1, end1 "+
			"FROM blocks WHERE grp1_idx="+gY+" AND grp2_idx="+gX;
		ResultSet rs = stat.executeQuery(qry);
		tile.clearBlocks();
		while (rs.next()) {
			int start2 = rs.getInt(2);
			int end2   = rs.getInt(3);
			int start1 = rs.getInt(4);
			int end1   = rs.getInt(5);
				
			tile.addBlock(
					rs.getInt(1), 				/* blocknum */
					swapped ? start1 : start2, 	/* int sX */ 
					swapped ? end1 : end2,		/* int eX */ 
					swapped ? start2 : start1, 	/* int sY */ 
					swapped ? end2 : end1);		/* int eY */
		}
		closeResultSet(rs);
		closeStatement(stat);
	}
	catch (SQLException e) {ErrorReport.print(e, "Loading Blocks");}
	}
	// For this tile, Add hits 
	public void setHits(Tile tile, boolean swapped)  {
	try {
		List<DPHit> hits = new ArrayList<DPHit>();
		int xx=X, yy=Y;
		if (swapped) {xx=Y; yy=X;}
		Project pX = tile.getProject(xx),  pY = tile.getProject(yy);
		Group gX   = tile.getGroup(xx), gY = tile.getGroup(yy);
		
		Statement stat = createStatement();
		
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
							pctid,	blockidx!=0, length);				
			hits.add(hit);
			minPctid = Math.min(minPctid, pctid);
		}
		closeResultSet(rs);
		closeStatement(stat);
		
		tile.setHits(hits.toArray(new DPHit[0]));
		hits.clear();
	}
	catch (SQLException e) {ErrorReport.print(e, "Loading Blocks"); }
	}
}
