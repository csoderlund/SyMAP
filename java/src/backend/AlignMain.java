package backend;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.util.Vector;

import java.util.TreeMap;
import java.util.Queue;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.sql.ResultSet;

import database.DBconn2;
import symap.Ext;
import symap.Globals;
import symap.manager.Mpair;
import symap.manager.Mproject;
import util.Cancelled;
import util.ErrorReport;
import util.ProgressDialog;
import util.Utilities;

/*******************************************************
 * Set up to run MUMmer for alignments for one pair of genomes
 */
public class AlignMain {
	public boolean mCancelled = false;
	
	private static final int CHUNK_SIZE = Constants.CHUNK_SIZE; // 1000000
	private static final int maxFileSize = 60000000; // 'Concat' parameter works for demo using this size
	
	private ProgressDialog plog;
	private String proj1Dir, proj2Dir; // also dbName
	private Mproject mProj1, mProj2;
	
	private String alignLogDirName;
	private Vector<AlignRun> alignList;
	private Queue<AlignRun>  toDoQueue;
	private Vector<Thread> threads;
	private int nMaxCPUs;
	
	private Mpair mp = null;
	private DBconn2 dbc2 = null;
	
	private boolean bDoCat = true; 
	private boolean notStarted = true;
	private boolean interrupted = false;
	private boolean error = false;
	
	private String resultDir;
	private String alignParams; // Set in pair_props from DoAlignSynPair;
	private int nAlignDone=0;
	
	protected AlignMain(DBconn2 dbc2, ProgressDialog log, Mpair mp, int nMaxThreads, String alignLogDirName)
	{
		this.plog = log; // diaLog and syLog
		this.nMaxCPUs = nMaxThreads;
		this.mp = mp;
		this.dbc2 = dbc2;
		this.mProj1 = mp.mProj1;
		this.mProj2 = mp.mProj2;
		this.alignLogDirName = alignLogDirName;
		
		proj1Dir = mProj1.getDBName();
		proj2Dir = mProj2.getDBName();
		bDoCat = mp.isConcat(Mpair.FILE);
		
		threads = 	new Vector<Thread>();
		alignList = new Vector<AlignRun>();
		toDoQueue = new LinkedList<AlignRun>();
	}
	protected String getParams() {// DoAlignSynPair save to DB pairs.params
		 return alignParams;
	}
	
	/*******************************************************
	 * Run all alignments for p1-p2 
	 */
	protected boolean run() {
		try {
			long startTime = Utils.getTime();
	
			System.gc(); // free unused heap for mummer to use (Java treats this as a suggestion)
			
			resultDir = Constants.getNameResultsDir(proj1Dir, proj2Dir);
			
			if (alignExists()) return true; // must have all.done and at least one .mum file; r/w params_used
			
			buildAlignments(); // build toDoQueue
			
			if (error) return false; // error occurred in buildAlignments
			if (mCancelled) return false;
			
			if (toDoQueue.size() > 0) {
				String program = toDoQueue.peek().program.toUpperCase();
				String msg = "\nRunning " + program + ": " + toDoQueue.size() + " alignments to perform";
				if (nAlignDone>0) msg += " (" + nAlignDone + " previously completed)"; // CAS566 add
				if (toDoQueue.size()>nMaxCPUs) msg += " using "+ nMaxCPUs + " CPUs";
				plog.msg(msg);
			}
			
			int alignNum = 0;
			while (true) {
				synchronized(threads) {
					if (toDoQueue.size()==0 && threads.size() == 0)
						break;
					
					while (toDoQueue.size()>0 && threads.size() < nMaxCPUs) {// Start another alignment thread
						final AlignRun p = toDoQueue.remove();
						alignNum++;
						p.alignNum = alignNum;
						Thread newThread = new Thread() {
							public void run() {
								try {
									p.doAlignment();
								}
								catch (Exception e) {
									ErrorReport.print(e, "Alignment thread");
									p.setStatus(AlignRun.STATUS_ERROR);
								}
								synchronized(threads) { threads.remove( this ); }
							}
						};
						
						threads.add( newThread );
						newThread.start();
						notStarted=false;
					}
				}
				Utilities.sleep(1000); // free processor
			}
			
			if (getNumErrors() == 0) {
				if (!mCancelled) {
					Utils.writeDoneFile(resultDir + Constants.alignDir);
					plog.msg("Alignments:  success " + getNumCompleted());
					
					String tmpDir = Constants.getNameTmpDir(proj1Dir, proj2Dir);// only delete tmp files if successful
					if (Constants.PRT_STATS) {
						System.out.println("Do not delete " + tmpDir);
					}
					else {
						File dir = new File(tmpDir);
						Utilities.clearAllDir(dir);
						dir.delete();
					}
				}
			}
			else { 
				plog.msg("Alignments:  success " + getNumCompleted() + "   failed " + getNumErrors());
			}
			Utils.prtMsgTimeDone(plog, "Alignments", startTime); // Align done: time
		} 
		catch (Exception e) {ErrorReport.print(e, "Run alignment"); }
		
		return (getNumErrors() == 0);
	}
	
