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
import symap.marker.MarkerTrack;
import symap.sequence.Annotation;
import symap.sequence.Sequence;
import symap.SyMAPConstants;

//public class FPCPseudoHits extends AbstractFPCPseudoHits implements Hits, SyMAPConstants { // mdb removed 7/25/07 #134
public class FPCPseudoHits extends AbstractHitData implements Hits, SyMAPConstants { // mdb added 7/25/07 #134
	//private static final boolean METHOD_TRACE = false; // mdb added 2/27/07
	
	private static final int MOUSE_PADDING = 3; // mdb added 3/1/07 #100
	
	private Mapper mapper;
	private MarkerTrack mt;
	private Sequence st;
	private PseudoMarkerHit[] markerHits;
	private PseudoBESHit[]    besHits;

	public FPCPseudoHits(int project1, int contig, int project2, int group) {
		super(project1,contig,project2,group,Mapper.FPC2PSEUDO,false);		
	}

	public FPCPseudoHits(Mapper mapper, MarkerTrack mt, Sequence st, FPCPseudoData data) {
		this(data.getProject1(),data.getContig(),data.getProject2(),data.getGroup());
		this.mapper = mapper;
		this.mt = mt;
		this.st = st;
		setHits(data);
	}

	public FPCPseudoData.PseudoMarkerData[] getPseudoMarkerHitData() {
		FPCPseudoData.PseudoMarkerData[] mhd = new FPCPseudoData.PseudoMarkerData[markerHits == null ? 0 : markerHits.length];
		for (int i = 0; i < mhd.length; i++) mhd[i] = markerHits[i].data;
		return mhd;
	}

	public FPCPseudoData.PseudoBESData[] getPseudoBESHitData() {
		FPCPseudoData.PseudoBESData[] bhd = new FPCPseudoData.PseudoBESData[besHits == null ? 0 : besHits.length];
		for (int i = 0; i < bhd.length; i++) bhd[i] = besHits[i].data;
		return bhd;
	}

	public boolean setHits(FPCPseudoData data) {
		if (upgradeHitContent(data.getHitContent())) {

			FPCPseudoData.PseudoMarkerData[] mhd = data.getPseudoMarkerData();
			FPCPseudoData.PseudoBESData[] bhd = data.getPseudoBESData();

			markerHits = new PseudoMarkerHit[data == null || mhd == null ? 0 : mhd.length];
			for (int i = 0; i < markerHits.length; i++) {
				markerHits[i] = new PseudoMarkerHit(mhd[i]);
				markerHits[i].set();
			}
			besHits = new PseudoBESHit[data == null || bhd == null ? 0 : bhd.length];
			for (int i = 0; i < besHits.length; i++) {
				besHits[i] = new PseudoBESHit(bhd[i]);
				besHits[i].set();
			}           
			return true;
		}
		return false;
	}

	public boolean addHits(int newHitContent, Collection markerData, Collection besData) {
		if (upgradeHitContent(newHitContent)) {
			if (markerHits == null) markerHits = new PseudoMarkerHit[0];
			if (besHits == null) besHits = new PseudoBESHit[0];
			if (markerData == null) markerData = new LinkedList();
			if (besData == null) besData = new LinkedList();

			PseudoMarkerHit tmh[] = new PseudoMarkerHit[markerHits.length+markerData.size()];
			System.arraycopy(markerHits,0,tmh,0,markerHits.length);
			int i = markerHits.length;
			markerHits = tmh;
			for (Iterator iter = markerData.iterator(); i < markerHits.length; i++) {
				markerHits[i] = new PseudoMarkerHit((FPCPseudoData.PseudoMarkerData)iter.next());
				markerHits[i].set();
			}

			PseudoBESHit tbh[] = new PseudoBESHit[besHits.length+besData.size()];
			System.arraycopy(besHits,0,tbh,0,besHits.length);
			i = besHits.length;
			besHits = tbh;
			for (Iterator iter = besData.iterator(); i < besHits.length; i++) {
				besHits[i] = new PseudoBESHit((FPCPseudoData.PseudoBESData)iter.next());
				besHits[i].set();
			}
			return true;
		}
		return false;
	}

