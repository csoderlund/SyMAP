 package backend;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.util.Date;
import java.util.TreeMap;

import javax.swing.JFileChooser;
import javax.swing.JFrame;

import backend.synteny.SyntenyMain;
import database.DBconn2;
import symap.manager.Mpair;
import symap.manager.Mproject;
import symap.manager.SumFrame;
import symap.Ext;
import symap.manager.ManagerFrame;
import util.Cancelled;
import util.ErrorCount;
import util.ErrorReport;
import util.ProgressDialog;
import util.Utilities;
import util.Popup;

/**********************************************************
 * DoAlignSynPair; Calls AlignMain, AnchorMain and SyntenyMain; Called from ManagerFrame
 *
 * When called, DoLoadProj has been executed:
 * 		the Sequence and Annotation have been loaded 
 * 		the GeneNum assigned
 * Calls AlignMain 
 * Calls AnchorMain
 * 		calls AnchorMain1 or AnchorMain2, which assign hits to genes
 * 			Note: Demo1 has many genes with identical coords but diff strands; 
 * 			both algorithms seem to be inconsistent as to which is assigned as the major hit.
 * 			It makes a difference to collinear, as only the major are processed.
 * 		computes hit# and assigns hitcnt to genes (for Query Singles)
 * Calls SyntenyMain
 * Calls AnchorPost which creates collinear sets from blocks
 */

public class DoAlignSynPair extends JFrame {
	private static final long serialVersionUID = 1L;
	private DBconn2 dbc2;
	private Mpair mp;
	
