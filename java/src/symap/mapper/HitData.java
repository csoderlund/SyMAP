package symap.mapper;

import java.util.Comparator;
import util.Utilities;

/**
 * Holds the data of a hit.
 */
public abstract class HitData {	
	private static final boolean DEBUG = false;
	private long id;
	private String name;
	private byte repetitive;
	private int blocknum; // CAS505 add
	private byte isBlock;
	private double evalue;
	private byte pctid, pctsim; // CAS515 add pctsim and nMerge
	private int nMerge;
	private int start1, end1; 
	private int start2, end2;
	private boolean orient;
	private boolean orient1;
	private boolean orient2;
	private String query_seq, target_seq; // coordinates of hit
	private int overlap = -1; 
	protected HitData(long id) {
		this.id = id;
	}

	protected HitData(long id, String name, String strand, boolean repetitive,
			int blocknum, double evalue, double pctid, int start2, int end2,
			String query_seq, String target_seq, int gene_olap, int pctsim, int nMerge) 
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
		this.query_seq = query_seq; 	
		this.target_seq = target_seq;	
		this.overlap = gene_olap;
		this.pctsim = (byte) pctsim;
		this.nMerge = nMerge;
		
		if (strand.length() >= 3) { 
			this.orient = strand.charAt(0) == strand.charAt(2);
			this.orient1 = (strand.charAt(0) == '+');
			this.orient2 = (strand.charAt(2) == '+');
		}
		else {
			if (DEBUG) System.err.println("Invalid strand value '"+strand+"' for hit id="+id);
			this.orient = true;
		}
	}
	
	protected HitData(long id, String name, String strand, boolean repetitive,
			int block, double evalue, double pctid, int start1, int end1, 
			int start2, int end2, int overlap,
			String query_seq, String target_seq, int pctsim, int nMerge)
	{
		this(id,name,strand,repetitive,block,evalue,pctid,start2,end2,"","", overlap, pctsim, nMerge);
		this.start1 = start1;
		this.end1 = end1;
		this.overlap = overlap;		
		this.query_seq = query_seq; 
		this.target_seq = target_seq;
	}
	// CAS515 add next three hit Help hover
	public String getLengths() {
		return String.format("Len1=%,d Len2=%,d", getLength1(), getLength2());
	}
	public String getCoords() {
		return Utilities.coordsStr(1, orient1, start1, end1) + "\n" 
	         + Utilities.coordsStr(2, orient2, start2, end2);
	}
	public String getHitData() {
		String msg = " Hit #" + id;
		if (nMerge>0) msg += "\nAvg";
		msg += " %Id=" + pctid;
		if (pctsim>0) msg += " %Sim=" + pctsim;
		if (nMerge>0) msg += " of " + nMerge + " hits";
		return msg;
	}
	public String toString() 	{ return name; }
	public boolean getOrientation() { return orient; }
	public boolean getOrientation1() { return orient1; }
	public boolean getOrientation2() { return orient2; }
	public long getID() 		{ return id; }
	public String getName() 	{ return name; }
	public int getBlock()	{ return blocknum;}
	public int getStart1() 		{ return start1; } 
	public int getEnd1() 		{ return end1; }   
	public int getLength1() 	{ return Math.abs(end1-start1); } 
	public int getStart2() 		{ return start2; }
	public int getEnd2() 		{ return end2; }
	public int getPos2() 		{ return (start2+end2)>>1; }
	public int getLength2() 	{ return Math.abs(end2-start2); } 
	public double getEvalue() 	{ return evalue; }
	public double getPctid() 	{ return (double)pctid; }
	public String getTargetSeq(){ return target_seq; } 
	public String getQuerySeq() { return query_seq; }  

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
}
