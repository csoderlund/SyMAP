package symap.mapper;

import java.util.Comparator;

import symap.Globals;
import symap.sequence.Sequence;
import util.ErrorReport;
import util.Utilities;

/**
 * Represents one Clustered Hit.
 * SeqHits has the set, plus a DrawHit that is 1-to-1 with a HitData
 */
public class HitData {	
	protected static int cntMerge=0, cntTotal=0, cntMergeSH=0, cntTotalSH=0; 
	private Mapper mapper;				// for way back to other data
	private int idx;				
	private int hitnum;				 
	private byte pctid, pctsim; 		
	private int covScore;				
	private int nMerge;
	private int geneOlp = -1; 
	private boolean isPosOrient1, isPosOrient2; 	
	protected int 	annot1_idx, annot2_idx; // pseudo changed to zero in MapperPool; CAS570
	private String 	query_seq, target_seq;  // coordinates of subhits
	
	protected int start1, end1, mid1, start2, end2, mid2;	// 1=q, 2=t; mid for paintComponent
	protected String qMergeSH, tMergeSH;					// merged subhits for faster drawing in SeqHits.DrawHit
	
	private int collinearSet, blocknum; 			
	
	private boolean isBlock, isCollinear;		// set on init
	
	private boolean isPopup=false;		// set when popup; 
	private boolean isHighG2xN=false, isForceG2xN=false;	// set on seqFilter 3-track ref only; force is filtered but must be shown
	
	protected String hitGeneTag;		// g(gene_overlap) or htype (EE) ; gNbN see MapperPool; 
	private String   hitTag; 
	
	// MapperPool.setSeqHitData populates, puts in array for SeqHits, where each HitData is associated with a DrawHit
	protected HitData(Mapper mapper, int id, int hitnum, 
			  double pctid, int pctsim, int nMerge, int covScore, String htype, int overlap,
			  int annot1_idx, int annot2_idx,
			  String strand, int start1, int end1, int start2, int end2, String query_seq, String target_seq,   
			  int runnum, int runsize, int block, double corr) 
	{
		this.mapper = mapper;
		this.idx = id;
		this.hitnum = hitnum;
		
		this.pctid = (byte)pctid;
		this.pctsim = (byte) pctsim;
		this.nMerge = nMerge;
		this.covScore = covScore;
		this.geneOlp = overlap;
		
		this.annot1_idx = annot1_idx;	// even if not annotated are number, this will still be 0 if not annotated
		this.annot2_idx = annot2_idx;
		
		if (strand.length() >= 3) { 
			this.isPosOrient1 = (strand.charAt(0) == '+');
			this.isPosOrient2 = (strand.charAt(2) == '+');
		}
		else Globals.dprt("HitData: Invalid strand value '"+strand+"' for hit id="+id);
			
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
		
		this.collinearSet = runnum; 
		this.isCollinear = (collinearSet==0) ? false : true; 
		
		this.blocknum = block;
		this.isBlock = (blocknum>0) ? true : false;
		
		String gg = (htype.equals("0") || htype.equals("")) ? (" g" + geneOlp) : (" " +htype); 
		String iv = (corr<0) ? " Inv  " : "  "; // Strand may be == when Inv
	
		String co = (runsize>0 && runnum>0) ? (" c" + runsize + "." + runnum) : "";
		hitTag = "Hit #" + hitnum + gg + "   Block #" + block + iv + co;
		
		hitGeneTag = "Hit #" + hitnum  + " Block #" + block;
	}
	/* The hits overlap, so merge the overlapping ones; similar to the one in anchor2.HitPair and SeqHits
	 * For similar sequences, there can be many merges */
	protected String calcMergeHits(String subHits, int start, int end) {
	try {
		if (subHits == null || subHits.length() == 0) return ""; // 1 sh; uses start,end
			
		String [] subseq = subHits.split(",");
		int newSH=0, origSH=subseq.length;
		
		int [] shstart = new int [origSH];
		int [] shend   = new int [origSH];
		
		for (int k = 0;  k < origSH;  k++) {// go through hits and either create a new one or merge
			String[] pos = subseq[k].split(":");
			int s = Integer.parseInt(pos[0]);
			int e = Integer.parseInt(pos[1]);
			 
			boolean found=false;
			for (int i=0; i<newSH; i++) { 
				int olap = Math.min(e, shend[i]) - Math.max(s,shstart[i]); 
				if (olap<0) continue;
				
				found=true;
				if (e>shend[i])   shend[i] = e;
				if (s<shstart[i]) shstart[i] = s;
				break;
			}
			if (!found) {
				shstart[newSH] = s;
				shend[newSH] = e;
				newSH++;
			}
		}
		cntTotalSH+=origSH; cntTotal++;
		if (newSH == origSH) return subHits; // no change
		if (newSH==1) return ""; // use start, end; was osh>1
		
		cntMerge++; cntMergeSH += (origSH-newSH);
		
		StringBuffer sb = new StringBuffer();
		for (int i=0; i<newSH; i++) {
			if (sb.length()==0) sb.append(shstart[i] + ":" + shend[i]);
			else sb.append("," + shstart[i] + ":" + shend[i]);
		}
		return sb.toString();
	}
	catch (Exception e) {ErrorReport.print(e, "merge hits"); return null;}	
	}
	
