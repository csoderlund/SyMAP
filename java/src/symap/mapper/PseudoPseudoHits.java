package symap.mapper;

/***********************************************
 * Two ends of a seq-seq hit; internal class PseudoHit has both ends
 * Mapper: Tracks 0-N are in order of display
 * Hit Wire is the line between chromosomes
 * Hit Rect is the hit rectangle in the track, one on each end of wire
 * 
 * OO programming is the ultimate GOTO - this is total spagetti code!
 * abstract classes should be banned with the GOTO - information should NOT be hidden
 */
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.event.MouseEvent;
import java.awt.Color;
import java.awt.Dimension;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedList;

import symap.sequence.Sequence;
import util.Utilities;
import symap.SyMAPConstants;

public class PseudoPseudoHits extends AbstractHitData implements Hits, SyMAPConstants {
	private static final int MOUSE_PADDING = 3;
	
	private Mapper mapper;
	private Sequence seqObj1;
	private Sequence seqObj2;
	private PseudoHit[] allHitsArray;
	private boolean st1LTst2=true;

	// called from below
	public PseudoPseudoHits(int p1ID, int group1, int p2ID, int group2, boolean swap) {
		super(p1ID,group1,p2ID,group2,Mapper.PSEUDO2PSEUDO,swap);		
	}
	// called in MapperPool
	public PseudoPseudoHits(Mapper mapper, Sequence st1, Sequence st2, PseudoPseudoData data, boolean swap) {
		this(data.getProject1(),data.getGroup1(),data.getProject2(),data.getGroup2(),swap /*always false*/);
		
		this.mapper = mapper;
		this.seqObj1 = st1;
		this.seqObj2 = st2;
	
		seqObj1.setPseudoPseudoHits(this, mapper.isQuery(seqObj1.getHolder().getTrack())); // CAS517 add to get hits 
		seqObj2.setPseudoPseudoHits(this, mapper.isQuery(seqObj2.getHolder().getTrack()));
		setHits(data);
		
		// CAS517 add so that the 1st track is listed first on hover and popups
		int t1 = seqObj1.getHolder().getTrackNum(); 
		int t2 = seqObj2.getHolder().getTrackNum(); 
		st1LTst2 = (t1<t2);
	}

	public PseudoPseudoData.PseudoHitData[] getPseudoHitData() {
		PseudoPseudoData.PseudoHitData[] phd = new PseudoPseudoData.PseudoHitData[allHitsArray == null ? 0 : allHitsArray.length];
		for (int i = 0; i < phd.length; i++) phd[i] = allHitsArray[i].phDataObj;
		return phd;
	}

	/****************************************************************
	 * PseudoPseudoHits calls
	 * isSelected - for TableDataPanel which has a selected row, that stays highlight in green
	 */
	public boolean setHits(PseudoPseudoData data) {
		PseudoPseudoData.PseudoHitData[] phd = data.getPseudoHitData();
			
		allHitsArray = new PseudoHit[data == null || phd == null ? 0 : phd.length];
		for (int i = 0; i < allHitsArray.length; i++) {
			boolean isSelected = mapper.isHitSelected(phd[i].getStart1(), phd[i].getEnd1(), phd[i].getStart2(),phd[i].getEnd2());	
			allHitsArray[i] = new PseudoHit(phd[i]);
			allHitsArray[i].set(isSelected);
		}
		return true;
	}
	// MapperPool.setPseudoPseudoData call
	public boolean addHits(int newHitContent, Collection <HitData> hitData) {
		if (allHitsArray == null) allHitsArray = new PseudoHit[0];
		if (hitData == null) hitData = new LinkedList <HitData> ();

		PseudoHit h[] = new PseudoHit[allHitsArray.length+hitData.size()];
		System.arraycopy(allHitsArray,0,h,0,allHitsArray.length);
		int i = allHitsArray.length;
		allHitsArray = h;
		for (Iterator <HitData> iter = hitData.iterator(); i < allHitsArray.length; i++) {
			allHitsArray[i] = new PseudoHit((PseudoPseudoData.PseudoHitData)iter.next());
			allHitsArray[i].set(false);
		}
		return true;
	}
	
	public void clear() {
		for (PseudoHit h : allHitsArray)
			h.clear();	
	}
	
