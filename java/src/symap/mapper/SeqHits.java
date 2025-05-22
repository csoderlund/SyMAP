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
import symap.closeup.AlignPool;
import symap.sequence.Annotation;
import symap.sequence.Sequence;
import symap.Globals;

/***********************************************
 * Two ends of a seq-seq hit; internal class DrawHit has both ends
 * Mapper: Tracks 0-N are in order of display; Ref is always seqObj1
 * Hit Wire is the line between chromosomes
 * Hit Rect is the hit rectangle in the track, one on each end of wire
 * DrawHit: draws hit part the occurs on sequence: hit length and text (e.g. block#)
 */
public class SeqHits  {
	private static final int MOUSE_PADDING = 3;
	private static final int HIT_OFFSET = 15, HIT_MIN_OFFSET=6; // amount line enters track; 15, 12,9,6
	private static final int HIT_INC = 3, HIT_OVERLAP = 75; // default minimum match bases is 100, so hits should be more
	
	public static Font textFont = Globals.textFont;
	
	private int projIdx1, projIdx2; // project IDs
	private int grpIdx1, grpIdx2; // groupIDs: chromosome 
	
	private Mapper mapper;
	private DrawHit[] allHitsArray;
	
	// query has dbName < target; seqObj1 is query (corresponds to pseudo_hits.annot1_idx); 
	private Sequence seqObj1, seqObj2; // query, target; query may be the left or right track
	private boolean st1LTst2=true; 	   // if seqObj1.track# <seqObj2.track#, query is left track, else, right
	
	private String infoMsg=""; 
	private int cntShowHit=0, cntHighHit=0, cntHitg2=0;
	
	// called in MapperPool
	public SeqHits(Mapper mapper, Sequence st1, Sequence st2, Vector <HitData> hitList) {
		this.mapper = mapper;
		this.seqObj1 = st1;
		this.seqObj2 = st2;
		boolean bHit1 = st1.position==1 || st2.position==1;
		
		int off1 = HIT_OFFSET, off2 = HIT_OFFSET;
		HitData lasthd = null;
		TreeMap <Integer, Integer> s2Map = new TreeMap <Integer, Integer>  (); // order by start2
		
		allHitsArray = new DrawHit[hitList.size()]; // transfer hitList to allHitsArray; ordered by start1
		for (int i = 0; i < allHitsArray.length; i++) {
			HitData hd = hitList.get(i);
			allHitsArray[i] = new DrawHit(hd);
			
			boolean isSelected = mapper.isQuerySelHit(hd.getHitNum(), bHit1); // for group or hit highlight;
			allHitsArray[i].set(isSelected);
			
			// Calc offset for display for seq1
			int olap = (lasthd!=null) ?  Math.min(lasthd.end1,hd.end1) - Math.max(lasthd.start1,hd.start1) : 0;
			if (olap > HIT_OVERLAP) { 
				off1 -= HIT_INC;
				if (off1 < HIT_MIN_OFFSET) off1 = HIT_OFFSET;
			}
			else  off1 = HIT_OFFSET;
			allHitsArray[i].off1 = off1;
			
			if (lasthd==null) lasthd = hd;
			else if (hd.end1>lasthd.end1) lasthd = hd; // otherwise, contained
			
			if (s2Map.containsKey(hd.start2)) s2Map.put(hd.start2+1, i); 
			else s2Map.put(hd.start2, i);
		}
		// Calc offset for display for seq2
		lasthd = null;
		for (int i : s2Map.values()) {
			HitData hd = hitList.get(i);
			int olap = (lasthd!=null) ?  Math.min(lasthd.end2,hd.end2) - Math.max(lasthd.start2,hd.start2) : 0;
			if (olap > HIT_OVERLAP) {
				off2 -= HIT_INC;
				if (off2 < HIT_MIN_OFFSET) off2 = HIT_OFFSET;
			}
			else off2 = HIT_OFFSET;
			allHitsArray[i].off2 = off2;
			
			if (lasthd==null) lasthd = hd;
			else if (hd.end2>lasthd.end2) lasthd = hd;
		}
		
		projIdx1 = st1.getProject();
		projIdx2 = st2.getProject();
		grpIdx1  = st1.getGroup(); 
		grpIdx2  = st2.getGroup(); 
		
		seqObj1.setSeqHits(this, mapper.isQueryTrack(seqObj1.getHolder().getTrack())); // seqObj has hits 
		seqObj2.setSeqHits(this, mapper.isQueryTrack(seqObj2.getHolder().getTrack()));
		
		// 1st track is listed first on hover and popups
		int t1 = seqObj1.getHolder().getTrackNum(); 
		int t2 = seqObj2.getHolder().getTrackNum(); 
		st1LTst2 = (t1<t2);
	}
	
