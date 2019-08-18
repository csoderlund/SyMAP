package symap.mapper;

import java.util.Vector;

import dotplot.FilterData;

/**
 * Class <code>HitFilter</code> a hit filter stores all the data used to communicate
 * between the filter and the mapper.
 *
 */
public class HitFilter {
	private static final double ANY_EVALUE = 1;
	private static final double ANY_PCTID  = 0;

	private static final double NO_EVALUE = 0;
	private static final double NO_PCTID  = 100;

	private static final double DEFAULT_FP_EVALUE  = ANY_EVALUE;
	private static final double DEFAULT_MRK_EVALUE = ANY_EVALUE;
	private static final double DEFAULT_BES_EVALUE = ANY_EVALUE;
	private static final double DEFAULT_MRK_PCTID  = ANY_PCTID;
	private static final double DEFAULT_BES_PCTID  = ANY_PCTID;

	public static boolean DEFAULT_NONREPETITIVE    = false;
	public static boolean DEFAULT_BLOCK            = true;
	public static boolean DEFAULT_GENE_CONTAINED   = false; // mdb added 3/7/07 #101
	public static boolean DEFAULT_GENE_OVERLAP     = false; // mdb added 3/7/07 #101
	public static boolean DEFAULT_NON_GENE		   = false; // mdb added 3/7/07 #101

	public static final boolean DEFAULT_HIDE_FP          = false;
	public static final boolean DEFAULT_HIDE_MRK         = false;
	public static final boolean DEFAULT_HIDE_BES         = false;
	public static final boolean DEFAULT_SHOW_JOIN_DOT    = true;
	public static final boolean DEFAULT_SHOW_ONLY_SHARED = false;

	public static final boolean DEFAULT_COLOR_BY_STRAND = false;

	private double mrkEvalue, besEvalue;
	private double mrkPctid, besPctid;
	private boolean hideMrk, hideBes;
	private boolean block, nonrepetitive; 
	private boolean geneContained; 	
	private boolean geneOverlap; 	
	private boolean nonGene; 		
	private boolean showJoinDot;
	private boolean onlyShared;

	private boolean colorByStrand;

	private double fpEvalue;
	private boolean hideFp;

	private double maxFpEvalue, minFpEvalue;
	private double maxMrkEvalue, minMrkEvalue, maxMrkPctid, minMrkPctid;
	private double maxBesEvalue, minBesEvalue, maxBesPctid, minBesPctid; 

	private Vector<HitFilterListener> listeners;

	public HitFilter() {
		listeners = null;
		setValueDefaults();
		setDefaults();
	}

	public HitFilter(HitFilterListener hfl) {
		this();
		addListener(hfl);
	}

	public HitFilter(HitFilter hf) {
		listeners = null;
		setValueDefaults();
		set(hf);
	}

	public void addListener(HitFilterListener listener) {
		if (listeners == null) listeners = new Vector<HitFilterListener>(1,1);
		listeners.add(listener);
	}

	private void updateListeners() {
		if (listeners != null) {
			for (int i = 0; i < listeners.size(); i++)
				(listeners.get(i)).update(this);
		}
	}

	public void setValueDefaults() {
		maxMrkEvalue = maxBesEvalue = maxFpEvalue = NO_EVALUE;
		minMrkEvalue = minBesEvalue = minFpEvalue = ANY_EVALUE;
		maxMrkPctid  = maxBesPctid  = ANY_PCTID;
		minMrkPctid  = minBesPctid  = NO_PCTID;
	}

