package symap.sequence;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.sql.SQLException;
import java.util.List;
import java.util.Vector;

import number.GenomicsNumber;
import symap.SyMAP;
import symap.closeup.CloseUp;
import symap.closeup.SequencePool; // CAS504 has extraction of sequence
import symap.track.Track;
import symap.track.TrackData;
import symap.track.TrackHolder;
import symap.drawingpanel.DrawingPanel;

import util.PropertiesReader;
import util.Rule;
import util.TextBox;
import util.Utilities;
import util.ErrorReport;

/**
 * The Sequence track for a pseudo molecule. 
 */
public class Sequence extends Track {	
	private static final boolean TEST_POPUP_ANNO=true;
	private static final double OFFSET_SPACE = 7;
	private static final double OFFSET_SMALL = 1;
	
	private static final int MIN_BP_FOR_ANNOT_DESC = 500; 

	public static final boolean DEFAULT_FLIPPED 		= false; 
	public static final boolean DEFAULT_SHOW_RULER      = true;
	public static final boolean DEFAULT_SHOW_GENE       = true;  
	public static final boolean DEFAULT_SHOW_FRAME      = true;
	public static /*final*/ boolean DEFAULT_SHOW_ANNOT  = false; 
	public static final boolean DEFAULT_SHOW_SCORE_LINE	= true;  
	public static final boolean DEFAULT_SHOW_SCORE_VALUE= false; 
	public static final boolean DEFAULT_SHOW_RIBBON		= true;	 
	public static final boolean DEFAULT_SHOW_GAP        = true;
	public static final boolean DEFAULT_SHOW_CENTROMERE = true;
	public static final boolean DEFAULT_SHOW_GENE_FULL  = true;  

	public static Color unitColor;
	public static Color bgColor1; 
	public static Color bgColor2; 
	
	private static final Color border = Color.black; 
	private static Color footerColor;
	private static Font footerFont;
	private static Font unitFont;
	private static final Point2D defaultSequenceOffset = new Point2D.Double(20,40); 
	private static final Point2D titleOffset = new Point2D.Double(1,3); 
	private static int mouseWheelScrollFactor;	
	private static int mouseWheelZoomFactor; 	
	private static final double DEFAULT_WIDTH;
	private static final double SPACE_BETWEEN_RULES;
	private static final double RULER_LINE_LENGTH;
	private static final double MIN_DEFAULT_BP_PER_PIXEL;
	private static final double MAX_DEFAULT_BP_PER_PIXEL;
	private static final double PADDING = 100; 
	private static final double ANNOT_WIDTH;
	private static final String HOVER_MESSAGE = "Right-click for menu."; 
	static {
		PropertiesReader props = new PropertiesReader(SyMAP.class.getResource("/properties/sequence.properties"));
	
		unitColor                  = props.getColor("unitColor");
		bgColor1            		 = props.getColor("bgColor1");
		bgColor2            		 = props.getColor("bgColor2");
	
		footerColor                = props.getColor("footerColor");
		footerFont                 = props.getFont("footerFont");
		unitFont                   = props.getFont("unitFont");
		DEFAULT_WIDTH              = props.getDouble("defaultWidth");
		SPACE_BETWEEN_RULES        = props.getDouble("spaceBetweenRules");
		RULER_LINE_LENGTH          = props.getDouble("rulerLineLength");
		MIN_DEFAULT_BP_PER_PIXEL   = props.getDouble("minDefaultBpPerPixel");
		MAX_DEFAULT_BP_PER_PIXEL   = props.getDouble("maxDefaultBpPerPixel");

		ANNOT_WIDTH                = props.getDouble("annotWidth");
		
		mouseWheelScrollFactor	   = props.getInt("mouseWheelScrollFactor"); 	
		mouseWheelZoomFactor	   = props.getInt("mouseWheelZoomFactor"); 		
	}

