package symap.mapper;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.event.MouseEvent;
import java.awt.Color; 				
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedList;

import symap.sequence.Sequence;
import symap.SyMAPConstants;
import util.Utilities;

public class PseudoPseudoHits extends AbstractHitData implements Hits, SyMAPConstants {
	private static final boolean TIME_TRACE = false;
	private static final boolean METHOD_TRACE = false;
	
	private static final int MOUSE_PADDING = 3;
	
	private Mapper mapper;
	private Sequence st1;
	private Sequence st2;
	private PseudoHit[] hits;

	public PseudoPseudoHits(int p1ID, int group1, int p2ID, int group2, boolean swap) {
		super(p1ID,group1,p2ID,group2,Mapper.PSEUDO2PSEUDO,swap);		
	}

	public PseudoPseudoHits(Mapper mapper, Sequence st1, Sequence st2, PseudoPseudoData data, boolean swap) {
		this(data.getProject1(),data.getGroup1(),data.getProject2(),data.getGroup2(),swap);
		this.mapper = mapper;
		this.st1 = st1;
		this.st2 = st2;
		setHits(data);
	}

	public PseudoPseudoData.PseudoHitData[] getPseudoHitData() {
		PseudoPseudoData.PseudoHitData[] phd = new PseudoPseudoData.PseudoHitData[hits == null ? 0 : hits.length];
		for (int i = 0; i < phd.length; i++) phd[i] = hits[i].data;
		return phd;
	}

	public boolean setHits(PseudoPseudoData data) {
		long cStart = System.currentTimeMillis();
		
		PseudoPseudoData.PseudoHitData[] phd = data.getPseudoHitData();
			
		hits = new PseudoHit[data == null || phd == null ? 0 : phd.length];
		for (int i = 0; i < hits.length; i++) {
			boolean selected = phd[i].getStart1() == mapper.getSelectedSeq1Start();
			selected = selected || phd[i].getStart2() == mapper.getSelectedSeq2Start();
			selected = selected || phd[i].getEnd1() == mapper.getSelectedSeq1End();
			selected = selected || phd[i].getEnd2() == mapper.getSelectedSeq2End();
				
			hits[i] = new PseudoHit(phd[i]);
			hits[i].set(selected);
		}
			
		if (TIME_TRACE) System.out.println("PseudoPseudoHits: setHits() time = "+(System.currentTimeMillis()-cStart)+" ms");
		return true;
	}

	public boolean addHits(int newHitContent, Collection hitData) {
			if (hits == null) hits = new PseudoHit[0];
			if (hitData == null) hitData = new LinkedList();

			PseudoHit h[] = new PseudoHit[hits.length+hitData.size()];
			System.arraycopy(hits,0,h,0,hits.length);
			int i = hits.length;
			hits = h;
			for (Iterator iter = hitData.iterator(); i < hits.length; i++) {
				hits[i] = new PseudoHit((PseudoPseudoData.PseudoHitData)iter.next());
				hits[i].set(false);
			}
			return true;
	}

	public void clear() {
		for (PseudoHit h : hits)
			h.clear();	
	}

	public void set(Sequence st1, Sequence st2) {
		this.st1 = st1;
		this.st2 = st2;
		for (PseudoHit h : hits)
			h.set(false);
	}

	public void setMinMax(HitFilter hf) {
		for (PseudoHit h : hits)
			h.setMinMax(hf);
	}

	public void getSequenceMinMax(int[] minMax) {
		for (PseudoHit h : hits)
			h.getSequenceMinMax(minMax);
	}
	
	public void getMinMax(int[] minMax, int start, int end, boolean swap) {
		for (PseudoHit h : hits) {
			if (h.isContained(start, end, swap)) {
				minMax[0] = Math.min( minMax[0], h.data.getStart1(swap) );
				minMax[1] = Math.max( minMax[1], h.data.getEnd1(swap) );
			}
		}
	}

