package util;

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.Font;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.geom.Dimension2D;
import javax.swing.SwingUtilities;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class Ruler {
	private static final double TICK_TEXT_OFFSET = 5;

	//public static final int HORIZONTAL = 0;
	//public static final int VERTICAL   = 1;

	private Dimension2D rDim, lDim, tDim;
	private double tickSpace;
	//private int orientation;
	private double lineThickness;
	private int start, end;
	private boolean tickText;

	private ArrowLine arrowLine = new ArrowLine();
	private Rectangle2D.Double bounds = new Rectangle2D.Double();
	private double[] points = new double[0];

	//public Ruler() { }

	public Ruler(Dimension2D lArrowDim, Dimension2D rArrowDim, Dimension2D tickDim,
			boolean showTickText, double tickSpace, double lineThickness) { //, int orientation) {
		setRuler(0,0,lArrowDim,rArrowDim,tickDim,showTickText,tickSpace,lineThickness);//,orientation);
	}

	public void paint(Graphics2D g2, Color rulerColor, Color fontColor, Font font) {
		if (rulerColor != null) g2.setPaint(rulerColor);
		g2.draw(arrowLine);
		g2.fill(arrowLine);

		if (points.length > 0) {
			if (tDim != null) {
				Rectangle2D.Double tick = new Rectangle2D.Double();
				tick.width = tDim.getWidth();
				tick.height = tDim.getHeight();
				tick.y = arrowLine.getBounds().getY() + (arrowLine.getBounds().getHeight()-tick.height) / 2.0;
				double tickOffset = tick.width/2.0;
				for (int i = 0; i < points.length; ++i) {
					tick.x = points[i] - tickOffset;
					g2.draw(tick);
					g2.fill(tick);
				}
			}
			if (tickText) {
				AffineTransform saveAt = g2.getTransform();
				g2.setPaint(fontColor);
				g2.setFont(font);
				double unitPerPixel = getUnitPerPixel();
				FontMetrics fm = g2.getFontMetrics(font);
				float fontOffset = (fm.getAscent()+fm.getDescent())/2f;
				float y = (float)(arrowLine.getBounds().getY() + arrowLine.getBounds().getHeight() / 2.0 - 
						(tDim == null ? 0 : tDim.getHeight()/2.0) - TICK_TEXT_OFFSET);
				for (int i = 0; i < points.length; ++i) {
					g2.rotate(-Math.PI/4.0,(float)points[i],y);
					g2.drawString(new Integer(getUnit(points[i],unitPerPixel)).toString(),(float)points[i]-fontOffset,y);
					g2.setTransform(saveAt);
				}
			}
		}
	}

	private int getUnit(double point, double unitPerPixel) {
		if (start < end)
			return (int)Math.round((point - bounds.x) * unitPerPixel) + start;
		else
			return end - (int)Math.round((point - bounds.x) * unitPerPixel);
	}

	private double[] getPoints(FontMetrics fm) {
		List<Double> points = new ArrayList<Double>();
		if ((tickText && tickSpace > 0) || (tickSpace > 0 && tDim != null && tDim.getWidth() > 0 && tDim.getWidth() > lineThickness)) {
			double x = bounds.x + bounds.getWidth()/2.0;
			double m = bounds.x + fm.getAscent() + fm.getDescent();
			while (x >= m) {
				points.add(new Double(x));
				x -= tickSpace;
			}
			m = bounds.x + bounds.getWidth() - fm.getAscent() - fm.getDescent();
			x = bounds.x + bounds.getWidth()/2.0 + tickSpace;
			while (x <= m) {
				points.add(new Double(x));
				x += tickSpace;
			}
		}
		double[] ret = new double[points.size()];
		for (int i = 0; i < ret.length; ++i)
			ret[i] = (points.get(i)).doubleValue();
		Arrays.sort(ret);
		return ret;
	}

	public void setBounds(int start, int end, double x, double y, 
			double unitsPerPixel, FontMetrics tickFontMetrics) 
	{
		if (tickText && tickFontMetrics == null)
			throw new IllegalArgumentException(
					"FontMetrics required in set bounds for a Ruler showing tick text.");

		this.start = start; 
		this.end = end;

		bounds.x = x;
		bounds.y = y;

		bounds.width = Math.abs(start-end)/unitsPerPixel;
		bounds.height = getHeight(Math.max(start,end),tickFontMetrics);
		
		// mdb added 12/29/08 - ensure minimum size
		if (bounds.width < 300)
			bounds.width = 300;

		Rectangle2D.Double lineRect = new Rectangle2D.Double();
		lineRect.x      = bounds.x;
		lineRect.width  = bounds.width;
		lineRect.height = getLineHeight();
		lineRect.y      = bounds.y + bounds.height - lineRect.height;

		arrowLine.setBounds(lineRect,lineThickness,lDim,rDim);

		points = getPoints(tickFontMetrics);
	}

	public Rectangle2D getBounds() {
		return bounds;
	}

	//public Dimension2D getLeftArrowDimension() {  return lDim; }
	//public Dimension2D getRightArrowDimension() { return rDim; }
	//public Dimension2D getTickDimension2D() {     return tDim; }
	//public boolean showTickText() {               return tickText; }
	//public double getTickSpace() {                return tickSpace; }
	//public double getLineThickness() {            return lineThickness; }
	//public int getOrientation() {                 return orientation; }
	//public int getStart() {                       return start; }
	//public int getEnd() {                         return end; }

	protected void setRuler(int start, int end, Dimension2D lDim, Dimension2D rDim, Dimension2D tDim,
			boolean tickText, double tickSpace, double lineThickness) { //, int orientation) {
		this.start = start;
		this.end = end;
		this.lDim = lDim;
		this.rDim = rDim;
		this.tDim = tDim;
		this.tickText = tickText;
		this.tickSpace = tickSpace;
		this.lineThickness = lineThickness;
		//this.orientation = orientation;
	}

	//protected void setLeftArrowDimension(Dimension2D dim) 	{ lDim = dim; }
	//protected void setRightArrowDimension(Dimension2D dim) 	{ rDim = dim; }
	//protected void setTickDimension(Dimension2D dim) 		{ tDim = dim; }
	//protected void setTickText(boolean show) 				{ tickText = show; }
	//protected void setTickSpace(double space) 				{ tickSpace = space; }
	//protected void setOrientation(int orient) { orientation = orient;
	//protected void setLineThickness(double thickness) { lineThickness = thickness; }
	//protected void setStart(int s) 	{ start = s; }
	//protected void setEnd(int e) 	{ end = e; }

	private double getHeight(int maxNumber, FontMetrics fm) {
		double maxLineHeight = getLineHeight();
		double maxStringWidth = getMaxStringWidth(maxNumber,fm);
		if (!tickText) return maxLineHeight;
		return Math.max(maxLineHeight,maxStringWidth+
				TICK_TEXT_OFFSET+(tDim == null ? 0 : (tDim.getHeight()+maxLineHeight)/2.0));
	}

	private double getMaxStringWidth(int maxNumber, FontMetrics fm) {
		if (!tickText) return 0;
		int max = 0;
		int stop = Math.max(0,maxNumber - 100);
		for (; maxNumber >= stop; --maxNumber)
			max = Math.max(SwingUtilities.computeStringWidth(fm,
					new Integer(maxNumber).toString()),max);
		return max;
	}

	private double getLineHeight() {
		double max = lineThickness;
		if (lDim != null) max = Math.max(max,lDim.getHeight());
		if (rDim != null) max = Math.max(max,rDim.getHeight());
		if (tDim != null) max = Math.max(max,tDim.getHeight());
		return max;
	}

	private double getUnitPerPixel() {
		return Math.abs(end-start)/bounds.width;
	}
}
