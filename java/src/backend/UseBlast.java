package backend;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;

import database.DBconn2;
import symap.Globals;
import symap.manager.Mpair;
import symap.manager.Mproject;
import util.ErrorReport;
import util.ProgressDialog;

/*********************************************************
 * This takes as input a blast -m8 tab file and uses them as hits. Add CAS589
 * They may be merged into a cluster if they hit the same gene pair.
 * The top 5 are taken, regardless of the number.
 * This could be a NCBI/Ensembl transcript file, in which case many of the transcripts will
 * not be in the SyMAP GFF file.
 * 
 * 1. For the selected gene pair: load the genes from the database
 * 2. Load hits from the data/seq_results/<proj1_to_proj2> directory
 * 
 * Parameters: Gene#, or GFF ID
 */
public class UseBlast {
	private boolean bVerbose = Constants.VERBOSE;
	private final String HTYPE = Globals.blastHit; // instead of EE..., or g2
	private final String SEP = ":";    // key for hitMap (geneIdxs) and blockMap (chrNames)
	
	private Mpair mp;
	private ProgressDialog mLog;
	private DBconn2 dbc;
	private boolean isID=true, isPR=true;		// right now, these are always true
	
	private int pairIdx, projIdx1, projIdx2;
	private Mproject mProj1, mProj2;

	private boolean bSuccess=true;
	private AccessDB aDB;
				
	private HashMap <String, Integer> idIdxMap1   = new HashMap <String, Integer> ();// LoadAnno: GFF Id, idx
	private HashMap <String, Integer> idIdxMap2   = new HashMap <String, Integer> ();//           ditto
	private HashMap <Integer, Gene>   geneIdxMap1 = new HashMap <Integer, Gene> ();  //           idx, Gene
	private HashMap <Integer, Gene>   geneIdxMap2 = new HashMap <Integer, Gene> ();  //           ditto
	
	private HashMap <String, Hit>     hitPairMap  = new HashMap <String, Hit> ();    // Created in Blast; key geneIdx1:geneIdx2

