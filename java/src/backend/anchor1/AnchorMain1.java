package backend.anchor1;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.TreeSet;
import java.util.Vector;

import backend.Constants;
import backend.Utils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Collections;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.File;
import java.lang.Math;

import backend.AnchorMain;
import database.DBconn2;
import symap.Globals;
import symap.manager.Mpair;
import symap.manager.Mproject;
import util.Cancelled;
import util.ErrorCount;
import util.ErrorReport;
import util.ProgressDialog;

/******************************************************
 * Read align files and create anchors 
 * CAS500 Jan2020 rewrote this whole thing, though produces same results
 * (still lots of obsolete/dead code) Removed AnnotAction
 * CAS535 removing still more dead code (some from fpc), more restructuring
 * CAS540 more restructuring, and the following changes:
 * 		Split long genes that have embedded genes, which helps match hits to internal genes; Group.createAnnoFromGenes
 *  	Non-gene AnnotElem do not extend over genes with no hits; 							 Group.createAnnoFromHits1
 *   	Always include geneGene and geneNonGene, but make sure they are responsible hits; 	 HitBin and Hit.useAnnot()
 *      Clusters hits do not have mixed strands, i.e. must be either ++/-- or +-/-; 		 Hit.clusterHits2
 * 		The Hit.matchLen is the Max q&t(summed(subhits)-summed(overlap)) instead of summed(query subhits) 
 * CAS541 change updataPool to dbc2
 */
enum HitStatus  {In, Out, Undecided }; 
enum QueryType  {Query, Target, Either };	
enum HitType 	{GeneGene, GeneNonGene, NonGene} // CAS535 was in HitBin
enum GeneType 	{Gene, NonGene, NA}				 // ditto

public class AnchorMain1 {
	private boolean VB = Constants.VERBOSE;
	private static final int HIT_VEC_INC = 1000;
	private static final int nLOOP = 10000;
	
	private AnchorMain mainObj;
	private DBconn2 dbc2, tdbc2;
	private ProgressDialog plog;
	private int pairIdx = -1;
	private Mpair mp;
	
	private boolean bSuccess=true;
	private String proj1Name, proj2Name;
	private Proj syProj1, syProj2; 
	
	private int mTotalHits=0, mTotalLargeHits=0, mTotalBrokenHits=0;
	private boolean isSelf = false, isNucmer=false;
	
	private HashSet<Hit> filtHitSet = new HashSet <Hit> ();
	private Vector<Hit> diagHits = new Vector <Hit> ();

	public static int nAnnot=0; // CAS543 different filter rules based on if 0,1,2 projects annotated
	
	public AnchorMain1(AnchorMain mainObj) {
		this.mainObj = mainObj;
		this.dbc2 = mainObj.dbc2;
		this.plog = mainObj.plog;
		this.mp = mainObj.mp;
		this.pairIdx = mp.getPairIdx();			
	}
	
	/**************************************************************************/
	public boolean run(Mproject pj1, Mproject pj2, File dh) throws Exception {
	try {
		tdbc2 = new DBconn2("Anchors-"+DBconn2.getNumConn(), dbc2);
		proj1Name = pj1.getDBName(); proj2Name = pj2.getDBName();
		isSelf = proj1Name.equals(proj2Name); 
		
		initStats();
		
	/** execute **/
		loadAnnoBins(pj1, pj2); 	if (!bSuccess) return false; // create initial hitbins from genes
			
		scanAlignFiles(dh);			if (!bSuccess) return false; // create new hitbins from non-gene hits
			
		filterFinalHits(); 			if (!bSuccess) return false;
				
		saveAllResults();			if (!bSuccess) return false;
		
	/** finish **/
		filtHitSet.clear();
		tdbc2.close();
	}
	catch (OutOfMemoryError e) {
		filtHitSet.clear();
		tdbc2.shutdown();
		ErrorReport.prtMem();
		System.out.println("\n\nOut of memory! Edit the symap script and increase the memory limit (mem=).\n\n");
		System.exit(0);
	}		
	return true;
	}

	/***********************************************************************
	 * Load annotations and create initial HitBins
	 * CAS535 moved the group loop from SyProj to here.
	 */
	private void loadAnnoBins(Mproject pj1, Mproject pj2) {
	try {
		long memTime = Utils.getTimeMem();
		
		int topN = mp.getTopN(Mpair.FILE); 
		syProj1 = new Proj(dbc2, plog, pj1, proj1Name, topN, Constants.QUERY);  // loads group
		syProj2 = new Proj(dbc2, plog, pj2, proj2Name, topN, Constants.TARGET); // make new object even if self or filtering gets confused
		
		nAnnot=0;						// static, make sure its 0
		if (syProj1.hasAnnot()) nAnnot++;
		if (syProj2.hasAnnot()) nAnnot++;
		if (nAnnot==0) { 				// CAS540x add check
			plog.msg("Neither project is annotated");
			return;
		}
		
		if (VB) plog.msg("Loading annotations"); else Globals.rprt("Loading annotation");
		Vector <Group> group1Vec = syProj1.getGroups();
		Vector <Group> group2Vec = syProj2.getGroups();
		
		if (syProj1.hasAnnot()) {// CAS540x check
			int tot1=0;
			for (Group grp : group1Vec) tot1 += grp.createAnnoFromGenes(tdbc2);
			if (VB) Utils.prtNumMsg(plog, tot1, proj1Name + " genes "); 
			else    Globals.rprt(String.format("%,d %s genes", tot1, proj1Name));
		}
		if (syProj2.hasAnnot()) {// CAS540x check
			int tot2=0;
			for (Group grp : group2Vec) tot2 += grp.createAnnoFromGenes(tdbc2);	
			if (!isSelf) {
				if (VB) Utils.prtNumMsg(plog, tot2, proj2Name + " genes "); 
				else    Globals.rprt(String.format("%,d %s genes", tot2, proj2Name));
			}
		}
		
		failCheck();
		if (Constants.PRT_STATS) Utils.prtTimeMemUsage("Load Annot bins", memTime);
	}
	catch (Exception e) {ErrorReport.print(e, "load annotation"); bSuccess=false;}
	}

