package symap.pool;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.List;
import java.util.LinkedList;

import util.ListCache;
import util.DatabaseReader;

public class HoverMessagePool extends DatabaseUser {	
	private static char BLOCK_SEP = '.';

	private static final String BES_BLOCK_QUERY =
		"SELECT DISTINCT g1.name,g2.name,ch.block_num "+
		//"FROM "+BES_HITS_TABLE+" AS h,"+BES_BLOCK_HITS_TABLE+" AS bb," // mdb removed 3/27/07 #110
		//	   +CTG_HITS_TABLE+" AS ch,"+CONTIGS_TABLE+" AS c,"+GROUPS_TABLE+" AS g2 "+ // mdb removed 3/27/07 #110
		"FROM bes_hits AS h JOIN bes_block_hits AS bb JOIN " // mdb added 3/27/07 #110
			   +"ctghits AS ch JOIN contigs AS c JOIN groups AS g2 "+ // mdb added 3/27/07 #110
		"LEFT JOIN groups AS g1 ON (c.grp_idx=g1.idx) "+
		"WHERE h.proj1_idx=? AND h.proj2_idx=? AND h.clone=? AND h.bes_type=? AND h.idx=bb.hit_idx AND bb.ctghit_idx=ch.idx AND "+
		"      ch.ctg1_idx=c.idx AND h.grp2_idx=g2.idx ";

	private static final String BES_TOT_HITS_QUERY = 
		"SELECT COUNT(*) FROM bes_hits WHERE proj1_idx=? AND proj2_idx=? AND clone=? AND bes_type=?";

	private static final String MRK_PSEUDO_BLOCKS_QUERY = 
		"SELECT DISTINCT g1.name,g2.name,ch.block_num "+
		//"FROM "+MRK_HITS_TABLE+" AS h,"+MRK_BLOCK_HITS_TABLE+" AS mb," // mdb removed 3/27/07 #110
		//	   +CTG_HITS_TABLE+" as ch,"+CONTIGS_TABLE+" AS c,"+GROUPS_TABLE+" AS g2 "+ // mdb removed 3/27/07 #110
		"FROM mrk_hits AS h JOIN mrk_block_hits AS mb JOIN " // mdb added 3/27/07 #110
			   +"ctghits as ch JOIN contigs AS c JOIN groups AS g2 "+ // mdb added 3/27/07 #110
		"LEFT JOIN groups AS g1 ON (c.grp_idx=g1.idx) "+
		"WHERE h.proj1_idx=? AND h.marker=? AND h.idx=mb.hit_idx AND mb.ctghit_idx=ch.idx AND ch.ctg1_idx=c.idx AND "+
		"      c.proj_idx=? AND ch.grp2_idx=g2.idx AND g2.proj_idx=?";

	private static final String MRK_FPC_BLOCKS_QUERY = 
		"SELECT DISTINCT g1.name,g2.name,ch.block_num "+
		//"FROM "+MARKERS_TABLE+" AS m,"+SHARED_MRK_BLOCK_TABLE+" AS mb," // mdb removed 3/27/07 #110
		//	   +CTG_HITS_TABLE+" AS ch,"+CONTIGS_TABLE+" AS c1,"+CONTIGS_TABLE+" AS c2 "+ // mdb removed 3/27/07 #110
		"FROM markers AS m JOIN shared_mrk_block_hits AS mb JOIN " // mdb added 3/27/07 #110
			   +"ctghits AS ch JOIN contigs AS c1 JOIN contigs AS c2 "+ // mdb added 3/27/07 #110
		"LEFT JOIN groups AS g1 ON (c1.grp_idx=g1.idx) "+
		"LEFT JOIN groups AS g2 ON (c2.grp_idx=g2.idx) "+
		"WHERE m.proj_idx=? AND m.name=? AND m.idx=mb.mrk_idx AND mb.ctghit_idx=ch.idx AND ch.ctg1_idx=c1.idx AND "+
		"      c1.proj_idx=? AND ch.ctg2_idx=c2.idx AND c2.proj_idx=?";	

	private static final String MRK_TOT_CONTIGS_QUERY = 
		"SELECT COUNT(*) "+
		"FROM markers AS m,mrk_ctg AS mc "+
		"WHERE m.proj_idx=? AND m.name=? AND m.idx=mc.mrk_idx";	

	private static final String MRK_PSEUDO_HITS_QUERY =
		"SELECT COUNT(*) "+
		"FROM mrk_hits as h,groups as g "+
		"WHERE h.proj1_idx=? AND h.marker=? AND g.proj_idx=? AND h.grp2_idx=g.idx";

	// project,marker,project,marker,otherProject
	private static final String MRK_PSEUDO_QUERIES = 
		"SELECT ("+MRK_TOT_CONTIGS_QUERY+") as contigs, ("+MRK_PSEUDO_HITS_QUERY+") as hits";

