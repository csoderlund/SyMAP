package symap.mapper;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.event.MouseEvent;
import java.awt.Color;
import java.awt.Font;
import java.util.List;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.HashSet;
import java.util.Vector;

import symap.closeup.TextShowInfo;
import symap.closeup.SeqData;
import symap.sequence.Annotation;
import symap.sequence.Sequence;
import symap.Globals;

/***********************************************
 * Two ends of a seq-seq hit; internal class PseudoHit has both ends
 * Mapper: Tracks 0-N are in order of display; Ref is always seqObj1
 * Hit Wire is the line between chromosomes
 * Hit Rect is the hit rectangle in the track, one on each end of wire
 * 
 * CAS531 renamed from PseudoPseudoHits and removed extends AbstractHitData; CAS545 PseudoHit->DrawHit
 */

public class SeqHits  {
	private static final int MOUSE_PADDING = 3;
	private static Font textFont = new Font(Font.MONOSPACED,Font.PLAIN,12);
	
	private int projIdx1, projIdx2; // project IDs
	private int grpIdx1, grpIdx2; // groupIDs: contig or chromosome 
	
	private Mapper mapper;
	private DrawHit[] allHitsArray;
	
	// query has dbName < target; seqObj1 is query (corresponds to pseudo_hits.annot1_idx); 
	private Sequence seqObj1, seqObj2; // query, target; query may be the left or right track
	private boolean st1LTst2=true; 	   // if seqObj1.track# <seqObj2.track#, query is left track, else, right
	
	private String infoMsg=""; // CAS541
	private int cntShowHit=0, cntHighHit=0, cntConserved=0;

	// called in MapperPool
	public SeqHits(Mapper mapper, Sequence st1, Sequence st2, Vector <HitData> hitList) {
		this.mapper = mapper;
		this.seqObj1 = st1;
		this.seqObj2 = st2;
		
		allHitsArray = new DrawHit[hitList.size()];
		for (int i = 0; i < allHitsArray.length; i++) {
			HitData hd = hitList.get(i);
			boolean isSelected = mapper.isQuerySelHit(hd.getStart1(), hd.getEnd1(), hd.getStart2(),hd.getEnd2());	
			allHitsArray[i] = new DrawHit(hd);
			allHitsArray[i].set(isSelected);
		}
		
		projIdx1 = st1.getProject();
		projIdx2 = st2.getProject();
		grpIdx1  = st1.getGroup(); 
		grpIdx2  = st2.getGroup(); 
		
		seqObj1.setSeqHits(this, mapper.isQueryTrack(seqObj1.getHolder().getTrack())); // CAS517 add so seqObj has hits 
		seqObj2.setSeqHits(this, mapper.isQueryTrack(seqObj2.getHolder().getTrack()));
		
		// CAS517 add so that the 1st track is listed first on hover and popups
		int t1 = seqObj1.getHolder().getTrackNum(); 
		int t2 = seqObj2.getHolder().getTrackNum(); 
		st1LTst2 = (t1<t2);
	}
	public String getInfo() { return infoMsg;}; // CAS541 called in Mapper
	
	// called from mapper.myinit sets for the Hit Filter %id slider
	public void setMinMax(HfilterData hf) { 
		for (DrawHit h : allHitsArray)
			h.setMinMax(hf);
	}
	
