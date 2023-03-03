package backend;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.TreeSet;
import java.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Collections;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.File;
import java.lang.Math;

import symap.manager.Mpair;
import symap.manager.Mproject;
import util.Cancelled;
import util.ErrorCount;
import util.ErrorReport;
import util.ProgressDialog;
import util.Utilities;

/******************************************************
 * Read align files and create anchors 
 * CAS500 Jan2020 rewrote this whole thing, though produces same results
 * (still lots of obsolete/dead code) Removed AnnotAction
 * CAS535 removing still more dead code (some from fpc), more restructuring
 */
enum HitStatus  {In, Out, Undecided }; 
enum QueryType  {Query, Target, Either };	
enum HitType 	{GeneGene, GeneNonGene, NonGene} // CAS535 was in HitBin
enum GeneType 	{Gene, NonGene, NA}				 // ditto

public class AnchorsMain {
	private static final int maxHitLength = 10000;
	private static final int HIT_VEC_INC = 1000;
	private static final int nLOOP = 10000;
	private boolean bNewLoad=false; 
	
	private UpdatePool pool;
	private ProgressDialog plog;
	
	private boolean bInterrupt = false;
	private int mTotalHits=0, mTotalLargeHits=0, mTotalBrokenHits=0;
	private boolean isSelf = false, isNucmer=false;
	private String resultDir=null;
	
	private HashSet<Hit> filteredHits = new HashSet <Hit> ();
	private Vector<Hit> diagHits = new Vector <Hit> ();

	private int pairIdx = -1;
	private SyProj syProj1, syProj2;  // assigned in load annotations
	private Mpair mp;
	private boolean bSuccess=true;
	private String proj1Name, proj2Name;
	
	public AnchorsMain(UpdatePool pool, ProgressDialog log,  Mpair mp) {	
		this.pool = pool;
		this.plog = log;
		this.mp = mp;
		this.pairIdx = mp.getPairIdx();
		if (Constants.PRT_STATS) System.out.println("Use new hit save: " + bNewLoad);
	}
	
	public boolean run(Mproject pj1, Mproject pj2) throws Exception {
	try {
	/** init **/
		long startTime = System.currentTimeMillis();
		
		proj1Name = pj1.getDBName(); proj2Name = pj2.getDBName();
		isSelf = proj1Name.equals(proj2Name); 
		plog.msg("Finding good hits for " + proj1Name + " and " + proj2Name);
		
		resultDir = Constants.getNameResultsDir(proj1Name, proj2Name); // e.g. data/seq_results/p1_to_p2
		if ( !Utilities.pathExists(resultDir) ) {
			plog.msg("Cannot find pair directory " + resultDir);
			ErrorCount.inc();
			return false;
		}
		initStats();
		
	/** execute **/
		
		loadAnnoHitBins(pj1, pj2); 	if (!bSuccess) return false; // create initial hitbins from genes
			
		scanAlignFiles();			if (!bSuccess) return false; // create new hitbins from non-gene hits
			
		filterHits(); 				if (!bSuccess) return false;
				
		saveAllResults();			if (!bSuccess) return false;
		
	/** finish **/
		filteredHits.clear();
		
		BinStats.uploadStats(pool, pairIdx, syProj1.idx, syProj2.idx);
		
		long modDirDate = new File(resultDir).lastModified(); // CAS532 add for Pair Summary with 'use existing files'
		mp.setPairProp("pair_align_date", Utils.getDateStr(modDirDate));
		
		Utils.timeDoneMsg(plog, startTime, "Find hits");
	}
	catch (OutOfMemoryError e) {
		System.out.println("\n\nOut of memory! Edit the symap launch script and increase the memory limit ($maxmem).\n\n");
		System.exit(0);
	}		
	return true;
	}
	public void interrupt() {bInterrupt = true;	}
	
	/***********************************************************************
	 * Load annotations and create initial HitBins
	 * CAS535 moved the group loop from SyProj to here.
	 */
	private void loadAnnoHitBins(Mproject pj1, Mproject pj2) {
	try {
		plog.msg("Loading annotations");
		int topN = mp.getTopN(Mpair.FILE), totalGenes=0;
		
		// SyProj1
		syProj1 = new SyProj(pool, plog, pj1, proj1Name, topN, QueryType.Query);
		
		Vector <Group> group1Vec = syProj1.getGroups();
		for (Group grp : group1Vec) {
			totalGenes += grp.createAnnoHitBins(pool);
		}
		Utils.prtNumMsg(plog, totalGenes, proj1Name + " genes");
		
		// SyProj2: make new object even if it is a self alignment - or filtering gets confused
		syProj2 = new SyProj(pool, plog, pj2, proj2Name, topN, QueryType.Target);
		
		totalGenes=0;
		Vector <Group> group2Vec = syProj2.getGroups();
		for (Group grp : group2Vec) {
			totalGenes += grp.createAnnoHitBins(pool);
		}
		Utils.prtNumMsg(plog, totalGenes,  proj2Name + " genes");	
		
		failCheck();
	}
	catch (Exception e) {ErrorReport.print(e, "load annotation"); bSuccess=false;}
	}

