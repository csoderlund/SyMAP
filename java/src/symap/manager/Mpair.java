package symap.manager;

import java.io.File;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.util.HashMap;

import backend.Constants;
import backend.anchor1.Group;
import database.DBconn2;
import props.PropertiesReader;
import symap.Globals;
import util.ErrorReport;
import util.Utilities;

/*********************************************************
 * CAS534 pairs stuff is all over the place; this is only used in backend, but will extend...
 * 1. created when enter the selectedPairTable in ManagerFrame
 * 2. if new, create directory and params file; else read params file
 * 3. if previously A&S, read pair_props
 * 4. PairParams save - save all to file
 * 5. AlignProj  save - save all to db
 * 6. Show file params on PairParams
 * 7. Show db params on Summary
 * CAS546 add Algo2 params
 */
public class Mpair {
	public static final int FILE = 0;
	public static final int DB = 1;
	private static final String algo1 = "Cluster Algo1 (modified original)";
	private static final String algo2 = "Cluster Algo2 (exon-intron)";
	private static final String defAlgo = algo1;
	
	public Mproject mProj1, mProj2;
	private int pairIdx=-1, proj1Idx=-1, proj2Idx=-1;
	private DBconn2 dbc2;
	private boolean bReverse=false;
	private String syVer="";
	
	private String resultDir;
	private HashMap <String, String> fileMap = new HashMap <String, String> ();
	private HashMap <String, String> dbMap = new HashMap <String, String> ();
	private HashMap <String, String> defMap = new HashMap <String, String> ();
	
	private static final String [] paramKey = { // repeated in PairParams
			"mindots", "topn", "merge_blocks", 
			"nucmer_args", "promer_args", "self_args", "nucmer_only","promer_only",
			"algo1", "algo2",
			"g0_match", "gintron_match", "gexon_match",
			"EE_pile", "EI_pile", "En_pile", "II_pile", "In_pile"
	};
	private static final String [] paramDef = {
			"7", "2", "0", 
			"", "", "", "0", "0",
			"1", "0",				// 1st is algo1, 2nd is algo2
			"600", "300", "100",	// intergenic, intron, exon
			"1", "1", "1", "0", "0"	// EE, EI, En, II, In CAS548 change EI and En to 1
			};
	
	
	public Mpair (DBconn2 dbc2, int pairIdx, Mproject p1, Mproject p2, boolean isReadOnly) {
		this.dbc2 = dbc2;
		this.mProj1 = p1;
		this.mProj2 = p2;
		this.pairIdx = pairIdx;
		
		proj1Idx=mProj1.getIdx();
		proj2Idx=mProj2.getIdx();
		resultDir = "./" + Constants.getNameResultsDir(p1.strDBName, p2.strDBName);
		
		makeParams();
		if (!isReadOnly)  loadFromFile();
		if (pairIdx!= -1) loadFromDB();
		loadSyVer();
	}
	
	public void reverse() {
		Mproject t=mProj1;
		mProj1 = mProj2;
		mProj2 = t;
		bReverse = !bReverse;
	}
	public boolean isReverse() { return bReverse;}
	protected boolean isSynteny() {return pairIdx>0;} // CAS547 add
	/***********************************************************************************
	 * CAS546 update params a bit
	 */
	public String getChangedParams(int type) {// ManagerFrame for selected when need align; SumFrame (DB type)
		String amsg = getAlign(type);
		String smsg = getSynteny(type);
		
		String fmsg = (!amsg.equals("")) ? amsg+"\n"+smsg : smsg;
		
		return fmsg+"\n";
	}
	public String getChangedSynteny() { // ManagerFrame.alignSelectedPair when no align
		return getSynteny(FILE) + "\n";
	}
	
	public String getChangedAlign() { // AlignMain write to terminal
		String msg = getAlign(FILE);
		String fmsg = (msg.trim().equals("")) ? "" : "\nNon-default parameters: " + msg;
		return fmsg+"\n";
	}
	
