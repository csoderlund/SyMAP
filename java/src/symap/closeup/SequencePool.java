package symap.closeup;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedList;
import java.util.Comparator;
import java.util.Vector;

import symap.pool.DatabaseUser;
import symap.mapper.HitData;
import symap.mapper.FPCPseudoData;
import symap.mapper.Mapper;
import symap.mapper.PseudoPseudoData;
import symap.mapper.AbstractHitData;
import symap.pool.ProjectProperties;
import util.DatabaseReader;
import util.ErrorReport;
import util.PairwiseAligner;
import symap.closeup.alignment.HitAlignment;
import symap.closeup.alignment.AbstractSequence;
import symap.track.Track;
import util.Utilities;

public class SequencePool extends DatabaseUser {
	final int CHUNK_SZ = backend.Constants.CHUNK_SIZE; // CAS504 was hardcoded below; 1000000
	
	// Note that target_seq and query_seq refer to indices of sequence 
	// segments not the sequences themselves.
	private static final String MRK_ALIGNMENT_QUERY = 
		"SELECT h.strand,h.start1,h.end1,h.query_seq,h.target_seq,s.seq,h.grp2_idx "
		+ "FROM mrk_hits AS h "
		+ "JOIN mrk_seq AS s "
		+ "WHERE h.idx=? AND h.proj1_idx=s.proj_idx AND h.marker=s.marker";

	private static final String BES_ALIGNMENT_QUERY = 
		"SELECT h.strand,h.start1,h.end1,h.query_seq,h.target_seq,s.seq,h.grp2_idx "
		+ "FROM bes_hits AS h " 
		+ "JOIN bes_seq AS s "
		+ "WHERE h.idx=? AND h.proj1_idx=s.proj_idx AND h.clone=s.clone AND h.bes_type=s.type";
	
	private static final String PSEUDO_ALIGNMENT_QUERY = 
		"SELECT h.strand,h.start1,h.end1,h.query_seq,h.target_seq,null,h.grp2_idx,h.grp1_idx "
		+ "FROM pseudo_hits AS h WHERE h.idx=?";
	
	private static final String PSEUDO_SEQ_QUERY2 =
		"SELECT SUBSTRING(seq FROM ? FOR ?) AS p_seq "
		+ "FROM pseudo_seq2 AS s " + "WHERE s.grp_idx=? AND s.chunk=?";

	private ProjectProperties pp;

	public SequencePool(DatabaseReader dr, ProjectProperties pp)
	{
		super(dr);
		this.pp = pp;
	}

	public synchronized void close() {
		super.close();
	}

	public synchronized void clear() {
		super.close();
	}
	
	public synchronized HitAlignment[] getHitAlignments(Track src, List<AbstractHitData> hitDataList) 
	throws SQLException {
		List<HitAlignment> alignments = new LinkedList<HitAlignment>();
		HitData[] hd = null;
		int project, otherProject, content;
		Comparator<HitData> comp = HitData.getPseudoPositionComparator();
		
		for (AbstractHitData data : hitDataList) {
			hd = data.getHitData();
			Arrays.sort(hd, comp);
			project = data.getProject1();
			content = data.getContent1();
			otherProject = (project == src.getProject() ? data.getProject2() : project);
			
			for (int i = 0; i < hd.length; i++) { // for each hit
				HitQueryResults hqr = getHitQueryResults(src, hd[i], data);
				
				for (int j = 0; j < hqr.matchCount; j++) { // creates a HitAlignment for each sub-block of the hit
					String qSeq = extractSequence(hqr.querySeq,  hqr.queryIndices[j],  hqr.queryStart);
					String tSeq = extractSequence(hqr.targetSeq, hqr.targetIndices[j], hqr.targetStart);
							
					if (hqr.revComplementQuery)
						qSeq = Utilities.revComplement(qSeq);
					
					if (hqr.revComplementTarget)
						tSeq = Utilities.revComplement(tSeq);
					
					HitAlignment ha = new HitAlignment(
							hd[i],
							(data.getMapType() != Mapper.PSEUDO2PSEUDO ? pp.getDisplayName(project) : null),
							(data.getMapType() != Mapper.PSEUDO2PSEUDO ? content : -1), // contig # only for FPC-FPC/PSEUDO
							hqr.strand,
							pp.getDisplayName(otherProject),
							new AbstractSequence(qSeq, hqr.strand.charAt(2)), 
							new AbstractSequence(tSeq, hqr.strand.charAt(0)),
							extractStart(hqr.queryIndices[j]),
							extractEnd(hqr.queryIndices[j]),
							extractStart(hqr.targetIndices[j]),
							extractEnd(hqr.targetIndices[j]),
							hqr.targetWidth);
					 
					if (ha != null) {
						alignments.add(ha);
					}
				}
			}
			
		}
		return (HitAlignment[]) alignments.toArray(new HitAlignment[0]);
	}
	
