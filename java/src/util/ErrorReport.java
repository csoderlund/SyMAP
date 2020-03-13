package util;

/********************************************
 * CAS500 added so printStackTrace goes to file
 */
import java.io.IOException;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ErrorReport {
	static final String strFileName = "error.log";
	static boolean isApplet=false;
	
	public static void isApplet() { isApplet=true; }; // CAS503
	
	public static void print(Throwable e, String debugInfo) {
		reportError(e, "Error: " + debugInfo, false);
	}
	public static void print(String debugInfo) {
		reportError(null, "Error: " + debugInfo, false);
	}
	public static void die(Throwable e, String debugInfo) {
		reportError(e, "Fatal Error: " + debugInfo, false);
		System.exit(-1);
	}
	public static void die(String debugInfo) {
		reportError(null, "Fatal Error: " + debugInfo, false);
		System.exit(-1);
	}
	public static void reportError(Throwable e, String debugInfo, boolean replaceContents) {
		System.err.println(debugInfo);
		
		if (isApplet) { // CAS503
			System.err.println(e);
			return;
		}
		else {
			System.err.println(e.getMessage());
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
		
		pWriter.println("\n" + getDate()); 
		
		if(debugInfo != null) pWriter.println(debugInfo + "\n");
		
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
}
