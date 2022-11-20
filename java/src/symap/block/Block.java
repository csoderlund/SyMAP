package symap.block;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;
import java.util.Vector;
import java.util.LinkedHashSet;
import java.util.Collection;
import number.GenomicsNumber;
import symap.SyMAP;
import symap.pool.HoverMessagePool;
import symap.marker.Marker;
import symap.marker.MarkerTrack;
import symap.marker.MarkerTrackData;
import symap.marker.MarkerList;
import symap.track.Track;
import symap.track.TrackData;
import symap.track.TrackHolder;
import symap.drawingpanel.DrawingPanel;
import util.ErrorReport;
import util.PropertiesReader;
import util.Rule;
import util.Utilities;

/**
 * The Block view track.
 */
public class Block extends MarkerTrack {
	private static final double SMALL_OFFSET = 4;

	public static final int MAX_BLOCKS_ALLOWED;
	private static Color border;
	private static Color footerColor;
	private static Font footerFont;
	private static double footerSpace;
	public static Color blockColor1;// = Color.blue;
	public static Color blockColor2;// = Color.blue;
	private static Color contigColor;
	private static Font contigFont;
	private static double minDefaultBpPerPixel;
	private static double maxDefaultBpPerPixel;
	private static double defaultWidth;
	private static double contigNameOffset;
	private static double contigNameSpace;
	private static Point2D defaultBlockOffset;
	private static double padding;
	private static Point2D titleOffset;
	static {
		PropertiesReader props = new PropertiesReader(SyMAP.class.getResource("/properties/block.properties"));
		border = props.getColor("border");
		footerColor = props.getColor("footerColor");
		footerFont = props.getFont("footerFont");
		footerSpace = props.getDouble("footerSpace");
		contigColor = props.getColor("contigColor");
		blockColor1 = props.getColor("blockColor1");
		blockColor2 = props.getColor("blockColor2");
		contigFont = props.getFont("contigFont");
		minDefaultBpPerPixel = props.getDouble("minDefaultBpPerPixel");
		maxDefaultBpPerPixel = props.getDouble("maxDefaultBpPerPixel");
		defaultWidth = props.getDouble("defaultWidth");
		contigNameOffset = props.getDouble("contigNameOffset");
		contigNameSpace = props.getDouble("contigNameSpace");
		defaultBlockOffset = props.getPoint("blockOffset");
		padding = props.getDouble("padding");
		MAX_BLOCKS_ALLOWED = props.getInt("maxBlocksAllowed");
		titleOffset = props.getPoint("titleOffset");
	}

	protected Collection<Integer> contigList;
	protected Collection<Integer> defaultContigList;
	protected String contigSetText, defaultContigSetText;
	protected String block;
	private Vector<InnerBlock> innerBlockList;
	private TextLayout footerLayout;
	private Point2D.Float footerPoint;
	private ContigPool pool;

	public Block(DrawingPanel dp, TrackHolder holder) {
		this(dp,holder,dp.getPools().getContigPool(),dp.getPools().getHoverMessagePool());
	}

	public Block(DrawingPanel dp, TrackHolder holder, ContigPool pool, HoverMessagePool hmp) {
		super(dp,holder,minDefaultBpPerPixel,maxDefaultBpPerPixel,defaultBlockOffset,hmp);
		this.pool = pool;
		innerBlockList = new Vector<InnerBlock>(10,2);
		contigList = new LinkedHashSet<Integer>(50);
		defaultContigList = new LinkedHashSet<Integer>(50);
		contigSetText = new String();
		defaultContigSetText = new String();
		footerPoint = new Point2D.Float();
	}

	public void setOtherProject(int otherProject) {
		if (otherProject != this.otherProject) {
			if (contigSetText == null || contigSetText.length() == 0)
				setup(project,block,otherProject,null);
			else
				setup(project,contigSetText,otherProject,null);
		}
	}
	
	// fix for 3D to show FPC chr in 2D view
	public void setBlock(String block) {
		this.block = block;
	}

