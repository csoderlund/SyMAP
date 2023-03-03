package backend;

/************************************* 
 * Holds information for a given aligment
 * Executes the alignment program
 */
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;

import util.ErrorReport;
import util.Utilities;

public class ProgSpec implements Comparable<ProgSpec> 
{
	public int alignNum;
	public String program, args;
	
	private File f1, f2;
	private String resDir, alignLogDirName;
	private String outRoot, outFile;
	private long startTime;	
	
	public static final int STATUS_UKNOWN  = 0;
	public static final int STATUS_QUEUED  = 1;
	public static final int STATUS_RUNNING = 2;
	public static final int STATUS_ERROR   = 3;
	public static final int STATUS_DONE    = 4;
	
	private Process process;
	private int status, nExitValue = -1;
	
	public ProgSpec(String program, String platpath, String args, 
			File f1, File f2, String resdir, String alignLogDirName)
	{
		this.program = program; 
		this.args = args;
		this.f1 = f1;
		this.f2 = f2;
		this.resDir = resdir;
		
		this.outRoot =  Constants.getNameAlignFile(f1.getName(), f2.getName());
		
		this.outFile = resDir + outRoot + Constants.mumSuffix;
		this.alignLogDirName = alignLogDirName;
		
		if (outputFileExists())	this.status = STATUS_DONE;
		else					this.status = STATUS_QUEUED;
	}
	
	public synchronized int     getStatus() { return status; }
	public synchronized void    setStatus(int n) { status = n; }
	public synchronized boolean isQueued() { return status == STATUS_QUEUED; }
	public synchronized boolean isRunning() { return status == STATUS_RUNNING; }
	public synchronized boolean isError() { return status == STATUS_ERROR; }
	public synchronized boolean isDone() { return status == STATUS_DONE; }
	
	public File getQueryFile() {
		return f1;	
	}
	private boolean outputFileExists() {
		File f1 = new File(outFile + Constants.doneSuffix);
		if (f1 != null && f1.exists()) {
			File f2 = new File(outFile);
			if (f2 != null && f2.exists())
				return true;
		}
		return false;
	}
	
	public String getOutputFilename() {
		return outFile;
	}
	
	public String getDescription() {
		return f1.getName() + " to " + f2.getName();
	}
	
	public String toString() {
		String s = Utilities.pad( getDescription(), 40 ) + "   ";
		if (isError()) 			s += "Error occurred (" + nExitValue + ")";
		else if (isRunning()) 	s += Utilities.getDurationString( getRunTime() );
		else if (isDone())		s += "Finished";
		else if (isQueued())	s += "Queued";
		else					s += "Unknown";		
		return s;
	}
	
	public long getRunTime() {
		return System.currentTimeMillis() - startTime;
	}
	
	public boolean doAlignment()  { // CAS508 remove throw Exception
		int rc = -1;
		String cmd="";
		
		try {
			startTime = System.currentTimeMillis();
			setStatus(STATUS_RUNNING);
	
			String doneFile = outFile + Constants.doneSuffix;
			Utilities.deleteFile(doneFile);
			
			String query = f1.getPath(); // CAS500 getAbsolutePath
			String targ  = f2.getPath();
			
			// CAS500 move log file to log directory
			String runLogName  = alignLogDirName + outRoot + ".log";
			FileWriter runFW = new FileWriter(runLogName);
			Log runLog = new Log(runFW);  
			
			if (program.equals("promer") || program.equals("nucmer"))//(type == ProgType.mummer)
			{
				String intFilePath = resDir + outRoot + "." + program;
				String deltaFilePath = intFilePath + ".delta";
				
				if (Constants.isMummerPath()) { // CAS508 path is set in symap.config
					query = f1.getAbsolutePath();
					targ =  f2.getAbsolutePath();
					intFilePath =   new File(intFilePath).getAbsolutePath();
					deltaFilePath = new File(deltaFilePath).getAbsolutePath();
				}
				
				String mummerPath = Constants.getProgramPath("mummer"); 
				if (!mummerPath.equals("") && !mummerPath.endsWith("/")) mummerPath += "/"; 
				
				// Run promer or nucmer
				cmd = mummerPath + program + " " + args + " -p " + intFilePath + " " + targ + " " + query;
				rc = runCommand(cmd, runFW, runLog); // send output and log messages to same file
				
				// Run show-coords
				if (rc == 0) { // CAS501 nucmer rc=1
					//CAS515 String parms = (program.equals("promer") ? "-dlkTH" : "-dlTH"); // -k to remove secondary frame hits
					String parms = (program.equals("promer") ? "-dlkT" : "-dlT"); // -k to remove secondary frame hits
					cmd = mummerPath + "show-coords " + parms +  " " + deltaFilePath;
					rc = runCommand(cmd, new FileWriter(outFile), runLog);
					runLog.msg( "#" + alignNum + " done: " + Utilities.getDurationString(getRunTime()) );
				}
				else runLog.msg( "#" + alignNum + " fail: " + Utilities.getDurationString(getRunTime()) );
				
			}
			else System.err.println("SyMAP error: unsupported program");
			
			if (rc == 0) {
				cleanup(); // Cleanup intermediate files. CAS500 only if successful
				Utilities.checkCreateFile(doneFile, "PS done");
				setStatus(STATUS_DONE);
			}
			else {
				ErrorReport.print("Failed command: " + cmd);
				setStatus(STATUS_ERROR);
			}
			runFW.close();
			
		} catch (Exception e) {
			ErrorReport.print(e, "Running command: " + cmd);
			setStatus(STATUS_ERROR);
		}
		return (rc == 0); 
	}
	
