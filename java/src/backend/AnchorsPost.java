package backend;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

import util.ErrorReport;
import util.Logger;

public class AnchorsPost {
	private int mPairIdx;
	private Logger mLog;
	private UpdatePool pool;
	private Project mProj1, mProj2;
	private int count=0;
	
	public AnchorsPost(int pairIdx, Project proj1, Project proj2, UpdatePool xpool, Logger log) {
		mPairIdx = pairIdx;
		mProj1 = proj1;
		mProj2 = proj2;
		pool = xpool;
		mLog = log;
	}
	
	public void setGeneRunCounts() {
	try {
		/* CAS512 runsize and genenum added here; MOVED compute genenum to AnnotLoadMain */
		
		pool.executeUpdate("update pseudo_hits set runsize=0 where pair_idx=" + mPairIdx);
		
		int num2go = mProj1.getGroups().size() * mProj2.getGroups().size();
		mLog.msg("Process " + num2go + " group pairs for colinear runs");
		
		
		for (Group g1 : mProj1.getGroups()) {
			for (Group g2: mProj2.getGroups()) {
				
				//if (!buildColinear(g1, g2)) return;
				if (!buildColinearOrig(g1, g2)) return;
				
				num2go--;
				if (num2go>0) // CAS500 22nov19
					System.out.print("Left to process: " + num2go + "\r");
			}
		}
		Utils.prtNumMsg(mLog, count, " updates");
	}
	catch (Exception e) {ErrorReport.print(e, "Compute colinear genes"); }
	}
	
/*****************************************************************************************************
 * This is the original algorithm, with a few changes for readability
 * // get the hits with genes ordered on one side and just count the ordered runs on the other side
 */	
	private boolean buildColinearOrig(Group g1, Group g2) {
	try {
		//
		// Build sparse matrix of gene number hits; complex because of how stored in DB and
		// because a hit can hit more than one gene number.
		// We will be strict about connecting + strand hits in forward direction, - strand in reverse direction.
		// 
		ResultSet rs = pool.executeQuery("select max(genenum) as maxnum from pseudo_annot as pa where pa.grp_idx=" + g2.idx);
		rs.first();
		int maxN2 = rs.getInt("maxnum");
		
		// each hit is downloaded 1-2x depending on whether have annotation on both or one side 
		rs = pool.executeQuery("select ph.idx, pa.genenum, pa.grp_idx, ph.strand " +
				" from pseudo_hits       as ph " +
				" join pseudo_hits_annot as pha on ph.idx = pha.hit_idx" +
				" join pseudo_annot      as pa  on pa.idx = pha.annot_idx " +
				" where ph.grp1_idx=" + g1.idx + " and ph.grp2_idx=" + g2.idx +
				" order by pa.genenum asc, ph.idx asc");
		
		TreeMap<Integer,Hit> hit2gn = new TreeMap<Integer,Hit>();
		
		// Create hit2gn 
		while (rs.next()) {
			int hidx = rs.getInt(1);
			int gnum = rs.getInt(2);
			int grpIdx = rs.getInt(3);
			String str = rs.getString(4);
			boolean inv = (str.contains("+") && str.contains("-"));
			if (inv && grpIdx == g2.idx) {
				// reverse the gene numbers for inverted hits since we want to connect them backwards
				// use 2* so they won't overlap (although they will also go in separate sparse mats)
				gnum = (1 + 2*maxN2) - gnum;
			}
			Hit hObj;
			if (!hit2gn.containsKey(hidx)) {
				hObj = new Hit(inv);
				hit2gn.put(hidx, hObj);
			}
			else hObj = hit2gn.get(hidx);
			hObj.add(grpIdx == g1.idx, gnum);
		}
		
		TreeMap<Integer,TreeMap<Integer,Integer>> gn2hidxF = new TreeMap<Integer,TreeMap<Integer,Integer>>(); // gn1 <gn2, hit>
		TreeMap<Integer,TreeMap<Integer,Integer>> gn2hidxR = new TreeMap<Integer,TreeMap<Integer,Integer>>(); // gn1 <gn2, hit>
		TreeMap<Integer,TreeMap<Integer,Integer>> sparse; // gn1 <gn2, hit>
		
		// transfer hit2gn to gn2hitF/R
		for (int hidx : hit2gn.keySet()){
			Hit hObj = hit2gn.get(hidx);
			int n1 = hObj.getLen1();
			int n2 = hObj.getLen2();

			if (n1 == 0 || n2 == 0) continue; // skips if anno only on one side
			
			sparse = (hObj.inv) ? gn2hidxR : gn2hidxF;// gn2hidxR/F 
			
			for (int i = 0; i < Math.min(n1,n2); i++) {
				int gn1 = hObj.getGn1(i);
				int gn2 = hObj.getGn2(i);
				if (!sparse.containsKey(gn1)) {			
					sparse.put(gn1, new TreeMap<Integer,Integer>()); // Gene gObj = new Gene(); sparse.put(gn1, gObj)
					sparse.get(gn1).put(gn2, hidx);
				}
			}
			for (int i = Math.min(n1,n2); i < Math.max(n1, n2); i++) {
				int gn1 = (i < n1 ? hObj.getGn1(i) : hObj.getGn1(n1-1) );
				int gn2 = (i < n2 ? hObj.getGn2(i) : hObj.getGn2(n2-1) );
				if (!sparse.containsKey(gn1)){
					sparse.put(gn1, new TreeMap<Integer,Integer>()); // Gene gObj = new Gene(); sparse.put(gn1, gObj)
					sparse.get(gn1).put(gn2, hidx);					 // gObj.gn2 = gn2; gObj.hit = hidx;
				}
			}
			hObj.clearVec();
		}
		// done with hidx2gn 
		
		
		// CAS517 use clear instead of recreating over and over
		// Connect the dots in the sparse matrices.
		// Both use increasing gene order since we already reversed the gene order if necessary.
		TreeMap<Integer,Integer> hitScores = new TreeMap<Integer,Integer>(); // hitId, score 
		
		TreeMap<Integer,TreeMap<Integer,Integer>> gnScore = new TreeMap<Integer,TreeMap<Integer,Integer>>(); // gn1 <gn2, score>
		TreeMap<Integer,TreeSet<Integer>> gnMax = new TreeMap<Integer,TreeSet<Integer>>(); // gn1 <gn2>
		TreeMap<Integer,Integer> hitBackLink = new TreeMap<Integer,Integer>(); // backLink: hitid, hitid
		HashSet<Integer> hitAlreadyLink = new HashSet<Integer>();              // alreadyLink: hitid
		
		int gapSize = 1;
		for (int k=0; k<2; k++){
			sparse = (k==0) ? gn2hidxF : gn2hidxR;
			gnScore.clear();
			gnMax.clear();
			hitBackLink.clear();
			hitAlreadyLink.clear();
			
			// Traverse sparse matrix (gn1,gn2) in increasing order of gn1,gn2 (note keySet from tree is sorted)
			// Build backlinks and scores, and track the sparse nodes that are currently heads of maximal chains
			for (int gn1 : sparse.keySet()) {
				gnScore.put(gn1, new TreeMap<Integer,Integer>());
				for (int gn2 : sparse.get(gn1).keySet()) {
					gnScore.get(gn1).put(gn2, 1);
					int gn1_min = gn1 - gapSize;
					int gn2_min = gn2 - gapSize;
					int gn1_connect = -1;
					int gn2_connect = -1;
					int newScore = -1;
					
					for (int gn1_try = gn1-1; gn1_try >= gn1_min; gn1_try--) {
						if (!gnScore.containsKey(gn1_try)) continue;
						
						for (int gn2_try = gn2-1; gn2_try >= gn2_min; gn2_try--) {
							if (!gnScore.get(gn1_try).containsKey(gn2_try)) continue;
							
							if (gnScore.get(gn1_try).get(gn2_try)+1 > newScore) {
								newScore = gnScore.get(gn1_try).get(gn2_try)+1;
								gn1_connect = gn1_try;
								gn2_connect = gn2_try;
							}
						}								
					}
					if (newScore > 0) {// there was a backlink
						assert(newScore >= 2);
						gnScore.get(gn1).put(gn2,newScore);
						// If the backlink was to a prior maximal chain, then it's no longer maximal
						if (gnMax.containsKey(gn1_connect)) {
							if (gnMax.get(gn1_connect).contains(gn2_connect)) {
								gnMax.get(gn1_connect).remove(gn2_connect);
							}
						}
						if (!gnMax.containsKey(gn1)) {
							gnMax.put(gn1, new TreeSet<Integer>());
						}
						gnMax.get(gn1).add(gn2); // it is currently maximal just because of the traverse order
						int hidx1 = sparse.get(gn1).get(gn2);
						int hidx2 = sparse.get(gn1_connect).get(gn2_connect);
						// We have to be careful because a hit can span adjacent genes on both genomes. 
						// Also, two or more hits can span the same adjacent genes on both genomes.
						// We have to avoid creating a self-link or link cycle.
						if (hidx1 != hidx2)  {										// Don't create a backlink FROM hit1 if 
							if (!hitAlreadyLink.contains(hidx1) ) { // something already links TO hit1		
								hitBackLink.put(hidx1,hidx2);
								hitAlreadyLink.add(hidx2);
							}
						}
					}
				}
			}
			// Takes some effort to get a highest-to-lowest ordered list of scores with their corresponding hits
			TreeMap<Integer,Integer> hit2max = new TreeMap<Integer,Integer>(); 
			for (int gn1 : gnMax.keySet()) {
				for (int gn2 : gnMax.get(gn1)){
					int score = gnScore.get(gn1).get(gn2);
					int hidx = sparse.get(gn1).get(gn2);
					if (!hit2max.containsKey(hidx) || score > hit2max.get(hidx)) { // 2nd should probably never happen
						hit2max.put(hidx, score);
					}
				}
			}
			TreeMap<Integer, TreeSet<Integer>> scores2Hits = new TreeMap<Integer, TreeSet<Integer>>();
			for (int hidx : hit2max.keySet()) {
				int score = hit2max.get(hidx);
				if (!scores2Hits.containsKey(score)) {
					scores2Hits.put(score, new TreeSet<Integer>());
				}
				scores2Hits.get(score).add(hidx);
			}

			for (int score : scores2Hits.descendingKeySet()) {
				for (int hidx : scores2Hits.get(score)) {
					if (hitScores.containsKey(hidx)) continue;
					
					hitScores.put(hidx,score);
					while (hitBackLink.containsKey(hidx)) {
						int hidx1 = hitBackLink.get(hidx);
						hidx = hidx1;
						if (!hitScores.containsKey(hidx)) {
							hitScores.put(hidx, score);
						}
					}							
				}
			}
		} // end of forward/reverse loo[
		PreparedStatement ps = pool.prepareStatement("update pseudo_hits set runsize=? where idx=?");
		for (int hidx : hitScores.keySet()) {
			int score = hitScores.get(hidx);
			ps.setInt(1, score);
			ps.setInt(2,hidx);
			ps.addBatch();
			count++;
		}
		ps.executeBatch();
		return true;
	}
	catch (Exception e) {ErrorReport.print(e, "Build collinear"); return false; }
	}
	