	public void setup(int project, String contigs, int otherProject, MarkerTrackData mtd) {
		if (contigs.indexOf(".") > 0) {
			block = contigs;
			if (otherProject > 0 && project > 0)
				contigs = getContigsFromBlock(project,otherProject,block);
			else {
				this.project = project;
				return ; // wait for projects to get set
			}		
		}
		else block = "";

		if (this.project != project 
			|| !contigList.equals(getContigs(contigs)) 
			|| this.otherProject != otherProject) 
		{
			if (this.project != project || this.otherProject != otherProject) innerBlockList.clear();
			reset(project,otherProject);
		}
		else reset();

		defaultContigSetText = contigSetText = contigs;
		setIntVector(defaultContigList,getContigs(contigs));
		setIntVector(contigList,defaultContigList);

		if (flipped) reverse(innerBlockList);
		for (InnerBlock ib : innerBlockList)
			ib.reset(flipped,contigList.contains(ib.getContig())); // CAS520
		flipped = DEFAULT_FLIPPED;

		if (otherProject != NO_VALUE) init();

		setMarkerFilters(mtd);

		if (flipped) {
			flipped = false;
			flip(true,false);
		}
	}

	public void setup(TrackData td) {
		BlockTrackData bd = (BlockTrackData)td;

		if (td.getProject() != project || !contigList.equals(bd.getContigList()) || td.getOtherProject() != otherProject) {
			if (td.getProject() != project || td.getOtherProject() != otherProject) innerBlockList.clear();
			reset(td.getProject(),td.getOtherProject());
		}

		boolean flip = flipped;
		bd.setTrack(this);

		if (flip) reverse(innerBlockList);
		for (InnerBlock ib : innerBlockList)
			ib.reset(flip,contigList.contains(ib.getContig()));

		firstBuild = false;
		init();
		setMarkerFilters();

		if (flipped) {
			flipped = false;
			flip(true,false);
		}
	}

	public TrackData getData() {
		return new BlockTrackData(this);
	}

	/**
	 * Clears the private member lists and calls super.clear().
	 * @see MarkerTrack#clear()
	 */
	public void clear() {
		contigSetText = new String();
		defaultContigSetText = new String();
		defaultContigList.clear();
		contigList.clear();
		footerPoint.setLocation(0,0);
		super.clear();
	}

	public void clearData() {
		super.clearData();
		innerBlockList.removeAllElements();
	}

	public String toString() {
		return "[Block (Contigs: " + contigList + ")]";
	}

	/**
	 * Returns the padding desired for this block.
	 * @see Track#getPadding()
	 */
	public double getPadding() {
		return padding;
	}

	/**
	 * Method <code>flip</code> sets the block to be flipped or not flipped on 
	 * the next build.  The block is started out as being not flipped.
	 */
	public boolean flip(boolean flip, boolean changeEnds) {
		if (flipped != flip) {
			flipped = flip;
			markerList.reverse();
			reverse(innerBlockList);
			for (InnerBlock ib : innerBlockList)
				ib.flip();
			clearTrackBuild();
			return true;
		}
		return false;
	}

	/**
	 * Method <code>getContigList</code> returns a copy of the Collection of current contigs.
	 */
	public Collection<Integer> getContigList() {
		return new LinkedHashSet<Integer>(contigList);
	}

	public String getDefaultContigs() {
		return getContigs(defaultContigList);
	}

	/**
	 * Resets the contig list to the original if its not equal to it already. 
	 * If needed, the clearInit() is called.
	 */
	public void resetContigList() {
		if (!equalNumbers(defaultContigList, contigList)) {
			setIntVector(contigList, defaultContigList);
			clearInit();
		}
		contigSetText = defaultContigSetText;
		init();
	}

	public void setContigList(Collection<Integer> contigNumbers) {
		contigSetText = getContigs(contigNumbers);
		setContigListVector(contigNumbers);
	}

	/**
	 * Method <code>setContigList</code> sets the current contig list
	 *
	 * @param contigNumbers a <code>String</code> value of contig numbers in comma dash form
	 * @exception IllegalArgumentException if contigNumbers is not properly formatted
	 */
	public void setContigList(String contigNumbers) throws IllegalArgumentException {
		contigSetText = contigNumbers;
		setContigListVector(getContigs(contigNumbers));
	}

