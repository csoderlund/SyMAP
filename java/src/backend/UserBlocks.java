package backend;

import java.util.HashMap;
import java.util.TreeMap;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;

import database.DBconn2;
import symap.Globals;
import symap.manager.Mpair;
import symap.manager.Mproject;
import util.ErrorReport;
import util.ProgressDialog;

/************************************************
 * Load Blocks: -lb this was written to load McScanX blocks for comparison
 * Pair parameters lower left pull down, select Load Blocks
 * ManagerFrame prompts for file
 * DoAlignSynPair call this
 */
public class UserBlocks {
	private final String HTYPE = "QH"; // instead of EE..., or g2
	private final String SEP = ":";    // key for hitMap (geneIdxs) and blockMap (chrNames)
	
	private String fileName; // e.g. data/seq_results/proj1_to_proj2/blocks.txt
	private int pairIdx, projIdx1, projIdx2;
	private Mpair mp;
	private Mproject mProj1, mProj2;
	private ProgressDialog mLog;
	private boolean bSuccess=true;
	private AccessDB aDB; 
	
	private HashMap <String, Integer> idIdxMap1 = new HashMap <String, Integer> ();// GFF Id, idx
	private HashMap <String, Integer> idIdxMap2 = new HashMap <String, Integer> ();// ditto
	private HashMap <String, Block> blockMap = new HashMap <String, Block> (); //chr:chr:blk# full names; contains list of created hits
	
	// Chr-Chr; saved then cleared between each pair
	private ArrayList <Block> blockList = new ArrayList <Block> ();				// created in process from blockMap
	private HashMap <Integer, Gene> geneIdxMap1 	= new HashMap <Integer, Gene> ();// loaded in loadAnno
	private HashMap <Integer, Gene> geneIdxMap2 	= new HashMap <Integer, Gene> ();// ditto
	private HashMap <String, Hit> hitMap 		= new HashMap <String, Hit> (); // created in addGene2Hits; key geneIdx1:geneIdx2
	
