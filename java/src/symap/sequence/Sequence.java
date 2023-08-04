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
import java.util.Vector;
import java.util.HashMap;

import number.GenomicsNumber;
import props.PropertiesReader;
import symap.Globals;
import symap.closeup.TextShowSeq;
import symap.drawingpanel.ControlPanel;
import symap.drawingpanel.DrawingPanel;
import symap.mapper.HitData;
import symap.mapper.SeqHits;
import util.Utilities;
import util.ErrorReport;

/**
 * The Sequence track for a chromosome. Called from DrawingPanel
 * Has a vector of annotations, which it also draws. The hit is drawn in this rectangle by PseudoPseudoHits
 */
public class Sequence extends Track {
	// moved colors and constants to end
	private static final String HOVER_MESSAGE = 
			"Track Information:"
			+ "\n-Hover on gene for information."
			+ "\n-Right-click on gene for popup of full description."
			+ "\n-Right-click in non-gene track space for subset filter popup."
			+ "\n-Filters are NOT retained between displays of a session.";
	private String infoMsg="";
	
	// flags; Sequence filter settings
	protected boolean bShowRuler, bShowGene, bShowAnnot, bShowGeneLine; // bFlipped in Track
	protected boolean bShowGap, bShowCentromere;
	protected boolean bShowScoreLine, bShowHitLen; 	   // CAS512 renamed Ribbon to HitLen; show Hit on Seq rect
	protected boolean bShowScoreText, bShowHitNumText; // CAS531 add showHitNum
	protected boolean bShowBlockText, bShowCsetText, bShowNoText;   // CAS545 add
	protected boolean bHighGenePopup; 								// CAS544 add 
	protected boolean bHighConserved; 								// CAS545 add 
	protected Annotation selectedGeneObj=null;						// CAS545 add
	
	protected int grpIdx=Globals.NO_VALUE;
	protected String chrName;			// e.g. Chr01
	
	private SeqHits hitsObj1=null,  hitsObj2=null; // CAS517 add; CAS543 add Obj2 for 3-track
	private boolean isQuery1=false, isQuery2=false;// CAS517 add; CAS545 add 2; True => this sequence is Query (pseudo_hits.annot1_idx)
	
	private SeqPool seqPool;
	private Vector <Rule> ruleList; 	// CAS545 was List
	private Vector <Annotation> allAnnoVec;
	private Vector <Annotation> geneVec; // CAS545 for faster searching
	private TextLayout headerLayout, footerLayout;
	private Point2D.Float headerPoint, footerPoint;
	
	private HashMap <Integer, Integer> olapMap = new HashMap <Integer, Integer> (); // CAS517 added for overlapping genes; CAS518 global
	private final int OVERLAP_OFFSET=12;			// CAS517/518 for overlap and yellow text
	
	/** the Sequence object is created on first use, but then reused to all data can change
	 * on reuse, clear and reset is called 
	 */
	public Sequence(DrawingPanel dp, TrackHolder holder) {
		super(dp, holder, MIN_DEFAULT_BP_PER_PIXEL, MAX_DEFAULT_BP_PER_PIXEL, defaultSequenceOffset);
		this.seqPool = new SeqPool(dp.getDBC());
		
		ruleList = new Vector<Rule>(15,2);
		allAnnoVec = new Vector<Annotation>(1); // CAS545 was 50,1000; now alloc in init based on numAnnos
		geneVec = new Vector <Annotation> ();
		headerPoint = new Point2D.Float();
		footerPoint = new Point2D.Float();

		projIdx = Globals.NO_VALUE; // declared in Track
	}

	public void setOtherProject(int otherProject) {
		if (otherProject != this.otherProjIdx) setup(projIdx,grpIdx,otherProject);
	}

