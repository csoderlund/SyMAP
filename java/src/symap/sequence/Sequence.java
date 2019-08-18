package symap.sequence;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.KeyEvent;
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
import symap.track.Track;
import symap.track.TrackData;
import symap.track.TrackHolder;
import symap.drawingpanel.DrawingPanel;
import util.PropertiesReader;
import util.Rule;
import util.TextBox;
import util.Utilities;

/**
 * The Sequence track for a pseudo molecule. 
 * 
 * @author Austin Shoemaker
 */
public class Sequence extends Track {	
//	private static final boolean TIME_TRACE = false;
	
	private static final double OFFSET_SPACE = 7;
	private static final double OFFSET_SMALL = 1;
	
	private static final int MIN_BP_FOR_ANNOT_DESC = 500; // mdb added 7/16/09 #166

	public static final boolean DEFAULT_FLIPPED 		= false; // mdb added 7/23/07 #132
	public static final boolean DEFAULT_SHOW_RULER      = true;
	public static final boolean DEFAULT_SHOW_GENE       = true;  // mdb changed 2/15/08
	public static final boolean DEFAULT_SHOW_FRAME      = true;
	public static /*final*/ boolean DEFAULT_SHOW_ANNOT  = false; // mdb removed "final" 2/1/10
	public static final boolean DEFAULT_SHOW_SCORE_LINE	= true;  // mdb added 2/22/07 #100
	public static final boolean DEFAULT_SHOW_SCORE_VALUE= false; // mdb added 3/14/07 #100
	public static final boolean DEFAULT_SHOW_RIBBON		= true;	 // mdb added 8/7/07 #126
	public static final boolean DEFAULT_SHOW_GAP        = true;
	public static final boolean DEFAULT_SHOW_CENTROMERE = true;
	public static final boolean DEFAULT_SHOW_GENE_FULL  = true;  // mdb changed to true 3/5/07 #102
// mdb removed 3/31/08 #156
//	static {
//		Annotation.setDefaultDraw(Annotation.GENE, DEFAULT_SHOW_GENE_FULL ? Annotation.RECT : Annotation.TICK );
//	}

	public static Color unitColor;
//	public static Color bgcolor; 		// mdb removed 6/9/09 - moved to Track
	public static Color bgColor1; // mdb added 6/9/09
	public static Color bgColor2; // mdb added 6/9/09
	
	private static final Color border = Color.black; // mdb changed 7/17/09 - simplify properties
	private static Color footerColor;
	//private static final Color closeupDragColor = new Color(255,255,255,120); // mdb removed 6/9/09
	//private static final Color closeupDragBorderColor = Color.black; 			// mdb removed 6/9/09
	private static Font footerFont;
	private static Font unitFont;
	private static final Point2D defaultSequenceOffset = new Point2D.Double(20,40); // mdb changed 7/16/09 - simplify properties
	private static final Point2D titleOffset = new Point2D.Double(1,3); // mdb changed 7/17/09 - simplify properties
	private static int mouseWheelScrollFactor;	// mdb added 2/28/08
	private static int mouseWheelZoomFactor; 	// mdb added 2/28/08
	private static final double DEFAULT_WIDTH;
	private static final double SPACE_BETWEEN_RULES;
	private static final double RULER_LINE_LENGTH;
	private static final double MIN_DEFAULT_BP_PER_PIXEL;
	private static final double MAX_DEFAULT_BP_PER_PIXEL;
	private static final double PADDING = 100; // mdb changed 7/16/09 - simplify properties
	private static final double ANNOT_WIDTH;
	private static final String HOVER_MESSAGE = "Right-click for menu."; // mdb changed 7/17/09 - simplify properties
	static {
		PropertiesReader props = new PropertiesReader(SyMAP.class.getResource("/properties/sequence.properties"));
		//defaultSequenceOffset      = props.getPoint("sequenceOffset"); // mdb removed 7/16/09 - simplify properties
		unitColor                  = props.getColor("unitColor");
		bgColor1            		 = props.getColor("bgColor1");
		bgColor2            		 = props.getColor("bgColor2");
		//border                     = props.getColor("border"); 		// mdb removed 7/17/09 - simplify properties
		footerColor                = props.getColor("footerColor");
		footerFont                 = props.getFont("footerFont");
		unitFont                   = props.getFont("unitFont");
		DEFAULT_WIDTH              = props.getDouble("defaultWidth");
		SPACE_BETWEEN_RULES        = props.getDouble("spaceBetweenRules");
		RULER_LINE_LENGTH          = props.getDouble("rulerLineLength");
		MIN_DEFAULT_BP_PER_PIXEL   = props.getDouble("minDefaultBpPerPixel");
		MAX_DEFAULT_BP_PER_PIXEL   = props.getDouble("maxDefaultBpPerPixel");
		//titleOffset                = props.getPoint("titleOffset"); 	// mdb removed 7/17/09 - simplify properties
		//PADDING                    = props.getDouble("padding"); 		// mdb removed 7/16/09 - simplify properties
		ANNOT_WIDTH                = props.getDouble("annotWidth");
		//closeupDragColor           = props.getColor("closeupDragColor"); 		// mdb removed 6/9/09
		//closeupDragBorderColor     = props.getColor("closeupDragBorderColor");// mdb removed 6/9/09
		//HOVER_MESSAGE              = props.getString("hoverMessage");			// mdb removed 7/17/09 - simplify properties
		mouseWheelScrollFactor	   = props.getInt("mouseWheelScrollFactor"); 	// mdb added 2/28/08
		mouseWheelZoomFactor	   = props.getInt("mouseWheelZoomFactor"); 		// mdb added 2/28/08
	}

