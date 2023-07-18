package symap.mapper;

import java.util.Vector;

import java.sql.ResultSet;

import database.DBconn2;
import props.ProjectPool;
import symap.sequence.Sequence;
import symap.sequence.Track;
import util.ErrorReport;

/**
 * The pool of Mapper hits.
 * CAS520 FPC is partially removed; CAS531 remove cache; CAS521 more FPC removed; CAS541 extends DBAbsUser
 * CAS543 reorder HitData and expand
 */
public class MapperPool {
	private DBconn2 dbc2;
	private ProjectPool projPool; // properties
	
	public MapperPool(DBconn2 dbc2, ProjectPool projPool) {  
		this.dbc2 = dbc2;
		this.projPool = projPool;
	}

	public boolean hasPair(Track t1, Track t2) {
		return projPool.hasProjectPair(t1.getProject(),t2.getProject());
	}

	public SeqHits setData(Mapper mapper, Track t1, Track t2, HfilterData hf) {
		SeqHits seqHitObj;
		
		if (hasPair(t1,t2)) seqHitObj = setSeqHitData(mapper, (Sequence)t1, (Sequence)t2);
		else                seqHitObj = setSeqHitData(mapper, (Sequence)t2, (Sequence)t1);
		
		return seqHitObj;
	}

	/*************************************************************************
	 * Update HitData
	 */
	private SeqHits setSeqHitData(Mapper mapper, Sequence st1, Sequence st2)  {
		int stProject1 = st1.getProject();
		int stProject2 = st2.getProject();
		int group1 = st1.getGroup();
		int group2 = st2.getGroup();
		String chr1="?", chr2="?";

		Vector <HitData> hitList = new Vector <HitData>();
		try {
			chr1 = dbc2.executeString("select name from xgroups where idx=" + group1); // CAS517 add chrname for display
			chr2 = dbc2.executeString("select name from xgroups where idx=" + group2);
			
			// CAS515 add cvgpct, countpct, CAS516 add corr; CAS504 bh.block_idx->b.blocknum, 
			// CAS520 change evalue to hitnum, add runnum, CAS540 add score, CAS543 add annot1 and annot2, put in DB order
			String query = "SELECT h.idx, h.hitnum, h.pctid, h.cvgpct, h.countpct, h.score, h.gene_overlap, "
				+ "h.annot1_idx, h.annot2_idx, h.strand, h.start1, h.end1, h.start2, h.end2, h.query_seq, h.target_seq,   " 
				+ "h.runnum, h.runsize,  b.blocknum, b.corr " 
				+ "FROM pseudo_hits AS h "
				+ "LEFT JOIN pseudo_block_hits AS bh ON (bh.hit_idx=h.idx) "
				+ "LEFT JOIN blocks as b on (b.idx=bh.block_idx) "  // CAS505 added for blocknum
				+ "WHERE h.grp1_idx=" + group1 + " AND h.grp2_idx="+ group2 +" order by h.start1"; // CAS520 add order by;
			
			ResultSet rs = dbc2.executeQuery(query);
			
			// 1 idx, 2 hitnum, 3 pctid, 4 cvgpct (sim), 5 countpct (merge), 6 score, 7 gene_overlap
			// 8 annot1_idx, 9 annot2_idx, 10 strand, 11 start1, 12 end1, 13 start2, 14 end2, 15 query_seq, 16 target_seq, 
			// 17 runnum, 18 runsize, 19 block, 20 bcorr	
			while (rs.next()) {
				HitData temp = 		new HitData(mapper,
						rs.getLong(1),		// long id 		
						rs.getInt(2),		// int hitnum 	
						rs.getDouble(3),	// double pctid	
						rs.getInt(4),		// int cvgpct->avg %sim 
						rs.getInt(5),       // int countpct -> nMergedHits 
						rs.getInt(6),		// CAS540 h.score 
						rs.getInt(7),		// int gene_overlap   
						
						rs.getInt(8),		// int annot1_idx 
						rs.getInt(9),       // int annot2_idx 
						rs.getString(10),	// String strand
						rs.getInt(11),		// int start1	
						rs.getInt(12),		// int end1		
						rs.getInt(13),		// int start2	
						rs.getInt(14),		// int end2		
						rs.getString(15),	// String query_seq 	
						rs.getString(16),	// String target_seq 	
						
						rs.getInt(17),		// int runnum
						rs.getInt(18),		// int runsize
						rs.getInt(19),		// int b.block	
						rs.getDouble(20),	// int b.corr 
						chr1, chr2	    // CAS517 add chr1, chr2 			
						);		
				hitList.add(temp);
			}
			rs.close();
			if (hitList.size()==0)  System.out.println("No hits for " + st1.getTitle() + " to " + st2.getTitle());
			
			SeqHits seqHitObj = new SeqHits(stProject1,group1,stProject2, group2, mapper, st1, st2, hitList, false);
			hitList.clear();
			return seqHitObj;
		} catch (Exception e) {ErrorReport.print(e, "Get hit data");return null;}
	}
}
