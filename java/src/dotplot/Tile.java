package dotplot;

import java.util.Vector;
import java.util.Iterator;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.util.Collections;

/***********************************************
 * A cell representing hits and blocks of 2 chromosomes
 */
public class Tile  {
	private final int X = Data.X, Y = Data.Y;
	
	private Group group[];  			// chromosomes for x and y axis
	private Project proj[]; 
	private Vector <ABlock> ablocks;  	// synteny blocks - rectangles around group of hits in block
	private DPHit hits[] = null;	  	// all hits with indication if in block
	
	protected Tile(Project pX, Project pY, Group grpX, Group grpY) {
		proj	   = new Project[2];
		group      = new Group[2];
		ablocks    = new Vector <ABlock> ();
		
		proj[X] = pX;
		proj[Y] = pY;
		group[X] = grpX;
		group[Y] = grpY;
	}
	// Called by Data.DBload
	protected void addBlock(int number, int sX, int eX, int sY, int eY) {		
		ablocks.add(new ABlock(this, number,sX,eX,sY,eY));
		Collections.sort(ablocks);
	}
	public boolean equals(Object obj) {
		if (obj instanceof Tile) {
			Tile b = (Tile)obj;
			return group[X].equals(b.group[X]) && group[Y].equals(b.group[Y]);
		}
		return false;
	}
	protected void clearBlocks() { ablocks.clear();}
		
	protected boolean setHits(DPHit[] hits) { 
		this.hits = hits;
		return true;
	}
	
	protected ABlock getBlock(int number) {return ablocks.get(number-1);} // Called by Plot
		
	protected int getNumBlocks() {return ablocks.size(); }// Called by Plot
		
	protected DPHit[] getHits()     { return hits; }// Called by Plot and Ablock
	
	protected int getProjID(int axis) {return group[axis].getProjID(); }// or proj.getID()
	
	protected Project getProject(int axis) {return proj[axis];} // DBload
	
	protected Group getGroup(int axis) { return group[axis]; } // Plot, ABlock, DBload
	
	protected boolean hasHits() {return (hits!=null && hits.length>0);} // Data.initialize
	
	protected void swap(int pid1, int pid2) { // Data.initialize 
		if (pid1==proj[X].getID() && pid2==proj[Y].getID()) return;
		
		Group tg = group[0]; group[0]=group[1]; group[1]=tg;
		Project tp = proj[0]; proj[0]=proj[1]; proj[1]=tp;
		for (ABlock blk : ablocks)  blk.swap();
		for (DPHit ht : hits) ht.swap();
	}
	
	public String toString() {
		int h = (hits==null) ? 0 : hits.length;
		return "Tile "+group[Y].getName()+","+group[X].getName() 
				+ " Blocks " + ablocks.size() + " Hits " + h
				+ "   " + proj[Y].getDisplayName() +"," + proj[X].getDisplayName();
	}
	
	/****************************************************************/
	// Called by Plot, Tile, Project
	protected static Tile getTile(Tile[] tiles, Group grpX, Group grpY) {
		if (tiles != null)
			for (Tile t : tiles)
				if (t.group[Data.X] == grpX && t.group[Data.Y] == grpY) 
					return t;
		return null;
	}
	/*************************************************************************/
	// Called from Data when there is a mouse selection
	protected static ABlock getBlock(Tile[] tiles, Group grpX, Group grpY,  double xUnits, double yUnits) {
		Tile t = getTile(tiles,grpX,grpY);
		ABlock b =  (t != null) ? t.getBlock(xUnits,yUnits) : null;
		return b;
	}
	private ABlock getBlock(double xUnits, double yUnits) { // called above
		synchronized (ablocks) {
			ABlock b = null;
			ABlock smallest = null;
			Iterator <ABlock> iter = ablocks.iterator();
			while (iter.hasNext()) {
				b = (ABlock)iter.next();
				if (b.contains(xUnits,yUnits)) {
					if (smallest == null || getSmallestBoundingArea(smallest,b) == b)
						smallest = b;
				}
			}
			return smallest;
		}	
	}
	private static Shape getSmallestBoundingArea(Shape s1, Shape s2) { // called above
		if (s1 == null) return s2;
		if (s2 == null) return s1;

		Rectangle2D b1, b2;
		b1 = s1.getBounds2D();
		b2 = s2.getBounds2D();

		return (b1.getWidth() * b1.getHeight()) <= (b2.getWidth() * b2.getHeight()) ? s1 : s2;
	}
}
