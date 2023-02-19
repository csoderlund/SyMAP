package dotplot;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.font.TextLayout;

import javax.swing.JPanel;
import javax.swing.JScrollPane;

import util.Utilities;
import symap.frame.HelpListener;
import symap.frame.HelpBar;

/**
 * Draws the dotplot
 * CAS533 removed 'implements Observer' and other major changes
 **/
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class Plot extends JPanel implements HelpListener {
	//private static final boolean DEBUG = symap.SyMAP.DEBUG;
	private final int X = Data.X, Y = Data.Y;
	private final double pctidLow = 30.0; // values don't seem to be lower than this, so use a constant
	private Data data;
	private JScrollPane scroller;
	private Dimension dim;
	private int sX1, sX2, sY1, sY2; 	// coordinates of selected region 
	private double xPixelBP, yPixelBP; 	// pixels per basepair
	private boolean isSelectingArea = false;
	
	public Plot(Data d, HelpBar hb) {
		super(null);
		
		data = d;
		dim  = new Dimension();
		
		setBackground(FAR_BACKGROUND);
		scroller = new JScrollPane(this);
		scroller.setBackground(FAR_BACKGROUND);
		scroller.getViewport().setBackground(FAR_BACKGROUND);
		scroller.getVerticalScrollBar().setUnitIncrement(10); 

		PlotListener l = new PlotListener();
		addMouseListener(l);
		addMouseMotionListener(l);
		
		hb.addHelpListener(this); 
	}
	
	public JScrollPane getScrollPane() { // DotPlotFrame, Plot
		return scroller;
	}

	/****************************************************************************/
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		
		// CAS533 Save Image had blurry text until this is added
		((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
		
		setDims();
		Group[] xGroups = data.getVisibleGroups(X);
		Group[] yGroups = data.getVisibleGroupsY(xGroups);

		Group.setOffsets(xGroups);
		Group.setOffsets(yGroups);
		
	//draw border
		g.setColor(BACKGROUND);
		g.fillRect(MARGIN + 1, MARGIN + 1, dim.width - 1, dim.height - 1);
		g.setColor(BACKGROUND_BORDER);
		g.drawRect(MARGIN, MARGIN, dim.width, dim.height);

	//draw title
		drawCentered(g, data.getProject(X).getDisplayName(),					// Ref
				MARGIN, MARGIN/4 + 10,  MARGIN + dim.width, MARGIN/2,  HORZ);
	
	//draw chromosome nuames
		if (data.isTileView()) {
			drawCentered(g, data.getCurrentProj().getDisplayName(), 
				MARGIN/2 + 10, MARGIN,  MARGIN/2, MARGIN + dim.height, VERT);
		}
		else { 
			long offset = 0;
			for (int i = 1;  i < data.getNumProjects();  i++) {
				Project p = data.getProject(i);
				int y = MARGIN + (int)(offset * yPixelBP);
				long size = data.getVisibleGroupsSize(i, xGroups);
				
				if (size > 0) {
					drawCentered(g, p.getDisplayName(),
						MARGIN/2 + 10, y, MARGIN/2, y + (int)(size * yPixelBP), VERT);
					g.setColor(BACKGROUND_BORDER);
					
					if (i > 1) g.drawLine(MARGIN-17, y, MARGIN, y);
					offset += size;
				}
			}
		}
		
	//draw blocks & hits
		if (!data.isTileView()) drawAll(g);
		else	                drawTile(g);
	}

	private void setDims() {
		double zoom  = data.getZoomFactor();

		Dimension d = new Dimension((int)(scroller.getWidth() * zoom), (int)(scroller.getHeight() * zoom));
		setPreferredSize(d);
		dim.width =  (int)((d.width - (MARGIN * 1.1)));
		dim.height = (int)((d.height - (MARGIN * 1.2)));
		
		if (data.getProject(X) == null || data.getProject(Y) == null) 
			return;

		if (data.isTileView()) { // selected tile
			xPixelBP = dim.getWidth()  / data.getCurrentGrpSize(X);
			yPixelBP = dim.getHeight() / data.getCurrentGrpSize(Y);
		}
		else {
			xPixelBP = dim.getWidth()  / data.getVisibleGroupsSize(X);
			yPixelBP = dim.getHeight() / data.getVisibleGroupsSizeY(data.getVisibleGroups(X));
		}

		if (data.isScaled()) {
			yPixelBP = xPixelBP * data.getScaleFactor();
			dim.height = (int)(yPixelBP * data.getVisibleGroupsSizeY(data.getVisibleGroups(X)));
			d.height   = (int)(dim.height + (MARGIN * 1.2));
			setPreferredSize(d);
		}

		revalidate();
		scroller.getViewport().revalidate();
	}
	/********************************************************************/
	private void drawAll(Graphics g) { 
		FilterData fd =   data.getFilterData();
		Group[] xGroups = data.getVisibleGroups(X);
		Group[] yGroups = data.getVisibleGroupsY(xGroups);
		
	// Figure out the longest group names
		int maxYGrpName = 0, maxXGrpName = 0;
		FontMetrics mtc = g.getFontMetrics(g.getFont());		
		for (Group grp : yGroups) {
			int w = mtc.stringWidth(grp.getName());
			if (w > maxYGrpName) maxYGrpName = w;	
		}
		for (Group grp : xGroups) {
			int w = mtc.stringWidth(grp.getName());
			if (w > maxXGrpName) maxXGrpName = w;	
		}	 
	
	//draw chr# and border; scale is  1 unless parameter min_display_sz_bp set by user
		for (Group grp : xGroups) {
			int x1 = MARGIN +   (int)(grp.getOffset() * xPixelBP );
			int x2 = x1 + (int)((int)(grp.getGrpLenBP()  * grp.getScale()) * xPixelBP); // use this order to match calc of grp offsets
			drawCentered(g, grp.getName(), x1, MARGIN/2 + 10, x2, MARGIN, HORZ);
			
			if (grp != xGroups[xGroups.length-1]) 
				g.drawLine(x2, MARGIN, x2, dim.height + MARGIN); // vertical line between 2 tiles
		}
		for (Group grp : yGroups) {
			int y1 = MARGIN +   (int)(grp.getOffset() * yPixelBP );
			int y2 = y1 + (int)((int)(grp.getGrpLenBP() * grp.getScale())*yPixelBP) ;
		
			drawCentered(g, grp.getName(), MARGIN/2, y1, MARGIN, y2, HORZ);
			
			if (grp != yGroups[yGroups.length-1]) 
				g.drawLine(MARGIN, y2, dim.width + MARGIN, y2); // horz line between 2 tiles
		}
	
	//draw hits
		int dotSizeB = dotSize(data.getDotSize());
		int dotSizeNB = (fd.isShowMixHits()) ? dotSize(1) : dotSizeB;
		for (Tile tile : data.getTiles()) {
			Group gX = tile.getGroup(X);
			Group gY = tile.getGroup(Y);
			if (!data.isGroupVisible(gX) || !data.isGroupVisible(gY)) continue;
			
			int x = MARGIN + (int)(gX.getOffset()*xPixelBP);
			int y = MARGIN + (int)(gY.getOffset()*yPixelBP);
			double xf = xPixelBP*gX.getScale();
			double yf = yPixelBP*gY.getScale();
			
			for (DPHit ht : tile.getHits()) {
				if (fd.isShowBlockHits() && !ht.isBlock()) continue;
				if (ht.getPctid() < fd.getPctid()) continue;
			
				Color c = colorHits;
				if (fd.isHighBlockHits() && ht.isBlock()) {
					c =  (fd.isHighGreen()) ? colorBlockHits2 : colorBlockHits1;
				}
				g.setColor(c);
				
				drawHit(g, ht, x, y, dotSizeB, xf, yf, ht.isBlock(), fd.isShowMixHits(), dotSizeNB);
			}
		}
		//draw blocks (CAS533 moved after hits so drawn on top
		if (fd.isShowBlocks()) {
			for (Tile tile : data.getTiles()) {	
				Group gX = tile.getGroup(X);
				Group gY = tile.getGroup(Y);
				if (!data.isGroupVisible(gX) || !data.isGroupVisible(gY)) continue;
				
				int x = (int)(gX.getOffset()*xPixelBP);
				int y = (int)(gY.getOffset()*yPixelBP);
				
				for (int k = 1; k <= tile.getNumBlocks(); k++) {
					ABlock blk = tile.getBlock(k);
					int rx      = x + (int)(blk.getStart(X) * xPixelBP * gX.getScale());
					int ry      = y + (int)(blk.getStart(Y) * yPixelBP * gY.getScale());
					int rwidth  = (int)((int)((blk.getEnd(X) - blk.getStart(X))) * xPixelBP * gX.getScale());
					int rheight = (int)((int)((blk.getEnd(Y) - blk.getStart(Y))) * yPixelBP * gY.getScale());
					
					g.setColor(colorBox);
					g.drawRect(rx+MARGIN, ry+MARGIN, rwidth+1, rheight+1);
					
					if (fd.isShowBlkNum()) {
						Rectangle r = new Rectangle(rx+MARGIN, ry+MARGIN, rwidth+1, rheight+1);
						drawBlockNum(g, blk, r);
					}
				}
			}
		}
	/*draw selection 
		if (isSelectingArea) { // doesn't happen in whole dot plot
			int x = Math.min(sX1,sX2);
			int y = Math.min(sY1,sY2);
			int width = Math.abs(sX1-sX2);
			int height = Math.abs(sY1-sY2);
			g.setColor(Color.BLACK);
			g.drawRect(x, y, width, height);
			g.setColor(SELECTED);
			g.fillRect(x+1, y+1, width-2, height-2);
		}
	*/
	}
	/*************************************************************************/
	//  draw hit for drawAll (similar in drawGrp)
	private void drawHit(Graphics g, DPHit ht, int x, int y, int size, double xf, double yf, 
			boolean isBlock, boolean isMix, int sizeNB) 
	{
		boolean bPct = data.getFilterData().isPctScale();
		boolean bLen = data.getFilterData().isLenScale();
		
		int s = ht.getLength() / 2; // polygon/line is length of hit
		int as = Math.abs(s);
		int hx = ht.getX(), hy = ht.getY();
		
		int x1 = x + (int)((hx - s)  * xf),	y1 = y + (int)((hy - as) * yf);
		int x2 = x + (int)((hx + s)  * xf),	y2 = y + (int)((hy + as) * yf);
	
		if (!isBlock && isMix) {
			g.drawOval((x1+x2)/2, (y1+y2)/2, sizeNB, sizeNB); 
			g.fillOval((x1+x2)/2, (y1+y2)/2, sizeNB, sizeNB); 
			return;
		}
		if (bLen) {
			if (size <= 1) {
				g.drawLine(x1,y1,x2,y2);
			}
			else { 
				double aspect = size * ((double)getHeight()/(double)getWidth()); // doesn't change
				int[] px = new int[4];
				int[] py = new int[4];
				px[0] = x1 - size;		py[0] = y1 - (int)(aspect);
				px[1] = x1 + size;		py[1] = y1 - (int)(aspect);
				px[2] = x2 + size;		py[2] = y2 + (int)(aspect);
				px[3] = x2 - size;		py[3] = y2 + (int)(aspect);
				
				// Constrain to drawing area
				px[0] = Math.max(px[0], MARGIN);			py[0] = Math.max(py[0], MARGIN);
				px[1] = Math.min(px[1], MARGIN+dim.width);	py[1] = Math.max(py[1], MARGIN);
				px[2] = Math.min(px[2], MARGIN+dim.width);	py[2] = Math.min(py[2], MARGIN+dim.height);
				px[3] = Math.max(px[3], MARGIN);			py[3] = Math.min(py[3], MARGIN+dim.height);
				
				g.fillPolygon(px, py, 4);
			}
		}
		else {
			if (bPct) size = size * ht.getScalePctid(pctidLow);
			g.drawOval((x1+x2)/2, (y1+y2)/2, size, size); 
			g.fillOval((x1+x2)/2, (y1+y2)/2, size, size); 
		}
	}

	private void drawTile(Graphics g) {
		g.setColor(Color.BLACK);
		Group gX = data.getCurrentGrp(X);
		Group gY = data.getCurrentGrp(Y);
		FilterData fd = data.getFilterData();

		g.setFont(PROJ_FONT);
		drawCentered(g, gX.getName(), MARGIN, MARGIN/2 + 10, MARGIN + dim.width, MARGIN, HORZ);
		drawCentered(g, gY.getName(), MARGIN/2 + 10, MARGIN, MARGIN, MARGIN + dim.height, HORZ);

		Rectangle rect = new Rectangle();
		Tile tile = Tile.getTile(data.getTiles(),gX,gY);
		
	// draw hits (similar to drawHit)
		DPHit hits[] = tile.getHits();
		
		boolean isMix = fd.isShowMixHits();
		int dotSizeB = dotSize(data.getDotSize());
		int dotSizeNB = (isMix) ? dotSize(1) : dotSizeB;
		
		boolean bPct = data.getFilterData().isPctScale();
		boolean bLen = data.getFilterData().isLenScale();
		
		double aspect = (double)getHeight()/(double)getWidth(); 
		
		for (int i = hits.length-1; i >= 0; --i) {
			if (hits[i] == null) continue;
			if (fd.isShowBlockHits() && !hits[i].isBlock()) continue;
			if (hits[i].getPctid() < fd.getPctid()) continue;
			
			Color c = colorHits;
			if (fd.isHighBlockHits() && hits[i].isBlock()) {
				c =  (fd.isHighGreen()) ? colorBlockHits2 : colorBlockHits1;
			}
			g.setColor(c);
			
			int s = hits[i].getLength() / 2;
			int x1 = (int)((hits[i].getX() - s) * xPixelBP) + MARGIN;
			int y1 = (int)((hits[i].getY() - Math.abs(s)) * yPixelBP) + MARGIN;
			int x2 = (int)((hits[i].getX() + s) * xPixelBP) + MARGIN;
			int y2 = (int)((hits[i].getY() + Math.abs(s)) * yPixelBP) + MARGIN;
			
			if (!hits[i].isBlock() && isMix) {
				g.drawOval((x1+x2)/2, (y1+y2)/2, dotSizeNB, dotSizeNB); 
				g.fillOval((x1+x2)/2, (y1+y2)/2, dotSizeNB, dotSizeNB); 
				continue;
			}
			int sz = dotSizeB;
			if (bLen) {
				if (sz <= 1 ) {
					g.drawLine(x1,y1,x2,y2);
				}
				else { 
					double sza = sz*aspect;
					int[] x = new int[4];
					int[] y = new int[4];
					x[0] = x1 - (int)(sz);	y[0] = y1 - (int)(sza);
					x[1] = x1 + (int)(sz);	y[1] = y1 - (int)(sza);
					x[2] = x2 + (int)(sz);	y[2] = y2 + (int)(sza);
					x[3] = x2 - (int)(sz);	y[3] = y2 + (int)(sza);
					
					// Constrain to drawing area
					x[0] = Math.max(x[0], MARGIN);			y[0] = Math.max(y[0], MARGIN);
					x[1] = Math.min(x[1], MARGIN+dim.width);y[1] = Math.max(y[1], MARGIN);
					x[2] = Math.min(x[2], MARGIN+dim.width);y[2] = Math.min(y[2], MARGIN+dim.height);
					x[3] = Math.max(x[3], MARGIN);			y[3] = Math.min(y[3], MARGIN+dim.height);
					
					g.fillPolygon(x, y, 4);
				}
			}
			else {
				if (bPct) sz = sz * hits[i].getScalePctid(pctidLow);
				g.drawOval((x1+x2)/2, (y1+y2)/2, sz, sz); 
				g.fillOval((x1+x2)/2, (y1+y2)/2, sz, sz); 
			}
		}
		
		//draw blocks; draw Number; highlight selected;
		if (fd.isShowBlocks() && tile != null){
			ABlock blk;
			for (int i = tile.getNumBlocks(); i > 0; --i) {
				blk = tile.getBlock(i);
				rect.x      = MARGIN + (int)(blk.getStart(X) * xPixelBP);
				rect.y      = MARGIN + (int)(blk.getStart(Y) * yPixelBP);
				rect.width  = MARGIN + (int)(blk.getEnd(X)   * xPixelBP) - rect.x;
				rect.height = MARGIN + (int)(blk.getEnd(Y)   * yPixelBP) - rect.y;
				
				if (blk == data.getSelectedBlock()) {
					g.setColor(SELECTED);
					g.fillRect(rect.x,rect.y,rect.width+1,rect.height+1);
				}
				
				g.setColor(colorBox);
				g.drawRect(rect.x,rect.y,rect.width+1,rect.height+1);
				
				if (fd.isShowBlkNum()) {
					drawBlockNum(g, blk, rect);
				}
			}
		}
		
		//draw dynamic selection
		if (data.hasSelectedArea()) {
			rect.x      = MARGIN + (int)(data.getX1() * xPixelBP); 
			rect.y      = MARGIN + (int)(data.getY1() * yPixelBP);
			rect.width  = MARGIN + (int)(data.getX2() * xPixelBP) - rect.x;
			rect.height = MARGIN + (int)(data.getY2() * yPixelBP) - rect.y;
			g.setColor(Color.BLACK);
			g.drawRect(rect.x, rect.y, rect.width, rect.height);
			g.setColor(SELECTED);
			g.fillRect(rect.x+1, rect.y+1, rect.width-1, rect.height-1);
		} 
		else if (isSelectingArea) {
			g.setColor(Color.BLACK);
			g.drawRect(	Math.min(sX1,sX2), Math.min(sY1,sY2), 
						Math.max(sX1,sX2)-Math.min(sX1,sX2), Math.max(sY1,sY2)-Math.min(sY1,sY2));
		}
	}
	private void drawBlockNum(Graphics g, ABlock blk, Rectangle r) {
		FontMetrics fm = g.getFontMetrics(); // fm.stringWidth(s)=8, fm.getHeight()=17
		String s = blk.getNumber() + "";
		g.setColor(Color.BLACK);
		g.setFont(BLK_FONT);
		
		int rx = r.x + (r.width/2); 
		int ry = r.y + (r.height/2);
		
		int low = MARGIN+(fm.getHeight()/2);
		if (ry<low) ry=low;
	
		g.drawString(s, rx, ry);
		g.setFont(PROJ_FONT);
	}
	
	private void drawCentered(Graphics g, String s, int x1, int y1, int x2, int y2, boolean orientation) {
		if (s == null || s.length() <= 0) return; 
		
		g.setFont(PROJ_FONT);
		FontMetrics fm = g.getFontMetrics();
		int x, y;
		
		if (orientation == HORZ) {
			x = x1 + (x2 - x1 - fm.stringWidth(s)) / 2;
			y = y1 + (y2 - y1 + fm.getAscent() - fm.getDescent()) / 2;
			g.drawString(s, x, y);
		} 
		else {
			x = x1 + (x2 - x1 - fm.getAscent() - fm.getDescent()) / 2;
			y = y1 + (y2 - y1 + fm.stringWidth(s)) / 2;

			Graphics2D g2 = (Graphics2D) g;
			TextLayout layout = new TextLayout(s, g.getFont(), g2.getFontRenderContext());
			AffineTransform saveAt = g2.getTransform();
			g2.rotate(-1 * Math.PI / 2.0, x, y);
			layout.draw(g2, x, y);
			g2.setTransform(saveAt);
		}
	}
	/**********************************************************************/
	private int dotSize(double sz) { // drawAll, drawGrp
		if (data.isTileView()) { // 2-chr
			double adjust = 1500;
			double maxavg = 400;
			double res    = 800;

			double zoom = Math.round(data.getZoomFactor() - Data.DEFAULT_ZOOM);
			double adj = Math.max(zoom * (maxavg / adjust),0);
			double max = Math.ceil(res / maxavg);
			sz += (int)Math.min(adj,max);
		}
		return (int) Math.round(sz);
	}
	
	public String getHelpText(MouseEvent e) {
		if (data.isTileView())
			return "Double-click on a blue (synteny) block, or on a region created by dragging the mouse, for the 2D view.";
		else
			return "Click on a cell in the DotPlot to zoom into the cell.";
	}
	/***********************************************************************/
	// CAS533 public void update(Observable obs, Object obj) {repaint();}
	// replaced Observable by adding 2 repaint below
	// Order called: Pressed, Released, Clicked; or Dragged,Release
	private class PlotListener implements MouseListener, MouseMotionListener {
		private PlotListener() { }

		public void mousePressed(MouseEvent evt) {
			int x = evt.getX();
			int y = evt.getY();

			if (data.hasSelectedArea() && 
				x >= Math.min(sX1, sX2) && x <= Math.max(sX1, sX2) && 
				y >= Math.min(sY1, sY2) && y <= Math.max(sY1, sY2)) 
			{
				data.show2dArea(); 
			} 
			else {
				if (data.clearSelectedArea())
					repaint(); // CAS533 add
			}
		}
		
		public void mouseClicked(MouseEvent evt) {	
			if (evt.getButton() != MouseEvent.BUTTON1) return;
			
			long x = evt.getX();
			long y = evt.getY();
			
			setDims();
			
			long bpX = (long)((x-MARGIN)/xPixelBP);
			long bpY = (long)((y-MARGIN)/yPixelBP);
			
			Utilities.setCursorBusy(Plot.this, true);  
			
			if (data.isTileView()) { // 2d-view (was isZoomed)
				if (data.selectBlock(bpX, bpY)) // selects the block if point is in a block
					repaint(); // CAS533 add
			}
			else if (x >= MARGIN && x <= MARGIN+dim.width 	// click within boundaries
				  && y >= MARGIN && y <= MARGIN+dim.height) // change to tile view
			{
				if (data.selectTile(bpX, bpY)) 
					repaint(); // CAS533 add
			}
			Utilities.setCursorBusy(Plot.this, false); 
		}
		
		public void mouseReleased(MouseEvent arg0) {
			if (!isSelectingArea)  return;
			
			isSelectingArea = false;
			if (sX1 > sX2) {int temp = sX1; sX1 = sX2; sX2 = temp;}
			if (sY1 > sY2) {int temp = sY1; sY1 = sY2; sY2 = temp;}
			
			if (data.isTileView()) {
				data.selectArea(dim,sX1-MARGIN,sY1-MARGIN,sX2-MARGIN,sY2-MARGIN); 
				repaint();
			}
		}
		public void mouseDragged(MouseEvent e) {
			if (!data.isTileView()) return;
			
			sX2 = e.getX();
			sY2 = e.getY();

			if (!isSelectingArea) {
				isSelectingArea = true;
				sX1 = sX2;
				sY1 = sY2;
			}
			repaint();
		}
		public void mouseEntered(MouseEvent e) { }
		public void mouseExited(MouseEvent e)  { }
		public void mouseMoved(MouseEvent e)   { }
	}
	/****************************************************************/
	// CAS533 removed dotplot.properties and hardcoded here
	
	private static final Color FAR_BACKGROUND = 	Color.white;
	private static final Color BACKGROUND =		 	Color.white;
	private static final Color BACKGROUND_BORDER = 	Color.black;
	private static final Color SELECTED = 			new Color(247,233,213,200);
	private static final Font  PROJ_FONT = 			new Font("Arial",0,14);
	private static final Font  BLK_FONT = 			new Font("Arial",0,12);

	private static final Color colorBox = Color.blue;
	private static final Color colorBlockHits1 = Color.blue;
	private static final Color colorBlockHits2 = new Color(0,200,85,255); // red, green, blue alpha
	private static final Color colorHits = Color.BLACK;
	
	private static int  MARGIN = 50;
	private static final boolean HORZ = true, VERT   = false;
}
