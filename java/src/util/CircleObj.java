package util;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;

/**
 * Class <code>CircleObj</code> is a circle that also holds it's desired colors
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 * @see Circle
 */
public class CircleObj extends Circle {
    private Color border,fill;

    /**
     * Creates a new <code>CircleObj</code> instance.
     *
     * @param radius a <code>double</code> value
     * @param border a <code>Color</code> value
     * @param fill a <code>Color</code> value
     */
    public CircleObj(double radius, Color border, Color fill) {
	super(radius);
	this.border = border;
	this.fill = fill;
    }

    /**
     * Method <code>setFill</code>
     *
     * @param c a <code>Color</code> value
     */
    public void setFill(Color c) {
	fill = c;
    }

    /**
     * Method <code>setBorder</code>
     *
     * @param c a <code>Color</code> value
     */
    public void setBorder(Color c) {
	border = c;
    }

    /**
     * Method <code>getFill</code>
     *
     * @return a <code>Color</code> value
     */
    public Color getFill() {
	return fill;
    }

    /**
     * Method <code>getBorder</code>
     *
     * @return a <code>Color</code> value
     */
    public Color getBorder() {
	return border;
    }

    /**
     * Method <code>paint</code> paints the circle using the values set
     *
     * @param g a <code>Graphics</code> value
     */
    public void paint(Graphics g) {
	super.paint(g,border,fill);
    }
	    
    /**
     * Method <code>paintIcon</code> paints the circle using the values set other than for the location
     * in which the x and y arguments are used.
     *
     * @param c a <code>Component</code> value
     * @param g a <code>Graphics</code> value
     * @param x an <code>int</code> value
     * @param y an <code>int</code> value
     */
    public void paintIcon(Component c, Graphics g, int x, int y) {
	Circle e = new Circle(x,y,getIconWidth()/2.0);
	e.paint(g,border,fill);
    }
}
