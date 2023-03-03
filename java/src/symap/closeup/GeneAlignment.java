package symap.closeup;

import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.Vector;

import symap.sequence.Annotation;
import util.ErrorReport;
import util.Utilities;

/***************************************************************
 * Draws the gene graphics for closeup
 * CAS533 removed Arrow from the end of the 1st/last exon, as +/- is in the text
 */
public class GeneAlignment implements Comparable<GeneAlignment> {
	private static final double EXON_HEIGHT = CloseUpComponent.EXON_HEIGHT; // 12
	private static final double INTRON_HEIGHT = CloseUpComponent.INTRON_HEIGHT;// 2;

	private String desc;
	private int gStart, gEnd; // gene start and end
	private boolean isReverse, isForward, bShow=true;
	
	private int eStart, eEnd; // CAS535 only show exons in selected/hit area
	private Exon[] exons;
 
	private int startGr = Integer.MIN_VALUE, endGr = Integer.MAX_VALUE; // start and end of graphics
	private Rectangle2D.Double bounds = new Rectangle2D.Double();
	private ArrowLine geneLine;
	
	public GeneAlignment(String name, int start, int end, boolean forward, Vector<Annotation> e) {
		if (name != null)
			name = name.replaceAll("=http://\\S+;", "   ").replaceAll(";", "   ");
		
		this.desc = name;
		this.gStart = start;
		this.gEnd = end;
		this.isReverse = (start>end); // CAS535 in DB, start<end, so always false
		this.isForward = forward;
				
		exons = new Exon[e == null ? 0 : e.size()];
		for (int i = 0;  i < e.size();  i++) {
			exons[i] = new Exon(e.get(i).getStart(), e.get(i).getEnd(), e.get(i).getTag());
			exons[i] = exons[i].translate(gStart, gEnd);
		}
		Arrays.sort(exons);
	}
	public void setDisplayExons(int sstart, int send) { // selected/hit start and end
	try {
		eStart = Integer.MAX_VALUE;
		eEnd   = Integer.MIN_VALUE;
		for (Exon ex : exons) {
			if (Utilities.isOverlap(sstart, send, ex.start, ex.end)) {
				eStart = Math.min(eStart, ex.start);
				eEnd =   Math.max(eEnd, ex.end);
				ex.bShow = true;
			}
			else ex.bShow = false;
		}
	}
	catch (Exception e) {ErrorReport.print(e, "Set display exons ");}
	}
	public void setNoShow() {bShow=false;}

	// CloseUpComponent: startG, endG, HORZ_BORDER, bpPerPixel, (layers[i] * layerSize + y)
	public void setBounds(int startGr, int endGr, double pixelStart, double bpPerPixel, double y) {
		bounds.x = getX(startGr, pixelStart, bpPerPixel);;
		bounds.y = y;
		bounds.width =  getExonLength(startGr,endGr) / bpPerPixel;
		bounds.height = EXON_HEIGHT;

		Rectangle2D.Double gbounds = new Rectangle2D.Double();
		gbounds.x = getgX(startGr, pixelStart, bpPerPixel);
		gbounds.y = y + (EXON_HEIGHT/2 - 2);
		gbounds.width = getGeneLength(startGr, endGr) / bpPerPixel;
		gbounds.height = INTRON_HEIGHT;
			
		int arrow=ArrowLine.NO_ARROW;
		int sg = getGmin(), se = getEmin();
		int eg = getGmax(), ee = getEmax();
		
		if (sg<se && eg>ee) arrow = ArrowLine.BOTH_ARROWS;
		else if (sg<se) 	arrow = ArrowLine.LEFT_ARROW;
		else if (eg>ee) 	arrow = ArrowLine.RIGHT_ARROW;
		 
		geneLine = new ArrowLine();
		geneLine.setBounds(gbounds, CloseUpComponent.INTRON_HEIGHT, CloseUpComponent.GARROW_DIM, arrow);
	}
	
	public void paint(Graphics2D g2, FontMetrics fm, Color fontColor, double vertFontSpace) {
		if (!bShow) return;
		
		if (!paintGene(g2, fm, fontColor, vertFontSpace)) return; 
		
		// paint name
		if (desc == null || desc.length() == 0) return;
		
		String s = desc;
		if (desc.length() > 100) s = desc.substring(0,100) + "...";
		
		double x = bounds.x + (bounds.width - fm.stringWidth(s)) / 2.0;
		double y = bounds.y - vertFontSpace;
		g2.setPaint(fontColor);
		g2.setFont(fm.getFont());
		g2.drawString(s, (float)x, (float)y);
	}

