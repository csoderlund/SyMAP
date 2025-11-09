package symap.manager;

import java.io.File;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.util.HashMap;

import backend.AnchorMain;
import backend.Constants;
import backend.Utils;
import backend.anchor1.Group;
import database.DBconn2;
import props.PropertiesReader;
import symap.Globals;
import util.ErrorReport;
import util.Utilities;
import util.FileDir;

/*********************************************************
 * this is mainly used in backend
 * 1. created when enter the selectedPairTable in ManagerFrame
 * 2. if new, create directory and params file; else read params file
 * 3. if previously A&S, read pair_props
 * 4. PairParams save - save all to file
 * 5. AlignProj  save - save all to db
 * 6. Show file params on PairParams
 * 7. Show db params on Summary
 */
public class Mpair {
	public  static final int FILE = 0;
	public  static final int DB = 1;
	// sections
	private static final String ALIGN   = "  Align";
	private static final String ALGO1   = "  Cluster Algo1 (modified original)";
	private static final String ALGO2   = "  Cluster Algo2 (exon-intron)";
	private static final String SYNORIG = "  Synteny Original";
	private static final String SYNSTRI = "  Synteny Strict";		
	private static final String spp     = "       ";	// space to go before parameters;
	
	public Mproject mProj1, mProj2;
	protected int pairIdx=-1; 		// accessed in ManagerFrame; 
	private int proj1Idx=-1, proj2Idx=-1;
	private DBconn2 dbc2;
	private String syVer="";
	public boolean bPseudo=false, bSynOnly=false; // set in PairParams and read in ManagerFrame
	
	private String resultDir;
	private HashMap <String, String> fileMap = new HashMap <String, String> ();
	private HashMap <String, String> dbMap = new HashMap <String, String> ();
	private HashMap <String, String> defMap = new HashMap <String, String> ();
	private boolean isSelf=false;
	
	// These must be in same order as PairParam.SYMBOLS arrays; the order isn't needed anywhere else in this file
	private static final String [] paramKey = { // repeated in PairParams and below
			"mindots", "strict_blocks", "same_orient", "merge_blocks",
			"order_none", "order_proj1", "order_proj2",			
			"concat", "mask1", "mask2", 					
			"nucmer_args", "promer_args", "self_args", "nucmer_only","promer_only",
			"number_pseudo","algo1", "algo2",
			"topn", 
			"gene_scale", "exon_scale","len_scale","g0_scale", 
			"EE_pile", "EI_pile", "En_pile", "II_pile", "In_pile"
	};
	// defMap value; not static because specific to pair, e.g. self-synteny, order, regular; CAS576
	private final int defStrict = 1, defAlgo = 16; // if annotated, [16]=0, [17]=1  
	private String [] paramDef = {  
			"7", "1", "0",	"0",        // synteny:  hits, strict, orient, merge; strict is default except for when there are many contigs
			"1", "0", "0", 				// 	 order against: none, proj1->proj2, proj2->proj1
			"1", "0", "0", 				// align: concat, mask1, mask2
			"", "", "", "0", "0",		//        nucmer, promer, self, only, only
			"0", "1", "0",				// cluster: number pseudo,  1st is algo1, 2nd is algo2
			"2", 					    //       topn
			"1.0", "1.0", "1.0", "1.0",	//       gene, exon, Len, G0_Len	
			"1", "1", "1", "0", "0" 	//       EE, EI, En, II, In 
	};
	
	public Mpair(DBconn2 dbc2, int pairIdx, Mproject p1, Mproject p2, boolean isReadOnly) {
		this.dbc2 = dbc2;
		this.mProj1 = p1;		// order is based on pairs table, or either can come first
		this.mProj2 = p2;
		this.pairIdx = pairIdx;
		
		proj1Idx = mProj1.getIdx();
		proj2Idx = mProj2.getIdx();
		isSelf = proj1Idx==proj2Idx;
		
		if (!isSelf && p1.hasGenes() && p2.hasGenes()) { 		// if annotated, algo2 is default
			paramDef[defAlgo]="0"; paramDef[defAlgo+1]="1";
		}
		else {
			paramDef[defAlgo]="1"; paramDef[defAlgo+1]="0";
		}
		if (p1.getNumDraft()>25 || p2.getNumDraft()>25) {		// strict is not good with many small contigs, i.e. ordering
			paramDef[defStrict] = "0"; 
		}
		String x = Constants.orderDelim; 						// ..	also does not work welll with ordered contigs		
		if ((mProj1.getDBName().contains(x) || mProj2.getDBName().contains(x))) {
			paramDef[defStrict] = "0"; 
		}
		
		resultDir = "./" + Constants.getNameResultsDir(p1.strDBName, p2.strDBName);
		
		makeParams();
		if (!isReadOnly)  loadFromFile(); // loads into FileMap
		if (pairIdx!= -1) loadFromDB();  //  loads into dbMap
		
		loadSyVer();
	}
	
