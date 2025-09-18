package dotplot;

import java.awt.Color;

import props.PropertiesReader;
import symap.Globals;

/************************************************************
 * Filter Data; Data class contains FilterData object, which gets passed to Filter
 */
public class FilterData  {
	private static int DOT_LEN = 0,  DOT_PCT = 1,   DOT_NO = 2;
	private static int BLOCK_HITS = 0, ALL_HITS = 1,  MIX_HITS = 2;
	private static int GENE_IGN = 0, GENE_1 = 1, GENE_2 = 2, GENE_0=3;
	
	// If change default, change Filter.setEnabled
	private static int dDotScale = DOT_LEN, dShowHits = ALL_HITS, dGeneHits = GENE_IGN;
	
	private int iPctid = 0, defaultPctid=0; 
	private int iDotSize=1; 			
	
	private int nDotScale = dDotScale; 		
	private int nShowHits = dShowHits;
	private int nGeneHits = dGeneHits; 
	
	private boolean bShowBlocks     = true;
	private boolean bShowBlkNum	 	= false;
	private boolean bShowEmpty      = true; // hide groups with no blocks; only when >20 chrs
	
	protected FilterData() {} // Data
	
	protected FilterData copy() {
		FilterData fd =  new FilterData();
		fd.defaultPctid = defaultPctid;
		fd.iPctid 		= iPctid;
		fd.iDotSize 	= iDotSize;
		
		fd.nDotScale	= nDotScale;
		fd.nShowHits 	= nShowHits;
		fd.nGeneHits	= nGeneHits;
		
		fd.bShowBlocks 	= bShowBlocks;
		fd.bShowBlkNum	= bShowBlkNum;
		fd.bShowEmpty 	= bShowEmpty;
		return fd;
	}
		
	protected void setFromFD(FilterData fd) {// FilterListener.cancel
		defaultPctid 	= fd.defaultPctid;
		iPctid 			= fd.iPctid;
		iDotSize 		= fd.iDotSize;
		
		nDotScale		= fd.nDotScale;
		nShowHits 		= fd.nShowHits;
		nGeneHits 		= fd.nGeneHits;
		
		bShowBlocks 	= fd.bShowBlocks;
		bShowBlkNum		= fd.bShowBlkNum;
		bShowEmpty 		= fd.bShowEmpty;
	}
	protected void setDefaults() { // Data.resetAll; FilterListener.defaults
		iPctid 		= defaultPctid;
		iDotSize 	= 1;
		
		nDotScale	= dDotScale;		
		nShowHits 	= dShowHits;
		nGeneHits 	= dGeneHits;
		
		bShowBlocks = true;
		bShowBlkNum = false;
		bShowEmpty 	= true;
	}
	
	protected void setBounds(int min, int max) { // Data
		iPctid = defaultPctid = min; 
	}
	public boolean equals(Object obj) {
		if (obj instanceof FilterData) {
			FilterData fd = (FilterData)obj;
			
			return fd.iPctid==iPctid && fd.iDotSize==iDotSize 
				&& fd.nDotScale==nDotScale && fd.nShowHits==nShowHits && fd.nGeneHits==nGeneHits
				&& fd.bShowBlocks == bShowBlocks && fd.bShowBlkNum == bShowBlkNum
				&& fd.bShowEmpty == bShowEmpty;
		}
		return false;
	}
	
	protected int	  getPctid() 	{ return iPctid; } 
	protected int     getDotSize() 	{ return iDotSize; } 
	
	protected boolean isPctScale()	{ return nDotScale==DOT_PCT; }
	protected boolean isLenScale()	{ return nDotScale==DOT_LEN; }
	protected boolean isNoScale()	{ return nDotScale==DOT_NO; }
	
	protected boolean isShowGeneIgn() 	{ return nGeneHits==GENE_IGN;}
	protected boolean isShowGene2()	{ return nGeneHits==GENE_2;}
	protected boolean isShowGene1()	{ return nGeneHits==GENE_1;}
	protected boolean isShowGene0()	{ return nGeneHits==GENE_0;}
	
