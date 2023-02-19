package blockview;

import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.Vector;
import java.util.TreeMap;
import java.util.HashSet;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import javax.swing.event.*;

import database.DBconn;

import javax.swing.*;

import symap.Globals;
import symap.manager.Mproject;
import util.ImageViewer;
import util.ErrorReport;

/************************************************
 * Draws the blocks for a synteny pair
 */
// CAS42 12/27/17 Seq-FPC has crashes from multiple problem. 
// They are fixed, but Seq-FPC reverse leaves a blank page of blocks
// CAS515 rearranged, made a little smaller
// CAS516 add refType, fonts for upper text, scroll works for many scaffolds, skip when no blocks
public class BlockViewFrame extends JFrame{
	private static final long serialVersionUID = 1L;
	public static final int MAX_GRPS =  150; // CAS42 12/6/17 - if #seqs>MAX_GRPS, blocks does not work right
	public static final int maxColors = 100;
	private final int fChromWidth = 8;
	private final int fLayerWidth = 8;
	private final int fChromSpace = 25;
	private final int fBlockMaxHeight = 700;
	
	private int mRefIdx, mIdx2;
	private String refName, refType, name2, type2;
	private DBconn mDB;
	private Vector<Integer> mColors;
	
	private TreeMap<Integer,TreeMap<Integer,Vector<Block>>> mLayout;
	private TreeMap<Integer,String> mRefNames, mGrp2Names;
	private TreeMap<Integer,Integer> mRefSizes;
	private Vector<Integer> mRefOrder;
	private Vector<Rectangle> mBlockRects;
	private TreeMap<Integer,Integer> grpColorOrder;
	private Vector<Integer> mGrps2;	
	private Vector<Block> mBlocks;
	private JButton saveBtn;
	
	private boolean reversed = false;
	private int mPairIdx;
	
	private int bpPerPx = 10000;
	private int chromLabelOffset = 0;
	private int chromXOffset = 0;
	private Font chromeFont, legendFont, chromFontVert;
	private boolean bChromLabelVert = false;
	private boolean bGtMaxGrpsRef = false, bGtMaxGrps2 = false;
	private int unGrp2 = 0; // idx of the unanchored group, "0"
	private JPanel mainPane = null;
	private JScrollPane scroller = null;

