package symap.manager;

import java.io.File;
import java.io.FileWriter;
import java.util.Vector;

import backend.Constants;
import symap.Globals;
import util.ErrorReport;
import util.Utilities;
import util.Popup;
import util.FileDir;

/***********************************************************
 * Methods for removing projects
 */
public class RemoveProj {
	final static String sp= "   ";
	private FileWriter loadLogFW = null;
	
	protected RemoveProj(FileWriter fw) {this.loadLogFW = fw;}
	
	private void close() {
		try {
			if (loadLogFW!=null) {loadLogFW.close(); loadLogFW=null;}
		}
		catch (Exception e){}
	}
	/***************************************************************
	 * Remove project from database: Manager database link
	 */
	protected void removeProjectDB(Mproject pj) { 
		String msg = "Remove " + pj.getDisplayName() + " from database"; 
		
		if (Popup.showConfirm2("Remove " + pj.getDisplayName(),msg)) {
			pj.removeProjectFromDB();
			try {
				loadLogFW.append("Remove project " + pj.getDisplayName() + " from database " + Utilities.getDateTime()+ "\n");
			} catch (Exception e) {}
		}
		close();
	}

	/**************************************************************
	 * Remove project from disk: Manager disk link 
	 */
	protected void removeProjectDisk(Mproject pj) {
		String path = Constants.seqDataDir + pj.getDBName();
		String title = "Remove project " + pj.getDBName();
		
		try {
			int rc;
			if (pj.hasAlignBlast(false)) { // if directory exists, even if no /align
				String res = (pj.alignStr.equals("")) ? "result" : "result (" +pj.alignStr + ")";	
				rc = Popup.showConfirm3(title,
						"Only: Remove " + res + " directories from disk" + // may want to remove alignments and not project
					  "\nAll:  Remove results and project directory from disk");
				if (rc==0) {close(); return;}
				
				System.out.println("Confirming removal of " +  pj.getDisplayName() + "  directories from disk...."); 
				removeAllAlignFromDisk(pj, true); // remove top directory; prints removals
				
				if (rc!=2) {close(); return;}
			}
			if (!Popup.showConfirm2(title, "Remove from disk: \n   " + path)) {close(); return;}
			
			System.out.println("Remove project from disk: \n   " + path);
			File f = new File(path);
			FileDir.deleteDir(f);
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
			String msg = "Reload project " + pj.getDisplayName()  + "\n" + pj.getLoadParameters(); 
			
			if (!pj.hasAlignBlast(true)) { // only care if there is a /align or /blast
				if (!Popup.showConfirm2("Reload project",msg)) {close(); return false;}
			}
			else {
				String res = (pj.alignStr.equals("")) ? "result files " : pj.alignStr + " files ";	
				int rc = Popup.showConfirm3("Reload project",msg +
					"\n\nOnly: Reload project only" +
					  "\nAll:  Reload project and remove " + res + " from disk");
				if (rc==0) {close(); return false;}
				
				if (rc==2) {
					System.out.println("Confirming removals of " +  pj.getDisplayName() + " " + res + " from disk...."); 
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
	 * Remove Project From Disk (true);  -- remove directory
	 * Reload Project (false) -- only remove files; leave params.txt
	 */
	private boolean removeAllAlignFromDisk(Mproject pj, boolean rmTopDir) {
		try {
			File top = new File(Constants.getNameResultsDir()); // check seq_results
			
			if (top == null  || !top.exists()) return true;
			
			Vector<File> alignDirs = new Vector<File>();
			String pStart = pj.getDBName() + Constants.projTo, pEnd = Constants.projTo + pj.getDBName();
			
			for (File f : top.listFiles()) {
				if (f.isDirectory()) {
					if	(f.getName().startsWith(pStart) || f.getName().endsWith(pEnd)) {
						alignDirs.add(f);
					}
				}
			}
			if (alignDirs.size() == 0) return true;
			
			for (File f : alignDirs) {
				String fn = Constants.seqRunDir + f.getName();
				String msg = (rmTopDir) ? "Remove directory: \n   " + fn : "Remove files in: \n   " + fn;
				if (!Popup.showConfirm2("Remove from disk", msg)) {
					System.out.println(sp + "cancel removal of " + f.getName());
					continue;
				}
				
				if (rmTopDir) {
					System.out.println(sp + "remove " + f.getName());
					loadLogFW.append("Remove align directory " + f.getName()  + "   " + Utilities.getDateTime()+ "\n");
					
					FileDir.deleteDir(f);
					if (f.exists()) f.delete();
				}
				else {// Remove MUMmer files   Remove Blast files   Remove MUMmer/Blast files
					System.out.println(sp + "remove " + pj.alignStr + " files from: " + f.getName()); 
					removeAlignFromDir(f);
				}
			}
			return true;
		} 
		catch (Exception e) {ErrorReport.print(e, "Remove all alignment files"); return false;}
	}
	/******************************************************************
	 * Clear pair button 
	 **********************************/
	protected void removeClearPair(Mproject p1, Mproject p2, Mpair mp) {// does use fw
		String msg = "Clear pair " + p1.getDBName() + " to " + p2.getDBName();
		String path = Constants.getNameResultsDir(p1.getDBName(), p2.getDBName());
		
		int rc = 2;
		if (mp.isPairInDB()) {
			if (mp.alignStr==null) {
				if (!Popup.showConfirm2("Clear pair", msg + "\n\nOnly: remove pair from database")) return;
				rc = 1;
			}
			else rc = Popup.showConfirm3("Clear pair", msg + "\n\nOnly: remove pair from database" +
				             "\nAll: remove pair and " + mp.alignStr + " for this pair from disk ");
		}
		else { 										
			if (!Popup.showConfirm2("Clear alignments",msg 
					+ "\nRemove " + mp.alignStr + " for this pair from disk")) {close(); return;}
		}
		if (rc==0) {close(); return;}
				
		try {
			if (rc==2) {
				msg = "Remove " + mp.alignStr + " in:\n   " + path;
				if (!Popup.showConfirm2("Remove from disk", msg)) {
					System.out.println(sp + "cancel removal of " + path);
					return;
				}
				
				Globals.prt("Remove " + mp.alignStr + " from " + path);
				removeAlignFromDir(new File(path)); 
			}
			if (mp.isPairInDB()) {
				mp.removePairFromDB(true); // True = redo numHits; 
				Globals.prt("   Finish remove " + p1.getDBName() + "-" + p2.getDBName() + " from database"); 
			}	
			close();
		}
		catch (Exception e) {ErrorReport.print(e, "Clearing alignment");}
	}
	/******************************************************************
	 * Remove /align, /results and params_align_used
	 * --removeAllAlignFromDisk for Reload Project
	 * --Clear pair 
	 * Remove /align, /blast, /final and used_align_params
	 */
	private void removeAlignFromDir(File f) { 
	try {
		File f1 = new File(f.getAbsoluteFile() + "/" + Constants.alignDir);
		if (f1.exists()) {
			FileDir.deleteDir(f1);
			if (f1.exists()) f1.delete();
		}
		f1 = new File(f.getAbsoluteFile() + "/" + Constants.blastDir);
		if (f1.exists()) {
			FileDir.deleteDir(f1);
			if (f1.exists()) f1.delete();
		}
		
		File f2 = new File(f.getAbsoluteFile() + "/" + Constants.finalDir);
		FileDir.deleteDir(f2);
		if (f2.exists()) f2.delete();
		
		File f3 = new File(f.getAbsoluteFile() + "/" + Constants.usedFile);
		if (f3.exists()) f3.delete();
	} 
	catch (Exception e) {ErrorReport.print(e, "\nRemove alignment files"); }
	}
}
