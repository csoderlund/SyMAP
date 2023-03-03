package symap.manager;

import java.io.File;
import java.io.FileWriter;
import java.util.Vector;

import backend.Constants;
import util.ErrorCount;
import util.ErrorReport;
import util.ProgressDialog;
import util.Utilities;

/***********************************************************
 * Methods for removing projects; CAS535 moved from ManagerFrame to here
 * All ProgressDialog must call finish in order to close fw
 * No Cancel on Progress
 */
public class RemoveProj {
	private ManagerFrame frame;
	private FileWriter fw;
	private boolean bCancel=false;
	
	public RemoveProj() {}
	
	public RemoveProj(ManagerFrame frame, FileWriter fw) {
		this.frame = frame;
		this.fw = fw;
	}
	/*********************************************************
	 * Project
	 */
	// Remove Project From Database
	public void removeProjectFromDB(Mproject mp) { 
		String title = "Remove from database";
		String msg = "Remove " + mp.getDisplayName() + " from database"; // CAS513 add dbname
		int rc=0;
		
		if (!mp.hasExistingAlignments()) {
			if (!Utilities.showConfirm2(msg,msg)) return;
		}
		else {
			rc = Utilities.showConfirm3(title,msg +
				"\n\nOnly: remove project" +
				"\nAll: remove project and alignments from disk");
			if (rc==0) return;
		}
		
		ErrorCount.init();
		final int rcx = rc;
		
		final ProgressDialog progress = new ProgressDialog(frame, title, msg + " ...",  bCancel, null);
		progress.closeIfNoErrors();
		
		Thread rmThread = new Thread() {
			public void run() {
				boolean success = true;
				progress.appendText(msg);
				
				try {
					mp.removeProjectFromDB();
					if (rcx==2) removeAllAlignFromDisk(mp, false, progress); 
				}
				catch (Exception e) {
					success = false;
					ErrorReport.print(e, "Remove project");
				}
				finally {
					progress.finish(success);
				}
			}
		};	
		progress.start( rmThread ); 
	}
	// Remove Project From Disk
	public void removeProjectFromDisk(final Mproject mProj) {
		String title = "Remove project from disk";
		String msg =  "Remove project from disk " + mProj.getDBName();
		if (!Utilities.showConfirm2(title,msg)) return;
		
		ErrorCount.init();
		final ProgressDialog progress = new ProgressDialog(frame,  title, msg + "' ...", bCancel, fw);
		progress.closeIfNoErrors();
		
		Thread rmThread = new Thread() {
			public void run() {
				boolean success = true;
				progress.appendText(msg);
				
				try {
					try { Thread.sleep(1000);}// CAS505 see if this stops it from crashing on Linux in progress.start
					catch(Exception e){}

					removeAllAlignFromDisk(mProj, true, progress); // CAS534 remove top dir
					
					String path = Constants.seqDataDir + mProj.getDBName();
					
					File f = new File(path);
					Utilities.deleteDir(f);
					if (f.exists()) f.delete();// CAS505 not removing topdir on Linux, so try again
				}
				catch (Exception e) {
					success = false;
					ErrorReport.print(e, "Remove project from disk");
				}
				finally {
					progress.finish(success);
				}
			}
		};
		progress.start( rmThread ); // crashed on linux
	}
	
	/******************************************************************
	 * Aligns
	 */
	// Clear Pair
	public void removeClearPair(final Mpair mp, final Mproject p1, final Mproject p2) {
		String title = "Clear alignment pair";
		String msg = "Clear pair " + p1.getDBName() + " to " + p2.getDBName();
		int rc = Utilities.showConfirm3(title, msg + // CAS515 add option to only clear from database
				"\n\nOnly: remove synteny from database only" +
				"\nAll: remove synteny from database and alignments from disk");
		if (rc==0) return;
		
		ErrorCount.init();
		
		final ProgressDialog progress = new ProgressDialog(frame, title, msg, bCancel, fw);
		progress.closeIfNoErrors();
		
		Thread clearThread = new Thread() {
			public void run() {
				boolean success = true;
				progress.appendText(msg);
				
				try {
					try {Thread.sleep(1000);}
					catch(Exception e){}
					
					mp.removePairFromDB();
					if (rc==2) {
						String path = Constants.getNameResultsDir(p1.getDBName(),  p2.getDBName());
						System.out.println("Remove alignments from " + path);
						removeAlignFromDisk(new File(path), false);
					}
				}
				catch (Exception e) {
					success = false;
					ErrorReport.print(e, "Clearing alignment");
				}
				finally {
					progress.finish(success);
				}
			}
		};
		progress.start( clearThread ); 
	}
	// Reload Project
	public void removeAllAlignFromDisk(Mproject p) {// Reload Project 
		removeAllAlignFromDisk(p, false, null);
	}

    // called on Remove Project From DB (true) and Reload Project (false)
	private boolean removeAllAlignFromDisk(Mproject p, boolean rmTopDir, ProgressDialog progress) {
		try {
			String msg = (rmTopDir) ? "Remove directory " : "Removing alignments ";
			if (progress!=null)
				progress.appendText(msg + "from disk - " + p.getDBName());		
			
			File top = new File(Constants.getNameResultsDir()); // check seq_results
			
			String projTo = Constants.projTo;
			
			if (top == null  || !top.exists()) return true;
			
			Vector<File> alignDirs = new Vector<File>();
			
			for (File f : top.listFiles()) {
				if (f.isDirectory()) {
					if	(f.getName().startsWith(p.getDBName() + projTo) ||
						 f.getName().endsWith(projTo + p.getDBName())) {
						alignDirs.add(f);
					}
				}
			}
			if (alignDirs.size() == 0) return true;
			
			for (File f : alignDirs) {
				removeAlignFromDisk(f, rmTopDir);
			}
			return true;
		} catch (Exception e) {ErrorReport.print(e, "Remove all alignment files"); return false;}
	}
	// clear pair and remove project from disk
	private void removeAlignFromDisk(File f, boolean rmTopDir) { // For project
	try {
		if (rmTopDir) { // only happens on remove project from disk
			System.out.println("    remove " + f.getName());
			Utilities.deleteDir(f);
			if (f.exists()) f.delete();
		}
		else { // leave params file
			File f1 = new File(f.getAbsoluteFile() + "/" + Constants.alignDir);
			if (f1.exists()) {
				System.out.println("    remove " + f.getName());
				Utilities.deleteDir(f1);
				if (f1.exists()) f1.delete();
			}
			File f2 = new File(f.getAbsoluteFile() + "/" + Constants.finalDir);
			Utilities.deleteDir(f2);
			if (f1.exists()) f2.delete();
		}
	} catch (Exception e) {ErrorReport.print(e, "Remove alignment files"); }
	}
	
}
