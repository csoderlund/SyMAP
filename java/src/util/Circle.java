package util;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.Component;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import javax.swing.Icon;

/**
 * Class <code>Circle</code> holds a circle shape in double precision (Backed by Ellepse2D.Double). The Circle shape also
 * keeps track of if it's location has been set (if not it will not paint itself on paintIcon())
 * and an immutable center point that can be acquired without any heap allocation.
 *
 * Location is set either through the corripsonding constructor or setLocation()
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 * @see Shape
 * @see Icon
 * @see Cloneable
 * @see Ellipse2D
 */
public class Circle implements Shape, Icon, Cloneable {
	private Ellipse2D.Double circle;
	private StaticPoint2D center;
	private boolean hasSet;

	private Circle() {
		circle = new Ellipse2D.Double();
		center = new StaticPoint2D();
		hasSet = false;
	}

	/**
	 * Creates a new <code>Circle</code> instance with a radius.
	 *
	 * @param radius a <code>double</code> value
	 */
	public Circle(double radius) {
		this();
		setRadius(radius);
	}

	public int getIconWidth() {
		return (int)circle.width;
	}

	public int getIconHeight() {
		return (int)circle.height;
	}

	/**
	 * Creates a new <code>Circle</code> instance.
	 *
	 * @param x a <code>double</code> value of the center x point
	 * @param y a <code>double</code> value of the center y point
	 * @param radius a <code>double</code> value of the radius of the circle
	 */
	public Circle(double x, double y, double radius) {
		this();
		setCircle(x,y,radius);
	}

	/**
	 * Method <code>offset</code> offset the circle by offsetX along the x axis and offsetY along the y axis
	 *
	 * @param offsetX a <code>double</code> value
	 * @param offsetY a <code>double</code> value
	 */
	public void offset(double offsetX, double offsetY) {
		circle.x += offsetX;
		circle.y += offsetY;
		center.x += offsetX;
		center.y += offsetY;
	}

	/**
	 * Method <code>contains</code> tests if the coordinates are with the circle
	 *
	 * @param x a <code>double</code> value
	 * @param y a <code>double</code> value
	 * @return a <code>boolean</code> value
	 */
	public boolean contains(double x, double y) {
		return circle.contains(x,y);
	}

	/**
	 * Method <code>contains</code>
	 * Tests if the interior of the Circle entirely contains the specified rectangular area.
	 * All coordinates that lie inside the rectangular area must lie within the Circle for the 
	 * entire rectanglar area to be considered contained within the Circle.
	 *
	 * @param x a <code>double</code> value
	 * @param y a <code>double</code> value
	 * @param w a <code>double</code> value
	 * @param h a <code>double</code> value
	 * @return a <code>boolean</code> value
	 */
	public boolean contains(double x, double y, double w, double h) {
		return circle.contains(x,y,w,h);
	}

	/**
	 * Method <code>contains</code>
	 *
	 * @param p a <code>Point2D</code> value of the point
	 * @return a <code>boolean</code> value of true if the circle contains p
	 */
	public boolean contains(Point2D p) {
		return circle.contains(p);
	}

	public boolean contains(Rectangle2D r) {
		return circle.contains(r);
	}

	public Rectangle getBounds() {
		return circle.getBounds();
	}

	public Rectangle2D getBounds2D() {
		return circle.getBounds2D();
	}

	public PathIterator getPathIterator(AffineTransform at) {
		return circle.getPathIterator(at);
	}

	public PathIterator getPathIterator(AffineTransform at, double flatness) {
		return circle.getPathIterator(at,flatness);
	}

	public boolean intersects(double x, double y, double w, double h) {
		return circle.intersects(x,y,w,h);
	}

	public boolean intersects(Rectangle2D r) {
		return circle.intersects(r);
	}

	/**
	 * Method <code>getCenter</code> returns the center point of the circle.  The point can not be changed.
	 *
	 * @return an immutable <code>Point2D</code> value of the center of the circle.
	 */
	public Point2D getCenter() {
		return center;
	}

	/**
	 * Method <code>setCircle</code> sets the circles location and radius.
	 *
	 * @param x a <code>double</code> value
	 * @param y a <code>double</code> value
	 * @param radius a <code>double</code> value
	 */
	public void setCircle(double x, double y, double radius) {
		setLocation(x,y);
		setRadius(radius);
	}

	/**
	 * Method <code>setLocation</code> sets the center point of the circle
	 *
	 * @param x a <code>double</code> value of the x value
	 * @param y a <code>double</code> value of the y value
	 */
	public void setLocation(double x, double y) {
		double radius = circle.height / 2.0;
		center.x = x;
		center.y = y;
		circle.x = x - radius;
		circle.y = y - radius;
		hasSet = true;
	}

