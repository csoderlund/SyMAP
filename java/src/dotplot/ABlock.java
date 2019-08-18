package dotplot;

import java.util.Comparator;
import java.util.List;
import java.util.Iterator;
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

	public Tile getParent() {
		return parent;
	}

	public String getName() {
		return parent.getGroup(Y).getName()+"."+parent.getGroup(X).getName()+"."+getNumber();
	}

	public Group getGroup(int axis) {
		return parent.getGroup(axis);
	}

	protected void setSummary(StringBuffer summary) {
		this.summary = summary;
	}

	public void addToSummary(String str) {
		this.summary.append(str);
	}

	public void addLineToSummary(String str) {
		this.summary.append(str).append("\n");
	}

	public String getSummary() {
		return summary.toString();
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

	/**
	 * Method <code>compareTo</code> compares by block number
	 *
	 * @param obj an <code>Object</code> value
	 * @return an <code>int</code> value
	 */
	public int compareTo(ABlock a) {
		return number - a.number;
	}

	public int getStart(int axis) {
		return (axis == X ? rect.x : rect.y);
	}

	public int getEnd(int axis) {
		return (axis == X ? rect.x+rect.width : rect.y+rect.height);
	}

	public boolean contains(double x, double y) {
		//int eX = rect.x+rect.width; // mdb removed 6/29/07 #118
		//int eY = rect.y+rect.height; // mdb removed 6/29/07 #118
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

	public boolean contains(Hit h) {
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

	public double diagonal() {
		return util.Utilities.diagonal(rect);
	}

	public double distance(ABlock ablock) {
		return util.Utilities.distance(rect,ablock.rect);
	}

	public int getNumber() {
		return number;
	}

	public boolean equals(Object obj) {
		if (obj instanceof ABlock) {
			ABlock b = (ABlock)obj;
			return number == b.number && rect.equals(b.rect);
		}
		return false;
	}

	public boolean equalRectangles(ABlock ablock) {
		return rect.equals(ablock.rect);
	}

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

	public void merge(ABlock block) {
		if (!rectSet) {
			rect.setBounds(block.rect);
			rectSet = block.rectSet;
		}
		else rect.add(block.rect);
	}

	public void addHit(Hit hit) {
		if (!rectSet) {
			rect.setRect(hit.getX(),hit.getY(),0,0);
			rectSet = true;
		}
		else rect.add(hit.getX(),hit.getY());
	}

	public int getX() {
		return rect.x;
	}

	public int getY() {
		return rect.y;
	}

	public int getWidth() {
		return rect.width;
	}

	public int getHeight() {
		return rect.height;
	}

	protected void clear() {
		rect.setRect(0,0,0,0);
		rectSet = false;
	}

	protected void setNumber(int n) {
		number = n;
	}

	public abstract Hit[] getHits(boolean includeRepetitive, boolean onlyBlock);
	public abstract int[] getContigNumbers(int axis);

	protected void setPosition(List indexs, Hit[] hits) {
		clear();
		for (Iterator iter = indexs.iterator(); iter.hasNext(); )
			addHit(hits[((Number)iter.next()).intValue()]);
	}

	public static void normalizeNumbers(List ablocks) {
		Iterator iter = ablocks.iterator();
		for (int i = 1; iter.hasNext(); i++)
			((ABlock)iter.next()).number = i;
	}

	public static Comparator<ABlock> locationComparator() {
		return new LocComparator();
	}

	private static class LocComparator implements Comparator<ABlock> {
		private LocComparator() { }

		public int compare(ABlock a1, ABlock a2) {
			if (a1.getX() != a2.getX()) return a1.getX() - a2.getX();
			return a1.getY() - a2.getY();
		}

		public boolean equals(Object obj) {
			return obj instanceof LocComparator;
		}
	}
}
