package symap.manager;

import java.awt.Color;
import java.io.File;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.sql.ResultSet;

import util.ErrorReport;
import util.Utilities;
import util.FileDir;
import database.DBconn2;
import props.PropertiesReader;
import symap.Globals;
import backend.AnchorMain;
import backend.Constants;
import backend.Utils;

/****************************************************
 * This is used by most backend and display stuff. Has project parameters, and loads basic data
 *   All load/align classes get info from here.
 *   However, project stuff is still all over the place for displays
 *        
 *   Keeps track of changes to file versus what is in database for previous load/align
 *   The load/align parameters are only loaded to DB when the corresponding task is executed
 *   ProjParams show file, View shows DB
 *        
 * 	 All Mproject obj are recreated on every refresh
 * 	 if !viewSymap, the file values have precedence over DB for projVal
 */

public class Mproject implements Comparable <Mproject> {
	public String strDBName; 	// This is SQL project.name, and seq/dirName
	public String strDisplayName;
	
	private int projIdx;		// unique database index
	private String strDate="";
	private DBconn2 dbc2;
	
	private int numExon = 0, numGene = 0, numGap = 0, numGroups=0, numSynteny=0, numDraft=0; // numDraft to see !strict by default
	private long length=0; 		// genome length - must be long
	
	private TreeMap <Integer, String> grpIdx2FullName = new TreeMap <Integer, String> (); // full name, e.g. chr01
	private TreeMap <Integer, String> grpIdx2Name = new TreeMap <Integer, String> ();	  // name, e.g. 01
	private TreeMap<String,Integer>   grpName2Idx = new TreeMap <String, Integer> ();     // full name and name
	private Pattern namePat;
	private boolean bHasSelf=false; 
	
	private HashMap<String, Params> pLabelMap = new HashMap<String, Params> ();
	private HashMap<String, Params> pKeysMap = new HashMap<String, Params> ();
	
	private Color color;
	private short nStatus = STATUS_IN_DB; 
	public static final short STATUS_ON_DISK = 0x0001; 
	public static final short STATUS_IN_DB   = 0x0002; 
	
	private boolean bStart=true;
	
	// ManagerFrame, ChrExpInit, DotPlot.Project; one is created for each project shown on Manager left panel
	public Mproject(DBconn2 dbc, int nIdx, String strName, String annotdate) { 
		this.projIdx = nIdx;
		this.strDBName = strName;
		this.strDate = annotdate;	
		this.dbc2 = dbc;
		
		makeParams();
				
		if (nIdx == -1) {
			nStatus = STATUS_ON_DISK;
			finishParams();
		}
		else {
			nStatus = STATUS_IN_DB;
			if (annotdate!="")  {
				strDate = Utilities.reformatAnnotDate(annotdate);
				if (strDate.contentEquals("???")) 
					System.out.println("Warning: " + strName + " interrupted on load - reload");
			}
		}
		bStart = false;
	}
	public Mproject() { // for display packages querying proj_props 
		makeParams();
	}
	public Mproject copyForQuery() { // for isSelf
	try {
		Mproject p = new Mproject(dbc2, projIdx, strDBName, "");
		p.loadDataFromDB();
		p.loadParamsFromDB();
		p.finishParams();
		return p;
	}
	catch (Exception e) {ErrorReport.print(e, "copyForQuery"); return null;}
	}
	public int compareTo(Mproject b) {
		return strDisplayName.compareTo(b.strDisplayName);
	}
	public boolean equals(Object o) {
		if (o instanceof Mproject) {
			Mproject p = (Mproject)o;
			return (strDBName != null && strDBName.equals(p.getDBName()));
		}
		return false;
	}
	
	public String getAnnoStr() {
		String msg= ""; 
		if (numGene>0) msg += String.format("Genes: %,d  ", numGene);
		if (numExon>0) msg += String.format("Exons: %,d  ", numExon);
		if (numGap>0)  msg += String.format("Gaps: %,d", numGap);
		
		return msg;
	}
	public String getCntSyntenyStr() {
		if (numSynteny==0) return "";
		else return "[" + numSynteny + "]";
	}
	public boolean hasSynteny() {return numSynteny>0;}
	public boolean hasGenes() 	{return numGene>0;}
	public boolean hasSelf() 	{return bHasSelf;}
	
