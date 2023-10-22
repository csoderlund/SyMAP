package backend.anchor2;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.TreeSet;

import backend.AnchorMain;
import backend.Constants;
import backend.Utils;
import database.DBconn2;
import symap.manager.Mpair;
import symap.manager.Mproject;
import symap.Globals;
import util.Cancelled;
import util.ErrorReport;
import util.ProgressDialog;
import util.Utilities;

/*******************************************************
 * Reads a Mummer files, create cluster hits and save.
 * -Reads and process one file at a time, so it is important that no chromosome gets split over multiple files!!!
 * -All database load/save are from here. Everything else is GrpPair based.
 * 
 * -s created output per file
 * -tt outputs files of results
 * -dd for other
 */
public class AnchorMain2 {
	private static final int T=Arg.T, Q=Arg.Q;
	
	private AnchorMain mainObj=null;
	
	// accessed from GrpPair
	protected DBconn2 tdbc2;
	protected ProgressDialog plog;
	protected Mpair mPair;
	protected Mproject mProj1, mProj2;
	
	protected int cntG2=0, cntG1=0, cntG0=0, cntClusters=0; // GrpPair.saveClusterHits
	protected int cntG2Filter=0, cntG1Filter=0, cntG0Filter=0, cntPileFilter=0;   // GrpPair,GrpPairGene,GrpPairPrune
	
	protected PrintWriter fhOutHit=null, fhOutCl=null, fhOutGene=null, fhOutGP=null, fhOutRep=null;
	protected String fileName="";
	
	// Main data structures; all genes; the ChrT-ChrQ genes with hits are transferred to GrpPair.
	private TreeMap <Integer, GeneTree> qGrpGeneMap = new TreeMap <Integer, GeneTree> (); // key grpIdxQ; Mproj1
	private TreeMap <Integer, GeneTree> tGrpGeneMap = new TreeMap <Integer, GeneTree> (); // key grpIdxT; Mproj2
	private TreeSet <String> donePair = new TreeSet <String> (); // key grpIdx2+"-"+grpIdx1; ensure that chr-pair not in mult files
	
	protected String proj1Name, proj2Name;
	private boolean bSuccess=true;
	
	// created and processed per file
	private TreeMap <String, GrpPair>  pairMap = new TreeMap <String, GrpPair> ();  // key chrT-chrQ

	/*********************************/
	public AnchorMain2(AnchorMain mainObj) {
		this.mainObj = mainObj;
		this.plog = mainObj.plog;
		this.mPair = mainObj.mp;
		
		Arg.g0MinBases = mPair.getG0Match(Mpair.FILE);
		Arg.gnMinExon = mPair.getGexonMatch(Mpair.FILE);
		Arg.gnMinIntron = mPair.getGintronMatch(Mpair.FILE);
		Arg.EE = mPair.isEEpile(Mpair.FILE);
		Arg.EI = mPair.isEIpile(Mpair.FILE);
		Arg.En = mPair.isEnpile(Mpair.FILE);
		Arg.II = mPair.isIIpile(Mpair.FILE);
		Arg.In = mPair.isInpile(Mpair.FILE);
		
		tdbc2 = new DBconn2("Anchors-"+DBconn2.getNumConn(), mainObj.dbc2);
	}
	/*********************************/
	public boolean run(Mproject pj1, Mproject pj2, File dh) throws Exception {
	try {
		this.mProj1=pj1; this.mProj2=pj2; // query, target
		proj1Name=mProj1.getDisplayName(); 
		proj2Name=mProj2.getDisplayName(); 
		long totTime = Utils.getTime();
		
	// read anno: create master copies for genes in a TreeMap per chr
		loadAnno(T, mProj2, tGrpGeneMap); 	if (!bSuccess) return false;
		loadAnno(Q, mProj1, qGrpGeneMap); 	if (!bSuccess) return false;
		
		if (Arg.PRT_STATS) Utils.prtIndentMsgFile(plog, 0, "                                "); 
		prtFileOpen();
		
	// for each file: read file, build clusters, save results to db
		processFiles(dh); 					if (!bSuccess) return false; 
			
	// finish
		prtFileClose();
		clearTree();
		
		Utils.prtIndentMsgFile(plog, 1, "Final totals:                                                   ");	
		
		String msg1 = String.format("%,12d Clusters  Both genes %,8d   One gene %,8d   No gene %,8d",
				cntClusters, cntG2, cntG1, cntG0);
		Utils.prtIndentMsgFile(plog, 1, msg1);	
		plog.msgOnly(String.format("%,10d Clusters", cntClusters));
		
		int total = cntG2Filter+cntG1Filter+cntG0Filter+cntPileFilter;
		
		String msg2 = String.format("%,12d Filtered  Both genes %,8d   One gene %,8d   No gene %,8d   Pile hits %,d",
				total, cntG2Filter, cntG1Filter, cntG0Filter, cntPileFilter);
		Utils.prtIndentMsgFile(plog, 1, msg2);	
		
		if (Arg.PRT_STATS) Utils.prtTimeMemUsage(plog, "   Finish ", totTime); // also writes in AnchorMain after a few more comps
		
		return bSuccess;
	}
	catch (Exception e) {ErrorReport.print(e, "create clustered hits"); return false;}
	}
	
