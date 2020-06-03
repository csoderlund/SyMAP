package circview;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;

import symap.frame.HelpBar;
import symap.frame.HelpListener;
import util.DatabaseReader;
import util.ImageViewer;
import util.PropertiesReader;

import dotplot.DotPlot;

import java.util.Vector;
import java.util.TreeMap;
import java.awt.geom.AffineTransform;
import java.util.TreeSet;

import java.awt.geom.GeneralPath;
import java.awt.geom.Arc2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

public class CircPanel extends JPanel implements HelpListener, MouseListener,MouseMotionListener
{
	private static final long serialVersionUID = -1711255873365895293L;
	int mRefIdx, mIdx2;
	String refName, Name2;
	DatabaseReader mDB;
	Vector<Integer> mColors;
	final int maxColors = 100;
	TreeMap<Integer,String> mRefNames, mGrp2Names;
	TreeMap<Integer,Integer> mRefSizes;
	Vector<Integer> mRefOrder;
	TreeMap<Integer,Integer> grpColorOrder;
	Vector<Integer> mGrps2;	
	Vector<Block> mBlocks;
	
	int mPairIdx;
	
	Vector<Group> allGrps;
	Vector<Project> allProj;
	Vector<Integer> projColorOrder;
	public TreeMap<Integer,Group> idx2Grp;
	TreeMap<Integer,Vector<Block>> grp2Blocks;
	Statement s;
	
	private static final Color FAR_BACKGROUND;
	private static final Color BACKGROUND;
	private static final Color BACKGROUND_BORDER;

	static {
		PropertiesReader props = new PropertiesReader(CircPanel.class.getResource(DotPlot.DOTPLOT_PROPS));
		FAR_BACKGROUND        = props.getColor("farBackgroundColor");
		BACKGROUND            = props.getColor("backgroundColor");
		BACKGROUND_BORDER     = props.getColor("backgroundBorderColor");
	}
	private static int     MARGIN     = 20;
	private static int CIRCMARGIN = 60;
	
    int rOuter;
    int rInner;
    int rBlock;
    int cX;
    int cX0=0;
    int cY;
    int cY0 = 0;
    int arc0 = 0;
    boolean toScale = false;
    int invChoice = 0;
    boolean showSelf = true;
    boolean bRotateText = false;
    float zoom = 1.0f;
    
    JButton plusButton, minusButton, rotateButton;
    JCheckBox scaleCheckbox, selfCheckbox;
    JComboBox invChooser;
    JScrollPane scroller;
    HelpBar hb = null;
    Dimension dim;
    