	protected int group;
	protected boolean showRuler, showGene, showFrame, showAnnot;
	protected boolean showScoreLine, showScoreValue; 
	protected boolean showGap, showCentromere;
	protected boolean showFullGene;
	protected boolean showRibbon; 
	protected String name;
	
	private PseudoPool psePool;
	private SequencePool seqPool;
	private List<Rule> ruleList;
	private Vector<Annotation> annotations;
	private TextLayout headerLayout, footerLayout;
	private Point2D.Float headerPoint, footerPoint;
	
	public Sequence(DrawingPanel dp, TrackHolder holder) {
		this(dp, holder, dp.getPools().getPseudoPool(), dp.getPools().getSequencePool());
	}

	private Sequence(DrawingPanel dp, TrackHolder holder, PseudoPool psePool, SequencePool seqPool) {
		super(dp, holder, MIN_DEFAULT_BP_PER_PIXEL, MAX_DEFAULT_BP_PER_PIXEL, defaultSequenceOffset);
		this.psePool = psePool;
		this.seqPool = seqPool; // CAS504 added for sequence popup
		ruleList = new Vector<Rule>(15,2);
		annotations = new Vector<Annotation>(50,1000);
		headerPoint = new Point2D.Float();
		footerPoint = new Point2D.Float();

		group   = NO_VALUE;
		project = NO_VALUE;
	}
	
	public void setOtherProject(int otherProject) {
		if (otherProject != this.otherProject) setup(project,group,otherProject);
	}

	public void setup(int project, int group, int otherProject) {
		if (this.group != group || this.project != project || this.otherProject != otherProject) { 
			reset(project,otherProject);
			this.group = group;
			if (this.otherProject != NO_VALUE) init();
		}
		else reset();
		showRuler      = DEFAULT_SHOW_RULER;
		showGene       = DEFAULT_SHOW_GENE;
		showFrame      = DEFAULT_SHOW_FRAME;
		showAnnot      = DEFAULT_SHOW_ANNOT;
		showScoreLine  = DEFAULT_SHOW_SCORE_LINE;   
		showScoreValue = DEFAULT_SHOW_SCORE_VALUE; 	
		showRibbon     = DEFAULT_SHOW_RIBBON; 		
		showGap        = DEFAULT_SHOW_GAP;
		showCentromere = DEFAULT_SHOW_CENTROMERE;
		showFullGene   = DEFAULT_SHOW_GENE_FULL;
		flipped = false; 
	}

	public void setup(TrackData td) {
		SequenceTrackData sd = (SequenceTrackData)td;
		if (td.getProject() != project 	|| sd.getGroup() != group 
										|| td.getOtherProject() != otherProject)
			reset(td.getProject(),td.getOtherProject());
		
		sd.setTrack(this);
		firstBuild = false;
		init();
		sd.setTrack(this);
	}

	public TrackData getData() {
		return new SequenceTrackData(this);
	}

	/**
	 * Clears the internal lists and calls super.clear()
	 * @see Track#clear()
	 */
	public void clear() {
		ruleList.clear();
		annotations.clear();
		headerPoint.setLocation(0,0);
		footerPoint.setLocation(0,0);
		
		dragPoint.setLocation(0,0);
		
		super.clear();
	}

	public void clearData() {
		ruleList.clear();
		annotations.removeAllElements();
	}

	public String toString() {return "[Sequence "+getProjectName()+" "+getGroup()+"]";}

	public int getGroup() {return group;}

	public String getName() {
		return drawingPanel.getPools().getProjectProperties().getProperty(getProject(),"grp_prefix") +
			(name == null ? "" : name);
	}

	/**
	 * @see Track#getPadding()
	 */
	public double getPadding() {
		return PADDING;
	}
	
	public static void setDefaultShowAnnotation(boolean b) {
		DEFAULT_SHOW_ANNOT = b;
	}

	public boolean showRuler(boolean show) {
		if (showRuler != show) {
			showRuler = show;
			clearTrackBuild();
			return true;
		}
		return false;
	}