	public void setMinMax(HitFilter hf) { 
		for (PseudoHit h : allHitsArray)
			h.setMinMax(hf);
	}
	public void getSequenceMinMax(int[] minMax) {
		for (PseudoHit h : allHitsArray)
			h.getSequenceMinMax(minMax);
	}
	public void getMinMax(int[] minMax, int start, int end, boolean swap) {
		for (PseudoHit h : allHitsArray) {
			if (h.isContained(start, end, swap)) {
				minMax[0] = Math.min( minMax[0], h.phDataObj.getStart1(swap) );
				minMax[1] = Math.max( minMax[1], h.phDataObj.getEnd1(swap) );
			}
		}
	}
	// Close up
	 public void getHitsInRange(List<AbstractHitData> hitList, int start, int end, boolean swap) {
		 List<AbstractHitData> h = new LinkedList<AbstractHitData>();
		 for (int i = 0; i < allHitsArray.length; i++)
			 allHitsArray[i].getHit(h,start,end,swap);
		 if (!h.isEmpty()) 
			 hitList.add(new PseudoPseudoData(getProject1(),getGroup1(),getProject2(),getGroup2(),
					 getHitContent(),h,swap));
	 }
	 /*****************************************************
	  * Paint all hits
	  */
	 public void paintComponent(Graphics2D g2) {
		Point stLocation1 = seqObj1.getLocation();
		Point stLocation2 = seqObj2.getLocation();
		int trackRelativePos1 = mapper.getTrackPosition(seqObj1); // left or right
		int trackRelativePos2 = mapper.getTrackPosition(seqObj2);
		List<PseudoHit> hHits = new LinkedList<PseudoHit>();

		// Draw non-highlighted hits first
		for (PseudoHit h : allHitsArray) {
			if (h.isHover()) hHits.add(h);
			else h.paintComponent(g2,trackRelativePos1,trackRelativePos2,stLocation1,stLocation2);
		}

		// Draw highlighted hits on top
		for (PseudoHit h : hHits)
			h.paintComponent(g2,trackRelativePos1,trackRelativePos2,stLocation1,stLocation2);
	}
	/*************************************************************
	 * Hover and popups
	 */
	// CAS517 For sequence object - hits aligned to a gene; Annotation.popDesc expects this format
	public String getHitsStr(Sequence seqObj, int start, int end) {
		String list= "";
		for (int i = 0; i < allHitsArray.length; i++) {
			 String x = allHitsArray[i].getIfHit(seqObj,start,end);
			 if (x!=null) list+= x + ";";
		}
		return list;
	}
	
	public void mouseMoved(MouseEvent e) {
		for (PseudoHit h : allHitsArray)
			h.mouseMoved(e);
	}
	// Need to do this otherwise hits will stay highlighted when exited.
	public void mouseExited(MouseEvent e) { 
		for (PseudoHit h : allHitsArray)
			h.setHover(false); 
		mapper.getDrawingPanel().repaint();
	}
	// CAS517 add popup on right click
	public boolean doPopupDesc(MouseEvent e) {
		if (e.isPopupTrigger()) { 
			Point p = e.getPoint();
			
			for (PseudoHit h : allHitsArray) {
				if (h.isContained(p)) {
					h.popupDesc();
					return true;
				}
			}
		}	
		return false;
	}
	
	/***************** Static stuff ***********************/
	 private static void getSequenceMinMax(int[] minMax, int start, int end) {
		 if (start < end) {
			 if (start < minMax[0]) minMax[0] = start;
			 if (end   > minMax[1]) minMax[1] = end;
		 }
		 else {
			 if (end   < minMax[0]) minMax[0] = end;
			 if (start > minMax[1]) minMax[1] = start;	    
		 }
	 }
	 private boolean isSequenceVisible(Sequence st, int start, int end) {
		 return st.isInRange( (start+end)>>1 );
	 }
	 private Point2D getSequenceCPoint(Sequence st, int start, int end, int orientation, Point loc) {
		 Point2D p = st.getPoint( (start+end)>>1 , orientation );
		 p.setLocation(p.getX()+loc.getX(),p.getY()+loc.getY());
		 return p;
	 }
	// CAS512 moved from Utilities because this is the only class to use it
	// adjust rectangle coordinates for negative width or height - on flipped
	private static void fixRect(Rectangle2D rect) {
		if (rect.getWidth() < 0)
			rect.setRect(rect.getX()+rect.getWidth()+1, rect.getY(), Math.abs(rect.getWidth()), rect.getHeight());
		
		if (rect.getHeight() < 0)
			rect.setRect(rect.getX(), rect.getY()+rect.getHeight()+1, rect.getWidth(), Math.abs(rect.getHeight()));
	}