	public void getMinMax(int[] minMax, int start, int end, boolean swap) {
		for (DrawHit h : allHitsArray) {
			if (h.isContained(start, end, swap)) {
				minMax[0] = Math.min( minMax[0], h.hitDataObj.getStart1(swap) );
				minMax[1] = Math.max( minMax[1], h.hitDataObj.getEnd1(swap) );
			}
		}
	}
	// Close up 
	 public void getHitsInRange(Vector <HitData> hitList, int start, int end, boolean swap) {
		 for (int i = 0; i < allHitsArray.length; i++) 
			 allHitsArray[i].getHit(hitList,start,end,swap); // adds to hitList
	 }
	 // SeqPool Conserved
	 public Vector <HitData>  getVisGene2Hits() {
		 Vector <HitData> hitList = new Vector<HitData> ();
		 for (DrawHit hObj : allHitsArray) {
			 if (hObj.isOlapHit() && !hObj.isFiltered()) {
				 if (hObj.hitDataObj.is2Gene())
					 hitList.add(hObj.hitDataObj);		 
			 }
		 }
		 return hitList;
	 }
	 public void clearConserved() { // called from Sequence (SeqPool sets conserved per hit)
		 for (DrawHit hd: allHitsArray) 
			 if (hd.hitDataObj.is2Gene()) 
				 hd.hitDataObj.setIsConserved(false);
	 }
	 public Sequence getSeqObj1() {return seqObj1;}
	 public Sequence getSeqObj2() {return seqObj2;}
	 
	 /*****************************************************
	  * XXX Paint all hits 	// CAS520 add highlight to block and collinear
	  */
	 public void paintComponent(Graphics2D g2) {
		Point stLoc1 = seqObj1.getLocation();
		Point stLoc2 = seqObj2.getLocation();
		int trackPos1 = mapper.getTrackPosition(seqObj1); // left or right
		int trackPos2 = mapper.getTrackPosition(seqObj2);
		List<DrawHit> hHits = new LinkedList<DrawHit>();
		
		TreeMap <Integer, Boolean> highMap = new TreeMap <Integer, Boolean> (); // if collinear or blocks is highlighted
		boolean toggle=true; 
		boolean bHiCset  = mapper.getHitFilter().isHiCset();
		boolean bHiBlock = mapper.getHitFilter().isHiBlock();
		boolean bHi = (bHiCset || bHiBlock);
		
		// allHitArray sorted by start1; this does not correspond to sequential blocks (sorted sets).
		if (bHi) {
			for (DrawHit h : allHitsArray) {
				int set = (bHiCset) ?  h.getCollinearSet() : h.getBlock();
				if (set!=0 && !highMap.containsKey(set)) {
					highMap.put(set, toggle);
					toggle = !toggle;
				}
			}
			toggle=true;
			if (bHiCset) { //sorted by start1 does not correspond to sequence sets
				for (int set : highMap.keySet()) {
					highMap.put(set, toggle);
					toggle = !toggle;
				}
			}
		}
	
		cntShowHit=cntHighHit=cntConserved=0; // counted in paint
		HashSet <Integer> blockSet = new HashSet <Integer> (); // CAS541 add to count blocks/sets shown
		toggle=true; 
		for (DrawHit dh : allHitsArray) {
			if (bHi) {
				int set = (bHiCset) ?  dh.getCollinearSet() : dh.getBlock();
				if (set!=0) toggle = highMap.get(set);
			}
			if (dh.isHover) hHits.add(dh);
			else {
				boolean show = dh.paintComponent(g2, trackPos1, trackPos2, stLoc1, stLoc2, toggle);
				if (bHi && show) {
					int set = (bHiCset) ?  dh.getCollinearSet() : dh.getBlock();
					if (set!=0 && !blockSet.contains(set)) blockSet.add(set);
				}
			}
		}
		
		// Draw highlighted hits on top with highlight
		for (DrawHit dh : hHits) {
			boolean show = dh.paintComponent(g2,trackPos1,trackPos2,stLoc1,stLoc2, true);
			if (bHi && show) {
				int set = (bHiCset) ?  dh.getCollinearSet() : dh.getBlock();
				if (set!=0 && !blockSet.contains(set)) blockSet.add(set);
			}
		}
		
		// Information box on Stats
		infoMsg = String.format("%s: %,d", "Hits", cntShowHit);
		if (cntConserved>0) infoMsg += String.format("\n%s: %,d", "Conserved", cntConserved);
		if (cntHighHit>0)   infoMsg += String.format("\n%s: %,d", "High", cntHighHit);
		if (bHi) {
			String type = (bHiCset) ? "Sets" : "Blocks";
			infoMsg += String.format("\n%s: %,d\n", type, blockSet.size());
		}
	
	}
	/*************************************************************
	 * Hover and popups
	 */
	// CAS517 For sequence object - hits aligned to a gene; Annotation.popDesc expects this format
	// CAS548 replace getHitStr, which displayed all subs; add score; change format 
	public String getHitsForGenePopup(Sequence seqObj, Annotation annoObj, TreeMap <Integer, String> hitScores) {
		String listVis= "";
		
		for (DrawHit drObj : allHitsArray) {
			HitData hdObj = drObj.hitDataObj;
			if (!hitScores.containsKey((int) hdObj.getID())) continue;
			
			boolean isQuery = mapper.isQueryTrack(seqObj);
			
			String score = "("+hitScores.get((int) hdObj.getID())+")";			
			String star = hdObj.getMinorForGenePopup(isQuery, annoObj.getAnnoIdx());

			if (!star.equals("*")) { // minor gets the wrong opposite gene 
				String hit = String.format("Hit #%d %-19s\n", hdObj.getHitNum(), score);
				hit += hdObj.getCoordsForGenePopup(isQuery, annoObj.getFullGeneNum()) + "\n";
			
				listVis += hit + ";";
			}
		}
		return listVis;
	}
	
