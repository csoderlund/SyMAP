package backend.anchor2;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;

import backend.Utils;
import database.DBconn2;
import symap.Globals;
import symap.manager.Mpair;
import util.ErrorReport;
import util.ProgressDialog;

/**************************************************************
 * Performs all processing for a chrT-chrQ; save results to DB; frees memory
 */
public class GrpPair {
	static public final int T=Arg.T, Q=Arg.Q;

	// Args
	protected String tChr="", qChr="";
	protected int tGrpIdx=-1, qGrpIdx=-1;
	protected ProgressDialog plog;
	protected AnchorMain2 anchorObj;
	protected Mpair mPair;
	
	// Only genes from this tChr/qChr that have hits; built when the MUMmer file is read; used by GrpPairGene
	protected TreeMap <Integer, Gene> tGeneMap = new TreeMap <Integer, Gene> (); // key: geneIdx
	protected TreeMap <Integer, Gene> qGeneMap = new TreeMap <Integer, Gene> (); // key: geneIdx
	
	// associated gene processing
	private GrpPairGene gpGeneObj;
			
	// From GrpPairGene; contains [tgeneIdx,qgeneIdx] for the hit; 0 if none; returned from GrpPairGene
	private HashMap <Integer, int []> binGeneMap = null;
			
	// Only hits between tChr-qChr; used by GrpPairGene
	protected ArrayList <Hit> gpHitList= new ArrayList <Hit> (); 

	// for DB and GrpPairPile
	protected ArrayList <HitCluster> gpClHitList= new ArrayList <HitCluster> ();
	
	private int arrLen=0, nBin=1;
	private boolean bSuccess=true;
	private int cntPseudoN=0;
	
	protected GrpPair(AnchorMain2 anchorObj, int grpIdxT, String chrT, int grpIdxQ, String chrQ, ProgressDialog plog) { // probably change to chr later
		this.anchorObj = anchorObj;
		mPair = anchorObj.mPair;
		
		this.tChr = chrT;
		this.tGrpIdx = grpIdxT;
		
		this.qChr = chrQ;
		this.qGrpIdx = grpIdxQ;
		
		this.plog = plog;
	}
	// the following two are populated during read mummer
	protected void addHit(Hit ht) {
		gpHitList.add(ht);
	}
	protected void addGeneHit(int X, Gene gn, Hit ht) {
	try {
		TreeMap <Integer, Gene> geneMap = (X==T) ?  tGeneMap : qGeneMap;
		
		Gene cpGn;
		if (geneMap.containsKey(gn.geneIdx)) cpGn = geneMap.get(gn.geneIdx);
		else {
			cpGn = (Gene) gn.copy();
			geneMap.put(cpGn.geneIdx, cpGn);
		}
		boolean bExon = cpGn.addHit(ht); 
		
		ht.addGene(X, cpGn, bExon);	  
	}
	catch (Exception e) {ErrorReport.print(e, "GrpPair: clone gene, add hit"); System.exit(-1);}	
	}
	
	/////////////////////////////////////////////////////////////////////////////
	public int run(int nextBin) {	
		if (Arg.PRT_STATS) Utils.prtIndentMsgFile(plog, 1, "Compute clusters for " + tChr + " and " + qChr);
		
		nBin = nextBin;
		arrLen = gpHitList.size();
		
		// Set g2 and g1 bins for clusters
		gpGeneObj = new GrpPairGene(this);
		binGeneMap = gpGeneObj.run(nBin); 	if (binGeneMap==null) bSuccess=false; 
											if (failCheck()) return -1; 
		nBin = nBin + binGeneMap.size() + 1;

		// Create g0 bins for clusters
		createPseudoGeneBins();	  			if (failCheck()) return -1; 		 	 

		// make clusters hits from bins 
		createClusterHits();  				if (failCheck()) return -1; 
		
		// Find piles in cluster hits
		GrpPairPile pileObj = new GrpPairPile(this);
		bSuccess = pileObj.run();			if (failCheck()) return -1;	
		
		// save to DB
		saveClusterHits(); 					if (failCheck()) return -1; 
		saveAnnoHits();						if (failCheck()) return -1; 
		
		if (Globals.TRACE) prtToFile();
		
		return nBin;
	}
	
