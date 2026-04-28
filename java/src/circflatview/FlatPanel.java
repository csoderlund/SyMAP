package circflatview;

import java.sql.ResultSet;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.geom.GeneralPath;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

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
 */
public class FlatPanel extends JPanel implements HelpListener, MouseListener,MouseMotionListener {
	private static final long serialVersionUID = -1711255873365895293L;
	private final boolean bTrace=false;
	
	static private String propName="SyMAPlinear"; // Does not share saved colors with Circle
	static private final int projWidth=740,    // total length of the project*zoom; screen is >800
							 projX=40, 		   // starting point of each project,
							 projY=70,		   // Y for first project
							 projIncY=200,     // inc Y for subsequent projects
							 barHeight = 15,   // chr bar height
							 barGap = 2; 	   // gap between grp-grp && grp-blk
	
	// ControlPanelCirc has Info popup with this 
	private final String helpText = "Click a project name to use its colors.\n" +
									"Click a bar to bring its blocks to the top."; 			
	private final String helpName = "Click a project name to use its colors for the blocks.";
	private final String helpBar =  "Click a bar to bring its blocks to the top."
								+ "\nDouble click a bar to only show its blocks.\n";
	protected final double ZOOM_DEFAULT = 1.0; 		
	
	 // set from ControlPanelCirc;  these defaults are also set in ControlPanelCirc
    protected int invChoice = 0, lenChoice = 0, hitChoice = 0;
    protected boolean bRevRef = false, bInvBlk = false; // Switch the ref if true; do not show blocks inverted if true
    protected boolean bToScale = false;			// genome size
    protected double zoom = ZOOM_DEFAULT;		// +/- icons
    
    // ControlPanelFlat color popup; default values, if changed, changed in Default button; saved in .symap_props
    protected int colorSet=1;					// ColorSet 1 or 2;
    protected boolean bOrder=false, bRevOrder=false, bShuffle=false, bScaleColors=false;				
    protected double scaleColors=0.8, scaleGrpLen=0.3;			
    protected int seed = 1;					
    
    private FlatFrame frame;
	
	private Vector<Integer> colorVec;
	
	private Vector<Project> allProjVec;
	private TreeMap <Integer,Group> idx2GrpMap;
	private Vector<Integer> priorityVec;
	
    private JScrollPane scroller;		
    private HelpBar helpPanel = null;
    
    private Zinit initObj;
    private boolean isSelf = false;	// not working
    
    private void dprt(String msg) {if (bTrace) symap.Globals.prt("FP: " + msg);}
    
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
	
	/*******************************************************************
	 * Control - toggles are directly changed from ControlPanel
	 *****************************************************************/
	protected void zoom (double f) { // -/= icons
		zoom *= f;				
		if (zoom<0.2) zoom=0.2; 
	    makeRepaint();  
	}
	
	protected void reverse() {     // reverse reference; only available for 2 WG view
	try {
		Project p1 = allProjVec.get(0);
		Project p2 = allProjVec.get(1);
		allProjVec.clear();
		allProjVec.add(p2);
		allProjVec.add(p1);
		
		initObj.setChrColorIdx();
	}
	catch (Exception e) {ErrorReport.print(e, "Reverse projects");}
	}	
	/***************************************************
	 * XXX Mouse
	 */
	public void handleClick(long x, long y, boolean dbl) {}
	
	public void mouseClicked(MouseEvent evt)  { // Click arc or project name
		int xRel = (int)evt.getX();
		int yRel = (int)evt.getY();
	
		Group grp = overGroupBar(xRel, yRel);

		boolean dbl = (evt.getClickCount() > 1);
			
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
			return;
		}
		
		Project p = overProjName((int)evt.getX(), (int)evt.getY());
		if (p == null) return;
		
		// click the project name; use priority projects colors
		priorityVec.add(0,p.idx); 
		int i = 1;
		for (; i < priorityVec.size();i++) {
			if (priorityVec.get(i) == p.idx) break;
		}
		priorityVec.remove(i);
		for (Project p1 : allProjVec) 
			for (Group g1 : p1.grps) g1.onTop=g1.onlyShow=false;
		
