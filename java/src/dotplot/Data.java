package dotplot;

import java.awt.Dimension;
import java.awt.Shape;
import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import database.DBconn2;
import props.PropsDB;
import symap.drawingpanel.SyMAP2d;
import symap.mapper.HfilterData;
import util.ErrorReport;
import util.Utilities;

/**
 * This contains the arrays of data (Project and Tile) and interface code with Filter
 * CAS533 Removed Observable, removed Loader (was painting tile at time); rearranged 
 * CAS541 Replace DBAbsUser with new DBconn2; CAS552 remove projectPair (not used), improved response to buttons
 */
public class Data  {
	protected static final double DEFAULT_ZOOM = 0.99;
	protected static final int X  = 0, Y   = 1;
	private static int initMinPctid = -1;		// CAS543 find first time
	
	private Project projects[]; // loaded data
	private Tile[] tiles;
	
	private Group currentGrp[];	// selected tile, 2 grps
	private Project currentProjY; 
	private int maxGrps=0;
	
	private SyMAP2d symap;
	private PropsDB projProps;
	private FilterData filtData;
	private ControlPanel cntl=null;
	private boolean is2D=false;
	
	private double sX1, sX2, sY1, sY2;
	private Shape selectedBlock;
	private double zoomFactor, scaleFactor = 1;
	private boolean hasSelectedArea, isTileView;
	protected boolean bIsScaled=false;
	
	private DBconn2 tdbc2;
	private DBload dbLoad;
	
	// Called from DotPlotFrame; for ChrExp, called on first time its used
	protected Data(DBconn2 dbc2, String type, boolean is2d) {
		try { 
			tdbc2 = new DBconn2("Dotplot" + type + "-" + DBconn2.getNumConn(), dbc2);
			this.is2D = is2d;
			
			dbLoad = new DBload(tdbc2); //symap = new SyMAP2d(dbc2, null); 
			
			projProps = new PropsDB(dbc2); 
			
			if (initMinPctid<=0)
				initMinPctid = tdbc2.executeInteger("select min(pctid) from pseudo_hits"); // CAS543 set for slider
			
		} catch (Exception e) {ErrorReport.print(e, "Unable to create SyMAP instance");}
		
		projects      = null; 
		tiles         = new Tile[0];
		currentGrp    = new Group[] {null,null};
		currentProjY  = null; 
		
		setHome();
		
		filtData = new FilterData(); // CAS543 moved from initialized to here to keep settings
	}
	/*************************************************************
	 * Called from: DotPlotFrame (genome), SyMAPFrameCommon (groups), Data.setReference (change ref)
	 *              xGroupIDs, yGroupIDs null if genome
	 * CAS533 for blocks and hits:
	 * 	this was loader.execute(projProps,projects,tiles,sb,true); added swap instead of reading database again
	 */
	public void initialize(int[] projIDs, int[] xGroupIDs, int[] yGroupIDs) {
	try {
		clear();
		
	/* init projects*/
		Vector<Project> newProjects = new Vector<Project>(projIDs.length);
		for (int i = 0;  i < projIDs.length;  i++) {// 1 -> 0
			Project p = Project.getProject(projects, 1, projIDs[i]);
			if (p == null) p = new Project(projIDs[i], tdbc2);
			newProjects.add( p );
		}
		projects = newProjects.toArray(new Project[0]);
	
		dbLoad.setGrpPerProj(projects, xGroupIDs, yGroupIDs, projProps); // needs to redone for ref
		for (Project p : projects) 
			maxGrps = Math.max(p.getNumGroups(), maxGrps);
		
	/* init tiles */
		tiles = Project.createTiles(projects, tiles, projProps);

	/* add blocks and hits to tiles */
		for (Tile tObj : tiles) {
			boolean bSwap = projProps.isSwapped(tObj.getProjID(X), tObj.getProjID(Y)); // if DB.pairs.proj1_idx=proj(Y)
			if (tObj.hasHits()) tObj.swap(tObj.getProjID(X), tObj.getProjID(Y));
			else {
				dbLoad.setBlocks(tObj, bSwap);

				dbLoad.setHits(tObj, bSwap);
			}
		}
		filtData.setBounds(dbLoad.minPctid, 100);
		
		if (getNumVisibleGroups() == 2) selectTile(100, 100); // kludge; this sets current...
		
	} catch (Exception e) { ErrorReport.print(e,"Initialize"); }
	}
	protected void setCntl(ControlPanel cntl) {this.cntl = cntl;}
	/***************************************************************************/
	private void clear() { // initialize, kill
		setHome();
		sX1 = sX2 = sY1 = sY2 = 0;
		currentGrp[0] = currentGrp[1] = null;
		currentProjY = null;	
		maxGrps = 0;
		
		tiles  = new Tile[0]; // CAS543 add 4 lines
		projects  = null; 
		dbLoad.clear();
	}
	/***********************************************************************/
	// Select for 2D
	private void show2dBlock() {
		if (selectedBlock==null) return;
		
		// CAS531 need to recreate since I changed the Hits code; bonus, allows multiple 2d displays
		// CAS541 cleaned up connection, no longer can do multiple 2d display
		//if (symap==null) new SyMAP2d(tdbc2, null); CAS542 messes up annotation; its fine allowing many
		symap = new SyMAP2d(tdbc2, null);
		symap.getDrawingPanel().setTracks(2); // CAS550 set exact number
		
		if (selectedBlock instanceof ABlock) {
			ABlock ib = (ABlock)selectedBlock;
			Project pX = projects[X];
			Project pY = getCurrentProj();
			Group gX = ib.getGroup(X);
			Group gY = ib.getGroup(Y);

			HfilterData hd = new HfilterData (); // CAS530 use 2D filter
			hd.setForDP(true, false);
			symap.getDrawingPanel().setHitFilter(1,hd); // copy template
			
			symap.getDrawingPanel().setSequenceTrack(1,pY.getID(),gY.getID(),Color.CYAN);	
			symap.getDrawingPanel().setSequenceTrack(2,pX.getID(),gX.getID(),Color.GREEN);
			symap.getDrawingPanel().setTrackEnds(1,ib.getStart(Y),ib.getEnd(Y));
			symap.getDrawingPanel().setTrackEnds(2,ib.getStart(X),ib.getEnd(X));
			symap.getFrame().showX();
		}
		else {
			Rectangle2D bounds = selectedBlock.getBounds2D();
			show2dArea(bounds.getMinX(),bounds.getMinY(),bounds.getMaxX(),bounds.getMaxY());
		}
	}
	
