package toSymap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Vector;

import backend.Utils;
import util.ErrorReport;
import util.Popup;
import util.FileDir;

/************************************************
 * Print lengths of sequences from converted files
 */
public class Lengths {
	private final String seqSubDir = backend.Constants.seqSeqDataDir;
	private String logFileName = "/xLengths.log";
	
	private final int [] cutoffs = {10,20,30,40,50,60,70,80,90,100};
	private final String [] fastaFile = {".fa", ".fna"}; // converted files
	private final int maxLenForPrt=500;
	
	private PrintWriter logFile=null; 
	private String projDir="", seqDirName="";
	private Vector <File> seqFiles = new Vector <File> ();
	
	private Vector <Integer> lenVec = new Vector <Integer> ();
		
	protected Lengths(String projDir) {
		this.projDir = projDir;
		
		seqDirName = FileDir.fileNormalizePath(projDir, seqSubDir);
		if (!FileDir.pathExists(seqDirName)) {
			Popup.showWarningMessage("Path for sequence files does not exist: " + seqDirName);
			return;
		}	
		getSeqFiles();
		if (seqFiles.size()==0) {
			Popup.showWarningMessage("No .fa or .fna files in: " + seqDirName);
			return;
		}
		prt("");
		prt("------ Output lengths for " + seqDirName + " ------");
		createLog();
		
		readFasta();
		findCutoff();
		
		prt("----- Finish lengths for " + seqDirName + " -------");
		if (logFile!=null) logFile.close();
	}
	/*********************************************************/
	private void readFasta() {
	try {
		if (seqFiles.size()>1) prt("FASTA files " + seqFiles.size());
		
		int cntSeq=0, cntLen=0;
		long total=0;
		prt(String.format("\n%5s  %-10s  %s", "Seq#", "Length", "Seqid"));
		
		for (File f : seqFiles) {
			BufferedReader fh = Utils.openGZIP(f.getAbsolutePath()); 
			if (fh==null) {
				prt("Cannot open " + f.getAbsolutePath());
				return;
			}
			String line, grpFullName="", saveLine="";
			int len=0;
			
			while ((line = fh.readLine()) != null) {
				if (line.startsWith("#") || line.startsWith("!") || line.trim().isEmpty()) continue; 
				
    			if (line.startsWith(">")) {
    				if (len>0) {
    					cntSeq++;
    					if (len>maxLenForPrt) prt(String.format("%5d  %,10d  %s", cntSeq, len,  saveLine));
    					else {
    						cntLen++;
    						if (cntLen%1000==0) System.out.print(cntLen + " short sequences....\r");
    					}
    					lenVec.add(len);
    					total += len;
    				}
    				len=0;
    				saveLine = line;
					grpFullName = Utils.parseGrpFullName(line);
					
					if (grpFullName==null || grpFullName.equals("")){	
						Popup.showWarningMessage("Unable to parse group name from:" + line);
						return;
					}
    			}
    			else len += line.length();
			}
			if (len>0) {
				cntSeq++;
				if (len>maxLenForPrt) prt(String.format("%5d  %,10d  %s", cntSeq, len,  saveLine));
				else {
					cntLen++;
					if (cntLen%1000==0) System.out.print(cntLen + " short sequences....\r");
				}
				lenVec.add(len);
				total += len;
			}
		}
		System.out.print("                                                \r");
		if (cntLen>0) prt(String.format("#Seqs<%d  %,d", maxLenForPrt, cntLen));
		prt(String.format("Total %,d", total));
	}
	catch (Exception e) {ErrorReport.print("Length Read FASTA ");}
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
	catch (Exception e) {ErrorReport.print("Length Summary ");}
	}
	/******************************************************/
	private void getSeqFiles() { 
		try {	
			File sdf = new File(seqDirName);
			if (!sdf.exists() || !sdf.isDirectory()) {
				prt("Incorrect directory name: " + seqDirName);
				return;
			}
				
			for (File f2 : sdf.listFiles()) {
				String name = f2.getAbsolutePath();
				for (String suf : fastaFile) { 
					if (name.endsWith(suf) || name.endsWith(suf+".gz")) {
						seqFiles.add(f2);
						break;
					}
				}
			}
		}
		catch (Exception e) {ErrorReport.print(e, "Cannot get sequence files");}
	}
	private void createLog() {
		logFileName = projDir + logFileName;
		prt("Log file to  " + logFileName);	
		
		try {
			logFile = new PrintWriter(new FileOutputStream(logFileName, false)); 
		}
		catch (Exception e) {ErrorReport.print("Cannot open " + logFileName); logFile=null;}
	}
	private void prt(String msg) {
		System.out.println(msg);
		if (logFile!=null) logFile.println(msg);
	}
}