	/******************************************************************/
	public UserBlocks(ProgressDialog mLog, DBconn2 dbc, Mpair mp, String fileName) {
		this.fileName = fileName;
		this.mLog = mLog;
		this.mp = mp;
		aDB = new AccessDB(dbc);

		mProj1 = mp.mProj1;
		mProj2 = mp.mProj2;
		pairIdx = mp.getPairIdx();
		projIdx1 = mProj1.getIdx();
		projIdx2 = mProj2.getIdx();
	
		String tmp = fileName.contains("data") ? fileName.substring(fileName.indexOf("data")) : fileName;
		Utils.prtIndentMsgFile(mLog, 1, String.format("Load blocks for %s and %s from %s", mProj1.getDBName(), mProj2.getDBName(), tmp));
		
		// Process
		TreeMap <Integer, String> grpMap1 = mProj1.getGrpIdxFullMap(); // grpIdx, grpFullName, e.g. Chr01
		TreeMap <Integer, String> grpMap2 = mProj2.getGrpIdxFullMap();
		
		aDB.loadGeneID(grpMap1, idIdxMap1); if (!bSuccess) return;
		aDB.loadGeneID(grpMap2, idIdxMap2); if (!bSuccess) return;
		
		readBlocks();	if (!bSuccess) return;
		aDB.clearDB();	if (!bSuccess) return;
		
		for (int grpIdx1 : grpMap1.keySet()) {
			for (int grpIdx2 : grpMap2.keySet()) {
				String chr1 = grpMap1.get(grpIdx1);
				String chr2 = grpMap2.get(grpIdx2);
				
				processChrChr(grpIdx1, chr1, grpIdx2, chr2); if (!bSuccess) return;
			}
		}
		Utils.prtIndentMsgFile(mLog, 1, "Complete Load Blocks");
	}
	/********************************************************************
	 * Create all blocks and their hits -- then processed by chr:chr
	 */
	private void readBlocks() {
	try {
		BufferedReader fhIn = new BufferedReader ( new FileReader (fileName));
		String line, chr1="", chr2="", id1, id2;
		int idx1, idx2, blkNum=1, cntPair=0;
		Block curBlk=null;
		boolean bSwap= false;
		
		// Read 1st 1-2 lines to determine if swap
		while ((line = fhIn.readLine()) != null) {
			if (line.isBlank() || line.startsWith("#")) continue;
			String [] tok = line.trim().split("\\s+");
			
			if (line.startsWith("Block: ")) { // could be Chr02 Chr02
				if (tok.length<3) {prtErr("Improper formatting: " + line); fhIn.close(); return;}
				chr1 = tok[1]; chr2 = tok[2];
				idx1 = mProj1.getGrpIdxFromFullName(chr1);
				idx2 = mProj2.getGrpIdxFromFullName(chr2);
				if (idx1 == -1 || idx2 == -1) {
					prtMsg("Species are in opposite order of database - swap");
					chr1 = tok[2];chr2 = tok[1];
					bSwap = true;
					idx1 = mProj1.getGrpIdxFromFullName(chr1);
					idx2 = mProj2.getGrpIdxFromFullName(chr2);
				}
				curBlk = new Block(blkNum);
				String key = chr1+SEP+chr2+SEP+blkNum;	// could be multiple blocks for chr1:chr2; search on chr1+SEP+chr2+SEP
				blockMap.put(key, curBlk);
				blkNum++;
			
				if (bSwap) break;
			}
			else {
				if (tok.length<2 || curBlk==null) {prtErr("Improper formatting: " + line); fhIn.close(); return;}
			
				id1 = tok[0]; id2 = tok[1];
				idx1 = (idIdxMap1.containsKey(id1)) ? idIdxMap1.get(id1) : -1;
				idx2 = (idIdxMap2.containsKey(id2)) ? idIdxMap2.get(id2) : -1;
				if (idx1 == -1 && idx2 == -1) {
					idx1 = (idIdxMap1.containsKey(id2)) ? idIdxMap1.get(id2) : -1;
					idx2 = (idIdxMap2.containsKey(id1)) ? idIdxMap2.get(id1) : -1;
					if (idx1 == -1 || idx2 == -1) prtErr("IDs are not in database: " + line);
					
					prtMsg("Species are in opposite order of database - swap");
					curBlk.hitList.add(new Hit(id1, id2));
					cntPair++;
					bSwap = true;
				}
				break;
			}
		}
		// read the rest
		while ((line = fhIn.readLine()) != null) {
			if (line.isBlank() || line.startsWith("#")) continue;
			String [] tok = line.trim().split("\\s+"); // if not trim, uses leading spaces as tok[0]
			
			if (line.startsWith("Block: ")) {
				if (tok.length<3) {prtErr("Improper formatting: " + line); fhIn.close(); return;}
				
				if (bSwap) {chr1 = tok[2]; chr2 = tok[1];}
				else       {chr1 = tok[1]; chr2 = tok[2];}
				
				idx1 = mProj1.getGrpIdxFromFullName(chr1);
				idx2 = mProj2.getGrpIdxFromFullName(chr2);
				if (idx1 == -1 ) {prtErr("No know chr: " + chr1); fhIn.close(); return;}
				if (idx2 == -1 ) {prtErr("No know chr: " + chr2); fhIn.close(); return;}
				
				curBlk = new Block(blkNum);
				String key = chr1+SEP+chr2+SEP+blkNum;	
				blockMap.put(key, curBlk);
				blkNum++;
			}
			else {
				if (tok.length<2 || curBlk==null) 
					{prtErr("Improper formatting: " + line); fhIn.close(); return;}
				
				if (bSwap) {id1 = tok[1]; id2 = tok[0];}
				else       {id1 = tok[0]; id2 = tok[1];}		
				curBlk.hitList.add(new Hit(id1, id2));
				cntPair++;
			}
		}
		fhIn.close();
		
		prtMsg(String.format("%,d Blocks %,d hit pairs", blockMap.size(), cntPair));
	}
	catch (Exception e) {ErrorReport.print(e, "readBlocks"); bSuccess=false;}	
	}
	/********************************************************************/
	private void processChrChr(int grpIdx1, String chr1, int grpIdx2, String chr2) {
	try {
		Globals.rprt("Process " + chr1 + " x " + chr2);
		
		String key = chr1 + SEP + chr2 + SEP;
		for (String ccb : blockMap.keySet()) {
			if (ccb.startsWith(key)) blockList.add(blockMap.get(ccb));
		}
		aDB.loadAnno(mProj1, geneIdxMap1, grpIdx1);	if (!bSuccess) return;
		aDB.loadAnno(mProj2, geneIdxMap2, grpIdx2);	if (!bSuccess) return;
		addGene2Hits();  							if (!bSuccess) return;
		
		aDB.loadHits(grpIdx1, grpIdx2);				if (!bSuccess) return;
		
		for (Block b : blockList) {
			for (Hit ht : b.hitList) ht.coords();
			b.coords();
		}
		aDB.saveData(grpIdx1, grpIdx2);
		
		geneIdxMap1.clear(); geneIdxMap2.clear(); hitMap.clear();  blockList.clear();
	}
	catch (Exception e) {ErrorReport.print(e, "loadAnno"); bSuccess=false;}
	}
	