	private void show2dArea(double x1, double y1, double x2, double y2) {
		Project pX = projects[X];
		Project pY = getCurrentProj();
		String track[] = {"",""};
		for (int n = 0;  n < 2;  n++) {
			track[n] = currentGrp[n].toString();
		}
		if (track[X].length() == 0 || track[Y].length() == 0) {
			System.out.println("No Sequences found to display! x=" + track[X] + " y=" + track[Y]);
			return;
		}
		
		try { //DBconn dr = DBconn.getInstance(dbStr, dbUser.getDBconn());
			symap = new SyMAP2d(tdbc2, null);
		} catch (Exception e) {ErrorReport.print(e, "Unable to create SyMAP instance");}
		
		symap.getDrawingPanel().setTracks(2);
		
		HfilterData hd = new HfilterData(); 		// CAS530 use 2D filter
		hd.setForDP(false, true); 					// set block, set all
		symap.getDrawingPanel().setHitFilter(1,hd); // template for defaults
		
		symap.getDrawingPanel().setSequenceTrack(1,pY.getID(),Integer.parseInt(track[Y]),Color.CYAN);
		symap.getDrawingPanel().setSequenceTrack(2,pX.getID(),Integer.parseInt(track[X]),Color.GREEN);
		symap.getDrawingPanel().setTrackEnds(1,y1,y2);
		symap.getDrawingPanel().setTrackEnds(2,x1,x2);
		
		symap.getFrame().showX();
	}
	
	/*****************************************
	 * DotPlotFrame
	 */
	protected Project[] getProjects() { return projects; }
	
	protected void kill() {
		clear();
		tiles = new Tile[0]; 
		if (projects != null) {
			for (Project p : projects)
				if (p != null) p.setGroups(null);
		}

		if (symap != null) symap = null; 
		tdbc2.close();
	}

	/*****************************************
	 * XXX Control
	 */
	protected void setHome() {
		zoomFactor    	= DEFAULT_ZOOM;
		hasSelectedArea = false;
		isTileView      = false;
		selectedBlock 	= null;
		bIsScaled 		= false;	// CAS552 add
		scaleFactor  	= 1;	
		// filtData.setDefaults();
	}
	
