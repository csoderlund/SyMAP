package symap.mapper;

/**
 * Class MfilterData a hit filter stores all the data used to communicate
 * between the filter and the mapper.
 * CAS520 remove or makes stubs of all FPC stuff. Change for new hit filter.
 * CAS533 removed reference to the Dotplot hitfilter
 * CAS541 HitFilter=>MfilterData; CAS542 remove listeners, that did nothing
 */
public class HfilterData {
	private boolean DEBUG = false; // Globals.DEBUG;
	public static int cntHitFilter=1; // for debugging
	private int myCntHitFilter=0;
	
	private static final double ANY_PCTID  = 0;
	private static final double NO_PCTID  = 100;
	
	private boolean bHiNone=true; // default
	private boolean bHiBlock=false, bHiSet=false, bHi2Gene=false, bHi0Gene=false, bHi1Gene=false;
	private boolean bHiPopup=false; // CAS543 add
	
	private boolean bBlock=true; // default
	private boolean bSet=false, b2Gene=false, b1Gene=false, b0Gene=false, bAllHit=false;
	
	private double pctid;
	private double minPctid=NO_PCTID, maxPctid=ANY_PCTID;
	
	private String hoverText=""; // CAS520 add

	public HfilterData() {
		myCntHitFilter = cntHitFilter++;
		setDefaults();
	}
	
	private HfilterData(HfilterData hf) { // copy
		myCntHitFilter = cntHitFilter++;
		setChanged(hf, "HfilterData from hf");
	}
	
	public String getFilterText() { // CAS520 So know what is set when hover in whitespace
		String msg = "";			// SeqHits expects this to start with High if there is highlightening
		if (bHiBlock) 		msg += "High Blocks; ";
		else if (bHiSet) 	msg += "High Sets; ";
		else if (bHi2Gene) 	msg += "High 2 Genes; ";
		else if (bHi1Gene) 	msg += "High 1 Gene; ";
		else if (bHi0Gene) 	msg += "High 0 Genes; ";
		else if (bHiPopup) 	msg += "High Popup; ";
		
		msg += "Show "; // something always shows
		if (bBlock) 	msg += "Block, ";
		if (bSet)	 	msg += "Sets, ";
		if (b2Gene) 	msg += "2 Genes, ";
		if (b1Gene) 	msg += "1 Gene, ";
		if (b0Gene) 	msg += "0 Genes, ";
		if (bAllHit) 	msg += "All, ";
		if (pctid!=0 && pctid!=minPctid) msg += "Id=" + (int) pctid + "%"; // CAS543 add
		
		if (msg.endsWith(", ")) msg = msg.substring(0, msg.length()-2);
		hoverText = 	msg + "\n";
		if (DEBUG) hoverText = myCntHitFilter + ": " + hoverText;
		return hoverText;
	}
	// block, set, region CAS520 add for showing synteny from query; b is blocks by default
	public void setForQuery(boolean b, boolean s, boolean r) { // block, set, region
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
		bHiBlock  = bHiSet = bHi2Gene = bHi0Gene = bHi1Gene = bHiPopup = false;		
		bHiNone = true;
		
		bSet = b2Gene = b0Gene = bAllHit = false; 
		bBlock = true;
		
		pctid=ANY_PCTID;
	}

	public HfilterData copy(String msg) {
		return new HfilterData(this);
	}

	// CAS520 was comparing with false for everything instead of the current setting
	// this changes initial settings for dotplot and query table
	public boolean setChanged(HfilterData hf, String msg) {
		boolean changed = false;
		if (setHiBlock(hf.bHiBlock))  	changed = true; 
		if (setHiSet(hf.bHiSet))    	changed = true;
		if (setHi2Gene(hf.bHi2Gene))    changed = true;
		if (setHi1Gene(hf.bHi1Gene))	changed = true;
		if (setHi0Gene(hf.bHi0Gene))	changed = true;
		if (setHiNone(hf.bHiNone))		changed = true;
		if (setHiPopup(hf.bHiPopup))	changed = true;
		
		if (setBlock(hf.bBlock))        changed = true;
		if (setSet(hf.bSet))        	changed = true;
		if (set2Gene(hf.b2Gene)) 		changed = true; 
		if (set1Gene(hf.b1Gene)) 		changed = true; 
		if (set0Gene(hf.b0Gene))		changed = true; 
		if (setAllHit(hf.bAllHit))		changed = true;
		
		if (setPctid(hf.pctid))   		changed = true;
		
		return changed;
	}

