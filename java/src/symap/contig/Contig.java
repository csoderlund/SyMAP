package symap.contig;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Vector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import number.GenomicsNumber;
import symap.SyMAP;
import symap.marker.Marker;
import symap.marker.MarkerList;
import symap.marker.MarkerTrack;
import symap.marker.MarkerTrackData;
import symap.track.Track;
import symap.track.TrackData;
import symap.track.TrackHolder;
import util.LinkLabel;
import util.PropertiesReader;
import symap.drawingpanel.DrawingPanel;
import symap.pool.HoverMessagePool;

/**
 * The Contig track object class.
 * 
 * @author Austin Shoemaker
 */
public class Contig extends MarkerTrack {
	public static final String NO_BOTH_BES_FILTER = "-1";

	public static final int CLONE_HIDE = 1;
	public static final int CLONE_SHOW = 0;
	public static final int CLONE_HIGHLIGHT = 2;

	public static final boolean DEFAULT_SHOW_CLONE_NAMES = false;

	private static final int LAYER_SIZE = 50;
	private static final double SMALL_OFFSET = 4;
	private static final double TINY_OFFSET = 2;
	private static final String TOBLOCK_TOOLTIP = "View this contig as a block";

	public static Color resizeColor;
	private static Dimension resizeSize;
	//private static Color sdCloneColor;
	//private static Color repeatBesColor;
	private static double verticalSpace;
	private static double minHorizontalSpace;
	private static double markerToContigSpace;
	private static Point2D defaultContigOffset;
	private static double minDefaultBpPerPixel;
	private static double maxDefaultBpPerPixel;
	private static double padding;
	private static final Color toBlockLinkColor = new Color(0, 0, 255);
	private static final Color toBlockLinkHoverColor = new Color(66, 0, 66);
	private static final Font toBlockLinkFont = new Font("Courier", 0, 12);
	private static final String toBlockLinkText = "Back to Block View";
	private static final Point2D toBlockLinkOffset = new Point2D.Float(5,5);
	private static Point2D titleOffset;

	static {
		PropertiesReader props = new PropertiesReader(SyMAP.class.getResource("/properties/contig.properties"));
		resizeColor = props.getColor("resizeColor");
		Point2D p = props.getPoint("resizeSize");
		resizeSize = new Dimension((int)p.getX(),(int)p.getY());
		defaultContigOffset = props.getPoint("contigOffset");
		//sdCloneColor = props.getColor("sdCloneColor");
		//repeatBesColor = props.getColor("repeatBesColor");
		verticalSpace = props.getDouble("verticalSpace");
		minHorizontalSpace = props.getDouble("minHorizontalSpace");
		markerToContigSpace = props.getDouble("markerToContigSpace");
		minDefaultBpPerPixel = props.getDouble("minDefaultBpPerPixel");
		maxDefaultBpPerPixel = props.getDouble("maxDefaultBpPerPixel");
		padding = props.getDouble("padding");

// mdb removed 6/25/09 - simplify properties		
//		toBlockLinkColor = props.getColor("toBlockLinkColor");
//		toBlockLinkHoverColor = props.getColor("toBlockLinkHoverColor");
//		toBlockLinkFont = props.getFont("toBlockLinkFont");
//		toBlockLinkText = props.getString("toBlockLinkText");
//		toBlockLinkOffset = props.getPoint("toBlockLinkOffset");

		titleOffset = props.getPoint("titleOffset");
	}

	protected int contig; // contig number
	protected Vector<Clone> cloneList; // list of clones
	protected Vector<Object> cloneCondFilters;
	protected String showBothBESFilter;
	protected double baseWidth;
	protected Vector<Integer> fromBlockList;
	protected boolean showCloneNames;
	protected String cloneFilterPattern;
	protected int cloneFilterShow;
	protected CloneRemarks.CloneRemark[] cloneRemarks;
	protected int[] selectedRemarkIDs;
	protected int cloneRemarkShow;
	private ContigClonePool pool;
	private LinkLabel toBlockLink;
	private SouthResize southResize;

	public Contig(DrawingPanel dp, TrackHolder holder) {
		this(dp,holder,dp.getPools().getContigClonePool(),dp.getPools().getHoverMessagePool());
	}