	protected int group;
	protected boolean showRuler, showGene, showFrame, showAnnot;
	protected boolean showScoreLine, showScoreValue; // mdb added 2/22/07 #100
	protected boolean showGap, showCentromere;
	protected boolean showFullGene;
	protected boolean showRibbon; // mdb added 8/7/07 #126
	protected String name;
	private PseudoPool pool;
	private List<Rule> ruleList;
	private Vector<Annotation> annotations;
	private TextLayout headerLayout, footerLayout;
	private Point2D.Float headerPoint, footerPoint;
	//private Rectangle dragRect; 	// mdb removed 6/25/09 - redundant with Track
	//private Point dragPoint;		// mdb removed 6/25/09 - redundant with Track

	public Sequence(DrawingPanel dp, TrackHolder holder) {
		this(dp,holder,dp.getPools().getPseudoPool());
	}

	public Sequence(DrawingPanel dp, TrackHolder holder, PseudoPool pool) {
		super(dp, holder, MIN_DEFAULT_BP_PER_PIXEL, MAX_DEFAULT_BP_PER_PIXEL, defaultSequenceOffset);
		this.pool = pool;
		ruleList = new Vector<Rule>(15,2);
		annotations = new Vector<Annotation>(50,1000);
		headerPoint = new Point2D.Float();
		footerPoint = new Point2D.Float();

// mdb removed 6/25/09 - redundant with Track
//		dragRect = new Rectangle(0, 0, NO_VALUE, NO_VALUE);
//		dragPoint = new Point();
		
		//bgColor = defaultBgColor; // mdb added 6/9/09

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
		showScoreLine  = DEFAULT_SHOW_SCORE_LINE;   // mdb added 2/22/07 #100
		showScoreValue = DEFAULT_SHOW_SCORE_VALUE; 	// mdb added 3/14/07 #100
		showRibbon     = DEFAULT_SHOW_RIBBON; 		// mdb added 8/7/07 #126
		showGap        = DEFAULT_SHOW_GAP;
		showCentromere = DEFAULT_SHOW_CENTROMERE;
		showFullGene   = DEFAULT_SHOW_GENE_FULL;
		flipped = false; // mdb added 2/10/10
	}