	public boolean hasCat()   	{return !Utilities.isEmpty(getDBVal(sCategory));} 
	public long getLength() 	{return length;}
	public Color getColor() 	{return color; }
	public void setColor(Color c) {color = c; }
	
	public int getIdx() 		{ return projIdx; }
	public int getID() 			{ return projIdx; }
	public String getDBName() 	{ return strDBName; }
	public String getDisplayName() 	{ return strDisplayName; }
	public int getGeneCnt()		{ return numGene;} // for Query Instructions
	public String getLoadDate() {return strDate;}
	public int getNumGroups() 	{ return numGroups; }
	public int getNumDraft() 	{ return numDraft; } // # <1000000
	
	public short getStatus() 	{return nStatus; }
	public boolean isLoaded() 	{return (nStatus==STATUS_IN_DB);}
	
	public String getdbDesc() 		{ return getDBVal(sDesc); }
	public String getdbGrpName() 	{ return getDBVal(sGrpType);}
	public String getdbCat() 		{ return getDBVal(sCategory); }
	public String getdbAbbrev() 	{ return getDBVal(sAbbrev); }
	
	public void setIsSelf(String display, String abbrev) { // for isSelf
		setProjVal(sDisplay, display); strDisplayName = display;
		setProjVal(sAbbrev, abbrev); 
	} 
	
	public int getdbMinKey() {return Utilities.getInt(getDBVal(sANkeyCnt));}
	
	public String getSequenceFile() { return getProjVal(lSeqFile); }
	public String getAnnoFile()     { return getProjVal(lAnnoFile); }
	
	public int getMinSize()  	
	{	String val =  getProjVal(lMinLen);
		if (val.contains(",")) val = val.replace(",",""); 
		return Utilities.getInt(val);
	}
	public String getKeywords()     { return getProjVal(lANkeyword); }
	public String getAnnoType()		{ return "";} 
	
	public String getGrpPrefix()    { return getProjVal(lGrpPrefix);}
	public int getGrpSize() { return grpIdx2Name.size();}
	public TreeMap <Integer, String> getGrpIdxMap() {return grpIdx2Name;} 
	
	public String getGrpFullNameFromIdx(int idx) {
		if (grpIdx2FullName.containsKey(idx)) return grpIdx2FullName.get(idx);
		return ("Unk" + idx);
	}
	public String getGrpNameFromIdx(int idx) {
		if (grpIdx2Name.containsKey(idx)) return grpIdx2Name.get(idx);
		return ("Unk" + idx);
	}
	public int getGrpIdxFromFullName(String name) {// can be full or short
		if (grpName2Idx.containsKey(name)) return grpName2Idx.get(name);
		return getGrpIdxRmPrefix(name);
	}
	
	public int getGrpIdxRmPrefix(String name) {	// grpName2Idx has both w/o prefix
		String s = name;
		
		Matcher m = namePat.matcher(name);
		if (m.matches()) s = m.group(2);
		if (grpName2Idx.containsKey(s)) return grpName2Idx.get(s);
		return -1;
	}
	public String getValidGroup() { // AnnotLoadMain; 
		String msg= grpName2Idx.size()+": ";
		int cnt=0; 
		for (String x : grpName2Idx.keySet())  {
			msg += x + ";";
			cnt++;
			if (cnt>50) {
				msg += "...";
				break;
			}
		}
		return msg;
	}
	///////////////////////
	public String notLoadedInfo() {
		String msg="Parameters: ";
		if (getProjVal(lSeqFile).contentEquals("")) 	msg += "Default file locations; ";
		else 											msg += "User file locations; ";
		
		msg += " Min len " + getProjVal(lMinLen) + "; ";
		
		String prefix = getProjVal(lGrpPrefix); 
		prefix = (Utilities.isEmpty(prefix)) ? "None" : prefix;
		msg += " Prefix " + prefix + "; ";
		
		String annotKeywords = getProjVal(lANkeyword);
		if (annotKeywords.equals("")) 	msg += " All anno keywords ";
		else 							msg += " Keywords " + annotKeywords;
		
		return msg;
	}
	// false: cnt all; true: only cnt if have /align;
	// used for all project related links (not pair)
	public boolean hasExistingAlignments(boolean bCheckAlign) { 
		try {
			File top = new File(Constants.getNameResultsDir());
			
			if (top == null || !top.exists()) return false; // may not have /data/seq_results directory
		
			// e.g. for brap; arab_to_brap  or brap_to_cabb
			String pStart = strDBName + Constants.projTo, pEnd = Constants.projTo + strDBName;
			int cnt=0;
			for (File f : top.listFiles()) {
				if (!f.isDirectory()) continue;
				
				String dir = f.getName();
				if	(!dir.startsWith(pStart) && !dir.endsWith(pEnd)) continue;
				
				if (bCheckAlign) {
					String alignDir = f.getAbsolutePath() + Constants.alignDir;
					File d = new File(alignDir);
					if (d.exists()) cnt++;  
				}
				else cnt++; 
			}				
			return (cnt>0);
		} 
		catch (Exception e) {ErrorReport.print(e, "Check existing alignment files"); return false;} 
	}
	