	/*******************************************************************
	 */
	private boolean scanAlignFiles() {
	try {
		String alignDir = resultDir + Constants.alignDir;
		
		File dh = new File(alignDir);
		if (!dh.exists() || !dh.isDirectory()) return rtError("/align directory does not exist " + alignDir);
		plog.msg("Alignment files in " + alignDir);
		
	/** first scan: process all aligned files in directory */
		plog.msg("Scan files to create cluster hits"); 	
		long startTime = System.currentTimeMillis();
		
		int nHitsScanned = 0;
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
				
				int nHits = scanFile1HitBins(f, syProj1, syProj2, skipSelf); 
						
				if (nHits == 0) skipList.add(fName);
				else 			nHitsScanned += nHits;
				
				if (failCheck()) return false;
			}
		}
		
		if (nHitsScanned == 0) return rtError("No readable anchors were found - MUMmer probably did not run correctly.");
		if (mTotalHits == 0)   return rtError("No good anchors were found");
		
		if (fs.length>1) Utils.prtNumMsg(plog, nHitsScanned, "Total scanned hits");
		if (nHitsScanned!=mTotalHits) Utils.prtNumMsg(plog, mTotalHits, "Total accepted hits");
		if (mTotalLargeHits > 0) { Utils.prtNumMsg(plog, mTotalLargeHits, "Large hits (> " 
					+ maxHitLength + ")  " + mTotalBrokenHits + " Split "); // CAS505 changed from 'Broken'
		}
		
		/* Finish scan1 processing: Bin the gene and nongene per group [syProj1.collectPGInfo] */
		Vector <Group> grp1Vec = syProj1.getGroups();
		for (Group g : grp1Vec) g.collectHitBinInfo(syProj1.getName());
		
		Vector <Group> grp2Vec = syProj2.getGroups();
		for (Group g : grp2Vec) g.collectHitBinInfo(syProj2.getName());
		
		if (Constants.PRT_STATS) Utils.prtMsgTimeFile(plog, startTime, "Complete Scan cluster");
					
	/** Second scan - to cluster **/	
		plog.msg("Scan files to load hits");
		startTime = System.currentTimeMillis();
		
		nHitsScanned = 0;			
		
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
						
				int nHits = scanFile2Cluster(f, syProj1, syProj2, bSelf);
				nHitsScanned += nHits;	
				
