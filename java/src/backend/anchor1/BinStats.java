package backend.anchor1;

import java.util.TreeMap;
import java.util.Vector;

import backend.Constants;
import symap.Globals;
import util.ProgressDialog;

/**************************************
 * shared by Proj and Groups for bins; 
 * -tt keys are recorded and printed
 * -tt histograms are recorded and printed
 */
public class BinStats {
	protected int mNBins=0;
	
	long mTotalLen = 0;
	int mTotalHits = 0, mHitsRm = 0;
	int nABins = 0, nAcnt=0, nArm=0, nAmix=0, nAmax=0;
	
	protected BinStats(){}
	
	protected String debugInfoAS() { 
		String avgSize =   String.format("%.2f", ((double)nAcnt/(double)nABins));

		return String.format("    AnnoSets %-,5d  AvgAnno %-5s   Max %-,5d  Mix %-,5d   Removed %-,5d",
				nABins, avgSize,  nAmax, nAmix, nArm);
	}
	protected String debugInfoHB() { 
		int avgLen =   Math.round(mTotalLen/mNBins);
		
		String avgHits =   String.format("%.2f", simpleRatio(mTotalHits, mNBins));
		
		return String.format("    HitBins  %-,5d  AvgHits %-5s   AvgLen %-,5d    HitsConsidered %-,5d Removed %-,5d",
				mNBins, avgHits, avgLen,  mTotalHits, mHitsRm);
	}
	/*****************************************************
	 * Hists stats;
	 */
	private  static TreeMap<String,Float> mStats = null;
	private static Vector<String> mKeyOrder = null;
	private static TreeMap<String,TreeMap<Integer,Integer>> mHist = null;
		
	protected static void initStats() {
		if (Constants.PRT_STATS) {
			mStats = new TreeMap<String,Float>();	
			mKeyOrder= new Vector<String>();
		}
		if (Globals.TRACE) // need -tt to see histogram
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
	
	protected static void dumpHist(ProgressDialog log) { 
		if (mHist==null || !Constants.PRT_STATS) return;
		
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
		mHist.clear(); 
		mHist=null;
	}
	/************************************************
	 * Keyword stats
	 */
	protected static void incStat(String key, float inc){
		if (mStats == null) return;
		
		if (!mStats.containsKey(key)) {
			mStats.put(key, 0.0F);	
			mKeyOrder.add(key);
		}
		mStats.put(key, inc + mStats.get(key));
	}
	protected static void dumpStats(ProgressDialog log) {
		if (mStats == null || !Constants.PRT_STATS) return;
		
		for (String key : mKeyOrder) {
			double val = mStats.get(key);
			if (Math.floor(val)==val) log.msgToFile(String.format("%6d %s\n", (int)val, key));
			else					  log.msgToFile(String.format("%6.2f %s\n", val, key));
		}
		mStats.clear(); 
		mStats=null;
		
		if (Globals.TRACE) dumpHist(log); // CAS560 was not be printed; still does not seem functional...
	}
	private float simpleRatio(int top, int bot) { // CAS569 moved from Utils
		float ratio = (float)(.1*Math.round(10.0*(float)top/(float)bot));
		return ratio;
	}
}