	/**********************************************************************/
	/**********************************************************************
	 * XXXXX PseudoHit class for drawing, 1-1 with data
	 */
	private class PseudoHit implements Hit {
		private PseudoPseudoData.PseudoHitData phDataObj;
		private Line2D.Double hitWire;
		private Rectangle2D.Double fullHitRect1=null, fullHitRect2=null; // CAS517 ends of wire
		
		private boolean hover;		// hover line is highlighted; CAS517 remove boolean highlight, not used
		private boolean geneContained; 
		private boolean geneOverlap; 
		private boolean bSelected; // From TableDataPanel
		
		public PseudoHit(PseudoPseudoData.PseudoHitData data) {
			this.phDataObj = data;
			hitWire = new Line2D.Double();
			hover = false;
		}
		
		private void getHit(List hitList, int start, int end, boolean swap) { 
			if ( isContained(start, end, swap) )
				hitList.add(phDataObj);
		}
		
		public void setMinMax(HitFilter hf) {
			hf.condSetBesEvalue(phDataObj.getEvalue(),phDataObj.getEvalue());
			hf.condSetBesPctid(phDataObj.getPctid(),phDataObj.getPctid());
		}
		public void getSequenceMinMax(int[] minMax) {
			if (isVisible() && !isFiltered()) {
				PseudoPseudoHits.getSequenceMinMax(minMax,phDataObj.getStart2(),phDataObj.getEnd2());
			}
		}	
		
		public String getType() 		{return SEQ_TYPE;}
		public boolean isBlockHit() 	{return phDataObj.isBlockHit();}
		
		public boolean isHover() {return hover; } // for this hit wire object
		 
		public void set(boolean isSelectedHit) { // CAS516 change from setting highlight to bSelected
			bSelected = isSelectedHit;
			geneContained = isGeneContained();
			geneOverlap = isGeneOverlap();
		}
		
		public boolean isFiltered() {
			 HitFilter hitfilter = mapper.getHitFilter();
			 return false 
			 		|| (hitfilter.getBlock() && !phDataObj.isBlockHit()) 
			 		|| (hitfilter.getGeneContained() && !geneContained) 
			 		|| (hitfilter.getGeneOverlap() && !geneOverlap) 
			 		|| (hitfilter.getNonGene() && (geneOverlap || geneContained));
		 }
		 
		 public boolean isVisible() {
			 return isSequenceVisible(seqObj1, phDataObj.getStart1(), phDataObj.getEnd1()) &&
			 		isSequenceVisible(seqObj2, phDataObj.getStart2(), phDataObj.getEnd2());
		 }
		
		private boolean isGeneContained() {return (phDataObj.getOverlap() > 0); } 
		private boolean isGeneOverlap()   {return (phDataObj.getOverlap() > 0); }
	
		// is Hit Wire contained in hover
		public boolean isContained(int start, int end, boolean swap) { 
			return ( isVisible() && !isFiltered() && 
					( (phDataObj.getStart2(swap) >= start && phDataObj.getStart2(swap) <= end) ||
					  (phDataObj.getEnd2(swap)   >= start && phDataObj.getEnd2(swap) <= end) ||
					  (start >= phDataObj.getStart2(swap) && end <= phDataObj.getEnd2(swap)) ));
		}
				
		private boolean lineContains(Line2D.Double line, Point p) { 
			 double lx1 = line.getX1();
			 double lx2 = line.getX2();
			 double ly1 = line.getY1();
			 double ly2 = line.getY2();
			 double delta;
			 
			 delta = p.getY() - ly1 - ((ly2-ly1)/(lx2-lx1))*(p.getX()-lx1);
			 
			 return (delta >= -MOUSE_PADDING && delta <= MOUSE_PADDING);
		 }
		 protected boolean isContained(Point point) {
			 return isVisible() && lineContains(hitWire, point);
		 }
		 protected boolean setHover(Point point) {
			 boolean b = isVisible() && lineContains(hitWire, point);
			 return setHover(b); 
		 } 
		 protected boolean setHover(boolean hover) {
			if (hover != this.hover) {
				this.hover = hover;
				return true;
			}
			return false;
		 } 
		 private Color getCColor(String orient) {
			 if (isHover())return Mapper.pseudoLineHighlightColor;
			 else if (bSelected) 			return Color.green;				// CAS516 different color for selected
			 else if (orient.contentEquals("++")) return Mapper.pseudoLineColorPP; 
			 else if (orient.contentEquals("+-")) return Mapper.pseudoLineColorPN;
			 else if (orient.contentEquals("-+")) return Mapper.pseudoLineColorNP;
			 else if (orient.contentEquals("--")) return Mapper.pseudoLineColorNN;
			 else return Mapper.pseudoLineColorPP;
		 }
		 
