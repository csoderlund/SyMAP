package dotplot;

import java.util.Observable;

public class FilterData extends Observable implements DotPlotConstants {
	private double pctid = ANY_PCTID, minPctid=0;
	
	private int showHits = ALL_HITS; // 0 show all, 1 show block
	private boolean bHighBlockHits = false;
	private boolean bShowBlocks    = true; 
	private boolean bShowEmpty     = true; // hide groups with no blocks
	
	public FilterData() {}

	public FilterData(FilterData fd) {
		this();
		set(fd);
	}

	public void setDefaults() {
		if (!equals(new FilterData())) {
			pctid=minPctid;
			showHits = ALL_HITS;
			
			bHighBlockHits=false;
			bShowBlocks = true;
			bShowEmpty = true;
			update();
		}
	}

	public void set(FilterData fd) {
		if (!equals(fd)) {
			pctid=fd.minPctid;
			showHits = fd.showHits;
			
			bHighBlockHits = fd.bHighBlockHits;
			bShowBlocks = fd.bShowBlocks;
			bShowEmpty = fd.bShowEmpty;
			
			update();
		}
	}
	public void setBounds(double min, double max) { 
		pctid=minPctid=min; 
		update();
	}
	public boolean equals(Object obj) {
		if (obj instanceof FilterData) {
			FilterData fd = (FilterData)obj;
			
			return fd.pctid==pctid 
					&& fd.bShowBlocks == bShowBlocks 
					&& fd.bHighBlockHits == bHighBlockHits
					&& fd.bShowEmpty == bShowEmpty
					&& fd.showHits == showHits;
		}
		return false;
	}

	public FilterData copy() {
		return new FilterData(this);
	}

	private void update() {
		setChanged();
		notifyObservers();
	}

	public double getPctid() { return pctid; } // remove type
	
	public boolean isHighlightBlockHits() 	{ return bHighBlockHits;}
	public boolean isShowBlocks() 			{ return bShowBlocks; }
	public boolean isShowEmpty() 			{ return bShowEmpty; }
	public boolean isShowBlockHits() 		{ return showHits == BLOCK_HITS; }
	public boolean isShowAllHits() 			{ return showHits == ALL_HITS; }
	public int getShowHits() 				{ return showHits; }
	
	// not used
	public boolean isShowContainedGeneHits() { return showHits == CONTAINED_GENE_HITS; } 
	public boolean isShowOverlapGeneHits() { return showHits == OVERLAP_GENE_HITS; }
	public boolean isShowNonGeneHits() { return showHits == NON_GENE_HITS; } 	
	
	public void setPctid(double s) {
		pctid = s;
		update();
	}
	public void setShowHits(boolean onlyBlock, boolean all) {
		int t = showHits;
		if (onlyBlock) showHits = BLOCK_HITS;
		else if (all)  showHits = ALL_HITS;
		if (t != showHits) update();
	}
	
	public void setHighBlockHits(boolean highBlockHits) {
		if (this.bHighBlockHits != highBlockHits) {
			this.bHighBlockHits = highBlockHits;
			update();
		}
	}
	
	public void setShowHits(int showHits) {
		if (this.showHits != showHits) {
			this.showHits = showHits;
			update();
		}
	}
	public void setShowBlocks(boolean showBlocks) {
		if (this.bShowBlocks != showBlocks) {
			this.bShowBlocks = showBlocks;
			update();
		}
	}
	public void setShowEmpty(boolean showEmpty) {
		if (this.bShowEmpty != showEmpty) {
			this.bShowEmpty = showEmpty;
			update();
		}
	}
}