	public void clear() {
		for (int i = 0; i < markerHits.length; i++) markerHits[i].clear();
		for (int i = 0; i < besHits.length; i++) besHits[i].clear();	
	}

	public void set(MarkerTrack mt, Sequence st) {
		this.st = st;
		this.mt = mt;
		for (int i = 0; i < markerHits.length; i++) markerHits[i].set();
		for (int i = 0; i < besHits.length; i++) besHits[i].set();
	}

	public void setMinMax(HitFilter hf) {
		for (int i = 0; i < markerHits.length; i++) markerHits[i].setMinMax(hf);
		for (int i = 0; i < besHits.length; i++) besHits[i].setMinMax(hf);
	}

	public void getSequenceMinMax(int[] minMax) {
		for (int i = 0; i < markerHits.length; i++)
			markerHits[i].getSequenceMinMax(minMax);
		for (int i = 0; i < besHits.length; i++)
			besHits[i].getSequenceMinMax(minMax);
	}

	/**
	 * Method <code>getHitData</code> adds an FPCPseudoData object to 
	 * hits containing all the marker hit data and bes hit data for the
	 * hits that are visible, not filtered, and fall in the range of start and
	 * end. If no hits exist than an FPCPseudoData object is not added.
	 *
	 * @param hits a <code>List</code> value
	 * @param start an <code>int</code> value
	 * @param end an <code>int</code> value
	 */
	 public void getHitsInRange(List<AbstractHitData> hits, int start, int end, 
			 boolean swap) // mdb added swap 12/19/08 - for pseudo-pseudo, unused here
	 {
		 List<AbstractHitData> mh = new LinkedList<AbstractHitData>();
		 List<AbstractHitData> bh = new LinkedList<AbstractHitData>();
		 
		 for (int i = 0; i < markerHits.length; i++)
			 markerHits[i].addHit(mh,start,end);
		 for (int i = 0; i < besHits.length; i++)
			 besHits[i].addHit(bh,start,end);
		 if (!mh.isEmpty() || !bh.isEmpty()) 
			 hits.add(new FPCPseudoData(getProject1(),getContig(),getProject2(),getGroup(),getHitContent(),mh,bh));
	 }

	 public void paintComponent(Graphics2D g2) {
		 int i;
		 Point mtLocation = mt.getLocation();
		 Point stLocation = st.getLocation();
		 int mtOrient = mapper.getOrientation(mt);
		 int stOrient = mapper.getOrientation(st);

		 List<PseudoMarkerHit> hMrkHits = new LinkedList<PseudoMarkerHit>();
		 List<PseudoBESHit> hBesHits = new LinkedList<PseudoBESHit>();
		 boolean showJoinDot = mapper.getHitFilter().getShowJoinDot();

		 for (i = 0; i < markerHits.length; i++) {
			 if (markerHits[i].isHighlighted()) hMrkHits.add(markerHits[i]);
			 else markerHits[i].paintComponent(g2,mtOrient,stOrient,mtLocation,stLocation,showJoinDot);
		 }
		 for (i = 0; i < besHits.length; i++) {
			 if (besHits[i].isHighlighted()) hBesHits.add(besHits[i]);
			 else besHits[i].paintComponent(g2,mtOrient,stOrient,mtLocation,stLocation);
		 }

		 for (PseudoMarkerHit h : hMrkHits)
			 h.paintComponent(g2,mtOrient,stOrient,mtLocation,stLocation,showJoinDot);
		 for (PseudoBESHit h : hBesHits)
			 h.paintComponent(g2,mtOrient,stOrient,mtLocation,stLocation);
	 }

	 public void mouseMoved(MouseEvent e) {
		 for (int i = 0; i < markerHits.length; i++)
			 markerHits[i].mouseMoved(e);
		 for (int i = 0; i < besHits.length; i++)
			 besHits[i].mouseMoved(e);
	 }
	 
	 public void mouseExited(MouseEvent e) { 
		 // Need to do this otherwise hits will stay highlighted when exited.
		 for (int i = 0; i < markerHits.length; i++)
			 markerHits[i].setHover(false);
		 for (int i = 0; i < besHits.length; i++)
			 besHits[i].setHover(false);
		 
		 mapper.getDrawingPanel().repaint();
	 }