	private void cleanup() {
		String intFilePath = resDir + outRoot + "." + program;
		Utilities.deleteFile(intFilePath + ".delta");
		Utilities.deleteFile(intFilePath + ".cluster");
		Utilities.deleteFile(intFilePath + ".mgaps");
		Utilities.deleteFile(intFilePath + ".aaqry"); // promer only
		Utilities.deleteFile(intFilePath + ".aaref"); // promer only
		Utilities.deleteFile(intFilePath + ".ntref"); // nucmer only
	}
	
	public void interrupt() {
		setStatus(ProgSpec.STATUS_ERROR);
		process.destroy(); // terminate process
		try { 
			process.waitFor(); // wait for process to terminate
		} 
		catch (Exception e) { ErrorReport.print(e, "Interrupt process"); }
		cleanup();
	}
	
    public int runCommand ( String strCommand, Writer runFW, Log runLog ) {// runFW and runLog go to same file
    	try {
	        boolean bDone = false;
	        System.err.println("#" + alignNum + " " + strCommand);
	        
	        if (runLog != null)
		        runLog.msgToFile("#" + alignNum + " " + strCommand); // log file for this run
	        
	        // Execute command
	        process = Runtime.getRuntime().exec( strCommand );
	        
	        // Capture stdout and stderr
	        InputStream    stdout = process.getInputStream();       
	        BufferedReader brOut = new BufferedReader( new InputStreamReader(stdout) );
	        InputStream    stderr = process.getErrorStream();       
	        BufferedReader brErr = new BufferedReader( new InputStreamReader(stderr) );
	        try {
	            synchronized ( stdout ) {
	                while ( !bDone || brErr.ready() || brOut.ready()) {
	                    // Send the sub-processes stderr to out stderr or log
	                    while (brErr.ready()) {
		                    if (runFW != null) 			runFW.write(brErr.read());
		                    else if (runLog != null)	runLog.write((char)brErr.read());
		                    else						System.err.print((char)brErr.read());
	                    }
	                    
	                    // Consume stdout so the process doesn't hang...
	                    while (brOut.ready()) {
		                    if (runFW != null)	runFW.write(brOut.read());
		                    else if (runLog != null)	runLog.write((char)brOut.read());
		                    else					System.out.print((char)brOut.read());
	                    } 
	
	                    if ( runFW != null )runFW.flush();
	                 
	                	stdout.wait( 1000 /*milliseconds*/ ); // sleep if nothing to do
	                    
	                    try {
	                    	nExitValue = process.exitValue(); // throws exception if process not done
	                        bDone = true;
	                    }
	                    catch ( Exception err ) { }
	                }
	            }
	        }
	        catch ( ClosedByInterruptException ignore ) { }
	        catch ( InterruptedException ignore ) { }
        
	        if (nExitValue != 0) 
	        	runLog.msg( "Alignment program error code: " + nExitValue );
	        
	        // Kill the process if it's still running (exception occurred)
	        if ( !bDone ) {
	        	interrupt();
	        	runLog.msg( "Interrupted #" + alignNum + ": " + Utilities.getDurationString(getRunTime()) );
	        }
	        
	        // Clean up
	        if (runFW != null) runFW.flush();
        
	        try { 
	        	// Prevent "too many open files" error
	        	process.getOutputStream().close();
	        	process.getInputStream().close();
	        	process.getErrorStream().close();
	        	process.destroy();
	        	process = null;
	        }
	        catch (IOException e) { }
    	} catch (Exception e) {ErrorReport.print(e, "Run command"); }
    	
        return nExitValue;  
    }
    
    // "Comparable" interface for sorting
    public int compareTo(ProgSpec p) {
		if (p != null)
			return p.getDescription().compareTo( getDescription() );
		return -1;
    }
}