	/*******************************************************************
	 */
	private boolean scanAlignFiles(File dh) throws Exception {
	try {
	/** first scan: process all aligned files in directory */
		plog.msg("Scan files to create candidate genes from hits"); 	
		long memTime = Utils.getTimeMem();
		
		int nHitsScanned = 0, nFile=0;
		TreeSet<String> skipList = new TreeSet<String>();
		File[] fs = dh.listFiles();
		
		int nLoop = (isSelf) ? 2 : 1; // process notSelf first to get same results as v4.2
	
		for (int i=0; i<nLoop; i++) {
			boolean skipSelf = (i==0) ? true : false; 

			for (File f : fs) {
				if (!f.isFile()) continue;
				
				String fName = f.getName();
				if (!fName.endsWith(Constants.mumSuffix))continue;
				if (isSelf) {
					if ( skipSelf &&  fName.startsWith(Constants.selfPrefix)) continue;
					if (!skipSelf && !fName.startsWith(Constants.selfPrefix)) continue;
				}
				nFile++;
				
				int nHits = scanFile1AnnoBins(f, syProj1, syProj2, skipSelf); 
						
				if (nHits == 0) skipList.add(fName);
				else 			nHitsScanned += nHits;
				
				if (failCheck()) return false;
			}
		}
		if (nHitsScanned == 0) { // CAS561 add more info 
			plog.msg("No readable anchors were found - MUMmer may not have run correctly.");
			return rtError(" Or missing loaded sequences (try Algo2, which allows missing sequences).");
		}
		if (mTotalHits == 0)   return rtError("No good anchors were found");
		Globals.rclear();
		if (nFile>1 || !VB) 			Utils.prtNumMsg(plog, nHitsScanned, "Total scanned hits");
		if (nHitsScanned!=mTotalHits) 	Utils.prtNumMsg(plog, mTotalHits, "Total accepted hits");
		if (mTotalLargeHits > 0 && VB)  Utils.prtNumMsg(plog, mTotalLargeHits, "Large hits (> " + Group.FSplitLen + ")  "
					 															+ mTotalBrokenHits + " Split "); 
		if (Constants.PRT_STATS) Utils.prtTimeMemUsage(plog, "Complete scan candidate genes", memTime);
		
	/** Second scan - to cluster and filter **/	
		plog.msg("Scan files to cluster and filter hits");
		memTime = Utils.getTimeMem();
		
		nHitsScanned = nFile = 0;			
		
		for (int i=0; i<nLoop; i++) {
			boolean bSelf = (i==0) ? true : false; 
			
			for (File f : fs) {
				if (!f.isFile()) continue;
				
				String fName = f.getName();
				if (!fName.endsWith(Constants.mumSuffix))	continue;
				if (skipList.contains(fName)) continue;
				
				if (isSelf) {
					if (i==0 &&  fName.startsWith(Constants.selfPrefix)) continue;
					if (i==1 && !fName.startsWith(Constants.selfPrefix)) continue;
				}
				nFile++;	
				int nHits = scanFile2ClusterFilter(f, syProj1, syProj2, bSelf);
				nHitsScanned += nHits;	
				
				if (failCheck()) return false;
			}
		}
		Globals.rclear();
		if (nFile>1 || !VB) 
			Utils.prtNumMsg(plog, nHitsScanned, "Total cluster hits   ");

		if (Constants.PRT_STATS) { 
			Utils.prtTimeMemUsage(plog, "Complete scan cluster", memTime);
			syProj1.printBinStats();  
			syProj2.printBinStats(); 
			BinStats.dumpStats(plog); // CAS560 was not printing
			plog.msgToFile("Complete stats"); 
		}
		return true;
	}
	catch (Exception e) {ErrorReport.print(e, "Reading seq anchors"); bSuccess=false; return false;}
	}
	