	/************************************************************************************/
	// ManagerFrame.alignSelectPair popop when need align; DoAlignSynPair terminal, dialog, symap.log
	public String getChgAllParams(int type) {
		String amsg = getChgAlign(type, false);   
		String cmsg = getChgCluster(type); 
		String smsg = getChgSynteny(type); 
		
		return amsg + "\n" + cmsg + "\n" + smsg; // none are empty
	}
	// ManagerFrame.alignSelectedPair popup when no align; DoAlignSynPair terminal, dialog, symap.log
	public String getChgClustSyn(int type) { 
		String cmsg = getChgCluster(type); 
		String smsg = getChgSynteny(type); 
		
		return cmsg + "\n" + smsg;
	}
	
	public String getChgAlign(int type, boolean forSum) {
		String msg="";
		if (isChg(type,"concat")) 			msg = pjoin(msg, "No concat");	
		if (isChg(type,"mask1")) 			msg = pjoin(msg, "Mask " + mProj1.getDisplayName());	
		if (isChg(type,"mask2")) 			msg = pjoin(msg, "Mask " + mProj2.getDisplayName());	
		
		if (isChg(type,"promer_only")) 		msg = pjoin(msg, "PROmer only  ");
		else if (isChg(type,"nucmer_only")) msg = pjoin(msg, "NUCmer only  ");
		
		if (isChg(type,"self_args")) 		msg = pjoin(msg, "Self args: "   + getSelfArgs(type)); 
		else if (isChg(type,"promer_args")) msg = pjoin(msg, "PROmer args: " + getPromerArgs(type));
		else if (isChg(type,"nucmer_args")) msg = pjoin(msg, "NUCmer args: " + getNucmerArgs(type));
		
		if (!forSum && Constants.MUM_NO_RM) msg = pjoin(msg, "Keep MUMmer .delta file"); 
		
		if (!msg.equals("")) {
			if (msg.contains("PROmer") || msg.contains("NUCmer")) return ALIGN + "\n" + msg;
			else if (isSelf)                                      return ALIGN + " NUCmer" + "\n" + msg;
			else                                                  return ALIGN + " PROmer" + "\n" + msg;
		}
		if (isSelf) return ALIGN + " NUCmer" ; // always remind what is being used
		return ALIGN + " PROmer";
	}
	
