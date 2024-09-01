package backend;

import java.io.File;
import java.io.BufferedReader;
import java.util.TreeSet;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Collections;
import java.util.Comparator;
import java.sql.ResultSet;

import database.DBconn2;
import symap.manager.Mproject;
import util.Cancelled;
import util.ErrorCount;
import util.ErrorReport;
import util.ProgressDialog;
import util.Utilities;
import blockview.BlockViewFrame;

/*****************************************
 * Load chromosome/draft/LG sequences to SyMAP database.
 * Also fix bad chars if any
 * CAS534 changed from props to Project; removed static on everything
 * CAS535 ignore sequences without prefixes
 * CAS541 UpdatePool->DBconn2
 * CAS557 split into multiple methods 
 */
public class SeqLoadMain {
	static int projIdx = 0;
	private static final int CHUNK_SIZE = Constants.CHUNK_SIZE;
	private static final int MAX_GRPS =   BlockViewFrame.MAX_GRPS; 	// CAS42 12/6/17 - for message
	private static final int MAX_COLORS = BlockViewFrame.maxColors; // CAS42 12/6/17 - for message
	private Mproject mProj;
	private DBconn2 tdbc2=null;
	private ProgressDialog plog;
	
	private Vector<File> seqFiles = new Vector<File>();	
	private String saveSeqDir="", projName;
	private long modDirDate=0;
	private int totalnSeqs=0, cntFile=0, totalSeqIgnore=0, totalBasesWritten=0;
	private Vector<String> grpList = new Vector<String>();
	