				if (failCheck()) return false;
			}
		}
		if (fs.length>1) Utils.prtNumMsg(plog, nHitsScanned, "Total load hits   ");
		if (Constants.PRT_STATS) Utils.prtMsgTimeFile(plog, startTime, "Complete Scan load");
		
		if (Constants.PRT_STATS) { 
			syProj1.printBinStats();  
			syProj2.printBinStats(); 
		}
		
		return true;
	}
	catch (Exception e) {ErrorReport.print(e, "Reading seq anchors"); bSuccess=false; return false;}
	}
	
	/*************************************************************
	 * first time through, create the predicted genes from non-gene hits
	 */
	private int scanFile1HitBins(File mFile, SyProj p1, SyProj p2, boolean skipSelf) throws Exception {
	try {
		Vector<Hit> rawHits = new Vector<Hit>(HIT_VEC_INC,HIT_VEC_INC);
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
					plog.msg("Parse error on line " + hitNum + " in " + mFile.getName());
					numErrors++;
					continue;
				}
				else {fh.close(); rtiError("Too many errors in file");}
			}
			hit.query.grpIdx = p1.grpIdxFromQuery(hit.query.name);
			if (hit.query.grpIdx == -1) {
				plog.msg(proj2Name + ": Query not found: " + hit.query.name + " --skipping file");
				fh.close();
				return 0;
			}
			hit.target.grpIdx = p2.grpIdxFromQuery(hit.target.name);
			if (hit.target.grpIdx == -1) {
				plog.msg(proj1Name + ": Target not found for: " + hit.target.name + " --skipping file");
				fh.close();
				return 0;
			}
			
			if (skipSelf && hit.query.grpIdx==hit.target.grpIdx) {
				skip1++;
				continue; // (run twice) run the chr x itself separately
			}
			if (hit.query.grpIdx == hit.target.grpIdx && hit.query.start < hit.target.start) {
				skip2++;
				continue; // mirror these later
			}
				
			mTotalHits++;
			if (hit.maxLength() > maxHitLength) {
				Vector<Hit> brokenHits = breakHit(hit);
				mTotalLargeHits++;
				mTotalBrokenHits+=brokenHits.size();
				rawHits.addAll(brokenHits);
			}
			else {
				hit.orderEnds();
				rawHits.add(hit);
			}
			if (failCheck()) {fh.close(); return 0;}
		}
		fh.close();
		
		if (Constants.PRT_STATS) {
			Utils.prtNumMsgNZx(skip1, "Skip1 - Self with same group (is run separately)");
			Utils.prtNumMsgNZx(skip2, "Skip2 - Same group   with start1<start2 (mirror later)");
		}
		
	/** Scan1 processing: create predicted genes **/
		for (Hit h : rawHits) {			
			Group grp1 = p1.getGrpByIdx(h.query.grpIdx);
			Group grp2 = p2.getGrpByIdx(h.target.grpIdx);
			
			grp1.createNonAnnoHitBins(h.query.start, h.query.end);
			grp2.createNonAnnoHitBins(h.target.start, h.target.end);
		}
		
		Utils.prtNumMsg(plog, hitNum, "scanned " +  fileType + " " + Utils.fileFromPath(mFile.toString()));
	
		return hitNum;
	}
	catch (Exception e) {ErrorReport.print(e, "Reading anchors: scanFile1"); bSuccess=false; return 0;}
	}
	/*********************************************************************
	 * 2nd time through - do the clustering
	 * this is about the same as scanFile1 except for no checks, diagonals, and final processing
	 */
	private int scanFile2Cluster(File mFile, SyProj p1, SyProj p2, boolean skipSelf) throws Exception {
	try {
		BufferedReader fh = new BufferedReader(new FileReader(mFile));
		Vector<Hit> rawHits = new Vector<Hit>(HIT_VEC_INC,HIT_VEC_INC);
		String line;
		
	/** Read file and build rawHits **/
		int totalLargeHits = 0, totalBrokenHits = 0;
		while (fh.ready()) {
			line = fh.readLine().trim();
			
			if (line.length() == 0) continue;
			if (!Character.isDigit(line.charAt(0))) continue; // CAS515 allow header lines
			
			Hit hit = new Hit();	// CAS515 make Object after reject lines
			hit.origHits = 1;
			
			boolean success = scanNextMummerHit(line,hit);
			if (!success) Utils.die("SyMAP error - scanNextHummerHit for scan2");
			
			hit.query.grpIdx  = p1.grpIdxFromQuery(hit.query.name);
			hit.target.grpIdx = p2.grpIdxFromQuery(hit.target.name);
			
			if (skipSelf && hit.query.grpIdx==hit.target.grpIdx) continue;
			if (hit.query.grpIdx == hit.target.grpIdx && hit.query.start < hit.target.start) continue; 

			// The only section different from scanFile1, which makes a big difference for self-synteny
			if (hit.isDiagHit()) { // Diagonal hits are handle separately
				hit.status = HitStatus.In; 
				diagHits.add(hit);
				continue;
			}
			
			if (hit.maxLength() > maxHitLength){
				Vector<Hit> brokenHits = breakHit(hit); if (!success) {fh.close(); return 0;}
				totalLargeHits++;
				totalBrokenHits += brokenHits.size();
				rawHits.addAll(brokenHits);
			}
			else {
				hit.orderEnds();
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
		BinStats.incStat("BrokenHits",totalBrokenHits);
		
		rawHits = clusterGeneHits2( rawHits );if (!bSuccess) return 0;
		Collections.sort(rawHits);
		preFilterHits2(rawHits, p1, p2);	  if (!bSuccess) return 0;
		
		Utils.prtNumMsg(plog, rawHits.size(), "load " + Utils.fileFromPath(mFile.toString()));
		
		return rawHits.size();
	}
	catch (Exception e) {ErrorReport.print(e, "Reading anchors: scanFile2"); bSuccess=false; return 0;}
	}	
	/*******************************************************************
	 * Break the hit into pieces, taking care to not leave a tiny leftover hit, and for
	 * cases where query and target are different lengths (maybe not possible with mummer).
	 * Note, hits have been previously fixed so start < end.
	 */
	private Vector<Hit> breakHit(Hit hit) {
	try {
		Vector<Hit> ret = new Vector<Hit>();
	
		int minLeftover = maxHitLength/10;
		
		int qlen = hit.query.length();
		int tlen = hit.target.length();
		
		int qleft = qlen%maxHitLength;
		int tleft = tlen%maxHitLength;		
		
		int qparts = (qleft >= minLeftover ?  (1 + qlen/maxHitLength) : (qlen/maxHitLength));
		int tparts = (tleft >= minLeftover ?  (1 + tlen/maxHitLength) : (tlen/maxHitLength));
		
		int parts = Math.min(qparts,tparts);
		
		if (parts == 1) {
			hit.orderEnds();
			hit.idx=6;
			ret.add(hit);
		}
		else if (!hit.reversed()) { // build (parts-1) hits of fixed size, and put the rest into the final hit
			
			hit.orderEnds(); // this is a bit hacky but in fact the query and target can BOTH be reversed,
							 // in which case reversed() returns false
			for (int i = 1; i < parts; i++) {
				int qstart = 	hit.query.start + maxHitLength*(i-1);
				int qend = 		qstart + maxHitLength - 1;
				int tstart = 	hit.target.start + maxHitLength*(i-1);
				int tend = 		tstart + maxHitLength - 1;
				Hit h = new Hit(hit, qstart, qend, tstart, tend, 1);
				ret.add(h);
			}
			int qstart = 		hit.query.start + maxHitLength*(parts-1);
			int qend = 			hit.query.end;
			int tstart = 		hit.target.start + maxHitLength*(parts-1);
			int tend = 			hit.target.end;
			
			Hit h = new Hit(hit, qstart, qend, tstart, tend, 2);
			ret.add(h);
		}
		else if (hit.reversed()) {//  for forward through the query, backward through target
		
			hit.orderEnds(); // this is the last time we need to know whether it started out reversed
			
			for (int i = 1; i < parts; i++) {
				int qstart = 	hit.query.start + maxHitLength*(i-1);
				int qend =		qstart + maxHitLength - 1;
				int tend = 		hit.target.end - maxHitLength*(i-1);
				int tstart = 	tend - maxHitLength + 1;	
				Hit h = new Hit(hit, qstart, qend, tstart, tend, 3);
				ret.add(h);
			}
			int qstart = 		hit.query.start + maxHitLength*(parts-1);
			int qend = 			hit.query.end;
			int tend = 			hit.target.end - maxHitLength*(parts-1);
			int tstart = 		hit.target.start;
			Hit h = new Hit(hit, qstart, qend, tstart, tend, 4);
			ret.add(h);		
		}
		return ret;
	}
	catch (Exception e) {ErrorReport.print(e, "Reading anchors: scanFile2"); bSuccess=false; return null;}
	}
	
	/*********************************************************************
	 * Cluster the mummer hits into larger hits using the annotations, including the "predicted genes".
	 * So our possible hit set will now be a subset of annotations x annotations, and the
	 * actual length of the hits will be the sum of the subhits that went into them.
	 */
	private Vector<Hit> clusterGeneHits2(Vector<Hit> inHits) {
	try {
		Vector<Hit> outHits = new Vector<Hit>(HIT_VEC_INC, HIT_VEC_INC);
		Vector<Hit> bigHits = new Vector<Hit>();
		
		HashMap<String,Vector<Hit>> hitBins =    new HashMap<String,Vector<Hit>>();
		HashMap<String,AnnotElem>   key2qannot = new HashMap<String,AnnotElem>();
		HashMap<String,AnnotElem>   key2tannot = new HashMap<String,AnnotElem>();
		HashMap<String,HitType>     binTypes =   new HashMap<String,HitType>(); // hack, for stats
						
		// First put the hits into bins labeled by annotation1 x annotation2
		// Note that a given annotation can only go to one group
		for (Hit hit : inHits)  {		
			Group grp1 = syProj1.getGrpByIdx(hit.query.grpIdx);
			Group grp2 = syProj2.getGrpByIdx(hit.target.grpIdx);
			
			AnnotElem qAnnot = grp1.getMostOverlappingAnnot(hit.query.start, hit.query.end);
			AnnotElem tAnnot = grp2.getMostOverlappingAnnot(hit.target.start, hit.target.end);

			if (qAnnot == null) {rtError("missing query annot! grp:" + grp1.idStr() + " start:" + hit.query.start + " end:" + hit.query.end); return null;}	
			if (tAnnot == null) {rtError("missing target annot! grp:" + grp2.idStr() + " start:" + hit.target.start + " end:" + hit.target.end); return null;}	
			
			String key = qAnnot.mID + "_" + tAnnot.mID;
			
			if (!hitBins.containsKey(key)) {
				hitBins.put(key, new Vector<Hit>());
				key2qannot.put(key,qAnnot);
				key2tannot.put(key,tAnnot);
				if (!qAnnot.isGene() && !tAnnot.isGene()) {
					BinStats.incStat("NonGeneClusters", 1);	
					binTypes.put(key,HitType.NonGene);
				}
				else if (qAnnot.isGene() && tAnnot.isGene()) {
					BinStats.incStat("GeneClusters", 1);
					binTypes.put(key,HitType.GeneGene);
				}
				else {
					BinStats.incStat("GeneNonGeneClusters", 1);	
					binTypes.put(key,HitType.GeneNonGene);
				}						
			}
			hitBins.get(key).add(hit);
			if (!qAnnot.isGene() && !tAnnot.isGene()) 	BinStats.incStat("NonGeneHits", 1);	
			else if (qAnnot.isGene() && tAnnot.isGene())BinStats.incStat("GeneHits", 1);	
			else 										BinStats.incStat("GeneNonGeneHits", 1);						
		}
		
		// Merge the binned hits
		for (String key : hitBins.keySet()) {			
			Vector<Hit> binHits = hitBins.get(key);	
			Hit h = Hit.clusterHits( binHits ,binTypes.get(key));
			
			AnnotElem qAnnot = key2qannot.get(key);
			if (qAnnot.isGene())
				h.annotIdx1 = qAnnot.idx;	
			
			AnnotElem tAnnot = key2tannot.get(key);
			if (tAnnot.isGene())
				h.annotIdx2 = tAnnot.idx;	
	
			outHits.add(h);			
		}
		
		BinStats.incStat("TotalClusters", outHits.size());
		
		if (bigHits.size() > 0) {
			BinStats.incStat("BigHits", bigHits.size());
			Hit.mergeOverlappingHits(bigHits);
			BinStats.incStat("MergedBigHits", bigHits.size());
			outHits.addAll(bigHits);
		}
		return outHits;
	}
	catch (Exception e) {ErrorReport.print(e, "cluster gene hits"); bSuccess=false; return null;}
	}
	/********************************************************
	 * Add hits to their topN bins, 
	 * applying the topN criterion immediately when possible to reduce memory
	 * Called from scanFile2; hits get transfered to respective project 
	 */
	private void preFilterHits2(Vector<Hit> rawHits, SyProj p1, SyProj p2) throws Exception {
	try {
		Vector<Hit> theseDiag = new Vector<Hit>();
		
		for (Hit hit : rawHits) {				
			if (hit.isDiagHit()) { // keep it but do not use in further processing	
				hit.status = HitStatus.In; 
				theseDiag.add(hit); 
				BinStats.incStat("DiagHits", 1);
				continue;
			}
			
			// Add to the appropriate TopN filter HitBin, unless it's already clear that it will not pass the filter
			Group grp1 = p1.getGrpByIdx(hit.query.grpIdx);
			grp1.checkAddToHitBin2(hit,hit.query);		// bins stored by group (e.g. chr)
			
			Group grp2 = p2.getGrpByIdx(hit.target.grpIdx);
			grp2.checkAddToHitBin2(hit,hit.target);		// bins stored by group (e.g. chr)
		}
		
		if (theseDiag.size() > 0) { // self only		
			Hit.mergeOverlappingHits(theseDiag);
	
			diagHits.addAll(theseDiag);
			BinStats.incStat("MergedDiagHits", diagHits.size());
		}	
	}
	catch (Exception e) {ErrorReport.print(e, "prefilter hits"); bSuccess=false;}
	}
	/*****************************************************************/
	private void filterHits() {
	try {
		plog.msg("Filter hits");
		
		// final TopN filtering, and collect all the hits that pass on at least one side into the set "hits"
		Vector <Group> group1Vec = syProj1.getGroups();
		for (Group grp1 : group1Vec) grp1.filterHits(filteredHits);
		
		Vector <Group> group2Vec = syProj2.getGroups();
		for (Group grp2 : group2Vec) grp2.filterHits(filteredHits);
		
		int nQueryIn = 0, nTargetIn = 0, nBothIn=0;
		for (Hit h : filteredHits) {	
			if (h.target.status == HitStatus.In) nTargetIn++;
			if (h.query.status ==  HitStatus.In) nQueryIn++;
			
			if (h.query.status == HitStatus.In && h.target.status == HitStatus.In)
				h.status = HitStatus.In;
		}
		Utils.prtNumMsg(plog, nQueryIn,  "for " +  proj1Name );
		Utils.prtNumMsg(plog, nTargetIn, "for " + proj2Name );
		
		filteredHits.addAll(diagHits); // diagHits only for self
		
		for (Hit hit : filteredHits) { 
			if (hit.status == HitStatus.In) {			
				BinStats.incHist("TopNHist1Accept", hit.binsize1);
				BinStats.incHist("TopNHist2Accept", hit.binsize2);
				if (hit.mHT != null) { // !null for seq-seq
					BinStats.incStat(hit.mHT.toString() + "FinalHits", 1);
					BinStats.incStat(hit.mHT.toString() + "FinalOrigHits", hit.origHits);
				}
				nBothIn++;
			}
		}
		Utils.prtNumMsg(plog, nBothIn, "Total hits to load");
		BinStats.incStat("TopNFinalKeep", nBothIn);	
	}
	catch (Exception e) {ErrorReport.print(e, "filter hits"); bSuccess=false;}
	}
	/*********************************************
	 * 0        1       2        3       4       5       6
	 * [S1]    [E1]    [S2]    	[E2]    [LEN 1] [LEN 2] [% IDY] [% SIM] [% STP] [LEN R] [LEN Q]     [FRM]   [TAGS]
     *15491   15814  20452060 20451737   324     324    61.11   78.70   1.39    73840631  37257345  2   -3  chr1    chr3
	 * NUCMER does not have %SIM or %STP
	 * The file is pair.proj1idx_to_pair.proj2idx, where proj1idx.name<proj2idx.name
	 * MUMmer S1/E1 is from proj2idx, which is referred to by SyMAP as target
	 *        S2/E2 is from proj1idx, which is referred to by SyMAP as query
	 */
	private boolean scanNextMummerHit(String line, Hit hit) {
		String[] fs = line.split("\\s+");
		if (fs.length < 13) return false;
		
		int poffset = (fs.length > 13 ? 2 : 0); // nucmer vs. promer
		
		int tstart 	= Integer.parseInt(fs[0]);
		int tend 	= Integer.parseInt(fs[1]);
		int qstart 	= Integer.parseInt(fs[2]);
		int qend 	= Integer.parseInt(fs[3]);
		int match 	= Integer.parseInt(fs[5]); // query length 
		int pctid 	= Math.round(Float.parseFloat(fs[6]));
		int pctsim 	= (isNucmer) ? 0 :  Math.round(Float.parseFloat(fs[7]));
		if (pctsim>110) pctsim=0; // in case mummer file does not have Nucmer header;
		String target 	= fs[11 + poffset];
		String query 	= fs[12 + poffset];
				
		String strand = (qstart <= qend ? "+" : "-") + "/" + (tstart <= tend ? "+" : "-"); 	

		hit.query.name  = query.intern();	
		hit.target.name = target.intern();
		hit.query.start = qstart;
		hit.query.end = qend;
		hit.target.start = tstart;
		hit.target.end = tend;
		hit.matchLen = match;
		hit.pctid = pctid;
		hit.pctsim = pctsim;
		hit.strand = strand.intern(); // reduce hit memory footprint
		
		return true;
	}	
	/*******************************************************************
	 * XXX Save CAS535 made all saves internal, quite using UpdatePoll
	 */
	private void saveAllResults()  throws Exception {
	try {
		long startTime = System.currentTimeMillis();
		
		Vector <Hit> vecHits = new Vector <Hit> (filteredHits.size());
		for (Hit h : filteredHits) vecHits.add(h);
		Hit.sortByTarget(vecHits);	// CAS515 they end up out of any logical order; order by start
		
		saveFilterHits(vecHits); 		if (!bSuccess) return;
		
		if (isSelf) addMirroredHits();	if (!bSuccess) return;
		
		if (bNewLoad) saveAnnoHits();			 		
		else          saveAnnoHits1(syProj1, syProj2);
		if (!bSuccess) return;
		
		if (Constants.PRT_STATS) Utils.prtMsgTimeFile(plog, startTime, "Complete save");
	}
	catch (Exception e) {ErrorReport.print(e, "save results"); throw e;}
	}
	/************************************************
	 * If set GLOBAL innodb_flush_log_at_trx_commit=0, these two saveFilterHits are the same speed,
	 * else, saveFilterHits1 is faster. Hits1 uses a bulk load whereas Hits uses PreparedStatement
	 */
	private void saveFilterHits(Vector <Hit> vecHits) throws Exception { 
		try {
			plog.msg("Save filter hits");
			
			int numLoaded=0, countBatch=0;
			pool.executeUpdate("alter table pseudo_hits modify countpct integer");
			
			PreparedStatement ps = pool.prepareStatement("insert into pseudo_hits "
					+ "(pair_idx, proj1_idx, proj2_idx, grp1_idx, grp2_idx,"
					+ "evalue, pctid, score, strand,"
					+ "start1, end1, start2, end2, query_seq, target_seq,"
					+ "gene_overlap, countpct, cvgpct,annot1_idx,annot2_idx) "
					+ "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ");
			
			for (Hit hit : vecHits) {
				if (hit.status != HitStatus.In) continue;
				
				int geneOlap = 0;
				if (hit.mHT == HitType.GeneGene)			geneOlap = 2;	
				else if (hit.mHT == HitType.GeneNonGene)	geneOlap = 1;	
				
				int i=1;
				ps.setInt(i++, pairIdx);
				ps.setInt(i++, syProj1.getIdx());
				ps.setInt(i++, syProj2.getIdx());
				ps.setInt(i++, hit.query.grpIdx);
				ps.setInt(i++, hit.target.grpIdx);
				ps.setInt(i++, 0) ;							// evalue, referred to but never has a value
				ps.setInt(i++, hit.pctid);
				ps.setInt(i++, hit.matchLen); 				// saved as score, but never used
				ps.setString(i++, hit.strand);
				ps.setInt(i++, hit.query.start);
				ps.setInt(i++, hit.query.end);
				ps.setInt(i++, hit.target.start);
				ps.setInt(i++, hit.target.end);
				ps.setString(i++, Utils.intArrayToBlockStr(hit.query.subHits));
				ps.setString(i++, Utils.intArrayToBlockStr(hit.target.subHits));
				ps.setInt(i++, geneOlap);
				ps.setInt(i++, (hit.query.subHits.length/2)); //CAS515 countpct;  unsigned tiny int; now #Merged Hits
				ps.setInt(i++, hit.pctsim); 				 // CAS515 cvgpct was 0; unsigned tiny int; now Avg%Sim
				ps.setInt(i++, hit.annotIdx1);
				ps.setInt(i++, hit.annotIdx2);
				
				ps.addBatch(); 
				countBatch++; numLoaded++;
				if (countBatch==nLOOP) {
					if (failCheck()) return;
					countBatch=0;
					ps.executeBatch();
					System.err.print("   " + numLoaded + " loaded...\r"); // CAS42 1/1/18 was log.msg
				}
			}
			if (countBatch>0) ps.executeBatch();
			ps.close();
		}
		catch (Exception e) {ErrorReport.print(e, "save annot hits"); bSuccess=false;}
	}
	
	private void addMirroredHits() throws Exception {
	try { // Create the reflected hits for self-alignment cases; CAS520 add hitnum
		pool.executeUpdate("insert into pseudo_hits (select 0, hitnum, pair_idx,proj1_idx,proj2_idx,grp2_idx,grp1_idx,evalue,pctid," + 
				"score,strand,start2,end2,start1,end1,target_seq,query_seq,gene_overlap,countpct,cvgpct,idx,annot2_idx,annot1_idx,runnum, runsize " + // CAS520 add  runnum
				" from pseudo_hits where pair_idx=" + pairIdx + " and refidx=0 and start1 != start2 and end1 != end2)"	
			);
		pool.executeUpdate("update pseudo_hits as ph1, pseudo_hits as ph2 set ph1.refidx=ph2.idx where ph1.idx=ph2.refidx " +
				" and ph1.refidx=0 and ph2.refidx != 0 and ph1.pair_idx=" + pairIdx + " and ph2.pair_idx=" + pairIdx);
	}
	catch (Exception e) {ErrorReport.print(e, "add mirror hits"); bSuccess=false;}
	}
	
	/****************************************************
	 * CAS535 changed from using UpdatePool via Group to PrepareStatement here [hitAnnotations]
	 */
	private void saveAnnoHits()  { // [hitAnnotations]
	String key="";
	try {
		plog.msg("Save hits to gene annotations ");
		
		/* Load hits from database, getting their new idx */
		Vector <Hit> vecHits = new Vector <Hit> (filteredHits.size()); 
		String st = "SELECT idx, start1, end1, start2, end2, grp1_idx, grp2_idx" +
      			" FROM pseudo_hits WHERE pair_idx=" + pairIdx;
		ResultSet rs = pool.executeQuery(st);
		while (rs.next()) {
			Hit h = new Hit();
			h.idx 			= rs.getInt(1);
			h.query.start 	= rs.getInt(2);
			h.query.end 	= rs.getInt(3);
			h.target.start	= rs.getInt(4);
			h.target.end 	= rs.getInt(5);
			h.query.grpIdx 	= rs.getInt(6);					
			h.target.grpIdx = rs.getInt(7);					
			vecHits.add(h);
		}
		rs.close();
		if (failCheck()) return;
		
	/* save the query annotation hits, which correspond to syProj1 */ /* on self-synteny, get dups;  */
		PreparedStatement ps = pool.prepareStatement("insert ignore into pseudo_hits_annot "
				+ "(hit_idx, annot_idx, olap) values (?,?,?)");
		
		int count=0, cntHit=0, cntBatch=0;
		int [][] vals;
	
		for (Hit ht : vecHits) {// CAS534 unanch never set; if (h.target.grpIdx == p2.unanchoredGrpIdx) {cntSkip++;continue;}
			Group grp = syProj1.getGrpByIdx(ht.query.grpIdx);
			vals = grp.getAnnoHitOverlap(ht, ht.query);
			boolean bSave=false;
			
			for (int i=0; i<vals.length; i++) {
				if (vals[i][1]==0) continue;
				if (!bSave) {cntHit++; bSave=true;}
				
				ps.setInt(1,vals[i][0]);
				ps.setInt(2,vals[i][1]);
				ps.setInt(3,vals[i][2]);
				ps.addBatch();
				count++; cntBatch++;
				if (cntBatch==nLOOP) {
					if (failCheck()) return;
					cntBatch=0;
					ps.executeBatch();
					System.out.print("   " + count + " loaded hit annotations...\r"); 
				}
			}	
		}
		if (cntBatch> 0) ps.executeBatch();
		Utils.prtNumMsg(plog, cntHit, "for " + proj1Name);
		if (failCheck()) return;
		
	/* save the target annotation hits */
		count=cntHit=cntBatch=0;
		for (Hit ht : vecHits) {// CAS534 unanch never set; if (h.target.grpIdx == p2.unanchoredGrpIdx) {cntSkip++;continue;}
			Group grp = syProj2.getGrpByIdx(ht.target.grpIdx);
			
			vals = grp.getAnnoHitOverlap(ht, ht.target);
			boolean bSave=false;
			
			for (int i=0; i<vals.length; i++) {
				if (vals[i][1]==0) continue;
				
				if (!bSave) {cntHit++; bSave=true;}
				
				ps.setInt(1,vals[i][0]);
				ps.setInt(2,vals[i][1]);
				ps.setInt(3,vals[i][2]);
				ps.addBatch();
				count++; cntBatch++;
				if (cntBatch==nLOOP) {
					if (failCheck()) return;
					cntBatch=0;
					ps.executeBatch();
					System.out.print("   " + count + " loaded hit annotations...\r"); 
				}
			}	
		}
		if (cntBatch>0) ps.executeBatch();
		ps.close();	
		if (!isSelf) Utils.prtNumMsg(plog, cntHit, "for " + proj2Name);
		if (failCheck()) return;
	}
	catch (Exception e) {ErrorReport.print(e, "save annot hits " + key); bSuccess=false;}
	}
	// this is faster than the above
	private void saveAnnoHits1(SyProj p1, SyProj p2) 
	{
	try {
		Vector <Hit> newHits = new Vector <Hit> (filteredHits.size()); 
		String st = "SELECT idx, start1, end1, start2, end2, grp1_idx, grp2_idx" +
      			" FROM pseudo_hits WHERE pair_idx=" + pairIdx;
		ResultSet rs = pool.executeQuery(st);
		while (rs.next()) {
			Hit h = new Hit();
			h.idx 			= rs.getInt(1);
			h.query.start 	= rs.getInt(2);
			h.query.end 	= rs.getInt(3);
			h.target.start	= rs.getInt(4);
			h.target.end 	= rs.getInt(5);
			h.query.grpIdx 	= rs.getInt(6);					
			h.target.grpIdx = rs.getInt(7);					
			newHits.add(h);
		}
		rs.close();
		if (failCheck()) return;
		plog.msg("Save hits to gene");
		
		///////// p1
		HashMap<String,Integer> counts1 = new HashMap<String,Integer>();

		int count=0;
		for (Hit h : newHits) {
			Group g = p1.getGrpByIdx(h.query.grpIdx);
			g.addAnnotHitOverlaps(h,h.query,pool,counts1);
			count++;
			if (count % 5000 == 0) System.err.print(count + " checked...\r"); // CAS42 1/1/18 changed from log
		}
		
		if (counts1.keySet().size() > 0) {
			if (counts1.size()==1) {
				for (String atype : counts1.keySet())
					Utils.prtNumMsg(plog, counts1.get(atype), "for " + p1.name);
			}
			else { 
				plog.msg("For " + p1.name + ":");
				for (String atype : counts1.keySet())
					Utils.prtNumMsg(plog, counts1.get(atype), atype);
			}		
		}
		
		/////////// p2 
		HashMap<String,Integer> counts2 = new HashMap<String,Integer>();
		count = 0;
		for (Hit h : newHits) {
			Group g = p2.getGrpByIdx(h.target.grpIdx);
			g.addAnnotHitOverlaps(h, h.target, pool,counts2);
			
			count++;
			if (count % 5000 == 0) System.err.print(count + " checked...\r"); 
		}
		
		if (counts2.keySet().size() > 0) {
			if (counts2.size()==1) {
				for (String atype : counts2.keySet())
					Utils.prtNumMsg(plog, counts2.get(atype), "for " + p2.name);
			}
			else { // I don't think this happens
				plog.msg("For " + p2.name + ":");
				for (String atype : counts2.keySet())
					Utils.prtNumMsg(plog, counts2.get(atype), atype);
			}
		}	
		pool.finishBulkInserts();
	} catch (Exception e) {ErrorReport.print(e, "finish bulk inserts");}
	}
	/*************************************************
	 * CAS500 this was in run(); some of this is obsolete; CAS535 removed addGeneParams,
	 */
	private void initStats() {
		try {
		// Obsolete unless default changed in SyProp; CAS535 remove HitBin.initKeepTypes();
		/** CAS534 was setting to 0 in SyProps, i.e. never 1
		if (mProps.getProperty("keep_gene_gene").equals("1")) HitBin.addKeepTypes(HitType.GeneGene);	// false
		if (mProps.getProperty("keep_gene").equals("1")) { // false
			HitBin.addKeepTypes(HitType.GeneGene);	HitBin.addKeepTypes(HitType.GeneNonGene);	}
		**/
		BinStats.initStats(); 
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
		if (Cancelled.isCancelled() || bInterrupt) {
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
	private int rtiError(String msg) {
		plog.msg(msg);
		ErrorCount.inc();
		bSuccess=false;
		return 0;
	}

}