	public void run(ManagerFrame frame, DBconn2 dbc2, Mpair mp,  int maxCPUs, boolean bAlignDone) {
		this.dbc2 = dbc2;
		this.mp = mp;
		
		Mproject mProj1 = mp.mProj1;
		Mproject mProj2 = mp.mProj2;
		String dbName1 = mProj1.getDBName();
		String dbName2 = mProj2.getDBName();
		String toName =  (mProj1 == mProj2) ? dbName1 + " to self (includes mirrored hits)": dbName1 + " to " + dbName2; 
		
		FileWriter syLogFW = symapLog(mProj1,mProj2);
		String alignLogDir = buildLogAlignDir(mProj1,mProj2);

		String msg = (mProj1 == mProj2) ? "Self-synteny "  + dbName1 + " ..." : "Synteny " + toName + " ...";
		
		final ProgressDialog diaLog = new ProgressDialog(this, "Running Synteny", msg, true, syLogFW); // write version and date
		
		String v = (Constants.VERBOSE) ? "   Verbose" : "    !Verbose"; 
		diaLog.msgLog(">>> " + toName + v);
		ErrorCount.init();
		
		if (Constants.NUMHITS_ONLY) {// hidden
			numHitsOnly(diaLog, mProj1, mProj2);
			return;
		}
		if (Constants.LOAD_BLOCKS) { // hidden 
			loadBlocks(diaLog, mProj1, mProj2);
			return;
		}
		long timeStart = Utils.getTime();
		final boolean bLoadBlast = mp.isBlast(Mpair.FILE);
		final boolean bOnly = mp.bSynOnly || mp.bCoSet || mp.bPseudo; // can't select if !isPairInDB
		
		if (mp.isPairInDB()) {
			if (bOnly) { // NO remove on pseudo and collinear
				if (mp.bSynOnly) {
					diaLog.msg("Remove synteny from database");
					mp.removeSyntenyFromDB(); // isBlastAdd uses existing hits
				}
			}
			else {
				if (bLoadBlast) {
					diaLog.msg("Remove synteny from database");
					mp.removeSyntenyFromDB(); // isBlastAdd uses existing hits
				}
				else {
					diaLog.msg("Remove pair from database");
					mp.removeAndSetIdx(); 			 // Remove existing and restart, sets projIdx; 
				}
			}	
		}
		else mp.setIdx();
		
		String chgMsg="";
		if (bOnly) {
			if (mp.bSynOnly)     chgMsg = "Only Synteny\n" + mp.getChgSynteny(Mpair.FILE);
			else if (mp.bCoSet)  chgMsg = "Only Collinear Sets\n" + mp.getChgCoSet(Mpair.FILE);
			else if (mp.bPseudo) chgMsg = "Only Pseudo Numbers";
			diaLog.msg(chgMsg);
		}
		else {
			if (bAlignDone) chgMsg = mp.getChgClustSyn(Mpair.FILE);
			else            chgMsg = mp.getChgAllParams(Mpair.FILE);  
			diaLog.msg("Pair Parameters" + "\n" + chgMsg); 
		}
		
		if (!bLoadBlast) { 				// CAS592 add; wasn't showing proper message and outputting lots of ...
			String mummerPath = Ext.getMummerPath(); 
			if (mummerPath.equals("")) {
				Popup.showErrorMsg(Ext.getMummerPathNoChk() + " does not exist\nCannot continue");
				return;
			}
		}
		
		final UseBlast loadBlast = (bLoadBlast)  ? new UseBlast(dbc2, diaLog, mp) : null;
		final AlignMain aligner =  (!bLoadBlast) ? new AlignMain(dbc2,diaLog, mp,maxCPUs,alignLogDir) : null;
		
		final AnchorMain anchors = new AnchorMain(dbc2, diaLog, mp );
		
		final SyntenyMain synteny = new SyntenyMain(dbc2, diaLog, mp );
		
		/** Thread for running MUMmer and updating dialog **/
		final Thread mummerThread = new Thread() {
			public void run() {
				if (aligner==null) return; // returns if MUMmer not going to run
								
				while (aligner.notStarted() && !Cancelled.isCancelled()) Utils.sleep(1000);
				if (aligner.getNumRemaining() == 0 && aligner.getNumRunning() == 0) return;
				
				if (Cancelled.isCancelled()) return;
				
				// Alignments\n list errors and current\nStatus
				String status = "\nAlignments:  running " + aligner.getNumRunning() + ", completed " + aligner.getNumCompleted();
				if (aligner.getNumRemaining()>0) status += ", queued " + aligner.getNumRemaining();
				if (aligner.getNumErrors()>0) status += ", errors " + aligner.getNumErrors(); 
				
				diaLog.updatePop("\nRunning alignments:\n", aligner.getStatusSummary() + status + "\n");

				while (aligner.getNumRunning()>0 || aligner.getNumRemaining()>0) {
					if (Cancelled.isCancelled()) return;
					Utils.sleep(5000);			// CAS592 was 10000 - was behind finishing for demo errors
					if (Cancelled.isCancelled()) return;
				
					// same as 3 lines before loop
					status = "\nAlignments:  running " + aligner.getNumRunning() + ", completed " + aligner.getNumCompleted();
					if (aligner.getNumRemaining()>0) status += ", queued " + aligner.getNumRemaining();
					if (aligner.getNumErrors()>0)    status += ", errors " + aligner.getNumErrors(); 
				
					diaLog.updatePop("\nRunning alignments:\n", aligner.getStatusSummary() + status + "\n");
				}
				diaLog.updatePop("Alignments: " ,  "Completed " + aligner.getNumCompleted() + "\n\n");
			}
		};
		
		/****************************************************
		 * XXX Main Thread: Perform alignment, load anchors and compute synteny 
		 *********************************************************/
		// each 'run' makes a new connection from dbc2
		// return exists thread and goes to finish
		final Thread runAllThread = new Thread() {
			public void run() {
				boolean bSuccess = true;
				try {
					if (bOnly) {
						if (mp.bSynOnly) synteny.run( mProj1, mProj2);	
						if (mp.bCoSet)   anchors.collinear(mProj1, mProj2);
						if (mp.bPseudo)  anchors.addPseudoOnly();
					
						if (Cancelled.isCancelled()) {
							diaLog.setVisible(false); 
							diaLog.setCancelled();
							return;
						}
						if (!bSuccess) {
							diaLog.setFailed();
							return;
						}	
					
						mp.saveParamsForOnly();	
						diaLog.finish(bSuccess); 
						return; 		// goto Finish
					}
					
					/** Align -- MUMmer or Blast**/
					if (bLoadBlast) bSuccess = loadBlast.run(); 
					else            bSuccess = aligner.run();  
					
					if (Cancelled.isCancelled()) {
						diaLog.setVisible(false); 
						diaLog.setCancelled();
						return;
					}
					if (!bSuccess) {
						diaLog.setFailed();
						diaLog.finish(bSuccess); 
						return;
					}	
					
					/** Anchors -- MUMmer add hits, cluster, pseudo; MUMmer/Blast number, collinear **/
					anchors.run( mProj1, mProj2, bLoadBlast); 
					
					if (Cancelled.isCancelled()) {
						diaLog.setVisible(false); 
						diaLog.setCancelled();
						return;
					}
					if (!bSuccess) {
						diaLog.setFailed();
						Utils.sleep(1000); // let mummerThread catch up
						diaLog.finish(bSuccess); 
						return;
					}	
					
					/** Synteny **/
					bSuccess = synteny.run( mProj1, mProj2);
					
					if (Cancelled.isCancelled()) {
						diaLog.setVisible(false); 
						diaLog.setCancelled();
						return;
					}
					if (!bSuccess) {
						diaLog.setFailed();
						diaLog.finish(bSuccess); 
						return;
					}	
					
					diaLog.finish(bSuccess); 
				}
				catch (OutOfMemoryError e) {
					mummerThread.interrupt();
					bSuccess = false;
					diaLog.msg( "Not enough memory - increase 'mem' in symap script");
					Popup.showOutOfMemoryMessage(diaLog);
				}
				catch (Exception e) {
					mummerThread.interrupt();
					bSuccess = false;
					ErrorReport.print(e,"Running A&S");
				}	
			}
		};
		/** Dialog popup **/
		diaLog.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				System.out.println(e.getActionCommand());
				
				// Stop aligner and wait on monitor threads to finish
				if (aligner!=null) 			aligner.interrupt();
				else if (loadBlast!=null) 	loadBlast.interrupt();
				
				if (anchors!=null) anchors.interrupt();
				if (synteny!=null) synteny.interrupt(); 
				
				try {
					runAllThread.wait(5000);
					mummerThread.wait(5000);
				}
				catch (Exception e2) { }
				
				// Threads should be done by now, but kill just in case
				runAllThread.interrupt();
				mummerThread.interrupt();
				
				// Close dialog
				diaLog.setVisible(false);
				if (e.getActionCommand().equals(ProgressDialog.CANCEL)) {
					diaLog.setCancelled();
				}
			}
		});
		
		runAllThread.start();	// main thread
		mummerThread.start();	// only runs if MUMmer is being used
		diaLog.start(); 		// blocks until thread finishes or user cancels
				
		/**********************************************************
		 * XXX Finish - all processing ends here
		 ********************************************************/
		if (diaLog.wasCancelled() || diaLog.failed()) { // Remove partially-loaded alignment
			if (bOnly) {
				Popup.showWarningMessage("The database may be in an incomplete state");
			}
			else {
				try {
					String msgx =  "Confirm: Remove " + mp.toString() + " from database (slow if large database)" + 
							     "\nCancel:  The user removes the database and starts over";
					if (Popup.showConfirm2("Remove pair", msgx)) { 
						mp.removePairFromDB(true); 					// True redo numHits; writes to stdout that it is removing
						System.out.println("   Removal complete");
					} 
					else System.out.println("Remove database and restart");
				}
				catch (Exception e) { ErrorReport.print(e, "Removing alignment");}
			}
		}
		else {
			if (!mp.bPseudo) { // when moved into thread, error on creating summary
				diaLog.msgPop(">> Summary for " + toName + "\n");
				new SumFrame(dbc2, mp);
				printStats(diaLog);
			}
		}
		String what = "Complete ";
		if (diaLog.wasCancelled()) what = "Cancelled ";
		else if (diaLog.failed())  what = "Failed ";
		if (bOnly)			 Utils.prtMsgTimeDone(diaLog, what + "Only", timeStart);
		else if (bLoadBlast) Utils.prtMsgTimeDone(diaLog, what + "Load&SyMAP", timeStart);
		else                 Utils.prtMsgTimeDone(diaLog, what + "Align&SyMAP", timeStart);
		
		System.out.println("--------------------------------------------------");
		frame.refreshMenu();
		try {
			if (syLogFW != null) {
				if (Cancelled.isCancelled())  syLogFW.write("Cancelled");
				if (ErrorCount.getCount()>0)  syLogFW.write("Errors");
				syLogFW.write("\n-------------- done " + new Date().toString() + " -----------------\n");
				syLogFW.close();
			}
		}
		catch (Exception e) {}
	} // end of run method
	
	/**********************************************************************/
	private FileWriter symapLog(Mproject p1, Mproject p2) {
		FileWriter ret = null;
		try {
			File pd = new File(Constants.logDir);
			if (!pd.isDirectory()) pd.mkdir();
			
			String pairDir = Constants.logDir + p1.getDBName() + Constants.projTo + p2.getDBName(); 
			pd = new File(pairDir);
			if (!pd.isDirectory()) pd.mkdir();
			
			File lf = new File(pd,Constants.syntenyLog);
			ret = new FileWriter(lf, true); // append
			System.out.println("\nAppend to log file: " 
					+ pairDir + "/" + Constants.syntenyLog + " (Length " + Utilities.kMText(lf.length()) + ")");	
		}
		catch (Exception e) {ErrorReport.print(e, "Creating log file");}
		return ret;
	}

	private void printStats(ProgressDialog prog)  {
		int pairIdx = mp.getPairIdx();
		if (pairIdx<=0) return;

		TreeMap<String,Integer> counts = new TreeMap<String,Integer>();
		
		try { 
			int cnt = dbc2.executeCount("select count(*) from pseudo_hits where pair_idx=" + pairIdx);	
			counts.put("nhits", cnt);
			
			cnt = dbc2.executeCount("select count(*) from pseudo_block_hits as pbh join pseudo_hits as ph on pbh.hit_idx=ph.idx " +
									" where ph.pair_idx=" + pairIdx);
			counts.put("blkhits", cnt);

			cnt =  dbc2.executeCount("select count(*) from pseudo_hits where gene_overlap > 0 and pair_idx=" + pairIdx);	
			counts.put("genehits", cnt);

			cnt = dbc2.executeCount("select count(*)  from blocks where pair_idx=" + pairIdx);	
			counts.put("blocks", cnt);
		}
		catch (Exception e) {ErrorReport.print(e, "Gtting counts"); }
		
		Utils.prtNumMsg(prog, counts.get("nhits"), "hits");
		Utils.prtNumMsg(prog, counts.get("blocks"), "synteny blocks");
		Utils.prtNumMsg(prog, counts.get("genehits"), "gene hits");
		Utils.prtNumMsg(prog, counts.get("blkhits"), "synteny hits");
	}
	
	private String buildLogAlignDir(Mproject p1, Mproject p2) {
		try {
			String logName = Constants.logDir + p1.getDBName() + Constants.projTo + p2.getDBName();
			if (util.FileDir.dirExists(logName)) System.out.println("Log alignments in directory: " + logName);
			else util.FileDir.checkCreateDir(logName, true /* bPrt */);
			return logName + "/";
		}
		catch (Exception e){ErrorReport.print(e, "Creating log file");}
		return null;
	}

	/*** Load Blocks Pair --blk  *************/
	private void loadBlocks(ProgressDialog mLog, Mproject mProj1, Mproject mProj2) {
	try {
		mLog.msg("Load blocks from file");
		if (!mProj1.hasGenes() || !mProj2.hasGenes()) { 
			mLog.msg("Both projects must have genes to load blocks");
			return;
		}
		String defaultDir = Constants.getNameResultsDir(mProj1.getDBName(), mProj2.getDBName());
		
		JFileChooser cf = new JFileChooser();
		cf.setMultiSelectionEnabled(false);
		cf.setCurrentDirectory(new File(defaultDir));
		String path=null;
		
		if (cf.showOpenDialog(getInstance()) == JFileChooser.APPROVE_OPTION) 
			path = cf.getSelectedFile().getAbsolutePath().trim();
		else {
			Utils.prtIndentMsgFile(mLog, 1, "Abort Load Blocks");
			return;
		}
		
		new UserBlocks(mLog, dbc2, mp, path);
		
		mp.saveLoad("Load Blocks " + path);
		new SumFrame(dbc2, mp);
		printStats(mLog);
		System.out.println("--------------------------------------------------");
	}
	catch (Exception e){ErrorReport.print(e, "Load block file"); }
	}
	
	private DoAlignSynPair getInstance() {return this;}
	
	/*** Update NumHits: -nh command line, not visible  ***/ 
	private void numHitsOnly(ProgressDialog mLog, Mproject mProj1, Mproject mProj2) {
	try {
		if (!mProj1.hasGenes() || !mProj2.hasGenes()) { 
			mLog.msg("Both projects must have genes to have numHits");
			return;
		}
		AnchorMain anchors = new AnchorMain(dbc2, mLog, mp);
		anchors.saveAnnoHitCnt(true);
		new SumFrame(dbc2, mp, "Recompute numHits"); // only so it will show the updated version
		System.out.println("--------------------------------------------------");
	}
	catch (Exception e){ErrorReport.print(e, "Creating log file"); }
	}
}