	/*********************************************************
	 * Special method to remove GrpPrefix
	 */
	public void updateGrpPrefix() {
		try {
			Vector <Integer> idxSet = new Vector <Integer> ();
			Vector <String> nameSet = new Vector <String> ();
			
			String prefix= getProjVal(lGrpPrefix);
			
			ResultSet rs = dbc2.executeQuery("select idx, name from xgroups where proj_idx=" + projIdx);
			while (rs.next()) {
				int ix = rs.getInt(1);
				String name = rs.getString(2);
				if (name.startsWith(prefix)) {
					name = name.substring(prefix.length());
					idxSet.add(ix);
					nameSet.add(name);
				}
			}
			rs.close();
			
			System.err.println("Change " + idxSet.size() + " group names to remove '" + prefix + "'" );
			
			for (int i=0; i<idxSet.size(); i++) {					
				dbc2.executeUpdate("update xgroups set name='" + nameSet.get(i) + 
												  "' WHERE idx=" + idxSet.get(i));
				saveProjParam(getKey(lGrpPrefix), prefix);
			}
		}
		catch (Exception e){ErrorReport.print(e, "Failed to update parameters");}
	}
	
	/*************************************************************************
	 * The View button on MF
	 */
	public String loadProjectForView() { 
	try {
		TreeMap <String, Integer> chrNameMap = new TreeMap <String, Integer> (); // name, idx
		TreeMap <Integer, Integer> chrLenMap = new TreeMap <Integer, Integer> ();// idx, chrName
		
	// get chrs and lengths
		ResultSet rs = dbc2.executeQuery("select xgroups.idx, xgroups.fullname, pseudos.length from pseudos " +
				" join xgroups on xgroups.idx=pseudos.grp_idx " +
				" where xgroups.proj_idx=" + projIdx);
		while (rs.next()) {
			int idx = rs.getInt(1);
			String name = rs.getString(2);
			int len = rs.getInt(3);
			chrNameMap.put(name, idx);
			chrLenMap.put(idx, len);
		}
		String desc = dbc2.executeString("select value from proj_props "
				+ "where name='description' and proj_idx=" + projIdx); 
		
		String info="Project " + strDisplayName + "\n";
		if (!Utilities.isEmpty(desc)) info += desc + "\n";
		info += "\n";
		
		String [] fields = {"Chr", "Length", "  ","#Genes", "AvgLen", "  ", "#Exons", "AvgLen", "  ", "Gaps"}; 
		int [] justify =   {1,    0,    0, 0, 0, 0, 0, 0, 0, 0};
		int nRow = chrNameMap.size()+1;
	    int nCol=  fields.length;
	    String [][] rows = new String[nRow][nCol];
	    int r=0, c=0;
	    int totGenes=0, totExons=0, totGaps=0; 
	    long totLen=0;
	    float totAvgGenes=0, totAvgExons=0;
	    
	    for (String name : chrNameMap.keySet()) {
	    	int idx = chrNameMap.get(name);
	    	rs = dbc2.executeQuery("select count(*) from pseudo_annot where type='gene' and grp_idx=" + idx);
	    	int geneCnt = (rs.next()) ? rs.getInt(1) : 0;
	    	
	    	rs = dbc2.executeQuery("select count(*) from pseudo_annot where type='exon' and grp_idx=" + idx);
	    	int exonCnt = (rs.next()) ? rs.getInt(1) : 0;
	    	
	    	rs = dbc2.executeQuery("select count(*) from pseudo_annot where type='gap' and grp_idx=" + idx);
	    	int gapCnt = (rs.next()) ? rs.getInt(1) : 0;
	    	
	    	rs = dbc2.executeQuery("select AVG(end-start+1) from pseudo_annot where type='gene' and grp_idx=" + idx);
	    	double avgglen = (rs.next()) ? rs.getDouble(1) : 0.0;
	    	
	    	rs = dbc2.executeQuery("select AVG(end-start+1) from pseudo_annot where type='exon' and grp_idx=" + idx);
	    	double avgelen = (rs.next()) ? rs.getDouble(1) : 0.0;
	    	
	    	rows[r][c++] = name;
	    	rows[r][c++] = String.format("%,d",chrLenMap.get(idx));
	    	rows[r][c++] = "  ";
	    	rows[r][c++] = String.format("%,d",geneCnt);
	    	rows[r][c++] = String.format("%,d",(int) avgglen);
	    	rows[r][c++] = "  ";
	    	rows[r][c++] = String.format("%,d",exonCnt);
	    	rows[r][c++] = String.format("%,d",(int) avgelen);
	    	rows[r][c++] = "  ";
	    	rows[r][c++] = String.format("%,d",gapCnt);
	    	r++; c=0;
	    	
	    	totLen += chrLenMap.get(idx);
	    	totGenes += geneCnt;
	    	totExons += exonCnt;
	    	totAvgGenes += avgglen;
	    	totAvgExons += avgelen;
	    	totGaps += gapCnt;
	    }
	    totAvgGenes /= r;
	    totAvgExons /= r;
	    rows[r][c++] = "Totals";
    	rows[r][c++] = String.format("%,d",totLen);
    	rows[r][c++] = "  ";
    	rows[r][c++] = String.format("%,d",totGenes);
    	rows[r][c++] = String.format("%,d",(int) totAvgGenes);
    	rows[r][c++] = "  ";
    	rows[r][c++] = String.format("%,d",totExons);
    	rows[r][c++] = String.format("%,d",(int) totAvgExons);
    	rows[r][c++] = "  ";
    	rows[r][c++] = String.format("%,d",totGaps);
    	r++; 
    	
    	if (totGaps==0) nCol -= 2;
	    info += Utilities.makeTable(nCol, nRow, fields, justify, rows);
	    
	    // these are saved in proj_props
	    String file="", fdate=null;
	    String sql = "SELECT value FROM proj_props WHERE proj_idx='" +  projIdx;
	    
	    rs = dbc2.executeQuery(sql + "' AND name='proj_seq_dir'");
		file = (rs.next()) ? rs.getString(1) : "";
		
		rs = dbc2.executeQuery(sql + "' AND name='proj_seq_date'");
		fdate = (rs.next()) ? rs.getString(1) : "";
		if (!file.trim().contentEquals("")) info += "\nSeq:  " + file + "\nDate: " + fdate + "\n";
		
		file="";
		rs = dbc2.executeQuery(sql + "' AND name='proj_anno_dir'");
		file = (rs.next()) ? rs.getString(1) : "";
		
		rs = dbc2.executeQuery(sql + "' AND name='proj_anno_date'");
		fdate = (rs.next()) ? rs.getString(1) : "";
		if (!file.trim().contentEquals("")) info += "\nAnno: " + file + "\nDate: " + fdate + "\n";
		rs.close();		
		
		Params paramObj = getParams(lMinLen);
		if (!paramObj.isDBvalDef() && !paramObj.dbVal.contentEquals("")) {
			String strN = paramObj.dbVal;
			if (!strN.contains(",")) { 
				try {
					int n = Integer.parseInt(strN);
					strN = String.format("%,d", n);
				} catch (Exception e) {}
			}
			info += "\n" + paramObj.label + ": " + strN;
		}
		
		return info;
	}
	catch (Exception e) {ErrorReport.print(e, "Load Project for view"); return "Error";}
	}
	
