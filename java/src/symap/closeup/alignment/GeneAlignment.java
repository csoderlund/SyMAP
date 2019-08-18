package symap.closeup.alignment;

import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.geom.Rectangle2D;

import java.util.Arrays;

import util.ArrowBox;
import symap.closeup.components.CloseUpComponent;

public class GeneAlignment implements Comparable<GeneAlignment> {
	private String name;
	private int start;
	private int end;
	private Exon[] exons;

	private int paintStart = Integer.MIN_VALUE, paintEnd = Integer.MAX_VALUE;
	private Rectangle2D.Double bounds = new Rectangle2D.Double();
	private boolean forward;

	public GeneAlignment(String name, int start, int end, boolean forward) {
		// mdb added 12/11/09 #166 - remove URLs and semicolons
		if (name != null)
			name = name.replaceAll("=http://\\S+;", "   ").replaceAll(";", "   ");
		
		this.name = name;
		this.start = start;
		this.end = end;
		this.forward = forward;
	}

	public GeneAlignment(String name, int start, int end, boolean forward, Exon[] exons) {
		this(name,start,end, forward);
		setExons(exons);
	}

	public void setExons(Exon[] e) {
		exons = new Exon[e == null ? 0 : e.length];
		for (int i = 0; i < exons.length; ++i)
			exons[i] = e[i].translate(start,end);
		Arrays.sort(exons);
	}

	public int getStart() { return start; }
	public int getEnd() { return end; }

	public double getWidth(double bpPerPixel, int minStartBP, int maxEndBP) {
		return getLength(minStartBP,maxEndBP) / bpPerPixel;
	}

	public double getX(int startBP, double pixelStart, double bpPerPixel, int minStartBP) {
		return (getStartBP(minStartBP)-startBP)/bpPerPixel + pixelStart;
	}

	public void setBounds(int startBP, double pixelStart, double bpPerPixel, double y, int minStartBP, int maxEndBP) {
		setBounds(getX(startBP,pixelStart,bpPerPixel,minStartBP),y,bpPerPixel,minStartBP,maxEndBP);
	}

	public void setBounds(double x, double y, double bpPerPixel, int minStartBP, int maxEndBP) {
		paintStart = minStartBP;
		paintEnd = maxEndBP;

		bounds.x = x;
		bounds.y = y;
		bounds.width = getLength(paintStart,paintEnd) / bpPerPixel;
		bounds.height = Math.max(CloseUpComponent.EXON_HEIGHT,CloseUpComponent.INTRON_HEIGHT);
	}

	public Rectangle2D getBounds() { return bounds; }

	public void paint(Graphics2D g2, FontMetrics fm, Color fontColor, double vertFontSpace) {
		if (!paint(g2)) return; // ASD modified: if nothing painted, don't paint name
		if (name != null && name.length() > 0) paintName(g2,fm,fontColor,vertFontSpace);
	}

	public void paintName(Graphics2D g2, FontMetrics fm, Color fontColor, double vertSpace) {
		// mdb added 8/27/09 - constrain really long gene descriptions
		String s = name;
		if (name.length() > 100)
			s = name.substring(0,100) + "...";
		
		double x = bounds.x + (bounds.width - fm.stringWidth(s)) / 2.0;
		double y = bounds.y - vertSpace;
		g2.setPaint(fontColor);
		g2.setFont(fm.getFont());
		g2.drawString(s,(float)x,(float)y);
	}

	public int compareTo(GeneAlignment ga) {
		return getFirst() - ga.getFirst();
	}

	public boolean paint(Graphics2D g2) {
		if (exons == null) return false;

		double bpPerPixel = getLength(paintStart,paintEnd)/bounds.width;
		int startBP       = getStartBP(paintStart);
		int endBP         = getEndBP(paintEnd);
		double endX       = bounds.x+bounds.width;

		if (end < startBP) return false;

		ArrowBox exon = new ArrowBox();
		Rectangle2D.Double exonBounds = new Rectangle2D.Double();

		exonBounds.x      = bounds.x;
		exonBounds.width  = bounds.width;
		exonBounds.y      = bounds.y + (bounds.height - CloseUpComponent.INTRON_HEIGHT) / 2.0;
		exonBounds.height = CloseUpComponent.INTRON_HEIGHT;

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
		if (exonBounds.width > 0) {
			g2.setPaint(CloseUpComponent.intronColor);
			g2.draw(exonBounds);
			g2.fill(exonBounds);	
		}

		g2.setPaint(CloseUpComponent.exonColor);
		exonBounds.y         = bounds.y + (bounds.height - CloseUpComponent.EXON_HEIGHT) / 2.0;
		exonBounds.height    = CloseUpComponent.EXON_HEIGHT;

		double arrowWidth = CloseUpComponent.EXON_ARROW_WIDTH;
		for (int i = 0; i < exons.length; ++i) {
			exonBounds.x = bounds.x + exons[i].getStart(startBP,bpPerPixel);
			exonBounds.width = exons[i].getWidth(startBP,endBP,bpPerPixel);
			if (exonBounds.width > 0) {
				if (i == 0 || i+1 == exons.length) {
					if (exons.length == 1) exon.setBounds(exonBounds,arrowWidth,forward ? ArrowBox.RIGHT : ArrowBox.LEFT);
					else if (i == 0)       exon.setBounds(exonBounds,arrowWidth,forward ? ArrowBox.NONE  : ArrowBox.LEFT);
					else                   exon.setBounds(exonBounds,arrowWidth,forward ? ArrowBox.RIGHT : ArrowBox.NONE);
				}
				else                       exon.setBounds(exonBounds,arrowWidth,ArrowBox.NONE);
				g2.draw(exon);
				g2.fill(exon);
			}
		}
		return true;
	}


	public boolean equals(Object obj) {
		if (obj instanceof GeneAlignment) {
			GeneAlignment g = (GeneAlignment)obj;
			return /*group == g.group &&*/ (name == null || name.equals(g.name)) && start == g.start && end == g.end;
		}
		return false;
	}

	public String toString() { return "Gene "+name+" "+start+"-"+end; }
	private int getStartBP(int paintStart) { return Math.max(start < end ? start : end,paintStart); }
	private int getEndBP(int paintEnd) { return Math.min(start < end ? end : start,paintEnd); }
	public int getMin() { return start < end ? start : end; }
	public int getMax() { return start > end ? start : end; }
	private int getLength(int paintStart, int paintEnd) { return Math.max(getEndBP(paintEnd) - getStartBP(paintStart),0); }
	private int getFirst() { return start < end ? start : end; }
}