	public Contig(DrawingPanel dp, TrackHolder holder, ContigClonePool pool, HoverMessagePool hmp) {
		super(dp,holder,minDefaultBpPerPixel,maxDefaultBpPerPixel,defaultContigOffset,hmp);
		this.pool = pool;
		cloneList = new Vector<Clone>(50,10);
		cloneCondFilters = new Vector<Object>(3,1);
		contig = NO_VALUE;
		showCloneNames = DEFAULT_SHOW_CLONE_NAMES;
		showBothBESFilter = NO_BOTH_BES_FILTER;
		baseWidth = -1.0;
		cloneFilterPattern = new String();
		toBlockLink = new LinkLabel(toBlockLinkText, toBlockLinkColor, toBlockLinkHoverColor);
		toBlockLink.setFont(toBlockLinkFont);
		toBlockLink.setToolTipText(TOBLOCK_TOOLTIP);
		toBlockLink.addMouseListener(this);
		if (holder.getHelpBar() != null) holder.getHelpBar().addHelpListener(toBlockLink);
		southResize = new SouthResize(resizeSize);
		fromBlockList = new Vector<Integer>();
		contig = NO_VALUE;
		cloneRemarkShow = CLONE_HIGHLIGHT;
	}

	public void setHeld() {
		holder.add(toBlockLink);
	}

	public void setOtherProject(int otherProject) {
		if (otherProject != this.otherProject) setup(project,contig,otherProject,null);
	}

	/**
	 * Method <code>setup</code>
	 *
	 * @param project an <code>int</code> value
	 * @param contig an <code>int</code> value
	 * @param otherProject an <code>int</code> value
	 * @param mtd a <code>MarkerTrackData</code> value (optional)
	 */
	public void setup(int project, int contig, int otherProject, MarkerTrackData mtd) {
		if (this.project != project || this.contig != contig || this.otherProject != otherProject) {
			reset(project,otherProject);
			this.contig = contig;
			fromBlockList.add(new Integer(contig));
			if (holder != null && holder.getHelpBar() != null) holder.getHelpBar().addHelpListener(toBlockLink);
			toBlockLink.addMouseMotionListener(this);		
			if (otherProject != NO_VALUE) init();
		}
		else {
			reset();
			cloneCondFilters.clear();
			if (flipped) {
				Iterator<Clone> iter;
				Clone clone;
				Vector<Clone> tvector;
				long s = size.getValue();
				tvector = new Vector<Clone>(cloneList);
				cloneList.clear();
				for (iter = tvector.iterator(); iter.hasNext();) {
					clone = iter.next();
					iter.remove();
					clone.flip(s);
					cloneList.add(0, clone);
				}
				markerList.flip(s);
			}
			filterCloneNames(new String(),CLONE_SHOW);
		}

		showCloneNames = DEFAULT_SHOW_CLONE_NAMES;
		showBothBESFilter = NO_BOTH_BES_FILTER;
		flipped = DEFAULT_FLIPPED;

		setMarkerFilters(mtd);

		if (flipped) {
			flipped = false;
			flip(true,false);
		}
	}

	public void setup(TrackData td) {
		ContigTrackData cd = (ContigTrackData)td;
		if (td.getProject() != project || cd.getContig() != contig || td.getOtherProject() != otherProject) {
			reset(td.getProject(),td.getOtherProject());
			this.contig = cd.getContig();
			if (holder != null && holder.getHelpBar() != null) holder.getHelpBar().addHelpListener(toBlockLink);
			toBlockLink.addMouseMotionListener(this);	
			firstBuild = false;
			init();
		}
		else {
			if (flipped) {
				Iterator<Clone> iter;
				Clone clone;
				Vector<Clone> tvector;
				long s = size.getValue();
				tvector = new Vector<Clone>(cloneList);
				cloneList.clear();
				for (iter = tvector.iterator(); iter.hasNext();) {
					clone = iter.next();
					iter.remove();
					clone.flip(s);
					cloneList.add(0,clone);
				}
				markerList.flip(s);
			}
			filterCloneNames(cd.getCloneFilterPattern(),cd.getCloneFilterShow());
			setSelectedRemarks(cd.getSelectedRemarkIDs(),cd.getCloneRemarkShow());
		}
		cd.setTrack(this);
		firstBuild = false;

		setMarkerFilters();

		if (flipped) {
			flipped = false;
			flip(true,false);
		}
	}

