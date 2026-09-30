package backend;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.Vector;

import database.DBconn2;
import symap.Globals;
import symap.manager.Mpair;
import symap.manager.Mproject;
import util.Cancelled;
import util.ErrorReport;
import util.ProgressDialog;
import util.Utilities;

/************************************************************
 * Compute collinear sets advanced; added CAS588
 * Database: (pseudo_hits.runsize, runnum); the term 'run' is used here for collinear (run/set)
 * NOTES: This is similar to AnchorSet1 but:
 * 1. Merges sets after initial creation.
 * 2. It reduces 2-hit sets (see setInitFinish and setInitPrune)
 * 3. It is a little better at area of extreme overlapping genes, though it does it by often ignoring embedded genes.
 */
public class AnchorCset2 {
	private boolean DEBUG=Globals.DEBUG, TRACE=Globals.TRACE; // prtdt uses -dd; TRACE -tt; if TRACE, then DEBUG 

	// parameters
	private int mPairIdx; 				// project pair
	private Mproject mProj1, mProj2;	// two projects
	private ProgressDialog mLog;
	private DBconn2 dbc2;
	private boolean isSelf=false; 
	private boolean bNumBySz=false;
	
	// initial; loaded from db	
	private Vector <Hit> fHitVec = new Vector <Hit> (); // forward set, in hitnum order
	private Vector <Hit> rHitVec = new Vector <Hit> (); // reverse set, in hitnum order
	private TreeMap <Integer, Gene> geneMap1 = new TreeMap <Integer, Gene> (); 		 // idx, gene
	private TreeMap <Integer, Gene> geneMap2 = new TreeMap <Integer, Gene> (); 		 // idx, gene 
	
	private Vector <Set> chrsSetList = new Vector <Set> (); // results: create and written per chr-chr
	private int coSetNum=1;									// working num: recomputed before saveToDB
	private int totalRuns=0, cntForward=0, cntReverse=0, cntOlap=0, cntEmbed=0;
	private int cntMergeHit=0, cntMergeGene=0, cntPrune=0, cntCross=0;
	
	private String chrs;	// ErrorReport and Trace
	
	/** Called from AnchorMain **/
	protected AnchorCset2(Mpair mp, Mproject proj1, Mproject proj2, DBconn2 dbc2, ProgressDialog log) {
		this.mPairIdx = mp.getPairIdx();
		this.mProj1 = proj1;
		this.mProj2 = proj2;
		this.dbc2 = dbc2;
		this.mLog = log;
		isSelf = proj1.getIdx()==proj2.getIdx();
		bNumBySz = !mp.isCoSetNum(Mpair.FILE);
		if (TRACE) DEBUG=true;
	}
	protected void collinearSets() {
	try {
		dbc2.executeUpdate("update pseudo_hits set runsize=0, runnum=0 where pair_idx=" + mPairIdx);
		
		int num2go = mProj1.getGrpSize() * mProj2.getGrpSize();
		Utils.prtMsgFile(mLog, "   Finding Collinear sets"); 
		long time = Utils.getTime();
		
		TreeMap <Integer, String> map1 = mProj1.getGrpIdxMap();
		TreeMap <Integer, String> map2 = mProj2.getGrpIdxMap();
		
		for (int grpIdx1 : map1.keySet()) {
			for (int grpIdx2 : map2.keySet()) {
				if (isSelf && grpIdx1<grpIdx2) continue;
				
				chrs = map1.get(grpIdx1) + ":" + map2.get(grpIdx2);
				String t = Utilities.getDurationString(Utils.getTime()-time);
				Globals.rprt(num2go + " pairs remaining (" + t + ")"); 
				num2go--;	
				
				if (!buildSets(grpIdx1, grpIdx2)) return;
			}
			if (Cancelled.isCancelled()) return; 		
		}
		Globals.rclear();
		
		int nhits = dbc2.executeInteger("SELECT count(*) FROM pseudo_hits WHERE runnum>0 and pair_idx=" + mPairIdx);
		Utils.prtNumMsg(mLog, totalRuns, "Collinear sets                          ");
		if (Constants.VERBOSE || DEBUG) {
			Utils.prtNumMsg(mLog,  cntForward, String.format("!Inverted     %,6d Inverted ", cntReverse));
			Utils.prtNumMsg(mLog,  cntOlap,    String.format("Gene Overlap  %,6d Embedded ", cntEmbed));
			Utils.prtNumMsg(mLog,  (cntMergeHit+cntMergeGene),  
					                           String.format("Merge Sets    %,6d Remove 2-Hit Sets", (cntPrune+cntCross)));
			Utils.prtNumMsg(mLog, nhits, "Collinear hits                        ");
			Utils.prtMsgTime(mLog, "   Finish Collinear", time);
			dprt(String.format("Prune %d  Crossed %d  Merge Gene %d  Merge Hit %d  ", cntPrune, cntCross, cntMergeGene, cntMergeHit));
		}
	}
	catch (Exception e) {ErrorReport.print(e, "Compute colinear genes"); }
	}
		
