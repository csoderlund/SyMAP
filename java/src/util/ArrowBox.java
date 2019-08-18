package util;

import java.awt.*;
import java.awt.geom.*;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class ArrowBox implements Shape {
	public static final int NONE  = 0;
	public static final int LEFT  = 1;
	public static final int RIGHT = 2;

	private DoublePolygon poly;

	public ArrowBox() {
		poly = new DoublePolygon();
	}

	public ArrowBox(Rectangle2D bounds, double arrowWidth, int direction) {
		this();
		setBounds(bounds,arrowWidth,direction);
	}

	public void setBounds(Rectangle2D bounds, double arrowWidth, int direction) {
		poly.reset();

		if (direction == NONE || arrowWidth <= 0) {
			poly.addPoint(bounds.getX(),bounds.getY());
			poly.addPoint(bounds.getX(),bounds.getY()+bounds.getHeight());
			poly.addPoint(bounds.getX()+bounds.getWidth(),bounds.getY()+bounds.getHeight());
			poly.addPoint(bounds.getX()+bounds.getWidth(),bounds.getY());
			return ;
		}
		if (arrowWidth > bounds.getWidth()) {
			bounds = new Rectangle2D.Double(bounds.getX(),bounds.getY(),bounds.getWidth(),bounds.getHeight());
			((Rectangle2D.Double)bounds).height = bounds.getHeight()/arrowWidth * bounds.getWidth();
			arrowWidth = bounds.getWidth();
		}

		if (direction == RIGHT) {
			poly.addPoint(bounds.getX(),bounds.getY());
			poly.addPoint(bounds.getX(),bounds.getY()+bounds.getHeight());
			if (arrowWidth < bounds.getWidth())
				poly.addPoint(bounds.getX()+bounds.getWidth()-arrowWidth,bounds.getY()+bounds.getHeight());
			poly.addPoint(bounds.getX()+bounds.getWidth(),bounds.getY()+(bounds.getHeight()/2.0));
			if (arrowWidth < bounds.getWidth())
				poly.addPoint(bounds.getX()+bounds.getWidth()-arrowWidth,bounds.getY());
		}
		else {
			poly.addPoint(bounds.getX()+bounds.getWidth(),bounds.getY());
			poly.addPoint(bounds.getX()+bounds.getWidth(),bounds.getY()+bounds.getHeight());
			if (arrowWidth < bounds.getWidth())
				poly.addPoint(bounds.getX()+arrowWidth,bounds.getY()+bounds.getHeight());
			poly.addPoint(bounds.getX(),bounds.getY()+(bounds.getHeight()/2.0));
			if (arrowWidth < bounds.getWidth())
				poly.addPoint(bounds.getX()+arrowWidth,bounds.getY());
		}
	}

	public boolean contains(double x, double y) {
		return poly.contains(x,y);
	}

	public boolean contains(double x, double y, double w, double h) {
		return poly.contains(x,y,w,h);
	}

	public boolean contains(Point2D p) {
		return poly.contains(p);
	}

	public boolean contains(Rectangle2D r) {
		return poly.contains(r);
	}

	public Rectangle getBounds() {
		return poly.getBounds();
	}

	public Rectangle2D getBounds2D() {
		return poly.getBounds2D();
	}

	public PathIterator getPathIterator(AffineTransform at) {
		return poly.getPathIterator(at);
	}

	public PathIterator getPathIterator(AffineTransform at, double flatness) {
		return poly.getPathIterator(at,flatness);
	}

	public boolean intersects(double x, double y, double w, double h) {
		return poly.intersects(x,y,w,h);
	}

	public boolean intersects(Rectangle2D r) {
		return poly.intersects(r);
	}

	private static class DoublePolygon extends Polygon {
		public DoublePolygon() {
			super();
		}

		public void addPoint(double x, double y) {
			super.addPoint((int)Math.round(x),(int)Math.round(y));
		}
	}
}