	 private class PseudoMarkerHit implements Hit {
		 private FPCPseudoData.PseudoMarkerData data;
		 private MarkerHit mh;
		 private Line2D.Double hitLine; 
		 private boolean hover;			
		 private boolean geneContained; 
		 private boolean geneOverlap; 	

		 public PseudoMarkerHit(FPCPseudoData.PseudoMarkerData data) {
			 this.data = data;
			 mh = new MarkerHit();
			 hitLine = new Line2D.Double(); 
			 hover = false; 	
			 geneOverlap = (data.getOverlap() > 0);
		 }

		 public void addHit(List hits, int start, int end) {
			 if ( ( (data.getStart2() >= start && data.getStart2() <= end) ||
					 (data.getEnd2() >= start && data.getEnd2() <= end) ||
					 (start >= data.getStart2() && end <= data.getEnd2()) ) &&
					 isVisible() && !isFiltered() ) {
				 hits.add(data);
			 }
		 }

		 public void setMinMax(HitFilter hf) {
			 hf.condSetMrkEvalue(data.getEvalue(),data.getEvalue());
			 hf.condSetMrkPctid(data.getPctid(),data.getPctid());
		 }

		 public String getType() {
			 return MRK_TYPE;
		 }

		 public void set() {
			 mh.set(this,mt,data.getName(),getContig());
			 
			 geneContained = isGeneContained(); // mdb added 3/9/07 #101
			 geneOverlap = isGeneOverlap(); 	// mdb added 3/9/07 #101
		 }

		 public boolean isBlockHit() {
			return data.isBlockHit();
		 }
		 
		 public boolean isGeneContained() { 

			 
			 return (data.getOverlap() > 0); // WN we don't make this distinction anymore
		 }
		 
		 public boolean isGeneOverlap() { 

			 
			 return (data.getOverlap() > 0); 
		 }

		public boolean isFiltered() {
			HitFilter hitfilter = mapper.getHitFilter();
			return (hitfilter.getBlock() && !data.isBlockHit())
					|| (hitfilter.getNonRepetitive() && data.isRepetitiveHit() 
							&& !data.isBlockHit()) || hitfilter.getMrkHide()
					|| hitfilter.getMrkEvalue() < data.getEvalue()
					|| hitfilter.getMrkPctid() > data.getPctid()
					|| mh.isFiltered(mt, hitfilter.getOnlyShared())
					|| isSequenceFiltered(data.getStart2(), data.getEnd2())
			 		|| (hitfilter.getGeneContained() && !geneContained) // mdb added 3/9/07 #101
			 		|| (hitfilter.getGeneOverlap() && !geneOverlap) // mdb added 3/9/07 #101
			 		|| (hitfilter.getNonGene() && (geneOverlap || geneContained)); // mdb added 3/9/07 #101
		}

		public boolean isHighlighted() {
			return mh.isHighlighted() || hover;
		}

		public boolean isVisible() {
			return mh.isVisible(mt)
					&& isSequenceVisible(data.getStart2(), data.getEnd2());
		}

		 private boolean lineContains(Line2D.Double line, Point p) { // mdb added 3/1/07 #100
			 double lx1 = line.getX1();
			 double lx2 = line.getX2();
			 double ly1 = line.getY1();
			 double ly2 = line.getY2();
			 double delta;
			 
			 delta = p.getY() - ly1 - ((ly2-ly1)/(lx2-lx1))*(p.getX()-lx1);
			 
			 return (delta >= -MOUSE_PADDING && delta <= MOUSE_PADDING);
		 }
		 
		 public boolean isHover(Point point) { // mdb added 3/1/07 #100
			 return isVisible() && lineContains(hitLine, point);
		 }
		 
		 protected boolean setHover(Point point) { // mdb added 3/1/07 #100
			return setHover(isHover(point));
		 }

		 protected boolean setHover(boolean hover) { // mdb added 3/1/07 #100
			if (hover != this.hover) {
				this.hover = hover;
				return true;
			}
			return false;
		 }
		
