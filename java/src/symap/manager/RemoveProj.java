package symap.manager;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileWriter;
import java.util.Vector;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import backend.Constants;
import backend.Utils;
import symap.Ext;
import symap.Globals;
import util.ErrorReport;
import util.Popup;
import util.ProgressDialog;
import util.Utilities;
import util.FileDir;
import util.Jcomp;

/***********************************************************
 * RemoveProj
 * Methods for removing seq_results/proj1_to_proj2 (fast - not in progressDialog)
 * Remove from DB occurs in removeClearPair and in backend.DoLoadProj using progressDialog
 * ClearPair asks specifically about /align and /blast
 * Remove project from disk just removes them
 */
public class RemoveProj {
	private static final String sp= "   ", spp = "      "; // spp same as 
	private static final int both=1, align=2, blast=3; // for clear pair
	
	private boolean bPrtOutLog=false;
	private FileWriter logFW = null; // can be load log or pairs log

	protected RemoveProj(FileWriter fw) {this.logFW = fw;}
	
	/**************************************************************
	 * Remove project from disk: Manager project link
	 */
	protected void removeProjectResultsDisk(Mproject mProj) {
	try {
		String name = mProj.getDBName();
		String path = Constants.seqDataDir + name;
		
		int cntDir = mProj.calcAlignBlast(false); // false : top directory only
		
		// confirm with final msg
		String finalMsg         = "Remove project: \n" + spp + path;
		if (cntDir>0) finalMsg += "\n" + 
		                          "Remove results:\n" + mProj.alignStr;
		if (cntDir==0) {
			if (!Popup.showConfirm2("Remove from disk", finalMsg)) return;  // Cancel
		}
		else { 
			if (!Popup.showConfirm2("Remove from disk", finalMsg)) return;// cancel
		}
		if (!Popup.showConfirm2("Final Confirm", "FINAL CONFIRM\n" + finalMsg)) return;  // cancel
			
		// Remove
		prtOutLog("Remove project from disk: " + name +  "          " +  Utilities.getDateTime(), true);
		if (cntDir>0) {
			removeResults(mProj, true); // true - topdir; prints directories removed
		}
		prtOutLog(sp + "Remove project: " + path, true);
		File f = new File(path);
		FileDir.deleteDir(f);
		if (f.exists()) f.delete();// not removing topdir on Linux, so try again
	}
	catch (Exception e) {ErrorReport.print(e, "Remove project from disk");}		
	finally {fwClose(); }
	}
	/****************************************************
	 * Reload Project: remove align directories?
	 * return false: do not reload
	 */
	protected boolean removeResultsDiskReload(Mproject mProj) {
	try {
		String name = mProj.getDisplayName();
		String msg = "Reload project " + name  + "\n" + mProj.getLoadParameters(); 
		int cntDir = mProj.calcAlignBlast(true); // calcs alignStr; only care if there is a /align or /blast
		
		if (cntDir==0) { 
			if (!Popup.showConfirm2("Reload project",msg)) return false; // cancel
			else                                           return true;
		}
		int rc = Popup.showConfirm3("Reload project",
			msg + "\n\n" +
			"Only: Reload project\n" +
			"All:    Plus, remove results: \n" + mProj.alignStr + "\n"
			); 
		Globals.xprt("rc " + rc);
		if (rc==0) return false; // Cancel
		if (rc==1) return true;  // reload only
		
		if (Popup.showConfirm2("Final Confirm", "FINAL CONFIRM\n" 
		                                        + sp + "Remove directories: \n" 
				                                + mProj.alignStr)) {
			removeResults(mProj, false); // do not remove topDir
		}
		return true;
	}
	catch (Exception e) {ErrorReport.print(e, "Remove project from disk"); return false;}	
	finally {fwClose();}
	}
	/*********************************************************/
	protected void removeClearPair(ManagerFrame frame, Mproject mProj1, Mproject mProj2, Mpair mp){// does use fw
		new ClearPair(frame, mProj1, mProj2, mp); 
	}
	/******************************************************************
	 * Clear pair button 
	 **********************************/	
	private class ClearPair extends JDialog {
		private static final long serialVersionUID = 1L;
		
