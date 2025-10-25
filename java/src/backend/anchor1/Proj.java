package backend.anchor1;

import java.sql.ResultSet;
import java.util.TreeMap;
import java.util.Vector;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import database.DBconn2;
import symap.manager.Mproject;
import util.ErrorReport;
import util.ProgressDialog;

/*****************************************************
 * Used by AnchorMain1
 * 
 * Contains Mproject object plus groups and annotation; CAS575 removed BinStats
 */
public class Proj  {
	protected int idx;
	protected String name, grpPrefix,  category = "", displayName = ""; 
	protected Mproject mProj;
	
	private Vector<Group> 			groupVec;
	private TreeMap<Integer,Group> 	idx2Grp;
	private TreeMap<String,Integer> grpName2Idx;
	private boolean hasAnnot = true;
	
	private DBconn2 dbc2;
	private Pattern namePat;
	
	private long totalSize = 0; 
	private int topN=2;
	
	/*****************************************************************
	 * QueryType: AnnotLoadMain - Either; 
	 * 			  AnchorMain, SyntenyMain - Target, query
	 */
	protected Proj(DBconn2 dbc2, ProgressDialog log, Mproject proj, String name, int topN, int qType) throws Exception {
		this.dbc2 = dbc2;
		this.mProj = proj;
		this.name = name;
		this.topN = topN;
		
		idx = 			proj.getIdx();
		grpPrefix = 	mProj.getGrpPrefix().trim();
		category =    	mProj.getdbCat();
		displayName =   mProj.getDisplayName();
		totalSize 	= 	mProj.getLength();
		hasAnnot 	=	mProj.hasGenes();
		
		String regx = "(" + grpPrefix + ")?(\\w+).*"; 
		namePat = Pattern.compile(regx,Pattern.CASE_INSENSITIVE);
		groupVec =      	new Vector<Group>(0,1);
		idx2Grp = 			new TreeMap<Integer,Group>();
		grpName2Idx = 		new TreeMap<String,Integer>();
		
		loadGroups(qType);
	}
	private void loadGroups(int qType) throws Exception {	
	try { 
		ResultSet rs = dbc2.executeQuery("SELECT idx, name, fullname FROM xgroups WHERE proj_idx=" + idx);
        while(rs.next())  {
            int idx= rs.getInt(1);
            String name = rs.getString(2);
            String fullname = rs.getString(3);
            
            Group grp = new Group(topN,name, displayName + "." + fullname,idx,qType); 
          	groupVec.add(grp);
          
            idx2Grp.put(idx,grp);
            grpName2Idx.put(name,idx);
            grpName2Idx.put(fullname,idx);
        }
        rs.close();
	}
	catch (Exception e) {ErrorReport.print(e, "load groups"); throw e;}
	}
	/**********************************************************************/
	public String getName() { return name; }
	protected int getIdx() { return idx; }
	protected boolean hasAnnot() {return hasAnnot;}
	
	protected Vector<Group> getGroups() { return groupVec; }
	protected Group getGrpByIdx(int idx) {return idx2Grp.get(idx);}
	protected long getSizeBP() { return totalSize; } 
	
	protected int grpIdxFromQuery(String name) {	
		String s = name;
		
		Matcher m = namePat.matcher(name);
		if (m.matches()) s = m.group(2);
			
		if (grpName2Idx.containsKey(s)) return grpName2Idx.get(s);
		return -1;
	}
}
