package backend;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;

import symap.Globals;
import util.ErrorReport;
import util.FileDir;
import util.ProgressDialog;
import util.Utilities;

/********************************************************
 * Utils for A&S
 * msgToFile          prints to file, terminal, but not ProgressDialog
 * msg and appendText prints to file, terminal, ProgressDialog
 */
public class Utils {
	public static void sleep(int milliseconds) {
 		try{ Thread.sleep(milliseconds); }
 		catch (InterruptedException e) { }
	}
	// time 
	public static long getTime() { return System.currentTimeMillis();}
	
	public static String getElapsedTime(long startTime) {
		return Utilities.getDurationString(getTime()-startTime);
	}
	public static void prtMsgTimeFile(ProgressDialog prog, String msg, long startTime) {
		String t = Utilities.getDurationString(getTime()-startTime);
		prog.msgLogOut(String.format("%-30s %s", msg, t));	
	}
	public static void prtMsgTime(ProgressDialog prog, String msg, long startTime) {
		String t = Utilities.getDurationString(getTime()-startTime);
		prog.msg(String.format("%-30s %s", msg, t));	
	}
	public static void prtMsgTimeDone(ProgressDialog prog, String msg, long startTime) {
		String t = Utilities.getDurationString(getTime()-startTime);
		String m = String.format("%-35s  %s                           \n\n", msg, t);
		
		if (prog!=null) prog.msg(m);	
		else System.out.println(m);
	}
	
	// Memory and time
	public static long getTimeMem() { System.gc(); return System.currentTimeMillis();}
	
	public static void prtTimeMemUsage(ProgressDialog prog, String title, long startTime) {
		prog.msgLogOut(getTimeMemUsage(title, startTime) + "\n\n");
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
	public static String getMemUsage() { 
		Runtime rt = Runtime.getRuntime();
		long total_mem = rt.totalMemory();
		long free_mem =  rt.freeMemory();
		long used_mem = total_mem - free_mem;
		String mem = String.format("%.2fMb", ((double)used_mem/1000000.0));
		
		return String.format("Memory %s\n\n", mem);
	}
	// no time
	// CAS589 added so will go to file, terminal, progress -- this is all a mess
	public static void prtIndentMsg(ProgressDialog prog, int indent, String msg) {
		String x = "";
		for (int i=0; i<indent; i++) x +="   ";
		prog.msgPopLogOut(x + msg);
	}
	public static void prtMsgFile(ProgressDialog prog, String msg) { 
		prog.msgLogOut(msg);
	}
	public static void prtIndentMsgFile(ProgressDialog prog, int indent, String msg) {
		String x = "";
		for (int i=0; i<indent; i++) x +="   ";
		prog.msgLogOut(x + msg);
	}
	public static void prtNumMsgFile(ProgressDialog prog, int num, String msg) {
		prog.msgLogOut(String.format("%,10d %s         ", num, msg));
	}
	public static void prtIndentNumMsgFile(ProgressDialog prog, int indent, int num, String msg) {
		String x = "";
		for (int i=0; i<indent; i++) x +="   ";
		prog.msgLogOut(String.format("%s%,10d %s           ", x, num, msg));
	}
	public static void prtNumMsg(ProgressDialog prog, int num, String msg) {
		prog.msgPopLogOut(String.format("%,10d %s           ", num, msg));
	}
	public static void prtNumMsgNZx(ProgressDialog prog, int num, String msg) { 
		if (num>0) prtNumMsg(prog, num, msg);
	}
	////////////////////////////////////////////////////////////////////////////
	public static void prtToFileHeader(FileWriter fw, String msg) { // CAS589 added
		try {
			String x =  String.format("%-40s  %s", msg, ErrorReport.getDate());
			Globals.prt(x);
			fw.append(x+"\n");
		}
		catch (Exception e) {ErrorReport.print(e, "Write to file");}
	}
	public static void prtToFileNano(FileWriter fw, String msg, long time) {
		try {
			String x =  String.format("%-40s  %s", msg, Utilities.getNanoTimeStr(time));
			Globals.prt(x);
			fw.append(x+"\n");
		}
		catch (Exception e) {ErrorReport.print(e, "Write to file");}
	}
	public static void prtToFile(FileWriter fw, String msg) {
		try {
			Globals.prt(msg);
			fw.append(msg+"\n");
		}
		catch (Exception e) {ErrorReport.print(e, "Write to file");}
	}
	public static void prtNumMsg(int num, String msg) {
		Globals.prt(String.format("%,10d %s           ", num, msg));
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
	    	ErrorReport.print(e, "Cannot open file " + file); 
	    }
		return null;
	}
	
