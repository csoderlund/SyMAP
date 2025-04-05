package symap.closeup;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Dimension2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/****************************************************************
 * Hit line and Ruler; CAS533 was a separate file in util
 */
public class ArrowLine implements Shape {
	protected static final int NO_ARROW    = 0, LEFT_ARROW  = 1, RIGHT_ARROW = 2, BOTH_ARROWS = 3;
	private DoublePolygon poly;
	private double lineY, arrowLineWidth;

	protected ArrowLine() {poly = new DoublePolygon();}

	protected void setBounds(Rectangle2D bounds, double lineThickness, Dimension2D arrowDimension, int arrow) {
		if (arrow == NO_ARROW) setBounds(bounds,lineThickness,null,null);
		else                   setBounds(bounds,lineThickness,
				arrow == RIGHT_ARROW ? null : arrowDimension,
				arrow == LEFT_ARROW  ? null : arrowDimension);
	}
	protected void setBounds(Rectangle2D bounds, double lineThickness, Dimension2D lArrowDim, Dimension2D rArrowDim) {
		poly.reset();
		if (bounds.getWidth() <= 0) return ;

		int arrow = NO_ARROW;
		if (lArrowDim != null) arrow += LEFT_ARROW;
		if (rArrowDim != null) arrow += RIGHT_ARROW;

		double halfY = bounds.getY() + bounds.getHeight()/2.0;

		lineY = halfY - lineThickness/2.0;
		arrowLineWidth = 0;

		if (arrow == NO_ARROW) {
			poly.addPoint(bounds.getX(),lineY);
			poly.addPoint(bounds.getX()+bounds.getWidth(),lineY);
			poly.addPoint(bounds.getX()+bounds.getWidth(),lineY+lineThickness);
			poly.addPoint(bounds.getX(),lineY+lineThickness);
			return ;
		}

		double lineRX, lineLX, arrowXR, arrowXL, arrowYL, arrowYR;

		if (rArrowDim == null) rArrowDim = new DoubleDimension(0,lineThickness);
		else                   rArrowDim = new DoubleDimension(rArrowDim.getWidth(),rArrowDim.getHeight());
		if (lArrowDim == null) lArrowDim = new DoubleDimension(0,lineThickness);
		else                   lArrowDim = new DoubleDimension(lArrowDim.getWidth(),lArrowDim.getHeight());

		if (rArrowDim.getWidth() + lArrowDim.getWidth() > bounds.getWidth()) {
			rArrowDim.setSize(bounds.getWidth()/2.0,rArrowDim.getHeight());
			lArrowDim.setSize(rArrowDim.getWidth(), lArrowDim.getHeight());
		}

		final double LA_OFFSET = lArrowDim.getHeight()<lineThickness ? 0 : lineThickness * lArrowDim.getWidth() / lArrowDim.getHeight();
		final double RA_OFFSET = rArrowDim.getHeight()<lineThickness ? 0 : lineThickness * rArrowDim.getWidth() / rArrowDim.getHeight();

		lineRX = bounds.getX()+bounds.getWidth()-RA_OFFSET;
		if (lineRX < bounds.getX()) lineRX = bounds.getX()+bounds.getWidth();

		arrowXR = bounds.getX() + bounds.getWidth() - rArrowDim.getWidth();
		if (arrowXR < bounds.getX()) arrowXR = bounds.getX();

		arrowYL = halfY - lArrowDim.getHeight()/2.0;
		arrowYR = halfY - rArrowDim.getHeight()/2.0;

		lineLX = bounds.getX()+LA_OFFSET;
		if (LA_OFFSET > bounds.getWidth()) lineLX = bounds.getX();

		arrowXL = bounds.getX() + lArrowDim.getWidth();
		if (lArrowDim.getWidth() > bounds.getWidth()) arrowXL = bounds.getX() + bounds.getWidth();

		if (arrow == RIGHT_ARROW)
			arrowLineWidth = bounds.getX()+bounds.getWidth() - arrowXR;
		else
			arrowLineWidth = arrowXL - bounds.getX();
		arrowLineWidth = arrowLineWidth-1;

		if (arrow == RIGHT_ARROW) {
			poly.addPoint(bounds.getX(),lineY);
			poly.addPoint(bounds.getX(),lineY+lineThickness);
			poly.addPoint(lineRX,lineY+lineThickness);
			poly.addPoint(arrowXR,arrowYR+rArrowDim.getHeight());
			poly.addPoint(bounds.getX()+bounds.getWidth(),halfY);
			poly.addPoint(arrowXR,arrowYR);
			poly.addPoint(lineRX,lineY);
		}
		else if (arrow == LEFT_ARROW) {
			poly.addPoint(bounds.getX()+bounds.getWidth(),lineY);
			poly.addPoint(bounds.getX()+bounds.getWidth(),lineY+lineThickness);
			poly.addPoint(lineLX,lineY+lineThickness);
			poly.addPoint(arrowXL,arrowYL+lArrowDim.getHeight());
			poly.addPoint(bounds.getX(),halfY);
			poly.addPoint(arrowXL,arrowYL);
			poly.addPoint(lineLX,lineY);
		}
		else if (arrow == BOTH_ARROWS) {
			poly.addPoint(lineLX,lineY);
			poly.addPoint(lineRX,lineY);
			poly.addPoint(arrowXR,arrowYR);
			poly.addPoint(bounds.getX()+bounds.getWidth(),halfY);
			poly.addPoint(arrowXR,arrowYR+rArrowDim.getHeight());
			poly.addPoint(lineRX,lineY+lineThickness);
			poly.addPoint(lineLX,lineY+lineThickness);
			poly.addPoint(arrowXL,arrowYL+lArrowDim.getHeight());
			poly.addPoint(bounds.getX(),halfY);
			poly.addPoint(arrowXL,arrowYL);
		}
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

	private class DoublePolygon extends Polygon {//CAS533 was static
		public DoublePolygon() {super();}
		public void addPoint(double x, double y) {
			super.addPoint((int)Math.round(x),(int)Math.round(y));
		}
	}
}

