package dotplot;

/************************************************************
 * Filter Data; Data class contains FilterData object, which gets passed to Filter
 * CAS533 removed 'extends Observable'; moved update to Filter
 */
public class FilterData  {
	private static int BLOCK_HITS = 0, ALL_HITS = 1,  MIX_HITS = 2;
	private static int HIGH_BLACK = 0, HIGH_BLUE = 1, HIGH_GREEN = 2;
	private static int DOT_LEN = 0,    DOT_PCT = 1,   DOT_NO = 2;
	
	private double dPctid = 0, defaultPctid=0;
	private int iDotSize=1; 			 // CAS533 add; moved from controls
	
	private int nShowHits = ALL_HITS;
	private int nHighHits = HIGH_BLACK; 
	private int nDotScale = DOT_LEN;
	
	private boolean bShowBlocks     = true;
	private boolean bShowBlkNum	 	= false;
	private boolean bShowEmpty      = true; // hide groups with no blocks
	
	public FilterData() {} // Data
	
	public FilterData copy() {
		FilterData fd =  new FilterData();
		fd.defaultPctid = defaultPctid;
		fd.dPctid 		= dPctid;
		fd.iDotSize 	= iDotSize;
		
		fd.nDotScale	= nDotScale;
		fd.nShowHits 	= nShowHits;
		fd.nHighHits	= nHighHits;
		
		fd.bShowBlocks 	= bShowBlocks;
		fd.bShowBlkNum	= bShowBlkNum;
		fd.bShowEmpty 	= bShowEmpty;
		return fd;
	}
		
	public void setFromFD(FilterData fd) {// FilterListener.cancel
		defaultPctid 	= fd.defaultPctid;
		dPctid 			= fd.dPctid;
		iDotSize 		= fd.iDotSize;
		
		nDotScale		= fd.nDotScale;
		nShowHits 		= fd.nShowHits;
		nHighHits 		= fd.nHighHits;
		
		bShowBlocks 	= fd.bShowBlocks;
		bShowBlkNum		= fd.bShowBlkNum;
		bShowEmpty 		= fd.bShowEmpty;
	}
	public void setDefaults() { // Data.resetAll; FilterListener.defaults
		dPctid 		= defaultPctid;
		iDotSize 	= 1;
		
		nDotScale	= DOT_LEN;
		nShowHits 	= ALL_HITS;
		nHighHits 	= HIGH_BLACK;
		
		bShowBlocks = true;
		bShowBlkNum = false;
		bShowEmpty 	= true;
	}
	
	public void setBounds(double min, double max) { // Data
		dPctid = defaultPctid = min; 
	}
	public boolean equals(Object obj) {
		if (obj instanceof FilterData) {
			FilterData fd = (FilterData)obj;
			
			return fd.dPctid==dPctid && fd.iDotSize==iDotSize 
				&& fd.nDotScale==nDotScale && fd.nShowHits==nShowHits && fd.nHighHits==nHighHits
				&& fd.bShowBlocks == bShowBlocks && fd.bShowBlkNum == bShowBlkNum
				&& fd.bShowEmpty == bShowEmpty;
		}
		return false;
	}
	
	public double  getPctid() 				{ return dPctid; } 
	public int     getDotSize() 			{ return iDotSize; } 
	
	public boolean isPctScale()				{ return nDotScale==DOT_PCT; }
	public boolean isLenScale()				{ return nDotScale==DOT_LEN; }
	
	public boolean isHighBlockHits() 		{ return nHighHits!=HIGH_BLACK;}
	public boolean isHighGreen()			{ return nHighHits==HIGH_GREEN;}
	public boolean isHighBlue()				{ return nHighHits==HIGH_BLUE;}
	
	public boolean isShowBlockHits() 		{ return nShowHits == BLOCK_HITS; }
	public boolean isShowAllHits() 			{ return nShowHits == ALL_HITS; }
	public boolean isShowMixHits() 			{ return nShowHits == MIX_HITS; }
	
	public boolean isShowBlocks() 			{ return bShowBlocks; }  
	public boolean isShowBlkNum() 			{ return bShowBlkNum; }  
	public boolean isShowEmpty() 			{ return bShowEmpty; }
	
	/*******************************************************************/
	// Filter.FilterListener changes
	public boolean setPctid(double s) {
		double x = dPctid;
		dPctid = s;
		return (x!=dPctid);
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
	public boolean setHighHits(boolean blue, boolean green) {
		int x = nHighHits;
		if (green) 		nHighHits = HIGH_GREEN;
		else if (blue)  nHighHits = HIGH_BLUE;
		else 			nHighHits = HIGH_BLACK;
		return (x!=nShowHits);
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
}
