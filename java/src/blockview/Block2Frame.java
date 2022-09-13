package blockview;

/********************************************
 * Block view for a single selected chromosome
 * CAS515 rearranged; made smaller; tried to get scrollbar and resize to work...
 */
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;

import util.DatabaseReader;
import util.ErrorReport;
import util.ImageViewer;
import util.Utilities;

import java.util.Vector;
import java.util.TreeMap;
import java.awt.geom.Rectangle2D;

import javax.swing.event.*;

import symap.*;
import backend.*;

public class Block2Frame extends JFrame
{
	private static final long serialVersionUID = 1L;
	private int blockMaxHeight = 800; // CAS515 was 900
	private int bpPerPx;
	private final int chromWidth = 15, chromWidth2 = 25, layerWidth = 100;
	private final int tooManySeqs = 75;

	private int mRefIdx;
	private String refName, tarName, grpPfx;
	private int mIdx2, mGrpIdx, mPairIdx;
	private DatabaseReader mDB;
	private Vector<Integer> mColors;
	private boolean unorderedRef, unordered2, mReversed;
	private int unGrp2;
	private Vector<Block> mBlocks;
	private TreeMap<Integer,Vector<Block>> mLayout;
	private int mRefSize;
	private String mRefChr;
	private TreeMap<Integer,String> mGrp2Names;
	private TreeMap<Integer,Integer> colorOrder;
	private boolean isFpc = false;
	private boolean savedRects = false;
	private JButton saveBtn;
	private Container topPane;
	
