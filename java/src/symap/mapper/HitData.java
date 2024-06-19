package symap.mapper;

import java.util.Comparator;

import symap.Globals;
import symap.sequence.Sequence;
import util.ErrorReport;
import util.Utilities;

/**
 * Represents one Clustered Hit
 * CAS531 was abstract so the code went through loops to get data into it via PseudoPseudoData, which is removed
 */
public class HitData {	
	public static int cntMerge=0, cntTotal=0, cntMergeSH=0, cntTotalSH=0; // CAS551 -dd see reduction in subhits
	
	private Mapper mapper;			// CAS544 add for way back to other data
	private int idx;				// CAS551 was long
	private int hitnum;				// CAS520 add, newly assigned hitnum
	private byte pctid, pctsim; 	// CAS515 add pctsim and nMerge
	private int covScore;			// CAS540 best coverage
	private int nMerge;
	private int geneOlp = -1; 
	private boolean isPosOrient1, isPosOrient2; 	
	private int 	annot1_idx, annot2_idx; // CAS543 add
	private String 	query_seq, target_seq; // coordinates of hit
	
	protected int start1, end1, mid1, start2, end2, mid2;	// 1=q, 2=t; CAS550 add mid for paintComponent
	protected String qMergeSH, tMergeSH;					// CAS551 merged for faster drawing in SeqHits.DrawHit
	
	private int collinearSet;		// CAS520 [c(runnum.runsize)] need to toggle highlight
	private int blocknum; 			// CAS505 add
	
	private boolean isBlock;		// set on init
	private boolean isCollinear;	// set on init; CAS520 add
	
	private boolean isPopup=false;		// set when popup; CAS543 add
	private boolean isHighHitg2=false;	// set on conserved seqFilter, same as geneOlap=2 if only 2 tracks; CAS545 add 
	
	protected String hitGeneTag;			// g(gene_overlap) or htype (EE) ; CAS516 gNbN see MapperPool; 
	private String   hitTag; 
	
	// MapperPool.setSeqHitData populates, puts in array for SeqHits, where each HitData is associated with a DrawHit
	protected HitData(Mapper mapper, int id, int hitnum, 
			  double pctid, int pctsim, int nMerge, int covScore, String htype, int overlap,
			  int annot1_idx, int annot2_idx,
			  String strand, int start1, int end1, int start2, int end2, String query_seq, String target_seq,   
			  int runnum, int runsize, int block, 
			  double corr, String chr1, String chr2) // chr1/2 not used
	{
		this.mapper = mapper;
		this.idx = id;
		this.hitnum = hitnum;
		
		this.pctid = (byte)pctid;
		this.pctsim = (byte) pctsim;
		this.nMerge = nMerge;
		this.covScore = covScore;
		this.geneOlp = overlap;
		
		this.annot1_idx = annot1_idx;
		this.annot2_idx = annot2_idx;
		
		if (strand.length() >= 3) { 
			this.isPosOrient1 = (strand.charAt(0) == '+');
			this.isPosOrient2 = (strand.charAt(2) == '+');
		}
		else if (Globals.DEBUG) System.err.println("HitData: Invalid strand value '"+strand+"' for hit id="+id);
			
		this.start1 = start1;
		this.end1 = end1;
		this.start2 = start2;
		this.end2 = end2;
		this.mid1 = (start1+end1) >>1;
		this.mid2 = (start2+end2) >>1;
		
		this.query_seq = query_seq; 	// start1: end1, ...
		this.target_seq = target_seq;	// start2: end2
		qMergeSH = calcMergeHits(query_seq, start1, end1);
		tMergeSH = calcMergeHits(target_seq, start2, end2);
		
		this.collinearSet = runnum; // CAS520 add; CAS543 move form MapperPool
		this.isCollinear = (collinearSet==0) ? false : true; 
		
		this.blocknum = block;
		this.isBlock = (blocknum>0) ? true : false;
		
		String gg = (htype.equals("0") || htype.equals("")) ? (" g" + geneOlp) : (" " +htype); 
		String iv = (corr<0) ? " Inv   " : "   "; // Only happens rarely with algo1
		String co = (runsize>0 && runnum>0) ? (" c" + runsize + "." + runnum) : "";
		hitTag = "Hit #" + hitnum + gg + "   Block #" + block + iv + co;
		
		hitGeneTag = "Hit #" + hitnum  + " Block #" + block;
	}
	/* The hits overlap, so merge the overlapping ones; similar to the one in anchor2.HitPair 
	 * For similar sequences, there can be many merges - I thought this would smooth on the display more... */
	protected String calcMergeHits(String subHits, int start, int end) {
	try {
		if (subHits == null || subHits.length() == 0) return ""; // 1 sh; uses start,end
			
		String[] subseq = subHits.split(",");
		int nsh=0, osh=subseq.length;
		
		int [] shstart = new int [osh];
		int [] shend = new int [osh];
		
		for (int k = 0;  k < osh;  k++) {
			String[] pos = subseq[k].split(":");
			int s = Integer.parseInt(pos[0]);
			int e = Integer.parseInt(pos[1]);
			 
			boolean found=false;
			for (int i=0; i<nsh; i++) {
				int olap = Math.min(e, shend[i]) - Math.max(s,shstart[i]); 
				if (olap<0) continue;
				
				found=true;
				if (e>shend[i])   shend[i] = e;
				if (s<shstart[i]) shstart[i] = s;
				break;
			}
			if (!found) {
				shstart[nsh] = s;
				shend[nsh] = e;
				nsh++;
			}
		}
		cntTotalSH+=osh; cntTotal++;
		if (nsh != osh) {cntMerge++; cntMergeSH += (osh-nsh);}
		else return subHits; // no change
		
		if (nsh==1) return ""; // use start, end; was osh>1
	
		StringBuffer sb = new StringBuffer();
		for (int i=0; i<nsh; i++) {
			if (sb.length()==0) sb.append(shstart[i] + ":" + shend[i]);
			else sb.append("," + shstart[i] + ":" + shend[i]);
		}
		return sb.toString();
	}
	catch (Exception e) {ErrorReport.print(e, "merge hits"); return null;}	
	}
	
