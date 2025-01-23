package backend.anchor2;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;

import backend.Utils;
import database.DBconn2;
import symap.Globals;
import util.ErrorReport;
import util.ProgressDialog;

/**************************************************************
 * Performs all processing for a chrT-chrQ; save results to DB; frees memory
 * The genes and hits are specific to the pair; 
 * 		the genes are copies since each gene can align to more than one chr
 * 		the hits are unique to the pair
 */
public class GrpPair {
	static public final int T=Arg.T, Q=Arg.Q;

	// Input Args
	private int tGrpIdx=-1, qGrpIdx=-1;
	protected AnchorMain2 mainObj;
	protected ProgressDialog plog; // also used directly by GrpPairGx and GrpPairPile
	protected String tChr="", qChr="";
	protected String traceHeader = "";
	
	// Only genes from this tChr-qChr that have hits; built when the MUMmer file is read; used by GrpPairGx
	protected TreeMap <Integer, Gene> tGeneMap = new TreeMap <Integer, Gene> (); // key: geneIdx
	protected TreeMap <Integer, Gene> qGeneMap = new TreeMap <Integer, Gene> (); // key: geneIdx
	
	// Only hits between tChr-qChr;  built from MUMmer file, used by GrpPairGx
	protected ArrayList <Hit> grpHitList= new ArrayList <Hit> (); 

	// GrpPairGx creates gxPairList, turned into clusters, used for saveAnnoHits for Major and Minor
	private GrpPairGx gpGxObj;			
	protected ArrayList <HitPair> gxPairList;	
		
	// GrpPairPile removes piles
	private GrpPairPile gpPileObj;
		
	private boolean bSuccess=true;
	
	protected GrpPair(AnchorMain2 anchorObj, int grpIdxT, String projT, String chrT, 
			                                 int grpIdxQ, String projQ, String chrQ, ProgressDialog plog) {
		this.mainObj = anchorObj;
		
		this.tChr = chrT;
		this.tGrpIdx = grpIdxT;
		
		this.qChr = chrQ;
		this.qGrpIdx = grpIdxQ;
		
		this.plog = plog;
		
		traceHeader = projT + "-" + chrT + " " + projQ + "-" + chrQ + " " + mainObj.fileName;
	}
	
	/*****************************************
	 * The following two are populated during read mummer; Only hits and genes belonging to the chr pair are added
	 */
	protected void addHit(Hit ht) {
		grpHitList.add(ht);
	}
	protected void addGeneHit(int X, Gene gn, Hit ht) {
	try {
		TreeMap <Integer, Gene> geneMap = (X==T) ?  tGeneMap : qGeneMap;
		
		Gene cpGn;
		if (geneMap.containsKey(gn.geneIdx)) cpGn = geneMap.get(gn.geneIdx);
		else {
			cpGn = (Gene) gn.copy();			// needs to be copy because gene can hit multiple chrs (diff hitList)
			geneMap.put(cpGn.geneIdx, cpGn);
		}
		int exonCov = cpGn.addHit(ht); 
		
		ht.addGene(X, cpGn, exonCov);
	}
	catch (Exception e) {ErrorReport.print(e, "GrpPair: clone gene, add hit"); System.exit(-1);}	
	}
	
	/**************************************************************
	 * All processing is performed and saved for this group pair
	 * Each grp-grp pair starts the nBin where the last grp-grp pair left off
	 */
	protected boolean buildClusters() {	
		if (Arg.TRACE) 
			Utils.prtIndentMsgFile(plog, 1, "Compute clusters for T-" + tChr + " and Q-" + qChr);
		
		gpGxObj = new GrpPairGx(this);			// filtered g2,g1,g0 hitpairs;  create gxPairList
		bSuccess   = gpGxObj.run(); 		    if (fChk()) return false; 
		gxPairList = gpGxObj.getPairList();		
		
		gpPileObj = new GrpPairPile(this);		// remove piles
		bSuccess = gpPileObj.run();				if (fChk()) return false;	
	
		HitPair.sortByXstart(Q, gxPairList);	// SORT
		createClustersFromHPR();  				if (fChk()) return  false; 
		saveClusterHits(); 						if (fChk()) return  false;  
		saveAnnoHits();							if (fChk()) return  false;  
		
		prtTrace();
		
		return true;
	}
	