	public boolean showGap(boolean show) {
		if (showGap != show) {
			showGap = show;
			clearTrackBuild();
			return true;
		}
		return false;
	}

	public boolean showCentromere(boolean show) {
		if (showCentromere != show) {
			showCentromere = show;
			clearTrackBuild();
			return true;
		}
		return false;
	}

	public boolean showAnnotation(boolean show) {
		if (annotations.size() == 0) {
			showAnnot = false;
			return false;
		}
		if (showAnnot != show)  {
			showAnnot = show;
			clearTrackBuild();
			return true;
		}
		return false;
	}
	public boolean getShowAnnot() { return showAnnot;}
	
	public Vector<Annotation> getAnnotations() {return annotations;}
	
	public Vector<Annotation> getAnnotations(int type, int start, int end) {
		Vector<Annotation> out = new Vector<Annotation>();
		
		for (Annotation a : annotations) {
			if ((type == -1 || a.getType() == type) 
					&& Utilities.isOverlapping(start, end, a.getStart(), a.getEnd()))
				out.add(a);
		}	
		return out;
	}

	public int[] getAnnotationTypeCounts() {
		int[] counts = new int[Annotation.numTypes];
		
		for (Annotation a : annotations)
			counts[a.getType()]++;
		
		return counts;
	}
	public boolean showScoreLine(boolean show) { 
		if (showScoreLine != show) {
			showScoreLine = show;
			clearTrackBuild();
			return true;
		}
		return false;
	}
	
	public boolean getShowScoreLine() { return showScoreLine;}
	
	public boolean showScoreValue(boolean show) { 
		if (showScoreValue != show) {
			showScoreValue = show;
			clearTrackBuild();
			return true;
		}
		return false;
	}
	
	public boolean getShowScoreValue() { 
		return showScoreValue;
	}
	
	public boolean showRibbon(boolean show) { // hit ribbon
		if (showRibbon != show) {
			showRibbon = show;
			clearTrackBuild();
			return true;
		}
		return false;
	}
	
	public boolean getShowRibbon() { 
		return showRibbon;
	}

	public boolean showFrame(boolean show) {
		if (showFrame != show) {
			showFrame = show;
			clearTrackBuild();
			return true;
		}
		return false;
	}

	public boolean showGene(boolean show) {
		if (showGene != show) {
			showGene = show;
			clearTrackBuild();
			return true;
		}
		return false;
	}

	public boolean showFullGene(boolean show) {
		if (showFullGene != show) {
			showFullGene = show;
			clearTrackBuild();
			return true;
		}
		return false;
	}
	
	public boolean flip(boolean flip) {
		if (flipped != flip) {
			flipped = flip;
			clearTrackBuild();
			return true;
		}
		return false;
	}
	
	protected boolean init() {
		if (hasInit()) return true;
		if (project < 1 || group < 1 || otherProject < 1) return false;

		try {
			name = psePool.setSequence(this, size, annotations);	
			if (annotations.size() == 0) showAnnot = false;
		} catch (SQLException s1) {
			ErrorReport.print(s1, "Initializing Sequence failed.");
			psePool.close();
			return false;
		}
		
		resetStart();
		resetEnd();
		setInit();

		return true;
	}

