package symap.closeup;

import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

import database.DBconn2;
import symap.Globals;
import symap.mapper.HitData;
import symap.sequence.Annotation;
import util.ErrorReport;
import util.Utilities;

/******************************************
 * 2D: CloseUp, Hit-Info AlignHit, Show Sequence, and symapQuery MSA
 * AlignData/AlignPair does the dynamic programming (DP) alignment.
 * This file loads the data from the database and sets it all up
 * 
 * HitAlignment  DP align for Closeup
 * TextShowAlign DP align Hit for Hit Info
 * TextShowSeq   get sequence for Show Sequence
 * Query MSA 	 get full hit sequence or concatenated subhit for MSA align
 * Query Export  get gene sequence with introns removed
 * 
 * 1. Query and target are used in the pseudo_hits table
 * 2. Select and Other are from the user selected sequence
 * 3. isQuery determines whether the Select=Query or Select=Target
 */
public class AlignPool  {
	private static final boolean TRACE = false; 
	private static final boolean toUpper = false;
	private static final boolean bTrim=Globals.bTrim; // only used for Closeup; Text align has it passed in
	private static final int lineLen = 70;
	
	private final int CHUNK_SZ = backend.Constants.CHUNK_SIZE; // 1000000;
	private HitAlignResults curHit; // hqr for the whole current hit
	
	private DBconn2 dbc2;
	
	/**************************************************************
	 * Symap query MSA 
	 */
	public AlignPool(DBconn2 dbc2){ // For SyMAPQueryFrame to use loadPseudoSeq
		this.dbc2 = dbc2;
	}
	/********************************************************
	 * Return concatenated hits, with nnnnn between each hit
	 * Overlapping hits are merged.
	 * If not gapless, load from start to end of hit.
	 * Otherwise, get the queryStr or targetStr from database, and just pull out their sequence
	 */
	public String loadSeq(int pos, String indices, int grpIdx, String gapLess) {
	try {
		if (gapLess.equals("")) return loadPseudoSeq(indices, grpIdx); // pseudo_seq2 is index by grpIdx only
		
		// process subhits
		String subhitstr = loadSubHitStr(pos, gapLess);				// reads pseudo_hits - 
		if (subhitstr==null) return loadPseudoSeq(indices, grpIdx); // shouldn't happen
		
		// Merge overlapping subhits
		String [] subhits = subhitstr.split(",");
		int [] start = new int [subhits.length];
		int [] end = new int [subhits.length];
		for (int i=0; i<subhits.length; i++) start[i]=end[i]=-1;
		
		int s2=0, e2=0;
		for (int i=0; i<subhits.length; i++) {
			int [] coords = extractInt(subhits[i]);
			
			int olap= (e2==0) ? 0 : Math.min(coords[1],e2) - Math.max(coords[0],s2) + 1;
				
			if (olap>0) end[i-1] = coords[1]; // merge; start[i] remains -1
			else {
				start[i] = coords[0]; 
				end[i] = coords[1];
			}
			s2 = coords[0]; e2 = coords[1];
		}
		
		// load sequence
		String mgSeq="";
		for (int i=0; i<subhits.length; i++) {
			if (start[i]!=-1) {
				if (!mgSeq.equals("")) mgSeq += "nnnnn"; // too many throws off alignment
				mgSeq += loadPseudoSeq(start[i]+":"+end[i], grpIdx);
			}
		}
			
		return mgSeq;
	}
	catch (Exception e) {ErrorReport.print(e, "Getting sequence"); return null;}
	}

	public String loadSeq(String indices, int grpIdx) { // For query export
		 return mkLines(loadPseudoSeq(indices, grpIdx), lineLen);
	}
	
	/*****************************************************************
	 * 2D Text popup and closeup
	 ***********************************************************/
	