	public Block2Frame(DatabaseReader dbReader, int refIdx, int idx2, int grpIdx, int pairIdx, boolean reversed) {
		super("SyMAP Block Detail View " + SyMAP.VERSION);
		mRefIdx = refIdx;
		mIdx2 = idx2;
		mGrpIdx = grpIdx;
		mPairIdx = pairIdx;
		mReversed = reversed;
		mDB = dbReader;
		
		setBackground(Color.white);
		getUniqueColors(100);
		
		init();
	}
	private void init() {
		try {
			if (!initFromDB()) return;
			if (!layoutBlocks()) return;
			
			int chrmHt = mRefSize/bpPerPx;
			int width = (1+mLayout.size()) * (layerWidth + chromWidth); 
			
			this.getContentPane().removeAll();
			setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			Dimension d = new Dimension(width, chrmHt + 100); // was 130
			setPreferredSize(d); setMinimumSize(d);
			
			topPane = new JPanel();
			topPane.setBackground(Color.white);
			topPane.setLayout(new GridBagLayout()); 
			JScrollPane scroller = new JScrollPane(topPane);
			
			GridBagConstraints c = new GridBagConstraints();
			c.gridx = c.gridy = 0;
			c.fill = GridBagConstraints.NORTH;
			
			// top row
			JPanel topRow = new JPanel();
			topRow.setBackground(Color.white);
			topRow.setLayout(new GridBagLayout());	
			
			JLabel title = new JLabel(tarName+ " synteny to " + refName + " " + grpPfx + mRefChr); 
			title.setFont(new Font("Verdana",Font.BOLD,18));
			GridBagConstraints c1 = new GridBagConstraints();
			c1.gridx = c1.gridy = 0;
			topRow.add(title,c1);	
			
			c1.gridx++;
			topRow.add(new JLabel("      "),c1);
			
			Icon icon = ImageViewer.getImageIcon("/images/print.gif"); 
			saveBtn =  new JButton(icon);
			saveBtn.setBackground(Color.white);
			saveBtn.setBorder(null);
			saveBtn.setToolTipText("Save Image");
			saveBtn.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					ImageViewer.showImage(topPane);
				}
			});
			c1.gridx++;
			topRow.add(saveBtn,c1);
			
			topRow.setVisible(true);
			topPane.add(topRow,c);

			BlockPanel blockPanel = new BlockPanel(this);
			d = new Dimension(width,chrmHt + 40);
			blockPanel.setPreferredSize(d); blockPanel.setMaximumSize(d);
			blockPanel.setVisible(true);
			c.gridy++;
			topPane.add(blockPanel,c);
			
			this.getContentPane().add(scroller);
			this.validate();
			this.repaint();
		}
		catch(Exception e){ErrorReport.print(e, "Init for Chromosome Blocks");}
	}
	private boolean initFromDB() {
		try {
			ResultSet rs;
			Statement s = mDB.getConnection().createStatement();
	
			rs = s.executeQuery("select count(*) as ngrps from xgroups where proj_idx=" + mRefIdx);
			rs.first();
			unorderedRef = (rs.getInt("ngrps") > tooManySeqs);

			rs = s.executeQuery("select count(*) as ngrps from xgroups where proj_idx=" + mIdx2);
			rs.first();
			unordered2 = (rs.getInt("ngrps") > tooManySeqs);
			
			if (unorderedRef && unordered2) {
				System.out.println("Genomes have too many chromosomes/contigs to show in block view");
				return false;
			}
			
			rs = s.executeQuery("select type from projects where idx=" + mIdx2);
			rs.first();
			isFpc = rs.getString("type").equals("fpc"); 
			
			rs = s.executeQuery("select idx from xgroups where name='0' and proj_idx=" + mIdx2);
			if (rs.first()) {
				unGrp2 = rs.getInt("idx");
			}

			rs = s.executeQuery("select value from proj_props where name='grp_prefix' and proj_idx=" + mRefIdx);
			rs.first();
			grpPfx = rs.getString("value");

			rs = s.executeQuery("select name,length from xgroups join pseudos on pseudos.grp_idx=xgroups.idx " +
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
				rs = s.executeQuery("select count(*) as count from blocks where pair_idx=" + mPairIdx + 
						" and (grp1_idx=" + unGrp2 +  " or grp2_idx=" + unGrp2 + ")");
				rs.first();
				haveUnanch = (0 < rs.getInt("count"));
				if (haveUnanch) {
					colorOrder.put(unGrp2, 0);
					mGrp2Names.put(unGrp2,"0");
					mColors.add(0, 0);
				}
			}

			rs = s.executeQuery("select name,idx from xgroups where proj_idx=" + mIdx2 + " and name != '0' order by sort_order asc");
			int i = mGrp2Names.size();
			while (rs.next()) {
				int idx = rs.getInt("idx");
				mGrp2Names.put(idx, rs.getString("name")); // name has grp_prefix removed
				colorOrder.put(idx,i);
				i++;
			}
			
			// CAS515 was using 'names from projects' which is directory
			rs = s.executeQuery("select value from proj_props where name='display_name' and proj_idx=" + mRefIdx);
			rs.first();
			refName = rs.getString("value");
			rs = s.executeQuery("select value from proj_props where name='display_name' and proj_idx=" + mIdx2);
			rs.first();
			tarName = rs.getString("value");
						
			rs.close();
			return true;
		}
		catch(Exception e){
			ErrorReport.print(e, "Init from DB for blocks");
			return false;
		}
	}
	private void paintBlocks(Graphics g) throws Exception {
		g.setFont(new Font("Verdana",0,14));
		int y0 = 40;
		int x = layerWidth/2;
		int chromHeight = mRefSize/bpPerPx;
		int chromXLeft, chromXRight;
		
		int L;
		for (L = -1;mLayout.containsKey(L); L--);
		L++;
		chromXLeft = x + (-L)*(layerWidth);
		chromXRight = chromXLeft + chromWidth; 
		g.setColor(Color.green);
		for (; L < 0; L++) {
			for (Block b : mLayout.get(L)) {
				int y1 = b.mS + y0;
				int ht = (b.mE - b.mS);
				g.drawLine(x, y1, chromXLeft, y1);
				g.drawLine(x, y1+ht-1, chromXLeft, y1+ht-1);
			}
			x += layerWidth;
		}
		x += chromWidth + layerWidth - chromWidth2;
		for (L=1; mLayout.containsKey(L); L++) {			
			for (Block b : mLayout.get(L)){
				int y1 = b.mS + y0;
				int ht = (b.mE - b.mS);
				g.drawLine(x, y1, chromXRight, y1);
				g.drawLine(x, y1+ht-1, chromXRight, y1+ht-1);
			} 
			x += layerWidth;
		}
		
		Font font1 = new Font("verdana",0,10); 
		Font font2 = new Font("verdana",Font.BOLD,10); 
		g.setFont(font1);
		x = layerWidth/2;		
		for (L = -1;mLayout.containsKey(L); L--);
		for (L++; L < 0; L++) {
			for (Block b : mLayout.get(L)){
				int y1 = b.mS + y0;
				int ht = (b.mE - b.mS);
				int co = colorOrder.get(b.unordered ? unGrp2 : b.mGrp2);
				g.setColor(new Color(mColors.get(co)));
				g.fillRect(x, y1, chromWidth2, ht);
				g.setColor(Color.black);
				g.drawRect(x, y1, chromWidth2, ht);
				
				String chrName = mGrp2Names.get(b.mGrp2) + ":"; // e.g. 3: where 3 is chr3
				int offset = 7*chrName.length() + 1;
				
				g.setFont(font2);
				g.drawString(chrName, x-10,  y1-15);
				g.setFont(font1);
				g.drawString(b.name,x-10+offset, y1-15);
				g.drawString(b.numHits + " anchors",x-10, y1-5);
				if (isFpc) {
					addCtgs(b,g,x,y1,chromWidth2,ht);
				}
				if (!savedRects) {
					b.blockRect = new Rectangle(x,y1,chromWidth2,ht);
				}		
			}
			x += layerWidth;
		}
		g.setColor(new Color(210,180,140));
		g.fillRect(x, y0,chromWidth,chromHeight);
		g.setColor(Color.black);
		g.drawRect(x, y0, chromWidth, chromHeight);
		g.drawString(refName,x, y0-15);
		g.drawString(grpPfx + mRefChr,x, y0-5);
		x += chromWidth + layerWidth - chromWidth2;
		for (L=1; mLayout.containsKey(L); L++) {
			for (Block b : mLayout.get(L)) {
				int y1 = b.mS + y0;
				int ht = (b.mE - b.mS);
				int co = colorOrder.get(b.unordered ? unGrp2 : b.mGrp2);
				g.setColor(new Color(mColors.get(co)));
				g.fillRect(x, y1, chromWidth2, ht);
				g.setColor(Color.black);
				g.drawRect(x, y1, chromWidth2, ht);
				String chrName = mGrp2Names.get(b.mGrp2) + ":";
				int offset = 7*chrName.length() + 1;
				g.setFont(font2);
				g.drawString(chrName, x-10,  y1-15);
				g.setFont(font1);
				g.drawString(b.name,x-10+offset, y1-15);
				g.drawString(b.numHits + " anchors",x-10, y1-5);
				if (isFpc) {
					addCtgs(b,g,x,y1,chromWidth2,ht);
				}
				if (!savedRects) {
					b.blockRect = new Rectangle(x,y1,chromWidth2,ht);
				}				
			} 
			x += layerWidth;
		}	
		savedRects = true;
	}	
	private void addCtgs(Block b, Graphics g, int x, int y, int w, int h) throws Exception {
		String[] ctgs = b.ctgs.split(",");
		if (b.ctgSizes == null) {
			b.ctgSizes = new int[ctgs.length];
			b.ctgNums = new int[ctgs.length];
			b.totalSize = 0;
			Statement s = mDB.getConnection().createStatement(); // CAS515 moved from calling routine
			ResultSet rs=null;
			for (int i = 0; i < b.ctgSizes.length; i++) {
				rs = s.executeQuery("select size,number from contigs where idx=" + ctgs[i]);
				rs.first();
				b.ctgSizes[i] = rs.getInt("size");
				b.ctgNums[i] = rs.getInt("number");
				b.totalSize += b.ctgSizes[i];
			}
			if (rs!=null) rs.close();
			s.close();
		}
		float discrep = 0.0F;
		g.setColor(Color.black);
		int curY = -10; // stores the current position of contig numbers drawn to the right
		for (int i = 0; i < b.ctgSizes.length; i++) {
			float ctgHF = h*b.ctgSizes[i]/b.totalSize;
			int ctgH = (int)ctgHF;
			discrep += (ctgHF - ctgH);
			if (discrep > 1) {
				ctgH++;
				discrep--;
			}		
			if (ctgH >= 14) {
				if (b.mGrp2 == unGrp2) {
					g.setColor(Color.white);
				}
				g.drawString("" + b.ctgNums[i], x + 1, y + 10);
				if (b.mGrp2 == unGrp2){
					g.setColor(Color.black);
				}			
			}
			else {
				// draw the number string to the right
				if (y+ctgH/2+4 > curY + 10) {
					// we're far enough below the last one that we can draw it 
					// immediately to the right and restart the positioning
					curY = y +ctgH/2+4 ;
				}
				else {
					curY += 10;
				}
				g.drawString("" + b.ctgNums[i], x + w + 10, curY);
				g.drawLine(x + w, y + ctgH/2, x + w + 9, curY-4);
				
			}
			y += ctgH;
			if (i < b.ctgSizes.length-1) {
				g.drawLine(x, y, x + w, y);
			}
		}
	}
	private boolean layoutBlocks() {
	try {
		ResultSet rs;
		Statement s = mDB.getConnection().createStatement();

		mLayout = new TreeMap<Integer,Vector<Block>>();

		bpPerPx = mRefSize/blockMaxHeight;
		
		// get the blocks in decreasing order of size.
		// as we add each one to the list we assign it an index which is just its order in the list. 
		mBlocks = new Vector<Block>();
		if (!mReversed) {
			rs = s.executeQuery("select idx,grp1_idx as grp2, start2 as start,end2 as end, blocknum,ctgs1 as ctgs," +
					" start1 as s2, end1 as e2 from blocks where pair_idx=" + mPairIdx + 
				" and grp2_idx=" + mGrpIdx + " order by (end2 - start2) desc");
		}
		else {
			rs = s.executeQuery("select idx, grp2_idx as grp2, start1 as start,end1 as end,blocknum,ctgs2 as ctgs, " +
					" start2 as s2, end2 as e2 from blocks where pair_idx=" + mPairIdx + 
			" and grp1_idx=" + mGrpIdx + " order by (end1 - start1) desc");		
		}
		while (rs.next()) {
			int grp2 = rs.getInt("grp2"); //(unordered2 ? unGrp2 : rs.getInt("grp2"));
			int start = rs.getInt("start");
			int end = rs.getInt("end");
			int s2 = rs.getInt("s2");
			int e2 = rs.getInt("e2");
			int blocknum = rs.getInt("blocknum");
			int idx = rs.getInt("idx");
			String ctgs = rs.getString("ctgs");
			String blockName = Utilities.blockStr(mGrp2Names.get(grp2), mRefChr, blocknum); // CAS513 call blockStr
			Block b = new Block(grp2,start/bpPerPx,end/bpPerPx,s2,e2,blockName, idx,ctgs,unordered2);
			mBlocks.add(b);
		}
		rs.close();
		
		for (Block b : mBlocks) {
			b.numHits = countBlockHits(b.idx,s);
		}
		// go through the blocks and make the layout
		
		for(int i = 0; i < mBlocks.size(); i++) {
			Block b = mBlocks.get(i);
			// find the first level where this block can fit
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
			}
			mLayout.get(L).add(b);
		}

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
	private int countBlockHits(int idx, Statement s) throws Exception {
		ResultSet rs = s.executeQuery("select count(*) as count from pseudo_block_hits where block_idx=" + idx);
		rs.first();
		return rs.getInt("count");
	}
	private class Block {
		int mS; int mE; /// on the reference, stored in *pixels*, so that the "overlaps"
						// test can use a pixel gap
		int mS2; int mE2; // on the query, stored as *basepairs*
		int mGrp2; 
		String name;
		int numHits = 0;
		int idx;
		String ctgs;
		Rectangle2D blockRect;
		int[] ctgSizes = null;
		int[] ctgNums = null;
		int totalSize = 0;
		boolean unordered;
		
		public Block(int _grp2, int _s, int _e, int _s2, int _e2, String _name, int _idx, String _ctgs,boolean _unord)
		{
			mGrp2 = _grp2;
			mS = _s;
			mE = _e;
			mS2 = _s2;
			mE2 = _e2;
			name = _name;
			idx = _idx;
			ctgs = _ctgs;
			unordered = _unord;
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
			SyMAP symap = new SyMAP(mDB, null);
			if (!isFpc) {
				symap.getDrawingPanel().setSequenceTrack(1,mRefIdx,mGrpIdx,Color.CYAN);
				symap.getDrawingPanel().setSequenceTrack(2,mIdx2,b.mGrp2,Color.GREEN);
				symap.getDrawingPanel().setTrackEnds(1,b.mS*bpPerPx,b.mE*bpPerPx);
				symap.getDrawingPanel().setTrackEnds(2,b.mS2,b.mE2);				
			}
			else {
				symap.getDrawingPanel().setSequenceTrack(1,mRefIdx,mGrpIdx,Color.CYAN);
				symap.getDrawingPanel().setBlockTrack(2,mIdx2,Utils.intArrayJoin(b.ctgNums,","),Color.GREEN);
				symap.getDrawingPanel().setTrackEnds(1,b.mS*bpPerPx,b.mE*bpPerPx);								
			}
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
