package symap.manager;

import java.io.File;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.util.HashMap;

import backend.AnchorMain;
import backend.Constants;
import backend.anchor1.Group;
import database.DBconn2;
import props.PropertiesReader;
import symap.Globals;
import util.ErrorReport;
import util.Utilities;

/*********************************************************
 * this is mainly used in backend
 * 1. created when enter the selectedPairTable in ManagerFrame
 * 2. if new, create directory and params file; else read params file
 * 3. if previously A&S, read pair_props
 * 4. PairParams save - save all to file
 * 5. AlignProj  save - save all to db
 * 6. Show file params on PairParams
 * 7. Show db params on Summary
 * CAS546 add Algo2 params; CAS565 add Number Pseudo; CAS567 add Concat, Orient and reorder
 */
public class Mpair {
	public static final int FILE = 0;
	public static final int DB = 1;
	private static final String algo1 = "Cluster Algo1 (modified original)";
	private static final String algo2 = "Cluster Algo2 (exon-intron)";
	
	public Mproject mProj1, mProj2;
	protected int pairIdx=-1; 		// accessed in ManagerFrame; CAS567	
	private int proj1Idx=-1, proj2Idx=-1;
	private DBconn2 dbc2;
	private String syVer="";
	
	private String resultDir;
	private HashMap <String, String> fileMap = new HashMap <String, String> ();
	private HashMap <String, String> dbMap = new HashMap <String, String> ();
	private HashMap <String, String> defMap = new HashMap <String, String> ();
	
	// These must be in same order as PairParam arrays; the order isn't needed anywhere else in this file
	private static final String [] paramKey = { // repeated in PairParams and below
			"mindots", "merge_blocks", "same_orient",
			"concat", "nucmer_args", "promer_args", "self_args", "nucmer_only","promer_only",
			"number_pseudo","algo1", "algo2",
			"topn", 
			"g0_scale", "gene_scale", "exon_scale",	"len_scale",	
			"EE_pile", "EI_pile", "En_pile", "II_pile", "In_pile"
	};
	private static final String [] paramDef = { // defMap value
			"7", "0", "0", 			    // synteny
			"1", "", "", "", "0", "0",	// mummer
			"0",					    // cluster, number pseudo
			"1", "0",				    // 1st is algo1, 2nd is algo2
			"2", 					    // topn
			"1.0", "1.0", "1.0", "1.0",	// G0_Len, gene, exon, Len	
			"1", "1", "1", "0", "0"	    // EE, EI, En, II, In 
	};
	
	public Mpair(DBconn2 dbc2, int pairIdx, Mproject p1, Mproject p2, boolean isReadOnly) {
		this.dbc2 = dbc2;
		this.mProj1 = p1;		// order is based on pairs table, or either can come first
		this.mProj2 = p2;
		this.pairIdx = pairIdx;
	
		proj1Idx=mProj1.getIdx();
		proj2Idx=mProj2.getIdx();
		resultDir = "./" + Constants.getNameResultsDir(p1.strDBName, p2.strDBName);
		
		makeParams();
		if (!isReadOnly)  loadFromFile(); // loads into FileMap
		if (pairIdx!= -1) loadFromDB();  //  loads into dbMap
		
		loadSyVer();
	}
	
	/************************************************************************************/
	protected String getChangedSynteny() { // ManagerFrame.alignSelectedPair when no align
		return getSynteny(FILE) + "\n";
	}
	
	public String getChangedAlign() { // AlignMain write to terminal
		String msg = getAlign(FILE);
		if (msg.isEmpty()) return "";
		return msg;
	}
	public String getChangedParams(int type) {// ManagerFrame for selected when need align; SumFrame (DB type)
		String amsg = getAlign(type);   // could be empty
		String smsg = getSynteny(type); // never empty
		return join(amsg, smsg);
	}
	
	private String getAlign(int type) {// return "" if no change
		String msg="";
		if (isChg(type,"concat")) 			msg = join(msg, "No concat");	// CAS567 add
		if (isChg(type,"promer_only")) 		msg = join(msg, "Align PROmer only  ");
		else if (isChg(type,"nucmer_only")) msg = join(msg, "Align NUCMER only  ");
		
		if (isChg(type,"self_args")) 		msg = " Self args: "   + getSelfArgs(type); 
		else if (isChg(type,"promer_args")) msg = " PROmer args: " + getPromerArgs(type);
		else if (isChg(type,"nucmer_args")) msg = " NUCmer args: " + getNucmerArgs(type);
		
		msg = join(msg, mProj1.getMaskedParam());
		msg = join(msg, mProj2.getMaskedParam());
		
		return msg;
	}
	private String getSynteny(int type) { // SyntenyMain only
		String cmsg = getClusterAlgo(type); // never empty
		
		String smsg="";
		if (isChg(type,"mindots")) 		smsg = join(smsg, " Min hits=" + getMinDots(type));
		if (isChg(type,"merge_blocks")) smsg = join(smsg, " Merge");
		if (isChg(type,"same_orient"))  smsg = join(smsg, " Same orient");
		
		smsg = join(smsg, mProj1.getOrderParam());
		smsg = join(smsg, mProj2.getOrderParam());
		
		if (!smsg.isEmpty()) smsg = "Synteny Blocks\n" + smsg;
		
		return join(cmsg, smsg);
	}
	