	/**************************************************************
	 * Clusters assignments and gxPairList are 1-to-1 for Major
	 * grpClHitList only contains Major non-filtered
	 * gxPairList contains info to save Minor
	 */
	private void createClustersFromHPR() {
	try {
		Globals.tprt(">> Create clusters for " + traceHeader);
		
		HashMap <Integer, HitPair> bin2clMap = new HashMap <Integer, HitPair>  (gxPairList.size());
		
		int clBin=1;
		for (HitPair hpr : gxPairList) {			
			if (hpr.bin!=0 && hpr.flag==Arg.MAJOR) {
				hpr.cHitNum = hpr.cHitNum = clBin++;		 
				bin2clMap.put(hpr.bin, hpr);
			}	
		} 
		
		// transfer clNum to minor; needed for saveAnnoHits
		for (HitPair hp : gxPairList) {
			if (hp.bin!=0 && hp.flag==Arg.MINOR) {
				if (bin2clMap.containsKey(hp.bin)) { // if not exist, major was filtered 
					HitPair clHit = bin2clMap.get(hp.bin);
					hp.cHitNum = clHit.cHitNum;
				}
			}
		}
	}
	catch (Exception e) {ErrorReport.print(e, "GrpPair createClusters");  bSuccess=false;}	
	}
	
	///////////////////////////////////////////////////////////////////////
	private void saveClusterHits() {
	try {
		DBconn2 tdbc2 = mainObj.tdbc2;
		int pairIdx  = mainObj.mPair.getPairIdx();
		int proj1Idx = mainObj.mProj1.getIdx();
		int proj2Idx = mainObj.mProj2.getIdx();
		
		int cntG2=0, cntG1=0, cntG0=0, countBatch=0, cntSave=0;
		int cntDOeq=0, cntDOne=0, cntWS=0;
		
		tdbc2.executeUpdate("alter table pseudo_hits modify countpct integer");
					
		PreparedStatement ps = tdbc2.prepareStatement("insert into pseudo_hits "	
				+ "(hitnum, pair_idx, proj1_idx, proj2_idx, grp1_idx, grp2_idx,"
				+ "pctid, cvgpct, countpct, score, htype, gene_overlap,"
				+ "annot1_idx, annot2_idx, strand, start1, end1, start2, end2,"
				+ "query_seq, target_seq) "
				+ "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ");
		
		
		for (HitPair hpr : gxPairList) {
			if (hpr.flag!=Arg.MAJOR) continue;
			
			if (!hpr.isOrder) {
				if (hpr.isStEQ) cntDOeq++; else cntDOne++;
			}
			if (!hpr.isRst) cntWS++; // shouldn't happen anymore
			hpr.setSubs();
			
			int i=1;
			ps.setInt(i++, hpr.cHitNum); // this gets reset in AnchorMain, but used to map saveAnnoHits
			ps.setInt(i++, pairIdx);
			ps.setInt(i++, proj1Idx);
			ps.setInt(i++, proj2Idx);
			ps.setInt(i++, qGrpIdx);
			ps.setInt(i++, tGrpIdx);
			
			ps.setInt(i++, hpr.xSumId);					// pctid Avg%Id
			ps.setInt(i++, hpr.xSumSim); 				// cvgpct;  unsigned tiny int; now Avg%Sim
			ps.setInt(i++, hpr.nHits); 					// countpct; unsigned tiny int; now #SubHits
			ps.setInt(i++, hpr.xMaxCov); 				// score; max coverage 
			ps.setString(i++, hpr.htype) ;				// htype EE, EI, etc
			ps.setInt(i++, hpr.getGeneOverlap());		// geneOlap 0,1,2
			
			ps.setInt(i++, hpr.geneIdx[Q]);
			ps.setInt(i++,  hpr.geneIdx[T]); 
			
			ps.setString(i++, hpr.sign); 				// Strand
			ps.setInt(i++, hpr.hpStart[Q]);
			ps.setInt(i++, hpr.hpEnd[Q]);
			ps.setInt(i++, hpr.hpStart[T]);
			ps.setInt(i++, hpr.hpEnd[T]);
			
			ps.setString(i++, hpr.querySubHits);
			ps.setString(i++, hpr.targetSubHits);
			
			ps.addBatch(); 
			countBatch++; cntSave++;
			if (countBatch==1000) {
				countBatch=0;
				ps.executeBatch();
				System.err.print("   " + cntSave + " loaded...\r"); 
			}
			
			if (hpr.geneIdx[Q]>0 && hpr.geneIdx[T]>0) cntG2++;
			else if (hpr.geneIdx[Q]>0 || hpr.geneIdx[T]>0) cntG1++;
			else cntG0++;
		}
		if (countBatch>0) ps.executeBatch();
		ps.close();
		
		mainObj.cntG2 += cntG2;
		mainObj.cntG1 += cntG1;
		mainObj.cntG0 += cntG0;
		mainObj.cntClusters += cntSave;
									
		String msg1 = String.format("%,10d %s-%s Clusters ", cntSave, qChr, tChr); // CAS560 format better, add cntDO and cntWS
		String msg2 = (Globals.INFO || Globals.TRACE) ? String.format("Disorder (EQ,NE) %,4d %,4d ", cntDOeq, cntDOne) : "";
		
		String msg = String.format("%32s   Both genes %,6d   One gene %,6d   No gene %,6d    %s", msg1, cntG2, cntG1, cntG0, msg2);
		Utils.prtIndentMsgFile(plog, 1, msg);
	}
	catch (Exception e) {ErrorReport.print(e, "save to database"); bSuccess=false;}
	}
	
