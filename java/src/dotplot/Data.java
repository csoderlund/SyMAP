/**
 * 
 */
package dotplot;

import java.awt.Dimension;
import java.awt.Shape;
import java.awt.Color;
import java.awt.geom.Rectangle2D;

import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;

import java.util.Observable;
import java.util.Observer;
import java.util.Vector;

import java.sql.SQLException;

import symap.SyMAP;
import symap.SyMAPConstants;
import symap.pool.ProjectProperties;

import symap.sequence.Sequence;
import util.DatabaseReader;
import util.Utilities;
import arranger.DPArrangerResults;
import arranger.SyArranger;
import arranger.SubBlock;

public class Data extends Observable implements DotPlotConstants {
	public static final double DEFAULT_ZOOM = 0.99;

	private SyMAP symap;
	private ProjectProperties projProps;
	private FilterData fd;
	private ScoreBounds sb;
	private Project projects[];
	private Tile[] tiles;
	private double zoomFactor;
	private Shape selectedBlock;
	private boolean hasSelectedArea;
	private boolean isZoomed;
	private double x1, x2, y1, y2;
	private Group currentGrp[];
	private Project currentProjY; 
	private boolean canPaint;
	private boolean isCentering = false;
	private boolean isScaled;
	private double scaleFactor;
	private int dotSize = 1;
	private Loader loader;
	private boolean onlyShowBlocksWhenHighlighted = false;
	private Vector<Plot> plots = new Vector<Plot>();
	
	public Data(DotPlotDBUser db) {
		this(new Loader(db));
	}

