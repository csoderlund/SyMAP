package symap.mapper;

/**
 * HfilterData a hit filter stores all the data used to communicate
 * between the filter and the mapper.
 * CAS520 remove or makes stubs of all FPC stuff. Change for new hit filter.
 * CAS533 removed reference to the Dotplot hitfilter
 * CAS541 HitFilter=>HfilterData; CAS542 remove listeners, that did nothing
 */
public class HfilterData {
	private static final double ANY_PCTID  = 0;
	private static final double NO_PCTID  = 100;
	
	private boolean bHiPopup=true, bHiNone=true; // Highlight popup, do not highlight; set in setDefaults; CAS543 add, CAS544 change default
	private boolean bHiBlock=false, bHiCset=false, bHi2Gene=false, bHi0Gene=false, bHi1Gene=false;
	
	private boolean bBlock=true; // set in setDefaults;
	private boolean bCset=false, b2Gene=false, b1Gene=false, b0Gene=false, bAllHit=false;
	
	private double pctid;
	private double minPctid=NO_PCTID, maxPctid=ANY_PCTID;
	
	private String hoverText=""; // CAS520 add
	
	public HfilterData() {
		setDefaults();
	}
	protected HfilterData copy(String msg) {
		return new HfilterData(this);
	}
	private HfilterData(HfilterData hf) { // copy
		setChanged(hf, "HfilterData from hf");
	}
	
	protected String getFilterText() { // CAS520 So know what is set when hover in whitespace
		String msg = "";			// SeqHits expects this to start with High if there is highlightening
		if (bHiBlock) 		msg += "High Blocks; ";
		else if (bHiCset) 	msg += "High Sets; ";
		else if (bHi2Gene) 	msg += "High =2 Genes; ";
		else if (bHi1Gene) 	msg += "High =1 Gene; ";
		else if (bHi0Gene) 	msg += "High =0 Genes; ";
		if (!bHiPopup) msg += "No High Popup (or Query); "; // CAS555 query; CAS552 more meaningful with !; remove else
		
		msg += "Show "; // something always shows
		if (bBlock) 	msg += "Block, ";
		if (bCset)	 	msg += "Sets, ";
		if (b2Gene) 	msg += "2 Genes, ";
		if (b1Gene) 	msg += "1 Gene, ";
		if (b0Gene) 	msg += "0 Genes, ";
		if (bAllHit) 	msg += "All, ";
		if (pctid!=0 && pctid!=minPctid) msg += "Id=" + (int) pctid + "%"; // CAS543 add
		
		if (msg.endsWith(", ")) msg = msg.substring(0, msg.length()-2);
		hoverText = 	msg + "\n";
		return hoverText;
	}
	// Query block, set, region CAS520 add for showing synteny from query; b is blocks by default
	public void setForQuery(boolean b, boolean s, boolean r) { // block (default), set, region
		if (s)      {setBlock(true); setCset(true); setHiCset(true); setHiNone(false);}
		else if (r) {setBlock(false); setAllHit(true);}
	}
	// dotplot CAS530 
	public void setForDP(boolean b, boolean r) {
		setBlock(false); setAllHit(false);
		if (b) setBlock(true);
		if (r) setAllHit(true);
	}
	
	private void setDefaults() {
		bHiBlock  = bHiCset = bHi2Gene = bHi0Gene = bHi1Gene = false;		
		bHiPopup = bHiNone = true;
		
		bCset = b2Gene = b0Gene = bAllHit = false; 
		bBlock = true;
		
		pctid=ANY_PCTID;
	}

	// CAS520 was comparing with false for everything instead of the current setting
	// this changes initial settings for dotplot and query table
	public boolean setChanged(HfilterData hf, String msg) {
		boolean changed = false;
		if (setHiBlock(hf.bHiBlock))  	changed = true; 
		if (setHiCset(hf.bHiCset))    	changed = true;
		if (setHi2Gene(hf.bHi2Gene))    changed = true;
		if (setHi1Gene(hf.bHi1Gene))	changed = true;
		if (setHi0Gene(hf.bHi0Gene))	changed = true;
		if (setHiNone(hf.bHiNone))		changed = true;
		if (setHiPopup(hf.bHiPopup))	changed = true;
		
		if (setBlock(hf.bBlock))        changed = true;
		if (setCset(hf.bCset))        	changed = true;
		if (set2Gene(hf.b2Gene)) 		changed = true; 
		if (set1Gene(hf.b1Gene)) 		changed = true; 
		if (set0Gene(hf.b0Gene))		changed = true; 
		if (setAllHit(hf.bAllHit))		changed = true;
		
		if (setPctid(hf.pctid))   		changed = true;
		
		return changed;
	}

