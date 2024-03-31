package symap.closeup;

import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

import database.DBconn2;
import props.PropsDB;
import symap.Globals;
import symap.mapper.HitData;
import util.ErrorReport;
import util.Utilities;

/******************************************
 * This is used by: Align (Closeup), and the Hit Info 'Align Hit', and the Show Sequence for extracting sequence
 * AlignData/AlignPair does the alignment
 * HitAlignment  displays it for Closeup
 * TextShowAlign display is for Align Hit from Hit Info
 * TextShowSeq   displays the sequence for Show Sequence
 * 
 * CAS531 restructured/renamed for clarity
 * 1. Query and target are used in the pseudo_hits table
 * 2. Select and Other are from the user selected sequence
 * 3. isQuery determines whether the Select=Query or Select=Target
 * CAS541 was DBAbsUser
 */
public class AlignPool  {
	private static final boolean TRACE = false; 
	private static final boolean toUpper = false;
	private static final boolean bTrim=Globals.bTrim;
	
	private final int CHUNK_SZ = backend.Constants.CHUNK_SIZE; // CAS504 was hardcoded below; 1000000
	private HitAlignResults curHit; // hqr for the whole current hit
	
	private DBconn2 dbc2;
	
	/*****************************************************************/
	public AlignPool(DBconn2 dbc2, PropsDB pp){
		this.dbc2 = dbc2;
	}
	public AlignPool(DBconn2 dbc2){ // For SyMAPQueryFrame to use loadPseudoSeq
		this.dbc2 = dbc2;
	}
	// Called from CloseUpDialog.setview 
	public synchronized HitAlignment[] buildHitAlignments(Vector <HitData> hitList, 
			boolean isQuery, String projS, String projO, int selStart, int selEnd) 
	{	
		return computeHitAlignments(hitList, isQuery, projS, projO, true, selStart, selEnd);
	}
	// Called from TextPopup
	public synchronized HitAlignment[] buildHitAlignments(Vector <HitData> hitList, boolean isNT, boolean isQuery) 
	{
		return computeHitAlignments(hitList, isQuery, "", "", isNT, -1, -1);
	}
	// Called from TextPopup 
	public synchronized HitAlignment[] getHitReverse(HitAlignment [] halign) 
	{
		return computeHitReverse(halign);
	}
	// Called from TextShowSeq 
	public Vector <SeqData> getHitSequence (Vector <HitData> hitList, int grpID, boolean isQuery, boolean bRev) 
	{
		return createHitSequence(hitList, grpID, isQuery, bRev);
	}
	
	/************************************************************************
	 * Computations 
	 */
	private HitAlignment[] computeHitAlignments(Vector <HitData> hitList, 
			boolean isSwap, String projS, String projO, boolean isNT, int sStart, int sEnd) {
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
				if (sStart!=-1 && sEnd != -1) { //CAS535 only subhits that overlap the selected coords
					if (!Utilities.isOverlap(sStart, sEnd, subStart, subEnd)) continue;	
				}
				HitAlignResults subHit = alignSubHit(i, isNT, subStart, subEnd);
				
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
	public synchronized HitAlignment[] computeHitReverse(HitAlignment [] halign) {
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
	 */
	private HitAlignResults alignSubHit(int i, boolean isNT, int subStart, int subEnd) {
	try {
		HitAlignResults subHit = new HitAlignResults();
		
		// make select sub-sequence
		subHit.selectStart = subStart; // [0] s1:e1 [1] s2:e2, etc; get i[0]
		subHit.selectEnd   = subEnd;
		
		int startOfs = subHit.selectStart - curHit.selectStart; // relative to beginning
		int endOfs   = subHit.selectEnd   - curHit.selectStart;
		subHit.selectSeq =   curHit.selectSeq.substring(startOfs, endOfs+1); 
		if (toUpper)         subHit.selectSeq = subHit.selectSeq.toUpperCase();// CAS531 stop toUpperCase
		if (curHit.isSelNeg) subHit.selectSeq = SeqData.revComplement(subHit.selectSeq);
		
		// make other sub-sequence
		subHit.otherStart = extractStart(curHit.otherIndices[i]);
		subHit.otherEnd =   extractEnd(curHit.otherIndices[i]);
		
		startOfs = subHit.otherStart - curHit.otherStart;
		endOfs =   subHit.otherEnd   - curHit.otherStart;
		subHit.otherSeq =    curHit.otherSeq.substring(startOfs, endOfs+1);
		if (toUpper)         subHit.otherSeq = subHit.otherSeq.toUpperCase();// CAS531 stop toUpperCase
		if (curHit.isOthNeg) subHit.otherSeq = SeqData.revComplement(subHit.otherSeq);
		
	    /** Align **/
		int type = (isNT) ? AlignData.NT : AlignData.AA;
		AlignData ad = new AlignData();
		String tmsg = (TRACE) ? "Hit #" + curHit.hitnum + "." + (i+1) + "  " 
					+ SeqData.coordsStr(!curHit.isSelNeg, subHit.selectStart, subHit.selectEnd) + "  "
					+ SeqData.coordsStr(!curHit.isOthNeg, subHit.otherStart, subHit.otherEnd) : "";
		if (!ad.align(type, subHit.selectSeq, subHit.otherSeq, bTrim, tmsg)) return null;
		
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
	 * This is called from SyMAPQueryFrame, alignSubHit, and getHitSequence
	 * indices is start:end
	 */
	public String loadPseudoSeq(String indices, long id) {
		ResultSet rs = null;
		String pseudoSeq = "";
		
		try {
			// Java and MUMmer are zero based but mysql is 1 based, add one
			int start = extractStart(indices) + 1; 
			int end =   extractEnd(indices) + 1;   
			if (start == -1 || end == -1) return "";
			
			if (start > end) {
				int temp = start;
				start = end;
				end = temp;
			}
			
			String query2="";
			long count = end - start + 1;
			long chunk = start / CHUNK_SZ;
			String seq;
			start = start % CHUNK_SZ;
			if (start>0) start--;  // CAS531 -1 =translate like MUMmer show_align AA output (it seems to do 6-frame and pick best)
			end = end % CHUNK_SZ;
			
			while (count > 0) {
				query2 = "SELECT SUBSTRING(seq FROM " + start + " FOR " + count + ") "
						+ "FROM pseudo_seq2 AS s WHERE s.grp_idx=" + id + " AND s.chunk=" + chunk;	
	
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
			if (pseudoSeq.equals("")) { // CAS5xx check was for null, so no error produced
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

	/******************************************************************/
	private String reverseStrand(String s) {
		return new StringBuffer(s).reverse().toString();
	}
	private int extractStart(String range) {return extractInt(range)[0];}
	private int extractEnd(String range)   {return extractInt(range)[1];}
	
	private int[] extractInt(String range) {
		String tokens[] = range.split(":");
		if (tokens.length != 2) return null;
		
		int[] out = new int[2];
		out[0] = Integer.parseInt(tokens[0]); // CAS512 new Integer(tokens[0]).intValue();
		out[1] = Integer.parseInt(tokens[1]); // new Integer(tokens[1]).intValue();
		return out;
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
