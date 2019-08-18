package symap.marker;

import java.util.Vector;
import java.util.List;
import java.awt.Point;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.Line2D;
import java.util.regex.*;

import symap.*;
import symap.block.Block;

public class MarkerList implements SyMAPConstants {
	public static final String DEFAULT_COND_FILTER = Marker.CURRENT_HIT_COND_FILTERED;
	public static final boolean IS_DEFAULT_SHARED_COND_FILTERED = false;

	public static final int NOTHING = 0;
	public static final int MATCH = 1;
	public static final int CHANGE = 2;
	public static final int MATCH_CHANGE = 3;

	public static final int NO_NAMES = 0;
	public static final int NAMES_WITH_CURRENT_HITS = 1;
	public static final int NAMES_WITH_HITS = 2;
	public static final int SHARED_NAMES = 3;
	public static final int SHARED_NAMES_WITH_CURRENT_HITS = 4;
	public static final int SHARED_NAMES_WITH_HITS = 5;
	public static final int ALL_NAMES = 6;

	private static final boolean DEFAULT_NO_NAMES = false;
	public static final int DEFAULT_SHOW;
	static {
		if (DEFAULT_NO_NAMES) DEFAULT_SHOW = NO_NAMES;
		else if (DEFAULT_COND_FILTER == null)
			DEFAULT_SHOW = (IS_DEFAULT_SHARED_COND_FILTERED ? SHARED_NAMES : ALL_NAMES);
		else if (DEFAULT_COND_FILTER.equals(Marker.CURRENT_HIT_COND_FILTERED))
			DEFAULT_SHOW = (IS_DEFAULT_SHARED_COND_FILTERED ? SHARED_NAMES_WITH_CURRENT_HITS : NAMES_WITH_CURRENT_HITS);
		else if (DEFAULT_COND_FILTER.equals(Marker.HIT_COND_FILTERED))
			DEFAULT_SHOW = (IS_DEFAULT_SHARED_COND_FILTERED ? SHARED_NAMES_WITH_HITS : NAMES_WITH_HITS);
		else // shouldn't happen
		DEFAULT_SHOW = ALL_NAMES;
	}

	private static final int SMALL_OFFSET = 4;

	private Vector<Marker> list;
	private Rectangle2D.Double rect;
	private Line2D.Double line;
	private double maxheight, maxwidth, nummarkers;
	protected Vector<Object> condFilters;
	protected int showNames;
	protected int orient;
	protected boolean sharedFilter;

	private Pattern pattern;
	private boolean show;

	/**
	 * Creates a new <code>MarkerList</code> instance setting the marker shown based on the public static values set
	 * in Marker (i.e. NAMES_WITH_CURRENT_HITS).
	 */
	public MarkerList() {
		condFilters = new Vector<Object>(2,1);
		list = new Vector<Marker>(100,10);
		rect = new Rectangle2D.Double();
		line = new Line2D.Double();
		sharedFilter = IS_DEFAULT_SHARED_COND_FILTERED;
		showNames = DEFAULT_SHOW;

		if (DEFAULT_COND_FILTER != null) condFilters.add(DEFAULT_COND_FILTER);
		if (sharedFilter) condFilters.add(Marker.SHARED_COND_FILTERED);
	}

	public Vector<Object> getCondFilters() {
		return condFilters;
	}

	public void setCondFilters(List<Object> filters) {
		condFilters.clear();
		condFilters.addAll(filters);
		condFilters.trimToSize(); // mdb added 2/3/10
	}

	/**
	 * Method <code>add</code> adds a marker to the end of the list.
	 * The markers added are assumed to have been instantiated with the
	 * vector returned from <code>getCondFilters()</code>.
	 *
	 * @param marker a <code>Marker</code> value
	 */
	public void add(Marker marker) {
		list.add(marker);
		if (pattern != null) {
			boolean matched = pattern.matcher(marker.getName()).matches();
			marker.setFiltered(Marker.NAME_FILTERED,(matched != show));
		}
	}

	/*
      public void addAll(Collection markers) {
      list.addAll(markers);
      }

      public void removeAll(Collection markers) {
      list.removeAll(markers);
      }
	 */

	/**
	 * Method <code>clear</code> clears the list of markers.
	 *
	 */
	public void clear() {
		list.removeAllElements();//.clear(); // mdb changed 2/3/10
	}