	// these all return "" if no change
	private String getAlign(int type) {
		String msg="";
		if (isChg(type,"self_args")) 	msg += "\n  Self args: "   + getSelfArgs(type);
		if (isChg(type,"promer_args")) 	msg += "\n  PROmer args: " + getPromerArgs(type);
		if (isChg(type,"nucmer_args")) 	msg += "\n  NUCmer args: " + getNucmerArgs(type);
		if (isChg(type,"promer_only")) 	msg += "\n  PROmer only";
		if (isChg(type,"nucmer_only")) 	msg += "\n  NUCmer only";
		
		msg += mProj1.getMaskedParam();
		msg += mProj2.getMaskedParam();
		return msg;
	}
	private String getSynteny(int type) { // SyntenyMain only
		
		String msg = getAlgo(type);
		
		if (isChg(type,"mindots")) 		msg += "\n  Min dots=" + getMinDots(type) + "  ";
		if (isChg(type,"merge_blocks")) msg += "\n  Merge blocks" + "  ";
		
		String fmsg = (msg.trim().equals("")) ? "Use default parameters with " + defAlgo : 
            "Non-default parameters: " + msg;
		
		fmsg += mProj1.getOrderParam();
		fmsg += mProj2.getOrderParam();
		
		return fmsg;
	}
	