	/**
	 * Clears the internal list members and also calls super.clear().
	 *  
	 * @see MarkerTrack#clear()
	 */
	public void clear() {
		if (holder.getHelpBar() != null) holder.getHelpBar().removeHelpListener(toBlockLink);
		toBlockLink.removeMouseMotionListener(this);
		cloneCondFilters.clear();
		cloneList.clear();
		fromBlockList.clear();
		cloneRemarks = null;
		cloneRemarkShow = CLONE_HIGHLIGHT;
		selectedRemarkIDs = null;

		cloneFilterPattern = new String();
		baseWidth = -1.0;
		contig = NO_VALUE;
		super.clear();
	}

	public void clearData() {
		super.clearData();
		cloneList.removeAllElements();//.clear(); // mdb changed 2/3/10
	}

	public String toString() {
		return "[Contig " + contig + "]";
	}

	/**
	 * 
	 * @see Track#getPadding()
	 */
	public double getPadding() {
		return padding;
	}

	public String getCloneHoverMessage(String clone, byte bes) {
		String str = null;
		if (hmp != null) try {
			str = hmp.getBESHoverMessage(project,otherProject,clone,bes);
		} catch (SQLException e) { e.printStackTrace(); }
		return str;
	}

	public CloneRemarks.CloneRemark[] getCloneRemarks() {
		if (cloneRemarks == null) return new CloneRemarks.CloneRemark[0];
		return cloneRemarks;
	}

	public int[] getSelectedRemarkIDs() {
		if (selectedRemarkIDs == null) return new int[0];
		return selectedRemarkIDs;
	}

	public int[] getSelectedRemarkIndices() {
		if (selectedRemarkIDs == null || cloneRemarks == null) return new int[0];

		int[] indxs = new int[selectedRemarkIDs.length];
		int j,i;
		for (i = 0, j = 0; i < indxs.length && j < cloneRemarks.length;) {
			if      (selectedRemarkIDs[i] > cloneRemarks[j].getID()) ++j;
			else if (selectedRemarkIDs[i] != cloneRemarks[j].getID()) System.err.println("Danger! Danger! Danger! Danger! Danger!");
			else indxs[i++] = j++;
		}
		return indxs;
	}

	public void setSelectedRemarks(CloneRemarks.CloneRemark[] remarks, int show) throws IllegalArgumentException {
		if (remarks == null) setSelectedRemarks((int[])null,show);
		else {
			int[] ids = new int[remarks.length];
			for (int i = 0; i < ids.length; i++) ids[i] = remarks[i].getID();
			setSelectedRemarks(ids,show);
		}
	}

	public void setSelectedRemarks(int[] remarkIDs, int show) {
		selectedRemarkIDs = remarkIDs;
		Iterator<Clone> iter = cloneList.iterator();
		if (selectedRemarkIDs == null || selectedRemarkIDs.length == 0) {
			cloneRemarkShow = CLONE_HIGHLIGHT;
			while (iter.hasNext())
				(iter.next()).setSelectedRemark(null,CLONE_HIGHLIGHT);
			cloneCondFilters.remove(Clone.CLONE_REMARK_COND_FILTERED);
		}
		else {
			cloneRemarkShow = show;
			Arrays.sort(selectedRemarkIDs);
			while (iter.hasNext())
				(iter.next()).setSelectedRemark(selectedRemarkIDs,show);
			if (show == CLONE_SHOW || show == CLONE_HIDE)
				cloneCondFilters.add(Clone.CLONE_REMARK_COND_FILTERED);
			else
				cloneCondFilters.remove(Clone.CLONE_REMARK_COND_FILTERED);
		}
		clearAllBuild();
		//drawingPanel.repaint();
	}

