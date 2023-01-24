package backend;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.TreeMap;
import java.util.Vector;
import java.util.Iterator;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.Comparator;
import java.util.zip.GZIPInputStream;

import symap.projectmanager.common.ProjectManagerFrameCommon;
import util.ErrorReport;
import util.ProgressDialog;
import util.Logger;
import util.Utilities;

public class Utils 
{
	static TreeMap<String,Float> mStats;
	static Vector<String> mKeyOrder;
	static TreeMap<String,Vector<Integer>> mHistLevels;
	static TreeMap<String,TreeMap<Integer,Integer>> mHist;
	
	public static void prt(Logger log, String msg) { // CAS520 add
		log.msg(msg);
	}
	public static void prtNumMsg(ProgressDialog prog, int num, String msg) {
		prog.appendText(String.format("%9d %s           ", num, msg));
	}
	public static void prtNumMsg(Logger log, int num, String msg) {
		log.msg(String.format("%9d %s         ", num, msg));
	}
	public static void prtNumMsg(Logger log, long num, String msg) {
		log.msg(String.format("%9d %s          ", num, msg));
	}
	public static void prtNumMsg(int num, String msg) {
		System.out.format("%9d %s              \n", num, msg);
	}
	public static void prtNumMsgNZ(int num, String msg) {
		if (num>0) System.out.format("%9d %s              \n", num, msg);
	}
	
