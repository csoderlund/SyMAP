package backend;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.Vector;

import database.DBconn2;
import symap.manager.Mproject;
import util.Cancelled;
import util.ErrorReport;
import util.ProgressDialog;
import util.Utilities;

/**************************************************
 * Computes the collinear sets (pseudo_hits.runsize, runnum) and 
 * 	the term 'run' is used here for collinear (run/set)
 * CAS520 added setAnnot, setHitNum and rewrote collinearSets; CAS541 moved setHitNum to AnchorsMain
 */

public class AnchorsPost {
	private boolean debug = false; 
	private boolean test = true;

	private int mPairIdx; // project pair 
	private ProgressDialog mLog;
	private DBconn2 dbc2;
	private Mproject mProj1, mProj2;		// CAS546 was syProj
	private int totalMerge=0, totalMult=0, finalRunNum=0;
	private int [] cntSizeSet = {0,0,0,0,0};
	
	// initial
	private TreeMap<Integer,Hit> hitMap = new TreeMap<Integer,Hit>(); 	

	private TreeMap <Integer, Gene> geneMap1 = new TreeMap <Integer, Gene> (); // gnum, gene
	private TreeMap <Integer, Gene> geneMap2 = new TreeMap <Integer, Gene> (); // gnum, gene
	private HashMap <Integer, Integer> gidxtnumA = new HashMap <Integer, Integer> (); // idx, gnum for 1st numbering
	private HashMap <Integer, Integer> gidxtnumB = new HashMap <Integer, Integer> (); // idx, gnum for collapse numbering
	
	// geneMap1 and geneMap2 are transferred to geneMapF and geneMapR
	private TreeMap <Integer, Gene> geneMapF = new TreeMap <Integer, Gene> (); // gnum, gene with =
	private TreeMap <Integer, Gene> geneMapR = new TreeMap <Integer, Gene> (); // gnum, gene with !=
	private String chrs;
		
	public AnchorsPost(int pairIdx, Mproject proj1, Mproject proj2, DBconn2 dbc2, ProgressDialog log) {
		this.mPairIdx = pairIdx;
		this.mProj1 = proj1;
		this.mProj2 = proj2;
		this.dbc2 = dbc2;
		this.mLog = log;
	}
	