	public void setup(int project, int group, int otherProject) {
		if (this.grpIdx != group || this.projIdx != project || this.otherProjIdx != otherProject) { 
			reset(project,otherProject);	// method in Track
			this.grpIdx = group;
			if (this.otherProjIdx != Globals.NO_VALUE) init();
		}
		else reset();						// method in Track
		
		bShowGene       = Sfilter.bDefGene;
		bShowRuler      = Sfilter.bDefRuler;
		bShowGap        = Sfilter.bDefGap;
		bShowCentromere = Sfilter.bDefCentromere;
	
		bFlipped 		= Sfilter.bDefFlipped; 	
		bShowAnnot      = Sfilter.bDefAnnot;
		bShowGeneLine   = Sfilter.bDefGeneLine;
		
		bShowScoreLine  = Sfilter.bDefScoreLine;   
		bShowHitLen     = Sfilter.bDefHitLen; 
		
		bShowScoreText 	= Sfilter.bDefScoreText; 	
		bShowHitNumText = Sfilter.bDefHitNumText;
		bShowBlockText 	= Sfilter.bDefBlockText;
		bShowCsetText 	= Sfilter.bDefCsetText;
		bShowNoText 	= Sfilter.bDefNoText;
		
		bHighGenePopup  = Sfilter.bDefGeneHigh;
		bHighConserved 	= Sfilter.bDefConserved;
	}
	// Set the annotation vector here
	protected boolean init() {
		if (hasInit()) return true;
		if (projIdx < 1 || grpIdx < 1 || otherProjIdx < 1) return false;
		try {
			int n = seqPool.getNumAllocs(grpIdx);
			allAnnoVec = new Vector <Annotation>(n); // CAS545 alloc here instead
			
			chrName = seqPool.loadSeqData(this, size, allAnnoVec);	
			if (allAnnoVec.size() == 0) bShowAnnot = false; 
			else {
				for (Annotation aObj : allAnnoVec)
					if (aObj.isGene()) geneVec.add(aObj);
			}
		} catch (Exception s1) {
			ErrorReport.print(s1, "Initializing Sequence failed.");
			seqPool.close();
			return false;
		}	
		resetStart();
		resetEnd();
		setInit();

		return true;
	}
	// CAS517 add in order to have access to hits 
	public void setSeqHits(SeqHits pObj, boolean isSwapped) { 
		if (hitsObj1==null) {
			hitsObj1 = pObj;
			isQuery1 = isSwapped;
		}
		else {
			hitsObj2 = pObj;
			isQuery2 = isSwapped;
		}
	}
		
	public void setup(TrackData td) { // This gets called on History back/forward; CAS545 remove dead code
		TrackData sd = (TrackData)td;
		//if (td.getProject() != projIdx 	|| sd.getGroup() != grpIdx  || td.getOtherProject() != otherProjIdx)
		//	reset(td.getProject(),td.getOtherProject());
		
		sd.setTrack(this);
		firstBuild = false;
		//init(); 			
		//sd.setTrack(this);
	
		if (isRef()) forceConserved(); 	// CAS545 add
	}
	
	/** @see Track#clear() */
	public void clear() {
		ruleList.clear();
		allAnnoVec.clear();
		geneVec.clear();
		olapMap.clear();
		hitsObj1=hitsObj2=null;
				
		headerPoint.setLocation(0,0);
		footerPoint.setLocation(0,0);
		dragPoint.setLocation(0,0);	
		super.clear();
	}

	public void clearData() {
		ruleList.clear();
		allAnnoVec.clear();
		geneVec.clear();
	}

	public int getGroup() {return grpIdx;}

	public String getChrName() {
		String prefix =  drawingPanel.getProjPool().getProperty(getProject(),"grp_prefix");
		if (prefix==null) prefix = "";
		String grp = (chrName==null) ? "" : chrName;
		return prefix + grp;
	}	

	public void setAnnotation() {bShowAnnot=true;} // CAS543 changed from setting static from Query.TableDataPanel
	
	public TrackData getData() {return new TrackData(this);}

	/**
	 * XXX Build layout: 
	 * Sets up the drawing objects for this sequence. Called by DrawingPanel and Track
	 * Start and end are Track GenomicsNumber variables, stating start/end 
	 * The Hit Length, Hit %Id Value and Bar are drawn in mapper.SeqHits.DrawHits.paintHitLen and its paintComponent
	 */
	public boolean build() {
		if (hasBuilt()) return true;
		if (!hasInit()) return false;

		if (allAnnoVec.size() == 0) bShowAnnot = false;
			
		if (width == Globals.NO_VALUE) width = DEFAULT_WIDTH;

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
			Utilities.showWarningMessage("Unable to size sequence view. Try again."); 				
			adjustSize();
		}

		GenomicsNumber length = new GenomicsNumber(this,end.getValue() - start.getValue());
		rect.setRect(trackOffset.getX(), trackOffset.getY(), width, length.getPixelValue()); // chr rect
		