	/*******************************************************************
	 * On params write: update Mproject but leave DB in case they do not load/A&S
	 */
	public void saveParams(int type) {
		if (projIdx == -1) return; // not loaded yet
		try {
			if (type==xLoad) {
				dbc2.executeUpdate("update projects set hasannot=1," 
						+ " annotdate=NOW(), syver='" + Globals.VERSION + "' where idx=" + projIdx);
			}
			
			for (Params p : pKeysMap.values()) { 
				boolean b=false;
				if (p.isSum) b=true;
				else if (p.isLoad && type==xLoad)  b = true;
		
				if (b) {
					dbc2.executeUpdate("delete from proj_props where proj_idx="+ projIdx 
							+ " and name='" + p.key + "'");
					
					dbc2.executeUpdate("insert into proj_props (proj_idx,name,value) "
							+ " values(" + projIdx + ",'" + p.key + "','" + p.projVal + "')");	
					
					pKeysMap.get(p.key).dbVal = p.projVal;
				}
				if (type==xLoad) nStatus=STATUS_IN_DB;
			}
			finishParams();
		}
		catch (Exception e){ErrorReport.print(e, "Failed to update load parameters ");}
	}
	
	// the following are saved: "proj_seq_dir", "proj_seq_date", "proj_anno_dir", "proj_anno_date"
	public void saveProjParam(String name, String val) throws Exception{
	try {
		dbc2.executeUpdate("delete from proj_props where name='" + name + "' and proj_idx=" + projIdx);
		dbc2.executeUpdate("insert into proj_props (name, value, proj_idx) values('" + name + "','" + val + "','" + projIdx + "')");	
	} 
	catch (Exception e) {ErrorReport.print(e, "Save project param " + name + " " + val); }
	}
	
