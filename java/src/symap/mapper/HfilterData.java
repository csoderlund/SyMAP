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
	
	private boolean bBlock=true; 
	private boolean bBlockAnd=true, bBlockOr=false, bBlockOnly=false; 
	private boolean bCset=false, b2Gene=false, b1Gene=false, b0Gene=false;
	private boolean bAll=false;	// when true, overrides all else; 
	
	private boolean bSignEQ=false, bSignNE=false, bSignIGN=true; // CAS588 add sign
	private int blkNum=0;
	private double pctid;
	private double minPctid=NO_PCTID, maxPctid=ANY_PCTID;
	
	public HfilterData() {
		bHiBlock  = bHiCset = bHi2Gene = bHi0Gene = bHi1Gene = false;		
		bHiPopup = bHiNone = true;
		
		bCset = b2Gene = b1Gene = b0Gene = false;
		bSignEQ = bSignNE = false; bSignIGN = true; 
		bBlock = true;    bBlockAnd = true; 
		bBlockOr = false; bBlockOnly=false;
		bAll = false;					
		
		blkNum=0;
		pctid=ANY_PCTID;
	}
	protected HfilterData copy(String msg) {
		return new HfilterData(this);
	}
	private HfilterData(HfilterData hf) { // copy
		setChanged(hf, "HfilterData from hf");
	}
	
	protected String getFilterText() { // called in Mapper.getHelpText for information box
		String msg="";	
		if (pctid!=0 && pctid!=minPctid) msg = addMsg(msg, " Id=" + (int) pctid + "%"); 
		if (bSignEQ) msg = addMsg(msg, " Sign==");
		if (bSignNE) msg = addMsg(msg, " Sign!=");
		
		if (bBlockOnly) msg = addMsg(msg, " Block#" + blkNum); 
		else {	
			String p="", x="";
			if (bBlock) {
				msg = addMsg(msg, " Block");
				p = (bBlockAnd) ? " (and)" : " (or)";
			}
			if (bCset)	x = " CoSets";
			if (b2Gene) x = addMsg(x, " g2");
			if (b1Gene) x = addMsg(x, " g1");
			if (b0Gene) x = addMsg(x, " g0");	
			if (!p.equals("") && !x.equals("")) msg = msg + p + x; // blocks (and) something
			else if (!x.equals("")) msg = addMsg(msg, x); // no blocks, so...
		}
		if (bAll) msg = addMsg(msg, " All");
		else if (msg.equals("")) msg = " All";
		
		if (bHiBlock) 		msg += "\nHigh Blocks ";	// There will always be a show; put high last
		else if (bHiCset) 	msg += "\nHigh CoSets "; 
		else if (bHi2Gene) 	msg += "\nHigh g2 ";
		else if (bHi1Gene) 	msg += "\nHigh g1 ";
		else if (bHi0Gene) 	msg += "\nHigh g0 ";
		
		if (!bHiPopup) 		msg += "\nNo High Popup "; // query; 
		
		return "Show" + msg + "\n";
	}
	private String addMsg(String msg, String add) {
		if (msg.equals("")) return add;
		else return msg + "," + add;
	}
	// Query block, set, region for showing synteny from query; b is blocks by default
	public void setForQuery(int blockNum, boolean s, boolean r) { // block, set, region
		setBlockOnly(false); setBlock(false); setCset(false); setHiNone(false);
		
		if (blockNum>0) {setBlockOnly(true); setBlockNum(blockNum);} 	
		else if (s)     {setCset(true); setHiCset(true); } 				
		else if (r)     {setAll(true);} 	// CAS588 was highlight blocks on region						
	}
	// dotplot 
	public void setForDP(int blockNum) {
		setBlockOnly(false); setBlock(false); setCset(false); setHiNone(false);
		
		if (blockNum>0) {setBlockOnly(true); setBlockNum(blockNum);}
		else            {setAll(true);} 	// CAS588 was highlight blocks on region
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
		if (setBlockOnly(hf.bBlockOnly)) changed = true;
		
		if (setCset(hf.bCset))        	changed = true;
		if (set2Gene(hf.b2Gene)) 		changed = true; 
		if (set1Gene(hf.b1Gene)) 		changed = true; 
		if (set0Gene(hf.b0Gene))		changed = true; 
		if (setAll(hf.bAll))			changed = true; 
		
		if (setSignEQ(hf.bSignEQ)) 		changed = true; 
		if (setSignNE(hf.bSignNE))		changed = true; 
		if (setSignIGN(hf.bSignIGN))		changed = true; 
		if (setPctid(hf.pctid))   		changed = true;
		if (setBlockNum(hf.blkNum)) 	 changed = true;
		
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
	
	protected boolean isBlockOnly() {return bBlockOnly;}
	protected boolean setBlockOnly(boolean bFilter) {
		if (bFilter != bBlockOnly) {
			bBlockOnly = bFilter;
			return true;
		}
		return false;
	}
	protected int getBlock() {return blkNum;}
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
	
	
	protected boolean isAll() {return bAll;} 
	protected boolean setAll(boolean bFilter) { 
		if (bFilter != bAll) {
			bAll = bFilter;
			return true;
		}
		return false;
	}
	
	protected boolean isSignEQ() {return bSignEQ;}
	protected boolean setSignEQ(boolean bFilter) { 
		if (bFilter != bSignEQ) {
			bSignEQ = bFilter;
			return true;
		}
		return false;
	}
	protected boolean isSignNE() {return bSignNE;}
	protected boolean setSignNE(boolean bFilter) { 
		if (bFilter != bSignNE) {
			bSignNE = bFilter;
			return true;
		}
		return false;
	}
	protected boolean isSignIGN() {return bSignIGN;}
	protected boolean setSignIGN(boolean bFilter) { 
		if (bFilter != bSignIGN) {
			bSignIGN = bFilter;
			return true;
		}
		return false;
	}
}
