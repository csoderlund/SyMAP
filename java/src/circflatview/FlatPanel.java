package circflatview;

import java.sql.ResultSet;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.util.Vector;
import java.util.TreeMap;

import database.DBconn2;
import props.PersistentProps;
import props.PropertiesReader;
import symap.Globals;
import symap.frame.HelpBar;
import symap.frame.HelpListener;
import symap.manager.Mproject;
import util.ErrorReport;
import util.Utilities;

/**************************************************************
 * Draws linear view -- this is copied from Circle view and changed to show it flat
 * Remove Rotate Circle/Text. No Self-align.
 * Keep +/-/scale, All Blocks, Reverse, Colors. All bar (arc) and Name clicks stay.
 * Add Reorder: (1) Order by chr# (2) Order by best match.
 * 
 * No 2D; 2 projects only; no isSelf (tried on 4/28/26 - too messy)
 * CAS575 Moved Group Load to Zinit, renamed some stuff
 */
public class FlatPanel extends JPanel implements HelpListener, MouseListener,MouseMotionListener {
	private static final long serialVersionUID = -1711255873365895293L;
	private static boolean bTrace=false;
	
	static private String propName="SyMAPlinear"; // Does not share saved colors with Circle
	static private final int dispProjWidth=740,    // total length of the project*zoom; screen is >800
							 projStartX=40, 		   // starting point of each project,
							 projStart1Y=50,		   // Y for first project
							 projIncY=200,     // inc Y for subsequent projects
							 barHeight = 15,   // chr bar height
							 barGap = 1; 	   // gap between bar and block
	
	// ControlPanelCirc has Info popup with this 
	private int cntAllBlks=0, cntInvBlks=0, cntChrHidden=0, cntBlkHidden=0;
	  		
	private final String helpName = "Click at the start of the project name to use its colors for the blocks.";
	private final String helpBar =  "Click a bar to bring its blocks to the top."
								+ "\nDouble click a bar to only show its blocks.\n";
	protected final double ZOOM_DEFAULT = 1.0; 		
	
	 // set from ControlPanelFlat 
    protected int lenChoice = 0, hitChoice = 0;  // len 0, 50k, 1M, 2M; hits 0, 50, 100, 200
    protected boolean bToScale = false;			// genome size
    protected double zoom = ZOOM_DEFAULT;		// +/- icons
    
    // set from ControlPanelFlat.Display -- same defaults; Project.bShowChrLabel is Display
    protected int blkChoice = 0; 	// Block: All, Two-color, Non-invert, Invert
    protected boolean bInvBlk = true, bInvStroke=false; // Invert orientation of inverted; outline the inverted
    protected int chrChoice = 0;	// Chr: All, Has Blocks, Has Visible Blocks
    protected int slantChoice = 0; 	// Label: 0-5 (sets slantRad at same time)
    protected int swapChoice = 0; 	// None, Species, Colors, Species&Colors 
    
    private final int NONE=0, SPECIES=1, COLORS=2, BOTH=3;
    protected double slantRad = 0; 	// Label: 0, -45,-90,-125, -190 
    protected int maxNumGrps = 0;	// set in Zinit for ControlPanelFlat
    		
    // ControlPanelFlat color popup; default values, if changed, changed in Default button; saved in .symap_props
    protected int colorSet=1;					// ColorSet 1 or 2;
    protected boolean bOrder=false, bRevOrder=false, bShuffle=false, bScaleColors=false;				
    protected double scaleColors=0.8, scaleGrpLen=0.3;			
    protected int seed = 1;					
    
    private FlatFrame frame;
	
	private Vector<Integer> colorVec;
	
	private Vector<Project> allProjVec = new Vector <Project> ();			// 2 projects - order displayed
	private Vector<Project> savProjVec = new Vector <Project> ();			// 2 projects - original order - set once
	private TreeMap <Integer,Group> idx2GrpMap = new TreeMap <Integer,Group> (); // all groups
	private Vector <Block> allBlkVec = new Vector <Block> ();				// CAS575 chg from groups.block vec
	private int selectRefIdx;		// The selected project, can be on top or bottom; only changes text
	
    private JScrollPane scroller;		
    private HelpBar helpPanel = null;
    
    private Zinit initObj;
    private boolean isSelf = false;	// not working
    
    private void dprt(String msg) {symap.Globals.prt("FP: " + msg);}
    