	private String getChgCluster(int type) {
		String msg="";
		if (isAlgo2(type)) {
			if (isChg(type,"exon_scale")) 	msg = pjoin(msg, "Exon scale = " + getExonScale(type)); 
			if (isChg(type,"gene_scale")) 	msg = pjoin(msg, "Gene scale = " + getGeneScale(type));
			if (isChg(type,"len_scale")) 	msg = pjoin(msg, "Length scale = " + getLenScale(type));
			if (isChg(type,"g0_scale")) 	msg = pjoin(msg, "G0 length scale = " + getG0Scale(type));
			
			if (isChg(type,"EE_pile"))		msg = pjoin(msg, "Limit Exon-Exon piles");
			if (isChg(type,"EI_pile"))		msg = pjoin(msg, "Limit Exon-Intron piles"); 
			if (isChg(type,"En_pile"))		msg = pjoin(msg, "Limit Exon-intergenic piles");
			if (isChg(type,"II_pile"))		msg = pjoin(msg, "Allow Intron-Intron piles");
			if (isChg(type,"In_pile"))		msg = pjoin(msg, "Allow Intron-intergenic piles");
			if (isChg(type,"topn")) 		msg = pjoin(msg, "Top N piles = " + getTopN(type));
			if (isChg(type,"number_pseudo")) msg = pjoin(msg, "Number pseudo"); 
			
			if (msg.equals("")) msg = ALGO2;
			else                msg = ALGO2 + "\n" + msg;
		}
		else if (isAlgo1(type)) {
			if (isChg(type,"topn")) msg = pjoin(msg,"Top N piles =" + getTopN(type));
			if (Group.bSplitGene)   msg = pjoin(msg,"Split gene");
			if (isChg(type,"number_pseudo")) msg = pjoin(msg, "Number pseudo"); // algo will come after
			
			if (msg.equals("")) msg = ALGO1;
			else                msg = ALGO1 +  "\n" + msg; 				
		}
		return msg;
	}
	// Called for A&S, C&S, and SynOnly (directly)
	protected String getChgSynteny(int type) {   
		String msg="", smsg="";
		
		if (isStrict(type))  smsg = SYNSTRI;
		else                 smsg = SYNORIG;
		
		if (isChg(type,"mindots")) 		msg = pjoin(msg, "Min hits=" + getMinDots(type));
		if (isChg(type,"same_orient"))  msg = pjoin(msg, "Same orient");
		if (isChg(type,"merge_blocks")) {// has a choice of 3 where 0 is default
			String idx  = (type==FILE) ? fileMap.get("merge_blocks") : dbMap.get("merge_blocks");
			int ix = Utilities.getInt(idx);
			msg = pjoin(msg, "Merge blocks: " + PairParams.mergeOpts[ix]);
		}
		
		if (isChg(type, "order_proj1"))	msg = pjoin(msg, "Order " + mProj1.getDisplayName() + "->" + mProj2.getDisplayName()); 
		if (isChg(type, "order_proj2"))	msg = pjoin(msg, "Order " + mProj2.getDisplayName() + "->" + mProj1.getDisplayName());
		
		if (msg.equals("")) return smsg;
		return smsg + "\n" + msg; 	
	}
	
	private boolean isChg(int type, String field) {
		String db = (type==FILE) ? fileMap.get(field) : dbMap.get(field);
		if (db==null) {
			Globals.prt("No parameter in database: " + field);
			return false;				
		}
		String def = defMap.get(field);
		if (def==null) return false;
		return !db.contentEquals(def);
	}
	private String pjoin(String m1, String m2) {
		if (!m1.equals("") && !m1.startsWith(spp)) m1 = spp + m1;
		if (!m2.equals("") && !m2.startsWith(spp)) m2 = spp + m2;
		if (m1.equals("")) return m2;
		if (m2.equals("")) return m1;
		return m1 + "\n" + m2; 
	}
	/************************************************************************************/
	public int getPairIdx()   {return pairIdx;}
	public int getProj1Idx()  { return mProj1.getIdx();}
	public int getProj2Idx()  { return mProj2.getIdx();}
	
	public Mproject getProj1() {return mProj1;}
	public Mproject getProj2() {return mProj2;}
	
	protected boolean isPairInDB() {return pairIdx>0;}
	public boolean hasSynteny()    {return pairIdx>0;} // if A&S fails, it will not be in database
	