	public void setup(TrackData td) {
		SequenceTrackData sd = (SequenceTrackData)td;
		if (td.getProject() != project 
			|| sd.getGroup() != group 
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
	 *
	 * @see Track#clear()
	 */
	public void clear() {
		ruleList.clear();
		annotations.clear();
		headerPoint.setLocation(0,0);
		footerPoint.setLocation(0,0);
		
// mdb removed 6/25/09 - redundant with Track		
//		dragRect.setRect(0,0,NO_VALUE,NO_VALUE);
//		dragPoint.setLocation(0,0);
		
		super.clear();
	}

	public void clearData() {
		ruleList.clear();
		annotations.removeAllElements();//clear(); // mdb changed 2/3/10
	}

	/** 
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return "[Sequence "+getProjectName()+" "+getGroup()+"]";
	}

	/**
	 * @return The sequence group if it has not been set.
	 */
	public int getGroup() {
		return group;
	}

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
	
	// mdb added 2/1/10
	public static void setDefaultShowAnnotation(boolean b) {
		DEFAULT_SHOW_ANNOT = b;
	}

	/**
	 * Set whether the ruler should be showen or hidden.
	 * Sets the track to be built on the next make if changed.
	 * 
	 * @param show true to show the rule.
	 * @return a <code>boolean</code> value of true on a change
	 */
	public boolean showRuler(boolean show) {
		if (showRuler != show) {
			showRuler = show;
			clearTrackBuild();
			return true;
		}
		return false;
	}

	/**
	 * Method <code>showGap</code> sets whether to show or hide the gap marker.
	 * If return true, Sequence requires a rebuild.
	 *
	 * @param show a <code>boolean</code> value of true to show, false to hide
	 * @return a <code>boolean</code> value of true when show did not equal to the current settings
	 */
	public boolean showGap(boolean show) {
		if (showGap != show) {
			showGap = show;
			clearTrackBuild();
			return true;
		}
		return false;
	}

	/**
	 * Method <code>showCentromere</code> sets whether to show or hide the centromere marker.
	 * If return true, Sequence requires a rebuild.
	 *
	 * @param show a <code>boolean</code> value of true to show, false to hide
	 * @return a <code>boolean</code> value of true when show did not equal to the current settings
	 */
	public boolean showCentromere(boolean show) {
		if (showCentromere != show) {
			showCentromere = show;
			clearTrackBuild();
			return true;
		}
		return false;
	}

	/**
	 * Method <code>showAnnotation</code> sets whether to show or hide the
	 * annotation ruler. Sets the track to be built on the next make if changed.
	 *
	 * @param show a <code>boolean</code> value
	 * @return a <code>boolean</code> value of true on a change
	 */
	public boolean showAnnotation(boolean show) {
		if (annotations.size() == 0)
		{
			showAnnot = false;
			return false;
		}
		if (showAnnot != show) 
		{
			showAnnot = show;
			clearTrackBuild();
			return true;
		}
		return false;
	}
	public boolean getShowAnnot() { return showAnnot;}
	// mdb added 3/8/07 #101
	public Vector<Annotation> getAnnotations() {
		return annotations;
	}
	
	// mdb added 1/7/09 for pseudo-pseudo closeup
	public Vector<Annotation> getAnnotations(int type, int start, int end) {
		Vector<Annotation> out = new Vector<Annotation>();
		
		for (Annotation a : annotations) {
			if ((type == -1 || a.getType() == type) 
					&& Utilities.isOverlapping(start, end, a.getStart(), a.getEnd()))
				out.add(a);
		}
		
		return out;
	}

	// mdb added 12/7/09 #204
	public int[] getAnnotationTypeCounts() {
		int[] counts = new int[Annotation.numTypes];
		
		for (Annotation a : annotations)
			counts[a.getType()]++;
		
		return counts;
	}
	
	/**
	 * Method <code>showScoreLine</code> sets whether to show or hide the
	 * hit score lines. Sets the track to be built on the next make if changed.
	 *
	 * @param show a <code>boolean</code> value
	 * @return a <code>boolean</code> value of true on a change
	 */
	public boolean showScoreLine(boolean show) { // mdb added 2/22/07 #100
		if (showScoreLine != show) {
			showScoreLine = show;
			clearTrackBuild();
			return true;
		}
		return false;
	}
	
	public boolean getShowScoreLine() { // mdb added 2/22/07 #100
		return showScoreLine;
	}
	
	/**
	 * Method <code>showScoreValue</code> sets whether to show or hide the
	 * hit score value labels. Sets the track to be built on the next make if changed.
	 *
	 * @param show a <code>boolean</code> value
	 * @return a <code>boolean</code> value of true on a change
	 */
	public boolean showScoreValue(boolean show) { // mdb added 3/14/07 #100
		if (showScoreValue != show) {
			showScoreValue = show;
			clearTrackBuild();
			return true;
		}
		return false;
	}
	
	public boolean getShowScoreValue() { // mdb added 3/14/07 #100
		return showScoreValue;
	}
	
	/**
	 * Method <code>showRibbon</code> sets whether to show or hide the
	 * hit ribbons. Sets the track to be built on the next make if changed.
	 *
	 * @param show a <code>boolean</code> value
	 * @return a <code>boolean</code> value of true on a change
	 */
	public boolean showRibbon(boolean show) { // mdb added 8/7/07 #126
		if (showRibbon != show) {
			showRibbon = show;
			clearTrackBuild();
			return true;
		}
		return false;
	}
	
	public boolean getShowRibbon() { // mdb added 8/7/07 #126
		return showRibbon;
	}

	/**
	 * Method <code>showFrame</code> sets whether to show or hide the
	 * framework markers. Sets the track to be built on the next make if changed.
	 *
	 * @param show a <code>boolean</code> value
	 * @return a <code>boolean</code> value of true on a change
	 */
	public boolean showFrame(boolean show) {
		if (showFrame != show) {
			showFrame = show;
			clearTrackBuild();
			return true;
		}
		return false;
	}

	/**
	 * Method <code>showGene</code> sets whether to show or hide the
	 * gene annotations. Sets the track to be built on the next make if changed.
	 *
	 * @param show a <code>boolean</code> value
	 * @return a <code>boolean</code> value of true on a change
	 */
	public boolean showGene(boolean show) {
		if (showGene != show) {
			showGene = show;
			clearTrackBuild();
			return true;
		}
		return false;
	}

	/**
	 * Method <code>showFullGene</code> sets whether a gene should be shown full (start to end) (true), or
	 * just as a mark at the mid point (false).
	 *
	 * @param show a <code>boolean</code> value
	 * @return a <code>boolean</code> value
	 */
	public boolean showFullGene(boolean show) {
		if (showFullGene != show) {
			showFullGene = show;
			clearTrackBuild();
			return true;
		}
		return false;
	}
	
	// mdb added 7/23/07
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
			name = pool.setSequence(this, size, annotations);	
			if (annotations.size() == 0) showAnnot = false;
		} catch (SQLException s1) {
			s1.printStackTrace();
			System.err.println("Initializing Sequence failed.");
			pool.close();
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
		
		//long cStart = System.currentTimeMillis();
		
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
			//if (drawingPanel != null) drawingPanel.displayWarning("Unable to size sequence view as requested."); // mdb removed 5/25/07 #119
			//Utilities.showWarningMessage("Unable to size sequence view as requested."); 	// mdb added 5/25/07 #119 // mdb removed 7/2/07
			System.err.println("Unable to size sequence view as requested."); 				// mdb added 7/2/07
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
			//for (y = rect.y + SPACE_BETWEEN_RULES; y < h; y += SPACE_BETWEEN_RULES) { // mdb removed 8/21/07 #143
			for (y = rect.y; y < h + SPACE_BETWEEN_RULES; y += SPACE_BETWEEN_RULES) {	// mdb added 8/21/07 #143
				if (y < h && y > h - SPACE_BETWEEN_RULES) continue; // mdb added 8/21/07 #143
				if (y > h) y = h; // mdb added 8/21/07 #143
				Rule r = new Rule(unitColor, unitFont);
				r.setLine(x1, y, x2, y);
				//cb = GenomicsNumber.getCbPerPixel(bpPerCb,bpPerPixel,y-rect.y) + start.getValue(); // mdb removed 7/23/07 #132
				cb = GenomicsNumber.getCbPerPixel(bpPerCb,bpPerPixel,(flipped ? h-y : y-rect.y)) + start.getValue(); // mdb added 7/23/07 #132
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
		 * Setup the annotations - mdb rewritten 3/31/08 #154
		 */
		getHolder().removeAll(); // mdb added 7/15/09 #166 - remove TextBoxes
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
				if (showFullGene) width /= 4; // mdb added 3/8/07 #102
			}
			else if (annot.isExon()
					|| annot.isSyGene()) // mdb added condition 7/31/09 #167
				width = ANNOT_WIDTH;
			
			annot.setRectangle(
					centRect,
					start.getBPValue(),
					end.getBPValue(),
					bpPerPixel,
					width,
					flipped);
			
			if (showAnnot && annot.isVisible()) { // Setup description
				//Rule r = null; // mdb added 7/15/09 #166
				x1 = (orient == RIGHT_ORIENT ? rect.x + rect.width + RULER_LINE_LENGTH + 2 : rect.x);
				x2 = (orient == RIGHT_ORIENT ? x1 + RULER_LINE_LENGTH : x1 - RULER_LINE_LENGTH);
				if (annot.hasShortDescription() 
						&& getBpPerPixel() < MIN_BP_FOR_ANNOT_DESC) // mdb added condition 7/16/09 #166 - only show annot desc if zoomed-in
				{ 
// mdb removed 7/15/09 #166
//					r = new Rule(annot.getColor(),unitFont); 
//					r.setLine(x1,annot.getY(),x2,annot.getY());
//					layout = new TextLayout(annot.getShortDescription(),unitFont,frc);
//					bounds = layout.getBounds();
					
					ty = annot.getY1();// annot.getY() + (bounds.getHeight() / 2.0); // mdb changed 7/15/09 #166
						
					// mdb added 7/15/09 #166 - add annotation description TextBox
					TextBox tb = new TextBox(annot.getVectorDescription(),unitFont,(int)x1,(int)ty,40,200);
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
					//r.setText(layout, tx, ty); // mdb removed 7/15/09 #166
				}
				//annot.setRule(r); // mdb removed 7/15/09 #166
			}
			// mdb removed 7/15/09 #166
			//else
			//	annot.setRule(null);
		} // end for(all annotations)
		
		// mdb added 12/9/09 #166 - show instructions for annotation descriptions when zoomed out
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
		//headerLayout = new TextLayout(start.getFormatted(), footerFont, frc);	// mdb removed 8/21/07 #143
		//bounds = headerLayout.getBounds();									// mdb removed 8/21/07 #143
		bounds = new Rectangle2D.Double(0, 0, 0, 0); 							// mdb added 8/21/07 #143
		x = (rect.x + (rect.width / 2.0)) - (bounds.getX() + (bounds.getWidth() / 2.0));
		if (flipped) // mdb added flipped 8/6/07 #132
			y = rect.y + rect.height + OFFSET_SPACE + bounds.getHeight();
		else
			y = rect.y - OFFSET_SPACE;
		headerPoint.setLocation((float) x, (float) y);

		totalRect.width = Math.max(totalRect.width, x + bounds.getX() + bounds.getWidth());
		totalRect.x = Math.min(totalRect.x, x);
		totalRect.y = Math.min(totalRect.y, y - bounds.getHeight());

		//footerLayout = new TextLayout(end.getFormatted()+" of "+size.getFormatted(), footerFont, frc); // mdb removed 8/21/07 #143
		footerLayout = new TextLayout(new GenomicsNumber(this,end.getValue()-start.getValue()+1).getFormatted()+" of "+size.getFormatted(), footerFont, frc); // mdb added 8/21/07 #143
		bounds = footerLayout.getBounds();
		x = (rect.x + (rect.width / 2.0)) - (bounds.getX() + (bounds.getWidth() / 2.0));
		if (flipped) // mdb added flipped 8/6/07 #132
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
		//if (TIME_TRACE) System.out.println("Sequence: build time = "+(System.currentTimeMillis()-cStart)+" ms");
		
		return true;
	}
	
