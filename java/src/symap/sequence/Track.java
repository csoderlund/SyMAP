package symap.sequence;

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
import javax.swing.JComponent;

import number.BPNumber;
import number.GenomicsNumber;
import number.GenomicsNumberHolder;
import props.ProjectPool;
import symap.Globals;
import symap.frame.HelpListener;
import symap.drawingpanel.DrawingPanel;
import util.ErrorReport;
import util.Utilities;

/**
 * Class Track contains the base track information; extended by Sequence. CAS534 remove SyMAPConstants
 * Note: this used to be shared by Sequence and FPC tracks, so there is probably residual FPC stuff here
 */
public abstract class Track implements GenomicsNumberHolder, HelpListener, KeyListener,
		MouseListener, MouseMotionListener, MouseWheelListener
{	
	private static final int MAX_PIXEL_HEIGHT = 10000;
	private static final int MAX_PIXEL_WIDTH = 1000;
	private static final Color REFNAME = new Color(0,0,200); // CAS530 change from red to deep blue
	private static final Color titleColor = Color.black;
	private static final Color dragColor = new Color(255,255,255,120);
	private static final Color dragBorder = Color.black;
	private static final Font titleFont = new Font("Arial", 1, 14);

	protected Rectangle2D.Double rect;
	protected Rectangle dragRect;
	protected Point dragPoint, trackOffset;

	protected int projIdx, otherProjIdx, orient;
	protected Point moveOffset;
	protected double defaultBpPerPixel, bpPerPixel;
	protected GenomicsNumber start, end, size;
	protected double height, width;
	protected int bpPerCb;
	protected Dimension dimension;
	
	protected boolean firstBuild = true, bFlipped; 
	protected int position; 

	private Point startMoveOffset, adjustMoveOffset;
	private Point2D.Float titlePoint;
	private Point2D defaultTrackOffset;
	
	private Color bgColor; 	
	private TextLayout titleLayout;
	private String projectName, displayName, otherProjName;

	private boolean hasInit, hasBuild;
	private double minDefaultBpPerPixel, maxDefaultBpPerPixel, startResizeBpPerPixel;
	private long curCenter=0, curWidth=0; // for +/- buttons
	
	protected DrawingPanel drawingPanel;
	private TrackHolder holder;
	private ProjectPool projPool;

	protected Track(DrawingPanel dp, TrackHolder holder, double minBpPerPixel, 
			double maxBpPerPixel, Point2D defaultTrackOffset) // Called by Sequence
	{
		this.drawingPanel = dp;
		this.holder = holder;
		this.minDefaultBpPerPixel = minBpPerPixel;
		this.maxDefaultBpPerPixel = maxBpPerPixel;
		this.defaultTrackOffset = defaultTrackOffset;
		
		if (holder.getTrack() != null) this.bgColor = holder.getTrack().bgColor; // maintain color when "back to block view" selected
		if (holder.getTrack() != null) this.position = holder.getTrack().position; // maintain position when "back to block view" selected
		
		projPool = dp.getProjPool();
		
		otherProjIdx 		= projIdx = Globals.NO_VALUE;
		titlePoint 			= new Point2D.Float();
		rect 				= new Rectangle2D.Double();
		trackOffset 		= new Point();
		startMoveOffset = new Point();
		adjustMoveOffset = new Point();
		moveOffset = new Point();
		dragRect = new Rectangle();
		dragPoint = new Point();
		dimension = new Dimension();
		orient = Globals.LEFT_ORIENT;
		start = new GenomicsNumber(this,0);
		end = new GenomicsNumber(this,0);
		size = new GenomicsNumber(this,0);
	}

	public TrackHolder getHolder() { return holder;}// Called by Sequence and DrawingPanel

	public void setHeld() { } // TrackHolder

	protected void reset(int project, int otherProject) { // Sequence
		clear();
		firstBuild = true;
		if (this.projIdx != project || this.otherProjIdx != otherProject) {
			this.projIdx = project;
			this.otherProjIdx = otherProject;

			displayName = projPool.getDisplayName(project);
			projectName = projPool.getName(project);
			if (Utilities.isEmpty(displayName)) displayName=projectName; // CAS534 need for new projects
			
			otherProjName = projPool.getDisplayName(otherProject);
			if (Utilities.isEmpty(otherProjName)) otherProjName=projPool.getName(otherProject);
			
			bpPerCb     = projPool.getIntProperty(project,"cbsize",1); // always 1 now

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

	protected void reset() { // Sequence
		firstBuild = true;

		rect.setRect(0,0,0,0);
		trackOffset.setLocation((int)defaultTrackOffset.getX(),(int)defaultTrackOffset.getY());
		clear(startMoveOffset);
		clear(adjustMoveOffset);
		moveOffset.setLocation(0,0);
		startResizeBpPerPixel = Globals.NO_VALUE;
		clear(dragRect);
		dragPoint.setLocation(0,0);
		titlePoint.setLocation(0,0);

		dimension.setSize(0,0);
		height = width = Globals.NO_VALUE;
		defaultBpPerPixel = bpPerPixel = minDefaultBpPerPixel;

		clearTrackBuild();
	}	

	public void clear() { // Sequence, Track, TrackHolder
		rect.setRect(0,0,0,0);
		trackOffset.setLocation((int)defaultTrackOffset.getX(),(int)defaultTrackOffset.getY());
		clear(startMoveOffset);
		clear(adjustMoveOffset);
		moveOffset.setLocation(0,0);
		startResizeBpPerPixel = Globals.NO_VALUE;
		clear(dragRect);
		dragPoint.setLocation(0,0);
		titlePoint.setLocation(0,0);

		dimension.setSize(0,0);
		height = width = Globals.NO_VALUE;
		defaultBpPerPixel = bpPerPixel = minDefaultBpPerPixel;
		clearInit();
	}

	public void resetData() { // drawingpanel
		clearData();
		clearInit();
		init();
	}

	public abstract void clearData(); // drawingpanel, track

	// getMoveOffset returns the move offset for the track to be used by the holder/layout manager.
	public Point getMoveOffset() {return moveOffset;} // trackholder

	/* getGraphics returns the holders graphics object if a holder exists, null otherwise.
	 * @see javax.swing.JComponent#getGraphics() */
	public Graphics getGraphics() {
		if (holder != null) return holder.getGraphics();
		return null;
	}

	// getLocation returns the location of the holder or a point at location 0,0 if holder is not set.*/
	public Point getLocation() {
		if (holder != null) return holder.getLocation();
		else 				return new Point();
	}

	/* getMidX returns the viewable middle as determined by the track.*/
	public double getMidX() {
		return rect.getX()+(rect.getWidth()/2.0);
	}

	public int getProject() {return projIdx;}
	
	public String getOtherProjectName() {return otherProjName;}

	public String getProjectDisplayName() {return displayName;}

	public double getBpPerPixel() { return bpPerPixel;}// GenomicsNumberHolder interface
	
	public int getBpPerCb() { return bpPerCb;}// GenomicsNumberHolder interface
	
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
	public void changeAlignRegion(double factor)  {
		long s = getStart();
		long e = getEnd();
		long mid = (s + e)/2;
		long width = (e - s)/2;
		boolean showingFull = (2*width > .95*getTrackSize());
		
		if (curCenter == 0) {
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
	public boolean fullyExpanded()  {
		long s = getStart();
		long e = getEnd();
		if (s > 1 || e < getTrackSize()) return false;	
		return true;
	}
	/* Sets the bp/pixel of the track, setting the track to need to be built on the next make
	 * Used with +/- buttons; drawToScale; */
	public void setBpPerPixel(double bp) {
		if (bp > 0) {
			height = Globals.NO_VALUE;

			if (bp != bpPerPixel) {
				bpPerPixel = bp;
				clearTrackBuild();
			}
		}
	}

	// Sets the height in pixels. Height can be used by the individual track during the build. 
	public void setHeight(double pixels) {
		pixels = Math.min(Math.max(pixels,1),MAX_PIXEL_HEIGHT);
		if (height != pixels) {
			height = pixels;
			clearTrackBuild();
		}
	}

	// Sets the width in pixels.  Width can be used by the individual implementations of the track during the build. 
	public void setWidth(double pixels) {
		pixels = Math.min(Math.max(pixels,0),MAX_PIXEL_WIDTH);
		if (width != pixels) {
			width = pixels;
			clearTrackBuild();
		}
	}
	// Sets the height to not be considered on the next build. 
	public void clearHeight() {
		if (height != Globals.NO_VALUE) {
			height = Globals.NO_VALUE;
			clearTrackBuild();
		}
	}
	// Sets the width to not be considered on the next build. 
	public void clearWidth() {
		if (width != Globals.NO_VALUE) {
			width = Globals.NO_VALUE;
			clearTrackBuild();
		}
	}
	// Some track implementations may only accept RIGHT_ORIENT and LEFT_ORIENT.
	protected void setOrientation(int orientation) {
		if (orientation != Globals.RIGHT_ORIENT && orientation != Globals.CENTER_ORIENT) orientation = Globals.LEFT_ORIENT;
		if (orientation != orient) {
			orient = orientation;
			clearTrackBuild();
		}
	}

	// Resets the track's end to the size set during initialization, setting the track to be built on the next build on a change.
	public void resetEnd() {
		if (end.getValue() != size.getValue()) {
			end.setValue(size.getValue());
			clearAllBuild();
		}
	}
	// Resets the tracks start to 0, setting the track to be built on the next build on a change. 
	public void resetStart() {
		if (start.getValue() != 0) {
			start.setValue(0);
			clearAllBuild();
		}
	}
	// Sets the start of the track - startValue start value in CB (BP for tracks where CB is not applicable).
	// CAS514 was throwing exception and stopping symap
	public void setStart(long startValue)  {
		if (startValue > size.getValue()) {
			Utilities.showWarningMessage("Start value "+startValue+" is greater than size "+size.getValue()+".");
			return;
		}
		if (startValue < 0) {
			Utilities.showWarningMessage("Start value is less than zero.");
			return;
		}
		if (start.getValue() != startValue) {
			firstBuild = true;

			start.setValue(startValue);
			clearAllBuild();
		}
	}

	// Sets the end of the track. endValue end point in base pair; CAS514 was throwing exception and stopping symap
	public long setEnd(long endValue) {
		if (endValue < 0) {
			Utilities.showWarningMessage("End value is less than zero.");
			return size.getValue();
		}
		if (endValue > size.getValue()) endValue = size.getValue();

		if (end.getValue() != endValue) {
			firstBuild = true;

			end.setValue(endValue);
			clearAllBuild();
		}
		return endValue;
	}

	// Sets the start of the track. startBP start point in base pair
	public void setStartBP(long startBP, boolean resetPlusMinus)  {
		if (startBP > size.getBPValue()) {
			Utilities.showWarningMessage("Start value "+startBP+" is greater than size "+size.getBPValue()+".");
			return;
		}
		if (startBP < 0) {
			Utilities.showWarningMessage("Start value "+startBP+" is less than zero.");
			return;
		}

		if (resetPlusMinus) curCenter = 0;
		if (start.getBPValue() != startBP) {
			firstBuild = true;
			start.setBPValue(startBP);
			clearAllBuild();
		}
	}

	//  Sets the end of the track. endBP end point in base pair;@return The end amount used (i.e. endBP or size if endBP > size)
	public long setEndBP(long endBP, boolean resetPlusMinus)  {
		if (endBP < 0) Utilities.showWarningMessage("End value is less than zero.");

		if (endBP > size.getBPValue() || endBP<0) endBP = size.getBPValue();

		if (resetPlusMinus) curCenter = 0;
		if (end.getBPValue() != endBP) {
			firstBuild = true;
			end.setBPValue(endBP);
			clearAllBuild();
		}

		return endBP;
	}

	public boolean isInRange(long num) {
		return num >= start.getValue() && num <= end.getValue();
	}

	// following 3 returns the CB (BP if CB not applicable) value of the start of the Track.
	public long getStart() {return start.getValue();}

	public long getEnd() {return end.getValue(); }

	public long getTrackSize() {return size.getValue();}

	// returns the dimension of the track  which corresponds to the preferred size post build.
	public Dimension getDimension() {return dimension; }

	/**
	 * getValue is a convenience method for getting the value of a CB number (equivalent to BP when CB is not applicable)
	 * using this tracks current settings.
	 * Equivalent to GenomicsNumber.getValue(units,track.getBpPerCB(),cb,track.getBpPerPixel())
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

		if (position % 2 == 0)
			g2.setPaint(REFNAME);
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

	// Sets the track to be built on the next make
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

			if (bpPerPixel < minDefaultBpPerPixel) 		bpPerPixel = minDefaultBpPerPixel;
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
		if (holder != null && holder.getParent() instanceof JComponent) {
			((JComponent)holder.getParent()).doLayout();
			((JComponent)holder.getParent()).repaint();
		}
		else if (drawingPanel != null) {
			drawingPanel.doLayout();
			drawingPanel.repaint();
		}
	}
	protected void adjustBpPerPixel(double bp) {
		if (bp > 0) bpPerPixel = bp;
	}
	protected Rectangle2D getTitleBounds() {
		return new TextLayout(getTitle(),titleFont,getFontRenderContext()).getBounds();
	}
	protected void setTitle() {
		String title = getTitle(); // CAS512 returned null, but not repeated 
		if (title==null) {
			title="bug";
			System.err.println("no title");
		}
		titleLayout = new TextLayout(title,titleFont,getFontRenderContext());
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
			if (height == Globals.NO_VALUE) height = getAvailPixels();
			bpPerPixel = (end.getBPValue()-start.getBPValue())/height; 
		}
	}
	protected int getBP(double y) {
		y -= rect.getY();
		if (y < 0)						y = 0;
		else if (y > rect.getHeight())	y = rect.getHeight();
		y *= bpPerPixel;
		y += start.getBPValue();

		if (y != Math.round(y)) 		y = Math.round(y);

		if (y < start.getBPValue())		y = start.getBPValue();
		else if (y > end.getBPValue())	y = end.getBPValue();

		return (int)Math.round(y);
	}
	protected Cursor getCursor() {
		return drawingPanel.getCursor(); 
		
	}
	protected void setCursor(Cursor c) {
		if (c == null) 	drawingPanel.setCursor(Globals.DEFAULT_CURSOR); 
		else			drawingPanel.setCursor(c); 
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

	public abstract void setOtherProject(int otherProject);
	public abstract void setup(TrackData track);
	public abstract TrackData getData();
	public abstract boolean build();
	protected abstract boolean init();
	protected abstract String getTitle();
	protected abstract Point2D getTitlePoint(Rectangle2D titleBounds);
	protected abstract boolean isSouthResizePoint(Point p);
	
	public void setPosition(int position) { this.position = position; }
	public int getPosition() { return position; }
	
	public void setBackground(Color c) {
		bgColor = c;
	}
	public void mouseDragged(MouseEvent e) { 
		Cursor cursor = getCursor();
		if (cursor != null && cursor.getType() == Cursor.WAIT_CURSOR)
			return;
			
		Point p = e.getPoint();
		
		if (cursor.getType() == Cursor.S_RESIZE_CURSOR) {
			double height = p.getY() - rect.getY();
			if (height > 0 && height < MAX_PIXEL_HEIGHT) {
				if (startResizeBpPerPixel == Globals.NO_VALUE) startResizeBpPerPixel = bpPerPixel;
				setHeight(height);
				if (build()) layout();
			}
		} 
		else if (drawingPanel.isMouseFunction()){ 
			if (isCleared(dragRect)) {
				dragPoint.setLocation(p);
				if (dragPoint.getX() < rect.x)					 dragPoint.setLocation(rect.x, dragPoint.getY());
				else if (dragPoint.getX() > rect.x + rect.width) dragPoint.setLocation(rect.x + rect.width, dragPoint.getY());
				if (dragPoint.getY() < rect.y)					 dragPoint.setLocation(dragPoint.getX(), rect.y);
				else if (dragPoint.getY() > rect.y + rect.height)dragPoint.setLocation(dragPoint.getX(), rect.y + rect.height);
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
			if (dragRect.height > 0 && dragRect.getY() + dragRect.getHeight() > rect.y + rect.height) 
				dragRect.height = (int) (rect.height + rect.y - dragRect.getY());
			
			if (dragRect.height < 0) dragRect.height = 0;
		}

		repaint();
	}

	public void mousePressed(MouseEvent e) { 
		Cursor cursor = getCursor();
		Point point = e.getPoint();
		
		if (e.isPopupTrigger()) { 
			holder.showPopupFilter(e);
		}
		else if (cursor.getType() == Cursor.S_RESIZE_CURSOR) { // Resize
		}
		else { //(e.isControlDown()) { // Zoom
			setCursor(Globals.CROSSHAIR_CURSOR);
			if (isCleared(dragRect)) {
				dragPoint.setLocation(point);
				if (dragPoint.getX() < rect.x)					 dragPoint.setLocation(rect.x, dragPoint.getY());
				else if (dragPoint.getX() > rect.x + rect.width) dragPoint.setLocation(rect.x + rect.width, dragPoint.getY());
				if (dragPoint.getY() < rect.y)					 dragPoint.setLocation(dragPoint.getX(), rect.y);
				else if (dragPoint.getY() > rect.y + rect.height)dragPoint.setLocation(dragPoint.getX(), rect.y + rect.height);
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
				setCursor(Globals.CROSSHAIR_CURSOR);
			else if (isSouthResizePoint(p)) 				// Resize
				setCursor(Globals.S_RESIZE_CURSOR);
			else {
				if (drawingPanel.isMouseFunction())
					setCursor(Globals.CROSSHAIR_CURSOR);
				else {
					setCursor(null);
					clearMouseSettings();
				}
			}
		}
	}
	public void mouseReleased(MouseEvent e) {
		boolean needUpdate = false;
		
		if (e.isPopupTrigger()) { 
			holder.showPopupFilter(e); // this never seems to happen
		}
		else { // sequence.mouseRelease is first, and checks for Align or Show popup; else, comes here for zoom
			if (!isCleared(dragRect) && drawingPanel.isMouseFunctionZoom()) {
				try {
					long newStart = getBP(dragRect.y);
					long newEnd   = getBP(dragRect.y+dragRect.height);
					if (newEnd != newStart) {
						if (bFlipped) { 
							long temp = newStart;
							newStart = end.getBPValue() - newEnd + start.getBPValue();
							newEnd = end.getBPValue() - temp + start.getBPValue();
						}
						setEndBP(newEnd, true);
						setStartBP(newStart, true);
						
						if (drawingPanel.isMouseFunctionZoomAll())
							drawingPanel.zoomAllTracks(this, (int)newStart, (int)newEnd);
						
						if (!hasBuilt()) {
							clearHeight(); // want to resize to screen
							needUpdate = true;
							notifyObserver();
						}
					}
				} catch (Exception ex) {ErrorReport.print(ex, "Exception resizing track!");}
			}
			if (needUpdate  || (startResizeBpPerPixel != Globals.NO_VALUE && startResizeBpPerPixel != bpPerPixel) 
							|| (!isCleared(startMoveOffset) && !startMoveOffset.equals(moveOffset))) {
				drawingPanel.setImmediateUpdateHistory();
				clearMouseSettings();
			}
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
		startResizeBpPerPixel = Globals.NO_VALUE;
	}
	protected boolean isCleared(Point p) {
		return p.x == Globals.NO_VALUE && p.y == Globals.NO_VALUE;
	}
	protected boolean isCleared(Rectangle r) {
		return r.x == 0 && r.y == 0 && r.width == Globals.NO_VALUE && r.height == Globals.NO_VALUE;
	}
	protected void clear(Point p) {
		p.setLocation(Globals.NO_VALUE,Globals.NO_VALUE);
	}
	protected void clear(Rectangle r) {
		r.setRect(0,0,Globals.NO_VALUE,Globals.NO_VALUE);
	}
	public void mouseClicked(MouseEvent e) { }
	public void mouseEntered(MouseEvent e) { 
		holder.requestFocusInWindow();
	}
	public void mouseExited(MouseEvent e) { 	}
	
	public void mouseWheelMoved(MouseWheelEvent e) { } 
	public void mouseWheelMoved(MouseWheelEvent e, long viewSize) { } 

    public void keyTyped(KeyEvent e) { } 
    public void keyPressed(KeyEvent e) { } 
    public void keyReleased(KeyEvent e) { } 
}