	/********************************************************************/
	public UseBlast(DBconn2 dbc, ProgressDialog mLog,  Mpair mp) {
		this.mLog = mLog;
		this.mp = mp;
		this.dbc = dbc;
		
		aDB = new AccessDB(dbc);
		pairIdx = mp.getPairIdx();
		mProj1  = mp.mProj1;		
		mProj2  = mp.mProj2;
		projIdx1 = mProj1.getIdx();	
		projIdx2 = mProj2.getIdx();
	}
	protected boolean run() {
		long time = Utils.getTime();
		TreeMap <Integer, String> grpMap1 = mProj1.getGrpIdxFullMap(); // grpIdx, grpFullName, e.g. Chr01
		TreeMap <Integer, String> grpMap2 = mProj2.getGrpIdxFullMap();		
		
		mLog.msg("");
		mLog.msg("Load Blast for " + mProj1.getDBName() + " and " + mProj2.getDBName());
		
		ArrayList <String> fileList = Utils.getBlastFiles(mp.getResultDir());
		if (fileList==null || fileList.isEmpty()) {
			prtErr("No files in " + mp.getResultDir() + Constants.blastDir);
			return false;
		}
		
	// Process
		aDB.loadGenes(mProj1.strDisplayName, grpMap1, idIdxMap1, geneIdxMap1); if (!bSuccess) return false;
		aDB.loadGenes(mProj2.strDisplayName, grpMap2, idIdxMap2, geneIdxMap2); if (!bSuccess) return false;
		int num = aDB.getNumHits();
		if (num>0) Utils.prtNumMsgFile(mLog, aDB.getNumHits(), "gene pair hits in database");
		boolean rc;
		
		for (String fileName : fileList) {
			Utils.prtIndentMsg(mLog, 1, "--- Read File " + fileName.substring(fileName.lastIndexOf("/")) + " ---");
			
			rc = readBlast(fileName);				if (!bSuccess) return false;						
			if (rc) {
				aDB.updateExistingHits(); 			if (!bSuccess) return false;
				
				finish();							if (!bSuccess) return false;
			
				aDB.saveHits(grpMap1, grpMap2);		if (!bSuccess) return false;
			}
			hitPairMap.clear();
		}
		Utils.prtNumMsgFile(mLog, aDB.getNumHits(), "Total hits in database");
		Utils.prtMsgTimeDone(mLog, "Finish Load Blast", time); 
		
	// Finish -- These all must be done!
		time = Utils.getTime();
		Utils.prtIndentMsg(mLog, 0, "Process hits"); 
		mp.saveParamsToDB("Load " + Utils.getBlastFileList(mp.getResultDir())); // goes in Summary, which is done in DoAlignSynPair
		
		AnchorMain anchors = new AnchorMain(dbc, mLog, mp);
		anchors.saveHitNum();
		anchors.saveAnnoHitCnt(true);
		
		if (mp.isCoSetAlgo(Mpair.FILE)) new AnchorCset2(mp, mProj1, mProj2, dbc, mLog).collinearSets(); 
		else 							new AnchorCset1(mp, mProj1, mProj2, dbc, mLog).collinearSets(); 
		
		Utils.prtMsgTimeDone(mLog, "Finish Processing hits", time); 

		return true;
	}
	/********************************************************************
	 * qseqid       sseqid     %id   len     mm    gapopen   qs 	 qe	     ss      se      evalue    bitscore
	 * LOC103862735 AT2G42710  40.0  215     123     2       126     334     188     402     1.93e-46  160
	 * 0            1          2     3       4       5       6       7       8       9       10        11
	 */
	private boolean readBlast(String fileName) {
	try {
		BufferedReader fhIn = new BufferedReader (new FileReader (fileName));
		String line;
		int cntPair=0, cntSkip=0, cnt=0;
		boolean bSwap = false, bSetSwap=false;
		int t = (isPR) ? 3 : 1;
		
		while ((line = fhIn.readLine()) != null) {
			if (line.isBlank() || line.startsWith("//") || line.startsWith("#")) continue;
			
			String [] tok = line.trim().split("\\s+");
			if (tok.length != 12) {
				Utils.prtIndentMsg(mLog, 2, "File does not have blast format of 12 columns -- skipping");
				fhIn.close();
				return false;
			}
			String qid = tok[0];
			String sid = tok[1];
			if (!bSetSwap) { // read until the first found qseqid or sseqid, set swap if necessary
				if      (idIdxMap1.containsKey(qid)) bSetSwap = true;
				else if (idIdxMap2.containsKey(qid)) bSetSwap = bSwap =  true;
				else if (idIdxMap2.containsKey(sid)) bSetSwap = true;
				else if (idIdxMap1.containsKey(sid)) bSetSwap = bSwap = true;
				else { // both not in; if only one is not in, not caught until below
					if (cntSkip<3)       Utils.prtIndentMsg(mLog, 2, " No ID's in database: " + qid + " or " + sid);
					else if (cntSkip==3) Utils.prtIndentMsg(mLog, 2, " Additional messages will be surpressed");
					cntSkip++; 
					continue;
				}		
				if (bSwap) Utils.prtIndentMsg(mLog, 2, " Projects Gene IDs in reverse order - swapping");
				else       Utils.prtIndentMsg(mLog, 2, " Projects Gene IDs in correct order");
			}
			int idx1=0, idx2=0;
			Gene gn1=null, gn2=null;
			if (bSwap) {String tmp=qid; qid=sid; sid=tmp;}
			
			if (idIdxMap1.containsKey(qid)) {
				idx1 = idIdxMap1.get(qid);
				gn1  = geneIdxMap1.get(idx1);
			}
			if (idIdxMap2.containsKey(sid)) {
				idx2 = idIdxMap2.get(sid);
				gn2  = geneIdxMap2.get(idx2);
			}
			if (idx1==0 || idx2==0) {
				if (cntSkip<3)       Utils.prtIndentMsg(mLog, 2, "No ID's in database: " + qid + " or " + sid);
				else if (cntSkip==3) Utils.prtIndentMsg(mLog, 2, "Additional messages will be surpressed");
				cntSkip++; 
				continue; 
			}
			if (idx1==idx2) continue; // for self 
			
			cntPair++;	
			Hit ht;
			String key = gn1.idx + SEP + gn2.idx;
			if (hitPairMap.containsKey(key)) ht = hitPairMap.get(key);
			else {
				ht = new Hit(gn1, gn2);
				hitPairMap.put(key, ht);
			}
			int s1, e1, s2, e2, len, pid;
			
			pid = toInt(tok[2]); 
			len = toInt(tok[3])*t; // skip mm and gapopen
			if (bSwap) {
				s2  = toInt(tok[6])   + gn2.cdsStart;
				e2  = toInt(tok[7])*t + gn2.cdsStart;
				s1  = toInt(tok[8])   + gn1.cdsStart;
				e1  = toInt(tok[9])*t + gn1.cdsStart; 
			}
			else {
				s1  = toInt(tok[6])   + gn1.cdsStart;
				e1  = toInt(tok[7])*t + gn1.cdsStart;
				s2  = toInt(tok[8])   + gn2.cdsStart;
				e2  = toInt(tok[9])*t + gn2.cdsStart; 
			}
			if (!bSuccess) {fhIn.close(); return false;} // for any toInt or interrupt
			
			ht.add(pid, len, s1, e1, s2, e2);	
			if (cnt++ == 10000) {Globals.rprt(" Load " + cntPair); cnt=0;}
		}
		fhIn.close();
		Globals.rclear();
		Utils.prtNumMsgFile(mLog, cntPair, "Hits from Blast file");
		
		if (cntSkip>0) Utils.prtNumMsgFile(mLog,cntSkip,"Ignore hits - no corresponding gene pair");
		if (cntSkip>0 && cntPair==0) {
			String name1="", name2="";
			for (Gene x : geneIdxMap1.values()) {name1 = x.name; break;} // get first one
			for (Gene x : geneIdxMap2.values()) {name2 = x.name; break;}
			Utils.prtIndentMsg(mLog, 2, "Example IDs in database: " + name1 + " " + name2) ;
		} 
		return true;
	}
	catch (Exception e) {ErrorReport.print(e, "Load file"); bSuccess=false; return false;}
	}
	private void finish() {
	try {
		for (Hit ht : hitPairMap.values()) {
			ht.gn1.olap = (ht.len<ht.gn1.len) ? (int) Math.round((((double)ht.len/(double)ht.gn1.len)*100.0)) : 100;
			ht.gn2.olap = (ht.len<ht.gn2.len) ? (int) Math.round((((double)ht.len/(double)ht.gn2.len)*100.0)) : 100;
			ht.pid = (int) Math.round((double)ht.pid/(double)ht.numHits);
			int len1 = ht.e1-ht.s1+1;
			int len2 = ht.e2-ht.s2+1;
			ht.len = Math.max(len1, len2);
		}
	}
	catch (Exception e) {ErrorReport.print(e, "Load file"); bSuccess=false;}
	}
	/***********************************************************/
	///////////////////////////
	private void prtErr(String msg) {
		Utils.prtIndentMsg(mLog, 1, msg + " -- cannot continue");
		bSuccess=false;
	}
	