	/************************************
	 *  Save all gene-hit pairs in pseudo_hits_annot 
	 */
	private void saveAnnoHits()  { 
	try {
		if (tGeneMap.size()==0 && qGeneMap.size()==0) return;
		
		if (Arg.TRACE) Utils.prtIndentMsgFile(plog, 1, "Save hits to genes ");
		DBconn2 tdbc2 = mainObj.tdbc2;
		
	// Load hits from database; the clNum was set as the unique hitNum (which will be redone in anchorMain)
		TreeMap <Integer, Integer> clIdxMap = new TreeMap <Integer, Integer> ();
		String st = "SELECT idx, hitnum" +
      			" FROM pseudo_hits WHERE gene_overlap>0 and grp1_idx=" + qGrpIdx + " and grp2_idx=" + tGrpIdx;
		ResultSet rs = tdbc2.executeQuery(st);
		while (rs.next()) clIdxMap.put(rs.getInt(2), rs.getInt(1)); // cHitNum, DB generated idx
		rs.close();
				
		// Save major and minor T and Q; major are sorted before minor 
		// NOTE: if hit X overlaps query geneIdx 1 and 2 and target geneIdx 3 and 4, it will save
		//       X 1 3, X 2 3, but not X 1 4 and X 1 4 because Unique(hitNum, annot_idx); since ALGO1 has
		//       annot2_idx of 0, cannot have Unique(hitNum, annot_idx, annot2_idx)
		PreparedStatement ps = tdbc2.prepareStatement("insert ignore into pseudo_hits_annot "
				+ "(hit_idx, annot_idx, olap, exlap, annot2_idx) values (?,?,?,?,?)");
		
		int cntBatch=0, cntAll=0;
		int [] count = {0,0};
		
		for (HitPair hpr : gxPairList) { 
			if (hpr.bin==0 || hpr.gtype==Arg.type0) continue;
			if (hpr.flag!=Arg.MAJOR && hpr.flag!=Arg.MINOR) continue;
			if (!clIdxMap.containsKey(hpr.cHitNum)) continue; // could have been filtered
			
			int dbhitIdx = clIdxMap.get(hpr.cHitNum);

			if (hpr.qGene!=null && hpr.geneIdx[Q]>0) {
				ps.setInt(1, dbhitIdx); 
				ps.setInt(2, hpr.geneIdx[Q]); 
				ps.setInt(3, intScore(hpr.pGeneHitOlap[Q])); // CAS560 GeneHitCov->GeneHitOlap
				ps.setInt(4, intScore(hpr.pExonHitCov[Q])); 
				ps.setInt(5, hpr.geneIdx[T]);
				ps.addBatch();
				cntBatch++;
				count[Q]++;
				cntAll++;
			}
			if (hpr.tGene!=null && hpr.geneIdx[T]>0) {
				ps.setInt(1, dbhitIdx); 
				ps.setInt(2, hpr.geneIdx[T]); 
				ps.setInt(3, intScore(hpr.pGeneHitOlap[T])); 
				ps.setInt(4, intScore(hpr.pExonHitCov[T])); 
				ps.setInt(5, hpr.geneIdx[Q]);
				ps.addBatch();
				cntBatch++;
				count[T]++;
				cntAll++;
			}
			if (cntBatch>=1000) {
				cntBatch=0;
				ps.executeBatch();
				System.out.print("   " + cntAll + " loaded hit annotations...\r"); 
			}
		}
		if (cntBatch> 0) ps.executeBatch();
		
		if (Arg.TRACE) {
			String msg = String.format("%,10d for %s;  %,d for %s", count[Q], qChr, count[T], tChr );
			Utils.prtIndentMsgFile(plog, 1, msg);
		}
	}
	catch (Exception e) {ErrorReport.print(e, "save annot hits "); bSuccess=false;}
	}
	private int intScore(double score) {
		if (score>99 && score<100.0)   score=99.0;
		else if (score>100.0)          score=100.0;
		else if (score>0 && score<1.0) score=1.0; // CAS548 add >0 check
		return (int) Math.round(score);
	}
	///////////////////////////////////////////////////////////////////////
	private boolean fChk() {
		if (mainObj.failCheck() || !bSuccess) {
			bSuccess=false;
			return true; 
		}
		return false;
	}
	protected void clear() {
		for (Gene gn : tGeneMap.values()) gn.clear();
		tGeneMap.clear();
		
		for (Gene gn : qGeneMap.values()) gn.clear();
		qGeneMap.clear();
		
		for (Hit ht : grpHitList) ht.clear();
		grpHitList.clear();
		
		gpGxObj.clear();
	}
	////////////////////////////////////////////////////////////////////////
	// Appends when multiple groups
	private void prtToFile() {
		try {
			if (!Arg.TRACE) return;
			String chr = "T " + tChr+ ": Q " + qChr;
			
			// Cluster Major and Minor written to DB
			if (mainObj.fhOutCl!=null) {
				mainObj.fhOutCl.println(">>>>Exon Score; Pair for " + traceHeader);
				mainObj.fhOutCl.println("   " + Arg.FLAG_DESC);
				
				if (gpGxObj==null) mainObj.fhOutCl.println("Error ");
				else  {
					mainObj.fhOutCl.println("Save order");
					
					for (HitPair hpr : gxPairList) 
						if (hpr.flag==Arg.MAJOR || hpr.flag==Arg.MINOR)
							mainObj.fhOutCl.println(hpr.toResults(chr, true)+"\n");
					for (HitPair hpr : gxPairList) 
						if (hpr.flag!=Arg.MAJOR && hpr.flag!=Arg.MINOR)
							mainObj.fhOutCl.println(hpr.toResults(chr, true)+"\n");
				}
			}
			// Hit
			if (mainObj.fhOutHit!=null) {
				mainObj.fhOutHit.println("\n>Bin " + toResults() + " " + traceHeader); 
				
				Hit.sortBySignByX(Q, grpHitList);
				mainObj.fhOutHit.println(">>PRT By Sign By X; G0 processed ---------------------------------------");
				Hit last=null;
				for (Hit ht : grpHitList) {
					mainObj.fhOutHit.println(ht.toDiff(last));
					last= ht;
				}
			}
			
			// Gene 
			if (mainObj.fhOutGene!=null) {
				mainObj.fhOutGene.println("\n>T GENEs with hits " + traceHeader );
				
				for (Gene gn : tGeneMap.values()) {
					Hit.sortBySignByX(Q, gn.gHitList);
					String x = gn.toResults();
					if (x!="") mainObj.fhOutGene.println("T" + gn.toResults());
				}
				
				mainObj.fhOutGene.println("\n>Q GENEs with hits " + traceHeader);
				
				for (Gene gn : qGeneMap.values()) {
					Hit.sortBySignByX(T, gn.gHitList);
					String x = gn.toResults();
					if (x!="") mainObj.fhOutGene.println("Q" + gn.toResults());
				}
			}
			// Pile
			if (mainObj.fhOutPile!=null) {
				mainObj.fhOutPile.println(mainObj.fileName);
				gpPileObj.prtToFile(mainObj.fhOutPile, chr);
			}
			
		} catch (Exception e) {ErrorReport.print(e, "Print to file");	}
	}
	private void prtTrace() {
		if (!Arg.TRACE) return;
		prtToFile();
		
		int [] limit = {1,3,6,12,10000000};
		int [] cntG0m = {0,0,0,0,0}; // major only
		int [] cntG1m = {0,0,0,0,0};
		int [] cntG2m = {0,0,0,0,0};
		int [] cntG0t = {0,0,0,0,0}; // totals
		int [] cntG1t = {0,0,0,0,0};
		int [] cntG2t = {0,0,0,0,0};
		int cntMin1=0, cntMin2=0, cntMin0=0, cntF1=0, cntF2=0, cntF0=0;
		int cntIgn1=0, cntIgn2=0, cntIgn0=0, cntP1=0, cntP2=0, cntP0=0, cntUnk1=0, cntUnk2=0, cntUnk0=0;
		int cntGgood1=0, cntGood2=0, cntGood0=0, cntDis1=0, cntDis2=0, cntDis0=0, cntDisNE1=0, cntDisNE2=0, cntDisNE0=0;
		
		for (HitPair hpr : gxPairList) {
			// All pairs
	
			if (hpr.tGene==null && hpr.qGene==null) {
				for (int i=0; i<limit.length; i++) {
					if (hpr.nHits<=limit[i]) {cntG0t[i]++; break;}
				}
			}
			else if (hpr.tGene==null || hpr.qGene==null) {
				for (int i=0; i<limit.length; i++) {
					if (hpr.nHits<=limit[i]) {cntG1t[i]++; break;}
				}
			}
			else {
				for (int i=0; i<limit.length; i++) {
					if (hpr.nHits<=limit[i]) {cntG2t[i]++; break;}
				}
			}
			// break them down
			if (hpr.flag==Arg.IGN) {
				if (hpr.gtype==Arg.type0) 		cntIgn0++;
				else if (hpr.gtype==Arg.type2) 	cntIgn2++;
				else 							cntIgn1++;
			}
			else if (hpr.flag==Arg.PILE) {
				if (hpr.gtype==Arg.type0) 		cntP0++;
				else if (hpr.gtype==Arg.type2) 	cntP2++;
				else 							cntP1++;
			}
			else if (hpr.flag==Arg.FILTER) {
				if (hpr.gtype==Arg.type0) 		cntF0++;
				else if (hpr.gtype==Arg.type2) 	cntF2++;
				else 							cntF1++;
			}
			else if (hpr.flag==Arg.MINOR) {
				if (hpr.gtype==Arg.type0) 		cntMin0++;
				else if (hpr.gtype==Arg.type2) 	cntMin2++;
				else 							cntMin1++;
			}
			else if (hpr.flag==Arg.UNK) {
				if (hpr.gtype==Arg.type0) 		cntUnk0++;
				else if (hpr.gtype==Arg.type2) 	cntUnk2++;
				else 							cntUnk1++;
			}
			else { // major
				if (hpr.note.contains("good+")) {
					if (hpr.gtype==Arg.type0) 		cntGood0++;
					else if (hpr.gtype==Arg.type2) 	cntGood2++;
					else 							cntGgood1++;	
				}
				if (!hpr.isOrder) {
					if (hpr.gtype==Arg.type0) 		{if (!hpr.isStEQ) cntDisNE0++; else cntDis0++;}
					else if (hpr.gtype==Arg.type2) 	{if (!hpr.isStEQ) cntDisNE2++; else cntDis2++;}
					else 							{if (!hpr.isStEQ) cntDisNE1++; else cntDis1++;}
				}
				if (hpr.tGene==null && hpr.qGene==null) {
					for (int i=0; i<limit.length; i++) {
						if (hpr.nHits<=limit[i]) {cntG0m[i]++; break;}
					}
				}
				else if (hpr.tGene==null || hpr.qGene==null) {
					for (int i=0; i<limit.length; i++) {
						if (hpr.nHits<=limit[i]) {cntG1m[i]++; break;}
					}
				}
				else {
					for (int i=0; i<limit.length; i++) {
						if (hpr.nHits<=limit[i]) {cntG2m[i]++; break;}
					}
				}
			}
		}
		
		String msg;
		int cnt=0;
		
		Utils.prtIndentMsgFile(plog, 1, String.format("%10s","Pair Summary             "));
		msg = String.format("%10s       %6s %6s %6s %6s %6s   %6s   %9s  %8s  %7s   %6s  %s", 
				"Major+", "="+limit[0], "<="+limit[1], "<="+limit[2],"<="+limit[3],">"+limit[3], 
				"Minor*", "Filter",  "Ign Unk",  "Pile", "Good+", "Disorder (EQ,NE)");
		Utils.prtIndentMsgFile(plog, 1, msg);
		
		cnt=0;
		for (int i=0; i<limit.length; i++) cnt+=cntG2m[i];
		msg = String.format("%,10d g2    %,6d %,6d %,6d %,6d %,6d   %,6d    %,9d  %,3d %,3d  %,7d  %,6d    %,d %,d", 
				cnt, cntG2m[0], cntG2m[1],  cntG2m[2], cntG2m[3], cntG2m[4],  
				cntMin2,  cntF2, cntIgn2, cntUnk2,  cntP2, cntGood2, cntDis2, cntDisNE2);
		Utils.prtIndentMsgFile(plog, 1, msg);
		
		cnt=0;
		for (int i=0; i<limit.length; i++) cnt+=cntG1m[i];
		msg = String.format("%,10d g1    %,6d %,6d %,6d %,6d %,6d   %,6d    %,9d  %,3d %,3d  %,7d  %,6d    %,d %,d", 
				cnt, cntG1m[0],  cntG1m[1],  cntG1m[2], cntG1m[3], cntG1m[4], 
				cntMin1, cntF1, cntIgn1, cntUnk1,  cntP1, cntGgood1, cntDis1, cntDisNE1);
		Utils.prtIndentMsgFile(plog, 1, msg);
		
		cnt=0;
		for (int i=0; i<limit.length; i++) cnt+=cntG0m[i];
		msg = String.format("%,10d g0    %,6d %,6d %,6d %,6d %,6d   %,6d    %,9d  %,3d %,3d  %,7d  %,6d    %,d %,d", 
				cnt, cntG0m[0],  cntG0m[1],  cntG0m[2], cntG0m[3], cntG0m[4], 
				cntMin0,  cntF0, cntIgn0, cntUnk0, cntP0, cntGood0, cntDis0, cntDisNE0);
		Utils.prtIndentMsgFile(plog, 1, msg);
		
		//////
		if (Arg.TRACE && Globals.DEBUG) {
			msg = String.format("%10s       %7s %7s %7s %7s %7s", 
					"Total", "="+limit[0], "<="+limit[1], "<="+limit[2],"<="+limit[3],">"+limit[3]);
			Globals.tprt(msg);
			
			cnt=0;
			for (int i=0; i<limit.length; i++) cnt+=cntG2t[i];
			msg = String.format("   %,10d g2    %,7d %,7d %,7d %,7d %,7d ", 
					cnt, cntG2t[0],  cntG2t[1],  cntG2t[2], cntG2t[3], cntG2t[4]);
			Globals.tprt(msg);
			
			cnt=0;
			for (int i=0; i<limit.length; i++) cnt+=cntG1t[i];
			msg = String.format("   %,10d g1    %,7d %,7d %,7d %,7d %,7d", 
					cnt, cntG1t[0],  cntG1t[1],  cntG1t[2], cntG1t[3], cntG1t[4]);
			Globals.tprt(msg);
			
			cnt=0;
			for (int i=0; i<limit.length; i++) cnt+=cntG0t[i];
			msg = String.format("   %,10d g0    %,7d %,7d %,7d %,7d %,7d ", 
					cnt, cntG0t[0],  cntG0t[1],  cntG0t[2], cntG0t[3], cntG0t[4]);	
			Globals.tprt(msg);
		}
	}
	protected String toResults() {
		String tg = "    T genes: " + tGeneMap.size();
		String qg = "    Q genes: " + qGeneMap.size();
		return "GP " + tChr + " " + qChr + " " +  " Hits: " + grpHitList.size()  + tg + qg;
	}
	public String toString() {return toResults();}
}
