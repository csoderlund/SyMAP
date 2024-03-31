package circview;

import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.util.Vector;
import java.util.TreeMap;
import java.awt.geom.AffineTransform;
import java.util.TreeSet;

import java.awt.geom.GeneralPath;
import java.awt.geom.Arc2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

import database.DBconn2;
import symap.frame.HelpBar;
import symap.frame.HelpListener;
import symap.manager.Mproject;
import util.ErrorReport;

/**************************************************************
 * Draws the circle view for Selected-2-WG and 2D-N-chr
 * CAS521 cleanup and some rewrite
 * 		2-WG add reverse
 * CAS533 was accessing DotPlot just to get static background color.white
 */
public class CircPanel extends JPanel implements HelpListener, MouseListener,MouseMotionListener {
	private static final long serialVersionUID = -1711255873365895293L;
	
	// ControlPanelCirc has Info popup with this 
	private final String helpText = "Click an arc to bring its blocks to the top.\n" + 
			  "Move mouse near name and click to use its colors.";
	private final String helpName = "Move mouse near name (changes to finger) and click to color by this project.";
	private final String helpArc =  "Click arc to bring its blocks to top.\nDouble click arc to only show its blocks.";
	private final double ZOOM_DEFAULT = 1.0; // CAS552 was float
	private final int ARC_DEFAULT = 0;
	
	 // set from ControlPanelCirc
    protected int invChoice = 0;
    protected boolean bShowSelf = false, bRotateText = false, bRevRef = false;
    protected boolean bToScale = false;			// genome size
    protected double zoom = ZOOM_DEFAULT;		// +/- icons
    private int arc0 = ARC_DEFAULT;				// rotate icon 
    
	private DBconn2 dbc2;
	private Vector<Integer> colorVec;
	
	private Vector<Project> allProjVec;
	private TreeMap<String, String> name2DisMap = new TreeMap<String, String> (); // CAS517 project display name
	private TreeMap <Integer,Group> idx2GrpMap;
	private Vector<Integer> priorityVec;
	
    private int rOuter, rInner, rBlock; // rOuter based on rotateText,; rInner=rOuter*0.933; rBlock=rOuter*0.917;
    private int cX, cY;					// both are rOuter+marginX; set at beginning and do not change
    
    private JScrollPane scroller;		// this scroll bar never did work; CAS552 does now
    private HelpBar helpPanel = null;
    private Dimension dim;
    
    //private void dprt(String msg) {symap.Globals.dprt("CP: " + msg);}
    
