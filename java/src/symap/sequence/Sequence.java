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

import props.PropsDB;
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
 * CAS551 dead code; Point startMoveOffset = new Point(), moveOffset = new Point(); 
 */
public class Sequence implements HelpListener, KeyListener,MouseListener,MouseMotionListener,MouseWheelListener {
	// flags; Sequence filter settings // moved colors and constants to end
	protected Annotation selectedGeneObj=null;						// CAS545 add
	
	protected int grpIdx=Globals.NO_VALUE;
	protected String chrName;					// e.g. Chr01
	protected int projIdx=Globals.NO_VALUE, otherProjIdx=Globals.NO_VALUE;
	private String projectName, displayName, otherProjName;
	protected int chrSize = 0;  				// full chr size, static; CAS551 was long
	protected int position;						// track position
	protected int orient = Globals.LEFT_ORIENT; // right (-1) center(0) left (1)
	private Color bgColor; 	
	private TextLayout titleLayout;
	
	private SeqHits hitsObj1=null,  hitsObj2=null; // CAS517 add; CAS543 add Obj2 for 3-track
	private boolean isQuery1=false, isQuery2=false;// CAS517 add; CAS545 add 2; True => this sequence is Query (pseudo_hits.annot1_idx)
	
	private SeqPool seqPool;
	private Vector <Rule> ruleList=  new Vector<Rule>(15,2); 			// CAS545 was List
	protected Vector <Annotation> allAnnoVec = new Vector<Annotation>(1); // include exons, CAS545 needs initial size; see loadAnnoFromDB
	private Vector <Annotation> geneVec = new Vector <Annotation> (); 	// CAS545 added vector for faster searching
	private Point2D.Float headerPoint = new Point2D.Float(), footerPoint = new Point2D.Float();
	private TextLayout headerLayout = null, footerLayout = null;
	
	private HashMap <Integer, Integer> olapMap = new HashMap <Integer, Integer> (); // CAS517 added for overlapping genes; CAS518 global
	
	/******* Track ************************************/
	private Rectangle2D.Double rect = new Rectangle2D.Double(); // blue track rectangle
	protected Dimension dimension = new Dimension();
	protected double height=Globals.NO_VALUE, 					// blue track height, can change
					 width=DEFAULT_WIDTH;     					// blue track width (w/o ruler or anno), static
	protected int chrDisplayStart = 0, chrDisplayEnd = 0;  		// displayed start, end; changes with zoom 
	
	private Rectangle dragRect = new Rectangle();
	private Point dragPoint = new Point(), trackOffset= new Point();
	
	private Point  adjustMoveOffset = new Point();
	private Point2D.Float titlePoint = new Point2D.Float();
	private Point2D defaultTrackOffset = defaultSequenceOffset;
	protected double defaultBpPerPixel=1, bpPerPixel=1; 		// MIN_DEFAULT_BP_PER_PIXEL
	private double startResizeBpPerPixel=Globals.NO_VALUE;
	private int curCenter=0, curWidth=0; 						// for +/- buttons
	
	private boolean hasLoad, hasBuild, firstBuild = true;
	
	private DrawingPanel drawingPanel;
	private TrackHolder holder;
	private PropsDB propDB;
	protected Sfilter sfilObj; // CAS552 moved everything to Sfilter, and just access it from here.

	private int distance_for_anno = 0;		// CAS554 show anno depend on density of genes
	
	private String buildCntMsg="", paintCntMsg="";
	private void dprt(String msg) {symap.Globals.dprt("SQ: " + msg);}
	
	/** the Sequence object is created when 2D is started */
	public Sequence(DrawingPanel dp, TrackHolder holder) {
		this.drawingPanel = dp;
		this.holder = holder;
		
		if (holder.getTrack() != null) this.bgColor = holder.getTrack().bgColor; // maintain color when "back to block view" selected
		if (holder.getTrack() != null) this.position = holder.getTrack().position; // maintain position when "back to block view" selected
		
		propDB = dp.getPropDB();
		
		this.seqPool = 	new SeqPool(dp.getDBC());
	}
	
	public void setup(int projIdx, int grpIdx) { // Sequence, Drawing panel
		this.grpIdx = grpIdx;
		this.projIdx = projIdx;
		displayName = propDB.getDisplayName(projIdx);
		projectName = propDB.getName(projIdx);
		if (Utilities.isEmpty(displayName)) displayName=projectName; // CAS534 need for new projects
		
		loadAnnosFromDB();	
		
		trackOffset.setLocation((int)defaultTrackOffset.getX(),(int)defaultTrackOffset.getY());
		dragPoint.setLocation(0,0);
		titlePoint.setLocation(0,0);
		dimension.setSize(0,0);
		adjustMoveOffset.setLocation(Globals.NO_VALUE,Globals.NO_VALUE);
		dragRect.setRect(0,0,Globals.NO_VALUE,Globals.NO_VALUE);;
	}
	
