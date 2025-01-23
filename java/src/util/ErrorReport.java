package util;

/********************************************
 * CAS500 added so printStackTrace goes to file; does not need to be opened by caller
 * See util.ProgressDialog for write to logs/LOAD.log and progress window
 * See backend.Log for write to alignment specific symap.log file
 */
import java.io.IOException;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import database.DBconn2;
import symap.Globals;

public class ErrorReport {
	static final String strFileName = "error.log";
	
	public static void print(Throwable e, String debugInfo) {
		reportError(e, "Error: " + debugInfo, false);
	}
	public static void print(String debugInfo) {
		reportError(null, "Error: " + debugInfo, false);
	}
	
	public static void print(String msg, String debugInfo) {// CAS505
		msg = "Error: " + msg;
			
		System.err.println(msg);
		try {
			PrintWriter pWriter = new PrintWriter(new FileWriter(strFileName, true));
			pWriter.println("\n" + getDate()); 
			pWriter.println(msg);
			pWriter.println(debugInfo);
			pWriter.close();
			System.err.println("See " + strFileName);
			
		} catch (IOException e1) {
			System.err.println("DebugInfo: " + debugInfo);
			System.err.println("An error has occurred, however SyMAP was unable to create an error log file");
			return;
		}
	}
	
	public static void die(Throwable e, String debugInfo) {
		DBconn2.shutConns(); // CAS541 
		reportError(e,    "Fatal Error " + Globals.VERDATE + "\n   " +  debugInfo, false); // CAS560 add version to log file
		System.exit(-1);
	}
	public static void die(String debugInfo) {
		DBconn2.shutConns(); // CAS541 
		reportError(null, "Fatal Error " + Globals.VERDATE + "\n   " +  debugInfo, false);
		System.exit(-1);
	}
	public static void reportError(Throwable e, String msg, boolean replaceContents) {
		System.err.println(msg);
		if (e!=null && e.getMessage()!=null) { // CAS512 add 2nd check
			String [] lines = e.getMessage().split("\n"); // CAS511 can include nested stacktrace
			if (lines.length>5) {
				System.err.println(lines[0]);
				for (String l : lines) {
					if (l.startsWith("MESSAGE")) {
						System.err.println(l);
						break;
					}
				}
			}
			else {
				System.err.println(e.getMessage());
			}
		}
		
		PrintWriter pWriter = null;
		try {
			if(replaceContents) {
				pWriter = new PrintWriter(new FileWriter(strFileName));
			}
			else {
				pWriter = new PrintWriter(new FileWriter(strFileName, true));
			}
		} catch (IOException e1) {
			System.err.println("An error has occurred, however SyMAP was unable to create an error log file");
			return;
		}
		
		pWriter.println("\n " + Globals.VERDATE + " run on " + getDate()); // CAS560 add verdate
		pWriter.println(msg + "\n");
		if (e != null)  {
			e.printStackTrace(pWriter);
			System.err.println("See " + strFileName);
		}
		pWriter.close();
	}
	
	static public String getDate ( )
    {
        Date date=new Date();
        SimpleDateFormat sdf=new SimpleDateFormat("dd-MMM-yy HH:mm:ss"); 
        return sdf.format(date);
    }
	static public void prtMem() {
		long free = Runtime.getRuntime().freeMemory();
		long total = Runtime.getRuntime().totalMemory();
		long max = Runtime.getRuntime().maxMemory();
		System.out.format("Memory: free %,d  total %,d  max %,d\n", free, total, max);
    }
}
