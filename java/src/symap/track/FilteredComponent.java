package symap.track;

import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Vector;
import java.util.Arrays;

import symap.SyMAPConstants;
import symap.mapper.Hit;

public abstract class FilteredComponent implements SyMAPConstants, Comparable<FilteredComponent> {
	public static final String NAME_FILTERED = "name";
	public static final String HIDDEN_FILTERED = "hide";

	public static final String CURRENT_HIT_COND_FILTERED = "current_hit_cf";
	public static final String HIT_COND_FILTERED = "has_hit_cf";

	protected static final int HORIZONTAL_TEXT = 0;
	protected static final int VERTICAL_TEXT = 1;
	private static final double THETA = -1 * Math.PI / 2.0;

	private static final int NUM_HIT_TYPES = 3;
	private static final Map<String,Integer> hitDisplayMap = Collections.synchronizedMap(new HashMap<String,Integer>());
	static {
		hitDisplayMap.put(MRK_TYPE,new Integer(0));
		hitDisplayMap.put(BES_TYPE,new Integer(1));
		hitDisplayMap.put(FINGERPRINT_TYPE,new Integer(2));
	}
	private static final String[] hitDisplayStrings = {"Marker Hits","BES Hits","Fingerprint Hits"};

	protected Vector<Hit> hits;
	protected Track parent;

	private String name;
	private boolean showName;
	private HashSet<Object> filters;
	private Vector<Object> conditionalFilters;

	private int textOrientation;
	private Font textFont;
	private TextLayout layout;
	private Point2D.Float textPoint;
	private boolean highlight, clickHighlight;
	private boolean hover;

	protected FilteredComponent(Track parent, String name, boolean showName, int text_orientation, Font text_font) {
		this(parent,new Vector<Object>(0,1),name,showName,text_orientation,text_font);
	}

	protected FilteredComponent(Track parent, Vector<Object> condFilters, String name, boolean showName,
			int text_orientation, Font text_font) {
		this.parent = parent;
		this.name = name;
		this.showName = showName;
		this.textOrientation = text_orientation;
		this.textFont = text_font;
		filters = new HashSet<Object>(2);
		conditionalFilters = condFilters;
		hits = new Vector<Hit>(0,2);
		hover = false;
		textPoint = new Point2D.Float();
		clickHighlight = highlight = false;
	}    

	/**
	 * Method <code>getName</code> returns the name of this FilteredComponent.
	 *
	 * @return a <code>String</code> value
	 */
	public String getName() {
		return name;
	}

	/**
	 * Method <code>setTextVisible</code> sets whether the name should be displayed or not.
	 *
	 * @param showName a <code>boolean</code> value
	 */
	public void setTextVisible(boolean showName) {
		this.showName = showName;
	}

	/**
	 * Method <code>isTextVisible</code> gets whether the name is set to be displayed or not.
	 *
	 * @return a <code>boolean</code> value
	 */
	public boolean isTextVisible() {
		return showName;
	}

	/**
	 * Method <code>setHighlighted</code> sets the state to be highlighted if highlight is true and
	 * false otherwise.
	 *
	 * @param highlight a <code>boolean</code> value
	 */
	public void setHighlighted(boolean highlight) {
		this.highlight = highlight;
	}

	/**
	 * Method <code>isHighlighted</code> returns true if this instance is 
	 * in a highlighted state.
	 *
	 * @return a <code>boolean</code> value of true if it is highlighted
	 */
	public boolean isHighlighted() {
		return highlight;
	}

	/**
	 * Method <code>setClickHighlighted</code> sets the state to be highlighted by a click if highlight is true and
	 * false otherwise.
	 *
	 * @param highlight a <code>boolean</code> value
	 */
	public void setClickHighlighted(boolean highlight) {
		this.clickHighlight = highlight;
	}

	/**
	 * Method <code>isClickHighlighted</code> returns true if this instance is 
	 * in a click highlighted state.
	 *
	 * @return a <code>boolean</code> value of true if it is highlighted
	 */
	public boolean isClickHighlighted() {
		return clickHighlight;
	}

	/**
	 * Method <code>isVisible</code> returns true if this instance is in range and not filtered (regular and conditional).
	 *
	 * @return a <code>boolean</code> value
	 */
	public boolean isVisible() {
		return isInRange() && !isFiltered() && !isConditionalFiltered();
	}

	/**
	 * Method <code>isConditionalFiltered</code> returns true if the instance is currently conditionally filtered.
	 *
	 * @return a <code>boolean</code> value
	 */
	private boolean isConditionalFiltered() {
		for (int i = 0; i < conditionalFilters.size(); i++) {
			if (isConditionalFiltered(conditionalFilters.get(i))) return true;
		}
		return false;
	}

	/**
	 * Method <code>isFiltered</code> returns true if the instance is currently being fitlered (not including conditional
	 * filtering).
	 *
	 * @return a <code>boolean</code> value
	 */
	private boolean isFiltered() {
		return !filters.isEmpty();
	}