	public void mouseMoved(MouseEvent e) {
		for (DrawHit h : allHitsArray)
			h.mouseMoved(e);
	}
	// Need to do this otherwise hits will stay highlighted when exited.
	public void mouseExited(MouseEvent e) { 
		for (DrawHit h : allHitsArray)
			h.isHover = false; // CAS550 was calling setHover
		mapper.getDrawingPanel().repaint();
	}
	// CAS517 add popup on right click
	public boolean doPopupDesc(MouseEvent e) {
		if (e.isPopupTrigger()) { 
			Point p = e.getPoint();
			
			for (DrawHit h : allHitsArray) {
				if (h.isHover && h.isContained(p)) {
					h.popupDesc(p.getX(), p.getY());
					return true;
				}
			}
		}	
		return false;
	}
	public void clearData() {
		 allHitsArray=null;
	}
	 /***************** Static stuff ***********************/
	// CAS512 moved from Utilities because this is the only class to use it
	// adjust rectangle coordinates for negative width or height - on flipped
	private static void fixRect(Rectangle2D rect) {
		if (rect.getWidth() < 0)
			rect.setRect(rect.getX()+rect.getWidth()+1, rect.getY(), Math.abs(rect.getWidth()), rect.getHeight());
		
		if (rect.getHeight() < 0)
			rect.setRect(rect.getX(), rect.getY()+rect.getHeight()+1, rect.getWidth(), Math.abs(rect.getHeight()));
	}

	public boolean equals(SeqHits d) {
		return d.projIdx1 == projIdx1 && d.projIdx2 == projIdx2
				&& d.grpIdx1 == grpIdx1 && d.grpIdx2 == grpIdx2; 
	}

	public String toString() {
		return "[HitData:    project1="+projIdx1+ " seq1=" + seqObj1.getChrName() + " grp1="+grpIdx1+ 
				        ";   project2="+projIdx2+ " seq2=" + seqObj2.getChrName()+ " grp2="+grpIdx2+"]";
	}

	public String getOtherChrName(Sequence seqObj) { // CAS531 added for Closeup title
		if (seqObj==seqObj1) return seqObj2.getChrName();
		else return seqObj1.getChrName();
	}
	
	/**********************************************************************/
	/**********************************************************************
	 * XXX
	 * XXX DrawHit class for drawing, 1-1 with HitData, which has the data and this has the drawing stuff
	 * CAS531 was accessing PseudoPseudoData.pseudoHits for the abstract HitData, which is no longer abstract
	 * CAS545 PseudoHit->DrawHit
	 */
	private class DrawHit {
		private HitData hitDataObj;
		private Line2D.Double hitWire;
		
		// There are also flags in hitDataObj; isBlock, isCollinear, isPopup, isDualGene
		private boolean isHover;		 // set on hover; 
		private boolean isQuerySelHit;   // set on Query select row; From TableDataPanel
		private boolean isDisplayed;     // isNotFiltered; CAS541 add.
		