	protected void setZoom(double zoom)    {this.zoomFactor = zoom; }; // Control and Plot
	protected void factorZoom(double mult) {if (mult != 1) zoomFactor *= mult;}
	
	protected boolean isHome() {
		boolean bAll = zoomFactor==DEFAULT_ZOOM && bIsScaled==false && scaleFactor==1;
		if (is2D) return bAll;
		return isTileView==false && bAll;
	}
	protected double getZoomFactor() 	{ return zoomFactor;  }
	protected double getScaleFactor()   { return scaleFactor; }
	protected boolean isScaled() 		{ return bIsScaled;    } 
	
	protected void setReference(Project reference) {
		if (reference == projects[X])
			return;
		
		int[] newProjects = new int[projects.length];
		newProjects[0] = reference.getID();
		int i = 1;
		for (Project p : projects)
			if (p.getID() != reference.getID())
				newProjects[i++] = p.getID();
		
		initialize(newProjects, null, null);
	}
	/***********************************************
	 * XXX Plot
	 */
	protected boolean hasSelectedArea()  {return hasSelectedArea;}
	protected ABlock  getSelectedBlock() {return (ABlock) selectedBlock;}
	protected boolean isTileView() 	  {return isTileView;} // plot and Control
	
	protected void    show2dArea() 	  {show2dArea(sX1,sY1,sX2,sY2);}
		
	// Plot.PlotListener mouseClick
	protected boolean selectTile(long xUnits, long yUnits) {
		if (projects[X] == null || projects[Y] == null) return false;

		currentGrp[X] = projects[X].getGroupByOffset(xUnits);
		
		currentGrp[Y] = getGroupByOffset(yUnits, getVisibleGroupsY(getVisibleGroups(X)));
		currentProjY  = getProjectByID(currentGrp[Y].getProjID());
		
		if (currentGrp[X] != null && currentGrp[Y] != null && currentProjY != null) {
			zoomFactor = DEFAULT_ZOOM;
			isTileView = true;
		}
		if (cntl!=null) cntl.setEnable(); // update home button, null the 1st time from 2D
		return true;
	}
	protected boolean selectBlock(double xUnits, double yUnits) {
		Shape s = null;
		if (filtData.isShowBlocks() && !hasSelectedArea && isTileView()) {
			s = Tile.getBlock(tiles,currentGrp[X],currentGrp[Y],xUnits,yUnits);
		
			if (s != null) {
				if (s == selectedBlock)
					show2dBlock();
			}
		}
		selectedBlock = s;
		return (selectedBlock!=null);
	}
	protected boolean selectArea(Dimension size, double x1, double y1, double x2, double y2) {
		double xmax = currentGrp[X].getGrpLenBP();
		double ymax = currentGrp[Y].getGrpLenBP();
		double xfactor = size.getWidth() / xmax;
		double yfactor = size.getHeight() / ymax;

		selectedBlock = null;
		hasSelectedArea = true;
		this.sX1 = Math.min(x1,x2) / xfactor;
		this.sX2 = Math.max(x1,x2) / xfactor;
		this.sY1 = Math.min(y1,y2) / yfactor;
		this.sY2 = Math.max(y1,y2) / yfactor;

		//keep inside bounds, or ignore if completely outside
		if (this.sX1 < 0) this.sX1 = 0;
		if (this.sY1 < 0) this.sY1 = 0;
		if (this.sX2 > xmax) this.sX2 = xmax;
		if (this.sY2 > ymax) this.sY2 = ymax;
		if (this.sX1 > xmax || this.sX2 < 0 || this.sY1 > ymax || this.sY2 < 0)
			hasSelectedArea = false;
		return hasSelectedArea;
	}
	
