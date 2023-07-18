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
import symap.sequence.Sequence;
import symap.Globals;

/***********************************************
 * Two ends of a seq-seq hit; internal class PseudoHit has both ends
 * Mapper: Tracks 0-N are in order of display; Ref is always seqObj1
 * Hit Wire is the line between chromosomes
 * Hit Rect is the hit rectangle in the track, one on each end of wire
 * 
 * CAS531 renamed from PseudoPseudoHits and removed extends AbstractHitData
 * 	 changed PseudoPseudoData.PseudoHitData getPhData() to HitData (which was abstract also)
 */

public class SeqHits  {
	private static final int MOUSE_PADDING = 3;
	private static Font textFont = new Font(Font.MONOSPACED,Font.PLAIN,12);
	
	private int project1, project2; // project IDs
	private int group1, group2; // groupIDs: contig or chromosome 
	
	private Mapper mapper;
	private Sequence seqObj1, seqObj2;
	private PseudoHit[] allHitsArray;
	private boolean st1LTst2=true; // seqObj1.track# < seqObj2.track#
	
	private String infoMsg=""; // CAS541
	private int cntShowHit=0, cntHighHit=0;

	// called in MapperPool
	public SeqHits(int p1, int g1, int p2, int g2, Mapper mapper, Sequence st1, Sequence st2, 
			Vector <HitData> hitList, boolean isSwap /* not used */) {
		this.project1 = p1;
		this.project2 = p2;
		this.group1 = g1; 
		this.group2 = g2; 
		
		this.mapper = mapper;
		this.seqObj1 = st1;
		this.seqObj2 = st2;

		seqObj1.setSeqHits(this, mapper.isQueryTrack(seqObj1.getHolder().getTrack())); // CAS517 add to get hits 
		seqObj2.setSeqHits(this, mapper.isQueryTrack(seqObj2.getHolder().getTrack()));
		
		allHitsArray = new PseudoHit[hitList.size()];
		for (int i = 0; i < allHitsArray.length; i++) {
			HitData hd = hitList.get(i);
			boolean isSelected = mapper.isHitSelected(hd.getStart1(), hd.getEnd1(), hd.getStart2(),hd.getEnd2());	
			allHitsArray[i] = new PseudoHit(hitList.get(i));
			allHitsArray[i].set(isSelected);
		}
		
		// CAS517 add so that the 1st track is listed first on hover and popups
		int t1 = seqObj1.getHolder().getTrackNum(); 
		int t2 = seqObj2.getHolder().getTrackNum(); 
		st1LTst2 = (t1<t2);
	}
	public String getInfo() { return infoMsg;}; // CAS541 called in Mapper
	
	public void clear() {
		infoMsg="";
		for (PseudoHit h : allHitsArray)
			h.clear();	
	}
	// called from mapper.myinit sets for the Hit Filter %id slider
	public void setMinMax(HfilterData hf) { 
		for (PseudoHit h : allHitsArray)
			h.setMinMax(hf);
	}
	