		public DrawHit(HitData hd) {
			hitDataObj = hd;
			hitWire = new Line2D.Double();		// setLine in paintComponent
			isHover = isQuerySelHit = isDisplayed = false;
		}
		
		private int getCollinearSet() { // CAS520 to toggle collinear highlight
			return hitDataObj.getCollinearSet();
		}
		private int getBlock() { // CAS520 to toggle block highlight
			return hitDataObj.getBlock();
		}
		
		private void getHit(Vector <HitData> hitList, int start, int end, boolean swap) { 
			if ( isContained(start, end, swap) )
				hitList.add(hitDataObj);
		}
		
		private void setMinMax(HfilterData hf) {// CAS520 change for seqHits
			hf.condSetPctid(hitDataObj.getPctid());
		}
		private boolean isFiltered() { // CAS520 rewrite for new filters
			 HfilterData hf = mapper.getHitFilter();
			 
			 double pctid = hf.getPctid();
			 boolean bIsPCT = (hitDataObj.getPctid()>=pctid);
			 
			 isDisplayed=true;
			 if (hf.isAllHit() && bIsPCT) return false;
			 if (hf.isBlock()  && hitDataObj.isBlock()  && bIsPCT) return false;
			 if (hf.isCset()   && hitDataObj.isCset()   && bIsPCT) return false;
			 if (hf.is2Gene()  && hitDataObj.is2Gene()  && bIsPCT) return false;
			 if (hf.is1Gene()  && hitDataObj.is1Gene()  && bIsPCT) return false;
			 if (hf.is0Gene()  && hitDataObj.is0Gene()  && bIsPCT) return false;
			 
			 isDisplayed=false;
			 return true; 
		 }
		 private Color getCColor(String orient, boolean toggle, boolean bWire) {
			 if (isHover)     return Mapper.pseudoLineHoverColor;	// CS520 change Highlight to Hover
			 if (isQuerySelHit) return Mapper.pseudoLineHoverColor;	// CAS516 different color for query selected
			 
			 HfilterData hf = mapper.getHitFilter(); 
			 if (hf.isHiPopup() && hitDataObj.isPopup()) return Mapper.pseudoLineHoverColor;
			 
			 if (hitDataObj.isConserved()) { // conserved takes precedence over highlight
				 if (bWire) cntConserved++;
				 return Mapper.pseudoLineHighlightColor2;
			 }
			
			 boolean isHi=false;
			 if      (hf.isHiBlock() && hitDataObj.isBlock()) isHi=true;
			 else if (hf.isHiCset()  && hitDataObj.isCset())  isHi=true;
			 else if (hf.isHi2Gene() && hitDataObj.is2Gene()) isHi=true;
			 else if (hf.isHi1Gene() && hitDataObj.is1Gene()) isHi=true;
			 else if (hf.isHi0Gene() && hitDataObj.is0Gene()) isHi=true;
			 if (isHi) {
				 if (bWire) cntHighHit++;
				 if (toggle) return Mapper.pseudoLineHighlightColor1;		// CAS520 for new filters for block and co hits
				 else 		 return Mapper.pseudoLineHighlightColor2;
			 }
			 
			 if (orient.contentEquals("++")) return Mapper.pseudoLineColorPP; 
			 if (orient.contentEquals("+-")) return Mapper.pseudoLineColorPN;
			 if (orient.contentEquals("-+")) return Mapper.pseudoLineColorNP;
			 if (orient.contentEquals("--")) return Mapper.pseudoLineColorNN;
 			 return Mapper.pseudoLineColorPP;
		 }
		 ////////////////////////////////////////////////////////////////////
		 private boolean isVisHitWire() {
			 boolean seq1 = seqObj1.isHitInRange(hitDataObj.mid1);
			 boolean seq2 = seqObj2.isHitInRange(hitDataObj.mid2);
			 return seq1 && seq2;
		 }
		 private boolean isOlapHit() {	// CAS550 add so partial hit can be viewed
			 boolean seq1 = seqObj1.isHitOlap(hitDataObj.start1, hitDataObj.end1);
			 boolean seq2 = seqObj2.isHitOlap(hitDataObj.start2, hitDataObj.end2);
			 return seq1 && seq2;
		 }
		 