	/*************************************************************
	 * first time through, create the predicted genes from non-gene hits
	 */
	private int scanFile1AnnoBins(File mFile, Proj p1, Proj p2, boolean skipSelf) throws Exception {
		Vector<Hit> rawHits = new Vector<Hit>(HIT_VEC_INC,HIT_VEC_INC);
	try {	
		if (!VB) Globals.rprt(String.format("Scan %s",  Utils.fileFromPath(mFile.toString())));
		
		String line, fileType="PROMER";
		int numErrors = 0, hitNum = 0;
		int skip1=0, skip2=0;
		
	/** Read file and build rawHits **/
		BufferedReader fh = new BufferedReader(new FileReader(mFile));
		
		while (fh.ready()) {
			line = fh.readLine().trim();
			if (line.length() == 0) continue;
			if (!Character.isDigit(line.charAt(0))) {// CAS515 add in order to allow headers
				if (line.startsWith("NUCMER")) {
					isNucmer=true;
					fileType = "NUCMER";
				}
				continue; 
			}
			hitNum++; 
			Hit hit = new Hit();
			hit.origHits = 1;
		
			boolean success = scanNextMummerHit(line,hit);
			if (!success) {
				if (numErrors < 5){
					plog.msg("  Error on line " + hitNum + " in " + mFile.getName()); // Will go to symap.log and terminal
					numErrors++;
					continue;
				}
				else {fh.close(); Globals.die("Too many errors in file " + mFile.getName());}
			}
			hit.queryHits.grpIdx = p1.grpIdxFromQuery(hit.queryHits.name);
			if (hit.queryHits.grpIdx == -1) {
				plog.msg(proj2Name + ": Query not found: " + hit.queryHits.name + " --skipping file");
				fh.close();
				return 0;
			}
			hit.targetHits.grpIdx = p2.grpIdxFromQuery(hit.targetHits.name);
			if (hit.targetHits.grpIdx == -1) {
				plog.msg(proj1Name + ": Target not found for: " + hit.targetHits.name + " --skipping file");
				fh.close();
				return 0;
			}
			
			if (skipSelf && hit.queryHits.grpIdx==hit.targetHits.grpIdx) {
				skip1++;
				continue; // (run twice) run the chr x itself separately
			}
			if (hit.queryHits.grpIdx == hit.targetHits.grpIdx && hit.queryHits.start < hit.targetHits.start) {
				skip2++;
				continue; // mirror these later
			}
				
			mTotalHits++;
			if (Group.bSplitGene && hit.maxLength() > Group.FSplitLen) {// CAS540 add splitgene check
				Globals.tprt(" split hit of size " + hit.maxLength());
				Vector<Hit> brokenHits = Hit.splitMUMmerHit(hit);
				mTotalLargeHits++;
				mTotalBrokenHits += brokenHits.size();
				rawHits.addAll(brokenHits);
			}
			else {
				rawHits.add(hit);
			}
			if (failCheck()) {fh.close(); return 0;}
		}
		fh.close();
		
		if (Constants.PRT_STATS) {
			Utils.prtNumMsgNZx(skip1, "Skip1 - Self with same group (is run separately)");
			Utils.prtNumMsgNZx(skip2, "Skip2 - Same group   with start1<start2 (mirror later)");
		}
		// CAS561 there is a lot of output if many chromosome, hence, only Verbose prints them
		String msg= String.format("%,10d scanned %s %s", hitNum, fileType, Utils.fileFromPath(mFile.toString()));
		if (VB) plog.msg(msg);  else    Globals.rprt(msg);
		
		if (hitNum==0 || mTotalHits==0) return 0;
		
	/** Scan1 processing: all hits get added to each grp; create non-genes (predicted); hit has start<end **/
		for (Hit h : rawHits) {			
			Group grp1 = p1.getGrpByIdx(h.queryHits.grpIdx);
			Group grp2 = p2.getGrpByIdx(h.targetHits.grpIdx);
			
			grp1.createAnnoFromHits1(h.queryHits.start, h.queryHits.end, h.isRev);
			grp2.createAnnoFromHits1(h.targetHits.start, h.targetHits.end, h.isRev);
		}
		rawHits.clear();
		return hitNum;
	}
	catch (Exception e) {
		rawHits.clear();
		ErrorReport.print(e, "ScanFile1: Reading file for hits"); 
		bSuccess=false; 
		return 0;}
	}
	/*********************************************************************
	 * 2nd time through - do the clustering
	 * this is about the same as scanFile1 except for no checks, diagonals, and final processing
	 */
	private int scanFile2ClusterFilter(File mFile, Proj p1, Proj p2, boolean skipSelf) throws Exception {
		Vector<Hit> rawHits = new Vector<Hit>(HIT_VEC_INC,HIT_VEC_INC);
	try {
		if (!VB) Globals.rprt(String.format("Cluster %s",  Utils.fileFromPath(mFile.toString())));
		
		BufferedReader fh = new BufferedReader(new FileReader(mFile));
		String line;
		int errCnt=0;
		
	/** Read file and build rawHits **/
		int totalLargeHits = 0, totalBrokenHits = 0;
		while (fh.ready()) {
			line = fh.readLine().trim();
			
			if (line.length() == 0) continue;
			if (!Character.isDigit(line.charAt(0))) continue; // CAS515 allow header lines
			
			Hit hit = new Hit();	
			hit.origHits = 1;
			
			boolean success = scanNextMummerHit(line,hit);
			if (!success) { // should have already been caught in ScanFile1
				plog.msg("  Error in file " + Utils.fileFromPath(mFile.toString()));
				errCnt++;
				if (errCnt>=5) Utils.die("Too many errors");
				continue;
			}
			
			hit.queryHits.grpIdx  = p1.grpIdxFromQuery(hit.queryHits.name);
			hit.targetHits.grpIdx = p2.grpIdxFromQuery(hit.targetHits.name);
			
			if (skipSelf && hit.queryHits.grpIdx==hit.targetHits.grpIdx) continue;
			if (hit.queryHits.grpIdx == hit.targetHits.grpIdx && hit.queryHits.start < hit.targetHits.start) continue; 

			// The only section different from scanFile1, which makes a big difference for self-synteny
			if (hit.isDiagHit()) { // Diagonal hits are handle separately
				hit.status = HitStatus.In; 
				diagHits.add(hit);
				continue;
			}
			
			if (hit.maxLength() > Group.FSplitLen){
				Vector<Hit> brokenHits = Hit.splitMUMmerHit(hit); // catches and prints error
				if (brokenHits==null) {bSuccess=false; fh.close(); return 0;}
				totalLargeHits++;
				totalBrokenHits += brokenHits.size();
				rawHits.addAll(brokenHits);
			}
			else {
				rawHits.add(hit);
			}	
			if (failCheck()) {fh.close(); return 0;}
		}
		fh.close();
		if (rawHits.size()==0) return 0;
		
	/** Scan2 processing ***/
		BinStats.incStat("RawDiagHits", diagHits.size());
		BinStats.incStat("RawHits",rawHits.size());
		BinStats.incStat("LargeHits",totalLargeHits);
		if (totalLargeHits>0) BinStats.incStat("BrokenHits",totalBrokenHits);
	
		Vector<Hit> clustHits = clusterHits2( rawHits ); if (!bSuccess) return 0;
		rawHits.clear();
		Collections.sort(clustHits);
		
		filterClusterHits2(clustHits, p1, p2);	     			if (!bSuccess) return 0;
		
		String msg= String.format("%,10d clustered %s", clustHits.size(), Utils.fileFromPath(mFile.toString()));
		if (VB) Utils.prt(plog, msg); else Globals.rprt(msg);
	
		return clustHits.size();
	}
	catch (Exception e) {
		rawHits.clear();
		ErrorReport.print(e, "scanFile2: Reading file for hits "); 
		bSuccess=false; 
		return 0;}
	}	
	
