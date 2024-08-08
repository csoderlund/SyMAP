package toSymap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Vector;

import backend.Constants;
import backend.Utils;
import util.ErrorReport;
import util.Utilities;

/************************************************
 * Print lengths of sequences
 */
public class Lengths {
	private String logFileName = "/xLengths.log";
	private PrintWriter logFile=null; 
	
	private String seqTopDir="", seqDirName="";
	
	private Vector <Integer> lenVec = new Vector <Integer> ();
	private int [] cutoffs = {10,20,30,40,50,60,70,80,90,100};
			
	private Summary checkObj=null;
	
	protected Lengths(String dirName) {
		seqTopDir = dirName;
		
		checkObj = new Summary(dirName);
		seqDirName = checkObj.getSeqDir();
		if (seqDirName==null) return;
		
		prt("");
		prt("------ Output lengths for " + seqDirName + " ------");
		createLog();
		
		readFasta();
		findCutoff();
		
		prt("----- Finish lengths for " + seqDirName + " -------");
		if (logFile!=null) logFile.close();
	}
	private void createLog() {
		logFileName = seqTopDir + logFileName;
		prt("Log file to  " + logFileName);	
		
		try {
			logFile = new PrintWriter(new FileOutputStream(logFileName, false)); 
		}
		catch (Exception e) {ErrorReport.print("Cannot open " + logFileName); logFile=null;}
	}
	private void readFasta() {
	try {
		Vector <File> seqFiles = checkObj.getSeqFiles();
		if (seqFiles==null || seqFiles.size()==0) {
			prt("Cannot find sequence files in " + seqTopDir + "/" + Constants.seqSeqDataDir );
			return;
		}
		
		int cntSeq=0;
		long total=0;
		prt(String.format("\n%5s  %-10s  %s", "Seq#", "Length", "Seqid"));
		
		for (File f : seqFiles) {
			BufferedReader fh = Utils.openGZIP(f.getAbsolutePath()); 
			if (fh==null) return;
			String line, grpFullName="", saveLine="";
			int len=0;
			
			while ((line = fh.readLine()) != null) {
				if (line.startsWith("#") || line.startsWith("!") || line.trim().isEmpty()) continue; 
				
    			if (line.startsWith(">")) {
    				if (len>0) {
    					cntSeq++;
    					prt(String.format("%5d  %,10d  %s", cntSeq, len,  saveLine));
    					lenVec.add(len);
    					total += len;
    				}
    				len=0;
    				saveLine = line;
					grpFullName = Utils.parseGrpFullName(line);
					
					if (grpFullName==null || grpFullName.equals("")){	
						Utilities.showWarningMessage("Unable to parse group name from:" + line);
						return;
					}
    			}
    			else len += line.length();
			}
		}
		prt(String.format("Total %,d", total));
	}
	catch (Exception e) {ErrorReport.print("Read FASTA ");}
	}
	/******************************************************/
	private void findCutoff() {
	try {
		if (lenVec.size()<cutoffs[0]) {
			prt("\nLess than " + cutoffs[0] + " sequences; no minimal length to compute");
			return;
		}
		prt("\nValues for parameter 'Minimal length' (assuming no duplicate lengths):");
		Collections.sort(lenVec);
		Collections.reverse(lenVec);
		prt("");
		prt(String.format("%5s %9s", "#Seqs", "Minimum length"));
		
		for (int i=0; i<cutoffs.length; i++) {
			int x = cutoffs[i];
			if (x>=lenVec.size()) break;
			
			prt(String.format("%,5d %,8d", x, lenVec.get(x-1)));
		}
	}
	catch (Exception e) {ErrorReport.print("Summary ");}
	}
	private void prt(int cnt, String msg) {
		String x = String.format("%,5d %s", cnt, msg);
		System.out.println(x);
		if (logFile!=null) logFile.println(x);
	}
	private void prt(String msg) {
		System.out.println(msg);
		if (logFile!=null) logFile.println(msg);
	}
}
