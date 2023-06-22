package symap.mapper;

import java.util.Vector;

/**
 * Class MfilterData a hit filter stores all the data used to communicate
 * between the filter and the mapper.
 * CAS520 remove or makes stubs of all FPC stuff. Change for new hit filter.
 * CAS533 removed reference to the Dotplot hitfilter
 * CAS541 HitFilter=>MfilterData
 */
public class HfilterData {
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

	public HfilterData() {
		listeners = null;
		setDefaults();
	}
	public HfilterData(HitFilterListener hfl) {
		this();
		addListener(hfl);
	}
	private HfilterData(HfilterData hf) { // copy
		listeners = null;
		set(hf);
	}
	
	private void addListener(HitFilterListener listener) {
		if (listeners == null) listeners = new Vector<HitFilterListener>(1,1);
		listeners.add(listener);
	}
	private void updateListeners() {
		if (listeners != null) {
			for (int i = 0; i < listeners.size(); i++)
				(listeners.get(i)).update(this);
		}
	}
	
	public String getFilterText() { // CAS520 So know what is set when hover in whitespace
		String msg = "";			// SeqHits expects this to start with High if there is highlightening
		if (bHiBlock) 		msg += "High Blocks; ";
		else if (bHiSet) 	msg += "High Sets; ";
		else if (bHi2Gene) 	msg += "High 2 Genes; ";
		else if (bHi1Gene) 	msg += "High 1 Gene; ";
		else if (bHi0Gene) 	msg += "High 0 Genes; ";
		
		msg += "Show "; // something always shows
		if (bBlock) 	msg += "Block, ";
		if (bSet)	 	msg += "Sets, ";
		if (b2Gene) 	msg += "2 Genes, ";
		if (b1Gene) 	msg += "1 Gene, ";
		if (b0Gene) 	msg += "0 Genes, ";
		if (bAllHit) 	msg += "All, ";
		
		msg = msg.substring(0, msg.length()-2);
		hoverText = 	msg + "\n";
		return hoverText;
	}
	// block, set, region CAS520 add for showing synteny from query; b is blocks by default
	public void setForQuery(boolean b, boolean s, boolean r) { 
		if (s)      {setBlock(true); setSet(true); setHiSet(true); setHiNone(false);}
		else if (r) {setBlock(false); setAllHit(true);}
	}
	// CAS530 dotplot
	public void setForDP(boolean b, boolean r) {
		setBlock(false); setAllHit(false);
		if (b) setBlock(true);
		if (r) setAllHit(true);
	}
	
	public void setDefaults() {
		bHiBlock  = bHiSet = bHi2Gene = bHi0Gene = bHi1Gene = false;		
		bHiNone = true;
		
		bSet = b2Gene = b0Gene = bAllHit = false; 
		bBlock = true;
		
		pctid=ANY_PCTID;
	}

	public HfilterData copy() {return new HfilterData(this);}

	// CAS520 was comparing with false for everything instead of the current setting
	public boolean set(HfilterData hf) {
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

	public interface HitFilterListener {public void update(HfilterData hf);}
	
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
	public boolean  setHiSet(boolean bFilter) {return setHiSet(bFilter,true);}
	private boolean setHiSet(boolean bFilter, boolean update) { 
		if (bFilter != bHiSet) {
			bHiSet = bFilter;
			if (update) updateListeners();
			return true;
		}
		return false;
	}  
	public boolean   isHi2Gene() {return bHi2Gene;}
	public boolean  setHi2Gene(boolean bFilter) { return setHi2Gene(bFilter,true);}
	private boolean setHi2Gene(boolean bFilter, boolean update) { 
		if (bFilter != bHi2Gene) {
			bHi2Gene = bFilter;
			if (update) updateListeners();
			return true;
		}
		return false;
	}
	public boolean   isHi1Gene() {return bHi1Gene;}
	public boolean  setHi1Gene(boolean bFilter) { return setHi1Gene(bFilter,true);}
	private boolean setHi1Gene(boolean bFilter, boolean update) { 
		if (bFilter != bHi1Gene) {
			bHi1Gene = bFilter;
			if (update) updateListeners();
			return true;
		}
		return false;
	}

	public boolean isHi0Gene() { return bHi0Gene;}
	public boolean  setHi0Gene(boolean bFilter) { return setHi0Gene(bFilter,true);}	
	private boolean setHi0Gene(boolean bFilter, boolean update) { 
		if (bFilter != bHi0Gene) {
			bHi0Gene = bFilter;
			if (update) updateListeners();
			return true;
		}
		return false;
	}
// shows
	public boolean isBlock() {return bBlock;}
	public boolean setBlock(boolean bFilter) {return setBlock(bFilter,true);}
	private boolean setBlock(boolean bFilter, boolean update) {
		if (bFilter != bBlock) {
			bBlock = bFilter;
			if (update) updateListeners();
			return true;
		}
		return false;
	}
	
	public boolean isSet() { return bSet;}
	public boolean setSet(boolean bFilter) {return setSet(bFilter,true);}
	private boolean setSet(boolean bFilter, boolean update) { 
		if (bFilter != bSet) {
			bSet = bFilter;
			if (update) updateListeners();
			return true;
		}
		return false;
	}
	
	public boolean is2Gene() {return b2Gene;}
	public boolean set2Gene(boolean bFilter) { return set2Gene(bFilter,true);}
	private boolean set2Gene(boolean bFilter, boolean update) { 
		if (bFilter != b2Gene) {
			b2Gene = bFilter;
			if (update) updateListeners();
			return true;
		}
		return false;
	}
	
	public boolean is1Gene() {return b1Gene;}
	public boolean set1Gene(boolean bFilter) { return set1Gene(bFilter,true);}
	private boolean set1Gene(boolean bFilter, boolean update) { 
		if (bFilter != b1Gene) {
			b1Gene = bFilter;
			if (update) updateListeners();
			return true;
		}
		return false;
	}

	public boolean is0Gene() { return b0Gene;}
	public boolean set0Gene(boolean bFilter) { return set0Gene(bFilter,true);}	
	private boolean set0Gene(boolean bFilter, boolean update) { 
		if (bFilter != b0Gene) {
			b0Gene = bFilter;
			if (update) updateListeners();
			return true;
		}
		return false;
	}
	
	public boolean isAllHit() { return bAllHit;}
	public boolean setAllHit(boolean bFilter) { return setAllHit(bFilter,true);}	
	private boolean setAllHit(boolean bFilter, boolean update) { 
		if (bFilter != bAllHit) {
			bAllHit = bFilter;
			if (update) updateListeners();
			return true;
		}
		return false;
	}
}
