package backend;

import java.sql.ResultSet;
import java.util.TreeMap;
import java.util.Vector;

/**************************************
 * shared by SyProj and Groups for bins
 */
public class BinStats {
	int mNBins = 0;
	long mTotalSize = 0;
	int mTotalHits = 0;
	int mHitsRm=0;
	
	public BinStats(){}
	
	/*****************************************************
	 * Hists stats; CAS534 the Hists and Stats use to always be run, but now only if -s
	 * CAS535 moved from Utils to BinStat as it only has the above few lines
	 */
	public  static TreeMap<String,Float> mStats;
	private static Vector<String> mKeyOrder;
	private static TreeMap<String,TreeMap<Integer,Integer>> mHist;
		
	public static void initStats() {
		mStats = new TreeMap<String,Float>();	
		mKeyOrder= new Vector<String>();
		mHist = new TreeMap<String,TreeMap<Integer,Integer>>();
	}
	public static void initHist(String key, Integer... levels) {
		if (mHist==null) return;
		mHist.put(key, new TreeMap<Integer,Integer>());
		for (Integer i : levels) {
			mHist.get(key).put(i,0);
		}
		mHist.get(key).put(Integer.MAX_VALUE,0);
	}
	public static void incHist(String key, int val) throws Exception {
		if (mHist==null) return;
		int l = -1;
		for (int lvl : mHist.get(key).keySet()) {
			l = lvl;
			if (val < lvl) break;
		}
		if (l == -1) {
			throw(new Exception("Bad level: " + val));	
		}
		int curval = mHist.get(key).get(l);
		curval++;
		mHist.get(key).put(l,curval);
	}
	public static void incHist(String key, int val, int inc) throws Exception {
		if (mHist==null) return;
		int l = -1;
		for (int lvl : mHist.get(key).keySet()) {
			l = lvl;
			if (val < lvl) break;
		}
		if (l == -1) {
			throw(new Exception("Bad level: " + val));	
		}
		int curval = mHist.get(key).get(l);
		curval += inc;
		mHist.get(key).put(l,curval);
	}	
	public static void dumpHist() {
		if (mHist==null) return;
		for (String name : mHist.keySet()) {
			System.out.println("Histogram: " + name);	
			for (int lvl : mHist.get(name).keySet()) {
				int val = mHist.get(name).get(lvl);
				if (val>0) System.out.println("<" + lvl + ":" + val);
			}
		}
	}
	/************************************************
	 * Keyword stats
	 */
	public static void incStat(String key, float inc){
		if (mStats != null) {
			if (!mStats.containsKey(key)) {
				mStats.put(key, 0.0F);	
				mKeyOrder.add(key);
			}
			mStats.put(key, inc + mStats.get(key));
		}
	}
	public static void dumpStats() {
		if (mStats == null) return;
		for (String key : mKeyOrder) {
			double val = mStats.get(key);
			if (Math.floor(val)==val) System.out.format("%6d %s\n", (int)val, key);
			else					  System.out.format("%6.2f %s\n", val, key);
		}
	}
	// CAS534 no reason to load these, only do it on -s; then remove the next time run.
	public static void uploadStats(UpdatePool db, int pair_idx, int pidx1, int pidx2) throws Exception {
		if (mStats == null) return;
		
		ResultSet rs = null;
		for (String key : mKeyOrder) {
			Integer val = mStats.get(key).intValue();
			if (Constants.PRT_STATS)
				db.executeUpdate("replace into pair_props (pair_idx,proj1_idx,proj2_idx,name,value) values(" + pair_idx + 
					"," + pidx1 + "," + pidx2 + ",'" + key + "','" + val + "')");	
			else {
				String st = "SELECT value FROM pair_props  WHERE pair_idx='" + pair_idx + "' AND name='" + key +"'";
				rs = db.executeQuery(st);
				if (rs.next()) 
					db.executeUpdate("delete from pair_props where key='" + key + "' and pair_idx=" + pair_idx);
			}
		}
		if (rs!=null) rs.close();
	}
}