	////////////////////////////////////////////////////////////////////////////////
	private void createPseudoGeneBins() {
		Hit.sortBySignByX(T, gpHitList); // SORT for createPseudoGeneBins(hitList)
		
		// g0 Clusters; create lists of g0-only
		ArrayList <Hit> eqHitList = new ArrayList <Hit> ();
		ArrayList <Hit> neHitList = new ArrayList <Hit> ();
		for (int r=0; r<arrLen; r++) {
			Hit ht = gpHitList.get(r);
			if (ht.bin>0 || ht.bEitherGene()) continue;
			
			if (gpHitList.get(r).isHitEQ) eqHitList.add(ht);
			else                          neHitList.add(ht);
		}
		createPseudoGeneBins(eqHitList);
		createPseudoGeneBins(neHitList);
		
		// g0 Singles
		int cntG0=0, cntF0=0;
		for (Hit ht : gpHitList) {
			if (ht.bin>0 || ht.bEitherGene()) continue;
		
			double score = Arg.baseScore(ht);
			
			if (score < Arg.g0MinBases) {
				ht.bDead=true;
				cntF0++;
			}
			else {
				ht.bin = (++nBin);
				cntG0++;
			}
		}
		anchorObj.cntG0Filter += cntF0;
		
		if (Arg.PRT_STATS) {
			String msg1 = String.format("%,10d g0 multi-hit   Single %,6d (filtered %,d)", 
					cntPseudoN, cntG0, cntF0);
			Utils.prtIndentMsgFile(plog, 1, msg1);
		}	
	}
	
	private void createPseudoGeneBins(ArrayList <Hit> hitList) {
	try {
		boolean isFW=true;	   // NW-SE, then NE-SW (NE-SW gets only a few, but its a flip)
		
		Hit.sortBySignByX(T, hitList); // SORT
		
		for (int d=0; d<2; d++) {
			
			for (int r=0; r<hitList.size()-1; r++) {
				Hit ht0 = hitList.get(r);
				
				for (int r1=r+1; r1<hitList.size(); r1++) {
					Hit ht1 = hitList.get(r1);
					if (ht1.bin>0) continue;
					
					int tDiff = Math.abs(ht1.start[T] - ht0.end[T]); 
					if (tDiff>Arg.useIntronLen[T]) break;  
					
					int qDiff = Arg.getDiff(Q, isFW, ht0, ht1);
					if (qDiff > Arg.useIntronLen[Q]) continue;
						
					if (ht0.bin==0) {
						ht0.bin = nBin++;
						cntPseudoN++;
					}
					ht1.bin = ht0.bin;
					break;
				}	
			}
			isFW=false;
		}
	}
	catch (Exception e) {ErrorReport.print(e, "GrpPair createPseudoGene");  bSuccess=false;}	
	}
	
