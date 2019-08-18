package symap.contig;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Stroke;
import java.awt.BasicStroke;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.Vector;
import java.util.Arrays;
import number.GenomicsNumber;
import symap.SyMAPConstants;
import symap.mapper.Hit;
import symap.mapper.CloneHit;
import symap.track.FilteredComponent;
import symap.track.Track;
import util.Circle;
import util.PropertiesReader;
import symap.SyMAP;

/**
 * A Clone is one of the lines in the contig track view including the circles (<code>Circle</code>)
 * on the ends if any and all the associated data.
 * 
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 * @see FilteredComponent
 * @see SyMAPConstants
 */
public class Clone extends FilteredComponent implements SyMAPConstants {
	private static final String REMARK_SEP = ",";

	public static final String CLONE_REMARK_COND_FILTERED = "cone_remark_filtered";

	public static final String BOTH_BES_COND_FILTERED = "both_bes_filtered";
	public static final String BOTH_BES_CURRENT_COND_FILTERED = "both_bes_have_current_hit";

	private static final int MOUSE_PADDING = 3;

	public static Color cloneColor;
	public static Color inverseCloneColor;
	public static Color cloneNameColor;
	public static Color cloneHighlightColor;
	public static Color besInBlockColor;
	public static Color besInBlockNoCurrentColor;
	public static Color besHasHitColor;
	public static Color besRepetitiveColor;
	public static Color besNoHitColor;
	public static Color remarkHighlightColor;
	private static double circleRadius;
	private static Font cloneNameFont;
	public static final int MIN_CLONE_REMARKS;
	static {
		PropertiesReader props   = new PropertiesReader(SyMAP.class.getResource("/properties/clone.properties"));
		cloneColor               = props.getColor("cloneColor");
		inverseCloneColor        = props.getColor("inverseCloneColor");
		cloneNameColor           = props.getColor("cloneNameColor");
		cloneHighlightColor      = props.getColor("cloneHighlightColor");
		besInBlockColor          = props.getColor("besInBlockColor");
		besHasHitColor           = props.getColor("besHasHitColor");
		besRepetitiveColor       = props.getColor("besRepetitiveColor");
		besNoHitColor            = props.getColor("besNoHitColor");
		besInBlockNoCurrentColor = props.getColor("besInBlockNoCurrentColor");
		circleRadius             = props.getDouble("circleRadius");
		cloneNameFont            = props.getFont("cloneNameFont");
		remarkHighlightColor     = props.getColor("remarkHighlightColor");
		MIN_CLONE_REMARKS        = props.getInt("minCloneRemarks");
	}

	private GenomicsNumber cb[];
	private byte bes[];
	private byte besFilters[];
	private int layer;
	private Line2D.Double line;
	private StaticPoint2D midPoint;
	private Circle[] circles;
	private Vector<String> hoverMarkers;
	private Vector<String> clickedMarkers;
	private int[] cloneRemarks;
	private boolean remarkSelected = false;
	private int remarkShow         = Contig.CLONE_HIGHLIGHT;