	public void setLocation(Point2D p) {
		double radius = circle.height / 2.0;
		center.x = p.getX();
		center.y = p.getY();
		circle.x = center.x - radius;
		circle.y = center.y - radius;
		hasSet = true;
	}	

	/**
	 * Method <code>setRadius</code> sets the circle's radius.
	 *
	 * @param radius a <code>double</code> value
	 */
	public void setRadius(double radius) {
		circle.width = radius * 2.0;
		circle.height = radius * 2.0;
	}

	/**
	 * Method <code>swap</code> swaps this circle instance member variables with <code>c</code>'s member variables.
	 *
	 * @param c a <code>Circle</code> value
	 */
	public void swap(Circle c) {
		Ellipse2D.Double te = new Ellipse2D.Double(circle.x,circle.y,circle.width,circle.height);
		circle.setFrame(c.circle.x,c.circle.y,c.circle.width,c.circle.height);
		c.circle.setFrame(te.x,te.y,te.width,te.height);

		StaticPoint2D tp = new StaticPoint2D(center.x,center.y);
		center.x = c.center.x;
		center.y = c.center.y;

		c.center.x = tp.x;
		c.center.y = tp.y;

		boolean tb = hasSet;
		hasSet = c.hasSet;
		c.hasSet = tb;
	}

	public Object clone() {
		Circle c = new Circle();
		c.circle.setFrame(circle.x,circle.y,circle.width,circle.height);
		c.center.x = center.x;
		c.center.y = center.y;
		c.hasSet = hasSet;
		return c;
	}

	/**
	 * Method <code>paint</code> paints the component using the colors passed in
	 * if the circle's location has been set.
	 *
	 * @param g a <code>Graphics</code> value
	 * @param border a <code>Color</code> value
	 * @param fill a <code>Color</code> value
	 */
	public void paint(Graphics g, Color border, Color fill) {
		if (hasSet) {
			Graphics2D g2 = (Graphics2D)g;
			Paint cColor = g2.getPaint();
			if (fill != null) {
				g2.setPaint(fill);
				g2.fill(circle);
			}
			if (border != null) {
				g2.setPaint(border);
				g2.draw(circle);
			}
			g2.setPaint(cColor);
		}	
	}

	/**
	 * Method <code>paint</code> paints in the color given if the circle's location has been set.
	 *
	 * @param g a <code>Graphics</code> value
	 * @param color a <code>Color</code> value
	 */
	public void paint(Graphics g, Color color) {
		if (hasSet && color != null) {
			Graphics2D g2 = (Graphics2D)g;
			Paint cColor = g2.getPaint();
			g2.setPaint(color);
			g2.fill(circle);
			g2.draw(circle);
			g2.setPaint(cColor);
		}
	}

	/**
	 * Method <code>paint</code> paints the circle using the given color if the
	 * circle's location has been set.
	 *
	 * @param g a <code>Graphics</code> value
	 */
	public void paint(Graphics g) {
		if (hasSet) {
			Graphics2D g2 = (Graphics2D)g;
			g2.fill(circle);
			g2.draw(circle);
		}
	}

	/**
	 * Method <code>paintIcon</code> paints the circle to the location given ignoring the set location if any.
	 * The current paint color of g is used as the color.
	 *
	 * @param c a <code>Component</code> value
	 * @param g a <code>Graphics</code> value
	 * @param x an <code>int</code> value
	 * @param y an <code>int</code> value
	 */
	public void paintIcon(Component c, Graphics g, int x, int y) {
		Ellipse2D e = new Ellipse2D.Double(x,y,circle.width,circle.height);
		Graphics2D g2 = (Graphics2D)g;
		g2.fill(e);
		g2.draw(e);
	}

	private static class StaticPoint2D extends Point2D {
		private double x,y;

		private StaticPoint2D() { }

		private StaticPoint2D(double x, double y) {
			this.x = x;
			this.y = y;
		}

		public double getX() {
			return x;
		}

		public double getY() {
			return y;
		}

		public void setLocation(double x, double y) {
			throw new UnsupportedOperationException("Modification not allowed");
		}

		public String toString() {
			return "StaticPoint2D["+x+", "+y+"]";
		}

		public boolean equals(Object obj) {
			if (obj instanceof Point2D) {
				Point2D p = (Point2D)obj;
				return p.getX() == x && p.getY() == y;
			}
			return false;
		}
	}
}