	public void setDefaults() {
		mrkEvalue     = DEFAULT_MRK_EVALUE;
		besEvalue     = DEFAULT_BES_EVALUE;
		mrkPctid      = DEFAULT_MRK_PCTID;
		besPctid      = DEFAULT_BES_PCTID;
		hideMrk       = DEFAULT_HIDE_MRK;
		hideBes       = DEFAULT_HIDE_BES;
		block         = DEFAULT_BLOCK;
		geneContained = DEFAULT_GENE_CONTAINED; // mdb added 3/7/07 #101
		geneOverlap   = DEFAULT_GENE_OVERLAP; 	// mdb added 3/7/07 #101
		nonGene		  = DEFAULT_NON_GENE; 		// mdb added 3/7/07 #101
		nonrepetitive = DEFAULT_NONREPETITIVE;
		showJoinDot   = DEFAULT_SHOW_JOIN_DOT;
		onlyShared    = DEFAULT_SHOW_ONLY_SHARED;
		fpEvalue      = DEFAULT_FP_EVALUE;
		hideFp        = DEFAULT_HIDE_FP;

		colorByStrand = DEFAULT_COLOR_BY_STRAND;
	}

	public HitFilter copy() {
		return new HitFilter(this);
	}

	public boolean set(HitFilter hf) {
		boolean changed = false;
		if (setMrkEvalue(hf.mrkEvalue,false))         changed = true;
		if (setBesEvalue(hf.besEvalue,false))         changed = true;
		if (setMrkPctid(hf.mrkPctid,false))           changed = true;
		if (setBesPctid(hf.besPctid,false))           changed = true;
		if (setMrkHide(hf.hideMrk,false))             changed = true;
		if (setBesHide(hf.hideBes,false))             changed = true;
		if (setBlock(hf.block,false))                 changed = true;
		if (setGeneContained(hf.geneContained,false)) changed = true; // mdb added 3/7/07 #101
		if (setGeneOverlap(hf.geneOverlap,false))	  changed = true; // mdb added 3/7/07 #101
		if (setNonGene(hf.nonGene,false))			  changed = true; // mdb added 3/7/07 #101
		if (setNonRepetitive(hf.nonrepetitive,false)) changed = true;
		if (setShowJoinDot(hf.showJoinDot,false))     changed = true;
		if (setOnlySharedHits(hf.onlyShared,false))   changed = true;
		if (setFpEvalue(hf.fpEvalue,false))           changed = true;
		if (setFpHide(hf.hideFp,false))               changed = true;
		if (setColorByStrand(hf.colorByStrand,false)) changed = true;
		if (changed) updateListeners();
		return changed;
	}

	public boolean set(FilterData fd) {
		boolean changed = false;
		if (setMrkEvalue(fd.getEvalue(FilterData.MRK),false))         changed = true;
		if (setBesEvalue(fd.getEvalue(FilterData.BES),false))         changed = true;
		if (setMrkPctid(fd.getPctid(FilterData.MRK),false))           changed = true;
		if (setBesPctid(fd.getPctid(FilterData.BES),false))           changed = true;
		if (setMrkHide(fd.getHide(FilterData.MRK),false))             changed = true;
		if (setBesHide(fd.getHide(FilterData.BES),false))             changed = true;
		if (setBlock(fd.isShowBlockHits(),false))                     changed = true;
		if (setGeneContained(fd.isShowContainedGeneHits(),false))	  changed = true; // mdb added 3/7/07 #101
		if (setGeneOverlap(fd.isShowOverlapGeneHits(),false))	  	  changed = true; // mdb added 3/7/07 #101
		if (setNonGene(fd.isShowNonGeneHits(),false))				  changed = true; // mdb added 3/7/07 #101
		if (setNonRepetitive(fd.isShowNonRepetitiveHits(),false))     changed = true;
		if (setFpEvalue(fd.getEvalue(FilterData.FP),false))           changed = true;
		if (setFpHide(fd.getHide(FilterData.FP),false))               changed = true;
		if (setColorByStrand(fd.getColorByStrand(),false))            changed = true;
		if (changed) updateListeners();
		return changed;
	}

	public boolean getColorByStrand() {
		return colorByStrand;
	}

	public boolean setColorByStrand(boolean cbs) {
		return setColorByStrand(cbs,true);
	}

	public boolean getFpHide() {
		return hideFp;
	}

	public boolean setFpHide(boolean hide) {
		return setFpHide(hide,true);
	}

	public double getFpEvalue() {
		return fpEvalue;
	}

	public boolean setFpEvalue(double score) {
		return setFpEvalue(score,true);
	}

