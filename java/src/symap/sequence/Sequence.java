package symap.sequence;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Vector;

import javax.swing.JComponent;

import java.util.HashMap;
import java.util.TreeMap;

import props.ProjectPool;
import props.PropertiesReader;
import symap.Globals;
import symap.closeup.TextShowSeq;
import symap.drawingpanel.DrawingPanel;
import symap.frame.HelpListener;
import symap.mapper.HitData;
import symap.mapper.SeqHits;
import util.Utilities;
import util.ErrorReport;

/**
 * The Sequence track for a chromosome. Called from DrawingPanel
 * Has a vector of annotations, which it also draws. The hit is drawn in this rectangle by PseudoPseudoHits
 * Sequence & TrackHolder are 1-1; get replaced with different sequence unless a current sequence uses. 
 * 
 * CAS550 merge Track Abstract and rearrange; Tract.start and Tract.end renamed gnDstart (genomic number, Displayed)
 * 		Track - all assignments moved to declarations
 * 		DrawingPanel was changed to create new TrackHolders/Sequence for each new draw.
 * 		Changed GenomeNumbers to longs (removed package number, and performed formatting in new BpNumber)
 * 		There is still more cleanup to be done, but...
 */
public class Sequence implements HelpListener, KeyListener,MouseListener,MouseMotionListener,MouseWheelListener {
	// moved colors and constants to end
	private static final int REFPOS=2;
	
	private String infoMsg="";
	
	// flags; Sequence filter settings
	protected boolean bShowRuler, bShowGene, bShowAnnot, bShowGeneLine; // bFlipped in Track
	protected boolean bShowGap, bShowCentromere;
	protected boolean bShowScoreLine, bShowHitLen; 	   				// CAS512 renamed Ribbon to HitLen; show Hit on Seq rect
	protected boolean bShowScoreText, bShowHitNumText; 				// CAS531 add showHitNum
	protected boolean bShowBlockText, bShowCsetText, bShowNoText;   // CAS545 add
	protected boolean bHighGenePopup,bHighConserved; 				// CAS544 add 
	protected Annotation selectedGeneObj=null;						// CAS545 add
	
	protected int grpIdx=Globals.NO_VALUE;
	protected String chrName;						// e.g. Chr01
	
	private SeqHits hitsObj1=null,  hitsObj2=null; // CAS517 add; CAS543 add Obj2 for 3-track
	private boolean isQuery1=false, isQuery2=false;// CAS517 add; CAS545 add 2; True => this sequence is Query (pseudo_hits.annot1_idx)
	
	private SeqPool seqPool;
	private Vector <Rule> ruleList=  new Vector<Rule>(15,2); 			// CAS545 was List
	private Vector <Annotation> allAnnoVec = new Vector<Annotation>(1); // include exons, CAS545 needs initial size; see loadAnnoFromDB
	private Vector <Annotation> geneVec = new Vector <Annotation> (); 	// CAS545 added vector for faster searching
	private Point2D.Float headerPoint = new Point2D.Float(), footerPoint = new Point2D.Float();
	private TextLayout headerLayout = null, footerLayout = null;
	
	private HashMap <Integer, Integer> olapMap = new HashMap <Integer, Integer> (); // CAS517 added for overlapping genes; CAS518 global
	private final int OVERLAP_OFFSET=18;			// CAS517/518 for overlap and yellow text; CAS548 was 12
	
	/******* Track ************************************/
	protected int projIdx=Globals.NO_VALUE, otherProjIdx=Globals.NO_VALUE;
	private String projectName, displayName, otherProjName;
	protected long gnSize = 0;  				// full sequence size
	private int position;						// track position
	protected int orient = Globals.LEFT_ORIENT; // right (-1) center(0) left (1)
	private Color bgColor; 	
	private TextLayout titleLayout;
	
	private Rectangle2D.Double rect = new Rectangle2D.Double();
	private Rectangle dragRect = new Rectangle();
	private Point dragPoint = new Point(), trackOffset= new Point();
	
	private Point startMoveOffset = new Point(), adjustMoveOffset = new Point();
	private Point2D.Float titlePoint = new Point2D.Float();
	private Point2D defaultTrackOffset = defaultSequenceOffset;
	
	protected Point moveOffset = new Point();
	protected double defaultBpPerPixel=MIN_DEFAULT_BP_PER_PIXEL, bpPerPixel=MIN_DEFAULT_BP_PER_PIXEL;
	protected long gnDstart = 0, gnDend = 0;  	// displayed start, end; changes with zoom 
	
	protected Dimension dimension = new Dimension();
	protected double height=Globals.NO_VALUE, width=Globals.NO_VALUE;
	
