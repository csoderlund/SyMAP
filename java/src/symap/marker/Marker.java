package symap.marker;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import util.PropertiesReader;
import number.GenomicsNumber;
import symap.SyMAP;
import symap.SyMAPConstants;
import symap.contig.Clone;
import symap.mapper.Hit;
import symap.track.FilteredComponent;
import symap.track.Track;

/**
 * A Marker object contains the markers text and lines (short segment and long segment)
 * and all of the coorisponding data.
 *
 * @see FilteredComponent
 * @see SyMAPConstants
 */
public class Marker extends FilteredComponent implements SyMAPConstants {
	public static final String SHARED_COND_FILTERED = "shared_cond_filter";

	public static Color markerColor;
	public static Color markerHighlightColor;
	public static Color markerLineColor;
	public static Color sharedMarkerColor;
	public static Color nohitsMarkerColor;
	public static Color nohitSharedMarkerColor;
	public static Color noCurrentHitSharedMarkerColor;
	public static Color noCurrentHitMarkerColor;
	private static Font markerNameFont;
	private static double bentLineSegment;
	private static double straightLineSegment;
	private static double markerNamePadding;
	static {
		PropertiesReader props = new PropertiesReader(SyMAP.class.getResource("/properties/marker.properties"));
		markerColor = props.getColor("markerColor");
		markerHighlightColor = props.getColor("markerHighlightColor");
		markerLineColor = props.getColor("markerLineColor");
		markerNameFont = props.getFont("markerNameFont");
		sharedMarkerColor = props.getColor("sharedMarkerColor");
		bentLineSegment = props.getDouble("bentLineSegment");
		straightLineSegment = props.getDouble("straightLineSegment");
		markerNamePadding = props.getDouble("markerNamePadding");
		nohitsMarkerColor = props.getColor("nohitsMarkerColor");
		nohitSharedMarkerColor = props.getColor("nohitSharedMarkerColor");
		noCurrentHitSharedMarkerColor = props.getColor("noCurrentHitSharedMarkerColor");
		noCurrentHitMarkerColor = props.getColor("noCurrentHitMarkerColor");
	}

	//private String type;
	private GenomicsNumber pos;
	private Line2D.Double shortSeg;
	private Line2D.Double longSeg;
	private List<Clone> clones;
	private boolean shared;
	private double yPoint;

	public Marker(Track parent, Vector<Object> condFilters, String name, String type, GenomicsNumber pos) {
		this(parent,condFilters,name,type,pos,null);
	}

	public Marker(Track parent, Vector<Object> condFilters, String name, String type, GenomicsNumber pos, List<Clone> clones) {
		super(parent,condFilters,name,true,HORIZONTAL_TEXT,markerNameFont);
		//this.type = type;
		this.pos = pos;
		this.clones = clones;
		shortSeg = new Line2D.Double();
		longSeg = new Line2D.Double();
		yPoint = -1;
	}

	/**
	 * Method equals returns true if obj is an instace of a Marker and has the same name
	 * as this marker. Otherwise, false is returned.
	 */
	public boolean equals(Object obj) {
		return (obj instanceof Marker && super.equals(obj));
	} 

	/**
	 * Method setYPoint sets the y offset of this marker which will be used by MarkerList in building.
	 */
	public void setYPoint(double num) {
		yPoint = num;
	}

	/**
	 * Method getYPoint returns the last value set from setYPoint(double) or -1 if setYPoint hasn't been called.
	 */
	public double getYPoint() {
		return yPoint;
	}

	/**
	 * Method offset offsets the marker by offsetX along the X axis.
	 */
	public void offset(double offsetX) {
		shortSeg.x1 += offsetX;
		shortSeg.x2 += offsetX;
		longSeg.x1 += offsetX;
		longSeg.x2 += offsetX;
		offsetText(offsetX);
	}

	/**
	 * Method addHit Adds a Marker hit to the marker and to any associated clones.
	 */
	public boolean addHit(Hit hit) {
		if (super.addHit(hit)) {
			if (clones != null) {
				for (Clone c : clones)
					c.addHit(hit);
			}
			return true;
		}
		return false;
	}

	/**
	 * Method removeHit Removes a Marker hit from the Marker and any associated clones.
	 */
	public boolean removeHit(Hit hit) {
		if (super.removeHit(hit)) {
			if (clones != null) {
				for (Clone c : clones)
					c.removeHit(hit);
			}
			return true;
		}
		return false;
	}

	/**
	 * Flips the marker based on end (i.e. pos = end - pos).
	 */
	public void flip(long end) {
		pos.setValue(end - pos.getValue());
	}

