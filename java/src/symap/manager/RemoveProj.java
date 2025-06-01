package symap.manager;

import java.io.File;
import java.io.FileWriter;
import java.util.Vector;

import backend.Constants;
import util.ErrorReport;
import util.Utilities;

/***********************************************************
 * Methods for removing projects
 * CAS568 removed threads and progressLog; popups and stdout out probably regards state of DB/disk
 * Note: the protected methods do similar things, but the messages are tailored
 */
public class RemoveProj {
	final static String sp= "   ";
	private FileWriter fw = null;
	
	public RemoveProj(FileWriter fw) {this.fw = fw;}
	private void close() {
		try {
			if (fw!=null) {fw.close(); fw=null;}
		}
		catch (Exception e){}
	}
	/***************************************************************
	 * Remove project from database: Manager database link
	 */
	protected void removeProjectDB(Mproject pj) { 
		String msg = "Remove " + pj.getDisplayName() + " from database"; 
		
		if (Utilities.showConfirm2("Remove from database",msg)) {
			pj.removeProjectFromDB();
			try {
				fw.append("Remove project " + pj.getDisplayName() + " from database " + Utilities.getDateTime()+ "\n");
			} catch (Exception e) {}
		}
		close();
	}

	/**************************************************************
	 * Remove project from disk: Manager disk link 
	 */
	protected void removeProjectDisk(Mproject pj) {
		String path = Constants.seqDataDir + pj.getDBName();
		String title = "Remove project";
		String msg   = "Remove project " + pj.getDBName(); // getDBName is seq/directory
		try {
			int rc;
			if (pj.hasExistingAlignments(false)) { // if directory exists, even if no /align
				rc = Utilities.showConfirm3(title,msg +
						"\n\nOnly: Remove alignment directories from disk" + // may want to remove alignments and not project
						"\nAll:  Remove alignments and project directory from disk");
				if (rc==0) {close(); return;}
				
				System.out.println("Confirming removal of " +  pj.getDisplayName() + " alignments directories from disk...."); 
				removeAllAlignFromDisk(pj, true); // remove top directory; prints removals
				
				if (rc!=2) {close(); return;}
			}
			// project directory
			if (!Utilities.showConfirm2(title,msg + "\n\nRemove from disk:\n   " + path)) {close(); return;}
			
			System.out.println("Remove project from disk: " + path);
			File f = new File(path);
			Utilities.deleteDir(f);
			if (f.exists()) f.delete();// not removing topdir on Linux, so try again
			
			close();
		}
		catch (Exception e) {ErrorReport.print(e, "Remove project from disk");}		
	}
	/**********************************************************
	 * Reload project: ManagerFrame link
	 */
	protected boolean reloadProject(Mproject pj) {
		try {
			String msg = "Reload project " + pj.getDisplayName();
			if (!pj.hasExistingAlignments(true)) { // only care if there is a /align
				if (!Utilities.showConfirm2("Reload project",msg)) {close(); return false;}
			}
			else {
				int rc = Utilities.showConfirm3("Reload project",msg +
					"\n\nOnly: Reload project only" +
					  "\nAll:  Reload project and remove alignments from disk");
				if (rc==0) {close(); return false;}
				
				if (rc==2) {
					System.out.println("Confirming removals of " +  pj.getDisplayName() + " alignments from disk...."); 
					removeAllAlignFromDisk(pj, false); // do not remove topDir
				}
			}
			pj.removeProjectFromDB(); // do not need finish method because starts loading after this
			close();
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, "reload project"); return false;}
	}
	/******************************************************************
	 * Clear pair button 
	 **********************************/
	protected void removeClearPair(Mproject p1, Mproject p2, Mpair mp) {// does use fw
		String msg = "Clear pair " + p1.getDBName() + " to " + p2.getDBName();
		int rc = 2;
		if (mp.isPairInDB()) {
			rc = Utilities.showConfirm3("Clear pair", msg + 
				"\n\nOnly: remove synteny from database" +
				"\nAll: remove synteny and alignments from disk for this pair");
		}
		else { 										// CAS568 only align confirm if not in DB 
			if (!Utilities.showConfirm2("Clear alignments",msg 
					+ "\nRemove alignments for this pair from disk")) {close(); return;}
		}
		if (rc==0) {close(); return;}
				
		try {
			if (rc==2) {
				String path = Constants.getNameResultsDir(p1.getDBName(),  p2.getDBName());
				msg = "Remove MUMmer files in:\n   " + path;
				if (!Utilities.showConfirm2("Remove from disk", msg)) {// CAS565 add confirm
					System.out.println(sp + "cancel removal of " + path);
					return;
				}
				
				System.out.println("Remove alignments from " + path);
				
				removeAlignFromDir(new File(path)); 
			}
			if (mp.isPairInDB()) mp.removePairFromDB(true); // True = redo numHits; CAS566 add true
			
			close();
		}
		catch (Exception e) {ErrorReport.print(e, "Clearing alignment");}
	}
	/******************************************************************
	 * Remove Project From Disk (true); 
	 * Reload Project (false)
	 */
	private boolean removeAllAlignFromDisk(Mproject p, boolean rmTopDir) {
		try {
			File top = new File(Constants.getNameResultsDir()); // check seq_results
			
			if (top == null  || !top.exists()) return true;
			
			Vector<File> alignDirs = new Vector<File>();
			String pStart = p.getDBName() + Constants.projTo, pEnd = Constants.projTo + p.getDBName();
			
			for (File f : top.listFiles()) {
				if (f.isDirectory()) {
					if	(f.getName().startsWith(pStart) || f.getName().endsWith(pEnd)) {
						alignDirs.add(f);
					}
				}
			}
			if (alignDirs.size() == 0) return true;
			
			for (File f : alignDirs) {
				String msg = (rmTopDir) ? "Remove directory: " : "Remove MUMmer files in: ";
				msg +=  "\n   " + Constants.seqRunDir + f.getName();
				
				if (!Utilities.showConfirm2("Remove from disk", msg)) {// CAS565 add confirm
					System.out.println(sp + "cancel removal of " + f.getName());
					continue;
				}
				if (rmTopDir) {
					System.out.println(sp + "remove " + f.getName());
					fw.append("Remove align directory " + f.getName()  + "   " + Utilities.getDateTime()+ "\n");
					
					Utilities.deleteDir(f);
					if (f.exists()) f.delete();
				}
				else {
					System.out.println(sp + "remove MUMmer files from: " + f.getName()); // not printed for clear
					removeAlignFromDir(f);
				}
			}
			return true;
		} 
		catch (Exception e) {ErrorReport.print(e, "Remove all alignment files"); return false;}
	}
	
	/******************************************************************
	 * Remove /align, /results and params_align_used
	 * --removeAllAlignFromDisk for Reload Project
	 * --Clear pair 
	 */
	private void removeAlignFromDir(File f) { 
	try {
		File f1 = new File(f.getAbsoluteFile() + "/" + Constants.alignDir);
		if (f1.exists()) {
			Utilities.deleteDir(f1);
			if (f1.exists()) f1.delete();
		}
		else System.out.println(sp + "MUMmer files " + f.getName() + " already deleted");
		
		File f2 = new File(f.getAbsoluteFile() + "/" + Constants.finalDir);
		Utilities.deleteDir(f2);
		if (f2.exists()) f2.delete();
		
		File f3 = new File(f.getAbsoluteFile() + "/" + Constants.usedFile);
		if (f3.exists()) f3.delete();
	} 
	catch (Exception e) {ErrorReport.print(e, "\nRemove alignment files"); }
	}
}