	private String extractSequence(String sequence, String index_set, int base) {
		int start = extractStart(index_set) - base;	
		int end = extractEnd(index_set) - base;
		if (start < 0 || end >= sequence.length()) {
			System.err.println("extractSequence: out of bounds: "
					+ "start=" + start + " end=" + end + " base=" + base + " index_set=" + index_set + " length=" + sequence.length());
			return null;
		}
		return sequence.substring(start, end+1);
	}

	private void doAlignment(HitQueryResults rhq) {
		try {
			String strQuery = rhq.querySeq.toLowerCase();
			String strTarget = rhq.targetSeq.toLowerCase();
			
			PairwiseAligner PA = new PairwiseAligner();
			PA.DPalign(strQuery, strTarget);
			
			String queryAlign = PA.getHorzResult('-');
			String targetAlign = PA.getVertResult('-');
			
			// Note: queryAlign and targetAlign will have the same length
			assert(queryAlign.length() == targetAlign.length());

			// Determine sub-block indices
			int start1, end1, start2, end2;
			end1 = start1 = rhq.queryStart;
			end2 = start2 = rhq.targetStart;
			Vector<String> indices1 = new Vector<String>();
			Vector<String> indices2 = new Vector<String>();
			
			// Find sub-blocks
			for (int i = 0;  i < queryAlign.length();  i++) {
				boolean isQGap = (queryAlign.charAt(i)  == '-' || queryAlign.charAt(i)  == '~');
				boolean isTGap = (targetAlign.charAt(i) == '-' || targetAlign.charAt(i) == '~');
				
				if (isQGap || isTGap) {
					if (end1 > start1 && end2 > start2) {
						indices1.add(start1 + ":" + (end1-1));
						indices2.add(start2 + ":" + (end2-1));
						start1 = end1;
						start2 = end2;
					}
					if (isQGap) start2 = ++end2;
					if (isTGap) start1 = ++end1;
				}
				else {
					end1++;
					end2++;
				}
			}
			
			// Save final sub-block (or whole match if no sub-blocks)
			indices1.add(start1 + ":" + (end1-1));
			indices2.add(start2 + ":" + (end2-1));
			
			rhq.queryIndices  = indices1.toArray(new String[0]);
			rhq.targetIndices = indices2.toArray(new String[0]);
	    }
	    catch (Exception e) {
	    	e.printStackTrace();
	    }
	    System.gc(); // try to free unused memory
	}

