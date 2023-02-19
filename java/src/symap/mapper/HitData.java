package symap.mapper;

import java.util.Comparator;
import symap.Globals;
import symap.closeup.SeqData;
import util.Utilities;

/**
 * CAS531 was abstract so the code went through loops to get data into it via PseudoPseudoData, which is removed
 */
public class HitData {	
	private long id;
	private int hitnum;				// CAS520 add, newly assigned hitnum
	private int blocknum; 			// CAS505 add
	private boolean isBlock;
	private boolean isCollinear;	// CAS520 add
	private byte pctid, pctsim; 	// CAS515 add pctsim and nMerge
	private int nMerge;
	private double corr;			// CAS516 add
	private String tag;				// CAS516 gNbN see MapperPool; CAS520 g(gene_overlap) [c(runnum.runsize)]
	private int collinearSet;	// CAS520 [c(runnum.runsize)] need to toggle highlight
	private int start1, end1; 
	private int start2, end2;
	private boolean isSameOrient; 	// CAS517 if the same
	private boolean isPosOrient1;
	private boolean isPosOrient2;
	private String query_seq, target_seq; // coordinates of hit
	private int overlap = -1; 
	private String chr1, chr2; 		// CAS517 add
	
	// Update HitData PseudoHitData 
	protected HitData(long id, int hitnum, String strand, int block,  double pctid, 
			int start1, int end1, int start2, int end2, int overlap,
			String query_seq, String target_seq, int pctsim, int nMerge, double corr, 
			String tag, String chr1, String chr2)
	{
		this.id = id;
		this.hitnum = hitnum;
		this.blocknum = block;
		this.isBlock = (blocknum>0) ? true : false;
		this.pctid = (byte)pctid;
		this.start2 = start2;
		this.end2 = end2;
		this.query_seq = query_seq; 	// start1, end1
		this.target_seq = target_seq;	// start2, end2
		this.overlap = overlap;
		this.pctsim = (byte) pctsim;
		this.nMerge = nMerge;
		this.corr = corr;
		this.tag = tag;		// Set in MapperPool.setPseudoPseudoData as g(gene_overlap) c(runsize).(runnum)
		this.collinearSet = Utilities.getCollinear(tag); // CAS520
		this.isCollinear = (collinearSet==0) ? false : true; // CAS520
		
		this.start1 = start1;
		this.end1 = end1;
		this.overlap = overlap;		
		this.query_seq = query_seq; 
		this.target_seq = target_seq;
		this.chr1 = chr1;
		this.chr2 = chr2;
		
		if (strand.length() >= 3) { 
			this.isSameOrient = strand.charAt(0) == strand.charAt(2);
			this.isPosOrient1 = (strand.charAt(0) == '+');
			this.isPosOrient2 = (strand.charAt(2) == '+');
		}
		else {
			if (Globals.TRACE) System.err.println("HitData: Invalid strand value '"+strand+"' for hit id="+id);
			this.isSameOrient = true;
		}
	}
	
	public boolean isSameOrient()  { return isSameOrient; }
	public boolean isPosOrient1() { return isPosOrient1; }
	public boolean isPosOrient2() { return isPosOrient2; }
	public long getID() 		{ return id; }
	public int getHitNum() 		{ return hitnum; }
	public int getBlock()		{ return blocknum;}
	public int getStart1() 		{ return start1; } 
	public int getEnd1() 		{ return end1; }   
	public int getLength1() 	{ return Math.abs(end1-start1)+1; } // CAS516 add +1
	public int getStart2() 		{ return start2; }
	public int getEnd2() 		{ return end2; }
	public int getPos2() 		{ return (start2+end2)>>1; }
	public int getLength2() 	{ return Math.abs(end2-start2)+1; } // CAS516 add +1

	public double getPctid() 	{ return (double)pctid; }
	public String getTargetSeq(){ return target_seq; } 
	public String getQuerySeq() { return query_seq; }  
	