	/**
	 * Filters the clones to show, hide, or highlight based on the name matched by the pattern.
	 * 
	 * @param pattern a <code>String</code> value given as a regular expression.
	 * @param show an <code>int</code> value of the CLONE_SHOW, CLONE_HIDE, CLONE_HIGHLIGHT
	 * @return a <code>boolean</code> value of true when a clone matched the pattern
	 * @exception IllegalArgumentException if show is invalid
	 */
	public boolean filterCloneNames(String pattern, int show) throws IllegalArgumentException {
		if (show != CLONE_SHOW && show != CLONE_HIDE && show != CLONE_HIGHLIGHT) throw new IllegalArgumentException("Show is invalid");
		if (pattern == null) pattern = new String();
		cloneFilterPattern = pattern;
		cloneFilterShow = show;

		if (!hasInit()) return false;

		boolean ret = false;
		try {
			ret = filterCloneNamesOnRegex(convertToRegex(pattern),show);
			if (!ret && pattern.indexOf('*') < 0) ret = filterCloneNamesOnRegex(addAsterics(pattern),show);
		}
		catch (PatternSyntaxException pse) {
			pse.printStackTrace();
		}

		return ret;
	}

	private boolean filterCloneNamesOnRegex(String pattern, int show) throws PatternSyntaxException {
		Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
		boolean showNow, changed, matched, ret;
		changed = ret = false;
		for (Clone clone : cloneList) {
			matched = p.matcher(clone.getName()).matches();
			showNow = (matched == (show==CLONE_SHOW) || show==CLONE_HIGHLIGHT);
			if (clone.setFiltered(Clone.NAME_FILTERED,!showNow)) changed = true;

			if (clone.isHighlighted() != (matched && (show==CLONE_HIGHLIGHT))) {
				clone.setHighlighted(matched && (show==CLONE_HIGHLIGHT));
				changed = true;
			}

			if (matched) ret = true;		
		}
		if (changed) clearAllBuild();
		return ret;
	}

	/**
	 * Method <code>flip</code> sets the contig to be flipped or not flipped on the next build.
	 * The contig is started out as being not flipped.  This method may also change the contig's
	 * start and end if needed.
	 *
	 * @param flip a <code>boolean</code> value
	 * @param changeEnds a <code>boolean</code> value of true to modify the contig's start and end to corrispond to
	 *                                         the same location as before considering the flip.
	 * @return a <code>boolean</code> value of true on a change
	 */
	public boolean flip(boolean flip, boolean changeEnds) {
		if (flipped != flip && hasInit()) {
			flipped = flip;
			Iterator<Clone> iter;
			Vector<Clone> tvector;
			long start, end, s;
			s = size.getValue();
			tvector = new Vector<Clone>(cloneList);
			cloneList.clear();
			Clone clone;
			for (iter = tvector.iterator(); iter.hasNext();) {
				clone = iter.next();
				iter.remove();
				clone.flip(s);
				cloneList.add(0, clone);
			}
			markerList.flip(s);

			if (changeEnds) {
				start = getStart();
				end   = getEnd();
				setStart(s - end);
				setEnd(s - start);
			}

			clearTrackBuild();
			return true;
		}
		return false;
	}

	/**
	 * Sets the clone names to be shown or hidden on the next build.
	 * 
	 * @param show
	 */
	public void showCloneNames(boolean show) {
		if (show != showCloneNames) {
			showCloneNames = show;
			clearTrackBuild();
		}
	}

	/**
	 * Method <code>showBothBESFilter</code> sets the clones up to be filtered on <code>filter</code>.
	 * filter =&gt; Contig.NO_BOTH_BES_FILTER    -&gt; not filter on this
	 *           Clone.BOTH_BES_COND_FILTERED -&gt; filter on having hits on both bes's
	 *           Clone.BOTH_BES_CURRENT_COND_FILTERED -&gt; filter on having hits currently showing on both bes's 
	 *
	 * @param filter a <code>String</code> value
	 */
	public void showBothBESFilter(String filter) {
		if (filter != showBothBESFilter) {
			if (!showBothBESFilter.equals(NO_BOTH_BES_FILTER)) cloneCondFilters.remove(showBothBESFilter);
			if (!filter.equals(NO_BOTH_BES_FILTER)) cloneCondFilters.add(filter);

			showBothBESFilter = filter;
			clearAllBuild();
		}
	}

	/**
	 * @return the contig number.
	 */
	public int getContig() {
		return contig;
	}