		private ClearPair(ManagerFrame frame, Mproject mProj1, Mproject mProj2, Mpair mp) {
			super(frame,"Clear Pair", true); 
			setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE); 
			addWindowListener(new WindowAdapter() {
				public void windowClosed(WindowEvent e) {}
			});
			
			mp.setAligns(); // sets alignStr
			String msgPair =  mProj1.getDisplayName() + "-" + mProj2.getDisplayName();
			String pathPair = Constants.getNameResultsDir(mProj1.strDBName, mProj2.strDBName); 
	
			// Clear Pair is usually done for change of parameters; aligns can remain
			String align = Constants.getAlignDir(), blast = Constants.getBlastDir();
			chkPair = Jcomp.createCheckBox("Remove pair from database", "Remove pair from database", 
					mp.isPairInDB());
			chkAlign = Jcomp.createCheckBox("Remove pair " + align + " directory", 
					"Remove " + pathPair + align + " from disk", false); 
			chkBlast = Jcomp.createCheckBox("Remove pair " + blast + " directory", 
					"Remove " + pathPair + blast + " from disk", false);
			
			chkPair.setEnabled(mp.isPairInDB());
			chkAlign.setEnabled(mp.hasAlignDir());
			chkBlast.setEnabled(mp.hasBlastDir());
			
