package symap.mapper;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.event.MouseEvent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.util.TreeMap;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Vector;
import java.util.ArrayList;

import symap.closeup.TextShowInfo;
import symap.closeup.AlignPool;
import symap.sequence.Annotation;
import symap.sequence.Sequence;
import symap.Globals;

/***********************************************
 * All hits for a chr-chr pair; internal class DrawHit has both ends, which links to HitData
 * Mapper: Tracks 0-N are in order of display; Ref is always seqObj1
 * Hit Wire is the line between chromosomes
 * Hit Rect is the hit rectangle in the track, one on each end of wire
 * DrawHit: draws hit part the occurs on sequence: hit length and text (e.g. block#)
 */
public class SeqHits  {
	private static final int MOUSE_PADDING = 3;
	private static final int HIT_OFFSET = 15, HIT_MIN_OFFSET=6; // amount line enters track; 15, 12,9,6
	private static final int HIT_INC = 3, HIT_OVERLAP = 75; // default minimum match bases is 100, so hits should be more
	private static final int TEXTCLOSE = 7;					
	
	public static  Font textFont = Globals.textFont;
	private static Font textFontBig = new Font(Font.MONOSPACED,Font.BOLD,16);
	
	private int projIdx1, projIdx2; // project IDs
	private int grpIdx1, grpIdx2; // groupIDs: chromosome 
	
	private Mapper mapper;
	private DrawHit[] allHitsArray;
	
	// query has dbName < target; seqObj1 is query (corresponds to pseudo_hits.annot1_idx); 
	private Sequence seqObj1, seqObj2; // query, target; query may be the left or right track
	private boolean st1LTst2=true; 	   // if seqObj1.track# <seqObj2.track#, query is left track, else, right
	
	private String infoMsg=""; 
	private int cntShowHit=0, cntHighHit=0;	
	private int lastText1=0, lastText2=0;		// Do not print text on top of another
	
	private int nG2xN=0;
	private boolean isOnlyG2xN=false;	
	private int cntHighG2xN=0, cntFiltG2xN=0, cntPossG2xN=0;
	
	private HashMap <Integer, Block> blockMap = new HashMap <Integer, Block> ();
	
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
			
			// Set up blocks - 1st/last gets set per display
			int b = hd.getBlock();
			if (b>0) {
				if (!blockMap.containsKey(b)) {
					Block blk = new Block();
					blockMap.put(b, blk);
					blk.isInv = hd.isBlkInv();
				}
			}
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
		
		projIdx1 = st1.getProjIdx();
		projIdx2 = st2.getProjIdx();
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
	
	public Sequence getSeqObj1() {return seqObj1;}
	public Sequence getSeqObj2() {return seqObj2;}
	 
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
	 
	 /*  g2xN methods  */
	 public Vector <HitData>  getVisG2Hits() {		// SeqPool.computeG2xN
		 Vector <HitData> hitList = new Vector<HitData> ();
		 for (DrawHit hObj : allHitsArray) {
			 if (hObj.isOlapHit()) {				// hit overlaps visible chromosome
				 if (hObj.hitDataObj.is2Gene() && !hObj.isFiltered())
					 hitList.add(hObj.hitDataObj);		 
			 }
		 }
		 return hitList;
	 }
	 public Vector <HitData>  getInVisG2Hits() {	// SeqPool.computeG2xN
		 Vector <HitData> hitList = new Vector<HitData> ();
		 for (DrawHit hObj : allHitsArray) {
			 if (hObj.isOlapHit()) {
				 if (hObj.hitDataObj.is2Gene() && hObj.isFiltered())
					 hitList.add(hObj.hitDataObj);		 
			 }
		 }
		 return hitList;
	 }
	 public Vector <HitData>  getVisG1Hits(int which) {		// SeqPool.computeG2xN
		 Vector <HitData> hitList = new Vector<HitData> ();
		 for (DrawHit dht : allHitsArray) {
			 if (dht.isOlapHit() && dht.hitDataObj.is1Gene() && !dht.isFiltered()) {
				if (which==1 && dht.hitDataObj.annot1_idx==0) 		hitList.add(dht.hitDataObj);	
				else if (which==2 && dht.hitDataObj.annot2_idx==0)	hitList.add(dht.hitDataObj);	
			 }
		 }
		 return hitList;
	 }
	 public void setnG2xN(int n) {						// Sequence.refHighG2xN 
		 nG2xN=n;
		 seqObj1.setnG2xN(n);  seqObj2.setnG2xN(n);
	 }	
	 public void setOnlyG2xN(boolean b) {isOnlyG2xN=b;} // Sequence.showG2xN
		
