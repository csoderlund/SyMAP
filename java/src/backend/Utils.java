package backend;

import java.sql.ResultSet;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.Comparator;
import java.util.zip.GZIPInputStream;

import database.DBconn2;
import util.ErrorReport;
import util.ProgressDialog;
import util.Utilities;

public class Utils {
	// msgToFile prints to file and terminal, but not ProgressDialog
	// msg and appendText     prints to file, terminal, ProgressDialog
	
	// time only
	public static long getTime() { return System.currentTimeMillis();}
	
	public static void prtMsgTimeFile(ProgressDialog prog, String msg, long startTime) {
		String t = Utilities.getDurationString(getTime()-startTime);
		prog.msgToFile(String.format("%-30s %s", msg, t));	
	}
	public static void prtMsgTime(ProgressDialog prog, String msg, long startTime) {
		String t = Utilities.getDurationString(getTime()-startTime);
		prog.msg(String.format("%-30s %s", msg, t));	
	}
	public static void timeDoneMsg(ProgressDialog prog, String msg, long startTime) {
		String t = Utilities.getDurationString(getTime()-startTime);
		String m = String.format("%-35s  %s                           \n\n", msg, t);
		if (prog!=null) prog.msg(m);	
		else System.out.println(m);
	}
	public static String getElapsedTime(long startTime) {// CAS546 add
		return Utilities.getDurationString(getTime()-startTime);
	}
	
	// Memory and time
	public static long getTimeMem() { System.gc(); return System.currentTimeMillis();}
	
	public static void prtTimeMemUsage(ProgressDialog prog, String title, long startTime) {
		prog.msgToFile(getTimeMemUsage(title, startTime) + "\n\n");
	}
	public static void prtTimeMemUsage(String title, long startTime) {
		System.out.print(getTimeMemUsage(title, startTime) + "\n\n");
	}
	public static String getTimeMemUsage(String title, long startTime) {
		Runtime rt = Runtime.getRuntime();
		long total_mem = rt.totalMemory();
		long free_mem =  rt.freeMemory();
		long used_mem = total_mem - free_mem;
		String mem = String.format("%.2fMb", ((double)used_mem/1000000.0));
		
		String str = Utilities.getDurationString(getTime() - startTime);
		return String.format("%-50s %-8s %10s", title, mem, str);
	}
	public static String getMemUsage() { // CAS546 add
		Runtime rt = Runtime.getRuntime();
		long total_mem = rt.totalMemory();
		long free_mem =  rt.freeMemory();
		long used_mem = total_mem - free_mem;
		String mem = String.format("%.2fMb", ((double)used_mem/1000000.0));
		
		return String.format("Memory %s\n\n", mem);
	}
	/************** assorted others  **/
	public static void prt(ProgressDialog prog, String msg) { // CAS520 add
		prog.msgToFile(msg);
	}
	public static void prtIndentMsgFile(ProgressDialog prog, int indent, String msg) {
		String x = "";
		for (int i=0; i<indent; i++) x +="   ";
		prog.msgToFile(x + msg);
	}
	public static void prtNumMsgFile(ProgressDialog prog, int num, String msg) {
		prog.msgToFile(String.format("%,10d %s         ", num, msg));
	}
	
	public static void prtNumMsg(ProgressDialog prog, int num, String msg) {
		prog.appendText(String.format("%,10d %s           ", num, msg));
	}
	
	public static void prtNumMsgNZx(int num, String msg) {// indent
		if (num>0) System.out.format("      %,10d %s              \n", num, msg);
	}

	public static void die(String msg) {
		System.err.println("Fatal error: " + msg);
		System.exit(-1);
	}
	/***************************************************************************/
	public static String fileFromPath(String path) {
		int i = path.lastIndexOf("/");
		if (i<=0) return path;
		return path.substring(i+1);
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
	    	ErrorReport.print(e, "Cannot open file " + file); // CAS557 add e
	    }
		return null;
	}
	// The prefix must be present. if no prefix, just use the given name.
	public static String parseGrpName(String name, String prefix){
		name = name + " "; // hack, otherwise we need two cases in the regex
		
		String regx = ">\\s*(" + prefix + ")(\\w+)\\s?.*";
		Pattern pat = Pattern.compile(regx,Pattern.CASE_INSENSITIVE);
		Matcher m = pat.matcher(name);
		if (m.matches()) return m.group(2);
		
		return parseGrpFullName(name);
	}
	public static String parseGrpFullName(String in){
		String regx = ">\\s*(\\w+)\\s?.*";
		Pattern pat = Pattern.compile(regx,Pattern.CASE_INSENSITIVE);
		Matcher m = pat.matcher(in);
		if (m.matches()) return m.group(1);
		
		return null;
	}
	/********************************************************
	 * DONE 
	 */
	public static boolean checkDoneFile(String dir)  {
		File d = new File(dir);
		if (!d.exists() || d.isFile()) return false;
		
		File f = new File(d,"all.done");

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
	/****************************************************************************/
	
	/**********************************************************************/
	/**********************************************************/
	public static int[] strArrayToInt(String[] sa) {
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
	public static String intArrayToBlockStr(int[] ia) {
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
	
	public static String strVectorJoin(java.util.Vector<String> sa, String delim) {
		String out = "";
		for (int i = 0; i < sa.size(); i++) {
			out += sa.get(i);
			if (i < sa.size() -1)
				out += delim;
		}
		return out;
	}
	
	public static boolean intervalsTouch(int s1,int e1, int s2, int e2) {
		return intervalsOverlap(s1,e1,s2,e2,0);
	}	
	
	static public boolean intervalsOverlap(int s1,int e1, int s2, int e2, int max_gap) {
		int gap = Math.max(s1,s2) - Math.min(e1,e2);
		return (gap <= max_gap);
	}
	
	// Returns the amount of the overlap (negative for gap)
	public static int intervalsOverlap(int s1,int e1, int s2, int e2) {
		int gap = Math.max(s1,s2) - Math.min(e1,e2);
		return -gap;
	}	
	public static boolean intervalContained(int s1,int e1, int s2, int e2){
		return ( (s1 >= s2 && e1 <= e2) || (s2 >= s1 && e2 <= e1));
	}
	
	public static float simpleRatio(int top, int bot) {
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
	public static int getPairIdx(int proj1Idx, int proj2Idx, DBconn2 dbc2) throws Exception {
		int idx = 0;
		
		String st = "SELECT idx FROM pairs WHERE proj1_idx='" + proj1Idx + "' AND proj2_idx='" + proj2Idx +"'";
		ResultSet rs = dbc2.executeQuery(st);
		if (rs.next())
			idx = rs.getInt("idx");
		rs.close();
		
		return idx;
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
	public static class ObjCmp implements Comparator<Comparable> {
		public int compare(Comparable a, Comparable b) {
			if (a == null){
				if (b == null) return 0;
				else return +1;
			}
			else if (b == null) {
				return -1;
			}
			else return a.compareTo(b);
		}
	}
	
	public static String reverseComplement(String in){
		in = (new StringBuffer(in)).reverse().toString().toUpperCase();
		in = in.replace('A', 't');
		in = in.replace('G', 'c');
		in = in.replace('C', 'g');
		in = in.replace('T', 'a');
		return in.toLowerCase();
	}
}