	/*********************************************************************
	 * Cluster the mummer hits into larger hits using the annotations, including the "non-genes". [clusterGeneHits]
	 * So our possible hit set will now be a subset of annotations x annotations, and the
	 * actual length of the hits will be the sum of the subhits that went into them.
	 * CAS540 it was mixing isRev and !isRev; fixed in Hit.clusterHits
	 */
	private Vector<Hit> clusterHits2(Vector<Hit> inHits) throws Exception {
		Vector<Hit> outHits = new Vector<Hit>(HIT_VEC_INC, HIT_VEC_INC);
		
		// all use key = qAnno.mID + "_" + tAnno.mID;
		HashMap<String,Vector<Hit>> pairMap =   new HashMap<String,Vector<Hit>>(); // all hits to the q-t pair
		HashMap<String,AnnotElem>   key2qAnno = new HashMap<String,AnnotElem>();   
		HashMap<String,AnnotElem>   key2tAnno = new HashMap<String,AnnotElem>();
		HashMap<String,HitType>     pairTypes = new HashMap<String,HitType>(); 
						
	try {
		// Find all hits for each qAnno x tAnno; every hit has a gene or non-gene AnnotElem for q&t
		for (Hit hit : inHits)  {		
			Group grp1 = syProj1.getGrpByIdx(hit.queryHits.grpIdx);
			Group grp2 = syProj2.getGrpByIdx(hit.targetHits.grpIdx);
			
			AnnotElem qAnno = grp1.getBestOlapAnno(hit.queryHits.start, hit.queryHits.end);   // priority to gene annotElem
			AnnotElem tAnno = grp2.getBestOlapAnno(hit.targetHits.start, hit.targetHits.end); 

			if (qAnno == null) {rtError("missing query annot!  grp:" + grp1.idStr() + " start:" + hit.queryHits.start + " end:" + hit.queryHits.end); return null;}	
			if (tAnno == null) {rtError("missing target annot! grp:" + grp2.idStr() + " start:" + hit.targetHits.start + " end:" + hit.targetHits.end); return null;}	
			
			String key = qAnno.mID + "_" + tAnno.mID;
			
			if (!pairMap.containsKey(key)) {
				pairMap.put(key, new Vector<Hit>());
				key2qAnno.put(key, qAnno);
				key2tAnno.put(key, tAnno);
				
				if (!qAnno.isGene() && !tAnno.isGene()) {
					pairTypes.put(key, HitType.NonGene);		BinStats.incStat("NonGeneClusters", 1);	
				}
				else if (qAnno.isGene() && tAnno.isGene()) {
					pairTypes.put(key, HitType.GeneGene);		BinStats.incStat("GeneClusters", 1);
				}
				else {
					pairTypes.put(key, HitType.GeneNonGene);	BinStats.incStat("GeneNonGeneClusters", 1);	
				}						
			}
			pairMap.get(key).add(hit);					
		}
		
		// Merge the pair hits into a clustered hit
		for (String key : pairMap.keySet()) {			
			Vector<Hit> pairHits = pairMap.get(key);
			AnnotElem qAnno = key2qAnno.get(key);
			AnnotElem tAnno = key2tAnno.get(key);
			
			outHits.addAll(Hit.clusterHits2( pairHits, pairTypes.get(key), qAnno, tAnno));		
		}
		BinStats.incStat("TotalClusters", outHits.size());
		
		return outHits;
	}
	catch (Exception e) {
		outHits.clear(); pairMap.clear(); key2qAnno.clear();key2tAnno.clear();  pairTypes.clear();
		ErrorReport.print(e, "cluster gene hits"); 
		bSuccess=false; 
		return null;}
	}
	/********************************************************
	 * Add hits to their topN bins, applying the topN criterion immediately when possible to reduce memory
	 * Called from scanFile2; hits get transfered to respective project [preFilterHit2]
	 */
	private void filterClusterHits2(Vector<Hit> clustHits, Proj p1, Proj p2) throws Exception {
	try {
		// Bin the annotated/candidate genes [syProj1.collectPGInfo]; CAS540 moved here
		Vector <Group> grp1Vec = syProj1.getGroups();
		for (Group grp : grp1Vec) grp.createHitBinFromAnno2(syProj1.getName());
		
		Vector <Group> grp2Vec = syProj2.getGroups();
		for (Group grp : grp2Vec) grp.createHitBinFromAnno2(syProj2.getName());
			
		Vector<Hit> theseDiag = new Vector<Hit>();
		
		for (Hit hit : clustHits) {				
			if (hit.isDiagHit()) { // keep it but do not use in further processing	
				hit.status = HitStatus.In; 
				theseDiag.add(hit); 
				continue;
			}
			
			// Add hit to group HitBin if passes filter
			Group grp1 = p1.getGrpByIdx(hit.queryHits.grpIdx);
			grp1.filterAddToHitBin2(hit, hit.queryHits.start, hit.queryHits.end);   
			 
			Group grp2 = p2.getGrpByIdx(hit.targetHits.grpIdx);
			grp2.filterAddToHitBin2(hit, hit.targetHits.start, hit.targetHits.end);	
		}
		
		if (theseDiag.size() > 0) { // self only	
			Hit.mergeOlapDiagHits(theseDiag);
			diagHits.addAll(theseDiag);
			
			BinStats.incStat("DiagHits", theseDiag.size());
			BinStats.incStat("MergedDiagHits", diagHits.size());
		}	
	}
	catch (Exception e) {ErrorReport.print(e, "prefilter hits"); bSuccess=false;}
	}
	/*****************************************************************/
	private void filterFinalHits() {
	try {
		if (VB) plog.msg("Filter hits"); else Globals.rprt("Filter hits");
		
		// final TopN filtering, and collect all the hits that pass on at least one side into the set "hits"
		Vector <Group> group1Vec = syProj1.getGroups();
		for (Group grp1 : group1Vec) grp1.filterFinalAddHits(filtHitSet); // adds to filtHitSet vector
		
		Vector <Group> group2Vec = syProj2.getGroups();
		for (Group grp2 : group2Vec) grp2.filterFinalAddHits(filtHitSet);
		
		int nQueryIn = 0, nTargetIn = 0, nBothIn=0;
		for (Hit h : filtHitSet) {	
			if (h.targetHits.status == HitStatus.In) nTargetIn++;
			if (h.queryHits.status ==  HitStatus.In) nQueryIn++;
			
			if (h.queryHits.status == HitStatus.In && h.targetHits.status == HitStatus.In) {
				h.status = HitStatus.In;
				nBothIn++;
			}
		}
		if (VB) Utils.prtNumMsg(plog, nQueryIn,  "for " +  proj1Name );
		if (VB) Utils.prtNumMsg(plog, nTargetIn, "for " + proj2Name );
		Utils.prtNumMsg(plog, nBothIn, "Filtered hits to save");
		
		filtHitSet.addAll(diagHits); // diagHits only for self
		
		if (!Constants.PRT_STATS) return;
		
		//*** -s only *****//
		for (Hit hit : filtHitSet) { 
			if (hit.status == HitStatus.In) {			
				BinStats.incHist("TopNHist1Accept", hit.binsize1);
				BinStats.incHist("TopNHist2Accept", hit.binsize2);
				if (hit.mHT==null) {
					BinStats.incStat("unkFinalHits", 1);
					BinStats.incStat("unkFinalOrigHits", hit.origHits);
				}
				else {
					BinStats.incStat(hit.mHT.toString() + "FinalHits", 1);
					BinStats.incStat(hit.mHT.toString() + "FinalOrigHits", hit.origHits);
				}
			}
		}
		BinStats.incStat("TopNFinalKeep", nBothIn);	
	}
	catch (Exception e) {ErrorReport.print(e, "filter hits"); bSuccess=false;}
	}
	/*********************************************
	 * 0        1       2        3       4       5       6		7
	 * [S1]    [E1]    [S2]    	[E2]    [LEN 1] [LEN 2] [% IDY] [% SIM] [% STP] [LEN R] [LEN Q]     [FRM]   [TAGS]
     *15491   15814  20452060 20451737   324     324    61.11   78.70   1.39    73840631  37257345  2   -3  chr1    chr3
	 * NUCMER does not have %SIM or %STP
	 * The file is pair.proj1idx_to_pair.proj2idx, where proj1idx.name<proj2idx.name
	 * MUMmer S1/E1 is from proj2idx, which is referred to by SyMAP as target
	 *        S2/E2 is from proj1idx, which is referred to by SyMAP as query
	 */
	private boolean scanNextMummerHit(String line, Hit hit) {
		String[] fs = line.split("\\s+");
		if (fs.length < 13) { // CAS561 add more info
			plog.msg("*** Missing values, only " + fs.length + " must be >=13                       ");
			plog.msg("  Line: " + line);
			return false;
		}	
		int poffset = (fs.length > 13 ? 2 : 0); // nucmer vs. promer
		
		int tstart 	= Integer.parseInt(fs[0]);
		int tend 	= Integer.parseInt(fs[1]);
		int qstart 	= Integer.parseInt(fs[2]);
		int qend 	= Integer.parseInt(fs[3]);
		int match 	= Math.max(Integer.parseInt(fs[4]), Integer.parseInt(fs[5])); // CAS540 LEN2=>MAX 
		int pctid 	= Math.round(Float.parseFloat(fs[6]));
		int pctsim 	= (isNucmer) ? 0 :  Math.round(Float.parseFloat(fs[7]));
		if (pctsim>110) pctsim=0; // in case mummer file does not have Nucmer header;
		String targetChr 	= fs[11 + poffset];
		String queryChr 	= fs[12 + poffset];
				
		String strand = (qstart <= qend ? "+" : "-") + "/" + (tstart <= tend ? "+" : "-"); 	

		hit.setHit(queryChr, targetChr, qstart, qend, tstart, tend, match, pctid, pctsim, strand); // CAS540x moved assignments to setHit
		
		return true;
	}	
	/*******************************************************************
	 * XXX 
	 */
	private void saveAllResults()  throws Exception {
	try {
		if (Constants.VERBOSE) plog.msg("Save results"); else Globals.rprt("Save results");
		long memTime = Utils.getTimeMem();
		
		Vector <Hit> vecHits = new Vector <Hit> (filtHitSet.size());
		for (Hit h : filtHitSet) vecHits.add(h);
		Hit.sortByTarget(vecHits);	// CAS515 they end up out of any logical order; order by start
		
		saveFilterHits(vecHits); 		if (!bSuccess) return;
		
		if (isSelf) addMirroredHits();	if (!bSuccess) return;
		
		saveAnnoHits();			 		if (!bSuccess) return;
		
		if (Constants.PRT_STATS) Utils.prtMsgTimeFile(plog, "Complete save", memTime);
	}
	catch (Exception e) {ErrorReport.print(e, "save results"); throw e;}
	}
	/************************************************
	 * If set GLOBAL innodb_flush_log_at_trx_commit=0, these two saveFilterHits are the same speed,
	 * else, saveFilterHits1 is faster. Hits1 uses a bulk load whereas Hits uses PreparedStatement
	 */
	private void saveFilterHits(Vector <Hit> vecHits) throws Exception { 
		try {
			if (VB) plog.msg("   Save filtered hits"); else Globals.rprt("Save filtered hits");
			int numLoaded=0, countBatch=0;
			tdbc2.executeUpdate("alter table pseudo_hits modify countpct integer");
						
			PreparedStatement ps = tdbc2.prepareStatement("insert into pseudo_hits "	// CAS544 put in correct order
					+ "(pair_idx, proj1_idx, proj2_idx, grp1_idx, grp2_idx,"
					+ "pctid, cvgpct, countpct, score, htype, gene_overlap,"
					+ "annot1_idx, annot2_idx, strand, start1, end1, start2, end2,"
					+ "query_seq, target_seq) "
					+ "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ");
			
			for (Hit hit : vecHits) {
				if (hit.status != HitStatus.In) continue;
				
				String htype="g0"; // CAS546 add to show on Query
				int geneOlap = 0;
				if (hit.mHT == HitType.GeneGene)			{geneOlap = 2;	htype="g2";}
				else if (hit.mHT == HitType.GeneNonGene)	{geneOlap = 1;	htype="g1";}
				
				int i=1;
				ps.setInt(i++, pairIdx);
				ps.setInt(i++, syProj1.getIdx());
				ps.setInt(i++, syProj2.getIdx());
				ps.setInt(i++, hit.queryHits.grpIdx);
				ps.setInt(i++, hit.targetHits.grpIdx);
				
				ps.setInt(i++, hit.pctid);							// pctid Avg%Id
				ps.setInt(i++, hit.pctsim); 				 		//CAS515 cvgpct;   unsigned tiny int; now Avg%Sim
				ps.setInt(i++, (hit.queryHits.subHits.length/2)); 	//CAS515 countpct; unsigned tiny int; now #SubHits
				ps.setInt(i++, hit.matchLen); 						// score
				ps.setString(i++, htype) ;							// CAS543 change from evalue to htype; CAS546 to string
				ps.setInt(i++, geneOlap);							// 0,1,2
				
				ps.setInt(i++, hit.annotIdx1);
				ps.setInt(i++, hit.annotIdx2);
				ps.setString(i++, hit.strand);
				ps.setInt(i++, hit.queryHits.start);
				ps.setInt(i++, hit.queryHits.end);
				ps.setInt(i++, hit.targetHits.start);
				ps.setInt(i++, hit.targetHits.end);
				
				ps.setString(i++, Utils.intArrayToBlockStr(hit.queryHits.subHits));
				ps.setString(i++, Utils.intArrayToBlockStr(hit.targetHits.subHits));
				
				ps.addBatch(); 
				countBatch++; numLoaded++;
				if (countBatch==nLOOP) {
					if (failCheck()) return;
					countBatch=0;
					ps.executeBatch();
					Globals.rprt(numLoaded + " loaded"); 
				}
			}
			if (countBatch>0) ps.executeBatch();
			ps.close();
		}
		catch (Exception e) {ErrorReport.print(e, "save annot hits"); bSuccess=false;}
	}
	
