package symap.mapper;

import java.util.Comparator;
import symap.Globals;
import symap.sequence.Sequence;
import util.Utilities;

/**
 * Represents one Hit
 * CAS531 was abstract so the code went through loops to get data into it via PseudoPseudoData, which is removed
 */
public class HitData {	
	private Mapper mapper;			// CAS544 add for way back to other data
	private long idx;
	private int hitnum;				// CAS520 add, newly assigned hitnum
	private byte pctid, pctsim; 	// CAS515 add pctsim and nMerge
	private int covScore;			// CAS540 best coverage
	private int nMerge;
	private String htype;			// CAS546 add htype (EE, EI, etc)
	private int geneOlp = -1; 
	private int annot1_idx, annot2_idx; // CAS543 add
	private int start1, end1, start2, end2;
	private boolean isSameOrient, isPosOrient1, isPosOrient2; 	// CAS517 if the same
	private String query_seq, target_seq; // coordinates of hit
	
	private int collinearSet;		// CAS520 [c(runnum.runsize)] need to toggle highlight
	private int blocknum; 			// CAS505 add
	private double corr;			// CAS516 add
	
	private boolean isBlock;		// set on init
	private boolean isCollinear;	// set on init; CAS520 add
	
	private boolean isPopup=false;		// set when popup; CAS543 add
	private boolean isConserved=false;	// set on conserved seqFilter, same as geneOlap=2 if only 2 tracks; CAS545 add 
	
	private String hitTag;			// CAS516 gNbN see MapperPool; CAS520 g(gene_overlap) [c(runnum.runsize)]
	private String chr1, chr2; 		// CAS517 add
	
	// MapperPool.setSeqHitData populates, puts in array for SeqHits, where each HitData is associated with a DrawHit
	protected HitData(Mapper mapper, long id, int hitnum, 
			  double pctid, int pctsim, int nMerge, int covScore, String htype, int overlap,
			  int annot1_idx, int annot2_idx,
			  String strand, int start1, int end1, int start2, int end2, String query_seq, String target_seq,   
			  int runnum, int runsize, int block, double corr, String chr1, String chr2)
	{
		this.mapper = mapper;
		this.idx = id;
		this.hitnum = hitnum;
		
		this.pctid = (byte)pctid;
		this.pctsim = (byte) pctsim;
		this.nMerge = nMerge;
		this.covScore = covScore;
		this.htype = htype;
		this.geneOlp = overlap;
		
		this.annot1_idx = annot1_idx;
		this.annot2_idx = annot2_idx;
		
		if (strand.length() >= 3) { 
			this.isSameOrient = strand.charAt(0) == strand.charAt(2);
			this.isPosOrient1 = (strand.charAt(0) == '+');
			this.isPosOrient2 = (strand.charAt(2) == '+');
		}
		else {
			if (Globals.DEBUG) System.err.println("HitData: Invalid strand value '"+strand+"' for hit id="+id);
			this.isSameOrient = true;
		}
		this.start1 = start1;
		this.end1 = end1;
		this.start2 = start2;
		this.end2 = end2;
		this.query_seq = query_seq; 	// start1, end1
		this.target_seq = target_seq;	// start2, end2
		
		this.collinearSet = runnum; // CAS520 add; CAS543 move form MapperPool
		this.isCollinear = (collinearSet==0) ? false : true; 
		
		hitTag    = (htype.equals("0") || htype.equals("")) ? ("g" + geneOlp) : htype;  // CAS546 add htype
		if (runsize>0 && runnum>0) hitTag += " c" + runsize + "." + runnum; 
		else if (runsize>0)        hitTag += " c" + runsize;			    // parsed in Utilities.isCollinear
		
		this.blocknum = block;
		this.corr = corr;
		this.isBlock = (blocknum>0) ? true : false;
		
		this.chr1 = chr1;
		this.chr2 = chr2;	
	}
	
	public boolean isSameOrient()  { return isSameOrient; }
	public boolean isPosOrient1() { return isPosOrient1; }
	public boolean isPosOrient2() { return isPosOrient2; }
	public long getID() 		{ return idx; }
	public String getAnnots()	{ return annot1_idx + " " + annot2_idx;} // CAS543 added for trace
	public int getAnnot1() 		{ return annot1_idx;}
	public int getAnnot2() 		{ return annot2_idx;}
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
	