	/********************************************************************/
	private void addGene2Hits() {
	try {
		int errors=0, found=0;
		for (Block b : blockList) {
			for (Hit ht : b.hitList) {
				if (!idIdxMap1.containsKey(ht.Id1)) {
					if (errors <5)    Globals.prt("No GFF Id from species 1: " + ht.Id1 + " in database");
					if (errors++ ==5) Globals.prt("Suppress further errors");
				}
				else if (!idIdxMap2.containsKey(ht.Id2)) {
					if (errors <5)    Globals.prt("No GFF Id from species 2: " + ht.Id2 + " in database");
					if (errors++ ==5) Globals.prt("Suppress further errors");
				}
				else {
					found++;
					int idx1 = idIdxMap1.get(ht.Id1);
					int idx2 = idIdxMap2.get(ht.Id2);
					ht.gene1 = geneIdxMap1.get(idx1);
					ht.gene2 = geneIdxMap2.get(idx2);
					
					String key = ht.gene1.idx + SEP + ht.gene2.idx;
					if (!hitMap.containsKey(key)) hitMap.put(key, ht);  // for lookup when reading hits
					else                          Globals.prt("Duplicate pair: " + key);
				}
			}
			if (errors>found) {
				prtErr("Too many errors: " + errors + " bad and " + found + " good");
				return;
			}
		}
		prtMsg(String.format("Linked genes to %,d hits", found));
	}
	catch (Exception e) {ErrorReport.print(e, "createPseudoHits"); bSuccess=false;}	
	}
	/********************************************************************/
	///////////////////////////
	private void prtErr(String msg) {
		Utils.prtMsgFile(mLog, msg + " -- cannot continue");
		bSuccess=false;
	}
	private void prtMsg(String msg) {
		Utils.prtIndentMsgFile(mLog, 1, msg);
	}
	private void prtDump() {
		for (Block b : blockList) {
			Globals.xprt(b.prt());
			for (Hit ht : b.hitList) {
				Globals.xprt(ht.prt());
			}
		}
	}
	/********************************************************************/
	private class AccessDB {
		DBconn2 dbc;
		
		private AccessDB(DBconn2 dbc) {this.dbc = dbc;}
		
