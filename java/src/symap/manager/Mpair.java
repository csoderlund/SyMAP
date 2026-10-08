package symap.manager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
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
	private static final String mummer = "MUMmer";
	private static final String promer = "PROmer"; // Also hardcoded in PairParams - no where else
	private static final String nucmer = "NUCmer";
	private static final String loadPrefix = "Load";    
	
	// sections
	private static final String ALIGN   = "  Align "; // space before PROmer or NUCmer
	private static final String LOAD	= "  Load Blast File";
	private static final String ALGO1   = "  Cluster Algo1 (modified original)";
	private static final String ALGO2   = "  Cluster Algo2 (exon-intron)";
	private static final String SYNORIG = "  Synteny Original";
	private static final String SYNSTRI = "  Synteny Strict";		
	private static final String spp     = "       ";	// space to go before parameters;
	
	public Mproject mProj1, mProj2;
	protected int pairIdx=-1; 		// accessed in ManagerFrame; 
	private int proj1Idx=-1, proj2Idx=-1;
	private DBconn2 dbc2;
	
	protected boolean hasMUMmer=false, hasBlast=false;
	protected String alignStr="";
	
	public boolean bPseudo=false, bSynOnly=false, bCoSet=false; // set in PairParams and read in ManagerFrame
	
	private String resultDir;									// data/seq_results/mProj1_to_mProj2
	private HashMap <String, String> fileMap = new HashMap <String, String> ();
	private HashMap <String, String> dbMap = new HashMap <String, String> ();
	private HashMap <String, String> defMap = new HashMap <String, String> ();
	private boolean isSelf=false, isAlignNT=false, isOnlyBlast=false; 	// CAS589 add isAlignNT; CAS590 add isOnlyBlast
	
	// These must be in same order as PairParam.SYMBOLS arrays; the order isn't needed anywhere else in this file
	private static final String [] paramKey = { // repeated exactly in PairParams and defaults below
			// Alignment
			"MUMmer", 
			"concat", "mask1", "mask2",
			"nucmer_args", "promer_args", "self_args", "nucmer_only","promer_only",
			"Blast", 
			// Cluster
			"number_pseudo",
			"algo1", "topn", 					// algo1 is default for self, order, no genes, else algo2
			"algo2", 
			"gene_scale", "exon_scale","len_scale","g0_scale", 
			"EE_pile", "EI_pile", "En_pile", "II_pile", "In_pile",
			// Collinear sets	
			"CoSet", 	// two booleans together, e.g. 1:0 
			// synteny
			"mindots", "strict_blocks", "same_orient", "merge_blocks",
			// Order
			"order_none", "order_proj1", "order_proj2",			 							
	};
	// defMap value; not static because specific to pair, e.g. self-synteny, order, regular
	// CHANGE these if anything is changed
	
	private String [] paramDef = {  	
			"1", 						// 1 Use Mummer
			"1", "0", "0", 				// 2-4  concat, mask1, mask2
			"", "", "", "0", "0",		// 5-9  nucmer, promer, self, only, only
			"0", 						// 0 Load Blast
			//Cluster
			"0",						// 10 number pseudo,  
			"0",						// 11 algo1
			"2", 					    // 12   topn
			
			"1",						// 13 algo2
			"1.0", "1.0", "1.0", "1.0",	// 14-17  gene, exon, Len, G0_Len	
			"1", "1", "1", "0", "0", 	// 18-22  EE, EI, En, II, In 
			
			"0:0",						// 23 Collinear  Num (default order):Algo (default new)
			//Synteny
			"7", "1", "0",	"0",        // 24-27 hits, strict, orient, merge; strict is default except for when there are many contigs
			"1", "0", "0"				// order against: none, proj1->proj2, proj2->proj1
	};
	
	public Mpair(DBconn2 dbc2, int pairIdx, Mproject p1, Mproject p2, boolean isReadOnly) {
		this.dbc2 = dbc2;
		this.mProj1 = p1;		// order is based on pairs table, or either can come first
		this.mProj2 = p2;
		this.pairIdx = pairIdx;
		
		proj1Idx = mProj1.getIdx();
		proj2Idx = mProj2.getIdx();
		isSelf = proj1Idx==proj2Idx;
		
		// Set defaults based on database
		int defStrict=25, defAlgo1=11, defAlgo2=13; 
		for (int i=0; i<paramKey.length; i++) {
			if (paramKey[i].equals("algo1")) defAlgo1=i;
			else if (paramKey[i].equals("algo2")) defAlgo2=i;
			else if (paramKey[i].equals("strict_blocks")) defStrict=i;
		}
		if (!isSelf && p1.hasGenes() && p2.hasGenes()) 	{paramDef[defAlgo1]="0"; paramDef[defAlgo2]="1";}
		else 											{paramDef[defAlgo1]="1"; paramDef[defAlgo2]="0";}
		
		if (p1.getNumDraft()>25 || p2.getNumDraft()>25) {		// strict is not good with many small contigs, i.e. ordering
			paramDef[defStrict] = "0"; 
		}
		String o = Constants.orderDelim; 						// ..	also does not work well with ordered contigs		
		if ((mProj1.getDBName().contains(o) || mProj2.getDBName().contains(o))) {
			paramDef[defStrict] = "0"; 
		}
		
		// Finish params
		resultDir = "./" + Constants.getNameResultsDir(p1.strDBName, p2.strDBName);
		
		makeParams();
		if (!isReadOnly)  loadFromFile(); // loads into FileMap
		if (pairIdx!= -1) loadFromDB();  //  loads into dbMap
		
		setAligns();
	}
	protected void setAligns() {// check here so can reuse values; called from ManagerFrame for updates; CAS589 add; 
	try {
		hasMUMmer 		 = Utils.checkDoneMUMmer(resultDir + Constants.alignDir);
		hasBlast 		 = Utils.checkDoneBlast(resultDir + Constants.blastDir);
		
		if (hasBlast && hasMUMmer) alignStr = "MUMmer/Blast files";
		else if (hasBlast)         alignStr = "Blast files";
		else if (hasMUMmer)        alignStr = "MUMmer files";
		else alignStr="";
	}
	catch (Exception e) {ErrorReport.print(e, "check align/blast directories");  }
	}
	protected void setHasBlast() {
		String name = Utils.getBlastFileList(resultDir);
	    hasBlast = (name!=null);
	}
	/************************************************************************************/
	// Summary: The folllowing lines can be in dbParam
	// -MUMmer files 05-Oct-26 08:11   CPUs: 12
	//    Align PROmer
	// -Use MUMmer
	//    Align PROmer
	// -Load
	// CHANGE WITH CAUTION: This is checked later for the strings PROmer, MUMmer, NUCmer, Load
	public String getParamsForSum(String dbParams) {
		String fullmsg="";
		if (dbParams!=null && !dbParams.isBlank()) {
			if (dbParams.contains(mummer)) { // Alignment params are on dbParams
				String cmsg = getChgCluster(DB);		
				
				if (dbParams.contains("\n")) { // take it apart to put cluster under mummer
					String [] tok = dbParams.split("\n");
					
					int load=tok.length+1;
					for (int i=0; i<tok.length; i++) {
						if (tok[i].startsWith(loadPrefix)) {load=i; break;}
						fullmsg += tok[i] + "\n";
					}
					fullmsg += cmsg + "\n";
					for (int i=load; i<tok.length; i++) {
						fullmsg += tok[i] + "\n";
					}
				}
				else {
					fullmsg = dbParams + "\n"  + cmsg;
				}
			}
			else fullmsg = dbParams; // Load only
		}
		String smsg = getChgSynteny(DB);  
		
		if (fullmsg.endsWith("\n")) return fullmsg + smsg;
		else                        return fullmsg + "\n" + smsg;
	}
	
	// ManagerFrame.alignSelectPair popop when need align; DoAlignSynPair terminal, dialog, symap.log
	public String getChgAllParams(int type) {
		String amsg = getChgAlign(type, false);   
		String cmsg = getChgCluster(type); 
		String smsg = getChgSynteny(type); 
		
		return amsg + "\n" + cmsg + "\n" + smsg; // none are empty
	}
	
	// ManagerFrame.alignSelectedPair popup when no align; DoAlignSynPair terminal, dialog, symap.log
	public String getChgClustSyn(int type) { 
		if (isNotDefault(type, "Blast")) return getChgLoad(type); // Load Blast selected
		
		String cmsg = getChgCluster(type); 
		String smsg = getChgSynteny(type); 
		
		if (type==FILE) {
			String amsg = getUseExistAlign(); // CAS588 include on initial popup
			return amsg + "\n" + cmsg + "\n" + smsg;
		}
		else return cmsg + "\n" + smsg; // Summary has type=DB, so gets existing from pairs.params
	}
	public String getChgLoad(int type) {
		String cmsg = getChgAlign(type, false);
		String smsg = getChgSynteny(type); 
		return cmsg + "\n" + smsg;
	}
	public String getChgAlign(int type, boolean forSum) {
		String msg="";
		if (isNotDefault(type, "Blast")) { // blast selected; CAS589 
			msg = LOAD;
			
			String fname = Utils.getBlastFileList(resultDir);
			if (fname==null) return msg + "\n" + spp + "NO BLAST FILE!!";
			
			hasBlast=true;
			
			msg = msg + "\n" + spp + fname;
			if (pairIdx>0) msg += "\n" + spp + "add to existing";
			return msg;
		}
			
		if (isNotDefault(type,"concat")) 			msg = pjoin(msg, "No concat");	
		if (isNotDefault(type,"mask1")) 			msg = pjoin(msg, "Mask " + mProj1.getDisplayName());	
		if (isNotDefault(type,"mask2")) 			msg = pjoin(msg, "Mask " + mProj2.getDisplayName());	
		
		if (isNotDefault(type,"promer_only")) 		msg = pjoin(msg, "Use PROmer ");// CAS589 was 'PROmer only'
		else if (isNotDefault(type,"nucmer_only"))  msg = pjoin(msg, "Use NUCmer ");
		
		if (isNotDefault(type,"self_args")) 		msg = pjoin(msg, "Self args: "   + getSelfArgs(type)); 
		else if (isNotDefault(type,"promer_args"))  msg = pjoin(msg, "PROmer args: " + getPromerArgs(type));
		else if (isNotDefault(type,"nucmer_args"))  msg = pjoin(msg, "NUCmer args: " + getNucmerArgs(type));
		
		if (!forSum && Constants.MUM_NO_RM)     msg = pjoin(msg, "Keep MUMmer .delta file"); 
		
		if (!msg.equals("")) {
			if (msg.contains(promer) || msg.contains(nucmer)) return ALIGN + "\n" + msg;
			else if (isSelf)                                  return ALIGN + nucmer + "\n" + msg;
			else                                              return ALIGN + promer + "\n" + msg;
		}
		if (isSelf) return ALIGN + nucmer ; // always remind what is being used
		return ALIGN + promer;
	}
	
	private String getChgCluster(int type) {
		String msg="";
		if (isAlgo2(type)) {
			if (isNotDefault(type,"exon_scale")) 	msg = pjoin(msg, "Exon scale = " + getExonScale(type)); 
			if (isNotDefault(type,"gene_scale")) 	msg = pjoin(msg, "Gene scale = " + getGeneScale(type));
			if (isNotDefault(type,"len_scale")) 	msg = pjoin(msg, "Length scale = " + getLenScale(type));
			if (isNotDefault(type,"g0_scale")) 	msg = pjoin(msg, "G0 length scale = " + getG0Scale(type));
			
			if (isNotDefault(type,"EE_pile"))		msg = pjoin(msg, "Limit Exon-Exon piles");
			if (isNotDefault(type,"EI_pile"))		msg = pjoin(msg, "Limit Exon-Intron piles"); 
			if (isNotDefault(type,"En_pile"))		msg = pjoin(msg, "Limit Exon-intergenic piles");
			if (isNotDefault(type,"II_pile"))		msg = pjoin(msg, "Allow Intron-Intron piles");
			if (isNotDefault(type,"In_pile"))		msg = pjoin(msg, "Allow Intron-intergenic piles");
			if (isNotDefault(type,"topn")) 		msg = pjoin(msg, "Top N piles = " + getTopN(type));
			if (isNotDefault(type,"number_pseudo")) msg = pjoin(msg, "Number pseudo"); 
			
			if (isNotDefault(type,"CoSet")) {
				if (!isCoSetNum(type)) msg = pjoin(msg,  "Order Collinear by size");
				if (!isCoSetAlgo(type)) msg = pjoin(msg, "Simple Collinear Algo");
			}
			if (msg.equals("")) msg = ALGO2;
			else                msg = ALGO2 + "\n" + msg;
		}
		else if (isAlgo1(type)) {
			if (isNotDefault(type,"topn")) msg = pjoin(msg,"Top N piles =" + getTopN(type));
			if (Group.bSplitGene)   msg = pjoin(msg,"Split gene");
			if (isNotDefault(type,"number_pseudo")) msg = pjoin(msg, "Number pseudo"); // algo will come after
			
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
		
		if (isNotDefault(type,"mindots")) 		msg = pjoin(msg, "Min hits=" + getMinDots(type));
		if (isNotDefault(type,"same_orient"))  msg = pjoin(msg, "Same orient");
		if (isNotDefault(type,"merge_blocks")) {// has a choice of 3 where 0 is default
			String idx  = (type==FILE) ? fileMap.get("merge_blocks") : dbMap.get("merge_blocks");
			int ix = Utilities.getInt(idx);
			msg = pjoin(msg, "Merge blocks: " + PairParams.mergeOpts[ix]);
		}
		
		if (isNotDefault(type, "order_proj1"))	msg = pjoin(msg, "Order " + mProj1.getDisplayName() + "->" + mProj2.getDisplayName()); 
		if (isNotDefault(type, "order_proj2"))	msg = pjoin(msg, "Order " + mProj2.getDisplayName() + "->" + mProj1.getDisplayName());
		
		if (msg.equals("")) return smsg;
		return smsg + "\n" + msg; 	
	}
	protected String getChgCoSet(int type) {
		String msg="";
		if (!isCoSetNum(type)) msg = pjoin(msg,  "Collinear order by size");
		if (!isCoSetAlgo(type)) msg = pjoin(msg, "Collinear Simple");
		return msg;
	}
	private boolean isNotDefault(int type, String field) {
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
	/* read align_params_used.txt file for A&S popup; 
	 * only PROMER/NUCMER, MASK and any MUMmer args (skip No concat and M4 directory) 
	 * Read and written in AlignMain */
	private String getUseExistAlign() { // this is also read in AlignMain, but contains a little more info
		String align="  Use existing MUMmer files"; 
		try {		
			File pfile = Utils.getParamsFile(resultDir,Constants.usedFile); 
			if (pfile==null) return align; // okay, not written yet
			
			String sp="     ";
			String line, params="", mask=sp, msg = ""; 
			BufferedReader reader = new BufferedReader(new FileReader(pfile));
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (!line.startsWith("#")) {
					if (msg.isBlank()) {
						msg = "  Use " + line;
						msg = msg.substring(0, msg.indexOf("CPU")); // remove CPU
					}
					else if (line.startsWith("Align")) msg += line.replace("Align ", ""); // remove Align
					else if (line.contains("Mask")) {
						if (line.contains("/")) line = line.substring(0, line.indexOf("/")); // was writing M4 path on same line
						mask += line + " ";		
					}
					else if (!line.contains("No concat") && !line.startsWith("/")){// only mummer args here
						if (line.length()>40) line = line.substring(0, params.length()) + "...";
						if (params.isBlank()) params = sp + line;
						else                  params += "\n" + sp + line; // nl for each new param line
					}
				}
			}
			reader.close();
			if (msg.isBlank()) return align;
			
			if (!mask.isBlank())   msg += "\n" + mask;
			if (!params.isBlank()) msg += "\n" + params;
			
			return msg;
		}
		catch (Exception e) {ErrorReport.print(e, "Read " + Constants.usedFile); return align;}
	}
	/************************************************************************************/
	public int getPairIdx()   {return pairIdx;}
	public int getProj1Idx()  { return mProj1.getIdx();}
	public int getProj2Idx()  { return mProj2.getIdx();}
	
	public Mproject getProj1() {return mProj1;}
	public Mproject getProj2() {return mProj2;}
	
	public boolean isPairInDB() {return pairIdx>0;}
	
	public String getResultDir() {return resultDir;}
	
	public boolean isOnlyBlast() { // CAS590 add
		return isOnlyBlast;
	}
	
	public boolean isBlast(int type) { // CAS589 add
		String x = (type==FILE) ? fileMap.get("Blast") : dbMap.get("Blast");
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
	
	public boolean isNumPseudo(int type) {
		String x = (type==FILE) ? fileMap.get("number_pseudo") : dbMap.get("number_pseudo");
		return x.contentEquals("1");
	}
	public boolean isAlgoX(int type) { // neither have exLap
		if (isBlast(type)) return true;
		return isAlgo1(type);
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
	public boolean isCoSetNum(int type) {
		String x = (type==FILE) ? fileMap.get("CoSet") : dbMap.get("CoSet");
		String [] y = x.split(":");
		if (y.length!=2) return true;
		return y[0].contentEquals("0");
	}
	public boolean isCoSetAlgo(int type) {
		String x = (type==FILE) ? fileMap.get("CoSet") : dbMap.get("CoSet");
		String [] y = x.split(":");
		if (y.length!=2) return true;
		return y[1].contentEquals("0");
	}
	public boolean isAlignNT() {return isAlignNT;}
	
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
	
	// ManagerFrame.alignSelected; remove existing and restart
	public int renewIdx() { 
		removePairFromDB(false); // False; do not redo numHits,  interrupt does not clear it
		
		try { 
			dbc2.executeUpdate("INSERT INTO pairs (proj1_idx,proj2_idx) " + " VALUES('" + proj1Idx + "','" + proj2Idx + "')");
			
			pairIdx = dbc2.getIdx("SELECT idx FROM pairs " + "WHERE proj1_idx=" + proj1Idx + " AND proj2_idx=" + proj2Idx);
			
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
		if (!Constants.checkCreateDataDir()) return; 
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
	public void saveLoad(String params) {
		try { 
			dbc2.executeUpdate("update pairs set aligned=1, aligndate=NOW(), "
					+ "syver='" + Globals.VERSION + "', params='" + params + "'" 
					+ " where (proj1_idx=" + proj1Idx + " and proj2_idx=" + proj2Idx 
					+ ") or   (proj1_idx=" + proj2Idx + " and proj2_idx=" + proj1Idx + ")");
		}
		catch (Exception e) {ErrorReport.print(e, "Update version"); return;}
	}
	
	public void saveUpdate() {
	try { 
		dbc2.executeUpdate("update pairs set aligndate=NOW(), syver='" + Globals.VERSION + "'"
				+ " where (proj1_idx=" + proj1Idx + " and proj2_idx=" + proj2Idx 
				+ ") or   (proj1_idx=" + proj2Idx + " and proj2_idx=" + proj1Idx + ")"); 
	}
	catch (Exception e) {ErrorReport.print(e, "Update version"); return;}
	}
	/**** called by DoAlignSynPair.run() for MUMmer and UseBlast for blast ***/
	public void saveParamsToDB(String alignParams) {// params are ones not in pair_props
		try {
			dbc2.executeUpdate("update projects set hasannot=1,loaddate=NOW() where idx=" + proj1Idx); 
			dbc2.executeUpdate("update projects set hasannot=1,loaddate=NOW() where idx=" + proj2Idx);
			
			// Append to current for MUMmer + Multiple Blast load
			String addParams = alignParams;
			String curParams = dbc2.executeString("select params from pairs where idx=" + pairIdx);
			if (curParams!=null && !curParams.isBlank()) {
				if (curParams.endsWith("\n")) addParams = curParams + alignParams;
				else                          addParams = curParams + "\n" + alignParams;
			}
	
			dbc2.executeUpdate("update pairs set aligned=1, aligndate=NOW(), "
					+ "syver='" + Globals.VERSION + "', params='" + addParams + "'" 
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
		
		// set isAlignNT; CAS589 add for query
		String params = dbc2.executeString("select params from pairs where idx=" + pairIdx);
		if (params!=null && !params.equals("")) {
			isOnlyBlast = !params.contains(mummer) && !params.contains(promer) && !params.contains(nucmer) ; // CAS590 for query
			if (params.contains(promer))      {isAlignNT=false; return;}
			else if (params.contains(nucmer)) {isAlignNT=true; return;}
		}
		// will only search all on self
		int cnt = dbc2.executeCount("select idx from pseudo_hits where cvgpct>0 and pair_idx=" + pairIdx + " limit 1");
		isAlignNT = (cnt<=0);
	}
	catch (Exception e) {ErrorReport.print(e, "load from db"); }
	}
	
	private void makeParams() {
		for (int i=0; i<paramKey.length; i++) {
			defMap.put(paramKey[i],  paramDef[i]);	// never changes
			dbMap.put(paramKey[i],   paramDef[i]);	// can change in loadFromDB
			fileMap.put(paramKey[i], paramDef[i]);  // can change in loadFromFIle
		}
	}
	public String toString() {return mProj1.strDisplayName + "-" + mProj2.strDisplayName + " pair";}
}