	/**
	 * Creates a new <code>Clone</code> instance.
	 * 
	 * @param parent
	 *            a <code>Track</code> value
	 * @param condFilters
	 *            a <code>Vector</code> used as the conditional filters
	 *            vector.
	 * @param name
	 *            a <code>String</code> value
	 * @param cb1
	 *            a <code>GenomicsNumber</code> value
	 * @param cb2
	 *            a <code>GenomicsNumber</code> value
	 * @param bes1
	 *            a <code>byte</code> value
	 * @param bes2
	 *            a <code>byte</code> value
	 * @param bes1Filter
	 *            a <code>byte</code> value bit mask of filter values for the
	 *            bes
	 * @param bes2Filter
	 *            a <code>byte</code> value bit mask of the filter values for
	 *            the bes
	 * @exception IllegalArgumentException
	 *                if an error occurs
	 * @see #getBESFilter(boolean,boolean,boolean)
	 */
	public Clone(Track parent, Vector<Object> condFilters, String name, 
			GenomicsNumber cb1, GenomicsNumber cb2,
			byte bes1, byte bes2, byte bes1Filter, byte bes2Filter, 
			int[] remarkIDs) 
	throws IllegalArgumentException 
	{
		super(parent,condFilters,name,false,VERTICAL_TEXT,cloneNameFont);

		if (bes1 != R_VALUE && bes1 != F_VALUE && bes1 != NORF_VALUE)
			throw new IllegalArgumentException("BES 1 value "+bes1+" is invalid.");
		if (bes2 != R_VALUE && bes2 != F_VALUE && bes2 != NORF_VALUE)
			throw new IllegalArgumentException("BES 2 value "+bes2+" is invalid.");

		cb = new GenomicsNumber[] {cb1,cb2};
		bes = new byte[] {bes1,bes2};
		besFilters = new byte[] {bes1Filter,bes2Filter};
		circles = new Circle[] {new Circle(circleRadius),new Circle(circleRadius)};

		this.layer = 0;

		line = new Line2D.Double();
		midPoint = new StaticPoint2D();
		hoverMarkers = new Vector<String>(0,1);
		clickedMarkers = new Vector<String>(0,1);

		cloneRemarks = remarkIDs;
		Arrays.sort(cloneRemarks);
	}

	public int[] getRemarks() {
		return cloneRemarks;
	}

	public static int[] getRemarks(String remarkIDs) {
		if (remarkIDs == null || remarkIDs.length() == 0) return new int[0];
		else {
			String[] remarks = remarkIDs.split(REMARK_SEP);
			int[] cr = new int[remarks.length];
			for (int i = 0; i < remarks.length; ++i)
				cr[i] = Integer.parseInt(remarks[i]);
			return cr;
		}
	}

	public boolean hasRemark(int remarkID) {
		return Arrays.binarySearch(cloneRemarks,remarkID) >= 0;
	}

	/**
	 * Method <code>hasAnyRemarks</code> tests to see if it has any of the
	 * remarks in <code>remarks</code>.
	 * 
	 * @param remarks
	 *            an <code>CloneRemarks.CloneRemark[]</code> value must be a
	 *            sorted array
	 * @return a <code>boolean</code> value of true if the clone has any clone
	 *         remark id's that are in remarks
	 */
	public boolean hasAnyRemarks(CloneRemarks.CloneRemark[] remarks) {
		int j, i;
		for (j = 0, i = 0; i < cloneRemarks.length && j < remarks.length;) {
			if      (cloneRemarks[i] < remarks[j].getID()) ++j;
			else if (cloneRemarks[i] > remarks[j].getID()) ++i;
			else return true;
		}
		return false;
	}

	/**
	 * Method <code>hasAnyRemarks</code> tests to see if it has any of
	 * remarkIDs.
	 * 
	 * @param remarkIDs
	 *            an <code>int[]</code> value must be a sorted array
	 * @return a <code>boolean</code> value of true if the clone has any clone
	 *         remark id's that are in remarkIDs
	 */
	public boolean hasAnyRemarks(int[] remarkIDs) {
		int j, i;
		for (j = 0, i = 0; i < cloneRemarks.length && j < remarkIDs.length;) {
			if      (cloneRemarks[i] < remarkIDs[j]) ++i;
			else if (cloneRemarks[i] > remarkIDs[j]) ++j;
			else return true;
		}
		return false;
	}

