package backend;

/******************************************************
 * Read align files and create anchors 
 * CAS500 Jan2020 rewrote this whole thing, though produces same results
 * (still lots of obsolete/dead code)
 * Removed AnnotAction
 */
import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.TreeSet;
import java.util.Vector;
import java.util.Properties;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Collections;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.File;
import java.lang.Math;

import util.Cancelled;
import util.ErrorCount;
import util.ErrorReport;
import util.Logger;
import util.Utilities;

enum AlignType   { Blat, Mum };
enum HitStatus   { In, Out, Undecided };
enum QueryType   { Query, Target, Either };

//CAS500 Pseudo->Seq (directory is /seq, still pseudo in database); Gene appears to be obsolete
enum FileType    { Bes, Mrk, Gene, Seq, Unknown }; 

public class AnchorsMain
{
	private static final int HIT_VEC_INC = 1000;
	private static final int maxHitLength = 10000;
	private static SyProps mProps = null;
	private UpdatePool pool;
	private Logger log;
	private int pairIdx = -1;
	private HashSet<Hit> hits;
	private Vector<Hit> diagHits;

	private boolean bInterrupt = false;
	private Project p1, p2;
	private int mTotalHits=0, mTotalLargeHits=0, mTotalBrokenHits=0;
	private boolean isSelf = false, isNucmer=false, isFPC = false, doClust = true;
	private String resultDir=null;

	public AnchorsMain(int idx, UpdatePool pool, Logger log,Properties props, SyProps pairProps) {
		this.pairIdx = idx;
		this.pool = pool;
		this.log = log;
		
		assert(pairProps != null);
		mProps = pairProps;
		
		doClust = true; // always true
		if (!doClust) log.msg("Clustering is dis-abled");
	}
	