		public void getSequenceMinMax(int[] minMax) {
			if (isVisible() && !isFiltered()) {
				FPCPseudoHits.getSequenceMinMax(minMax, data.getStart2(), data
						.getEnd2());
			}
		}
		
		 private Color getCColor() { // mdb added 3/1/07 #100
			 if (mh.isHighlighted() || isHighlighted())
				 return Mapper.markerLineHighlightColor;
			 if (mapper.getHitFilter().getColorByStrand()) 
				 return data.getOrientation() ? Mapper.posOrientLineColor : Mapper.negOrientLineColor;
			 return Mapper.markerLineColor;
		 }
		 
		 // mdb added 8/22/07 #126
		 private void drawHitRibbon(Graphics2D g2, Point2D pStart, Point2D pEnd, int orient) {
			 String target_seq = data.getTargetSeq();
			 Rectangle2D.Double rect = new Rectangle2D.Double();
			 if (target_seq == null || target_seq.equals("")) {
				 g2.draw(new Line2D.Double(pStart,pEnd));
			 }
			 else {
				 g2.setPaint(Mapper.hitRibbonBackgroundColor);
				 rect.setRect(pStart.getX(),pStart.getY(),Mapper.hitRibbonWidth,pEnd.getY()-pStart.getY());
				 g2.fill(rect);
				 g2.draw(rect);
				 g2.setPaint(getCColor());
				 String[] subseq = target_seq.split(",");
				 for (int i = 0;  i < subseq.length;  i++) {
					 String[] pos = subseq[i].split(":");
					 long start = Long.parseLong(pos[0]);
					 long end = Long.parseLong(pos[1]);
					 Point2D p1 = st.getPoint(start, orient);
					 p1.setLocation(pStart.getX(),p1.getY());
					 Point2D p2 = st.getPoint(end, orient);
					 p2.setLocation(pStart.getX(),p2.getY());
					 rect.setRect(p1.getX(),p1.getY(),Mapper.hitRibbonWidth,p2.getY()-p1.getY());
					 g2.fill(rect);
					 g2.draw(rect);
				 }
			 }
		 }
		 
		 public void paintComponent(Graphics2D g2, int mtOrient, int stOrient, Point mtLocation, Point stLocation, boolean showJoinDot) {
			 if (isVisible() && !isFiltered()) {
				 Point2D sp = getSequenceCPoint(data.getStart2(),data.getEnd2(),stOrient,stLocation);
				 Point2D mp = mh.getCPoint(mt,mtOrient,mtLocation,showJoinDot);
				 mh.paintComponent(g2,mt,mtLocation,sp,showJoinDot,mapper.getHitFilter().getColorByStrand(),data.getOrientation());
				 if (mp != null) {
					 //g2.setPaint(mh.getCColor(mapper.getHitFilter().getColorByStrand(),data.getOrientation())); // mdb removed 3/1/07 #100
					 g2.setPaint(getCColor()); // mdb added 3/1/07 #100
					 hitLine.setLine(sp,mp); // mdb added 3/1/07 #100
					 g2.draw(hitLine);
				 }
				 		
				 double lineLength = 0;
				 if (st.getShowScoreLine()) { // mdb added 2/28/07 #100
					 double pctid = data.getPctid();
					 double minPctid = Math.min(mapper.getHitFilter().getMinBesPctid(),
			 									mapper.getHitFilter().getMinMrkPctid());			 
					 double x=sp.getX(), y=sp.getY();
					 
					 lineLength = 30.0*(pctid-minPctid+1)/(100-minPctid+1);		
					 if (mp != null && mp.getX() > x) lineLength = 0 - lineLength;
					 g2.setPaint(getCColor()/*mh.getCColor(mapper.getHitFilter().getColorByStrand(),data.getOrientation())*/); // mdb changed 3/1/07 #100
					 g2.draw(new Line2D.Double(x,y,x+lineLength,y));
				 }
				 if (st.getShowRibbon()) { // mdb added 8/7/07 #126
					 if (lineLength == 0) lineLength = 30;
					 Point2D rp1 = st.getPoint(data.getStart2(), stOrient);
					 rp1.setLocation(sp.getX()+lineLength,rp1.getY());
					 Point2D rp2 = st.getPoint(data.getEnd2(), stOrient);
					 rp2.setLocation(sp.getX()+lineLength,rp2.getY());
					 if (Math.abs(rp2.getY()-rp1.getY()) > 3) // only draw if it will be visible
						 drawHitRibbon(g2, rp1, rp2, stOrient);
				 }
				 if (st.getShowScoreValue() || isHighlighted()) { // mdb added 3/14/07 #100
					 double pctid = data.getPctid();
					 double textX;
					 textX = sp.getX();
					 if (mp != null && mp.getX() > sp.getX()) textX += lineLength-14-(pctid==100 ? -7 : 0);
					 else textX += lineLength;					 
					 g2.setPaint(Color.black);
					 g2.drawString(""+(int)pctid, (int)textX, (int)sp.getY());
				 }
			 }
		 }

