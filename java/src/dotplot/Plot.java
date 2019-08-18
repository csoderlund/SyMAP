/**
 *
 * First draft by marti@email.arizona.edu. 
 * Rewritten by Austin Shoemaker.
 *
 */
package dotplot;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.font.TextLayout;

import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;

import javax.swing.JPanel;
import javax.swing.JScrollPane;

import util.PropertiesReader;
import util.Utilities;
import symap.frame.HelpListener;
import symap.frame.HelpBar;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class Plot extends JPanel implements Observer, DotPlotConstants,
											HelpListener // mdb added 7/6/09
{
	private static final boolean DEBUG = false;
	
	private static final double DOT_ADJUST_FACTOR = 1500;
	private static final double DOT_MAX_FACTOR    = 400;
	private static final double DEFAULT_AVG_RES   = 800;

	private static final Color FAR_BACKGROUND;
	private static final Color BACKGROUND;
	private static final Color BACKGROUND_BORDER;
	private static final Color MRK_HIT_COLOR;
	private static final Color BES_HIT_COLOR;
	private static final Color FP_HIT_COLOR;
	private static final Color SELECTED;
	private static final Color CONTIG;
	private static final Font PROJ_FONT;
	private static final Font GRP_FONT;

	public static final Color[] BLOCK_HITS_COLORS;
	public static final Color SHARED_BLOCK_HITS_COLOR;

	static {
		PropertiesReader props = new PropertiesReader(Plot.class.getResource(DotPlot.DOTPLOT_PROPS));
		FAR_BACKGROUND        = props.getColor("farBackgroundColor");
		BACKGROUND            = props.getColor("backgroundColor");
		BACKGROUND_BORDER     = props.getColor("backgroundBorderColor");
		MRK_HIT_COLOR         = props.getColor("mrkHitColor");
		BES_HIT_COLOR         = props.getColor("besHitColor");
		FP_HIT_COLOR          = props.getColor("fpHitColor");
		SELECTED              = props.getColor("selectedColor");
		CONTIG                = props.getColor("contigColor");
		BLOCK_HITS_COLORS       = (Color[])props.getColors("blockHitsColors").toArray(new Color[0]);
		SHARED_BLOCK_HITS_COLOR = props.getColor("sharedBlockHitsColor"); 
		PROJ_FONT 				= props.getFont("projFont");
		GRP_FONT 				= props.getFont("grpFont");
	}

	private static int     MARGIN     = 50;
	private static final boolean HORIZONTAL = true;
	private static final boolean VERTICAL   = false;

	private Data data;
	private JScrollPane scroller;
	private Dimension dim;
	private int x1, x2, y1, y2; // coordinates of selected region 
	private double xPixelPerBP, yPixelPerBP;
	private double xPixelPerGrp, yPixelPerGrp;
	private boolean isSelectingArea = false;
	private Point center;
	private double avgRes = DEFAULT_AVG_RES;
	
	public Plot(Data d, HelpBar hb) {
		super(null);

		dim  = new Dimension();
		scroller = new JScrollPane(this);

		data = d;
		
		setBackground(FAR_BACKGROUND);

		scroller.setBackground(FAR_BACKGROUND);
		scroller.getViewport().setBackground(FAR_BACKGROUND);
		scroller.getVerticalScrollBar().setUnitIncrement(10); 

		data.addObserver(this);
		PlotListener l = new PlotListener();
		addMouseListener(l);
		addMouseMotionListener(l);
		
		hb.addHelpListener(this); 
	}

	public String getHelpText(MouseEvent e) {
		if (data.isZoomed())
			return "Double-click on a blue (synteny) block, or on a region created by dragging the mouse, to open a detailed view.";
		else
			return "Click on a dot plot cell to zoom in.";
	}
	
	public void setScreenBounds(Rectangle bounds) {
		avgRes = avgResolution(bounds);
	}

	private static double avgResolution(Rectangle screenBounds) {
		if (screenBounds == null) return DEFAULT_AVG_RES;
		return screenBounds.width + screenBounds.height / 2.0;
	}

	private int dotSize() {
		int s = data.getDotSize();
		if (data.isZoomed()) {
			double adj = Math.max(Math.round((data.getZoomFactor() - Data.DEFAULT_ZOOM) * (avgRes / DOT_ADJUST_FACTOR)),0);
			double max = Math.ceil(avgRes / DOT_MAX_FACTOR);
			s += (int)Math.min(adj,max);
		}
		if (DEBUG) System.out.println("dotSize: "+s+" "+data.isZoomed());
		return s;
	}

	private Point getCenter() {
		Point p = null;
		if (scroller != null && scroller.getViewport() != null) {
			Rectangle r = scroller.getViewport().getViewRect();
			int x = r.x + (r.width/2);
			int y = r.y + (r.height/2);
			p = new Point((int)((x-MARGIN)/xPixelPerBP),(int)((y-MARGIN)/yPixelPerBP));
		}
		return p;
	}

	private Point toActual(Point p) {
		if (p == null)
			return null;
	
		int x = (int)(p.x * xPixelPerBP + MARGIN);
		int y = (int)(p.y * yPixelPerBP + MARGIN);
		return new Point(x, y);
	}

	private Rectangle toActual(Rectangle r) {
		Rectangle a = null;
		if (r != null) {
			a = new Rectangle();	    
			a.x = (int)(r.x * xPixelPerBP + MARGIN);
			a.y = (int)(r.y * yPixelPerBP + MARGIN);
			a.width  = (int)(r.width  * xPixelPerBP);
			a.height = (int)(r.height * yPixelPerBP);
		}
		return a;
	}

	private Rectangle toFullRect(Point center) {
		Rectangle v = getScrollPane().getViewport().getViewRect();
		return new Rectangle(center.x - (v.width/2), center.y - (v.height/2), v.width, v.height);
	}
	
	public void saveCenter() {
		center = getCenter();
	}

	public void restoreCenter() {
		if (center != null) {
			setDims();

			/* hack for zooming out for scrollRectToVisible() */
			setPreferredSize(new Dimension(getPreferredSize().width*2, getPreferredSize().height*2));

			revalidate();
			scroller.getViewport().revalidate();

			Point p = toActual(center);
			center = null;
			scrollRectToVisible(toFullRect(p));

			if (data.isZoomed()) {
				Rectangle r = null;
				if (data.hasSelectedArea()) {
					r = toActual(new Rectangle((int)data.getX1(),
							(int)data.getY1(),
							(int)data.getX2()-(int)data.getX1(),
							(int)data.getY2()-(int)data.getY1()));
				}
				else if (data.getSelectedBlock() != null) {
					r = toActual(data.getSelectedBlock().getBounds());
				}

				if (r != null /*&& !viewIntersects(r)*/) {
					// if r isn't in the view, try to center on r
					//p = new Point(r.x+(r.width>>1),r.y+(r.height>>1));
					//p = toFull(p);

					scrollRectToVisible(r);
				}
			}

			setDims();
		}
	}

	public JScrollPane getScrollPane() {
		return scroller;
	}

	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		
		if (!data.canPaint()) return ;
		setDims();
		if (data.isCentering()) return ;
		
		Group[] xGroups = data.getVisibleGroups(X);
		Group[] yGroups = data.getVisibleGroupsY(xGroups);
//		Group.setScaleFactors(xGroups);
//		Group.setScaleFactors(data.getVisibleGroupsY(xGroups));

		Group.setOffsets(xGroups);
		Group.setOffsets(yGroups);

		int plotStartX = MARGIN; //(data.isZoomed() ? MARGIN : Math.max(MARGIN,maxYGrpName)); 
		
		//draw base
		g.setColor(BACKGROUND);
		g.fillRect(plotStartX + 1, MARGIN + 1, dim.width - 1, dim.height - 1);
		g.setColor(BACKGROUND_BORDER);
		g.drawRect(plotStartX, MARGIN, dim.width, dim.height);

		//draw titles
		drawCentered(g, data.getProject(X).getDisplayName(),
				plotStartX, MARGIN / 4 + 10,
				plotStartX + dim.width, MARGIN / 2,
				HORIZONTAL);
		if (data.isZoomed()) {
			drawCentered(g, data.getCurrentProj().getDisplayName(), 
					MARGIN / 2 + 10, MARGIN, 
					MARGIN / 2, MARGIN + dim.height, 
					VERTICAL);
		}
		else { // mdb added 12/16/09 #205 #207
			long offset = 0;
			for (int i = 1;  i < data.getNumProjects();  i++) {
				Project p = data.getProject(i);
				int y = MARGIN + (int)(offset * yPixelPerBP);
				long size = data.getVisibleGroupsSize(i, xGroups);
				if (size > 0) {
					drawCentered(g, p.getDisplayName(),
							MARGIN / 2 + 10, y,
							MARGIN / 2, y + (int)(size * yPixelPerBP),
							VERTICAL);
					g.setColor(BACKGROUND_BORDER);
					if (i > 1) g.drawLine(MARGIN-17, y, MARGIN, y);
					offset += size;
				}
			}
		}
		
		//draw status message
		String statusString = getStatusString();
		if (statusString != null)
			g.drawString(statusString, MARGIN / 2, MARGIN / 2);

		//draw blocks & hits
		if (!data.isZoomed()) drawAll(g);
		else	              drawGrp(g);
	}

	protected String getStatusString() {
		return (data.isLoading() ? "Loading data ..." : null);
	}

	public void setDims() {
		double zoom  = data.getZoomFactor();

		Dimension d = new Dimension( (int)(scroller.getWidth() * zoom), 
				                     (int)(scroller.getHeight() * zoom) );

		setPreferredSize(d);
		dim.width =  (int)((d.width - (MARGIN * 1.1)));
		dim.height = (int)((d.height - (MARGIN * 1.2)));
		

		if (data.getProject(X) == null || data.getProject(Y) == null) 
			return;

		if (data.isZoomed()) {
			xPixelPerBP = dim.getWidth()  / data.getCurrentGrpSize(X);
			yPixelPerBP = dim.getHeight() / data.getCurrentGrpSize(Y);
		}
		else {
			xPixelPerBP = dim.getWidth()  / data.getVisibleGroupsSize(X);
			yPixelPerBP = dim.getHeight() / data.getVisibleGroupsSizeY(data.getVisibleGroups(X));
		}

		xPixelPerGrp = 0;
		yPixelPerGrp = 0;
		if (data.isScaled()) 
		{
			yPixelPerBP = xPixelPerBP * data.getScaleFactor();
			dim.height = (int)(yPixelPerBP * data.getVisibleGroupsSizeY(data.getVisibleGroups(X)));
			d.height   = (int)(dim.height + (MARGIN * 1.2));
			setPreferredSize(d);
		}

		revalidate();
		scroller.getViewport().revalidate();
	}
	
	private void drawAll(Graphics g) { 
		FilterData fd = data.getFilterData();
		Group[] xGroups = data.getVisibleGroups(X);
		Group[] yGroups = data.getVisibleGroupsY(xGroups);
		
		// Figure out the longest group names
		int maxYGrpName = 0;
		int sumYGrpName = 0;
		FontMetrics mtc = g.getFontMetrics(g.getFont());		
		for (Group grp : yGroups)
		{
			int w = mtc.stringWidth(grp.getName());
			sumYGrpName += w;
			if (w > maxYGrpName)
			{
				maxYGrpName = w;	
			}
		}
		int maxXGrpName = 0;
		for (Group grp : xGroups)
		{
			int w = mtc.stringWidth(grp.getName());
			if (w > maxXGrpName)
			{
				maxXGrpName = w;	
			}
		}	
		//System.out.println(maxXGrpName + " " + maxYGrpName);
		int plotStartX = MARGIN; //Math.max(MARGIN,maxYGrpName); 
		//draw groups
		// we have to be careful how we do things to avoid roundoff discrepancies from the scale factors
		for (Group grp : xGroups) {
			int x1 = plotStartX + (int)(grp.getOffset() * xPixelPerBP );
			int x2 = x1 + (int)((int)(grp.getSize()  * grp.getScaleFactor()) * xPixelPerBP); // use this order to match calc of grp offsets
			drawCentered(g, grp.getName(), x1, MARGIN/2 + 10, x2, MARGIN, HORIZONTAL);
			if (grp != xGroups[xGroups.length-1]) g.drawLine(x2, MARGIN, x2, dim.height + MARGIN);
		}
		for (Group grp : yGroups) {
			int y1 = MARGIN + (int)(grp.getOffset() * yPixelPerBP );
			int y2 = y1 + (int)((int)(grp.getSize() * grp.getScaleFactor())*yPixelPerBP) ;
			//if (sumYGrpName > 800 || maxYGrpName > 40)
			//{
				drawCentered(g, grp.getName(), MARGIN/2, y1, plotStartX, y2, HORIZONTAL);
			//}
			//else
			//{
			//	drawCentered(g, grp.getName(), MARGIN/2 + 25, y1, plotStartX, y2, VERTICAL);
			//}
			if (grp != yGroups[yGroups.length-1]) g.drawLine(plotStartX, y2, dim.width + plotStartX, y2);
		}
	
		
		//draw blocks
		if (fd.isShowBlocks()) {
			for (Tile tile : data.getTiles()) {
				if (tile.isSomeLoaded()) {
					Group gX = tile.getGroup(X);
					Group gY = tile.getGroup(Y);
					if (data.isGroupVisible(gX) && data.isGroupVisible(gY)) {
						int x = (int)(gX.getOffset()*xPixelPerBP);
						int y = (int)(gY.getOffset()*yPixelPerBP);
						for (int k = 1; k <= tile.getNumIBlocks(); k++) {
							InnerBlock ib = tile.getIBlock(k);
							int rx      = x + (int)(ib.getStart(X) * xPixelPerBP * gX.getScaleFactor());
							int ry      = y + (int)(ib.getStart(Y) * yPixelPerBP * gY.getScaleFactor());
							int rwidth  = (int)((int)((ib.getEnd(X) - ib.getStart(X))) * xPixelPerBP * gX.getScaleFactor());
							int rheight  = (int)((int)((ib.getEnd(Y) - ib.getStart(Y))) * yPixelPerBP * gY.getScaleFactor());
							
							//int rx      = (int)((x + ib.getStart(X)) * xPixelPerBP * gX.getScaleFactor());
							//int ry      = (int)((y + ib.getStart(Y)) * yPixelPerBP * gY.getScaleFactor());
							//int rwidth  = (int)((x + ib.getEnd(X)) * xPixelPerBP * gX.getScaleFactor()) - rx;
							//int rheight = (int)((y + ib.getEnd(Y)) * yPixelPerBP * gY.getScaleFactor()) - ry;
							g.setColor(getBlockColor(data,tile,0,(ABlock)ib,fd));
							g.drawRect(rx+plotStartX, ry+MARGIN, rwidth, rheight);
						}
					}
				}
			}
		}
		
		//draw hits
		int dotsize = dotSize();
		for (Tile tile : data.getTiles()) {
			Group gX = tile.getGroup(X);
			Group gY = tile.getGroup(Y);
			if (data.isGroupVisible(gX) && data.isGroupVisible(gY)) {
				int x = plotStartX + (int)(gX.getOffset()*xPixelPerBP);
				int y = MARGIN + (int)(gY.getOffset()*yPixelPerBP);
				if (tile.isSomeLoaded()) {
					for (Hit h : tile.getHits()) {
						if ((!fd.isShowBlockHits() || h.isBlock()) && !h.isRepetitive()) {
							int type = h.getType();
							if (fd.getHide(type) || h.getPctid() < fd.getPctid(type) 
									|| h.getEvalue() > fd.getEvalue(type)) continue;
							g.setColor(getHitColor(type,h,fd));
							drawHit(g, h, x, y, dotsize,gX.getScaleFactor(), gY.getScaleFactor());
						}
					}
				}
			}
		}
		
		//draw selection 
		if (isSelectingArea) {
			int x = Math.min(x1,x2);
			int y = Math.min(y1,y2);
			int width = Math.abs(x1-x2);
			int height = Math.abs(y1-y2);
			g.setColor(Color.BLACK);
			g.drawRect(x, y, width, height);
			g.setColor(SELECTED);
			g.fillRect(x+1, y+1, width-2, height-2);
		}
	}
	
	//  draw hit as line or polygon
	private void drawHit(Graphics g, Hit h, int x, int y, int size, float xFact, float yFact) {
		int s = h.getLength() / 2;
		int x1 = x + (int)((h.getX() - s)*xPixelPerBP*xFact);
		int y1 = y + (int)((h.getY() - Math.abs(s))*yPixelPerBP*yFact);
		int x2 = x + (int)((h.getX() + s)*xPixelPerBP*xFact);
		int y2 = y + (int)((h.getY() + Math.abs(s))*yPixelPerBP*yFact);
		if (size <= 1)
			g.drawLine(x1,y1,x2,y2);
		else { // works as intended, but could be improved
			// Scale dot size
			double aspect = (double)getHeight()/(double)getWidth();
			int[] px = new int[4];
			int[] py = new int[4];
			px[0] = x1 - size;
			py[0] = y1 - (int)(size*aspect);
			px[1] = x1 + size;
			py[1] = y1 - (int)(size*aspect);
			px[2] = x2 + size;
			py[2] = y2 + (int)(size*aspect);
			px[3] = x2 - size;
			py[3] = y2 + (int)(size*aspect);
			
			// Constrain to drawing area
			px[0] = Math.max(px[0], MARGIN);
			px[1] = Math.min(px[1], MARGIN+dim.width);
			px[2] = Math.min(px[2], MARGIN+dim.width);
			px[3] = Math.max(px[3], MARGIN);
			py[0] = Math.max(py[0], MARGIN);
			py[1] = Math.max(py[1], MARGIN);
			py[2] = Math.min(py[2], MARGIN+dim.height);
			py[3] = Math.min(py[3], MARGIN+dim.height);
			  
			g.fillPolygon(px, py, 4);
		}
	}

	private void drawGrp(Graphics g) {
		g.setColor(Color.BLACK);
		Group gX = data.getCurrentGrp(X);
		Group gY = data.getCurrentGrp(Y);
		FilterData fd = data.getFilterData();

		g.setFont(GRP_FONT);
		drawCentered(g, gX.getName(), MARGIN, MARGIN / 2 + 10, MARGIN + dim.width, MARGIN, HORIZONTAL);
		drawCentered(g, gY.getName(), MARGIN / 2 + 10, MARGIN, MARGIN, MARGIN + dim.height, HORIZONTAL);

		Rectangle rect = new Rectangle();
		FontMetrics fm;

		//draw contig lines
		if (fd.isShowContigs()) {
			Iterator<Contig> iter = gY.getContigList().iterator();
			Contig contig;
			while (iter.hasNext()) {
				contig = (Contig)iter.next();
				rect.y = MARGIN + (int)(contig.getCCB() * yPixelPerBP);
				if (rect.y == MARGIN) rect.y++;
				g.setColor(CONTIG);
				g.drawLine(MARGIN + 1, rect.y, dim.width + MARGIN - 1, rect.y);
				g.setColor(Color.black);	
				fm = g.getFontMetrics();
				g.drawString(new Integer(contig.getNumber()).toString(), MARGIN, rect.y+fm.getAscent());
			}

			iter = gX.getContigList().iterator(); // if not FPC, there won't be any elements in the list
			while (iter.hasNext()) {
				contig = (Contig)iter.next();
				rect.x = MARGIN + (int)(contig.getCCB() * xPixelPerBP);
				if (rect.x == MARGIN) rect.x++;
				g.setColor(CONTIG);
				g.drawLine(rect.x,MARGIN + 1,rect.x,dim.height + MARGIN - 1);
				g.setColor(Color.black);	
				fm = g.getFontMetrics();
				g.drawString(new Integer(contig.getNumber()).toString(),rect.x,MARGIN+fm.getAscent());
			}
		}

		Tile tile = Tile.getTile(data.getTiles(),gX,gY);
		//draw blocks
		if (fd.isShowBlocks() && tile != null 
				&& (!data.isOnlyShowBlocksWhenHighlighted() || fd.isHighlightBlockHits()))
		{
			InnerBlock ib;
			for (int i = tile.getNumIBlocks(); i > 0; --i) {
				ib = tile.getIBlock(i);
				rect.x      = MARGIN + (int)(ib.getStart(X) * xPixelPerBP);
				rect.y      = MARGIN + (int)(ib.getStart(Y) * yPixelPerBP);
				rect.width  = MARGIN + (int)(ib.getEnd(X)   * xPixelPerBP) - rect.x;
				rect.height = MARGIN + (int)(ib.getEnd(Y)   * yPixelPerBP) - rect.y;
				if (ib == data.getSelectedBlock()) {
					g.setColor(SELECTED);
					g.fillRect(rect.x,rect.y,rect.width,rect.height);
				}
				g.setColor(getBlockColor(data,tile,0,(ABlock)ib,fd));
				g.drawRect(rect.x,rect.y,rect.width,rect.height);
			}
		}

		//draw dynamic selection
		if (data.hasSelectedArea()) {
			rect.x      = MARGIN + (int)(data.getX1() * xPixelPerBP); 
			rect.y      = MARGIN + (int)(data.getY1() * yPixelPerBP);
			rect.width  = MARGIN + (int)(data.getX2() * xPixelPerBP) - rect.x;
			rect.height = MARGIN + (int)(data.getY2() * yPixelPerBP) - rect.y;
			g.setColor(Color.BLACK);
			g.drawRect(rect.x, rect.y, rect.width, rect.height);
			g.setColor(SELECTED);
			g.fillRect(rect.x+1, rect.y+1, rect.width-1, rect.height-1);
		} 
		else if (isSelectingArea) {
			g.setColor(Color.BLACK);
			g.drawRect(	Math.min(x1,x2), 
						Math.min(y1,y2), 
						Math.max(x1,x2)-Math.min(x1,x2),
						Math.max(y1,y2)-Math.min(y1,y2));
		}

		if (tile == null || !tile.isSomeLoaded()) return ;
		Hit hits[] = tile.getHits();

		//draw hits
		int dotsize = dotSize();
		double aspect = (double)getHeight()/(double)getWidth(); // mdb added 2/29/08 #149
		for (int i = hits.length-1; i >= 0; --i) {
			if (hits[i] != null && ((!fd.isShowBlockHits() || hits[i].isBlock()) && !hits[i].isRepetitive()) || fd.isShowAllHits()) {
				int t = hits[i].getType();
				if (fd.getHide(t) || hits[i].getPctid() < fd.getPctid(t) || hits[i].getEvalue() > fd.getEvalue(t)) continue;
				g.setColor(getHitColor(t,hits[i],fd));
				
				
				// mdb added 2/29/08 #149 - draw hit as line or polygon -- BEGIN
				int s = hits[i].getLength() / 2;
				int x1 = (int)((hits[i].getCoord(X) - s)*xPixelPerBP)+MARGIN;
				int y1 = (int)((hits[i].getCoord(Y) - Math.abs(s))*yPixelPerBP)+MARGIN;
				int x2 = (int)((hits[i].getCoord(X) + s)*xPixelPerBP)+MARGIN;
				int y2 = (int)((hits[i].getCoord(Y) + Math.abs(s))*yPixelPerBP)+MARGIN;
				if (dotsize <= 1)
					g.drawLine(x1,y1,x2,y2);
				else { // mdb: works as intended, but could be improved // FIXME: moved into drawHit() like drawAll()
					// Scale dot size
					int[] x = new int[4];
					int[] y = new int[4];
					x[0] = x1 - (int)(dotsize);
					y[0] = y1 - (int)(dotsize*aspect);
					x[1] = x1 + (int)(dotsize);
					y[1] = y1 - (int)(dotsize*aspect);
					x[2] = x2 + (int)(dotsize);
					y[2] = y2 + (int)(dotsize*aspect);
					x[3] = x2 - (int)(dotsize);
					y[3] = y2 + (int)(dotsize*aspect);
					
					// Constrain to drawing area
					x[0] = Math.max(x[0], MARGIN);
					x[1] = Math.min(x[1], MARGIN+dim.width);
					x[2] = Math.min(x[2], MARGIN+dim.width);
					x[3] = Math.max(x[3], MARGIN);
					y[0] = Math.max(y[0], MARGIN);
					y[1] = Math.max(y[1], MARGIN);
					y[2] = Math.min(y[2], MARGIN+dim.height);
					y[3] = Math.min(y[3], MARGIN+dim.height);
					
					g.fillPolygon(x, y, 4);
				}
				// mdb added 2/29/08 #149 - draw hit as line or polygon -- END
			}
		}
	}

	private void drawCentered(Graphics g, String s, int x1, int y1, int x2, int y2, boolean orientation) {
		if (s == null || s.length() <= 0) return; // mdb added 7/25/07
		
		g.setFont(PROJ_FONT);
		FontMetrics fm = g.getFontMetrics();
		int x, y;
		
		if (orientation == HORIZONTAL) {
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

	public void update(Observable obs, Object obj) {
		repaint();
	}

	private class PlotListener implements MouseListener, MouseMotionListener {
		private PlotListener() { }

		public void mouseClicked(MouseEvent evt) {
			long x = evt.getX();
			long y = evt.getY();

			if (evt.getButton() == MouseEvent.BUTTON1) {
				setDims();
				
				long bpX = (long)((x-MARGIN)/xPixelPerBP);
				long bpY = (long)((y-MARGIN)/yPixelPerBP);

				if (data.isZoomed()) {
					Utilities.setCursorBusy(Plot.this, true);  // mdb added 1/14/09
					data.selectBlock(bpX, bpY);
					Utilities.setCursorBusy(Plot.this, false); // mdb added 1/14/09
				}
				else if (x >= MARGIN
							&& x <= MARGIN+dim.width 
							&& y >= MARGIN 
							&& y <= MARGIN+dim.height) 
				{
					data.selectTile(bpX, bpY);
				}
			}
		}

		public void mousePressed(MouseEvent evt) {
			int x = evt.getX();
			int y = evt.getY();

			if (data.hasSelectedArea() && 
					x >= Math.min(x1, x2) && x <= Math.max(x1, x2) && 
					y >= Math.min(y1, y2) && y <= Math.max(y1, y2)) 
			{
				data.zoomArea();
			} 
			else
				data.clearSelectedArea();
		}

		public void mouseReleased(MouseEvent arg0) {
			if (isSelectingArea) {
				isSelectingArea = false;
				
				if (x1 > x2) {
					int temp = x1;
					x1 = x2; 
					x2 = temp;
				}
				if (y1 > y2) {
					int temp = y1;
					y1 = y2; 
					y2 = temp;
				}
				
				if (data.isZoomed())
					data.selectArea(dim,x1-MARGIN,y1-MARGIN,x2-MARGIN,y2-MARGIN);
				else { 
					Rectangle r = scroller.getViewport().getViewRect();
					if (r.x < MARGIN) { x1 -= MARGIN-r.x; x2 -= MARGIN-r.x; }
					if (r.y < MARGIN) { y1 -= MARGIN-r.y; y2 -= MARGIN-r.y; }
					int x = r.x + x1 + (x2-x1+1)/2;
					int y = r.y + y1 + (y2-y1+1)/2;
					center = new Point((int)(x/xPixelPerBP),(int)(y/yPixelPerBP));
					
					data.setZoom( dim.getWidth()/Math.max(x2-x1+1, y2-y1+1) );

					setDims();
					
					// Hack for zooming out for scrollRectToVisible():
					setPreferredSize(new Dimension(getPreferredSize().width*2, getPreferredSize().height*2));

					revalidate();
					scroller.getViewport().revalidate();

					Point p = toActual(center);
					scrollRectToVisible(toFullRect(p));
					
					setDims();
				}

				repaint();
			}
		}
		
		public void mouseDragged(MouseEvent e) {
				x2 = e.getX();
				y2 = e.getY();

				if (!isSelectingArea) {
					isSelectingArea = true;
					x1 = x2;
					y1 = y2;
				}
				repaint();
		}

		public void mouseEntered(MouseEvent e) { }
		public void mouseExited(MouseEvent e)  { }
		public void mouseMoved(MouseEvent e)   { }
	}
	
	private static Color getBlockColor(Data data, Tile b, int altNum, ABlock ab, FilterData fd) {
		for (int i = 0; i < DotPlot.TOT_RUNS; i++) {
			if (i == altNum || (data.isOnlyShowBlocksWhenHighlighted() && !fd.isHighlightBlockHits(i))) continue;
			if (b.hasMatchingBlock(i,ab)) return fd.getSharedHitsColor();
		}
		return fd.getBlockHitsColor(altNum);
	}

	private static Color getHitColor(int hitType, Hit hit, FilterData fd) {
		for (int i = 0; i < DotPlot.TOT_RUNS; i++) {
			if (hit.isBlock(i) && fd.isHighlightBlockHits(i)) {
				for (int j = i+1; j < DotPlot.TOT_RUNS; j++) {
					if (fd.isHighlightBlockHits(j) && hit.isBlock(j))
						return fd.getSharedHitsColor();
				}
				return fd.getBlockHitsColor(i);
			}
		}
		switch (hitType) {
			case MRK: return MRK_HIT_COLOR;
			case BES: return BES_HIT_COLOR;
			case FP:  return FP_HIT_COLOR;
		}
		return null;
	}
}