	private int toInt(String i) {
		try {
			if (i.contains(".")) return (int) Math.round(Double.parseDouble(i));
			return Integer.parseInt(i);
		} 
		catch (Exception e) {ErrorReport.print(e, "To integer"); bSuccess=false; return -1;}
	}
	protected void interrupt() {
		bSuccess=false;
		Utils.prtIndentMsgFile(mLog, 1, "Load Blast was interrupted");
	}
	/********************************************************************/
	// Created in loadAnno
	private class Gene {
		private Gene(int idx, int grpIdx, int start, int end, String strand, String name) {
			this.idx=idx; 	
			this.grpIdx=grpIdx;
			this.len = end-start+1;
			this.strand=strand;
			this.name = name;	
		}
		private void addCDS(int cstart) {
			if (cdsStart==0 || cstart<cdsStart) cdsStart=cstart;
		} 
		private int idx, grpIdx;	 // hit uses idx and grpIdx
		private int len;			 
		private String strand, name; // strand is used for hit; name is used in idIdxMap
		private int cdsStart=0;		 // hits relative to first CDS
		private int olap=0;
	}
	// created during load hits
	private class Hit {	
		private Hit (Gene gn1, Gene gn2) {this.gn1=gn1; this.gn2=gn2;}
		private int hidx; 				// assigned in saveHits
		