	////////////////////////////////////////////////////////////////////////////////
	// Called after all bins are set
	private void createClusterHits() {
	try {
		// bins are not sequential, so must make intermediate structure
		HashMap <Integer, HitCluster> clMap = new HashMap <Integer, HitCluster> (); // key: bin
		int [] geneIdx = {0,0};
		int [] noGeneIdx = {0,0};
		int cHitNum=1;  // saved in pseudo_hits as hitNum, used for pseudo_annot_hits, rewritten in AnchorMain
		
		Hit.sortByX(Q, gpHitList);						// SORT 
		
	// gather each bin for cluster
		for (int h=0; h<arrLen; h++) {					
			Hit ht = gpHitList.get(h);
			if (ht.bin==0 || ht.bDead) continue;
				
			HitCluster clHit;
			if (!clMap.containsKey(ht.bin)) {
				geneIdx = binGeneMap.containsKey(ht.bin) ? binGeneMap.get(ht.bin) : noGeneIdx;
				clHit = new HitCluster(ht.bin, geneIdx);  // set geneIdxs
				clHit.cHitNum = cHitNum++;
				
				clMap.put(ht.bin, clHit);
			}
			else {
				clHit = clMap.get(ht.bin);
			}
			clHit.addSubHit(ht);
			ht.clNum = clHit.cHitNum;
		}
		
		gpGeneObj.setClBin(clMap);						// set CL# for genePairs for pseudo_annot_hits
		
	// Move to final list
		int cntF0=0, cntF1=0, cntF2=0;					
		for (HitCluster clHit : clMap.values()) {
			Gene tgene =  (clHit.geneIdx[T]>0) ? tGeneMap.get(clHit.geneIdx[T]) : null; // use geneIdxs
			Gene qgene =  (clHit.geneIdx[Q]>0) ? qGeneMap.get(clHit.geneIdx[Q]) : null;
			
			clHit.finishSubs(tgene, qgene);				// creates subhit query and target, tag
			
			if (clHit.nHit==1) {// already filtered as single hit
				gpClHitList.add(clHit); 
				continue; 
			}
			
			// Filter
			if (clHit.bBothGene) {
				double  tbases = Arg.baseScore(T, clHit);
				boolean btexon = clHit.bHitsExon(T);
				double  qbases = Arg.baseScore(Q, clHit);
				boolean bqexon = clHit.bHitsExon(Q);
				
				boolean bGoodT = (btexon) ? (tbases>Arg.gnMinExon) : (tbases>Arg.gnMinIntron);
				boolean bGoodQ = (bqexon) ? (qbases>Arg.gnMinExon) : (qbases>Arg.gnMinIntron);
				
				if (!bGoodT && !bGoodQ) { 
					clHit.bBaseFilter=true;
					cntF2++;
				}
			}
			else if (clHit.bEitherGene) {
				int ix = (tgene==null) ? Q : T;
				double  bases = clHit.sumLen[ix]*(clHit.pId/100.0);
				boolean bexon = clHit.bHitsExon(ix);
				boolean bGood = (bexon) ? (bases>Arg.gnMinExon) : (bases>Arg.gnMinIntron);
				if (!bGood) {
					clHit.bBaseFilter=true;
					cntF1++;
				}
			}
			else {
				double score = clHit.bLen*(clHit.pId/100.0);
				if (score < Arg.g0MinBases) {
					clHit.bBaseFilter=true;
					cntF0++;
				}
			}
			if (!clHit.bBaseFilter || Globals.TRACE) gpClHitList.add(clHit); 
		}
		anchorObj.cntG0Filter += cntF0;
		anchorObj.cntG1Filter += cntF1;
		anchorObj.cntG2Filter += cntF2;
		
		if (Arg.PRT_STATS) {
			int cnt = cntF2+cntF1+cntF0;
			String msg1 = String.format("%,10d Filtered multi-hits   g2 %,d   g1 %,d   g0 %,d", cnt, cntF2,  cntF1,  cntF0);
			Utils.prtIndentMsgFile(plog, 1, msg1);
		}	
	}
	catch (Exception e) {ErrorReport.print(e, "GrpPair createClusters");  bSuccess=false;}	
	}	
	///////////////////////////////////////////////////////////////////////
	private void saveClusterHits() {
	try {
		DBconn2 tdbc2 = anchorObj.tdbc2;
		int pairIdx = anchorObj.mPair.getPairIdx();
		int proj1Idx = anchorObj.mProj1.getIdx();
		int proj2Idx = anchorObj.mProj2.getIdx();
		
		int cntG2=0, cntG1=0, cntG0=0, countBatch=0, cntSave=0;
		tdbc2.executeUpdate("alter table pseudo_hits modify countpct integer");
					
		PreparedStatement ps = tdbc2.prepareStatement("insert into pseudo_hits "	
				+ "(hitnum, pair_idx, proj1_idx, proj2_idx, grp1_idx, grp2_idx,"
				+ "pctid, cvgpct, countpct, score, htype, gene_overlap,"
				+ "annot1_idx, annot2_idx, strand, start1, end1, start2, end2,"
				+ "query_seq, target_seq) "
				+ "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ");
		
		HitCluster.sortByX(Q, gpClHitList);		// SORT
		
		for (HitCluster cht : gpClHitList) {
			if (cht.bBaseFilter || cht.bPile) continue;
			
			int i=1;
			ps.setInt(i++, cht.cHitNum); // this gets reset in AnchorMain
			ps.setInt(i++, pairIdx);
			ps.setInt(i++, proj1Idx);
			ps.setInt(i++, proj2Idx);
			ps.setInt(i++, qGrpIdx);
			ps.setInt(i++, tGrpIdx);
			
			ps.setInt(i++, cht.pId);					// pctid Avg%Id
			ps.setInt(i++, cht.pSim); 				 	// cvgpct;   unsigned tiny int; now Avg%Sim
			ps.setInt(i++, (cht.clHitList.size())); 		// countpct; unsigned tiny int; now #SubHits
			ps.setInt(i++, cht.bLen); 					// score
			ps.setString(i++, cht.htype) ;				// htype EE, EI, etc
			ps.setInt(i++, cht.getGeneOverlap());		// geneOlap 0,1,2
			
			ps.setInt(i++, cht.geneIdx[Q]);
			ps.setInt(i++, cht.geneIdx[T]); 
			
			ps.setString(i++, cht.sign); 
			ps.setInt(i++, cht.cstart[Q]);
			ps.setInt(i++, cht.cend[Q]);
			ps.setInt(i++, cht.cstart[T]);
			ps.setInt(i++, cht.cend[T]);
			
			ps.setString(i++, cht.querySubHits);
			ps.setString(i++, cht.targetSubHits);
			
			ps.addBatch(); 
			countBatch++; cntSave++;
			if (countBatch==1000) {
				countBatch=0;
				ps.executeBatch();
				System.err.print("   " + cntSave + " loaded...\r"); 
			}
			
			if (cht.geneIdx[Q]>0 && cht.geneIdx[T]>0) cntG2++;
			else if (cht.geneIdx[Q]>0 || cht.geneIdx[T]>0) cntG1++;
			else cntG0++;
		}
		if (countBatch>0) ps.executeBatch();
		ps.close();
		
		anchorObj.cntG2 += cntG2;
		anchorObj.cntG1 += cntG1;
		anchorObj.cntG0 += cntG0;
		anchorObj.cntClusters += cntSave;
		
		String msg = String.format("%,10d %6s %6s Clusters   Both genes %,8d   One gene %,8d   No gene %,8d  ",  
				cntSave, qChr, tChr,  cntG2, cntG1, cntG0);
		Utils.prtIndentMsgFile(plog, 1, msg);
	}
	catch (Exception e) {ErrorReport.print(e, "save to database"); bSuccess=false;}
	}
	
