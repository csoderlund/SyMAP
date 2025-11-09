package toSymap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.HashSet;

import backend.Constants;
import backend.Utils;
import util.ErrorReport;
import util.FileDir;

/*********************************************************
 * Splits converted files into chromosome files. Must be converted files.
 */
public class Split {
	private final String header = "### Written by SyMAP Convert";
	private String logFileName = "/xSplit.log";
	
	//output - the projDir gets appended to the front - predefined names, do not change
	private String seqDir =   	Constants.seqSeqDataDir;	// Default sequence directory for symap
	private String annoDir =  	Constants.seqAnnoDataDir;   // Default annotation directory for symap
	private String inFaFile =  "genomic";		// tries .fna and .fa   (constant in Convert methods)
	private String inGffFile = "anno";			// tries .gff and .gff3 (constant in Convert methods)
	
	private String seqScafFile = "scaf.fna";
	private String seqSuffix = ".fna";
	private String annoScafFile = "scaf.gff";
	private String annoSuffix = ".gff";
	
	// From ConvertNCBI and ConvertEnsembl
	private String chrPrefix="Chr", chrPrefix2 = "C", chrType="chromosome";	// changed to "C" if scaffolds too
	
	private String projDir="";
	
	private HashSet <String> chrSet = new HashSet <String> ();
	
	private PrintWriter logFile=null; 
	
	protected Split(String dirName) {
		projDir = dirName;
		
		prt("");
		prt("------ Split files for " + dirName + " ------");
		createLog();
		
		splitFasta();
		splitAnno();  
		
		prt("----- Finish split for " + dirName + " -------");
		if (logFile!=null) logFile.close();
	}
	/*************************************************************************/
	private void splitFasta() {
		try {
			String seqDirName = projDir + seqDir;
			if (!FileDir.pathExists(seqDirName)) {
				util.Popup.showWarningMessage("Directory does not exist: " + seqDirName);
				return;
			}	
			File sf = new File(seqDirName, inFaFile + ".fna");
			if (!sf.exists()) {
				sf = new File(seqDirName, inFaFile + ".fa");
				if (!sf.exists()) {
					prt("No sequence file called " + seqDirName + inFaFile + ".fna or .fa");
					return;
				}
			}
			///////////////////////////////////////////////////////
			BufferedReader fin = Utils.openGZIP(sf.getAbsolutePath()); 
			if (fin==null) return;
			String line = fin.readLine();
			if (!line.startsWith(header)) {
				prt("Incorrect 1st line: " + line);
				prt("The line should start with: " + header);
				return;
			}
			String xHead = line;
			boolean isScaf=false;
		
			PrintWriter fhOut=null;
			String filename="file";
			int seqLen=0, badLines=0;
			
			while ((line = fin.readLine()) != null) {
				if (line.startsWith("#") || line.startsWith("!") || line.trim().isEmpty()) continue; 
				
    			if (line.startsWith(">")) {
    				if (fhOut!=null && !isScaf) {
    					fhOut.close();
						prt(String.format("Write %-10s %,d", filename, seqLen));
						seqLen=0;
    				}
    				String line1 = line.substring(1).trim();
    				String [] tok = line1.split("\\s+");
    				if (tok.length==0) {
    					prt("****" + line);
    					if (badLines++>3) return;
    					continue;
    				}
    				String chr = tok[0];
    				if (line.contains(chrType) || chr.startsWith(chrPrefix) || chr.startsWith(chrPrefix2)) {
    					filename =  chr + seqSuffix;
    					fhOut = new PrintWriter(new FileOutputStream(seqDirName + filename, false));
    					fhOut.println(xHead);
    					chrSet.add(chr);
    				}
    				else if (!isScaf) {
    					filename = seqScafFile;
    					fhOut = new PrintWriter(new FileOutputStream(seqDirName + filename, false));
    					fhOut.println(xHead);
    					isScaf = true;
    				}
    				fhOut.println(line);
    			}
    			else {
    				seqLen += line.length();
    				fhOut.println(line);
    			}
			}
			if (fhOut!=null) {
				fhOut.close();
				prt(String.format("Write %-10s %,d", filename, seqLen));
			}
			fin.close();
			///////////////////////////////////////////////////////
			// Delete original
			prt("Delete " + sf.getName());
			sf.delete();
		}
		catch (Exception e) {ErrorReport.print(e, "Split Read FASTA ");}
	}
	/*************************************************************************/
	private void splitAnno() {
		try {	
			if (chrSet.size()==0) {
				prt("No sequences read from /sequence directory");
				return;
			}
			String annoDirName = projDir + annoDir;
			if (!FileDir.pathExists(annoDirName)) {
				prt("Directory does not exist: " + annoDirName);
				return;
			}		
			File af = new File(annoDirName, inGffFile + ".gff");
			if (!af.exists()) {
				af = new File(annoDirName, inGffFile + ".gff3");
				if (!af.exists()) {
					prt("No annotation file called " + annoDirName + inGffFile + ".gff or .gff3");
					return;
				}
			}
			///////////////////////////////////////////////////////
			BufferedReader fin = Utils.openGZIP(af.getAbsolutePath()); 
			if (fin==null) return;
			
			String line = fin.readLine();
			if (!line.startsWith(header)) {
				prt("Incorrect 1st line: " + line);
				prt("The line should start with: " + header);
				return;
			}
			String xHead = line, lastChr=null, filename="";
			boolean isScaf=false;
			PrintWriter ghOut=null;
			int cntLines=0, badLines=0;
			
			while ((line = fin.readLine()) != null) {
				if (line.startsWith("#") || line.startsWith("!") || line.trim().isEmpty()) continue; 
				cntLines++;
				String [] tok = line.split("\\s+");
				if (tok.length==0) {
					prt("****" + line);
					if (badLines++>3) return;
					continue;
				}
				String chr = tok[0];
				
				if (chr.equals(lastChr) || isScaf) {
					ghOut.println(line);
					continue;
				}
				
				if (ghOut!=null) {
					ghOut.close();
					prt(String.format("Write %-10s %,d lines", filename, cntLines));
					cntLines=0;
				}	
				if (chrSet.contains(chr)) {
					lastChr = chr;
					filename = chr + annoSuffix;
				}
				else {
					isScaf = true;
					filename = annoScafFile;
				}
				ghOut = new PrintWriter(new FileOutputStream(annoDirName + filename, false));
				ghOut.println(xHead);
				ghOut.println(line);
			}
			if (ghOut!=null) ghOut.close();
			fin.close();
			
			///////////////////////////////////////////////////////
			prt("Delete " + af.getName());
			af.delete();
		}
		catch (Exception e) {ErrorReport.print(e, "Split Read anno ");}
	}
	/*************************************************************************/
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