	/*****************************************************************************************************
	 *    COLLINEAR SETS between two chromosomes
	 */	
	private boolean buildSets(int grpIdx1, int grpIdx2) {
	try {
		coSetNum=1;
		if (!loadFromDB(grpIdx1, grpIdx2)) return false;  // load: fHitVec <Hit>, rHitVec, geneMap1 <Gene>, geneMap2
	
		if (!new Compute(false, fHitVec).setsChrChrMain()) return false;  // create: chrsSetList <Set>
		if (!new Compute(true,  rHitVec).setsChrChrMain()) return false;  // add to: chrsSetList <Set>; must be run 2nd for olap
		
		if (!saveToDB()) return false; // save chrsSetList
		
		totalRuns+= chrsSetList.size();
		chrsSetList.clear();		   // hitVec already cleared
		
		geneMap1.clear(); geneMap2.clear(); 
		fHitVec.clear();  rHitVec.clear(); 
		
		return true;
	}
	catch (Exception e) {ErrorReport.print(e, "Build collinear " + chrs); return false; }
	}
	
	/*************************************************************
	 * Create hit and gene maps
	 */
	private boolean loadFromDB(int grpIdx1, int grpIdx2) {
	try {
	// Hits with 2 genes - annot1 and annot2 have the best overlap; ignore others; 
		String sql = "select PH.idx, PH.strand, PH.hitnum, PH.annot1_idx, PH.annot2_idx, PH.start2 " +
				" from pseudo_hits 				AS PH  " +
				" left join pseudo_block_hits 	AS PBH ON PBH.hit_idx=PH.idx" +
				" where PH.grp1_idx=" + grpIdx1 + " and PH.grp2_idx=" + grpIdx2;
		
		if (isSelf && grpIdx1==grpIdx2) sql += " AND PH.start1 > PH.start2 "; // DIR_SELF 
		sql +=	" and PH.gene_overlap>1 order by PH.hitnum";// algo1 does not enter them ordered 
		
		ResultSet rs = dbc2.executeQuery(sql); 
		while (rs.next()) {
			int i=1;
			int hidx = 		rs.getInt(i++);
			String str = 	rs.getString(i++);
			int hitnum = 	rs.getInt(i++);
			int gidx1 = 	rs.getInt(i++);
			int gidx2 = 	rs.getInt(i++);
			int start = 	rs.getInt(i++);
			
			Hit hObj = new Hit(hidx, hitnum, gidx1, gidx2, start);
			
			boolean inv = (str.contains("+") && str.contains("-"));
			if (inv) rHitVec.add(hObj);
			else  	 fHitVec.add(hObj);
		}
		rs.close();
				
	// All genes
		String ssql = "select idx, tag, start, end, genenum, strand, (end-start) as len from pseudo_annot "
							+ "	where type='gene' and grp_idx="; 
		String osql = " order by start ASC, len DESC";
		     
		// Grp1
		Gene lastg=null;		// keep tnum the same for embedded; this causes some problems which are corrected in prune
		int tnum1=0;  			// using tnum keeps them ordered by start
		rs = dbc2.executeQuery(ssql + grpIdx1 + osql);
		while (rs.next()) {
			int gidx = rs.getInt(1);		// tag          start         end            genenum        strand
			Gene gObj = new Gene(gidx, tnum1, rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getString(6));
			geneMap1.put(gidx, gObj);
			
			if (lastg!=null && lastg.isOlap && gObj.isOlap && lastg.bContains(gObj)) {
				gObj.isEmbed = true; 
				cntEmbed++;
			}
			else {
				gObj.tnum = (++tnum1); // replace tnum unless embedded
				lastg = gObj;
			}
			if (gObj.isOlap) cntOlap++;
		}
		// Grp2
		lastg = null;
		int tnum2=0;	
		rs = dbc2.executeQuery(ssql + grpIdx2 + osql);
		while (rs.next()) {
			int gidx = rs.getInt(1);
			Gene gObj = new Gene(gidx, tnum2, rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getString(6));
			geneMap2.put(gidx, gObj);
			
			if (lastg!=null && lastg.isOlap && gObj.isOlap && lastg.bContains(gObj)) {
				gObj.isEmbed = true; 
				cntEmbed++;
			}
			else {
				gObj.tnum = (++tnum2); // replace tnum unless embedded
				lastg = gObj;
			}
			if (gObj.isOlap) cntOlap++;
		}
		return true;
	}
	catch (Exception e) {ErrorReport.print(e, "Load from DB " + chrs); return false; }
	}
	