	public Point2D getPoint(long bpPos, int orient) {
		//if (bpPos < start.getValue() || bpPos > end.getValue()) return null; // mdb removed 8/7/07 #126
		if (bpPos < start.getValue()) bpPos = start.getValue(); // mdb added 8/7/07 #126
		if (bpPos > end.getValue())   bpPos = end.getValue(); 	// mdb added 8/7/07 #126
		double x = (orient == LEFT_ORIENT ? rect.x+rect.width : rect.x);						    // mdb added 8/6/07 #132
		double y = rect.y + GenomicsNumber.getPixelValue(bpPerCb,bpPos-start.getValue(),bpPerPixel);// mdb added 8/6/07 #132
		if (flipped) y = rect.y + rect.y + rect.height - y;	// mdb added 8/6/07 #132
		//return new Point2D.Double(orient == LEFT_ORIENT ? rect.x+rect.width : rect.x,  	// mdb removed 8/6/07 #132
		//rect.y + GenomicsNumber.getPixelValue(bpPerCb,bpPos-start.getValue(),bpPerPixel));// mdb removed 8/6/07 #132
		return new Point2D.Double(x, y); // mdb added 8/6/07 #132
	}
	
	// mdb added 2/23/07 #102
	public double getX() { return rect.getX(); }
	