	protected String getQueryMerge() { return qMergeSH;}
	protected String getTargetMerge() { return tMergeSH;}
	
	protected boolean isPosOrient1() { return isPosOrient1; }
	protected boolean isPosOrient2() { return isPosOrient2; }
	protected String getAnnots()	{ return annot1_idx + " " + annot2_idx;} // CAS543 added for trace
	
	public int getID() 		{ return idx; }
	public int getAnnot1() 		{ return annot1_idx;}
	public int getAnnot2() 		{ return annot2_idx;}
	public int getHitNum() 		{ return hitnum; }
	public int getBlock()		{ return blocknum;}
	public int getPos2() 		{ return (start2+end2)>>1; }
	
	public int getStart1() 		{ return start1; } 
	public int getEnd1() 		{ return end1; }   
	public int getStart2() 		{ return start2; }
	public int getEnd2() 		{ return end2; }
	
	protected int getStart1(boolean swap) { return (swap ? start2 : start1); }
	protected int getEnd1(boolean swap)   { return (swap ? end2 : end1); }
	protected int getStart2(boolean swap) { return (swap ? start1 : start2); }
	protected int getEnd2(boolean swap)   { return (swap ? end1 : end2); }

	protected double getPctid() 	{ return (double)pctid; }
	protected String getTargetSeq() { return target_seq; } 
	protected String getQuerySeq()  { return query_seq; }  
	