	public void setHighlighted(boolean highlight) {
		boolean chighlight = isHighlighted();
		super.setHighlighted(highlight);
		if (highlight != chighlight) paint();
	}

	/**
	 * Method setShared sets this marker to be shared or not which effects
	 * the coloring of the marker's name.
	 */
	public void setShared(boolean shared) {
		this.shared = shared;
	}

	/**
	 * Method isShared returns the value set in the last call to setShared or false by default.
	 */
	public boolean isShared() {
		return shared;
	}

	/**
	 * @return the list of Clones associated with this Marker or null if none
	 */
	public List<Clone> getClones() {
		return clones;
	}

	/**
	 * @return The position of the Marker
	 */
	public GenomicsNumber getPosition() {
		return pos;
	}

	/**
	 * Sets the short segment line which is a straight horizontal line.
	 */
	public void setShortSeg(double x1, double x2, double y) {
		shortSeg.setLine(x1, y, x2, y);
	}

	/**
	 * Sets the long segment line which is the line that connects between the text and the short segment.
	 */
	public void setLongSeg(double x1, double y1, double x2, double y2) {
		longSeg.setLine(x1, y1, x2, y2);
	}

	/**
	 * @return the point at the end of the short segment on the other side from the long segment
	 */
	public Point2D getPoint() {
		if (shortSeg.getP1().equals(longSeg.getP1()) || shortSeg.getP1().equals(longSeg.getP2()))
			return shortSeg.getP2();
		else
			return shortSeg.getP1();
	}

	public void setClickHighlighted(boolean highlight) {
		super.setClickHighlighted(highlight);
	}    

	public boolean setHover(boolean hover) {
		boolean changed = super.setHover(hover);
		if (clones != null && changed) {
			for (Clone c : clones)
				c.setMarkerHover(hover,getName());
		}
		return changed;
	}

	public boolean isCloneHover() {
		boolean cloneHover = false;
		if (clones != null) {
			Iterator<Clone> iter = clones.iterator();
			while (iter.hasNext() && !cloneHover)
				if ( (iter.next()).isHover() ) cloneHover = true;
		}
		return cloneHover;
	}

	public String getHoverMessage(Point2D p) {
		return ((MarkerTrack)parent).getMarkerHoverMessage(getName());
	}

	public void paintComponent(Graphics2D g2) {		
		if (isVisible() && isTextVisible()) {
			g2.setPaint(markerLineColor);
			g2.draw(shortSeg);
			g2.draw(longSeg);
			Color cColor;
			if (isHighlighted() || isClickHighlighted()) cColor = markerHighlightColor;
			else if (!hasHit()) {
				if (isShared())       cColor = nohitSharedMarkerColor;
				else                  cColor = nohitsMarkerColor;
			}
			else if (!hasCurrentHit()) {
				if (isShared())       cColor = noCurrentHitSharedMarkerColor;
				else                  cColor = noCurrentHitMarkerColor;
			}
			else if (isShared())      cColor = sharedMarkerColor;
			else                      cColor = markerColor;
			g2.setPaint(cColor);
			paintText(g2);
		}
	}

	protected boolean hasCurrentHit() {
		return super.hasCurrentHit() && (clones == null || hasVisibleClone());
	}

	protected boolean isHover(Point point) {
		return isVisible() && textContains(point) && isTextVisible();
	}

	protected boolean getInRange(long start, long end) {
		return (pos.getValue() >= start && pos.getValue() <= end);
	}

	protected String getPositionDisplay() {
		return String.format("%d", pos.getValue()); // CAS520 new Long().toString();
	}

	protected boolean isConditionalFiltered(Object filter) {
		if ( filter.equals(SHARED_COND_FILTERED) && !isShared() ) return true;
		return super.isConditionalFiltered(filter);
	}

	public static double getLineSpace() {
		return bentLineSegment + straightLineSegment;
	}

	/**@return The horizontal length of the bent line segment*/
	public static double getBentLineLength() {
		return bentLineSegment;
	}

	/*** @return The horizontal length of the straight line segment*/
	public static double getStraightLineLength() {
		return straightLineSegment;
	}

	public static double getMarkerNamePadding() {
		return markerNamePadding;
	}

	public static Color getMarkerLineColor() {
		return markerLineColor;
	}

	public boolean hasVisibleClone() {
		if (clones != null) {
			for (Clone c : clones)
				if ( c.isVisible() ) return true;
		}
		return false;
	}
	public boolean hasInRangeClone() {
		if (clones != null) {
			for (Clone c : clones)
				if ( c.isInRange() ) return true;
		}
		return false;
	}
}
