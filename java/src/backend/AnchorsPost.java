package backend;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.Vector;

import database.DBconn2;
import symap.manager.Mproject;
import util.Cancelled;
import util.ErrorReport;
import util.ProgressDialog;
import util.Utilities;

/**************************************************
 * Computes the collinear sets (pseudo_hits.runsize, runnum); the term 'run' is used here for collinear (run/set)
 * NOTES: 
 * - The hits are ordered along the alphabetically lesser project name, say project A. Hence the 
 *   hits are analyzed along the project A length.
 * - The annot_idx are sequential for sequential genes, but have gaps for exons. The temporary tnum is sequential.
 * - The hits for collinear may not be sequential when a gene has two hits.
 * - Only hits to two genes are downloaded from DB, hence, if a gene has a g1 and g2 hit, the g1 hit is ignored.
 * 
 * CAS520 added setAnnot, setHitNum and rewrote collinearSets; CAS541 moved setHitNum to AnchorsMain
 * CAS556 fixed bug which only happened when one gene in projectA was in two different collinear sets; this
 *  only happens when the chromosome has duplications within itself. I changed the whole logic to number
 *  using the hitVec instead of gene1Map. This fixed the bug and added some sets.
 */

public class AnchorsPost {
	// parameters
	private int mPairIdx; 				// project pair 
	private Mproject mProj1, mProj2;	// two projects
	private ProgressDialog mLog;
	private DBconn2 dbc2;
	
	// initial; loaded from db	
	private Vector <Hit> fHitVec = new Vector <Hit> ();
	private Vector <Hit> rHitVec = new Vector <Hit> ();
	private TreeMap <Integer, Gene> geneMap1 = new TreeMap <Integer, Gene> (); 		 // tnum, gene
	private TreeMap <Integer, Gene> geneMap2 = new TreeMap <Integer, Gene> (); 		 // tnum, gene
	private HashMap <Integer, Integer> gidxTnum = new HashMap <Integer, Integer> (); // idx, tnum 
	
	private int totalRuns=0;
	private int runnum;	// step2AssignRunNum
	
	private int [] cntSizeSet = {0,0,0,0,0};// -dd
	private String chrs;					// ErrorReport
	
	/** Called from SyntenyMain **/
	public AnchorsPost(int pairIdx, Mproject proj1, Mproject proj2, DBconn2 dbc2, ProgressDialog log) {
		this.mPairIdx = pairIdx;
		this.mProj1 = proj1;
		this.mProj2 = proj2;
		this.dbc2 = dbc2;
		this.mLog = log;
	}
	
	/*********************************************************
	 * Called from SyntenyMain right after creating object
	 */
	public void collinearSets() {
		try {
			dbc2.executeUpdate("update pseudo_hits set runsize=0, runnum=0 where pair_idx=" + mPairIdx);
			
			int num2go = mProj1.getGrpSize() * mProj2.getGrpSize();
			mLog.msg("Finding Collinear sets");
			long time = Utils.getTime();
			
			TreeMap <Integer, String> map1 = mProj1.getGrpIdxMap();
			TreeMap <Integer, String> map2 = mProj2.getGrpIdxMap();
			
			for (int grpIdx1 : map1.keySet()) {
				for (int grpIdx2 : map2.keySet()) {
					
					chrs = map1.get(grpIdx1) + ":" + map2.get(grpIdx2);
					String t = Utilities.getDurationString(Utils.getTime()-time);
					symap.Globals.rprt(num2go + " pairs remaining (" + t + ")"); 
					num2go--;
					
					if (grpIdx1==grpIdx2) continue; 		// CAS521 can crash on self-chr
					
					if (!step0BuildSets(grpIdx1, grpIdx2)) return;
					
					totalRuns+= (runnum-1);
				}
				if (Cancelled.isCancelled()) return; 		// CAS535 there was no checks
			}
			// CAS540 computed counts are not right; just get from db; CAS556 distinct not work across chrs
			// int nsets = dbc2.executeInteger("SELECT count(DISTINCT(runnum)) FROM pseudo_hits WHERE runnum>0 and pair_idx=" + mPairIdx);
			int nhits = dbc2.executeInteger("SELECT count(*) FROM pseudo_hits WHERE runnum>0 and pair_idx=" + mPairIdx);
			Utils.prtNumMsg(mLog, totalRuns, "Collinear sets                          ");
			Utils.prtNumMsg(mLog, nhits, "Updates                          ");
			Utils.timeDoneMsg(mLog, "Collinear", time);
		}
		catch (Exception e) {ErrorReport.print(e, "Compute colinear genes"); }
	}
		