	public int[] getContigs() {
		return new int[] {contig};
	}

	/**
	 * Sets the new contig number to contig, clearing init if contig is different then the current contig number.
	 * Also sets the block list when the To Block link is clicked to the single contig if the current list doesn't
	 * contain contig <code>contig</code>.
	 * 
	 * @param contig the new contig number
	 */
	public void setContig(int contig) {
		if (contig != this.contig) {
			this.contig = contig;
			clearHeight();
			clearWidth();
			Integer contigInt = new Integer(contig);
			if (!fromBlockList.contains(contigInt)) {
				fromBlockList.clear();
				fromBlockList.add(contigInt);
			}
			clearInit();
			init();
		}
	}

	/**
	 * Method <code>setFromBlockList</code> sets the block list for when the user clicks on the To Block link.
	 *
	 * @param contigList a <code>Collection</code> value
	 */
	public void setFromBlockList(Collection<Integer> contigList) {
		fromBlockList.clear();
		fromBlockList.addAll(contigList);
	}

	protected boolean init() {
		if (hasInit()) return true;
		if (project < 1 || contig < 0 || otherProject < 1) {
			System.out.println("Attempting to initialize contig with invalid values. "+
					"Project: "+project+" Contig: "+contig+" Other Project: "+otherProject);
			return false;
		}
		if (SyMAP.DEBUG) System.out.println("In contig init");

		cloneList.clear();
		markerList.clear();

		try {
			pool.setContig(this,size,cloneList,cloneCondFilters,markerList);
		} catch (SQLException s1) {
			s1.printStackTrace();
			System.err.println("Initializing Contig failed.");
			pool.close();
			return false;
		}

		CloneRemarks crs = null;
		try {
			crs = pool.getCloneRemarks(getProject());
		} catch (SQLException s) {
			System.err.println("Unable to get Clone Remarks for project "+getProject()+" from database.");
			s.printStackTrace();
		}
		Collection<CloneRemarks.CloneRemark> remarks = new LinkedHashSet<CloneRemarks.CloneRemark>();
		if (crs != null) {
			for (Clone clone : cloneList)
				crs.setRemarks(remarks,clone.getRemarks());
		}
		cloneRemarks = (CloneRemarks.CloneRemark[])remarks.toArray(new CloneRemarks.CloneRemark[0]);
		Arrays.sort(cloneRemarks);

		if (SyMAP.DEBUG) System.out.println("CloneRemarks.length = "+cloneRemarks.length+" from "+crs);

		resetStart();
		resetEnd();
		setInit();

		return true;
	}

