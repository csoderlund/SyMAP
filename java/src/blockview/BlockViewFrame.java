package blockview;

import javax.swing.*;

import java.awt.*;
import java.awt.event.*;
import java.sql.*;

import util.DatabaseReader;
import util.ImageViewer;

import java.util.Vector;
import java.util.TreeMap;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import javax.swing.event.*;
// CAS42 12/27/17 Seq-FPC has crashes from multiple problem. 
// They are fixed, but Seq-FPC reverse leaves a blank page of blocks
public class BlockViewFrame extends JFrame
{
	private static final long serialVersionUID = 1L;
	public static final int MAX_GRPS =  150; // CAS42 12/6/17 - if #seqs>MAX_GRPS, blocks does not work right
	public static final int maxColors = 100;
	
	int mRefIdx, mIdx2;
	String refName, Name2;
	DatabaseReader mDB;
	Vector<Integer> mColors;
	
	int mBlocksWidth;
	int mBlocksHeight;
	TreeMap<Integer,TreeMap<Integer,Vector<Block>>> mLayout;
	TreeMap<Integer,String> mRefNames, mGrp2Names;
	TreeMap<Integer,Integer> mRefSizes;
	Vector<Integer> mRefOrder;
	Vector<Rectangle> mBlockRects;
	TreeMap<Integer,Integer> grpColorOrder;
	Vector<Integer> mGrps2;	
	Vector<Block> mBlocks;
	JButton saveBtn;
	
	boolean reversed = false;
	int mPairIdx;
	final int chromWidth = 8;
	final int layerWidth = 8;
	final int chromSpace = 25;
	int blockMaxHeight = 700;
	int bpPerPx = 10000;
	int chromLabelOffset = 0;
	int chromXOffset = 0;
	Font chromeFont,legendFont, chromFontVert;
	boolean chromLabelVert = false;
	boolean unorderedRef = false;
	boolean unordered2 = false;
	int unGrp2 = 0; // idx of the unanchored group, "0"
	Container topPane = null;