	/*******************************************************************
	 * True -  all.done && at least one .mum file; do not do any alignments
	 * False - !all.done; do any alignments not done (see buildAlignments ps.isDone)
	 ****************************************************************/
	private boolean alignExists() {
		try {
			String alignDir = Constants.getNameResultsDir(proj1Dir, proj2Dir) + Constants.alignDir;
			File f = new File(alignDir);
			if (!f.exists()) {	/*-- alignments to be done --*/
				paramsWrite();
				return false;
			}
			
			boolean bdone = Utils.checkDoneFile(alignDir); // all.done
			nAlignDone = Utils.checkDoneMaybe(alignDir);   // # existing .mum files
			
			if (bdone && nAlignDone>0) {// could by SyMAP or user complete
				plog.msg("Warning: " + nAlignDone + " alignment files exist - using existing files");
				plog.msg("   If not correct, remove " + resultDir + " and re-align.");
				paramsRead(Utils.getDateStr(f.lastModified()));
				return true;
			}
			/*-------- Alignments to be done ------*/
			paramsWrite();
			
			if (nAlignDone==0) return false; // do all
			
			/* If !bDone && nAlign>0, probably did not finish; output state CAS566 add 
			 * This is strictly to let the user know what is being done 
			 * See buildAlignments ps.isDone() */
			HashSet <String> fdone = new HashSet <String> ();
			HashSet <String> align = new HashSet <String> ();
			
			for (File x : f.listFiles()) {
				if (!x.isFile()) continue;
				if (x.isHidden()) continue;
				
				String path = x.getName();
				String name = path.substring(path.lastIndexOf("/")+1, path.length());
				if (name.endsWith(Constants.doneSuffix)) fdone.add(name);
				else if (name.endsWith(Constants.mumSuffix)) align.add(name);
			}
			plog.msg("+++ Checking "+ alignDir);
			plog.msg(String.format("   No all.done file; %,d .mum files; %,d .mum.done files ", align.size(), fdone.size()));
			plog.msg("    Finding possible missing .mum files to align");
			return false;
		}
		catch (Exception e) {ErrorReport.print(e, "Trying to see if alignment exists");}
		return false;
	}
	/*********************************************************
	 * Create alignParams for save to pairs.param for summary
	 * If align, write to params_used, else read it. This is not in the /align directory, but its main directory
	 * CAS568 add; nothing else has to be changed for this
	 */
	private void paramsWrite() {
		try {
			alignParams = "MUMmer files " + Utilities.getDateTime() + "   CPUs: " + nMaxCPUs + "\n";
			alignParams += mp.getAlign(Mpair.FILE, true); // true, do not add command line
			if (Ext.isMummer4Path()) alignParams += Ext.getMummerPath() + " ";
			
			String resultDir = "./" + Constants.getNameResultsDir(mProj1.strDBName, mProj2.strDBName);
			File pfile = new File(resultDir,Constants.usedFile);
			if (!pfile.exists()) return;	// can happen
			
			PrintWriter out = new PrintWriter(pfile);
			out.println("# Parameters used for MUMmer alignments in /align.");
			out.println(alignParams);
			out.close();
		}
		catch (Exception e) {ErrorReport.print(e, "Save " + Constants.usedFile); }
	}
	private void paramsRead(String dateTime) {
		try {
			alignParams = "Use previous " + nAlignDone + " MUMmer files " + dateTime; 
			
			if (!Utilities.dirExists(resultDir)) return; // ok, may not have been created
			
			File pfile = new File(resultDir,Constants.usedFile); 
			if (!pfile.isFile()) return; 
			
			alignParams = "Use previous " + nAlignDone + " "; // completed with what is in file
			String line;
			BufferedReader reader = new BufferedReader(new FileReader(pfile));
			while ((line = reader.readLine()) != null) {
				if (!line.startsWith("#"))
					alignParams += line + "\n";
			}
			reader.close();
		}
		catch (Exception e) {ErrorReport.print(e, "Save params_used"); }
	}
	/****************************************************
	 * Create preprocessed files and run alignment
	 */
	private void buildAlignments()  {
		try {
			plog.msg("\nAligning " + proj1Dir + " and " + proj2Dir + " with " + nMaxCPUs + " CPUs"); // CAS568 remove mp.getAlign, but add CPU
			
		/* create directories */
			// result directory (e.g. data/seq_results/demo1_to_demo2)
			Utilities.checkCreateDir(resultDir, true /* bPrt */);
					
			// temporary directories to put data for alignment
			String tmpDir =  Constants.getNameTmpDir(proj1Dir, proj2Dir);
			Utilities.checkCreateDir(tmpDir, false);
			
			boolean isSelf = (proj1Dir.equals(proj2Dir));
			String tmpDir1 = Constants.getNameTmpPreDir(proj1Dir,  proj2Dir, proj1Dir);
			String tmpDir2 = (isSelf) ? tmpDir1 : Constants.getNameTmpPreDir(proj1Dir, proj2Dir, proj2Dir);
			File fh_tDir1 = Utilities.checkCreateDir(tmpDir1, false);
			File fh_tDir2 = Utilities.checkCreateDir(tmpDir2, false);
			
		// Preprocessing for proj1
			if (!writePreprocSeq(fh_tDir1, mProj1,  bDoCat, isSelf, "Query")) { // concat=true if Concat not checked
				mCancelled = true;
				Cancelled.cancel();
				System.out.println("Cancelling"); 
				return;								
			}
			
		// Preprocessing for proj2	
			if (!writePreprocSeq(fh_tDir2, mProj2,  false, isSelf, "Target")) { // concat=false
				mCancelled = true;
				Cancelled.cancel();
				System.out.println("Cancelling");
				return;															
			}
			
			/** Assign values for Alignment; path is obtained in AlignRun **/
			String program = Ext.exPromer;
			if (isSelf) program = Ext.exNucmer;
			
			// user over-rides
			if      (program.equals(Ext.exPromer) && mp.isNucmer(Mpair.FILE)) program = Ext.exNucmer; // CAS568 was backwards
			else if (program.equals(Ext.exNucmer) && mp.isPromer(Mpair.FILE)) program = Ext.exPromer; // CAS568 was backwards
				
			String args = (program.contentEquals(Ext.exPromer)) ? mp.getPromerArgs(Mpair.FILE) : mp.getNucmerArgs(Mpair.FILE);
			String self = (isSelf) ? (" " + mp.getSelfArgs(Mpair.FILE)) : ""; 
			
		/** create list of comparisons to run **/
			// under runDir/<p1>_to_<p2>/tmp/<px>/ with file names
			// files in <p1> are aligned with files in <p2>. 
			// For self, p1=p2
			String alignDir = resultDir + Constants.alignDir;
			Utilities.checkCreateDir(alignDir, false);
			
			for (File f1 : fh_tDir1.listFiles()) {    
				if (!f1.isFile() || f1.isHidden()) continue; 
				
				for (File f2 : fh_tDir2.listFiles()) {
					if (!f2.isFile() || f2.isHidden()) continue; 
					
					// XXX
					String aArgs = args;
					if (isSelf) { // chr files have been written as is
						if (f1.getName().equals(f2.getName())) aArgs += self;
						else if (f1.getName().compareTo(f2.getName()) < 0)  continue; 
					}
			
					AlignRun ps = new AlignRun(program, aArgs, f1, f2, alignDir, alignLogDirName);
					if (ps.isDone()) continue;
					
					alignList.add(ps);	
				}
			}
			if (alignList.size()>0) {
				Collections.sort(alignList); // CAS566 add sort and add to queue after sort
				for (AlignRun ps : alignList) {
					toDoQueue.add(ps);
					ps.setStatus(AlignRun.STATUS_QUEUED);
					if (Globals.TRACE) Globals.prt(ps.getDescription());
				}
			}
			if (nAlignDone>0) {
				if (alignList.size()==0) Utils.writeDoneFile(alignDir);
				plog.msg("Alignments to be completed: " + alignList.size()); // CAS566 add
			}
			else if (alignList.size() == 0  && !Cancelled.isCancelled()) {
				plog.msg("Warning: no alignments between projects");
				error = true; 
			}
		} 
		catch (Exception e) {ErrorReport.print(e, "Build alignments"); error=true;}
	}
	