	// Called from AlignPool, HitAlignment
	protected synchronized HitAlignment[] buildHitAlignments(Vector <HitData> hitList, 
			boolean isQuery, String projS, String projO, int selStart, int selEnd) 
	{	
		return computeHitAlign(hitList, isQuery, projS, projO, true, selStart, selEnd, bTrim); // use -a trim
	}
	// Called from TextPopup for hit alignment
	protected synchronized HitAlignment[] buildHitAlignments(Vector <HitData> hitList, boolean isNT, boolean isQuery, boolean isTrim) 
	{
		return computeHitAlign(hitList, isQuery, "", "", isNT, -1, -1, isTrim); 
	}
	// Called from TextPopup for gene alignment
	protected synchronized HitAlignment buildGeneAlignments(HitData hd, 
			String projS, Annotation anno1, String projO, Annotation anno2, boolean isTrim) 
	{
		// get spliced sequences
		char s1 = (anno1.isStrandPos()) ? '+' : '-';
		char s2 = (anno2.isStrandPos()) ? '+' : '-';
		int [] num1 = new int [4];
		String seq1 = loadGeneSplice(anno1.getAnnoIdx(), s1+"", anno1.getGrpIdx(), num1);
		int [] num2 = new int [4];
		String seq2 = loadGeneSplice(anno2.getAnnoIdx(), s2+"", anno2.getGrpIdx(), num2);
		
		 /** Align **/
		AlignData ad = new AlignData();
		if (!ad.align(AlignData.NT, seq1, seq2, isTrim, "")) return null; 
		
		// Get results
		String seq1Align =  ad.getAlignSeq1();
		String seq2Align =  ad.getAlignSeq2();
		String matchAlign =	ad.getAlignMatch();
		
		String scoreStr = ad.getScore();
		int offset  = 	  ad.getStartOffset();
		int alignLength = seq1Align.length();
		
		// data structure that will put it into text form
		HitAlignment ha = new HitAlignment(hd, anno1.getFullGeneNum(), anno2.getFullGeneNum(), 
				new SeqData(seq1, seq1Align, s1),  new SeqData(seq2,  seq2Align, s2),
				new SeqData(matchAlign, ' '),
				num1[2], num1[3], num2[2], num2[3], // start, end
				alignLength, offset, scoreStr, "");
		return ha;
	}
	// Called from TextPopup 
	protected synchronized HitAlignment[] getHitReverse(HitAlignment [] halign) 
	{
		return computeHitReverse(halign);
	}
	// Called from TextShowSeq 
	protected Vector <SeqData> getHitSequence (Vector <HitData> hitList, int grpID, boolean isQuery, boolean bRev) 
	{
		return createHitSequence(hitList, grpID, isQuery, bRev);
	}
	
	/************************************************************************
	 * Computations for CloseUp and TextPopup
	 */
	private HitAlignment[] computeHitAlign(Vector <HitData> hitList, 
			boolean isSwap, String projS, String projO, boolean isNT, int sStart, int sEnd, boolean isTrim) {
	try {
		// make sorted hitArr from hitList
		HitData[] hitArr = new HitData [hitList.size()];
		for (int i=0; i<hitList.size(); i++) hitArr[i] = hitList.get(i);
		Comparator<HitData> comp = HitData.sortByStart2();
		Arrays.sort(hitArr, comp);
			
		// for each hit from selected region
		Vector <HitAlignment> hitAlign = new Vector <HitAlignment>();
		
		for (HitData hitObj : hitArr) { 
			curHit = loadHitForAlign(hitObj, isSwap);
			
			if (curHit==null) return null;
			
			// for each subhit; 
			int num=curHit.selectIndices.length;
			for (int i = 0;  i < num;  i++) {
				int subStart = extractStart(curHit.selectIndices[i]);
				int subEnd = extractEnd(curHit.selectIndices[i]);
				if (sStart!=-1 && sEnd != -1) { 
					if (!Utilities.isOverlap(sStart, sEnd, subStart, subEnd)) continue;	
				}
				// Do Alignment
				HitAlignResults subHit = alignSubHit(i, isNT, subStart, subEnd, isTrim);
				
				HitAlignment ha = new HitAlignment(hitObj, projS, projO, 
						new SeqData(subHit.selectSeq, subHit.selectAlign, curHit.strand.charAt(0)), 
						new SeqData(subHit.otherSeq,  subHit.otherAlign, curHit.strand.charAt(2)),
						new SeqData(subHit.matchAlign, ' '),
						subHit.selectStart, subHit.selectEnd,
						subHit.otherStart,  subHit.otherEnd,
						subHit.alignLength, subHit.offset,
						subHit.scoreStr, (i+1)+"/"+num);
				 
				hitAlign.add(ha);
			}
		}
		return (HitAlignment[]) hitAlign.toArray(new HitAlignment[0]);
	} catch (Exception e) {ErrorReport.print(e, "getHitAlignments"); return null; }
	}
	
