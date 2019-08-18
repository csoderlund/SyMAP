package util;

import java.awt.Component;
import java.awt.geom.Dimension2D;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import javax.swing.Icon;

/**
 * Class <code>Arrow</code> is an icon that just draws an arrow pointing downward (i.e. like a 'v')
 * or in the direction specified.
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 * @see Icon
 */
public class Arrow implements Icon {
    public static final double POINT_DOWN  = 0;
    public static final double POINT_LEFT  = Math.PI/2.0;
    public static final double POINT_UP    = Math.PI;
    public static final double POINT_RIGHT = -Math.PI/2.0;

    private Dimension2D dim;
    private Line2D line = new Line2D.Double();

    public Arrow() { 
	this.dim = new DoubleDimension();
    }

    public Arrow(Dimension2D dim) {
	this.dim = dim;
    }

    /**
     * Method <code>setDimension</code> sets the dimensions of the arrow.  The width being the distance between 
     * the points on the opening.
     *
     * @param dim a <code>Dimension2D</code> value
     */
    public void setDimension(Dimension2D dim) {
	this.dim = dim;
    }

    public Dimension2D getDimension() {
	return dim;
    }

    public int getIconHeight() {
	return (int)dim.getHeight();
    }

    public int getIconWidth() {
	return (int)dim.getWidth();
    }

    public double getHeight() {
	return dim.getHeight();
    }

    public double getWidth() {
	return dim.getWidth();
    }

    public void paintIcon(Component c, Graphics g, int x, int y) {
	paint((Graphics2D)g,(double)x,(double)y,POINT_DOWN);
    }

    /**
     * Method <code>paint</code> paints in double precision using the current paint (g2.getPaint()).
     *
     * @param g2 a <code>Graphics2D</code> value
     * @param x a <code>double</code> value
     * @param y a <code>double</code> value
     * @param direction a <code>double</code> value of UP, DOWN, LEFT, RIGHT or other desired radians
     */
    public void paint(Graphics2D g2, double x, double y, double direction) {
	AffineTransform saveAt = g2.getTransform();

	g2.rotate(direction,x+dim.getWidth()/2.0,y+dim.getHeight()/2.0);

	line.setLine(x,y,x + dim.getWidth()/2.0,y+dim.getHeight());
	g2.draw(line);
	line.setLine(line.getX2(),line.getY2(),x+dim.getWidth(),y);
	g2.draw(line);

	g2.setTransform(saveAt);
    }
}