		totalRect.setRect(0, 0, rect.x + rect.width + 2, rect.y + rect.height + 2);
		frc = getFontRenderContext();

	// Setup the sequence ruler
		ruleList.clear();
		if (bShowRuler) {
			if (orient == Globals.LEFT_ORIENT) {
				x1 = rect.x - OFFSET_SMALL;
				x2 = x1 - RULER_LINE_LENGTH;
			} else if (orient == Globals.RIGHT_ORIENT) {
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
				
				cb = GenomicsNumber.getCbPerPixel(bpPerCb,bpPerPixel,(bFlipped ? h-y : y-rect.y)) + start.getValue(); 
				layout = new TextLayout(GenomicsNumber.getFormatted(bpSep,bpPerCb,cb,bpPerPixel), unitFont, frc);
				bounds = layout.getBounds();
				ty = y + (bounds.getHeight() / 2.0);
				totalRect.height = Math.max(totalRect.height, ty);
				totalRect.y = Math.min(totalRect.y, ty);
				if (orient == Globals.RIGHT_ORIENT) {
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

	// Setup the annotations
		boolean isRight = (orient == Globals.RIGHT_ORIENT);
		double lastStart=0;
		
		getHolder().removeAll(); 
		Rectangle2D centRect = new Rectangle2D.Double(rect.x+1,rect.y,rect.width-2,rect.height);
		
		if (olapMap.size()==0) seqPool.buildOlap(olapMap, geneVec); // builds first time only
			
	// XXX Sorted by genes (genenum, start), then exons (see PseudoData.setAnnotations)
	    for (Annotation annot : allAnnoVec) {
	    	if (((annot.isGene() || annot.isExon()) && !bShowGene) 
	    			|| (annot.isGap() && !bShowGap)
	    			|| (annot.isCentromere() && !bShowCentromere)){
					annot.clear();
					continue; // CAS520 v518 bug, does not display track if, return true;
			}
	    	
			double dwidth = rect.width, hwidth=0; // displayWidth, hoverWidth for gene
			int offset=0;
			
			if (annot.isGene()) {
				hwidth = ANNOT_WIDTH;
				dwidth = ANNOT_WIDTH/GENE_DIV; 
				if (olapMap.containsKey(annot.getAnnoIdx())) offset = olapMap.get(annot.getAnnoIdx());
				
			}
			else if (annot.isExon()) { // CAS543 remove || annot.isSyGene()) {
				dwidth = ANNOT_WIDTH;
				if (olapMap.containsKey(annot.getGeneIdx())) offset = olapMap.get(annot.getGeneIdx());
			}
			if (offset>0 && isRight) offset = -offset;
			
		/****/
			annot.setRectangle(centRect, start.getBPValue(), end.getBPValue(),
					bpPerPixel, dwidth, hwidth, bFlipped, offset, bShowGeneLine, bHighGenePopup); // CAS520 add showGeneLine; CAS544 add popup
			
		// Setup yellow description
			if (annot.isGene() && bShowAnnot && annot.isVisible()) { // CAS517 added isGene
				if (annot.hasShortDescription() && getBpPerPixel() < MIN_BP_FOR_ANNOT_DESC)  { 
					x1 = isRight ? (rect.x + rect.width + RULER_LINE_LENGTH + 2) : rect.x; 
					x2 = (isRight ? x1 + RULER_LINE_LENGTH  : x1 - RULER_LINE_LENGTH);
					
					ty = annot.getY1();
					if (lastStart==ty) ty = ty+OVERLAP_OFFSET;
					lastStart=ty;
					
					TextBox tb = new TextBox(annot.getVectorDescription(),unitFont, (int)x1, (int)ty, 40, 200);
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
	
	// Set header and footer
		bounds = new Rectangle2D.Double(0, 0, 0, 0); 							
		x = (rect.x + (rect.width / 2.0)) - (bounds.getX() + (bounds.getWidth() / 2.0));
		if (bFlipped) 
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
		if (bFlipped) 
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
				
	// Offset everything
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
	
	/***********************************************************************************/
	public Point2D getPoint(long bpPos, int trackPos) {
		if (bpPos < start.getValue()) bpPos = start.getValue(); 
		if (bpPos > end.getValue())   bpPos = end.getValue(); 	
		double x = (trackPos == Globals.LEFT_ORIENT ? rect.x+rect.width : rect.x);			
		double y = rect.y + GenomicsNumber.getPixelValue(bpPerCb,bpPos-start.getValue(),bpPerPixel);
		
		if (bFlipped) y = rect.y + rect.y + rect.height - y;	
		
		return new Point2D.Double(x, y); 
	}
	
	public double getX() { return rect.getX(); }
	
	public double getWidth() { return rect.getWidth(); }
	
	public void paintComponent(Graphics g) {
		if (hasBuilt()) {
			Graphics2D g2 = (Graphics2D) g;
	        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); // CAS535
	        
			g2.setPaint((position % 2 == 0 ? bgColor1 : bgColor2)); 
			g2.fill(rect);
			g2.setPaint(border);
			g2.draw(rect);

			if (bShowRuler) {
				for (Rule rule : ruleList)
					rule.paintComponent(g2);
			}
			int cntShow=0, cntCon=0;
			for (Annotation annot : allAnnoVec)
				if (annot.isVisible()) {
					annot.paintComponent(g2); 
					if (annot.isGene()) {
						cntShow++;
						if (annot.isConserved()) cntCon++;
					}
				}
			infoMsg= String.format("Genes: %,d", cntShow);
			if (Globals.TRACE && cntCon>0) infoMsg += String.format("\nConserved: %,d", cntCon); // CAS545 add
			
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
				
				if (bFlipped) {
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
		if (bFlipped) {
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
		
		if (!(drawingPanel.isMouseFunctionCloseup() && getCursor().getType() != Cursor.S_RESIZE_CURSOR)) return;
		
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
			int mh = (int)Math.round(Globals.MAX_CLOSEUP_BP / bpPerPixel);		
			if (happened == 1) {
				if (dragRect.height * bpPerPixel > Globals.MAX_CLOSEUP_BP) {
					dragRect.height = mh;
					if (dragRect.height+dragRect.y < dragPoint.y)
						dragPoint.y = dragRect.height+dragRect.y;
				}
			}
			else { 
				if (dragRect.height * bpPerPixel > Globals.MAX_CLOSEUP_BP) {
					dragRect.y += dragRect.height - mh;
					dragRect.height = mh;
					if (dragPoint.y < dragRect.y)
						dragPoint.y = dragRect.y;
				}
			}
		}
	}

	protected Point2D getTitlePoint(Rectangle2D titleBounds) {	
		Rectangle2D headerBounds = (bFlipped ? footerLayout.getBounds() : /*headerLayout.getBounds()*/new Rectangle2D.Double(0, 0, 0, 0)); 
		Point2D headerP = (bFlipped ? footerPoint : headerPoint); 
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
	
	public boolean isFlipped() { return bFlipped; }
	public boolean isQuery() { return isQuery1; } // CAS531 add;
	
	/* Show Alignment */
	private void showCloseupAlign(int start, int end) {
		if (drawingPanel == null || drawingPanel.getCloseUp() == null) return;

		try {// The CloseUp panel created at startup and reused.
			if (end-start>Globals.MAX_CLOSEUP_BP) {// CAS531 add this check
				Utilities.showWarningMessage("Region greater than " + Globals.MAX_CLOSEUP_K + ". Cannot align.");
				return;
			}
			Vector <HitData> hitList1 = new Vector <HitData> ();
			Vector <HitData> hitList2 = new Vector <HitData> ();
			
			// one side
			int numShow=1;
			hitsObj1.getHitsInRange(hitList1, start, end, isQuery1);
			if (hitList1.size()>0) {
				int rc = drawingPanel.getCloseUp().showCloseUp(hitList1, this, start, end, 
						isQuery1, hitsObj1.getOtherChrName(this), numShow++);
				if (rc < 0) Utilities.showWarningMessage("Error showing close up view."); 
			}
			// possible other side CAS543 add
			if (hitsObj2!=null) { 
				hitsObj2.getHitsInRange(hitList2, start, end, isQuery2);
				if (hitList2.size()>0) {
					int rc = drawingPanel.getCloseUp().showCloseUp(hitList2, this, start, end, 
						isQuery2, hitsObj2.getOtherChrName(this), numShow);
					if (rc < 0) Utilities.showWarningMessage("Error showing close up view."); 
				}
			}
			
			if (hitList1.size()==0 && hitList2.size()==0) {
				 Utilities.showWarningMessage("No hits found in region. Cannot align without hits."); 
				 return;
			}	
		}
		catch (OutOfMemoryError e) { 
			Utilities.showOutOfMemoryMessage();
			drawingPanel.smake(); // redraw after user clicks "ok"
		}
	}
	
	/* CAS504 Add Show Sequence; CAS571 compared a pos & neg with TAIR 
	 * CAS531 add menu with 'hit', 'gene', 'exons', 'region' */
	private void showSequence(int start, int end) {
		try {
			if (end-start > 1000000) {
				String x = "Regions is greater than 1Mbp (" + (end-start+1) + ")";
				if (!Utilities.showContinue("Show Sequence", x)) return;
			}
			new TextShowSeq(drawingPanel, this, getProjectDisplayName(), getGroup(), start, end, isQuery1);
		}
		catch (Exception e) {ErrorReport.print(e, "Error showing popup of sequence");}
	}
	/**
	 * XXX returns the desired text for when the mouse is over a certain point.
	 * Called from symap.frame.HelpBar mouseMoved event
	 */
	public String getHelpText(MouseEvent event) {
		int n = drawingPanel.getStatOpts();
		if (n==ControlPanel.pHELP) return HOVER_MESSAGE;
		
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
					else { // CAS517  messes up with overlapping genes 
						if (annot.isExon() && annot.getGeneIdx()==gIdx) {
							x += "\n" + annot.getLongDescription().trim(); 	// CAS512 add exon if available
							return x;
						}
					}
				}
			}
			if (x!=null) return x;
		}
		
		return getTitle() + " \n\n" + infoMsg; 
	}
	
	/*****************************************************************************/
	// Called by CloseUpDialog and TextShowSeq (gene only); type is Gene or Exon
	public Vector<Annotation> getAnnoGene(int start, int end) {// CAS535 gene only;
		Vector<Annotation> out = new Vector<Annotation>();
		
		for (Annotation a : geneVec) {
			if (isOverlapping(start, end, a.getStart(), a.getEnd()))
				out.add(a);
		}	
		return out;
	}
	public Vector<Annotation> getAnnoExon(int gene_idx) {// CAS535 was getting exons from other genes
		Vector<Annotation> out = new Vector<Annotation>();
		
		for (Annotation a : allAnnoVec) {
			if (a.isExon() && a.getGeneIdx()==gene_idx)
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
	public String getGeneNumFromIdx(int idx1, int idx2) { // CAS545 called from HitData for its popup
		if (idx1>0) {
			for (Annotation aObj : geneVec) {
				if (aObj.getAnnoIdx()==idx1) return " (# " + aObj.getFullGeneNum() + ")";
			}
		}
		if (idx2>0) {
			for (Annotation aObj : geneVec) {
				if (aObj.getAnnoIdx()==idx2) return " (# " + aObj.getFullGeneNum() + ")";;
			}
		}
		return "";
	}
	/*************************************************
	 * In click in anno space: if click on gene, popup info; else pass to parent
	 */
	public void mousePressed(MouseEvent e) {
		if (e.isPopupTrigger()) { // CAS516 add popup on right click
			String title = getTitle(); // DisplayName Chr
			Point p = e.getPoint();
			if (rect.contains(p)) { // within blue rectangle of track
				for (Annotation annot : allAnnoVec) { 
					if (annot.contains(p)) {	// in this gene annotation
						setExonList(annot);	
						annot.popupDesc(getHolder(), title, title);
						if (bHighGenePopup) drawingPanel.smake(); // CAS544 to highlight
						return;
					}
				}
			}
		}
		super.mousePressed(e);					// right click - blue area for menu
	}
	private void setExonList(Annotation annot) { // Called above on mousePressed
		if (hitsObj1!=null) {// CAS517 added
			if (!annot.hasHitList()) { 
				String hits = hitsObj1.getHitsStr(this, annot.getStart(), annot.getEnd());
				annot.setHitList(hits);
			}
		}
		if (hitsObj2!=null) {// CAS543 added
			if (!annot.hasHitList2()) { 
				String hits = hitsObj2.getHitsStr(this, annot.getStart(), annot.getEnd());
				annot.setHitList2(hits);
			}
		}
		annot.setExonList(allAnnoVec);
	}
	public String getFullName() {
		return "Track #" + getHolder().getTrackNum() + " " + getProjectDisplayName() + " " + getChrName();
	}
	public String getTitle() {
		return getProjectDisplayName()+" "+getChrName();
	}
	public String toString() {
		int tn = getHolder().getTrackNum();
		String ref = (tn%2==0) ? " REF " : " "; 
		return "[Sequence "+ getFullName() + " (Grp" + getGroup() + ") " + getOtherProjectName() + ref + "]";
	}
	
	/************************************************
	 *  XXX Called when Sfilter changes and DrawingPanel
	 */
	public boolean getShowAnnot() 		{ return bShowAnnot;}
	public boolean getShowHitLen() 		{ return bShowHitLen; }
	public boolean getShowScoreLine() 	{ return bShowScoreLine;}
	public boolean getShowScoreText() 	{ return bShowScoreText; }
	public boolean getShowHitNumText() 	{ return bShowHitNumText; }
	public boolean getShowBlockText()  	{ return bShowBlockText; }
	public boolean getShowCsetText()   	{ return bShowCsetText; }
	public boolean getHighConserved()	{ return bHighConserved; }
	
	public int[] getAnnotationTypeCounts() {
		int[] counts = new int[Annotation.numTypes];
		
		for (Annotation a : allAnnoVec)
			counts[a.getType()]++;
		
		return counts;
	}
		
	public boolean flipSeq(boolean flip) {
		if (bFlipped != flip) {
			bFlipped = flip;
			clearTrackBuild();
			return true;
		}
		return false;
	}
	public boolean showRuler(boolean show) {
		if (bShowRuler != show) {
			bShowRuler = show;
			clearTrackBuild();
			return true;
		}
		return false;
	}
	public boolean showGene(boolean show) {
		if (bShowGene != show) {
			bShowGene = show;
			clearTrackBuild();
			return true;
		}
		return false;
	}
	public boolean showGeneLine(boolean show) {// CAS520 add
		if (bShowGeneLine != show) {
			bShowGeneLine = show;
			clearTrackBuild();
			return true;
		}
		return false;
	}
	public boolean highGenePopup(boolean high) {
		if (bHighGenePopup != high) {
			bHighGenePopup = high;
			if (!high) 
				for (Annotation aObj : allAnnoVec) aObj.setIsPopup(false); // exons are highlighted to, so use all
			clearTrackBuild();
			return true;
		}
		return false;
	}
	public boolean showGap(boolean show) {
		if (bShowGap != show) {
			bShowGap = show;
			clearTrackBuild();
			return true;
		}
		return false;
	}
	public boolean showCentromere(boolean show) {
		if (bShowCentromere != show) {
			bShowCentromere = show;
			clearTrackBuild();
			return true;
		}
		return false;
	}
	public boolean showAnnotation(boolean show) {
		if (allAnnoVec.size() == 0) {
			bShowAnnot = false;
			return false;
		}
		if (bShowAnnot != show)  {
			bShowAnnot = show;
			clearTrackBuild();
			return true;
		}
		return false;
	}
	public boolean showScoreLine(boolean show) { 
		if (bShowScoreLine != show) {
			bShowScoreLine = show;
			clearTrackBuild();
			return true;
		}
		return false;
	}
	// drawn SeqHits.DrawHits.paintHitLen
	public boolean showHitLen(boolean show) { // hit ribbon - graphics on sequence rectangle
		if (bShowHitLen != show) {
			bShowHitLen = show;
			clearTrackBuild();
			return true;
		}
		return false;
	}
	public boolean showScoreText(boolean show) { 
		if (bShowScoreText != show) {
			setText(false, false, false, show);
			clearTrackBuild();
			return true;
		}
		return false;
	}
	public boolean showHitNumText(boolean show) { // CAS531 add
		if (bShowHitNumText != show) {
			setText(false, false, show, false);
			clearTrackBuild();
			return true;
		}
		return false;
	}
	public boolean showBlockText(boolean show) { // CAS545 add
		if (bShowBlockText != show) {
			setText(show, false, false, false);
			clearTrackBuild();
			return true;
		}
		return false;
	}
	public boolean showCsetText(boolean show) { // CAS545 add
		if (bShowCsetText != show) {
			setText(false, show, false, false);
			clearTrackBuild();
			return true;
		}
		return false;
	}
	public boolean showNoText(boolean show) { // CAS545 add
		if (bShowNoText != show) {
			setText(false, false, false, false);
			clearTrackBuild();
			return true;
		}
		return false;
	}
	private void setText(boolean b, boolean c, boolean h, boolean s) {
		bShowBlockText	=b;
		bShowCsetText	=c;
		bShowHitNumText	=h;
		bShowScoreText	=s;
	
		if (!b&&!c&&!s&&!h) bShowNoText=true;
		else bShowNoText=false;
	}
	public void noSelectedGene() {
		if (selectedGeneObj!=null) {
			selectedGeneObj.setIsSelectedGene(true);
			selectedGeneObj=null;
		}
	}
	public int getMidCoordForGene(String name) { // CAS545 filter on gene#
		int mid = -1;
		for (Annotation aObj : geneVec) {
			mid = aObj.isMatchGeneN(name);
			if (mid>0) {
				selectedGeneObj = aObj;
				aObj.setIsSelectedGene(true);
				return mid;
			}
		}
		return mid;
	}
	public void setHighforHit(int annot1_idx, int annot2_idx, boolean isHigh) { // CAS545 highlight gene of hit
		for (Annotation aObj : geneVec) {
			int idx = aObj.getAnnoIdx();
			if (idx==annot1_idx || idx==annot2_idx) {
				aObj.setIsHitPopup(isHigh); 
				return;
			}
		}
	}
	/******************************************************************/
	// used for conserved to highlight genes at ends of hit; CAS545 add
	public void setConservedforHit(int annot1_idx, int annot2_idx, boolean isHigh) {
		for (Annotation aObj : geneVec) {
			int idx = aObj.getAnnoIdx();
			if (idx==annot1_idx || idx==annot2_idx) {
				aObj.setIsConserved(isHigh); 
				return;
			}
		}
	}
	public Vector <Integer> getGeneIdx() {
		Vector <Integer> idxVec = new Vector <Integer> ();
		for (Annotation annot : geneVec)  idxVec.add(annot.getAnnoIdx());
		return idxVec;
	}
	public void clearConserved() {
		for (Annotation ag : geneVec) ag.setIsConserved(false);
	}
	
	private void forceConserved()  {// see reset() for history
		if (bHighConserved) 
			seqPool.flagConserved(hitsObj1, hitsObj2, this);
		else { 
			if (hitsObj1!=null) hitsObj1.clearConserved(); 
			if (hitsObj2!=null) hitsObj2.clearConserved();
		}
	}
	// Sfilter.FilterListener;  sets isConserved in HitData and Annotation
	public boolean highConserved(boolean high) {
		if (bHighConserved==high) return false;
		
		bHighConserved = high;
		if (high) seqPool.flagConserved(hitsObj1, hitsObj2, this);
		else { // hit conserve clears its end annos
			if (hitsObj1!=null) hitsObj1.clearConserved();
			if (hitsObj2!=null) hitsObj2.clearConserved();
		}
		return true;
	}
	/*************************************************************************/
	private static final double MOUSE_PADDING = 2;

	private static final double OFFSET_SPACE = 7;
	private static final double OFFSET_SMALL = 1;
	
	private static final int GENE_DIV = 4; // Gene is this much narrower than exon
	private static final int MIN_BP_FOR_ANNOT_DESC = 500; 
	
	private static final Color border = Color.black; 
	private static Color footerColor = Color.black;
	private static Font footerFont = new Font("Arial",0,10);
	private static Font unitFont = new Font("Arial",0,10);
	private static final Point2D defaultSequenceOffset = new Point2D.Double(20,40); 
	private static final Point2D titleOffset = new Point2D.Double(1,3); 
	private static int mouseWheelScrollFactor = 20;	
	private static int mouseWheelZoomFactor = 4; 	
	private static final double DEFAULT_WIDTH = 100.0;
	private static final double SPACE_BETWEEN_RULES = 35.0;
	private static final double RULER_LINE_LENGTH = 3.0;
	private static final double MIN_DEFAULT_BP_PER_PIXEL = 1;
	private static final double MAX_DEFAULT_BP_PER_PIXEL =1000000;
	
	private static final double ANNOT_WIDTH = 15.0;
	
	public static Color unitColor;
	public static Color bgColor1; 
	public static Color bgColor2; 
	
	static { // CAS532 remove everything except colors that can be set by user
		PropertiesReader props = new PropertiesReader(Globals.class.getResource("/properties/sequence.properties"));
	
		unitColor                  = props.getColor("unitColor");
		bgColor1            	   = props.getColor("bgColor1");
		bgColor2            	   = props.getColor("bgColor2");
	}
}