	public boolean build() {
		if (hasBuilt()) return true;
		if (!hasInit()) return false;

		//System.out.println("Contig.build");
		
		setFullBpPerPixel();

		toBlockLink.setupLink();

		if (SyMAP.DEBUG) System.out.println("In contig build");

		boolean gaveWidth = (width != NO_VALUE);
		if (!gaveWidth) width = 0;

		double maxlayer, layers[], tempArray[], startPixels;
		double horizontalSpace, x, y1, y2;
		int i;
		GenomicsNumber length;

		// Set base bp per pixel
		if (height > 0)
			adjustBpPerPixel(GenomicsNumber.getBpPerPixel(bpPerCb,Math.abs(end.getValue()-start.getValue()),height));

		if (!validSize()) {
			//if (drawingPanel != null) drawingPanel.displayWarning("Unable to size contig view as requested."); // mdb removed 5/25/07 #119
			//Utilities.showWarningMessage("Unable to size contig view as requested."); // mdb added 5/25/07 #119 // mdb removed 7/2/07
			System.err.println("Unable to size contig view as requested."); // mdb added 7/2/07
			adjustSize();
		}

		length = new GenomicsNumber(this,end.getValue() - start.getValue());
		startPixels = start.getPixelValue();

		/*
		 * Figure layers and sizes.
		 */
		layers = new double[LAYER_SIZE];
		layers[0] = Double.MIN_VALUE;
		maxlayer = 0;
		for (Clone clone : cloneList) {
			clone.setTextVisible(showCloneNames);
			clone.setTextLayout();
			if (!clone.isVisible()) continue;

			for (i = 0; i <= maxlayer; i++) {
				if (clone.getMinCB().getPixelValue() > layers[i])
					break;
			}
			if (i > maxlayer) {
				maxlayer = i;
				if (maxlayer >= layers.length) { // just in case
					tempArray = new double[layers.length + LAYER_SIZE];
					System.arraycopy(layers, 0, tempArray, 0, layers.length);
					layers = tempArray;
				}
			}
			layers[i] = clone.getEndPixel(verticalSpace);
			clone.setLayer(i);
		}

		rect.setRect(trackOffset.getX(), trackOffset.getY(), width, 0);

		horizontalSpace = 0;
		if (maxlayer > 0) {
			horizontalSpace = rect.width / (double) maxlayer;
			if (!gaveWidth && horizontalSpace < minHorizontalSpace)
				horizontalSpace = minHorizontalSpace;
		}
		rect.width = maxlayer * horizontalSpace;
		rect.height = length.getPixelValue();

		if (orient == LEFT_ORIENT) {
			markerList.setup(trackOffset,orient,rect.height);
			rect.x = markerList.getRect().getX() + markerList.getRect().getWidth() + markerToContigSpace;
		}
		else {
			markerList.setup(new Point2D.Double(rect.x+rect.width+markerToContigSpace,rect.y),orient,rect.height);
		}
		markerList.build(startPixels);

		/*
		 * Set up Clones
		 */
		for (Clone clone : cloneList) {
			if (!clone.isVisible()) continue;

			if (orient == LEFT_ORIENT)
				x = rect.x + clone.getLayer() * horizontalSpace;
			else
				x = rect.x + rect.width - clone.getLayer() * horizontalSpace;
			y1 = rect.y
			+ (clone.getMinCB().getPixelValue() - startPixels);
			y2 = rect.y
			+ (clone.getMaxCB().getPixelValue() - startPixels);
			clone.setPoints(x, y1, y2);
			clone.setTextLocation(x - clone.getTextWidth() - TINY_OFFSET, y1);
		}

		Rectangle2D mRect = markerList.getRect();
		dimension.setSize(Math.ceil(Math.max(mRect.getWidth() +mRect.getX(), rect.width + rect.x)) +defaultContigOffset.getX(), 
				Math.ceil(Math.max(mRect.getHeight()+mRect.getY(), rect.height + rect.y))+defaultContigOffset.getY());

		if (orient == LEFT_ORIENT)
			toBlockLink.setLocation((int)(mRect.getX() + toBlockLinkOffset.getX()),
					(int)(mRect.getY() - toBlockLinkOffset.getY() - toBlockLink.getHeight()));
		else
			toBlockLink.setLocation((int)(mRect.getX()+mRect.getWidth()-toBlockLinkOffset.getX() - toBlockLink.getWidth()),
					(int)(mRect.getY() - toBlockLinkOffset.getY() - toBlockLink.getHeight()));

		// determine if we need to move things over to adjust for the title
		if (orient == RIGHT_ORIENT) {
			Rectangle2D titleBounds = getTitleBounds();
			double highest = toBlockLink.getX() - titleOffset.getX() - (titleBounds.getX()+titleBounds.getWidth());
			if (highest < 0) {
				// move everything over by |highest|
				double off = 0 - highest;
				if (SyMAP.DEBUG) System.out.println("\t\t\t\t %%%% Readjusting contig size by = "+off);
				rect.x += off;
				for (Clone clone : cloneList)
					clone.offset(off);
				markerList.offsetX(off);
				dimension.setSize(dimension.getWidth()+off,dimension.getHeight());
				toBlockLink.setLocation((int)(toBlockLink.getX()+off),toBlockLink.getY());
				trackOffset.x += off;
			}

		}

		setTitle();

		if (baseWidth < 0)
			baseWidth = rect.getWidth();
		if (!gaveWidth)
			width = NO_VALUE;

		Point p = new Point();
		if (markerList.getShowNames() != MarkerList.NO_NAMES) {
			p.x = (int)markerList.getLine().getX2();
			if (orient == RIGHT_ORIENT) p.x -= resizeSize.width;
			p.y = (int)(markerList.getLine().getY2()-(resizeSize.height/3.0));
		}
		else {
			if (orient == LEFT_ORIENT)
				p.x = (int)(trackOffset.x+SMALL_OFFSET);
			else
				p.x = (int)(rect.x+rect.width+SMALL_OFFSET);
			p.y = (int)(rect.y+rect.height-(resizeSize.height/3.0));
		}
		southResize.setLocation(p);

		setBuild();
		
		return true;
	}

