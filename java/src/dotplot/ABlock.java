package dotplot;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/***********************************************
 * Represents the rectangle of a block (does not contains its hits)
 *      
 * The Shape is used in Tile, see getSmallestBoundingArea
 */

public class ABlock implements Shape, Comparable<ABlock> {
	private final int X = Data.X, Y = Data.Y;
	
	private int number;
	private Rectangle rect;
	
	private Tile tileObj;
	int sX, eX, sY, eY; // for swap reference

	protected ABlock(Tile parent, int blockNum, int sX, int eX, int sY, int eY) {    
		this.sX=sX; this.eX=eX; this.sY=sY; this.eY=eY;
		rect = new Rectangle();
		
		this.tileObj = parent;
		this.number = blockNum;
		rect.setFrameFromDiagonal(sX,sY,eX,eY);
	}
	protected void swap() { 
		int t=sX; sX=sY; sY=t;
		    t=eX; eX=eY; eY=t;
		rect.setFrameFromDiagonal(sX,sY,eX,eY);
	}
	
	public int compareTo(ABlock a) {
		return number - a.number;
	}
	public boolean equals(Object obj) {
		if (obj instanceof ABlock) {
			ABlock b = (ABlock)obj;
			return number == b.number && rect.equals(b.rect);
		}
		return false;
	}
	
	protected String getName() {
		return tileObj.getGroup(Y).getName()+"."+tileObj.getGroup(X).getName()+"."+getNumber();
	}
	// Called by Plot and Data
	protected int getStart(int axis) {return (axis == X ? rect.x : rect.y);}
	protected int getEnd(int axis)   {return (axis == X ? rect.x+rect.width : rect.y+rect.height);}
	protected int getNumber()        {return number;}
	protected String getNumberStr()  {return number+"";}
	protected Group getGroup(int axis) {return tileObj.getGroup(axis);}

	////// Shape
	public boolean contains(double x, double y) {
		return x >= rect.x && x <= (rect.x+rect.width) && y >= rect.y && y <= (rect.y+rect.height);
	}
	public boolean contains(double x, double y, double w, double h) {
		return contains(x,y) && contains(x+w,y) && contains(x,y+h) && contains(x+w,y+h);
	}
	public boolean contains(Point2D p) {
		return contains(p.getX(),p.getY());
	}
	public boolean contains(Rectangle2D r) {
		return contains(r.getX(),r.getY(),r.getWidth(),r.getHeight());
	}
	public Rectangle getBounds() {
		return rect.getBounds();
	}
	public Rectangle2D getBounds2D() {
		return rect.getBounds2D();
	}
	public PathIterator getPathIterator(AffineTransform at) {
		return rect.getPathIterator(at);
	}
	public PathIterator getPathIterator(AffineTransform at, double flatness) {
		return rect.getPathIterator(at,flatness);
	}
	public boolean intersects(double x, double y, double w, double h) {
		return rect.intersects(x,y,w,h);
	}
	public boolean intersects(Rectangle2D r) {
		return rect.intersects(r);
	}
	public boolean intersects(ABlock ablock) {
		return rect.intersects(ablock.rect);
	}
	public boolean contains(ABlock ablock) {
		return contains(ablock.rect);
	}

	// other
	public String toString() {
		return "[ABlock #"+number+" ("+rect.x+","+rect.y+") - ("+(rect.x+rect.width)+","+(rect.y+rect.height)+")]";
	}
	protected void clear() {
		rect.setRect(0,0,0,0);
	}
}
