package backend;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;

import util.Logger;
import util.Utilities;

enum ProgType {blat,mummer};

public class ProgSpec implements Comparable<ProgSpec> 
{
	private ProgType type;
	public String program;
	private String args;
	private FileType fType1, fType2;
	private File f1, f2;
	private String resDir;
	private String outRoot;
	private String outFile;
	private String platPath;
	private long startTime;	
	public int alignNum;
	
	public static final int STATUS_UKNOWN  = 0;
	public static final int STATUS_QUEUED  = 1;
	public static final int STATUS_RUNNING = 2;
	public static final int STATUS_ERROR   = 3;
	public static final int STATUS_DONE    = 4;
	private int status;
	
	private Process process;
	private int nExitValue = -1;
	
	public ProgSpec(ProgType type, String program, String platpath, String args, 
			FileType t1, FileType t2, File f1, File f2, String resdir)
	{
		this.program = program;
		this.platPath = platpath;
		if (program.equals("blat"))
			this.type = ProgType.blat;
		else if (program.equals("promer") || program.equals("nucmer"))
			this.type = ProgType.mummer;
		else {
			this.type = type;
			if (program == null || program.length() == 0) {
				if (type == ProgType.blat)
					this.program = "blat";
				else if (type == ProgType.mummer)
					this.program = "promer";
			}
		}
		
		this.args = args;
		this.fType1 = t1;
		this.fType2 = t2;
		this.f1 = f1;
		this.f2 = f2;
		this.resDir = resdir;
		this.outRoot =  f1.getName() + "." + f2.getName();
		this.outFile = resDir + "/" + outRoot + "." + (this.type == ProgType.blat ? "blat" : "mum");
		
		if (outputFileExists())
			this.status = STATUS_DONE;
		else
			this.status = STATUS_QUEUED;
	}
	