	/******************************************************************************/
	private synchronized HitAlignment[] computeHitReverse(HitAlignment [] halign) {
		Vector <HitAlignment> alignments = new Vector <HitAlignment>();
		
		for (int i=0; i<halign.length; i++) {
			HitAlignResults subHit = alignSubHitRev(halign[i]);
			
			HitAlignment ha = new HitAlignment(halign[i], 
					new SeqData(subHit.selectAlign, subHit.strand.charAt(0)), 
					new SeqData(subHit.otherAlign,  subHit.strand.charAt(2)),
					new SeqData(subHit.matchAlign, ' '),
					subHit.selectStart, subHit.selectEnd,
					subHit.otherStart,  subHit.otherEnd,
					subHit.alignLength, subHit.offset,
					subHit.scoreStr);
			 
			alignments.add(ha);
		}
		return (HitAlignment[]) alignments.toArray(new HitAlignment[0]);
	}
	/*********************************************************************
	 * ComputeHitAlign
	 */
	private HitAlignResults alignSubHit(int i, boolean isNT, int subStart, int subEnd, boolean isTrim) {
	try {
		HitAlignResults subHit = new HitAlignResults();
		
		// make select sub-sequence
		subHit.selectStart = subStart; // [0] s1:e1 [1] s2:e2, etc; get i[0]
		subHit.selectEnd   = subEnd;
		
		int startOfs = subHit.selectStart - curHit.selectStart; // relative to beginning
		int endOfs   = subHit.selectEnd   - curHit.selectStart;
		subHit.selectSeq =   curHit.selectSeq.substring(startOfs, endOfs+1); 
		if (toUpper)         subHit.selectSeq = subHit.selectSeq.toUpperCase();
		if (curHit.isSelNeg) subHit.selectSeq = SeqData.revComplement(subHit.selectSeq);
		
		// make other sub-sequence
		subHit.otherStart = extractStart(curHit.otherIndices[i]);
		subHit.otherEnd =   extractEnd(curHit.otherIndices[i]);
		
		startOfs = subHit.otherStart - curHit.otherStart;
		endOfs =   subHit.otherEnd   - curHit.otherStart;
		subHit.otherSeq =    curHit.otherSeq.substring(startOfs, endOfs+1);
		if (toUpper)         subHit.otherSeq = subHit.otherSeq.toUpperCase();
		if (curHit.isOthNeg) subHit.otherSeq = SeqData.revComplement(subHit.otherSeq);
		
	    /** Align **/
		int type = (isNT) ? AlignData.NT : AlignData.AA;
		AlignData ad = new AlignData();
		String tmsg = (TRACE) ? "Hit #" + curHit.hitnum + "." + (i+1) + "  " 
					+ SeqData.coordsStr(!curHit.isSelNeg, subHit.selectStart, subHit.selectEnd) + "  "
					+ SeqData.coordsStr(!curHit.isOthNeg, subHit.otherStart, subHit.otherEnd) : "";
		
		if (!ad.align(type, subHit.selectSeq, subHit.otherSeq, isTrim, tmsg)) return null; 
		
		// Get results
		subHit.selectAlign = ad.getAlignSeq1();
		subHit.otherAlign =  ad.getAlignSeq2();
		subHit.matchAlign =	 ad.getAlignMatch();
		
		subHit.scoreStr =	 ad.getScore();
		subHit.offset  = 	 ad.getStartOffset();
		subHit.alignLength = subHit.selectAlign.length();
		
		return subHit;
	}
	catch (Exception e) {ErrorReport.print(e, "align hit"); return null;}
	}
	/****************************************************************/
	private HitAlignResults alignSubHitRev(HitAlignment ha) {
		try {
			HitAlignResults subHit = new HitAlignResults();
			
			String subSelect =   ha.getSelectSeq(); 
			if (subSelect==null || subSelect.length()==0) ErrorReport.die("no select seq");
			subSelect = SeqData.revComplement(subSelect);
			
			String subOther =    ha.getOtherSeq();
			subOther = SeqData.revComplement(subOther);
			
		    /** Align **/
			int type = AlignData.NT;
			AlignData ad = new AlignData();
			String tmsg = (TRACE) ? ha.getSubHitName() + "  Reverse" : "";
			if (!ad.align(type, subSelect, subOther, bTrim, tmsg)) return null;
			
			// Get results
			subHit.selectAlign = ad.getAlignSeq1();
			subHit.otherAlign =  ad.getAlignSeq2();
			subHit.matchAlign =	 ad.getAlignMatch();
			
			subHit.scoreStr =	 ad.getScore();
			subHit.offset  = 	 ad.getStartOffset();
			subHit.alignLength = subHit.selectAlign.length();
			
			subHit.selectStart = ha.getSstart();
			subHit.selectEnd = 	 ha.getSend();
			
			subHit.otherStart =  ha.getOstart();
			subHit.otherEnd = 	 ha.getOend();
			char ss =     (ha.getSelectStrand()=='+') ? '-' : '+'; // reverse
			char so =     (ha.getOtherStrand()=='+') ? '-' : '+';
			subHit.strand = ss + "/" + so;
			
			return subHit;
		}
		catch (Exception e) {ErrorReport.print(e, "align hit"); return null;}
	}
	