	public void getMinMax(int[] minMax, int start, int end, boolean swap) {
		for (PseudoHit h : allHitsArray) {
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
	 /*****************************************************
	  * XXX Paint all hits
	  */
	 public void paintComponent(Graphics2D g2) {
		Point stLoc1 = seqObj1.getLocation();
		Point stLoc2 = seqObj2.getLocation();
		int trackRelPos1 = mapper.getTrackPosition(seqObj1); // left or right
		int trackRelPos2 = mapper.getTrackPosition(seqObj2);
		List<PseudoHit> hHits = new LinkedList<PseudoHit>();
		
		boolean bHiSet   = mapper.getHitFilter().isHiSet();
		boolean bHiBlock = mapper.getHitFilter().isHiBlock();
		boolean bHi = (bHiSet || bHiBlock);
		TreeMap <Integer, Boolean> highMap = new TreeMap <Integer, Boolean> (); // if collinear or blocks is highlighted
		boolean toggle=true; 
		
		// CAS520 add highlight to block and collinear
		// allHitArray sorted by start1; this does not correspond to sequential blocks (sorted sets).
		if (bHi) {
			for (PseudoHit h : allHitsArray) {
				int set = (bHiSet) ?  h.getCollinearSet() : h.getBlock();
				if (set!=0 && !highMap.containsKey(set)) {
					highMap.put(set, toggle);
					toggle = !toggle;
				}
			}
			toggle=true;
			if (bHiSet) { //sorted by start1 does not correspond to sequence sets
				for (int set : highMap.keySet()) {
					highMap.put(set, toggle);
					toggle = !toggle;
				}
			}
		}
	
		cntShowHit=cntHighHit=0;
		HashSet <Integer> blockSet = new HashSet <Integer> (); // CAS541 add to count blocks/sets shown
		
		toggle=true; 
		for (PseudoHit h : allHitsArray) {
			if (bHi) {
				int set = (bHiSet) ?  h.getCollinearSet() : h.getBlock();
				if (set!=0) toggle = highMap.get(set);
			}
			if (h.isHover()) hHits.add(h);
			else {
				boolean show = h.paintComponent(g2, trackRelPos1, trackRelPos2, stLoc1, stLoc2, toggle);
				if (bHi && show) {
					int set = (bHiSet) ?  h.getCollinearSet() : h.getBlock();
					if (set!=0 && !blockSet.contains(set)) blockSet.add(set);
				}
			}
		}
		
		// Draw highlighted hits on top with highlight
		for (PseudoHit h : hHits) {
			boolean show = h.paintComponent(g2,trackRelPos1,trackRelPos2,stLoc1,stLoc2, true);
			if (bHi && show) {
				int set = (bHiSet) ?  h.getCollinearSet() : h.getBlock();
				if (set!=0 && !blockSet.contains(set)) blockSet.add(set);
			}
		}
		
		// XXX for the Information box on Stats
		infoMsg = String.format("%s: %,d", "Hits", cntShowHit);
		if (mapper.getFilterText().startsWith("High")) {
			infoMsg += String.format("\n%s: %,d", "High", cntHighHit);
			if (bHi) {
				String type = (bHiSet) ? "Sets" : "Blocks";
				infoMsg += String.format("\n%s: %,d\n", type, blockSet.size());
			}
		}
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
				if (h.bHover && h.isContained(p)) {
					h.popupDesc(p.getX(), p.getY());
					return true;
				}
			}
		}	
		return false;
	}
	
	/***************** Static stuff ***********************/
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

	public boolean equals(SeqHits d) {
		return d.project1 == project1 && d.project2 == project2
				&& d.group1 == group1 && d.group2 == group2; 
	}

	public String toString() {
		return "[HitData:    project1="+project1+ " seq1=" + seqObj1.getChrName() + " grp1="+group1+ 
				        ";   project2="+project2+ " seq2=" + seqObj2.getChrName()+ " grp2="+group2+"]";
	}

	public String getOtherChrName(Sequence seqObj) { // CAS531 added for Closeup title
		if (seqObj==seqObj1) return seqObj2.getChrName();
		else return seqObj1.getChrName();
	}
	
	/**********************************************************************/
	/**********************************************************************
	 * XXXXX PseudoHit class for drawing, 1-1 with HitData, which has the data and this has the drawing stuff
	 * CAS531 was accessing PseudoPseudoData.pseudoHits for the abstract HitData, which is no longer abstract
	 */
	private class PseudoHit {
		private HitData hitDataObj;
		private Line2D.Double hitWire;
		private boolean bHover;		// hover line is highlighted; CAS517 remove boolean highlight, not used
		private boolean bSelected; // From TableDataPanel
		private boolean bShow;     // CAS541 add for bug where Popup happens on hidden hitwire after All Hits on/off.
		
