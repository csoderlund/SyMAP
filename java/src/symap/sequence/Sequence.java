package symap.sequence;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.sql.SQLException;
import java.util.List;
import java.util.Vector;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import number.GenomicsNumber;
import symap.SyMAP;
import symap.closeup.CloseUp;
import symap.closeup.TextShowSeq;
import symap.track.Track;
import symap.track.TrackData;
import symap.track.TrackHolder;
import symap.drawingpanel.DrawingPanel;
import symap.mapper.HitData;
import symap.mapper.SeqHits;
import util.PropertiesReader;
import util.TextBox;
import util.Utilities;
import util.ErrorReport;

/**
 * The Sequence track for a chromosome. Called from DrawingPanel
 * Has a vector of annotations, which it also draws. The hit is drawn in this rectangle by PseudoPseudoHits
 */
public class Sequence extends Track {
	// moved colors and constants to end
	protected int group;
	protected boolean showRuler, showGene, showAnnot, showGeneLine;
	protected boolean showScoreLine, showScoreValue, showHitNum; // CAS531 add showHitNum
	protected boolean showGap, showCentromere;
	protected boolean showFullGene;
	protected boolean showHitLen; 	// CAS512 renamed Ribbon to HitLen everywhere; show Hit Graphics on Sequence rect
	protected String name;			// e.g. Chr01
	
	private SeqHits seqHitsObj; 	// CAS517 added so can get to hits 
	private boolean isQuery;	    // CAS517 added; CAS531 renamed to be clear that it is pairs.proj1_idx and first in hits
	
	private PseudoPool psePool;
	private List<Rule> ruleList;
	private Vector<Annotation> allAnnoVec;
	private TextLayout headerLayout, footerLayout;
	private Point2D.Float headerPoint, footerPoint;
	
	private HashMap <Integer, Integer> olapMap = new HashMap <Integer, Integer> (); // CAS517 added for overlapping genes; CAS518 global
	private Rectangle2D.Double centGeneRect;		// CAS518 add so do not display message if hover in-between genes
	private final int OVERLAP_OFFSET=12;			// CAS517/518 for overlap and yellow text
	
	public Sequence(DrawingPanel dp, TrackHolder holder) {
		this(dp, holder, dp.getPools().getPseudoPool());
	}

	private Sequence(DrawingPanel dp, TrackHolder holder, PseudoPool psePool) {
		super(dp, holder, MIN_DEFAULT_BP_PER_PIXEL, MAX_DEFAULT_BP_PER_PIXEL, defaultSequenceOffset);
		this.psePool = psePool;
		
		ruleList = new Vector<Rule>(15,2);
		allAnnoVec = new Vector<Annotation>(50,1000);
		headerPoint = new Point2D.Float();
		footerPoint = new Point2D.Float();

		group   = NO_VALUE;
		projIdx = NO_VALUE;
		centGeneRect = new Rectangle2D.Double();
	}
	// CAS517 add in order to have access to hits 
	public void setSeqHits(SeqHits pObj, boolean isSwapped) { 
		this.seqHitsObj = pObj;
		this.isQuery = isSwapped;
	}
	
	public void setOtherProject(int otherProject) {
		if (otherProject != this.otherProjIdx) setup(projIdx,group,otherProject);
	}

	public void setup(int project, int group, int otherProject) {
		if (this.group != group || this.projIdx != project || this.otherProjIdx != otherProject) { 
			reset(project,otherProject);
			this.group = group;
			if (this.otherProjIdx != NO_VALUE) init();
		}
		else reset();
		
		showRuler      = DEFAULT_SHOW_RULER;
		showGene       = DEFAULT_SHOW_GENE;
		showGeneLine   = DEFAULT_SHOW_GENE_LINE;
		showAnnot      = DEFAULT_SHOW_ANNOT;
		showScoreLine  = DEFAULT_SHOW_SCORE_LINE;   
		showScoreValue = DEFAULT_SHOW_SCORE_VALUE; 	
		showHitNum 		= false;
		showHitLen     = DEFAULT_SHOW_RIBBON; 		
		showGap        = DEFAULT_SHOW_GAP;
		showCentromere = DEFAULT_SHOW_CENTROMERE;
		showFullGene   = DEFAULT_SHOW_GENE_FULL;
		flipped = false; 
	}

