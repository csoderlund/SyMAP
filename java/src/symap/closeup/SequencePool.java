package symap.closeup;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.Comparator;
import java.util.Vector;

/*import org.biojava.bio.alignment.NeedlemanWunsch;
import org.biojava.bio.alignment.SequenceAlignment;
import org.biojava.bio.alignment.SubstitutionMatrix;
import org.biojava.bio.seq.DNATools;
import org.biojava.bio.seq.Sequence;
import org.biojava.bio.symbol.AlphabetManager;
import org.biojava.bio.symbol.FiniteAlphabet;
import org.biojava.bio.symbol.SymbolList;
import org.biojava.bio.symbol.Alignment;
*/

import symap.pool.DatabaseUser;
import symap.mapper.HitData;
import symap.mapper.FPCPseudoData;
import symap.mapper.Mapper;
import symap.mapper.PseudoPseudoData;
import symap.mapper.AbstractHitData;
import symap.pool.ProjectProperties;
import util.DatabaseReader;
import util.PairwiseAligner;
import symap.closeup.alignment.HitAlignment;
import symap.closeup.alignment.AbstractSequence;
import symap.track.Track;
import util.Utilities;

public class SequencePool extends DatabaseUser {
	// ASD start of modifications for database changes 5/5/2006 ----------------
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
	
	private static final String PSEUDO_SEQ_QUERY = 
		"SELECT SUBSTRING(seq FROM ? FOR ?) AS p_seq "
		+ "FROM pseudo_seq AS s " + "WHERE s.grp_idx=?";
	// ASD End of modifications 5/5/2006 ---------------------------------------

	// mdb added PSEUDO_ALIGNMENT_QUERY 7/25/07 #121 - mimic MRK/BES
	private static final String PSEUDO_ALIGNMENT_QUERY = 
		"SELECT h.strand,h.start1,h.end1,h.query_seq,h.target_seq,null,h.grp2_idx,h.grp1_idx "
		+ "FROM pseudo_hits AS h WHERE h.idx=?";
	
	// mdb added PSEUDO_SEQ_QUERY2 5/8/07 #114
	private static final String PSEUDO_SEQ_QUERY2 =
		"SELECT SUBSTRING(seq FROM ? FOR ?) AS p_seq "
		+ "FROM pseudo_seq2 AS s " + "WHERE s.grp_idx=? AND s.chunk=?";

// mdb removed 1/8/09
//	// ASD modified to include strand (cannot tell from start and end anymore)
//	private static final String GENE_QUERY = 
//		"SELECT name,start,end,strand "
//		+ "FROM " + PSEUDO_ANNOT_TABLE + " "
//		+ "WHERE grp_idx=? AND type='gene' AND "
//		+ "      ( (start<end AND NOT (end<? OR start>?)) OR (end<=start AND NOT (start<? OR end>?)) ) "
//		+ "ORDER BY name";

// mdb removed 12/15/08	
//	private static final String EXON_QUERY = 
//		"SELECT start,end " + "FROM " + PSEUDO_ANNOT_TABLE + " "
//		+ "WHERE grp_idx=? AND type='exon' AND name=?";
	
// mdb removed 1/8/09	
//	// mdb added 12/15/08 - query on exon coords instead of gene name
//	private static final String EXON_QUERY = 
//		"SELECT start,end "
//		+ "FROM " + PSEUDO_ANNOT_TABLE + " "
//		+ "WHERE grp_idx=? AND type='exon' AND "
//		+ "      ( (start<end AND NOT (end<? OR start>?)) OR (end<=start AND NOT (start<? OR end>?)) ) ";