	/**
	 * Method <code>build</code> sets up the drawing objects for this sequence.
	 */
	public boolean build() { 
		if (hasBuilt()) return true;
		if (!hasInit()) return false;
		
		if (annotations.size() == 0) showAnnot = false;
		
		if (width == NO_VALUE) width = DEFAULT_WIDTH;

		setFullBpPerPixel();

		TextLayout layout;
		FontRenderContext frc;
		Rectangle2D bounds;
		double x, y, h, x1, x2, tx, ty;
		Rectangle2D.Double totalRect = new Rectangle2D.Double();

		if (height > 0)
			adjustBpPerPixel(
					GenomicsNumber.getBpPerPixel(bpPerCb,
							Math.abs(end.getValue()-start.getValue()),height));

		if (!validSize()) {
			System.err.println("Unable to size sequence view as requested."); 				
			adjustSize();
		}

		GenomicsNumber length = new GenomicsNumber(this,end.getValue() - start.getValue());
		rect.setRect(trackOffset.getX(), trackOffset.getY(), width, length.getPixelValue());
		totalRect.setRect(0, 0, rect.x + rect.width + 2, rect.y + rect.height + 2);
		frc = getFontRenderContext();

		/*
		 * Setup the sequence ruler
		 */
		ruleList.clear();
		if (showRuler) {
			if (orient == LEFT_ORIENT) {
				x1 = rect.x - OFFSET_SMALL;
				x2 = x1 - RULER_LINE_LENGTH;
			} else if (orient == RIGHT_ORIENT) {
				x1 = rect.x + rect.width + OFFSET_SMALL;
				x2 = x1 + RULER_LINE_LENGTH;
			} else { // center
				x1 = rect.x + rect.width - OFFSET_SMALL;
				x2 = x1 - RULER_LINE_LENGTH;
			}
			h = rect.y + rect.height;
			double bpSep = SPACE_BETWEEN_RULES * bpPerPixel;
			double cb;
			
			for (y = rect.y; y < h + SPACE_BETWEEN_RULES; y += SPACE_BETWEEN_RULES) {	
				if (y < h && y > h - SPACE_BETWEEN_RULES) continue; 
				if (y > h) y = h; 
				Rule r = new Rule(unitColor, unitFont);
				r.setLine(x1, y, x2, y);
				
				cb = GenomicsNumber.getCbPerPixel(bpPerCb,bpPerPixel,(flipped ? h-y : y-rect.y)) + start.getValue(); 
				layout = new TextLayout(GenomicsNumber.getFormatted(bpSep,bpPerCb,cb,bpPerPixel), unitFont, frc);
				bounds = layout.getBounds();
				ty = y + (bounds.getHeight() / 2.0);
				totalRect.height = Math.max(totalRect.height, ty);
				totalRect.y = Math.min(totalRect.y, ty);
				if (orient == RIGHT_ORIENT) {
					tx = x2 + OFFSET_SMALL;
					totalRect.width = Math.max(totalRect.width, bounds.getWidth() + bounds.getX() + tx);
				} 
				else {
					tx = x2 - OFFSET_SMALL - bounds.getWidth() - bounds.getX();
					totalRect.x = Math.min(totalRect.x, tx);
				}
				r.setText(layout, tx, ty);
				ruleList.add(r);
			}
		}

		/* 
		 * Setup the annotations - 
		 */
		getHolder().removeAll(); 
		Rectangle2D.Double centRect = new Rectangle2D.Double(rect.x+1,rect.y,rect.width-2,rect.height);
		
		for (Annotation annot : annotations) {	
			if (((annot.isGene() || annot.isExon()) && !showGene)
				|| (annot.isGap() && !showGap)
				|| (annot.isCentromere() && !showCentromere)
				|| (annot.isFramework() && !showFrame))
			{
				annot.clear();
				continue;
			}
			
			double width = rect.width;
			
			if (annot.isGene()) {
				width = ANNOT_WIDTH;
				if (showFullGene) width /= 4; 
			}
			else if (annot.isExon() || annot.isSyGene()) 
				width = ANNOT_WIDTH;
			
			annot.setRectangle(centRect,
					start.getBPValue(), end.getBPValue(),
					bpPerPixel, width, flipped);
			
			if (showAnnot && annot.isVisible()) { // Setup description
				x1 = (orient == RIGHT_ORIENT ? rect.x + rect.width + RULER_LINE_LENGTH + 2 : rect.x);
				x2 = (orient == RIGHT_ORIENT ? x1 + RULER_LINE_LENGTH : x1 - RULER_LINE_LENGTH);
				if (annot.hasShortDescription() 
						&& getBpPerPixel() < MIN_BP_FOR_ANNOT_DESC) 
				{ 
					ty = annot.getY1();
					
					// Creates annotation description textbox (see TextBox.java)
					TextBox tb = new TextBox(annot.getVectorDescription(),unitFont,(int)x1,(int)ty,40,200);
					if (TEST_POPUP_ANNO) annot.setTextBox(tb); // CAS503
					getHolder().add(tb);
					bounds = tb.getBounds();
					
					totalRect.height = Math.max(totalRect.height, ty);
					totalRect.y = Math.min(totalRect.y, ty);
					if (orient == RIGHT_ORIENT) {
						tx = x2 + OFFSET_SMALL;
						totalRect.width = Math.max(totalRect.width,
								bounds.getWidth() + bounds.getX() + tx);
					}
					else {
						tx = x2-OFFSET_SMALL-bounds.getWidth()-bounds.getX();
						totalRect.x = Math.min(totalRect.x, tx);
					}
				}
			}
		} // end for(all annotations)
		
		// show instructions for annotation descriptions when zoomed out
		if (showAnnot && getBpPerPixel() >= MIN_BP_FOR_ANNOT_DESC) { 
			x1 = (orient == RIGHT_ORIENT ? rect.x + rect.width + RULER_LINE_LENGTH + 2 : rect.x);
			x2 = (orient == RIGHT_ORIENT ? x1 + RULER_LINE_LENGTH : x1 - RULER_LINE_LENGTH);
			ty = rect.y+rect.height/2;
			TextBox tb = new TextBox(
					"Annotation descriptions cannot be displayed\n"+
					"at this scale, zoom in further by dragging\n" +
					"the mouse over the sequence rectangle.\n",
					unitFont,(int)x1,(int)ty,200,1000);
			getHolder().add(tb);
			bounds = tb.getBounds();
			
			totalRect.height = Math.max(totalRect.height, ty);
			totalRect.y = Math.min(totalRect.y, ty);
			if (orient == RIGHT_ORIENT) {
				tx = x2 + OFFSET_SMALL;
				totalRect.width = Math.max(totalRect.width, bounds.getWidth() + bounds.getX() + tx);
			}
			else {
				tx = x2-OFFSET_SMALL-bounds.getWidth()-bounds.getX();
				totalRect.x = Math.min(totalRect.x, tx);
			}
		}
		
		/*
		 * Set header and footer
		 */
		bounds = new Rectangle2D.Double(0, 0, 0, 0); 							
		x = (rect.x + (rect.width / 2.0)) - (bounds.getX() + (bounds.getWidth() / 2.0));
		if (flipped) 
			y = rect.y + rect.height + OFFSET_SPACE + bounds.getHeight();
		else
			y = rect.y - OFFSET_SPACE;
		headerPoint.setLocation((float) x, (float) y);

		totalRect.width = Math.max(totalRect.width, x + bounds.getX() + bounds.getWidth());
		totalRect.x = Math.min(totalRect.x, x);
		totalRect.y = Math.min(totalRect.y, y - bounds.getHeight());

		footerLayout = new TextLayout(new GenomicsNumber(this,end.getValue()-start.getValue()+1).getFormatted()+" of "+size.getFormatted(), footerFont, frc); 
		bounds = footerLayout.getBounds();
		x = (rect.x + (rect.width / 2.0)) - (bounds.getX() + (bounds.getWidth() / 2.0));
		if (flipped) 
			y = rect.y - OFFSET_SPACE;
		else
			y = rect.y + rect.height + OFFSET_SPACE + bounds.getHeight();
		footerPoint.setLocation((float) x, (float) y);

		totalRect.width = Math.max(totalRect.width, x + bounds.getX() + bounds.getWidth());
		totalRect.x = Math.min(totalRect.x, x);
		totalRect.height = Math.max(totalRect.height, y);

		totalRect.width = totalRect.width - totalRect.x;
		totalRect.height = totalRect.height - totalRect.y;
		dimension.setSize(Math.ceil(totalRect.width) + defaultSequenceOffset.getX(), 
				Math.ceil(totalRect.height)+ defaultSequenceOffset.getY());
				
		/*
		 * Offset everything
		 */
		rect.x -= totalRect.x;
		rect.y -= totalRect.y;
		for (Rule rule : ruleList)
			rule.setOffset(totalRect.x, totalRect.y);
		for (Annotation annot : annotations)
			annot.setOffset(totalRect.x-1, totalRect.y);

		headerPoint.x -= totalRect.x;
		headerPoint.y -= totalRect.y;
		footerPoint.x -= totalRect.x;
		footerPoint.y -= totalRect.y;

		setTitle();
		setBuild();
		
		return true;
	}
	