	public static void timeMsg(Logger log, long startTime, String msg) {
		log.msg(msg + " done:  " 
				+ Utilities.getDurationString(System.currentTimeMillis()-startTime) 
				+ "                    \n");		
	}
	public static void die(String msg) {
		System.err.println("Fatal error: " + msg);
	}
	public static String fileFromPath(String path) {
		int i = path.lastIndexOf("/");
		if (i<=0) return path;
		return path.substring(i);
	}
	public static String pathOnly(String path) {
		int i = path.lastIndexOf("/");
		if (i<=0) return path;
		return path.substring(0, i);
	}
	public static BufferedReader openGZIP(String file) {
		try {
			File f = new File (file);
			if (!file.endsWith(".gz")) {
				if (f.exists())
	    				return new BufferedReader ( new FileReader (f));
			}
			if (file.endsWith(".gz")) {
				FileInputStream fin = new FileInputStream(file);
				GZIPInputStream gzis = new GZIPInputStream(fin);
				InputStreamReader xover = new InputStreamReader(gzis);
				return new BufferedReader(xover);
			}
		}
		catch (Exception e) {
	    		ErrorReport.print("Cannot open file " + file); 
	    }
		return null;
	}
	/********************************************************
	 * DONE 
	 */
	public static boolean checkDoneFile(String dir) 
	{
		File d = new File(dir);
		if (!d.exists() || d.isFile()) return false;
		
		File f = new File(d,"all.done");
		if (Constants.TRACE) System.out.println(" Trace: all.done " + dir + " " + d.exists());
		if (f.exists()) return true;
		
		return false;
	}
	public static int checkDoneMaybe(String dir) {
		File d = new File(dir);
		if (!d.exists() || d.isFile()) return 0;
		
		int numFiles=0;
		for (File x : d.listFiles()) {
			if (!x.isFile()) continue;
			if (x.isHidden()) continue;
			
			String path = x.getName();
			String name = path.substring(path.lastIndexOf("/")+1, path.length());
			if (name.endsWith(Constants.doneSuffix)) continue;
			if (name.endsWith(".log")) continue; // pre v5
			
			if (name.endsWith(Constants.mumSuffix)) numFiles++;			
		}
		return numFiles;
	}
	public static void writeDoneFile(String dir)  {
		try {
			File f = new File(dir);
			File d = new File(f,"all.done");
			d.createNewFile();
		}
		catch (Exception e) {ErrorReport.print(e, "Cannot write done file to " + dir);}
	}
	public static String getDateStr(long l) {
		DateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy hh:mm a");
		return sdf.format(l);
	}
	/*****************************************************
	 * Hists stats
	 */
	static void initStats() {
		mStats = new TreeMap<String,Float>();	
		mKeyOrder= new Vector<String>();
		mHist = new TreeMap<String,TreeMap<Integer,Integer>>();
	}
	static void initHist(String key, Integer... levels) {
		mHist.put(key, new TreeMap<Integer,Integer>());
		for (Integer i : levels) {
			mHist.get(key).put(i,0);
		}
		mHist.get(key).put(Integer.MAX_VALUE,0);
	}
	static void incHist(String key, int val) throws Exception {
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
	static void incHist(String key, int val, int inc) throws Exception
	{
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
	static void dumpHist() {
		for (String name : mHist.keySet()) {
			System.out.println("Histogram: " + name);	
			for (int lvl : mHist.get(name).keySet()) {
				int val = mHist.get(name).get(lvl);
				System.out.println("<" + lvl + ":" + val);
			}
		}
	}
	/************************************************
	 * Keyword stats
	 */
	static void incStat(String key, float inc){
		if (mStats != null) {
			if (!mStats.containsKey(key)) {
				mStats.put(key, 0.0F);	
				mKeyOrder.add(key);
			}
			mStats.put(key, inc + mStats.get(key));
		}
	}
	static void dumpStats() {
		if (mStats == null) return;
		for (String key : mKeyOrder) {
			double val = mStats.get(key);
			if (Math.floor(val)==val) System.out.format("%6d %s\n", (int)val, key);
			else					  System.out.format("%6.2f %s\n", val, key);
		}
	}
	static void uploadStats(UpdatePool db, int pair_idx, int pidx1, int pidx2) throws Exception
	{
		if (mStats == null) return;
		for (String key : mKeyOrder) {
			Integer val = mStats.get(key).intValue();
			
			db.executeUpdate("replace into pair_props (pair_idx,proj1_idx,proj2_idx,name,value) values(" + pair_idx + 
					"," + pidx1 + "," + pidx2 + ",'" + key + "','" + val + "')");			
		}
	}
	/**********************************************************/
	static int[] strArrayToInt(String[] sa) {
		int[] ia = new int[sa.length];
		for (int i = 0; i < sa.length; i++)
			ia[i] = Integer.parseInt(sa[i]);
		return ia;
	}
	
	public static String intArrayJoin(int[] sa, String delim) {
		String out = "";
		if (sa != null) {
			for (int i = 0; i < sa.length; i++) {
				out += sa[i];
				if (i < sa.length -1)
					out += delim;
			}
		}
		return out;
	}	
	static String intArrayToBlockStr(int[] ia) {
		String out = "";
		if (ia != null) {
			for (int i = 0; i < ia.length; i+=2) {
				out += ia[i] + ":" + ia[i+1];
				if (i + 1 < ia.length - 1)
					out += ",";
			}
		}
		return out;
	}
	
	static String strVectorJoin(java.util.Vector<String> sa, String delim) {
		String out = "";
		for (int i = 0; i < sa.size(); i++)
		{
			out += sa.get(i);
			if (i < sa.size() -1)
				out += delim;
		}
		return out;
	}
	
	static boolean intervalsTouch(int s1,int e1, int s2, int e2) {
		return intervalsOverlap(s1,e1,s2,e2,0);
	}	
	
	static public boolean intervalsOverlap(int s1,int e1, int s2, int e2, int max_gap) {
		int gap = Math.max(s1,s2) - Math.min(e1,e2);
		return (gap <= max_gap);
	}
	
	// Returns the amount of the overlap (negative for gap)
	static int intervalsOverlap(int s1,int e1, int s2, int e2) {
		int gap = Math.max(s1,s2) - Math.min(e1,e2);
		return -gap;
	}	
	static boolean intervalContained(int s1,int e1, int s2, int e2)
	{
		return ( (s1 >= s2 && e1 <= e2) || (s2 >= s1 && e2 <= e1));
	}	
	
	static float simpleRatio(int top, int bot) {
		float ratio = (float)(.1*Math.round(10.0*(float)top/(float)bot));
		return ratio;
	}
	
	static public String join(Collection<?> s, String delimiter)  {
		String buffer = "";
	    Iterator<?> iter = s.iterator();
	    while (iter.hasNext()) 
	    {
	    	buffer += iter.next().toString();
	        if (iter.hasNext()) 
	        	buffer += delimiter;
	    }
        return buffer;	
	} 
	/*****************************************************/
	static int getPairIdx(int proj1Idx, int proj2Idx, UpdatePool conn) throws SQLException {
		int idx = 0;
		
		String st = "SELECT idx FROM pairs WHERE proj1_idx='" + proj1Idx + "' AND proj2_idx='" + proj2Idx +"'";
		ResultSet rs = conn.executeQuery(st);
		if (rs.next())
			idx = rs.getInt("idx");
		rs.close();
		
		return idx;
	}

	public static boolean yesNo(String question) {
		BufferedReader inLine = new BufferedReader(new InputStreamReader(System.in));

		System.err.print(question + " (y/n)? "); // CAS42 SOP no newline; easy to miss otherwise
		try {
			String resp = inLine.readLine();
			if (resp.equals("y"))
				return true;
			else if (resp.equals("n"))
				return false;
			else {
				System.err.println("Sorry, could not understand the response, please try again:");
				System.err.print(question + " (y/n)? "); // CAS42 SOP no newline; easy to miss otherwise
				resp = inLine.readLine();
				if (resp.equals("y"))
					return true;
				else if (resp.equals("n"))
					return false;
				else {
					System.err.println("Sorry, could not understand the response, exiting.");
					System.exit(0);
					// Have to just exit since returning "n" won't necessarily cause an exit
					return false;
				}
			}

		} catch (Exception e){return false;}
	}
	
	public static String getParamsName()
	{
		String s1 = ProjectManagerFrameCommon.MAIN_PARAMS;
		String s2 = Constants.paramsFile;
		File f = new File(s1);
		if (f.isFile()) {
			return s1;	
		}
		f = new File(s2);
		if (f.isFile()) {
			return s2;	
		}
		System.err.println("Parameters file " + s1 + " not found");
		return s1;
	}
	// Sort the Comparable array, and apply the same sorting to the Object array,
	// so they stay parallel.
	// The purpose of cmp is to sort nulls to one end.
	public static void HeapDoubleSort(Comparable[] a, Object[] aa, ObjCmp cmp){
        int i,f,s;
        for(i=1;i<a.length;i++){
            Comparable e = a[i];
            Object ee = aa[i];
            s = i;
            f = (s-1)/2;
            while(s > 0 && cmp.compare(a[f], e) < 0){
                a[s] = a[f];
                aa[s] = aa[f];
                s = f;
                f = (s-1)/2;
            }
            a[s] = e;
            aa[s] = ee;
        }
        for(i=a.length-1;i>0;i--){
            Comparable value = a[i];
            Object vv = aa[i];
            a[i] = a[0];
            aa[i] = aa[0];
            f = 0;
            if(i == 1)
                s = -1;
            else
                s = 1;
            if(i > 2 && cmp.compare(a[1], a[2]) < 0)
                s = 2;
            while(s >= 0 && cmp.compare(value, a[s]) < 0){
                a[f] = a[s];
                aa[f] = aa[s];
                f = s;
                s = 2*f+1;
                if(s+1 <= i-1 && cmp.compare(a[s], a[s+1]) < 0)
                    s = s+1;
                if(s > i-1)
                    s = -1;
            }
            a[f] = value;
            aa[f] = vv;
        }
    }	
	public static class ObjCmp implements Comparator<Comparable>
	{
		public int compare(Comparable a, Comparable b)
		{
			if (a == null)
			{
				if (b == null) return 0;
				else return +1;
			}
			else if (b == null)
			{
				return -1;
			}
			else return a.compareTo(b);
		}
	}
	
	public static String reverseComplement(String in)
	{
		in = (new StringBuffer(in)).reverse().toString().toUpperCase();
		in = in.replace('A', 't');
		in = in.replace('G', 'c');
		in = in.replace('C', 'g');
		in = in.replace('T', 'a');
		return in.toLowerCase();
	}
}