	/*****************************************************************/
	private boolean saveToDB() {
	try {
		if (chrsSetList.size()==0) return true;
		
		if (bNumBySz) {	// Reassign coset num based on size and hitnum
			Collections.sort(chrsSetList, new Comparator<Set>() {
				public int compare(Set a1, Set a2) { 
					if (a2.sz==a1.sz) return a1.hitnum - a2.hitnum;
					return a2.sz - a1.sz;		  
				}
			});
		}
		else {
			Collections.sort(chrsSetList, new Comparator<Set>() {
				public int compare(Set a1, Set a2) { 
					return a1.hitnum - a2.hitnum; 
				}
			});
		}	
		int setNum=1;
		for (Set cs : chrsSetList) {
			cs.setNum = setNum++;
			cs.sz = cs.hitVec.size();
		}
			
		PreparedStatement ps = dbc2.prepareStatement("update pseudo_hits set runsize=?, runnum=? where idx=?");
		for (Set cs : chrsSetList) {
			if (cs.bInv) cntReverse++; else cntForward++;
			for (Hit hObj : cs.hitVec) {
				ps.setInt(1, cs.sz);
				ps.setInt(2, cs.setNum);
				ps.setInt(3, hObj.hidx);
				ps.addBatch();
			}
			cs.hitVec.clear(); cs.sz=cs.setNum=0;
		}
		ps.executeBatch();
		
		if (isSelf) { // mirror collinear for self
			dbc2.executeUpdate("update pseudo_hits as ph1, pseudo_hits as ph2 "
				+ " set ph2.runsize=ph1.runsize, ph2.runnum=ph1.runnum "
				+ " where ph1.pair_idx=" + mPairIdx + " and ph2.pair_idx=" + mPairIdx
				+ " and ph1.idx=ph2.refidx");
		}
		return true;
	}
	catch (Exception e) {ErrorReport.print(e, "Save sets to database " + chrs); return false; }
	}
	
	private void dprt(String msg) {symap.Globals.dprt(msg);}
	
	/******************************************************************************/
	private class Compute {
		private Vector <Hit> xHitVec; // input: all hits for this bInv and this chr-chr 
		private boolean bInv;		  // input: run once with !bInv then run with bInv
		
		// the following are global to this class because they are used by multiple methods
		private Hit ht1=null, ht2=null;
		private Gene gH1g1=null, gH1g2=null, gH2g1=null, gH2g2=null; // H can be Hit number or Set number
		
		private int d1, d2, t1, t2;
		private boolean b1=false, b2=false, bSuccess=true;
		private String drows=""; // globals for debugging; only used in setsInit
		
		private TreeMap <Integer, Olap> olapMap1 = new TreeMap <Integer, Olap> (); // created loadFromDB, used in setAllMerge
		private TreeMap <Integer, Olap> olapMap2 = new TreeMap <Integer, Olap> ();
		
		private Compute(boolean bInv, Vector <Hit> xHitVec) {
			this.bInv = bInv;
			this.xHitVec = xHitVec; 
		}
		
