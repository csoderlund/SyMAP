package backend.anchor2;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.util.Arrays;
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
 * -Reads and process one MUMmer file at a time, so it is important that no chromosome gets split over multiple files!!!
 * -For each chr-chr pair, creates a GrpPair object which performs on processing and DB saves
 * 		each GrpPair has genes and hits for the pair; a gene can be in multiple GrpPairs, but a hit will only be in one
 * 
 * -tt outputs files of results and extended results to terminal
 * I tried to add self-synteny, but it almost always did worse and was entering some hits twice...
 */
public class AnchorMain2 {
	private static final int T=Arg.T, Q=Arg.Q;
	protected static boolean bTrace = Globals.TRACE;
	
	private AnchorMain mainObj=null;
	
	// accessed from GrpPair
	protected DBconn2 tdbc2;
	protected ProgressDialog plog;
	protected Mpair mPair;
	protected Mproject mProj1, mProj2;
	protected int topN=0;
	
	protected long cntRawHits=0; // for logged output
	protected int cntG2=0, cntG1=0, cntG0=0, cntClusters=0; 			// GrpPair.saveClusterHits
	protected int cntG2Fil=0, cntG1Fil=0, cntG0Fil=0, cntPileFil=0;   	// GrpPair.createClusters
	protected int cntWS=0, cntRmEnd=0, cntRmLow=0;						// GrpPair.GrpPairGx 
	
	protected PrintWriter fhOutHit=null, fhOutCl=null, fhOutGene=null, fhOutPile=null;
	protected PrintWriter [] fhOutHPR = {null,null,null};
	protected String fileName="", argParams="";
	
	// Main data structures; all genes; the ChrT-ChrQ genes with hits are transferred to GrpPair.
	private TreeMap <Integer, GeneTree> qGrpGeneMap = new TreeMap <Integer, GeneTree> (); // key grpIdxQ; Mproj1
	private TreeMap <Integer, GeneTree> tGrpGeneMap = new TreeMap <Integer, GeneTree> (); // key grpIdxT; Mproj2
	private TreeSet <String> donePair = new TreeSet <String> (); // key grpIdx2+"-"+grpIdx1; ensure that chr-pair not in mult files
	
	protected String proj1Name, proj2Name;
	private boolean bSuccess=true;
	private int [] avgGeneLen = {0,0}, avgIntronLen = {0,0}, avgExonLen = {0,0}; // T,Q: 
	private double [] percentCov = {0,0};
	
	// created and processed per file
	private TreeMap <String, GrpPair>  grpPairMap = new TreeMap <String, GrpPair> ();  // key chrT-chrQ
	
