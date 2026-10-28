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
import util.ErrorCount;
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
	private final String BpTYPE = Globals.blastHit; // instead of EE..., or g2
	private final String SEP = ":";    // key for hitMap (geneIdxs) and blockMap (chrNames)
	
	private Mpair mp;
	private ProgressDialog mLog;

	private int pairIdx, projIdx1, projIdx2;
	private Mproject mProj1, mProj2;
	
	private boolean isAA=true;
	private int idCutoff=0;
	private double evalCutoff= Double.MAX_VALUE;

	private boolean bSuccess=true;
	private AccessDB aDB;
				
	private HashMap <String, Integer> gnIdIdxMap1   = new HashMap <String, Integer> ();// LoadAnno: GFF Id, idx
	private HashMap <String, Integer> gnIdIdxMap2   = new HashMap <String, Integer> ();//           ditto
	private HashMap <Integer, Gene>   gnIdxGeneMap1 = new HashMap <Integer, Gene> ();  //           idx, Gene
	private HashMap <Integer, Gene>   gnIdxGeneMap2 = new HashMap <Integer, Gene> ();  //           ditto
	
	private HashMap <String, Hit>     hitPairMap  = new HashMap <String, Hit> ();    // Created in Blast; key geneIdx1:geneIdx2
	/********************************************************************/
	public UseBlast(DBconn2 dbc, ProgressDialog mLog,  Mpair mp) {
		this.mLog = mLog;
		this.mp = mp;
		
		aDB = new AccessDB(dbc);
		pairIdx = mp.getPairIdx();
		mProj1  = mp.mProj1;		
		mProj2  = mp.mProj2;
		projIdx1 = mProj1.getIdx();	
		projIdx2 = mProj2.getIdx();
		
		isAA = mp.isBlastAA(Mpair.FILE);
		idCutoff = mp.getpIdentity(Mpair.FILE);
		evalCutoff = mp.getEval(Mpair.FILE);
	}
	protected boolean run() {
		long time = Utils.getTime();
		TreeMap <Integer, String> grpMap1 = mProj1.getGrpIdxFullMap(); // grpIdx, grpFullName, e.g. Chr01
		TreeMap <Integer, String> grpMap2 = mProj2.getGrpIdxFullMap();		
		
		mLog.msg("");
		mLog.msg("Load Blast for " + mProj1.getDBName() + " and " + mProj2.getDBName());
		
		String blastFileList = Utils.getBlastFileList(mp.getResultDir()); // use for summary at end
		ArrayList <String> fileList = Utils.getBlastFiles(mp.getResultDir());
		if (fileList==null || fileList.isEmpty()) {
			failure(null, "No files in " + mp.getResultDir() + Constants.blastDir);
			return false;
		}
		
	// Process
		aDB.loadGenes(mProj1.strDisplayName, grpMap1, gnIdIdxMap1, gnIdxGeneMap1); if (!bSuccess) return false;
		aDB.loadGenes(mProj2.strDisplayName, grpMap2, gnIdIdxMap2, gnIdxGeneMap2); if (!bSuccess) return false;
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
		
		// Finish (numHits && collinear done in AnchorMain)
		time = Utils.getTime();
		mp.saveParamsToDBforBlast(blastFileList); // goes in Summary, which is called in DoAlignSynPair
				
		Utils.prtMsgTimeDone(mLog, "Finish Load Blast", time); 
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
		int cntRead=0, cntGood=0, cntSkip=0, cnt=0, idFail=0, evalFail=0;
		boolean bSwap = false, bSetSwap=false;
		int tm = (isAA) ? 3 : 1;
		
		while ((line = fhIn.readLine()) != null) {
			if (line.isBlank() || line.startsWith("//") || line.startsWith("#")) continue;
			
			String [] tok = line.trim().split("\\s+");
			if (tok.length != 12) {
				Utils.prtIndentMsg(mLog, 2, "File does not have blast format of 12 columns -- skipping");
				fhIn.close();
				return false;
			}
			cntRead++;
			String qid = tok[0];
			String sid = tok[1];
			if (!bSetSwap) { // read until the first found qseqid or sseqid, set swap if necessary
				if      (gnIdIdxMap1.containsKey(qid)) bSetSwap = true;
				else if (gnIdIdxMap2.containsKey(qid)) bSetSwap = bSwap =  true;
				else if (gnIdIdxMap2.containsKey(sid)) bSetSwap = true;
				else if (gnIdIdxMap1.containsKey(sid)) bSetSwap = bSwap = true;
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
			
			if (gnIdIdxMap1.containsKey(qid)) {
				idx1 = gnIdIdxMap1.get(qid);
				gn1  = gnIdxGeneMap1.get(idx1);
			}
			if (gnIdIdxMap2.containsKey(sid)) {
				idx2 = gnIdIdxMap2.get(sid);
				gn2  = gnIdxGeneMap2.get(idx2);
			}
			if (idx1==0 || idx2==0) {
				if (cntSkip<3)       Utils.prtIndentMsg(mLog, 2, "No ID's in database: " + qid + " or " + sid);
				else if (cntSkip==3) Utils.prtIndentMsg(mLog, 2, "Additional messages will be surpressed");
				cntSkip++; 
				continue; 
			}
			if (idx1==idx2) continue; // for self 
			int pid = toInt(tok[2]); 
			if (pid<idCutoff) {
				idFail++;
				continue;
			}
			double eval = toDouble(tok[10]);
			if (eval>evalCutoff) {
				evalFail++;
				continue;
			}
			cntGood++;
			Hit ht;
			String key = gn1.idx + SEP + gn2.idx;
			if (hitPairMap.containsKey(key)) ht = hitPairMap.get(key);
			else {
				ht = new Hit(gn1, gn2);
				hitPairMap.put(key, ht);
			}
			int s1, e1, s2, e2, len;
			
			len = toInt(tok[3])*tm; // skip mm and gapopen
			if (bSwap) {
				s2  = toInt(tok[6])    + gn2.cdsStart;
				e2  = toInt(tok[7])*tm + gn2.cdsStart;
				s1  = toInt(tok[8])    + gn1.cdsStart;
				e1  = toInt(tok[9])*tm + gn1.cdsStart; 
			}
			else {
				s1  = toInt(tok[6])    + gn1.cdsStart;
				e1  = toInt(tok[7])*tm + gn1.cdsStart;
				s2  = toInt(tok[8])    + gn2.cdsStart;
				e2  = toInt(tok[9])*tm + gn2.cdsStart; 	
			}
			if (s1>e1) {int i=s1; s1=e1; e1=i;} // happens with NT on +/-
			if (s2>e2) {int i=s2; s2=e2; e2=i;}
			if (e1>gn1.geneEnd) e1 = gn1.geneEnd; // if load NT, but did not change in pair params to NT (then *3)
			if (e2>gn2.geneEnd) e2 = gn2.geneEnd;
			
			if (!bSuccess) {fhIn.close(); return false;} // for any toInt or interrupt
			
			ht.add(pid, len, s1, e1, s2, e2);	
			if (cnt++ == 10000) {Globals.rprt(" Load " + cntRead); cnt=0;}
		}
		fhIn.close();
		Globals.rclear();
		Utils.prtNumMsgFile(mLog, cntRead, "Hits from Blast file");
		if (idFail>0) Utils.prtNumMsgFile(mLog, idFail, "Failed %identity cutoff of " + idCutoff);
		if (evalFail>0) Utils.prtNumMsgFile(mLog, evalFail, "Failed E-value cutoff of " + evalCutoff);
		
		if (cntSkip>0) Utils.prtNumMsgFile(mLog,cntSkip,"Ignore hits - no corresponding gene pair");
		if (cntSkip>0 && cntGood==0) {
			String name1="", name2="";
			for (Gene x : gnIdxGeneMap1.values()) {name1 = x.name; break;} // get first one
			for (Gene x : gnIdxGeneMap2.values()) {name2 = x.name; break;}
			Utils.prtIndentMsg(mLog, 2, "Example IDs in database: " + name1 + " " + name2) ;
		} 
		return true;
	}
	catch (Exception e) {return failure(e, "Load file");}
	}
	private void finish() {
	try {
		for (Hit ht : hitPairMap.values()) {
			ht.gn1.eolap = (ht.len<ht.gn1.exonLen) ? (int) Math.round((((double)ht.len/(double)ht.gn1.exonLen)*100.0)) : 100;
			ht.gn2.eolap = (ht.len<ht.gn2.exonLen) ? (int) Math.round((((double)ht.len/(double)ht.gn2.exonLen)*100.0)) : 100;
			
			ht.gn1.golap = (ht.len<ht.gn1.geneLen) ? (int) Math.round((((double)ht.len/(double)ht.gn1.geneLen)*100.0)) : 100;
			ht.gn2.golap = (ht.len<ht.gn2.geneLen) ? (int) Math.round((((double)ht.len/(double)ht.gn2.geneLen)*100.0)) : 100;
			
			ht.pid = (int) Math.round((double)ht.pid/(double)ht.numHits);
			int len1 = ht.e1-ht.s1+1;
			int len2 = ht.e2-ht.s2+1;
			ht.len = Math.max(len1, len2);
		}
	}
	catch (Exception e) {failure(e, "Load file"); }
	}
	/***********************************************************/
	private int toInt(String i) {
		try {
			if (i.contains(".")) return (int) Math.round(Double.parseDouble(i));
			return Integer.parseInt(i);
		} 
		catch (Exception e) {failure(e, "To integer"); return -1;}
	}
	private double toDouble(String i) {
		try {
			return Double.parseDouble(i);
		} 
		catch (Exception e) {failure(e, "To double"); return -1;}
	}
	private boolean failure(Exception e, String msg) {
		Utils.prtIndentMsg(mLog, 1, msg + " -- cannot continue");
		if (e!=null) ErrorReport.print(e, msg);        
		ErrorCount.inc();
		bSuccess=false;
		return false;
	}
	protected void interrupt() {
		bSuccess=false;
		Utils.prtIndentMsgFile(mLog, 1, "Load Blast was interrupted");
	}
	/********************************************************************/
	// Created in loadAnno
	private class Gene {
		private Gene(int idx, int grpIdx, int start, int end, int elen, String strand, String name) {
			this.idx=idx; 	
			this.grpIdx=grpIdx;
			this.geneEnd = end;
			this.geneLen = end-start+1;
			this.exonLen = elen;
			this.strand=strand;
			this.name = name;	
		}
		private void addCDS(int cstart) {
			if (cdsStart==0 || cstart<cdsStart) cdsStart=cstart;
		} 
		private int idx, grpIdx;	 // hit uses idx and grpIdx
		private int exonLen;			 
		private String strand, name; // strand is used for hit; name is used in idIdxMap
		private int cdsStart=0;		 // hits relative to first CDS
		private int geneLen, geneEnd=0;		 // 
		private int golap=0, eolap;
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
		private String htype = BpTYPE;
		
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
				HashMap <String, Integer> gnIdIdxMap, HashMap <Integer, Gene> geneIdxMap) {
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
				int elen = getExonLen(tag);
				
				String name="";
				if (!anno.isBlank() && anno.contains(Globals.idKey)) {
					String [] tok = anno.split(";");
					for (String tuple : tok) {
						if (tuple.startsWith(Globals.idKey)) {
							name = tuple.split("=")[1];	
							break;
						}
					}
				} else {
					if (cntNoId<3) {
						if (tag.contains("(")) name = tag.substring(0,tag.indexOf(")"));
						else name = tag;
						Globals.prt(String.format("%6s does not have GFF ID", tag));
					}
					else if (cntNoId==3) Globals.prt("Further 'no ID' messages will be surpressed");
					cntNoId++; continue;
				}
				
				gnIdIdxMap.put(name, idx);
				Gene gn = new Gene(idx, grpIdx, s, e, elen, strand, name);
				geneIdxMap.put(idx, gn);
				cntGene++;
			}
			
			if (gnIdIdxMap.size()==0 && cntNoId>0) {
				failure(null, "Genes do not have a GFF ID"); 
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
		catch (Exception e) {failure(e, "loadAnno"); }
		}
		private static int getExonLen(String tag) { // 1.a (15 1,637)
			String [] tok = tag.split("\\s+");
			if (tok.length!=3) return -1;
				
			String len = tok[2].replace(")", "");
			len = len.replace(",", "");
			
			return Integer.parseInt(len);
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
				ps.setString(i++, ht.htype) ;	// EE:Bp1, or Bp12....
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
				else {failure(null, "No hitnum " + hnum); rs.close(); return; }
			}
			rs.close();
			
			// pseudo_hits_annot
			ps = dbc.prepareStatement("insert ignore into pseudo_hits_annot "
					+ "(hit_idx, annot_idx, olap, exlap, annot2_idx) values (?,?,?,?,?)");
			
			int cntBatch=0, cntAll=0;
			for (Hit ht : hitPairMap.values()) { 
				ps.setInt(1, ht.hidx); 
				ps.setInt(2, ht.gn1.idx); 
				ps.setInt(3, ht.gn1.golap); 
				ps.setInt(4, ht.gn1.eolap); 
				ps.setInt(5, ht.gn2.idx);
				ps.addBatch();
				cntBatch++;
				cntAll++;
			
				ps.setInt(1, ht.hidx); 
				ps.setInt(2, ht.gn2.idx); 
				ps.setInt(3, ht.gn2.golap); 
				ps.setInt(4, ht.gn1.eolap); 
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
			Utils.prtNumMsgFile(mLog, hitIdxMap.size(), "Saved new hits");
		}
		catch (Exception e) {failure(e, "Save New Hits"); }
		}
		/********************************************************************
		 * Read hits from database; for those in hitPairMap, update htype
		 * Each blast file will get an incremental number assigned to htype
		 ***********************************************************/
		private void updateExistingHits() {
		try {
			int numExistHits = aDB.getNumHits();
			if (numExistHits==0) {
				for (Hit ht : hitPairMap.values()) ht.htype = BpTYPE+"1";
				return;
			}
			Utils.prtNumMsgFile(mLog, numExistHits, "Hits from gene pairs in database");
			
			ResultSet rs = dbc.executeQuery("SELECT idx, annot1_idx, annot2_idx, htype "
					+ " FROM pseudo_hits where pair_idx=" + mp.getPairIdx());
			
			int found=0, lastNum=0;
			while (rs.next()){
				String key = rs.getInt(2) + SEP + rs.getInt(3);
				if (!hitPairMap.containsKey(key)) continue;
				found++;
				
				Hit ht = hitPairMap.get(key);
				ht.isNew = false;
				ht.hidx = rs.getInt(1);
				
				ht.htype = rs.getString(4);
				if (ht.htype==null) ht.htype="";
				int htlen = ht.htype.length();
				if (htlen==0) continue;
				
				boolean isBp = ht.htype.contains(BpTYPE);
				int maxsz = ht.htype.startsWith(BpTYPE) ? 11 : 14; // Bp123456789 : EE:Bp123456789
				
				if (isBp && htlen<maxsz) { 
					String last = ht.htype.substring(htlen-1, htlen);
					try {
						int i = Integer.parseInt(last);
						if (i>lastNum) lastNum=i;
					} 
					catch (Exception e) {}
				}
			}
			rs.close();
			if (found==0) return;
			
			lastNum++;		// will be 0 on first, or if pre-v591
			if (lastNum<10) {
				for (Hit ht : hitPairMap.values()) {
					if (ht.htype.length()>=14) continue;
					
					if (ht.htype.isBlank()) 			ht.htype = BpTYPE + lastNum;		// first hit
					else {
						if (ht.htype.contains(BpTYPE)) 	ht.htype += lastNum;				// at least on Blast hit already
						else 							ht.htype += SEP + BpTYPE  + lastNum; // MUMmer hit
					}
				}
			}
			Utils.prtNumMsgFile(mLog, found, "Update existing hits - file #" + lastNum);
			
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
		catch (Exception e) {failure(e, "Load Hits"); }
		}
		
		private int getNumHits() {
			return dbc.executeCount("select count(*) from pseudo_hits where gene_overlap=2 and pair_idx=" + pairIdx);
		}
	}
}