	public boolean getOnlyShared() {
		return onlyShared;
	}

	public boolean setOnlySharedHits(boolean filter) {
		return setOnlySharedHits(filter,true);
	}

	public boolean getMrkHide() {
		return hideMrk;
	}

	public boolean setMrkHide(boolean hide) {
		return setMrkHide(hide,true);
	}

	public boolean getBesHide() {
		return hideBes;
	}

	public boolean setBesHide(boolean hide) {
		return setBesHide(hide,true);
	}

	public double getMrkEvalue() {
		return mrkEvalue;
	}

	public boolean setMrkEvalue(double score) {
		return setMrkEvalue(score,true);
	}

	public double getMrkPctid() {
		return mrkPctid;
	}

	public boolean setMrkPctid(double score) {
		return setMrkPctid(score,true);
	}

	public double getBesEvalue() {
		return besEvalue;
	}

	public boolean setBesEvalue(double score) {
		return setBesEvalue(score,true);
	}

	public double getBesPctid() {
		return besPctid;
	}

	public boolean setBesPctid(double score) {
		return setBesPctid(score,true);
	}

	public boolean getBlock() {
		return block;
	}

	public boolean setBlock(boolean filter) {
		return setBlock(filter,true);
	}

	public boolean getGeneContained() { 
		return geneContained;
	}

	public boolean setGeneContained(boolean filter) { 
		return setGeneContained(filter,true);
	}

	public boolean getGeneOverlap() { 
		return geneOverlap;
	}
	
	public boolean setGeneOverlap(boolean filter) { 
		return setGeneOverlap(filter,true);
	}	
	
	public boolean getNonGene() { 
		return nonGene;
	}

	public boolean setNonGene(boolean filter) { 
		return setNonGene(filter,true);
	}	

	public boolean getShowJoinDot() {
		return showJoinDot;
	}

	public boolean setShowJoinDot(boolean show) {
		return setShowJoinDot(show,true);
	}

	public boolean getNonRepetitive() {
		return nonrepetitive;
	}

	public boolean setNonRepetitive(boolean nonrepetitive) {
		return setNonRepetitive(nonrepetitive,true);
	}

	public double getMaxFpEvalue() {
		return maxFpEvalue;
	}

	public double getMinFpEvalue() {
		return minFpEvalue;
	}

	public void condSetFpEvalue(double min, double max) {
		if (min > 0 && min < minFpEvalue) minFpEvalue = min;
		if (max > maxFpEvalue) maxFpEvalue = max;
	}

	public double getMaxMrkEvalue() {
		return maxMrkEvalue;
	}

	public double getMinMrkEvalue() {
		return minMrkEvalue;
	}

	public void condSetMrkEvalue(double min, double max) {
		if (min > 0 && min < minMrkEvalue) minMrkEvalue = min;
		if (max > maxMrkEvalue) maxMrkEvalue = max;
	}

	public double getMaxBesEvalue() {
		return maxBesEvalue;
	}

	public double getMinBesEvalue() {
		return minBesEvalue;
	}

	public void condSetBesEvalue(double min, double max) {
		if (min > 0 && min < minBesEvalue) minBesEvalue = min;
		if (max > maxBesEvalue) maxBesEvalue = max;
	}

	public double getMaxMrkPctid() {
		return maxMrkPctid;
	}

	public double getMinMrkPctid() {
		return minMrkPctid;
	}

	public void condSetMrkPctid(double min, double max) {
		if (min < minMrkPctid) minMrkPctid = min;
		if (max > maxMrkPctid) maxMrkPctid = max;
	}

	public double getMaxBesPctid() {
		return maxBesPctid;
	}

	public double getMinBesPctid() {
		return minBesPctid;
	}

	public void condSetBesPctid(double min, double max) {
		if (min < minBesPctid) minBesPctid = min;
		if (max > maxBesPctid) maxBesPctid = max;
	}

	private boolean setColorByStrand(boolean cbs, boolean update) {
		if (cbs != colorByStrand) {
			colorByStrand = cbs;
			if (update) updateListeners();
			return true;
		}
		return false;
	}