		makeRepaint();						
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
				setCursor( Cursor.getDefaultCursor() );
				helpPanel.setHelp(helpText, this);
			}
		}	
	}
	private Group overGroupBar(int x, int y) { // mouse over grpbar
		for (Group g : idx2GrpMap.values()) { // XXX
			if (x >= g.x && x <= g.x+g.w && y >= g.y && y<=g.y+g.h) return g;
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
	
	// Note that circle angles are measured from the middle-right of the circle, going around counterclockwise. 
	// Hence, the right side of the circle is angle 0-90, plus angle 270-360.   
	public void paintComponent(Graphics g)  {
		super.paintComponent(g); 
		
		dprt("Paint");
	// Setup 
		Dimension d = new Dimension((int)(scroller.getWidth()*zoom), (int)(scroller.getHeight()*zoom)); //  d 825 819
		
	    setPreferredSize(d); 
		revalidate();
		scroller.getViewport().revalidate();
 
		FontMetrics fm = g.getFontMetrics();
		
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int curProjY = projY;				// increments through projects
        int projW = (int) ((double) projWidth * zoom);
        double scale = 1.0;
        if (bToScale) {
        	long max = Math.max(allProjVec.get(0).projLen, allProjVec.get(1).projLen);
        	scale = (double) projWidth/ (double) max;
        }
        
    // Loop through projects draw name, grps, blocks 
        boolean isTop=true;
        for (Project prjObj : allProjVec) {
        	int curProjW = projW;
        	if (bToScale) {
        		curProjW = (int) ((double) prjObj.projLen * scale);
        		curProjW *= zoom;
        	}
        	
        	// Project name
        	int off = (isTop) ? -18 : 70;
            paintProjName(g, prjObj, projX, curProjW,  curProjY+off); // Sets font for name and chromosome numbers
           
        	prjObj.setGrpBars(curProjW, projX, curProjY); // set x & y for each group
 
        	// Loop through chromosomes to draw chr# and chr outer arc
        	off = (isTop) ? -8 : 30;
        	
        	for (Group go : prjObj.grps) {
        		g.setColor(new Color(colorVec.get(go.colorIdx)));
        		g.fillRect(go.x, go.y, go.w, go.h);// x,y,w,h
        		g.setColor(Color.black);	
        		
        		int tx = (go.x+(go.x+go.w))/2 - fm.stringWidth(go.grpName)/2;
                g.drawString(go.grpName, tx, go.y+off); 
        	}
        	curProjY += projIncY;
        	isTop=false;  
        } // end loop through projects
        
        ////////  Paint blocks /////////
        g.setColor(Color.white);
      
        float alpha = 0.7f;
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        boolean showOne = false, onTop = false;
        for (Group grp : idx2GrpMap.values()) {
        	if (grp.onlyShow) {
        		showOne = onTop = true;
        		break;
        	}
        	else if (grp.onTop) onTop = true;
        }
      
      // NOTE: blocks are assigned to only one chromosome of the block pair
      // Group Loop 1: paint the non-on-top groups.
        if (!showOne) {										 
	        for (Group grp : idx2GrpMap.values()) {
	        	
	        	for (Block b : grp.blocks) {
	        		Group grp1 = idx2GrpMap.get(b.gidx1); 
	        		Group grp2 = idx2GrpMap.get(b.gidx2);
	        		
	        		if (!isSelf && grp1.proj_idx == grp2.proj_idx) continue;
	        		
	        		if (grp1.onTop || grp2.onTop) continue;
	        	
	        		paintGroupBlk(g, g2, grp1, grp2, b);
	        	}
	        }
        }
     // Group Loop 2: paint the on-top group.
        if (onTop) { 							
	        for (Group grp : idx2GrpMap.values()) {
	        	
	        	for (Block b : grp.blocks) {
	        		
	        		Group grp1 = idx2GrpMap.get(b.gidx1);
	        		Group grp2 = idx2GrpMap.get(b.gidx2);
	        		
	        		if (!isSelf && grp1.proj_idx == grp2.proj_idx) continue;
	        		
	        		if (grp.onTop || grp2.onTop) 
		        		paintGroupBlk(g, g2, grp1, grp2, b);
	        	}
	        }
        }
	}
	// Paint Block
	private void paintGroupBlk(Graphics g, Graphics2D g2, Group grp1, Group grp2, Block blk) {
		if (lenChoice==1 && blk.len<500000) return;
		if (lenChoice==2 && blk.len<1000000) return;
		if (lenChoice==3 && blk.len<2000000) return;
		
		if (hitChoice==1 && blk.hits<50) return;
		if (hitChoice==2 && blk.hits<100) return;
		if (hitChoice==3 && blk.hits<200) return;
		
		if (invChoice == 1 && !blk.inverted) return;
		if (invChoice == 2 && blk.inverted) return;

		int cidx = (colorFirstPriority(grp1.proj_idx, grp2.proj_idx) ? grp1.colorIdx : grp2.colorIdx);
		if (invChoice != 3) g.setColor(new Color(colorVec.get(cidx)));
		else                g.setColor(blk.inverted ? blockN : blockP);
	    
		int s1 = grp1.blkLoc(blk.s1);
		int e1 = grp1.blkLoc(blk.e1);
		int s2 = grp2.blkLoc(blk.s2);
		int e2 = grp2.blkLoc(blk.e2);
		
		int y1 = grp1.y + grp1.h + barGap;
		int y2 = grp2.y - barGap;
		if (bRevRef) {
			y1 = grp1.y - barGap;
			y2 = grp2.y + grp1.h + barGap;
		}
		
		GeneralPath gp = new GeneralPath();
		
		gp.moveTo((double)s1, (double) y1);
		
		if (blk.inverted && !bInvBlk) {
			gp.lineTo((double)e2, (double) y2);
			gp.lineTo((double)s2, (double) y2);
		}
		else {
			gp.lineTo((double)s2, (double) y2);
			gp.lineTo((double)e2, (double) y2);
		}
		gp.lineTo((double)e1, (double) y1);
		gp.closePath();
		g2.fill(gp);
	}
	
	// Paint project name
	private void paintProjName(Graphics g, Project prjObj, int x, int wz, int y) { // wz zoom full length
		g.setColor(Color.black);
       
		Font f = g.getFont();
    	f = setProjFont(0, f, prjObj.idx);
    	g.setFont(f);
		    	
		FontMetrics fm = g.getFontMetrics();
		int nameH =   (int) (fm.getStringBounds(prjObj.displayName,g).getHeight());
		int nameW =   (int) (fm.getStringBounds(prjObj.displayName,g).getWidth());
		
    	int nameX = x + ((wz - nameW)/2); 														
    	int nameY = y - nameH;  
    	
		g.drawString(prjObj.displayName,nameX,nameY);
		
		// mouse-over coords;
		prjObj.labelR1 = nameX - 1; 
		prjObj.labelR2 = nameX + nameW;
		prjObj.labelA1 = nameY - nameH; // x,y is lower left
		prjObj.labelA2 = nameY + 1;
    	
    	f = setProjFont(1, f, prjObj.idx);
    	g.setFont (f);
	}
	// Set font based on priority; 2nd is italics because its colors are given used for project 2 to project 3.
	private Font setProjFont(int i, Font f, int pidx) {
		int x = (i==0) ? 16 : 14;
		
		if (priorityVec.get(0) == pidx)  							 f = new Font ("Courier", Font.BOLD | Font.ITALIC, x);
    	else if (priorityVec.size()>2 && priorityVec.get(1) == pidx) f = new Font ("Courier", Font.ITALIC, x);
    	else 														 f = new Font ("Courier", Font.PLAIN, x);	
		
		return f;
	}
	
	// Click Project Name: returns true if first project has color priority, i.e. group colors are used for blocks
	private boolean colorFirstPriority(int idx1, int idx2) {
		for (int idx : priorityVec) {
			if (idx == idx1) return true;
			if (idx == idx2) return false;
		}
		return true; 
	}
	//////////////////////////////////////////////////////////////
	protected void clear() {idx2GrpMap.clear();}
	
	protected void resetToHome() { 
		zoom = ZOOM_DEFAULT;
		bToScale =  false;
	}
	protected boolean isHome() { 
		return zoom==ZOOM_DEFAULT && !bToScale;
	}
	public String getHelpText(MouseEvent event) { return helpText;} // frame.HelpBar
	/************************************************************************
	 * Classes
	 **********************************************************************/
	private class Project {
		private int idx;
		private String displayName;							// load from db
		private Vector<Group> grps = new Vector<Group>();	// load from db
		private String maxGrpName="";						// Not used - would be useful to stagger long chrNames
		private long projLen = 0;						    // compute when groups are loaded			
		
		private int labelR1,labelR2,labelA1,labelA2;		// mouse-over coords; set in paintProjText
		
		private Project(int idx, String name, DBconn2 dbc2)  {
		try {
			this.idx = idx;
	
			ResultSet rs = dbc2.executeQuery("select idx, name, length from xgroups join pseudos " + 
						" on pseudos.grp_idx=xgroups.idx where xgroups.proj_idx=" + idx + 
						" order by sort_order");

			while (rs.next()){
				Group g = new Group(rs.getInt(3), rs.getInt(1), idx, rs.getString(2), name);
				grps.add(g);
				
				if (g.grpName.length()>maxGrpName.length()) maxGrpName=g.grpName; 
				
				projLen += g.grpLen;
			}
			
			Mproject tProj = new Mproject();
			String display_name = tProj.getKey(tProj.sDisplay);
			displayName = name;
			rs = dbc2.executeQuery("select value from proj_props where name='"+display_name+"' and proj_idx=" + idx);
			if (rs.next()) displayName = rs.getString(1); 
		}
		catch (Exception e) {ErrorReport.print(e, "Getting project");}
		}
			
		private void setGrpBars(long pWz, int x, int y){ // project width*zoom, projX, curProjY
			int nextx = x;
			
			for (Group g : grps) {
				g.x = nextx;
				g.w = (int)(Math.round((pWz * g.grpLen) / projLen));
				
				g.y = y;
				g.h = barHeight;
				
				nextx = (g.x + g.w + barGap);
			}
		}
	}// end project class
	
	// Chromosome and respective blocks
	private class Group {
		private int grpLen;
		private int idx, proj_idx;
		private String grpName;
		private int x, w, y, h;		 	// Assigned values in setGrpBar during paintComponent
		private int colorIdx = -1; 		// index into color vector
		private boolean onTop = false, onlyShow = false; // click, double click
		private Vector <Block> blocks = new Vector <Block> ();
		
		private Group(int size, int idx, int proj_idx, String name, String pname){
			this.grpLen = size;
			this.idx = idx;
			this.proj_idx = proj_idx;
			this.grpName = name;		// chromosome number
		}
		private void addBlock(Block b) {blocks.add(b);}
		
		private int blkLoc(double blkLoc) { // for painting block
			double a = w + 1;
			double loc = ((a*blkLoc)/((double)grpLen));
			return x + (int)loc;
		}
	}
	private class Block {
		private int s1, e1, s2, e2, len, hits;
		private int gidx1, gidx2;
		private boolean inverted;
		
		private Block(int gidx1, int gidx2, int s1, int e1, int s2, int e2, boolean inv, int hits) {
			this.gidx1 = gidx1; this.gidx2 = gidx2;
			this.s1 = s1; 		this.e1 = e1;
			this.s2 = s2; 		this.e2 = e2;
			this.inverted = inv;
			len = Math.min(e1-s1+1, e2-s2+1);
			this.hits = hits;
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
			allProjVec = new Vector<Project>();
			idx2GrpMap = new TreeMap<Integer,Group> ();
			priorityVec = new Vector<Integer>();
			
			for (int i = 0; i < projIdxList.length; i++){ //allProjVec, allGrpsVec,idx2GrpMap
				loadProjects(projIdxList[i]);
			}
			
		// Blocks
			loadBlocks();
			prt();
			
		// Colors
			priorityVec.add(refIdx);	// initial reference has priority colors
			for (Project p : allProjVec){
				if (p.idx!=refIdx) priorityVec.add(p.idx);
			}
			
			makeNewColorVec();							// create colorVec, use allGrpVec;
			setChrColorIdx(); 							// does not need colorVec, only allProjVec
			if (!initColorsFromProps()) return false; 	// color params are saved in .symap_props
				
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
				
				for (Group g : proj.grps) idx2GrpMap.put(g.idx, g);	
			}
			catch(Exception e) {ErrorReport.print(e, "Add project for Circle Panel");}	
		}
		
		// Get the blocks in both directions since order is unknown
		private void loadBlocks()  {
		try {
			ResultSet rs;
			String sql = "select grp1_idx, grp2_idx, start1, end1, start2, end2, corr, score from blocks ";
			
			for (int i1 = 0; i1 < allProjVec.size(); i1++) {
				Project p1 = allProjVec.get(i1);
				
				for (int i2 = i1; i2 < allProjVec.size(); i2++) {
					Project p2 = allProjVec.get(i2);
					
					rs = dbc2.executeQuery(sql +  " where proj1_idx=" + p1.idx + " and proj2_idx=" + p2.idx );
					while (rs.next()) {
						addBlock(rs.getInt(1), rs.getInt(2), rs.getInt(3),rs.getInt(4),rs.getInt(5),rs.getInt(6),(rs.getFloat(7)<0), rs.getInt(8));
					}
					
					if (p1.idx != p2.idx) {//isSelf not working
						rs = dbc2.executeQuery(sql + " where proj1_idx=" + p2.idx + " and proj2_idx=" + p1.idx );
						while (rs.next()) {
							addBlock(rs.getInt(1), rs.getInt(2), rs.getInt(3),rs.getInt(4),rs.getInt(5),rs.getInt(6),(rs.getFloat(7)<0), rs.getInt(8));
						}
					}
				}
			}
		}
		catch(Exception e) {ErrorReport.print(e, "Add project for Circle Panel");}	
		}
		private void addBlock(int gidx1, int gidx2, int s1, int e1, int s2, int e2, boolean inv, int score) {
			Block b = new Block(gidx1, gidx2, s1, e1, s2, e2, inv, score);
			
			Group grp = idx2GrpMap.get(gidx1);
			grp.addBlock(b);
		}
		private void prt() {
			if (!bTrace) return;
			
			for (Project p : allProjVec) {
				dprt(String.format("p%2d %10s #Grps %,d", p.idx, p.displayName, p.grps.size()));
				for (Group g : p.grps) {
					dprt(String.format("   g%2d  p%2d  %10s #Blocks %,d", g.idx, g.proj_idx, g.grpName, g.blocks.size()));
					for (Block b : g.blocks) {
						dprt(String.format("      g%2d  g%2d   Len %,10d   1: %,10d %,10d  2: %,10d %,10d", b.gidx1, b.gidx2, b.len, b.s1, b.e1, b.s2, b.e2 ));
					}
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
		// try to make them always with same color regardless of comparison
		private void setChrColorIdx() {
			try {
				int maxGrps=0;
				for (Project p : allProjVec) maxGrps = Math.max(maxGrps, p.grps.size());
				
				boolean bad=false;
				if (allProjVec.size() <=4 && maxGrps<=25) {
					int i=0;
					int [] start = {0,24,49,74};
					for (Project p : allProjVec) {
						int inc = start[i];
						for (Group g : p.grps) {
							g.colorIdx = inc++;
							if (g.colorIdx>=colorVec.size()) bad=true; 	
						}
						i++;
					}
				}
				else {
					int inc=0;
					for (Project p : allProjVec) {
						for (Group g : p.grps) {
							g.colorIdx = inc++;
							if (g.colorIdx>=colorVec.size()) bad=true;
						}
					}
				}
				if (bad) { // to fix properly, do this per project and start over on coloring
					String msg = "Not enough colors (" + colorVec.size() + ") for all chromosomes";
					Globals.prt(msg);
					for (Project p : allProjVec) {
						for (Group g : p.grps) {
							if (g.colorIdx>=colorVec.size()) g.colorIdx=colorVec.size()-1;
						}
					}
				}
			}
			catch(Exception e) {ErrorReport.print(e, "Set colors");}
		}
	}// End Zinit
	protected void makeNewColorVec() { // ControlPanelFlat.ChgColor
		colorVec = frame.getColorVec(colorSet, bScaleColors, scaleColors, bOrder, bRevOrder, bShuffle, seed);;
		
		int nColors = 0;
		for (Project p : allProjVec) nColors += p.grps.size();
		while (colorVec.size() < nColors) colorVec.addAll(colorVec); // duplicates set if >100 chromosomes
	}
	/************************************************************
	 * Two-color all blocks
	 */
	public static Color blockP; 		
	public static Color blockN;
	static {
		PropertiesReader props = new PropertiesReader(Globals.class.getResource("/properties/circle.properties"));
		blockP = props.getColor("blockP"); 
		blockN = props.getColor("blockN"); 
	}
}