	public CircPanel(DBconn2 dbReader, int[] projIdxList, TreeSet<Integer> selGrpSet, int refIdx,
							HelpBar hb, Vector <Integer> mCol) {
		dbc2 = dbReader;
		helpPanel = hb;
		colorVec = mCol;
		
		dim  = new Dimension();		// CAS552 moved init() lines here
		setBackground(Color.white);
		
		scroller = new JScrollPane(this);
		scroller.setBackground(Color.white);
		scroller.getViewport().setBackground(Color.white);
		scroller.getVerticalScrollBar().setUnitIncrement(10); 
		
		helpPanel.addHelpListener(this); 
		addMouseListener(this);
		addMouseMotionListener(this);
		
		try {
			if (!initDS(projIdxList, selGrpSet, refIdx)) return;
		}
		catch(Exception e) {ErrorReport.print(e, "Create CircPanel");}
	}
	/****************************************************************************
	 * XXX Setup and database load
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
		setChrColors();
		
		if (selGrpSet != null){ // From 2d - selected chromosomes
			for (Group g : idx2GrpMap.values()){
				g.shown = selGrpSet.contains(g.idx);
			}
		}
	// Colors - starts with 101 colors. Make sure there is enough
		priorityVec = new Vector<Integer>();
		priorityVec.add(refIdx);	// CAS521 added so the reference has priority colors
		for (Project p : allProjVec){
			if (p.idx!=refIdx) priorityVec.add(p.idx);
		}
		
		int nColors = 0;
		for (Project p : allProjVec) nColors += p.grps.size();
		while (colorVec.size() < nColors + 1) colorVec.addAll(colorVec);
		
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
			if (!rs.first()) {
				System.out.println("Cannot find proj_idx=" + idx);
				return;
			}

			Project proj = new Project(idx, rs.getString(1), dbc2); // loads groups
			allProjVec.add(proj);
			
			for (Group g : proj.grps) idx2GrpMap.put(g.idx, g);				
		}
		catch(Exception e) {ErrorReport.print(e, "Add project for Circle Panel");}	
	}
	// CAS521 try to make them always with same color regardless of comparison
	private void setChrColors() {
		try {
			int maxGrps=0;
			for (Project p : allProjVec) maxGrps = Math.max(maxGrps, p.grps.size());
			
			if (allProjVec.size() <=4 && maxGrps<=25) {
				int i=0;
				int [] start = {0,24,49,74};
				for (Project p : allProjVec) {
					int inc = start[i];
					for (Group g : p.grps) g.cStart= inc++;
					i++;
				}
			}
			else {
				int inc=0;
				for (Project p : allProjVec) {
					for (Group g : p.grps) g.cStart= inc++;
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
	
	/*******************************************************************
	 * Control - toggles are directly changed from ControlPanel
	 *****************************************************************/
	protected void zoom (double f) { // -/= icons
		zoom *= f;
	    makeRepaint();  
	}
	protected void rotate(int da) { // rotate icon; rotate(-10) 
		arc0 += da;
		if (arc0 > 360) arc0 -= 360;
		makeRepaint();
	}
	protected void reverse() {     // reverse reference; only available for 2 WG view
	try {
		Project p1 = allProjVec.get(0);
		Project p2 = allProjVec.get(1);
		allProjVec.clear();
		allProjVec.add(p2);
		allProjVec.add(p1);
		
		setChrColors();
	}
	catch (Exception e) {ErrorReport.print(e, "Reverse projects");}
	}	
	/***************************************************
	 * XXX Mouse
	 */
	public void handleClick(long x, long y, boolean dbl) {}
	