	private boolean setFpHide(boolean hide, boolean update) {
		if (hideFp != hide) {
			hideFp = hide;
			if (update) updateListeners();
			return true;
		}
		return false;
	}

	private boolean setFpEvalue(double score, boolean update) {
		if (fpEvalue != score) {
			fpEvalue = score;
			if (update) updateListeners();
			return true;
		}
		return false;
	}

	private boolean setOnlySharedHits(boolean filter, boolean update) {
		if (onlyShared != filter) {
			onlyShared = filter;
			if (update) updateListeners();
			return true;
		}
		return false;
	}

	private boolean setMrkHide(boolean hide, boolean update) {
		if (hideMrk != hide) {
			hideMrk = hide;
			if (update) updateListeners();
			return true;
		}
		return false;
	}

	private boolean setBesHide(boolean hide, boolean update) {
		if (hideBes != hide) {
			hideBes = hide;
			if (update) updateListeners();
			return true;
		}
		return false;
	}

	private boolean setMrkEvalue(double score, boolean update) {
		if (mrkEvalue != score) {
			mrkEvalue = score;
			if (update) updateListeners();
			return true;
		}
		return false;
	}

	private boolean setMrkPctid(double score, boolean update) {
		if (mrkPctid != score) {
			mrkPctid = score;
			return true;
		}
		return false;
	}

	private boolean setBesEvalue(double score, boolean update) {
		if (besEvalue != score) {
			besEvalue = score;
			if (update) updateListeners();
			return true;
		}
		return false;
	}

	private boolean setBesPctid(double score, boolean update) {
		if (besPctid != score) {
			besPctid = score;
			if (update) updateListeners();
			return true;
		}
		return false;
	}

	private boolean setBlock(boolean filter, boolean update) {
		if (filter != block) {
			block = filter;
			if (update) updateListeners();
			return true;
		}
		return false;
	}
	
	private boolean setGeneContained(boolean filter, boolean update) { // mdb added 3/7/07 #101
		if (filter != geneContained) {
			geneContained = filter;
			if (update) updateListeners();
			return true;
		}
		return false;
	}	

	private boolean setGeneOverlap(boolean filter, boolean update) { // mdb added 3/7/07 #101
		if (filter != geneOverlap) {
			geneOverlap = filter;
			if (update) updateListeners();
			return true;
		}
		return false;
	}	
	
	private boolean setNonGene(boolean filter, boolean update) { // mdb added 3/7/07 #101
		if (filter != nonGene) {
			nonGene = filter;
			if (update) updateListeners();
			return true;
		}
		return false;
	}		
	
	private boolean setShowJoinDot(boolean show, boolean update) {
		if (show != showJoinDot) {
			showJoinDot = show;
			if (update) updateListeners();
			return true;
		}
		return false;
	}

	private boolean setNonRepetitive(boolean nonrepetitive, boolean update) {
		if (nonrepetitive != this.nonrepetitive) {
			this.nonrepetitive = nonrepetitive;
			if (update) updateListeners();
			return true;
		}
		return false;
	}    

	public static void setFilteredDefault(boolean filtered) {
		if (filtered) {
			DEFAULT_NONREPETITIVE    = false;
			DEFAULT_BLOCK            = true;
			DEFAULT_GENE_CONTAINED	 = false; // mdb added 3/7/07 #101
			DEFAULT_GENE_OVERLAP	 = false; // mdb added 3/7/07 #101
			DEFAULT_NON_GENE	 	 = false; // mdb added 3/7/07 #101
		}
		else {
			DEFAULT_NONREPETITIVE    = true;
			DEFAULT_BLOCK            = false;
			DEFAULT_GENE_CONTAINED	 = false; // mdb added 3/7/07 #101
			DEFAULT_GENE_OVERLAP	 = false; // mdb added 3/7/07 #101
			DEFAULT_NON_GENE	 	 = false; // mdb added 3/7/07 #101
		}
	}

	public interface HitFilterListener {
		public void update(HitFilter hf);
	}
}