	public BlockViewFrame(DatabaseReader dbReader, int projXIdx, int projYIdx) throws Exception
	{
		super("SyMAP Block View");
		mRefIdx = projXIdx;
		mIdx2 = projYIdx;
		mDB = dbReader;
		init();
	}
	public void init() 
	{		
		try
		{
			this.getContentPane().removeAll();

			setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			getUniqueColors(maxColors);
			chromeFont = new Font("Verdana",0,14);
			legendFont = new Font("Verdana",0,12);
			AffineTransform rot90 = new AffineTransform(0,-1,1,0,0,0);
			chromFontVert = chromeFont.deriveFont(rot90);
				
			ResultSet rs;
			Statement s = mDB.getConnection().createStatement();
	
			rs = s.executeQuery("select count(*) as ngrps from groups where proj_idx=" + mRefIdx);
			rs.first();
			unorderedRef = (rs.getInt("ngrps") > MAX_GRPS);

			rs = s.executeQuery("select count(*) as ngrps from groups where proj_idx=" + mIdx2);
			rs.first();
			unordered2 = (rs.getInt("ngrps") > MAX_GRPS);
			
			if (unorderedRef && unordered2) 
			{
				System.out.println("Genomes have too many chromosomes/contigs to show in block view");
				return;
			}

			if (unorderedRef)
			{
				int tmp = mRefIdx;
				mRefIdx = mIdx2;
				mIdx2 = tmp;
				unordered2 = true;
				unorderedRef = false;
			}
		
			reversed = false;
			rs = s.executeQuery("select idx from pairs where proj1_idx=" + mIdx2 + " and proj2_idx=" + mRefIdx);
			if (rs.first())
			{
				mPairIdx = rs.getInt("idx");
			}
			else
			{
				rs = s.executeQuery("select idx from pairs where proj2_idx=" + mIdx2 + " and proj1_idx=" + mRefIdx);
				if (rs.first())
				{
					mPairIdx = rs.getInt("idx");
					reversed = true;
				}
				else
				{
					System.out.println("Unable to find project pair");
					return;
				}
			}
			
			rs = s.executeQuery("select idx from groups where name='0' and proj_idx=" + mIdx2);
			if (rs.first())
			{
				unGrp2 = rs.getInt("idx");
			}
			
			JPanel legendPane = new JPanel();
			legendPane.setBackground(Color.white);
			drawLegend(legendPane);
	
			layoutBlocks();
			int nLevels = 0;
			for (int grp : mLayout.keySet())
			{
				nLevels += mLayout.get(grp).size();			
			}
	
			int blockTotalWidth = mLayout.size()*(chromWidth + chromSpace) + nLevels*layerWidth;
			if (blockTotalWidth < 400) blockTotalWidth=400;
			int blockWindowWidth =  (blockTotalWidth > 1200? 1200 : blockTotalWidth);
			setPreferredSize(new Dimension(blockWindowWidth + 100,100 + blockMaxHeight + chromLabelOffset + 150));
			setMinimumSize(new Dimension(blockWindowWidth + 100,100 + blockMaxHeight + chromLabelOffset + 150));
	
			topPane = new JPanel();
			topPane.setBackground(Color.white);
			topPane.setLayout(new GridBagLayout()); 
			topPane.setPreferredSize(new Dimension(blockTotalWidth,blockMaxHeight + chromLabelOffset + 150));
			topPane.setMinimumSize(new Dimension(blockTotalWidth,blockMaxHeight + chromLabelOffset + 150));
			JScrollPane scroller = new JScrollPane(topPane);
						
			GridBagConstraints c = new GridBagConstraints();
			c.gridx = 0;
			c.gridy = 0;
			JPanel pane;
			
			rs = s.executeQuery("select value from proj_props where name='display_name' and proj_idx=" + mRefIdx);
			rs.first();
			refName = rs.getString("value");
			rs = s.executeQuery("select value from proj_props where name='display_name' and proj_idx=" + mIdx2);
			rs.first();
			Name2 = rs.getString("value");
	
			pane = new JPanel();
			c.fill = GridBagConstraints.CENTER;
			pane.setBackground(Color.white);
			pane.setLayout(new GridBagLayout());	
			GridBagConstraints c1 = new GridBagConstraints();
			c1.gridx = 0;
			c1.gridy = 0;
			JLabel title = new JLabel(Name2 + " synteny to " + refName); 
			title.setFont(new Font("Verdana",Font.BOLD,18));
			pane.add(title,c1);	
			c1.gridx++;
			c1.fill = GridBagConstraints.SOUTHEAST;
			pane.add(new JLabel("          "),c1);
			
			if (!unordered2)
			{
				c1.gridx++;
				c1.fill = GridBagConstraints.SOUTHEAST;
				JLabel revLabel = new JLabel("<html><u>reverse</u>");
				revLabel.addMouseListener(reverseClick);
				pane.add(revLabel,c1);
			}
			else { // CAS42 12/6/17 - there was NO message if this happened
				System.out.println("Warning: one of the genomes has >" + MAX_GRPS + " sequences");
				System.out.println("   this causes the 'reverse' option to not work on the block view");
				System.out.println("   use scripts/lenFasta.pl to find cutoff length for min_size.");
				System.out.println("   email symap@agcol.arizona.edu if you would like further instructions");
			}
			c1.gridx++;
			pane.add(new JLabel("      "),c1);
			
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
			pane.add(saveBtn,c1);
			
			topPane.add(pane,c);
			
			c.gridy++;
			pane = new JPanel();
			c.fill = GridBagConstraints.CENTER;
			pane.setBackground(Color.white);
			pane.add(new JLabel(Name2 + " color key"));
			topPane.add(pane,c);
			
			c.gridy++;
			c.fill = GridBagConstraints.CENTER;
			topPane.add(legendPane,c);
	
			c.gridy++;
			BlockPanel blockPanel = new BlockPanel(this);
			blockPanel.setPreferredSize(new Dimension(blockTotalWidth,blockMaxHeight + chromLabelOffset + 10));
			blockPanel.setMinimumSize(new Dimension(blockTotalWidth,blockMaxHeight + chromLabelOffset + 10));
			blockPanel.setVisible(true);
			topPane.add(blockPanel,c);
			
			this.getContentPane().add(scroller);
			
			this.validate();
			this.repaint();
		}
		catch(Exception e)
		{
			e.printStackTrace();
			return;
		}

	}
	private MouseListener reverseClick = new MouseAdapter() 
	{
		public void mouseEntered(MouseEvent e) 
		{
			Cursor cur = new Cursor(Cursor.HAND_CURSOR);
			setCursor(cur);
		}

		public void mouseExited(MouseEvent e) 
		{
			Cursor cur = new Cursor(Cursor.DEFAULT_CURSOR);
			setCursor(cur);
		}		
		public void mouseClicked(MouseEvent e) 
		{
			reverseView();
		}
	};	
	void reverseView() 
	{
		int tmp = mIdx2;
		mIdx2 = mRefIdx;
		mRefIdx = tmp;
		init();
	}
	void layoutBlocks() throws Exception
	{
		ResultSet rs;
		Statement s = mDB.getConnection().createStatement();

		mRefNames = new TreeMap<Integer,String>();
		mRefSizes = new TreeMap<Integer,Integer>();
		mRefOrder = new Vector<Integer>();
		mLayout = new TreeMap<Integer,TreeMap<Integer,Vector<Block>>>();
		rs = s.executeQuery("select idx,name,length from groups join pseudos on pseudos.grp_idx=groups.idx " + 
				" where proj_idx=" + mRefIdx + " order by sort_order asc");
		int maxSize = 0;
		
		while (rs.next())
		{
			int idx = rs.getInt("idx");
			int size = rs.getInt("length");
			String name = rs.getString("name");
			mRefNames.put(idx, name);
			mRefSizes.put(idx, size);
			mLayout.put(idx, new TreeMap<Integer,Vector<Block>>());
			mRefOrder.add(idx);
			maxSize = Math.max(maxSize,size);
			if (name.length() >= 6) 
			{
				chromLabelVert = true;
				chromeFont = chromFontVert;
			}
		}
		bpPerPx = maxSize/blockMaxHeight;
		if (bpPerPx<=0) bpPerPx=1; // CAS42 12/27/17 - crashing on divide by 0 below 
		
		// get the blocks in decreasing order of size.
		// as we add each one to the list we assign it an index which is just its order in the list. 
		mBlocks = new Vector<Block>();
		mBlockRects = new Vector<Rectangle>();
		
		if (!reversed)
		{
			rs = s.executeQuery("select grp1_idx as grp2, grp2_idx as grpRef, start2 as start,end2 as end from blocks where pair_idx=" + mPairIdx + 
				" order by (end2 - start2) desc");
		}
		else
		{
			rs = s.executeQuery("select grp2_idx as grp2, grp1_idx as grpRef, start1 as start,end1 as end from blocks where pair_idx=" + mPairIdx + 
			" order by (end1 - start1) desc");		
		}
		while (rs.next())
		{
			int grp2 = (unordered2 ? unGrp2 : rs.getInt("grp2"));
			int grpRef = rs.getInt("grpRef");
			int start = rs.getInt("start");
			int end = rs.getInt("end");
			
			Block b = new Block(grpRef,grp2,start/bpPerPx,end/bpPerPx);
			mBlocks.add(b);
			b.mI = mBlocks.size() - 1;			
		}
		
		// go through the blocks and make the layout
		
		for(int i = 0; i < mBlocks.size(); i++)
		{
			Block b = mBlocks.get(i);
			TreeMap<Integer,Vector<Block>> thisLayout = mLayout.get(b.mGrpRef);
			if (thisLayout==null) continue; // CAS42 12.27.17
			
			// find the first level where this block can fit
			int L;
			for (L = 1; ;L++)
			{
				if (!thisLayout.containsKey(L))
				{
					break;
				}
				else
				{
					boolean hasSpace = true;
					for (Block b1 : thisLayout.get(L))
					{
						if (b.overlaps(b1))
						{
							hasSpace = false;
							break;
						}
					}
					if (hasSpace)
					{
						break;
					}
				}
				if (!thisLayout.containsKey(-L))
				{
					L = -L;					
					break;
				}
				else
				{
					boolean hasSpace = true;
					for (Block b1 : thisLayout.get(-L))
					{
						if (b.overlaps(b1))
						{
							hasSpace = false;
							break;
						}
					}
					if (hasSpace)
					{
						L = -L;
						break;
					}
				}				
			}
			if (!thisLayout.containsKey(L))
			{
				thisLayout.put(L, new Vector<Block>());
			}
			thisLayout.get(L).add(b);
		}
		for (int g : mLayout.keySet())
		{
			TreeMap<Integer,Vector<Block>> thisLayout = mLayout.get(g);
			for (int L = 1; ; L++)
			{
				if (thisLayout.containsKey(L))
				{
					if (!thisLayout.containsKey(-L))
					{
						thisLayout.put(-L, new Vector<Block>());
					}
				}
				else if (thisLayout.containsKey(-L))
				{
					if (!thisLayout.containsKey(L))
					{
						thisLayout.put(L, new Vector<Block>());
					}					
				}
				else
				{
					break;
				}
			}
		}
	}
	void blockMouseClick(MouseEvent m)
	{
		for (int i = 0; i < mRefOrder.size(); i++)
		{
			Rectangle blockRect = mBlockRects.get(i);
			if (blockRect.contains(m.getPoint()))
			{
				int grpIdx = mRefOrder.get(i);
				Block2Frame frame = new Block2Frame(mDB, mRefIdx, mIdx2,grpIdx,mPairIdx,reversed);
				frame.setVisible(true);
				
			}
		}
	}
	
