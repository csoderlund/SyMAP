package symap.mapper;

/**
 * HfilterData a hit filter stores all the data used to communicate
 * between the filter and the mapper.
 */
public class HfilterData {
	private static final double ANY_PCTID  = 0;
	private static final double NO_PCTID  = 100;
	
	private boolean bHiPopup=true; // Highlight popup, do not highlight; set in setDefaults; 
	private boolean bHiBlock=false, bHiCset=false, bHi2Gene=false, bHi0Gene=false, bHi1Gene=false, bHiNone=true;
	
	private boolean bBlock=true; // set in setDefaults;
	private boolean bBlockAnd=true, bBlockOr=false, bBlockOnly=false; // CAS567 add only 
	private boolean bCset=false, b2Gene=false, b1Gene=false, b0Gene=false;
	private boolean bAll=false;	// when true, overrides all else; CAS567
	
	private int blkNum=0;
	private double pctid;
	private double minPctid=NO_PCTID, maxPctid=ANY_PCTID;
	
	private String hoverText=""; 
	
	public HfilterData() {
		setDefaults();
	}
	protected HfilterData copy(String msg) {
		return new HfilterData(this);
	}
	private HfilterData(HfilterData hf) { // copy
		setChanged(hf, "HfilterData from hf");
	}
	
	protected String getFilterText() { // know what is set when hover in whitespace
		String msg = "";			// SeqHits expects this to start with High if there is highlightening
		if (bHiBlock) 		msg += "High Blocks; ";
		else if (bHiCset) 	msg += "High CoSets; "; 
		else if (bHi2Gene) 	msg += "High =2 Genes; ";
		else if (bHi1Gene) 	msg += "High =1 Gene; ";
		else if (bHi0Gene) 	msg += "High =0 Genes; ";
		if (!bHiPopup) 		msg += "No High Popup (or Query); "; // query; 
		
		msg += "Show "; // something always shows
		if (pctid!=0 && pctid!=minPctid) msg += "Id=" + (int) pctid + "%"; 
		
		if (bBlock && bBlockOnly) msg += "Block#" + blkNum; // CAS567 add
		else {
			String x = " ";									// CAS570 spacing was wrong
			if (bBlock) {
				msg += "Block";
				x = (bBlockAnd) ? " and " : " or ";
			}
			if (bCset)	 	msg += x+"CoSet, ";
			
			if (b2Gene) 	msg += x+"2 Genes, ";
			if (b1Gene) 	msg += x+"1 Gene, ";
			if (b0Gene) 	msg += x+"0 Genes, ";
			if (bAll)		msg += x+"All, ";			// CAS567 add
			if (msg.endsWith("Show ")) msg += "all";
			
			if (msg.endsWith(", ")) msg = msg.substring(0, msg.length()-2);
		}
		hoverText = 	msg + "\n";
		return hoverText;
	}
	// Query block, set, region for showing synteny from query; b is blocks by default
	public void setForQuery(boolean b, boolean s, boolean r) { // block (default), set, region
		if (s)      {setBlock(true); setCset(true); setHiCset(true); setHiNone(false);}
		else if (r) {setBlock(false);}
	}
	// dotplot 
	public void setForDP(boolean b) {
		if (b) setBlock(true);
		else setBlock(false); 
	}
	
	private void setDefaults() {
		bHiBlock  = bHiCset = bHi2Gene = bHi0Gene = bHi1Gene = false;		
		bHiPopup = bHiNone = true;
		
		bCset = b2Gene = b1Gene = b0Gene =false; 
		bBlock = true; bBlockAnd = true; bBlockOr = false; bBlockOnly=false;
		bAll = false;					// CAS567 add
		
		blkNum=0;
		pctid=ANY_PCTID;
	}

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
		if (setBlockAnd(hf.bBlockAnd))  changed = true;
		if (setBlockOr(hf.bBlockOr))    changed = true;
		if (setBlockOnly(hf.bBlockOnly)) changed = true;// CAS567 add
		if (setBlockNum(hf.blkNum)) 	 changed = true;// CAS567 add
		
		if (setCset(hf.bCset))        	changed = true;
		if (set2Gene(hf.b2Gene)) 		changed = true; 
		if (set1Gene(hf.b1Gene)) 		changed = true; 
		if (set0Gene(hf.b0Gene))		changed = true; 
		
		if (setAll(hf.bAll))			changed = true; // CAS567 add
		
		if (setPctid(hf.pctid))   		changed = true;
		
		return changed;
	}

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
	protected boolean  isHiPopup() {return bHiPopup;} 
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
	
	protected boolean  isHiCset() {return bHiCset;}
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
	
	protected boolean isBlockAnd() {return bBlockAnd;}
	protected boolean setBlockAnd(boolean bFilter) {
		if (bFilter != bBlockAnd) {
			bBlockAnd = bFilter;
			return true;
		}
		return false;
	}
	protected boolean isBlockOr() {return bBlockOr;}
	protected boolean setBlockOr(boolean bFilter) {
		if (bFilter != bBlockOr) {
			bBlockOr = bFilter;
			return true;
		}
		return false;
	}
	
	protected boolean isBlockOnly() {return bBlockOnly;}// CAS567 add
	protected boolean setBlockOnly(boolean bFilter) {
		if (bFilter != bBlockOnly) {
			bBlockOnly = bFilter;
			return true;
		}
		return false;
	}
	protected int getBlock() {return blkNum;}// CAS567 add
	protected boolean setBlockNum(int bn) { 
		if (blkNum != bn) {
			blkNum = bn;
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

	protected boolean is0Gene() {return b0Gene;}
	protected boolean set0Gene(boolean bFilter) { 
		if (bFilter != b0Gene) {
			b0Gene = bFilter;
			return true;
		}
		return false;
	}
	
	protected boolean isAll() {return bAll;} // CAS567
	protected boolean setAll(boolean bFilter) { 
		if (bFilter != bAll) {
			bAll = bFilter;
			return true;
		}
		return false;
	}
}