	/*********************************************************
	 * Also does pseudo_hits.hitnum and pseudo_annot.numhits
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
					String t = Utilities.getDurationString(Utils.getTime()-time);
					System.err.print(num2go + " pairs remaining... (" + t + ")   \r"); // CAS541 add time
					num2go--;
					
					if (grpIdx1==grpIdx2) continue; 		// CAS521 can crash on self-chr
					
					chrs = map1.get(grpIdx1) + ":" + map2.get(grpIdx2);
					
					if (!step0BuildSets(grpIdx1, grpIdx2)) return;
				
					debug=false;
				}
				if (Cancelled.isCancelled()) return; // CAS535 there was no checks
			}
			// CAS540 computed counts are not right; just get from db
			int nsets = dbc2.executeInteger("SELECT count(DISTINCT(runnum)) FROM pseudo_hits WHERE runnum>0 and pair_idx=" + mPairIdx);
			int nhits = dbc2.executeInteger("SELECT count(*) FROM pseudo_hits WHERE runnum>0 and pair_idx=" + mPairIdx);
			Utils.prtNumMsg(mLog, nsets, "Collinear sets                          ");
			Utils.prtNumMsg(mLog, nhits, "Updates                          ");
			Utils.timeDoneMsg(mLog, "Collinear", time);
			if (debug) System.out.println("Total merge " + totalMerge + " total mult " + totalMult);
		}
		catch (Exception e) {ErrorReport.print(e, "Compute colinear genes"); }
	}
		
/*****************************************************************************************************
 *                    COLLINEAR SETS between two chromosomes
 */	
	private boolean step0BuildSets(int grpIdx1, int grpIdx2) {
	try {
		if (!step1LoadFromDB(grpIdx1, grpIdx2)) return false;  // hitMap, geneMap1, geneMap2, gidxtnumA
		if (hitMap.size()==0) return true;			 // CAS521 add
		
		if (!step2Hits2Genes()) return false;
		
		if (!step3AssignGeneNum(1, geneMap1)) return false;
		if (!step3AssignGeneNum(2, geneMap2)) return false;
		gidxtnumA.clear();
		
		if (!step4CreateFR()) return false;
		
		finalRunNum=0; // restart for each chr-chr
		if (!step5AssignRunNum(true  /*isR*/ , geneMapR)) return false;
		if (!step6SaveToDB()) return false;
		
		if (!step5AssignRunNum(false /*!isR*/, geneMapF)) return false;
		if (!step6SaveToDB()) return false;
		
		geneMap1.clear(); geneMap2.clear(); gidxtnumB.clear();
		geneMapF.clear(); geneMapR.clear(); hitMap.clear();

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
			
		// Hits to 2 genes - annot1 and annot2 have the best overlap; ignore others
			rs = dbc2.executeQuery("select ph.idx, ph.strand, ph.hitnum, ph.annot1_idx, ph.annot2_idx, B.blocknum " +
				" from pseudo_hits as ph  " +
				" LEFT JOIN pseudo_block_hits AS PBH ON PBH.hit_idx=ph.idx" +
				" LEFT JOIN blocks AS B ON B.idx=PBH.block_idx " +
				" where ph.grp1_idx=" + grpIdx1 + " and ph.grp2_idx=" + grpIdx2 + 
				" and ph.gene_overlap>1"); 
			while (rs.next()) {
				int i=1;
				int hidx = 		rs.getInt(i++);
				String str = 	rs.getString(i++);
				int hitnum =	rs.getInt(i++);
				int gidx1= 		rs.getInt(i++);
				int gidx2 = 	rs.getInt(i++);
				int blocknum =  rs.getInt(i++);
				
				boolean inv = (str.contains("+") && str.contains("-"));
				
				if (!hitMap.containsKey(hitnum)) {
					hObj = new Hit(inv, hidx, hitnum, gidx1, gidx2, blocknum);
					hitMap.put(hitnum, hObj);
				}
				else if (test) System.out.println("SyMAP error: two hitnum=" + hitnum); 
			}
			// only returns one side of hit; this is to find where a hit aligns to overlapping genes
			rs = dbc2.executeQuery(
					"select ph.hitnum, pa.idx, pa.grp_idx  from pseudo_hits       as ph " +
					" join pseudo_hits_annot as pha on ph.idx = pha.hit_idx" +
					" join pseudo_annot      as pa  on pa.idx = pha.annot_idx " +
					" where ph.grp1_idx=" + grpIdx1 + " and ph.grp2_idx=" + grpIdx2 + 
					" and ph.gene_overlap>1 ");
			while (rs.next()) {
				int hitnum =	rs.getInt(1);
				int gidx = 		rs.getInt(2);
				int chridx= 	rs.getInt(3);
				
				if (hitMap.containsKey(hitnum)) {
					hObj = hitMap.get(hitnum);
					hObj.addGeneIdx((chridx==grpIdx1), gidx);
				}
				else if (test) System.err.println("SyMAP error: no hitnum " + hitnum);
			}
			
	// All genes 
			String ssql = "select idx, tag, start, end, genenum, strand, (end-start) as len from pseudo_annot "
								+ "	where type='gene' and grp_idx="; 
			String osql = " order by start ASC, len DESC";
			                        
			int tnum1=0;
			rs = dbc2.executeQuery(ssql + grpIdx1 + osql);
			while (rs.next()) {
				int idx = rs.getInt(1);		// tag          start         end            genenum        strand
				gObj = new Gene(idx, tnum1, rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getString(6));
				geneMap1.put(tnum1, gObj);  // using tnum keeps them ordered by start
				gidxtnumA.put(idx, tnum1);
				tnum1++;
			}
			
			int tnum2=0;
			rs = dbc2.executeQuery(ssql + grpIdx2 + osql);
			while (rs.next()) {
				int idx = rs.getInt(1);
				gObj = new Gene(idx, tnum2, rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getString(6));
				geneMap2.put(tnum2, gObj);	
				gidxtnumA.put(idx, tnum2);
				tnum2++;
			}
			rs.close();
	
			if (debug) System.out.println("Total genes1 tnum: " + tnum1 + " Total genes2 tnum: " + tnum2);
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, "Load from DB " + chrs); return false; }
	}
	/*******************************************************************/
	private boolean step2Hits2Genes() {
	try {
		// Transfer hit set to each gene
		for (Hit hObj : hitMap.values()) {
			for (int gidx : hObj.geneSet1) {
				int tnum = gidxtnumA.get(gidx);
				geneMap1.get(tnum).hitIdxSet.add(hObj.hitnum);
			}
			for (int gidx : hObj.geneSet2) {
				int tnum = gidxtnumA.get(gidx);
				geneMap2.get(tnum).hitIdxSet.add(hObj.hitnum);
			}
		}
		// Assign hit-g2 to g1 using hit.gidx1 (annot1_idx) and hit.gidx2 (annot2_idx)
		for (int hn : hitMap.keySet()){
			Hit hObj = hitMap.get(hn);
		
			if (!gidxtnumA.containsKey(hObj.gidx1)) return die("no gidx1 " + hObj.gidx1);
			if (!gidxtnumA.containsKey(hObj.gidx2)) return die("no gidx2 " + hObj.gidx2);
			
			int tnum1 = gidxtnumA.get(hObj.gidx1); 
			int tnum2 = gidxtnumA.get(hObj.gidx2);
			
			if (!geneMap1.containsKey(tnum1)) return die("no tnum1 " + tnum1 + " gidx1 " + hObj.gidx1 + " Hit#" + hObj.hitnum);
			if (!geneMap2.containsKey(tnum2)) return die("no tnum2 " + tnum2 + " gidx2 " + hObj.gidx2 + " Hit#" + hObj.hitnum);
			
			Gene gObj1 = geneMap1.get(tnum1);
			Gene gObj2 = geneMap2.get(tnum2);
			
			gObj1.isMain=true;
			gObj2.isMain=true;
			
			if (gObj1.addHit(gObj2, hObj)) totalMult++;
			
			if (hObj.bInv) gObj2.rtnum = 1; // to be updated after renumbering
		}		
		return true;
	}
	catch (Exception e) {ErrorReport.print(e, "Hits to genes " + chrs); return false; }
	}
	/***************************************************************
	 * Collapse overlapping genes with shared hits
	 * ReAssign unique numbers to the rest
	 */
	private boolean step3AssignGeneNum(int mapNum, TreeMap <Integer, Gene> geneMap) {
	try {
		// Set skip non-blocks hits if there is a block one 
		for (Gene g : geneMap1.values()) g.removeHits();
						
		TreeMap <Integer, Olaps> olapSet = new TreeMap <Integer, Olaps> ();
		Vector <Gene> geneVec = new Vector <Gene> ();
			
		// Find overlap genes
		for (Gene gObj : geneMap.values()) {
			if (!gObj.isOlap) continue;
			
			if (!olapSet.containsKey(gObj.genenum)) olapSet.put(gObj.genenum, new Olaps());
			Olaps og = olapSet.get(gObj.genenum);
			og.tnum.add(gObj.tnum);
		}
		
		// Collapse overlaps with same hits
		for (Gene mainObj : geneMap.values()) {
			if (!mainObj.isMain) continue; 
			if (!mainObj.isOlap) continue;
			
			Olaps olObj = olapSet.get(mainObj.genenum);
			
			for (int tnum : olObj.tnum) {
				if (tnum!=mainObj.tnum) {
					Gene xObj = geneMap.get(tnum);
					if (!xObj.isMain && !xObj.isMerge) {
						xObj.setMerge(mainObj, mainObj.hitIdxSet);  // sets isMerge if hits are the same
					}
				}
			}
		}
		olapSet.clear();
		
		for (Gene gObj : geneMap.values()) {
			if (!gObj.isMerge) geneVec.add(gObj);
			else totalMerge++;
		}
		
		// transfer new set back to geneMap and assign new tnums
		geneMap.clear();
		int maxN2 = geneVec.size();
		int tnum=1;
		
		for (Gene gObj : geneVec) {
			gObj.tnum = tnum++;
			// reverse the g2 gene numbers for inverted hits to connect them backwards
			if (mapNum==2 && gObj.rtnum>0) gObj.rtnum = maxN2 - gObj.tnum;
						
			geneMap.put(gObj.tnum, gObj);
			gidxtnumB.put(gObj.idx, gObj.tnum);
		}
		geneVec.clear();
		
		return true;
	}
	catch (Exception e) {ErrorReport.print(e, mapNum + " assign tmpnum " + chrs); return false; }
	}
	private class Olaps {
		Vector <Integer> tnum = new Vector <Integer> ();
	}
	
	/*****************************************************************
	 * Transfer genes to geneMapF (=) and geneMapR (!=); All genes with hits get added
	 */
	private boolean step4CreateFR() {
	try {
		for (int hn : hitMap.keySet()){
			Hit hObj = hitMap.get(hn);
			if (hObj.skip) continue;
			
			if (!gidxtnumB.containsKey(hObj.gidx1)) {
				if (test) System.out.println("Not exist #" + hObj.hitnum + " " + hObj.gidx1);
				continue;
			}
			int tnum1 = gidxtnumB.get(hObj.gidx1); 
			Gene gObj1 = geneMap1.get(tnum1);
			
			if (hObj.bInv) { 
				if (!geneMapR.containsKey(tnum1)) geneMapR.put(tnum1, gObj1);
			}
			else {
				if (!geneMapF.containsKey(tnum1)) geneMapF.put(tnum1, gObj1);
			}	
		}
		return true;
	}
	catch (Exception e) {ErrorReport.print(e, "create FR " + chrs); return false; }
	}
	/*************************************************************
	 * All genes in geneMap have a hit, but if the next gene#!=prev+1, then skip a gene
	 * but what if overlapping gene has hit but is not in geneMap because not annot_idx
	 */
	private boolean step5AssignRunNum(boolean bInv, TreeMap <Integer, Gene> geneMap) {
	try {
		int lastg1=-1, lastg2=-1;
		TreeMap <Integer, Integer> runSizeMap = new TreeMap <Integer, Integer> (); // runnum, runsize
		
		int tmpnum=0;
		
	// assign runnums - geneMap sorted by tnum/rtnum
		for (int t1num : geneMap.keySet()) {
			Gene gObj1 = geneMap.get(t1num);
			
			gObj1.setGeneIndex(bInv, (lastg2+1));
			
			Hit  hObj =   gObj1.getHitObj();
			Gene gObj2 =  gObj1.getGeneObj();
		
			int t2num = (hObj.bInv) ? gObj2.rtnum : gObj2.tnum;
			
			boolean b1notLast = (t1num != lastg1+1); 
			boolean b2notLast = (t2num != lastg2+1);
			if (b1notLast || b2notLast) tmpnum++; 
			
			hObj.runnum = tmpnum;
			lastg1 = t1num; 
			lastg2 = t2num;
			
			if (runSizeMap.containsKey(tmpnum)) runSizeMap.put(tmpnum, runSizeMap.get(tmpnum)+1);
			else 								runSizeMap.put(tmpnum, 1);	
		}
		
		// reassign final runnums where size>1
		TreeMap <Integer, Integer> goodRunMap = new TreeMap <Integer, Integer> ();
		for (int tnum : runSizeMap.keySet()) {
			if (runSizeMap.get(tnum)>1) {
				finalRunNum++;
				goodRunMap.put(tnum, finalRunNum);
			}
		}
		
		// transfer runnum to final collinear set numbers and sizes
		for (int hitnum : hitMap.keySet()) {
			Hit hObj = hitMap.get(hitnum);
			int tnum = hObj.runnum;
			if (tnum>0 && goodRunMap.containsKey(tnum)) {
				hObj.frunsize = runSizeMap.get(tnum);
				hObj.frunnum  = goodRunMap.get(tnum);
			}
		}
		return true;
	}
	catch (Exception e) {ErrorReport.print(e, "Build sets " + chrs); return false; }
	}
	/*****************************************************************/
	private boolean step6SaveToDB() {
	try {
		PreparedStatement ps = dbc2.prepareStatement("update pseudo_hits set runsize=?, runnum=? where idx=?");
		for (int hitnum : hitMap.keySet()) {
			Hit hObj = hitMap.get(hitnum);
			int rsize = hObj.frunsize;
			if (rsize>1) { // CAS521 was 0
				ps.setInt(1, rsize);
				ps.setInt(2, hObj.frunnum);
				ps.setInt(3, hObj.hidx);
				ps.addBatch();
			}
		}
		ps.executeBatch();
		
		// counts
		for (int hidx : hitMap.keySet()) {
			Hit hObj = hitMap.get(hidx);
			int rsize = hObj.frunsize;
			if (rsize>1) {
				if (rsize==2)       cntSizeSet[0]++;
				else if (rsize==3)  cntSizeSet[1]++;
				else if (rsize<=5)  cntSizeSet[2]++;
				else if (rsize<=10) cntSizeSet[3]++;
				else cntSizeSet[4]++;
			}
			hObj.frunsize=hObj.frunnum=hObj.runnum=0;
		}
		return true;
	}
	catch (Exception e) {ErrorReport.print(e, "Save sets to database " + chrs); return false; }
	}
	/******************************************************************
	 * annot_hit:  idx  annot1_hit annot2_hit  (best two overlaps)
	 */
	private class Hit {
		private Hit(boolean inv, int idx, int hitnum, int gidx1, int gidx2, int blocknum) {
			this.bInv = inv; 
			this.hidx = idx;
			this.hitnum=hitnum;
			this.gidx1= gidx1;
			this.gidx2= gidx2;
			this.blocknum=blocknum;
		}
		private void addGeneIdx(boolean is1st, int idx) {
			if (is1st) 	geneSet1.add(idx);
			else 		geneSet2.add(idx);
		}
		
		private boolean bInv=false;
		private int hidx, hitnum, blocknum;
		private int gidx1=-1, gidx2=-1;     // best
		private TreeSet <Integer> geneSet1 = new TreeSet <Integer> ();
		private TreeSet <Integer> geneSet2 = new TreeSet <Integer> ();
		
		private int frunsize=0, frunnum=0;	// to be saved to db
		private int runnum=0;				// working...
		private boolean skip=false;
	}
	
	/******************************************************/
	private class Gene {
		private Gene(int idx,  int tnum, String tag, int start, int end, int genenum, String strand) {
			this.idx = idx;
			this.tnum = tnum;
			this.start = start;
			this.end = end;
			this.genenum = genenum;
			this.tag  = Utilities.getGenenumFromDBtag(tag);
			String [] tok = this.tag.split("\\.");
			isOlap = (tok.length>1);
			this.bPosStrand= (strand.contentEquals("+"));
		}
		
		public boolean addHit(Gene gObj, Hit hObj) {
			gObj2Vec.add(gObj);
			hObjVec.add(hObj);
			vSize = gObj2Vec.size();
			return (vSize>1);
		}
		
		// keep block and remove non-block; this got rid of problem of only having N hits for collinear set of size N+1
		private void removeHits() {
			if (vSize==1) return;
				
			Vector <Gene> gObj2Tmp = new Vector <Gene> ();
			Vector <Hit> hObjTmp =  new Vector <Hit> ();
			for (int i=0; i<vSize; i++) {
				if (hObjVec.get(i).blocknum==0) {
					hObjVec.get(i).skip=true;
					hitIdxSet.remove(hObjVec.get(i).hidx);
				}
				else {
					gObj2Tmp.add(gObj2Vec.get(i));
					hObjTmp.add(hObjVec.get(i));
				}
			}
			hObjVec = hObjTmp;
			gObj2Vec = gObj2Tmp;
			vSize = gObj2Vec.size();
		}
		// if it is contained, and has exact same hits as main overlap, it is removed
		private void setMerge(Gene mainObj, TreeSet <Integer> hitSet) {
			if (hitSet.size()!=hitIdxSet.size()) return;
			if (!(start>=mainObj.start && end<=mainObj.end)) return;
			
			for (int x : hitSet) {
				if (!hitIdxSet.contains(x)) return;
			}
			isMerge=true;
		}
		
		// if multiple hits-gene2, find the one that is last or closest
		private int setGeneIndex(boolean inv, int last) {
			vSize=gObj2Vec.size();
			if (vSize==1) {vIdx=0; return 0;};
			
			for (int i=0; i<vSize; i++) {
				if (inv  && gObj2Vec.get(i).rtnum==last) {vIdx=i; return vIdx;}
				if (!inv && gObj2Vec.get(i).tnum==last)  {vIdx=i; return vIdx;}
			}
			
			int bestdiff= (inv) ?  (gObj2Vec.get(0).rtnum-last) : (gObj2Vec.get(0).tnum-last);
			for (int i=1; i<vSize; i++) {
				int diff = (inv) ? (gObj2Vec.get(i).rtnum-last) : (gObj2Vec.get(i).tnum-last);
				if (diff<bestdiff) {
					vIdx=i;
					bestdiff=diff;
				}
			}	
			return vIdx;
		}
		private Hit getHitObj()   { return hObjVec.get(vIdx);}
		private Gene getGeneObj() { return gObj2Vec.get(vIdx);}
		
		private void prt(String msg) {
			if (gObj2Vec.size()<=0) {
				System.out.println(msg + " " + tag + " TmpGN " + tnum + " idx " + idx + " " + bPosStrand);
				return;
			}
			for (int i=0; i<vSize; i++) {
				String h = "#" + hObjVec.get(i).hitnum;
				String x = (hObjVec.get(i).bInv) ? "I" : " ";
				String b = (hObjVec.get(i).blocknum>0) ? "B" : " "; 
				String g1 = "GN1 " + tag;
				String g2 = "GN2 " + gObj2Vec.get(i).tag;
				String s = (i==0) ? "Run" : "   ";
				String  hit = String.format("%s %3d %-10s Hit %-4s %s%s %-10s:: ", s, hObjVec.get(i).runnum, 
						g1, h, x, b, g2);
		
				int g2num = (hObjVec.get(i).bInv) ? gObj2Vec.get(i).rtnum : gObj2Vec.get(i).tnum;
				String gene = String.format("   TmpGN %4d %6d  V: %d,%d", tnum,  g2num, vSize, vIdx);
				
				System.out.println(msg + " " + hit + " " + gene + " " + idx + ":" + gObj2Vec.get(i).idx + " "+ chrs + 
						" Mg"+isMerge+" A"+isMain+hitIdxSet.size());
			}
		}
	
		// gObj2Vec and hObjVec go together.
		private Vector <Gene> gObj2Vec = new Vector <Gene> ();
		private Vector <Hit> hObjVec =  new Vector <Hit> ();
		
		private TreeSet <Integer> hitIdxSet = new TreeSet <Integer>();
		private boolean isMerge=false; // if hits are the same as another
		private boolean isMain=false;  // pseudo_hits.annot1_idx or anno2_idx
		
		private int vSize=0, vIdx=0;
		private int tnum=0;  // set in runLoadFromDB
		private int rtnum=0; // set in runCreateFR for R
		
		private String tag="";	// // e.g. Gene #999 (4 1,128bp) 
		private int idx, genenum, start, end;
		private boolean isOlap=false, bPosStrand=true;
	} // end Gene
	private boolean die(String msg) {System.out.println(msg); return false;}
}