	private ListCache mrkCache;
	private ListCache besCache;
	private ProjectProperties projectProperties;

	public HoverMessagePool(DatabaseReader dr, ProjectProperties pp, ListCache mrkMessageCache, ListCache besMessageCache) {
		super(dr);
		this.projectProperties = pp;
		this.mrkCache = mrkMessageCache;
		this.besCache = besMessageCache;
	}

	public void clear() {
		close();
		if (mrkCache != null) mrkCache.clear();
		if (besCache != null) besCache.clear();
	}

	public String getMarkerHoverMessage(int project, int otherProject, String marker) throws SQLException {
		MarkerMessageData d = null;
		if (mrkCache != null)
			d = (MarkerMessageData)mrkCache.get(new MarkerMessageData(project,otherProject,marker));
		if (d == null) {
			d = new MarkerMessageData(project,otherProject,marker);

			Statement stat = null;
			ResultSet rs = null;
			try {
				List<String> blocks = new LinkedList<String>();
				int p1 = project, p2 = otherProject;
				boolean isFPC = !projectProperties.isFPCPseudo(project,otherProject);
				if (isFPC) {
					ProjectPair projPair = projectProperties.getProjectPair(p1,p2);
					if (projPair.getP1() != p1) {
						int t = p1;
						p1 = p2;
						p2 = t;
					}
				}

				stat = createStatement();

				String query = isFPC ? MRK_FPC_BLOCKS_QUERY : MRK_PSEUDO_BLOCKS_QUERY;
				query = setInt(setInt(setString(setInt(query,p1),marker),p1),p2);
				rs = stat.executeQuery(query);
				while (rs.next())
					blocks.add(getBlock(rs.getString(1),rs.getString(2),rs.getInt(3)));
				closeResultSet(rs);
				d.setBlocks(blocks);

				query = isFPC ? MRK_TOT_CONTIGS_QUERY : MRK_PSEUDO_QUERIES;
				query = setString(setInt(query,project),marker);
				if (!isFPC) query = setInt(setString(setInt(query,project),marker),otherProject);
				rs = stat.executeQuery(query);
				rs.next();
				d.setContigs(rs.getInt(1));
				d.setHits(isFPC ? 0 : rs.getInt(2));

				closeResultSet(rs);
				rs = null;
				closeStatement(stat);
				stat = null;

				if (mrkCache != null) mrkCache.add(d);

				//if (SyMAP.DEBUG) System.out.println("Found "+d+" in db.");
			}
			catch (SQLException e) {
				closeResultSet(rs);
				closeStatement(stat);
				rs = null;
				stat = null;
				close();
				throw e;
			}
		}
		//else if (SyMAP.DEBUG) System.out.println("Found "+d+" in cache.");
		return d == null ? null : d.getMessage();
	}

	public String getBESHoverMessage(int project, int otherProject, String clone, byte bes) throws SQLException {
		BESMessageData d = null;
		if (besCache != null)
			d = (BESMessageData)besCache.get(new BESMessageData(project,otherProject,clone,bes));
		if (d == null) {
			List<String> blocks = new LinkedList<String>();
			Statement stat = null;
			ResultSet rs = null;
			try {
				stat = createStatement(); 
				rs = stat.executeQuery(setString(setString(setInt(setInt(BES_BLOCK_QUERY,project),otherProject),clone),
						getBESValueStr(bes)));
				while (rs.next())
					blocks.add(getBlock(rs.getString(1),rs.getString(2),rs.getInt(3)));
				closeResultSet(rs);
				rs = null;

				rs = stat.executeQuery(setString(setString(setInt(setInt(BES_TOT_HITS_QUERY,project),otherProject),clone),
						getBESValueStr(bes)));
				rs.next();
				d = new BESMessageData(project,otherProject,clone,bes,blocks,rs.getInt(1));

				if (besCache != null) besCache.add(d);

				//if (SyMAP.DEBUG) System.out.println("Found "+d+" in db.");
			}
			catch (SQLException e) {
				closeResultSet(rs);
				closeStatement(stat);
				rs = null;
				stat = null;
				close();
				throw e;
			}
			finally {
				blocks.clear();
				closeResultSet(rs);
				closeStatement(stat);
			}
		}
		//else if (SyMAP.DEBUG) System.out.println("Found "+d+" in cache.");
		return d == null ? null : d.getMessage();
	}

	private static String getBlock(String g1, String g2, int blocknum) {
		if (g1 == null) g1 = "0";
		if (g2 == null) g2 = "0";
		return new StringBuffer(g1).append(BLOCK_SEP).
		append(g2).append(BLOCK_SEP).
		append(blocknum).toString();//.intern(); // mdb removed intern() 2/2/10 - intern is unnecessary here
	}