	private void addMirroredHits() throws Exception {
	try { // Create the reflected hits for self-alignment cases; CAS520 add hitnum, CAS543 evalue->htype and rearranged
		// NOTE that grp1_idx is swapped with grp2_idx, etc; and refidx->idx; order of columns matches schema
		tdbc2.executeUpdate("insert into pseudo_hits (select 0, "
		+ "hitnum,pair_idx,proj1_idx,proj2_idx, grp2_idx, grp1_idx, pctid, "
		+ "cvgpct, countpct, score, htype, gene_overlap,annot2_idx, annot1_idx, "
		+ "strand, start2, end2, start1, end1, target_seq, query_seq, runnum, runsize, idx "
		+ " from pseudo_hits where pair_idx=" + pairIdx + " and refidx=0 and start1 != start2 and end1 != end2)"	
		);
		tdbc2.executeUpdate("update pseudo_hits as ph1, pseudo_hits as ph2 set ph1.refidx=ph2.idx where ph1.idx=ph2.refidx " +
				" and ph1.refidx=0 and ph2.refidx != 0 and ph1.pair_idx=" + pairIdx + " and ph2.pair_idx=" + pairIdx);
	}
	catch (Exception e) {
		try {// pre-v543 - the order and fields must be exactly like it is in the database
			tdbc2.executeUpdate("insert into pseudo_hits (select 0, "
			+ "hitnum, pair_idx,proj1_idx,proj2_idx,grp2_idx,grp1_idx,htype,pctid," 
			+ "score,strand,start2,end2,start1,end1,target_seq,query_seq,gene_overlap,"
			+ "countpct,cvgpct,idx,annot2_idx,annot1_idx,runnum, runsize " + 
			" from pseudo_hits where pair_idx=" + pairIdx + " and refidx=0 and start1 != start2 and end1 != end2)"	
			);
			tdbc2.executeUpdate("update pseudo_hits as ph1, pseudo_hits as ph2 set ph1.refidx=ph2.idx where ph1.idx=ph2.refidx " +
					" and ph1.refidx=0 and ph2.refidx != 0 and ph1.pair_idx=" + pairIdx + " and ph2.pair_idx=" + pairIdx);
		}
		catch (Exception e2) {
			ErrorReport.print(e2, "add mirror hits"); 
			bSuccess=false;
		}
	}
	}
	