	protected boolean isShowBlockHits(){ return nShowHits == BLOCK_HITS; }
	protected boolean isShowAllHits()  { return nShowHits == ALL_HITS; }
	protected boolean isShowMixHits()  { return nShowHits == MIX_HITS; }
	
	protected boolean isShowBlocks() { return bShowBlocks; }  
	protected boolean isShowBlkNum() { return bShowBlkNum; }  
	protected boolean isShowEmpty() { return bShowEmpty; }
	
	/*******************************************************************/
	// Filter.FilterListener changes
	protected boolean setPctid(int s) {
		int x = iPctid;
		iPctid = s;
		return (x!=iPctid);
	}
	protected boolean setDotSize(int s) {
		int x = iDotSize;
		iDotSize = s;
		return (x!=iDotSize);
	}
	protected boolean setDotScale(boolean lenScale, boolean pctScale) {
		int n = nDotScale;
		if (lenScale) 		nDotScale=DOT_LEN;
		else if (pctScale) 	nDotScale=DOT_PCT;
		else 				nDotScale=DOT_NO;
		return (n!=nDotScale);
	}
	protected boolean setShowHits(boolean onlyBlock, boolean all) {
		int x = nShowHits;
		if (onlyBlock) 	nShowHits = BLOCK_HITS;
		else if (all)  	nShowHits = ALL_HITS;
		else 			nShowHits = MIX_HITS;
		return (x!=nShowHits);
	}
	protected boolean setGeneHits(boolean gene2, boolean gene1, boolean gene0) {
		int x = nGeneHits;
		if (gene2) 		nGeneHits = GENE_2;
		else if (gene1) nGeneHits = GENE_1;
		else if (gene0) nGeneHits = GENE_0;
		else 			nGeneHits = GENE_IGN;
		return (x!=nGeneHits);
	}
	protected boolean setShowBlocks(boolean showBlocks) {
		boolean b = bShowBlocks;
		bShowBlocks = showBlocks;
		return (b!=bShowBlocks);
	}
	protected boolean setShowBlkNum(boolean showBlkNum) {
		boolean b = bShowBlkNum;
		bShowBlkNum = showBlkNum;
		return (b!=bShowBlkNum);
	}
	protected boolean setShowEmpty(boolean showEmpty) {
		boolean b = bShowEmpty;
		bShowEmpty = showEmpty;
		return (b!=bShowEmpty);
	}
	protected Color getRectColor() {return blockRectColor;}
	protected Color getColor(DPHit hd) {
		if (hd.isBlock()) {
			if (hd.bothGene()) return blockGeneBothColor;
			if (hd.oneGene()) return blockGeneOneColor;
			return blockColor;
		}
		if (hd.bothGene()) return geneBothColor;
		if (hd.oneGene()) return geneOneColor;
		return nonBlockColor;
	}
	
	// accessed and changed by ColorDialog - do not change
	protected static Color blockColor;
	protected static Color nonBlockColor;
	protected static Color blockRectColor; 
	protected static Color blockGeneBothColor;
	protected static Color blockGeneOneColor;
	protected static Color geneBothColor; 				
	protected static Color geneOneColor;
	
	static {
		PropertiesReader props = new PropertiesReader(Globals.class.getResource("/properties/dotplot.properties"));
	
		blockColor 			= props.getColor("blockColor");
		nonBlockColor 		= props.getColor("nonBlockColor");
		blockRectColor 		= props.getColor("blockRectColor"); 
		blockGeneBothColor 	= props.getColor("blockGeneBothColor");
		blockGeneOneColor 	= props.getColor("blockGeneOneColor");
		geneBothColor 		= props.getColor("geneBothColor");
		geneOneColor 		= props.getColor("geneOneColor"); 
	}
}