	public boolean isNumPseudo(int type) {
		String x = (type==FILE) ? fileMap.get("number_pseudo") : dbMap.get("number_pseudo");
		return x.contentEquals("1");
	}
	public boolean isAlgo1(int type) {
		String x = (type==FILE) ? fileMap.get("algo1") : dbMap.get("algo1");
		return x.contentEquals("1");
	}
	public int getTopN(int type) {
		String x = (type==FILE) ? fileMap.get("topn") : dbMap.get("topn");
		return Utilities.getInt(x);
	}
	public boolean isAlgo2(int type) {
		String x = (type==FILE) ? fileMap.get("algo2") : dbMap.get("algo2");
		return x.contentEquals("1");
	}
	public double getG0Scale(int type) {
		String x = (type==FILE) ? fileMap.get("g0_scale") : dbMap.get("g0_scale");
		return Utilities.getDouble(x);
	}
	public double getExonScale(int type) {
		String x = (type==FILE) ? fileMap.get("exon_scale") : dbMap.get("exon_scale");
		return Utilities.getDouble(x);
	}
	public double getGeneScale(int type) {
		String x = (type==FILE) ? fileMap.get("gene_scale") : dbMap.get("gene_scale");
		return Utilities.getDouble(x);
	}
	public double getLenScale(int type) {
		String x = (type==FILE) ? fileMap.get("len_scale") : dbMap.get("len_scale");
		return Utilities.getDouble(x);
	}
	public boolean isEEpile(int type) {
		String x = (type==FILE) ? fileMap.get("EE_pile") : dbMap.get("EE_pile");
		return x.contentEquals("1");
	}
	public boolean isEIpile(int type) {
		String x = (type==FILE) ? fileMap.get("EI_pile") : dbMap.get("EI_pile");
		return x.contentEquals("1");
	}
	public boolean isEnpile(int type) {
		String x = (type==FILE) ? fileMap.get("En_pile") : dbMap.get("En_pile");
		return x.contentEquals("1");
	}
	public boolean isIIpile(int type) {
		String x = (type==FILE) ? fileMap.get("II_pile") : dbMap.get("II_pile");
		return x.contentEquals("1");
	}
	public boolean isInpile(int type) {
		String x = (type==FILE) ? fileMap.get("In_pile") : dbMap.get("In_pile");
		return x.contentEquals("1");
	}
	public boolean isConcat(int type) {
		String x = (type==FILE) ? fileMap.get( "concat") : dbMap.get( "concat");
		return x.contentEquals("1");
	}
	public boolean isMask1(int type) {
		String x = (type==FILE) ? fileMap.get( "mask1") : dbMap.get( "mask1");
		return x.contentEquals("1");
	}
	public boolean isMask2(int type) {
		String x = (type==FILE) ? fileMap.get( "mask2") : dbMap.get( "mask2");
		return x.contentEquals("1");
	}
	public boolean isNucmer(int type) {
		String x = (type==FILE) ? fileMap.get( "nucmer_only") : dbMap.get( "nucmer_only");
		return x.contentEquals("1");
	}
	public boolean isPromer(int type) {
		String x = (type==FILE) ? fileMap.get( "promer_only") : dbMap.get("promer_only");
		return x.contentEquals("1");
	}
	public String getNucmerArgs(int type) {
		String x = (type==FILE) ? fileMap.get( "nucmer_args") : dbMap.get("nucmer_args");
		return x;
	}
	public String getPromerArgs(int type) {
		String x = (type==FILE) ? fileMap.get( "promer_args") : dbMap.get("promer_args");
		return x;
	}
	public String getSelfArgs(int type) {
		String x = (type==FILE) ? fileMap.get( "self_args") : dbMap.get("self_args");
		return x;
	}
	public int getMinDots(int type) {
		String x = (type==FILE) ? fileMap.get("mindots") : dbMap.get("mindots");
		return Utilities.getInt(x);
	}
	public String getMergeIndex(int type) {// 3 options
		String x = (type==FILE) ? fileMap.get("merge_blocks") : dbMap.get("merge_blocks");
		return x;
	}
	public boolean isOrient(int type) {
		String x = (type==FILE) ? fileMap.get("same_orient") : dbMap.get("same_orient");
		return x.contentEquals("1");
	}
	public boolean isStrict(int type) {
		String x = (type==FILE) ? fileMap.get("strict_blocks") : dbMap.get("strict_blocks");
		return x!=null && x.contentEquals("1");
	}
	public boolean isOrder1(int type) {
		String x = (type==FILE) ? fileMap.get("order_proj1") : dbMap.get("order_proj1");
		return x.contentEquals("1");
	}
	public boolean isOrder2(int type) {
		String x = (type==FILE) ? fileMap.get("order_proj2") : dbMap.get("order_proj2");
		return x.contentEquals("1");
	}
	/************************************************************************************/
	public HashMap <String, String> getFileParams() { return fileMap;}
	public HashMap <String, String> getDbParams() { return dbMap;} // for PairParams Pseudo
	
	public String getFileParamsStr() {
		String msg="";
		for (String x : fileMap.keySet()) {
			msg += String.format("%15s %s\n", x, fileMap.get(x));
		}
		return msg;
	}
	public HashMap <String, String> getDefaults() { return defMap;}
	