	public Data(Loader l) {
		fd            = new FilterData();
		sb            = new ScoreBounds();
		projects      = null; 
		tiles         = new Tile[0];
		zoomFactor    = DEFAULT_ZOOM;
		hasSelectedArea = false;
		isZoomed      = false;
		canPaint      = false;
		currentGrp    = new Group[] {null,null};
		currentProjY  = null; 
		selectedBlock = null;

		isScaled = false;
		scaleFactor  = 1;

		loader = l;

		//if (loader == null) CAS507
		//	loader = new Loader(new DotPlotDBUser(), Loader.APPLICATION);

		loader.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				update();
			}
		});

		fd.addObserver(new Observer() {
			public void update(Observable o, Object arg) {
				Data.this.update();
			}
		});

		try {
			symap = new SyMAP(DatabaseReader.getInstance(
							SyMAPConstants.DB_CONNECTION_DOTPLOT_2D,
							getDotPlotDBUser().getDatabaseReader()), null);
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Unable to create SyMAP instance");
			throw new RuntimeException("Unable to create SyMAP instance");
		}

		projProps = symap.getDrawingPanel().getPools().getProjectProperties();
	}

	public void addObserver(Observer o) {
		if (o instanceof Plot) plots.add((Plot)o);
		super.addObserver(o);
	}

	public void deleteObserver(Observer o) {
		if (o instanceof Plot) plots.remove(o);
		super.deleteObserver(o);
	}

	public void deleteObservers() {
		plots.clear();
		super.deleteObservers();
	}

	public void setOnlyShowBlocksWhenHighlighted(boolean val) {
		onlyShowBlocksWhenHighlighted = val;
	}

	public boolean isOnlyShowBlocksWhenHighlighted() {
		return onlyShowBlocksWhenHighlighted;
	}

	public DotPlotDBUser getDotPlotDBUser() {
		return loader.getDB();
	}

	public Loader getLoader() {
		return loader;
	}

	public void kill() {
		clear();
		tiles = new Tile[0]; 
		if (projects != null) {
			for (Project p : projects)
				if (p != null) p.setGroups(null);
		}
		loader.kill();
		if (symap != null) {
			symap.getDatabaseReader().close();
			symap = null; 
		}
		getDotPlotDBUser().getDatabaseReader().close();
	}

	public void clear() {
		loader.stop();

		Filter.hideFilter(this);

		scaleFactor = 1;

		zoomFactor = DEFAULT_ZOOM;
		hasSelectedArea = false;
		isZoomed = false;
		selectedBlock= null;
		x1 = x2 = y1 = y2 = 0;
		if (currentGrp != null)
			currentGrp[0] = currentGrp[1] = null;
		currentProjY = null;	
		
		update();
		canPaint = false;
	}

	public SyMAP getSyMAP() { return symap; }
	public boolean isCentering() { return isCentering; }
	public boolean canPaint() { return canPaint; }
	
	public void initialize(int[] projIDs, int[] xGroupIDs, int[] yGroupIDs) {
		try {
			clear();
			initProjects(projIDs, xGroupIDs, yGroupIDs);
			loader.execute(projProps,projects,tiles,sb,true);
			
			if (getNumVisibleGroups() == 2)
				selectTile(100, 100); // kludge
			
		} catch (SQLException e) { sqlError(e,null); }
	}

	private void initProjects(int[] projIDs, int[] xGroupIDs, int[] yGroupIDs) throws SQLException {
		Vector<Project> newProjects = new Vector<Project>(projIDs.length);
		if (projects != null && projects[0].getID() == projIDs[0])
			newProjects.add( projects[0] );
		else 
			newProjects.add( new Project(projIDs[0], projProps) );
		for (int i = 1;  i < projIDs.length;  i++) {
			Project p = Project.getProject(projects, 1, projIDs[i]);
			if (p == null)
				p = new Project(projIDs[i], projProps);
			
			// kludge for FPC (x-axis) to Pseudo from 3D frame
			if (i == 1 && newProjects.get(0).isFPC() && p.isFPC())
				continue;
			
			newProjects.add( p );
		}
		projects = newProjects.toArray(new Project[0]);
		
		getDotPlotDBUser().setProjects(projects, xGroupIDs, yGroupIDs, projProps);
		
		scaleFactor = 1;

		canPaint = true;
		tiles = Project.getGroupPairs(projects, tiles, projProps);

		update();
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

	private void zoomBlock() {
		symap.getDrawingPanel().setMaps(1);
		symap.getHistory().clear(); 
		if (selectedBlock != null) {
			if (selectedBlock instanceof InnerBlock) {
				InnerBlock ib = (InnerBlock)selectedBlock;
				Project pX = projects[X];
				Project pY = getCurrentProj();
				Group gX = ib.getGroup(X);
				Group gY = ib.getGroup(Y);

				FilterData fd2 = new FilterData(fd);
				fd2.setShowHits(FilterData.BLOCK_HITS); 
				symap.getDrawingPanel().setHitFilter(1,fd2);
				Sequence.setDefaultShowAnnotation(false); 

				if (pX.isPseudo() && pY.isPseudo()) { // PSEUDO to PSEUDO
					symap.getDrawingPanel().setSequenceTrack(1,pY.getID(),gY.getID(),Color.CYAN);
					
					symap.getDrawingPanel().setSequenceTrack(2,pX.getID(),gX.getID(),Color.GREEN);
					
					symap.getDrawingPanel().setTrackEnds(1,ib.getStart(Y),ib.getEnd(Y));
					
					symap.getDrawingPanel().setTrackEnds(2,ib.getStart(X),ib.getEnd(X));
				}
				else if (pX.isPseudo() && pY.isFPC()) { // FPC to PSEUDO
					symap.getDrawingPanel().setBlockTrack(1,pY.getID(),ib.getName(),Color.CYAN);
					symap.getDrawingPanel().setSequenceTrack(2,pX.getID(),gX.getID(),Color.GREEN);
					symap.getDrawingPanel().setTrackEnds(2,ib.getStart(X),ib.getEnd(X));
				}
				else if (pX.isFPC() && pY.isPseudo()) { // PSEUDO to FPC 
					symap.getDrawingPanel().setBlockTrack(1,pX.getID(),swapGroupsInBlockName(ib.getName()),Color.CYAN);
					symap.getDrawingPanel().setSequenceTrack(2,pY.getID(),gY.getID(),Color.GREEN);
					symap.getDrawingPanel().setTrackEnds(2,ib.getStart(Y),ib.getEnd(Y));
				}
				else {// FPC to FPC
					symap.getDrawingPanel().setBlockTrack(1,pY.getID(),ib.getName(),Color.CYAN);
					symap.getDrawingPanel().setBlockTrack(2,pX.getID(),Utilities.getIntsString(ib.getContigNumbers(X)),Color.GREEN);
				}
				
				symap.getFrame().show();
			}
			else {
				Rectangle2D bounds = selectedBlock.getBounds2D();
				zoomArea(bounds.getMinX(),bounds.getMinY(),bounds.getMaxX(),bounds.getMaxY());
			}
		}
	}
	
	private String swapGroupsInBlockName(String name) {
		String[] fields = name.split("\\.");
		return fields[1] + "." + fields[0] + "." + fields[2];
	}

	public void zoomArea() {
		zoomArea(x1,y1,x2,y2);
	}

	private void zoomArea(double x1, double y1, double x2, double y2) {
		symap.getDrawingPanel().setMaps(1);
		symap.getDrawingPanel().setHitFilter(1,fd);
		symap.getHistory().clear(); 

		Project pX = projects[X];
		Project pY = getCurrentProj();
		String track[] = {"",""};
		int start[] = {(int)Math.floor(x1),(int)Math.floor(y1)};
		int end[]   = {(int)Math.ceil(x2),(int)Math.ceil(y2)};
		for (int n = 0;  n < 2;  n++) {
			if (projects[n].isFPC())
				track[n] = Utilities.getIntsString(Contig.getContigNumbers(currentGrp[n].getContigs(start[n],end[n])));
			else // isPseudo()
				track[n] = currentGrp[n].toString();
		}
		
		if (track[X].length() == 0 || track[Y].length() == 0)
			System.out.println("No Contigs Found! x=" + track[X] + " y=" + track[Y]);
		else {
			if (pX.isPseudo() && pY.isPseudo()) { // PSEUDO to PSEUDO
				symap.getDrawingPanel().setSequenceTrack(1,pY.getID(),Integer.parseInt(track[Y]),Color.CYAN);
				symap.getDrawingPanel().setSequenceTrack(2,pX.getID(),Integer.parseInt(track[X]),Color.GREEN);
				symap.getDrawingPanel().setTrackEnds(1,y1,y2);
				symap.getDrawingPanel().setTrackEnds(2,x1,x2);
			}
			else if (pX.isPseudo() && pY.isFPC()) { // FPC to PSEUDO
				symap.getDrawingPanel().setBlockTrack(1,pY.getID(),track[Y],Color.CYAN);
				symap.getDrawingPanel().setSequenceTrack(2,pX.getID(),Integer.parseInt(track[X]),Color.GREEN);
				symap.getDrawingPanel().setTrackEnds(2,x1,x2);
			}
			else if (pX.isFPC() && pY.isPseudo()) { // PSEUDO to FPC 
				symap.getDrawingPanel().setBlockTrack(1,pX.getID(),track[X],Color.CYAN);
				symap.getDrawingPanel().setSequenceTrack(2,pY.getID(),Integer.parseInt(track[Y]),Color.GREEN);
				symap.getDrawingPanel().setTrackEnds(2,y1,y2);
			}
			else { // FPC to FPC
				symap.getDrawingPanel().setBlockTrack(1,pY.getID(),track[Y],Color.CYAN);
				symap.getDrawingPanel().setBlockTrack(2,pX.getID(),track[X],Color.GREEN);
			}
			symap.getFrame().show();
		}
	}

	public void resetAll() {
		zoomFactor = DEFAULT_ZOOM;
		selectedBlock = null;
		fd.setDefaults(sb);
		update();
	}

	public void update() {
		setChanged();
		notifyObservers();
	}	

	private void sqlError(SQLException e, String q) {
		if (e != null)
			e.printStackTrace();
		throw new RuntimeException("Error running SQL Command"+(q == null ? "" : ": "+q));
	}

	public boolean isLoading() {
		return loader.isLoading();
	}

	public boolean isZoomed() {
		return isZoomed;
	}
	
	public static Group getGroupByOffset(long offset, Group[] groups) {
		if (groups != null)
			for (int i = groups.length-1; i >= 0; i--)
				if (groups[i].getOffset() < offset) return groups[i];
		return null;
	}

	public void selectTile(long xUnits, long yUnits) {
		if (projects[X] == null || projects[Y] == null) return ;

		currentGrp[X] = projects[X].getGroupByOffset(xUnits);
		
		currentGrp[Y] = getGroupByOffset(yUnits, getVisibleGroupsY(getVisibleGroups(X)));
		currentProjY = getProjectByID(currentGrp[Y].getProjID());
		
		if (currentGrp[X] != null && currentGrp[Y] != null && currentProjY != null) {
			zoomFactor = DEFAULT_ZOOM;
			isZoomed = true;
			update();
		}
	}

	public int getDotSize() {
		return dotSize;
	}

	public void setDotSize(int ds) {
		if (ds != dotSize) {
			dotSize = ds;
			update();
		}
	}

	public Group getCurrentGrp(int axis) {
		return currentGrp[axis];
	}
	
	public Project getCurrentProj() {
		return currentProjY;
	}

	public int getCurrentGrpSize(int axis) {
		return currentGrp[axis] == null ? 0 : currentGrp[axis].getSize();
	}

	public void selectBlock(double xUnits, double yUnits) {
		Shape shapes[] = new Shape[DotPlot.TOT_RUNS];
		Shape s = null;
		if (fd.isShowBlocks() && !hasSelectedArea && isZoomed() 
				&& (!isOnlyShowBlocksWhenHighlighted() || fd.isHighlightAnyBlockHits())) 
		{
			for (int i = 0; i < shapes.length; i++) {
				shapes[i] = !isOnlyShowBlocksWhenHighlighted() || fd.isHighlightBlockHits(i) ? 
						Tile.getABlock(tiles,currentGrp[X],currentGrp[Y],i,xUnits,yUnits) : null;
			}
			
			s = Utilities.getSmallestBoundingArea(shapes);
			if (s != null) {
				if (s == selectedBlock)
					zoomBlock();
				else if (s instanceof ABlock) {
					if (DotPlot.RUN_SUBCHAIN_FINDER && !(s instanceof SubBlock) && fd.isHighlightSubChains())
						new SyArranger().arrange(new DPArrangerResults(Tile.getTile(tiles,currentGrp[X],currentGrp[Y]),(ABlock)s));
				}
			}
		}
		selectedBlock = s;

		update();
	}
	
	public Tile[] getTiles()  		{ return tiles;       }
	public double getZoomFactor() 	{ return zoomFactor;  }
	public double getScaleFactor()  { return scaleFactor; }
	public boolean isScaled() 		{ return isScaled;    }

	public void setHome() {
		isZoomed = false;
		zoomFactor = DEFAULT_ZOOM;
		selectedBlock = null;
		hasSelectedArea = false;
		update();
	}

	public void setScale(boolean scale) {
		if (this.isScaled != scale) {
			this.isScaled = scale;
			update();
		}
	}

	public void setZoom(double zoom) {
		if (this.zoomFactor != zoom) {
			this.zoomFactor = zoom;
			update();
		}
	}
	
	public void factorZoom(double mult) {
		if (mult != 1) {
			isCentering = true;

			Plot plots[] = (Plot[])this.plots.toArray(new Plot[0]);
			for (int i = 0; i < plots.length; i++)
				plots[i].saveCenter();

			zoomFactor *= mult;
			update();

			isCentering = false;

			for (int i = 0; i < plots.length; i++)
				plots[i].restoreCenter();
		}
	}

	public boolean hasSelectedArea() {
		return hasSelectedArea;
	}

	public void clearSelectedArea() {
		if (hasSelectedArea) {
			hasSelectedArea = false;
			update();
		}
	}

	public void clearSelectedBlock() {
		if (selectedBlock != null) {
			selectedBlock = null;
			update();
		}
	}

	public void selectArea(Dimension size, double x1, double y1, double x2, double y2) {
		double xmax = currentGrp[X].getSize();
		double ymax = currentGrp[Y].getSize();
		double xfactor = size.getWidth() / xmax;
		double yfactor = size.getHeight() / ymax;

		selectedBlock = null;
		hasSelectedArea = true;
		this.x1 = Math.min(x1,x2) / xfactor;
		this.x2 = Math.max(x1,x2) / xfactor;
		this.y1 = Math.min(y1,y2) / yfactor;
		this.y2 = Math.max(y1,y2) / yfactor;

		//keep inside bounds, or ignore if completely outside
		if (this.x1 < 0) this.x1 = 0;
		if (this.y1 < 0) this.y1 = 0;
		if (this.x2 > xmax) this.x2 = xmax;
		if (this.y2 > ymax) this.y2 = ymax;
		if (this.x1 > xmax || this.x2 < 0 || this.y1 > ymax || this.y2 < 0)
			hasSelectedArea = false;
	}

	public ABlock getSelectedBlock() {
		return (selectedBlock instanceof ABlock) ? (ABlock)selectedBlock : null;
	}

	public FilterData getFilterData() { return fd; }
	public ScoreBounds getScoreBounds() { return sb; }
	// CAS42 12/26/17 - does not work on self dot-plots in applet, because projects[1] is not defined
	public Project getProject(int axis) { return projects[axis]; }
	public int getNumProjects() { return projects.length; } 
	public Project[] getProjects() { return projects; }
	
	
	public Project getProjectByID(int id) {
		for (Project p : projects)
			if (p.getID() == id)
				return p;
		return null;
	}
	
	public boolean isGroupVisible(Group g) {
		return (g.isVisible() && (g.hasBlocks() || fd.isShowEmpty()));
	}
	
	public boolean isGroupVisible(Group gY, Group[] gX) {
		return (gY.isVisible() && (gY.hasBlocks(gX) || fd.isShowEmpty()));
	}
	
	public int getNumVisibleGroups() {
		int count = 0;
		for (Project p : projects)
			count += p.getNumVisibleGroups();
		return count;
	}
	
	public Group[] getVisibleGroups(int axis) {
		int num = getProject(axis).getNumGroups();
		Vector<Group> out = new Vector<Group>(num);
		
		for (int i = 1;  i <= num ;  i++) {
			Group g = projects[axis].getGroupByOrder(i);
			if (isGroupVisible(g))
				out.add(g);
		}
		
		return out.toArray(new Group[0]);
	}
	
	public Group[] getVisibleGroupsY(Group[] xGroups) {
		Vector<Group> out = new Vector<Group>();
		
		if (isZoomed)
			out.add(currentGrp[Y]);
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
	
	public Group[] getVisibleGroups(int yAxis, Group[] xGroups) {
		Vector<Group> out = new Vector<Group>();
		
		int numGroups = projects[yAxis].getNumGroups();
		for (int i = 1;  i <= numGroups ;  i++) {
			Group g = projects[yAxis].getGroupByOrder(i);
			if (isGroupVisible(g, xGroups))
				out.add(g);
		}
		
		return out.toArray(new Group[0]);
	}
	
	public long getVisibleGroupsSize(int axis) {
		long size = 0;
		for (Group g : getVisibleGroups(axis))
			size += g.getEffectiveSize();
		return size;
	}
	
	public long getVisibleGroupsSize(int yAxis, Group[] xGroups) {
		long size = 0;
		for (Group g : getVisibleGroups(yAxis, xGroups))
			size += g.getEffectiveSize();
		return size;
	}

	public long getVisibleGroupsSizeY(Group[] xGroups) {
		long size = 0;
		for (Group g : getVisibleGroupsY(xGroups))
		size += g.getEffectiveSize();
		return size;
	}

	public double getX1() { return x1; }
	public double getX2() { return x2; }
	public double getY1() { return y1; }
	public double getY2() { return y2; }
}