	// Set the annotation vector here
	private boolean loadAnnosFromDB() { // resetData, setup
		try {
			int n = seqPool.getNumAllocs(grpIdx);
			allAnnoVec = new Vector <Annotation>(n); // CAS545 alloc here instead
			
			chrSize = seqPool.loadChrSize(this);
			chrName = seqPool.loadSeqData(this, allAnnoVec); // Add annotations to allAnnoVec, and gnSize value
			if (allAnnoVec.size() >0) { // CAS553 fix for 552 bug when no annot; was accessing a null filtObj
				int geneLen=0;			// CAS554 add computation
				for (Annotation aObj : allAnnoVec) {
					if (aObj.isGene()) {
						geneVec.add(aObj);
						geneLen += aObj.getGeneLen();
					}
				}
				if (geneVec.size()>0) { // CAS555 bug fix for zero
					int avg_gene = (geneVec.size()>0) ? (int)(geneLen/geneVec.size()) : 0;
					distance_for_anno = avg_gene/GENES_FOR_ANNOT_DESC; 
					if (distance_for_anno<MIN_BP_FOR_ANNOT_DESC) distance_for_anno = MIN_BP_FOR_ANNOT_DESC;
				}
				else distance_for_anno = MIN_BP_FOR_ANNOT_DESC;
			}
		} catch (Exception s1) {
			ErrorReport.print(s1, "Initializing Sequence failed.");
			seqPool.close();
			return false;
		}	
		chrDisplayStart = 0;
		chrDisplayEnd = chrSize;
		hasLoad = true;

		return true;
	}
	public void setOtherProject(int otherProjIdx) { // Drawing panel
		otherProjName = propDB.getDisplayName(otherProjIdx);
		this.otherProjIdx = otherProjIdx;
		if (Utilities.isEmpty(otherProjName)) otherProjName=propDB.getName(otherProjIdx);
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
		
		if (isRef()) resetHitg2(); 	// CAS545 add
	}
	protected void setFilter(Sfilter obj) {sfilObj = obj;} // CAS552 added
	
	public void setBackground(Color c) {bgColor = c;}
	
	public void setPosition(int position) { this.position = position; } // DrawingPanel.initTrack
	
	protected boolean isRef() {return (position % REFPOS == 0);} // Sequence.setup, Sfilter; CAS545 add for 2g2/1g2; (position % 2 == 0)
	
