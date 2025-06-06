package backend;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;

import symap.Ext;		// all external programs stuff in Ext; CAS566
import util.ErrorReport;
import util.Utilities;

/************************************* 
 * Executes the alignment program for one query_to_target where either may have >1 chromosomes/scaffold
 * v560 renamed from ProgSpec
 */
public class AlignRun implements Comparable<AlignRun> 
{
	protected static final int STATUS_UKNOWN  = 0;
	protected static final int STATUS_QUEUED  = 1;
	protected static final int STATUS_RUNNING = 2;
	protected static final int STATUS_ERROR   = 3;
	protected static final int STATUS_DONE    = 4;
	
	protected int alignNum;
	protected String program, args;
	
	private File f1, f2;
	private String resDir, alignLogDirName;
	private String outRoot, outFile;
	private long startTime;	

	private Process process;
	private int status, nExitValue = -1;
	
	protected AlignRun(String program, String args, 
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
	
	protected synchronized void    setStatus(int n) {status = n;}
	protected synchronized int     getStatus() 	{return status;}
	protected synchronized boolean isQueued() 	{return status == STATUS_QUEUED;}
	protected synchronized boolean isRunning() 	{return status == STATUS_RUNNING;}
	protected synchronized boolean isError() 	{return status == STATUS_ERROR;}
	protected synchronized boolean isDone() 	{return status == STATUS_DONE;}
	
	protected String toStatus() {
		String s = Utilities.pad( getDescription(), 40 ) + "   ";
		if (isError()) 			s += "Error occurred (" + nExitValue + ")";
		else if (isRunning()) 	s += Utilities.getDurationString( getRunTime() );
		else if (isDone())		s += "Finished";
		else if (isQueued())	s += "Queued";
		else					s += "Unknown";		
		return s;
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
	protected String getDescription() {
		return f1.getName() + " to " + f2.getName();
	}
	
	protected long getRunTime() {
		return System.currentTimeMillis() - startTime;
	}
	
	protected boolean doAlignment()  { 
		int rc = -1;
		String cmd="";
		
		try {
			startTime = System.currentTimeMillis();
			setStatus(STATUS_RUNNING);
	
			String doneFile = outFile + Constants.doneSuffix;
			Utilities.deleteFile(doneFile);
			
			String query = f1.getPath(); 
			String targ  = f2.getPath();
				
			String runLogName  = alignLogDirName + outRoot + ".log"; 
			FileWriter runFW = new FileWriter(runLogName);
			Log runLog = new Log(runFW);  
			
			// Make command
			String intFilePath = resDir + outRoot + "." + program;
			String deltaFilePath = intFilePath + ".delta";
			
			if (Ext.isMummer4Path()) { 
				query = f1.getAbsolutePath();
				targ =  f2.getAbsolutePath();
				intFilePath =   new File(intFilePath).getAbsolutePath();
				deltaFilePath = new File(deltaFilePath).getAbsolutePath();
			}
			
			String mummerPath = Ext.getMummerPath(); 
			if (!mummerPath.equals("") && !mummerPath.endsWith("/")) mummerPath += "/"; 
			
			// Run promer or nucmer
			cmd = mummerPath + program + " " + args + " -p " + intFilePath + " " + targ + " " + query;
			rc = runCommand(cmd, runFW, runLog); // send output and log messages to same file
			
			// Run show-coords // nucmer rc=1
			if (rc == 0) { 
				String parms = (program.equals(Ext.exPromer) ? "-dlkT" : "-dlT"); // -k to remove secondary frame hits
				cmd = mummerPath + Ext.exCoords + " " + parms +  " " + deltaFilePath;
				rc = runCommand(cmd, new FileWriter(outFile), runLog);
				runLog.msg( "#" + alignNum + " done: " + Utilities.getDurationString(getRunTime()) );
			}
			else runLog.msg( "#" + alignNum + " fail: " + Utilities.getDurationString(getRunTime()) );
		
			if (rc == 0) {
				cleanup(); // Cleanup intermediate files. 
				Utilities.checkCreateFile(doneFile, "PS done");
				setStatus(STATUS_DONE);
			}
			else {
				ErrorReport.print("#" + alignNum + " rc" + rc + " Failed: " + cmd); 
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
		if (Constants.MUM_NO_RM) {// command line -mum; user to keep all args
			System.out.println("For mummer files, see " + resDir);
			return; 
		}
		Utilities.deleteFile(intFilePath + ".delta"); // appears to be the only one MUMmer keeps if successfully completes
		Utilities.deleteFile(intFilePath + ".cluster");
		Utilities.deleteFile(intFilePath + ".mgaps");
		Utilities.deleteFile(intFilePath + ".aaqry"); // promer only
		Utilities.deleteFile(intFilePath + ".aaref"); // promer only
		Utilities.deleteFile(intFilePath + ".ntref"); // nucmer only
	}
	
	protected void interrupt() {
		setStatus(AlignRun.STATUS_ERROR);
		process.destroy(); // terminate process
		try { 
			process.waitFor(); // wait for process to terminate
		} 
		catch (Exception e) { ErrorReport.print(e, "Interrupt process"); }
		cleanup();
	}
	
    protected int runCommand (String strCommand, Writer runFW, Log runLog) {// runFW and runLog go to same file
    	try {
	        boolean bDone = false;
	        System.err.println("#" + alignNum + " " + strCommand);
	        
	        if (runLog != null)
		        runLog.msgToFile("#" + alignNum + " " + strCommand); // log file for this run
	        
	        // Execute command
	        String [] x = strCommand.split("\\s+");
	        process = Runtime.getRuntime().exec(x); // CAS547 single string depreciated
	        
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
    
    public int compareTo(AlignRun p) {// goes into queue sorted ascending order
		if (p != null)
			return getDescription().compareTo(p.getDescription() );
		return -1;
    }
}