	private String getAlgo(int type) {
		String msg="";
		
		if (isAlgo2(type)) {
			boolean chg = isChg(type,"g0_match") || isChg(type,"gintron_match") || isChg(type,"gexon_match") 
			|| isChg(type, "EI_pile") || isChg(type, "En_pile") || isChg(type, "II_pile") 
			|| isChg(type, "In_pile") || isChg(type,"topn") || backend.Constants.WRONG_STRAND_EXC;
			if (chg) {
				msg = "\n" + algo2;
				if (isChg(type,"g0_match")) 		msg += "\n    Intergenic base match = " + getG0Match(type);
				if (isChg(type,"gintron_match")) 	msg += "\n    Intron base match = " + getGintronMatch(type);
				if (isChg(type,"gexon_match")) 		msg += "\n    Exon base match = " + getGexonMatch(type);
				
				if (isChg(type,"EE_pile"))			msg += "\n    Limit Exon-Exon piles";
				if (isChg(type,"EI_pile"))			msg += "\n    Limit Exon-Intron piles"; // CAS549 did not chg msg
				if (isChg(type,"En_pile"))			msg += "\n    Limit Exon-intergenic piles";
				if (isChg(type,"II_pile"))			msg += "\n    Allow Intron-Intron piles";
				if (isChg(type,"In_pile"))			msg += "\n    Allow Intron-intergenic piles";
				if (isChg(type,"topn")) 			msg += "\n    Top N piles ="    + getTopN(type);
				if (backend.Constants.WRONG_STRAND_EXC) msg += "\n    Exclude g2 wrong strand hits";
			}
		}
		else if (isAlgo1(type)) {
			boolean chg = isChg(type, "topn") || Group.bSplitGene;
			if (chg) {
				msg += "\n" + algo1;
				if (isChg(type,"topn")) msg += "\n    Top N piles ="    + getTopN(type);
				if (Group.bSplitGene)   msg += "\n    Split gene";
			}
		}
		if (msg.equals("")) { // so I can change the default without changing this method
			if (defMap.get("algo2")=="0" && isChg(type, "algo2")) msg = "\n" + algo2;
			else if (defMap.get("algo1")=="0" && isChg(type, "algo1")) msg = "\n" + algo1;
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
	public int getG0Match(int type) {
		String x = (type==FILE) ? fileMap.get("g0_match") : dbMap.get("g0_match");
		return Utilities.getInt(x);
	}
	public int getGexonMatch(int type) {
		String x = (type==FILE) ? fileMap.get("gexon_match") : dbMap.get("gexon_match");
		return Utilities.getInt(x);
	}
	public int getGintronMatch(int type) {
		String x = (type==FILE) ? fileMap.get("gintron_match") : dbMap.get("gintron_match");
		return Utilities.getInt(x);
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
	public int getMinDots(int type) {
		String x = (type==FILE) ? fileMap.get("mindots") : dbMap.get("mindots");
		return Utilities.getInt(x);
	}
	public boolean isMerge(int type) {
		String x = (type==FILE) ? fileMap.get("merge_blocks") : dbMap.get("merge_blocks");
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
	
	public int renewIdx() {
		removePairFromDB(); // CAS535 interrupt does not clear it
		
		try { 
			dbc2.executeUpdate("INSERT INTO pairs (proj1_idx,proj2_idx) " +
					" VALUES('" + proj1Idx + "','" + proj2Idx + "')");
			
			pairIdx = dbc2.getIdx("SELECT idx FROM pairs " +
				"WHERE proj1_idx=" + proj1Idx + " AND proj2_idx=" + proj2Idx);
			
			return pairIdx;
		}
		catch (Exception e) {ErrorReport.die(e, "SyMAP error getting pair idx");return -1;}
	}
	public int removePairFromDB() {
	try {
		
		int x = dbc2.getIdx("select idx from pairs where proj1_idx=" + proj1Idx + " and proj2_idx=" + proj2Idx);
		if (x!= -1) {
			 dbc2.executeUpdate("DELETE from pairs WHERE idx="+ x);
			 dbc2.resetAllIdx(); // CAS535 check all, even though some are not relevant
		}
	    pairIdx = -1;
	}
	catch (Exception e) {ErrorReport.print(e, "Error removing pair from database - you may need to Clear Pair (leave alignments)");}
	return -1;
	}
	// called by PairParams on save
	public void writeFile(HashMap <String, String> valMap) { // CAS534 used to only write changed; now writes all
		Utilities.checkCreateDir(Constants.seqRunDir, true); 
		Utilities.checkCreateDir(resultDir, true); 
		
		File pfile = new File(resultDir,Constants.paramsFile);
		try {
			PrintWriter out = new PrintWriter(pfile);
			out.println("#");
			out.println("# Pairs parameter file ");
			out.println("# Created " + ErrorReport.getDate());
			out.println("# Note: changes MUST be made in SyMAP parameter window");
			
			for (String key : valMap.keySet()) 
				out.format("%s=%s\n", key, valMap.get(key));
	
			out.close();
		} catch(Exception e) {ErrorReport.print(e, "Creating params file");}
	}
	// called by AlignProj on A&S; CAS535 move update date from AlignProjs to here
	public void saveParams(String params) {
		try {
			dbc2.tableCheckAddColumn("pairs", "params", "VARCHAR(128)", null); // CAS511 add params column (not schema update)
			
			dbc2.executeUpdate("update pairs set aligned=1, aligndate=NOW(), "
					+ "syver='" + Globals.VERSION + "', params='" + params + "'" // CAS520 add syver
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

	// AnchorMain.run
	public void setPairProp(String name, String val) throws Exception {
		dbc2.executeUpdate("delete from pair_props where name='" + name + "' and pair_idx=" + pairIdx);
		
		dbc2.executeUpdate("INSERT INTO pair_props VALUES ('" + 
				pairIdx + "','" + proj1Idx + "','" + proj2Idx + "','" + name + "','" + val + "')");	
	}
	/***************************************************************************
	 * private
	 */
	private void loadFromFile() {
	try {
		if (!Utilities.dirExists(resultDir)) return; // ok, created when needed
		
		File pfile = new File(resultDir,Constants.paramsFile); 
		if (!pfile.isFile()) return; 				// ok, uses defaults
		
		PropertiesReader props = new PropertiesReader( pfile);
		for (int i=0; i<paramKey.length; i++) {
			String name =  paramKey[i];
			if (props.getProperty(name) != null) {
				fileMap.put(name, props.getProperty(name));
			}
		}
	}
	catch (Exception e) {ErrorReport.print(e, "load from file"); }
	}
	private void loadFromDB() {
	try {
		ResultSet rs = dbc2.executeQuery("select name,value from pair_props where pair_idx=" + pairIdx);
		while (rs.next()) {
			String name = rs.getString(1);
			String value = rs.getString(2);
			dbMap.put(name, value);
		}
		rs.close();
	}
	catch (Exception e) {ErrorReport.print(e, "load from db"); }
	}
	private void makeParams() {
		for (int i=0; i<paramKey.length; i++) {
			defMap.put(paramKey[i], paramDef[i]);
			dbMap.put(paramKey[i], paramDef[i]);
			fileMap.put(paramKey[i], paramDef[i]);
		}
	}
	///// CAS547 add ////////////////////////////////////////////////
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