	/**
	 * @return the current width of the contig in pixels.
	 */
	public int getContigWidth() {
		return (int) Math.round(rect.getWidth());
	}

	/**
	 * @return the base width of the contig.
	 */
	public int getBaseWidth() {
		return (int) Math.round(baseWidth);
	}

	/**
	 * Method <code>getMarker</code> Searches the marker list for the marker with
	 * a name equal to <code>name</code>.
	 *
	 * @param name a <code>String</code> value of the name of the marker to find.
	 * @param contig an <code>int</code> value of the contig to look in.
	 * @return a <code>Marker</code> value of found marker or null if marker not found
	 */
	public Marker getMarker(String name, int contig) {
		if (this.contig == contig || contig == NO_VALUE)
			return markerList.getMarker(name);
		return null;
	}

	public Marker[] getMarkers(int contig) {
		if (this.contig == contig || contig == NO_VALUE) {
			return (Marker[])markerList.getMarkers().toArray(new Marker[0]);
		}
		return null;
	}

	/**
	 * Method <code>getClone</code> searches the list of clones for a clone 
	 * with a name equal to <code>name</code>.
	 *
	 * @param name a <code>String</code> of the name of clone to find
	 * @return a <code>Clone</code> value of the clone or null if not found
	 */
	public Clone getClone(String name) {
		for (Clone c : cloneList) {
			if (c.getName().equals(name))
				return c;
		}
		if (SyMAP.DEBUG) System.out.println("#!#!#!#!#! Name ["+name+"] clone cannot be found #!#!#!#!#!");
		return null;
	}

	/**
	 * Paints the Contig.
	 */
	public void paintComponent(Graphics g) { 
		if (hasBuilt()) {
			super.paintComponent(g);

			Graphics2D g2 = (Graphics2D)g;
			for (Clone clone : cloneList)
				clone.paintComponent(g2);

			markerList.paintComponent(g2);	
			g2.setPaint(resizeColor);
			southResize.paintIcon(holder,g,0,0);
		}
	}

	public boolean isContig(int contigNum) {
		boolean success = false;
		try {
			ArrayList<Integer> tvec = new ArrayList<Integer>(1);
			tvec.add(new Integer(contigNum));
			success = pool.isContigs(getProject(),tvec);
		} catch (Exception e) { }
		return success;
	}
	
	public int getNumOfClones() { // mdb added 3/30/07 #112
		int count = 0;
		
		for (Clone clone : cloneList) 
			if (clone.isVisible()) count++;
		
		return count;
	}

	/**
	 * Method <code>getHelpText</code> returns the help text desired when the
	 * mouse is at a certain point.
	 * 
	 * @param event
	 *            a <code>MouseEvent</code> value
	 * @return a <code>String</code> value
	 */
	public String getHelpText(MouseEvent event) {
		Point point = event.getPoint();
		String pretext = "Contig Track ("+getTitle()+"):  "; // mdb added 3/30/07 #112

		for (Clone clone : cloneList) {
			if (clone.isHover())
				return pretext+clone.getHoverMessage(point); // mdb changed 3/30/07 #112
		}

		// clone isn't being hovered	
		if (rect.contains(point)) {
			return pretext+getNumOfClones()+" of "+cloneList.size()+" clones shown.  Hover over clone to highlight hits.  Right-click for menu."; // mdb changed 3/30/07 #112
		}
		else if (markerList.contains(point)) {
			return pretext+super.getHelpText(event); // mdb changed 3/30/07 #112
		}
		else if (isSouthResizePoint(point)) {
			return pretext+"Drag to resize the contig."; // mdb changed 3/30/07 #112
		}
		else {
			//return "Shift+Drag to move, Control+Drag to select a region."; // mdb removed 3/29/07 #112
			return pretext+getNumOfClones()+" of "+cloneList.size()+" clones shown.  Right-click for menu.";
		}
	}

