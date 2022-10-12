package symap.mapper;

import java.util.Comparator;
import util.Utilities;

/**
 * Holds the data of a hit. Extended by PseudoMarkerData, PseudoBESData, PseudoHitData
 */
public abstract class HitData {	
	private long id;
	private String name;
	private byte repetitive;
	private int blocknum; // CAS505 add
	private byte isBlock;
	private double evalue;
	private byte pctid, pctsim; // CAS515 add pctsim and nMerge
	private int nMerge;
	private double corr;		// CAS516 add
	private String tag;			// CAS516 g(gene_overlap)r(runsize) see MapperPool
	private int start1, end1; 
	private int start2, end2;
	private boolean isSameOrient; // CAS517 if the same
	private boolean isPosOrient1;
	private boolean isPosOrient2;
	private String query_seq, target_seq; // coordinates of hit
	private int overlap = -1; 
	private String chr1, chr2; 	// CAS517 add
	
	// SeqSeq: HitData below, PseudoPseudoData.PseudoHitData, MapperPool.setPseudoPseudoData reads DB
	protected HitData(long id, String name, String strand, boolean repetitive,
			int blocknum, double evalue, double pctid, int start2, int end2,
			String query_seq, String target_seq, int gene_olap, int pctsim, int nMerge, 
			double corr, String tag) 
	{
		this.id = id;
		this.name = name;
		this.repetitive = repetitive ? (byte)1 : (byte)0;
		this.blocknum = blocknum;
		this.isBlock = (blocknum>0) ? (byte)1 : (byte)0;
		this.evalue = evalue;
		this.pctid = (byte)pctid;
		this.start2 = start2;
		this.end2 = end2;
		this.query_seq = query_seq; 	// start1, end1
		this.target_seq = target_seq;	// start2, end2
		this.overlap = gene_olap;
		this.pctsim = (byte) pctsim;
		this.nMerge = nMerge;
		this.corr = corr;
		this.tag = tag;
		chr1="1";
		chr2="2";
		
		if (strand.length() >= 3) { 
			this.isSameOrient = strand.charAt(0) == strand.charAt(2);
			this.isPosOrient1 = (strand.charAt(0) == '+');
			this.isPosOrient2 = (strand.charAt(2) == '+');
		}
		else {
			if (symap.SyMAP.TRACE) System.err.println("Invalid strand value '"+strand+"' for hit id="+id);
			this.isSameOrient = true;
		}
	}
	// PseudoHitData
	protected HitData(long id, String name, String strand, boolean repetitive,
			int block, double evalue, double pctid, int start1, int end1, 
			int start2, int end2, int overlap,
			String query_seq, String target_seq, int pctsim, int nMerge, double corr, String tag, String chr1, String chr2)
	{
		this(id,name,strand,repetitive,block,evalue,pctid,start2,end2,"","", overlap, pctsim, nMerge, corr, tag);
		
		this.start1 = start1;
		this.end1 = end1;
		this.overlap = overlap;		
		this.query_seq = query_seq; 
		this.target_seq = target_seq;
		this.chr1 = chr1;
		this.chr2 = chr2;
	}
	public String toString() 	{ return name; }
	public boolean isSameOrient()  { return isSameOrient; }
	public boolean isPosOrient1() { return isPosOrient1; }
	public boolean isPosOrient2() { return isPosOrient2; }
	public long getID() 		{ return id; }
	public String getName() 	{ return name; }
	public int getBlock()		{ return blocknum;}
	public int getStart1() 		{ return start1; } 
	public int getEnd1() 		{ return end1; }   
	public int getLength1() 	{ return Math.abs(end1-start1)+1; } // CAS516 add +1
	public int getStart2() 		{ return start2; }
	public int getEnd2() 		{ return end2; }
	public int getPos2() 		{ return (start2+end2)>>1; }
	public int getLength2() 	{ return Math.abs(end2-start2)+1; } // CAS516 add +1
	public double getEvalue() 	{ return evalue; }
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
	
	public int getOverlap()    { return overlap; }    
	
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

	public boolean isRepetitiveHit() { return repetitive != 0; }

	public boolean isBlockHit() { return isBlock != 0; }

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
		String msg =  " Hit #" + id + "\n";
	
		if (nMerge>0) msg += "Avg";
		msg += " %Id=" + pctid;
		if (pctsim>0) msg += " %Sim=" + pctsim;
		if (nMerge>0) msg += " of " + nMerge + " hits ";
		return msg;
	}
	/********************************************************
	 * CAS512 left/right->start:end; CAS516 add Inv, tag CAS517 puts track1 info before track2
	 */
	public String createHover(boolean s1LTs2) {
		String x = (corr<0) ? " Inv" : "";
		String o = (isPosOrient1==isPosOrient2) ? "(=)" : "(!=)"; // CAS517x
		String msg =  "Block #" + getBlock() + x + "    Hit #" + id + "  " + o + "   " + tag + "\n"; 
		
		if (nMerge>0) msg += "Avg";
		msg += " %Id=" + pctid;
		if (pctsim>0) msg += " %Sim=" + pctsim;
		if (nMerge>0) msg += " of " + nMerge + " merged hits";
		
		String msg1 = Utilities.coordsHit(chr1, isPosOrient1, start1, end1, "\n");
		String msg2 = Utilities.coordsHit(chr2, isPosOrient2, start2, end2, "\n");
		String coords = s1LTs2 ? (msg1+"\n"+msg2) : (msg2+"\n"+msg1);
		
		return  msg + "\n\n" + coords;
	}
}