	/**
	 * Sets the default contig list and the current contig list.
	 * 
	 * @param contigNumbers the numbers in comma dash form to make the default contig list.
	 * @exception IllegalArgumentException if contigNumbers is not properly formatted
	 */
	public void setDefaultContigList(String contigNumbers) throws IllegalArgumentException {
		defaultContigSetText = contigNumbers;
		contigSetText = defaultContigSetText;
		setDefaultContigList(getContigs(contigNumbers));
	}

	public int[] getContigs() {
		int[] ret = new int[innerBlockList.size()];
		int i = 0;
		for (InnerBlock ib : innerBlockList) {
			if (ib.isVisible())
				ret[i++] = ib.getContig();
		}
		if (i < ret.length)
			ret = Utilities.copy(ret,i);
		return ret;
	}

	protected boolean init() {
		if (hasInit()) return true;
		if (contigList.isEmpty() || project < 1 || otherProject < 1) return false;

		if (SyMAP.DEBUG) System.out.println("In Block init");

		resetStart();
		resetEnd();
		markerList.clear();
		int preSize = contigList.size();

		try {
			pool.setBlock(this,contigList,innerBlockList,size);
		} catch (SQLException s1) {
			ErrorReport.print(s1, "Initializing block");
			pool.close();
			return false;
		}
		if (preSize != contigList.size()) {
			
			Utilities.showWarningMessage("Not all contigs were able to be shown in the view."); 
			contigSetText = getContigs(contigList);
		}

		setInit();

		return true;
	}

	public boolean build() {
		if (hasBuilt()) return true;
		if (!hasInit()) return false;

		if (width == NO_VALUE) width = defaultWidth;

		long sum;
		double recty, precty, m2cSpace, pixelDif, rectheight;
		FontRenderContext frc = getFontRenderContext();
		Rectangle2D fb, mRect;

		/*
		 * figure spacing and set color
		 */
		sum = 0;
		m2cSpace = 0;
		int i = 0;
		for (InnerBlock ib : innerBlockList) {
			if (ib.isVisible()) {
				ib.setColor((i % 2 == 0 ? blockColor1 : blockColor2)); 
				i++;
				sum += ib.getLength();		
				m2cSpace = Math.max(m2cSpace, ib.setTextLayout(frc));
			}
		}
		m2cSpace += contigNameOffset + SMALL_OFFSET;

		start.setValue(0);
		end.setValue(sum);
		setFullBpPerPixel();

		if (height != NO_VALUE) {
			adjustBpPerPixel(GenomicsNumber.getBpPerPixel(bpPerCb,end.getValue(),height));
		}

		if (!validSize() && drawingPanel != null) {
			
			System.err.println("Unable to size block view as requested."); 
			adjustSize();
		}

		rect.setRect(trackOffset.getX(), trackOffset.getY(), width, end.getPixelValue());

		if (orient == LEFT_ORIENT) {
			markerList.setup(trackOffset,orient,rect.height);
			rect.x = markerList.getRect().getX() + markerList.getRect().getWidth() + m2cSpace;
		}
		else {
			markerList.setup(new Point2D.Double(rect.x+rect.width+m2cSpace,rect.y),orient,rect.height);
		}

		/*
		 * set up inner blocks
		 */
		precty = recty = rect.getY();
		pixelDif = Math.abs(end.getPixelValue() - start.getPixelValue());
		for (InnerBlock ib : innerBlockList) {
			if (ib.isVisible()) {
				rectheight = ib.getPercent(sum) * pixelDif;
				ib.setRectangle(rect.getX(), recty, width, rectheight);
				recty = recty + rectheight;
				precty = ib.setText(precty);
				for (Marker marker : ib.getMarkers())
					marker.setYPoint(ib.getYPoint(marker.getPosition().getValue()));
			}
		}

		markerList.build(-1);

		footerLayout = new TextLayout(end.toString(bpPerCb), footerFont, frc);
		fb = footerLayout.getBounds();
		footerPoint.x = (float) ((rect.getX() + (rect.getWidth() / 2.0)) - (fb.getX() + (fb.getWidth() / 2.0)));
		footerPoint.y = (float) (rect.getY() + rect.getHeight() + footerSpace + fb.getHeight());

		mRect = markerList.getRect();
		dimension.setSize(Math.ceil(Math.max(mRect.getWidth() + mRect.getX(), 
				Math.max(rect.width + rect.x, footerPoint.x + fb.getWidth()))) + defaultBlockOffset.getX(), 
				Math.ceil(Math.max(mRect.getHeight() + mRect.getY(), 
						footerPoint.y + fb.getHeight())) + defaultBlockOffset.getY());
		setTitle();

		setBuild();

		return true;
	}