	/**
	 * Method <code>getHitData</code> adds an FPCPseudoData object to 
	 * hits containing all the marker hit data and bes hit data for the
	 * hits that are visible, not filtered, and fall in the range of start and
	 * end. If no hits exist than an FPCPseudoData object is not added.
	 *
	 * @param hitList a <code>List</code> value
	 * @param start an <code>int</code> value
	 * @param end an <code>int</code> value
	 */
	 public void getHitsInRange(List<AbstractHitData> hitList, int start, int end, boolean swap) {
		 List<AbstractHitData> h = new LinkedList<AbstractHitData>();
		 for (int i = 0; i < hits.length; i++)
			 hits[i].getHit(h,start,end,swap);
		 if (!h.isEmpty()) 
			 hitList.add(new PseudoPseudoData(getProject1(),getGroup1(),getProject2(),getGroup2(),getHitContent(),h,swap));
		 
		 if (METHOD_TRACE) System.out.println("PseudoPseudoHits.getHitData: "+h.size()+" hits " + start + ":" + end + " " + swap);
	 }

	public void paintComponent(Graphics2D g2) {
		Point stLocation1 = st1.getLocation();
		Point stLocation2 = st2.getLocation();
		int stOrient1 = mapper.getOrientation(st1);
		int stOrient2 = mapper.getOrientation(st2);
		List<PseudoHit> hHits = new LinkedList<PseudoHit>();

		// Draw non-highlighted hits first
		for (PseudoHit h : hits) {
			if (h.isHighlight() || h.isHover()) hHits.add(h);
			else h.paintComponent(g2,stOrient1,stOrient2,stLocation1,stLocation2);
		}

		// Draw highlighted hits on top
		for (PseudoHit h : hHits)
			h.paintComponent(g2,stOrient1,stOrient2,stLocation1,stLocation2);
	}

	public void mouseMoved(MouseEvent e) {
		for (PseudoHit h : hits)
			h.mouseMoved(e);
	}
	 
	public void mouseExited(MouseEvent e) { 
		// Need to do this otherwise hits will stay highlighted when exited.
		for (PseudoHit h : hits)
			h.setHover(false); 
		mapper.getDrawingPanel().repaint();
	}

	private class PseudoHit implements Hit {
		private PseudoPseudoData.PseudoHitData data;
		private Line2D.Double hitLine;
		private boolean hover;	
		private boolean highlight;
		private boolean geneContained; 
		private boolean geneOverlap; 	

		public PseudoHit(PseudoPseudoData.PseudoHitData data) {
			this.data = data;
			hitLine = new Line2D.Double();
			hover = false;
			highlight = false;
		}
		
		public boolean isContained(int start, int end, boolean swap) { 
			return ( isVisible() && !isFiltered() && 
					( (data.getStart2(swap) >= start && data.getStart2(swap) <= end) ||
					  (data.getEnd2(swap) >= start && data.getEnd2(swap) <= end) ||
					  (start >= data.getStart2(swap) && end <= data.getEnd2(swap)) ));
		}
		 
		public void getHit(List hitList, int start, int end, boolean swap) { 
			if ( isContained(start, end, swap) )
				hitList.add(data);
		}

		public void setMinMax(HitFilter hf) {
			// Note that we re-used the "bes" methods instead of creating
			// new ones for pseudo-to-pseudo.
			hf.condSetBesEvalue(data.getEvalue(),data.getEvalue());
			hf.condSetBesPctid(data.getPctid(),data.getPctid());
		}

		 public void getSequenceMinMax(int[] minMax) {
			 if (isVisible() && !isFiltered()) {
				 PseudoPseudoHits.getSequenceMinMax(minMax,data.getStart2(),data.getEnd2());
			 }
		 }
		 
		 public void getSequenceMinMax(int[] minMax, int start, int end, boolean swap) {
			 if (isVisible() && !isFiltered()) {
				 PseudoPseudoHits.getSequenceMinMax(minMax,start,end);
			 }
		 }

		public String getType() {
			return SEQ_TYPE;
		}

		 public boolean isBlockHit() {
			 return data.isBlockHit();
		 }

		 public void set(boolean highlightHit) {
			 highlight = highlightHit;
			 geneContained = isGeneContained();
			 geneOverlap = isGeneOverlap();
		 }
		 