	/**
	 * Method <code>setRemarkSelected</code> sets the Clone to be remark
	 * selected if it has any of the remark id's associated with it; otherwise,
	 * the clone is set to not be remark selected.
	 * 
	 * @param remarkIDs
	 *            an <code>int[]</code> value
	 * @param show
	 *            a <code>int</code> value of Contig.CLONE_SHOW,
	 *            Contig.CLONE_HIDE, Contig.CLONE_HIGHLIGHT
	 * @return a <code>boolean</code> value of true if the Clone was set to be
	 *         remark selected.
	 */
	public boolean setSelectedRemark(int[] remarkIDs, int show) {
		remarkShow = show;
		if (remarkIDs != null && remarkIDs.length > 0) {
			int j, i;
			for (j = 0, i = 0; i < cloneRemarks.length && j < remarkIDs.length;) {
				if      (cloneRemarks[i] < remarkIDs[j]) ++i;
				else if (cloneRemarks[i] > remarkIDs[j]) ++j;
				else {
					remarkSelected = true;
					return true;
				}
			}
		}
		remarkSelected = false;
		return false;
	}

	public String getHoverMessage(Point2D p) {
		if (circles[0].contains(p) && bes[0] != NORF_VALUE && hasHit(besFilters[0]))
			return ((Contig)parent).getCloneHoverMessage(getName(),bes[0]);
		if (circles[1].contains(p) && bes[1] != NORF_VALUE && hasHit(besFilters[1]))
			return ((Contig)parent).getCloneHoverMessage(getName(),bes[1]);
		int min = getMinDistanceBetweenHitsShowing();
		if (min > 0)
			return "Clone "+getName()+ " "+getLength()+"CB"+", minimum "+min+"BP between shown hits";
		return "Clone "+getName()+" "+getLength()+"CB";
	}

	public int getMinDistanceBetweenHitsShowing() {
		//boolean rFound = false; // mdb removed 6/29/07 #118
		//boolean fFound = false; // mdb removed 6/29/07 #118
		Hit hit;
		int ind;
		int rf[][] = {new int[hits.size()],new int[hits.size()]};
		int rfs[] = {0,0};
		for (int i = 0; i < hits.size(); i++) {
			hit = (Hit)hits.get(i);	    
			if (hit instanceof CloneHit && !hit.isFiltered()) {
				ind = getIndex(((CloneHit)hit).getBES());
				rf[ind][rfs[ind]++] = ((CloneHit)hit).getPos2();
			}
		}
		ind = Integer.MAX_VALUE;
		for (int i = 0; i < rfs[0]; i++)
			for (int j = 0; j < rfs[1]; j++)
				ind = Math.min(Math.abs(rf[0][i]-rf[1][j]),ind);
		return ind == Integer.MAX_VALUE ? -1 : ind;
	}

	/**
	 * Method <code>setMarkerHover</code> sets this clone to have the given
	 * marker to be hovered or not hovered.
	 * 
	 * @param hover
	 *            a <code>boolean</code> value whether the marker is being
	 *            hovered
	 * @param markerName
	 *            a <code>String</code> value the name of the marker being
	 *            hovered/not hovered
	 */
	public void setMarkerHover(boolean hover, String markerName) {
		if (!hover) hoverMarkers.remove(markerName);
		else if (!hoverMarkers.contains(markerName)) hoverMarkers.add(markerName);
	}

	/**
	 * Method <code>isMarkerHover</code> returns if this clone has a marker
	 * being hovered.
	 * 
	 * @return a <code>boolean</code> value
	 */
	public boolean isMarkerHover() {
		return !hoverMarkers.isEmpty();
	}

	/**
	 * Method <code>setMarkerClicked</code> sets this clone to have the given
	 * marker being in a clicked state
	 * 
	 * @param clicked
	 *            a <code>boolean</code> value of whether the marker is
	 *            clicked
	 * @param markerName
	 *            a <code>String</code> value the name of the marker that is
	 *            clicked or not clicked
	 */
	public void setMarkerClicked(boolean clicked, String markerName) {
		if (!clicked) clickedMarkers.remove(markerName);
		else if (!clickedMarkers.contains(markerName)) clickedMarkers.add(markerName);
	}

	/**
	 * Method <code>isMarkerClicked</code> returns if this clone has a marker
	 * that is in a clicked state.
	 * 
	 * @return a <code>boolean</code> value
	 */
	public boolean isMarkerClicked() {
		return !clickedMarkers.isEmpty();
	}