	// mdb added 2/23/07 #102
	public double getWidth() { return rect.getWidth(); }
	
	public void paintComponent(Graphics g) {
		//long cStart = System.currentTimeMillis();
		
		if (hasBuilt()) {
			Graphics2D g2 = (Graphics2D) g;

			g2.setPaint((position % 2 == 0 ? bgColor1 : bgColor2)); 
			//g2.setPaint(bgColor); 
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

// mdb removed 6/25/09 - redundant with Track			
//			if (!isCleared(dragRect)) {
//				g2.setPaint(closeupDragColor);
//				g2.fill(dragRect);
//				g2.setPaint(closeupDragBorderColor);
//				g2.draw(dragRect);
//			}
		}

		super.paintComponent(g); // must be done at the end
		
		//if (TIME_TRACE) System.out.println("Sequence: paint time = "+(System.currentTimeMillis()-cStart)+" ms");
	}

	public void mousePressed(MouseEvent e) {
		super.mousePressed(e);

// mdb removed 6/25/09 - redundant with Track		
//		Point point = e.getPoint();
//		//Cursor c = getCursor();
//		if (drawingPanel.getMouseFunction().equals("Close-up")
//				/*c == null || c.getType() == Cursor.DEFAULT_CURSOR 
//				&& rect.contains(point.getX(),point.getY())*/)
//		{
//			if (isCleared(dragRect)) {
//				dragPoint.setLocation(point);
//				if (dragPoint.getX() < rect.x)
//					dragPoint.setLocation(rect.x, dragPoint.getY());
//				else if (dragPoint.getX() > rect.x + rect.width)
//					dragPoint.setLocation(rect.x + rect.width, dragPoint.getY());
//				if (dragPoint.getY() < rect.y)
//					dragPoint.setLocation(dragPoint.getX(), rect.y);
//				else if (dragPoint.getY() > rect.y + rect.height)
//					dragPoint.setLocation(dragPoint.getX(), rect.y + rect.height);
//				dragRect.setLocation(dragPoint);
//				dragRect.setSize(0, 0);
//			}
//		}
	}

