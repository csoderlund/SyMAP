package backend;

import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;
import java.util.Enumeration;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import symap.manager.Mproject;
import util.Cancelled;
import util.ErrorReport;
import util.Logger;

/*****************************************************
 * CAS534 renamed backend.Project to SyProj
 * Contains project object and extended methods for backend
 */
public class SyProj  {
	public int idx;
	public String name, grpPrefix,  category = "", displayName = ""; 
	public Mproject mProj;
	
	private String orderAgainst = "";
	
	private Vector<Group> 			groups;
	private TreeMap<Integer,Group> 	idx2Grp;
	private TreeMap<String,Integer> grpName2Idx;
	
	private UpdatePool conn;
	private Pattern namePat;
	private TreeMap<Integer, Integer> 	annotIdx2GrpIdx;
	private TreeMap<Integer, AnnotElem> annotIdx2Elem;

	private long totalSize = 0;
	private Logger log;
	private int topN=2;
	
	public SyProj(UpdatePool pool, Logger log, Mproject proj, String name, int topN, QueryType qt) throws Exception {
		this.conn = pool;
		this.log = log;
		this.mProj = proj;
		this.name = name;
		this.topN = topN;
		
		idx = 			proj.getIdx();
		grpPrefix = 	mProj.getGrpPrefix().trim();
		category =    	mProj.getdbCat();
		displayName =   mProj.getDisplayName();
		orderAgainst =  mProj.getOrderAgainst();
		totalSize 	= 	mProj.getLength();
		
		String regx = "(" + grpPrefix + ")?(\\w+).*"; 
		namePat = Pattern.compile(regx,Pattern.CASE_INSENSITIVE);
		groups =      		new Vector<Group>(0,1);
		idx2Grp = 			new TreeMap<Integer,Group>();
		grpName2Idx = 		new TreeMap<String,Integer>();
		annotIdx2GrpIdx = 	new TreeMap<Integer,Integer>();
		annotIdx2Elem = 	new TreeMap<Integer,AnnotElem>();
		
		loadGroups(qt);
	}
	
	/** both **/
	public String getName() { return name; }
	public int getIdx() { return idx; }
	public String getOrderAgainst() { return orderAgainst; }
	public boolean hasOrderAgainst() {return !getOrderAgainst().contentEquals("");}
	
	public Vector<Group> getGroups() { return groups; }
	
	public int grpIdxFromQuery(String name) {	
		Matcher m = namePat.matcher(name);
		if (m.matches()) {
			String grp = m.group(2);
			if (grpName2Idx.containsKey(grp))  return grpName2Idx.get(grp);
		}
		else {
			if (grpName2Idx.containsKey(name)) return grpName2Idx.get(name);
		}
		return -1;
	}
	
	public long getSizeBP() { return totalSize; }
	public boolean isUnordered() { return false;}
	
	public void loadAnnotation(UpdatePool pool) throws SQLException {
		int totalGenes=0;
		
		for (Enumeration<Group> e = groups.elements(); e.hasMoreElements();) {
			Group g = e.nextElement();
			if (Cancelled.isCancelled()) return;
			
			int nGenes = g.loadAnnotation(pool, annotIdx2GrpIdx, annotIdx2Elem);
			if (Constants.PRT_STATS) 
				Utils.prtNumMsg(log, nGenes, " on " + g.fullname);
			totalGenes+=nGenes;
		}
		Utils.prtNumMsg(log, totalGenes, "total genes for " + name);
	}
	public Group getGrpByIdx(int idx) {
		return idx2Grp.get(idx);
	}
	
	public void collectPGInfo() {
		for (Group g : groups) {
			g.collectPGInfo(name);
		}
	}
	// Go through the hit bins and mark the ones passing TopN
    //  HitBins in Group file called 
	public void filterHits(Set<Hit> hits) throws Exception {
		for (Group g : groups) {// HitBins are by group (e.g. chr)
			g.filterHits(hits);
		}
	}
	public void setGrpGeneParams(int maxGap, int maxGeneSize) {
		for (Group g : groups) {
			g.setGeneParams(maxGap, maxGeneSize);
		}
	}
	// Called for sh.query and sh.target to filter both separately
	//   overlapping hits are binned and the TopN applied to the bin lists
	public void checkPreFilter2(Hit hit, SubHit sh) throws Exception{
		Group grp = idx2Grp.get(sh.grpIdx);
		grp.checkAddToHitBin2(hit,sh);		// bins stored by group (e.g. chr)
	}
	
	/***********************************************************/
	private void loadGroups(QueryType qt) throws SQLException {			
		// CAS42 1/4/17 changed from selecting * to being specific and using get(n)
		ResultSet rs = conn.executeQuery("SELECT idx, name, fullname FROM xgroups WHERE proj_idx=" + idx);
        while(rs.next())  {
            int idx= rs.getInt(1);
            String name = rs.getString(2);
            String fullname = rs.getString(3);
            
            Group grp = new Group(topN,name,fullname,idx,qt); 
          	groups.add(grp);
          
            idx2Grp.put(idx,grp);
            grpName2Idx.put(name,idx);
            grpName2Idx.put(fullname,idx);
        }
        rs.close();
	}
	public void printBinStats(){
		BinStats bs = new BinStats(); // this class is only used for debugging 

		for (Group g : groups) 
			g.collectBinStats(bs);
		
		if (bs.mNBins == 0) return;
		
		int avgSize =   Math.round(bs.mTotalSize/bs.mNBins);
		float avgHits = Utils.simpleRatio(bs.mTotalHits, bs.mNBins);
		
		log.msg(name + ": Seq: " + 
				bs.mNBins + " bins, avg size " + avgSize + ", avg binned hits " + avgHits
				+ ", hitsConsidered " + bs.mTotalHits  + ", removed " + bs.mHitsRm);
	}	
	static String getProjProp(int projIdx, String name, UpdatePool conn)  {
		try {
			String value = null;
			
			String st = "SELECT value FROM proj_props WHERE proj_idx='" + projIdx + "' AND name='" + name +"'";
			ResultSet rs = conn.executeQuery(st);
			if (rs.next())
				value = rs.getString("value");
			rs.close();
			
			return value;
		}
		catch (Exception e) {ErrorReport.print(e, "Getting parameters from database"); return "";}
	}
}