	/**
	 * Method <code>offset</code> offset the clone along the x axis by offsetX
	 * 
	 * @param offsetX
	 *            a <code>double</code> value amount to offset
	 */
	public void offset(double offsetX) {
		line.x1 += offsetX;
		line.x2 += offsetX;
		midPoint.x += offsetX;
		circles[0].offset(offsetX,0);
		circles[1].offset(offsetX,0);
		offsetText(offsetX);
	}

	/**
	 * Flips the clone
	 * 
	 * @param end
	 */
	public void flip(long end) {
		cb[0].setValue(end - cb[0].getValue());
		cb[1].setValue(end - cb[1].getValue());
	}

	/**
	 * Returns the end point. Which side is determined by besValue. The returned
	 * point should not be changed.
	 * 
	 * @param besValue
	 *            R_VALUE or any
	 * @return r-side if besValue == R_VALUE, f-side otherwise.
	 */
	public Point2D getEndPoint(byte besValue) {
		return circles[getIndex(besValue)].getCenter();
	}

	/**
	 * Method <code>getMidPoint</code> gets the mid point of the clone. The
	 * point is unchangeable. It's clone however is not.
	 * 
	 * @return an immutable <code>Point2D</code> value
	 */
	public Point2D getMidPoint() {
		return midPoint;
	}

	/**
	 * Returns the lowest pixel (highest y-value) that this clone occupies.
	 * 
	 * @param padding
	 * @return the highest y value that this Clone occupies
	 */
	public double getEndPixel(double padding) {
		int i1 = getMinIndex();
		int i2 = (i1+1) % 2;

		if (isTextVisible())
			return Math.max(cb[i2].getPixelValue(), cb[i1].getPixelValue() + getTextHeight()) + padding;
		else
			return cb[i2].getPixelValue() + padding;
	}

	public GenomicsNumber getMinCB() {
		return cb[getMinIndex()];
	}

	public GenomicsNumber getMaxCB() {
		return cb[(getMinIndex()+1) % 2];
	}

	public int getLength() {
		return (int)Math.abs(cb[0].getValue()-cb[1].getValue());
	}

	/**
	 * @return the layer that this clone is in the contig
	 */
	public int getLayer() {
		return layer;
	}

	/**
	 * Sets the layer that this clone is in the contig
	 * 
	 * @param layer
	 */
	public void setLayer(int layer) {
		this.layer = layer;
	}

	/**
	 * Sets the location of the Clone
	 * 
	 * @param x
	 * @param y1
	 * @param y2
	 */
	public void setPoints(double x, double y1, double y2) {
		midPoint.setLoc(x,(y1 + y2) / 2.0);
		line.setLine(x, y1, x, y2);

		int b;
		int mi = getMinIndex();
		for (int i = 0; i < 2; i++) {
			if (bes[i] != NORF_VALUE) {
				b = getIndex(bes[i]);
				if (mi == b) circles[b].setLocation(x,y1);
				else circles[b].setLocation(x,y2);
			}
		}
	}

	/**
	 * Resizes the clone keeping the x coordinate the same
	 * 
	 * @param y1
	 * @param y2
	 */
	public void resize(double y1, double y2) {
		setPoints(line.x1, y1, y2);
	}

	private boolean isRemarkHighlighted() {
		return remarkSelected && remarkShow == Contig.CLONE_HIGHLIGHT;
	}