	/****************************************************
	 * CAS535 changed from using Updatedbc2 via Group to PrepareStatement here [hitAnnotations]
	 * This is slower than the old method if innodb_flush_log_at_trx_commit=1; =0, the same
	 * show variables like 'max_prepared_stmt_count'; 16386 is default
	 * CAS540 add gene_olap, otherwise, get hits that span genes (which are never used)
	 */
	private void saveAnnoHits()  { // [hitAnnotations]
	String key="";
	try {
		if (VB) plog.msg("   Save hit to gene "); else Globals.rprt("   Save hit to gene ");
		
		/* Load hits from database, getting their new idx;  */
		Vector <Hit> vecHits = new Vector <Hit> (filtHitSet.size()); 
		String st = "SELECT idx, start1, end1, start2, end2, strand, grp1_idx, grp2_idx, annot1_idx, annot2_idx" +
      			" FROM pseudo_hits WHERE gene_overlap>0 and pair_idx=" + pairIdx;
		ResultSet rs = tdbc2.executeQuery(st);
		while (rs.next()) {
			Hit h = new Hit();
			h.idx 				= rs.getInt(1);
			h.queryHits.start 	= rs.getInt(2);
			h.queryHits.end 	= rs.getInt(3);
			h.targetHits.start	= rs.getInt(4);
			h.targetHits.end 	= rs.getInt(5);
			h.strand 			= rs.getString(6);
			h.queryHits.grpIdx 	= rs.getInt(7);					
			h.targetHits.grpIdx = rs.getInt(8);	
			h.annotIdx1			= rs.getInt(9);
			h.annotIdx2			= rs.getInt(10);
			h.isRev = (h.strand.contains("-") && h.strand.contains("+"));
			vecHits.add(h);
		}
		rs.close();
		if (failCheck()) return;
		
	/* save the query annotation hits, which correspond to syProj1 */ /* on self-synteny, get dups;  */
		PreparedStatement ps = tdbc2.prepareStatement("insert ignore into pseudo_hits_annot "
				+ "(hit_idx, annot_idx, olap, exlap, annot2_idx) values (?,?,?,?,?)");
		
		int count=0, cntHit=0, cntBatch=0;
		int [][] vals;
	
		for (Hit ht : vecHits) {// FIRST LOOP for Q
			Group grp = syProj1.getGrpByIdx(ht.queryHits.grpIdx);
			vals = grp.getAnnoHitOlapForSave(ht, ht.queryHits.start, ht.queryHits.end); 
			if (vals.length>0) cntHit++;
			
			for (int i=0; i<vals.length; i++) { // loop is repeated below
				if (vals[i][1]==0) continue; // not a gene
					
				ps.setInt(1,vals[i][0]); // hitid
				ps.setInt(2,vals[i][1]); // annot_idx
				ps.setInt(3,vals[i][2]); // CAS548 %olap
				ps.setInt(4, 0); 		 // CAS548 does not know exon overlap; -1 indicates no value
				ps.setInt(5,vals[i][3]); // CAS548 annot2
				ps.addBatch();
				count++; cntBatch++;
				if (cntBatch==nLOOP) {
					if (failCheck()) {Globals.eprt("Failure during load hit"); return;}
					cntBatch=0;
					ps.executeBatch();
					Globals.rprt(count + " loaded hit annotations"); 
				}
			}	
		}
		if (cntBatch> 0) ps.executeBatch();
		if (cntHit>0) {
			String m = String.format(" (%,d) for %s", count, proj1Name); // CAS560 add comma
			if (VB) Utils.prtNumMsg(plog, cntHit, m+"          "); else Globals.rprt(cntHit + m);
		}
		if (failCheck()) return;
		
	/* save the target annotation hits */
		cntHit=cntBatch=0;
		for (Hit ht : vecHits) { // SECOND LOOP for T
			Group grp = syProj2.getGrpByIdx(ht.targetHits.grpIdx);
			
			vals = grp.getAnnoHitOlapForSave(ht, ht.targetHits.start, ht.targetHits.end);
			if (vals.length>0) cntHit++;
			
			for (int i=0; i<vals.length; i++) {
				if (vals[i][1]==0) continue;
				
				ps.setInt(1,vals[i][0]); // hit_idx
				ps.setInt(2,vals[i][1]); // annot_idx
				ps.setInt(3,vals[i][2]); // olap
				ps.setInt(4, 0); 		 // exlap
				ps.setInt(5, vals[i][3]); //annot2_idx
				
				ps.addBatch();
				count++; cntBatch++;
				if (cntBatch==nLOOP) {
					if (failCheck()) return;
					cntBatch=0;
					ps.executeBatch();
					Globals.rprt("   " + count + " loaded hit annotations"); 
				}
			}	
		}
		if (cntBatch>0) ps.executeBatch();
		ps.close();	
		if (!isSelf && cntHit>0) {
			String m = String.format(" (%,d) for %s", count,proj2Name); // CAS560 add comma
			if (VB) Utils.prtNumMsg(plog, cntHit, m+"                 "); else Globals.rprt(cntHit + m);
		}
		if (failCheck()) return;
	}
	catch (Exception e) {ErrorReport.print(e, "save annot hits " + key); bSuccess=false;}
	}
	