	private double minDefaultBpPerPixel=MIN_DEFAULT_BP_PER_PIXEL, 
			       maxDefaultBpPerPixel=MAX_DEFAULT_BP_PER_PIXEL, 
			       startResizeBpPerPixel=Globals.NO_VALUE;
	private long curCenter=0, curWidth=0; // for +/- buttons
	
	private boolean hasLoad, hasBuild, firstBuild = true, bFlipped;
	
	private DrawingPanel drawingPanel;
	private TrackHolder holder;
	private ProjectPool projPool;
	private void dprt(String msg) {symap.Globals.dprt("SEQ " + msg);}

	/** the Sequence object is created when 2D is started; for explorer, reused with different sequences */
	public Sequence(DrawingPanel dp, TrackHolder holder) {
		this.drawingPanel = dp;
		this.holder = holder;
		
		if (holder.getTrack() != null) this.bgColor = holder.getTrack().bgColor; // maintain color when "back to block view" selected
		if (holder.getTrack() != null) this.position = holder.getTrack().position; // maintain position when "back to block view" selected
		
		projPool = dp.getProjPool();
		
		this.seqPool = 	new SeqPool(dp.getDBC());
	}
	
	public void setup(int projIdx, int grpIdx) { // Sequence, Drawing panel
		this.projIdx = projIdx;
		displayName = projPool.getDisplayName(projIdx);
		projectName = projPool.getName(projIdx);
		if (Utilities.isEmpty(displayName)) displayName=projectName; // CAS534 need for new projects
		
		this.grpIdx = grpIdx;
		
		loadAnnosFromDB();	
		
		trackOffset.setLocation((int)defaultTrackOffset.getX(),(int)defaultTrackOffset.getY());
		moveOffset.setLocation(0,0);
		dragPoint.setLocation(0,0);
		titlePoint.setLocation(0,0);
		dimension.setSize(0,0);
		startMoveOffset.setLocation(Globals.NO_VALUE,Globals.NO_VALUE);
		adjustMoveOffset.setLocation(Globals.NO_VALUE,Globals.NO_VALUE);
		dragRect.setRect(0,0,Globals.NO_VALUE,Globals.NO_VALUE);;
		
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
	private boolean loadAnnosFromDB() { // resetData, setup
		try {
			int n = seqPool.getNumAllocs(grpIdx);
			allAnnoVec = new Vector <Annotation>(n); // CAS545 alloc here instead
			
			gnSize = seqPool.loadGenomeSize(this);
			chrName = seqPool.loadSeqData(this, allAnnoVec); // Add annotations to allAnnoVec, and gnSize value
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

		dprt(String.format("loadAnnos p=%s #anno=%d, %d ", chrName, allAnnoVec.size(), gnSize));
		gnDstart=0;
		gnDend=gnSize;
		hasLoad = true;

		return true;
	}
	public void setOtherProject(int otherProjIdx) { // Drawing panel
		otherProjName = projPool.getDisplayName(otherProjIdx);
		this.otherProjIdx = otherProjIdx;
		if (Utilities.isEmpty(otherProjName)) otherProjName=projPool.getName(otherProjIdx);
	}
	
	// CAS517 add in order to have access to hits; called once for each sequence
	public void setSeqHits(SeqHits pObj, boolean isSwapped) {  // mapper.seqHits
		if (hitsObj1==null) {
			hitsObj1 = pObj;
			isQuery1 = isSwapped;
		}
		else { // if a track is on both sides, this is the other side
			hitsObj2 = pObj;
			isQuery2 = isSwapped;
		}
	}
		
	protected void setup(TrackData td) { // TrackHolder.setTrackData; This gets called on History back/forward; CAS545 remove dead code
		TrackData sd = (TrackData)td;
		
		sd.setTrack(this);
		firstBuild = false;
		
		if (isRef()) forceConserved(); 	// CAS545 add
	}

	/**
	 * XXX Build layout: 
	 * Sets up the drawing objects for this sequence. Called by DrawingPanel and Track
	 * Start and end are Track GenomicsNumber variables, stating start/end 
	 * The Hit Length, Hit %Id Value and Bar are drawn in mapper.SeqHits.DrawHits.paintHitLen and its paintComponent
	 */
	public boolean build() { // DrawingPanel.buildAll & firstViewBuild; Sequence.mouseDragged
		dprt("build FT hasBuild " + hasBuild + " hasLoad" +  hasLoad + " Proj " + displayName+ " Chr" + chrName);
		if (hasBuild) return true;
		if (!hasLoad) return false;

		if (allAnnoVec.size() == 0) bShowAnnot = false;
			
		if (width == Globals.NO_VALUE) width = DEFAULT_WIDTH;

		setFullBpPerPixel();

		TextLayout layout;
		FontRenderContext frc;
		Rectangle2D bounds;
		double x, y, h, x1, x2, tx, ty;
		Rectangle2D.Double totalRect = new Rectangle2D.Double();

		if (height > 0) {
			long diff = Math.abs(gnDend-gnDstart);
			double bp = BpNumber.getBpPerPixel(diff, height);
			if (bp > 0) bpPerPixel = bp;
		}
		// this started happening with later Java versions if they click too fast
		if (!validSize()) { // CAS521 make popup
			Utilities.showWarningMessage("Unable to size sequence view. Try again. (" + gnDstart + "," + gnDend + ")"); 				
			adjustSize();
		}
		long len = gnDend - gnDstart;
		rect.setRect(trackOffset.getX(), trackOffset.getY(), width, getPixelValue(len)); // chr rect
		
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
				
				cb = BpNumber.getCbPerPixel(bpPerPixel, (bFlipped ? h-y : y-rect.y)) + gnDstart; 
				layout = new TextLayout(BpNumber.getFormatted(bpSep,cb,bpPerPixel), unitFont, frc);
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
		int lastInc=1;
		
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
			annot.setRectangle(centRect, gnDstart, gnDend,
					bpPerPixel, dwidth, hwidth, bFlipped, offset, bShowGeneLine, bHighGenePopup); // CAS520 add showGeneLine; CAS544 add popup
			
		// Setup yellow description
			if (annot.isGene() && bShowAnnot && annot.isVisible()) { // CAS517 added isGene
				if (annot.hasDesc() && bpPerPixel < MIN_BP_FOR_ANNOT_DESC)  { 
					x1 = isRight ? (rect.x + rect.width + RULER_LINE_LENGTH + 2) : rect.x; 
					x2 = (isRight ? x1 + RULER_LINE_LENGTH  : x1 - RULER_LINE_LENGTH);
					
					ty = annot.getY1();
					if (lastStart+OVERLAP_OFFSET>ty) {//CAS548; was incrementing y, now increment x so still line up
						x1 = x1 + (lastInc*OVERLAP_OFFSET); 
						lastInc++;
						if (lastInc>=4) lastInc=1;
					}
					else lastInc=1;
					lastStart=ty;
					
					TextBox tb = new TextBox(annot.getYellowBoxDesc(),unitFont, (int)x1, (int)ty); // CAS548 use new TextBox
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

		footerLayout = new TextLayout(BpNumber.getFormatted(gnDend-gnDstart+1, bpPerPixel)+
				               " of "+BpNumber.getFormatted(gnSize, bpPerPixel), footerFont, frc); 
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
		hasBuild = true;
		firstBuild = false;
		
		return true;
	}
	protected void setTitle() {// Sequence.build
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
	private Point2D getTitlePoint(Rectangle2D titleBounds) {	
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

	/***********************************************************************************/
	public void paintComponent(Graphics g) { // TrackHolder.paintComponent
		if (!hasBuild) return;
		
		Graphics2D g2 = (Graphics2D) g;
		
		// Sequence code
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

		if (headerLayout != null)	headerLayout.draw(g2, headerPoint.x, headerPoint.y);
		if (footerLayout != null)	footerLayout.draw(g2, footerPoint.x, footerPoint.y);
		
		// Track code
		if (position % 2 == 0)	g2.setPaint(REFNAME);
		else					g2.setPaint(titleColor);
		
		if (titleLayout != null) titleLayout.draw(g2, titlePoint.x, titlePoint.y);

		if (!isCleared(dragRect)) {
			g2.setPaint(dragColor);
			g2.fill(dragRect);
			g2.setPaint(dragBorder);
			g2.draw(dragRect);
		}	
	}
	
	public void mouseReleased(MouseEvent e) { 
		// Sequence
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
		// Track
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
							newStart = gnDend - newEnd + gnDstart;
							newEnd = gnDend - temp + gnDstart;
						}
						setEndBP(newEnd, true);
						setStartBP(newStart, true);
						
						if (drawingPanel.isMouseFunctionZoomAll())
							drawingPanel.zoomAllTracks(this, (int)newStart, (int)newEnd);
						
						if (!hasBuild) {
							clearHeight(); // want to resize to screen
							needUpdate = true;
							if (drawingPanel != null) drawingPanel.smake();
						}
					}
				} catch (Exception ex) {ErrorReport.print(ex, "Exception resizing track!");}
			}
			if (needUpdate  || (startResizeBpPerPixel != Globals.NO_VALUE && startResizeBpPerPixel != bpPerPixel) 
							|| (!isCleared(startMoveOffset) && !startMoveOffset.equals(moveOffset))) {
				drawingPanel.updateHistory();
				clearMouseSettings();
			}
			clearMouseSettings();
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
			if (drawingPanel != null) drawingPanel.smake();
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
		long newStart = gnDstart + (long)(length*r1*notches);
		long newEnd   = gnDend - (long)(length*r2*notches);

		if (newEnd < 0) newEnd = 0; // unnecessary?
		else if (newEnd > getTrackSize()) newEnd = getTrackSize();
		if (newStart < 0) newStart = 0;
		else if (newStart > getTrackSize()) newStart = getTrackSize(); // unnecessary?
		if (newStart < newEnd) {
			setEndBP(newEnd,true);
			setStartBP(newStart,true);
			if (drawingPanel != null) drawingPanel.smake();
		}
	}
	
	public void mouseDragged(MouseEvent e) {	
		// Track first
		Cursor cursor = getCursor();
		if (cursor != null && cursor.getType() == Cursor.WAIT_CURSOR) return;
			
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
		
		// Sequence 2nd
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
	protected void layout() { // mouseDragged
		if (holder != null && holder.getParent() instanceof JComponent) {
			((JComponent)holder.getParent()).doLayout();
			((JComponent)holder.getParent()).repaint();
		}
		else if (drawingPanel != null) {
			drawingPanel.doLayout();
			drawingPanel.repaint();
		}
	}
	
	public boolean isFlipped() { return bFlipped; } // Sfilter, TrackData
	public boolean isQuery() { return isQuery1; } // mapper.SeqHits.DrawHit; CAS531 add;
	
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
			// if a track has a track on both sides (e.g. 2 track shown), this is the otherside CAS543 add
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
		Point p = event.getPoint();
		if (rect.contains(p)) { // within blue rectangle of track
			String x = null;
			int gIdx=0;
			for (Annotation annot : allAnnoVec) {
				if (annot.contains(p)) {
					if (x==null) {
						if (annot.isGap()) 			return annot.getHoverDesc().trim();
						if (annot.isCentromere()) 	return annot.getHoverDesc().trim();
						
						x = annot.getHoverDesc().trim(); 
						gIdx = annot.getAnnoIdx();
					}
					else { // CAS517  messes up with overlapping genes 
						if (annot.isExon() && annot.getGeneIdx()==gIdx) {
							x += "\n" + annot.getHoverDesc().trim(); 	// CAS512 add exon if available
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
			if (isOlap(start, end, a.getStart(), a.getEnd())) // CAS550 was separate dedicated method
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
	
	public String getGeneNumFromIdx(int idx1, int idx2) { // CAS545 called from HitData for its popup
		if (idx1>0) {
			for (Annotation aObj : geneVec) {
				if (aObj.getAnnoIdx()==idx1) return " (#" + aObj.getFullGeneNum() + ")"; // CAS548 remove space
			}
		}
		if (idx2>0) {
			for (Annotation aObj : geneVec) {
				if (aObj.getAnnoIdx()==idx2) return " (#" + aObj.getFullGeneNum() + ")";
			}
		}
		return "";
	}
	public String getGeneNumFromIdx(int idx1) { // CAS548 called from HitData for its popup
		for (Annotation aObj : geneVec) {
			if (aObj.getAnnoIdx()==idx1) return aObj.getFullGeneNum(); 
		}	
		return "";
	}
	/*************************************************
	 * In click in anno space: if click on gene, popup info; else pass to parent
	 */
	public void mousePressed(MouseEvent e) {
		// Sequence 1st
		if (e.isPopupTrigger()) { // CAS516 add popup on right click
			String title = getTitle(); // DisplayName Chr
			Point p = e.getPoint();
			if (rect.contains(p)) { // within blue rectangle of track
				for (Annotation annot : geneVec) { // CAS548x was allAnnoVec
					if (annot.contains(p)) {	// in this gene annotation
						setForGenePopup(annot);	
						annot.popupDesc(getHolder(), title, title);
						if (bHighGenePopup) drawingPanel.smake(); // CAS544 to highlight
						return;
					}
				}
			}
		}
		// Track 2nd
		Cursor cursor = getCursor();
		Point point = e.getPoint();
	
		if (e.isPopupTrigger()) { 
			holder.showPopupFilter(e);
		}
		else if (cursor.getType() == Cursor.S_RESIZE_CURSOR) { } // Resize

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
	// called from above on mousePressed for Gene Popup
	private void setForGenePopup(Annotation annot) { 
		annot.setExonList(); // CAS548 was sending full exonList when annot already has its own
		
		if (hitsObj1!=null) {// CAS517 added; CAS548 change format to start:end[+], add score
			if (!annot.hasHitList()) { 
				TreeMap <Integer, String> scoreMap = seqPool.getGeneHits(annot.getAnnoIdx(), grpIdx);
				if (scoreMap.size()>0) {
					String hits = hitsObj1.getHitsForGenePopup(this, annot, scoreMap);
					annot.setHitList(hits);
				}
			}
		}
		if (hitsObj2!=null) {// CAS543 added for 3-track
			if (!annot.hasHitList2()) { 
				TreeMap <Integer, String> scoreMap = seqPool.getGeneHits(annot.getAnnoIdx(), grpIdx);
				if (scoreMap.size()>0) {
					String hits = hitsObj2.getHitsForGenePopup(this, annot, scoreMap);
					annot.setHitList2(hits);
				}
			}
		}
	}
	public int getGroup() {return grpIdx;}

	public String getChrName() {
		String prefix =  drawingPanel.getProjPool().getProperty(getProject(),"grp_prefix");
		if (prefix==null) prefix = "";
		String grp = (chrName==null) ? "" : chrName;
		return prefix + grp;
	}	

	public void setAnnotation() {bShowAnnot=true;} // CAS543 changed from setting static from Query.TableDataPanel
	
	protected TrackData getData() {return new TrackData(this);} // TrackHolder.getTrackData
	
	protected String getFullName() {
		return "Track #" + getHolder().getTrackNum() + " " + getProjectDisplayName() + " " + getChrName();
	}
	public String getTitle() {
		return getProjectDisplayName()+" "+getChrName();
	}
	public String toString() {
		int tn = getHolder().getTrackNum();
		String ref = (tn%2==0) ? " REF " : " "; 
		return "[Sequence "+ getFullName() + " (Grp" + getGroup() + ") " + 
				getOtherProjectName() + ref + " p" + position + " o" + orient + "] ";
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
			setTrackBuild();
			return true;
		}
		return false;
	}
	public boolean showRuler(boolean show) {
		if (bShowRuler != show) {
			bShowRuler = show;
			setTrackBuild();
			return true;
		}
		return false;
	}
	public boolean showGene(boolean show) {
		if (bShowGene != show) {
			bShowGene = show;
			setTrackBuild();
			return true;
		}
		return false;
	}
	public boolean showGeneLine(boolean show) {// CAS520 add
		if (bShowGeneLine != show) {
			bShowGeneLine = show;
			setTrackBuild();
			return true;
		}
		return false;
	}
	public boolean highGenePopup(boolean high) {
		if (bHighGenePopup != high) {
			bHighGenePopup = high;
			if (!high) 
				for (Annotation aObj : allAnnoVec) aObj.setIsPopup(false); // exons are highlighted to, so use all
			setTrackBuild();
			return true;
		}
		return false;
	}
	public boolean showGap(boolean show) {
		if (bShowGap != show) {
			bShowGap = show;
			setTrackBuild();
			return true;
		}
		return false;
	}
	public boolean showCentromere(boolean show) {
		if (bShowCentromere != show) {
			bShowCentromere = show;
			setTrackBuild();
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
			setTrackBuild();
			return true;
		}
		return false;
	}
	public boolean showScoreLine(boolean show) { 
		if (bShowScoreLine != show) {
			bShowScoreLine = show;
			setTrackBuild();
			return true;
		}
		return false;
	}
	// drawn SeqHits.DrawHits.paintHitLen
	public boolean showHitLen(boolean show) { // hit ribbon - graphics on sequence rectangle
		if (bShowHitLen != show) {
			bShowHitLen = show;
			setTrackBuild();
			return true;
		}
		return false;
	}
	public boolean showScoreText(boolean show) { 
		if (bShowScoreText != show) {
			setText(false, false, false, show);
			setTrackBuild();
			return true;
		}
		return false;
	}
	public boolean showHitNumText(boolean show) { // CAS531 add
		if (bShowHitNumText != show) {
			setText(false, false, show, false);
			setTrackBuild();
			return true;
		}
		return false;
	}
	public boolean showBlockText(boolean show) { // CAS545 add
		if (bShowBlockText != show) {
			setText(show, false, false, false);
			setTrackBuild();
			return true;
		}
		return false;
	}
	public boolean showCsetText(boolean show) { // CAS545 add
		if (bShowCsetText != show) {
			setText(false, show, false, false);
			setTrackBuild();
			return true;
		}
		return false;
	}
	public boolean showNoText(boolean show) { // CAS545 add
		if (bShowNoText != show) {
			setText(false, false, false, false);
			setTrackBuild();
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
	protected void noSelectedGene() { // Sfilter
		if (selectedGeneObj!=null) {
			selectedGeneObj.setIsSelectedGene(true);
			selectedGeneObj=null;
		}
	}
	protected int getMidCoordForGene(String name) { // Sfilter; CAS545 filter on gene#
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
	
	/******************************************************************/
	// XXX Hit interface
	// mapper.HitData used for conserved to highlight genes at ends of hit; CAS545 add
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
	public void setHighforHit(int annot1_idx, int annot2_idx, boolean isHigh) { // mapper.HitData CAS545 highlight gene of hit
		for (Annotation aObj : geneVec) {
			int idx = aObj.getAnnoIdx();
			if (idx==annot1_idx || idx==annot2_idx) {
				aObj.setIsHitPopup(isHigh); 
				return;
			}
		}
	}
	public Point2D getPointForHit(long bpPos, int trackPos) { // mapper.seqHits & DrawHit
		if (bpPos < gnDstart) bpPos = gnDstart; 
		if (bpPos > gnDend)   bpPos = gnDend; 	
		
		double x = (trackPos == Globals.LEFT_ORIENT) ? rect.x+rect.width : rect.x;	
		
		double y = rect.y + BpNumber.getPixelValue(bpPos-gnDstart ,bpPerPixel);
		
		if (bFlipped) y = rect.y + rect.y + rect.height - y;	
		
		return new Point2D.Double(x, y); 
	}
	public Point getLocation() { // SeqHits.paintComponent
		if (holder != null) return holder.getLocation();
		else 				return new Point();
	}

	public boolean isHitInRange(long num) { // mapper.SeqHits.isVisHitWire
		return num >= gnDstart && num <= gnDend;
	}
	public boolean isHitOlap(int hs, int he) { // mapper.SeqHits.isOlapHit; CAS550 add for partial hit
		return isOlap(hs, he, (int) gnDstart, (int) gnDend);
	}
	private boolean isOlap(int s1, int e1, int s2, int e2) {
		int olap = Math.min(e1,e2) - Math.max(s1,s2) + 1;
		return (olap>0);
	}
	/************************************************************************
	 * XXX From Track
	 */
	public TrackHolder getHolder() { return holder;}// Called by Sequence and DrawingPanel
	
	public Point getMoveOffset() {return moveOffset;} // trackholder

	/* getGraphics returns the holders graphics object if a holder exists, null otherwise.
	 * @see javax.swing.JComponent#getGraphics() */
	public Graphics getGraphics() {
		if (holder != null) return holder.getGraphics();
		return null;
	}

	protected double getMidX() {return rect.getX()+(rect.getWidth()/2.0);} // trackLayout

	public int getProject() {return projIdx;}
	
	public String getOtherProjectName() {
		dprt("set other Proj " + otherProjName + " idx " + otherProjIdx);
		return otherProjName;
	}

	public String getProjectDisplayName() {return displayName;}

	public double getBpPerPixel() { return bpPerPixel;} // DrawingPanel.drawToScale
	
	public boolean hasLoad() {return hasLoad;}
	
	// Implements the +/- buttons. 
	// It stores the current region width and center so that (-) can exactly undo (+) even
	// when the (+) expands beyond the bounds of one sequence.
	public void changeAlignRegion(double factor)  { // DrawingPanel
		long s = gnDstart;
		long e = gnDend;
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
		
		setBpPerPixel( bpPerPixel * ( 1.0 / effectiveFactor ) );
		
	}
	public boolean fullyExpanded()  { // Drawing panel
		long s = getStart();
		long e = getEnd();
		if (s > 1 || e < getTrackSize()) return false;	
		return true;
	}
	public void setBpPerPixel(double bp) { // DrawingPanel; Used with +/- buttons; drawToScale; 
		if (bp > 0) {
			height = Globals.NO_VALUE;

			if (bp != bpPerPixel) {
				bpPerPixel = bp;
				setTrackBuild();
			}
		}
	}
	private void setHeight(double pixels) { // mouseDragged
		pixels = Math.min(Math.max(pixels,1),MAX_PIXEL_HEIGHT);
		if (height != pixels) {
			height = pixels;
			setTrackBuild();
		}
	}
	private void clearHeight() { // mouseReleased
		if (height != Globals.NO_VALUE) {
			height = Globals.NO_VALUE;
			setTrackBuild();
		}
	}	
	protected void setOrientation(int orientation) { // TrackHolder.setTrack
		if (orientation != Globals.RIGHT_ORIENT && orientation != Globals.CENTER_ORIENT) orientation = Globals.LEFT_ORIENT;
		if (orientation != orient) {
			orient = orientation;
			setTrackBuild();
		}
	}
	public void resetEnd() { // DrawingPanel, Sequence
		if (gnDend != gnSize) {
			gnDend = gnSize;
			setAllBuild();
		}
	}
	public void resetStart() {// DrawingPanel, Sequence
		if (gnDstart != 0) {
			gnDstart = 0;
			setAllBuild();
		}
	}
	public void setStart(long startValue)  { // DrawingPanel, Sfilter; CAS514 was throwing exception and stopping symap
		if (startValue > gnSize) {
			Utilities.showWarningMessage("Start value "+startValue+" is greater than size "+gnSize+".");
			return;
		}
		if (startValue < 0) {
			Utilities.showWarningMessage("Start value is less than zero.");
			return;
		}
		if (gnDstart != startValue) {
			firstBuild = true;

			gnDstart = startValue;
			setAllBuild();
		}
	}

	public long setEnd(long endValue) { // DrawingPanel, Sfilter; CAS514 was throwing exception and stopping symap
		if (endValue < 0) {
			Utilities.showWarningMessage("End value is less than zero.");
			return gnSize;
		}
		if (endValue > gnSize) endValue = gnSize;

		if (gnDend != endValue) {
			firstBuild = true;

			gnDend = endValue;
			setAllBuild();
		}
		return endValue;
	}

	public void setStartBP(long startBP, boolean resetPlusMinus)  { // Track, DrawingPanel, Sequence
		if (startBP > gnSize) {
			Utilities.showWarningMessage("Start value "+startBP+" is greater than size "+gnSize+".");
			return;
		}
		if (startBP < 0) {
			Utilities.showWarningMessage("Start value "+startBP+" is less than zero.");
			return;
		}

		if (resetPlusMinus) curCenter = 0;
		if (gnDstart != startBP) {
			firstBuild = true;
			gnDstart = startBP;
			setAllBuild();
		}
	}

	public long setEndBP(long endBP, boolean resetPlusMinus)  {// Track, DrawingPanel, Sequence
		if (endBP < 0) Utilities.showWarningMessage("End value is less than zero.");

		if (endBP > gnSize || endBP<0) endBP = gnSize;

		if (resetPlusMinus) curCenter = 0;
		if (gnDend != endBP) {
			firstBuild = true;
			gnDend = endBP;
			setAllBuild();
		}

		return endBP;
	}

	public long getStart() {return gnDstart;} // Track, Sequence, Mapper
	public long getEnd() {return gnDend; }
	public long getTrackSize() {return gnSize;}

	protected Dimension getDimension() {return dimension; } // TrackHolder

	protected double getValue(long cb, String units) { return BpNumber.getValue(units, cb,bpPerPixel);} // Sfilter
	private double getPixelValue(long num) {return (double)num/(double)bpPerPixel;}
	
	private double getAvailPixels() {
		return (drawingPanel.getViewHeight() - (trackOffset.getY() * 2));
	}
	protected void setFullBpPerPixel() { // Sequence
		if (firstBuild) {
			bpPerPixel = (gnDend - gnDstart) / getAvailPixels();

			if (bpPerPixel < minDefaultBpPerPixel) 		bpPerPixel = minDefaultBpPerPixel;
			else if (bpPerPixel > maxDefaultBpPerPixel) bpPerPixel = maxDefaultBpPerPixel;
			
			defaultBpPerPixel = bpPerPixel;
		}
	}
	
	protected boolean validSize() {// Sequence.build
		double dif = getPixelValue(gnDend) - getPixelValue(gnDstart);
		return (dif > 0 && dif <= MAX_PIXEL_HEIGHT);
	}
	protected void adjustSize() {// Sequence.build
		double dif = getPixelValue(gnDend) - getPixelValue(gnDstart);
		if (dif < 0) {
			long temp = gnDend;
			gnDend = gnDstart;
			gnDstart = temp;
			dif = getPixelValue(gnDend) - getPixelValue(gnDstart);
		}	    
		if (dif > MAX_PIXEL_HEIGHT) {
			if (height == Globals.NO_VALUE) height = getAvailPixels();
			bpPerPixel = (gnDend-gnDstart)/height; 
		}
	}
	protected int getBP(double y) {// Sequence, Track mouseReleased
		y -= rect.getY();
		if (y < 0)						y = 0;
		else if (y > rect.getHeight())	y = rect.getHeight();
		y *= bpPerPixel;
		y += gnDstart;

		if (y != Math.round(y)) 		y = Math.round(y);

		if (y < gnDstart)		y = gnDstart;
		else if (y > gnDend)	y = gnDend;

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
	
	public void setPosition(int position) { this.position = position; } // DrawingPanel.initTrack
	public boolean isRef() {return (position==REFPOS);} // Sequence.setup, Sfilter; CAS545 add for conserved; (position % 2 == 0)
	public void setBackground(Color c) {bgColor = c;}
	
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
	protected boolean isSouthResizePoint(Point p) {
		return (p.getX() >= rect.x &&
				p.getX() <= rect.x + rect.width && 
				p.getY() >= (rect.y+rect.height) - MOUSE_PADDING && 
				p.getY() <= (rect.y+rect.height) + MOUSE_PADDING);
	}
	
	/************************************************************
	 * Clear for sequence and track
	 */
	protected void clearSeq() { // TrackHolder
		ruleList.clear();
		allAnnoVec.clear();
		geneVec.clear();
		olapMap.clear();
		hitsObj1=hitsObj2=null;
				
		headerPoint.setLocation(0,0);
		footerPoint.setLocation(0,0);
		dragPoint.setLocation(0,0);	
		clearTrack();
	}

	public void clearData() { // DrawingPanel.clearData, resetData
		ruleList.clear();
		allAnnoVec.clear();
		geneVec.clear();
		olapMap.clear();
	}

	private void clearMouseSettings() {
		if (!isCleared(dragRect)) {
			dragRect.setRect(0,0,Globals.NO_VALUE,Globals.NO_VALUE);;
			repaint();
		}
		startMoveOffset.setLocation(Globals.NO_VALUE,Globals.NO_VALUE);
		adjustMoveOffset.setLocation(Globals.NO_VALUE,Globals.NO_VALUE);
		dragPoint.setLocation(Globals.NO_VALUE,Globals.NO_VALUE);
		startResizeBpPerPixel = Globals.NO_VALUE;
	}
	protected boolean isCleared(Point p) {
		return p.x == Globals.NO_VALUE && p.y == Globals.NO_VALUE;
	}
	protected boolean isCleared(Rectangle r) {
		return r.x == 0 && r.y == 0 && r.width == Globals.NO_VALUE && r.height == Globals.NO_VALUE;
	}
	
	protected void setAllBuild() { // TrackData and all methods that data
		if (drawingPanel != null)
			drawingPanel.setTrackBuild(); // sets hasBuild to false on all tracks
		else setTrackBuild();
	}
	public void setTrackBuild() {if (hasBuild) {hasBuild = false; dprt("Chg hasBuild to F");}} // Track, DrawingPanel, Sequence	
	
	private void clearInit() { 
		firstBuild = true;
		hasLoad = false;
		setAllBuild();
	}
	public void clearTrack() { // Sequence, Track, TrackHolder
		rect.setRect(0,0,0,0);
		trackOffset.setLocation((int)defaultTrackOffset.getX(),(int)defaultTrackOffset.getY());
		startMoveOffset.setLocation(Globals.NO_VALUE,Globals.NO_VALUE);
		adjustMoveOffset.setLocation(Globals.NO_VALUE,Globals.NO_VALUE);
		moveOffset.setLocation(0,0);
		startResizeBpPerPixel = Globals.NO_VALUE;
		dragRect.setRect(0,0,Globals.NO_VALUE,Globals.NO_VALUE);
		dragPoint.setLocation(0,0);
		titlePoint.setLocation(0,0);

		dimension.setSize(0,0);
		height = width = Globals.NO_VALUE;
		defaultBpPerPixel = bpPerPixel = minDefaultBpPerPixel;
		clearInit();
	}

	/////////////////////////////////////////////////////////////////////////
	public void mouseEntered(MouseEvent e) { holder.requestFocusInWindow();}
	public void mouseClicked(MouseEvent e) { }
	public void mouseExited(MouseEvent e) { 	}
    public void keyTyped(KeyEvent e) { } 
    public void keyPressed(KeyEvent e) { } 
    public void keyReleased(KeyEvent e) { } 
    
	/*************************************************************************/
    // from Track
	private static final int MAX_PIXEL_HEIGHT = 10000;
	private static final Color REFNAME = new Color(0,0,200); // CAS530 change from red to deep blue
	private static final Color titleColor = Color.black;
	private static final Color dragColor = new Color(255,255,255,120);
	private static final Color dragBorder = Color.black;
	private static final Font titleFont = new Font("Arial", 1, 14);

	// Original Sequence
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