	/***************************************************************
	 * Get subhits for TextShowSeq (Show Sequence) - set query only and get isSwap from grpID
	 */
	private Vector <SeqData> createHitSequence (Vector <HitData> hitList, 
			int grpID, boolean isQuery, boolean bRev) {
	try {
		Vector <SeqData> hitVec = new Vector <SeqData> ();
		
		// make sorted hitArr from hitList
		HitData[] hitArr = new HitData [hitList.size()];
		for (int i=0; i<hitList.size(); i++) hitArr[i] = hitList.get(i);
		Comparator<HitData> comp = HitData.sortByStart2();
		Arrays.sort(hitArr, comp);
			
		// for each hit from selected region
		for (HitData hitObj : hitArr) { 
			curHit = loadHitForSeq(hitObj, grpID, isQuery);
			
			// for each subhit, extract subsequence
			int num=curHit.selectIndices.length;
			for (int i = 0;  i < num;  i++) {
				// make query sub-sequence
				int qStart = extractStart(curHit.selectIndices[i]); // [0] s1:e1 [1] s2:e2, etc; get i[0]
				int qEnd   = extractEnd(curHit.selectIndices[i]);
				
				int startOfs 	= qStart - curHit.selectStart; // relative to beginning
				int endOfs   	= qEnd   - curHit.selectStart;
				String strQuery = curHit.selectSeq.substring(startOfs, endOfs+1);
				if (toUpper)    strQuery = strQuery.toUpperCase();
				char strand 	= curHit.strand.charAt(0);
				String rc="";
				if (strand == '-') {
					if (!bRev) {
						strQuery = SeqData.revComplement(strQuery); 
						rc=" RC";
					}
				}
				else if (bRev) {
					strQuery = SeqData.revComplement(strQuery);
					rc=" RC";
				}
				
				String coords = SeqData.coordsStr(strand, qStart, qEnd);
				String header = hitObj.getName() + "." + (i+1)+"/"+num + "   " + coords + rc;
				SeqData sd = new SeqData(strQuery, qStart, strand, header);
				hitVec.add(sd);
			}
		}
		Collections.sort(hitVec);
		return hitVec;
	} catch (Exception e) {ErrorReport.print(e, "align hit"); return null;}
	}
	/************************************************************************
	 * Database calls
	 */
	// For ViewProj -- it adds >ID 
	public String loadProtein(int annotIdx, int grpIdx, String strand) {
		try {
			int [] num = new int [4]; 
			String seq = loadGeneSplice(annotIdx, strand, grpIdx, num);
			String aa = SeqData.translate(seq);
			return mkLines(aa, lineLen);
		}
		catch (Exception e) {ErrorReport.print("load protein " + annotIdx);}
		return null;
	}
	public String loadSpliced(int annotIdx, int grpIdx, String strand) {
		try {
			int [] num = new int [4];
			String seq = loadGeneSplice(annotIdx, strand, grpIdx, num);
			return mkLines(seq, lineLen);
		}
		catch (Exception e) {ErrorReport.print("load protein " + annotIdx);}
		return null;
	}
	