	protected boolean clearSelectedArea() {
		boolean rc = hasSelectedArea;
		hasSelectedArea = false;
		return rc;
	}
	/***************************************************************************/
	protected boolean isGroupVisible(Group g) { // plot and data
		return (g.isVisible() && (g.hasBlocks() || filtData.isShowEmpty()));
	}
	private boolean isGroupVisible(Group gY, Group[] gX) { // data
		return (gY.isVisible() && (gY.hasBlocks(gX) || filtData.isShowEmpty()));
	}
	protected int getNumVisibleGroups() { // control and data
		int count = 0;
		for (Project p : projects)
			count += p.getNumVisibleGroups();
		return count;
	}
	protected Group[] getVisibleGroups(int axis) { // plot and data
		int num = getProject(axis).getNumGroups();
		Vector<Group> out = new Vector<Group>(num);
		
		for (int i = 1;  i <= num ;  i++) {
			Group g = projects[axis].getGroupByOrder(i);
			if (isGroupVisible(g))
				out.add(g);
		}
		
		return out.toArray(new Group[0]);
	}
	protected Group[] getVisibleGroupsY(Group[] xGroups) { // plot and data
		Vector<Group> out = new Vector<Group>();
		
		if (isTileView) out.add(currentGrp[Y]);
		else {
			for (int axis = 1;  axis < projects.length;  axis++) {
				int num = projects[axis].getNumGroups();
				for (int i = 1;  i <= num ;  i++) {
					Group g = projects[axis].getGroupByOrder(i);
					if (isGroupVisible(g, xGroups))
						out.add(g);
				}
			}
		}
		return out.toArray(new Group[0]);
	}
	protected long getVisibleGroupsSizeY(Group[] xGroups) { // plot.setDims
		long size = 0;
		for (Group g : getVisibleGroupsY(xGroups))
			size += g.getEffectiveSize();
		return size;
	}
	protected long getVisibleGroupsSize(int axis) { // plot.setDims
		long size = 0;
		for (Group g : getVisibleGroups(axis))
			size += g.getEffectiveSize();
		return size;
	}
	protected long getVisibleGroupsSize(int yAxis, Group[] xGroups) { // plot.paintComponenet
		long size = 0;
		for (Group g : getVisibleGroups(yAxis, xGroups))
			size += g.getEffectiveSize();
		return size;
	}
	private Group[] getVisibleGroups(int yAxis, Group[] xGroups) { // above
		Vector<Group> out = new Vector<Group>();
		
		int numGroups = projects[yAxis].getNumGroups();
		for (int i = 1;  i <= numGroups ;  i++) {
			Group g = projects[yAxis].getGroupByOrder(i);
			if (isGroupVisible(g, xGroups))
				out.add(g);
		}
		return out.toArray(new Group[0]);
	}
	/***************************************************************************/
	protected int getInitPctid() { return initMinPctid;} // Filter on startup; CAS543 get the initial DB value
	
	protected FilterData getFilterData() { return filtData; } // plot and Filter.FilterListener
	protected Project getProject(int axis) { return projects[axis]; } // plot and data
	protected int getNumProjects() { return projects.length; } // plot and data
	
	protected Group getCurrentGrp(int axis) 	{return currentGrp[axis];}
	protected Project getCurrentProj() 		{return currentProjY;}
	protected int getCurrentGrpSize(int axis) {
		return currentGrp[axis] == null ? 0 : currentGrp[axis].getGrpLenBP();
	}
	protected int getMaxGrps() { return maxGrps;}
	protected double getX1() { return sX1; } // plot
	protected double getX2() { return sX2; }
	protected double getY1() { return sY1; }
	protected double getY2() { return sY2; }
	protected Tile[] getTiles()  		{ return tiles;       }
	
	protected int getDotSize()			{return filtData.getDotSize();}
	
	// CAS541 Set in ControlPanel, read by Plot
	private int statOpt=0;
	protected void setStatOpts(int n) {statOpt = n;}
	protected int getStatOpts() {return statOpt;}
	
	/******************************************************************/
	private static Group getGroupByOffset(long offset, Group[] groups) {
		if (groups != null)
			for (int i = groups.length-1; i >= 0; i--)
				if (groups[i].getOffset() < offset) return groups[i];
		return null;
	}
	private Project getProjectByID(int id) { // Data.selectTile
		for (Project p : projects)
			if (p.getID() == id)
				return p;
		return null;
	}
	
	/****************************************************
	 * Loads data for the plot
	 * CAS531 removed cache and dead code; CAS533 removed some Loader stuff, dead code, a little rewrite
	 * CAS541 renamed from DotPlotDBuser to DBload; moved from separate file
	 */
	protected class DBload {
		private final int X = Data.X, Y = Data.Y;
		private int minPctid;
		private DBconn2 tdbc2;
		protected DBload(DBconn2 tdbc2) {
			this.tdbc2 = tdbc2;
		}
		protected void clear() {minPctid=100;};
		
