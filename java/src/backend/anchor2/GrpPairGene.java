package backend.anchor2;

import java.io.PrintWriter;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;

import backend.Utils;
import database.DBconn2;
import symap.Globals;
import util.ErrorReport;
import util.ProgressDialog;

/******************************************************
 * Computes bins for genes for this GrpPair
 * Single hit clusters are filtered here, but may be further filtered in GrpPairPrune
 * 
 * g2
 * - Using hits, make list of all gene pairs
 * - For each gene pair, make PairHits grouping all hits for the pair
 * - Assign binN to each hit in a gene pair
 * g1
 * - for all genes from X
 * -	sort hits on Y (assume X coords are within intron distance)
 * -    make cluster from hits until the Y coords > intron
 */
public class GrpPairGene {
	private static final int T=Arg.T, Q=Arg.Q;

	private int nBin=1;
	
	// from GrpPair; 
	protected TreeMap <Integer, Gene> tGeneMap = null;  // key: geneIdx
	protected TreeMap <Integer, Gene> qGeneMap = null;  // key: geneIdx
	private ArrayList <Hit> gpHitList = null; 
	
	private GrpPair grpPairObj;
	private ProgressDialog plog;
	private String tChr, qChr;
	
	// return
	private HashMap <Integer, int []> binGeneMap = new HashMap <Integer, int []>  ();
	
	// Working; 1st add gene-gene, 2nd gene-none, 3rd used for saveAnnoHits
	private ArrayList <PairHits> genePairList = new ArrayList <PairHits> ();
		