	// pseudo-pseudo closeup
	private HitQueryResults getHitQueryResults(Track src, HitData hd, AbstractHitData ahd) 
	throws SQLException {
		String query = null; 
		Statement stat = null;
		ResultSet rs = null;
		HitQueryResults hqr = new HitQueryResults();
		
		if (hd instanceof FPCPseudoData.PseudoMarkerData)
			query = MRK_ALIGNMENT_QUERY;
		else if (hd instanceof FPCPseudoData.PseudoBESData)
			query = BES_ALIGNMENT_QUERY;
		else if (hd instanceof PseudoPseudoData.PseudoHitData)
			query = PSEUDO_ALIGNMENT_QUERY;
		
		query = setLong(query, hd.getID());
		try {
			stat = createStatement();
			rs = stat.executeQuery(query);
			if (rs.next()) {
				String query_seq = rs.getString(4);  // string list of sub-block start/end values
				String target_seq = rs.getString(5); // string list of sub-block start/end values
				int grp2_idx = rs.getInt(7);
				hqr.strand = rs.getString(1);
				
				int grp1_idx = -1;
				if (rs.getMetaData().getColumnCount() >= 8) // only true for PSEUDO_ALIGNMENT_QUERY
					grp1_idx = rs.getInt(8);
				
				// use default for bad strand value
				if (hqr.strand == null || hqr.strand.length() < 3) {
					System.err.println("Invalid strand value '" + hqr.strand + "' for hit idx=" + hd.getID());
					hqr.strand = "+/+";
				}
				
				int start1 = (ahd.isSwapped() ? hd.getStart2() : hd.getStart1());
				int end1   = (ahd.isSwapped() ? hd.getEnd2()   : hd.getEnd1());
				int start2 = (ahd.isSwapped() ? hd.getStart1() : hd.getStart2());
				int end2   = (ahd.isSwapped() ? hd.getEnd1()   : hd.getEnd2());
				if (ahd.isSwapped()) {
					String strTemp = query_seq;
					query_seq = target_seq;
					target_seq = strTemp;
					int nTemp = grp1_idx;
					grp1_idx = grp2_idx;
					grp2_idx = nTemp;
					hqr.strand = Utilities.reverseString(hqr.strand);
				}
				
				if (hd instanceof PseudoPseudoData.PseudoHitData) { 
					if (query_seq  == null || query_seq.length()  == 0 ||
						target_seq == null || target_seq.length() == 0)
					{
						// No sub-blocks so fake a big one for whole hit
						query_seq = start1 + ":" + end1;
						target_seq = start2 + ":" + end2;
					}
					
					// Set up hit query results structure
					hqr.queryIndices  = query_seq.split(",");
					hqr.targetIndices = target_seq.split(",");
					
					hqr.queryStart = start1;
					hqr.querySeq = loadPseudoSeq(start1 + ":" + end1, grp1_idx);
					
					hqr.targetStart = start2;
					hqr.targetWidth = end2 - start2 + 1;
					hqr.targetSeq = loadPseudoSeq(start2 + ":" + end2, grp2_idx);
					
					// Do alignment for each sub-block
					HitQueryResults temp = new HitQueryResults();
					Vector<String> newQIndices = new Vector<String>();
					Vector<String> newTIndices = new Vector<String>();
					int startOfs, endOfs;
					
					for (int i = 0;  i < hqr.queryIndices.length;  i++) {
						temp.queryStart = extractStart(hqr.queryIndices[i]);
						temp.queryEnd = extractEnd(hqr.queryIndices[i]);
						startOfs = temp.queryStart - hqr.queryStart;
						endOfs = extractEnd(hqr.queryIndices[i]) - hqr.queryStart;
						if ((startOfs+endOfs)>=hqr.querySeq.length()) continue; // CAS501 - fix for crash
						temp.querySeq = hqr.querySeq.substring(startOfs, endOfs+1);
						if (hqr.strand.charAt(0) == '-') {
							temp.querySeq = Utilities.revComplement(temp.querySeq);
						}

						temp.targetStart = extractStart(hqr.targetIndices[i]);
						temp.targetEnd = extractEnd(hqr.targetIndices[i]);
						startOfs = temp.targetStart - hqr.targetStart;
						endOfs = extractEnd(hqr.targetIndices[i]) - hqr.targetStart;
						temp.targetSeq = hqr.targetSeq.substring(startOfs, endOfs+1);
						if (hqr.strand.charAt(2) == '-') {
							temp.targetSeq = Utilities.revComplement(temp.targetSeq);
						}

						doAlignment(temp); // sets indices in temp
						
						for (String s : temp.queryIndices)
						{
							if (hqr.strand.charAt(0) == '-')
							{
								int st = extractStart(s);
								int end = extractEnd(s);
								int stNew = temp.queryStart + (temp.queryEnd - end);
								int endNew = temp.queryStart + (temp.queryEnd - st);
								s = stNew + ":" + endNew;
 							}
							newQIndices.add(s);
						}
						for (String s : temp.targetIndices)
						{
							if (hqr.strand.charAt(2) == '-')
							{
								int st = extractStart(s);
								int end = extractEnd(s);
								int stNew = temp.targetStart + (temp.targetEnd - end);
								int endNew = temp.targetStart + (temp.targetEnd - st);
								s = stNew + ":" + endNew;
 							}
							newTIndices.add(s);
						}
					}
					
					hqr.queryIndices  = newQIndices.toArray(new String[0]);
					hqr.targetIndices = newTIndices.toArray(new String[0]);
				}
				else { // FPC-to-PSEUDO (old code before pseudo-pseudo closeup additions)
					hqr.queryIndices = query_seq.split(",");
					hqr.targetIndices = target_seq.split(",");
					
					hqr.queryStart = start1;
					hqr.querySeq = rs.getString(6); // s.seq
					
					// for gene-gene backend feature (pseudo hits now may have subblocks but no sequence)
					if (hqr.querySeq == null && grp1_idx > 0) {
						String queryRange = getPseudoRange(query_seq);
						hqr.querySeq = loadPseudoSeq(queryRange, grp1_idx);
						hqr.queryStart = extractStart(queryRange);
					}
					
					// Need range of pseudo to extract seq from database
					String pseudoRange = getPseudoRange(target_seq);
					hqr.targetStart = extractStart(pseudoRange);			
					hqr.targetWidth = 
						    extractEnd(hqr.targetIndices[hqr.targetIndices.length-1])
							- extractStart(hqr.targetIndices[0]);
					hqr.targetSeq = loadPseudoSeq(pseudoRange, grp2_idx);
					

				}
				if (hqr.strand.charAt(0) == '-')
					hqr.revComplementQuery = true;
				if (hqr.strand.charAt(2) == '-')
					hqr.revComplementTarget = true;	
				
				hqr.matchCount = Math.min(hqr.queryIndices.length,hqr.targetIndices.length); // Success
			}
		}
		catch (SQLException e) {
			System.err.println("SQL exception acquiring alignment data: getHitQueryResults");
			System.err.println("Message: " + e.getMessage());
			throw e;
		} 
		finally {
			closeStatement(stat);
			closeResultSet(rs);
			if (hqr == null) close();
		}

		return hqr;
	}