	private String getClusterAlgo(int type) {
		String msg="";
		if (isChg(type,"number_pseudo"))	msg += "Number pseudo\n"; // algo will come after
		
		if (isAlgo2(type)) {
			boolean chg = isChg(type,"g0_scale") || isChg(type,"gene_scale") 
					   || isChg(type,"exon_scale" ) || isChg(type,"len_scale")
					   || isChg(type, "EI_pile") || isChg(type, "En_pile") || isChg(type, "II_pile") 
					   || isChg(type, "In_pile") || isChg(type,"topn") || isChg(type,"number_pseudo");
			
			msg += algo2;
			if (chg) {
				if (isChg(type,"exon_scale")) 	msg += "\n  Exon scale = " + getExonScale(type);
				if (isChg(type,"gene_scale")) 	msg += "\n  Gene scale = " + getGeneScale(type);
				if (isChg(type,"len_scale")) 	msg += "\n  Length scale = " + getLenScale(type);
				if (isChg(type,"g0_scale")) 	msg += "\n  G0 length scale = " + getG0Scale(type);
				
				if (isChg(type,"EE_pile"))		msg += "\n  Limit Exon-Exon piles";
				if (isChg(type,"EI_pile"))		msg += "\n  Limit Exon-Intron piles"; 
				if (isChg(type,"En_pile"))		msg += "\n  Limit Exon-intergenic piles";
				if (isChg(type,"II_pile"))		msg += "\n  Allow Intron-Intron piles";
				if (isChg(type,"In_pile"))		msg += "\n  Allow Intron-intergenic piles";
				if (isChg(type,"topn")) 		msg += "\n  Top N piles ="    + getTopN(type);
			}
		}
		else if (isAlgo1(type)) {
			msg += algo1; 				// always lists clustering
			boolean chg = isChg(type, "topn") ||  Group.bSplitGene;
			if (chg) {
				if (isChg(type,"topn")) msg += "\n  Top N piles ="    + getTopN(type);
				if (Group.bSplitGene)   msg += "\n  Split gene";
			}
		}
		return msg;
	}
	
	private boolean isChg(int type, String field) {
		String db = (type==FILE) ? fileMap.get(field) : dbMap.get(field);
		if (db==null) System.out.println("no " + field);
		String def = defMap.get(field);
		return !db.contentEquals(def);
	}
	/************************************************************************************/
	public int getPairIdx()   {return pairIdx;}
	public int getProj1Idx()  { return mProj1.getIdx();}
	public int getProj2Idx()  { return mProj2.getIdx();}
	
	public Mproject getProj1() {return mProj1;}
	public Mproject getProj2() {return mProj2;}
	
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
	
	public boolean isConcat(int type) {// CAS567 add
		String x = (type==FILE) ? fileMap.get( "concat") : dbMap.get( "concat");
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
	public boolean isMerge(int type) {
		String x = (type==FILE) ? fileMap.get("merge_blocks") : dbMap.get("merge_blocks");
		return x.contentEquals("1");
	}
	public boolean isOrient(int type) {
		String x = (type==FILE) ? fileMap.get("same_orient") : dbMap.get("same_orient");
		return x.contentEquals("1");
	}
	/************************************************************************************/
	public HashMap <String, String> getFileParams() { return fileMap;}
	
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
		removePairFromDB(false); // False; do not redo numHits, CAS566 add F; interrupt does not clear it
		
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
			 ancObj.removePseudo();   // Remove pseudo where numhits=-pair_idx; do before saveAnno; CAS565 add
			 
			 dbc2.executeUpdate("DELETE from pairs WHERE idx="+ x);
			 dbc2.resetAllIdx(); 				// check all, even though some are not relevant
			 
		     if (bHitCnt) ancObj.saveAnnoHitCnt(); // Redo numhits for this pair; CAS561 add; CAS566 not for reDo A&S 
		}
	    pairIdx = -1;
	}
	catch (Exception e) {ErrorReport.print(e, "Error removing pair from database - you may need to Clear Pair (leave alignments)");}
	return -1;
	}
	/********** called by PairParams on save; write all ***********/
	public void writeFile(HashMap <String, String> valMap) { 
		Utilities.checkCreateDir(Constants.seqRunDir, true); 
		Utilities.checkCreateDir(resultDir, true); 
		
		File pfile = new File(resultDir,Constants.paramsFile);
		try {
			PrintWriter out = new PrintWriter(pfile);
			out.println("#");
			out.println("# Pairs parameter file ");
			out.println("# Created " + ErrorReport.getDate());
			out.println("# Note: changes MUST be made in SyMAP parameter window");
			
			for (int i=0; i<paramKey.length; i++) {// write in order; CAS567
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
	public void saveParams(String params) {// params are ones not in pair_props
		try {
			dbc2.tableCheckAddColumn("pairs", "params", "VARCHAR(128)", null); 
			
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
		if (!Utilities.dirExists(resultDir)) return false; // ok, created when needed
		
		File pfile = new File(resultDir,Constants.paramsFile); 
		if (!pfile.isFile()) return false; 				// ok, uses defaults
		
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
		for (int i=0; i<paramKey.length; i++) 	// CAS567 add 'concat' and any other further additions
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
		for (int i=0; i<paramKey.length; i++) 	// CAS567 add 'concat' and any other further additions
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
	private String join(String m1, String m2) {
		if (m1.isEmpty()) return m2;
		if (m2.isEmpty()) return m1;
		return m1 + "\n" + m2;
	}
}