	public synchronized int getStatus() { return status; }
	public synchronized void setStatus(int n) { status = n; }
	public synchronized boolean isQueued() { return status == STATUS_QUEUED; }
	public synchronized boolean isRunning() { return status == STATUS_RUNNING; }
	public synchronized boolean isError() { return status == STATUS_ERROR; }
	public synchronized boolean isDone() { return status == STATUS_DONE; }
	
	
	private String getProgramPath(String program) {
		// Determine platform (linux, windows, mac) and get path to packaged
		// platform-specific executables.
		File f = new File("ext/" + program );
		if (!f.isDirectory())
		{
			System.err.println("Unable to find the executables directory " + f.getAbsolutePath());
			return "";
		}
		File g = new File(f,platPath);
		if (!g.isDirectory())
		{
			System.err.println("Unable to find the executables directory " + g.getAbsolutePath());
			return "";			
		}
		return g.getAbsolutePath();
	}
	public File getQueryFile()
	{
		return f1;	
	}
	private boolean outputFileExists() {
		File f1 = new File(outFile + ".done");
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
	
	public String toString()
	{
		String s = Utilities.pad( getDescription(), 40 ) + "   ";
		if (isError())
			s += "Error occurred (" + nExitValue + ")";
		else if (isRunning()) 
			s += Utilities.getDurationString( getRunTime() );
		else if (isDone())
			s += "Finished";
		else if (isQueued())
			s += "Queued";
		else
			s += "Unknown";
					
		return s;
	}
	
	public long getRunTime() {
		return System.currentTimeMillis() - startTime;
	}
	
	public boolean doAlignment() throws Exception
	{
		startTime = System.currentTimeMillis();
		setStatus(STATUS_RUNNING);

		// Remove .done and .log files
		String doneFile = outFile + ".done";
		String logFile  = outFile + ".log";
		Utilities.deleteFile(logFile);
		Utilities.deleteFile(doneFile);
		
		String query = f1.getAbsolutePath();
		String targ  = f2.getAbsolutePath();
		FileWriter flog = new FileWriter(logFile);
		int rc = -1;
		
		if (program.equals("blat"))//if (type == ProgType.blat)
		{
			// If we're doing pseudo to gene, we have to reverse the order
			if (fType1 == FileType.Pseudo)
			{
				assert(fType2 == FileType.Gene);
				String temp = query;
				query = targ;
				targ = temp;
			}
			
			// Run blat
			String blatPath = getProgramPath("blat"); 
			if (!blatPath.equals("")) blatPath += "/"; 
			String cmd = blatPath + program + " " + targ + " " + query + " " + args + " " + outFile;
			rc = runCommand(cmd, flog, new Log(flog)); // send output and log messages to same file
		}
		else if (program.equals("promer") || program.equals("nucmer"))//(type == ProgType.mummer)
		{
			String intFilePath = resDir + "/" + outRoot + "." + program;
			String deltaFilePath = intFilePath + ".delta";
			String mummerPath = getProgramPath("mummer"); 
			
			// Run promer
			if (!mummerPath.equals("")) mummerPath += "/"; 
			String cmd = mummerPath + program + " " + args + " -p " + intFilePath + " " + targ + " " + query;
			rc = runCommand(cmd, flog, new Log(flog)); // send output and log messages to same file
			
			// Run show-coords
			if (rc == 0) 
			{
				String parms = (program.equals("promer") ? "-dlkTH" : "-dlTH");
				cmd = mummerPath + "show-coords " + parms +  // WN added -k to remove secondary frame hits
				" " + deltaFilePath;
				rc = runCommand(cmd, new FileWriter(outFile), null);
			}
		}
		else
			throw( new Exception("Unsupported alignment type '" + program + "'") );
		
		// Cleanup intermediate files.
		cleanup();
		
		if (rc == 0) {
			// Create .done file to signal completion.
			Utilities.checkCreateFile(doneFile);
			setStatus(STATUS_DONE);
		}
		else {
			setStatus(STATUS_ERROR);
		}
		
		flog.close();
		
		return (rc == 0);
	}
	
	private void cleanup() {
		if (type == ProgType.blat) {
			// Nothing to remove for blat
		}
		else if (type == ProgType.mummer) { 
			String intFilePath = resDir + "/" + outRoot + "." + program;
			Utilities.deleteFile(intFilePath + ".delta");
			Utilities.deleteFile(intFilePath + ".cluster");
			Utilities.deleteFile(intFilePath + ".mgaps");
			Utilities.deleteFile(intFilePath + ".aaqry"); // promer only
			Utilities.deleteFile(intFilePath + ".aaref"); // promer only
			Utilities.deleteFile(intFilePath + ".ntref"); // nucmer only
		}
	}
	
	public void interrupt() {
		setStatus(ProgSpec.STATUS_ERROR);
		process.destroy(); // terminate process
		try { process.waitFor(); } // wait for process to terminate
		catch (Exception e) { e.printStackTrace(); }; 
		cleanup();
	}
	
	// mdb added 3/26/09 - copied from jPAVE and modified
    public int runCommand ( String strCommand, Writer outWriter, Logger log ) throws Exception
    {
        boolean bDone = false;
        System.err.println(strCommand);
        // Log command
        if (log != null)
	        log.msgToFile(strCommand);
        
        // Execute command
        process = Runtime.getRuntime().exec( strCommand );
        
        // Capture stdout and stderr
        InputStream stdout = process.getInputStream();       
        BufferedReader brOut = new BufferedReader( new InputStreamReader(stdout) );
        InputStream stderr = process.getErrorStream();       
        BufferedReader brErr = new BufferedReader( new InputStreamReader(stderr) );
        
        try {
            synchronized ( stdout ) {
                while ( !bDone || brErr.ready() || brOut.ready()) {
                    // Send the sub-processes stderr to out stderr or log
                    while ( brErr.ready() ) {
                    	if ( outWriter != null )
                    		outWriter.write( brErr.read() );
                    	else if ( log != null)
                    		log.write( (char)brErr.read() );
                    	else
                    		System.err.print( (char)brErr.read() );
                    }
                    
                    // Consume stdout so the process doesn't hang...
                    while ( brOut.ready() ) {
                    	if ( outWriter != null )
                    		outWriter.write( brOut.read() );
                    	else if ( log != null )
                    		log.write( (char)brOut.read() );
                    	else
                    		System.out.print( (char)brOut.read() );
                    } 

                    if ( outWriter != null )
                    	outWriter.flush();
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
        	log.msg( "Non-zero return code: " + nExitValue );
        
        // Kill the process if it's still running (exception occurred)
        if ( !bDone ) {
        	interrupt();
        	log.msg( "Interrupted: " + Utilities.getDurationString(getRunTime()) );
        }
        else if (log != null)
	        log.msg( "Completed alignment " + alignNum + ": " + Utilities.getDurationString(getRunTime()) );
        
        // Clean up
        if ( outWriter != null )
        	outWriter.flush();
        try { 
        	// mdb added 5/29/09 - Prevent "too many open files" error
        	process.getOutputStream().close();
        	process.getInputStream().close();
        	process.getErrorStream().close();
        	process.destroy();
        	process = null;
        }
        catch (IOException e) { }
        
        return nExitValue;  
    }
    
    // "Comparable" interface for sorting
    public int compareTo(ProgSpec p) {
    	if (p != null)
    		return p.getDescription().compareTo( getDescription() );
    	return -1;
    }
}