	/**
	 * Sets markers to be highlight or not highlighted
	 * determined by <code>Marker.setHover(Point)</code>.
	 *  
	 * @see java.awt.event.MouseMotionListener#mouseMoved(java.awt.event.MouseEvent)
	 */
	public void mouseMoved(MouseEvent e) {
		super.mouseMoved(e);

		Point p = e.getPoint();
		boolean changed = false;
		for (Clone clone : cloneList)
			if ( clone.setHover(p) ) changed = true;
		Marker marker = markerList.setHover(p);
		if (drawingPanel != null) {
			if (marker != null)
				drawingPanel.setHoveredMarker(marker,marker.isHover());
			else if (changed) drawingPanel.repaint();
		}
	}

	/**
	 * Handles the clicking of the To Block link, notifying the track observer.
	 * 
	 * @see java.awt.event.MouseListener#mouseClicked(java.awt.event.MouseEvent)
	 */
	public void mouseClicked(MouseEvent e) {
		super.mouseClicked(e);
		
		if (e.getSource() == toBlockLink)
			notifyObserver(fromBlockList);
		else {
			Marker marker = markerList.getMarker(e.getPoint());
			if (drawingPanel != null && marker != null) 
				drawingPanel.setClickedMarker(marker,!marker.isClickHighlighted());
		}
	}

	protected int getBP(double y) {
		y -= rect.getY();
		if (y < 0)
			y = 0;
		else if (y > rect.getHeight())
			y = rect.getHeight();
		y *= bpPerPixel;
		y += start.getBPValue();

		double cb = y / bpPerCb;
		if (cb != Math.round(cb)) {
			cb = Math.round(cb);
			y = bpPerCb * cb;
		}

		if (y < start.getBPValue())
			y = start.getBPValue();
		else if (y > end.getBPValue())
			y = end.getBPValue();

		return (int)Math.round(y);
	}

	public TrackData getData() {
		return new ContigTrackData(this);
	}

	protected boolean isSouthResizePoint(Point p) {
		return southResize.contains(p);
	}

	protected String getTitle() {
		return getProjectDisplayName() + ": Contig " + contig;
	}

	protected Point2D getTitlePoint(Rectangle2D titleBounds) {
		if (orient == LEFT_ORIENT) return getLeftTitlePoint(titleBounds);
		else return getRightTitlePoint(titleBounds);
	}

	private Point2D getRightTitlePoint(Rectangle2D titleBounds) {
		double highest = toBlockLink.getX() - titleOffset.getX() - (titleBounds.getX()+titleBounds.getWidth());

		Point2D point = new Point2D.Double( (rect.getWidth() / 2.0 + rect.x) - (titleBounds.getWidth() / 2.0 + titleBounds.getX()), 
				rect.y - titleOffset.getY());

		if (point.getX() + titleBounds.getX() + titleBounds.getWidth() > dimension.getWidth()) {
			point.setLocation(dimension.getWidth() - (titleBounds.getX() + titleBounds.getWidth()), point.getY());
		}

		if (point.getX() < 0) point.setLocation(0,point.getY());
		else if (point.getX() > highest) point.setLocation(highest,point.getY());

		if (SyMAP.DEBUG) System.out.println("Contig "+this+" titlePoint: "+point);

		return point;
	}

	private Point2D getLeftTitlePoint(Rectangle2D titleBounds) {
		double x, off, lowest;
		lowest = toBlockLink.getX() + toBlockLink.getWidth() + titleOffset.getX();
		if (dimension.getWidth() < lowest + titleBounds.getX() + titleBounds.getWidth()) {
			x = lowest;
		} 
		else {
			x = (rect.getWidth() / 2.0 + rect.x) - (titleBounds.getWidth() / 2.0 + titleBounds.getX());
			if (x < lowest) {
				off = dimension.getWidth() - (x + titleBounds.getX() + titleBounds.getWidth());
				if (off < 0) x = lowest;
				else x += off;
			}
			if (x < lowest) x = lowest; // just to make sure
		}
		return new Point2D.Double(x, rect.y - titleOffset.getY());		
	}
}


