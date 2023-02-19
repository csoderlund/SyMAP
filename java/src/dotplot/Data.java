package dotplot;

import java.awt.Dimension;
import java.awt.Shape;
import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.util.Vector;

import database.DBconn;
import props.ProjectPair;
import props.ProjectPool;
import symap.Globals;
import symap.frame.SyMAP2d;
import symap.mapper.HitFilter;
import symap.sequence.Sequence;
import util.ErrorReport;
import util.Utilities;

/**
 * This contains the arrays of data (Project and Tile) and interface code with Filter
 * CAS533 Removed Observable, removed Loader (was painting tile at time); rearranged 
 */
public class Data  {
	public static final double DEFAULT_ZOOM = 0.99;
	public static final int X  = 0, Y   = 1;
	private final String dbStr = Globals.DB_CONN_DOTPLOT;
	
	private Project projects[]; // loaded data
	private Tile[] tiles;
	
	private Group currentGrp[];	// selected tile, 2 grps
	private Project currentProjY; 
	private int maxGrps=0;
	
	private SyMAP2d symap;
	private ProjectPool projProps;
	private DBconn dbReader;
	private FilterData filtData;
	
	private double sX1, sX2, sY1, sY2;
	private Shape selectedBlock;
	private double zoomFactor;
	private boolean hasSelectedArea, isTileView;
	private boolean isScaled;
	private double scaleFactor = 1;
	private double minPctId=100;
	
	// CAS533 added this in order to replace the massive Loader.java; it no longer draws tite by tile
	private DotPlotDBUser dbUser;
	
