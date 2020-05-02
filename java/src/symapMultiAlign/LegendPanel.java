package symapMultiAlign;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;

import javax.swing.BorderFactory;
import javax.swing.JPanel;

public class LegendPanel extends JPanel
{
	private static final long serialVersionUID = -5292053168212278988L;
	private String [] names;
	
	public LegendPanel (String [] names ) 
	{
		this.names = names;
		setLayout( null );
        setBackground( Color.WHITE );
        setBorder(BorderFactory.createLineBorder(Color.BLACK));
        setAlignmentX(Component.LEFT_ALIGNMENT);
        
        int w = 0, h=(names.length * 25) + 10;
        for (String n : names) {
        	 	if (w<n.length()) w=n.length();
        }
        w *= 9;
        	
        setMinimumSize(new Dimension(w, h));
        setPreferredSize(getMinimumSize());
        setMaximumSize(getMinimumSize());
	}
	
	public void paintComponent(Graphics g)
	{
		super.paintComponent( g );
		Graphics2D g2 = (Graphics2D)g;
	
		int y = 10;
		int x = 10;
		g2.drawString("Locations", x, y+=10);
		x += 10;
		
		g2.setFont(new Font(g2.getFont().getName(), Font.PLAIN, g2.getFont().getSize()));

		for (String n : names) { // CAS505 changed from incorrect legend to names
			drawString(g2,  n,  x, y+=15);
		}
	}
	private static void drawString(Graphics2D g2,String s, int x, int y) {
		Font font = new Font("Courier", Font.PLAIN, 10);
	    g2.setFont(font);
		g2.setColor(Color.BLACK);
		g2.drawString(s, x+10, y+9);
	}
	private static void drawKey(int type, Graphics2D g2, Color c, String s, int x, int y) 
	{
		g2.setColor(c);
		if (type == 0)
			g2.fillRect(x, y, 10, 10);
		else {
			Polygon triangle = new Polygon();
			triangle.addPoint(x,y);
			triangle.addPoint(x+10,y);
			triangle.addPoint(x+5,y+10);
			g2.fill(triangle);
		}
		
		g2.setColor(Color.BLACK);
		g2.drawString(s, x+15, y+9);
	}
}
