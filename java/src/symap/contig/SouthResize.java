package symap.contig;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;

import javax.swing.Icon;

/**
 * Class <code>SouthResize</code> is the icon the signifyies moving up and down.
 *
 * The desired location of the icon can be preset using setLocation(). The x and y
 * in paintIcon are used as the origin of the graphics object.  So to use the location
 * set in setLocation(), paintIcon should be called with x=0 and y=0.
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 * @see Icon
 */
public class SouthResize implements Icon {
    private Rectangle rect;
    private Polygon poly;

    /**
     * Creates a new <code>SouthResize</code> instance setting its location to (0,0).
     *
     * @param dim a <code>Dimension</code> value the size of the icon
     */
    public SouthResize(Dimension dim) {
	super();
	rect = new Rectangle(dim);
	poly = new Polygon();
	setLocation(new Point());
    }

    /**
     * Method <code>getIconHeight</code> is the height of the icon
     *
     * @return an <code>int</code> value
     */
    public int getIconHeight() {
	return rect.height;
    }

    /**
     * Method <code>getIconWidth</code> is the width of the icon
     *
     * @return an <code>int</code> value
     */
    public int getIconWidth() {
	return rect.width;
    }

    /**
     * Method <code>setLocation</code> sets the location of the icon.
     *
     * @param p a <code>Point</code> value
     */
    public void setLocation(Point p) {
	rect.setLocation(p);

	poly.reset();
	poly.addPoint(p.x+(int)Math.round(rect.width/2.0),p.y);
	poly.addPoint(p.x+(int)Math.round(rect.width*11/16.0),p.y+(int)Math.round(rect.height*3/16.0));
	poly.addPoint(p.x+(int)Math.round(rect.width*9/16.0),p.y+(int)Math.round(rect.height*3/16.0));
	poly.addPoint(p.x+(int)Math.round(rect.width*9/16.0),p.y+(int)Math.round(rect.height*7/16.0));
	poly.addPoint(p.x+rect.width,p.y+(int)Math.round(rect.height*7/16.0));
	poly.addPoint(p.x+rect.width,p.y+(int)Math.round(rect.height*9/16.0));
	poly.addPoint(p.x+(int)Math.round(rect.width*9/16.0),p.y+(int)Math.round(rect.height*9/16.0));
	poly.addPoint(p.x+(int)Math.round(rect.width*9/16.0),p.y+(int)Math.round(rect.height*13/16.0));
	poly.addPoint(p.x+(int)Math.round(rect.width*11/16.0),p.y+(int)Math.round(rect.height*13/16.0));
	poly.addPoint(p.x+(int)Math.round(rect.width/2.0),p.y+rect.height);
	poly.addPoint(p.x+(int)Math.round(rect.width*5/16.0),p.y+(int)Math.round(rect.height*13/16.0));
	poly.addPoint(p.x+(int)Math.round(rect.width*7/16.0),p.y+(int)Math.round(rect.height*13/16.0));
	poly.addPoint(p.x+(int)Math.round(rect.width*7/16.0),p.y+(int)Math.round(rect.height*9/16.0));
	poly.addPoint(p.x,p.y+(int)Math.round(rect.height*9/16.0));
	poly.addPoint(p.x,p.y+(int)Math.round(rect.height*7/16.0));
	poly.addPoint(p.x+(int)Math.round(rect.width*7/16.0),p.y+(int)Math.round(rect.height*7/16.0));
	poly.addPoint(p.x+(int)Math.round(rect.width*7/16.0),p.y+(int)Math.round(rect.height*3/16.0));
	poly.addPoint(p.x+(int)Math.round(rect.width*5/16.0),p.y+(int)Math.round(rect.height*3/16.0));
    }

    /**
     * Method <code>contains</code> determines if the point is contained within the icon based
     * off of the location as set in setLocatioin().
     *
     * @param p a <code>Point</code> value
     * @return a <code>boolean</code> value
     */
    public boolean contains(Point p) {
	return rect.contains(p);
    }

    /**
     * Method <code>paintIcon</code> paints the icon to g using (x,y) as the origin.
     *
     * @param c a <code>Component</code> value
     * @param g a <code>Graphics</code> value
     * @param x an <code>int</code> value
     * @param y an <code>int</code> value
     */
    public void paintIcon(Component c, Graphics g, int x, int y) {
	Graphics2D g2 = (Graphics2D)g;
	AffineTransform saveAT = g2.getTransform();
	g2.translate(x,y);
	g2.fill(poly);
	g2.setTransform(saveAT);
    }
}