	private boolean bSuccess=true;
	private int cntG2=0, cntG2Single=0, cntG2Fil=0, cntG1=0, cntG1Single=0, cntG1Fil=0;
	
	
	public GrpPairGene(GrpPair grpPairObj) { // created on GrpPair.run
		this.grpPairObj = grpPairObj;
		plog 		= grpPairObj.plog;
		
		tGeneMap 	= grpPairObj.tGeneMap;
		qGeneMap 	= grpPairObj.qGeneMap;
		gpHitList 	= grpPairObj.gpHitList;
	
		tChr 		= grpPairObj.tChr;
		qChr 		= grpPairObj.qChr;
		
		for (Gene gn : tGeneMap.values()) {gn.sortHits(T);}
		for (Gene gn : qGeneMap.values()) {gn.sortHits(T);}
	}
	// Called by GrpPair.run
	protected HashMap <Integer, int []> run(int bin) { 
	try {
		if (tGeneMap.size()==0 && qGeneMap.size()==0) return binGeneMap;
		
		this.nBin = bin;
		
		createGenePairList();		if (!bSuccess) return null;
		
		createG2();				    if (!bSuccess) return null;
		
		createG1(Q, T, qGeneMap);	if (!bSuccess) return null;
		
		createG1(T, Q, tGeneMap);	if (!bSuccess) return null;
		
		grpPairObj.anchorObj.cntG2Filter += cntG2Fil;
		grpPairObj.anchorObj.cntG1Filter += cntG1Fil;
		
		if (Arg.PRT_STATS) {
			String msg1 = String.format("%,10d g2 multi-hit   Single %,6d (filtered %,d)", 
					cntG2, cntG2Single, cntG2Fil);
			Utils.prtIndentMsgFile(plog, 1, msg1);
			String msg2 = String.format("%,10d g1 multi-hit   Single %,6d (filtered %,d)", 
					cntG1, cntG1Single,  cntG1Fil);
			Utils.prtIndentMsgFile(plog, 1, msg2);
		}
		return binGeneMap;
	}
	catch (Exception e) {ErrorReport.print(e,"Compute gene clusters"); return null;}	
	}
	/////////////////////////////////////////////////////////////
	// called during createClusterHits to transfer cHitNum for save
	protected boolean setClBin(HashMap <Integer, HitCluster> clMap) {
	try {
		for (PairHits ph : genePairList) { 
			if (ph.bin==0) {
				ph.setBin(); // overlap, so bin was assigned to another pair
				if (ph.bin==0) continue;
			}
			
			if (clMap.containsKey(ph.bin)) 
				ph.clBin = clMap.get(ph.bin).cHitNum;
			else if (Globals.DEBUG) System.out.println("No cl for " + ph.toResults());
		}
		return true;
	}
	catch (Exception e) {ErrorReport.print(e,"Compute gene clusters"); return false;}	
	}
	/*****************************************************************
	 * pseudo_annot_hits 
	 */
	protected boolean saveAnnoHits(DBconn2 tdbc2, int qGrpIdx, int tGrpIdx) {
	try {
		// Load hits from database, getting their new idx 
		TreeMap <Integer, Integer> clIdxMap = new TreeMap <Integer, Integer> ();
		String st = "SELECT idx, hitnum" +
      			" FROM pseudo_hits WHERE gene_overlap>0 and grp1_idx=" + qGrpIdx + " and grp2_idx=" + tGrpIdx;
		ResultSet rs = tdbc2.executeQuery(st);
		while (rs.next()) clIdxMap.put(rs.getInt(2), rs.getInt(1)); // cHitNum, DB generated idx
		rs.close();
				
		// save all PairHits gene-gene, gene-none, none-gene
		PreparedStatement ps = tdbc2.prepareStatement("insert ignore into pseudo_hits_annot "
				+ "(hit_idx, annot_idx, olap, exlap, annot2_idx) values (?,?,?,?,?)");
		
		int cntBatch=0, cntAll=0;
		int [] count = {0,0};
		
		for (PairHits ph : genePairList) {
			if (ph.bin==0) continue;
			if (!clIdxMap.containsKey(ph.clBin)) continue; // could have been filtered
			
			int dbhitIdx = clIdxMap.get(ph.clBin);
			
			if (ph.qGeneIdx>0) {
				ps.setInt(1, dbhitIdx); 
				ps.setInt(2, ph.qGeneIdx); 
				ps.setInt(3, intScore(ph.qGeneScore)); 
				ps.setInt(4, intScore(ph.qExonScore)); 
				ps.setInt(5, ph.tGeneIdx);
				ps.addBatch();
				cntBatch++;
				count[Q]++;
				cntAll++;
			}
			if (ph.tGeneIdx>0) {
				ps.setInt(1, dbhitIdx); 
				ps.setInt(2, ph.tGeneIdx); 
				ps.setInt(3, intScore(ph.tGeneScore)); 
				ps.setInt(4, intScore(ph.tExonScore)); 
				ps.setInt(5, ph.qGeneIdx);
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
		
		if (Arg.PRT_STATS) {
			String msg = String.format("%,10d for %s;  %,d for %s", count[Q], qChr, count[T], tChr );
			Utils.prtIndentMsgFile(plog, 1, msg);
		}
		return true;
	}
	catch (Exception e) {ErrorReport.print(e,"Compute gene clusters"); return false;}	
	}
	private int intScore(double score) {
		if (score>100.0) score=100;
		if (score<1.0) score=1.0;
		return (int) Math.round(score);
	}
	/*****************************************************************
	// create GenePairList and assign homologous genes to each other
	 *****************************************************************8*/
	private void createGenePairList() {
	try {
		if (tGeneMap.size()==0 || qGeneMap.size()==0) return;
				
		TreeMap <String, PairHits> genePairMap = new TreeMap <String, PairHits> (); // tgidx:qgidx -> hits
		
		for (Hit ht: gpHitList) { // order does not matter
			
			ht.createGenePairs();     
			if (ht.genePair==null) continue;	
			
			for (String idxPair : ht.genePair) { // tgn.geneIdx + ":" + qgn.geneIdx
				PairHits hpr;
				String [] tok = idxPair.split(":");
				int tidx = getInt(tok[0]);
				int qidx = getInt(tok[1]);
				
				if (!genePairMap.containsKey(idxPair)) {
					hpr = new PairHits(tidx, qidx);
					genePairMap.put(idxPair, hpr);	
				}
				else hpr = genePairMap.get(idxPair);
				
				hpr.addHit(ht);
				
				if (Globals.TRACE) {
					Gene tgene = tGeneMap.get(tidx);
					Gene qgene = qGeneMap.get(qidx);
					tgene.addHomoGene(qgene);
					qgene.addHomoGene(tgene);
				}
			}
		}
	
		for (PairHits ph : genePairMap.values()) genePairList.add(ph);
		
		for (PairHits hpr : genePairList) {	// if a genepair has EQ && NE, the score is not quite right; redone before save
			Gene tgene = tGeneMap.get(hpr.tGeneIdx);
			Gene qgene = qGeneMap.get(hpr.qGeneIdx);
			hpr.tExonScore = tgene.scoreExons(hpr.hitSet);  if (hpr.tExonScore==-1) {bSuccess=false; return;}
			hpr.qExonScore = qgene.scoreExons(hpr.hitSet);  if (hpr.qExonScore==-1) {bSuccess=false; return;}
			hpr.exonScore  = hpr.tExonScore + hpr.qExonScore;
			hpr.tGeneScore = tgene.scoreGene(hpr.hitSet);
			hpr.qGeneScore = tgene.scoreGene(hpr.hitSet);
			hpr.geneScore  = hpr.tGeneScore + hpr.qGeneScore;
		}
		
		// A hit may be assigned to multiple gene-pairs; it is used in the first occurrence
		Collections.sort(genePairList, 
			new Comparator<PairHits>() {
				public int compare(PairHits h1, PairHits h2) {
					if (h1.exonScore>h2.exonScore) return -1; 
					if (h1.exonScore<h2.exonScore) return  1;
					
					if (h1.geneScore>h2.geneScore) return -1;
					if (h1.geneScore<h2.geneScore) return  1;
					
					if (h1.hitList.size()>h2.hitList.size()) return -1;
					if (h1.hitList.size()<h2.hitList.size()) return 1;
					
					return 0;
				}
			}
		);
	}
	catch (Exception e) {ErrorReport.print(e,"Compute gene DS"); bSuccess=false;}	
	}
	/****************************************************************
	// G2: Set bin for set of hits between gene pairs
	****************************************************************/
	private void createG2() {
	try {
		for (PairHits ph : genePairList) {
			int tidx = ph.tGeneIdx;
			int qidx = ph.qGeneIdx;
			int hitSize = ph.hitList.size();
			
		// Multi-hit
			Hit.sortBySignByX(T, ph.hitList); 	// SORT gene pair hits by T
			int lenEQ=0, lenNE=0, cntBin=0;		// determine orientation to use
			for (Hit ht: ph.hitList) {
				if (ht.isHitEQ) lenEQ+=ht.blen; 
				else            lenNE+=ht.blen;
				if (ht.bin>0)   cntBin++;
			}
			if (cntBin==hitSize) continue; 		// totally overlapping gene; will inherit parent CLnum
			
			boolean isEQ = (lenEQ>lenNE);
		
			Hit lastHt=null;					// Assign bin
			int cntHt=0;
			for (Hit ht: ph.hitList) {
				if (ht.bin>0 || ht.isHitEQ!=isEQ) continue; 
				ph.bin = ht.bin = nBin;
				cntHt++; 
			
				lastHt = ht;
			}
			if (lastHt==null) continue; 		// all isEQ have bin
			
			if (cntHt>1) {						// finish
				cntG2++;
				createNewBinG2(tidx, qidx);
				continue;
			}
			ph.bin = lastHt.bin = 0; // CAS547 had a bin that was being used
			
		// Single hit - filter	
			Gene tgene = tGeneMap.get(tidx);	
			Gene qgene = qGeneMap.get(qidx);
			
			double  tbases = lastHt.len[T]*(lastHt.id/100.0);	
			boolean btexon = lastHt.bHitsExon(T, tgene);
			double  qbases = lastHt.len[Q]*(lastHt.id/100.0);
			boolean bqexon = lastHt.bHitsExon(Q, qgene);
			
			boolean bGoodT = (btexon) ? (tbases>Arg.gnMinExon) : (tbases>Arg.gnMinIntron);
			boolean bGoodQ = (bqexon) ? (qbases>Arg.gnMinExon) : (qbases>Arg.gnMinIntron);
			
			if (bGoodT || bGoodQ) { 
				lastHt.bin = ph.bin = nBin;
				cntG2Single++;
				createNewBinG2(tidx, qidx);
			}
			else {
				cntG2Fil++;
				lastHt.bDead=true; 
			}
		}
	}
	catch (Exception e) {ErrorReport.print(e,"Compute gene pair"); bSuccess=false;}	
	}
	private void createNewBinG2(int tidx, int qidx) {
		int [] idx = {tidx, qidx};
		binGeneMap.put(nBin, idx);
		nBin++;
	}
	/****************************************************************
	// G1: Set bins where genes with hits without mates
	************************************************************/
	private void createG1(int ix, int iy, TreeMap <Integer, Gene> xGeneMap) {
	try {
		if (xGeneMap.size()==0) return;
		
		for (Gene xgene : xGeneMap.values()) { 
			ArrayList <Hit> hit0List = xgene.get0Bin(ix); // bin=0 and does not have paired gene
			if (hit0List==null) continue;

			int arrLen = hit0List.size();
			Hit.sortBySignByX(iy, hit0List); // SORT: assume X is proper distance, check Y
			
		// only process a EQ or NE set
			int lenEQ=0, lenNE=0;
			for (Hit ht: hit0List) {
				if (ht.isHitEQ) lenEQ+=ht.blen; 
				else lenNE+=ht.blen;
			}
			boolean isEQ = (lenEQ>lenNE);
	
		// find cluster
			ArrayList <Hit> hList = new ArrayList <Hit> (); // for exon scoring
			Hit lastHt = hit0List.get(0);
			int cnt=0;
			
			for (int r=1; r<arrLen; r++) {
				Hit ht = hit0List.get(r);
				
				if (!isEQ &&  hit0List.get(r).isHitEQ) continue; // find first !EQ
				if ( isEQ && !hit0List.get(r).isHitEQ) break;    // done with EQ
				
				int diffY = Arg.getDiff(iy, isEQ, lastHt, ht);
				
				if (diffY < Arg.useIntronLen[iy]) { 
					if (lastHt!=null && lastHt.bin==0) {
						lastHt.bin = nBin;
						hList.add(lastHt);
					}
					ht.bin = nBin;
					hList.add(ht);
					lastHt = ht;
					
					cnt++;
				}
				else break;
			}
			
			if (cnt>0) { // will have at least two hits
				cntG1++;
				createNewBinG1(ix, xgene, hList);
				continue;
			}
			
			// Single hit 
			Hit ht = xgene.getBestHit(); // could have multiple hits, but too far apart on Y side
			if (ht==null) continue;
			
			double  bases = ht.len[ix]*(ht.id/100.0);
			boolean bexon = ht.bHitsExon(ix, xgene);
			
			if ((bexon && (bases > Arg.gnMinExon)) || (!bexon && (bases>Arg.gnMinIntron))) {
				ht.bin = nBin;
				cntG1Single++;
				hList.add(ht);
				createNewBinG1(ix, xgene, hList);
			} 
			else {
				cntG1Fil++;
				ht.bDead=true;
			}
		}
	}
	catch (Exception e) {ErrorReport.print(e,"Compute gene single");  bSuccess=false;}	
	}
	private void createNewBinG1(int X, Gene xgene, ArrayList <Hit> hList) {
	try {
		int [] idx = {0,0};
		idx[X] = xgene.geneIdx;
		binGeneMap.put(nBin, idx);
		
		// Used for pseudo_annot_hits
		PairHits ph =  (X==Q) ? new PairHits(0, xgene.geneIdx) : new PairHits(xgene.geneIdx, 0);
		for (Hit ht : hList) ph.addHit(ht);
		ph.bin = nBin;
		genePairList.add(ph);
		
		nBin++;
	}
	catch (Exception e) {ErrorReport.print(e,"Compute gene single");  bSuccess=false;}	
	}
	
	protected void clear() {
		for (PairHits gp : genePairList) gp.clear();
		genePairList.clear();
		binGeneMap.clear();
		// gpHitList, tGeneMap, qGeneMap cleared in GrpPair
	}
	
	private int getInt(String i) { 
		try {return Integer.parseInt(i);}
		catch (Exception e) {return -1;}
	}

	public void prtAll(PrintWriter fhOut) {
	try {
		for (PairHits ph : genePairList) fhOut.println(ph.toResults()+"\n");
	}
	catch (Exception e) {ErrorReport.print(e,"Compute gene single");  bSuccess=false;}
	}
	////////////////////////////////////////////////////////////////////////
	private class PairHits {
		private int tGeneIdx=0, qGeneIdx=0;
		private ArrayList <Hit>   hitList = new ArrayList <Hit> (); // hits shared by tgene-qgene (or gene-none for final)
		private HashSet <Integer> hitSet = new HashSet <Integer> (); // used for exon scoring
		
		private double tExonScore=0.0, qExonScore=0.0; 
		private double tGeneScore=0.0, qGeneScore=0.0;
		private double exonScore=0.0, geneScore=0.0;
		
		private int bin=0;
		private int clBin=0; 
		private char flag=' ';
		
		private PairHits(int tidx, int qidx) {
			tGeneIdx = tidx;
			qGeneIdx = qidx;
		}
		private void addHit(Hit ht) {
			hitList.add(ht);
			hitSet.add(ht.hitCnt);
		}
		private void setBin() { 
			if (!hitList.get(0).bDead) {
				bin = hitList.get(0).bin;
				flag='*';
			}
		}
		private void clear() {
			hitList.clear();
			hitSet.clear();
		}
		private String toResults() {
			String n1 = (tGeneIdx>0) ? tGeneMap.get(tGeneIdx).toName() : "None";
			String n2 = (qGeneIdx>0) ? qGeneMap.get(qGeneIdx).toName() : "None";
			String msg = String.format(">GP: %-10s %-10s [t %.2f q %.2f][exon %.2f gene %.2f] %s %s [b%d CL%d]%c", 
					n1, n2, tExonScore, qExonScore, exonScore, geneScore, tChr, qChr, bin, clBin, flag);
			
			Hit last=null;
			int bin=0;
			
			for (Hit ht: hitList) {
				String m="";
				if (bin==0) bin=ht.bin;
				else if (ht.bin!=bin) {
					m="!!!";	// change bin within group
					bin=ht.bin;
				}
				msg += "\n" + ht.toDiff(last) + m;
				last = ht;
			}
			return msg;
		}
	}
}