	public Point2D getPoint(long bpPos, int orient) {
		if (bpPos < start.getValue()) bpPos = start.getValue(); 
		if (bpPos > end.getValue())   bpPos = end.getValue(); 	
		double x = (orient == LEFT_ORIENT ? rect.x+rect.width : rect.x);			
		double y = rect.y + GenomicsNumber.getPixelValue(bpPerCb,bpPos-start.getValue(),bpPerPixel);
		if (flipped) y = rect.y + rect.y + rect.height - y;	
		return new Point2D.Double(x, y); 
	}
	
	public double getX() { return rect.getX(); }
	
	public double getWidth() { return rect.getWidth(); }
	
	public void paintComponent(Graphics g) {
		
		if (hasBuilt()) {
			Graphics2D g2 = (Graphics2D) g;

			g2.setPaint((position % 2 == 0 ? bgColor1 : bgColor2)); 
			g2.fill(rect);
			g2.setPaint(border);
			g2.draw(rect);

			if (showRuler) {
				for (Rule rule : ruleList)
					rule.paintComponent(g2);
			}
			
			for (Annotation annot : annotations)
				if (annot.isVisible())
					annot.paintComponent(g2); 

			g2.setFont(footerFont);
			g2.setPaint(footerColor);

			if (headerLayout != null)
				headerLayout.draw(g2, headerPoint.x, headerPoint.y);
			if (footerLayout != null)
				footerLayout.draw(g2, footerPoint.x, footerPoint.y);
		}

		super.paintComponent(g); // must be done at the end
	}