	// for hit popup: the query and target seq have subhits, and are blank if just one hit; CAS517 add
	public String getQuerySubhits(){ 
		if (query_seq!=null && query_seq.length()>0) return query_seq; 
		return start1 + ":" + end1;
	} 
	public String getTargetSubhits(){ 
		if (target_seq!=null && target_seq.length()>0) return target_seq; 
		return start2 + ":" + end2;
	}
	// for annotation popup: CAS548 was full list of subhits; now coords for both sides
	public String getCoordsForGenePopup(boolean isQuery, String tag) { 
		
		String tag1 = mapper.getGeneNum1(annot1_idx);
		String tag2 = mapper.getGeneNum2(annot2_idx);
		String otherTag = (tag.equals(tag1)) ? tag2 : tag1;
		
		int l = Math.max(tag1.length(), tag2.length());
		String fmt = "%-" + l + "s" + " %s";
		String xtag1 = (isQuery)  ? tag : otherTag;
		String xtag2 = (!isQuery) ? tag : otherTag;
		
		String msg1 = String.format(fmt, xtag1, Utilities.coordsStr(isPosOrient1, start1, end1)); 
		String msg2 = String.format(fmt, xtag2, Utilities.coordsStr(isPosOrient2, start2, end2)); 
	
		String coords = isQuery ? (msg1+"\n" + msg2) : (msg2+ "\n"+ msg1);
		return coords;
	} 
	public String getMinorForGenePopup(boolean isQuery, int annotIdx) {
		String d = (annotIdx!=annot1_idx && annotIdx!=annot2_idx) ? Globals.minorAnno : "";
		return d;
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
	public boolean isCset() 	{ return isCollinear; } 
	public boolean isPopup()	{ return isPopup;}
	public boolean isConserved(){ return isConserved;}
	
	public boolean isGene() 	{ return (geneOlp>0); } 
	public boolean is2Gene() 	{ return (geneOlp==2); } 
	public boolean is1Gene() 	{ return (geneOlp==1); } 
	public boolean is0Gene()  	{ return (geneOlp==0); } 
	public boolean isExon()		{ return htype.equals("EE");}
	public boolean isIntron()	{ return htype.contains("I");}
	
	public void setIsPopup(boolean b) {// CAS543 add
		isPopup=b;
		Sequence s1 = (Sequence) mapper.getTrack1();// CAS545 highlight gene also
		Sequence s2 = (Sequence) mapper.getTrack2();
		s1.setHighforHit(annot1_idx, annot2_idx, b); 
		s2.setHighforHit(annot1_idx, annot2_idx, b); 
	} 
	public void setIsConserved(boolean b) {	// CAS545 add; SeqPool sets true, SeqHits sets false
		isConserved=b;
	
		Sequence s1 = (Sequence) mapper.getTrack1();
		Sequence s2 = (Sequence) mapper.getTrack2();
			
		s1.setConservedforHit(annot1_idx, annot2_idx, b); 
		s2.setConservedforHit(annot1_idx, annot2_idx, b); 
	}
	
	public int getCollinearSet() {return collinearSet;} // CAS520 add 
	
	public boolean equals(Object obj) {
		return (obj instanceof HitData && ((HitData)obj).idx == idx);
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
	
	public String toString() 	{ // not used anywhere 
		String msg =  " Hit #" + hitnum + "\n";
		
		if (nMerge>0) msg += "Approx";
		msg += " %Id=" + pctid;
		if (pctsim>0) msg += " %Sim=" + pctsim;
		if (covScore>0) msg += " " + String.format("Cov=%,d", covScore);
		if (nMerge>0) msg += " of " + nMerge + " hits ";
		return msg;
	}
	public String getName()		{ return "Hit #" + hitnum;}
	/********************************************************
	 * CAS512 left/right->start:end; CAS516 add Inv, tag CAS517 puts track1 info before track2
	 * Called from SeqHits.popupDesc (top of popup) and SeqHits.DrawHit (Hover)
	 */
	public String createHover(boolean s1LTs2) {
		String x = (corr<0) ? " Inv" : "";
		String o = (isPosOrient1==isPosOrient2) ? "(=)" : "(!=)"; // CAS517x
		String msg =  "Block #" + getBlock() + x + "  Hit #" + hitnum + " " + o + " " + hitTag + "\n"; 
		
		String n = (nMerge>0) ? "#Subhits=" + nMerge + "  " : "#Subhit=1  "; // CAS548 added '#'
		msg += n;
		String op = (nMerge>0) ? "~" : "";
		msg +=  "Id=" + op + pctid  + "%  ";
		msg +=  "Sim="+ op + pctsim + "%  ";
		msg +=  String.format("Cov=%,dbp", covScore); // CAS548 add bp; Cov is merged hits, remove ~
		
		String msg1 =  Utilities.coordsStr(isPosOrient1, start1, end1);  // CAS548 rm chr
		String msg2 =  Utilities.coordsStr(isPosOrient2, start2, end2); 
		String L="L ", R="R ";
		String coords = s1LTs2 ? (L + msg1+"\n"+R+ msg2) : (L +msg2+ "\n"+ R + msg1);// CAS548 add L/R
		
		return  msg + "\n\n" + coords;
	}
	
}