		 /***************************************************************************
		  * Draw hit line connector, and hit info on seq1/seq2 rectangle (%ID, hitLen)
		  */
		 public void paintComponent(Graphics2D g2, int trackPos1, int trackPos2, Point stLocation1, Point stLocation2) {
			 if (!isVisible() || isFiltered()) return;
			 
		   /* get border locations of sequence tracks to draw hit stuff to/in */
			 Point2D sp1 = getSequenceCPoint(seqObj1, phDataObj.getStart1(), phDataObj.getEnd1(), trackPos1, stLocation1);
			 Point2D sp2 = getSequenceCPoint(seqObj2, phDataObj.getStart2(), phDataObj.getEnd2(), trackPos2, stLocation2);
			 int x1=(int)sp1.getX(), y1=(int)sp1.getY();
			 int x2=(int)sp2.getX(), y2=(int)sp2.getY();
			 
			/* hitLine: hit connecting seq1/seq2 chromosomes */
			 g2.setPaint(getCColor(phDataObj.getOrients())); // set color
			 hitWire.setLine(sp1,sp2); 
			 g2.draw(hitWire);	
			 		
			 int pctid = (int)phDataObj.getPctid();
			 int minPctid = (int)Math.min(mapper.getHitFilter().getMinBesPctid(),mapper.getHitFilter().getMinMrkPctid()); // FPC
					 	 
			 int lineLength = Math.max(1, 30*(pctid-minPctid+1)/(100-minPctid+1)); // CAS517 was only set if getShowScoreLine
			 if (trackPos1 == RIGHT_ORIENT) lineLength = 0 - lineLength; 
			 
			 /* %id line: paint in seq1/2 rectangle the %id length */
			 if (seqObj1.getShowScoreLine()) { // gets turned back on with redraws
				 g2.drawLine(x1,y1,x1-lineLength,y1);
			 }
			 if (seqObj2.getShowScoreLine()) {
				 g2.drawLine(x2,y2,x2+lineLength,y2);
			 }
			 
			 /* %id text: paint in seq1/2 rectangle the %id */
			 if (seqObj1.getShowScoreValue() || isHover()) {  	
				 double textX = x1;
				 if (x1 < x2) textX += -lineLength-15-(pctid==100 ? -7 : 0);
				 else 		  textX +=  lineLength;					 
				 
				 g2.setPaint(Color.black);
				 g2.drawString(""+(int)pctid, (int)textX, (int)y1);
			 }
			 if (seqObj2.getShowScoreValue() || isHover()) {  	
				 double textX = x2;
				 if (x2 < x1) 	textX += lineLength-15-(pctid==100 ? -7 : 0);
				 else 			textX += lineLength;					 
				 
				 g2.setPaint(Color.black);
				 g2.drawString(""+(int)pctid, (int)textX, (int)y2);					 
			 }
			/* hit graphics: paint in seq1/2 rectangle the hit length graphics */
			 if (seqObj1.getShowHitLen()) { 					
				 Point2D rp1 = seqObj1.getPoint(phDataObj.getStart1(), trackPos1);
				 Point2D rp2 = seqObj1.getPoint(phDataObj.getEnd1(),   trackPos1);
				 
				 if (Math.abs(rp2.getY()-rp1.getY()) > 3) { 		// only draw if it will be visible
					 rp1.setLocation(x1-lineLength,rp1.getY());				 
					 rp2.setLocation(x1-lineLength,rp2.getY());
					 
					 paintHitLen(g2, seqObj1, rp1, rp2, trackPos1, phDataObj.isPosOrient1());
				 }
			 }
			 if (seqObj2.getShowHitLen()) {
				 Point2D rp3 = seqObj2.getPoint(phDataObj.getStart2(), trackPos2);
				 Point2D rp4 = seqObj2.getPoint(phDataObj.getEnd2(),   trackPos2);
				 if (Math.abs(rp4.getY()-rp3.getY()) > 3) { 		// only draw if it will be visible
					 rp3.setLocation(x2+lineLength,rp3.getY());				 
					 rp4.setLocation(x2+lineLength,rp4.getY());
					 
					 paintHitLen(g2, seqObj2, rp3, rp4, trackPos2, phDataObj.isPosOrient2());
				 }
			 }
			 /* Hover over hit line connector; CAS517 move creation to HitData */
			 if (isHover()) { 						
				 mapper.setHelpText(phDataObj.createHover(st1LTst2));
			 }
			 else mapper.setHelpText(null);
		 }
		 /***************************************************************************
		  * Draw hit - draws the length of the hit along the blue chromosome box, which shows breaks between merged hitsd
		  */
		 private void paintHitLen(Graphics2D g2, Sequence st, Point2D pStart, Point2D pEnd, int trackPos, boolean forward) {
			 double psx = pStart.getX();
			 double psy = pStart.getY();
			 double w = Mapper.hitRibbonWidth;
			 
			 // for hover
			 Rectangle2D.Double hrect = new Rectangle2D.Double(psx, psy, w+1, (pEnd.getY()-psy));
			 if (st==seqObj1) 	fullHitRect1 = hrect;	// always query
			 else 				fullHitRect2 = hrect;   // always target
				 
			 String subHits;
			 Rectangle2D.Double rect;
			 
			 if (mapper.isQuery(st)) // CAS517 check isSelf in isQuery || (trackPos == LEFT_ORIENT && mapper.isSelf() ))
				 subHits = phDataObj.getQuerySeq();
			 else
				 subHits = phDataObj.getTargetSeq();

			 if (subHits == null || subHits.length() == 0) {// CAS512 long time bug when flipped and one hit
				 g2.setPaint(getCColor(phDataObj.getOrients()));
				 rect = new Rectangle2D.Double(psx, psy, w, pEnd.getY()-psy);
				 
				 fixRect(rect); // for flipped track
				 g2.fill(rect);
			 }
			 else {
				 // Draw background rectangle
				 g2.setPaint(Mapper.hitRibbonBackgroundColor); // grey between merged hits
				 rect = new Rectangle2D.Double(psx, psy, w, pEnd.getY()-psy);
				 fixRect(rect); // for flipped track
				 g2.fill(rect);
				 g2.draw(rect);
				 
				 g2.setPaint(getCColor(phDataObj.getOrients()));
				 
				 // Draw sub-hits
				 String[] subseq = subHits.split(",");
				 for (int i = 0;  i < subseq.length;  i++) {
					 String[] pos = subseq[i].split(":");
					 long start = Long.parseLong(pos[0]);
					 long end =   Long.parseLong(pos[1]);
					 
					 Point2D p1 = st.getPoint(start, trackPos);
					 p1.setLocation( pStart.getX(), p1.getY() );
					 
					 Point2D p2 = st.getPoint(end, trackPos);
					 p2.setLocation( pStart.getX(), p2.getY() );
					 
					 rect.setRect( p1.getX(), p1.getY(), w, p2.getY()-p1.getY() );
					 fixRect(rect); // for flipped track
					 g2.fill(rect);
					 g2.draw(rect);
				 }
			 }
		 }
		 public void clear() { }
		 