	/**************************************************************
	 * Exon spliced sequence; CAS586 add
	 * Read DB for idx and strand; read DB for exons/CDS; read DB for each splice CDS
	 * Very slow
	 */
	public String loadGeneSeq(String genetag, int grpIdx, String annoType) { // For view export, query export, MSA
		String seq="", query="";
		if (genetag.isEmpty()) {Globals.prt("No genetag"); return null;}
		
		String genenum = genetag.substring(0, genetag.indexOf(Globals.DOT)); // 123. or 123.b; int is genenum
		if (genenum.isEmpty()) {Globals.prt("Could not parse: " + genetag); return null;}
		
		try {
			int annotIdx=0;
			String strand="+", id="", rna="", tag="";
			
			query = "select idx, tag, name, strand from pseudo_annot "
					+ " where genenum=" + genenum + " and grp_idx=" + grpIdx;
			
			// Go through records getting the genenum and tag, and use that one where pseudo_tag.startsWith(genetag)
			ResultSet rs = dbc2.executeQuery(query);
			while (rs.next()) {
				tag = rs.getString(2);		// 123. (exon stuff) or 123.b (exon stuff)
				tag = tag.substring(0, tag.indexOf("(")).trim();
	
				if (tag.equals(genetag)) {
					annotIdx 		= rs.getInt(1);
					strand   		= rs.getString(4);
					
					// ID=AT1G01010;Name=NAC001;rnaID=NM_099983.2 (1);desc=NAC domain containing protein 1
					String name     = rs.getString(3);
					String [] tok = name.split(";");
					for (String val : tok) {
						if (val.startsWith("ID="))    id=val.replace("ID=","");
						else if (val.startsWith("rnaID")) rna=val;
					}
					if (id.isEmpty()) id = name;
					break;
				}
			}
			rs.close();
			if (annotIdx==0) {Globals.prt("Fail: " + genetag + " (" + tag + ") " + query);return null;}
			
			int [] num = new int [4]; 
			seq = loadGeneSplice(annotIdx, strand, grpIdx, num);
			
			seq = mkLines(seq, lineLen);
			
			// header is first line, where > geneTag will be put in front from calling routine
			// queries MSA annoType=null;  queries Export annoType is CDS or Exon  
			String header=""; 
			if (annoType!=null) { // MSA is null
				header = String.format("%s  %s  %s  #%s=%d %,dbp\n", id, strand, rna, annoType, num[0], num[1]);
			}
			return header + seq; 
		} 
		catch (Exception e) {ErrorReport.print(e, "Getting gene seq for: " + genetag); Globals.prt(query);} 	
		return seq;
	}
	// returns Spliced gene sequence; get coordinates of exons/CDS, then call LoadGeneSeq for each
	// the output was compared with gffRead on CDSs
	// num[0]=#exons, num[1]=length, num[2]=start, num[3]=end (if CDS, not the same as gene start/end)
	public String loadGeneSplice(int annotIdx, String strand, int grpIdx, int [] num) { // For query export and MSA
		String seq="", query="";
		ResultSet rs=null;
		try {
			num[2]=Integer.MAX_VALUE; num[3]=0;
			
			Vector <String> coords = new Vector <String> (20);
	
			query = "SELECT start, end FROM pseudo_annot WHERE gene_idx=" + annotIdx 
					+ " and grp_idx= " + grpIdx + " order by start";
		
			// GFF coordinates are 1-based and fully closed (the start position is the exact position of the first base, and the end position is the exact position of the last base).
			rs = dbc2.executeQuery(query); // loadPseudoSeq does db calls, so cannot be in the loop
			while (rs.next()) {
				int start = rs.getInt(1); // loadPseudoSeq adds one assuming zero-based coords
				int end   = rs.getInt(2);
				
				coords.add(start + ":" + end);
				
				num[0]++;
				num[1] += (end-start)+1;
				num[2] = Math.min(num[2], start);
				num[3] = Math.max(num[3], end);
			}
			rs.close();
			
			// get splice strings and concatenate 
			StringBuffer seqbuf = new StringBuffer();
			for (String c : coords) {
				String exon = loadPseudoSeq(c, grpIdx);
				seqbuf.append(exon);
			}
			seq = seqbuf.toString();
			if (strand.equals("-")) seq = reverseComplement(seq);
			
			return seq;
		}
		catch (Exception e) {
			ErrorReport.print(e, "Getting gene seq for: " + annotIdx); 
			try {if (rs!=null) rs.close();} catch (Exception ee) {};
		} 
		
		return "#error\n" + seq;
	}
	
