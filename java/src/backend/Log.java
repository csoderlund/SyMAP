package backend;

/********************************************
 * used by backend.ProgSpec.doAlignment() only writes to file symap.log in alignment specific directory
 * see ProgressDialog for writing to LOAD.log and symap.log
 * see util.ErrorReport to writing to error.log
 */
import java.io.FileWriter;
import java.io.IOException;

import util.ErrorReport;
import util.Logger;

public class Log implements Logger
{
	public FileWriter logFile = null;
	
	public Log(FileWriter fw) {
		logFile = fw;
	}
	
	public Log(String filePath) throws IOException
	{
		this( new FileWriter(filePath,false) );
	}
	
	public void msg(String s)
	{
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
	public void msgToFile(String s)
	{
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
	public void write(char c)
	{
		System.out.print(c);
	}
}