	/**********************************************************/
	public void createProject() throws Exception {
	try {
		projIdx = dbc2.getIdx("select idx from projects where name='" + strDBName + "'");
		
		if (projIdx == -1) {
			dbc2.executeUpdate("INSERT INTO projects (name,type,loaddate, syver) " + 
					"VALUES('" + strDBName + "','pseudo',NOW(),'" + Globals.VERSION + "')");
			projIdx = dbc2.getIdx("select idx from projects where name='" + strDBName + "'");
			
			System.out.println("Create project in database - " + strDBName);
		}
		else System.out.println("SyMAP warning: project " + strDBName + " exists");
	} 
	catch (Exception e) {ErrorReport.print(e, "Createproject from DB"); }
	}
	
	public void removeAnnoFromDB() {
	try {
		dbc2.executeUpdate("DELETE FROM pseudo_annot USING pseudo_annot, xgroups " +
				" WHERE xgroups.proj_idx='" + projIdx + 
				"' AND pseudo_annot.grp_idx=xgroups.idx");

		dbc2.executeUpdate("delete from pairs " +
					" where proj1_idx=" + projIdx + " or proj2_idx=" + projIdx);
		
		dbc2.resetAllIdx();
	}
	catch (Exception e) {ErrorReport.print(e, "Remove annotations"); }
	}
	public void removeProjectFromDB() {
	try {
		Globals.rprt("Removing " + strDisplayName + " from database..."); 
		// Setup for update numhits for single Query
		Vector <Integer> proj2Idx = new Vector <Integer> ();
		ResultSet rs = dbc2.executeQuery("select proj1_idx, proj2_idx from pairs where proj1_idx=" + projIdx + " or proj2_idx=" + projIdx);
	    while (rs.next()) {
	    	int idx1 = rs.getInt(1), idx2 = rs.getInt(2);
	    	if (idx1==projIdx) 	proj2Idx.add(idx2); 
	    	else 				proj2Idx.add(idx1);
	    }
	    
	    /* Main Delete */
        dbc2.executeUpdate("DELETE from projects WHERE name='"+ strDBName+"'");
        dbc2.resetAllIdx(); 
        
        nStatus = STATUS_ON_DISK; 
        projIdx = -1; 
        
        // update numhits
        Globals.rprt("Update " + strDisplayName + " numHits...");
        AnchorMain ancObj = new AnchorMain(dbc2, null, null);
        for (int idx : proj2Idx) {
        	Vector <Integer> gidxList = new Vector <Integer> ();
        	rs = dbc2.executeQuery("select idx from xgroups where proj_idx=" + idx);
        	while (rs.next()) gidxList.add(rs.getInt(1));
        	
        	for (int gidx : gidxList) ancObj.saveAnnotHitCnt(gidx,""); // get ResultClose error if not put in vector
        }
        Globals.rclear();
	} 
	catch (Exception e) {ErrorReport.print(e, "Remove project from DB"); }
	}
	public void removeProjectAnnoFromDB() {
	try {
        dbc2.executeUpdate("DELETE pseudo_annot.* from pseudo_annot, xgroups where pseudo_annot.grp_idx=xgroups.idx and xgroups.proj_idx=" + projIdx);
        dbc2.resetIdx("idx", "pseudo_annot"); 
	} 
	catch (Exception e) {ErrorReport.print(e, "Remove annotation from DB"); }
	}	
	/*********************************************************
	 * ManagerFrame: every time it is re-initilized on refresh; the following are called in this order
	 * 		loadParamsFromDB
	 * 		loadDataFromDB
	 *  	loadParamsFromDisk (if !viewSymap)
	 */
	public void loadParamsFromDB() {
		try {
	        ResultSet rs = dbc2.executeQuery("SELECT name, value FROM proj_props WHERE proj_idx=" + projIdx);
	        while (rs.next()) {
	        	String key = rs.getString(1);
	        	String val = rs.getString(2);
	        	if (pKeysMap.containsKey(key)) { // there are entries, e.g. proj_anno_date, that are not param
	        		pKeysMap.get(key).dbVal   = val;
	        		pKeysMap.get(key).projVal = val;
	        	}
	        }
			rs = dbc2.executeQuery("select idx from pairs where proj1_idx=" + projIdx + " and proj2_idx=" + projIdx); 
			bHasSelf = (rs.next()) ? true : false;
	        rs.close();
	        
	        loadParamsFromDisk(); 
		}
		catch (Exception e) {ErrorReport.print(e,"Load projects properties"); }
	}
	protected void loadParamsFromDisk() {
		if (!FileDir.dirExists(Constants.dataDir)) {
			finishParams(); 
			return;
		}
		String dir = Constants.seqDataDir + strDBName;
		loadParamsFromDisk(dir);
	}
	protected void loadParamsFromDisk(File dir) {
		loadParamsFromDisk(dir.getAbsolutePath()); 
	} 
	