	/**
	 * Method <code>isInRange</code> returns true if this instance is in range of the start and end of the parent.
	 *
	 * @return a <code>boolean</code> value
	 * @see Track#getStart()
	 * @see Track#getEnd()
	 */
	public boolean isInRange() {
		return getInRange(parent.getStart(),parent.getEnd());
	}

	/**
	 * Method <code>setFiltered</code> sets the instance to be or not to be filtered on <code>filter</code>.
	 *
	 * @param filter an <code>Object</code> of the filter to change
	 * @param filtered a <code>boolean</code> value of whether to filter out (true) or not to filter out (false).
	 * @return a <code>boolean</code> value of true on a change
	 */
	public boolean setFiltered(Object filter, boolean filtered) {
		if (!filtered) return filters.remove(filter);
		else           return filters.add(filter);
	}


	/**
	 * Method <code>isFiltered</code> returns true if this instance has been set to be filtered out
	 * on <code>filter</code> (equality of filters determined by the hash).
	 *
	 * @param filter an <code>Object</code> value
	 * @return a <code>boolean</code> value
	 */
	public boolean isFiltered(Object filter) {
		return filters.contains(filter);
	}

	/**
	 * Method <code>setConditionalFiltered</code> sets the instance to be or not to be conditionaly filtered on filter
	 *
	 * An instance is determined to be actually filtered out or not on a call to isVisible() or isConditionalyFiltered()
	 * in which case the the implemented  instance determines if it should be filtered out based on the conditional 
	 * filters and if the conditions hold.
	 *
	 * @param filter an <code>Object</code> value of ther filter to change
	 * @param filtered a <code>boolean</code> value of whether to filter out (true) or not to filter out (false) conditionaly
	 * @return a <code>boolean</code> value of true on change
	 */
	public boolean setConditionalFiltered(Object filter, boolean filtered) {
		if (!filtered) return conditionalFilters.remove(filter);
		else if (!conditionalFilters.contains(filter)) return conditionalFilters.add(filter);
		return false;
	}

	protected boolean condFilteredContains(Object obj) {
		return conditionalFilters.contains(obj);
	}

	/**
	 * Method <code>addHit</code> adds the hit to this component
	 * 
	 * @param hit
	 *            a <code>Hit</code> value
	 * @return a <code>boolean</code> value of true if the hit was not already
	 *         in the list of hits for this component
	 */
	public boolean addHit(Hit hit) {
		if (!hits.contains(hit)) {
			hits.add(hit);
			return true;
		}
		return false;
	}

	/**
	 * Method <code>removeHit</code> removes the hit
	 * 
	 * @param hit
	 *            a <code>Hit</code> value
	 * @return a <code>boolean</code> value of true if a hit was removed and
	 *         false if that hit didn't exist
	 */
	public boolean removeHit(Hit hit) {
		return hits.remove(hit);
	}

	/**
	 * Method <code>isHover</code> returns true if this instance is
	 * in a hovered state.
	 *
	 * @return a <code>boolean</code> value
	 */
	public boolean isHover() {
		return hover;
	}

	/**
	 * Method <code>getHoverMessage</code> returns a descriptive string of the
	 * FilteredComponent for display.
	 * 
	 * @return a <code>String</code> value
	 */
	public String getHoverMessage(Point2D p) {
		StringBuffer ret = new StringBuffer("Name: ").append(getName());
		String pos = getPositionDisplay();
		if (pos != null) ret.append(" position: ").append(pos);

		int[] typeVis = new int[NUM_HIT_TYPES];
		int[] typeHid = new int[NUM_HIT_TYPES];

		Arrays.fill(typeVis,0);
		Arrays.fill(typeHid,0);

		int i;
		Hit hit;
		for (i = 0; i < hits.size(); i++) {
			hit = hits.get(i);
			try {
				if (hit.isVisible() && !hit.isFiltered())
					typeVis[((Integer)hitDisplayMap.get(hit.getType())).intValue()]++;
				else
					typeHid[((Integer)hitDisplayMap.get(hit.getType())).intValue()]++;
			} catch (Exception e) { // shouldn't happen
				System.out.println("Hit has an undefined type!");
				e.printStackTrace();
			}
		}

		final String SPACE = " ";
		final String COLUN = ": ";
		//final String COMMA = ",";
		for (i = 0; i < NUM_HIT_TYPES; i++) {
			if (typeVis[i] > 0 || typeHid[i] > 0)
				//ret.append(SPACE).append(hitDisplayStrings[i]).append(COLUN).append(typeVis[i]).append(COMMA).append(typeHid[i]);
				ret.append(SPACE).append(hitDisplayStrings[i]).append(COLUN).append(typeVis[i]+typeHid[i]);
		}

		return ret.toString();
	}

	/**
	 * Returns the text height considering the orientation.
	 * 
	 * @return the texts height or zero if the layout is not set
	 */
	public double getTextHeight() {
		if (layout == null) return 0.0;
		if (textOrientation == VERTICAL_TEXT) return layout.getBounds().getWidth();
		else return layout.getBounds().getHeight();
	}

