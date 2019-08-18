package util;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import java.applet.Applet;

import javax.swing.JLabel;

import symap.frame.SyMAPFrame;

/**
 * Class <code>LinkLabel</code> is a JLabel with its text underlined and a 
 * transparent background that changes foreground color when the mouse 
 * enters and exits.
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 * @see JLabel
 * @see MouseListener
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class LinkLabel extends JLabel implements MouseListener {
	private static final Cursor HAND_CURSOR = new Cursor(Cursor.HAND_CURSOR);
	
	private Color defaultColor;
    private Color hoverColor;
    private String url; // mdb added 7/21/09

    /**
     * Creates a new <code>LinkLabel</code> instance with the given text and colors.
     *
     * @param text a <code>String</code> value of label's text
     * @param color a <code>Color</code> value of default color for label's text
     * @param hoverColor a <code>Color</code> value of the text's color when 
     * the mouse is hovering over the label
     */
    public LinkLabel(String text, Color color, Color hoverColor) {
		super(text);
		this.defaultColor = color;
		this.hoverColor = hoverColor;
		if (this.defaultColor == null) this.defaultColor = getForeground();
		if (this.hoverColor == null) this.hoverColor = getForeground();
	
		if (defaultColor != null) setTheColor(defaultColor);
		setOpaque(false);
		addMouseListener(this);
    }
    
    // mdb added 7/17/09
    public LinkLabel(String text) {
    	this(text, Color.blue.darker().darker(), Color.blue);
    }
    
    // mdb added 7/21/09
    public LinkLabel(String text, Color color, Color hoverColor, String url) {
    	this(text, color, hoverColor);
    	this.url = url;
    }
    
    // mdb added 9/3/09
    public LinkLabel(String text, String url) {
    	this(text, Color.blue.darker().darker(), Color.blue, url);
    }

    /**
     * Method <code>setColor</code> sets the color to be used when the mouse 
     * is not over the label.
     *
     * @param color a <code>Color</code> value
     */
    public void setColor(Color color) {
    	defaultColor = color;
    }

    /**
     * Method <code>setHoverColor</code> sets the color to be used when the 
     * mouse is over the label.
     *
     * @param color a <code>Color</code> value
     */
    public void setHoverColor(Color color) {
    	hoverColor = color;
    }

    /**
     * Method <code>paint</code> paints the component, painting the text and 
     * the line underneath it.
     *
     * @param g a <code>Graphics</code> value
     */
    public void paint(Graphics g) {
		super.paint(g);
		Rectangle r = getBounds();//g.getClipBounds(); // mdb changed 5/27/09 - fix scrolling bug
		g.drawLine(0, r.height - this.getFontMetrics(this.getFont()).getDescent() + 1,
			   this.getFontMetrics(this.getFont()).stringWidth(this.getText()),  
			   r.height - this.getFontMetrics(this.getFont()).getDescent() + 1);
    }

    /**
     * Method <code>setupLink</code> should be used to set the size of the 
     * component if it doesn't appear to be sizing itself.
     */
    public void setupLink() {
		FontMetrics fm = this.getFontMetrics(this.getFont());
		setSize(fm.stringWidth(this.getText()),fm.getMaxAscent()+fm.getMaxDescent());
    }

    /**
     * Method <code>mouseEntered</code> handles when the mouse enters the 
     * block link changing its color to the hover color.
     *
     * @param e a <code>MouseEvent</code> value
     */
    public void mouseEntered(MouseEvent e) {
		setTheColor(hoverColor);
		setCursor(HAND_CURSOR);
    }

    /**
     * Method <code>mouseExited</code> handles when the mouse exits the link, 
     * change its color back to the default.
     *
     * @param e a <code>MouseEvent</code> value
     */
    public void mouseExited(MouseEvent e) {
		setTheColor(defaultColor);
		setCursor(null);
    }

    public void mouseClicked(MouseEvent e) { 
    	// mdb added 7/21/09 #166 - for TextBox URL support
    	if (url != null) {
	    	Applet applet = null;
	    	for (Frame f : Frame.getFrames()) { // get applet if exists
	    		if (f instanceof SyMAPFrame) {
	    			applet = ((SyMAPFrame)f).getApplet();
	    			if (applet != null) break;
	    		}
	    	}
	    	Utilities.tryOpenURL(applet, url);
    	}
    }
    
    public void mouseReleased(MouseEvent e) { }
    public void mousePressed(MouseEvent e) { }

    private void setTheColor(Color c) {
		setForeground(c);
		setBackground(c);
    }
}