		 public void clear() {
			 mh.clear(this);
		 }

		 public void mouseMoved(MouseEvent e) {
			 if (mh.isVisible(mt)) mh.mouseMoved(e);
			 if (setHover(e.getPoint()))
				 mapper.getDrawingPanel().repaint();
		 }
	 }

	 private class PseudoBESHit implements CloneHit {
		 private FPCPseudoData.PseudoBESData data;
		 private BESHit bh;
		 private Line2D.Double hitLine; 
		 private boolean hover;			
		 private boolean geneContained; 
		 private boolean geneOverlap; 	

		 public PseudoBESHit(FPCPseudoData.PseudoBESData data) {
			 this.data = data;
			 bh = new BESHit();
			 hitLine = new Line2D.Double(); 
			 hover = false;		
			 geneOverlap = (data.getOverlap() > 0);
		 }

		 public void addHit(List hits, int start, int end) {
			 if ( ( (data.getStart2() >= start && data.getStart2() <= end) ||
					(data.getEnd2() >= start && data.getEnd2() <= end) ||
					(start >= data.getStart2() && end <= data.getEnd2()) ) &&
					isVisible() && !isFiltered() ) 
			 {
				 hits.add(data);
			 }
		 }

		 public int getPos2() {
			 return data.getPos2();
		 }

		 public void setMinMax(HitFilter hf) {
			 hf.condSetBesEvalue(data.getEvalue(),data.getEvalue());
			 hf.condSetBesPctid(data.getPctid(),data.getPctid());
		 }

		 public void getSequenceMinMax(int[] minMax) {
			 if (isVisible() && !isFiltered()) {
				 FPCPseudoHits.getSequenceMinMax(minMax,data.getStart2(),data.getEnd2());
			 }
		 }

		 public String getType() { return BES_TYPE; }
		 public byte getBES() { return data.getBES(); }
		 public boolean getOrientation() { return data.getOrientation(); }
		 public boolean isBlockHit() { return data.isBlockHit(); }

		 public void set() {
			 bh.set(this,mt,data.getName(),getContig());
			 geneContained = isGeneContained(); // mdb added 3/9/07 #101
			 geneOverlap = isGeneOverlap(); 	// mdb added 3/9/07 #101
		 }
		 
		 public boolean isGeneContained() { // mdb added 3/8/07 #101
			 for (Annotation annot : st.getAnnotations()) {
				 if (annot.isGene() && 
					 data.getStart2() >= annot.getStart() &&
					 data.getEnd2() <= annot.getEnd()) 
				 {
					 return true;
				 }
			 }
			 
			 return false;
		 }
		 
		 public boolean isGeneOverlap() { // mdb added 3/8/07 #101
			 long h1 = data.getStart2();
			 long h2 = data.getEnd2();
			 
			 for (Annotation annot : st.getAnnotations()) {
				 // mdb: this compare could be made more efficient:
				 if (annot.isGene() && 
					 // Is hit start inside gene?
					 ((h1 >= annot.getStart() && h1 <= annot.getEnd()) ||
					 // Is hit end inside gene?
					  (h2 >= annot.getStart() && h2 <= annot.getEnd()) ||
					 // Is gene start inside hit?
					  (annot.getStart() >= h1 && annot.getStart() <= h2) ||					  
					 // Is gene end inside hit? 
					  (annot.getEnd() >= h1 && annot.getEnd() <= h2)) &&
					 // Is hit NOT contained in gene?
					  !(h1 >= annot.getStart() && h2 <= annot.getEnd()))
				 {
					 return true;
				 }
			 }
			 
			 return false;		 
		 }