		 // For all projects; add groups to projects, and blocks to groups; CAS533 renamed from setGroups
		protected void setGrpPerProj(Project[] projects, int[] xGroupIDs, int[] yGroupIDs, PropsDB pp) {
		try {
			Vector<Group> list = new Vector<Group>();
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
				
				rs = tdbc2.executeQuery(qry);
				while (rs.next()) {
					int idx = rs.getInt(1);
					Group g = prj.getGroupByID(idx);
					if (g == null)
						g = new Group(idx, rs.getInt(2), rs.getString(3), rs.getInt(4),  prj.getID());
					list.add(g);
				}
				prj.setGroups( list.toArray(new Group[0]) );
				list.clear();
				rs.close();
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
				rs = tdbc2.executeQuery(qry);
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
				rs.close();
			}	
		}
		catch (Exception e) {ErrorReport.print(e, "Loading projects and groups");}
		}
		
		// For this Tile: Add blocks to a tile (cell)
		protected void setBlocks(Tile tile,  boolean swapped) {
		try {
			Group gX = (swapped ? tile.getGroup(Y) : tile.getGroup(X));
			Group gY = (swapped ? tile.getGroup(X) : tile.getGroup(Y));
			
			String qry =  "SELECT blocknum, start2, end2, start1, end1 "+
				"FROM blocks WHERE grp1_idx="+gY+" AND grp2_idx="+gX;
			ResultSet rs = tdbc2.executeQuery(qry);
			tile.clearBlocks();
			while (rs.next()) {
				int start2 = rs.getInt(2);
				int end2   = rs.getInt(3);
				int start1 = rs.getInt(4);
				int end1   = rs.getInt(5);
					
				tile.addBlock(
						rs.getInt(1), 				// blocknum 
						swapped ? start1 : start2, 	// int sX  
						swapped ? end1 : end2,		// int eX  
						swapped ? start2 : start1, 	// int sY  
						swapped ? end2 : end1);		// int eY 
			}
			rs.close();
		}
		catch (Exception e) {ErrorReport.print(e, "Loading Blocks");}
		}
		// For this tile, Add hits 
		protected void setHits(Tile tile, boolean swapped)  {
		try {
			List<DPHit> hits = new ArrayList<DPHit>();
			int xx=X, yy=Y;
			if (swapped) {xx=Y; yy=X;}
			Project pX = tile.getProject(xx),  pY = tile.getProject(yy);
			Group gX   = tile.getGroup(xx), gY = tile.getGroup(yy);
		
			String qry =
				"SELECT (h.start2+h.end2)>>1 as posX, (h.start1+h.end1)>>1 as posY,"
				+ "h.pctid, h.gene_overlap, b.block_idx, (h.end2-h.start2) as length, h.strand "+
				"FROM pseudo_hits AS h "+
				"LEFT JOIN pseudo_block_hits AS b ON (h.idx=b.hit_idx) "+
				"WHERE (h.proj1_idx="+pY.getID()+" AND h.proj2_idx="+pX.getID()+
				"       AND h.grp1_idx="+gY.getID()+" AND h.grp2_idx="+gX.getID()+")";
			ResultSet rs = tdbc2.executeQuery(qry);
			while (rs.next()) {
				int posX = 		rs.getInt(1);
				int posY = 		rs.getInt(2);
				int pctid = 	rs.getInt(3);
				int geneOlap = 	rs.getInt(4);
				long blockidx = rs.getLong(5);
				int length = 	rs.getInt(6);
				String strand = rs.getString(7);
				if (strand.equals("+/-") || strand.equals("-/+")) length = -length;
				
				DPHit hit = new DPHit(
								swapped ? posY : posX,	
								swapped ? posX : posY,	 
								pctid,	geneOlap, blockidx!=0, length);				
				hits.add(hit);
				minPctid = Math.min(minPctid, pctid); 
			}
			rs.close();	
			tile.setHits(hits.toArray(new DPHit[0]));
			hits.clear();
		}
		catch (Exception e) {ErrorReport.print(e, "Loading Blocks"); }
		}
	}
	// End DBload
}