	// Called from DotPlotFrame
	public Data(DotPlotDBUser db) {
		dbUser = db;
		
		projects      = null; 
		tiles         = new Tile[0];
		zoomFactor    = DEFAULT_ZOOM;
		hasSelectedArea = false;
		isTileView      = false;
		currentGrp    = new Group[] {null,null};
		currentProjY  = null; 
		selectedBlock = null;

		isScaled = false;
		scaleFactor  = 1;

		try { // ugh - symap is used in different contexts, so needs to be active
			dbReader = DBconn.getInstance(dbStr, dbUser.getDBconn());
			symap = new SyMAP2d(dbReader, null);
		} catch (Exception e) {ErrorReport.print(e, "Unable to create SyMAP instance");}
		
		projProps = symap.getDrawingPanel().getPools().getProjectPropPool(); // ugh
	}
	/*************************************************************
	 *  DotPlotFrame (genome), SyMAPFrameCommon (groups), Data.setReference (change ref)
	 * @param xGroupIDs, yGroupIDs null if genome
	 * CAS533 for blocks and hits:
	 * 		this was loader.execute(projProps,projects,tiles,sb,true);added swap instead of reading database again
	 */
	public void initialize(int[] projIDs, int[] xGroupIDs, int[] yGroupIDs) {
	try {
		clear();
		
	/* init projects*/
		Vector<Project> newProjects = new Vector<Project>(projIDs.length);
		for (int i = 0;  i < projIDs.length;  i++) {// 1 -> 0
			Project p = Project.getProject(projects, 1, projIDs[i]);
			if (p == null) p = new Project(projIDs[i], dbReader);
			newProjects.add( p );
		}
		projects = newProjects.toArray(new Project[0]);
	
		dbUser.setGrpPerProj(projects, xGroupIDs, yGroupIDs, projProps); // needs to redone for ref
		for (Project p : projects) 
			maxGrps = Math.max(p.getNumGroups(), maxGrps);
		
	/* init tiles */
		tiles = Project.createTiles(projects, tiles, projProps);

		long time = Utilities.getNanoTime();
		
	/* add blocks and hits to tiles */
		for (Tile tObj : tiles) {
			boolean bSwap = projProps.isSwapped(tObj.getProjID(X), tObj.getProjID(Y)); // if DB.pairs.proj1_idx=proj(Y)
			if (tObj.hasHits()) tObj.swap(tObj.getProjID(X), tObj.getProjID(Y));
			else {
				dbUser.setBlocks(tObj, bSwap);

				dbUser.setHits(tObj, bSwap);
			}
		}
		prt("Load dotplot: " + Utilities.getTimeStr(time));

		filtData = new FilterData(); 
		minPctId = dbUser.getMinPctID();
		filtData.setBounds(minPctId, 100);
	
		if (getNumVisibleGroups() == 2) selectTile(100, 100); // kludge; this sets current...
	} catch (Exception e) { ErrorReport.print(e,"Initialize"); }
	}
	public double getMinPctid() { return minPctId; }
	/***************************************************************************/
	private void clear() { // initialize, kill
		scaleFactor = 1;
		zoomFactor = DEFAULT_ZOOM;
		hasSelectedArea = false;
		isTileView = false;
		selectedBlock= null;
		sX1 = sX2 = sY1 = sY2 = 0;
		currentGrp[0] = currentGrp[1] = null;
		currentProjY = null;	
		maxGrps=0;
	}
	/***********************************************************************/
	// Select for 2D
	private void show2dBlock() {
		if (selectedBlock==null) return;
		
		symap.getDrawingPanel().setMaps(1);
		symap.getHistory().clear(); 
		
		if (selectedBlock instanceof ABlock) {
			ABlock ib = (ABlock)selectedBlock;
			Project pX = projects[X];
			Project pY = getCurrentProj();
			Group gX = ib.getGroup(X);
			Group gY = ib.getGroup(Y);

			HitFilter hd = new HitFilter (); // CAS530 use 2D filter
			hd.setForDP(true, false);
			
			try { // CAS531 need to recreate since I changed the Hits code; bonus, allows multiple 2d displays
				DBconn dr = DBconn.getInstance(dbStr, dbUser.getDBconn());
				symap = new SyMAP2d(dr, null);
			} catch (Exception e) {ErrorReport.print(e, "Unable to create SyMAP instance");}
			
			symap.getDrawingPanel().setHitFilter(1,hd);
			
			Sequence.setDefaultShowAnnotation(false); 
			prt("Block#" + ib.getNumber() + " " + ib.getStart(Y) + " " + ib.getEnd(Y)+ " " 
					+ ib.getStart(X) + " " + ib.getEnd(X));
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
		
		try {
			DBconn dr = DBconn.getInstance(dbStr, dbUser.getDBconn());
			symap = new SyMAP2d(dr, null);
		} catch (Exception e) {ErrorReport.print(e, "Unable to create SyMAP instance");}
		
		symap.getDrawingPanel().setMaps(1);
		symap.getHistory().clear(); 
		
		HitFilter hd = new HitFilter(); // CAS530 use 2D filter
		hd.setForDP(false, true);
		symap.getDrawingPanel().setHitFilter(1,hd);
		Sequence.setDefaultShowAnnotation(false); 
		
		symap.getDrawingPanel().setSequenceTrack(1,pY.getID(),Integer.parseInt(track[Y]),Color.CYAN);
		symap.getDrawingPanel().setSequenceTrack(2,pX.getID(),Integer.parseInt(track[X]),Color.GREEN);
		symap.getDrawingPanel().setTrackEnds(1,y1,y2);
		symap.getDrawingPanel().setTrackEnds(2,x1,x2);
		
		symap.getFrame().showX();
	}
	
	/*****************************************
	 * DotPlotFrame
	 */
	public SyMAP2d getSyMAP() { return symap; }
	public Project[] getProjects() { return projects; }
	public void kill() {
		clear();
		tiles = new Tile[0]; 
		if (projects != null) {
			for (Project p : projects)
				if (p != null) p.setGroups(null);
		}

		if (symap != null) {
			symap.getDatabaseReader().close();
			symap = null; 
		}
		dbUser.getDBconn().close();
	}

	/*****************************************
	 * XXX Control
	 */
	public void setHome() {
		isTileView = false;
		zoomFactor = DEFAULT_ZOOM;
		selectedBlock = null;
		hasSelectedArea = false;
		// filtData.setDefaults();
	}
	public void setReference(Project reference) {
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
	public void setScale(boolean scale) {this.isScaled = scale;}
	public void setZoom(double zoom)    {this.zoomFactor = zoom; }; // Control and Plot

	public void factorZoom(double mult) {
		if (mult == 1) return;
		
		zoomFactor *= mult;
	}
	
	/***********************************************
	 * XXX Plot
	 */
	public boolean hasSelectedArea()  {return hasSelectedArea;}
	public ABlock  getSelectedBlock() {return (ABlock) selectedBlock;}
	public boolean isTileView() 	  {return isTileView;} // plot and Control
	
	public void    show2dArea() 	  {show2dArea(sX1,sY1,sX2,sY2);}
		
	// Plot.PlotListener mouseClick
	public boolean selectTile(long xUnits, long yUnits) {
		if (projects[X] == null || projects[Y] == null) return false;

		currentGrp[X] = projects[X].getGroupByOffset(xUnits);
		
		currentGrp[Y] = getGroupByOffset(yUnits, getVisibleGroupsY(getVisibleGroups(X)));
		currentProjY  = getProjectByID(currentGrp[Y].getProjID());
		
		if (currentGrp[X] != null && currentGrp[Y] != null && currentProjY != null) {
			zoomFactor = DEFAULT_ZOOM;
			isTileView = true;
		}
		return true;
	}
	public boolean selectBlock(double xUnits, double yUnits) {
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
	public boolean selectArea(Dimension size, double x1, double y1, double x2, double y2) {
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
	
	public boolean clearSelectedArea() {
		boolean rc = hasSelectedArea;
		hasSelectedArea = false;
		return rc;
	}
	/***************************************************************************/
	public boolean isGroupVisible(Group g) { // plot and data
		return (g.isVisible() && (g.hasBlocks() || filtData.isShowEmpty()));
	}
	private boolean isGroupVisible(Group gY, Group[] gX) { // data
		return (gY.isVisible() && (gY.hasBlocks(gX) || filtData.isShowEmpty()));
	}
	public int getNumVisibleGroups() { // control and data
		int count = 0;
		for (Project p : projects)
			count += p.getNumVisibleGroups();
		return count;
	}
	public Group[] getVisibleGroups(int axis) { // plot and data
		int num = getProject(axis).getNumGroups();
		Vector<Group> out = new Vector<Group>(num);
		
		for (int i = 1;  i <= num ;  i++) {
			Group g = projects[axis].getGroupByOrder(i);
			if (isGroupVisible(g))
				out.add(g);
		}
		
		return out.toArray(new Group[0]);
	}
	public Group[] getVisibleGroupsY(Group[] xGroups) { // plot and data
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
	public long getVisibleGroupsSizeY(Group[] xGroups) { // plot.setDims
		long size = 0;
		for (Group g : getVisibleGroupsY(xGroups))
			size += g.getEffectiveSize();
		return size;
	}
	public long getVisibleGroupsSize(int axis) { // plot.setDims
		long size = 0;
		for (Group g : getVisibleGroups(axis))
			size += g.getEffectiveSize();
		return size;
	}
	public long getVisibleGroupsSize(int yAxis, Group[] xGroups) { // plot.paintComponenet
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
	public FilterData getFilterData() { return filtData; } // plot and Filter.FilterListener
	public Project getProject(int axis) { return projects[axis]; } // plot and data
	public int getNumProjects() { return projects.length; } // plot and data
	
	public Group getCurrentGrp(int axis) 	{return currentGrp[axis];}
	public Project getCurrentProj() 		{return currentProjY;}
	public int getCurrentGrpSize(int axis) {
		return currentGrp[axis] == null ? 0 : currentGrp[axis].getGrpLenBP();
	}
	public int getMaxGrps() { return maxGrps;}
	public double getX1() { return sX1; } // plot
	public double getX2() { return sX2; }
	public double getY1() { return sY1; }
	public double getY2() { return sY2; }
	public Tile[] getTiles()  		{ return tiles;       }
	
	public double getZoomFactor() 	{ return zoomFactor;  }
	public double getScaleFactor()  { return scaleFactor; }
	public boolean isScaled() 		{ return isScaled;    }
	public int getDotSize()			{return filtData.getDotSize();}
	
	public ProjectPair getProjectPair(int x, int y) {
		return projProps.getProjectPair(projects[x].getID(),projects[y].getID());
	}
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
	
	private void prt (String msg) {
		if (Globals.DEBUG) System.out.println("Data: " + msg);
	}
}