	public void mouseReleased(MouseEvent e) {
		Point p = e.getPoint();
		if (drawingPanel.isMouseFunctionCloseup() 
				&& dragRect.getHeight() > 0 
				&& rect.contains(p.getX(), p.getY()))
		{
			int start = getBP( dragRect.getY() );
			int end = getBP( dragRect.getY() + dragRect.getHeight() );
			
			// mdb added 8/31/09 - adjust coords based on flip
			if (flipped) {
				int temp = start;
				start = (int)getEnd() - end + (int)getStart();
				end = (int)getEnd() - temp + (int)getStart();
			}
			
			openCloseup(start, end);
		}
		
		super.mouseReleased(e);
		
// mdb removed 6/25/09 - redundant with Track		
//		if (!isCleared(dragRect)) {
//			clear(dragRect);
//			repaint();
//		}
//		clear(dragPoint);
	}
	
	// mdb added 3/2/07 #106
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
	
	// mdb added 2/28/08 #124
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
	
	public void mouseWheelMoved(MouseWheelEvent e) { // mdb added 3/1/07 #106
		int notches = e.getWheelRotation();
		long viewSize = getEnd() - getStart() + 1;
		
		if (e.isControlDown()) // Zoom - mdb added 2/28/08 #124
			zoomRange(notches, e.getPoint().getY() - rect.y + 1, viewSize);
		else
			scrollRange(notches, viewSize);
	}
	