		 public boolean isFiltered() {
			 HitFilter hitfilter = mapper.getHitFilter();
			 return hitfilter.getBesHide() 
			 		|| hitfilter.getBesEvalue() < data.getEvalue() 
			 		|| hitfilter.getBesPctid() > data.getPctid() 
			 		|| (hitfilter.getBlock() && !data.isBlockHit()) 
			 		|| (hitfilter.getNonRepetitive() && data.isRepetitiveHit() && !data.isBlockHit()) 
			 		|| bh.isFiltered(mt,hitfilter,getContig())
			 		|| isSequenceFiltered(data.getStart2(),data.getEnd2()) 
			 		|| (hitfilter.getGeneContained() && !geneContained) // mdb added 3/9/07 #101
			 		|| (hitfilter.getGeneOverlap() && !geneOverlap) // mdb added 3/9/07 #101
			 		|| (hitfilter.getNonGene() && (geneOverlap || geneContained)); // mdb added 3/9/07 #101
		 }

		 public boolean isHighlighted() {
			 return bh.isHighlighted() || hover;
		 }

		 public boolean isVisible() {
			 return bh.isVisible(mt,getContig()) && isSequenceVisible(data.getStart2(),data.getEnd2());
		 }

		 private boolean lineContains(Line2D.Double line, Point p) { // mdb added 3/1/07 #100
			 double lx1 = line.getX1();
			 double lx2 = line.getX2();
			 double ly1 = line.getY1();
			 double ly2 = line.getY2();
			 double delta;
			 
			 delta = p.getY() - ly1 - ((ly2-ly1)/(lx2-lx1))*(p.getX()-lx1);
			 
			 return (delta >= -MOUSE_PADDING && delta <= MOUSE_PADDING);
		 }
		 
		 public boolean isHover(Point point) { // mdb added 3/1/07 #100
			 return isVisible() && lineContains(hitLine, point);
		 }
		 
		 protected boolean setHover(Point point) { // mdb added 3/1/07 #100
			return setHover(isHover(point));
		 }

		 protected boolean setHover(boolean hover) { // mdb added 3/1/07 #100
			if (hover != this.hover) {
				this.hover = hover;
				return true;
			}
			return false;
		 }
		 
		 private Color getCColor() { // mdb added 3/1/07 #100
			 if (bh.isHighlighted() || isHighlighted())
				 return Mapper.besLineHighlightColor;
			 if (mapper.getHitFilter().getColorByStrand()) 
				 return data.getOrientation() ? Mapper.posOrientLineColor : Mapper.negOrientLineColor;
			 return Mapper.besLineColor;
		 }
		 
		 // mdb added 8/22/07 #126
		 private void drawHitRibbon(Graphics2D g2, Point2D pStart, Point2D pEnd, int orient) {
			 String target_seq = data.getTargetSeq();
			 Rectangle2D.Double rect = new Rectangle2D.Double();
			 if (target_seq == null || target_seq.equals("")) {
				 g2.draw(new Line2D.Double(pStart,pEnd));
			 }
			 else {
				 g2.setPaint(Mapper.hitRibbonBackgroundColor);
				 rect.setRect(pStart.getX(),pStart.getY(),Mapper.hitRibbonWidth,pEnd.getY()-pStart.getY());
				 g2.fill(rect);
				 g2.draw(rect);
				 g2.setPaint(getCColor());
				 String[] subseq = target_seq.split(",");
				 for (int i = 0;  i < subseq.length;  i++) {
					 String[] pos = subseq[i].split(":");
					 long start = Long.parseLong(pos[0]);
					 long end = Long.parseLong(pos[1]);
					 Point2D p1 = st.getPoint(start, orient);
					 p1.setLocation(pStart.getX(),p1.getY());
					 Point2D p2 = st.getPoint(end, orient);
					 p2.setLocation(pStart.getX(),p2.getY());
					 rect.setRect(p1.getX(),p1.getY(),Mapper.hitRibbonWidth,p2.getY()-p1.getY());
					 g2.fill(rect);
					 g2.draw(rect);
				 }
			 }
		 }
		 
