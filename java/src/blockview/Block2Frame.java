package blockview;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.Vector;
import java.util.TreeMap;
import java.awt.geom.Rectangle2D;
import javax.swing.event.*;

import database.DBconn2;
import symap.manager.Mproject;
import symap.Globals;
import symap.drawingpanel.SyMAP2d;
import util.ErrorReport;
import util.ImageViewer;
import util.Utilities;

/********************************************
 * Block view for a single selected chromosome
 * CAS515 rearranged; made smaller; tried to get scrollbar and resize to work...
 */
public class Block2Frame extends JFrame {
	private static final long serialVersionUID = 1L;
	private final int fBlockMaxHeight = 800; // CAS515 was 900
	private final int fChromWidth = 15, fChromWidth2 = 25; // width of Ref Chr; all others
	private final int fLayerWidth = 90; 		// CAS56 was 100; width of level
	private final int fTooManySeqs = 75;

	private DBconn2 tdbc2;
	
	private int bpPerPx;
	private int mRefIdx;
	private String refName, tarName, grpPfx;
	private int mIdx2, mGrpIdx, mPairIdx;
	private Vector<Integer> mColors;
	private boolean unorderedRef, unordered2, mReversed;
	private int unGrp2;
	private Vector<Block> mBlocks;
	private TreeMap<Integer,Vector<Block>> mLayout;
	private int mRefSize;
	private String mRefChr;
	private TreeMap<Integer,String> mGrp2Names;
	private TreeMap<Integer,Integer> colorOrder;
	
	private boolean savedRects = false;
	private JButton saveBtn;
	private JPanel mainPane; // CAS533 changed from Container to JPanel for ImageViewer
	private int farL, farR;
	
