package symap.frame;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.TreeSet;

import javax.swing.JComponent;

import symap.manager.Mproject;

/*************************************************
 * Called by ChrExpFrame. Draw Chromosome Icons to for a project
 */
public class ProjIcons extends JComponent implements MouseListener, MouseMotionListener {
	private static final long serialVersionUID = 1L;
	private final static double MAX_CHR_HEIGHT = 80;
	private final static int MIN_CHR_HEIGHT = 15;
	private final static int WIDTH = 12;
	private final static int GAP = 5;
	private final static int FONT_HEIGHT = 11;//*fm.getHeight()*/
	private Mproject project;
	private Block[] blocks;		
	private ChrInfo[] tracks; // chromosome
	private MapLeft mapper;
	private ActionListener listener;
	private int lineSize = 15;
	private int chrHeight = 0;
	private int maxTracks = lineSize*30;

	protected ProjIcons(MapLeft mapper, Mproject project, ActionListener listener,
			TreeSet<Integer> grpIdxWithSynteny) {
		super();
		
		this.mapper = mapper;
		this.project = project;
		this.listener = listener;
		this.tracks = mapper.getChrs(project.getID(),grpIdxWithSynteny);
		this.blocks = mapper.getBlocksForProject(project.getID());
		
		int height = 0;
		for (ChrInfo t : tracks)
			height = Math.max(height, (int)(MAX_CHR_HEIGHT * t.getSizeBP() / mapper.getMaxBpPerUnit()));
		
		chrHeight = height + 5;
		if (tracks.length > maxTracks)
			System.out.println(project.getDisplayName() + " has more than " + maxTracks + " sequences.\nThe first " + maxTracks + " will be shown.");
		
		int numTracks = Math.min(tracks.length,maxTracks);
		while (numTracks > lineSize && numTracks % lineSize > 0 && numTracks % lineSize < 4)
		{
			lineSize++; // Don't put just one or two on a new line
		}
		int width = lineSize * (WIDTH + GAP) + GAP;
		int nRows = 1 + numTracks/lineSize;
		int compHeight = (chrHeight + FONT_HEIGHT + 10)*nRows;
		Dimension d = new Dimension(width,compHeight);
		setMinimumSize(d);
		setMaximumSize(d);
		setPreferredSize(d);
		
		addMouseListener(this);
		addMouseMotionListener(this);
	}
	
	protected void paintComponent(Graphics g) { 
		Graphics2D g2 = (Graphics2D)g;
		int x = 5;
		int y = FONT_HEIGHT;
		
		// Draw each chromosome
		// Label box runs from y-FONT_HEIGHT to y
		// Chromosome rectangle runs from y+6 to y+6+height
		g2.setFont(new Font(g2.getFont().getName(), Font.PLAIN, 9));
		FontMetrics fm = g2.getFontMetrics();
		int nTrack = 0;
		int numTracks = 1;
		for (ChrInfo t : tracks) {
			int height = (int)(MAX_CHR_HEIGHT * t.getSizeBP() / mapper.getMaxBpPerUnit());
			height = Math.max(height,MIN_CHR_HEIGHT);
			boolean isVisible = (t.isVisible() || t == mapper.getRefChr());
			
			int strWidth = fm.stringWidth(t.getGroupName());
			if (t == mapper.getRefChr()) {
				g2.setColor(Color.RED);
				g2.drawRect(x-1, y-FONT_HEIGHT/*fm.getHeight()*/, WIDTH+1, FONT_HEIGHT/*fm.getHeight()*/+3);
			}
			else
				g2.setColor(Color.BLACK);
			g2.drawString(t.getGroupName(), x+(WIDTH-strWidth)/2, y);
			
			if (isVisible)
				g2.setColor(project.getColor());
			else
				g2.setColor(project.getColor().darker().darker());

			g2.fillRect(x, y+6, WIDTH, height);
			
			if (isVisible)
				g2.setColor(Color.red);
			else 
				g2.setColor(Color.red.darker());
			
			for (Block b : blocks) {
				// Make this project be the query and the reference be the target
				if (b.isTarget(project.getID()))
					b = b.swap(); 
				
				if ((b.getGroup2Idx() == mapper.getRefChr().getGroupIdx() || mapper.isRefChr(t))
						&& b.getGroup1Idx() == t.getGroupIdx())
				{
					long startBP = (long) b.getStart1(); // this does not work right unless long
					long endBP = (long) b.getEnd1();
				
					int startY = (int)(height * startBP / t.getSizeBP());
					int endY = (int)(height * endBP / t.getSizeBP());
					int heightY = Math.max(1,Math.abs(endY-startY));
					g2.fillRect(x, y+6+startY, WIDTH, heightY);
				}
			}
		
			x += WIDTH+GAP;
			nTrack++;
			numTracks++;
			if (numTracks > maxTracks) break;
			
			if (nTrack % lineSize == 0){
				x = 5;
				y += chrHeight + FONT_HEIGHT + 10;
				nTrack = 0;
			}
		}
	}

	public void mouseClicked(MouseEvent e) { 
		int xClick = e.getX();
		int yClick = e.getY();
		int x = 5;
		int y = FONT_HEIGHT;
		boolean bChanged = false;
		
		int nTrack = 0;
		int numTracks = 0;
		for (ChrInfo t : tracks) {
			if (!mapper.isRefChr(t)) {
				if (xClick >= x && xClick <= x+WIDTH 
						&& yClick >= y && yClick <= y+chrHeight+6) 
				{ // Rectangle clicked: add/remove track
					mapper.setVisibleChr(t, !t.isVisible());
					bChanged = true;
					break;
				}
				else if (xClick >= x && xClick <= x+WIDTH 
						&& yClick >= y-19 && yClick < y) 
				{ // Number clicked: change reference
					mapper.setRefChr(t);
					mapper.hideVisibleChrs(); // clear selection (excluding reference)
					bChanged = true;
					break;
				}
			}
			
			x += WIDTH+5;
			nTrack++;
			numTracks++;
			if (numTracks > maxTracks) break;
			
			if (nTrack % lineSize == 0) {
				x = 5;
				y += chrHeight + FONT_HEIGHT + 10;
				nTrack = 0;
			}
		}
		
		if (bChanged) { // Call parent handler to redraw all displays of this type
			if (listener != null)
				listener.actionPerformed( new ActionEvent(this, -1, "Redraw") );
		}
	}
	
	public void mouseEntered(MouseEvent e) { 		
		setCursor( Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) );
	}
	public void mouseExited(MouseEvent e) { 
		setCursor( Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR) );
	}
	
	public void mouseDragged(MouseEvent e) { }
	public void mousePressed(MouseEvent e) { }
	public void mouseReleased(MouseEvent e) { }
	public void mouseMoved(MouseEvent e) { }
	public String toString() { return project.getDBName(); }
}