	private ProjectProperties pp;
	//private ListCache mrkCache, besCache, geneCache; // mdb removed 12/15/08 - not worth added complexity

// mdb removed 12/15/08
//	public SequencePool(DatabaseReader dr, ProjectProperties pp,
//			ListCache besAlignmentCache, ListCache mrkAlignmentCache,
//			ListCache geneAlignmentCache) {
//		super(dr);
//		this.pp = pp;
//		this.mrkCache = mrkAlignmentCache;
//		this.besCache = besAlignmentCache;
//		this.geneCache = geneAlignmentCache;
//	}
	
	// mdb added 12/15/08
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
// mdb removed 12/15/08		
//		mrkCache.clear();
//		besCache.clear();
//		geneCache.clear();
	}
	
/* mdb rewritten below 7/26/07 #121,134
	// ASD - new function to handle database changes 5/5/2006
	// ASD called by setView in closeUpDialog.java (setView called by constructor)
	public synchronized HitAlignment[] getHitAlignments(List fpcPseudoData)
	throws SQLException {
		List alignments = new LinkedList();
		HitData[] hd;
		FPCPseudoData data;
		int p, c, i;
		Comparator comp = HitData.getPseudoPositionComparator();
		Iterator iter = fpcPseudoData.iterator();
		while (iter.hasNext()) {
			data = (FPCPseudoData) iter.next();
			hd = data.getHitData();
			Arrays.sort(hd, comp);
			p = data.getProject1();
			c = data.getContig();
			for (i = 0; i < hd.length; i++) {
				if (hd[i] instanceof FPCPseudoData.PseudoMarkerData) {
					// First run query so get count then create the individual
					// graphics
					HitQueryResults hqr = getHitQueryResults(hd[i],
							MRK_ALIGNMENT_QUERY);
					for (int j = 0; j < hqr.matchCounts; j++) {
						//  ASD modified to add null protection (bad database entry will return null)
						HitAlignment ha = getMarkerHitAlignment(hd[i], p, c, hqr, j, i);
						if (ha != null) alignments.add(ha);
					}
				} else {
					HitQueryResults hqr = getHitQueryResults(hd[i],
							BES_ALIGNMENT_QUERY);
					for (int j = 0; j < hqr.matchCounts; j++) {
						HitAlignment ha = getBESHitAlignment(
								(FPCPseudoData.PseudoBESData) hd[i], p, c, hqr, j, i);
						if (ha != null) alignments.add(ha);
					}
				}
			}
		}
		return (HitAlignment[]) alignments.toArray(new HitAlignment[alignments.size()]);
	}
*/
	
	// mdb added 7/26/07 #121,134
	public synchronized HitAlignment[] getHitAlignments(Track src, List<AbstractHitData> hitDataList) // mdb added "src" arg 1/7/09
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
							
					// mdb added 12/29/08
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
// mdb removed 12/15/08	- not necessary to cache hit alignments for closeup	
//						if (hd[i] instanceof FPCPseudoData.PseudoMarkerData)
//							mrkCache.add(ha);
//						else if (hd[i] instanceof FPCPseudoData.PseudoBESData)
//							besCache.add(ha);
					}
				}
			}
			
		}
		return (HitAlignment[]) alignments.toArray(new HitAlignment[0]);
	}
	
/* mdb removed 7/26/07 #134
	// ASD modified to handle database 5/5/2006
	private MarkerHitAlignment getMarkerHitAlignment(HitData hd, int project,
			int contig, HitQueryResults hqr, int j, int uniqueId) throws SQLException {
		// ASD had to comment out or only one marker shows
//		MarkerHitAlignment mha = (MarkerHitAlignment) mrkCache 
//		.get(new MarkerHitAlignment(hd));
//		if (mha == null) {
		MarkerHitAlignment mha = (MarkerHitAlignment) getAlignments(hd, project, contig,
				MarkerHitAlignment.class, hqr, j, uniqueId);
		if (mha != null) mrkCache.add(mha);
//		}
		return mha;
	}
*/