	// for hit popup: the query and target seq have subhits, and are blank if just one hit; CAS517 add
	protected String getQuerySubhits(){ 
		if (query_seq!=null && query_seq.length()>0) return query_seq; 
		return start1 + ":" + end1;
	} 
	protected String getTargetSubhits(){ 
		if (target_seq!=null && target_seq.length()>0) return target_seq; 
		return start2 + ":" + end2;
	}
	protected String getMinorForGenePopup(boolean isQuery, int annotIdx) {
		String d = (annotIdx!=annot1_idx && annotIdx!=annot2_idx) ? Globals.minorAnno : "";
		return d;
	}
	// for annotation popup;  
	// CAS548 list of subhits -> coords for both sides; CAS552 rm unnecessary lines, add minor
	protected String getHitCoordsForGenePopup(boolean isQuery, String tag) { 
		String tag1 = mapper.getGeneNum1(annot1_idx);
		String tag2 = mapper.getGeneNum2(annot2_idx);
		
		if (tag!=null) {
			if (isQuery) tag1 = tag;
			else tag2 = tag;
		}
		int l = Math.max(tag1.length(), tag2.length());
		String fmt = "%-" + l + "s" + " %s";
		
		String msg1 = String.format(fmt, tag1, Utilities.coordsStr(isPosOrient1, start1, end1)); 
		String msg2 = String.format(fmt, tag2, Utilities.coordsStr(isPosOrient2, start2, end2)); 
	
		String coords = isQuery ? (msg1+"\n" + msg2) : (msg2+ "\n"+ msg1);
		return coords;
	} 
	// Called from SeqHits.popupDesc (top of popup) and SeqHits.DrawHit (Hover)
	// CAS512 left/right->start:end; CAS516 add Inv, tag CAS517 puts track1 info before track2; CAS552 switch hit&block
	protected String createHover(boolean isQuery) {
		String msg = hitTag +  "\n"; 
		
		String n = (nMerge>0) ? "#Subhits=" + nMerge + "  " : "#Subhit=1  "; // CAS548 added '#'
		msg += n;
		String op = (nMerge>0) ? "~" : "";
		msg +=  "Id=" + op + pctid  + "%  ";
		msg +=  "Sim="+ op + pctsim + "%  ";
		msg +=  String.format("Cov=%,dbp", covScore); // CAS548 add bp; Cov is merged hits, remove ~
		
		String msg1 =  Utilities.coordsStr(isPosOrient1, start1, end1);  // CAS548 rm chr
		String msg2 =  Utilities.coordsStr(isPosOrient2, start2, end2); 
		String L="L ", R="R ";
		String coords = isQuery ? (L + msg1+"\n"+R+ msg2) : (L +msg2+ "\n"+ R + msg1);// CAS548 add L/R
		
		return  msg + "\n\n" + coords;
	}
	protected boolean isBlock() 	{ return isBlock; }
	protected boolean isCset() 		{ return isCollinear; } 
	protected boolean isPopup()		{ return isPopup;}
	protected boolean isHighHitg2()	{ return isHighHitg2;}
	public 	  boolean is2Gene() 	{ return (geneOlp==2); } 
	protected boolean is1Gene() 	{ return (geneOlp==1); } 
	protected boolean is0Gene()  	{ return (geneOlp==0); } 
	
	public void setIsPopup(boolean b) {// CAS543 add; SeqHits, closeup.TextShowInfo
		isPopup=b;
		Sequence s1 = (Sequence) mapper.getTrack1();// CAS545 highlight gene also
		Sequence s2 = (Sequence) mapper.getTrack2();
		s1.setHighforHit(annot1_idx, annot2_idx, b); 
		s2.setHighforHit(annot1_idx, annot2_idx, b); 
	} 
	public void setIsHighHitg2(boolean b) {	// CAS545 add; SeqPool sets true, SeqHits sets false
		isHighHitg2=b;
	
		Sequence s1 = (Sequence) mapper.getTrack1();
		Sequence s2 = (Sequence) mapper.getTrack2();
			
		s1.setConservedforHit(annot1_idx, annot2_idx, b); 
		s2.setConservedforHit(annot1_idx, annot2_idx, b); 
	}
	
	protected int getCollinearSet() {return collinearSet;} // CAS520 add 
	
	public boolean equals(Object obj) {
		return (obj instanceof HitData && ((HitData)obj).idx == idx);
	}

	public static Comparator<HitData> sortByStart2() {
		return new Comparator<HitData>() {
			public int compare(HitData hd1, HitData hd2) {
				int d = hd1.start2 - hd2.start2; 
				if (d == 0) d = hd1.end2 - hd2.end2; 
				return d;
			}
		};
	}
	// CAS517 add for coloring hit line according to directions
	protected String getOrients() { 
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
	
}