	/**************************************
	 *  Write sequences to file; first is concatenated, second is not. Apply gene masking if requested.
	 */
	private boolean writePreprocSeq(File dir, Mproject mProj,  boolean concat, boolean isSelf, String who) {
		try {
			int projIdx = mProj.getIdx();
			String projName = mProj.getDBName();
			
			if (concat && isSelf) return true; // all get written as originals
			
			if (Cancelled.isCancelled()) return false;
			
			DBconn2 tdbc2 = new DBconn2("WriteAlignFiles-" + DBconn2.getNumConn(), dbc2);
			int nSeqs = tdbc2.executeCount("select count(*) as nseqs from pseudos " +
				" join xgroups on xgroups.idx=pseudos.grp_idx " +
				" where xgroups.proj_idx=" + projIdx);
			if (nSeqs == 0) {
				plog.msg("No sequences are loaded for " + projName + "!! (idx " + projIdx + ")");
				return false;
			}
			
			boolean geneMask = (mp.mProj1.getIdx() == mProj.getIdx()) ? 
								mp.isMask1(Mpair.FILE) : mp.isMask2(Mpair.FILE);	// CAS568 mask in pairs instead of proj
			String gmprop = (geneMask) ? "Masking non-genic sequence; " : "";
			if (geneMask) plog.msg(projName + ": " + gmprop);
				
			if (Utilities.dirNumFiles(dir) > 0)
				Utilities.clearAllDir(dir); // leaves directory, just removes files
		
		// Figure out what chromosomes to group into one file.
			Vector<Vector<Integer>> groups = new Vector<Vector<Integer>>();
		
			String msg = projName + ": ";
			if (!isSelf) {
				if (concat) msg += "Concatenating all sequences into one file for " + who; 
				else        msg += "Writing sequences into one or more files for " + who; 
			}
			else msg += "Writing separate chromosome files for self-synteny"; // CAS568
					
			// pseudos (files): grp_idx, fileName (e.g. chr3.seq), length
			// xgroups (chrs):  idx, proj_idx, chr#, fullname where grp_idx is xgroups.idx
			ResultSet rs = tdbc2.executeQuery("select grp_idx, length from pseudos " +
					" join xgroups on xgroups.idx=pseudos.grp_idx " +
					" where xgroups.proj_idx=" + projIdx + " order by xgroups.sort_order");
			groups.add(new Vector<Integer>());
			long curSize = 0, totSize = 0;
			int nOrigGrp = 0, nGrp = 0;
			while (!interrupted && rs.next()) {
				nOrigGrp++;
				int grp_idx = rs.getInt(1);
				long chrLen = rs.getLong(2);
				totSize += chrLen;
				
				if (!isSelf) {  // Unless concat=true, group up to total seq length maxFileSize.
					if (!concat && nOrigGrp > 1 && curSize+chrLen > maxFileSize) { // 60,000,000
						nGrp++;
						groups.add(new Vector<Integer>());
						curSize = 0;
					}
					groups.get(nGrp).add(grp_idx);
					curSize += chrLen;
				}
				else { // each group only has one chr in it (written like the original file)
					groups.get(nGrp).add(grp_idx);
					nGrp++;
					groups.add(new Vector<Integer>());
					curSize += chrLen;
				}
			}
			plog.msg(msg + " (" + Utilities.kMText(totSize) + ")"); // write total length here first
			
			TreeMap<Integer,TreeMap<Integer,Vector<Range>>> geneMap = new TreeMap<Integer,TreeMap<Integer,Vector<Range>>>();
			int cSize = 100000;
	
		// MASK 
			if (geneMask) {
				// Build a map of the gene annotations that we can use to mask each sequence chunk as we get it.
				// The map is sorted into 100kb bins for faster searching. 
				rs = tdbc2.executeQuery("select xgroups.idx, pseudo_annot.start, pseudo_annot.end " +
						" from pseudo_annot join xgroups on xgroups.idx=pseudo_annot.grp_idx " +
						" where pseudo_annot.type='gene' and xgroups.proj_idx=" + projIdx);
				while (!interrupted && rs.next())
				{
					int grpIdx = rs.getInt(1);
					int start = rs.getInt(2);
					int end = rs.getInt(3);
					if (!geneMap.containsKey(grpIdx)) {
						geneMap.put(grpIdx, new TreeMap<Integer,Vector<Range>>());
					}
					int cStart = (int)Math.floor(start/cSize);
					int cEnd = (int)Math.ceil(end/cSize);
					for (int c = cStart; c <= cEnd; c++) {
						int s = c*cSize;
						int e = (c+1)*cSize - 1;
						int r1 = Math.max(s, start);
						int r2 = Math.min(e, end);
						if (!geneMap.get(grpIdx).containsKey(c)) geneMap.get(grpIdx).put(c, new Vector<Range>());
						geneMap.get(grpIdx).get(c).add(new Range(r1,r2));
					}
					if (interrupted) break;
				}
			} // end mask
			
			if (interrupted) {
				tdbc2.close();
				return false;
			}
			
		/** Write files **/
			// Go through each grouping, write the preprocess file, with masking if called for.
			// Note that sequences are stored in chunks of size CHUNK_SIZE (1,000,000).
			
			for (int i = 0; i < groups.size(); i++) {
				if (groups.get(i).size() == 0) break; // last one can come up empty
				
				String fileName = (concat) ? ("_cc") : ("_f" + (i+1)); 
				
				fileName = projName + fileName + Constants.faFile;
				msg = "   " + fileName + ": ";
				long fileSize = 0; // must be long so does not become negative number
				int count=0;
				
				File f = Utilities.checkCreateFile(dir,fileName, "AM WritePreprocSeq");
				FileWriter fw = new FileWriter(f);
				
				for (int gIdx : groups.get(i)) {
					if (interrupted) break;
	
					rs = tdbc2.executeQuery("select fullname from xgroups where idx=" + gIdx);
					rs.next(); // CAS560 was first()
					String grpFullName = rs.getString(1);
					fw.write(">" + grpFullName + "\n");
					
					if (count==0) 		msg += grpFullName;
					else if (count<4) 	msg += ", " + grpFullName;
					else if (count==4)  msg += "... ";
					count++;
					
					rs = tdbc2.executeQuery("select seq from pseudo_seq2 join xgroups on xgroups.idx=pseudo_seq2.grp_idx " + 
							" where grp_idx=" + gIdx + " order by chunk asc");
					int cNum = 0;
					while (!interrupted && rs.next()){
						if (interrupted) break;
						
						int start = cNum*CHUNK_SIZE + 1; // use 1-indexed string positions for comparing to annot
						int end = (1+cNum)*CHUNK_SIZE;
						String seq = rs.getString(1);
						if (geneMask) {
							if (geneMap.containsKey(gIdx)) {
								StringBuffer seqMask = new StringBuffer(seq.replaceAll(".", "N"));
								int cs = (int)Math.floor(start/cSize);
								int ce = (int)Math.ceil(end/cSize);
								TreeMap<Integer,Vector<Range>> map = geneMap.get(gIdx);
								
								for (int c = cs; c <= ce; c++) {// check the bins covered by this chunk
									if (map.containsKey(c)) {
										for (Range r : map.get(c)) {
											int olapS = (int)Math.max(start, r.s);
											int olapE = (int)Math.min(end, r.e);
											if (olapE > olapS) {
												olapS -= start; // get the 0-indexed relative coords within this chunk
												olapE -= start;
												seqMask.replace(olapS, olapE, seq.substring(olapS, olapE));
											}
										}
									}
									else { } //System.out.println("No entries in bin " + c + " for " + gIdx );
								}
								seq = seqMask.toString();
							}				
						}
						for (int j = 0; j < seq.length(); j += 50) {
							int endw = Math.min(j+50,seq.length());
							String x = seq.substring(j, endw);
							fileSize += x.length();
							fw.write(x);
							fw.write("\n");
						}
						cNum++;
					}
				}
				plog.msg(msg + String.format(": length %,d", fileSize)); 
				fw.close(); 
			}
			tdbc2.close();
			return true;
		}
		catch (Exception e) {ErrorReport.die(e, "AlignMain.writePreproc");}
		return false;
	}
	
