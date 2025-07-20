package symap.mapper;

import java.util.Vector;

import java.sql.ResultSet;

import database.DBconn2;
import props.PropsDB;
import symap.Globals;
import symap.sequence.Sequence;
import util.ErrorReport;

/**
 * Create an array of Hits from the DB and passes them to a SeqHit object
 */
public class MapperPool {
	private DBconn2 dbc2;
	private PropsDB projPairs; // properties
	
	public MapperPool(DBconn2 dbc2, PropsDB projPool) {  // Created when Mapper is created
		this.dbc2 = dbc2;
		this.projPairs = projPool;
	}

	protected boolean hasPair(Sequence t1, Sequence t2) {
		return projPairs.hasPair(t1.getProject(),t2.getProject());
	}

	protected SeqHits setData(Mapper mapper, Sequence t1, Sequence t2) { // Mapper.isInit
		SeqHits seqHitObj;
		
		if (hasPair(t1,t2)) seqHitObj = setSeqHitData(mapper, (Sequence)t1, (Sequence)t2);
		else                seqHitObj = setSeqHitData(mapper, (Sequence)t2, (Sequence)t1);
		
		return seqHitObj;
	}

	/*************************************************************************
	 * Update HitData
	 */
	private SeqHits setSeqHitData(Mapper mapper, Sequence st1, Sequence st2)  {
		int grpIdx1 = st1.getGroup();
		int grpIdx2 = st2.getGroup();
		
		try {
			int n = dbc2.executeCount("select count(*) from pseudo_hits where grp1_idx="+grpIdx1 + " AND grp2_idx="+ grpIdx2);
			Vector <HitData> hitList = new Vector <HitData>(n);
			
			String query = "SELECT h.idx, h.hitnum, h.pctid, h.cvgpct, h.countpct, h.score, h.htype, h.gene_overlap, "
				+ "h.annot1_idx, h.annot2_idx, h.strand, h.start1, h.end1, h.start2, h.end2, h.query_seq, h.target_seq,   " 
				+ "h.runnum, h.runsize,  b.blocknum, b.corr " 
				+ "FROM pseudo_hits AS h "
				+ "LEFT JOIN pseudo_block_hits AS bh ON (bh.hit_idx=h.idx) "
				+ "LEFT JOIN blocks as b on (b.idx=bh.block_idx) "  
				+ "WHERE h.grp1_idx=" + grpIdx1 + " AND h.grp2_idx="+ grpIdx2 +" order by h.start1"; 
			
			ResultSet rs = dbc2.executeQuery(query);
			
			// 1 idx, 2 hitnum, 3 pctid, 4 cvgpct (sim), 5 countpct (merge), 6 score, 7 gene_overlap
			// 8 annot1_idx, 9 annot2_idx, 10 strand, 11 start1, 12 end1, 13 start2, 14 end2, 15 query_seq, 16 target_seq, 
			// 17 runnum, 18 runsize, 19 block, 20 bcorr	
			while (rs.next()) {
				int i=1;
				HitData temp = 		new HitData(mapper,
						rs.getInt(i++),		// int id 		
						rs.getInt(i++),		// int hitnum 	
						rs.getDouble(i++),	// double pctid	
						rs.getInt(i++),		// int cvgpct->avg %sim 
						rs.getInt(i++),     // int countpct -> nMergedHits 
						rs.getInt(i++),		// h.score 
						rs.getString(i++),	// h.htype
						rs.getInt(i++),		// int gene_overlap   
						
						rs.getInt(i++),		// int annot1_idx 
						rs.getInt(i++),     // int annot2_idx 
						rs.getString(i++),	// String strand
						rs.getInt(i++),		// int start1	
						rs.getInt(i++),		// int end1		
						rs.getInt(i++),		// int start2	
						rs.getInt(i++),		// int end2		
						rs.getString(i++),	// String query_seq 	
						rs.getString(i++),	// String target_seq 	
						
						rs.getInt(i++),		// int runnum
						rs.getInt(i++),		// int runsize
						rs.getInt(i++),		// int b.block	
						rs.getDouble(i++)	// int b.corr     			
						);		
				hitList.add(temp);
			}
			rs.close();
			if (hitList.size()==0) Globals.prt("No hits for " + st1.getTitle() + " to " + st2.getTitle());
			
			if (projPairs.hasPseudo(st1.getProject(), st2.getProject())) {
				zeroPseudo(1, grpIdx1, hitList);
				zeroPseudo(2, grpIdx2, hitList);
			}
			SeqHits seqHitObj = new SeqHits(mapper, st1, st2, hitList);
			hitList.clear();
			
			if (Globals.DEBUG) {
				String x = String.format("MP: Total>1 hits %,d: %d (%d)  Merged: %d (%d)", 
						hitList.size(), HitData.cntTotal, HitData.cntTotalSH, HitData.cntMerge, HitData.cntMergeSH);
				Globals.dprt(x);
				Globals.dprt(st1.toString() + "\n" + st2.toString());
			}
			HitData.cntTotal= HitData.cntMerge = HitData.cntTotalSH= HitData.cntMergeSH = 0;
			return seqHitObj;
		} catch (Exception e) {ErrorReport.print(e, "Get hit data");return null;}
	}
	/***********************************************************************
	 * Set numbered pseudo genes annot_idx to zero. 
	 * CAS570 everything works when HitData has annot_idx>0 for pseudo genes, but it could cause problems.
	 * It is not a problem because the search is always on gene_overlap=2,1,0.
	 * 
	 * All genes for a chromosome are loaded, hence, any idx after that for this chr (grpIdx) is pseudo.
	 * Even if another project is loaded, its idx's will not occur in this list.
	 */
	private void zeroPseudo(int which, int grpIdx, Vector <HitData> hitList) {
	try {
		int cnt=0;
		int maxGeneIdx = dbc2.executeCount("select max(pa.idx) from pseudo_annot as pa " // could also be min(idx) pseudo
				+ "join xgroups as g on pa.grp_idx=g.idx "
				+ "where pa.type='gene' and pa.grp_idx=" + grpIdx);
		for (HitData hd : hitList) {
			if (which==1 && hd.annot1_idx>maxGeneIdx)      {hd.annot1_idx=0; cnt++;}
			else if (which==2 && hd.annot2_idx>maxGeneIdx) {hd.annot2_idx=0; cnt++;}
		}
if (Globals.TRACE) {
	int pidx = dbc2.executeCount("select proj_idx from xgroups where idx="+grpIdx);
	String chr = dbc2.executeString("select fullname from xgroups where idx="+grpIdx);
	Globals.tprt(String.format("%d. %d %-6s Grp %d  maxGeneIdx %,8d  cntZero %,d", which, pidx, chr, grpIdx, maxGeneIdx, cnt));
}	
} 
	catch (Exception e) {ErrorReport.print(e, "Zero #pseudo");}
	}
}