	/**
	 * Method <code>paintComponent</code> paints the clone
	 *
	 * @param g2 a <code>Graphics2D</code> value
	 */
	public void paintComponent(Graphics2D g2) {
		if (isVisible()) {
			boolean highlight = doHighlight();
			boolean remarkHighlight = isRemarkHighlighted();

			if (highlight)              
				g2.setPaint(cloneHighlightColor);
			else if (remarkHighlight)
				g2.setPaint(remarkHighlightColor);
			else if (isInverse())
				g2.setPaint(inverseCloneColor);
			else
				g2.setPaint(cloneColor);

			if (highlight || remarkHighlight) {
				Stroke stroke = g2.getStroke();
				g2.setStroke(new BasicStroke(2.0f));
				g2.draw(line);
				g2.setStroke(stroke);
			}
			else {
				g2.draw(line);
			}

			paintCircles(g2,highlight,remarkHighlight,0);
			paintCircles(g2,highlight,remarkHighlight,1);

			if (isTextVisible()) {
				if (highlight) g2.setPaint(cloneHighlightColor);
				else           g2.setPaint(cloneNameColor);
				paintText(g2);
			}
		}
	}

	private void paintCircles(Graphics2D g2, boolean highlight, boolean remarkHighlight, int i) {
		Color b = getColor(besFilters[i]);
		Color f = b;
		if      (b == besNoHitColor) f = null;
		else if (b == besInBlockColor && !isCurrentHitOnBES(bes[i])) f = besInBlockNoCurrentColor;

		if (highlight) {
			b = cloneHighlightColor;
			if (f == besInBlockColor) f = cloneHighlightColor;
		}
		else if (remarkHighlight) {
			b = remarkHighlightColor;
			if (f == besInBlockColor) f = remarkHighlightColor;
		}

		circles[i].paint(g2,b,f);
	}

	public static byte getBESFilter(boolean hasHit, boolean inBlock, boolean repetitive) {
		byte ret = NO_HITS;
		if (inBlock) ret |= IN_BLOCK;
		if (hasHit)  ret |= LEAST_ONE_HIT;
		if (repetitive) ret |= REPETITIVE;
		return ret;
	}

	/**
	 * @return The default circle radius
	 */
	public static double getCircleRadius() {
		return circleRadius;
	}

	/**
	 * Method <code>isHover</code> returns true if the clone is visible and
	 * the point lies within or near the clone line or the text contains the
	 * point.
	 * 
	 * @param point
	 *            a <code>Point</code> value
	 * @return a <code>boolean</code> value
	 */
	protected boolean isHover(Point point) {
		return isVisible() && (lineContains(point) || textContains(point) || circles[0].contains(point) || circles[1].contains(point));
	}

	protected String getPositionDisplay() {
		int mi = getMinIndex();
		return new StringBuffer().append(cb[mi].getValue()).append("-").append(cb[(mi+1)%2].getValue()).toString();
	}

	protected boolean getInRange(long start, long end) {
		int mi = getMinIndex();
		return (cb[mi].getValue() >= start && cb[(mi+1)%2].getValue() <= end);
	}

	protected boolean isConditionalFiltered(Object filter) {
		if ( (filter.equals(BOTH_BES_CURRENT_COND_FILTERED) && !isCurrentHitOnBothBESs()) ||
				(filter.equals(BOTH_BES_COND_FILTERED) && !isHitOnBothBESs()) ) return true;

		if (filter.equals(CLONE_REMARK_COND_FILTERED))
			return remarkSelected ? remarkShow == Contig.CLONE_HIDE : remarkShow == Contig.CLONE_SHOW;

		return super.isConditionalFiltered(filter);
	}

	private int getIndex(byte besValue) {
		byte rValue = 0;
		if (bes[0] != NORF_VALUE || bes[1] != NORF_VALUE) {
			if (bes[1] == R_VALUE || bes[0] == F_VALUE) rValue = 1;
		}
		else {
			if (SyMAP.DEBUG) System.out.println("Getting a value from a clone requiring bes value when one isn't assigned.");
			rValue = (byte)getMinIndex();
		}
		return besValue == R_VALUE ? rValue : (rValue+1) % 2;
	}

	private int getMinIndex() {
		return (cb[0].getValue() < cb[1].getValue()) ? 0 : 1;
	}