		 private boolean isGeneContained() {			 
			 return (data.getOverlap() > 0); 
		 }
		 
		 private boolean isGeneOverlap() {			 
			 return (data.getOverlap() > 0); 
		 }
		 
		 public boolean isFiltered() {
			 HitFilter hitfilter = mapper.getHitFilter();
			 return false 
			 		|| (hitfilter.getBlock() && !data.isBlockHit()) 
			 		|| (hitfilter.getGeneContained() && !geneContained) 
			 		|| (hitfilter.getGeneOverlap() && !geneOverlap) 
			 		|| (hitfilter.getNonGene() && (geneOverlap || geneContained));
		 }

		 public boolean isHover() {
			 return hover;
		 }
		 
		 public boolean isHighlight() {
			 return highlight;
		 }

		 public boolean isVisible() {
			 return isSequenceVisible(st1,data.getStart1(),data.getEnd1()) 
			 		&& isSequenceVisible(st2,data.getStart2(),data.getEnd2());
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
		 
		 public boolean isHover(Point point) { 
			 return isVisible() && lineContains(hitLine, point);
		 }
		 
		 protected boolean setHover(Point point) { 
			return setHover(isHover(point));
		 }

		 protected boolean setHover(boolean hover) {
			if (hover != this.hover) {
				this.hover = hover;
				return true;
			}
			return false;
		 }
		 
		 private Color getCColor() {
			 if (isHighlight() || isHover())
				 return Mapper.pseudoLineHighlightColor;
			 return Mapper.pseudoLineColor;
		 }
		 
		 private void drawHitRibbon(Graphics2D g2, Sequence st, Point2D pStart, Point2D pEnd, int orient, boolean forward) {
			 String subHits;
			 
			 if (mapper.isSwapped(st) || (orient == LEFT_ORIENT && mapper.isSelf() ))
				 subHits = data.getQuerySeq();
			 else
				 subHits = data.getTargetSeq();
			 
			 if (subHits == null || subHits.length() == 0) {
				 g2.setPaint(getCColor());
				 g2.fill( new Rectangle2D.Double( 
						 		pStart.getX(),
	 							pStart.getY(),
	 							Mapper.hitRibbonWidth,
	 							Math.abs(pEnd.getY()-pStart.getY())) );
			 }
			 else {
				 // Draw background rectangle
				 g2.setPaint(Mapper.hitRibbonBackgroundColor);
				 Rectangle2D.Double rect = new Rectangle2D.Double(
						 						pStart.getX(),
						 						pStart.getY(),
						 						Mapper.hitRibbonWidth,
						 						pEnd.getY()-pStart.getY());
				 Utilities.fixRect(rect); // for flipped track
				 g2.fill(rect);
				 g2.draw(rect);
				 g2.setPaint(getCColor());
				 
				 // Draw sub-hits
				 String[] subseq = subHits.split(",");
				 for (int i = 0;  i < subseq.length;  i++) {
					 String[] pos = subseq[i].split(":");
					 long start = Long.parseLong(pos[0]);
					 long end = Long.parseLong(pos[1]);
					 Point2D p1 = st.getPoint(start, orient);
					 p1.setLocation( pStart.getX(), p1.getY() );
					 Point2D p2 = st.getPoint(end, orient);
					 p2.setLocation( pStart.getX(), p2.getY() );
					 rect.setRect( p1.getX(), p1.getY(), Mapper.hitRibbonWidth, p2.getY()-p1.getY() );
					 Utilities.fixRect(rect); // for flipped track
					 g2.fill(rect);
					 g2.draw(rect);
				 }
			 }

	
		 }
		 
		 public void paintComponent(Graphics2D g2, int stOrient1, int stOrient2, Point stLocation1, Point stLocation2) {
			 if (isVisible() && !isFiltered()) {
				 Point2D sp1 = getSequenceCPoint(st1,data.getStart1(),data.getEnd1(),stOrient1,stLocation1);
				 Point2D sp2 = getSequenceCPoint(st2,data.getStart2(),data.getEnd2(),stOrient2,stLocation2);

				 g2.setPaint(getCColor());
				 hitLine.setLine(sp1,sp2);
				 g2.draw(hitLine);	
				 		
				 // FIXME: code below could be simplified/condensed.
				 
				 int lineLength = 1;
				 if (st1.getShowScoreLine() || st2.getShowScoreLine()) { 
					 int pctid = (int)data.getPctid();
					 int minPctid = (int)Math.min(mapper.getHitFilter().getMinBesPctid(),
							 					mapper.getHitFilter().getMinMrkPctid());
					 
					 lineLength = Math.max(1, 30*(pctid-minPctid+1)/(100-minPctid+1));
					 g2.setPaint(getCColor());
					 int x1=(int)sp1.getX(), y1=(int)sp1.getY();
					 int x2=(int)sp2.getX(), y2=(int)sp2.getY();
					 if (stOrient1 == RIGHT_ORIENT) lineLength = 0 - lineLength; 
					 if (st2.getShowScoreLine()) g2.drawLine(x2,y2,x2+lineLength,y2);
					 if (st1.getShowScoreLine()) g2.drawLine(x1,y1,x1-lineLength,y1);
				 }
				 if (st1.getShowScoreValue() || isHover()) {
					 double pctid = data.getPctid();
					 double textX;
					 textX = sp1.getX();
					 if (sp1.getX() < sp2.getX()) textX += -lineLength-15-(pctid==100 ? -7 : 0);
					 else textX += lineLength;					 
					 g2.setPaint(Color.black);
					 g2.drawString(""+(int)pctid, (int)textX, (int)sp1.getY());
				 }
				 if (st2.getShowScoreValue() || isHover()) {
					 double pctid = data.getPctid();
					 double textX;
					 textX = sp2.getX();
					 if (sp2.getX() < sp1.getX()) textX += lineLength-15-(pctid==100 ? -7 : 0);
					 else textX += lineLength;					 
					 g2.setPaint(Color.black);
					 g2.drawString(""+(int)pctid, (int)textX, (int)sp2.getY());					 
				 }
				 if (st1.getShowRibbon()) { 
					 Point2D rp1 = st1.getPoint(data.getStart1(), stOrient1);
					 Point2D rp2 = st1.getPoint(data.getEnd1(), stOrient1);
					 if (Math.abs(rp2.getY()-rp1.getY()) > 3) { // only draw if it will be visible
						 rp1.setLocation(sp1.getX()-lineLength,rp1.getY());				 
						 rp2.setLocation(sp1.getX()-lineLength,rp2.getY());
						 drawHitRibbon(g2, st1, rp1, rp2, stOrient1, data.getOrientation1());
					 }
				 }
				 if (st2.getShowRibbon()) { 
					 Point2D rp3 = st2.getPoint(data.getStart2(), stOrient2);
					 Point2D rp4 = st2.getPoint(data.getEnd2(), stOrient2);
					 if (Math.abs(rp4.getY()-rp3.getY()) > 3) { // only draw if it will be visible
						 rp3.setLocation(sp2.getX()+lineLength,rp3.getY());				 
						 rp4.setLocation(sp2.getX()+lineLength,rp4.getY());
						 drawHitRibbon(g2, st2, rp3, rp4, stOrient2,data.getOrientation2());
					 }
				 }
				 
				 if (isHover() || isHighlight()) {
					 mapper.setHelpText(
							 "Hit=" + data.getID() + " " + 
							 "Identity=" + data.getPctid() + "\n" +
							 "Left: len=" + (Math.abs(data.getEnd1()-data.getStart1())+1) + " " +
							 "Right: len=" + (Math.abs(data.getEnd2()-data.getStart2())+1)
					 );
				 }
				 else mapper.setHelpText(null);
			 }
		 }

		 public void clear() {
			 //bh.clear(this);
		 }

		 public void mouseMoved(MouseEvent e) {
			 if (setHover(e.getPoint()))
				 mapper.getDrawingPanel().repaint();
		 }
	 }


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
}