		private void clearDB() {
			try {
				int x = dbc.getIdx("select idx from pairs where proj1_idx=" + projIdx1 + " and proj2_idx=" + projIdx2);
				if (x == -1) {
					pairIdx = mp.removeAndSetIdx();
					return;
				}
				Utils.prtIndentMsgFile(mLog, 1, "Delete blocks from database");
				// the following also deletes from pseudo_hits_annot and pseudo_block_hits CASCADE
				dbc.executeUpdate("DELETE from pseudo_hits WHERE pair_idx="+ pairIdx + " and htype='" + HTYPE + "'");
				dbc.resetIdx("idx", "pseudo_hits");
				
				// also deletes from pseudo_block_hits
				mp.removeSyntenyFromDB(); 
			}
			catch (Exception e) {ErrorReport.print(e, "clearDB"); bSuccess=false;}	
		}
		/* *******************************************************************/
		private void loadGeneID(TreeMap <Integer, String> grpMap, HashMap <String, Integer> idIdxMap) {
		try {
			int cntNoId=0, cntGene=0;
			String gffId;
			
			for (int grpIdx : grpMap.keySet()) {
				ResultSet rs = dbc.executeQuery(
					"SELECT idx, name FROM pseudo_annot  where type='gene' and grp_idx=" + grpIdx );
			
				while (rs.next()){
					int idx = rs.getInt(1);
					String anno = rs.getString(2);
					if (!anno.isBlank() && anno.contains(Globals.idKey)) {
						String [] tok = anno.split(";");
						for (String tuple : tok) {
							if (tuple.startsWith(Globals.idKey)) {
								gffId = tuple.split("=")[1];
								idIdxMap.put(gffId, idx);
								break;
							}
						}
					} else cntNoId++;
					cntGene++;
				}
			}
			if (idIdxMap.size()==0 && cntNoId>0) {
				prtErr("Genes do not have a GFF ID");
				return;
			}
			if (cntNoId>0) Globals.prt(String.format("%,d genes have no GFF ID from %,d total", cntNoId, cntGene));
		}
		catch (Exception e) {ErrorReport.print(e, "loadAnno"); bSuccess=false;}
		}
		/* *******************************************************************/
		private void loadAnno(Mproject mProj, HashMap <Integer, Gene> geneMap, int grpIdx) {
		try {
			int cntNoId=0, cntGene=0;
			ResultSet rs = dbc.executeQuery(
					"SELECT idx, start, end, strand, tag FROM pseudo_annot  where type='gene' and grp_idx=" + grpIdx);
			while (rs.next()){
				Gene gn = new Gene(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getString(4), rs.getString(5));
				
				geneMap.put(gn.idx, gn);
			}
			rs.close();
			
			if (geneMap.size()==0 && cntNoId>0) {prtErr("Genes do not have a GFF ID"); return;}
			
			if (cntNoId>0) Globals.prt(String.format("%,d genes have no GFF ID from %,d total", cntNoId, cntGene));
		}
		catch (Exception e) {ErrorReport.print(e, "loadAnno"); bSuccess=false;}
		}
		/* *******************************************************************
		 * Load Block made Hits with gene IDs 
		 */
		private void loadHits(int grpIdx1, int grpIdx2) {
		try {
			int cnt=0;
			ResultSet rs = dbc.executeQuery(
					"SELECT idx, annot1_idx, annot2_idx, start1, end1, start2, end2 FROM pseudo_hits where gene_overlap=2 and pair_idx =" + pairIdx + 
					                                       " and grp1_idx=" + grpIdx1 + " and grp2_idx=" + grpIdx2);
			// add only if it matches a hit from file
			while (rs.next()){
				int idx = rs.getInt(1);
				int aidx1 = rs.getInt(2);
				int aidx2 = rs.getInt(3);
				
				String key = aidx1 + SEP + aidx2;
				if (hitMap.containsKey(key)) { // not an error if not
					Hit ht = hitMap.get(key);
					ht.idx = idx;
					ht.s1 = rs.getInt(4);
					ht.e1 = rs.getInt(5);
					ht.s2 = rs.getInt(6);
					ht.e2 = rs.getInt(7);
					ht.isNew = false;
					cnt++;
				}
			}
			Utils.prtIndentMsgFile(mLog, 1, String.format("%,d hits exists  %,d new hits", cnt, (hitMap.size()-cnt)));
		}
		catch (Exception e) {ErrorReport.print(e, "loadHits"); bSuccess=false;}
		}
		/******************************************************************* */
		private void saveData(int grpIdx1, int grpIdx2) {
		try {
			saveNewHit(grpIdx1, grpIdx2);
			saveBlocks(grpIdx1, grpIdx2);		
		}
		catch (Exception e) {ErrorReport.print(e, "saveData"); bSuccess=false;}
		}
		/* ****************************************************************** */
		private void saveNewHit(int grpIdx1, int grpIdx2) {
		try {
			// pseudo_hits
			String sql = "insert into pseudo_hits (hitnum, pair_idx,"
					+ "proj1_idx, proj2_idx, grp1_idx, grp2_idx,"
					+ "pctid, cvgpct, countpct, score, htype, gene_overlap, strand,"
					+ "annot1_idx, annot2_idx, start1, end1, start2, end2, query_seq, target_seq)";
	
			PreparedStatement ps = dbc.prepareStatement(sql 
				+ "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ");
			
			HashMap <Integer, Hit> hitIdxMap = new HashMap <Integer, Hit> ();
			int countBatch=0, cntSave=0;
			int hitnum = dbc.getMaxValue("hitnum", "pseudo_hits")+1;
		
			for (Hit ht : hitMap.values()) {
				if (!ht.isNew) continue;
				
				int i=1;
				ps.setInt(i++, hitnum);
				hitIdxMap.put(hitnum, ht);
				hitnum++;
				
				ps.setInt(i++, pairIdx);
				ps.setInt(i++, projIdx1);
				ps.setInt(i++, projIdx2);
				ps.setInt(i++, grpIdx1);
				ps.setInt(i++, grpIdx2);
				ps.setInt(i++, 0);				// pctid Avg%Id
				ps.setInt(i++, 0); 				// cvgpct;  unsigned tiny int; now Avg%Sim
				ps.setInt(i++, 0); 				// countpct; unsigned tiny int; now #SubHits
				ps.setInt(i++, 0); 				// score; max coverage 
				ps.setString(i++, HTYPE) ;		// htype usually EE or g2
				ps.setInt(i++, 2);				// geneOlap 0,1,2
				ps.setString(i++, ht.gene1.strand +"/" + ht.gene2.strand); 		// Strand
				
				ps.setInt(i++, ht.gene1.idx);
				ps.setInt(i++, ht.gene2.idx); 
				ps.setInt(i++, ht.gene1.start);
				ps.setInt(i++, ht.gene1.end);
				ps.setInt(i++, ht.gene2.start);
				ps.setInt(i++, ht.gene2.end);
				
				ps.setString(i++, "");
				ps.setString(i++, "");
				
				ps.addBatch(); 
				countBatch++; cntSave++;
				if (countBatch==1000) {
					countBatch=0;
					ps.executeBatch();
				}
				if (cntSave%10000==0) Globals.rprt(cntSave + " loaded"); 
			}
			if (countBatch> 0) ps.executeBatch();
			ps.close();
			
			/* Get new hit indices */
			int cnt=0;
			ResultSet rs = dbc.executeQuery("select idx, hitnum from pseudo_hits where htype='" + HTYPE + "'"
					+ " and grp1_idx=" + grpIdx1 + " and grp2_idx=" + grpIdx2);
			while(rs.next()) {
				cnt++;
				int idx = rs.getInt(1);
				int hnum = rs.getInt(2);
				
				if (hitIdxMap.containsKey(hnum)) hitIdxMap.get(hnum).idx=idx;
				else {prtErr("No hitnum " + hnum); rs.close(); return; }
			}
			rs.close();
			
			// pseudo_hits_annot
			ps = dbc.prepareStatement("insert ignore into pseudo_hits_annot "
					+ "(hit_idx, annot_idx, olap, exlap, annot2_idx) values (?,?,?,?,?)");
			
			int cntBatch=0, cntAll=0;
			for (Hit ht : hitMap.values()) { 
				if (!ht.isNew) continue;
				ps.setInt(1, ht.idx); 
				ps.setInt(2, ht.gene1.idx); 
				ps.setInt(3, 0); 
				ps.setInt(4, 0); 
				ps.setInt(5, ht.gene2.idx);
				ps.addBatch();
				cntBatch++;
				cntAll++;
			
				ps.setInt(1, ht.idx); 
				ps.setInt(2, ht.gene2.idx); 
				ps.setInt(3, 0); 
				ps.setInt(4, 0); 
				ps.setInt(5, ht.gene1.idx);
				ps.addBatch();
				cntBatch++;
				cntAll++;
				
				if (cntBatch>=1000) {
					cntBatch=0;
					ps.executeBatch();
				}
				if (cntAll%10000 ==0) Globals.rprt("   " + cntAll + " loaded hit annotations"); 
			}
			if (cntBatch> 0) ps.executeBatch();
			prtMsg(String.format("Save %,d new hits; hit-gene %,d", hitIdxMap.size(), cnt, cntAll));
		}
		catch (Exception e) {ErrorReport.print(e, "save New Hits"); bSuccess=false;}
		}
		/* ****************************************************************** */
		// add blocks and pseudo_block_hits
		private void saveBlocks(int grpIdx1, int grpIdx2) {
		try {
			// blocks
			String sql= "insert into blocks (pair_idx, blocknum, proj1_idx, grp1_idx, start1, end1, "
					+ "proj2_idx, grp2_idx, start2, end2, avgGap1, avgGap2, "
					+ "score, corr, comment, ngene1, ngene2, genef1, genef2) "
					+ "values (?,?, ?,?,?,?, ?,?,?,?, ?,?,  ?,?,?,  0, 0, 0.0, 0.0)";
			PreparedStatement ps = dbc.prepareStatement(sql);
			
			for (Block b : blockList) { 
				int i=1;
				ps.setInt(i++, pairIdx);
				ps.setInt(i++, b.blkNum);
				
				ps.setInt(i++, mProj1.getIdx());
				ps.setInt(i++, grpIdx1);
				ps.setInt(i++, b.s1);
				ps.setInt(i++, b.e1);
				
				ps.setInt(i++, mProj2.getIdx());
				ps.setInt(i++, grpIdx2);
				ps.setInt(i++, b.s2);
				ps.setInt(i++, b.e2);
				
				ps.setInt(i++, 0);
				ps.setInt(i++, 0);
				
				ps.setInt(i++,    b.hitList.size());
				ps.setFloat(i++, (float) 0.0);
				ps.setString(i++, ("Avg %ID: 0.0")); // tinytext (256char)
			
				ps.executeUpdate();
			}
			ps.close();
			
			// Create pseudo_block_hits; first, get block idx (if bSwap, swapped above so can use start1...)
			for (Block b : blockList) { 
				String st = "SELECT idx FROM blocks WHERE pair_idx=" + pairIdx +
						" AND blocknum=" + b.blkNum + 
						" AND proj1_idx=" + mProj1.getIdx() + " AND grp1_idx=" +grpIdx1 +
						" AND proj2_idx=" + mProj2.getIdx() + " AND grp2_idx=" +grpIdx2 +
						" AND start1=" + b.s1 + " AND end1=" + b.e1 +
						" AND start2=" + b.s2 + " AND end2=" + b.e2;   
				b.idx = dbc.executeInteger(st);
				if (b.idx==-1) ErrorReport.die("SyMAP error, cannot get block idx \n" + st);
			}
		
			PreparedStatement ps2 = dbc.prepareStatement(
					"insert into pseudo_block_hits (hit_idx, block_idx) values (?,?)");
			for (Block b : blockList) {
				for (Hit ht : b.hitList) {
					if (ht.idx>0) {
						ps2.setInt(1, ht.idx); 
						ps2.setInt(2,  b.idx);
						ps2.executeUpdate(); 
					}
					else Globals.prt("No hit idx " + b.blkNum + " " + ht.prt());
				}
			}
			ps2.close();
			
			/** compute final block fields  - copied from synteny.RWdb**/
			// count genes1 with start1 and end2 of block
			dbc.executeUpdate("update blocks set "
					+ "ngene1 = (select count(*) from pseudo_annot as pa " +
					  "where pa.grp_idx=grp1_idx and greatest(pa.start,start1) < least(pa.end,end1) " +
					"  and pa.type='gene') where pair_idx=" + pairIdx);
			
			dbc.executeUpdate("update blocks set "
					+ "ngene2 = (select count(*) from pseudo_annot as pa " +
					" where pa.grp_idx=grp2_idx  and greatest(pa.start,start2) < least(pa.end,end2) " +
					" and pa.type='gene') where pair_idx=" + pairIdx);

			// compute gene1 in grp1 that have hit, and divide by ngene1
			dbc.executeUpdate("update blocks set "
					+ "genef1=(select count(distinct annot_idx) from  " +
					" pseudo_hits_annot      as pha " +
					" join pseudo_block_hits as pbh on pbh.hit_idx=pha.hit_idx " +
					" join pseudo_annot      as pa  on pa.idx=pha.annot_idx " +
					" where pbh.block_idx=blocks.idx and pa.grp_idx=blocks.grp1_idx)/ngene1 " +
					" where ngene1 > 0 and pair_idx=" + pairIdx);
			
			dbc.executeUpdate("update blocks set "
					+ "genef2=(select count(distinct annot_idx) from  " +
					" pseudo_hits_annot as pha " +
					" join pseudo_block_hits as pbh on pbh.hit_idx=pha.hit_idx " +
					" join pseudo_annot as pa on pa.idx=pha.annot_idx " +
					" where pbh.block_idx=blocks.idx and pa.grp_idx=blocks.grp2_idx)/ngene2 " +
					" where ngene2 > 0 and pair_idx=" + pairIdx);			
		}
		catch (Exception e) {ErrorReport.print(e, "save blocks"); bSuccess=false;}
		}
	} // End AccessDB
	/********************************************************************/
	/********************************************************************/
	
	
	private class Block { // created in readBlocks
		private Block(int blkNum) {
			this.blkNum  = blkNum;
		}
		private void coords() {
			for (Hit ht : hitList) {
				s1 = Math.min(s1, ht.s1);
				s2 = Math.min(s2, ht.s2);
				e1 = Math.max(e1, ht.e1);
				e2 = Math.max(e2, ht.e2);
			}
		}
		private String prt() {
			return String.format("Block %3d  %,8d %,8d  %,8d %,8d", blkNum, s1, e1, s2, e2);
		}
		private int idx; 				// assigned when entered into DB 
		private int blkNum;
		private int s1=Integer.MAX_VALUE, s2=Integer.MAX_VALUE, e1=0, e2=0;
		