/* mdb removed 7/26/07 #134
	// ASD modified to handle database change 5/5/2006
	private HitAlignment getBESHitAlignment(HitData hd, int project,
			int contig, HitQueryResults hqr, int j, int uniqueId) throws SQLException {
		// ASD: An error in Austin's original code (he used mrkCache here) exposed that
		//    this causes only one hit to display. I'm just pitching it for the moment
		//BESHitAlignment bha = (BESHitAlignment) besCache
		//		.get(new BESHitAlignment(hd));
		//if (bha == null) {
		BESHitAlignment bha = (BESHitAlignment) getAlignments(hd, project, contig,
				BESHitAlignment.class, hqr, j, uniqueId);
		if (bha != null) besCache.add(bha);
		//}
		return bha;
	}
*/	

/* mdb rewritten below 7/26/07 #121,134
	// ASD modified to handle database change 5/5/2006
	private HitAlignment getAlignments(HitData hd, int project, int contig,
			Class alignClass, HitQueryResults hqr, int j, int uniqueId) 
		throws SQLException 
	{
		Object args[] = new Object[] { hd,
				(pp != null ? pp.getDisplayName(project) : null),
				new Integer(contig), null, null, null, null, null, null, null,
				null, null, null, null, null };
		args[3] = new String(hqr.strand);
		args[4] = new Integer(hqr.start1); // ASD note - Integer needed because params needs objects - will not make an array with basic structure ints
		args[5] = new Integer(hqr.end1);
		String s1 = extractCharSequence(hqr.cloneSeq, hqr.cloneIndices[j], 0);
		args[6] = s1;		
		String s2 = extractCharSequence(hqr.pseudoSeq, hqr.pseudoIndices[j], hqr.pseudoStart);
		if (s1 == null || s2 == null) return null; // Database problem
		if (hqr.strand.indexOf("-") != -1) {
			args[7] = Utilities.revComplement(s2);
			args[11] = new Integer(extractInt(hqr.pseudoIndices[j], 2));
			args[12] = new Integer(extractInt(hqr.pseudoIndices[j], 1));
		} else {
			args[7] = s2;
			args[11] = new Integer(extractInt(hqr.pseudoIndices[j], 1));
			args[12] = new Integer(extractInt(hqr.pseudoIndices[j], 2));
		}

		// args[8] = new Integer (Math.min(s1.length(), s2.length())); //
		// Whoops, he wanted the full marker/hit
		args[8]  = new Integer(hqr.cloneSeq.length()); // FIXME probably not using anymore!
		args[9]  = new Integer(extractInt(hqr.cloneIndices[j], 1));
		args[10] = new Integer(extractInt(hqr.cloneIndices[j], 2));
		args[13] = new Integer(hqr.pseudoWidth);
		args[14] = new Integer(uniqueId);
		
		HitAlignment ha = null;
		Class params[] = new Class[] { HitData.class, String.class,
				Integer.TYPE, String.class, Integer.TYPE, Integer.TYPE,
				String.class, String.class, Integer.TYPE, Integer.TYPE,
				Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE };
		try {
			ha = (HitAlignment) alignClass.getConstructor(params).newInstance(args);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return ha;
	}*/

	// ASD added to handle database change 5/5/2006
	private String extractSequence(String sequence, String index_set, int base) {
		int start = extractStart(index_set) - base;	
		int end = extractEnd(index_set) - base;
		if (start < 0 || end >= sequence.length()) {
			System.err.println("extractSequence: out of bounds: "
					+ "start=" + start + " end=" + end + " base=" + base + " index_set=" + index_set + " length=" + sequence.length());
			return null;
		}
		//System.out.println("extractCharSequence: "+sequence.substring(start, end)+" "+start+" "+end);
		return sequence.substring(start, end+1);
	}