	// CAS517 the query and target seq have subhits, and are blank if just one hit
	public String getQueryBounds(){ 
		if (query_seq!=null && query_seq.length()>0) return query_seq; 
		return start1 + ":" + end1;
	} 
	public String getTargetBounds(){ 
		if (target_seq!=null && target_seq.length()>0) return target_seq; 
		return start2 + ":" + end2;
	} 

	public int getStart1(boolean swap) { return (swap ? start2 : start1); }
	public int getEnd1(boolean swap)   { return (swap ? end2 : end1); }
	public int getStart2(boolean swap) { return (swap ? start1 : start2); }
	public int getEnd2(boolean swap)   { return (swap ? end1 : end2); }
	
	public void normalizeCoords() {
		if (end1 < start1) {
			int temp = end1;
			end1 = start1;
			start1 = temp;
		}
		if (end2 < start2) {
			int temp = end2;
			end2 = start2;
			start2 = temp;
		}
	}

	public boolean isBlock() 	{ return isBlock; }
	public boolean isSet() 		{ return isCollinear; } // CAS520 add these 4 new fo;ters
	public boolean isGene() 	{ return (overlap>0); } 
	public boolean is2Gene() 	{ return (overlap==2); } 
	public boolean is1Gene() 	{ return (overlap==1); } 
	public boolean is0Gene()  { return (overlap==0); } 
	
	public int getCollinearSet() {return collinearSet;} // CAS520 add 
	
	public boolean equals(Object obj) {
		return (obj instanceof HitData && ((HitData)obj).id == id);
	}

	public static Comparator<HitData> getPseudoPositionComparator() {
		return new Comparator<HitData>() {
			public int compare(HitData hd1, HitData hd2) {
				int d = hd1.start2 - hd2.start2; 
				if (d == 0)
					d = hd1.end2 - hd2.end2; 
				return d;
			}
		};
	}
	// CAS517 add for coloring hit line according to directions
	public String getOrients() { 
		String x = (isPosOrient1) ? "+" : "-";
		String y = (isPosOrient2) ? "+" : "-";
		return x+y;
	}
	/* hit data for Closeup (via PseudoPseudoData.toString() */
	public String getHitData() {
		String msg =  " Hit #" + hitnum + "\n";
	
		if (nMerge>0) msg += "Avg";
		msg += " %Id=" + pctid;
		if (pctsim>0) msg += " %Sim=" + pctsim;
		if (nMerge>0) msg += " of " + nMerge + " hits ";
		return msg;
	}
	// called for CloseUp Align
	public String toString() 	{ 
		String msg="";
		if (nMerge>0) msg += " Avg";
		msg += " %Id=" + pctid;
		if (pctsim>0) msg += " %Sim=" + pctsim;
		if (nMerge>0) msg += " of " + nMerge + " sub-hits";
		return "Hit #" + hitnum + msg; 
	}
	public String getName()		{ return "Hit #" + hitnum;}
	/********************************************************
	 * CAS512 left/right->start:end; CAS516 add Inv, tag CAS517 puts track1 info before track2
	 */
	public String createHover(boolean s1LTs2) {
		String x = (corr<0) ? " Inv" : "";
		String o = (isPosOrient1==isPosOrient2) ? "(=)" : "(!=)"; // CAS517x
		String msg =  "Block #" + getBlock() + x + "    Hit #" + hitnum + " " + o + " " + tag + "\n"; 
		
		if (nMerge>0) msg += "Avg";
		msg += " %Id=" + pctid;
		if (pctsim>0) msg += " %Sim=" + pctsim;
		if (nMerge>0) msg += " of " + nMerge + " sub-hits";
		
		String msg1 = chr1 + " " + SeqData.coordsStr(isPosOrient1, start1, end1);
		String msg2 = chr2 + " " + SeqData.coordsStr(isPosOrient2, start2, end2);
		String coords = s1LTs2 ? (msg1+"\n"+msg2) : (msg2+"\n"+msg1);
		
		return  msg + "\n\n" + coords;
	}
}