	public boolean run(DBconn2 dbc2, ProgressDialog plog, Mproject mProj) throws Exception {
		try {
			tdbc2 = new DBconn2("SeqLoad-"+ DBconn2.getNumConn(), dbc2);
			this.plog = plog;
			this.mProj = mProj;
			projName = mProj.getDBName();
			projIdx = mProj.getIdx();
			
			long startTime = Utils.getTime();
			plog.msg("Loading sequences for " + projName);
			
			if (!getSeqFiles()) return false;
			if (Cancelled.isCancelled()) return rtError("User cancelled");
				
			if (!loadSeqFiles()) return false;
			if (Cancelled.isCancelled()) return rtError("User cancelled");
			
			/* ************************************************************ */
			// CAS532 add these to print on View
			mProj.saveProjParam("proj_seq_date", Utils.getDateStr(modDirDate));
			mProj.saveProjParam("proj_seq_dir", saveSeqDir);
			
			if (cntFile>1) {
				plog.msg("Total:");
				if (totalSeqIgnore>0)
					 plog.msg(String.format("%,5d sequences   %,10d bases   %,4d sequences ignored", totalnSeqs, totalBasesWritten, totalSeqIgnore));
				else plog.msg(String.format("%,5d sequences   %,10d bases ", totalnSeqs, totalBasesWritten));
			}
			if (totalnSeqs >= MAX_COLORS)
				plog.msg("+++ There are " + MAX_COLORS + " distinct colors for blocks -- there will be duplicates");
			
			if (totalnSeqs >= MAX_GRPS){
				plog.msg("+++ More than " + MAX_GRPS + " sequences loaded!");
				plog.msg("  Unless you are ordering draft contigs,");
				plog.msg("    It is recommended to reload with a higher Minimum Length setting, before proceeding");
				plog.msg("    Use script/lenFasta.pl to determine Minimum Length to use to reduce number of loaded sequences");
				//ErrorCount.inc(); CAS505 
			}
			updateSortOrder(grpList);
			tdbc2.close();
			
			Utils.prtTimeMemUsage(plog, "Finish load sequences", startTime); // CAS556 add finish and mem
		} catch (Exception e) {ErrorReport.print(e, "Seq Load Main"); return false;}	
		return true;
	}
	/***************************************************************
	 * Get vector of sequence files (CAS557 made separate method)
	 */
	private boolean getSeqFiles() {	
	try {
		String projDir = Constants.seqDataDir + projName;
		String seqFileName = mProj.getSequenceFile();
		
		if (seqFileName.equals("")) {
			String seqDir = projDir + Constants.seqSeqDataDir; // created with Add Project
			plog.msg("   Sequence_files not specified - use " + seqDir);
			
			if (!Utilities.pathExists(seqDir)) {
				return rtError("Sequence files not found in " + seqDir);
			}			
			File sdf = new File(seqDir);
			if (sdf.exists() && sdf.isDirectory()) {
				saveSeqDir = seqDir;
				modDirDate = sdf.lastModified();
				
				for (File f2 : sdf.listFiles()) {
					if (!f2.isFile() || f2.isHidden()) continue; // CAS511 macos add ._ files in tar
					String name = f2.getAbsolutePath();
					for (String suf : Constants.fastaFile) { // CAS557 add check
						if (name.endsWith(suf) || name.endsWith(suf+".gz")) {
							seqFiles.add(f2);
							break;
						}
					}
				}
			}
			else {
				return rtError("Cannot find sequence directory " + seqDir);
			}
		}
		else {
			String[] fileList = seqFileName.split(",");
			String xxx = (fileList.length>1) ? (fileList.length + " files ") : seqFileName;
			plog.msg("   User specified sequence files - " + xxx);
			for (String filstr : fileList) {
				if (filstr == null) continue;
				if (filstr.trim().equals("")) continue;
				File f = new File(filstr);
				
				if (!f.exists()) {
					plog.msg("*** Cannot find sequence file " + filstr + " - try to continue...");
				}
				else if (f.isDirectory()) {
					saveSeqDir = f.getAbsolutePath();
					modDirDate = f.lastModified();
					
					for (File f2 : f.listFiles()) {
						if (!f2.isFile() || f2.isHidden()) continue; // CAS511 macos add ._ files in tar
						
						String name = f2.getAbsolutePath();
						for (String suf : Constants.fastaFile) { // CAS557 add check
							if (name.endsWith(suf) || name.endsWith(suf+".gz")) {
								seqFiles.add(f2);
								break;
							}
						}
					}
				}
				else {
					saveSeqDir = Utils.pathOnly(f.getAbsolutePath());
					modDirDate = f.lastModified();
					
					String name = f.getAbsolutePath();
					for (String suf : Constants.fastaFile) { // CAS557 add check
						if (name.endsWith(suf) || name.endsWith(suf+".gz")) {
							seqFiles.add(f);
							break;
						}
					}
				}
			}
		}
		if (seqFiles.size()==0)  // CAS500
			return rtError("No sequence files ending in " + Constants.fastaList + "!!");
		return true;
	}
	catch (Exception e) {ErrorReport.print(e, "Getting sequence files"); return false;}
	}
	/***************************************************************
	 * Load sequence files (CAS557 made separate method)
	 */
	private boolean loadSeqFiles() {
	try {
		String prefix = mProj.getGrpPrefix();
		if (prefix.contentEquals("")) plog.msg("   No sequence prefix supplied (See Parameters - Group prefix)");
		else 						  plog.msg("   Load sequences with '" + prefix + "' prefix (See Parameters - Group prefix)");
		
		int minSize = mProj.getMinSize(), readSeq=0;
		plog.msg(String.format("   Load sequences > %,dbp (See Parameters - Minimum length)", minSize)); // CAS558 add commas
		
		TreeSet<String> grpNamesSeen = new TreeSet<String>();
		TreeSet<String> grpFullNamesSeen = new TreeSet<String>();
		
	// For all files, Scan and upload the sequences		
		for (File f : seqFiles) {
			int nBadCharLines = 0, seqIgnore = -1,  seqPrefixIgnore=0, nSeqs=0;
			long basesWritten = 0;
			
			cntFile++;
			plog.msg("Reading " + f.getName());
			
			StringBuffer curSeq = new StringBuffer();
			String grpName = null, grpFullName = null, firstTok=null, line;
			
			BufferedReader fh = Utils.openGZIP(f.getAbsolutePath()); // CAS500

			while ((line = fh.readLine()) != null) {
				
				if (line.startsWith("#") || line.isEmpty()) continue; // CAS512 add
				
				if (line.startsWith(">")) {
					if (grpName != null && curSeq.length() >= minSize) {
						if (!grpNamesSeen.contains(grpName) &&  !grpFullNamesSeen.contains(grpFullName)){
							grpList.add(grpName);
							grpNamesSeen.add(grpName);
							grpFullNamesSeen.add(grpFullName);
							nSeqs++; 
							basesWritten += curSeq.length();
							System.out.format("%s: %,d                            \r", grpName,curSeq.length());
							
							// load sequence
							String chrSeq = curSeq.toString(); // CAS556 prevent two copies in memory at once
							curSeq.setLength(0);
							
							uploadSequence(grpName,grpFullName,chrSeq,f.getName(),totalnSeqs+1);	
		
							chrSeq = null;
							System.gc();	
						}
						else {
							plog.msgToFile("+++ Dup seqid: " + grpName + " (" + firstTok + ") skipping...");
						}
					} else seqIgnore++;
					
					readSeq++;
					if (readSeq%100 == 0) System.err.print("Read sequences " + readSeq + "....\r"); // CAS557 add
					
					grpName = null; firstTok = grpFullName = "";
					curSeq.setLength(0);
					
					if (!parseHasPrefix(line, prefix)){ 
						seqPrefixIgnore++;
						if (seqPrefixIgnore<=3) plog.msgToFile("+++ Invalid prefix, ignore: " + line);
						if (seqPrefixIgnore==3) plog.msgToFile("+++ Surpressing further invalid prefix ");
						continue;
					}
					
					grpName     = Utils.parseGrpName(line,prefix);
					grpFullName = Utils.parseGrpFullName(line);
					if (grpName==null || grpFullName==null || grpName.equals("") || grpFullName.equals("")){	
						return rtError("Unable to parse group name from:" + line);
					}
					String [] tl = line.split(" "); // CAS557 HSCHR19KIR_CA01-TB04_CTG3_1 fullName splits at "-"; want to show it all
					firstTok = (tl.length>0) ? tl[0] :  Utils.parseGrpFullName(line); 
				} 	
				else if (grpName!=null) { // sequence for valid grpName
					line = line.replaceAll("\\s+",""); 

					if (line.matches(".*[^agctnAGCTN].*")) {
						nBadCharLines++;
						line = line.replaceAll("[^agctnAGCTN]", "N");	
					}
					curSeq.append(line);
				}
				
				if (Cancelled.isCancelled()) {
					fh.close();
					return rtError("User cancelled");
				}	
			} // end reading file
			fh.close(); // CAS535 add
			
			if (grpName != null &&  curSeq.length() >= minSize) {// load last sequence
				grpList.add(grpName);
				nSeqs++;
				basesWritten += curSeq.length();
				System.out.format("%s: %,d                            \r", grpName,curSeq.length()); // CAS556 add
				
				String chrSeq = curSeq.toString(); // CAS556 
				curSeq.setLength(0);
				uploadSequence(grpName, grpFullName, chrSeq, f.getName(), totalnSeqs+1);	
				chrSeq="";
			}
			else if (curSeq.length()>0) seqIgnore++; 
			curSeq.setLength(0);
			
			if (seqIgnore==0) plog.msg(String.format("%,5d sequences   %,10d bases", nSeqs, basesWritten));
			else 			  plog.msg(String.format("%,5d sequences   %,10d bases   %,4d sequences ignore", nSeqs, basesWritten, seqIgnore));
			
			if (nBadCharLines > 0) {
				plog.msg("+++ " + nBadCharLines + " lines contain characters that are not AGCT; replaced by N");
				mProj.saveProjParam("badCharLines","" + nBadCharLines);	
			}
			totalSeqIgnore += seqIgnore;
			totalBasesWritten += basesWritten;
			totalnSeqs += nSeqs;
		} // end loop through files
		
		if (totalnSeqs==0) return rtError( "No sequences were loaded! Read the documentation on 'input'.");
		return true;
	}
	catch (Exception e) {ErrorReport.print(e, "Loading sequences"); return rtError("Fatal error");}
	catch (OutOfMemoryError e){
		tdbc2.shutdown();
		ErrorReport.prtMem();
		System.out.println("\n\nOut of memory! Modify the symap script 'mem=' line to specify higher memory.\n\n");
		System.exit(0);
		return false;
	}
	}
	private boolean parseHasPrefix(String name, String prefix){
		if (prefix.equals("")) return true;
	
		String regx = "\\s*(" + prefix + ")(\\w+)\\s?.*"; 
		Pattern pat = Pattern.compile(regx,Pattern.CASE_INSENSITIVE);
		
		String n = name + " "; // hack, otherwise we need two cases in the regex
		if (n.startsWith(">")) n = n.substring(1);
		Matcher m = pat.matcher(n);
		if (m.matches()) return true;
		return false;
	}
	/***********************************************************************/
	private void uploadSequence(String grp, String fullname, String seq, String file, int order)  {
	try {
		// First, create the group; FIXME check for duplicates
		tdbc2.executeUpdate("INSERT INTO xgroups VALUES('0','" + projIdx + "','" + 
				grp + "','" + fullname + "'," + order + ",'0')" );
		String sql = "select max(idx) as maxidx from xgroups where proj_idx=" + projIdx;
		ResultSet rs = tdbc2.executeQuery(sql);
		rs.first();
		int grpIdx = rs.getInt("maxidx");
		
		// CAS553 add this check; max int in java is 2,147,483,647 (max human chr is 249M); can't test, run out of memory trying
		int seqlen = -1;
		try {seqlen = seq.length();} // may set to a negative number
		catch (Exception e) {seqlen = -1;}
		if (seqlen<0) {
			String x = String.format("Chromosome sequence from file %s is length %,d", file, seq.length());
			ErrorReport.print(x);
			x = String.format("The maximum allowed is %,d", Integer.MAX_VALUE);
			ErrorReport.die(x);
		}
		tdbc2.executeUpdate("insert into pseudos (grp_idx,file,length) values(" + grpIdx + ",'" + file + "'," + seqlen + ")");
		
		// Finally, upload the sequence in chunks
		for (int chunk = 0; chunk*CHUNK_SIZE < seq.length(); chunk++) {
			int start = chunk*CHUNK_SIZE;
			int len = Math.min(CHUNK_SIZE, seq.length() - start );

			String cseq = seq.substring(start,start + len );
			String st = "INSERT INTO pseudo_seq2 VALUES('" + grpIdx + "','" + chunk + "','" + cseq + "')";
			tdbc2.executeUpdate(st);
		}
	}
	catch (Exception e) {ErrorReport.print(e, "Loading sequence - fail loading to database "); };
	}
	/**************************************************************************/
	// CAS518 when there is errors and nothing is loaded, it still shows it is; remove project
	private boolean rtError(String msg) {
		plog.msgToFileOnly("*** " + msg);
		Utilities.showWarningMessage("*** " + msg); // prints to stdout
		ErrorCount.inc();
		tdbc2.close();
		
		try {
			if (projIdx > 0) {
				mProj.removeProjectFromDB();
				plog.msg("Remove partially loaded project from database");
			}
		}
		catch (Exception e) {ErrorReport.print(e, "Removing project due to load failure");}
		
		return false;
	}
	/***********************************************************************/
	private void updateSortOrder(Vector<String> grpList)  {
	try {
		int minIdx = tdbc2.executeCount("select min(idx) from xgroups where proj_idx=" + projIdx);
		tdbc2.executeUpdate("update xgroups set sort_order = idx+1-" + minIdx + " where proj_idx=" + projIdx);
		GroupSorter gs = new GroupSorter();
		
		// CAS534 removed a bunch of code because SyProps had grp_order and grp_sort that never changed
		// CAS543 does nothing code; for (String grp : grpList) {if (!gs.orderCheck(grp)) undef += grp + ", ";
		
		Collections.sort(grpList,gs);
		
		for (int i = 1; i <= grpList.size(); i++) {
			String grp = grpList.get(i-1);
			tdbc2.executeUpdate("update xgroups set sort_order=" + i + " where proj_idx=" + projIdx + 
					" and name='" + grp + "'");
		}
	}
	catch (Exception e) {ErrorReport.print(e, "Loading sequence - fail ordering them"); };
	}
	// Calculate order to be saved in xgroups
	private class GroupSorter implements Comparator<String>{ // CAS543 moved from separate file	
		public GroupSorter(){}
		