	private boolean paintGene(Graphics2D g2, FontMetrics fm, Color fontColor, double vertFontSpace) {
		g2.setPaint(CloseUpComponent.intronColor);
		g2.draw(geneLine);
		g2.fill(geneLine);
		
		double bpPerPixel = getExonLength(startGr,endGr)/bounds.width;
		int startBP       = getStartExon(startGr);
		int endBP         = getEndExon(endGr);
		double endX       = bounds.x + bounds.width;

		if (gEnd < startBP) return false;
		
		ExonBox exon = new ExonBox();
		Rectangle2D.Double exonBounds = new Rectangle2D.Double();

		exonBounds.x      = bounds.x;
		exonBounds.width  = bounds.width;
		exonBounds.y      = bounds.y + (bounds.height - INTRON_HEIGHT) / 2.0;
		exonBounds.height = INTRON_HEIGHT;

		if (exons.length > 0) {
			if (exons[0].getStart() <= startBP) {
				exonBounds.x = bounds.x + exons[0].getEnd(startBP,endBP,bpPerPixel);
				exonBounds.width = endX - exonBounds.x;
			}
			if (exons[exons.length-1].getEnd() >= endBP) {
				exonBounds.width = bounds.x+exons[exons.length-1].getStart(startBP,bpPerPixel) - exonBounds.x;
			}
		}
		if (exonBounds.x < bounds.x) {
			exonBounds.width = exonBounds.width - (bounds.x - exonBounds.x);
			exonBounds.x = bounds.x;
		}
		if (exonBounds.x + exonBounds.width > endX) {
			exonBounds.width = endX - exonBounds.x;
		}
		/* draw gene length as black line CAS535 replace with ArrowLine
		if (exonBounds.width > 0) {g2.setPaint(CloseUpComponent.intronColor);g2.draw(exonBounds);g2.fill(exonBounds);}
		*/
		
		// draw all exons over black line
		Color c = (isForward) ? CloseUpComponent.exonColorP : CloseUpComponent.exonColorN; // CAS535
		g2.setPaint(c);
		exonBounds.y         = bounds.y + (bounds.height - EXON_HEIGHT) / 2.0;
		exonBounds.height    = EXON_HEIGHT;

		for (int i = 0; i < exons.length; ++i) {
			exonBounds.x =     bounds.x + exons[i].getStart(startBP, bpPerPixel);
			exonBounds.width = exons[i].getWidth(startBP, endBP, bpPerPixel);
			
			if (exonBounds.width > 0) {
				exon.setBounds(exonBounds);
				g2.draw(exon);
				g2.fill(exon);
			}
		}
		// CAS535 add exon tag
		g2.setPaint(fontColor);
		g2.setFont(fm.getFont());
		double y = bounds.y + EXON_HEIGHT*2;
		
		for (int i = 0; i < exons.length; ++i) {
			if (!exons[i].bShow) continue;
			
			double width = exons[i].getWidth(startBP, endBP, bpPerPixel);
			if (width>0) {
				String tag = exons[i].getTag();
				double x = bounds.x + exons[i].getStart(startBP, bpPerPixel);
				x += (width - fm.stringWidth(tag)) / 2.0;
				
				g2.drawString(tag, (float)x, (float)y);
			}
		}
		
		return true;
	}
	public int compareTo(GeneAlignment ga) {
		return getFirstExon() - ga.getFirstExon();
	}
	public boolean equals(Object obj) {
		if (obj instanceof GeneAlignment) {
			GeneAlignment g = (GeneAlignment)obj;
			return (desc == null || desc.equals(g.desc)) && gStart == g.gStart && gEnd == g.gEnd;
		}
		return false;
	}
	
	public double getWidth(double bpPerPixel, int minStartBP, int maxEndBP) {
		return getExonLength(minStartBP,maxEndBP) / bpPerPixel;
	}

	public double getX(int startG, double pixelStart, double bpPerPixel) {
		return (getStartExon(startG)-startG)/bpPerPixel + pixelStart;
	}
	
	public double getgX(int startGr, double pixelStart, double bpPerPixel) {
		int start = Math.max(!isReverse ? gStart : gEnd,  startGr);
		return (start-startGr)/bpPerPixel + pixelStart;
	}
	public int getGeneLength(int paintStart, int paintEnd) {
		int end =   Math.min(!isReverse ? gEnd   : gStart, paintEnd);
		int start = Math.max(!isReverse ? gStart : gEnd,  paintStart); 
		return Math.max(end-start, 0);
	}
	