			okButton = Jcomp.createButton("Confirm","Remove selected items"); 
			okButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent arg0) {
					setVisible(false);
					confirm(frame, mp, msgPair, pathPair);
				}
			});
			cancelButton = Jcomp.createButton("Cancel", "Cancel removal");
			cancelButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent arg0) {
					setVisible(false);
				}
			});
			
			JPanel page = Jcomp.createPagePanel();
			page.add(Jcomp.createLabel("Pair:    " + msgPair)); page.add(Box.createVerticalStrut(5));
			page.add(chkPair); 					page.add(Box.createVerticalStrut(10));
			
			page.add(Jcomp.createLabel("Results: " + pathPair + "   ")); page.add(Box.createVerticalStrut(5));
			page.add(chkAlign); 				page.add(Box.createVerticalStrut(5));
			page.add(chkBlast); 				page.add(Box.createVerticalStrut(15));
			
			JPanel row = Jcomp.createRowPanel();
			row.add(Box.createHorizontalStrut(15));
			if (Ext.isLinux()) { // Make them the same order as the popup.confirm2
				row.add(cancelButton); row.add(Box.createHorizontalStrut(15));
				row.add(okButton);
			}
			else {
				row.add(okButton); row.add(Box.createHorizontalStrut(15));
				row.add(cancelButton);
			}
			page.add(row);
			page.add(Box.createVerticalStrut(5)); // for linux 
			add(page);
			
			pack();
			setModal(true);
			setBackground(Color.white);
			setResizable(false);
			setAlwaysOnTop(true); 										// doesn't work on Ubuntu
			setLocationRelativeTo(null);	
			setVisible(true);
		}
		/***********************************************/
		private void confirm(ManagerFrame frame, Mpair mp, String msgPair, String rmPath) {
		try {
			if (!chkPair.isSelected() && !chkAlign.isSelected() && !chkBlast.isSelected()) return;
			
			int drc=0;
			if (chkAlign.isSelected() && chkBlast.isSelected()) {drc = both; }
			else if (chkAlign.isSelected())                     {drc = align;}
			else if (chkBlast.isSelected())                     {drc = blast;}
			
			// Final confirm
			String msg="";
			if (chkPair.isSelected() && mp.isPairInDB()) 
				msg += "    Remove Pair from database:  \n        " + msgPair  + "\n";
			if (drc>0)  {                              
				msg += "    Remove from disk: ";
				if (chkAlign.isSelected()) msg += "\n        " + rmPath+Constants.alignDir;
				if (chkBlast.isSelected()) msg += "\n        " + rmPath+Constants.blastDir;
			}
			if (!msg.isBlank()) {
				if (!Popup.showConfirm2("Final Confirm", "FINAL CONFIRM\n" + msg)) return; // Cancelled
			}
			remove(frame, mp, drc, msgPair, rmPath);
		}
		catch (Exception e) {ErrorReport.print(e, "Clearing alignment");}
		}
		/*********************************************/
		private void remove(ManagerFrame frame, Mpair mp, int drc, String msgPair, String path) {
			// No cancel; logFW closed in plog.finish()
			final ProgressDialog plog = new ProgressDialog(frame, "Clear Pair", "Pair: " + msgPair, logFW); 
						
			Thread loadThread = new Thread() {
				public void run() {
					boolean success = true;
					
					try {
						plog.msg("Clear pair: " + msgPair + "        " + Utilities.getDateTime());
						if (drc>0) {                                   // remove directories blast, align or both
							rmResultsSubDir(new File(path), drc); 
						}
						if (chkPair.isSelected() && mp.isPairInDB()) { // remove from database
							plog.msg("   Remove pair from database ");
							plog.msgPop("   Please be patient....");
							mp.removePairFromDB(true); // True = redo numHits; 
						}	
					}
					catch (Exception e) {
						success = false;
						ErrorReport.print(e, "Loading project");
					}
					finally {
						plog.finish(success);
					}
				}
			};
			plog.start( loadThread ); // blocks until thread finishes or user cancels	
		}
		
		JButton okButton, cancelButton;
		JCheckBox chkPair, chkAlign, chkBlast;
	}
	/******************************************************************
	 * Remove Project From Disk (true);  -- remove top directories 
	 * Reload Project (false)            -- only remove /align and /blast; leave params.txt
	 */
	private boolean removeResults(Mproject mProj, boolean rmTopDir) {
		try {
			File top = new File(Constants.getNameResultsDir()); // check seq_results
			if (top == null  || !top.exists()) return true;
			
			// get all seq_result directories with this project name; same as done in Mproject.calcAlignBlast
			Vector<File> alignDirs = new Vector<File>();
			String dbName = mProj.getDBName();
			String pStart = dbName + Constants.projTo, pEnd = Constants.projTo + dbName;
			
			for (File f : top.listFiles()) {
				if (f.isDirectory()) {
					String dir = f.getName(); // gets final directory in path
					if	(dir.startsWith(pStart) || dir.endsWith(pEnd)) {
						alignDirs.add(f);
					}
				}
			}
			if (alignDirs.size() == 0) return true;
			
			// go through directories
			for (File aDir : alignDirs) {
				if (rmTopDir) {
					prtOutLog(sp + "Remove results: " + Utils.dataPathOnly(aDir.getAbsolutePath()), true);
					FileDir.deleteDir(aDir);
					if (aDir.exists()) aDir.delete();
				}
				else {// Both align and blast will be removed - Reload project usually has sequence change
					rmResultsSubDir(aDir, both); // prints what it deletes
				}
			}
			return true;
		} 
		catch (Exception e) {ErrorReport.print(e, "Remove all alignment files"); return false;}
	}
	/**********************************************
	 * Clear Pair - which can be 1,2,3
	 * Reload Project, Remove project from Disk - which is 3
	 */
	private void rmResultsSubDir(File f, int which) { 
	try {
		if (which==align || which==both) {
			File f1 = new File(f.getAbsoluteFile() + "/" + Constants.alignDir);
			if (f1.exists()) {
				FileDir.deleteDir(f1);
				if (f1.exists()) f1.delete();
				prtOutLog(sp + "Remove results: " +  Utils.dataPathOnly(f1.getAbsolutePath()), true);
			}
			File f3 = new File(f.getAbsoluteFile() + "/" + Constants.usedFile);
			if (f3.exists()) f3.delete();
		}
		if (which==blast || which==both) {
			File f1 = new File(f.getAbsoluteFile() + "/" + Constants.blastDir);
			if (f1.exists()) {
				FileDir.deleteDir(f1);
				if (f1.exists()) f1.delete();
				prtOutLog(sp + "Remove results: " + Utils.dataPathOnly(f1.getAbsolutePath()), true);
			}
		}
	} 
	catch (Exception e) {ErrorReport.print(e, "\nRemove alignment files"); }
	}
	/*******************************************************************/
	private void fwClose() {
		try {
			if (logFW!=null) {
				if (bPrtOutLog) logFW.append(Globals.lastLine);
				logFW.close(); logFW=null;
			}
		}
		catch (Exception e){}
	}
	private void prtOutLog(String msg, boolean toFile) {
		bPrtOutLog=true;
		System.out.println(msg);
		if (toFile && logFW!=null) {
			try {
				logFW.append(msg + "\n");
			} catch(Exception e) {}
		}
	}
}