	/***************************************************************************
	 * Load the hit info and subhit target/query sequence
	 * Coords for subhits are relative to forward strand
	 */
	private HitAlignResults loadHitForAlign(HitData hitData, boolean isQuery) {
		ResultSet rs = null;
		curHit = new HitAlignResults();
		
		// Note that target_seq and query_seq refer to indices of sequence segments not the sequences themselves.
		String query = "SELECT h.strand, h.query_seq, h.target_seq,  h.grp1_idx, h.grp2_idx, h.hitnum "
				     + "FROM pseudo_hits AS h WHERE h.idx=" +  hitData.getID();		
		try {
			rs = dbc2.executeQuery(query);
			if (!rs.next()) {
				System.err.println("Major SyMAP error reading database");
				return null;
			}
			curHit.strand = rs.getString(1);
			if (curHit.strand == null || curHit.strand.length() < 3) {
				System.err.println("Invalid strand value '" + curHit.strand + "' for hit idx=" + hitData.getID());
				curHit.strand = "+/+";
			}
			
			String query_seq  	= rs.getString(2);  // string list of sub-block start/end values
			String target_seq 	= rs.getString(3);  // string list of sub-block start/end values
			int grp1_idx 		= rs.getInt(4);
			int grp2_idx 		= rs.getInt(5);
			curHit.hitnum 		= rs.getInt(6);
			
			int start1 = (isQuery) ? hitData.getStart1() : hitData.getStart2();
			int end1   = (isQuery) ? hitData.getEnd1()   : hitData.getEnd2();
			int start2 = (isQuery) ? hitData.getStart2() : hitData.getStart1();
			int end2   = (isQuery) ? hitData.getEnd2()   : hitData.getEnd1();
	
			if (!isQuery) {
				String strTemp = query_seq;
				query_seq = target_seq;
				target_seq = strTemp;
				
				int nTemp = grp1_idx;
				grp1_idx = grp2_idx;
				grp2_idx = nTemp;
				
				curHit.strand = reverseStrand(curHit.strand); // swap x/y
			}

			// No indices if one hit, so create indices for whole hit
			if (query_seq  == null || query_seq.length()  == 0 || target_seq == null || target_seq.length() == 0){   
				query_seq  = start1 + ":" + end1;
				target_seq   = start2 + ":" + end2;
			}
			
			// Get sequences of select and other covering merged hits
			curHit.isSelNeg = (curHit.strand.charAt(0) == '-'); // -/?
			curHit.selectIndices = query_seq.split(",");
			curHit.selectStart =   start1;
			curHit.selectSeq =     loadPseudoSeq(start1 + ":" + end1, grp1_idx);
		
			curHit.isOthNeg = (curHit.strand.charAt(2) == '-'); // ?/-
			curHit.otherIndices = target_seq.split(",");
			curHit.otherStart =   start2;
			curHit.otherSeq =     loadPseudoSeq(start2 + ":" + end2, grp2_idx);
		}
		catch (Exception e) {ErrorReport.print(e, "Acquiring alignment"); } 
		return curHit;
	}
	/*********************************
	 * indices is start:end; called from anywhere that needs chucks of sequence;
	 * called directly from TextShowSeq and from this file
	 * Original routine written by Will
	 */
	protected String loadPseudoSeq(String indices, int grpIdx) {
		ResultSet rs = null;
		String pseudoSeq = ""; // add to in 1M chucks
		
		try {// Java and MUMmer are zero based but mysql is 1 based, add one
			int start = extractStart(indices) + 1; 
			int end =   extractEnd(indices) + 1;   
			if (start == -1 || end == -1) return "";
			
			if (start > end) {
				int temp = start;
				start = end;
				end = temp;
			}
			
			String query2="", seq="";
			
			long count = end - start + 1;
			long chunk = start / CHUNK_SZ;
			start = start % CHUNK_SZ;
			if (start>0) start--;  // -1 =translate like MUMmer show_align AA output (it seems to do 6-frame and pick best)
			end = end % CHUNK_SZ;
			
			while (count > 0) {
				query2 = "SELECT SUBSTRING(seq FROM " + start + " FOR " + count + ") "
						+ "FROM pseudo_seq2 AS s WHERE s.grp_idx=" + grpIdx + " AND s.chunk=" + chunk;	
	
				rs = dbc2.executeQuery(query2);
				if (rs.next()) {
					seq = rs.getString(1);
					pseudoSeq += seq;
					count -= seq.length(); // account for start offset
					end   -= seq.length();
					start = 1;
					chunk++;
				}
				else break;
			}	
			if (pseudoSeq.equals("")) { 
				String msg="Could not read sequence for " + indices + " may be wrong alignment files loaded.";
				ErrorReport.print(msg);
				return "";
			}
			return pseudoSeq;
		}
		catch (Exception e) {ErrorReport.print(e, "Getting sequence data"); return "";}
	}
	/*******************************************************
	 *  For getSeq Get subhit query only and get isSwap from grpID
	 *  HitData is from Mapper 
	 */
	private HitAlignResults loadHitForSeq(HitData hitData, int grpID, boolean isQuery) {
		ResultSet rs = null;
		curHit = new HitAlignResults();
		
		// Note that target_seq and query_seq refer to indices of sequence segments not the sequences themselves.
		String query = "SELECT h.strand, h.query_seq, h.target_seq,  h.grp1_idx, h.grp2_idx "
				     + "FROM pseudo_hits AS h WHERE h.idx=" +  hitData.getID();
		
		try {
			rs = dbc2.executeQuery(query);
			if (!rs.next()) {
				System.err.println("Major SyMAP error reading database");
				return null;
			}
			curHit.strand = rs.getString(1);
			if (curHit.strand == null || curHit.strand.length() < 3) {
				System.err.println("Invalid strand value '" + curHit.strand + "' for hit idx=" + hitData.getID());
				curHit.strand = "+/+";
			}
			
			String query_seq  = rs.getString(2);  // string list of sub-block start/end values
			String target_seq = rs.getString(3);  // string list of sub-block start/end values
			
			int start1 = (isQuery) ? hitData.getStart1() : hitData.getStart2();
			int end1   = (isQuery) ? hitData.getEnd1()   : hitData.getEnd2();
		
			if (!isQuery) {
				query_seq = target_seq;
				curHit.strand = reverseStrand(curHit.strand);
			}
			// No indices if one hit, so create indices for whole hit
			if (query_seq  == null || query_seq.length()  == 0 || target_seq == null || target_seq.length() == 0){   
				query_seq  = start1 + ":" + end1;
			}
			
			// Get sequence covering to merged hits
			curHit.selectIndices  = query_seq.split(",");
			curHit.selectStart =    start1;
			curHit.selectSeq =      loadPseudoSeq(start1 + ":" + end1, grpID);
		}
		catch (Exception e) {ErrorReport.print(e, "Acquiring alignment"); } 
		
		return curHit;
	}
	