	////////// Save all gene-hit pairs in pseudo_hits_annot /////////////////
	private void saveAnnoHits()  { 
	try {
		if (tGeneMap.size()==0 && qGeneMap.size()==0) return;
		
		if (Arg.PRT_STATS) Utils.prtIndentMsgFile(plog, 1, "Save hits to genes ");
		
		gpGeneObj.saveAnnoHits(anchorObj.tdbc2, qGrpIdx, tGrpIdx); 
	}
	catch (Exception e) {ErrorReport.print(e, "save annot hits "); bSuccess=false;}
	}
	///////////////////////////////////////////////////////////////////////
	private boolean failCheck() {
		if (anchorObj.failCheck() || !bSuccess) {
			bSuccess=false;
			return true; 
		}
		return false;
	}
	protected void clear() {
		for (Gene gn : tGeneMap.values()) gn.clear();
		for (Gene gn : qGeneMap.values()) gn.clear();
		tGeneMap.clear();
		qGeneMap.clear();
		
		gpHitList.clear();
		gpClHitList.clear();
		
		if (binGeneMap!=null) binGeneMap.clear();
		
		gpGeneObj.clear();
	}
	////////////////////////////////////////////////////////////////////////
	private void prtToFile() {
		try {
			String fName = anchorObj.fileName;
			// Hit
			if (anchorObj.fhOutHit!=null) {
				anchorObj.fhOutHit.println("\n>Bin " + toResults() + " " + fName);
				Hit.sortBySignByX(T, gpHitList);
				
				for (Hit ht : gpHitList) {
					anchorObj.fhOutHit.println("T" + ht.toResults());
				}
			}
			// Cluster
			if (anchorObj.fhOutCl!=null) {
				anchorObj.fhOutCl.println("\n>T Clusters " + fName);
				HitCluster.sortByX(T, gpClHitList);
				
				String chr = tChr+":" + qChr;
				for (HitCluster ht : gpClHitList) {
					anchorObj.fhOutCl.println("T" + ht.toResults(chr));
				}	
				
				anchorObj.fhOutCl.println("\n>Q Clusters " + fName);
				HitCluster.sortByX(Q, gpClHitList);
				for (HitCluster ht : gpClHitList) {
					anchorObj.fhOutCl.println("Q" + ht.toResults(chr));
				}
			}
			// Gene
			if (anchorObj.fhOutGene!=null) {
				anchorObj.fhOutGene.println("\n>T GENEs with hits " + fName );
				
				for (Gene gn : tGeneMap.values()) {
					String x = gn.toResults();
					if (x!="") anchorObj.fhOutGene.println("T" + gn.toResults());
				}
				
				anchorObj.fhOutGene.println("\n>Q GENEs with hits " + fName);
				
				for (Gene gn : qGeneMap.values()) {
					String x = gn.toResults();
					if (x!="") anchorObj.fhOutGene.println("Q" + gn.toResults());
				}
			}
			// GenePair
			if (anchorObj.fhOutGP!=null) {
				anchorObj.fhOutGP.println(">>>>Pair " + tChr + " " + qChr + " " + fName);
				if (gpGeneObj==null) anchorObj.fhOutGP.println("No grp genes");
				else                 gpGeneObj.prtAll(anchorObj.fhOutGP);
			}
			
		} catch (Exception e) {e.printStackTrace();	}
	}
	
	protected String toResults() {
		String tg = "    T genes: " + tGeneMap.size();
		String qg = "    Q genes: " + qGeneMap.size();
		return "GP " + tChr + " " + qChr + " " +  " Hits: " + gpHitList.size()  + tg + qg + " bin " + nBin;
	}
	public String toString() {return toResults();}
}