// mdb removed 1/8/08 - replaced by CloseUpDialog.getGeneAlignments()
//	public synchronized GeneAlignment[] getGeneAlignments(int group, int start, int end) throws SQLException 
//	{
//// mdb removed 12/15/08	- disabled gene caching for closeup
////		GeneAlignments ga = (GeneAlignments) geneCache.get(new GeneAlignments(group, start, end));
////		if (ga != null)
////			return ga;
//		
//		// Get gene alignments
//		List<GeneAlignment> genes = new LinkedList<GeneAlignment>();
//		List<Exon> exons = new LinkedList<Exon>();
//		Statement stat = null;
//		ResultSet rs = null;
//		String query;
//		try {
//			// Query genes
//			stat = createStatement();   
//			query = setInt(setInt(setInt(setInt(setInt(GENE_QUERY,group),start),end),start),end);
//			rs = stat.executeQuery(query);
//			while (rs.next()) {
//				GeneAlignment ga = new GeneAlignment(group, rs.getString(1), rs.getInt(2), rs.getInt(3), !rs.getString(4).equals("-"));
//				//if (!genes.contains(ga)) // mdb added condition 12/19/08 - work around redundant database entries
//				genes.add(ga);
//			}
//			closeResultSet(rs);
//			
//			// Query exons
//			//query = setInt(EXON_QUERY, group); // mdb removed 12/15/08
//			for (GeneAlignment cga : genes) {
//				//rs = stat.executeQuery(setString(query, cga.getName())); // mdb removed 12/15/08
//				int gStart = cga.getStart();
//				int gEnd = cga.getEnd();
//				query = setInt(setInt(setInt(setInt(setInt(EXON_QUERY,group),gStart),gEnd),gStart),gEnd); // mdb added 12/15/08
//				rs = stat.executeQuery(query);
//				while (rs.next()) {
//					Exon e = new Exon(rs.getInt(1), rs.getInt(2));
//					//if (!exons.contains(e)) // mdb added condition 12/19/08 - work around redundant database entries
//					exons.add(e);
//				}
//				cga.setExons((Exon[]) exons.toArray(new Exon[0]));
//				exons.clear();
//				closeResultSet(rs);
//			}
//		} 
//		catch (SQLException e) {
//			System.err.println("SQL exception acquiring exon data.");
//			System.err.println("Message: " + e.getMessage());
//			throw e;
//		}
//		finally {
//			if (stat != null) closeStatement(stat);
//			if (rs != null) closeResultSet(rs);
//			close();
//		}
//		
//// mdb removed 12/15/08		
////		ga = new GeneAlignments(group, start, end, (GeneAlignment[]) genes.toArray(new GeneAlignment[genes.size()]));
////		geneCache.add(ga);
////		return ga.getAlignments();
//		
//		// mdb added 12/15/08
//		return (GeneAlignment[]) genes.toArray(new GeneAlignment[0]);
//	}
	
// mdb removed 12/15/08	
//	private static class GeneAlignments {
//		private GeneAlignment[] alignments;
//		private int group;
//		private int start, end;
//
//		public GeneAlignments(int group, int start, int end) {
//			this.group = group;
//			this.start = start;
//			this.end = end;
//		}
//
//		public GeneAlignments(int group, int start, int end,
//				GeneAlignment[] alignments) {
//			this(group, start, end);
//			this.alignments = alignments;
//		}
//
//		public GeneAlignment[] getAlignments() {
//			return alignments == null ? new GeneAlignment[0] : alignments;
//		}
//
//		public boolean equals(Object obj) {
//			if (obj instanceof GeneAlignments) {
//				GeneAlignments g = (GeneAlignments) obj;
//				return g.group == group && g.start == start && g.end == end;
//			}
//			return false;
//		}
//	}
	
	// mdb added 12/15/08 for pseudo-pseudo
	private static final String strMatrix = 
		"   A  C  G  T \n" +
		"A  1 -1 -1 -1 \n" +
		"C -1  1 -1 -1 \n" +
		"G -1 -1  1 -1 \n" +
		"T -1 -1 -1  1 \n";
	
	// mdb added 12/15/08 for pseudo-pseudo
	private void doAlignment(HitQueryResults rhq) {
		try {
			String strQuery = rhq.querySeq.toLowerCase();
			String strTarget = rhq.targetSeq.toLowerCase();
			
			PairwiseAligner PA = new PairwiseAligner();
			PA.DPalign(strQuery, strTarget);
			
			String queryAlign = PA.getHorzResult('-');
			String targetAlign = PA.getVertResult('-');
			//System.out.println("###########\n" + queryAlign);
			//System.out.println("***********\n" + targetAlign);
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
			//System.out.println(indices1.lastElement() + "::" + indices2.lastElement());
			
			rhq.queryIndices  = indices1.toArray(new String[0]);
			rhq.targetIndices = indices2.toArray(new String[0]);
	    }
	    catch (Exception e) {
	    	e.printStackTrace();
	    }
	    System.gc(); // try to free unused memory
	}