	public FlatPanel(FlatFrame frame, DBconn2 dbc2, HelpBar hb, int[] projIdxList, int refIdx) { 
		if (projIdxList==null || projIdxList.length<=1 || projIdxList[0]==projIdxList[1]) {
			Globals.prt("Incorrect project set");
			return;
		}
		
		this.frame = frame;
		this.helpPanel = hb;
		setBackground(Color.white);
		
		scroller = new JScrollPane(this);
		scroller.setBackground(Color.white);
		scroller.getViewport().setBackground(Color.white);
		scroller.getVerticalScrollBar().setUnitIncrement(10); 
		
		helpPanel.addHelpListener(this); 
		addMouseListener(this);
		addMouseMotionListener(this);
		
		try {
			initObj = new Zinit();
		
			if (!initObj.initDS(dbc2, projIdxList, refIdx)) return; // create allProjVec, idx2GrpMap, priortyVec
		}
		catch(Exception e) {ErrorReport.print(e, "Create CircPanel");}
	}
	
	protected JScrollPane getScrollPane() {return scroller;}
	
	
	/***************************************************
	 * XXX Mouse
	 */
	public void handleClick(long x, long y, boolean dbl) {}
	
	public void mouseClicked(MouseEvent evt)  { // Click arc or project name
		int xRel = (int)evt.getX();
		int yRel = (int)evt.getY();
		boolean dbl = (evt.getClickCount() > 1);
		
		Group grp = overGroupBar(xRel, yRel);
		if (grp != null) { // clicked the group arc
			for (Group g1 : idx2GrpMap.values()) {
				g1.onTop = false;
				g1.onlyShow = false;
				
				if (g1 == grp) {
					g1.onTop = true;
					if (dbl) g1.onlyShow = true;
				}
			}
			makeRepaint();
		}
		else {
			Project p = overProjName((int)evt.getX(), (int)evt.getY());
			if (p != null) {
				selectRefIdx = p.idx;
				makeRepaint();	
			}
		}
	}
	