	private void loadParamsFromDisk(String dir){
		File pfile = Utils.getParamsFile(dir,Constants.paramsFile);  
		if (pfile==null) { 				// If user created, no params file
			finishParams();
			if (!ManagerFrame.inReadOnlyMode) writeNewParamsFile(); 
			return;
		}
	
		String msg = "";
		PropertiesReader props = new PropertiesReader( pfile);
		for (int i=0; i<paramKey.length; i++) {
			String name =  paramKey[i];
			if (props.getProperty(name) != null) {
				Params p = pKeysMap.get(paramKey[i]);
				String fprop = props.getProperty(paramKey[i]);
				
				if (p.projVal.length()>0 && !p.projVal.equals(fprop)) { 
					msg += String.format("%-20s  DB: %-20s  File: %-20s\n", p.label, p.projVal, fprop);
				}
				p.projVal = fprop; // overwrite from DB
			}
		}
		// uses params from file; display params can be changed when another database is shown
		if (msg.length()>0 && nStatus == STATUS_IN_DB && bStart) {// bStart only do when this object is created
			if (Globals.TRACE) Globals.prt(strDBName + " updating in DataBase:\n" + msg);
			saveParams(xUpdate);
		}
		finishParams();
	}

	private void finishParams() { 
		strDisplayName = getDBVal(sDisplay).trim(); 
		if (Utilities.isEmpty(strDisplayName)) {
			strDisplayName = getProjVal(sDisplay).trim(); 
			
			if (Utilities.isEmpty(strDisplayName)) {
				strDisplayName = strDBName;
				setProjVal(sDisplay, strDisplayName);	
			}
		}
		String abbrev = getDBVal(sAbbrev).trim();
		if (Utilities.isEmpty(abbrev)) {
			abbrev = getProjVal(sAbbrev).trim();
			
			if (Utilities.isEmpty(abbrev)) {
				int len = strDisplayName.length();
				if (len>4) abbrev = strDisplayName.substring(len-4);
				else {
					abbrev=strDisplayName;
					for (int i=len; i<4; i++) abbrev += "_";
				}
				setProjVal(sAbbrev, abbrev);
			}
		}
		String regx = "(" + getProjVal(lGrpPrefix) + ")?(\\w+).*"; 
		namePat = Pattern.compile(regx,Pattern.CASE_INSENSITIVE);
	}
	