	public void mousePressed(MouseEvent e) {
		// CAS503 add popUp description
		if (TEST_POPUP_ANNO && e.isPopupTrigger()  && showAnnot) {
			Point p = e.getPoint();
			for (Annotation annot : annotations) { 
				if (annot.popupDesc(p)) 
					return;
			}
		}
		super.mousePressed(e);
	}

	public void mouseReleased(MouseEvent e) { 
		Point p = e.getPoint();
		if (drawingPanel.isMouseFunctionPop() 
				&& dragRect.getHeight() > 0 
				&& rect.contains(p.getX(), p.getY()))
		{
			int start = getBP( dragRect.getY() );
			int end = getBP( dragRect.getY() + dragRect.getHeight() );
			
			if (flipped) {
				int temp = start;
				start = (int)getEnd() - end + (int)getStart();
				end = (int)getEnd() - temp + (int)getStart();
			}
			
			if (drawingPanel.isMouseFunctionCloseup()) openCloseup(start, end);
			else popupSequence(start, end); 
		}
		
		super.mouseReleased(e);
	}
	
	private void scrollRange(int notches, long viewSize) {
		long curViewSize = getEnd() - getStart() + 1;
		if (curViewSize < getTrackSize()) {
			long offset = notches*(viewSize/mouseWheelScrollFactor);
			long newStart, newEnd;
			
			newStart = getStart()+offset;
			if (newStart < 0) {
				newStart = 0;
				newEnd = viewSize;
			}
			else {
				newEnd = getEnd()+offset;
				if (newEnd > getTrackSize()) {
					newStart = getTrackSize() - viewSize;
					newEnd = getTrackSize();
				}
			}
			setStartBP(newStart,true);
			setEndBP(newEnd,true);	
			notifyObserver();	
		}
	}
	