	/*********************************/
	public AnchorMain2(AnchorMain mainObj) {
		Arg.setVB();
		
		this.mainObj = mainObj;
		this.plog = mainObj.plog;
		this.mPair = mainObj.mp;
		
		Arg.topN = mPair.getTopN(Mpair.FILE); 
		
		double exon = mPair.getExonScale(Mpair.FILE);
		double gene = mPair.getGeneScale(Mpair.FILE);
		double g0   = mPair.getG0Scale(Mpair.FILE);
		double len   = mPair.getLenScale(Mpair.FILE);
		argParams += Arg.setFromParams(exon, gene, g0, len);
		
		Arg.pEE = mPair.isEEpile(Mpair.FILE);
		Arg.pEI = mPair.isEIpile(Mpair.FILE);
		Arg.pEn = mPair.isEnpile(Mpair.FILE);
		Arg.pII = mPair.isIIpile(Mpair.FILE);
		Arg.pIn = mPair.isInpile(Mpair.FILE);
		
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
		if (Arg.VB) Utils.prtIndentMsgFile(plog, 1, "Load genes for Algo2"); else Globals.rprt("Load genes for Algo2");
		loadAnno(T, mProj2, tGrpGeneMap); 	if (!bSuccess) return false;
		loadAnno(Q, mProj1, qGrpGeneMap); 	if (!bSuccess) return false;
		argParams += Arg.setLenFromGenes(avgIntronLen, avgGeneLen, percentCov);
		
		prtFileOpen();
		
	// for each file: read file, build clusters, save results to db
		int nfile = processFiles(dh); 					if (!bSuccess) return false; 	
			
	// finish
		prtFileClose();
		clearTree();
		
		Globals.rclear();
		Utils.prtIndentMsgFile(plog, 1, String.format("Final totals    Raw hits %,d    Files %,d", cntRawHits, nfile));	
		
		String msg1 = String.format("Clusters   Both genes %,9d   One gene %,9d   No gene %,9d",  cntG2, cntG1, cntG0);
		Utils.prtNumMsgFile(plog, cntClusters, msg1);	
		
		int total = cntG2Fil+cntG1Fil+cntG0Fil;
		String msg2 = String.format("Filtered   Both genes %,9d   One gene %,9d   No gene %,9d   Pile hits %,d",
				cntG2Fil, cntG1Fil, cntG0Fil, cntPileFil);
		Utils.prtNumMsgFile(plog, total, msg2);
		if (Arg.WRONG_STRAND_PRT) Utils.prtNumMsgFile(plog, cntWS, "Wrong strand");
		
		if (bTrace) Globals.prt(String.format("Wrong strand %,d  Rm End %,d  Rm Low %,d", cntWS, cntRmEnd, cntRmLow));
		if (Arg.TRACE) Utils.prtTimeMemUsage(plog, "   Finish ", totTime); // also writes in AnchorMain after a few more comps
		
		return bSuccess;
	}
	catch (Exception e) {ErrorReport.print(e, "create clustered hits"); return false;}
	}
	
	/**************************************************************************
	// Step1: All anno read at once, but loaded in separate GeneTrees per group (chr) 
	 * Average and median stats computed for Arg settings
	 ************************************************************************/
	private void loadAnno(int X, Mproject mProj, TreeMap <Integer, GeneTree> grpGeneMap) {
		if (!mProj.hasGenes()) return;
		
		try {
		// Genes
			HashMap <Integer, Gene> geneMap = new HashMap <Integer, Gene> ();
			
			// get all groups for project
			TreeMap <Integer, String> grpIdxNameMap = mProj.getGrpIdxMap();
			String gx = null;
			for (int idx : grpIdxNameMap.keySet()) {
				if (gx==null) gx = idx+"";
				else gx += "," + idx;
			}
			// query for the all groups genes
			ResultSet rs = tdbc2.executeQuery(
					"SELECT idx, grp_idx, start, end, strand, tag FROM pseudo_annot "
					+ " where type='gene' and grp_idx in (" + gx + ") order by idx" );
			
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
				
				// Create new geneMap for this chromosome if needed
				if (!grpGeneMap.containsKey(grpIdx)) {
					if (!grpIdxNameMap.containsKey(grpIdx)) die("No groupIdx " + grpIdx);
						
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
			
			// for avg values
			int nGene = geneMap.size(), nExon=0, nIntron=0;
			int minGene=Integer.MAX_VALUE, minExon=Integer.MAX_VALUE, minIntron=Integer.MAX_VALUE;
			int maxGene=0, maxExon=0, maxIntron=0;
			long sumGene=0, sumExon=0, sumIntron=0;
			int lastGeneIdx=0, lastExonEnd=0;
			
		// Exons - query for all exons
			rs = tdbc2.executeQuery("SELECT gene_idx, start, end FROM pseudo_annot "
					+ " where type='exon' and grp_idx in (" + gx + ") order by grp_idx, gene_idx, start");
		
			while (rs.next()){
				int geneIdx = rs.getInt(1);
				if (geneIdx==0) continue;  // happened in Mus13
				if (!geneMap.containsKey(geneIdx)) die("No geneMap for " + geneIdx + " grpIdx " + gx);
				
				int start = rs.getInt(2);
				int end = rs.getInt(3);
				
				Gene geneObj = geneMap.get(geneIdx);
				geneObj.addExon(start, end);
				
				// exon and intron stats
				int len = end-start+1;
				sumExon += len;
				if (len>maxExon) maxExon = len;
				if (len<minExon) minExon = len;
				nExon++;
				
				if (geneIdx==lastGeneIdx) {
					len = start-lastExonEnd-1;
					if (len>0) {
						sumIntron += len;
						if (len>maxIntron) maxIntron = len;
						if (len<minIntron) minIntron = len;
						nIntron++;
					}
				}
				else lastGeneIdx = geneIdx;
				
				lastExonEnd = end;
			}
			rs.close();
			
		// Finish tree and genes
			for (GeneTree treeObj : grpGeneMap.values()) {
				treeObj.finish();
				
				Gene [] geneArr = treeObj.getSortedGeneArray();
				for (Gene gn : geneArr) {
					gn.sortExons();
					
					sumGene += gn.gLen;
					if (gn.gLen<minGene) minGene = gn.gLen;
					if (gn.gLen>maxGene) maxGene = gn.gLen;
				}
			}
			avgGeneLen[X]   = (int) Math.round((double)sumGene/(double)nGene); 
			avgExonLen[X]   = (int) Math.round((double)sumExon/(double)nExon); 
			avgIntronLen[X] = (int) Math.round((double)sumIntron/(double)nIntron); 
			percentCov[X] 	= ((double)sumExon/(double)(sumGene))*100.0;
			
			if (Arg.VB) {
				String msg = String.format("%-10s [Avg Min Max] [Gene %5s %3s %5s] [Intron %5s %3s %5s] [Exon %5s %3s %5s] Cov %4.1f%s",
				  mProj.getDisplayName(),
				  Utilities.kText(avgGeneLen[X]),   Utilities.kText(minGene),   Utilities.kText(maxGene), 
				  Utilities.kText(avgIntronLen[X]), Utilities.kText(minIntron), Utilities.kText(maxIntron),
				  Utilities.kText(avgExonLen[X]),   Utilities.kText(minExon),   Utilities.kText(maxExon),  percentCov[X], "%");
				Utils.prtIndentMsgFile(plog, 2, msg);
			}
			else {
				String msg = String.format("%-10s [Avg Gene %5s Intron %5s Exon %5s] Cov %4.1f%s",
					mProj.getDisplayName(), Utilities.kText(avgGeneLen[X]),  
					Utilities.kText(avgIntronLen[X]), Utilities.kText(avgExonLen[X]), percentCov[X], "%");
				Globals.rprt(msg);
			}
		} 
		catch (Exception e) {ErrorReport.print(e, "load anno " + Arg.side[X]); bSuccess=false;}
	}
	
	/******************************************************
	 * Step 2: Each file is read and immediately processed and save
	 */	
	private int processFiles(File dh) { 
	try {
		int nfile=0;
		File[] fs = dh.listFiles();
		Arrays.sort(fs); 			
		for (File f : fs) {
			if (!f.isFile() || f.isHidden()) continue; //  macOS add ._ files in tar; CAS576 add isHidden
			fileName = f.getName();
			if (!fileName.endsWith(Constants.mumSuffix))continue;
			nfile++;
			long time = Utils.getTime();
		
		// load hits into grpPairs
			readMummer(f);							
			
		// process all group pairs from file
			for (GrpPair gp : grpPairMap.values()) {	
				bSuccess = gp.buildClusters(); 	if (!bSuccess) return nfile;
			}
			
			if (failCheck()) return nfile;
			
			clearFileSpecific();
			
			if (Arg.TRACE) Utils.prtTimeMemUsage(plog, "   Finish ", time);
		}
		return nfile;				
	}
	catch (Exception e) {ErrorReport.print(e, "analyze and save"); bSuccess=false; return 0;}
	}
	/*********************************************************************
	 // 0        1       2        3       4       5       6		7        8       9       10         11 12   13 14
	 // [tS1]    [tE1]  [qS2]    [qE2]    [tLEN] [qLEN] [% IDY] [% SIM] [% STP] [tChrLEN] [qChrLEN]  [FRM]   [TAGS]
     // 15491   15814  20452060 20451737   324     324    61.11   78.70   1.39   73840631  37257345  2   -3  chr1    chr3
	************************************************************************/
	private void readMummer(File fObj) {
	try {
		int hitNum=0, hitEQ=0, hitNE=0, cntLongHit=0;
		int [] cntHitGene = {0,0};
		String line;
		GrpPair pairObj;
		int cntErr=0, cntHits=0, cntNoChr=0;
		TreeSet <String> noChrSetQ = new TreeSet <String> ();
		TreeSet <String> noChrSetT = new TreeSet <String> ();
		
		BufferedReader reader = new BufferedReader ( new FileReader ( fObj ) ); 
		while ((line = reader.readLine()) != null) {	
			line = line.trim();
			if (line.equals("") || !Character.isDigit(line.charAt(0))) continue;
			
			cntHits++;
			String[] tok = line.split("\\s+");
			if (tok.length<13) { 								
				plog.msg("*** Missing values, only " + tok.length + " must be >=13                       ");
				plog.msg("  Line: " + line);
				plog.msg("  Line " + cntHits + " of file " + fObj.getAbsolutePath());
				cntErr++;
				if (cntErr>5) Globals.die("Too many errors in " + fObj.getAbsolutePath());
				continue;
			}
			int poffset = (tok.length > 13 ? 2 : 0); // nucmer vs. promer
			
			int [] start = {Integer.parseInt(tok[0]), Integer.parseInt(tok[2])};
			int [] end =   {Integer.parseInt(tok[1]), Integer.parseInt(tok[3])};
			int [] len =   {Integer.parseInt(tok[4]), Integer.parseInt(tok[5])};
			int id =       (int) Math.round(Double.parseDouble(tok[6]));
			int sim = 	   (poffset==0) ? 0 : (int) Math.round(Double.parseDouble(tok[7]));
			
			int [] f =  {0, 0}; // frame; in Hit but not used
			if (poffset==2) {
				f[T] = Integer.parseInt(tok[11]);
				f[Q] = Integer.parseInt(tok[12]);	
			}
			String [] chr = {"",""};
			chr[T] = tok[11+poffset];
			chr[Q] = tok[12+poffset]; 
			
		// Get grpIdx using name
			int [] grpIdx = {0,0};
			grpIdx[Q] = mProj1.getGrpIdxFromFullName(chr[Q]); // proj1 is Q, 2nd set of coords (Q==1, T==0)
			if (grpIdx[Q]==-1) { 									
				if (!noChrSetQ.contains(chr[Q])) noChrSetQ.add(chr[Q]);
				cntNoChr++; continue;
			}
			grpIdx[T] = mProj2.getGrpIdxFromFullName(chr[T]); 
			if (grpIdx[T]==-1) {									
				if (!noChrSetT.contains(chr[T])) noChrSetQ.add(chr[T]);
				cntNoChr++; continue;
			}
			
			String strand = (start[Q] <= end[Q] ? "+" : "-") + "/" + (start[T] <= end[T] ? "+" : "-"); 	// copied from anchor1
			if (Arg.isEqual(strand)) hitEQ++; else hitNE++;
			
			if (start[T]>end[T]) {int t=start[T]; start[T]=end[T]; end[T]=t;} 
			if (start[Q]>end[Q]) {int t=start[Q]; start[Q]=end[Q]; end[Q]=t;}
			
		// Add hit to pairObj 
			String grp2grpkey=grpIdx[Q]+"-"+grpIdx[T]; 
				
			if (!grpPairMap.containsKey(grp2grpkey)) {
				if (donePair.contains(grp2grpkey)) die(chr[Q] + " and " + chr[T] + " occurs in multiple files");
				donePair.add(grp2grpkey);
				
				pairObj = new GrpPair(this, grpIdx[T], proj2Name, chr[T], grpIdx[Q], proj1Name, chr[Q], plog);
				grpPairMap.put(grp2grpkey, pairObj);
			}
			else pairObj = grpPairMap.get(grp2grpkey);
			
		// Create hit
			hitNum++;
			Hit ht = new Hit(hitNum, id, sim, start, end, len, grpIdx, strand, f); 
			pairObj.addHit(ht);
			
		// add hit to gene and vice versa;
			if (qGrpGeneMap.size()>0 && qGrpGeneMap.containsKey(grpIdx[Q])) { // grp may have no genes
				GeneTree qGrpGeneObj = qGrpGeneMap.get(grpIdx[Q]); 
				TreeSet <Gene> qSet = qGrpGeneObj.findOlapSet(start[Q], end[Q], true);
			
				for (Gene gn : qSet) {
					cntHitGene[Q]++;
					pairObj.addGeneHit(Q, gn, ht);
				}
			}
			if (tGrpGeneMap.size()>0 && tGrpGeneMap.containsKey(grpIdx[T])) {
				GeneTree tGrpGeneObj = tGrpGeneMap.get(grpIdx[T]); 
				TreeSet <Gene> tSet = tGrpGeneObj.findOlapSet(start[T], end[T], true);
			
				for (Gene gn : tSet) {
					cntHitGene[T]++;
					pairObj.addGeneHit(T, gn, ht);
				}
			}	
			if (len[T]>Arg.mamGeneLen || len[Q]>Arg.mamGeneLen) cntLongHit++;
		} // finish reading file
		reader.close();
		
		/* ---------- Finish ----------- */
		Globals.rclear();
		String x =  (cntLongHit>0) ? String.format("Long Hits %,d", cntLongHit) : "";
		String msg = String.format("Load %s  Hits %,d (EQ %,d NE %,d) %s", fileName, hitNum, hitEQ, hitNE, x);
		if (Arg.VB)  Utils.prtIndentMsgFile(plog, 1, msg); else Globals.rprt(msg);	
		
		cntRawHits += hitNum;
		
		if (cntNoChr>0) { 
			if (noChrSetQ.size()>0) {
				msg = proj1Name + " (" + noChrSetQ.size() + "): ";
				for (String c : noChrSetQ) msg += c + " ";
				Utils.prtNumMsgFile(plog, cntNoChr, "lines without loaded sequence:  " + msg);
			}
			if (noChrSetT.size()>0) {
				msg = proj2Name + " (" + noChrSetT.size() + "): ";
				for (String c : noChrSetT) msg += c + " ";
				Utils.prtNumMsgFile(plog, cntNoChr, "lines without loaded sequence:  " + msg);
			}
		}
							
		if (Arg.TRACE || bTrace) {
			msg = String.format("SubHit->genes %s %,d   %s %,d", proj2Name, cntHitGene[Arg.T], proj1Name,  cntHitGene[Arg.Q]);
			Utils.prtIndentMsgFile(plog, 2, msg); 
		}
	}
	catch (Exception e) {ErrorReport.print(e, "read file"); bSuccess=false;}
	}
	
	////////////////////////////////////////////////////////////////////////
	private void clearFileSpecific() {
		for (GrpPair p : grpPairMap.values()) {
			p.clear();
			p=null;
		}
		grpPairMap.clear();
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
			Globals.prt("Cancelling operation");
			bSuccess=false;
			return true; 
		}
		return false;
	}
	/////////////////////////////////////////////////////////////////////////
	// the 1st 4 are written from GrpPair when done, the last set is written from GrpPairGx before Pile.
	private void prtFileOpen() {
	try {
		if (Globals.INFO || Globals.TRACE) Utils.prtIndentMsgFile(plog, 0, argParams + "\n");
		if (!Globals.TRACE) return;
		
		boolean doHit=true, doGenePair=true, doCluster=true, doGene=true, doPile=true;
		
		if (doPile) 
			fhOutPile = new PrintWriter(new FileOutputStream("logs/zPiles.log", false));
		if (doHit) 
			fhOutHit = new PrintWriter(new FileOutputStream("logs/zHits.log", false));
		if (doCluster) 
			fhOutCl = new PrintWriter(new FileOutputStream("logs/zCluster.log", false));
		if (doGene) 
			fhOutGene = new PrintWriter(new FileOutputStream("logs/zGene.log", false));	
		if (doGenePair) { // before final clusters, written in GrpPairGx 
			fhOutHPR[2] = new PrintWriter(new FileOutputStream("logs/zHprG2.log", false));
			fhOutHPR[1] = new PrintWriter(new FileOutputStream("logs/zHprG1.log", false));
			fhOutHPR[0] = new PrintWriter(new FileOutputStream("logs/zHprG0.log", false));
			fhOutHPR[2].print(argParams);
			fhOutHPR[1].print(argParams);
			fhOutHPR[0].print(argParams);
		}
	}
	catch (Exception e) {ErrorReport.print(e, "save to file"); bSuccess=false;}
	}
	private void prtFileClose() {
	try {
		if (fhOutHit!=null)  fhOutHit.close();
		if (fhOutCl!=null)   fhOutCl.close();
		if (fhOutGene!=null) fhOutGene.close();
		if (fhOutPile!=null) fhOutPile.close();
		for (int i=0; i<2; i++)
			if (fhOutHPR[i]!=null)   fhOutHPR[i].close();
	}
	catch (Exception e) {ErrorReport.print(e, "file close"); bSuccess=false;}
	}
	public String toResults() {
		String msg = "";
		for (GrpPair gp : grpPairMap.values()) msg += "\n" + gp.toResults();
		return "!!!AnchorMain " + msg + "\n";
	}
	public String toString() {return toResults();}
	
	private void die(String msg) {symap.Globals.die("Load Hits: " + msg);}
}