	public void loadDataFromDB() throws Exception {
		ResultSet rs = dbc2.executeQuery("select idx, fullname, name from xgroups where proj_idx=" + projIdx);
		while (rs.next()) {
			int idx = rs.getInt(1);
			String full = rs.getString(2);
			String name = rs.getString(3);
			
			grpName2Idx.put(full, idx);
			grpName2Idx.put(name, idx);
			grpIdx2Name.put(idx, name);
			grpIdx2FullName.put(idx, full);
		} 
		rs.close();
		
		numGroups = dbc2.executeCount("SELECT count(*) FROM xgroups WHERE proj_idx=" + projIdx);
       
        numExon = dbc2.executeCount("select count(*) from pseudo_annot as pa " +
        		" join xgroups as g on g.idx=pa.grp_idx " +
        		" where g.proj_idx=" + projIdx + " and type='exon'");
        
        numGene = dbc2.executeCount("select count(*) from pseudo_annot as pa " +
        		" join xgroups as g on g.idx=pa.grp_idx " +
        		" where g.proj_idx=" + projIdx + " and type='gene'");
        
        numGap = dbc2.executeCount("select count(*) from pseudo_annot as pa " +
        		" join xgroups as g on g.idx=pa.grp_idx " +
        		" where g.proj_idx=" + projIdx + " and type='gap'");
       
        length = dbc2.executeLong("select sum(length) from pseudos " +
				"join xgroups on xgroups.idx=pseudos.grp_idx  " +
				"where xgroups.proj_idx=" + projIdx);	
		
        numDraft = dbc2.executeCount("select count(length<1000000) from pseudos " +
				"join xgroups on xgroups.idx=pseudos.grp_idx  " +
				"where xgroups.proj_idx=" + projIdx);	
        
		numSynteny = dbc2.executeCount("select count(*) from pairs where proj1_idx=" + projIdx +
				" or proj2_idx=" + projIdx); 
	}
	private void writeNewParamsFile() { 
		if (Utilities.isEmpty(strDBName)) return; // OrderAgainst writes the file, but not with Mproject
		String dir = Constants.seqDataDir + strDBName;
		FileDir.checkCreateDir(dir, true);
		
		File pfile = new File(dir,Constants.paramsFile);
		if (!pfile.exists())
			System.out.println("Create parameter file " + dir + Constants.paramsFile);
		
		try {
			PrintWriter out = new PrintWriter(pfile);
			out.println("#");
			out.println("#  " + strDisplayName + " project parameter file");
			out.println("#  Note: changes MUST be made in SyMAP parameter window");
			out.println(getKey(sDisplay) + " = " + strDisplayName);
			out.println(getKey(sAbbrev) + " = " + getProjVal(sAbbrev));
			out.println(getKey(sDesc) + " =  New project");
			out.close();
		}
		catch (Exception e) {ErrorReport.print(e, "Wrie new params file");}
	}
	public String toString() { return strDBName + ":" + projIdx; }
	
	public void prtInfo() {// public for testing query
		Globals.prt(String.format("   %-20s %s index %d", "DBname", strDBName, projIdx));
		String key = paramLabel[sCategory];
		Globals.prt(String.format("   %-20s %s", key, pLabelMap.get(key).projVal));
		key = paramLabel[sDisplay];
		Globals.prt(String.format("   %-20s %s", key, pLabelMap.get(key).projVal));
		key = paramLabel[sAbbrev];	
		Globals.prt(String.format("   %-20s %s", key, pLabelMap.get(key).projVal));
	}
	/***********************************************************
	 * ProjParams interface
	 */
	public String getLab(int iLabel)  {return paramLabel[iLabel];}
	public String getKey(int iLabel)  {return paramKey[iLabel];} // used by non mProj packages to get keyword
	public String getDef(int iLabel)  {return paramDef[iLabel];}
	public String getDesc(int iLabel) {return paramDesc[iLabel];}
	