	// String is start:end; id is grpID
	public String loadPseudoSeq(String indices, long id) throws SQLException 
	{		
		Statement stat = createStatement();
		ResultSet rs = null;
		String pseudoSeq = "";
		
		// Java and BLAT are zero based but mysql is 1 based, add one
		int start = extractStart(indices) + 1;
		int end = extractEnd(indices) + 1;
		if (start == -1 || end == -1) return "";
		
		if (start > end) {
			int temp = start;
			start = end;
			end = temp;
		}
		String query2="";
		long count = end - start + 1;
		long chunk = start / CHUNK_SZ;
		
		try {
			String seq;
			
			start = start % CHUNK_SZ;
			end = end % CHUNK_SZ;
			while (count > 0) {
				query2 = setLong(PSEUDO_SEQ_QUERY2, start); // Insert start position
				query2 = setLong(query2, count); // Insert length - indices are inclusive according to Will so add 1
				query2 = setLong(query2, id); // Insert id into the query	
				query2 = setLong(query2, chunk);
				
				rs = stat.executeQuery(query2);
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
		}
		catch (SQLException e) {
			System.err.println("SQL exception acquiring sequence data.");
			System.err.println("Message: " + e.getMessage());
			throw e;
		}
		finally {
			closeStatement(stat);
			closeResultSet(rs);
			if (rs == null) close();
		}

		if (pseudoSeq.equals("")) { // CAS5xx check was for null, so no error produced
			// this happens if the wrong alignment files are loaded
			String msg="Could not read sequence for " + indices;
			String info = query2 + "\nCount=" + count + 
					"\nChunk=" + chunk + " (" + CHUNK_SZ + ")" + "\nSeq=" + pseudoSeq;
			ErrorReport.print(msg, info);
			return "";
		}
		return pseudoSeq;
	}
	
	private String getPseudoRange(String indices) { // get full range from first start to last end
		if (indices.indexOf(",") == -1)
			return indices;
		
		String tokens[] = indices.split(",");
		int start = extractStart(tokens[0]);
		int end = extractEnd(tokens[tokens.length - 1]);
		if (start == -1 || end == -1)
			return "";
		
		return new String(start + ":" + end);
	}

	private int[] extractInt(String range) {
		String tokens[] = range.split(":");
		if (tokens.length != 2)
			return null;
		
		int[] out = new int[2];
		out[0] = new Integer(tokens[0]).intValue();
		out[1] = new Integer(tokens[1]).intValue();
		return out;
	}
	
	private int extractStart(String range) {
		return extractInt(range)[0];
	}
	
	private int extractEnd(String range) {
		return extractInt(range)[1];
	}
	
	static class HitQueryResults {
		private int matchCount;
		private String strand;
		private int queryStart; 
		private int queryEnd;
		private int targetStart;
		private int targetEnd;
		private int targetWidth;
		private String querySeq, targetSeq;
		private String[] queryIndices, targetIndices;
		
		private boolean revComplementQuery;
		private boolean revComplementTarget;

		protected HitQueryResults(/*String name*/) {
			this.matchCount = 0; // set to zero if bad query call
			this.targetSeq = null;
			this.targetStart = 0;
			this.revComplementQuery = false;
			this.revComplementTarget = false;
		}
	}
}
