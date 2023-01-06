package dotplot;

/***********************************************
 * Used by Tile.IBlock class
 * CAS531 removed dead stuff
 */
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

public abstract class ABlock implements Shape, DotPlotConstants, Comparable<ABlock>, Cloneable {
	private int number;
	private Rectangle rect;
	private boolean rectSet;
	private Tile parent;

	protected StringBuffer summary;

	protected ABlock() { 
		super();

		summary = new StringBuffer();
		rect = new Rectangle();
		rectSet = false;
	}

	public ABlock(Tile parent, int blockNum, int sX, int eX, int sY, int eY) {    
		this();
		this.parent = parent;
		this.number = blockNum;
		rect.setFrameFromDiagonal(sX,sY,eX,eY);
		rectSet = true;
	}
	public Object clone() {
		ABlock b = null;
		try {
			b = (ABlock)super.clone();
			b.number = number;
			b.rect = (Rectangle)rect.clone();
			b.rectSet = rectSet;
		} catch (CloneNotSupportedException e) { e.printStackTrace(); }
		return b;
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
	
	// InnerBlock which is implemented by Tile.IBlock which extends ABlock
	public Tile getParent() {return parent;}
	public Group getGroup(int axis) {return parent.getGroup(axis);}
	
	public String getName() {
		return parent.getGroup(Y).getName()+"."+parent.getGroup(X).getName()+"."+getNumber();
	}
	public int getStart(int axis) {
		return (axis == X ? rect.x : rect.y);
	}
	public int getEnd(int axis) {
		return (axis == X ? rect.x+rect.width : rect.y+rect.height);
	}
	public int getNumber() {return number;}
	public int getX() {return rect.x;}
	public int getY() {return rect.y;}
	public int getWidth() {return rect.width;}
	public int getHeight() {return rect.height;}

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
	public boolean contains(DPHit h) {
		return contains(h.getX(),h.getY());
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
	public boolean equalRectangles(ABlock ablock, int pad) {
		Rectangle r = ablock.rect;
		return closeTo(rect.x,r.x,pad) && closeTo(rect.y,r.y,pad) && closeTo(rect.x+rect.width,r.x+r.width,pad) && closeTo(rect.y+rect.height,r.y+r.height,pad);
	}
	private static boolean closeTo(int n1, int n2, int pad) {
		return (n1 < n2) ? n2 - n1 <= pad : n1 - n2 <= pad;
	}
	public String toString() {
		return "[ABlock #"+number+" ("+rect.x+","+rect.y+") - ("+(rect.x+rect.width)+","+(rect.y+rect.height)+")]";
	}
	protected void clear() {
		rect.setRect(0,0,0,0);
		rectSet = false;
	}
	public abstract DPHit[] getHits(boolean includeRepetitive, boolean onlyBlock);
}