		// If prefix defined in parameters, it is stripped off already
		public int compare(String g1, String g2) {
			
			if (g1.equals(g2)) return 0;
			boolean areNumbers = true;
			try {
				Integer.parseInt(g1);
				Integer.parseInt(g2);
			}
			catch(Exception e) {areNumbers = false;}
			
			if (areNumbers) return Integer.parseInt(g1) - Integer.parseInt(g2);
		
			int nMatch = 0;  		// Look for a matching prefix; CAS543 was not checking chars, so useless code
			while (nMatch < g1.length() && nMatch < g2.length() && g1.charAt(nMatch)==g2.charAt(nMatch)) nMatch++;		
			if(nMatch == g1.length()) return -1; // all of g1 matched, hence must come before g2 alphabetically 
			if(nMatch == g2.length()) return 1;
			
			String suff1 = g1.substring(nMatch);
			String suff2 = g2.substring(nMatch);
			
			
			areNumbers = true; 		// are these suffixes numeric??
			try {
				Integer.parseInt(suff1);
				Integer.parseInt(suff2);
			}
			catch(Exception e) {areNumbers = false;}
			
			if (areNumbers) return Integer.parseInt(suff1) - Integer.parseInt(suff2);
				
			return g1.compareTo(g2); 
		}
	}
}