	public void setup(TrackData td) {
		SequenceTrackData sd = (SequenceTrackData)td;
		if (td.getProject() != projIdx 	|| sd.getGroup() != group  || td.getOtherProject() != otherProjIdx)
			reset(td.getProject(),td.getOtherProject());
		
		sd.setTrack(this);
		firstBuild = false;
		init();
		sd.setTrack(this);
	}

	public TrackData getData() {
		return new SequenceTrackData(this);
	}

	/** @see Track#clear() */
	public void clear() {
		ruleList.clear();
		allAnnoVec.clear();
		olapMap.clear();
		headerPoint.setLocation(0,0);
		footerPoint.setLocation(0,0);
		dragPoint.setLocation(0,0);
		
		super.clear();
	}

	public void clearData() {
		ruleList.clear();
		allAnnoVec.removeAllElements();
	}

	public int getGroup() {return group;}

	public String getName() {
		return drawingPanel.getPools().getProjectProperties().getProperty(getProject(),"grp_prefix") +
			(name == null ? "" : name);
	}

	/*** @see Track#getPadding() */
	public double getPadding() {return PADDING;}
	
	public static void setDefaultShowAnnotation(boolean b) {DEFAULT_SHOW_ANNOT = b;}

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
		if (allAnnoVec.size() == 0) {
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
	
	public Vector<Annotation> getAnnotations() {return allAnnoVec;}
	
	// Called by CloseUpDialog; type is Gene or Exon
	public Vector<Annotation> getAnnotations(int type, int start, int end) {
		Vector<Annotation> out = new Vector<Annotation>();
		
		for (Annotation a : allAnnoVec) {
			if (a.getType() == type && isOverlapping(start, end, a.getStart(), a.getEnd()))
				out.add(a);
		}	
		return out;
	}
	private boolean isOverlapping(int start1, int end1, int start2, int end2) {
		if ((start1 >= start2 && start1 <= end2) || (end1  >= start2 && end1 <= end2)
		 || (start2 >= start1 && start2 <= end1) || (end2  >= start1 && end2 <= end1))
		{
			return true;
		}
		return false;
	}
	// Called by SequenceFilter
	public int[] getAnnotationTypeCounts() {
		int[] counts = new int[Annotation.numTypes];
		
		for (Annotation a : allAnnoVec)
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
	
	public SeqHits getSeqHits() { return seqHitsObj;} // CAS531 add for Closeup title
	
	public boolean getShowHitLen() { return showHitLen; }
	
	public boolean getShowScoreLine() { return showScoreLine;}
	
	public boolean getShowScoreValue() { return showScoreValue; }
	
	public boolean getShowHitNum() { return showHitNum; }
	
	public boolean showScoreValue(boolean show) { 
		if (showScoreValue != show) {
			showScoreValue = show;
			if (show) showHitNum(false);
			clearTrackBuild();
			return true;
		}
		return false;
	}
	public boolean showHitNum(boolean show) { // CAS531 add
		if (showHitNum != show) {
			showHitNum = show;
			if (show) showScoreValue(false);
			clearTrackBuild();
			return true;
		}
		return false;
	}
	// drawn SeqHits.PseudoHits.paintHitLen
	public boolean showHitLen(boolean show) { // hit ribbon - graphics on sequence rectangle
		if (showHitLen != show) {
			showHitLen = show;
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
	public boolean showGeneLine(boolean show) {// CAS520 add
		if (showGeneLine != show) {
			showGeneLine = show;
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
	// Set the annotation vector here
	protected boolean init() {
		if (hasInit()) return true;
		if (projIdx < 1 || group < 1 || otherProjIdx < 1) return false;

		try {
			name = psePool.setSequence(this, size, allAnnoVec);	
			if (allAnnoVec.size() == 0) showAnnot = false; 
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
	 * XXX Build layout: 
	 * Sets up the drawing objects for this sequence. Called by DrawingPanel and Track
	 * Start and end are Track GenomicsNumber variables, stating start/end 
	 * The Hit Length, Hit %Id Value and Bar are drawn in PseudoPseudoHits.PseudoHits.paintHitLen and its paintComponent
	 */
	public boolean build() { 
		if (hasBuilt()) return true;
		if (!hasInit()) return false;
		
		if (allAnnoVec.size() == 0) showAnnot = false;
		
		if (width == NO_VALUE) width = DEFAULT_WIDTH;

		setFullBpPerPixel();

		TextLayout layout;
		FontRenderContext frc;
		Rectangle2D bounds;
		double x, y, h, x1, x2, tx, ty;
		Rectangle2D.Double totalRect = new Rectangle2D.Double();

		if (height > 0) {
			long diff = Math.abs(end.getValue()-start.getValue());
			adjustBpPerPixel(GenomicsNumber.getBpPerPixel(bpPerCb, diff, height));
		}
		// this started happening with later Java versions if they click too fast
		if (!validSize()) { // CAS521 make popup
			Utilities.showWarningMessage("Unable to size sequence view. Try again - make sure box is highlighted."); 				
			adjustSize();
		}

		GenomicsNumber length = new GenomicsNumber(this,end.getValue() - start.getValue());
		rect.setRect(trackOffset.getX(), trackOffset.getY(), width, length.getPixelValue()); // chr rect
		
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
		 * Setup the annotations
		 */
		boolean isRight = (orient == RIGHT_ORIENT);
		double lastStart=0;
		
		getHolder().removeAll(); 
		Rectangle2D centRect = new Rectangle2D.Double(rect.x+1,rect.y,rect.width-2,rect.height);
		centGeneRect = new Rectangle2D.Double(rect.x+1,rect.y,rect.width-2,rect.height);
		
		if (SyMAP.DEBUG) System.err.println("SEQ: " + allAnnoVec.size() + " " + getHolder().getTrackNum() + " " + toString());
		
		buildOlap(); // builds first time only
			
		// XXX Sorted by genes (genenum, start), then exons (see PseudoData.setAnnotations)
	    for (Annotation annot : allAnnoVec) {
	    	if (((annot.isGene() || annot.isExon()) && !showGene) 
	    			|| (annot.isGap() && !showGap)
	    			|| (annot.isCentromere() && !showCentromere)){
					annot.clear();
					continue; // CAS520 v518 bug, does not display track if, return true;
			}
	    	
			double dwidth = rect.width, hwidth=0; // displayWidth, hoverWidth for gene
			int offset=0;
			
			if (annot.isGene()) {
				dwidth = hwidth = ANNOT_WIDTH;
				if (showFullGene) dwidth /= GENE_DIV; // showFullGene always true
				if (olapMap.containsKey(annot.getAnnoIdx())) offset = olapMap.get(annot.getAnnoIdx());
				
			}
			else if (annot.isExon() || annot.isSyGene()) {
				dwidth = ANNOT_WIDTH;
				if (olapMap.containsKey(annot.getGeneIdx())) offset = olapMap.get(annot.getGeneIdx());
			}
			if (offset>0 && isRight) offset = -offset;
			
		/****/
			annot.setRectangle(centRect, start.getBPValue(), end.getBPValue(),
					bpPerPixel, dwidth, hwidth, flipped, offset, showGeneLine); // CAS520 add showGeneLine
			
			// Setup yellow description
			if (annot.isGene() && showAnnot && annot.isVisible()) { // CAS517 added isGene
				if (annot.hasShortDescription() && getBpPerPixel() < MIN_BP_FOR_ANNOT_DESC)  { 
					x1 = isRight ? (rect.x + rect.width + RULER_LINE_LENGTH + 2) : rect.x; 
					x2 = (isRight ? x1 + RULER_LINE_LENGTH  : x1 - RULER_LINE_LENGTH);
					
					ty = annot.getY1();
					if (lastStart==ty) ty = ty+OVERLAP_OFFSET;
					lastStart=ty;
					
					TextBox tb = new TextBox(annot.getVectorDescription(),unitFont, (int)x1, (int)ty, 40, 200);
					if (POPUP_ANNO) annot.setTextBox(tb); 	// CAS503 right-click (mousePressed)
					getHolder().add(tb); 					// adds it to the TrackHolder JComponent
					
					bounds = tb.getBounds();
					totalRect.height = Math.max(totalRect.height, ty); // adjust totalRect for this box
					totalRect.y = Math.min(totalRect.y, ty);
					if (isRight) {
						tx = x2 + OFFSET_SMALL;
						totalRect.width = Math.max(totalRect.width, bounds.getWidth()+bounds.getX()+tx);
					}
					else {
						tx = x2-OFFSET_SMALL-bounds.getWidth()-bounds.getX();
						totalRect.x = Math.min(totalRect.x, tx);
					}
				}
			}
		} // end for loop of (all annotations)
		
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
		for (Annotation annot : allAnnoVec)
			annot.setOffset(totalRect.x-1, totalRect.y);

		headerPoint.x -= totalRect.x;
		headerPoint.y -= totalRect.y;
		footerPoint.x -= totalRect.x;
		footerPoint.y -= totalRect.y;

		setTitle();
		setBuild();
		
		return true;
	}
	
	/*******************************************************
	 * CAS518 added: Called during init(); used in build()
	 */
	private void buildOlap() {
		if (olapMap.size()>0) return;

		int lastGeneNum=-1;
		Vector <Annotation> numList = new Vector <Annotation> ();
		
		 for (Annotation annot : allAnnoVec) {
		    	if (!annot.isGene()) continue;
		    	if (annot.isExon()) break; // at end
		    	
				if (annot.getGeneNum() == lastGeneNum) {
					numList.add(annot);
					continue;
				}
				
				buildPlace(numList);
				numList.clear();
				
				lastGeneNum = annot.getGeneNum();
				numList.add(annot);
		 }
		 buildPlace(numList); // CAS519 missing last one
		 
		 if (olapMap.size()==0) {
			 olapMap.put(-1,-1);
		 }
	}
	/*******************************************************
	 * It is run on every build, so needs to be fast, but is a heuristic
	 * CAS519 simplified algo; uses genenum to find set of overlapping, 
	 * but does not use genenum suffix (uses same overlap check as in AnnoLoadPost.computeGeneNum)
	 */
	private void buildPlace(Vector <Annotation> numList) {
		try {
			if (numList.size()==1) return;
			if (numList.size()==2) { // get(0) has no offset
				olapMap.put(numList.get(1).getAnnoIdx(), OVERLAP_OFFSET);
				return;
			}
		// Create vector ordered by length
			Vector <GeneData> gdVec = new Vector <GeneData> ();
			for (Annotation annot : numList) gdVec.add(new GeneData(annot));
			
			Collections.sort(gdVec,
				new Comparator<GeneData>() {
					public int compare(GeneData a1, GeneData a2) { 
						if (a1.start!=a2.start) return (a1.start - a2.start); // it does not always catch =start
						
						if (SyMAP.DEBUG) System.out.println("Seq cmp: a1 " + a1.annot.getGeneNumStr() + " s1: " + a1.start + " l1: " + a1.len + " l2: " + a2.len + " l2-l1: " + (a2.len - a1.len));
						return (a2.len - a1.len);
					}
				});
		
		// Determine contained and overlap relations AND assign initial level	
			for (int i=0; i<gdVec.size()-1; i++) {
				GeneData gdi = gdVec.get(i);
				for (int j=i+1; j<gdVec.size(); j++) {
					GeneData gdj = gdVec.get(j);
					if (!gdi.isContain(gdj))
						 gdi.isOverlap(gdj);
				}
			}
			for (GeneData gd : gdVec) {
				for (GeneData o : gd.olVec) {
					if (gd.level==o.level)  {
						o.level = gd.level+1;
						if (o.level==3) o.level=0;
					}
				}
			}
			for (GeneData gd : gdVec) {
				if (gd.level==0) continue;
				
				int offset = gd.level* OVERLAP_OFFSET;
				olapMap.put(gd.annot.getAnnoIdx(), offset);
			}
		}
		catch (Exception e) {ErrorReport.print(e, "Place overlapping genes");}
	}
	private class GeneData {
		Annotation annot;
		int start, end, len;
		int level=0;
		Vector <GeneData> olVec = new Vector <GeneData> ();
		
		public GeneData(Annotation annot) {
			this.annot=annot;
			start = annot.getStart();
			end = annot.getEnd();
			len = Math.abs(end-start)+1;
		}
		boolean isContain(GeneData gd) {
			if (gd.start >= start && gd.end <= end) {
				olVec.add(gd);
				return true;
			}
			return false;
		}
		private boolean isOverlap(GeneData gd) {
			int gap = Math.min(end,gd.end) - Math.max(start,gd.start);
			if (gap > 0) {
				olVec.add(gd);
				return true;
			}
			return false;
		}
	}
	/***************** complete build methods **************************************/
	
	/***********************************************************************************/
	public Point2D getPoint(long bpPos, int trackPos) {
		if (bpPos < start.getValue()) bpPos = start.getValue(); 
		if (bpPos > end.getValue())   bpPos = end.getValue(); 	
		double x = (trackPos == LEFT_ORIENT ? rect.x+rect.width : rect.x);			
		double y = rect.y + GenomicsNumber.getPixelValue(bpPerCb,bpPos-start.getValue(),bpPerPixel);
		
		if (flipped) y = rect.y + rect.y + rect.height - y;	
		
		return new Point2D.Double(x, y); 
	}
	
	public double getX() { return rect.getX(); }
	
	public double getWidth() { return rect.getWidth(); }
	
	public void paintComponent(Graphics g) {
		if (hasBuilt()) {
			Graphics2D g2 = (Graphics2D) g;
			// CAS533 add so text is better in ImageViewer
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
	                            RenderingHints.VALUE_ANTIALIAS_ON);

			g2.setPaint((position % 2 == 0 ? bgColor1 : bgColor2)); 
			g2.fill(rect);
			g2.setPaint(border);
			g2.draw(rect);

			if (showRuler) {
				for (Rule rule : ruleList)
					rule.paintComponent(g2);
			}
			for (Annotation annot : allAnnoVec)
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

	public void mouseReleased(MouseEvent e) { 
		// Point p = e.getPoint(); The rect check was causing it to fail when zoomed in, and does not seem necessary
		if (drawingPanel.isMouseFunctionPop())  {
			if (dragRect.getHeight() > 0 /*&& rect.contains(p)*/) {
				int start = getBP( dragRect.getY() );
				int end =   getBP( dragRect.getY() + dragRect.getHeight() );
				
				if (flipped) {
					int temp = start;
					start = (int)getEnd() - end + (int)getStart();
					end = (int)getEnd() - temp + (int)getStart();
				}
				
				if (drawingPanel.isMouseFunctionCloseup()) showCloseupAlign(start, end); // XXX
				else showSequence(start, end); 
			}
		}
		super.mouseReleased(e); // call track.mouseReleased
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
	public boolean isQuery() { return isQuery; } // CAS531 add
	
	/**
	 * returns the desired text for when the mouse is over a certain point.
	 * Called from symap.frame.HelpBar mouseMoved event
	 */
	public String getHelpText(MouseEvent event) {
		Point p = event.getPoint();
		if (rect.contains(p)) { // within blue rectangle of track
			String x = null;
			int gIdx=0;
			for (Annotation annot : allAnnoVec) {
				if (annot.contains(p)) {
					if (x==null) {
						if (annot.isGap()) 			return annot.getLongDescription().trim();
						if (annot.isCentromere()) 	return annot.getLongDescription().trim();
						
						x = annot.getLongDescription().trim(); 
						gIdx = annot.getAnnoIdx();
					}
					else { // CAS517  inactive - messes up with overlapping genes 
						if (annot.isExon() && annot.getGeneIdx()==gIdx) {
							x += "\n" + annot.getLongDescription().trim(); 	// CAS512 add exon if available
							return x;
						}
					}
				}
			}
			if (x!=null) return x;
		}
		else {					// not within blue rectangle of track
			if (POPUP_ANNO) {	// CAS503 add - in yellow box; see end of Annotation.java and TextBox.java 
				for (Annotation annot : allAnnoVec) { 
					if (annot.boxContains(p)) 
						return "Right click for popup of description";
				}
			}
		}
		if (centGeneRect.contains(p)) return "   "; // CAS518 so it will not show any message in gene area
		return "Sequence Track (" + getTitle() + "):  " + HOVER_MESSAGE; 
	}
	public String getFullName() {
		return "Track #" + getHolder().getTrackNum() + " " + getProjectDisplayName() + " " + getName();
	}
	public String getTitle() {
		return getProjectDisplayName()+" "+getName();
	}
	
	/* Show Alignment */
	private void showCloseupAlign(int start, int end) {
		if (drawingPanel == null || drawingPanel.getCloseUp() == null) return;

		try {// The CloseUp panel created at startup and reused.
			if (end-start>CloseUp.MAX_CLOSEUP_BP) {// CAS531 add this check
				Utilities.showWarningMessage("Region greater than " + CloseUp.MAX_CLOSEUP_BP + ". Cannot align.");
				return;
			}
			Vector <HitData> hitList = new Vector <HitData> ();
			seqHitsObj.getHitsInRange(hitList, start, end, isQuery);
			if (hitList.size()==0) {
				 Utilities.showWarningMessage("No hits found in region. Cannot align without hits."); 
				 return;
			}
		
			int rc = drawingPanel.getCloseUp().showCloseUp(hitList, this, start, end, getOtherProjectName(), isQuery);
			
			if (rc < 0) Utilities.showWarningMessage("Error showing close up view."); 
		}
		catch (OutOfMemoryError e) { 
			Utilities.showOutOfMemoryMessage();
			drawingPanel.smake(); // redraw after user clicks "ok"
		}
	}
	
	/* CAS504 Add Show Sequence; CAS571 compared a pos & neg with TAIR 
	 * CAS531 add menu with 'hit', 'gene', 'exons', 'region'
	 * XXX annoVec find contains(start,end)
	 *     (seqHitsObj
	 **/
	private void showSequence(int start, int end) {
		try {
			if (end-start > 1000000) {
				String x = "Regions is greater than 1Mbp (" + (end-start+1) + ")";
				if (!Utilities.showContinue("Show Sequence", x)) return;
			}
			new TextShowSeq(drawingPanel, this, getProjectDisplayName(), getGroup(), start, end, isQuery);
		}
		catch (Exception e) {ErrorReport.print(e, "Error showing popup of sequence");}
	}
	/*************************************************
	 * In click in anno space: if click in yellow box or anno, popup info
	 * else pass to parent
	 */
	public void mousePressed(MouseEvent e) {
		if (e.isPopupTrigger()) { // CAS516 add popup on right click
			String title = getFullName();
			String chr = getProjectDisplayName() + " " + getName();
			Point p = e.getPoint();
			if (rect.contains(p)) { // within blue rectangle of track
				for (Annotation annot : allAnnoVec) { 
					if (annot.contains(p)) {	// in this gene annotation
						setGene(annot);	
						annot.popupDesc(getHolder(), title, chr);
						return;
					}
				}
			}
			// CAS503 add popUp description; created in Build
			if (POPUP_ANNO && showAnnot) { 					
				for (Annotation annot : allAnnoVec) { 		
					if (annot.boxContains(p)) {	// within gene's yellow box
						setGene(annot);	
						annot.popupDesc(null, title, chr); 
						return;
					}	
				}
			}
		}
		super.mousePressed(e);					// right click - blue area for menu
	}
	private void setGene(Annotation annot) {
		if (seqHitsObj==null) return; 
		if (!annot.hasHitList()) { // CAS517 added
			String hits = seqHitsObj.getHitsStr(this, annot.getStart(), annot.getEnd());
			annot.setHitList(hits);
		}
		annot.setExonList(allAnnoVec);
	}
	public String toString() {
		int tn = getHolder().getTrackNum();
		String ref = (tn%2==0) ? " REF " : " "; 
		return "[Sequence "+getFullName() + " Chr" + getGroup()+ ref + isQuery + "]";
	}
	
	/*************************************************************************/
	public static boolean POPUP_ANNO=false; // not working from Query/dotplot
	private static final double OFFSET_SPACE = 7;
	private static final double OFFSET_SMALL = 1;
	
	private static final int GENE_DIV = 4; // Gene is this much narrower than exon
	private static final int MIN_BP_FOR_ANNOT_DESC = 500; 

	public static final boolean DEFAULT_FLIPPED 		= false; 
	public static final boolean DEFAULT_SHOW_RULER      = true;
	public static final boolean DEFAULT_SHOW_GENE       = true;  
	public static final boolean DEFAULT_SHOW_GENE_LINE  = false;  
	public static       boolean DEFAULT_SHOW_ANNOT  	= false; 
	public static final boolean DEFAULT_SHOW_SCORE_LINE	= true;  
	public static final boolean DEFAULT_SHOW_SCORE_VALUE= false; 
	public static final boolean DEFAULT_SHOW_RIBBON		= true;	 
	public static final boolean DEFAULT_SHOW_GAP        = true;
	public static final boolean DEFAULT_SHOW_CENTROMERE = true;
	public static final boolean DEFAULT_SHOW_GENE_FULL  = true;  
	
	private static final Color border = Color.black; 
	private static Color footerColor = Color.black;
	private static Font footerFont = new Font("Ariel",0,10);
	private static Font unitFont = new Font("Ariel",0,10);;
	private static final Point2D defaultSequenceOffset = new Point2D.Double(20,40); 
	private static final Point2D titleOffset = new Point2D.Double(1,3); 
	private static int mouseWheelScrollFactor = 20;	
	private static int mouseWheelZoomFactor = 4; 	
	private static final double DEFAULT_WIDTH = 100.0;
	private static final double SPACE_BETWEEN_RULES = 35.0;
	private static final double RULER_LINE_LENGTH = 3.0;
	private static final double MIN_DEFAULT_BP_PER_PIXEL = 1;
	private static final double MAX_DEFAULT_BP_PER_PIXEL =1000000;
	private static final double PADDING = 100; 
	private static final double ANNOT_WIDTH = 15.0;
	private static final String HOVER_MESSAGE =  
					"\nHover on gene or right-click on gene for popup of full description.\n";
	
	public static Color unitColor;
	public static Color bgColor1; 
	public static Color bgColor2; 
	
	static { // CAS532 remove everything except colors that can be set by user
		PropertiesReader props = new PropertiesReader(SyMAP.class.getResource("/properties/sequence.properties"));
	
		unitColor                  = props.getColor("unitColor");
		bgColor1            	   = props.getColor("bgColor1");
		bgColor2            	   = props.getColor("bgColor2");
	}
}