		 // provide hover
		 public void mouseMoved(MouseEvent e) {
			 if (setHover(e.getPoint()))
				 mapper.getDrawingPanel().repaint();
		 }
		 /* CAS517 called for gene annotation from Sequence.java */
		public String getIfHit(Sequence st, int start, int end) {
			boolean isQuery = mapper.isQuery(st);
			if (!isContained(start, end, isQuery)) return null;
			
			boolean isPos = (isQuery) ? phDataObj.isPosOrient1() : phDataObj.isPosOrient2();
			String o = ""; // (isPos) ? "+" : "-"; CAS517x 
			String hit = "Hit #" + phDataObj.getID() + " " + o + "\n";
			
			if (isQuery) 	return hit + phDataObj.getQueryBounds();
			else 			return hit + phDataObj.getTargetBounds();
		}
		/* CAS516 popup from clicking hit wire 
		 * */
		public void popupDesc() { 
			String title="Hit #" + phDataObj.getID();
			
			String msg = phDataObj.createHover(st1LTst2) + "\n"; 
			
			String q = phDataObj.getQueryBounds();
			
			if (q!=null && q.length()>0) {
				String msg1 = "\n" + Utilities.formatHit(seqObj1.getFullName() + "\n" + q);
				String msg2 = "\n" + Utilities.formatHit(seqObj2.getFullName() + "\n" + phDataObj.getTargetBounds());
				
				msg +=  st1LTst2 ? (msg1+msg2) : (msg2+msg1);
			}
			Dimension d = new Dimension (350, 220); 
			Utilities.displayInfoMonoSpace(mapper, title, msg,  d, 0.0, 0.0); 
		}
	 } // End class PseudoHit
}