	/*************************************************
	 * CAS500 this was in run(); some of this is obsolete; CAS535 removed addGeneParams,
	 */
	private void initStats() {
		try {
		if (!Constants.PRT_STATS) return;
		
		BinStats.initStats(); 
		
		if (!Globals.TRACE) return; // -s -tt for histogram
		BinStats.initHist("TopNHist1" + GeneType.Gene, 3,6,10,25,50,100);
		BinStats.initHist("TopNHist1" + GeneType.NonGene, 3,6,10,25,50,100);
		BinStats.initHist("TopNHist1" + GeneType.NA, 3,6,10,25,50,100);
		BinStats.initHist("TopNHist1Accept", 3,6,10,25,50,100);
		BinStats.initHist("TopNHistTotal1", 3,6,10,25,50,100);
		BinStats.initHist("TopNHist2" + GeneType.Gene, 3,6,10,25,50,100);
		BinStats.initHist("TopNHist2" + GeneType.NonGene, 3,6,10,25,50,100);
		BinStats.initHist("TopNHist2" + GeneType.NA, 3,6,10,25,50,100);
		BinStats.initHist("TopNHist2Accept", 3,6,10,25,50,100);
		BinStats.initHist("TopNHistTotal2", 3,6,10,25,50,100);	
	}
	catch (Exception e) {ErrorReport.print("adding properties for Anchors");}
	}
	private boolean failCheck() {
		if (Cancelled.isCancelled() || mainObj.bInterrupt) {
			if (mainObj.bInterrupt) plog.msg("Process was interrupted");
			else plog.msg("Process was cancelled");
			bSuccess=false;
			return true; 
		}
		return false;
	}
	private boolean rtError(String msg) {
		plog.msg(msg);
		ErrorCount.inc();
		bSuccess=false;
		return false;
	}
}