	/********************************************************
	 * DONE 
	 */
	public static boolean checkDoneBlast(String dir)  {// CAS589 for Load Blast
		File d = new File(dir);
		if (!d.exists() || d.isFile()) return false;
		
		ArrayList <String> files = FileDir.getFilesFromDir(dir);
		if (files == null || files.size()==0) return false;
		return true;
	}
	public static boolean checkDoneMUMmer(String dir)  {
		File d = new File(dir);
		if (!d.exists() || d.isFile()) return false;
		
		File f = new File(d,"all" + Constants.doneSuffix);

		if (f.exists()) return true;
		
		return false;
	}
	public static int checkDoneMaybe(String dir) { // no all.done file
		File d = new File(dir);
		if (!d.exists() || d.isFile()) return 0;
		
		int numFiles=0;
		for (File x : d.listFiles()) {
			if (!x.isFile()) continue;
			if (x.isHidden()) continue;
			
			String path = x.getName();
			String name = path.substring(path.lastIndexOf("/")+1, path.length());
			if (name.endsWith(Constants.doneSuffix)) continue; // do not care for Manager; AlignMain further checks
	
			if (name.endsWith(Constants.mumSuffix)) numFiles++;			
		}
		return numFiles;
	}
	public static void writeDoneFile(String dir)  {
		try {
			File f = new File(dir);
			File d = new File(f,"all" + Constants.doneSuffix);
			d.createNewFile();
		}
		catch (Exception e) {ErrorReport.print(e, "Cannot write done file to " + dir);}
	}
	// Get .tab file in resultDir/blast; CAS589 add; CAS590 change to read all files
	public static String getBlastFileList(String dir) {
	try {
		String blastDir = dir + Constants.blastDir;
		ArrayList <String> files = util.FileDir.getFilesFromDir(blastDir);
		if (files==null || files.size()==0) return null;
		
		String fileList=null;
		for (String f : files) {
			String name = f;
			if (name.contains("/")) name = name.substring(name.lastIndexOf("/"));
			
			if (fileList==null) fileList = name;
			else fileList += ", " + name;
		}
		return fileList;
	}
	catch (Exception e) {ErrorReport.print(e, "Cannot access " + dir); return null;}
	}
	public static ArrayList <String >getBlastFiles(String dir) {
		try {
			String blastDir = dir + Constants.blastDir;
			ArrayList <String> files = util.FileDir.getFilesFromDir(blastDir);
			return files;
		}
		catch (Exception e) {ErrorReport.print(e, "Cannot access " + dir); return null;}
	}
	
	public static String getDateStr(long l) {
		DateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy hh:mm a");
		return sdf.format(l);
	}
	// Pre-v569 did not have ".txt" suffix; this checks and renames
	public static File getParamsFile(String dir, String fileName) {
	try {
		if (!util.FileDir.dirExists(dir)) return null; 
		
		File pfile = new File(dir,fileName); 
		if (pfile.isFile()) return pfile;	// exists with new name
		
		String oldName = fileName.replace(".txt", ""); // old name
		File ofile = new File(dir,oldName);
		if (!ofile.isFile()) return null;				// no new or old name

		boolean b = ofile.renameTo(pfile);
		if (!b) return null; 
		
		return pfile;
	}
	catch (Exception e) {ErrorReport.print(e, "Could not open " + dir +"/" + fileName); return null;}
	}
	/****************************************************************************/
	
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
}