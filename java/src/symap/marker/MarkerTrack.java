package symap.marker;

import java.awt.geom.Point2D;
import java.awt.event.MouseEvent;

import java.util.regex.PatternSyntaxException;

import java.sql.SQLException;

import symap.SyMAP;
import symap.track.Track;
import symap.track.TrackHolder;
import symap.drawingpanel.DrawingPanel;
import symap.pool.HoverMessagePool;

/**
 * Class <code>MarkerTrack</code> should be extended by Tracks that contain
 * markers (i.e. Block and Contig).
 * 
 * The MarkerTrack holds the flipped variables had stores/restores it in
 * MarkerTrackData. However, using and setting, the variable, and doing the
 * flipping is the resposiblility of the implementing class (other than for
 * setting the variable to false on instantiation).
 * 
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 * @see Track
 */
public abstract class MarkerTrack extends Track {	
	protected HoverMessagePool hmp;

	public static final boolean DEFAULT_FLIPPED = false; // leave false

	protected MarkerList markerList;

	protected String pattern;
	protected boolean show;
	protected int markerShow;

	protected boolean flipped;

	public MarkerTrack(DrawingPanel dp, TrackHolder holder,
			double minBpPerPixel, double maxBpPerPixel,
			Point2D defaultTrackOffset, HoverMessagePool hmp) 
	{
		super(dp,holder,minBpPerPixel,maxBpPerPixel,defaultTrackOffset);
		markerList = new MarkerList();
		pattern = null;
		show = true;
		markerShow = MarkerList.DEFAULT_SHOW;
		flipped = DEFAULT_FLIPPED;
		this.hmp = hmp;
	}

	protected void reset(int project, int otherProject) {
		markerList.clear();
		pattern = null;
		show = true;
		markerList.filterMarkerNames(pattern,show);

		markerShow = MarkerList.DEFAULT_SHOW;
		markerList.setShowNames(markerShow);

		super.reset(project,otherProject);
	}

	/*protected*/public void reset() {
		pattern = null;
		show = true;
		markerList.filterMarkerNames(pattern,show);
		markerShow = MarkerList.DEFAULT_SHOW;
		markerList.setShowNames(markerShow);
		super.reset();
	}

	protected void setMarkerFilters(MarkerTrackData mtd) {
		if (mtd != null)
			mtd.setMarkerTrackData(this);
		setMarkerFilters();
	}

	protected void setMarkerFilters() {
		markerList.setShowNames(markerShow);
		filterMarkerNames(pattern,show);
	}

	/**
	 * Method <code>clear</code> clears the list of markers and calls
	 * super.clear()
	 * 
	 * @see Track#clear()
	 */
	public void clear() {
		if (markerList != null) markerList.clear();
		super.clear();
	}

	public void clearData() {
		if (markerList != null) markerList.clear();
	}

	/**
	 * Method <code>setShowMarkerNames</code> sets the state of what markers
	 * to show and clears all of the builds on change.
	 * 
	 * @param show
	 *            an <code>int</code> value excepted by setShowNames in
	 *            MarkerList
	 * @see MarkerList#setShowNames(int)
	 * @see Track#clearAllBuild()
	 */
	public void setShowMarkerNames(int show) {
		markerShow = show;
		if (markerList.setShowNames(show)) clearAllBuild();
	}

	/**
	 * Filter the marker names based on the string pattern.
	 * 
	 * @param pattern
	 *            is the string to be matched based on the string matching rules
	 * @param show
	 *            true to show matching markers, false to hide
	 * @return true if marker matched the pattern
	 */
	public boolean filterMarkerNames(String pattern, boolean show) {
		if (SyMAP.DEBUG) System.out.println("Filtering on "+pattern+" with show = "+show);

		this.pattern = pattern;
		this.show = show;

		int ret = MarkerList.NOTHING;
		try {
			ret = markerList.filterMarkerNames(convertToRegex(pattern),show);
			if (MarkerList.isChange(ret)) clearAllBuild();
			if (pattern != null && !MarkerList.isMatch(ret) && pattern.indexOf('*') == -1) {
				ret = markerList.filterMarkerNames(addAsterics(pattern),show);
				if (MarkerList.isChange(ret)) clearAllBuild();		
			}
		} catch (PatternSyntaxException pse) {
			pse.printStackTrace();
		}
		return MarkerList.isMatch(ret);
	}

	/**
	 * Method <code>getHelpText</code>
	 *
	 * @param event a <code>MouseEvent</code> value
	 * @return a <code>String</code> value
	 */
	public String getHelpText(MouseEvent event) {
		Marker m = markerList.getMarker(event.getPoint());
		return m == null ? 
				markerList.getNumVisibleMarkers()+" of "
				+markerList.getNumMarkers()+" markers shown.  "
				+"Right-click for menu." 
				: m.getHoverMessage(event.getPoint()); // mdb changed 3/30/07 #112
	}

	public String getMarkerHoverMessage(String name) {
		String str = null;
		if (hmp != null) try {
			str = hmp.getMarkerHoverMessage(project,otherProject,name);
		} catch (SQLException e) { e.printStackTrace(); }
		return str;
	}

	/**
	 * Method <code>getMarkerList</code> returns the MarkerList of this Block
	 *
	 * @return a <code>List</code> value
	 */
	public MarkerList getMarkerList() {
		return markerList;
	}

	/**
	 * Method <code>flip</code> should handle flipping the Track.
	 * 
	 * @param flip
	 *            a <code>boolean</code> value
	 * @param changeEnds
	 *            a <code>boolean</code> value of true to change the start and
	 *            end of the track so that the same location on the contig is
	 *            being shown post flip (if applicable).
	 * @return a <code>boolean</code> value of true on change.
	 */
	public abstract boolean flip(boolean flip, boolean changeEnds);

	/**
	 * Method <code>getMarker</code> should return the marker that is in the
	 * given contig.
	 * 
	 * @param name
	 *            a <code>String</code> value
	 * @param contig
	 *            an <code>int</code> value
	 * @return a <code>Marker</code> value
	 */
	public abstract Marker getMarker(String name, int contig);

	/**
	 * Method <code>getMarkers</code> should return an array of markers
	 * corrisponding to the given contig.
	 * 
	 * @param contig
	 *            an <code>int</code> value
	 * @return a <code>Marker[]</code> value of the markers or null if the
	 *         track does not contain contig
	 */
	public abstract Marker[] getMarkers(int contig);

	public abstract int[] getContigs();
}