	/*****************************************************************/
	protected String getStatusSummary() {
		String s = "";
		
		for (AlignRun p : alignList)
			if ( p.isRunning() || p.isError() )
				s += p.toStatus() + "\n";
		
		return s;
	}
	protected int getNumRunning() {
		synchronized(threads) {
			return threads.size();
		}
	}
	protected int getNumRemaining() {
		synchronized(threads) {
			return toDoQueue.size(); // CAS566 don't add error (was +getNumErrors();) 
		}
	}
	protected int getNumCompleted() {
		return alignList.size() - getNumRunning() - getNumRemaining() - getNumErrors(); // CAS566 remove errors here
	}
	protected int getNumErrors() {
		int count = 0;
		
		for (AlignRun p : alignList)
			if (p.isError())
				count++;
		
		return count;
	}
	protected boolean notStarted() {
		return notStarted;
	}
	protected void interrupt() {
		interrupted = true;
		mCancelled = true;
		synchronized(threads) {
			for (AlignRun p : alignList)
				if (p.isRunning())
					p.interrupt();
			
			toDoQueue.clear();
			
			for (Thread t : threads)
				t.interrupt();
			threads.removeAllElements();
		}
	}
	private class Range {// for mask gene
		int s;
		int e;
		Range(int start, int end) {
			s = start; e = end;
		}
	}
}