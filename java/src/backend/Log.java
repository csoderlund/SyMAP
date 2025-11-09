package backend;

/********************************************
 * see ProgressDialog for writing to LOAD.log and symap.log
 */
import java.io.FileWriter;
import java.io.IOException;

import util.ErrorReport;

public class Log  {
	public FileWriter logFile = null;
	
	public Log(FileWriter fw) { // backend.AlignRun, backend.Log
		logFile = fw;
	}
	
	public Log(String filePath) throws IOException {
		this( new FileWriter(filePath,false) );
	}
	
	public void msg(String s) {
		System.out.println(s);
		
		if (logFile != null) {
			try {
				logFile.write(s + "\n");		
				logFile.flush();
			}
			catch(Exception e) {
				ErrorReport.print(e, "Cannot write to log file");
			}		
		}
	}
	public void msgToFile(String s) {
		if (logFile != null) {
			try {
				logFile.write(s + "\n");		
				logFile.flush();
			}
			catch(Exception e) {
				ErrorReport.print(e, "Cannot write to log file");
			}		
		}
	}	
	public void write(char c) {
		System.out.print(c);
	}
}