	public int getGmin() { return isReverse  ? gEnd : gStart; }
	public int getGmax() { return isReverse  ? gStart : gEnd; }
	public int getEmin() { return isReverse  ? eEnd : eStart; }
	public int getEmax() { return isReverse  ? eStart : eEnd; }
	public String toString() { return "Gene "+desc+" "+gStart+"-"+gEnd; }
	
	private int getStartExon(int paintStart) { 
		return Math.max(!isReverse ? eStart : eEnd,  paintStart); 
	}
	private int getEndExon(int paintEnd)     { 
		return Math.min(!isReverse ? eEnd   : eStart, paintEnd); 
	}
	private int getExonLength(int paintStart, int paintEnd) { 
		return Math.max(getEndExon(paintEnd) - getStartExon(paintStart),0); 
	}
	private int getFirstExon() { return !isReverse ? eStart : eEnd; }
	
	/*******************************************************************
	 * Holds the exon coordinates for the gene drawn in GeneAlignment
	 * CAS533 added tag to display under exon, CAS535 implemented, move Exon from separate class
	 */
	public class Exon implements Comparable<Exon> {
		private int start;
		private int end;
		private String tag;
		private boolean bShow=true;

		public Exon(int start, int end, String tag) {
			if (start > end) {
				int temp = start;
				start = end;
				end = temp;
			}
			
			this.start = start;
			this.end   = end;
			this.tag = tag;
		}

		public int getStart() {return start;}

		public int getEnd() {return end;}
		
		public String getTag() {return tag;}

		public double getStart(int startBP, double bpPerPixel) {
			if (start <= startBP) return 0;
			return (start - startBP)/bpPerPixel;
		}

		public double getWidth(int startBP, int endBP, double bpPerPixel) {
			int x = (end > endBP) ? endBP : end;
			int y = (start < startBP) ? startBP : start;
			return Math.max((x-y)/bpPerPixel, 0);
		}

		public double getEnd(int startBP, int endBP, double bpPerPixel) {
			if (end < startBP) return 0;
			int x = (end > endBP) ? endBP : end;
			return (x - startBP)/bpPerPixel;
		}

		public int compareTo(Exon e) {return start - e.start;}

		public boolean equals(Object obj) {
			if (obj instanceof Exon) {
				Exon e = (Exon)obj;
				return e.start == start && e.end == end;
			}
			return false;
		}

		public String toString() {return tag + " " +start+"-"+end;}

		public Exon translate(int s, int e) {
			if (s <= e) return this;
			start = s-end+e; // CAS533 was creating new Exon object instead of just changing start/end
			end = s-start+e;
			return this;
		}
	}
	/****************************************************************************
	 * ExonBox; 
	 * CAS533 ArrowBox moved from a separate file in Util; then removed arrow 
	 */
	public class ExonBox implements Shape {
		
		private DoublePolygon poly;

		public ExonBox() {poly = new DoublePolygon();}

		public void setBounds(Rectangle2D bounds) {
			poly.reset();
			poly.addPoint(bounds.getX(),bounds.getY());
			poly.addPoint(bounds.getX(),bounds.getY() + bounds.getHeight());
			poly.addPoint(bounds.getX()+bounds.getWidth(), bounds.getY() + bounds.getHeight());
			poly.addPoint(bounds.getX()+bounds.getWidth(), bounds.getY());
		}

		public boolean contains(double x, double y) {return poly.contains(x,y);}
		public boolean contains(double x, double y, double w, double h) {return poly.contains(x,y,w,h);}
		public boolean contains(Point2D p) {return poly.contains(p);}
		public boolean contains(Rectangle2D r) {return poly.contains(r);}
		public Rectangle getBounds() {return poly.getBounds();}
		public Rectangle2D getBounds2D() {return poly.getBounds2D();}
		public PathIterator getPathIterator(AffineTransform at) {return poly.getPathIterator(at);}
		public PathIterator getPathIterator(AffineTransform at, double flatness) {return poly.getPathIterator(at,flatness);}
		public boolean intersects(double x, double y, double w, double h) {return poly.intersects(x,y,w,h);}
		public boolean intersects(Rectangle2D r) {return poly.intersects(r);}

		private class DoublePolygon extends Polygon {// CAS533 was static
			public DoublePolygon() {super();}
			public void addPoint(double x, double y) {
				super.addPoint((int)Math.round(x),(int)Math.round(y));
			}
		}
	}
}