		// is Hit Wire contained in hover, or hits for align; CAS531 reordered for clarity
		private boolean isContained(int start, int end, boolean isQuery) { 
			if (!isOlapHit()) return false; // CAS550 was isVisHitWire
			if (isFiltered()) return false;
			
			int s = (isQuery) ? hitDataObj.start1 : hitDataObj.start2;
			int e = (isQuery) ? hitDataObj.end1 : hitDataObj.end2;

			return (s >= start && s <= end) || (e >= start && e <= end) || (start >= s && end <= e);
		}
				
		private boolean lineContains(Point p) { 
			 double lx1 = hitWire.getX1();
			 double lx2 = hitWire.getX2();
			 double ly1 = hitWire.getY1();
			 double ly2 = hitWire.getY2();
			 
			 double delta = p.getY() - ly1 - ((ly2-ly1)/(lx2-lx1)) * (p.getX()-lx1);
			 
			 return (delta >= -MOUSE_PADDING && delta <= MOUSE_PADDING);
		 }
		 protected boolean isContained(Point point) {
			 return isOlapHit() && lineContains(point);// CAS550 was isVisHitWire
		 }
		 private void set(boolean isSelectedHit) { // CAS516 change from setting highlight to bSelected
			isQuerySelHit = isSelectedHit;
		 }
		
		 // provide hover; called from main mouseMoved
		 private void mouseMoved(MouseEvent e) {
			 if (!isDisplayed) return;
	
			 boolean b = isOlapHit() && lineContains(e.getPoint());
	 
			 if (setHover(b)) mapper.getDrawingPanel().repaint();
		 }
		
		 protected boolean setHover(boolean bHover) { // mouseMoved/isHover
			if (bHover != this.isHover) {
				this.isHover = bHover;
				return true;
			}
			return false;
		 } 
		 