	public void mouseWheelMoved(MouseWheelEvent e, long viewSize) { // mdb added 2/29/08 #124
		int notches = e.getWheelRotation();
		
		if (e.isControlDown()) // Zoom - mdb added 2/28/08 #124
			zoomRange(notches, e.getPoint().getY() - rect.y + 1, viewSize);
		else
			scrollRange(notches, viewSize);
	}
	
	public void keyPressed(KeyEvent e) { // mdb added 3/2/07 #106
		// mdb: this code is incomplete/non-working
		int c = e.getKeyCode();
		
		switch (c) {
		//case KeyEvent.VK_PAGE_UP	: scrollRange(-1);	break;
		//case KeyEvent.VK_PAGE_DOWN	: scrollRange(1); 	break;
		}
	}

	public void mouseDragged(MouseEvent e) {	
		super.mouseDragged(e);
		
		if (drawingPanel.isMouseFunctionCloseup() && getCursor().getType() != Cursor.S_RESIZE_CURSOR) // mdb changed 6/25/09
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
				else { //if (happened == 2)
					if (dragRect.height * bpPerPixel > CloseUp.MAX_CLOSEUP_BP) {
						dragRect.y += dragRect.height - mh;
						dragRect.height = mh;
						if (dragPoint.y < dragRect.y)
							dragPoint.y = dragRect.y;
					}
				}
			}
		}
		//else if (!isCleared(dragRect)) clear(dragRect); // mdb removed 6/25/09
	}

	/**
	 * Method <code>getHelpText</code> returns the desired text for when the mouse is
	 * over a certain point.
	 *
	 * @param event a <code>MouseEvent</code> value
	 * @return a <code>String</code> value
	 */
	public String getHelpText(MouseEvent event) {
		Point p = event.getPoint();
		if (rect.contains(p)) {
			for (Annotation annot : annotations) {
				if (annot.contains(p))
					return annot.getLongDescription();
			}
		}

		return "Sequence Track (" + getTitle() + "):  " + HOVER_MESSAGE; // mdb changed 3/30/07 #112
	}

	public String getTitle() {
		return getProjectDisplayName()+" "+getName();
	}

	protected Point2D getTitlePoint(Rectangle2D titleBounds) {	
		Rectangle2D headerBounds = (flipped ? footerLayout.getBounds() : /*headerLayout.getBounds()*/new Rectangle2D.Double(0, 0, 0, 0)); // mdb added flipped 8/6/07 #132 // mdb removed header 8/21/07 #143
		Point2D headerP = (flipped ? footerPoint : headerPoint); // mdb added flipped 8/6/07 #132 // mdb removed header 8/21/07 #143
		
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
	
	// mdb added 7/9/09
	public boolean isFlipped() { return flipped; }

	private void openCloseup(int start, int end) {
		if (drawingPanel == null || drawingPanel.getCloseUp() == null)
			return;

		try {
			int hits = drawingPanel.getCloseUp().showCloseUp(this, start, end);
			if (hits == 0)
				Utilities.showWarningMessage("No hits found in region."); // mdb changed 5/25/07 #119
			else if (hits < 0)
				Utilities.showWarningMessage("Error showing close up view."); // mdb changed 5/25/07 #119
		}
		catch (OutOfMemoryError e) { // mdb added 8/31/09
			Utilities.showOutOfMemoryMessage();
			drawingPanel.smake(); // redraw after user clicks "ok"
		}
	}
}