	private boolean isHitOnBothBESs() {
		return getColor(besFilters[0]) != besNoHitColor && getColor(besFilters[1]) != besNoHitColor;
	}

	public boolean isInverse() {
		if (bes[0] == NORF_VALUE || bes[1] == NORF_VALUE || !isInBlock(besFilters[0]) || !isInBlock(besFilters[1])) 
			return false; // needs block hits on both sides

		int orient[] = {-1,-1};
		Hit hit;
		CloneHit ch;
		int ind;
		int co;
		for (int i = 0; i < hits.size(); ++i) {
			hit = (Hit)hits.get(i);
			if (hit instanceof CloneHit && hit.isBlockHit()) {
				ch = (CloneHit)hit;
				ind = getIndex(ch.getBES());
				co = ch.getOrientation() ? 1 : 2;
				if (orient[ind] > 0 && orient[ind] != co) return false; // no consensus
				orient[ind] = co;
			}
		}
		return orient[0] > 0 && orient[1] > 0 && orient[0] == orient[1]; // both sides set going in same direction
	}

	private boolean isCurrentHitOnBES(byte bes) {
		Hit hit;
		for (int i = 0; i < hits.size(); ++i) {
			hit = (Hit)hits.get(i);
			if (hit instanceof CloneHit && ((CloneHit)hit).getBES() == bes && !hit.isFiltered())
				return true;
		}
		return false;
	}

	private boolean isCurrentHitOnBothBESs() {
		boolean rFound = false, fFound = false;
		Hit hit;
		int besValue;
		for (int i = 0; i < hits.size(); i++) {
			hit = (Hit)hits.get(i);	    
			if (hit instanceof CloneHit && !hit.isFiltered()) {
				besValue = ((CloneHit)hit).getBES();
				if (besValue == R_VALUE) {
					rFound = true;
					if (fFound) break;
				}
				else if (besValue == F_VALUE) {
					fFound = true;
					if (rFound) break;
				}
			}
		}
		return rFound && fFound;
	}

	private boolean doHighlight() {
		return isHighlighted() || isHover() || isMarkerHover() || isMarkerClicked();
	}

	private boolean lineContains(Point p) {
		if ( (p.getY() < line.getY1() && p.getY() > line.getY2()) ||
				(p.getY() < line.getY2() && p.getY() > line.getY1()) ) {
			if (p.getX()+MOUSE_PADDING > line.getX1() && p.getX()-MOUSE_PADDING < line.getX1()) return true;
		}
		return false;
	}

	private Color getColor(byte bes) {
		if (isInBlock(bes))         return besInBlockColor;
		else if (isRepetitive(bes)) return besRepetitiveColor;
		else if (hasHit(bes))       return besHasHitColor;
		else                        return besNoHitColor;
	}

	private boolean hasHit(byte bes) {
		return (bes & LEAST_ONE_HIT) != 0;
	}

	private boolean isInBlock(byte bes) {
		return (bes & IN_BLOCK) != 0;
	}

	private boolean isRepetitive(byte bes) {
		return (bes & REPETITIVE) != 0;
	}

	private static class StaticPoint2D extends Point2D implements Cloneable {
		private double x,y;

		private StaticPoint2D() { }

//		private StaticPoint2D(double x, double y) { // mdb removed 6/29/07 #118
//			setLoc(x,y);
//		}

		public double getX() { return x; }
		public double getY() { return y; }

		public void setLocation(double x, double y) {
			throw new UnsupportedOperationException("Modification not allowed");
		}

		public String toString() {
			return "StaticPoint2D["+x+", "+y+"]";
		}

		public boolean equals(Object obj) {
			if (obj instanceof Point2D) {
				Point2D p = (Point2D)obj;
				return p.getX() == x && p.getY() == y;
			}
			return false;
		}

		private void setLoc(double x, double y) {
			this.x = x;
			this.y = y;
		}

		public Object clone() {
			return new Point2D.Double(x,y);
		}	    
	}
}
