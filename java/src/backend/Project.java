package backend;

import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.TreeMap;
import java.util.Set;
import java.util.Vector;
import java.util.Enumeration;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import util.Cancelled;
import util.Logger;
import backend.Group;

enum ProjType {unknown,fpc,pseudo};

public class Project 
{
	public String name;
	public int idx;
	public ProjType type;
	private Vector<Group> groups;
	public TreeMap<Integer,Group> idx2Grp;
	private TreeMap<String,Integer> grpName2Idx;
	String grpPrefix;
	private UpdatePool conn;
	private Pattern namePat;
	private TreeMap<String,Integer> annotExcludes;
	private TreeMap<String,Integer> annotIncludes;
	private TreeMap<Integer,Integer> annotIdx2GrpIdx;
	private TreeMap<Integer,AnnotElem> annotIdx2Elem;
	private QueryType queryType;
	private TreeMap<FileType,TreeMap<String,HitBin>> queryBinMap; // TopN hit lists, by query name and type
	private int topN = 0;
	
	// For FPC projects, the unanchored contigs get a group called "0" with this index
	public int unanchoredGrpIdx = 0;
	
	private FPCData fpcData = null;
	private long totalSize = 0;
	private Logger log;
	private SyProps props;
	String orderAgainst = "";
	String category = "";
	String displayName = ""; 
	
	public Project(UpdatePool pool, Logger log, SyProps props, String name, ProjType type, QueryType qt) throws Exception
	{
		this.conn = pool;
		this.log = log;
		this.name = name;
		this.type = type;
		this.queryType = qt;
		this.props = props;
		
		switch(qt)
		{
			case Query:
				this.topN = props.getInt("topn"); 
				break;
			case Target:
				this.topN =  props.getInt("topn"); 
				break;
			default:
				break;
		}
		
		this.idx = conn.getProjIdx(name, type);
		if (idx <= 0)
		{
			log.msg("Can't find project " + name);
			throw(new Exception("Can't find project " + name));			
		}
		
		this.groups = new Vector<Group>(0,1);
		this.grpPrefix = pool.getProjProp(idx, "grp_prefix").trim();
		this.category = pool.getProjProp(idx, "category").trim();
		this.displayName = pool.getProjProp(idx, "display_name").trim();
		
		String regx = "(" + grpPrefix + ")?(\\w+).*"; 
		this.namePat = Pattern.compile(regx,Pattern.CASE_INSENSITIVE);
		this.idx2Grp = new TreeMap<Integer,Group>();
		this.grpName2Idx = new TreeMap<String,Integer>();
		this.annotExcludes = new TreeMap<String,Integer>();
		this.annotIncludes = new TreeMap<String,Integer>();
		this.annotIdx2GrpIdx = new TreeMap<Integer,Integer>();
		this.annotIdx2Elem = new TreeMap<Integer,AnnotElem>();
		loadGroups(qt);
		if (isFPC())
			this.fpcData = new FPCData(conn,log,idx,name,this);
		this.queryBinMap = new TreeMap<FileType,TreeMap<String,HitBin>>();
		for (FileType ft : FileType.values())
			this.queryBinMap.put(ft, new TreeMap<String,HitBin>());
		this.orderAgainst = pool.getProjProp(idx, "order_against");
		
		this.totalSize = getSize();
	}
	
	public String getName() { return name; }
	public int getIdx() { return idx; }
	public int getUnanchoredGrpIdx() { return unanchoredGrpIdx; }
	public long getSizeBP() { return totalSize; }
	public boolean isPseudo() { return (type == ProjType.pseudo); }
	public boolean isFPC() { return (type == ProjType.fpc); }
	public FPCData getFPCData() { return fpcData; }
	public Vector<Group> getGroups() { return groups; }
	public boolean isUnordered() { return false;}
	public boolean orderedAgainst() {return !orderAgainst.equals("");}

	public void loadAnnotation(UpdatePool pool, TreeMap<String, AnnotAction> annotSpec) throws SQLException
	{
		TreeMap<String,Integer> annotData = new TreeMap<String,Integer>();
		
		log.msg("Load " + name + " annotation:");
		for (Enumeration<Group> e = groups.elements(); e.hasMoreElements();)
		{
			Group g = e.nextElement();
			if (Cancelled.isCancelled()) return;
			g.loadAnnotation(pool, annotSpec, annotData, annotIdx2GrpIdx, annotIdx2Elem/*, geneName2AnnotIdx*/);
		}
		
		if (annotData.keySet().isEmpty())
			log.msg("   None");
		else
			for (String type : annotData.keySet())
				log.msg("   " + type + " " + annotData.get(type));
	}
	
	private void loadGroups(QueryType qt) throws SQLException
	{			
		// CAS 1/4/17 changed from selecting * to being specific and using get(n)
		ResultSet rs = conn.executeQuery("SELECT idx, name, fullname FROM groups WHERE proj_idx=" + idx);
        while(rs.next())
        {
            int idx= rs.getInt(1);
            String name = rs.getString(2);
            String fullname = rs.getString(3);
            Group grp = new Group(props,name,fullname,idx,qt);
            if (isFPC())
            {
	            if (!"0".equals(name))
	            		groups.add(grp);
	            else
	            		unanchoredGrpIdx = idx;
            }
            else
            {
            		groups.add(grp);
            }
            idx2Grp.put(idx,grp);
            grpName2Idx.put(name,idx);
            grpName2Idx.put(fullname,idx);
        }
        rs.close();
	}
	
