package backend;

import java.io.File;
import java.io.BufferedReader;
import java.util.TreeSet;
import java.util.Vector;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.sql.ResultSet;

import symap.Globals;
import symap.manager.Mproject;
import util.Cancelled;
import util.ErrorCount;
import util.ErrorReport;
import util.Logger;
import util.Utilities;
import blockview.BlockViewFrame;

/*****************************************
 * Load chromosome/draft/LG sequences to SyMAP database.
 * Also fix bad chars if any
 * CAS534 changed from props to Project; removed static on everything
 */

public class SeqLoadMain {
	static int projIdx = 0;
	private static final int CHUNK_SIZE = Constants.CHUNK_SIZE;
	private static final int MAX_GRPS =   BlockViewFrame.MAX_GRPS; 	// CAS42 12/6/17 - for message
	private static final int MAX_COLORS = BlockViewFrame.maxColors; // CAS42 12/6/17 - for message
	private static boolean TRACE = Globals.TRACE; 					// CAS519 add for Guillermo hang problem
	private Mproject mProj;
	
	public boolean run(UpdatePool pool, Logger log, Mproject proj) throws Exception {
		try {
			mProj = proj;
			String projName = mProj.getDBName();
			projIdx = mProj.getIdx();
			
			long startTime = System.currentTimeMillis();
			log.msg("Loading sequences for " + projName);
			if (TRACE) log.msg("Tracing each step....");
			
	// Create project in DB; CAS534 project created in ManagerFrame/Mproject
			
	// Check Sequence files 
			if (TRACE) log.msg("Processing files");
			
			Vector<File> seqFiles = new Vector<File>();	
			
			String saveSeqDir="";
			long modDirDate=0;
			String projDir = Constants.seqDataDir + projName;
			String seqFileName = mProj.getSequenceFile();
			if (seqFileName.equals("")) {
				String seqDir = projDir + Constants.seqSeqDataDir; // created with Add Project
				log.msg("   Sequence_files not specified - use " + seqDir);
				
				if (!Utilities.pathExists(seqDir)) {
					return rtError(pool, log, projIdx, "Sequence files not found in " + seqDir);
				}			
				File sdf = new File(seqDir);
				if (sdf.exists() && sdf.isDirectory()) {
					saveSeqDir = seqDir;
					modDirDate = sdf.lastModified();
					
					for (File f2 : sdf.listFiles())
						seqFiles.add(f2);
				}
				else {
					return rtError(pool, log, projIdx, "Cannot find sequence directory " + seqDir);
				}
			}
			else {
				String[] fileList = seqFileName.split(",");
				String xxx = (fileList.length>1) ? (fileList.length + " files ") : seqFileName;
				log.msg("   User specified sequence files - " + xxx);
				for (String filstr : fileList) {
					if (filstr == null) continue;
					if (filstr.trim().equals("")) continue;
					File f = new File(filstr);
					
					if (!f.exists()) {
						log.msg("*** Cannot find sequence file " + filstr + " - try to continue...");
					}
					else if (f.isDirectory()) {
						saveSeqDir = f.getAbsolutePath();
						modDirDate = f.lastModified();
						
						for (File f2 : f.listFiles())
							seqFiles.add(f2);
					}
					else {
						saveSeqDir = Utils.pathOnly(f.getAbsolutePath());
						modDirDate = f.lastModified();
						
						seqFiles.add(f);
					}
				}
			}
			if (seqFiles.size()==0) { // CAS500
				return rtError(pool, log, projIdx, "No sequence files!!");
			}
			
	// Scan and upload the sequences
			log.msg("Checking sequence files....");
			String prefix = mProj.getGrpPrefix();
			Vector<String> grpList = new Vector<String>();
			int nSeqs = 0, seqIgnore=0, cntFile=0;
			long totalSize = 0;
			
			int minSize = mProj.getMinSize();
			log.msg("+++ Sequences < " + minSize + "bp will be ignored ('Minimum length' project parameter).");
	
			TreeSet<String> grpNamesSeen = new TreeSet<String>();
			TreeSet<String> grpFullNamesSeen = new TreeSet<String>();
			
			for (File f : seqFiles) {
				int nBadCharLines = 0, fileIgnore = 0;
				long fileSize = 0;
				if (!f.isFile() || f.isHidden()) continue; // CAS511 macos add ._ files in tar
				
				cntFile++;
				log.msg("Reading " + f.getName());
				BufferedReader fh = Utils.openGZIP(f.getAbsolutePath()); // CAS500
	
				int n = 0;
				StringBuffer curSeq = new StringBuffer();
				String grpName = null;
				String grpFullName = null;
				
				while (fh.ready()){
					String line = fh.readLine();
					if (Cancelled.isCancelled()) break;
					if (line.startsWith("#")) continue; // CAS512 add
					
					if (line.startsWith(">")) {
						if (grpName != null) {
							if (curSeq.length() >= minSize) {
								if (!grpNamesSeen.contains(grpName) &&  !grpFullNamesSeen.contains(grpFullName)){
									grpList.add(grpName);
									grpNamesSeen.add(grpName);
									grpFullNamesSeen.add(grpFullName);
									
									// load sequence
									System.out.print(grpName + ": " + curSeq.length() + "               \r");
									uploadSequence(grpName,grpFullName,curSeq.toString(),f.getName(),pool,nSeqs+1);	
									
									nSeqs++; n++;
									totalSize += curSeq.length();
									fileSize += curSeq.length();
								}
								else {
									System.out.println("+++ Duplicate sequence name: " + grpFullName + " (" + grpName + ") skipping...");
								}
							}
							else {seqIgnore++; fileIgnore++;} 
						}
						grpName = null;
						curSeq.setLength(0);
						
						if (!prefix.equals("") && !line.startsWith(">" + prefix)){
							String msg = "*** Invalid sequence name " + line + 
									"\n    Name should start with the prefix " + prefix + " (see Parameter Help)." +
									"\n    To change this, set the 'grp_prefix' project parameter to the proper value, or leave it blank.";
							return rtError(pool, log, projIdx, msg);
						}
						grpName = parseGrpName(line,prefix);
						grpFullName = parseGrpFullName(line);
						if (grpName==null || grpFullName==null || grpName.equals("") || grpFullName.equals("")){	
							return rtError(pool, log, projIdx, "Unable to parse group name from:" + line);
						}
					}	
					else {
						line = line.replaceAll("\\s+",""); 

						if (line.matches(".*[^agctnAGCTN].*")) {
							nBadCharLines++;
							line = line.replaceAll("[^agctnAGCTN]", "N");	
						}
						curSeq.append(line);
					}
				} // end reading file
			
		// load last sequence
				if (grpName != null) {
					if (curSeq.length() >= minSize) {
						grpList.add(grpName);
						uploadSequence(grpName, grpFullName, curSeq.toString(), f.getName(), pool, nSeqs+1);	
						nSeqs++; n++;
						totalSize += curSeq.length();
						fileSize += curSeq.length();
					}
					else {seqIgnore++; fileIgnore++;}
				}
				if (fileIgnore>0)
					 log.msg(String.format("%,10d sequences   %,10d bases   %,4d sequences ignore", n, fileSize, fileIgnore));
				else log.msg(String.format("%,10d sequences   %,10d bases", n, fileSize));
				
				if (nBadCharLines > 0)
					log.msg("+++ " + nBadCharLines + " lines contained characters other than AGCT; these will be replaced by N");
			
				if (nBadCharLines>0) mProj.saveProjParam("badCharLines","" + nBadCharLines);	
			} // end loop through files
			
			if (nSeqs == 0) {
				return rtError(pool, log, projIdx, "No sequences were loaded! Check for problems with the sequence files and re-load.");
			}
			// CAS532 add these to print on View
			mProj.saveProjParam("proj_seq_date", Utils.getDateStr(modDirDate));
			mProj.saveProjParam("proj_seq_dir", saveSeqDir);
			
			if (cntFile>1) {
				log.msg("Total:");
				if (seqIgnore>0)
					 log.msg(String.format("%,10d sequences   %,10d bases   %,4d sequences ignored", nSeqs, totalSize, seqIgnore));
				else log.msg(String.format("%,10d sequences   %,10d bases ", nSeqs, totalSize));
			}
			if (nSeqs >= MAX_COLORS)
				log.msg("+++ There are " + MAX_COLORS + " distinct colors for blocks -- there will be duplicates");
			
			if (nSeqs >= MAX_GRPS){
				log.msg("+++ More than " + MAX_GRPS + " sequences loaded!");
				log.msg("  Unless you are ordering draft contigs,");
				log.msg("    It is recommended to reload with a higher Minimum Length setting, before proceeding");
				log.msg("    Use script/lenFasta.pl to determine Minimum Length to use to reduce number of loaded sequences");
				//ErrorCount.inc(); CAS505 
			}
			updateSortOrder(grpList,pool, log);
					
			log.msg("Done:  " + Utilities.getDurationString(System.currentTimeMillis()-startTime) + "\n");
		}
		catch (OutOfMemoryError e){
			log.msg("\n\nOut of memory! To fix, \nA)Make sure you are using a 64-bit computer\nB)Launch SyMAP using the -m option to specify higher memory.\n\n");
			System.out.println("\n\nOut of memory! To fix, \nA)Make sure you are using a 64-bit computer\nB)Launch SyMAP using the -m option to specify higher memory.\n\n");
			System.exit(0);
			return false;
		}
		return true;
	}
	// CAS518 when there is errors and nothing is loaded, it still shows it is; remove project
	private boolean rtError(UpdatePool pool, Logger log, int projIdx, String msg) {
		Utilities.showWarningMessage("*** " + msg); // prints to stdout
		ErrorCount.inc();
		try {
			if (projIdx > 0) {
				mProj.removeProjectFromDB();
				log.msg("Remove project from database");
			}
		}
		catch (Exception e) {ErrorReport.print(e, "Removing project due to load failure");}
		
		return false;
	}
	// The prefix must be present. if no prefix, just use the given name.
	// Note, expects the starting ">" as well
	private String parseGrpName(String in, String prefix){
		in = in + " "; // hack, otherwise we need two cases in the regex
		String regx = ">\\s*(" + prefix + ")(\\w+)\\s?.*";
		Pattern pat = Pattern.compile(regx,Pattern.CASE_INSENSITIVE);
		Matcher m = pat.matcher(in);
		if (m.matches())
			return m.group(2);
		return parseGrpFullName(in);
	}
	private String parseGrpFullName(String in){
		String regx = ">\\s*(\\w+)\\s?.*";
		Pattern pat = Pattern.compile(regx,Pattern.CASE_INSENSITIVE);
		Matcher m = pat.matcher(in);
		if (m.matches())
			return m.group(1);
		System.err.println("Unable to parse group name from:" + in);
		return null;
	}
	private void updateSortOrder(Vector<String> grpList,UpdatePool pool, Logger log) throws Exception
	{
		// First, just set it to the idx order
		int minIdx;
		ResultSet rs = pool.executeQuery("select min(idx) as minidx from xgroups where proj_idx=" + projIdx);
		rs.first();
		minIdx = rs.getInt("minidx");
		pool.executeUpdate("update xgroups set sort_order = idx+1-" + minIdx + " where proj_idx=" + projIdx);
		//pool.executeUpdate("update xgroups set sort_order = 1 where proj_idx=" + projIdx);
		
		// CAS534 removed a bunch of code because SyProps had grp_order and grp_sort that never changed
		GroupSorter gs = new GroupSorter(GrpSortType.Alpha); 

		String undef = "";
		for (String grp : grpList) {
			if (!gs.orderCheck(grp)) {
				undef += grp + ", ";
			}
		}
		if (undef.length() > 0)  {
			log.msg("Group order not defined for: " + undef.substring(0, undef.length()-2));
			ErrorCount.inc();
			return;
		}
		Collections.sort(grpList,gs);
		
		for (int i = 1; i <= grpList.size(); i++) {
			String grp = grpList.get(i-1);
			pool.executeUpdate("update xgroups set sort_order=" + i + " where proj_idx=" + projIdx + 
					" and name='" + grp + "'");
		}
	}
	private void uploadSequence(String grp, String fullname, String seq, String file,
			UpdatePool pool,int order) throws Exception
	{
		// First, create the group
		pool.executeUpdate("INSERT INTO xgroups VALUES('0','" + projIdx + "','" + 
				grp + "','" + fullname + "'," + order + ",'0')" );
		String sql = "select max(idx) as maxidx from xgroups where proj_idx=" + projIdx;
		ResultSet rs = pool.executeQuery(sql);
		rs.first();
		int grpIdx = rs.getInt("maxidx");
		
		// Now, create the pseudos entry		
		pool.executeUpdate("insert into pseudos (grp_idx,file,length) values(" + grpIdx + ",'" + file + "'," + seq.length() + ")");
		
		// Finally, upload the sequence in chunks
		for (int chunk = 0; chunk*CHUNK_SIZE < seq.length(); chunk++) {
			int start = chunk*CHUNK_SIZE;
			int len = Math.min(CHUNK_SIZE, seq.length() - start );

			String cseq = seq.substring(start,start + len );
			String st = "INSERT INTO pseudo_seq2 VALUES('" + grpIdx + "','" + chunk + "','" + cseq + "')";
			pool.executeUpdate(st);
		}
	}
}