	// Called from BlockViewFrame - subwindow
	public Block2Frame(DBconn2 tdbc2, int refIdx, int idx2, int grpIdx, int pairIdx, boolean reversed) {
		super("SyMAP " + Globals.VERSION + " - Block Detail View");
		mRefIdx = refIdx;
		mIdx2 = idx2;
		mGrpIdx = grpIdx;
		mPairIdx = pairIdx;
		mReversed = reversed;
		
		this.tdbc2 = new DBconn2("BlocksC-" + DBconn2.getNumConn(), tdbc2); 
		
		setBackground(Color.white);
		getUniqueColors(100);
		
		init();
	}
	private void init() {
		try {
			if (!initFromDB()) return;
			if (!layoutBlocks()) return;
			
			this.getContentPane().removeAll();
			setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			
			mainPane = new JPanel();
			mainPane.setBackground(Color.white);
			mainPane.setLayout(new GridBagLayout()); 
			JScrollPane scroller = new JScrollPane(mainPane);
			
			GridBagConstraints cPane = new GridBagConstraints();
			cPane.gridx = cPane.gridy = 0;
			cPane.fill = GridBagConstraints.NORTH;
			
			// top row
			JPanel topRow = new JPanel();
			topRow.setBackground(Color.white);
			topRow.setLayout(new GridBagLayout());	
			
			JLabel title = new JLabel(tarName+ " to " + refName + " " + grpPfx + mRefChr); 
			title.setFont(new Font("Verdana",Font.BOLD,18));
			GridBagConstraints cRow = new GridBagConstraints();
			cRow.gridx = cRow.gridy = 0;
			topRow.add(title,cRow);	
			
			cRow.gridx++;
			topRow.add(new JLabel("      "),cRow);
			
			Icon icon = ImageViewer.getImageIcon("/images/print.gif"); 
			saveBtn =  new JButton(icon);
			saveBtn.setBackground(Color.white);
			saveBtn.setBorder(null);
			saveBtn.setToolTipText("Save Image");
			saveBtn.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					ImageViewer.showImage("Block", mainPane);
				}
			});
			cRow.gridx++;
			topRow.add(saveBtn,cRow);
			
			topRow.setVisible(true);
			mainPane.add(topRow,cPane);

		// Click for detail
			JPanel selectRow = new JPanel();
			selectRow.setBackground(Color.white);
			JLabel select = new JLabel("Click block for 2D display"); 
			select.setFont(new Font("Verdana",0,12));
			selectRow.add(select);
			cPane.gridy++; 
			mainPane.add(selectRow,cPane);
			
		// Block panel
			BlockPanel blockPanel = new BlockPanel(this);
			blockPanel.setVisible(true);
			cPane.gridy++;
			mainPane.add(blockPanel,cPane);
		
		// dimensions
			int chrmHt = (mRefSize/bpPerPx) + 100;
			int totalWinWidth = ((1+mLayout.size()) * fLayerWidth) + fChromWidth; 
			int d1winWidth =  totalWinWidth;
			if (d1winWidth>1000)     d1winWidth=1000;
			else if (d1winWidth<400) d1winWidth=400;
			Dimension d = new Dimension(d1winWidth, chrmHt);
			setPreferredSize(d); setMinimumSize(d);
			
			d = new Dimension(totalWinWidth,chrmHt);
			blockPanel.setPreferredSize(d); blockPanel.setMinimumSize(d);
			
			this.setLocationRelativeTo(null);
			this.getContentPane().add(scroller);
			this.validate();
			this.repaint();
		}
		catch(Exception e){ErrorReport.print(e, "Init for Chromosome Blocks");}
	}
	// Main Block can be closed separately, so this needs its own tdbc closed
	public void dispose() { // override; CAS541 add
		setVisible(false); 
		tdbc2.close();  
		super.dispose();
	}
	private boolean initFromDB() {
		try {
			ResultSet rs;
			
		// xgroups
			int cnt = tdbc2.executeCount("select count(*) as ngrps from xgroups where proj_idx=" + mRefIdx);
			unorderedRef = (cnt > fTooManySeqs);

			cnt = tdbc2.executeCount("select count(*) as ngrps from xgroups where proj_idx=" + mIdx2);
			unordered2 = (cnt > fTooManySeqs);
			
			if (unorderedRef && unordered2) {
				System.out.println("Genomes have too many chromosomes/contigs to show in block view");
				return false;
			}
			
			unGrp2 = tdbc2.executeInteger("select idx from xgroups where name='0' and proj_idx=" + mIdx2);
			
			rs = tdbc2.executeQuery("select name,length from xgroups join pseudos on pseudos.grp_idx=xgroups.idx " +
					" where xgroups.idx=" + mGrpIdx);
			if (!rs.first()) {
				System.out.println("Unable to find reference group " + mGrpIdx);
				return false;
			}
			mRefSize = rs.getInt("length");
			mRefChr = rs.getString("name");

			mGrp2Names = new TreeMap<Integer,String>();
			colorOrder = new TreeMap<Integer,Integer>();
			if (unordered2) {
				colorOrder.put(unGrp2, 0);
				mGrp2Names.put(unGrp2,"0");		
				mColors.add(0, 0);
			}
			else {
				boolean haveUnanch = false;
				cnt = tdbc2.executeCount("select count(*) as count from blocks where pair_idx=" + mPairIdx + 
						" and (grp1_idx=" + unGrp2 +  " or grp2_idx=" + unGrp2 + ")");
				haveUnanch = (0 < cnt);
				if (haveUnanch) {
					colorOrder.put(unGrp2, 0);
					mGrp2Names.put(unGrp2,"0");
					mColors.add(0, 0);
				}
			}

			rs = tdbc2.executeQuery("select name, idx from xgroups where proj_idx=" + mIdx2 + " and name != '0' order by sort_order asc");
			int i = mGrp2Names.size();
			while (rs.next()) {
				int idx = rs.getInt("idx");
				mGrp2Names.put(idx, rs.getString("name")); // name has grp_prefix removed
				colorOrder.put(idx,i);
				i++;
			}
			
			// CAS515 was using 'names from projects' which is directory
			Mproject tProj = new Mproject();
			String grp_prefix = tProj.getKey(tProj.lGrpPrefix);
			
			rs = tdbc2.executeQuery("select value from proj_props where name='" + grp_prefix + "' and proj_idx=" + mRefIdx);
			grpPfx = rs.first() ? rs.getString("value") : "Chr"; // CAS534 should be loaded, but if not...
			rs.close();
			
			String display_name = tProj.getKey(tProj.sDisplay);
			refName = tdbc2.executeString("select value from proj_props where name='"+display_name+"' and proj_idx=" + mRefIdx);
			tarName = tdbc2.executeString("select value from proj_props where name='"+display_name+"' and proj_idx=" + mIdx2);
			
			return true;
		}
		catch(Exception e){
			ErrorReport.print(e, "Init from DB for blocks");
			return false;
		}
	}
	private void paintBlocks(Graphics g1) throws Exception {
		Graphics2D g2 = (Graphics2D) g1;
		g2.setFont(new Font("Verdana",0,14));
		int y0 = 40;
		int x = fLayerWidth/2;
		int chromHeight = mRefSize/bpPerPx;
		int chromXLeft, chromXRight;
		
		// CAS516 add different outline to inverted
		// BasicStroke(float width, int cap, int join, float miterlimit, float[] dash, float dash_phase)
		Stroke stroke0 = new BasicStroke(1.0f);
		Stroke stroke1 = new BasicStroke(2.0f);
		float [] dash = {2f, 2f};
		Stroke stroke2 = new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1.0f, dash, 2.0f);
		
		int L=farL;
		chromXLeft  = x + (-L)*(fLayerWidth);
		chromXRight = chromXLeft + fChromWidth; 
		g2.setColor(Color.green);
		
		// Draw lines from ref to chr to the left
		for (; L < 0; L++) {
			for (Block b : mLayout.get(L)) {
				int y1 =  b.mS + y0;
				int ht =  b.mE - b.mS;
				g2.drawLine(x, y1, chromXLeft, y1);
				g2.drawLine(x, y1+ht-1, chromXLeft, y1+ht-1);
			}
			x += fLayerWidth;
		}
		// Draw lines from ref to chr to the rigth
		x += fChromWidth + fLayerWidth - fChromWidth2;
		for (L=1; mLayout.containsKey(L); L++) {			
			for (Block b : mLayout.get(L)){
				int y1 = b.mS + y0;
				int ht = b.mE - b.mS;
				g2.drawLine(x, y1, chromXRight, y1);
				g2.drawLine(x, y1+ht-1, chromXRight, y1+ht-1);
			} 
			x += fLayerWidth;
		}
		
		Font font1 = new Font("verdana",0,10); 
		Font font2 = new Font("verdana",Font.BOLD,10); 
		g2.setFont(font1);
		x = fLayerWidth/2;	
		
		for (L=farL; L < 0; L++) {
			for (Block b : mLayout.get(L)){
				int y1 = b.mS + y0;
				int ht = b.mE - b.mS;
				int co = colorOrder.get(b.unordered ? unGrp2 : b.mGrp2);
				g2.setColor(new Color(mColors.get(co)));
				g2.fillRect(x, y1, fChromWidth2, ht);
				g2.setColor(Color.black);
				
				if (b.bInv) g2.setStroke(stroke2);
				else        g2.setStroke(stroke1);
				g2.drawRect(x, y1, fChromWidth2, ht);
				
				String chrName = mGrp2Names.get(b.mGrp2) + ":"; // e.g. 3: where 3 is chr3
				int offset = 7*chrName.length() + 1;
				
				g2.setFont(font2);
				g2.drawString(chrName, x-10,  y1-15);
				g2.setFont(font1);
				g2.drawString(b.name,x-10+offset, y1-15);
				g2.drawString(b.numHits + " anchors",x-10, y1-5);
				
				if (!savedRects) {
					b.blockRect = new Rectangle(x,y1,fChromWidth2,ht);
				}		
			}
			x += fLayerWidth;
		}
		// Draw ref chr
		g2.setStroke(stroke0);
		g2.setColor(Color.LIGHT_GRAY);  // CAS516 new Color(210,180,140)
		g2.fillRect(x, y0,fChromWidth,chromHeight);
		g2.setColor(Color.black);
		g2.drawRect(x, y0, fChromWidth, chromHeight);
		g2.drawString(refName,x, y0-15);
		g2.drawString(grpPfx + mRefChr,x, y0-5);
		
		// Draw rectangles to the right
		x += fChromWidth + fLayerWidth - fChromWidth2;
		for (L=1; L<=farR; L++) {
			for (Block b : mLayout.get(L)) {
				int y1 = b.mS + y0;
				int ht = b.mE - b.mS;
				int co = colorOrder.get(b.unordered ? unGrp2 : b.mGrp2);
				g2.setColor(new Color(mColors.get(co)));
				g2.fillRect(x, y1, fChromWidth2, ht);
				g2.setColor(Color.black);
				
				if (b.bInv) g2.setStroke(stroke2);
				else        g2.setStroke(stroke1);
				g2.drawRect(x, y1, fChromWidth2, ht);
				String chrName = mGrp2Names.get(b.mGrp2) + ":";
				int offset = 7*chrName.length() + 1;
				g2.setFont(font2);
				g2.drawString(chrName, x-10,  y1-15);
				g2.setFont(font1);
				g2.drawString(b.name,x-10+offset, y1-15);
				g2.drawString(b.numHits + " anchors",x-10, y1-5);
				
				if (!savedRects) {
					b.blockRect = new Rectangle(x,y1,fChromWidth2,ht);
				}				
			} 
			x += fLayerWidth;
		}	
		savedRects = true;
	}	
	
	private boolean layoutBlocks() {
	try {
		ResultSet rs;
		
		mLayout = new TreeMap<Integer,Vector<Block>>();

		bpPerPx = mRefSize/fBlockMaxHeight;
		
		// get the blocks in decreasing order of size.
		// as we add each one to the list we assign it an index which is just its order in the list. 
		mBlocks = new Vector<Block>();
		String sql;
		if (!mReversed) { // CAS516 add corr
			sql ="select idx, grp1_idx as grp2, start2 as start, end2 as end, blocknum," +
				" start1 as s2, end1 as e2, corr " +
				" from blocks where pair_idx=" + mPairIdx + 
				" and grp2_idx=" + mGrpIdx + " order by (end2 - start2) desc";
		}
		else {
			sql="select idx, grp2_idx as grp2, start1 as start, end1 as end, blocknum,  " +
			   " start2 as s2, end2 as e2, corr " +
			   " from blocks where pair_idx=" + mPairIdx + 
			   " and grp1_idx=" + mGrpIdx + " order by (end1 - start1) desc";		
		}
		rs = tdbc2.executeQuery(sql);
		while (rs.next()) {
			int grp2 = rs.getInt("grp2"); //(unordered2 ? unGrp2 : rs.getInt("grp2"));
			int start = rs.getInt("start");
			int end = rs.getInt("end");
			int s2 = rs.getInt("s2");
			int e2 = rs.getInt("e2");
			int blocknum = rs.getInt("blocknum");
			int idx = rs.getInt("idx");
			boolean isInv = (rs.getFloat("corr")<0); // CAS516 add
			String blockName = Utilities.blockStr(mGrp2Names.get(grp2), mRefChr, blocknum); // CAS513 call blockStr
			Block b = new Block(grp2, start/bpPerPx, end/bpPerPx, s2, e2, blockName, idx, unordered2, isInv);
			mBlocks.add(b);
		}
		rs.close();
		
		for (Block b : mBlocks) {
			b.numHits = tdbc2.executeCount("select count(*) as count from pseudo_block_hits where block_idx=" + b.idx);
			
		}
		// go through the blocks and make layout; find the first level where this block can fit
		// similar algorithm as BlockView, so order is basically the same
		for(int i = 0; i < mBlocks.size(); i++) {
			Block b = mBlocks.get(i);
			
			int L;
			for (L = 1; ;L++) {
				if (!mLayout.containsKey(L)) {
					break;
				}
				else {
					boolean hasSpace = true;
					for (Block b1 : mLayout.get(L)) {
						if (b.overlaps(b1)) {
							hasSpace = false;
							break;
						}
					}
					if (hasSpace) {
						break;
					}
				}
				if (!mLayout.containsKey(-L)) {
					L = -L;					
					break;
				}
				else {
					boolean hasSpace = true;
					for (Block b1 : mLayout.get(-L)) {
						if (b.overlaps(b1)) {
							hasSpace = false;
							break;
						}
					}
					if (hasSpace) {
						L = -L;
						break;
					}
				}				
			}
			if (!mLayout.containsKey(L)) {
				mLayout.put(L, new Vector<Block>());
				if (L<0) farL=L;
				else farR=L;
			}
			mLayout.get(L).add(b);
		}
/** CAS516 not necessary
		for (int L = 1; ; L++) {
			if (mLayout.containsKey(L)) {
				if (!mLayout.containsKey(-L)) {
					mLayout.put(-L, new Vector<Block>());
				}
			}
			else if (mLayout.containsKey(-L)) {
				if (!mLayout.containsKey(L)) {
					mLayout.put(L, new Vector<Block>());
				}					
			}
			else {
				break;
			}
		}
**/
		return true;
	}
	catch (Exception e) {ErrorReport.print(e, "Layout blocks"); return false;}
	}	
	private int colorInt(int R, int G, int B) {
		return (R<<16) + (G<<8) + B;
	}
	
	private void getUniqueColors(int amount) 
	{
		int max = (0xE0 << 16) + (0xE0 << 8)  + 0xE0;
		int step =  (15*max)/(4*amount);
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
	    
		for (int c = 0xE0; mColors.size() <= amount ; c += step ) {
			c %= max;
			int R = c >> 16;
			int G = c >> 8;
			int B = c & 0xFF;
			if (Math.abs(R-G) <= 0x30 && Math.abs(R-B) <= 0x30 && Math.abs(B-G) <= 0x30) {
				continue; // too gray
			}
			boolean tooClose = false;
			for (int j = 0; j < mColors.size(); j++) {
				int c1 = mColors.get(j);
				int R1 = c1 >> 16; int G1 = c1 >> 8; int B1 = c1 & 0xFF;
				if ( Math.abs(R - R1) <= 0x30 && Math.abs(G - G1) <= 0x30 && Math.abs(B - B1) <= 0x30) {
					tooClose = true;
					break;
				}
				if (tooClose) continue;
			}
			mColors.add(c);
		}
	}	
	
	private class Block {
		int mS; int mE;   // on the reference, stored in *pixels*, so that the "overlaps"
		int mS2; int mE2; // on the query, stored as *basepairs*
		int mGrp2; 
		String name;
		int numHits = 0;
		int idx;
		Rectangle2D blockRect;
		boolean unordered;
		boolean bInv;
		
		public Block(int _grp2, int _s, int _e, int _s2, int _e2, String _name, int _idx, 
				boolean _unord, boolean isInv)
		{
			mGrp2 = _grp2;
			mS = _s;
			mE = _e;
			mS2 = _s2;
			mE2 = _e2;
			name = _name;
			idx = _idx;
			unordered = _unord;
			bInv = isInv;
		}
		public boolean overlaps(Block b) {
			return (Math.max(b.mS, mS) <= Math.min(b.mE,mE) + 30); // should match value in BlockViewFrame
		}
	}	
	private void blockMouseMoved(MouseEvent m) {
		for (Block b : mBlocks) {
			if (b.blockRect.contains(m.getPoint())) {				
				Cursor c = new Cursor(Cursor.HAND_CURSOR);
				setCursor(c);
				return;
			}
		}
		Cursor c = new Cursor(Cursor.DEFAULT_CURSOR);
		setCursor(c);
		
	}		
	private void blockMouseClicked(MouseEvent m) {
		for (Block b : mBlocks) {
			if (b.blockRect.contains(m.getPoint())) {				
				showDetailView(b);
				return;
			}
		}
	}			
	private void showDetailView(Block b) {
		try {
			SyMAP2d symap = new SyMAP2d(tdbc2, null); // makes new conn
			
			symap.getDrawingPanel().setSequenceTrack(1,mRefIdx,mGrpIdx,Color.CYAN);
			symap.getDrawingPanel().setSequenceTrack(2,mIdx2,b.mGrp2,Color.GREEN);
			symap.getDrawingPanel().setTrackEnds(1,b.mS*bpPerPx,b.mE*bpPerPx);
			symap.getDrawingPanel().setTrackEnds(2,b.mS2,b.mE2);
									
			symap.getFrame().showX(); // CAS512
		}
		catch (Exception err) {
			ErrorReport.print(err, "Show Detail View");
		}
		finally {}
	}
	private class BlockPanel extends JPanel implements MouseInputListener {
		private static final long serialVersionUID = 1L;
		Block2Frame blockFrame;
		
		public BlockPanel(Block2Frame _blockFrame) {
			super();
			setBackground(Color.white);
			blockFrame = _blockFrame;
			addMouseListener(this);
			addMouseMotionListener(this);
		}
	    public void paintComponent(Graphics g) {
	        super.paintComponent(g); //paint background
	        try {
	        	blockFrame.paintBlocks(g);
	        }
	        catch(Exception e) {
	        	ErrorReport.print(e, "Draw blocks");
	        }
	    }
	    public void mouseClicked(MouseEvent m){
	    	blockFrame.blockMouseClicked(m);
	    }
	    public void mousePressed(MouseEvent m){
	    }	    
	    public void mouseEntered(MouseEvent m){
	    }	    
	    public void mouseReleased(MouseEvent m){
	    }	    
	    public void mouseExited(MouseEvent m){
	    }	
	    public void mouseDragged(MouseEvent m){
	    }	  
	    public void mouseMoved(MouseEvent m) {
	    	blockFrame.blockMouseMoved(m);
	    }	  	    
	}		
}