	public boolean run(String proj1Name, String proj2Name) throws Exception
	{
		try {
			long startTime = System.currentTimeMillis();
			
			log.msg("Loading alignments for " + proj1Name + " and " + proj2Name);
			
			Utils.initStats();

			ProjType pType = pool.getProjType(proj1Name);
			isSelf = (proj1Name.equals(proj2Name) && pType == ProjType.pseudo); 
			isFPC = (pType == ProjType.fpc);
			
			p1 = new Project(pool, log, mProps, proj1Name, pType, QueryType.Query);
			
			// make new object even if it is a self alignment - or filtering gets confused
			p2 = new Project(pool, log, mProps, proj2Name, ProjType.pseudo, QueryType.Target);
	
		/** assignments **/
			
			if (Cancelled.isCancelled()) return false; // CAS500 all cancelled returned true
			
			if (p1.isSeq()) p1.loadAnnotation(pool);
			
			assert(p2.isSeq()); 
			p2.loadAnnotation(pool);
			
			// renewPairIdx(p1, p2); CAS501
			
			if (Cancelled.isCancelled()) return false;
			
			addProps();
			
			if (Cancelled.isCancelled()) return false;
	
			// e.g. data/seq_results/p1_to_p2
			resultDir = Constants.getNameResultsDir(proj1Name, isFPC, proj2Name);
			if ( !Utilities.pathExists(resultDir) ) {
				log.msg("Cannot find pair directory " + resultDir);
				ErrorCount.inc();
				return false;
			}
			diagHits = new Vector<Hit>();
			
	/** Read files and prefilter hits ***/	
			boolean rc;
			if (p1.isSeq()) rc = processSeqFiles();
			else 			rc = processFpcFiles();
			if (!rc) return false;
			
			if (Constants.TRACE || Constants.PRT_STATS) {
				p1.printBinStats();  
				p2.printBinStats(); 
			}
	
	/** Filter hits ***/	
			log.msg("Filter hits");
			
			// Do the final TopN filtering, and collect all the hits
			// which pass on at least one side into the set "hits"
			hits = new HashSet<Hit>(); 
			p1.filterHits(hits); 
			p2.filterHits(hits);
			
			int nQueryIn = 0, nTargetIn = 0, nBothIn=0;
			boolean bAnd = mProps.getBoolean("topn_and"); // true
			
			for (Hit h : hits) {	
				if (h.target.status == HitStatus.In) nTargetIn++;
				if (h.query.status ==  HitStatus.In) nQueryIn++;
				
				if (bAnd) {
					if (h.query.status == HitStatus.In && h.target.status == HitStatus.In)
						h.status = HitStatus.In;
				}
				else {
					if (h.query.status == HitStatus.In || h.target.status == HitStatus.In)					
						h.status = HitStatus.In;
				}
			}
			Utils.prtNumMsg(log, nQueryIn, "Query filtered hits");
			Utils.prtNumMsg(log, nTargetIn,"Target filtered hits");
			
			hits.addAll(diagHits); // diagHits only for self
			
			for (Hit hit : hits) { 
				if (hit.status == HitStatus.In) {			
					Utils.incHist("TopNHist1Accept", hit.binsize1);
					Utils.incHist("TopNHist2Accept", hit.binsize2);
					if (hit.mBT != null) { // null for fpc-seq, !null for seq-seq
						Utils.incStat(hit.mBT.toString() + "FinalHits", 1);
						Utils.incStat(hit.mBT.toString() + "FinalOrigHits", hit.origHits);
					}
					nBothIn++;
				}
			}
			Utils.prtNumMsg(log, nBothIn, "Total hits to load");
			Utils.incStat("TopNFinalKeep", nBothIn);		
						
			int numLoaded = 0;
			
			// CAS515 they end up out of any logical order; since the Hitidx is the Hit#, put in logical order
			pool.executeUpdate("alter table pseudo_hits modify countpct integer"); 
			Vector <Hit> vecHits = new Vector <Hit> (hits.size());
			for (Hit h : hits) vecHits.add(h);
			Hit.sortByTarget(vecHits);
			
			for (Hit hit : vecHits) {
				if (hit.status == HitStatus.In) {
					if (bInterrupt) return false;
					
					uploadHit(hit, p1, p2); // enters hit into database
					if (++numLoaded % 5000 == 0)
						System.out.print(numLoaded + " loaded...\r"); // CAS42 1/1/18 was log.msg
				}
			}
			pool.finishBulkInserts();
			
			hits.clear();
			hits = null;
			
			if (isSelf) {
				addMirroredHits();	
			}
			hitAnnotations(p1,p2);
			
			// Lastly flip the anchor coordinates if the first project is draft that has been ordered
			// against another project
			/*** CAS505 this gets changed int OrderAgainst
			if (!p1.orderAgainst.equals("") && !p1.orderAgainst.equals(p2.getName()))
			{
				pool.executeUpdate("update pseudo_hits as ph, pseudos as p, groups as g  " +
					" set ph.start1=p.length-ph.start1, ph.end1=p.length-ph.end1 " +
					"where ph.pair_idx=" + pairIdx + " and p.grp_idx=ph.grp1_idx and g.idx=ph.grp1_idx and g.flipped=1");
			}
			***/
			if (p1.isSeq()) {
				Utils.uploadStats(pool, pairIdx, p1.idx, p2.idx);
			}	
			
			// CAS517 move from SyntenyMain - there is no reason to wait until after synteny computation
			if (p1.isSeq() && p2.isSeq() && p1.idx != p2.idx) {
				AnchorsPost obj = new AnchorsPost(pairIdx, p1, p2, pool, log);
				obj.setGeneRunCounts();
			}
			Utils.timeMsg(log, startTime, "Anchors");
		}
		catch (OutOfMemoryError e)
		{
			System.out.println("\n\nOut of memory! Edit the symap launch script and increase the memory limit ($maxmem).\n\n");
			System.exit(0);
		}		
		return true;
	}
	public void interrupt()
	{
		bInterrupt = true;	
	}
	private boolean processFpcFiles() {
		try {
			// Changes results slightly if bes is before mrk
			String[] btypes1 = new String[] {Constants.mrkType, Constants.besType};
			String alignDir = resultDir + Constants.alignDir ;
			
			File dh = new File(alignDir);
			if (!dh.exists() || !dh.isDirectory()) {
				log.msg("Error: /align directory does not exist " + alignDir);
				return false;
			}
			log.msg("Align directory " + alignDir);
			int nHitsScanned = 0;
			
			for(int i1 = 0; i1 < btypes1.length; i1++) {
				String btype1 = btypes1[i1];
				log.msg("Processing type " + btype1);
				
				// process all aligned files in directory	
				File[] fs = dh.listFiles();
				for (int k = 0; k < fs.length; k++) {
					File f = fs[k];
					if (!f.isFile() || !f.getName().endsWith(Constants.blatSuffix)) continue;
				
					if (f.getName().startsWith(btype1)) {
						AnchorFile af = new AnchorFile(f, btype1, Constants.seqType);
						int nHits = scanBlatFile(af, p1, p2);
						nHitsScanned+=nHits;
								
						if (bInterrupt) return false;
						Utils.prtNumMsg(log, nHits, "scanned " + Utils.fileFromPath(f.toString()));
					}
				}
			}
			Utils.prtNumMsg(log, nHitsScanned, "Total scanned hits           ");
			
			if (nHitsScanned == 0) { // CASz check was not here, so always wrote
				log.msg("No readable anchors were found - BLAT probably did not run correctly.");
				return false;
			}
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, "Reading fpc anchors"); return false;}
	}
	/*******************************************************************/
	private boolean processSeqFiles() {
		try {
			String alignDir = resultDir + Constants.alignDir;
			
			File dh = new File(alignDir);
			if (!dh.exists() || !dh.isDirectory()) {
				log.msg("Error: /align directory does not exist " + alignDir);
				return false;
			}
			log.msg("Alignment files in " + alignDir);
			
			// process all aligned files in directory
			log.msg("Scan files to create clusters:"); 	
			int nHitsScanned = 0;
			TreeSet<String> skipList = new TreeSet<String>();
			File[] fs = dh.listFiles();
			
			int iter = (isSelf) ? 2 : 1; // process notSelf first to get same results as v4.2
			
			for (int i=0; i<iter; i++) {
				boolean skipSelf = (i==0) ? true : false; // v4 aligns files or user-supplied
	
				for (int k = 0; k < fs.length; k++) {
					if (bInterrupt) return false;
							
					File f = fs[k];
					if (!f.isFile()) continue;
					
					String fName = f.getName();
					if (!fName.endsWith(Constants.mumSuffix))	continue;
					if (isSelf) {
						if ( skipSelf &&  fName.startsWith(Constants.selfPrefix)) continue;
						if (!skipSelf && !fName.startsWith(Constants.selfPrefix)) continue;
					}
					
					AnchorFile af = new AnchorFile(f,Constants.seqType, Constants.seqType);
					int nHits = scanFile1(af, p1, p2, skipSelf); 
							
					if (nHits == 0) skipList.add(af.mFile.getName());
					else nHitsScanned += nHits;
					
					Utils.prtNumMsg(log, nHits, "scanned " + Utils.fileFromPath(f.toString()));
					
					if (Cancelled.isCancelled()) return false; 
				}
			}
			p1.collectPGInfo(); // XXX
			p2.collectPGInfo();
			
			if (Cancelled.isCancelled()) return false;
			
			Utils.prtNumMsg(log, nHitsScanned, "Total scanned hits");
			if (nHitsScanned!=mTotalHits)
				Utils.prtNumMsg(log, mTotalHits, "Total accepted hits");
			
			if (nHitsScanned == 0) {
				log.msg("No readable anchors were found - MUMmer probably did not run correctly.");
				ErrorCount.inc();
				return false;
			}
			if (mTotalHits == 0) {
				log.msg("No good anchors were found");
				ErrorCount.inc();
				return false;
			}
			if (mTotalLargeHits > 0) {
				Utils.prtNumMsg(log, mTotalLargeHits, "Large hits (> " + maxHitLength 
						+ ")  " + mTotalBrokenHits + " Split "); // CAS505 changed from 'Broken'
			}
			
	/** Second scan - to cluster **/	
			log.msg("Scan files to load hits:");
			
			nHitsScanned = 0;			
			int filesScanned = 0, filesToScan = fs.length;	
				
			for (int i=0; i<iter; i++) {
				boolean bSelf = (i==0) ? true : false; 
				
				for (int k = 0; k < fs.length; k++) {
					if (Cancelled.isCancelled()) return false;
					if (bInterrupt) return false;
							
					File f = fs[k];		
					if (!f.isFile()) continue;
					
					String fName = f.getName();
					if (!fName.endsWith(Constants.mumSuffix))	continue;
					if (skipList.contains(fName)) continue;
					
					if (isSelf) {
						if (i==0 &&  fName.startsWith(Constants.selfPrefix)) continue;
						if (i==1 && !fName.startsWith(Constants.selfPrefix)) continue;
					}
					
					AnchorFile af = new AnchorFile(f,Constants.seqType, Constants.seqType);
							
					int nHits = scanFile2(af, p1, p2, bSelf);
					nHitsScanned += nHits;
							
					filesScanned++;
					if (p1.isUnordered()) {
						if (filesScanned % 100 == 0)
							Utils.prtNumMsg(log, nHits, "load " + filesScanned + "/" + filesToScan + " files");
					}
					else		
						Utils.prtNumMsg(log, nHits, "load " + Utils.fileFromPath(f.toString()));
				}
			}
			Utils.prtNumMsg(log, nHitsScanned, "Total load hits   ");
			Utils.incStat("DiagHitsRaw", diagHits.size());
			
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, "Reading seq anchors"); return false;}
	}
	
	// seq to seq - first time through, create the predicted genes
	private int scanFile1(AnchorFile file, Project p1, Project p2, boolean skipSelf) throws Exception
	{
		BufferedReader fh = new BufferedReader(new FileReader(file.mFile));
		
		Vector<Hit> rawHits = new Vector<Hit>(HIT_VEC_INC,HIT_VEC_INC);
		String line;
		int numErrors = 0, lineNum = 0;
		int skip1=0, skip2=0, skip3=0;
		
		while (fh.ready()) {
			if (bInterrupt) {
				fh.close();
				return 0;
			}
			
			line = fh.readLine().trim();
			if (line.length() == 0) continue;
			if (!Character.isDigit(line.charAt(0))) {// CAS515 add in order to allow headers
				if (line.startsWith("NUCMER")) {
					isNucmer=true;
					log.msg("Scanning NUCMER file");
				}
				continue; 
			}
			lineNum++;
			
			Hit hit = new Hit();
			hit.origHits = 1;
			hit.query.fileType = file.mType1;
			hit.target.fileType = file.mType2;
			
			boolean success = scanNextMummerHit(line,hit);
			if (!success) {
				if (numErrors < 5){
					log.msg("Parse error on line " + lineNum + " in " + file.mFile.getName());
					numErrors++;
					continue;
				}
				else {
					log.msg("Too many errors in file");
					fh.close();
					return 0;
				}
			}
			hit.query.grpIdx = p1.grpIdxFromQuery(hit.query.name);
			if (hit.query.grpIdx == -1) {
				log.msg(p2.getName() + ": Query not found: " + hit.query.name + " --skipping file");
				fh.close();
				return 0;
			}
			hit.target.grpIdx = p2.grpIdxFromQuery(hit.target.name);
			if (hit.target.grpIdx == -1) {
				log.msg(p2.getName() + ": Target not found for: " + hit.target.name + " --skipping file");
				fh.close();
				return 0;
			}
			
			// XXX - check repeated in scanFile2
			if (skipSelf && hit.query.grpIdx==hit.target.grpIdx) {
				skip1++;
				continue; // v4.2 (run twice) run the chr x itself separately
			}
			/** needed for v42 but not v5
			if (p1.idx == p2.idx && hit.query.grpIdx > hit.target.grpIdx) {
				skip2++;
				continue; // for self, mirroring later
			}
			**/
			if (hit.query.grpIdx == hit.target.grpIdx && hit.query.start < hit.target.start) {
				skip3++;
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
		}
		fh.close();
		
		// Only has values for self (or maybe user supplied files)
		if (Constants.PRT_STATS) {
			Utils.prtNumMsgNZ(skip1, "   Skip1 - Self with same group (is run separately)");
			Utils.prtNumMsgNZ(skip2, "   Skip2 - Same project with group1>group2 (mirror later)");
			Utils.prtNumMsgNZ(skip3, "   Skip3 - Same group   with start1<start2 (mirror later)");
		}
		/** cluster genes (create predicted genes) **/
		for (Hit h : rawHits) 
		{			
			Group grp1 = p1.getGrpByIdx(h.query.grpIdx);
			Group grp2 = p2.getGrpByIdx(h.target.grpIdx);
			
			grp1.testHitForAnnotOverlapAndUpdate2(h.query.start, h.query.end);
			grp2.testHitForAnnotOverlapAndUpdate2(h.target.start, h.target.end);
		}
		
		rawHits = null;
		return lineNum;
	}
	// seq/seq 2nd time through - do the clustering
	// this is about the same as scanFile1 except for no checks, diagonals, and final processing
	private int scanFile2(AnchorFile file, Project p1, Project p2, boolean skipSelf) throws Exception
	{
		BufferedReader fh = new BufferedReader(new FileReader(file.mFile));
		Vector<Hit> rawHits = new Vector<Hit>(HIT_VEC_INC,HIT_VEC_INC);
		String line;
		
		int totalLargeHits = 0, totalBrokenHits = 0;
		while (fh.ready()) 
		{
			if (bInterrupt) {
				fh.close();
				return 0;
			}
			line = fh.readLine().trim();
			
			if (line.length() == 0) continue;
			if (!Character.isDigit(line.charAt(0))) continue; // CAS515 allow header lines
			
			Hit hit = new Hit();	// CAS515 make Object after reject lines
			hit.origHits = 1;
			hit.query.fileType  = file.mType1;
			hit.target.fileType = file.mType2;
			
			boolean success = scanNextMummerHit(line,hit);
			if (!success) Utils.die("SyMAP error - scanNextHummerHit for scan2");
			
			hit.query.grpIdx  = p1.grpIdxFromQuery(hit.query.name);
			hit.target.grpIdx = p2.grpIdxFromQuery(hit.target.name);
			
			// same check as in scanFile1
			if (skipSelf && hit.query.grpIdx==hit.target.grpIdx) continue;
			//if (p1.idx == p2.idx && hit.query.grpIdx > hit.target.grpIdx) continue; 
			if (hit.query.grpIdx == hit.target.grpIdx && hit.query.start < hit.target.start) continue; 

			// Diagonal hits are handle separately (always want to keep them, and sometimes very long etc.)
			if (hit.query.grpIdx == hit.target.grpIdx && hit.query.start == hit.target.start
					&& hit.query.end == hit.target.end) {
				hit.status = HitStatus.In; 
				diagHits.add(hit);
				continue;
			}
			
			if (hit.maxLength() > maxHitLength){
				Vector<Hit> brokenHits = breakHit(hit);
				totalLargeHits++;
				totalBrokenHits += brokenHits.size();
				rawHits.addAll(brokenHits);
			}
			else {
				hit.orderEnds();
				rawHits.add(hit);
			}			
		}
		fh.close();
		if (rawHits.size()==0) return 0;
		
		Utils.incStat("RawHits",rawHits.size());
		Utils.incStat("LargeHits",totalLargeHits);
		Utils.incStat("BrokenHits",totalBrokenHits);

		if (doClust ) 	// always true										
			rawHits = clusterGeneHits2( rawHits );
		
		// put them in their TopN 'cluster' bins, dropping off those that already fail topN
		Collections.sort(rawHits);
		preFilterHits2(rawHits, p1, p2);
		
		return rawHits.size();
	}	
		
	private Vector<Hit> breakHit(Hit hit)
	{
		Vector<Hit> ret = new Vector<Hit>();
		
		// Break the hit into pieces, taking care to not leave a tiny leftover hit, and for 
		// cases where query and target are different lengths (maybe not possible with mummer).
		// Note, hits have been previously fixed so start < end. 
		int minLeftover = maxHitLength/10;
		
		int qlen = hit.query.length();
		int tlen = hit.target.length();
		
		int qleft = qlen%maxHitLength;
		int tleft = tlen%maxHitLength;		
		
		int qparts = (qleft >= minLeftover ?  (1 + qlen/maxHitLength) : (qlen/maxHitLength));
		int tparts = (tleft >= minLeftover ?  (1 + tlen/maxHitLength) : (tlen/maxHitLength));
		
		int parts = Math.min(qparts,tparts);
		
		if (parts == 1)
		{
			hit.orderEnds();
			hit.idx=6;
			ret.add(hit);
		}
		else if (!hit.reversed())
		{
			// build (parts-1) hits of fixed size, and put the rest into the final hit
			
			hit.orderEnds(); // this is a bit hacky but in fact the query and target can BOTH be reversed,
								// in which case reversed() returns false
			for (int i = 1; i < parts; i++)
			{
				int qstart = hit.query.start + maxHitLength*(i-1);
				int qend = qstart + maxHitLength - 1;
				int tstart = hit.target.start + maxHitLength*(i-1);
				int tend = tstart + maxHitLength - 1;
				Hit h = new Hit(hit);
				h.query.start = qstart;
				h.query.end = qend;
				h.target.start = tstart;
				h.target.end = tend;
				h.idx=1;
				ret.add(h);
			
			}
			int qstart = hit.query.start + maxHitLength*(parts-1);
			int qend = hit.query.end;
			int tstart = hit.target.start + maxHitLength*(parts-1);
			int tend = hit.target.end;
			Hit h = new Hit(hit);
			h.query.start = qstart;
			h.query.end = qend;
			h.target.start = tstart;
			h.target.end = tend;
			h.idx=2;
			ret.add(h);
		}
		else if (hit.reversed())
		{
			// reversed - for forward through the query, backward through target
			
			hit.orderEnds(); // because this is the last time we need to know whether it started out reversed
			
			for (int i = 1; i < parts; i++)
			{
				int qstart = hit.query.start + maxHitLength*(i-1);
				int qend = qstart + maxHitLength - 1;
				int tend = hit.target.end - maxHitLength*(i-1);
				int tstart = tend - maxHitLength + 1;
				Hit h = new Hit(hit);
				h.query.start = qstart;
				h.query.end = qend;
				h.target.start = tstart;
				h.target.end = tend;
				h.idx=3;
				ret.add(h);

			}
			int qstart = hit.query.start + maxHitLength*(parts-1);
			int qend = hit.query.end;
			int tend = hit.target.end - maxHitLength*(parts-1);
			int tstart = hit.target.start;
			Hit h = new Hit(hit);
			h.query.start = qstart;
			h.query.end = qend;
			h.target.start = tstart;
			h.target.end = tend;
			h.idx=4;
			ret.add(h);		
		}
		return ret;
	}
	
	// for fpc/pseudo only
	private int scanBlatFile(AnchorFile file, Project p1, Project p2) throws Exception
	{
		AlignType alignType = null;
		if (file.mFile.getName().endsWith(".blat"))
			alignType = AlignType.Blat;
		else return 0;
		
		BufferedReader fh = new BufferedReader(new FileReader(file.mFile));
		int lineNum = 0;

		if (alignType == AlignType.Blat) {
			// Skip header 
			fh.readLine();
			fh.readLine();
			fh.readLine();
			fh.readLine();
			fh.readLine();
		}
		
		Vector<Hit> rawHits = new Vector<Hit>(HIT_VEC_INC,HIT_VEC_INC);
		String line;
		int numErrors = 0;
		
		while (fh.ready()) {
			if (bInterrupt) {
				fh.close();
				return 0;
			}
			Hit hit = new Hit();
			hit.origHits = 1;
			hit.query.fileType = file.mType1;
			hit.target.fileType = file.mType2;
			line = fh.readLine().trim();
			lineNum++;
			if (line.length() > 0)
			{
				boolean success = false; 
				boolean reversed = (file.mType1 == FileType.Seq && file.mType2 != FileType.Seq); // Obsolete
				success = scanNextBlatHit(line,hit,reversed);
				
				if (!success)
				{
					if (numErrors < 5)
					{
						log.msg("Parse error on line " + lineNum + " in " + file.mFile.getName());
						numErrors++;
						continue;
					}
					else throw( new Exception("Too many errors in file!") );
				}

				if (hit.query.fileType == FileType.Bes)
				{
					if (!p1.getFPCData().parseBES(hit.query.name,hit))
						throw(new Exception("Unable to parse BES " + hit.query.name));
				}
				
				if (hit.target.fileType == FileType.Seq) {
					hit.target.grpIdx = p2.grpIdxFromQuery(hit.target.name);
					if (hit.target.grpIdx == -1)
						throw(new Exception("Target not found: " + hit.target.name));
				}
				
				rawHits.add(hit);
			}
		}
		fh.close();
		Utils.incStat("RawHits",rawHits.size());

		preFilterHits2(rawHits, p1, p2);
		
		return lineNum;
	}		

	// Cluster the mummer hits into larger hits using the annotations, including the "predicted genes".
	// So our possible hit set will now be a subset of annotations x annotations, and the 
	// actual length of the hits will be the sum of the hits that went into them.
	// The component hits are remembered and saved to show as "exons" in the viewer.

	private Vector<Hit> clusterGeneHits2(Vector<Hit> inHits) throws Exception
	{
		Vector<Hit> outHits = new Vector<Hit>(HIT_VEC_INC, HIT_VEC_INC);
		Vector<Hit> bigHits = new Vector<Hit>();
		
		HashMap<String,Vector<Hit>> hitBins = new HashMap<String,Vector<Hit>>();
		HashMap<String,AnnotElem> key2qannot = new HashMap<String,AnnotElem>();
		HashMap<String,AnnotElem> key2tannot = new HashMap<String,AnnotElem>();
		HashMap<String,HitType> binTypes = new HashMap<String,HitType>(); // hack, for stats
						
		// First put the hits into bins labeled by annotation1 x annotation2
		// Note that a given annotation can only go to one group
		for (Hit hit : inHits) 
		{		
			Group grp1 = p1.getGrpByIdx(hit.query.grpIdx);
			Group grp2 = p2.getGrpByIdx(hit.target.grpIdx);
			
			AnnotElem qAnnot = grp1.getMostOverlappingAnnot(hit.query.start, hit.query.end);
			AnnotElem tAnnot = grp2.getMostOverlappingAnnot(hit.target.start, hit.target.end);

			if (qAnnot == null)
				throw(new Exception("missing query annot! grp:" + grp1.idStr() + " start:" + hit.query.start + " end:" + hit.query.end + " idx:" + hit.idx));	
			if (tAnnot == null)
				throw(new Exception("missing target annot! grp:" + grp2.idStr() + " start:" + hit.target.start + " end:" + hit.target.end));	
			
			String key = qAnnot.mID + "_" + tAnnot.mID;
			if (!hitBins.containsKey(key))
			{
				hitBins.put(key, new Vector<Hit>());
				key2qannot.put(key,qAnnot);
				key2tannot.put(key,tAnnot);
				if (!qAnnot.isGene() && !tAnnot.isGene()) {
					Utils.incStat("NonGeneClusters", 1);	
					binTypes.put(key,HitType.NonGene);
				}
				else if (qAnnot.isGene() && tAnnot.isGene()) {
					Utils.incStat("GeneClusters", 1);
					binTypes.put(key,HitType.GeneGene);
				}
				else {
					Utils.incStat("GeneNonGeneClusters", 1);	
					binTypes.put(key,HitType.GeneNonGene);
				}						
			}
			hitBins.get(key).add(hit);
			if (!qAnnot.isGene() && !tAnnot.isGene()) 	Utils.incStat("NonGeneHits", 1);	
			else if (qAnnot.isGene() && tAnnot.isGene())	Utils.incStat("GeneHits", 1);	
			else 										Utils.incStat("GeneNonGeneHits", 1);						
		}
		
		// Merge the binned hits
		for (String key : hitBins.keySet()) 
		{			
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
		
		Utils.incStat("TotalClusters", outHits.size());
		
		if (bigHits.size() > 0)
		{
			Utils.incStat("BigHits", bigHits.size());
			Hit.mergeOverlappingHits(bigHits);
			Utils.incStat("MergedBigHits", bigHits.size());
			outHits.addAll(bigHits);
		}
		return outHits;
	}	
	// Add hits to their topN bins, 
	// applying the topN criterion immediately when possible to reduce memory
	// Called from scanBlatFile and scanFile2; hits get transfered to respective project 
	private void preFilterHits2(Vector<Hit> rawHits, Project p1, Project p2) throws Exception
	{
		Vector<Hit> theseDiag = new Vector<Hit>();
		
		for (Hit hit : rawHits) {
			// If it's a diagonal hit, keep it but do not use in further processing					
			if (hit.query.grpIdx == hit.target.grpIdx 
			 && hit.query.start == hit.target.start
			 && hit.query.end == hit.target.end)
			{
				hit.status = HitStatus.In; 
				theseDiag.add(hit); 
				Utils.incStat("DiagHits", 1);
				continue;
			}
			
			// Add to the appropriate TopN filter HitBin, unless it's
			// already clear that it will not pass the filter
			p1.checkPreFilter2(hit,hit.query);
			p2.checkPreFilter2(hit,hit.target);
		}
		
		if (theseDiag.size() > 0) { // self only		
			Hit.mergeOverlappingHits(theseDiag);
	
			diagHits.addAll(theseDiag);
			Utils.incStat("MergedDiagHits", diagHits.size());
		}	
	}

	private boolean scanNextBlatHit(String line, Hit hit, boolean reversed)
	{
		String[] fs = line.split("\\s+");
		if (fs.length < 21)
			return false;
		
		int match = Integer.parseInt(fs[0]) + Integer.parseInt(fs[2]);
		int mis = Integer.parseInt(fs[1]);
		String strand 	= fs[8];
			
		int tstart 	= Integer.parseInt(fs[15]);
		int tend 	= Integer.parseInt(fs[16]);
		int qstart 	= Integer.parseInt(fs[11]);
		int qend 	= Integer.parseInt(fs[12]);
		int qlen 	= Integer.parseInt(fs[10]);
				
		int[] qstarts 	= Utils.strArrayToInt(fs[19].split(","));
		int[] tstarts 	= Utils.strArrayToInt(fs[20].split(","));
		int[] bsizes 	= Utils.strArrayToInt(fs[18].split(","));

		// for reverse strand hits, blat reverses the coordinates of the blocks
		if (strand.equals("-"))
		{
			for (int i = 0; i < qstarts.length; i++)
				qstarts[i] = qlen - qstarts[i] - bsizes[i];
		}

		int pctid = Math.round(100*match/(match + mis));
		strand = (strand.equals("-") ? "+/-" : "+/+");
		
		int[] qseqs = new int[qstarts.length*2];
		int[] tseqs = new int[tstarts.length*2];
		
		for (int i = 0; i < qstarts.length; i++)
		{
			int qend1 = qstarts[i] + bsizes[i] - 1;
			int tend1 = tstarts[i] + bsizes[i] - 1;
			
			qseqs[i*2] = qstarts[i];
			qseqs[i*2+1] = qend1;
			
			tseqs[i*2] = tstarts[i];
			tseqs[i*2+1] = tend1;
		}	
		
		hit.query.start  = (!reversed ? qstart : tstart);
		hit.query.end    = (!reversed ? qend   : tend);
		hit.target.start = (!reversed ? tstart : qstart);
		hit.target.end   = (!reversed ? tend   : qend);
		
		hit.query.name  = (!reversed ? fs[9]  : fs[13]);
		hit.target.name = (!reversed ? fs[13] : fs[9]);
		
		hit.query.name = hit.query.name.intern();
		hit.target.name = hit.target.name.intern();
		
		hit.matchLen = match;
		hit.pctid = pctid;
		hit.strand = strand.intern(); 
		
		hit.query.blocks = (!reversed ? qseqs : tseqs);
		hit.target.blocks = (!reversed ? tseqs : qseqs);
		
		return true;
	}
	/*********************************************
	 * 0        1       2        3       4       5       6
	 * [S1]    [E1]    [S2]    	[E2]    [LEN 1] [LEN 2] [% IDY] [% SIM] [% STP] [LEN R] [LEN Q]     [FRM]   [TAGS]
      15491   15814  20452060 20451737   324     324    61.11   78.70   1.39    73840631  37257345  2   -3  chr1    chr3
	 NUCMER does not have %SIM or %STP
	 */
	private boolean scanNextMummerHit(String line, Hit hit)
	{
		String[] fs = line.split("\\s+");
		if (fs.length < 13)
			return false;
		
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

	private void uploadHit(Hit hit, Project p1, Project p2) throws Exception
	{
		// Set gene overlap field. For pseudo, we've already got this due to clustering, but for
		// FPC we've got to set it. 
		int geneOlap = 0;
		if (p1.isFPC())
		{
			Group g2 = p2.getGrpByIdx(hit.target.grpIdx);
			if (g2 != null && g2.testHitForAnnotOverlap(hit.target)) geneOlap = 1; 
		}
		else
		{
			if (hit.mBT == HitType.GeneGene)			geneOlap = 2;	
			else if (hit.mBT == HitType.GeneNonGene)	geneOlap = 1;	
		}
		Vector<String> vals = new Vector<String>(30);
		switch (hit.query.fileType)
		{
			case Bes:
				String rf = (hit.rf == RF.R ? "r" : "f");
				vals.add("" + pairIdx);
				vals.add("" + p1.getIdx());
				vals.add("" + p2.getIdx());
				vals.add(hit.clone);
				vals.add(rf);
				vals.add("" + hit.target.grpIdx);
				vals.add("0");
				vals.add("" + hit.pctid);
				vals.add("" + hit.matchLen);
				vals.add(hit.strand);
				vals.add("" + hit.query.start);
				vals.add("" + hit.query.end);
				vals.add("" + hit.target.start);
				vals.add("" + hit.target.end);
				vals.add(Utils.intArrayToBlockStr(hit.query.blocks));
				vals.add(Utils.intArrayToBlockStr(hit.target.blocks));
				vals.add("" + geneOlap);
				pool.bulkInsertAdd("bes", vals);					
				break;
			case Mrk:
				vals.add("" + pairIdx);
				vals.add("" + p1.getIdx());
				vals.add(hit.query.name);
				vals.add("" + hit.target.grpIdx);
				vals.add("0");
				vals.add("" + hit.pctid);
				vals.add("" + hit.matchLen);
				vals.add(hit.strand);
				vals.add("" + hit.query.start);
				vals.add("" + hit.query.end);
				vals.add("" + hit.target.start);
				vals.add("" + hit.target.end);
				vals.add(Utils.intArrayToBlockStr(hit.query.blocks));
				vals.add(Utils.intArrayToBlockStr(hit.target.blocks));
				vals.add("" + geneOlap);
				pool.bulkInsertAdd("mrk", vals);				
				break;
			case Seq:
			case Gene: // See UpdatePool
				//stmt = "insert into pseudo_hits (pair_idx,proj1_idx,proj2_idx," +
				//"grp1_idx,grp2_idx,evalue,pctid,score,strand,start1,end1,start2,end2,query_seq," +
				//"target_seq,gene_overlap,countpct,cvgpct,annot1_idx,annot2_idx)";
				vals.add("" + pairIdx);
				vals.add("" + p1.getIdx());
				vals.add("" + p2.getIdx());
				vals.add("" + hit.query.grpIdx);
				vals.add("" + hit.target.grpIdx);
				vals.add("0");			// evalue, referred to but never has a value
				vals.add("" + hit.pctid);
				vals.add("" + hit.matchLen); // saved as score, but never used
				vals.add(hit.strand);
				vals.add("" + hit.query.start);
				vals.add("" + hit.query.end);
				vals.add("" + hit.target.start);
				vals.add("" + hit.target.end);
				vals.add(Utils.intArrayToBlockStr(hit.query.blocks));
				vals.add(Utils.intArrayToBlockStr(hit.target.blocks));
				vals.add("" + geneOlap);
				vals.add(""+ (hit.query.blocks.length/2)); //CAS515 countpct;  unsigned tiny int; now #Merged Hits
				vals.add("" + hit.pctsim); // CAS515 cvgpct was 0; unsigned tiny int; now Avg%Sim
				vals.add("" + hit.annotIdx1);
				vals.add("" + hit.annotIdx2);
				pool.bulkInsertAdd("pseudo", vals);
				break;
			default:
				throw(new Exception("Unknown file type!!"));
		}
	}
	/*** CAS504 don't need
	private void checkUpdateHitsTable() throws Exception
	{
		ResultSet rs = pool.executeQuery("show columns from pseudo_hits where field='annot1_idx'");
		if (!rs.first())
		{
			pool.executeUpdate("alter table pseudo_hits add annot1_idx integer default 0");	
			pool.executeUpdate("alter table pseudo_hits add annot2_idx integer default 0");	
		}
	}
	**/
	/** CAS501 moved to ProjectManagerFrameCommon so can save parameters 
	private void renewPairIdx(Project p1, Project p2) throws SQLException
	{
		String st = "DELETE FROM pairs WHERE proj1_idx=" + p1.getIdx() + " AND proj2_idx=" + p2.getIdx();
		pool.executeUpdate(st);
	
		st = "INSERT INTO pairs (proj1_idx,proj2_idx) VALUES('" + p1.getIdx() + "','" + p2.getIdx() + "')";
		pool.executeUpdate(st);
		
		st = "SELECT idx FROM pairs WHERE proj1_idx=" + p1.getIdx() + " AND proj2_idx=" + p2.getIdx();
		pairIdx = pool.getIdx(st);
	}
	**/
	private void addMirroredHits() throws Exception
	{
		// Create the reflected hits for self-alignment cases
		pool.executeUpdate("insert into pseudo_hits (select 0,pair_idx,proj1_idx,proj2_idx,grp2_idx,grp1_idx,evalue,pctid," + 
				"score,strand,start2,end2,start1,end1,target_seq,query_seq,gene_overlap,countpct,cvgpct,idx,annot2_idx,annot1_idx,runsize " + 
				" from pseudo_hits where pair_idx=" + pairIdx + " and refidx=0 and start1 != start2 and end1 != end2)"	
			);
		pool.executeUpdate("update pseudo_hits as ph1, pseudo_hits as ph2 set ph1.refidx=ph2.idx where ph1.idx=ph2.refidx " +
				" and ph1.refidx=0 and ph2.refidx != 0 and ph1.pair_idx=" + pairIdx + " and ph2.pair_idx=" + pairIdx);
	}
	
	// Download the recently-uploaded hits (to get their db index) and 
	// enter the pseudo_hits_annot entry for the gene hits only.
	// This is kind of ineffecient because we've already found annotation overlaps
	// in doing the clustering, but it's more complicated to keep track there.
	private void hitAnnotations(Project p1, Project p2) throws SQLException
	{
		Vector<Hit> newHits = getUploadedHits(p1);
		log.msg("Load hits with annotation overlaps ");
		
		/////////// p2 (always seq)
		HashMap<String,Integer> counts2 = new HashMap<String,Integer>();
		int count = 0;
		for (Hit h : newHits) {
			if (h.target.grpIdx == p2.unanchoredGrpIdx) continue;
			Group g = p2.getGrpByIdx(h.target.grpIdx);
			g.addAnnotHitOverlaps(h,h.target,pool,counts2);
			count++;
			if (count % 5000 == 0) System.out.print(count + " checked...\r"); 
		}
		if (counts2.keySet().size() > 0) {
			if (counts2.size()==1) {
				for (String atype : counts2.keySet())
					Utils.prtNumMsg(log, counts2.get(atype), "for " + p2.name);
			}
			else { // I don't think this happens
				log.msg("For " + p2.name + ":");
				for (String atype : counts2.keySet())
					Utils.prtNumMsg(log, counts2.get(atype), atype);
			}
		}	
		///////// p1
		if (p1.type == ProjType.pseudo)
		{
			HashMap<String,Integer> counts1 = new HashMap<String,Integer>();

			count = 0;
			for (Hit h : newHits) {
				if (h.query.grpIdx == p1.unanchoredGrpIdx) continue;
				
				Group g = p1.getGrpByIdx(h.query.grpIdx);
				g.addAnnotHitOverlaps(h,h.query,pool,counts1);
				count++;
				if (count % 5000 == 0) System.out.print(count + " checked...\r"); // CAS42 1/1/18 changed from log
			}
			
			if (counts1.keySet().size() > 0) {
				if (counts1.size()==1) {
					for (String atype : counts1.keySet())
						Utils.prtNumMsg(log, counts1.get(atype), "for " + p1.name);
				}
				else { 
					log.msg("For " + p1.name + ":");
					for (String atype : counts1.keySet())
						Utils.prtNumMsg(log, counts1.get(atype), atype);
				}		
			}
		}
		pool.finishBulkInserts();
	}
	
	private Vector<Hit> getUploadedHits(Project p1) throws SQLException
	{
		Vector<Hit> ret = new Vector<Hit>();
		if (p1.type != ProjType.pseudo) return ret;
		
      	String st = "SELECT pseudo_hits.idx as hidx, pseudo_hits.start2, pseudo_hits.end2, " +
      			" pseudo_hits.start1, pseudo_hits.end1, pseudo_hits.grp1_idx, pseudo_hits.grp2_idx" +
      			" FROM pseudo_hits WHERE pair_idx=" + pairIdx;
		ResultSet rs = pool.executeQuery(st);
		while (rs.next())
		{
			int idx = rs.getInt(1);
			int s1 = rs.getInt(4);
			int e1 = rs.getInt(5);					
			int s2 = rs.getInt(2);
			int e2 = rs.getInt(3);
			Hit h = new Hit();
			h.idx = idx;
			h.query.start = s1;
			h.query.end = e1;					
			h.query.grpIdx = rs.getInt(6);					
			h.target.start = s2;
			h.target.end = e2;
			h.target.grpIdx = rs.getInt(7);					
			ret.add(h);
		}
		rs.close();
		
		return ret;
	}
	/*************************************************
	 * CAS500 this was in run(); some of this is obsolete
	 */
	private void addProps() {
		try {
		/* CAS501
		mProps.uploadPairProps( pool, p1.getIdx(), p2.getIdx(), pairIdx,
					new String[] { 		
						"topn_and", "topn", "topn_maxwindow", "use_genemask",
						"gene_pad",	"do_clustering", "joinfact", "merge_blocks", 
						"mindots", "keep_best", "mindots_keepbest",
						"mingap1", "mingap1_cb", "mingap2", "search_factor",
						"corr1_A", "corr1_B", "corr1_C", "corr1_D",	
						"corr2_A", "corr2_B", "corr2_C", "corr2_D",	
						"avg1_A", "avg2_A",  "maxgap1", "maxgap2",
						"do_unanchored", "unanch_join_dist_bp", "subblock_multiple", 
						"do_bes_fixing", "checkctg1", "checkgrp1", "checkgrp2",
						"maxjoin_cb", "maxjoin_bp", "do_synteny","nucmer_only","promer_only",
						"blat_args","nucmer_args","promer_args"});
		*/
		// Obsolete unless default changed in SyProp
		HitBin.initKeepTypes();
		if (mProps.getProperty("keep_gene_gene").equals("1")) // false
		{
			HitBin.addKeepTypes(HitType.GeneGene);	
		}
		if (mProps.getProperty("keep_gene").equals("1")) // false
		{
			HitBin.addKeepTypes(HitType.GeneGene);	
			HitBin.addKeepTypes(HitType.GeneNonGene);	
		}

		Utils.initHist("TopNHist1" + GeneType.Gene, 3,6,10,25,50,100);
		Utils.initHist("TopNHist1" + GeneType.NonGene, 3,6,10,25,50,100);
		Utils.initHist("TopNHist1" + GeneType.NA, 3,6,10,25,50,100);
		Utils.initHist("TopNHist1Accept", 3,6,10,25,50,100);
		Utils.initHist("TopNHistTotal1", 3,6,10,25,50,100);
		Utils.initHist("TopNHist2" + GeneType.Gene, 3,6,10,25,50,100);
		Utils.initHist("TopNHist2" + GeneType.NonGene, 3,6,10,25,50,100);
		Utils.initHist("TopNHist2" + GeneType.NA, 3,6,10,25,50,100);
		Utils.initHist("TopNHist2Accept", 3,6,10,25,50,100);
		Utils.initHist("TopNHistTotal2", 3,6,10,25,50,100);
		
		if (p1.isSeq()) 
			  p1.setGrpGeneParams(mProps.getInt("max_cluster_gap"),mProps.getInt("max_cluster_size"));
		if (p2.isSeq()) 
			  p2.setGrpGeneParams(mProps.getInt("max_cluster_gap"),mProps.getInt("max_cluster_size"));
		}
		catch (Exception e) {ErrorReport.print("adding properties for Anchors");}
	}
	/*
	 * Sub-classes
	 */
	public class AnchorFile 
	{
		File mFile;
		FileType mType1, mType2;
		
		public AnchorFile(File file, String btype1, String btype2) throws Exception
		{
			mFile = file;
			mType1 = parseFileType(btype1);
			mType2 = parseFileType(btype2);
		}
		
		public FileType parseFileType(String typename) throws Exception
		{
			for (FileType ft : FileType.values())
				if (ft.toString().equalsIgnoreCase(typename))
					return ft;

			throw(new Exception("Unable to parse file type " + typename));
		}
	}
}
