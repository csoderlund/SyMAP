package colordialog;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;

import javax.swing.Icon;

/**
 * Class <code>ColorIcon</code> consists of a rectangular color with a possible icon displayed over that color.
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 * @see Icon
 */
public class ColorIcon implements Icon {
    private Color color;
    private Icon overIcon;
    private int width, height;

    /**
     * Creates a new <code>ColorIcon</code> instance.
     *
     * @param color a <code>Color</code> value
     * @param over an <code>Icon</code> value of an icon to display over the color, null to display no icon over color
     * @param minWidth an <code>int</code> value
     * @param minHeight an <code>int</code> value
     */
    public ColorIcon(Color color, Icon over, int minWidth, int minHeight) {
	this.color = color;
	overIcon = over;
	width = minWidth;
	height = minHeight;
	if (overIcon != null) {
	    if (overIcon.getIconWidth() > width) width = overIcon.getIconWidth();
	    if (overIcon.getIconHeight() > height) height = overIcon.getIconHeight();
	}

    }

    public Color getColor() {
	return color;
    }

    public void setColor(Color color) {
	this.color = color;
    }

    public int getIconWidth() {
	return width;
    }

    public int getIconHeight() {
	return height;
    }

    public void paintIcon(Component c, Graphics g, int x, int y) {
	Color tcolor = g.getColor();
	g.setColor(color);
	g.fillRect(x,y,getIconWidth(),getIconHeight());
	g.setColor(tcolor);
	if (overIcon != null) {
	    int ox = (int)Math.round( x + ( (getIconWidth()  - overIcon.getIconWidth() ) / 2.0 ) );
	    int oy = (int)Math.round( y + ( (getIconHeight() - overIcon.getIconHeight()) / 2.0 ) );
	    overIcon.paintIcon(c,g,ox,oy);
	}
    }
}