	/**
	 * Method <code>getMarkers</code> returns a vector of all the markers that
	 * are not HIDDEN_FILTERED.  The vector returned
	 * can be changed without changing the internal list of markers.
	 *
	 * @return a <code>Vector</code> value
	 */
	public Vector<Marker> getMarkers() {
		Vector<Marker> newList = new Vector<Marker>(list.size());
		for (Marker marker : list)
			if (!marker.isFiltered(Marker.HIDDEN_FILTERED)) newList.add(marker);
		newList.trimToSize();
		return newList;
	}

	/**
	 * Method <code>getRect</code> returns the rectangle that bounds the marker list.
	 *
	 * @return a <code>Rectangle2D</code> value
	 */
	public Rectangle2D getRect() {
		return rect;
	}

	/**
	 * Method <code>getLine</code> returns the marker line.
	 *
	 * @return a <code>Line2D</code> value
	 */
	public Line2D getLine() {
		return line;
	}
	
	public int getNumVisibleMarkers() { // mdb added 3/29/07 #112
		int count = 0;
		
		for (Marker marker : list)
			if (marker.isVisible() && marker.isTextVisible()) count++;
		
		return count;
	}
	
	public int getNumMarkers() { // mdb added 3/29/07 #112
		return list.size();
	}

	/**
	 * Method <code>filterMarkerNames</code> filters the markers to show or hide based on the
	 * name matched by the regular expression pattern.
	 *
	 * @param pattern a <code>String</code> value
	 * @param show a <code>boolean</code> value
	 * @return an <code>int</code> value of MATCH on a match, CHANGE, on a change, MATCH_CHANGE on both, and NOTHING otherwise
	 * @exception IllegalStateException if an error occurs
	 * @exception PatternSyntaxException if an error occurs
	 */
	public int filterMarkerNames(String pattern, boolean show) throws IllegalStateException, PatternSyntaxException {
		int v = NOTHING;
		if (pattern == null) {
			this.pattern = null;
			boolean changed = false;
			for (Marker marker : list)
				if (marker.setFiltered(Marker.NAME_FILTERED,false)) changed = true;
			if (changed) v += CHANGE + MATCH;
		}
		else if (this.pattern == null || pattern != this.pattern.pattern() || show != this.show) {
			this.pattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
			this.show = show;
			boolean changed, matched, ret;
			changed = ret = false;
			for (Marker marker : list) {
				matched = this.pattern.matcher(marker.getName()).matches();
				if (marker.setFiltered(Marker.NAME_FILTERED,(matched != show))) changed = true;
				if (matched) ret = true;
			}
			if (changed) v += CHANGE;
			if (ret)     v += MATCH;
		}
		return v;
	}

	/**
	 * Method <code>filterSharedMarkers</code> filters markers based on if they are shared or not.
	 *
	 * @param filtered a <code>boolean</code> value of true to filter out all unshared markers
	 * @return a <code>boolean</code> value of true on a change
	 */
	public boolean filterSharedMarkers(boolean filtered) {
		if (sharedFilter != filtered) {
			sharedFilter = filtered;
			setConditionalFiltered(Marker.SHARED_COND_FILTERED,filtered);
			return true;
		}
		return false;
	}

	/**
	 * Method <code>reverse</code> reverses the order of the markers.
	 *
	 */
	public void reverse() {
		Block.reverse(list);
	}

	/**
	 * Method <code>flip</code> flips each marker by calling Marker.flip(end) and
	 * reversing the order of the markers in the list.
	 *
	 * @param end a <code>long</code> value
	 */
	public void flip(long end) {
		Vector<Marker> vec = new Vector<Marker>(list);
		list.clear();
		for (Marker marker : vec) {
			marker.flip(end);
			list.add(0,marker);
		}
	}

	/**
	 * Method <code>setShowNames</code> sets the state of what markers to show.
	 *
	 * NO_NAMES                -> show no marker names
	 * NAMES_WITH_CURRENT_HITS -> show marker names with hits currently shown
	 * NAMES_WITH_HITS         -> show marker names with hits
	 * SHARED_NAMES            -> show marker names that are shared
	 * SHARED_NAMES_WITH_CURRENT_HITS -> show shared marker names with current hits
	 * SHARED_NAMES_WITH_HITS  -> show shared marker names with hits
	 * ALL_NAMES               -> show all marker names
	 *
	 * @param show an <code>int</code> value of one of the above values
	 * @return a <code>boolean</code> value of true on a change
	 */
	public boolean setShowNames(int show) {
		if (show != showNames) {
			showNames = show;
			boolean hitFilter = showNames == NAMES_WITH_HITS || showNames == SHARED_NAMES_WITH_HITS;
			boolean currentHitFilter = showNames == NAMES_WITH_CURRENT_HITS || showNames == SHARED_NAMES_WITH_CURRENT_HITS;
			setConditionalFiltered(Marker.HIT_COND_FILTERED,hitFilter);
			setConditionalFiltered(Marker.CURRENT_HIT_COND_FILTERED,currentHitFilter);
			return true;
		}
		return false;
	}

