package dotplot;

import java.awt.Color;

import java.util.Observable;
import java.util.Arrays;

public class FilterData extends Observable implements DotPlotConstants {
	public static final int DEFAULT_HITS = ALL_HITS;

	private double evalue[]       = new double[NUM_HIT_TYPES];
	private double pctid[]        = new double[NUM_HIT_TYPES];
	private boolean hide[]        = new boolean[NUM_HIT_TYPES];
	private boolean showBlocks    = true;
	private boolean showContigs   = true;
	private boolean showEmpty     = true; // mdb added 12/2/09 #203 - hide groups with no blocks
	private int showHits = DEFAULT_HITS;

	private boolean[] highlight    = new boolean[DotPlot.TOT_RUNS];
	private Color[] highlightColor = new Color[DotPlot.TOT_RUNS];
	private Color   sharedColor    = null;

	private boolean lockHighlight = false;

	public FilterData() {
		Arrays.fill(evalue,ANY_EVALUE);
		Arrays.fill(pctid,ANY_PCTID);
		Arrays.fill(hide,false);
		Arrays.fill(highlight,false);
		for (int i = 0; i < highlightColor.length; i++)
			highlightColor[i] = Plot.BLOCK_HITS_COLORS[i];
		sharedColor = Plot.SHARED_BLOCK_HITS_COLOR;
	}

	public FilterData(FilterData fd) {
		this();
		set(fd);
		lockHighlight = fd.lockHighlight;
	}

	/**
	 * Method <code>lockHitHighlighting</code> when true, locks the highlightHits
	 * variable so that no method call other than an explicit setting (setHighlightHits or
	 * setHighlightBlockHits) will have an affect on it until this method is called passing
	 * in a false value.  This lock highlights variable only gets modified through a call
	 * to this method (directly). The variable is also not used in determining equality.
	 *
	 * @param lock a <code>boolean</code> value
	 */
	public void lockHitHighlighting(boolean lock) {
		lockHighlight = lock;
	}

	public boolean isLockHitHighlighting() {
		return lockHighlight;
	}

	public void setDefaults() {
		if (!equals(new FilterData())) {
			Arrays.fill(evalue,1);
			Arrays.fill(pctid,0);
			Arrays.fill(hide,false);
			showBlocks = true;
			showContigs = true;
			showEmpty = true;
			showHits = DEFAULT_HITS;
			if (!lockHighlight) {
				Arrays.fill(highlight,false);
				for (int i = 0; i < highlightColor.length; i++)
					highlightColor[i] = Plot.BLOCK_HITS_COLORS[i];
				sharedColor = Plot.SHARED_BLOCK_HITS_COLOR;
				sharedColor = Plot.SHARED_BLOCK_HITS_COLOR;
			}
			update();
		}
	}

	public void setDefaults(ScoreBounds sb) {
		FilterData fd = new FilterData(this);
		fd.set(sb);
		set(fd);
	}

	private void set(ScoreBounds sb) {
		evalue[MRK] = Math.pow(10,sb.getEvalue(MIN,MRK)*-1);
		evalue[BES] = Math.pow(10,sb.getEvalue(MIN,BES)*-1);
		evalue[FP]  = Math.pow(10,sb.getEvalue(MIN,FP) *-1);
		pctid[MRK] = sb.getPctid(MIN,MRK);
		pctid[BES] = sb.getPctid(MIN,BES);
	}

	public void set(FilterData fd) {
		if (!equals(fd)) {
			System.arraycopy(fd.evalue,0,evalue,0,evalue.length);
			System.arraycopy(fd.pctid,0,pctid,0,pctid.length);
			System.arraycopy(fd.hide,0,hide,0,hide.length);
			showBlocks = fd.showBlocks;
			showContigs = fd.showContigs;
			showEmpty = fd.showEmpty;
			showHits = fd.showHits;
			if (!lockHighlight) {
				for (int i = 0; i < highlight.length; i++) {
					highlight[i]      = fd.highlight[i];
					highlightColor[i] = fd.highlightColor[i];
				}
				sharedColor = fd.sharedColor;
			}
			update();
		}
	}