	private void zoomRange(int notches, double focus, long length) {
		double r1 = (focus / rect.height) / mouseWheelZoomFactor;
		double r2 = ((rect.height - focus) / rect.height) / mouseWheelZoomFactor;
		if (flipped) {
			double temp = r1;
			r1 = r2;
			r2 = temp;
		}
		long newStart = start.getBPValue() + (long)(length*r1*notches);
		long newEnd   = end.getBPValue() - (long)(length*r2*notches);

		if (newEnd < 0) newEnd = 0; // unnecessary?
		else if (newEnd > getTrackSize()) newEnd = getTrackSize();
		if (newStart < 0) newStart = 0;
		else if (newStart > getTrackSize()) newStart = getTrackSize(); // unnecessary?
		if (newStart < newEnd) {
			setEndBP(newEnd,true);
			setStartBP(newStart,true);
			notifyObserver();
		}
	}
	
	public void mouseWheelMoved(MouseWheelEvent e) { 
		int notches = e.getWheelRotation();
		long viewSize = getEnd() - getStart() + 1;
		
		if (e.isControlDown()) // Zoom 
			zoomRange(notches, e.getPoint().getY() - rect.y + 1, viewSize);
		else
			scrollRange(notches, viewSize);
	}
	
	public void mouseWheelMoved(MouseWheelEvent e, long viewSize) { 
		int notches = e.getWheelRotation();
		
		if (e.isControlDown()) // Zoom - 
			zoomRange(notches, e.getPoint().getY() - rect.y + 1, viewSize);
		else
			scrollRange(notches, viewSize);
	}
	
	public void mouseDragged(MouseEvent e) {	
		super.mouseDragged(e);
		
		if (drawingPanel.isMouseFunctionCloseup() && getCursor().getType() != Cursor.S_RESIZE_CURSOR) 
		{
			Point point = e.getPoint();
			int happened = 0;
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
			else {
				if (point.getX() < dragPoint.x) {
					dragRect.width = dragPoint.x - point.x;
					dragRect.x = point.x;
				} 
				else
					dragRect.width = point.x - dragRect.x;

				if (point.getY() < dragPoint.y) {
					happened = 1;
					dragRect.height = dragPoint.y - point.y;
					dragRect.y = point.y;
				} 
				else {
					dragRect.height = point.y - dragRect.y;
					happened = 2;
				}
			}

			// match with rect (do we want this?)
			dragRect.width = (int) rect.width;
			dragRect.x = (int) rect.x;
			if (dragRect.height > 0 && dragRect.getY() < rect.y) {
				dragRect.height = (int) (dragRect.height + dragRect.y - rect.y);
				dragRect.y = (int) rect.y;
			}
			if (dragRect.height > 0 && dragRect.getY() + dragRect.getHeight() > rect.y + rect.height)
				dragRect.height = (int) (rect.height + rect.y - dragRect.getY());
			if (dragRect.height < 0)
				dragRect.height = 0;

			if (happened != 0) {
				int mh = (int)Math.round(CloseUp.MAX_CLOSEUP_BP / bpPerPixel);		
				if (happened == 1) {
					if (dragRect.height * bpPerPixel > CloseUp.MAX_CLOSEUP_BP) {
						dragRect.height = mh;
						if (dragRect.height+dragRect.y < dragPoint.y)
							dragPoint.y = dragRect.height+dragRect.y;
					}
				}
				else { 
					if (dragRect.height * bpPerPixel > CloseUp.MAX_CLOSEUP_BP) {
						dragRect.y += dragRect.height - mh;
						dragRect.height = mh;
						if (dragPoint.y < dragRect.y)
							dragPoint.y = dragRect.y;
					}
				}
			}
		}
	}