	public CircPanel(DatabaseReader dbReader, int[] projIdxList, TreeSet<Integer> shownGroups, HelpBar _hb) 
	{
		createStructs();
		mDB = dbReader;
		hb = _hb;
		
		if (projIdxList.length == 0) 
		{
			System.out.println("Circle view called with no projects!"); 
			return;
		}
		

		try
		{
			s = mDB.getConnection().createStatement();
			for (int i = 0; i < projIdxList.length; i++)
			{
				if (projIdxList[i] > 0)
				{
					addProject(projIdxList[i]);
				}
			}
			if (shownGroups != null)
			{
				for (Group g : allGrps)
				{
					g.shown = shownGroups.contains(g.idx);
				}
			}
			init();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public void init() 
	{
		setPreferredSize(new Dimension(900,800));
		setMinimumSize(new Dimension(900,800));
		dim  = new Dimension();
		scroller = new JScrollPane(this);
		setBackground(FAR_BACKGROUND);

		scroller.setBackground(FAR_BACKGROUND);
		scroller.getViewport().setBackground(FAR_BACKGROUND);
		scroller.getVerticalScrollBar().setUnitIncrement(10); 
		
		hb.addHelpListener(this); 
		addMouseListener(this);
		addMouseMotionListener(this);

		for (Project p : allProj)
		{
			projColorOrder.add(p.idx);
		}
		try
		{
			int nColors = 0;
			for (Project p : allProj)
			{
				nColors += p.grps.size();
			}
			getUniqueColors(0); // arg doesn't matter now
			while (mColors.size() < nColors + 1)
			{
				mColors.addAll(mColors);
			}

			loadBlocks();
		}
		catch(Exception e)
		{
			e.printStackTrace();
			return;
		}		
	}

	public void createStructs()
	{
		allProj = new Vector<Project>();
		projColorOrder = new Vector<Integer>();
		idx2Grp = new TreeMap<Integer,Group> ();
		grp2Blocks = new TreeMap<Integer,Vector<Block>> ();
		allGrps = new Vector<Group>();
		
	}
	public void addProject(int idx) throws Exception
	{
		ResultSet rs;
		rs = s.executeQuery("select name,type from projects where idx=" + idx);
		if (rs.first())
		{
			Project proj = new Project(idx, rs.getString("name"), rs.getString("type").equals("fpc"), s);
			allProj.add(proj);
			for (Group g : proj.grps)
			{
				int curID = allGrps.size();
				g.localID = curID;
				allGrps.add(g);
				idx2Grp.put(g.idx, g);				
			}
		}
		else
		{
			System.out.println("Can't find proj_idx=" + idx);
		}
	}
	public void loadBlocks() throws Exception
	{
		ResultSet rs;
		for (int i1 = 0; i1 < allProj.size(); i1++)
		{
			Project p1 = allProj.get(i1);
			for (int i2 = i1; i2 < allProj.size(); i2++)
			{
				Project p2 = allProj.get(i2);
				
				// Get the blocks in both directions since we don't know the ordering
				rs = s.executeQuery("select grp1_idx, grp2_idx, start1, end1, start2, end2, corr from blocks " + 
						" where proj1_idx=" + p1.idx + " and proj2_idx=" + p2.idx );
				while (rs.next())
				{
					addBlock(rs.getInt("grp1_idx"), rs.getInt("grp2_idx"), rs.getInt("start1"),
							rs.getInt("end1"),rs.getInt("start2"),rs.getInt("end2"),(rs.getFloat("corr")<0));
				}
				if (p1.idx != p2.idx)
				{
					rs = s.executeQuery("select grp1_idx, grp2_idx, start1, end1, start2, end2,corr from blocks " + 
							" where proj1_idx=" + p2.idx + " and proj2_idx=" + p1.idx );
					while (rs.next())
					{
						addBlock(rs.getInt("grp1_idx"), rs.getInt("grp2_idx"), rs.getInt("start1"),
								rs.getInt("end1"),rs.getInt("start2"),rs.getInt("end2"),(rs.getFloat("corr")<0));
					}
				}
			}
		}
	}
	public void addBlock(int gidx1, int gidx2, int s1, int e1, int s2, int e2, boolean inv)
	{
		if (!idx2Grp.containsKey(gidx1) || !idx2Grp.containsKey(gidx2)) return;
		Group g1 = idx2Grp.get(gidx1);
		Group g2 = idx2Grp.get(gidx2);
		Block b = new Block(g1.localID, g2.localID, s1,e1,s2, e2, inv);
		if (!grp2Blocks.containsKey(g1.localID))
		{
			grp2Blocks.put(g1.localID, new Vector<Block>());
		}
		grp2Blocks.get(g1.localID).add(b);
	}
	
	public JScrollPane getScrollPane() {
		return scroller;
	}
	@SuppressWarnings("deprecation")

	// This provides the help text for elements not supporting tooltips
	public String getHelpText(MouseEvent event) 
	{ 
		Component comp = (Component)event.getSource();
		Component parent = comp.getParent();
		if (parent == invChooser)
		{
			return "Select how to show inverted and non-inverted synteny blocks";
		}

		return "Circular alignment view.\nClick a chromosome to bring its blocks to the top.\n" + 
			"Click a project name to color the block ribbons according to that project's chromosome colors.";
	}
	public Group overGroup(int r, int angle)
	{
		// Figure out whether it's in the annulus of the group colors
		if (r <= rOuter && r >= rInner)
		{
			return groupFromAngle(angle); // this code works b/c no group crosses the 0 angle line

		}	
		return null;
	}
	public Project overProject(int r, int angle)
	{
		for (Project p : allProj)
		{
			if (r >= p.labelR1 && r <= p.labelR2)
			{
				if (angle >= p.labelA1 && angle <= p.labelA2)
				{
					return p;
				}
			}
		}
		return null;
	}
	public void handleClick(long x, long y, boolean dbl)
	{

	}
	private Group groupFromAngle(int a)
	{
		for (Group g : allGrps)
		{
			if (a >= g.a1 && a <= g.a2)
			{
				return g;
			}
		}
		return null;
	}
	private static void addComponent(Container container, Component component, int gridx, int gridy,
	      int gridwidth, int gridheight, int anchor, int fill) 
	{
			GridBagConstraints gbc = new GridBagConstraints(gridx, gridy, gridwidth, gridheight, 1.0, 1.0,
		        anchor, fill, new Insets(0,0,0,0), 0, 0);
		    container.add(component, gbc);
	}
			

	class Block
	{
		int s1,e1,s2,e2;
		int g1, g2;
		boolean inverted;
		
		public Block(int _g1, int _g2, int _s1, int _e1, int _s2, int _e2, boolean _inv)
		{
			g1 = _g1; g2 = _g2;
			s1 = _s1; e1 = _e1;
			s2 = _s2; e2 = _e2;
			inverted = _inv;

		}

	}
	public class Group
	{
		double size;
		int idx;
		int proj_idx;
		String name;
		String projName;
		int a1;
		int a2;
		int cidx;
		int localID = -1;
		public boolean shown = true;
		boolean onTop = false;
		boolean onlyShow = false;
		
		public Group(int _size, int _idx, int _proj_idx, String _name,String _pname)
		{
			size = _size;
			idx = _idx;
			proj_idx = _proj_idx;
			name = _name;
			projName= _pname;
		}
		public int arcLoc(double bpLoc)
		{
			double a = a2 - a1 + 1;
			double arcLoc = ((a*bpLoc)/((double)size));
			return a1 + (int)arcLoc;
		}
		
	}
	class Project
	{
		int idx;
		String name;
		String displayName;
		boolean fpc;
		Vector<Group> grps;
		//Rectangle2D labelRect = null;
		int labelR1,labelR2,labelA1,labelA2;
		int scaleFactor = 1; // for cbsize, if fpc
		
		public Project(int _idx, String _name, boolean _fpc, Statement s) throws Exception
		{
			idx = _idx;
			name = _name;
			fpc = _fpc;
			grps = new Vector<Group>();
			ResultSet rs;
			if (fpc)
			{
				rs = s.executeQuery("select xgroups.idx,xgroups.name,sum(size) as length from xgroups join contigs " +
						" on contigs.grp_idx=xgroups.idx where xgroups.proj_idx=" + idx + 
						" and xgroups.name != '0' group by xgroups.idx order by sort_order");
			}
			else
			{
				rs = s.executeQuery("select idx, name, length from xgroups join pseudos " + 
						" on pseudos.grp_idx=xgroups.idx where xgroups.proj_idx=" + idx + " order by sort_order");

			}
			while (rs.next())
			{
				Group g = new Group(rs.getInt("length"), rs.getInt("idx"), idx, rs.getString("name"),name);
				grps.add(g);
			}
			if (fpc)
			{
				rs = s.executeQuery("select value from proj_props where name='cbsize' and proj_idx=" + idx);
				if (rs.first())
				{
					scaleFactor = Integer.parseInt(rs.getString("value"));
				}
			}
			displayName = name;
			rs = s.executeQuery("select value from proj_props where name='display_name' and proj_idx=" + idx);
			if (rs.first())
			{
				displayName = rs.getString("value");
			}
		}
		int totalSize()
		{
			int ret = 0;
			for (Group g : grps)
			{
				ret += g.size*scaleFactor;
			}
			return ret;
		}
		double totalSizeShown()
		{
			double ret = 0;
			for (Group g : grps)
			{
				if (g.shown)
				{
					ret += ((double)g.size)*((double)scaleFactor);
				}	
			}
			return ret;
		}		
		void setArc(int a1, int a2, int cidx)
		{
			double arc = a2 - a1; 
			double totalSize = totalSizeShown();
			double a = a1;
			int cnt = 0;
			int nShown = 0;
			for (Group g : grps)
			{
				if (g.shown) nShown++;
			}
			double cumulativeSize = 0;
			for (Group g : grps)
			{
				if (!g.shown) continue;
				cumulativeSize += g.size;
				cnt++;
				double grpEndPos = (arc*cumulativeSize*((double)scaleFactor))/totalSize;
				
				long b;
				if (cnt < nShown)
				{
					b = a1 + (long)(Math.round(grpEndPos));
				}
				else
				{
					b = a2;
				}
				g.a1 = (int)a;
				g.a2 = (int)b;
				
				g.cidx = cidx;
				cidx++;
				a = b;
			}
		}
	}
	int colorInt(int R, int G, int B)
	{
		return (R<<16) + (G<<8) + B;
	}	
	void getUniqueColors(int amount) 
	{
	    mColors = new Vector<Integer>();
	    
	    mColors.add(colorInt(255,0,0));
	    mColors.add(colorInt(0,0,255));
	    mColors.add(colorInt(20,200,70));
	    mColors.add(colorInt(138,0,188));
	    mColors.add(colorInt(255,165,0));
	    mColors.add(colorInt(255,181,197));
	    mColors.add(colorInt(210, 180, 140));
	    mColors.add(colorInt(64,224,208));
	    mColors.add(colorInt(165,42,42));
	    mColors.add(colorInt(0,255,255));
	    mColors.add(colorInt(230,230,250));
	    mColors.add(colorInt(255,255,0));
	    mColors.add(colorInt(85,107, 47));
	    mColors.add(colorInt(70,130,180));

	    mColors.add(colorInt(127,125,21));
	    mColors.add(colorInt(207,137,97));
	    mColors.add(colorInt(144,67,233));
	    mColors.add(colorInt(199,189,70));
	    mColors.add(colorInt(82,203,128));
	    mColors.add(colorInt(120,202,102));
	    mColors.add(colorInt(194,102,115));
	    mColors.add(colorInt(20,17,118));
	    mColors.add(colorInt(145,21,129));
	    mColors.add(colorInt(109,62,232));
	    mColors.add(colorInt(108,86,28));
	    mColors.add(colorInt(185,206,7));
	    mColors.add(colorInt(50,200,133));
	    mColors.add(colorInt(46,102,237));
	    mColors.add(colorInt(27,149,81));
	    mColors.add(colorInt(114,155,241));
	    mColors.add(colorInt(33,240,129));
	    mColors.add(colorInt(117,170,160));
	    mColors.add(colorInt(93,14,79));
	    mColors.add(colorInt(129,210,166));
	    mColors.add(colorInt(124,191,79));
	    mColors.add(colorInt(188,55,188));
	    mColors.add(colorInt(117,219,105));
	    mColors.add(colorInt(11,142,235));
	    mColors.add(colorInt(144,97,194));
	    mColors.add(colorInt(215,77,161));
	    mColors.add(colorInt(192,148,92));
	    mColors.add(colorInt(197,86,215));
	    mColors.add(colorInt(103,159,140));
	    mColors.add(colorInt(42,80,186));
	    mColors.add(colorInt(136,44,28));
	    mColors.add(colorInt(59,207,239));
	    mColors.add(colorInt(115,62,251));
	    mColors.add(colorInt(136,179,26));
	    mColors.add(colorInt(48,29,82));
	    mColors.add(colorInt(31,187,116));
	    mColors.add(colorInt(98,129,244));
	    mColors.add(colorInt(214,198,72));
	    mColors.add(colorInt(30,104,54));
	    mColors.add(colorInt(11,240,45));
	    mColors.add(colorInt(182,73,232));
	    mColors.add(colorInt(181,116,173));
	    mColors.add(colorInt(154,54,156));
	    mColors.add(colorInt(157,62,134));
	    mColors.add(colorInt(95,155,130));
	    mColors.add(colorInt(1,49,242));
	    mColors.add(colorInt(207,10,187));
	    mColors.add(colorInt(62,82,11));
	    mColors.add(colorInt(118,19,178));
	    mColors.add(colorInt(168,242,211));
	    mColors.add(colorInt(173,147,121));
	    mColors.add(colorInt(67,38,212));
	    mColors.add(colorInt(27,229,235));
	    mColors.add(colorInt(9,82,242));
	    mColors.add(colorInt(57,155,84));
	    mColors.add(colorInt(114,18,40));
	    mColors.add(colorInt(132,11,12));
	    mColors.add(colorInt(33,22,144));
	    mColors.add(colorInt(41,3,241));
	    mColors.add(colorInt(164,18,247));
	    mColors.add(colorInt(48,16,230));
	    mColors.add(colorInt(220,101,8));
	    mColors.add(colorInt(190,216,38));
	    mColors.add(colorInt(135,190,4));
	    mColors.add(colorInt(174,225,161));
	    mColors.add(colorInt(60,218,203));
	    mColors.add(colorInt(93,171,163));
	    mColors.add(colorInt(106,58,113));
	    mColors.add(colorInt(155,100,221));
	    mColors.add(colorInt(92,208,48));
	    mColors.add(colorInt(79,252,70));
	    mColors.add(colorInt(47,6,104));
	    mColors.add(colorInt(141,198,123));
	    mColors.add(colorInt(195,19,156));
	    mColors.add(colorInt(214,18,222));
	    mColors.add(colorInt(28,110,137));
	    mColors.add(colorInt(137,51,155));
	    mColors.add(colorInt(167,54,22));
	    mColors.add(colorInt(69,157,85));
	    mColors.add(colorInt(146,24,202));
	    mColors.add(colorInt(58,64,207));
	    mColors.add(colorInt(216,108,174));
	    mColors.add(colorInt(78,58,136));
	    mColors.add(colorInt(146,82,91));
	    mColors.add(colorInt(40,76,111));
	    mColors.add(colorInt(80,34,231));
	    mColors.add(colorInt(193,81,118));
	}
	int circX(int cX, int R, double arc)
	{
		return cX + (int)(R*Math.cos(2*Math.PI*arc/360));
	}
	int circY(int cY, int R, double arc)
	{
		return cY - (int)(R*Math.sin(2*Math.PI*arc/360)); // y increases down
	}
	public  AbstractButton createButton(String path, String tip, ActionListener listener, boolean checkbox) 
	{
		AbstractButton button;
		
		Icon icon = ImageViewer.getImageIcon(path); 
		if (icon != null) {
		    if (checkbox)
		    	button = new JCheckBox(icon);
		    else
		    	button = new JButton(icon);
		    	button.setMargin(new Insets(0,0,0,0));
		}
		else {
		    if (checkbox)
		    	button = new JCheckBox(path);
		    else
		    	button = new JButton(path);
		    	button.setMargin(new Insets(1,3,1,3));
		}
		if (listener != null) 
		    button.addActionListener(listener);

		button.setToolTipText(tip);
		
		button.setName(tip); 
		
		return button;
	}
		
	void zoom (double f)
	{
		zoom *= f;
	    makeRepaint();  
	}
	void rotate(int da)
	{
		arc0 += da;
		if (arc0 > 360) arc0 -= 360;
		makeRepaint();
	}
	void toggleScaled(boolean scaled)
	{
		if (toScale == scaled)
		{
			return;
		}
		toScale = scaled;
		makeRepaint();
	}
	void updateDims()
	{
	    rInner = (int)(rOuter*0.933);
	    rBlock = (int)(rOuter*0.917);	
        cX = rOuter + 50;
        cY = rOuter + 50;
        if (cX < cX0 && cY < cY0)
        {
        		cX = cX0; 
        		cY = cY0;
        }
	}

	public void makeRepaint()
	{
		repaint();
	}

	public boolean colorFirstPriority(int idx1, int idx2)
	{
		// returns true if first project has color priority, i.e., its
		// group colors are used for blocks
		for (int idx : projColorOrder)
		{
			if (idx == idx1) return true;
			if (idx == idx2) return false;
		}
		return true; // should throw
		
	}
	public void mouseClicked(MouseEvent evt) 
	{
		long x = evt.getX();
		long y = evt.getY();
		int xRel = (int)x - cX;
		int yRel = (int)y - cY;
		boolean dbl = (evt.getClickCount() > 1);
		int r = (int)Math.sqrt(xRel*xRel + yRel*yRel);
		int angle = -arc0 + (int)(180*Math.atan2(-yRel,xRel)/Math.PI);
		if (angle < 0) angle += 360;
		Group g = overGroup(r,angle);

		if (g != null)
		{
			for (Group g1 : allGrps)
			{
				if (!dbl)
				{
					g1.onTop = false;
					g1.onlyShow = false;
					if (g1 == g)
					{
						g1.onTop = true;
					}
				}
				else
				{
					g1.onTop = false;
					g1.onlyShow = false;
					if (g1 == g)
					{
						g1.onlyShow = true;
						g1.onTop = true;
					}						
				}
			}
			makeRepaint();
		}
		else
		{
			Project p = overProject(r,angle);
			if (p != null)
			{
				projColorOrder.add(0,p.idx);
				int i = 1;
				for (; i < projColorOrder.size();i++)
				{
					if (projColorOrder.get(i) == p.idx)
					{
						break;
					}
				}
				projColorOrder.remove(i);
				makeRepaint();						
			}
		}
	}

	public void mousePressed(MouseEvent evt) {	}

	public void mouseReleased(MouseEvent arg0) {}
	
	public void mouseDragged(MouseEvent e) {}

	public void mouseEntered(MouseEvent e) { }
	public void mouseExited(MouseEvent e)  {setCursor( Cursor.getDefaultCursor() ); }
	public void mouseMoved(MouseEvent e)   
	{ 
		
		if (hb == null) return;
		
		long x = e.getX();
		long y = e.getY();
		int xRel = (int)x - cX;
		int yRel = (int)y - cY;
		int r = (int)Math.sqrt(xRel*xRel + yRel*yRel);
		int angle = -arc0 + (int)(180*Math.atan2(-yRel,xRel)/Math.PI);
		if (angle < 0) angle += 360;
		Group g = overGroup(r,angle);

		if (g != null)
		{
			setCursor( Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) );
			hb.setHelp(g.projName + "/" + g.name + ", click to bring to top, doubleclick to hide others",this);
		}
		else
		{
			Project p = overProject(r,angle);
			if (p != null)
			{
				setCursor( Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) );
				hb.setHelp(p.name + ", click to recolor blocks", this);
			}
			else
			{
				setCursor( Cursor.getDefaultCursor() );
			}
		}	

	}
	
