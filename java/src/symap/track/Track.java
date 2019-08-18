package symap.track;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Stack;
import javax.swing.JComponent;
import number.BPNumber;
import number.GenomicsNumber;
import number.GenomicsNumberHolder;
import symap.SyMAPConstants;
import symap.frame.HelpListener;
import symap.pool.ProjectProperties;
import symap.drawingpanel.DrawingPanel;

/**
 * Class <code>Track</code> contains the base track information.
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 */
public abstract class Track implements GenomicsNumberHolder, HelpListener,
		SyMAPConstants, KeyListener,
		MouseListener, MouseMotionListener, MouseWheelListener
{	
	public static final int MAX_PIXEL_HEIGHT = 10000;
	public static final int MAX_PIXEL_WIDTH = 1000;

	protected static final double MOUSE_PADDING = 2;

// mdb removed 1/28/09 #159 simplify properties	
//	private static Color titleColor;
//	private static Color dragColor;
//	private static Color dragBorder;
//	private static Font titleFont;
//	static {
//		PropertiesReader props = new PropertiesReader(SyMAP.class.getResource("/properties/track.properties"));
//		MAX_PIXEL_HEIGHT = props.getInt("maxPixelHeight");
//		MAX_PIXEL_WIDTH  = props.getInt("maxPixelWidth");
//		titleColor = props.getColor("titleColor");
//		titleFont = props.getFont("titleFont");
//		dragColor = props.getColor("dragColor");
//		dragBorder = props.getColor("dragBorder");
//	}
	
	// mdb added 1/28/09 #159 simplify properties
	private static final Color titleColor = Color.black;
	private static final Color dragColor = new Color(255,255,255,120);
	private static final Color dragBorder = Color.black;
	private static final Font titleFont = new Font("Ariel", 1, 14);

	protected Color bgColor = new Color(247,233,213); // mdb added 6/9/09
	
	private double startResizeBpPerPixel;
	private Point startMoveOffset, adjustMoveOffset;
	protected Rectangle dragRect;
	protected Point dragPoint;

	private TextLayout titleLayout;
	private Point2D.Float titlePoint;
	private Point2D defaultTrackOffset;
	private String projectName, projectType, displayName;
	private String otherProjectType; // mdb added 4/10/08

	protected int project, otherProject;
	protected int orient;
	protected Point moveOffset;
	protected double defaultBpPerPixel;
	protected double bpPerPixel;
	protected GenomicsNumber start, end, size;
	protected double height, width;

	protected boolean hasInit, hasBuild;
	protected double minDefaultBpPerPixel, maxDefaultBpPerPixel;
	protected int bpPerCb;
	protected Dimension dimension;
	protected Point trackOffset;
	protected Rectangle2D.Double rect;
	protected boolean firstBuild = true;
	
	protected boolean flipped; // mdb added 8/6/07 #132
	
	protected int position; // mdb added 3/18/08 - mapper position

	protected TrackHolder holder;
	protected DrawingPanel drawingPanel;
	
	protected long curCenter=0;
	protected long curWidth=0; // for +/- buttons

	protected Track(DrawingPanel dp, TrackHolder holder, double minBpPerPixel, 
			double maxBpPerPixel, Point2D defaultTrackOffset) 
	{
		this.drawingPanel = dp;
		this.holder = holder;
		otherProject = project = NO_VALUE;
		titlePoint = new Point2D.Float();
		rect = new Rectangle2D.Double();
		trackOffset = new Point();
		startMoveOffset = new Point();
		adjustMoveOffset = new Point();
		moveOffset = new Point();
		dragRect = new Rectangle();
		dragPoint = new Point();
		dimension = new Dimension();
		orient = LEFT_ORIENT;
		start = new GenomicsNumber(this,0);
		end = new GenomicsNumber(this,0);
		size = new GenomicsNumber(this,0);
		this.minDefaultBpPerPixel = minBpPerPixel;
		this.maxDefaultBpPerPixel = maxBpPerPixel;
		this.defaultTrackOffset = defaultTrackOffset;
		if (holder.getTrack() != null) this.bgColor = holder.getTrack().bgColor; // mdb added 7/16/09 - maintain color when "back to block view" selected
		if (holder.getTrack() != null) this.position = holder.getTrack().position; // mdb added 7/16/09 - maintain position when "back to block view" selected
	}

	public TrackHolder getHolder() {
		return holder;
	}

	public void setHeld() { }

	protected void reset(int project, int otherProject) {
		clear();
		firstBuild = true;
		if (this.project != project || this.otherProject != otherProject) {
			this.project = project;
			this.otherProject = otherProject;

			ProjectProperties pp = drawingPanel.getPools().getProjectProperties();
			displayName = pp.getDisplayName(project);
			projectName = pp.getName(project);
			projectType = pp.getType(project);
			otherProjectType = pp.getType(otherProject); // mdb added 4/10/08
			bpPerCb     = pp.getIntProperty(project,"cbsize",1);

			if (bpPerCb == 1) {
				if (start instanceof BPNumber) {
					start.setValue(0);
					end.setValue(0);
					size.setValue(0);
				}
				else {
					start = new BPNumber(this,0);
					end = new BPNumber(this,0);
					size = new BPNumber(this,0);
				}
			}
			else {
				if (start instanceof BPNumber) {
					start = new GenomicsNumber(this,0);
					end = new GenomicsNumber(this,0);
					size = new GenomicsNumber(this,0);
				}
				else {
					start.setValue(0);
					end.setValue(0);
					size.setValue(0);
				}
			}
		}
	}

	protected void reset() {
		firstBuild = true;

		rect.setRect(0,0,0,0);
		trackOffset.setLocation((int)defaultTrackOffset.getX(),(int)defaultTrackOffset.getY());
		clear(startMoveOffset);
		clear(adjustMoveOffset);
		moveOffset.setLocation(0,0);
		startResizeBpPerPixel = NO_VALUE;
		clear(dragRect);
		dragPoint.setLocation(0,0);
		titlePoint.setLocation(0,0);

		dimension.setSize(0,0);
		height = width = NO_VALUE;
		defaultBpPerPixel = bpPerPixel = minDefaultBpPerPixel;

		clearTrackBuild();
	}	

	public void clear() {
		rect.setRect(0,0,0,0);
		trackOffset.setLocation((int)defaultTrackOffset.getX(),(int)defaultTrackOffset.getY());
		clear(startMoveOffset);
		clear(adjustMoveOffset);
		moveOffset.setLocation(0,0);
		startResizeBpPerPixel = NO_VALUE;
		clear(dragRect);
		dragPoint.setLocation(0,0);
		titlePoint.setLocation(0,0);

		dimension.setSize(0,0);
		height = width = NO_VALUE;
		defaultBpPerPixel = bpPerPixel = minDefaultBpPerPixel;
		clearInit();
	}

	public void resetData() {
		clearData();
		clearInit();
		init();
	}

	public abstract void clearData();

	/**
	 * Method <code>getMoveOffset</code> returns the move offset for the track to
	 * be used by the holder/layout manager.
	 *
	 * @return a <code>Point</code> value
	 */
	public Point getMoveOffset() {
		return moveOffset;
	}

	/**
	 * Method <code>getGraphics</code> returns the holders graphics object if 
	 * a holder exists, null otherwise.
	 *
	 * @return a <code>Graphics</code> value
	 * @see javax.swing.JComponent#getGraphics()
	 */
	public Graphics getGraphics() {
		if (holder != null) return holder.getGraphics();
		return null;
	}

	/**
	 * Method <code>getLocation</code> returns the location of the holder
	 * or a point at location 0,0 if holder is not set.
	 *
	 * @return a <code>Point</code> value
	 */
	public Point getLocation() {
		if (holder != null) return holder.getLocation();
		else return new Point();
	}

	/**
	 * Method <code>getMidX</code> returns the viewable middle 
	 * as determined by the track.
	 *
	 * @return an <code>double</code> value
	 */
	public double getMidX() {
		return rect.getX()+(rect.getWidth()/2.0);
	}

	public int getProject() {
		return project;
	}

	public int getOtherProject() {
		return otherProject;
	}
	
	// mdb added 4/10/08
	public String getProjectType() {
		return projectType;
	}
	
	// mdb added 4/10/08
	public String getOtherProjectType() {
		return otherProjectType;
	}

	public String getProjectName() {
		return projectName;
	}

	// mdb unused 7/6/07
	//public String getProjectType() {
	//	return projectType;
	//}

	public String getProjectDisplayName() {
		return displayName;
	}

	/**
	 * Method <code>getBpPerPixel</code>
	 *
	 * @return a <code>double</code> value of the bp/pixel of this track
	 */
	public double getBpPerPixel() {
		return bpPerPixel;
	}
	

	public int getBpPerCb() {
		return bpPerCb;
	}


	public boolean changeZoomFactor(double factor) {
		if (factor > 0) {
			setBpPerPixel( getBpPerPixel() * ( 1.0 / factor ) );
			return true;
		}
		return false;
	}
	// Implements the +/- buttons. 
	// It stores the current region width and center so that (-) can exactly undo (+) even
	// when the (+) expands beyond the bounds of one sequence.
	public void changeAlignRegion(double factor) 
	{
		long s = getStart();
		long e = getEnd();
		long mid = (s + e)/2;
		long width = (e - s)/2;
		boolean showingFull = (2*width > .95*getTrackSize());
		
		if (curCenter == 0)
		{
			curCenter = mid;
			curWidth = width;
		}
		
		curWidth *= factor;
		
		long sNew = Math.max(1,curCenter - curWidth);
		long eNew = Math.min(curCenter + curWidth,getTrackSize());
		
		setStartBP(sNew, false);
		setEndBP(eNew, false);
		
		if (showingFull && factor > 1) return; // don't scale if already at max
		
		double displayedWidth = (eNew - sNew)/2;
		double effectiveFactor = displayedWidth/width;
		setBpPerPixel( getBpPerPixel() * ( 1.0 / effectiveFactor ) );
		
	}
	public boolean fullyExpanded() 
	{
		long s = getStart();
		long e = getEnd();
		if (s > 1 || e < getTrackSize())
		{
			return false;
		}
		
		return true;
	}
	/**
	 * Sets the bp/pixel of the track, setting the track to need to be built on 
	 * the next make.  If bp is less than or equal to zero, nothing is done.
	 * 
	 * @param bp New bp/pixel
	 */
	public void setBpPerPixel(double bp) {
		if (bp > 0) {
			height = NO_VALUE;

			if (bp != bpPerPixel) {
				bpPerPixel = bp;
				clearTrackBuild();
			}
		}
	}


	/**
	 * Sets the height in pixels. Height can be used by the individual 
	 * implementations of the track during the build. If pixels is less than 
	 * or equal to 0, 1 is used. If pixels is greater than
	 * MAX_PIXEL_HEIGHT, than the MAX_PIXEL_HEIGHT is used.
	 * 
	 * @param pixels Height of the track in pixels on the next build
	 */
	public void setHeight(double pixels) {
		pixels = Math.min(Math.max(pixels,1),MAX_PIXEL_HEIGHT);
		if (height != pixels) {
			height = pixels;
			clearTrackBuild();
		}
	}

	/**
	 * Sets the width in pixels.  Width can be used by the individual 
	 * implementations of the track during the build. If pixels is less than 0, 
	 * 0 is used. If pixels is greater than
	 * MAX_PIXEL_WIDTH, than the MAX_PIXEL_WIDTH is used.
	 * 
	 * @param pixels Width of the track in pixels on the next build
	 */
	public void setWidth(double pixels) {
		pixels = Math.min(Math.max(pixels,0),MAX_PIXEL_WIDTH);
		if (width != pixels) {
			width = pixels;
			clearTrackBuild();
		}
	}

	/**
	 * Sets the height to not be considered on the next build.  Sets the track to
	 * need to be built on the next make if the height was set.
	 */
	public void clearHeight() {
		if (height != NO_VALUE) {
			height = NO_VALUE;
			clearTrackBuild();
		}
	}

	/**
	 * Sets the width to not be considered on the next build.  Sets the track 
	 * to need to be built on the next make if the width was set.
	 */
	public void clearWidth() {
		if (width != NO_VALUE) {
			width = NO_VALUE;
			clearTrackBuild();
		}
	}

	/**
	 * Sets the orientation of the track, settting the track to need to be 
	 * built on the next make on a change.
	 * 
	 * Some track implementations may only accept RIGHT_ORIENT and LEFT_ORIENT.
	 * 
	 * @param orientation The side at which the track is on in the map.  
	 * LEFT_ORIENT is used if this value is not defined
	 */
	protected void setOrientation(int orientation) {
		if (orientation != RIGHT_ORIENT && orientation != CENTER_ORIENT) orientation = LEFT_ORIENT;
		if (orientation != orient) {
			orient = orientation;
			clearTrackBuild();
		}
	}
	
	public int getOrientation() { return orient; }

	/**
	 * Resets the track's end to the size set during initialization, 
	 * setting the track to be built on the next build on a change.
	 */
	public void resetEnd() {
		if (end.getValue() != size.getValue()) {
			end.setValue(size.getValue());
			clearAllBuild();
		}
	}

	/**
	 * Resets the tracks start to 0, setting the track to be built on the next 
	 * build on a change. 
	 */
	public void resetStart() {
		if (start.getValue() != 0) {
			start.setValue(0);
			clearAllBuild();
		}
	}

	/**
	 * Sets the start of the track.
	 * 
	 * @param startValue start value in CB (BP for tracks where CB is not applicable).
	 * @throws IllegalArgumentException Thrown if startValue is less than 0 or 
	 * greater than the size set during initialization
	 */
	public void setStart(long startValue) throws IllegalArgumentException {
		if (startValue > size.getValue())
			throw new IllegalArgumentException("Start value "+startValue+" is greater than size "+size.getValue()+".");

		if (startValue < 0) throw new IllegalArgumentException("Start value is less than zero.");

		if (start.getValue() != startValue) {
			firstBuild = true;

			start.setValue(startValue);
			clearAllBuild();
		}
	}

	/**
	 * Sets the end of the track. If endBP is greater than the size, than the 
	 * end is set to the size.
	 * 
	 * @param endValue end point in base pair
	 * @return The end amount used (i.e. endBP or size if endBP > size)
	 * @throws IllegalArgumentException Thrown if endBP is less than 0.
	 */
	public long setEnd(long endValue) throws IllegalArgumentException {
		if (endValue < 0) throw new IllegalArgumentException("End value is less than zero.");

		if (endValue > size.getValue()) endValue = size.getValue();

		if (end.getValue() != endValue) {
			firstBuild = true;

			end.setValue(endValue);
			clearAllBuild();
		}

		return endValue;
	}

	/**
	 * Sets the start of the track
	 * 
	 * @param startBP start point in base pair
	 * @throws IllegalArgumentException Thrown if startBP is less than 0 or 
	 * greater than the size set during initialization
	 */
	public void setStartBP(long startBP, boolean resetPlusMinus) throws IllegalArgumentException {
		if (startBP > size.getBPValue()) {
			throw new IllegalArgumentException("Start value "+startBP+" is greater than size "+size.getBPValue()+".");
		}

		if (startBP < 0) throw new IllegalArgumentException("Start value "+startBP+" is less than zero.");

		if (resetPlusMinus) curCenter = 0;
		if (start.getBPValue() != startBP) {
			firstBuild = true;
			start.setBPValue(startBP);
			clearAllBuild();
		}
	}

	/**
	 * Sets the end of the track. If endBP is greater than the size, than 
	 * the end is set to the size.
	 * 
	 * @param endBP end point in base pair
	 * @return The end amount used (i.e. endBP or size if endBP > size)
	 * @throws IllegalArgumentException Thrown if endBP is less than 0.
	 */
	public long setEndBP(long endBP, boolean resetPlusMinus) throws IllegalArgumentException {
		if (endBP < 0) throw new IllegalArgumentException("End value is less than zero.");

		if (endBP > size.getBPValue()) endBP = size.getBPValue();

		if (resetPlusMinus) curCenter = 0;
		if (end.getBPValue() != endBP) {
			firstBuild = true;
			end.setBPValue(endBP);
			clearAllBuild();
		}

		return endBP;
	}

	/**
	 * Method <code>isInRange</code> returns true if the value given
	 * is between the start and end values inclusively.
	 *
	 * @param num a <code>long</code> value
	 * @return a <code>boolean</code> value
	 */
	public boolean isInRange(long num) {
		return num >= start.getValue() && num <= end.getValue();
	}

	/**
	 * Method <code>getStart</code> returns the CB (BP if CB not applicable) 
	 * value of the start of the Track.
	 *
	 * @return a <code>long</code> value
	 */
	public long getStart() {
		return start.getValue();
	}

	/**
	 * Method <code>getEnd</code> returns the CB (BP if CB not applicable) 
	 * value of the end of the Track.
	 *
	 * @return a <code>long</code> value
	 */
	public long getEnd() {
		return end.getValue();
	}

	/**
	 * Method <code>getTrackSize</code> returns the CB (BP if CB not applicable) 
	 * value of the size of the Track.
	 *
	 * @return a <code>long</code> value
	 */
	public long getTrackSize() {
		return size.getValue();
	}

	/**
	 * Method <code>getDimension</code> returns the dimension of the track 
	 * which corresponds to the preferred size post build.
	 *
	 * @return a <code>Dimension</code> value
	 */
	public Dimension getDimension() {
		return dimension; 
	}

	/**
	 * Method <code>getValue</code> is a convenience method for getting
	 * the value of a CB number (equivalent to BP when CB is not applicable)
	 * using this tracks current settings.
	 *
	 * Equivalent to GenomicsNumber.getValue(units,track.getBpPerCB(),cb,track.getBpPerPixel());
	 *
	 * @param cb a <code>long</code> value
	 * @param units a <code>String</code> value
	 * @return a <code>double</code> value
	 */
	public double getValue(long cb, String units) {
		return GenomicsNumber.getValue(units,bpPerCb,cb,bpPerPixel);
	}

	/**
	 * Paints the Track title.  This method should be overloaded by subclasses 
	 * which still need to call super.paintComponent().
	 */
	public void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D)g;

		// mdb added 1/28/09 - highlight reference track title in red
		if (position % 2 == 0)
			g2.setPaint(Color.red);
		else
			g2.setPaint(titleColor);
		
		if (titleLayout != null) titleLayout.draw(g2, titlePoint.x, titlePoint.y);

		if (!isCleared(dragRect)) {
			g2.setPaint(dragColor);
			g2.fill(dragRect);
			g2.setPaint(dragBorder);
			g2.draw(dragRect);
		}	
	}

	/**
	 * Sets the track to be built on the next make
	 */
	protected void clearAllBuild() {
		if (drawingPanel != null)
			drawingPanel.clearTrackBuild();
		else clearTrackBuild();
	}

	public void clearTrackBuild() {
		hasBuild = false;
	}

	public boolean hasInit() {
		return hasInit;
	}

	public boolean hasBuilt() {
		return hasBuild;
	}

	protected void setBuild() {
		hasBuild = true;
		firstBuild = false;
	}

	protected void clearInit() {
		firstBuild = true;
		hasInit = false;
		clearAllBuild();
		//if (holder != null) holder.clearMapperInit();
	}

	protected void setInit() {
		hasInit = true;
	}
	
	private double getAvailPixels() {
		return (drawingPanel.getViewHeight() - (trackOffset.getY() * 2));
	}

	protected void setFullBpPerPixel() {
		if (firstBuild) {
			bpPerPixel = (end.getBPValue() - start.getBPValue()) / getAvailPixels();

			if (bpPerPixel < minDefaultBpPerPixel) bpPerPixel = minDefaultBpPerPixel;
			else if (bpPerPixel > maxDefaultBpPerPixel) bpPerPixel = maxDefaultBpPerPixel;
			defaultBpPerPixel = bpPerPixel;
		}
	}

	protected void notifyObserver(Object obj) {
		if (drawingPanel != null) drawingPanel.update(this, obj);
	}

	protected void notifyObserver() {
		if (drawingPanel != null) drawingPanel.smake();
	}

	protected void layout() {
		//if (drawingPanel != null) drawingPanel.validate();

		if (holder != null && holder.getParent() instanceof JComponent) {
			((JComponent)holder.getParent()).doLayout();
			((JComponent)holder.getParent()).repaint();
		}
		else if (drawingPanel != null) {
			drawingPanel.doLayout();
			drawingPanel.repaint();
		}

		//if (holder != null) ((JComponent)holder.getParent()).revalidate();
	}

	protected void adjustBpPerPixel(double bp) {
		if (bp > 0) bpPerPixel = bp;
	}

	protected Rectangle2D getTitleBounds() {
		return new TextLayout(getTitle(),titleFont,getFontRenderContext()).getBounds();
	}

	protected void setTitle() {
		titleLayout = new TextLayout(getTitle(),titleFont,getFontRenderContext());
		Rectangle2D bounds = titleLayout.getBounds();
		Point2D point = getTitlePoint(bounds);

		if (dimension.getWidth() < point.getX() + bounds.getX() + bounds.getWidth()) {
			dimension.setSize(point.getX() + bounds.getX() + bounds.getWidth(), dimension.getHeight());
		}
		titlePoint.setLocation((float) point.getX(), (float) point.getY());

		if (holder != null)
			holder.setPreferredSize(dimension);
	}

	protected boolean validSize() {
		double dif = end.getPixelValue() - start.getPixelValue();
		return (dif > 0 && dif <= MAX_PIXEL_HEIGHT);
	}

	protected void adjustSize() {
		double dif = end.getPixelValue() - start.getPixelValue();
		if (dif < 0) {
			long temp = end.getValue();
			end.setValue(start.getValue());
			start.setValue(temp);
			dif = end.getPixelValue() - start.getPixelValue();
		}	    
		if (dif > MAX_PIXEL_HEIGHT) {
			if (height == NO_VALUE) height = getAvailPixels();//height = MAX_PIXEL_HEIGHT; // mdb changed 8/27/09 for 3D
			/*else*/ bpPerPixel = (end.getBPValue()-start.getBPValue())/height; // mdb removed else 8/27/09 for 3D
			System.out.println("Adjusting track size");
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

		if (y != Math.round(y)) {
			y = Math.round(y);
		}

		if (y < start.getBPValue())
			y = start.getBPValue();
		else if (y > end.getBPValue())
			y = end.getBPValue();

		return (int)Math.round(y);
	}

	protected Cursor getCursor() {
		/*if (holder != null)*/ return /*holder*/drawingPanel.getCursor(); // mdb changed 6/24/09
		//else return null; // mdb removed 6/24/09
	}

	protected void setCursor(Cursor c) {
		//if (holder != null) { // mdb removed 6/24/09
			if (c == null)
				/*holder*/drawingPanel.setCursor(SyMAPConstants.DEFAULT_CURSOR); // mdb changed 6/24/09
			else
				/*holder*/drawingPanel.setCursor(c); // mdb changed 6/24/09
		//}
	}

	protected void repaint() {
		if (drawingPanel != null) drawingPanel.repaint();
		else if (holder != null) holder.repaint();
	}

	protected FontRenderContext getFontRenderContext() {
		if (holder != null && holder.getGraphics() != null)
			return ((Graphics2D)holder.getGraphics()).getFontRenderContext();
		return null;
	}

	public abstract double getPadding();
	public abstract void setOtherProject(int otherProject);
	public abstract void setup(TrackData track);
	public abstract TrackData getData();
	public abstract boolean build();
	protected abstract boolean init();
	protected abstract String getTitle();
	protected abstract Point2D getTitlePoint(Rectangle2D titleBounds);
	protected abstract boolean isSouthResizePoint(Point p);
	
	// mdb added 3/18/08
	public void setPosition(int position) { this.position = position; }
	public int getPosition() { return position; }
	
	// mdb added 6/9/09
	public void setBackground(Color c) {
		if (c != null)
			bgColor = c;
	}
	
	public void mouseDragged(MouseEvent e) { // mdb partially rewritten 6/26/09
		Cursor cursor = getCursor();
		if (cursor != null && cursor.getType() == Cursor.WAIT_CURSOR)
			return;
			
		Point p = e.getPoint();
		
		if (cursor.getType() == Cursor.S_RESIZE_CURSOR) {
			double height = p.getY() - rect.getY();
			if (height > 0 && height < MAX_PIXEL_HEIGHT) {
				if (startResizeBpPerPixel == NO_VALUE) startResizeBpPerPixel = bpPerPixel;
				setHeight(height);
				if (build()) layout();
			}
		} 
// mdb removed move feature 6/26/09		
//		else if (cursor.getType() == Cursor.MOVE_CURSOR) {
//			if (isCleared(startMoveOffset)) {
//				dragPoint.setLocation(p);
//				startMoveOffset.setLocation(moveOffset);
//				adjustMoveOffset.setLocation(moveOffset);
//			}
//			else {
//				Point op = new Point(moveOffset);
//
//				moveOffset.x = p.x - dragPoint.x + adjustMoveOffset.x;
//				if (moveOffset.x < 0) moveOffset.x = 0;
//				moveOffset.y = p.y - dragPoint.y + adjustMoveOffset.y;
//				if (moveOffset.y < 0) moveOffset.y = 0;
//
//				/* Adjust for the track moving */
//				adjustMoveOffset.x += (moveOffset.x-op.x);
//				adjustMoveOffset.y += (moveOffset.y-op.y);
//
//				layout();
//			}
//		}
		else if (drawingPanel.isMouseFunctionZoomSingle()
				|| drawingPanel.isMouseFunctionZoomAll()
				|| drawingPanel.isMouseFunctionCloseup())
		{ 
			if (isCleared(dragRect)) {
				dragPoint.setLocation(p);
				if (dragPoint.getX() < rect.x)
					dragPoint.setLocation(rect.x, dragPoint.getY());
				else if (dragPoint.getX() > rect.x + rect.width)
					dragPoint.setLocation(rect.x + rect.width, dragPoint.getY());
				if (dragPoint.getY() < rect.y)
					dragPoint.setLocation(dragPoint.getX(), rect.y);
				else if (dragPoint.getY() > rect.y + rect.height)
					dragPoint.setLocation(dragPoint.getX(), rect.y + rect.height);
				dragRect.setLocation(dragPoint);
				dragRect.setSize(0, 0);
			} else {
				if (p.getX() < dragPoint.x) {
					dragRect.width = dragPoint.x - p.x;
					dragRect.x = p.x;
				} else {
					dragRect.width = p.x - dragRect.x;
				}
				if (p.getY() < dragPoint.y) {
					dragRect.height = dragPoint.y - p.y;
					dragRect.y = p.y;
				} else {
					dragRect.height = p.y - dragRect.y;
				}
			}

			// match with rect
			dragRect.width = (int) rect.width;
			dragRect.x = (int) rect.x;
			if (dragRect.height > 0 && dragRect.getY() < rect.y) {
				dragRect.height = (int) (dragRect.height + dragRect.y - rect.y);
				dragRect.y = (int) rect.y;
			}
			if (dragRect.height > 0
					&& dragRect.getY() + dragRect.getHeight() > rect.y
					+ rect.height) 
			{
				dragRect.height = (int) (rect.height + rect.y - dragRect.getY());
			}
			if (dragRect.height < 0)
				dragRect.height = 0;
		}

		repaint();
	}

	public void mousePressed(MouseEvent e) { // mdb partially rewritten 6/26/09
		Cursor cursor = getCursor();
		Point point = e.getPoint();
		
		if (e.isPopupTrigger()) { // mdb added 3/12/07 #104
			holder.showPopupFilter(e);
		}
		else if (cursor.getType() == Cursor.S_RESIZE_CURSOR) { // Resize
			
		}
// mdb removed move feature 6/26/09		
//		else if (cursor.getType() == Cursor.MOVE_CURSOR) { // Move
//			//setCursor(MOVE_CURSOR);
//			if (isCleared(startMoveOffset)) {
//				dragPoint.setLocation(point);
//				startMoveOffset.setLocation(moveOffset);
//				adjustMoveOffset.setLocation(moveOffset);
//			}
//		}
		else { //(e.isControlDown()) { // Zoom
			setCursor(CROSSHAIR_CURSOR);
			if (isCleared(dragRect)) {
				dragPoint.setLocation(point);
				if (dragPoint.getX() < rect.x)
					dragPoint.setLocation(rect.x, dragPoint.getY());
				else if (dragPoint.getX() > rect.x + rect.width)
					dragPoint.setLocation(rect.x + rect.width, dragPoint.getY());
				if (dragPoint.getY() < rect.y)
					dragPoint.setLocation(dragPoint.getX(), rect.y);
				else if (dragPoint.getY() > rect.y + rect.height)
					dragPoint.setLocation(dragPoint.getX(), rect.y + rect.height);
				dragRect.setLocation(dragPoint);
				dragRect.setSize(0, 0);
			}
		}
	}

	public void mouseMoved(MouseEvent e) {
		Cursor c = getCursor();
		if (c == null || c.getType() != Cursor.WAIT_CURSOR) {
			Point p = e.getPoint();
			
			if (e.isControlDown()) 							// Zoom
				setCursor(CROSSHAIR_CURSOR);
// mdb removed move feature 6/26/09					
//			else if (rect.contains(p) && e.isShiftDown())	// Move
//				setCursor(MOVE_CURSOR);
			else if (isSouthResizePoint(p)) 				// Resize
				setCursor(S_RESIZE_CURSOR);
			else {
				// mdb added 6/24/09
				if (drawingPanel.isMouseFunctionCloseup()
						|| drawingPanel.isMouseFunctionZoomSingle()
						|| drawingPanel.isMouseFunctionZoomAll())
					setCursor(CROSSHAIR_CURSOR);
//				else if (drawingPanel.getMouseFunction().equals("Move"))
//					setCursor(MOVE_CURSOR);
				else {
					setCursor(null);
					clearMouseSettings();
				}
			}
		}
	}

	public void mouseReleased(MouseEvent e) {
		boolean needUpdate = false;
		
		if (e.isPopupTrigger()) { // mdb added 3/12/07 #104
			holder.showPopupFilter(e);
		}
		else {
			if (!isCleared(dragRect) 
					&& (drawingPanel.isMouseFunctionZoomSingle() 
							|| drawingPanel.isMouseFunctionZoomAll())) 
			{
				try {
					long newStart = getBP(dragRect.y);
					long newEnd   = getBP(dragRect.y+dragRect.height);
					if (newEnd != newStart) {
						if (flipped) { // mdb added 8/6/07 #132
							long temp = newStart;
							newStart = end.getBPValue() - newEnd + start.getBPValue();
							newEnd = end.getBPValue() - temp + start.getBPValue();
						}
						setEndBP(newEnd, true);
						setStartBP(newStart, true);
						
						// mdb added 4/23/09 #161
						if (drawingPanel.isMouseFunctionZoomAll())
							drawingPanel.zoomAllTracks(this, (int)newStart, (int)newEnd);
						
						if (!hasBuilt()) {
							clearHeight(); // want to resize to screen
							needUpdate = true;
							notifyObserver();
						}
					}
				} catch (Exception exc) {
					System.out.println("Exception resizing track!");
					exc.printStackTrace();
				}
			}
	
			if (needUpdate 
					|| (startResizeBpPerPixel != NO_VALUE && startResizeBpPerPixel != bpPerPixel) 
					|| (!isCleared(startMoveOffset) && !startMoveOffset.equals(moveOffset)))
				if (drawingPanel != null) drawingPanel.setImmediateUpdateHistory();
	
			clearMouseSettings();
		}
	}

	private void clearMouseSettings() {
		if (!isCleared(dragRect)) {
			clear(dragRect);
			repaint();
		}
		clear(startMoveOffset);
		clear(adjustMoveOffset);
		clear(dragPoint);
		startResizeBpPerPixel = NO_VALUE;
	}

	protected boolean isCleared(Point p) {
		return p.x == NO_VALUE && p.y == NO_VALUE;
	}

	protected boolean isCleared(Rectangle r) {
		return r.x == 0 && r.y == 0 && r.width == NO_VALUE && r.height == NO_VALUE;
	}

	protected void clear(Point p) {
		p.setLocation(NO_VALUE,NO_VALUE);
	}

	protected void clear(Rectangle r) {
		r.setRect(0,0,NO_VALUE,NO_VALUE);
	}

	public void mouseClicked(MouseEvent e) { }
	public void mouseEntered(MouseEvent e) { // mdb added 3/2/07 #106
		holder.requestFocusInWindow();
	}
	
	public void mouseExited(MouseEvent e) { 
		//drawingPanel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)); // mdb added 6/24/09
	}
	
	public void mouseWheelMoved(MouseWheelEvent e) { } // mdb added 3/1/07 #106
	public void mouseWheelMoved(MouseWheelEvent e, long viewSize) { } // mdb added 2/29/08 #124

    public void keyTyped(KeyEvent e) { } // mdb added 3/2/07 #106
    public void keyPressed(KeyEvent e) { } // mdb added 3/2/07 #106
    public void keyReleased(KeyEvent e) { } // mdb added 3/2/07 #106
	
	protected static String convertToRegex(String str) {
		if (str == null) str = new String();
		else str = str.trim();
		if (str.length() == 0)
			return ".*";
		return str.replaceAll("\\*", "(.*)");
	}

	protected static String addAsterics(String str) {
		if (str == null) str = new String();
		else str = str.trim();
		if (str.length() == 0)
			return ".*";
		return "(.*)".concat(str).concat("(.*)");
	}
}