	protected String getInfo() { return infoMsg;}; // called in Mapper
	
	// called from mapper.myinit sets for the Hit Filter %id slider
	protected void setMinMax(HfilterData hf) { 
		for (DrawHit h : allHitsArray)
			h.setMinMax(hf);
	}
	
	protected void getMinMax(int[] minMax, int start, int end, boolean swap) {
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
	 public void clearHighHitg2() { // called from Sequence (SeqPool sets conserved per hit)
		 for (DrawHit hd: allHitsArray) 
			 if (hd.hitDataObj.is2Gene()) 
				 hd.hitDataObj.setIsHighHitg2(false);
	 }
	 public Sequence getSeqObj1() {return seqObj1;}
	 public Sequence getSeqObj2() {return seqObj2;}
	 
	 /*****************************************************
	  * XXX Paint all hits 	
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
		cntShowHit=cntHighHit=cntHitg2=0; // counted in paint
		HashSet <Integer> blockSet = new HashSet <Integer> (); // count blocks/sets shown
		toggle=true; 
		
		for (DrawHit dh : allHitsArray) {
			if (!dh.isOlapHit() || dh.isFiltered()) continue; 
			
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
			if (!dh.isOlapHit() || dh.isFiltered()) continue; 
			
			boolean show = dh.paintComponent(g2,trackPos1,trackPos2,stLoc1,stLoc2, true);
			if (bHi && show) {
				int set = (bHiCset) ?  dh.getCollinearSet() : dh.getBlock();
				if (set!=0 && !blockSet.contains(set)) blockSet.add(set);
			}
		}
		
		// Information box on Stats
		infoMsg = String.format("%s: %,d", "Hits", cntShowHit);
		if (cntHitg2>0) infoMsg += String.format("\n%s: %,d", "Conserved", cntHitg2);
		if (cntHighHit>0)   infoMsg += String.format("\n%s: %,d", "High", cntHighHit);
		if (bHi) {
			String type = (bHiCset) ? "CoSets" : "Blocks";	
			infoMsg += String.format("\n%s: %,d\n", type, blockSet.size());
		}
	}
	/*************************************************************
	 * Hover and popups
	 */
	// Hit list: each is HitTag\ncoord1\ncoord2
	// For sequence object - hits aligned to a gene; Annotation.popDesc expects this format
	public String getHitsForGenePopup(Sequence seqObj, Annotation annoObj, TreeMap <Integer, String> hitScores) {
		String listVis= "";
		boolean isQuery = mapper.isQueryTrack(seqObj);
		
		for (DrawHit drObj : allHitsArray) {
			HitData hdObj = drObj.hitDataObj;
			if (!hitScores.containsKey(hdObj.getID())) continue;
			
			String star = hdObj.getMinorForGenePopup(isQuery, annoObj.getAnnoIdx());
			String score = "(" + hitScores.get(hdObj.getID()) + ")";	
			
			String minorTag = (star.equals("*")) ? annoObj.getFullGeneNum() : null;
			String coords = hdObj.getHitCoordsForGenePopup(isQuery, minorTag) + "\n";
			
			String hit = String.format("%s%s %s\n%s", star.trim(), hdObj.hitGeneTag, score, coords); 
			
			listVis += hit + ";";
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
			h.isHover = false; 
		mapper.getDrawingPanel().repaint();
	}
	// popup on right click
	protected boolean doPopupDesc(MouseEvent e) {
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
	protected void clearData() {
		 allHitsArray=null;
	}
	 /***************** Static stuff ***********************/
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

	public String getOtherChrName(Sequence seqObj) { // for Closeup title
		if (seqObj==seqObj1) return seqObj2.getChrName();
		else return seqObj1.getChrName();
	}
	
	/**********************************************************************/
	/**********************************************************************
	 * XXX
	 * XXX DrawHit class for drawing, 1-1 with HitData, which has the data and this has the drawing stuff
	 */
	private class DrawHit {
		private HitData hitDataObj;
		private Line2D.Double hitWire;
		
		// There are also flags in hitDataObj; isBlock, isCollinear, isPopup, isDualGene
		private boolean isHover;		 // set on hover; 
		private boolean isQuerySelHit;   // set on Query select row; From TableDataPanel
		private boolean isDisplayed;     // isNotFiltered;
		public int off1 = HIT_OFFSET, off2 = HIT_OFFSET;
		
		public DrawHit(HitData hd) {
			hitDataObj = hd;
			hitWire = new Line2D.Double();		// setLine in paintComponent
			isHover = isQuerySelHit = isDisplayed = false;
		}
		
		private int getCollinearSet() { // toggle collinear highlight
			return hitDataObj.getCollinearSet();
		}
		private int getBlock() { // toggle block highlight
			return hitDataObj.getBlock();
		}
		
		private void getHit(Vector <HitData> hitList, int start, int end, boolean swap) { 
			if ( isContained(start, end, swap) )
				hitList.add(hitDataObj);
		}
		
		private void setMinMax(HfilterData hf) {// for seqHits
			hf.condSetPctid(hitDataObj.getPctid());
		}
		private boolean isFiltered() { 
			 HfilterData hf = mapper.getHitFilter();
			 
			 isDisplayed=true;
			
			 // precedence
			 if (hf.isAll()) return false; // Over-rides everything; CAS567 add
			 
			 boolean bIsNotPCT = (hitDataObj.getPctid()<hf.getPctid());
			 if (bIsNotPCT) {
				 isDisplayed=false;
				 return true;
			 }
			 if (hf.isBlockOnly() && hf.getBlock()>0) {		// CAS567 - 
				 if (hitDataObj.getBlock() == hf.getBlock()) return false;
				 isDisplayed=false;
				 return true;
			 }
			
			 boolean noNoOtherSet = !hf.isCset() && !hf.is2Gene() && !hf.is1Gene() && !hf.is0Gene();
			 if (!hf.isBlock() && noNoOtherSet) return false; // all hits
			 
			 if (hf.isBlock()) { // block set
				 boolean bHitIsBlk = hitDataObj.isBlock();// block hit
				 if (bHitIsBlk && noNoOtherSet) return false; // no other checks, so not filtered
				
				 boolean and = (hf.isBlockAnd());
				 if (!and && bHitIsBlk) return false; // or, is block, show regardless what else
				 
				 if (and && !bHitIsBlk) {	
					 isDisplayed=false;
					 return true; 				// and, !block, filter
				 }
			 }
			 if (hitDataObj.isCset()  && hf.isCset())  return false; 
			 if (hitDataObj.is2Gene() && hf.is2Gene()) return false;
			 if (hitDataObj.is1Gene() && hf.is1Gene()) return false;
			 if (hitDataObj.is0Gene() && hf.is0Gene()) return false;
			 
			 // not display
			 isDisplayed=false;
			 return true; 
		 }
		 private Color getCColor(String orient, boolean toggle, boolean bWire) {
			 if (isHover)     return Mapper.pseudoLineHoverColor;	
			
			 HfilterData hf = mapper.getHitFilter(); 
			 if (hf.isHiPopup() && hitDataObj.isPopup()) return Mapper.pseudoLineGroupColor;
			 if (hf.isHiPopup() && isQuerySelHit) return Mapper.pseudoLineGroupColor;	
			 
			 if (hitDataObj.isHighHitg2()) { // conserved takes precedence over highlight
				 if (bWire) cntHitg2++;
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
				 if (toggle) return Mapper.pseudoLineHighlightColor1;		
				 else 		 return Mapper.pseudoLineHighlightColor2;
			 }
			 
			 if (orient.contentEquals("++")) return Mapper.pseudoLineColorPP; 
			 if (orient.contentEquals("+-")) return Mapper.pseudoLineColorPN;
			 if (orient.contentEquals("-+")) return Mapper.pseudoLineColorNP;
			 if (orient.contentEquals("--")) return Mapper.pseudoLineColorNN;
 			 return Mapper.pseudoLineColorPP;
		 }
		 ////////////////////////////////////////////////////////////////////
		 private boolean isOlapHit() {	// partial hit can be viewed
			 boolean seq1 = seqObj1.isHitOlap(hitDataObj.start1, hitDataObj.end1);
			 boolean seq2 = seqObj2.isHitOlap(hitDataObj.start2, hitDataObj.end2);
			 return seq1 && seq2;
		 }
		 
		// is Hit Wire contained in hover, or hits for align; 
		private boolean isContained(int start, int end, boolean isQuery) { 
			if (!isOlapHit()) return false;
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
			 return isOlapHit() && lineContains(point);
		 }
		 private void set(boolean isSelectedHit) { 
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
		  * draw if any of the hit is visible; if hit wire not visible, draw length and put hit-wire at very end
		  * trackPos1 and trackPos2: 1 or 2 indicating left or right
		  * stLoc1 and stLoc2: x,y from origin 
		  */
		 private boolean paintComponent(Graphics2D g2, int trackPos1, int trackPos2, Point stLoc1, Point stLoc2, 
				 boolean btoggle) {  	
			 
			 if (!isOlapHit() || isFiltered()) return false; 
			
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
			 
			 /* %id line: paint in seq1/2 rectangle the %id length;  */
			 // id=19 is 5 outside rect, id=23 is 7 inside rect; id=100 is 30 far in rect
			 int pctid = (int)hitDataObj.getPctid();
			 int lineScoreLen = Math.max(1, 30*(pctid+1)/(100+1)); 
			 int len1=off1, len2 = off2;
			 if (trackPos1 == Globals.RIGHT_ORIENT) {
				 lineScoreLen = 0 - lineScoreLen; 
				 len1 = 0 - len1;
				 len2 = 0 - len2;
			 }
			
			// Hit length: paint in sequence objects the rectangle for the hit length graphics ; 	
			// if no hitLen, then no scoreLine either. If no scoreLine, use LINE_W
			 if (seqObj1.getShowHitLen()) {
				 int wlen1 = seqObj1.getShowScoreLine() ? lineScoreLen : len1;
				 g2.drawLine(x1, y1, x1-wlen1, y1);
				 
				 Point2D rp1 = seqObj1.getPointForHit(hitDataObj.getStart1(), trackPos1);
				 Point2D rp2 = seqObj1.getPointForHit(hitDataObj.getEnd1(),   trackPos1);
				 
				 if (Math.abs(rp2.getY()-rp1.getY()) > 3) { 		// only draw if it will be visible
					 rp1.setLocation(x1-wlen1, rp1.getY());				 
					 rp2.setLocation(x1-wlen1, rp2.getY());
					 
					 paintHitLen(g2, seqObj1, rp1, rp2, trackPos1, hitDataObj.isPosOrient1(), btoggle);
				 }
			 }
			 if (seqObj2.getShowHitLen()) {
				 int wlen2 = seqObj2.getShowScoreLine() ? lineScoreLen : len2;
				 g2.drawLine(x2, y2, x2+wlen2, y2);
				 
				 Point2D rp3 = seqObj2.getPointForHit(hitDataObj.getStart2(), trackPos2);
				 Point2D rp4 = seqObj2.getPointForHit(hitDataObj.getEnd2(),   trackPos2);
				
				 if (Math.abs(rp4.getY()-rp3.getY()) > 3) { 		// only draw if it will be visible
					 rp3.setLocation(x2+wlen2, rp3.getY());				 
					 rp4.setLocation(x2+wlen2, rp4.getY());
					 
					 paintHitLen(g2, seqObj2, rp3, rp4, trackPos2, hitDataObj.isPosOrient2(), btoggle);
				 }
			 }
		/* text */
			 int xr=4, xl=19; 
			 
			 String numText1=null;  
			 if       (seqObj1.getShowScoreText())  numText1 = (int)pctid+"%";
			 else  if (seqObj1.getShowHitNumText()) numText1 = "#" + hitDataObj.getHitNum();  
			 else  if (seqObj1.getShowBlockText())  {
				 int n = hitDataObj.getBlock();
				 if (n>0) numText1 = "b"+n;
			 }
			 else  if (seqObj1.getShowCsetText())   {
				 int n = hitDataObj.getCollinearSet();
				 if (n>0) numText1 = "c"+n;
			 }
			 
			 if (numText1!=null) {  
				 double textX = x1;
				 if (x1 < x2) textX += xr;
				 else	{
					 int nl = numText1.length()-1;
					 textX -= (xl + (nl*4));
				 }			 
				 g2.setPaint(Color.black);
				 g2.setFont(textFont);
				 g2.drawString(numText1, (int)textX, (int)y1);
			 }
			 
			 String numText2=null; 
			 if       (seqObj2.getShowScoreText())  numText2 = (int)pctid +"%";
			 else  if (seqObj2.getShowHitNumText()) numText2 = "#"+hitDataObj.getHitNum();
			 else  if (seqObj2.getShowBlockText())  {
				 int n = hitDataObj.getBlock();
				 if (n>0) numText2 = "b"+n;
			 }
			 else  if (seqObj2.getShowCsetText())  {
				 int n = hitDataObj.getCollinearSet();
				 if (n>0) numText2 = "c"+n;
			 }
			 
			 if (numText2!=null) {  
				 double textX = x2;
				 if (x1 < x2) {
					 int nl = numText2.length()-1;
					 textX -= (xl + (nl*4)) ;
				 }
				 else textX += xr;
				
				 g2.setPaint(Color.black);
				 g2.setFont(textFont);
				 g2.drawString(numText2, (int)textX, (int)y2);					 
			 }
			 
			 // Hover over hit line connector; creation in HitData 
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
			 
			 if (mapper.isQueryTrack(st)) 
				 subHits = hitDataObj.getQueryMerge(); 
			 else
				 subHits = hitDataObj.getTargetMerge();

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
					 int start = Integer.parseInt(pos[0]); 
					 int end =   Integer.parseInt(pos[1]);
					 
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
		 
		 
		/* popup from clicking hit wire;  */
		private void popupDesc(double x, double y) { 
			if (mapper.getHitFilter().isHiPopup()) hitDataObj.setIsPopup(true); 
			
			String title="Hit #" + hitDataObj.getHitNum(); 
			
			String theInfo = hitDataObj.createHover(st1LTst2) + "\n"; 
			
			String proj1 = seqObj1.getTitle(); //Proj Chr
			String proj2 = seqObj2.getTitle(); 
			Annotation aObj1 = seqObj1.getAnnoObj(hitDataObj.getAnnot1(), hitDataObj.getAnnot2());
			Annotation aObj2 = seqObj2.getAnnoObj(hitDataObj.getAnnot1(), hitDataObj.getAnnot2());
			
			String trailer = "";
			if (Globals.INFO) {
				trailer = "\nDB-index " + hitDataObj.getID();   // useful for debugging
				trailer += "\nAnnot " + hitDataObj.getAnnots(); 
			}
	
			AlignPool ap = new AlignPool(mapper.getDrawingPanel().getDBC());
			
			new TextShowInfo(ap, hitDataObj, title, 
				theInfo, trailer, st1LTst2, proj1, proj2, aObj1, aObj2, 
				hitDataObj.getQuerySubhits(), hitDataObj.getTargetSubhits(),
				seqObj1.isQuery(), hitDataObj.isInv(), true /*bsort*/); 	
		}
		public String toString() {return hitDataObj.createHover(true);}
	 } // End class DrawHit
}