	public boolean equals(Object obj) {
		if (obj instanceof FilterData) {
			FilterData fd = (FilterData)obj;
			return Arrays.equals(fd.evalue,evalue) 
					&& Arrays.equals(fd.pctid,pctid) 
					&& Arrays.equals(fd.hide,hide) 
					&& fd.showBlocks == showBlocks 
					&& fd.showContigs == showContigs 
					&& fd.showEmpty == showEmpty
					&& fd.showHits == showHits 
					&& Arrays.equals(highlight,fd.highlight) 
					&& Arrays.equals(highlightColor,fd.highlightColor) 
					&& equalColors(sharedColor,fd.sharedColor);
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

	public boolean getColorByStrand() { return false; }
	public double getEvalue(int type) { return evalue[type]; }
	public double getPctid(int type) { return pctid[type]; }
	public boolean getHide(int type) { return hide[type]; }
	public boolean isShowBlocks() { return showBlocks; }
	public boolean isShowContigs() { return showContigs; }
	public boolean isShowEmpty() { return showEmpty; }
	public boolean isShowNonRepetitiveHits() { return showHits == NON_REPETITIVE_HITS; }
	public boolean isShowBlockHits() { return showHits == BLOCK_HITS; }
	public boolean isShowAllHits() { return showHits == ALL_HITS; }
	public int getShowHits() { return showHits; }
	
	// mdb added 3/7/07
	public boolean isShowContainedGeneHits() { return showHits == CONTAINED_GENE_HITS; } 
	public boolean isShowOverlapGeneHits() { return showHits == OVERLAP_GENE_HITS; }
	public boolean isShowNonGeneHits() { return showHits == NON_GENE_HITS; } 	
	
	public void setShowHits(boolean onlyBlock, boolean onlyNonRepetitive, boolean all) {
		int t = showHits;
		if (onlyBlock) showHits = BLOCK_HITS;
		else if (onlyNonRepetitive) showHits = NON_REPETITIVE_HITS;
		else if (all) showHits = ALL_HITS;
		if (t != showHits) update();
	}

	public void setShowHits(int showHits) {
		if (this.showHits != showHits) {
			this.showHits = showHits;
			update();
		}
	}

	public boolean isHighlightBlockHits() {
		return highlight[0];
	}

	public boolean isHighlightBlockHits(int altNum) {
		return highlight[altNum];
	}

	public boolean isHighlightAnyBlockHits() {
		for (int i = 0; i < highlight.length; i++)
			if (highlight[i]) return true;
		return false;
	}

	public void setHighlightBlockHits(boolean highlightHits) {
		setHighlightBlockHits(0,highlightHits);
	}

	public void setHighlightSubChains(boolean highlightSubChains) {
		setHighlightBlockHits(DotPlot.SUB_CHAIN_RUN,highlightSubChains);
	}

	public boolean isHighlightSubChains() {
		return highlight[DotPlot.SUB_CHAIN_RUN];
	}

	public void setHighlightBlockHits(int altNum, boolean highlightHits) {
		if (highlight[altNum] != highlightHits) {
			highlight[altNum] = highlightHits;
			update();
		}
	}

	public Color getBlockHitsColor(int altNum) {
		return highlightColor[altNum];
	}

	public Color getBlockHitsColor() {
		return highlightColor[0];
	}

	public void setBlockHitsColor(int altNum, Color c) {
		if (!equalColors(highlightColor[altNum],c)) {
			highlightColor[altNum] = c;
			update();
		}
	}

	public void setBlockHitsColor(Color c) {
		setBlockHitsColor(0,c);
	}

	public Color getSharedHitsColor() {
		return sharedColor;
	}

	public void setSharedHitsColor(Color c) {
		if (!equalColors(sharedColor,c)) {
			sharedColor = c;
			update();
		}
	}

	public void setEvalue(int type, double s) {
		if (evalue[type] != s) {
			evalue[type] = s;
			update();
		}
	}

	public void setPctid(int type, double s) {
		if (pctid[type] != s) {
			pctid[type] = s;
			update();
		}
	}

	public void setHide(int type, boolean hide) {
		if (this.hide[type] != hide) {
			this.hide[type] = hide;
			update();
		}
	}

	public void setShowBlocks(boolean showBlocks) {
		if (this.showBlocks != showBlocks) {
			this.showBlocks = showBlocks;
			update();
		}
	}

	public void setShowContigs(boolean showContigs) {
		if (this.showContigs != showContigs) {
			this.showContigs = showContigs;
			update();
		}
	}
	
	// mdb added 12/3/09 #203
	public void setShowEmpty(boolean showEmpty) {
		if (this.showEmpty != showEmpty) {
			this.showEmpty = showEmpty;
			update();
		}
	}

	private static boolean equalColors(Color c1, Color c2) {
		return c1 == null ? c2 == null : c1.equals(c2);
	}
}