	/*****************************************************************************************************
	 *    COLLINEAR SETS between two chromosomes
	 */	
	private boolean step0BuildSets(int grpIdx1, int grpIdx2) {
	try {
		runnum=1;
		if (!step1LoadFromDB(grpIdx1, grpIdx2)) return false;  // fHitVec, rHitVec, geneMap1, geneMap2, gidxTnum
		
		if (!step2AssignRunNum(false, fHitVec)) return false;
		if (!step3SaveToDB(fHitVec)) return false;
		
		if (!step2AssignRunNum(true,  rHitVec)) return false;
		if (!step3SaveToDB(rHitVec)) return false;
		
		geneMap1.clear(); geneMap2.clear(); gidxTnum.clear();
		fHitVec.clear(); rHitVec.clear(); 

		return true;
	}
	catch (Exception e) {ErrorReport.print(e, "Build collinear " + chrs); return false; }
	}
	
	/*************************************************************
	 * Create hit and gene maps
	 *   Create hitMap from gene_overlap>1: the pseudo_hits_annot table can hold multiple occurrences of a hit.
	 *   It can hit on one or both side, and multiple genes on one side (overlapping, contained, or close)
	 *   Use the two from the pseudo_hits table  
	 */
	private boolean step1LoadFromDB(int grpIdx1, int grpIdx2) {
		try {
			Gene gObj;
			Hit hObj;
			ResultSet rs;
			
		// Hits with 2 genes - annot1 and annot2 have the best overlap; ignore others
			rs = dbc2.executeQuery("select ph.idx, ph.strand, ph.hitnum, ph.annot1_idx, ph.annot2_idx, B.blocknum " +
				" from pseudo_hits as ph  " +
				" LEFT JOIN pseudo_block_hits AS PBH ON PBH.hit_idx=ph.idx" +
				" LEFT JOIN blocks AS B ON B.idx=PBH.block_idx " +
				" where ph.grp1_idx=" + grpIdx1 + " and ph.grp2_idx=" + grpIdx2 + 
				" and ph.gene_overlap>1 order by hitnum"); // algo1 does not enter them ordered 
			while (rs.next()) {
				int i=1;
				int hidx = 		rs.getInt(i++);
				String str = 	rs.getString(i++);
				int hitnum =	rs.getInt(i++);
				int gidx1= 		rs.getInt(i++);
				int gidx2 = 	rs.getInt(i++);
				int blocknum =  rs.getInt(i++);
				
				boolean inv = (str.contains("+") && str.contains("-"));
				
				hObj = new Hit(hidx, hitnum, gidx1, gidx2, blocknum);
				if (inv) rHitVec.add(hObj);
				else  	 fHitVec.add(hObj);
			}
			if (rHitVec.size()==0 && fHitVec.size()==0) return true; // not an error
			
		// All genes 
			String ssql = "select idx, tag, start, end, genenum, strand, (end-start) as len from pseudo_annot "
								+ "	where type='gene' and grp_idx="; 
			String osql = " order by start ASC, len DESC";
			                        
			int tnum1=1;
			rs = dbc2.executeQuery(ssql + grpIdx1 + osql);
			while (rs.next()) {
				int idx = rs.getInt(1);		// tag          start         end            genenum        strand
				gObj = new Gene(idx, tnum1, rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getString(6));
				geneMap1.put(tnum1, gObj);  // using tnum keeps them ordered by start
				gidxTnum.put(idx, tnum1);
				tnum1++;
			}
			
			int tnum2=1;	// uses same gidxTnum but starts over numbering (idx are unique) 
			rs = dbc2.executeQuery(ssql + grpIdx2 + osql);
			while (rs.next()) {
				int idx = rs.getInt(1);
				gObj = new Gene(idx, tnum2, rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getString(6));
				geneMap2.put(tnum2, gObj);	
				gidxTnum.put(idx, tnum2);
				tnum2++;
			}
			rs.close();
			
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, "Load from DB " + chrs); return false; }
	}
	
	/*************************************************************
	 * CAS556 was looping through gene1Map, now looping through the hitVec
	 * Using genenums so do not have to worry about gene overlaps; hits are to best
	 * Example of reverse:
	 * 		Hit  Gene1  Gene2
	 * Row1 767  1930	2040		r1G2-r2G2
	 * Row2 768  1931   2039        if bInv r2G1-r2G2 else r1G2-r2G2
	 * Row3 769  1932   2038
	 * 1. backRow1 allows for co-mingled coSets.
	 * 2. could possibly miss a set at very end, but if the 1st loop row2<nHits isn't there, adds bad row to current coSet
	 */
	private boolean step2AssignRunNum(boolean bInv, Vector <Hit> xHitVec) {
	try {	
		HashSet <Integer> coHitSet = new HashSet <Integer> ();
		int row1=0, row2=0, nHits = xHitVec.size();
		int r1G1=0, r1G2=0, r2G1=0, r2G2=0;
		int savRow1, backRow1=0, d1=0, d2=0;
		Hit hObj1=null, hObj2=null;
		boolean bNewCo=true;
		int cnt=0, max = xHitVec.size()*100;
		
		dprt(">>>>>> " + chrs + " Hits " + nHits + " bInv=" + bInv);
		while (row1 < nHits-1 && row2 < nHits) {
			hObj1 = xHitVec.get(row1);
			if (hObj1.frunnum>0) {
				row1++; 
				continue;
			}
			savRow1 = row1;
			
			if (bNewCo) {
				coHitSet.add(row1); // start new one
				bNewCo = false;
			}
			
			r1G1 = gidxTnum.get(hObj1.gidx1);	
			r1G2 = gidxTnum.get(hObj1.gidx2);	
			
			row2 = row1+1;
			while (row2 < nHits) { // either breaks or row2++ 
				hObj2 = xHitVec.get(row2);
				if (hObj2.frunnum>0) {
					row2++;
					continue;	
				}		
				r2G1 = gidxTnum.get(hObj2.gidx1); 
				r2G2 = gidxTnum.get(hObj2.gidx2); 
				
				d1 =           r2G1 - r1G1;
				d2 = (!bInv) ? r2G2 - r1G2 : r1G2 - r2G2;
				
				if (d1==1 && d2==1) { 
					if (!coHitSet.contains(row2)) coHitSet.add(row2);
					else dprt("***found already: " + row2);
					
					if (backRow1==0) backRow1=row1+1; // at beginning of this coSet, may skip entries, so start 
					row1 = row2;					  
					
					break;
				}
				else if (d1>1 && (d2>1 || d2<1)) { 
					if (coHitSet.size()>1) { 			// finish current set
						for (int i: coHitSet) {
							xHitVec.get(i).frunnum = runnum;
							xHitVec.get(i).frunsize = coHitSet.size();
						}
						runnum++; 	
					}
					if (backRow1!=0) row1=backRow1; // only have value if there was a coSet, i.e. row1 can only become this backRow value once
					else 			 row1++;
					
					backRow1=0;
					coHitSet.clear();
					bNewCo=true;
					
					break;
				}
				else {
					row2++; 
				}
			}
			if (savRow1==row1) row1++; // prevent endless loops; often happens with backRow
			
			if (++cnt % 10000 == 0) 
				symap.Globals.rprt("Iterations: " + cnt + " Row: " + row1 + " of " + nHits + " rows for " + chrs);
			if (cnt>max) { // insurance: in case a bizarre situation causes an endloop
				symap.Globals.eprt("Too many iterations: " + cnt + " Row1: " + row1 + " of " + nHits);
				prtLine(bInv, coHitSet,"C", hObj1, hObj2, r1G1, r1G2, r2G1, r2G2, row1, row2, d1, d2);
				break;
			}
		}
		if (coHitSet.size()>1) { // finish last set
			for (int i: coHitSet) {
				xHitVec.get(i).frunnum = runnum;
				xHitVec.get(i).frunsize = coHitSet.size();
			}
			runnum++; 
		}
		if (cnt> nHits+10) dprt("Iterations " + cnt); // cnt is generally < nHits
		prtWrong(bInv, xHitVec);
		return true;
	}
	catch (Exception e) {ErrorReport.print(e, "Build sets " + chrs); return false; }
	}
	/***************************************************
	 * Trace and verify print routines
	 */
	private void prtWrong(boolean bInv, Vector <Hit> xHitVec) { // unexpected situation...
		HashMap <Integer, Set> csetMap = new HashMap <Integer, Set> ();
		Set cs;
		
		for (Hit hObj : xHitVec) {
			if (hObj.frunnum==0) continue;
			
			int tnum1 = gidxTnum.get(hObj.gidx1);
			int tnum2 = gidxTnum.get(hObj.gidx2);
			
			if (csetMap.containsKey(hObj.frunnum)) cs = csetMap.get(hObj.frunnum);
			else {
				cs = new Set(hObj.frunsize);
				csetMap.put(hObj.frunnum, cs);
			}
			cs.add(tnum1, tnum2);
		}
		
		for (int rn : csetMap.keySet()) {
			cs = csetMap.get(rn);
			int tsz = cs.tnum1.size();
			if (tsz!=cs.sz) prt("Error in set Size " + chrs + " " + cs.sz + "." + rn + " hits " + tsz);
			
			for (int i=0; i<tsz-1; i++) {
				int r1G1 = cs.tnum1.get(i);
				int r2G1 = cs.tnum1.get(i+1);
				int r1G2 = cs.tnum2.get(i);
				int r2G2 = cs.tnum2.get(i+1);
				
				int d1 =           r2G1 - r1G1;
				int d2 = (!bInv) ? r2G2 - r1G2 : r1G2 - r2G2;
				
				if (d1!=1 || d2!=1) {
					prt("Error in set Order "+ chrs +  " " + cs.sz + "." + rn);
					break;
				}
			}
		}
	}
	private class Set {
		private Set(int sz) {
			this.sz = sz;
		}
		private void add(int t1, int t2) {
			tnum1.add(t1);
			tnum2.add(t2);
		}
		int sz=0;
		Vector <Integer> tnum1 = new Vector <Integer> ();
		Vector <Integer> tnum2 = new Vector <Integer> ();
	}
	private void prtLine(boolean bInv, HashSet <Integer> coHitSet ,String xxx, Hit hObj1, Hit hObj2, 
			int t1G1, int t1G2, int t2G1, int t2G2, int r1, int r2, int d1, int d2) {
		
		String t1Tag1 = geneMap1.get(t1G1).tag; String t2Tag1 = geneMap1.get(t2G1).tag;
		String t1Tag2 = geneMap2.get(t1G2).tag; String t2Tag2 = geneMap2.get(t2G2).tag;
		
		String x1 =  (d1==1)? "T" : "F"; if (t1Tag1.equals(t2Tag1)) x1="=";
		String x2 =  (d2==1)? "T" : "F"; if (t1Tag2.equals(t2Tag2)) x2="=";;
		
		String s1 = String.format("%s %s%s #%d.%d; R %3d %3d; D %5d %5d; ",
				xxx, x1, x2, coHitSet.size(), runnum, r1, r2, d1, d2); 
		int h2 = (hObj2 != null) ? hObj2.hitnum : 0;
		String s2 = String.format("#%-4d #%-4d; t1 %4d %4d; t2 %4d %4d; gn1 %7s %7s; gn2 %7s %7s", 
				hObj1.hitnum,  h2, t1G1, t2G1, t1G2, t2G2, t1Tag1,  t2Tag1, t1Tag2,  t2Tag2);	
		dprt(s1+s2);
	}
	private void dprt(String msg) {symap.Globals.dprt(msg);}
	private void prt(String msg) {symap.Globals.prt(msg);}
	/////////////////////////////////////////////////////////////////
	/*****************************************************************/
	private boolean step3SaveToDB(Vector <Hit> xHitVec) {
	try {
		PreparedStatement ps = dbc2.prepareStatement("update pseudo_hits set runsize=?, runnum=? where idx=?");
		for (Hit hObj : xHitVec) {
			if (hObj.frunnum!=0 && hObj.frunsize>1) { // CAS521 was 0
				ps.setInt(1, hObj.frunsize);
				ps.setInt(2, hObj.frunnum);
				ps.setInt(3, hObj.hidx);
				ps.addBatch();
			}
		}
		ps.executeBatch();
		
		// counts
		for (Hit hObj : xHitVec) {
			int rsize = hObj.frunsize;
			if (rsize>1) {
				if (rsize==2)       cntSizeSet[0]++;
				else if (rsize==3)  cntSizeSet[1]++;
				else if (rsize<=5)  cntSizeSet[2]++;
				else if (rsize<=10) cntSizeSet[3]++;
				else cntSizeSet[4]++;
			}
		}
		return true;
	}
	catch (Exception e) {ErrorReport.print(e, "Save sets to database " + chrs); return false; }
	}
	/******************************************************************
	 * annot_hit:  idx  annot1_hit annot2_hit  (best two overlaps)
	 */
	private class Hit {
		private Hit(int idx, int hitnum, int gidx1, int gidx2, int blocknum) { 
			this.hidx = idx;
			this.hitnum=hitnum;
			this.gidx1= gidx1;
			this.gidx2= gidx2;
		}
		private int hidx, hitnum;			// hitnum debugging only
		private int gidx1=-1, gidx2=-1;     // best
		private int frunsize=0, frunnum=0;	// to be saved to db
	}
	
	/******************************************************
	 * After v556 rewrite, do not need Gene at all, but keep for debugging */
	private class Gene {
		private Gene(int idx,  int tnum, String tag, int start, int end, int genenum, String strand) {
			this.tag  = Utilities.getGenenumFromDBtag(tag);
		}
		private String tag="";	
	} 
}