	public void mouseClicked(MouseEvent evt)  {
		int xRel = (int)evt.getX() - cX;
		int yRel = (int)evt.getY() - cY;
		
		int r = (int)Math.sqrt(xRel*xRel + yRel*yRel);
		
		int angle = -arc0 + (int)(180*Math.atan2(-yRel,xRel)/Math.PI);
		if (angle < 0) angle += 360;
		
		Group grp = overGroupArc(r,angle);

		boolean dbl = (evt.getClickCount() > 1);
		
		if (grp != null) { // click the group arc
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
		
		Project p = overProjName(r,angle);
		if (p == null) return;
		
		// click the project name
		priorityVec.add(0,p.idx); 
		int i = 1;
		for (; i < priorityVec.size();i++) {
			if (priorityVec.get(i) == p.idx) break;
		}
		priorityVec.remove(i);
		
		for (Project p1 : allProjVec) { // pre-v521 this was not necessary and onlyShow remained.
			for (Group g1 : p1.grps) {
				g1.onTop = (p1.idx==p.idx) ? true : false;
			}
		}
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
			String dName = name2DisMap.containsKey(grp.projName) ? name2DisMap.get(grp.projName) : grp.projName; // CAS517
			helpPanel.setHelp(dName + "/" + grp.name + "\n" + helpArc,this);
		}
		else {
			Project p = overProjName(r,angle);
			if (p != null) {
				setCursor( Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) );
				helpPanel.setHelp(name2DisMap.get(p.name) + "\n" + helpName, this);
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
				if (angle >= g.a1 && angle <= g.a2)return g;
			}
		}	
		return null;
	}
	private Project overProjName(int r, int angle){// mouse over name
		for (Project p : allProjVec){
			if (r >= p.labelR1 && r <= p.labelR2){
				if (angle >= p.labelA1 && angle <= p.labelA2) return p;
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
		
		final int projEmptyArc = 20;
		
	// Setup 
		Dimension d = new Dimension( (int)(scroller.getWidth()*zoom), 
				                     (int)(scroller.getHeight()*zoom) );
		
		dim.width =  (int) (d.width - 20); // so the scroll doesn't show at zoom=1
		dim.height = (int) (d.height - 20);
		
		setPreferredSize(dim); // CAS552 added these 3 lines to make scrollbar work
		revalidate();
		scroller.getViewport().revalidate();
	
	    int maxNameW = 0;
	    if (!bRotateText) { // Rotate does not need it, it causes the Circle to be diff sizes
	        for (Project p : allProjVec) {
	            FontMetrics fm = g.getFontMetrics();
	            int nameW = (int)(fm.getStringBounds(p.displayName,g).getWidth());		
	            if (nameW > maxNameW) maxNameW = nameW;
	        }  
	    }
        int marginX = 50 + Math.max(60, maxNameW); // spacing at top/bottom; 
	
	    rOuter = Math.min(dim.width, dim.height)/2 - marginX;
	    rInner = (int)(rOuter*0.933);
	    rBlock = (int)(rOuter*0.917);	
        cX = rOuter + marginX;
        cY = rOuter + marginX;
        
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        double totalSizeShown = 0;
        int nEmpty = 0;
        
        for (Project p : allProjVec) {
        	double shown =  p.totalSizeShown();
        	totalSizeShown += shown;
        	if (shown == 0) nEmpty++;
        }        
        if (totalSizeShown == 0) {System.out.println("Nothing to show!");return;}
        
        int curArc = 0;				// increments through projects
        int curColor = 0;
        
    // Loop through projects draw project name and chromosomes 
        for (Project prjObj : allProjVec) {
            int projArc = 360/allProjVec.size();
            
            if (prjObj.totalSizeShown() == 0) {
                g.drawArc(cX - rOuter, cY - rOuter, 2*rOuter, 2*rOuter, curArc, projArc - 1);
                g.drawArc(cX - rInner - 1, cY - rInner - 1, 2*rInner + 2, 2*rInner + 2, curArc, projArc - 1);
                g.drawLine((int) circX(cX, rInner, curArc), (int)circY(cY, rInner, curArc), 
                		   (int) circX(cX, rOuter, curArc), (int)circY(cY, rOuter, curArc));
                g.drawLine((int) circX(cX, rInner+1, curArc + projArc - 1), (int)circY(cY, rInner+1,curArc + projArc - 1), 
                		   (int) circX(cX, rOuter,   curArc + projArc - 1),   (int)circY(cY, rOuter,curArc + projArc - 1));
            }
     
            if (bToScale) {
            	if (prjObj.totalSizeShown() == 0) {
            		projArc = projEmptyArc;
            	}
            	else {
            		double shown = prjObj.totalSizeShown();
            		projArc = (int) Math.floor(((360.0 - ((double)(nEmpty*projEmptyArc)))*shown)/totalSizeShown);
            	}
            }
      
            int nextArc = curArc + projArc;
        	int midArc = (curArc + nextArc)/2;
        	
        	// Project name
        	int sp = (bRotateText) ? 40 : 30; // CAS552 was constant 40;
            paintProjText(g, prjObj, midArc, rOuter + sp); 
        		
        	if (nextArc > 360) nextArc = 360;
        	prjObj.setArc(curArc, nextArc - 1, curColor); // this makes a 1 degree gap between projects
        	
        	Font f = g.getFont();
        	FontMetrics fm = g.getFontMetrics();
        	
        	// Loop through chromosomes to draw chr# 
        	for (Group grpObj : prjObj.grps) {
        		if (!grpObj.shown) continue;
        		
        		g.setColor(new Color(colorVec.get(grpObj.cStart)));
        		g.fillArc(cX - rOuter, cY - rOuter, 2*rOuter, 2*rOuter, arc0 + grpObj.a1, grpObj.a2 - grpObj.a1);
        		g.setColor(Color.black);
        		
        		double aMid = arc0 + (grpObj.a1 + grpObj.a2)/2;
    			double rotAngle = 90 - aMid;
    			while (rotAngle > 360) {rotAngle -= 360;}
    			while (rotAngle < 0) {rotAngle += 360;}
    			
    			if (bRotateText) {
    				AffineTransform rot = AffineTransform.getRotateInstance(rotAngle*Math.PI/180);
    				Font fRot = g.getFont().deriveFont(rot);
	    			g.setFont(fRot);        		
	        		g.drawString(grpObj.name, (int)circX(cX,rOuter+10,aMid), (int)circY(cY,rOuter+10,aMid));
	        		g.setFont(f);
    			}
    			else {
    				while (aMid > 360) {aMid -= 360;} 
    				while (aMid < 0) {aMid += 360;}
    				
        			// Make arc measure with zero point vertical
                	double vArc = aMid - 90;
        			while (vArc > 360) {vArc -= 360;}
        			while (vArc < 0) {vArc += 360;}

	                double grW = fm.getStringBounds(grpObj.name,g).getWidth();		
				
    				int pX = (int)circX(cX,rOuter+10,aMid);
    				int pY = (int)circY(cY,rOuter+10,aMid);
    				
    				pX -=       (int)(grW*(1.0-Math.abs(aMid-180.0)/180.0));   //  (int)(((double)grW)*Math.sin(aMid*Math.PI/360));
                	int yOff =  (int)(10.0*(1.0-Math.abs(vArc-180.0)/180.0));  // 0 at the top, 10 at the bottom;

    				pY += yOff;
    				
    				g.drawString(grpObj.name, pX, pY);   
    			}
        	}
        	curColor += prjObj.grps.size();
        	curArc = nextArc;
        } // end loop through projects
        
      ////////  Paint ribbons /////////
        g.setColor(Color.white);
        g.fillArc(cX - rInner, cY - rInner, 2*rInner, 2*rInner, 0, 360);	

        float alpha = .7f;
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        boolean showOne = false;
        for (Group grp : idx2GrpMap.values()) {
        	if (grp.onlyShow) {
        		showOne = true;
        		break;
        	}
        }
      // Group Loop 1: paint the non-on-top groups.
        for (Group grp : idx2GrpMap.values()) {
        	if (!grp.shown) continue;
        	if (showOne) continue; 
        	
        	for (Block b : grp.blocks) {
        		if (invChoice == 1 && !b.inverted) continue;
        		if (invChoice == 2 &&  b.inverted) continue;
 
        		Group grp1 = idx2GrpMap.get(b.gidx1); // allGrpsMap.get(b.g1)
        		Group grp2 = idx2GrpMap.get(b.gidx2);
        		
        		if (!bShowSelf && (grp1.proj_idx == grp2.proj_idx)) continue;
        		
        		if (!grp1.shown || !grp2.shown) continue;
        		
        		if (grp1.onTop || grp2.onTop) continue;
        		
        		paintArc(g, g2, grp1, grp2, b);
        	}
        }
     // Group Loop 2: paint the on-top group.
        for (Group grp : idx2GrpMap.values()) {
        	if (!grp.shown) continue;
        	
        	for (Block b : grp.blocks) {
        		if (invChoice == 1 && !b.inverted) continue;
        		if (invChoice == 2 && b.inverted) continue;

        		Group grp1 = idx2GrpMap.get(b.gidx1);
        		Group grp2 = idx2GrpMap.get(b.gidx2);
        		if (!bShowSelf && (grp1.proj_idx == grp2.proj_idx)) continue;
        		
        		if (!grp1.shown || !grp2.shown) continue;
        		
        		if (grp1.onTop || grp2.onTop) {
        			paintArc(g, g2, grp1, grp2, b);
        		}
        	}
        }       
	}
	// Paint Arc
	private void paintArc(Graphics g, Graphics2D g2, Group gr1, Group gr2, Block b) {
		int cidx = (colorFirstPriority(gr1.proj_idx, gr2.proj_idx) ? gr1.cStart : gr2.cStart);
		if (invChoice != 3) 
			g.setColor(new Color(colorVec.get(cidx)));
		else // CAS521 for 1st loop, was g.setColor(!b.inverted ? Color.red.brighter() : Color.green.darker());
			g.setColor(b.inverted ? Color.green.brighter() : Color.red.darker());
	       		
		int a1 = gr1.arcLoc(b.s1);
		int a2 = gr1.arcLoc(b.e1);
		int a3 = gr2.arcLoc(b.s2);
		int a4 = gr2.arcLoc(b.e2);
	
		GeneralPath gp = new GeneralPath();
		
		gp.moveTo(circX(cX, rBlock, a1+arc0),circY(cY, rBlock, a1+arc0));
		
		Arc2D arc1 = new Arc2D.Double(cX-rBlock, cY-rBlock, 2*rBlock, 2*rBlock, a1+arc0, a2-a1, Arc2D.OPEN);
		gp.append(arc1, true);
		
		gp.quadTo(circX(cX, 0, a2+arc0), circY(cY, 0, a2+arc0),circX(cX, rBlock, a3+arc0),circY(cY, rBlock, a3+arc0));

		Arc2D arc2 = new Arc2D.Double(cX-rBlock, cY-rBlock, 2*rBlock, 2*rBlock, a3+arc0, a4-a3, Arc2D.OPEN);
		gp.append(arc2, true);
		
		gp.quadTo(circX(cX, 0, a4+arc0), circY(cY, 0, a4+arc0), circX(cX, rBlock, a1+arc0), circY(cY, rBlock, a1+arc0));
		
		gp.closePath();
		g2.fill(gp);
	}
	// Paint project name
	private void paintProjText(Graphics g, Project prjObj, int midArc, int locLabel) {// locLabel= rOuter + 40);
		g.setColor(Color.black);
       
		FontMetrics fm = g.getFontMetrics();
        int unrotW =  (int) (fm.getStringBounds(prjObj.displayName,g).getWidth()/2); // CAS552 was prjObj.name; not much diff
        int nameH =   (int) (fm.getStringBounds(prjObj.displayName,g).getHeight());
      
        int nameArcW = 0;
        if (unrotW > 0 && unrotW < 200) {// exclude pathological values
        	nameArcW = (int)((180/Math.PI) * Math.atan(((double)unrotW/(double)(locLabel))));
        } 
        else symap.Globals.dprt("Special case for " + prjObj.displayName + " " + unrotW );
        
        int arc = midArc + arc0 + nameArcW;
    	int nameX = (int) circX(cX, locLabel, arc);
    	int nameY = (int) circY(cY, locLabel, arc);

		// Put the angle within 0-360 range for texts that do not work mod 360
		double rotAngle = 90 - midArc - arc0;
		while (rotAngle > 360) {rotAngle -= 360;} 
		while (rotAngle < 0)   {rotAngle += 360;}

		// this does not works so well for non-rotated; these are mouse-over coords
		prjObj.labelR1 = locLabel - nameH;
		prjObj.labelR2 = locLabel + nameH;
		prjObj.labelA1 = midArc - nameArcW - 5;
		prjObj.labelA2 = midArc + nameArcW + 5;
		
    	// CAS521 add font to reference project/chromosomes
    	Font f = g.getFont();
    	f = setProjFont(0, f, prjObj.idx);
    	g.setFont(f);
    	 
    	if (bRotateText) {
    		AffineTransform rot = AffineTransform.getRotateInstance(rotAngle*Math.PI/180);
    		Font fRot = g.getFont().deriveFont(rot);
    		g.setFont(fRot);
    		g.drawString(prjObj.displayName, nameX, nameY);
    		g.setFont(f);
    	}
    	else {
    		double correctedArc = midArc + arc0;
			while (correctedArc > 360) {correctedArc -= 360;}
			while (correctedArc < 0)   {correctedArc += 360;}

			// Make arc measure with zero point vertical
        	double vArc = correctedArc - 90;
			while (vArc > 360) {vArc -= 360;}
			while (vArc < 0)   {vArc += 360;}
			
    		double strW = fm.getStringBounds(prjObj.displayName,g).getWidth();
        	nameX = (int) circX(cX,rOuter + 50, correctedArc);         																
        	nameY = (int) circY(cY,rOuter + 50, correctedArc);

        	int xOff = (int)(strW*(1-Math.abs(correctedArc-180.0)/180.0)); // 0 at the right, 1 at the left
        	nameX -=  xOff;
        	int yOff =  (int)(10*(1.0-Math.abs(vArc-180.0)/180.0));  // 0 at the top, 10 at the bottom;
        	nameY += yOff;
        	
    		g.drawString(prjObj.displayName,nameX,nameY);
    	}
    	
    	f = setProjFont(1, f, prjObj.idx);
    	g.setFont (f);
	}
	// Set font based on priority 
	private Font setProjFont(int i, Font f, int pidx) {
		int x = (i==0) ? 16 : 14;
		
		if (priorityVec.get(0) == pidx)  							 f = new Font ("Courier", Font.BOLD | Font.ITALIC, x);
    	else if (priorityVec.size()>2 && priorityVec.get(1) == pidx) f = new Font ("Courier", Font.ITALIC, x);
    	else 														 f = new Font ("Courier", Font.PLAIN, x);	
		
		return f;
	}
	private int circX(int cX, int R, double arc){
		return cX + (int)(R*Math.cos(2*Math.PI*arc/360));
	}
	private int circY(int cY, int R, double arc) {
		return cY - (int)(R*Math.sin(2*Math.PI*arc/360)); // y increases down
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
	
	protected void resetToHome() { // CAS552 for new Home button
		zoom = ZOOM_DEFAULT;
		arc0 = ARC_DEFAULT;
		bToScale =  bRotateText = false;
		//These are not included:
		//bShowSelf =bRevRef = false
		//if (bRevRef) reverse();
		//invChoice = 0;
	}
	protected boolean isHome() { // these checks match above; CAS552 add for Control
		return zoom==ZOOM_DEFAULT && arc0==ARC_DEFAULT && !bToScale && !bRotateText;
	}
	public String getHelpText(MouseEvent event) { return helpText;} // frame.HelpBar
	/************************************************************************
	 * Classes
	 **********************************************************************/
	private class Project {
		private int idx;
		private String name;
		
		private String displayName;							// load from db
		private Vector<Group> grps = new Vector<Group>();	// load from db
		
		private int labelR1,labelR2,labelA1,labelA2;		// mouse-over coords; set in paintProjText
		
		private Project(int idx, String name, DBconn2 dbc2)  {
		try {
			this.idx = idx;
			this.name = name;
	
			ResultSet rs = dbc2.executeQuery("select idx, name, length from xgroups join pseudos " + 
						" on pseudos.grp_idx=xgroups.idx where xgroups.proj_idx=" + idx + " order by sort_order");

			while (rs.next()){
				Group g = new Group(rs.getInt(3), rs.getInt(1), idx, rs.getString(2), name);
				grps.add(g);
			}
			Mproject tProj = new Mproject();
			String display_name = tProj.getKey(tProj.sDisplay);
			
			displayName = name;
			rs = dbc2.executeQuery("select value from proj_props where name='"+display_name+"' and proj_idx=" + idx);
			if (rs.first()) {
				displayName = rs.getString(1);
				name2DisMap.put(name, displayName); // CAS517
			}
		}
		catch (Exception e) {ErrorReport.print(e, "Getting project");}
		}
		
		private double totalSizeShown() {
			double ret = 0;
			for (Group g : grps){
				if (g.shown){
					ret += ((double)g.size);
				}	
			}
			return ret;
		}		
		private void setArc(int a1, int a2, int cidx){
			double arc = a2 - a1; 
			double totalSize = totalSizeShown();
			double a = a1;
			int cnt = 0;
			int nShown = 0;
			for (Group g : grps) {
				if (g.shown) nShown++;
			}
			double cumulativeSize = 0;
			for (Group g : grps) {
				if (!g.shown) continue;
				
				cumulativeSize += g.size;
				cnt++;
				double grpEndPos = (arc*cumulativeSize) / totalSize;
				
				long b;
				if (cnt < nShown) b = a1 + (long)(Math.round(grpEndPos));
				else 			  b = a2;
				
				g.a1 = (int) a;
				g.a2 = (int) b;
				
				a = b;
			}
		}
	}// end project class
	
	private class Group {
		private double size;
		private int idx, proj_idx;
		private String name, projName;
		private int a1, a2;
		private int cStart = -1; // index into color vector
		private boolean shown = true, onTop = false, onlyShow = false;
		private Vector <Block> blocks = new Vector <Block> ();
		
		private Group(int size, int idx, int proj_idx, String name, String pname){
			this.size = size;
			this.idx = idx;
			this.proj_idx = proj_idx;
			this.name = name;
			this.projName = pname;
		}
		private void addBlock(Block b) {blocks.add(b);}
		
		private int arcLoc(double bpLoc) {
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
}
