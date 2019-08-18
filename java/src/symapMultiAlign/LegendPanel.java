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

	public LegendPanel ( )         // for contig alignment
	{
		setLayout( null );
        setBackground( Color.WHITE );
        setBorder(BorderFactory.createLineBorder(Color.BLACK));
        //setAlignmentY(Component.LEFT_ALIGNMENT);
        setAlignmentX(Component.LEFT_ALIGNMENT);
        
        setMinimumSize(new Dimension(500, 200));
        setPreferredSize(getMinimumSize());
        setMaximumSize(getMinimumSize());
	}
	
	public LegendPanel (boolean b ) // for pairwise alignment, need smaller box
	{
		setLayout( null );
        setBackground( Color.WHITE );
        setBorder(BorderFactory.createLineBorder(Color.BLACK));
        //setAlignmentY(Component.LEFT_ALIGNMENT);
        setAlignmentX(Component.LEFT_ALIGNMENT);
        
        setMinimumSize(new Dimension(300, 130));
        setPreferredSize(getMinimumSize());
        setMaximumSize(getMinimumSize());
	}
	
	public void paintComponent(Graphics g)
	{
		super.paintComponent( g );
		Graphics2D g2 = (Graphics2D)g;
	
		int y = 10;
		int x = 10;
		g2.drawString("Legend", x, y+=10);
		x += 25;
		
		g2.setFont(new Font(g2.getFont().getName(), Font.PLAIN, g2.getFont().getSize()));

		if (isPair) { // what else is there?
			drawKey(0, g2, AlignmentPanelBase.gapGreen, 	    "Gap", 		x+20, y+=15);
			drawKey(0, g2, AlignmentPanelBase.mismatchRed, 	"Non-synonymous mismatch", x+20, y+=15);
			drawKey(0, g2, AlignmentPanelBase.purple, 		"Synonymous mismatch", x+20, y+=15);
			drawKey(0, g2, AlignmentPanelBase.mediumGray, 	"X's in AA or extended end", 	 x+20, y+=15);
		}
	}
	private boolean isPair=false;
	public void setIsPair(boolean b) {isPair=b;}

//	private static final Color ERR = new Color(255, 230, 230);
//	private static final Color FOR = new Color(230, 230, 255);
//	private static final Color REV = Color.LIGHT_GRAY;


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
		g2.drawString("= "+s, x+15, y+9);
	}
}
