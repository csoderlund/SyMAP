package symap.mapper;

import java.util.Comparator;


/**
 * Class <code>HitData</code> holds the data of a hit.
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 */
public abstract class HitData {	
	private static final boolean DEBUG = false;
	private long id;
	private String name;
	private byte repetitive;
	private byte block;
	private double evalue;
	private byte pctid;
	private int start1, end1; 
	private int start2, end2;
	private boolean orient;
	private boolean orient1;
	private boolean orient2;
	private String query_seq, target_seq; 
	private int overlap = -1; 
	protected HitData(long id) {
		this.id = id;
	}

	/**
	 * Creates a new <code>HitData</code> instance.
	 *
	 * @param id a <code>long</code> value
	 * @param name a <code>String</code> value
	 * @param repetitive a <code>boolean</code> value true means filtered out
	 * @param block a <code>boolean</code> value
	 * @param evalue a <code>double</code> value
	 * @param pctid a <code>double</code> value
	 * @param start2 an <code>int</code> value
	 * @param end2 an <code>int</code> value
	 * @param strand an <code>String</code> value
	 */
	protected HitData(long id, String name, String strand, boolean repetitive,
			boolean block, double evalue, double pctid, int start2, int end2,
			String query_seq, String target_seq, int gene_olap) 
	{
		this.id = id;
		this.name = name;
		this.repetitive = repetitive ? (byte)1 : (byte)0;
		this.block = block ? (byte)1 : (byte)0;
		this.evalue = evalue;
		this.pctid = (byte)pctid;
		this.start2 = start2;
		this.end2 = end2;
		this.query_seq = query_seq; 	
		this.target_seq = target_seq;	
		this.overlap = gene_olap;
		
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
			boolean block, double evalue, double pctid, int start1, int end1, 
			int start2, int end2, int overlap,
			String query_seq, String target_seq)
	{
		this(id,name,strand,repetitive,block,evalue,pctid,start2,end2,"","", overlap);
		this.start1 = start1;
		this.end1 = end1;
		this.overlap = overlap;		// mdb added overlap 2/19/08 #150
		this.query_seq = query_seq; // mdb added 4/17/09 #126 - draw hit ribbon
		this.target_seq = target_seq;// mdb added 4/17/09 #126 - draw hit ribbon
	}

	public String toString() 	{ return name; }
	public boolean getOrientation() { return orient; }
	public boolean getOrientation1() { return orient1; }
	public boolean getOrientation2() { return orient2; }
	public long getID() 		{ return id; }
	public String getName() 	{ return name; }
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

	public int getOverlap()    
	{ 

		return overlap; 
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

	public boolean isRepetitiveHit() { return repetitive != 0; }

	public boolean isBlockHit() { return block != 0; }

	public boolean equals(Object obj) {
		return (obj instanceof HitData && ((HitData)obj).id == id);
	}

// mdb unused 12/4/09	
//	public static Comparator<HitData> getIDComparator() {
//		return new Comparator<HitData>() {
//			public int compare(HitData hd1, HitData hd2) {
//				return (int)(hd1.id - hd2.id);
//			}		
//		};
//	}

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