	/**
	 * Searches the marker list for the marker with a name equal to <code>name</code>.
	 * In contig <code>contig</code>.  If contig is equal to NO_VALUE, than the first 
	 * marker with the name found is returned.
	 * Returns null if the marker can not be found.
	 * 
	 * @see MarkerTrack#getMarker(java.lang.String, int)
	 */
	public Marker getMarker(String name, int contig) {
		Marker marker = null;
		for (InnerBlock ib : innerBlockList) {
			if (ib.getContig() == contig && ib.isVisible() || contig == NO_VALUE) {
				marker = ib.getMarker(name);
				if (contig != NO_VALUE || marker != null) break;
			}
		}
		return marker;
	}

	public Marker[] getMarkers(int contig) {
		for (InnerBlock ib : innerBlockList) {
			if (ib.getContig() == contig && ib.isVisible())
				return (Marker[])ib.getMarkers().toArray(new Marker[0]);
		}
		return null;
	}

	/**
	 * Method <code>getPoint</code> returns the point along the rectangle given the base pair orientation and
	 * contig.  The value is updated based on if the block is flipped, so <code>num</code> should be the raw value
	 * (not updated for any flipping).
	 *
	 * @param num a <code>long</code> value of the base pair value
	 * @param orient an <code>int</code> value of the orientation (LEFT_ORIENT gives right side of rectangle)
	 * @param contig an <code>int</code> value of the contig where the position num is
	 * @return a <code>Point2D</code> value of the point along the rectangle or null if out of range
	 */
	public Point2D getPoint(long num, int orient, int contig) {
		Point2D point = null;
		for (InnerBlock ib : innerBlockList) {
			if (ib.getContig() == contig && ib.isVisible()) {
				point = ib.getPoint(num, orient);
				break;
			}
		}
		return point;
	}

	/**
	 * Method <code>isContigVisible</code> returns true if this block
	 * contains the contig and it is visible.
	 */
	public boolean isContigVisible(int contig) {
		for (InnerBlock ib : innerBlockList) {
			if (ib.getContig() == contig)
				return ib.isVisible();
		}
		return false;
	}
	/**
	 * Method <code>getXPoint</code> gets the x point along the block given the orientation.
	 * So if orient == LEFT_ORIENT the right side of the rectangle is given, otherwise the left
	 * side is returned
	 */
	public double getXPoint(int orient) {
		double x = rect.x;
		if (orient == LEFT_ORIENT) x += rect.getWidth();
		return x;
	}

	public void paintComponent(Graphics g) {
		if (hasBuilt()) {
			Graphics2D g2 = (Graphics2D)g;

			for (InnerBlock ib : innerBlockList)
				if (ib.isVisible()) ib.paintComponent(g2);
			markerList.paintComponent(g2);

			g2.setPaint(footerColor);
			footerLayout.draw(g2, footerPoint.x, footerPoint.y);
		}

		super.paintComponent(g);
	}