	/*****************************************************************************
	 * In progress... simplifying algorithm and adding runNum
	*****************************************************************************/
	private boolean buildColinear(Group g1, Group g2) {
	try {
		pool.executeUpdate("update pseudo_hits set runsize=0 where pair_idx=" + mPairIdx);
		// when this column gets added, update AnchorMain.addMirrorHits
		pool.tableCheckAddColumn("pseudo_hits", "runnum", "INTEGER default 0", "runsize");
		
		ResultSet rs = pool.executeQuery("select max(genenum) as maxnum from pseudo_annot as pa where pa.grp_idx=" + g2.idx);
		rs.first();
		int maxN2 = rs.getInt("maxnum");
		
		TreeMap<Integer,Gene> gn1hit = new TreeMap<Integer,Gene>();
		TreeMap<Integer,Gene> gn2hit = new TreeMap<Integer,Gene>();
		
		rs = pool.executeQuery("select genenum, strand from pseudo_annot where genenum>0 and grp_idx=" + g1.idx);
		while (rs.next()) {
			int gnum = rs.getInt(1);
			boolean inv = rs.getString(2).contentEquals("-");
			gn1hit.put(gnum, new Gene(inv, gnum));
		}
		System.err.println(g1.idx + " Grp1 " + gn1hit.size());
		
		rs = pool.executeQuery("select genenum, strand from pseudo_annot where genenum>0 and grp_idx=" + g2.idx);
		while (rs.next()) {
			int gnum = rs.getInt(1);
			boolean inv = rs.getString(2).contentEquals("-");
			gn2hit.put(gnum, new Gene(inv, gnum));
		}
		System.err.println(g2.idx + " Grp2 " + gn2hit.size());
		
		// each hit is downloaded 1-2x depending on whether have annotation on one or both sides 
		rs = pool.executeQuery("select ph.idx, pa.genenum, pa.grp_idx, ph.strand, ph.gene_overlap " +
						" from pseudo_hits       as ph " +
						" join pseudo_hits_annot as pha on ph.idx = pha.hit_idx" +
						" join pseudo_annot      as pa  on pa.idx = pha.annot_idx " +
						" where ph.grp1_idx=" + g1.idx + " and ph.grp2_idx=" + g2.idx +
						" order by pa.genenum asc, ph.idx asc");
				
		TreeMap<Integer,Hit> hit2gn = new TreeMap<Integer,Hit>();
		
		// Create hit2gn 
		while (rs.next()) {
			int hidx = rs.getInt(1);
			int gnum = rs.getInt(2);
			
			int grpIdx = rs.getInt(3);
			String str = rs.getString(4);
			int gene_overlap = rs.getInt(5);
			
			boolean inv = (str.contains("+") && str.contains("-"));
			boolean isChr1 = grpIdx == g1.idx;
			boolean isChr2 = grpIdx == g2.idx;
			
			int inum = gnum;
			if (inv && isChr2) inum = (1 + 2*maxN2) - gnum; // causes gnum to be in reverse order
			
			Hit hObj;
			if (!hit2gn.containsKey(hidx)) {
				hObj = new Hit(inv, hidx, gene_overlap);
				hit2gn.put(hidx, hObj);
			}
			else hObj = hit2gn.get(hidx);
			
			Gene gObj;
			if (isChr1) {
				if (!gn1hit.containsKey(gnum)) die("1 no " + inum + " " + gnum);
				gObj = gn1hit.get(inum);
			}
			else {
				if (!gn2hit.containsKey(gnum)) die("2 no " + gnum + " " + inum);
				gObj = gn2hit.get(gnum);
			}
			gObj.addHit(hObj); 		// add hit to gene
			hObj.add(isChr1,gnum); 	// add genenum to hit
		}
		int cnt0=0, cnt2=0;
		System.err.println(">Gene1");
		for (int gnum : gn1hit.keySet()) {
			Gene gObj = gn1hit.get(gnum);
			if (gObj.hits.size()==0) {
				cnt0++;
				if (cnt0<4) {
					String line = " Gene: " + gObj.gnum;
					System.err.println(line);
				}
			}
			else if (gObj.hits.size()>1) {
				cnt2++;
				if (cnt2<4) {
					String line = " Gene: " + gObj.gnum;
					for (Hit hObj : gObj.hits)  line += String.format("  %,6d ", hObj.idx);
					System.err.println(line);
				}
			}
		}
		System.err.println("Zero hits: " + cnt0 + " More than one: " + cnt2);
		cnt0=cnt2=0;
		System.err.println(">Gene2");
		for (int gnum : gn2hit.keySet()) {
			Gene gObj = gn2hit.get(gnum);
			if (gObj.hits.size()==0) {
				cnt0++;
				if (cnt0<4) {
					String line = " Gene: " + gObj.gnum;
					System.err.println(line);
				}
			}
			else if (gObj.hits.size()>1) {
				cnt2++;
				if (cnt2<4) {
					String line = " Gene: " + gObj.gnum;
					for (Hit hObj : gObj.hits)  line += String.format("  %,6d ", hObj.idx);
					System.err.println(line);
				}
			}
		}
		System.err.println("Zero hits: " + cnt0 + " More than one: " + cnt2);
		cnt0=cnt2=0;
		System.err.println(">Hits");
		for (int idx : hit2gn.keySet()) {
			Hit hObj = hit2gn.get(idx);
			if (hObj.gn1.size()>1) {
				cnt2++; 
				if (cnt2<4) {
					String line = String.format("1. Hit: %,6d (g%d)", idx, hObj.gn1.size());
					for (int gnum : hObj.gn1)  line += String.format("%,6d ", gnum);
					System.err.println(line);
				}
			}
			if (hObj.gn2.size()>1) {
				cnt2++; 
				if (cnt2<4) {
					String line = String.format("1. Hit: %,6d (g%d)", idx, hObj.gn2.size());
					for (int gnum : hObj.gn2)  line += String.format("%,6d ", gnum);
					System.err.println(line);
				}
			}
		}
		System.err.println("Zero hits: " + cnt0 + " More than one: " + cnt2);
		return true;
	}
	catch (Exception e) {ErrorReport.print(e, "compute colinear runs"); return false;}
	}
	private void die (String msg) {
		System.err.println(msg);
		System.exit(0);;
	}
	/******************************************************************/
	private class Hit {
		// Step 1: read input; clear after transferring to gene array
		public Hit(boolean inv) {this.inv = inv;}
		
		public Hit(boolean inv, int idx, int gene_overlap) {
			this.inv = inv;
			this.idx = idx; 
			this.gene_overlap = gene_overlap;
		}
		
		public void add(boolean is1, int gn_idx) {
			if (is1) gn1.add(gn_idx);
			else 	 gn2.add(gn_idx);
		}
		public int getLen1() { return gn1.size();}
		public int getLen2() { return gn2.size();}
		
		public int getGn1(int i) { return gn1.get(i);}
		public int getGn2(int i) { return gn2.get(i);}
		
		public void clearVec() {
			gn1.clear();
			gn2.clear();
		}
		boolean inv=false;
		Vector <Integer> gn1 = new Vector <Integer> (); // genes with this hit overlapping
		Vector <Integer> gn2 = new Vector <Integer> ();
		int gene_overlap, idx;
	}
	
	private class Gene {
		public Gene(boolean inv, int gnum) {
			this.inv = inv;
			this.gnum = gnum; // original num
		}
		public void addHit(Hit hObj) {
			hits.add(hObj);
		}
		boolean inv=false;
		int gnum;
		Vector <Hit> hits = new Vector <Hit> (); // typical one hit
	}
}