	// Query MSA 
	private String loadSubHitStr(int pos, String gapLess) {
	try {
		String [] tok = gapLess.split(";");		// pseudo_hits needs all of these to be unique since no hitIdx
		String where = " FROM pseudo_hits where hitNum=" + tok[0] 
				+ " and grp1_idx=" + tok[1] + " and proj1_idx=" + tok[2] 
				+ " and grp2_idx=" + tok[3] + " and proj2_idx=" + tok[4];
		if (pos==0) {
			ResultSet rs = dbc2.executeQuery("SELECT query_seq " + where);
			if (rs.next()) return rs.getString(1);
		}
		else {
			ResultSet rs = dbc2.executeQuery( "SELECT target_seq " +  where);
			if (rs.next()) return rs.getString(1);
		}
		Globals.eprt("Load indices: " + where);
		return null;
	}
	catch (Exception e) {ErrorReport.print(e, "Subhit string"); return "";} 	
	}
	/******************************************************************/
	private String reverseStrand(String s) { // used to reverse +/- or -/+
		return new StringBuffer(s).reverse().toString();
	}
	
	private String reverseComplement(String dna){ 
		StringBuilder rc = new StringBuilder();
        
        // Loop through the string backwards to reverse it
        for (int i = dna.length() - 1; i >= 0; i--) {
            char c = dna.charAt(i);
            
            switch (c) { // Append the complement while preserving case
                case 'A': rc.append('T'); break;
                case 'a': rc.append('t'); break;
                case 'T': rc.append('A'); break;
                case 't': rc.append('a'); break;
                case 'C': rc.append('G'); break;
                case 'c': rc.append('g'); break;
                case 'G': rc.append('C'); break;
                case 'g': rc.append('c'); break;
                default: rc.append(c); // Retain unknown characters (like N)
            }
        }
        return rc.toString();
	}
	
