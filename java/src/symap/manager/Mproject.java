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
import database.DBconn2;
import props.PropertiesReader;
import symap.Globals;
import backend.Constants;

/****************************************************
 * This is used by most backend and display stuff. Has project parameters, and loads basic data
 * 
 * CAS522 remove FPC
 * CAS534 rewrite of project parameters
 * 		  renamed from Project to make unique and pair with Mpair;
 *        All load/align classes get info from here.
 *        However, project stuff is still all over the place for displays
 *        
 *        Keeps track of changes to file versus what is in database for previous load/align
 *        The load/align parameters are only loaded to DB when the corresponding task is executed
 *        ProjParams show file, View shows DB
 *        
 * 		  All Mproject obj are recreated on every refresh
 * 		  if !viewSymap, the file values have precedence over DB for projVal
 * CAS541 change dbUser to DBconn
 */

public class Mproject implements Comparable <Mproject> {//CAS513 for TreeSet sort
	private boolean TRACE = false;
	public String strDBName; 	// This is SQL project.name, and seq/dirName
	public String strDisplayName;
	
	private int projIdx;		// unique database index
	private String strDate="";
	private DBconn2 dbc2;
	
	private int numAnnot = 0, numGene = 0, numGap = 0, numGroups=0, numSynteny=0;;
	private long length=0; // CAS551 made int, CAS552 crashes on large genomes in MySQL
	
	private TreeMap <Integer, String> grpIdx2Name = new TreeMap <Integer, String> ();
	private TreeMap<String,Integer> grpName2Idx = new TreeMap <String, Integer> ();
	private Pattern namePat;
	private boolean bHasSelf=false; // CAS552 do not have Self-Align on circle if no self
	