		 public void paintComponent(Graphics2D g2, int mtOrient, int stOrient, Point mtLocation, Point stLocation) {
			 if (isVisible() && !isFiltered()) {
				 Point2D sp = getSequenceCPoint(data.getStart2(),data.getEnd2(),stOrient,stLocation);
				 Point2D bp = bh.getCPoint(mt,mtOrient,mtLocation,getContig(),data.getPos(),data.getBES());
				 //g2.setPaint(bh.getCColor(mapper.getHitFilter().getColorByStrand(),data.getOrientation())); // mdb removed 3/1/07 #100
				 g2.setPaint(getCColor()); 						// mdb added 3/1/07 #100
				 hitLine.setLine(sp,bp); 						// mdb added 3/1/07 #100
				 g2.draw(hitLine/*new Line2D.Double(sp,bp)*/); 	// mdb changed 3/1/07 #100				 
				 		
				 int lineLength = 0;
				 if (st.getShowScoreLine()) { // mdb added 2/28/07 #100
					 int pctid = (int)data.getPctid();
					 int minPctid = (int)Math.min(mapper.getHitFilter().getMinBesPctid(),
							 					mapper.getHitFilter().getMinMrkPctid());		 
					 
					 g2.setPaint(getCColor());
					 lineLength = 30*(pctid-minPctid+1)/(100-minPctid+1);
					 if (lineLength > 1) { // mdb added 4/1/09 - don't bother drawing if too small
						 int x=(int)sp.getX(), y=(int)sp.getY();
						 if (bp.getX() > x) lineLength = 0 - lineLength;
						 g2.drawLine(x,y,x+lineLength,y);//g2.draw(new Line2D.Double(x,y,x+lineLength,y)); // mdb changed 4/1/09 - drawLine() is faster than draw()
					 }
				 }
				 if (st.getShowRibbon()) { // mdb added 8/7/07 #126
					 if (lineLength == 0) lineLength = 30;
					 Point2D rp1 = st.getPoint(data.getStart2(), stOrient);
					 Point2D rp2 = st.getPoint(data.getEnd2(), stOrient);
					 if (rp2.getY()-rp1.getY() > 3)  { // only draw if it will be visible
						 rp1.setLocation(sp.getX()+lineLength,rp1.getY());
						 rp2.setLocation(sp.getX()+lineLength,rp2.getY());
						 drawHitRibbon(g2, rp1, rp2, stOrient);
					 }
				 }
				 if (st.getShowScoreValue() || isHighlighted()) { // mdb added 3/14/07 #100
					 double pctid = data.getPctid();
					 double textX;
					 textX = sp.getX();
					 if (bp.getX() > sp.getX()) textX += lineLength-14-(pctid==100 ? -7 : 0);
					 else textX += lineLength;
					 g2.setPaint(Color.black);
					 g2.drawString(""+(int)pctid, (int)textX, (int)sp.getY());
				 }
			 }
		 }

		 public void clear() {
			 bh.clear(this);
		 }

		 public void mouseMoved(MouseEvent e) { // mdb added 3/1/07 #100
			 if (setHover(e.getPoint()))
				 mapper.getDrawingPanel().repaint();
		 }
	 }

	 private boolean isSequenceFiltered(int start, int end) {
		 return !st.isInRange( (start+end)>>1 );
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

	 private boolean isSequenceVisible(int start, int end) {
		 return st.isInRange( (start+end)>>1 );
	 }

	 public Point2D getSequenceCPoint(int start, int end, int orientation, Point loc) {
		 Point2D p = st.getPoint( (start+end)>>1 , orientation );
		 p.setLocation(p.getX()+loc.getX(),p.getY()+loc.getY());
		 return p;
	 }
}