	public void mouseMoved(MouseEvent e)   { 
		if (helpPanel == null) return;
		
		int xRel = (int)e.getX();
		int yRel = (int)e.getY();
		Group grp = overGroupBar(xRel,yRel);

		if (grp != null) {
			setCursor( Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) );
			helpPanel.setHelp(helpBar,this); 
		}
		else {
			Project p = overProjName((int)e.getX(), (int)e.getY());
			if (p != null) {
				setCursor( Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) );
				helpPanel.setHelp(helpName, this);
			}
			else{
				//setCursor( Cursor.getDefaultCursor() ); // CAS584 replaced with counts
				//helpPanel.setHelp(helpText, this);
			}
		}	
	}
	private Group overGroupBar(int x, int y) { // mouse over grpbar
		for (Group g : idx2GrpMap.values()) { // XXX
			if (x >= g.x && x <= g.x+g.w && y >= g.yb && y<=g.yb+g.h) return g;
		}	
		return null;
	}
	private Project overProjName( int x, int y){ // XXX int r, int angle,
		int x1 = x;
		int x2 = y;
		
		for (Project p : allProjVec){
			if (x1 >= p.labelR1 && x1 <= p.labelR2){
				if (x2 >= p.labelA1 && x2 <= p.labelA2) return p;
			}
		}
		return null;
	}
	public void mousePressed(MouseEvent e) {}
	public void mouseReleased(MouseEvent e) {}
	public void mouseDragged(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) { }
	public void mouseExited(MouseEvent e)  {setCursor( Cursor.getDefaultCursor() ); }
		
	/************************************************************************************
	 * XXX Painting methods
	 ********************************************************************************/
	protected void makeRepaint() {repaint();} // ControlPanelFlat
	 
	private Font defFont;
	private FontMetrics fm;
	
	private AffineTransform at=null;
	private FontRenderContext frc=null;
	private Font slantFont=null;
	
	private int nextProjY=0;
	
	public void paintComponent(Graphics g)  {
		super.paintComponent(g); 
		cntAllBlks=cntInvBlks=cntChrHidden=cntBlkHidden=0;
		
		/* Setup */
		Graphics2D g2 = (Graphics2D) g;
	    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
	        
		Dimension d = new Dimension((int)(scroller.getWidth()*zoom), (int)(scroller.getHeight()*zoom)); //  d 825 819
	    setPreferredSize(d); 
		revalidate();
		scroller.getViewport().revalidate();
 
		fm = g.getFontMetrics();
		defFont = g.getFont();
		
		if (slantRad!=0) { 
			at = new AffineTransform();
			at.rotate(Math.toRadians(slantRad));
			frc = new FontRenderContext(at, true, true); // null works same as at
			slantFont = defFont.deriveFont(at);
		}
		
        double scale = 1.0;
        if (bToScale) {
        	long max = Math.max(allProjVec.get(0).dispLen, allProjVec.get(1).dispLen);
        	scale = (double) dispProjWidth/ (double) max;
        }
        
        // For Display Filter: Blocks are only on one side, so need to get counts set both sides
        Project p0 = allProjVec.get(0), p1 = allProjVec.get(1);
        for (Group grp : p0.grpVec) grp.cntDisplay=0;
        for (Group grp : p1.grpVec) grp.cntDisplay=0;
        
        for (Block blk : allBlkVec) blk.setDisplayFilter(); // adds to grp.cntDisplay
        
        p0.setDispLen();	
        if (p1!=null) p1.setDispLen();

        if (isBlkFilter()) {
			for (Block b : allBlkVec) 
				if (!b.bDisplay) cntBlkHidden++;
		} 
        
        
        nextProjY = projStart1Y;
        
    // Paint
        /* Project: draw projname and groups */
        paintProject(g, allProjVec.get(0), scale, true   /* isTop */);
        paintProject(g, allProjVec.get(1), scale, false /* !isTop */);
        
        /*  Paint blocks by group (not project) */
        float alpha = 0.7f;
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        boolean showOne = false, onTop = false; // Click chr twice, click chr once
        for (Group grp : idx2GrpMap.values()) {
        	if (grp.onlyShow) {
        		showOne = onTop = true;
        		break;
        	}
        	else if (grp.onTop) onTop = true;
        }
        paintBlocksAll(g, g2, showOne, onTop); 
	} 
	
	/**********************************************************/
	// paint Project
	private void paintProject(Graphics g, Project proj, double scale, boolean isTop) {
		long curProjW = (long) ((double) dispProjWidth * zoom);
    	if (bToScale) {
    		curProjW = (long) ((double) proj.dispLen * scale); 
    		curProjW *= zoom;
    	}
    	int h=0, off1, off2;				
		if (slantRad!=0 && proj.bShowChrLabel) { 
			GlyphVector gv = defFont.createGlyphVector(frc, proj.maxGrpName);
			Rectangle2D rotRect = at.createTransformedShape(gv.getOutline()).getBounds2D();	
	    	h = (int) Math.round(rotRect.getHeight()); // do not use getBounds().getHeight()
			
			off1 = (isTop) ?  h+9 : 9; 		// chrNameY
			off2 = (isTop) ?  5   : h+20; 	// isTop chrBarY; !isTop speciesY
		}
		else {	
			off1 = (isTop) ? 25 : 16;
			off2 = (isTop) ?  8 : 25;
		}
		
		int chrBarY, chrNameY, speciesY, projYStart = nextProjY;
		if (isTop) {
			speciesY	= projYStart;			
			chrNameY 	= speciesY + off1;		
			chrBarY 	= chrNameY + off2; 
			
			nextProjY 	= chrBarY + projIncY; 
		}
		else {
			chrBarY 	= nextProjY;
			chrNameY	= chrBarY + barHeight + off1;
			speciesY	= chrNameY + off2; // wrong 
		}
		
    	// Project name
        paintProjName(g, proj, projStartX, curProjW,  speciesY); // Sets font for name and chromosome numbers
       
       // Display groups      	
    	proj.setGrpBars(curProjW, projStartX, chrNameY, chrBarY); // set x & y for each group
    	if (slantRad!=0) g.setFont(slantFont);
    	
    	for (Group grp : proj.grpVec) 
    		paintGroup(g, proj, grp, isTop);
    	
    	if (slantRad!=0) g.setFont(defFont);
	}
	// Paint project name
	private void paintProjName(Graphics g, Project prjObj, int x, long wz, int y) { // wz zoom full length
		Font f = (selectRefIdx == prjObj.idx) ? new Font ("Courier", Font.BOLD | Font.ITALIC, 16)
    										  : new Font ("Courier", Font.PLAIN, 16);	
		
		int nameH = (int) (fm.getStringBounds(prjObj.displayName,g).getHeight());
		int nameW = (int) (fm.getStringBounds(prjObj.displayName,g).getWidth());
    	int nameX = (int) (x + ((wz - nameW)/2)); 		// center										
    	
    	g.setColor(Color.black);
    	g.setFont(f);
		g.drawString(prjObj.displayName, nameX, y);
		g.setFont (defFont);
		
		// mouse-over coords;
		prjObj.labelR1 = nameX - 1; 
		prjObj.labelR2 = nameX + nameW;
		prjObj.labelA1 = y - nameH; // y is lower left
		prjObj.labelA2 = y + 1;
	}
	// Paint Group
	private void paintGroup(Graphics g, Project proj, Group grp, boolean isTop) {
		if (!grp.bDisplay) return; 		// Display filter: do not print grps without blocks
		
		// paint chromosome bar
		g.setColor(new Color(colorVec.get(grp.colorIdx)));
		g.fillRect(grp.x, grp.yb, grp.w, grp.h);// x,y,w,h
		g.setColor(Color.black);	
		
		if (!proj.bShowChrLabel) return; // Display filter: do not print chromosome filters
		
		// paint chromosome label
		int nameW=0, nameH=0;
    	if (slantRad!=0) {
    		GlyphVector gv = defFont.createGlyphVector(frc, grp.grpName);
        	Rectangle2D rotRect = at.createTransformedShape(gv.getOutline()).getBounds2D();
			nameW = (int) Math.round(rotRect.getWidth());
        	nameH = (int) Math.round(rotRect.getHeight());
    	}
    	else nameW = fm.stringWidth(grp.grpName); 
    	
    	int centerX = (grp.x + (grp.x+grp.w))/2; 
    	int nameX = 0;
		if (slantRad==0) {
			nameX = (nameW>grp.w) ? grp.x+2 : centerX - (nameW/2);
		}
		else if (slantRad==-90) { // vertical in center
			nameX = centerX+4; 
		}
		else { 
			if (isTop) {		// start should not go before grp start
				nameX = (nameW>grp.w) ? grp.x+4 : centerX - (nameW/2);
			}
			else {				// end should not go after  grp end
				nameX = (nameW>grp.w) ? ((grp.x+grp.w)-nameW)+4 : centerX - (nameW/2);
			}
		}
    	
		int nameY = grp.yn;
		if (slantRad!=0 && !isTop) nameY += nameH;  // a little lower
		
		g.drawString(grp.grpName, nameX, nameY); 
		
	}
	/**********************************************************
	 * Blocks are assigned to only one chromosome of the block pair; A selected chromosome is painted on top.
	 */
	private void paintBlocksAll(Graphics g, Graphics2D g2, boolean showOne, boolean onTop) {
		
		
        if (!showOne) {	// Loop 1: paint the non-on-top groups.								     	
        	for (int inv=0; inv<2; inv++) {// Inverse can be small and get hidden; draw second
	        	for (Block blk : allBlkVec) {
	        		if (!blk.bDisplay) continue;	// Filters: <len, <nhits, invert or !invert
	        		
	        		if (inv==1 && !blk.bInverted) continue;
	        		if (inv==0 && blk.bInverted) continue;
	        		
	        		Group grp1 = idx2GrpMap.get(blk.gidx1); 
	        		Group grp2 = idx2GrpMap.get(blk.gidx2);
	        		if (!grp1.bDisplay || !grp2.bDisplay) continue;
	        		
	        		if (!isSelf && grp1.proj_idx == grp2.proj_idx) continue;
	        		
	        		if (!grp1.onTop && !grp2.onTop) 
	        			paintBlock(g, g2, grp1, grp2, blk);
	        	}
        	}
        }
     
        if (onTop) { // Loop 2: paint the on-top group.					   
        	for (int inv=0; inv<2; inv++) {
	        	for (Block blk : allBlkVec) {
	        		if (!blk.bDisplay) continue;
	        		
	        		if (inv==1 && !blk.bInverted) continue;
	        		if (inv==0 && blk.bInverted) continue;
	        		
	        		Group grp1 = idx2GrpMap.get(blk.gidx1);
	        		Group grp2 = idx2GrpMap.get(blk.gidx2);
	        		if (!grp1.bDisplay || !grp2.bDisplay) continue;
	        		
	        		if (!isSelf && grp1.proj_idx == grp2.proj_idx) continue;
	        		
	        		if (grp1.onTop || grp2.onTop) 
		        		paintBlock(g, g2, grp1, grp2, blk);
	        	}
        	}
        }
	}
	// Paint Block
	private void paintBlock(Graphics g, Graphics2D g2, Group grp1, Group grp2, Block blk) {
		cntAllBlks++;
		if (blk.bInverted) cntInvBlks++;
		
		Color cfill;
		if (blkChoice != 1) {
			int cidx = (grp1.proj_idx == selectRefIdx) ? grp1.colorIdx : grp2.colorIdx;
			cfill = new Color(colorVec.get(cidx));
		}
		else cfill = blk.bInverted ? blockN : blockP;
	    
		// draw
		int s1 = grp1.blkLoc(blk.s1);
		int e1 = grp1.blkLoc(blk.e1);
		int s2 = grp2.blkLoc(blk.s2);
		int e2 = grp2.blkLoc(blk.e2);
		
		int y1 = grp1.yb + grp1.h + barGap;
		int y2 = grp2.yb - barGap;
		if (swapChoice==SPECIES || swapChoice==BOTH) {						
			y1 = grp1.yb - barGap;
			y2 = grp2.yb + grp1.h + barGap;
		}
		
		if (bInvStroke && blk.bInverted) {
			Stroke stroke = new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        	g2.setColor(Color.darkGray);
        	g2.setStroke(stroke); 
		}
		GeneralPath gp = new GeneralPath();
		gp.moveTo((double)s1, (double) y1);
		
		if (blk.bInverted && bInvBlk) {
			gp.lineTo((double)e2, (double) y2);
			gp.lineTo((double)s2, (double) y2);
		}
		else {
			gp.lineTo((double)s2, (double) y2);
			gp.lineTo((double)e2, (double) y2);
		}
		gp.lineTo((double)e1, (double) y1);
		gp.closePath();
		
		if (bInvStroke && blk.bInverted) {
			g2.draw(gp);			// necessary for stroke; leaves weird residue if done without stroke
			g2.setStroke(new BasicStroke());
		}
		g2.setColor(cfill);	
		g2.fill(gp);
	}
	
	//////////////////////////////////////////////////////////////
	protected void saveColors() { // on ControlPanelFlat.Save 
		PersistentProps cookies = new PersistentProps();
		PersistentProps allCook = cookies.copy(propName);
		
		String c=colorSet + ":";
		c += (bScaleColors) ? scaleColors + ":" : "0:";
		
		if (bOrder)         c += "1:0";
		else if (bRevOrder) c += "2:0";
		else if (bShuffle)  c += "3:" + seed;
		else                c += "0:0";
		
		allCook.setProp(c);
	}
	protected void makeNewColorVec() { // ControlPanelFlat.ChgColor
		colorVec = frame.getColorVec(colorSet, bScaleColors, scaleColors, bOrder, bRevOrder, bShuffle, seed);;
		
		int nColors = 0;
		for (Project p : allProjVec) nColors += p.grpVec.size();
		while (colorVec.size() < nColors) colorVec.addAll(colorVec); // duplicates set if >100 chromosomes
	}

	protected void clear() {idx2GrpMap.clear();}
	/*******************************************************************
	 * Control - toggles are directly changed from ControlPanel
	 *****************************************************************/
	protected void resetToHome() { 
		zoom = ZOOM_DEFAULT;
		bToScale =  false;
		lenChoice = hitChoice = 0;
	}
	protected boolean isHome() { 
		return zoom==ZOOM_DEFAULT && !bToScale && lenChoice==0 && hitChoice==0;
	}
	public String getHelpText(MouseEvent event) { 
		String msg = "  #Blocks " + cntAllBlks + "  #Inverse " + cntInvBlks; 
		if (chrChoice>0) msg += "   #Hidden Chromosomes " + cntChrHidden; 
		if (isBlkFilter()) msg += "  #Hidden Blocks " + cntBlkHidden; 
		return msg; 
	} 
	protected void zoom (double f) { // -/= icons
		zoom *= f;				
		if (zoom<0.2) zoom=0.2; 
	    makeRepaint();  
	}
	// 0 none, 1 species, 2 colors, 3 both
	protected void swapSpecies(int choice) {     
	try {
		if (swapChoice == choice) return;
		
		Project p0=null, p1=null;
		
		if (choice==SPECIES || choice==BOTH) { // swap species
			p0 = savProjVec.get(1);
			p1 = savProjVec.get(0);
		}
		else if (choice==NONE || choice==COLORS) { // put back to original
			p0 = savProjVec.get(0);
			p1 = savProjVec.get(1);
		}
		if (p0 != null) {
			allProjVec.clear();
			allProjVec.add(p0);
			allProjVec.add(p1);
			
			selectRefIdx = p0.idx; // make top selected
		}
		swapChoice = choice;	
		initObj.setChrColorIdx();	// used swapChoice
	}
	catch (Exception e) {ErrorReport.print(e, "Reverse projects");}
	}	
	private boolean isBlkFilter() {
		return (blkChoice>1 || lenChoice>0 || hitChoice>0);
	}
	protected String getProj(int i) {return savProjVec.get(i).displayName;}
	protected void setChrShow(int i, boolean b) {savProjVec.get(i).bShowChrLabel=b;}
	
	/************************************************************************
	 * XXX Classes
	 **********************************************************************/
	private class Project {
		private int idx;
		private String displayName;							// load from db
		private Vector<Group> grpVec = new Vector<Group>();	// load from db
		private String maxGrpName="";						
		private long dispLen = 0;							// computes before display
		private boolean bBlkSide;							// only one-side has the blocks
		private int labelR1,labelR2,labelA1,labelA2;		// mouse-over coords; set in paintProjText
		private boolean bShowChrLabel=true;					// Show chromosome labels
		
		private Project(int idx, String name, DBconn2 dbc2)  {
		try {
			this.idx = idx;
			this.displayName = name;
	
			Mproject tProj = new Mproject();
			String display_name = tProj.getKey(tProj.sDisplay);
			ResultSet rs = dbc2.executeQuery("select value from proj_props where name='"+display_name+"' and proj_idx=" + idx);
			if (rs.next()) displayName = rs.getString(1); 
		}
		catch (Exception e) {ErrorReport.print(e, "Getting project");}
		}
			
		private void setDispLen() { // both sides
			dispLen = 0;
			for (Group grp : grpVec) 
				if (grp.setDisplay()) dispLen += grp.grpLen; // sets grp.bDisplay and blk.bDisplay
		}
		private void setGrpBars(long pWz, int x, int yn, int yb){ // project width*zoom, projX, curProjY
			int nextx = x;
			
			for (Group grp : grpVec) {
				if (!grp.bDisplay) continue;
				
				grp.x = nextx;
				grp.w = (int)(Math.round((pWz * grp.grpLen) / dispLen));
				grp.h = barHeight;
				grp.yn = yn;
				grp.yb = yb;
				
				nextx = (grp.x + grp.w + barGap); 
			}
		}

	}// end project class
	
	// Chromosomes
	private class Group {
		private int grpLen;
		private int idx, proj_idx;
		private String grpName;
		private int x, w, yn, yb, h;		 	// Assigned values in Project.setGrpBar during paintComponent
		private int colorIdx = -1; 		// index into color vector
		private boolean onTop = false, onlyShow = false; // click, double click
		private int cntBlk=0;									// has count for other side
		private int cntDisplay;						
		private boolean bDisplay=true;
		
		private Group(int size, int idx, int proj_idx, String name){
			this.grpLen = size;
			this.idx = idx;
			this.proj_idx = proj_idx;
			this.grpName = name;		// chromosome number
		}
		
		private int blkLoc(double blkLoc) { // for painting block
			double a = w + 1;
			double loc = ((a*blkLoc)/((double)grpLen));
			return x + (int)loc;
		}
		
		private boolean setDisplay() { // filters chromosomes
			bDisplay = false;
			
			if (chrChoice==0) { 	// always
				bDisplay = true;
			}
			else if (chrChoice==1) { // if has blocks
				bDisplay = (cntBlk>0);
			}
			else {						// if has display blocks; could be hits, len or inverted/non-inverted
				bDisplay = (cntDisplay>0);
			}
			if (!bDisplay) cntChrHidden++;
			return bDisplay;
		}
	}
	/// Blocks 
	private class Block {
		private int s1, e1, s2, e2, lenMin, nHits, blkNum;
		private int gidx1, gidx2;
		private boolean bInverted, bDisplay=true;
		
		private Block(int gidx1, int gidx2, int s1, int e1, int s2, int e2, boolean inv, int hits, int blkNum) {
			this.gidx1 = gidx1; this.gidx2 = gidx2;
			this.s1 = s1; 		this.e1 = e1;
			this.s2 = s2; 		this.e2 = e2;
			this.bInverted = inv;
			lenMin = Math.min(e1-s1+1, e2-s2+1);
			this.nHits = hits;
			this.blkNum = blkNum;
		}
		private boolean setDisplayFilter() { // filters blocks
			bDisplay=false;
			if (lenChoice!=0) {
				for (int i=1; i<ControlPanelFlat.lenCuts.length; i++) { // index[0] is 0
					if (lenChoice==i && lenMin<ControlPanelFlat.lenCuts[i]) return false; 
				}
			}
			if (hitChoice!=0) {
				for (int i=1; i<ControlPanelFlat.numHits.length; i++) {// index[0] is 0
					if (hitChoice==i && nHits<ControlPanelFlat.numHits[i]) return false; 
				}
			}
			if (blkChoice == 2 &&  bInverted) return false;  // these must be after len & hit filters
			if (blkChoice == 3 && !bInverted) return false; 
			
			bDisplay = true;
			
			Group grp1 = idx2GrpMap.get(gidx1);
    		Group grp2 = idx2GrpMap.get(gidx2);
			grp1.cntDisplay++;
			grp2.cntDisplay++;
			
			return true;
		}
	}
	/****************************************************************************
	 * XXX Setup and database load
	 **************************************************************************/
	private class Zinit {
		private DBconn2 dbc2;
		
		private boolean initDS(DBconn2 dbc2, int[] projIdxList, int refIdx) {
		try {
			this.dbc2 = dbc2;
			
		// Projects and groups
			for (int i = 0; i < projIdxList.length; i++){ //allProjVec, allGrpsVec,idx2GrpMap
				loadProjects(projIdxList[i]);
			}
			
		// Blocks
			loadBlocks();
			
		// Colors
			selectRefIdx = refIdx;
			
			setChrColorIdx(); 							// does not need colorVec, only allProjVec
			if (!initColorsFromProps()) return false; 	// color params are saved in .symap_props
			makeNewColorVec();							// create colorVec, use allGrpVec;
				
			return true;
		}
		catch(Exception e) {ErrorReport.print(e, "Init data structures"); return false;}	
		}
		// Create Project, which loads its groups
		private void loadProjects(int idx)  {
			try {
				ResultSet rs;
				rs = dbc2.executeQuery("select name from projects where idx=" + idx);
				if (!rs.next()) { 
					System.out.println("Cannot find proj_idx=" + idx);
					return;
				}

				Project proj = new Project(idx, rs.getString(1), dbc2); // loads groups
				allProjVec.add(proj);
				savProjVec.add(proj);
				
				loadGroups(proj);
				for (Group g : proj.grpVec) idx2GrpMap.put(g.idx, g);	
				
				maxNumGrps = Math.max(maxNumGrps, proj.grpVec.size());
			}
			catch(Exception e) {ErrorReport.print(e, "Add project for Circle Panel");}	
		}
		// Load Groups
		private void loadGroups(Project proj) {
		try {
			int proj_idx = proj.idx;
			ResultSet rs = dbc2.executeQuery("select idx, name, length from xgroups join pseudos " + 
					" on pseudos.grp_idx=xgroups.idx where xgroups.proj_idx=" + proj_idx + " order by sort_order");

			while (rs.next()){
				int gidx = rs.getInt(1);
				
				Group g = new Group(rs.getInt(3), gidx, proj_idx, rs.getString(2));
				proj.grpVec.add(g);
				if (g.grpName.length()>proj.maxGrpName.length()) proj.maxGrpName=g.grpName; 
				proj.dispLen += g.grpLen;
			}
		}
		catch(Exception e) {ErrorReport.print(e, "Load groups");}	
		}
		// The blocks are just loaded on the grp1_idx side
		private void loadBlocks()  {
		try {
			ResultSet rs;
			String sql = "select grp1_idx, grp2_idx, start1, end1, start2, end2, corr, score, blocknum from blocks ";
			
			Project p1 = allProjVec.get(0);
			Project p2 = allProjVec.get(1);
			
			rs = dbc2.executeQuery(sql +  " where proj1_idx=" + p1.idx + " and proj2_idx=" + p2.idx );
			while (rs.next()) {
				addBlock(rs.getInt(1), rs.getInt(2), rs.getInt(3),rs.getInt(4),rs.getInt(5),rs.getInt(6),(rs.getFloat(7)<0), rs.getInt(8), rs.getInt(9));
				p1.bBlkSide = true;
			}
			if (!p1.bBlkSide) {// switch the p.idx
				rs = dbc2.executeQuery(sql + " where proj1_idx=" + p2.idx + " and proj2_idx=" + p1.idx );
				while (rs.next()) {
					addBlock(rs.getInt(1), rs.getInt(2), rs.getInt(3),rs.getInt(4),rs.getInt(5),rs.getInt(6),(rs.getFloat(7)<0), rs.getInt(8), rs.getInt(9));
					p2.bBlkSide = true;
				}
			}
		}
		catch(Exception e) {ErrorReport.print(e, "Add project for Circle Panel");}	
		}
		private void addBlock(int gidx1, int gidx2, int s1, int e1, int s2, int e2, boolean inv, int score, int blkNum) {
			Block b = new Block(gidx1, gidx2, s1, e1, s2, e2, inv, score, blkNum);
			allBlkVec.add(b);
			
			Group grp = idx2GrpMap.get(gidx1);
			grp.cntBlk++;
			
			grp = idx2GrpMap.get(gidx2);
			grp.cntBlk++;
		}
		private void prt() {
			if (!bTrace) return;
			
			for (Project p : allProjVec) {
				dprt(String.format("p%2d %10s #Grps %,d", p.idx, p.displayName, p.grpVec.size()));
				for (Group g : p.grpVec) {
					dprt(String.format("   g%2d  p%2d  %10s #Blocks %,d", g.idx, g.proj_idx, g.grpName, g.cntBlk));
					
				}
			}
		}
		
		/*****************************************************
		 * Load/Save colors
		 */
		private boolean initColorsFromProps() {
			try {
				PersistentProps cookies = new PersistentProps(); 
				String propSaved = cookies.copy(propName).getProp();
				if (propSaved==null) return true; // use defaults
				
				String [] tok = propSaved.split(":"); 
				if (tok.length!=4) return true; // use defaults
					
				if (tok[0].equals("1")) 		colorSet=1;
				else if (tok[0].equals("2"))    colorSet=2;
				
				if (!tok[1].equals("0")) {
					double s = Utilities.getDouble(tok[1]);
					if (s>0) {
						bScaleColors=true;
						scaleColors = s;
					}
				}
				if (!tok[2].equals("0")) {
					if      (tok[2].equals("1")) bOrder=true;
					else if (tok[2].equals("2")) bRevOrder=true;
					else if (tok[2].equals("3")) {
						bShuffle=true;
						int s = Utilities.getInt(tok[3]);
						if (s>0) seed=s;
					}
				}		
				return true;
			}
			catch(Exception e) {ErrorReport.print(e, "Init colors"); return false;}
		}
		// Assign a color to each group
		private void setChrColorIdx() {
			try {
				// always the same set of color regardless of comparison
				if (maxNumGrps<=50) {
					Project p0 = allProjVec.get(0), 
							p1 = allProjVec.get(1);
					
					if (swapChoice==COLORS || swapChoice==BOTH) { 
						p0 = allProjVec.get(1); 
						p1 = allProjVec.get(0);
					} 
					
					int inc = 0;
					for (Group g : p0.grpVec) g.colorIdx = inc++;
					
					inc = 50;
					for (Group g : p1.grpVec) g.colorIdx = inc++;
				}
				else { // does not allow color swap if >50 chrs
					int inc=0;
					for (Project p : allProjVec) {// duplicates set if over 100 chromosomes
						for (Group g : p.grpVec) g.colorIdx = inc++;
					}
				}
			}
			catch(Exception e) {ErrorReport.print(e, "Set colors");}
		}
	}// End Zinit
	
	
	/************************************************************
	 * Two-color all blocks
	 */
	public static Color blockP; 		
	public static Color blockN;
	static {
		PropertiesReader props = new PropertiesReader(Globals.class.getResource("/properties/linear.properties"));
		blockP = props.getColor("blockP"); 
		blockN = props.getColor("blockN"); 
	}
}