	protected boolean is3Track() {return drawingPanel.getNumMaps()>1;} // CAS555 hitObj2 is not known when filter created
	/**
	 * XXX Build layout: Sets up the drawing objects for this sequence. Called by DrawingPanel and Track
	 * The Gene boxes are drawn in Annotations.setRectangle and its paintComponent
	 * The Hit Length, Hit %Id Value and Bar are drawn in mapper.SeqHits.DrawHits.paintHitLen and its paintComponent
	 */
	public boolean buildGraphics() { // DrawingPanel.buildAll & firstViewBuild; Sequence.mouseDragged
		if (hasBuild) return true;
		if (!hasLoad) return false;

		if (allAnnoVec.size() == 0) sfilObj.bShowAnnot = sfilObj.bShowGeneNum = false;
			
		if (firstBuild) {
			bpPerPixel = (chrDisplayEnd - chrDisplayStart) / getAvailPixels();
			if (bpPerPixel < MIN_DEFAULT_BP_PER_PIXEL) 		bpPerPixel = MIN_DEFAULT_BP_PER_PIXEL;
			else if (bpPerPixel > MAX_DEFAULT_BP_PER_PIXEL) bpPerPixel = MAX_DEFAULT_BP_PER_PIXEL;
			
			defaultBpPerPixel = bpPerPixel;
		}

		TextLayout layout;
		FontRenderContext frc;
		Rectangle2D bounds;
		double x, y, h, x1, x2, tx, ty;
		Rectangle2D.Double totalRect = new Rectangle2D.Double();

		if (height > 0) { // after 1st build
			int diff = Math.abs(chrDisplayEnd-chrDisplayStart);
			double bp = BpNumber.getBpPerPixel(diff, height); // diff/height
			if (bp > 0) bpPerPixel = bp;
		}
		// this started happening with later Java versions if they click too fast
		double dif = getPixelValue(chrDisplayEnd) - getPixelValue(chrDisplayStart);
		if (!(dif > 0 && dif <= MAX_PIXEL_HEIGHT)) { // CAS521 make popup
			Utilities.showWarningMessage("Unable to size sequence view. Try again. (" + chrDisplayStart + "," + chrDisplayEnd + ")"); 				
			if (height == Globals.NO_VALUE) height = getAvailPixels();
			bpPerPixel = (chrDisplayEnd-chrDisplayStart)/height; 
		}
		int len = chrDisplayEnd - chrDisplayStart;
		rect.setRect(trackOffset.getX(), trackOffset.getY(), width, getPixelValue(len)); // chr rect
		
		totalRect.setRect(0, 0, rect.x + rect.width + 2, rect.y + rect.height + 2);
		frc = getFontRenderContext();

	// Setup the sequence ruler
		ruleList.clear();
		if (sfilObj.bShowRuler) {
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
			
			for (y = rect.y; y < h + SPACE_BETWEEN_RULES; y += SPACE_BETWEEN_RULES) {	
				if (y < h && y > h - SPACE_BETWEEN_RULES) continue; 
				
				if (y > h) y = h; 
				Rule rObj = new Rule(unitColor, unitFont);
				rObj.setLine(x1, y, x2, y);
				
				double bp =  BpNumber.getCbPerPixel(bpPerPixel, (sfilObj.bFlipped ? h-y : y-rect.y)) + chrDisplayStart; 
				String num = BpNumber.getFormatted(bpSep, bp, bpPerPixel);
				layout = new TextLayout(num, unitFont, frc);
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
				rObj.setText(layout, tx, ty);
				ruleList.add(rObj);
			}
		}

	// Setup the annotations
		boolean isRight = (orient == Globals.RIGHT_ORIENT);
		double lastStart=0;
		int lastInc=1;
		
		getHolder().removeAll(); 
		Rectangle2D centRect = new Rectangle2D.Double(rect.x+1,rect.y,rect.width-2,rect.height);
		
		if (olapMap.size()==0) seqPool.buildOlap(olapMap, geneVec); // builds first time only
		
		int cntAnno=0;
	// XXX Sorted by genes (genenum, start), then exons 
		Annotation last=null;
	    for (Annotation annot : allAnnoVec) {
	    	if ( (annot.isGap()        && !sfilObj.bShowGap)
	    	 ||  (annot.isCentromere() && !sfilObj.bShowCentromere)
	    	 ||  ((annot.isGene() || annot.isExon()) && !sfilObj.bShowGene) )
			{
					annot.clear();
					continue; // CAS520 v518 bug, does not display track if, return true;
			}
	    	
	    // Display gene: first Calc displayWidth, hoverWidth and offset for gene
			double dwidth = rect.width, hwidth=0;  
			int offset=0;
			
			if (annot.isGene()) {
				hwidth = EXON_WIDTH;
				dwidth = EXON_WIDTH/GENE_DIV; 
				if (olapMap.containsKey(annot.getAnnoIdx())) offset = olapMap.get(annot.getAnnoIdx());
			}
			else if (annot.isExon()) { // CAS543 remove || annot.isSyGene()) {
				dwidth = EXON_WIDTH;
				if (olapMap.containsKey(annot.getGeneIdx())) offset = olapMap.get(annot.getGeneIdx());
			}
			if (offset>0 && isRight) offset = -offset;
			
			annot.setRectangle(centRect, chrDisplayStart, chrDisplayEnd,bpPerPixel, dwidth, hwidth, sfilObj.bFlipped, offset,
					sfilObj.bShowGeneLine, sfilObj.bShowGeneNum, sfilObj.bHighGenePopup); // CAS520 showGeneLine; CAS544 popup; CAS551 showGeneNum
			if (last!=null) annot.setLastY(last); // CAS551 so annotation are separated
			last=annot;
			
			if (annot.isGene() && annot.isVisible()) cntAnno++; //isVis needs rect set; CAS554 moved from paintComponent
		} // end for loop of (all annotations)
	    
	    buildCntMsg = String.format("Genes: %,d", cntAnno);
	    
	    if (sfilObj.bShowAnnot) {
	    	if (symap.Globals.DEBUG) {
	    		String bp = Utilities.kText((int) bpPerPixel);
	    		buildCntMsg += String.format("\nAnnotation: if x(%s)<y(%,d)", bp, (int)distance_for_anno);
	    	}	
	    	
	    	if (((int)bpPerPixel > distance_for_anno)) buildCntMsg += "\nZoom in for annotation";
	    	else {
	    		double lastBP=rect.y + rect.height; // CAS554 
		    	for (Annotation annot : allAnnoVec) {
		    		if (!(annot.isGene() && annot.hasDesc() && annot.isVisible())) continue;
		    		
					x1 = isRight ? (rect.x + rect.width + RULER_LINE_LENGTH + 2) : rect.x; 
					x2 = isRight ? x1 + RULER_LINE_LENGTH  						 : x1 - RULER_LINE_LENGTH;
					
					ty = annot.getY1();
					if (lastStart+OVERLAP_OFFSET>ty) {//CAS548; was incrementing y, now increment x so still line up
						x1 = x1 + (lastInc*OVERLAP_OFFSET); 
						lastInc++;
						if (lastInc>=4) lastInc=1;
					}
					else lastInc=1;
					lastStart=ty;
					
					TextBox tb = new TextBox(annot.getYellowBoxDesc(),unitFont, (int)x1, (int)ty, annot.getBorderColor()); // CAS548 use new TextBox
					if (tb.getLowY()>lastBP) continue;    // if flipped, this may be first, so do not break
					
					getHolder().add(tb); 				  // adds it to the TrackHolder JComponent
					
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
	    }
	    
	// Set header and footer
		bounds = new Rectangle2D.Double(0, 0, 0, 0); 							
		x = (rect.x + (rect.width / 2.0)) - (bounds.getX() + (bounds.getWidth() / 2.0));
		if (sfilObj.bFlipped) 	y = rect.y + rect.height + OFFSET_SPACE + bounds.getHeight();
		else			y = rect.y - OFFSET_SPACE;
		headerPoint.setLocation((float) x, (float) y);

		totalRect.width = Math.max(totalRect.width, x + bounds.getX() + bounds.getWidth());
		totalRect.x = Math.min(totalRect.x, x);
		totalRect.y = Math.min(totalRect.y, y - bounds.getHeight());

		footerLayout = new TextLayout(BpNumber.getFormatted(chrDisplayEnd-chrDisplayStart+1, bpPerPixel)+
				               " of "+BpNumber.getFormatted(chrSize, bpPerPixel), footerFont, frc); 
		bounds = footerLayout.getBounds();
		x = (rect.x + (rect.width / 2.0)) - (bounds.getX() + (bounds.getWidth() / 2.0));
		if (sfilObj.bFlipped) 	y = rect.y - OFFSET_SPACE;
		else			y = rect.y + rect.height + OFFSET_SPACE + bounds.getHeight();
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
			dprt("no title for " + chrName);
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
		Rectangle2D headerBounds = (sfilObj.bFlipped ? footerLayout.getBounds() : /*headerLayout.getBounds()*/new Rectangle2D.Double(0, 0, 0, 0)); 
		Point2D headerP = (sfilObj.bFlipped ? footerPoint : headerPoint); 
		Point2D point = new Point2D.Double((rect.getWidth() / 2.0 + rect.x) - (titleBounds.getWidth() / 2.0 + titleBounds.getX()),
				headerP.getY() - headerBounds.getHeight() - titleOffset.getY());

		if (point.getX() + titleBounds.getX() + titleBounds.getWidth() > dimension.getWidth())
			point.setLocation(dimension.getWidth()- (titleBounds.getX() + titleBounds.getWidth()), point.getY());

		if (point.getX() < titleOffset.getX())
			point.setLocation(titleOffset.getX(), point.getY());

		return point;
	}
	private FontRenderContext getFontRenderContext() {
		if (holder != null && holder.getGraphics() != null)
			return ((Graphics2D)holder.getGraphics()).getFontRenderContext();
		return null;
	}
	/***********************************************************************************/
	public void paintComponent(Graphics g) { // TrackHolder.paintComponent
		if (!hasBuild) return;
		
		Graphics2D g2 = (Graphics2D) g;
		
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); // CAS535
        
		g2.setPaint((position % REFPOS == 0 ? bgColor1 : bgColor2)); 
		g2.fill(rect);
		g2.setPaint(border);
		g2.draw(rect);

		if (sfilObj.bShowRuler) {
			for (Rule rule : ruleList)
				rule.paintComponent(g2);
		}
		// Paint Annotation 
		int cntCon=0; // conserved can be set and build not called, so compute here
		for (Annotation annot : allAnnoVec) {
			if (annot.isVisible()) {
				annot.paintComponent(g2); 	
				if (annot.isGene() && annot.isHitg2()) cntCon++; 
			}
		}
		if (cntCon>0) paintCntMsg = String.format("\n%s %,d", sfilObj.xHitg2(), cntCon);
		
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
			drawingPanel.smake("seq outofmem"); // redraw after user clicks "ok"
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
		
		return getTitle() + " \n\n" + buildCntMsg + "\n" + paintCntMsg; // + "\n\n" + drawingPanel.mouseFunction; 
	}
	
	public boolean isFlipped() { return sfilObj.bFlipped; } // Sfilter, TrackData
	public boolean isQuery() { return isQuery1; } // mapper.SeqHits.DrawHit; CAS531 add;
	
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
	public Annotation getAnnoObj(int idx1, int idx2) { // CAS560 replaced getGeneNumFromIdx with getAnnoObj
		if (idx1>0) {
			for (Annotation aObj : geneVec) {
				if (aObj.getAnnoIdx()==idx1) return aObj; 
			}
		}
		if (idx2>0) {
			for (Annotation aObj : geneVec) {
				if (aObj.getAnnoIdx()==idx2) return aObj;
			}
		}
		return null;
	}
	
	public String getGeneNumFromIdx(int idx1) { // CAS548 called from HitData for its popup
		for (Annotation aObj : geneVec) {
			if (aObj.getAnnoIdx()==idx1) return aObj.getFullGeneNum(); 
		}	
		return "";
	}
	
	public int getGroup() {return grpIdx;}

	public String getChrName() {
		String prefix =  drawingPanel.getPropDB().getProperty(getProject(),"grp_prefix");
		if (prefix==null) prefix = "";
		String grp = (chrName==null) ? "" : chrName;
		return prefix + grp;
	}	

	public void setAnnotation() {sfilObj.bShowAnnot=true;} // CAS543 changed from setting static from Query.TableDataPanel
	
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
	 *  XXX Interface between Sfilter and DrawingPanel, etc
	 */
	public boolean getShowAnnot() 		{ return sfilObj.bShowAnnot;} 		// drawingpanel
	
	//SeqHits.DrawHits
	public boolean getShowHitLen()		{ return sfilObj.bShowHitLen; }
	public boolean getShowScoreLine()	{ return sfilObj.bShowScoreLine; }
	public boolean getShowScoreText() 	{ return sfilObj.bShowScoreText; }	 
	public boolean getShowHitNumText() 	{ return sfilObj.bShowHitNumText; }
	public boolean getShowBlockText()  	{ return sfilObj.bShowBlockText; }
	public boolean getShowCsetText()   	{ return sfilObj.bShowCsetText; }

	public int[] getAnnotationTypeCounts() {
		int[] counts = new int[Annotation.numTypes];
		
		for (Annotation a : allAnnoVec)
			counts[a.getType()]++;
		
		return counts;
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
	protected void setSelectedGene(Annotation aObj) { // TrackData CAS552 add for history
		selectedGeneObj = aObj;
		aObj.setIsSelectedGene(true);
	}
	protected void noSelectedGene() { // Sfilter
		if (selectedGeneObj!=null) {
			selectedGeneObj.setIsSelectedGene(false);
			selectedGeneObj=null;
		}
	}
	/******************************************************************/
	// Hit interface
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
	public Vector <Integer> getGeneIdx() { // seqPool.flagConserved
		Vector <Integer> idxVec = new Vector <Integer> ();
		for (Annotation annot : geneVec)  idxVec.add(annot.getAnnoIdx());
		return idxVec;
	}
	
	private void resetHitg2()  {// see reset() for history
		if (sfilObj.bHitHighg2x2 || sfilObj.bHitHighg2x1) {
			seqPool.setNg2(sfilObj.bHitHighg2x2, hitsObj1, hitsObj2, this);
		}
		else { 
			if (hitsObj1!=null) hitsObj1.clearHighHitg2(); 
			if (hitsObj2!=null) hitsObj2.clearHighHitg2();
		}
	}
	// Sfilter.FilterListener;  sets isHighHitg2x2 in HitData and Annotation
	public boolean highHitg2x2(boolean high) {
		if (sfilObj.bHitHighg2x2==high) return false;
		
		sfilObj.bHitHighg2x2 = high;
		if (high) seqPool.setNg2(true, hitsObj1, hitsObj2, this);
		else { //  clears hit its end annos
			if (hitsObj1!=null) hitsObj1.clearHighHitg2();
			if (hitsObj2!=null) hitsObj2.clearHighHitg2();
		}
		return true;
	}
	// Sfilter.FilterListener;  sets isHighHitg2x1 in HitData and Annotation
	public boolean highHitg2x1(boolean high) {
		if (sfilObj.bHitHighg2x1==high) return false;
		
		sfilObj.bHitHighg2x1 = high;
		if (high) seqPool.setNg2(false, hitsObj1, hitsObj2, this);
		else { // hit conserve clears its end annos
			if (hitsObj1!=null) hitsObj1.clearHighHitg2();
			if (hitsObj2!=null) hitsObj2.clearHighHitg2();
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
	public Point2D getPointForHit(int hitMidPt, int trackPos) { // mapper.seqHits & DrawHit
		if (hitMidPt < chrDisplayStart) hitMidPt = chrDisplayStart; 
		if (hitMidPt > chrDisplayEnd)   hitMidPt = chrDisplayEnd; 	
		
		double x = (trackPos == Globals.LEFT_ORIENT) ? rect.x + rect.width : rect.x;	
		
		double y = rect.y + BpNumber.getPixelValue(hitMidPt-chrDisplayStart ,bpPerPixel);
		
		if (sfilObj.bFlipped) y = rect.y + rect.y + rect.height - y;	
		
		return new Point2D.Double(x, y); 
	}
	
	public Point getLocation() { // SeqHits.paintComponent
		if (holder != null) return holder.getLocation();
		else 				return new Point();
	}

	public boolean isHitInRange(int num) { // mapper.SeqHits.isVisHitWire
		return num >= chrDisplayStart && num <= chrDisplayEnd;
	}
	public boolean isHitOlap(int hs, int he) { // mapper.SeqHits.isOlapHit; CAS550 add for partial hit
		return isOlap(hs, he, (int) chrDisplayStart, (int) chrDisplayEnd);
	}
	private boolean isOlap(int s1, int e1, int s2, int e2) {
		int olap = Math.min(e1,e2) - Math.max(s1,s2) + 1;
		return (olap>0);
	}
	/************************************************************************
	 * From Track
	 */
	public TrackHolder getHolder() { return holder;}// Called by Sequence and DrawingPanel

	/* getGraphics returns the holders graphics object if a holder exists, null otherwise.*/
	public Graphics getGraphics() {
		if (holder != null) return holder.getGraphics();
		return null;
	}

	protected double getMidX() {return rect.getX()+(rect.getWidth()/2.0);} // trackLayout

	public int getProject() {return projIdx;}
	
	public String getOtherProjectName() {return otherProjName;} // Closeup

	public String getProjectDisplayName() {return displayName;}

	public double getBpPerPixel() { return bpPerPixel;} // DrawingPanel.drawToScale
	
	protected boolean hasLoad() {return hasLoad;}
	
	// Implements the +/- buttons. 
	// It stores the current region width and center so that (-) can exactly undo (+) even
	// when the (+) expands beyond the bounds of one sequence.
	public void changeAlignRegion(double factor)  { // DrawingPanel
		int s = chrDisplayStart;
		int e = chrDisplayEnd;
		int mid = (s + e)/2;
		int width = (e - s)/2;
		boolean showingFull = (2*width > .95*getTrackSize());
		
		if (curCenter == 0) {
			curCenter = mid;
			curWidth = width;
		}
		
		curWidth *= factor;
		
		int sNew = Math.max(1,curCenter - curWidth);
		int eNew = Math.min(curCenter + curWidth,getTrackSize());
		
		setStartBP(sNew, false);
		setEndBP(eNew, false);
		
		if (showingFull && factor > 1) return; // don't scale if already at max
		
		double displayedWidth = (eNew - sNew)/2;
		double effectiveFactor = displayedWidth/width;
		
		setBpPerPixel( bpPerPixel * ( 1.0 / effectiveFactor ) );
	}
	public boolean fullyExpanded()  { // Drawing panel
		int s = getStart();
		int e = getEnd();
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
		if (chrDisplayEnd != chrSize) {
			chrDisplayEnd = chrSize;
			setAllBuild();
		}
	}
	public void resetStart() {// DrawingPanel, Sequence
		if (chrDisplayStart != 0) {
			chrDisplayStart = 0;
			setAllBuild();
		}
	}
	public void setStart(int startValue)  { // DrawingPanel, Sfilter; CAS514 was throwing exception and stopping symap
		if (startValue > chrSize) {
			Utilities.showWarningMessage("Start value "+startValue+" is greater than size "+chrSize+".");
			return;
		}
		if (startValue < 0) {
			Utilities.showWarningMessage("Start value is less than zero.");
			return;
		}
		if (chrDisplayStart != startValue) {
			firstBuild = true;

			chrDisplayStart = startValue;
			setAllBuild();
		}
	}
	public int setEnd(int endValue) { // DrawingPanel, Sfilter; CAS514 was throwing exception and stopping symap
		if (endValue < 0) {
			Utilities.showWarningMessage("End value is less than zero.");
			return chrSize;
		}
		if (endValue > chrSize) endValue = chrSize;

		if (chrDisplayEnd != endValue) {
			firstBuild = true;

			chrDisplayEnd = endValue;
			setAllBuild();
		}
		return endValue;
	}
	public void setStartBP(int startBP, boolean resetPlusMinus)  { // Track, DrawingPanel, Sequence
		if (startBP > chrSize) {
			Utilities.showWarningMessage("Start value "+startBP+" is greater than size "+chrSize+".");
			return;
		}
		if (startBP < 0) {
			Utilities.showWarningMessage("Start value "+startBP+" is less than zero.");
			return;
		}
		if (resetPlusMinus) curCenter = 0;
		if (chrDisplayStart != startBP) {
			firstBuild = true;
			chrDisplayStart = startBP;
			setAllBuild();
		}
	}
	public int setEndBP(int endBP, boolean resetPlusMinus)  {// Track, DrawingPanel, Sequence
		if (endBP < 0) Utilities.showWarningMessage("End value is less than zero.");

		if (endBP > chrSize || endBP<0) endBP = chrSize;

		if (resetPlusMinus) curCenter = 0;
		if (chrDisplayEnd != endBP) {
			firstBuild = true;
			chrDisplayEnd = endBP;
			setAllBuild();
		}
		return endBP;
	}
	public void setDefPixel() {
		bpPerPixel = (chrDisplayEnd - chrDisplayStart) / getAvailPixels();
		if (bpPerPixel < MIN_DEFAULT_BP_PER_PIXEL) 		bpPerPixel = MIN_DEFAULT_BP_PER_PIXEL;
		else if (bpPerPixel > MAX_DEFAULT_BP_PER_PIXEL) bpPerPixel = MAX_DEFAULT_BP_PER_PIXEL;
		
		defaultBpPerPixel = bpPerPixel;
		setAllBuild();
	}

	protected void setAllBuild() { // TrackData and all methods that data
		if (drawingPanel != null)
			drawingPanel.setTrackBuild(); // sets hasBuild to false on all tracks
		else setTrackBuild();
	}
	public void setTrackBuild() {hasBuild = false; } // DrawingPanel, Sequence	
	
	public void setWideWidth(boolean b) {
		if (b) width = DEFAULT_WIDE_WIDTH;
		else width =  DEFAULT_WIDTH;
	}
	public int getStart() {return chrDisplayStart;} // Track, Sequence, Mapper
	public int getEnd() {return chrDisplayEnd; }
	public int getTrackSize() {return chrSize;}

	protected Dimension getDimension() {return dimension; } // TrackHolder

	protected double getValue(int cb, String units) { return BpNumber.getValue(units, cb,bpPerPixel);} // Sfilter
	private double getPixelValue(int num) {return (double)num/(double)bpPerPixel;}
	
	private double getAvailPixels() {
		return (drawingPanel.getViewHeight() - (trackOffset.getY() * 2));
	}
	
	private int getBP(double y) {// Sequence mouseReleased
		y -= rect.getY();
		if (y < 0)						y = 0;
		else if (y > rect.getHeight())	y = rect.getHeight();
		y *= bpPerPixel;
		y += chrDisplayStart;

		if (y != Math.round(y)) 		y = Math.round(y);

		if (y < chrDisplayStart)		y = chrDisplayStart;
		else if (y > chrDisplayEnd)	y = chrDisplayEnd;

		return (int)Math.round(y);
	}

	private boolean isSouthResizePoint(Point p) {
		return (p.getX() >= rect.x &&
				p.getX() <= rect.x + rect.width && 
				p.getY() >= (rect.y+rect.height) - MOUSE_PADDING && 
				p.getY() <= (rect.y+rect.height) + MOUSE_PADDING);
	}
	
	/**** Clear for sequence and track */
	public void clearData() { // DrawingPanel.clearData, resetData
		ruleList.clear();
		allAnnoVec.clear();
		geneVec.clear();
		olapMap.clear();
	}

	private boolean isCleared(Rectangle r) {
		return r.x == 0 && r.y == 0 && r.width == Globals.NO_VALUE && r.height == Globals.NO_VALUE;
	}

	/*** XXX Mouse **************************/
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
				if (buildGraphics()) layout();
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
		
		// Sequence Close-up
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
	// mouseIn click in anno space: if click on gene, popup info; else pass to parent
	public void mousePressed(MouseEvent e) {
		// Sequence 1st
		if (e.isPopupTrigger()) { 					// CAS516 add popup on right click
			String title = getTitle(); 				// DisplayName Chr
			Point p = e.getPoint();
			if (rect.contains(p)) { 				// within blue rectangle of track
				for (Annotation annot : geneVec) { 	// CAS548x was allAnnoVec
					if (annot.contains(p)) {		// in this gene annotation
						setForGenePopup(annot);	
						annot.popupDesc(getHolder(), title, title);
						if (sfilObj.bHighGenePopup) drawingPanel.smake("seq: mousepressed"); // CAS544 to highlight
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
	public void mouseReleased(MouseEvent e) { 
		// Sequence
		if (drawingPanel.isMouseFunctionPop())  {
			if (dragRect.getHeight() > 0 /*&& rect.contains(p)*/) {
				int start = getBP( dragRect.getY() );
				int end =   getBP( dragRect.getY() + dragRect.getHeight() );
				
				if (sfilObj.bFlipped) {
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
					int newStart = getBP(dragRect.y);
					int newEnd   = getBP(dragRect.y+dragRect.height);
					if (newEnd != newStart) {
						if (sfilObj.bFlipped) { 
							int temp = newStart;
							newStart = chrDisplayEnd - newEnd + chrDisplayStart;
							newEnd = chrDisplayEnd - temp + chrDisplayStart;
						}
						setEndBP(newEnd, true);
						setStartBP(newStart, true);
						
						if (drawingPanel.isMouseFunctionZoomAll())
							drawingPanel.zoomAllTracks(this, (int)newStart, (int)newEnd);
						
						if (!hasBuild) {
							clearHeight(); // want to resize to screen
							needUpdate = true;
							if (drawingPanel != null) drawingPanel.smake("seq: mouse release");
						}
					}
				} catch (Exception ex) {ErrorReport.print(ex, "Exception resizing track!");}
			}
			if (needUpdate)
					// CAS552 || (startResizeBpPerPixel!=Globals.NO_VALUE && startResizeBpPerPixel!=bpPerPixel))
					// CAS551 || (!isCleared(startMoveOffset) && !startMoveOffset.equals(moveOffset))
			{
				drawingPanel.updateHistory();
				// CAS552 clearMouseSettings();
			}
			clearMouseSettings();
		}
	}
	
	public void mouseWheelMoved(MouseWheelEvent e) { 
		int notches = e.getWheelRotation();
		int viewSize = getEnd() - getStart() + 1;
		
		if (e.isControlDown()) // Zoom 
			zoomRange(notches, e.getPoint().getY() - rect.y + 1, viewSize);
		else
			scrollRange(notches, viewSize);
	}
	
	public void mouseWheelMoved(MouseWheelEvent e, int viewSize) { 
		int notches = e.getWheelRotation();
		
		if (e.isControlDown()) // Zoom - 
			zoomRange(notches, e.getPoint().getY() - rect.y + 1, viewSize);
		else
			scrollRange(notches, viewSize);
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
	public void mouseEntered(MouseEvent e) { holder.requestFocusInWindow();}
	public void mouseClicked(MouseEvent e) { }
	public void mouseExited(MouseEvent e) { 	}
    public void keyTyped(KeyEvent e) { } 
    public void keyPressed(KeyEvent e) { } 
    public void keyReleased(KeyEvent e) { } 
    
    //** Mouse methods **/
    private void repaint() {
		if (drawingPanel != null) drawingPanel.repaint();
		else if (holder != null) holder.repaint();
	}
    private Cursor getCursor() {
		return drawingPanel.getCursor(); 	
	}
	private void setCursor(Cursor c) {
		if (c == null) 	drawingPanel.setCursor(Globals.DEFAULT_CURSOR); 
		else			drawingPanel.setCursor(c); 
	}
	private void layout() { // mouseDragged
		if (holder != null && holder.getParent() instanceof JComponent) {
			((JComponent)holder.getParent()).doLayout();
			((JComponent)holder.getParent()).repaint();
		}
		else if (drawingPanel != null) {
			drawingPanel.doLayout();
			drawingPanel.repaint();
		}
	}
	private void clearMouseSettings() {
		if (!isCleared(dragRect)) {
			dragRect.setRect(0,0,Globals.NO_VALUE,Globals.NO_VALUE);;
			repaint();
		}
		//startMoveOffset.setLocation(Globals.NO_VALUE,Globals.NO_VALUE);
		adjustMoveOffset.setLocation(Globals.NO_VALUE,Globals.NO_VALUE);
		dragPoint.setLocation(Globals.NO_VALUE,Globals.NO_VALUE);
		startResizeBpPerPixel = Globals.NO_VALUE;
	}
	// Scroll wheel
	// Called from mouseWheelMoved, which is called from Mapper.mouseWheelMoved
	// CAS560 if sequence is flipped, should go negative instead of continued positive
	private void scrollRange(int notches, int viewSize) {
		int curViewSize = getEnd() - getStart() + 1;
		
		if (curViewSize >= getTrackSize()) return;

		int offset = notches*(viewSize/mouseWheelScrollFactor);
		int newStart, newEnd;
		
		newStart = (sfilObj.bFlipped) ? (chrDisplayStart-offset) : (chrDisplayStart+offset);
		if (newStart < 0) {
			newStart = 0;
			newEnd = viewSize;
		}
		else {
			newEnd = (sfilObj.bFlipped) ? (chrDisplayEnd-offset) : (chrDisplayEnd+offset);
			if (newEnd > getTrackSize()) {
				newStart = getTrackSize() - viewSize;
				newEnd = getTrackSize();
			}
		}
		
		setStartBP(newStart,true);
		setEndBP(newEnd,true);			
		if (drawingPanel != null) drawingPanel.smake("seq: scrollrange");
	}
	private void zoomRange(int notches, double focus, int length) {
		double r1 = (focus / rect.height) / mouseWheelZoomFactor;
		double r2 = ((rect.height - focus) / rect.height) / mouseWheelZoomFactor;
		if (sfilObj.bFlipped) {
			double temp = r1;
			r1 = r2;
			r2 = temp;
		}
		int newStart = chrDisplayStart + (int)(length*r1*notches);
		int newEnd   = chrDisplayEnd - (int)(length*r2*notches);

		if (newEnd < 0) newEnd = 0; // unnecessary?
		else if (newEnd > getTrackSize()) newEnd = getTrackSize();
		if (newStart < 0) newStart = 0;
		else if (newStart > getTrackSize()) newStart = getTrackSize(); // unnecessary?
		if (newStart < newEnd) {
			setEndBP(newEnd,true);
			setStartBP(newStart,true);
			if (drawingPanel != null) drawingPanel.smake("seq: zoom range");
		}
	}
	// called on mousePressed for Gene Popup
	private void setForGenePopup(Annotation annot) { 
		annot.setExonList(); // CAS548 was sending full exonList when annot already has its own
		
		if (hitsObj1!=null) {// CAS517 added; CAS548 change format to start:end[+], add score
			if (!annot.hasHitList()) { 
				boolean isAlgo1 = propDB.isAlgo1(projIdx, otherProjIdx);
				TreeMap <Integer, String> scoreMap = 
						seqPool.getGeneHits(annot.getAnnoIdx(), grpIdx, isAlgo1);
				if (scoreMap.size()>0) {
					String hits = hitsObj1.getHitsForGenePopup(this, annot, scoreMap);
					annot.setHitList(hits);
				}
			}
		}
		if (hitsObj2!=null) {// CAS543 added for 3-track
			if (!annot.hasHitList2()) { 
				boolean isAlgo1 = propDB.isAlgo1(projIdx, otherProjIdx);
				TreeMap <Integer, String> scoreMap = seqPool.getGeneHits(annot.getAnnoIdx(), grpIdx, isAlgo1);
				if (scoreMap.size()>0) {
					String hits = hitsObj2.getHitsForGenePopup(this, annot, scoreMap);
					annot.setHitList2(hits);
				}
			}
		}
	}
	/*************************************************************************/
    // Track
	private static final int REFPOS=2;
	private static final int MAX_PIXEL_HEIGHT = 10000;
	private static final Color REFNAME = new Color(0,0,200); // CAS530 change from red to deep blue
	private static final Color titleColor = Color.black;
	private static final Color dragColor = new Color(255,255,255,120);
	private static final Color dragBorder = Color.black;
	private static final Font titleFont = new Font("Arial", 1, 14);

	// Sequence
	private static final int OVERLAP_OFFSET = 18;	// CAS518 for overlap and yellow text; CAS548 was 12
	
	private static final double MOUSE_PADDING = 2;

	private static final double OFFSET_SPACE = 7;	// header/footer
	private static final double OFFSET_SMALL = 1;
	
	private static final double  DEFAULT_WIDTH = 90.0; // Width of track; CAS551 was 100.0
	private static final double  DEFAULT_WIDE_WIDTH = 100.0;
	
	private static final double EXON_WIDTH = 15.0;
	private static final int    GENE_DIV = 4; // Gene is this much narrower than exon
	private static final int MIN_BP_FOR_ANNOT_DESC = 500; // minimum bp for show anno
	private static final int GENES_FOR_ANNOT_DESC = 20;   // average distance for 20 genes
	
	private static final double SPACE_BETWEEN_RULES = 35.0;
	private static final double RULER_LINE_LENGTH = 3.0;
	
	private static final double MIN_DEFAULT_BP_PER_PIXEL = 1; 
	private static final double MAX_DEFAULT_BP_PER_PIXEL =1000000;
	
	private static final Color border = Color.black; 
	private static Color footerColor = Color.black;
	private static Font footerFont = new Font("Arial",0,10);
	private static Font unitFont = new Font("Arial",0,10);
	private static final Point2D defaultSequenceOffset = new Point2D.Double(20,40); 
	private static final Point2D titleOffset = new Point2D.Double(1,3); 
	private static int mouseWheelScrollFactor = 20;	
	private static int mouseWheelZoomFactor = 4; 	
	
	public static Color unitColor, bgColor1, bgColor2; 
	
	static { // CAS532 remove everything except colors that can be set by user
		PropertiesReader props = new PropertiesReader(Globals.class.getResource("/properties/sequence.properties"));
	
		unitColor                  = props.getColor("unitColor");
		bgColor1            	   = props.getColor("bgColor1");
		bgColor2            	   = props.getColor("bgColor2");
	}
}