		private Gene gn1, gn2;			// geneIdx and grpIdx come from these
		
		private int s1, s2, e1=0, e2=0;     
		private int pid, len;			
		
		private int numHits=0; 			// in case there is more than one hit between 2 genes; not used yet...
		private String querySeq="", targetSeq="";
		
		private boolean isNew=true;		// only changes if bAddExist and there is a hit in db for this gene:gene
		private String htype = HTYPE;
		
		private void add(int pid, int len, int s1, int e1, int s2, int e2) {
			if (numHits>0 && isOverlap(this.s1, this.e1, s1, e1) || isOverlap(this.s2, this.e2, s2, e2)) return; 
			//if (s1>=this.s1 && e1<=this.e1 ||  s2>=this.s2 && e2<=this.e2) return;// blast gives many overlapping hits that seem all jumbled
			
			numHits++;
			if (numHits==1) {
				this.pid=pid; this.len=len; 
				this.s1=s1; this.e1=e1;
				this.s2=s2; this.e2=e2;
				return;
			}
			// Merge
			if (numHits==2) {
				querySeq  = this.s1 + ":" + this.e1;
				targetSeq = this.s2 + ":" + this.e2;
			}
			querySeq  += "," + s1 + ":" + e1;
			targetSeq += "," + s2 + ":" + e2;
			if (s1<this.s1) this.s1=s1;
			if (e1>this.e1) this.e1=e1;
			if (s2<this.s2) this.s2=s2;
			if (e2>this.e2) this.e2=e2;
			this.pid += pid;
			// len computed on finish, and pid/numHits
		}
		public static boolean isOverlap(int start1, int end1, int start2, int end2) {
			if ((start1 >= start2 && start1 <= end2) || (end1  >= start2 && end1 <= end2)
			 || (start2 >= start1 && start2 <= end1) || (end2  >= start1 && end2 <= end1)) return true;
			
			return false;
		}
	}	
	
	/**************************************************************************/
	private class AccessDB {
		DBconn2 dbc;
		
		private AccessDB(DBconn2 dbc) {this.dbc = dbc;}
		