		 /***************************************************************************
		  * Draw hit line connector, and hit info on seq1/seq2 rectangle (%ID, hitLen)
		  * CAS550 change to draw if any of the hit is visible; if hit wire not visible, draw length that
		  *        is and put hit-wire at very end
		  * trackPos1 and trackPos2: 1 or 2 indicating left or right
		  * stLoc1 and stLoc2: x,y from origin 
		  * btoggle CAS520 add toggle highlight color for collinear set and blocks 
		  */
		 private boolean paintComponent(Graphics2D g2, int trackPos1, int trackPos2, Point stLoc1, Point stLoc2, boolean btoggle) {  		 
			 if (!isOlapHit() || isFiltered()) return false; // CAS550 chg hit-wire visible to hit length visible
			 
			 cntShowHit++;
			
		   // get border locations of sequence tracks to draw hit stuff to it and along it
			 // sqObj.getPointForHit truncates point to not overlap track ends
			 Point2D hw1 = seqObj1.getPointForHit(hitDataObj.mid1 , trackPos1 );
			 hw1.setLocation(hw1.getX() + stLoc1.getX(), hw1.getY() + stLoc1.getY());
			
			 Point2D hw2 = seqObj2.getPointForHit(hitDataObj.mid2 , trackPos2 );
			 hw2.setLocation(hw2.getX() + stLoc2.getX(), hw2.getY() + stLoc2.getY());
			
			 int x1 = (int)hw1.getX(), y1 = (int)hw1.getY();
			 int x2 = (int)hw2.getX(), y2 = (int)hw2.getY();

		   // hitLine: hit connecting seq1/seq2 chromosomes 
			 Color c = getCColor(hitDataObj.getOrients(), btoggle, true);
			 g2.setPaint(c); 
			 hitWire.setLine(hw1,hw2); 
			 g2.draw(hitWire); 
			 
			 /* %id line: paint in seq1/2 rectangle the %id length */
			 int pctid = (int)hitDataObj.getPctid();
			 int lineScoreLen = Math.max(1, 30*(pctid+1)/(100+1)); // CAS517 was only set if getShowScoreLine
			 if (trackPos1 == Globals.RIGHT_ORIENT) lineScoreLen = 0 - lineScoreLen; 
		
			 if (seqObj1.getShowScoreLine()) { // gets turned back on with redraws
				 g2.drawLine(x1,y1,x1-lineScoreLen,y1);
			 }
			 if (seqObj2.getShowScoreLine()) {
				 g2.drawLine(x2,y2,x2+lineScoreLen,y2);
			 }
			// Hit length: paint in sequence objects the rectangle for the hit length graphics 
			 if (seqObj1.getShowHitLen()) { 					
				 Point2D rp1 = seqObj1.getPointForHit(hitDataObj.getStart1(), trackPos1);
				 Point2D rp2 = seqObj1.getPointForHit(hitDataObj.getEnd1(),   trackPos1);
				 
				 if (Math.abs(rp2.getY()-rp1.getY()) > 3) { 		// only draw if it will be visible
					 rp1.setLocation(x1-lineScoreLen,rp1.getY());				 
					 rp2.setLocation(x1-lineScoreLen,rp2.getY());
					 
					 paintHitLen(g2, seqObj1, rp1, rp2, trackPos1, hitDataObj.isPosOrient1(), btoggle);
				 }
			 }
			 if (seqObj2.getShowHitLen()) {
				 Point2D rp3 = seqObj2.getPointForHit(hitDataObj.getStart2(), trackPos2);
				 Point2D rp4 = seqObj2.getPointForHit(hitDataObj.getEnd2(),   trackPos2);
				 if (Math.abs(rp4.getY()-rp3.getY()) > 3) { 		// only draw if it will be visible
					 rp3.setLocation(x2+lineScoreLen,rp3.getY());				 
					 rp4.setLocation(x2+lineScoreLen,rp4.getY());
					 
					 paintHitLen(g2, seqObj2, rp3, rp4, trackPos2, hitDataObj.isPosOrient2(), btoggle);
				 }
			 }
			 
		/* text */
			 // CAS531 add hitNum; CAS543 always put text on outside rect; CAS545 compress code and add block/cset
			 int xr=4, xl=19; 
			 String num1=null;
			 if       (seqObj1.getShowScoreText())  num1 = ""+(int)pctid;
			 else  if (seqObj1.getShowHitNumText()) num1 = ""+hitDataObj.getHitNum();  
			 else  if (seqObj1.getShowBlockText())  num1 = ""+hitDataObj.getBlock();
			 else  if (seqObj1.getShowCsetText())   num1 = ""+hitDataObj.getCollinearSet();
			 
			 if (num1!=null && !num1.equals("0")) {  
				 double textX = x1;
				 if (x1 < x2) textX += xr;
				 else	{
					 int nl = num1.length()-1;
					 textX -= (xl + (nl*4));
				 }			 
				 g2.setPaint(Color.black);
				 g2.setFont(textFont);
				 g2.drawString(num1, (int)textX, (int)y1);
			 }
			 
			 String num2=null;
			 if       (seqObj2.getShowScoreText())  num2 = ""+(int)pctid;
			 else  if (seqObj2.getShowHitNumText()) num2 = ""+hitDataObj.getHitNum();
			 else  if (seqObj2.getShowBlockText())  num2 = ""+hitDataObj.getBlock();
			 else  if (seqObj2.getShowCsetText())   num2 = ""+hitDataObj.getCollinearSet();
			
			 if (num2!=null && !num2.equals("0")) {  
				 double textX = x2;
				 if (x1 < x2) {
					 int nl = num2.length()-1;
					 textX -= (xl + (nl*4)) ;
				 }
				 else textX += xr;
				
				 g2.setPaint(Color.black);
				 g2.setFont(textFont);
				 g2.drawString(num2, (int)textX, (int)y2);					 
			 }
			 
			 // Hover over hit line connector; CAS517 move creation to HitData 
			 if (isHover) { 						
				 mapper.setHelpText(hitDataObj.createHover(st1LTst2));
			 }
			 else mapper.setHelpText(null);
			 
			 return true;
		 }
		 /***************************************************************************
		  * Draw hit - draws the length of the hit along the blue chromosome box, which shows breaks between merged hitsd
		  */
		 private void paintHitLen(Graphics2D g2, Sequence st, Point2D pStart, Point2D pEnd, 
				 int trackPos, boolean forward, boolean toggle) {
			 double psx = pStart.getX();
			 double psy = pStart.getY();
			 double w = Mapper.hitRibbonWidth;
			 
			 String subHits;
			 Rectangle2D.Double rect;
			 
			 if (mapper.isQueryTrack(st)) // CAS517 check isSelf in isQuery || (trackPos == LEFT_ORIENT && mapper.isSelf() ))
				 subHits = hitDataObj.getQuerySeq();
			 else
				 subHits = hitDataObj.getTargetSeq();

			 if (subHits == null || subHits.length() == 0) {// CAS512 long time bug when flipped and one hit
				 g2.setPaint(getCColor(hitDataObj.getOrients(), toggle, false));
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
				 
				 g2.setPaint(getCColor(hitDataObj.getOrients(), toggle, false));
				 
				 // Draw sub-hits
				 String[] subseq = subHits.split(",");
				 for (int i = 0;  i < subseq.length;  i++) {
					 String[] pos = subseq[i].split(":");
					 long start = Long.parseLong(pos[0]);
					 long end =   Long.parseLong(pos[1]);
					 
					 Point2D p1 = st.getPointForHit(start, trackPos);
					 p1.setLocation( pStart.getX(), p1.getY() );
					 
					 Point2D p2 = st.getPointForHit(end, trackPos);
					 p2.setLocation( pStart.getX(), p2.getY() );
					 
					 rect.setRect( p1.getX(), p1.getY(), w, p2.getY()-p1.getY() );
					 fixRect(rect); // for flipped track
					 g2.fill(rect);
					 g2.draw(rect);
				 }
			 }
		 }
		
		
		 /* CAS548 removed getIfHit called from getHitStr, which has been removed (was showing all subhits) **/
		 
