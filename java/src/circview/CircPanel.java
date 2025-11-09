package circview;

import java.sql.ResultSet;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.geom.GeneralPath;
import java.awt.geom.Arc2D;
import java.awt.geom.AffineTransform;
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
import java.util.TreeSet;

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
 * Draws the circle view for Selected-2-WG and 2D-N-chr
 */
public class CircPanel extends JPanel implements HelpListener, MouseListener,MouseMotionListener {
	private static final long serialVersionUID = -1711255873365895293L;
	static private String propName="SyMAPcircle";
	
	// ControlPanelCirc has Info popup with this 
	private final String helpText = "Click a project name to use its colors.\n" +
									"Click an arc to bring its blocks to the top."; 			
	private final String helpName = "Click a project name to use its colors for the blocks.";
	private final String helpArc =  "Click an arc to bring its blocks to the top."
									+ "\nDouble click an arc to only show its blocks.\n";
	protected final double ZOOM_DEFAULT = 1.0; 			
	protected final int ARC_DEFAULT = 0, ARC_INC=5;	 
	
	 // set from ControlPanelCirc;  these defaults are also set in ControlPanelCirc
    protected int invChoice = 0;
    protected boolean bShowSelf = false, bRotateText = false, bRevRef = false;
    protected boolean bToScale = false;			// genome size
    protected double zoom = ZOOM_DEFAULT;		// +/- icons
    private int arc0 = ARC_DEFAULT;				// rotate icon 
    
    // ControlPanelCirc color popup; default values, if changed, changed in Default button; saved in .symap_props
    protected int colorSet=1;					// ColorSet 1 or 2;
    protected boolean bOrder=false, bRevOrder=false, bShuffle=false, bScaleColors=false;				
    protected double scaleColors=0.8;			
    protected int seed = 1;					
    
    private CircFrame frame;
	private DBconn2 dbc2;
	private Vector<Integer> colorVec;
	
	private Vector<Project> allProjVec;
	private TreeMap <Integer,Group> idx2GrpMap;
	private Vector<Integer> priorityVec;
	
    private int rOuter, rInner, rBlock; // rOuter based on rotateText,; rInner=rOuter*0.933; rBlock=rOuter*0.917;
    private int cX, cY;					// both are rOuter+marginX; set at beginning and do not change
    
    private JScrollPane scroller;		
    private HelpBar helpPanel = null;
    
    private void dprt(String msg) {symap.Globals.dprt("CP: " + msg);}
    