		public PseudoHit(HitData hd) {
			hitDataObj = hd;
			hitWire = new Line2D.Double();
			bHover = bSelected = bShow = false;
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
		
		// CAS520 change for seqHits
		private void setMinMax(HfilterData hf) {
			hf.condSetPctid(hitDataObj.getPctid());
		}
		private void set(boolean isSelectedHit) { // CAS516 change from setting highlight to bSelected
			bSelected = isSelectedHit;
		}
		
		private boolean isHover() {return bHover; } // for this hit wire object
		 
		private boolean isHighLightHit() { // CAS520 add method
			 HfilterData hf = mapper.getHitFilter(); 
			
			 if (hf.isHiPopup() && hitDataObj.isPopup()) return true;
			 if (hf.isHiBlock() && hitDataObj.isBlock()) return true;
			 if (hf.isHiSet() 	&& hitDataObj.isSet())   return true;
			 if (hf.isHi2Gene() && hitDataObj.is2Gene()) return true;
			 if (hf.isHi1Gene() && hitDataObj.is1Gene()) return true;
			 if (hf.isHi0Gene() && hitDataObj.is0Gene()) return true;
			
			 return false;	// hf.isHiNone default
		}
		// XXX
		private boolean isFiltered() { // CAS520 rewrite for new filters
			 HfilterData hf = mapper.getHitFilter();
			 
			 double pctid = hf.getPctid();
			 boolean bIsPCT = (hitDataObj.getPctid()>=pctid);
			 
			 bShow=true;
			 if (hf.isAllHit() && bIsPCT) return false;
			 if (hf.isBlock()  && hitDataObj.isBlock()  && bIsPCT) return false;
			 if (hf.isSet()    && hitDataObj.isSet()    && bIsPCT) return false;
			 if (hf.is2Gene()  && hitDataObj.is2Gene()  && bIsPCT) return false;
			 if (hf.is1Gene()  && hitDataObj.is1Gene()  && bIsPCT) return false;
			 if (hf.is0Gene()  && hitDataObj.is0Gene()  && bIsPCT) return false;
			 
			 bShow=false;
			 return true; 
		 }
		 
		 private boolean isVisible() {
			 return isSequenceVisible(seqObj1, hitDataObj.getStart1(), hitDataObj.getEnd1()) &&
			 		isSequenceVisible(seqObj2, hitDataObj.getStart2(), hitDataObj.getEnd2());
		 }
		
		// is Hit Wire contained in hover, or hits for align; CAS531 reordered for clarity
		private boolean isContained(int start, int end, boolean isQuery) { 
			if (!isVisible()) return false;
			if (isFiltered()) return false;
			
			int s = (isQuery) ? hitDataObj.getStart1() : hitDataObj.getStart2();
			int e = (isQuery) ? hitDataObj.getEnd1() : hitDataObj.getEnd2();

			return (s >= start && s <= end) || (e >= start && e <= end) || (start >= s && end <= e);
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
			 if (!bShow) return false; // CAS541 add for bug where any popup shows after All Hits
			 
			 boolean b = isVisible() && lineContains(hitWire, point);
			 return setHover(b); 
		 } 
		 protected boolean setHover(boolean bHover) {
			if (bHover != this.bHover) {
				this.bHover = bHover;
				return true;
			}
			return false;
		 } 
		 private Color getCColor(String orient, boolean toggle, boolean bWire) {
			 if (isHover())          return Mapper.pseudoLineHoverColor;	// CS520 change Highlight to Hover
			 else if (bSelected) 	 return Mapper.pseudoLineHoverColor;	// CAS516 different color for query selected
			 else if (isHighLightHit()) {
				 if (bWire) cntHighHit++;
				 if (toggle) return Mapper.pseudoLineHighlightColor1;		// CAS520 for new filters for block and co hits
				 else 		 return Mapper.pseudoLineHighlightColor2;
			 }
			 
			 else if (orient.contentEquals("++")) return Mapper.pseudoLineColorPP; 
			 else if (orient.contentEquals("+-")) return Mapper.pseudoLineColorPN;
			 else if (orient.contentEquals("-+")) return Mapper.pseudoLineColorNP;
			 else if (orient.contentEquals("--")) return Mapper.pseudoLineColorNN;
			 else return Mapper.pseudoLineColorPP;
		 }
		 
		 /***************************************************************************
		  * Draw hit line connector, and hit info on seq1/seq2 rectangle (%ID, hitLen)
		  */
		 private boolean paintComponent(Graphics2D g2, int trackPos1, int trackPos2, Point stLoc1, Point stLoc2, 
				 boolean btoggle) { // CAS520 add toggle for collinear set 
			 if (!isVisible() || isFiltered()) return false;
			 
			 cntShowHit++;
			
		   /* get border locations of sequence tracks to draw hit stuff to/in */
			 Point2D sp1 = getSequenceCPoint(seqObj1, hitDataObj.getStart1(), hitDataObj.getEnd1(), trackPos1, stLoc1);
			 Point2D sp2 = getSequenceCPoint(seqObj2, hitDataObj.getStart2(), hitDataObj.getEnd2(), trackPos2, stLoc2);
			 int x1=(int)sp1.getX(), y1=(int)sp1.getY();
			 int x2=(int)sp2.getX(), y2=(int)sp2.getY();

			/* hitLine: hit connecting seq1/seq2 chromosomes */
			 g2.setPaint(getCColor(hitDataObj.getOrients(), btoggle, true)); // set color;
			 hitWire.setLine(sp1,sp2); 
			 g2.draw(hitWire);	
			 		
			 int pctid = (int)hitDataObj.getPctid();
			 int lineLength = Math.max(1, 30*(pctid+1)/(100+1)); // CAS517 was only set if getShowScoreLine
			 if (trackPos1 == Globals.RIGHT_ORIENT) lineLength = 0 - lineLength; 
			 
			 /* %id line: paint in seq1/2 rectangle the %id length */
			 if (seqObj1.getShowScoreLine()) { // gets turned back on with redraws
				 g2.drawLine(x1,y1,x1-lineLength,y1);
			 }
			 if (seqObj2.getShowScoreLine()) {
				 g2.drawLine(x2,y2,x2+lineLength,y2);
			 }
			
		/* text */
			 /* %id text: paint in seq1/2 rectangle the %id; CAS543 always put text on outside rect */
			 int xr=4, xl=19; 
			 if (seqObj1.getShowScoreText() || (isHover() && !seqObj1.getShowHitNumText())) {  	
				 double textX = x1; 
				 if (x1 < x2) textX += xr;
				 else		  textX -= xl;
				 //if (x1 < x2) textX -= xl; else	textX += xr; // on inside
				 //if (x1 < x2) textX += -lineLength-15-(pctid==100 ? -7 : 0);
				 //else 		textX +=  lineLength;					 
				 
				 g2.setPaint(Color.black);
				 g2.setFont(textFont);
				 g2.drawString(""+(int)pctid, (int)textX, (int)y1);
			 }
			 if (seqObj2.getShowScoreText() || (isHover() && !seqObj2.getShowHitNumText())) {  	
				 double textX = x2; 
				 if (x1 < x2) textX -= xl;
				 else		  textX += xr;
				 //if (x1 < x2) textX += xr; else		  textX -= xl; // on inside
				 //if (x2 < x1) 	textX += lineLength-15-(pctid==100 ? -7 : 0);
				 //else 			textX += lineLength;					 
				 
				 g2.setPaint(Color.black);
				 g2.setFont(textFont);
				 g2.drawString(""+(int)pctid, (int)textX, (int)y2);					 
			 }
			 
			 /* hitNum CAS531 new */
			 if (seqObj1.getShowHitNumText()) {  
				 int num = (int)hitDataObj.getHitNum();
				 
				 double textX = x1;
				 if (x1 < x2) textX += xr;
				 else	{
					 int nl = String.valueOf(num).length()-1;
					 textX -= (xl + (nl*4));
				 }			 
				 g2.setPaint(Color.black);
				 g2.setFont(textFont);
				 g2.drawString(""+num, (int)textX, (int)y1);
			 }
			 if (seqObj2.getShowHitNumText()) {  
				 int num = (int)hitDataObj.getHitNum();
				 
				 double textX = x2;
				 if (x1 < x2) {
					 int nl = String.valueOf(num).length()-1;
					 textX -= (xl + (nl*4)) ;
				 }
				 else		  textX += xr;
				
				 g2.setPaint(Color.black);
				 g2.setFont(textFont);
				 g2.drawString(""+(int)hitDataObj.getHitNum(), (int)textX, (int)y2);					 
			 }
			 
		/* hit graphics: paint in seq1/2 rectangle the hit length graphics */
			 if (seqObj1.getShowHitLen()) { 					
				 Point2D rp1 = seqObj1.getPoint(hitDataObj.getStart1(), trackPos1);
				 Point2D rp2 = seqObj1.getPoint(hitDataObj.getEnd1(),   trackPos1);
				 
				 if (Math.abs(rp2.getY()-rp1.getY()) > 3) { 		// only draw if it will be visible
					 rp1.setLocation(x1-lineLength,rp1.getY());				 
					 rp2.setLocation(x1-lineLength,rp2.getY());
					 
					 paintHitLen(g2, seqObj1, rp1, rp2, trackPos1, hitDataObj.isPosOrient1(), btoggle);
				 }
			 }
			 if (seqObj2.getShowHitLen()) {
				 Point2D rp3 = seqObj2.getPoint(hitDataObj.getStart2(), trackPos2);
				 Point2D rp4 = seqObj2.getPoint(hitDataObj.getEnd2(),   trackPos2);
				 if (Math.abs(rp4.getY()-rp3.getY()) > 3) { 		// only draw if it will be visible
					 rp3.setLocation(x2+lineLength,rp3.getY());				 
					 rp4.setLocation(x2+lineLength,rp4.getY());
					 
					 paintHitLen(g2, seqObj2, rp3, rp4, trackPos2, hitDataObj.isPosOrient2(), btoggle);
				 }
			 }
			 /* Hover over hit line connector; CAS517 move creation to HitData */
			 if (isHover()) { 						
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
		 private void clear() {
			bHover=bSelected=bShow=false; // CAS541 add
		 }
		
		 // provide hover
		 private void mouseMoved(MouseEvent e) {
			 if (setHover(e.getPoint()))
				 mapper.getDrawingPanel().repaint();
		 }
		 /* CAS517 called for gene annotation from Sequence.java */
		private String getIfHit(Sequence st, int start, int end) {
			boolean isQuery = mapper.isQueryTrack(st);
			if (!isContained(start, end, isQuery)) return null;
			
			//boolean isPos = (isQuery) ? hitDataObj.isPosOrient1() : hitDataObj.isPosOrient2();
			String hit = "Hit #" + hitDataObj.getHitNum() + "\n"; // CAS520 changed from hit idx to hitnum
			
			if (isQuery) 	return hit + hitDataObj.getQueryBounds();
			else 			return hit + hitDataObj.getTargetBounds();
		}
		/* CAS516 popup from clicking hit wire; CAS531 change to use TextPopup */
		private void popupDesc(double x, double y) {
			hitDataObj.setIsPopup(true);
			String title="Hit #" + hitDataObj.getHitNum(); // CAS520 changed from hit idx to hitnum
			
			String theInfo = hitDataObj.createHover(st1LTst2) + "\n"; 
			
			String qHits = hitDataObj.getQueryBounds(); // list of subhits x:y, etc
			
			if (qHits!=null && qHits.length()>0) {
				String name1 = seqObj1.getFullName(); // Track Proj Chr
				String name2 = seqObj2.getFullName();
				String msg1 = "\n" + SeqData.formatHit(name1 + "\n" + qHits);
				String msg2 = "\n" + SeqData.formatHit(name2 + "\n" + hitDataObj.getTargetBounds());
				
				theInfo +=  st1LTst2 ? (msg1+msg2) : (msg2+msg1);
			}
			if (Globals.TRACE) {
				theInfo += "\nDB-index " + hitDataObj.getID(); // CAS520 puts at bottom, useful for debugging and out of way 
				theInfo += "\nAnnot " + hitDataObj.getAnnots(); // CAS543 add
				seqObj1.geneHigh(hitDataObj.getAnnot1(), true);
				seqObj2.geneHigh(hitDataObj.getAnnot2(), true);
			}
			boolean isQuery1 = seqObj1.isQuery();
			
			new TextShowInfo(mapper, title, theInfo, mapper.getDrawingPanel(), hitDataObj, 
				seqObj1.getTitle(), seqObj2.getTitle(), // title is Proj Chr
				seqObj1.getProjectDisplayName(), seqObj2.getProjectDisplayName(), isQuery1); 
		}
		public String toString() {return hitDataObj.createHover(true);}
	 } // End class PseudoHit
}