	/**
	 * Method <code>getShowNames</code> returns the current value of show name (i.e. ALL_NAMES, NO_NAMES, etc...).
	 *
	 * @return an <code>int</code> value
	 */
	public int getShowNames() {
		return showNames;
	}

	/**
	 * Method <code>getMarker</code> returns the first marker who's name matches <code>name</code>
	 * or null if no such marker is found.
	 *
	 * @param name a <code>String</code> value
	 * @return a <code>Marker</code> value
	 */
	public Marker getMarker(String name) {
		Marker fmarker = null;
		for (Marker marker : list) {
			if (marker.getName().equals(name)) {
				fmarker = marker;
				break;
			}
		}
		return fmarker;
	}

	/**
	 * Method <code>getMarker</code> returns the first marker 
	 * who's marker.isHover(point) returns true and null if no
	 * marker that satisfies this constraint exists.
	 *
	 * @param point a <code>Point</code> value
	 * @return a <code>Marker</code> value
	 */
	public Marker getMarker(Point point) {
		Marker foundMarker = null;
		for (Marker marker : list) {
			if (marker.isHover(point)) {
				foundMarker = marker;
				break;
			}
		}
		return foundMarker;
	}

	/**
	 * Method <code>setHover</code> calls marker.setHover on each marker.  If a marker that becomes
	 * set to be hovered is found, that marker is returned immediatly.  Otherwise, a changed marker is returned
	 * or null if no markers changed.
	 *
	 * @param point a <code>Point</code> value
	 * @return a <code>Marker</code> value
	 */
	public Marker setHover(Point point) {
		Marker changedMarker = null;
		for (Marker marker : list) {
			if (marker.setHover(point)) {
				if (marker.isHover(point)) {
					changedMarker = marker;
					break;
				}
				else changedMarker = marker;
			}
			else if (marker.isHover(point)) { // hovering but already highlighted
				if (changedMarker != null && changedMarker.equals(marker)) {
					changedMarker.setHover(true);
					changedMarker = null;
				}
				break;
			}

		}
		return changedMarker;
	}

	/**
	 * Method <code>contains</code> returns true if the rectangle housing the markers contains Point <code>p</code>.
	 *
	 * @param p a <code>Point</code> value
	 * @return a <code>boolean</code> value
	 */
	public boolean contains(Point p) {
		return rect.contains(p);
	}

	/**
	 * Method <code>setup</code> sets up the rectangle
	 *
	 * @param position a <code>Point2D</code> value
	 * @param orient an <code>int</code> value
	 * @param height a <code>double</code> value
	 */
	public void setup(Point2D position, int orient, double height) {
		double x, w, h;
		this.orient = orient;
		nummarkers = maxwidth = maxheight = 0;

		boolean sharedNameFiltered = isSharedNameFiltered();

		for (Marker marker : list) {
			marker.setTextVisible(showNames != NO_NAMES && (!sharedNameFiltered || marker.isShared()));
			if (!marker.isVisible() || !marker.isTextVisible())	continue;
			nummarkers++; // for spacing
			marker.setTextLayout();
			w = marker.getTextWidth();
			h = marker.getTextHeight();
			if (w > maxwidth)
				maxwidth = w;
			if (h > maxheight)
				maxheight = h;
		}

		rect.setRect(position.getX(), position.getY(), 0, height);
		rect.width = maxwidth + Marker.getLineSpace() + SMALL_OFFSET;

		x = rect.x;
		if (orient == LEFT_ORIENT) x += rect.width;
		line.setLine(x, rect.y, x, rect.y+rect.height);
	}