	/**
	 * Method <code>setStartBP</code> checks the blocks and removes the ones 
	 * that fall before the given base pair value.  The block needs to have 
	 * been built before in order to determine the placing of the contigs.
	 */
	public void setStartBP(long startBP) throws IllegalArgumentException {
		if (!hasInit()) return ;
		if (startBP > size.getBPValue()) 
			throw new IllegalArgumentException("Start value "+startBP+" is greater than size "+size.getBPValue()+".");
		if (startBP < 0) throw new IllegalArgumentException("Start value is less than zero.");

		boolean change = false;
		double startPixel = getPixel(startBP);

		if (SyMAP.DEBUG) System.out.println("Setting the start from pixel "+startPixel);

		for (InnerBlock ib : innerBlockList) {
			if (ib.isVisible() && !ib.isOutsideOf(startPixel)) {
				if (SyMAP.DEBUG) System.out.println("Removing the contig "+ib.getContig()+" from the block view by way of start.");

				ib.setVisible(false);
				contigList.remove(ib.getContig());
				change = true;
			}
		}

		if (change) {
			firstBuild = true;
			clearAllBuild();
			contigSetText = getContigs(contigList);
		}
	}

	/**
	 * Method <code>setEndBP</code> checks the blocks and removes the ones that 
	 * fall completely outside of 0 to the given base pair value.  The block 
	 * needs to have been built before to determine the placing of the contigs.
	 */
	public long setEndBP(long endBP) throws IllegalArgumentException {
		if (!hasInit()) return 0;
		if (endBP < 0) throw new IllegalArgumentException("End value is less than zero.");

		boolean change = false;
		double endPixel = getPixel(endBP);

		if (SyMAP.DEBUG) System.out.println("Setting the end from pixel "+endPixel);

		for (InnerBlock ib : innerBlockList) {
			if (ib.isVisible() && !ib.isInsideOf(endPixel)) {
				if (SyMAP.DEBUG) System.out.println("Removing the contig "+ib.getContig()+" from the block view by way of end.");

				ib.setVisible(false);
				contigList.remove(ib.getContig());
				change = true;
			}
		}

		if (change) {
			firstBuild = true;

			clearAllBuild();
			contigSetText = getContigs(contigList);
		}

		return endBP;
	}

	public long getStart() {
		return Long.MIN_VALUE;
	}

	public long getEnd() {
		return Long.MAX_VALUE;
	}

	public String getHelpText(MouseEvent event) {
		Point point = event.getPoint();
		String pretext = "Block Track (" + getTitle() + "):  "; 
		if (rect.contains(point)) {
			for (InnerBlock ib : innerBlockList) {
				if (ib.contains(point) && ib.getGroupList() != null)
					return pretext
						+(ib.getGroupList().equals("") ? "" :
								"Contig "+ib.getContig()+" hits "+ib.getGroupList()+".  ") 
						+ "Left-click for detailed view, right-click for menu."; 
			}
			return "Left-click on a contig to view it in detail, right-click for menu.";
		}
		else if (markerList.contains(point)) {
			return pretext+super.getHelpText(event); 
		}
		else {
			return pretext+ "Right-click for menu."; 
		}
	}

	/**
	 * Determines if the user clicked on a block and notifies the track observer
	 * of the contig that was clicked if needed.
	 */
	public void mouseClicked(MouseEvent e) {
		super.mouseClicked(e);
		
		Point p = e.getPoint();
		if (rect.contains(p)) {
				for (InnerBlock ib : innerBlockList) {
					if (ib.isVisible() && ib.contains(p)) {
						setCursor(null);
						notifyObserver(ib.getContig());
						break;
					}
				}
		}
		else {
			Marker marker = markerList.getMarker(p);
			if (drawingPanel != null && marker != null) 
				drawingPanel.setClickedMarker(marker,!marker.isClickHighlighted());
		}
	}


	public void mouseMoved(MouseEvent e) {
		super.mouseMoved(e);
	}

	/**
	 * Method <code>reverse</code> reverses the list.
	 *
	 * @param list a <code>List</code> value
	 */
	public static void reverse(List list) {
		Stack s = new Stack();
		s.addAll(list);
		list.clear();
		while (!s.empty())
			list.add(s.pop());
	}

	protected boolean isSouthResizePoint(Point p) {
		return (p.getX() >= rect.x &&
				p.getX() <= rect.x + rect.width && 
				p.getY() >= (rect.y+rect.height) - MOUSE_PADDING && 
				p.getY() <= (rect.y+rect.height) + MOUSE_PADDING);
	}