	/**************************************************************************
	// Step1: All anno read at once, but loaded in separate GeneTrees per group (chr) 
	 ************************************************************************/
	private void loadAnno(int X, Mproject mProj, TreeMap <Integer, GeneTree> grpGeneMap) {
		if (!mProj.hasGenes()) return;
		
		try {
		// Genes
			HashMap <Integer, Gene> geneMap = new HashMap <Integer, Gene> ();
			int avgGeneLen=0, maxGeneLen=0;
			
			// get all groups for project and query for the genes
			TreeMap <Integer, String> grpIdxNameMap = mProj.getGrpIdxMap();
			String gx = null;
			for (int idx : grpIdxNameMap.keySet()) {
				if (gx==null) gx = idx+"";
				else gx += "," + idx;
			}
			ResultSet rs = tdbc2.executeQuery(
					"SELECT idx, grp_idx, start, end, strand, tag FROM pseudo_annot "
					+ " where type='gene' and grp_idx in (" + gx + ")");
			
			while (rs.next()){
				int geneIdx = rs.getInt(1);
				int grpIdx = rs.getInt(2);
				int s = rs.getInt(3);
				int e = rs.getInt(4);
				String strand = rs.getString(5);
				String tag = rs.getString(6);
				tag = tag.substring(0, tag.indexOf("("));
				
				Gene geneObj = new Gene(X, geneIdx, grpIdxNameMap.get(grpIdx), s, e, strand, tag);
				geneMap.put(geneIdx, geneObj);
				
				int length = (e-s+1);
				avgGeneLen += length;
				maxGeneLen = Math.max(maxGeneLen, length);		
				
				// Create new geneMap for this chromosome if needed
				if (!grpGeneMap.containsKey(grpIdx)) {
					if (!grpIdxNameMap.containsKey(grpIdx)) {
						prt("No groupIdx " + grpIdx + " Valid:");
						for (int idx : grpIdxNameMap.keySet()) prt(idx + " " + grpIdxNameMap.get(idx));
						die("cannot continue");
					}
					String title = Arg.side[X] + " " + grpIdxNameMap.get(grpIdx) + " (" + grpIdx + ")";
					grpGeneMap.put(grpIdx, new GeneTree(title));
				}
				// add to tree for this chromosome
				GeneTree treeObj = grpGeneMap.get(grpIdx);
				treeObj.addGene(s, geneObj);
			}
			rs.close();
			
			if (geneMap.size()==0) {
				plog.msg(mProj.getDisplayName() + " - no genes");
				return;
			}
			if (failCheck()) return;
			
		// Exons
			rs = tdbc2.executeQuery("SELECT gene_idx, start, end FROM pseudo_annot "
					+ " where type='exon' and grp_idx in (" + gx + ")");
			
			while (rs.next()){
				int geneIdx = rs.getInt(1);
				if (geneIdx==0) continue;  // happened in Mus13
				
				int start = rs.getInt(2);
				int end = rs.getInt(3);
				
				if (geneMap.containsKey(geneIdx)) {
					Gene geneObj = geneMap.get(geneIdx);
					geneObj.addExon(start, end);
				}
				else die("No geneMap for " + geneIdx + " grpIdx " + gx);
			}
			rs.close();
			
			// g0 cluster hits are easier to understand if fixed sizes; defaults are set based on test plants and hsa
			avgGeneLen /= geneMap.size();
			if (avgGeneLen>Arg.minGeneLen) { // mammal
				Arg.useGeneLen[X]   = Arg.maxGeneLen;
				Arg.useIntronLen[X] = Arg.maxIntronLen;
			}
			else {							 // plant
				Arg.useGeneLen[X]   = Arg.minGeneLen;
				Arg.useIntronLen[X] = Arg.minIntronLen;
			}
			if (Arg.PRT_STATS) {
				String msg = String.format("%-10s Chrs %d   Genes %,d   AvgLen %,d   MaxLen %,d  (Use intron %,d  gene %,d)",
					mProj.getDisplayName(), grpGeneMap.size(), geneMap.size(), avgGeneLen, maxGeneLen, 
					Arg.useIntronLen[X], Arg.useGeneLen[X]);
				Utils.prtIndentMsgFile(plog, 1, msg); 
			}
			
			if (failCheck()) return;
			
		// Finish tree and genes
			for (GeneTree treeObj : grpGeneMap.values()) {
				treeObj.finish();
				
				Gene [] geneArr = treeObj.getSortedGeneArray();
				for (Gene gn : geneArr) {
					gn.sortExons(); // for binary search of hits		
				}
			}
			if (failCheck()) return;
		} 
		catch (Exception e) {ErrorReport.print(e, "load anno " + Arg.side[X]); bSuccess=false;}
	}
	/******************************************************
	 * Step 2: Each file is read and immediately processed and save
	 */	
	private void processFiles(File dh) {
	try {
		File[] fs = dh.listFiles();
		for (File f : fs) {
			if (!f.isFile()) continue;
			fileName = f.getName();
			if (!fileName.endsWith(Constants.mumSuffix))continue;
			
			long time = Utils.getTime();
		
		// load hits into grpPairs
			readMummer(f);							if (!bSuccess) return;
			
		// process all group pairs from file
			int nBin=0; 					// Can be multiple GrpPairs, all nBins need to be unique
			for (GrpPair gp : pairMap.values()) {	
				nBin = gp.run(nBin+1); 	if (nBin<0) {bSuccess=false; return;}
			}
			
			if (failCheck()) return;
			
			clearFileSpecific();
			
			if (Arg.PRT_STATS) Utils.prtTimeMemUsage(plog, "   Finish ", time);
		}
	}
	catch (Exception e) {ErrorReport.print(e, "analyze and save"); bSuccess=false;}
	}
	/*********************************************************************
	 // 0        1       2        3       4       5       6		7        8       9       10         11 12   13 14
	 // [tS1]    [tE1]  [qS2]    [qE2]    [tLEN] [qLEN] [% IDY] [% SIM] [% STP] [tChrLEN] [qChrLEN]  [FRM]   [TAGS]
     // 15491   15814  20452060 20451737   324     324    61.11   78.70   1.39   73840631  37257345  2   -3  chr1    chr3
	************************************************************************/
	private void readMummer(File fObj) {
	try {
		int hitNum=0, cntHitGeneT=0, cntHitGeneQ=0, cntLongHit=0;
		String line;
		GrpPair pairObj;
		
		BufferedReader reader = new BufferedReader ( new FileReader ( fObj ) ); 
		while ((line = reader.readLine()) != null) {	
			line = line.trim();
			if (line.equals("")) continue;
			if (!Character.isDigit(line.charAt(0))) {
				if (line.startsWith("NUCMER")) {
					Utilities.showWarningMessage("Algorithm 2 does not process NUCmer files");
					reader.close(); bSuccess=false; return;
				}
				continue;
			}
			
			String[] tok = line.split("\\s+");
			if (tok.length < 13) {
				prt("Line: " + line); 
				Utilities.showWarningMessage("Incorrect MUMmer file - must have 13 columns");
				reader.close(); bSuccess=false; return;
			}
			int [] start = {Integer.parseInt(tok[0]), Integer.parseInt(tok[2])};
			int [] end =   {Integer.parseInt(tok[1]), Integer.parseInt(tok[3])};
			int [] len =   {Integer.parseInt(tok[4]), Integer.parseInt(tok[5])};
			int id =       (int) Math.round(Double.parseDouble(tok[6]));
			int sim =      (int) Math.round(Double.parseDouble(tok[7]));
			
			int tf = Integer.parseInt(tok[11]);
			int qf = Integer.parseInt(tok[12]);	

			String chrT = tok[13];
			String chrQ = tok[14];
			
			String strand = (start[Q] <= end[Q] ? "+" : "-") + "/" + (start[T] <= end[T] ? "+" : "-"); 	// copied from anchor1
			
			if (start[T]>end[T]) {int t=start[T]; start[T]=end[T]; end[T]=t;}
			if (start[Q]>end[Q]) {int t=start[Q]; start[Q]=end[Q]; end[Q]=t;}
			int [] f =  {tf, qf};
		
		// Get grpIdx using name
			int grpIdx1 = mProj1.getGrpIdxFromName(chrQ); if (grpIdx1==-1) die("No " + chrQ + " for " + proj1Name);
			int grpIdx2 = mProj2.getGrpIdxFromName(chrT); if (grpIdx2==-1) die("No " + chrT + " for " + proj2Name);
			
			int [] grpIdx = {grpIdx2, grpIdx1}; // Query is Mproj1; second set of MUMMER coords
			
		// Add hit to pairObj 
			String grp2grpkey=grpIdx2+"-"+grpIdx1;
				
			if (!pairMap.containsKey(grp2grpkey)) {
				if (donePair.contains(grp2grpkey)) die(chrQ + " and " + chrT + " occurs in multiple files");
				donePair.add(grp2grpkey);
				
				pairObj = new GrpPair(this, grpIdx2, chrT, grpIdx1, chrQ, plog);
				pairMap.put(grp2grpkey, pairObj);
			}
			else pairObj = pairMap.get(grp2grpkey);
			
		// Create hit
			hitNum++;
			Hit ht = new Hit(hitNum, id, sim, start, end, len, grpIdx, strand, f); 
			if (len[T]>Arg.maxGeneLen || len[Q]>Arg.maxGeneLen) cntLongHit++;
			pairObj.addHit(ht);
			
		// add hit to gene and vice versa
			if (qGrpGeneMap.size()>0 && qGrpGeneMap.containsKey(grpIdx1)) { // grp may have no genes
				GeneTree qGrpGeneObj = qGrpGeneMap.get(grpIdx1); 
			
				TreeSet <Gene> qSet = qGrpGeneObj.findOlapSet(start[Q], end[Q], true);
				for (Gene gn : qSet) {
					cntHitGeneQ++;
					pairObj.addGeneHit(Q, gn, ht);
				}
			}
			if (tGrpGeneMap.size()>0 && tGrpGeneMap.containsKey(grpIdx2)) {
				GeneTree tGrpGeneObj = tGrpGeneMap.get(grpIdx2); 
				TreeSet <Gene> tSet = tGrpGeneObj.findOlapSet(start[T], end[T], true);
				
				for (Gene gn : tSet) {
					cntHitGeneT++;
					pairObj.addGeneHit(T, gn, ht);
				}
			}	
		}
		reader.close();
		
		plog.msg(String.format("%,10d Load %s", hitNum, fileName));
		if (Arg.PRT_STATS) {
			String msg = String.format("%,10d Hits  %d PairMap    Gene Hits:  %s %,d   %s %,d   Long hits %,d", 
					hitNum,  pairMap.size(), proj2Name, cntHitGeneT, proj1Name,  cntHitGeneQ, cntLongHit);
			Utils.prtIndentMsgFile(plog, 1, msg); 
		}
	}
	catch (Exception e) {ErrorReport.print(e, "read file"); bSuccess=false;}
	}
	