	/**
	 * Method <code>build</code> builds the marker list.  This should be called after setupMarkers and setupRect.
	 *
	 * @param startPixel a <code>double</code> value of starting pixel, negative if marker.getYPoint() should be used instead
	 */
	public void build(double startPixel) {
		double x, x1, y1, x2, y2, x1b, py, ry, nameHeight, h;
		int i;
		Marker marker;

		x = rect.x;
		if (orient == LEFT_ORIENT) {
			x += rect.width;
			x1 = x - Marker.getStraightLineLength();
			x2 = x1 - Marker.getBentLineLength();
		} else {
			x1 = x + Marker.getStraightLineLength();
			x2 = x1 + Marker.getBentLineLength();
		}

		/*
		 * Set up Markers
		 */
		nameHeight = Marker.getMarkerNamePadding() + maxheight;

		boolean isfirst = true;
		py = rect.y + (nameHeight * nummarkers--);
		for (i = list.size()-1; i >= 0; i--) {
			marker = (Marker)list.get(i);
			if (!marker.isVisible()) continue;
			if (!marker.isTextVisible()) {
				if (startPixel < 0) y1 = marker.getYPoint();
				else y1 = marker.getPosition().getPixelValue() - startPixel + rect.y;
				marker.setShortSeg(x1, x, y1);
			}
			else {
				h = marker.getTextHeight();
				//namePadding = nameHeight - h;
				if (startPixel < 0) y1 = marker.getYPoint();
				else y1 = marker.getPosition().getPixelValue() - startPixel + rect.y;
				y2 = py - nameHeight + (h / 2.0);

				ry = rect.y + (nameHeight * nummarkers--) + (h / 2.0);

				if (y2 > y1) {
					if (ry < y1) y2 = y1;
					else y2 = ry;
				}
				else if (isfirst) { // y2 <= y1
					y2 = y1;
				}

				marker.setShortSeg(x1, x, y1);
				marker.setLongSeg(x1, y1, x2, y2);

				y1 = y2 + (h / 2.0);
				if (orient == LEFT_ORIENT) x1b = x2 - marker.getTextWidth() - SMALL_OFFSET;
				else                       x1b = x2 + SMALL_OFFSET;
				marker.setTextLocation(x1b, y1);
				if (isfirst) {
					rect.height = y1 - rect.y;
					isfirst = false;
				}
				py = y1 - h;
			}
		}
	}

	/**
	 * Method <code>paintComponent</code> paints the marker list to g.
	 *
	 * @param g2 a <code>Graphics2D</code> value
	 */
	public void paintComponent(Graphics2D g2) {
		if (showNames != NO_NAMES) {
			if (getNumVisibleMarkers() > 0) { // mdb added "if" 3/30/07 #112
				for (Marker marker : list)
					marker.paintComponent(g2);
				g2.setPaint(Marker.getMarkerLineColor());
				g2.draw(line); 
			}
		}
	}

	/**
	 * Method <code>offsetX</code> offsets everything along the x axis by <code>offset</code>.
	 *
	 * @param offset a <code>double</code> value
	 */
	public void offsetX(double offset) {
		rect.x += offset;
		for (Marker marker : list)
			marker.offset(offset);
		line.x1 += offset;
		line.x2 += offset;
	}

	/**
	 * Method <code>isMatch</code> returns true if value == MATCH || value == MATCH_CHANGE
	 *
	 * @param value an <code>int</code> value
	 * @return a <code>boolean</code> value
	 */
	public static boolean isMatch(int value) {
		return (value == MATCH || value == MATCH_CHANGE);
	}

	/**
	 * Method <code>isChange</code> returns true if value == CHANGE || value == MATCH_CHANGE
	 *
	 * @param value an <code>int</code> value
	 * @return a <code>boolean</code> value
	 */
	public static boolean isChange(int value) {
		return (value == CHANGE || value == MATCH_CHANGE);
	}

	private boolean setConditionalFiltered(Object filter, boolean filtered) {
		if (!filtered) return condFilters.remove(filter);
		else if (!condFilters.contains(filter)) return condFilters.add(filter);
		return false;
	}

	public boolean isSharedNameFiltered() {
		return showNames == SHARED_NAMES || showNames == SHARED_NAMES_WITH_HITS || showNames == SHARED_NAMES_WITH_CURRENT_HITS;
	}

	public boolean isCurrentHitsNameFiltered() {
		return showNames == NAMES_WITH_CURRENT_HITS || showNames == SHARED_NAMES_WITH_CURRENT_HITS;
	}

	public boolean isHitsNameFiltered() {
		return showNames == NAMES_WITH_HITS || showNames == SHARED_NAMES_WITH_HITS;
	}

	public boolean isNoNamesFiltered() {
		return showNames == NO_NAMES;
	}
}