	private static class MarkerMessageData {
		private static final String MRK_STR  = "Marker ";
		private static final String HITS_STR = " hits ";
		private static final String CTGS_STR  = " contigs, ";
		private static final String CTG_STR  = " contig, ";
		private static final String BLKS_STR  = " blocks, and has ";
		private static final String BLK_STR  = " block, and has ";
		private static final String POSTS_STR = " total hits.";
		private static final String POST_STR = " total hit.";

		private static final String BLKS2_STR  = " blocks.";
		private static final String BLK2_STR  = " block.";

		private static final String INS_STR = " Blocks: ";
		private static final String IN_STR = " Block: ";
		private static final char COMMA = ',';

		private int p, op;
		private String name;
		private int hits, contigs;
		private String[] blocks;

		private MarkerMessageData(int p, int op, String name) {
			this.p = p;
			this.op = op;
			this.name = name;//.intern(); // mdb removed intern() 2/2/10 - can cause memory leaks in this case
			hits = contigs = 0;
		}

// mdb removed 6/29/07 #118
//		private MarkerMessageData(int p, int op, String name, int hits, int contigs, List blocks) {
//			this(p,op,name);
//			this.hits = hits;
//			this.contigs = contigs;
//			this.blocks = (String[])blocks.toArray(new String[blocks.size()]);
//		}

		private void setBlocks(List<String> blocks) {
			this.blocks = (String[])blocks.toArray(new String[blocks.size()]);
		}

		private void setHits(int h) {
			this.hits = h;
		}

		private void setContigs(int c) {
			this.contigs = c;
		}

		private String getMessage() {
			StringBuffer mes = new StringBuffer();

			int numBlocks = blocks == null ? 0 : blocks.length;

			if (hits > 0) 
				mes.append(MRK_STR).append(name).append(HITS_STR).
				append(contigs).append(contigs == 1 ? CTG_STR : CTGS_STR).
				append(numBlocks).append(numBlocks == 1 ? BLK_STR : BLKS_STR).
				append(hits).append(hits == 1 ? POST_STR : POSTS_STR);
			else
				mes.append(MRK_STR).append(name).append(HITS_STR).
				append(contigs).append(contigs == 1 ? CTG_STR : CTGS_STR).
				append(numBlocks).append(numBlocks == 1 ? BLK2_STR : BLKS2_STR);

			if (blocks != null && blocks.length > 0) {
				mes.append(blocks.length > 1 ? INS_STR : IN_STR);
				mes.append(blocks[0]);
				for (int i = 1; i < blocks.length; i++)
					mes.append(COMMA).append(blocks[i]);
			}

			return mes.toString();
		}

		public String toString() {
			return "[MarkerMessageData ("+p+","+op+") "+name+" Message: "+getMessage()+"]";
		}

		public boolean equals(Object obj) {
			if (obj instanceof MarkerMessageData) {
				MarkerMessageData d = (MarkerMessageData)obj;
				return p == d.p && op == d.op && name == d.name;
			}
			return false;
		}
	}

	private static class BESMessageData {
		private static final String CLONE_STR = "BES ";
		private static final String HAS_STR = " has ";
		private static final String HITS_STR = " total hits";
		private static final String HIT_STR = " total hit";
		private static final String IN_STR = " with Blk Hits on ";
		private static final char COMMA = ',';

		int p, op;
		String name;
		byte bes;
		String blocks[];
		int totHits;

		private BESMessageData(int p, int op, String name, byte bes) {
			this.p = p;
			this.op = op;
			this.name = name;//.intern(); // mdb removed intern() 2/2/10 - can cause memory leaks in this case
			this.bes = bes;
		}

		private BESMessageData(int p, int op, String name, byte bes, List<String> blocks, int totHits) {
			this(p,op,name,bes);
			if (blocks == null || blocks.isEmpty()) this.blocks = null;
			else this.blocks = (String[])blocks.toArray(new String[blocks.size()]);
			this.totHits = totHits;
		}

		private String getMessage() {
			StringBuffer mes = new StringBuffer(CLONE_STR).append(name).append(getBESValueStr(bes));
			mes.append(HAS_STR).append(totHits).append(totHits == 1 ? HIT_STR : HITS_STR);
			if (blocks != null && blocks.length > 0) {
				mes.append(IN_STR);
				mes.append(blocks[0]);
				for (int i = 1; i < blocks.length; i++)
					mes.append(COMMA).append(blocks[i]);
			}
			return mes.toString();
		}

		public String toString() {
			return "[BESMessageData ("+p+","+op+") "+name+getBESValueStr(bes)+" Message: "+getMessage()+"]";
		}

		public boolean equals(Object obj) {
			if (obj instanceof BESMessageData) {
				BESMessageData d = (BESMessageData)obj;
				return p == d.p && op == d.op && name == d.name && bes == d.bes;
			}
			return false;
		}
	}
}
