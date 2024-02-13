package backend.anchor1;

import java.sql.ResultSet;
import java.util.TreeMap;
import java.util.Vector;

import backend.Constants;
import backend.Utils;
import database.DBconn2;
import util.ProgressDialog;

/**************************************
 * shared by Proj and Groups for bins; Static methods also used by SyntenyMain
 * -s keys are recorded and printed
 * -tt histograms are recorded and printed
 */
public class BinStats {
	protected int mNBins=0;
	
	long mTotalLen = 0;
	int mTotalHits = 0, mHitsRm = 0;
	int nABins = 0, nAcnt=0, nArm=0, nAmix=0, nAmax=0;
	
	protected BinStats(){}
	
	protected String debugInfoAS() { // CAS540x
		String avgSize =   String.format("%.2f", ((double)nAcnt/(double)nABins));

		return String.format("#AnnoSets %-,5d  AvgAnno %-5s   Max %-,5d  Mix %-,5d   Removed %-,5d",
				nABins, avgSize,  nAmax, nAmix, nArm);
	}
	protected String debugInfoHB() { // CAS540x
		int avgLen =   Math.round(mTotalLen/mNBins);
		
		String avgHits =   String.format("%.2f", Utils.simpleRatio(mTotalHits, mNBins));
		
		return String.format("#HitBins  %-,5d  AvgHits %-5s   AvgLen %-,5d    HitsConsidered %-,5d Removed %-,5d",
				mNBins, avgHits, avgLen,  mTotalHits, mHitsRm);
	}
	/*****************************************************
	 * Hists stats; CAS534 the Hists and Stats use to always be run, but now only if -s/-tt
	 * CAS535 moved from Utils to BinStat as it only has the above few lines
	 */
	public  static TreeMap<String,Float> mStats = null;
	private static Vector<String> mKeyOrder = null;
	private static TreeMap<String,TreeMap<Integer,Integer>> mHist = null;
		
	protected static void initStats() {
		if (Constants.PRT_STATS) {
			mStats = new TreeMap<String,Float>();	
			mKeyOrder= new Vector<String>();
		}
		if (Constants.TRACE) 
			mHist = new TreeMap<String,TreeMap<Integer,Integer>>();
	}
	
	protected static void initHist(String key, Integer... levels) {//3,6,10,25,50,100
		if (mHist==null) return;
		
		mHist.put(key, new TreeMap<Integer,Integer>());
		for (Integer i : levels) {
			mHist.get(key).put(i,0);
		}
		mHist.get(key).put(Integer.MAX_VALUE,0);
	}
	protected static void incHist(String key, int num) throws Exception {
		if (mHist==null) return;
		
		int l = -1, last=0;
		for (int lvl : mHist.get(key).keySet()) {
			l = lvl;
			if (num < lvl) break;
			last = lvl;
		}
		if (l == -1) l=last;	
		
		int curval = mHist.get(key).get(l);
		curval++;
		mHist.get(key).put(l,curval);
	}
	
	public static void dumpHist(ProgressDialog log) { // CAS540 add log
		if (mHist==null) return;
		
		for (String name : mHist.keySet()) {
			log.msgToFile("Histogram: " + name);	
			for (int lvl : mHist.get(name).keySet()) {
				int val = mHist.get(name).get(lvl);
				if (val>0) {
					if (lvl==Integer.MAX_VALUE) log.msgToFile(String.format(">=100: %5d", val));
					else log.msgToFile(String.format("<%-3d: %5d", lvl, val));
				}
			}
		}
		mHist.clear(); // CAS546 add so will not display if algo2 is run next
		mHist=null;
	}
	/************************************************
	 * Keyword stats
	 */
	public static void incStat(String key, float inc){
		if (mStats == null) return;
		
		if (!mStats.containsKey(key)) {
			mStats.put(key, 0.0F);	
			mKeyOrder.add(key);
		}
		mStats.put(key, inc + mStats.get(key));
	}
	public static void dumpStats(ProgressDialog log) {// CAS540 add log
		if (mStats == null) return;
		
		for (String key : mKeyOrder) {
			double val = mStats.get(key);
			if (Math.floor(val)==val) log.msgToFile(String.format("%6d %s\n", (int)val, key));
			else					  log.msgToFile(String.format("%6.2f %s\n", val, key));
		}
		mStats.clear(); // CAS546 add so will not display if algo2 is run next
		mStats=null;
	}
	// CAS534 no reason to load these, only do it on -s; then remove the next time run.
	// CAS540 (now dead) writes to symap.log now; never used from database so this is not currently called
	//        was called in AnchorMain and SyntenyMain at end of processing
	protected static void uploadStats(DBconn2 dbc2, int pair_idx, int pidx1, int pidx2) throws Exception {
		if (mStats == null) return;
		
		ResultSet rs = null;
		for (String key : mKeyOrder) {
			Integer val = mStats.get(key).intValue();
			if (Constants.PRT_STATS)
				dbc2.executeUpdate("replace into pair_props (pair_idx,proj1_idx,proj2_idx,name,value) values(" + pair_idx + 
					"," + pidx1 + "," + pidx2 + ",'" + key + "','" + val + "')");	
			else {
				String st = "SELECT value FROM pair_props  WHERE pair_idx='" + pair_idx + "' AND name='" + key +"'";
				rs = dbc2.executeQuery(st);
				if (rs.next()) 
					dbc2.executeUpdate("delete from pair_props where key='" + key + "' and pair_idx=" + pair_idx);
			}
		}
		if (rs!=null) rs.close();
	}
}