	private int extractStart(String range) {return extractInt(range)[0];}
	private int extractEnd(String range)   {return extractInt(range)[1];}
	
	private int[] extractInt(String range) {
		String tokens[] = range.split(":");
		if (tokens.length != 2) return null;
		
		int[] out = new int[2];
		out[0] = Integer.parseInt(tokens[0]); 
		out[1] = Integer.parseInt(tokens[1]); 
		return out;
	}
	private String mkLines(String seq, int len) { // CAS586 add for Queries Export transcript and spliced gene
		StringBuffer seqbuf = new StringBuffer();
		int i=0;
		while (i<seq.length()) {
			if (i+len < seq.length()) {
				seqbuf.append(seq.substring(i, i+len) + "\n");
			}
			else {
				seqbuf.append(seq.substring(i, seq.length()));
			}
			i+=len;
		}
		return seqbuf.toString();
	}
	
	/********************************************************
	 * One hqr for current hit, and one for each subhit for alignment, transferred to HitAlignment class
	 */
	static class HitAlignResults {
		private boolean isSelNeg, isOthNeg;
		private String 	strand;
		
		private int 	selectStart, selectEnd;
		private int 	otherStart, otherEnd;
		private int 	alignLength, offset;
		
		private String 	selectSeq, otherSeq;
		private String 	selectAlign=null, otherAlign=null, matchAlign;
		
		private String[] selectIndices, otherIndices;
		private String 	scoreStr;
		private int hitnum; // for tracing
		
		protected HitAlignResults() {}
	}
}