		/* CAS516 popup from clicking hit wire; CAS531 change to use TextPopup */
		private void popupDesc(double x, double y) {
			hitDataObj.setIsPopup(true);
			String title="Hit #" + hitDataObj.getHitNum(); // CAS520 changed from hit idx to hitnum
			
			String theInfo = hitDataObj.createHover(st1LTst2) + "\n"; 
			
			String name1 = seqObj1.getTitle(); 
			String name2 = seqObj2.getTitle(); 
			name1 += seqObj1.getGeneNumFromIdx(hitDataObj.getAnnot1(), hitDataObj.getAnnot2()); // CAS545 add gene#
			name2 += seqObj2.getGeneNumFromIdx(hitDataObj.getAnnot1(), hitDataObj.getAnnot2()); 
			String msg1 = SeqData.formatHit(name1, hitDataObj.getQuerySubhits(), hitDataObj.isPosOrient1());
			String msg2 = SeqData.formatHit(name2, hitDataObj.getTargetSubhits(), hitDataObj.isPosOrient2());
				
			theInfo +=  st1LTst2 ? ("\nL " + msg1+ "\nR " + msg2) : ("\nL " + msg2+"\nR " + msg1);
			
			if (Globals.TRACE) {
				theInfo += "\nDB-index " + hitDataObj.getID();  // CAS520 useful for debugging
				theInfo += "\nAnnot " + hitDataObj.getAnnots(); // CAS543 add
			}
			
			new TextShowInfo(mapper, title, theInfo, mapper.getDrawingPanel(), hitDataObj, 
				seqObj1.getTitle(), seqObj2.getTitle(), 									// title is Proj Chr
				seqObj1.getProjectDisplayName(), seqObj2.getProjectDisplayName(), seqObj1.isQuery()); 
		}
		public String toString() {return hitDataObj.createHover(true);}
	 } // End class DrawHit
}