		/*************************************************************
		 * This iterates through hits finding adjacent ones with adjacent genes
		 * Hits are numbered along the gene1 side. The gene2 side may be inverted.
		 */
		private boolean setsChrChrMain() {// treat xHitVec as set of rows
		try {	
			if (xHitVec.size()==0) return true;
			
			Vector <Set> setList = new Vector <Set> ();   // created for this bInv and transferred to chrsSetVec, which has bInv and Inv
			
			setsInit(setList);	  if (!bSuccess) return false;
			
			setsMerge(setList);   if (!bSuccess) return false;
			
			for (Set st : setList) {
				if (st.sz>1) chrsSetList.add(st);	// put in final list for this chr-chr
			}
			return true;	
		}
		catch (Exception e) {ErrorReport.print(e, "Build sets " + chrs); return false; }
		}
		/*
		 * setsInit: Creates sets from adjacent hits with adjacent genes
		 */
		private boolean setsInit(Vector <Set> setList) {
		try {
			Vector <Hit> hitSetList = new Vector <Hit> ();	// hits in current set
		
			int savRow1, backRow=0;
			int cntLoops=0, maxCnt = xHitVec.size()*100;
			int row1=0, row2=0, nHits = xHitVec.size();
			
			while (row1 < nHits-1) {
				ht1 = xHitVec.get(row1);
				if (ht1.setNum>0) {
					row1++; 
					continue;
				}
				savRow1 = row1;
				
				if (hitSetList.size()==0) hitSetList.add(ht1); // start new set
				
				gH1g1 = geneMap1.get(ht1.gidx1);
				gH1g2 = geneMap2.get(ht1.gidx2);
				
				row2 = row1+1;
				while (row2 < nHits) { // either breaks or row2++ 
					ht2 = xHitVec.get(row2);
					if (ht2.setNum>0 || ht1.gidx1==ht2.gidx1 || ht1.gidx2==ht2.gidx2) {
						row2++;
						continue;	
					}	
					gH2g1 = geneMap1.get(ht2.gidx1);
					gH2g2 = geneMap2.get(ht2.gidx2);
					
					if (TRACE) drows=row1+":"+row2+":"+backRow;
					if (isGoodGene("P")) { // add to current set
						hitSetList.add(ht2); 
						
						if (backRow==0) backRow=row1+1; // one past beginning of this set 
						row1 = row2; 					  
						break;  		// break row2, resume outer row1
					}
			
					if (d1>1 && Math.abs(d2)>1) { // finish current set since distance between genenums is growing
						setsInitFinish(hitSetList, setList);
						
						hitSetList.clear();
						if (backRow!=0) row1 = backRow; // only have value if there was a coSet, i.e. row1 can only become this backRow value once
						else row1++;
					
						backRow = 0;
						break;  		// break row2, resume outer row1
					}
					row2++; 	// keep looking in inner row2
				}// end inner loop
				
				if (savRow1==row1) row1++; // prevent endless loops; can happen if row2 hits the end w/o increment row1
				
				if (++cntLoops % 10000 == 0) 
					symap.Globals.rprt("Iterations: " + cntLoops + " Row: " + row1 + " of " + nHits + " rows for " + chrs);
				
				if (cntLoops>maxCnt) { // insurance: in case a bizarre situation causes an endloop
					Globals.eprt("Too many iterations: " + cntLoops + " Row1: " + row1 + " of " + nHits);
					Globals.prt(String.format("   Inv %s  Hits %d %d ",Globals.toTF(bInv), ht1.hitnum, ht2.hitnum));
					break; // out of outer loop
				}
			}// end output loop
			if (hitSetList.size()>1) setsInitFinish(hitSetList, setList); // finish last set
			
			for (Set st : setList) st.sz = st.hitVec.size();
			
			setsInitPrune(setList);
			
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, "Create sets " + chrs); bSuccess=false; return false; }
		}
		/*
		 * transfer set from hitSetVec to setList 
		 */
		private boolean setsInitFinish(Vector <Hit> hitSetVec, Vector <Set> setList) {
		try { 
			if (hitSetVec.size()==1) return true;
			
			if (hitSetVec.size()==2) { // 1st, remove if genes matched twice; tends to happen on the inverse
				int usedCnt=0;
				for (Hit ht : hitSetVec) {
					if (geneMap1.get(ht.gidx1).cntSet>0) usedCnt++;
					if (geneMap2.get(ht.gidx2).cntSet>0) usedCnt++;
				}
				if (usedCnt>2) { 
					if (TRACE) dprt(String.format("Cross #%d  %s %s   %s %s", hitSetVec.get(0).hitnum, geneMap1.get(hitSetVec.get(0).gidx1).tag, 
							geneMap2.get(hitSetVec.get(0).gidx2).tag, chrs, Globals.toTF(bInv)));
					cntCross++;
					return true;
				}
			}
			
			Set csSet = new Set (hitSetVec.size(), coSetNum, bInv, hitSetVec.get(0).hitnum);
			setList.add(csSet);
			
			for (Hit ht : hitSetVec) {
				csSet.addHitSE(ht);
				ht.setNum = coSetNum;
				geneMap1.get(ht.gidx1).cntSet++;
				geneMap2.get(ht.gidx2).cntSet++;
			}
			coSetNum++; 
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, "Finish sets " + chrs); bSuccess=false; return false; }
		}
		/*
		 * Remove 2-set (Cherry picking problems):
		 * (1) genes are in other sets (most are taken care of in setsInitFinish, but not all
		 * (2) a gene is embedded and the next gene is not its parent (use genenum which includes overlaps)
		 */
		private void setsInitPrune(Vector <Set> setList) {
		try {
			for (Set st : setList) {
				if (st.sz>2 || st.sz<=1) continue;
				
				int usedCnt=0, embedCnt=0;
				Hit ht1 = st.hitVec.get(0), ht2 = st.hitVec.get(1);
				Gene h1g1 = geneMap1.get(ht1.gidx1), h1g2 = geneMap2.get(ht1.gidx2);
				Gene h2g1 = geneMap1.get(ht2.gidx1), h2g2 = geneMap2.get(ht2.gidx2);
				
				if (h1g1.cntSet>1) usedCnt++;	if (h1g2.cntSet>1) usedCnt++;
				if (h2g1.cntSet>1) usedCnt++;	if (h2g2.cntSet>1) usedCnt++;
				
				if (h1g1.genenum != h2g1.genenum && (h1g1.isEmbed || h2g1.isEmbed)) embedCnt++;
				if (h1g2.genenum != h2g2.genenum && (h1g2.isEmbed || h2g2.isEmbed)) embedCnt++;
					
				if (usedCnt>2 || embedCnt>0) { 
					if (TRACE) {
						String msg = String.format("#%d #%d  %s %s  %s %s  Used %d Inbed %d  %s %s", ht1.hitnum, ht2.hitnum, 
							h1g1.tag, h2g1.tag,h1g2.tag, h2g2.tag, usedCnt, embedCnt, chrs, Globals.toTF(bInv));
						if (usedCnt>2) dprt("Prune1 " + msg); else dprt("Prune2 " + msg);
					}		
					cntPrune++;
					st.setNum=st.sz=0;
					st.hitVec.clear();
				}
			}
		}
		catch (Exception e) {ErrorReport.print(e, "Prune sets " + chrs); bSuccess=false; return; }
		}
		/*************************************************************
		 * setsMerge: This iterates through SETS (not hits)
		 */
		private boolean setsMerge(Vector <Set> setList) {
			try {
				if (setList.size()==0) return true;
				
				// add singles as coSets to see if they will merge
				int tmpSet = coSetNum + 100;
				for (Hit ht : xHitVec) {
					if (ht.setNum==0 && geneMap1.get(ht.gidx1).cntSet==0 && geneMap2.get(ht.gidx2).cntSet==0) {
						Set sngSet = new Set (1, tmpSet++, bInv, ht.hitnum);
						setList.add(sngSet);
						sngSet.addHitSE(ht);				// computes top and bottom genes of set
						ht.setNum = sngSet.setNum;
					}
				}
				
				// top/bottom genes will be used, but can't sort on them; hitnum will work....
				Collections.sort(setList, new Comparator<Set>() {
					public int compare(Set a1, Set a2) { 
						return a1.hitnum - a2.hitnum; 
					}
				});
				
				setsMergeGene(setList);	if (!bSuccess) return false;
				setsMergeOlap(setList);	if (!bSuccess) return false;
				
				return true;
			}
			catch (Exception e) {ErrorReport.print(e, "Finish sets " + chrs); bSuccess=false; return false; }
		}
		/* 
		 * setsMergeOlap: go through first and last genes of set; use isGoodOlap
		 */
		private boolean setsMergeOlap(Vector <Set> setList) {
		try {
			// Redone each time so can use cntSet to determine ties
			for (Gene g1 : geneMap1.values()) {
				if (g1.isOlap) {
					Olap op=null;	
					if (olapMap1.containsKey(g1.genenum)) op = olapMap1.get(g1.genenum);
					else {
						op = new Olap(1);
						olapMap1.put(g1.genenum, op);
					}
					op.addGene(g1);
				}
			}
			for (Gene g2 : geneMap2.values()) {
				if (g2.isOlap) {
					Olap op=null;	
					if (olapMap2.containsKey(g2.genenum)) op = olapMap2.get(g2.genenum);
					else {
						op = new Olap(2);
						olapMap2.put(g2.genenum, op);
					}
					op.addGene(g2);
				}
			}	
			if (bInv) {
				for (Olap ol : olapMap2.values()) ol.swap();
			}
			
			int row1=0, row2=0, nSets=setList.size(); 
			while (row1 < nSets-1) {
				Set set1 = setList.get(row1);
				if (set1.sz<=0) {
					row1++;
					continue;
				}
				ht1 = set1.hitVec.lastElement();   // not used but for print
				gH1g1 = geneMap1.get(set1.gEidx1); // end gene before next set start gene
				gH1g2 = geneMap2.get(set1.gEidx2); 
				
				row2 = row1+1;
				while (row2 < nSets) { 				
					Set set2 = setList.get(row2);
					if (set2.sz<=0) {
						row2++;
						continue;
					}
					ht2 = set2.hitVec.firstElement(); 
					gH2g1 = geneMap1.get(set2.gSidx1); // end gene before next set start gene
					gH2g2 = geneMap2.get(set2.gSidx2);
						
					if (gH1g1.gidx==gH2g1.gidx || gH1g2.gidx==gH2g2.gidx) { 
						row2++;
						continue;
					}
					if (isGoodOlap()) { 
						set2.merge(set1); // set1 must merge into set2 for rows to work
						cntMergeGene++;
						break; 			 // out of row2, return to row1
					}
					if (Math.abs(d1)>2 && Math.abs(d2)>2) break; 	// out of row2, return to row1
					
					row2++;					// if hits in not in order but the genes are, this finds them
				} // end row2 while
				
				row1++; 
			} // row1 while
			olapMap1.clear(); olapMap2.clear();
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, "Merge " + chrs); bSuccess=false; return false; }
		}
		
		/*
		 * setsMergeGene: go through using first/last hits of set; use isGoodGene
		 */
		private boolean setsMergeGene(Vector <Set> setList) {
		try {
			int row1=0, row2=0, nSets = setList.size();
			
			while (row1 < nSets-1) {
				Set set1 = setList.get(row1);
				if (set1.sz<=0) {
					row1++;
					continue;
				}
				ht1 = set1.hitVec.lastElement(); // last hit of set
				gH1g1 = geneMap1.get(ht1.gidx1); 
				gH1g2 = geneMap2.get(ht1.gidx2); 
				
				row2 = row1+1;
				while (row2 < nSets) { 				
					Set set2 = setList.get(row2);
					if (set2.sz<=0) {
						row2++;
						continue;
					}
					ht2 = set2.hitVec.firstElement(); // first hit of set	
					if (ht1.gidx1==ht2.gidx1 || ht1.gidx2==ht2.gidx2) {
						row2++;
						continue;
					}
		
					gH2g1 = geneMap1.get(ht2.gidx1);
					gH2g2 = geneMap2.get(ht2.gidx2);
					if (isGoodGene("M")) { 
						set2.merge(set1); // set1 must merge into set2 for rows to work
						cntMergeHit++;
						break; 			 // out of row2, return to row1
					}
					if (Math.abs(d1)>2 && Math.abs(d2)>2) break; 	// out of row2, return to row1
					
					row2++;					// if hits in not in order but the genes are, this finds them
				} // end row2 while
				
				row1++; 
			} // row1 while
			
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, "Merge " + chrs); bSuccess=false; return false; }
		}
		
		/***************************************************************/
		private boolean isGoodGene(String msg) { // P (uses hit genes) or M (uses set top/bottom genes)
			d1 = (gH2g1.genenum - gH1g1.genenum); 	// have same genenum if overlap or contained
			d2 = (gH2g2.genenum - gH1g2.genenum);
			t1 = (gH2g1.tnum    - gH1g1.tnum); 		// tnums are sequential
			t2 = (gH2g2.tnum    - gH1g2.tnum);
			b1=b2=false;
			
			if (gH1g1.start <= gH2g1.start) {		// side one - never inverted
				if (d1==1 && t1==1) 		b1=true; 								  // genenum are sequential, no overlap
				else if (d1==0 && t1==1)	b1=true;								  // overlap, same genenum but order is sequential	 
			}
			if (d1==0 && t1==0) b1=isSuffix(gH1g1.tag, gH2g1.tag); // embedded
			
			if (!bInv) {							// side two - not inverted
				if (gH1g2.start <= gH2g2.start) {
					if (d2==1 && t2==1) 	 b2=true;
					else if (d2==0 && t2==1) b2=true; 	
				}
			}
			else {
				if (gH1g2.end >= gH2g2.end) { 	// side two - inverted
					if (d2==-1 && t2==-1) 	  b2=true;  
					else if (d2==0 && t2==-1) b2=true;
				}	
			}
			if (d2==0 && t2==0) b2=isSuffix(gH1g2.tag, gH2g2.tag); 
			
			if (TRACE) {
				if (b1 && b2) prtdt("S" + msg); else prtdt("F"+msg);
			}
			return b1 && b2;
		}
		// this uses top/bottom genes from set; this takes care of problems where there hits end up ordered weird in overlap areas
		private boolean isGoodOlap() { 
			if (!gH1g1.isOlap && !gH1g2.isOlap && !gH2g1.isOlap && !gH2g2.isOlap) return false;
			
			d1 = (gH2g1.genenum - gH1g1.genenum); 	
			d2 = (gH2g2.genenum - gH1g2.genenum);
			t1 = (gH2g1.tnum - gH1g1.tnum); 	
			t2 = (gH2g2.tnum - gH1g2.tnum);
			b1 = b2 = false;
			
			// immediate failures
			if (d1!=0 && d1!=1) return false;
			if (!bInv && d2!=0 && d2!= 1) return false;
			if (bInv  && d2!=0 && d2!=-1) return false;
			
			// Check each hit ends to make sure one is not embedded where the other does not even overlap
			if ((gH1g1.isEmbed && !gH1g2.isOlap) || (!gH1g1.isOlap && gH1g2.isEmbed)) {if (TRACE) prtdt("EM"); return false;}
			if ((gH2g1.isEmbed && !gH2g2.isOlap) || (!gH2g1.isOlap && gH2g2.isEmbed)) {if (TRACE) prtdt("EM"); return false;}
			
			boolean g1=true, g2=true;
			if (t1!=0) {
				if (gH1g1.isOlap) {// low
					Olap ol = olapMap1.get(gH1g1.genenum);
					if (gH1g1.end != ol.gE) g1=false;
				}
				if (gH2g1.isOlap) {//high
					Olap ol = olapMap1.get(gH2g1.genenum);
					if (gH2g1.start != ol.gS) g2=false;
				}
			}
			b1 = g1 && g2;
			
			g1=g2=true; // swaps low/high for bInv, so can do same check
			if (t2!=0) {
				if (gH1g2.isOlap) { // low
					Olap ol = olapMap2.get(gH1g2.genenum);
					if (gH1g2.end != ol.gE) g1=false;
				}
				if (gH2g2.isOlap) {//high
					Olap ol = olapMap2.get(gH2g2.genenum);
					if (gH2g2.start != ol.gS) g2=false;
				}
			}
			b2 = g1 && g2;
			
			if (TRACE) {
				if (b1 && b2) {prtdt("SO"); } else {prtdt("FO");}
			}
			return b1 && b2;
		}
		/*
		 * a-z, a2-z2, a3-z3
		 */
		private boolean isSuffix(String tag1, String tag2) {
			if (!(tag1.contains(".") &&  tag1.contains("."))) return true;
			String s1 = tag1.split("\\.")[1];
			String s2 = tag2.split("\\.")[1];
			
			int i1 = (int) s1.charAt(0);
			int i2 = (int) s2.charAt(0);
			
			if (s1.length()==2)  {
				int num =  Character.getNumericValue(s1.charAt(1))-1;
				int add = ((int)'z'-(int)'a') + num;
				i1 += add;
			}
			if (s2.length()==2)  {
				int num = Character.getNumericValue(s2.charAt(1))-1;
				int add = ((int)'z'-(int)'a') + num;
				i2 += add;
			}
			int d = Math.abs(i2-i1); 
			return d==0 || d==1;
		}
		//////////////////////////////////////////////////////////////////
		private void prtdt(String msg) {
			if (!DEBUG) return;
			if (!TRACE) {
				if (msg.startsWith("F")) return;
				if (msg.endsWith("P")) return;
			}
			int d1x = (d1>100) ? 99 : d1;	d1x = (d1<-100) ? -99 : d1x;
			int t1x = (t1>100) ? 99 : t1;	t1x = (t1<-100) ? -99 : t1x;
			int d2x = (d2>100) ? 99 : d2;	d2x = (d2<-100) ? -99 : d2x;
			int t2x = (d2>100) ? 99 : t2;	t2x = (t2<-100) ? -99 : t2x;
			String h1g1 = (gH1g1.isOlap) ? gH1g1.tag : gH1g1.tag+" ";
			String h1g2 = (gH1g2.isOlap) ? gH1g2.tag : gH1g2.tag+" ";
			String h2g1 = (gH2g1.isOlap) ? gH2g1.tag : gH2g1.tag+" ";
			String h2g2 = (gH2g2.isOlap) ? gH2g2.tag : gH2g2.tag+" ";
			String s1="-", s2="-", s3="-", s4="-";
			if (gH1g1.isEmbed) s1="E"; else if (gH1g1.isOlap) s1= "O";
			if (gH2g1.isEmbed) s2="E"; else if (gH2g1.isOlap) s2= "O";
			if (gH1g2.isEmbed) s3="E"; else if (gH1g2.isOlap) s3= "O";
			if (gH2g2.isEmbed) s4="E"; else if (gH2g2.isOlap) s4= "O";
			
			dprt(String.format("%s #%4d #%4d   %s %3d %3d   %s %3d %3d   Tag %6s %6s   %6s %6s  Tnum %3d %3d  %3d %3d  %s %s %s %s", 
					msg, ht1.hitnum, ht2.hitnum,  Globals.toTF(b1), d1x, t1x, Globals.toTF(b2),  d2x, t2x, 	
					h1g1, h2g1, h1g2, h2g2, gH1g1.tnum, gH2g1.tnum, gH1g2.tnum, gH2g2.tnum, 
					(s1+s2+s3+s4) , drows, chrs, Globals.toTF(bInv)));
		}
	} // End Compute
	////////////////////////////////////////////////////////////////////////
	/*********************************************************
	 * Created while transversing the hit list for adjacent hits */
	private class Set {
		private Set(int sz, int num, boolean bInv, int hitnum) {
			this.sz = sz;
			this.setNum = num; // set when creating, then reset before save
			this.bInv = bInv;
			this.hitnum = hitnum; // from 1st hit
			if (bInv) {
				gS2=0; gE2=Integer.MAX_VALUE;
			}
		}
		private void addHitSE(Hit ht) {
			hitVec.add(ht);
			
			Gene g1 = geneMap1.get(ht.gidx1); // left
			if (g1.start<gS1) {gS1 = g1.start; gSidx1 = g1.gidx;}
			if (g1.end  >gE1) {gE1 = g1.end;   gEidx1 = g1.gidx;}
			
			Gene g2 = geneMap2.get(ht.gidx2); // right
			if (!bInv) {
				if (g2.start<gS2) {gS2 = g2.start; gSidx2 = g2.gidx;}
				if (g2.end  >gE2) {gE2 = g2.end;   gEidx2 = g2.gidx;}
			}
			else {
				if (g2.start>gS2) {gS2 = g2.start; gSidx2 = g2.gidx;}
				if (g2.end  <gE2) {gE2 = g2.end;   gEidx2 = g2.gidx;}
			}
		}
		
		private void merge(Set mst) {
			if (TRACE) mst.prt("Set1:");
			for (Hit ht: mst.hitVec) {
				addHitSE(ht);
				ht.setNum = setNum;
			}
			sz = hitVec.size();
			if (TRACE) prt("Set2:");
			
			mst.setNum = mst.sz = -1;
			mst.hitVec.clear();
		}
		private void prt(String msg) {
			String m1 = (gSidx1==gEidx1) ? "==" : "  ";
			String m2 = (gSidx2==gEidx2) ? "==" : "  ";
			dprt(String.format("%4s %3d. sz %2d  #%4d  R1 %6s %6s  L2 %6s %6s  %s %s", msg, setNum, sz, hitnum,
					geneMap1.get(gSidx1).tag, geneMap1.get(gEidx1).tag, 
					geneMap2.get(gSidx2).tag, geneMap2.get(gEidx2).tag, m1, m2));
		}
		private int sz=0, setNum=0, hitnum=-1; // 1st hitnum for ordering sets
		private Vector <Hit> hitVec = new Vector <Hit> (); // ordered by hitnum
		private boolean bInv = false;	
		
		private int gS1=Integer.MAX_VALUE, gE1=0, gS2=Integer.MAX_VALUE, gE2=0;
		private int gSidx1=0, gEidx1=0, gSidx2=0,  gEidx2=0;
	}
	/*******************************************************************
	 *  Created in Load */
	private class Hit {
		private Hit(int idx, int hitnum, int gidx1, int gidx2, int start2) { 
			this.hidx = idx;
			this.hitnum=hitnum;
			this.gidx1= gidx1;
			this.gidx2= gidx2;
		}
		private int hidx, hitnum;			
		private int gidx1=-1, gidx2=-1;     // best overlaps
		
		private int setNum=0;	// for step2 loop to keep track of what is in a set before making a set
	}
	
	/******************************************************
	 * Created in Load */
	private class Gene {
		private Gene(int idx,  int tnum, String tagx, int start, int end, int genenum, String strand) {
			this.gidx = idx;
			this.tnum = tnum;
			this.tag  = Utilities.getGenenumFromDBtag(tagx);
			this.genenum = genenum;
			this.start = start;
			this.end = end;
			
			isOlap = !tag.endsWith(Globals.DOT);
		}
		private boolean bContains(Gene g) {return start<=g.start && end>=g.end;} 
		
		private String tag="";	
		private int gidx, genenum,  start, end;
		private int tnum;
		
		private int cntSet=0;
		private boolean isOlap=false, isEmbed=false; // CAS589 change parentid back to isEmbed
	} 
	/*********************************************
	 * Created and used in setMergeOlap
	 */
	private class Olap {
		private Olap(int side) {this.side=side;}
		private void addGene(Gene gn) {
			if (gn.start<gS) {
				gS = gn.start; gSidx = gn.gidx;
			}
			else if (gn.start==gS && gn.cntSet>0) { // start the same, use the one that is in a set 
				Gene g = (side==1) ? geneMap1.get(gSidx) : geneMap2.get(gSidx);
				if (g.cntSet==0) {
					gS = gn.start; gSidx = gn.gidx;
				}
			}
			if (gn.end>gE) {
				gE = gn.end; gEidx = gn.gidx;
			}
			else if (gn.end==gE && gn.cntSet>0) {// end the same, use the one that is in a set 
				Gene g = (side==1) ? geneMap1.get(gSidx) : geneMap2.get(gSidx);
				if (g.cntSet==0) {
					gE = gn.end; gEidx = gn.gidx;
				}
			}
		}
		private void swap() { 
			int tmp = gS;	gS = gE;	gE = tmp;
			tmp = gSidx;	gSidx = gEidx;	gEidx = tmp;
		}
		
		private int gS=Integer.MAX_VALUE, gE=0;
		private int gSidx=0, gEidx=0, side=0;
	}
}
