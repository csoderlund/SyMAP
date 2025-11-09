package util;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JLabel;

/**
 * LinkLabel creates a JLabel with its text underlined and a 
 * transparent background that changes foreground color when the mouse  enters and exits.
 * Only used by ManagerFrame, ChrExpFrame, sequence.TextBox
 */

public class LinkLabel extends JLabel implements MouseListener {
	private static final long serialVersionUID = 1L;

	private static final Cursor HAND_CURSOR = new Cursor(Cursor.HAND_CURSOR);
	
	private Color defaultColor;
    private Color hoverColor;
    private String url; 

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
    
    public LinkLabel(String text) {
    	this(text, Color.blue.darker().darker(), Color.blue);
    }
    
    public LinkLabel(String text, Color color, Color hoverColor, String url) {
    	this(text, color, hoverColor);
    	this.url = url;
    }
    
    public LinkLabel(String text, String url) {
    	this(text, Color.blue.darker().darker(), Color.blue, url);
    }

    public void setColor(Color color) {
    	defaultColor = color;
    }
    public void setHoverColor(Color color) {
    	hoverColor = color;
    }

    public void paint(Graphics g) {
		super.paint(g);
		Rectangle r = getBounds();
		g.drawLine(0, r.height - this.getFontMetrics(this.getFont()).getDescent() + 1,
			   this.getFontMetrics(this.getFont()).stringWidth(this.getText()),  
			   r.height - this.getFontMetrics(this.getFont()).getDescent() + 1);
    }
    public void setupLink() {
		FontMetrics fm = this.getFontMetrics(this.getFont());
		setSize(fm.stringWidth(this.getText()),fm.getMaxAscent()+fm.getMaxDescent());
    }
    public void mouseEntered(MouseEvent e) {
		setTheColor(hoverColor);
		setCursor(HAND_CURSOR);
    }
    public void mouseExited(MouseEvent e) {
		setTheColor(defaultColor);
		setCursor(null);
    }
    public void mouseClicked(MouseEvent e) { 
    	if (url != null) util.Jhtml.tryOpenURL(url);
    }
    public void mouseReleased(MouseEvent e) { }
    public void mousePressed(MouseEvent e) { }

    private void setTheColor(Color c) {
		setForeground(c);
		setBackground(c);
    }
}