	public BlockViewFrame(DBconn dbReader, int projXIdx, int projYIdx) throws Exception {
		super("SyMAP Block View " + Globals.VERSION);
		mRefIdx = projXIdx;
		mIdx2 = projYIdx;
		mDB = dbReader;
		init();
	}
	private void init() {		
		try {
			if (!initFromDB()) return;
			if (!layoutBlocks()) return;
			
			getUniqueColors(maxColors);
			chromeFont = new Font("Verdana",0,14);
			legendFont = new Font("Verdana",0,12);
			AffineTransform rot90 = new AffineTransform(0,-1,1,0,0,0);
			chromFontVert = chromeFont.deriveFont(rot90);
			
			this.getContentPane().removeAll();
			setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			
			mainPane = new JPanel();
			mainPane.setBackground(Color.white);
			mainPane.setLayout(new GridBagLayout()); 
			scroller = new JScrollPane(mainPane);
						
			GridBagConstraints cPane = new GridBagConstraints();
			cPane.gridx = cPane.gridy = 0;	cPane.fill = GridBagConstraints.CENTER;
			
		// Top row
			JPanel topRow = new JPanel();
			topRow.setBackground(Color.white);
			topRow.setLayout(new GridBagLayout());	
			
			GridBagConstraints cRow = new GridBagConstraints();
			cRow.gridx = cRow.gridy = 0; cRow.fill = GridBagConstraints.SOUTHEAST;
			
			JLabel title = new JLabel(name2 + " synteny to " + refName); 
			title.setFont(new Font("Verdana",Font.BOLD,18));
			topRow.add(title,cRow);
			cRow.gridx++; 	
			topRow.add(new JLabel("          "),cRow);
			
			if (!bGtMaxGrps2 ) {
				cRow.gridx++;	cRow.fill = GridBagConstraints.SOUTHEAST;
				JLabel revLabel = new JLabel("<html><u>reverse</u>");
				revLabel.addMouseListener(reverseClick);
				topRow.add(revLabel,cRow);
			}
			else { // CAS42 12/6/17 - there was NO message if this happened
				System.out.println("Warning: one of the genomes has >" + MAX_GRPS + " sequences");
				System.out.println("   this causes the 'reverse' option to not work on the block view");
				System.out.println("   use scripts/lenFasta.pl to find cutoff length for Minimum Length.");
				System.out.println("   email symap@agcol.arizona.edu if you would like further instructions");
			}
			cRow.gridx++;
			topRow.add(new JLabel("      "),cRow);
			
			Icon icon = ImageViewer.getImageIcon("/images/print.gif"); 
			saveBtn =  new JButton(icon);
			saveBtn.setBackground(Color.white);
			saveBtn.setBorder(null);
			saveBtn.setToolTipText("Save Image");
			saveBtn.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					ImageViewer.showImage("Blocks", mainPane);
				}
			});
			cRow.gridx++;
			topRow.add(saveBtn,cRow);
			
			cPane.gridy++; 
			mainPane.add(topRow,cPane);
			
		// Legend
		    JPanel keyRow = new JPanel();
			keyRow.setBackground(Color.white);
			JLabel key = new JLabel(name2 + " " + type2 + " color key"); 
			key.setFont(new Font("Verdana",0,12));
			keyRow.add(key);
			cPane.gridy++; 
			mainPane.add(keyRow,cPane);
			
			JPanel legendPane = new JPanel();
			legendPane.setBackground(Color.white);
			drawLegend(legendPane);
			cPane.gridy++; 
			mainPane.add(legendPane,cPane);
			
		// Click for detail
			JPanel selectRow = new JPanel();
			selectRow.setBackground(Color.white);
			JLabel select = new JLabel(refName + " " + refType + " - click for detail view"); 
			select.setFont(new Font("Verdana",0,12));
			selectRow.add(select);
			cPane.gridy++; 
			mainPane.add(selectRow,cPane);
		
		// blocks
			BlockPanel blockPanel = new BlockPanel(this);
			blockPanel.setVisible(true);
			cPane.gridy++;
			mainPane.add(blockPanel,cPane);
			
			// CAS515 Dimension d = new Dimension(blockWindowWidth + 100, blockMaxHeight + chromLabelOffset + 250)
			int nLevels = 0;
			for (int grp : mLayout.keySet()) nLevels += mLayout.get(grp).size();			
			int totalWinWidth = (mLayout.size()*(fChromWidth + fChromSpace)) + (nLevels*fLayerWidth); // used by legend
			int d1winWidth =  totalWinWidth;
			if (d1winWidth>1000)     d1winWidth=1000;
			else if (d1winWidth<400) d1winWidth=400;	
			int height = fBlockMaxHeight + chromLabelOffset + 100;
			
			Dimension d1 = new Dimension(d1winWidth, height);
			setPreferredSize(d1); 
			setMinimumSize(d1); // without this, panel is not all shown, but can be resized
				
			Dimension d2 = new Dimension(totalWinWidth + 20, height + 20);
			blockPanel.setPreferredSize(d2);  blockPanel.setMinimumSize(d2);
			
			this.setLocationRelativeTo(null);
			this.getContentPane().add(scroller);
			this.validate();
			this.repaint();
		}
		catch(Exception e){ErrorReport.print(e, "Initializing panel for blocks");}
	}

	private boolean initFromDB() {
		try {
			ResultSet rs;
			Statement s = mDB.getConnection().createStatement();
	
			rs = s.executeQuery("select count(*) as ngrps from xgroups where proj_idx=" + mRefIdx);
			rs.first();
			bGtMaxGrpsRef = (rs.getInt("ngrps") > MAX_GRPS);

			rs = s.executeQuery("select count(*) as ngrps from xgroups where proj_idx=" + mIdx2);
			rs.first();
			bGtMaxGrps2 = (rs.getInt("ngrps") > MAX_GRPS);
			
			if (bGtMaxGrpsRef && bGtMaxGrps2) {
				System.out.println("Genomes have too many chromosomes/contigs to show in block view");
				return false;
			}
			if (bGtMaxGrpsRef) {
				int tmp = mRefIdx;
				mRefIdx = mIdx2;
				mIdx2 = tmp;
				bGtMaxGrps2 = true;
				bGtMaxGrpsRef = false;
			}
			reversed = false;
			rs = s.executeQuery("select idx from pairs where proj1_idx=" + mIdx2 + " and proj2_idx=" + mRefIdx);
			if (rs.first()) {
				mPairIdx = rs.getInt("idx");
			}
			else{
				rs = s.executeQuery("select idx from pairs where proj2_idx=" + mIdx2 + " and proj1_idx=" + mRefIdx);
				if (rs.first()){
					mPairIdx = rs.getInt("idx");
					reversed = true;
				}
				else{
					System.out.println("Unable to find project pair");
					return false;
				}
			}
			rs = s.executeQuery("select idx from xgroups where name='0' and proj_idx=" + mIdx2);
			if (rs.first()){
				unGrp2 = rs.getInt("idx");
			}
			
			Mproject tProj = new Mproject();
			String display_name = tProj.getKey(tProj.sDisplay);
			String grp_type = tProj.getKey(tProj.sGrpType);
			
			rs = s.executeQuery("select value from proj_props where name='"+display_name+"' and proj_idx=" + mRefIdx);
			rs.first();
			refName = rs.getString("value");
			rs = s.executeQuery("select value from proj_props where name='"+ grp_type +"' and proj_idx=" + mRefIdx);
			rs.first();
			refType = rs.first() ? rs.getString("value") : "Chromosomes"; // CAS534 should be loaded, but if not...
			
			rs = s.executeQuery("select value from proj_props where name='"+ display_name+"' and proj_idx=" + mIdx2);
			rs.first();
			name2 = rs.getString("value");
			rs = s.executeQuery("select value from proj_props where name='"+grp_type+"' and proj_idx=" + mIdx2);
			type2 = rs.first() ? rs.getString("value") : "Chromosomes";
			
			rs.close();
			return true;
		}
		catch(Exception e){ErrorReport.print(e, "Init from DB"); return false;}
	}
	private MouseListener reverseClick = new MouseAdapter() 
	{
		public void mouseEntered(MouseEvent e) {
			Cursor cur = new Cursor(Cursor.HAND_CURSOR);
			setCursor(cur);
		}
		public void mouseExited(MouseEvent e) {
			Cursor cur = new Cursor(Cursor.DEFAULT_CURSOR);
			setCursor(cur);
		}		
		public void mouseClicked(MouseEvent e) {
			reverseView();
		}
	};	
	private void reverseView() {
		int tmp = mIdx2;
		mIdx2 = mRefIdx;
		mRefIdx = tmp;
		chromLabelOffset = 0; // CAS516
		init();
	}
	private boolean layoutBlocks() {
	try {
		ResultSet rs;
		Statement s = mDB.getConnection().createStatement();

		HashSet <Integer> chrBlocks = new HashSet <Integer> (); // CAS516 add 
		String sql1="";
		if (!reversed) sql1 = "select grp2_idx from blocks where pair_idx=" + mPairIdx;
		else 		   sql1 = "select grp1_idx from blocks where pair_idx=" + mPairIdx;
		rs = s.executeQuery(sql1);
		while (rs.next()) {
			int idx = rs.getInt(1);
			if (!chrBlocks.contains(idx)) chrBlocks.add(idx);
		}
		
		mRefNames = new TreeMap<Integer,String>();
		mRefSizes = new TreeMap<Integer,Integer>();
		mRefOrder = new Vector<Integer>();
		mLayout = new TreeMap<Integer,TreeMap<Integer,Vector<Block>>>(); //xgroups.idx, <level, Blocks>
		int maxChrLen = 0;
		
		// Get chr names and sizes
		rs = s.executeQuery("select idx,name,length from xgroups " +
				" join pseudos on pseudos.grp_idx=xgroups.idx " + 
				" where proj_idx=" + mRefIdx + " order by sort_order asc");
		while (rs.next()){
			int idx = rs.getInt("idx");
			if (!chrBlocks.contains(idx)) continue;
			
			int chrLen = rs.getInt("length");
			String name = rs.getString("name");
			mRefNames.put(idx, name); // chromosome number
			mRefSizes.put(idx, chrLen);
			mLayout.put(idx, new TreeMap<Integer,Vector<Block>>());
			mRefOrder.add(idx);
			maxChrLen = Math.max(maxChrLen,chrLen);
			if (name.length() >= 6) {
				bChromLabelVert = true;
				chromeFont = chromFontVert;
			}
		}
		rs.close();
		
		bpPerPx = maxChrLen/fBlockMaxHeight;
		if (bpPerPx<=0) bpPerPx=1; // CAS42 12/27/17 - crashing on divide by 0 below 
		
		// get the blocks in decreasing order of size.
		// as we add each one to the list we assign it an index which is just its order in the list. 
		mBlocks = new Vector<Block>();
		mBlockRects = new Vector<Rectangle>();
		
		String sql="";
		if (!reversed) {
			sql = "select grp1_idx as grp2, grp2_idx as grpRef, start2 as start,end2 as end from blocks "
				+ " where pair_idx=" + mPairIdx + " order by (end2 - start2) desc";
		}
		else {
			sql = "select grp2_idx as grp2, grp1_idx as grpRef, start1 as start,end1 as end from blocks "
				+ " where pair_idx=" + mPairIdx + " order by (end1 - start1) desc";		
		}
		rs = s.executeQuery(sql);
		while (rs.next()) {
			int grp2 = (bGtMaxGrps2 ? unGrp2 : rs.getInt("grp2"));
			int grpRef = rs.getInt("grpRef");
			int start = rs.getInt("start");
			int end = rs.getInt("end");
			
			Block b = new Block(grpRef,grp2,start/bpPerPx,end/bpPerPx);
			mBlocks.add(b);
			b.mI = mBlocks.size() - 1;			
		}
		rs.close(); 
			
		// go through the blocks and make the layout
		for(int i = 0; i < mBlocks.size(); i++){
			Block b = mBlocks.get(i);
			TreeMap<Integer,Vector<Block>> chrLayout = mLayout.get(b.mGrpRef);
			if (chrLayout==null) continue; // CAS42 12.27.17
			
			// find the first level where this block can fit
			int L;
			for (L = 1; ;L++) {
				if (!chrLayout.containsKey(L)){
					break;
				}
				else {
					boolean hasSpace = true;
					for (Block b1 : chrLayout.get(L)) {
						if (b.overlaps(b1)){
							hasSpace = false;
							break;
						}
					}
					if (hasSpace) {
						break;
					}
				}
				if (!chrLayout.containsKey(-L)){
					L = -L;					
					break;
				}
				else {
					boolean hasSpace = true;
					for (Block b1 : chrLayout.get(-L)) {
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
			if (!chrLayout.containsKey(L)) {
				chrLayout.put(L, new Vector<Block>());
			}
			chrLayout.get(L).add(b);
		}
		// CAS516 - removed code to add mirrored level on opposite where they do not exist
		return true;
	}
	catch (Exception e) {ErrorReport.print(e, "Block layout");; return false;}
	}
	private void blockMouseClick(MouseEvent m)
	{
		for (int i = 0; i < mRefOrder.size(); i++) {
			Rectangle blockRect = mBlockRects.get(i);
			if (blockRect.contains(m.getPoint())) {
				int grpIdx = mRefOrder.get(i);
				Block2Frame frame = new Block2Frame(mDB, mRefIdx, mIdx2,grpIdx,mPairIdx,reversed);
				frame.setVisible(true);
				
			}
		}
	}
	private void blockMouseMoved(MouseEvent m) {
		if (mBlockRects.size()==0) return; // CAS42 12/27/17
		for (int i = 0; i < mRefOrder.size(); i++) {
			Rectangle blockRect = mBlockRects.get(i);
			if (blockRect.contains(m.getPoint())) {				
				setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				return;
			}
		}
		setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));	
	}	
	private void paintBlocks(Graphics g){
		boolean bStoreRects = mBlockRects.isEmpty();
		
		if (chromLabelOffset == 0) {			
			g.setFont(chromeFont);
			FontMetrics fm = g.getFontMetrics(chromeFont);
			
			for (String name : mRefNames.values()) {
				Rectangle2D r = fm.getStringBounds(name, g);
				chromLabelOffset = Math.max(chromLabelOffset,(int)r.getHeight());
				if (bChromLabelVert) {
					chromXOffset = Math.max(chromXOffset, (int)r.getWidth());
				}
			}
			chromLabelOffset += 12;
		}
	
		int y0 = 20;
		int yA = y0 + chromLabelOffset;
		int x = 10; 
		for (int i = 0; i < mRefOrder.size(); i++) {
			int xA = x;
			
			int refIdx = mRefOrder.get(i);
			TreeMap<Integer,Vector<Block>> chrLayout = mLayout.get(refIdx);
			int chromHeight = mRefSizes.get(refIdx)/bpPerPx;
			
			int L;
			for (L = -1;chrLayout.containsKey(L); L--);
			for (L++; L < 0; L++) {
				for (Block b : chrLayout.get(L)) {
					int y1 = chromLabelOffset + b.mS + y0;
					int ht = (b.mE - b.mS);
					g.setColor(new Color(mColors.get(grpColorOrder.get(b.mGrp2))));
					g.fillRect(x, y1, fLayerWidth, ht);
				}
				x += fLayerWidth;
			}
			g.setColor(Color.black);
			g.setFont(chromeFont);
			g.drawString(mRefNames.get(refIdx),x + chromXOffset, chromLabelOffset - 10 + y0);
			
			// g.setColor(new Color(0xE0,0xE0,0xE0)); // CAS505 was showing white on linux
			g.setColor(Color.lightGray); // CAS505
			g.fillRect(x,chromLabelOffset + y0, fChromWidth,chromHeight);
			int yB = yA + chromHeight;
			x += fChromWidth;
			for (L=1; chrLayout.containsKey(L); L++) {
				for (Block b : chrLayout.get(L)) {
					int y1 = chromLabelOffset + b.mS + y0;
					int ht = (b.mE - b.mS);
					g.setColor(new Color(mColors.get(grpColorOrder.get(b.mGrp2))));
					g.fillRect(x, y1, fLayerWidth, ht);
				}
				x += fLayerWidth;
			}	
			int xB = x;
			if (bStoreRects) mBlockRects.add(new Rectangle(xA,yA,xB-xA,yB-yA));
			x += fChromSpace;
		}
	}
	private void drawLegend(JPanel pane) throws Exception {
		grpColorOrder = new TreeMap<Integer,Integer>();
		Statement r = mDB.getConnection().createStatement();
		ResultSet rs;
		mGrps2 = new Vector<Integer>();
		mGrp2Names = new TreeMap<Integer,String>();
		Dimension d = new Dimension(25, 25);
		
		if (bGtMaxGrps2) {
			grpColorOrder.put(unGrp2, 0);
			mGrps2.add(unGrp2);		
			mColors.add(0, 0);
			JPanel f = new JPanel();
			f.setPreferredSize(d); f.setMinimumSize(d);
			f.setVisible(true);
			f.setBackground(Color.black);
			pane.add(f);
			JLabel l = new JLabel("0 (unordered) ");
			l.setFont(legendFont);
			pane.add(l);	
			return;
		}
	    // Proceed with drawing	
		// unanchored blocks have grp_idx=0 (unGrp2=0)
		rs = r.executeQuery("select count(*) as count from blocks where pair_idx=" + mPairIdx + 
				" and (grp1_idx=" + unGrp2 +  " or grp2_idx=" + unGrp2 + ")");
		rs.first();
		if (rs.getInt("count") > 0) {
			grpColorOrder.put(unGrp2, 0);
			mGrps2.add(unGrp2);
			mGrp2Names.put(unGrp2, "0");
			mColors.add(0, 0);
		}
		// get all chromosomes for second species
		rs = r.executeQuery("select name,idx from xgroups where name != '0' and proj_idx=" + mIdx2 + 
				" order by sort_order asc"); // sort_order is by name unless 'ordered_against'
		int i = mGrps2.size();
		while (rs.next() ) {
			String name = rs.getString("name");
			int idx = rs.getInt("idx");
			mGrps2.add(idx);
			mGrp2Names.put(idx, name);
			grpColorOrder.put(idx,i);
			i++;	
			if (i > maxColors) i = 1;
		}
		rs.close();
		
		int j=0;
		for (i = 0; i < mGrps2.size(); i++) {
			int idx = mGrps2.get(i);
			JPanel f = new JPanel();
			f.setPreferredSize(d); f.setMinimumSize(d);
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
	private int colorInt(int R, int G, int B) {
		return (R<<16) + (G<<8) + B;
	}
	private void getUniqueColors(int amount)  {
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
	    
		for (int c = 0xE0; mColors.size() <= amount ; c += step ){
			c %= max;
			int R = c >> 16;
			int G = c >> 8;
			int B = c & 0xFF;
			if (Math.abs(R-G) <= 0x30 && Math.abs(R-B) <= 0x30 && Math.abs(B-G) <= 0x30) {
				continue; // too gray
			}
			boolean tooClose = false;
			for (int j = 0; j < mColors.size(); j++){
				int c1 = mColors.get(j);
				int R1 = c1 >> 16; int G1 = c1 >> 8; int B1 = c1 & 0xFF;
				if ( Math.abs(R - R1) <= 0x30 && Math.abs(G - G1) <= 0x30 && Math.abs(B - B1) <= 0x30){
					tooClose = true;
					break;
				}
				if (tooClose) continue;
			}
			mColors.add(c);
		}
	}	

	class BlockPanel extends JPanel implements MouseInputListener {
		private static final long serialVersionUID = 1L;
		BlockViewFrame blockFrame;
		
		public BlockPanel(BlockViewFrame _blockFrame) {
			super();
			setBackground(Color.white);
			blockFrame = _blockFrame;
			addMouseListener(this);
			addMouseMotionListener(this);
		}
	    public void paintComponent(Graphics g)  {
	        super.paintComponent(g); //paint background
	        blockFrame.paintBlocks(g);
	    }
	    public void mouseClicked(MouseEvent m){
	    	blockFrame.blockMouseClick(m);
	    }
	    public void mousePressed(MouseEvent m){    	
	    }	    
	    public void mouseEntered(MouseEvent m){
	    }	    
	    public void mouseReleased(MouseEvent m){
	    }	    
	    public void mouseExited(MouseEvent m) {
	    }	
	    public void mouseDragged(MouseEvent m) {
	    }	  
	    public void mouseMoved(MouseEvent m) {
	    	blockFrame.blockMouseMoved(m);
	    }	  	    
	}	
	class Block {
		int mS; int mE;
		int mGrp2; int mGrpRef;
		int mI;
		
		public Block(int _ref, int _grp2, int _s, int _e) {
			mGrpRef = _ref;
			mGrp2 = _grp2;
			mS = _s;
			mE = _e;
		}
		public boolean overlaps(Block b){
			return (Math.max(b.mS, mS) <= Math.min(b.mE,mE) + 30); // should match value in Block2Frame
		}
	}
}
