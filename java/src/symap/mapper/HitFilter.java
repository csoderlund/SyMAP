package symap.mapper;

import java.util.Vector;

import dotplot.FilterData;

/**
 * Class HitFilter a hit filter stores all the data used to communicate
 * between the filter and the mapper.
 * CAS520 remove or makes stubs of all FPC stuff. Change for new hit filter.
 */
public class HitFilter {
	private static final double ANY_PCTID  = 0;
	private static final double NO_PCTID  = 100;
	
	private boolean bHiNone; // default
	private boolean bHiBlock, bHiSet, bHi2Gene, bHi0Gene, bHi1Gene;
	
	private boolean bBlock; // default
	private boolean bSet, b2Gene, b1Gene, b0Gene, bAllHit;
	
	private double pctid;
	private double minPctid=NO_PCTID, maxPctid=ANY_PCTID;
	
	private String hoverText=""; // CAS520 add

	private Vector<HitFilterListener> listeners;

	public HitFilter() {
		listeners = null;
		setDefaults();
	}
	public HitFilter(HitFilterListener hfl) {
		this();
		addListener(hfl);
	}
	public HitFilter(HitFilter hf) {
		listeners = null;
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
	
	public String getHover() { // CAS520 So know what is set when hover in whitespace
		String msg = "";
		if (bHiBlock) 		msg += "High Blocks. ";
		else if (bHiSet) 	msg += "High Sets. ";
		else if (bHi2Gene) 	msg += "High >0 Genes. ";
		else if (bHi1Gene) 	msg += "High 1 Gene. ";
		else if (bHi0Gene) 	msg += "High 0 Genes. ";
		
		msg += "Show "; // something always shows
		if (bBlock) 	msg += "Block, ";
		if (bSet)	 	msg += "Sets, ";
		if (b2Gene) 		msg += ">0 Genes, ";
		if (b0Gene) 	msg += "=0 Genes, ";
		if (bAllHit) 	msg += "All, ";
		
		msg = msg.substring(0, msg.length()-2);
		hoverText = 	msg + ".  \n";
		return hoverText;
	}
	// block, set, region CAS520 add for showing synteny from query; b is blocks by default
	public void setForQuery(boolean b, boolean s, boolean r) { 
		if (s)      {setBlock(true); setSet(true); setHiSet(true);}
		else if (r) {setBlock(false); setAllHit(true);}
	}
	
	public void setDefaults() {
		bHiBlock  = bHiSet = bHi2Gene = bHi0Gene = bHi1Gene = false;		
		bHiNone = true;
		
		bSet = b2Gene = b0Gene = bAllHit = false; 
		bBlock = true;
		
		pctid=ANY_PCTID;
	}

	public HitFilter copy() {return new HitFilter(this);}

	// CAS520 was comparing with false for everything instead of the current setting
	public boolean set(HitFilter hf) {
		boolean changed = false;
		if (setHiBlock(hf.bHiBlock, bHiBlock))  	changed = true; 
		if (setHiSet(hf.bHiSet, bHiSet))    		changed = true;
		if (setHi2Gene(hf.bHi2Gene, bHi2Gene))    	changed = true;
		if (setHi1Gene(hf.bHi1Gene, bHi1Gene))		changed = true;
		if (setHi0Gene(hf.bHi0Gene, bHi0Gene))		changed = true;
		if (setHiNone(hf.bHiNone, bHiNone))			changed = true;
		
		if (setBlock(hf.bBlock,bBlock))             changed = true;
		if (setSet(hf.bSet, bSet))        			changed = true;
		if (set2Gene(hf.b2Gene, b2Gene)) 			changed = true; 
		if (set1Gene(hf.b1Gene, b1Gene)) 			changed = true; 
		if (set0Gene(hf.b0Gene, b0Gene))			changed = true; 
		if (setAllHit(hf.bAllHit, bAllHit))			changed = true;
		
		if (setPctid(hf.pctid,false))           	changed = true;
		
		if (changed) updateListeners();
		return changed;
	}

	public boolean set(FilterData fd) { // DrawingPanel DotPlot
		boolean changed = false;
		if (setBlock(fd.isShowBlockHits(),false))           changed = true;
		if (set2Gene(fd.isShowContainedGeneHits(),false))	changed = true;   
		if (set0Gene(fd.isShowNonGeneHits(),false))			changed = true;
		if (setPctid(fd.getPctid(0),false))           	changed = true;
		if (changed) updateListeners();
		return changed;
	}
	
	public interface HitFilterListener {public void update(HitFilter hf);}
	
	/*******************************************************/
// %id
	public double getPctid() {return pctid;}
	public boolean setPctid(double score) {return setPctid(score,true);}
	private boolean setPctid(double score, boolean update) {
		if (pctid != score) {
			pctid = score;
			return true;
		}
		return false;
	}
	public double getMinPctid() {return minPctid;}
	public double getMaxPctid() {return maxPctid;}
	public void setMinPctid(double d) {minPctid=d;}
	public void setMaxPctid(double d) {maxPctid=d;}
	public void condSetPctid(double hitid) {
		if (hitid < minPctid) minPctid = hitid;
		if (hitid > maxPctid) maxPctid = hitid;
	}
	
// highs
	public boolean   isHiNone() {return bHiNone;}
	public boolean  setHiNone(boolean filter) {return setHiNone(filter,true);}
	private boolean setHiNone(boolean filter, boolean update) { 
		if (filter != bHiNone) {
			bHiNone = filter;
			if (update) updateListeners();
			return true;
		}
		return false;
	}
	
	public boolean  isHiBlock() {return bHiBlock;}
	public boolean  setHiBlock(boolean filter) {return setHiBlock(filter,true);}
	private boolean setHiBlock(boolean filter, boolean update) { 
		if (filter != bHiBlock) {
			bHiBlock = filter;
			if (update) updateListeners();
			return true;
		}
		return false;
	}
	
	public boolean   isHiSet() { return bHiSet;}
	public boolean  setHiSet(boolean filter) {return setHiSet(filter,true);}
	private boolean setHiSet(boolean filter, boolean update) { 
		if (filter != bHiSet) {
			bHiSet = filter;
			if (update) updateListeners();
			return true;
		}
		return false;
	}  
	public boolean   isHi2Gene() {return bHi2Gene;}
	public boolean  setHi2Gene(boolean filter) { return setHi2Gene(filter,true);}
	private boolean setHi2Gene(boolean filter, boolean update) { 
		if (filter != bHi2Gene) {
			bHi2Gene = filter;
			if (update) updateListeners();
			return true;
		}
		return false;
	}
	public boolean   isHi1Gene() {return bHi1Gene;}
	public boolean  setHi1Gene(boolean filter) { return setHi1Gene(filter,true);}
	private boolean setHi1Gene(boolean filter, boolean update) { 
		if (filter != bHi1Gene) {
			bHi1Gene = filter;
			if (update) updateListeners();
			return true;
		}
		return false;
	}

	public boolean isHi0Gene() { return bHi0Gene;}
	public boolean  setHi0Gene(boolean filter) { return setHi0Gene(filter,true);}	
	private boolean setHi0Gene(boolean filter, boolean update) { 
		if (filter != bHi0Gene) {
			bHi0Gene = filter;
			if (update) updateListeners();
			return true;
		}
		return false;
	}
// shows
	public boolean isBlock() {return bBlock;}
	public boolean setBlock(boolean filter) {return setBlock(filter,true);}
	private boolean setBlock(boolean filter, boolean update) {
		if (filter != bBlock) {
			bBlock = filter;
			if (update) updateListeners();
			return true;
		}
		return false;
	}
	
	public boolean isSet() { return bSet;}
	public boolean setSet(boolean filter) {return setSet(filter,true);}
	private boolean setSet(boolean filter, boolean update) { 
		if (filter != bSet) {
			bSet = filter;
			if (update) updateListeners();
			return true;
		}
		return false;
	}
	
	public boolean is2Gene() {return b2Gene;}
	public boolean set2Gene(boolean filter) { return set2Gene(filter,true);}
	private boolean set2Gene(boolean filter, boolean update) { 
		if (filter != b2Gene) {
			b2Gene = filter;
			if (update) updateListeners();
			return true;
		}
		return false;
	}
	
	public boolean is1Gene() {return b1Gene;}
	public boolean set1Gene(boolean filter) { return set1Gene(filter,true);}
	private boolean set1Gene(boolean filter, boolean update) { 
		if (filter != b1Gene) {
			b1Gene = filter;
			if (update) updateListeners();
			return true;
		}
		return false;
	}

	public boolean is0Gene() { return b0Gene;}
	public boolean set0Gene(boolean filter) { return set0Gene(filter,true);}	
	private boolean set0Gene(boolean filter, boolean update) { 
		if (filter != b0Gene) {
			b0Gene = filter;
			if (update) updateListeners();
			return true;
		}
		return false;
	}
	
	public boolean isAllHit() { return bAllHit;}
	public boolean setAllHit(boolean filter) { return setAllHit(filter,true);}	
	private boolean setAllHit(boolean filter, boolean update) { 
		if (filter != bAllHit) {
			bAllHit = filter;
			if (update) updateListeners();
			return true;
		}
		return false;
	}
	
	/***********************************************/
	// FPC stubbs - no longer activ
	public boolean getFpHide() {return false;}
	public boolean setFpHide(boolean hide) {return false;}
	public double getFpEvalue() {return 0.0;}
	public boolean setFpEvalue(double score) {return false;}
	public boolean getOnlyShared() {return false;}
	public boolean setOnlySharedHits(boolean filter) {return false;}
	public boolean getMrkHide() {return false;}
	public boolean setMrkHide(boolean hide) {return false;}
	public boolean getBesHide() {return false;}
	public boolean setBesHide(boolean hide) {return false;}
	public double getMrkEvalue() {return 0.0;}
	public boolean setMrkEvalue(double score) {return false;}
	public double getBesEvalue() {return 0.0;}
	public boolean setBesEvalue(double score) {return false;}
	public double getBesPctid() {return 0.0;}
	public boolean setBesPctid(double score) {return false;}
	public boolean getShowJoinDot() {return false;}
	public boolean setShowJoinDot(boolean show) {return false;}
	public boolean getNonRepetitive() {return false;}
	public boolean setNonRepetitive(boolean nonrepetitive) {return false;}
	public double getMaxFpEvalue() {return 0.0;}
	public double getMinFpEvalue() {return 0.0;}
	public void condSetFpEvalue(double min, double max) {}
	public double getMaxMrkEvalue() {return 0.0;}
	public double getMinMrkEvalue() {return 0.0;}
	public void condSetMrkEvalue(double min, double max) {}
	public double getMaxBesEvalue() {return 0.0;}
	public double getMinBesEvalue() {return 0.0;}
	public void condSetBesEvalue(double min, double max) {}
	public double getMaxMrkPctid() {return 0.0;}
	public double getMinMrkPctid() {return 0.0;}
	public void condSetMrkPctid(double min, double max) {}
	public double getMaxBesPctid() {return 0.0;}
	public double getMinBesPctid() {return 0.0;}
	public void condSetBesPctid(double min, double max) {}	
	public boolean isGeneOverlap() { return false;}
	public boolean setGeneOverlap(boolean filter) { return false;}	
	
}