	protected String getTitle() {
		if (block != null && block.length() > 0) return getProjectDisplayName()+" "+block;
		return getProjectDisplayName();
	}

	protected Point2D getTitlePoint(Rectangle2D titleBounds) {
		Point2D point = new Point2D.Double((rect.getWidth() / 2.0 + rect.x)
				- (titleBounds.getWidth() / 2.0 + titleBounds.getX()), rect.y
				- titleOffset.getY());

		if (point.getX() + titleBounds.getX() + titleBounds.getWidth() > dimension.getWidth()) {
			point.setLocation(dimension.getWidth() - (titleBounds.getX() + titleBounds.getWidth()), point.getY());
		}

		if (point.getX() < titleOffset.getX())
			point.setLocation(titleOffset.getX(), point.getY());

		return point;
	}

	private void setIntVector(Collection<Integer> dest, Collection<Integer> source) {
		dest.clear();
		dest.addAll(source);
	}

	private void setContigListVector(Collection<Integer> contigNumbers) {
		if (defaultContigList.isEmpty()) setDefaultContigList(contigNumbers);
		else if (!equalNumbers(contigList, contigNumbers)) {
			setIntVector(contigList, contigNumbers);
			clearInit();
			init();
		}
	}

	private void setDefaultContigList(Collection<Integer> contigNumbers) {
		setIntVector(defaultContigList, contigNumbers);
		if (!equalNumbers(contigList, defaultContigList)) {
			setIntVector(contigList, contigNumbers);
			clearInit();
			init();
		}
	}

	private boolean equalNumbers(Collection<Integer> v1, Collection<Integer> v2) {
		if (v1.size() != v2.size()) return false;
		Iterator<Integer> i1 = v1.iterator(), i2 = v2.iterator();
		while (i1.hasNext())
			if (!i1.next().equals(i2.next())) 
				return false;
		return true;
	}

	private double getPixel(double bp) {
		bp -= start.getBPValue();
		bp /= bpPerPixel;
		bp += rect.getY();
		return bp;
	}

	protected class InnerBlock {
		private int contig;
		private GenomicsNumber size;
		private Vector<Marker> markers;
		private TextLayout tl;
		private Rule rule;
		private Color color;
		private Rectangle2D.Double rect;
		private boolean show;
		private String groupList; 

		public InnerBlock(int contig, GenomicsNumber size, String groupList) {
			this.contig = contig;
			this.size = size;
			rule = new Rule(contigColor, contigFont);
			rect = new Rectangle2D.Double();
			markers = new Vector<Marker>();
			show = true;
			this.groupList = groupList; 
		}

		public boolean isInsideOf(double pixel) {
			return rect.y <= pixel;
		}

		public boolean isOutsideOf(double pixel) {
			return rect.y+rect.height >= pixel;
		}

		public String toString() {
			return "[InnerBlock (contig: " + contig + ") (Visible: " + show + ")]";
		}

		public boolean contains(Point point) {
			return rect.contains((double) point.x, (double) point.y);
		}

		protected double getYPoint(long num) {
			return ((num / (double)size.getValue()) * rect.getHeight()) + rect.getY();
		}

		private double getAdjustedYPoint(long num) {
			if (flipped) num = size.getValue() - num;
			return ((num / (double)size.getValue()) * rect.getHeight()) + rect.getY();
		}

		public Point2D getPoint(long num, int orient) {
			// pre: this guy is all set up
			double x = rect.x;
			if (orient == LEFT_ORIENT) x += rect.getWidth();

			return new Point2D.Double(x, getAdjustedYPoint(num));
		}

		protected void addMarkersToMarkerList(MarkerList ml) {
			for (Marker m : markers)
				ml.add(m);
		}

		protected void reset(boolean flip, boolean visible) {
			long end = size.getValue();
			for (Marker marker : markers) {
				if (flip) marker.flip(end);
				if (visible != show) marker.setFiltered(Marker.HIDDEN_FILTERED,!visible);
			}
			show = visible;
		}

		public void flip() {
			long end = size.getValue();
			for (Marker m : markers)
				m.flip(end);
		}

		public int getContig() {
			return contig;
		}
				