	private Color color;
	private short nStatus = STATUS_IN_DB; 
	public static final short STATUS_ON_DISK = 0x0001; 
	public static final short STATUS_IN_DB   = 0x0002; 
	
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
			if (annotdate!="")  {// CAS513 add loaddate Manager left side Projects
				strDate = Utilities.reformatAnnotDate(annotdate);
				if (strDate.contentEquals("???")) 
					System.out.println("Warning: " + strName + " interrupted on load - reload");
			}
		}
	}
	public Mproject() { // for display packages querying proj_props 
		makeParams();
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
	public String toString() { return strDBName; }
	
	public String getAnnoStr() {
		String msg= String.format("Annotations: %,d ", numAnnot);
		if (numGene>0&&numGap>0) 	msg += String.format("(Genes: %,d, Gap: %,d)", numGene, numGap);
		else if (numGene>0) 		msg += String.format("(Genes: %,d)", numGene);
		else if (numGap>0)  		msg += String.format("(Gaps: %,d)", numGap);
		
		return msg;
	}
	public String getCntSyntenyStr() {
		if (numSynteny==0) return "";
		else return "[" + numSynteny + "]";
	}
	public boolean hasSynteny() {return numSynteny>0;}
	public boolean hasGenes() {return numGene>0;}
	public boolean hasSelf() { return bHasSelf;}
	
	public boolean hasCat()   	{return !Utilities.isEmpty(getDBVal(sCategory));} // CAS535 not finished
	public long getLength() 	{return length;}
	public Color getColor() 	{return color; }
	public void setColor(Color c) {color = c; }
	
	public int getIdx() 		{ return projIdx; }
	public int getID() 			{ return projIdx; }
	public String getDBName() 	{ return strDBName; }
	public String getDisplayName() 	{ return strDisplayName; }// getProjVal(sDisplay); }
	
	public String getLoadDate() {return strDate;}
	public int getNumGroups() 	{ return numGroups; }
	
	public short getStatus() 	{return nStatus; }
	public boolean isLoaded() 	{return (nStatus==STATUS_IN_DB);}
	
	public String getdbDesc() 		{ return getDBVal(sDesc); }
	public String getdbGrpName() 	{ return getDBVal(sGrpType);}
	public String getdbCat() 		{ return getDBVal(sCategory); }
	public String getdbAbbrev() 	{ return getDBVal(sAbbrev); }
	public int getdbDPsize() {return Utilities.getInt(getDBVal(sDPsize));}
	public int getdbMinKey() {return Utilities.getInt(getDBVal(sANkeyCnt));}
	
	public String getSequenceFile() { return getProjVal(lSeqFile); }
	public String getAnnoFile()     { return getProjVal(lAnnoFile); }
	
	public int getMinSize()  		{ return Utilities.getInt(getProjVal(lMinLen));}
	public String getKeywords()     { return getProjVal(lANkeyword); }
	public String getAnnoType()		{ return "";} 
	
	public boolean isMasked()  		{ return getProjVal(aMaskNonGenes).contentEquals("1");} // CAS504
	public String  getOrderAgainst(){ return getProjVal(aOrderAgainst); }
	public boolean hasOrderAgainst(){ return !getOrderAgainst().contentEquals("");}
	
	// CAS546 Group
	public String getGrpPrefix()    { return getProjVal(lGrpPrefix);}
	public int getGrpSize() { return grpIdx2Name.size();}
	public TreeMap <Integer, String> getGrpIdxMap() {return grpIdx2Name;} // CAS546 add
	
	public int getGrpIdxFromName(String name) {// CAS546 add
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
	public String getValidGroup() {
		String msg="";
		for (String x : grpName2Idx.keySet())  msg += x + ";";
		return msg;
	}
	///////////////////////
	public String notLoadedInfo() {
		String msg="Parameters: ";
		if (getProjVal(lSeqFile).contentEquals("")) 	msg += "Default file locations; ";
		else 											msg += "User file locations; ";
		
		msg += " Min len " + getProjVal(lMinLen) + "; ";
		
		String prefix = getProjVal(lGrpPrefix); // CAS543 add to summary
		prefix = (Utilities.isEmpty(prefix)) ? "None" : prefix;
		msg += " Prefix " + prefix + "; ";
		
		String annotKeywords = getProjVal(lANkeyword);
		if (annotKeywords.equals("")) 	msg += " All anno keywords ";
		else 							msg += " Keywords " + annotKeywords;
		
		return msg;
	}
	public boolean hasExistingAlignments() {
		try {
			File top = new File(Constants.getNameResultsDir());
			
			if (top == null || !top.exists()) return false; // CAS511 may not have /data/seq_results directory
			Vector<File> alignDirs = new Vector<File>();
			
			for (File f : top.listFiles()) {
				if (f.isDirectory()) {
					if	(f.getName().startsWith(strDBName + Constants.projTo) ||
							f.getName().endsWith(Constants.projTo+strDBName)) {
						alignDirs.add(f);
					}
				}
				if (alignDirs.size() > 0) return true;
			}
		} catch (Exception e) {ErrorReport.print(e, "Check existing alignment files");} // CAS511 add try-catch
		return false;
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
	/*******************************************************
	 * For Summary and AlignProg
	 */
	public String getOrderParam() { // CAS540
		if (hasOrderAgainst()) return "\n  " + strDisplayName + ": Order against " + getOrderAgainst() ;
		return "";
	}
	public String getMaskedParam() {
		if (isMasked()) return "\n  " + strDisplayName + ": Masked genes  ";
		return "";
	}
	/*************************************************************************
	 * The View button on MF
	 * CAS540 remove Average (in Summary now); change %Exon to #Exon
	 */
	public String loadProjectForView() { // CAS521 add to can see what was loaded
	try {
		TreeMap <String, Integer> chrNameMap = new TreeMap <String, Integer> ();
		TreeMap <Integer, Integer> chrLenMap = new TreeMap <Integer, Integer> ();
		
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
		
		String info="Project " + strDisplayName + "\n\n";
		
		String [] fields = {"Chr", "Length" ,"#Genes", "#Exons"}; // CAS534 add Exon columns; 
		int [] justify =   {1,    0,    0, 0};
		int nRow = chrNameMap.size();
	    int nCol=  fields.length;
	    String [][] rows = new String[nRow][nCol];
	    int r=0, c=0;
	    
	    for (String name : chrNameMap.keySet()) {
	    	int idx = chrNameMap.get(name);
	    	rs = dbc2.executeQuery("select count(*) from pseudo_annot where type='gene' and grp_idx=" + idx);
	    	int geneCnt = (rs.next()) ? rs.getInt(1) : 0;
	    	
	    	rs = dbc2.executeQuery("select count(*) from pseudo_annot where type='exon' and grp_idx=" + idx);
	    	int exonCnt = (rs.next()) ? rs.getInt(1) : 0;
	    	
	    	rows[r][c++] = name;
	    	rows[r][c++] = String.format("%,d",chrLenMap.get(idx));
	    	rows[r][c++] = String.format("%,d",geneCnt);
	    	rows[r][c++] = String.format("%,d",exonCnt);
	    	r++; c=0;
	    }
	    info += Utilities.makeTable(nCol, nRow, fields, justify, rows);
	    
	    // get file dates CAS532 add the following; these are saved in proj_props
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
				
		// CAS533 add these 
		Params paramObj;
		
		paramObj = getParams(lMinLen);
		if (!paramObj.isDBvalDef() && !paramObj.dbVal.contentEquals("")) // pre-v534 not in db if default
			info += "\n" + paramObj.getStr();
		
		paramObj = getParams(aMaskNonGenes);
		if (!paramObj.isDBvalDef()) info += "\n" + paramObj.getStr();
		
		paramObj = getParams(aOrderAgainst);
		if (!paramObj.isDBvalDef()) info += "\n" + paramObj.getStr();
		
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
			if (type==xLoad) {//CAS535 moved date updates here
				dbc2.executeUpdate("update projects set hasannot=1," // CAS520 add version
						+ " annotdate=NOW(), syver='" + Globals.VERSION + "' where idx=" + projIdx);
			}
			else if (type==xAlign) {//Load date is for loading anchors/synteny
				dbc2.executeUpdate("update projects set hasannot=1,loaddate=NOW() where idx=" + projIdx);
			}
			for (Params p : pKeysMap.values()) { 
				boolean b=false;
				if (p.isSum) b=true;
				else if (p.isLoad && type==xLoad)  b = true;
				else if (p.isAS   && type==xAlign) b = true;
		
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
			dbc2.executeUpdate("INSERT INTO projects (name,type,loaddate, syver) " + // CAS520 add version
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
        dbc2.executeUpdate("DELETE from projects WHERE name='"+ strDBName+"'");
        dbc2.resetAllIdx(); // CAS535 changed from individual
        nStatus = STATUS_ON_DISK; 
        projIdx = -1; 
	} 
	catch (Exception e) {ErrorReport.print(e, "Remove project from DB"); }
	}
	public void removeProjectAnnoFromDB() {
	try {
        dbc2.executeUpdate("DELETE pseudo_annot.* from pseudo_annot, xgroups where pseudo_annot.grp_idx=xgroups.idx and xgroups.proj_idx=" + projIdx);
        dbc2.resetIdx("idx", "pseudo_annot"); // CAS512
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
	        while ( rs.next() ) {
	        	String key = rs.getString(1);
	        	String val = rs.getString(2);
	        	if (pKeysMap.containsKey(key)) { // there are entries, e.g. proj_anno_date, that are not param
	        		pKeysMap.get(key).dbVal = val;
	        		pKeysMap.get(key).projVal = val;
	        		pKeysMap.get(key).prtDiff();
	        	}
	        }
	        // CAS552 add
			rs = dbc2.executeQuery("select idx from pairs where proj1_idx=" + projIdx + " and proj2_idx=" + projIdx); 
			bHasSelf = (rs.next()) ? true : false;
			
	        rs.close();
	        if (!Utilities.isEmpty(getDBVal(sCategory))) loadParamsFromDisk(); // CAS535 not finished
	        else finishParams();
		}
		catch (Exception e) {ErrorReport.print(e,"Load projects properties"); }
	}
	public void loadParamsFromDisk() {
		if (!Utilities.dirExists(Constants.dataDir)) {
			finishParams(); // CAS535 but if there is no data directory
			return;
		}
		
		String dir = Constants.seqDataDir + strDBName;
		loadParamsFromDisk(new File(dir));
	}
	public void loadParamsFromDisk(File dir){
		File pfile = new File(dir,Constants.paramsFile); 
		if (!pfile.isFile()) { // If user created, no params file
			finishParams();
			writeNewParamsFile();
			return;
		}
	
		PropertiesReader props = new PropertiesReader( pfile);
		for (int i=0; i<paramKey.length; i++) {
			String name =  paramKey[i];
			if (props.getProperty(name) != null) {
				Params p = pKeysMap.get(paramKey[i]);
				p.projVal = props.getProperty(paramKey[i]); // overwrite from DB
			}
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
					for (int i=len; i<4; i++) abbrev += "x";
				}
				setProjVal(sAbbrev, abbrev);
			}
		}
		String regx = "(" + getProjVal(lGrpPrefix) + ")?(\\w+).*"; 
		namePat = Pattern.compile(regx,Pattern.CASE_INSENSITIVE);
	}
	
	public void loadDataFromDB() throws Exception {// CAS534 moved from ManagerFrame
		ResultSet rs = dbc2.executeQuery("select idx, fullname, name from xgroups where proj_idx=" + projIdx);
		while (rs.next()) {// CAS546 get name too
			grpIdx2Name.put(rs.getInt(1), rs.getString(2));
			grpName2Idx.put(rs.getString(2), rs.getInt(1));
			grpName2Idx.put(rs.getString(3), rs.getInt(1));
		} 
		rs.close();
		
		numGroups = dbc2.executeCount("SELECT count(*) FROM xgroups WHERE proj_idx=" + projIdx);
       
        numAnnot = dbc2.executeCount("select count(*) from pseudo_annot as pa " +
        		" join xgroups as g on g.idx=pa.grp_idx " +
        		" where g.proj_idx=" + projIdx);
       
        numGene = dbc2.executeCount("select count(*) from pseudo_annot as pa " +
        		" join xgroups as g on g.idx=pa.grp_idx " +
        		" where g.proj_idx=" + projIdx + " and type='gene'");
        
        numGap = dbc2.executeCount("select count(*) from pseudo_annot as pa " +
        		" join xgroups as g on g.idx=pa.grp_idx " +
        		" where g.proj_idx=" + projIdx + " and type='gap'");
      
        length = dbc2.executeLong("select sum(length) from pseudos " +
				"join xgroups on xgroups.idx=pseudos.grp_idx  " +
				"where xgroups.proj_idx=" + projIdx);	// CAS500 for Summary and for orderProjects
		
		numSynteny = dbc2.executeCount("select count(*) from pairs where proj1_idx=" + projIdx +
				" or proj2_idx=" + projIdx); // CAS513 for Project on left, mark those with synteny
	}
	private void writeNewParamsFile() { // CAS534 add
		if (Utilities.isEmpty(strDBName)) return; // OrderAgainst writes the file, but not with Mproject
		String dir = Constants.seqDataDir + strDBName;
		Utilities.checkCreateDir(dir, true); // CAS500 
		
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
	
	/***********************************************************
	 * ProjParams interface
	 */
	public String getLab(int iLabel)  {return paramLabel[iLabel];}
	public String getKey(int iLabel)  {return paramKey[iLabel];} // used by non mProj packages to get keyword
	public String getDef(int iLabel)  {return paramDef[iLabel];}
	
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
	private HashMap<String, Params> pLabelMap = new HashMap<String, Params> ();
	private HashMap<String, Params> pKeysMap = new HashMap<String, Params> ();
	
	// CAS534 keep in order; CAS519 added abbrev_name;
	
	private final String [] paramKey = { // do NOT change w/o a global search as still hardcoded in many places
			"category", "display_name", "abbrev_name", 
			"grp_type", "description",  "min_display_size_bp", "annot_kw_mincount", 
			"grp_prefix", "min_size", "annot_keywords",
			"sequence_files", "anno_files",
			"mask_all_but_genes", "order_against"
	};
	private final String [] paramDef = {
			"Uncategorized", "", "", 
			"Chromosome", "", "0", "50",
			"", "100000", "",
			"", "",
			"0", ""
	};
	private String [] paramLabel = {
			"Category", "Display name", "Abbreviation", 
			"Group type", "Description", "DP cell size", "Anno key count",
			"Group prefix", "Minimum length", "Anno keywords", 
			"Sequence files", "Anno files",
			"Mask non-genes", "Order against"
	};
	// These are used by ProjParams to display propLabel instead of propKey
	public final int sCategory =  	0;
	public final int sDisplay =   	1;
	public final int sAbbrev =    	2;
	public final int sGrpType =   	3;
	public final int sDesc =      	4;
	public final int sDPsize =    	5;
	public final int sANkeyCnt =  	6;
	
	public final int lGrpPrefix = 	7;
	public final int lMinLen =   	8;
	public final int lANkeyword = 	9;
	public final int lSeqFile =   	10;
	public final int lAnnoFile =  	11;
	
	public final int aMaskNonGenes = 12;
	public final int aOrderAgainst = 13;	
	
	public final int xDisplay = 1;
	public final int xLoad = 2;
	public final int xAlign = 3;
	
	private class Params {
		private Params(int index, String label, String key, String defVal) {
			this.index = index;
			this.label = label;
			this.key = key;   			// write to file
			this.defVal = 	defVal;  	// default
			projVal =		defVal;  	// assigned in proj params
			
			if (index<7) isSum=true;
			else if (index<12) isLoad=true;
			else isAS=true;
		}
		private boolean isDBvalDef() {
			if (index==aMaskNonGenes && dbVal.contentEquals("")) return true;
			return dbVal.equals(defVal);
		}
		private String getStr() {
			if (index==aMaskNonGenes && dbVal.contentEquals("1")) return label + ": yes";
			return label + ": " + dbVal;
		}
		
		private void prtDiff() {
			if (TRACE && !projVal.contentEquals(dbVal))
				System.out.format("Param file != DB: %-10s %-10s %-10s  File=%s  DB=%s  Default=%s\n", 
						strDBName, strDisplayName, label, projVal, dbVal, defVal);
		}
		int index=-1;
		String key="", label="";
		String defVal="", projVal="", dbVal="";
		boolean isSum=false, isLoad=false, isAS=false;
	}
}