		private ArrayList <Hit> hitList = new ArrayList <Hit> ();
	}
	// created during load blocks
	private class Hit {			
		private Hit(String id1, String id2) {this.Id1=id1; this.Id2=id2;} 
		private String Id1, Id2;		// assigned in readBlocks
		private Gene gene1, gene2;  	// assigned in addGene2Hit
		private int idx; 				// assigned in loadHit and saveNewHit
		private boolean isNew = true;   // set false if found
		private int s1, s2, e1, e2;     // assigned in loadHit or coords
		
		private String prt() {
			return String.format("ID %-15s %-15s  %-10s %-10s Coords %,8d %,8d %,8d %,8d  New %s",
					Id1, Id2, gene1.tag, gene2.tag, s1, e1, s2, e2, Globals.toTF(isNew));
		}
		private void coords() {
			if (isNew) {
				s1 = gene1.start; e1=gene1.end;
				s2 = gene2.start; e2=gene2.end;
			}
		}
	}	
	// Created in loadAnno
	private class Gene {
		private Gene(int idx, int start, int end, String strand, String tag) {
			this.idx=idx; 	
			this.start=start; 		this.end=end; 
			this.strand=strand;
			this.tag = tag.substring(0, tag.indexOf("("));
		}
		private int idx;
		private int start, end;	
		private String strand, tag;
	}
}