/*	private void doAlignmentOld(HitQueryResults rhq) {
		System.gc(); // try to free unused memory
		try {
			String strQuery = rhq.querySeq.toLowerCase();
			String strTarget = rhq.targetSeq.toLowerCase();
			
			SubstitutionMatrix matrix = new SubstitutionMatrix(
					(FiniteAlphabet)AlphabetManager.alphabetForName("DNA"),
					strMatrix,
					null);
			SequenceAlignment aligner = new NeedlemanWunsch( 
					0, 		// match
			        15,//3,	// replace
			        15,//2, // insert
			        15,//2,	// delete
			        1,      // gapExtend
			        matrix);// SubstitutionMatrix
		    Sequence query  = DNATools.createDNASequence(strQuery, "query");
		    Sequence target = DNATools.createDNASequence(strTarget, "target");
		    
		    Alignment alignment = aligner.getAlignment(query, target);
		    //System.out.println("alignment:\n" + aligner.getAlignmentString());
			Iterator<SymbolList> iter = alignment.symbolListIterator();
			String queryAlign = iter.next().seqString();
			String targetAlign = iter.next().seqString();
			// Note: queryAlign and targetAlign will have the same length

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

*/
	// mdb rewritten 12/29/08 for pseudo-pseudo closeup
	private HitQueryResults getHitQueryResults(Track src, HitData hd, AbstractHitData ahd) // mdb added "src" and "ahd" args 1/7/09
	throws SQLException {
		String query = null; // mdb added 7/26/07 #134
		Statement stat = null;
		ResultSet rs = null;
		HitQueryResults hqr = new HitQueryResults();
		
		// mdb added 7/26/07 #121,134
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
				
				// mdb added 12/18/08 - use default for bad strand value
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
				
				if (hd instanceof PseudoPseudoData.PseudoHitData) { // PSEUDO-to-PSEUDO // mdb rewritten 8/25/09 #167
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
//					if (hqr.strand.charAt(0) == '-')
//						hqr.querySeq = Utilities.revComplement(hqr.querySeq);
					
					hqr.targetStart = start2;
					hqr.targetWidth = end2 - start2 + 1;
					hqr.targetSeq = loadPseudoSeq(start2 + ":" + end2, grp2_idx);
//					if (hqr.strand.charAt(2) == '-')
//						hqr.targetSeq = Utilities.revComplement(hqr.targetSeq);
					
					// Do alignment for each sub-block
					HitQueryResults temp = new HitQueryResults();
					Vector<String> newQIndices = new Vector<String>();
					Vector<String> newTIndices = new Vector<String>();
					int startOfs, endOfs;
					
					for (int i = 0;  i < hqr.queryIndices.length;  i++) {
						//System.out.println(hqr.queryIndices[i] + "," + hqr.targetIndices[i]);
						
						temp.queryStart = extractStart(hqr.queryIndices[i]);
						temp.queryEnd = extractEnd(hqr.queryIndices[i]);
						startOfs = temp.queryStart - hqr.queryStart;
						endOfs = extractEnd(hqr.queryIndices[i]) - hqr.queryStart;
						temp.querySeq = hqr.querySeq.substring(startOfs, endOfs+1);
						if (hqr.strand.charAt(0) == '-')
						{
							temp.querySeq = Utilities.revComplement(temp.querySeq);
							//System.out.println("XXX Reverse comp query");
						}

						//System.out.println(startOfs+":"+endOfs+" "+temp.querySeq);
						
						temp.targetStart = extractStart(hqr.targetIndices[i]);
						temp.targetEnd = extractEnd(hqr.targetIndices[i]);
						startOfs = temp.targetStart - hqr.targetStart;
						endOfs = extractEnd(hqr.targetIndices[i]) - hqr.targetStart;
						temp.targetSeq = hqr.targetSeq.substring(startOfs, endOfs+1);
						if (hqr.strand.charAt(2) == '-')
						{
							temp.targetSeq = Utilities.revComplement(temp.targetSeq);
							//System.out.println("XXX Reverse comp target");
						}

						//System.out.println(startOfs+":"+endOfs+" "+temp.targetSeq);
						
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
					
					// mdb added 5/5/09 for gene-gene backend feature (pseudo hits now may have subblocks but no sequence)
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
				//System.out.println("querySeq = " + rhq.querySeq);
				//System.out.println("targetSeq = " + rhq.targetSeq);
				//System.out.println("matchCount = " + rhq.matchCount);
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

	public String loadPseudoSeq(String indices, long id)
	throws SQLException {
		Statement stat = null;
		ResultSet rs = null;
		String pseudoSeq = "";//= null; // mdb changed 5/8/07
		// Java and BLAT are zero based but mysql is 1 based, add one
		int start = extractStart(indices) + 1;
		int end = extractEnd(indices) + 1;
		if (start == -1 || end == -1)
			return "";
		
		// mdb added 12/17/08
		if (start > end) {
			int temp = start;
			start = end;
			end = temp;
		}
		
		String query = setLong(PSEUDO_SEQ_QUERY, start);
		query = setLong(query, end - start + 1);
		query = setLong(query, id);
		
		try {
			//System.out.println("extractPseudoRange: query1: " + query);
			stat = createStatement();
			rs = stat.executeQuery(query);
			if (rs.next()) 
				pseudoSeq = rs.getString(1);
		} catch (SQLException e) { 
			// mdb added 5/8/07 #114 -- BEGIN ********************************
			final int CHUNK_SZ = 1000000;
			try {
				long count = end - start + 1;
				long chunk = start / CHUNK_SZ;
				String seq;
				String query2;
				
				start = start % CHUNK_SZ;
				end = end % CHUNK_SZ;
				while (count > 0) {
					query2 = setLong(PSEUDO_SEQ_QUERY2, start); // Insert start position
					query2 = setLong(query2, count); // Insert length - indices are inclusive according to Will so add 1
					query2 = setLong(query2, id); // Insert id into the query	
					query2 = setLong(query2, chunk);
					//System.out.println("extractPseudoRange: start=" + start + " end=" + end + " query2: " + query2);
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
			catch (SQLException e2) {
				System.err.println("SQL exception acquiring pseudo data.");
				System.err.println("Message: " + e.getMessage());
				throw e2;
			}
			// mdb added 5/8/07 #114 -- END ************************************
		} 
		finally {
			closeStatement(stat);
			closeResultSet(rs);
			if (rs == null) close();
		}

		if (pseudoSeq == null) {
			System.err.println("Pseudo sequence not found for query = " + query);
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
	
	// ASD added helper class (defines a structure) for database changes 5/5/2006
	// mdb renamed members from "clone/pseudo" to "query/target" 12/31/08
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
		
		// mdb added 12/30/08 for pseudo-pseudo closeup
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