	public CircPanel(CircFrame frame, DBconn2 dbc2, HelpBar hb, int[] projIdxList, int refIdx,
			TreeSet<Integer> selGrpSet,  double [] lastParams) { // selGrpSet is for 2D
		this.frame = frame;
		this.dbc2 = dbc2;
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
			if (selGrpSet!=null && selGrpSet.size()>1) setLastParams(lastParams); // use previous Home settings when same ref
			if (!initDS(projIdxList, selGrpSet, refIdx)) return; // create allProjVec, idx2GrpMap, priortyVec
		}
		catch(Exception e) {ErrorReport.print(e, "Create CircPanel");}
	}
	/****************************************************************************
	 * XXX Setup and database load; called in 2D every time left panel changes
	 **************************************************************************/
	private boolean initDS(int[] projIdxList, TreeSet<Integer> selGrpSet, int refIdx) {
	try {
	// Projects and groups
		allProjVec = new Vector<Project>();
		idx2GrpMap = new TreeMap<Integer,Group> ();
		
		for (int i = 0; i < projIdxList.length; i++){ //allProjVec, allGrpsVec,idx2GrpMap
			if (projIdxList[i] > 0) {
				addProject(projIdxList[i]);
			}
		}
	
		if (selGrpSet != null){ // From 2d - selected chromosomes
			for (Group g : idx2GrpMap.values()){
				g.isSel = selGrpSet.contains(g.idx);
			}
		}
		for (Project p : allProjVec) p.setTotalSize(); 
		
		priorityVec = new Vector<Integer>();
		priorityVec.add(refIdx);	// initial reference has priority colors
		for (Project p : allProjVec){
			if (p.idx!=refIdx) priorityVec.add(p.idx);
		}
		
		setChrColorIdx(); 							// does not need colorVec, only allProjVec
		if (!initColorsFromProps()) return false; 	// color params are saved in .symap_props
		makeNewColorVec();			// create colorVec, use allGrpVec;
		
	// Blocks
		loadBlocks();			
		return true;
	}
	catch(Exception e) {ErrorReport.print(e, "Init data structures"); return false;}	
	}
	
	private void addProject(int idx)  {
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
	// try to make them always with same color regardless of comparison
	private void setChrColorIdx() {
		try {
			int maxGrps=0;
			for (Project p : allProjVec) maxGrps = Math.max(maxGrps, p.grps.size());
			
			if (allProjVec.size() <=4 && maxGrps<=25) {
				int i=0;
				int [] start = {0,24,49,74};
				for (Project p : allProjVec) {
					int inc = start[i];
					for (Group g : p.grps) g.colorIdx= inc++;
					i++;
				}
			}
			else {
				int inc=0;
				for (Project p : allProjVec) {
					for (Group g : p.grps) g.colorIdx= inc++;
				}
			}
		}
		catch(Exception e) {ErrorReport.print(e, "Set colors");}
	}
	// Get the blocks in both directions since order is unknown
	private void loadBlocks()  {
	try {
		ResultSet rs;
		String sql = "select grp1_idx, grp2_idx, start1, end1, start2, end2, corr from blocks ";
		
		for (int i1 = 0; i1 < allProjVec.size(); i1++) {
			Project p1 = allProjVec.get(i1);
			
			for (int i2 = i1; i2 < allProjVec.size(); i2++) {
				Project p2 = allProjVec.get(i2);
				
				rs = dbc2.executeQuery(sql +  " where proj1_idx=" + p1.idx + " and proj2_idx=" + p2.idx );
				while (rs.next()) {
					addBlock(rs.getInt(1), rs.getInt(2), rs.getInt(3),rs.getInt(4),rs.getInt(5),rs.getInt(6),(rs.getFloat(7)<0));
				}
				
				if (p1.idx != p2.idx) {
					rs = dbc2.executeQuery(sql + " where proj1_idx=" + p2.idx + " and proj2_idx=" + p1.idx );
					while (rs.next()) {
						addBlock(rs.getInt(1), rs.getInt(2), rs.getInt(3),rs.getInt(4),rs.getInt(5),rs.getInt(6),(rs.getFloat(7)<0));
					}
				}
			}
		}
	}
	catch(Exception e) {ErrorReport.print(e, "Add project for Circle Panel");}	
	}
	private void addBlock(int gidx1, int gidx2, int s1, int e1, int s2, int e2, boolean inv) {
		Block b = new Block(gidx1, gidx2, s1, e1, s2, e2, inv);
		
		Group grp = idx2GrpMap.get(gidx1);
		grp.addBlock(b);
	}
	protected JScrollPane getScrollPane() {return scroller;}
	
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
	protected void makeNewColorVec() { // ControlPanelCirc.ChgColor
		colorVec = frame.getColorVec(colorSet, bScaleColors, scaleColors, bOrder, bRevOrder, bShuffle, seed);;
		
		int nColors = 0;
		for (Project p : allProjVec) nColors += p.grps.size();
		while (colorVec.size() < nColors) colorVec.addAll(colorVec); // duplicates set if >100 chromosomes
	}
	// int invChoice; boolean bShowSelf, bRotateText, bToScale, bShowSelf; double zoom; int arc0
	// invChoice is set in ControlPanelCirc in CircFrame; the rest are directly used
	protected double [] getLastParams() { // called from CircFrame to reuse in the next CircFrame
		double [] lp = new double [7];
		lp[0] = (double) invChoice;
		lp[1] = (bShowSelf) ? 1 : 0;
		lp[2] = (bRotateText) ? 1 : 0;
		lp[3] = (bToScale) ? 1 : 0;
		lp[4] = (bShowSelf) ? 1 : 0;
		lp[5] = zoom;
		lp[6] = arc0;
		
		return lp;
	}
	protected void setLastParams(double [] lp) {
		if (lp==null || lp.length!=7) return;
		
		invChoice = 	(int) lp[0]; 
		bShowSelf = 	(lp[1]==1);
		bRotateText = 	(lp[2]==1);
		bToScale = 		(lp[3]==1);
		bShowSelf = 	(lp[4]==1);
		zoom = 			lp[5];
		arc0 = 			(int) lp[6];
	}
	protected void saveColors() { // on ControlPanelCirc.Save 
		PersistentProps cookies = new PersistentProps();
		PersistentProps allCook = cookies.copy(propName);
		
		String c=colorSet + ":";
		c += (bScaleColors) ? scaleColors + ":" : "0:";
		
		if (bOrder)         c += "1:0";
		else if (bRevOrder) c+= "2:0";
		else if (bShuffle)  c+= "3:" + seed;
		else                c += "0:0";
		
		allCook.setProp(c);
	}
	
	/*******************************************************************
	 * Control - toggles are directly changed from ControlPanel
	 *****************************************************************/
	protected void zoom (double f) { // -/= icons
		zoom *= f;				
		if (zoom<0.3) zoom=0.3; 
		else if (zoom>2.5) zoom=2.5;
	    makeRepaint();  
	}
	protected void rotate(int da) { 
		arc0 += da;
		if (Math.abs(arc0)>=360) arc0=0; 
		makeRepaint();
	}
	protected void reverse() {     // reverse reference; only available for 2 WG view
	try {
		Project p1 = allProjVec.get(0);
		Project p2 = allProjVec.get(1);
		allProjVec.clear();
		allProjVec.add(p2);
		allProjVec.add(p1);
		
		setChrColorIdx();
	}
	catch (Exception e) {ErrorReport.print(e, "Reverse projects");}
	}	
	/***************************************************
	 * XXX Mouse
	 */
	public void handleClick(long x, long y, boolean dbl) {}
	
	public void mouseClicked(MouseEvent evt)  { // Click arc or project name
		int xRel = (int)evt.getX() - cX;
		int yRel = (int)evt.getY() - cY;
	
		int r = (int)Math.sqrt(xRel*xRel + yRel*yRel);
		
		int angle = -arc0 + (int)(180*Math.atan2(-yRel,xRel)/Math.PI);
		if (angle < 0) angle += 360;
	
		Group grp = overGroupArc(r,angle);

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
		
		Project p = overProjName(r,angle, (int)evt.getX(), (int)evt.getY());
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
		
		int xRel = (int)e.getX() - cX;
		int yRel = (int)e.getY() - cY;
		int r = (int)Math.sqrt(xRel*xRel + yRel*yRel);
		int angle = -arc0 + (int)(180*Math.atan2(-yRel,xRel)/Math.PI);
		if (angle < 0) angle += 360;
		Group grp = overGroupArc(r,angle);

		if (grp != null) {
			setCursor( Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) );
			helpPanel.setHelp(helpArc,this); 
		}
		else {
			Project p = overProjName(r,angle,(int)e.getX(), (int)e.getY());
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
	private Group overGroupArc(int r, int angle) { // mouse over arc
		if (r <= rOuter && r >= rInner) {
			for (Group g : idx2GrpMap.values()){
				if (angle >= g.a1 && angle <= g.a2) return g;
			}
		}	
		return null;
	}
	private Project overProjName(int r, int angle, int x, int y){ 
		int x1 = (bRotateText) ? r : x;
		int x2 = (bRotateText) ? angle : y;
		
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
	
	protected void makeRepaint() {repaint();} // ControlPanelCirc
	
	// Note that circle angles are measured from the middle-right of the circle, going around counterclockwise. 
	// Hence, the right side of the circle is angle 0-90, plus angle 270-360. Arc0 is added to everything.  
	public void paintComponent(Graphics g)  {
		super.paintComponent(g); 
		
		final int PROJ_EMPTY_ARC = 20;
		
	// Setup 
		Dimension d = new Dimension( (int)(scroller.getWidth()*zoom), (int)(scroller.getHeight()*zoom) );
		Dimension dimCircle = new Dimension(d.width-20, d.height-20);
		
	    int maxNameW = 0;
    	FontMetrics fm = g.getFontMetrics();
        for (Project p : allProjVec) {
            int nw = (int)(fm.getStringBounds(p.displayName,g).getWidth()) + 
            		 (int)(fm.getStringBounds(p.maxGrpName,g).getWidth());		
            if (nw > maxNameW) maxNameW = nw;
        } 
	    
	    Dimension dd = new Dimension(d.width+maxNameW-20, d.height-20); 
	    setPreferredSize(dd); 
		revalidate();
		scroller.getViewport().revalidate();
        
    	int center = Math.min(dimCircle.width, dimCircle.height)/2;
	    rOuter = center - 100; 
	    rInner = (int)(rOuter*0.933);
	    rBlock = (int)(rOuter*0.917);
	    int off = (allProjVec.size()%2==1) ? (maxNameW/2) : 15; // 1,3,5... will use name
        cX = center + off;		
        cY = center;    
        
        int cXrOuter = cX-rOuter, cYrOuter = cY-rOuter; 
        int cXrInner = cX-rInner, cYrInner = cY-rInner;
        int rOuter2  = rOuter*2,  rInner2  = rInner*2;
 
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        double totalSizeShown = 0;
        int nEmpty = 0;
        
        for (Project prjObj : allProjVec) {
        	if (prjObj.projSizeShown == 0) 	nEmpty++;
        	else 							totalSizeShown += prjObj.projSizeShown;
        }        
        if (totalSizeShown == 0) {System.out.println("Nothing to show!");return;}

        int curArc = 0, curColor = 0;				// increments through projects
        
    // Loop through projects draw name, arc, ribbons 
        for (Project prjObj : allProjVec) {
            int projArc = 360/allProjVec.size(); 
           
            if (bToScale) { 
            	if (prjObj.projSizeShown == 0) {
            		projArc = PROJ_EMPTY_ARC;
            	}
            	else {
            		double empty = (360.0 - ((double)(nEmpty*PROJ_EMPTY_ARC)));
            		projArc = (int) Math.floor((empty*prjObj.projSizeShown)/totalSizeShown);
            	}
            }
   
            if (prjObj.projSizeShown == 0) { // on 2D, no chr selected for this project
            	g.drawArc(cXrOuter,   cYrOuter,   rOuter2,   rOuter2,   curArc, projArc-1);//x,y,w,h, rotate, extent
                g.drawArc(cXrInner-1, cYrInner-1, rInner2+2, rInner2+2, curArc, projArc-1);
                
                g.drawLine((int) circX(cX, rInner, curArc), (int)circY(cY, rInner, curArc), 
                		   (int) circX(cX, rOuter, curArc), (int)circY(cY, rOuter, curArc));
                int endArc = curArc+projArc-1; 
                g.drawLine((int) circX(cX, rInner+1, endArc), (int)circY(cY, rInner+1, endArc), 
                		   (int) circX(cX, rOuter,   endArc), (int)circY(cY, rOuter,   endArc));
            }
     
            int nextArc = curArc + projArc;
        	if (nextArc > 360) nextArc = 360;	
        	
        	// Project name
        	int sp = (bRotateText) ? 40 : 20; 
        	int midArc = (curArc + nextArc)/2;
            paintProjName(g, prjObj, midArc, rOuter + sp); // Sets font for name and chromosome numbers
            
        	prjObj.setGrpArc(curArc, nextArc-1, curColor); // set start/end of grps; this makes a 1 degree gap between projects
 
        	Font f = g.getFont();
        	
        	// Loop through chromosomes to draw chr# and chr outer arc
        	for (Group grpObj : prjObj.grps) {
        		if (!grpObj.isSel) continue;
        		if (grpObj.colorIdx>=colorVec.size()) {
        			dprt("Color out of range " + grpObj.colorIdx + " " + colorVec.size());
        			grpObj.colorIdx=colorVec.size()-1;
        		}
        		g.setColor(new Color(colorVec.get(grpObj.colorIdx)));
        		g.fillArc(cXrOuter, cYrOuter, rOuter2, rOuter2, arc0+grpObj.a1, grpObj.a2-grpObj.a1);
        		g.setColor(Color.black);
        		
        		double aMid = arc0 + (grpObj.a1 + grpObj.a2)/2;
    			
    			if (bRotateText) {
    				double rotAngle = 90 - aMid;
        			while (rotAngle > 360) {rotAngle -= 360;}
        			while (rotAngle < 0)   {rotAngle += 360;}
        			
    				AffineTransform rot = AffineTransform.getRotateInstance(rotAngle*Math.PI/180);
    				Font fRot = g.getFont().deriveFont(rot);
	    			g.setFont(fRot);        		
	        		g.drawString(grpObj.grpName, (int)circX(cX,rOuter+10,aMid), (int)circY(cY,rOuter+10,aMid));
	        		g.setFont(f);
    			}
    			else {
    				while (aMid > 360) {aMid -= 360;} 
    				while (aMid < 0)   {aMid += 360;}
    				
        			// Make arc measure with zero point vertical
                	double vArc = aMid - 90;
        			while (vArc > 360) {vArc -= 360;}
        			while (vArc < 0)   {vArc += 360;}

	                double grW = fm.getStringBounds(grpObj.grpName,g).getWidth();		
				
    				int pX = (int)circX(cX,rOuter+10,aMid);
    				int pY = (int)circY(cY,rOuter+10,aMid);
    				
    				pX -= (int)(grW* (1.0-Math.abs(aMid-180.0)/180.0));  // (((double)grW)*Math.sin(aMid*Math.PI/360));
    				pY += (int)(10.0*(1.0-Math.abs(vArc-180.0)/180.0));  // 0 at the top, 10 at the bottom;
    				
    				g.drawString(grpObj.grpName, pX, pY);   
    			}
        	}
        	
        	curColor += prjObj.grps.size();
        	curArc = nextArc;
        } // end loop through projects
        
        ////////  Paint ribbons /////////
        g.setColor(Color.white);
        g.fillArc(cXrInner, cYrInner, rInner2, rInner2, 0, 360);	

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
      
      // NOTE: blocks are assigned to only one chromosome of the ribbon pair
      // Group Loop 1: paint the non-on-top groups.
        if (!showOne) {										 
	        for (Group grp : idx2GrpMap.values()) {
	        	if (!grp.isSel) continue;
	        	
	        	for (Block b : grp.blocks) {
	        		if (invChoice == 1 && !b.inverted) continue;
	        		if (invChoice == 2 &&  b.inverted) continue;
	 
	        		Group grp1 = idx2GrpMap.get(b.gidx1); 
	        		Group grp2 = idx2GrpMap.get(b.gidx2);
	        		
	        		if (!bShowSelf && (grp1.proj_idx == grp2.proj_idx)) continue;
	        		
	        		if (!grp1.isSel || !grp2.isSel) continue;
	        		
	        		if (grp1.onTop || grp2.onTop) continue;
	        	
	        		paintGroupBlk(g, g2, grp1, grp2, b);
	        	}
	        }
        }
     // Group Loop 2: paint the on-top group.
        if (onTop) { 							
	        for (Group grp : idx2GrpMap.values()) {
	        	if (!grp.isSel) continue;
	        	
	        	for (Block b : grp.blocks) {
	        		if (invChoice == 1 && !b.inverted) continue;
	        		if (invChoice == 2 && b.inverted) continue;
	
	        		Group grp1 = idx2GrpMap.get(b.gidx1);
	        		Group grp2 = idx2GrpMap.get(b.gidx2);
	        		if (!bShowSelf && (grp1.proj_idx == grp2.proj_idx)) continue;
	        		
	        		if (!grp1.isSel || !grp2.isSel) continue;
	        		
	        		if (grp.onTop || grp2.onTop) 
		        		paintGroupBlk(g, g2, grp1, grp2, b);
	        	}
	        }
        }
	}
	// Paint Block
	private void paintGroupBlk(Graphics g, Graphics2D g2, Group grp1, Group grp2, Block blk) {
		
		int cidx = (colorFirstPriority(grp1.proj_idx, grp2.proj_idx) ? grp1.colorIdx : grp2.colorIdx);
		if (invChoice != 3) g.setColor(new Color(colorVec.get(cidx)));
		else                g.setColor(blk.inverted ? blockN : blockP);
	       		
		int s1 = grp1.arcLoc(blk.s1);
		int e1 = grp1.arcLoc(blk.e1);
		int s2 = grp2.arcLoc(blk.s2);
		int e2 = grp2.arcLoc(blk.e2);
		int cXrBlk = cX-rBlock, cYrBlk = cY-rBlock, rBlk2 = 2*rBlock; 
		int s1arc0 = s1 + arc0, e1arc0 = e1 + arc0, s2arc0 = s2 + arc0, e2arc0 = e2 + arc0;
	
		GeneralPath gp = new GeneralPath();
		
		gp.moveTo(circX(cX, rBlock, s1arc0), circY(cY, rBlock, s1arc0));
		
		Arc2D arc1 = new Arc2D.Double(cXrBlk, cYrBlk, rBlk2, rBlk2, s1arc0, e1-s1, Arc2D.OPEN);//x,y,w,h,arc,extent
		gp.append(arc1, true);
		
		gp.quadTo(circX(cX, 0, e1arc0), circY(cY, 0, e1arc0), circX(cX, rBlock, s2arc0), circY(cY, rBlock, s2arc0));

		Arc2D arc2 = new Arc2D.Double(cXrBlk, cYrBlk, rBlk2, rBlk2, s2arc0, e2-s2, Arc2D.OPEN);
		gp.append(arc2, true);
		
		gp.quadTo(circX(cX, 0, e2arc0), circY(cY, 0, e2arc0), circX(cX, rBlock, s1arc0), circY(cY, rBlock, s1arc0));
		
		gp.closePath();
		g2.fill(gp);
	}
	// Paint project name
	private void paintProjName(Graphics g, Project prjObj, int midArc, int locLabel) {
		g.setColor(Color.black);
       
		Font f = g.getFont();
    	f = setProjFont(0, f, prjObj.idx);
    	g.setFont(f);
		    	
		FontMetrics fm = g.getFontMetrics();
		int nameH =   (int) (fm.getStringBounds(prjObj.displayName,g).getHeight());
		int nameW =   (int) (fm.getStringBounds(prjObj.displayName,g).getWidth());
       
    	if (bRotateText) {
    		int unrotW =  (int) (nameW/2); 
	        int nameArcW = 0;
	        if (unrotW > 0 && unrotW < 200) // exclude pathological values
	        	nameArcW = (int)((180/Math.PI) * Math.atan(((double)unrotW/(double)(locLabel))));
	        else dprt("Special case for " + prjObj.displayName + " " + unrotW );

			// Put the angle within 0-360 range  for texts that do not work mod 360
			double rotAngle = 90 - midArc - arc0;
			while (rotAngle > 360) {rotAngle -= 360;} 
			while (rotAngle < 0)   {rotAngle += 360;}

    		int arc = midArc + arc0 + nameArcW;
    		int nameX = (int) circX(cX, locLabel, arc);
        	int nameY = (int) circY(cY, locLabel, arc);
        	
    		AffineTransform rot = AffineTransform.getRotateInstance(rotAngle*Math.PI/180);
    		Font fRot = g.getFont().deriveFont(rot);
    		g.setFont(fRot);
    		g.drawString(prjObj.displayName, nameX, nameY);
    		g.setFont(f);
    		
    		// mouse over coords; finger occurs passed the name in all 4 directions, but also right on text.
    		prjObj.labelR1 = locLabel - nameH;
    		prjObj.labelR2 = locLabel + nameH;
    		prjObj.labelA1 = midArc - nameArcW;
    		prjObj.labelA2 = midArc + nameArcW;
    	}
    	else {
    		double correctedArc = midArc + arc0;
			while (correctedArc > 360) {correctedArc -= 360;}
			while (correctedArc < 0)   {correctedArc += 360;}

			// Make arc measure with zero point vertical
        	double vArc = correctedArc - 90;
			while (vArc > 360) {vArc -= 360;}
			while (vArc < 0)   {vArc += 360;}
			
        	int nameX = (int) circX(cX, rOuter + 40, correctedArc); 															
        	int nameY = (int) circY(cY, rOuter + 40, correctedArc);  

        	int xOff = (int)(nameW*(1.0-Math.abs(correctedArc-180.0)/180.0)); // 0 at the right, 1 at the left
        	nameX -=  xOff;
        	int yOff = (int)(10.0*(1.0-Math.abs(vArc-180.0)/180.0));  // 0 at the top, 10 at the bottom;
        	nameY += yOff;
        	
    		g.drawString(prjObj.displayName,nameX,nameY);
    		
    		// mouse-over coords;
    		prjObj.labelR1 = nameX - 1; 
    		prjObj.labelR2 = nameX + nameW;
    		prjObj.labelA1 = nameY - nameH; // x,y is lower left
    		prjObj.labelA2 = nameY + 1;
    	}
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
	private int circX(int cX, int R, double arc){
		return cX + (int)(R * Math.cos(2*Math.PI*arc/360));
	}
	private int circY(int cY, int R, double arc) {
		return cY - (int)(R * Math.sin(2*Math.PI*arc/360)); // y increases down
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
		arc0 = ARC_DEFAULT;
		bToScale =  bRotateText = false;
	}
	protected boolean isHome() { 
		return zoom==ZOOM_DEFAULT && arc0==ARC_DEFAULT && !bToScale && !bRotateText;
	}
	public String getHelpText(MouseEvent event) { return helpText;} // frame.HelpBar
	/************************************************************************
	 * Classes
	 **********************************************************************/
	private class Project {
		private int idx;
		
		private String displayName;							// load from db
		private Vector<Group> grps = new Vector<Group>();	// load from db
		private String maxGrpName="";						
		private double projSizeShown = 0;					// selected groups in 2D
		
		private int labelR1,labelR2,labelA1,labelA2;		// mouse-over coords; set in paintProjText
		
		private Project(int idx, String name, DBconn2 dbc2)  {
		try {
			this.idx = idx;
	
			ResultSet rs = dbc2.executeQuery("select idx, name, length from xgroups join pseudos " + 
						" on pseudos.grp_idx=xgroups.idx where xgroups.proj_idx=" + idx + " order by sort_order");

			while (rs.next()){
				Group g = new Group(rs.getInt(3), rs.getInt(1), idx, rs.getString(2), name);
				grps.add(g);
				if (g.grpName.length()>maxGrpName.length()) maxGrpName=g.grpName; 
			}
			
			Mproject tProj = new Mproject();
			String display_name = tProj.getKey(tProj.sDisplay);
			
			displayName = name;
			rs = dbc2.executeQuery("select value from proj_props where name='"+display_name+"' and proj_idx=" + idx);
			if (rs.next()) displayName = rs.getString(1); 
		}
		catch (Exception e) {ErrorReport.print(e, "Getting project");}
		}
		
		private void setTotalSize() {
			for (Group g : grps){
				if (g.isSel) projSizeShown += ((double)g.size);
			}
		}	
		private void setGrpArc(int a1, int a2, int cidx){
			int cnt = 0, nShown = 0;
			for (Group g : grps) 
				if (g.isSel) nShown++;
			
			double arcLen = a2 - a1; 
			double a = a1;
			double grpSize = 0;
			
			for (Group g : grps) {
				if (!g.isSel) continue;
				
				grpSize += g.size;
				cnt++;
				double grpEndPos = (arcLen*grpSize) / projSizeShown;
				
				long b;
				if (cnt < nShown) b = a1 + (long)(Math.round(grpEndPos));
				else 			  b = a2;
				
				g.a1 = (int) a;
				g.a2 = (int) b;
				
				a = b;
			}
		}
	}// end project class
	
	// Chromosome and respective blocks
	private class Group {
		private double size;
		private int idx, proj_idx;
		private String grpName;
		private int a1, a2;		 		// Assigned values in setGrpArc during paintComponent
		private int colorIdx = -1; 		// index into color vector
		private boolean isSel = true; 	// 2D is selected
		private boolean onTop = false, onlyShow = false; // click, double click
		private Vector <Block> blocks = new Vector <Block> ();
		
		private Group(int size, int idx, int proj_idx, String name, String pname){
			this.size = size;
			this.idx = idx;
			this.proj_idx = proj_idx;
			this.grpName = name;		// chromosome number
		}
		private void addBlock(Block b) {blocks.add(b);}
		
		private int arcLoc(double bpLoc) { // paintArc
			double a = a2 - a1 + 1;
			double arcLoc = ((a*bpLoc)/((double)size));
			return a1 + (int)arcLoc;
		}
	}
	private class Block {
		private int s1, e1, s2, e2;
		private int gidx1, gidx2;
		private boolean inverted;
		
		private Block(int gidx1, int gidx2, int s1, int e1, int s2, int e2, boolean inv) {
			this.gidx1 = gidx1; this.gidx2 = gidx2;
			this.s1 = s1; 		this.e1 = e1;
			this.s2 = s2; 		this.e2 = e2;
			this.inverted = inv;
		}
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