	// Note that circle angles are measured from the middle-right of the circle, going around counterclockwise. 
	// Hence, the right side of the circle is angle 0-90, plus angle 270-360. 
	// The overall rotation angle arc0 is added to everything.  
	
	public void paintComponent(Graphics g) 
	{
		super.paintComponent(g); // repaints the window background
				
		Dimension d = new Dimension( (int)(scroller.getWidth() * zoom), 
                (int)(scroller.getHeight() * zoom) );
		dim.width =  (int)((d.width - MARGIN)); // so the scroll doesn't show at zoom=1
		dim.height = (int)((d.height - MARGIN));

		
	    int maxNameW = 0;
	    if (!bRotateText)
	    {
	        for (Project p : allProj)
	        {
	            FontMetrics fm = g.getFontMetrics();
	            int nameW = (int)(fm.getStringBounds(p.displayName,g).getWidth());		
	            if (nameW > maxNameW) maxNameW = nameW;
	        }
	    }
	    
        int marginX = 50 + Math.max(CIRCMARGIN, maxNameW);
	    rOuter = Math.min(dim.width, dim.height)/2 - marginX;

	    rInner = (int)(rOuter*0.933);
	    rBlock = (int)(rOuter*0.917);	
        cX = rOuter + marginX;
        cY = rOuter + marginX;
        if (cX0 == 0)
        {
        	cX0 = cX;
        	cY0 = cY;
        }
        
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        
        double totalSizeShown = 0;
        int nEmpty = 0;
        
        for (Project p : allProj)
        {
	        	double shown =  p.totalSizeShown();
	        	totalSizeShown += shown;
	        	if (shown == 0) nEmpty++;
        }        
        if (totalSizeShown == 0)
        {
	        	System.out.println("Nothing shown!");
	        	return;
        }
        int projEmptyArc = 20;
        int curArc = 0;
        int curColor = 0;
        int rLabel = rOuter + 40;
        for (Project p : allProj)
        {
            int projArc = 360/allProj.size();
            if (p.totalSizeShown() == 0)
            {
                g.drawArc(cX - rOuter, cY - rOuter, 2*rOuter, 2*rOuter, curArc, projArc - 1);
                g.drawArc(cX - rInner - 1, cY - rInner - 1, 2*rInner + 2, 2*rInner + 2, curArc, projArc - 1);
                g.drawLine((int)circX(cX,rInner,curArc), (int)circY(cY,rInner,curArc), 
                		(int)circX(cX,rOuter,curArc), (int)circY(cY,rOuter,curArc));
                g.drawLine((int)circX(cX,rInner+1,curArc + projArc - 1), (int)circY(cY,rInner+1,curArc + projArc - 1), 
                		(int)circX(cX,rOuter,curArc + projArc - 1), (int)circY(cY,rOuter,curArc + projArc - 1));
            }
            if (toScale)
            {
	            	if (p.totalSizeShown() == 0)
	            	{
	            		projArc = projEmptyArc;
	            	}
	            	else
	            	{
	            		double shown = p.totalSizeShown();
	            		projArc = (int) Math.floor(((360.0 - ((double)(nEmpty*projEmptyArc)))*shown)/totalSizeShown);
	            	}
            }
            
            // This calculation is for the rotated text.
        	int nextArc = curArc + projArc;
        	int midArc = (curArc + nextArc)/2;
        	g.setColor(Color.black);
        FontMetrics fm = g.getFontMetrics();
        int unrotW = (int)(fm.getStringBounds(p.name,g).getWidth()/2);		
        int stringH = (int)(fm.getStringBounds(p.name,g).getHeight());
        int stringArc = midArc; 
        int stringArcW = 0;
        if (unrotW > 0 && unrotW < 200) // exclude pathological values
        {
        	stringArcW = (int)((180/Math.PI)*Math.atan(((double)unrotW/(double)(rLabel))));
        }
        	int stringX = (int)circX(cX,rLabel,stringArc + arc0 + stringArcW);
        	int stringY = (int)circY(cY,rLabel,stringArc + arc0 + stringArcW);
		Font f = g.getFont();

		double rotAngle = 90 - midArc - arc0;
		// Put the angle within 0-360 range for tests that don't work mod 360
		while (rotAngle > 360) {rotAngle -= 360;} 
		while (rotAngle < 0) {rotAngle += 360;}

        	p.labelR1 = rLabel - stringH;
        	p.labelR2 = rLabel + stringH;
        	p.labelA1 = stringArc - stringArcW -5;
        	p.labelA2 = stringArc + stringArcW + 5;
        	
        	AffineTransform rot; 
        	Font fRot;

        	if (bRotateText)
        	{
        		rot = AffineTransform.getRotateInstance(rotAngle*Math.PI/180);
        		fRot = g.getFont().deriveFont(rot);
        		g.setFont(fRot);
        		g.drawString(p.displayName,stringX,stringY);
            	g.setFont(f);
        	}
        	else
        	{
        		double correctedArc = midArc + arc0;
    			while (correctedArc > 360) {correctedArc -= 360;}
    			while (correctedArc < 0) {correctedArc += 360;}

    			// Now we need an arc measure with zero point vertical
            	double vArc = correctedArc - 90;
    			while (vArc > 360) {vArc -= 360;}
    			while (vArc < 0) {vArc += 360;}
    			
    			String str = p.displayName;
        		double strW = fm.getStringBounds(str,g).getWidth();
            	stringX = (int)(circX(cX,rOuter + 50,correctedArc) );            																
            	stringY = (int)circY(cY,rOuter + 50,correctedArc);

           	
            	int xOff = (int)(strW*(1-Math.abs(correctedArc-180.0)/180.0)); // 0 at the right, 1 at the left
            	stringX -=  xOff;
            	int yOff =  (int)(10*(1.0-Math.abs(vArc-180.0)/180.0));  // 0 at the top, 10 at the bottom;
            	stringY += yOff;
            	
        		g.drawString(str,stringX,stringY);
        	}
			
        	
        	if (nextArc > 360)
        	{
        		nextArc = 360;
        	}
        	p.setArc(curArc, nextArc - 1, curColor); // this makes a 1 degree gap between projects
        	for (Group gr : p.grps)
        	{
        		if (!gr.shown) continue;
        		g.setColor(new Color(mColors.get(gr.localID)));
        		g.fillArc(cX - rOuter, cY - rOuter, 2*rOuter, 2*rOuter, arc0 + gr.a1, gr.a2 - gr.a1);
        		double aMid = arc0 + (gr.a1 + gr.a2)/2;
        		g.setColor(Color.black);
    			rotAngle = 90 - aMid;
    			while (rotAngle > 360) {rotAngle -= 360;}
    			while (rotAngle < 0) {rotAngle += 360;}
    			if (bRotateText)
    			{
	            	rot = AffineTransform.getRotateInstance(rotAngle*Math.PI/180);
	    			fRot = g.getFont().deriveFont(rot);
	    			g.setFont(fRot);        		
	        		g.drawString(gr.name, (int)circX(cX,rOuter+10,aMid), (int)circY(cY,rOuter+10,aMid));
	        		g.setFont(f);
    			}
    			else
    			{
    				while (aMid > 360) {aMid -= 360;} 
    				while (aMid < 0) {aMid += 360;}
    				
        			// Now we need an arc measure with zero point vertical
                	double vArc = aMid - 90;
        			while (vArc > 360) {vArc -= 360;}
        			while (vArc < 0) {vArc += 360;}

                String str = gr.name;
                fm = g.getFontMetrics();
                double grW = fm.getStringBounds(str,g).getWidth();		
				
    				int pX = (int)circX(cX,rOuter+10,aMid);
    				int pY = (int)circY(cY,rOuter+10,aMid);
    				
    				pX -=  (int)(grW*(1.0-Math.abs(aMid-180.0)/180.0)); //  (int)(((double)grW)*Math.sin(aMid*Math.PI/360));
                	int yOff =  (int)(10.0*(1.0-Math.abs(vArc-180.0)/180.0));  // 0 at the top, 10 at the bottom;

    				pY += yOff;
    				
	       		g.drawString(str, pX, pY);   
    			}
        	}
        	curColor += p.grps.size();
        	curArc = nextArc;
        }
        
        g.setColor(Color.white);
        g.fillArc(cX - rInner, cY - rInner, 2*rInner, 2*rInner, 0, 360);	

        float alpha = .7f;
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        boolean showOne = false;
        for (Group grp : allGrps)
        {
        	if (grp.onlyShow) 
        	{
        		showOne = true;
        		break;
        	}
        }
         // Note,this paint code is fully repeated to paint the on-top group.
        // If you change this loop, must also change the one below. 
        for (Group grp : allGrps)
        {
        	if (!grp.shown) continue;
        	if (showOne) continue; // easier than putting the whole thing in a block
        	int lid = grp.localID;
        	if (!grp2Blocks.containsKey(lid)) continue;
        	for (Block b : grp2Blocks.get(lid))
        	{
        		if (invChoice == 1 && !b.inverted) continue;
        		if (invChoice == 2 && b.inverted) continue;
 
        		Group gr1 = allGrps.get(b.g1);
        		Group gr2 = allGrps.get(b.g2);
        		if (!showSelf && (gr1.proj_idx == gr2.proj_idx)) continue;
        		if (gr1.onTop || gr2.onTop) continue;
        		if (gr1.shown && gr2.shown)
        		{
	        		int cidx = (colorFirstPriority(gr1.proj_idx,gr2.proj_idx) ? gr1.localID : gr2.localID);
	        		if (invChoice != 3)
	        		{
	        			g.setColor(new Color(mColors.get(cidx)));
	        		}
	        		else
	        		{
	        			g.setColor(!b.inverted ? Color.red.brighter() : Color.green.darker());
	        		}
	        		int a1 = gr1.arcLoc(b.s1);
	        		int a2 = gr1.arcLoc(b.e1);
	        		int a3 = gr2.arcLoc(b.s2);
	        		int a4 = gr2.arcLoc(b.e2);
	        		
	        		GeneralPath gp = new GeneralPath();
	        		
	        		gp.moveTo(circX(cX,rBlock,a1 + arc0),circY(cY,rBlock,a1 + arc0));
	        		
	        		Arc2D arc1 = new Arc2D.Double(cX - rBlock, cY - rBlock, 2*rBlock, 2*rBlock, a1 + arc0, a2 - a1, Arc2D.OPEN);
	        		gp.append(arc1, true);
	        		
	        		gp.quadTo(circX(cX,0,a2 + arc0), circY(cY,0,a2 + arc0),circX(cX,rBlock,a3 + arc0),circY(cY,rBlock,a3 + arc0));

	        		Arc2D arc2 = new Arc2D.Double(cX - rBlock, cY - rBlock, 2*rBlock, 2*rBlock, a3 + arc0, a4 - a3, Arc2D.OPEN);
	        		gp.append(arc2, true);
	        		
	        		gp.quadTo(circX(cX,0,a4 + arc0), circY(cY,0,a4 + arc0),circX(cX,rBlock,a1 + arc0),circY(cY,rBlock,a1 + arc0));
	       		
	        		gp.closePath();
	        		g2.fill(gp);
        		}
        	}
        }
        for (Group grp : allGrps)
        {
        	if (!grp.shown) continue;
        	int lid = grp.localID;
        	if (!grp2Blocks.containsKey(lid)) continue;
        	for (Block b : grp2Blocks.get(lid))
        	{
        		if (invChoice == 1 && !b.inverted) continue;
        		if (invChoice == 2 && b.inverted) continue;

        		Group gr1 = allGrps.get(b.g1);
        		Group gr2 = allGrps.get(b.g2);
        		if (!showSelf && (gr1.proj_idx == gr2.proj_idx)) continue;
        		if (gr1.shown && gr2.shown && (gr1.onTop || gr2.onTop))
        		{
	        		int cidx = (colorFirstPriority(gr1.proj_idx,gr2.proj_idx) ? gr1.localID : gr2.localID);
	        		if (invChoice != 3)
	        		{
	        			g.setColor(new Color(mColors.get(cidx)));
	        		}
	        		else
	        		{
	        			g.setColor(b.inverted ? Color.green.brighter() : Color.red.darker());
	        		}	        		
	        		int a1 = gr1.arcLoc(b.s1);
	        		int a2 = gr1.arcLoc(b.e1);
	        		int a3 = gr2.arcLoc(b.s2);
	        		int a4 = gr2.arcLoc(b.e2);
	        	
	        		GeneralPath gp = new GeneralPath();
	        		
	        		gp.moveTo(circX(cX,rBlock,a1 + arc0),circY(cY,rBlock,a1 + arc0));
	        		
	        		Arc2D arc1 = new Arc2D.Double(cX - rBlock, cY - rBlock, 2*rBlock, 2*rBlock, a1 + arc0, a2 - a1, Arc2D.OPEN);
	        		gp.append(arc1, true);
	        		
	        		gp.quadTo(circX(cX,0,a2 + arc0), circY(cY,0,a2 + arc0),circX(cX,rBlock,a3 + arc0),circY(cY,rBlock,a3 + arc0));

	        		Arc2D arc2 = new Arc2D.Double(cX - rBlock, cY - rBlock, 2*rBlock, 2*rBlock, a3 + arc0, a4 - a3, Arc2D.OPEN);
	        		gp.append(arc2, true);
	        		
	        		gp.quadTo(circX(cX,0,a4 + arc0), circY(cY,0,a4 + arc0),circX(cX,rBlock,a1 + arc0),circY(cY,rBlock,a1 + arc0));
	       		
	        		gp.closePath();
	        		g2.fill(gp);
        		}
        	}
        }       
	}	

}

	