	protected String getQueryMerge()  { return qMergeSH;}  // paintHitLen; expects "" if only one hit
	protected String getTargetMerge() { return tMergeSH;}
	
	protected boolean isPosOrient1() { return isPosOrient1; }
	protected boolean isPosOrient2() { return isPosOrient2; }
	protected String getAnnots()	 { return annot1_idx + " " + annot2_idx;} // for trace
	
	public int getID() 		    { return idx; }
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
	
	// for hit popup: the query and target seq have subhits, and are blank if just one hit; 
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
	// list of subhits -> coords for both sides; 
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
	protected String createHover(boolean isQuery) {
		String msg = hitTag +  "\n"; 
		
		String op = (nMerge>0) ? "~" : "";
		msg +=  "Id=" + op + pctid  + "%  ";
		msg +=  "Sim="+ op + pctsim + "%  ";
		msg +=  String.format("Cov=%,dbp", covScore); 
		
		String L="L ", R="R ";
		String gn1 = "#"+mapper.getGeneNum1(annot1_idx), gn2 = "#"+mapper.getGeneNum2(annot2_idx);
		String gn = isQuery  ? (L + gn1+"   "+R+ gn2) : (L +gn2+ "   "+ R + gn1); // CAS570 add
		
		String msg1 =  Utilities.coordsStr(isPosOrient1, start1, end1);  
		String msg2 =  Utilities.coordsStr(isPosOrient2, start2, end2); 
		
		String coords = isQuery ? (L + msg1+"\n"+R+ msg2) : (L +msg2+ "\n"+ R + msg1);
		
		return  msg + "\n\n" + coords + "\n\n" + gn; // gn on end to be similar to popup
	}
	protected String createPopup(boolean isQuery) {//  slightly different from createHover; CAS570 made separate,
		String msg = hitTag +  "\n"; 
		
		String op = (nMerge>0) ? "~" : "";
		msg +=  "Id=" + op + pctid  + "%  ";
		msg +=  "Sim="+ op + pctsim + "%  ";
		msg +=  String.format("Cov=%,dbp  ", covScore); 
		String n = (nMerge>0) ? "#Subhits=" + nMerge + "  " : "#Subhit=1  "; // CAS570 moved to end; only in popup
		msg += n;
		
		String L="L ", R="R ";
		String msg1 =  Utilities.coordsStr(isPosOrient1, start1, end1);  
		String msg2 =  Utilities.coordsStr(isPosOrient2, start2, end2); 
		
		String coords = isQuery ? (L + msg1+"\n"+R+ msg2) : (L +msg2+ "\n"+ R + msg1);
		
		return  msg + "\n\n" + coords;
	}
	protected boolean isBlock() 	{ return isBlock; }
	protected boolean isCset() 		{ return isCollinear; } 
	protected boolean isPopup()		{ return isPopup;}
	public 	  boolean is2Gene() 	{ return (geneOlp==2); } 
	protected boolean is1Gene() 	{ return (geneOlp==1); } 
	protected boolean is0Gene()  	{ return (geneOlp==0); } 
	protected boolean isInv()		{ return isPosOrient1!=isPosOrient2;} // for TextShowInfo
	
	public void setIsPopup(boolean b) {// SeqHits, closeup.TextShowInfo
		isPopup=b;
		Sequence s1 = (Sequence) mapper.getTrack1();// highlight gene also
		Sequence s2 = (Sequence) mapper.getTrack2();
		s1.setHighforHit(annot1_idx, annot2_idx, b); 
		s2.setHighforHit(annot1_idx, annot2_idx, b); 
	} 
	protected int getCollinearSet() {return collinearSet;} 
	
	/* ***** g2xN methods ******** */
	protected boolean isHighG2xN()	{ return isHighG2xN;}
	protected boolean isForceG2xN()	{ return isForceG2xN;}
	
	public void setHighForceG2xN(boolean b) {isHighG2xN = b; isForceG2xN=b;}
	public void setForceG2xN(boolean b)     {isForceG2xN=b;}
	
	public void setHighAnnoG2xN(boolean b) {	// SeqPool.computeG2xn sets true, SeqHits.clearHighG2xN sets false
		if (!b && !isHighG2xN) return;
		
		isHighG2xN = b;	// highlights hit  
	
		Sequence s1 = (Sequence) mapper.getTrack1();
		Sequence s2 = (Sequence) mapper.getTrack2();
		
		s1.setAnnoG2xN(annot1_idx, annot2_idx, b);  // highlights annotation for gene (either annot1 or annot2)
		s2.setAnnoG2xN(annot1_idx, annot2_idx, b); 
	}
	public void clearG2xN() {
		if (isHighG2xN) setHighAnnoG2xN(false);
		
		isHighG2xN=isForceG2xN=false;
	}
	////////////////////////////////////////////
	
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
	// for coloring hit line according to directions
	protected String getOrients() { 
		String x = (isPosOrient1) ? "+" : "-";
		String y = (isPosOrient2) ? "+" : "-";
		return x+y;
	}
	
	public String toStr() 	{ 
		String msg =  hitTag + " AnnoIdx " + annot1_idx + " " + annot2_idx;
		return msg;
	}
	public String getName()		{ return "Hit #" + hitnum;}
	
}