	/**
	 * Method <code>getHelpText</code> returns the desired text for when the mouse is
	 * over a certain point.
	 */
	public String getHelpText(MouseEvent event) {
		Point p = event.getPoint();
		if (rect.contains(p)) { // within blue rectangle of track
			for (Annotation annot : annotations) {
				if (annot.contains(p))
					return annot.getLongDescription();
			}
		}
		else {					// not within blue rectangle of track
			if (TEST_POPUP_ANNO) {
				for (Annotation annot : annotations) { 
					if (annot.boxContains(p)) // CAS503 add
						return "Right click for popup of description";
				}
			}
		}
		return "Sequence Track (" + getTitle() + "):  " + HOVER_MESSAGE; 
	}

	public String getTitle() {
		return getProjectDisplayName()+" "+getName();
	}

	protected Point2D getTitlePoint(Rectangle2D titleBounds) {	
		Rectangle2D headerBounds = (flipped ? footerLayout.getBounds() : /*headerLayout.getBounds()*/new Rectangle2D.Double(0, 0, 0, 0)); 
		Point2D headerP = (flipped ? footerPoint : headerPoint); 
		Point2D point = new Point2D.Double((rect.getWidth() / 2.0 + rect.x) - (titleBounds.getWidth() / 2.0 + titleBounds.getX()),
				headerP.getY() - headerBounds.getHeight() - titleOffset.getY());

		if (point.getX() + titleBounds.getX() + titleBounds.getWidth() > dimension.getWidth())
			point.setLocation(dimension.getWidth()- (titleBounds.getX() + titleBounds.getWidth()), point.getY());

		if (point.getX() < titleOffset.getX())
			point.setLocation(titleOffset.getX(), point.getY());

		return point;
	}

	protected boolean isSouthResizePoint(Point p) {
		return (p.getX() >= rect.x &&
				p.getX() <= rect.x + rect.width && 
				p.getY() >= (rect.y+rect.height) - MOUSE_PADDING && 
				p.getY() <= (rect.y+rect.height) + MOUSE_PADDING);
	}
	
	public boolean isFlipped() { return flipped; }

	/* Show Alignment */
	private void openCloseup(int start, int end) {
		if (drawingPanel == null || drawingPanel.getCloseUp() == null)
			return;

		try {// The CloseUp panel created at startup and reused.
			int hits = drawingPanel.getCloseUp().showCloseUp(this, start, end);
			if (hits == 0)
				Utilities.showWarningMessage("No hits found in region.\nCannot align without hits."); 
			else if (hits < 0)
				Utilities.showWarningMessage("Error showing close up view."); 
		}
		catch (OutOfMemoryError e) { 
			Utilities.showOutOfMemoryMessage();
			drawingPanel.smake(); // redraw after user clicks "ok"
		}
	}
	/* XXX CAS504  Show Sequence */
	private void popupSequence(int start, int end) {
		try {
			if (end-start > 1000000) {
				String x = "Regions is greater than 1Mbp (" + (end-start+1) + ")";
				if (!Utilities.showContinue("Show Sequence", x)) return;
			}
			String projName = getProjectName();
			int grpid = getGroup();
			String coords = start + ":" + end;
			String title = String.format("%s %,d-%,d  Len=%,d", projName, start, end, (end-start+1));
			 
			String seq = seqPool.loadPseudoSeq(coords, grpid);
			StringBuffer bf = new StringBuffer ();
			int len = seq.length(), x=0, inc=120;
			while ((x+inc)<len) {
				bf.append(seq.substring(x, x+inc) + "\n");
				x+=inc;
			}
			if (x<len) bf.append(seq.substring(x, len) + "\n");
			String msg = bf.toString();
			
			Utilities.displayInfoMonoSpace(null /*Component*/, title, msg, false /*!isModal*/);
		}
		catch (Exception e) {ErrorReport.print(e, "Error showing popup of sequence");}
	}
}
