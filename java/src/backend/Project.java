package backend;

/*****************************************************
 * Both projects being compared have a Project object. (Note, there is another Project.java in commons)
 * For FPC, the binning occurs in this file (in Group file for SEQ)
 */
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;
import java.util.Enumeration;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import util.Cancelled;
import util.Logger;

public class Project 
{
	public int idx;
	public String name, grpPrefix, orderAgainst = "", category = "", displayName = ""; 
	
	// For FPC projects, the unanchored contigs get a group called "0" with this index
	public int unanchoredGrpIdx = 0;
		
	private Vector<Group> 			groups;
	private TreeMap<Integer,Group> 	idx2Grp;
	private TreeMap<String,Integer> 	grpName2Idx;
	
	private UpdatePool conn;
	private Pattern namePat;
	private TreeMap<Integer, Integer> 	annotIdx2GrpIdx;
	private TreeMap<Integer, AnnotElem> 	annotIdx2Elem;

	private long totalSize = 0;
	private Logger log;
	private SyProps props;
	
	public Project(UpdatePool pool, Logger log, SyProps props, String name, QueryType qt) throws Exception
	{
		this.conn = pool;
		this.log = log;
		this.props = props;
		this.name = name;
		
		idx = conn.getProjIdx(name);
		if (idx <= 0) {
			log.msg("Cannot find project " + name);
			throw(new Exception("Cannot find project " + name));			
		}
		
		groups =      new Vector<Group>(0,1);
		grpPrefix =   pool.getProjProp(idx, "grp_prefix").trim();
		category =    pool.getProjProp(idx, "category").trim();
		displayName = pool.getProjProp(idx, "display_name").trim();
		
		String regx = "(" + grpPrefix + ")?(\\w+).*"; 
		namePat = Pattern.compile(regx,Pattern.CASE_INSENSITIVE);
		idx2Grp = 			new TreeMap<Integer,Group>();
		grpName2Idx = 		new TreeMap<String,Integer>();
		annotIdx2GrpIdx = 	new TreeMap<Integer,Integer>();
		annotIdx2Elem = 		new TreeMap<Integer,AnnotElem>();
		
		loadGroups(qt);
		
		orderAgainst = pool.getProjProp(idx, "order_against");
		
		totalSize = getSize();
	}
	
	public String getName() { return name; }
	public int getIdx() { return idx; }
	public int getUnanchoredGrpIdx() { return unanchoredGrpIdx; }
	public long getSizeBP() { return totalSize; }
	
	public Vector<Group> getGroups() { return groups; }
	public boolean isUnordered() { return false;}
	public boolean orderedAgainst() {return !orderAgainst.equals("");}
	
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
	public int getnClusters() {
		int n=0;
		for (Group g: groups) n+= g.getnClusters();
		return n;
	}
	private void loadGroups(QueryType qt) throws SQLException {			
		// CAS42 1/4/17 changed from selecting * to being specific and using get(n)
		ResultSet rs = conn.executeQuery("SELECT idx, name, fullname " +
				"FROM xgroups WHERE proj_idx=" + idx);
        while(rs.next())  {
            int idx= rs.getInt(1);
            String name = rs.getString(2);
            String fullname = rs.getString(3);
            
            Group grp = new Group(props,name,fullname,idx,qt);
          	groups.add(grp);
          
            idx2Grp.put(idx,grp);
            grpName2Idx.put(name,idx);
            grpName2Idx.put(fullname,idx);
        }
        rs.close();
	}
	
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
	
	public int getGrpIdx(String name){
		return grpName2Idx.get(name);
	}
	
	public Group getGrpByIdx(int idx) {
		return idx2Grp.get(idx);
	}
	
	// PreFilter
	// Called for sh.query and sh.target to filter both separately
	// For SEQ: overlapping hits are binned and the TopN applied to the bin lists
	public void checkPreFilter2(Hit hit, SubHit sh) throws Exception{
		Group grp = idx2Grp.get(sh.grpIdx);
		grp.checkAddToHitBin2(hit,sh);		// bins stored by group (e.g. chr)
	}
	public void printBinStats2() {
		if (Constants.PRT_STATS)
			for (Group g : groups) {
				if (Constants.PRT_STATS)
					log.msg(g.info());
			}
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
	
	// // Go through the hit bins and mark the ones passing TopN
	// SEQ: HitBins in Group file called 
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

	public void collectPGInfo() {// called for seq-seq
		for (Group g : groups) {
			g.collectPGInfo(name);
		}
	}
	public long getSize() throws SQLException {
		long size = 0;
		
		ResultSet rs = conn.executeQuery("SELECT SUM(length) AS size " +
				" FROM pseudos, xgroups " +  
				" WHERE xgroups.proj_idx = " + idx +
				" AND pseudos.grp_idx = xgroups.idx");
		if (rs.next())
			size = rs.getLong("size");
		rs.close();
		return size;
	}
}