	void blockMouseMoved(MouseEvent m)
	{
		if (mBlockRects.size()==0) return; // CAS42 12/27/17
		for (int i = 0; i < mRefOrder.size(); i++)
		{
			Rectangle blockRect = mBlockRects.get(i);
			if (blockRect.contains(m.getPoint()))
			{				
				setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				return;
			}
		}
		setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));	
	}	
	void paintBlocks(Graphics g)
	{
		boolean bStoreRects = mBlockRects.isEmpty();
		g.setFont(new Font("Verdana",0,14));
		g.drawString(refName + " Chromosomes - click for detail view", 5, 15);
		if (chromLabelOffset == 0)
		{			
			g.setFont(chromeFont);
			FontMetrics fm = g.getFontMetrics(chromeFont);
			for (String name : mRefNames.values())
			{
				Rectangle2D r = fm.getStringBounds(name, g);
				chromLabelOffset = Math.max(chromLabelOffset,(int)r.getHeight());
				if (chromLabelVert)
				{
					chromXOffset = Math.max(chromXOffset, (int)r.getWidth());
				}
			}
			chromLabelOffset += 12;
		}
		int y0 = 20;
		int yA = y0 + chromLabelOffset;
		int x = 0;
		for (int i = 0; i < mRefOrder.size(); i++)
		{
			int xA = x;
			
			int refIdx = mRefOrder.get(i);
			TreeMap<Integer,Vector<Block>> thisLayout = mLayout.get(refIdx);
			int chromHeight = mRefSizes.get(refIdx)/bpPerPx;
			
			int L;
			for (L = -1;thisLayout.containsKey(L); L--);
			for (L++; L < 0; L++)
			{
				for (Block b : thisLayout.get(L))
				{
					int y1 = chromLabelOffset + b.mS + y0;
					int ht = (b.mE - b.mS);
					g.setColor(new Color(mColors.get(grpColorOrder.get(b.mGrp2))));
					g.fillRect(x, y1, layerWidth, ht);
				}
				x += layerWidth;
			}
			g.setColor(Color.black);
			g.setFont(chromeFont);
			g.drawString(mRefNames.get(refIdx),x + chromXOffset, chromLabelOffset - 10 + y0);
			g.setColor(new Color(0xE0,0xE0,0xE0));
			g.fillRect(x,chromLabelOffset + y0,chromWidth,chromHeight);
			int yB = yA + chromHeight;
			x += chromWidth;
			for (L=1; thisLayout.containsKey(L); L++)
			{
				for (Block b : thisLayout.get(L))
				{
					int y1 = chromLabelOffset + b.mS + y0;
					int ht = (b.mE - b.mS);
					g.setColor(new Color(mColors.get(grpColorOrder.get(b.mGrp2))));
					//g.drawRect(x, y1, layerWidth, y2-y1);
					g.fillRect(x, y1, layerWidth, ht);
				}
				x += layerWidth;
			}	
			int xB = x;
			if (bStoreRects) mBlockRects.add(new Rectangle(xA,yA,xB-xA,yB-yA));
			x += chromSpace;
		}
	}

	void drawChrom(JPanel pane, String name, int size, int idx, int bpPerPx)
	{
		int pxSize = size/bpPerPx;
		pane.setPreferredSize(new Dimension(pxSize,100));
		pane.setBackground(Color.white);
		JPanel chromPane = new JPanel();
		chromPane.setBackground(Color.gray);
		chromPane.setPreferredSize(new Dimension(pxSize,30));
		pane.add(chromPane);
		chromPane.setVisible(true);
	}
	void drawLegend(JPanel pane) throws Exception
	{
		grpColorOrder = new TreeMap<Integer,Integer>();
		Statement r = mDB.getConnection().createStatement();
		ResultSet rs;
		mGrps2 = new Vector<Integer>();
		mGrp2Names = new TreeMap<Integer,String>();
		
		if (unordered2)
		{
			grpColorOrder.put(unGrp2, 0);
			mGrps2.add(unGrp2);		
			mColors.add(0, 0);
			JPanel f = new JPanel();
			f.setPreferredSize( new Dimension(25, 25) );
			f.setMinimumSize( new Dimension(25, 25) );
			f.setVisible(true);
			f.setBackground(Color.black);
			pane.add(f);
			JLabel l = new JLabel("0 (unanchored) ");
			l.setFont(legendFont);
			pane.add(l);			
		}
		else
		{
			// First, do we have any unanchored blocks?
			boolean haveUnanch = false;
			rs = r.executeQuery("select count(*) as count from blocks where pair_idx=" + mPairIdx + 
					" and (grp1_idx=" + unGrp2 +  " or grp2_idx=" + unGrp2 + ")");
			rs.first();
			haveUnanch = (0 < rs.getInt("count"));
			if (haveUnanch)
			{
				grpColorOrder.put(unGrp2, 0);
				mGrps2.add(unGrp2);
				mGrp2Names.put(unGrp2, "0");
				mColors.add(0, 0);
			}
			rs = r.executeQuery("select name,idx from groups where name != '0' and proj_idx=" + mIdx2 + " order by sort_order asc");
			int i = mGrps2.size();
			while (rs.next() )
			{
				String name = rs.getString("name");
				int idx = rs.getInt("idx");
				mGrps2.add(idx);
				mGrp2Names.put(idx, name);
				grpColorOrder.put(idx,i);
				i++;	
				if (i > maxColors) i = 1;
			}
			int j=0;
			for (i = 0; i < mGrps2.size(); i++)
			{
				int idx = mGrps2.get(i);
				JPanel f = new JPanel();
				f.setPreferredSize( new Dimension(25, 25) );
				f.setMinimumSize( new Dimension(25, 25) );
				f.setVisible(true);
				f.setBackground(new Color(mColors.get(j))); 
				j++;  // CAS42 12/6/17 - let it cycle through colors, will have dups, but better than black
				if (j>=mColors.size()) j=0;
				pane.add(f);
				JLabel l = new JLabel(mGrp2Names.get(idx) + " ");
				l.setFont(legendFont);
				pane.add(l);
			}
		}
	}


	int colorInt(int R, int G, int B)
	{
		return (R<<16) + (G<<8) + B;
	}
	void getUniqueColors(int amount) 
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
	    
		for (int c = 0xE0; mColors.size() <= amount ; c += step )
		{
			c %= max;
			int R = c >> 16;
			int G = c >> 8;
			int B = c & 0xFF;
			if (Math.abs(R-G) <= 0x30 && Math.abs(R-B) <= 0x30 && Math.abs(B-G) <= 0x30)
			{
				continue; // too gray
			}
			boolean tooClose = false;
			for (int j = 0; j < mColors.size(); j++)
			{
				int c1 = mColors.get(j);
				int R1 = c1 >> 16; int G1 = c1 >> 8; int B1 = c1 & 0xFF;
				if ( Math.abs(R - R1) <= 0x30 && Math.abs(G - G1) <= 0x30 && Math.abs(B - B1) <= 0x30)
				{
					tooClose = true;
					break;
				}
				if (tooClose) continue;
			}
			mColors.add(c);
		}
	}	

	class BlockPanel extends JPanel implements MouseInputListener
	{
		private static final long serialVersionUID = 1L;
		BlockViewFrame blockFrame;
		
		public BlockPanel(BlockViewFrame _blockFrame) 
		{
			super();
			setBackground(Color.white);
			blockFrame = _blockFrame;
			addMouseListener(this);
			addMouseMotionListener(this);
		}
	    public void paintComponent(Graphics g) 
	    {
	        super.paintComponent(g); //paint background
	        blockFrame.paintBlocks(g);
	    }
	    public void mouseClicked(MouseEvent m)
	    {
	    	blockFrame.blockMouseClick(m);
	    }
	    public void mousePressed(MouseEvent m)
	    {
	    	
	    }	    
	    public void mouseEntered(MouseEvent m)
	    {
	    }	    
	    public void mouseReleased(MouseEvent m)
	    {
	    	
	    }	    
	    public void mouseExited(MouseEvent m)
	    {
	    }	
	    public void mouseDragged(MouseEvent m)
	    {
	    	
	    }	  
	    public void mouseMoved(MouseEvent m)
	    {
	    	blockFrame.blockMouseMoved(m);
	    }	  	    
	}	
	class Block
	{
		int mS; int mE;
		int mGrp2; int mGrpRef;
		int mI;
		
		public Block(int _ref, int _grp2, int _s, int _e)
		{
			mGrpRef = _ref;
			mGrp2 = _grp2;
			mS = _s;
			mE = _e;
		}
		public boolean overlaps(Block b)
		{
			return (Math.max(b.mS, mS) <= Math.min(b.mE,mE) + 30); // should match value in Block2Frame
		}
	}
}