	public interface HitFilterListener {public void update(HfilterData hf);}
	
	/*******************************************************/
// %id
	public double getPctid() {return pctid;}
	public boolean setPctid(double score) {
		if (pctid != score) {
			pctid = score;
			return true;
		}
		return false;
	}
	public double getMinPctid() {return minPctid;}
	public double getMaxPctid() {return maxPctid;}
	public void condSetPctid(double hitid) { // set when HfilterData is created
		if (hitid < minPctid) minPctid = hitid;
		if (hitid > maxPctid) maxPctid = hitid;
	}
	
// highs
	public boolean   isHiPopup() {return bHiPopup;} // CAS543 new
	public boolean  setHiPopup(boolean filter) {
		if (filter != bHiPopup) {
			bHiPopup = filter;
			return true;
		}
		return false;
	}
	public boolean   isHiNone() {return bHiNone;}
	public boolean  setHiNone(boolean filter) {
		if (filter != bHiNone) {
			bHiNone = filter;
			return true;
		}
		return false;
	}
	
	public boolean  isHiBlock() {return bHiBlock;}
	public boolean  setHiBlock(boolean filter) { 
		if (filter != bHiBlock) {
			bHiBlock = filter;
			return true;
		}
		return false;
	}
	
	public boolean   isHiSet() { return bHiSet;}
	public boolean  setHiSet(boolean bFilter) {
		if (bFilter != bHiSet) {
			bHiSet = bFilter;
			return true;
		}
		return false;
	}  
	public boolean   isHi2Gene() {return bHi2Gene;}
	public boolean  setHi2Gene(boolean bFilter) {  
		if (bFilter != bHi2Gene) {
			bHi2Gene = bFilter;
			return true;
		}
		return false;
	}
	public boolean   isHi1Gene() {return bHi1Gene;}
	public boolean  setHi1Gene(boolean bFilter) { 
		if (bFilter != bHi1Gene) {
			bHi1Gene = bFilter;
			return true;
		}
		return false;
	}

	public boolean isHi0Gene() { return bHi0Gene;}
	public boolean  setHi0Gene(boolean bFilter) { 
		if (bFilter != bHi0Gene) {
			bHi0Gene = bFilter;
			return true;
		}
		return false;
	}
// shows
	public boolean isBlock() {return bBlock;}
	public boolean setBlock(boolean bFilter) {
		if (bFilter != bBlock) {
			bBlock = bFilter;
			return true;
		}
		return false;
	}
	
	public boolean isSet() { return bSet;}
	public boolean setSet(boolean bFilter) {
		if (bFilter != bSet) {
			bSet = bFilter;
			return true;
		}
		return false;
	}
	
	public boolean is2Gene() {return b2Gene;}
	public boolean set2Gene(boolean bFilter) {  
		if (bFilter != b2Gene) {
			b2Gene = bFilter;
			return true;
		}
		return false;
	}
	
	public boolean is1Gene() {return b1Gene;}
	public boolean set1Gene(boolean bFilter) { 
		if (bFilter != b1Gene) {
			b1Gene = bFilter;
			return true;
		}
		return false;
	}

	public boolean is0Gene() { return b0Gene;}
	public boolean set0Gene(boolean bFilter) { 
		if (bFilter != b0Gene) {
			b0Gene = bFilter;
			return true;
		}
		return false;
	}
	
	public boolean isAllHit() { return bAllHit;}
	public boolean setAllHit(boolean bFilter) { 
		if (bFilter != bAllHit) {
			bAllHit = bFilter;
			return true;
		}
		return false;
	}
}