		/* *******************************************************************/
		private void loadGenes(String species, TreeMap <Integer, String> grpMap, 
				HashMap <String, Integer> idIdxMap, HashMap <Integer, Gene> geneIdxMap) {
		try {
			int cntNoId=0, cntGene=0;
			
			String grps=null;
			for (int ix : grpMap.keySet()) {
				if (grps==null) grps = ix+"";
				else grps += "," + ix;
			}
			
			ResultSet rs = dbc.executeQuery(
				"SELECT idx, grp_idx, start, end, strand, tag, name FROM pseudo_annot  "
				+ " where type='gene' and grp_idx in (" + grps + ")" );
		
			while (rs.next()){
				int i=1;
				int idx 		= rs.getInt(i++);
				int grpIdx 		= rs.getInt(i++);
				int s 			= rs.getInt(i++);
				int e 			= rs.getInt(i++);
				String strand 	= rs.getString(i++);
				String tag 		= rs.getString(i++);
				String anno 	= rs.getString(i++);
				
				String name="";
				if (isID) {
					if (!anno.isBlank() && anno.contains(Globals.idKey)) {
						String [] tok = anno.split(";");
						for (String tuple : tok) {
							if (tuple.startsWith(Globals.idKey)) {
								name = tuple.split("=")[1];
								
								break;
							}
						}
					} else {cntNoId++; continue;}
				}
				else name = tag.substring(0, tag.indexOf("("));
					
				idIdxMap.put(name, idx);
				Gene gn = new Gene(idx, grpIdx, s, e, strand, name);
				geneIdxMap.put(idx, gn);
				cntGene++;
			}
			
			if (idIdxMap.size()==0 && cntNoId>0 && isID) {
				prtErr("Genes do not have a GFF ID"); bSuccess=false;
				return;
			}
			// exons/cds can be ascending or descending, so need to check all; addCDS takes lowest value
			rs = dbc.executeQuery("SELECT gene_idx, start FROM pseudo_annot  "
					+ " where type='exon' and grp_idx in (" + grps + ")" );
			while (rs.next()) {
				Gene gn = geneIdxMap.get(rs.getInt(1));
				gn.addCDS(rs.getInt(2));
			}
			if (cntNoId>0) Utils.prtIndentMsg(mLog, 2,String.format("%s: %,d genes have no GFF ID from %,d total", species, cntNoId, cntGene));
			else if (bVerbose) Utils.prtNumMsgFile(mLog, cntGene, String.format("%-15s genes", species));
		}
		catch (Exception e) {ErrorReport.print(e, "loadAnno"); bSuccess=false;}
		}
		/* ****************************************************************** */
		private void saveHits(TreeMap <Integer, String> grpMap1, TreeMap <Integer, String> grpMap2) {
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
			int hitNum = dbc.getMaxValue("hitnum", "pseudo_hits")+1;
			int saveHitNum = hitNum;
			
			for (Hit ht : hitPairMap.values()) {
				if (!ht.isNew) continue;
				
				int i=1;
				ps.setInt(i++, hitNum);
				hitIdxMap.put(hitNum, ht);
				hitNum++;
				
				ps.setInt(i++, pairIdx);
				ps.setInt(i++, projIdx1);
				ps.setInt(i++, projIdx2);
				ps.setInt(i++, ht.gn1.grpIdx);
				ps.setInt(i++, ht.gn2.grpIdx);
			
				ps.setInt(i++, ht.pid);			// pctid Avg%Id
				ps.setInt(i++, 0); 				// cvgpct;  unsigned tiny int; now Avg%Sim
				ps.setInt(i++, ht.numHits); 	// countpct; unsigned tiny int, #SubHits
				ps.setInt(i++, ht.len); 		// score; max coverage 
				ps.setString(i++, HTYPE) ;		// htype usually EE or g2
				ps.setInt(i++, 2);				// geneOlap 0,1,2
				ps.setString(i++, ht.gn1.strand+"/"+ht.gn2.strand); 		// Strand
				ps.setInt(i++, ht.gn1.idx);
				ps.setInt(i++, ht.gn2.idx); 
				ps.setInt(i++, ht.s1);
				ps.setInt(i++, ht.e1);
				ps.setInt(i++, ht.s2);
				ps.setInt(i++, ht.e2);
				ps.setString(i++, ht.querySeq);	  // s:e, s:e ... for query; none if no subhits
				ps.setString(i++, ht.targetSeq);  // s:e, s:e ... for target; none if no subhits
				
				ps.addBatch(); 
				countBatch++; cntSave++;
				if (countBatch==1000) {
					countBatch=0;
					ps.executeBatch();
				}
				if (cntSave%10000==0) Globals.rprt(cntSave + " hits saved"); 
			}
			if (countBatch> 0) ps.executeBatch();
			ps.close();
			
			/* Get new hit indices */
			ResultSet rs = dbc.executeQuery("select idx, hitnum from pseudo_hits "
					+ " where hitnum >=" + saveHitNum + " and pair_idx=" + pairIdx);
			while(rs.next()) {
				int idx = rs.getInt(1);
				int hnum = rs.getInt(2);
				
				if (hitIdxMap.containsKey(hnum)) hitIdxMap.get(hnum).hidx=idx;
				else {prtErr("No hitnum " + hnum); rs.close(); return; }
			}
			rs.close();
			
			// pseudo_hits_annot
			ps = dbc.prepareStatement("insert ignore into pseudo_hits_annot "
					+ "(hit_idx, annot_idx, olap, exlap, annot2_idx) values (?,?,?,?,?)");
			
			int cntBatch=0, cntAll=0;
			for (Hit ht : hitPairMap.values()) { 
				ps.setInt(1, ht.hidx); 
				ps.setInt(2, ht.gn1.idx); 
				ps.setInt(3, ht.gn1.olap); 
				ps.setInt(4, 0); 
				ps.setInt(5, ht.gn2.idx);
				ps.addBatch();
				cntBatch++;
				cntAll++;
			
				ps.setInt(1, ht.hidx); 
				ps.setInt(2, ht.gn2.idx); 
				ps.setInt(3, ht.gn2.olap); 
				ps.setInt(4, 0); 
				ps.setInt(5, ht.gn1.idx);
				
				ps.addBatch();
				cntBatch++;  cntAll++;
				
				if (cntBatch>=1000) {
					cntBatch=0;
					ps.executeBatch();
				}
				if (cntAll%10000 ==0) Globals.rprt("   " + cntAll + " loaded hit annotations"); 
			}
			if (cntBatch>0) ps.executeBatch();
			ps.close();
			Globals.rclear();
			Utils.prtNumMsgFile(mLog, hitIdxMap.size(), "Saved hits");
		}
		catch (Exception e) {ErrorReport.print(e, "Save New Hits"); bSuccess=false;}
		}
		/******************************************************************
		 * 
		 */
		private void updateExistingHits() {
		try {
			// load and update ht.htype 
			ResultSet rs = dbc.executeQuery("SELECT idx, annot1_idx, annot2_idx, htype FROM pseudo_hits "
					+ " where pair_idx=" + mp.getPairIdx());
			
			int found=0;
			while (rs.next()){
				String key = rs.getInt(2) + SEP + rs.getInt(3);
				if (!hitPairMap.containsKey(key)) continue;
				
				Hit ht = hitPairMap.get(key);
				ht.isNew = false;
				found++;
				ht.hidx = rs.getInt(1);
				String type = rs.getString(4);
				
				if (!type.contains(HTYPE)) { // MUMmer type of g2, EE, EI, II; htype = EE:Bp
					ht.htype = type + SEP + HTYPE;
					continue;
				}
				else if (type.endsWith(HTYPE)) {// Bp or EE:Bp, etc;           htype = Bp2 or EE:Bp2
					ht.htype = type+2;
					continue;
				}
				
				String prefix=null, bhtype; // else increment last number, e.g. Bp3 or EE:Bp3, etc
				if (type.contains(SEP)) {
					String [] tok = type.split(":");
					prefix = tok[0];
					bhtype = tok[1];
				}
				else bhtype = type;
					
				bhtype = bhtype.replace(HTYPE, "");
				int num = toInt(bhtype) + 1;
				
				if (prefix==null) ht.htype = HTYPE + num;
				else              ht.htype = prefix + SEP +  HTYPE + num;
			}
			rs.close();
			if (found==0) return;
			
			Utils.prtNumMsgFile(mLog, found, "Update existing hits");
			
			int countBatch=0, countSave=0;
			PreparedStatement ps = dbc.prepareStatement("update pseudo_hits set htype=? where idx=?");
			for (Hit hObj : hitPairMap.values()) {
				ps.setString(1, hObj.htype);
				ps.setInt(2, hObj.hidx);
				ps.addBatch();
				
				countBatch++; countSave++;
				if (countBatch==1000) {
					countBatch=0;
					ps.executeBatch();
				}
				if (countSave%10000==0) Globals.rprt(countSave + " saved"); 
			}
			if (countBatch>0) ps.executeBatch();
			ps.close();
		}
		catch (Exception e) {ErrorReport.print(e, "Load Hits"); bSuccess=false;}
		}
		
		private int getNumHits() {
			return dbc.executeCount("select count(*) from pseudo_hits where gene_overlap=2 and pair_idx=" + pairIdx);
		}
	}
}