	// ManagerFrame alignAll and alignSelected; remove existing and restart
	public int renewIdx() { 
		removePairFromDB(false); // False; do not redo numHits,  interrupt does not clear it
		
		try { 
			dbc2.executeUpdate("INSERT INTO pairs (proj1_idx,proj2_idx) " +
					" VALUES('" + proj1Idx + "','" + proj2Idx + "')");
			
			pairIdx = dbc2.getIdx("SELECT idx FROM pairs " +
				"WHERE proj1_idx=" + proj1Idx + " AND proj2_idx=" + proj2Idx);
			
			return pairIdx;
		}
		catch (Exception e) {ErrorReport.die(e, "SyMAP error getting pair idx");return -1;}
	}
	/************************************************************
	* Above (!bHitCnt), removeProj (bHitCnt), Cancel (bHitCnt)
	 */
	public int removePairFromDB(boolean bHitCnt) {
	try {
		int x = dbc2.getIdx("select idx from pairs where proj1_idx=" + proj1Idx + " and proj2_idx=" + proj2Idx);
		if (x!= -1) {
			 AnchorMain ancObj = new AnchorMain(dbc2, null, this);
			 ancObj.removePseudo();   // Remove pseudo where numhits=-pair_idx; do before saveAnno; 
			 
			 Globals.prt("Start remove pair from database, please be patient.....");
			 dbc2.executeUpdate("DELETE from pairs WHERE idx="+ x);
			 dbc2.resetAllIdx(); 				// check all, even though some are not relevant
			 
		     if (bHitCnt) ancObj.saveAnnoHitCnt(false); // Redo numhits for this pair; false is to not print
		}
	    pairIdx = -1;
	}
	catch (Exception e) {ErrorReport.print(e, "Error removing pair from database - you may need to Clear Pair (leave alignments)");}
	return -1;
	}
	public boolean removeSyntenyFromDB() { // for synteny only
	try {
		if (pairIdx==-1) return false;
		dbc2.executeUpdate("DELETE from blocks WHERE pair_idx="+ pairIdx);
		dbc2.resetIdx("idx", "blocks");
		return true;
	}
	catch (Exception e) {ErrorReport.print(e, "Error removing pair from database");}
	return false;
	}
	/********** called by PairParams on save; write all ***********/
	public void saveParamsToFile(HashMap <String, String> valMap) { 
		FileDir.checkCreateDir(Constants.dataDir, true); 
		FileDir.checkCreateDir(Constants.seqRunDir, true); 
		FileDir.checkCreateDir(resultDir, true); 
		
		File pfile = new File(resultDir,Constants.paramsFile);
		try {
			PrintWriter out = new PrintWriter(pfile);
			out.println("#");
			out.println("# Pairs parameter file ");
			out.println("# Created " + ErrorReport.getDate());
			out.println("# Note: changes MUST be made in SyMAP parameter window");
			
			for (int i=0; i<paramKey.length; i++) {// write in order; 
				String key = paramKey[i];
				out.format("%s=%s\n", key, valMap.get(key));
			}
	
			out.close();
		} catch(Exception e) {ErrorReport.print(e, "Creating params file");}
	}
	/*********************************************************************/
	public void saveUpdate() {
	try {
		dbc2.executeUpdate("update pairs set aligndate=NOW(), syver='" + Globals.VERSION + "'"
				+ " where (proj1_idx=" + proj1Idx + " and proj2_idx=" + proj2Idx 
				+ ") or   (proj1_idx=" + proj2Idx + " and proj2_idx=" + proj1Idx + ")"); 
	}
	catch (Exception e) {ErrorReport.print(e, "Update version"); return;}
	}
	/**** called by DoAlignSynPair.run() on A&S ***/
	public void saveParamsToDB(String params) {// params are ones not in pair_props
		try {
			if (dbc2.tableColumnExists("pairs", "params"))
				 dbc2.tableCheckModifyColumn("pairs", "params", "text");
			else dbc2.tableCheckAddColumn("pairs", "params", "text", null); 
			
			dbc2.executeUpdate("update projects set hasannot=1,loaddate=NOW() where idx=" + proj1Idx); 
			dbc2.executeUpdate("update projects set hasannot=1,loaddate=NOW() where idx=" + proj2Idx);
			
			dbc2.executeUpdate("update pairs set aligned=1, aligndate=NOW(), "
					+ "syver='" + Globals.VERSION + "', params='" + params + "'" 
					+ " where (proj1_idx=" + proj1Idx + " and proj2_idx=" + proj2Idx 
					+ ") or   (proj1_idx=" + proj2Idx + " and proj2_idx=" + proj1Idx + ")"); 
			
			String st = "DELETE FROM pair_props WHERE pair_idx=" + pairIdx;
			dbc2.executeUpdate(st);
			
			for (String key : fileMap.keySet()) {
				String val = fileMap.get(key);
				st = "INSERT INTO pair_props VALUES ('" + 
						pairIdx + "','" + proj1Idx + "','" + proj2Idx + "','" + key + "','" + val + "')";
				dbc2.executeUpdate(st);
				dbMap.put(key, val);
			}
		}
		catch (Exception e) {ErrorReport.print(e, "Cannot save pair parameters");}
	}

