 package backend;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.util.Date;
import java.util.TreeMap;
import javax.swing.JFrame;

import backend.synteny.SyntenyMain;
import database.DBconn2;
import symap.manager.Mpair;
import symap.manager.Mproject;
import symap.manager.SumFrame;
import symap.manager.ManagerFrame;
import util.Cancelled;
import util.ErrorReport;
import util.ProgressDialog;
import util.Utilities;

/**********************************************************
 * DoAlignSynPair; Calls AlignMain, AnchorMain and SyntenyMain; Called from ManagerFrame
 * CAS508 moved all the following from ManagerFrame; CAS544 AlignProj->AlgSynMain; CAS560 AlgSynMain->DoAlignSynPair
 
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
	
	public void run(ManagerFrame frame, DBconn2 dbc2, Mpair mp,  boolean closeWhenDone,  int maxCPUs) {

		this.dbc2 = dbc2;
		this.mp = mp;
		
		Mproject mProj1 = mp.mProj1;
		Mproject mProj2 = mp.mProj2;
		String dbName1 = mProj1.getDBName();
		String dbName2 = mProj2.getDBName();
		String toName =  (mProj1 == mProj2) ? dbName1 + " to self" : dbName1 + " to " + dbName2;
		
		FileWriter syFW =   symapLog(mProj1,mProj2);
		String alignLogDir = buildLogAlignDir(mProj1,mProj2);

		String msg = (mProj1 == mProj2) ? "Synteny "  + dbName1 + " to itself ..." : "Synteny " + toName + " ...";
		
		final ProgressDialog diaLog = new ProgressDialog(this, "Running Synteny", msg, true, syFW); // write version and date
		String v = (Constants.VERBOSE) ? "   Verbose" : "    !Verbose"; 
		diaLog.msgToFileOnly(">>> " + toName + v);
		System.out.println("\n>>> Starting " + toName + "     " + Utilities.getDateTime());
		
		if (Constants.CoSET_ONLY) {
			collinearOnly(diaLog, mProj1, mProj2);
			return;
		}
		else if (Constants.PSEUDO_ONLY) {// CAS565 add
			pseudoOnly(diaLog, mProj1, mProj2);
			return;
		}
		
		String chgMsg = mp.getChangedParams(Mpair.FILE);  // Saved to database pairs table in SumFrame
		diaLog.msg(chgMsg);
		diaLog.closeWhenDone();					
		
		final AlignMain aligner = new AlignMain(dbc2, diaLog, mp,  maxCPUs, alignLogDir);
		if (aligner.mCancelled) return;
		
		final AnchorMain anchors = new AnchorMain(dbc2, diaLog, mp );
		
		final SyntenyMain synteny = new SyntenyMain(dbc2, diaLog, mp );
		
		final Thread statusThread = new Thread() {
			public void run() {
				
				while (aligner.notStarted() && !Cancelled.isCancelled()) Utilities.sleep(1000);
				if (aligner.getNumRemaining() == 0 && aligner.getNumRunning() == 0) return;
				if (Cancelled.isCancelled()) return;
				
				// Alignments\n list errors and current\nStatus
				String status = "\nAlignments:  running " + aligner.getNumRunning() + ", completed " + aligner.getNumCompleted();
				if (aligner.getNumRemaining()>0) status += ", queued " + aligner.getNumRemaining();
				if (aligner.getNumErrors()>0) status += ", errors " + aligner.getNumErrors(); // CAS566 add
				
				diaLog.updateText("\nRunning alignments:\n", aligner.getStatusSummary() + status + "\n");

				while (aligner.getNumRunning()>0 || aligner.getNumRemaining()>0) {
					if (Cancelled.isCancelled()) return;
					Utilities.sleep(10000);
					if (Cancelled.isCancelled()) return;
					
					// same as before loop
					status = "\nAlignments:  running " + aligner.getNumRunning() + ", completed " + aligner.getNumCompleted();
					if (aligner.getNumRemaining()>0) status += ", queued " + aligner.getNumRemaining();
					if (aligner.getNumErrors()>0)    status += ", errors " + aligner.getNumErrors(); // CAS566 add
				
					diaLog.updateText("\nRunning alignments:\n", aligner.getStatusSummary() + status + "\n");
				}
				diaLog.updateText("Alignments:" ,  "Completed " + aligner.getNumCompleted() + "\n\n");
			}
		};
		
		// Perform alignment, load anchors and compute synteny; cancel and success like original
		// each 'run' makes a new connection from dbc2
		final Thread alignThread = new Thread() {
			public void run() {
				boolean success = true;
				
				try {
					long timeStart = Utils.getTime();
					
					/** Align **/
					success &= aligner.run(); 
					
					if (Cancelled.isCancelled()) {
						diaLog.setVisible(false);
						diaLog.setCancelled();
						return;
					}
					if (!success) {
						diaLog.finish(false);
						return;
					}	
					
					if (Cancelled.isCancelled()) return;
					
					/** Anchors **/
					success &= anchors.run( mProj1, mProj2); // add anchors, cluster, number, collinear, pseudo
					
					if (Cancelled.isCancelled()) return;
					if (!success) {
						diaLog.finish(false);
						return;
					}
					
					/** Synteny **/
					success &= synteny.run( mProj1, mProj2);
					
					if (Cancelled.isCancelled()) return;
					if (!success) {
						diaLog.finish(false);
						return;
					}
					
					/** Finish **/
					String params = aligner.getParams();
					mp.saveParams(params);		// deletes all pair_props, then add params
					mProj1.saveParams(mProj1.xAlign);
					mProj2.saveParams(mProj2.xAlign);
				
					new SumFrame(dbc2, mp);
					
					diaLog.appendText(">> Summary for " + toName + "\n");
					printStats(diaLog, mProj1, mProj2);
					
					Utils.timeDoneMsg(diaLog, "Complete Alignment&Synteny", timeStart);
				}
				catch (OutOfMemoryError e) {
					success = false;
					statusThread.interrupt();
					diaLog.msg( "Not enough memory - increase 'mem' in symap script");
					Utilities.showOutOfMemoryMessage(diaLog);
				}
				catch (Exception e) {
					success = false;
					ErrorReport.print(e,"Running alignment");
					statusThread.interrupt();
				}
				diaLog.finish(success); 
			}
		};
		
		diaLog.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				System.out.println(e.getActionCommand());
				// Stop aligner and wait on monitor threads to finish
				aligner.interrupt();
				anchors.interrupt();
				try {
					alignThread.wait(5000);
					statusThread.wait(5000);
				}
				catch (Exception e2) { }
				
				// Threads should be done by now, but kill just in case
				alignThread.interrupt();
				statusThread.interrupt();
				
				// Close dialog
				diaLog.setVisible(false);
				if (e.getActionCommand().equals("Cancel")) {
					diaLog.setCancelled();
					Cancelled.cancel();
				}
			}
		});
		
		alignThread.start();
		statusThread.start();
		diaLog.start(); // blocks until thread finishes or user cancels
		
		if (diaLog.wasCancelled()) { // Remove partially-loaded alignment
			try {
				Thread.sleep(1000);
				String msgx =  "Confirm: Remove " + mp.toString() + " from database (slow if large database)" + 
						     "\nCancel:  The user removes the database and starts over";
				
				if (Utilities.showConfirm2("Remove pair", msgx)) { 
					System.out.println("Cancel alignment (removing alignment from DB)");
						mp.removePairFromDB(true); 					// redo numHits;
					System.out.println("Removal complete");
				} 
				else System.out.println("Remove database and restart");
			}
			catch (Exception e) { ErrorReport.print(e, "Removing alignment");}
		}
		else if (closeWhenDone) {
			diaLog.dispose();	
		}
		
		System.out.println("--------------------------------------------------");
		frame.refreshMenu();
		try {
			if (syFW != null) {
				if (Cancelled.isCancelled())  syFW.write("Cancelled");
				syFW.write("\n-------------- done " + new Date().toString() + " --------------------\n");
				syFW.close();
			}
		}
		catch (Exception e) {}
	} // end of run
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
			System.out.println("Append to log file: " 
					+ pairDir + "/" + Constants.syntenyLog + " (Length " + Utilities.kMText(lf.length()) + ")");	
		}
		catch (Exception e) {ErrorReport.print(e, "Creating log file");}
		return ret;
	}

	private void printStats(ProgressDialog prog, Mproject p1, Mproject p2)  {
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
			if (Utilities.dirExists(logName)) System.out.println("Log alignments in directory: " + logName);
			else Utilities.checkCreateDir(logName, true /* bPrt */);
			return logName + "/";
		}
		catch (Exception e){ErrorReport.print(e, "Creating log file");}
		return null;
	}
	/**********************************************************************/
	private void collinearOnly(ProgressDialog mLog, Mproject mProj1, Mproject mProj2) {
	try {
		mLog.msg("Only run collinear set algorithm");
		if (!mProj1.hasGenes() || !mProj2.hasGenes()) { 
			mLog.msg("Both projects must have genes for the collinear set algorithm");
			dbc2.close(); return;
		}
		int mPairIdx = Utils.getPairIdx(mProj1.getIdx(), mProj2.getIdx(), dbc2);
		if (mPairIdx==0) {
			mLog.msg("Cannot find project pair in database for " + mProj1.getDisplayName() + "," + mProj2.getDisplayName());
			dbc2.close(); return;
		}
		AnchorPost collinear = new AnchorPost(mPairIdx, mProj1, mProj2, dbc2, mLog);
		collinear.collinearSets();
			
		mp.saveUpdate();
		new SumFrame(dbc2, mp);
		System.out.println("--------------------------------------------------");
	}
	catch (Exception e){ErrorReport.print(e, "Creating log file"); }
	}
	/*** Add Pseudo CAS565 ***/ 
	private void pseudoOnly(ProgressDialog mLog, Mproject mProj1, Mproject mProj2) {
	try {
		mLog.msg("Only add pseudo-genes");
		int mPairIdx = Utils.getPairIdx(mProj1.getIdx(), mProj2.getIdx(), dbc2);
		if (mPairIdx==0) {
			mLog.msg("Cannot find project pair in database for " + mProj1.getDisplayName() + "," + mProj2.getDisplayName());
			dbc2.close(); return;
		}
		AnchorMain anchors = new AnchorMain(dbc2, mLog, mp );
		anchors.addPseudoFromFlag();
			
		String params = dbc2.executeString("select params from pairs where idx=" + mp.getPairIdx());
		mp.saveParams(params);		// re-enter params and update NOW(), then update pair_props
		
		new SumFrame(dbc2, mp); // only so it will show the updated version
		System.out.println("--------------------------------------------------");
	}
	catch (Exception e){ErrorReport.print(e, "Creating log file"); }
	}
}