		public String getGroupList() { 
			return groupList;
		}

		public Marker getMarker(String name) {
			for (Marker marker : markers)
				if (marker.getName().equals(name)) return marker;
			return null;
		}

		public void addMarker(Marker marker) {
			markers.add(marker);
		}

		public Vector<Marker> getMarkers() {
			return markers;
		}
		
		public long getLength() {
			return size.getValue();
		}

		public double getPercent(long sum) {
			return size.getValue()/(double)sum;
		}

		public boolean setVisible(boolean show) {
			if (this.show == show) return false;
			this.show = show;
			for (Marker marker : markers)
				marker.setFiltered(Marker.HIDDEN_FILTERED,!show);
			return true;
		}

		public boolean isVisible() {
			return show;
		}

		public void setColor(Color c) {
			color = c;
		}

		public void setRectangle(double x, double y, double width, double height) {
			rect.setRect(x, y, width, height);
		}

		public double setTextLayout(FontRenderContext frc) {
			tl = new TextLayout(String.format("%d", contig), contigFont, frc); // CAS520 Integer
			Rectangle2D rec = tl.getBounds();
			return rec.getX() + rec.getWidth();
		}

		public double setText(double py) {
			// pre: setRectangle and setTextLayout have been called
			double lx1, lx2;
			Rectangle2D rec = tl.getBounds();
			Rectangle2D.Double crect = new Rectangle2D.Double();
			crect.setRect(rect.x + (rect.width / 2.0), rect.y
					+ (rect.height / 2.0), rec.getWidth(), rec.getHeight());
			crect.x = crect.x - (rec.getX() + (rec.getWidth() / 2.0));
			crect.y = crect.y - (rec.getY() + (rec.getHeight() / 2.0));

			if (crect.x > rect.x
					&& crect.y > rect.y
					&& (rect.x + rect.width) > (crect.x + crect.width + rec.getX())
					&& (rect.y + rect.height) > (crect.y + crect.height + rec.getY()))
				rule.showLine(false);
			else {
				if (orient == LEFT_ORIENT) {
					lx1 = rect.x;
					lx2 = lx1 - contigNameOffset;
					crect.x = lx2 - crect.width - SMALL_OFFSET;
				} else {
					lx1 = rect.x + rect.width;
					lx2 = lx1 + contigNameOffset;
					crect.x = lx2 + SMALL_OFFSET;
				}
				rule.showLine(true);
				py += contigNameSpace;
				if (py > crect.y)
					crect.y = py;
				else
					py = crect.y;
				py += crect.height;
				rule.setLine(lx1, rect.y + (rect.height / 2.0), lx2, crect.y
						+ (rec.getY() + (rec.getHeight() / 2.0)));
			}
			rule.setText(tl, crect.x, crect.y);
			return py;
		}

		public void paintComponent(Graphics g) {
			if (isVisible()) {
				Graphics2D g2 = (Graphics2D) g;
				if (color != null)
					g2.setPaint(color);
				g2.fill(rect);
				g2.setPaint(border);
				g2.draw(rect);
				rule.paintComponent(g2);
			}
		}
	}

	public boolean isContigs(String contigList) {
		boolean success = false;
		try {
			success = pool.isContigs(getProject(),getContigs(contigList));
		} catch (Exception e) { }
		return success;
	}

	private String getContigsFromBlock(int p1, int p2, String b) {
		try {
			return pool.getContigs(p1,p2,b);
		} catch (Exception e) {
			try {
				return pool.getContigs(p1,p2,b);
			} catch (Exception e2) {
				e2.printStackTrace();
				System.err.println("Two attempts at getting the contigs for block "+b+" failed!");
			}
		}
		return "";
	}

	public static boolean validContigSet(String contigList) {
		boolean success = false;
		try {
			getContigs(contigList);
			success = true;
		} catch (Exception e) { }
		return success;
	}

	public static String getContigs(Collection<Integer> contigList) throws IllegalArgumentException {
		return Utilities.getIntsString(contigList);
	}

	public static Collection<Integer> getContigs(String input) throws IllegalArgumentException {
		return Utilities.getIntsSet(input);
	}
}