	 public void clearHighG2xN() { 					// Sequence.refHighG2xN 
		 for (DrawHit hd: allHitsArray) 
			 hd.hitDataObj.clearG2xN();
		 
		 seqObj1.setnG2xN(0);  seqObj2.setnG2xN(0); // need to get set in L/R also
		 
		 cntHighG2xN=cntFiltG2xN=cntPossG2xN=0;
		 nG2xN=0;	
	 }
	 /*****************************************************
	  * XXX Paint all hits 	
	  */
	 public void paintComponent(Graphics2D g2) {
		lastText1=lastText2=0;
		
		mapper.setHelpText(null); // will be set if hover; CAS577
		
		Point stLoc1 = seqObj1.getLocation();
		Point stLoc2 = seqObj2.getLocation();
		int trackPos1 = mapper.getTrackPosition(seqObj1); // left or right
		int trackPos2 = mapper.getTrackPosition(seqObj2);
		ArrayList<DrawHit> selHits = new ArrayList<DrawHit>(); 
		
		TreeMap <Integer, Integer> highMap = new TreeMap <Integer, Integer> (); // if collinear or blocks is highlighted
		int hcolor=1; 
		
		HfilterData hf = mapper.getHitFilter(); 
		boolean bHiCset  = hf.isHiCset();
		boolean bHiBlock = hf.isHiBlock();
		boolean bHiLight = (bHiCset || bHiBlock);
		boolean bHi = bHiLight || hf.isHi0Gene() || hf.isHi1Gene() || hf.isHi2Gene();
		
		// Create blk/coset color map; allHitArray sorted by start1; !correspond to sequential blocks (sorted sets).
		if (bHiLight) {
			for (DrawHit h : allHitsArray) {
				if (!h.isOlapHit()) continue; 
				
				int set = (bHiCset) ?  h.getCollinearSet() : h.getBlock();
				if (set!=0 && !highMap.containsKey(set)) {
					highMap.put(set, hcolor);
					hcolor++;  
					if (hcolor==5) hcolor=1; // redone for cosets below;  4 colors for blocks
				}
			}
			hcolor=1;
			if (bHiCset) { //sorted by start1 does not correspond to sequence sets
				for (int set : highMap.keySet()) {
					highMap.put(set, hcolor);
					hcolor++;
					if (hcolor==3) hcolor=1;  
				}
			}
		}
		// Set block 1st/last for this display
		if (seqObj1.getShowBlock1stText() || seqObj2.getShowBlock1stText()) {
			for (Block blk : blockMap.values()) blk.hitnum1 = blk.hitnumN = 0;
			
			for (DrawHit h : allHitsArray) {
				if (h.isOlapHit() && !h.isFiltered()) { 
					int n = h.getBlock();
					if (n>0) {
						Block b = blockMap.get(h.getBlock());
						int num = h.hitDataObj.getHitNum();
						if (b.hitnum1==0) b.hitnum1 = num;
						b.hitnumN = num;
					}
				}
			}
		}
		cntShowHit=cntHighHit=cntHighG2xN=cntPossG2xN=cntFiltG2xN=0; // counted in paint
		HashSet <Integer> numSet = new HashSet <Integer> (); // count blocks/sets shown
		hcolor=1; 

		// Draw all lines but hover or selected from Query Table
		for (DrawHit dh : allHitsArray) {
			if (!dh.isOlapHit() || dh.isFiltered()) continue; 
			
			if (bHiLight) {
				int set = (bHiCset) ?  dh.getCollinearSet() : dh.getBlock();
				if (set!=0) hcolor = highMap.get(set);
			}
			if (dh.isHover || dh.isQuerySelHit) selHits.add(dh); 
			else {
				dh.paintComponent(g2, trackPos1, trackPos2, stLoc1, stLoc2, hcolor, false);
				if (bHiLight) {
					int set = (bHiCset) ?  dh.getCollinearSet() : dh.getBlock();
					if (set!=0 && !numSet.contains(set)) numSet.add(set);
				}
			}
		}
		int cntGrpHits=0;
		
		// Draw hover hits on top with highlight
		for (DrawHit dh : selHits) {
			if (!dh.isOlapHit() || dh.isFiltered()) continue; 
			
			dh.paintComponent(g2,trackPos1,trackPos2,stLoc1,stLoc2, 1, false);
			if (bHiLight) {
				int set = (bHiCset) ?  dh.getCollinearSet() : dh.getBlock();
				if (set!=0 && !numSet.contains(set)) numSet.add(set);
			}
			if (dh.isQuerySelHit) cntGrpHits++;
		}
		
		// Draw text on top of hits
		if (seqObj1.getHasText() || seqObj2.getHasText()) {
			for (DrawHit dh : allHitsArray) {
				dh.paintComponent(g2,trackPos1,trackPos2,stLoc1,stLoc2, 1, true);
			}
		}
				
		// Information box on Stats; \n and extra blanks removed for Query 2D in HelpBar.setHelp
		infoMsg =  String.format("Hits:   %,d", cntShowHit);
		
		if (bHi)          infoMsg += String.format("\nHigh:   %,d",  cntHighHit);// after hits since it high hits
		if (cntGrpHits>0) infoMsg += String.format("\nGroup Hits: %,d",  cntGrpHits);// only from Queries Group
		
		if (bHiBlock)     infoMsg += String.format("\nBlocks: %,d", numSet.size());
		else if (bHiCset) infoMsg += String.format("\nCosets: %,d", numSet.size());
		
		if (nG2xN>0) {
			String x = (nG2xN==2) ? "g2x2" : "g2x1";
			int cnt =  (nG2xN==2) ? (cntHighG2xN+cntFiltG2xN) : cntHighG2xN;
			if (cntHighG2xN>0 && (cntFiltG2xN>0 ||cntPossG2xN>0)) infoMsg += String.format("\n\n%-11s: %,3d", x, cnt);
			else 												  infoMsg += String.format("\n\n%s: %,d", x, cnt);
			
			if (cntFiltG2xN>0) infoMsg += String.format("\n%-11s: %,3d", "Filtered g2", cntFiltG2xN); 
			if (cntPossG2xN>0) infoMsg += String.format("\n%-11s: %,3d (red)",  "Possible g2", cntPossG2xN);	
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
	public boolean hasVisHit(int [] idxList) { // Called from Sequence for Annotation display GeneNum
		for (DrawHit drObj : allHitsArray) {
			if (!drObj.isDisplayed) continue;
			int idx = drObj.hitDataObj.getID();
			for (int i=0; i<idxList.length; i++) if (idx==idxList[i]) return true;
		}
		return false;
	}
	public void mouseMoved(MouseEvent e) {
		for (DrawHit h : allHitsArray)
			if (h.mouseMovedHit(e)) return; // once wire is highlighted, do not need to check the rest; CAS577
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
	 * XXX DrawHit class for drawing, 1-1 with HitData, which has the data and this has the drawing stuff
	 */
	private class DrawHit {
		private HitData hitDataObj;
		private Line2D.Double hitWire;
		
		// There are also flags in hitDataObj; isBlock, isCollinear, isPopup, isHighHitg2
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
			// hit filter PCT
			 HfilterData hf = mapper.getHitFilter();
			 boolean bIsNotPCT = (hitDataObj.getPctid()<hf.getPctid());
			 if (bIsNotPCT) {
				 isDisplayed=false;
				 return true;
			 }
			 
			// Sequence filter
			 if (isOnlyG2xN) {
				 if (!hitDataObj.isHighG2xN())             {isDisplayed=false; return true;}// red and all others
				 if (nG2xN!=2 && hitDataObj.isForceG2xN()) {isDisplayed=false; return true;} 
			 }
			 if (hitDataObj.isHighG2xN())  {isDisplayed=true;  return false;}
			 if (hitDataObj.isForceG2xN()) {isDisplayed=true;  return false;}
				
			 // Hit filters
			 isDisplayed=true;
			
			 // precedence
			 if (hf.isAll()) return false; // Over-rides everything but G2xN and PCT
			 
			 if (hf.isBlockOnly() && hf.getBlock()>0) {		
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
		 private Color getCColor(String orient, int hcolor, boolean bCnt) {
			 if (isHover)     return Mapper.pseudoLineHoverColor;	
			
			 HfilterData hf = mapper.getHitFilter(); 
			 
			 if (hf.isHiPopup() && hitDataObj.isPopup()) return Mapper.pseudoLineGroupColor;
			 if (hf.isHiPopup() && isQuerySelHit)        return Mapper.pseudoLineGroupColor;
			 
			 if (hitDataObj.isHighG2xN()) { // takes precedence over highlight
				 if (hitDataObj.isForceG2xN()) {
					 if (bCnt) cntFiltG2xN++;
					 return Mapper.pseudoLineHighlightColor2; // default pink
				 }
				 else{
					 if (bCnt) cntHighG2xN++;
					 return Mapper.pseudoLineHighlightColor1; // default green
				 }
			 }
			 else if (hitDataObj.isForceG2xN()) {
				 if (bCnt) cntPossG2xN++;
				 return Color.red; // default red
			 }
	
			 boolean isHi=false;
			 if      (hf.isHiBlock() && hitDataObj.isBlock()) isHi=true;
			 else if (hf.isHiCset()  && hitDataObj.isCset())  isHi=true;
			 else if (hf.isHi2Gene() && hitDataObj.is2Gene()) isHi=true;
			 else if (hf.isHi1Gene() && hitDataObj.is1Gene()) isHi=true;
			 else if (hf.isHi0Gene() && hitDataObj.is0Gene()) isHi=true;
			 if (isHi) {
				 if (bCnt) cntHighHit++;
				 if (hcolor==1) 		 return Mapper.pseudoLineHighlightColor1;		
				 else if (hcolor==2)	 return Mapper.pseudoLineHighlightColor2;
				 else if (hcolor==3)	 return Mapper.pseudoLineHighlightColor3;
				 else if (hcolor==4)	 return Mapper.pseudoLineHighlightColor4;
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
		 private boolean mouseMovedHit(MouseEvent e) {
			 if (!isDisplayed) return false;
	
			 boolean b = isOlapHit() && lineContains(e.getPoint()); 
			 if (setHover(b)) {
				 mapper.getDrawingPanel().repaint();
				 return true;
			 }
			 return false;
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
		 private boolean paintComponent(Graphics2D g2, int trackPos1, int trackPos2, Point stLoc1, Point stLoc2, int hcolor, boolean isText) {  	
			 if (!isOlapHit() || isFiltered()) return false; 
			
			 if (!isText) cntShowHit++;
			
		   // get border locations of sequence tracks to draw hit stuff to it and along it
			 // sqObj.getPointForHit truncates point to not overlap track ends
			 Point2D hw1 = seqObj1.getPointForHit(hitDataObj.mid1 , trackPos1 );
			 hw1.setLocation(hw1.getX() + stLoc1.getX(), hw1.getY() + stLoc1.getY());
			
			 Point2D hw2 = seqObj2.getPointForHit(hitDataObj.mid2 , trackPos2 );
			 hw2.setLocation(hw2.getX() + stLoc2.getX(), hw2.getY() + stLoc2.getY());
			
			 int x1 = (int)hw1.getX(), y1 = (int)hw1.getY();
			 int x2 = (int)hw2.getX(), y2 = (int)hw2.getY();
			 int pctid = (int)hitDataObj.getPctid();
			 
		   // hitLine: hit connecting seq1/seq2 chromosomes 
			 if (!isText) {
				 Color c = getCColor(hitDataObj.getOrients(), hcolor, true);
				 if (c==Mapper.pseudoLineGroupColor) g2.setStroke(new BasicStroke(3)); 
				 g2.setPaint(c); 
				 hitWire.setLine(hw1,hw2); 
				 g2.draw(hitWire); 
				 if (c==Mapper.pseudoLineGroupColor) g2.setStroke(new BasicStroke(1));
				 
				 /* %id line: paint in seq1/2 rectangle the %id length;  */
				 // id=19 is 5 outside rect, id=23 is 7 inside rect; id=100 is 30 far in rect
				
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
						 
						 paintHitLen(g2, seqObj1, rp1, rp2, trackPos1, hitDataObj.isPosOrient1(), hcolor);
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
						 
						 paintHitLen(g2, seqObj2, rp3, rp4, trackPos2, hitDataObj.isPosOrient2(), hcolor);
					 }
				 }
				 // Hover over hit line connector; creation in HitData 
				 if (isHover) mapper.setHelpText(hitDataObj.createHover(st1LTst2));
			 }
			 else {/******** text from sequence.Filter 'Show text' ***********/
				 int xr=4, xl=19; 
				 
				 String numText1=null;  
				 if (seqObj1.getShowBlock1stText())  { 
					 int n = hitDataObj.getBlock();
					 if (n>0) {
						 Block blk = blockMap.get(n);
						 int hitnum = hitDataObj.getHitNum();
						 if (!seqObj1.isFlipped()) {
							 if (hitnum==blk.hitnum1) numText1 = "b"+n;
						 }
						 else {
							 if (hitnum==blk.hitnumN) numText1 = "b"+n;
						 }
					 }
				 }
				 else  if (seqObj1.getShowBlockText())  {
					 int n = hitDataObj.getBlock();
					 if (n>0) numText1 = "b"+n;
				 }
				 else  if (seqObj1.getShowCsetText())   {
					 int n = hitDataObj.getCollinearSet();
					 if (n>0) numText1 = "c"+n;
				 }
				 else if (seqObj1.getShowHitNumText()) numText1 = "#" + hitDataObj.getHitNum();  
				 else if (seqObj1.getShowScoreText())  numText1 = (int)pctid+"%";
				
				 if (numText1!=null) {  
					 double textX = x1;
					 if (x1 < x2) textX += xr;
					 else	{
						 int nl = numText1.length()-1;
						 textX -= (xl + (nl*4));
					 }		
					 if (Math.abs(y1-lastText1)>TEXTCLOSE) { 
						 g2.setPaint(Color.black);
						 if (seqObj1.getShowBlock1stText()) g2.setFont(textFontBig);
						 else                               g2.setFont(textFont);
						 g2.drawString(numText1, (int)textX, (int)y1);
						 lastText1 = y1;
					 }
					
				 }
				
				 String numText2=null; 
				 if (seqObj2.getShowBlock1stText())  {
					 int n = hitDataObj.getBlock();
					 if (n>0) {
						 Block blk = blockMap.get(n);
						 int hitnum = hitDataObj.getHitNum();
						 if (!seqObj2.isFlipped()) {
							 if ((!blk.isInv && hitnum==blk.hitnum1) || (blk.isInv && hitnum==blk.hitnumN)) numText2 = "b"+n;
						 }
						 else {
							 if ((!blk.isInv && hitnum==blk.hitnumN) || (blk.isInv && hitnum==blk.hitnum1)) numText2 = "b"+n;
						 }	 
					 }
				 }
				 else if (seqObj2.getShowBlockText())  {
					 int n = hitDataObj.getBlock();
					 if (n>0) numText2 = "b"+n;
				 }
				 else if (seqObj2.getShowCsetText())  {
					 int n = hitDataObj.getCollinearSet();
					 if (n>0) numText2 = "c"+n;
				 }
				 else if (seqObj2.getShowHitNumText()) numText2 = "#"+hitDataObj.getHitNum();
				 else if (seqObj2.getShowScoreText())  numText2 = (int)pctid +"%";
				
				 if (numText2!=null) {  
					 double textX = x2;
					 if (x1 < x2) {
						 int nl = numText2.length()-1;
						 textX -= (xl + (nl*4)) ;
					 }
					 else textX += xr;
					
					 if (Math.abs(y2-lastText2)>TEXTCLOSE) { // not sorted on this side, so may not work all the time
						 g2.setPaint(Color.black);
						 if (seqObj2.getShowBlock1stText()) g2.setFont(textFontBig);
						 else                               g2.setFont(textFont);
						 g2.drawString(numText2, (int)textX, (int)y2);	
						 lastText2 = y2;
					 }
				 }
			 }
			 return true;
		 }
		 /***************************************************************************
		  * Draw hit - draws the length of the hit along the blue chromosome box, which shows breaks between merged hitsd
		  */
		 private void paintHitLen(Graphics2D g2, Sequence st, Point2D pStart, Point2D pEnd, 
				 int trackPos, boolean forward, int hcolor) {
			 double psx = pStart.getX();
			 double psy = pStart.getY();
			 double w = Mapper.hitRibbonWidth;
			 
			 String subHits;
			 Rectangle2D.Double rect;
			 
			 if (mapper.isQueryTrack(st)) 
				 subHits = hitDataObj.getQueryMerge(); 
			 else
				 subHits = hitDataObj.getTargetMerge();

			 if (subHits == null || subHits.length() == 0) {
				 g2.setPaint(getCColor(hitDataObj.getOrients(), hcolor, false));
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
				 
				 g2.setPaint(getCColor(hitDataObj.getOrients(), hcolor, false));
				 
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
		// adjust rectangle coordinates for negative width or height - on flipped
		private void fixRect(Rectangle2D rect) { 
			if (rect.getWidth() < 0)
				rect.setRect(rect.getX()+rect.getWidth()+1, rect.getY(), Math.abs(rect.getWidth()), rect.getHeight());
			
			if (rect.getHeight() < 0)
				rect.setRect(rect.getX(), rect.getY()+rect.getHeight()+1, rect.getWidth(), Math.abs(rect.getHeight()));
		}
		/* popup from clicking hit wire;  */
		private void popupDesc(double x, double y) { 
			if (mapper.getHitFilter().isHiPopup()) hitDataObj.setIsPopup(true); 
			
			String title="Hit #" + hitDataObj.getHitNum(); 
			
			String theInfo = hitDataObj.createPopup(st1LTst2) + "\n"; 
			
			String proj1 = seqObj1.getTitle(); //Proj Chr
			String proj2 = seqObj2.getTitle(); 
			Annotation aObj1 = seqObj1.getAnnoObj(hitDataObj.getAnnot1(), hitDataObj.getAnnot2()); // check seqObj1 geneVec
			Annotation aObj2 = seqObj2.getAnnoObj(hitDataObj.getAnnot1(), hitDataObj.getAnnot2()); // check seqObj2 geneVec
			
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
	
	// To draw block num at beginning
	private class Block {
		int hitnum1=0, hitnumN=0;
		boolean isInv=false;
	}
}
