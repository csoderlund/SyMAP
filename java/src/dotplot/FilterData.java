package dotplot;

import java.awt.Color;

import props.PropertiesReader;
import symap.Globals;

/************************************************************
 * Filter Data; Data class contains FilterData object, which gets passed to Filter
 * CAS533 removed 'extends Observable'; moved update to Filter
 */
public class FilterData  {
	private static int DOT_LEN = 0,  DOT_PCT = 1,   DOT_NO = 2;
	private static int BLOCK_HITS = 0, ALL_HITS = 1,  MIX_HITS = 2;
	private static int GENE_IGN = 0, GENE_1 = 1, GENE_2 = 2, GENE_0=3;
	
	private int iPctid = 0, defaultPctid=0; // CAS543 changed Pctid from double to int
	private int iDotSize=1; 			 // CAS533 add; moved from controls
	
	private int nDotScale = DOT_LEN;
	private int nShowHits = ALL_HITS;
	private int nGeneHits = GENE_IGN; 
	
	private boolean bShowBlocks     = true;
	private boolean bShowBlkNum	 	= false;
	private boolean bShowEmpty      = true; // hide groups with no blocks; only when >20 chrs
	
	public FilterData() {} // Data
	
	public FilterData copy() {
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
		
	public void setFromFD(FilterData fd) {// FilterListener.cancel
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
	public void setDefaults() { // Data.resetAll; FilterListener.defaults
		iPctid 		= defaultPctid;
		iDotSize 	= 1;
		
		nDotScale	= DOT_LEN;
		nShowHits 	= ALL_HITS;
		nGeneHits 	= GENE_IGN;
		
		bShowBlocks = true;
		bShowBlkNum = false;
		bShowEmpty 	= true;
	}
	
	public void setBounds(int min, int max) { // Data
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
	
	public int	   getPctid() 		{ return iPctid; } 
	public int     getDotSize() 	{ return iDotSize; } 
	
	public boolean isPctScale()		{ return nDotScale==DOT_PCT; }
	public boolean isLenScale()		{ return nDotScale==DOT_LEN; }
	
	public boolean isShowGeneIgn() 	{ return nGeneHits==GENE_IGN;}
	public boolean isShowGene2()	{ return nGeneHits==GENE_2;}
	public boolean isShowGene1()	{ return nGeneHits==GENE_1;}
	public boolean isShowGene0()	{ return nGeneHits==GENE_0;}
	
	public boolean isShowBlockHits(){ return nShowHits == BLOCK_HITS; }
	public boolean isShowAllHits() 	{ return nShowHits == ALL_HITS; }
	public boolean isShowMixHits() 	{ return nShowHits == MIX_HITS; }
	
	public boolean isShowBlocks() 	{ return bShowBlocks; }  
	public boolean isShowBlkNum() 	{ return bShowBlkNum; }  
	public boolean isShowEmpty() 	{ return bShowEmpty; }
	
	/*******************************************************************/
	// Filter.FilterListener changes
	public boolean setPctid(int s) {
		int x = iPctid;
		iPctid = s;
		return (x!=iPctid);
	}
	public boolean setDotSize(int s) {
		int x = iDotSize;
		iDotSize = s;
		return (x!=iDotSize);
	}
	public boolean setDotScale(boolean lenScale, boolean pctScale) {
		int n = nDotScale;
		if (lenScale) 		nDotScale=DOT_LEN;
		else if (pctScale) 	nDotScale=DOT_PCT;
		else 				nDotScale=DOT_NO;
		return (n!=nDotScale);
	}
	public boolean setShowHits(boolean onlyBlock, boolean all) {
		int x = nShowHits;
		if (onlyBlock) 	nShowHits = BLOCK_HITS;
		else if (all)  	nShowHits = ALL_HITS;
		else 			nShowHits = MIX_HITS;
		return (x!=nShowHits);
	}
	public boolean setGeneHits(boolean gene2, boolean gene1, boolean gene0) {
		int x = nGeneHits;
		if (gene2) 		nGeneHits = GENE_2;
		else if (gene1) nGeneHits = GENE_1;
		else if (gene0) nGeneHits = GENE_0;
		else 			nGeneHits = GENE_IGN;
		return (x!=nGeneHits);
	}
	public boolean setShowBlocks(boolean showBlocks) {
		boolean b = bShowBlocks;
		bShowBlocks = showBlocks;
		return (b!=bShowBlocks);
	}
	public boolean setShowBlkNum(boolean showBlkNum) {
		boolean b = bShowBlkNum;
		bShowBlkNum = showBlkNum;
		return (b!=bShowBlkNum);
	}
	public boolean setShowEmpty(boolean showEmpty) {
		boolean b = bShowEmpty;
		bShowEmpty = showEmpty;
		return (b!=bShowEmpty);
	}
	public Color getRectColor() {return blockRectColor;}
	public Color getColor(DPHit hd) {
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
	public static Color blockColor;
	public static Color nonBlockColor;
	public static Color blockRectColor; 
	public static Color blockGeneBothColor;
	public static Color blockGeneOneColor;
	public static Color geneBothColor; 				
	public static Color geneOneColor;
	
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