	//public interface HitFilterListener {public void update(HfilterData hf);}
	
	/*******************************************************/
// %id
	protected double getPctid() {return pctid;}
	protected boolean setPctid(double score) {
		if (pctid != score) {
			pctid = score;
			return true;
		}
		return false;
	}
	protected double getMinPctid() {return minPctid;}
	protected double getMaxPctid() {return maxPctid;}
	protected void condSetPctid(double hitid) { // set when HfilterData is created
		if (hitid < minPctid) minPctid = hitid;
		if (hitid > maxPctid) maxPctid = hitid;
	}
	
// highs
	protected boolean  isHiPopup() {return bHiPopup;} // CAS543 new
	protected boolean  setHiPopup(boolean filter) {
		if (filter != bHiPopup) {
			bHiPopup = filter;
			return true;
		}
		return false;
	}
	protected boolean  isHiNone() {return bHiNone;}
	protected boolean  setHiNone(boolean filter) {
		if (filter != bHiNone) {
			bHiNone = filter;
			return true;
		}
		return false;
	}
	
	protected boolean  isHiBlock() {return bHiBlock;}
	protected boolean  setHiBlock(boolean filter) { 
		if (filter != bHiBlock) {
			bHiBlock = filter;
			return true;
		}
		return false;
	}
	
	protected boolean   isHiCset() { return bHiCset;}
	protected boolean  setHiCset(boolean bFilter) {
		if (bFilter != bHiCset) {
			bHiCset = bFilter;
			return true;
		}
		return false;
	}  
	protected boolean  isHi2Gene() {return bHi2Gene;}
	protected boolean  setHi2Gene(boolean bFilter) {  
		if (bFilter != bHi2Gene) {
			bHi2Gene = bFilter;
			return true;
		}
		return false;
	}
	protected boolean  isHi1Gene() {return bHi1Gene;}
	protected boolean  setHi1Gene(boolean bFilter) { 
		if (bFilter != bHi1Gene) {
			bHi1Gene = bFilter;
			return true;
		}
		return false;
	}

	protected boolean isHi0Gene() { return bHi0Gene;}
	protected boolean setHi0Gene(boolean bFilter) { 
		if (bFilter != bHi0Gene) {
			bHi0Gene = bFilter;
			return true;
		}
		return false;
	}
// shows
	protected boolean isBlock() {return bBlock;}
	protected boolean setBlock(boolean bFilter) {
		if (bFilter != bBlock) {
			bBlock = bFilter;
			return true;
		}
		return false;
	}
	
	protected boolean isCset() { return bCset;}
	protected boolean setCset(boolean bFilter) {
		if (bFilter != bCset) {
			bCset = bFilter;
			return true;
		}
		return false;
	}
	
	protected boolean is2Gene() {return b2Gene;}
	protected boolean set2Gene(boolean bFilter) {  
		if (bFilter != b2Gene) {
			b2Gene = bFilter;
			return true;
		}
		return false;
	}
	
	protected boolean is1Gene() {return b1Gene;}
	protected boolean set1Gene(boolean bFilter) { 
		if (bFilter != b1Gene) {
			b1Gene = bFilter;
			return true;
		}
		return false;
	}

	protected boolean is0Gene() { return b0Gene;}
	protected boolean set0Gene(boolean bFilter) { 
		if (bFilter != b0Gene) {
			b0Gene = bFilter;
			return true;
		}
		return false;
	}
	
	protected boolean isAllHit() { return bAllHit;}
	protected boolean setAllHit(boolean bFilter) { 
		if (bFilter != bAllHit) {
			bAllHit = bFilter;
			return true;
		}
		return false;
	}
}