	public int grpIdxFromQuery(String name)
	{	
		Matcher m = namePat.matcher(name);
		if (m.matches())
		{
			String grp = m.group(2);
			if (grpName2Idx.containsKey(grp))
			{
				return grpName2Idx.get(grp);
			}
		}
		else
		{
			if (grpName2Idx.containsKey(name))
			{
				return grpName2Idx.get(name);
			}			
		}
		
		return -1;
	}
	public boolean isConcatenated() throws Exception
	{
		String val = Utils.getProjProp(idx, "concatenated", conn);
		boolean ret = false;
		if (val != null)
		{
			if (val.equals("1"))
			{
				ret = true;	
			}
		}
		return ret;
	}
	public int getGrpIdx(String name)
	{
		return grpName2Idx.get(name);
	}
	
	public Group getGrpByIdx(int idx) {
		return idx2Grp.get(idx);
	}

	public void incAnnotIncExcCount(AnnotAction aa, String type)
	{
		TreeMap<String,Integer> counts = (aa == AnnotAction.Exclude ? annotExcludes : annotIncludes);
		if (!counts.containsKey(type))
			counts.put(type,0);
		counts.put(type,counts.get(type) + 1);
	}
	
	public void printAnnotIncExcCounts()
	{
		if (annotIncludes.keySet().size() > 0 || annotExcludes.keySet().size() > 0) {
			log.msg(name + " annotation includes/excludes:");
			for (String type : annotIncludes.keySet())
				log.msg("  Include: " + type + ": " + annotIncludes.get(type));
			for (String type : annotExcludes.keySet())
				log.msg("  Exclude: " + type + ": " + annotExcludes.get(type));
		}
	}
	
	// PreFilter = don't store the hit if it already fails TopN
	public void checkPreFilter(Hit hit, SubHit sh) throws Exception
	{
		if (sh.fileType == FileType.Pseudo)
		{
			// Pseudo: overlapping hits are binned and the TopN applied to the bin lists
			Group grp = idx2Grp.get(sh.grpIdx);
			grp.checkAddToHitBin(hit,sh);
		}
		else if (sh.fileType == FileType.Gene)
			checkAddToQueryList(hit,sh);
		else // Mrk or Bes
			checkAddToQueryList(hit,sh);
	}
	
	public void checkAddToQueryList(Hit hit, SubHit sh)
	{
		TreeMap<String,HitBin> Query2Bin = queryBinMap.get(sh.fileType);
		if (!Query2Bin.containsKey(sh.name))
		{
			Query2Bin.put(sh.name,new HitBin(hit,sh,topN,queryType));
			return;
		}
		Query2Bin.get(sh.name).checkAddHit(hit, sh);		
	}
	
	public void printBinStats()
	{
		for (FileType ft : queryBinMap.keySet())
		{
			TreeMap<String,HitBin> binmap = queryBinMap.get(ft);
			int nbins = binmap.keySet().size();
			int nhits = 0;
			for (HitBin hb : binmap.values())
				nhits += hb.mHitsConsidered;
			if (nhits == 0) continue;
			
			float avghits = Utils.simpleRatio(nhits, nbins);
			log.msg(name + ": " + ft.toString() + ": " + nbins + " queries, avg binned hits " + avghits );
		}

		BinStats bs = new BinStats();

		for (Group g : groups)
			g.collectBinStats(bs);
		
		if (bs.mNBins == 0) return;
		
		int avgSize = Math.round(bs.mTotalSize/bs.mNBins);
		float avgHits = Utils.simpleRatio(bs.mTotalHits, bs.mNBins);
		
		log.msg(name + ": " + bs.mNBins + " bins, avg size " + avgSize + ", avg binned hits " + avgHits );
	}	
	
	public void filterHits(Set<Hit> hits, boolean keepFamilies) throws Exception
	{
		// Go through the hit bins and mark the ones passing TopN
		if (isFPC())
		{
			for (FileType ft : queryBinMap.keySet())
			{
				TreeMap<String,HitBin> binmap = queryBinMap.get(ft);
				for (HitBin hb : binmap.values())
					hb.filterHits(hits, keepFamilies);
			}
		}
		else
		{
			for (Group g : groups)
				g.filterHits(hits, keepFamilies);
		}
	}
	public void setGrpGeneParams(int maxGap, int maxGeneSize)
	{
		for (Group g : groups)
		{
			g.setGeneParams(maxGap, maxGeneSize);
		}
	}

	public void collectPGInfo()
	{
		for (Group g : groups)
		{
			g.collectPGInfo(name);
		}
	}
	public long getSize() throws SQLException
	{
		long size = 0;
		
		if (isFPC())
			return fpcData.getTotalCBSize();
		else {
			ResultSet rs = conn.executeQuery("SELECT SUM(length) AS size FROM pseudos, groups " +  
					" WHERE groups.proj_idx = " + idx +
					" AND pseudos.grp_idx = groups.idx");
			if (rs.next())
				size = rs.getLong("size");
			rs.close();
		}
		
		return size;
	}
}