	////////////////////////////////////////////////////////////////////////
	private void clearFileSpecific() {
		for (GrpPair p : pairMap.values()) {
			p.clear();
			p=null;
		}
		pairMap.clear();
		System.gc();
	}
	private void clearTree() {
		for (GeneTree gt : tGrpGeneMap.values()) gt.clear();
		for (GeneTree gt : qGrpGeneMap.values()) gt.clear();
		tGrpGeneMap.clear();
		qGrpGeneMap.clear();
	}
	protected boolean failCheck() {
		if (Cancelled.isCancelled() || mainObj.bInterrupt || !bSuccess) {
			bSuccess=false;
			return true; 
		}
		return false;
	}
	/////////////////////////////////////////////////////////////////////////
	private void prtFileOpen() {
	try {
		if (!Globals.TRACE) return;
		
		boolean doHit=false, doGenePair=false, doCluster=true, doGene=true, doPile=true;
		
		if (doPile)
			fhOutRep = new PrintWriter(new FileOutputStream("logs/zPiles.log", false));
		if (doHit) 
			fhOutHit = new PrintWriter(new FileOutputStream("logs/zHits.log", false));
		if (doCluster) 
			fhOutCl = new PrintWriter(new FileOutputStream("logs/zCluster.log", false));
		if (doGene) 
			fhOutGene = new PrintWriter(new FileOutputStream("logs/zGene.log", false));	
		if (doGenePair) 
			fhOutGP = new PrintWriter(new FileOutputStream("logs/zGenePair.log", false));
	}
	catch (Exception e) {ErrorReport.print(e, "save to file"); bSuccess=false;}
	}
	private void prtFileClose() {
	try {
		if (fhOutHit!=null)  fhOutHit.close();
		if (fhOutCl!=null)   fhOutCl.close();
		if (fhOutGene!=null) fhOutGene.close();
		if (fhOutGP!=null)   fhOutGP.close();
		if (fhOutRep!=null) fhOutRep.close();
	}
	catch (Exception e) {ErrorReport.print(e, "file close"); bSuccess=false;}
	}
	public String toResults() {
		String msg = "";
		for (GrpPair gp : pairMap.values()) msg += "\n" + gp.toResults();
		return "!!!AnchorMain " + msg + "\n";
	}
	public String toString() {return toResults();}
	private void prt(String msg) {System.out.println(msg);}
	private void die(String msg) {prt("*** Load Hits: " + msg); System.exit(0);}
}