	public HashMap <String, String> getParams() {
		HashMap <String, String> pMap = new HashMap <String, String> ();
		for (String key : pLabelMap.keySet()) {
			pMap.put(key, pLabelMap.get(key).projVal);
		}
		return pMap;
	}
	public String getParam(String label) {
		if (!pLabelMap.containsKey(label)) return null;
		return pLabelMap.get(label).key;
	}
	/*******************************************************************/
	private String  getProjVal(int idx) {
		String key = paramKey[idx];
		String val = pKeysMap.get(key).projVal;
		if (val==null) val="";
		return val;
	}
	
	private void setProjVal(int idx, String value) {
		String key = paramKey[idx];
		pKeysMap.get(key).projVal = value;
		pKeysMap.get(key).dbVal = value; // for Query self-synteny
	}
	
	private String  getDBVal(int idx) {
		String key = paramKey[idx];
		String val = (isLoaded()) ? pKeysMap.get(key).dbVal : pKeysMap.get(key).projVal;
		return val;
	}
	
	private Params getParams(int idx) {
		String key = paramKey[idx];
		return pKeysMap.get(key);
	}
	private void makeParams() {
		for (int i=0; i<paramLabel.length; i++) {
			Params p = new Params(i, paramLabel[i], paramKey[i], paramDef[i]);
			pLabelMap.put(paramLabel[i], p);
			pKeysMap.put(paramKey[i], p);
		}
	}
	
	private final String [] paramKey = { // key for file and db saves
			"category", "display_name", "abbrev_name", "grp_type", 
			"description", "annot_kw_mincount", "grp_prefix", "min_size", 
			"annot_keywords", "sequence_files", "anno_files"
	};
	private final String [] paramDef = {
			"Uncategorized", "", "", "Chromosome", 
			"",  "50", "", "100000", 
			"", "", ""
	};
	private String [] paramLabel = {
			"Category", "Display name", "Abbreviation", "Group type", 
			"Description",  "Anno key count","Group prefix", "Minimum length", 
			 "Anno keywords", "Sequence files", "Anno files"
	};
	private String [] paramDesc = { 
			"Select a Category from the drop down or enter a new one in the text box.", 
			"This name will be used for the project everywhere.", 
			"This must be exactly 4 characters to be used in Queries.", 
			"Group type is generally 'Chromosome'; this is used as a label on the Selected panel.", 
			"The description is information to display with the project.", 
			"If there are at least this many occurances of a keyword, it will be a column in Queries",
			"Only sequences with this group prefix will be loaded; if blank, all will be loaded", 
			"Only load sequences that have at least this many bases will be loaded", 
			"Only load this set of keywords; if blank, all will be loaded", 
			"The directory or file name of the FASTA file(s); if blank, the default location will be used", 
			"The directory or file name of the GFF file(s); if blank, the default location will be used"
	};
	// Index into the above three arrays
	protected final int sCategory = 0;
	public final int sDisplay =   	1;
	protected final int sAbbrev =   2;
	public final int sGrpType =   	3;
	protected final int sDesc =     4; 
	public final int sANkeyCnt =  	5;
	
	public final int lGrpPrefix = 	6;
	protected final int lMinLen =   7;
	public final int lANkeyword = 	8;
	protected final int lSeqFile =  9;
	protected final int lAnnoFile = 10;
	
	public final int xDisplay = 1;
	public final int xLoad = 2;
	private final int xUpdate = 3; // only happens if the params where changes when a different database is shown
	
	private class Params {
		private Params(int index, String label, String key, String defVal) {
			this.label = label;
			this.key = key;   			// write to file
			this.defVal = 	defVal;  	// default
			projVal =		defVal;  	// assigned in proj params
			
			if (index<7) isSum=true;
			else isLoad=true;
		}
		private boolean isDBvalDef() {
			return dbVal.equals(defVal);
		}
		String key="", label="";
		String defVal="", projVal="", dbVal="";
		boolean isSum=false, isLoad=false;
	}
}