	/**
	 * Returns the text width which considering the orientation.
	 * 
	 * @return The text height or 0 if the layout has not been set yet
	 */
	public double getTextWidth() {
		if (layout == null) return 0.0;
		if (textOrientation == VERTICAL_TEXT) return layout.getBounds().getHeight();
		else return layout.getBounds().getWidth();
	}

	/**
	 * Sets the text layout.  Should only be called once the graphics object can be acquired
	 * from the parent class (<code>parent.getGraphics() != null</code>).
	 */
	public void setTextLayout() {
		layout = new TextLayout(name,textFont,parent.getFontRenderContext());
	}

	/**
	 * Sets the location to draw the text considering on the orientation;
	 * 
	 * @param x
	 * @param y
	 */
	public void setTextLocation(double x, double y) {
		if (textOrientation == VERTICAL_TEXT) {
			x += getTextWidth();
			y += getTextHeight();
		}
		textPoint.x = (float)x;
		textPoint.y = (float)y;
	}

	/**
	 * Method <code>textContains</code> returns true if the text is visible
	 * and point lies within the text.
	 * 
	 * @param point
	 *            a <code>Point</code> value
	 * @return a <code>boolean</code> value
	 */
	public boolean textContains(Point point) {
		if (!isTextVisible()) return false;
		Rectangle2D.Double rect = new Rectangle2D.Double();
		if (layout != null) {
			rect.setRect(layout.getBounds());
			if (textOrientation == HORIZONTAL_TEXT) {
				rect.x = textPoint.getX() + rect.x;
				rect.y = textPoint.getY() + rect.y;
			}
			else {
				rect.x = textPoint.getX();
				rect.y = textPoint.getY();
				rect.x -= rect.height;
				rect.y -= rect.width;
				double temp = rect.height;
				rect.height = rect.width;
				rect.width = temp;
			}
		}
		return rect.contains(point);
	}

	public int compareTo(FilteredComponent c) {
		return getName().compareTo(c.getName());
	}

	/**
	 * Method <code>equals</code> returns true if obj is an instance of FilteredComponent and has the same name.
	 *
	 * @param obj an <code>Object</code> value
	 * @return a <code>boolean</code> value
	 */
	public boolean equals(Object obj) {
		return (obj instanceof FilteredComponent && getName().equals(((FilteredComponent)obj).getName()));
	}

	/**
	 * Method <code>setHover</code> sets the state to be hovered over or not.
	 * 
	 * This method is a convenience method that returns
	 * <code>setHover(isHover(point))</code>.
	 * 
	 * @param point
	 *            a <code>Point</code> value of the mouse position
	 * @return a <code>boolean</code> value returning true if the state was
	 *         changed.
	 */
	public boolean setHover(Point point) {
		return setHover(isHover(point));
	}

	/**
	 * Method <code>setHover</code> sets the state to be hovered over if hover
	 * is true and false otherwise.
	 * 
	 * @param hover
	 *            a <code>boolean</code> value of true for hovered hover
	 * @return a <code>boolean</code> value of true if a change occurs.
	 */
	public boolean setHover(boolean hover) {
		if (hover != this.hover) {
			this.hover = hover;
			return true;
		}
		return false;
	}

	protected boolean isConditionalFiltered(Object filter) {
		if (filter.equals(CURRENT_HIT_COND_FILTERED) && !hasCurrentHit()) return true;
		if (filter.equals(HIT_COND_FILTERED) && !hasHit()) return true;
		return false;
	}

	protected void paintText(Graphics2D g2) {
		if (isTextVisible() && layout != null) {
			if (textOrientation == VERTICAL_TEXT) {
				AffineTransform saveAt = g2.getTransform();
				g2.rotate(THETA, textPoint.x, textPoint.y);
				layout.draw(g2, textPoint.x, textPoint.y);
				g2.setTransform(saveAt);
			}
			else {
				layout.draw(g2, textPoint.x, textPoint.y);
			}
		}
	}

	protected void offsetText(double offsetX) {
		textPoint.x += offsetX;
	}

	protected void paint() {
		Graphics g = parent.getGraphics();
		if (g != null) paintComponent((Graphics2D)g);
	}

	protected boolean hasHit() {
		return !hits.isEmpty();
	}

	protected boolean hasCurrentHit() {
		for (int i = 0; i < hits.size(); i++) {
			if ( !((Hit)hits.get(i)).isFiltered() ) return true;	    
		}
		return false;
	}

	/**
	 * Method <code>offset</code> offset the component along the x axis by offsetX.
	 * Implementations should call offsetText to offset the text also.
	 *
	 * @param offsetX a <code>double</code> value amount to offset
	 */
	public abstract void offset(double offsetX);

	public abstract void paintComponent(Graphics2D g2);

	/**
	 * Method <code>isHover</code> determines if the point lies within the
	 * hover area. No changes should be made to the state.
	 * 
	 * @param point
	 *            a <code>Point</code> value
	 * @return a <code>boolean</code> value of true if the point lies within
	 *         the hover are.
	 */
	protected abstract boolean isHover(Point point);

	protected abstract boolean getInRange(long start, long end);
	protected abstract String getPositionDisplay();
}