	/******** AnchorMain.run; update one value ************************/
	public void setPairProp(String name, String val) throws Exception {
		dbc2.executeUpdate("delete from pair_props where name='" + name + "' and pair_idx=" + pairIdx);
		
		dbc2.executeUpdate("INSERT INTO pair_props VALUES ('" + 
				pairIdx + "','" + proj1Idx + "','" + proj2Idx + "','" + name + "','" + val + "')");	
	}
	
	/******** If pairs not in DB ***********/
	private boolean loadFromFile() {
	try {
		File pfile = Utils.getParamsFile(resultDir,Constants.paramsFile); 
		if (pfile==null) return false; 				// ok, uses defaults
		
		PropertiesReader props = new PropertiesReader( pfile);
		for (int i=0; i<paramKey.length; i++) {
			String name =  paramKey[i];
			if (props.getProperty(name) != null) {
				for (String x : paramKey) {
					if (x.contains(name)) {	// in case there are old/incorrect params
						fileMap.put(name, props.getProperty(name));
						break;
					}
				}
			}
		}
		for (int i=0; i<paramKey.length; i++) 	// for any additions in release
			if (!fileMap.containsKey(paramKey[i]))
				 fileMap.put(paramKey[i], paramDef[i]);
		return true;
	}
	catch (Exception e) {ErrorReport.print(e, "load from file"); return false; }
	}
	/***** From last A&S *************************************************/
	private void loadFromDB() {
	try {
		ResultSet rs = dbc2.executeQuery("select name,value from pair_props where pair_idx=" + pairIdx);
		while (rs.next()) {
			String name = rs.getString(1);
			String value = rs.getString(2);
			dbMap.put(name, value);
		}
		rs.close();
		for (int i=0; i<paramKey.length; i++) 	// any other further additions
			if (!dbMap.containsKey(paramKey[i]))
				dbMap.put(paramKey[i], paramDef[i]);
	}
	catch (Exception e) {ErrorReport.print(e, "load from db"); }
	}
	private void makeParams() {
		for (int i=0; i<paramKey.length; i++) {
			defMap.put(paramKey[i],  paramDef[i]);	// never changes
			dbMap.put(paramKey[i],   paramDef[i]);	// can changes
			fileMap.put(paramKey[i], paramDef[i]);
		}
	}
	/////////////////////////////////////////////////////
	private void loadSyVer() {
	try {
		ResultSet rs = dbc2.executeQuery("select syver from pairs where idx=" + pairIdx);
		if (rs.next()) syVer = rs.getString(1);
		rs.close();
	}
	catch (Exception e) {ErrorReport.print(e, "load syVer"); }
	}
	// v should be number like 547, so anything version >547
	public boolean isPostVn(int v) { //v546 had bug that added bad pseudo_hit_annot, which only shows up in Every+
	try {
		if (syVer.equals("")) return false;
		
		String x = syVer.substring(1, syVer.length());
		x = x.replaceAll("\\.", "");
		int y = Utilities.getInt(x);
		if (y>v) return true;
		
		return false;
	}
	catch (Exception e) {ErrorReport.print(e, "post v546"); return false;}
	}
	public String toString() {return mProj1.strDisplayName + "-" + mProj2.strDisplayName + " pair";}
}
