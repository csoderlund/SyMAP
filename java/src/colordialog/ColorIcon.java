package colordialog;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;

import javax.swing.Icon;

/**
 * Class ColorIcon consists of a rectangular color with a possible icon displayed over that color.
 * @see Icon
 */
public class ColorIcon implements Icon {
    private Color color;
    private Icon overIcon;
    private int width, height;

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
